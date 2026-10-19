package com.chimali.fido2.data.eventsourcing

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.Snapshot
import com.chimali.core.domain.eventsourcing.passkey.PasskeyState
import com.chimali.core.domain.repository.SnapshotRepository
import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.EventStoreKeyProvider
import com.chimali.fido2.data.database.Fido2Database
import java.io.ByteArrayInputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

/**
 * Implementation of SnapshotRepository for the Fido2Database.
 * Handles serialization and encryption of Passkey aggregate snapshots.
 */
@OptIn(ExperimentalSerializationApi::class)
class PasskeySnapshotRepositoryImpl(
    private val database: Fido2Database,
    private val encryptionManager: EncryptionManager,
    private val keyProvider: EventStoreKeyProvider,
) : SnapshotRepository {
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getSerializer(kind: EventKind): KSerializer<T> =
        when (kind) {
            EventKind.PASSKEY -> PasskeyState.serializer() as KSerializer<T>
            else -> throw IllegalArgumentException("Unsupported aggregate kind: $kind")
        }

    /** Encrypts the snapshot and erases temporary plaintext and key buffers on all exit paths. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun <T> save(
        kind: EventKind,
        snapshot: Snapshot<T>,
    ): Result<Unit> {
        if (kind != EventKind.PASSKEY) return Result.success(Unit)

        val context = currentCoroutineContext()
        var key: ByteArray? = null
        return try {
            captureStorageResult {
                context.ensureActive()
                val eventKey = keyProvider.getEventStoreKey("chimali_fido2_es_v1")
                key = eventKey
                database.transaction {
                    context.ensureActive()
                    val stateSerializer = getSerializer<T>(kind)
                    val snapshotSerializer = Snapshot.serializer(stateSerializer)
                    var plaintext: ByteArray? = null
                    var encryptedPayload: ByteArray? = null
                    try {
                        val state =
                            snapshot.state as? PasskeyState
                                ?: throw IllegalArgumentException("Unsupported passkey snapshot state")

                        @Suppress("UNCHECKED_CAST")
                        val sanitizedSnapshot =
                            snapshot.copy(
                                state =
                                    state.copy(
                                        rpId = charArrayOf(),
                                        userId = charArrayOf(),
                                        userName = charArrayOf(),
                                        userDisplayName = charArrayOf(),
                                    ) as T,
                            )
                        plaintext =
                            SensitiveJsonFields.encode(
                                linkedMapOf(
                                    "rpId" to state.rpId,
                                    "userId" to state.userId,
                                    "userName" to state.userName,
                                    "userDisplayName" to state.userDisplayName,
                                ),
                            ) { output -> json.encodeToStream(snapshotSerializer, sanitizedSnapshot, output) }
                        encryptedPayload = encryptionManager.encrypt(plaintext, eventKey)
                        database.fido2DatabaseQueries.insert_snapshot(
                            aggregate_id = snapshot.aggregateId,
                            sequence_number = snapshot.sequenceNumber,
                            timestamp = snapshot.timestamp.toString(),
                            payload = encryptedPayload,
                        )

                        // Retain only the last 2 snapshots
                        database.fido2DatabaseQueries.delete_old_snapshots(
                            aggregate_id = snapshot.aggregateId,
                            aggregate_id_ = snapshot.aggregateId,
                        )
                    } finally {
                        plaintext?.fill(0)
                        encryptedPayload?.fill(0)
                    }
                    context.ensureActive()
                }
                Unit
            }
        } finally {
            key?.fill(0)
        }
    }

    /** Reconstructs the latest snapshot, transfers state ownership and erases temporary buffers. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun <T> getLatest(
        kind: EventKind,
        aggregateId: String,
    ): Result<Snapshot<T>?> =
        getLatestWithDecoder(kind, aggregateId) { plaintext ->
            decodePasskeySnapshot(kind, plaintext)
        }

    /** Runs snapshot retrieval with a decoder so terminal ownership can be checked through retained references. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal suspend fun <T> getLatestWithDecoder(
        kind: EventKind,
        aggregateId: String,
        decodeSnapshot: (ByteArray) -> Snapshot<T>,
    ): Result<Snapshot<T>?> {
        if (kind != EventKind.PASSKEY) return Result.success(null)

        val context = currentCoroutineContext()
        var key: ByteArray? = null
        var plaintext: ByteArray? = null
        return try {
            captureStorageResult {
                context.ensureActive()
                val row =
                    database.fido2DatabaseQueries.get_latest_snapshot(aggregate_id = aggregateId).executeAsOneOrNull()
                        ?: return@captureStorageResult null

                val eventKey = keyProvider.getEventStoreKey("chimali_fido2_es_v1")
                key = eventKey
                plaintext = encryptionManager.decrypt(row.payload, eventKey)
                var decoded: Snapshot<T>? = null
                var transferred = false
                try {
                    decoded = decodeSnapshot(plaintext)
                    context.ensureActive()
                    transferred = true
                    decoded
                } finally {
                    if (!transferred) (decoded?.state as? PasskeyState)?.eraseSensitiveMetadata()
                }
            }
        } finally {
            plaintext?.fill(0)
            key?.fill(0)
        }
    }

    /** Reconstructs snapshot state using only its root `state` sensitive-field path. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun <T> decodePasskeySnapshot(
        kind: EventKind,
        plaintext: ByteArray,
    ): Snapshot<T> {
        val stateSerializer = getSerializer<T>(kind)
        val snapshotSerializer = Snapshot.serializer(stateSerializer)
        return SensitiveJsonFields.decode(
            input = plaintext,
            fieldNames = SENSITIVE_FIELDS,
            fieldObjectPath = listOf("state"),
            deserializeSanitized = { sanitized ->
                json.decodeFromStream(snapshotSerializer, ByteArrayInputStream(sanitized))
            },
            attachFields = { sanitizedSnapshot, fields ->
                val state =
                    sanitizedSnapshot.state as? PasskeyState
                        ?: throw IllegalArgumentException("Unsupported passkey snapshot state")
                @Suppress("UNCHECKED_CAST")
                sanitizedSnapshot.copy(
                    state =
                        state.copy(
                            rpId = requireNotNull(fields["rpId"]),
                            userId = requireNotNull(fields["userId"]),
                            userName = requireNotNull(fields["userName"]),
                            userDisplayName = requireNotNull(fields["userDisplayName"]),
                        ) as T,
                )
            },
        )
    }

    private companion object {
        val SENSITIVE_FIELDS = setOf("rpId", "userId", "userName", "userDisplayName")
    }
}

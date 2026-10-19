package com.chimali.fido2.data.eventsourcing

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.eventsourcing.DomainEvent
import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.passkey.PasskeyEvent
import com.chimali.core.domain.repository.EventStoreRepository
import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.EventStoreKeyProvider
import com.chimali.fido2.data.database.Fido2Database
import java.io.ByteArrayInputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Implementation of EventStoreRepository for the Fido2Database.
 * Handles serialization and encryption of Passkey aggregate events.
 */
@OptIn(ExperimentalSerializationApi::class)
class PasskeyEventStoreRepositoryImpl(
    private val database: Fido2Database,
    private val encryptionManager: EncryptionManager,
    private val keyProvider: EventStoreKeyProvider,
) : EventStoreRepository {
    private val json =
        Json {
            serializersModule =
                SerializersModule {
                    polymorphic(DomainEvent::class) {
                        subclass(PasskeyEvent.Registered::class)
                        subclass(PasskeyEvent.Authenticated::class)
                        subclass(PasskeyEvent.Deleted::class)
                    }
                }
        }

    /** Encrypts and appends events, erasing temporary plaintext and key buffers on all exit paths. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun append(
        kind: EventKind,
        events: List<DomainEvent>,
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
                    events.forEach { event ->
                        context.ensureActive()
                        var plaintext: ByteArray? = null
                        var encryptedPayload: ByteArray? = null
                        try {
                            plaintext = encodePasskeyEvent(event)
                            encryptedPayload = encryptionManager.encrypt(plaintext, eventKey)
                            database.fido2DatabaseQueries.insert_event(
                                aggregate_id = event.aggregateId,
                                sequence_number = event.sequenceNumber,
                                timestamp = event.timestamp.toString(),
                                payload = encryptedPayload,
                            )
                        } finally {
                            plaintext?.fill(0)
                            encryptedPayload?.fill(0)
                        }
                    }
                    context.ensureActive()
                }
                Unit
            }
        } finally {
            key?.fill(0)
        }
    }

    /** Reconstructs the event stream and transfers results after erasing temporary plaintext and keys. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun getEvents(
        kind: EventKind,
        aggregateId: String,
        upTo: Instant?,
    ): Result<List<DomainEvent>> = getEventsWithDecoder(kind, aggregateId, upTo, ::decodePasskeyEvent)

    /** Executes replay with a caller-supplied decoder so the owner cleanup path can be retained in tests. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal suspend fun getEventsWithDecoder(
        kind: EventKind,
        aggregateId: String,
        upTo: Instant?,
        decodeEvent: (ByteArray) -> DomainEvent,
    ): Result<List<DomainEvent>> {
        if (kind != EventKind.PASSKEY) return Result.success(emptyList())

        val context = currentCoroutineContext()
        var key: ByteArray? = null
        return try {
            captureStorageResult {
                context.ensureActive()
                val timestampLimit = upTo?.toString() ?: "9999-12-31T23:59:59Z"
                val eventKey = keyProvider.getEventStoreKey("chimali_fido2_es_v1")
                key = eventKey
                val rows =
                    database.fido2DatabaseQueries
                        .get_events(aggregate_id = aggregateId, timestamp = timestampLimit)
                        .executeAsList()
                val events = mutableListOf<DomainEvent>()
                var transferred = false
                try {
                    rows.forEach { row ->
                        context.ensureActive()
                        var plaintext: ByteArray? = null
                        try {
                            plaintext = encryptionManager.decrypt(row.payload, eventKey)
                            events += decodeEvent(plaintext)
                        } finally {
                            plaintext?.fill(0)
                        }
                    }
                    context.ensureActive()
                    transferred = true
                    events.toList()
                } finally {
                    if (!transferred) events.forEach(::eraseSensitiveEvent)
                }
            }
        } finally {
            key?.fill(0)
        }
    }

    /** Reconstructs events from the requested sequence and transfers results after erasing temporary buffers. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun getEventsFrom(
        kind: EventKind,
        aggregateId: String,
        fromSequence: Long,
    ): Result<List<DomainEvent>> {
        if (kind != EventKind.PASSKEY) return Result.success(emptyList())

        val context = currentCoroutineContext()
        var key: ByteArray? = null
        return try {
            captureStorageResult {
                context.ensureActive()
                val eventKey = keyProvider.getEventStoreKey("chimali_fido2_es_v1")
                key = eventKey
                val rows =
                    database.fido2DatabaseQueries
                        .get_events_from(aggregate_id = aggregateId, sequence_number = fromSequence)
                        .executeAsList()
                val events = mutableListOf<DomainEvent>()
                var transferred = false
                try {
                    rows.forEach { row ->
                        context.ensureActive()
                        var plaintext: ByteArray? = null
                        try {
                            plaintext = encryptionManager.decrypt(row.payload, eventKey)
                            events += decodePasskeyEvent(plaintext)
                        } finally {
                            plaintext?.fill(0)
                        }
                    }
                    context.ensureActive()
                    transferred = true
                    events.toList()
                } finally {
                    if (!transferred) events.forEach(::eraseSensitiveEvent)
                }
            }
        } finally {
            key?.fill(0)
        }
    }

    private fun eraseSensitiveEvent(event: DomainEvent) {
        if (event is PasskeyEvent.Registered) event.eraseSensitiveMetadata()
    }

    private fun encodePasskeyEvent(event: DomainEvent): ByteArray =
        if (event is PasskeyEvent.Registered) {
            val sanitized =
                event.copy(
                    rpId = charArrayOf(),
                    userId = charArrayOf(),
                    userName = charArrayOf(),
                    userDisplayName = charArrayOf(),
                )
            SensitiveJsonFields.encode(
                linkedMapOf(
                    "rpId" to event.rpId,
                    "userId" to event.userId,
                    "userName" to event.userName,
                    "userDisplayName" to event.userDisplayName,
                ),
            ) { output -> json.encodeToStream(PolymorphicSerializer(DomainEvent::class), sanitized, output) }
        } else {
            encodeToErasableJsonBytes { output ->
                json.encodeToStream(PolymorphicSerializer(DomainEvent::class), event, output)
            }
        }

    private fun decodePasskeyEvent(plaintext: ByteArray): DomainEvent =
        SensitiveJsonFields.decode(
            input = plaintext,
            fieldNames = SENSITIVE_FIELDS,
            deserializeSanitized = { sanitized -> json.decodeFromStream<DomainEvent>(ByteArrayInputStream(sanitized)) },
            attachFields = { decoded, fields ->
                if (decoded !is PasskeyEvent.Registered) {
                    require(fields.isEmpty()) { "Unexpected sensitive fields for passkey event" }
                    decoded
                } else {
                    decoded.copy(
                        rpId = requireNotNull(fields["rpId"]),
                        userId = requireNotNull(fields["userId"]),
                        userName = requireNotNull(fields["userName"]),
                        userDisplayName = requireNotNull(fields["userDisplayName"]),
                    )
                }
            },
        )

    private companion object {
        val SENSITIVE_FIELDS = setOf("rpId", "userId", "userName", "userDisplayName")
    }
}

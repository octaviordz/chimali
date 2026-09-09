package com.chimali.core.data.eventsourcing

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.core.database.VaultDatabase
import com.chimali.core.domain.eventsourcing.EventKind
import com.chimali.core.domain.eventsourcing.Snapshot
import com.chimali.core.domain.eventsourcing.vault.VaultEvent
import com.chimali.core.domain.eventsourcing.vault.VaultState
import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.EventStoreKeyProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** T039 title-path dependency: observes actual buffers, independently of immutable-model remediation. */
class VaultMetadataBufferOwnershipTest {
    private val timestamp = Instant.parse("2026-09-09T00:00:00Z")
    private val snapshot = Snapshot("aggregate", 1, VaultState(id = "aggregate", title = "synthetic title"), timestamp)

    @Test
    fun `snapshot save erases key and serialized bytes on success failure and cancellation`() =
        runTest {
            for (failure in listOf(null, IllegalStateException("synthetic"), CancellationException("synthetic"))) {
                Fixture().use { fixture ->
                    fixture.crypto.failure = failure
                    val result = runCatching { fixture.snapshots.save(EventKind.VAULT_ENTRY, snapshot).getOrThrow() }
                    if (failure == null) assertTrue(result.isSuccess) else assertSame(failure, result.exceptionOrNull())
                    assertNotNull(fixture.crypto.plaintext)
                    assertErased(fixture.crypto.plaintext!!)
                    assertErased(fixture.key)
                }
            }
        }

    @Test
    fun `snapshot read erases key and decoded bytes on valid and malformed contents`() =
        runTest {
            val valid = Json.encodeToString(Snapshot.serializer(VaultState.serializer()), snapshot).encodeToByteArray()
            for (bytes in listOf(valid, "{malformed synthetic title".encodeToByteArray())) {
                Fixture().use { fixture ->
                    fixture.insertSnapshot()
                    fixture.crypto.decoded = bytes
                    val result =
                        runCatching { fixture.snapshots.getLatest<VaultState>(EventKind.VAULT_ENTRY, "aggregate") }
                    if (bytes === valid) {
                        assertTrue(
                            result
                                .getOrThrow()
                                .getOrThrow()!!
                                .state.title
                                .contentEquals("synthetic title".toCharArray()),
                        )
                    } else {
                        assertTrue(result.isFailure)
                    }
                    assertErased(bytes)
                    assertErased(fixture.key)
                }
            }
        }

    @Test
    fun `snapshot decryption failure and cancellation erase acquired key`() =
        runTest {
            for (failure in listOf(IllegalStateException("synthetic"), CancellationException("synthetic"))) {
                Fixture().use { fixture ->
                    fixture.insertSnapshot()
                    fixture.crypto.failure = failure
                    val result =
                        runCatching { fixture.snapshots.getLatest<VaultState>(EventKind.VAULT_ENTRY, "aggregate") }
                    assertSame(failure, result.exceptionOrNull())
                    assertErased(fixture.key)
                }
            }
        }

    @Test
    fun `snapshot key provider failure precedes serialization and encryption`() =
        runTest {
            Fixture().use { fixture ->
                val failure = CancellationException("synthetic provider cancellation")
                fixture.providerFailure = failure
                val result = runCatching { fixture.snapshots.save(EventKind.VAULT_ENTRY, snapshot) }
                assertSame(failure, result.exceptionOrNull())
                assertEquals(null, fixture.crypto.plaintext)
                assertTrue("Provider never transferred this key", fixture.key.all { it == 7.toByte() })
            }
        }

    @Test
    fun `event append erases serialized bytes on success failure and cancellation`() =
        runTest {
            for (failure in listOf(null, IllegalStateException("synthetic"), CancellationException("synthetic"))) {
                Fixture().use { fixture ->
                    fixture.crypto.failure = failure
                    val repository = EventStoreRepositoryImpl(fixture.database, fixture.crypto, fixture.provider)
                    val event =
                        VaultEvent.Created(
                            "aggregate",
                            1,
                            timestamp,
                            "NOTE",
                            "synthetic title",
                            byteArrayOf(9),
                            "identity",
                        )
                    val result = runCatching { repository.append(EventKind.VAULT_ENTRY, listOf(event)).getOrThrow() }
                    if (failure == null) assertTrue(result.isSuccess) else assertSame(failure, result.exceptionOrNull())
                    assertNotNull(fixture.crypto.plaintext)
                    assertErased(fixture.crypto.plaintext!!)
                    assertErased(fixture.key)
                }
            }
        }

    private fun assertErased(bytes: ByteArray) {
        assertTrue("Observation must be nonempty", bytes.isNotEmpty())
        assertTrue("Owned bytes must be overwritten", bytes.all { it == 0.toByte() })
    }

    private class Fixture : AutoCloseable {
        private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val database = VaultDatabase(driver)
        val key = ByteArray(32) { 7 }
        val crypto = RecordingEncryption()
        var providerFailure: Throwable? = null
        val provider =
            object : EventStoreKeyProvider {
                override suspend fun getEventStoreKey(aggregateLabel: String): ByteArray {
                    providerFailure?.let { throw it }
                    return key
                }
            }
        val snapshots = SnapshotRepositoryImpl(database, crypto, provider)

        init {
            VaultDatabase.Schema.create(driver)
        }

        fun insertSnapshot() {
            database.vaultQueries.insert_snapshot("aggregate", 1, "2026-09-09T00:00:00Z", byteArrayOf(1, 2, 3))
        }

        override fun close() = driver.close()
    }

    private class RecordingEncryption : EncryptionManager {
        var plaintext: ByteArray? = null
        var decoded = byteArrayOf()
        var failure: Throwable? = null

        override fun encrypt(
            plaintext: ByteArray,
            key: ByteArray,
        ): ByteArray {
            this.plaintext = plaintext
            failure?.let { throw it }
            return byteArrayOf(1, 2, 3)
        }

        override fun decrypt(
            ciphertext: ByteArray,
            key: ByteArray,
        ): ByteArray {
            failure?.let { throw it }
            return decoded
        }
    }
}

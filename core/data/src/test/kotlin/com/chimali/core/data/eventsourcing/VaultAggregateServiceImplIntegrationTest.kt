package com.chimali.core.data.eventsourcing

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.core.database.VaultDatabase
import com.chimali.core.domain.eventsourcing.vault.VaultCommand
import com.chimali.core.domain.eventsourcing.vault.VaultState
import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.EventStoreKeyProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VaultAggregateServiceImplIntegrationTest {
    private lateinit var service: VaultAggregateServiceImpl
    private lateinit var database: VaultDatabase
    private val encryption = mockk<EncryptionManager>()
    private val keys = mockk<EventStoreKeyProvider>()
    private val key = ByteArray(32) { 3 }

    @Before
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        VaultDatabase.Schema.create(driver)
        database = VaultDatabase(driver)
        coEvery { keys.getEventStoreKey(any()) } returns key.copyOf()
        every { encryption.encrypt(any(), any()) } answers { firstArg<ByteArray>().copyOf() }
        every { encryption.decrypt(any(), any()) } answers { firstArg<ByteArray>().copyOf() }
        val events = EventStoreRepositoryImpl(database, encryption, keys)
        val snapshots = SnapshotRepositoryImpl(database, encryption, keys)
        service = VaultAggregateServiceImpl(events, snapshots)
    }

    @Test
    fun allVaultTypesRoundTripCreateUpdateDeleteAgainstRealStores() =
        runTest {
            val types = listOf("PASSWORD", "CREDIT_CARD", "NOTE")

            types.forEachIndexed { index, type ->
                val id = "vault-$index"
                service
                    .execute(
                        id,
                        VaultCommand.Create(id, type, "Initial $type", byteArrayOf(index.toByte()), "identity"),
                    ).getOrThrow()
                service
                    .execute(id, VaultCommand.Update(id, "Updated $type", byteArrayOf(9, index.toByte())))
                    .getOrThrow()

                val updated: VaultState = service.getState(id).getOrThrow()
                assertEquals(type, updated.type)
                assertTrue(updated.title.contentEquals("Updated $type".toCharArray()))
                assertEquals(listOf<Byte>(9, index.toByte()), updated.payload.toList())

                service.execute(id, VaultCommand.Delete(id)).getOrThrow()
                val deleted = service.getState(id).getOrThrow()
                assertTrue(deleted.isDeleted)
            }
        }
}

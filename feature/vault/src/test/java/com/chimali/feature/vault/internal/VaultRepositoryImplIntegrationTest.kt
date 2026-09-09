package com.chimali.feature.vault.internal

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.core.common.result.getOrThrow
import com.chimali.core.data.eventsourcing.EventStoreRepositoryImpl
import com.chimali.core.data.eventsourcing.SnapshotRepositoryImpl
import com.chimali.core.database.VaultDatabase
import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.EventStoreKeyProvider
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VaultRepositoryImplIntegrationTest {
    private lateinit var repository: VaultRepositoryImpl
    private lateinit var database: VaultDatabase
    private val encryption = mockk<EncryptionManager>()
    private val keys = mockk<EventStoreKeyProvider>()

    @Before
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        VaultDatabase.Schema.create(driver)
        database = VaultDatabase(driver)
        coEvery { keys.getEventStoreKey(any()) } returns ByteArray(32) { 4 }
        every { encryption.encrypt(any(), any()) } answers { firstArg<ByteArray>().copyOf() }
        every { encryption.decrypt(any(), any()) } answers { firstArg<ByteArray>().copyOf() }
        val events = EventStoreRepositoryImpl(database, encryption, keys)
        val snapshots = SnapshotRepositoryImpl(database, encryption, keys)
        repository =
            VaultRepositoryImpl(
                database,
                com.chimali.core.data.eventsourcing
                    .VaultAggregateServiceImpl(events, snapshots),
            )
    }

    @Test
    fun createUpdateReadAndDeleteProjectsAllVaultTypes() =
        runTest {
            val identityId = UUID.randomUUID()
            val items = VaultType.entries.map { type -> item(type, identityId) }

            items.forEach { repository.saveItem(it).getOrThrow() }
            val created = repository.getItems(null).getOrThrow()
            assertEquals(items.map { it.type }.toSet(), created.map { it.type }.toSet())

            val updated = items.first().copy(title = "Updated".toCharArray(), payload = byteArrayOf(8, 9))
            repository.saveItem(updated).getOrThrow()
            val reloaded = repository.getItems(null).getOrThrow().first { it.id == updated.id }
            assertArrayEquals("Updated".toCharArray(), reloaded.title)
            assertTrue(reloaded.payload.contentEquals(byteArrayOf(8, 9)))

            repository.deleteItem(updated.id).getOrThrow()
            assertTrue(repository.getItems(null).getOrThrow().none { it.id == updated.id })
        }

    private fun item(
        type: VaultType,
        identityId: UUID,
    ) = VaultItem(
        id = UUID.randomUUID(),
        type = type,
        title = type.name,
        payload = byteArrayOf(1, 2, 3),
        crdtState = byteArrayOf(),
        dateCreated = "2026-09-08T00:00:00Z",
        dateModified = "2026-09-08T00:00:00Z",
        lastBackedUpAt = null,
        identityId = identityId,
    )
}

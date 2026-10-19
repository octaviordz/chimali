package com.chimali.fido2.data.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.mapper.toDomainModel
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

class RelyingPartyTimestampInvariantTest {
    @Test
    fun `all activity writers reject clock regressions and preserve stored timestamps`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            Fido2Database.Schema.create(driver)
            val database = Fido2Database(driver)
            val timeProvider = mockk<TimeProvider>()
            every { timeProvider.now() } returns Instant.fromEpochMilliseconds(CLOCK_BEFORE_CREATION)
            val metadata = mockk<CredentialMetadataProtectionService>()
            every { metadata.encryptRelyingPartyMetadata(any()) } returns byteArrayOf(1, 2)
            val dao = RelyingPartyDao(database, timeProvider, metadata)
            val rpId = RpId("https://timestamp.example")
            val initial = relyingParty(rpId, CREATED_AT, LAST_USED_AT, name = "Original")
            try {
                dao.insertOrUpdateRelyingParty(initial)
                initial.eraseSensitiveText()
                val before = database.relyingPartyQueries.select_by_id(rpId.value.encodeToByteArray()).executeAsOne()

                val operations: List<suspend () -> Unit> =
                    listOf(
                        { dao.updateCredentialCount(rpId, 1) },
                        { dao.incrementCredentialCount(rpId) },
                        { dao.decrementCredentialCount(rpId) },
                        { dao.updateLastUsedAt(rpId) },
                    )
                operations.forEach { operation ->
                    assertFailsWith<Exception> { operation() }
                    val after = database.relyingPartyQueries.select_by_id(rpId.value.encodeToByteArray()).executeAsOne()
                    assertEquals(before.created_at, after.created_at)
                    assertEquals(before.last_used_at, after.last_used_at)
                    assertEquals(before.updated_at, after.updated_at)
                    assertEquals(before.credential_count, after.credential_count)
                }

                val olderSnapshot = relyingParty(rpId, 1_000L, 2_000L, name = "Older incoming snapshot")
                try {
                    dao.updateRelyingParty(olderSnapshot)
                } finally {
                    olderSnapshot.eraseSensitiveText()
                }
                val afterSnapshot =
                    database.relyingPartyQueries
                        .select_by_id(rpId.value.encodeToByteArray())
                        .executeAsOne()
                assertEquals(before.created_at, afterSnapshot.created_at)
                assertEquals(before.last_used_at, afterSnapshot.last_used_at)
                assertTrue(afterSnapshot.updated_at > before.updated_at)
                assertTrue(afterSnapshot.name.contentEquals("Older incoming snapshot".encodeToByteArray()))
                val mappedSnapshot = afterSnapshot.copy().toDomainModel()
                try {
                    assertEquals(CREATED_AT, mappedSnapshot.createdAt.toEpochMilliseconds())
                    assertEquals(LAST_USED_AT, mappedSnapshot.lastUsedAt?.toEpochMilliseconds())
                } finally {
                    mappedSnapshot.eraseSensitiveText()
                }

                val neverUsedId = RpId("https://never-used.timestamp.example")
                val neverUsed = relyingParty(neverUsedId, CREATED_AT, null, name = "Never used")
                dao.insertOrUpdateRelyingParty(neverUsed)
                neverUsed.eraseSensitiveText()
                val conflictingSnapshot = relyingParty(neverUsedId, 1_000L, 2_000L, name = "Rejected snapshot")
                try {
                    assertFailsWith<Exception> { dao.updateRelyingParty(conflictingSnapshot) }
                } finally {
                    conflictingSnapshot.eraseSensitiveText()
                }
                val afterRejectedSnapshot =
                    database.relyingPartyQueries
                        .select_by_id(neverUsedId.value.encodeToByteArray())
                        .executeAsOne()
                assertEquals(CREATED_AT, afterRejectedSnapshot.created_at)
                assertEquals(null, afterRejectedSnapshot.last_used_at)
                assertTrue(afterRejectedSnapshot.name.contentEquals("Never used".encodeToByteArray()))
            } finally {
                driver.close()
            }
        }

    @Test
    fun `bulk relying party update rolls back earlier rows when a later snapshot violates timestamp order`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            Fido2Database.Schema.create(driver)
            val database = Fido2Database(driver)
            val timeProvider = mockk<TimeProvider>()
            every { timeProvider.now() } returns Instant.fromEpochMilliseconds(CLOCK_BEFORE_CREATION)
            val metadata = mockk<CredentialMetadataProtectionService>()
            every { metadata.encryptRelyingPartyMetadata(any()) } returns byteArrayOf(3, 4)
            val dao = RelyingPartyDao(database, timeProvider, metadata)
            val firstId = RpId("https://first.timestamp.example")
            val secondId = RpId("https://second.timestamp.example")
            val firstOriginal = relyingParty(firstId, CREATED_AT, LAST_USED_AT, name = "First original")
            val secondOriginal = relyingParty(secondId, CREATED_AT, null, name = "Second original")
            try {
                dao.insertOrUpdateRelyingParty(firstOriginal)
                dao.insertOrUpdateRelyingParty(secondOriginal)
                firstOriginal.eraseSensitiveText()
                secondOriginal.eraseSensitiveText()
                val firstBefore =
                    database.relyingPartyQueries
                        .select_by_id(firstId.value.encodeToByteArray())
                        .executeAsOne()
                val secondBefore =
                    database.relyingPartyQueries
                        .select_by_id(secondId.value.encodeToByteArray())
                        .executeAsOne()
                val firstChange = relyingParty(firstId, CREATED_AT, LAST_USED_AT, name = "Must roll back")
                val invalidSecond = relyingParty(secondId, 1_000L, 2_000L, name = "Invalid second")

                try {
                    assertFailsWith<Exception> { dao.updateRelyingParties(listOf(firstChange, invalidSecond)) }
                } finally {
                    firstChange.eraseSensitiveText()
                    invalidSecond.eraseSensitiveText()
                }

                val firstAfter =
                    database.relyingPartyQueries
                        .select_by_id(firstId.value.encodeToByteArray())
                        .executeAsOne()
                val secondAfter =
                    database.relyingPartyQueries
                        .select_by_id(secondId.value.encodeToByteArray())
                        .executeAsOne()
                assertEquals(firstBefore.created_at, firstAfter.created_at)
                assertEquals(firstBefore.last_used_at, firstAfter.last_used_at)
                assertEquals(firstBefore.updated_at, firstAfter.updated_at)
                assertTrue(firstAfter.name.contentEquals("First original".encodeToByteArray()))
                assertEquals(secondBefore.created_at, secondAfter.created_at)
                assertEquals(secondBefore.last_used_at, secondAfter.last_used_at)
                assertEquals(secondBefore.updated_at, secondAfter.updated_at)
                assertTrue(secondAfter.name.contentEquals("Second original".encodeToByteArray()))
            } finally {
                driver.close()
            }
        }

    private fun relyingParty(
        id: RpId,
        createdAt: Long,
        lastUsedAt: Long?,
        name: String,
    ) = RelyingParty(
        id = id,
        name = name,
        iconUrl = null,
        credentialCount = 0,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        lastUsedAt = lastUsedAt?.let(Instant::fromEpochMilliseconds),
    )

    private companion object {
        const val CREATED_AT = 3_000L
        const val LAST_USED_AT = 3_500L
        const val CLOCK_BEFORE_CREATION = 2_000L
    }
}

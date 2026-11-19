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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

class RelyingPartyTimestampInvariantTest {
    private val ownedRelyingParties = mutableListOf<RelyingParty>()
    private val ownedRows = mutableListOf<Relying_party>()
    private val ownedQueryIds = mutableListOf<ByteArray>()

    @AfterTest
    fun tearDown() {
        ownedRows.forEach { row ->
            row.id.fill(0)
            row.name.fill(0)
            row.encrypted_metadata?.fill(0)
        }
        ownedRelyingParties.forEach(RelyingParty::eraseSensitiveText)
        ownedQueryIds.forEach { it.fill(0) }
        ownedRows.clear()
        ownedRelyingParties.clear()
        ownedQueryIds.clear()
    }

    private fun own(rp: RelyingParty): RelyingParty = rp.also(ownedRelyingParties::add)

    private fun own(row: Relying_party): Relying_party = row.also(ownedRows::add)

    private fun ownQueryId(bytes: ByteArray): ByteArray = bytes.also(ownedQueryIds::add)

    private fun selectRelyingParty(
        database: Fido2Database,
        id: RpId,
    ): Relying_party =
        own(
            database.relyingPartyQueries
                .select_by_id(ownQueryId(id.value.encodeToByteArray()))
                .executeAsOne(),
        )

    private fun assertNameEquals(
        actual: ByteArray,
        expectedText: String,
    ) {
        val expected = expectedText.encodeToByteArray()
        try {
            assertTrue(actual.contentEquals(expected))
        } finally {
            expected.fill(0)
        }
    }

    @Test
    fun `teardown erases retained database rows query keys and domain owners`() {
        val rowId = byteArrayOf(1, 2, 3)
        val rowName = byteArrayOf(4, 5, 6)
        val rowMetadata = byteArrayOf(7, 8)
        val row =
            own(
                Relying_party(
                    id = rowId,
                    created_at = CREATED_AT,
                    last_used_at = LAST_USED_AT,
                    updated_at = CREATED_AT,
                    credential_count = 1,
                    encrypted_metadata = rowMetadata,
                    icon_url = null,
                    is_blocked = 0,
                    name = rowName,
                ),
            )
        val queryId = ownQueryId(byteArrayOf(9, 10, 11))
        val relyingParty = relyingParty(RpId("https://synthetic.example"), CREATED_AT, LAST_USED_AT, "Synthetic")

        tearDown()

        assertContentEquals(ByteArray(3), rowId)
        assertContentEquals(ByteArray(3), rowName)
        assertContentEquals(ByteArray(2), rowMetadata)
        assertContentEquals(ByteArray(3), queryId)
        assertTrue(relyingParty.idText.all { it == '\u0000' })
        assertTrue(relyingParty.nameText.all { it == '\u0000' })
    }

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
                val before = selectRelyingParty(database, rpId)

                val operations: List<suspend () -> Unit> =
                    listOf(
                        { dao.updateCredentialCount(rpId, 1) },
                        { dao.incrementCredentialCount(rpId) },
                        { dao.decrementCredentialCount(rpId) },
                        { dao.updateLastUsedAt(rpId) },
                    )
                operations.forEach { operation ->
                    assertFailsWith<Exception> { operation() }
                    val after = selectRelyingParty(database, rpId)
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
                val afterSnapshot = selectRelyingParty(database, rpId)
                assertEquals(before.created_at, afterSnapshot.created_at)
                assertEquals(before.last_used_at, afterSnapshot.last_used_at)
                assertTrue(afterSnapshot.updated_at > before.updated_at)
                assertNameEquals(afterSnapshot.name, "Older incoming snapshot")
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
                val afterRejectedSnapshot = selectRelyingParty(database, neverUsedId)
                assertEquals(CREATED_AT, afterRejectedSnapshot.created_at)
                assertEquals(null, afterRejectedSnapshot.last_used_at)
                assertNameEquals(afterRejectedSnapshot.name, "Never used")
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
                val firstBefore = selectRelyingParty(database, firstId)
                val secondBefore = selectRelyingParty(database, secondId)
                val firstChange = relyingParty(firstId, CREATED_AT, LAST_USED_AT, name = "Must roll back")
                val invalidSecond = relyingParty(secondId, 1_000L, 2_000L, name = "Invalid second")

                try {
                    assertFailsWith<Exception> { dao.updateRelyingParties(listOf(firstChange, invalidSecond)) }
                } finally {
                    firstChange.eraseSensitiveText()
                    invalidSecond.eraseSensitiveText()
                }

                val firstAfter = selectRelyingParty(database, firstId)
                val secondAfter = selectRelyingParty(database, secondId)
                assertEquals(firstBefore.created_at, firstAfter.created_at)
                assertEquals(firstBefore.last_used_at, firstAfter.last_used_at)
                assertEquals(firstBefore.updated_at, firstAfter.updated_at)
                assertNameEquals(firstAfter.name, "First original")
                assertEquals(secondBefore.created_at, secondAfter.created_at)
                assertEquals(secondBefore.last_used_at, secondAfter.last_used_at)
                assertEquals(secondBefore.updated_at, secondAfter.updated_at)
                assertNameEquals(secondAfter.name, "Second original")
            } finally {
                driver.close()
            }
        }

    private fun relyingParty(
        id: RpId,
        createdAt: Long,
        lastUsedAt: Long?,
        name: String,
    ): RelyingParty =
        own(
            RelyingParty(
                id = id,
                name = name,
                iconUrl = null,
                credentialCount = 0,
                createdAt = Instant.fromEpochMilliseconds(createdAt),
                lastUsedAt = lastUsedAt?.let(Instant::fromEpochMilliseconds),
            ),
        )

    private companion object {
        const val CREATED_AT = 3_000L
        const val LAST_USED_AT = 3_500L
        const val CLOCK_BEFORE_CREATION = 2_000L
    }
}

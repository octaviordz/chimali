package com.chimali.fido2.data.repository

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.model.RelyingParty
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.database.Relying_party
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

@Req("NFR-SEC-041", "TR-AUTH-031")
class RelyingPartyRepositoryFlowOwnershipTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `t157 successful batch emission transfers live RP owners and erases rows`() =
        runTest {
            val row = row()
            val dao = mockk<RelyingPartyDao>()
            every { dao.getAllRelyingParties() } returns flowOf(listOf(row))
            val repository = RelyingPartyRepositoryImpl(dao)
            var retained: List<RelyingParty>? = null

            repository.getAllRelyingParties().collect { batch ->
                retained = batch
                assertEquals("https://synthetic.example", text(batch.single().idText))
                assertEquals("Synthetic RP", text(batch.single().nameText))
            }

            assertTrue(row.id.all { it == 0.toByte() })
            assertTrue(row.name.all { it == 0.toByte() })
            val received = requireNotNull(retained).single()
            assertEquals("https://synthetic.example", text(received.idText))
            assertEquals("Synthetic RP", text(received.nameText))
            received.eraseSensitiveText()
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `t157 failed downstream emission erases retained RP owners and source rows`() =
        runTest {
            val row = row()
            val dao = mockk<RelyingPartyDao>()
            every { dao.getAllRelyingParties() } returns flowOf(listOf(row))
            val repository = RelyingPartyRepositoryImpl(dao)
            var retained: List<RelyingParty>? = null

            assertFailsWith<IllegalStateException> {
                repository.getAllRelyingParties().collect { batch ->
                    retained = batch
                    assertOwned(batch.single())
                    error("synthetic downstream failure")
                }
            }

            assertErased(requireNotNull(retained).single())
            assertRowsErased(row)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `t157 cancelled downstream emission erases retained RP owners and source rows`() =
        runTest {
            val row = row()
            val dao = mockk<RelyingPartyDao>()
            every { dao.getAllRelyingParties() } returns flowOf(listOf(row))
            val repository = RelyingPartyRepositoryImpl(dao)
            var retained: List<RelyingParty>? = null

            assertFailsWith<CancellationException> {
                repository.getAllRelyingParties().collect { batch ->
                    retained = batch
                    assertOwned(batch.single())
                    throw CancellationException("synthetic downstream cancellation")
                }
            }

            assertErased(requireNotNull(retained).single())
            assertRowsErased(row)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `t157 partial RP mapping failure erases earlier owners and all source rows`() =
        runTest {
            val first = row()
            val malformed = row(createdAt = 3_000, updatedAt = 2_000)
            val dao = mockk<RelyingPartyDao>()
            every { dao.getAllRelyingParties() } returns flowOf(listOf(first, malformed))
            val repository = RelyingPartyRepositoryImpl(dao)
            val retained = mutableListOf<RelyingParty>()
            repository.onRelyingPartyCreatedForTest = retained::add

            assertFailsWith<IllegalArgumentException> { repository.getAllRelyingParties().collect { } }

            assertEquals(1, retained.size)
            assertErased(retained.single())
            assertRowsErased(first, malformed)
        }

    private fun row(
        createdAt: Long = 1_000,
        updatedAt: Long = 1_000,
    ) = Relying_party(
        id = "https://synthetic.example".encodeToByteArray(),
        created_at = createdAt,
        last_used_at = null,
        updated_at = updatedAt,
        credential_count = 0,
        encrypted_metadata = null,
        icon_url = null,
        is_blocked = 0,
        name = "Synthetic RP".encodeToByteArray(),
    )

    private fun assertOwned(rp: RelyingParty) {
        listOf(rp.idText, rp.nameText).forEach { owner ->
            val chars = owner.copyCharacters()
            try {
                assertTrue(chars.any { it != '\u0000' })
            } finally {
                chars.fill('\u0000')
            }
        }
    }

    private fun text(owner: com.chimali.core.domain.model.SensitiveText): String {
        val chars = owner.copyCharacters()
        return try {
            chars.concatToString()
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun assertErased(rp: RelyingParty) {
        listOf(rp.idText, rp.nameText).forEach { owner ->
            val chars = owner.copyCharacters()
            try {
                assertTrue(chars.all { it == '\u0000' })
            } finally {
                chars.fill('\u0000')
            }
        }
    }

    private fun assertRowsErased(vararg rows: Relying_party) {
        rows.forEach { row ->
            assertContentEquals(ByteArray(row.id.size), row.id)
            assertContentEquals(ByteArray(row.name.size), row.name)
        }
    }
}

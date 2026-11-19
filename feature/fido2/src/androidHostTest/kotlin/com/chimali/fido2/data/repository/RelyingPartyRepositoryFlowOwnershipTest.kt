package com.chimali.fido2.data.repository

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.database.Relying_party
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock

@Req("NFR-SEC-041", "TR-AUTH-031")
class RelyingPartyRepositoryFlowOwnershipTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `t158 DAO cancellation propagates from result-returning operations`() =
        runTest {
            val rpId = RpId("https://synthetic.example")
            val rp =
                RelyingParty(
                    id = rpId,
                    name = "Synthetic RP",
                    iconUrl = null,
                    credentialCount = 0,
                    createdAt = Clock.System.now(),
                )
            val dao = mockk<RelyingPartyDao>()
            coEvery { dao.insertOrUpdateRelyingParty(any()) } answers {
                throw CancellationException("synthetic save cancellation")
            }
            coEvery { dao.deleteRelyingParty(any()) } answers {
                throw CancellationException("synthetic delete cancellation")
            }
            coEvery { dao.updateCredentialCount(any(), any()) } answers {
                throw CancellationException("synthetic count-update cancellation")
            }
            val repository = RelyingPartyRepositoryImpl(dao)

            try {
                assertFailsWith<CancellationException> { repository.saveRelyingParty(rp) }
                assertFailsWith<CancellationException> { repository.deleteRelyingParty(rpId) }
                assertFailsWith<CancellationException> { repository.updateCredentialCount(rpId, 1) }
                assertTrue(rp.idText.contentEquals(rpId.value))
                assertTrue(rp.nameText.contentEquals("Synthetic RP"))
            } finally {
                rp.eraseSensitiveText()
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `result operations do not retain DAO failure details`() =
        runTest {
            val rpId = RpId("https://synthetic.example")
            val rp =
                RelyingParty(
                    id = rpId,
                    name = "Synthetic RP",
                    iconUrl = null,
                    credentialCount = 0,
                    createdAt = Clock.System.now(),
                )
            val dao = mockk<RelyingPartyDao>()
            coEvery { dao.insertOrUpdateRelyingParty(any()) } throws
                IllegalStateException("synthetic-rp-dao-marker")
            coEvery { dao.deleteRelyingParty(any()) } throws
                IllegalStateException("synthetic-rp-dao-marker")
            coEvery { dao.updateCredentialCount(any(), any()) } throws
                IllegalStateException("synthetic-rp-dao-marker")
            val repository = RelyingPartyRepositoryImpl(dao)

            val failures =
                listOf(
                    repository.saveRelyingParty(rp).exceptionOrNull(),
                    repository.deleteRelyingParty(rpId).exceptionOrNull(),
                    repository.updateCredentialCount(rpId, 1).exceptionOrNull(),
                )

            failures.forEach { failure ->
                assertEquals("Relying-party operation failed", failure?.message)
                assertNull(failure?.cause)
                assertFalse(failure.toString().contains("synthetic-rp-dao-marker"))
            }
        }

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
    fun `failed RP batch admission erases the not-yet-admitted model`() =
        runTest {
            val model =
                RelyingParty(
                    id = RpId("https://synthetic.example"),
                    name = "Synthetic RP",
                    iconUrl = null,
                    credentialCount = 0,
                    createdAt = Clock.System.now(),
                )

            assertFailsWith<IllegalStateException> {
                admitOwnedRelyingParty(model) { false }
            }

            assertErased(model)
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

package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.getOrThrow
import com.chimali.core.common.result.isFailure
import com.chimali.core.common.result.isSuccess
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.time.TimeProvider
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.testing.CapturedDiagnostics
import com.chimali.fido2.testing.testCredentialIdFromLabel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

/**
 * T082 — Unit tests for [SelectCredentialUseCase].
 */
class SelectCredentialUseCaseTest {
    private lateinit var useCase: SelectCredentialUseCase
    private val ownedSummaries = mutableListOf<CredentialSummary>()

    @BeforeTest
    fun setup() {
        useCase = SelectCredentialUseCase()
    }

    @AfterTest
    fun clearTestOwners() {
        ownedSummaries.forEach(CredentialSummary::eraseSensitiveText)
        ownedSummaries.clear()
    }

    private fun createSummary(
        id: String,
        rpId: String = "https://example.com",
        lastUsedAt: Instant = TimeProvider().now(),
    ): CredentialSummary =
        CredentialSummary
            .fromText(
                id = id,
                rpId = rpId,
                credentialId = testCredentialIdFromLabel(id),
                lastUsedAt = lastUsedAt,
                coseAlgorithm = PasskeyCredential.COSE_ES256,
                derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                storedPublicKeyEncoding = byteArrayOf(),
            ).also(ownedSummaries::add)

    // ── Empty candidates ─────────────────────────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `returns failure when candidates list is empty`() =
        runTest {
            val result = useCase(emptyList())

            assertTrue(result.isFailure)
            val error = assertIs<DomainError.NotFound>(assertIs<Outcome.Error<*>>(result).error)
            assertEquals("No eligible credentials", error.message)
            assertNull(error.cause)
        }

    // ── Single credential ────────────────────────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `selection does not disclose the RP association in errors or diagnostics`() =
        runTest {
            val marker = "synthetic-private-rp"
            val rpId = "https://$marker.example.com"
            val older = createSummary("older", rpId, Instant.fromEpochMilliseconds(1000))
            val newer = createSummary("newer", rpId, Instant.fromEpochMilliseconds(2000))
            CapturedDiagnostics().use { logs ->
                val error = assertIs<Outcome.Error<*>>(useCase(emptyList())).error
                assertEquals("No eligible credentials", error.message)
                assertNull(error.cause)
                assertEquals(
                    "newer",
                    assertIs<Outcome.Success<CredentialSummary>>(
                        useCase(listOf(older, newer)),
                    ).data.id,
                )
                assertTrue(logs.size > 0)
                logs.assertNoSensitiveValues(marker)
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `auto-selects single credential`() =
        runTest {
            val summary = createSummary("cred1")
            val result = useCase(listOf(summary))

            assertTrue(result.isSuccess)
            assertEquals("cred1", result.getOrThrow().id)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `returns the selected summary owner by reference and leaves cleanup to caller`() =
        runTest {
            val summary = createSummary("selected")
            val result = assertIs<Outcome.Success<CredentialSummary>>(useCase(listOf(summary))).data

            assertSame(summary, result)
            assertTrue(summary.rpIdText.any { it != '\u0000' })

            clearTestOwners()

            assertTrue(summary.rpIdText.all { it == '\u0000' })
        }

    // ── Multiple credentials ─────────────────────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `selects most recently used credential from multiple`() =
        runTest {
            val now = TimeProvider().now()
            val older = createSummary("cred1", lastUsedAt = now - 3600.seconds)
            val newer = createSummary("cred2", lastUsedAt = now)

            val result = useCase(listOf(older, newer))

            assertTrue(result.isSuccess)
            assertEquals("cred2", result.getOrThrow().id)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `selects from three candidates without error`() =
        runTest {
            val c1 = createSummary("cred1")
            val c2 = createSummary("cred2")
            val c3 = createSummary("cred3")

            val result = useCase(listOf(c1, c2, c3))

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().id in listOf("cred1", "cred2", "cred3"))
        }
}

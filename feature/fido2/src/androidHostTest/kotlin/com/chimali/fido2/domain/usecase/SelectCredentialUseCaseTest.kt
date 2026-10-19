package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.getOrThrow
import com.chimali.core.common.result.isFailure
import com.chimali.core.common.result.isSuccess
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.testing.CapturedDiagnostics
import com.chimali.fido2.testing.testCredentialIdFromLabel
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

/**
 * T082 — Unit tests for [SelectCredentialUseCase].
 */
class SelectCredentialUseCaseTest {
    private lateinit var useCase: SelectCredentialUseCase

    @BeforeTest
    fun setup() {
        useCase = SelectCredentialUseCase()
    }

    private companion object {
        private const val HASH_SIZE_32 = 32
    }

    private fun createOptions(rpId: String = "https://example.com"): GetAssertionOptions =
        GetAssertionOptions.create(
            rpId = RpId(rpId),
            clientDataHash = ByteArray(HASH_SIZE_32),
        )

    private fun createSummary(
        id: String,
        rpId: String = "https://example.com",
        lastUsedAt: Instant = TimeProvider().now(),
    ): CredentialSummary =
        CredentialSummary(
            id = id,
            rpId = RpId(rpId),
            credentialId = testCredentialIdFromLabel(id),
            lastUsedAt = lastUsedAt,
            coseAlgorithm = PasskeyCredential.COSE_ES256,
            derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
            storedPublicKeyEncoding = byteArrayOf(),
        )

    // ── Empty candidates ─────────────────────────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `returns failure when candidates list is empty`() =
        runTest {
            val result = useCase(emptyList(), createOptions())

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
            val options = createOptions("$marker.example.com")
            val older = createSummary("older", options.rpId.value, Instant.fromEpochMilliseconds(1000))
            val newer = createSummary("newer", options.rpId.value, Instant.fromEpochMilliseconds(2000))
            CapturedDiagnostics().use { logs ->
                val error = assertIs<Outcome.Error<*>>(useCase(emptyList(), options)).error
                assertEquals("No eligible credentials", error.message)
                assertNull(error.cause)
                assertEquals(
                    "newer",
                    assertIs<Outcome.Success<CredentialSummary>>(
                        useCase(listOf(older, newer), options),
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
            val result = useCase(listOf(summary), createOptions())

            assertTrue(result.isSuccess)
            assertEquals("cred1", result.getOrThrow().id)
        }

    // ── Multiple credentials ─────────────────────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `selects most recently used credential from multiple`() =
        runTest {
            val now = TimeProvider().now()
            val older = createSummary("cred1", lastUsedAt = now - 3600.seconds)
            val newer = createSummary("cred2", lastUsedAt = now)

            val result = useCase(listOf(older, newer), createOptions())

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

            val result = useCase(listOf(c1, c2, c3), createOptions())

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().id in listOf("cred1", "cred2", "cred3"))
        }
}

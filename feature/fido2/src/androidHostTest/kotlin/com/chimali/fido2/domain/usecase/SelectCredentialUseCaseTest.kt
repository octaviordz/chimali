package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.exceptionOrNull
import com.chimali.core.common.result.getOrThrow
import com.chimali.core.common.result.isFailure
import com.chimali.core.common.result.isSuccess
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
            credentialId = CredentialId.fromEncoded(id),
            lastUsedAt = lastUsedAt,
            coseAlgorithm = PasskeyCredential.COSE_ES256,
        )

    // ── Empty candidates ─────────────────────────────────────────────────────

    @Test
    fun `returns failure when candidates list is empty`() =
        runTest {
            val result = useCase(emptyList(), createOptions())

            assertTrue(result.isFailure)
            assertIs<Fido2Exception.CredentialNotFound>(result.exceptionOrNull())
        }

    // ── Single credential ────────────────────────────────────────────────────

    @Test
    fun `auto-selects single credential`() =
        runTest {
            val summary = createSummary("cred1")
            val result = useCase(listOf(summary), createOptions())

            assertTrue(result.isSuccess)
            assertEquals("cred1", result.getOrThrow().id)
        }

    // ── Multiple credentials ─────────────────────────────────────────────────

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

package com.chimali.fido2.domain.usecase

import android.util.Log
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import io.mockk.every
import io.mockk.mockkStatic
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * T082 — Unit tests for [SelectCredentialUseCase].
 */
class SelectCredentialUseCaseTest {
    private lateinit var useCase: SelectCredentialUseCase

    @BeforeTest
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        useCase = SelectCredentialUseCase()
    }

    private companion object {
        private const val HASH_SIZE_32 = 32
        private const val HOUR_IN_SECONDS_3600 = 3600L
    }

    private fun createOptions(rpId: String = "https://example.com"): GetAssertionOptions {
        return GetAssertionOptions.create(
            rpId = rpId,
            clientDataHash = ByteArray(HASH_SIZE_32),
        )
    }

    private fun createSummary(
        id: String,
        rpId: String = "https://example.com",
        lastUsedAt: Instant = Instant.now(),
    ): com.chimali.fido2.domain.model.CredentialSummary {
        return com.chimali.fido2.domain.model.CredentialSummary(
            id = id,
            rpId = rpId,
            credentialId = id.toByteArray(),
            lastUsedAt = lastUsedAt,
            coseAlgorithm = PasskeyCredential.COSE_ES256,
        )
    }

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
            val older = createSummary("cred1", lastUsedAt = Instant.now().minusSeconds(HOUR_IN_SECONDS_3600))
            val newer = createSummary("cred2", lastUsedAt = Instant.now())

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

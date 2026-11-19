package com.chimali.fido2.domain.usecase

import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.data.crypto.ClientDataHashService
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.testing.testCredentialIdFromLabel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Owner cleanup for summaries rejected by GetAssertion allow-list filtering. */
class GetAssertionCandidateOwnershipTest {
    private val repository = mockk<CredentialRepository>()
    private val cryptoService = mockk<Fido2CryptoService>()
    private val useCase =
        GetAssertionUseCase(
            repository,
            mockk<UserVerificationService>(),
            mockk<SelectCredentialUseCase>(),
            cryptoService,
            ClientDataHashService(),
        )

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `allow-list lookup erases rejected owners and transfers selected owner`() =
        runTest {
            val rpId = RpId("https://example.com")
            val selected = createSummary("allow-listed-owner", rpId)
            val rejected = createSummary("excluded-owner", rpId)
            val clientDataHash = ByteArray(32)
            var options: GetAssertionOptions? = null
            var candidates: List<CredentialSummary>? = null
            coEvery {
                repository.getCredentialSummariesForRpText(match { it.contentEquals(rpId.value) })
            } returns Outcome.Success(listOf(selected, rejected))
            try {
                options =
                    GetAssertionOptions.create(
                        rpId = rpId,
                        clientDataHash = clientDataHash,
                        allowCredentials = listOf(PublicKeyCredentialDescriptor.create(id = selected.credentialId)),
                    )

                candidates = useCase.findCandidateSummaries(requireNotNull(options))

                assertEquals(listOf(selected), candidates)
                assertTrue(rejected.rpIdText.all { it == '\u0000' })
                assertTrue(selected.rpIdText.any { it != '\u0000' })
                coVerify(exactly = 0) { cryptoService.sign(any(), any(), any()) }
            } finally {
                candidates?.forEach(CredentialSummary::eraseSensitiveText)
                selected.eraseSensitiveText()
                rejected.eraseSensitiveText()
                options?.eraseSensitiveText()
                clientDataHash.fill(0)
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `result wrapper failure erases selected and excluded candidate owners`() {
        val rpId = RpId("https://example.com")
        val selected = createSummary("wrapper-failure-selected", rpId)
        val rejected = createSummary("wrapper-failure-rejected", rpId)

        try {
            assertFailsWith<IllegalStateException> {
                createOwnedCandidateResult(
                    all = listOf(selected, rejected),
                    selectCandidates = { listOf(it.first()) },
                    createResult = { throw IllegalStateException("synthetic result wrapper failure") },
                )
            }

            assertTrue(selected.rpIdText.all { it == '\u0000' })
            assertTrue(rejected.rpIdText.all { it == '\u0000' })
        } finally {
            selected.eraseSensitiveText()
            rejected.eraseSensitiveText()
        }
    }

    private fun createSummary(
        id: String,
        rpId: RpId,
    ): CredentialSummary =
        CredentialSummary.fromRpId(
            id = id,
            rpId = rpId,
            credentialId = testCredentialIdFromLabel(id),
            lastUsedAt = TimeProvider().now(),
            coseAlgorithm = PasskeyCredential.COSE_ES256,
            derivationProfile = CredentialKeyDescriptor.profileFor(PasskeyCredential.COSE_ES256),
            storedPublicKeyEncoding = byteArrayOf(),
        )
}

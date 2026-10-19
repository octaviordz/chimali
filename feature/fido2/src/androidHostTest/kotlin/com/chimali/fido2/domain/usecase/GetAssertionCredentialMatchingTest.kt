package com.chimali.fido2.domain.usecase

import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.isSuccess
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.data.crypto.ClientDataHashService
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.service.BiometricStrength
import com.chimali.fido2.domain.service.BiometricType
import com.chimali.fido2.domain.service.UserVerificationAvailability
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.util.matchesCredentialId
import com.chimali.fido2.testing.testCredentialIdFromLabel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Verifies constant-time identity matching at the assertion candidate boundary. */
@Req("NFR-SEC-041", "TR-AUTH-031")
class GetAssertionCredentialMatchingTest {
    private val rpId = RpId("https://example.com")
    private val repository = mockk<CredentialRepository>()
    private val selector = mockk<SelectCredentialUseCase>()
    private val crypto = mockk<Fido2CryptoService>()
    private val verification = mockk<UserVerificationService>()
    private val useCase =
        GetAssertionUseCase(
            repository,
            verification,
            selector,
            crypto,
            ClientDataHashService(),
        )

    @BeforeTest
    fun setUp() {
        coEvery { crypto.sign(any(), any(), any()) } returns Outcome.Success(ByteArray(72) { 0x30 })
        coEvery { verification.getUserVerificationAvailability() } returns
            UserVerificationAvailability(
                isBiometricAvailable = true,
                isPinAvailable = true,
                isDeviceLockAvailable = false,
                supportedBiometricTypes = listOf(BiometricType.FINGERPRINT),
                maxPinLength = 8,
                minPinLength = 4,
                biometricStrength = BiometricStrength.STRONG,
            )
    }

    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `allow-list candidate lookup keeps only matching credential IDs`() =
        runTest {
            val allowed = summary("allow-listed")
            val excluded = summary("not-allow-listed")
            coEvery {
                repository.getCredentialSummariesForRpText(match { it.contentEquals(rpId.value) })
            } returns Outcome.Success(listOf(allowed, excluded))

            val result =
                useCase.findCandidateSummaries(
                    options(listOf(PublicKeyCredentialDescriptor.create(id = allowed.credentialId))),
                )

            assertEquals(listOf(allowed), result)
            coVerify(exactly = 0) { crypto.sign(any(), any(), any()) }
        }

    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `allow-list matching admits only byte-equal candidate and derives only selected credential`() =
        runTest {
            val targetBytes = ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { (it + 1).toByte() }
            val targetId = CredentialId.fromByteArray(targetBytes)
            val firstByteMismatch = targetBytes.copyOf().also { it[0] = 0 }
            val lastByteMismatch = targetBytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            val differentLength = targetBytes + 0x7f.toByte()
            val first = summary("id-mismatch-first", CredentialId.fromByteArray(firstByteMismatch))
            val last = summary("id-mismatch-last", CredentialId.fromByteArray(lastByteMismatch))
            val differentSize = summary("id-mismatch-size", CredentialId.fromByteArray(differentLength))
            val selected = summary("id-match", targetId)
            coEvery {
                repository.getCredentialSummariesForRpText(match { it.contentEquals(rpId.value) })
            } returns Outcome.Success(listOf(first, last, differentSize, selected))
            coEvery { selector(any(), any()) } returns Outcome.Success(selected)
            coEvery { repository.getSignCount(selected.credentialId) } returns Outcome.Success(5L)
            coEvery { repository.updateSignCount(selected.credentialId, any()) } returns Outcome.Success(Unit)

            val result =
                useCase(
                    options(
                        listOf(PublicKeyCredentialDescriptor.create(id = targetId)),
                        UserVerificationRequirement.DISCOURAGED,
                    ),
                )

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { selector(listOf(selected), any()) }
            coVerify(exactly = 1) {
                crypto.sign(match { it.credentialId.matchesCredentialId(selected.credentialId) }, any(), any())
            }
            coVerify(exactly = 0) {
                crypto.sign(match { it.credentialId.matchesCredentialId(first.credentialId) }, any(), any())
            }
            coVerify(exactly = 0) {
                crypto.sign(match { it.credentialId.matchesCredentialId(last.credentialId) }, any(), any())
            }
            coVerify(exactly = 0) {
                crypto.sign(match { it.credentialId.matchesCredentialId(differentSize.credentialId) }, any(), any())
            }
        }

    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `credProtect policy two accepts only a matching allow-list or completed verification`() =
        runTest {
            val protectedCredential =
                summary(
                    "policy-two",
                    CredentialId.fromByteArray(ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { it.toByte() }),
                ).copy(credProtectPolicy = 2)
            coEvery {
                repository.getCredentialSummariesForRpText(match { it.contentEquals(rpId.value) })
            } returns Outcome.Success(listOf(protectedCredential))
            coEvery { selector(any(), any()) } returns Outcome.Success(protectedCredential)
            coEvery { repository.getSignCount(protectedCredential.credentialId) } returns Outcome.Success(5L)
            coEvery {
                repository.updateSignCount(protectedCredential.credentialId, any())
            } returns Outcome.Success(Unit)

            val absent = useCase(options(), userVerificationPerformed = false)
            val mismatch =
                useCase(
                    options(listOf(PublicKeyCredentialDescriptor.create(id = testCredentialIdFromLabel("other")))),
                    userVerificationPerformed = false,
                )
            val paddedId = CredentialId.fromEncoded(protectedCredential.credentialId.encoded + "==")
            val equal =
                useCase(
                    options(listOf(PublicKeyCredentialDescriptor.create(id = paddedId))),
                    userVerificationPerformed = false,
                )
            val verified = useCase(options(), userVerificationPerformed = true)

            assertIs<Outcome.Error<*>>(absent)
            assertIs<Outcome.Error<*>>(mismatch)
            assertTrue(equal.isSuccess)
            assertTrue(verified.isSuccess)
            coVerify(exactly = 2) { selector(listOf(protectedCredential), any()) }
            coVerify(exactly = 2) { crypto.sign(any(), any(), any()) }
        }

    private fun options(
        allowCredentials: List<PublicKeyCredentialDescriptor>? = null,
        uv: UserVerificationRequirement = UserVerificationRequirement.PREFERRED,
    ): GetAssertionOptions =
        GetAssertionOptions.create(
            rpId = rpId,
            clientDataHash = ByteArray(32),
            allowCredentials = allowCredentials,
            userVerification = uv,
        )

    private fun summary(
        id: String,
        credentialId: CredentialId = testCredentialIdFromLabel(id),
    ): CredentialSummary =
        CredentialSummary(
            id = id,
            rpId = rpId,
            credentialId = credentialId,
            lastUsedAt = TimeProvider().now(),
            coseAlgorithm = PasskeyCredential.COSE_ES256,
            derivationProfile = CredentialKeyDescriptor.profileFor(PasskeyCredential.COSE_ES256),
            storedPublicKeyEncoding = byteArrayOf(),
        )
}

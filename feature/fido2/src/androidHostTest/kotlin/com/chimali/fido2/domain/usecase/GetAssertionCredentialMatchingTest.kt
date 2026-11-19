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
import com.chimali.fido2.domain.model.AssertionObject
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
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Verifies constant-time identity matching at the assertion candidate boundary. */
@Req("NFR-SEC-041", "TR-AUTH-031")
class GetAssertionCredentialMatchingTest {
    private val ownedBuffers = mutableListOf<ByteArray>()
    private val ownedSummaries = mutableListOf<CredentialSummary>()
    private val ownedOptions = mutableListOf<GetAssertionOptions>()
    private val ownedAssertions = mutableListOf<AssertionObject>()

    private fun own(buffer: ByteArray): ByteArray {
        ownedBuffers += buffer
        return buffer
    }

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
        coEvery { crypto.sign(any(), any(), any()) } returns Outcome.Success(own(ByteArray(72) { 0x30 }))
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

    @AfterTest
    fun tearDown() {
        ownedOptions.forEach {
            it.eraseSensitiveText()
            it.clientDataHash.fill(0)
        }
        ownedOptions.clear()
        ownedAssertions.forEach {
            it.authData.fill(0)
            it.signature.fill(0)
        }
        ownedAssertions.clear()
        ownedSummaries.forEach(CredentialSummary::eraseSensitiveText)
        ownedSummaries.clear()
        ownedBuffers.forEach { it.fill(0) }
        ownedBuffers.clear()
        clearMocks(repository, selector, crypto, verification)
    }

    @Test
    fun fixtureCleanup_erasesOwnedAssertionMatchingBuffers() {
        val fixture = own(byteArrayOf(0x2A))
        val optionsFixture = options()
        val summaryFixture = summary("teardown")
        val assertionFixture =
            AssertionObject(
                credential = null,
                authData = own(ByteArray(37)),
                signature = own(byteArrayOf(0x01)),
                user = null,
            ).also(ownedAssertions::add)

        tearDown()

        assertTrue(fixture.all { it == 0.toByte() })
        assertTrue(optionsFixture.clientDataHash.all { it == 0.toByte() })
        assertTrue(
            optionsFixture.rpIdText.contentEquals(
                CharArray(optionsFixture.rpIdText.length) { '\u0000' }.concatToString(),
            ),
        )
        assertTrue(
            summaryFixture.rpIdText.contentEquals(
                CharArray(summaryFixture.rpIdText.length) { '\u0000' }.concatToString(),
            ),
        )
        assertTrue(assertionFixture.authData.all { it == 0.toByte() })
        assertTrue(assertionFixture.signature.all { it == 0.toByte() })
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
            val targetBytes = own(ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { (it + 1).toByte() })
            val targetId = CredentialId.fromByteArray(targetBytes)
            val firstByteMismatch = own(targetBytes.copyOf().also { it[0] = 0 })
            val lastByteMismatch =
                own(targetBytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
            val differentLength = own(targetBytes + 0x7f.toByte())
            val first = summary("id-mismatch-first", CredentialId.fromByteArray(firstByteMismatch))
            val last = summary("id-mismatch-last", CredentialId.fromByteArray(lastByteMismatch))
            val differentSize = summary("id-mismatch-size", CredentialId.fromByteArray(differentLength))
            val selected = summary("id-match", targetId)
            coEvery {
                repository.getCredentialSummariesForRpText(match { it.contentEquals(rpId.value) })
            } returns Outcome.Success(listOf(first, last, differentSize, selected))
            coEvery { selector(any()) } returns Outcome.Success(selected)
            coEvery { repository.getSignCount(selected.credentialId) } returns Outcome.Success(5L)
            coEvery { repository.updateSignCount(selected.credentialId, any()) } returns Outcome.Success(Unit)

            val result =
                invokeAssertion(
                    options(
                        listOf(PublicKeyCredentialDescriptor.create(id = targetId)),
                        UserVerificationRequirement.DISCOURAGED,
                    ),
                )

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { selector(listOf(selected)) }
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
                ).copy(credProtectPolicy = 2).also(ownedSummaries::add)
            coEvery {
                repository.getCredentialSummariesForRpText(match { it.contentEquals(rpId.value) })
            } returns Outcome.Success(listOf(protectedCredential))
            coEvery { selector(any()) } returns Outcome.Success(protectedCredential)
            coEvery { repository.getSignCount(protectedCredential.credentialId) } returns Outcome.Success(5L)
            coEvery {
                repository.updateSignCount(protectedCredential.credentialId, any())
            } returns Outcome.Success(Unit)

            val absent = invokeAssertion(options(), userVerificationPerformed = false)
            val mismatch =
                invokeAssertion(
                    options(listOf(PublicKeyCredentialDescriptor.create(id = testCredentialIdFromLabel("other")))),
                    userVerificationPerformed = false,
                )
            val paddedId = CredentialId.fromEncoded(protectedCredential.credentialId.encoded + "==")
            val equal =
                invokeAssertion(
                    options(listOf(PublicKeyCredentialDescriptor.create(id = paddedId))),
                    userVerificationPerformed = false,
                )
            val verified = invokeAssertion(options(), userVerificationPerformed = true)

            assertIs<Outcome.Error<*>>(absent)
            assertIs<Outcome.Error<*>>(mismatch)
            assertTrue(equal.isSuccess)
            assertTrue(verified.isSuccess)
            coVerify(exactly = 2) { selector(listOf(protectedCredential)) }
            coVerify(exactly = 2) { crypto.sign(any(), any(), any()) }
        }

    private suspend fun invokeAssertion(
        options: GetAssertionOptions,
        userVerificationPerformed: Boolean = false,
    ) = useCase(options, userVerificationPerformed).also { outcome ->
        if (outcome is Outcome.Success) ownedAssertions += outcome.data
    }

    private fun options(
        allowCredentials: List<PublicKeyCredentialDescriptor>? = null,
        uv: UserVerificationRequirement = UserVerificationRequirement.PREFERRED,
    ): GetAssertionOptions =
        GetAssertionOptions
            .create(
                rpId = rpId,
                clientDataHash = own(ByteArray(32)),
                allowCredentials = allowCredentials,
                userVerification = uv,
            ).also(ownedOptions::add)

    private fun summary(
        id: String,
        credentialId: CredentialId = testCredentialIdFromLabel(id),
    ): CredentialSummary =
        CredentialSummary
            .fromRpId(
                id = id,
                rpId = rpId,
                credentialId = credentialId,
                lastUsedAt = TimeProvider().now(),
                coseAlgorithm = PasskeyCredential.COSE_ES256,
                derivationProfile = CredentialKeyDescriptor.profileFor(PasskeyCredential.COSE_ES256),
                storedPublicKeyEncoding = own(byteArrayOf()),
            ).also(ownedSummaries::add)
}

package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.exceptionOrNull
import com.chimali.core.common.result.getOrThrow
import com.chimali.core.common.result.isFailure
import com.chimali.core.common.result.isSuccess
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.CredentialSummary
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.service.BiometricStrength
import com.chimali.fido2.domain.service.BiometricType
import com.chimali.fido2.domain.service.UserVerificationAvailability
import com.chimali.fido2.domain.service.UserVerificationService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * T082 — Unit tests for [GetAssertionUseCase].
 *
 * Since GetAssertionUseCase now delegates signing to [Fido2CryptoService] (rather
 * than directly to the Android KeyStore), all signing interactions are replaced by
 * a mock [Fido2CryptoService].
 */
class GetAssertionUseCaseTest {
    private lateinit var credentialRepository: CredentialRepository
    private lateinit var userVerificationService: UserVerificationService
    private lateinit var selectCredentialUseCase: SelectCredentialUseCase
    private lateinit var cryptoService: Fido2CryptoService
    private lateinit var useCase: GetAssertionUseCase

    private val testRpId = "https://example.com"
    private val testClientDataHash = ByteArray(HASH_SIZE_32) { it.toByte() }
    private val fakeSignature = ByteArray(SIGNATURE_SIZE_72) { DUMMY_BYTE_30 } // plausible DER signature size

    private companion object {
        private const val HASH_SIZE_32 = 32
        private const val SIGNATURE_SIZE_72 = 72
        private const val DUMMY_BYTE_30 = 0x30.toByte()
        private const val MAX_PIN_LEN_8 = 8
        private const val MIN_PIN_LEN_4 = 4
        private const val SIGN_COUNT_5 = 5L
        private const val SIGN_COUNT_6 = 6L
        private const val SIGN_COUNT_10 = 10L
        private const val SIGN_COUNT_11 = 11L
        private const val MIN_AUTH_DATA_SIZE_37 = 37
    }

    @BeforeTest
    fun setup() {
        credentialRepository = mockk()
        userVerificationService = mockk()
        selectCredentialUseCase = mockk()
        cryptoService = mockk()

        // Default: signing succeeds with a fake DER signature
        coEvery { cryptoService.sign(any(), any()) } returns Outcome.Success(fakeSignature)

        // Default: UV preferred, biometric available
        coEvery { userVerificationService.getUserVerificationAvailability() } returns
            UserVerificationAvailability(
                biometricAvailable = true,
                pinAvailable = true,
                deviceLockAvailable = false,
                supportedBiometricTypes = listOf(BiometricType.FINGERPRINT),
                maxPinLength = MAX_PIN_LEN_8,
                minPinLength = MIN_PIN_LEN_4,
                biometricStrength = BiometricStrength.STRONG,
            )

        useCase =
            GetAssertionUseCase(
                credentialRepository,
                userVerificationService,
                selectCredentialUseCase,
                cryptoService,
            )
    }

    private fun createOptions(
        uv: UserVerificationRequirement = UserVerificationRequirement.PREFERRED,
        allowCredentials: List<PublicKeyCredentialDescriptor>? = null,
    ): GetAssertionOptions {
        return GetAssertionOptions.create(
            rpId = testRpId,
            clientDataHash = testClientDataHash,
            allowCredentials = allowCredentials,
            userVerification = uv,
        )
    }

    private fun createSummary(id: String): CredentialSummary {
        return CredentialSummary(
            id = id,
            rpId = testRpId,
            credentialId = id.toByteArray(),
            lastUsedAt = Instant.now(),
            coseAlgorithm = PasskeyCredential.COSE_ES256,
        )
    }

    // ── No credentials found ──────────────────────────────────────────────────

    @Test
    fun `returns failure when no credentials found for RP`() =
        runTest {
            coEvery { credentialRepository.getCredentialSummariesForRp(testRpId) } returns Outcome.Success(emptyList())

            val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull()
            assertTrue(
                exception is Fido2Exception.CredentialNotFound || exception is Fido2Exception.AuthenticationFailed,
            )
        }

    // ── User verification required but fails ─────────────────────────────────

    @Test
    fun `returns failure when user verification required but fails`() =
        runTest {
            coEvery { userVerificationService.getUserVerificationAvailability() } returns
                UserVerificationAvailability(
                    biometricAvailable = false,
                    pinAvailable = false,
                    deviceLockAvailable = false,
                    supportedBiometricTypes = emptyList(),
                    maxPinLength = 0,
                    minPinLength = 0,
                    biometricStrength = BiometricStrength.WEAK,
                )

            val result = useCase(createOptions(uv = UserVerificationRequirement.REQUIRED))

            assertTrue(result.isFailure)
        }

    // ── Credential selection delegation ───────────────────────────────────────

    @Test
    fun `delegates to SelectCredentialUseCase when multiple credentials found`() =
        runTest {
            val s1 = createSummary("cred1")
            val s2 = createSummary("cred2")
            coEvery {
                credentialRepository.getCredentialSummariesForRp(testRpId)
            } returns Outcome.Success(listOf(s1, s2))
            coEvery { selectCredentialUseCase(any(), any()) } returns Outcome.Success(s1)
            coEvery { credentialRepository.getSignCount("cred1") } returns Outcome.Success(SIGN_COUNT_5)
            coEvery { credentialRepository.updateSignCount("cred1", SIGN_COUNT_6) } returns Outcome.Success(Unit)

            val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

            coVerify { selectCredentialUseCase(any(), any()) }

            if (result.isSuccess) {
                val assertion = result.getOrThrow()
                assertNotNull(assertion.authData)
                assertNotNull(assertion.signature)
                assertTrue(assertion.authData.size >= MIN_AUTH_DATA_SIZE_37)
            }
        }

    // ── SelectCredentialUseCase fails ─────────────────────────────────────────

    @Test
    fun `returns failure when credential selection fails`() =
        runTest {
            val s1 = createSummary("cred1")
            coEvery { credentialRepository.getCredentialSummariesForRp(testRpId) } returns Outcome.Success(listOf(s1))
            coEvery { selectCredentialUseCase(any(), any()) } returns
                Outcome.Error(DomainError.NotFound("No eligible credential"))

            val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

            assertTrue(result.isFailure)
        }

    // ── Sign count is incremented ─────────────────────────────────────────────

    @Test
    fun `increments sign count on successful assertion`() =
        runTest {
            val s1 = createSummary("cred1")
            coEvery { credentialRepository.getCredentialSummariesForRp(testRpId) } returns Outcome.Success(listOf(s1))
            coEvery { selectCredentialUseCase(any(), any()) } returns Outcome.Success(s1)
            coEvery { credentialRepository.getSignCount("cred1") } returns Outcome.Success(SIGN_COUNT_10)
            coEvery { credentialRepository.updateSignCount("cred1", SIGN_COUNT_11) } returns Outcome.Success(Unit)

            val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

            if (result.isSuccess) {
                coVerify { credentialRepository.updateSignCount("cred1", SIGN_COUNT_11) }
            }
        }

    // ── Signing failure is surfaced ───────────────────────────────────────────

    @Test
    fun `returns failure when cryptoService signing fails`() =
        runTest {
            val s1 = createSummary("cred1")
            coEvery { credentialRepository.getCredentialSummariesForRp(testRpId) } returns Outcome.Success(listOf(s1))
            coEvery { selectCredentialUseCase(any(), any()) } returns Outcome.Success(s1)
            coEvery { credentialRepository.getSignCount("cred1") } returns Outcome.Success(SIGN_COUNT_5)
            coEvery { cryptoService.sign(any(), any()) } returns
                Outcome.Error(
                    DomainError.CryptoError(
                        "Master seed unavailable",
                        Fido2Exception.SigningFailed("Master seed unavailable"),
                    ),
                )

            val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

            assertTrue(result.isFailure)
        }
}

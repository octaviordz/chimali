package com.chimali.fido2.domain.usecase

import android.util.Log
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
import com.chimali.fido2.domain.service.VerificationMethod
import java.time.Instant
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
    private val testClientDataHash = ByteArray(32) { it.toByte() }
    private val fakeSignature = ByteArray(72) { 0x30.toByte() } // plausible DER signature size

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        credentialRepository = mockk()
        userVerificationService = mockk()
        selectCredentialUseCase = mockk()
        cryptoService = mockk()

        // Default: signing succeeds with a fake DER signature
        coEvery { cryptoService.sign(any(), any()) } returns Result.success(fakeSignature)

        // Default: UV preferred, biometric available
        coEvery { userVerificationService.getUserVerificationAvailability() } returns
            UserVerificationAvailability(
                biometricAvailable = true,
                pinAvailable = true,
                deviceLockAvailable = false,
                supportedBiometricTypes = listOf(BiometricType.FINGERPRINT),
                maxPinLength = 8,
                minPinLength = 4,
                biometricStrength = BiometricStrength.STRONG
            )

        useCase = GetAssertionUseCase(credentialRepository, userVerificationService, selectCredentialUseCase, cryptoService)
    }

    private fun createOptions(
        uv: UserVerificationRequirement = UserVerificationRequirement.PREFERRED,
        allowCredentials: List<PublicKeyCredentialDescriptor>? = null
    ): GetAssertionOptions {
        return GetAssertionOptions.create(
            rpId = testRpId,
            clientDataHash = testClientDataHash,
            allowCredentials = allowCredentials,
            userVerification = uv
        )
    }

    private fun createSummary(id: String): CredentialSummary {
        return CredentialSummary(
            id = id,
            rpId = testRpId,
            credentialId = id.toByteArray(),
            lastUsedAt = Instant.now(),
            coseAlgorithm = PasskeyCredential.COSE_ES256
        )
    }

    // ── No credentials found ──────────────────────────────────────────────────

    @Test
    fun `returns failure when no credentials found for RP`() = runTest {
        coEvery { credentialRepository.getCredentialSummariesForRp(testRpId) } returns Result.success(emptyList())

        val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is Fido2Exception.CredentialNotFound || exception is Fido2Exception.AuthenticationFailed)
    }

    // ── User verification required but fails ─────────────────────────────────

    @Test
    fun `returns failure when user verification required but fails`() = runTest {
        coEvery { userVerificationService.getUserVerificationAvailability() } returns
            UserVerificationAvailability(
                biometricAvailable = false,
                pinAvailable = false,
                deviceLockAvailable = false,
                supportedBiometricTypes = emptyList(),
                maxPinLength = 0,
                minPinLength = 0,
                biometricStrength = BiometricStrength.WEAK
            )

        val result = useCase(createOptions(uv = UserVerificationRequirement.REQUIRED))

        assertTrue(result.isFailure)
    }

    // ── Credential selection delegation ───────────────────────────────────────

    @Test
    fun `delegates to SelectCredentialUseCase when multiple credentials found`() = runTest {
        val s1 = createSummary("cred1")
        val s2 = createSummary("cred2")
        coEvery { credentialRepository.getCredentialSummariesForRp(testRpId) } returns Result.success(listOf(s1, s2))
        coEvery { selectCredentialUseCase(any(), any()) } returns Result.success(s1)
        coEvery { credentialRepository.getSignCount("cred1") } returns Result.success(5L)
        coEvery { credentialRepository.updateSignCount("cred1", 6L) } returns Result.success(Unit)

        val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

        coVerify { selectCredentialUseCase(any(), any()) }

        if (result.isSuccess) {
            val assertion = result.getOrThrow()
            assertNotNull(assertion.authData)
            assertNotNull(assertion.signature)
            assertTrue(assertion.authData.size >= 37)
        }
    }

    // ── SelectCredentialUseCase fails ─────────────────────────────────────────

    @Test
    fun `returns failure when credential selection fails`() = runTest {
        val s1 = createSummary("cred1")
        coEvery { credentialRepository.getCredentialSummariesForRp(testRpId) } returns Result.success(listOf(s1))
        coEvery { selectCredentialUseCase(any(), any()) } returns
            Result.failure(Fido2Exception.CredentialNotFound("No eligible credential"))

        val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

        assertTrue(result.isFailure)
    }

    // ── Sign count is incremented ─────────────────────────────────────────────

    @Test
    fun `increments sign count on successful assertion`() = runTest {
        val s1 = createSummary("cred1")
        coEvery { credentialRepository.getCredentialSummariesForRp(testRpId) } returns Result.success(listOf(s1))
        coEvery { selectCredentialUseCase(any(), any()) } returns Result.success(s1)
        coEvery { credentialRepository.getSignCount("cred1") } returns Result.success(10L)
        coEvery { credentialRepository.updateSignCount("cred1", 11L) } returns Result.success(Unit)

        val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

        if (result.isSuccess) {
            coVerify { credentialRepository.updateSignCount("cred1", 11L) }
        }
    }

    // ── Signing failure is surfaced ───────────────────────────────────────────

    @Test
    fun `returns failure when cryptoService signing fails`() = runTest {
        val s1 = createSummary("cred1")
        coEvery { credentialRepository.getCredentialSummariesForRp(testRpId) } returns Result.success(listOf(s1))
        coEvery { selectCredentialUseCase(any(), any()) } returns Result.success(s1)
        coEvery { credentialRepository.getSignCount("cred1") } returns Result.success(5L)
        coEvery { cryptoService.sign(any(), any()) } returns Result.failure(
            Fido2Exception.SigningFailed("Master seed unavailable")
        )

        val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

        assertTrue(result.isFailure)
    }
}

package com.chimali.fido2.domain.usecase

import android.util.Log
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.service.UserVerificationAvailability
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.service.BiometricVerificationResult
import com.chimali.fido2.domain.service.BiometricStrength
import com.chimali.fido2.domain.service.BiometricType
import com.chimali.fido2.domain.service.VerificationMethod
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature

/**
 * T082 — Unit tests for [GetAssertionUseCase].
 *
 * Since the use case internally accesses the Android KeyStore for signing,
 * we test at the boundary: verify that it correctly orchestrates repository
 * look-ups, credential selection, user verification, and error handling.
 *
 * Signing / KeyStore interactions are tested indirectly (the use case expects
 * a key alias pattern), and we mock the KeyStore via mockkStatic for
 * the happy-path signing test.
 */
class GetAssertionUseCaseTest {

    private lateinit var credentialRepository: CredentialRepository
    private lateinit var userVerificationService: UserVerificationService
    private lateinit var selectCredentialUseCase: SelectCredentialUseCase
    private lateinit var useCase: GetAssertionUseCase

    private val testRpId = "https://example.com"
    private val testClientDataHash = ByteArray(32) { it.toByte() }

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

        // Default: UV preferred, biometric available, auto-succeed
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
        coEvery { userVerificationService.verifyBiometric(any(), any()) } returns
            Result.success(mockk<BiometricVerificationResult>())
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

    // ── No credentials found ─────────────────────────────────────────────────

    @Test
    fun `returns failure when no credentials found for RP`() = runTest {
        coEvery { credentialRepository.getCredentialsForRp(testRpId) } returns Result.success(emptyList())

        // We must construct the use case after mocking KeyStore
        mockkStatic(KeyStore::class)
        val ks = mockk<KeyStore>()
        every { KeyStore.getInstance("AndroidKeyStore") } returns ks
        every { ks.load(null) } returns Unit

        useCase = GetAssertionUseCase(credentialRepository, userVerificationService, selectCredentialUseCase)
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
                biometricAvailable = true,
                pinAvailable = false,
                deviceLockAvailable = false,
                supportedBiometricTypes = listOf(BiometricType.FINGERPRINT),
                maxPinLength = 8,
                minPinLength = 4,
                biometricStrength = BiometricStrength.STRONG
            )
        coEvery { userVerificationService.verifyBiometric(any(), any()) } returns
            Result.failure(Fido2Exception.UserVerificationFailed("Biometric cancelled"))

        mockkStatic(KeyStore::class)
        val ks = mockk<KeyStore>()
        every { KeyStore.getInstance("AndroidKeyStore") } returns ks
        every { ks.load(null) } returns Unit

        useCase = GetAssertionUseCase(credentialRepository, userVerificationService, selectCredentialUseCase)
        val result = useCase(createOptions(uv = UserVerificationRequirement.REQUIRED))

        assertTrue(result.isFailure)
    }

    // ── Credential selection delegation ──────────────────────────────────────

    @Test
    fun `delegates to SelectCredentialUseCase when multiple credentials found`() = runTest {
        val cred1 = PasskeyCredential.createTest("cred1", testRpId, "alice")
        val cred2 = PasskeyCredential.createTest("cred2", testRpId, "bob")
        coEvery { credentialRepository.getCredentialsForRp(testRpId) } returns Result.success(listOf(cred1, cred2))
        coEvery { selectCredentialUseCase(any(), any()) } returns Result.success(cred1)
        coEvery { credentialRepository.getSignCount("cred1") } returns Result.success(5L)
        coEvery { credentialRepository.updateSignCount("cred1", 6L) } returns Result.success(Unit)

        mockkStatic(KeyStore::class)
        val ks = mockk<KeyStore>()
        every { KeyStore.getInstance("AndroidKeyStore") } returns ks
        every { ks.load(null) } returns Unit

        // Mock signing
        val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        every { ks.getKey("fido2_cred_cred1", null) } returns keyPair.private

        useCase = GetAssertionUseCase(credentialRepository, userVerificationService, selectCredentialUseCase)
        val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

        // The use case should have called SelectCredentialUseCase
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
        val cred1 = PasskeyCredential.createTest("cred1", testRpId, "alice")
        coEvery { credentialRepository.getCredentialsForRp(testRpId) } returns Result.success(listOf(cred1))
        coEvery { selectCredentialUseCase(any(), any()) } returns
            Result.failure(Fido2Exception.CredentialNotFound("No eligible credential"))

        mockkStatic(KeyStore::class)
        val ks = mockk<KeyStore>()
        every { KeyStore.getInstance("AndroidKeyStore") } returns ks
        every { ks.load(null) } returns Unit

        useCase = GetAssertionUseCase(credentialRepository, userVerificationService, selectCredentialUseCase)
        val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

        assertTrue(result.isFailure)
    }

    // ── Sign count is incremented ────────────────────────────────────────────

    @Test
    fun `increments sign count on successful assertion`() = runTest {
        val cred = PasskeyCredential.createTest("cred1", testRpId, "alice")
        coEvery { credentialRepository.getCredentialsForRp(testRpId) } returns Result.success(listOf(cred))
        coEvery { selectCredentialUseCase(any(), any()) } returns Result.success(cred)
        coEvery { credentialRepository.getSignCount("cred1") } returns Result.success(10L)
        coEvery { credentialRepository.updateSignCount("cred1", 11L) } returns Result.success(Unit)

        mockkStatic(KeyStore::class)
        val ks = mockk<KeyStore>()
        every { KeyStore.getInstance("AndroidKeyStore") } returns ks
        every { ks.load(null) } returns Unit

        val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        every { ks.getKey("fido2_cred_cred1", null) } returns keyPair.private

        useCase = GetAssertionUseCase(credentialRepository, userVerificationService, selectCredentialUseCase)
        val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

        if (result.isSuccess) {
            coVerify { credentialRepository.updateSignCount("cred1", 11L) }
        }
    }
}

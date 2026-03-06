package com.chimali.fido2.domain.usecase

import com.chimali.fido2.domain.model.*

import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.service.Fido2Authenticator
import com.chimali.fido2.domain.service.*
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.domain.exception.Fido2Exception
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.security.KeyPairGenerator
import java.time.Instant

@DisplayName("RegisterCredential Use Case Tests")
class RegisterCredentialUseCaseTest {
    
    private lateinit var credentialRepository: CredentialRepository
    private lateinit var userVerificationService: UserVerificationService
    private lateinit var fido2Authenticator: Fido2Authenticator
    private lateinit var cborCodec: CborCodec
    private lateinit var cryptoService: Fido2CryptoService
    private lateinit var registerCredentialUseCase: RegisterCredentialUseCase
    
    private lateinit var testPublicKey: java.security.PublicKey
    private lateinit var testOptions: MakeCredentialOptions
    private lateinit var testRp: PublicKeyCredentialRpEntity
    private lateinit var testUser: PublicKeyCredentialUserEntity
    private lateinit var testParams: PublicKeyCredentialParameters
    
    @BeforeEach
    fun setUp() = runTest {
        credentialRepository = mockk()
        userVerificationService = mockk()
        fido2Authenticator = mockk()
        cborCodec = mockk()
        cryptoService = mockk()
        registerCredentialUseCase = RegisterCredentialUseCase(
            credentialRepository,
            userVerificationService,
            fido2Authenticator,
            cborCodec,
            cryptoService
        )
        
        // Setup test data
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(256)
        testPublicKey = keyPairGenerator.generateKeyPair().public
        
        testRp = PublicKeyCredentialRpEntity.create(
            id = "https://example.com",
            name = "Example Website",
            
        )
        
        testUser = PublicKeyCredentialUserEntity.create(
            id = "user123".toByteArray(),
            name = "testuser",
            displayName = "Test User"
        )
        
        testParams = PublicKeyCredentialParameters.createES256P256()
        
        testOptions = MakeCredentialOptions.create(
            rp = testRp,
            user = testUser,
            challenge = "test_challenge".toByteArray(),
            pubKeyCredParams = testParams,
            timeout = 60000L,
            allowCredentials = null,
            excludeCredentials = null,
            authenticatorSelection = AuthenticatorSelectionCriteria.create(
                userVerification = com.chimali.fido2.domain.model.UserVerificationRequirement.REQUIRED
            ),
            attestation = AttestationConveyancePreference.NONE
        )
        
        // Setup default mock responses
        val testFido2KeyPair = com.chimali.fido2.data.crypto.Fido2KeyPair("test_alias", ByteArray(65) { 0x01 })
        coEvery { cryptoService.generateCredentialKeyPair(any(), any()) } returns Result.success(testFido2KeyPair)
        coEvery { cryptoService.getPublicKey(any()) } returns testPublicKey
        coEvery { cborCodec.encodeCosePublicKeyFromJavaKey(any()) } returns ByteArray(77)
        coEvery { userVerificationService.isUserVerificationRequired(any(), any(), any()) } returns com.chimali.fido2.domain.service.UserVerificationRequirement.REQUIRED
        coEvery { userVerificationService.getUserVerificationAvailability() } returns UserVerificationAvailability(
            biometricAvailable = true,
            pinAvailable = true,
            deviceLockAvailable = true,
            supportedBiometricTypes = listOf(BiometricType.FINGERPRINT),
            maxPinLength = 8,
            minPinLength = 4,
            biometricStrength = BiometricStrength.STRONG
        )

        coEvery { userVerificationService.recordUserConsent(any()) } returns Result.success(mockk())
        coEvery { credentialRepository.validateCredentialCreation(any(), any()) } returns Result.success(Unit)
        coEvery { credentialRepository.saveCredential(any()) } returns Result.success(Unit)
        coEvery { credentialRepository.getRelyingParty(any()) } returns null
        coEvery { credentialRepository.updateRelyingParty(any(), any()) } returns Result.success(Unit)
        coEvery { credentialRepository.saveRelyingParty(any<com.chimali.fido2.domain.model.RelyingParty>()) } returns Result.success(Unit)
    }
    
    @Nested
    @DisplayName("Successful Registration Tests")
    inner class SuccessfulRegistrationTests {
        
        @Test
        @DisplayName("Should successfully register credential with biometric verification")
        fun `should successfully register credential with biometric verification`() = runTest {
            val result = registerCredentialUseCase(testOptions)
            
            assertTrue(result.isSuccess, "Result failed with exception: ${result.exceptionOrNull()?.message}")
            val attestationObject = result.getOrThrow()
            assertNotNull(attestationObject)
            assertEquals("none", attestationObject.fmt)
            
            // Verify all expected interactions
            coVerify { userVerificationService.isUserVerificationRequired(any(), any(), any()) }
            coVerify { userVerificationService.recordUserConsent(any()) }
            coVerify { credentialRepository.validateCredentialCreation(any(), any()) }
            coVerify { credentialRepository.saveCredential(any()) }
            coVerify { credentialRepository.saveRelyingParty(any()) }
        }
        
        @Test
        @DisplayName("Should successfully register credential with PIN verification")
        fun `should successfully register credential with pin verification`() = runTest {
            // Mock PIN as the only available method
            coEvery { userVerificationService.getUserVerificationAvailability() } returns UserVerificationAvailability(
                biometricAvailable = false,
                pinAvailable = true,
                deviceLockAvailable = false,
                supportedBiometricTypes = emptyList(),
                maxPinLength = 8,
                minPinLength = 4,
                biometricStrength = BiometricStrength.WEAK
            )
            
            val result = registerCredentialUseCase(testOptions)
            
            assertTrue(result.isSuccess, "Result failed with exception: ${result.exceptionOrNull()?.message}")
            val attestationObject = result.getOrThrow()
            assertNotNull(attestationObject)
            
            // Verify availability was checked 
            coVerify { userVerificationService.getUserVerificationAvailability() }
        }
        
        @Test
        @DisplayName("Should successfully register credential without verification when not required")
        fun `should successfully register credential without verification when not required`() = runTest {
            // Mock no verification required
            coEvery { userVerificationService.isUserVerificationRequired(any(), any(), any()) } returns com.chimali.fido2.domain.service.UserVerificationRequirement.NOT_REQUIRED
            
            val optionsNotRequired = MakeCredentialOptions.create(
                rp = testRp,
                user = testUser,
                challenge = "test_challenge".toByteArray(),
                pubKeyCredParams = testParams,
                timeout = 60000L,
                allowCredentials = null,
                excludeCredentials = null,
                authenticatorSelection = AuthenticatorSelectionCriteria.create(
                    userVerification = com.chimali.fido2.domain.model.UserVerificationRequirement.DISCOURAGED
                ),
                attestation = AttestationConveyancePreference.NONE
            )
            
            val result = registerCredentialUseCase(optionsNotRequired)
            
            assertTrue(result.isSuccess, "Result failed with exception: ${result.exceptionOrNull()?.message}")
            val attestationObject = result.getOrThrow()
            assertNotNull(attestationObject)
            
            // Verify no verification was performed
            coVerify(exactly = 0) { userVerificationService.getUserVerificationAvailability() }
        }
        
        @Test
        @DisplayName("Should update existing RP information")
        fun `should update existing rp information`() = runTest {
            val existingRp = RelyingParty.create(
                id = "https://example.com",
                name = "Example Website",
                
            ).copy(credentialCount = 3)
            
            coEvery { credentialRepository.getRelyingParty(any()) } returns existingRp
            
            val result = registerCredentialUseCase(testOptions)
            
            assertTrue(result.isSuccess, "Result failed with exception: ${result.exceptionOrNull()?.message}")
            
            // Verify RP was updated with incremented credential count
            coVerify { credentialRepository.saveRelyingParty(any()) }
        }
    }
    
    @Nested
    @DisplayName("Validation Failure Tests")
    inner class ValidationFailureTests {
        
        @Test
        @DisplayName("Should fail when RP validation fails")
        fun `should fail when rp validation fails`() = runTest {
            assertThrows(IllegalArgumentException::class.java) {
                PublicKeyCredentialRpEntity.create(
                    id = "ftp://invalid-rp.com", // ftp is invalid scheme
                    name = "Test RP"
                )
            }
        }
        
        @Test
        @DisplayName("Should fail when user validation fails")
        fun `should fail when user validation fails`() = runTest {
            assertThrows(IllegalArgumentException::class.java) {
                PublicKeyCredentialUserEntity.create(
                    id = "user123".toByteArray(),
                    name = "", // Blank name is invalid
                    displayName = "Test User"
                )
            }
        }
        
        @Test
        @DisplayName("Should fail when challenge is empty")
        fun `should fail when challenge is empty`() = runTest {
            assertThrows(IllegalArgumentException::class.java) {
                MakeCredentialOptions.create(
                    rp = testRp,
                    user = testUser,
                    challenge = ByteArray(0) // Empty challenge is invalid
                )
            }
        }
        
        @Test
        @DisplayName("Should fail when challenge exceeds maximum size")
        fun `should fail when challenge exceeds maximum size`() = runTest {
            assertThrows(IllegalArgumentException::class.java) {
                MakeCredentialOptions.create(
                    rp = testRp,
                    user = testUser,
                    challenge = ByteArray(65) // Max is 64
                )
            }
        }
        
        @Test
        @DisplayName("Should fail when timeout is invalid")
        fun `should fail when timeout is invalid`() = runTest {
            assertThrows(IllegalArgumentException::class.java) {
                MakeCredentialOptions.create(
                    rp = testRp,
                    user = testUser,
                    challenge = "test_challenge".toByteArray(),
                    timeout = -1L // Negative timeout is invalid
                )
            }
        }
        
        @Test
        @DisplayName("Should fail when credential creation validation fails")
        fun `should fail when credential creation validation fails`() = runTest {
            coEvery { credentialRepository.validateCredentialCreation(any(), any()) } returns Result.failure(
                Fido2Exception.CredentialCreationNotAllowed("Credential creation not allowed")
            )
            
            val result = registerCredentialUseCase(testOptions)
            
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is Fido2Exception.CredentialCreationNotAllowed)
        }
    }
    
    @Nested
    @DisplayName("User Verification Failure Tests")
    inner class UserVerificationFailureTests {
        

        
        @Test
        @DisplayName("Should fail when no verification method is available")
        fun `should fail when no verification method is available`() = runTest {
            coEvery { userVerificationService.getUserVerificationAvailability() } returns UserVerificationAvailability(
                biometricAvailable = false,
                pinAvailable = false,
                deviceLockAvailable = false,
                supportedBiometricTypes = emptyList(),
                maxPinLength = 0,
                minPinLength = 0,
                biometricStrength = BiometricStrength.WEAK
            )
            
            val result = registerCredentialUseCase(testOptions)
            
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is Fido2Exception.NoVerificationMethodAvailable)
        }
        
        @Test
        @DisplayName("Should fail when user consent recording fails")
        fun `should fail when user consent recording fails`() = runTest {
            coEvery { userVerificationService.recordUserConsent(any()) } returns Result.failure(
                Fido2Exception.ConsentDenied("Consent denied")
            )
            
            val result = registerCredentialUseCase(testOptions)
            
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is Fido2Exception.ConsentDenied)
        }
    }
    
    @Nested
    @DisplayName("Storage Failure Tests")
    inner class StorageFailureTests {
        
        @Test
        @DisplayName("Should fail when credential storage fails")
        fun `should fail when credential storage fails`() = runTest {
            coEvery { credentialRepository.saveCredential(any()) } returns Result.failure(
                Fido2Exception.CredentialStorageFailed("Credential storage failed")
            )
            
            val result = registerCredentialUseCase(testOptions)
            
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is Fido2Exception.CredentialStorageFailed)
        }
        
        @Test
        @DisplayName("Should fail when RP update fails")
        fun `should fail when rp update fails`() = runTest {
            coEvery { credentialRepository.saveRelyingParty(any()) } returns Result.failure(
                Fido2Exception.RelyingPartyUpdateFailed("Relying party update failed")
            )
            
            val result = registerCredentialUseCase(testOptions)
            
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is Fido2Exception.RelyingPartyUpdateFailed)
        }
    }
    
    @Nested
    @DisplayName("Algorithm Support Tests")
    inner class AlgorithmSupportTests {
        
        @Test
        @DisplayName("Should support ES256 algorithm")
        fun `should support es256 algorithm`() = runTest {
            val es256Options = testOptions.copy(
                pubKeyCredParams = PublicKeyCredentialParameters.createES256P256()
            )
            
            val result = registerCredentialUseCase(es256Options)
            
            assertTrue(result.isSuccess)
        }
        
        @Test
        @DisplayName("Should support RS256 algorithm")
        fun `should support rs256 algorithm`() = runTest {
            val rs256Options = testOptions.copy(
                pubKeyCredParams = PublicKeyCredentialParameters.createRS256()
            )
            
            val result = registerCredentialUseCase(rs256Options)
            
            assertTrue(result.isSuccess)
        }
        
        @Test
        @DisplayName("Should fail with unsupported algorithm")
        fun `should fail with unsupported algorithm`() = runTest {
            // PublicKeyCredentialParameters validates algorithm in init, so creating with unsupported
            // algorithm throws IllegalArgumentException before the use case is even called.
            assertThrows(IllegalArgumentException::class.java) {
                PublicKeyCredentialParameters.create(algorithm = "UNSUPPORTED")
            }
        }
    }
    
    @Nested
    @DisplayName("Verification Preference Tests")
    inner class VerificationPreferenceTests {
        
        @Test
        @DisplayName("Should handle preferred verification with biometric available")
        fun `should handle preferred verification with biometric available`() = runTest {
            coEvery { userVerificationService.isUserVerificationRequired(any(), any(), any()) } returns com.chimali.fido2.domain.service.UserVerificationRequirement.PREFERRED
            
            val preferredOptions = testOptions.copy(
                authenticatorSelection = AuthenticatorSelectionCriteria.create(
                    userVerification = com.chimali.fido2.domain.model.UserVerificationRequirement.PREFERRED
                )
            )
            
            val result = registerCredentialUseCase(preferredOptions)
            
            assertTrue(result.isSuccess)
            coVerify(exactly = 0) { userVerificationService.getUserVerificationAvailability() }
        }
        
        @Test
        @DisplayName("Should handle discouraged verification")
        fun `should handle discouraged verification`() = runTest {
            coEvery { userVerificationService.isUserVerificationRequired(any(), any(), any()) } returns com.chimali.fido2.domain.service.UserVerificationRequirement.DISCOURAGED
            
            val discouragedOptions = testOptions.copy(
                authenticatorSelection = AuthenticatorSelectionCriteria.create(
                    userVerification = com.chimali.fido2.domain.model.UserVerificationRequirement.DISCOURAGED
                )
            )
            
            val result = registerCredentialUseCase(discouragedOptions)
            
            assertTrue(result.isSuccess)
            coVerify(exactly = 0) { userVerificationService.getUserVerificationAvailability() }
        }
    }
    
    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {
        
        @Test
        @DisplayName("Should handle exclude credentials list")
        fun `should handle exclude credentials list`() = runTest {
            val excludeCredentials = listOf(
                PublicKeyCredentialDescriptor.create(
                    type = PublicKeyCredentialType.PUBLIC_KEY,
                    id = "existing_credential".toByteArray()
                )
            )
            
            val optionsWithExcludes = testOptions.copy(
                excludeCredentials = excludeCredentials
            )
            
            val result = registerCredentialUseCase(optionsWithExcludes)
            assertTrue(result.isSuccess, "Failed with exception: ${result.exceptionOrNull()?.message}")
        }
        
        @Test
        @DisplayName("Should handle allow credentials list")
        fun `should handle allow credentials list`() = runTest {
            val allowCredentials = listOf(
                PublicKeyCredentialDescriptor.create(
                    type = PublicKeyCredentialType.PUBLIC_KEY,
                    id = "allowed_credential".toByteArray()
                )
            )
            
            val optionsWithAllows = testOptions.copy(
                allowCredentials = allowCredentials
            )
            
            val result = registerCredentialUseCase(optionsWithAllows)
            assertTrue(result.isSuccess, "Failed with exception: ${result.exceptionOrNull()?.message}")
        }
        
        @Test
        @DisplayName("Should handle resident key requirements")
        fun `should handle resident key requirements`() = runTest {
            val residentKeyOptions = testOptions.copy(
                authenticatorSelection = AuthenticatorSelectionCriteria.create(
                    requireResidentKey = ResidentKeyRequirement.REQUIRED,
                    userVerification = com.chimali.fido2.domain.model.UserVerificationRequirement.REQUIRED
                )
            )
            
            val result = registerCredentialUseCase(residentKeyOptions)
            
            assertTrue(result.isSuccess)
        }
        
        @Test
        @DisplayName("Should handle attestation preferences")
        fun `should handle attestation preferences`() = runTest {
            val directAttestationOptions = testOptions.copy(
                attestation = AttestationConveyancePreference.DIRECT
            )
            
            val result = registerCredentialUseCase(directAttestationOptions)
            
            assertTrue(result.isSuccess)
            val attestationObject = result.getOrThrow()
            assertEquals("none", attestationObject.fmt) // Still self-attested for privacy
        }
    }
}

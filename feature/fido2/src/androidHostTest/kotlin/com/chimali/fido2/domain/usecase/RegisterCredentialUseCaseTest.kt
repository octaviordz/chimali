package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.exceptionOrNull
import com.chimali.core.common.result.getOrThrow
import com.chimali.core.common.result.isFailure
import com.chimali.core.common.result.isSuccess
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.ClientDataHashService
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.AttestationConveyancePreference
import com.chimali.fido2.domain.model.AuthenticatorSelectionCriteria
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.domain.model.PublicKeyCredentialParameters
import com.chimali.fido2.domain.model.PublicKeyCredentialRpEntity
import com.chimali.fido2.domain.model.PublicKeyCredentialType
import com.chimali.fido2.domain.model.PublicKeyCredentialUserEntity
import com.chimali.fido2.domain.model.ResidentKeyRequirement
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import com.chimali.fido2.domain.service.BiometricStrength
import com.chimali.fido2.domain.service.BiometricType
import com.chimali.fido2.domain.service.UserVerificationAvailability
import com.chimali.fido2.domain.service.UserVerificationService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.security.KeyPairGenerator
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows

class RegisterCredentialUseCaseTest {
    private lateinit var credentialRepository: CredentialRepository
    private lateinit var userVerificationService: UserVerificationService
    private lateinit var cborCodec: CborCodec
    private lateinit var cryptoService: Fido2CryptoService
    private lateinit var fido2SettingsRepository: Fido2SettingsRepository
    private lateinit var clientDataHashService: ClientDataHashService
    private lateinit var registerCredentialUseCase: RegisterCredentialUseCase

    private lateinit var testPublicKey: java.security.PublicKey
    private lateinit var testOptions: MakeCredentialOptions
    private lateinit var testRp: PublicKeyCredentialRpEntity
    private lateinit var testUser: PublicKeyCredentialUserEntity
    private lateinit var testParams: PublicKeyCredentialParameters

    private companion object {
        private const val KEY_SIZE_256 = 256
        private const val TIMEOUT_60000 = 60000L
        private const val PUB_KEY_SIZE_65 = 65
        private const val PUB_KEY_ENCODED_SIZE_77 = 77
        private const val MAX_PIN_LEN_8 = 8
        private const val MIN_PIN_LEN_4 = 4
        private const val SIGNATURE_SIZE_72 = 72
        private const val DUMMY_BYTE_01 = 0x01.toByte()
        private const val DUMMY_BYTE_30 = 0x30.toByte()
        private const val LIMIT_1000 = 1000
        private const val LIMIT_2000 = 2000
        private const val COUNT_3 = 3
        private const val COUNT_49 = 49
        private const val LIMIT_50 = 50
    }

    @BeforeTest
    fun setUp() =
        runTest {
            credentialRepository = mockk()
            userVerificationService = mockk()
            cborCodec = mockk()
            cryptoService = mockk()
            fido2SettingsRepository = mockk()
            clientDataHashService = ClientDataHashService()
            registerCredentialUseCase =
                RegisterCredentialUseCase(
                    passkeyCredentialRepository = credentialRepository,
                    userVerificationService = userVerificationService,
                    cborCodec = cborCodec,
                    cryptoService = cryptoService,
                    settingsRepository = fido2SettingsRepository,
                    clientDataHashService = clientDataHashService,
                )

            // Setup test data
            val keyPairGenerator = KeyPairGenerator.getInstance("EC")
            keyPairGenerator.initialize(KEY_SIZE_256)
            testPublicKey = keyPairGenerator.generateKeyPair().public

            testRp =
                PublicKeyCredentialRpEntity.create(
                    id = RpId("https://example.com"),
                    name = "Example Website",
                )

            testUser =
                PublicKeyCredentialUserEntity.create(
                    id = UserId("user123"),
                    name = "testuser",
                    displayName = "Test User",
                )

            testParams = PublicKeyCredentialParameters.createES256P256()

            testOptions =
                MakeCredentialOptions.create(
                    rp = testRp,
                    user = testUser,
                    challenge = "test_challenge".toByteArray(),
                    pubKeyCredParams = testParams,
                    timeout = TIMEOUT_60000,
                    allowCredentials = null,
                    excludeCredentials = null,
                    authenticatorSelection =
                        AuthenticatorSelectionCriteria.create(
                            userVerification = com.chimali.fido2.domain.model.UserVerificationRequirement.REQUIRED,
                        ),
                    attestation = AttestationConveyancePreference.NONE,
                    selectedAlgId = Fido2CryptoService.COSE_ES256,
                )

            // Setup default mock responses
            val testFido2KeyPair =
                com.chimali.fido2.data.crypto.Fido2KeyPair(
                    "test_alias",
                    ByteArray(PUB_KEY_SIZE_65) { DUMMY_BYTE_01 },
                )
            coEvery { cryptoService.generateCredentialKeyPair(any()) } returns Outcome.Success(testFido2KeyPair)
            coEvery { cryptoService.getPublicKey(any(), any()) } returns testPublicKey
            coEvery { cborCodec.encodeCosePublicKeyFromJavaKey(any()) } returns ByteArray(PUB_KEY_ENCODED_SIZE_77)
            coEvery {
                userVerificationService.isUserVerificationRequired(any(), any(), any())
            } returns com.chimali.fido2.domain.service.UserVerificationRequirement.REQUIRED
            coEvery { userVerificationService.getUserVerificationAvailability() } returns
                UserVerificationAvailability(
                    isBiometricAvailable = true,
                    isPinAvailable = true,
                    isDeviceLockAvailable = true,
                    supportedBiometricTypes = listOf(BiometricType.FINGERPRINT),
                    maxPinLength = MAX_PIN_LEN_8,
                    minPinLength = MIN_PIN_LEN_4,
                    biometricStrength = BiometricStrength.STRONG,
                )

            coEvery { userVerificationService.recordUserConsent(any()) } returns Outcome.Success(Unit)
            coEvery { fido2SettingsRepository.getMaxCredentialCount() } returns LIMIT_1000
            coEvery { credentialRepository.validateCredentialCreation(any(), any()) } returns Outcome.Success(Unit)
            coEvery { credentialRepository.saveCredential(any()) } returns Outcome.Success(Unit)
            coEvery { credentialRepository.getRelyingParty(any()) } returns null
            coEvery { credentialRepository.updateRelyingParty(any(), any()) } returns Outcome.Success(Unit)
            coEvery {
                credentialRepository.saveRelyingParty(any<RelyingParty>())
            } returns Outcome.Success(Unit)
            // T115a: Stub getCredentialStatistics so the quota check in RegisterCredentialUseCase can proceed.
            // Default: 0 credentials stored → registration allowed.
            coEvery { credentialRepository.getCredentialStatistics() } returns
                com.chimali.fido2.domain.repository.CredentialStatistics(
                    totalCredentials = 0,
                    credentialsByRp = emptyMap(),
                    expiredCredentials = 0,
                    recentlyUsedCredentials = 0,
                    credentialsRequiringUserVerification = 0,
                    averageAgeDays = 0.0,
                )
            // T145b: stub sign() so the packed attestation path succeeds in tests
            coEvery {
                cryptoService.sign(any(), any())
            } returns Outcome.Success(ByteArray(SIGNATURE_SIZE_72) { DUMMY_BYTE_30 })
        }

    @Nested
    inner class SuccessfulRegistrationTests {
        @Test
        fun `should successfully register credential with biometric verification`() =
            runTest {
                val result = registerCredentialUseCase(testOptions)

                assertTrue(result.isSuccess, "Result failed with exception: ${result.exceptionOrNull()?.message}")
                val makeResult = result.getOrThrow()
                val attestationObject = makeResult.attestationObject
                val credential = makeResult.credential

                assertNotNull(attestationObject)
                assertNotNull(credential)
                assertEquals("packed", attestationObject.fmt)
                assertEquals(testRp.id, credential.rpId)
                assertEquals("testuser", credential.userName)

                // Verify all expected interactions
                coVerify { userVerificationService.isUserVerificationRequired(any(), any(), any()) }
                coVerify { userVerificationService.recordUserConsent(any()) }
                coVerify { credentialRepository.validateCredentialCreation(any(), any()) }
                coVerify { credentialRepository.saveCredential(any()) }
                coVerify { credentialRepository.saveRelyingParty(any()) }
            }

        @Test
        fun `should successfully register credential with pin verification`() =
            runTest {
                // Mock PIN as the only available method
                coEvery { userVerificationService.getUserVerificationAvailability() } returns
                    UserVerificationAvailability(
                        isBiometricAvailable = false,
                        isPinAvailable = true,
                        isDeviceLockAvailable = false,
                        supportedBiometricTypes = emptyList(),
                        maxPinLength = MAX_PIN_LEN_8,
                        minPinLength = MIN_PIN_LEN_4,
                        biometricStrength = BiometricStrength.WEAK,
                    )

                val result = registerCredentialUseCase(testOptions)

                assertTrue(result.isSuccess, "Result failed with exception: ${result.exceptionOrNull()?.message}")
                val makeResult = result.getOrThrow()
                assertNotNull(makeResult.attestationObject)
                assertNotNull(makeResult.credential)

                // Verify availability was checked
                coVerify { userVerificationService.getUserVerificationAvailability() }
            }

        @Test
        fun `should successfully register credential without verification when not required`() =
            runTest {
                // Mock no verification required
                coEvery {
                    userVerificationService.isUserVerificationRequired(any(), any(), any())
                } returns com.chimali.fido2.domain.service.UserVerificationRequirement.NOT_REQUIRED

                val optionsNotRequired =
                    MakeCredentialOptions.create(
                        rp = testRp,
                        user = testUser,
                        challenge = "test_challenge".toByteArray(),
                        pubKeyCredParams = testParams,
                        timeout = TIMEOUT_60000,
                        allowCredentials = null,
                        excludeCredentials = null,
                        authenticatorSelection =
                            AuthenticatorSelectionCriteria.create(
                                userVerification =
                                    com.chimali.fido2.domain.model.UserVerificationRequirement.DISCOURAGED,
                            ),
                        attestation = AttestationConveyancePreference.NONE,
                        selectedAlgId = Fido2CryptoService.COSE_ES256,
                    )

                val result = registerCredentialUseCase(optionsNotRequired)

                assertTrue(result.isSuccess, "Result failed with exception: ${result.exceptionOrNull()?.message}")
                val makeResult = result.getOrThrow()
                assertNotNull(makeResult.attestationObject)
                assertNotNull(makeResult.credential)

                // Verify no verification was performed
                coVerify(exactly = 0) { userVerificationService.getUserVerificationAvailability() }
            }

        @Test
        fun `should update existing rp information`() =
            runTest {
                val existingRp =
                    RelyingParty
                        .create(
                            id = RpId("https://example.com"),
                            name = "Example Website",
                        ).copy(credentialCount = COUNT_3)

                coEvery { credentialRepository.getRelyingParty(any()) } returns existingRp

                val result = registerCredentialUseCase(testOptions)

                assertTrue(result.isSuccess, "Result failed with exception: ${result.exceptionOrNull()?.message}")

                // Verify RP was updated with incremented credential count
                coVerify { credentialRepository.saveRelyingParty(any()) }
            }
    }

    @Nested
    inner class ValidationFailureTests {
        @Test
        fun `should fail when rp validation fails`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    PublicKeyCredentialRpEntity.create(
                        // ftp is invalid scheme
                        id = RpId("ftp://invalid-rp.com"),
                        name = "Test RP",
                    )
                }
            }

        @Test
        fun `should fail when user validation fails`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    PublicKeyCredentialUserEntity.create(
                        id = UserId("user123"),
                        // Blank name is invalid
                        name = "",
                        displayName = "Test User",
                    )
                }
            }

        @Test
        fun `should fail when challenge is empty`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    MakeCredentialOptions.create(
                        rp = testRp,
                        user = testUser,
                        // Empty challenge is invalid
                        challenge = ByteArray(0),
                        selectedAlgId = Fido2CryptoService.COSE_ES256,
                    )
                }
            }

        @Test
        fun `should fail when challenge exceeds maximum size`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    MakeCredentialOptions.create(
                        rp = testRp,
                        user = testUser,
                        // Max is 64
                        challenge = ByteArray(PUB_KEY_SIZE_65),
                        selectedAlgId = Fido2CryptoService.COSE_ES256,
                    )
                }
            }

        @Test
        fun `should fail when timeout is invalid`() =
            runTest {
                assertThrows<IllegalArgumentException> {
                    MakeCredentialOptions.create(
                        rp = testRp,
                        user = testUser,
                        challenge = "test_challenge".toByteArray(),
                        // Negative timeout is invalid
                        timeout = -1L,
                        selectedAlgId = com.chimali.fido2.data.crypto.Fido2CryptoService.COSE_ES256,
                    )
                }
            }

        @Test
        fun `should fail when credential creation validation fails`() =
            runTest {
                coEvery { credentialRepository.validateCredentialCreation(any(), any()) } returns
                    Outcome.Error(
                        DomainError.OperationDenied(
                            "Credential creation not allowed",
                            Fido2Exception.CredentialCreationNotAllowed("Credential creation not allowed"),
                        ),
                    )

                val result = registerCredentialUseCase(testOptions)

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is Fido2Exception.CredentialCreationNotAllowed)
            }
    }

    @Nested
    inner class UserVerificationFailureTests {
        @Test
        fun `should fail when no verification method is available`() =
            runTest {
                coEvery { userVerificationService.getUserVerificationAvailability() } returns
                    UserVerificationAvailability(
                        isBiometricAvailable = false,
                        isPinAvailable = false,
                        isDeviceLockAvailable = false,
                        supportedBiometricTypes = emptyList(),
                        maxPinLength = 0,
                        minPinLength = 0,
                        biometricStrength = BiometricStrength.WEAK,
                    )

                val result = registerCredentialUseCase(testOptions)

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is Fido2Exception.NoVerificationMethodAvailable)
            }

        @Test
        fun `should fail when user consent recording fails`() =
            runTest {
                coEvery { userVerificationService.recordUserConsent(any()) } returns
                    Outcome.Error(
                        DomainError.OperationDenied(
                            "Consent denied",
                            Fido2Exception.ConsentDenied("Consent denied"),
                        ),
                    )

                val result = registerCredentialUseCase(testOptions)

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is Fido2Exception.ConsentDenied)
            }
    }

    @Nested
    inner class StorageFailureTests {
        @Test
        fun `should fail when credential storage fails`() =
            runTest {
                coEvery { credentialRepository.saveCredential(any()) } returns
                    Outcome.Error(
                        DomainError.DatabaseError(
                            "Credential storage failed",
                            Fido2Exception.CredentialStorageFailed("Credential storage failed"),
                        ),
                    )

                val result = registerCredentialUseCase(testOptions)

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is Fido2Exception.CredentialStorageFailed)
            }

        @Test
        fun `should fail when rp update fails`() =
            runTest {
                coEvery { credentialRepository.saveRelyingParty(any()) } returns
                    Outcome.Error(
                        DomainError.DatabaseError(
                            "Relying party update failed",
                            Fido2Exception.RelyingPartyUpdateFailed("Relying party update failed"),
                        ),
                    )

                val result = registerCredentialUseCase(testOptions)

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is Fido2Exception.RelyingPartyUpdateFailed)
            }
    }

    @Nested
    inner class AlgorithmSupportTests {
        @Test
        fun `should support es256 algorithm`() =
            runTest {
                val es256Options =
                    testOptions.copy(
                        pubKeyCredParams = PublicKeyCredentialParameters.createES256P256(),
                    )

                val result = registerCredentialUseCase(es256Options)

                assertTrue(result.isSuccess)
            }

        @Test
        fun `should support rs256 algorithm`() =
            runTest {
                val rs256Options =
                    testOptions.copy(
                        pubKeyCredParams = PublicKeyCredentialParameters.createRS256(),
                    )

                val result = registerCredentialUseCase(rs256Options)

                assertTrue(result.isSuccess)
            }

        @Test
        fun `should fail with unsupported algorithm`() =
            runTest {
                // PublicKeyCredentialParameters validates algorithm in init, so creating with unsupported
                // algorithm throws IllegalArgumentException before the use case is even called.
                assertFailsWith<IllegalArgumentException> {
                    PublicKeyCredentialParameters.create(algorithm = "UNSUPPORTED")
                }
            }
    }

    @Nested
    inner class VerificationPreferenceTests {
        @Test
        fun `should handle preferred verification with biometric available`() =
            runTest {
                coEvery {
                    userVerificationService.isUserVerificationRequired(any(), any(), any())
                } returns com.chimali.fido2.domain.service.UserVerificationRequirement.PREFERRED

                val preferredOptions =
                    testOptions.copy(
                        authenticatorSelection =
                            AuthenticatorSelectionCriteria.create(
                                userVerification = com.chimali.fido2.domain.model.UserVerificationRequirement.PREFERRED,
                            ),
                    )

                val result = registerCredentialUseCase(preferredOptions)

                assertTrue(result.isSuccess)
                coVerify(exactly = 0) { userVerificationService.getUserVerificationAvailability() }
            }

        @Test
        fun `should handle discouraged verification`() =
            runTest {
                coEvery {
                    userVerificationService.isUserVerificationRequired(any(), any(), any())
                } returns com.chimali.fido2.domain.service.UserVerificationRequirement.DISCOURAGED

                val discouragedOptions =
                    testOptions.copy(
                        authenticatorSelection =
                            AuthenticatorSelectionCriteria.create(
                                userVerification =
                                    com.chimali.fido2.domain.model.UserVerificationRequirement.DISCOURAGED,
                            ),
                    )

                val result = registerCredentialUseCase(discouragedOptions)

                assertTrue(result.isSuccess)
                coVerify(exactly = 0) { userVerificationService.getUserVerificationAvailability() }
            }
    }

    @Nested
    inner class EdgeCases {
        @Test
        fun `should handle exclude credentials list`() =
            runTest {
                val excludeCredentials =
                    listOf(
                        PublicKeyCredentialDescriptor.create(
                            type = PublicKeyCredentialType.PUBLIC_KEY,
                            id = CredentialId.fromEncoded("existing_credential"),
                        ),
                    )

                val optionsWithExcludes =
                    testOptions.copy(
                        excludeCredentials = excludeCredentials,
                    )

                val result = registerCredentialUseCase(optionsWithExcludes)
                assertTrue(result.isSuccess, "Failed with exception: ${result.exceptionOrNull()?.message}")
            }

        @Test
        fun `should handle allow credentials list`() =
            runTest {
                val allowCredentials =
                    listOf(
                        PublicKeyCredentialDescriptor.create(
                            type = PublicKeyCredentialType.PUBLIC_KEY,
                            id = CredentialId.fromEncoded("allowed_credential"),
                        ),
                    )

                val optionsWithAllows =
                    testOptions.copy(
                        allowCredentials = allowCredentials,
                    )

                val result = registerCredentialUseCase(optionsWithAllows)
                assertTrue(result.isSuccess, "Failed with exception: ${result.exceptionOrNull()?.message}")
            }

        @Test
        fun `should handle resident key requirements`() =
            runTest {
                val residentKeyOptions =
                    testOptions.copy(
                        authenticatorSelection =
                            AuthenticatorSelectionCriteria.create(
                                requireResidentKey = ResidentKeyRequirement.REQUIRED,
                                userVerification = com.chimali.fido2.domain.model.UserVerificationRequirement.REQUIRED,
                            ),
                    )

                val result = registerCredentialUseCase(residentKeyOptions)

                assertTrue(result.isSuccess)
            }

        @Test
        fun `should handle attestation preferences`() =
            runTest {
                val directAttestationOptions =
                    testOptions.copy(
                        attestation = AttestationConveyancePreference.DIRECT,
                    )

                val result = registerCredentialUseCase(directAttestationOptions)

                assertTrue(result.isSuccess)
                val makeResult = result.getOrThrow()
                val attestationObject = makeResult.attestationObject
                assertEquals("packed", attestationObject.fmt) // packed self-attestation from HDK key
            }
    }

    /**
     * T115b (FR-HID-022) — Dynamic credential global storage limit.
     *
     * Verifies that [RegisterCredentialUseCase] enforces the maximum credential count
     * by consulting [CredentialStatistics.totalCredentials] and [Fido2SettingsRepository]
     * before generating a new credential.
     */
    @Nested
    inner class CredentialLimitTests {
        private fun stubCountAndLimit(
            count: Int,
            limit: Int,
        ) {
            coEvery { credentialRepository.getCredentialStatistics() } returns
                com.chimali.fido2.domain.repository.CredentialStatistics(
                    totalCredentials = count,
                    credentialsByRp = emptyMap(),
                    expiredCredentials = 0,
                    recentlyUsedCredentials = 0,
                    credentialsRequiringUserVerification = 0,
                    averageAgeDays = 0.0,
                )
            coEvery { fido2SettingsRepository.getMaxCredentialCount() } returns limit
        }

        @Test
        fun `T115b registration succeeds below limit`() =
            runTest {
                stubCountAndLimit(count = COUNT_49, limit = LIMIT_50)
                val result = registerCredentialUseCase(testOptions)
                assertTrue(
                    result.isSuccess,
                    "Expected success at 49/50",
                )
            }

        @Test
        fun `T115b registration fails at limit`() =
            runTest {
                stubCountAndLimit(count = LIMIT_1000, limit = LIMIT_1000)
                val result = registerCredentialUseCase(testOptions)
                assertTrue(result.isFailure, "Expected failure at 1000/1000")
                assertTrue(result.exceptionOrNull() is Fido2Exception.TooManyCredentials)
                val exception = result.exceptionOrNull() as Fido2Exception.TooManyCredentials
                assertEquals(LIMIT_1000, exception.limit, "Exception should report the correct limit reached")
            }

        @Test
        fun `T115b registration respects increased limit`() =
            runTest {
                stubCountAndLimit(count = LIMIT_1000, limit = LIMIT_2000)
                val result = registerCredentialUseCase(testOptions)
                assertTrue(
                    result.isSuccess,
                    "Expected success at 1000/2000",
                )
            }
    }
}

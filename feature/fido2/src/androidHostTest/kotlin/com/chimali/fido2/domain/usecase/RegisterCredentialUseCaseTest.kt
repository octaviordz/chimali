package com.chimali.fido2.domain.usecase
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.exceptionOrNull
import com.chimali.core.common.result.getOrThrow
import com.chimali.core.common.result.isFailure
import com.chimali.core.common.result.isSuccess
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.core.security.api.GeneratedCredentialPublicKey
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.ClientDataHashService
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.AttestationConveyancePreference
import com.chimali.fido2.domain.model.AuthenticatorSelectionCriteria
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.PasskeyCredential
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
import com.chimali.fido2.testing.testCredentialIdFromLabel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.security.KeyPairGenerator
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Req("NFR-SEC-041")
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
        private const val PROVIDER_EXCEPTION_MARKER = "synthetic-private-material-marker"
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
                    id = UserId.fromByteArray("user123".encodeToByteArray()),
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
                    selectedAlgId = CredentialKeyDescriptor.COSE_ES256,
                )

            // Setup default mock responses
            coEvery { cryptoService.generateCredentialPublicKey(any()) } returns
                Outcome.Success(GeneratedCredentialPublicKey(ByteArray(PUB_KEY_SIZE_65) { DUMMY_BYTE_01 }))
            every { cryptoService.decodePublicKey(any(), any()) } returns testPublicKey
            coEvery { cborCodec.encodeCosePublicKeyFromJavaKey(any()) } returns ByteArray(PUB_KEY_ENCODED_SIZE_77)
            coEvery {
                userVerificationService.isUserVerificationRequiredForText(any(), any(), any())
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
            coEvery {
                credentialRepository.validateCredentialCreationForText(any(), any())
            } returns Outcome.Success(Unit)
            coEvery { credentialRepository.saveCredential(any()) } returns Outcome.Success(Unit)
            coEvery { credentialRepository.getRelyingPartyForText(any()) } returns null
            coEvery { credentialRepository.updateRelyingParty(any(), any()) } returns Outcome.Success(Unit)
            coEvery {
                credentialRepository.saveRelyingParty(any<RelyingParty>())
            } returns Outcome.Success(Unit)
            // T115a: Stub getCredentialStatistics so the quota check in RegisterCredentialUseCase can proceed.
            // Default: 0 credentials stored → registration allowed.
            coEvery { credentialRepository.getCredentialStatistics() } returns
                com.chimali.fido2.domain.repository.CredentialStatistics(
                    totalCredentials = 0,
                    expiredCredentials = 0,
                    recentlyUsedCredentials = 0,
                    credentialsRequiringUserVerification = 0,
                    averageAgeDays = 0.0,
                )
            // T145b: stub sign() so the packed attestation path succeeds in tests
            coEvery {
                cryptoService.sign(any(), any(), any())
            } returns Outcome.Success(ByteArray(SIGNATURE_SIZE_72) { DUMMY_BYTE_30 })
        }

    @Nested
    inner class SuccessfulRegistrationTests {
        /** @see NFR-SEC-041 */
        @Test
        fun `provider exception details are not returned from credential generation`() =
            runTest {
                coEvery { cryptoService.generateCredentialPublicKey(any()) } throws
                    java.security.GeneralSecurityException(PROVIDER_EXCEPTION_MARKER)

                val result = registerCredentialUseCase(testOptions, userVerificationPerformed = true)

                val error = assertIs<DomainError.CryptoError>(assertIs<Outcome.Error<*>>(result).error)
                assertEquals("Credential cryptographic operation failed", error.message)
                assertNull(error.cause)
                assertFalse(error.message.contains(PROVIDER_EXCEPTION_MARKER))
                coVerify(exactly = 0) { credentialRepository.saveCredential(any()) }
            }

        /** @see NFR-SEC-041 */
        @Test
        fun `unexpected crypto argument failure does not expose its details`() =
            runTest {
                coEvery { cryptoService.generateCredentialPublicKey(any()) } throws
                    IllegalArgumentException(PROVIDER_EXCEPTION_MARKER)

                val result = registerCredentialUseCase(testOptions, userVerificationPerformed = true)

                val error = assertIs<DomainError.ValidationError>(assertIs<Outcome.Error<*>>(result).error)
                assertEquals("Invalid credential request", error.message)
                assertNull(error.cause)
                assertFalse(error.message.contains(PROVIDER_EXCEPTION_MARKER))
                coVerify(exactly = 0) { credentialRepository.saveCredential(any()) }
            }

        /** @see NFR-SEC-041 */
        @Test
        fun `unexpected crypto state failure does not expose its details`() =
            runTest {
                coEvery { cryptoService.generateCredentialPublicKey(any()) } throws
                    IllegalStateException(PROVIDER_EXCEPTION_MARKER)

                val result = registerCredentialUseCase(testOptions, userVerificationPerformed = true)

                val error = assertIs<DomainError.OperationDenied>(assertIs<Outcome.Error<*>>(result).error)
                assertEquals("Credential operation is unavailable", error.message)
                assertNull(error.cause)
                assertFalse(error.message.contains(PROVIDER_EXCEPTION_MARKER))
                coVerify(exactly = 0) { credentialRepository.saveCredential(any()) }
            }

        /** @see NFR-SEC-041 */
        @Test
        fun `self attestation failure is returned before profile persistence`() =
            runTest {
                var retainedCredential: PasskeyCredential? = null
                coEvery { cryptoService.sign(any(), any(), any()) } returns
                    Outcome.Error(DomainError.CryptoError("synthetic signing failure"))

                val result =
                    registerCredentialUseCase.invokeWithCredentialOwnerObserver(
                        testOptions,
                        userVerificationPerformed = true,
                        onCredentialOwned = { retainedCredential = it },
                    )

                assertTrue(result.isFailure)
                assertCredentialTextErased(requireNotNull(retainedCredential))
                coVerify(exactly = 0) { credentialRepository.saveCredential(any()) }
            }

        /** @see NFR-SEC-041 */
        @Test
        fun `cancellation during self attestation propagates before profile persistence`() =
            runTest {
                var retainedCredential: PasskeyCredential? = null
                coEvery { cryptoService.sign(any(), any(), any()) } throws CancellationException("synthetic cancel")

                assertFailsWith<CancellationException> {
                    registerCredentialUseCase.invokeWithCredentialOwnerObserver(
                        testOptions,
                        userVerificationPerformed = true,
                        onCredentialOwned = { retainedCredential = it },
                    )
                }
                assertCredentialTextErased(requireNotNull(retainedCredential))
                coVerify(exactly = 0) { credentialRepository.saveCredential(any()) }
            }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `credential persistence failure erases the retained registration owner`() =
            runTest {
                var retainedCredential: PasskeyCredential? = null
                coEvery { credentialRepository.saveCredential(any()) } returns
                    Outcome.Error(DomainError.StorageError("synthetic persistence failure"))

                val result =
                    registerCredentialUseCase.invokeWithCredentialOwnerObserver(
                        testOptions,
                        userVerificationPerformed = true,
                        onCredentialOwned = { retainedCredential = it },
                    )

                assertTrue(result.isFailure)
                assertCredentialTextErased(requireNotNull(retainedCredential))
                coVerify(exactly = 1) { credentialRepository.saveCredential(any()) }
            }

        /** @see NFR-SEC-041 */
        @Test
        fun `should successfully register credential with biometric verification`() =
            runTest {
                val result = registerCredentialUseCase(testOptions, userVerificationPerformed = true)

                assertTrue(result.isSuccess, "Result failed with exception: ${result.exceptionOrNull()?.message}")
                val makeResult = result.getOrThrow()
                val attestationObject = makeResult.attestationObject
                val credential = makeResult.credential

                assertNotNull(attestationObject)
                assertNotNull(credential)
                assertEquals("packed", attestationObject.fmt)
                assertEquals(testRp.id.value, credential.rpId.toBoundaryString())
                assertEquals("testuser", credential.userName.toBoundaryString())

                // Verify all expected interactions
                coVerify { userVerificationService.isUserVerificationRequiredForText(any(), any(), any()) }
                coVerify { userVerificationService.recordUserConsent(any()) }
                coVerify { credentialRepository.validateCredentialCreationForText(any(), any()) }
                coVerify { credentialRepository.saveCredential(any()) }
                coVerify { credentialRepository.saveRelyingParty(any()) }
            }

        /** @see NFR-SEC-041 */
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

                val result = registerCredentialUseCase(testOptions, userVerificationPerformed = true)

                assertTrue(result.isSuccess, "Result failed with exception: ${result.exceptionOrNull()?.message}")
                val makeResult = result.getOrThrow()
                assertNotNull(makeResult.attestationObject)
                assertNotNull(makeResult.credential)

                assertTrue(
                    makeResult.attestationObject.authData.flags[0]
                        .toInt() and 0x04 != 0,
                )
            }

        /** @see NFR-SEC-041 */
        @Test
        fun `should successfully register credential without verification when not required`() =
            runTest {
                // Mock no verification required
                coEvery {
                    userVerificationService.isUserVerificationRequiredForText(any(), any(), any())
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
                        selectedAlgId = CredentialKeyDescriptor.COSE_ES256,
                    )

                val result = registerCredentialUseCase(optionsNotRequired)

                assertTrue(result.isSuccess, "Result failed with exception: ${result.exceptionOrNull()?.message}")
                val makeResult = result.getOrThrow()
                assertNotNull(makeResult.attestationObject)
                assertNotNull(makeResult.credential)

                // Verify no verification was performed
                coVerify(exactly = 0) { userVerificationService.getUserVerificationAvailability() }
            }

        /** @see NFR-SEC-041 */
        @Test
        fun `should update existing rp information`() =
            runTest {
                val existingRp =
                    RelyingParty
                        .create(
                            id = RpId("https://example.com"),
                            name = "Example Website",
                        ).copy(credentialCount = COUNT_3)

                coEvery { credentialRepository.getRelyingPartyForText(any()) } returns existingRp

                val result = registerCredentialUseCase(testOptions, userVerificationPerformed = true)

                assertTrue(result.isSuccess, "Result failed with exception: ${result.exceptionOrNull()?.message}")

                // Verify RP was updated with incremented credential count
                coVerify { credentialRepository.saveRelyingParty(any()) }
            }
    }

    @Nested
    inner class ValidationFailureTests {
        /** @see NFR-SEC-041 */
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

        /** @see NFR-SEC-041 */
        @Test
        fun `should fail when user validation fails`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    PublicKeyCredentialUserEntity.create(
                        id = UserId.fromByteArray("user123".encodeToByteArray()),
                        // Blank name is invalid
                        name = "",
                        displayName = "Test User",
                    )
                }
            }

        /** @see NFR-SEC-041 */
        @Test
        fun `should fail when challenge is empty`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    MakeCredentialOptions.create(
                        rp = testRp,
                        user = testUser,
                        // Empty challenge is invalid
                        challenge = ByteArray(0),
                        selectedAlgId = CredentialKeyDescriptor.COSE_ES256,
                    )
                }
            }

        /** @see NFR-SEC-041 */
        @Test
        fun `should fail when challenge exceeds maximum size`() =
            runTest {
                assertFailsWith<IllegalArgumentException> {
                    MakeCredentialOptions.create(
                        rp = testRp,
                        user = testUser,
                        // Max is 64
                        challenge = ByteArray(PUB_KEY_SIZE_65),
                        selectedAlgId = CredentialKeyDescriptor.COSE_ES256,
                    )
                }
            }

        /** @see NFR-SEC-041 */
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
                        selectedAlgId = CredentialKeyDescriptor.COSE_ES256,
                    )
                }
            }

        /** @see NFR-SEC-041 */
        @Test
        fun `should fail when credential creation validation fails`() =
            runTest {
                coEvery { credentialRepository.validateCredentialCreationForText(any(), any()) } returns
                    Outcome.Error(
                        DomainError.OperationDenied(
                            "Credential creation not allowed",
                            Fido2Exception.CredentialCreationNotAllowed("Credential creation not allowed"),
                        ),
                    )

                val result = registerCredentialUseCase(testOptions, userVerificationPerformed = true)

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is Fido2Exception.CredentialCreationNotAllowed)
            }
    }

    @Nested
    inner class UserVerificationFailureTests {
        /** @see NFR-SEC-041 */
        @Test
        fun `required registration failure stops before key generation or persistence`() =
            runTest {
                val result = registerCredentialUseCase(testOptions)

                assertIs<Outcome.Error<*>>(result)
                assertIs<DomainError.OperationDenied>(result.error)
                coVerify(exactly = 0) { cryptoService.generateCredentialPublicKey(any()) }
                coVerify(exactly = 0) { cryptoService.sign(any(), any(), any()) }
                coVerify(exactly = 0) { credentialRepository.saveCredential(any()) }
            }

        /** @see NFR-SEC-041 */
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

                val result = registerCredentialUseCase(testOptions, userVerificationPerformed = true)

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is Fido2Exception.ConsentDenied)
            }
    }

    @Nested
    inner class StorageFailureTests {
        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Test
        fun `should fail when credential storage fails`() =
            runTest {
                val storedCredential = slot<PasskeyCredential>()
                coEvery { credentialRepository.saveCredential(capture(storedCredential)) } returns
                    Outcome.Error(
                        DomainError.DatabaseError(
                            "Credential storage failed",
                            Fido2Exception.CredentialStorageFailed("Credential storage failed"),
                        ),
                    )

                val result = registerCredentialUseCase(testOptions, userVerificationPerformed = true)

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is Fido2Exception.CredentialStorageFailed)
                assertCredentialTextErased(storedCredential.captured)
            }

        /** @see NFR-SEC-041 */
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

                val result = registerCredentialUseCase(testOptions, userVerificationPerformed = true)

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is Fido2Exception.RelyingPartyUpdateFailed)
            }
    }

    @Nested
    inner class AlgorithmSupportTests {
        /** @see NFR-SEC-041 */
        @Test
        fun `registration uses one descriptor for P-256 Ed25519 and ML-DSA key generation and self attestation`() =
            runTest {
                val cases =
                    listOf(
                        Triple(
                            CredentialKeyDescriptor.COSE_ES256,
                            PublicKeyCredentialParameters.createES256P256(),
                            CredentialKeyDescriptor.P256_PROFILE,
                        ),
                        Triple(
                            CredentialKeyDescriptor.COSE_EDDSA,
                            PublicKeyCredentialParameters.createEdDsa(),
                            CredentialKeyDescriptor.ED25519_PROFILE,
                        ),
                        Triple(
                            CredentialKeyDescriptor.COSE_ML_DSA_65,
                            PublicKeyCredentialParameters.createMlDsa65(),
                            CredentialKeyDescriptor.MLDSA65_PROFILE,
                        ),
                    )

                cases.forEach { (algorithm, parameters, profile) ->
                    val options = testOptions.copy(pubKeyCredParams = parameters, selectedAlgId = algorithm)
                    val result = registerCredentialUseCase(options, userVerificationPerformed = true)

                    assertTrue(result.isSuccess, "Registration should succeed for algorithm $algorithm")
                    coVerify(exactly = 1) {
                        cryptoService.generateCredentialPublicKey(
                            match { it.coseAlgorithm == algorithm && it.derivationProfile == profile },
                        )
                    }
                    coVerify(exactly = 1) {
                        cryptoService.sign(
                            match { it.coseAlgorithm == algorithm && it.derivationProfile == profile },
                            any(),
                            any(),
                        )
                    }
                }
            }

        /** @see NFR-SEC-041 */
        @Test
        fun `should support es256 algorithm`() =
            runTest {
                val es256Options =
                    testOptions.copy(
                        pubKeyCredParams = PublicKeyCredentialParameters.createES256P256(),
                    )

                val result = registerCredentialUseCase(es256Options, userVerificationPerformed = true)

                assertTrue(result.isSuccess)
            }

        /** @see NFR-SEC-041 */
        @Test
        fun `should support rs256 algorithm`() =
            runTest {
                val rs256Options =
                    testOptions.copy(
                        pubKeyCredParams = PublicKeyCredentialParameters.createRS256(),
                    )

                val result = registerCredentialUseCase(rs256Options, userVerificationPerformed = true)

                assertTrue(result.isSuccess)
            }

        /** @see NFR-SEC-041 */
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
        /** @see NFR-SEC-041 */
        @Test
        fun `should handle preferred verification with biometric available`() =
            runTest {
                coEvery {
                    userVerificationService.isUserVerificationRequiredForText(any(), any(), any())
                } returns com.chimali.fido2.domain.service.UserVerificationRequirement.PREFERRED

                val preferredOptions =
                    testOptions.copy(
                        authenticatorSelection =
                            AuthenticatorSelectionCriteria.create(
                                userVerification = com.chimali.fido2.domain.model.UserVerificationRequirement.PREFERRED,
                            ),
                    )

                val result = registerCredentialUseCase(preferredOptions, userVerificationPerformed = true)

                assertTrue(result.isSuccess)
                val flags =
                    result
                        .getOrThrow()
                        .attestationObject.authData.flags
                assertTrue(flags[0].toInt() and 0x04 != 0)
                assertEquals(
                    flags[0],
                    result
                        .getOrThrow()
                        .attestationObject.attStmt.authData!![32],
                )
                coVerify(exactly = 0) { userVerificationService.getUserVerificationAvailability() }
            }

        /** @see NFR-SEC-041 */
        @Test
        fun `should handle discouraged verification`() =
            runTest {
                coEvery {
                    userVerificationService.isUserVerificationRequiredForText(any(), any(), any())
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
                val flags =
                    result
                        .getOrThrow()
                        .attestationObject.authData.flags
                assertEquals(0, flags[0].toInt() and 0x04)
                assertEquals(
                    flags[0],
                    result
                        .getOrThrow()
                        .attestationObject.attStmt.authData!![32],
                )
                coVerify(exactly = 0) { userVerificationService.getUserVerificationAvailability() }
            }
    }

    @Nested
    inner class EdgeCases {
        /** @see NFR-SEC-041 */
        @Test
        fun `should handle exclude credentials list`() =
            runTest {
                val excludeCredentials =
                    listOf(
                        PublicKeyCredentialDescriptor.create(
                            type = PublicKeyCredentialType.PUBLIC_KEY,
                            id = testCredentialIdFromLabel("existing_credentiakAZXhpc3RpbmdfY3JlZGVudGlhbA"),
                        ),
                    )

                val optionsWithExcludes =
                    testOptions.copy(
                        excludeCredentials = excludeCredentials,
                    )

                val result = registerCredentialUseCase(optionsWithExcludes, userVerificationPerformed = true)
                assertTrue(result.isSuccess, "Failed with exception: ${result.exceptionOrNull()?.message}")
            }

        /** @see NFR-SEC-041 */
        @Test
        fun `should handle allow credentials list`() =
            runTest {
                val allowCredentials =
                    listOf(
                        PublicKeyCredentialDescriptor.create(
                            type = PublicKeyCredentialType.PUBLIC_KEY,
                            id = testCredentialIdFromLabel("allowed_credentiagBhbGxvd2VkX2NyZWRlbnRpYWw"),
                        ),
                    )

                val optionsWithAllows =
                    testOptions.copy(
                        allowCredentials = allowCredentials,
                    )

                val result = registerCredentialUseCase(optionsWithAllows, userVerificationPerformed = true)
                assertTrue(result.isSuccess, "Failed with exception: ${result.exceptionOrNull()?.message}")
            }

        /** @see NFR-SEC-041 */
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

                val result = registerCredentialUseCase(residentKeyOptions, userVerificationPerformed = true)

                assertTrue(result.isSuccess)
            }

        /** @see NFR-SEC-041 */
        @Test
        fun `should handle attestation preferences`() =
            runTest {
                val directAttestationOptions =
                    testOptions.copy(
                        attestation = AttestationConveyancePreference.DIRECT,
                    )

                val result = registerCredentialUseCase(directAttestationOptions, userVerificationPerformed = true)

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
                    expiredCredentials = 0,
                    recentlyUsedCredentials = 0,
                    credentialsRequiringUserVerification = 0,
                    averageAgeDays = 0.0,
                )
            coEvery { fido2SettingsRepository.getMaxCredentialCount() } returns limit
        }

        /** @see NFR-SEC-041 and TR-AUTH-031 */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        @Test
        fun `registration preserves Unicode account and independently expected RP hash through owned path`() =
            runTest {
                stubCountAndLimit(count = 0, limit = LIMIT_50)
                val unicodeRp = PublicKeyCredentialRpEntity.create(RpId("https://example.com"), "München Web")
                val unicodeIdBytes = "usuario-ñ".encodeToByteArray()
                val unicodeUser =
                    PublicKeyCredentialUserEntity.create(
                        UserId.fromByteArray(unicodeIdBytes),
                        "José",
                        "José 用户",
                    )
                val options = testOptions.copy(rp = unicodeRp, user = unicodeUser)
                val signedData = slot<ByteArray>()
                coEvery { cryptoService.sign(any(), capture(signedData), any()) } returns
                    Outcome.Success(ByteArray(SIGNATURE_SIZE_72) { DUMMY_BYTE_30 })
                val credentialSlot = slot<PasskeyCredential>()
                coEvery { credentialRepository.saveCredential(capture(credentialSlot)) } returns Outcome.Success(Unit)
                val expectedRpHash =
                    java.security.MessageDigest
                        .getInstance("SHA-256")
                        .digest("https://example.com".encodeToByteArray())

                val result = registerCredentialUseCase(options, userVerificationPerformed = true)

                assertTrue(result.isSuccess)
                val credential = credentialSlot.captured
                assertEquals("José", credential.userName.toBoundaryString())
                assertEquals("José 用户", credential.userDisplayName.toBoundaryString())
                assertEquals(UserId.fromByteArray(unicodeIdBytes).value, credential.userId.toBoundaryString())
                assertContentEquals(expectedRpHash, signedData.captured.copyOfRange(0, expectedRpHash.size))
                assertEquals("München Web", unicodeRp.name)
                assertEquals("José", unicodeUser.name)
                credential.eraseSensitiveText()
                unicodeRp.eraseSensitiveText()
                unicodeUser.eraseSensitiveText()
                unicodeIdBytes.fill(0)
            }

        /** @see NFR-SEC-041 */
        @Test
        fun `T115b registration succeeds below limit`() =
            runTest {
                stubCountAndLimit(count = COUNT_49, limit = LIMIT_50)
                val result = registerCredentialUseCase(testOptions, userVerificationPerformed = true)
                assertTrue(
                    result.isSuccess,
                    "Expected success at 49/50",
                )
            }

        /** @see NFR-SEC-041 */
        @Test
        fun `T115b registration fails at limit`() =
            runTest {
                stubCountAndLimit(count = LIMIT_1000, limit = LIMIT_1000)
                val result = registerCredentialUseCase(testOptions, userVerificationPerformed = true)
                assertTrue(result.isFailure, "Expected failure at 1000/1000")
                assertTrue(result.exceptionOrNull() is Fido2Exception.TooManyCredentials)
                val exception = result.exceptionOrNull() as Fido2Exception.TooManyCredentials
                assertEquals(LIMIT_1000, exception.limit, "Exception should report the correct limit reached")
            }

        /** @see NFR-SEC-041 */
        @Test
        fun `T115b registration respects increased limit`() =
            runTest {
                stubCountAndLimit(count = LIMIT_1000, limit = LIMIT_2000)
                val result = registerCredentialUseCase(testOptions, userVerificationPerformed = true)
                assertTrue(
                    result.isSuccess,
                    "Expected success at 1000/2000",
                )
            }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @ParameterizedTest
    @ValueSource(
        strings = [
            "success", "rp-failure", "consent-failure", "validation-cancel",
            "persistence-failure", "persistence-cancel",
        ],
    )
    fun `registration erases RP consent and encoded user owners on every terminal path`(scenario: String) =
        runTest {
            val rpOwners = mutableListOf<RelyingParty>()
            val consentOwners = mutableListOf<com.chimali.core.domain.model.UserConsentRecord>()
            val encodedUsers = mutableListOf<CharSequence>()
            val attestations = mutableListOf<com.chimali.fido2.domain.model.AttestationObject>()
            coEvery { credentialRepository.saveCredential(any()) } coAnswers {
                if (scenario == "persistence-cancel") {
                    throw kotlinx.coroutines.CancellationException("synthetic cancellation")
                }
                if (scenario == "persistence-failure") {
                    Outcome.Error(DomainError.StorageError("synthetic failure"))
                } else {
                    Outcome.Success(Unit)
                }
            }
            coEvery { credentialRepository.saveRelyingParty(any()) } coAnswers {
                val model = firstArg<RelyingParty>()
                rpOwners += model
                assertTrue(model.idText.contentEquals(testOptions.rp.idText))
                if (scenario == "rp-failure") {
                    Outcome.Error(DomainError.StorageError("synthetic failure"))
                } else {
                    Outcome.Success(Unit)
                }
            }
            coEvery { userVerificationService.recordUserConsent(any()) } coAnswers {
                val model = firstArg<com.chimali.core.domain.model.UserConsentRecord>()
                consentOwners += model
                assertTrue(model.rpIdText.contentEquals(testOptions.rp.idText))
                if (scenario == "consent-failure") {
                    Outcome.Error(DomainError.OperationDenied("synthetic failure"))
                } else {
                    Outcome.Success(Unit)
                }
            }
            coEvery { credentialRepository.validateCredentialCreationForText(any(), any()) } coAnswers {
                assertTrue(firstArg<CharSequence>().contentEquals(testOptions.rp.idText))
                encodedUsers += secondArg<CharSequence>()
                if (scenario == "validation-cancel") {
                    throw kotlinx.coroutines.CancellationException("synthetic cancellation")
                }
                Outcome.Success(Unit)
            }
            if (scenario.endsWith("-cancel")) {
                assertFailsWith<kotlinx.coroutines.CancellationException> {
                    registerCredentialUseCase.invokeWithCredentialOwnerObserver(
                        testOptions,
                        true,
                        onAttestationOwned = attestations::add,
                        onCredentialOwned = {},
                    )
                }
            } else {
                val result =
                    registerCredentialUseCase.invokeWithCredentialOwnerObserver(
                        testOptions,
                        true,
                        onAttestationOwned = attestations::add,
                        onCredentialOwned = {},
                    )
                assertEquals(scenario == "success", result.isSuccess)
                if (result is Outcome.Success) {
                    assertTrue(
                        result.data.attestationObject.clientData.originText
                            .contentEquals(testOptions.rp.idText),
                    )
                    result.data.eraseSensitiveText()
                }
            }
            assertEquals(1, rpOwners.size)
            rpOwners.forEach {
                assertTrue(it.idText.all { character -> character == '\u0000' })
                assertTrue(it.nameText.all { character -> character == '\u0000' })
            }
            assertEquals(if (scenario == "rp-failure") 0 else 1, consentOwners.size)
            consentOwners.forEach { assertTrue(it.rpIdText.all { character -> character == '\u0000' }) }
            assertEquals(if (scenario !in setOf("rp-failure", "consent-failure")) 1 else 0, encodedUsers.size)
            encodedUsers.forEach { assertTrue(it.all { character -> character == '\u0000' }) }
            assertEquals(if (scenario == "success" || scenario.startsWith("persistence-")) 1 else 0, attestations.size)
            attestations.forEach { assertTrue(it.clientData.originText.all { char -> char == '\u0000' }) }
            assertTrue(testOptions.rp.idText.contentEquals("https://example.com"))
            assertEquals("Test User", testOptions.user.displayName)
        }
}

private fun assertCredentialTextErased(credential: PasskeyCredential) {
    listOf(credential.rpId, credential.userId, credential.userName, credential.userDisplayName).forEach { owner ->
        val characters = owner.copyCharacters()
        assertContentEquals(CharArray(characters.size), characters)
        characters.fill('\u0000')
    }
}

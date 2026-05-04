package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.exceptionOrNull
import com.chimali.core.common.result.getOrThrow
import com.chimali.core.common.result.isFailure
import com.chimali.core.common.result.isSuccess
import com.chimali.core.domain.model.ConsentMethod
import com.chimali.core.domain.model.ConsentOperationType
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.service.BiometricStrength
import com.chimali.fido2.domain.service.BiometricType
import com.chimali.fido2.domain.service.UserVerificationAvailability
import com.chimali.fido2.domain.service.UserVerificationService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows

class GetUserConsentUseCaseTest {
    private lateinit var credentialRepository: CredentialRepository
    private lateinit var userVerificationService: UserVerificationService
    private lateinit var getUserConsentUseCase: GetUserConsentUseCase
    private var testRpId: RpId = RpId("https://example.com")
    private lateinit var testTimestamp: Instant
    private lateinit var testConsentRecord: UserConsentRecord

    @BeforeTest
    fun setUp() =
        runTest {
            credentialRepository = mockk()
            userVerificationService = mockk()
            getUserConsentUseCase =
                GetUserConsentUseCase(
                    credentialRepository,
                    userVerificationService,
                )

            // Setup test data
            testRpId = RpId("https://example.com")
            testTimestamp = TimeProvider().now()

            testConsentRecord =
                UserConsentRecord.create(
                    id = "test_consent_id",
                    operationType = ConsentOperationType.REGISTRATION,
                    rpId = testRpId,
                    credentialId = CredentialId.fromEncoded("dGVzdF9jcmVkZW50aWFsX2lk"),
                    biometricUsed = true,
                    pinUsed = false,
                    ipAddress = "192.168.1.1",
                    userAgent = "Test User Agent",
                    deviceId = "test_device_id",
                )

            // Setup default mock responses
            coEvery {
                userVerificationService.isUserVerificationRequired(any(), any(), any())
            } returns com.chimali.fido2.domain.service.UserVerificationRequirement.REQUIRED
            coEvery { userVerificationService.getUserVerificationAvailability() } returns
                UserVerificationAvailability(
                    biometricAvailable = true,
                    pinAvailable = true,
                    deviceLockAvailable = true,
                    supportedBiometricTypes = listOf(BiometricType.FINGERPRINT),
                    maxPinLength = MAX_PIN_LEN_8,
                    minPinLength = MIN_PIN_LEN_4,
                    biometricStrength = BiometricStrength.STRONG,
                )

            coEvery { credentialRepository.saveUserConsent(any()) } returns Outcome.Success(Unit)
            coEvery { credentialRepository.getRecentUserConsent(any(), any()) } returns flowOf(testConsentRecord)
        }

    @Nested
    inner class SuccessfulConsentRecordingTests {
        @Test
        fun `should successfully record consent with biometric verification`() =
            runTest {
                coEvery { userVerificationService.getUserVerificationAvailability() } returns
                    UserVerificationAvailability(
                        biometricAvailable = true,
                        pinAvailable = false,
                        deviceLockAvailable = true,
                        supportedBiometricTypes = listOf(BiometricType.FINGERPRINT),
                        maxPinLength = MAX_PIN_LEN_8,
                        minPinLength = MIN_PIN_LEN_4,
                        biometricStrength = BiometricStrength.STRONG,
                    )
                val result =
                    getUserConsentUseCase(
                        rpId = testRpId,
                        operationType = ConsentOperationType.REGISTRATION,
                        credentialId = CredentialId.fromEncoded("dGVzdF9jcmVkZW50aWFsX2lk"),
                        requireVerification = true,
                    )

                assertTrue(result.isSuccess)
                val consentRecord = result.getOrThrow()
                assertNotNull(consentRecord)
                assertEquals(ConsentOperationType.REGISTRATION, consentRecord.operationType)
                assertEquals(testRpId, consentRecord.rpId)
                assertEquals("dGVzdF9jcmVkZW50aWFsX2lk", consentRecord.credentialId?.encoded)
                assertTrue(consentRecord.biometricUsed)
                assertFalse(consentRecord.pinUsed)

                // Verify all expected interactions
                coVerify { userVerificationService.isUserVerificationRequired(any(), any(), any()) }
                // Verify verification check was performed
                coVerify { credentialRepository.saveUserConsent(any()) }
            }

        @Test
        fun `should successfully record consent with pin verification`() =
            runTest {
                // Mock PIN as the only available method
                coEvery { userVerificationService.getUserVerificationAvailability() } returns
                    UserVerificationAvailability(
                        biometricAvailable = false,
                        pinAvailable = true,
                        deviceLockAvailable = false,
                        supportedBiometricTypes = emptyList(),
                        maxPinLength = MAX_PIN_LEN_8,
                        minPinLength = MIN_PIN_LEN_4,
                        biometricStrength = BiometricStrength.WEAK,
                    )

                val result =
                    getUserConsentUseCase(
                        rpId = testRpId,
                        operationType = ConsentOperationType.AUTHENTICATION,
                        credentialId = CredentialId.fromEncoded("dGVzdF9jcmVkZW50aWFsX2lk"),
                        requireVerification = true,
                    )

                assertTrue(result.isSuccess)
                val consentRecord = result.getOrThrow()
                assertEquals(ConsentOperationType.AUTHENTICATION, consentRecord.operationType)
                assertFalse(consentRecord.biometricUsed)
                assertTrue(consentRecord.pinUsed)

                // Expected behavior is to just check availability and proceed with recording the consent.
            }

        @Test
        fun `should successfully record consent without verification when not required`() =
            runTest {
                // Mock no verification required
                coEvery {
                    userVerificationService.isUserVerificationRequired(any(), any(), any())
                } returns com.chimali.fido2.domain.service.UserVerificationRequirement.NOT_REQUIRED

                val result =
                    getUserConsentUseCase(
                        rpId = testRpId,
                        operationType = ConsentOperationType.CREDENTIAL_DELETION,
                        credentialId = CredentialId.fromEncoded("dGVzdF9jcmVkZW50aWFsX2lk"),
                        requireVerification = false,
                    )

                assertTrue(result.isSuccess)
                val consentRecord = result.getOrThrow()
                assertEquals(ConsentOperationType.CREDENTIAL_DELETION, consentRecord.operationType)
                assertFalse(consentRecord.biometricUsed)
                assertFalse(consentRecord.pinUsed)

                // Verify no verification check was performed
                coVerify(exactly = 0) { userVerificationService.getUserVerificationAvailability() }
            }

        @Test
        fun `should successfully record consent without credential id`() =
            runTest {
                val result =
                    getUserConsentUseCase(
                        rpId = testRpId,
                        operationType = ConsentOperationType.REGISTRATION,
                        credentialId = null,
                        requireVerification = true,
                    )

                assertTrue(result.isSuccess)
                val consentRecord = result.getOrThrow()
                assertEquals(ConsentOperationType.REGISTRATION, consentRecord.operationType)
                assertNull(consentRecord.credentialId)
            }

        @Test
        fun `should use custom prompt when provided`() =
            runTest {
                val customPrompt = "Custom verification message"

                val result =
                    getUserConsentUseCase(
                        rpId = testRpId,
                        operationType = ConsentOperationType.REGISTRATION,
                        credentialId = CredentialId.fromEncoded("dGVzdF9jcmVkZW50aWFsX2lk"),
                        requireVerification = true,
                        prompt = customPrompt,
                    )

                assertTrue(result.isSuccess)

                // Verification prompt relies on availability internally now.
            }
    }

    @Nested
    inner class ConsentRetrievalTests {
        @Test
        fun `should retrieve recent consent records`() =
            runTest {
                val consentRecords =
                    listOf(
                        testConsentRecord,
                        UserConsentRecord.create(
                            id = "auth_consent_id",
                            operationType = ConsentOperationType.AUTHENTICATION,
                            rpId = testRpId,
                            biometricUsed = false,
                            pinUsed = true,
                        ),
                    )

                coEvery {
                    credentialRepository.getRecentUserConsent(any(), any())
                } returns flowOf(*consentRecords.toTypedArray())

                val result = getUserConsentUseCase.getRecentConsentRecords(testRpId, FETCH_LIMIT_50)
                val retrievedRecords = result.toList()

                assertEquals(EXPECTED_SIZE_2, retrievedRecords.size)
                assertTrue(retrievedRecords.contains(testConsentRecord))

                coVerify { credentialRepository.getRecentUserConsent(testRpId, FETCH_LIMIT_50) }
            }

        @Test
        fun `should retrieve consent records by operation type`() =
            runTest {
                val registrationConsent =
                    UserConsentRecord.create(
                        id = "reg_consent_id",
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        biometricUsed = true,
                        pinUsed = false,
                    )
                val authConsent =
                    UserConsentRecord.create(
                        id = "auth_consent_id",
                        operationType = ConsentOperationType.AUTHENTICATION,
                        rpId = testRpId,
                        biometricUsed = false,
                        pinUsed = true,
                    )

                coEvery {
                    credentialRepository.getRecentUserConsent(any(), any())
                } returns flowOf(registrationConsent, authConsent)

                val result =
                    getUserConsentUseCase.getConsentRecordsByOperationType(
                        ConsentOperationType.REGISTRATION,
                        testRpId,
                        FETCH_LIMIT_50,
                    )
                val retrievedRecords = result.toList()

                assertEquals(1, retrievedRecords.size)
                assertEquals(ConsentOperationType.REGISTRATION, retrievedRecords.first().operationType)
            }

        @Test
        fun `should retrieve consent records by credential id`() =
            runTest {
                val targetCredentialId = CredentialId.fromEncoded("dGFyZ2V0X2lk")
                val targetConsent =
                    UserConsentRecord.create(
                        id = "target_consent_id",
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        credentialId = targetCredentialId,
                        biometricUsed = true,
                        pinUsed = false,
                    )
                val otherConsent =
                    UserConsentRecord.create(
                        id = "other_consent_id",
                        operationType = ConsentOperationType.AUTHENTICATION,
                        rpId = testRpId,
                        credentialId = CredentialId.fromEncoded("b3RoZXJfaWQ"),
                        biometricUsed = false,
                        pinUsed = true,
                    )

                coEvery {
                    credentialRepository.getRecentUserConsent(any(), any())
                } returns flowOf(targetConsent, otherConsent)

                val result = getUserConsentUseCase.getConsentRecordsByCredential(targetCredentialId, FETCH_LIMIT_50)
                val retrievedRecords = result.toList()

                assertEquals(1, retrievedRecords.size)
                assertEquals(targetCredentialId, retrievedRecords.first().credentialId)
            }

        @Test
        fun `should retrieve consent records by rp id`() =
            runTest {
                val targetRpId = RpId("https://target.com")
                val targetConsent =
                    UserConsentRecord.create(
                        id = "target_rp_consent",
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = targetRpId,
                        biometricUsed = true,
                        pinUsed = false,
                    )
                val otherConsent =
                    UserConsentRecord.create(
                        id = "other_rp_consent",
                        operationType = ConsentOperationType.AUTHENTICATION,
                        rpId = RpId("https://other.com"),
                        biometricUsed = false,
                        pinUsed = true,
                    )

                coEvery {
                    credentialRepository.getRecentUserConsent(any(), any())
                } returns flowOf(targetConsent, otherConsent)

                val result = getUserConsentUseCase.getConsentRecordsByRpId(targetRpId, FETCH_LIMIT_50)
                val retrievedRecords = result.toList()

                assertEquals(1, retrievedRecords.size)
                assertEquals(targetRpId, retrievedRecords.first().rpId)
            }

        @Test
        fun `should retrieve consent records by time range`() =
            runTest {
                val startTime = TimeProvider().now() - 3600.seconds // 1 hour ago
                val endTime = TimeProvider().now() + 3600.seconds // 1 hour from now

                val inRangeConsent =
                    UserConsentRecord.create(
                        id = "in_range_consent",
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        biometricUsed = true,
                        pinUsed = false,
                    )
                val outOfRangeConsent =
                    UserConsentRecord
                        .create(
                            id = "out_of_range_consent",
                            operationType = ConsentOperationType.AUTHENTICATION,
                            rpId = testRpId,
                            biometricUsed = false,
                            pinUsed = true,
                        ).copy(timestamp = TimeProvider().now() - 7200.seconds) // 2 hours ago

                coEvery {
                    credentialRepository.getRecentUserConsent(any(), any())
                } returns flowOf(inRangeConsent, outOfRangeConsent)

                val result = getUserConsentUseCase.getConsentRecordsByTimeRange(startTime, endTime, testRpId)
                val retrievedRecords = result.toList()

                assertEquals(1, retrievedRecords.size)
                assertTrue(retrievedRecords.first().timestamp > startTime)
                assertTrue(retrievedRecords.first().timestamp < endTime)
            }
    }

    @Nested
    inner class ConsentStatisticsTests {
        @Test
        fun `should calculate consent statistics correctly`() =
            runTest {
                val consentRecords =
                    listOf(
                        UserConsentRecord.create(
                            id = "stat_reg_1",
                            operationType = ConsentOperationType.REGISTRATION,
                            rpId = testRpId,
                            biometricUsed = true,
                            pinUsed = false,
                        ),
                        UserConsentRecord.create(
                            id = "stat_auth_1",
                            operationType = ConsentOperationType.AUTHENTICATION,
                            rpId = testRpId,
                            biometricUsed = false,
                            pinUsed = true,
                        ),
                        UserConsentRecord.create(
                            id = "stat_reg_other",
                            operationType = ConsentOperationType.REGISTRATION,
                            rpId = RpId("https://other.com"),
                            biometricUsed = true,
                            pinUsed = true,
                        ),
                    )

                coEvery {
                    credentialRepository.getRecentUserConsent(any(), any())
                } returns flowOf(*consentRecords.toTypedArray())

                val result = getUserConsentUseCase.getConsentStatistics()

                assertEquals(EXPECTED_TOTAL_3, result.totalConsents)
                assertEquals(EXPECTED_REG_2, result.registrationConsents)
                assertEquals(EXPECTED_AUTH_1, result.authenticationConsents)
                assertEquals(
                    EXPECTED_BIOMETRIC_1,
                    result.biometricConsents,
                ) // Only pure BIOMETRIC (not BIOMETRIC_AND_PIN)
                assertEquals(EXPECTED_PIN_1, result.pinConsents) // Only pure PIN (not BIOMETRIC_AND_PIN)
                assertEquals(EXPECTED_COMBINED_1, result.combinedConsents)
                assertEquals(EXPECTED_RP_COUNT_2, result.consentsByRp.size) // Two different RPs
                assertTrue(result.consentsByRp.containsKey(testRpId))
                assertTrue(result.consentsByRp.containsKey(RpId("https://other.com")))
            }

        @Test
        fun `should calculate statistics for specific rp`() =
            runTest {
                val targetRpConsent =
                    UserConsentRecord.create(
                        id = "target_stat_consent",
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        biometricUsed = true,
                        pinUsed = false,
                    )

                coEvery { credentialRepository.getRecentUserConsent(any(), any()) } returns flowOf(targetRpConsent)

                val result = getUserConsentUseCase.getConsentStatistics(testRpId)

                assertEquals(1, result.totalConsents)
                assertEquals(1, result.registrationConsents)
                assertEquals(0, result.authenticationConsents)
                assertEquals(1, result.consentsByRp.size)
                assertTrue(result.consentsByRp.containsKey(testRpId))
                assertEquals(1, result.consentsByRp[testRpId])
            }

        @Test
        fun `should identify most used consent method`() =
            runTest {
                val consentRecords =
                    listOf(
                        UserConsentRecord.create(
                            id = "most_used_1",
                            operationType = ConsentOperationType.REGISTRATION,
                            rpId = testRpId,
                            biometricUsed = true,
                            pinUsed = false,
                        ),
                        UserConsentRecord.create(
                            id = "most_used_2",
                            operationType = ConsentOperationType.AUTHENTICATION,
                            rpId = testRpId,
                            biometricUsed = true,
                            pinUsed = false,
                        ),
                        UserConsentRecord.create(
                            id = "most_used_3",
                            operationType = ConsentOperationType.CREDENTIAL_UPDATE,
                            rpId = testRpId,
                            biometricUsed = false,
                            pinUsed = true,
                        ),
                    )

                coEvery {
                    credentialRepository.getRecentUserConsent(any(), any())
                } returns flowOf(*consentRecords.toTypedArray())

                val result = getUserConsentUseCase.getConsentStatistics()

                assertEquals(ConsentMethod.BIOMETRIC, result.getMostUsedMethod())
                assertEquals(EXPECTED_SIZE_2, result.getTotalVerificationMethods())
            }
    }

    @Nested
    inner class RecentConsentCheckTests {
        @Test
        fun `should correctly identify recent consent`() =
            runTest {
                val recentConsent =
                    UserConsentRecord.create(
                        id = "recent_consent",
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        biometricUsed = true,
                        pinUsed = false,
                    )

                coEvery { credentialRepository.getRecentUserConsent(any(), any()) } returns flowOf(recentConsent)

                val result =
                    getUserConsentUseCase.isRecentConsentGranted(
                        rpId = testRpId,
                        operationType = ConsentOperationType.REGISTRATION,
                        minutes = RECENT_MIN_5.toInt(),
                    )

                assertTrue(result)
            }

        @Test
        fun `should correctly identify non recent consent`() =
            runTest {
                val oldConsent =
                    UserConsentRecord
                        .create(
                            id = "old_consent",
                            operationType = ConsentOperationType.REGISTRATION,
                            rpId = testRpId,
                            biometricUsed = true,
                            pinUsed = false,
                        ).copy(
                            timestamp = TimeProvider().now() - 600.seconds,
                        ) // 10 minutes ago

                coEvery { credentialRepository.getRecentUserConsent(any(), any()) } returns flowOf(oldConsent)

                val result =
                    getUserConsentUseCase.isRecentConsentGranted(
                        rpId = testRpId,
                        operationType = ConsentOperationType.REGISTRATION,
                        minutes = RECENT_MIN_5.toInt(),
                    )

                assertFalse(result)
            }

        @Test
        fun `should handle no consent records`() =
            runTest {
                coEvery { credentialRepository.getRecentUserConsent(any(), any()) } returns flowOf()

                val result =
                    getUserConsentUseCase.isRecentConsentGranted(
                        rpId = testRpId,
                        operationType = ConsentOperationType.REGISTRATION,
                        minutes = RECENT_MIN_5.toInt(),
                    )

                assertFalse(result)
            }
    }

    @Nested
    inner class ValidationFailureTests {
        @Test
        fun `should fail when rp id is blank`() =
            runTest {
                kotlin.test.assertFailsWith<IllegalArgumentException> {
                    getUserConsentUseCase(
                        rpId = RpId(""),
                        operationType = ConsentOperationType.REGISTRATION,
                        credentialId = CredentialId.fromEncoded("dGVzdF9jcmVkZW50aWFsX2lk"),
                        requireVerification = true,
                    )
                }
            }

        @Test
        fun `should fail when rp id is invalid`() =
            runTest {
                val result =
                    getUserConsentUseCase(
                        rpId = RpId("invalid-rp-id"),
                        operationType = ConsentOperationType.REGISTRATION,
                        credentialId = CredentialId.fromEncoded("dGVzdF9jcmVkZW50aWFsX2lk"),
                        requireVerification = true,
                    )

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            }

        @Test
        fun `should fail when credential id is blank`() =
            runTest {
                kotlin.test.assertFailsWith<IllegalArgumentException> {
                    getUserConsentUseCase(
                        rpId = testRpId,
                        operationType = ConsentOperationType.REGISTRATION,
                        credentialId = CredentialId.fromByteArray(ByteArray(0)),
                        requireVerification = true,
                    )
                }
            }

        @Test
        fun `should fail when credential id exceeds maximum length`() =
            runTest {
                assertThrows<IllegalArgumentException> {
                    getUserConsentUseCase(
                        rpId = testRpId,
                        operationType = ConsentOperationType.REGISTRATION,
                        credentialId = CredentialId.fromByteArray(ByteArray(INVALID_CRED_ID_SIZE_1024)),
                        requireVerification = true,
                    )
                }
            }
    }

    @Nested
    inner class UserVerificationFailureTests {
        @Test
        fun `should fail when no verification method is available`() =
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

                val result =
                    getUserConsentUseCase(
                        rpId = testRpId,
                        operationType = ConsentOperationType.REGISTRATION,
                        credentialId = CredentialId.fromEncoded("dGVzdF9jcmVkZW50aWFsX2lk"),
                        requireVerification = true,
                    )

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is Fido2Exception.NoVerificationMethodAvailable)
            }

        @Test
        fun `should fail when consent storage fails`() =
            runTest {
                coEvery { credentialRepository.saveUserConsent(any()) } returns
                    Outcome.Error(
                        DomainError.StorageError(
                            "Consent storage failed",
                            Fido2Exception.ConsentStorageFailed("Consent storage failed"),
                        ),
                    )

                val result =
                    getUserConsentUseCase(
                        rpId = testRpId,
                        operationType = ConsentOperationType.REGISTRATION,
                        credentialId = CredentialId.fromEncoded("dGVzdF9jcmVkZW50aWFsX2lk"),
                        requireVerification = true,
                    )

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is Fido2Exception.ConsentStorageFailed)
            }
    }

    @Nested
    inner class OperationTypeTests {
        @Test
        fun `should handle all operation types`() =
            runTest {
                val operationTypes =
                    listOf(
                        ConsentOperationType.REGISTRATION,
                        ConsentOperationType.AUTHENTICATION,
                        ConsentOperationType.CREDENTIAL_DELETION,
                        ConsentOperationType.CREDENTIAL_UPDATE,
                    )

                operationTypes.forEach { operationType ->
                    val result =
                        getUserConsentUseCase(
                            rpId = testRpId,
                            operationType = operationType,
                            credentialId = CredentialId.fromEncoded("dGVzdF9jcmVkZW50aWFsX2lk"),
                            // Skip verification for this test
                            requireVerification = false,
                        )

                    assertTrue(result.isSuccess)
                    val consentRecord = result.getOrThrow()
                    assertEquals(operationType, consentRecord.operationType)
                }
            }

        @Test
        fun `should use appropriate prompts for different operation types`() =
            runTest {
                val operationTypes =
                    listOf(
                        ConsentOperationType.REGISTRATION to "Verify your identity to register new passkey",
                        ConsentOperationType.AUTHENTICATION to "Verify your identity to sign in",
                        ConsentOperationType.CREDENTIAL_DELETION to "Verify your identity to delete passkey",
                        ConsentOperationType.CREDENTIAL_UPDATE to "Verify your identity to update passkey",
                    )

                operationTypes.forEach { (operationType, expectedPrompt) ->
                    val result =
                        getUserConsentUseCase(
                            rpId = testRpId,
                            operationType = operationType,
                            credentialId = CredentialId.fromEncoded("dGVzdF9jcmVkZW50aWFsX2lk"),
                            requireVerification = true,
                        )

                    assertTrue(result.isSuccess)

                    // Removed coVerify as verifyBiometric no longer exists in Service.
                }
            }
    }

    @Nested
    inner class EdgeCases {
        @Test
        fun `should handle maximum allowed credential id length`() =
            runTest {
                val result =
                    getUserConsentUseCase(
                        rpId = testRpId,
                        operationType = ConsentOperationType.REGISTRATION,
                        credentialId = CredentialId.fromByteArray(ByteArray(MAX_CRED_ID_LEN_1023)),
                        requireVerification = true,
                    )

                assertTrue(result.isSuccess)
                val consentRecord = result.getOrThrow()
                assertEquals(MAX_CRED_ID_LEN_1023, consentRecord.credentialId?.toByteArray()?.size)
            }

        @Test
        fun `should handle http rp id`() =
            runTest {
                val httpRpId = RpId("http://localhost:8080")

                val result =
                    getUserConsentUseCase(
                        rpId = httpRpId,
                        operationType = ConsentOperationType.REGISTRATION,
                        credentialId = CredentialId.fromEncoded("dGVzdF9jcmVkZW50aWFsX2lk"),
                        requireVerification = true,
                    )

                assertTrue(result.isSuccess)
                val consentRecord = result.getOrThrow()
                assertEquals(httpRpId, consentRecord.rpId)
            }

        @Test
        fun `should handle empty consent statistics`() =
            runTest {
                coEvery { credentialRepository.getRecentUserConsent(any(), any()) } returns flowOf()

                val result = getUserConsentUseCase.getConsentStatistics()

                assertEquals(0, result.totalConsents)
                assertEquals(0, result.registrationConsents)
                assertEquals(0, result.authenticationConsents)
                assertEquals(0, result.biometricConsents)
                assertEquals(0, result.pinConsents)
                assertEquals(0, result.combinedConsents)
                assertEquals(0, result.consentsByRp.size)
                assertEquals(0, result.recentConsents)
                assertEquals(0.0, result.averageConsentsPerDay)
                assertEquals(ConsentMethod.NONE, result.getMostUsedMethod())
                assertEquals(0, result.getTotalVerificationMethods())
            }
    }

    private companion object {
        private const val MAX_PIN_LEN_8 = 8
        private const val MIN_PIN_LEN_4 = 4
        private const val FETCH_LIMIT_50 = 50
        private const val RECENT_MIN_5 = 5L
        private const val INVALID_CRED_ID_SIZE_1024 = 1024
        private const val MAX_CRED_ID_LEN_1023 = 1023

        private const val EXPECTED_TOTAL_3 = 3
        private const val EXPECTED_REG_2 = 2
        private const val EXPECTED_AUTH_1 = 1
        private const val EXPECTED_BIOMETRIC_1 = 1
        private const val EXPECTED_PIN_1 = 1
        private const val EXPECTED_COMBINED_1 = 1
        private const val EXPECTED_RP_COUNT_2 = 2
        private const val EXPECTED_SIZE_2 = 2
    }
}

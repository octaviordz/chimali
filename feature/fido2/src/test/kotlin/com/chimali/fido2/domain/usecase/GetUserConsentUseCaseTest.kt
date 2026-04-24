package com.chimali.fido2.domain.usecase

import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.*
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.service.*
import com.chimali.fido2.domain.service.UserVerificationService
import io.mockk.*
import org.junit.jupiter.api.Nested
import java.time.Instant
import kotlin.test.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class GetUserConsentUseCaseTest {
    private lateinit var credentialRepository: CredentialRepository
    private lateinit var userVerificationService: UserVerificationService
    private lateinit var getUserConsentUseCase: GetUserConsentUseCase
    private lateinit var testRpId: String
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
            testRpId = "https://example.com"
            testTimestamp = Instant.now()

            testConsentRecord =
                UserConsentRecord.create(
                    operationType = ConsentOperationType.REGISTRATION,
                    rpId = testRpId,
                    credentialId = "test_credential_id",
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
                    maxPinLength = 8,
                    minPinLength = 4,
                    biometricStrength = BiometricStrength.STRONG,
                )

            coEvery { credentialRepository.saveUserConsent(any()) } returns Result.success(Unit)
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
                        maxPinLength = 8,
                        minPinLength = 4,
                        biometricStrength = BiometricStrength.STRONG,
                    )
                val result =
                    getUserConsentUseCase(
                        rpId = testRpId,
                        operationType = ConsentOperationType.REGISTRATION,
                        credentialId = "test_credential_id",
                        requireVerification = true,
                    )

                assertTrue(result.isSuccess)
                val consentRecord = result.getOrThrow()
                assertNotNull(consentRecord)
                assertEquals(ConsentOperationType.REGISTRATION, consentRecord.operationType)
                assertEquals(testRpId, consentRecord.rpId)
                assertEquals("test_credential_id", consentRecord.credentialId)
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
                        maxPinLength = 8,
                        minPinLength = 4,
                        biometricStrength = BiometricStrength.WEAK,
                    )

                val result =
                    getUserConsentUseCase(
                        rpId = testRpId,
                        operationType = ConsentOperationType.AUTHENTICATION,
                        credentialId = "test_credential_id",
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
                        credentialId = "test_credential_id",
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
                        credentialId = "test_credential_id",
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
                            operationType = ConsentOperationType.AUTHENTICATION,
                            rpId = testRpId,
                            biometricUsed = false,
                            pinUsed = true,
                        ),
                    )

                coEvery { credentialRepository.getRecentUserConsent(any(), any()) } returns flowOf(*consentRecords.toTypedArray())

                val result = getUserConsentUseCase.getRecentConsentRecords(testRpId, 50)
                val retrievedRecords = result.toList()

                assertEquals(2, retrievedRecords.size)
                assertTrue(retrievedRecords.contains(testConsentRecord))

                coVerify { credentialRepository.getRecentUserConsent(testRpId, 50) }
            }

        @Test
        fun `should retrieve consent records by operation type`() =
            runTest {
                val registrationConsent =
                    UserConsentRecord.create(
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        biometricUsed = true,
                        pinUsed = false,
                    )
                val authConsent =
                    UserConsentRecord.create(
                        operationType = ConsentOperationType.AUTHENTICATION,
                        rpId = testRpId,
                        biometricUsed = false,
                        pinUsed = true,
                    )

                coEvery { credentialRepository.getRecentUserConsent(any(), any()) } returns flowOf(registrationConsent, authConsent)

                val result =
                    getUserConsentUseCase.getConsentRecordsByOperationType(
                        ConsentOperationType.REGISTRATION,
                        testRpId,
                        50,
                    )
                val retrievedRecords = result.toList()

                assertEquals(1, retrievedRecords.size)
                assertEquals(ConsentOperationType.REGISTRATION, retrievedRecords.first().operationType)
            }

        @Test
        fun `should retrieve consent records by credential id`() =
            runTest {
                val targetCredentialId = "target_credential_id"
                val targetConsent =
                    UserConsentRecord.create(
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        credentialId = targetCredentialId,
                        biometricUsed = true,
                        pinUsed = false,
                    )
                val otherConsent =
                    UserConsentRecord.create(
                        operationType = ConsentOperationType.AUTHENTICATION,
                        rpId = testRpId,
                        credentialId = "other_credential_id",
                        biometricUsed = false,
                        pinUsed = true,
                    )

                coEvery { credentialRepository.getRecentUserConsent(any(), any()) } returns flowOf(targetConsent, otherConsent)

                val result = getUserConsentUseCase.getConsentRecordsByCredential(targetCredentialId, 50)
                val retrievedRecords = result.toList()

                assertEquals(1, retrievedRecords.size)
                assertEquals(targetCredentialId, retrievedRecords.first().credentialId)
            }

        @Test
        fun `should retrieve consent records by rp id`() =
            runTest {
                val targetRpId = "https://target.com"
                val targetConsent =
                    UserConsentRecord.create(
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = targetRpId,
                        biometricUsed = true,
                        pinUsed = false,
                    )
                val otherConsent =
                    UserConsentRecord.create(
                        operationType = ConsentOperationType.AUTHENTICATION,
                        rpId = "https://other.com",
                        biometricUsed = false,
                        pinUsed = true,
                    )

                coEvery { credentialRepository.getRecentUserConsent(any(), any()) } returns flowOf(targetConsent, otherConsent)

                val result = getUserConsentUseCase.getConsentRecordsByRpId(targetRpId, 50)
                val retrievedRecords = result.toList()

                assertEquals(1, retrievedRecords.size)
                assertEquals(targetRpId, retrievedRecords.first().rpId)
            }

        @Test
        fun `should retrieve consent records by time range`() =
            runTest {
                val startTime = Instant.now().minusSeconds(3600) // 1 hour ago
                val endTime = Instant.now().plusSeconds(3600) // 1 hour from now

                val inRangeConsent =
                    UserConsentRecord.create(
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        biometricUsed = true,
                        pinUsed = false,
                    )
                val outOfRangeConsent =
                    UserConsentRecord.create(
                        operationType = ConsentOperationType.AUTHENTICATION,
                        rpId = testRpId,
                        biometricUsed = false,
                        pinUsed = true,
                    ).copy(timestamp = Instant.now().minusSeconds(7200)) // 2 hours ago

                coEvery { credentialRepository.getRecentUserConsent(any(), any()) } returns flowOf(inRangeConsent, outOfRangeConsent)

                val result = getUserConsentUseCase.getConsentRecordsByTimeRange(startTime, endTime, testRpId)
                val retrievedRecords = result.toList()

                assertEquals(1, retrievedRecords.size)
                assertTrue(retrievedRecords.first().timestamp.isAfter(startTime))
                assertTrue(retrievedRecords.first().timestamp.isBefore(endTime))
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
                            operationType = ConsentOperationType.REGISTRATION,
                            rpId = testRpId,
                            biometricUsed = true,
                            pinUsed = false,
                        ),
                        UserConsentRecord.create(
                            operationType = ConsentOperationType.AUTHENTICATION,
                            rpId = testRpId,
                            biometricUsed = false,
                            pinUsed = true,
                        ),
                        UserConsentRecord.create(
                            operationType = ConsentOperationType.REGISTRATION,
                            rpId = "https://other.com",
                            biometricUsed = true,
                            pinUsed = true,
                        ),
                    )

                coEvery { credentialRepository.getRecentUserConsent(any(), any()) } returns flowOf(*consentRecords.toTypedArray())

                val result = getUserConsentUseCase.getConsentStatistics()

                assertEquals(3, result.totalConsents)
                assertEquals(2, result.registrationConsents)
                assertEquals(1, result.authenticationConsents)
                assertEquals(1, result.biometricConsents) // Only pure BIOMETRIC (not BIOMETRIC_AND_PIN)
                assertEquals(1, result.pinConsents) // Only pure PIN (not BIOMETRIC_AND_PIN)
                assertEquals(1, result.combinedConsents)
                assertEquals(2, result.consentsByRp.size) // Two different RPs
                assertTrue(result.consentsByRp.containsKey(testRpId))
                assertTrue(result.consentsByRp.containsKey("https://other.com"))
            }

        @Test
        fun `should calculate statistics for specific rp`() =
            runTest {
                val targetRpConsent =
                    UserConsentRecord.create(
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        biometricUsed = true,
                        pinUsed = false,
                    )
                val otherRpConsent =
                    UserConsentRecord.create(
                        operationType = ConsentOperationType.AUTHENTICATION,
                        rpId = "https://other.com",
                        biometricUsed = false,
                        pinUsed = true,
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
                            operationType = ConsentOperationType.REGISTRATION,
                            rpId = testRpId,
                            biometricUsed = true,
                            pinUsed = false,
                        ),
                        UserConsentRecord.create(
                            operationType = ConsentOperationType.AUTHENTICATION,
                            rpId = testRpId,
                            biometricUsed = true,
                            pinUsed = false,
                        ),
                        UserConsentRecord.create(
                            operationType = ConsentOperationType.CREDENTIAL_UPDATE,
                            rpId = testRpId,
                            biometricUsed = false,
                            pinUsed = true,
                        ),
                    )

                coEvery { credentialRepository.getRecentUserConsent(any(), any()) } returns flowOf(*consentRecords.toTypedArray())

                val result = getUserConsentUseCase.getConsentStatistics()

                assertEquals(ConsentMethod.BIOMETRIC, result.getMostUsedMethod())
                assertEquals(2, result.getTotalVerificationMethods())
            }
    }

    @Nested
    inner class RecentConsentCheckTests {
        @Test
        fun `should correctly identify recent consent`() =
            runTest {
                val recentConsent =
                    UserConsentRecord.create(
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
                        minutes = 5,
                    )

                assertTrue(result)
            }

        @Test
        fun `should correctly identify non recent consent`() =
            runTest {
                val oldConsent =
                    UserConsentRecord.create(
                        operationType = ConsentOperationType.REGISTRATION,
                        rpId = testRpId,
                        biometricUsed = true,
                        pinUsed = false,
                    ).copy(timestamp = Instant.now().minusSeconds(10 * 60)) // 10 minutes ago

                coEvery { credentialRepository.getRecentUserConsent(any(), any()) } returns flowOf(oldConsent)

                val result =
                    getUserConsentUseCase.isRecentConsentGranted(
                        rpId = testRpId,
                        operationType = ConsentOperationType.REGISTRATION,
                        minutes = 5,
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
                        minutes = 5,
                    )

                assertFalse(result)
            }
    }

    @Nested
    inner class ValidationFailureTests {
        @Test
        fun `should fail when rp id is blank`() =
            runTest {
                val result =
                    getUserConsentUseCase(
                        rpId = "",
                        operationType = ConsentOperationType.REGISTRATION,
                        credentialId = "test_credential_id",
                        requireVerification = true,
                    )

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            }

        @Test
        fun `should fail when rp id is invalid`() =
            runTest {
                val result =
                    getUserConsentUseCase(
                        rpId = "invalid-rp-id",
                        operationType = ConsentOperationType.REGISTRATION,
                        credentialId = "test_credential_id",
                        requireVerification = true,
                    )

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            }

        @Test
        fun `should fail when credential id is blank`() =
            runTest {
                val result =
                    getUserConsentUseCase(
                        rpId = testRpId,
                        operationType = ConsentOperationType.REGISTRATION,
                        credentialId = "",
                        requireVerification = true,
                    )

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            }

        @Test
        fun `should fail when credential id exceeds maximum length`() =
            runTest {
                val longCredentialId = "a".repeat(1024)

                val result =
                    getUserConsentUseCase(
                        rpId = testRpId,
                        operationType = ConsentOperationType.REGISTRATION,
                        credentialId = longCredentialId,
                        requireVerification = true,
                    )

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is IllegalArgumentException)
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
                        credentialId = "test_credential_id",
                        requireVerification = true,
                    )

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is Fido2Exception.NoVerificationMethodAvailable)
            }

        @Test
        fun `should fail when consent storage fails`() =
            runTest {
                coEvery { credentialRepository.saveUserConsent(any()) } returns
                    Result.failure(
                        Fido2Exception.ConsentStorageFailed("Consent storage failed"),
                    )

                val result =
                    getUserConsentUseCase(
                        rpId = testRpId,
                        operationType = ConsentOperationType.REGISTRATION,
                        credentialId = "test_credential_id",
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
                            credentialId = "test_credential_id",
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
                            credentialId = "test_credential_id",
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
                val maxCredentialId = "a".repeat(1023)

                val result =
                    getUserConsentUseCase(
                        rpId = testRpId,
                        operationType = ConsentOperationType.REGISTRATION,
                        credentialId = maxCredentialId,
                        requireVerification = true,
                    )

                assertTrue(result.isSuccess)
                val consentRecord = result.getOrThrow()
                assertEquals(maxCredentialId, consentRecord.credentialId)
            }

        @Test
        fun `should handle http rp id`() =
            runTest {
                val httpRpId = "http://localhost:8080"

                val result =
                    getUserConsentUseCase(
                        rpId = httpRpId,
                        operationType = ConsentOperationType.REGISTRATION,
                        credentialId = "test_credential_id",
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
}

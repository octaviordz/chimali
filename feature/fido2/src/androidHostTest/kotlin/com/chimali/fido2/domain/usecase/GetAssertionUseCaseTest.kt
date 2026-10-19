package com.chimali.fido2.domain.usecase

import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.getOrThrow
import com.chimali.core.common.result.isFailure
import com.chimali.core.common.result.isSuccess
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.fido2.data.crypto.ClientDataHashService
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.PublicKeyCredentialDescriptor
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.service.BiometricStrength
import com.chimali.fido2.domain.service.BiometricType
import com.chimali.fido2.domain.service.UserVerificationAvailability
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.testing.CapturedDiagnostics
import com.chimali.fido2.testing.testCredentialIdFromLabel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * T082 — Unit tests for [GetAssertionUseCase].
 *
 * Since GetAssertionUseCase now delegates signing to [Fido2CryptoService] (rather
 * than directly to the Android KeyStore), all signing interactions are replaced by
 * a mock [Fido2CryptoService].
 */
@Req("NFR-PERF-030", "NFR-SEC-041")
class GetAssertionUseCaseTest {
    private lateinit var credentialRepository: CredentialRepository
    private lateinit var userVerificationService: UserVerificationService
    private lateinit var selectCredentialUseCase: SelectCredentialUseCase
    private lateinit var cryptoService: Fido2CryptoService
    private lateinit var clientDataHashService: ClientDataHashService
    private lateinit var useCase: GetAssertionUseCase

    private val testRpId = RpId("https://example.com")
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
        private const val MAX_AUTHENTICATOR_COUNTER = 0xFFFF_FFFFL
        private const val AUTH_DATA_COUNTER_OFFSET = 33
        private const val AUTH_DATA_COUNTER_END = 37
        private const val AUTH_DATA_FLAGS_OFFSET = 32
        private const val FLAG_USER_VERIFIED = 0x04
        private const val MIN_AUTH_DATA_SIZE_37 = 37
    }

    @BeforeTest
    fun setup() {
        credentialRepository = mockk()
        userVerificationService = mockk()
        selectCredentialUseCase = mockk()
        cryptoService = mockk()
        clientDataHashService = ClientDataHashService()

        // Default: signing succeeds with a fake DER signature
        coEvery { cryptoService.sign(any(), any(), any()) } returns Outcome.Success(fakeSignature)

        // Default: UV preferred, biometric available
        coEvery { userVerificationService.getUserVerificationAvailability() } returns
            UserVerificationAvailability(
                isBiometricAvailable = true,
                isPinAvailable = true,
                isDeviceLockAvailable = false,
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
                clientDataHashService,
            )
    }

    private fun createOptions(
        uv: UserVerificationRequirement = UserVerificationRequirement.PREFERRED,
        allowCredentials: List<PublicKeyCredentialDescriptor>? = null,
    ): GetAssertionOptions =
        GetAssertionOptions.create(
            rpId = testRpId,
            clientDataHash = testClientDataHash,
            allowCredentials = allowCredentials,
            userVerification = uv,
        )

    private fun createSummary(
        id: String,
        algorithm: Int = PasskeyCredential.COSE_ES256,
        credentialId: CredentialId = testCredentialIdFromLabel(id),
    ): CredentialSummary =
        CredentialSummary(
            id = id,
            rpId = testRpId,
            credentialId = credentialId,
            lastUsedAt = TimeProvider().now(),
            coseAlgorithm = algorithm,
            derivationProfile = CredentialKeyDescriptor.profileFor(algorithm),
            storedPublicKeyEncoding = byteArrayOf(),
        )

    // ── No credentials found ──────────────────────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `missing credential errors and diagnostics omit the RP association`() =
        runTest {
            val marker = "synthetic-private-rp"
            val options = createOptions().copy(rpId = RpId("$marker.example.com"))
            coEvery {
                credentialRepository.getCredentialSummariesForRpText(match { it.contentEquals(options.rpId.value) })
            } returns
                Outcome.Success(emptyList())
            CapturedDiagnostics().use { logs ->
                val error = assertIs<Outcome.Error<*>>(useCase(options)).error
                assertIs<DomainError.NotFound>(error)
                assertTrue(marker !in error.message)
                assertTrue(error.cause?.stackTraceToString()?.contains(marker) != true)
                assertTrue(logs.size > 0)
                logs.assertNoSensitiveValues(marker)
            }
            coVerify(exactly = 0) { cryptoService.sign(any(), any(), any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `returns failure when no credentials found for RP`() =
        runTest {
            coEvery {
                credentialRepository.getCredentialSummariesForRpText(match { it.contentEquals(testRpId.value) })
            } returns Outcome.Success(emptyList())

            val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { cryptoService.sign(any(), any(), any()) }
            val error = assertIs<DomainError.NotFound>(assertIs<Outcome.Error<*>>(result).error)
            assertEquals("No credentials found", error.message)
            assertNull(error.cause)
        }

    // ── User verification required but fails ─────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `returns failure when user verification required but fails`() =
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

            val result = useCase(createOptions(uv = UserVerificationRequirement.REQUIRED))

            assertTrue(result.isFailure)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `user verification flag reflects completed verification rather than requested preference`() =
        runTest {
            val summary = createSummary("uv-flag")
            coEvery {
                credentialRepository.getCredentialSummariesForRpText(match { it.contentEquals(testRpId.value) })
            } returns
                Outcome.Success(listOf(summary))
            coEvery { selectCredentialUseCase(any(), any()) } returns Outcome.Success(summary)
            coEvery { credentialRepository.getSignCount(summary.credentialId) } returns Outcome.Success(SIGN_COUNT_5)
            coEvery { credentialRepository.updateSignCount(summary.credentialId, SIGN_COUNT_6) } returns
                Outcome.Success(Unit)

            val headlessResult = useCase(createOptions(uv = UserVerificationRequirement.PREFERRED))
            val verifiedResult = useCase(createOptions(uv = UserVerificationRequirement.PREFERRED), true)

            assertTrue(headlessResult.isSuccess)
            assertTrue(verifiedResult.isSuccess)
            assertEquals(0, headlessResult.getOrThrow().authData[AUTH_DATA_FLAGS_OFFSET].toInt() and FLAG_USER_VERIFIED)
            assertEquals(
                FLAG_USER_VERIFIED,
                verifiedResult.getOrThrow().authData[AUTH_DATA_FLAGS_OFFSET].toInt() and FLAG_USER_VERIFIED,
            )
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `headless assertion reuses the candidate snapshot used for fast path selection`() =
        runTest {
            val summary = createSummary("headless-prefetched")
            coEvery { selectCredentialUseCase(any(), any()) } returns Outcome.Success(summary)
            coEvery { credentialRepository.getSignCount(summary.credentialId) } returns Outcome.Success(SIGN_COUNT_5)
            coEvery { credentialRepository.updateSignCount(summary.credentialId, SIGN_COUNT_6) } returns
                Outcome.Success(Unit)

            val result = useCase.invokeHeadless(createOptions(), listOf(summary))

            assertTrue(result.isSuccess)
            coVerify(exactly = 0) { credentialRepository.getCredentialSummariesForRpText(any()) }
            coVerify(exactly = 1) { selectCredentialUseCase(listOf(summary), any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `credProtect UV-required candidate remains promptable and is filtered until verification completes`() =
        runTest {
            val protectedSummary = createSummary("uv-required-credential").copy(credProtectPolicy = 3)
            coEvery {
                credentialRepository.getCredentialSummariesForRpText(match { it.contentEquals(testRpId.value) })
            } returns
                Outcome.Success(listOf(protectedSummary))
            coEvery { selectCredentialUseCase(any(), any()) } returns Outcome.Success(protectedSummary)
            coEvery {
                credentialRepository.getSignCount(protectedSummary.credentialId)
            } returns Outcome.Success(SIGN_COUNT_5)
            coEvery {
                credentialRepository.updateSignCount(protectedSummary.credentialId, SIGN_COUNT_6)
            } returns Outcome.Success(Unit)

            val potentialCandidates = useCase.findCandidateSummaries(createOptions())
            val headlessResult = useCase.invokeHeadless(createOptions(), potentialCandidates)
            val verifiedResult = useCase(createOptions(), userVerificationPerformed = true)

            assertEquals(listOf(protectedSummary), potentialCandidates)
            assertTrue(headlessResult.isFailure)
            assertTrue(verifiedResult.isSuccess)
            coVerify(exactly = 1) { selectCredentialUseCase(listOf(protectedSummary), any()) }
            coVerify(exactly = 2) {
                credentialRepository.getCredentialSummariesForRpText(match { it.contentEquals(testRpId.value) })
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `unsupported candidate protection policy fails before selection or signing`() =
        runTest {
            val invalidSummary =
                mockk<CredentialSummary> {
                    every { eraseSensitiveText() } returns Unit
                    every { credProtectPolicy } returns 4
                }

            val result = useCase.invokeHeadless(createOptions(), listOf(invalidSummary))

            assertIs<DomainError.ValidationError>(assertIs<Outcome.Error<*>>(result).error)
            coVerify(exactly = 0) { selectCredentialUseCase(any(), any()) }
            coVerify(exactly = 0) { cryptoService.sign(any(), any(), any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `required verification rejects an assertion when verification was not performed`() =
        runTest {
            val result = useCase(createOptions(uv = UserVerificationRequirement.REQUIRED))

            assertIs<DomainError.OperationDenied>(assertIs<Outcome.Error<*>>(result).error)
            coVerify(exactly = 0) { credentialRepository.getCredentialSummariesForRpText(any()) }
            coVerify(exactly = 0) { cryptoService.sign(any(), any(), any()) }
        }

    // ── Credential selection delegation ───────────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `delegates to SelectCredentialUseCase when multiple credentials found`() =
        runTest {
            val s1 = createSummary("cred1")
            val s2 = createSummary("cred2")
            coEvery {
                credentialRepository.getCredentialSummariesForRpText(match { it.contentEquals(testRpId.value) })
            } returns Outcome.Success(listOf(s1, s2))
            coEvery { selectCredentialUseCase(any(), any()) } returns Outcome.Success(s1)
            coEvery { credentialRepository.getSignCount(s1.credentialId) } returns Outcome.Success(SIGN_COUNT_5)
            coEvery {
                credentialRepository.updateSignCount(s1.credentialId, SIGN_COUNT_6)
            } returns Outcome.Success(Unit)

            val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

            coVerify { selectCredentialUseCase(any(), any()) }

            if (result.isSuccess) {
                val assertion = result.getOrThrow()
                assertNotNull(assertion.authData)
                assertNotNull(assertion.signature)
                assertTrue(assertion.authData.size >= MIN_AUTH_DATA_SIZE_37)
            }
            coVerify(exactly = 0) { credentialRepository.getCredentialById(s1.credentialId) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `assertion signs exactly the selected descriptor once without hydrating candidate credentials`() =
        runTest {
            val first = createSummary("candidate-first-credential")
            val selected = createSummary("candidate-selected-credential")
            val last = createSummary("candidate-last-credential")
            coEvery {
                credentialRepository.getCredentialSummariesForRpText(match { it.contentEquals(testRpId.value) })
            } returns Outcome.Success(listOf(first, selected, last))
            coEvery { selectCredentialUseCase(any(), any()) } returns Outcome.Success(selected)
            coEvery { credentialRepository.getSignCount(selected.credentialId) } returns Outcome.Success(SIGN_COUNT_5)
            coEvery {
                credentialRepository.updateSignCount(selected.credentialId, SIGN_COUNT_6)
            } returns Outcome.Success(Unit)

            val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) {
                cryptoService.sign(
                    match { it.credentialId == selected.credentialId },
                    any(),
                    match { it.contentEquals(selected.storedPublicKeyEncoding) },
                )
            }
            coVerify(exactly = 0) {
                cryptoService.sign(match { it.credentialId == first.credentialId }, any(), any())
            }
            coVerify(exactly = 0) {
                cryptoService.sign(match { it.credentialId == last.credentialId }, any(), any())
            }
            coVerify(exactly = 0) { credentialRepository.getCredentialById(first.credentialId) }
            coVerify(exactly = 0) { credentialRepository.getCredentialById(selected.credentialId) }
            coVerify(exactly = 0) { credentialRepository.getCredentialById(last.credentialId) }
            coVerify(exactly = 0) { credentialRepository.getSignCount(first.credentialId) }
            coVerify(exactly = 0) { credentialRepository.getSignCount(last.credentialId) }
        }

    // ── SelectCredentialUseCase fails ─────────────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `returns failure when credential selection fails`() =
        runTest {
            val s1 = createSummary("cred1")
            coEvery {
                credentialRepository.getCredentialSummariesForRpText(match { it.contentEquals(testRpId.value) })
            } returns Outcome.Success(listOf(s1))
            coEvery { selectCredentialUseCase(any(), any()) } returns
                Outcome.Error(DomainError.NotFound("No eligible credential"))

            val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { credentialRepository.getSignCount(s1.credentialId) }
            coVerify(exactly = 0) { credentialRepository.updateSignCount(s1.credentialId, any()) }
            coVerify(exactly = 0) { cryptoService.sign(any(), any(), any()) }
        }

    // ── Sign count is incremented ─────────────────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `increments sign count on successful assertion`() =
        runTest {
            val s1 = createSummary("cred1")
            coEvery {
                credentialRepository.getCredentialSummariesForRpText(match { it.contentEquals(testRpId.value) })
            } returns Outcome.Success(listOf(s1))
            coEvery { selectCredentialUseCase(any(), any()) } returns Outcome.Success(s1)
            coEvery { credentialRepository.getSignCount(s1.credentialId) } returns Outcome.Success(SIGN_COUNT_10)
            coEvery {
                credentialRepository.updateSignCount(s1.credentialId, SIGN_COUNT_11)
            } returns Outcome.Success(Unit)

            val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

            if (result.isSuccess) {
                coVerify { credentialRepository.updateSignCount(s1.credentialId, SIGN_COUNT_11) }
            }
            coVerify(exactly = 0) { credentialRepository.getCredentialById(s1.credentialId) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `last representable authenticator counter is encoded as unsigned big endian`() =
        runTest {
            val summary = createSummary("last-authenticator-counter")
            var capturedSignedPayload: ByteArray? = null
            coEvery {
                credentialRepository.getCredentialSummariesForRpText(match { it.contentEquals(testRpId.value) })
            } returns
                Outcome.Success(listOf(summary))
            coEvery { selectCredentialUseCase(any(), any()) } returns Outcome.Success(summary)
            coEvery {
                credentialRepository.getSignCount(summary.credentialId)
            } returns Outcome.Success(MAX_AUTHENTICATOR_COUNTER - 1)
            coEvery {
                credentialRepository.updateSignCount(summary.credentialId, MAX_AUTHENTICATOR_COUNTER)
            } returns Outcome.Success(Unit)
            coEvery { cryptoService.sign(any(), any(), any()) } coAnswers {
                capturedSignedPayload = secondArg()
                Outcome.Success(fakeSignature)
            }

            val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

            assertTrue(result.isSuccess)
            assertContentEquals(
                byteArrayOf((-1).toByte(), (-1).toByte(), (-1).toByte(), (-1).toByte()),
                capturedSignedPayload!!.copyOfRange(AUTH_DATA_COUNTER_OFFSET, AUTH_DATA_COUNTER_END),
            )
            coVerify(exactly = 1) {
                credentialRepository.updateSignCount(summary.credentialId, MAX_AUTHENTICATOR_COUNTER)
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `exhausted or corrupt authenticator counter fails before signing and mutation`() =
        runTest {
            val invalidCounters = listOf(-1L, MAX_AUTHENTICATOR_COUNTER, MAX_AUTHENTICATOR_COUNTER + 1)

            invalidCounters.forEachIndexed { index, counter ->
                val summary = createSummary("invalid-authenticator-counter-$index")
                coEvery {
                    credentialRepository.getCredentialSummariesForRpText(match { it.contentEquals(testRpId.value) })
                } returns
                    Outcome.Success(listOf(summary))
                coEvery { selectCredentialUseCase(any(), any()) } returns Outcome.Success(summary)
                coEvery { credentialRepository.getSignCount(summary.credentialId) } returns Outcome.Success(counter)

                val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

                val error = assertIs<DomainError.DatabaseError>(assertIs<Outcome.Error<*>>(result).error)
                assertEquals("Credential signing counter is outside the supported range", error.message)
                coVerify(exactly = 0) { cryptoService.sign(any(), any(), any()) }
                coVerify(exactly = 0) {
                    credentialRepository.updateSignCount(summary.credentialId, any())
                }
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `counter persistence failure prevents returning the signed assertion`() =
        runTest {
            val summary = createSummary("counter-persist-failure")
            val exceptionMarker = "synthetic-private-material-marker"
            coEvery {
                credentialRepository.getCredentialSummariesForRpText(match { it.contentEquals(testRpId.value) })
            } returns
                Outcome.Success(listOf(summary))
            coEvery { selectCredentialUseCase(any(), any()) } returns Outcome.Success(summary)
            coEvery { credentialRepository.getSignCount(summary.credentialId) } returns Outcome.Success(SIGN_COUNT_5)
            coEvery { credentialRepository.updateSignCount(summary.credentialId, SIGN_COUNT_6) } returns
                Outcome.Error(DomainError.DatabaseError(exceptionMarker, IllegalStateException(exceptionMarker)))

            val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

            val error = assertIs<DomainError.DatabaseError>(assertIs<Outcome.Error<*>>(result).error)
            assertEquals("Unable to persist credential signing counter", error.message)
            assertNull(error.cause)
            assertTrue(fakeSignature.all { it == 0.toByte() })
            coVerify(exactly = 1) { cryptoService.sign(any(), any(), any()) }
            coVerify(exactly = 1) { credentialRepository.updateSignCount(summary.credentialId, SIGN_COUNT_6) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `counter persistence cancellation propagates and discards the signed assertion`() =
        runTest {
            val summary = createSummary("counter-persist-cancel")
            coEvery {
                credentialRepository.getCredentialSummariesForRpText(match { it.contentEquals(testRpId.value) })
            } returns
                Outcome.Success(listOf(summary))
            coEvery { selectCredentialUseCase(any(), any()) } returns Outcome.Success(summary)
            coEvery { credentialRepository.getSignCount(summary.credentialId) } returns Outcome.Success(SIGN_COUNT_5)
            coEvery { credentialRepository.updateSignCount(summary.credentialId, SIGN_COUNT_6) } throws
                CancellationException("synthetic counter write cancellation")

            assertFailsWith<CancellationException> {
                useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))
            }

            assertTrue(fakeSignature.all { it == 0.toByte() })
            coVerify(exactly = 1) { cryptoService.sign(any(), any(), any()) }
        }

    // ── Signing failure is surfaced ───────────────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `assertion routes selected P-256 Ed25519 and ML-DSA credentials through their current profiles`() =
        runTest {
            val cases =
                listOf(
                    CredentialKeyDescriptor.COSE_ES256 to CredentialKeyDescriptor.P256_PROFILE,
                    CredentialKeyDescriptor.COSE_EDDSA to CredentialKeyDescriptor.ED25519_PROFILE,
                    CredentialKeyDescriptor.COSE_ML_DSA_65 to CredentialKeyDescriptor.MLDSA65_PROFILE,
                )

            cases.forEachIndexed { index, (algorithm, profile) ->
                val id = "profile-assertion-$index"
                val summary = createSummary(id, algorithm)
                coEvery {
                    credentialRepository.getCredentialSummariesForRpText(match { it.contentEquals(testRpId.value) })
                } returns
                    Outcome.Success(listOf(summary))
                coEvery { selectCredentialUseCase(any(), any()) } returns Outcome.Success(summary)
                coEvery { credentialRepository.getSignCount(summary.credentialId) } returns
                    Outcome.Success(SIGN_COUNT_5)
                coEvery { credentialRepository.updateSignCount(summary.credentialId, SIGN_COUNT_6) } returns
                    Outcome.Success(Unit)

                val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

                assertTrue(result.isSuccess, "Assertion should succeed for algorithm $algorithm")
                coVerify(exactly = 1) {
                    cryptoService.sign(
                        match { it.coseAlgorithm == algorithm && it.derivationProfile == profile },
                        any(),
                        match { it.contentEquals(summary.storedPublicKeyEncoding) },
                    )
                }
                coVerify(exactly = 0) { credentialRepository.getCredentialById(summary.credentialId) }
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `malformed summary profile fails before signing or counter mutation`() =
        runTest {
            val invalidSummary =
                createSummary("invalid-profile").copy(
                    derivationProfile = CredentialKeyDescriptor.ED25519_PROFILE,
                )
            coEvery {
                credentialRepository.getCredentialSummariesForRpText(match { it.contentEquals(testRpId.value) })
            } returns
                Outcome.Success(listOf(invalidSummary))
            coEvery { selectCredentialUseCase(any(), any()) } returns Outcome.Success(invalidSummary)

            val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { cryptoService.sign(any(), any(), any()) }
            coVerify(exactly = 0) { credentialRepository.getSignCount(invalidSummary.credentialId) }
            coVerify(exactly = 0) { credentialRepository.updateSignCount(invalidSummary.credentialId, any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `sign count error details are not returned to assertion callers`() =
        runTest {
            val summary = createSummary("sign-count-error")
            val exceptionMarker = "synthetic-private-material-marker"
            coEvery {
                credentialRepository.getCredentialSummariesForRpText(match { it.contentEquals(testRpId.value) })
            } returns
                Outcome.Success(listOf(summary))
            coEvery { selectCredentialUseCase(any(), any()) } returns Outcome.Success(summary)
            coEvery { credentialRepository.getSignCount(summary.credentialId) } returns
                Outcome.Error(
                    DomainError.DatabaseError(
                        exceptionMarker,
                        IllegalStateException(exceptionMarker),
                    ),
                )

            val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

            val error = assertIs<DomainError.CryptoError>(assertIs<Outcome.Error<*>>(result).error)
            assertEquals("Unable to read credential signing counter", error.message)
            assertNull(error.cause)
            coVerify(exactly = 0) { cryptoService.sign(any(), any(), any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `returns failure when cryptoService signing fails`() =
        runTest {
            val s1 = createSummary("cred1")
            coEvery {
                credentialRepository.getCredentialSummariesForRpText(match { it.contentEquals(testRpId.value) })
            } returns Outcome.Success(listOf(s1))
            coEvery { selectCredentialUseCase(any(), any()) } returns Outcome.Success(s1)
            coEvery { credentialRepository.getSignCount(s1.credentialId) } returns Outcome.Success(SIGN_COUNT_5)
            coEvery { cryptoService.sign(any(), any(), any()) } returns
                Outcome.Error(
                    DomainError.CryptoError(
                        "Master seed unavailable",
                        Fido2Exception.SigningFailed("Master seed unavailable"),
                    ),
                )

            val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { credentialRepository.getCredentialById(s1.credentialId) }
            coVerify(exactly = 0) { credentialRepository.updateSignCount(s1.credentialId, any()) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `registered public key mismatch fails without mutating the selected key or sign counter`() =
        runTest {
            val storedKey = ByteArray(SIGNATURE_SIZE_72) { it.toByte() }
            val originalStoredKey = storedKey.copyOf()
            val selected = createSummary("registered-key-mismatch").copy(storedPublicKeyEncoding = storedKey)
            coEvery {
                credentialRepository.getCredentialSummariesForRpText(match { it.contentEquals(testRpId.value) })
            } returns
                Outcome.Success(listOf(selected))
            coEvery { selectCredentialUseCase(any(), any()) } returns Outcome.Success(selected)
            coEvery { credentialRepository.getSignCount(selected.credentialId) } returns Outcome.Success(SIGN_COUNT_5)
            coEvery {
                cryptoService.sign(
                    any(),
                    any(),
                    match { it.contentEquals(originalStoredKey) },
                )
            } returns
                Outcome.Error(
                    DomainError.CryptoError("Derived credential public key does not match the registered key"),
                )

            val result = useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))

            assertTrue(result.isFailure)
            assertContentEquals(originalStoredKey, selected.storedPublicKeyEncoding)
            coVerify(exactly = 0) { credentialRepository.updateSignCount(selected.credentialId, any()) }
            coVerify(exactly = 0) { credentialRepository.updateLastUsedAt(selected.credentialId) }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `signing cancellation propagates without changing the selected credential counter`() =
        runTest {
            val summary = createSummary("cancelled-signing")
            coEvery {
                credentialRepository.getCredentialSummariesForRpText(match { it.contentEquals(testRpId.value) })
            } returns
                Outcome.Success(listOf(summary))
            coEvery { selectCredentialUseCase(any(), any()) } returns Outcome.Success(summary)
            coEvery { credentialRepository.getSignCount(summary.credentialId) } returns Outcome.Success(SIGN_COUNT_5)
            coEvery { cryptoService.sign(any(), any(), any()) } throws CancellationException("synthetic cancellation")

            assertFailsWith<CancellationException> {
                useCase(createOptions(uv = UserVerificationRequirement.DISCOURAGED))
            }

            coVerify(exactly = 0) { credentialRepository.updateSignCount(summary.credentialId, any()) }
        }
}

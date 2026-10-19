package com.chimali.fido2.integration

import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.security.api.GeneratedCredentialPublicKey
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.ctap2.Ctap2MakeCredentialHandler
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.ClientDataHashService
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.repository.CredentialStatistics
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import com.chimali.fido2.domain.service.BiometricStrength
import com.chimali.fido2.domain.service.CeremonyLock
import com.chimali.fido2.domain.service.UserVerificationAvailability
import com.chimali.fido2.domain.service.UserVerificationRequirement
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.usecase.RegisterCredentialUseCase
import com.chimali.fido2.presentation.navigation.Fido2UiEventBus
import com.chimali.fido2.presentation.viewmodel.RegistrationEffect
import com.chimali.fido2.presentation.viewmodel.RegistrationIntent
import com.chimali.fido2.presentation.viewmodel.RegistrationPromptViewModel
import com.chimali.fido2.presentation.viewmodel.RegistrationState
import com.chimali.fido2.testing.stubSensitiveRequestCompatibility
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.security.KeyPairGenerator
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** Actual CTAP-to-presenter-to-use-case authorization. @see NFR-SEC-041 and TR-AUTH-031 */
@OptIn(ExperimentalCoroutinesApi::class)
@Req("NFR-SEC-041", "TR-AUTH-031")
class RequiredRegistrationVerificationIntegrationTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @ParameterizedTest
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @MethodSource("scenarios")
    fun `CTAP verification policy prevents unverified creation and signs truthful flags`(mode: String) =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val codec = mockk<CborCodec>()
            codec.stubSensitiveRequestCompatibility()
            val crypto = mockk<Fido2CryptoService>()
            val repository = mockk<CredentialRepository>()
            val verification = mockk<UserVerificationService>()
            val settings = mockk<Fido2SettingsRepository>()
            val provider = mockk<MasterSeedProvider>()
            every { provider.sessionGeneration() } returns 1L
            every { provider.isSessionGenerationCurrent(any()) } returns true
            val bus = Fido2UiEventBus()
            val algorithmScenario = mode.startsWith("algorithm-")
            val unsupportedOnlyScenario = mode == "algorithm-rs256-only"
            val available = mode != "required-unavailable" && mode != "optional-none" && !algorithmScenario
            coEvery { verification.getUserVerificationAvailability() } returns
                UserVerificationAvailability(
                    isBiometricAvailable = available,
                    isPinAvailable = false,
                    isDeviceLockAvailable = false,
                    supportedBiometricTypes = emptyList(),
                    maxPinLength = 8,
                    minPinLength = 4,
                    biometricStrength = BiometricStrength.STRONG,
                )
            coEvery { verification.isUserVerificationRequiredForText(any(), any(), any()) } returns
                UserVerificationRequirement.NOT_REQUIRED
            coEvery { provider.withSessionOwner<Any?>(any(), any(), any()) } coAnswers {
                thirdArg<suspend (Long) -> Any?>()(1L)
            }
            coEvery { repository.getRelyingPartyForText(any()) } returns null
            coEvery { repository.saveRelyingParty(any()) } returns Outcome.Success(Unit)
            coEvery { repository.validateCredentialCreationForText(any(), any()) } returns Outcome.Success(Unit)
            coEvery { repository.getCredentialStatistics() } returns CredentialStatistics(0, 0, 0, 0, 0.0)
            coEvery { repository.saveCredential(any()) } returns Outcome.Success(Unit)
            coEvery { settings.getMaxCredentialCount() } returns 100
            val key =
                KeyPairGenerator
                    .getInstance("EC")
                    .apply { initialize(256) }
                    .generateKeyPair()
                    .public
            val generatedDescriptor = slot<CredentialKeyDescriptor>()
            coEvery { crypto.generateCredentialPublicKey(capture(generatedDescriptor)) } answers {
                Outcome.Success(GeneratedCredentialPublicKey(ByteArray(65) { 1 }))
            }
            every { crypto.decodePublicKey(any(), any()) } returns key
            every { codec.encodeCosePublicKeyFromJavaKey(key) } returns byteArrayOf(0xA0.toByte())
            val signedData = slot<ByteArray>()
            coEvery { crypto.sign(any(), capture(signedData), any()) } returns Outcome.Success(byteArrayOf(1, 2, 3))
            val encodedResponse = slot<Map<String, Any>>()
            every { codec.encodeToFido2Format(capture(encodedResponse)) } returns byteArrayOf(0x55)
            val offeredAlgorithms = algorithmsFor(mode)
            every { codec.decodeFromFido2Format(any()) } returns
                mapOf(
                    "1" to ByteArray(32),
                    "2" to mapOf("id" to "example.com", "name" to "Synthetic RP"),
                    "3" to mapOf("id" to byteArrayOf(1), "name" to "synthetic-user", "displayName" to "Synthetic User"),
                    "4" to offeredAlgorithms.map { mapOf("alg" to it.toLong()) },
                    "7" to mapOf("uv" to (mode != "optional-none" && !algorithmScenario)),
                )
            val parser =
                mockk<HidReportParser> {
                    every { encodeResponse(any()) } answers { listOf(firstArg<CtapHidMessage>().payload) }
                }
            val lock = mockk<CeremonyLock>(relaxed = true) { every { tryLock() } returns true }
            val useCase =
                RegisterCredentialUseCase(repository, verification, codec, crypto, settings, ClientDataHashService())
            val presenter = RegistrationPromptViewModel(useCase, verification, bus, provider)
            val handler = Ctap2MakeCredentialHandler(codec, parser, bus, lock)
            val successExpected =
                mode == "required-success" || mode == "optional-none" || (algorithmScenario && !unsupportedOnlyScenario)
            val successEffect =
                if (successExpected) {
                    async {
                        presenter.effects.first { it is RegistrationEffect.NavigateToSuccess } as
                            RegistrationEffect.NavigateToSuccess
                    }
                } else {
                    null
                }
            try {
                val response =
                    async { handler.handle(CtapHidMessage(byteArrayOf(1, 2, 3, 4), 0x10, byteArrayOf(1, 0))) }
                runCurrent()
                if (mode != "optional-none" && !algorithmScenario) {
                    assertTrue(presenter.state.value is RegistrationState.AwaitingUserConsent)
                    presenter.handleIntent(RegistrationIntent.ConfirmRegistration)
                    runCurrent()
                    when (mode) {
                        "required-success" -> presenter.handleIntent(RegistrationIntent.UserVerificationSuccess(1L))
                        "required-failed" ->
                            presenter.handleIntent(
                                RegistrationIntent.UserVerificationFailed(1L, "Denied"),
                            )
                        "required-cancelled" -> presenter.handleIntent(RegistrationIntent.CancelRegistration)
                    }
                    runCurrent()
                }
                assertTrue(response.isCompleted, "Policy outcome must complete without consuming the request timeout")
                val packets = response.await()
                val succeeds = successExpected
                assertEquals(succeeds, packets.single().first() == 0.toByte())
                coVerify(exactly = if (succeeds) 1 else 0) { crypto.generateCredentialPublicKey(any()) }
                coVerify(exactly = if (succeeds) 1 else 0) { crypto.sign(any(), any(), any()) }
                coVerify(exactly = if (succeeds) 1 else 0) { repository.saveCredential(any()) }
                if (algorithmScenario && succeeds) {
                    val expectedAlgorithm = expectedAlgorithmFor(mode)
                    assertEquals(expectedAlgorithm, generatedDescriptor.captured.coseAlgorithm)
                    assertEquals(
                        CredentialKeyDescriptor.profileFor(expectedAlgorithm),
                        generatedDescriptor.captured.derivationProfile,
                    )
                    assertEquals(
                        expectedAlgorithm,
                        (encodedResponse.captured.getValue("3") as Map<*, *>)["alg"],
                    )
                }
                if (unsupportedOnlyScenario) {
                    assertEquals(0x26.toByte(), packets.single().first())
                    assertTrue(presenter.state.value is RegistrationState.Idle)
                }
                if (succeeds) {
                    val expectedFlags = if (mode == "required-success") 0x45 else 0x41
                    assertEquals(expectedFlags.toByte(), signedData.captured[32])
                    assertEquals(expectedFlags.toByte(), (encodedResponse.captured.getValue("2") as ByteArray)[32])
                    runCurrent()
                    val uiCredential = (presenter.state.value as RegistrationState.Success).credential
                    advanceUntilIdle()
                    assertEquals(uiCredential.id.encoded, successEffect?.await()?.credentialId)
                    presenter.handleIntent(RegistrationIntent.CancelRegistration)
                    runCurrent()
                    assertEquals(RegistrationState.Cancelled, presenter.state.value)
                    assertTrue(uiCredential.rpId.copyCharacters().all { it == '\u0000' })
                    assertTrue(uiCredential.userId.copyCharacters().all { it == '\u0000' })
                    assertTrue(uiCredential.userName.copyCharacters().all { it == '\u0000' })
                    assertTrue(uiCredential.userDisplayName.copyCharacters().all { it == '\u0000' })
                }
            } finally {
                presenter.handleIntent(RegistrationIntent.CancelRegistration)
                runCurrent()
                Dispatchers.resetMain()
            }
        }

    companion object {
        @JvmStatic
        fun scenarios(): Stream<String> =
            Stream.of(
                "required-unavailable",
                "required-failed",
                "required-cancelled",
                "required-success",
                "optional-none",
                "algorithm-rs256-es256",
                "algorithm-es256-rs256",
                "algorithm-ed25519",
                "algorithm-mldsa",
                "algorithm-rs256-only",
            )

        private fun algorithmsFor(mode: String): List<Int> =
            when (mode) {
                "algorithm-rs256-es256" -> listOf(-257, -7)
                "algorithm-es256-rs256" -> listOf(-7, -257)
                "algorithm-ed25519" -> listOf(-8)
                "algorithm-mldsa" -> listOf(-49)
                "algorithm-rs256-only" -> listOf(-257)
                else -> listOf(-7)
            }

        private fun expectedAlgorithmFor(mode: String): Int =
            when (mode) {
                "algorithm-ed25519" -> CredentialKeyDescriptor.COSE_EDDSA
                "algorithm-mldsa" -> CredentialKeyDescriptor.COSE_ML_DSA_65
                else -> CredentialKeyDescriptor.COSE_ES256
            }
    }
}

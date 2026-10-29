package com.chimali.fido2.performance

import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.core.security.api.GeneratedCredentialPublicKey
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.ClientDataHashService
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.domain.model.AuthenticatorSelectionCriteria
import com.chimali.fido2.domain.model.GetAssertionOptions
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.PublicKeyCredentialParameters
import com.chimali.fido2.domain.model.PublicKeyCredentialRpEntity
import com.chimali.fido2.domain.model.PublicKeyCredentialUserEntity
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.repository.CredentialStatistics
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import com.chimali.fido2.domain.service.BiometricStrength
import com.chimali.fido2.domain.service.UserVerificationAvailability
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import com.chimali.fido2.domain.usecase.RegisterCredentialUseCase
import com.chimali.fido2.domain.usecase.SelectCredentialUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.security.KeyPairGenerator
import java.security.Signature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Exercises real host-compatible registration and assertion use cases through one synthetic RP journey. */
@Req("TR-PERF-010")
class SimulatedWorkloadIntegrationTest {
    @Test
    fun `synthetic RP verifies registration and authentication through production use cases`() =
        runTest {
            val rpId = "https://example.com"
            val clientDataHash = ByteArray(32) { (it + 9).toByte() }
            val request = SimulatedRelyingParty.Request(rpId, rpId, clientDataHash, clientDataHash.copyOf())
            val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
            val generatedKeyBytes = ByteArray(65) { (it + 1).toByte() }
            val repository = mockk<CredentialRepository>()
            val verificationService = mockk<UserVerificationService>()
            val settings = mockk<Fido2SettingsRepository>()
            val crypto = mockk<Fido2CryptoService>()
            val cbor = CborCodec()
            every { crypto.decodePublicKey(any(), any()) } returns keyPair.public
            coEvery { crypto.generateCredentialPublicKey(any()) } returns
                Outcome.Success(GeneratedCredentialPublicKey(generatedKeyBytes.copyOf()))
            coEvery { crypto.sign(any(), any(), any()) } coAnswers {
                val signer = Signature.getInstance("SHA256withECDSA")
                signer.initSign(keyPair.private)
                signer.update(secondArg<ByteArray>())
                Outcome.Success(signer.sign())
            }
            coEvery { verificationService.isUserVerificationRequiredForText(any(), any(), any()) } returns
                com.chimali.fido2.domain.service.UserVerificationRequirement.NOT_REQUIRED
            coEvery { verificationService.getUserVerificationAvailability() } returns
                UserVerificationAvailability(
                    isBiometricAvailable = true,
                    isPinAvailable = false,
                    isDeviceLockAvailable = false,
                    supportedBiometricTypes = emptyList(),
                    maxPinLength = 8,
                    minPinLength = 4,
                    biometricStrength = BiometricStrength.STRONG,
                )
            coEvery { repository.getRelyingPartyForText(any()) } returns null
            coEvery { repository.saveRelyingParty(any<RelyingParty>()) } returns Outcome.Success(Unit)
            coEvery { repository.validateCredentialCreationForText(any(), any()) } returns Outcome.Success(Unit)
            coEvery { repository.getCredentialStatistics() } returns CredentialStatistics(0, 0, 0, 0, 0.0)
            coEvery { settings.getMaxCredentialCount() } returns 100
            coEvery { repository.saveCredential(any()) } returns Outcome.Success(Unit)

            val registrationUseCase =
                RegisterCredentialUseCase(
                    repository,
                    verificationService,
                    cbor,
                    crypto,
                    settings,
                    ClientDataHashService(),
                )
            val assertionUseCase =
                GetAssertionUseCase(
                    repository,
                    verificationService,
                    SelectCredentialUseCase(),
                    crypto,
                    ClientDataHashService(),
                )
            val options =
                MakeCredentialOptions.create(
                    rp = PublicKeyCredentialRpEntity.create(RpId(rpId), "Synthetic RP"),
                    user =
                        PublicKeyCredentialUserEntity.create(
                            UserId.fromByteArray("synthetic-user".encodeToByteArray()),
                            "user",
                            "Synthetic User",
                        ),
                    challenge = clientDataHash.copyOf(),
                    pubKeyCredParams = PublicKeyCredentialParameters.createES256P256(),
                    authenticatorSelection =
                        AuthenticatorSelectionCriteria.create(
                            userVerification = UserVerificationRequirement.REQUIRED,
                        ),
                    selectedAlgId = CredentialKeyDescriptor.COSE_ES256,
                )
            val relyingParty = SimulatedRelyingParty(cbor)
            val runner = SimulatedCeremony(SimulatedUserVerification())
            var rpCredential: SimulatedRelyingParty.VerifiedCredential? = null
            var summary: CredentialSummary? = null

            val registration =
                runner.run(
                    runId = "synthetic-fido2-run",
                    attemptId = "registration-1",
                    actionId = "registration-action-1",
                    kind = CeremonyKind.REGISTRATION,
                    attemptKind = AttemptKind.MEASURED,
                    issueRequest = { request },
                    execute = {
                        userVerification(VerificationOutcome.SUCCESS)
                        operation("makeCredential") {
                            when (val outcome = registrationUseCase(options, userVerificationPerformed = true)) {
                                is Outcome.Success ->
                                    outcome.data.also { result ->
                                        summary =
                                            CredentialSummary(
                                                id = result.credential.id.encoded,
                                                rpId = RpId(rpId),
                                                credentialId = result.credential.id,
                                                lastUsedAt = TimeProvider().now(),
                                                coseAlgorithm = result.credential.coseAlgorithm,
                                                derivationProfile = result.credential.derivationProfile,
                                                storedPublicKeyEncoding = result.credential.publicKey.encoded,
                                            )
                                    }
                                is Outcome.Error ->
                                    error(
                                        "synthetic registration failed: ${outcome.error::class.simpleName}",
                                    )
                            }
                        }
                    },
                    verify = { rpRequest, result ->
                        try {
                            relyingParty.verifyRegistration(rpRequest, result.attestationObject).value?.let {
                                rpCredential = it
                                true
                            }
                                ?: false
                        } finally {
                            result.eraseSensitiveText()
                        }
                    },
                )

            val registered = assertNotNull(summary)
            val verifiedCredential = assertNotNull(rpCredential)
            coEvery { repository.getSignCount(registered.credentialId) } returns Outcome.Success(0L)
            coEvery { repository.updateSignCount(registered.credentialId, 1L) } returns Outcome.Success(Unit)
            coEvery { repository.getCredentialSummariesForRpText(any()) } returns Outcome.Success(listOf(registered))
            val assertionOptions =
                GetAssertionOptions.create(
                    rpId = RpId(rpId),
                    clientDataHash = clientDataHash.copyOf(),
                    allowCredentials = null,
                    userVerification = UserVerificationRequirement.REQUIRED,
                )
            val authentication =
                runner.run(
                    runId = "synthetic-fido2-run",
                    attemptId = "authentication-1",
                    actionId = "authentication-action-1",
                    kind = CeremonyKind.AUTHENTICATION,
                    attemptKind = AttemptKind.MEASURED,
                    issueRequest = { request },
                    execute = {
                        userVerification(VerificationOutcome.SUCCESS)
                        operation("getAssertion") {
                            when (val outcome = assertionUseCase(assertionOptions, userVerificationPerformed = true)) {
                                is Outcome.Success -> outcome.data
                                is Outcome.Error ->
                                    error(
                                        "synthetic assertion failed: ${outcome.error::class.simpleName}",
                                    )
                            }
                        }
                    },
                    verify = { rpRequest, response ->
                        try {
                            relyingParty.verifyAssertion(rpRequest, verifiedCredential, response).verified
                        } finally {
                            response.authData.fill(0)
                            response.signature.fill(0)
                        }
                    },
                )

            assertEquals(CeremonyOutcome.VERIFIED, registration.outcome)
            assertEquals(CeremonyOutcome.VERIFIED, authentication.outcome)
            assertEquals("makeCredential", registration.operations.single().type)
            assertEquals("getAssertion", authentication.operations.single().type)
            assertTrue(
                registration.operations
                    .single()
                    .interval.elapsedNanos != null,
            )
            assertTrue(
                authentication.operations
                    .single()
                    .interval.elapsedNanos != null,
            )
            assertTrue(registration.verification.interval?.elapsedNanos != null)
            assertTrue(authentication.verification.interval?.elapsedNanos != null)
            registered.eraseSensitiveText()
            assertionOptions.eraseSensitiveText()
            assertionOptions.clientDataHash.fill(0)
            options.challenge.fill(0)
            options.rp.eraseSensitiveText()
            options.user.eraseSensitiveText()
            clientDataHash.fill(0)
            request.challenge.fill(0)
            request.clientDataHash.fill(0)
            generatedKeyBytes.fill(0)
        }
}

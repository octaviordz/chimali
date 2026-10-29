package com.chimali.fido2.performance

import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.core.security.api.GeneratedCredentialPublicKey
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.ClientDataHashService
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.domain.model.AuthenticatorSelectionCriteria
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.PublicKeyCredentialParameters
import com.chimali.fido2.domain.model.PublicKeyCredentialRpEntity
import com.chimali.fido2.domain.model.PublicKeyCredentialUserEntity
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.repository.CredentialStatistics
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.usecase.RegisterCredentialUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.security.KeyPairGenerator
import java.security.Signature
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Exercises the production registration use case and independently verifies its packed response on the host. */
@Req("TR-PERF-010")
class SimulatedRegistrationIntegrationTest {
    @Test
    fun `fake RP verifies real registration use case response`() =
        runTest {
            val rpId = "https://example.com"
            val requestHash = ByteArray(32) { (it + 7).toByte() }
            val options =
                MakeCredentialOptions.create(
                    rp = PublicKeyCredentialRpEntity.create(RpId(rpId), "Synthetic RP"),
                    user =
                        PublicKeyCredentialUserEntity.create(
                            UserId.fromByteArray("synthetic-user".encodeToByteArray()),
                            "user",
                            "User",
                        ),
                    challenge = requestHash,
                    pubKeyCredParams = PublicKeyCredentialParameters.createES256P256(),
                    authenticatorSelection =
                        AuthenticatorSelectionCriteria.create(
                            userVerification = UserVerificationRequirement.REQUIRED,
                        ),
                    selectedAlgId = CredentialKeyDescriptor.COSE_ES256,
                )
            val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
            val repository = mockk<CredentialRepository>()
            val verificationService = mockk<UserVerificationService>()
            val settings = mockk<Fido2SettingsRepository>()
            val crypto = mockk<Fido2CryptoService>()
            val cbor = CborCodec()
            every { crypto.decodePublicKey(any(), any()) } returns keyPair.public
            coEvery { crypto.generateCredentialPublicKey(any()) } returns
                Outcome.Success(GeneratedCredentialPublicKey(ByteArray(65) { 1 }))
            coEvery { crypto.sign(any(), any(), any()) } coAnswers {
                val signer = Signature.getInstance("SHA256withECDSA")
                signer.initSign(keyPair.private)
                signer.update(secondArg<ByteArray>())
                Outcome.Success(signer.sign())
            }
            coEvery { verificationService.isUserVerificationRequiredForText(any(), any(), any()) } returns
                com.chimali.fido2.domain.service.UserVerificationRequirement.NOT_REQUIRED
            coEvery { repository.getRelyingPartyForText(any()) } returns null
            coEvery { repository.saveRelyingParty(any<RelyingParty>()) } returns Outcome.Success(Unit)
            coEvery { repository.validateCredentialCreationForText(any(), any()) } returns Outcome.Success(Unit)
            coEvery { repository.getCredentialStatistics() } returns CredentialStatistics(0, 0, 0, 0, 0.0)
            coEvery { settings.getMaxCredentialCount() } returns 100
            coEvery { repository.saveCredential(any()) } returns Outcome.Success(Unit)
            val useCase =
                RegisterCredentialUseCase(
                    repository,
                    verificationService,
                    cbor,
                    crypto,
                    settings,
                    ClientDataHashService(),
                )
            val relyingParty = SimulatedRelyingParty(cbor)
            val request = SimulatedRelyingParty.Request(rpId, rpId, requestHash, requestHash.copyOf())
            val verificationDouble = SimulatedUserVerification()
            val attempt =
                SimulatedCeremony(verificationDouble).run(
                    runId = "synthetic-registration-run",
                    attemptId = "registration-1",
                    actionId = "registration-action-1",
                    kind = CeremonyKind.REGISTRATION,
                    attemptKind = AttemptKind.MEASURED,
                    issueRequest = { request },
                    execute = {
                        userVerification(VerificationOutcome.SUCCESS)
                        operation("makeCredential") {
                            when (val result = useCase(options, userVerificationPerformed = true)) {
                                is Outcome.Success -> result.data
                                is Outcome.Error ->
                                    error(
                                        "synthetic registration failed: ${result.error::class.simpleName}",
                                    )
                            }
                        }
                    },
                    verify = {
                        rpRequest,
                        response,
                        ->
                        relyingParty.verifyRegistration(rpRequest, response.attestationObject).verified
                    },
                )

            assertTrue(attempt.verifiedResponse)
            assertTrue(
                attempt.operations
                    .single()
                    .interval.elapsedNanos != null,
            )
            assertTrue(attempt.verification.interval?.elapsedNanos != null)
        }
}

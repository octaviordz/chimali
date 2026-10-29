package com.chimali.fido2.performance

import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.core.security.api.GeneratedCredentialPublicKey
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.ctap2.Ctap2GetAssertionHandler
import com.chimali.fido2.ctap2.Ctap2ResetAuthenticatorHandler
import com.chimali.fido2.ctap2.Ctap2ResponseBuilder
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.ClientDataHashService
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.data.transport.CtapHidCommandDispatcher
import com.chimali.fido2.data.transport.CtapHidRequestOwner
import com.chimali.fido2.data.transport.U2fApduCommandHandler
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
import com.chimali.fido2.domain.service.AuthenticatorInfoProvider
import com.chimali.fido2.domain.service.BiometricStrength
import com.chimali.fido2.domain.service.UserVerificationAvailability
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.usecase.GetAssertionUseCase
import com.chimali.fido2.domain.usecase.RegisterCredentialUseCase
import com.chimali.fido2.domain.usecase.ResetAuthenticatorUseCase
import com.chimali.fido2.domain.usecase.SelectCredentialUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature

/** Deterministic synthetic boundaries with real FIDO2 use-case, CBOR and ECDSA behavior. */
internal class SimulatedAssessmentFixture {
    private val relyingPartyId = "https://example.com"
    private val cbor = CborCodec()
    private val repository = mockk<CredentialRepository>()
    private val verificationService = mockk<UserVerificationService>()
    private val settings = mockk<Fido2SettingsRepository>()
    private val crypto = mockk<Fido2CryptoService>()
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private val generatedKeyBytes = ByteArray(65) { (it + 1).toByte() }
    private val userVerification = SimulatedUserVerification()
    private val ceremony = SimulatedCeremony(userVerification)
    private val relyingParty = SimulatedRelyingParty(cbor)
    private val registrationUseCase =
        RegisterCredentialUseCase(repository, verificationService, cbor, crypto, settings, ClientDataHashService())
    private val assertionUseCase =
        GetAssertionUseCase(repository, verificationService, SelectCredentialUseCase(), crypto, ClientDataHashService())
    private var currentSummary: CredentialSummary? = null
    private var relyingPartyCredential: SimulatedRelyingParty.VerifiedCredential? = null
    private var signingCounter = 0L

    init {
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
        coEvery { repository.resetAuthenticator() } returns Outcome.Success(Unit)
        coEvery { repository.getCredentialSummariesForRpText(any()) } coAnswers {
            val source = requireNotNull(currentSummary) { "synthetic registration has not produced a credential" }
            Outcome.Success(listOf(source.copy()))
        }
    }

    suspend fun registration(
        runId: String,
        attemptId: String,
        actionId: String,
        kind: AttemptKind,
        repetition: Int,
    ): CeremonyAttempt {
        val requestHash = syntheticHash("registration-$repetition")
        val request = SimulatedRelyingParty.Request(relyingPartyId, relyingPartyId, requestHash, requestHash.copyOf())
        val options =
            MakeCredentialOptions.create(
                rp = PublicKeyCredentialRpEntity.create(RpId(relyingPartyId), "Synthetic RP"),
                user =
                    PublicKeyCredentialUserEntity.create(
                        UserId.fromByteArray("synthetic-user".encodeToByteArray()),
                        "synthetic-user",
                        "Synthetic User",
                    ),
                challenge = request.clientDataHash.copyOf(),
                pubKeyCredParams = PublicKeyCredentialParameters.createES256P256(),
                authenticatorSelection =
                    AuthenticatorSelectionCriteria.create(
                        userVerification = UserVerificationRequirement.REQUIRED,
                    ),
                selectedAlgId = CredentialKeyDescriptor.COSE_ES256,
            )
        return try {
            ceremony.run(
                runId = runId,
                attemptId = attemptId,
                actionId = actionId,
                kind = CeremonyKind.REGISTRATION,
                attemptKind = kind,
                issueRequest = { request },
                execute = {
                    userVerification(VerificationOutcome.SUCCESS)
                    operation("MakeCredential") {
                        when (val result = registrationUseCase(options, userVerificationPerformed = true)) {
                            is Outcome.Success ->
                                result.data.also { makeResult ->
                                    currentSummary?.let { old ->
                                        old.storedPublicKeyEncoding.fill(0)
                                        old.eraseSensitiveText()
                                    }
                                    currentSummary =
                                        CredentialSummary(
                                            id = makeResult.credential.id.encoded,
                                            rpId = RpId(relyingPartyId),
                                            credentialId = makeResult.credential.id,
                                            lastUsedAt = TimeProvider().now(),
                                            coseAlgorithm = makeResult.credential.coseAlgorithm,
                                            derivationProfile = makeResult.credential.derivationProfile,
                                            storedPublicKeyEncoding = makeResult.credential.publicKey.encoded,
                                        )
                                    signingCounter = 0L
                                }
                            is Outcome.Error ->
                                error(
                                    "synthetic registration failed: ${result.error::class.simpleName}",
                                )
                        }
                    }
                },
                verify = { rpRequest, response ->
                    try {
                        relyingParty.verifyRegistration(rpRequest, response.attestationObject).value?.let { verified ->
                            relyingPartyCredential?.credentialId?.fill(0)
                            relyingPartyCredential = verified
                            true
                        } ?: false
                    } finally {
                        response.eraseSensitiveText()
                    }
                },
            )
        } finally {
            requestHash.fill(0)
            request.challenge.fill(0)
            request.clientDataHash.fill(0)
            options.challenge.fill(0)
            options.rp.eraseSensitiveText()
            options.user.eraseSensitiveText()
        }
    }

    suspend fun authentication(
        runId: String,
        attemptId: String,
        actionId: String,
        kind: AttemptKind,
        repetition: Int,
    ): CeremonyAttempt {
        val requestHash = syntheticHash("authentication-$repetition")
        val request = SimulatedRelyingParty.Request(relyingPartyId, relyingPartyId, requestHash, requestHash.copyOf())
        val summary = requireNotNull(currentSummary) { "synthetic registration has not produced a credential" }
        val credential = requireNotNull(relyingPartyCredential) { "synthetic RP has not verified a registration" }
        coEvery { repository.getSignCount(summary.credentialId) } coAnswers { Outcome.Success(signingCounter) }
        coEvery { repository.updateSignCount(summary.credentialId, any()) } coAnswers {
            signingCounter = secondArg()
            Outcome.Success(Unit)
        }
        val options =
            GetAssertionOptions.create(
                rpId = RpId(relyingPartyId),
                clientDataHash = request.clientDataHash.copyOf(),
                allowCredentials = null,
                userVerification = UserVerificationRequirement.REQUIRED,
            )
        return try {
            ceremony.run(
                runId = runId,
                attemptId = attemptId,
                actionId = actionId,
                kind = CeremonyKind.AUTHENTICATION,
                attemptKind = kind,
                issueRequest = { request },
                execute = {
                    userVerification(VerificationOutcome.SUCCESS)
                    operation("GetAssertion") {
                        when (val result = assertionUseCase(options, userVerificationPerformed = true)) {
                            is Outcome.Success -> result.data
                            is Outcome.Error -> error("synthetic assertion failed: ${result.error::class.simpleName}")
                        }
                    }
                },
                verify = { rpRequest, response ->
                    try {
                        relyingParty.verifyAssertion(rpRequest, credential, response).verified
                    } finally {
                        response.authData.fill(0)
                        response.signature.fill(0)
                    }
                },
            )
        } finally {
            requestHash.fill(0)
            request.challenge.fill(0)
            request.clientDataHash.fill(0)
            options.clientDataHash.fill(0)
            options.eraseSensitiveText()
        }
    }

    /** Runs the production GetInfo dispatcher, capability provider and CTAP response encoder. */
    suspend fun getInfoProbe(
        runId: String,
        attemptId: String,
        actionId: String,
        kind: AttemptKind,
        repetition: Int,
    ): CeremonyAttempt {
        val requestHash = syntheticHash("get-info-$repetition")
        val request = SimulatedRelyingParty.Request(relyingPartyId, relyingPartyId, requestHash, requestHash.copyOf())
        val parser = HidReportParser()
        val responseBuilder = Ctap2ResponseBuilder(cbor, parser)
        val dispatcher =
            CtapHidCommandDispatcher(
                hidReportParser = parser,
                responseBuilder = responseBuilder,
                getAssertionHandler = mockk<Ctap2GetAssertionHandler>(relaxed = true),
                authenticatorInfoProvider = AuthenticatorInfoProvider(),
                cborRequestOwner = mockk<CtapHidRequestOwner>(relaxed = true),
                u2fApduCommandHandler = mockk<U2fApduCommandHandler>(relaxed = true),
                isCurrentSession = { true },
                isChannelRegistered = { _, _ -> true },
                allocateCid = { null },
                sendPackets = { _, _ -> },
            )
        val cid = byteArrayOf(1, 2, 3, 4)
        return try {
            ceremony.run(
                runId = runId,
                attemptId = attemptId,
                actionId = actionId,
                kind = CeremonyKind.OPERATION_PROBE,
                attemptKind = kind,
                issueRequest = { request },
                execute = {
                    operation("GetInfo") { dispatcher.handleGetInfo(cid) }
                },
                verify = { _, packets -> packets.isNotEmpty() && packets.all { it.size == 62 } },
            )
        } finally {
            requestHash.fill(0)
            request.challenge.fill(0)
            request.clientDataHash.fill(0)
            cid.fill(0)
        }
    }

    /** Runs the production reset handler/use case against an isolated synthetic repository. */
    suspend fun resetProbe(
        runId: String,
        attemptId: String,
        actionId: String,
        kind: AttemptKind,
        repetition: Int,
    ): CeremonyAttempt {
        val requestHash = syntheticHash("reset-$repetition")
        val request = SimulatedRelyingParty.Request(relyingPartyId, relyingPartyId, requestHash, requestHash.copyOf())
        val handler = Ctap2ResetAuthenticatorHandler(ResetAuthenticatorUseCase(repository))
        return try {
            ceremony.run(
                runId = runId,
                attemptId = attemptId,
                actionId = actionId,
                kind = CeremonyKind.OPERATION_PROBE,
                attemptKind = kind,
                issueRequest = { request },
                execute = { operation("ResetAuthenticator") { handler.handle(byteArrayOf()) } },
                verify = { _, response -> response.contentEquals(byteArrayOf(0x00)) },
            )
        } finally {
            requestHash.fill(0)
            request.challenge.fill(0)
            request.clientDataHash.fill(0)
        }
    }

    fun dispose() {
        currentSummary?.let {
            it.storedPublicKeyEncoding.fill(0)
            it.eraseSensitiveText()
        }
        relyingPartyCredential?.credentialId?.fill(0)
        generatedKeyBytes.fill(0)
        runCatching { keyPair.private.destroy() }
    }

    private fun syntheticHash(label: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("TR-PERF-010:$label".encodeToByteArray())
}

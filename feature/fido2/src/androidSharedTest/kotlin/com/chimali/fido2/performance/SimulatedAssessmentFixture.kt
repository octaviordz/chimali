@file:Suppress("MagicNumber")

package com.chimali.fido2.performance

import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.CredentialSummary
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.model.UserConsentRecord
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
import com.chimali.fido2.domain.service.BiometricEnrollmentStatus
import com.chimali.fido2.domain.service.BiometricStrength
import com.chimali.fido2.domain.service.PinConfiguration
import com.chimali.fido2.domain.service.UserVerificationAvailability
import com.chimali.fido2.domain.service.UserVerificationRequirement as ServiceVerificationRequirement
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.Instant

/** Deterministic synthetic boundaries with real FIDO2 use-case, CBOR and ECDSA behavior. */
internal class SimulatedAssessmentFixture {
    private val relyingPartyId = "https://example.com"
    private val cbor = CborCodec()
    private val repository = mockk<CredentialRepository>()
    private val verificationService = ImmediateVerificationPolicy()
    private val settings =
        object : Fido2SettingsRepository {
            override suspend fun getMaxCredentialCount(): Int = MAX_CREDENTIAL_COUNT

            override suspend fun setMaxCredentialCount(count: Int) = Unit
        }
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
        coEvery { repository.getRelyingPartyForText(any()) } returns null
        coEvery { repository.saveRelyingParty(any<RelyingParty>()) } returns Outcome.Success(Unit)
        coEvery { repository.validateCredentialCreationForText(any(), any()) } returns Outcome.Success(Unit)
        coEvery { repository.getCredentialStatistics() } returns CredentialStatistics(0, 0, 0, 0, 0.0)
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
        verificationOutcome: VerificationOutcome = VerificationOutcome.SUCCESS,
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
                    val verification = requireSuccessfulUserVerification(verificationOutcome)
                    operation("GetInfo") {
                        getInfoDispatcher().handleGetInfo(byteArrayOf(1, 2, 3, 4)).also { packets ->
                            check(isSuccessfulGetInfo(packets)) { "GetInfo returned an invalid CTAP response" }
                        }
                    }
                    operation("MakeCredential") {
                        when (
                            val result =
                                registrationUseCase(
                                    options,
                                    userVerificationPerformed = verification.outcome == VerificationOutcome.SUCCESS,
                                )
                        ) {
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
        verificationOutcome: VerificationOutcome = VerificationOutcome.SUCCESS,
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
                    val verification = requireSuccessfulUserVerification(verificationOutcome)
                    val assertion =
                        operation("GetAssertion") {
                            when (
                                val result =
                                    assertionUseCase(
                                        options,
                                        userVerificationPerformed =
                                            verification.outcome == VerificationOutcome.SUCCESS,
                                    )
                            ) {
                                is Outcome.Success -> result.data
                                is Outcome.Error ->
                                    error(
                                        "synthetic assertion failed: ${result.error::class.simpleName}",
                                    )
                            }
                        }
                    operation("ResetAuthenticator") {
                        Ctap2ResetAuthenticatorHandler(ResetAuthenticatorUseCase(repository))
                            .handle(byteArrayOf())
                            .also { status -> check(status.contentEquals(byteArrayOf(0))) }
                    }
                    assertion
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
    private fun getInfoDispatcher(): CtapHidCommandDispatcher {
        val parser = HidReportParser()
        val responseBuilder = Ctap2ResponseBuilder(cbor, parser)
        return CtapHidCommandDispatcher(
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

    private class ImmediateVerificationPolicy : UserVerificationService {
        override suspend fun isUserVerificationRequiredForText(
            rpId: CharSequence,
            operationType: String,
            context: com.chimali.fido2.domain.service.VerificationContext?,
        ): ServiceVerificationRequirement = ServiceVerificationRequirement.NOT_REQUIRED

        override suspend fun getUserVerificationAvailability() =
            UserVerificationAvailability(
                isBiometricAvailable = true,
                isPinAvailable = false,
                isDeviceLockAvailable = false,
                supportedBiometricTypes = emptyList(),
                maxPinLength = 8,
                minPinLength = 4,
                biometricStrength = BiometricStrength.STRONG,
            )

        override suspend fun isBiometricAvailable(): Boolean = true

        override suspend fun isPinAvailable(): Boolean = false

        override suspend fun getBiometricEnrollmentStatus() =
            BiometricEnrollmentStatus(false, emptyList(), BiometricStrength.STRONG, Instant.fromEpochMilliseconds(0))

        override suspend fun getPinConfiguration() = PinConfiguration(4, 8, false, null, 5, 0L)

        override suspend fun recordUserConsent(consent: UserConsentRecord) = Outcome.Success(Unit)

        override fun getRecentConsentRecords(
            rpId: RpId?,
            limit: Int,
        ): Flow<UserConsentRecord> = emptyFlow()

        override suspend fun isUserVerificationRequired(
            rpId: RpId,
            operationType: String,
            context: com.chimali.fido2.domain.service.VerificationContext?,
        ): ServiceVerificationRequirement = ServiceVerificationRequirement.NOT_REQUIRED
    }

    private companion object {
        const val MAX_CREDENTIAL_COUNT = 100
    }
}

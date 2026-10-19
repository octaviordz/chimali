package com.chimali.fido2.domain.usecase

import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.mapError
import com.chimali.core.domain.model.ConsentOperationType
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.CredentialProtectionPolicy
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.model.SensitiveText
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.model.AttestationObject
import com.chimali.fido2.domain.model.AttestationStatement
import com.chimali.fido2.domain.model.AuthenticatorData
import com.chimali.fido2.domain.model.ClientData
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.MakeCredentialResult
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.PublicKeyCredentialRpEntity
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import com.chimali.fido2.domain.service.UserVerificationRequirement as ServiceVerificationRequirement
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.service.VerificationContext
import com.chimali.fido2.util.performance.PerformanceTrace
import kotlinx.coroutines.CancellationException
import org.koin.core.annotation.Factory

/**
 * Use case for registering new FIDO2 credentials.
 * Handles the complete credential registration flow with user verification.
 */
@Factory
class RegisterCredentialUseCase(
    private val passkeyCredentialRepository: CredentialRepository,
    private val userVerificationService: UserVerificationService,
    private val cborCodec: CborCodec,
    private val cryptoService: Fido2CryptoService,
    private val settingsRepository: Fido2SettingsRepository,
    private val clientDataHashService: com.chimali.fido2.data.crypto.ClientDataHashService,
) {
    companion object {
        private const val MAX_CHALLENGE_SIZE = 64
        private const val MAX_TIMEOUT_MS = 600_000 // 10 minutes
        private const val MAX_CREDENTIALS_IN_LIST = 32

        // Authenticator Flags
        private const val FLAG_USER_PRESENT = 0x01
        private const val FLAG_USER_VERIFIED = 0x04
        private const val FLAG_ATTESTED_CRED_DATA = 0x40
        private const val SHIFT_8 = 8
        private const val BYTE_MASK_FF = 0xFF

        private val CHIMALI_AAGUID =
            byteArrayOf(
                // "CHIMALI\0"
                0x43,
                0x48,
                0x49,
                0x4D,
                0x41,
                0x4C,
                0x49,
                0x00,
                // ...version 1
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x01,
            )
    }

    /**
     * Registers a new credential with the authenticator.
     *
     * @param options The registration options containing all necessary parameters
     * @return Result containing MakeCredentialResult (Attestation + Passkey) on success
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend operator fun invoke(
        options: MakeCredentialOptions,
        userVerificationPerformed: Boolean = false,
    ): Outcome<MakeCredentialResult, DomainError> =
        invokeWithCredentialOwnerObserver(options, userVerificationPerformed) {}

    /** Runs registration while exposing the created owner to retained-reference ownership tests. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal suspend fun invokeWithCredentialOwnerObserver(
        options: MakeCredentialOptions,
        userVerificationPerformed: Boolean,
        onAttestationOwned: (AttestationObject) -> Unit = {},
        onCredentialOwned: (PasskeyCredential) -> Unit,
    ): Outcome<MakeCredentialResult, DomainError> {
        var credentialOwner: PasskeyCredential? = null
        var attestationOwner: AttestationObject? = null
        var transferred = false
        return try {
            val verificationRequirement =
                options.authenticatorSelection?.userVerification ?: UserVerificationRequirement.PREFERRED
            if (verificationRequirement == UserVerificationRequirement.REQUIRED && !userVerificationPerformed) {
                return Outcome.Error(
                    DomainError.OperationDenied(
                        "User verification is required before credential registration",
                        Fido2Exception.NoVerificationMethodAvailable(),
                    ),
                )
            }

            // Validate registration options
            validateRegistrationOptions(options)

            // Ensure relying party exists before creating consent records or credentials
            val rpResult =
                PerformanceTrace.section("Fido2.Registration.RelyingParty") {
                    updateRelyingParty(options.rp)
                }
            if (rpResult is Outcome.Error) return Outcome.Error(rpResult.error)

            // Check if user consent is required
            val consentRequired =
                PerformanceTrace.section("Fido2.Registration.VerificationPolicy") {
                    userVerificationService.isUserVerificationRequiredForText(
                        rpId = options.rp.idText,
                        operationType = "registration",
                        context = VerificationContext.CREDENTIAL_CREATION,
                    )
                }

            // Get user consent if required
            if (consentRequired == ServiceVerificationRequirement.REQUIRED) {
                val consentResult = getUserConsentForRegistration(options)
                if (consentResult is Outcome.Error) {
                    return Outcome.Error(consentResult.error)
                }
            }

            // Validate credential creation with repository
            val validationResult =
                PerformanceTrace.section("Fido2.Registration.Validation") {
                    val userId = options.user.copyEncodedIdOwner()
                    try {
                        passkeyCredentialRepository.validateCredentialCreationForText(options.rp.idText, userId)
                    } finally {
                        userId.erase()
                    }
                }
            if (validationResult is Outcome.Error) {
                return Outcome.Error(validationResult.error)
            }

            // FR-HID-022: Enforce global storage limit.
            val stats =
                PerformanceTrace.section("Fido2.Registration.Statistics") {
                    passkeyCredentialRepository.getCredentialStatistics()
                }
            val limit = settingsRepository.getMaxCredentialCount()
            if (stats.totalCredentials >= limit) {
                return Outcome.Error(
                    DomainError.StorageError(
                        "Too many credentials: limit $limit",
                        Fido2Exception.TooManyCredentials(limit),
                    ),
                )
            }

            // Generate the credential
            val credentialGenerationResult =
                PerformanceTrace.section("Fido2.Registration.PublicKey") {
                    generateCredential(options)
                }
            if (credentialGenerationResult is Outcome.Error) {
                return Outcome.Error(credentialGenerationResult.error)
            }

            val credential = (credentialGenerationResult as Outcome.Success).data
            credentialOwner = credential
            onCredentialOwned(credential)

            // Finish current self-attestation before persisting the registered profile.
            val attestationResult =
                PerformanceTrace.section("Fido2.Registration.Attestation") {
                    createAttestationObject(
                        options = options,
                        credential = credential,
                        userVerificationPerformed = userVerificationPerformed,
                    )
                }
            val attestationObject =
                when (attestationResult) {
                    is Outcome.Success -> attestationResult.data
                    is Outcome.Error -> return Outcome.Error(attestationResult.error)
                }

            attestationOwner = attestationObject
            onAttestationOwned(attestationObject)

            // Store only after current key generation and attestation processing have completed.
            val storageResult =
                PerformanceTrace.section("Fido2.Registration.Persistence") {
                    passkeyCredentialRepository.saveCredential(credential)
                }
            if (storageResult is Outcome.Error) {
                return Outcome.Error(storageResult.error)
            }

            val result = MakeCredentialResult(attestationObject, credential)
            transferred = true
            Outcome.Success(result)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IllegalArgumentException) {
            Outcome.Error(DomainError.ValidationError("Invalid credential request"))
        } catch (_: IllegalStateException) {
            Outcome.Error(DomainError.OperationDenied("Credential operation is unavailable"))
        } finally {
            if (!transferred) {
                credentialOwner?.eraseSensitiveText()
                attestationOwner?.clientData?.eraseSensitiveText()
            }
        }
    }

    /**
     * Validates the registration options according to FIDO2 specifications.
     */

    private fun validateRegistrationOptions(options: MakeCredentialOptions) {
        options.rp.validate()
        options.user.validate()
        options.pubKeyCredParams.validate()

        require(options.challenge.isNotEmpty()) { "Challenge cannot be empty" }
        require(options.challenge.size <= MAX_CHALLENGE_SIZE) { "Challenge cannot exceed $MAX_CHALLENGE_SIZE bytes" }

        val timeout = options.getSafeTimeout()
        require(timeout > 0) { "Timeout must be positive" }
        require(timeout <= MAX_TIMEOUT_MS) { "Timeout cannot exceed 10 minutes" }

        options.allowCredentials?.let { allowList ->
            require(allowList.size <= MAX_CREDENTIALS_IN_LIST) {
                "Allow credentials list cannot exceed $MAX_CREDENTIALS_IN_LIST items"
            }
            allowList.forEach { it.validate() }
        }

        options.excludeCredentials?.let { excludeList ->
            require(
                excludeList.size <= MAX_CREDENTIALS_IN_LIST,
            ) { "Exclude credentials list cannot exceed $MAX_CREDENTIALS_IN_LIST items" }
            excludeList.forEach { it.validate() }
        }
    }

    /**
     * Gets user consent for credential registration.
     */

    private suspend fun getUserConsentForRegistration(options: MakeCredentialOptions): Outcome<Unit, DomainError> {
        val consentRecord =
            UserConsentRecord.fromText(
                id =
                    java.util.UUID
                        .randomUUID()
                        .toString(),
                operationType = ConsentOperationType.REGISTRATION,
                rpId = options.rp.idText,
                credentialId = null,
                isBiometricUsed =
                    options.authenticatorSelection?.userVerification ==
                        UserVerificationRequirement.REQUIRED,
                isPinUsed = options.authenticatorSelection?.userVerification == UserVerificationRequirement.REQUIRED,
            )

        return try {
            userVerificationService
                .recordUserConsent(consentRecord)
                .mapError {
                    DomainError.OperationDenied(
                        "Consent denied",
                        Fido2Exception.ConsentDenied("Consent denied"),
                    )
                }
        } finally {
            consentRecord.eraseSensitiveText()
        }
    }

    /**
     * Generates a new credential with cryptographic operations.
     */

    private suspend fun generateCredential(options: MakeCredentialOptions): Outcome<PasskeyCredential, DomainError> {
        return try {
            // Generate random 32-byte credential ID (bytes + encoded string).
            val credentialId = PasskeyCredential.generateRandomId()

            val descriptor = descriptorFor(credentialId, options.selectedAlgId)
            val publicKeyResult = cryptoService.generateCredentialPublicKey(descriptor)
            val generatedPublicKey =
                when (publicKeyResult) {
                    is Outcome.Success -> publicKeyResult.data.publicKey
                    is Outcome.Error -> return Outcome.Error(publicKeyResult.error)
                }
            try {
                // Adapt the public bytes already returned by this derivation; do not derive again.
                val publicKey = cryptoService.decodePublicKey(descriptor, generatedPublicKey)

                // Generate AAGUID for this authenticator
                val aaguid = CHIMALI_AAGUID
                val credProtectPolicy =
                    CredentialProtectionPolicy.fromExtensions(options.extensions) ?: CredentialProtectionPolicy.DEFAULT

                // Create the credential domain model
                val credential =
                    PasskeyCredential.createFromRequestOwners(
                        id = credentialId,
                        rp = options.rp,
                        user = options.user,
                        publicKey = publicKey,
                        aaguid = aaguid,
                        credentialId = credentialId.toByteArray(),
                        coseAlgorithm = options.selectedAlgId,
                        derivationProfile = descriptor.derivationProfile,
                        credProtectPolicy = credProtectPolicy,
                    )

                Outcome.Success(credential)
            } finally {
                generatedPublicKey.fill(0)
            }
        } catch (_: java.security.GeneralSecurityException) {
            Outcome.Error(DomainError.CryptoError("Credential cryptographic operation failed"))
        }
    }

    /**
     * Updates relying party information in the repository.
     */

    private suspend fun updateRelyingParty(rp: PublicKeyCredentialRpEntity): Outcome<Unit, DomainError> {
        val existingRp = passkeyCredentialRepository.getRelyingPartyForText(rp.idText)
        var rpToSave: RelyingParty? = null
        try {
            val owned =
                existingRp?.withCredentialCount(existingRp.credentialCount + 1)
                    ?: RelyingParty.fromOwnedText(
                        SensitiveText.copyOf(rp.idText),
                        SensitiveText.copyOf(rp.nameText),
                        rp.icon,
                    )
            rpToSave = owned
            return passkeyCredentialRepository.saveRelyingParty(owned)
        } finally {
            rpToSave?.eraseSensitiveText()
            existingRp?.eraseSensitiveText()
        }
    }

    /** Hashes a borrowed request RP identifier through an erasable owner and releases that copy. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    private fun rpIdHash(rp: PublicKeyCredentialRpEntity): ByteArray {
        val owner = rp.copyIdOwner()
        return try {
            clientDataHashService.rpIdHash(owner)
        } finally {
            owner.erase()
        }
    }

    /**
     * Creates an attestation object for the registration response.
     *
     * Uses packed self-attestation with the credential's current descriptor. Required signing
     * failures are returned so the caller does not persist an unusable registration.
     */

    private suspend fun createAttestationObject(
        options: MakeCredentialOptions,
        credential: PasskeyCredential,
        userVerificationPerformed: Boolean,
    ): Outcome<AttestationObject, DomainError> {
        // Compute COSE public key once — used in both authDataBytes (signed) and AuthenticatorData (serialized).
        // They MUST be the same bytes; signing authDataBytes with a different key than
        // the COSE key embedded in it causes "Invalid data" on the server.
        val pubKeyCose = cborCodec.encodeCosePublicKeyFromJavaKey(credential.publicKey)

        // Build authenticatorData per WebAuthn §6.1
        val authDataBytes =
            run {
                val rpIdHash = rpIdHash(options.rp)
                val flags = createAuthenticatorFlags(userVerificationPerformed)
                val counter = byteArrayOf(0, 0, 0, 0) // 4-byte big-endian sign count = 0
                val credIdLen =
                    byteArrayOf(
                        (credential.credentialId.size shr SHIFT_8).toByte(),
                        (credential.credentialId.size and BYTE_MASK_FF).toByte(),
                    )
                java.io
                    .ByteArrayOutputStream()
                    .apply {
                        write(rpIdHash)
                        write(flags)
                        write(counter)
                        write(credential.aaguid)
                        write(credIdLen)
                        write(credential.credentialId)
                        write(pubKeyCose)
                    }.toByteArray()
            }

        val authData =
            AuthenticatorData.create(
                rpIdHash = rpIdHash(options.rp),
                flags = createAuthenticatorFlags(userVerificationPerformed),
                counter = 0L,
                aaguid = credential.aaguid,
                credentialId = credential.credentialId,
                // reuse — must match bytes in authDataBytes above
                publicKey = pubKeyCose,
            )

        // For CTAP2, the host already computed the clientDataHash and passed it in options.challenge.
        // Sign authData || options.challenge (clientDataHash) with the SAME algorithm as the credential.
        // Passing options.selectedAlgId ensures ML-DSA credentials are signed with ML-DSA,
        // not the default ES256 — a mismatch causes "Invalid data" during server verification.
        val descriptor = descriptorFor(credential.id, options.selectedAlgId)
        val signatureResult =
            cryptoService.sign(
                descriptor = descriptor,
                data = authDataBytes + options.challenge,
                expectedStoredPublicKey = credential.publicKey.encoded,
            )

        val signature =
            when (signatureResult) {
                is Outcome.Success -> signatureResult.data
                is Outcome.Error -> return Outcome.Error(signatureResult.error)
            }

        val clientData =
            ClientData.createFromText(
                type = "webauthn.create",
                challenge = options.challenge,
                origin = options.rp.idText,
            )
        var transferred = false
        try {
            return Outcome
                .Success(
                    AttestationObject.create(
                        fmt = "packed",
                        authData = authData,
                        attStmt =
                            AttestationStatement.create(
                                alg = options.selectedAlgId,
                                fmt = "packed",
                                attCert = signature,
                                authData = authDataBytes,
                            ),
                        clientData = clientData,
                    ),
                ).also { transferred = true }
        } finally {
            if (!transferred) clientData.eraseSensitiveText()
        }
    }

    private fun descriptorFor(
        credentialId: CredentialId,
        coseAlgorithm: Int,
    ): CredentialKeyDescriptor =
        CredentialKeyDescriptor(
            credentialId = credentialId,
            coseAlgorithm = coseAlgorithm,
            derivationProfile = CredentialKeyDescriptor.profileFor(coseAlgorithm),
        )

    private fun createAuthenticatorFlags(userVerificationPerformed: Boolean): ByteArray {
        var flags = 0x00
        flags = flags or FLAG_USER_PRESENT
        if (userVerificationPerformed) {
            flags = flags or FLAG_USER_VERIFIED
        }
        flags = flags or FLAG_ATTESTED_CRED_DATA
        return byteArrayOf(flags.toByte())
    }
}

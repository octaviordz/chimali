package com.chimali.fido2.domain.usecase

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.map
import com.chimali.core.common.result.mapError
import com.chimali.core.domain.model.ConsentOperationType
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.model.UserConsentRecord
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
        private const val DEFAULT_CRED_PROTECT_POLICY = 1

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
    suspend operator fun invoke(options: MakeCredentialOptions): Outcome<MakeCredentialResult, DomainError> {
        return try {
            // Validate registration options
            validateRegistrationOptions(options)

            // Ensure relying party exists before creating consent records or credentials
            val rpResult = updateRelyingParty(options.rp)
            if (rpResult is Outcome.Error) return Outcome.Error(rpResult.error)

            // Check if user consent is required
            val consentRequired =
                userVerificationService.isUserVerificationRequired(
                    rpId = options.rp.id,
                    operationType = "registration",
                    context = VerificationContext.CREDENTIAL_CREATION,
                )

            // Get user consent if required
            if (consentRequired == ServiceVerificationRequirement.REQUIRED) {
                val consentResult = getUserConsentForRegistration(options)
                if (consentResult is Outcome.Error) {
                    return Outcome.Error(consentResult.error)
                }
            }

            // Verify user identity if required
            val verificationResult = performUserVerification(options)
            if (verificationResult is Outcome.Error) {
                return Outcome.Error(verificationResult.error)
            }

            // Validate credential creation with repository
            val validationResult =
                passkeyCredentialRepository.validateCredentialCreation(
                    rpId = options.rp.id,
                    userId = options.user.id,
                )
            if (validationResult is Outcome.Error) {
                return Outcome.Error(validationResult.error)
            }

            // FR-HID-022: Enforce global storage limit.
            val stats = passkeyCredentialRepository.getCredentialStatistics()
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
            val credentialGenerationResult = generateCredential(options)
            if (credentialGenerationResult is Outcome.Error) {
                return Outcome.Error(credentialGenerationResult.error)
            }

            val credential = (credentialGenerationResult as Outcome.Success).data

            // Store the credential
            val storageResult = passkeyCredentialRepository.saveCredential(credential)
            if (storageResult is Outcome.Error) {
                return Outcome.Error(storageResult.error)
            }

            // Create attestation object
            val attestationObject =
                createAttestationObject(
                    options = options,
                    credential = credential,
                )

            Outcome.Success(MakeCredentialResult(attestationObject, credential))
        } catch (e: IllegalArgumentException) {
            Outcome.Error(DomainError.ValidationError(e.message ?: "Invalid parameters", e))
        } catch (e: IllegalStateException) {
            Outcome.Error(DomainError.OperationDenied(e.message ?: "Invalid state", e))
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
    private suspend fun getUserConsentForRegistration(
        options: MakeCredentialOptions,
    ): Outcome<UserConsentRecord, DomainError> {
        val consentRecord =
            UserConsentRecord.create(
                id =
                    java.util.UUID
                        .randomUUID()
                        .toString(),
                operationType = ConsentOperationType.REGISTRATION,
                rpId = options.rp.id,
                credentialId = null,
                isBiometricUsed =
                    options.authenticatorSelection?.userVerification ==
                        UserVerificationRequirement.REQUIRED,
                isPinUsed = options.authenticatorSelection?.userVerification == UserVerificationRequirement.REQUIRED,
                // Will be populated by actual implementation
                ipAddress = null,
                // Will be populated by actual implementation
                userAgent = null,
                // Will be populated by actual implementation
                deviceId = null,
            )

        return userVerificationService
            .recordUserConsent(consentRecord)
            .map { consentRecord }
            .mapError { DomainError.OperationDenied("Consent denied", it.cause) }
    }

    /**
     * Performs user verification for credential registration.
     */
    private suspend fun performUserVerification(options: MakeCredentialOptions): Outcome<Unit, DomainError> {
        val verificationRequirement =
            options.authenticatorSelection?.userVerification
                ?: UserVerificationRequirement.PREFERRED

        if (verificationRequirement == UserVerificationRequirement.REQUIRED) {
            val availability = userVerificationService.getUserVerificationAvailability()
            if (availability.getBestAvailableMethod() == com.chimali.fido2.domain.service.VerificationMethod.NONE) {
                return Outcome.Error(
                    DomainError.OperationDenied(
                        "No verification method available",
                        Fido2Exception.NoVerificationMethodAvailable(),
                    ),
                )
            }
        }

        return Outcome.Success(Unit)
    }

    /**
     * Generates a new credential with cryptographic operations.
     */
    private suspend fun generateCredential(options: MakeCredentialOptions): Outcome<PasskeyCredential, DomainError> {
        return try {
            // Generate random 32-byte credential ID (bytes + encoded string).
            val credentialId = PasskeyCredential.generateRandomId()

            // Generate hardware-backed key pair via Fido2CryptoService
            val cryptoResult =
                cryptoService.generateCredentialKeyPair(
                    credentialId = credentialId,
                    algId = options.selectedAlgId,
                )
            if (cryptoResult is Outcome.Error) {
                Outcome.Error(cryptoResult.error)
            } else {
                // Retrieve the public key object for PasskeyCredential
                val publicKey =
                    cryptoService.getPublicKey(
                        credentialId = credentialId,
                        algId = options.selectedAlgId,
                    ) ?: return Outcome.Error(
                        DomainError.CryptoError("Generated key not found in KeyStore: $credentialId"),
                    )

                // Generate AAGUID for this authenticator
                val aaguid = CHIMALI_AAGUID
                val credProtectPolicy = (options.extensions?.get("credProtect") as? Int) ?: DEFAULT_CRED_PROTECT_POLICY

                // Create the credential domain model
                val credential =
                    PasskeyCredential.create(
                        id = credentialId,
                        rpId = options.rp.id,
                        userId = options.user.id,
                        userName = options.user.name,
                        userDisplayName = options.user.displayName,
                        publicKey = publicKey,
                        privateKeyAlias = Fido2CryptoService.credentialAlias(credentialId),
                        aaguid = aaguid,
                        credentialId = credentialId.toByteArray(),
                        coseAlgorithm = options.selectedAlgId,
                        credProtectPolicy = credProtectPolicy,
                    )

                Outcome.Success(credential)
            }
        } catch (e: java.security.GeneralSecurityException) {
            Outcome.Error(DomainError.CryptoError(e.message ?: "Unknown error", e))
        }
    }

    /**
     * Updates relying party information in the repository.
     */
    private suspend fun updateRelyingParty(rp: PublicKeyCredentialRpEntity): Outcome<Unit, DomainError> {
        val existingRp = passkeyCredentialRepository.getRelyingParty(rp.id)
        val rpToSave =
            existingRp?.withCredentialCount(existingRp.credentialCount + 1)
                ?: RelyingParty.create(rp.id, rp.name, rp.icon)
        return passkeyCredentialRepository.saveRelyingParty(rpToSave)
    }

    /**
     * Creates an attestation object for the registration response.
     *
     * T145b: Uses "packed" self-attestation, signing authData||clientDataHash with the
     * HDK-derived ECDSA P-256 key via [Fido2CryptoService.sign]. Falls back to "none"
     * attestation if signing fails (e.g. master seed not yet available).
     */
    private suspend fun createAttestationObject(
        options: MakeCredentialOptions,
        credential: PasskeyCredential,
    ): AttestationObject {
        // Compute COSE public key once — used in both authDataBytes (signed) and AuthenticatorData (serialized).
        // They MUST be the same bytes; signing authDataBytes with a different key than
        // the COSE key embedded in it causes "Invalid data" on the server.
        val pubKeyCose = cborCodec.encodeCosePublicKeyFromJavaKey(credential.publicKey)

        // Build authenticatorData per WebAuthn §6.1
        val authDataBytes =
            run {
                val rpIdHash = clientDataHashService.rpIdHash(options.rp.id.value)
                val flags = createAuthenticatorFlags(options)
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
                rpIdHash = clientDataHashService.rpIdHash(options.rp.id.value),
                flags = createAuthenticatorFlags(options),
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
        val signatureResult =
            cryptoService.sign(
                credentialId = credential.id,
                data = authDataBytes + options.challenge,
                algId = options.selectedAlgId,
            )

        val (fmt, attStmt) =
            if (signatureResult is Outcome.Success) {
                val sig = signatureResult.data
                "packed" to
                    AttestationStatement.create(
                        alg = options.selectedAlgId,
                        fmt = "packed",
                        attCert = sig,
                        authData = authDataBytes,
                    )
            } else {
                // Graceful degradation: fall back to none-attestation if seed not yet available
                "none" to AttestationStatement.createNone()
            }

        return AttestationObject.create(
            fmt = fmt,
            authData = authData,
            attStmt = attStmt,
            clientData =
                ClientData.create(
                    type = "webauthn.create",
                    challenge = options.challenge,
                    origin = options.rp.id.value,
                ),
        )
    }

    private fun createAuthenticatorFlags(options: MakeCredentialOptions): ByteArray {
        var flags = 0x00
        flags = flags or FLAG_USER_PRESENT
        if (options.authenticatorSelection?.userVerification == UserVerificationRequirement.REQUIRED) {
            flags = flags or FLAG_USER_VERIFIED
        }
        flags = flags or FLAG_ATTESTED_CRED_DATA
        return byteArrayOf(flags.toByte())
    }
}

package com.chimali.fido2.domain.usecase

import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.domain.model.*
import com.chimali.fido2.domain.model.CredentialId
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.service.UserVerificationRequirement as ServiceVerificationRequirement
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.service.*
import com.chimali.fido2.domain.exception.Fido2Exception
import javax.inject.Inject

/**
 * Use case for registering new FIDO2 credentials.
 * Handles the complete credential registration flow with user verification.
 */
class RegisterCredentialUseCase @Inject constructor(
    private val credentialRepository: CredentialRepository,
    private val userVerificationService: UserVerificationService,
    private val cborCodec: CborCodec,
    private val cryptoService: Fido2CryptoService
) {

    /**
     * Registers a new credential with the authenticator.
     *
     * @param options The registration options containing all necessary parameters
     * @return Result containing MakeCredentialResult (Attestation + Passkey) on success
     */
    suspend operator fun invoke(options: MakeCredentialOptions): Result<MakeCredentialResult> {
        try {
            // Validate registration options
            validateRegistrationOptions(options)

            // Ensure relying party exists before creating consent records or credentials
            val rpResult = updateRelyingParty(options.rp)
            if (rpResult.isFailure) {
                return Result.failure(rpResult.exceptionOrNull() ?: Fido2Exception.RelyingPartyUpdateFailed("Failed to register relying party"))
            }

            // Check if user consent is required
            val consentRequired = userVerificationService.isUserVerificationRequired(
                rpId = options.rp.id,
                operationType = "registration",
                context = VerificationContext.CREDENTIAL_CREATION
            )

            // Get user consent if required
            if (consentRequired == ServiceVerificationRequirement.REQUIRED) {
                val consentResult = getUserConsentForRegistration(options)
                if (consentResult.isFailure) {
                    return Result.failure(consentResult.exceptionOrNull() ?: Fido2Exception.ConsentDenied("User consent denied"))
                }
            }

            // Verify user identity if required
            val verificationResult = performUserVerification(options)
            if (verificationResult.isFailure) {
                return Result.failure(verificationResult.exceptionOrNull() ?: Fido2Exception.UserVerificationFailed("User verification failed"))
            }

            // Validate credential creation with repository
            val validationResult = credentialRepository.validateCredentialCreation(
                rpId = options.rp.id,
                userId = String(options.user.id)
            )
            if (validationResult.isFailure) {
                return Result.failure(validationResult.exceptionOrNull() ?: Fido2Exception.CredentialCreationNotAllowed("Credential creation not allowed"))
            }

            // Generate the credential
            val credentialGenerationResult = generateCredential(options)
            if (credentialGenerationResult.isFailure) {
                return Result.failure(credentialGenerationResult.exceptionOrNull() ?: Fido2Exception.CredentialGenerationFailed("Credential generation failed"))
            }
            val credential = credentialGenerationResult.getOrThrow()

            // Store the credential
            val storageResult = credentialRepository.saveCredential(credential)
            if (storageResult.isFailure) {
                return Result.failure(storageResult.exceptionOrNull() ?: Fido2Exception.CredentialStorageFailed("Credential storage failed"))
            }

            // Create attestation object
            val attestationObject = createAttestationObject(
                options = options,
                credential = credential
            )

            return Result.success(MakeCredentialResult(attestationObject, credential))
        } catch (e: Exception) {
            return Result.failure(Fido2Exception.RegistrationFailed(e.message ?: "Unknown error", e))
        }
    }

    /**
     * Validates the registration options according to FIDO2 specifications.
     */
    private fun validateRegistrationOptions(options: MakeCredentialOptions) {
        // Validate RP entity
        options.rp.validate()

        // Validate user entity
        options.user.validate()

        // Validate cryptographic parameters
        options.pubKeyCredParams.validate()

        // Validate challenge
        require(options.challenge.isNotEmpty()) { "Challenge cannot be empty" }
        require(options.challenge.size <= 64) { "Challenge cannot exceed 64 bytes" }

        // Validate timeout
        val timeout = options.getSafeTimeout()
        require(timeout > 0) { "Timeout must be positive" }
        require(timeout <= 300000) { "Timeout cannot exceed 5 minutes" }

        // Validate credential lists
        options.allowCredentials?.let { allowList ->
            require(allowList.size <= 32) { "Allow credentials list cannot exceed 32 items" }
            allowList.forEach { it.validate() }
        }

        options.excludeCredentials?.let { excludeList ->
            require(excludeList.size <= 32) { "Exclude credentials list cannot exceed 32 items" }
            excludeList.forEach { it.validate() }
        }
    }

    /**
     * Gets user consent for credential registration.
     */
    private suspend fun getUserConsentForRegistration(
        options: MakeCredentialOptions
    ): Result<UserConsentRecord> {
        val consentRecord = UserConsentRecord.create(
            operationType = ConsentOperationType.REGISTRATION,
            rpId = options.rp.id,
            credentialId = null,
            biometricUsed = options.authenticatorSelection?.userVerification == UserVerificationRequirement.REQUIRED,
            pinUsed = options.authenticatorSelection?.userVerification == UserVerificationRequirement.REQUIRED,
            ipAddress = null, // Will be populated by actual implementation
            userAgent = null, // Will be populated by actual implementation
            deviceId = null // Will be populated by actual implementation
        )

        return userVerificationService.recordUserConsent(consentRecord).map { consentRecord }
    }

    /**
     * Performs user verification for credential registration.
     */
    private suspend fun performUserVerification(
        options: MakeCredentialOptions
    ): Result<Unit> {
        val verificationRequirement = options.authenticatorSelection?.userVerification
            ?: UserVerificationRequirement.PREFERRED

        if (verificationRequirement == UserVerificationRequirement.REQUIRED) {
            val availability = userVerificationService.getUserVerificationAvailability()
            if (availability.getBestAvailableMethod() == com.chimali.fido2.domain.service.VerificationMethod.NONE) {
                return Result.failure(Fido2Exception.NoVerificationMethodAvailable())
            }
        }

        return Result.success(Unit)
    }

    /**
     * Generates a new credential with cryptographic operations.
     */
    private suspend fun generateCredential(
        options: MakeCredentialOptions
    ): Result<PasskeyCredential> {
        try {
            // Generate random 32-byte credential ID (bytes + encoded string).
            val credentialId = PasskeyCredential.generateRandomId()  // returns CredentialId

            // Generate hardware-backed key pair via Fido2CryptoService
            val cryptoResult = cryptoService.generateCredentialKeyPair(
                credentialId = credentialId,
                algId = options.selectedAlgId
            )
            if (cryptoResult.isFailure) {
                return Result.failure(cryptoResult.exceptionOrNull() ?: Fido2Exception.KeyGenerationFailed("Key generation failed"))
            }
            
            // Retrieve the public key object for PasskeyCredential
            val publicKey = cryptoService.getPublicKey(credentialId)
                ?: return Result.failure(Fido2Exception.KeyNotFound("Generated key not found in KeyStore: ${credentialId.encoded}"))
            
            // Generate AAGUID for this authenticator
            val aaguid = generateAAGUID()

            // Create the credential domain model
            val credential = PasskeyCredential.create(
                id = credentialId.encoded,
                rpId = options.rp.id,
                userId = String(options.user.id),
                userName = options.user.name,
                userDisplayName = options.user.displayName,
                publicKey = publicKey,
                privateKeyAlias = Fido2CryptoService.credentialAlias(credentialId),
                aaguid = aaguid,
                credentialId = credentialId.toByteArray(),
                coseAlgorithm = options.selectedAlgId ?: Fido2CryptoService.COSE_ES256
            )

            return Result.success(credential)
        } catch (e: Exception) {
            return Result.failure(Fido2Exception.CredentialGenerationFailed(e.message ?: "Unknown error", e))
        }
    }




    /**
     * Returns the fixed AAGUID for the Chimali authenticator (version 1).
     * Must be identical to CHIMALI_AAGUID in Ctap2MakeCredentialHandler.
     */
    private fun generateAAGUID(): ByteArray = byteArrayOf(
        0x43, 0x48, 0x49, 0x4D, 0x41, 0x4C, 0x49, 0x00, // "CHIMALI\0"
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01  // ...version 1
    )


    /**
     * Updates relying party information in the repository.
     */
    private suspend fun updateRelyingParty(rp: PublicKeyCredentialRpEntity): Result<Unit> {
        val existingRp = credentialRepository.getRelyingParty(rp.id)
        val rpToSave = existingRp?.withCredentialCount(existingRp.credentialCount + 1)
            ?: RelyingParty.create(rp.id, rp.name, rp.icon)
        return credentialRepository.saveRelyingParty(rpToSave)
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
        credential: PasskeyCredential
    ): AttestationObject {
        // Build authenticatorData
        val authDataBytes = run {
            val rpIdHash   = hashRpId(options.rp.id)
            val flags      = createAuthenticatorFlags(options)
            val counter    = byteArrayOf(0, 0, 0, 0) // 4-byte big-endian sign count = 0
            val credIdLen  = byteArrayOf(
                (credential.credentialId.size shr 8).toByte(),
                (credential.credentialId.size and 0xFF).toByte()
            )
            val pubKeyCose = cborCodec.encodeCosePublicKeyFromJavaKey(credential.publicKey)
            // AT flag (0x40) in flags signals attested credential data is present
            rpIdHash + flags + counter + credential.aaguid + credIdLen +
                credential.credentialId + pubKeyCose
        }

        val authData = AuthenticatorData.create(
            rpIdHash = hashRpId(options.rp.id),
            flags = createAuthenticatorFlags(options),
            counter = 0L,
            aaguid = credential.aaguid,
            credentialId = credential.credentialId,
            publicKey = cborCodec.encodeCosePublicKeyFromJavaKey(credential.publicKey)
        )

        // For CTAP2, the host already computed the clientDataHash and passed it in options.challenge.
        val clientDataHash = options.challenge

        // Sign authData || clientDataHash with the HDK-derived key (packed self-attestation)
        val signatureResult = cryptoService.sign(
            credentialId = CredentialId.fromString(credential.id),
            data         = authDataBytes + clientDataHash
        )

        val (fmt, attStmt) = if (signatureResult.isSuccess) {
            val sig = signatureResult.getOrThrow()
            "packed" to AttestationStatement.create(
                alg     = "ES256",
                fmt     = "packed",
                attCert = sig,
                authData = authDataBytes
            )
        } else {
            // Graceful degradation: fall back to none-attestation if seed not yet available
            "none" to AttestationStatement.createNone()
        }

        val clientData = ClientData.create(
            type      = "webauthn.create",
            challenge = options.challenge,
            origin    = options.rp.id
        )

        return AttestationObject.create(
            fmt        = fmt,
            authData   = authData,
            attStmt    = attStmt,
            clientData = clientData
        )
    }

    /**
     * Hashes the RP ID for authenticator data.
     */
    private fun hashRpId(rpId: String): ByteArray {
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(rpId.toByteArray())
    }

    /**
     * Creates authenticator flags byte.
     */
    private fun createAuthenticatorFlags(options: MakeCredentialOptions): ByteArray {
        var flags = 0x00

        // Set user present flag (UP)
        flags = flags or 0x01

        // Set user verified flag (UV) if verification is required
        if (options.authenticatorSelection?.userVerification == UserVerificationRequirement.REQUIRED) {
            flags = flags or 0x04
        }

        // Set attested credential data included flag (AT)
        flags = flags or 0x40

        return byteArrayOf(flags.toByte())
    }
}

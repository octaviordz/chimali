package com.chimali.fido2.domain.usecase

import com.chimali.fido2.domain.model.*
import com.chimali.fido2.domain.model.UserVerificationRequirement
import com.chimali.fido2.domain.service.UserVerificationRequirement as ServiceVerificationRequirement
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.service.*
import com.chimali.fido2.domain.exception.Fido2Exception
import kotlinx.coroutines.flow.Flow
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject

/**
 * Use case for registering new FIDO2 credentials.
 * Handles the complete credential registration flow with user verification.
 */
class RegisterCredentialUseCase @Inject constructor(
    private val credentialRepository: CredentialRepository,
    private val userVerificationService: UserVerificationService,
    private val fido2Authenticator: Fido2Authenticator,
    private val cborCodec: com.chimali.fido2.data.crypto.CborCodec
) {
    
    /**
     * Registers a new credential with the authenticator.
     * 
     * @param options The registration options containing all necessary parameters
     * @return Result containing AttestationObject on success, error on failure
     */
    suspend operator fun invoke(options: MakeCredentialOptions): Result<AttestationObject> {
        return try {
            // Validate registration options
            validateRegistrationOptions(options)
            
            // Ensure relying party exists before creating consent records or credentials
            val rpResult = updateRelyingParty(options.rp)
            if (rpResult.isFailure) {
                return Result.failure(Fido2Exception.CredentialStorageFailed("Failed to register relying party: ${rpResult.exceptionOrNull()?.message}"))
            }
            
            // Check if user consent is required
            val consentRequired = userVerificationService.isUserVerificationRequired(
                rpId = options.rp.id,
                operationType = "registration",
                context = VerificationContext.CREDENTIAL_CREATION
            )
            
            // Get user consent if required
            if (consentRequired == com.chimali.fido2.domain.service.UserVerificationRequirement.REQUIRED) {
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
            
            // Store the credential
            val storageResult = credentialRepository.saveCredential(credentialGenerationResult.getOrThrow())
            if (storageResult.isFailure) {
                return Result.failure(storageResult.exceptionOrNull() ?: Fido2Exception.CredentialStorageFailed("Credential storage failed"))
            }
            
            // Create attestation object
            val attestationObject = createAttestationObject(
                options = options,
                credential = credentialGenerationResult.getOrThrow()
            )
            
            Result.success(attestationObject)
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.RegistrationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Validates the registration options according to FIDO2 specifications.
     */
    private suspend fun validateRegistrationOptions(options: MakeCredentialOptions) {
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
        
        return when (verificationRequirement) {
            UserVerificationRequirement.REQUIRED -> {
                val availability = userVerificationService.getUserVerificationAvailability()
                when {
                    availability.biometricAvailable -> {
                        val result = userVerificationService.verifyBiometric(
                            prompt = "Verify your identity to register new passkey",
                            rpId = options.rp.id
                        )
                        if (result.isSuccess) {
                            Result.success(Unit)
                        } else {
                            Result.failure(Fido2Exception.UserVerificationFailed(result.exceptionOrNull()?.message ?: "Verification failed"))
                        }
                    }
                    availability.pinAvailable -> {
                        val result = userVerificationService.verifyPin(
                            prompt = "Enter your PIN to register new passkey",
                            rpId = options.rp.id
                        )
                        if (result.isSuccess) {
                            Result.success(Unit)
                        } else {
                            Result.failure(Fido2Exception.UserVerificationFailed(result.exceptionOrNull()?.message ?: "Verification failed"))
                        }
                    }
                    else -> {
                        Result.failure(Fido2Exception.NoVerificationMethodAvailable())
                    }
                }
            }
            UserVerificationRequirement.PREFERRED -> {
                // Try biometric first, then fallback to PIN
                val availability = userVerificationService.getUserVerificationAvailability()
                if (availability.biometricAvailable) {
                    val result = userVerificationService.verifyBiometric(
                        prompt = "Verify your identity to register new passkey",
                        rpId = options.rp.id
                    )
                    if (result.isSuccess) {
                        Result.success(Unit)
                    } else {
                        // Fallback to PIN
                        if (availability.pinAvailable) {
                            val pinResult = userVerificationService.verifyPin(
                                prompt = "Enter your PIN to register new passkey",
                                rpId = options.rp.id
                            )
                            if (pinResult.isSuccess) {
                                Result.success(Unit)
                            } else {
                                Result.failure(Fido2Exception.UserVerificationFailed(pinResult.exceptionOrNull()?.message ?: "Verification failed"))
                            }
                        } else {
                            Result.failure(Fido2Exception.NoVerificationMethodAvailable())
                        }
                    }
                } else if (availability.pinAvailable) {
                    val result = userVerificationService.verifyPin(
                        prompt = "Enter your PIN to register new passkey",
                        rpId = options.rp.id
                    )
                    if (result.isSuccess) {
                        Result.success(Unit)
                    } else {
                        Result.failure(Fido2Exception.UserVerificationFailed(result.exceptionOrNull()?.message ?: "Verification failed"))
                    }
                } else {
                    Result.failure(Fido2Exception.NoVerificationMethodAvailable())
                }
            }
            UserVerificationRequirement.DISCOURAGED -> {
                // User verification is discouraged or not required
                Result.success(Unit)
            }
        }
    }
    
    /**
     * Generates a new credential with cryptographic operations.
     */
    private suspend fun generateCredential(
        options: MakeCredentialOptions
    ): Result<PasskeyCredential> {
        return try {
            // Generate credential ID
            val credentialId = generateCredentialId()
            
            // Generate key pair
            val keyPairResult = generateKeyPair(options.pubKeyCredParams)
            if (keyPairResult.isFailure) {
                return Result.failure(keyPairResult.exceptionOrNull() ?: Fido2Exception.KeyGenerationFailed("Key generation failed"))
            }
            
            val keyPair = keyPairResult.getOrThrow()
            
            // Generate AAGUID for this authenticator
            val aaguid = generateAAGUID()
            
            // Create the credential
            val credential = PasskeyCredential.create(
                id = credentialId,
                rpId = options.rp.id,
                userId = String(options.user.id),
                userName = options.user.name,
                userDisplayName = options.user.displayName,
                publicKey = keyPair.public,
                privateKeyAlias = "fido2_credential_${credentialId}",
                aaguid = aaguid,
                credentialId = credentialId.toByteArray()
            )
            
            Result.success(credential)
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.CredentialGenerationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Generates a unique credential ID.
     */
    private fun generateCredentialId(): String {
        return "cred_${System.currentTimeMillis()}_${SecureRandom().nextInt(10000)}"
    }
    
    /**
     * Generates a cryptographic key pair based on parameters.
     */
    private suspend fun generateKeyPair(
        params: PublicKeyCredentialParameters
    ): Result<java.security.KeyPair> {
        return try {
            when (params.algorithm) {
                "ES256" -> generateECKeyPair("secp256r1")
                "RS256" -> generateRSAKeyPair(2048)
                "EdDSA" -> generateEdDSAKeyPair()
                else -> Result.failure(Fido2Exception.UnsupportedAlgorithm(params.algorithm))
            }
        } catch (e: Exception) {
            Result.failure(Fido2Exception.KeyGenerationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Generates an elliptic curve key pair.
     */
    private fun generateECKeyPair(curve: String): Result<java.security.KeyPair> {
        return try {
            val curveName = when (curve) {
                "secp256r1" -> "secp256r1"
                "secp384r1" -> "secp384r1"
                "secp521r1" -> "secp521r1"
                else         -> return Result.failure(Fido2Exception.UnsupportedCurve(curve))
            }
            val keyPairGenerator = java.security.KeyPairGenerator.getInstance("EC")
            keyPairGenerator.initialize(java.security.spec.ECGenParameterSpec(curveName))
            Result.success(keyPairGenerator.generateKeyPair())
        } catch (e: Exception) {
            Result.failure(Fido2Exception.KeyGenerationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Generates an RSA key pair.
     */
    private fun generateRSAKeyPair(keySize: Int): Result<java.security.KeyPair> {
        return try {
            val keyPairGenerator = java.security.KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(java.security.spec.RSAKeyGenParameterSpec(
                keySize,
                java.math.BigInteger.valueOf(65537)
            ))
            Result.success(keyPairGenerator.generateKeyPair())
        } catch (e: Exception) {
            Result.failure(Fido2Exception.KeyGenerationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Generates an EdDSA key pair.
     */
    private fun generateEdDSAKeyPair(): Result<java.security.KeyPair> {
        return try {
            // Note: EdDSA support may require additional libraries
            // For now, fallback to EC
            generateECKeyPair("secp256r1")
        } catch (e: Exception) {
            Result.failure(Fido2Exception.KeyGenerationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Generates an AAGUID for this authenticator.
     */
    private fun generateAAGUID(): ByteArray {
        // Generate a random AAGUID for this authenticator
        // In a real implementation, this would be device-specific
        val aaguid = ByteArray(16)
        SecureRandom().nextBytes(aaguid)
        return aaguid
    }
    
    /**
     * Updates relying party information in the repository.
     */
    private suspend fun updateRelyingParty(rp: PublicKeyCredentialRpEntity): Result<Unit> {
        val existingRp = credentialRepository.getRelyingParty(rp.id)
        val rpToSave = if (existingRp != null) {
            existingRp.withCredentialCount(existingRp.credentialCount + 1)
        } else {
            RelyingParty.create(rp.id, rp.name, rp.icon)
        }
        return credentialRepository.saveRelyingParty(rpToSave)
    }
    
    /**
     * Creates an attestation object for the registration response.
     */
    private suspend fun createAttestationObject(
        options: MakeCredentialOptions,
        credential: PasskeyCredential
    ): AttestationObject {
        // Create authenticator data
        val authData = AuthenticatorData.create(
            rpIdHash = hashRpId(options.rp.id),
            flags = createAuthenticatorFlags(options),
            counter = 0L, // New credential starts with counter 0
            aaguid = credential.aaguid,
            credentialId = credential.credentialId,
            // FIDO2 spec requires a CBOR-encoded COSE_Key, NOT raw DER
            publicKey = cborCodec.encodeCosePublicKeyFromJavaKey(credential.publicKey)
        )
        
        // Create client data
        val clientData = ClientData.create(
            type = "webauthn.create",
            challenge = options.challenge,
            origin = options.rp.id
        )
        
        // Create attestation statement (self-attested for privacy)
        val attStmt = AttestationStatement.createNone()
        
        return AttestationObject.create(
            fmt = "none",
            authData = authData,
            attStmt = attStmt,
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
        
        // Set user present flag
        flags = flags or 0x01
        
        // Set user verified flag if verification is required
        if (options.authenticatorSelection?.userVerification == UserVerificationRequirement.REQUIRED) {
            flags = flags or 0x04
        }
        
        // Set user verification flag if verification is required
        if (options.authenticatorSelection?.userVerification == UserVerificationRequirement.REQUIRED) {
            flags = flags or 0x04
        }
        
        return byteArrayOf(flags.toByte())
    }
}

package com.chimali.fido2.ctap2

import com.chimali.fido2.domain.model.*
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.service.Fido2Authenticator
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.data.crypto.PostQuantumCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.security.KeyPair
import java.security.SecureRandom
import java.time.Instant
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for CTAP2 MakeCredential commands.
 * Processes credential creation requests and generates appropriate attestation responses.
 */
@Singleton
class Ctap2MakeCredentialHandler @Inject constructor(
    private val userVerificationService: UserVerificationService,
    private val fido2Authenticator: Fido2Authenticator,
    private val cborCodec: CborCodec,
    private val postQuantumCrypto: PostQuantumCrypto
) {
    
    companion object {
        private const val CTAP2_MAKE_CREDENTIAL = 0x01
        private const val CTAP2_STATUS_SUCCESS = 0x00
        private const val CTAP2_STATUS_ERROR_INVALID_CBOR = 0x12
        private const val CTAP2_STATUS_ERROR_INVALID_PARAMETER = 0x13
        private const val CTAP2_STATUS_ERROR_MISSING_PARAMETER = 0x14
        private const val CTAP2_STATUS_ERROR_UNSUPPORTED_EXTENSION = 0x16
        private const val CTAP2_STATUS_ERROR_CREDENTIAL_EXCLUDED = 0x19
        private const val CTAP2_STATUS_ERROR_PROCESSING = 0x21
        private const val CTAP2_STATUS_ERROR_INVALID_CREDENTIAL = 0x22
        private const val CTAP2_STATUS_ERROR_USER_ACTION_PENDING = 0x23
        private const val CTAP2_STATUS_ERROR_OPERATION_PENDING = 0x24
        private const val CTAP2_STATUS_ERROR_NO_OPERATIONS = 0x25
        private const val CTAP2_STATUS_ERROR_UNSUPPORTED_ALGORITHM = 0x26
        private const val CTAP2_STATUS_ERROR_OPERATION_DENIED = 0x27
        private const val CTAP2_STATUS_ERROR_KEY_STORE_FULL = 0x28
        private const val CTAP2_STATUS_ERROR_NOT_BUSY = 0x2C
        private const val CTAP2_STATUS_ERROR_NO_OPERATION_PENDING = 0x2D
        private const val CTAP2_STATUS_ERROR_UNSUPPORTED_OPTION = 0x2E
        private const val CTAP2_STATUS_ERROR_INVALID_OPTION = 0x2F
        private const val CTAP2_STATUS_ERROR_KEEPALIVE_CANCEL = 0x34
        private const val CTAP2_STATUS_ERROR_NO_CREDENTIALS = 0x2E
        private const val CTAP2_STATUS_ERROR_USER_ACTION_TIMEOUT = 0x35
        private const val CTAP2_STATUS_ERROR_NOT_ALLOWED = 0x36
        private const val CTAP2_STATUS_ERROR_PIN_INVALID = 0x31
        private const val CTAP2_STATUS_ERROR_PIN_BLOCKED = 0x32
        private const val CTAP2_STATUS_ERROR_PIN_AUTH_INVALID = 0x33
        private const val CTAP2_STATUS_ERROR_PIN_REQUIRED = 0x36
        private const val CTAP2_STATUS_ERROR_PIN_POLICY_VIOLATION = 0x37
        private const val CTAP2_STATUS_ERROR_PIN_TOO_SHORT = 0x38
        private const val CTAP2_STATUS_ERROR_PIN_TOO_LONG = 0x39
        
        private const val ALGORITHM_ES256 = -7 // COSE algorithm ID for ES256
        private const val ALGORITHM_RS256 = -257 // COSE algorithm ID for RS256
        private const val ALGORITHM_EDDSA = -8 // COSE algorithm ID for EdDSA
        
        private const val CREDENTIAL_TYPE_PUBLIC_KEY = "public-key"
        private const val ATTESTATION_FORMAT_NONE = "none"
        private const val ATTESTATION_FORMAT_PACKED = "packed"
        private const val ATTESTATION_FORMAT_FIDO_U2F = "fido-u2f"
        private const val ATTESTATION_FORMAT_TPM = "tpm"
        private const val ATTESTATION_FORMAT_ANDROID_KEY = "android-key"
        private const val ATTESTATION_FORMAT_ANDROID_SAFETYNET = "android-safetynet"
        
        private const val AUTHENTICATOR_DATA_FLAG_USER_PRESENT = 0x01
        private const val AUTHENTICATOR_DATA_FLAG_USER_VERIFIED = 0x04
        private const val AUTHENTICATOR_DATA_FLAG_ATTESTED = 0x40
        private const val AUTHENTICATOR_DATA_FLAG_EXTENSION_DATA_INCLUDED = 0x80
        
        private const val MAX_CREDENTIAL_COUNT = 10
        private const val MAX_CREDENTIAL_ID_LENGTH = 1023
        private const val MAX_RP_ID_LENGTH = 255
    }
    
    private val secureRandom = SecureRandom()
    
    /**
     * Handles a CTAP2 MakeCredential command.
     */
    suspend fun handleMakeCredential(commandData: ByteArray): Result<Ctap2Response> {
        return try {
            // Parse command parameters
            val parameters = parseMakeCredentialParameters(commandData)
            if (parameters == null) {
                return Result.failure(Fido2Exception.InvalidCborData("Invalid MakeCredential parameters"))
            }
            
            // Validate parameters
            val validationResult = validateMakeCredentialParameters(parameters)
            if (validationResult.isFailure) {
                return validationResult
            }
            
            // Check user verification requirements
            val verificationResult = checkUserVerification(parameters)
            if (verificationResult.isFailure) {
                return verificationResult
            }
            
            // Generate credential
            val credentialResult = generateCredential(parameters)
            if (credentialResult.isFailure) {
                return credentialResult
            }
            
            // Create attestation object
            val attestationResult = createAttestationObject(parameters, credentialResult.getOrThrow())
            if (attestationResult.isFailure) {
                return attestationResult
            }
            
            // Store credential
            val storageResult = storeCredential(credentialResult.getOrThrow())
            if (storageResult.isFailure) {
                return storageResult
            }
            
            // Create success response
            val responseData = createMakeCredentialResponse(attestationResult.getOrThrow())
            Result.success(Ctap2Response(
                commandId = CTAP2_MAKE_CREDENTIAL,
                status = CTAP2_STATUS_SUCCESS,
                data = responseData,
                isSuccess = true,
                errorMessage = null
            ))
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.MakeCredentialFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Parses MakeCredential command parameters from CBOR data.
     */
    private fun parseMakeCredentialParameters(data: ByteArray): MakeCredentialParameters? {
        return try {
            val cborData = cborCodec.decode(data)
            if (cborData !is Map<*, *>) {
                return null
            }
            
            val map = cborData as Map<*, *>
            
            // Extract required parameters
            val clientDataHash = map["clientDataHash"] as? ByteArray
            val rp = map["rp"] as? ByteArray
            val user = map["user"] as? ByteArray
            val pubKeyCredParams = map["pubKeyCredParams"] as? List<*>
            
            if (clientDataHash == null || rp == null || user == null || pubKeyCredParams == null) {
                return null
            }
            
            // Extract optional parameters
            val options = map["options"] as? Map<*, *>
            val extensions = map["extensions"] as? Map<*, *>
            
            MakeCredentialParameters(
                clientDataHash = clientDataHash,
                rp = rp,
                user = user,
                pubKeyCredParams = pubKeyCredParams,
                options = options,
                extensions = extensions
            )
            
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Validates MakeCredential parameters.
     */
    private suspend fun validateMakeCredentialParameters(parameters: MakeCredentialParameters): Result<Unit> {
        return try {
            // Validate client data hash
            if (parameters.clientDataHash.size != 32) {
                return Result.failure(Fido2Exception.InvalidClientDataHash("Client data hash must be 32 bytes"))
            }
            
            // Validate RP
            val rpData = cborCodec.decode(parameters.rp) as? Map<*, *>
            val rpId = rpData?.get("id") as? String
            if (rpId == null || rpId.length > MAX_RP_ID_LENGTH) {
                return Result.failure(Fido2Exception.InvalidRelyingParty("Invalid RP ID"))
            }
            
            // Validate user
            val userData = cborCodec.decode(parameters.user) as? Map<*, *>
            val userId = userData?.get("id") as? ByteArray
            if (userId == null || userId.size > 64) {
                return Result.failure(Fido2Exception.InvalidUser("Invalid user ID"))
            }
            
            // Validate public key credential parameters
            if (parameters.pubKeyCredParams.isEmpty()) {
                return Result.failure(Fido2Exception.InvalidPublicKeyParameters("No public key parameters provided"))
            }
            
            // Validate algorithms
            for (param in parameters.pubKeyCredParams) {
                val paramData = param as? Map<*, *>
                val alg = paramData?.get("alg") as? Int
                if (alg == null || !isSupportedAlgorithm(alg)) {
                    return Result.failure(Fido2Exception.UnsupportedAlgorithm("Unsupported algorithm: $alg"))
                }
            }
            
            // Validate options
            parameters.options?.let { options ->
                val residentKey = options["rk"] as? Boolean
                val userVerification = options["uv"] as? Boolean
                
                // Check if user verification is required but not available
                if (userVerification == true) {
                    val availability = userVerificationService.getUserVerificationAvailability()
                    if (!availability.hasAnyVerificationMethod()) {
                        return Result.failure(Fido2Exception.UserVerificationNotAvailable())
                    }
                }
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.ParameterValidationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Checks if algorithm is supported.
     */
    private fun isSupportedAlgorithm(algorithm: Int): Boolean {
        return algorithm in listOf(ALGORITHM_ES256, ALGORITHM_RS256, ALGORITHM_EDDSA)
    }
    
    /**
     * Checks user verification requirements.
     */
    private suspend fun checkUserVerification(parameters: MakeCredentialParameters): Result<Unit> {
        val userVerificationRequired = parameters.options?.get("uv") as? Boolean ?: false
        
        if (userVerificationRequired) {
            val availability = userVerificationService.getUserVerificationAvailability()
            if (!availability.hasAnyVerificationMethod()) {
                return Result.failure(Fido2Exception.UserVerificationNotAvailable())
            }
            
            // Perform user verification
            val verificationResult = when {
                availability.biometricAvailable -> {
                    userVerificationService.verifyBiometric(
                        prompt = "Verify your identity to create new passkey",
                        rpId = String(parameters.rp)
                    )
                }
                availability.pinAvailable -> {
                    userVerificationService.verifyPin(
                        prompt = "Enter your PIN to create new passkey",
                        rpId = String(parameters.rp)
                    )
                }
                else -> {
                    Result.failure(Fido2Exception.NoVerificationMethodAvailable())
                }
            }
            
            if (verificationResult.isFailure) {
                return Result.failure(Fido2Exception.UserVerificationFailed())
            }
        }
        
        return Result.success(Unit)
    }
    
    /**
     * Generates a new credential.
     */
    private suspend fun generateCredential(parameters: MakeCredentialParameters): Result<GeneratedCredential> {
        return try {
            // Select algorithm (prefer ES256)
            val selectedAlgorithm = selectAlgorithm(parameters.pubKeyCredParams)
            
            // Generate key pair
            val keyPairResult = when (selectedAlgorithm) {
                ALGORITHM_ES256 -> generateEcKeyPair()
                ALGORITHM_RS256 -> generateRsaKeyPair()
                ALGORITHM_EDDSA -> generateEdDsaKeyPair()
                else -> Result.failure(Fido2Exception.UnsupportedAlgorithm("Algorithm: $selectedAlgorithm"))
            }
            
            if (keyPairResult.isFailure) {
                return keyPairResult
            }
            
            val keyPair = keyPairResult.getOrThrow()
            
            // Generate credential ID
            val credentialId = generateCredentialId()
            
            // Generate AAGUID
            val aaguid = generateAaguid()
            
            // Create credential
            val credential = GeneratedCredential(
                keyPair = keyPair,
                credentialId = credentialId,
                aaguid = aaguid,
                algorithm = selectedAlgorithm,
                createdAt = Instant.now()
            )
            
            Result.success(credential)
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.CredentialGenerationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Selects the best algorithm from the provided parameters.
     */
    private fun selectAlgorithm(pubKeyCredParams: List<*>): Int {
        // Prefer ES256, then RS256, then EdDSA
        val algorithms = pubKeyCredParams.mapNotNull { param ->
            (param as? Map<*, *>)?.get("alg") as? Int
        }
        
        return when {
            algorithms.contains(ALGORITHM_ES256) -> ALGORITHM_ES256
            algorithms.contains(ALGORITHM_RS256) -> ALGORITHM_RS256
            algorithms.contains(ALGORITHM_EDDSA) -> ALGORITHM_EDDSA
            else -> ALGORITHM_ES256 // Default to ES256
        }
    }
    
    /**
     * Generates EC key pair.
     */
    private fun generateEcKeyPair(): Result<KeyPair> {
        return try {
            val keyPairGenerator = java.security.KeyPairGenerator.getInstance("EC")
            keyPairGenerator.initialize(256)
            Result.success(keyPairGenerator.generateKeyPair())
        } catch (e: Exception) {
            Result.failure(Fido2Exception.KeyGenerationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Generates RSA key pair.
     */
    private fun generateRsaKeyPair(): Result<KeyPair> {
        return try {
            val keyPairGenerator = java.security.KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(2048)
            Result.success(keyPairGenerator.generateKeyPair())
        } catch (e: Exception) {
            Result.failure(Fido2Exception.KeyGenerationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Generates EdDSA key pair.
     */
    private fun generateEdDsaKeyPair(): Result<KeyPair> {
        return try {
            // For now, fallback to EC as EdDSA support may be limited
            generateEcKeyPair()
        } catch (e: Exception) {
            Result.failure(Fido2Exception.KeyGenerationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Generates a unique credential ID.
     */
    private fun generateCredentialId(): ByteArray {
        val credentialId = ByteArray(16)
        secureRandom.nextBytes(credentialId)
        return credentialId
    }
    
    /**
     * Generates AAGUID for this authenticator.
     */
    private fun generateAaguid(): ByteArray {
        // Generate a random AAGUID for this authenticator
        // In production, this should be device-specific
        val aaguid = ByteArray(16)
        secureRandom.nextBytes(aaguid)
        return aaguid
    }
    
    /**
     * Creates an attestation object.
     */
    private suspend fun createAttestationObject(
        parameters: MakeCredentialParameters,
        credential: GeneratedCredential
    ): Result<AttestationObject> {
        return try {
            // Create authenticator data
            val authenticatorData = createAuthenticatorData(parameters, credential)
            
            // Create client data
            val clientData = createClientData(parameters)
            
            // Determine attestation format
            val attestationFormat = determineAttestationFormat(parameters)
            
            // Create attestation statement
            val attestationStatement = createAttestationStatement(attestationFormat, credential)
            
            AttestationObject(
                fmt = attestationFormat,
                authData = authenticatorData,
                attStmt = attestationStatement,
                clientData = clientData
            )
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.AttestationCreationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Creates authenticator data.
     */
    private fun createAuthenticatorData(
        parameters: MakeCredentialParameters,
        credential: GeneratedCredential
    ): AuthenticatorData {
        val rpData = cborCodec.decode(parameters.rp) as? Map<*, *>
        val rpIdHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest((rpData?.get("id") as? String ?: "").toByteArray())
        
        var flags = AUTHENTICATOR_DATA_FLAG_USER_PRESENT
        
        // Add user verified flag if verification was performed
        if (parameters.options?.get("uv") as? Boolean == true) {
            flags = flags or AUTHENTICATOR_DATA_FLAG_USER_VERIFIED
        }
        
        // Add attested flag (for self-attestation, this should be false)
        // flags = flags or AUTHENTICATOR_DATA_FLAG_ATTESTED
        
        return AuthenticatorData(
            rpIdHash = rpIdHash,
            flags = flags.toByte(),
            signCount = 0L,
            aaguid = credential.aaguid,
            credentialId = credential.credentialId,
            credentialPublicKey = credential.keyPair.public.encoded
        )
    }
    
    /**
     * Creates client data.
     */
    private fun createClientData(parameters: MakeCredentialParameters): ClientData {
        val rpData = cborCodec.decode(parameters.rp) as? Map<*, *>
        val rpId = rpData?.get("id") as? String ?: ""
        
        return ClientData(
            type = "webauthn.create",
            challenge = Base64.getEncoder().encodeToString(parameters.clientDataHash),
            origin = rpId,
            crossOrigin = null
        )
    }
    
    /**
     * Determines attestation format.
     */
    private fun determineAttestationFormat(parameters: MakeCredentialParameters): String {
        // Check if attestation is preferred
        val preferDirectAttestation = parameters.options?.get("preferDirectAttestation") as? Boolean ?: false
        
        return when {
            preferDirectAttestation -> ATTESTATION_FORMAT_PACKED
            else -> ATTESTATION_FORMAT_NONE // Self-attestation for privacy
        }
    }
    
    /**
     * Creates attestation statement.
     */
    private fun createAttestationStatement(
        format: String,
        credential: GeneratedCredential
    ): AttestationStatement {
        return when (format) {
            ATTESTATION_FORMAT_NONE -> createNoneAttestationStatement()
            ATTESTATION_FORMAT_PACKED -> createPackedAttestationStatement(credential)
            else -> createNoneAttestationStatement()
        }
    }
    
    /**
     * Creates a "none" attestation statement.
     */
    private fun createNoneAttestationStatement(): AttestationStatement {
        return AttestationStatement(
            alg = ALGORITHM_ES256,
            sig = ByteArray(0), // No signature for "none"
            x5c = emptyList() // No certificate chain
            attestationType = "none"
        )
    }
    
    /**
     * Creates a "packed" attestation statement.
     */
    private fun createPackedAttestationStatement(credential: GeneratedCredential): AttestationStatement {
        // For self-attestation, we would sign with the generated key
        // This is a simplified implementation
        return AttestationStatement(
            alg = credential.algorithm,
            sig = ByteArray(0), // Would be actual signature in production
            x5c = emptyList(), // Would be certificate chain in production
            attestationType = "packed"
        )
    }
    
    /**
     * Stores the generated credential.
     */
    private suspend fun storeCredential(credential: GeneratedCredential): Result<Unit> {
        return try {
            // Create domain model
            val passkeyCredential = PasskeyCredential.create(
                id = Base64.getEncoder().encodeToString(credential.credentialId),
                rpId = "", // Would be extracted from parameters
                userId = "", // Would be extracted from parameters
                userName = "", // Would be extracted from parameters
                userDisplayName = "", // Would be extracted from parameters
                publicKey = credential.keyPair.public,
                privateKeyAlias = "fido2_${Base64.getEncoder().encodeToString(credential.credentialId)}",
                aaguid = credential.aaguid,
                credentialId = credential.credentialId
            )
            
            // Store using authenticator
            val result = fido2Authenticator.makeCredential(
                MakeCredentialOptions.create(
                    rp = PublicKeyCredentialRpEntity.create("", ""), // Would be actual RP
                    user = PublicKeyCredentialUserEntity.create(ByteArray(0), "", ""), // Would be actual user
                    challenge = ByteArray(0), // Would be actual challenge
                    pubKeyCredParams = listOf(PublicKeyCredentialParameters.createES256P256())
                )
            )
            
            if (result.isFailure) {
                return Result.failure(result.exceptionOrNull() ?: Fido2Exception.CredentialStorageFailed())
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.CredentialStorageFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Creates MakeCredential response data.
     */
    private fun createMakeCredentialResponse(attestationObject: AttestationObject): ByteArray {
        // Serialize attestation object to CBOR
        return cborCodec.encode(mapOf(
            "fmt" to attestationObject.fmt,
            "authData" to attestationObject.authData.toByteArray(),
            "attStmt" to attestationObject.attStmt.toMap(),
            "clientData" to attestationObject.clientData.toJson().toByteArray()
        ))
    }
    
    /**
     * Creates error response.
     */
    fun createErrorResponse(status: Int, errorMessage: String? = null): Ctap2Response {
        return Ctap2Response(
            commandId = CTAP2_MAKE_CREDENTIAL,
            status = status,
            data = null,
            isSuccess = false,
            errorMessage = errorMessage ?: getStatusMessage(status)
        )
    }
    
    /**
     * Gets human-readable message for status code.
     */
    private fun getStatusMessage(status: Int): String {
        return when (status) {
            CTAP2_STATUS_SUCCESS -> "Success"
            CTAP2_STATUS_ERROR_INVALID_CBOR -> "Invalid CBOR"
            CTAP2_STATUS_ERROR_INVALID_PARAMETER -> "Invalid parameter"
            CTAP2_STATUS_ERROR_MISSING_PARAMETER -> "Missing parameter"
            CTAP2_STATUS_ERROR_UNSUPPORTED_EXTENSION -> "Unsupported extension"
            CTAP2_STATUS_ERROR_CREDENTIAL_EXCLUDED -> "Credential excluded"
            CTAP2_STATUS_ERROR_PROCESSING -> "Processing error"
            CTAP2_STATUS_ERROR_INVALID_CREDENTIAL -> "Invalid credential"
            CTAP2_STATUS_ERROR_USER_ACTION_PENDING -> "User action pending"
            CTAP2_STATUS_ERROR_OPERATION_PENDING -> "Operation pending"
            CTAP2_STATUS_ERROR_NO_OPERATIONS -> "No operations"
            CTAP2_STATUS_ERROR_UNSUPPORTED_ALGORITHM -> "Unsupported algorithm"
            CTAP2_STATUS_ERROR_OPERATION_DENIED -> "Operation denied"
            CTAP2_STATUS_ERROR_KEY_STORE_FULL -> "Key store full"
            CTAP2_STATUS_ERROR_NOT_BUSY -> "Not busy"
            CTAP2_STATUS_ERROR_NO_OPERATION_PENDING -> "No operation pending"
            CTAP2_STATUS_ERROR_UNSUPPORTED_OPTION -> "Unsupported option"
            CTAP2_STATUS_ERROR_INVALID_OPTION -> "Invalid option"
            CTAP2_STATUS_ERROR_KEEPALIVE_CANCEL -> "Keepalive cancel"
            CTAP2_STATUS_ERROR_NO_CREDENTIALS -> "No credentials"
            CTAP2_STATUS_ERROR_USER_ACTION_TIMEOUT -> "User action timeout"
            CTAP2_STATUS_ERROR_NOT_ALLOWED -> "Not allowed"
            CTAP2_STATUS_ERROR_PIN_INVALID -> "PIN invalid"
            CTAP2_STATUS_ERROR_PIN_BLOCKED -> "PIN blocked"
            CTAP2_STATUS_ERROR_PIN_AUTH_INVALID -> "PIN auth invalid"
            CTAP2_STATUS_ERROR_PIN_REQUIRED -> "PIN required"
            CTAP2_STATUS_ERROR_PIN_POLICY_VIOLATION -> "PIN policy violation"
            CTAP2_STATUS_ERROR_PIN_TOO_SHORT -> "PIN too short"
            CTAP2_STATUS_ERROR_PIN_TOO_LONG -> "PIN too long"
            else -> "Unknown status: 0x${status.toString(16).uppercase()}"
        }
    }
}

/**
 * Data class representing MakeCredential parameters.
 */
data class MakeCredentialParameters(
    val clientDataHash: ByteArray,
    val rp: ByteArray,
    val user: ByteArray,
    val pubKeyCredParams: List<*>,
    val options: Map<*, *>?,
    val extensions: Map<*, *>?
)

/**
 * Data class representing a generated credential.
 */
data class GeneratedCredential(
    val keyPair: KeyPair,
    val credentialId: ByteArray,
    val aaguid: ByteArray,
    val algorithm: Int,
    val createdAt: Instant
)

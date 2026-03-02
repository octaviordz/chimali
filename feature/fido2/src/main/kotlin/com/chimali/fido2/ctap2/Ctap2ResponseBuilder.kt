package com.chimali.fido2.ctap2

import com.chimali.fido2.domain.model.*
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.domain.exception.Fido2Exception
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builder for CTAP2 responses, particularly for attestation objects.
 * Handles creation of properly formatted CTAP2 responses with CBOR encoding.
 */
@Singleton
class Ctap2ResponseBuilder @Inject constructor(
    private val cborCodec: CborCodec
) {
    
    companion object {
        private const val CTAP2_MAKE_CREDENTIAL = 0x01
        private const val CTAP2_GET_ASSERTION = 0x02
        private const val CTAP2_GET_INFO = 0x04
        private const val CTAP2_CLIENT_PIN = 0x06
        private const val CTAP2_GET_NEXT_ASSERTION = 0x08
        private const val CTAP2_RESET = 0x07
        private const val CTAP2_GET_BIO_ENROLLMENT = 0x0A
        private const val CTAP2_BIO_ENROLLMENT = 0x0B
        private const val CTAP2_CREDENTIAL_MGMT = 0x0A
        
        private const val CTAP2_STATUS_SUCCESS = 0x00
        private const val CTAP2_STATUS_ERROR_INVALID_CBOR = 0x12
        private const val CTAP2_STATUS_ERROR_INVALID_PARAMETER = 0x13
        private const val CTAP2_STATUS_ERROR_MISSING_PARAMETER = 0x14
        private const val CTAP2_STATUS_ERROR_LIMIT_EXCEEDED = 0x15
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
        
        private const val ALGORITHM_ES256 = -7
        private const val ALGORITHM_RS256 = -257
        private const val ALGORITHM_EDDSA = -8
        
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
        
        private const val MAX_RESPONSE_SIZE = 7609
    }
    
    /**
     * Creates a successful MakeCredential response.
     */
    fun createMakeCredentialResponse(attestationObject: AttestationObject): Result<Ctap2Response> {
        return try {
            val responseData = buildMakeCredentialResponseData(attestationObject)
            
            Result.success(Ctap2Response(
                commandId = CTAP2_MAKE_CREDENTIAL,
                status = CTAP2_STATUS_SUCCESS,
                data = responseData,
                isSuccess = true,
                errorMessage = null
            ))
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.ResponseCreationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Creates a successful GetAssertion response.
     */
    fun createGetAssertionResponse(assertionObject: AssertionObject): Result<Ctap2Response> {
        return try {
            val responseData = buildGetAssertionResponseData(assertionObject)
            
            Result.success(Ctap2Response(
                commandId = CTAP2_GET_ASSERTION,
                status = CTAP2_STATUS_SUCCESS,
                data = responseData,
                isSuccess = true,
                errorMessage = null
            ))
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.ResponseCreationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Creates a GetInfo response.
     */
    fun createGetInfoResponse(authenticatorInfo: AuthenticatorInfo): Result<Ctap2Response> {
        return try {
            val responseData = buildGetInfoResponseData(authenticatorInfo)
            
            Result.success(Ctap2Response(
                commandId = CTAP2_GET_INFO,
                status = CTAP2_STATUS_SUCCESS,
                data = responseData,
                isSuccess = true,
                errorMessage = null
            ))
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.ResponseCreationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Creates a Client PIN response.
     */
    fun createClientPinResponse(responseData: ByteArray): Result<Ctap2Response> {
        return try {
            Result.success(Ctap2Response(
                commandId = CTAP2_CLIENT_PIN,
                status = CTAP2_STATUS_SUCCESS,
                data = responseData,
                isSuccess = true,
                errorMessage = null
            ))
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.ResponseCreationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Creates an error response.
     */
    fun createErrorResponse(commandId: Int, status: Int, errorMessage: String? = null): Ctap2Response {
        return Ctap2Response(
            commandId = commandId,
            status = status,
            data = null,
            isSuccess = false,
            errorMessage = errorMessage ?: getStatusMessage(status)
        )
    }
    
    /**
     * Creates a KeepAlive response.
     */
    fun createKeepAliveResponse(status: Int): Ctap2Response {
        return Ctap2Response(
            commandId = 0x3B, // KeepAlive command
            status = status,
            data = null,
            isSuccess = status == CTAP2_STATUS_SUCCESS,
            errorMessage = if (status == CTAP2_STATUS_SUCCESS) null else getStatusMessage(status)
        )
    }
    
    /**
     * Builds MakeCredential response data.
     */
    private fun buildMakeCredentialResponseData(attestationObject: AttestationObject): ByteArray {
        val responseMap = mutableMapOf<String, Any>()
        
        // Add format
        responseMap["fmt"] = attestationObject.fmt
        
        // Add authenticator data
        responseMap["authData"] = attestationObject.authData.toByteArray()
        
        // Add attestation statement
        responseMap["attStmt"] = buildAttestationStatementMap(attestationObject.attStmt)
        
        // Add client data
        responseMap["clientData"] = attestationObject.clientData.toJson().toByteArray()
        
        return cborCodec.encode(responseMap)
    }
    
    /**
     * Builds GetAssertion response data.
     */
    private fun buildGetAssertionResponseData(assertionObject: AssertionObject): ByteArray {
        val responseMap = mutableMapOf<String, Any>()
        
        // Add credential ID
        responseMap["credentialId"] = assertionObject.credentialId
        
        // Add authenticator data
        responseMap["authData"] = assertionObject.authData.toByteArray()
        
        // Add signature
        responseMap["signature"] = assertionObject.signature
        
        // Add user handle
        assertionObject.userHandle?.let { userHandle ->
            responseMap["userHandle"] = userHandle
        }
        
        return cborCodec.encode(responseMap)
    }
    
    /**
     * Builds GetInfo response data.
     */
    private fun buildGetInfoResponseData(authenticatorInfo: AuthenticatorInfo): ByteArray {
        val responseMap = mutableMapOf<String, Any>()
        
        // Add versions
        responseMap["versions"] = listOf("FIDO_2_0", "FIDO_2_1_PRE", "U2F_V2")
        
        // Add extensions
        responseMap["extensions"] = authenticatorInfo.supportedExtensions
        
        // Add AAGUID
        responseMap["aaguid"] = authenticatorInfo.aaguid
        
        // Add options
        responseMap["options"] = mapOf(
            "platform" to authenticatorInfo.isPlatformDevice,
            "rk" to authenticatorInfo.supportsResidentKeys,
            "clientPin" to authenticatorInfo.supportsClientPin,
            "uv" to authenticatorInfo.supportsUserVerification,
            "up" to authenticatorInfo.supportsUserPresence
        )
        
        // Add max message size
        responseMap["maxMsgSize"] = authenticatorInfo.maxMessageSize
        
        // Add max credential count
        responseMap["maxCredentialCountInList"] = authenticatorInfo.maxCredentialCount
        
        // Add max credential ID length
        responseMap["maxCredentialIdLength"] = authenticatorInfo.maxCredentialIdLength
        
        // Add transports
        responseMap["transports"] = authenticatorInfo.supportedTransports.map { it.name.lowercase() }
        
        return cborCodec.encode(responseMap)
    }
    
    /**
     * Builds attestation statement map.
     */
    private fun buildAttestationStatementMap(attestationStatement: AttestationStatement): Map<String, Any> {
        val statementMap = mutableMapOf<String, Any>()
        
        // Add algorithm
        statementMap["alg"] = attestationStatement.alg
        
        // Add signature
        statementMap["sig"] = attestationStatement.sig
        
        // Add x5c (certificate chain)
        if (attestationStatement.x5c.isNotEmpty()) {
            statementMap["x5c"] = attestationStatement.x5c.map { it.toByteArray() }
        }
        
        // Add attestation type
        attestationStatement.attestationType?.let { type ->
            statementMap["attestationType"] = type
        }
        
        return statementMap
    }
    
    /**
     * Creates a self-signed attestation statement.
     */
    fun createSelfSignedAttestation(
        publicKey: PublicKey,
        authenticatorData: AuthenticatorData,
        algorithm: Int = ALGORITHM_ES256
    ): Result<AttestationStatement> {
        return try {
            // Create self-signed certificate (simplified)
            val signature = createSelfSignature(authenticatorData.toByteArray(), publicKey, algorithm)
            
            Result.success(AttestationStatement(
                alg = algorithm,
                sig = signature,
                x5c = emptyList(), // No certificate chain for self-attestation
                attestationType = "self"
            ))
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.AttestationCreationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Creates a Packed attestation statement.
     */
    fun createPackedAttestationStatement(
        publicKey: PublicKey,
        authenticatorData: AuthenticatorData,
        algorithm: Int = ALGORITHM_ES256
    ): Result<AttestationStatement> {
        return try {
            // Create attestation certificate (simplified)
            val certificate = createAttestationCertificate(publicKey, algorithm)
            val signature = createCertificateSignature(authenticatorData.toByteArray(), certificate.privateKey, algorithm)
            
            Result.success(AttestationStatement(
                alg = algorithm,
                sig = signature,
                x5c = listOf(certificate.encoded),
                attestationType = "packed"
            ))
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.AttestationCreationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Creates a FIDO-U2F attestation statement.
     */
    fun createFidoU2fAttestationStatement(
        publicKey: PublicKey,
        authenticatorData: AuthenticatorData,
        algorithm: Int = ALGORITHM_ES256
    ): Result<AttestationStatement> {
        return try {
            // FIDO-U2F uses a different format
            val signature = createSelfSignature(authenticatorData.toByteArray(), publicKey, algorithm)
            
            Result.success(AttestationStatement(
                alg = algorithm,
                sig = signature,
                x5c = emptyList(), // FIDO-U2F doesn't use x5c
                attestationType = "fido-u2f"
            ))
            
        } catch (e: Exception) {
            Result.failure(Fido2Exception.AttestationCreationFailed(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Creates a self-signature.
     */
    private fun createSelfSignature(data: ByteArray, publicKey: PublicKey, algorithm: Int): ByteArray {
        return try {
            val signatureAlgorithm = when (algorithm) {
                ALGORITHM_ES256 -> "SHA256withECDSA"
                ALGORITHM_RS256 -> "SHA256withRSA"
                ALGORITHM_EDDSA -> "SHA256withEdDSA"
                else -> "SHA256withECDSA" // Default
            }
            
            val signature = java.security.Signature.getInstance(signatureAlgorithm)
            signature.initSign(publicKey)
            signature.update(data)
            signature.sign()
            
        } catch (e: Exception) {
            throw Fido2Exception.SignatureCreationFailed(e.message ?: "Unknown error", e)
        }
    }
    
    /**
     * Creates an attestation certificate.
     */
    private fun createAttestationCertificate(publicKey: PublicKey, algorithm: Int): Certificate {
        return try {
            // This is a simplified certificate creation
            // In production, you would use proper certificate generation
            val keyPair = KeyPair(publicKey, mockk()) // Placeholder private key
            
            val certGen = java.security.cert.CertificateGenerator.getInstance("X.509")
            val certSpec = java.security.cert.X509CertInfo(
                1L,
                System.currentTimeMillis(),
                Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000),
                java.security.Principal(),
                java.security.Principal(),
                keyPair
            )
            
            certGen.generate(certSpec)
            
        } catch (e: Exception) {
            throw Fido2Exception.CertificateCreationFailed(e.message ?: "Unknown error", e)
        }
    }
    
    /**
     * Creates a certificate signature.
     */
    private fun createCertificateSignature(data: ByteArray, privateKey: PrivateKey, algorithm: Int): ByteArray {
        return try {
            val signatureAlgorithm = when (algorithm) {
                ALGORITHM_ES256 -> "SHA256withECDSA"
                ALGORITHM_RS256 -> "SHA256withRSA"
                ALGORITHM_EDDSA -> "SHA256withEdDSA"
                else -> "SHA256withECDSA" // Default
            }
            
            val signature = java.security.Signature.getInstance(signatureAlgorithm)
            signature.initSign(privateKey)
            signature.update(data)
            signature.sign()
            
        } catch (e: Exception) {
            throw Fido2Exception.SignatureCreationFailed(e.message ?: "Unknown error", e)
        }
    }
    
    /**
     * Creates authenticator data bytes.
     */
    fun createAuthenticatorDataBytes(authenticatorData: AuthenticatorData): ByteArray {
        return try {
            val data = mutableListOf<Byte>()
            
            // Add RP ID hash (32 bytes)
            data.addAll(authenticatorData.rpIdHash.toList())
            
            // Add flags (1 byte)
            data.add(authenticatorData.flags)
            
            // Add sign count (4 bytes, big-endian)
            val signCountBytes = ByteBuffer.allocate(4)
                .order(java.nio.ByteOrder.BIG_ENDIAN)
                .putInt(authenticatorData.signCount.toInt())
                .array()
            data.addAll(signCountBytes.toList())
            
            // Add AAGUID (16 bytes)
            data.addAll(authenticatorData.aaguid.toList())
            
            // Add credential ID length (2 bytes, big-endian)
            val credIdLength = authenticatorData.credentialId.size
            val credIdLengthBytes = ByteBuffer.allocate(2)
                .order(java.nio.ByteOrder.BIG_ENDIAN)
                .putShort(credIdLength.toShort())
                .array()
            data.addAll(credIdLengthBytes.toList())
            
            // Add credential ID
            data.addAll(authenticatorData.credentialId.toList())
            
            // Add public key (variable length)
            data.addAll(authenticatorData.credentialPublicKey.toList())
            
            data.toByteArray()
            
        } catch (e: Exception) {
            throw Fido2Exception.AuthenticatorDataCreationFailed(e.message ?: "Unknown error", e)
        }
    }
    
    /**
     * Gets human-readable message for status code.
     */
    fun getStatusMessage(status: Int): String {
        return when (status) {
            CTAP2_STATUS_SUCCESS -> "Success"
            CTAP2_STATUS_ERROR_INVALID_CBOR -> "Invalid CBOR"
            CTAP2_STATUS_ERROR_INVALID_PARAMETER -> "Invalid parameter"
            CTAP2_STATUS_ERROR_MISSING_PARAMETER -> "Missing parameter"
            CTAP2_STATUS_ERROR_LIMIT_EXCEEDED -> "Limit exceeded"
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
    
    /**
     * Validates response size.
     */
    fun validateResponseSize(data: ByteArray): Boolean {
        return data.size <= MAX_RESPONSE_SIZE
    }
    
    /**
     * Creates a response flow for streaming large responses.
     */
    fun createResponseFlow(responses: List<Ctap2Response>): Flow<Ctap2Response> {
        return flowOf(*responses.toTypedArray())
    }
    
    /**
     * Gets response statistics.
     */
    fun getResponseStatistics(responses: List<Ctap2Response>): ResponseStatistics {
        val successful = responses.count { it.isSuccess }
        val failed = responses.count { !it.isSuccess }
        val commandTypes = responses.groupBy { it.commandId }
        
        return ResponseStatistics(
            totalResponses = responses.size,
            successfulResponses = successful,
            failedResponses = failed,
            successRate = if (responses.isNotEmpty()) successful.toDouble() / responses.size else 0.0,
            commandTypeDistribution = commandTypes.mapValues { it.value.size },
            averageResponseSize = responses.mapNotNull { it.data?.size }.average()
        )
    }
}

/**
 * Data class representing response statistics.
 */
data class ResponseStatistics(
    val totalResponses: Int,
    val successfulResponses: Int,
    val failedResponses: Int,
    val successRate: Double,
    val commandTypeDistribution: Map<Int, Int>,
    val averageResponseSize: Double
) {
    /**
     * Gets a summary of response statistics.
     */
    fun getSummary(): String {
        return "Response Stats: $totalResponses total, $successfulResponses successful, $failedResponses failed, ${"%.1f".format(successRate * 100)}% success rate"
    }
}

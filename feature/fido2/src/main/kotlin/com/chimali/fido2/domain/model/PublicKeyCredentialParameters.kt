package com.chimali.fido2.domain.model

/**
 * Domain model representing PublicKeyCredentialParameters.
 * This contains cryptographic parameters for credential creation.
 */
data class PublicKeyCredentialParameters(
    val type: PublicKeyCredentialType,
    val algorithm: String,
    val curve: String?,
    val salt: ByteArray?
) {
    
    init {
        validate()
    }
    
    /**
     * Validates the PublicKeyCredentialParameters according to FIDO2 specifications.
     * Throws IllegalArgumentException if validation fails.
     */
    internal fun validate() {
        // Validate required fields
        require(algorithm.isNotBlank()) { "Algorithm cannot be blank" }
        require(type != PublicKeyCredentialType.UNKNOWN) { "Credential type must be specified" }
        
        // Validate algorithm
        require(algorithm in setOf("ES256", "RS256", "EdDSA", "ML-DSA")) {
            "Algorithm must be one of: ES256, RS256, EdDSA, ML-DSA"
        }
        
        // Validate curve if present
        curve?.let { curveValue ->
            require(curveValue in setOf("P-256", "P-384", "P-521", "Ed25519", "Ed448")) { 
                "Curve must be one of: P-256, P-384, P-521, Ed25519, Ed448" 
            }
        }
        
        // Validate salt if present
        salt?.let { saltValue ->
            require(saltValue.isNotEmpty()) { "Salt cannot be empty if provided" }
            require(saltValue.size <= 32) { "Salt cannot exceed 32 bytes" }
        }
    }
    
    /**
     * Checks if this uses elliptic curve cryptography.
     */
    fun isEllipticCurve(): Boolean {
        return algorithm.startsWith("ES") || algorithm.startsWith("Ed")
    }
    
    /**
     * Checks if this uses RSA.
     */
    fun isRsa(): Boolean {
        return algorithm.startsWith("RS")
    }
    
    /**
     * Returns the curve name for display.
     */
    fun getCurveName(): String {
        return curve ?: "N/A"
    }
    
    /**
     * Returns the algorithm family.
     */
    fun getAlgorithmFamily(): String {
        return when {
            algorithm.startsWith("ES") -> "Elliptic Curve"
            algorithm.startsWith("RS") -> "RSA"
            algorithm.startsWith("Ed") -> "Edwards Curve"
            else -> "Unknown"
        }
    }
    
    /**
     * Returns a safe salt value.
     */
    fun getSafeSalt(): ByteArray {
        return salt ?: ByteArray(16) { it.hashCode().toByte() }
    }
    
    companion object {
        /**
         * Maximum allowed sizes for various fields.
         */
        const val MAX_SALT_SIZE = 32
        const val DEFAULT_SALT_SIZE = 16
        
        /**
         * Creates a new PublicKeyCredentialParameters with validation.
         */
        fun create(
            type: PublicKeyCredentialType = PublicKeyCredentialType.PUBLIC_KEY,
            algorithm: String = "ES256",
            curve: String? = "P-256",
            salt: ByteArray? = null
        ): PublicKeyCredentialParameters {
            return PublicKeyCredentialParameters(
                type = type,
                algorithm = algorithm,
                curve = curve,
                salt = salt
            )
        }
        
        /**
         * Creates parameters for ES256 with P-256 curve.
         */
        fun createES256P256(): PublicKeyCredentialParameters {
            return create(
                type = PublicKeyCredentialType.PUBLIC_KEY,
                algorithm = "ES256",
                curve = "P-256"
            )
        }
        
        /**
         * Creates parameters for RS256.
         */
        fun createRS256(): PublicKeyCredentialParameters {
            return create(
                type = PublicKeyCredentialType.PUBLIC_KEY,
                algorithm = "RS256",
                curve = null
            )
        }

        /**
         * Creates parameters for Ed25519.
         */
        fun createEd25519(): PublicKeyCredentialParameters {
            return create(
                type = PublicKeyCredentialType.PUBLIC_KEY,
                algorithm = "EdDSA",
                curve = "Ed25519"
            )
        }

        /**
         * Creates parameters for ML-DSA-65 (Dilithium, NIST FIPS 204 Level 3).
         * COSE algorithm ID: -49 (working-draft; IANA finalization pending).
         */
        fun createMlDsa65(): PublicKeyCredentialParameters {
            return create(
                type = PublicKeyCredentialType.PUBLIC_KEY,
                algorithm = "ML-DSA",
                curve = null
            )
        }
    }
}

/**
 * Enumeration of public key credential types.
 */
enum class PublicKeyCredentialType {
    PUBLIC_KEY,
    UNKNOWN
}

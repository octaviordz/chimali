package com.chimali.fido2.domain.model

/**
 * Domain model representing PublicKeyCredentialParameters.
 * This contains cryptographic parameters for credential creation.
 */
data class PublicKeyCredentialParameters(
    val type: PublicKeyCredentialType,
    val algorithm: String,
    val curve: String?,
    val keyType: String?,
    val salt: ByteArray?,
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

        curve?.let { curveValue ->
            require(curveValue in setOf("P-256", "P-384", "P-521", "Ed25519", "Ed448")) {
                "Curve must be one of: P-256, P-384, P-521, Ed25519, Ed448"
            }
        }

        // Validate keyType if present
        keyType?.let { kty ->
            require(kty in setOf("EC2", "OKP", "RSA")) {
                "Key type must be one of: EC2, OKP, RSA"
            }
        }

        // Validate salt if present
        salt?.let { saltValue ->
            require(saltValue.isNotEmpty()) { "Salt cannot be empty if provided" }
            require(saltValue.size <= MAX_SALT_SIZE) { "Salt cannot exceed $MAX_SALT_SIZE bytes" }
        }
    }

    /**
     * Checks if this uses elliptic curve cryptography.
     */
    fun isEllipticCurve(): Boolean = algorithm.startsWith("ES") || algorithm.startsWith("Ed")

    /**
     * Checks if this uses RSA.
     */
    fun isRsa(): Boolean = algorithm.startsWith("RS")

    /**
     * Returns the curve name for display.
     */
    @Suppress("unused")
    fun getCurveName(): String = curve ?: "N/A"

    /**
     * Returns the algorithm family.
     */
    fun getAlgorithmFamily(): String =
        when {
            algorithm.startsWith("ES") -> "Elliptic Curve"
            algorithm.startsWith("RS") -> "RSA"
            algorithm.startsWith("Ed") -> "Edwards Curve"
            else -> "Unknown"
        }

    /**
     * Returns a safe salt value.
     */
    @Suppress("unused")
    fun getSafeSalt(): ByteArray = salt ?: ByteArray(DEFAULT_SALT_SIZE) { it.hashCode().toByte() }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PublicKeyCredentialParameters

        if (type != other.type) return false
        if (algorithm != other.algorithm) return false
        if (curve != other.curve) return false
        if (keyType != other.keyType) return false
        if (salt != null) {
            if (other.salt == null) return false
            if (!salt.contentEquals(other.salt)) return false
        } else if (other.salt != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = (31 * result) + algorithm.hashCode()
        result = (31 * result) + (curve?.hashCode() ?: 0)
        result = (31 * result) + (keyType?.hashCode() ?: 0)
        result = (31 * result) + (salt?.contentHashCode() ?: 0)
        return result
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
            keyType: String? = "EC2",
            salt: ByteArray? = null,
        ): PublicKeyCredentialParameters =
            PublicKeyCredentialParameters(
                type = type,
                algorithm = algorithm,
                curve = curve,
                keyType = keyType,
                salt = salt,
            )

        /**
         * Creates parameters for ES256 with P-256 curve.
         */
        fun createES256P256(): PublicKeyCredentialParameters = createEs256()

        /**
         * T018a: Creates parameters for ES256 with explicit P-256 curve and EC2 key type.
         * Fulfills FR-FIDO2-006.
         */
        fun createEs256(): PublicKeyCredentialParameters =
            create(
                type = PublicKeyCredentialType.PUBLIC_KEY,
                algorithm = "ES256",
                curve = "P-256",
                keyType = "EC2",
            )

        /**
         * Creates parameters for RS256.
         */
        fun createRS256(): PublicKeyCredentialParameters =
            create(
                type = PublicKeyCredentialType.PUBLIC_KEY,
                algorithm = "RS256",
                curve = null,
                keyType = "RSA",
            )

        /**
         * Creates parameters for EdDSA with Ed25519 curve.
         */
        fun createEdDsa(): PublicKeyCredentialParameters =
            create(
                type = PublicKeyCredentialType.PUBLIC_KEY,
                algorithm = "EdDSA",
                curve = "Ed25519",
                keyType = "OKP",
            )

        /**
         * Creates parameters for ML-DSA-65 (Dilithium, NIST FIPS 204 Level 3).
         * COSE algorithm ID: -49 (working-draft; IANA finalization pending).
         */
        fun createMlDsa65(): PublicKeyCredentialParameters =
            create(
                type = PublicKeyCredentialType.PUBLIC_KEY,
                algorithm = "ML-DSA",
                curve = null,
            )
    }
}

/**
 * Enumeration of public key credential types.
 */
enum class PublicKeyCredentialType {
    PUBLIC_KEY,
    UNKNOWN,
}

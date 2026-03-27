package com.chimali.fido2.domain.model

import java.time.Instant

/**
 * Domain model representing a FIDO2 AttestationObject.
 * This contains the attestation data returned during credential creation.
 */
data class AttestationObject(
    val fmt: String,
    val authData: AuthenticatorData,
    val attStmt: AttestationStatement,
    val clientData: ClientData
) {
    
    init {
        validate()
    }
    
    /**
     * Validates the AttestationObject according to FIDO2 specifications.
     * Throws IllegalArgumentException if validation fails.
     */
    internal fun validate() {
        // Validate required fields
        require(fmt.isNotBlank()) { "Format cannot be blank" }
        require(fmt in setOf("packed", "fido-u2f", "none", "android-safetynet", "android-key")) { 
            "Format must be one of: packed, fido-u2f, none, android-safetynet, android-key" 
        }
        
        // Validate auth data
        authData.validate()
        
        // Validate attestation statement
        attStmt.validate()
        
        // Validate client data
        clientData.validate()
    }
    
    /**
     * Checks if this attestation is self-attested.
     */
    fun isSelfAttested(): Boolean {
        return fmt == "none"
    }
    
    /**
     * Checks if this attestation uses packed format.
     */
    fun isPacked(): Boolean {
        return fmt == "packed"
    }
    
    /**
     * Checks if this attestation is from Android SafetyNet.
     */
    fun isAndroidSafetyNet(): Boolean {
        return fmt == "android-safetynet"
    }
    
    /**
     * Returns a safe format description.
     */
    fun getFormatDescription(): String {
        return when (fmt) {
            "packed" -> "Packed attestation format"
            "fido-u2f" -> "FIDO U2F format"
            "none" -> "No attestation"
            "android-safetynet" -> "Android SafetyNet attestation"
            "android-key" -> "Android Key attestation"
            else -> "Unknown format: $fmt"
        }
    }
    
    companion object {
        /**
         * Creates a new AttestationObject with validation.
         */
        fun create(
            fmt: String = "packed",
            authData: AuthenticatorData,
            attStmt: AttestationStatement,
            clientData: ClientData
        ): AttestationObject {
            return AttestationObject(
                fmt = fmt,
                authData = authData,
                attStmt = attStmt,
                clientData = clientData
            )
        }
        
        /**
         * Creates a self-attested object.
         */
        fun createSelfAttested(
            authData: AuthenticatorData,
            clientData: ClientData
        ): AttestationObject {
            return create(
                fmt = "none",
                authData = authData,
                attStmt = AttestationStatement.createNone(),
                clientData = clientData
            )
        }
    }
}

/**
 * Domain model representing AuthenticatorData.
 * This contains the authenticator data from the attestation object.
 */
data class AuthenticatorData(
    val rpIdHash: ByteArray,
    val flags: ByteArray,
    val counter: Long,
    val aaguid: ByteArray,
    val credentialId: ByteArray,
    val publicKey: ByteArray
) {
    
    init {
        validate()
    }
    
    /**
     * Validates the AuthenticatorData according to FIDO2 specifications.
     */
    internal fun validate() {
        // Validate required fields
        require(rpIdHash.size == 32) { "RP ID hash must be exactly 32 bytes" }
        require(flags.size == 1) { "Flags must be exactly 1 byte" }
        require(counter >= 0) { "Counter cannot be negative" }
        require(aaguid.size == 16) { "AAGUID must be exactly 16 bytes" }
        require(credentialId.isNotEmpty()) { "Credential ID cannot be empty" }
        require(credentialId.size <= 1023) { "Credential ID cannot exceed 1023 bytes" }
        require(publicKey.isNotEmpty()) { "Public key cannot be empty" }
        require(publicKey.size <= MAX_PUBLIC_KEY_BYTES) { "Public key cannot exceed $MAX_PUBLIC_KEY_BYTES bytes" }
    }
    
    /**
     * Checks if user verification is required.
     */
    fun isUserVerificationRequired(): Boolean {
        return flags.isNotEmpty() && (flags[0].toInt() and 0x04) != 0
    }
    
    /**
     * Checks if user was present.
     */
    fun isUserPresent(): Boolean {
        return flags.isNotEmpty() && (flags[0].toInt() and 0x01) != 0
    }
    
    /**
     * Checks if user verification is satisfied.
     */
    fun isUserVerified(): Boolean {
        return flags.isNotEmpty() && (flags[0].toInt() and 0x04) != 0
    }
    
    /**
     * Returns the credential ID as base64.
     */
    fun getCredentialIdBase64(): String {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(credentialId)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AuthenticatorData

        if (!rpIdHash.contentEquals(other.rpIdHash)) return false
        if (!flags.contentEquals(other.flags)) return false
        if (counter != other.counter) return false
        if (!aaguid.contentEquals(other.aaguid)) return false
        if (!credentialId.contentEquals(other.credentialId)) return false
        if (!publicKey.contentEquals(other.publicKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rpIdHash.contentHashCode()
        result = 31 * result + flags.contentHashCode()
        result = 31 * result + counter.hashCode()
        result = 31 * result + aaguid.contentHashCode()
        result = 31 * result + credentialId.contentHashCode()
        result = 31 * result + publicKey.contentHashCode()
        return result
    }
    
    companion object {
        /** P-256 uncompressed point in COSE CBOR = ~77 bytes; ML-DSA-65 DER = ~1952 bytes → use 2048 as the cap. */
        const val MAX_PUBLIC_KEY_BYTES = 2048

        /**
         * Creates a new AuthenticatorData with validation.
         */
        fun create(
            rpIdHash: ByteArray,
            flags: ByteArray = byteArrayOf(0x00),
            counter: Long = 0L,
            aaguid: ByteArray,
            credentialId: ByteArray,
            publicKey: ByteArray
        ): AuthenticatorData {
            return AuthenticatorData(
                rpIdHash = rpIdHash,
                flags = flags,
                counter = counter,
                aaguid = aaguid,
                credentialId = credentialId,
                publicKey = publicKey
            )
        }
    }
}

/**
 * Domain model representing AttestationStatement.
 * This contains the attestation statement from the attestation object.
 */
data class AttestationStatement(
    val alg: Any,
    val fmt: String,
    val attCert: ByteArray?,
    val authData: ByteArray?,
    val x5c: List<ByteArray>?
) {
    
    init {
        validate()
    }
    
    /**
     * Validates the AttestationStatement according to FIDO2 specifications.
     */
    internal fun validate() {
        // Validate required fields
        // Validate algorithm
        when (alg) {
            is String -> {
                require(alg.isNotBlank()) { "Algorithm cannot be blank" }
                require(alg in setOf("ES256", "RS256", "RS1", "ES384", "RS384", "ES512", "RS512", "EdDSA", "Ed25519", "none")) { 
                    "Algorithm must be a valid signature algorithm string" 
                }
            }
            is Int, is Long -> {
                val algInt = (alg as Number).toInt()
                require(algInt in setOf(
                    COSE_ALG_ES256, COSE_ALG_ES384, COSE_ALG_ES512, 
                    COSE_ALG_PS256, COSE_ALG_PS384, COSE_ALG_PS512, 
                    COSE_ALG_EDDSA, COSE_ALG_ED25519, COSE_ALG_ML_DSA_65, COSE_ALG_RS256
                )) { 
                    "Algorithm must be a valid COSE algorithm identifier" 
                }
            }
            else -> require(false) { "Algorithm must be a String or Integer" }
        }
        
        // Validate format
        require(fmt in setOf("packed", "fido-u2f", "none", "android-safetynet", "android-key")) { 
            "Format must be one of: packed, fido-u2f, none, android-safetynet, android-key" 
        }
        
        // Validate optional fields
        attCert?.let { cert ->
            require(cert.isNotEmpty()) { "Attestation certificate cannot be empty if provided" }
            require(cert.size <= 2048) { "Attestation certificate cannot exceed 2048 bytes" }
        }
        
        authData?.let { auth ->
            require(auth.isNotEmpty()) { "Auth data cannot be empty if provided" }
            require(auth.size <= MAX_AUTH_DATA_BYTES) { "Auth data cannot exceed $MAX_AUTH_DATA_BYTES bytes" }
        }
        
        x5c?.let { chain ->
            require(chain.isNotEmpty()) { "X5C chain cannot be empty if provided" }
            require(chain.size <= 10) { "X5C chain cannot exceed 10 certificates" }
            chain.forEach { cert ->
                require(cert.isNotEmpty()) { "Certificate in chain cannot be empty" }
                require(cert.size <= 2048) { "Certificate in chain cannot exceed 2048 bytes" }
            }
        }
    }
    
    /**
     * Checks if this statement has an attestation certificate.
     */
    fun hasCertificate(): Boolean {
        return attCert?.isNotEmpty() ?: false
    }
    
    /**
     * Checks if this statement has auth data.
     */
    fun hasAuthData(): Boolean {
        return authData?.isNotEmpty() ?: false
    }
    
    /**
     * Checks if this statement has an X5C chain.
     */
    fun hasX5cChain(): Boolean {
        return x5c?.isNotEmpty() ?: false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AttestationStatement

        if (alg != other.alg) return false
        if (fmt != other.fmt) return false
        if (attCert != null) {
            if (other.attCert == null) return false
            if (!attCert.contentEquals(other.attCert)) return false
        } else if (other.attCert != null) return false
        if (authData != null) {
            if (other.authData == null) return false
            if (!authData.contentEquals(other.authData)) return false
        } else if (other.authData != null) return false
        
        if (x5c != null) {
            if (other.x5c == null) return false
            if (x5c.size != other.x5c.size) return false
            for (i in x5c.indices) {
                if (!x5c[i].contentEquals(other.x5c[i])) return false
            }
        } else if (other.x5c != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = alg.hashCode()
        result = 31 * result + fmt.hashCode()
        result = 31 * result + (attCert?.contentHashCode() ?: 0)
        result = 31 * result + (authData?.contentHashCode() ?: 0)
        result = 31 * result + (x5c?.fold(1) { acc, bytes -> 31 * acc + bytes.contentHashCode() } ?: 0)
        return result
    }
    
    companion object {
        // Standard COSE Signature Algorithms
        const val COSE_ALG_ES256 = -7
        const val COSE_ALG_ES384 = -35
        const val COSE_ALG_ES512 = -36
        const val COSE_ALG_PS256 = -37
        const val COSE_ALG_PS384 = -38
        const val COSE_ALG_PS512 = -39
        const val COSE_ALG_EDDSA = -8
        const val COSE_ALG_ED25519 = -19
        const val COSE_ALG_ML_DSA_65 = -49
        const val COSE_ALG_RS256 = -257

        const val MAX_AUTH_DATA_BYTES = 4096

        /**
         * Creates a new AttestationStatement with validation.
         */
        fun create(
            alg: Any = COSE_ALG_ES256,
            fmt: String = "packed",
            attCert: ByteArray? = null,
            authData: ByteArray? = null,
            x5c: List<ByteArray>? = null
        ): AttestationStatement {
            return AttestationStatement(
                alg = alg,
                fmt = fmt,
                attCert = attCert,
                authData = authData,
                x5c = x5c
            )
        }
        
        /**
         * Creates a "none" attestation statement.
         */
        fun createNone(): AttestationStatement {
            return AttestationStatement(
                alg = "none",
                fmt = "none",
                attCert = null,
                authData = null,
                x5c = null
            )
        }
    }
}

/**
 * Domain model representing ClientData.
 * This contains the client data from the attestation object.
 */
data class ClientData(
    val type: String,
    val challenge: ByteArray,
    val origin: String,
    val crossOrigin: Boolean,
    val timestamp: Instant
) {
    
    init {
        validate()
    }
    
    /**
     * Validates the ClientData according to FIDO2 specifications.
     */
    internal fun validate() {
        // Validate required fields
        require(type.isNotBlank()) { "Type cannot be blank" }
        require(challenge.isNotEmpty()) { "Challenge cannot be empty" }
        require(challenge.size <= 64) { "Challenge cannot exceed 64 bytes" }
        require(origin.isNotBlank()) { "Origin cannot be blank" }
        require(RelyingParty.isValidRpId(origin)) { 
            "Origin must be a valid domain or HTTPS origin: $origin" 
        }
        
        // Validate timestamp
        require(timestamp.isBefore(Instant.now().plusSeconds(60))) { 
            "Timestamp cannot be more than 60 seconds in the future" 
        }
    }
    
    /**
     * Returns the challenge as a base64 URL-safe string.
     */
    fun getChallengeBase64Url(): String {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)
    }
    
    /**
     * Checks if this is for credential creation.
     */
    fun isCredentialCreation(): Boolean {
        return type == "webauthn.create"
    }
    
    /**
     * Checks if this is for credential assertion.
     */
    fun isCredentialAssertion(): Boolean {
        return type == "webauthn.get"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ClientData

        if (type != other.type) return false
        if (!challenge.contentEquals(other.challenge)) return false
        if (origin != other.origin) return false
        if (crossOrigin != other.crossOrigin) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + challenge.contentHashCode()
        result = 31 * result + origin.hashCode()
        result = 31 * result + crossOrigin.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
    
    companion object {
        /**
         * Creates a new ClientData with validation.
         */
        fun create(
            type: String = "webauthn.create",
            challenge: ByteArray,
            origin: String,
            crossOrigin: Boolean = false
        ): ClientData {
            return ClientData(
                type = type,
                challenge = challenge,
                origin = origin,
                crossOrigin = crossOrigin,
                timestamp = Instant.now()
            )
        }
        
        /**
         * Creates a ClientData from base64 challenge.
         */
        fun fromBase64Challenge(
            type: String = "webauthn.create",
            challengeBase64: String,
            origin: String,
            crossOrigin: Boolean = false
        ): ClientData {
            val challenge = try {
                java.util.Base64.getUrlDecoder().decode(challengeBase64)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid base64 challenge", e)
            }
            
            return create(type, challenge, origin, crossOrigin)
        }
    }
}

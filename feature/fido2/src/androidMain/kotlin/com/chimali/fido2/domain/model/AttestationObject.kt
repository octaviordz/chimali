package com.chimali.fido2.domain.model

import com.chimali.core.domain.model.RelyingParty
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Instant

/**
 * Domain model representing a FIDO2 AttestationObject.
 * This contains the attestation data returned during credential creation.
 */
data class AttestationObject(
    val fmt: String,
    val authData: AuthenticatorData,
    val attStmt: AttestationStatement,
    val clientData: ClientData,
) {
    init {
        validate()
    }

    /**
     * Validates the AttestationObject according to FIDO2 specifications.
     * Throws IllegalArgumentException if validation fails.
     */
    fun validate() {
        // Validate required fields
        require(fmt.isNotBlank()) { "Format cannot be blank" }
        require(fmt in SUPPORTED_FORMATS) {
            "Format must be one of: ${SUPPORTED_FORMATS.joinToString()}"
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
    fun isSelfAttested(): Boolean = fmt == FORMAT_NONE

    /**
     * Checks if this attestation uses packed format.
     */
    fun isPacked(): Boolean = fmt == FORMAT_PACKED

    companion object {
        private const val FORMAT_PACKED = "packed"
        private const val FORMAT_FIDO_U2F = "fido-u2f"
        private const val FORMAT_NONE = "none"
        private const val FORMAT_ANDROID_SAFETYNET = "android-safetynet"
        private const val FORMAT_ANDROID_KEY = "android-key"

        // T037: AttCA format — required for WebAuthn L3 full attestation compliance
        private const val FORMAT_ATT_CA = "attCA"

        private val SUPPORTED_FORMATS =
            setOf(
                FORMAT_PACKED,
                FORMAT_FIDO_U2F,
                FORMAT_NONE,
                FORMAT_ANDROID_SAFETYNET,
                FORMAT_ANDROID_KEY,
                FORMAT_ATT_CA,
            )

        /**
         * Creates a new AttestationObject with validation.
         */
        fun create(
            fmt: String = FORMAT_PACKED,
            authData: AuthenticatorData,
            attStmt: AttestationStatement,
            clientData: ClientData,
        ): AttestationObject =
            AttestationObject(
                fmt = fmt,
                authData = authData,
                attStmt = attStmt,
                clientData = clientData,
            )

        /**
         * Creates a self-attested object.
         */
        fun createSelfAttested(
            authData: AuthenticatorData,
            clientData: ClientData,
        ): AttestationObject =
            create(
                fmt = FORMAT_NONE,
                authData = authData,
                attStmt = AttestationStatement.createNone(),
                clientData = clientData,
            )

        /**
         * T037: Creates an AttCA (Attestation CA) attested object.
         * Used for Basic and AttCA attestation types where a certificate chain is present.
         */
        fun createAttCa(
            authData: AuthenticatorData,
            attStmt: AttestationStatement,
            clientData: ClientData,
        ): AttestationObject =
            create(
                fmt = FORMAT_ATT_CA,
                authData = authData,
                attStmt = attStmt,
                clientData = clientData,
            )
    }
}

/**
 * T037: Attestation type classification per WebAuthn L3 §6.5.3.
 *
 * - [NONE]  — No attestation statement ("none" format). Privacy-preserving.
 * - [SELF]  — Self attestation — authenticator signs with its own credential key.
 * - [BASIC] — Basic attestation — authenticator has a batch attestation key.
 * - [ATT_CA] — Attestation CA — intermediate CA certificate chain present.
 */
enum class AttestationType {
    NONE,
    SELF,
    BASIC,
    ATT_CA,
    ;

    /** Human-readable label for logging and UI. */
    fun getLabel(): String =
        when (this) {
            NONE -> "No Attestation"
            SELF -> "Self Attestation"
            BASIC -> "Basic Attestation"
            ATT_CA -> "Attestation CA"
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
    val publicKey: ByteArray,
) {
    init {
        validate()
    }

    /**
     * Validates the AuthenticatorData according to FIDO2 specifications.
     */
    fun validate() {
        // Validate required fields
        require(rpIdHash.size == RP_ID_HASH_SIZE) { "RP ID hash must be exactly $RP_ID_HASH_SIZE bytes" }
        require(flags.size == FLAGS_SIZE) { "Flags must be exactly $FLAGS_SIZE byte" }
        require(counter >= 0) { "Counter cannot be negative" }
        require(aaguid.size == AAGUID_SIZE) { "AAGUID must be exactly $AAGUID_SIZE bytes" }
        require(credentialId.isNotEmpty()) { "Credential ID cannot be empty" }
        require(
            credentialId.size <= MAX_CREDENTIAL_ID_SIZE,
        ) { "Credential ID cannot exceed $MAX_CREDENTIAL_ID_SIZE bytes" }
        require(publicKey.isNotEmpty()) { "Public key cannot be empty" }
        require(publicKey.size <= MAX_PUBLIC_KEY_BYTES) { "Public key cannot exceed $MAX_PUBLIC_KEY_BYTES bytes" }
    }

    /**
     * Checks if user verification is required.
     */
    fun isUserVerificationRequired(): Boolean = flags.isNotEmpty() && ((flags[0].toInt() and FLAG_UV_MASK) != 0)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AuthenticatorData

        return rpIdHash.contentEquals(other.rpIdHash) &&
            flags.contentEquals(other.flags) &&
            (counter == other.counter) &&
            aaguid.contentEquals(other.aaguid) &&
            credentialId.contentEquals(other.credentialId) &&
            publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int {
        var result = rpIdHash.contentHashCode()
        result = (31 * result) + flags.contentHashCode()
        result = (31 * result) + counter.hashCode()
        result = (31 * result) + aaguid.contentHashCode()
        result = (31 * result) + credentialId.contentHashCode()
        result = (31 * result) + publicKey.contentHashCode()
        return result
    }

    companion object {
        private const val RP_ID_HASH_SIZE = 32
        private const val FLAGS_SIZE = 1
        private const val AAGUID_SIZE = 16
        private const val MAX_CREDENTIAL_ID_SIZE = 1023
        private const val FLAG_UV_MASK = 0x04

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
            publicKey: ByteArray,
        ): AuthenticatorData =
            AuthenticatorData(
                rpIdHash = rpIdHash,
                flags = flags,
                counter = counter,
                aaguid = aaguid,
                credentialId = credentialId,
                publicKey = publicKey,
            )
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
    val x5c: List<ByteArray>?,
) {
    init {
        validate()
    }

    /**
     * Validates the AttestationStatement according to FIDO2 specifications.
     */
    fun validate() {
        // Validate required fields
        // Validate algorithm
        when (alg) {
            is String -> {
                require(alg.isNotBlank()) { "Algorithm cannot be blank" }
                require(alg in SUPPORTED_ALGORITHMS) {
                    "Algorithm must be a valid signature algorithm string"
                }
            }
            is Int, is Long -> {
                val algInt = (alg as Number).toInt()
                require(algInt in SUPPORTED_COSE_ALGORITHMS) {
                    "Algorithm must be a valid COSE algorithm identifier"
                }
            }
            else -> throw IllegalArgumentException("Algorithm must be a String or Integer")
        }

        // Validate format
        require(fmt in SUPPORTED_FORMATS) {
            "Format must be one of: ${SUPPORTED_FORMATS.joinToString()}"
        }

        // Validate optional fields
        attCert?.let { cert ->
            require(cert.isNotEmpty()) { "Attestation certificate cannot be empty if provided" }
            require(cert.size <= MAX_CERT_SIZE) { "Attestation certificate cannot exceed $MAX_CERT_SIZE bytes" }
        }

        authData?.let { auth ->
            require(auth.isNotEmpty()) { "Auth data cannot be empty if provided" }
            require(auth.size <= MAX_AUTH_DATA_BYTES) { "Auth data cannot exceed $MAX_AUTH_DATA_BYTES bytes" }
        }

        x5c?.let { chain ->
            require(chain.isNotEmpty()) { "X5C chain cannot be empty if provided" }
            require(chain.size <= MAX_X5C_CHAIN_SIZE) { "X5C chain cannot exceed $MAX_X5C_CHAIN_SIZE certificates" }
            chain.forEach { cert ->
                require(cert.isNotEmpty()) { "Certificate in chain cannot be empty" }
                require(cert.size <= MAX_CERT_SIZE) { "Certificate in chain cannot exceed $MAX_CERT_SIZE bytes" }
            }
        }
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
        } else if (other.attCert != null) {
            return false
        }
        if (authData != null) {
            if (other.authData == null) return false
            if (!authData.contentEquals(other.authData)) return false
        } else if (other.authData != null) {
            return false
        }

        if (x5c != null) {
            if (other.x5c == null) return false
            if (x5c.size != other.x5c.size) return false
            for (i in x5c.indices) {
                if (!x5c[i].contentEquals(other.x5c[i])) return false
            }
        } else if (other.x5c != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = alg.hashCode()
        result = 31 * result + (fmt.hashCode())
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

        private val SUPPORTED_ALGORITHMS =
            setOf(
                "ES256",
                "RS256",
                "RS1",
                "ES384",
                "RS384",
                "ES512",
                "RS512",
                "EdDSA",
                "Ed25519",
                "none",
            )

        private val SUPPORTED_COSE_ALGORITHMS =
            setOf(
                COSE_ALG_ES256,
                COSE_ALG_ES384,
                COSE_ALG_ES512,
                COSE_ALG_PS256,
                COSE_ALG_PS384,
                COSE_ALG_PS512,
                COSE_ALG_EDDSA,
                COSE_ALG_ED25519,
                COSE_ALG_ML_DSA_65,
                COSE_ALG_RS256,
            )

        private val SUPPORTED_FORMATS =
            setOf(
                "packed",
                "fido-u2f",
                "none",
                "android-safetynet",
                "android-key",
                "attCA",
            )

        // ML-DSA-65 signatures are 3309 bytes; 4096 covers all current PQC schemes
        private const val MAX_CERT_SIZE = 4096
        private const val MAX_X5C_CHAIN_SIZE = 10
        const val MAX_AUTH_DATA_BYTES = 4096

        /**
         * Creates a new AttestationStatement with validation.
         */
        fun create(
            alg: Any = COSE_ALG_ES256,
            fmt: String = "packed",
            attCert: ByteArray? = null,
            authData: ByteArray? = null,
            x5c: List<ByteArray>? = null,
        ): AttestationStatement =
            AttestationStatement(
                alg = alg,
                fmt = fmt,
                attCert = attCert,
                authData = authData,
                x5c = x5c,
            )

        /**
         * Creates a "none" attestation statement.
         */
        fun createNone(): AttestationStatement =
            AttestationStatement(
                alg = "none",
                fmt = "none",
                attCert = null,
                authData = null,
                x5c = null,
            )
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
    val timestamp: Instant,
) {
    init {
        validate()
    }

    /**
     * Validates the ClientData according to FIDO2 specifications.
     */
    fun validate() {
        // Validate required fields
        require(type.isNotBlank()) { "Type cannot be blank" }
        require(challenge.isNotEmpty()) { "Challenge cannot be empty" }
        require(challenge.size <= MAX_CHALLENGE_SIZE) { "Challenge cannot exceed $MAX_CHALLENGE_SIZE bytes" }
        require(origin.isNotBlank()) { "Origin cannot be blank" }
        require(RelyingParty.isValidRpId(origin)) {
            "Origin must be a valid domain or HTTPS origin: $origin"
        }

        // Validate timestamp
        require(
            timestamp <= Instant.fromEpochMilliseconds(System.currentTimeMillis()) + FUTURE_GRACE_PERIOD,
        ) {
            "Timestamp cannot be more than $FUTURE_GRACE_PERIOD_SECONDS seconds in the future"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ClientData

        return (type == other.type) &&
            challenge.contentEquals(other.challenge) &&
            (origin == other.origin) &&
            (crossOrigin == other.crossOrigin) &&
            (timestamp == other.timestamp)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = (31 * result) + challenge.contentHashCode()
        result = (31 * result) + origin.hashCode()
        result = (31 * result) + crossOrigin.hashCode()
        result = (31 * result) + timestamp.hashCode()
        return result
    }

    companion object {
        private const val MAX_CHALLENGE_SIZE = 64
        private const val FUTURE_GRACE_PERIOD_SECONDS = 60
        private val FUTURE_GRACE_PERIOD = FUTURE_GRACE_PERIOD_SECONDS.seconds
        private const val TYPE_CREATE = "webauthn.create"

        /**
         * Creates a new ClientData with validation.
         */
        fun create(
            type: String = TYPE_CREATE,
            challenge: ByteArray,
            origin: String,
            crossOrigin: Boolean = false,
        ): ClientData =
            ClientData(
                type = type,
                challenge = challenge,
                origin = origin,
                crossOrigin = crossOrigin,
                timestamp = Instant.fromEpochMilliseconds(System.currentTimeMillis()),
            )
    }
}

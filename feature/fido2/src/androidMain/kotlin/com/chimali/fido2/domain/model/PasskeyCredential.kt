package com.chimali.fido2.domain.model

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.CredentialProtectionPolicy
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.PublicKey
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Instant

/**
 * Domain model representing a FIDO2 passkey credential.
 * This is the core entity for storing and managing user passkeys.
 */
@Suppress("LongParameterList")
@Req("NFR-SEC-041", "TR-AUTH-031")
class PasskeyCredential internal constructor(
    val id: CredentialId,
    val rpId: SensitiveCredentialText,
    val userId: SensitiveCredentialText,
    val userName: SensitiveCredentialText,
    val userDisplayName: SensitiveCredentialText,
    val publicKey: PublicKey,
    val signCount: Long,
    val createdAt: Instant,
    val lastUsedAt: Instant,
    val aaguid: ByteArray,
    val credentialId: ByteArray,
    val coseAlgorithm: Int = COSE_ES256,
    val derivationProfile: String,
    val credProtectPolicy: Int = 1,
    val label: String? = null,
) {
    // Preserve the former data-class construction API while storing sensitive metadata in owned arrays.
    constructor(
        id: CredentialId,
        rpId: RpId,
        userId: UserId,
        userName: String,
        userDisplayName: String,
        publicKey: PublicKey,
        signCount: Long,
        createdAt: Instant,
        lastUsedAt: Instant,
        aaguid: ByteArray,
        credentialId: ByteArray,
        coseAlgorithm: Int = COSE_ES256,
        derivationProfile: String,
        credProtectPolicy: Int = 1,
        label: String? = null,
    ) : this(
        id = id,
        rpId = SensitiveCredentialText.copyOf(rpId.value),
        userId = SensitiveCredentialText.copyOf(userId.value),
        userName = SensitiveCredentialText.copyOf(userName),
        userDisplayName = SensitiveCredentialText.copyOf(userDisplayName),
        publicKey = publicKey,
        signCount = signCount,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
        aaguid = aaguid,
        credentialId = credentialId,
        coseAlgorithm = coseAlgorithm,
        derivationProfile = derivationProfile,
        credProtectPolicy = credProtectPolicy,
        label = label,
    )

    init {
        try {
            validate()
        } catch (failure: IllegalArgumentException) {
            eraseSensitiveText()
            throw failure
        } catch (failure: java.nio.charset.CharacterCodingException) {
            eraseSensitiveText()
            throw failure
        } catch (failure: ArithmeticException) {
            eraseSensitiveText()
            throw failure
        }
    }

    /**
     * Validates the PasskeyCredential according to FIDO2 specifications.
     * Throws IllegalArgumentException if validation fails.
     */

    private fun validate() {
        CredentialProtectionPolicy.requireSupported(credProtectPolicy)
        // Validate required fields
        require(!userName.isBlankSensitive()) { "User name cannot be blank" }
        require(!userDisplayName.isBlankSensitive()) { "User display name cannot be blank" }
        require(CredentialKeyDescriptor.profileFor(coseAlgorithm) == derivationProfile) {
            "Unsupported credential algorithm/profile pair"
        }

        // Validate formats
        require(isValidRpId(rpId)) {
            "RP ID must be a valid domain or HTTPS origin"
        }
        require(userId.length <= MAX_USER_ID_LENGTH) { "User ID cannot exceed $MAX_USER_ID_LENGTH bytes" }
        require(utf8Size(userName) <= MAX_NAME_LENGTH) {
            "User name cannot exceed $MAX_NAME_LENGTH bytes (UTF-8)"
        }
        require(utf8Size(userDisplayName) <= MAX_DISPLAY_NAME_LENGTH) {
            "User display name cannot exceed $MAX_DISPLAY_NAME_LENGTH bytes (UTF-8)"
        }
        require(aaguid.size == AAGUID_LENGTH) { "AAGUID must be exactly $AAGUID_LENGTH bytes" }
        require(credentialId.isNotEmpty()) { "Credential ID cannot be empty" }
        require(
            credentialId.size <= MAX_CREDENTIAL_ID_LENGTH,
        ) { "Credential ID cannot exceed $MAX_CREDENTIAL_ID_LENGTH bytes" }

        // Validate sign count
        require(signCount >= 0) { "Sign count cannot be negative" }
        require(signCount <= Long.MAX_VALUE) { "Sign count exceeds maximum value" }

        // Validate timestamps
        val now = TimeProvider().now()
        require(createdAt < now + FUTURE_GRACE_SECONDS.seconds) {
            "Creation time cannot be more than $FUTURE_GRACE_SECONDS seconds in the future"
        }
        require(lastUsedAt < now + FUTURE_GRACE_SECONDS.seconds) {
            "Last used time cannot be more than $FUTURE_GRACE_SECONDS seconds in the future"
        }
        require(lastUsedAt >= createdAt) {
            "Last used time cannot be before creation time"
        }

        // Validate COSE Algorithm
        require(
            coseAlgorithm == COSE_ES256 ||
                coseAlgorithm == COSE_EDSA ||
                coseAlgorithm == COSE_ML_DSA_65 ||
                coseAlgorithm == COSE_RS256,
        ) {
            "Unsupported COSE algorithm ID: $coseAlgorithm"
        }
    }

    /**
     * Checks if this credential is expired based on creation time.
     * Credentials typically expire after a certain period (e.g., 2 years).
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun isExpired(maxAgeDays: Long = DEFAULT_MAX_AGE_DAYS): Boolean {
        val expiryTime = createdAt + maxAgeDays.days
        return TimeProvider().now() > expiryTime
    }

    /**
     * Checks if this credential belongs to the specified relying party.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun belongsToRelyingParty(rpId: RpId): Boolean = this.rpId.trimmedContentEquals(rpId.value.trimEnd('/'))

    /**
     * Checks if this credential belongs to the specified user.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun belongsToUser(userId: UserId): Boolean = this.userId.contentEquals(userId.value, ignoreCase = true)

    /**
     * Returns a safe display name for the credential.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getSafeDisplayName(): String =
        if (!userDisplayName.isBlankSensitive()) {
            userDisplayName.toBoundaryString()
        } else {
            userName.toBoundaryString()
        }

    /** Explicitly erase all application-owned RP/account text for this credential instance. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun eraseSensitiveText() {
        rpId.erase()
        userId.erase()
        userName.erase()
        userDisplayName.erase()
    }

    /** Creates an independent sensitive-text owner for a distinct UI or transport lifetime. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun copyWithIndependentSensitiveText(): PasskeyCredential =
        PasskeyCredential(
            id = id,
            rpId = rpId.copyOwner(),
            userId = userId.copyOwner(),
            userName = userName.copyOwner(),
            userDisplayName = userDisplayName.copyOwner(),
            publicKey = publicKey,
            signCount = signCount,
            createdAt = createdAt,
            lastUsedAt = lastUsedAt,
            aaguid = aaguid.copyOf(),
            credentialId = credentialId.copyOf(),
            coseAlgorithm = coseAlgorithm,
            derivationProfile = derivationProfile,
            credProtectPolicy = credProtectPolicy,
            label = label,
        )

    /** Compares public identity and sensitive metadata by value. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is PasskeyCredential &&
                    id == other.id &&
                    rpId == other.rpId &&
                    userId == other.userId &&
                    userName == other.userName &&
                    userDisplayName == other.userDisplayName &&
                    signCount == other.signCount &&
                    createdAt == other.createdAt &&
                    lastUsedAt == other.lastUsedAt &&
                    aaguid.contentEquals(other.aaguid) &&
                    credentialId.contentEquals(other.credentialId) &&
                    coseAlgorithm == other.coseAlgorithm &&
                    derivationProfile == other.derivationProfile &&
                    credProtectPolicy == other.credProtectPolicy &&
                    label == other.label
            )

    /** Hashes the same metadata fields used by [equals]. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + rpId.hashCode()
        result = 31 * result + userId.hashCode()
        result = 31 * result + userName.hashCode()
        result = 31 * result + userDisplayName.hashCode()
        result = 31 * result + signCount.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + lastUsedAt.hashCode()
        result = 31 * result + aaguid.contentHashCode()
        result = 31 * result + credentialId.contentHashCode()
        result = 31 * result + coseAlgorithm
        result = 31 * result + derivationProfile.hashCode()
        result = 31 * result + credProtectPolicy
        result = 31 * result + (label?.hashCode() ?: 0)
        return result
    }

    /** Returns a diagnostic summary that redacts all relying-party, account, key and credential identifiers. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun toString(): String =
        "PasskeyCredential(id=$id, rpId=[redacted], userId=[redacted], userName=[redacted], " +
            "userDisplayName=[redacted], signCount=$signCount, createdAt=$createdAt, " +
            "lastUsedAt=$lastUsedAt, coseAlgorithm=$coseAlgorithm, derivationProfile=$derivationProfile)"

    /** Deep copy for immutable-counter update paths; every resulting model owns independent arrays. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun copy(
        id: CredentialId = this.id,
        rpId: SensitiveCredentialText = this.rpId,
        userId: SensitiveCredentialText = this.userId,
        userName: SensitiveCredentialText = this.userName,
        userDisplayName: SensitiveCredentialText = this.userDisplayName,
        publicKey: PublicKey = this.publicKey,
        signCount: Long = this.signCount,
        createdAt: Instant = this.createdAt,
        lastUsedAt: Instant = this.lastUsedAt,
        aaguid: ByteArray = this.aaguid,
        credentialId: ByteArray = this.credentialId,
        coseAlgorithm: Int = this.coseAlgorithm,
        derivationProfile: String = this.derivationProfile,
        credProtectPolicy: Int = this.credProtectPolicy,
        label: String? = this.label,
    ): PasskeyCredential =
        PasskeyCredential(
            id = id,
            rpId = rpId.copyOwner(),
            userId = userId.copyOwner(),
            userName = userName.copyOwner(),
            userDisplayName = userDisplayName.copyOwner(),
            publicKey = publicKey,
            signCount = signCount,
            createdAt = createdAt,
            lastUsedAt = lastUsedAt,
            aaguid = aaguid,
            credentialId = credentialId,
            coseAlgorithm = coseAlgorithm,
            derivationProfile = derivationProfile,
            credProtectPolicy = credProtectPolicy,
            label = label,
        )

    private fun SensitiveCredentialText.trimmedContentEquals(other: String): Boolean {
        var ownEnd = length
        while (ownEnd > 0 && this[ownEnd - 1] == '/') ownEnd--
        var otherEnd = other.length
        while (otherEnd > 0 && other[otherEnd - 1] == '/') otherEnd--
        if (ownEnd != otherEnd) return false
        return (0 until ownEnd).all { this[it].equals(other[it], ignoreCase = true) }
    }

    private fun SensitiveCredentialText.isBlankSensitive(): Boolean =
        length == 0 || (0 until length).all { this[it].isWhitespace() }

    private fun isValidRpId(value: SensitiveCredentialText): Boolean {
        if (value.isBlankSensitive()) return false
        val separator = findSchemeSeparator(value)
        if (separator >= 0) {
            val isHttp = value.regionMatches(0, "http", ignoreCase = true)
            val isHttps = value.regionMatches(0, "https", ignoreCase = true)
            val schemeEnd =
                when {
                    isHttps -> HTTPS_SCHEME_LENGTH
                    isHttp -> HTTP_SCHEME_LENGTH
                    else -> return false
                }
            return separator == schemeEnd && (schemeEnd until value.length).any { !value[it].isWhitespace() }
        }
        if (value.contentEquals("localhost") || value.startsWith("localhost:")) return true
        var hasDot = false
        for (index in 0 until value.length) {
            if (value[index] == '.') hasDot = true
            if (value[index].isWhitespace()) return false
        }
        return hasDot
    }

    private fun findSchemeSeparator(value: SensitiveCredentialText): Int {
        for (index in 0 until value.length - 2) {
            if (value[index] == ':' && value[index + 1] == '/' && value[index + 2] == '/') return index
        }
        return -1
    }

    private fun SensitiveCredentialText.regionMatches(
        start: Int,
        expected: String,
        ignoreCase: Boolean = false,
    ): Boolean =
        start >= 0 &&
            start + expected.length <= length &&
            expected.indices.all { index ->
                if (ignoreCase) {
                    this[start + index].equals(expected[index], ignoreCase = true)
                } else {
                    this[start + index] == expected[index]
                }
            }

    private fun SensitiveCredentialText.startsWith(expected: String): Boolean = regionMatches(0, expected)

    private fun utf8Size(value: SensitiveCredentialText): Int {
        val output = ByteBuffer.allocate(Math.multiplyExact(value.length, MAX_UTF8_BYTES_PER_CHARACTER))
        return try {
            val encoder =
                StandardCharsets.UTF_8
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
            val result = encoder.encode(CharBuffer.wrap(value), output, true)
            if (result.isError) result.throwException()
            require(result.isUnderflow) { "Credential text exceeds its validation buffer" }
            output.position()
        } finally {
            output.array().fill(0)
        }
    }

    /** A fresh copy of the public X.509 encoding persisted with the registration. */
    val storedPublicKeyEncoding: ByteArray get() = publicKey.encoded.copyOf()

    /**
     * Returns the credential age in days.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getAgeInDays(): Long = (TimeProvider().now() - createdAt).inWholeDays

    /**
     * Creates a copy with updated sign count.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun withSignCount(newSignCount: Long): PasskeyCredential =
        copy(
            signCount = newSignCount,
            lastUsedAt = TimeProvider().now(),
        )

    /**
     * Creates a copy with updated last used time.
     */

    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun withLastUsedAt(newLastUsedAt: Instant): PasskeyCredential = copy(lastUsedAt = newLastUsedAt)

    companion object {
        // COSE algorithm IDs
        const val COSE_ES256 = -7 // ECDSA with SHA-256 / P-256
        const val COSE_EDSA = -8 // EdDSA / Ed25519, per WebAuthn L3 § 5.4
        const val COSE_RS256 = -257 // RSASSA-PKCS1-v1_5 with SHA-256 (WebAuthn §5.8.5)

        /** ML-DSA-65 (Dilithium, NIST FIPS 204 Level 3). Working-draft COSE ID. */
        const val COSE_ML_DSA_65 = -49 // ML-DSA-65 (Dilithium)

        // Maximum allowed sizes for various fields according to FIDO2 specs.
        const val MAX_USER_ID_LENGTH = 64
        const val MAX_NAME_LENGTH = 64
        const val MAX_DISPLAY_NAME_LENGTH = 64
        const val MAX_CREDENTIAL_ID_LENGTH = 1023
        const val AAGUID_LENGTH = 16
        private const val MAX_UTF8_BYTES_PER_CHARACTER = 4
        private const val HTTP_SCHEME_LENGTH = 4
        private const val HTTPS_SCHEME_LENGTH = 5

        private const val DEFAULT_MAX_AGE_DAYS = 730L
        private const val FUTURE_GRACE_SECONDS = 60L

        /**
         * Generates a new cryptographically secure random credential ID.
         * Returns a [CredentialId] with 32 bytes of entropy and its Base64URL-safe encoding.
         */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun generateRandomId(): CredentialId = CredentialId.generate()

        /**
         * Creates a new PasskeyCredential with validation.
         */

        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun create(
            id: CredentialId,
            rpId: RpId,
            userId: UserId,
            userName: String,
            userDisplayName: String,
            publicKey: PublicKey,
            aaguid: ByteArray,
            credentialId: ByteArray,
            coseAlgorithm: Int = COSE_ES256,
            derivationProfile: String,
            credProtectPolicy: Int = 1,
        ): PasskeyCredential {
            val now = TimeProvider().now()
            return PasskeyCredential(
                id = id,
                rpId = rpId,
                userId = userId,
                userName = userName,
                userDisplayName = userDisplayName,
                publicKey = publicKey,
                signCount = 0L,
                createdAt = now,
                lastUsedAt = now,
                aaguid = aaguid,
                credentialId = credentialId,
                coseAlgorithm = coseAlgorithm,
                derivationProfile = derivationProfile,
                credProtectPolicy = credProtectPolicy,
                label = null,
            )
        }

        /**
         * Creates a credential from borrowed request models without materializing compatibility Strings.
         * The independent owners created here transfer to the credential constructor; any failure erases them.
         */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        internal fun createFromRequestOwners(
            id: CredentialId,
            rp: PublicKeyCredentialRpEntity,
            user: PublicKeyCredentialUserEntity,
            publicKey: PublicKey,
            aaguid: ByteArray,
            credentialId: ByteArray,
            coseAlgorithm: Int = COSE_ES256,
            derivationProfile: String,
            credProtectPolicy: Int = 1,
        ): PasskeyCredential {
            var rpOwner: SensitiveCredentialText? = null
            var userIdOwner: SensitiveCredentialText? = null
            var userNameOwner: SensitiveCredentialText? = null
            var userDisplayNameOwner: SensitiveCredentialText? = null
            try {
                rpOwner = rp.copyIdOwner()
                userIdOwner = user.copyEncodedIdOwner()
                userNameOwner = user.copyNameOwner()
                userDisplayNameOwner = user.copyDisplayNameOwner()
                val ownedRpId = requireNotNull(rpOwner)
                val ownedUserId = requireNotNull(userIdOwner)
                val ownedUserName = requireNotNull(userNameOwner)
                val ownedUserDisplayName = requireNotNull(userDisplayNameOwner)
                val now = TimeProvider().now()
                return PasskeyCredential(
                    id = id,
                    rpId = ownedRpId,
                    userId = ownedUserId,
                    userName = ownedUserName,
                    userDisplayName = ownedUserDisplayName,
                    publicKey = publicKey,
                    signCount = 0L,
                    createdAt = now,
                    lastUsedAt = now,
                    aaguid = aaguid,
                    credentialId = credentialId,
                    coseAlgorithm = coseAlgorithm,
                    derivationProfile = derivationProfile,
                    credProtectPolicy = credProtectPolicy,
                    label = null,
                ).also {
                    rpOwner = null
                    userIdOwner = null
                    userNameOwner = null
                    userDisplayNameOwner = null
                }
            } finally {
                rpOwner?.erase()
                userIdOwner?.erase()
                userNameOwner?.erase()
                userDisplayNameOwner?.erase()
            }
        }

        /**
         * Test-only factory — creates a minimal valid [PasskeyCredential] without a real [PublicKey].
         * Must NOT be called in production code.
         */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun createTest(
            id: CredentialId,
            rpId: RpId,
            userName: String,
            coseAlgorithm: Int = COSE_ES256,
            credProtectPolicy: Int = 1,
            label: String? = null,
        ): PasskeyCredential {
            val now = TimeProvider().now()
            val syntheticPubKey =
                object : PublicKey {
                    @Req("NFR-SEC-041", "TR-AUTH-031")
                    override fun getAlgorithm(): String = "EC"

                    @Req("NFR-SEC-041", "TR-AUTH-031")
                    override fun getFormat(): String = "X.509"

                    @Req("NFR-SEC-041", "TR-AUTH-031")
                    override fun getEncoded(): ByteArray = ByteArray(0)
                }
            return PasskeyCredential(
                id = id,
                rpId = rpId,
                userId = UserId("user_${id.encoded}"),
                userName = userName,
                userDisplayName = userName,
                publicKey = syntheticPubKey,
                signCount = 0L,
                createdAt = now,
                lastUsedAt = now,
                aaguid = ByteArray(AAGUID_LENGTH),
                credentialId = id.toByteArray(),
                coseAlgorithm = coseAlgorithm,
                derivationProfile = CredentialKeyDescriptor.profileFor(coseAlgorithm),
                credProtectPolicy = credProtectPolicy,
                label = label,
            )
        }
    }
}

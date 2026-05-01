package com.chimali.fido2.domain.model

import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import java.security.PublicKey
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Instant

/**
 * Domain model representing a FIDO2 passkey credential.
 * This is the core entity for storing and managing user passkeys.
 */
data class PasskeyCredential(
    val id: CredentialId,
    val rpId: RpId,
    val userId: UserId,
    val userName: String,
    val userDisplayName: String,
    val publicKey: PublicKey,
    val privateKeyAlias: String,
    val signCount: Long,
    val createdAt: Instant,
    val lastUsedAt: Instant,
    val aaguid: ByteArray,
    val credentialId: ByteArray,
    val coseAlgorithm: Int = COSE_ES256,
    val credProtectPolicy: Int = 1,
    val label: String? = null,
) {
    init {
        validate()
    }

    /**
     * Validates the PasskeyCredential according to FIDO2 specifications.
     * Throws IllegalArgumentException if validation fails.
     */
    private fun validate() {
        // Validate required fields
        require(userName.isNotBlank()) { "User name cannot be blank" }
        require(userDisplayName.isNotBlank()) { "User display name cannot be blank" }
        require(privateKeyAlias.isNotBlank()) { "Private key alias cannot be blank" }

        // Validate formats
        require(RelyingParty.isValidRpId(rpId.value)) {
            "RP ID must be a valid domain or HTTPS origin: ${rpId.value}"
        }
        require(userId.value.length <= MAX_USER_ID_LENGTH) { "User ID cannot exceed $MAX_USER_ID_LENGTH bytes" }
        require(userName.length <= MAX_NAME_LENGTH) { "User name cannot exceed $MAX_NAME_LENGTH bytes" }
        require(userDisplayName.length <= MAX_DISPLAY_NAME_LENGTH) {
            "User display name cannot exceed $MAX_DISPLAY_NAME_LENGTH bytes"
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
                coseAlgorithm == COSE_ED25519 ||
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
    fun isExpired(maxAgeDays: Long = DEFAULT_MAX_AGE_DAYS): Boolean {
        val expiryTime = createdAt + maxAgeDays.days
        return TimeProvider().now() > expiryTime
    }

    /**
     * Checks if this credential belongs to the specified relying party.
     */
    fun belongsToRelyingParty(rpId: RpId): Boolean =
        this.rpId.value
            .trimEnd('/')
            .equals(rpId.value.trimEnd('/'), ignoreCase = true)

    /**
     * Checks if this credential belongs to the specified user.
     */
    fun belongsToUser(userId: UserId): Boolean = this.userId.value.equals(userId.value, ignoreCase = true)

    /**
     * Returns a safe display name for the credential.
     */
    fun getSafeDisplayName(): String = userDisplayName.ifBlank { userName }

    /**
     * Returns the credential age in days.
     */
    fun getAgeInDays(): Long = (TimeProvider().now() - createdAt).inWholeDays

    /**
     * Creates a copy with updated sign count.
     */
    fun withSignCount(newSignCount: Long): PasskeyCredential =
        copy(
            signCount = newSignCount,
            lastUsedAt = TimeProvider().now(),
        )

    /**
     * Creates a copy with updated last used time.
     */
    fun withLastUsedAt(newLastUsedAt: Instant): PasskeyCredential = copy(lastUsedAt = newLastUsedAt)

    companion object {
        // COSE algorithm IDs
        const val COSE_ES256 = -7 // ECDSA with SHA-256 / P-256
        const val COSE_ED25519 = -19 // EdDSA
        const val COSE_RS256 = -257 // RSASSA-PKCS1-v1_5 with SHA-256 (WebAuthn §5.8.5)

        /** ML-DSA-65 (Dilithium, NIST FIPS 204 Level 3). Working-draft COSE ID. */
        const val COSE_ML_DSA_65 = -49 // ML-DSA-65 (Dilithium)

        // Maximum allowed sizes for various fields according to FIDO2 specs.
        const val MAX_USER_ID_LENGTH = 64
        const val MAX_NAME_LENGTH = 64
        const val MAX_DISPLAY_NAME_LENGTH = 64
        const val MAX_CREDENTIAL_ID_LENGTH = 1023
        const val AAGUID_LENGTH = 16

        private const val DEFAULT_MAX_AGE_DAYS = 730L
        private const val FUTURE_GRACE_SECONDS = 60L

        /**
         * Generates a new cryptographically secure random credential ID.
         * Returns a [CredentialId] with 32 bytes of entropy and its Base64URL-safe encoding.
         */
        fun generateRandomId(): CredentialId = CredentialId.generate()

        /**
         * Creates a new PasskeyCredential with validation.
         */

        fun create(
            id: CredentialId,
            rpId: RpId,
            userId: UserId,
            userName: String,
            userDisplayName: String,
            publicKey: PublicKey,
            privateKeyAlias: String,
            aaguid: ByteArray,
            credentialId: ByteArray,
            coseAlgorithm: Int = COSE_ES256,
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
                privateKeyAlias = privateKeyAlias,
                signCount = 0L,
                createdAt = now,
                lastUsedAt = now,
                aaguid = aaguid,
                credentialId = credentialId,
                coseAlgorithm = coseAlgorithm,
                credProtectPolicy = credProtectPolicy,
                label = null,
            )
        }

        /**
         * Test-only factory — creates a minimal valid [PasskeyCredential] without a real [PublicKey].
         * Must NOT be called in production code.
         */
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
                    override fun getAlgorithm(): String = "EC"

                    override fun getFormat(): String = "X.509"

                    override fun getEncoded(): ByteArray = ByteArray(0)
                }
            return PasskeyCredential(
                id = id,
                rpId = rpId,
                userId = UserId("user_${id.encoded}"),
                userName = userName,
                userDisplayName = userName,
                publicKey = syntheticPubKey,
                privateKeyAlias = "fido2_cred_${id.encoded}",
                signCount = 0L,
                createdAt = now,
                lastUsedAt = now,
                aaguid = ByteArray(AAGUID_LENGTH),
                credentialId = id.toByteArray(),
                coseAlgorithm = coseAlgorithm,
                credProtectPolicy = credProtectPolicy,
                label = label,
            )
        }
    }
}

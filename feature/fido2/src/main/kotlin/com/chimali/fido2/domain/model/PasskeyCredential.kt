package com.chimali.fido2.domain.model

import java.security.PublicKey
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Domain model representing a FIDO2 passkey credential.
 * This is the core entity for storing and managing user passkeys.
 */
data class PasskeyCredential(
    val id: String,
    val rpId: String,
    val userId: String,
    val userName: String,
    val userDisplayName: String,
    val publicKey: PublicKey,
    val privateKeyAlias: String,
    val signCount: Long,
    val createdAt: Instant,
    val lastUsedAt: Instant,
    val aaguid: ByteArray,
    val credentialId: ByteArray
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
        require(id.isNotBlank()) { "Credential ID cannot be blank" }
        require(rpId.isNotBlank()) { "RP ID cannot be blank" }
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(userName.isNotBlank()) { "User name cannot be blank" }
        require(userDisplayName.isNotBlank()) { "User display name cannot be blank" }
        require(privateKeyAlias.isNotBlank()) { "Private key alias cannot be blank" }
        
        // Validate formats
        require(RelyingParty.isValidRpId(rpId)) { 
            "RP ID must be a valid domain or HTTPS origin: $rpId" 
        }
        require(userId.length <= 64) { "User ID cannot exceed 64 bytes" }
        require(userName.length <= 64) { "User name cannot exceed 64 bytes" }
        require(userDisplayName.length <= 64) { "User display name cannot exceed 64 bytes" }
        require(aaguid.size == 16) { "AAGUID must be exactly 16 bytes" }
        require(credentialId.isNotEmpty()) { "Credential ID cannot be empty" }
        require(credentialId.size <= 1023) { "Credential ID cannot exceed 1023 bytes" }
        
        // Validate sign count
        require(signCount >= 0) { "Sign count cannot be negative" }
        require(signCount <= Long.MAX_VALUE) { "Sign count exceeds maximum value" }
        
        // Validate timestamps
        require(createdAt.isBefore(Instant.now().plusSeconds(60))) { 
            "Creation time cannot be more than 60 seconds in the future" 
        }
        require(lastUsedAt.isBefore(Instant.now().plusSeconds(60))) { 
            "Last used time cannot be more than 60 seconds in the future" 
        }
        require(!lastUsedAt.isBefore(createdAt)) { 
            "Last used time cannot be before creation time" 
        }
    }
    
    /**
     * Checks if this credential is expired based on creation time.
     * Credentials typically expire after a certain period (e.g., 2 years).
     */
    fun isExpired(maxAgeDays: Long = 730): Boolean {
        val expiryTime = createdAt.plusSeconds(maxAgeDays * 24 * 60 * 60)
        return Instant.now().isAfter(expiryTime)
    }
    
    /**
     * Checks if this credential belongs to the specified relying party.
     */
    fun belongsToRelyingParty(rpId: String): Boolean {
        return this.rpId.trimEnd('/').equals(rpId.trimEnd('/'), ignoreCase = true)
    }
    
    /**
     * Checks if this credential belongs to the specified user.
     */
    fun belongsToUser(userId: String): Boolean {
        return this.userId.equals(userId, ignoreCase = true)
    }
    
    /**
     * Returns a safe display name for the credential.
     */
    fun getSafeDisplayName(): String {
        return if (userDisplayName.isNotBlank()) userDisplayName else userName
    }
    
    /**
     * Returns the credential age in days.
     */
    fun getAgeInDays(): Long {
        return ChronoUnit.DAYS.between(createdAt, Instant.now())
    }
    
    /**
     * Creates a copy with updated sign count.
     */
    fun withSignCount(newSignCount: Long): PasskeyCredential {
        return copy(signCount = newSignCount, lastUsedAt = Instant.now())
    }
    
    /**
     * Creates a copy with updated last used time.
     */
    fun withLastUsedAt(newLastUsedAt: Instant): PasskeyCredential {
        return copy(lastUsedAt = newLastUsedAt)
    }
    
    companion object {
        /**
         * Maximum allowed sizes for various fields according to FIDO2 specs.
         */
        const val MAX_USER_ID_LENGTH = 64
        const val MAX_NAME_LENGTH = 64
        const val MAX_DISPLAY_NAME_LENGTH = 64
        const val MAX_CREDENTIAL_ID_LENGTH = 1023
        const val AAGUID_LENGTH = 16
        
        /**
         * Creates a new PasskeyCredential with validation.
         */
        fun create(
            id: String,
            rpId: String,
            userId: String,
            userName: String,
            userDisplayName: String,
            publicKey: PublicKey,
            privateKeyAlias: String,
            aaguid: ByteArray,
            credentialId: ByteArray
        ): PasskeyCredential {
            val now = Instant.now()
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
                credentialId = credentialId
            )
        }
        /**
         * T071 — Creates a lightweight display-only [PasskeyCredential] from [MakeCredentialOptions].
         *
         * This is used by the ViewModel to surface a usable credential object to the UI immediately
         * after successful registration, while the full credential is persisted by the use case.
         * A synthetic no-op [PublicKey] is used since the real key is stored in Android KeyStore.
         */
        fun fromMakeCredentialOptions(options: MakeCredentialOptions): PasskeyCredential {
            val now = java.time.Instant.now()
            val syntheticPubKey = object : java.security.PublicKey {
                override fun getAlgorithm(): String = "EC"
                override fun getFormat(): String = "X.509"
                override fun getEncoded(): ByteArray = ByteArray(0)
            }
            val credentialId = "cred_${now.toEpochMilli()}"
            return PasskeyCredential(
                id               = credentialId,
                rpId             = options.rp.id,
                userId           = String(options.user.id),
                userName         = options.user.name,
                userDisplayName  = options.user.displayName ?: options.user.name,
                publicKey        = syntheticPubKey,
                privateKeyAlias  = "fido2_cred_$credentialId",
                signCount        = 0L,
                createdAt        = now,
                lastUsedAt       = now,
                aaguid           = ByteArray(16),
                credentialId     = credentialId.toByteArray()
            )
        }

        /**
         * Test-only factory — creates a minimal valid [PasskeyCredential] without a real [PublicKey].
         * Must NOT be called in production code.
         */
        fun createTest(
            id: String,
            rpId: String,
            userName: String
        ): PasskeyCredential {
            val now = java.time.Instant.now()
            val syntheticPubKey = object : java.security.PublicKey {
                override fun getAlgorithm(): String = "EC"
                override fun getFormat(): String = "X.509"
                override fun getEncoded(): ByteArray = ByteArray(0)
            }
            return PasskeyCredential(
                id               = id,
                rpId             = rpId,
                userId           = "user_$id",
                userName         = userName,
                userDisplayName  = userName,
                publicKey        = syntheticPubKey,
                privateKeyAlias  = "fido2_cred_$id",
                signCount        = 0L,
                createdAt        = now,
                lastUsedAt       = now,
                aaguid           = ByteArray(16),
                credentialId     = id.toByteArray()
            )
        }
    }
}

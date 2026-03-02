package com.chimali.fido2.domain.model

import java.time.Instant
import java.net.URI

/**
 * Domain model representing a FIDO2 Relying Party (RP).
 * This entity represents the service/website that requests authentication.
 */
data class RelyingParty(
    val id: String,
    val name: String,
    val iconUrl: String?,
    val credentialCount: Int,
    val createdAt: Instant,
    val lastUsedAt: Instant?
) {
    
    init {
        validate()
    }
    
    /**
     * Validates the RelyingParty according to FIDO2 specifications.
     * Throws IllegalArgumentException if validation fails.
     */
    private fun validate() {
        // Validate required fields
        require(id.isNotBlank()) { "RP ID cannot be blank" }
        require(name.isNotBlank()) { "RP name cannot be blank" }
        
        // Validate formats
        require(id.matches(Regex("^https?://[a-zA-Z0-9.-]+[a-zA-Z0-9./]*$"))) { 
            "RP ID must be a valid HTTPS origin" 
        }
        require(name.length <= 64) { "RP name cannot exceed 64 characters" }
        require(credentialCount >= 0) { "Credential count cannot be negative" }
        
        // Validate icon URL if present
        iconUrl?.let { url ->
            require(url.isNotBlank()) { "Icon URL cannot be blank if provided" }
            require(url.length <= 256) { "Icon URL cannot exceed 256 characters" }
            try {
                URI.create(url)
                require(url.startsWith("https://") || url.startsWith("http://")) { 
                    "Icon URL must use HTTP or HTTPS protocol" 
                }
            } catch (e: Exception) {
                throw IllegalArgumentException("Icon URL must be a valid URI", e)
            }
        }
        
        // Validate timestamps
        require(createdAt.isBefore(Instant.now().plusSeconds(60))) { 
            "Creation time cannot be more than 60 seconds in the future" 
        }
        lastUsedAt?.let { lastUsed ->
            require(lastUsed.isBefore(Instant.now().plusSeconds(60))) { 
                "Last used time cannot be more than 60 seconds in the future" 
            }
            require(!lastUsed.isBefore(createdAt)) { 
                "Last used time cannot be before creation time" 
            }
        }
    }
    
    /**
     * Checks if this RP has any credentials.
     */
    fun hasCredentials(): Boolean {
        return credentialCount > 0
    }
    
    /**
     * Returns the domain from the RP ID.
     */
    fun getDomain(): String {
        return try {
            URI.create(id).host ?: id
        } catch (e: Exception) {
            id
        }
    }
    
    /**
     * Checks if this RP is trusted based on criteria.
     */
    fun isTrusted(trustedDomains: Set<String>): Boolean {
        return trustedDomains.contains(getDomain())
    }
    
    /**
     * Returns a safe name for display.
     */
    fun getSafeName(): String {
        return if (name.isNotBlank()) name else getDomain()
    }
    
    /**
     * Returns the age of this RP in days.
     */
    fun getAgeInDays(): Long {
        return createdAt.until(Instant.now()).toDays()
    }
    
    /**
     * Creates a copy with updated credential count.
     */
    fun withCredentialCount(newCount: Int): RelyingParty {
        return copy(credentialCount = newCount, lastUsedAt = Instant.now())
    }
    
    /**
     * Creates a copy with updated last used time.
     */
    fun withLastUsedAt(newLastUsedAt: Instant): RelyingParty {
        return copy(lastUsedAt = newLastUsedAt)
    }
    
    /**
     * Checks if this RP has been used recently.
     */
    fun isRecentlyUsed(days: Long = 30): Boolean {
        val cutoff = Instant.now().minusSeconds(days * 24 * 60 * 60)
        return lastUsedAt?.isAfter(cutoff) ?: false
    }
    
    companion object {
        /**
         * Maximum allowed sizes for various fields according to FIDO2 specs.
         */
        const val MAX_NAME_LENGTH = 64
        const val MAX_ICON_URL_LENGTH = 256
        
        /**
         * Creates a new RelyingParty with validation.
         */
        fun create(
            id: String,
            name: String,
            iconUrl: String? = null
        ): RelyingParty {
            val now = Instant.now()
            return RelyingParty(
                id = id,
                name = name,
                iconUrl = iconUrl,
                credentialCount = 0,
                createdAt = now,
                lastUsedAt = null
            )
        }
        
        /**
         * Validates RP ID format according to FIDO2 specifications.
         */
        fun isValidRpId(rpId: String): Boolean {
            return try {
                val uri = URI.create(rpId)
                val scheme = uri.scheme?.lowercase()
                val host = uri.host
                
                scheme in setOf("https", "http") && 
                host != null && 
                host.matches(Regex("^[a-zA-Z0-9.-]+[a-zA-Z0-9./]*$"))
            } catch (e: Exception) {
                false
            }
        }
        
        /**
         * Normalizes RP ID to HTTPS format.
         */
        fun normalizeRpId(rpId: String): String {
            return if (rpId.startsWith("https://") || rpId.startsWith("http://")) {
                rpId
            } else {
                "https://$rpId"
            }
        }
    }
}

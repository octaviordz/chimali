package com.chimali.fido2.domain.model

import java.time.Instant
import java.time.temporal.ChronoUnit
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
    val lastUsedAt: Instant? = null,
    val isBlocked: Boolean = false
) {
    
    init {
        validate()
    }
    
    /**
     * Validates the RelyingParty according to FIDO2 specifications.
     * Throws IllegalArgumentException if validation fails.
     */
    internal fun validate() {
        // Validate required fields
        require(id.isNotBlank()) { "RP ID cannot be blank" }
        require(name.isNotBlank()) { "RP name cannot be blank" }
        
        // Validate formats
        require(isValidRpId(id)) { 
            "RP ID must be a valid domain or HTTPS origin: $id" 
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
            val uri = URI.create(id)
            val host = uri.host ?: return id
            if (uri.port != -1) "$host:${uri.port}" else host
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
        return name.ifBlank { getDomain() }
    }
    
    /**
     * Returns the age of this RP in days.
     */
    fun getAgeInDays(): Long {
        return ChronoUnit.DAYS.between(createdAt, Instant.now())
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
            // Auto-set name to domain extracted from id if blank
            val resolvedName = name.ifBlank {
                try {
                    val uri = URI.create(id)
                    uri.host ?: id
                } catch (e: Exception) {
                    id
                }
            }
            return RelyingParty(
                id = id,
                name = resolvedName,
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
            if (rpId.isBlank()) return false
            return try {
                if (rpId.contains("://")) {
                    // Must have http or https scheme
                    val uri = URI(rpId)
                    val scheme = uri.scheme?.lowercase()
                    scheme in setOf("https", "http") && uri.host != null && uri.host.isNotBlank()
                } else {
                    // Bare domain: must contain a dot (e.g., example.com) or be localhost
                    rpId == "localhost" || rpId.startsWith("localhost:") ||
                        (rpId.contains('.') && !rpId.contains(' '))
                }
            } catch (e: Exception) {
                false
            }
        }
        
        /**
         * Normalizes RP ID to HTTPS format.
         */
        fun normalizeRpId(rpId: String): String {
            if (rpId.startsWith("http://localhost")) return rpId
            if (rpId.startsWith("http://")) return "https://" + rpId.substring(7)
            if (rpId.startsWith("https://")) return rpId
            return "https://$rpId"
        }
    }
}

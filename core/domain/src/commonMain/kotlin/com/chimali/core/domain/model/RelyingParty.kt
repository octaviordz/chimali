package com.chimali.core.domain.model

import com.chimali.core.domain.valueobject.RpId
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Domain model representing a FIDO2 Relying Party (RP).
 * This entity represents the service/website that requests authentication.
 */
@Serializable
data class RelyingParty(
    val id: RpId,
    val name: String,
    val iconUrl: String?,
    val credentialCount: Int,
    val createdAt: Instant,
    val lastUsedAt: Instant? = null,
    val isBlocked: Boolean = false,
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
        require(name.isNotBlank()) { "RP name cannot be blank" }

        // Validate formats
        require(isValidRpId(id.value)) {
            "RP ID must be a valid domain or HTTPS origin: ${id.value}"
        }
        require(name.length <= MAX_NAME_LENGTH) { "RP name cannot exceed $MAX_NAME_LENGTH characters" }
        require(credentialCount >= 0) { "Credential count cannot be negative" }

        // Validate icon URL if present
        iconUrl?.let { url ->
            require(url.isNotBlank()) { "Icon URL cannot be blank if provided" }
            require(url.length <= MAX_ICON_URL_LENGTH) { "Icon URL cannot exceed $MAX_ICON_URL_LENGTH characters" }
            require(url.startsWith("https://") || url.startsWith("http://")) {
                "Icon URL must use HTTP or HTTPS protocol"
            }
        }

        // Validate timestamps
        val now = Clock.System.now()
        require(createdAt <= now + FUTURE_GRACE) {
            "Creation time cannot be more than 60 seconds in the future"
        }
        lastUsedAt?.let { lastUsed ->
            require(lastUsed <= now + FUTURE_GRACE) {
                "Last used time cannot be more than 60 seconds in the future"
            }
            require(lastUsed >= createdAt) {
                "Last used time cannot be before creation time"
            }
        }
    }

    /**
     * Checks if this RP has any credentials.
     */
    fun hasCredentials(): Boolean = credentialCount > 0

    /**
     * Returns the domain from the RP ID.
     */
    fun getDomain(): String {
        val value = id.value
        val schemeIndex = value.indexOf("://")
        if (schemeIndex == -1) return value

        val afterScheme = value.substring(schemeIndex + SCHEME_PREFIX_LENGTH)
        val slashIndex = afterScheme.indexOf('/')
        return if (slashIndex == -1) afterScheme else afterScheme.substring(0, slashIndex)
    }

    /**
     * Checks if this RP is trusted based on criteria.
     */
    fun isTrusted(trustedDomains: Set<String>): Boolean = trustedDomains.contains(getDomain())

    /**
     * Returns a safe name for display.
     */
    fun getSafeName(): String = name.ifBlank { getDomain() }

    /**
     * Creates a copy with updated credential count.
     */
    fun withCredentialCount(newCount: Int): RelyingParty =
        copy(credentialCount = newCount, lastUsedAt = Clock.System.now())

    /**
     * Creates a copy with updated last used time.
     */
    fun withLastUsedAt(newLastUsedAt: Instant): RelyingParty = copy(lastUsedAt = newLastUsedAt)

    /**
     * Returns the RP age in days.
     */
    fun getAgeInDays(): Long {
        val diff = Clock.System.now() - createdAt
        return diff.inWholeDays
    }

    /**
     * Checks if this RP has been used recently.
     */
    fun isRecentlyUsed(days: Int = 30): Boolean {
        val cutoff = Clock.System.now() - kotlin.time.Duration.parse("${days}d")
        return lastUsedAt?.let { it > cutoff } ?: false
    }

    companion object {
        const val MAX_NAME_LENGTH = 64
        const val MAX_ICON_URL_LENGTH = 256
        private const val SCHEME_PREFIX_LENGTH = 3
        private const val HTTP_PREFIX_LENGTH = 7
        private val FUTURE_GRACE = 1.minutes

        /**
         * Creates a new RelyingParty with validation.
         */
        fun create(
            id: RpId,
            name: String,
            iconUrl: String? = null,
        ): RelyingParty {
            val now = Clock.System.now()
            // Auto-set name to domain extracted from id if blank
            val resolvedName =
                name.ifBlank {
                    val value = id.value
                    val schemeIndex = value.indexOf("://")
                    if (schemeIndex == -1) {
                        value
                    } else {
                        val afterScheme = value.substring(schemeIndex + SCHEME_PREFIX_LENGTH)
                        val slashIndex = afterScheme.indexOf('/')
                        if (slashIndex == -1) afterScheme else afterScheme.substring(0, slashIndex)
                    }
                }
            return RelyingParty(
                id = id,
                name = resolvedName,
                iconUrl = iconUrl,
                credentialCount = 0,
                createdAt = now,
                lastUsedAt = null,
            )
        }

        /**
         * Validates RP ID format according to FIDO2 specifications.
         */
        fun isValidRpId(rpId: String): Boolean {
            if (rpId.isBlank()) return false
            return if (rpId.contains("://")) {
                // Must have http or https scheme
                val scheme = rpId.substringBefore("://").lowercase()
                val rest = rpId.substringAfter("://")
                (scheme == "https" || scheme == "http") && rest.isNotBlank()
            } else {
                // Bare domain: must contain a dot (e.g., example.com) or be localhost
                rpId == "localhost" ||
                    rpId.startsWith("localhost:") ||
                    (rpId.contains('.') && !rpId.contains(' '))
            }
        }

        /**
         * Normalizes RP ID to HTTPS format.
         */
        fun normalizeRpId(rpId: String): String {
            if (rpId.startsWith("http://localhost")) return rpId
            if (rpId.startsWith("http://")) return "https://" + rpId.substring(HTTP_PREFIX_LENGTH)
            if (rpId.startsWith("https://")) return rpId
            return "https://$rpId"
        }
    }
}

package com.chimali.core.domain.model

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.valueobject.RpId
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Domain model representing a FIDO2 Relying Party (RP).
 * This entity represents the service/website that requests authentication.
 */
@Serializable
class RelyingParty private constructor(
    @SerialName("id") private val idOwner: SensitiveText,
    @SerialName("name") private val nameOwner: SensitiveText,
    val iconUrl: String?,
    val credentialCount: Int,
    val createdAt: Instant,
    val lastUsedAt: Instant? = null,
    val isBlocked: Boolean = false,
) {
    /** Copies compatibility inputs; production operations use fromOwnedText and borrowed text views. */
    constructor(
        id: RpId,
        name: String,
        iconUrl: String?,
        credentialCount: Int,
        createdAt: Instant,
        lastUsedAt: Instant? = null,
        isBlocked: Boolean = false,
    ) : this(
        SensitiveText.copyOf(id.value),
        SensitiveText.copyOf(name),
        iconUrl,
        credentialCount,
        createdAt,
        lastUsedAt,
        isBlocked,
    )

    /** Compatibility/rendering RP value; internal operations borrow idText instead. */
    val id: RpId get() = RpId(idOwner.toBoundaryString())

    /** Compatibility/rendering name; internal operations borrow nameText instead. */
    val name: String get() = nameOwner.toBoundaryString()

    /** Borrows the live RP owner; callers must neither erase nor retain it beyond this model. */
    val idText: SensitiveText get() = idOwner

    /** Borrows the live display-name owner with the same lifetime as this model. */
    val nameText: SensitiveText get() = nameOwner

    init {
        try {
            validate()
        } catch (failure: IllegalArgumentException) {
            eraseSensitiveText()
            throw failure
        }
    }

    /** Erases metadata owned by this model, leaving separately copied models usable. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun eraseSensitiveText() {
        idOwner.erase()
        nameOwner.erase()
    }

    /** Deep-copies owned metadata; unchanged fields never pass through immutable text. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun copy(
        id: RpId? = null,
        name: String? = null,
        iconUrl: String? = this.iconUrl,
        credentialCount: Int = this.credentialCount,
        createdAt: Instant = this.createdAt,
        lastUsedAt: Instant? = this.lastUsedAt,
        isBlocked: Boolean = this.isBlocked,
    ): RelyingParty =
        RelyingParty(
            SensitiveText.copyOf(id?.value ?: idOwner),
            SensitiveText.copyOf(name ?: nameOwner),
            iconUrl,
            credentialCount,
            createdAt,
            lastUsedAt,
            isBlocked,
        )

    /** Compares model metadata by value without creating plaintext Strings. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun equals(other: Any?): Boolean =
        other is RelyingParty &&
            idOwner == other.idOwner &&
            nameOwner == other.nameOwner &&
            iconUrl == other.iconUrl &&
            credentialCount == other.credentialCount &&
            createdAt == other.createdAt &&
            lastUsedAt == other.lastUsedAt &&
            isBlocked == other.isBlocked

    /** Hashes the value fields used by equals. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun hashCode(): Int =
        listOf(
            idOwner,
            nameOwner,
            iconUrl,
            credentialCount,
            createdAt,
            lastUsedAt,
            isBlocked,
        ).hashCode()

    /** Redacts RP and display-name data in model diagnostics. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun toString(): String = "RelyingParty([sensitive metadata redacted])"

    /**
     * Validates the RelyingParty according to FIDO2 specifications.
     * Throws IllegalArgumentException if validation fails.
     */
    private fun validate() {
        // Validate required fields
        require(nameOwner.isNotBlank()) { "RP name cannot be blank" }

        // Validate formats
        require(isValidRpId(idOwner)) {
            "RP ID must be a valid domain or HTTPS origin"
        }
        require(nameOwner.length <= MAX_NAME_LENGTH) { "RP name cannot exceed $MAX_NAME_LENGTH characters" }
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
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun hasCredentials(): Boolean = credentialCount > 0

    /**
     * Returns the domain from the RP ID.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
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
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun isTrusted(trustedDomains: Set<String>): Boolean = trustedDomains.contains(getDomain())

    /**
     * Returns a safe name for display.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getSafeName(): String = name.ifBlank { getDomain() }

    /**
     * Creates a copy with updated credential count.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun withCredentialCount(newCount: Int): RelyingParty =
        copy(credentialCount = newCount, lastUsedAt = Clock.System.now())

    /**
     * Creates a copy with updated last used time.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun withLastUsedAt(newLastUsedAt: Instant): RelyingParty = copy(lastUsedAt = newLastUsedAt)

    /**
     * Returns the RP age in days.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getAgeInDays(): Long {
        val diff = Clock.System.now() - createdAt
        return diff.inWholeDays
    }

    /**
     * Checks if this RP has been used recently.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun isRecentlyUsed(days: Int = 30): Boolean {
        val cutoff = Clock.System.now() - kotlin.time.Duration.parse("${days}d")
        return lastUsedAt?.let { it > cutoff } ?: false
    }

    companion object {
        const val MAX_NAME_LENGTH = 64
        const val MAX_ICON_URL_LENGTH = 256
        private const val SCHEME_PREFIX_LENGTH = 3
        private const val HTTP_PREFIX_LENGTH = 7
        private const val HTTP_SCHEME_LENGTH = 4
        private const val HTTPS_SCHEME_LENGTH = 5
        private val FUTURE_GRACE = 1.minutes

        /** Consumes both text owners on entry; callers erase a returned model when its operation ends. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun fromOwnedText(
            id: SensitiveText,
            name: SensitiveText,
            iconUrl: String? = null,
            credentialCount: Int = 0,
            createdAt: Instant = Clock.System.now(),
            lastUsedAt: Instant? = null,
            isBlocked: Boolean = false,
        ): RelyingParty = RelyingParty(id, name, iconUrl, credentialCount, createdAt, lastUsedAt, isBlocked)

        /**
         * Creates a new RelyingParty with validation.
         */
        @Req("NFR-SEC-041", "TR-AUTH-031")
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

        /** Validates RP ID format without forcing borrowed mutable text into an immutable String. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun isValidRpId(rpId: CharSequence): Boolean {
            if (rpId.isBlank()) return false
            var schemeSeparator = -1
            for (index in 0 until (rpId.length - 2).coerceAtLeast(0)) {
                if (rpId[index] == ':' && rpId[index + 1] == '/' && rpId[index + 2] == '/') {
                    schemeSeparator = index
                    break
                }
            }
            if (schemeSeparator < 0) {
                // Bare domain: must contain a dot (e.g., example.com) or be localhost.
                return rpId.contentEquals("localhost") ||
                    rpId.startsWith("localhost:") ||
                    (rpId.contains('.') && !rpId.contains(' '))
            }

            // Check the supported scheme without copying the RP ID into a String.
            val isHttp =
                schemeSeparator == HTTP_SCHEME_LENGTH &&
                    "http".indices.all { index ->
                        rpId[index].equals("http"[index], ignoreCase = true)
                    }
            val isHttps =
                schemeSeparator == HTTPS_SCHEME_LENGTH &&
                    "https".indices.all { index ->
                        rpId[index].equals("https"[index], ignoreCase = true)
                    }
            if (!isHttp && !isHttps) return false
            val authorityStart = schemeSeparator + SCHEME_PREFIX_LENGTH
            var authorityEnd = rpId.length
            for (index in authorityStart until rpId.length) {
                if (rpId[index] == '/' || rpId[index] == '?' || rpId[index] == '#') {
                    authorityEnd = index
                    break
                }
            }
            if (authorityStart >= authorityEnd) return false
            return (authorityStart until authorityEnd).none { rpId[it].isWhitespace() }
        }

        /**
         * Normalizes RP ID to HTTPS format.
         */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun normalizeRpId(rpId: String): String {
            if (rpId.startsWith("http://localhost")) return rpId
            if (rpId.startsWith("http://")) return "https://" + rpId.substring(HTTP_PREFIX_LENGTH)
            if (rpId.startsWith("https://")) return rpId
            return "https://$rpId"
        }
    }
}

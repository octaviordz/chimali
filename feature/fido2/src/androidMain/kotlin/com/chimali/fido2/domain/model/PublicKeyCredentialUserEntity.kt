package com.chimali.fido2.domain.model

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.valueobject.UserId

/** Domain owner for user identity and account text received in a CTAP MakeCredential request. */
class PublicKeyCredentialUserEntity private constructor(
    private val idBytes: ByteArray,
    private val nameOwner: SensitiveCredentialText,
    private val displayNameOwner: SensitiveCredentialText,
    val icon: String?,
) {
    /** Copies borrowed compatibility values into mutable entity ownership. */
    constructor(
        id: UserId,
        name: String,
        displayName: String,
        icon: String?,
    ) : this(id.toByteArray(), SensitiveCredentialText.copyOf(name), SensitiveCredentialText.copyOf(displayName), icon)

    /** Creates the short-lived encoded ID required by current persistence and response APIs. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val id: UserId get() = UserId.fromByteArray(idBytes)

    /** Creates display text for the consent UI or persistence boundary. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val name: String get() = nameOwner.toBoundaryString()

    /** Creates display text for the consent UI or persistence boundary. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val displayName: String get() = displayNameOwner.toBoundaryString()

    init {
        try {
            validate()
        } catch (failure: IllegalArgumentException) {
            // Construction consumes these buffers; failed validation must erase all of them.
            idBytes.fill(0)
            nameOwner.erase()
            displayNameOwner.erase()
            throw failure
        }
    }

    /** Validates owned fields without embedding account values in diagnostics. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun validate() {
        require(idBytes.isNotEmpty()) { "User ID cannot be empty" }
        require(!nameOwner.isBlank()) { "User name cannot be blank" }
        require(!displayNameOwner.isBlank()) { "User display name cannot be blank" }
        require(nameOwner.length <= MAX_NAME_LENGTH) { "User name cannot exceed $MAX_NAME_LENGTH characters" }
        require(displayNameOwner.length <= MAX_DISPLAY_NAME_LENGTH) {
            "User display name cannot exceed $MAX_DISPLAY_NAME_LENGTH characters"
        }
        icon?.let { iconUrl ->
            require(iconUrl.isNotBlank()) { "Icon cannot be blank if provided" }
            require(iconUrl.length <= MAX_ICON_LENGTH) { "Icon cannot exceed $MAX_ICON_LENGTH characters" }
            require(iconUrl.startsWith("https://") || iconUrl.startsWith("http://")) {
                "Icon must use HTTP or HTTPS protocol"
            }
        }
    }

    /** Returns the user ID as a base64 URL-safe string for an API boundary. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getIdBase64Url(): String = id.value

    /** Returns a safe display name. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getSafeDisplayName(): String = if (displayNameOwner.length == 0) name else displayName

    /** Checks whether this user has an icon. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun hasIcon(): Boolean = icon?.isNotBlank() ?: false

    /** Returns the user ID as a hex value for a compatibility API and erases the temporary bytes. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getIdHex(): String {
        val bytes = idBytes.copyOf()
        return try {
            bytes.joinToString("") { "%02x".format(it) }
        } finally {
            bytes.fill(0)
        }
    }

    /** Erases the owned user ID and account text. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun eraseSensitiveText() {
        idBytes.fill(0)
        nameOwner.erase()
        displayNameOwner.erase()
    }

    /** Copies the account name into an independently erasable presenter owner. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun copyNameOwner(): SensitiveCredentialText = nameOwner.copyOwner()

    /** Copies the account display name into an independently erasable presenter owner. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun copyDisplayNameOwner(): SensitiveCredentialText = displayNameOwner.copyOwner()

    /** Copies the current user ID into its existing unpadded Base64URL representation. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun copyEncodedIdOwner(): SensitiveCredentialText = SensitiveCredentialText.fromBase64UrlBytes(idBytes)

    /** Creates a new independently owned entity with selected replacement fields. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun copy(
        id: UserId? = null,
        name: String? = null,
        displayName: String? = null,
        icon: String? = this.icon,
    ): PublicKeyCredentialUserEntity = copyWithOwnerFactories(id, name, displayName, icon)

    /** Copies through injectable factories so all partial/failed-copy owners can be retained and verified. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun copyWithOwnerFactories(
        id: UserId? = null,
        name: String? = null,
        displayName: String? = null,
        icon: String? = this.icon,
        byteFactory: (ByteArray) -> ByteArray = { it.copyOf() },
        ownerFactory: (CharSequence) -> SensitiveCredentialText = SensitiveCredentialText::copyOf,
    ): PublicKeyCredentialUserEntity {
        var createdId: ByteArray? = null
        var createdName: SensitiveCredentialText? = null
        var createdDisplayName: SensitiveCredentialText? = null
        try {
            createdId = id?.toByteArray() ?: byteFactory(idBytes)
            createdName = ownerFactory(name ?: nameOwner)
            createdDisplayName = ownerFactory(displayName ?: displayNameOwner)
            return PublicKeyCredentialUserEntity(
                requireNotNull(createdId),
                requireNotNull(createdName),
                requireNotNull(createdDisplayName),
                icon,
            ).also {
                createdId = null
                createdName = null
                createdDisplayName = null
            }
        } finally {
            createdId?.fill(0)
            createdName?.erase()
            createdDisplayName?.erase()
        }
    }

    /** Returns a fixed diagnostic summary without account text or identifier. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun toString(): String = "PublicKeyCredentialUserEntity([sensitive account fields redacted])"

    /** Compares owned values without materializing account text. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun equals(other: Any?): Boolean =
        other is PublicKeyCredentialUserEntity &&
            idBytes.contentEquals(other.idBytes) &&
            nameOwner == other.nameOwner &&
            displayNameOwner == other.displayNameOwner &&
            icon == other.icon

    /** Hashes owned values without materializing account text. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun hashCode(): Int {
        var result = idBytes.contentHashCode()
        result = 31 * result + nameOwner.hashCode()
        result = 31 * result + displayNameOwner.hashCode()
        return 31 * result + (icon?.hashCode() ?: 0)
    }

    companion object {
        const val MAX_USER_ID_LENGTH = 64
        const val MAX_NAME_LENGTH = 64
        const val MAX_DISPLAY_NAME_LENGTH = 64
        const val MAX_ICON_LENGTH = 128

        /** Creates a validated entity by copying caller-owned values. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun create(
            id: UserId,
            name: String,
            displayName: String,
            icon: String? = null,
        ): PublicKeyCredentialUserEntity = PublicKeyCredentialUserEntity(id, name, displayName, icon)

        /** Creates an entity from base64 user ID. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun fromBase64Id(
            idBase64: String,
            name: String,
            displayName: String,
            icon: String? = null,
        ): PublicKeyCredentialUserEntity = create(UserId(idBase64), name, displayName, icon)

        /**
         * Transfers decoded request owners into this entity without making text copies.
         * Ownership transfers on entry; validation failure erases the ID and both text owners.
         */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        internal fun fromSensitiveOwners(
            idBytes: ByteArray,
            name: SensitiveCredentialText,
            displayName: SensitiveCredentialText,
            icon: String? = null,
        ): PublicKeyCredentialUserEntity = PublicKeyCredentialUserEntity(idBytes, name, displayName, icon)
    }
}

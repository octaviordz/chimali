package com.chimali.fido2.domain.model

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.valueobject.RpId

/** Domain owner for the relying-party text received in a CTAP MakeCredential request. */
class PublicKeyCredentialRpEntity private constructor(
    private val idOwner: SensitiveCredentialText,
    private val nameOwner: SensitiveCredentialText,
    val icon: String?,
) {
    /** Copies text from a borrowed compatibility value into mutable entity ownership. */
    constructor(
        id: RpId,
        name: String,
        icon: String?,
    ) : this(SensitiveCredentialText.copyOf(id.value), SensitiveCredentialText.copyOf(name), icon)

    /** Produces the short-lived RP-ID value required by existing protocol and persistence APIs. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val id: RpId get() = idOwner.toRpId()

    /** Produces display text for the consent UI or a persistence boundary. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val name: String get() = nameOwner.toBoundaryString()

    /** Borrows the request RP ID until the request owner ends. */
    internal val idText: CharSequence get() = idOwner

    /** Borrows the request display name without creating application working Strings. */
    internal val nameText: CharSequence get() = nameOwner

    init {
        try {
            validate()
        } catch (failure: IllegalArgumentException) {
            // Construction consumes both owners; failed validation must not strand them.
            idOwner.erase()
            nameOwner.erase()
            throw failure
        }
    }

    /** Validates the owned request fields and reports only fixed diagnostic text. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun validate() {
        require(!nameOwner.isBlank()) { "RP name cannot be blank" }
        require(RelyingParty.isValidRpId(idOwner)) { "RP ID must be a valid domain or HTTPS origin" }
        require(nameOwner.length <= MAX_NAME_LENGTH) { "RP name cannot exceed $MAX_NAME_LENGTH characters" }
        icon?.let { iconUrl ->
            require(iconUrl.isNotBlank()) { "Icon cannot be blank if provided" }
            require(iconUrl.length <= MAX_ICON_LENGTH) { "Icon cannot exceed $MAX_ICON_LENGTH characters" }
        }
    }

    /** Returns the RP domain as a short-lived boundary value. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getDomain(): String = idOwner.toBoundaryString()

    /** Returns a safe name for display. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getSafeName(): String = if (nameOwner.length == 0) getDomain() else nameOwner.toBoundaryString()

    /** Erases the RP identifier and display name owned by this entity. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun eraseSensitiveText() {
        idOwner.erase()
        nameOwner.erase()
    }

    /** Copies the RP identifier into an independently erasable presenter owner. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun copyIdOwner(): SensitiveCredentialText = idOwner.copyOwner()

    /** Copies the RP display name into an independently erasable presenter owner. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun copyNameOwner(): SensitiveCredentialText = nameOwner.copyOwner()

    /** Creates a new independently owned entity with selected replacement fields. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun copy(
        id: RpId? = null,
        name: String? = null,
        icon: String? = this.icon,
    ): PublicKeyCredentialRpEntity = copyWithOwnerFactory(id, name, icon)

    /** Copies through an injectable owner factory so failed-copy cleanup can be retained and verified. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun copyWithOwnerFactory(
        id: RpId? = null,
        name: String? = null,
        icon: String? = this.icon,
        ownerFactory: (CharSequence) -> SensitiveCredentialText = SensitiveCredentialText::copyOf,
    ): PublicKeyCredentialRpEntity {
        var createdId: SensitiveCredentialText? = null
        var createdName: SensitiveCredentialText? = null
        try {
            createdId = ownerFactory(id?.value ?: idOwner)
            createdName = ownerFactory(name ?: nameOwner)
            return PublicKeyCredentialRpEntity(requireNotNull(createdId), requireNotNull(createdName), icon).also {
                createdId = null
                createdName = null
            }
        } finally {
            createdId?.erase()
            createdName?.erase()
        }
    }

    /** Returns a fixed diagnostic summary without relying-party text. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun toString(): String = "PublicKeyCredentialRpEntity([sensitive RP fields redacted])"

    /** Compares owned text without creating String copies. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun equals(other: Any?): Boolean =
        other is PublicKeyCredentialRpEntity &&
            idOwner == other.idOwner &&
            nameOwner == other.nameOwner &&
            icon == other.icon

    /** Hashes the owned text without creating String copies. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun hashCode(): Int = 31 * (31 * idOwner.hashCode() + nameOwner.hashCode()) + (icon?.hashCode() ?: 0)

    companion object {
        const val MAX_NAME_LENGTH = 64
        const val MAX_ICON_LENGTH = 128

        /** Creates a validated entity by copying borrowed caller text. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun create(
            id: RpId,
            name: String,
            icon: String? = null,
        ): PublicKeyCredentialRpEntity = PublicKeyCredentialRpEntity(id, name, icon)

        /**
         * Transfers the supplied mutable owners into this entity without making String copies.
         * Ownership transfers on entry; validation failure erases both supplied owners.
         */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        internal fun fromSensitiveOwners(
            id: SensitiveCredentialText,
            name: SensitiveCredentialText,
            icon: String? = null,
        ): PublicKeyCredentialRpEntity = PublicKeyCredentialRpEntity(id, name, icon)
    }
}

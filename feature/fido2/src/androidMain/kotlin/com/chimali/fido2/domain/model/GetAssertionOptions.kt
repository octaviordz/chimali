package com.chimali.fido2.domain.model

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.valueobject.RpId

/** Owned options for one CTAP authenticatorGetAssertion request. */
@Req("NFR-SEC-041", "TR-AUTH-031")
class GetAssertionOptions private constructor(
    private val rpIdOwner: SensitiveCredentialText,
    val clientDataHash: ByteArray,
    val allowCredentials: List<PublicKeyCredentialDescriptor>?,
    val userVerification: UserVerificationRequirement,
    val extensions: Map<String, Any>?,
    val timeout: Long?,
) {
    /** Copies a compatibility RP-ID value into mutable request ownership. */
    constructor(
        rpId: RpId,
        clientDataHash: ByteArray,
        allowCredentials: List<PublicKeyCredentialDescriptor>?,
        userVerification: UserVerificationRequirement = UserVerificationRequirement.PREFERRED,
        extensions: Map<String, Any>? = null,
        timeout: Long? = null,
    ) :
        this(
            SensitiveCredentialText.copyOf(rpId.value),
            clientDataHash,
            allowCredentials,
            userVerification,
            extensions,
            timeout,
        )

    /** Produces the RP-ID value required by existing service APIs. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    val rpId: RpId get() = rpIdOwner.toRpId()

    /** Borrows this request's RP text only for the live request lifetime. */
    internal val rpIdText: CharSequence get() = rpIdOwner

    init {
        try {
            validate()
        } catch (failure: IllegalArgumentException) {
            // The private constructor owns this buffer from entry, including when a
            // caller transferred it through fromSensitiveRpId.
            rpIdOwner.erase()
            throw failure
        }
    }

    private fun validate() {
        require(RelyingParty.isValidRpId(rpIdOwner)) {
            "Invalid RP ID"
        }
        require(clientDataHash.size == CLIENT_DATA_HASH_SIZE) {
            "clientDataHash must be 32 bytes (SHA-256), got ${clientDataHash.size}"
        }
        allowCredentials?.let { list ->
            require(list.size <= MAX_ALLOW_CREDENTIALS) { "allowCredentials cannot exceed 32 items" }
        }
        timeout?.let { require(it >= 0) { "Timeout cannot be negative" } }
    }

    /** Returns a safe timeout value. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun getSafeTimeout(): Long = timeout?.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS) ?: DEFAULT_TIMEOUT_MS

    /** True when no credential ID list is provided. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun isDiscoverableFlow(): Boolean = allowCredentials.isNullOrEmpty()

    /** Erases the request-owned RP identifier. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun eraseSensitiveText() = rpIdOwner.erase()

    /** Copies the RP identifier into an independently erasable presenter owner. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun copyRpIdOwner(): SensitiveCredentialText = rpIdOwner.copyOwner()

    /** Copies option references and creates independent ownership for the sensitive RP ID. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun copy(
        rpId: RpId? = null,
        clientDataHash: ByteArray = this.clientDataHash,
        allowCredentials: List<PublicKeyCredentialDescriptor>? = this.allowCredentials,
        userVerification: UserVerificationRequirement = this.userVerification,
        extensions: Map<String, Any>? = this.extensions,
        timeout: Long? = this.timeout,
    ): GetAssertionOptions =
        copyWithOwnerFactory(rpId, clientDataHash, allowCredentials, userVerification, extensions, timeout)

    /** Copies through an injectable owner factory so failure cleanup can be retained and verified. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun copyWithOwnerFactory(
        rpId: RpId? = null,
        clientDataHash: ByteArray = this.clientDataHash,
        allowCredentials: List<PublicKeyCredentialDescriptor>? = this.allowCredentials,
        userVerification: UserVerificationRequirement = this.userVerification,
        extensions: Map<String, Any>? = this.extensions,
        timeout: Long? = this.timeout,
        ownerFactory: (CharSequence) -> SensitiveCredentialText = SensitiveCredentialText::copyOf,
        sourceOwnerFactory: (SensitiveCredentialText) -> SensitiveCredentialText = SensitiveCredentialText::copyOwner,
    ): GetAssertionOptions {
        var createdOwner: SensitiveCredentialText? = null
        try {
            createdOwner = rpId?.let { ownerFactory(it.value) } ?: sourceOwnerFactory(rpIdOwner)
            return GetAssertionOptions(
                requireNotNull(createdOwner),
                clientDataHash,
                allowCredentials,
                userVerification,
                extensions,
                timeout,
            ).also { createdOwner = null }
        } finally {
            createdOwner?.erase()
        }
    }

    /** Returns a fixed diagnostic summary without the relying-party identifier. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun toString(): String = "GetAssertionOptions([sensitive request fields redacted])"

    /** Compares owned request text without creating String copies. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun equals(other: Any?): Boolean =
        other is GetAssertionOptions &&
            rpIdOwner == other.rpIdOwner &&
            clientDataHash.contentEquals(other.clientDataHash) &&
            allowCredentials == other.allowCredentials &&
            userVerification == other.userVerification &&
            extensions == other.extensions &&
            timeout == other.timeout

    /** Hashes owned request text without creating String copies. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun hashCode(): Int {
        var result = rpIdOwner.hashCode()
        result = 31 * result + clientDataHash.contentHashCode()
        result = 31 * result + (allowCredentials?.hashCode() ?: 0)
        result = 31 * result + userVerification.hashCode()
        result = 31 * result + (extensions?.hashCode() ?: 0)
        return 31 * result + (timeout?.hashCode() ?: 0)
    }

    companion object {
        private const val CLIENT_DATA_HASH_SIZE = 32
        private const val MAX_ALLOW_CREDENTIALS = 32
        const val MIN_TIMEOUT_MS = 30000L
        const val MAX_TIMEOUT_MS = 600000L
        const val DEFAULT_TIMEOUT_MS = 120000L

        /**
         * Creates options while transferring an RP-ID owner from a decoded CTAP request.
         * Ownership transfers on entry; validation failure erases the supplied owner.
         */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        internal fun fromSensitiveRpId(
            rpId: SensitiveCredentialText,
            clientDataHash: ByteArray,
            allowCredentials: List<PublicKeyCredentialDescriptor>? = null,
            userVerification: UserVerificationRequirement = UserVerificationRequirement.PREFERRED,
            extensions: Map<String, Any>? = null,
            timeout: Long? = null,
        ) = GetAssertionOptions(rpId, clientDataHash, allowCredentials, userVerification, extensions, timeout)

        /** Quick factory for tests and internal callers. */
        @Req("NFR-SEC-041", "TR-AUTH-031")
        fun create(
            rpId: RpId,
            clientDataHash: ByteArray,
            allowCredentials: List<PublicKeyCredentialDescriptor>? = null,
            userVerification: UserVerificationRequirement = UserVerificationRequirement.PREFERRED,
            extensions: Map<String, Any>? = null,
            timeout: Long? = null,
        ) = GetAssertionOptions(rpId, clientDataHash, allowCredentials, userVerification, extensions, timeout)
    }
}

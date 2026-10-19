package com.chimali.core.security.api

import com.chimali.core.common.annotation.Req

/** Lifetime class for a bounded operation owner; foreground loss only cancels foreground work. */
enum class MasterSeedOwnerKind {
    BOUNDED,
    FOREGROUND,
    AUTHENTICATOR,
}

/**
 * Provides owned operation copies of the current wallet's derivation material.
 *
 * A ready provider session caches at most one 32-byte root PRK and one P-256 parent key pair.
 * Every getter returns separate caller-owned storage. Callers erase private copies in `finally`;
 * no PQ child seed or per-credential secret is cached by this provider.
 */
interface MasterSeedProvider {
    /**
     * Runs one bounded operation under a provider-tracked owner. Implementations must bind the
     * owner to the calling coroutine, cancel it when its generation is fenced, and release it in
     * `finally`; when the last current-generation owner exits, cached root material is erased.
     * Set [initialize] to false for an authorized operation such as mnemonic display that reads
     * encrypted recovery material without deriving the wallet root.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun <T> withSessionOwner(
        initialize: Boolean = true,
        ownerKind: MasterSeedOwnerKind = MasterSeedOwnerKind.BOUNDED,
        operation: suspend (generation: Long) -> T,
    ): T

    /** Cancels active owners of [ownerKind] without fencing unrelated owners or generations. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun cancelSessionOwners(ownerKind: MasterSeedOwnerKind)

    /** Current cache generation, changed whenever the active wallet session is fenced. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun sessionGeneration(): Long

    /** Returns whether work admitted in [generation] may still publish a result. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun isSessionGenerationCurrent(generation: Long): Boolean

    /** Fences the active generation and erases cached root/parent material and derived metadata keys. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun clearSession()

    /**
     * Returns an owned 32-byte RFC 5869 root PRK copy, or `null` if initialization is unavailable.
     * The caller must erase this array after its operation; it never aliases the provider cache.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getMasterSeed(): ByteArray?

    /** Returns an owned parent key-pair copy, or `null` if initialization is unavailable. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getDeviceKeyPair(): HdkKeyPair?

    /**
     * Returns the current English mnemonic in an owned mutable space-separated character buffer,
     * or `null` when no wallet is provisioned. The caller must erase the returned buffer.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun getMnemonic(): CharArray?

    /**
     * Imports/replaces the wallet mnemonic using this operation-owned buffer. The provider always
     * erases [mnemonic] on success, failure, or cancellation before returning/throwing.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun importMnemonic(mnemonic: CharArray): ImportMnemonicResult
}

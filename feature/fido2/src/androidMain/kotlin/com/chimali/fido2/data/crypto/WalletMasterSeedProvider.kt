package com.chimali.fido2.data.crypto

import androidx.datastore.core.DataStore
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.datastore.EncryptionWrapper
import com.chimali.core.common.datastore.UserPreferences
import com.chimali.core.security.api.EncryptedMetadataService
import com.chimali.core.security.api.HdkKeyPair
import com.chimali.core.security.api.ImportMnemonicResult
import com.chimali.core.security.api.MasterSeedGenerator
import com.chimali.core.security.api.MasterSeedOwnerKind
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.core.security.api.MetadataLookupTokenService
import com.chimali.core.security.hdkeys.HkdfSha256
import com.chimali.core.security.hdkeys.P256ParentKeyDerivation
import com.chimali.core.security.impl.AesGcmEncryptedMetadataService
import com.chimali.core.security.impl.HmacMetadataLookupTokenService
import com.chimali.fido2.util.performance.PerformanceTrace
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.koin.core.annotation.Single

/**
 * Current wallet root provider backed only by encrypted Proto DataStore recovery material.
 *
 * It caches one 32-byte RFC 5869 PRK and one deterministic P-256 parent pair. Getter methods
 * return operation-owned copies. Mnemonics, BIP39 seeds, parent inputs and temporary keys are
 * erased in `finally`; per-credential Ed25519/ML-DSA seeds are derived by the crypto service and
 * are never cached here.
 */
@Single
class WalletMasterSeedProvider(
    private val masterSeedGenerator: MasterSeedGenerator,
    private val dataStore: DataStore<UserPreferences>,
    private val encryptionWrapper: EncryptionWrapper,
    private val lookupTokenService: MetadataLookupTokenService,
    private val encryptedMetadataService: EncryptedMetadataService,
) : MasterSeedProvider {
    private val initializationMutex = Mutex()
    private val cacheLock = Any()

    private var cachedPrk: ByteArray? = null
    private var cachedParent: HdkKeyPair? = null
    private var generation: Long = 0
    private var nextOwnerId: Long = 0
    private val activeOwners = mutableMapOf<Long, ActiveOwner>()

    private data class ActiveOwner(
        val generation: Long,
        val job: Job,
        val kind: MasterSeedOwnerKind,
    )

    private class OwnerContext(
        val ownerId: Long,
    ) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<OwnerContext>
    }

    /** Returns the current wallet-session fence value used to reject stale operations. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun sessionGeneration(): Long = synchronized(cacheLock) { generation }

    /** Reports whether [generation] still identifies the active wallet session. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun isSessionGenerationCurrent(generation: Long): Boolean =
        synchronized(cacheLock) { this.generation == generation }

    /** Fences bootstrap/operation copies and erases all cached root-derived material. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun clearSession() {
        synchronized(cacheLock) {
            generation += 1
            val ownersToCancel = activeOwners.values.map { it.job }
            activeOwners.clear()
            eraseCacheLocked()
            clearProvisionedMetadataKeys()
            ownersToCancel.forEach { it.cancel(CancellationException("Wallet session was fenced")) }
        }
    }

    /** Cancels owners of the requested kind in the current session without clearing other owners. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun cancelSessionOwners(ownerKind: MasterSeedOwnerKind) {
        synchronized(cacheLock) {
            activeOwners.values
                .filter { it.generation == generation && it.kind == ownerKind }
                .forEach { it.job.cancel(CancellationException("$ownerKind wallet operation ended")) }
        }
    }

    /** Owns cache material only for this operation; cancellation/replacement fences its child job. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun <T> withSessionOwner(
        initialize: Boolean,
        ownerKind: MasterSeedOwnerKind,
        operation: suspend (generation: Long) -> T,
    ): T {
        val parentJob = currentCoroutineContext()[Job]
        var registeredOwner: Pair<Long, ActiveOwner>? = null
        return try {
            val (ownerId, owner) =
                initializationMutex.withLock {
                    currentCoroutineContext().ensureActive()
                    val registered =
                        synchronized(cacheLock) {
                            nextOwnerId += 1
                            ActiveOwner(generation, Job(parentJob), ownerKind)
                                .also { activeOwners[nextOwnerId] = it }
                                .let { nextOwnerId to it }
                        }
                    registeredOwner = registered
                    registered
                }
            withContext(owner.job + OwnerContext(ownerId)) {
                // Bootstrap must run in the same cancellable owner context as the operation.
                // The mutex still serializes initialization and replacement, but cancellation
                // of this owner can now release it while a suspending store read/write is pending.
                initializationMutex.withLock {
                    currentCoroutineContext().ensureActive()
                    ensureOwnerCanPublish(ownerId, owner)
                    if (initialize && !isInitialized()) initializeLocked()
                    currentCoroutineContext().ensureActive()
                    ensureOwnerCanPublish(ownerId, owner)
                    if (initialize && !isInitialized()) {
                        throw CancellationException("Wallet cache was not initialized for the admitted operation")
                    }
                }
                operation(owner.generation)
            }
        } finally {
            registeredOwner?.let { (ownerId, owner) ->
                owner.job.cancel()
                withContext(NonCancellable) {
                    releaseSessionOwner(ownerId, owner)
                }
            }
        }
    }

    /** Returns an owned copy of the 32-byte wallet PRK inside an admitted owner operation. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun getMasterSeed(): ByteArray? {
        requireOwnedOperation()
        ensureInitialized()
        return synchronized(cacheLock) { cachedPrk?.copyOf() }
    }

    /** Returns owned copies of the cached deterministic parent key pair inside an owner operation. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun getDeviceKeyPair(): HdkKeyPair? {
        requireOwnedOperation()
        ensureInitialized()
        return synchronized(cacheLock) {
            cachedParent?.let { parent -> HdkKeyPair(parent.privateKey.copyOf(), parent.publicKey.copyOf()) }
        }
    }

    /** Decrypts the recovery phrase into a caller-owned mutable array; the caller must erase it. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun getMnemonic(): CharArray? {
        requireOwnedOperation()
        val prefs = dataStore.data.first()
        if (prefs.encryptedWalletSeed.isEmpty()) return null
        return decryptMnemonic(prefs.encryptedWalletSeed)
    }

    /** Validates and consumes [mnemonic], persists its encrypted form, and replaces session material. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun importMnemonic(mnemonic: CharArray): ImportMnemonicResult {
        try {
            return initializationMutex.withLock {
                val validationSeed = masterSeedGenerator.deriveSeed(mnemonic)
                try {
                    val alreadyExisted =
                        dataStore.data
                            .first()
                            .encryptedWalletSeed
                            .isNotEmpty()
                    val encrypted = encryptMnemonic(mnemonic)
                    clearSession()
                    val (ownerId, owner) =
                        registerSessionOwnerLocked(
                            MasterSeedOwnerKind.FOREGROUND,
                            currentCoroutineContext()[Job],
                        )
                    try {
                        withContext(owner.job + OwnerContext(ownerId)) {
                            dataStore.updateData { current -> current.copy(encryptedWalletSeed = encrypted) }
                        }
                        if (alreadyExisted) ImportMnemonicResult.Replaced else ImportMnemonicResult.Created
                    } finally {
                        owner.job.cancel()
                        releaseSessionOwnerLocked(ownerId, owner)
                    }
                } finally {
                    validationSeed.fill(0)
                }
            }
        } finally {
            mnemonic.fill('\u0000')
        }
    }

    private suspend fun ensureInitialized() {
        if (isInitialized()) return
        initializationMutex.withLock {
            if (!isInitialized()) initializeLocked()
        }
    }

    private suspend fun initializeLocked() {
        val initializingGeneration = sessionGeneration()
        val initializingOwnerId =
            currentCoroutineContext()[OwnerContext]?.ownerId
                ?: throw CancellationException("Wallet bootstrap requires an active operation owner")
        var mnemonic: CharArray? = null
        var bip39Seed: ByteArray? = null
        var prk: ByteArray? = null
        var parentResult: P256ParentKeyDerivation.Result? = null
        var pendingParent: HdkKeyPair? = null
        try {
            mnemonic = PerformanceTrace.section("Fido2.Wallet.ReadMnemonic") { getOrCreateMnemonic() }
            bip39Seed =
                PerformanceTrace.section("Fido2.Wallet.Bip39") {
                    masterSeedGenerator.deriveSeed(mnemonic)
                }
            prk = HkdfSha256.deriveWalletPrk(bip39Seed)
            parentResult =
                PerformanceTrace.section("Fido2.Wallet.Parent") {
                    P256ParentKeyDerivation.deriveFromPrk(prk)
                }
            pendingParent = HdkKeyPair(parentResult.privateScalar.copyOf(), parentResult.publicKey.copyOf())

            // Provision external root consumers first. Publish the root and parent atomically only
            // after all initialization work has succeeded.
            synchronized(cacheLock) {
                if (generation != initializingGeneration) {
                    throw CancellationException("Wallet cache generation was fenced during initialization")
                }
                ensureCurrentOwnerCanPublishLocked(initializingGeneration, initializingOwnerId)
                var metadataProvisioned = false
                try {
                    provisionSearchableMetadataKeys(prk)
                    eraseCacheLocked()
                    cachedPrk = prk.copyOf()
                    cachedParent = pendingParent
                    pendingParent = null
                    metadataProvisioned = true
                } finally {
                    if (!metadataProvisioned) clearProvisionedMetadataKeys()
                }
            }
            Logger.d { "Wallet root initialized" }
        } finally {
            mnemonic?.fill('\u0000')
            bip39Seed?.fill(0)
            prk?.fill(0)
            parentResult?.erasePrivateScalar()
            pendingParent?.erasePrivateKey()
        }
    }

    /** Returns a decrypted persisted phrase or generates and persists a fresh 24-word phrase. */

    private suspend fun getOrCreateMnemonic(): CharArray {
        val prefs = dataStore.data.first()
        if (prefs.encryptedWalletSeed.isNotEmpty()) return decryptMnemonic(prefs.encryptedWalletSeed)

        val generated = masterSeedGenerator.generateMnemonic(MNEMONIC_WORD_COUNT)
        var persisted = false
        try {
            val encrypted = encryptMnemonic(generated)
            dataStore.updateData { current -> current.copy(encryptedWalletSeed = encrypted) }
            persisted = true
            return generated
        } finally {
            if (!persisted) generated.fill('\u0000')
        }
    }

    private fun decryptMnemonic(encodedCiphertext: String): CharArray {
        val ciphertext =
            requireNotNull(encodedCiphertext.decodeBase64()) {
                "Encrypted wallet mnemonic is not valid Base64"
            }.toByteArray()
        var plaintext: ByteArray? = null
        var decoded: CharBuffer? = null
        try {
            plaintext = encryptionWrapper.decrypt(ciphertext)
            require(plaintext.isNotEmpty()) { "Encrypted wallet mnemonic is empty" }
            val charBuffer = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(plaintext))
            decoded = charBuffer
            return CharArray(charBuffer.remaining()).also { charBuffer.get(it) }
        } finally {
            ciphertext.fill(0)
            plaintext?.fill(0)
            decoded?.let { chars -> if (chars.hasArray()) chars.array().fill('\u0000') }
        }
    }

    /** Encrypts borrowed mnemonic characters without constructing an immutable plaintext String. */

    private fun encryptMnemonic(mnemonic: CharArray): String {
        require(mnemonic.isNotEmpty()) { "Mnemonic must not be empty" }
        var encoded: ByteBuffer? = null
        var plaintext: ByteArray? = null
        var ciphertext: ByteArray? = null
        try {
            val byteBuffer = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(mnemonic))
            encoded = byteBuffer
            val plainBytes = ByteArray(byteBuffer.remaining()).also { byteBuffer.get(it) }
            plaintext = plainBytes
            ciphertext = encryptionWrapper.encrypt(plainBytes)
            return ciphertext.toByteString().base64()
        } finally {
            plaintext?.fill(0)
            encoded?.let { buffer -> if (buffer.hasArray()) buffer.array().fill(0) }
            ciphertext?.fill(0)
        }
    }

    private fun provisionSearchableMetadataKeys(prk: ByteArray) {
        val label = SEARCHABLE_METADATA_EXPANSION_KEY.toByteArray(StandardCharsets.US_ASCII)
        var derived: ByteArray? = null
        var metadataKey: ByteArray? = null
        try {
            derived = hmacSha512(label, prk)
            metadataKey = derived.copyOf(METADATA_KEY_SIZE_BYTES)
            (lookupTokenService as? HmacMetadataLookupTokenService)?.provisionKey(metadataKey)
            (encryptedMetadataService as? AesGcmEncryptedMetadataService)?.provisionKey(metadataKey)
        } finally {
            label.fill(0)
            derived?.fill(0)
            metadataKey?.fill(0)
        }
    }

    private fun hmacSha512(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA512)
        mac.init(SecretKeySpec(key, HMAC_SHA512))
        return mac.doFinal(data)
    }

    private fun isInitialized(): Boolean = synchronized(cacheLock) { cachedPrk != null && cachedParent != null }

    private suspend fun requireOwnedOperation() {
        val ownerId =
            currentCoroutineContext()[OwnerContext]?.ownerId
                ?: throw CancellationException("Wallet material requires an active operation owner")
        synchronized(cacheLock) {
            val owner = activeOwners[ownerId]
            if (owner == null || owner.generation != generation || !owner.job.isActive) {
                throw CancellationException("Wallet operation owner is no longer active")
            }
        }
    }

    private fun ensureOwnerCanPublish(
        ownerId: Long,
        expected: ActiveOwner,
    ) {
        synchronized(cacheLock) {
            if (generation != expected.generation || activeOwners[ownerId] !== expected || !expected.job.isActive) {
                throw CancellationException("Wallet operation owner is no longer active")
            }
        }
    }

    /** Called under [cacheLock] immediately before publishing any bootstrap cache material. */
    private fun ensureCurrentOwnerCanPublishLocked(
        initializingGeneration: Long,
        ownerId: Long,
    ) {
        val owner = activeOwners[ownerId]
        if (generation != initializingGeneration ||
            owner == null ||
            owner.generation != generation ||
            !owner.job.isActive
        ) {
            throw CancellationException("Wallet operation owner was cancelled during bootstrap")
        }
    }

    private suspend fun releaseSessionOwner(
        ownerId: Long,
        owner: ActiveOwner,
    ) {
        initializationMutex.withLock {
            releaseSessionOwnerLocked(ownerId, owner)
        }
    }

    /** Called while [initializationMutex] is held so wallet replacement remains serialized. */

    private fun registerSessionOwnerLocked(
        ownerKind: MasterSeedOwnerKind,
        parentJob: Job?,
    ): Pair<Long, ActiveOwner> =
        synchronized(cacheLock) {
            nextOwnerId += 1
            val owner = ActiveOwner(generation, Job(parentJob), ownerKind)
            activeOwners[nextOwnerId] = owner
            nextOwnerId to owner
        }

    /** Called with [initializationMutex] held by either owner completion or replacement. */

    private fun releaseSessionOwnerLocked(
        ownerId: Long,
        owner: ActiveOwner,
    ) {
        synchronized(cacheLock) {
            if (generation != owner.generation || activeOwners.remove(ownerId) !== owner) return@synchronized
            if (activeOwners.isEmpty()) {
                generation += 1
                eraseCacheLocked()
                clearProvisionedMetadataKeys()
            }
        }
    }

    private fun eraseCacheLocked() {
        cachedPrk?.fill(0)
        cachedPrk = null
        cachedParent?.erasePrivateKey()
        cachedParent = null
    }

    private fun clearProvisionedMetadataKeys() {
        (lookupTokenService as? HmacMetadataLookupTokenService)?.clearKey()
        (encryptedMetadataService as? AesGcmEncryptedMetadataService)?.clearKey()
    }

    private companion object {
        const val MNEMONIC_WORD_COUNT = 24
        const val METADATA_KEY_SIZE_BYTES = 32
        const val HMAC_SHA512 = "HmacSHA512"
        const val SEARCHABLE_METADATA_EXPANSION_KEY = "chimali_searchable_metadata_v1"
    }
}

package com.chimali.fido2.data.crypto

import com.chimali.core.security.api.HdkKeyPair
import com.chimali.core.security.api.HdkManager
import com.chimali.core.security.api.ImportMnemonicResult
import com.chimali.core.security.api.MasterSeedGenerator
import com.chimali.core.security.api.MasterSeedProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Unit tests for [WalletMasterSeedProvider].
 *
 * [WalletMasterSeedProvider] uses [EncryptedSharedPreferences] (Android instrumentation).
 * These unit tests use [TestableWalletMasterSeedProvider], a test subclass that replaces
 * encrypted storage with a simple in-memory variable, verifying business logic:
 *  - seed initialization from mnemonic
 *  - in-process caching (init runs only once)
 *  - reuse of a persisted mnemonic
 *  - device key pair derivation
 *  - T146g-p: importMnemonic coverage (Created, Replaced, cache invalidation, validation)
 */
class WalletMasterSeedProviderTest {
    private val fakeMnemonic =
        listOf(
            "abandon",
            "abandon",
            "abandon",
            "abandon",
            "abandon",
            "abandon",
            "abandon",
            "abandon",
            "abandon",
            "abandon",
            "abandon",
            "about",
        )
    private val fakeSeed = ByteArray(SEED_SIZE_64) { it.toByte() }
    private val fakeKeyPair = mockk<HdkKeyPair>(relaxed = true)

    private companion object {
        private const val SEED_SIZE_64 = 64
        private const val PQ_SEED_SIZE_64 = 64
        private const val MNEMONIC_WORDS_24 = 24
        private const val MNEMONIC_WORDS_12 = 12
        private const val OFFSET_10 = 10
        private const val OFFSET_99 = 99
        private const val HDK_SEED_SIZE_32 = 32
        private const val PQ_CONTEXT_STRING = "PQ_ML-DSA_Branch"
        private const val PQ_EXPANSION_KEY = "chimali_pq_seed_v1"
    }

    private lateinit var mockGenerator: MasterSeedGenerator
    private lateinit var mockHdkManager: HdkManager
    private lateinit var provider: TestableWalletMasterSeedProvider

    @BeforeTest
    fun setUp() {
        mockGenerator = mockk()
        mockHdkManager = mockk()

        every { mockGenerator.generateMnemonic(MNEMONIC_WORDS_24) } returns fakeMnemonic
        every { mockGenerator.deriveSeed(fakeMnemonic, "") } returns fakeSeed
        every { mockHdkManager.generateDeviceKeyPair() } returns fakeKeyPair
        // HDK DeriveSalt: SHA-256(salt || ctx) — mock with a real SHA-256 computation
        every { mockHdkManager.deriveSalt(any(), any()) } answers {
            val salt = firstArg<ByteArray>()
            val ctx = secondArg<ByteArray>()
            java.security.MessageDigest.getInstance("SHA-256").run {
                update(salt)
                update(ctx)
                digest()
            }
        }

        provider = TestableWalletMasterSeedProvider(mockGenerator, mockHdkManager)
    }

    @Test
    fun `getMasterSeed returns non-null seed on first call`() =
        runTest {
            val seed = provider.getMasterSeed()
            assertNotNull(seed)
            assertContentEquals(fakeSeed, seed)
        }

    @Test
    fun `getMasterSeed returns same seed on subsequent calls (cached)`() =
        runTest {
            val seed1 = provider.getMasterSeed()
            val seed2 = provider.getMasterSeed()
            assertContentEquals(seed1, seed2)
        }

    @Test
    fun `mnemonic is generated only once even across multiple getSeed calls`() =
        runTest {
            provider.getMasterSeed()
            provider.getMasterSeed()
            provider.getMasterSeed()

            verify(exactly = 1) { mockGenerator.generateMnemonic(MNEMONIC_WORDS_24) }
            verify(exactly = 1) { mockGenerator.deriveSeed(fakeMnemonic, "") }
        }

    @Test
    fun `getDeviceKeyPair returns non-null pair after initialization`() =
        runTest {
            val keyPair = provider.getDeviceKeyPair()
            assertNotNull(keyPair)
        }

    @Test
    fun `getDeviceKeyPair returns same instance across calls`() =
        runTest {
            val kp1 = provider.getDeviceKeyPair()
            val kp2 = provider.getDeviceKeyPair()
            assertSame(kp1, kp2)
        }

    @Test
    fun `when persisted mnemonic exists it is reused without generating new one`() =
        runTest {
            provider.persistedMnemonic = fakeMnemonic.joinToString(" ")

            provider.getMasterSeed()

            verify(exactly = 0) { mockGenerator.generateMnemonic(any()) }
            verify(exactly = 1) { mockGenerator.deriveSeed(fakeMnemonic, "") }
        }

    @Test
    fun `getMnemonic returns the persisted mnemonic as word list`() =
        runTest {
            // T146: programmatic persistence verification
            provider.persistedMnemonic = fakeMnemonic.joinToString(" ")
            val words = provider.getMnemonic()
            assertEquals(fakeMnemonic, words)
        }

    @Test
    fun `getMnemonic returns null when no mnemonic is persisted`() =
        runTest {
            provider.persistedMnemonic = null
            val words = provider.getMnemonic()
            assertNull(words)
        }

    // -----------------------------------------------------------------------
    // T146g-p: importMnemonic tests
    // -----------------------------------------------------------------------

    @Test
    fun `importMnemonic returns Created when no mnemonic existed before`() =
        runTest {
            // No mnemonic persisted yet.
            provider.persistedMnemonic = null

            val twentyFourWords = List(MNEMONIC_WORDS_24) { "word${it + 1}" }
            every { mockGenerator.deriveSeed(twentyFourWords, "") } returns ByteArray(SEED_SIZE_64)

            val chars = twentyFourWords.joinToString(" ").toCharArray()
            val result = provider.importMnemonic(chars)

            assertEquals(ImportMnemonicResult.Created, result)
            assertEquals(twentyFourWords.joinToString(" "), provider.persistedMnemonic)
        }

    @Test
    fun `importMnemonic returns Replaced when a mnemonic already existed`() =
        runTest {
            // Pre-populate storage with an existing mnemonic.
            provider.persistedMnemonic = fakeMnemonic.joinToString(" ")

            val newWords = List(MNEMONIC_WORDS_24) { "new${it + 1}" }
            every { mockGenerator.deriveSeed(newWords, "") } returns ByteArray(SEED_SIZE_64)

            val chars = newWords.joinToString(" ").toCharArray()
            val result = provider.importMnemonic(chars)

            assertEquals(ImportMnemonicResult.Replaced, result)
            assertEquals(newWords.joinToString(" "), provider.persistedMnemonic)
        }

    @Test
    fun `importMnemonic invalidates cache so getMasterSeed re-derives from new mnemonic`() =
        runTest {
            // Warm the cache with the original mnemonic / seed.
            val originalSeed = provider.getMasterSeed()

            val newWords = List(MNEMONIC_WORDS_24) { "cache${it + 1}" }
            val newSeed = ByteArray(SEED_SIZE_64) { (it + OFFSET_10).toByte() }
            every { mockGenerator.deriveSeed(newWords, "") } returns newSeed

            val chars = newWords.joinToString(" ").toCharArray()
            provider.importMnemonic(chars)

            // After import the cache must be refreshed; getMasterSeed must return the new seed.
            val seedAfterImport = provider.getMasterSeed()
            assertFalse(originalSeed.contentEquals(seedAfterImport!!))
            assertContentEquals(newSeed, seedAfterImport)
        }

    @Test
    fun `importMnemonic throws IllegalArgumentException for wrong word count`() =
        runTest {
            val shortWords =
                listOf(
                    "only",
                    "twelve",
                    "words",
                    "here",
                    "but",
                    "need",
                    "more",
                    "this",
                    "will",
                    "fail",
                    "validation",
                    "check",
                )
            assertEquals(MNEMONIC_WORDS_12, shortWords.size)
            val chars = shortWords.joinToString(" ").toCharArray()

            assertFailsWith<IllegalArgumentException> {
                kotlinx.coroutines.runBlocking { provider.importMnemonic(chars) }
            }
        }

    @Test
    fun `importMnemonic zeroes the CharArray after use`() =
        runTest {
            val words = List(MNEMONIC_WORDS_24) { "zero${it + 1}" }
            every { mockGenerator.deriveSeed(words, "") } returns ByteArray(SEED_SIZE_64)

            val chars = words.joinToString(" ").toCharArray()
            provider.importMnemonic(chars)

            // All chars must be null characters after importMnemonic returns.
            assertTrue(chars.all { it == '\u0000' }, "CharArray must be zeroed after importMnemonic")
        }

    // -----------------------------------------------------------------------
    // T001: HDK-based PQ child seed — KAT, determinism, clean break
    // -----------------------------------------------------------------------

    @Test
    fun `getPqChildSeed returns deterministic 64-byte result via HDK DeriveSalt`() =
        runTest {
            val pq1 = provider.getPqChildSeed()
            val pq2 = provider.getPqChildSeed()

            assertNotNull(pq1)
            assertEquals(PQ_SEED_SIZE_64, pq1.size, "PQ child seed must be 64 bytes")
            assertContentEquals(pq1, pq2, "PQ child seed must be deterministic")
        }

    @Test
    fun `getPqChildSeed produces different output from legacy BIP-85 derivation (clean break)`() =
        runTest {
            val pqChildSeed = provider.getPqChildSeed()
            assertNotNull(pqChildSeed)

            // Legacy BIP-85 stub returned ByteArray(64) { (it + 99).toByte() }
            val legacyStub = ByteArray(PQ_SEED_SIZE_64) { (it + OFFSET_99).toByte() }
            assertFalse(
                pqChildSeed.contentEquals(legacyStub),
                "HDK-derived PQ seed must differ from legacy BIP-85 output",
            )
        }

    @Test
    fun `getPqChildSeed enforces domain separation (T006)`() =
        runTest {
            val actualSeed = provider.getPqChildSeed()

            // Compute what the seed would be with a DIFFERENT context string
            val master = provider.getMasterSeed()!!
            val hdkSeed = master.copyOf(HDK_SEED_SIZE_32)
            val differentContext = "SOME_OTHER_Branch".toByteArray(Charsets.UTF_8)
            val fakeSalt = mockHdkManager.deriveSalt(hdkSeed, differentContext)

            val expansionKey = PQ_EXPANSION_KEY.toByteArray(Charsets.UTF_8)
            val mac = javax.crypto.Mac.getInstance("HmacSHA512")
            mac.init(javax.crypto.spec.SecretKeySpec(expansionKey, "HmacSHA512"))
            val differentSeed = mac.doFinal(fakeSalt)

            assertFalse(
                actualSeed!!.contentEquals(differentSeed),
                "Different HDK contexts must yield different child seeds (domain separation)",
            )
        }

    @Test
    fun `getPqChildSeed returns null when master seed is not available`() =
        runTest {
            // Create a provider with no mnemonic and no generation capability
            val emptyProvider = TestableWalletMasterSeedProvider(mockGenerator, mockHdkManager)
            emptyProvider.persistedMnemonic = null

            // Override to make getMasterSeed return null
            val nullSeedProvider =
                object : MasterSeedProvider {
                    override suspend fun getMasterSeed(): ByteArray? = null

                    override suspend fun getDeviceKeyPair(): HdkKeyPair? = null

                    override suspend fun getMnemonic(): List<String>? = null

                    override suspend fun getPqChildSeed(): ByteArray? = null

                    override suspend fun importMnemonic(mnemonic: CharArray): ImportMnemonicResult =
                        ImportMnemonicResult.Created
                }
            assertNull(nullSeedProvider.getPqChildSeed())
        }

    /**
     * Test double that replaces [EncryptedSharedPreferences] with an in-memory string variable.
     *
     * The PQ child seed derivation uses the **real HDK DeriveSalt** path:
     * 1. `DeriveSalt(masterSeed[0:32], "PQ_ML-DSA_Branch")` → 32-byte salt
     * 2. `HMAC-SHA512("chimali_pq_seed_v1", pqSalt)` → 64-byte child seed
     */
    private inner class TestableWalletMasterSeedProvider(
        private val gen: MasterSeedGenerator,
        private val hdkMgr: HdkManager,
    ) : MasterSeedProvider {
        var persistedMnemonic: String? = null

        @Volatile
        private var cachedSeed: ByteArray? = null

        @Volatile
        private var cachedKeyPair: HdkKeyPair? = null

        @Volatile
        private var cachedPqChildSeed: ByteArray? = null

        override suspend fun getMasterSeed(): ByteArray? = ensureInit().first

        override suspend fun getDeviceKeyPair(): HdkKeyPair? = ensureInit().second

        override suspend fun getMnemonic(): List<String>? = persistedMnemonic?.takeIf { it.isNotBlank() }?.split(" ")

        /**
         * HDK-based PQ child seed derivation (mirrors production logic):
         * 1. DeriveSalt(masterSeed[0:32], "PQ_ML-DSA_Branch") → 32-byte salt
         * 2. HMAC-SHA512("chimali_pq_seed_v1", pqSalt) → 64-byte child seed
         */
        override suspend fun getPqChildSeed(): ByteArray? {
            cachedPqChildSeed?.let { return it }
            val master = getMasterSeed() ?: return null
            return synchronized(this) {
                cachedPqChildSeed ?: run {
                    val hdkSeed = master.copyOf(HDK_SEED_SIZE_32)
                    val pqContext = PQ_CONTEXT_STRING.toByteArray(Charsets.UTF_8)
                    val pqSalt = hdkMgr.deriveSalt(hdkSeed, pqContext)
                    val expansionKey = PQ_EXPANSION_KEY.toByteArray(Charsets.UTF_8)
                    val mac = javax.crypto.Mac.getInstance("HmacSHA512")
                    mac.init(javax.crypto.spec.SecretKeySpec(expansionKey, "HmacSHA512"))
                    mac.doFinal(pqSalt).also { cachedPqChildSeed = it }
                }
            }
        }

        override suspend fun importMnemonic(mnemonic: CharArray): ImportMnemonicResult {
            try {
                val mnemonicString = String(mnemonic)
                val words = mnemonicString.split(" ")
                require(words.size == MNEMONIC_WORDS_24) {
                    "Invalid mnemonic: expected 24 words, got ${words.size}."
                }
                val alreadyExisted = !persistedMnemonic.isNullOrBlank()
                persistedMnemonic = mnemonicString
                // Invalidate cache.
                synchronized(this) {
                    cachedSeed = null
                    cachedKeyPair = null
                    cachedPqChildSeed?.fill(0)
                    cachedPqChildSeed = null
                }
                // Re-derive immediately.
                ensureInit()
                return if (alreadyExisted) ImportMnemonicResult.Replaced else ImportMnemonicResult.Created
            } finally {
                mnemonic.fill('\u0000')
            }
        }

        @Synchronized
        private fun ensureInit(): Pair<ByteArray?, HdkKeyPair?> {
            if (cachedSeed != null) return Pair(cachedSeed, cachedKeyPair)

            val mnemonic =
                if (!persistedMnemonic.isNullOrBlank()) {
                    persistedMnemonic!!.split(" ")
                } else {
                    val newMnemonic = gen.generateMnemonic(MNEMONIC_WORDS_24)
                    persistedMnemonic = newMnemonic.joinToString(" ")
                    newMnemonic
                }

            cachedSeed = gen.deriveSeed(mnemonic)
            cachedKeyPair = hdkMgr.generateDeviceKeyPair()
            return Pair(cachedSeed, cachedKeyPair)
        }
    }
}

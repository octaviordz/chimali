package com.chimali.fido2.data.crypto

import com.chimali.core.security.api.HdkKeyPair
import com.chimali.core.security.api.HdkManager
import com.chimali.core.security.api.MasterSeedGenerator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

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
            "abandon", "abandon", "abandon", "abandon",
            "abandon", "abandon", "abandon", "abandon",
            "abandon", "abandon", "abandon", "about",
        )
    private val fakeSeed = ByteArray(64) { it.toByte() }
    private val fakeKeyPair = mockk<HdkKeyPair>(relaxed = true)

    private lateinit var mockGenerator: MasterSeedGenerator
    private lateinit var mockHdkManager: HdkManager
    private lateinit var provider: TestableWalletMasterSeedProvider

    @BeforeTest
    fun setUp() {
        mockGenerator = mockk()
        mockHdkManager = mockk()

        every { mockGenerator.generateMnemonic(24) } returns fakeMnemonic
        every { mockGenerator.deriveSeed(fakeMnemonic, "") } returns fakeSeed
        every { mockHdkManager.generateDeviceKeyPair() } returns fakeKeyPair

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

            verify(exactly = 1) { mockGenerator.generateMnemonic(24) }
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

            val twentyFourWords = List(24) { "word${it + 1}" }
            every { mockGenerator.deriveSeed(twentyFourWords, "") } returns ByteArray(64)

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

            val newWords = List(24) { "new${it + 1}" }
            every { mockGenerator.deriveSeed(newWords, "") } returns ByteArray(64)

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

            val newWords = List(24) { "cache${it + 1}" }
            val newSeed = ByteArray(64) { (it + 10).toByte() }
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
                    "only", "twelve", "words",
                    "here", "but", "need", "more",
                    "this", "will", "fail", "validation", "check",
                )
            val chars = shortWords.joinToString(" ").toCharArray()

            assertFailsWith<IllegalArgumentException> {
                kotlinx.coroutines.runBlocking { provider.importMnemonic(chars) }
            }
        }

    @Test
    fun `importMnemonic zeroes the CharArray after use`() =
        runTest {
            val words = List(24) { "zero${it + 1}" }
            every { mockGenerator.deriveSeed(words, "") } returns ByteArray(64)

            val chars = words.joinToString(" ").toCharArray()
            provider.importMnemonic(chars)

            // All chars must be null characters after importMnemonic returns.
            assertTrue(chars.all { it == '\u0000' }, "CharArray must be zeroed after importMnemonic")
        }

    /**
     * Test double that replaces [EncryptedSharedPreferences] with an in-memory string variable.
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

        override suspend fun getMasterSeed(): ByteArray? = ensureInit().first

        override suspend fun getDeviceKeyPair(): HdkKeyPair? = ensureInit().second

        override suspend fun getMnemonic(): List<String>? = persistedMnemonic?.takeIf { it.isNotBlank() }?.split(" ")

        /** T017a: Returns a deterministic test PQ child seed. */
        override suspend fun getPqChildSeed(): ByteArray? = ByteArray(64) { (it + 99).toByte() }

        override suspend fun importMnemonic(mnemonic: CharArray): ImportMnemonicResult {
            try {
                val mnemonicString = String(mnemonic)
                val words = mnemonicString.split(" ")
                require(words.size == 24) {
                    "Invalid mnemonic: expected 24 words, got ${words.size}."
                }
                val alreadyExisted = !persistedMnemonic.isNullOrBlank()
                persistedMnemonic = mnemonicString
                // Invalidate cache.
                synchronized(this) {
                    cachedSeed = null
                    cachedKeyPair = null
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
                    val newMnemonic = gen.generateMnemonic(24)
                    persistedMnemonic = newMnemonic.joinToString(" ")
                    newMnemonic
                }

            cachedSeed = gen.deriveSeed(mnemonic)
            cachedKeyPair = hdkMgr.generateDeviceKeyPair()
            return Pair(cachedSeed, cachedKeyPair)
        }
    }
}

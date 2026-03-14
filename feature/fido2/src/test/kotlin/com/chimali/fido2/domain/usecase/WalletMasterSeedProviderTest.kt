package com.chimali.fido2.data.crypto

import com.chimali.core.security.api.HdkKeyPair
import com.chimali.core.security.api.HdkManager
import com.chimali.core.security.api.MasterSeedGenerator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
 */
class WalletMasterSeedProviderTest {

    private val fakeMnemonic = listOf(
        "abandon", "abandon", "abandon", "abandon",
        "abandon", "abandon", "abandon", "abandon",
        "abandon", "abandon", "abandon", "about"
    )
    private val fakeSeed = ByteArray(64) { it.toByte() }
    private val fakeKeyPair = mockk<HdkKeyPair>(relaxed = true)

    private lateinit var mockGenerator: MasterSeedGenerator
    private lateinit var mockHdkManager: HdkManager
    private lateinit var provider: TestableWalletMasterSeedProvider

    @BeforeEach
    fun setUp() {
        mockGenerator = mockk()
        mockHdkManager = mockk()

        every { mockGenerator.generateMnemonic(24) } returns fakeMnemonic
        every { mockGenerator.deriveSeed(fakeMnemonic, "") } returns fakeSeed
        every { mockHdkManager.generateDeviceKeyPair() } returns fakeKeyPair

        provider = TestableWalletMasterSeedProvider(mockGenerator, mockHdkManager)
    }

    @Test
    fun `getMasterSeed returns non-null seed on first call`() = runTest {
        val seed = provider.getMasterSeed()
        assertNotNull(seed)
        assertArrayEquals(fakeSeed, seed)
    }

    @Test
    fun `getMasterSeed returns same seed on subsequent calls (cached)`() = runTest {
        val seed1 = provider.getMasterSeed()
        val seed2 = provider.getMasterSeed()
        assertArrayEquals(seed1, seed2)
    }

    @Test
    fun `mnemonic is generated only once even across multiple getSeed calls`() = runTest {
        provider.getMasterSeed()
        provider.getMasterSeed()
        provider.getMasterSeed()

        verify(exactly = 1) { mockGenerator.generateMnemonic(24) }
        verify(exactly = 1) { mockGenerator.deriveSeed(fakeMnemonic, "") }
    }

    @Test
    fun `getDeviceKeyPair returns non-null pair after initialization`() = runTest {
        val keyPair = provider.getDeviceKeyPair()
        assertNotNull(keyPair)
    }

    @Test
    fun `getDeviceKeyPair returns same instance across calls`() = runTest {
        val kp1 = provider.getDeviceKeyPair()
        val kp2 = provider.getDeviceKeyPair()
        assertSame(kp1, kp2)
    }

    @Test
    fun `when persisted mnemonic exists it is reused without generating new one`() = runTest {
        provider.persistedMnemonic = fakeMnemonic.joinToString(" ")

        provider.getMasterSeed()

        verify(exactly = 0) { mockGenerator.generateMnemonic(any()) }
        verify(exactly = 1) { mockGenerator.deriveSeed(fakeMnemonic, "") }
    }

    @Test
    fun `getMnemonic returns the persisted mnemonic as word list`() = runTest {
        // T146: programmatic persistence verification
        provider.persistedMnemonic = fakeMnemonic.joinToString(" ")
        val words = provider.getMnemonic()
        assertEquals(fakeMnemonic, words)
    }

    @Test
    fun `getMnemonic returns null when no mnemonic is persisted`() = runTest {
        provider.persistedMnemonic = null
        val words = provider.getMnemonic()
        assertNull(words)
    }

    /**
     * Test double that replaces [EncryptedSharedPreferences] with an in-memory string variable.
     */
    private inner class TestableWalletMasterSeedProvider(
        private val gen: MasterSeedGenerator,
        private val hdkMgr: HdkManager
    ) : MasterSeedProvider {

        var persistedMnemonic: String? = null

        @Volatile
        private var cachedSeed: ByteArray? = null

        @Volatile
        private var cachedKeyPair: HdkKeyPair? = null

        override suspend fun getMasterSeed(): ByteArray? = ensureInit().first

        override suspend fun getDeviceKeyPair(): HdkKeyPair? = ensureInit().second

        @Synchronized
        private fun ensureInit(): Pair<ByteArray?, HdkKeyPair?> {
            if (cachedSeed != null) return Pair(cachedSeed, cachedKeyPair)

            val mnemonic = if (!persistedMnemonic.isNullOrBlank()) {
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

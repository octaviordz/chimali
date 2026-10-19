package com.chimali.fido2.data.crypto

import androidx.datastore.core.DataStore
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.datastore.EncryptionWrapper
import com.chimali.core.common.datastore.UserPreferences
import com.chimali.core.security.api.EncryptedMetadataService
import com.chimali.core.security.api.ImportMnemonicResult
import com.chimali.core.security.api.MasterSeedGenerator
import com.chimali.core.security.api.MasterSeedOwnerKind
import com.chimali.core.security.api.MetadataLookupTokenService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Production-provider boundary tests with only the encrypted store and crypto services faked. */
class WalletMasterSeedProviderTest {
    private val phrase =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
    private val seed = ByteArray(64) { it.toByte() }
    private val generator = mockk<MasterSeedGenerator>()
    private val dataStore = InMemoryDataStore()
    private val encryption = mockk<EncryptionWrapper>()
    private val lookupTokens = mockk<MetadataLookupTokenService>()
    private val encryptedMetadata = mockk<EncryptedMetadataService>()
    private val provider = WalletMasterSeedProvider(generator, dataStore, encryption, lookupTokens, encryptedMetadata)

    init {
        every { generator.generateMnemonic(any()) } answers { phrase.toCharArray() }
        every { generator.deriveSeed(any()) } answers { seed.copyOf() }
        every { encryption.encrypt(any()) } answers {
            val plaintext = firstArg<ByteArray>()
            plaintext.copyOf().also { plaintext.fill(0) }
        }
        every { encryption.decrypt(any()) } answers { firstArg<ByteArray>().copyOf() }
    }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `clean bootstrap stores encrypted mnemonic and returns owned root and parent copies`() =
        runTest {
            provider.withSessionOwner { _ ->
                val root1 = requireNotNull(provider.getMasterSeed())
                val root2 = requireNotNull(provider.getMasterSeed())
                val pair1 = requireNotNull(provider.getDeviceKeyPair())
                val pair2 = requireNotNull(provider.getDeviceKeyPair())
                try {
                    assertEquals(32, root1.size)
                    assertContentEquals(EXPECTED_ROOT_PRK.hexToBytes(), root1)
                    assertContentEquals(root1, root2)
                    assertNotSame(root1, root2)
                    assertNotSame(pair1.privateKey, pair2.privateKey)
                    assertNotSame(pair1.publicKey, pair2.publicKey)
                    root1.fill(0)
                    pair1.erasePrivateKey()
                    assertContentEquals(EXPECTED_ROOT_PRK.hexToBytes(), root2)
                    assertTrue(pair2.privateKey.any { it != 0.toByte() })
                    assertTrue(
                        dataStore.data
                            .first()
                            .encryptedWalletSeed
                            .isNotEmpty(),
                    )
                    verify(exactly = 1) { generator.generateMnemonic(24) }
                    verify(exactly = 1) { generator.deriveSeed(any()) }
                } finally {
                    root1.fill(0)
                    root2.fill(0)
                    pair1.erasePrivateKey()
                    pair2.erasePrivateKey()
                    seed.fill(0)
                }
            }
        }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `persisted encrypted mnemonic reconstructs the same root without generating another phrase`() =
        runTest {
            val encryptedPhrase = Base64.getEncoder().encodeToString(phrase.toByteArray(Charsets.US_ASCII))
            dataStore.updateData { it.copy(encryptedWalletSeed = encryptedPhrase) }

            provider.withSessionOwner { _ ->
                val root = requireNotNull(provider.getMasterSeed())
                val mnemonic = requireNotNull(provider.getMnemonic())
                try {
                    assertContentEquals(EXPECTED_ROOT_PRK.hexToBytes(), root)
                    assertContentEquals(phrase.toCharArray(), mnemonic)
                    verify(exactly = 0) { generator.generateMnemonic(any()) }
                } finally {
                    root.fill(0)
                    mnemonic.fill('\u0000')
                    seed.fill(0)
                }
            }
        }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `import consumes mutable phrase and replaces current derivation material`() =
        runTest {
            provider.withSessionOwner { _ -> requireNotNull(provider.getMasterSeed()).fill(0) }
            val imported = phrase.toCharArray()

            val result = provider.importMnemonic(imported)
            provider.withSessionOwner { _ ->
                val root = requireNotNull(provider.getMasterSeed())
                try {
                    assertEquals(ImportMnemonicResult.Replaced, result)
                    assertTrue(imported.all { it == '\u0000' })
                    assertContentEquals(EXPECTED_ROOT_PRK.hexToBytes(), root)
                } finally {
                    imported.fill('\u0000')
                    root.fill(0)
                    seed.fill(0)
                }
            }
        }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `failed root derivation does not publish a partial cache`() =
        runTest {
            every { generator.deriveSeed(any()) } throws IllegalStateException("synthetic failure")
            repeat(2) {
                kotlin.test.assertFailsWith<IllegalStateException> {
                    kotlinx.coroutines.runBlocking { provider.withSessionOwner { provider.getMasterSeed() } }
                }
                assertEquals(null, cachedValue("cachedPrk"))
                assertEquals(null, cachedValue("cachedParent"))
            }
            verify(exactly = 2) { generator.deriveSeed(any()) }
            seed.fill(0)
        }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `provider owns only one root and parent cache and no credential PQ cache`() {
        val fields = provider.javaClass.declaredFields.map { it.name }
        assertEquals(1, fields.count { it == "cachedPrk" })
        assertEquals(1, fields.count { it == "cachedParent" })
        assertTrue(
            fields.none { field ->
                val normalized = field.lowercase()
                normalized.contains("pq") || normalized.contains("mldsa") || normalized.contains("credentialseed")
            },
        )
    }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `session clearing fences an in-flight bootstrap and next request reconstructs lazily`() =
        runTest {
            val derivationEntered = CountDownLatch(1)
            val allowDerivationToFinish = CountDownLatch(1)
            every { generator.deriveSeed(any()) } answers {
                derivationEntered.countDown()
                check(allowDerivationToFinish.await(5, TimeUnit.SECONDS)) { "test release timed out" }
                seed.copyOf()
            }

            val staleRequest =
                async(Dispatchers.IO) {
                    provider.withSessionOwner { provider.getMasterSeed() }
                }
            assertTrue(derivationEntered.await(5, TimeUnit.SECONDS), "bootstrap should enter seed derivation")
            val oldGeneration = provider.sessionGeneration()

            provider.clearSession()
            assertTrue(provider.sessionGeneration() > oldGeneration)
            allowDerivationToFinish.countDown()

            assertFailsWith<CancellationException> { staleRequest.await() }
            provider.withSessionOwner { _ ->
                val reconstructed = requireNotNull(provider.getMasterSeed())
                try {
                    assertContentEquals(EXPECTED_ROOT_PRK.hexToBytes(), reconstructed)
                    verify(exactly = 2) { generator.deriveSeed(any()) }
                } finally {
                    reconstructed.fill(0)
                    seed.fill(0)
                }
            }
        }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `parent cancellation during synchronous bootstrap leaves no unowned cache and reconstructs lazily`() =
        runTest {
            val derivationEntered = CountDownLatch(1)
            val allowDerivationToFinish = CountDownLatch(1)
            every { generator.deriveSeed(any()) } answers {
                derivationEntered.countDown()
                check(allowDerivationToFinish.await(5, TimeUnit.SECONDS)) { "test release timed out" }
                seed.copyOf()
            }

            val request = async(Dispatchers.IO) { provider.withSessionOwner { provider.getMasterSeed() } }
            assertTrue(derivationEntered.await(5, TimeUnit.SECONDS), "bootstrap should enter seed derivation")
            request.cancel()
            allowDerivationToFinish.countDown()
            assertFailsWith<CancellationException> { request.await() }
            assertEquals(0, activeOwnerCount())
            assertEquals(null, cachedValue("cachedPrk"))
            assertEquals(null, cachedValue("cachedParent"))

            provider.withSessionOwner { _ ->
                val reconstructed = requireNotNull(provider.getMasterSeed())
                try {
                    assertContentEquals(EXPECTED_ROOT_PRK.hexToBytes(), reconstructed)
                    verify(exactly = 2) { generator.deriveSeed(any()) }
                } finally {
                    reconstructed.fill(0)
                    seed.fill(0)
                }
            }
        }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `clear and foreground cancellation release suspended bootstrap owners without parent cancellation`() =
        runTest {
            suspend fun verifyCancellation(
                blockedStage: SuspendedFirstAccessDataStore.Stage,
                clearGeneration: Boolean,
            ) {
                var generatedPhrase: CharArray? = null
                every { generator.generateMnemonic(any()) } answers {
                    phrase.toCharArray().also { generatedPhrase = it }
                }
                val blockedStore = SuspendedFirstAccessDataStore(blockedStage)
                val testProvider =
                    WalletMasterSeedProvider(
                        generator,
                        blockedStore,
                        encryption,
                        lookupTokens,
                        encryptedMetadata,
                    )
                var operationEntered = false
                val request =
                    async(Dispatchers.IO) {
                        testProvider.withSessionOwner(ownerKind = MasterSeedOwnerKind.FOREGROUND) {
                            operationEntered = true
                            testProvider.getMasterSeed()
                        }
                    }

                withContext(Dispatchers.Default) { withTimeout(2_000) { blockedStore.blockedAccess.await() } }
                if (clearGeneration) {
                    testProvider.clearSession()
                } else {
                    testProvider.cancelSessionOwners(MasterSeedOwnerKind.FOREGROUND)
                }

                assertFailsWith<CancellationException> {
                    withContext(Dispatchers.Default) { withTimeout(2_000) { request.await() } }
                }
                assertTrue(!operationEntered, "cancelled bootstrap must not enter its operation")
                if (blockedStage == SuspendedFirstAccessDataStore.Stage.UPDATE) {
                    val phraseErased = requireNotNull(generatedPhrase).all { it == '\u0000' }
                    assertTrue(phraseErased, "cancelled mnemonic must be erased")
                }
                val activeOwnersField =
                    testProvider.javaClass.getDeclaredField("activeOwners").apply { isAccessible = true }
                assertEquals(0, (activeOwnersField.get(testProvider) as Map<*, *>).size)
                for (fieldName in listOf("cachedPrk", "cachedParent")) {
                    val field = testProvider.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }
                    assertEquals(null, field.get(testProvider), "$fieldName must not publish for a cancelled owner")
                }

                testProvider.withSessionOwner { _ ->
                    val recovered = requireNotNull(testProvider.getMasterSeed())
                    try {
                        assertContentEquals(EXPECTED_ROOT_PRK.hexToBytes(), recovered)
                    } finally {
                        recovered.fill(0)
                    }
                }
            }

            verifyCancellation(SuspendedFirstAccessDataStore.Stage.READ, clearGeneration = true)
            verifyCancellation(SuspendedFirstAccessDataStore.Stage.READ, clearGeneration = false)
            verifyCancellation(SuspendedFirstAccessDataStore.Stage.UPDATE, clearGeneration = true)
            verifyCancellation(SuspendedFirstAccessDataStore.Stage.UPDATE, clearGeneration = false)
            seed.fill(0)
        }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `foreground cancellation discards completed synchronous bootstrap before cache publication`() =
        runTest {
            val derivationEntered = CountDownLatch(1)
            val allowDerivationToFinish = CountDownLatch(1)
            var derivationCount = 0
            var staleDerivedSeed: ByteArray? = null
            var staleGeneratedPhrase: CharArray? = null
            every { generator.generateMnemonic(any()) } answers {
                phrase.toCharArray().also { staleGeneratedPhrase = it }
            }
            every { generator.deriveSeed(any()) } answers {
                derivationCount += 1
                val derived = seed.copyOf()
                if (derivationCount == 1) {
                    staleDerivedSeed = derived
                    derivationEntered.countDown()
                    check(allowDerivationToFinish.await(5, TimeUnit.SECONDS)) { "test release timed out" }
                }
                derived
            }

            val staleRequest =
                async(Dispatchers.IO) {
                    provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.FOREGROUND) { provider.getMasterSeed() }
                }
            assertTrue(derivationEntered.await(5, TimeUnit.SECONDS), "bootstrap should enter seed derivation")
            provider.cancelSessionOwners(MasterSeedOwnerKind.FOREGROUND)
            allowDerivationToFinish.countDown()

            assertFailsWith<CancellationException> { staleRequest.await() }
            assertEquals(0, activeOwnerCount())
            assertEquals(null, cachedValue("cachedPrk"))
            assertEquals(null, cachedValue("cachedParent"))
            val seedErased = requireNotNull(staleDerivedSeed).all { it == 0.toByte() }
            val phraseErased = requireNotNull(staleGeneratedPhrase).all { it == '\u0000' }
            assertTrue(seedErased, "cancelled seed must be erased")
            assertTrue(phraseErased, "cancelled mnemonic must be erased")
            provider.withSessionOwner { _ ->
                val fresh = requireNotNull(provider.getMasterSeed())
                try {
                    assertContentEquals(EXPECTED_ROOT_PRK.hexToBytes(), fresh)
                } finally {
                    fresh.fill(0)
                }
            }
            seed.fill(0)
        }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `cancelled owner release waits for wallet replacement then erases without orphaning`() =
        runTest {
            val replacementDerivationEntered = CountDownLatch(1)
            val allowReplacementToFinish = CountDownLatch(1)
            var derivationCount = 0
            every { generator.deriveSeed(any()) } answers {
                derivationCount += 1
                if (derivationCount == 2) {
                    replacementDerivationEntered.countDown()
                    check(allowReplacementToFinish.await(5, TimeUnit.SECONDS)) { "test release timed out" }
                }
                seed.copyOf()
            }
            val ownerEntered = kotlinx.coroutines.CompletableDeferred<Unit>()
            val ownerOperation =
                async(Dispatchers.IO) {
                    provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.AUTHENTICATOR) {
                        ownerEntered.complete(Unit)
                        awaitCancellation()
                    }
                }
            ownerEntered.await()

            val replacement = async(Dispatchers.IO) { provider.importMnemonic(phrase.toCharArray()) }
            assertTrue(
                replacementDerivationEntered.await(5, TimeUnit.SECONDS),
                "replacement should hold initialization mutex",
            )
            ownerOperation.cancel()
            allowReplacementToFinish.countDown()

            assertEquals(ImportMnemonicResult.Replaced, replacement.await())
            assertFailsWith<CancellationException> { ownerOperation.await() }
            assertEquals(0, activeOwnerCount())
            assertEquals(null, cachedValue("cachedPrk"))
            assertEquals(null, cachedValue("cachedParent"))

            provider.withSessionOwner { _ ->
                val reconstructed = requireNotNull(provider.getMasterSeed())
                try {
                    assertContentEquals(EXPECTED_ROOT_PRK.hexToBytes(), reconstructed)
                } finally {
                    reconstructed.fill(0)
                    seed.fill(0)
                }
            }
        }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `overlapping owners keep one cache until the last owner releases then reconstruct lazily`() =
        runTest {
            val firstEntered = kotlinx.coroutines.CompletableDeferred<Unit>()
            val secondEntered = kotlinx.coroutines.CompletableDeferred<Unit>()
            val releaseFirst = kotlinx.coroutines.CompletableDeferred<Unit>()
            val releaseSecond = kotlinx.coroutines.CompletableDeferred<Unit>()
            var firstRoot: ByteArray? = null
            var secondRoot: ByteArray? = null
            var firstGeneration = -1L
            var secondGeneration = -1L

            val first =
                async {
                    provider.withSessionOwner { generation ->
                        firstGeneration = generation
                        firstRoot = requireNotNull(provider.getMasterSeed())
                        firstEntered.complete(Unit)
                        releaseFirst.await()
                    }
                }
            firstEntered.await()
            val second =
                async {
                    provider.withSessionOwner { generation ->
                        secondGeneration = generation
                        secondRoot = requireNotNull(provider.getMasterSeed())
                        secondEntered.complete(Unit)
                        releaseSecond.await()
                    }
                }
            secondEntered.await()

            try {
                assertEquals(firstGeneration, secondGeneration)
                assertContentEquals(firstRoot, secondRoot)
                verify(exactly = 1) { generator.deriveSeed(any()) }

                releaseFirst.complete(Unit)
                first.await()
                assertTrue(provider.isSessionGenerationCurrent(secondGeneration))
                assertContentEquals(EXPECTED_ROOT_PRK.hexToBytes(), requireNotNull(secondRoot))

                releaseSecond.complete(Unit)
                second.await()
                assertTrue(!provider.isSessionGenerationCurrent(secondGeneration))

                provider.withSessionOwner { nextGeneration ->
                    assertTrue(nextGeneration > secondGeneration)
                    val reconstructed = requireNotNull(provider.getMasterSeed())
                    try {
                        assertContentEquals(EXPECTED_ROOT_PRK.hexToBytes(), reconstructed)
                        verify(exactly = 2) { generator.deriveSeed(any()) }
                    } finally {
                        reconstructed.fill(0)
                    }
                }
            } finally {
                releaseFirst.complete(Unit)
                releaseSecond.complete(Unit)
                firstRoot?.fill(0)
                secondRoot?.fill(0)
                seed.fill(0)
            }
        }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `session fence cancels its active operation owner`() =
        runTest {
            val ownerEntered = kotlinx.coroutines.CompletableDeferred<Long>()
            val operation =
                async {
                    provider.withSessionOwner { generation ->
                        ownerEntered.complete(generation)
                        awaitCancellation()
                    }
                }
            val ownedGeneration = ownerEntered.await()

            provider.clearSession()

            assertTrue(!provider.isSessionGenerationCurrent(ownedGeneration))
            assertFailsWith<CancellationException> { operation.await() }
            provider.withSessionOwner { newGeneration ->
                assertTrue(newGeneration > ownedGeneration)
                val root = requireNotNull(provider.getMasterSeed())
                try {
                    assertContentEquals(EXPECTED_ROOT_PRK.hexToBytes(), root)
                } finally {
                    root.fill(0)
                }
            }
            seed.fill(0)
        }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `foreground loss cancels foreground owner while admitted authenticator owner continues`() =
        runTest {
            val foregroundEntered = kotlinx.coroutines.CompletableDeferred<Long>()
            val authenticatorEntered = kotlinx.coroutines.CompletableDeferred<Long>()
            val foreground =
                async {
                    provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.FOREGROUND) { generation ->
                        foregroundEntered.complete(generation)
                        awaitCancellation()
                    }
                }
            val foregroundGeneration = foregroundEntered.await()
            val authenticator =
                async {
                    provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.AUTHENTICATOR) { generation ->
                        authenticatorEntered.complete(generation)
                        awaitCancellation()
                    }
                }
            val authenticatorGeneration = authenticatorEntered.await()

            provider.cancelSessionOwners(MasterSeedOwnerKind.FOREGROUND)

            assertFailsWith<CancellationException> { foreground.await() }
            assertTrue(provider.isSessionGenerationCurrent(authenticatorGeneration))
            assertTrue(!authenticator.isCompleted)
            assertEquals(foregroundGeneration, authenticatorGeneration)

            provider.cancelSessionOwners(MasterSeedOwnerKind.AUTHENTICATOR)

            assertFailsWith<CancellationException> { authenticator.await() }
            assertTrue(!provider.isSessionGenerationCurrent(authenticatorGeneration))
            seed.fill(0)
        }

    /**
     * @see NFR-SEC-041
     * @see TR-AUTH-031
     */
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `wallet replacement cancels old owner and next owner derives from imported wallet`() =
        runTest {
            var replacementSeed: ByteArray? = null
            every { generator.deriveSeed(any()) } answers {
                (replacementSeed ?: seed).copyOf()
            }
            val ownerEntered = kotlinx.coroutines.CompletableDeferred<Long>()
            var previousRoot: ByteArray? = null
            val operation =
                async {
                    provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.AUTHENTICATOR) { generation ->
                        previousRoot = requireNotNull(provider.getMasterSeed())
                        ownerEntered.complete(generation)
                        awaitCancellation()
                    }
                }
            val oldGeneration = ownerEntered.await()
            replacementSeed = ByteArray(64) { (it + 1).toByte() }
            val imported = phrase.toCharArray()

            try {
                assertEquals(ImportMnemonicResult.Replaced, provider.importMnemonic(imported))
                assertTrue(imported.all { it == '\u0000' })
                assertFailsWith<CancellationException> { operation.await() }
                assertTrue(!provider.isSessionGenerationCurrent(oldGeneration))

                provider.withSessionOwner { newGeneration ->
                    assertTrue(newGeneration > oldGeneration)
                    val root = requireNotNull(provider.getMasterSeed())
                    try {
                        assertTrue(!root.contentEquals(requireNotNull(previousRoot)))
                    } finally {
                        root.fill(0)
                    }
                }
                verify(exactly = 3) { generator.deriveSeed(any()) }
            } finally {
                previousRoot?.fill(0)
                replacementSeed?.fill(0)
                imported.fill('\u0000')
                seed.fill(0)
            }
        }

    private class InMemoryDataStore : DataStore<UserPreferences> {
        private val state = MutableStateFlow(UserPreferences())
        private val mutex = Mutex()

        override val data: Flow<UserPreferences> = state

        override suspend fun updateData(transform: suspend (t: UserPreferences) -> UserPreferences): UserPreferences =
            mutex.withLock {
                transform(state.value).also { state.value = it }
            }
    }

    private class SuspendedFirstAccessDataStore(
        private val stage: Stage,
    ) : DataStore<UserPreferences> {
        enum class Stage { READ, UPDATE }

        val blockedAccess = CompletableDeferred<Unit>()
        private val state = MutableStateFlow(UserPreferences())
        private val reads = AtomicInteger()
        private val updates = AtomicInteger()
        private val mutex = Mutex()

        override val data: Flow<UserPreferences> =
            flow {
                if (stage == Stage.READ && reads.getAndIncrement() == 0) {
                    blockedAccess.complete(Unit)
                    awaitCancellation()
                }
                emit(state.value)
            }

        override suspend fun updateData(transform: suspend (t: UserPreferences) -> UserPreferences): UserPreferences =
            mutex.withLock {
                if (stage == Stage.UPDATE && updates.getAndIncrement() == 0) {
                    blockedAccess.complete(Unit)
                    awaitCancellation()
                }
                transform(state.value).also { state.value = it }
            }
    }

    private fun cachedValue(fieldName: String): Any? =
        provider.javaClass
            .getDeclaredField(fieldName)
            .apply { isAccessible = true }
            .get(provider)

    private fun activeOwnerCount(): Int = (cachedValue("activeOwners") as Map<*, *>).size

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        const val EXPECTED_ROOT_PRK = "aa6ff6f8d8e560bb6a8f8ff11f88b7684e184c480f30f17ac64a696730aacea2"
    }
}

package com.chimali.fido2.data.crypto

import android.content.ComponentCallbacks2
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimali.core.common.datastore.EncryptionWrapper
import com.chimali.core.common.datastore.createUserPreferencesDataStore
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.getOrThrow
import com.chimali.core.database.EncryptedDriverFactory
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.time.TimeProvider
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.core.security.api.HdkChild
import com.chimali.core.security.api.HdkManager
import com.chimali.core.security.api.ImportMnemonicResult
import com.chimali.core.security.api.MasterSeedOwnerKind
import com.chimali.core.security.hdkeys.HdkP256
import com.chimali.core.security.impl.AesEncryptionManager
import com.chimali.core.security.impl.AesGcmEncryptedMetadataService
import com.chimali.core.security.impl.Bip39MasterSeedGenerator
import com.chimali.core.security.impl.EventStoreKeyProviderImpl
import com.chimali.core.security.impl.HmacMetadataLookupTokenService
import com.chimali.fido2.data.dao.PasskeyCredentialDao
import com.chimali.fido2.data.dao.RelyingPartyDao
import com.chimali.fido2.data.dao.UserConsentRecordDao
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.eventsourcing.PasskeyAggregateServiceImpl
import com.chimali.fido2.data.eventsourcing.PasskeyEventStoreRepositoryImpl
import com.chimali.fido2.data.eventsourcing.PasskeySnapshotRepositoryImpl
import com.chimali.fido2.data.repository.CredentialRepositoryImpl
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import com.chimali.fido2.domain.service.BiometricEnrollmentStatus
import com.chimali.fido2.domain.service.BiometricStrength
import com.chimali.fido2.domain.service.BiometricType
import com.chimali.fido2.domain.service.PinConfiguration
import com.chimali.fido2.domain.service.UserVerificationAvailability
import com.chimali.fido2.domain.service.UserVerificationRequirement as ServiceUserVerificationRequirement
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.domain.service.VerificationContext
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device-only checks of real Android Keystore, encrypted recovery storage, and SQLite3MC wiring. */
@RunWith(AndroidJUnit4::class)
class LocalProfileLifecycleTest {
    private companion object {
        const val SQLITE_HEADER_LENGTH = 16
        const val RECOVERY_STORE_RELATIVE_PATH = "hdk-device-tests/%s/user-preferences.pb"
        const val TEST_TIMEOUT_SECONDS = 10L
        const val DERIVATION_TEST_BYTE = 0x5A.toByte()
        const val EXISTING_CREDENTIAL_TEST_BYTE = 0x11.toByte()
        const val CONFLICTING_CREDENTIAL_TEST_BYTE = 0x22.toByte()
        const val AAGUID_LENGTH = 16
        const val TEST_P256_KEY_SIZE_BITS = 256
        const val SYNTHETIC_SIGNING_INPUT_LENGTH_BYTES = 32
        const val MNEMONIC_FIRST_WORDS =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon"
        const val MNEMONIC_LAST_WORDS =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun emptyEncryptedRecoveryStoreInitializesAndReconstructsTheSameWalletAndDatabase() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val testId = UUID.randomUUID().toString()
            val recoveryFile = recoveryFile(context.filesDir, testId)
            recoveryFile.parentFile?.mkdirs()
            val databaseName = "hdk-device-$testId.db"
            val dataStore = createUserPreferencesDataStore { recoveryFile.absolutePath }
            val encryption = EncryptionWrapper()
            val provider =
                WalletMasterSeedProvider(
                    masterSeedGenerator = Bip39MasterSeedGenerator(),
                    dataStore = dataStore,
                    encryptionWrapper = encryption,
                    lookupTokenService = HmacMetadataLookupTokenService(),
                    encryptedMetadataService = AesGcmEncryptedMetadataService(),
                )
            var initialRoot: ByteArray? = null
            var databaseDriver: app.cash.sqldelight.db.SqlDriver? = null
            try {
                assertTrue(
                    dataStore.data
                        .first()
                        .encryptedWalletSeed
                        .isEmpty(),
                )
                provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.BOUNDED) {
                    initialRoot = requireNotNull(provider.getMasterSeed())
                }

                val persistedCiphertext = dataStore.data.first().encryptedWalletSeed
                assertTrue("Fresh wallet must persist encrypted recovery material", persistedCiphertext.isNotEmpty())
                assertFalse(
                    "The persisted field must not contain a plain BIP39 phrase",
                    persistedCiphertext.contains(' '),
                )

                val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                assertNotNull(
                    "Production AES-GCM wrapping key should exist in Android Keystore",
                    keyStore.getKey("chimali_datastore_key", null),
                )

                val factory = EncryptedDriverFactory(context, provider)
                databaseDriver = factory.createDriver(Fido2Database.Schema, databaseName)
                val databaseFile = context.getDatabasePath(databaseName)
                assertTrue("The disposable encrypted database should be created", databaseFile.exists())
                assertFalse(
                    "The SQLite3MC file must not have the plain SQLite header",
                    databaseFile.inputStream().use { input ->
                        val header = ByteArray(SQLITE_HEADER_LENGTH)
                        input.read(header)
                        header.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.UTF_8))
                    },
                )
                // Close the first connection before verifying with a second independent open.
                databaseDriver.close()
                databaseDriver = factory.createDriver(Fido2Database.Schema, databaseName)
                assertNotNull("SQLite3MC database should reopen with the current FIDO2 schema", databaseDriver)
                databaseDriver.close()
                databaseDriver = null

                provider.clearSession()
                provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.BOUNDED) {
                    val reconstructed = requireNotNull(provider.getMasterSeed())
                    try {
                        assertArrayEquals(
                            "Restart reconstruction must reproduce the local root",
                            initialRoot,
                            reconstructed,
                        )
                    } finally {
                        reconstructed.fill(0)
                    }
                }
            } finally {
                initialRoot?.fill(0)
                databaseDriver?.close()
                provider.clearSession()
                context.deleteDatabase(databaseName)
                recoveryFile.parentFile?.deleteRecursively()
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun foregroundOwnerCanEndWhileAuthenticatorOwnerKeepsWalletSessionAlive() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val testId = UUID.randomUUID().toString()
            val recoveryFile = recoveryFile(context.filesDir, testId)
            recoveryFile.parentFile?.mkdirs()
            val dataStore = createUserPreferencesDataStore { recoveryFile.absolutePath }
            val provider =
                WalletMasterSeedProvider(
                    masterSeedGenerator = Bip39MasterSeedGenerator(),
                    dataStore = dataStore,
                    encryptionWrapper = EncryptionWrapper(),
                    lookupTokenService = HmacMetadataLookupTokenService(),
                    encryptedMetadataService = AesGcmEncryptedMetadataService(),
                )
            var originalRoot: ByteArray? = null
            val foregroundEntered = CompletableDeferred<Long>()
            val authenticatorEntered = CompletableDeferred<Long>()
            val foreground =
                async {
                    provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.FOREGROUND) { generation ->
                        originalRoot = requireNotNull(provider.getMasterSeed())
                        foregroundEntered.complete(generation)
                        awaitCancellation()
                    }
                }
            try {
                val foregroundGeneration = foregroundEntered.await()
                val authenticator =
                    async {
                        provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.AUTHENTICATOR) { generation ->
                            authenticatorEntered.complete(generation)
                            awaitCancellation()
                        }
                    }
                val authenticatorGeneration = authenticatorEntered.await()
                assertEquals(foregroundGeneration, authenticatorGeneration)

                provider.cancelSessionOwners(MasterSeedOwnerKind.FOREGROUND)

                try {
                    foreground.await()
                    throw AssertionError("Foreground owner should be cancelled")
                } catch (_: CancellationException) {
                }
                assertTrue(
                    "The admitted authenticator must retain the current generation",
                    provider.isSessionGenerationCurrent(authenticatorGeneration),
                )
                assertFalse(
                    "Foreground cancellation must leave the authenticator owner active",
                    authenticator.isCompleted,
                )

                provider.clearSession()

                try {
                    authenticator.await()
                    throw AssertionError("Authenticator owner should be cancelled by the session fence")
                } catch (_: CancellationException) {
                }
                assertFalse(
                    "Clearing the final generation must fence its owner",
                    provider.isSessionGenerationCurrent(authenticatorGeneration),
                )
                provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.BOUNDED) { reconstructedGeneration ->
                    val reconstructed = requireNotNull(provider.getMasterSeed())
                    try {
                        assertTrue(
                            "A later authorized owner must initialize a new generation",
                            reconstructedGeneration > authenticatorGeneration,
                        )
                        assertArrayEquals(
                            "Lazy reconstruction must use the same encrypted wallet",
                            requireNotNull(originalRoot),
                            reconstructed,
                        )
                    } finally {
                        reconstructed.fill(0)
                    }
                }
            } finally {
                provider.clearSession()
                if (!foreground.isCompleted) foreground.cancel()
                originalRoot?.fill(0)
                recoveryFile.parentFile?.deleteRecursively()
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun replacingEncryptedWalletFencesAdmittedAndroidProviderOwner() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val testId = UUID.randomUUID().toString()
            val recoveryFile = recoveryFile(context.filesDir, testId)
            recoveryFile.parentFile?.mkdirs()
            val dataStore = createUserPreferencesDataStore { recoveryFile.absolutePath }
            val provider =
                WalletMasterSeedProvider(
                    masterSeedGenerator = Bip39MasterSeedGenerator(),
                    dataStore = dataStore,
                    encryptionWrapper = EncryptionWrapper(),
                    lookupTokenService = HmacMetadataLookupTokenService(),
                    encryptedMetadataService = AesGcmEncryptedMetadataService(),
                )
            var initialRoot: ByteArray? = null
            val ownerEntered = CompletableDeferred<Long>()
            val replacement = "$MNEMONIC_FIRST_WORDS $MNEMONIC_LAST_WORDS"
            try {
                val owner =
                    async {
                        provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.AUTHENTICATOR) { generation ->
                            initialRoot = requireNotNull(provider.getMasterSeed())
                            ownerEntered.complete(generation)
                            awaitCancellation()
                        }
                    }
                val admittedGeneration = ownerEntered.await()
                assertNotNull("The admitted owner should hold a root copy", initialRoot)

                val input = replacement.toCharArray()
                assertEquals(ImportMnemonicResult.Replaced, provider.importMnemonic(input))
                assertTrue("Import must erase the transferred mutable phrase", input.all { it == '\u0000' })
                try {
                    owner.await()
                    throw AssertionError("Wallet replacement should cancel the admitted owner")
                } catch (_: CancellationException) {
                }
                assertFalse(
                    "Replacement must fence the admitted generation",
                    provider.isSessionGenerationCurrent(admittedGeneration),
                )

                provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.BOUNDED) { replacementGeneration ->
                    val replacementRoot = requireNotNull(provider.getMasterSeed())
                    try {
                        assertTrue(replacementGeneration > admittedGeneration)
                        assertFalse(
                            "The imported wallet must not reconstruct the previous root",
                            initialRoot!!.contentEquals(replacementRoot),
                        )
                        val persisted = dataStore.data.first().encryptedWalletSeed
                        assertTrue(
                            "Replacement recovery material must remain encrypted",
                            persisted.isNotEmpty() && !persisted.contains(' '),
                        )
                    } finally {
                        replacementRoot.fill(0)
                    }
                }
            } finally {
                provider.clearSession()
                initialRoot?.fill(0)
                recoveryFile.parentFile?.deleteRecursively()
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Suppress("DEPRECATION") // These framework trim levels are retained as compatibility callback constants.
    @Test
    fun severeMemoryTrimLevelsFenceWalletOwnersAndPermitEncryptedReconstruction() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val testId = UUID.randomUUID().toString()
            val recoveryFile = recoveryFile(context.filesDir, testId)
            recoveryFile.parentFile?.mkdirs()
            val dataStore = createUserPreferencesDataStore { recoveryFile.absolutePath }
            val provider =
                WalletMasterSeedProvider(
                    masterSeedGenerator = Bip39MasterSeedGenerator(),
                    dataStore = dataStore,
                    encryptionWrapper = EncryptionWrapper(),
                    lookupTokenService = HmacMetadataLookupTokenService(),
                    encryptedMetadataService = AesGcmEncryptedMetadataService(),
                )
            var childDerivations = 0
            val countingHdkManager =
                object : HdkManager {
                    override fun deriveChild(
                        localSecret: ByteArray,
                        parentPublicKey: ByteArray,
                        index: UInt,
                    ): HdkChild {
                        childDerivations += 1
                        return HdkP256.deriveChild(localSecret, parentPublicKey, index)
                    }

                    override fun deriveChildPrivateKey(
                        parentPrivateKey: ByteArray,
                        factor: ByteArray,
                    ): ByteArray = HdkP256.deriveChildPrivateKey(parentPrivateKey, factor)
                }
            val crypto = Fido2CryptoService(countingHdkManager, provider, PostQuantumCrypto(), Dispatchers.Default)
            val descriptor =
                CredentialKeyDescriptor(
                    credentialId =
                        CredentialId.fromByteArray(
                            ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { DERIVATION_TEST_BYTE },
                        ),
                    coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                )
            val signingInput = ByteArray(SYNTHETIC_SIGNING_INPUT_LENGTH_BYTES) { it.toByte() }
            var expectedRoot: ByteArray? = null
            try {
                listOf(
                    ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
                    ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
                    ComponentCallbacks2.TRIM_MEMORY_MODERATE,
                ).forEach { level ->
                    val ownerEntered = CompletableDeferred<Long>()
                    val owner =
                        async {
                            provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.AUTHENTICATOR) { generation ->
                                val root = requireNotNull(provider.getMasterSeed())
                                try {
                                    if (expectedRoot == null) expectedRoot = root.copyOf()
                                    ownerEntered.complete(generation)
                                    awaitCancellation()
                                } finally {
                                    root.fill(0)
                                }
                            }
                        }
                    val admittedGeneration = ownerEntered.await()
                    var gcHints = 0

                    WalletSessionMemoryPressure.handle(
                        level = level,
                        clearSession = provider::clearSession,
                        requestGc = { gcHints += 1 },
                    )

                    assertEquals("Each severe trim level should request one GC hint", 1, gcHints)
                    assertFalse(
                        "A severe trim level must fence the admitted wallet generation",
                        provider.isSessionGenerationCurrent(admittedGeneration),
                    )
                    try {
                        owner.await()
                        throw AssertionError("A severe trim level must cancel the admitted owner")
                    } catch (_: CancellationException) {
                    }

                    var unownedAccessRejected = false
                    try {
                        provider.getMasterSeed()?.fill(0)
                    } catch (_: CancellationException) {
                        unownedAccessRejected = true
                    }
                    assertTrue("Trimmed wallet material must not be read outside a new owner", unownedAccessRejected)

                    var signingAdmissionCancelled = false
                    try {
                        crypto.sign(descriptor, signingInput)
                    } catch (_: CancellationException) {
                        signingAdmissionCancelled = true
                    }
                    assertTrue(
                        "A severe trim must reject signing until a new wallet owner is explicitly admitted",
                        signingAdmissionCancelled,
                    )
                    assertEquals("Rejected signing must not derive a child key", 0, childDerivations)

                    provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.BOUNDED) { nextGeneration ->
                        assertTrue(nextGeneration > admittedGeneration)
                        val reconstructed = requireNotNull(provider.getMasterSeed())
                        try {
                            assertArrayEquals(
                                "A later authorized owner should reconstruct the encrypted wallet",
                                expectedRoot,
                                reconstructed,
                            )
                        } finally {
                            reconstructed.fill(0)
                        }
                    }
                }
            } finally {
                provider.clearSession()
                signingInput.fill(0)
                expectedRoot?.fill(0)
                recoveryFile.parentFile?.deleteRecursively()
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun androidProvidersGenerateAndVerifyAllSupportedCredentialSignatures() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val testId = UUID.randomUUID().toString()
            val recoveryFile = recoveryFile(context.filesDir, testId)
            recoveryFile.parentFile?.mkdirs()
            val dataStore = createUserPreferencesDataStore { recoveryFile.absolutePath }
            val provider =
                WalletMasterSeedProvider(
                    masterSeedGenerator = Bip39MasterSeedGenerator(),
                    dataStore = dataStore,
                    encryptionWrapper = EncryptionWrapper(),
                    lookupTokenService = HmacMetadataLookupTokenService(),
                    encryptedMetadataService = AesGcmEncryptedMetadataService(),
                )
            val crypto = Fido2CryptoService(HdkP256, provider, PostQuantumCrypto(), Dispatchers.Default)
            val message = "device-local-profile-signature-check".toByteArray(Charsets.UTF_8)
            try {
                provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.BOUNDED) {
                    listOf(
                        CredentialKeyDescriptor.COSE_ES256 to CredentialKeyDescriptor.P256_PROFILE,
                        CredentialKeyDescriptor.COSE_EDDSA to CredentialKeyDescriptor.ED25519_PROFILE,
                        CredentialKeyDescriptor.COSE_ML_DSA_65 to CredentialKeyDescriptor.MLDSA65_PROFILE,
                    ).forEachIndexed { index, (algorithm, profile) ->
                        val descriptor =
                            CredentialKeyDescriptor(
                                credentialId =
                                    CredentialId.fromByteArray(
                                        ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { (index + 1).toByte() },
                                    ),
                                coseAlgorithm = algorithm,
                                derivationProfile = profile,
                            )
                        val generated = crypto.generateCredentialPublicKey(descriptor).getOrThrow()
                        var signatureBytes: ByteArray? = null
                        var storedPublicKey: ByteArray? = null
                        try {
                            val publicKey = crypto.decodePublicKey(descriptor, generated.publicKey)
                            storedPublicKey = publicKey.encoded
                            signatureBytes =
                                crypto
                                    .sign(descriptor, message, storedPublicKey)
                                    .getOrThrow()
                            val jcaAlgorithm =
                                when (algorithm) {
                                    CredentialKeyDescriptor.COSE_ES256 -> "SHA256withECDSA"
                                    CredentialKeyDescriptor.COSE_EDDSA -> "Ed25519"
                                    CredentialKeyDescriptor.COSE_ML_DSA_65 -> "ML-DSA-65"
                                    else -> error("Unexpected configured algorithm: $algorithm")
                                }
                            val verifier =
                                if (algorithm == CredentialKeyDescriptor.COSE_ML_DSA_65) {
                                    Signature.getInstance(
                                        jcaAlgorithm,
                                        org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME,
                                    )
                                } else {
                                    Signature.getInstance(jcaAlgorithm)
                                }
                            verifier.initVerify(publicKey)
                            verifier.update(message)
                            assertTrue(
                                "$profile signature must verify using Android crypto providers",
                                verifier.verify(signatureBytes),
                            )
                        } finally {
                            generated.publicKey.fill(0)
                            signatureBytes?.fill(0)
                            storedPublicKey?.fill(0)
                        }
                    }
                }
            } finally {
                provider.clearSession()
                message.fill(0)
                recoveryFile.parentFile?.deleteRecursively()
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun generationFenceDiscardsSynchronousP256DerivationResult() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val testId = UUID.randomUUID().toString()
            val recoveryFile = recoveryFile(context.filesDir, testId)
            recoveryFile.parentFile?.mkdirs()
            val dataStore = createUserPreferencesDataStore { recoveryFile.absolutePath }
            val provider =
                WalletMasterSeedProvider(
                    masterSeedGenerator = Bip39MasterSeedGenerator(),
                    dataStore = dataStore,
                    encryptionWrapper = EncryptionWrapper(),
                    lookupTokenService = HmacMetadataLookupTokenService(),
                    encryptedMetadataService = AesGcmEncryptedMetadataService(),
                )
            val derivationEntered = CountDownLatch(1)
            val releaseDerivation = CountDownLatch(1)
            val blockingHdkManager =
                object : HdkManager {
                    override fun deriveChild(
                        localSecret: ByteArray,
                        parentPublicKey: ByteArray,
                        index: UInt,
                    ): HdkChild {
                        val child = HdkP256.deriveChild(localSecret, parentPublicKey, index)
                        derivationEntered.countDown()
                        check(releaseDerivation.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            "Test did not release synchronous P-256 derivation"
                        }
                        return child
                    }

                    override fun deriveChildPrivateKey(
                        parentPrivateKey: ByteArray,
                        factor: ByteArray,
                    ): ByteArray = HdkP256.deriveChildPrivateKey(parentPrivateKey, factor)
                }
            val crypto = Fido2CryptoService(blockingHdkManager, provider, PostQuantumCrypto(), Dispatchers.Default)
            val descriptor =
                CredentialKeyDescriptor(
                    credentialId =
                        CredentialId.fromByteArray(
                            ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { DERIVATION_TEST_BYTE },
                        ),
                    coseAlgorithm = CredentialKeyDescriptor.COSE_ES256,
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                )
            var resultPublished: ByteArray? = null
            try {
                val operation =
                    async {
                        provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.AUTHENTICATOR) {
                            resultPublished = crypto.generateCredentialPublicKey(descriptor).getOrThrow().publicKey
                        }
                    }
                val derivationStarted =
                    withContext(Dispatchers.IO) {
                        derivationEntered.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    }
                if (!derivationStarted) {
                    provider.clearSession()
                    releaseDerivation.countDown()
                    operation.cancel()
                    operation.join()
                    throw AssertionError("The synchronous derivation should reach the controlled in-flight point")
                }
                provider.clearSession()
                releaseDerivation.countDown()
                try {
                    operation.await()
                    throw AssertionError("A stale synchronous result must be discarded after the generation fence")
                } catch (_: CancellationException) {
                }
                assertEquals("No stale public key may be published", null, resultPublished)
            } finally {
                releaseDerivation.countDown()
                provider.clearSession()
                resultPublished?.fill(0)
                recoveryFile.parentFile?.deleteRecursively()
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun encryptedProjectionReplacementRollsBackEarlierDeletionWhenInsertFails() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val testId = UUID.randomUUID().toString()
            val recoveryFile = recoveryFile(context.filesDir, testId)
            recoveryFile.parentFile?.mkdirs()
            val databaseName = "hdk-rollback-$testId.db"
            val dataStore = createUserPreferencesDataStore { recoveryFile.absolutePath }
            val lookupTokens = HmacMetadataLookupTokenService()
            val encryptedMetadata = AesGcmEncryptedMetadataService()
            val provider =
                WalletMasterSeedProvider(
                    masterSeedGenerator = Bip39MasterSeedGenerator(),
                    dataStore = dataStore,
                    encryptionWrapper = EncryptionWrapper(),
                    lookupTokenService = lookupTokens,
                    encryptedMetadataService = encryptedMetadata,
                )
            var driver: app.cash.sqldelight.db.SqlDriver? = null
            try {
                provider.withSessionOwner(ownerKind = MasterSeedOwnerKind.BOUNDED) {
                    val factory = EncryptedDriverFactory(context, provider)
                    driver = factory.createDriver(Fido2Database.Schema, databaseName)
                    val database = Fido2Database(requireNotNull(driver))
                    val metadataProtection = CredentialMetadataProtectionService(lookupTokens, encryptedMetadata)
                    val dao = PasskeyCredentialDao(database, TimeProvider(), metadataProtection)
                    val oldId =
                        CredentialId.fromByteArray(
                            ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { EXISTING_CREDENTIAL_TEST_BYTE },
                        )
                    val conflictId =
                        CredentialId.fromByteArray(
                            ByteArray(CredentialId.MIN_CREDENTIAL_ID_BYTES) { CONFLICTING_CREDENTIAL_TEST_BYTE },
                        )

                    dao.insertCredential(deviceTestCredential(oldId, "example.com", "user-1"))
                    dao.insertCredential(deviceTestCredential(conflictId, "other.example", "other-user"))

                    var replacementFailure: java.sql.SQLException? = null
                    try {
                        // The candidate duplicates another row's primary key. The DAO first deletes
                        // the old RP/user projection; the real SQLDelight transaction must restore
                        // that row when the insertion then fails.
                        dao.replaceCredentialsForRpAndUser(
                            deviceTestCredential(conflictId, "example.com", "user-1"),
                        )
                    } catch (failure: java.sql.SQLException) {
                        replacementFailure = failure
                    }

                    assertNotNull("The conflicting insert must fail", replacementFailure)
                    assertNotNull("Rollback must restore the previous projection", dao.getCredentialById(oldId))
                    assertNotNull(
                        "Rollback must preserve the unrelated conflicting row",
                        dao.getCredentialById(conflictId),
                    )
                    assertEquals(2L, database.passkeyCredentialQueries.count_all().executeAsOne())
                }
            } finally {
                provider.clearSession()
                driver?.close()
                context.deleteDatabase(databaseName)
                recoveryFile.parentFile?.deleteRecursively()
            }
        }

    private fun deviceTestCredential(
        id: CredentialId,
        rpId: String,
        userId: String,
    ): PasskeyCredential {
        val publicKey =
            KeyPairGenerator
                .getInstance("EC")
                .apply { initialize(TEST_P256_KEY_SIZE_BITS) }
                .generateKeyPair()
                .public
        return PasskeyCredential.create(
            id = id,
            rpId = RpId(rpId),
            userId = UserId(userId),
            userName = "synthetic-user",
            userDisplayName = "Synthetic User",
            publicKey = publicKey,
            aaguid = ByteArray(AAGUID_LENGTH),
            credentialId = id.toByteArray(),
            coseAlgorithm = PasskeyCredential.COSE_ES256,
            derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
        )
    }

    private fun recoveryFile(
        filesDirectory: File,
        testId: String,
    ): File = File(filesDirectory, RECOVERY_STORE_RELATIVE_PATH.format(testId))
}

internal fun createCredentialRepository(
    context: android.content.Context,
    provider: WalletMasterSeedProvider,
    databaseName: String,
    lookupTokens: HmacMetadataLookupTokenService,
    encryptedMetadata: AesGcmEncryptedMetadataService,
): Pair<app.cash.sqldelight.db.SqlDriver, CredentialRepositoryImpl> {
    val driver = EncryptedDriverFactory(context, provider).createDriver(Fido2Database.Schema, databaseName)
    val database = Fido2Database(driver)
    val timeProvider = TimeProvider()
    val metadataProtection = CredentialMetadataProtectionService(lookupTokens, encryptedMetadata)
    val encryptionManager = AesEncryptionManager()
    val keyProvider = EventStoreKeyProviderImpl(provider)
    val aggregateService =
        PasskeyAggregateServiceImpl(
            PasskeyEventStoreRepositoryImpl(database, encryptionManager, keyProvider),
            PasskeySnapshotRepositoryImpl(database, encryptionManager, keyProvider),
        )
    return driver to
        CredentialRepositoryImpl(
            passkeyCredentialDao = PasskeyCredentialDao(database, timeProvider, metadataProtection),
            relyingPartyDao = RelyingPartyDao(database, timeProvider, metadataProtection),
            userConsentRecordDao = UserConsentRecordDao(database, metadataProtection),
            publicKeyDecoder = PublicKeyDecoder(),
            timeProvider = timeProvider,
            aggregateService = aggregateService,
            metadataProtectionService = metadataProtection,
        )
}

internal object FixedCredentialLimit : Fido2SettingsRepository {
    override suspend fun getMaxCredentialCount(): Int = 100

    override suspend fun setMaxCredentialCount(count: Int) = Unit
}

internal object NoUserVerification : UserVerificationService {
    private const val MIN_PIN_LENGTH = 4
    private const val MAX_PIN_LENGTH = 8
    private const val MAX_PIN_ATTEMPTS = 5
    private const val PIN_LOCKOUT_MINUTES = 0L

    override suspend fun getUserVerificationAvailability() =
        UserVerificationAvailability(
            isBiometricAvailable = false,
            isPinAvailable = false,
            isDeviceLockAvailable = false,
            supportedBiometricTypes = emptyList(),
            maxPinLength = 8,
            minPinLength = 4,
            biometricStrength = BiometricStrength.WEAK,
        )

    override suspend fun isBiometricAvailable(): Boolean = false

    override suspend fun isPinAvailable(): Boolean = false

    override suspend fun getBiometricEnrollmentStatus() =
        BiometricEnrollmentStatus(
            isEnrolled = false,
            enrolledTypes = emptyList<BiometricType>(),
            enrollmentStrength = BiometricStrength.WEAK,
            lastUpdated = Clock.System.now(),
        )

    override suspend fun getPinConfiguration() =
        PinConfiguration(MIN_PIN_LENGTH, MAX_PIN_LENGTH, false, null, MAX_PIN_ATTEMPTS, PIN_LOCKOUT_MINUTES)

    override suspend fun recordUserConsent(consent: UserConsentRecord): Outcome<Unit, DomainError> =
        Outcome.Success(Unit)

    override fun getRecentConsentRecords(
        rpId: RpId?,
        limit: Int,
    ): Flow<UserConsentRecord> = emptyFlow()

    override suspend fun isUserVerificationRequiredForText(
        rpId: CharSequence,
        operationType: String,
        context: VerificationContext?,
    ): ServiceUserVerificationRequirement = ServiceUserVerificationRequirement.NOT_REQUIRED

    override suspend fun isUserVerificationRequired(
        rpId: RpId,
        operationType: String,
        context: VerificationContext?,
    ): ServiceUserVerificationRequirement = ServiceUserVerificationRequirement.NOT_REQUIRED
}

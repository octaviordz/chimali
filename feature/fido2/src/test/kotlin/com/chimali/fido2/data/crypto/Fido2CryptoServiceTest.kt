package com.chimali.fido2.data.crypto

import com.chimali.core.security.api.HdkKeyPair
import com.chimali.core.security.api.HdkManager
import com.chimali.core.security.api.HdkResult
import com.chimali.core.security.hdkeys.P256Group
import com.chimali.fido2.domain.model.CredentialId
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.BeforeTest
import org.junit.jupiter.api.Nested
import kotlin.test.Test
import java.security.Security

/**
 * T145a unit tests for [Fido2CryptoService] using the HDK-based software key derivation.
 *
 * Uses MockK to mock [HdkManager] and [MasterSeedProvider], verifying:
 * - deterministic public key derivation per credential ID
 * - correct delegation to HdkManager.deriveHdk and HdkManager.blindPrivateKey
 * - signing produces a non-empty DER-encoded byte array
 */
class Fido2CryptoServiceTest {
    private lateinit var hdkManager: HdkManager
    private lateinit var masterSeedProvider: MasterSeedProvider
    private lateinit var service: Fido2CryptoService

    // Use a real root key pair and seed so we can verify math
    private val realSeed = ByteArray(32) { it.toByte() }
    private val realDeviceKeyPair: HdkKeyPair by lazy { P256Group.generateKeyPair().let { HdkKeyPair(P256Group.serializeScalar(it.first), P256Group.serializeElement(it.second)) } }

    @BeforeTest
    fun setUp() {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0

        hdkManager = mockk()
        masterSeedProvider = mockk()

        // Wire up defaults
        coEvery { masterSeedProvider.getMasterSeed() } returns realSeed
        coEvery { masterSeedProvider.getDeviceKeyPair() } returns realDeviceKeyPair

        service = Fido2CryptoService(hdkManager, masterSeedProvider, PostQuantumCrypto(), UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    @Nested
    inner class GenerateCredentialKeyPairTests {
        @Test
        fun `generateCredentialKeyPair delegates to HdkManager deriveHdk`() =
            runTest {
                val credentialId = "test-cred-id-1"
                val devicePubKeyBytes = realDeviceKeyPair.publicKey
                val fakeResult =
                    HdkResult(
                        publicKey = realDeviceKeyPair.publicKey, // reuse for simplicity
                        salt = ByteArray(32),
                        blindingFactor = P256Group.serializeScalar(P256Group.randomScalar()),
                    )

                val capturedPath = slot<List<UInt>>()
                every {
                    hdkManager.deriveHdk(
                        devicePublicKey = devicePubKeyBytes,
                        seed = realSeed,
                        path = capture(capturedPath),
                    )
                } returns fakeResult

                val result = service.generateCredentialKeyPair(CredentialId.fromString(credentialId))

                assertTrue(result.isSuccess)
                val keyPair = result.getOrThrow()
                assertEquals(Fido2CryptoService.credentialAlias(CredentialId.fromString(credentialId)), keyPair.alias)
                assertEquals(65, keyPair.publicKeyBytes.size)
                assertEquals(0x04.toByte(), keyPair.publicKeyBytes[0])
                // Path must be a 2-element list [FIDO2_APP_INDEX, credIndex]
                assertEquals(2, capturedPath.captured.size)
            }

        @Test
        fun `generateCredentialKeyPair returns same public key for same credentialId`() =
            runTest {
                val credentialId = "stable-cred"
                val (sk, pk) = P256Group.generateKeyPair()
                val fakeResult = HdkResult(P256Group.serializeElement(pk), ByteArray(32), P256Group.serializeScalar(P256Group.randomScalar()))

                every { hdkManager.deriveHdk(any(), any(), any()) } returns fakeResult

                val result1 = service.generateCredentialKeyPair(CredentialId.fromString(credentialId)).getOrThrow()
                val result2 = service.generateCredentialKeyPair(CredentialId.fromString(credentialId)).getOrThrow()

                assertTrue(result1.publicKeyBytes.contentEquals(result2.publicKeyBytes))
            }

        @Test
        fun `generateCredentialKeyPair returns different public keys for different credentialIds`() =
            runTest {
                val (_, pk1) = P256Group.generateKeyPair()
                val (_, pk2) = P256Group.generateKeyPair()
                var callCount = 0
                every { hdkManager.deriveHdk(any(), any(), any()) } answers {
                    if (callCount++ == 0) {
                        HdkResult(P256Group.serializeElement(pk1), ByteArray(32), P256Group.serializeScalar(P256Group.randomScalar()))
                    } else {
                        HdkResult(P256Group.serializeElement(pk2), ByteArray(32), P256Group.serializeScalar(P256Group.randomScalar()))
                    }
                }

                val key1 = service.generateCredentialKeyPair(CredentialId.fromString("cred-1")).getOrThrow()
                val key2 = service.generateCredentialKeyPair(CredentialId.fromString("cred-2")).getOrThrow()

                assertTrue(!key1.publicKeyBytes.contentEquals(key2.publicKeyBytes))
            }

        @Test
        fun `generateCredentialKeyPair fails when master seed not available`() =
            runTest {
                coEvery { masterSeedProvider.getMasterSeed() } returns null

                val result = service.generateCredentialKeyPair(CredentialId.fromString("any-cred"))

                assertTrue(result.isFailure)
            }
    }

    @Nested
    inner class SignTests {
        @Test
        fun `sign produces non-empty signature bytes`() =
            runTest {
                val credentialId = "sign-cred"
                val data = "authData + clientDataHash".toByteArray()

                // Use real HDK derivation to sign
                val realHdkManager = com.chimali.core.security.hdkeys.HdkEcdhP256()
                val realService = Fido2CryptoService(realHdkManager, masterSeedProvider, PostQuantumCrypto(), UnconfinedTestDispatcher())

                val result = realService.sign(CredentialId.fromString(credentialId), data)

                assertTrue(result.isSuccess)
                val signature = result.getOrThrow()
                assertTrue(signature.isNotEmpty())
                // DER ECDSA signatures start with 0x30
                assertEquals(0x30.toByte(), signature[0])
            }
    }

    @Nested
    inner class GetPublicKeyTests {
        @Test
        fun `getPublicKey returns non-null PublicKey for valid credentialId`() =
            runTest {
                val (_, pk) = P256Group.generateKeyPair()
                every { hdkManager.deriveHdk(any(), any(), any()) } returns HdkResult(P256Group.serializeElement(pk), ByteArray(32), P256Group.serializeScalar(P256Group.randomScalar()))

                val publicKey = service.getPublicKey(CredentialId.fromString("some-cred"), Fido2CryptoService.COSE_ES256)

                assertNotNull(publicKey)
            }
    }

    /**
     * T148b — Known-Answer Tests (KAT) for deterministic HDK key derivation.
     *
     * Verifies Constitution §II and SC-006: the same BIP39 seed MUST always derive
     * the exact same public key for a given credential ID, enabling wallet recovery.
     *
     * Uses a real [HdkEcdhP256] instance (no mocking) so the math is actually exercised.
     */
    @Nested
    inner class KnownAnswerTests {
        private val realHdkManager = com.chimali.core.security.hdkeys.HdkEcdhP256()

        @Test
        fun `T148b same seed and credentialId always derives the same public key`() =
            runTest {
                // Given: a fixed seed (simulating recovery from the same 24-word mnemonic)
                val fixedSeed = ByteArray(32) { (it * 7 + 3).toByte() }
                val credentialId = "kat-credential-stable"

                coEvery { masterSeedProvider.getMasterSeed() } returns fixedSeed
                coEvery { masterSeedProvider.getDeviceKeyPair() } returns realDeviceKeyPair

                val realService = Fido2CryptoService(realHdkManager, masterSeedProvider, PostQuantumCrypto(), UnconfinedTestDispatcher())

                // When: derive twice from the same seed
                val keyPair1 = realService.generateCredentialKeyPair(CredentialId.fromString(credentialId)).getOrThrow()
                val keyPair2 = realService.generateCredentialKeyPair(CredentialId.fromString(credentialId)).getOrThrow()

                // Then: public key bytes are identical (determinism — SC-006)
                assertTrue(
                    keyPair1.publicKeyBytes.contentEquals(keyPair2.publicKeyBytes),
                    "Same seed must always yield the same public key (SC-006)",
                )
            }

        @Test
        fun `T148b different seeds derive different public keys for the same credentialId`() =
            runTest {
                // Given: two different seeds (different wallet recoveries)
                val seed1 = ByteArray(32) { it.toByte() }
                val seed2 = ByteArray(32) { (255 - it).toByte() }
                val credentialId = "kat-credential-different-seeds"

                val realService1 = Fido2CryptoService(realHdkManager, masterSeedProvider, PostQuantumCrypto(), UnconfinedTestDispatcher())
                val realService2 = Fido2CryptoService(realHdkManager, masterSeedProvider, PostQuantumCrypto(), UnconfinedTestDispatcher())

                coEvery { masterSeedProvider.getMasterSeed() } returns seed1
                coEvery { masterSeedProvider.getDeviceKeyPair() } returns realDeviceKeyPair
                val keyPair1 = realService1.generateCredentialKeyPair(CredentialId.fromString(credentialId)).getOrThrow()

                coEvery { masterSeedProvider.getMasterSeed() } returns seed2
                val keyPair2 = realService2.generateCredentialKeyPair(CredentialId.fromString(credentialId)).getOrThrow()

                // Then: Different seeds must produce different public keys
                assertTrue(
                    !keyPair1.publicKeyBytes.contentEquals(keyPair2.publicKeyBytes),
                    "Different seeds must produce different public keys",
                )
            }

        @Test
        fun `T148b after seed import re-derived keys differ from old seed keys`() =
            runTest {
                // Given: derive a key with the original seed
                val oldSeed = ByteArray(32) { (it + 1).toByte() }
                val newSeed = ByteArray(32) { (it + 100).toByte() }
                val credentialId = "kat-cred-post-import"

                val realService = Fido2CryptoService(realHdkManager, masterSeedProvider, PostQuantumCrypto(), UnconfinedTestDispatcher())

                coEvery { masterSeedProvider.getMasterSeed() } returns oldSeed
                coEvery { masterSeedProvider.getDeviceKeyPair() } returns realDeviceKeyPair
                val keysBeforeImport = realService.generateCredentialKeyPair(CredentialId.fromString(credentialId)).getOrThrow()

                // Simulate seed import (cache invalidated, new seed returned)
                coEvery { masterSeedProvider.getMasterSeed() } returns newSeed
                val keysAfterImport = realService.generateCredentialKeyPair(CredentialId.fromString(credentialId)).getOrThrow()

                assertTrue(
                    !keysBeforeImport.publicKeyBytes.contentEquals(keysAfterImport.publicKeyBytes),
                    "After seed replace, derived public keys must change (orphaned credential warning)",
                )
            }

        /**
         * T173 / T181 — End-to-end HDK KAT for the two-level FIDO2 path.
         *
         * Fixed inputs (do NOT change without regenerating expectedHex):
         *   seed       = ByteArray(32) { (it * 2).toByte() }  → [0, 2, 4, …, 62]
         *   deviceSk   = BigInteger.ONE
         *   devicePk   = P256Group.G  (the P-256 generator)
         *   credentialId = "kat-t173-two-level-path-stable-reference"
         *
         * The derivation path is [FIDO2_APP_INDEX, credentialPathIndex(credentialId)].
         * expectedHex was captured from a clean local run after the T166 DeriveSalt fix.
         * Any change to the hash preimage, blinding formula, or I2OSP encoding will
         * cause this test to fail — which is the intent.
         *
         * Verification (Python 3):
         *   See specs/004-fido2-hid/checklists/hdk-conformance.md §T173 for the full
         *   step-by-step reference computation.
         */
        @Test
        fun `t173 end-to-end HDK KAT for the two-level FIDO2 path`() =
            runTest {
                val fixedSeed = ByteArray(32) { (it * 2).toByte() }
                val credentialId = "kat-t173-two-level-path-stable-reference"

                val fixedDeviceSk = java.math.BigInteger.ONE
                val fixedDevicePk = com.chimali.core.security.hdkeys.P256Group.G
                val fixedDeviceKeyPair = HdkKeyPair(P256Group.serializeScalar(fixedDeviceSk), P256Group.serializeElement(fixedDevicePk))

                coEvery { masterSeedProvider.getMasterSeed() } returns fixedSeed
                coEvery { masterSeedProvider.getDeviceKeyPair() } returns fixedDeviceKeyPair

                val realService =
                    Fido2CryptoService(
                        realHdkManager,
                        masterSeedProvider,
                        PostQuantumCrypto(),
                        UnconfinedTestDispatcher(),
                    )
                val keyPair =
                    realService.generateCredentialKeyPair(
                        CredentialId.fromString(credentialId),
                    ).getOrThrow()

                // Pinned expected value — captured from local run after UInt fix.
                // The raw 65-byte uncompressed P-256 point: 0x04 || X (32 bytes) || Y (32 bytes).
                val expectedHex = "04962A65A7E025CCFE68130E0BC74AC061736FE5AE48CF63B7937B5F2B84875491DEB440FC850B2D88AFEA9A492866DA0AA0B80D3B423012DD71767F9F50A219FE"
                val actualHex = keyPair.publicKeyBytes.joinToString("") { "%02X".format(it) }

                assertEquals(
                    65,
                    keyPair.publicKeyBytes.size,
                    "Public key must be 65-byte uncompressed P-256 point",
                )
                assertEquals(
                    0x04.toByte(),
                    keyPair.publicKeyBytes[0],
                    "Must start with 0x04 uncompressed prefix",
                )
                assertEquals(
                    expectedHex,
                    actualHex,
                    "T173/T181: End-to-end HDK derivation output changed. " +
                        "If this is intentional (e.g. after changing KAT inputs), " +
                        "update expectedHex to: $actualHex",
                )
            }
    }
}

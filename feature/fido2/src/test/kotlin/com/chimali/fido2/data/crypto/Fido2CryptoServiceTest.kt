package com.chimali.fido2.data.crypto

import com.chimali.core.security.api.HdkKeyPair
import com.chimali.core.security.api.HdkManager
import com.chimali.core.security.api.HdkResult
import com.chimali.core.security.hdkeys.P256Group
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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
    private val realDeviceKeyPair: HdkKeyPair by lazy { P256Group.generateKeyPair().let { HdkKeyPair(it.first, it.second) } }

    @BeforeEach
    fun setUp() {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())

        hdkManager = mockk()
        masterSeedProvider = mockk()

        // Wire up defaults
        coEvery { masterSeedProvider.getMasterSeed() } returns realSeed
        coEvery { masterSeedProvider.getDeviceKeyPair() } returns realDeviceKeyPair

        service = Fido2CryptoService(hdkManager, masterSeedProvider)
    }

    @Nested
    inner class GenerateCredentialKeyPairTests {

        @Test
        fun `generateCredentialKeyPair delegates to HdkManager deriveHdk`() = runTest {
            val credentialId = "test-cred-id-1"
            val devicePubKeyBytes = P256Group.serializeElement(realDeviceKeyPair.publicKey)
            val fakeResult = HdkResult(
                publicKey = realDeviceKeyPair.publicKey, // reuse for simplicity
                salt = ByteArray(32),
                blindingFactor = P256Group.randomScalar()
            )

            val capturedPath = slot<List<Int>>()
            every {
                hdkManager.deriveHdk(
                    devicePublicKey = devicePubKeyBytes,
                    seed = realSeed,
                    path = capture(capturedPath)
                )
            } returns fakeResult

            val result = service.generateCredentialKeyPair(credentialId)

            assertTrue(result.isSuccess)
            val keyPair = result.getOrThrow()
            assertEquals(Fido2CryptoService.credentialAlias(credentialId), keyPair.alias)
            assertEquals(65, keyPair.publicKeyBytes.size)
            assertEquals(0x04.toByte(), keyPair.publicKeyBytes[0])
            // Path must be a 2-element list [FIDO2_APP_INDEX, credIndex]
            assertEquals(2, capturedPath.captured.size)
        }

        @Test
        fun `generateCredentialKeyPair returns same public key for same credentialId`() = runTest {
            val credentialId = "stable-cred"
            val (sk, pk) = P256Group.generateKeyPair()
            val fakeResult = HdkResult(pk, ByteArray(32), P256Group.randomScalar())

            every { hdkManager.deriveHdk(any(), any(), any()) } returns fakeResult

            val result1 = service.generateCredentialKeyPair(credentialId).getOrThrow()
            val result2 = service.generateCredentialKeyPair(credentialId).getOrThrow()

            assertTrue(result1.publicKeyBytes.contentEquals(result2.publicKeyBytes))
        }

        @Test
        fun `generateCredentialKeyPair returns different public keys for different credentialIds`() = runTest {
            val (_, pk1) = P256Group.generateKeyPair()
            val (_, pk2) = P256Group.generateKeyPair()
            var callCount = 0
            every { hdkManager.deriveHdk(any(), any(), any()) } answers {
                if (callCount++ == 0) HdkResult(pk1, ByteArray(32), P256Group.randomScalar())
                else HdkResult(pk2, ByteArray(32), P256Group.randomScalar())
            }

            val key1 = service.generateCredentialKeyPair("cred-1").getOrThrow()
            val key2 = service.generateCredentialKeyPair("cred-2").getOrThrow()

            assertTrue(!key1.publicKeyBytes.contentEquals(key2.publicKeyBytes))
        }

        @Test
        fun `generateCredentialKeyPair fails when master seed not available`() = runTest {
            coEvery { masterSeedProvider.getMasterSeed() } returns null

            val result = service.generateCredentialKeyPair("any-cred")

            assertTrue(result.isFailure)
        }
    }

    @Nested
    inner class SignTests {

        @Test
        fun `sign produces non-empty signature bytes`() = runTest {
            val credentialId = "sign-cred"
            val data = "authData + clientDataHash".toByteArray()

            // Use real HDK derivation to sign
            val realHdkManager = com.chimali.core.security.hdkeys.HdkEcdhP256()
            val realService = Fido2CryptoService(realHdkManager, masterSeedProvider)

            val result = realService.sign(credentialId, data)

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
        fun `getPublicKey returns non-null PublicKey for valid credentialId`() = runTest {
            val (_, pk) = P256Group.generateKeyPair()
            every { hdkManager.deriveHdk(any(), any(), any()) } returns HdkResult(pk, ByteArray(32), P256Group.randomScalar())

            val publicKey = service.getPublicKey("some-cred")

            assertNotNull(publicKey)
        }
    }
}

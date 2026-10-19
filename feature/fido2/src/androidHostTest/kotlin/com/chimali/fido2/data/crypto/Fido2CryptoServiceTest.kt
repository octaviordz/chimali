package com.chimali.fido2.data.crypto

import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.getOrThrow
import com.chimali.core.common.result.isFailure
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.security.api.HdkChild
import com.chimali.core.security.api.HdkKeyPair
import com.chimali.core.security.api.HdkManager
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.core.security.hdkeys.HdkP256
import com.chimali.core.security.hdkeys.HkdfSha256
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.security.GeneralSecurityException
import java.security.KeyPair
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.util.HexFormat
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.bouncycastle.jcajce.interfaces.MLDSAPublicKey
import org.bouncycastle.jce.provider.BouncyCastleProvider

/** Current local-profile integration tests against frozen independent reference outputs. */
@OptIn(ExperimentalCoroutinesApi::class)
class Fido2CryptoServiceTest {
    private val seedProvider = mockk<MasterSeedProvider>()
    private val root = ROOT_HEX.decodeHex()
    private val parentPrivate = PARENT_PRIVATE_HEX.decodeHex()
    private val parentPublic = PARENT_PUBLIC_HEX.decodeHex()
    private val issuedRootCopies = mutableListOf<ByteArray>()
    private val issuedParentPairs = mutableListOf<HdkKeyPair>()
    private var childDerivations = 0
    private lateinit var service: Fido2CryptoService

    @BeforeTest
    fun setUp() {
        Security.addProvider(BouncyCastleProvider())
        every { seedProvider.sessionGeneration() } returns 1L
        every { seedProvider.isSessionGenerationCurrent(any()) } returns true
        issuedRootCopies.clear()
        issuedParentPairs.clear()
        coEvery { seedProvider.getMasterSeed() } answers {
            root.copyOf().also(issuedRootCopies::add)
        }
        coEvery { seedProvider.getDeviceKeyPair() } answers {
            HdkKeyPair(parentPrivate.copyOf(), parentPublic.copyOf()).also(issuedParentPairs::add)
        }
        val countingHdkManager =
            object : HdkManager {
                override fun deriveChild(
                    localSecret: ByteArray,
                    parentPublicKey: ByteArray,
                    index: UInt,
                ): HdkChild {
                    childDerivations++
                    return HdkP256.deriveChild(localSecret, parentPublicKey, index)
                }

                override fun deriveChildPrivateKey(
                    parentPrivateKey: ByteArray,
                    factor: ByteArray,
                ): ByteArray = HdkP256.deriveChildPrivateKey(parentPrivateKey, factor)
            }
        childDerivations = 0
        service = Fido2CryptoService(countingHdkManager, seedProvider, PostQuantumCrypto(), UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        root.fill(0)
        parentPrivate.fill(0)
        parentPublic.fill(0)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `P-256 public key matches independent fixture and signature verifies`() =
        runTest {
            val descriptor = descriptor(CREDENTIAL_ID_HEX, CredentialKeyDescriptor.COSE_ES256)
            val generated = service.generateCredentialPublicKey(descriptor).getOrThrow()
            assertContentEquals(P256_PUBLIC_HEX.decodeHex(), generated.publicKey)
            assertEquals(1, childDerivations)

            val jcaKey = service.decodePublicKey(descriptor, generated.publicKey)
            val message = "fixture assertion".toByteArray(Charsets.US_ASCII)
            val signature = service.sign(descriptor, message, jcaKey.encoded).getOrThrow()
            Signature.getInstance("SHA256withECDSA", BouncyCastleProvider()).run {
                initVerify(jcaKey)
                update(message)
                assertTrue(verify(signature))
            }
            assertEquals(2, childDerivations)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `Ed25519 public key matches independent fixture and signature verifies`() =
        runTest {
            val descriptor = descriptor(CREDENTIAL_ID_HEX, CredentialKeyDescriptor.COSE_EDDSA)
            val generated = service.generateCredentialPublicKey(descriptor).getOrThrow()
            assertContentEquals(ED25519_PUBLIC_HEX.decodeHex(), generated.publicKey)
            val publicKey = service.decodePublicKey(descriptor, generated.publicKey)
            val message = "fixture assertion".toByteArray(Charsets.US_ASCII)
            val signature = service.sign(descriptor, message, publicKey.encoded).getOrThrow()
            Signature.getInstance("Ed25519", BouncyCastleProvider()).run {
                initVerify(publicKey)
                update(message)
                assertTrue(verify(signature))
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `ML-DSA public key matches the independent frozen fixture`() =
        runTest {
            val descriptor = descriptor(CREDENTIAL_ID_HEX, CredentialKeyDescriptor.COSE_ML_DSA_65)
            val generated = service.generateCredentialPublicKey(descriptor).getOrThrow()
            val publicKey = service.decodePublicKey(descriptor, generated.publicKey) as MLDSAPublicKey
            val digest = MessageDigest.getInstance("SHA-256").digest(publicKey.publicData)
            assertEquals(ML_DSA_PUBLIC_SHA256, digest.joinToString("") { "%02x".format(it) })
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `signing discards output when session generation is fenced during provider work`() =
        runTest {
            val descriptor = descriptor(CREDENTIAL_ID_HEX, CredentialKeyDescriptor.COSE_ES256)
            every { seedProvider.isSessionGenerationCurrent(any()) } returnsMany listOf(true, false)

            assertFailsWith<kotlinx.coroutines.CancellationException> {
                service.sign(descriptor, "stale synthetic assertion".toByteArray(Charsets.US_ASCII))
            }
            assertEquals(1, childDerivations)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `complete identifier distinguishes values colliding under retired shortened mapping`() =
        runTest {
            val first = descriptor(COLLISION_ID_ONE_HEX, CredentialKeyDescriptor.COSE_EDDSA)
            val second = descriptor(COLLISION_ID_TWO_HEX, CredentialKeyDescriptor.COSE_EDDSA)
            val keyOne = service.generateCredentialPublicKey(first).getOrThrow().publicKey
            val keyTwo = service.generateCredentialPublicKey(second).getOrThrow().publicKey
            assertNotEquals(keyOne.toList(), keyTwo.toList())
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `credential public output is stable for the same descriptor`() =
        runTest {
            val descriptor = descriptor(CREDENTIAL_ID_HEX, CredentialKeyDescriptor.COSE_EDDSA)
            val first = service.generateCredentialPublicKey(descriptor).getOrThrow().publicKey
            val second = service.generateCredentialPublicKey(descriptor).getOrThrow().publicKey
            assertContentEquals(first, second)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `sign rejects a valid but mismatched registered P-256 key before returning a signature`() =
        runTest {
            val descriptor = descriptor(CREDENTIAL_ID_HEX, CredentialKeyDescriptor.COSE_ES256)
            val otherCredential = descriptor(COLLISION_ID_ONE_HEX, CredentialKeyDescriptor.COSE_ES256)
            val mismatch = service.generateCredentialPublicKey(otherCredential).getOrThrow().publicKey

            val result = service.sign(descriptor, byteArrayOf(1, 2, 3), mismatch)

            assertTrue(result.isFailure)
            assertEquals(2, childDerivations)
            mismatch.fill(0)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `provider exception details are excluded from crypto error diagnostics`() =
        runTest {
            val secretMarker = "synthetic-private-material-marker"
            val failingManager =
                object : HdkManager {
                    override fun deriveChild(
                        localSecret: ByteArray,
                        parentPublicKey: ByteArray,
                        index: UInt,
                    ): HdkChild = throw GeneralSecurityException(secretMarker)

                    override fun deriveChildPrivateKey(
                        parentPrivateKey: ByteArray,
                        factor: ByteArray,
                    ): ByteArray = throw GeneralSecurityException(secretMarker)
                }
            val failingService =
                Fido2CryptoService(
                    failingManager,
                    seedProvider,
                    PostQuantumCrypto(),
                    UnconfinedTestDispatcher(),
                )

            val result =
                failingService.generateCredentialPublicKey(
                    descriptor(CREDENTIAL_ID_HEX, CredentialKeyDescriptor.COSE_ES256),
                )
            val error = (result as Outcome.Error).error

            assertEquals("Credential key derivation failed", error.message)
            assertNull(error.cause)
            assertTrue(!error.message.contains(secretMarker))
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `P-256 signing erases operation-owned secrets when child scalar derivation fails`() =
        runTest {
            listOf(
                GeneralSecurityException("synthetic signer failure"),
                CancellationException("synthetic signer cancellation"),
            ).forEach { failure ->
                var observedLocalSecret: ByteArray? = null
                var observedParentPrivate: ByteArray? = null
                var observedFactor: ByteArray? = null
                var observedChild: HdkChild? = null
                val failingManager =
                    object : HdkManager {
                        override fun deriveChild(
                            localSecret: ByteArray,
                            parentPublicKey: ByteArray,
                            index: UInt,
                        ): HdkChild {
                            observedLocalSecret = localSecret
                            val child = HdkP256.deriveChild(localSecret, parentPublicKey, index)
                            observedChild = child
                            return child
                        }

                        override fun deriveChildPrivateKey(
                            parentPrivateKey: ByteArray,
                            factor: ByteArray,
                        ): ByteArray {
                            observedParentPrivate = parentPrivateKey
                            observedFactor = factor
                            throw failure
                        }
                    }
                val failingService =
                    Fido2CryptoService(
                        failingManager,
                        seedProvider,
                        PostQuantumCrypto(),
                        UnconfinedTestDispatcher(),
                    )
                val descriptor = descriptor(CREDENTIAL_ID_HEX, CredentialKeyDescriptor.COSE_ES256)

                if (failure is CancellationException) {
                    assertFailsWith<CancellationException> {
                        failingService.sign(descriptor, byteArrayOf(0x01, 0x02, 0x03))
                    }
                } else {
                    val result = failingService.sign(descriptor, byteArrayOf(0x01, 0x02, 0x03))
                    assertTrue(result.isFailure)
                    assertEquals("Credential signing failed", (result as Outcome.Error).error.message)
                }

                assertTrue(issuedRootCopies.last().all { it == 0.toByte() })
                assertTrue(issuedParentPairs.last().privateKey.all { it == 0.toByte() })
                assertTrue(requireNotNull(observedLocalSecret).all { it == 0.toByte() })
                assertTrue(requireNotNull(observedParentPrivate).all { it == 0.toByte() })
                assertTrue(requireNotNull(observedFactor).all { it == 0.toByte() })
                assertTrue(requireNotNull(observedChild).factor.all { it == 0.toByte() })
            }
            assertEquals(2, issuedRootCopies.size)
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `Ed25519 signing erases the credential seed when session fencing cancels the result`() =
        runTest {
            val issuedSeeds = mutableListOf<ByteArray>()
            mockkObject(HkdfSha256)
            try {
                every {
                    HkdfSha256.deriveCredentialSeed(any(), any(), any())
                } answers {
                    callOriginal().also(issuedSeeds::add)
                }
                every { seedProvider.isSessionGenerationCurrent(any()) } returnsMany listOf(true, false)

                assertFailsWith<CancellationException> {
                    service.sign(
                        descriptor(CREDENTIAL_ID_HEX, CredentialKeyDescriptor.COSE_EDDSA),
                        byteArrayOf(0x01, 0x02, 0x03),
                    )
                }

                assertEquals(1, issuedSeeds.size)
                assertTrue(issuedSeeds.single().all { it == 0.toByte() })
                assertTrue(issuedRootCopies.single().all { it == 0.toByte() })
            } finally {
                unmockkObject(HkdfSha256)
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `ML-DSA signing erases xi and private encoding when session fencing cancels the result`() =
        runTest {
            val issuedSeeds = mutableListOf<ByteArray>()
            val privateEncoding = ByteArray(ML_DSA_PRIVATE_ENCODING_LENGTH) { 0x53 }
            val privateKey = mockk<PrivateKey> { every { encoded } returns privateEncoding }
            val publicKey = mockk<PublicKey>()
            val keyPair = KeyPair(publicKey, privateKey)
            val postQuantumCrypto = mockk<PostQuantumCrypto>()
            mockkObject(HkdfSha256)
            try {
                every {
                    HkdfSha256.deriveCredentialSeed(any(), any(), any())
                } answers {
                    callOriginal().also(issuedSeeds::add)
                }
                every { postQuantumCrypto.generateMlDsaKeyPair(any()) } returns keyPair
                every { postQuantumCrypto.publicKeyBytes(keyPair) } returns byteArrayOf(0x02)
                every { postQuantumCrypto.sign(privateKey, any()) } returns byteArrayOf(0x03)
                every { seedProvider.isSessionGenerationCurrent(any()) } returnsMany listOf(true, false)
                val mlDsaService =
                    Fido2CryptoService(
                        HdkP256,
                        seedProvider,
                        postQuantumCrypto,
                        UnconfinedTestDispatcher(),
                    )

                assertFailsWith<CancellationException> {
                    mlDsaService.sign(
                        descriptor(CREDENTIAL_ID_HEX, CredentialKeyDescriptor.COSE_ML_DSA_65),
                        byteArrayOf(0x01, 0x02, 0x03),
                    )
                }

                assertEquals(1, issuedSeeds.size)
                assertTrue(issuedSeeds.single().all { it == 0.toByte() })
                assertTrue(privateEncoding.all { it == 0.toByte() })
                assertTrue(issuedRootCopies.single().all { it == 0.toByte() })
            } finally {
                unmockkObject(HkdfSha256)
            }
        }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `descriptor rejects an algorithm paired with another profile`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialKeyDescriptor(
                CredentialId.fromByteArray(CREDENTIAL_ID_HEX.decodeHex()),
                CredentialKeyDescriptor.COSE_ES256,
                CredentialKeyDescriptor.ED25519_PROFILE,
            )
        }
    }

    private fun descriptor(
        idHex: String,
        algorithm: Int,
    ): CredentialKeyDescriptor =
        CredentialKeyDescriptor(
            credentialId = CredentialId.fromByteArray(idHex.decodeHex()),
            coseAlgorithm = algorithm,
            derivationProfile = CredentialKeyDescriptor.profileFor(algorithm),
        )

    private fun String.decodeHex(): ByteArray = HexFormat.of().parseHex(this)

    private companion object {
        const val ROOT_HEX = "aa6ff6f8d8e560bb6a8f8ff11f88b7684e184c480f30f17ac64a696730aacea2"
        const val PARENT_PRIVATE_HEX = "773c062d85301fafa2be488494743ff510be3113768bffb23efd225f7f374fc2"
        const val PARENT_PUBLIC_HEX =
            "04728ddf2908da797079d719fa48e749c50f46e45d22c990166aa8164f8345783" +
                "86b3faf07d3df11231dda5b929f4ec87ca140eb10d8bb95682808b4aa749bfae4"
        const val CREDENTIAL_ID_HEX = "0b30557a9fc4e90e33587da2c7ec1136"
        const val P256_PUBLIC_HEX =
            "042ba469fef75c4b6443f441f05f72da95fcd19e0738921f337ec247a29181571" +
                "0c855edac395bd09d395fa181bcfaecbae704e4c444ac31699d4567e3e779944e"
        const val ED25519_PUBLIC_HEX = "0d9acca24e59e3160e9e1526bfa77b385e78b275d9d1a7c046df447c0a2048c9"
        const val ML_DSA_PRIVATE_ENCODING_LENGTH = 4032
        const val ML_DSA_PUBLIC_SHA256 = "bd673eba4feb26964b9d1435692b042fc77c80af3a13fc59d632bccda40a16a4"
        const val COLLISION_ID_ONE_HEX = "00000000000076be636f6c6c6973696f6e2d66697874757265"
        const val COLLISION_ID_TWO_HEX = "0000000000011616636f6c6c6973696f6e2d66697874757265"
    }
}

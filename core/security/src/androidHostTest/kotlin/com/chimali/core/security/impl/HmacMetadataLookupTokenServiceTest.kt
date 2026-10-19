package com.chimali.core.security.impl

import com.chimali.core.common.annotation.Req
import com.chimali.core.security.api.MetadataLookupKeyUnavailableException
import com.chimali.core.security.api.MetadataLookupTokenService
import java.nio.CharBuffer
import java.nio.charset.MalformedInputException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies synchronized ownership of deterministic searchable-metadata lookup keys. */
@Req("NFR-SEC-041", "TR-AUTH-031")
class HmacMetadataLookupTokenServiceTest {
    private val service = HmacMetadataLookupTokenService()

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `generateToken is deterministic and domain separated`() {
        val key = ByteArray(KEY_BYTES) { it.toByte() }
        try {
            service.provisionKey(key)
            val first = service.generateToken(RP_DOMAIN, RP_VALUE)
            val repeated = service.generateToken(RP_DOMAIN, RP_VALUE)
            val otherDomain = service.generateToken(USER_DOMAIN, RP_VALUE)
            val otherKey = ByteArray(KEY_BYTES) { (it + 41).toByte() }
            val differentKey = referenceToken(otherKey, RP_DOMAIN, RP_VALUE)

            assertContentEquals(referenceToken(key, RP_DOMAIN, RP_VALUE), first)
            assertContentEquals(first, repeated)
            assertFalse(first.contentEquals(otherDomain))
            assertFalse(first.contentEquals(differentKey))
            first.fill(0)
            repeated.fill(0)
            otherDomain.fill(0)
            otherKey.fill(0)
            differentKey.fill(0)
        } finally {
            key.fill(0)
            service.clearKey()
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `generation snapshots key before provider setup and survives concurrent clear`() {
        val key = ByteArray(KEY_BYTES) { (it + 1).toByte() }
        val expected = referenceToken(key, RP_DOMAIN, RP_VALUE)
        val factoryEntered = CountDownLatch(1)
        val continueFactory = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        var operation: java.util.concurrent.Future<ByteArray>? = null
        var retainedOperationSnapshot: ByteArray? = null
        try {
            service.provisionKey(key)
            val retainedServiceOwner = serviceOwnedKey()
            operation =
                executor.submit<ByteArray> {
                    service.generateTokenWithMacFactory(
                        RP_DOMAIN,
                        RP_VALUE,
                        macFactory = { snapshot ->
                            retainedOperationSnapshot = snapshot
                            factoryEntered.countDown()
                            check(continueFactory.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                                "Timed out before JCA key capture"
                            }
                            Mac.getInstance(HMAC_ALGORITHM, REFERENCE_PROVIDER).apply {
                                init(SecretKeySpec(snapshot, HMAC_ALGORITHM))
                            }
                        },
                    )
                }

            assertTrue(factoryEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            service.clearKey()
            assertTrue(retainedServiceOwner.all { it == 0.toByte() })
            assertTrue(requireNotNull(retainedOperationSnapshot).any { it != 0.toByte() })
            assertFailsWith<MetadataLookupKeyUnavailableException> {
                service.generateToken(RP_DOMAIN, RP_VALUE)
            }

            continueFactory.countDown()
            val token = operation.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertContentEquals(expected, token)
            token.fill(0)
            assertTrue(requireNotNull(retainedOperationSnapshot).all { it == 0.toByte() })
            assertFailsWith<MetadataLookupKeyUnavailableException> {
                service.generateToken(RP_DOMAIN, RP_VALUE)
            }
        } finally {
            continueFactory.countDown()
            operation?.cancel(true)
            executor.shutdownNow()
            check(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "Timed out waiting for the race-test worker to terminate"
            }
            key.fill(0)
            expected.fill(0)
            service.clearKey()
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `operation snapshot and owned buffers are erased after success and admitted encoding failure`() {
        val key = ByteArray(KEY_BYTES) { (it + 23).toByte() }
        val invalidChars = charArrayOf('\uD800')
        val invalidValue = CharBuffer.wrap(invalidChars)
        var failureSnapshot: ByteArray? = null
        var failureOwnedBuffers: List<ByteArray>? = null
        var failureEncodingScratch: ByteArray? = null
        try {
            service.provisionKey(key)

            val successfulOwnedBuffers = mutableListOf<ByteArray>()
            val actual =
                service.generateTokenWithMacFactory(
                    RP_DOMAIN,
                    RP_VALUE,
                    macFactory = { snapshot ->
                        Mac.getInstance(HMAC_ALGORITHM, REFERENCE_PROVIDER).apply {
                            init(SecretKeySpec(snapshot, HMAC_ALGORITHM))
                        }
                    },
                    onOwnedBuffersErased = successfulOwnedBuffers::addAll,
                )
            val expected = referenceToken(key, RP_DOMAIN, RP_VALUE)
            assertContentEquals(expected, actual)
            assertTrue(successfulOwnedBuffers.size == 3)
            assertTrue(successfulOwnedBuffers.all { buffer -> buffer.all { it == 0.toByte() } })
            actual.fill(0)
            expected.fill(0)

            val failure =
                assertFailsWith<MalformedInputException> {
                    service.generateTokenWithMacFactory(
                        RP_DOMAIN,
                        invalidValue,
                        macFactory = { snapshot ->
                            failureSnapshot = snapshot
                            Mac.getInstance(HMAC_ALGORITHM, REFERENCE_PROVIDER).apply {
                                init(SecretKeySpec(snapshot, HMAC_ALGORITHM))
                            }
                        },
                        onOwnedBuffersErased = { buffers -> failureOwnedBuffers = buffers },
                        onEncodedScratchErased = { scratch -> failureEncodingScratch = scratch },
                    )
                }
            assertTrue(failure.message?.isNotBlank() == true)
            assertContentEquals(charArrayOf('\uD800'), invalidChars)
            assertTrue(requireNotNull(failureSnapshot).all { it == 0.toByte() })
            assertTrue(requireNotNull(failureOwnedBuffers).size == 2)
            assertTrue(requireNotNull(failureOwnedBuffers).all { buffer -> buffer.all { it == 0.toByte() } })
            assertTrue(requireNotNull(failureEncodingScratch).all { it == 0.toByte() })

            val successor = service.generateToken(RP_DOMAIN, RP_VALUE)
            val successorExpected = referenceToken(key, RP_DOMAIN, RP_VALUE)
            assertContentEquals(successorExpected, successor)
            successor.fill(0)
            successorExpected.fill(0)
        } finally {
            key.fill(0)
            service.clearKey()
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `replacement erases prior service-owned key and uses replacement`() {
        val original = ByteArray(KEY_BYTES) { it.toByte() }
        val replacement = ByteArray(KEY_BYTES) { (it + 17).toByte() }
        try {
            service.provisionKey(original)
            val previousOwner = serviceOwnedKey()
            service.provisionKey(replacement)

            assertTrue(previousOwner.all { it == 0.toByte() })
            val actual = service.generateToken(RP_DOMAIN, RP_VALUE)
            val expected = referenceToken(replacement, RP_DOMAIN, RP_VALUE)
            assertContentEquals(expected, actual)
            actual.fill(0)
            expected.fill(0)
        } finally {
            original.fill(0)
            replacement.fill(0)
            service.clearKey()
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `unprovisioned and cleared generation fail until key is provisioned again`() {
        assertFailsWith<MetadataLookupKeyUnavailableException> {
            service.generateToken(RP_DOMAIN, RP_VALUE)
        }

        val key = ByteArray(KEY_BYTES) { (it + 31).toByte() }
        try {
            service.provisionKey(key)
            service.generateToken(RP_DOMAIN, RP_VALUE).fill(0)
            service.clearKey()
            assertFailsWith<MetadataLookupKeyUnavailableException> {
                service.generateToken(RP_DOMAIN, RP_VALUE)
            }

            service.provisionKey(key)
            service.generateToken(RP_DOMAIN, RP_VALUE).fill(0)
        } finally {
            key.fill(0)
            service.clearKey()
        }
    }

    private fun serviceOwnedKey(): ByteArray {
        val field = HmacMetadataLookupTokenService::class.java.getDeclaredField("indexKey")
        field.isAccessible = true
        return requireNotNull(field.get(service) as ByteArray?)
    }

    private fun referenceToken(
        key: ByteArray,
        domain: String,
        value: String,
    ): ByteArray {
        val framing =
            domain.toByteArray(Charsets.UTF_8) + MetadataLookupTokenService.VERSION_1.toByte() +
                value.toByteArray(Charsets.UTF_8)
        val referenceKey = SecretKeySpec(key, HMAC_ALGORITHM)
        return try {
            Mac.getInstance(HMAC_ALGORITHM, REFERENCE_PROVIDER).run {
                init(referenceKey)
                doFinal(framing)
            }
        } finally {
            framing.fill(0)
        }
    }

    private companion object {
        private const val KEY_BYTES = 32
        private const val TIMEOUT_SECONDS = 5L
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val REFERENCE_PROVIDER = "SunJCE"
        private const val RP_DOMAIN = MetadataLookupTokenService.DOMAIN_RP_ID
        private const val USER_DOMAIN = MetadataLookupTokenService.DOMAIN_USER_ID
        private const val RP_VALUE = "https://synthetic.example"
    }
}

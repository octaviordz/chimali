package com.chimali.core.security.impl

import com.chimali.core.common.annotation.Req
import com.chimali.core.security.api.MetadataLookupKeyUnavailableException
import com.chimali.core.security.api.MetadataLookupTokenService
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.koin.core.annotation.Single

/**
 * Provides keyed, deterministic tokens for exact-match searchable metadata.
 *
 * The service erases its mutable key snapshot and owned input buffers after each operation.
 * [SecretKeySpec] and the JCA [Mac] provider may retain internal key copies beyond that lifetime;
 * this service cannot erase provider-managed storage and makes no physical-erasure claim.
 */
@Single
class HmacMetadataLookupTokenService : MetadataLookupTokenService {
    companion object {
        private const val ALGORITHM = "HmacSHA256"
        private const val MAX_UTF8_BYTES_PER_CHARACTER = 4
    }

    private val keyLock = Any()
    private var indexKey: ByteArray? = null

    /** Replaces the owned index key copy and erases the previous copy while key ownership is locked.
     * In-flight operations use separate owned snapshots, so the borrowed input remains untouched.
     *
     * @param key borrowed key bytes copied into service ownership
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun provisionKey(key: ByteArray) {
        val replacement = key.copyOf()
        synchronized(keyLock) {
            indexKey?.fill(0)
            indexKey = replacement
        }
    }

    /** Erases and removes the provisioned key; later calls fail until another key is provisioned. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun clearKey() {
        synchronized(keyLock) {
            indexKey?.fill(0)
            indexKey = null
        }
    }

    /** Generates a deterministic lookup token using an owned key snapshot that is erased on return.
     *
     * @param domain borrowed domain-separation text
     * @param value borrowed metadata text encoded strictly as UTF-8
     * @return caller-owned token bytes; the caller must erase them when no longer needed
     * @throws MetadataLookupKeyUnavailableException when the service has no provisioned key
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override fun generateToken(
        domain: String,
        value: CharSequence,
    ): ByteArray =
        generateTokenWithMacFactory(
            domain,
            value,
            macFactory = { operationKey ->
                Mac.getInstance(ALGORITHM).apply { init(SecretKeySpec(operationKey, ALGORITHM)) }
            },
        )

    /** Executes the token operation through an instance-local seam for lifecycle verification.
     *
     * The factory must initialize a MAC synchronously from the supplied borrowed snapshot and must
     * not retain it. The optional cleanup observer exists only for host tests; it receives owned
     * buffers after they have been erased.
     *
     * @param domain borrowed domain-separation text
     * @param value borrowed metadata text encoded strictly as UTF-8
     * @param macFactory creates and initializes a MAC using the operation snapshot
     * @param onOwnedBuffersErased optional host-test observer for erased operation buffers
     * @param onEncodedScratchErased optional host-test observer for the erased encoder scratch
     * @return caller-owned token bytes
     * @throws MetadataLookupKeyUnavailableException when the service has no provisioned key
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    internal fun generateTokenWithMacFactory(
        domain: String,
        value: CharSequence,
        macFactory: (ByteArray) -> Mac,
        onOwnedBuffersErased: ((List<ByteArray>) -> Unit)? = null,
        onEncodedScratchErased: ((ByteArray) -> Unit)? = null,
    ): ByteArray {
        val operationKey =
            synchronized(keyLock) {
                indexKey?.copyOf() ?: throw MetadataLookupKeyUnavailableException()
            }
        var domainBytes: ByteArray? = null
        var valueBytes: ByteArray? = null
        return try {
            val mac = macFactory(operationKey)
            domainBytes = domain.toByteArray(StandardCharsets.UTF_8)
            valueBytes = encodeUtf8(value, onEncodedScratchErased)
            mac.update(requireNotNull(domainBytes))
            mac.update(MetadataLookupTokenService.VERSION_1.toByte())
            mac.doFinal(requireNotNull(valueBytes))
        } finally {
            operationKey.fill(0)
            domainBytes?.fill(0)
            valueBytes?.fill(0)
            onOwnedBuffersErased?.invoke(listOfNotNull(operationKey, domainBytes, valueBytes))
        }
    }

    private fun encodeUtf8(
        value: CharSequence,
        onScratchErased: ((ByteArray) -> Unit)?,
    ): ByteArray {
        val output = ByteBuffer.allocate(Math.multiplyExact(value.length, MAX_UTF8_BYTES_PER_CHARACTER))
        try {
            val encoder =
                StandardCharsets.UTF_8
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
            val encoded = encoder.encode(CharBuffer.wrap(value), output, true)
            if (encoded.isError) encoded.throwException()
            val flushed = encoder.flush(output)
            if (flushed.isError) flushed.throwException()
            output.flip()
            return ByteArray(output.remaining()).also(output::get)
        } finally {
            output.array().fill(0)
            onScratchErased?.invoke(output.array())
        }
    }
}

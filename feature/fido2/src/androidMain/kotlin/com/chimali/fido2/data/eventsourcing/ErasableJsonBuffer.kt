package com.chimali.fido2.data.eventsourcing

import com.chimali.core.common.annotation.Req
import java.io.OutputStream

/** Mutable JSON output whose complete backing storage can be erased after use or failure. */
internal class ErasableJsonBuffer(
    initialCapacity: Int = 256,
) : OutputStream() {
    private var bytes = ByteArray(initialCapacity)
    private var length = 0

    /** Appends one low-order byte, growing owned storage and erasing any replaced buffer. */
    @Req("NFR-SEC-010", "NFR-SEC-041")
    override fun write(value: Int) {
        ensureCapacity(1)
        bytes[length++] = value.toByte()
    }

    /** Copies [count] bytes from [source] into this owner; the source remains borrowed. */
    @Req("NFR-SEC-010", "NFR-SEC-041")
    override fun write(
        source: ByteArray,
        offset: Int,
        count: Int,
    ) {
        require(offset >= 0 && count >= 0 && offset <= source.size - count)
        ensureCapacity(count)
        source.copyInto(bytes, length, offset, offset + count)
        length += count
    }

    /** Returns an independent caller-owned copy of the written prefix. */
    @Req("NFR-SEC-010", "NFR-SEC-041")
    fun copyToOwnedBytes(): ByteArray = bytes.copyOf(length)

    /** Zeroes the complete backing array and resets the visible length; safe to call repeatedly. */
    @Req("NFR-SEC-010", "NFR-SEC-041")
    fun erase() {
        bytes.fill(0)
        length = 0
    }

    /** Test inspection that reports whether length is zero and all owned bytes were overwritten. */
    @Req("NFR-SEC-010", "NFR-SEC-041")
    internal fun isErased(): Boolean = length == 0 && bytes.all { it == 0.toByte() }

    private fun ensureCapacity(additional: Int) {
        val required = Math.addExact(length, additional)
        if (required <= bytes.size) return
        val capacity = maxOf(bytes.size.toLong() * 2, required.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val previous = bytes
        bytes = previous.copyOf(capacity)
        previous.fill(0)
    }
}

/** Returns an owned serialization copy and always erases the serializer's backing buffer. */
@Req("NFR-SEC-010", "NFR-SEC-041")
internal inline fun encodeToErasableJsonBytes(encode: (OutputStream) -> Unit): ByteArray {
    val buffer = ErasableJsonBuffer()
    try {
        encode(buffer)
        return buffer.copyToOwnedBytes()
    } finally {
        buffer.erase()
    }
}

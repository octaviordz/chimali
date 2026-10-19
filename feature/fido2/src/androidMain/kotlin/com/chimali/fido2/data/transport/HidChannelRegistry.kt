package com.chimali.fido2.data.transport

import com.chimali.core.common.annotation.Req

/** Owns bounded CTAPHID channel IDs and binds each allocation to one host generation. */
internal class HidChannelRegistry {
    private data class ChannelOwner(
        val sessionGeneration: Long,
        val cid: ByteArray,
    )

    private val lock = Any()
    private val channels = mutableMapOf<String, ChannelOwner>()

    /** Allocates one unique channel while the caller holds the transport session lock. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun allocate(
        sessionGeneration: Long,
        maxAttempts: Int,
        maximumChannels: Int,
        isSessionCurrent: () -> Boolean,
        nextCidBytes: (ByteArray) -> Unit,
    ): ByteArray? {
        val cid = ByteArray(CID_SIZE)
        repeat(maxAttempts) {
            nextCidBytes(cid)
            val assigned =
                synchronized(lock) {
                    if (!isSessionCurrent()) return@synchronized false
                    val key = cid.toHex()
                    val available =
                        channels.size < maximumChannels &&
                            !cid.contentEquals(BROADCAST_CID) &&
                            !channels.containsKey(key)
                    if (available) channels[key] = ChannelOwner(sessionGeneration, cid)
                    available
                }
            if (assigned) return cid
        }
        cid.fill(0)
        return null
    }

    /** Erases every owned CID before dropping its generation registry. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun clear() {
        synchronized(lock) {
            channels.values.forEach { it.cid.fill(0) }
            channels.clear()
        }
    }

    /** Registers a copied CID for direct host-dispatch regressions. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun registerForTest(
        cid: ByteArray,
        generation: Long,
        maximumChannels: Int,
    ) {
        require(cid.size == CID_SIZE)
        synchronized(lock) {
            val key = cid.toHex()
            require(key in channels || channels.size < maximumChannels)
            channels[key] = ChannelOwner(generation, cid.copyOf())
        }
    }

    /** Checks both the generation and the full CID bytes before admitting a command. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun isRegistered(
        cid: ByteArray,
        generation: Long,
    ): Boolean =
        synchronized(lock) {
            channels[cid.toHex()]?.let { it.sessionGeneration == generation && it.cid.contentEquals(cid) } == true
        }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val CID_SIZE = 4
        val BROADCAST_CID = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
    }
}

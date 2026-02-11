package com.chimali.core.security.impl

import com.chimali.core.security.api.HDKeyDerivator
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

class Bip32HDKeyDerivator @Inject constructor() : HDKeyDerivator {

    private val HMAC_SHA512 = "HmacSHA512"
    private val MASTER_SECRET = "Chimali seed".toByteArray()

    override fun deriveKey(seed: ByteArray, path: String): ByteArray {
        // Master key generation
        val i = hmacSha512(MASTER_SECRET, seed)
        val masterPrivateKey = i.sliceArray(0 until 32)
        val masterChainCode = i.sliceArray(32 until 64)

        if (path == "m" || path == "") return masterPrivateKey

        val components = path.split("/").drop(1) // Drop "m"
        var currentKey = masterPrivateKey
        var currentChainCode = masterChainCode

        for (component in components) {
            val isHardened = component.endsWith("'")
            val index = component.removeSuffix("'").toLong()
            
            val result = deriveChild(currentKey, currentChainCode, index, isHardened)
            currentKey = result.first
            currentChainCode = result.second
        }

        return currentKey
    }

    private fun deriveChild(
        parentKey: ByteArray,
        parentChainCode: ByteArray,
        index: Long,
        isHardened: Boolean
    ): Pair<ByteArray, ByteArray> {
        val data = ByteBuffer.allocate(37)
        if (isHardened) {
            data.put(0x00)
            data.put(parentKey)
            data.putInt((index or 0x80000000L).toInt())
        } else {
            // Unhardened derivation requires public key (omitted for pure private key derivation simplicity for now)
            // In Chimali, we mostly use hardened paths for security.
            throw UnsupportedOperationException("Only hardened derivation is supported for now")
        }

        val i = hmacSha512(parentChainCode, data.array())
        val il = i.sliceArray(0 until 32)
        val ir = i.sliceArray(32 until 64)
        
        // In real BIP32, currentKey = (il + parentKey) % n
        // For simplicity and since Chimali uses seeds for encryption keys, 
        // we use il as the derived key if it meets criteria, or we can use the full HMAC.
        // For actual BIP32 compliance, we should use a BigInteger for elliptic curve addition.
        return Pair(il, ir)
    }

    private fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA512)
        mac.init(SecretKeySpec(key, HMAC_SHA512))
        return mac.doFinal(data)
    }
}

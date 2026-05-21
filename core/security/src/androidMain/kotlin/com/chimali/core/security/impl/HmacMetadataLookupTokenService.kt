package com.chimali.core.security.impl

import com.chimali.core.security.api.MetadataLookupTokenService
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.koin.core.annotation.Single

@Single
class HmacMetadataLookupTokenService : MetadataLookupTokenService {
    companion object {
        private const val ALGORITHM = "HmacSHA256"
    }

    private var indexKey: ByteArray? = null

    fun provisionKey(key: ByteArray) {
        indexKey = key.copyOf()
    }

    fun clearKey() {
        indexKey?.fill(0)
        indexKey = null
    }

    override fun generateToken(
        domain: String,
        value: String,
    ): ByteArray {
        val key = checkNotNull(indexKey) { "Index key not provisioned" }
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(key, ALGORITHM))

        mac.update(domain.toByteArray(Charsets.UTF_8))
        mac.update(MetadataLookupTokenService.VERSION_1.toByte())
        return mac.doFinal(value.toByteArray(Charsets.UTF_8))
    }
}

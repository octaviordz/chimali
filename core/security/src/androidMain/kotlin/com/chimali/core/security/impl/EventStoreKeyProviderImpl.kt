package com.chimali.core.security.impl

import com.chimali.core.security.api.EventStoreKeyProvider
import com.chimali.core.security.api.MasterSeedProvider
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.koin.core.annotation.Single

@Single
class EventStoreKeyProviderImpl(
    private val masterSeedProvider: MasterSeedProvider,
) : EventStoreKeyProvider {
    override suspend fun getEventStoreKey(aggregateLabel: String): ByteArray {
        val masterSeed =
            checkNotNull(masterSeedProvider.getMasterSeed()) {
                "Master seed not initialized. Cannot derive event store keys."
            }

        // T035: Derive a deterministic 256-bit key from the master seed using HMAC-SHA512
        // with the aggregateLabel as the salt/info to ensure isolation.
        val mac = Mac.getInstance("HmacSHA512")
        val keySpec = SecretKeySpec(masterSeed, "HmacSHA512")
        mac.init(keySpec)

        val fullHash = mac.doFinal(aggregateLabel.toByteArray(Charsets.UTF_8))

        // We take the first 32 bytes (256 bits) for the AES-256 key.
        return fullHash.copyOf(32)
    }
}

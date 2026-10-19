package com.chimali.core.security.impl

import com.chimali.core.common.annotation.Req
import com.chimali.core.security.api.EventStoreKeyProvider
import com.chimali.core.security.api.MasterSeedProvider
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.koin.core.annotation.Single

@Single
class EventStoreKeyProviderImpl(
    private val masterSeedProvider: MasterSeedProvider,
) : EventStoreKeyProvider {
    /** Derives and returns an owned event-store key, erasing the wallet-root copy and HMAC buffers. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    override suspend fun getEventStoreKey(aggregateLabel: String): ByteArray =
        masterSeedProvider.withSessionOwner { _ ->
            var rootPrk: ByteArray? = null
            var label: ByteArray? = null
            var fullHash: ByteArray? = null
            try {
                val ownedRootPrk =
                    checkNotNull(masterSeedProvider.getMasterSeed()) {
                        "Wallet root not initialized. Cannot derive event store keys."
                    }
                rootPrk = ownedRootPrk
                val encodedLabel = aggregateLabel.toByteArray(Charsets.UTF_8)
                label = encodedLabel
                // Preserve this consumer's existing HMAC-SHA512 label policy; the provider now
                // supplies an owned 32-byte root PRK copy instead of the full BIP39 seed.
                val mac = Mac.getInstance("HmacSHA512")
                mac.init(SecretKeySpec(ownedRootPrk, "HmacSHA512"))
                fullHash = mac.doFinal(encodedLabel)
                fullHash.copyOf(32)
            } finally {
                rootPrk?.fill(0)
                label?.fill(0)
                fullHash?.fill(0)
            }
        }
}

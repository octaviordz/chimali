package com.chimali.fido2.data.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.koin.core.annotation.Single
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

@Single
class PublicKeyDecoder {

    /**
     * Decodes a Base64-encoded public key string into a java.security.PublicKey.
     * 
     * @param base64Key The Base64 string representing the DER-encoded public key.
     * @param coseAlgorithm The COSE algorithm identifier.
     * @return Result containing the PublicKey, or failure if decoding or algorithm matching fails.
     */
    fun decodePublicKey(base64Key: String, coseAlgorithm: Int): Result<PublicKey> {
        return runCatching {
            val keyBytes = Base64.getDecoder().decode(base64Key)
            val keySpec = X509EncodedKeySpec(keyBytes)

            val keyFactory = when (coseAlgorithm) {
                COSE_ML_DSA_65 -> KeyFactory.getInstance("ML-DSA-65", BouncyCastleProvider.PROVIDER_NAME)
                COSE_ES256 -> KeyFactory.getInstance("EC") // Standard Java EC for P-256
                COSE_EDDSA -> KeyFactory.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME)
                else -> throw IllegalArgumentException("Unsupported COSE algorithm: $coseAlgorithm")
            }

            keyFactory.generatePublic(keySpec)
        }
    }
    
    companion object {
        const val COSE_ML_DSA_65 = -49
        const val COSE_ES256 = -7
        const val COSE_EDDSA = -8
    }
}

package com.chimali.fido2.data.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidKeyStoreWrapper @Inject constructor() : KeyStoreWrapper {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override suspend fun generateKeyPair(alias: String): Result<KeyPair> {
        return try {
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .build()

            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                "AndroidKeyStore"
            )
            keyPairGenerator.initialize(spec)
            Result.success(keyPairGenerator.generateKeyPair())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPublicKey(alias: String): PublicKey? {
        return try {
            keyStore.getCertificate(alias)?.publicKey
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getPrivateKey(alias: String): PrivateKey? {
        return try {
            keyStore.getKey(alias, null) as? PrivateKey
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun deleteKey(alias: String): Result<Unit> {
        return try {
            keyStore.deleteEntry(alias)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun keyExists(alias: String): Boolean {
        return keyStore.containsAlias(alias)
    }

    companion object {
        private const val EC_CURVE = "secp256r1"
    }
}

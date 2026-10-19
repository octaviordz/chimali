package com.chimali.fido2.data.crypto

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.fido2.util.crypto.BouncyCastleLoader
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import org.bouncycastle.jcajce.interfaces.MLDSAPrivateKey
import org.bouncycastle.jcajce.interfaces.MLDSAPublicKey
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec
import org.bouncycastle.jcajce.spec.MLDSAPrivateKeySpec
import org.koin.core.annotation.Single

/** COSE algorithm identifier for ML-DSA-65 (NIST FIPS 204, Level 3). */
const val COSE_ML_DSA_65 = -49

/** BC 1.80 ML-DSA-65 seed import, signing, verification and public-key encoding. */
@Req("NFR-SEC-041", "TR-AUTH-031")
@Single
class PostQuantumCrypto() {
    private var providerName: String = PROVIDER_NAME

    /** Test-only provider selection for exercising unavailable-provider error handling. */
    internal constructor(providerName: String) : this() {
        this.providerName = providerName
    }

    init {
        BouncyCastleLoader.ensureRegistered()
    }

    /**
     * Returns whether the configured JCA provider supplies ML-DSA-65 key generation; security
     * errors from the provider produce `false`.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun isMlDsaSupported(): Boolean =
        try {
            keyFactory()
            true
        } catch (_: java.security.GeneralSecurityException) {
            Logger.e { "ML-DSA-65 provider is unavailable" }
            false
        }

    /**
     * Imports a borrowed 32-byte FIPS 204 xi seed without a custom randomness adapter.
     * Copies and clears its working seed and exported private-key encoding; BC/JCA may retain
     * provider-owned key material. Returns an owned [KeyPair], or null when the provider fails.
     * Throws [IllegalArgumentException] when [xi] is not exactly 32 bytes.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun generateMlDsaKeyPair(xi: ByteArray): KeyPair? {
        require(xi.size == MLDSA_SEED_LENGTH) { "ML-DSA-65 xi must be exactly 32 bytes" }
        val ownedXi = xi.copyOf()
        var privateKeyEncoding: ByteArray? = null
        try {
            val privateKey =
                keyFactory().generatePrivate(MLDSAPrivateKeySpec(MLDSAParameterSpec.ml_dsa_65, ownedXi))
                    as MLDSAPrivateKey
            privateKeyEncoding = privateKey.encoded
            val publicKey = privateKey.getPublicKey() as MLDSAPublicKey
            return KeyPair(publicKey, privateKey)
        } catch (_: java.security.GeneralSecurityException) {
            Logger.e { "ML-DSA-65 seed import failed" }
            return null
        } finally {
            ownedXi.fill(0)
            privateKeyEncoding?.fill(0)
        }
    }

    /**
     * Signs borrowed [data] with [privateKey], returning an owned signature or null on JCA failure.
     * Clears an exported private-key encoding when available; the provider may retain internal copies.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun sign(
        privateKey: PrivateKey,
        data: ByteArray,
    ): ByteArray? {
        var privateEncoding: ByteArray? = null
        try {
            privateEncoding = privateKey.encoded
            val signer = Signature.getInstance(MLDSA_ALGORITHM, providerName)
            signer.initSign(privateKey)
            signer.update(data)
            return signer.sign()
        } catch (_: java.security.GeneralSecurityException) {
            Logger.e { "ML-DSA-65 signing failed" }
            return null
        } finally {
            privateEncoding?.fill(0)
        }
    }

    /**
     * Verifies borrowed [signature] and [data] with [publicKey]. Returns false when verification
     * fails or the configured JCA provider reports a security error; inputs remain caller-owned.
     */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun verify(
        publicKey: PublicKey,
        data: ByteArray,
        signature: ByteArray,
    ): Boolean =
        try {
            Signature
                .getInstance(MLDSA_ALGORITHM, providerName)
                .apply {
                    initVerify(publicKey)
                    update(data)
                }.verify(signature)
        } catch (_: java.security.GeneralSecurityException) {
            Logger.e { "ML-DSA-65 signature verification failed" }
            false
        }

    /** Returns an owned copy of the JCA X.509 public-key encoding. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun publicKeyBytes(keyPair: KeyPair): ByteArray = keyPair.public.encoded.copyOf()

    private fun keyFactory(): KeyFactory = KeyFactory.getInstance(MLDSA_ALGORITHM, providerName)

    private companion object {
        const val MLDSA_ALGORITHM = "ML-DSA-65"
        const val MLDSA_SEED_LENGTH = 32
        const val PROVIDER_NAME = "BC"
    }
}

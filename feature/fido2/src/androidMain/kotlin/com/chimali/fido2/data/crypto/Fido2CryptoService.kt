package com.chimali.fido2.data.crypto

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.security.api.GeneratedCredentialPublicKey
import com.chimali.core.security.api.HdkChild
import com.chimali.core.security.api.HdkKeyPair
import com.chimali.core.security.api.HdkManager
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.core.security.hdkeys.HkdfSha256
import com.chimali.core.security.hdkeys.P256Group
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.CryptoException
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

/** Routes each explicitly described credential through its independent local derivation branch. */
@Req("NFR-SEC-041", "TR-AUTH-031")
@Single
class Fido2CryptoService(
    private val hdkManager: HdkManager,
    private val masterSeedProvider: MasterSeedProvider,
    private val postQuantumCrypto: PostQuantumCrypto,
    @Named("DefaultDispatcher") private val defaultDispatcher: CoroutineDispatcher,
) {
    /** Derives the selected public key once and returns public bytes only. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun generateCredentialPublicKey(
        descriptor: CredentialKeyDescriptor,
    ): Outcome<GeneratedCredentialPublicKey, DomainError.CryptoError> =
        withContext(defaultDispatcher) {
            val admittedGeneration = masterSeedProvider.sessionGeneration()
            var publicKey: ByteArray? = null
            try {
                ensureCurrentGeneration(admittedGeneration)
                publicKey =
                    when (descriptor.coseAlgorithm) {
                        CredentialKeyDescriptor.COSE_ES256 -> {
                            requireProfile(descriptor, CredentialKeyDescriptor.P256_PROFILE)
                            deriveP256PublicKey(descriptor)
                        }
                        CredentialKeyDescriptor.COSE_EDDSA -> {
                            requireProfile(descriptor, CredentialKeyDescriptor.ED25519_PROFILE)
                            deriveEd25519PublicKey(descriptor)
                        }
                        CredentialKeyDescriptor.COSE_ML_DSA_65 -> {
                            requireProfile(descriptor, CredentialKeyDescriptor.MLDSA65_PROFILE)
                            deriveMlDsaPublicKey(descriptor)
                        }
                        else -> throw IllegalArgumentException("Unsupported credential algorithm")
                    }
                ensureCurrentGeneration(admittedGeneration)
                val result = GeneratedCredentialPublicKey(publicKey)
                publicKey = null
                Outcome.Success(result)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: GeneralSecurityException) {
                return@withContext cryptoFailure(PUBLIC_KEY_DERIVATION_FAILED, KEY_DERIVATION_FALLBACK)
            } catch (_: CryptoException) {
                return@withContext cryptoFailure(PUBLIC_KEY_DERIVATION_FAILED, KEY_DERIVATION_FALLBACK)
            } catch (_: IllegalArgumentException) {
                return@withContext cryptoFailure(PUBLIC_KEY_DERIVATION_FAILED, KEY_DERIVATION_FALLBACK)
            } catch (_: IllegalStateException) {
                return@withContext cryptoFailure(PUBLIC_KEY_DERIVATION_FAILED, KEY_DERIVATION_FALLBACK)
            } catch (_: SecurityException) {
                return@withContext cryptoFailure(PUBLIC_KEY_DERIVATION_FAILED, KEY_DERIVATION_FALLBACK)
            } finally {
                publicKey?.fill(0)
            }
        }

    /** Signs with one selected credential key derived from the descriptor's full decoded ID. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    suspend fun sign(
        descriptor: CredentialKeyDescriptor,
        data: ByteArray,
        expectedStoredPublicKey: ByteArray? = null,
    ): Outcome<ByteArray, DomainError.CryptoError> =
        withContext(defaultDispatcher) {
            val admittedGeneration = masterSeedProvider.sessionGeneration()
            var signature: ByteArray? = null
            try {
                ensureCurrentGeneration(admittedGeneration)
                signature =
                    when (descriptor.coseAlgorithm) {
                        CredentialKeyDescriptor.COSE_ES256 -> {
                            requireProfile(descriptor, CredentialKeyDescriptor.P256_PROFILE)
                            signP256(descriptor, data, expectedStoredPublicKey)
                        }
                        CredentialKeyDescriptor.COSE_EDDSA -> {
                            requireProfile(descriptor, CredentialKeyDescriptor.ED25519_PROFILE)
                            signEd25519(descriptor, data, expectedStoredPublicKey)
                        }
                        CredentialKeyDescriptor.COSE_ML_DSA_65 -> {
                            requireProfile(descriptor, CredentialKeyDescriptor.MLDSA65_PROFILE)
                            signMlDsa(descriptor, data, expectedStoredPublicKey)
                        }
                        else -> throw IllegalArgumentException("Unsupported credential algorithm")
                    }
                ensureCurrentGeneration(admittedGeneration)
                val result = signature
                signature = null
                Outcome.Success(result)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: GeneralSecurityException) {
                return@withContext cryptoFailure(SIGNING_FAILED, SIGNING_FAILED)
            } catch (_: CryptoException) {
                return@withContext cryptoFailure(SIGNING_FAILED, SIGNING_FAILED)
            } catch (_: IllegalArgumentException) {
                return@withContext cryptoFailure(SIGNING_FAILED, SIGNING_FAILED)
            } catch (_: IllegalStateException) {
                return@withContext cryptoFailure(SIGNING_FAILED, SIGNING_FAILED)
            } catch (_: SecurityException) {
                return@withContext cryptoFailure(SIGNING_FAILED, SIGNING_FAILED)
            } finally {
                signature?.fill(0)
            }
        }

    /** Discards operation output when trim/replacement has fenced its provider generation. */

    private fun ensureCurrentGeneration(generation: Long) {
        if (!masterSeedProvider.isSessionGenerationCurrent(generation)) {
            throw CancellationException("Wallet session generation changed during credential operation")
        }
    }

    private fun cryptoFailure(
        operation: String,
        fallback: String,
    ): Outcome.Error<DomainError.CryptoError> {
        // Provider exception messages and causes are not safe diagnostic material: they may
        // include key/provider state. Keep diagnostics and domain errors deliberately generic.
        Logger.e { operation }
        return Outcome.Error(DomainError.CryptoError(fallback))
    }

    /** Converts one already-derived native public representation to JCA without regenerating it. */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun decodePublicKey(
        descriptor: CredentialKeyDescriptor,
        publicKeyBytes: ByteArray,
    ): PublicKey =
        when (descriptor.coseAlgorithm) {
            CredentialKeyDescriptor.COSE_ES256 -> {
                requireProfile(descriptor, CredentialKeyDescriptor.P256_PROFILE)
                decodeP256Point(publicKeyBytes)
            }
            CredentialKeyDescriptor.COSE_EDDSA -> {
                requireProfile(descriptor, CredentialKeyDescriptor.ED25519_PROFILE)
                val encoded = ED25519_X509_PREFIX + publicKeyBytes
                KeyFactory
                    .getInstance("Ed25519", BouncyCastleProvider())
                    .generatePublic(X509EncodedKeySpec(encoded))
            }
            CredentialKeyDescriptor.COSE_ML_DSA_65 -> {
                requireProfile(descriptor, CredentialKeyDescriptor.MLDSA65_PROFILE)
                KeyFactory
                    .getInstance(MLDSA_ALGORITHM, BouncyCastleProvider())
                    .generatePublic(X509EncodedKeySpec(publicKeyBytes))
            }
            else -> throw IllegalArgumentException("Unsupported credential algorithm")
        }

    private suspend fun deriveP256PublicKey(descriptor: CredentialKeyDescriptor): ByteArray {
        val prk = requireNotNull(masterSeedProvider.getMasterSeed()) { WALLET_ROOT_UNAVAILABLE }
        var parent: HdkKeyPair? = null
        var credentialId: ByteArray? = null
        var localSecret: ByteArray? = null
        var child: HdkChild? = null
        try {
            parent = requireNotNull(masterSeedProvider.getDeviceKeyPair()) { "P-256 parent is unavailable" }
            credentialId = descriptor.credentialId.toByteArray()
            localSecret = HkdfSha256.deriveCredentialSeed(prk, HkdfSha256.P256_CREDENTIAL_LABEL, credentialId)
            child = hdkManager.deriveChild(localSecret, parent.publicKey, LOCAL_CREDENTIAL_INDEX)
            return child.publicKey.copyOf()
        } finally {
            prk.fill(0)
            parent?.erasePrivateKey()
            credentialId?.fill(0)
            localSecret?.fill(0)
            child?.eraseFactor()
        }
    }

    private suspend fun deriveEd25519PublicKey(descriptor: CredentialKeyDescriptor): ByteArray {
        val prk = requireNotNull(masterSeedProvider.getMasterSeed()) { WALLET_ROOT_UNAVAILABLE }
        var credentialId: ByteArray? = null
        var seed: ByteArray? = null
        try {
            credentialId = descriptor.credentialId.toByteArray()
            seed = HkdfSha256.deriveCredentialSeed(prk, HkdfSha256.ED25519_CREDENTIAL_LABEL, credentialId)
            return Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
        } finally {
            prk.fill(0)
            credentialId?.fill(0)
            seed?.fill(0)
        }
    }

    private suspend fun deriveMlDsaPublicKey(descriptor: CredentialKeyDescriptor): ByteArray {
        val prk = requireNotNull(masterSeedProvider.getMasterSeed()) { WALLET_ROOT_UNAVAILABLE }
        var credentialId: ByteArray? = null
        var xi: ByteArray? = null
        var privateEncoding: ByteArray? = null
        try {
            credentialId = descriptor.credentialId.toByteArray()
            xi = HkdfSha256.deriveCredentialSeed(prk, HkdfSha256.MLDSA65_CREDENTIAL_LABEL, credentialId)
            val keyPair = requireNotNull(postQuantumCrypto.generateMlDsaKeyPair(xi)) { "ML-DSA-65 is unavailable" }
            privateEncoding = keyPair.private.encoded
            return postQuantumCrypto.publicKeyBytes(keyPair)
        } finally {
            prk.fill(0)
            credentialId?.fill(0)
            xi?.fill(0)
            privateEncoding?.fill(0)
        }
    }

    private suspend fun signP256(
        descriptor: CredentialKeyDescriptor,
        data: ByteArray,
        expectedStoredPublicKey: ByteArray?,
    ): ByteArray {
        val prk = requireNotNull(masterSeedProvider.getMasterSeed()) { WALLET_ROOT_UNAVAILABLE }
        var parent: HdkKeyPair? = null
        var credentialId: ByteArray? = null
        var localSecret: ByteArray? = null
        var child: HdkChild? = null
        var childPrivateKey: ByteArray? = null
        try {
            parent = requireNotNull(masterSeedProvider.getDeviceKeyPair()) { "P-256 parent is unavailable" }
            credentialId = descriptor.credentialId.toByteArray()
            localSecret = HkdfSha256.deriveCredentialSeed(prk, HkdfSha256.P256_CREDENTIAL_LABEL, credentialId)
            child = hdkManager.deriveChild(localSecret, parent.publicKey, LOCAL_CREDENTIAL_INDEX)
            requireRegisteredPublicKeyMatches(descriptor, child.publicKey, expectedStoredPublicKey)
            childPrivateKey = hdkManager.deriveChildPrivateKey(parent.privateKey, child.factor)
            return signP256Scalar(childPrivateKey, data)
        } finally {
            prk.fill(0)
            parent?.erasePrivateKey()
            credentialId?.fill(0)
            localSecret?.fill(0)
            child?.eraseFactor()
            childPrivateKey?.fill(0)
        }
    }

    private suspend fun signEd25519(
        descriptor: CredentialKeyDescriptor,
        data: ByteArray,
        expectedStoredPublicKey: ByteArray?,
    ): ByteArray {
        val prk = requireNotNull(masterSeedProvider.getMasterSeed()) { WALLET_ROOT_UNAVAILABLE }
        var credentialId: ByteArray? = null
        var seed: ByteArray? = null
        try {
            credentialId = descriptor.credentialId.toByteArray()
            seed = HkdfSha256.deriveCredentialSeed(prk, HkdfSha256.ED25519_CREDENTIAL_LABEL, credentialId)
            val privateParameters = Ed25519PrivateKeyParameters(seed, 0)
            requireRegisteredPublicKeyMatches(
                descriptor,
                privateParameters.generatePublicKey().encoded,
                expectedStoredPublicKey,
            )
            val signer = Ed25519Signer()
            signer.init(true, privateParameters)
            signer.update(data, 0, data.size)
            return signer.generateSignature()
        } finally {
            prk.fill(0)
            credentialId?.fill(0)
            seed?.fill(0)
        }
    }

    private suspend fun signMlDsa(
        descriptor: CredentialKeyDescriptor,
        data: ByteArray,
        expectedStoredPublicKey: ByteArray?,
    ): ByteArray {
        val prk = requireNotNull(masterSeedProvider.getMasterSeed()) { WALLET_ROOT_UNAVAILABLE }
        var credentialId: ByteArray? = null
        var xi: ByteArray? = null
        var privateEncoding: ByteArray? = null
        try {
            credentialId = descriptor.credentialId.toByteArray()
            xi = HkdfSha256.deriveCredentialSeed(prk, HkdfSha256.MLDSA65_CREDENTIAL_LABEL, credentialId)
            val keyPair = requireNotNull(postQuantumCrypto.generateMlDsaKeyPair(xi)) { "ML-DSA-65 is unavailable" }
            privateEncoding = keyPair.private.encoded
            requireRegisteredPublicKeyMatches(
                descriptor,
                postQuantumCrypto.publicKeyBytes(keyPair),
                expectedStoredPublicKey,
            )
            return requireNotNull(postQuantumCrypto.sign(keyPair.private, data)) { "ML-DSA-65 signing failed" }
        } finally {
            prk.fill(0)
            credentialId?.fill(0)
            xi?.fill(0)
            privateEncoding?.fill(0)
        }
    }

    private fun signP256Scalar(
        privateKeyBytes: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val scalar = BigInteger(1, privateKeyBytes)
        val curve = P256Group.G.curve
        val ecSpec =
            org.bouncycastle.jce.spec.ECNamedCurveSpec(
                "secp256r1",
                curve,
                P256Group.G,
                P256Group.ORDER,
                curve.cofactor,
            )
        val privateKey =
            KeyFactory
                .getInstance("EC")
                .generatePrivate(java.security.spec.ECPrivateKeySpec(scalar, ecSpec))
        return Signature
            .getInstance("SHA256withECDSA")
            .apply {
                initSign(privateKey)
                update(data)
            }.sign()
    }

    private fun decodeP256Point(bytes: ByteArray): PublicKey {
        require(bytes.size == P256Group.ELEMENT_UNCOMPRESSED_LENGTH) { "P-256 public key must be 65 bytes" }
        val point = P256Group.deserializeElement(bytes).normalize()
        val ecSpec =
            org.bouncycastle.jce.spec.ECNamedCurveSpec(
                "secp256r1",
                P256Group.G.curve,
                P256Group.G,
                P256Group.ORDER,
                P256Group.G.curve.cofactor,
            )
        return KeyFactory
            .getInstance("EC")
            .generatePublic(
                ECPublicKeySpec(ECPoint(point.affineXCoord.toBigInteger(), point.affineYCoord.toBigInteger()), ecSpec),
            )
    }

    private fun requireProfile(
        descriptor: CredentialKeyDescriptor,
        expectedProfile: String,
    ) {
        require(descriptor.derivationProfile == expectedProfile) { "Unsupported credential derivation profile" }
    }

    private fun requireRegisteredPublicKeyMatches(
        descriptor: CredentialKeyDescriptor,
        derivedPublicKey: ByteArray,
        expectedStoredPublicKey: ByteArray?,
    ) {
        if (expectedStoredPublicKey == null) return
        val derived = decodePublicKey(descriptor, derivedPublicKey)
        val storedAlgorithm =
            when (descriptor.coseAlgorithm) {
                CredentialKeyDescriptor.COSE_ES256 -> "EC"
                CredentialKeyDescriptor.COSE_EDDSA -> "Ed25519"
                CredentialKeyDescriptor.COSE_ML_DSA_65 -> MLDSA_ALGORITHM
                else -> throw IllegalArgumentException("Unsupported credential algorithm")
            }
        val storedKeyFactory =
            if (storedAlgorithm == "EC") {
                KeyFactory.getInstance("EC")
            } else {
                KeyFactory.getInstance(storedAlgorithm, BouncyCastleProvider())
            }
        val stored = storedKeyFactory.generatePublic(X509EncodedKeySpec(expectedStoredPublicKey))
        require(MessageDigest.isEqual(derived.encoded, stored.encoded)) {
            "Derived credential public key does not match the registered key"
        }
    }

    private companion object {
        const val PUBLIC_KEY_DERIVATION_FAILED = "Credential public-key derivation failed"
        const val SIGNING_FAILED = "Credential signing failed"
        const val KEY_DERIVATION_FALLBACK = "Credential key derivation failed"
        const val LOCAL_CREDENTIAL_INDEX: UInt = 0u
        const val MLDSA_ALGORITHM = "ML-DSA-65"
        const val WALLET_ROOT_UNAVAILABLE = "Wallet root is unavailable"
        val ED25519_X509_PREFIX =
            byteArrayOf(
                0x30,
                0x2A,
                0x30,
                0x05,
                0x06,
                0x03,
                0x2B,
                0x65,
                0x70,
                0x03,
                0x21,
                0x00,
            )
    }
}

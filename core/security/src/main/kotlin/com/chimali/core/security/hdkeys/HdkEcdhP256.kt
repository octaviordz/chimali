package com.chimali.core.security.hdkeys

import com.chimali.core.security.api.HdkKeyPair
import com.chimali.core.security.api.HdkManager
import com.chimali.core.security.api.HdkResult
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject

/**
 * HDK-ECDH-P256 instantiation as defined in draft-dijkhuis-cfrg-hdkeys-06.
 *
 * This is the RECOMMENDED instantiation using:
 * - G: NIST P-256 (secp256r1)
 * - H: SHA-256
 * - BL: Multiplicative blinding
 * - KEM: DHKEM(P-256, HKDF-SHA256)
 *
 * Implements [HdkManager] for integration with the Chimali security module.
 */
class HdkEcdhP256 @Inject constructor() : HdkManager {

    companion object {
        /**
         * Application-level instantiation label used in [createContext] per §2.3 of
         * `draft-dijkhuis-cfrg-hdkeys-06`.
         *
         * **T168 verification note**: §4.1 of the spec defines only `DST = "ECDH Key Blind"`
         * for the HDK-ECDH-P256 concrete instantiation; it does **not** prescribe an `ID` value.
         * The `ID` used in `CreateContext` (§2.3) is an application-level choice. The value
         * `"HDK-ECDH-P256-v1"` is a stable, self-describing label that uniquely identifies
         * this instantiation. It MUST NOT be changed without regenerating all KAT vectors
         * (T172) and all derived credentials, as it is baked into the `DeriveSalt` preimage.
         */
        val ID = "HDK-ECDH-P256-v1".toByteArray(Charsets.US_ASCII)

        /** Seed length in bytes (= SHA-256 output length, per §2.2 Ns). */
        const val NS = 32

        private val random = SecureRandom()
    }

    // --- Core HDK Functions (§2.3–2.5) ---

    /**
     * CreateContext: Builds the per-index context string per §2.3 of
     * `draft-dijkhuis-cfrg-hdkeys-06`.
     *
     * `ctx = ID || I2OSP(index, 4)`
     *
     * [ID] is a 16-byte application-level label. [index] is encoded as a 4-byte
     * big-endian unsigned integer (I2OSP per RFC 8017). The resulting [ctx] is
     * passed to [deriveSalt] and [MultiplicativeBlinding.deriveBlindingFactor].
     */
    internal fun createContext(index: Int): ByteArray {
        return ID + HashToScalar.i2osp(index, 4)
    }

    /**
     * DeriveSalt: Derive child salt from parent salt and context.
     *
     * Per §2.4 of draft-dijkhuis-cfrg-hdkeys-06:
     *   salt' = H(salt || ctx)
     *
     * Note: [ctx] is produced by [createContext] as `ID || I2OSP(index, 4)` per §2.3.
     * The [ID] domain separator is already embedded in [ctx] and MUST NOT be
     * prepended again to the hash input.
     */
    internal fun deriveSalt(salt: ByteArray, ctx: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(ctx)
        return digest.digest()
    }

    /**
     * HDK: The main hierarchical deterministic key derivation function.
     *
     * Derives a child (pk', salt', bf') from a parent.
     *
     * @param index Child index.
     * @param pk Parent public key.
     * @param salt Parent salt.
     * @param bf Parent blinding factor (null for root).
     * @return Triple of (blinded public key, derived salt, combined blinding factor).
     */
    internal fun hdk(
        index: Int,
        pk: ECPoint,
        salt: ByteArray,
        bf: BigInteger? = null
    ): Triple<ECPoint, ByteArray, BigInteger> {
        val ctx = createContext(index)
        val newSalt = deriveSalt(salt, ctx)
        val bk = MultiplicativeBlinding.deriveBlindKey(salt)
        val blindedPk = MultiplicativeBlinding.blindPublicKey(pk, bk, ctx)
        val bf2 = MultiplicativeBlinding.deriveBlindingFactor(bk, ctx)
        val combinedBf = if (bf == null) bf2 else MultiplicativeBlinding.combine(bf, bf2)
        return Triple(blindedPk, newSalt, combinedBf)
    }

    /**
     * Fold: Traverse a path of indices, applying HDK at each step.
     *
     * Handles both local indices (Int) for hierarchical derivation.
     *
     * @param path List of integer indices.
     * @param pk Starting public key.
     * @param salt Starting salt.
     * @param bf Starting blinding factor (null for root).
     * @return Final (pk, salt, bf) after traversing the full path.
     */
    internal fun fold(
        path: List<Int>,
        pk: ECPoint,
        salt: ByteArray,
        bf: BigInteger? = null
    ): Triple<ECPoint, ByteArray, BigInteger> {
        var currentPk = pk
        var currentSalt = salt
        var currentBf = bf

        for (index in path) {
            val (newPk, newSalt, newBf) = hdk(index, currentPk, currentSalt, currentBf)
            currentPk = newPk
            currentSalt = newSalt
            currentBf = newBf
        }

        return Triple(currentPk, currentSalt, currentBf!!)
    }

    // --- HdkManager Interface Implementation ---

    override fun generateSeed(): ByteArray {
        val seed = ByteArray(NS)
        random.nextBytes(seed)
        return seed
    }

    override fun generateDeviceKeyPair(): HdkKeyPair {
        val (sk, pk) = P256Group.generateKeyPair()
        return HdkKeyPair(sk, pk)
    }

    override fun deriveHdk(
        devicePublicKey: ByteArray,
        seed: ByteArray,
        path: List<Int>
    ): HdkResult {
        val pk = P256Group.deserializeElement(devicePublicKey)
        val (derivedPk, derivedSalt, derivedBf) = fold(path, pk, seed)
        return HdkResult(derivedPk, derivedSalt, derivedBf)
    }

    override fun blindPrivateKey(
        devicePrivateKey: ByteArray,
        blindingFactor: ByteArray
    ): ByteArray {
        val sk = P256Group.deserializeScalar(devicePrivateKey)
        val bf = P256Group.deserializeScalar(blindingFactor)
        val blindedSk = MultiplicativeBlinding.blindPrivateKey(sk, bf)
        return P256Group.serializeScalar(blindedSk)
    }

    override fun createBlindedSharedSecret(
        devicePrivateKey: ByteArray,
        blindingFactor: ByteArray,
        readerPublicKey: ByteArray
    ): ByteArray {
        val sk = P256Group.deserializeScalar(devicePrivateKey)
        val bf = P256Group.deserializeScalar(blindingFactor)
        val pkReader = P256Group.deserializeElement(readerPublicKey)
        return MultiplicativeBlinding.blindDh(sk, bf, pkReader)
    }

    override fun requestRemoteDerivation(salt: ByteArray): ByteArray {
        val (_, pk) = DhKem.deriveKeyPair(salt)
        return P256Group.serializeElement(pk)
    }

    override fun acceptRemoteKey(
        parentSalt: ByteArray,
        keyHandle: ByteArray,
        index: Int,
        parentPublicKey: ByteArray,
        expectedPublicKey: ByteArray
    ): HdkResult {
        // Recover the KEM private key from the parent salt
        val (skR, _) = DhKem.deriveKeyPair(parentSalt)

        // Decapsulate the key handle to recover the salt
        val newSalt = DhKem.decap(keyHandle, skR)

        // Derive the new HDK at the given index
        val parentPk = P256Group.deserializeElement(parentPublicKey)
        val (derivedPk, derivedSalt, derivedBf) = hdk(index, parentPk, newSalt)

        // Verify the public key matches expectations
        val expectedPk = P256Group.deserializeElement(expectedPublicKey)
        require(derivedPk.normalize() == expectedPk.normalize()) {
            "Derived public key does not match expected public key"
        }

        return HdkResult(derivedPk, derivedSalt, derivedBf)
    }
}

package com.chimali.core.security.api

/**
 * Interface for Hierarchical Deterministic Key (HDK) management.
 *
 * Implements the HDK-ECDH-P256 instantiation from
 * IETF draft-dijkhuis-cfrg-hdkeys-06 for privacy-preserving
 * elliptic curve key derivation with key blinding.
 */
interface HdkManager {
    /**
     * Generate a new random seed of Ns bytes for HDK derivation.
     *
     * @see <a href="https://www.ietf.org/archive/id/draft-dijkhuis-cfrg-hdkeys-06.html#section-2.6">
     * draft-dijkhuis-cfrg-hdkeys-06 §2.6 (GenerateSeed)</a>
     */
    fun generateSeed(): ByteArray

    /**
     * Generate a new device key pair (sk, pk) on P-256.
     */
    fun generateDeviceKeyPair(): HdkKeyPair

    /**
     * Derive an HDK at a given path from the root.
     *
     * Performs local derivation by traversing the path of integer indices,
     * applying key blinding at each level.
     *
     * **Index domain (T179)**: The spec defines indices as `uint32` (0–2^32−1).
     * This implementation natively uses Kotlin's unsigned 32-bit `UInt`.
     *
     * @param devicePublicKey The device's public key (uncompressed encoding).
     * @param seed The root seed (32 bytes).
     * @param path List of unsigned 32-bit indices for derivation (e.g., [0u], [0u, 1u, 2u]).
     * @return The derived HDK result containing blinded public key, salt, and blinding factor.
     * @see <a href="https://www.ietf.org/archive/id/draft-dijkhuis-cfrg-hdkeys-06.html#section-2.5">
     * draft-dijkhuis-cfrg-hdkeys-06 §2.5 (HDK)</a>
     */
    fun deriveHdk(
        devicePublicKey: ByteArray,
        seed: ByteArray,
        path: List<UInt>,
    ): HdkResult

    /**
     * Compute a blinded private key for signing or key agreement.
     *
     * sk' = sk * bf mod Order()
     *
     * @param devicePrivateKey The device private key (32 bytes, big-endian).
     * @param blindingFactor The combined blinding factor (32 bytes, big-endian).
     * @return The blinded private key (32 bytes).
     * @see <a href="https://www.ietf.org/archive/id/draft-dijkhuis-cfrg-hdkeys-06.html#section-3.2.2">
     * draft-dijkhuis-cfrg-hdkeys-06 §3.2.2 (Multiplicative Blinding)</a>
     */
    fun blindPrivateKey(
        devicePrivateKey: ByteArray,
        blindingFactor: ByteArray,
    ): ByteArray

    /**
     * Create a blinded ECDH shared secret for proof of possession.
     *
     * The device computes: CreateSharedSecret(sk, ScalarMult(pkReader, bf))
     * This equals what the reader computes: CreateSharedSecret(skReader, pk_blinded)
     *
     * @param devicePrivateKey The device private key (32 bytes).
     * @param blindingFactor The combined blinding factor (32 bytes).
     * @param readerPublicKey The reader's public key (uncompressed, 65 bytes).
     * @return Shared secret (32 bytes).
     * @see <a href="https://www.ietf.org/archive/id/draft-dijkhuis-cfrg-hdkeys-06.html#section-3.3.1">
     * draft-dijkhuis-cfrg-hdkeys-06 §3.3.1 (Proof of Possession)</a>
     */
    fun createBlindedSharedSecret(
        devicePrivateKey: ByteArray,
        blindingFactor: ByteArray,
        readerPublicKey: ByteArray,
    ): ByteArray

    /**
     * Request remote key derivation by generating a KEM key pair from a salt.
     *
     * Returns the KEM public key that an issuer can use to Encap a key handle.
     *
     * @param salt The current salt at the derivation point.
     * @return KEM public key (uncompressed, 65 bytes).
     */
    fun requestRemoteDerivation(salt: ByteArray): ByteArray

    /**
     * Accept a remotely-derived key handle and produce an HDK at the given index.
     *
     * Decapsulates the key handle to recover the salt, then applies HDK
     * blinding at the specified index. Verifies the resulting public key
     * matches the expected value.
     *
     * @param parentSalt The parent's salt (used to derive the KEM key pair).
     * @param keyHandle The encapsulated key handle from the issuer.
     * @param index The child index for the new HDK.
     * @param parentPublicKey The parent's blinded public key (uncompressed, 65 bytes).
     * @param expectedPublicKey The expected resulting public key (uncompressed, 65 bytes).
     * @return The derived HDK result.
     * @throws IllegalArgumentException if the derived public key doesn't match expected.
     */
    fun acceptRemoteKey(
        parentSalt: ByteArray,
        keyHandle: ByteArray,
        index: UInt,
        parentPublicKey: ByteArray,
        expectedPublicKey: ByteArray,
    ): HdkResult
}

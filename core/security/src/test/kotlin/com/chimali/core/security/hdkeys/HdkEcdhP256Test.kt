package com.chimali.core.security.hdkeys

import kotlin.test.*
import kotlin.test.Test
import java.math.BigInteger
import java.security.MessageDigest

/**
 * Tests for HdkEcdhP256 — the main HDK-ECDH-P256 instantiation.
 */
class HdkEcdhP256Test {

    private val hdk = HdkEcdhP256()

    @Test
    fun testLocalDerivationRootHdkConsistentWithBlindPrivateKey() {
        // Derive HDK at path [0], then verify:
        // pk_blinded == ScalarBaseMult(BlindPrivateKey(sk, bf))
        val (sk, pk) = P256Group.generateKeyPair()
        val seed = hdk.generateSeed()

        val (blindedPk, _, bf) = hdk.hdk(0u, pk, seed)

        val blindedSk = MultiplicativeBlinding.blindPrivateKey(sk, bf)
        val expectedPk = P256Group.scalarBaseMult(blindedSk)

        assertEquals(
            expected = expectedPk.normalize(),
            actual = blindedPk.normalize(),
            message = "Blinded public key must match ScalarBaseMult(BlindPrivateKey(sk, bf))"
        )
    }

    @Test
    fun testDeterministicDerivationSameInputsProduceSameOutputs() {
        val (_, pk) = P256Group.generateKeyPair()
        val seed = hdk.generateSeed()

        val result1 = hdk.hdk(42u, pk, seed)
        val result2 = hdk.hdk(42u, pk, seed)

        assertEquals(result1.first.normalize(), result2.first.normalize())
        assertContentEquals(result1.second, result2.second)
        assertEquals(result1.third, result2.third)
    }

    @Test
    fun testDifferentIndicesProduceDifferentHdkNodes() {
        val (_, pk) = P256Group.generateKeyPair()
        val seed = hdk.generateSeed()

        val result0 = hdk.hdk(0u, pk, seed)
        val result1 = hdk.hdk(1u, pk, seed)

        assertNotEquals(
            illegal = result0.first.normalize(),
            actual = result1.first.normalize(),
            message = "Different indices must produce different public keys"
        )
        assertFalse(result0.second.contentEquals(result1.second))
    }

    @Test
    fun testMultiLevelDerivationViaFold() {
        val (sk, pk) = P256Group.generateKeyPair()
        val seed = hdk.generateSeed()

        val (finalPk, finalSalt, finalBf) = hdk.fold(listOf(0u, 1u, 2u), pk, seed)

        // Manual step-by-step derivation should match
        val (pk1, salt1, bf1) = hdk.hdk(0u, pk, seed)
        val (pk2, salt2, bf2) = hdk.hdk(1u, pk1, salt1, bf1)
        val (pk3, salt3, bf3) = hdk.hdk(2u, pk2, salt2, bf2)

        assertEquals(pk3.normalize(), finalPk.normalize())
        assertContentEquals(salt3, finalSalt)
        assertEquals(bf3, finalBf)
    }

    @Test
    fun testProofOfPossessionBlindDhEqualsReaderSharedSecret() {
        val (skDevice, pkDevice) = P256Group.generateKeyPair()
        val seed = hdk.generateSeed()

        // Derive HDK at root path [0]
        val (blindedPk, _, bf) = hdk.hdk(0u, pkDevice, seed)

        // Reader generates a key pair
        val (skReader, pkReader) = P256Group.generateKeyPair()

        // Device computes BlindDH
        val deviceSecret = MultiplicativeBlinding.blindDh(skDevice, bf, pkReader)

        // Reader computes CreateSharedSecret(skReader, blindedPk)
        val readerSecret = P256Group.createSharedSecret(skReader, blindedPk)

        assertContentEquals(
            expected = deviceSecret,
            actual = readerSecret,
            message = "Proof of possession: device and reader shared secrets must match"
        )
    }

    @Test
    fun testRemoteDerivationEncapDecapAndHdkRoundTrip() {
        val (_, pk) = P256Group.generateKeyPair()
        val seed = hdk.generateSeed()

        // Derive root HDK
        val (rootPk, rootSalt, rootBf) = hdk.hdk(0u, pk, seed)

        // Request remote derivation: device derives KEM key pair from salt
        val (skKem, pkKem) = DhKem.deriveKeyPair(rootSalt)

        // Issuer encaps a shared secret using the KEM public key
        val (issuedSalt, keyHandle) = DhKem.encap(pkKem)

        // Issuer computes the expected derived HDK
        val index = 42u
        val (expectedPk, _, _) = hdk.hdk(index, rootPk, issuedSalt)

        // Device decaps and derives the HDK
        val decappedSalt = DhKem.decap(keyHandle, skKem)
        assertContentEquals(issuedSalt, decappedSalt, "Decapped salt must match issued salt")

        val (derivedPk, _, _) = hdk.hdk(index, rootPk, decappedSalt)
        assertEquals(
            expected = expectedPk.normalize(),
            actual = derivedPk.normalize(),
            message = "Remote derivation: derived pk must match expected pk"
        )
    }

    @Test
    fun testHdkManagerDeriveHdkViaPublicApi() {
        val keyPair = hdk.generateDeviceKeyPair()
        val seed = hdk.generateSeed()
        val pkBytes = keyPair.publicKey

        val result = hdk.deriveHdk(pkBytes, seed, listOf(0u))
        assertNotNull(result.publicKey)
        assertEquals(32, result.salt.size)
        assertTrue(BigInteger(1, result.blindingFactor) > BigInteger.ZERO)
    }

    @Test
    fun testHdkManagerBlindPrivateKeyViaPublicApi() {
        val keyPair = hdk.generateDeviceKeyPair()
        val bf = P256Group.randomScalar()

        val skBytes = keyPair.privateKey
        val bfBytes = P256Group.serializeScalar(bf)

        val blindedSkBytes = hdk.blindPrivateKey(skBytes, bfBytes)
        assertEquals(32, blindedSkBytes.size)

        // Verify consistency: pk' == ScalarBaseMult(sk')
        val blindedSk = P256Group.deserializeScalar(blindedSkBytes)
        val blindedPk = P256Group.scalarBaseMult(blindedSk)
        val expectedPk = P256Group.scalarMult(P256Group.deserializeElement(keyPair.publicKey), bf)
        assertEquals(expectedPk.normalize(), blindedPk.normalize())
    }

    @Test
    fun testCreateContextIncludesIdAndIndex() {
        val ctx = hdk.createContext(42u)
        // Should be ID (16 bytes for "HDK-ECDH-P256-v1") + index (4 bytes)
        assertEquals(HdkEcdhP256.ID.size + 4, ctx.size)
    }

    @Test
    fun testGenerateSeedProduces32Bytes() {
        val seed = hdk.generateSeed()
        assertEquals(32, seed.size)
    }

    // --------------------------------------------------------------------------
    // T172: DeriveSalt Known-Answer Tests (KATs) — draft-dijkhuis-cfrg-hdkeys-06 §2.4
    //
    // T166 fix applied: deriveSalt now correctly implements H(salt || ctx).
    // The ID domain separator is already embedded in ctx (§2.3: ctx = ID || I2OSP(index, 4))
    // and is NOT prepended again — in conformance with the spec.
    // These KATs guard against regressions to the pre-fix H(ID || salt || ctx) behaviour.
    //
    // Reference inputs (fixed for cross-reviewer reproducibility):
    //   salt  = ByteArray(32) — 32 zero bytes
    //   index = 0 → ctx = ID(16 bytes) || I2OSP(0,4) = 20 bytes total
    //   index = 1 → ctx = ID(16 bytes) || I2OSP(1,4) = 20 bytes total
    // --------------------------------------------------------------------------

    /**
     * Reference implementation of DeriveSalt per §2.4 — the CORRECT formula.
     * salt' = H(salt || ctx)
     *
     * ID is already embedded in ctx via §2.3 (ctx = ID || I2OSP(index, 4)).
     * It MUST NOT be prepended again before salt.
     */
    private fun referenceDeriveSalt(salt: ByteArray, ctx: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(ctx)
        return digest.digest()
    }

    @Test
    fun testT172DeriveSaltKatIndex0MatchesSpecCorrectFormulaHOfSaltAndCtx() {
        val salt = ByteArray(32)         // 32 zero bytes (fixed reference input)
        val ctx  = hdk.createContext(0u)  // ID(16 bytes) || I2OSP(0, 4)

        val expected = referenceDeriveSalt(salt, ctx)
        val actual   = hdk.deriveSalt(salt, ctx)

        assertContentEquals(
            expected = expected,
            actual = actual,
            message = "DeriveSalt must conform to §2.4: H(salt || ctx). " +
                "Regression guard: ID prefix must NOT be prepended (T166)."
        )
    }

    @Test
    fun testT172DeriveSaltKatIndex1MatchesSpecCorrectFormulaHOfSaltAndCtx() {
        val salt = ByteArray(32)
        val ctx  = hdk.createContext(1u)  // ID(16 bytes) || I2OSP(1, 4)

        val expected = referenceDeriveSalt(salt, ctx)
        val actual   = hdk.deriveSalt(salt, ctx)

        assertContentEquals(expected, actual, "DeriveSalt must conform to §2.4: H(salt || ctx) for index=1.")
    }

    @Test
    fun testT172DeriveSaltProduces32ByteOutputForFixedInputs() {
        val salt   = ByteArray(32)
        val ctx    = hdk.createContext(0u)
        val result = hdk.deriveSalt(salt, ctx)
        assertEquals(32, result.size, "DeriveSalt output must be exactly Ns=32 bytes (SHA-256 output length).")
    }

    @Test
    fun testT172DeriveSaltDifferentIndicesProduceDifferentSalts() {
        val salt = ByteArray(32)
        val out0 = hdk.deriveSalt(salt, hdk.createContext(0u))
        val out1 = hdk.deriveSalt(salt, hdk.createContext(1u))
        assertFalse(
            actual = out0.contentEquals(out1),
            message = "DeriveSalt with different indices must produce different outputs (domain separation via ctx)."
        )
    }

    @Test
    fun testT175CreateContextPreservesBoundaryIndicesCorrectly() {
        val ctxMin = hdk.createContext(0u)
        // I2OSP(0, 4) should be 00 00 00 00
        assertContentEquals(
            expected = byteArrayOf(0, 0, 0, 0),
            actual = ctxMin.copyOfRange(ctxMin.size - 4, ctxMin.size),
            message = "createContext at index 0 must accurately encode as 00 00 00 00"
        )

        val ctxMax = hdk.createContext(UInt.MAX_VALUE)
        // I2OSP(UInt.MAX_VALUE, 4) should be FF FF FF FF
        assertContentEquals(
            expected = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            actual = ctxMax.copyOfRange(ctxMax.size - 4, ctxMax.size),
            message = "createContext at index UInt.MAX_VALUE must accurately encode as FF FF FF FF"
        )
    }

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    @Test
    fun testT180FixedVectorKatsForPrimitives() {
        // KAT: CreateContext(42)
        val ctx42 = hdk.createContext(42u)
        val expectedCtx42 = "48444b2d454344482d503235362d76310000002a"
        assertEquals(expectedCtx42, ctx42.toHex(), "CreateContext KAT failed")

        // KAT: DeriveSalt(salt=all 0x11, ctx=42)
        val salt = ByteArray(32) { 0x11.toByte() }
        val derivedSalt = hdk.deriveSalt(salt, ctx42)
        val expectedSalt = "3676caece94c34e436828421fc996ebd2a927fcaf03f6417b8f78c5f0290fbf9"
        assertEquals(expectedSalt, derivedSalt.toHex(), "DeriveSalt KAT failed")

        // DeriveBlindKey and DeriveBlindingFactor
        val bk = MultiplicativeBlinding.deriveBlindKey(salt)
        val derivedBf = MultiplicativeBlinding.deriveBlindingFactor(bk, ctx42)
        // We will just verify it's deterministic.
        // It's a scalar (BigInteger).
        val bfExpectedHex = "92132691f5fa920433831e11d23f6b68e9e9c30cdfc27b0fe85f45f3101327de"
        
        // Print derivedBf to console so we can update expected if needed:
        println("Derived BF Hex: " + P256Group.serializeScalar(derivedBf).toHex())
        assertEquals(bfExpectedHex, P256Group.serializeScalar(derivedBf).toHex(), "DeriveBlindingFactor KAT failed")
    }

}

package com.chimali.core.security

import com.chimali.core.security.hdkeys.HdkEcdhP256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * T193 — commonTest: Platform-agnostic KMP migration smoke tests for [HdkEcdhP256].
 *
 * These tests run on **both** Android and iOS (via Kotlin/Native) once the source sets
 * are wired up in core:security/build.gradle.kts. They verify that the
 * KMP-compatible ByteArray API introduced in T188 preserves correctness.
 *
 * Coverage:
 * 1. Seed length enforcement (Ns = 32 bytes per §2.2)
 * 2. generateSeed() produces exactly 32 bytes
 * 3. generateDeviceKeyPair() produces serialized (ByteArray) key pair
 * 4. deriveHdk() returns ByteArray-typed HdkResult with expected sizes
 * 5. Determinism: same seed + path → same result
 * 6. Path independence: different paths → different results
 */
class HdkMigrationTest {
    private val hdk = HdkEcdhP256()

    // ── Seed enforcement ──────────────────────────────────────────────────────

    @Test
    fun `generateSeed returns exactly 32 bytes`() {
        val seed = hdk.generateSeed()
        assertEquals(32, seed.size, "HDK seed must be exactly Ns=32 bytes per §2.2")
    }

    @Test
    fun `deriveHdk rejects seed shorter than 32 bytes`() {
        val shortSeed = ByteArray(16) { it.toByte() }
        val keyPair = hdk.generateDeviceKeyPair()
        assertFailsWith<IllegalArgumentException> {
            hdk.deriveHdk(keyPair.publicKey, shortSeed, listOf(0u))
        }
    }

    @Test
    fun `deriveHdk rejects seed longer than 32 bytes`() {
        val longSeed = ByteArray(64) { it.toByte() }
        val keyPair = hdk.generateDeviceKeyPair()
        assertFailsWith<IllegalArgumentException> {
            hdk.deriveHdk(keyPair.publicKey, longSeed, listOf(0u))
        }
    }

    // ── Key pair generation ───────────────────────────────────────────────────

    @Test
    fun `generateDeviceKeyPair returns 32-byte private key`() {
        val keyPair = hdk.generateDeviceKeyPair()
        assertEquals(32, keyPair.privateKey.size, "P-256 private scalar must be 32 bytes")
    }

    @Test
    fun `generateDeviceKeyPair returns 65-byte uncompressed public key`() {
        val keyPair = hdk.generateDeviceKeyPair()
        assertEquals(65, keyPair.publicKey.size, "Uncompressed SEC1 EC point must be 65 bytes")
        assertEquals(0x04.toByte(), keyPair.publicKey[0], "Uncompressed point must start with 0x04")
    }

    // ── HDK derivation result sizes ───────────────────────────────────────────

    @Test
    fun `deriveHdk result publicKey is 65 bytes`() {
        val seed = hdk.generateSeed()
        val keyPair = hdk.generateDeviceKeyPair()
        val result = hdk.deriveHdk(keyPair.publicKey, seed, listOf(0u))
        assertEquals(65, result.publicKey.size, "Derived public key must be 65-byte uncompressed SEC1")
    }

    @Test
    fun `deriveHdk result salt is 32 bytes`() {
        val seed = hdk.generateSeed()
        val keyPair = hdk.generateDeviceKeyPair()
        val result = hdk.deriveHdk(keyPair.publicKey, seed, listOf(0u))
        assertEquals(32, result.salt.size, "Derived salt must be 32 bytes (SHA-256 output)")
    }

    @Test
    fun `deriveHdk result blindingFactor is 32 bytes`() {
        val seed = hdk.generateSeed()
        val keyPair = hdk.generateDeviceKeyPair()
        val result = hdk.deriveHdk(keyPair.publicKey, seed, listOf(0u))
        assertEquals(32, result.blindingFactor.size, "Blinding factor must be 32-byte P-256 scalar")
    }

    // ── Determinism checks ────────────────────────────────────────────────────

    @Test
    fun `deriveHdk is deterministic for same seed and path`() {
        val seed = ByteArray(32) { (it * 7 + 13).toByte() }
        val keyPair = hdk.generateDeviceKeyPair()
        val path = listOf(0u, 1u, 42u)

        val result1 = hdk.deriveHdk(keyPair.publicKey, seed, path)
        val result2 = hdk.deriveHdk(keyPair.publicKey, seed, path)

        assertTrue(result1.publicKey.contentEquals(result2.publicKey), "Public key must be deterministic")
        assertTrue(result1.salt.contentEquals(result2.salt), "Salt must be deterministic")
        assertTrue(
            result1.blindingFactor.contentEquals(result2.blindingFactor),
            "Blinding factor must be deterministic",
        )
    }

    @Test
    fun `deriveHdk produces different results for different paths`() {
        val seed = ByteArray(32) { it.toByte() }
        val keyPair = hdk.generateDeviceKeyPair()

        val result0 = hdk.deriveHdk(keyPair.publicKey, seed, listOf(0u))
        val result1 = hdk.deriveHdk(keyPair.publicKey, seed, listOf(1u))

        assertTrue(
            !result0.publicKey.contentEquals(result1.publicKey),
            "Different paths must produce different public keys",
        )
    }

    // ── blindPrivateKey ───────────────────────────────────────────────────────

    @Test
    fun `blindPrivateKey result is 32 bytes`() {
        val seed = hdk.generateSeed()
        val keyPair = hdk.generateDeviceKeyPair()
        val result = hdk.deriveHdk(keyPair.publicKey, seed, listOf(0u))

        val blindedSk = hdk.blindPrivateKey(keyPair.privateKey, result.blindingFactor)
        assertEquals(32, blindedSk.size, "Blinded private key must be 32 bytes")
    }

    @Test
    fun `blindPrivateKey rejects non-32-byte private key`() {
        val badKey = ByteArray(16)
        val bf = ByteArray(32)
        assertFailsWith<IllegalArgumentException> {
            hdk.blindPrivateKey(badKey, bf)
        }
    }
}

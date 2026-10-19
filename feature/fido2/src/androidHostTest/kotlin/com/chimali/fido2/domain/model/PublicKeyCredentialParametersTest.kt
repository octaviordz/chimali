package com.chimali.fido2.domain.model

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * T017 — Unit tests for [PublicKeyCredentialParameters.createEdDsa] factory.
 *
 * Verifies:
 * - algorithm == "EdDSA"  (WebAuthn L3 § 5.4 COSE key type for Ed25519)
 * - curve == "Ed25519"
 * - type == PUBLIC_KEY
 */
class PublicKeyCredentialParametersTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `createEdDsa returns algorithm EdDSA`() {
        val params = PublicKeyCredentialParameters.createEdDsa()
        assertEquals("EdDSA", params.algorithm, "createEdDsa() must set algorithm to 'EdDSA'")
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `createEdDsa returns curve Ed25519`() {
        val params = PublicKeyCredentialParameters.createEdDsa()
        assertEquals("Ed25519", params.curve, "createEdDsa() must set curve to 'Ed25519'")
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `createEdDsa returns type PUBLIC_KEY`() {
        val params = PublicKeyCredentialParameters.createEdDsa()
        assertEquals(
            PublicKeyCredentialType.PUBLIC_KEY,
            params.type,
            "createEdDsa() must set type to PUBLIC_KEY",
        )
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `createEdDsa passes internal validation`() {
        // Should not throw
        val params = PublicKeyCredentialParameters.createEdDsa()
        assertNotNull(params, "createEdDsa() must not throw during construction")
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `createEdDsa is identified as elliptic curve`() {
        val params = PublicKeyCredentialParameters.createEdDsa()
        assertEquals(
            true,
            params.isEllipticCurve(),
            "EdDSA must be classified as an elliptic-curve algorithm",
        )
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `createEdDsa is not identified as RSA`() {
        val params = PublicKeyCredentialParameters.createEdDsa()
        assertEquals(false, params.isRsa(), "EdDSA must NOT be classified as RSA")
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `createEdDsa getAlgorithmFamily returns Edwards Curve`() {
        val params = PublicKeyCredentialParameters.createEdDsa()
        assertEquals("Edwards Curve", params.getAlgorithmFamily())
    }

    // Sanity check: ES256 factory still works alongside createEdDsa

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `createES256P256 still works after EdDSA addition`() {
        val params = PublicKeyCredentialParameters.createES256P256()
        assertEquals("ES256", params.algorithm)
        assertEquals("P-256", params.curve)
    }

    /**
     * T017a — Verify ES256 explicit parameters per WebAuthn L3 / FR-FIDO2-006.
     * @see NFR-SEC-041 and TR-AUTH-031
     */
    @Test
    fun `createEs256 returns explicit P-256 and EC2 parameters`() {
        val params = PublicKeyCredentialParameters.createEs256()
        assertEquals("ES256", params.algorithm, "Algorithm must be ES256")
        assertEquals("P-256", params.curve, "ES256 must use P-256 curve (FR-FIDO2-006)")
        assertEquals("EC2", params.keyType, "ES256 must use EC2 key type (FR-FIDO2-006)")
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `algorithm classification and optional salt helpers cover supported families`() {
        val edDsa = PublicKeyCredentialParameters.createEdDsa()
        val rsa = PublicKeyCredentialParameters.createRS256()
        val mlDsa = PublicKeyCredentialParameters.createMlDsa65()

        assertTrue(edDsa.isEllipticCurve())
        assertEquals(false, rsa.isEllipticCurve())
        assertTrue(rsa.isRsa())
        assertEquals(false, edDsa.isRsa())
        assertEquals("Elliptic Curve", PublicKeyCredentialParameters.createEs256().getAlgorithmFamily())
        assertEquals("RSA", rsa.getAlgorithmFamily())
        assertEquals("Edwards Curve", edDsa.getAlgorithmFamily())
        assertEquals("Unknown", mlDsa.getAlgorithmFamily())
        assertEquals("N/A", mlDsa.getCurveName())
        assertEquals(16, mlDsa.getSafeSalt().size)
        assertContentEquals(
            byteArrayOf(1, 2),
            PublicKeyCredentialParameters.create(salt = byteArrayOf(1, 2)).getSafeSalt(),
        )
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `invalid credential parameters are rejected at construction`() {
        assertFailsWith<IllegalArgumentException> { PublicKeyCredentialParameters.create(algorithm = " ") }
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialParameters.create(type = PublicKeyCredentialType.UNKNOWN)
        }
        assertFailsWith<IllegalArgumentException> { PublicKeyCredentialParameters.create(algorithm = "ES384") }
        assertFailsWith<IllegalArgumentException> { PublicKeyCredentialParameters.create(curve = "P-192") }
        assertFailsWith<IllegalArgumentException> { PublicKeyCredentialParameters.create(keyType = "EC") }
        assertFailsWith<IllegalArgumentException> { PublicKeyCredentialParameters.create(salt = byteArrayOf()) }
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialParameters.create(salt = ByteArray(PublicKeyCredentialParameters.MAX_SALT_SIZE + 1))
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `equality compares scalar fields and salt contents`() {
        val original = PublicKeyCredentialParameters.create(salt = byteArrayOf(1, 2))
        val equal = PublicKeyCredentialParameters.create(salt = byteArrayOf(1, 2))

        assertEquals(original, original)
        assertEquals(original, equal)
        assertEquals(original.hashCode(), equal.hashCode())
        assertNotEquals(
            original,
            PublicKeyCredentialParameters.create(algorithm = "RS256", curve = null, keyType = "RSA"),
        )
        assertNotEquals(original, PublicKeyCredentialParameters.create(curve = "P-384"))
        assertNotEquals(original, PublicKeyCredentialParameters.create(keyType = "OKP"))
        assertNotEquals(original, PublicKeyCredentialParameters.create(salt = byteArrayOf(2, 1)))
        assertNotEquals(original, PublicKeyCredentialParameters.create(salt = null))
    }
}

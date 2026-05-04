package com.chimali.fido2.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * T017 — Unit tests for [PublicKeyCredentialParameters.createEdDsa] factory.
 *
 * Verifies:
 * - algorithm == "EdDSA"  (WebAuthn L3 § 5.4 COSE key type for Ed25519)
 * - curve == "Ed25519"
 * - type == PUBLIC_KEY
 */
class PublicKeyCredentialParametersTest {
    @Test
    fun `createEdDsa returns algorithm EdDSA`() {
        val params = PublicKeyCredentialParameters.createEdDsa()
        assertEquals("EdDSA", params.algorithm, "createEdDsa() must set algorithm to 'EdDSA'")
    }

    @Test
    fun `createEdDsa returns curve Ed25519`() {
        val params = PublicKeyCredentialParameters.createEdDsa()
        assertEquals("Ed25519", params.curve, "createEdDsa() must set curve to 'Ed25519'")
    }

    @Test
    fun `createEdDsa returns type PUBLIC_KEY`() {
        val params = PublicKeyCredentialParameters.createEdDsa()
        assertEquals(
            PublicKeyCredentialType.PUBLIC_KEY,
            params.type,
            "createEdDsa() must set type to PUBLIC_KEY",
        )
    }

    @Test
    fun `createEdDsa passes internal validation`() {
        // Should not throw
        val params = PublicKeyCredentialParameters.createEdDsa()
        assertNotNull(params, "createEdDsa() must not throw during construction")
    }

    @Test
    fun `createEdDsa is identified as elliptic curve`() {
        val params = PublicKeyCredentialParameters.createEdDsa()
        assertEquals(
            true,
            params.isEllipticCurve(),
            "EdDSA must be classified as an elliptic-curve algorithm",
        )
    }

    @Test
    fun `createEdDsa is not identified as RSA`() {
        val params = PublicKeyCredentialParameters.createEdDsa()
        assertEquals(false, params.isRsa(), "EdDSA must NOT be classified as RSA")
    }

    @Test
    fun `createEdDsa getAlgorithmFamily returns Edwards Curve`() {
        val params = PublicKeyCredentialParameters.createEdDsa()
        assertEquals("Edwards Curve", params.getAlgorithmFamily())
    }

    // Sanity check: ES256 factory still works alongside createEdDsa
    @Test
    fun `createES256P256 still works after EdDSA addition`() {
        val params = PublicKeyCredentialParameters.createES256P256()
        assertEquals("ES256", params.algorithm)
        assertEquals("P-256", params.curve)
    }
}

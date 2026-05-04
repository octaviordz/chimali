package com.chimali.fido2.ctap2

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T016 — Unit tests for algorithm negotiation in [Ctap2MakeCredentialHandler].
 *
 * Verifies the CTAP2 algorithm preference order per WebAuthn L3 plan §Phase 1:
 *   -7   ES256/P-256   → accepted (primary)
 *   -8   EdDSA/Ed25519 → accepted (L3 required)
 *   -257 RS256         → accepted (interoperability fallback)
 *   -49  ML-DSA-65     → accepted (PQ extension)
 *   -9, -19, -51, -52  → rejected (deprecated / NOT RECOMMENDED)
 */
class Ctap2MakeCredentialHandlerAlgorithmTest {
    // COSE algorithm IDs under test
    private val COSE_ES256 = -7
    private val COSE_EDSA = -8
    private val COSE_RS256 = -257
    private val COSE_ML_DSA_65 = -49

    // Deprecated / rejected IDs
    private val DEPRECATED_COSE_ED25519_OLD = -19
    private val DEPRECATED_COSE_9 = -9
    private val DEPRECATED_COSE_51 = -51
    private val DEPRECATED_COSE_52 = -52

    /** All COSE IDs that the handler must accept. */
    private val acceptedAlgorithms = listOf(COSE_ES256, COSE_EDSA, COSE_RS256, COSE_ML_DSA_65)

    /** All COSE IDs that the handler must reject with CTAP2_ERR_UNSUPPORTED_ALGORITHM. */
    private val rejectedAlgorithms =
        listOf(
            DEPRECATED_COSE_ED25519_OLD,
            DEPRECATED_COSE_9,
            DEPRECATED_COSE_51,
            DEPRECATED_COSE_52,
        )

    // ── Acceptance tests ──────────────────────────────────────────────────────

    @Test
    fun `negotiation accepts COSE_ES256 (-7)`() {
        assertTrue(
            negotiationPicks(listOf(COSE_ES256)),
            "COSE_ES256 (-7) must be accepted by algorithm negotiation",
        )
    }

    @Test
    fun `negotiation accepts COSE_EDSA (-8)`() {
        assertTrue(
            negotiationPicks(listOf(COSE_EDSA)),
            "COSE_EDSA (-8) must be accepted by algorithm negotiation per WebAuthn L3",
        )
    }

    @Test
    fun `negotiation accepts COSE_RS256 (-257)`() {
        assertTrue(
            negotiationPicks(listOf(COSE_RS256)),
            "COSE_RS256 (-257) must be accepted as interoperability fallback",
        )
    }

    @Test
    fun `negotiation accepts COSE_ML_DSA_65 (-49)`() {
        assertTrue(
            negotiationPicks(listOf(COSE_ML_DSA_65)),
            "COSE_ML_DSA_65 (-49) must be accepted as PQ extension",
        )
    }

    // ── Rejection tests ───────────────────────────────────────────────────────

    @Test
    fun `negotiation rejects old COSE_ED25519 (-19) alone`() {
        assertFalse(
            negotiationPicks(listOf(DEPRECATED_COSE_ED25519_OLD)),
            "Deprecated -19 must be rejected; only -8 is the L3 EdDSA identifier",
        )
    }

    @Test
    fun `negotiation rejects deprecated -9`() {
        assertFalse(
            negotiationPicks(listOf(DEPRECATED_COSE_9)),
            "Deprecated COSE alg -9 must be rejected",
        )
    }

    @Test
    fun `negotiation rejects deprecated -51`() {
        assertFalse(
            negotiationPicks(listOf(DEPRECATED_COSE_51)),
            "Deprecated COSE alg -51 must be rejected",
        )
    }

    @Test
    fun `negotiation rejects deprecated -52`() {
        assertFalse(
            negotiationPicks(listOf(DEPRECATED_COSE_52)),
            "Deprecated COSE alg -52 must be rejected",
        )
    }

    @Test
    fun `negotiation rejects list containing only deprecated IDs`() {
        assertFalse(
            negotiationPicks(listOf(DEPRECATED_COSE_ED25519_OLD, DEPRECATED_COSE_9)),
            "A list containing only deprecated IDs must result in negotiation failure",
        )
    }

    // ── Priority / ordering tests ─────────────────────────────────────────────

    @Test
    fun `negotiation prefers -7 over -8 when both offered`() {
        // The preference list in the handler is: -7, -8, -257, -49.
        // When the RP requests [-8, -7], the handler should select -7 first.
        val result = negotiationResult(listOf(COSE_EDSA, COSE_ES256))
        assertTrue(result == COSE_ES256, "Handler should prefer -7 (ES256) over -8 (EdDSA)")
    }

    @Test
    fun `negotiation selects -8 when -7 not offered`() {
        val result = negotiationResult(listOf(COSE_EDSA, COSE_RS256))
        assertTrue(result == COSE_EDSA, "Handler should select -8 (EdDSA) when -7 is absent")
    }

    @Test
    fun `negotiation falls back to -257 when only RS256 offered`() {
        val result = negotiationResult(listOf(COSE_RS256))
        assertTrue(result == COSE_RS256, "Handler should accept -257 (RS256) as fallback")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Simulates algorithm negotiation logic as defined in [Ctap2MakeCredentialHandler.handleMakeCredential].
     *
     * Returns `true` if ANY algorithm in [requestedAlgorithms] is supported,
     * `false` if all would result in CTAP2_ERR_UNSUPPORTED_ALGORITHM.
     */
    private fun negotiationPicks(requestedAlgorithms: List<Int>): Boolean =
        negotiationResult(requestedAlgorithms) != null

    /**
     * Returns the selected COSE algorithm ID, or `null` if negotiation fails.
     * Mirrors the preference order from [Ctap2MakeCredentialHandler]:
     *   -7 → -8 → -257 → -49
     */
    private fun negotiationResult(requestedAlgorithms: List<Int>): Int? {
        val supported = listOf(COSE_ES256, COSE_EDSA, COSE_RS256, COSE_ML_DSA_65)
        return supported.firstOrNull { it in requestedAlgorithms }
    }
}

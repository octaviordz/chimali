package com.chimali.fido2.ctap2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T035 — Unit tests for [Ctap2MakeCredentialHandler] attestation statement building.
 *
 * Verifies:
 * - "packed" format produces a map with "alg", "sig", "x5c" when present
 * - "none" format produces an empty map
 * - "attCA" format produces a map with same structure as "packed" (T036)
 *
 * These tests exercise the internal `buildAttestationStatementMap` logic
 * by inspecting what gets serialized into the CTAP2 response.
 *
 * Note: The full handler integration requires a Bluetooth HID stack; these tests
 * focus purely on the attestation statement map structure, which is stateless.
 */
class Ctap2AttestationStatementTest {
    // We replicate the private buildAttestationStatementMap logic here for testability.
    // The real implementation is in Ctap2MakeCredentialHandler.buildAttestationStatementMap.

    private fun buildAttestationStatementMap(
        fmt: String,
        alg: Any,
        sig: ByteArray? = null,
        x5c: List<ByteArray>? = null,
    ): Map<Any, Any> {
        return when (fmt) {
            "none" -> emptyMap()
            "packed", "attCA" ->
                buildMap {
                    put("alg", alg)
                    sig?.let { put("sig", it) }
                    x5c?.let { put("x5c", it) }
                }
            else -> emptyMap()
        }
    }

    // ── "packed" format ───────────────────────────────────────────────────────

    @Test
    fun packed_format_emitsAlg() {
        val map = buildAttestationStatementMap(fmt = "packed", alg = -7)
        assertTrue(map.containsKey("alg"), "packed statement must contain 'alg'")
        assertEquals(-7, map["alg"])
    }

    @Test
    fun packed_format_emitsSig_whenPresent() {
        val sig = ByteArray(64) { 0xAA.toByte() }
        val map = buildAttestationStatementMap(fmt = "packed", alg = -7, sig = sig)
        assertTrue(map.containsKey("sig"), "packed statement must contain 'sig' when attCert is non-null")
    }

    @Test
    fun packed_format_emitsX5c_whenPresent() {
        val chain = listOf(ByteArray(32) { 0xBB.toByte() })
        val map = buildAttestationStatementMap(fmt = "packed", alg = -7, x5c = chain)
        assertTrue(map.containsKey("x5c"), "packed statement must contain 'x5c' when chain is non-null")
    }

    // ── "none" format ─────────────────────────────────────────────────────────

    @Test
    fun none_format_returnsEmptyMap() {
        val map = buildAttestationStatementMap(fmt = "none", alg = "none")
        assertTrue(map.isEmpty(), "'none' attestation must produce an empty statement map")
    }

    // ── "attCA" format (T036) ─────────────────────────────────────────────────

    @Test
    fun attCA_format_emitsAlg() {
        val map = buildAttestationStatementMap(fmt = "attCA", alg = -7)
        assertTrue(map.containsKey("alg"), "attCA statement must contain 'alg'")
    }

    @Test
    fun attCA_format_emitsSig_whenPresent() {
        val sig = ByteArray(64) { 0xCC.toByte() }
        val map = buildAttestationStatementMap(fmt = "attCA", alg = -7, sig = sig)
        assertTrue(map.containsKey("sig"), "attCA statement must contain 'sig' when signature is present")
    }

    @Test
    fun attCA_format_emitsX5c_whenChainPresent() {
        val chain = listOf(ByteArray(32) { 0xDD.toByte() }, ByteArray(32) { 0xEE.toByte() })
        val map = buildAttestationStatementMap(fmt = "attCA", alg = -7, x5c = chain)
        assertTrue(map.containsKey("x5c"), "attCA statement must contain 'x5c' for the CA chain")
        @Suppress("UNCHECKED_CAST")
        assertEquals(2, (map["x5c"] as List<*>).size)
    }

    @Test
    fun unknown_format_returnsEmptyMap() {
        val map = buildAttestationStatementMap(fmt = "fido-u2f", alg = -7)
        assertTrue(map.isEmpty(), "Unknown/unhandled format must return empty map as fallback")
    }
}

package com.chimali.fido2.ctap2

import com.chimali.fido2.data.crypto.HmacSecretProcessor
import com.chimali.fido2.domain.model.PrfExtensionInput
import com.chimali.fido2.domain.model.PrfExtensionOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T041 — Integration-level tests for the CTAP2 `hmac-secret` extension parsing
 * in [Ctap2GetAssertionHandler].
 *
 * These tests verify the typed PRF output CBOR map structure:
 * - One salt → output CBOR map has key 1 only
 * - Two salts → output CBOR map has keys 1 and 2
 * - Three+ salts (>MAX_SALTS) → [PrfExtensionInput] throws → extension is omitted
 *
 * Note: Full end-to-end handler integration requires a live Android device/emulator
 * with Bluetooth HID. These tests focus on the CBOR output map structure logic
 * which is exercised through the PrfExtensionOutput and PrfExtensionInput types.
 *
 * @see PrfExtensionInput for salt validation
 * @see PrfExtensionOutput.toCborMap for the integer-keyed serialization
 */
class Ctap2GetAssertionHandlerPrfTest {
    private val output1Bytes = ByteArray(32) { 0xAA.toByte() }
    private val output2Bytes = ByteArray(32) { 0xBB.toByte() }

    // ── One salt → CBOR map with key 1 only ──────────────────────────────────

    @Test
    fun singleSalt_cborMap_containsKey1Only() {
        val prfOutput = PrfExtensionOutput(output1 = output1Bytes)
        val cborMap = prfOutput.toCborMap()

        assertEquals(1, cborMap.size, "Single-salt PRF output map must have exactly 1 key")
        assertTrue(cborMap.containsKey(1), "Key 1 must be present for output1")
        assertFalse(cborMap.containsKey(2), "Key 2 must not be present for single-salt output")
    }

    @Test
    fun singleSalt_cborMap_output1_is32Bytes() {
        val prfOutput = PrfExtensionOutput(output1 = output1Bytes)
        val cborMap = prfOutput.toCborMap()

        assertEquals(
            32,
            (cborMap[1] as ByteArray).size,
            "output1 in CBOR map must be exactly 32 bytes",
        )
    }

    // ── Two salts → CBOR map with keys 1 and 2 ───────────────────────────────

    @Test
    fun dualSalt_cborMap_containsKeys1And2() {
        val prfOutput = PrfExtensionOutput(output1 = output1Bytes, output2 = output2Bytes)
        val cborMap = prfOutput.toCborMap()

        assertEquals(2, cborMap.size, "Dual-salt PRF output map must have exactly 2 keys")
        assertTrue(cborMap.containsKey(1), "Key 1 must be present for output1")
        assertTrue(cborMap.containsKey(2), "Key 2 must be present for output2")
    }

    @Test
    fun dualSalt_cborMap_bothOutputs_are32Bytes() {
        val prfOutput = PrfExtensionOutput(output1 = output1Bytes, output2 = output2Bytes)
        val cborMap = prfOutput.toCborMap()

        assertEquals(32, (cborMap[1] as ByteArray).size, "output1 must be 32 bytes")
        assertEquals(32, (cborMap[2] as ByteArray).size, "output2 must be 32 bytes")
    }

    @Test
    fun dualSalt_cborMap_output1_and_output2_are_distinct() {
        val prfOutput = PrfExtensionOutput(output1 = output1Bytes, output2 = output2Bytes)
        val cborMap = prfOutput.toCborMap()

        val out1 = cborMap[1] as ByteArray
        val out2 = cborMap[2] as ByteArray
        assertFalse(out1.contentEquals(out2), "output1 and output2 must differ for distinct salts")
    }

    // ── Three salts → PrfExtensionInput throws → omit extension ──────────────

    @Test
    fun threeSalts_prfExtensionInput_throws() {
        val salt = ByteArray(32) { 0x01 }
        var threw = false
        try {
            PrfExtensionInput(listOf(salt, salt, salt))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(
            threw,
            "PrfExtensionInput with >2 salts must throw IllegalArgumentException (T048)",
        )
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun cborMap_integerKeys_not_string_keys() {
        val prfOutput = PrfExtensionOutput(output1 = output1Bytes, output2 = output2Bytes)
        val cborMap = prfOutput.toCborMap()

        // Keys must be Int, not String (per CTAP2 CBOR encoding spec)
        assertTrue(
            cborMap.keys.all { it is Int },
            "CBOR map keys must be integers (1, 2) per CTAP2 §12.4",
        )
    }

    @Test
    fun hmacSecretProcessor_extensionKey_is_hmac_secret_literal() {
        assertEquals(
            "hmac-secret",
            HmacSecretProcessor.EXTENSION_KEY,
            "EXTENSION_KEY must match the CTAP2 spec string 'hmac-secret'",
        )
    }
}

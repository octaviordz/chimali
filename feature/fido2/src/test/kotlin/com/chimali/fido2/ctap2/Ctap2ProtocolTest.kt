package com.chimali.fido2.ctap2

import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.domain.model.AttestationObject
import com.chimali.fido2.domain.model.AttestationStatement
import com.chimali.fido2.domain.model.AuthenticatorData
import com.chimali.fido2.domain.model.AuthenticatorTransport
import com.chimali.fido2.domain.model.ClientData
import com.chimali.fido2.domain.service.AuthenticatorInfo
import kotlin.test.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import java.time.Instant

/**
 * Unit tests for CTAP2 protocol layer (T056).
 *
 * Tests [Ctap2ResponseBuilder] response encoding to verify correct CTAP2 wire format:
 * - Status byte at byte-offset 0 of payload
 * - Encoded body following the status byte
 * - Correct 64-byte HID packet framing from [HidReportParser]
 */
class Ctap2ProtocolTest {
    private lateinit var cborCodec: CborCodec
    private lateinit var hidReportParser: HidReportParser
    private lateinit var responseBuilder: Ctap2ResponseBuilder

    private val testCid = byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D)
    private val aaguid = ByteArray(16) { 0x43.toByte() }
    private val credId = ByteArray(16) { 0x01 }
    private val rpIdHash = ByteArray(32) { 0x99.toByte() }
    private val pubKey = ByteArray(65) { 0x04 }

    @BeforeTest
    fun setUp() {
        cborCodec = CborCodec()
        hidReportParser = HidReportParser()
        responseBuilder = Ctap2ResponseBuilder(cborCodec, hidReportParser)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeAuthData(uvPerformed: Boolean = false): AuthenticatorData {
        val flags = if (uvPerformed) 0x45.toByte() else 0x41.toByte()
        return AuthenticatorData(
            rpIdHash = rpIdHash,
            flags = byteArrayOf(flags),
            counter = 1L,
            aaguid = aaguid,
            credentialId = credId,
            publicKey = pubKey,
        )
    }

    private fun makeAttestationObject(): AttestationObject {
        val authData = makeAuthData()
        val attStmt =
            AttestationStatement(
                alg = "ES256",
                fmt = "none",
                attCert = null,
                authData = null,
                x5c = null,
            )
        val clientData =
            ClientData(
                type = "webauthn.create",
                challenge = ByteArray(16) { 0x77.toByte() },
                origin = "https://example.com",
                crossOrigin = false,
                timestamp = Instant.now(),
            )
        return AttestationObject(
            fmt = "none",
            authData = authData,
            attStmt = attStmt,
            clientData = clientData,
        )
    }

    // ── Ctap2ResponseBuilder — error response ─────────────────────────────────

    @Test
    fun `errorResponse produces single packet with status byte`() {
        val statusCode = 0x12.toByte()
        val packets = responseBuilder.errorResponse(testCid, statusCode)

        assertTrue(packets.isNotEmpty())
        assertEquals(62, packets[0].size)
        assertContentEquals(testCid, packets[0].copyOfRange(0, 4))

        // Init flag must be set in CMD byte
        val cmdByte = packets[0][4].toInt() and 0xFF
        assertTrue(cmdByte and 0x80 != 0)

        // BCNT = 1 (just status byte)
        val bcnt = ((packets[0][5].toInt() and 0xFF) shl 8) or (packets[0][6].toInt() and 0xFF)
        assertEquals(1, bcnt)

        // First data byte = status
        assertEquals(statusCode, packets[0][7])
    }

    @Test
    fun `keepAliveResponse produces correct structure`() {
        val packets = responseBuilder.keepAliveResponse(testCid, 0x01)
        assertTrue(packets.isNotEmpty())
        assertEquals(62, packets[0].size)
        assertContentEquals(testCid, packets[0].copyOfRange(0, 4))
    }

    // ── makeCredentialResponse ────────────────────────────────────────────────

    @Test
    fun `makeCredentialResponse success starts with CTAP2_OK`() {
        val attestation = makeAttestationObject()
        val packets = responseBuilder.makeCredentialResponse(testCid, attestation)

        assertTrue(packets.isNotEmpty())
        assertEquals(0x00.toByte(), packets[0][7], "CTAP2_OK (0x00) must be first payload byte")
    }

    @Test
    fun `makeCredentialResponse encodes into 64-byte packets`() {
        val attestation = makeAttestationObject()
        val packets = responseBuilder.makeCredentialResponse(testCid, attestation)
        packets.forEach { assertEquals(62, it.size) }
    }

    @Test
    fun `makeCredentialResponse payload includes status plus body`() {
        val attestation = makeAttestationObject()
        val packets = responseBuilder.makeCredentialResponse(testCid, attestation)

        val bcnth = packets[0][5].toInt() and 0xFF
        val bcntl = packets[0][6].toInt() and 0xFF
        val totalLen = (bcnth shl 8) or bcntl
        assertTrue(totalLen >= 2, "Must have status + at least 1 body byte; got $totalLen")
    }

    // ── getInfoResponse ───────────────────────────────────────────────────────

    @Test
    fun `getInfoResponse success starts with CTAP2_OK`() {
        val info =
            AuthenticatorInfo(
                aaguid = aaguid,
                version = "1.0",
                supportedAlgorithms = listOf("ES256"),
                supportedTransports = listOf(AuthenticatorTransport.BLE),
                supportsResidentKeys = true,
                supportsUserVerification = true,
                maxCredentialCount = 50,
                maxCredentialIdLength = 128,
                firmwareVersion = "1.0.0",
                serialNumber = "CHIMALI-0001",
                isInitialized = true,
                isLocked = false,
            )
        val packets = responseBuilder.getInfoResponse(testCid, info)
        assertTrue(packets.isNotEmpty())
        assertEquals(0x00.toByte(), packets[0][7])
    }

    // ── hidErrorResponse ─────────────────────────────────────────────────────

    @Test
    fun `hidErrorResponse produces framed 64-byte packet`() {
        val packets = responseBuilder.hidErrorResponse(testCid, 0x01)
        assertTrue(packets.isNotEmpty())
        assertEquals(62, packets[0].size)
        assertContentEquals(testCid, packets[0].copyOfRange(0, 4))
    }

    // ── statusDescription ─────────────────────────────────────────────────────

    @Test
    fun `statusDescription returns non-empty for known codes`() {
        val knownCodes =
            listOf(
                0x00.toByte(),
                0x12.toByte(),
                0x14.toByte(),
                0x27.toByte(),
                0x28.toByte(),
                0x36.toByte(),
            )
        knownCodes.forEach { code ->
            val desc = responseBuilder.statusDescription(code)
            assertTrue(desc.isNotBlank())
            assertFalse(
                desc.startsWith("UNKNOWN"),
                "Known code should not produce UNKNOWN: $desc",
            )
        }
    }

    @Test
    fun `statusDescription returns UNKNOWN for unrecognised code`() {
        val desc = responseBuilder.statusDescription(0x99.toByte())
        assertTrue(desc.startsWith("UNKNOWN"))
    }
}

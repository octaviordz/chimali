package com.chimali.fido2.ctap2

import com.chimali.fido2.bluetooth.*
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.domain.model.AttestationObject
import com.chimali.fido2.domain.model.AttestationStatement
import com.chimali.fido2.domain.model.AuthenticatorData
import com.chimali.fido2.domain.model.AuthenticatorTransport
import com.chimali.fido2.domain.model.ClientData
import com.chimali.fido2.domain.service.AuthenticatorInfo
import java.time.Instant
import kotlin.test.*
import kotlin.test.BeforeTest
import kotlin.test.Test

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

    private val testCid = byteArrayOf(CID_0, CID_1, CID_2, CID_3)
    private val aaguid = ByteArray(AAGUID_SIZE_16) { 0x43.toByte() }
    private val credId = ByteArray(CREDENTIAL_ID_SIZE_16) { 0x01 }
    private val rpIdHash = ByteArray(RP_ID_HASH_SIZE_32) { 0x99.toByte() }
    private val pubKey = ByteArray(PUBLIC_KEY_SIZE_65) { UNCOMPRESSED_KEY_PREFIX }

    private companion object {
        private const val AAGUID_SIZE_16 = 16
        private const val CREDENTIAL_ID_SIZE_16 = 16
        private const val RP_ID_HASH_SIZE_32 = 32
        private const val PUBLIC_KEY_SIZE_65 = 65
        private const val CHALLENGE_SIZE_16 = 16
        private const val STATUS_BYTE_OFFSET = 7
        private const val TEST_KEEPALIVE_STATUS = 0x01.toByte()
        private const val TEST_HID_ERROR_CODE = 0x01.toByte()
        private const val UNKNOWN_STATUS_CODE = 0x99.toByte()

        private const val CID_0 = 0x0A.toByte()
        private const val CID_1 = 0x0B.toByte()
        private const val CID_2 = 0x0C.toByte()
        private const val CID_3 = 0x0D.toByte()
        private const val UNCOMPRESSED_KEY_PREFIX = 0x04.toByte()

        private const val FLAG_UP_UV = 0x45.toByte()
        private const val FLAG_UP_ONLY = 0x41.toByte()
        private const val COUNTER_INITIAL = 1L
        private const val DUMMY_CHALLENGE_BYTE = 0x77.toByte()
        private const val BYTE_MASK_FF = 0xFF
        private const val BIT_8 = 8
        private const val BIT_7_MASK = 0x80
        private const val BCNT_ONE = 1
        private const val MIN_BODY_LEN_2 = 2
        private const val MAX_CRED_COUNT_50 = 50
        private const val MAX_CRED_ID_LEN_128 = 128
    }

    @BeforeTest
    fun setUp() {
        cborCodec = CborCodec()
        hidReportParser = HidReportParser()
        responseBuilder = Ctap2ResponseBuilder(cborCodec, hidReportParser)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeAuthData(uvPerformed: Boolean = false): AuthenticatorData {
        val flags = if (uvPerformed) FLAG_UP_UV else FLAG_UP_ONLY
        return AuthenticatorData(
            rpIdHash = rpIdHash,
            flags = byteArrayOf(flags),
            counter = COUNTER_INITIAL,
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
                challenge = ByteArray(CHALLENGE_SIZE_16) { DUMMY_CHALLENGE_BYTE },
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
        val statusCode = CTAP2_ERR_INVALID_CBOR
        val packets = responseBuilder.errorResponse(testCid, statusCode)

        assertTrue(packets.isNotEmpty())
        assertEquals(HID_PACKET_SIZE, packets[0].size)
        assertContentEquals(testCid, packets[0].copyOfRange(0, CID_SIZE))

        // Init flag must be set in CMD byte
        val cmdByte = packets[0][INIT_CMD_OFFSET].toInt() and BYTE_MASK_FF
        assertTrue(cmdByte and BIT_7_MASK != 0)

        // BCNT = 1 (just status byte)
        val bcnth = packets[0][INIT_BCNTH_OFFSET].toInt() and BYTE_MASK_FF
        val bcntl = packets[0][INIT_BCNTL_OFFSET].toInt() and BYTE_MASK_FF
        val bcnt = (bcnth shl BIT_8) or bcntl
        assertEquals(BCNT_ONE, bcnt)

        // First data byte = status
        assertEquals(statusCode, packets[0][STATUS_BYTE_OFFSET])
    }

    @Test
    fun `keepAliveResponse produces correct structure`() {
        val packets = responseBuilder.keepAliveResponse(testCid, TEST_KEEPALIVE_STATUS)
        assertTrue(packets.isNotEmpty())
        assertEquals(HID_PACKET_SIZE, packets[0].size)
        assertContentEquals(testCid, packets[0].copyOfRange(0, CID_SIZE))
    }

    // ── makeCredentialResponse ────────────────────────────────────────────────

    @Test
    fun `makeCredentialResponse success starts with CTAP2_OK`() {
        val attestation = makeAttestationObject()
        val packets = responseBuilder.makeCredentialResponse(testCid, attestation)

        assertTrue(packets.isNotEmpty())
        assertEquals(CTAP2_OK, packets[0][STATUS_BYTE_OFFSET], "CTAP2_OK (0x00) must be first payload byte")
    }

    @Test
    fun `makeCredentialResponse encodes into 64-byte packets`() {
        val attestation = makeAttestationObject()
        val packets = responseBuilder.makeCredentialResponse(testCid, attestation)
        packets.forEach { assertEquals(HID_PACKET_SIZE, it.size) }
    }

    @Test
    fun `makeCredentialResponse payload includes status plus body`() {
        val attestation = makeAttestationObject()
        val packets = responseBuilder.makeCredentialResponse(testCid, attestation)

        val bcnth = packets[0][INIT_BCNTH_OFFSET].toInt() and BYTE_MASK_FF
        val bcntl = packets[0][INIT_BCNTL_OFFSET].toInt() and BYTE_MASK_FF
        val totalLen = (bcnth shl BIT_8) or bcntl
        assertTrue(totalLen >= MIN_BODY_LEN_2, "Must have status + at least 1 body byte; got $totalLen")
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
                maxCredentialCount = MAX_CRED_COUNT_50,
                maxCredentialIdLength = MAX_CRED_ID_LEN_128,
                firmwareVersion = "1.0.0",
                serialNumber = "CHIMALI-0001",
                isInitialized = true,
                isLocked = false,
            )
        val packets = responseBuilder.getInfoResponse(testCid, info)
        assertTrue(packets.isNotEmpty())
        assertEquals(CTAP2_OK, packets[0][STATUS_BYTE_OFFSET])
    }

    // ── hidErrorResponse ─────────────────────────────────────────────────────

    @Test
    fun `hidErrorResponse produces framed 64-byte packet`() {
        val packets = responseBuilder.hidErrorResponse(testCid, TEST_HID_ERROR_CODE)
        assertTrue(packets.isNotEmpty())
        assertEquals(HID_PACKET_SIZE, packets[0].size)
        assertContentEquals(testCid, packets[0].copyOfRange(0, CID_SIZE))
    }

    // ── statusDescription ─────────────────────────────────────────────────────

    @Test
    fun `statusDescription returns non-empty for known codes`() {
        val knownCodes =
            listOf(
                CTAP2_OK,
                CTAP2_ERR_INVALID_CBOR,
                CTAP2_ERR_MISSING_PARAMETER,
                CTAP2_ERR_OPERATION_DENIED,
                CTAP2_ERR_KEY_STORE_FULL,
                CTAP2_ERR_NOT_ALLOWED,
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
        val desc = responseBuilder.statusDescription(UNKNOWN_STATUS_CODE)
        assertTrue(desc.startsWith("UNKNOWN"))
    }
}

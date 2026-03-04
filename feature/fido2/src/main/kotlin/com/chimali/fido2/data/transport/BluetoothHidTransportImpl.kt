package com.chimali.fido2.data.transport

import android.util.Log
import com.chimali.fido2.bluetooth.BluetoothHidDeviceWrapper
import com.chimali.fido2.bluetooth.BROADCAST_CID
import com.chimali.fido2.bluetooth.CTAPHID_CBOR
import com.chimali.fido2.bluetooth.CTAPHID_INIT
import com.chimali.fido2.bluetooth.CTAPHID_PING
import com.chimali.fido2.bluetooth.CTAPHID_CANCEL
import com.chimali.fido2.bluetooth.CTAPHID_MSG
import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.bluetooth.HidConnectionState
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.ctap2.Ctap2GetAssertionHandler
import com.chimali.fido2.ctap2.Ctap2MakeCredentialHandler
import com.chimali.fido2.ctap2.Ctap2ResponseBuilder
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.service.AuthenticatorInfo
import com.chimali.fido2.domain.service.Fido2Authenticator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BluetoothHidTransport"

// CTAPHID error codes (§8.4)
private const val ERR_INVALID_CMD:   Byte = 0x01
private const val ERR_INVALID_PAR:   Byte = 0x02
private const val ERR_INVALID_LEN:   Byte = 0x03
private const val ERR_INVALID_SEQ:   Byte = 0x04
private const val ERR_MSG_TIMEOUT:   Byte = 0x05
private const val ERR_CHANNEL_BUSY:  Byte = 0x06
private const val ERR_LOCK_REQUIRED: Byte = 0x0A
private const val ERR_INVALID_CHANNEL: Byte = 0x0B
private const val ERR_OTHER:         Byte = 0x7F.toByte()

/**
 * Central FIDO2 HID transport layer (T053 + T054).
 *
 * Implements [Fido2Transport] as the top-level orchestrator that:
 * 1. Initialises and registers the [BluetoothHidDeviceWrapper] (peripheral role)
 * 2. Consumes raw 64-byte HID reports from [BluetoothHidDeviceWrapper.incomingReports]
 * 3. Reassembles multi-packet CTAPHID messages via [HidReportParser]
 * 4. Dispatches each command to the appropriate CTAP2 handler
 * 5. Sends responses back via [BluetoothHidDeviceWrapper.sendReport]
 * 6. Manages per-channel session state and CID assignment
 *
 * Connection State Management (T054)
 * ------------------------------------
 * Channel IDs (CIDs) are 4-byte per-session identifiers assigned during
 * CTAPHID_INIT. The broadcast CID (0xFFFFFFFF) is used only for INIT.
 * Each connected host gets its own CID tracked in [channelRegistry].
 */
@Singleton
class BluetoothHidTransportImpl @Inject constructor(
    private val hidWrapper: BluetoothHidDeviceWrapper,
    private val hidReportParser: HidReportParser,
    private val makeCredentialHandler: Ctap2MakeCredentialHandler,
    private val getAssertionHandler: Ctap2GetAssertionHandler,
    private val responseBuilder: Ctap2ResponseBuilder,
    private val fido2Authenticator: Fido2Authenticator
) : Fido2Transport {

    private val secureRandom = SecureRandom()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Set of currently allocated channel IDs (hex string → CID bytes). */
    private val channelRegistry = mutableMapOf<String, ByteArray>()

    private var receiveJob: Job? = null

    // ── Fido2Transport interface ───────────────────────────────────────────────

    override suspend fun connect(): Result<Unit> {
        return try {
            hidWrapper.initialize().getOrThrow()
            hidWrapper.registerApp().getOrThrow()
            startReceiving()
            observeConnectionState()
            Log.i(TAG, "BluetoothHidTransport connected and advertising")
            Result.success(Unit)
        } catch (e: Fido2Exception) {
            Log.e(TAG, "connect() failed: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "connect() unexpected failure", e)
            Result.failure(Fido2Exception.TransportException("Failed to start HID transport: ${e.message}"))
        }
    }

    override suspend fun disconnect(): Result<Unit> {
        return try {
            receiveJob?.cancel()
            receiveJob = null
            hidWrapper.unregisterApp()
            channelRegistry.clear()
            hidReportParser.reset()
            Log.i(TAG, "BluetoothHidTransport disconnected")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "disconnect() failed", e)
            Result.failure(Fido2Exception.TransportException("Disconnect error: ${e.message}"))
        }
    }

    /**
     * Low-level send — encodes [command] as a CTAPHID_CBOR init-packet and
     * transmits every resulting 64-byte report.
     *
     * Callers should prefer using the higher-level [Ctap2ResponseBuilder] to
     * build response packets and then call [sendPackets] directly.
     */
    override suspend fun sendCommand(command: ByteArray): Result<ByteArray> {
        // Not used directly — higher-level handlers call sendPackets()
        return Result.success(byteArrayOf())
    }

    override suspend fun isConnected(): Boolean = hidWrapper.isConnected()

    // ── Connection state observation (T054) ───────────────────────────────────

    private fun observeConnectionState() {
        hidWrapper.connectionState.onEach { state ->
            when (state) {
                is HidConnectionState.Connected -> {
                    Log.i(TAG, "Host connected: ${state.device.address}")
                    // Transport is ready for CTAPHID_INIT from the host
                }
                is HidConnectionState.Advertising -> {
                    Log.d(TAG, "Advertising for host connections")
                    // Clean up any channels from prior host session
                    channelRegistry.clear()
                    hidReportParser.reset()
                }
                is HidConnectionState.Idle -> {
                    Log.d(TAG, "HID transport idle")
                }
                is HidConnectionState.Error -> {
                    Log.e(TAG, "HID connection error: ${state.message}")
                }
                else -> { /* Connecting — nothing to do */ }
            }
        }.launchIn(scope)
    }

    // ── Report receiver loop ──────────────────────────────────────────────────

    private fun startReceiving() {
        receiveJob?.cancel()
        receiveJob = scope.launch {
            for (report in hidWrapper.incomingReports) {
                processReport(report)
            }
        }
    }

    private suspend fun processReport(report: ByteArray) {
        val result = hidReportParser.processReport(report)
        if (result.isFailure) {
            Log.e(TAG, "Report parse error: ${result.exceptionOrNull()?.message}")
            // Can't identify CID from a broken packet; send broadcast error
            sendPackets(responseBuilder.hidErrorResponse(BROADCAST_CID, ERR_INVALID_SEQ))
            return
        }

        val message = result.getOrNull() ?: return  // null = still accumulating

        Log.d(TAG, "CTAPHID cmd=0x${message.command.toString(16).uppercase()} " +
                   "cid=${message.channelId.toHex()} payloadLen=${message.payload.size}")

        dispatchMessage(message)
    }

    // ── Command dispatcher ────────────────────────────────────────────────────

    private suspend fun dispatchMessage(message: CtapHidMessage) {
        val cid = message.channelId
        when (message.command) {
            CTAPHID_INIT    -> handleInit(message)
            CTAPHID_CBOR    -> handleCbor(message)
            CTAPHID_MSG     -> handleMsg(message)
            CTAPHID_PING    -> handlePing(message)
            CTAPHID_CANCEL  -> handleCancel(message)
            else -> {
                Log.w(TAG, "Unknown CTAPHID command 0x${message.command.toString(16)}")
                sendPackets(responseBuilder.hidErrorResponse(cid, ERR_INVALID_CMD))
            }
        }
    }

    // ── CTAPHID_INIT ──────────────────────────────────────────────────────────

    private fun handleInit(message: CtapHidMessage) {
        val nonce = message.payload.takeIf { it.size >= 8 }?.copyOfRange(0, 8)
        if (nonce == null) {
            sendPackets(responseBuilder.hidErrorResponse(BROADCAST_CID, ERR_INVALID_LEN))
            return
        }

        // Assign a new CID for this session
        val newCid = generateCid()
        channelRegistry[newCid.toHex()] = newCid
        Log.i(TAG, "CTAPHID_INIT: assigned CID=${newCid.toHex()}")

        val initResponse = hidReportParser.buildInitResponse(nonce, newCid)
        sendPackets(hidReportParser.encodeResponse(initResponse))
    }

    // ── CTAPHID_CBOR ──────────────────────────────────────────────────────────

    private suspend fun handleCbor(message: CtapHidMessage) {
        val cid = message.channelId

        // Validate channel (broadcast CID not allowed for CBOR commands)
        if (cid.contentEquals(BROADCAST_CID)) {
            sendPackets(responseBuilder.hidErrorResponse(cid, ERR_INVALID_CHANNEL))
            return
        }

        val payload = message.payload
        if (payload.isEmpty()) {
            sendPackets(responseBuilder.errorResponse(cid, 0x12.toByte())) // INVALID_CBOR
            return
        }

        val ctapCommand = payload[0].toInt() and 0xFF
        Log.d(TAG, "CTAP2 command=0x${ctapCommand.toString(16)} on CID=${cid.toHex()}")

        // ── Periodic keepalive loop ────────────────────────────────────────────
        // CTAP HID spec §8.5.5: the authenticator MUST send CTAPHID_KEEPALIVE
        // continuously at a period ≤ 500ms while processing a CBOR command.
        //
        // IMPORTANT: the initial delay must come BEFORE the first keepalive send.
        // Fast commands (GetInfo) complete in <10ms; an immediate keepalive would
        // arrive at Windows BEFORE the real response, causing ERROR_INVALID_DATA.
        // The rauth-android reference always sleeps first, then sends.
        val keepaliveJob: Job = scope.launch {
            delay(200L)  // ← wait first; fast commands finish before this fires
            sendPackets(responseBuilder.keepAliveResponse(cid, 0x01)) // PROCESSING
            while (true) {
                delay(200L) // 200ms between subsequent keepalives (≤500ms per spec)
                sendPackets(responseBuilder.keepAliveResponse(cid, 0x02)) // UPNEEDED
            }
        }

        val responsePackets = try {
            when (ctapCommand) {
                0x01 -> makeCredentialHandler.handle(message)         // authenticatorMakeCredential
                0x02 -> handleGetAssertion(message)                    // authenticatorGetAssertion
                0x04 -> handleGetInfo(cid)                            // authenticatorGetInfo
                else -> {
                    Log.w(TAG, "Unsupported CTAP2 command 0x${ctapCommand.toString(16)}")
                    responseBuilder.errorResponse(cid, 0x3E.toByte()) // CTAP2_ERR_OPERATION_DENIED
                }
            }
        } finally {
            // cancelAndJoin() (not just cancel()) ensures the keepalive coroutine
            // has completely stopped before we send the real response. Without this,
            // a keepalive in-flight could arrive at Windows AFTER the CBOR response,
            // corrupting the framing of the next request.
            keepaliveJob.cancelAndJoin()
        }

        sendPackets(responsePackets)
    }

    // ── CTAPHID_PING ──────────────────────────────────────────────────────────

    private fun handlePing(message: CtapHidMessage) {
        // Echo back the same data on PING
        val pongMsg = CtapHidMessage(message.channelId, CTAPHID_PING, message.payload)
        sendPackets(hidReportParser.encodeResponse(pongMsg))
    }

    // ── CTAPHID_MSG (U2F / APDU compat layer) ────────────────────────────────
    //
    // Windows wraps CTAP2 MakeCredential/GetAssertion in an ISO 7816-4 APDU
    // and delivers it via CTAPHID_MSG when it needs U2F compatibility.
    //
    // Extended-length APDU layout (73 bytes for a typical MakeCredential):
    //   [0]    CLA  (0x00)
    //   [1]    INS  (0x10 = CTAP2-over-MSG, 0x01/02/03 = legacy U2F)
    //   [2]    P1
    //   [3]    P2
    //   [4]    0x00 (extended-length marker)
    //   [5-6]  Lc big-endian (number of data bytes)
    //   [7..7+Lc-1] DATA (for CTAP2: first byte = CTAP cmd, rest = CBOR)
    //   last 2 bytes: Le (often 0x00, 0x00)
    //
    // For CTAP2-over-MSG (INS=0x10): unwrap and route to handleCbor().
    // For pure U2F (INS != 0x10): respond with SW1=0x6D SW2=0x00 so
    // Windows knows to use the CTAP2 path instead.

    private suspend fun handleMsg(message: CtapHidMessage) {
        val cid     = message.channelId
        val payload = message.payload
        Log.d(TAG, "CTAPHID_MSG len=${payload.size} cid=${cid.toHex()}")

        if (payload.size < 4) {
            sendPackets(responseBuilder.hidErrorResponse(cid, ERR_INVALID_LEN))
            return
        }

        val ins = payload[1].toInt() and 0xFF

        // CTAP2-over-MSG: INS = 0x10, data is CBOR payload
        if (ins == 0x10) {
            val cborData = extractApduData(payload)
            if (cborData == null || cborData.isEmpty()) {
                Log.w(TAG, "CTAPHID_MSG INS=0x10 but APDU data is empty")
                sendPackets(u2fErrorResponse(cid, 0x6F, 0x00)) // SW_UNKNOWN
                return
            }
            Log.d(TAG, "CTAPHID_MSG routing CTAP2 cmd=0x${(cborData[0].toInt() and 0xFF).toString(16)} as CBOR")
            // Synthesise a CTAPHID_CBOR message with the unwrapped CBOR payload
            val syntheticMsg = CtapHidMessage(cid, CTAPHID_CBOR, cborData)
            handleCbor(syntheticMsg)
            return
        }

        // Pure U2F commands (Register=0x01, Authenticate=0x02, Version=0x03)
        when (ins) {
            0x03 -> {
                // U2F_VERSION — respond "U2F_V2" so Windows knows we speak U2F
                val u2fVersion = "U2F_V2".toByteArray(Charsets.US_ASCII)
                sendPackets(u2fSuccessResponse(cid, u2fVersion))
            }
            else -> {
                Log.d(TAG, "CTAPHID_MSG U2F INS=0x${ins.toString(16)} not supported — returning SW_INS_NOT_SUPPORTED")
                // SW 0x6D 0x00 = INS_NOT_SUPPORTED → Windows will use CTAP2 path
                sendPackets(u2fErrorResponse(cid, 0x6D, 0x00))
            }
        }
    }

    /** Extract data bytes from an ISO 7816-4 APDU (handles extended and short Lc). */
    private fun extractApduData(apdu: ByteArray): ByteArray? {
        if (apdu.size < 4) return null
        return try {
            if (apdu.size == 4) return ByteArray(0)          // no body
            if (apdu[4] != 0x00.toByte()) {                  // short Lc
                val lc = apdu[4].toInt() and 0xFF
                apdu.copyOfRange(5, 5 + lc)
            } else {                                         // extended Lc
                if (apdu.size < 7) return null
                val lc = ((apdu[5].toInt() and 0xFF) shl 8) or (apdu[6].toInt() and 0xFF)
                apdu.copyOfRange(7, 7 + lc)
            }
        } catch (e: Exception) { null }
    }

    /** Build a U2F success APDU response: data + SW1=0x90 SW2=0x00 */
    private fun u2fSuccessResponse(cid: ByteArray, data: ByteArray): List<ByteArray> {
        val resp = data + byteArrayOf(0x90.toByte(), 0x00)
        val msg  = CtapHidMessage(cid, CTAPHID_MSG, resp)
        return hidReportParser.encodeResponse(msg)
    }

    /** Build a U2F error APDU response: SW1 + SW2 only */
    private fun u2fErrorResponse(cid: ByteArray, sw1: Int, sw2: Int): List<ByteArray> {
        val resp = byteArrayOf(sw1.toByte(), sw2.toByte())
        val msg  = CtapHidMessage(cid, CTAPHID_MSG, resp)
        return hidReportParser.encodeResponse(msg)
    }

    private fun handleCancel(message: CtapHidMessage) {
        Log.d(TAG, "CTAPHID_CANCEL on CID=${message.channelId.toHex()}")
        // Acknowledge cancel — no response payload per spec
    }

    // ── authenticatorGetAssertion ─────────────────────────────────────────────

    private suspend fun handleGetAssertion(message: CtapHidMessage): List<ByteArray> {
        val cid = message.channelId
        val requestBytes = message.payload.drop(1).toByteArray() // strip the 0x02 command byte
        return try {
            val responsePayload = getAssertionHandler.handle(requestBytes)
            // Wrap payload in a CTAPHID_CBOR response packet
            val responseMsg = CtapHidMessage(cid, CTAPHID_CBOR, responsePayload)
            hidReportParser.encodeResponse(responseMsg)
        } catch (e: Exception) {
            Log.e(TAG, "GetAssertion handler exception: ${e.message}")
            responseBuilder.errorResponse(cid, 0x30.toByte())
        }
    }

    // ── authenticatorGetInfo ──────────────────────────────────────────────────

    private suspend fun handleGetInfo(cid: ByteArray): List<ByteArray> {
        return try {
            val info = fido2Authenticator.getAuthenticatorInfo()
            responseBuilder.getInfoResponse(cid, info)
        } catch (e: Exception) {
            Log.e(TAG, "GetInfo failed: ${e.message}")
            responseBuilder.errorResponse(cid, 0x30.toByte())
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun sendPackets(packets: List<ByteArray>) {
        for (packet in packets) {
            if (!hidWrapper.sendReport(packet)) {
                Log.w(TAG, "sendReport returned false — host may have disconnected")
                break
            }
        }
    }

    private fun generateCid(): ByteArray {
        val cid = ByteArray(4)
        do {
            secureRandom.nextBytes(cid)
            // Avoid re-using BROADCAST_CID or already-allocated CIDs
        } while (cid.contentEquals(BROADCAST_CID) || channelRegistry.containsKey(cid.toHex()))
        return cid
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    /** Expose connection state for observing by the presentation layer. */
    override val connectionState: StateFlow<HidConnectionState> = hidWrapper.connectionState
}

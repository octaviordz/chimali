package com.chimali.fido2.data.transport

import android.util.Log
import com.chimali.fido2.bluetooth.BluetoothHidDeviceWrapper
import com.chimali.fido2.bluetooth.BROADCAST_CID
import com.chimali.fido2.bluetooth.CTAPHID_CBOR
import com.chimali.fido2.bluetooth.CTAPHID_INIT
import com.chimali.fido2.bluetooth.CTAPHID_PING
import com.chimali.fido2.bluetooth.CTAPHID_CANCEL
import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.bluetooth.HidConnectionState
import com.chimali.fido2.bluetooth.HidReportParser
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
            hidWrapper.initialize()
            hidWrapper.registerApp()
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

        // Send periodic keepalive while processing
        sendPackets(responseBuilder.keepAliveResponse(cid, 0x01))

        val responsePackets = when (ctapCommand) {
            0x01 -> makeCredentialHandler.handle(message)         // authenticatorMakeCredential
            0x04 -> handleGetInfo(cid)                            // authenticatorGetInfo
            else -> {
                Log.w(TAG, "Unsupported CTAP2 command 0x${ctapCommand.toString(16)}")
                responseBuilder.errorResponse(cid, 0x3E.toByte()) // CTAP2_ERR_OPERATION_DENIED
            }
        }

        sendPackets(responsePackets)
    }

    // ── CTAPHID_PING ──────────────────────────────────────────────────────────

    private fun handlePing(message: CtapHidMessage) {
        // Echo back the same data on PING
        val pongMsg = CtapHidMessage(message.channelId, CTAPHID_PING, message.payload)
        sendPackets(hidReportParser.encodeResponse(pongMsg))
    }

    // ── CTAPHID_CANCEL ────────────────────────────────────────────────────────

    private fun handleCancel(message: CtapHidMessage) {
        Log.d(TAG, "CTAPHID_CANCEL on CID=${message.channelId.toHex()}")
        // Acknowledge cancel — no response payload per spec
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
    val connectionState: StateFlow<HidConnectionState> = hidWrapper.connectionState
}

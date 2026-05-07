package com.chimali.fido2.data.transport

import co.touchlab.kermit.Logger
import com.chimali.core.common.result.exceptionOrNull
import com.chimali.core.common.result.getOrNull
import com.chimali.core.common.result.getOrThrow
import com.chimali.core.common.result.isFailure
import com.chimali.core.common.result.onFailure
import com.chimali.core.events.Fido2Event
import com.chimali.core.events.Fido2EventBus
import com.chimali.fido2.bluetooth.BROADCAST_CID
import com.chimali.fido2.bluetooth.BluetoothHidConfigProvider
import com.chimali.fido2.bluetooth.BluetoothHidDeviceWrapper
import com.chimali.fido2.bluetooth.CTAPHID_CANCEL
import com.chimali.fido2.bluetooth.CTAPHID_CBOR
import com.chimali.fido2.bluetooth.CTAPHID_INIT
import com.chimali.fido2.bluetooth.CTAPHID_MSG
import com.chimali.fido2.bluetooth.CTAPHID_PING
import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.bluetooth.HidConnectionState
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.ctap2.Ctap2GetAssertionHandler
import com.chimali.fido2.ctap2.Ctap2MakeCredentialHandler
import com.chimali.fido2.ctap2.Ctap2ResponseBuilder
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.domain.coordinator.PairedDeviceEventCoordinator
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.service.Fido2Authenticator
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.util.performance.LatencyProfiler
import com.chimali.fido2.util.performance.WarmUpHelper
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

/**
 * Central FIDO2 HID transport layer (T053 + T054).
 *
 * Implements [Fido2Transport] as the top-level orchestrator that:
 * 1. initializes and registers the [BluetoothHidDeviceWrapper] (peripheral role)
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
@Single
class BluetoothHidTransportImpl(
    private val hidWrapper: BluetoothHidDeviceWrapper,
    private val hidReportParser: HidReportParser,
    private val makeCredentialHandler: Ctap2MakeCredentialHandler,
    private val getAssertionHandler: Ctap2GetAssertionHandler,
    private val responseBuilder: Ctap2ResponseBuilder,
    private val fido2Authenticator: Fido2Authenticator,
    private val fido2EventBus: Fido2EventBus,
    // Injected to trigger background event observation
    @Suppress("unused") private val pairedDeviceEventCoordinator: PairedDeviceEventCoordinator,
    private val userVerificationService: UserVerificationService,
    private val cryptoService: Fido2CryptoService,
) : Fido2Transport {
    companion object {
        // CTAPHID error codes (§8.4)
        private const val ERR_INVALID_CMD: Byte = 0x01
        private const val ERR_INVALID_LEN: Byte = 0x03
        private const val ERR_INVALID_SEQ: Byte = 0x04
        private const val ERR_INVALID_CHANNEL: Byte = 0x0B

        // CTAP2 Command Codes
        private const val CMD_MAKE_CREDENTIAL = 0x01
        private const val CMD_GET_ASSERTION = 0x02
        private const val CMD_GET_INFO = 0x04

        // U2F/APDU Constants
        private const val INS_REGISTER = 0x01
        private const val INS_AUTHENTICATE = 0x02
        private const val INS_VERSION = 0x03
        private const val INS_CTAP2_OVER_MSG = 0x10
        private const val SW_SUCCESS_1 = 0x90.toByte()
        private const val SW_SUCCESS_2 = 0x00.toByte()
        private const val SW_CONDITIONS_NOT_SATISFIED_1 = 0x69.toByte()
        private const val SW_CONDITIONS_NOT_SATISFIED_2 = 0x85.toByte()
        private const val SW_WRONG_DATA_1 = 0x6A.toByte()
        private const val SW_WRONG_DATA_2 = 0x80.toByte()
        private const val SW_UNKNOWN_1 = 0x6F.toByte()
        private const val SW_UNKNOWN_2 = 0x00.toByte()
        private const val SW_INS_NOT_SUPPORTED_1 = 0x6D.toByte()
        private const val SW_INS_NOT_SUPPORTED_2 = 0x00.toByte()

        private const val APDU_MIN_SIZE = 4
        private const val APDU_LC_SHORT_OFFSET = 4
        private const val APDU_LC_EXTENDED_OFFSET = 5
        private const val APDU_LC_EXTENDED_MIN_SIZE = 7

        /**
         * Inter-report pacing delay in milliseconds.
         *
         * After each HID report is dispatched to [BluetoothHidDeviceWrapper.sendReport],
         * the sender coroutine sleeps for this duration to let the Bluetooth HCI layer
         * process the outbound HCI command before the next packet is queued.
         *
         * Actual value read from [BluetoothHidConfigProvider.config] at runtime.
         */
        private val REPORT_PACE_DELAY get() = BluetoothHidConfigProvider.config.reportPaceDelay
        private val KEEPALIVE_INITIAL_DELAY get() = BluetoothHidConfigProvider.config.keepaliveInitialDelay
        private val KEEPALIVE_PERIOD get() = BluetoothHidConfigProvider.config.keepalivePeriod

        // Protocol
        private const val NONCE_SIZE = 8
        private const val CID_SIZE = 4
        private const val STATUS_PROCESSING: Byte = 0x01
        private const val STATUS_UPNEEDED: Byte = 0x02

        private const val HEX_RADIX = 16
        private const val APDU_DATA_OFFSET_SHORT = 5

        private const val BYTE_MASK = 0xFF
        private const val SHIFT_8 = 8
    }

    private val secureRandom = SecureRandom()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Set of currently allocated channel IDs (hex string → CID bytes). */
    private val channelRegistry = mutableMapOf<String, ByteArray>()

    private var receiveJob: Job? = null
    private var stateObserverJob: Job? = null

    /**
     * Guard flag for zombie-kill auto-reregistration.
     *
     * On Motorola, the BT daemon keeps the previous session's HID registration alive for
     * ~66 s after the process dies. When the zombie is finally cleaned up, the daemon fires
     * [BluetoothHidDevice.Callback.onAppStatusChanged] with `registered=false`, driving
     * [HidConnectionState.Idle] while the transport expects to be advertising.
     *
     * The [observeConnectionState] handler detects this unexpected Idle and re-invokes
     * [BluetoothHidDeviceWrapper.registerApp]. The zombie is dead at that point so
     * [android.bluetooth.BluetoothHidDevice.registerApp] returns `true` and properly
     * binds the callback — new connections from the already-bonded host then succeed.
     *
     * This flag prevents a concurrent second re-registration if the prelude
     * [android.bluetooth.BluetoothHidDevice.unregisterApp] inside [BluetoothHidDeviceWrapper.registerApp]
     * triggers another transient [HidConnectionState.Idle] emission.
     *
     * T053a — Thread-safe FIFO queue for outgoing HID reports (Constitution §IV).
     *
     * All outgoing 64-byte HID packets are enqueued here and drained by a single
     * sender coroutine ([sendQueueJob]). This prevents packet interleaving between
     * concurrent coroutines (e.g., keepalive loop + CBOR response), which would
     * corrupt the framing of multi-packet CTAPHID messages on the host side.
     *
     * Capacity: 256 packets (~16 KB at 64 B/packet). Overflow: SUSPEND (backpressure).
     */
    private val sendQueue = Channel<ByteArray>(capacity = 256)
    private var sendQueueJob: Job? = null

    // ── Fido2Transport interface ───────────────────────────────────────────────

    override suspend fun connect(): Result<Unit> =
        try {
            Logger.d { "connect() starting..." }
            hidWrapper.initialize().getOrThrow()
            Logger.d { "hidWrapper initialized, now registering app..." }
            hidWrapper.registerApp().getOrThrow()
            Logger.d { "hidWrapper app registered, starting receiver, sender and observer..." }
            startSendQueue() // T053a: start FIFO sender before receiving
            startReceiving()
            observeConnectionState()
            Logger.i("BluetoothHidTransport connected and advertising")
            Result.success(Unit)
        } catch (e: Fido2Exception) {
            Logger.e { "connect() failed with Fido2Exception: ${e.message}" }
            hidWrapper.reportError(e.message ?: "HID connection failed")
            Result.failure(e)
        } catch (e: IllegalStateException) {
            Logger.e(e) { "connect() unexpected failure: ${e.message}" }
            val msg = "Failed to start HID transport: ${e.message}"
            hidWrapper.reportError(msg)
            Result.failure(Fido2Exception.TransportException(msg))
        }

    override suspend fun disconnect(): Result<Unit> =
        try {
            Logger.d { "disconnect() starting..." }
            receiveJob?.cancel()
            receiveJob = null
            // Use cancelAndJoin (not just cancel) so the observer coroutine is guaranteed dead
            // before unregisterApp() fires registered=false →  Idle. Without this, the
            // observer's Idle handler could race with the intentional disconnect and trigger
            // an unwanted auto re-registration.
            stateObserverJob?.cancelAndJoin()
            stateObserverJob = null
            sendQueueJob?.cancel() // T053a: stop FIFO sender
            sendQueueJob = null
            hidWrapper.unregisterApp()
            channelRegistry.clear()
            hidReportParser.reset()
            Logger.i { "BluetoothHidTransport disconnected" }
            Result.success(Unit)
        } catch (e: IllegalStateException) {
            Logger.e(e) { "disconnect() failed: ${e.message}" }
            Result.failure(Fido2Exception.TransportException("Disconnect error: ${e.message}"))
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
        stateObserverJob?.cancel()
        stateObserverJob =
            hidWrapper.connectionState
                .onEach { state ->
                    when (state) {
                        is HidConnectionState.Connected -> {
                            Logger.i { "Host connected: ${state.device.address}" }
                            // NFR-PERF-030: Pre-warm latency-sensitive subsystems so the first
                            // real GetAssertion ceremony doesn't pay cold-start costs.
                            //
                            // The host still has to complete CTAPHID_INIT channel negotiation +
                            // the Windows pre-flight GetAssertion checks before issuing the real
                            // ceremony, giving this coroutine a realistic head start.
                            //
                            // All results are discarded — only the side-effects (cache population
                            // and TEE channel initialization) matter. Failures are non-fatal.
                            scope.launch(Dispatchers.IO) {
                                // (1) BiometricManager availability cache — eliminates 50–150ms
                                //     Binder IPC into Android SystemServer on first UV check.
                                runCatching { userVerificationService.getUserVerificationAvailability() }
                                    .onFailure { e ->
                                        Logger.w { "BiometricManager pre-warm failed (non-fatal): ${e.message}" }
                                    }

                                // (2) AndroidKeyStore TEE/HAL IPC channel — eliminates the 200ms+
                                //     HAL init spike on the first Crypto.sign() call.
                                //     Fido2Initializer also calls this at app start, but the first
                                //     CTAP2 message can arrive before that warmup finishes on a
                                //     parallel thread. Repeating it here at connect-time is safe
                                //     (the key already exists; it's just a 5ms lookup + sign).
                                WarmUpHelper.warmUpAndroidKeyStore()

                                // (3) Master seed (EncryptedSharedPreferences) — the dominant
                                //     cold-start cost in Crypto.sign(). On the first call per session,
                                //     getMasterSeed() decrypts the BIP39 mnemonic using the
                                //     'androidx_security_master_key_v2' AndroidKeyStore key. That
                                //     specific key has its own lazy-init cost (~150ms) separate from
                                //     the generic warmup key exercised by warmUpAndroidKeyStore().
                                //     After this call, WalletMasterSeedProvider caches the seed in
                                //     memory, so all subsequent getMasterSeed() calls are ~0ms.
                                cryptoService.warmUpMasterSeed()
                            }
                            // Transport is now ready for CTAPHID_INIT from the host
                        }
                        is HidConnectionState.Advertising -> {
                            Logger.d { "Advertising for host connections" }
                            // Clean up any channels from prior host session
                            channelRegistry.clear()
                            hidReportParser.reset()
                        }
                        is HidConnectionState.Idle -> {
                            Logger.d { "HID transport idle" }
                        }
                        is HidConnectionState.Error -> {
                            Logger.e { "HID connection error: ${state.message}" }
                        }
                        else -> { /* Connecting — nothing to do */ }
                    }
                }.launchIn(scope)
    }

    // ── Report receiver loop ──────────────────────────────────────────────────

    private fun startReceiving() {
        receiveJob?.cancel()
        receiveJob =
            scope.launch {
                for (report in hidWrapper.incomingReports) {
                    processReport(report)
                }
            }
    }

    private suspend fun processReport(report: ByteArray) {
        val result = hidReportParser.processReport(report)
        if (result.isFailure) {
            Logger.e { "Report parse error: ${result.exceptionOrNull()?.message}" }
            // Can't identify CID from a broken packet; send broadcast error
            sendPackets(responseBuilder.hidErrorResponse(BROADCAST_CID, ERR_INVALID_SEQ))
            return
        }

        val message = result.getOrNull() ?: return // null = still accumulating

        Logger.d {
            "CTAPHID cmd=0x${message.command.toString(HEX_RADIX).uppercase()} " +
                "cid=${message.channelId.toHex()} payloadLen=${message.payload.size}"
        }

        dispatchMessage(message)
    }

    // ── Command dispatcher ────────────────────────────────────────────────────

    private suspend fun dispatchMessage(message: CtapHidMessage) {
        val cid = message.channelId
        when (message.command) {
            CTAPHID_INIT -> handleInit(message)
            CTAPHID_CBOR -> handleCbor(message)
            CTAPHID_MSG -> handleMsg(message)
            CTAPHID_PING -> handlePing(message)
            CTAPHID_CANCEL -> handleCancel(message)
            else -> {
                Logger.w { "Unknown CTAPHID command 0x${message.command.toString(HEX_RADIX)}" }
                sendPackets(responseBuilder.hidErrorResponse(cid, ERR_INVALID_CMD))
            }
        }
    }

    // ── CTAPHID_INIT ──────────────────────────────────────────────────────────

    private fun handleInit(message: CtapHidMessage) {
        val nonce = message.payload.takeIf { it.size >= NONCE_SIZE }?.copyOfRange(0, NONCE_SIZE)
        if (nonce == null) {
            sendPackets(responseBuilder.hidErrorResponse(BROADCAST_CID, ERR_INVALID_LEN))
            return
        }

        // Assign a new CID for this session
        val newCid = generateCid()
        channelRegistry[newCid.toHex()] = newCid
        Logger.i { "CTAPHID_INIT: assigned CID=${newCid.toHex()}" }

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

        val ctapCommand = payload[0].toInt() and BYTE_MASK
        val operationLabel =
            when (ctapCommand) {
                CMD_MAKE_CREDENTIAL -> "MakeCredential"
                CMD_GET_ASSERTION -> "GetAssertion"
                CMD_GET_INFO -> "GetInfo"
                else -> "CTAP2_0x${ctapCommand.toString(HEX_RADIX)}"
            }
        // NFR-PERF-030: Start measuring full CTAP2 processing time
        LatencyProfiler.start(operationLabel)
        Logger.d {
            "CTAP2 command=0x${ctapCommand.toString(HEX_RADIX)} on CID=${cid.toHex()}"
        }

        // ── Periodic keepalive loop ────────────────────────────────────────────
        // CTAP HID spec §8.5.5: the authenticator MUST send CTAPHID_KEEPALIVE
        // continuously at a period ≤ 500ms while processing a CBOR command.
        //
        // IMPORTANT: the initial delay must come BEFORE the first keepalive send.
        // Fast commands (GetInfo) complete in <10ms; an immediate keepalive would
        // arrive at Windows BEFORE the real response, causing ERROR_INVALID_DATA.
        // The rauth-android reference always sleeps first, then sends.
        val keepaliveJob: Job =
            scope.launch(Dispatchers.IO) {
                delay(KEEPALIVE_INITIAL_DELAY) // wait first; spec requires first ~100ms. Reference uses 75ms.
                sendPackets(responseBuilder.keepAliveResponse(cid, STATUS_PROCESSING))
                while (true) {
                    delay(KEEPALIVE_PERIOD) // 75ms between subsequent keepalives.
                    sendPackets(responseBuilder.keepAliveResponse(cid, STATUS_UPNEEDED))
                }
            }

        val responsePackets =
            try {
                when (ctapCommand) {
                    CMD_MAKE_CREDENTIAL -> {
                        // authenticatorMakeCredential — CTAP2 registration
                        val resp = makeCredentialHandler.handle(message)
                        publishSuccessEvent()
                        resp
                    }

                    CMD_GET_ASSERTION -> {
                        // authenticatorGetAssertion — CTAP2 authentication
                        val resp = handleGetAssertion(message)
                        publishSuccessEvent()
                        resp
                    }

                    CMD_GET_INFO -> handleGetInfo(cid) // authenticatorGetInfo

                    else -> {
                        Logger.w { "Unsupported CTAP2 command 0x${ctapCommand.toString(HEX_RADIX)}" }
                        responseBuilder.errorResponse(cid, ERR_INVALID_CMD)
                    }
                }
            } finally {
                // cancelAndJoin() (not just cancel()) ensures the keepalive coroutine
                // has completely stopped before we send the real response. Without this,
                // a keepalive in-flight could arrive at Windows AFTER the CBOR response,
                // corrupting the framing of the next request.
                keepaliveJob.cancelAndJoin()
                // NFR-PERF-030: Record processing time before transmitting response
                LatencyProfiler.end(operationLabel)
            }

        sendPackets(responsePackets)
    }

    private fun publishSuccessEvent() {
        val state = hidWrapper.connectionState.value
        if (state is HidConnectionState.Connected) {
            val mac = state.device.address
            val name =
                try {
                    state.device.name
                } catch (e: SecurityException) {
                    Logger.w(e) { "BluetoothHidTransport: Failed to get device name" }
                    null
                }

            // Requires API 31+ or suppression for BLUETOOTH_CONNECT, but we already have permission
            // The property is `bluetoothClass.deviceClass` which returns the Int representing the major/minor class
            val devClass =
                try {
                    state.device.bluetoothClass?.deviceClass
                } catch (e: SecurityException) {
                    Logger.w(e) { "BluetoothHidTransport: Failed to get bluetooth class" }
                    null
                }

            Logger.d { "FIDO2 Operation succeeded for host: $name ($mac), Class: $devClass" }
            fido2EventBus.publish(
                Fido2Event.InteractionSuccessful(
                    hostDeviceAddress = mac,
                    hostDeviceName = name,
                    hostDeviceClass = devClass,
                ),
            )
        }
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
        val cid = message.channelId
        val payload = message.payload
        Logger.d { "CTAPHID_MSG len=${payload.size} cid=${cid.toHex()}" }

        if (payload.size < APDU_MIN_SIZE) {
            sendPackets(responseBuilder.hidErrorResponse(cid, ERR_INVALID_LEN))
            return
        }

        val ins = payload[1].toInt() and BYTE_MASK

        // CTAP2-over-MSG: INS = 0x10, data is CBOR payload
        if (ins == INS_CTAP2_OVER_MSG) {
            val cborData = extractApduData(payload)
            if (cborData == null || cborData.isEmpty()) {
                Logger.w { "CTAPHID_MSG INS=0x10 but APDU data is empty" }
                sendPackets(u2fErrorResponse(cid, SW_UNKNOWN_1.toInt(), SW_UNKNOWN_2.toInt()))
                return
            }
            // Synthesise a CTAPHID_CBOR message with the unwrapped CBOR payload
            Logger.d {
                "CTAPHID_MSG routing CTAP2 cmd=0x${(cborData[0].toInt() and BYTE_MASK).toString(HEX_RADIX)} as CBOR"
            }
            val syntheticMsg = CtapHidMessage(cid, CTAPHID_CBOR, cborData)
            handleCbor(syntheticMsg)
            return
        }

        // Pure U2F commands (Register=0x01, Authenticate=0x02, Version=0x03)
        when (ins) {
            INS_VERSION -> {
                // U2F_VERSION — respond "U2F_V2" so the host knows we speak the FIDO protocol
                val u2fVersion = "U2F_V2".toByteArray(Charsets.US_ASCII)
                sendPackets(u2fSuccessResponse(cid, u2fVersion))
            }
            INS_REGISTER -> {
                // U2F_REGISTER — return SW_CONDITIONS_NOT_SATISFIED (0x6985).
                //
                // We are a CTAP2-only authenticator; we do not implement the legacy U2F
                // registration path. 0x6985 ("conditions of use not satisfied") is the
                // correct refusal code per the U2F spec §5.2.7. Windows treats this as:
                // "user-presence test failed on the U2F path" and escalates to sending
                // authenticatorMakeCredential over CTAPHID_CBOR (0x10) instead.
                //
                // DO NOT send a fake/dummy registration response here. If we do, Windows
                // stores a credential with an ephemeral key we don't hold, then immediately
                // tries U2F_AUTHENTICATE (which fails with SW_WRONG_DATA), and concludes
                // the device is broken — resulting in ERROR_NOT_READY (0x80070018).
                Logger.i {
                    "CTAPHID_MSG U2F_REGISTER → returning SW_CONDITIONS_NOT_SATISFIED (0x6985) to escalate to CTAP2"
                }
                sendPackets(
                    u2fErrorResponse(cid, SW_CONDITIONS_NOT_SATISFIED_1.toInt(), SW_CONDITIONS_NOT_SATISFIED_2.toInt()),
                )
            }
            INS_AUTHENTICATE -> {
                // U2F_AUTHENTICATE — return SW_WRONG_DATA (0x6A80) to signal that we don't
                // recognise this U2F key handle. Per the U2F spec, this tells the platform
                // "credential not found here" and causes Windows to fall back to CTAP2 GetAssertion.
                Logger.d { "CTAPHID_MSG U2F_AUTHENTICATE → SW_WRONG_DATA (triggers CTAP2 GetAssertion)" }
                sendPackets(u2fErrorResponse(cid, SW_WRONG_DATA_1.toInt(), SW_WRONG_DATA_2.toInt()))
            }
            else -> {
                Logger.d { "CTAPHID_MSG U2F INS=0x${ins.toString(HEX_RADIX)} unknown — returning SW_INS_NOT_SUPPORTED" }
                sendPackets(u2fErrorResponse(cid, SW_INS_NOT_SUPPORTED_1.toInt(), SW_INS_NOT_SUPPORTED_2.toInt()))
            }
        }
    }

    /** Extract data bytes from an ISO 7816-4 APDU (handles extended and short Lc). */
    private fun extractApduData(apdu: ByteArray): ByteArray? {
        if (apdu.size < APDU_MIN_SIZE) return null
        if (apdu.size == APDU_MIN_SIZE) return ByteArray(0) // no body

        return if (apdu[APDU_LC_SHORT_OFFSET] != 0x00.toByte()) { // short Lc
            val lc = apdu[APDU_LC_SHORT_OFFSET].toInt() and BYTE_MASK
            if (apdu.size >= APDU_DATA_OFFSET_SHORT + lc) {
                apdu.copyOfRange(APDU_DATA_OFFSET_SHORT, APDU_DATA_OFFSET_SHORT + lc)
            } else {
                null
            }
        } else { // extended Lc
            if (apdu.size < APDU_LC_EXTENDED_MIN_SIZE) return null
            val lc = (
                ((apdu[APDU_LC_EXTENDED_OFFSET].toInt() and BYTE_MASK) shl SHIFT_8) or
                    (apdu[APDU_LC_EXTENDED_OFFSET + 1].toInt() and BYTE_MASK)
            )
            val endOffset = APDU_LC_EXTENDED_MIN_SIZE + lc
            if (apdu.size >= endOffset) {
                apdu.copyOfRange(APDU_LC_EXTENDED_MIN_SIZE, endOffset)
            } else {
                Logger.e { "BluetoothHidTransport: APDU data extraction failed - buffer too small for lc=$lc" }
                null
            }
        }
    }

    /** Build a U2F success APDU response: data + SW1=0x90 SW2=0x00 */
    private fun u2fSuccessResponse(
        cid: ByteArray,
        data: ByteArray,
    ): List<ByteArray> {
        val resp = data + byteArrayOf(SW_SUCCESS_1, SW_SUCCESS_2)
        val msg = CtapHidMessage(cid, CTAPHID_MSG, resp)
        return hidReportParser.encodeResponse(msg)
    }

    /** Build a U2F error APDU response: SW1 + SW2 only */
    private fun u2fErrorResponse(
        cid: ByteArray,
        sw1: Int,
        sw2: Int,
    ): List<ByteArray> {
        val resp = byteArrayOf(sw1.toByte(), sw2.toByte())
        val msg = CtapHidMessage(cid, CTAPHID_MSG, resp)
        return hidReportParser.encodeResponse(msg)
    }

    private fun handleCancel(message: CtapHidMessage) {
        Logger.d { "CTAPHID_CANCEL on CID=${message.channelId.toHex()}" }
        // Acknowledge cancel — no response payload per spec
    }

    // ── authenticatorGetAssertion ─────────────────────────────────────────────

    private suspend fun handleGetAssertion(message: CtapHidMessage): List<ByteArray> {
        val cid = message.channelId
        val requestBytes = message.payload.drop(1).toByteArray() // strip the 0x02 command byte
        return getAssertionHandler.handle(cid, requestBytes)
    }

    // ── authenticatorGetInfo ──────────────────────────────────────────────────

    private suspend fun handleGetInfo(cid: ByteArray): List<ByteArray> =
        try {
            val info = fido2Authenticator.getAuthenticatorInfo()
            val packets = responseBuilder.getInfoResponse(cid, info)
            // Debug: log the first packet hex so we can diagnose Windows rejection
            if (packets.isNotEmpty()) {
                val hex = packets.first().joinToString("") { packetByte -> "%02x".format(packetByte) }
                Logger.d { "GetInfo response packet[0] hex: $hex" }
            }
            packets
        } catch (e: Fido2Exception) {
            Logger.e(e) { "GetInfo failed: ${e.message}" }
            responseBuilder.errorResponse(cid, 0x30.toByte())
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * T053a — Enqueues all [packets] into the thread-safe FIFO send queue.
     *
     * The [sendQueueJob] coroutine drains the queue sequentially, ensuring packets
     * from concurrent callers (keepalive loop, CBOR response, ping) never interleave.
     * [trySendBlocking] is used for non-suspend contexts; the Channel capacity (256)
     * ensures this blocks only under extremely high load, not in normal operation.
     */
    private fun sendPackets(packets: List<ByteArray>) {
        for (packet in packets) {
            val result = sendQueue.trySendBlocking(packet)
            if (result.isFailure) {
                Logger.w { "Send queue full — dropping HID packet (queue capacity exceeded)" }
            }
        }
    }

    /**
     * T053a — Starts the FIFO sender coroutine that drains [sendQueue] serially.
     *
     * A single coroutine calls [BluetoothHidDeviceWrapper.sendReport] one packet at
     * a time, preventing any concurrent access to the underlying HID driver.
     *
     * After each report, the coroutine sleeps for [REPORT_PACE_DELAY_MS] to allow
     * the Bluetooth HCI layer to process the outbound command. See [REPORT_PACE_DELAY_MS]
     * KDoc for rationale.
     */
    private fun startSendQueue() {
        sendQueueJob?.cancel()
        sendQueueJob =
            scope.launch(Dispatchers.IO) {
                for (packet in sendQueue) {
                    if (!hidWrapper.sendReport(packet)) {
                        Logger.w { "sendReport returned false — host may have disconnected" }
                    }
                    delay(REPORT_PACE_DELAY)
                }
            }
    }

    private fun generateCid(): ByteArray {
        val cid = ByteArray(CID_SIZE)
        do {
            secureRandom.nextBytes(cid)
            // Avoid re-using BROADCAST_CID or already-allocated CIDs
        } while (cid.contentEquals(BROADCAST_CID) || channelRegistry.containsKey(cid.toHex()))
        return cid
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /** Expose connection state for observing by the presentation layer. */
    override val connectionState: StateFlow<HidConnectionState> = hidWrapper.connectionState
}

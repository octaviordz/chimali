package com.chimali.fido2.data.transport

import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.exceptionOrNull
import com.chimali.core.common.result.getOrNull
import com.chimali.core.common.result.getOrThrow
import com.chimali.core.common.result.isFailure
import com.chimali.core.common.result.onFailure
import com.chimali.core.events.Fido2Event
import com.chimali.core.events.Fido2EventBus
import com.chimali.core.security.api.MasterSeedOwnerKind
import com.chimali.core.security.api.MasterSeedProvider
import com.chimali.fido2.bluetooth.BROADCAST_CID
import com.chimali.fido2.bluetooth.BluetoothHidConfigProvider
import com.chimali.fido2.bluetooth.BluetoothHidDeviceWrapper
import com.chimali.fido2.bluetooth.CtapHidMessage
import com.chimali.fido2.bluetooth.HidConnectionState
import com.chimali.fido2.bluetooth.HidReportParser
import com.chimali.fido2.ctap2.Ctap2GetAssertionHandler
import com.chimali.fido2.ctap2.Ctap2MakeCredentialHandler
import com.chimali.fido2.ctap2.Ctap2ResponseBuilder
import com.chimali.fido2.domain.coordinator.PairedDeviceEventCoordinator
import com.chimali.fido2.domain.exception.Fido2Exception
import com.chimali.fido2.domain.service.AuthenticatorInfoProvider
import com.chimali.fido2.domain.service.UserVerificationService
import com.chimali.fido2.util.performance.BaselineMeasurements
import com.chimali.fido2.util.performance.LatencyProfiler
import com.chimali.fido2.util.performance.PerformanceTrace
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single

/**
 * Central FIDO2 HID transport layer (T053 + T054).
 *
 * Implements [Fido2Transport] as the top-level orchestrator that:
 * 1. initializes and registers the [BluetoothHidDeviceWrapper] (peripheral role)
 * 2. Consumes raw 62-byte HID reports from [BluetoothHidDeviceWrapper.incomingReports]
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
    private val authenticatorInfoProvider: AuthenticatorInfoProvider,
    private val fido2EventBus: Fido2EventBus,
    // Injected to trigger background event observation
    @Suppress("unused") private val pairedDeviceEventCoordinator: PairedDeviceEventCoordinator,
    private val userVerificationService: UserVerificationService,
    private val masterSeedProvider: MasterSeedProvider,
) : Fido2Transport {
    companion object {
        // CTAPHID error codes (§8.4)
        private const val ERR_INVALID_CMD: Byte = 0x01
        private const val ERR_INVALID_SEQ: Byte = 0x04
        private const val ERR_CHANNEL_BUSY: Byte = 0x06
        private const val ERR_INVALID_CHANNEL: Byte = 0x0B

        // CTAP2 Command Codes
        private const val CMD_MAKE_CREDENTIAL = 0x01
        private const val CMD_GET_ASSERTION = 0x02
        private const val CMD_GET_INFO = 0x04
        private const val CTAP2_ERR_USER_ACTION_TIMEOUT: Byte = 0x2F

        /** Default maximum ceremony duration in milliseconds, including user interaction. */
        const val DEFAULT_CEREMONY_TIMEOUT_MILLIS = 120_000L

        private const val NANOS_PER_MILLISECOND = 1_000_000L

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
        private const val STATUS_PROCESSING: Byte = 0x01
        private const val STATUS_UPNEEDED: Byte = 0x02

        private const val HEX_RADIX = 16
        private const val BYTE_MASK = 0xFF
        private const val REASSEMBLY_EXPIRY_POLL_MILLIS = 500L

        /** Maximum channel identifiers owned by one transport session. */
        internal const val MAX_SESSION_CIDS = 16

        /** Maximum attempts to find an unused channel identifier before failing allocation. */
        internal const val MAX_CID_ALLOCATION_ATTEMPTS = 32
    }

    private val secureRandom = SecureRandom()

    /**
     * Fills the allocator's reused 4-byte CID buffer synchronously for each allocation attempt.
     * Do not retain or mutate it after the callback; a successful allocation transfers it to the registry.
     */
    internal var nextCidBytes: (ByteArray) -> Unit = secureRandom::nextBytes
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Host scheduling seam; defaults preserve the production IO worker and monotonic clock. */
    internal var workerDispatcher: CoroutineDispatcher = Dispatchers.IO

    /** Host regression seam for advancing response consumption independently from request work. */
    internal var sendQueueDispatcher: CoroutineDispatcher? = null

    /** Monotonic nanosecond clock used for elapsed transport timing; values are not wall-clock timestamps. */
    internal var monotonicNanos: () -> Long = System::nanoTime

    /** Host regression seam; production keeps the CTAP safe default. */
    internal var ceremonyTimeoutMillis = DEFAULT_CEREMONY_TIMEOUT_MILLIS

    /** Retains the actual admitted request owner for host terminal-zeroing assertions. */
    @Req("FR-HID-010", "FR-VAL-020")
    internal var onCborRequestPayloadForTest: ((ByteArray) -> Unit)? = null

    /** Synchronous test observer before completed-message dispatch; production keeps this unset. */
    @Req("FR-HID-010", "FR-VAL-020")
    internal var onCompletedMessageForTest: ((CtapHidMessage) -> Unit)? = null

    /** Serializes parser/CID ownership with synchronous wrapper session transitions. */
    private val transportSessionLock = Any()
    private var observedSessionGeneration = Long.MIN_VALUE
    private var observedConnectionState: HidConnectionState? = null

    /** Set of currently allocated channel IDs, each bound to its host-session generation. */
    private val channelRegistry = HidChannelRegistry()

    private var receiveJob: Job? = null
    private var stateObserverJob: Job? = null

    /** Owns admitted CTAPHID requests and bounds concurrent ceremony work per host generation. */
    private val cborRequestOwner =
        CtapHidRequestOwner(
            scope = scope,
            dispatcher = { workerDispatcher },
            monotonicNanos = { monotonicNanos() },
            isSessionCurrent = ::isCurrentSession,
            handleRequest = ::handleCbor,
            reportChannelBusy = { cid, generation ->
                sendPackets(responseBuilder.hidErrorResponse(cid, ERR_CHANNEL_BUSY), generation)
            },
            onPayloadForTest = { payload -> onCborRequestPayloadForTest?.invoke(payload) },
        )
    private val u2fApduCommandHandler =
        U2fApduCommandHandler(
            hidReportParser = hidReportParser,
            responseBuilder = responseBuilder,
            enqueuePackets = ::sendPackets,
            launchCborOperation = { message, generation -> cborRequestOwner.dispatch(message, generation) },
        )
    private val commandDispatcher =
        CtapHidCommandDispatcher(
            hidReportParser = hidReportParser,
            responseBuilder = responseBuilder,
            getAssertionHandler = getAssertionHandler,
            authenticatorInfoProvider = authenticatorInfoProvider,
            cborRequestOwner = cborRequestOwner,
            u2fApduCommandHandler = u2fApduCommandHandler,
            isCurrentSession = ::isCurrentSession,
            isChannelRegistered = ::isChannelRegistered,
            allocateCid = ::generateCid,
            sendPackets = ::sendPackets,
        )

    /** Serializes outgoing reports in a bounded queue and disposes pending packets at generation changes. */
    private val sendQueueSender =
        HidReportSender(
            scope = scope,
            dispatcher = { sendQueueDispatcher ?: workerDispatcher },
            sessionGeneration = hidWrapper::sessionGeneration,
            sendReport = { bytes, generation ->
                PerformanceTrace.section("Fido2.HID.SendReport") {
                    hidWrapper.sendReport(bytes, generation)
                }
            },
            reportAccepted = BaselineMeasurements::reportAccepted,
            reportPaceDelayMillis = REPORT_PACE_DELAY.inWholeMilliseconds,
        )

    init {
        hidWrapper.onSessionTransition = ::onWrapperSessionTransition
        val (generation, state) = hidWrapper.sessionSnapshot()
        onWrapperSessionTransition(generation, state)
    }

    // ── Fido2Transport interface ───────────────────────────────────────────────

    /** Acquires the Android HID profile, registers it and starts session-bound transport workers. */
    @Req("FR-HID-010", "FR-VAL-020")
    override suspend fun connect(): Result<Unit> =
        try {
            Logger.d { "connect() starting..." }
            // Drop any buffered reports from the previous transport generation before the
            // wrapper can admit reports for a newly registered host session.
            hidWrapper.discardQueuedIncomingReports()
            hidWrapper.initialize().getOrThrow()
            Logger.d { "hidWrapper initialized, now registering app..." }
            hidWrapper.registerApp().getOrThrow()
            Logger.d { "hidWrapper app registered, starting receiver, sender and observer..." }
            startTransportWorkers()
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

    /** Initiates connection to the selected bonded host, starting advertising when the transport is idle. */
    @Req("FR-HID-010", "FR-VAL-020")
    override suspend fun connectDevice(macAddress: String): Result<Unit> =
        try {
            Logger.i { "connectDevice($macAddress) called" }
            if (hidWrapper.connectionState.value is HidConnectionState.Idle) {
                Logger.i { "Transport is Idle; starting transport before connecting to device $macAddress" }
                connect().getOrThrow()
            }
            val initiated = hidWrapper.connect(macAddress)
            if (initiated) {
                Result.success(Unit)
            } else {
                Result.failure(Fido2Exception.BluetoothException("Failed to initiate connection to $macAddress"))
            }
        } catch (e: Fido2Exception) {
            Logger.e(e) { "connectDevice($macAddress) failed: ${e.message}" }
            Result.failure(e)
        } catch (e: IllegalStateException) {
            Logger.e(e) { "connectDevice($macAddress) failed: ${e.message}" }
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Logger.e(e) { "connectDevice($macAddress) failed: ${e.message}" }
            Result.failure(e)
        }

    /** Stops request and response workers and erases parser, queue and channel state for this session. */
    @Req("FR-HID-010", "FR-VAL-020")
    override suspend fun disconnect(): Result<Unit> =
        try {
            Logger.d { "disconnect() starting..." }
            receiveJob?.cancelAndJoin()
            receiveJob = null
            cborRequestOwner.cancelAll()
            // Join the observer before unregistering so the intentional state transition cannot
            // race with observer work while the session and transport workers are being torn down.
            stateObserverJob?.cancelAndJoin()
            stateObserverJob = null
            sendQueueSender.stop()
            BaselineMeasurements.disconnect()
            hidWrapper.unregisterApp()
            hidWrapper.discardQueuedIncomingReports()
            clearChannelRegistry()
            hidReportParser.reset()
            Logger.i { "BluetoothHidTransport disconnected" }
            Result.success(Unit)
        } catch (e: IllegalStateException) {
            Logger.e(e) { "disconnect() failed: ${e.message}" }
            Result.failure(Fido2Exception.TransportException("Disconnect error: ${e.message}"))
        }

    // ── Connection state observation (T054) ───────────────────────────────────

    /** Applies parser, CID, request and sender ownership changes synchronously with wrapper callbacks. */
    @Req("FR-HID-010", "FR-VAL-020")
    private fun onWrapperSessionTransition(
        generation: Long,
        state: HidConnectionState,
    ) {
        var endedGeneration: Long? = null
        synchronized(transportSessionLock) {
            if (generation < observedSessionGeneration) return
            if (generation == observedSessionGeneration && state == observedConnectionState) return

            if (observedSessionGeneration != Long.MIN_VALUE && observedSessionGeneration != generation) {
                endedGeneration = observedSessionGeneration
            }
            observedSessionGeneration = generation
            observedConnectionState = state
            hidReportParser.reset()
            clearChannelRegistry()

            if (state is HidConnectionState.Connected) {
                sendQueueSender.start(generation)
            } else {
                sendQueueSender.stop(endedGeneration ?: generation)
            }
        }
        endedGeneration?.let(cborRequestOwner::cancelGeneration)
    }

    private fun isCurrentSession(generation: Long): Boolean =
        synchronized(transportSessionLock) {
            isCurrentSessionLocked(generation)
        }

    private fun isCurrentSessionLocked(generation: Long): Boolean =
        generation == observedSessionGeneration && generation == hidWrapper.sessionGeneration()

    /** Checks that a non-INIT command belongs to a CID allocated in the active host generation. */
    @Req("FR-HID-010", "FR-VAL-020")
    private fun isChannelRegistered(
        cid: ByteArray,
        generation: Long,
    ): Boolean = channelRegistry.isRegistered(cid, generation)

    private fun observeConnectionState() {
        stateObserverJob?.cancel()
        stateObserverJob =
            hidWrapper.connectionState
                .onEach { state ->
                    when (state) {
                        is HidConnectionState.Connected -> {
                            Logger.i { "Host connected: ${state.device.address}" }
                            // Best-effort user-verification availability cache warm-up. This is
                            // asynchronous and does not guarantee the cache is populated before
                            // a ceremony begins; signing remains software-derived.
                            scope.launch(workerDispatcher) {
                                runCatching { userVerificationService.getUserVerificationAvailability() }
                                    .onFailure { e ->
                                        Logger.w { "BiometricManager pre-warm failed (non-fatal): ${e.message}" }
                                    }
                            }
                            // Transport is now ready for CTAPHID_INIT from the host
                        }
                        is HidConnectionState.Advertising -> {
                            Logger.d { "Advertising for host connections" }
                        }
                        is HidConnectionState.Idle -> {
                            Logger.d { "HID transport idle" }
                        }
                        is HidConnectionState.Error -> {
                            Logger.e { "HID connection error: ${state.message}" }
                        }
                        else -> { /* Connecting — nothing to do */ }
                    }
                }.launchIn(CoroutineScope(scope.coroutineContext + workerDispatcher))
    }

    // ── Report receiver loop ──────────────────────────────────────────────────

    /** Owns each queued report until parsing finishes and erases it even on cancellation/error. */
    @Req("FR-HID-010", "FR-VAL-020")
    private fun startReceiving() {
        receiveJob?.cancel()
        receiveJob =
            scope.launch(workerDispatcher) {
                while (true) {
                    val result =
                        withTimeoutOrNull(REASSEMBLY_EXPIRY_POLL_MILLIS) {
                            hidWrapper.incomingReports.receiveCatching()
                        }
                    if (result == null) {
                        synchronized(transportSessionLock) { hidReportParser.expirePending() }
                    } else {
                        val report = result.getOrNull() ?: break
                        try {
                            if (report.sessionGeneration == hidWrapper.sessionGeneration()) {
                                processReport(report.bytes, report.sessionGeneration)
                            }
                        } finally {
                            report.bytes.fill(0)
                        }
                    }
                }
            }
    }

    /** Starts the same receive/observer loops used after successful wrapper registration. */
    @Req("FR-HID-010", "FR-VAL-020")
    private fun startTransportWorkers() {
        startReceiving()
        observeConnectionState()
        val (generation, state) = hidWrapper.sessionSnapshot()
        onWrapperSessionTransition(generation, state)
    }

    /** Test seam for exercising real wrapper callbacks without acquiring the Android profile proxy. */
    @Req("FR-HID-010", "FR-VAL-020")
    internal fun startTransportWorkersForTest() = startTransportWorkers()

    /** Borrows one receive-loop-owned report; a completed message is transferred to dispatch. */
    @Req("FR-HID-010", "FR-VAL-020")
    private suspend fun processReport(
        report: ByteArray,
        sessionGeneration: Long,
    ) {
        val result =
            synchronized(transportSessionLock) {
                if (!isCurrentSessionLocked(sessionGeneration)) return
                hidReportParser.processReport(report)
            }
        if (result.isFailure) {
            Logger.e { "Report parse error: ${result.exceptionOrNull()?.message}" }
            // Can't identify CID from a broken packet; send broadcast error
            sendPackets(responseBuilder.hidErrorResponse(BROADCAST_CID, ERR_INVALID_SEQ), sessionGeneration)
            return
        }

        val message = result.getOrNull() ?: return // null = still accumulating
        onCompletedMessageForTest?.invoke(message)

        Logger.d {
            "CTAPHID cmd=0x${message.command.toString(HEX_RADIX).uppercase()} " +
                "cid=${message.channelId.toHex()} payloadLen=${message.payload.size}"
        }

        var ownershipTransferred = false
        try {
            if (isCurrentSession(sessionGeneration)) {
                dispatchMessage(message, sessionGeneration)
                ownershipTransferred = true
            }
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            // A memory trim/replacement cancels only the bounded provider owner. Keep the
            // receiver alive when its transport coroutine itself is still active; disconnect
            // cancellation must continue propagating and terminate the receiver.
            currentCoroutineContext().ensureActive()
            Logger.i(cancellation) { "Discarded CTAPHID work after its wallet owner was fenced" }
        } finally {
            if (!ownershipTransferred) message.payload.fill(0)
        }
    }

    // ── Command dispatcher ────────────────────────────────────────────────────

    /** Transfers CBOR payload ownership to the asynchronous request owner; other messages erase locally. */
    @Req("FR-HID-010", "FR-VAL-020")
    internal suspend fun dispatchMessage(
        message: CtapHidMessage,
        sessionGeneration: Long = hidWrapper.sessionGeneration(),
    ) = commandDispatcher.dispatch(message, sessionGeneration)

    // ── CTAPHID_CBOR ──────────────────────────────────────────────────────────

    private suspend fun handleCbor(
        message: CtapHidMessage,
        requestAdmissionNanos: Long,
        requestSessionGeneration: Long,
    ) {
        val cid = message.channelId

        // Accept CBOR only for a channel allocated in this live transport session.
        if (!isCurrentSession(requestSessionGeneration)) return
        val channelIsCurrent = isChannelRegistered(cid, requestSessionGeneration)
        if (cid.contentEquals(BROADCAST_CID) || !channelIsCurrent) {
            sendPackets(responseBuilder.hidErrorResponse(cid, ERR_INVALID_CHANNEL), requestSessionGeneration)
            return
        }

        val payload = message.payload
        if (!isCurrentSession(requestSessionGeneration)) return
        if (payload.isEmpty()) {
            sendPackets(
                responseBuilder.errorResponse(cid, com.chimali.fido2.ctap2.CTAP2_ERR_INVALID_CBOR),
                requestSessionGeneration,
            )
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
        // NFR-PERF-030: Start timing the request through outbound HID response handoff.
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
            CoroutineScope(currentCoroutineContext()).launch(workerDispatcher) {
                delay(KEEPALIVE_INITIAL_DELAY) // wait first; spec requires first ~100ms. Reference uses 75ms.
                if (isCurrentSession(requestSessionGeneration)) {
                    sendPackets(responseBuilder.keepAliveResponse(cid, STATUS_PROCESSING), requestSessionGeneration)
                }
                while (true) {
                    delay(KEEPALIVE_PERIOD) // 75ms between subsequent keepalives.
                    if (isCurrentSession(requestSessionGeneration)) {
                        sendPackets(responseBuilder.keepAliveResponse(cid, STATUS_UPNEEDED), requestSessionGeneration)
                    } else {
                        break
                    }
                }
            }

        var responseHandedOff = false
        var responsePackets: List<ByteArray>? = null

        fun ownResponse(packets: List<ByteArray>): List<ByteArray> {
            responsePackets?.takeIf { it !== packets }?.forEach { it.fill(0) }
            responsePackets = packets
            return packets
        }
        try {
            val packets =
                try {
                    try {
                        val elapsedMillis = (monotonicNanos() - requestAdmissionNanos) / NANOS_PER_MILLISECOND
                        val remainingMillis = ceremonyTimeoutMillis - elapsedMillis
                        withTimeout(remainingMillis) {
                            when (ctapCommand) {
                                CMD_MAKE_CREDENTIAL -> {
                                    // authenticatorMakeCredential — CTAP2 registration
                                    val resp =
                                        masterSeedProvider.withSessionOwner(
                                            ownerKind = MasterSeedOwnerKind.AUTHENTICATOR,
                                        ) {
                                            ownResponse(makeCredentialHandler.handle(message))
                                        }
                                    publishSuccessEvent()
                                    resp
                                }

                                CMD_GET_ASSERTION -> {
                                    // authenticatorGetAssertion — CTAP2 authentication
                                    val resp =
                                        masterSeedProvider.withSessionOwner(
                                            ownerKind = MasterSeedOwnerKind.AUTHENTICATOR,
                                        ) {
                                            ownResponse(commandDispatcher.handleGetAssertion(message))
                                        }
                                    publishSuccessEvent()
                                    resp
                                }

                                CMD_GET_INFO ->
                                    ownResponse(commandDispatcher.handleGetInfo(cid)) // authenticatorGetInfo

                                else -> {
                                    Logger.w { "Unsupported CTAP2 command 0x${ctapCommand.toString(HEX_RADIX)}" }
                                    ownResponse(responseBuilder.errorResponse(cid, ERR_INVALID_CMD))
                                }
                            }
                        }
                    } catch (_: TimeoutCancellationException) {
                        Logger.w { "CTAP ceremony exceeded its admission deadline" }
                        ownResponse(responseBuilder.errorResponse(cid, CTAP2_ERR_USER_ACTION_TIMEOUT))
                    }
                } finally {
                    // cancelAndJoin() (not just cancel()) ensures the keepalive coroutine
                    // has completely stopped before we send the real response. Without this,
                    // a keepalive in-flight could arrive at Windows AFTER the CBOR response,
                    // corrupting the framing of the next request.
                    PerformanceTrace.section("Fido2.HID.KeepaliveShutdown") {
                        keepaliveJob.cancelAndJoin()
                    }
                }
            // Capture CTAP processing separately from response queueing and report pacing.
            BaselineMeasurements.processingComplete(operationLabel)
            BaselineMeasurements.trackResponse(operationLabel, packets)
            if (!isCurrentSession(requestSessionGeneration)) return
            // Wait until the final response report has been handed to the Bluetooth HID
            // wrapper so NFR-PERF-030 includes transport-queue wait and report pacing.
            responseHandedOff =
                PerformanceTrace.section("Fido2.HID.ResponseQueueAndHandoff") {
                    sendPacketsAndAwaitHandoff(packets, requestSessionGeneration)
                }
        } finally {
            // These packets contain the serialized CTAP response (including any PRF output).
            // The queue handoff completes before this owner releases and erases them.
            responsePackets?.forEach { it.fill(0) }
            // User-interaction time remains excluded from the measured system latency.
            LatencyProfiler.end(operationLabel, responseHandedOff)
        }
    }

    private fun publishSuccessEvent() {
        val state = hidWrapper.connectionState.value
        if (state is HidConnectionState.Connected) {
            val mac = state.device.address
            val name =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    try {
                        state.device.name
                    } catch (e: SecurityException) {
                        Logger.w(e) { "BluetoothHidTransport: Failed to get device name" }
                        null
                    }
                } else {
                    @Suppress("DEPRECATION")
                    state.device.name
                }

            val devClass =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    try {
                        state.device.bluetoothClass?.deviceClass
                    } catch (e: SecurityException) {
                        Logger.w(e) { "BluetoothHidTransport: Failed to get bluetooth class" }
                        null
                    }
                } else {
                    @Suppress("DEPRECATION")
                    state.device.bluetoothClass?.deviceClass
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Transfers packet ownership to the generation-bound sender. */
    @Req("FR-HID-010", "FR-VAL-020")
    private fun sendPackets(
        packets: List<ByteArray>,
        sessionGeneration: Long = hidWrapper.sessionGeneration(),
    ) = sendQueueSender.enqueue(packets, sessionGeneration)

    /** Transfers an owned response copy to the matching generation until HID handoff completes. */
    @Req("FR-HID-010", "FR-VAL-020")
    private suspend fun sendPacketsAndAwaitHandoff(
        packets: List<ByteArray>,
        sessionGeneration: Long,
    ): Boolean = sendQueueSender.enqueueAndAwait(packets, sessionGeneration)

    /** Starts the generation-bound sender before the receiver begins admitting requests. */
    @Req("FR-HID-010", "FR-VAL-020")
    internal fun restartSendQueueForTest() = sendQueueSender.start()

    /** Enqueues an owned test packet through the production generation-bound sender. */
    @Req("FR-HID-010", "FR-VAL-020")
    internal fun enqueueSendPacketForTest(packet: ByteArray) = sendQueueSender.enqueue(listOf(packet))

    /** Stops the sender through its production generation cleanup path. */
    @Req("FR-HID-010", "FR-VAL-020")
    internal fun stopSendQueueForTest() = sendQueueSender.stop()

    @Req("FR-HID-010", "FR-VAL-020")
    private fun generateCid(sessionGeneration: Long = hidWrapper.sessionGeneration()): ByteArray? =
        synchronized(transportSessionLock) {
            channelRegistry.allocate(
                sessionGeneration = sessionGeneration,
                maxAttempts = MAX_CID_ALLOCATION_ATTEMPTS,
                maximumChannels = MAX_SESSION_CIDS,
                isSessionCurrent = { isCurrentSessionLocked(sessionGeneration) },
                nextCidBytes = nextCidBytes,
            )
        }

    private fun clearChannelRegistry() = channelRegistry.clear()

    /** Registers a copied synthetic CID for transport dispatch regressions. */
    @Req("FR-HID-010", "FR-VAL-020")
    internal fun registerChannelForTest(cid: ByteArray) =
        channelRegistry.registerForTest(cid, hidWrapper.sessionGeneration(), MAX_SESSION_CIDS)

    /** Checks a CID against the same generation-bound registry used by production dispatch. */
    @Req("FR-HID-010", "FR-VAL-020")
    internal fun isChannelRegisteredForTest(
        cid: ByteArray,
        generation: Long,
    ): Boolean = channelRegistry.isRegistered(cid, generation)

    /** Allocates a CID through the production session registry for bounded-allocation tests. */
    @Req("FR-HID-010", "FR-VAL-020")
    internal fun allocateCidForTest(): ByteArray? = generateCid()

    /** Returns the wrapper's current host generation for transport lifecycle assertions. */
    @Req("FR-HID-010", "FR-VAL-020")
    internal fun sessionGenerationForTest(): Long = hidWrapper.sessionGeneration()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /** Expose connection state for observing by the presentation layer. */
    override val connectionState: StateFlow<HidConnectionState> = hidWrapper.connectionState
}

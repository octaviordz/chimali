@file:Suppress("MissingPermission")

package com.chimali.fido2.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.exceptionOrNull
import com.chimali.core.common.result.isSuccess
import com.chimali.core.common.result.onFailure
import com.chimali.fido2.domain.exception.Fido2Exception
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

// ── FIDO2 HID report constants ────────────────────────────────────────────────

/*
 * Report descriptor for a FIDO2 HID authenticator interface.
 */

// Note: The report count is 0x3E (62 bytes) instead of 64.
// Bluetooth Classic L2CAP MTU for HID interrupt is capped at 64 bytes.
// With 1 byte HID header + 1 byte Report ID, the payload must be 62.
// Windows 11 bthid.sys strictly enforces this and returns ERROR_NOT_SUPPORTED (0x32)
// if it is asked to fragment an Output Report over L2CAP.

private val FIDO_HID_REPORT_DESCRIPTOR =
    byteArrayOf(
        // Usage Page (FIDO Alliance)
        0x06.toByte(),
        0xD0.toByte(),
        0xF1.toByte(),
        // Usage (FIDO Usage Data In)
        0x09.toByte(),
        0x01.toByte(),
        // Collection (Application)
        0xA1.toByte(),
        0x01.toByte(),
        // ── Input Report ──
        // Usage (Input Report Data)
        0x09.toByte(),
        0x20.toByte(),
        // Logical Minimum (0)
        0x15.toByte(),
        0x00.toByte(),
        // Logical Maximum (255)
        0x26.toByte(),
        0xFF.toByte(),
        0x00.toByte(),
        // Report Size (8 bits)
        0x75.toByte(),
        0x08.toByte(),
        // Report Count (62)
        0x95.toByte(),
        0x3E.toByte(),
        // Input (Data, Variable, Absolute)
        0x81.toByte(),
        0x02.toByte(),
        // ── Output Report ──
        // Usage (Output Report Data)
        0x09.toByte(),
        0x21.toByte(),
        // Logical Minimum (0)
        0x15.toByte(),
        0x00.toByte(),
        // Logical Maximum (255)
        0x26.toByte(),
        0xFF.toByte(),
        0x00.toByte(),
        // Report Size (8 bits)
        0x75.toByte(),
        0x08.toByte(),
        // Report Count (62)
        0x95.toByte(),
        0x3E.toByte(),
        // Output (Data, Variable, Absolute)
        0x91.toByte(),
        0x02.toByte(),
        // End Collection
        0xC0.toByte(),
    )

/**
 * Payload bytes in each FIDO HID report, matching both report-count fields in the HID descriptor
 * and [HID_PACKET_SIZE] used by the CTAPHID parser. The HID framing and Bluetooth MTU require this wire size.
 */
@Req("FR-HID-010", "FR-HID-020")
internal const val FIDO_HID_REPORT_SIZE = 62

/**
 * Wraps [BluetoothHidDevice] profile proxy and manages the HID peripheral lifecycle
 * for the FIDO2 authenticator.
 *
 * ## Architecture (Clean Standard Lifecycle)
 *
 * This wrapper follows the standard Android BluetoothHidDevice lifecycle:
 *
 * 1. **initialize()** — acquire the HID_DEVICE profile proxy via getProfileProxy()
 * 2. **registerApp()** — call unregisterApp() first (clears stale state), then registerApp()
 * 3. **onAppStatusChanged(registered=true)** — app is now registered, set Advertising
 * 4. **onConnectionStateChanged(CONNECTED)** — host connected, verify bond, accept
 * 5. **unregisterApp() / close()** — cleanup
 *
 * ## Diagnostic Logging
 *
 * Every lifecycle transition logs:
 * - getConnectedDevices() snapshot
 * - getConnectionState() for relevant devices
 * - Bond state of involved devices
 * - Current _connectionState value
 *
 * This enables diagnosis of connectivity issues without speculative workarounds.
 */
@org.koin.core.annotation.Single
class BluetoothHidDeviceWrapper(
    private val context: Context,
) {
    // ── Bluetooth infrastructure ──────────────────────────────────────────────

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private val bluetoothStateWaiter = BluetoothAdapterStateWaiter { bluetoothAdapter }
    private val reportSender = BluetoothHidReportSender()

    /** The HID Device profile proxy, obtained asynchronously from the system. */
    private var hidDevice: BluetoothHidDevice? = null

    /** Currently connected host device (null if not connected). */
    @Volatile private var connectedDevice: BluetoothDevice? = null

    @Volatile private var hidSessionGeneration: Long = 0L
    private val sessionLock = Any()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── State ─────────────────────────────────────────────────────────────────

    private val _connectionState = MutableStateFlow<HidConnectionState>(HidConnectionState.Idle)

    /** Read-only connection lifecycle published after each synchronized host-session transition. */
    @Req("FR-HID-010", "FR-HID-020")
    val connectionState: StateFlow<HidConnectionState> = _connectionState.asStateFlow()

    /** Synchronous owner notification before the wrapper admits reports from a new host. */
    @Req("FR-HID-010", "FR-HID-020")
    internal var onSessionTransition: ((Long, HidConnectionState) -> Unit)? = null

    private fun publishSessionState(state: HidConnectionState) {
        _connectionState.value = state
        onSessionTransition?.invoke(hidSessionGeneration, state)
    }

    private var proactiveConnectJob: Job? = null

    /**
     * A device whose L2CAP HID channels have opened but whose link-key exchange is still
     * in progress ([BluetoothDevice.BOND_BONDING]). We defer emitting [HidConnectionState.Connected]
     * until [BluetoothDevice.ACTION_BOND_STATE_CHANGED] confirms [BluetoothDevice.BOND_BONDED].
     *
     * Without this, FIDO2 operations can start on an unencrypted L2CAP channel, which causes
     * Windows to intermittently fail with 0x8007000d (ERROR_INVALID_DATA).
     */
    @Volatile private var pendingBondDevice: BluetoothDevice? = null

    /**
     * Bounded queue of owned 62-byte report copies. Platform callback arrays are borrowed and
     * never enqueued directly. Buffered reports are erased if delivery is abandoned or closed.
     */
    @Req("FR-HID-010", "FR-HID-020")
    val incomingReports: Channel<IncomingHidReport> =
        Channel(capacity = 64, onUndeliveredElement = { report -> report.bytes.fill(0) })

    /** Host-test observer for report bytes after queue ownership is established. */
    @Req("FR-HID-010", "FR-HID-020")
    internal var onIncomingReportOwnedForTest: ((ByteArray) -> Unit)? = null

    /** Erases reports left from a disconnected HID session before another can consume them. */
    @Req("FR-HID-010", "FR-HID-020")
    internal fun discardQueuedIncomingReports() {
        while (true) {
            val report = incomingReports.tryReceive().getOrNull() ?: return
            report.bytes.fill(0)
        }
    }

    /** Changes synchronously at host-session boundaries, before StateFlow observers run. */
    @Req("FR-HID-010", "FR-HID-020")
    internal fun sessionGeneration(): Long = hidSessionGeneration

    /** Reads a consistent state/generation pair for listener-attachment reconciliation. */
    @Req("FR-HID-010", "FR-HID-020")
    internal fun sessionSnapshot(): Pair<Long, HidConnectionState> =
        synchronized(sessionLock) { hidSessionGeneration to _connectionState.value }

    private fun advanceSessionGeneration() {
        synchronized(sessionLock) { hidSessionGeneration += 1L }
    }

    /** Advances the wrapper generation through the production session lock for lifecycle regressions. */
    @Req("FR-HID-010", "FR-HID-020")
    internal fun advanceSessionGenerationForTest() = advanceSessionGeneration()

    // ── Diagnostic helpers ────────────────────────────────────────────────────

    /**
     * Logs a diagnostic snapshot of the HID profile state.
     * Called at every significant lifecycle point for visibility.
     */
    private fun logDiagnosticSnapshot(tag: String) = callbackCoordinator.logDiagnosticSnapshot(tag)

    private val callbackCoordinator =
        BluetoothHidCallbackCoordinator(
            owner =
                object : BluetoothHidCallbackOwner {
                    override var hidDevice: BluetoothHidDevice?
                        get() = this@BluetoothHidDeviceWrapper.hidDevice
                        set(value) {
                            this@BluetoothHidDeviceWrapper.hidDevice = value
                        }
                    override var connectedDevice: BluetoothDevice?
                        get() = this@BluetoothHidDeviceWrapper.connectedDevice
                        set(value) {
                            this@BluetoothHidDeviceWrapper.connectedDevice = value
                        }
                    override var pendingBondDevice: BluetoothDevice?
                        get() = this@BluetoothHidDeviceWrapper.pendingBondDevice
                        set(value) {
                            this@BluetoothHidDeviceWrapper.pendingBondDevice = value
                        }
                    override var proactiveConnectJob: Job?
                        get() = this@BluetoothHidDeviceWrapper.proactiveConnectJob
                        set(value) {
                            this@BluetoothHidDeviceWrapper.proactiveConnectJob = value
                        }
                    override val bluetoothAdapter: BluetoothAdapter?
                        get() = this@BluetoothHidDeviceWrapper.bluetoothAdapter
                    override val scope: CoroutineScope
                        get() = this@BluetoothHidDeviceWrapper.scope
                    override val sessionLock: Any
                        get() = this@BluetoothHidDeviceWrapper.sessionLock
                    override val connectionState: MutableStateFlow<HidConnectionState>
                        get() = this@BluetoothHidDeviceWrapper._connectionState

                    override fun advanceSessionGeneration() = this@BluetoothHidDeviceWrapper.advanceSessionGeneration()

                    override fun discardQueuedIncomingReports() =
                        this@BluetoothHidDeviceWrapper.discardQueuedIncomingReports()

                    override fun publishSessionState(state: HidConnectionState) =
                        this@BluetoothHidDeviceWrapper.publishSessionState(state)

                    override fun acceptConnectedDevice(device: BluetoothDevice) =
                        this@BluetoothHidDeviceWrapper.acceptConnectedDevice(device)

                    override fun connect(device: BluetoothDevice) = this@BluetoothHidDeviceWrapper.connect(device)

                    override fun enqueueIncomingReportFromDevice(
                        device: BluetoothDevice,
                        data: ByteArray,
                    ) = this@BluetoothHidDeviceWrapper.enqueueIncomingReportFromDevice(device, data)
                },
        )

    /** Platform callback retained on the wrapper for lifecycle testing and callback registration. */
    private val hidCallback = callbackCoordinator.hidCallback

    private var isReceiverRegistered = false

    // ── Callbacks ─────────────────────────────────────────────────────────────

    /**
     * Completes a device connection: sets [connectedDevice], emits [HidConnectionState.Connected],
     * and notifies the transport layer.
     */
    private fun acceptConnectedDevice(device: BluetoothDevice) {
        Logger.i { "Accepting connection from ${device.address}" }
        synchronized(sessionLock) {
            advanceSessionGeneration()
            discardQueuedIncomingReports()
            connectedDevice = device
            publishSessionState(HidConnectionState.Connected(device))
        }
        logDiagnosticSnapshot("DEVICE_ACCEPTED")

        // "?"? WORKAROUND: Prevent Windows 11 5-second HID timeout "?"?
        // Windows 11 will aggressively disconnect Bluetooth HID peripherals exactly 5 seconds
        // after channel open if the peripheral has not transmitted any reports. We fire a 64-byte
        // report of all zeros (Channel ID 0x00000000). The FIDO spec mandates that packets with
        // Channel ID 0 MUST be ignored by the host. This effectively acts as a transport-level
        // keep-alive, proving to the Windows hidclass.sys driver that the device is alive.
        //
        // CRITICAL DELAY: We MUST delay this transmission by ~3 seconds. Sending an L2CAP DATA
        // packet immediately upon channel creation causes the Motorola baseband to violently
        // crash with hci_status=36 (LMP PDU Not Allowed). 3000ms ensures the link is fully
        // stabilized (sniff/QoS parameters negotiated) while safely beating the 5000ms timeout.
        // Delayed readiness packet removed. It causes the Motorola baseband to crash with hci_status=36
        // because sending un-numbered L2CAP DATA on the Interrupt IN channel while Windows is aborting
        // the connection is physically rejected by the Link Manager.
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Must be called once to acquire the [BluetoothHidDevice] profile proxy from the system.
     *
     * On some OEM devices (e.g. Asus Zenfone 10), [BluetoothProfile.ServiceListener.onServiceConnected]
     * is silently never delivered even when [BluetoothAdapter.getProfileProxy] returns true. This can
     * happen when the Bluetooth daemon is still initializing. We apply the same timeout+retry pattern
     * used in [registerApp] to handle this gracefully.
     */
    @Req("FR-HID-010", "FR-HID-020")
    suspend fun initialize(): Result<Unit> {
        Logger.d { "initialize() called. Current hidDevice: $hidDevice" }
        if (hidDevice != null) return Result.success(Unit)

        // Register adapter-state receiver for BT hardware toggle and bond-state changes.
        if (!isReceiverRegistered) {
            val filter =
                IntentFilter().apply {
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                    addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                    addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                }
            // CRITICAL: On Android 13+ (API 33), system broadcasts like
            // ACTION_BOND_STATE_CHANGED are silently dropped unless we
            // register with RECEIVER_EXPORTED. Without this flag, our
            // proactive connect() on BOND_BONDED never fires.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(callbackCoordinator.bluetoothStateReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(callbackCoordinator.bluetoothStateReceiver, filter)
            }
            isReceiverRegistered = true
            Logger.d {
                "BluetoothAdapter state + bond-state receiver registered (exported=" +
                    "${android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU})"
            }
        }

        var lastException: Exception? = null
        val cfg = BluetoothHidConfigProvider.config
        val maxRetries = cfg.initMaxRetries
        var retryDelay = cfg.initRetryDelay

        for (attempt in 1..maxRetries) {
            Logger.d { "initialize attempt $attempt/$maxRetries" }

            val result =
                withTimeoutOrNull(cfg.initTimeout) {
                    suspendCancellableCoroutine { cont ->
                        callbackCoordinator.initContinuation = cont
                        try {
                            val adapterState = bluetoothAdapter?.state
                            Logger.d { "Bluetooth adapter state before getProfileProxy: $adapterState (STATE_ON=12)" }
                            Logger.d { "Calling getProfileProxy for HID_DEVICE..." }
                            val isSuccess =
                                bluetoothAdapter?.getProfileProxy(
                                    context,
                                    callbackCoordinator.serviceListener,
                                    BluetoothProfile.HID_DEVICE,
                                )
                                    ?: false
                            Logger.d { "getProfileProxy returned: $isSuccess" }
                            if (!isSuccess) {
                                Logger.e {
                                    "getProfileProxy returned false - " +
                                        "Bluetooth may be off or profile unsupported"
                                }
                                cont.resumeWithException(
                                    Fido2Exception.BluetoothException("Failed to request HID proxy. Is Bluetooth on?"),
                                )
                                callbackCoordinator.initContinuation = null
                            } else {
                                Logger.d {
                                    "getProfileProxy returned true - waiting for onServiceConnected callback..."
                                }
                            }
                        } catch (e: SecurityException) {
                            Logger.e(
                                e,
                            ) { "SecurityException in getProfileProxy - missing BLUETOOTH_CONNECT permission?" }
                            cont.resumeWithException(
                                Fido2Exception.BluetoothPermissionDenied(
                                    "Bluetooth permission denied",
                                    e,
                                ),
                            )
                            callbackCoordinator.initContinuation = null
                        }
                    }
                }

            when {
                result == null -> {
                    Logger.e {
                        "initialize() timed out after ${cfg.initTimeout} - " +
                            "onServiceConnected never received."
                    }
                    lastException =
                        Fido2Exception.BluetoothException(
                            "HID proxy acquisition timed out (onServiceConnected never fired)",
                        )
                }

                result.isSuccess -> {
                    Logger.i { "initialize() succeeded on attempt $attempt" }
                    return Result.success(Unit)
                }

                else -> {
                    lastException = result.exceptionOrNull() as? Exception
                    Logger.w { "initialize() failed on attempt $attempt: ${lastException?.message}" }
                    // If it's a permission error, don't retry — user action needed
                    if (lastException is Fido2Exception.BluetoothPermissionDenied) {
                        return Result.failure(lastException)
                    }
                }
            }

            if (attempt < maxRetries) {
                Logger.d { "Retrying initialize() in $retryDelay..." }
                delay(retryDelay)
                retryDelay *= 2
            }
        }

        Logger.e { "initialize() failed after $maxRetries attempts" }
        return Result.failure(
            lastException ?: Fido2Exception.BluetoothException("HID proxy acquisition failed after retries"),
        )
    }

    /**
     * Registers the FIDO2 HID application and begins advertising so that a
     * Bluetooth host can discover and connect to this authenticator.
     *
     * ## Standard Lifecycle
     *
     * Per Android best practices and AOSP documentation:
     * 1. Call `unregisterApp()` first to clear any stale registration
     * 2. Wait briefly for the unregister to propagate
     * 3. Call `registerApp()` with a fresh callback
     * 4. Trust `onAppStatusChanged` as the source of truth for registration status
     *
     * The `registerApp()` return value only indicates whether the request was
     * successfully *sent* to the system service, NOT whether registration succeeded.
     * The actual registration outcome is delivered via `onAppStatusChanged`.
     */
    @Req("FR-HID-010", "FR-HID-020")
    suspend fun registerApp(): Result<Unit> {
        val cfg = BluetoothHidConfigProvider.config
        val maxRetries = cfg.registerMaxRetries
        var retryDelay = cfg.registerRetryDelay
        var lastException: Exception? = null

        for (attempt in 1..maxRetries) {
            Logger.d { "registerApp attempt $attempt/$maxRetries" }
            logDiagnosticSnapshot("PRE_REGISTER_$attempt")

            // ── Step 0: Ensure Bluetooth is ON ──
            // If the user just clicked "Allow" on the system prompt, the state might
            // be STATE_TURNING_ON. We wait up to 5 seconds for it to reach STATE_ON.
            val bluetoothState = bluetoothStateWaiter.waitForBluetoothToTurnOn()

            val result =
                withTimeoutOrNull(cfg.registerTimeout) {
                    suspendCancellableCoroutine<Result<Unit>> { cont ->
                        val hid = hidDevice
                        if (hid == null) {
                            cont.resume(
                                Result.failure(
                                    Fido2Exception.BluetoothException("HID_DEVICE profile not yet acquired"),
                                ),
                            )
                            return@suspendCancellableCoroutine
                        }

                        if (bluetoothState != BluetoothAdapter.STATE_ON) {
                            Logger.w { "Bluetooth is not ON (current state: $bluetoothState), cannot register HID app" }
                            cont.resume(
                                Result.failure(
                                    Fido2Exception.BluetoothException(
                                        "Bluetooth must be enabled to start the authenticator",
                                    ),
                                ),
                            )
                            return@suspendCancellableCoroutine
                        }

                        // ── Step 1: Clear any stale registration ──
                        // Best practice: call unregisterApp() before registerApp() to ensure
                        // no previous app instance (same or different process) holds the
                        // exclusive HID_DEVICE registration slot.
                        Logger.d { "Step 1: Calling unregisterApp() to clear any stale registration..." }
                        try {
                            val isUnregistrationSuccessful = hid.unregisterApp()
                            Logger.d { "unregisterApp() result: $isUnregistrationSuccessful" }
                        } catch (e: IllegalStateException) {
                            Logger.w(e) { "unregisterApp() threw (non-fatal, proceeding with registerApp)" }
                        }

                        logDiagnosticSnapshot("POST_UNREGISTER_$attempt")

                        // ── Step 2: Register with fresh callback ──
                        val sdpSubclass = BluetoothHidDevice.SUBCLASS1_COMBO
                        Logger.d { "Step 2: Using SUBCLASS1_COMBO..." }

                        val sdp =
                            BluetoothHidDeviceAppSdpSettings(
                                "Chimali Authenticator",
                                "FIDO2 Virtual Security Key",
                                "Chimali",
                                sdpSubclass,
                                FIDO_HID_REPORT_DESCRIPTOR,
                            )
// Wrapper callback that resolves the continuation on app status change
                        val registrationCallback = callbackCoordinator.createRegistrationCallback(cont)

                        val registerResult =
                            try {
                                Logger.d("Step 3: Calling registerApp with SDP subclass COMBO and null QoS...")
                                val isCallSuccessful =
                                    hid.registerApp(
                                        sdp,
                                        null,
                                        null,
                                        Executors.newSingleThreadExecutor(),
                                        registrationCallback,
                                    )
                                Logger.i { "registerApp() framework return value: $isCallSuccessful" }
                                isCallSuccessful
                            } catch (e: SecurityException) {
                                Logger.e(e) { "SecurityException in registerApp" }
                                cont.resume(
                                    Result.failure(
                                        Fido2Exception.BluetoothPermissionDenied(
                                            "BLUETOOTH_ADVERTISE permission denied",
                                            e,
                                        ),
                                    ),
                                )
                                return@suspendCancellableCoroutine
                            } catch (e: IllegalStateException) {
                                Logger.e(e) { "Unexpected IllegalStateException in registerApp" }
                                cont.resume(
                                    Result.failure(
                                        Fido2Exception.BluetoothException(
                                            "Unexpected error during app registration: ${e.message}",
                                        ),
                                    ),
                                )
                                return@suspendCancellableCoroutine
                            }

                        logDiagnosticSnapshot("POST_REGISTER_CALL_$attempt")

                        if (!registerResult) {
                            // registerApp() returned false — the request was not accepted by the
                            // system service. This typically means another app or stale process
                            // already holds the exclusive HID_DEVICE registration.
                            Logger.w(
                                "registerApp() returned false — system did not accept registration request. " +
                                    "Another app or stale process may hold the HID_DEVICE slot.",
                            )
                            // The callback may still fire (some OEMs send a courtesy callback).
                            // We wait for the timeout to expire rather than immediately failing,
                            // since the callback might still arrive with registered=true.
                        }
                    }
                }

            when {
                result?.isSuccess == true -> {
                    Logger.i { "registerApp() succeeded on attempt $attempt" }
                    logDiagnosticSnapshot("REGISTER_SUCCESS")
                    callbackCoordinator.checkExistingConnectionsWhileAdvertising()
                    return Result.success(Unit)
                }
                result == null -> {
                    Logger.e { "registerApp timed out after ${cfg.registerTimeout} on attempt $attempt" }
                    lastException = Fido2Exception.BluetoothException("HID registration timed out")
                    logDiagnosticSnapshot("REGISTER_TIMEOUT_$attempt")
                }
                else -> {
                    lastException = result.exceptionOrNull() as? Exception
                    Logger.w { "registerApp failure on attempt $attempt: ${lastException?.message}" }
                    logDiagnosticSnapshot("REGISTER_FAILURE_$attempt")
                }
            }

            if (attempt < maxRetries) {
                Logger.d { "Retrying registerApp() in $retryDelay..." }
                delay(retryDelay)
                retryDelay *= 2
                if (retryDelay > cfg.registerRetryMaxDelay) {
                    retryDelay = cfg.registerRetryMaxDelay
                }
            }
        }

        return Result.failure(
            lastException ?: Fido2Exception.BluetoothException("HID registration failed after $maxRetries attempts"),
        )
    }

    /**
     * Connects to the given paired host device over Bluetooth HID.
     *
     * Returns `true` if the connection attempt was successfully initiated, `false` otherwise.
     */
    @Req("FR-HID-010", "FR-HID-020")
    fun connect(device: BluetoothDevice): Boolean {
        val hid = hidDevice
        if (hid == null) {
            Logger.w { "connect: HID device proxy not available" }
            return false
        }
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            Logger.w { "connect: device ${device.address} is not bonded (bondState=${device.bondState})" }
            return false
        }
        return try {
            val initiated = hid.connect(device)
            Logger.i { "connect(${device.address}) initiated: $initiated" }
            initiated
        } catch (e: SecurityException) {
            Logger.e(e) { "connect: Bluetooth permission denied for ${device.address}" }
            false
        } catch (e: IllegalStateException) {
            Logger.e(e) { "connect: Bluetooth not available for ${device.address} — ${e.message}" }
            false
        }
    }

    /**
     * Connects to a paired host device specified by its MAC address.
     */
    @Req("FR-HID-010", "FR-HID-020")
    fun connect(macAddress: String): Boolean {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Logger.w { "connect: BluetoothAdapter not available" }
            return false
        }
        val device =
            try {
                adapter.getRemoteDevice(macAddress)
            } catch (e: IllegalArgumentException) {
                Logger.e(e) { "connect: invalid Bluetooth MAC address: $macAddress" }
                null
            } ?: return false

        return connect(device)
    }

    /**
     * Sends a raw 62-byte FIDO2 HID report to the connected Bluetooth host.
     *
     * @param data Raw report bytes. Must be <= 62 bytes.
     * The wrapper borrows the array for the synchronous platform send and does not retain or erase it.
     * @return `true` if the report was dispatched to the BluetoothHidDevice profile,
     *         `false` if the profile proxy or connected device is unavailable.
     */
    @Req("FR-HID-010", "FR-HID-020")
    fun sendReport(data: ByteArray): Boolean = sendReport(data, sessionGeneration())

    /** Sends a borrowed report only while its expected host generation is current. */
    @Req("FR-HID-010", "FR-HID-020")
    fun sendReport(
        data: ByteArray,
        expectedSessionGeneration: Long,
    ): Boolean =
        synchronized(sessionLock) {
            if (expectedSessionGeneration != hidSessionGeneration) {
                return@synchronized false
            }
            sendReportToCurrentHost(data)
        }

    private fun sendReportToCurrentHost(data: ByteArray): Boolean = reportSender.send(hidDevice, connectedDevice, data)

    /** Unregisters the HID app (stops advertising / disconnects host). */
    @Req("FR-HID-010", "FR-HID-020")
    fun unregisterApp() {
        Logger.d("unregisterApp() called")
        logDiagnosticSnapshot("PRE_UNREGISTER")
        proactiveConnectJob?.cancel()
        proactiveConnectJob = null
        try {
            hidDevice?.unregisterApp()
        } catch (e: SecurityException) {
            Logger.e(e) { "unregisterApp: Bluetooth permission denied" }
        }
        synchronized(sessionLock) {
            advanceSessionGeneration()
            connectedDevice = null
            pendingBondDevice = null
            discardQueuedIncomingReports()
            publishSessionState(HidConnectionState.Idle)
        }
        logDiagnosticSnapshot("POST_UNREGISTER")
    }

    /** Releases the profile proxy. Should be called from Application.onTerminate. */
    @Req("FR-HID-010", "FR-HID-020")
    fun close() {
        Logger.d("close() called")
        proactiveConnectJob?.cancel()
        proactiveConnectJob = null
        scope.coroutineContext.cancelChildren()
        unregisterApp()
        if (isReceiverRegistered) {
            runCatching { context.unregisterReceiver(callbackCoordinator.bluetoothStateReceiver) }
                .onFailure { Logger.w { "close: failed to unregister BT state receiver: ${it.message}" } }
            isReceiverRegistered = false
        }
        try {
            hidDevice?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        } catch (e: SecurityException) {
            Logger.e(e) { "close: Bluetooth permission denied" }
        }
        hidDevice = null
        discardQueuedIncomingReports()
        // Cancellation drops any concurrently enqueued buffered reports through the erasing
        // onUndeliveredElement callback; this wrapper is terminal after close().
        incomingReports.cancel()
    }

    /**
     * Transitions the HID state to [HidConnectionState.Error].
     * Used to propagate external transport or registration failures to the UI.
     */
    @Req("FR-HID-010", "FR-HID-020")
    fun reportError(message: String) {
        synchronized(sessionLock) {
            advanceSessionGeneration()
            connectedDevice = null
            pendingBondDevice = null
            discardQueuedIncomingReports()
            publishSessionState(HidConnectionState.Error(message))
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Copies borrowed platform data into the bounded queue and erases it if admission fails. */
    @Req("FR-HID-010", "FR-HID-020")
    internal fun enqueueIncomingReport(data: ByteArray) {
        enqueueIncomingReport(data, hidSessionGeneration)
    }

    /**
     * Borrows platform callback bytes, copies them only for the active [device] and a correctly sized report, and
     * enqueues the owned copy. The caller retains its original array; a rejected queued copy is erased immediately.
     */
    @Req("FR-HID-010", "FR-HID-020")
    internal fun enqueueIncomingReportFromDevice(
        device: BluetoothDevice,
        data: ByteArray,
    ) {
        synchronized(sessionLock) {
            val activeDevice = connectedDevice
            if (
                activeDevice == null ||
                activeDevice.address != device.address ||
                _connectionState.value !is HidConnectionState.Connected
            ) {
                Logger.w { "Discarding HID report from a host outside the active session" }
                return
            }
            enqueueIncomingReport(data, hidSessionGeneration)
        }
    }

    private fun enqueueIncomingReport(
        data: ByteArray,
        generation: Long,
    ) {
        synchronized(sessionLock) {
            if (data.size != FIDO_HID_REPORT_SIZE) {
                Logger.w { "Discarding malformed HID report with ${data.size} bytes" }
                return
            }
            val ownedReport = data.copyOf()
            onIncomingReportOwnedForTest?.invoke(ownedReport)
            if (incomingReports.trySend(IncomingHidReport(ownedReport, generation)).isFailure) ownedReport.fill(0)
        }
    }
}

// ── State sealed class ────────────────────────────────────────────────────────

/** Queue-owned callback bytes tagged with the host-session generation that admitted them. */
@Req("FR-HID-010", "FR-HID-020")
data class IncomingHidReport(
    /** Owned fixed-size report bytes; the queue erases them after consumption or abandonment. */
    @Req("FR-HID-010", "FR-HID-020")
    val bytes: ByteArray,
    /** Wrapper generation that admitted the report; stale generations are discarded. */
    @Req("FR-HID-010", "FR-HID-020")
    val sessionGeneration: Long,
)

/** Represents the HID peripheral connection lifecycle. */
@Req("FR-HID-010", "FR-HID-020")
sealed class HidConnectionState {
    /** Profile proxy acquired but app not yet registered. */
    @Req("FR-HID-010", "FR-HID-020")
    object Idle : HidConnectionState()

    /** App registered; advertising to Bluetooth hosts. */
    @Req("FR-HID-010", "FR-HID-020")
    object Advertising : HidConnectionState()

    /** A host is in the process of connecting. */
    @Req("FR-HID-010", "FR-HID-020")
    data class Connecting(
        @Req("FR-HID-010", "FR-HID-020")
        val device: BluetoothDevice,
    ) : HidConnectionState()

    /** A host is connected and can exchange HID reports. */
    @Req("FR-HID-010", "FR-HID-020")
    data class Connected(
        @Req("FR-HID-010", "FR-HID-020")
        val device: BluetoothDevice,
    ) : HidConnectionState()

    /** An unrecoverable error occurred. */
    @Req("FR-HID-010", "FR-HID-020")
    data class Error(
        @Req("FR-HID-010", "FR-HID-020")
        val message: String,
    ) : HidConnectionState()
}

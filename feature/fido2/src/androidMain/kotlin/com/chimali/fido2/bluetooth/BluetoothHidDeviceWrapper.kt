@file:Suppress("MissingPermission")

package com.chimali.fido2.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import co.touchlab.kermit.Logger
import com.chimali.core.common.result.exceptionOrNull
import com.chimali.core.common.result.getOrDefault
import com.chimali.core.common.result.isSuccess
import com.chimali.core.common.result.onFailure
import com.chimali.fido2.domain.exception.Fido2Exception
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellableContinuation
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

private const val FIDO_HID_REPORT_SIZE = 62
private const val FIDO_REPORT_ID: Byte = 0

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

    /** The HID Device profile proxy, obtained asynchronously from the system. */
    private var hidDevice: BluetoothHidDevice? = null

    /** Currently connected host device (null if not connected). */
    private var connectedDevice: BluetoothDevice? = null

    // ── State ─────────────────────────────────────────────────────────────────

    private val _connectionState = MutableStateFlow<HidConnectionState>(HidConnectionState.Idle)
    val connectionState: StateFlow<HidConnectionState> = _connectionState.asStateFlow()

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
     * Channel that receives raw 62-byte HID reports from the host.
     * Consumers (e.g. [HidReportParser]) should collect from this channel.
     */
    val incomingReports: Channel<ByteArray> = Channel(capacity = Channel.UNLIMITED)

    // ── Diagnostic helpers ────────────────────────────────────────────────────

    /**
     * Logs a diagnostic snapshot of the HID profile state.
     * Called at every significant lifecycle point for visibility.
     */
    private fun logDiagnosticSnapshot(tag: String) {
        val hid = hidDevice
        if (hid == null) {
            Logger.d { "[DIAG:$tag] hidDevice=null, _connectionState=${_connectionState.value}" }
            return
        }

        try {
            val connectedDevices = hid.getConnectedDevices()
            val bondedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()

            Logger.i {
                "[DIAG:$tag] _connectionState=${_connectionState.value}, " +
                    "connectedDevice=${connectedDevice?.address}, " +
                    "pendingBondDevice=${pendingBondDevice?.address}"
            }
            Logger.i {
                "[DIAG:$tag] getConnectedDevices()=[" +
                    "${connectedDevices.joinToString { "${it.address}(state=${hid.getConnectionState(it)})" }}] " +
                    "(count=${connectedDevices.size})"
            }
            Logger.i {
                "[DIAG:$tag] bondedDevices=[${bondedDevices.joinToString { "${it.address}(bond=${it.bondState})" }}] " +
                    "(count=${bondedDevices.size})"
            }
        } catch (e: SecurityException) {
            Logger.w(e) { "[DIAG:$tag] SecurityException reading diagnostic state" }
        } catch (e: IllegalStateException) {
            Logger.w(e) { "[DIAG:$tag] IllegalStateException reading diagnostic state" }
        }
    }

    /**
     * Returns a human-readable name for a BluetoothProfile connection state int.
     */
    private fun connectionStateName(state: Int): String =
        when (state) {
            BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED(0)"
            BluetoothProfile.STATE_CONNECTING -> "CONNECTING(1)"
            BluetoothProfile.STATE_CONNECTED -> "CONNECTED(2)"
            BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING(3)"
            else -> "UNKNOWN($state)"
        }

    /**
     * Returns a human-readable name for a bond state int.
     */
    private fun bondStateName(state: Int): String =
        when (state) {
            BluetoothDevice.BOND_NONE -> "BOND_NONE(10)"
            BluetoothDevice.BOND_BONDING -> "BOND_BONDING(11)"
            BluetoothDevice.BOND_BONDED -> "BOND_BONDED(12)"
            else -> "UNKNOWN($state)"
        }

    /**
     * Handles system Bluetooth events:
     * - ACTION_STATE_CHANGED: resets when Bluetooth is turned off
     * - ACTION_BOND_STATE_CHANGED: completes deferred connections when bonding finishes
     * - ACTION_ACL_CONNECTED: diagnostic logging for connection lifecycle monitoring
     */
    private val bluetoothStateReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        Logger.d { "BluetoothAdapter state changed: $state" }
                        if (state == BluetoothAdapter.STATE_OFF) {
                            Logger.w { "Bluetooth turned OFF — resetting HID proxy and connection state" }
                            pendingBondDevice = null
                            hidDevice = null
                            connectedDevice = null
                            _connectionState.value = HidConnectionState.Idle
                        }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        @Suppress("DEPRECATION")
                        val device =
                            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                                ?: return
                        val bondState =
                            intent.getIntExtra(
                                BluetoothDevice.EXTRA_BOND_STATE,
                                BluetoothDevice.BOND_NONE,
                            )
                        val previousBondState =
                            intent.getIntExtra(
                                BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                                BluetoothDevice.BOND_NONE,
                            )
                        Logger.i {
                            "Bond state changed: device=${device.address} " +
                                "${bondStateName(previousBondState)}→${bondStateName(bondState)}"
                        }

                        // Log the HID connection state of this device
                        val hid = hidDevice
                        if (hid != null) {
                            try {
                                val hidState = hid.getConnectionState(device)
                                Logger.i {
                                    "  HID connection state for ${device.address}: ${connectionStateName(hidState)}"
                                }
                            } catch (e: IllegalStateException) {
                                Logger.w(e) { "  Could not query HID connection state for ${device.address}" }
                            }
                        }

                        logDiagnosticSnapshot("BOND_CHANGED")

                        val pending = pendingBondDevice
                        if (bondState == BluetoothDevice.BOND_BONDED &&
                            pending != null &&
                            pending.address == device.address
                        ) {
                            // Path A: L2CAP channel opened while bonding was still in progress.
                            // onConnectionStateChanged(CONNECTED) deferred us here — now the link
                            // is encrypted, so it is safe to accept the device.
                            Logger.i {
                                "Pending device ${device.address} is now BOND_BONDED — completing deferred connection."
                            }
                            pendingBondDevice = null
                            acceptConnectedDevice(device)
                        } else if (
                            bondState == BluetoothDevice.BOND_BONDED &&
                            pending == null &&
                            _connectionState.value is HidConnectionState.Advertising
                        ) {
                            // Path B: Device bonded while we are Advertising.
                            // Do NOT proactively call connect() here — evidence from the Asus phone
                            // (logcat 2026-04-07 18:52) shows that calling connect() on a device
                            // whose previous bond is stale causes btif_storage_remove_bonded_device,
                            // destroying the pairing entirely. Let the host drive the natural
                            // HID L2CAP connection flow after bonding completes.
                            Logger.i {
                                "BOND_BONDED for ${device.address} while Advertising — " +
                                    "waiting for host-initiated HID connection."
                            }
                        }
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        // Diagnostic: log ACL connection events for connection lifecycle monitoring.
                        // Do NOT proactively call connect() here — evidence from the Asus phone
                        // (logcat 2026-04-07 18:52) shows that calling connect() on a device with
                        // a stale bond causes the stack to remove the bond entirely
                        // (btif_storage_remove_bonded_device), breaking the pairing flow.
                        @Suppress("DEPRECATION")
                        val device =
                            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                                ?: return

                        Logger.d {
                            "ACL_CONNECTED: device=${device.address} bond=${bondStateName(device.bondState)} " +
                                "currentState=${_connectionState.value::class.simpleName}"
                        }
                    }
                }
            }
        }
    private var isReceiverRegistered = false

    // ── Callbacks ─────────────────────────────────────────────────────────────

    private var initContinuation: CancellableContinuation<Result<Unit>>? = null

    private val serviceListener =
        object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(
                profile: Int,
                proxy: BluetoothProfile,
            ) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = proxy as BluetoothHidDevice
                    Logger.d { "HID_DEVICE profile proxy acquired successfully" }
                    logDiagnosticSnapshot("PROXY_ACQUIRED")
                    initContinuation?.takeIf { it.isActive }?.resume(Result.success(Unit))
                    initContinuation = null
                } else {
                    Logger.w { "onServiceConnected received for profile $profile, expected HID_DEVICE" }
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = null
                    _connectionState.value = HidConnectionState.Idle
                    Logger.d { "HID_DEVICE profile proxy released (service disconnected)" }
                }
            }
        }

    /** Callback dispatched on the executor supplied to [registerApp]. */
    private val hidCallback =
        object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(
                pluggedDevice: BluetoothDevice?,
                registered: Boolean,
            ) {
                Logger.i {
                    "onAppStatusChanged registered=$registered pluggedDevice=${pluggedDevice?.address ?: "null"}"
                }
                logDiagnosticSnapshot("APP_STATUS_CHANGED")

                if (registered) {
                    if (pluggedDevice != null && BluetoothHidConfigProvider.config.isPhantomDisconnectRequired) {
                        // A device is already reported at registration time.
                        // This is a stale socket from a previous session — disconnect it to free
                        // the L2CAP channel for new incoming connections.
                        Logger.w {
                            "Stale pluggedDevice ${pluggedDevice.address} reported at registration. " +
                                "Disconnecting to free L2CAP socket."
                        }
                        try {
                            val isDisconnected = hidDevice?.disconnect(pluggedDevice)
                            Logger.d {
                                "disconnect(${pluggedDevice.address}) result=$isDisconnected — " +
                                    "socket should be freed for new connections."
                            }
                        } catch (e: SecurityException) {
                            Logger.e(e) { "Security error disconnecting stale device ${pluggedDevice.address}" }
                        }
                    }
                    _connectionState.value = HidConnectionState.Advertising
                } else {
                    _connectionState.value = HidConnectionState.Idle
                }
            }

            override fun onConnectionStateChanged(
                device: BluetoothDevice,
                state: Int,
            ) {
                Logger.i {
                    "onConnectionStateChanged device=${device.address} state=${connectionStateName(state)}"
                }
                logDiagnosticSnapshot("CONNECTION_STATE_CHANGED")

                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        val bondState = runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
                        Logger.i { "Device ${device.address} connected — bondState=${bondStateName(bondState)}" }
                        when (bondState) {
                            BluetoothDevice.BOND_NONE -> {
                                // No link key at all — reject immediately.
                                Logger.w {
                                    "Rejecting connection from completely unbonded device ${device.address}. " +
                                        "No link key present — device has never paired."
                                }
                                try {
                                    hidDevice?.disconnect(device)
                                } catch (e: SecurityException) {
                                    Logger.e(e) { "Failed to disconnect unbonded device" }
                                }
                            }
                            BluetoothDevice.BOND_BONDING -> {
                                // Link-key exchange still in progress. Defer acceptance until
                                // BOND_BONDED arrives via ACTION_BOND_STATE_CHANGED.
                                Logger.d {
                                    "Device ${device.address} is still bonding — " +
                                        "deferring connection acceptance until BOND_BONDED."
                                }
                                pendingBondDevice = device
                            }
                            else -> {
                                // BOND_BONDED — link is encrypted; accept immediately.
                                acceptConnectedDevice(device)
                            }
                        }
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Logger.d { "Device disconnected: ${device.address}" }
                        if (pendingBondDevice?.address == device.address) {
                            Logger.d { "Pending bond device ${device.address} disconnected — clearing deferred state." }
                            pendingBondDevice = null
                        }
                        connectedDevice = null
                        _connectionState.value = HidConnectionState.Advertising
                    }

                    BluetoothProfile.STATE_CONNECTING -> {
                        Logger.i { "Device ${device.address} connecting..." }
                        _connectionState.value = HidConnectionState.Connecting(device)
                    }

                    BluetoothProfile.STATE_DISCONNECTING -> {
                        Logger.i { "Device ${device.address} disconnecting..." }
                    }
                }
            }

            /** Called when the host sends a SET_REPORT command (output report). */
            override fun onSetReport(
                device: BluetoothDevice,
                type: Byte,
                id: Byte,
                data: ByteArray,
            ) {
                Logger.d { "onSetReport type=$type id=$id len=${data.size}" }
                incomingReports.trySend(ensureReportSize(data))
            }

            /** Called when the host sends data over the HID interrupt channel. */
            override fun onInterruptData(
                device: BluetoothDevice,
                reportId: Byte,
                data: ByteArray,
            ) {
                Logger.d { "onInterruptData reportId=$reportId len=${data.size}" }
                incomingReports.trySend(ensureReportSize(data))
            }

            override fun onGetReport(
                device: BluetoothDevice,
                type: Byte,
                id: Byte,
                bufferSize: Int,
            ) {
                // Host polling for a report — respond with empty/idle report
                try {
                    hidDevice?.replyReport(device, type, id, ByteArray(FIDO_HID_REPORT_SIZE))
                } catch (e: SecurityException) {
                    Logger.e(e) { "onGetReport: BLUETOOTH_CONNECT permission denied" }
                }
            }

            override fun onVirtualCableUnplug(device: BluetoothDevice) {
                Logger.d { "onVirtualCableUnplug device=${device.address}" }
                connectedDevice = null
                _connectionState.value = HidConnectionState.Advertising
            }
        }

    // ── Internal connection helper ────────────────────────────────────────────

    /**
     * Completes a device connection: sets [connectedDevice], emits [HidConnectionState.Connected],
     * and notifies the transport layer.
     */
    private fun acceptConnectedDevice(device: BluetoothDevice) {
        Logger.i { "Accepting connection from ${device.address}" }
        connectedDevice = device
        _connectionState.value = HidConnectionState.Connected(device)
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
                context.registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(bluetoothStateReceiver, filter)
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
                        initContinuation = cont
                        try {
                            val adapterState = bluetoothAdapter?.state
                            Logger.d { "Bluetooth adapter state before getProfileProxy: $adapterState (STATE_ON=12)" }
                            Logger.d { "Calling getProfileProxy for HID_DEVICE..." }
                            val isSuccess =
                                bluetoothAdapter?.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
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
                                initContinuation = null
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
                            initContinuation = null
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
            val bluetoothState = waitForBluetoothToTurnOn()

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
                        val registrationCallback = createRegistrationCallback(cont)

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
     * Sends a single HID input report to the connected host over the interrupt channel.
     *
     * [data] is padded/truncated to exactly [FIDO_HID_REPORT_SIZE] bytes before dispatch.
     * Pacing (inter-report delay) is handled by the caller ([BluetoothHidTransportImpl]
     * `sendQueueJob`), so this function is a straight pass-through to the OS.
     *
     * @return `true` if the report was dispatched to the BluetoothHidDevice profile,
     *         `false` if the profile proxy or connected device is unavailable.
     */
    fun sendReport(data: ByteArray): Boolean {
        val hid = hidDevice
        val device = connectedDevice

        if (hid == null) {
            Logger.w("sendReport: HID device not available")
            return false
        }
        if (device == null) {
            Logger.w("sendReport: no connected device")
            return false
        }

        val report = ensureReportSize(data)
        return try {
            val isSent = hid.sendReport(device, FIDO_REPORT_ID.toInt(), report)
            Logger.d { "sendReport dispatched len=${report.size} success=$isSent" }
            isSent
        } catch (e: SecurityException) {
            Logger.e(e) { "sendReport: BLUETOOTH_CONNECT permission denied" }
            false
        } catch (e: IllegalStateException) {
            Logger.e(e) { "sendReport: Bluetooth not available — ${e.message}" }
            false
        }
    }

    /** Unregisters the HID app (stops advertising / disconnects host). */
    fun unregisterApp() {
        Logger.d("unregisterApp() called")
        logDiagnosticSnapshot("PRE_UNREGISTER")
        try {
            hidDevice?.unregisterApp()
        } catch (e: SecurityException) {
            Logger.e(e) { "unregisterApp: Bluetooth permission denied" }
        }
        _connectionState.value = HidConnectionState.Idle
        logDiagnosticSnapshot("POST_UNREGISTER")
    }

    /** Releases the profile proxy. Should be called from Application.onTerminate. */
    fun close() {
        Logger.d("close() called")
        unregisterApp()
        if (isReceiverRegistered) {
            runCatching { context.unregisterReceiver(bluetoothStateReceiver) }
                .onFailure { Logger.w { "close: failed to unregister BT state receiver: ${it.message}" } }
            isReceiverRegistered = false
        }
        try {
            hidDevice?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        } catch (e: SecurityException) {
            Logger.e(e) { "close: Bluetooth permission denied" }
        }
        hidDevice = null
        incomingReports.close()
    }

    /** Returns true if a Bluetooth host is currently connected. */
    fun isConnected(): Boolean = _connectionState.value is HidConnectionState.Connected

    /**
     * Transitions the HID state to [HidConnectionState.Error].
     * Used to propagate external transport or registration failures to the UI.
     */
    fun reportError(message: String) {
        _connectionState.value = HidConnectionState.Error(message)
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun ensureReportSize(data: ByteArray): ByteArray {
        if (data.size == FIDO_HID_REPORT_SIZE) return data
        val report = ByteArray(FIDO_HID_REPORT_SIZE)
        data.copyInto(report, 0, 0, minOf(data.size, FIDO_HID_REPORT_SIZE))
        return report
    }

    private suspend fun waitForBluetoothToTurnOn(): Int {
        var bluetoothState =
            try {
                bluetoothAdapter?.state ?: BluetoothAdapter.ERROR
            } catch (e: SecurityException) {
                Logger.e(e) { "registerApp: Failed to query Bluetooth state (permission denied)" }
                BluetoothAdapter.ERROR
            }
        var waitAttempt = 0
        while (bluetoothState == BluetoothAdapter.STATE_TURNING_ON &&
            waitAttempt < BLUETOOTH_TURNING_ON_WAIT_ATTEMPTS
        ) {
            Logger.d {
                "Bluetooth is turning on, waiting $BLUETOOTH_TURNING_ON_WAIT_DELAY " +
                    "(attempt ${waitAttempt + 1})..."
            }
            delay(BLUETOOTH_TURNING_ON_WAIT_DELAY)
            bluetoothState =
                try {
                    bluetoothAdapter?.state ?: BluetoothAdapter.ERROR
                } catch (e: SecurityException) {
                    Logger.e(e) { "registerApp: Failed to query Bluetooth state (permission denied) during wait" }
                    BluetoothAdapter.ERROR
                }
            waitAttempt++
        }
        return bluetoothState
    }

    private fun createRegistrationCallback(cont: CancellableContinuation<Result<Unit>>) =
        object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(
                pluggedDevice: BluetoothDevice?,
                registered: Boolean,
            ) {
                hidCallback.onAppStatusChanged(pluggedDevice, registered)
                if (registered) {
                    Logger.i { "registerApp: onAppStatusChanged registered=true — registration confirmed" }
                    if (cont.isActive) cont.resume(Result.success(Unit))
                } else {
                    Logger.w { "registerApp: onAppStatusChanged registered=false — registration lost" }
                    if (cont.isActive) {
                        cont.resume(
                            Result.failure(
                                Fido2Exception.BluetoothException("HID app registration failed (registered=false)"),
                            ),
                        )
                    }
                }
            }

            override fun onConnectionStateChanged(
                device: BluetoothDevice,
                state: Int,
            ) = hidCallback.onConnectionStateChanged(device, state)

            override fun onSetReport(
                device: BluetoothDevice,
                type: Byte,
                id: Byte,
                data: ByteArray,
            ) = hidCallback.onSetReport(device, type, id, data)

            override fun onInterruptData(
                device: BluetoothDevice,
                reportId: Byte,
                data: ByteArray,
            ) = hidCallback.onInterruptData(device, reportId, data)

            override fun onGetReport(
                device: BluetoothDevice,
                type: Byte,
                id: Byte,
                bufferSize: Int,
            ) = hidCallback.onGetReport(device, type, id, bufferSize)

            override fun onVirtualCableUnplug(device: BluetoothDevice) = hidCallback.onVirtualCableUnplug(device)
        }

    companion object {
        private const val BLUETOOTH_TURNING_ON_WAIT_ATTEMPTS = 10
        private val BLUETOOTH_TURNING_ON_WAIT_DELAY = 500.milliseconds
    }
}

// ── State sealed class ────────────────────────────────────────────────────────

/** Represents the HID peripheral connection lifecycle. */
sealed class HidConnectionState {
    /** Profile proxy acquired but app not yet registered. */
    object Idle : HidConnectionState()

    /** App registered; advertising to Bluetooth hosts. */
    object Advertising : HidConnectionState()

    /** A host is in the process of connecting. */
    data class Connecting(
        val device: BluetoothDevice,
    ) : HidConnectionState()

    /** A host is connected and can exchange HID reports. */
    data class Connected(
        val device: BluetoothDevice,
    ) : HidConnectionState()

    /** An unrecoverable error occurred. */
    data class Error(
        val message: String,
    ) : HidConnectionState()
}

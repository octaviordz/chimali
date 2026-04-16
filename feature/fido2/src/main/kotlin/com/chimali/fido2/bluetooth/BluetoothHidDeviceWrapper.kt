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
import com.chimali.fido2.domain.exception.Fido2Exception
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ── FIDO2 HID report constants ────────────────────────────────────────────────

/**
 * Report descriptor for a FIDO2 HID authenticator interface.
 */
// Note: The report count is 0x3E (62 bytes) instead of 64.
// Bluetooth Classic L2CAP MTU for HID interrupt is capped at 64 bytes.
// With 1 byte HID header + 1 byte Report ID, the payload must be 62.
// Windows 11 bthid.sys strictly enforces this and returns ERROR_NOT_SUPPORTED (0x32)
// if it is asked to fragment an Output Report over L2CAP.
@Suppress("MagicNumber")
private val FIDO_HID_REPORT_DESCRIPTOR =
    byteArrayOf(
        // Usage Page (FIDO Alliance)
        0x06.toByte(), 0xD0.toByte(), 0xF1.toByte(),
        // Usage (FIDO Usage Data In)
        0x09.toByte(), 0x01.toByte(),
        // Collection (Application)
        0xA1.toByte(), 0x01.toByte(),
        // ── Input Report ──
        0x09.toByte(), 0x20.toByte(), // Usage (Input Report Data)
        0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
        0x26.toByte(), 0xFF.toByte(), 0x00.toByte(), // Logical Maximum (255)
        0x75.toByte(), 0x08.toByte(), // Report Size (8 bits)
        0x95.toByte(), 0x3E.toByte(), // Report Count (62)
        0x81.toByte(), 0x02.toByte(), // Input (Data, Variable, Absolute)
        // ── Output Report ──
        0x09.toByte(), 0x21.toByte(), // Usage (Output Report Data)
        0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
        0x26.toByte(), 0xFF.toByte(), 0x00.toByte(), // Logical Maximum (255)
        0x75.toByte(), 0x08.toByte(), // Report Size (8 bits)
        0x95.toByte(), 0x3E.toByte(), // Report Count (62)
        0x91.toByte(), 0x02.toByte(), // Output (Data, Variable, Absolute)
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
@Singleton
class BluetoothHidDeviceWrapper
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
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
                Timber.d("[DIAG:%s] hidDevice=null, _connectionState=%s", tag, _connectionState.value)
                return
            }

            try {
                val connectedDevices = hid.getConnectedDevices()
                val bondedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()

                Timber.i(
                    "[DIAG:%s] _connectionState=%s, connectedDevice=%s, pendingBondDevice=%s",
                    tag,
                    _connectionState.value,
                    connectedDevice?.address,
                    pendingBondDevice?.address,
                )
                Timber.i(
                    "[DIAG:%s] getConnectedDevices()=[%s] (count=%d)",
                    tag,
                    connectedDevices.joinToString { "${it.address}(state=${hid.getConnectionState(it)})" },
                    connectedDevices.size,
                )
                Timber.i(
                    "[DIAG:%s] bondedDevices=[%s] (count=%d)",
                    tag,
                    bondedDevices.joinToString { "${it.address}(bond=${it.bondState})" },
                    bondedDevices.size,
                )
            } catch (e: SecurityException) {
                Timber.w(e, "[DIAG:%s] SecurityException reading diagnostic state", tag)
            } catch (e: Exception) {
                Timber.w(e, "[DIAG:%s] Exception reading diagnostic state", tag)
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
                            Timber.d("BluetoothAdapter state changed: %d", state)
                            if (state == BluetoothAdapter.STATE_OFF) {
                                Timber.w("Bluetooth turned OFF — resetting HID proxy and connection state")
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
                            Timber.i(
                                "Bond state changed: device=%s %s→%s",
                                device.address,
                                bondStateName(previousBondState),
                                bondStateName(bondState),
                            )

                            // Log the HID connection state of this device
                            val hid = hidDevice
                            if (hid != null) {
                                try {
                                    val hidState = hid.getConnectionState(device)
                                    Timber.i(
                                        "  HID connection state for %s: %s",
                                        device.address,
                                        connectionStateName(hidState),
                                    )
                                } catch (e: Exception) {
                                    Timber.w(e, "  Could not query HID connection state for %s", device.address)
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
                                Timber.i(
                                    "Pending device %s is now BOND_BONDED — completing deferred connection.",
                                    device.address,
                                )
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
                                Timber.i(
                                    "BOND_BONDED for %s while Advertising — waiting for host-initiated HID connection.",
                                    device.address,
                                )
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

                            Timber.d(
                                "ACL_CONNECTED: device=%s bond=%s currentState=%s",
                                device.address,
                                bondStateName(device.bondState),
                                _connectionState.value::class.simpleName,
                            )
                        }
                    }
                }
            }
        private var receiverRegistered = false

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
                        Timber.d("HID_DEVICE profile proxy acquired successfully")
                        logDiagnosticSnapshot("PROXY_ACQUIRED")
                        initContinuation?.takeIf { it.isActive }?.resume(Result.success(Unit))
                        initContinuation = null
                    } else {
                        Timber.w("onServiceConnected received for profile %d, expected HID_DEVICE", profile)
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        hidDevice = null
                        _connectionState.value = HidConnectionState.Idle
                        Timber.d("HID_DEVICE profile proxy released (service disconnected)")
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
                    Timber.i(
                        "onAppStatusChanged registered=%b pluggedDevice=%s",
                        registered,
                        pluggedDevice?.address ?: "null",
                    )
                    logDiagnosticSnapshot("APP_STATUS_CHANGED")

                    if (registered) {
                        if (pluggedDevice != null && BluetoothHidConfigProvider.config.requiresPhantomDisconnect) {
                            // A device is already reported at registration time.
                            // This is a stale socket from a previous session — disconnect it to free
                            // the L2CAP channel for new incoming connections.
                            Timber.w(
                                "Stale pluggedDevice %s reported at registration. " +
                                    "Disconnecting to free L2CAP socket.",
                                pluggedDevice.address,
                            )
                            try {
                                val disconnected = hidDevice?.disconnect(pluggedDevice)
                                Timber.d(
                                    "disconnect(%s) result=%b — socket should be freed for new connections.",
                                    pluggedDevice.address,
                                    disconnected,
                                )
                            } catch (e: SecurityException) {
                                Timber.e(e, "Security error disconnecting stale device %s", pluggedDevice.address)
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
                    Timber.i(
                        "onConnectionStateChanged device=%s state=%s",
                        device.address,
                        connectionStateName(state),
                    )
                    logDiagnosticSnapshot("CONNECTION_STATE_CHANGED")

                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            val bondState = runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
                            Timber.i(
                                "Device %s connected — bondState=%s",
                                device.address,
                                bondStateName(bondState),
                            )
                            when (bondState) {
                                BluetoothDevice.BOND_NONE -> {
                                    // No link key at all — reject immediately.
                                    Timber.w(
                                        "Rejecting connection from completely unbonded device %s. " +
                                            "No link key present — device has never paired.",
                                        device.address,
                                    )
                                    try {
                                        hidDevice?.disconnect(device)
                                    } catch (e: SecurityException) {
                                        Timber.e(e, "Failed to disconnect unbonded device")
                                    }
                                }
                                BluetoothDevice.BOND_BONDING -> {
                                    // Link-key exchange still in progress. Defer acceptance until
                                    // BOND_BONDED arrives via ACTION_BOND_STATE_CHANGED.
                                    Timber.d(
                                        "Device %s is still bonding — deferring connection acceptance until BOND_BONDED.",
                                        device.address,
                                    )
                                    pendingBondDevice = device
                                }
                                else -> {
                                    // BOND_BONDED — link is encrypted; accept immediately.
                                    acceptConnectedDevice(device)
                                }
                            }
                        }

                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Timber.d("Device disconnected: %s", device.address)
                            if (pendingBondDevice?.address == device.address) {
                                Timber.d("Pending bond device %s disconnected — clearing deferred state.", device.address)
                                pendingBondDevice = null
                            }
                            connectedDevice = null
                            _connectionState.value = HidConnectionState.Advertising
                        }

                        BluetoothProfile.STATE_CONNECTING -> {
                            Timber.i("Device %s connecting...", device.address)
                            _connectionState.value = HidConnectionState.Connecting(device)
                        }

                        BluetoothProfile.STATE_DISCONNECTING -> {
                            Timber.i("Device %s disconnecting...", device.address)
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
                    Timber.d("onSetReport type=%d id=%d len=%d", type, id, data.size)
                    incomingReports.trySend(ensureReportSize(data))
                }

                /** Called when the host sends data over the HID interrupt channel. */
                override fun onInterruptData(
                    device: BluetoothDevice,
                    reportId: Byte,
                    data: ByteArray,
                ) {
                    Timber.d("onInterruptData reportId=%d len=%d", reportId, data.size)
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
                        Timber.e(e, "onGetReport: BLUETOOTH_CONNECT permission denied")
                    }
                }

                override fun onVirtualCableUnplug(device: BluetoothDevice) {
                    Timber.d("onVirtualCableUnplug device=%s", device.address)
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
            Timber.i("Accepting connection from %s", device.address)
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
            Timber.d("initialize() called. Current hidDevice: %s", hidDevice)
            if (hidDevice != null) return Result.success(Unit)

            // Register adapter-state receiver for BT hardware toggle and bond-state changes.
            if (!receiverRegistered) {
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
                receiverRegistered = true
                Timber.d(
                    "BluetoothAdapter state + bond-state receiver registered (exported=%b)",
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU,
                )
            }

            var lastException: Exception? = null
            val cfg = BluetoothHidConfigProvider.config
            val maxRetries = cfg.initMaxRetries
            var retryDelay = cfg.initRetryDelayMs

            for (attempt in 1..maxRetries) {
                Timber.d("initialize attempt %d/%d", attempt, maxRetries)

                val result =
                    withTimeoutOrNull(cfg.initTimeoutMs) {
                        suspendCancellableCoroutine { cont ->
                            initContinuation = cont
                            try {
                                val adapterState = bluetoothAdapter?.state
                                Timber.d("Bluetooth adapter state before getProfileProxy: %s (STATE_ON=12)", adapterState)
                                Timber.d("Calling getProfileProxy for HID_DEVICE...")
                                val success =
                                    bluetoothAdapter?.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
                                        ?: false
                                Timber.d("getProfileProxy returned: %b", success)
                                if (!success) {
                                    Timber.e("getProfileProxy returned false - Bluetooth may be off or profile unsupported")
                                    cont.resumeWithException(
                                        Fido2Exception.BluetoothException("Failed to request HID proxy. Is Bluetooth on?"),
                                    )
                                    initContinuation = null
                                } else {
                                    Timber.d("getProfileProxy returned true - waiting for onServiceConnected callback...")
                                }
                            } catch (e: SecurityException) {
                                Timber.e(e, "SecurityException in getProfileProxy - missing BLUETOOTH_CONNECT permission?")
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
                        Timber.e("initialize() timed out after %dms - onServiceConnected never received.", cfg.initTimeoutMs)
                        lastException =
                            Fido2Exception.BluetoothException("HID proxy acquisition timed out (onServiceConnected never fired)")
                    }

                    result.isSuccess -> {
                        Timber.i("initialize() succeeded on attempt %d", attempt)
                        return Result.success(Unit)
                    }

                    else -> {
                        lastException = result.exceptionOrNull() as? Exception
                        Timber.w("initialize() failed on attempt %d: %s", attempt, lastException?.message)
                        // If it's a permission error, don't retry — user action needed
                        if (lastException is Fido2Exception.BluetoothPermissionDenied) {
                            return Result.failure(lastException)
                        }
                    }
                }

                if (attempt < maxRetries) {
                    Timber.d("Retrying initialize() in %dms...", retryDelay)
                    delay(retryDelay)
                    retryDelay *= 2
                }
            }

            Timber.e("initialize() failed after %d attempts", maxRetries)
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
            var retryDelay = cfg.registerRetryDelayMs
            var lastException: Exception? = null

            for (attempt in 1..maxRetries) {
                Timber.d("registerApp attempt %d/%d", attempt, maxRetries)
                logDiagnosticSnapshot("PRE_REGISTER_$attempt")

                val result =
                    withTimeoutOrNull(cfg.registerTimeoutMs) {
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

                            val isEnabled =
                                try {
                                    val enabled = bluetoothAdapter?.isEnabled == true
                                    Timber.d("Bluetooth adapter enabled: %b", enabled)
                                    enabled
                                } catch (e: SecurityException) {
                                    Timber.e(e, "SecurityException checking Bluetooth enabled state")
                                    cont.resume(
                                        Result.failure(
                                            Fido2Exception.BluetoothPermissionDenied("BLUETOOTH_CONNECT permission denied", e),
                                        ),
                                    )
                                    return@suspendCancellableCoroutine
                                }
                            if (!isEnabled) {
                                Timber.w("Bluetooth is disabled, cannot register HID app")
                                cont.resume(
                                    Result.failure(
                                        Fido2Exception.BluetoothException("Bluetooth is disabled"),
                                    ),
                                )
                                return@suspendCancellableCoroutine
                            }

                            // ── Step 1: Clear any stale registration ──
                            // Best practice: call unregisterApp() before registerApp() to ensure
                            // no previous app instance (same or different process) holds the
                            // exclusive HID_DEVICE registration slot.
                            Timber.d("Step 1: Calling unregisterApp() to clear any stale registration...")
                            try {
                                val unregResult = hid.unregisterApp()
                                Timber.d("unregisterApp() result: %b", unregResult)
                            } catch (e: Exception) {
                                Timber.w(e, "unregisterApp() threw (non-fatal, proceeding with registerApp)")
                            }

                            logDiagnosticSnapshot("POST_UNREGISTER_$attempt")

                            // ── Step 2: Register with fresh callback ──
                            val sdpSubclass = BluetoothHidDevice.SUBCLASS1_COMBO
                            Timber.d("Step 2: Using SUBCLASS1_COMBO...")

                            val sdp =
                                BluetoothHidDeviceAppSdpSettings(
                                    "Chimali Authenticator",
                                    "FIDO2 Virtual Security Key",
                                    "Chimali",
                                    sdpSubclass,
                                    FIDO_HID_REPORT_DESCRIPTOR,
                                )

                            // Wrapper callback that resolves the continuation on app status change
                            val registrationCallback =
                                object : BluetoothHidDevice.Callback() {
                                    override fun onAppStatusChanged(
                                        pluggedDevice: BluetoothDevice?,
                                        registered: Boolean,
                                    ) {
                                        // Delegate to our persistent callback for state management
                                        hidCallback.onAppStatusChanged(pluggedDevice, registered)

                                        if (registered) {
                                            Timber.i("registerApp: onAppStatusChanged registered=true — registration confirmed")
                                            if (cont.isActive) cont.resume(Result.success(Unit))
                                        } else {
                                            Timber.w("registerApp: onAppStatusChanged registered=false — registration lost")
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

                            val registerResult =
                                try {
                                    Timber.d("Step 3: Calling registerApp with SDP subclass COMBO and null QoS...")
                                    val callResult =
                                        hid.registerApp(
                                            sdp,
                                            null,
                                            null,
                                            Executors.newSingleThreadExecutor(),
                                            registrationCallback,
                                        )
                                    Timber.i("registerApp() framework return value: %b", callResult)
                                    callResult
                                } catch (e: SecurityException) {
                                    Timber.e(e, "SecurityException in registerApp")
                                    cont.resume(
                                        Result.failure(
                                            Fido2Exception.BluetoothPermissionDenied("BLUETOOTH_ADVERTISE permission denied", e),
                                        ),
                                    )
                                    return@suspendCancellableCoroutine
                                } catch (e: Exception) {
                                    Timber.e(e, "Unexpected Exception in registerApp")
                                    cont.resume(
                                        Result.failure(
                                            Fido2Exception.BluetoothException("Unexpected error during app registration: ${e.message}"),
                                        ),
                                    )
                                    return@suspendCancellableCoroutine
                                }

                            logDiagnosticSnapshot("POST_REGISTER_CALL_$attempt")

                            if (!registerResult) {
                                // registerApp() returned false — the request was not accepted by the
                                // system service. This typically means another app or stale process
                                // already holds the exclusive HID_DEVICE registration.
                                Timber.w(
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
                        Timber.i("registerApp() succeeded on attempt %d", attempt)
                        logDiagnosticSnapshot("REGISTER_SUCCESS")
                        return Result.success(Unit)
                    }
                    result == null -> {
                        Timber.e("registerApp timed out after %dms on attempt %d", cfg.registerTimeoutMs, attempt)
                        lastException = Fido2Exception.BluetoothException("HID registration timed out")
                        logDiagnosticSnapshot("REGISTER_TIMEOUT_$attempt")
                    }
                    else -> {
                        lastException = result.exceptionOrNull() as? Exception
                        Timber.w("registerApp failure on attempt %d: %s", attempt, lastException?.message)
                        logDiagnosticSnapshot("REGISTER_FAILURE_$attempt")
                    }
                }

                if (attempt < maxRetries) {
                    Timber.d("Retrying registerApp in %dms...", retryDelay)
                    delay(retryDelay)
                    retryDelay = minOf(retryDelay * 2, cfg.registerRetryMaxDelayMs)
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
                Timber.w("sendReport: HID device not available")
                return false
            }
            if (device == null) {
                Timber.w("sendReport: no connected device")
                return false
            }

            val report = ensureReportSize(data)
            return try {
                val sent = hid.sendReport(device, FIDO_REPORT_ID.toInt(), report)
                Timber.d("sendReport dispatched len=%d success=%b", report.size, sent)
                sent
            } catch (e: SecurityException) {
                Timber.e(e, "sendReport: BLUETOOTH_CONNECT permission denied")
                false
            } catch (e: Exception) {
                Timber.e(e, "sendReport: exception — %s", e.message)
                false
            }
        }

        /** Unregisters the HID app (stops advertising / disconnects host). */
        fun unregisterApp() {
            Timber.d("unregisterApp() called")
            logDiagnosticSnapshot("PRE_UNREGISTER")
            try {
                hidDevice?.unregisterApp()
            } catch (e: SecurityException) {
                Timber.e(e, "unregisterApp: Bluetooth permission denied")
            }
            _connectionState.value = HidConnectionState.Idle
            logDiagnosticSnapshot("POST_UNREGISTER")
        }

        /** Releases the profile proxy. Should be called from Application.onTerminate. */
        fun close() {
            Timber.d("close() called")
            unregisterApp()
            if (receiverRegistered) {
                runCatching { context.unregisterReceiver(bluetoothStateReceiver) }
                    .onFailure { Timber.w("close: failed to unregister BT state receiver: %s", it.message) }
                receiverRegistered = false
            }
            try {
                hidDevice?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
            } catch (e: SecurityException) {
                Timber.e(e, "close: Bluetooth permission denied")
            }
            hidDevice = null
            incomingReports.close()
        }

        /** Returns true if a Bluetooth host is currently connected. */
        fun isConnected(): Boolean = _connectionState.value is HidConnectionState.Connected

        // ── Internal helpers ──────────────────────────────────────────────────────

        private fun ensureReportSize(data: ByteArray): ByteArray {
            if (data.size == FIDO_HID_REPORT_SIZE) return data
            val report = ByteArray(FIDO_HID_REPORT_SIZE)
            data.copyInto(report, 0, 0, minOf(data.size, FIDO_HID_REPORT_SIZE))
            return report
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
    data class Connecting(val device: BluetoothDevice) : HidConnectionState()

    /** A host is connected and can exchange HID reports. */
    data class Connected(val device: BluetoothDevice) : HidConnectionState()

    /** An unrecoverable error occurred. */
    data class Error(val message: String) : HidConnectionState()
}

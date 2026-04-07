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

/**
 * FIDO2 HID descriptor per FIDO Alliance CTAP HID spec (section 8).
 * Usage Page 0xF1D0 (FIDO Alliance), Usage 0x01 (U2F Authenticator Device).
 * Reports are 64 bytes (report ID 0).
 */
// Android's Classic BT HID L2CAP MTU is hard-capped at 64 bytes total.
// The HIDP layer consumes 2 bytes (protocol header + report ID), leaving
// only 62 bytes available for the actual HID payload. Per wiokey-android.
private val FIDO_HID_REPORT_DESCRIPTOR = byteArrayOf(
    0x06.toByte(), 0xD0.toByte(), 0xF1.toByte(), // Usage Page (FIDO Alliance)
    0x09.toByte(), 0x01.toByte(),                  // Usage (U2F Authenticator Device)
    0xA1.toByte(), 0x01.toByte(),                  // Collection (Application)
    0x09.toByte(), 0x20.toByte(),                  //   Usage (Input Report Data)
    0x15.toByte(), 0x00.toByte(),                  //   Logical Minimum (0)
    0x26.toByte(), 0xFF.toByte(), 0x00.toByte(),   //   Logical Maximum (255)
    0x75.toByte(), 0x08.toByte(),                  //   Report Size (8)
    0x95.toByte(), 0x3E.toByte(),                  //   Report Count (62) — MTU cap
    0x81.toByte(), 0x02.toByte(),                  //   Input (Data, Var, Abs)
    0x09.toByte(), 0x21.toByte(),                  //   Usage (Output Report Data)
    0x15.toByte(), 0x00.toByte(),                  //   Logical Minimum (0)
    0x26.toByte(), 0xFF.toByte(), 0x00.toByte(),   //   Logical Maximum (255)
    0x75.toByte(), 0x08.toByte(),                  //   Report Size (8)
    0x95.toByte(), 0x3E.toByte(),                  //   Report Count (62) — MTU cap
    0x91.toByte(), 0x02.toByte(),                  //   Output (Data, Var, Abs)
    0xC0.toByte()                                   // End Collection
)

/**
 * HID report payload size — 62 bytes (not 64) due to Android L2CAP MTU cap.
 * Android's Classic HID over L2CAP SCO has a hard 64-byte MTU. The HIDP
 * layer consumes 2 bytes for the protocol header and report ID, leaving
 * exactly 62 bytes for the FIDO HID payload. Per wiokey-android reference.
 */
const val FIDO_HID_REPORT_SIZE = 62

/** Report ID 0 — FIDO2 HID uses no report ID prefix (report ID 0 means bare data) */
private const val FIDO_REPORT_ID: Byte = 0

/**
 * Wraps the Android [BluetoothHidDevice] profile to act as a FIDO2 HID
 * **peripheral/server**. The phone presents itself as a FIDO2 hardware
 * authenticator to any Bluetooth Classic host (e.g. Windows).
 *
 * Lifecycle
 * ---------
 * 1. Call [initialize] once to acquire the [BluetoothHidDevice] proxy via
 *    [BluetoothProfile.ServiceListener].
 * 2. Call [registerApp] to SDP-register the FIDO2 HID application and start
 *    advertising.
 * 3. The host pairs and connects; [connectionState] transitions accordingly.
 * 4. Received HID interrupt-channel packets arrive on [incomingReports].
 * 5. Call [sendReport] to push a response to the host.
 * 6. Call [unregisterApp] / [close] on cleanup.
 */
@Singleton
class BluetoothHidDeviceWrapper @Inject constructor(
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
     * Device to reconnect to once the current L2CAP channel has fully torn down.
     *
     * Set in [BluetoothHidDevice.Callback.onAppStatusChanged] when a stale socket is cleared
     * via [BluetoothHidDevice.disconnect]. The actual [BluetoothHidDevice.connect] call is
     * deferred until [BluetoothHidDevice.Callback.onConnectionStateChanged] fires with
     * [BluetoothProfile.STATE_DISCONNECTED], confirming the channel is fully released.
     *
     * This sequencing is required on OEM stacks (confirmed: Motorola) where calling
     * `connect()` immediately after `disconnect()` races with the HCI teardown and causes
     * the outbound connection attempt to time out after ~5 s.
     */
    @Volatile private var pendingReconnectDevice: BluetoothDevice? = null

    /**
     * Channel that receives raw 64-byte HID reports from the host.
     * Consumers (e.g. [HidReportParser]) should collect from this channel.
     */
    val incomingReports: Channel<ByteArray> = Channel(capacity = Channel.UNLIMITED)

    // ── Bluetooth adapter state broadcast receiver ────────────────────────────

    /**
     * Tears down the HID proxy and resets connection state when the user
     * disables Bluetooth (STATE_OFF). This prevents a stale [hidDevice] reference
     * from causing silent deadlocks on the next [connect] call.
     *
     * Also handles [BluetoothDevice.ACTION_BOND_STATE_CHANGED]: when a previously
     * [pendingBondDevice] finishes bonding ([BluetoothDevice.BOND_BONDED]), the
     * deferred connection acceptance is completed here.
     *
     * Registered dynamically in [initialize] and unregistered in [close].
     */
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
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
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        ?: return
                    val bondState = intent.getIntExtra(
                        BluetoothDevice.EXTRA_BOND_STATE,
                        BluetoothDevice.BOND_NONE,
                    )
                    Timber.d("Bond state changed: device=%s bondState=%d", device.address, bondState)
                    // Complete the deferred connection if a pending device is now fully bonded.
                    val pending = pendingBondDevice
                    if (bondState == BluetoothDevice.BOND_BONDED &&
                        pending != null &&
                        pending.address == device.address
                    ) {
                        Timber.i(
                            "Pending device %s is now BOND_BONDED — completing deferred connection.",
                            device.address,
                        )
                        pendingBondDevice = null
                        acceptConnectedDevice(device)
                    }
                }
            }
        }
    }
    private var receiverRegistered = false

    // ── Callbacks ─────────────────────────────────────────────────────────────

    private var initContinuation: kotlinx.coroutines.CancellableContinuation<Result<Unit>>? = null

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                Timber.d("HID_DEVICE profile proxy acquired successfully")
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
    private val hidCallback = object : BluetoothHidDevice.Callback() {

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Timber.d("onAppStatusChanged registered=%b device=%s", registered, pluggedDevice)
            if (registered) {
                if (pluggedDevice != null) {
                    val isPhantomQuirk = BluetoothQuirks.requiresPhantomDeviceDisconnect()
                    Timber.w(
                        "Device reported upon registration: %s (phantomQuirk=%b).",
                        pluggedDevice.address,
                        isPhantomQuirk,
                    )
                    // In all cases — phantom-quirk OEMs (Motorola) or standard stacks —
                    // disconnect the stale L2CAP socket and schedule a reconnect.
                    //
                    // IMPORTANT: We do NOT call connect() here immediately. On slower OEM
                    // stacks (Motorola) the HCI teardown is still in progress when this
                    // callback fires, so a back-to-back disconnect→connect races and times
                    // out after ~5 s.  Instead we store the device in pendingReconnectDevice
                    // and let onConnectionStateChanged(DISCONNECTED) fire connect() once the
                    // channel is confirmed fully released.  This mirrors wiokey-android's
                    // waitingForDevice pattern in HidDeviceController.updateDeviceList().
                    try {
                        val disconnected = hidDevice?.disconnect(pluggedDevice)
                        Timber.d(
                            "Cleared stale socket for %s (phantomQuirk=%b) — result: %b. " +
                                "Deferring connect() until DISCONNECTED callback.",
                            pluggedDevice.address,
                            isPhantomQuirk,
                            disconnected,
                        )
                        pendingReconnectDevice = pluggedDevice
                    } catch (e: SecurityException) {
                        Timber.e(e, "Security error clearing stale socket for %s", pluggedDevice.address)
                    }

                }
                // Return to advertising; onConnectionStateChanged will update state on connect.
                _connectionState.value = HidConnectionState.Advertising
            } else {
                _connectionState.value = HidConnectionState.Idle
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Timber.d("onConnectionStateChanged state=%d device=%s", state, device.address)
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    val bondState = runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
                    when (bondState) {
                        BluetoothDevice.BOND_NONE -> {
                            // No link key at all — reject immediately.
                            Timber.w(
                                "Rejecting connection from completely unbonded device %s. " +
                                    "No link key present — device has never paired with this authenticator.",
                                device.address,
                            )
                            try {
                                hidDevice?.disconnect(device)
                            } catch (e: SecurityException) {
                                Timber.e(e, "Failed to disconnect unbonded device")
                            }
                        }
                        BluetoothDevice.BOND_BONDING -> {
                            // Link-key exchange still in progress. Accepting now would allow
                            // FIDO2 operations over an unencrypted channel, causing Windows
                            // to intermittently fail with 0x8007000d (ERROR_INVALID_DATA).
                            // Park the device and complete the connection once BOND_BONDED
                            // arrives via ACTION_BOND_STATE_CHANGED in bluetoothStateReceiver.
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

                    // If a reconnect was deferred from onAppStatusChanged (stale-socket
                    // clearance), now that the channel is fully down we can safely connect.
                    val reconnectTarget = pendingReconnectDevice
                    if (reconnectTarget != null && reconnectTarget.address == device.address) {
                        pendingReconnectDevice = null
                        try {
                            val result = hidDevice?.connect(reconnectTarget)
                            Timber.i(
                                "Deferred connect(%s) result=%b — channel was clear.",
                                reconnectTarget.address,
                                result,
                            )
                        } catch (e: SecurityException) {
                            Timber.e(e, "Security error during deferred connect for %s", reconnectTarget.address)
                        }
                    }
                }

                BluetoothProfile.STATE_CONNECTING -> {
                    _connectionState.value = HidConnectionState.Connecting(device)
                }
            }
        }

        /** Called when the host sends a SET_REPORT command (output report). */
        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            Timber.d("onSetReport type=%d id=%d len=%d", type, id, data.size)
            incomingReports.trySend(ensureReportSize(data))
        }

        /** Called when the host sends data over the HID interrupt channel. */
        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            Timber.d("onInterruptData reportId=%d len=%d", reportId, data.size)
            incomingReports.trySend(ensureReportSize(data))
        }

        override fun onGetReport(
            device: BluetoothDevice,
            type: Byte,
            id: Byte,
            bufferSize: Int
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
     *
     * **No initial zero keepalive is sent.** Wiokey-android (a reference FIDO2 HID peripheral
     * implementation that does not exhibit the Windows 0x8007000d error) never sends any HID
     * report immediately on connect. The all-zeros report Chimali used to send is a malformed
     * CTAPHID packet (CID=0x00000000, CMD=0x00) that Windows's CTAPHID parser can misinterpret
     * as a stale continuation packet, contributing to intermittent ERROR_INVALID_DATA errors.
     * The "Windows keepalive" concern is handled at the CTAPHID protocol level via KEEPALIVE
     * packets sent during CBOR command processing.
     */
    private fun acceptConnectedDevice(device: BluetoothDevice) {
        Timber.i("Accepting connection from %s", device.address)
        connectedDevice = device
        _connectionState.value = HidConnectionState.Connected(device)
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

        // T2d — Register adapter-state receiver so we can react to BT hardware toggle
        // and bond-state changes.
        if (!receiverRegistered) {
            context.registerReceiver(
                bluetoothStateReceiver,
                IntentFilter().apply {
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                    addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                },
            )
            receiverRegistered = true
            Timber.d("BluetoothAdapter state + bond-state receiver registered")
        }

        var lastException: Exception? = null
        val maxRetries = 3
        var retryDelay = 1000L

        for (attempt in 1..maxRetries) {
            Timber.d("initialize attempt %d/%d", attempt, maxRetries)

            val result = withTimeoutOrNull(5000L) {
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
                            cont.resumeWithException(Fido2Exception.BluetoothException("Failed to request HID proxy. Is Bluetooth on?"))
                            initContinuation = null
                        } else {
                            Timber.d("getProfileProxy returned true - waiting for onServiceConnected callback...")
                        }
                    } catch (e: SecurityException) {
                        Timber.e(e, "SecurityException in getProfileProxy - missing BLUETOOTH_CONNECT permission?")
                        cont.resumeWithException(
                            Fido2Exception.BluetoothPermissionDenied(
                                "Bluetooth permission denied",
                                e
                            )
                        )
                        initContinuation = null
                    }
                }
            }

            when {
                result == null -> {
                    Timber.e("initialize() timed out after 5000ms - onServiceConnected never received. OEM stack may require a retry or Bluetooth stack isn't fully up.")
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
            lastException ?: Fido2Exception.BluetoothException("HID proxy acquisition failed after retries")
        )
    }

    /**
     * Registers the FIDO2 HID application and begins advertising so that a
     * Bluetooth host can discover and connect to this authenticator.
     *
     * Suspends until the app is registered (i.e. [BluetoothHidDevice.Callback.onAppStatusChanged] fires
     * with registered=true). Implements a timeout and retry mechanism for
     * unreliable Bluetooth stacks that drop callbacks.
     */
    suspend fun registerApp(): Result<Unit> {
        // NOTE: We deliberately do NOT call unregisterApp() here before registering.
        //
        // On Motorola, the BT daemon keeps the previous session's HID registration alive as
        // a "zombie" in its routing table after the process dies (~66 s cleanup timeout).
        // Calling unregisterApp() from the new session resets the daemon's internal cleanup
        // timer to [now + 66s], which is WORSE — the daemon would have otherwise cleaned up
        // the zombie at [old_process_death + 66s], which may be much sooner.
        //
        // We accept that registerApp() may return false (zombie alive). A zombie state-sync
        // onAppStatusChanged(registered=true) keeps us in "Advertising" mode. When the
        // zombie cleanup eventually fires (registered=false / Idle), the transport's
        // observeConnectionState() auto-reregisters immediately so the next registerApp()
        // call returns true and properly binds our callback to the HID L2CAP server.

        var lastException: Exception? = null
        // Up to 30 attempts × 3 s = 90 s — enough to survive the Motorola BT daemon
        // zombie cleanup window (~66 s from old-process death).  Non-zombie failures
        // (timeout, permission) still use exponential backoff with fewer retries.
        val maxRetries = 30
        val zombieRetryDelayMs = 3_000L
        var normalRetryDelayMs = 1_000L

        for (attempt in 1..maxRetries) {
            Timber.d("registerApp attempt %d/%d", attempt, maxRetries)

            // Track whether the framework call itself returned true.
            // The Motorola BT daemon sends a courtesy onAppStatusChanged(registered=true)
            // callback even when registerApp() returns false (zombie active).  We must
            // NOT treat that zombie sync as a genuine success — doing so would mask the
            // failure and prevent the retry loop from ever running.
            var frameworkReturnedTrue = false
            var wasZombieAttempt = false

            val result = withTimeoutOrNull(5000L) {
                suspendCancellableCoroutine<Result<Unit>> { cont ->
                    val hid = hidDevice
                    if (hid == null) {
                        cont.resume(Result.failure(
                            Fido2Exception.BluetoothException("HID_DEVICE profile not yet acquired")
                        ))
                        return@suspendCancellableCoroutine
                    }

                    val isEnabled = try {
                        val enabled = bluetoothAdapter?.isEnabled == true
                        Timber.d("Bluetooth adapter enabled: %b", enabled)
                        enabled
                    } catch (e: SecurityException) {
                        Timber.e(e, "SecurityException checking Bluetooth enabled state")
                        cont.resume(Result.failure(
                            Fido2Exception.BluetoothPermissionDenied("BLUETOOTH_CONNECT permission denied", e)
                        ))
                        return@suspendCancellableCoroutine
                    }
                    if (!isEnabled) {
                        Timber.w("Bluetooth is disabled, cannot register HID app")
                        cont.resume(Result.failure(
                            Fido2Exception.BluetoothException("Bluetooth is disabled")
                        ))
                        return@suspendCancellableCoroutine
                    }

                    Timber.d("Preparing SDP settings for 'Chimali Authenticator'...")
                    val sdp = BluetoothHidDeviceAppSdpSettings(
                        "Chimali Authenticator",
                        "FIDO2 Virtual Security Key",
                        "Chimali",
                        BluetoothHidDevice.SUBCLASS1_COMBO,
                        FIDO_HID_REPORT_DESCRIPTOR
                    )

                    val registrationCallback = object : BluetoothHidDevice.Callback() {
                        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                            if (!frameworkReturnedTrue && registered) {
                                Timber.w(
                                    "Zombie session claimed by daemon (device=%s). Executing Phantom Flush.",
                                    pluggedDevice?.address
                                )
                                if (pluggedDevice != null) {
                                    try {
                                        // The Phantom Flush Exploit:
                                        // The Motorola daemon refuses to clear pluggedDevice because it has no
                                        // active ACL link, making disconnect() fail silently. By calling connect()
                                        // followed immediately by disconnect(), we force the baseband state machine
                                        // to transition through CONNECTING -> DISCONNECTING, which explicitly
                                        // zeroes out the `plugged_device` reference in BTA_HD, reopening the service
                                        // to incoming connections from Windows.
                                        Timber.d("Flushing BTA_HD cache via connect() -> disconnect().")
                                        hid.connect(pluggedDevice)
                                        hid.disconnect(pluggedDevice)
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error executing Phantom Flush")
                                    }
                                }
                                Timber.i("Phantom Flush complete. Assuming control of HID service.")
                                
                                hidCallback.onAppStatusChanged(null, registered) // Pass null so transport knows it's clear
                                if (cont.isActive) cont.resume(Result.success(Unit))
                                return
                            }

                            // Normal flow for non-zombie or subsequent events
                            hidCallback.onAppStatusChanged(pluggedDevice, registered)
                            if (registered) {
                                if (cont.isActive) cont.resume(Result.success(Unit))
                            } else {
                                if (cont.isActive) cont.resume(Result.failure(
                                    Fido2Exception.BluetoothException("HID app registration failed")
                                ))
                            }
                        }

                        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) =
                            hidCallback.onConnectionStateChanged(device, state)

                        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) =
                            hidCallback.onSetReport(device, type, id, data)

                        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) =
                            hidCallback.onInterruptData(device, reportId, data)

                        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) =
                            hidCallback.onGetReport(device, type, id, bufferSize)

                        override fun onVirtualCableUnplug(device: BluetoothDevice) =
                            hidCallback.onVirtualCableUnplug(device)
                    }

                    val registeredValue = try {
                        Timber.d("Performing registerApp with SDP subclass COMBO and null QoS...")
                        val callResult = hid.registerApp(
                            sdp,
                            null,
                            null,
                            Executors.newSingleThreadExecutor(),
                            registrationCallback
                        )
                        Timber.d("registerApp framework call result: %b", callResult)
                        callResult
                    } catch (e: SecurityException) {
                        Timber.e(e, "SecurityException in registerApp - missing permissions?")
                        cont.resume(Result.failure(
                            Fido2Exception.BluetoothPermissionDenied("BLUETOOTH_ADVERTISE permission denied", e)
                        ))
                        return@suspendCancellableCoroutine
                    } catch (e: Exception) {
                        Timber.e(e, "Unexpected Exception in registerApp")
                        cont.resume(Result.failure(
                            Fido2Exception.BluetoothException("Unexpected error during app registration: ${e.message}")
                        ))
                        return@suspendCancellableCoroutine
                    }

                    frameworkReturnedTrue = registeredValue
                    if (!registeredValue) {
                        // Zombie active! The framework returned false because it is already registered in the daemon.
                        // However, the daemon will dispatch the callback anyway. We wait here for it, without failing.
                        wasZombieAttempt = true
                        Timber.d("Zombie active: waiting for daemon's zombie sync callback to intercept and flush.")
                    }
                }
            }

            when {
                result?.isSuccess == true -> {
                    Timber.i("registerApp successful")
                    return Result.success(Unit)
                }
                wasZombieAttempt -> {
                    // Zombie retry: fixed 3 s interval, no exponential backoff.
                    Timber.d(
                        "Zombie active — retrying in %dms (attempt %d/%d). " +
                            "Daemon will clean up stale session in ~66 s from previous process death.",
                        zombieRetryDelayMs, attempt, maxRetries
                    )
                    if (attempt < maxRetries) delay(zombieRetryDelayMs)
                }
                result == null -> {
                    Timber.e("registerApp timed out after 5000ms on attempt %d", attempt)
                    lastException = Fido2Exception.BluetoothException("HID registration timed out")
                    if (attempt < maxRetries) {
                        delay(normalRetryDelayMs)
                        normalRetryDelayMs = minOf(normalRetryDelayMs * 2, 8_000L)
                    }
                }
                else -> {
                    lastException = result.exceptionOrNull() as? Exception
                    Timber.w("registerApp failure on attempt %d: %s", attempt, lastException?.message)
                    if (attempt < maxRetries) {
                        delay(normalRetryDelayMs)
                        normalRetryDelayMs = minOf(normalRetryDelayMs * 2, 8_000L)
                    }
                }
            }
        }

        return Result.failure(
            lastException ?: Fido2Exception.BluetoothException("HID registration failed after $maxRetries attempts")
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
        try {
            hidDevice?.unregisterApp()
        } catch (e: SecurityException) {
            Timber.e(e, "unregisterApp: Bluetooth permission denied")
        }
        _connectionState.value = HidConnectionState.Idle
    }

    /** Releases the profile proxy. Should be called from Application.onTerminate. */
    fun close() {
        unregisterApp()
        // T2d — Unregister the adapter-state receiver to prevent leaks.
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

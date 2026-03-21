package com.chimali.fido2.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.chimali.fido2.domain.exception.Fido2Exception
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
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

    private val reportQueue = ConcurrentLinkedQueue<ByteArray>()
    private val isSending = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Channel that receives raw 64-byte HID reports from the host.
     * Consumers (e.g. [HidReportParser]) should collect from this channel.
     */
    val incomingReports: Channel<ByteArray> = Channel(capacity = Channel.UNLIMITED)

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
                    Timber.w("Phantom device reported upon registration: %s. Forcing disconnect to clear L2CAP socket.", pluggedDevice.address)
                    try {
                        // Some Android devices (like Moto G) falsely report a connected device 
                        // immediately upon registration, occupying the socket and blocking real connections.
                        // Force a disconnect to clear the state.
                        val disconnected = hidDevice?.disconnect(pluggedDevice)
                        Timber.d("Forced disconnect result: %b", disconnected)
                    } catch (e: SecurityException) {
                        Timber.e(e, "Failed to force disconnect phantom device")
                    }
                }
                // Always return to advertising, waiting for the REAL host connection attempt
                _connectionState.value = HidConnectionState.Advertising
            } else {
                _connectionState.value = HidConnectionState.Idle
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Timber.d("onConnectionStateChanged state=%d device=%s", state, device.address)
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    _connectionState.value = HidConnectionState.Connected(device)
                    // Windows keepalive: some drivers instantly drop idle L2CAP connections.
                    // Send an empty HID report immediately so they know the device is active.
                    try {
                        val report = ByteArray(FIDO_HID_REPORT_SIZE)
                        val sent = hidDevice?.sendReport(device, FIDO_REPORT_ID.toInt(), report)
                        Timber.d("Sent initial keepalive report on connect: %b", sent)
                    } catch (e: SecurityException) {
                        Timber.e(e, "Failed to send initial keepalive: permission denied")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to send initial keepalive")
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.d("Device disconnected: %s", device.address)
                    connectedDevice = null
                    _connectionState.value = HidConnectionState.Advertising
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
        var lastException: Exception? = null
        val maxRetries = 3
        var retryDelay = 1000L

        for (attempt in 1..maxRetries) {
            Timber.d("registerApp attempt %d/%d", attempt, maxRetries)

            // Wrap the coroutine in a timeout. If the Android Bluetooth stack returns false
            // to registerApp() and drops the callback, this prevents hanging forever.
            val result = withTimeoutOrNull(5000L) {
                suspendCancellableCoroutine { cont ->
                    val hid = hidDevice
                    if (hid == null) {
                        cont.resumeWithException(
                            Fido2Exception.BluetoothException("HID_DEVICE profile not yet acquired")
                        )
                        return@suspendCancellableCoroutine
                    }

                    val isEnabled = try {
                        val enabled = bluetoothAdapter?.isEnabled == true
                        Timber.d("Bluetooth adapter enabled: %b", enabled)
                        enabled
                    } catch (e: SecurityException) {
                        Timber.e(
                            e,
                            "SecurityException checking if adapter is enabled - BLUETOOTH_CONNECT permission missing?"
                        )
                        cont.resumeWithException(
                            Fido2Exception.BluetoothPermissionDenied("BLUETOOTH_CONNECT permission denied", e)
                        )
                        return@suspendCancellableCoroutine
                    }
                    if (!isEnabled) {
                        Timber.w("Bluetooth is disabled, cannot register HID app")
                        cont.resumeWithException(
                            Fido2Exception.BluetoothException("Bluetooth is disabled")
                        )
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

                    // Pass null for QoS to let Android use safe defaults.
                    // Strict Android 13/14 vendor stacks (like Asus) often reject explicit outQos.

                    val registrationCallback = object : BluetoothHidDevice.Callback() {
                        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                            hidCallback.onAppStatusChanged(pluggedDevice, registered)
                            if (registered) {
                                if (cont.isActive) cont.resume(Result.success(Unit))
                            } else {
                                if (cont.isActive) cont.resumeWithException(
                                    Fido2Exception.BluetoothException("HID app registration failed")
                                )
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
                            null, // QOS: Use null to avoid vendor rejection
                            Executors.newSingleThreadExecutor(),
                            registrationCallback
                        )
                        Timber.d("registerApp framework call result: %b", callResult)
                        callResult
                    } catch (e: SecurityException) {
                        Timber.e(e, "SecurityException in registerApp - missing permissions?")
                        cont.resumeWithException(
                            Fido2Exception.BluetoothPermissionDenied("BLUETOOTH_ADVERTISE permission denied", e)
                        )
                        return@suspendCancellableCoroutine
                    } catch (e: Exception) {
                        Timber.e(e, "Unexpected Exception in registerApp")
                        cont.resumeWithException(
                            Fido2Exception.BluetoothException("Unexpected error during app registration: ${e.message}")
                        )
                        return@suspendCancellableCoroutine
                    }

                    if (!registeredValue) {
                        Timber.w("registerApp() returned false - internal stack failure. Waiting for callback anyway...")
                    }
                }
            }

            if (result != null) {
                if (result.isSuccess) {
                    Timber.i("registerApp successful")
                    return Result.success(Unit)
                } else {
                    lastException = result.exceptionOrNull() as? Exception
                    Timber.w("registerApp result was failure: %s", lastException?.message)
                }
            } else {
                Timber.e("registerApp timed out after 5000ms - callback onAppStatusChanged never received")
                lastException = Fido2Exception.BluetoothException("HID registration timed out")
            }

            if (attempt < maxRetries) {
                Timber.w("Retrying registration in %dms...", retryDelay)
                delay(retryDelay)
                retryDelay *= 2 // Exponential backoff
            }
        }

        return Result.failure(
            lastException ?: Fido2Exception.BluetoothException("HID registration failed after retries")
        )
    }

    /**
     * Sends a 64-byte HID input report to the connected host over the
     * interrupt channel.  [data] is padded/truncated to exactly 64 bytes.
     */
    fun sendReport(data: ByteArray): Boolean {
        if (hidDevice == null) {
            Timber.w("sendReport: HID device not available")
            return false
        }
        if (connectedDevice == null) {
            Timber.w("sendReport: no connected device")
            return false
        }
        val report = ensureReportSize(data)
        reportQueue.add(report)
        processNextReport()
        return true
    }

    @Synchronized
    private fun processNextReport() {
        if (isSending.get() || reportQueue.isEmpty()) return

        val report = reportQueue.poll() ?: return
        isSending.set(true)

        val hid = hidDevice
        val device = connectedDevice

        if (hid == null || device == null) {
            Timber.w("processNextReport: HID device or connected device not available")
            isSending.set(false)
            processNextReport()
            return
        }

        try {
            val sent = hid.sendReport(device, FIDO_REPORT_ID.toInt(), report)
            Timber.d("sendReport dispatched len=%d success=%b", report.size, sent)

            // Delay slightly to give the Bluetooth stack time to process the HCI commands
            scope.launch {
                delay(20L)
                isSending.set(false)
                processNextReport()
            }
        } catch (e: SecurityException) {
            Timber.e(e, "sendReport: BLUETOOTH_CONNECT permission denied")
            isSending.set(false)
            processNextReport()
        } catch (e: Exception) {
            Timber.e(e, "sendReport: Exception")
            isSending.set(false)
            processNextReport()
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

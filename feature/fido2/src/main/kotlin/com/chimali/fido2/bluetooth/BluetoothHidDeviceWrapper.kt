package com.chimali.fido2.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.chimali.fido2.domain.exception.Fido2Exception
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "BluetoothHidWrapper"

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
    @ApplicationContext private val context: Context,
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
                Log.d(TAG, "HID_DEVICE profile proxy acquired")
                initContinuation?.takeIf { it.isActive }?.resume(Result.success(Unit))
                initContinuation = null
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                _connectionState.value = HidConnectionState.Idle
                Log.d(TAG, "HID_DEVICE profile proxy released")
            }
        }
    }

    /** Callback dispatched on the executor supplied to [registerApp]. */
    private val hidCallback = object : BluetoothHidDevice.Callback() {

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged registered=$registered device=$pluggedDevice")
            if (registered) {
                if (pluggedDevice != null) {
                    Log.w(TAG, "Phantom device reported upon registration: ${pluggedDevice.address}. Forcing disconnect to clear L2CAP socket.")
                    try {
                        // Some Android devices (like Moto G) falsely report a connected device 
                        // immediately upon registration, occupying the socket and blocking real connections.
                        // Force a disconnect to clear the state.
                        val disconnected = hidDevice?.disconnect(pluggedDevice)
                        Log.d(TAG, "Forced disconnect result: $disconnected")
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Failed to force disconnect phantom device", e)
                    }
                }
                // Always return to advertising, waiting for the REAL host connection attempt
                _connectionState.value = HidConnectionState.Advertising
            } else {
                _connectionState.value = HidConnectionState.Idle
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.d(TAG, "onConnectionStateChanged state=$state device=${device.address}")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    _connectionState.value = HidConnectionState.Connected(device)
                    // Windows keepalive: some drivers instantly drop idle L2CAP connections.
                    // Send an empty HID report immediately so they know the device is active.
                    try {
                        val report = ByteArray(FIDO_HID_REPORT_SIZE)
                        val sent = hidDevice?.sendReport(device, FIDO_REPORT_ID.toInt(), report)
                        Log.d(TAG, "Sent initial keepalive report on connect: $sent")
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Failed to send initial keepalive: permission denied", e)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send initial keepalive", e)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Device disconnected: ${device.address}")
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
            Log.d(TAG, "onSetReport type=$type id=$id len=${data.size}")
            incomingReports.trySend(ensureReportSize(data))
        }

        /** Called when the host sends data over the HID interrupt channel. */
        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            Log.d(TAG, "onInterruptData reportId=$reportId len=${data.size}")
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
                Log.e(TAG, "onGetReport: BLUETOOTH_CONNECT permission denied", e)
            }
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            Log.d(TAG, "onVirtualCableUnplug device=${device.address}")
            connectedDevice = null
            _connectionState.value = HidConnectionState.Advertising
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Must be called once to acquire the BluetoothHidDevice profile proxy from the system.
     * This suspends until the proxy is delivered.
     */
    suspend fun initialize(): Result<Unit> {
        if (hidDevice != null) return Result.success(Unit)
        
        return suspendCancellableCoroutine { cont ->
            initContinuation = cont
            try {
                val success = bluetoothAdapter?.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE) ?: false
                if (!success) {
                    cont.resumeWithException(Fido2Exception.BluetoothException("Failed to request HID proxy. Is Bluetooth on?"))
                    initContinuation = null
                }
            } catch (e: SecurityException) {
                cont.resumeWithException(Fido2Exception.BluetoothPermissionDenied("Bluetooth permission denied", e))
                initContinuation = null
            }
        }
    }

    /**
     * Registers the FIDO2 HID application and begins advertising so that a
     * Bluetooth host can discover and connect to this authenticator.
     *
     * Suspends until the app is registered (i.e. [onAppStatusChanged] fires
     * with registered=true). Implements a timeout and retry mechanism for
     * unreliable Bluetooth stacks that drop callbacks.
     */
    suspend fun registerApp(): Result<Unit> {
        var lastException: Exception? = null
        val maxRetries = 3
        var retryDelay = 1000L

        for (attempt in 1..maxRetries) {
            Log.d(TAG, "registerApp attempt $attempt/$maxRetries")
            
            // Wrap the coroutine in a timeout. If the Android Bluetooth stack returns false
            // to registerApp() and drops the callback, this prevents hanging forever.
            val result = withTimeoutOrNull(5000L) {
                suspendCancellableCoroutine<Result<Unit>> { cont ->
                    val hid = hidDevice
                    if (hid == null) {
                        cont.resumeWithException(
                            Fido2Exception.BluetoothException("HID_DEVICE profile not yet acquired")
                        )
                        return@suspendCancellableCoroutine
                    }

                    val isEnabled = try {
                        bluetoothAdapter?.isEnabled == true
                    } catch (e: SecurityException) {
                        cont.resumeWithException(
                            Fido2Exception.BluetoothPermissionDenied("BLUETOOTH_CONNECT permission denied", e)
                        )
                        return@suspendCancellableCoroutine
                    }
                    if (!isEnabled) {
                        cont.resumeWithException(
                            Fido2Exception.BluetoothException("Bluetooth is disabled")
                        )
                        return@suspendCancellableCoroutine
                    }

                    val sdp = BluetoothHidDeviceAppSdpSettings(
                        "Chimali Authenticator",
                        "FIDO2 Virtual Security Key",
                        "Chimali",
                        BluetoothHidDevice.SUBCLASS1_NONE,
                        FIDO_HID_REPORT_DESCRIPTOR
                    )

                    // QoS: wiokey-android values
                    val outQos = BluetoothHidDeviceAppQosSettings(
                        BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                        1000, 
                        FIDO_HID_REPORT_SIZE + 1, 
                        2000, 
                        5000, 
                        BluetoothHidDeviceAppQosSettings.MAX
                    )

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
                        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) = hidCallback.onConnectionStateChanged(device, state)
                        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) = hidCallback.onSetReport(device, type, id, data)
                        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) = hidCallback.onInterruptData(device, reportId, data)
                        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) = hidCallback.onGetReport(device, type, id, bufferSize)
                        override fun onVirtualCableUnplug(device: BluetoothDevice) = hidCallback.onVirtualCableUnplug(device)
                    }

                    val registered = try {
                        val callResult = hid.registerApp(
                            sdp,
                            null,
                            outQos,
                            Executors.newSingleThreadExecutor(),
                            registrationCallback
                        )
                        Log.d(TAG, "registerApp framework call returned: $callResult")
                        callResult
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException in registerApp", e)
                        cont.resumeWithException(
                            Fido2Exception.BluetoothPermissionDenied("BLUETOOTH_ADVERTISE permission denied", e)
                        )
                        return@suspendCancellableCoroutine
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected Exception in registerApp", e)
                        cont.resumeWithException(
                            Fido2Exception.BluetoothException("Unexpected error during app registration: ${e.message}")
                        )
                        return@suspendCancellableCoroutine
                    }

                    if (!registered) {
                        Log.w(TAG, "registerApp() returned false. Waiting up to 5s for callback...")
                    }
                }
            }

            if (result != null) {
                if (result.isSuccess) {
                    return Result.success(Unit)
                } else {
                    lastException = result.exceptionOrNull() as? Exception
                }
            } else {
                Log.e(TAG, "registerApp timed out after 5000ms")
                lastException = Fido2Exception.BluetoothException("HID registration timed out")
            }

            if (attempt < maxRetries) {
                Log.w(TAG, "Retrying registration in ${retryDelay}ms...")
                delay(retryDelay)
                retryDelay *= 2 // Exponential backoff
            }
        }
        
        return Result.failure(lastException ?: Fido2Exception.BluetoothException("HID registration failed after retries"))
    }

    /**
     * Sends a 64-byte HID input report to the connected host over the
     * interrupt channel.  [data] is padded/truncated to exactly 64 bytes.
     */
    fun sendReport(data: ByteArray): Boolean {
        val hid = hidDevice ?: run {
            Log.w(TAG, "sendReport: HID device not available")
            return false
        }
        val device = connectedDevice ?: run {
            Log.w(TAG, "sendReport: no connected device")
            return false
        }
        val report = ensureReportSize(data)
        return try {
            val sent = hid.sendReport(device, FIDO_REPORT_ID.toInt(), report)
            Log.d(TAG, "sendReport sent=$sent len=${report.size}")
            sent
        } catch (e: SecurityException) {
            Log.e(TAG, "sendReport: BLUETOOTH_CONNECT permission denied", e)
            false
        }
    }

    /** Unregisters the HID app (stops advertising / disconnects host). */
    fun unregisterApp() {
        try {
            hidDevice?.unregisterApp()
        } catch (e: SecurityException) {
            Log.e(TAG, "unregisterApp: Bluetooth permission denied", e)
        }
        _connectionState.value = HidConnectionState.Idle
    }

    /** Releases the profile proxy. Should be called from Application.onTerminate. */
    fun close() {
        unregisterApp()
        try {
            hidDevice?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        } catch (e: SecurityException) {
            Log.e(TAG, "close: Bluetooth permission denied", e)
        }
        hidDevice = null
        incomingReports.close()
    }

    /** Returns true if a Bluetooth host is currently connected. */
    fun isConnected(): Boolean = _connectionState.value is HidConnectionState.Connected

    /** Returns true if Bluetooth is enabled on the device. */
    fun isBluetoothEnabled(): Boolean = try {
        bluetoothAdapter?.isEnabled == true
    } catch (e: SecurityException) {
        Log.e(TAG, "isBluetoothEnabled: permission denied", e)
        false
    }

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

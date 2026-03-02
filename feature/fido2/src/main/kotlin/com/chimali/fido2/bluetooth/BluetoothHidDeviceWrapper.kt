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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
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
private val FIDO_HID_REPORT_DESCRIPTOR = byteArrayOf(
    0x06.toByte(), 0xD0.toByte(), 0xF1.toByte(), // Usage Page (FIDO Alliance)
    0x09.toByte(), 0x01.toByte(),                  // Usage (U2F Authenticator Device)
    0xA1.toByte(), 0x01.toByte(),                  // Collection (Application)
    0x09.toByte(), 0x20.toByte(),                  //   Usage (Input Report Data)
    0x15.toByte(), 0x00.toByte(),                  //   Logical Minimum (0)
    0x26.toByte(), 0xFF.toByte(), 0x00.toByte(),   //   Logical Maximum (255)
    0x75.toByte(), 0x08.toByte(),                  //   Report Size (8)
    0x95.toByte(), 0x40.toByte(),                  //   Report Count (64)
    0x81.toByte(), 0x02.toByte(),                  //   Input (Data, Var, Abs)
    0x09.toByte(), 0x21.toByte(),                  //   Usage (Output Report Data)
    0x15.toByte(), 0x00.toByte(),                  //   Logical Minimum (0)
    0x26.toByte(), 0xFF.toByte(), 0x00.toByte(),   //   Logical Maximum (255)
    0x75.toByte(), 0x08.toByte(),                  //   Report Size (8)
    0x95.toByte(), 0x40.toByte(),                  //   Report Count (64)
    0x91.toByte(), 0x02.toByte(),                  //   Output (Data, Var, Abs)
    0xC0.toByte()                                   // End Collection
)

/** Fixed 64-byte HID report size per FIDO CTAP HID spec §8.1 */
const val FIDO_HID_REPORT_SIZE = 64

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

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                Log.d(TAG, "HID_DEVICE profile proxy acquired")
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
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
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
        override fun onIntrData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            Log.d(TAG, "onIntrData reportId=$reportId len=${data.size}")
            incomingReports.trySend(ensureReportSize(data))
        }

        override fun onGetReport(
            device: BluetoothDevice,
            type: Byte,
            id: Byte,
            bufferSize: Int
        ) {
            // Host polling for a report — respond with empty/idle report
            hidDevice?.replyReport(device, type, id, ByteArray(FIDO_HID_REPORT_SIZE))
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            Log.d(TAG, "onVirtualCableUnplug device=${device.address}")
            connectedDevice = null
            _connectionState.value = HidConnectionState.Advertising
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Must be called once (e.g. from Application.onCreate or a Hilt entry-point)
     * to acquire the BluetoothHidDevice profile proxy from the system.
     */
    fun initialize() {
        bluetoothAdapter?.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
    }

    /**
     * Registers the FIDO2 HID application and begins advertising so that a
     * Bluetooth host can discover and connect to this authenticator.
     *
     * Suspends until the app is registered (i.e. [onAppStatusChanged] fires
     * with registered=true) or throws on failure.
     */
    suspend fun registerApp(): Result<Unit> = suspendCancellableCoroutine { cont ->
        val hid = hidDevice
        if (hid == null) {
            cont.resumeWithException(
                Fido2Exception.BluetoothException("HID_DEVICE profile not yet acquired — call initialize() first")
            )
            return@suspendCancellableCoroutine
        }

        if (bluetoothAdapter?.isEnabled != true) {
            cont.resumeWithException(
                Fido2Exception.BluetoothException("Bluetooth is disabled")
            )
            return@suspendCancellableCoroutine
        }

        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Chimali Authenticator",
            "FIDO2 Virtual Security Key",
            "Chimali",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            FIDO_HID_REPORT_DESCRIPTOR
        )

        // QoS: latency-optimised for HID (matches reference wiokey values)
        val inQos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
        )
        val outQos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
        )

        // Wrap the existing callback to capture registration result
        val registrationCallback = object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                hidCallback.onAppStatusChanged(pluggedDevice, registered)
                if (registered) {
                    if (cont.isActive) cont.resume(Unit)
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

            override fun onIntrData(device: BluetoothDevice, reportId: Byte, data: ByteArray) =
                hidCallback.onIntrData(device, reportId, data)

            override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) =
                hidCallback.onGetReport(device, type, id, bufferSize)

            override fun onVirtualCableUnplug(device: BluetoothDevice) =
                hidCallback.onVirtualCableUnplug(device)
        }

        val registered = hid.registerApp(
            sdp,
            inQos,
            outQos,
            Executors.newSingleThreadExecutor(),
            registrationCallback
        )

        if (!registered) {
            cont.resumeWithException(
                Fido2Exception.BluetoothException("registerApp() returned false — Bluetooth may not be ready")
            )
        }
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
        val sent = hid.sendReport(device, FIDO_REPORT_ID.toInt(), report)
        Log.d(TAG, "sendReport sent=$sent len=${report.size}")
        return sent
    }

    /** Unregisters the HID app (stops advertising / disconnects host). */
    fun unregisterApp() {
        hidDevice?.unregisterApp()
        _connectionState.value = HidConnectionState.Idle
    }

    /** Releases the profile proxy. Should be called from Application.onTerminate. */
    fun close() {
        unregisterApp()
        hidDevice?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        hidDevice = null
        incomingReports.close()
    }

    /** Returns true if a Bluetooth host is currently connected. */
    fun isConnected(): Boolean = _connectionState.value is HidConnectionState.Connected

    /** Returns true if Bluetooth is enabled on the device. */
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

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

package com.chimali.core.bluetooth.impl

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.chimali.core.bluetooth.api.AuthenticatorState
import com.chimali.core.bluetooth.api.BluetoothHidAuthenticator
import com.chimali.core.bluetooth.util.BluetoothHidConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

private const val TAG = "BtHidAuthenticator"

/**
 * Manages the Bluetooth Classic HID Device profile to emulate a FIDO2 security key.
 *
 * Uses [FidoHidFraming] to reassemble CTAP packets from raw HID reports, then
 * forwards complete messages to the registered [HidMessageListener].
 *
 * Integration reference:
 * - WIOsense/rauth-android (TransactionManager.java / HidDeviceApp.java)
 * - octaviordz/wiokey-android (HidDeviceApp.java)
 */
class BluetoothHidAuthenticatorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BluetoothHidAuthenticator, BluetoothProfile.ServiceListener {

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    // Monotonically increasing channel IDs (skip reserved 0x00000000, 0xFFFFFFFF)
    private val channelIdCounter = AtomicInteger(1)

    // Per-channel accumulators. In the HID transport there is only one host, so
    // a single accumulator is sufficient; keep this map for future multi-channel support.
    private val accumulators = mutableMapOf<Int, FidoHidFraming.Accumulator>()

    // True if startAdvertising() was called before the BluetoothHidDevice proxy was ready.
    // Will be consumed in onServiceConnected() to start advertising automatically.
    @Volatile private var pendingAdvertise = false

    // The callback receives complete FIDO messages (reassembled from HID packets).
    private var messageListener: HidMessageListener? = null

    private val _state = MutableStateFlow(AuthenticatorState.IDLE)
    override val stateFlow: StateFlow<AuthenticatorState> = _state.asStateFlow()
    override val state: AuthenticatorState get() = _state.value

    /** Functional interface for receiving fully-reassembled FIDO HID messages. */
    fun interface HidMessageListener {
        fun onHidMessage(message: FidoHidFraming.HidMessage)
    }

    /** Register a listener that will receive complete FIDO HID messages. */
    fun setMessageListener(listener: HidMessageListener?) {
        messageListener = listener
    }

    init {
        adapter?.getProfileProxy(context, this, BluetoothProfile.HID_DEVICE)
    }

    // -----------------------------------------------------------------------------------
    // BluetoothHidAuthenticator
    // -----------------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    override fun startAdvertising() {
        if (_state.value != AuthenticatorState.IDLE) {
            Log.w(TAG, "startAdvertising called in state ${_state.value}, ignoring")
            return
        }

        val hid = hidDevice
        if (hid == null) {
            // Proxy not yet available — schedule advertising for when onServiceConnected fires.
            Log.w(TAG, "BluetoothHidDevice proxy not ready yet, deferring startAdvertising")
            pendingAdvertise = true
            return
        }

        doRegisterApp(hid)
    }

    @SuppressLint("MissingPermission")
    private fun doRegisterApp(hid: BluetoothHidDevice) {
        Log.d(TAG, "Registering HID app with BluetoothHidDevice")
        hid.registerApp(
            BluetoothHidConstants.SDP_RECORD,
            null,
            BluetoothHidConstants.QOS_OUT,
            Executors.newSingleThreadExecutor(),
            hidCallback
        )
    }

    @SuppressLint("MissingPermission")
    override fun stop() {
        hidDevice?.unregisterApp()
        connectedHost = null
        accumulators.clear()
        _state.value = AuthenticatorState.IDLE
    }

    @SuppressLint("MissingPermission")
    override fun sendConfirmation() {
        // Intentional no-op: responses are sent by the CTAP2 layer via sendHidPackets().
        // This method remains for interface compatibility and can be used for a "wink".
        wink()
    }

    // -----------------------------------------------------------------------------------
    // HID Report Sending
    // -----------------------------------------------------------------------------------

    /**
     * Sends a complete [FidoHidFraming.HidMessage] to the connected host by encoding
     * it into one or more 64-byte HID report packets.
     */
    @SuppressLint("MissingPermission")
    fun sendHidMessage(message: FidoHidFraming.HidMessage) {
        val host = connectedHost ?: return
        val hid = hidDevice ?: return
        val packets = FidoHidFraming.encode(message)
        for (packet in packets) {
            val sent = hid.sendReport(host, BluetoothHidConstants.HID_REPORT_ID.toInt(), packet)
            if (!sent) {
                Log.e(TAG, "sendReport failed — packet dropped")
                break
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun wink() {
        val host = connectedHost ?: return
        val hid = hidDevice ?: return
        // A WINK command is an Init packet with no payload on the device's channel.
        val winkMsg = FidoHidFraming.HidMessage(
            channelId = ByteArray(4) { 0x01 }, // use a dedicated channel or device channel
            cmd = FidoHidFraming.CMD_WINK,
            data = ByteArray(0)
        )
        val packet = FidoHidFraming.encode(winkMsg).first()
        hid.sendReport(host, BluetoothHidConstants.HID_REPORT_ID.toInt(), packet)
    }

    // -----------------------------------------------------------------------------------
    // Bluetooth HID Callback
    // -----------------------------------------------------------------------------------

    private val hidCallback = object : BluetoothHidDevice.Callback() {

        override fun onAppStatusChanged(device: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged: registered=$registered device=$device")
            if (registered) {
                _state.value = AuthenticatorState.ADVERTISING
            } else {
                _state.value = AuthenticatorState.IDLE
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, newState: Int) {
            Log.d(TAG, "onConnectionStateChanged: state=$newState device=${device?.address}")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedHost = device
                    _state.value = AuthenticatorState.CONNECTED
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedHost = null
                    accumulators.clear()
                    _state.value = AuthenticatorState.ADVERTISING // keep advertising after disconnect
                }
                else -> Unit
            }
        }

        @SuppressLint("MissingPermission")
        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            // Host is polling for a report. For FIDO this is not commonly used; respond with empty.
            hidDevice?.replyReport(device, type, id, ByteArray(BluetoothHidConstants.HID_REPORT_SIZE))
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            Log.d(TAG, "onSetReport: type=$type id=$id len=${data?.size ?: 0}")
            if (data == null || data.size < BluetoothHidConstants.HID_REPORT_SIZE) return

            // Ensure we only process the first HID_REPORT_SIZE bytes if the host sent more (e.g. padding)
            val reportData = if (data.size > BluetoothHidConstants.HID_REPORT_SIZE) {
                data.copyOfRange(0, BluetoothHidConstants.HID_REPORT_SIZE)
            } else {
                data
            }

            // Determine the Channel ID from the first 4 bytes.
            val cidInt = ((reportData[0].toInt() and 0xFF) shl 24) or
                         ((reportData[1].toInt() and 0xFF) shl 16) or
                         ((reportData[2].toInt() and 0xFF) shl 8) or
                         (reportData[3].toInt() and 0xFF)

            // Handle INIT command on broadcast CID.
            val isBroadcast = cidInt == -1 // 0xFFFFFFFF
            val cmd = reportData[4].toInt() and 0xFF
            if (isBroadcast && (cmd == (FidoHidFraming.CMD_INIT.toInt() and 0xFF))) {
                handleInitRequest(reportData)
                return
            }

            val accumulator = accumulators.getOrPut(cidInt) { FidoHidFraming.Accumulator() }
            val completedMessage = accumulator.addPacket(reportData)
            if (completedMessage != null) {
                Log.d(TAG, "Complete HID message: cmd=0x${completedMessage.cmd.toInt().and(0xFF).toString(16)} len=${completedMessage.data.size}")
                messageListener?.onHidMessage(completedMessage)
            }
        }

        override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) {
            Log.d(TAG, "onInterruptData: reportId=$reportId len=${data?.size ?: 0}")
            // Interrupt channel data — some hosts send data here instead of via SET_REPORT.
            if (data != null) onSetReport(device, 0, reportId, data)
        }
    }

    // -----------------------------------------------------------------------------------
    // INIT Command Handling
    // -----------------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun handleInitRequest(packet: ByteArray) {
        Log.d(TAG, "Handling INIT request on broadcast CID")
        val newCidInt = channelIdCounter.getAndIncrement()
        val newCid = byteArrayOf(
            ((newCidInt shr 24) and 0xFF).toByte(),
            ((newCidInt shr 16) and 0xFF).toByte(),
            ((newCidInt shr 8)  and 0xFF).toByte(),
            (newCidInt and 0xFF).toByte()
        )
        val responsePacket = FidoHidFraming.buildInitResponse(packet, newCid)
        val host = connectedHost ?: return
        hidDevice?.sendReport(host, BluetoothHidConstants.HID_REPORT_ID.toInt(), responsePacket)
        Log.d(TAG, "INIT: assigned CID 0x${newCidInt.toString(16)}")
    }

    // -----------------------------------------------------------------------------------
    // BluetoothProfile.ServiceListener
    // -----------------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
        if (profile == BluetoothProfile.HID_DEVICE) {
            val hid = proxy as BluetoothHidDevice
            hidDevice = hid
            Log.d(TAG, "BluetoothHidDevice service connected; pendingAdvertise=$pendingAdvertise")
            if (pendingAdvertise) {
                pendingAdvertise = false
                doRegisterApp(hid)
            }
        }
    }

    override fun onServiceDisconnected(profile: Int) {
        if (profile == BluetoothProfile.HID_DEVICE) {
            hidDevice = null
            _state.value = AuthenticatorState.ERROR
            Log.w(TAG, "BluetoothHidDevice service disconnected")
        }
    }
}

package com.chimali.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HidManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothManager: BluetoothManager
) {
    private var bluetoothHidDevice: BluetoothHidDevice? = null
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                bluetoothHidDevice = proxy as BluetoothHidDevice
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                bluetoothHidDevice = null
            }
        }
    }

    init {
        adapter?.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "Chimali Authenticator",
            "Virtual FIDO2 Token",
            "Chimali",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            descriptor
        )

        bluetoothHidDevice?.registerApp(
            sdpSettings,
            null,
            null,
            context.mainExecutor,
            object : BluetoothHidDevice.Callback() {
                override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                    Log.d("HidManager", "Registration status: $registered")
                }

                override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                    val stateStr = when(state) {
                        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
                        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
                        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
                        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
                        else -> "UNKNOWN ($state)"
                    }
                    Log.d("HidManager", "Connection state changed: ${device?.address} is now $stateStr")
                    // TODO: Notify ViewModel to refresh device list
                }

                override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
                    Log.d("HidManager", "Received report: ${data?.size} bytes")
                    data?.let { packet ->
                        if (packet.isNotEmpty()) {
                            // T014: Distinguish between GetAssertion (0x02) and MakeCredential (0x01)
                            val command = packet[0]
                            Log.d("HidManager", "CTAP Command: $command")
                            val address = device?.address ?: return@let
                            _incomingRequests.tryEmit(address to packet)
                        }
                    }
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    fun getConnectedDevices(): List<BluetoothDevice> {
        return bluetoothHidDevice?.connectedDevices ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return adapter?.bondedDevices?.toList() ?: emptyList()
    }

    private val _discoveredDevices = MutableStateFlow<Set<BluetoothDevice>>(emptySet())
    val discoveredDevices: StateFlow<Set<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _incomingRequests = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 10)
    val incomingRequests: SharedFlow<Pair<String, ByteArray>> = _incomingRequests.asSharedFlow()

    private val receiver = object : android.content.BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: android.content.Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null && device.name != null) {
                        _discoveredDevices.value = _discoveredDevices.value + device
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isScanning.value = true
                    _discoveredDevices.value = emptySet()
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                    try {
                        context.unregisterReceiver(this)
                    } catch (e: Exception) {
                        // Already unregistered
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (adapter?.isDiscovering == true) {
            adapter.cancelDiscovery()
        }
        val filter = android.content.IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)
        adapter?.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        if (adapter?.isDiscovering == true) {
            adapter.cancelDiscovery()
        }
    }

    @SuppressLint("MissingPermission")
    fun pairDevice(address: String): Boolean {
        return try {
            val device = adapter?.getRemoteDevice(address)
            device?.createBond() ?: false
        } catch (e: Exception) {
            Log.e("HidManager", "Failed to initiate pairing: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(device: BluetoothDevice): Boolean {
        return bluetoothHidDevice?.connect(device) ?: false
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice(device: BluetoothDevice): Boolean {
        return bluetoothHidDevice?.disconnect(device) ?: false
    }

    @SuppressLint("MissingPermission")
    fun unpairDevice(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("removeBond")
            method.invoke(device) as Boolean
        } catch (e: Exception) {
            Log.e("HidManager", "Failed to unpair device: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun sendReport(device: BluetoothDevice, data: ByteArray): Boolean {
        // ID 2 corresponds to the Data In report ID defined in the descriptor
        return bluetoothHidDevice?.replyReport(device, BluetoothHidDevice.REPORT_TYPE_INPUT, 2.toByte(), data) ?: false
    }

    companion object {
        // Simplified FIDO HID Descriptor
        private val descriptor = byteArrayOf(
            0x06.toByte(), 0xD0.toByte(), 0xF1.toByte(), // Usage Page (FIDO Alliance)
            0x09.toByte(), 0x01.toByte(),                 // Usage (U2F Authenticator)
            0xA1.toByte(), 0x01.toByte(),                 // Collection (Application)
            0x09.toByte(), 0x20.toByte(),                 //   Usage (Data Out)
            0x15.toByte(), 0x00.toByte(),                 //   Logical Minimum (0)
            0x26.toByte(), 0xFF.toByte(), 0x00.toByte(),  //   Logical Maximum (255)
            0x75.toByte(), 0x08.toByte(),                 //   Report Size (8)
            0x95.toByte(), 0x40.toByte(),                 //   Report Count (64)
            0x81.toByte(), 0x02.toByte(),                 //   Input (Data, Absolute, Variable)
            0x09.toByte(), 0x21.toByte(),                 //   Usage (Data In)
            0x15.toByte(), 0x00.toByte(),                 //   Logical Minimum (0)
            0x26.toByte(), 0xFF.toByte(), 0x00.toByte(),  //   Logical Maximum (255)
            0x75.toByte(), 0x08.toByte(),                 //   Report Size (8)
            0x95.toByte(), 0x40.toByte(),                 //   Report Count (64)
            0x91.toByte(), 0x02.toByte(),                 //   Output (Data, Absolute, Variable)
            0xC0.toByte()                                 // End Collection
        )
    }
}

package com.chimali.bluetooth.hid

import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.ParcelUuid
import com.chimali.authenticator.domain.model.BluetoothHidConnection
import com.chimali.authenticator.domain.model.ConnectionState
import com.chimali.authenticator.domain.model.PairedDevice
import com.chimali.authenticator.domain.repository.BluetoothHidRepository
import com.chimali.authenticator.domain.repository.PairedDeviceRepository
import com.chimali.authenticator.domain.logging.AuthenticatorLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothHidService @Inject constructor(
    private val context: Context,
    private val bluetoothHidRepository: BluetoothHidRepository,
    private val pairedDeviceRepository: PairedDeviceRepository,
    private val logger: AuthenticatorLogger
) {
    
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var hidDevice: BluetoothHidDevice? = null
    
    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive = _isServiceActive.asStateFlow()
    
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()
    
    // HID Device Constants
    companion object {
        val HID_SERVICE_UUID: UUID = UUID.fromString("00001812-0000-1000-8000-00805F9B34FB")
        val HUMAN_INTERFACE_DEVICE_CLASS = 0x2540
        val KEYBOARD_SUBCLASS = 0x40
        val MOUSE_SUBCLASS = 0x80
        val JOYSTICK_SUBCLASS = 0x20
    }
    
    private val hidDeviceCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            logger.i("BluetoothHidService", "HID app status changed: device=$pluggedDevice, registered=$registered")
            _isServiceActive.value = registered
        }
        
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            val stateName = when (state) {
                BluetoothHidDevice.STATE_CONNECTED -> "CONNECTED"
                BluetoothHidDevice.STATE_CONNECTING -> "CONNECTING"
                BluetoothHidDevice.STATE_DISCONNECTED -> "DISCONNECTED"
                else -> "UNKNOWN"
            }
            
            logger.i("BluetoothHidService", "Connection state changed: device=$device, state=$stateName")
            logger.logSecurityEvent("Bluetooth connection state changed", mapOf(
                "device_address" to device.address,
                "device_name" to device.name,
                "state" to stateName
            ))
            
            handleConnectionStateChange(device, state)
        }
        
        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            logger.d("BluetoothHidService", "Get report request: device=$device, type=$type, id=$id, bufferSize=$bufferSize")
            handleGetReport(device, type, id, bufferSize)
        }
        
        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int, buffer: ByteArray) {
            logger.d("BluetoothHidService", "Set report request: device=$device, type=$type, id=$id, bufferSize=$bufferSize")
            handleSetReport(device, type, id, buffer)
        }
        
        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            logger.d("BluetoothHidService", "Interrupt data: device=$device, reportId=$reportId, dataSize=${data.size}")
            handleInterruptData(device, reportId, data)
        }
    }
    
    suspend fun initializeService(): Boolean {
        return try {
            if (!isBluetoothAvailable()) {
                logger.e("BluetoothHidService", "Bluetooth not available")
                return false
            }
            
            if (!isBluetoothEnabled()) {
                logger.w("BluetoothHidService", "Bluetooth not enabled")
                return false
            }
            
            setupHidDevice()
            true
        } catch (e: Exception) {
            logger.e("BluetoothHidService", "Failed to initialize service", e)
            false
        }
    }
    
    private suspend fun setupHidDevice() {
        bluetoothAdapter?.let { adapter ->
            hidDevice = adapter.getProfileProxy(
                context,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                        if (profile == BluetoothProfile.HID_DEVICE) {
                            hidDevice = proxy as? BluetoothHidDevice
                            logger.i("BluetoothHidService", "HID device proxy connected")
                            registerHidApp()
                        }
                    }
                    
                    override fun onServiceDisconnected(profile: Int) {
                        if (profile == BluetoothProfile.HID_DEVICE) {
                            hidDevice = null
                            _isServiceActive.value = false
                            logger.i("BluetoothHidService", "HID device proxy disconnected")
                        }
                    }
                },
                BluetoothProfile.HID_DEVICE
            )
        }
    }
    
    private fun registerHidApp() {
        hidDevice?.let { device ->
            try {
                // Create HID descriptor for keyboard/mouse combo
                val hidDescriptor = createHidDescriptor()
                
                val success = device.registerApp(
                    sdpRecord = hidDescriptor,
                    inQoS = null,
                    outQoS = null,
                    callback = hidDeviceCallback
                )
                
                if (success) {
                    _isServiceActive.value = true
                    logger.i("BluetoothHidService", "HID app registered successfully")
                    logger.logSecurityEvent("HID service started", mapOf(
                        "service_active" to true
                    ))
                } else {
                    logger.e("BluetoothHidService", "Failed to register HID app")
                }
            } catch (e: Exception) {
                logger.e("BluetoothHidService", "Failed to register HID app", e)
            }
        }
    }
    
    private fun createHidDescriptor(): BluetoothHidDevice.AppSdpRecord {
        // Simplified HID descriptor for keyboard + mouse
        val descriptor = byteArrayOf(
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x06.toByte(), // Usage (Keyboard)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x05.toByte(), 0x07.toByte(), // Usage Page (Key Codes)
            0x19.toByte(), 0xE0.toByte(), // Usage Minimum (224)
            0x29.toByte(), 0xE7.toByte(), // Usage Maximum (231)
            0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), // Logical Maximum (1)
            0x75.toByte(), 0x01.toByte(), // Report Size (1)
            0x95.toByte(), 0x08.toByte(), // Report Count (8)
            0x81.toByte(), 0x02.toByte(), // Input (Data, Variable, Absolute)
            0xC0.toByte()                  // End Collection
        )
        
        return BluetoothHidDevice.AppSdpRecord(
            "Chimali FIDO2 Authenticator",
            "Chimali",
            "FIDO2 Virtual Authenticator",
            "1.0",
            descriptor,
            null
        )
    }
    
    private fun handleConnectionStateChange(device: BluetoothDevice, state: Int) {
        val connectionState = when (state) {
            BluetoothHidDevice.STATE_CONNECTED -> ConnectionState.CONNECTED
            BluetoothHidDevice.STATE_CONNECTING -> ConnectionState.CONNECTING
            BluetoothHidDevice.STATE_DISCONNECTED -> ConnectionState.DISCONNECTED
            else -> ConnectionState.ERROR
        }
        
        // Find or create connection
        val existingConnection = bluetoothHidRepository.getActiveConnectionForDevice(device.address)
        if (existingConnection != null) {
            bluetoothHidRepository.updateConnectionState(existingConnection.connectionId, connectionState)
        } else if (state == BluetoothHidDevice.STATE_CONNECTED) {
            // Create new connection
            val newConnection = BluetoothHidConnection(
                deviceId = device.address,
                connectionState = connectionState,
                capabilities = mapOf(
                    "device_name" to device.name,
                    "device_address" to device.address,
                    "device_class" to device.bluetoothClass.toString()
                )
            )
            bluetoothHidRepository.createConnection(newConnection)
            
            // Create paired device entry
            val pairedDevice = PairedDevice(
                deviceId = device.address,
                deviceName = device.name ?: "Unknown Device",
                platform = detectPlatform(device),
                lastConnected = System.currentTimeMillis()
            )
            pairedDeviceRepository.insertDevice(pairedDevice)
        }
    }
    
    private fun detectPlatform(device: BluetoothDevice): PairedDevice.Platform {
        // Simple platform detection based on device name/class
        val deviceName = device.name?.lowercase() ?: ""
        return when {
            deviceName.contains("mac") || deviceName.contains("apple") -> PairedDevice.Platform.MACOS
            deviceName.contains("windows") || deviceName.contains("pc") -> PairedDevice.Platform.WINDOWS
            deviceName.contains("linux") || deviceName.contains("ubuntu") -> PairedDevice.Platform.LINUX
            else -> PairedDevice.Platform.UNKNOWN
        }
    }
    
    private fun handleGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
        // Handle HID report requests from host
        logger.d("BluetoothHidService", "Processing GET report request")
        // Implementation would send appropriate HID report data
    }
    
    private fun handleSetReport(device: BluetoothDevice, type: Byte, id: Byte, buffer: ByteArray) {
        // Handle HID report data from host
        logger.d("BluetoothHidService", "Processing SET report data")
        // Implementation would process incoming HID data
    }
    
    private fun handleInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
        // Handle interrupt data (real-time HID data)
        logger.d("BluetoothHidService", "Processing interrupt data")
        // Implementation would handle real-time input/output
    }
    
    suspend fun startDiscovery(): Boolean {
        return try {
            if (!isBluetoothEnabled()) {
                logger.w("BluetoothHidService", "Cannot start discovery: Bluetooth not enabled")
                return false
            }
            
            val success = bluetoothAdapter?.startDiscovery() ?: false
            if (success) {
                logger.i("BluetoothHidService", "Bluetooth discovery started")
                logger.logUserAction("Started Bluetooth discovery")
            } else {
                logger.e("BluetoothHidService", "Failed to start Bluetooth discovery")
            }
            success
        } catch (e: Exception) {
            logger.e("BluetoothHidService", "Error starting discovery", e)
            false
        }
    }
    
    suspend fun stopDiscovery(): Boolean {
        return try {
            val success = bluetoothAdapter?.cancelDiscovery() ?: false
            if (success) {
                logger.i("BluetoothHidService", "Bluetooth discovery stopped")
                logger.logUserAction("Stopped Bluetooth discovery")
            }
            success
        } catch (e: Exception) {
            logger.e("BluetoothHidService", "Error stopping discovery", e)
            false
        }
    }
    
    suspend fun connectToDevice(deviceAddress: String): Boolean {
        return try {
            val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
            if (device == null) {
                logger.e("BluetoothHidService", "Device not found: $deviceAddress")
                return false
            }
            
            // Create connection entry
            val connection = BluetoothHidConnection(
                deviceId = deviceAddress,
                connectionState = ConnectionState.CONNECTING,
                capabilities = mapOf(
                    "device_name" to device.name,
                    "device_address" to device.address
                )
            )
            bluetoothHidRepository.createConnection(connection)
            
            logger.i("BluetoothHidService", "Initiating connection to device: $deviceAddress")
            logger.logUserAction("Initiated device connection", mapOf(
                "device_address" to deviceAddress,
                "device_name" to device.name
            ))
            
            true
        } catch (e: Exception) {
            logger.e("BluetoothHidService", "Error connecting to device: $deviceAddress", e)
            false
        }
    }
    
    suspend fun disconnectFromDevice(deviceAddress: String): Boolean {
        return try {
            hidDevice?.disconnect(bluetoothAdapter?.getRemoteDevice(deviceAddress))
            
            // Update connection state
            val activeConnection = bluetoothHidRepository.getActiveConnectionForDevice(deviceAddress)
            if (activeConnection != null) {
                bluetoothHidRepository.updateConnectionState(
                    activeConnection.connectionId, 
                    ConnectionState.DISCONNECTED
                )
            }
            
            logger.i("BluetoothHidService", "Disconnected from device: $deviceAddress")
            logger.logUserAction("Disconnected device", mapOf(
                "device_address" to deviceAddress
            ))
            
            true
        } catch (e: Exception) {
            logger.e("BluetoothHidService", "Error disconnecting from device: $deviceAddress", e)
            false
        }
    }
    
    suspend fun sendHidReport(connectionId: String, reportId: Byte, data: ByteArray): Boolean {
        return try {
            val connection = bluetoothHidRepository.getConnectionById(connectionId)
            if (connection == null) {
                logger.e("BluetoothHidService", "Connection not found: $connectionId")
                return false
            }
            
            if (connection.connectionState != ConnectionState.CONNECTED) {
                logger.w("BluetoothHidService", "Cannot send HID report: connection not active")
                return false
            }
            
            val device = bluetoothAdapter?.getRemoteDevice(connection.deviceId)
            val success = hidDevice?.setReport(device, BluetoothHidDevice.REPORT_TYPE_OUTPUT, reportId, data) ?: false
            
            if (success) {
                logger.d("BluetoothHidService", "HID report sent successfully")
            } else {
                logger.e("BluetoothHidService", "Failed to send HID report")
            }
            
            success
        } catch (e: Exception) {
            logger.e("BluetoothHidService", "Error sending HID report", e)
            false
        }
    }
    
    fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter != null
    }
    
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    suspend fun shutdown() {
        try {
            hidDevice?.unregisterApp()
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
            _isServiceActive.value = false
            logger.i("BluetoothHidService", "Service shutdown completed")
            logger.logSecurityEvent("HID service stopped", mapOf(
                "service_active" to false
            ))
        } catch (e: Exception) {
            logger.e("BluetoothHidService", "Error during shutdown", e)
        }
    }
}

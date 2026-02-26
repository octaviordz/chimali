package com.chimali.bluetooth.hid

import android.bluetooth.*
import android.content.Context
import com.chimali.authenticator.domain.logging.AuthenticatorLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class BluetoothHidDevice @Inject constructor(
    private val context: Context,
    private val logger: AuthenticatorLogger
) {
    
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var hidDevice: BluetoothHidDevice? = null
    
    private val _deviceState = MutableStateFlow<DeviceState>(DeviceState.Disconnected)
    val deviceState = _deviceState.asStateFlow()
    
    private val _isRegistered = MutableStateFlow(false)
    val isRegistered = _isRegistered.asStateFlow()
    
    enum class DeviceState {
        Unavailable,
        Disconnected,
        Connecting,
        Connected,
        Error
    }
    
    suspend fun initialize(): Boolean {
        return try {
            if (bluetoothAdapter == null) {
                _deviceState.value = DeviceState.Unavailable
                logger.e("BluetoothHidDevice", "Bluetooth not available")
                return false
            }
            
            if (!bluetoothAdapter.isEnabled) {
                _deviceState.value = DeviceState.Disconnected
                logger.w("BluetoothHidDevice", "Bluetooth not enabled")
                return false
            }
            
            val success = setupHidDeviceProxy()
            if (success) {
                _deviceState.value = DeviceState.Disconnected
                logger.i("BluetoothHidDevice", "HID device initialized successfully")
            } else {
                _deviceState.value = DeviceState.Error
                logger.e("BluetoothHidDevice", "Failed to initialize HID device")
            }
            
            success
        } catch (e: Exception) {
            _deviceState.value = DeviceState.Error
            logger.e("BluetoothHidDevice", "Error initializing HID device", e)
            false
        }
    }
    
    private suspend fun setupHidDeviceProxy(): Boolean = suspendCancellableCoroutine { continuation ->
        bluetoothAdapter?.getProfileProxy(
            context,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    if (profile == BluetoothProfile.HID_DEVICE && proxy is BluetoothHidDevice) {
                        hidDevice = proxy
                        logger.i("BluetoothHidDevice", "HID device proxy connected")
                        continuation.resume(true)
                    } else {
                        continuation.resume(false)
                    }
                }
                
                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        hidDevice = null
                        _deviceState.value = DeviceState.Disconnected
                        _isRegistered.value = false
                        logger.i("BluetoothHidDevice", "HID device proxy disconnected")
                    }
                }
            },
            BluetoothProfile.HID_DEVICE
        )
    }
    
    suspend fun registerHidApp(
        appName: String = "Chimali FIDO2 Authenticator",
        providerName: String = "Chimali",
        description: String = "FIDO2 Virtual Authenticator",
        version: String = "1.0",
        hidDescriptor: ByteArray? = null
    ): Boolean {
        return try {
            hidDevice?.let { device ->
                val descriptor = hidDescriptor ?: createDefaultHidDescriptor()
                val appSdpRecord = BluetoothHidDevice.AppSdpRecord(
                    appName,
                    providerName,
                    description,
                    version,
                    descriptor,
                    null
                )
                
                val success = device.registerApp(
                    appSdpRecord,
                    null, // inQoS
                    null, // outQoS
                    object : BluetoothHidDevice.Callback() {
                        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                            _isRegistered.value = registered
                            logger.i("BluetoothHidDevice", "App status changed: registered=$registered")
                        }
                        
                        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
                            val newState = when (state) {
                                BluetoothHidDevice.STATE_CONNECTED -> DeviceState.Connected
                                BluetoothHidDevice.STATE_CONNECTING -> DeviceState.Connecting
                                BluetoothHidDevice.STATE_DISCONNECTED -> DeviceState.Disconnected
                                else -> DeviceState.Error
                            }
                            _deviceState.value = newState
                            logger.i("BluetoothHidDevice", "Connection state changed: device=${device.name}, state=$newState")
                        }
                    }
                )
                
                if (success) {
                    logger.i("BluetoothHidDevice", "HID app registered successfully")
                } else {
                    logger.e("BluetoothHidDevice", "Failed to register HID app")
                }
                
                success
            } ?: false
        } catch (e: Exception) {
            logger.e("BluetoothHidDevice", "Error registering HID app", e)
            false
        }
    }
    
    suspend fun unregisterHidApp(): Boolean {
        return try {
            hidDevice?.unregisterApp()
            _isRegistered.value = false
            logger.i("BluetoothHidDevice", "HID app unregistered")
            true
        } catch (e: Exception) {
            logger.e("BluetoothHidDevice", "Error unregistering HID app", e)
            false
        }
    }
    
    suspend fun connectToDevice(device: BluetoothDevice): Boolean {
        return try {
            if (!isRegistered.value) {
                logger.w("BluetoothHidDevice", "Cannot connect: HID app not registered")
                return false
            }
            
            _deviceState.value = DeviceState.Connecting
            // Note: Actual connection is handled by the host device
            // This just prepares the device for incoming connections
            logger.i("BluetoothHidDevice", "Device ready for connection: ${device.name}")
            true
        } catch (e: Exception) {
            _deviceState.value = DeviceState.Error
            logger.e("BluetoothHidDevice", "Error preparing device connection", e)
            false
        }
    }
    
    suspend fun disconnectFromDevice(device: BluetoothDevice): Boolean {
        return try {
            hidDevice?.disconnect(device)
            _deviceState.value = DeviceState.Disconnected
            logger.i("BluetoothHidDevice", "Disconnected from device: ${device.name}")
            true
        } catch (e: Exception) {
            logger.e("BluetoothHidDevice", "Error disconnecting from device", e)
            false
        }
    }
    
    suspend fun sendReport(
        device: BluetoothDevice,
        reportType: Int,
        reportId: Byte,
        data: ByteArray
    ): Boolean {
        return try {
            if (_deviceState.value != DeviceState.Connected) {
                logger.w("BluetoothHidDevice", "Cannot send report: device not connected")
                return false
            }
            
            val success = hidDevice?.setReport(device, reportType, reportId, data) ?: false
            if (success) {
                logger.d("BluetoothHidDevice", "Report sent successfully: type=$reportType, id=$reportId, size=${data.size}")
            } else {
                logger.e("BluetoothHidDevice", "Failed to send report")
            }
            success
        } catch (e: Exception) {
            logger.e("BluetoothHidDevice", "Error sending report", e)
            false
        }
    }
    
    suspend fun sendKeyboardReport(device: BluetoothDevice, modifierKeys: Byte, pressedKeys: ByteArray): Boolean {
        return sendReport(
            device,
            BluetoothHidDevice.REPORT_TYPE_OUTPUT,
            0x01.toByte(), // Keyboard report ID
            byteArrayOf(modifierKeys, 0x00) + pressedKeys
        )
    }
    
    suspend fun sendMouseReport(device: BluetoothDevice, buttons: Byte, deltaX: Byte, deltaY: Byte): Boolean {
        return sendReport(
            device,
            BluetoothHidDevice.REPORT_TYPE_OUTPUT,
            0x02.toByte(), // Mouse report ID
            byteArrayOf(buttons, deltaX, deltaY, 0x00)
        )
    }
    
    private fun createDefaultHidDescriptor(): ByteArray {
        // Combined keyboard + mouse HID descriptor
        return byteArrayOf(
            // Keyboard part
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
            0x95.toByte(), 0x01.toByte(), // Report Count (1)
            0x75.toByte(), 0x08.toByte(), // Report Size (8)
            0x81.toByte(), 0x03.toByte(), // Input (Constant, Variable, Absolute)
            0x95.toByte(), 0x05.toByte(), // Report Count (5)
            0x75.toByte(), 0x01.toByte(), // Report Size (1)
            0x05.toByte(), 0x08.toByte(), // Usage Page (LEDs)
            0x19.toByte(), 0x01.toByte(), // Usage Minimum (1)
            0x29.toByte(), 0x05.toByte(), // Usage Maximum (5)
            0x91.toByte(), 0x02.toByte(), // Output (Data, Variable, Absolute)
            0x95.toByte(), 0x01.toByte(), // Report Count (1)
            0x75.toByte(), 0x03.toByte(), // Report Size (3)
            0x91.toByte(), 0x03.toByte(), // Output (Constant, Variable, Absolute)
            0x95.toByte(), 0x06.toByte(), // Report Count (6)
            0x75.toByte(), 0x08.toByte(), // Report Size (8)
            0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
            0x25.toByte(), 0x65.toByte(), // Logical Maximum (101)
            0x05.toByte(), 0x07.toByte(), // Usage Page (Key Codes)
            0x19.toByte(), 0x00.toByte(), // Usage Minimum (0)
            0x29.toByte(), 0x65.toByte(), // Usage Maximum (101)
            0x81.toByte(), 0x00.toByte(), // Input (Data, Array, Absolute)
            0xC0.toByte(),                  // End Collection
            
            // Mouse part
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x02.toByte(), // Usage (Mouse)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x09.toByte(), 0x01.toByte(), // Usage (Pointer)
            0xA1.toByte(), 0x00.toByte(), // Collection (Physical)
            0x05.toByte(), 0x09.toByte(), // Usage Page (Button)
            0x19.toByte(), 0x01.toByte(), // Usage Minimum (1)
            0x29.toByte(), 0x03.toByte(), // Usage Maximum (3)
            0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), // Logical Maximum (1)
            0x95.toByte(), 0x03.toByte(), // Report Count (3)
            0x75.toByte(), 0x01.toByte(), // Report Size (1)
            0x81.toByte(), 0x02.toByte(), // Input (Data, Variable, Absolute)
            0x95.toByte(), 0x01.toByte(), // Report Count (1)
            0x75.toByte(), 0x05.toByte(), // Report Size (5)
            0x81.toByte(), 0x03.toByte(), // Input (Constant, Variable, Absolute)
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x30.toByte(), // Usage (X)
            0x09.toByte(), 0x31.toByte(), // Usage (Y)
            0x09.toByte(), 0x38.toByte(), // Usage (Wheel)
            0x15.toByte(), 0x81.toByte(), // Logical Minimum (-127)
            0x25.toByte(), 0x7F.toByte(), // Logical Maximum (127)
            0x75.toByte(), 0x08.toByte(), // Report Size (8)
            0x95.toByte(), 0x03.toByte(), // Report Count (3)
            0x81.toByte(), 0x06.toByte(), // Input (Data, Variable, Relative)
            0xC0.toByte(),                  // End Collection
            0xC0.toByte()                   // End Collection
        )
    }
    
    fun getDeviceCapabilities(): Map<String, Any> {
        return mapOf(
            "device_available" to (bluetoothAdapter != null),
            "bluetooth_enabled" to (bluetoothAdapter?.isEnabled == true),
            "hid_supported" to (hidDevice != null),
            "app_registered" to isRegistered.value,
            "current_state" to deviceState.value.name
        )
    }
    
    suspend fun shutdown() {
        try {
            unregisterHidApp()
            hidDevice?.let { device ->
                bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, device)
            }
            hidDevice = null
            _deviceState.value = DeviceState.Disconnected
            logger.i("BluetoothHidDevice", "Device shutdown completed")
        } catch (e: Exception) {
            logger.e("BluetoothHidDevice", "Error during shutdown", e)
        }
    }
}

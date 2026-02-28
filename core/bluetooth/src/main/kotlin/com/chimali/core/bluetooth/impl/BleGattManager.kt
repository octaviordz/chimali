package com.chimali.core.bluetooth.impl

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleGattManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothManager: BluetoothManager
) {
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = adapter?.bluetoothLeAdvertiser
    
    var onError: ((String) -> Unit)? = null

    private val _incomingRequests = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 10)
    val incomingRequests: SharedFlow<Pair<String, ByteArray>> = _incomingRequests.asSharedFlow()

    private val _connectionEvents = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 5)
    val connectionEvents: SharedFlow<Pair<String, Boolean>> = _connectionEvents.asSharedFlow()

    private val fidoServiceUuid = UUID.fromString("0000FFFD-0000-1000-8000-00805F9B34FB")
    private val controlPointUuid = UUID.fromString("F1D0FFF1-DEAA-ECEE-B42F-C9BA7ED623BB")
    private val statusUuid = UUID.fromString("F1D0FFF2-DEAA-ECEE-B42F-C9BA7ED623BB")
    private val controlPointLengthUuid = UUID.fromString("F1D0FFF3-DEAA-ECEE-B42F-C9BA7ED623BB")
    private val serviceRevisionUuid = UUID.fromString("F1D0FFF4-DEAA-ECEE-B42F-C9BA7ED623BB")

    private val genericAccessServiceUuid = UUID.fromString("00001800-0000-1000-8000-00805F9B34FB")
    private val appearanceUuid = UUID.fromString("00002A01-0000-1000-8000-00805F9B34FB")
    // Device Information Service
    private val disServiceUuid = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")
    private val manufacturerNameUuid = UUID.fromString("00002A29-0000-1000-8000-00805F9B34FB")
    private val modelNumberUuid = UUID.fromString("00002A24-0000-1000-8000-00805F9B34FB")
    private val firmwareRevisionUuid = UUID.fromString("00002A26-0000-1000-8000-00805F9B34FB")

    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private val connectedDevices = mutableSetOf<BluetoothDevice>()
    private val deviceMtus = mutableMapOf<String, Int>()
    private val deviceAccumulators = mutableMapOf<String, FidoBleFraming.Accumulator>()

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            Log.d("BleGattManager", "Service added: ${service.uuid}, status: $status")
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d("BleGattManager", "onConnectionStateChange: device=${device.address}, status=$status, newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BleGattManager", "Device connected: ${device.address}")
                connectedDevices.add(device)
                deviceMtus[device.address] = 23 // Default BLE MTU
                deviceAccumulators[device.address] = FidoBleFraming.Accumulator()
                _connectionEvents.tryEmit(device.address to true)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BleGattManager", "Device disconnected: ${device.address}")
                connectedDevices.remove(device)
                deviceMtus.remove(device.address)
                deviceAccumulators.remove(device.address)
                _connectionEvents.tryEmit(device.address to false)
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            Log.d("BleGattManager", "MTU changed for ${device.address}: $mtu")
            deviceMtus[device.address] = mtu
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == controlPointUuid) {
                Log.d("BleGattManager", "Received FIDO fragment: ${value.size} bytes from ${device.address}")
                
                val accumulator = deviceAccumulators[device.address]
                val completeMessage = accumulator?.addFragment(value)
                
                if (completeMessage != null) {
                    Log.d("BleGattManager", "Complete FIDO message assembled: ${completeMessage.data.size} bytes, cmd=${completeMessage.cmd}")
                    
                    if (completeMessage.cmd == FidoBleFraming.CMD_MSG) {
                        _incomingRequests.tryEmit(device.address to completeMessage.data)
                    } else if (completeMessage.cmd == FidoBleFraming.CMD_PING) {
                        // Echo ping
                        sendReport(device.address, completeMessage.data, FidoBleFraming.CMD_PING)
                    }
                }

                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            } else if (characteristic.uuid == serviceRevisionUuid) {
                // Client selects version. We just accept it for now.
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            } else {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            when (characteristic.uuid) {
                controlPointLengthUuid -> {
                    val maxValue = byteArrayOf(0x02.toByte(), 0x00.toByte()) // 512 bytes (standard max)
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, maxValue)
                }
                serviceRevisionUuid -> {
                    // FIDO BLE Spec: This must be a UTF-8 string, not the bitfield.
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, "1.2".toByteArray())
                }
                appearanceUuid -> {
                    // Appearance: Security Key (0x0181) -> [0x81, 0x01] in little-endian
                    val appearance = byteArrayOf(0x81.toByte(), 0x01.toByte())
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, appearance)
                }
                manufacturerNameUuid -> {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, "Chimali".toByteArray())
                }
                modelNumberUuid -> {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, "Authenticator v1".toByteArray())
                }
                firmwareRevisionUuid -> {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, "1.0.0".toByteArray())
                }
                else -> {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (descriptor.uuid == UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")) { // CCCD
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startServer() {
        if (gattServer != null) return

        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        
        val fidoService = BluetoothGattService(fidoServiceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // Control Point (Write)
        val cpChar = BluetoothGattCharacteristic(
            controlPointUuid,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        
        // Status (Notify)
        statusCharacteristic = BluetoothGattCharacteristic(
            statusUuid,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0 
        )
        val cccd = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        statusCharacteristic?.addDescriptor(cccd)

        // Control Point Length (Read)
        val lengthChar = BluetoothGattCharacteristic(
            controlPointLengthUuid,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        // Service Revision (Read/Write)
        val revChar = BluetoothGattCharacteristic(
            serviceRevisionUuid,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        fidoService.addCharacteristic(cpChar)
        fidoService.addCharacteristic(statusCharacteristic)
        fidoService.addCharacteristic(lengthChar)
        fidoService.addCharacteristic(revChar)

        // Device Information Service
        val disService = BluetoothGattService(disServiceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        disService.addCharacteristic(BluetoothGattCharacteristic(manufacturerNameUuid, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))
        disService.addCharacteristic(BluetoothGattCharacteristic(modelNumberUuid, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))
        disService.addCharacteristic(BluetoothGattCharacteristic(firmwareRevisionUuid, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))

        // Generic Access Service (for Appearance)
        val gapService = BluetoothGattService(genericAccessServiceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        gapService.addCharacteristic(BluetoothGattCharacteristic(appearanceUuid, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))

        gattServer?.addService(fidoService)
        gattServer?.addService(disService)
        gattServer?.addService(gapService)
        startAdvertising()
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        if (advertiser == null) {
            Log.e("BleGattManager", "Advertiser is null. BLE Peripheral mode may not be supported.")
            onError?.invoke("BLE Advertising not supported on this device.")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        // FIDO BLE Spec: Service Data (0xFFFD) should contain the service revision bitfield
        // Bit 5 = 1.2, Bit 6 = 1.1, Bit 7 = 1.0. We'll set 0x20 for 1.2 support.
        val serviceData = byteArrayOf(0x20.toByte())

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false) 
            .setIncludeTxPowerLevel(true)
            .addServiceUuid(ParcelUuid(fidoServiceUuid))
            .addServiceData(ParcelUuid(fidoServiceUuid), serviceData)
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        advertiser?.startAdvertising(settings, data, scanResponse, object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.d("BleGattManager", "Advertising started successfully")
            }

            override fun onStartFailure(errorCode: Int) {
                val msg = "Advertising failed: $errorCode"
                Log.e("BleGattManager", msg)
                onError?.invoke(msg)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun stopServer() {
        advertiser?.stopAdvertising(object : AdvertiseCallback() {})
        gattServer?.close()
        gattServer = null
        connectedDevices.clear()
    }

    @SuppressLint("MissingPermission")
    fun sendReport(address: String, data: ByteArray, cmd: Byte = FidoBleFraming.CMD_MSG): Boolean {
        val device = connectedDevices.find { it.address == address } ?: return false
        val char = statusCharacteristic ?: return false
        val mtu = deviceMtus[address] ?: 23

        val fragments = FidoBleFraming.fragment(FidoBleFraming.Message(cmd, data), mtu)
        Log.d("BleGattManager", "Sending ${data.size} bytes as ${fragments.size} fragments to $address")

        var success = true
        for (fragment in fragments) {
            char.value = fragment
            val result = gattServer?.notifyCharacteristicChanged(device, char, false) ?: false
            if (!result) {
                Log.e("BleGattManager", "Failed to send fragment to $address")
                success = false
            }
            // Small delay between fragments to avoid overwhelming Windows? 
            // In a real app, we should wait for onNotificationSent (not available in GATT Server API easily)
            // or just rely on the BLE stack's queue.
        }
        return success
    }

    @SuppressLint("MissingPermission")
    fun getConnectedDevices(): List<BluetoothDevice> {
        return connectedDevices.toList()
    }

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDevice> {
        return adapter?.bondedDevices?.toList() ?: emptyList()
    }

    fun isPeripheralSupported(): Boolean {
        return adapter?.isMultipleAdvertisementSupported ?: (adapter?.bluetoothLeAdvertiser != null)
    }
}

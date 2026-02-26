package com.chimali.authenticator.data.repository

import android.bluetooth.BluetoothDevice
import com.chimali.authenticator.domain.model.PairedDevice
import com.chimali.authenticator.domain.model.BluetoothHidConnection
import com.chimali.authenticator.domain.model.ConnectionState
import com.chimali.authenticator.domain.model.Platform
import com.chimali.authenticator.domain.repository.PairedDeviceRepository
import com.chimali.authenticator.domain.repository.BluetoothHidRepository
import com.chimali.authenticator.domain.usecase.Result
import com.chimali.authenticator.domain.error.AuthenticatorError
import com.chimali.authenticator.domain.logging.AuthenticatorLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PairingRepository @Inject constructor(
    private val pairedDeviceRepository: PairedDeviceRepository,
    private val bluetoothHidRepository: BluetoothHidRepository,
    private val logger: AuthenticatorLogger
) {
    
    suspend fun startDiscovery(): Result<Boolean> = safeCall {
        val success = bluetoothHidRepository.startBluetoothDiscovery()
        if (success) {
            logger.logUserAction("Started device discovery")
        } else {
            throw AuthenticatorError.BluetoothConnectionFailed("Failed to start discovery")
        }
        success
    }
    
    suspend fun stopDiscovery(): Result<Boolean> = safeCall {
        val success = bluetoothHidRepository.stopBluetoothDiscovery()
        if (success) {
            logger.logUserAction("Stopped device discovery")
        }
        success
    }
    
    suspend fun pairWithDevice(device: BluetoothDevice): Result<PairedDevice> = safeCall {
        logger.logUserAction("Initiated device pairing", mapOf(
            "device_address" to device.address,
            "device_name" to device.name,
            "device_class" to device.bluetoothClass.toString()
        ))
        
        // Create paired device entry
        val pairedDevice = PairedDevice(
            deviceId = device.address,
            deviceName = device.name ?: "Unknown Device",
            platform = detectPlatform(device),
            lastConnected = System.currentTimeMillis()
        )
        
        // Save to repository
        pairedDeviceRepository.insertDevice(pairedDevice)
        
        // Create initial connection entry
        val connection = BluetoothHidConnection(
            deviceId = device.address,
            connectionState = ConnectionState.CONNECTING,
            capabilities = mapOf(
                "device_name" to device.name,
                "device_address" to device.address,
                "device_class" to device.bluetoothClass.toString(),
                "bond_state" to device.bondState.toString()
            )
        )
        
        bluetoothHidRepository.createConnection(connection)
        
        logger.logSecurityEvent("Device paired successfully", mapOf(
            "device_id" to pairedDevice.deviceId,
            "device_name" to pairedDevice.deviceName,
            "platform" to pairedDevice.platform.name
        ))
        
        pairedDevice
    }
    
    suspend fun connectToDevice(deviceId: String): Result<Boolean> = safeCall {
        val device = pairedDeviceRepository.getDeviceById(deviceId)
            ?: throw AuthenticatorError.EntityNotFound("PairedDevice", deviceId)
        
        logger.logUserAction("Initiated device connection", mapOf(
            "device_id" to deviceId,
            "device_name" to device.deviceName
        ))
        
        val success = bluetoothHidRepository.connectToDevice(deviceId)
        if (success) {
            // Update last connected timestamp
            pairedDeviceRepository.updateLastConnected(deviceId)
            logger.logSecurityEvent("Device connection initiated", mapOf(
                "device_id" to deviceId
            ))
        } else {
            throw AuthenticatorError.BluetoothConnectionFailed(deviceId)
        }
        
        success
    }
    
    suspend fun disconnectFromDevice(deviceId: String): Result<Boolean> = safeCall {
        val device = pairedDeviceRepository.getDeviceById(deviceId)
            ?: throw AuthenticatorError.EntityNotFound("PairedDevice", deviceId)
        
        logger.logUserAction("Initiated device disconnection", mapOf(
            "device_id" to deviceId,
            "device_name" to device.deviceName
        ))
        
        val success = bluetoothHidRepository.disconnectFromDevice(deviceId)
        if (success) {
            logger.logSecurityEvent("Device disconnected", mapOf(
                "device_id" to deviceId
            ))
        }
        
        success
    }
    
    suspend fun unpairDevice(deviceId: String): Result<Boolean> = safeCall {
        val device = pairedDeviceRepository.getDeviceById(deviceId)
            ?: throw AuthenticatorError.EntityNotFound("PairedDevice", deviceId)
        
        logger.logUserAction("Initiated device unpairing", mapOf(
            "device_id" to deviceId,
            "device_name" to device.deviceName
        ))
        
        // Disconnect first
        bluetoothHidRepository.disconnectFromDevice(deviceId)
        
        // Remove connection entries
        bluetoothHidRepository.deleteConnectionsByDevice(deviceId)
        
        // Remove paired device
        pairedDeviceRepository.deleteDevice(deviceId)
        
        logger.logSecurityEvent("Device unpaired", mapOf(
            "device_id" to deviceId,
            "device_name" to device.deviceName
        ))
        
        true
    }
    
    suspend fun updateDeviceTrustStatus(deviceId: String, isTrusted: Boolean): Result<Boolean> = safeCall {
        val device = pairedDeviceRepository.getDeviceById(deviceId)
            ?: throw AuthenticatorError.EntityNotFound("PairedDevice", deviceId)
        
        logger.logUserAction("Updated device trust status", mapOf(
            "device_id" to deviceId,
            "device_name" to device.deviceName,
            "is_trusted" to isTrusted
        ))
        
        pairedDeviceRepository.updateTrustStatus(deviceId, isTrusted)
        
        logger.logSecurityEvent("Device trust status updated", mapOf(
            "device_id" to deviceId,
            "is_trusted" to isTrusted
        ))
        
        true
    }
    
    fun getPairedDevices(): Flow<List<PairedDevice>> {
        return pairedDeviceRepository.getAllDevices()
    }
    
    fun getTrustedDevices(): Flow<List<PairedDevice>> {
        return pairedDeviceRepository.getTrustedDevices()
    }
    
    fun getDeviceConnections(): Flow<List<BluetoothHidConnection>> {
        return bluetoothHidRepository.getAllConnections()
    }
    
    fun getActiveConnections(): Flow<List<BluetoothHidConnection>> {
        return bluetoothHidRepository.getConnectionsByState(ConnectionState.CONNECTED)
    }
    
    suspend fun getDeviceById(deviceId: String): PairedDevice? {
        return pairedDeviceRepository.getDeviceById(deviceId)
    }
    
    suspend fun getConnectionByDeviceId(deviceId: String): BluetoothHidConnection? {
        return bluetoothHidRepository.getActiveConnectionForDevice(deviceId)
    }
    
    fun getDeviceWithConnection(deviceId: String): Flow<Pair<PairedDevice?, BluetoothHidConnection?>> {
        return combine(
            pairedDeviceRepository.getAllDevices().map { devices -> devices.find { it.deviceId == deviceId } },
            bluetoothHidRepository.getAllConnections().map { connections -> connections.find { it.deviceId == deviceId } }
        ) { device, connection ->
            device to connection
        }
    }
    
    suspend fun getPairingStatistics(): PairingStatistics {
        val allDevices = pairedDeviceRepository.getAllDevices().first()
        val trustedDevices = pairedDeviceRepository.getTrustedDevices().first()
        val activeConnections = bluetoothHidRepository.getActiveConnectionCount()
        
        val platformStats = allDevices.groupBy { it.platform }
            .mapValues { it.value.size }
        
        return PairingStatistics(
            totalDevices = allDevices.size,
            trustedDevices = trustedDevices.size,
            activeConnections = activeConnections,
            platformDistribution = platformStats
        )
    }
    
    suspend fun validateDeviceForPairing(device: BluetoothDevice): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Check if device is already paired
        val existingDevice = pairedDeviceRepository.getDeviceById(device.address)
        if (existingDevice != null) {
            errors.add("Device is already paired")
        }
        
        // Check device class
        if (device.bluetoothClass.deviceClass != BluetoothHidDevice.HUMAN_INTERFACE_DEVICE_CLASS) {
            errors.add("Device is not a HID device")
        }
        
        // Check bond state
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            errors.add("Device is not bonded")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }
    
    private fun detectPlatform(device: BluetoothDevice): Platform {
        val deviceName = device.name?.lowercase() ?: ""
        val deviceClass = device.bluetoothClass.deviceClass
        
        return when {
            deviceName.contains("mac") || deviceName.contains("apple") -> Platform.MACOS
            deviceName.contains("windows") || deviceName.contains("pc") -> Platform.WINDOWS
            deviceName.contains("linux") || deviceName.contains("ubuntu") || 
            deviceName.contains("debian") || deviceName.contains("fedora") -> Platform.LINUX
            deviceClass == 0x0204 -> Platform.WINDOWS // Computer major class
            deviceClass == 0x0202 -> Platform.LINUX   // Computer major class
            deviceClass == 0x0200 -> Platform.MACOS   // Computer major class
            else -> Platform.UNKNOWN
        }
    }
    
    data class PairingStatistics(
        val totalDevices: Int,
        val trustedDevices: Int,
        val activeConnections: Int,
        val platformDistribution: Map<Platform, Int>
    )
    
    sealed class ValidationResult {
        object Success : ValidationResult()
        data class Error(val errors: List<String>) : ValidationResult()
    }
}

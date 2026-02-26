package com.chimali.authenticator.logging

import com.chimali.authenticator.domain.logging.AuthenticatorLogger
import com.chimali.authenticator.domain.model.PairedDevice
import com.chimali.authenticator.domain.model.BluetoothHidConnection
import com.chimali.authenticator.domain.model.ConnectionState
import com.chimali.authenticator.domain.model.Platform
import com.chimali.authenticator.domain.error.AuthenticatorError
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PairingLogger @Inject constructor(
    private val logger: AuthenticatorLogger
) {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    fun logPairingOperation(operation: String, deviceId: String, details: Map<String, Any> = emptyMap()) {
        val enrichedDetails = details + mapOf(
            "operation" to operation,
            "device_id" to deviceId,
            "timestamp" to getCurrentTimestamp(),
            "category" to "pairing"
        )
        
        logger.logSecurityEvent("Pairing operation: $operation", enrichedDetails)
        logger.i("PairingLogger", "$operation | Device: $deviceId | Details: $enrichedDetails")
    }
    
    fun logDeviceDiscovery(started: Boolean, details: Map<String, Any> = emptyMap()) {
        val operation = if (started) "discovery_started" else "discovery_stopped"
        val enrichedDetails = details + mapOf(
            "operation" to operation,
            "timestamp" to getCurrentTimestamp(),
            "category" to "discovery"
        )
        
        logger.logUserAction("Device discovery $operation", enrichedDetails)
        logger.i("PairingLogger", "Device discovery ${if (started) "started" else "stopped"} | $enrichedDetails")
    }
    
    fun logDevicePaired(device: PairedDevice, details: Map<String, Any> = emptyMap()) {
        val enrichedDetails = details + mapOf(
            "device_id" to device.deviceId,
            "device_name" to device.deviceName,
            "platform" to device.platform.name,
            "is_trusted" to device.isTrusted,
            "timestamp" to getCurrentTimestamp(),
            "category" to "pairing"
        )
        
        logger.logSecurityEvent("Device paired successfully", enrichedDetails)
        logger.i("PairingLogger", "Device paired: ${device.deviceName} (${device.platform}) | $enrichedDetails")
    }
    
    fun logDeviceUnpaired(deviceId: String, deviceName: String, details: Map<String, Any> = emptyMap()) {
        val enrichedDetails = details + mapOf(
            "device_id" to deviceId,
            "device_name" to deviceName,
            "timestamp" to getCurrentTimestamp(),
            "category" to "pairing"
        )
        
        logger.logSecurityEvent("Device unpaired", enrichedDetails)
        logger.i("PairingLogger", "Device unpaired: $deviceName | $enrichedDetails")
    }
    
    fun logConnectionStateChanged(
        deviceId: String,
        deviceName: String,
        oldState: ConnectionState,
        newState: ConnectionState,
        details: Map<String, Any> = emptyMap()
    ) {
        val enrichedDetails = details + mapOf(
            "device_id" to deviceId,
            "device_name" to deviceName,
            "old_state" to oldState.name,
            "new_state" to newState.name,
            "timestamp" to getCurrentTimestamp(),
            "category" to "connection"
        )
        
        logger.logSecurityEvent("Connection state changed", enrichedDetails)
        logger.i("PairingLogger", "Connection state changed: $deviceName | $oldState -> $newState | $enrichedDetails")
    }
    
    fun logConnectionEstablished(connection: BluetoothHidConnection, details: Map<String, Any> = emptyMap()) {
        val enrichedDetails = details + mapOf(
            "connection_id" to connection.connectionId,
            "device_id" to connection.deviceId,
            "protocol_version" to connection.protocolVersion,
            "capabilities" to connection.capabilities,
            "timestamp" to getCurrentTimestamp(),
            "category" to "connection"
        )
        
        logger.logSecurityEvent("Connection established", enrichedDetails)
        logger.i("PairingLogger", "Connection established: ${connection.connectionId} | $enrichedDetails")
    }
    
    fun logConnectionTerminated(connectionId: String, deviceId: String, reason: String, details: Map<String, Any> = emptyMap()) {
        val enrichedDetails = details + mapOf(
            "connection_id" to connectionId,
            "device_id" to deviceId,
            "termination_reason" to reason,
            "timestamp" to getCurrentTimestamp(),
            "category" to "connection"
        )
        
        logger.logSecurityEvent("Connection terminated", enrichedDetails)
        logger.i("PairingLogger", "Connection terminated: $connectionId | Reason: $reason | $enrichedDetails")
    }
    
    fun logTrustStatusChanged(deviceId: String, deviceName: String, isTrusted: Boolean, details: Map<String, Any> = emptyMap()) {
        val enrichedDetails = details + mapOf(
            "device_id" to deviceId,
            "device_name" to deviceName,
            "is_trusted" to isTrusted,
            "timestamp" to getCurrentTimestamp(),
            "category" to "trust"
        )
        
        logger.logSecurityEvent("Device trust status changed", enrichedDetails)
        logger.i("PairingLogger", "Trust status changed: $deviceName | Trusted: $isTrusted | $enrichedDetails")
    }
    
    fun logPairingError(error: AuthenticatorError, context: Map<String, Any> = emptyMap()) {
        val enrichedContext = context + mapOf(
            "error_type" to error::class.simpleName,
            "error_message" to error.message,
            "timestamp" to getCurrentTimestamp(),
            "category" to "error"
        )
        
        logger.logError(error, enrichedContext)
        logger.e("PairingLogger", "Pairing error: ${error.message} | Context: $enrichedContext")
    }
    
    fun logValidationResult(deviceId: String, isValid: Boolean, errors: List<String> = emptyList()) {
        val details = mapOf(
            "device_id" to deviceId,
            "validation_result" to isValid,
            "validation_errors" to errors,
            "timestamp" to getCurrentTimestamp(),
            "category" to "validation"
        )
        
        if (isValid) {
            logger.logSecurityEvent("Device validation passed", details)
            logger.d("PairingLogger", "Validation passed for device: $deviceId")
        } else {
            logger.logSecurityEvent("Device validation failed", details)
            logger.w("PairingLogger", "Validation failed for device: $deviceId | Errors: $errors")
        }
    }
    
    fun logServiceStatus(isActive: Boolean, details: Map<String, Any> = emptyMap()) {
        val enrichedDetails = details + mapOf(
            "service_active" to isActive,
            "timestamp" to getCurrentTimestamp(),
            "category" to "service"
        )
        
        logger.logSecurityEvent("HID service status", enrichedDetails)
        logger.i("PairingLogger", "HID service status: ${if (isActive) "Active" else "Inactive"} | $enrichedDetails")
    }
    
    fun logBluetoothState(isEnabled: Boolean, isAvailable: Boolean, details: Map<String, Any> = emptyMap()) {
        val enrichedDetails = details + mapOf(
            "bluetooth_enabled" to isEnabled,
            "bluetooth_available" to isAvailable,
            "timestamp" to getCurrentTimestamp(),
            "category" to "bluetooth"
        )
        
        logger.logSecurityEvent("Bluetooth state", enrichedDetails)
        logger.i("PairingLogger", "Bluetooth state: Available=$isAvailable, Enabled=$isEnabled | $enrichedDetails")
    }
    
    fun logHidReport(connectionId: String, reportType: String, reportId: Byte, dataSize: Int, details: Map<String, Any> = emptyMap()) {
        val enrichedDetails = details + mapOf(
            "connection_id" to connectionId,
            "report_type" to reportType,
            "report_id" to reportId,
            "data_size" to dataSize,
            "timestamp" to getCurrentTimestamp(),
            "category" to "hid_communication"
        )
        
        logger.logSecurityEvent("HID report transmitted", enrichedDetails)
        logger.d("PairingLogger", "HID report: $reportType | ID: $reportId | Size: $dataSize | Connection: $connectionId")
    }
    
    fun logPairingStatistics(stats: PairingStatistics) {
        val details = mapOf(
            "total_devices" to stats.totalDevices,
            "trusted_devices" to stats.trustedDevices,
            "active_connections" to stats.activeConnections,
            "platform_distribution" to stats.platformDistribution,
            "timestamp" to getCurrentTimestamp(),
            "category" to "statistics"
        )
        
        logger.logSecurityEvent("Pairing statistics", details)
        logger.i("PairingLogger", "Pairing stats: ${stats.totalDevices} total, ${stats.trustedDevices} trusted, ${stats.activeConnections} active | $details")
    }
    
    fun logSecurityEvent(event: String, severity: SecuritySeverity = SecuritySeverity.INFO, details: Map<String, Any> = emptyMap()) {
        val enrichedDetails = details + mapOf(
            "security_event" to event,
            "severity" to severity.name,
            "timestamp" to getCurrentTimestamp(),
            "category" to "security"
        )
        
        when (severity) {
            SecuritySeverity.CRITICAL -> logger.logSecurityEvent("CRITICAL: $event", enrichedDetails)
            SecuritySeverity.HIGH -> logger.logSecurityEvent("HIGH: $event", enrichedDetails)
            SecuritySeverity.MEDIUM -> logger.logSecurityEvent("MEDIUM: $event", enrichedDetails)
            SecuritySeverity.LOW -> logger.logSecurityEvent("LOW: $event", enrichedDetails)
            SecuritySeverity.INFO -> logger.logSecurityEvent("INFO: $event", enrichedDetails)
        }
        
        logger.i("PairingLogger", "Security event [$severity]: $event | $enrichedDetails")
    }
    
    private fun getCurrentTimestamp(): String {
        return dateFormat.format(Date())
    }
    
    data class PairingStatistics(
        val totalDevices: Int,
        val trustedDevices: Int,
        val activeConnections: Int,
        val platformDistribution: Map<Platform, Int>
    )
    
    enum class SecuritySeverity {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW,
        INFO
    }
}

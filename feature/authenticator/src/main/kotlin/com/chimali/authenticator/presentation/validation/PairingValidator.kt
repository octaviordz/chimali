package com.chimali.authenticator.presentation.validation

import android.bluetooth.BluetoothDevice
import com.chimali.authenticator.domain.error.AuthenticatorError
import com.chimali.authenticator.domain.model.PairedDevice
import com.chimali.authenticator.domain.model.BluetoothHidConnection
import com.chimali.authenticator.domain.model.ConnectionState

object PairingValidator {
    
    fun validateDeviceForPairing(device: BluetoothDevice): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        
        // Check if device is null
        if (device.name.isNullOrEmpty()) {
            errors.add(ValidationError.MissingDeviceName)
        }
        
        // Check device class
        if (device.bluetoothClass.deviceClass != 0x2540) { // HID Device class
            errors.add(ValidationError.NotHidDevice)
        }
        
        // Check bond state
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            errors.add(ValidationError.DeviceNotBonded)
        }
        
        // Check if device is discoverable
        if (device.bluetoothClass == 0) {
            errors.add(ValidationError.DeviceNotDiscoverable)
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }
    
    fun validateConnection(connection: BluetoothHidConnection): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        
        // Check connection ID
        if (connection.connectionId.isBlank()) {
            errors.add(ValidationError.MissingConnectionId)
        }
        
        // Check device ID
        if (connection.deviceId.isBlank()) {
            errors.add(ValidationError.MissingDeviceId)
        }
        
        // Validate connection state
        if (connection.connectionState == ConnectionState.ERROR) {
            errors.add(ValidationError.ConnectionError)
        }
        
        // Check if connection is expired (older than 5 minutes)
        val currentTime = System.currentTimeMillis()
        val connectionAge = currentTime - connection.establishedAt
        if (connectionAge > 300000) { // 5 minutes
            errors.add(ValidationError.ConnectionExpired)
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }
    
    fun validatePairedDevice(device: PairedDevice): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        
        // Check device ID
        if (device.deviceId.isBlank()) {
            errors.add(ValidationError.MissingDeviceId)
        }
        
        // Check device name
        if (device.deviceName.isBlank()) {
            errors.add(ValidationError.MissingDeviceName)
        }
        
        // Validate device name length
        if (device.deviceName.length > 100) {
            errors.add(ValidationError.DeviceNameTooLong)
        }
        
        // Validate platform
        if (device.platform == PairedDevice.Platform.UNKNOWN) {
            errors.add(ValidationError.UnknownPlatform)
        }
        
        // Check last connected timestamp
        if (device.lastConnected <= 0) {
            errors.add(ValidationError.InvalidTimestamp)
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }
    
    fun validateDeviceName(name: String): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        
        if (name.isBlank()) {
            errors.add(ValidationError.MissingDeviceName)
        }
        
        if (name.length > 100) {
            errors.add(ValidationError.DeviceNameTooLong)
        }
        
        // Check for invalid characters
        val invalidChars = setOf('<', '>', ':', '"', '|', '?', '*')
        if (name.any { it in invalidChars }) {
            errors.add(ValidationError.InvalidDeviceName)
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }
    
    fun validateBluetoothState(isEnabled: Boolean, isAvailable: Boolean): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        
        if (!isAvailable) {
            errors.add(ValidationError.BluetoothNotAvailable)
        }
        
        if (!isEnabled) {
            errors.add(ValidationError.BluetoothNotEnabled)
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }
    
    fun validateServiceState(isActive: Boolean): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        
        if (!isActive) {
            errors.add(ValidationError.ServiceNotActive)
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }
    
    fun toAuthenticatorError(validationResult: ValidationResult): AuthenticatorError? {
        return when (validationResult) {
            is ValidationResult.Error -> {
                val primaryError = validationResult.errors.first()
                when (primaryError) {
                    ValidationError.BluetoothNotAvailable -> AuthenticatorError.BluetoothNotAvailable
                    ValidationError.BluetoothNotEnabled -> AuthenticatorError.BluetoothPermissionDenied
                    ValidationError.DeviceNotBonded -> AuthenticatorError.BluetoothConnectionFailed("Device not bonded")
                    ValidationError.NotHidDevice -> AuthenticatorError.BluetoothHidNotSupported
                    ValidationError.ServiceNotActive -> AuthenticatorError.BluetoothConnectionFailed("Service not active")
                    ValidationError.ConnectionError -> AuthenticatorError.BluetoothConnectionFailed("Connection error")
                    ValidationError.ConnectionExpired -> AuthenticatorError.TimeoutError("Connection expired")
                    ValidationError.MissingDeviceId -> AuthenticatorError.ValidationError("Missing device ID")
                    ValidationError.MissingDeviceName -> AuthenticatorError.ValidationError("Missing device name")
                    ValidationError.MissingConnectionId -> AuthenticatorError.ValidationError("Missing connection ID")
                    ValidationError.DeviceNameTooLong -> AuthenticatorError.ValidationError("Device name too long")
                    ValidationError.InvalidDeviceName -> AuthenticatorError.ValidationError("Invalid device name")
                    ValidationError.UnknownPlatform -> AuthenticatorError.ValidationError("Unknown platform")
                    ValidationError.InvalidTimestamp -> AuthenticatorError.ValidationError("Invalid timestamp")
                    ValidationError.DeviceNotDiscoverable -> AuthenticatorError.BluetoothConnectionFailed("Device not discoverable")
                }
            }
            ValidationResult.Success -> null
        }
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val errors: List<ValidationError>) : ValidationResult()
}

enum class ValidationError {
    BluetoothNotAvailable,
    BluetoothNotEnabled,
    DeviceNotBonded,
    NotHidDevice,
    ServiceNotActive,
    ConnectionError,
    ConnectionExpired,
    MissingDeviceId,
    MissingDeviceName,
    MissingConnectionId,
    DeviceNameTooLong,
    InvalidDeviceName,
    UnknownPlatform,
    InvalidTimestamp,
    DeviceNotDiscoverable
}

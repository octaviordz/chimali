package com.chimali.authenticator.domain.error

sealed class AuthenticatorError(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    // Bluetooth errors
    class BluetoothNotAvailable : AuthenticatorError("Bluetooth is not available on this device")
    class BluetoothPermissionDenied : AuthenticatorError("Bluetooth permission denied")
    class BluetoothConnectionFailed(deviceId: String, cause: Throwable? = null) : 
        AuthenticatorError("Failed to connect to device: $deviceId", cause)
    class BluetoothHidNotSupported : AuthenticatorError("Bluetooth HID is not supported")
    
    // FIDO2 errors
    class CredentialCreationFailed(cause: Throwable? = null) : 
        AuthenticatorError("Failed to create credential", cause)
    class AuthenticationFailed(cause: Throwable? = null) : 
        AuthenticatorError("Authentication failed", cause)
    class InvalidSignature : AuthenticatorError("Invalid signature provided")
    class CredentialNotFound(credentialId: String) : 
        AuthenticatorError("Credential not found: $credentialId")
    
    // Security errors
    class KeyStoreOperationFailed(operation: String, cause: Throwable? = null) : 
        AuthenticatorError("KeyStore operation failed: $operation", cause)
    class EncryptionFailed(cause: Throwable? = null) : 
        AuthenticatorError("Encryption failed", cause)
    class DecryptionFailed(cause: Throwable? = null) : 
        AuthenticatorError("Decryption failed", cause)
    
    // Database errors
    class DatabaseOperationFailed(operation: String, cause: Throwable? = null) : 
        AuthenticatorError("Database operation failed: $operation", cause)
    class EntityNotFound(entity: String, id: String) : 
        AuthenticatorError("$entity not found: $id")
    
    // UI errors
    class UserCancelled : AuthenticatorError("User cancelled the operation")
    class UserVerificationFailed : AuthenticatorError("User verification failed")
    class TimeoutError(operation: String) : AuthenticatorError("Operation timed out: $operation")
    
    // Network errors
    class NetworkError(cause: Throwable? = null) : 
        AuthenticatorError("Network error occurred", cause)
    class ServerError(message: String) : AuthenticatorError("Server error: $message")
    
    // General errors
    class UnknownError(cause: Throwable? = null) : 
        AuthenticatorError("Unknown error occurred", cause)
    class ValidationError(message: String) : AuthenticatorError("Validation error: $message")
}

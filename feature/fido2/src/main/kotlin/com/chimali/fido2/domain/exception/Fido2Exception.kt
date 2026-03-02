package com.chimali.fido2.domain.exception

sealed class Fido2Exception(
    message: String,
    cause: Throwable? = null,
    errorCode: String? = null
) : Exception(message, cause) {
    
    val errorCode: String? = errorCode
    
    // Cryptographic exceptions
    class CryptographicException(
        message: String,
        cause: Throwable? = null
    ) : Fido2Exception(message, cause, "CRYPTO_ERROR")
    
    class KeyGenerationException(
        message: String,
        cause: Throwable? = null
    ) : Fido2Exception(message, cause, "KEY_GEN_ERROR")
    
    class SignatureException(
        message: String,
        cause: Throwable? = null
    ) : Fido2Exception(message, cause, "SIGNATURE_ERROR")
    
    // Storage exceptions
    class StorageException(
        message: String,
        cause: Throwable? = null
    ) : Fido2Exception(message, cause, "STORAGE_ERROR")
    
    class DatabaseException(
        message: String,
        cause: Throwable? = null
    ) : Fido2Exception(message, cause, "DATABASE_ERROR")
    
    class KeyStoreException(
        message: String,
        cause: Throwable? = null
    ) : Fido2Exception(message, cause, "KEYSTORE_ERROR")
    
    // Protocol exceptions
    class ProtocolException(
        message: String,
        cause: Throwable? = null
    ) : Fido2Exception(message, cause, "PROTOCOL_ERROR")
    
    class InvalidFormatException(
        message: String,
        cause: Throwable? = null
    ) : Fido2Exception(message, cause, "INVALID_FORMAT")
    
    class UnsupportedAlgorithmException(
        message: String,
        cause: Throwable? = null
    ) : Fido2Exception(message, cause, "UNSUPPORTED_ALGORITHM")
    
    // User verification exceptions
    class UserVerificationException(
        message: String,
        cause: Throwable? = null
    ) : Fido2Exception(message, cause, "USER_VERIFICATION_ERROR")
    
    class BiometricException(
        message: String,
        cause: Throwable? = null
    ) : Fido2Exception(message, cause, "BIOMETRIC_ERROR")
    
    class DeviceLockException(
        message: String,
        cause: Throwable? = null
    ) : Fido2Exception(message, cause, "DEVICE_LOCK_ERROR")
    
    // Transport exceptions
    class TransportException(
        message: String,
        cause: Throwable? = null
    ) : Fido2Exception(message, cause, "TRANSPORT_ERROR")
    
    class BluetoothException(
        message: String,
        cause: Throwable? = null
    ) : Fido2Exception(message, cause, "BLUETOOTH_ERROR")
    
    class ConnectionException(
        message: String,
        cause: Throwable? = null
    ) : Fido2Exception(message, cause, "CONNECTION_ERROR")
    
    // Business logic exceptions
    class CredentialException(
        message: String,
        cause: Throwable? = null
    ) : Fido2Exception(message, cause, "CREDENTIAL_ERROR")
    
    class CredentialNotFoundException(
        credentialId: String,
        cause: Throwable? = null
    ) : Fido2Exception("Credential not found: $credentialId", cause, "CREDENTIAL_NOT_FOUND")
    
    class DuplicateCredentialException(
        rpId: String,
        cause: Throwable? = null
    ) : Fido2Exception("Duplicate credential for RP: $rpId", cause, "DUPLICATE_CREDENTIAL")
    
    class RelyingPartyException(
        message: String,
        cause: Throwable? = null
    ) : Fido2Exception(message, cause, "RP_ERROR")
    
    // Security exceptions
    class SecurityException(
        message: String,
        cause: Throwable? = null
    ) : Fido2Exception(message, cause, "SECURITY_ERROR")
    
    class TamperException(
        message: String,
        cause: Throwable? = null
    ) : Fido2Exception(message, cause, "TAMPER_ERROR")
    
    class AuthenticationException(
        message: String,
        cause: Throwable? = null
    ) : Fido2Exception(message, cause, "AUTH_ERROR")
}

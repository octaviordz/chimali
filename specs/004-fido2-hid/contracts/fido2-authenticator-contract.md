# FIDO2 Authenticator Contract

**Date**: 2026-03-01  
**Feature**: 004-fido2-hid

## Overview

This contract defines the interface for the FIDO2 Virtual Authenticator, implementing the Client to Authenticator Protocol (CTAP2) and Bluetooth HID transport layer.

## Core Interfaces

### Fido2Authenticator

Main interface for FIDO2 operations following CTAP2 specification.

```kotlin
interface Fido2Authenticator {
    suspend fun getInfo(): GetInfoResult
    suspend fun makeCredential(options: MakeCredentialOptions): AttestationObject
    suspend fun getAssertion(options: GetAssertionOptions): AssertionObject
    suspend fun getPinResult(options: ClientPinOptions): ClientPinResult
    suspend fun getAllCredentials(): List<PasskeyCredential>
    suspend fun deleteCredential(credentialId: ByteArray): Boolean
    suspend fun deleteAllCredentials(): Boolean
    suspend fun resetAuthenticator(): Boolean
}
```

### BluetoothHidTransport

Interface for Bluetooth HID device communication.

```kotlin
interface BluetoothHidTransport {
    suspend fun startHidDevice(): Boolean
    suspend fun stopHidDevice(): Boolean
    suspend fun sendReport(report: ByteArray): Boolean
    fun receiveReports(): Flow<ByteArray>
    suspend fun getConnectionState(): ConnectionState
}
```

### UserVerification

Interface for user authentication methods.

```kotlin
interface UserVerification {
    suspend fun verifyBiometric(prompt: String): VerificationResult
    suspend fun verifyPin(pin: String): VerificationResult
    suspend fun isBiometricAvailable(): Boolean
    suspend fun isPinSet(): Boolean
    suspend fun setPin(pin: String): Boolean
    suspend fun resetPin(): Boolean
}
```

### CredentialStorage

Interface for secure credential persistence.

```kotlin
interface CredentialStorage {
    suspend fun storeCredential(credential: PasskeyCredential): Boolean
    suspend fun getCredential(credentialId: ByteArray): PasskeyCredential?
    suspend fun getAllCredentials(): List<PasskeyCredential>
    suspend fun deleteCredential(credentialId: ByteArray): Boolean
    suspend fun deleteAllCredentials(): Boolean
    suspend fun updateSignCount(credentialId: ByteArray, newCount: UInt): Boolean
}
```

## Data Transfer Objects

### GetInfoResult

```kotlin
data class GetInfoResult(
    val versions: List<Fido2Version>,
    val extensions: List<String>,
    val aaguid: ByteArray,
    val options: Map<String, Boolean>,
    val maxMsgSize: Int,
    val pinProtocols: List<Int>,
    val maxCredentialCountInList: Int,
    val maxCredentialIdLength: Int,
    val transports: List<Transport>
)
```

### MakeCredentialOptions

```kotlin
data class MakeCredentialOptions(
    val clientDataHash: ByteArray,
    val rp: PublicKeyCredentialRpEntity,
    val user: PublicKeyCredentialUserEntity,
    val pubKeyCredParams: List<PublicKeyCredentialParameters>,
    val excludeList: List<PublicKeyCredentialDescriptor>?,
    val extensions: Map<String, Any>?,
    val options: Map<String, Boolean>?,
    val pinAuth: ByteArray?,
    val pinProtocol: Int?
)
```

### AttestationObject

```kotlin
data class AttestationObject(
    val fmt: String,
    val authData: ByteArray,
    val attStmt: Map<String, Any>
)
```

### GetAssertionOptions

```kotlin
data class GetAssertionOptions(
    val rpId: String,
    val clientDataHash: ByteArray,
    val allowList: List<PublicKeyCredentialDescriptor>?,
    val extensions: Map<String, Any>?,
    val options: Map<String, Boolean>?,
    val pinAuth: ByteArray?,
    val pinProtocol: Int?
)
```

### AssertionObject

```kotlin
data class AssertionObject(
    val credentialId: ByteArray,
    val authData: ByteArray,
    val signature: ByteArray,
    val user: Map<String, Any>?
)
```

## Enums

### Fido2Version

```kotlin
enum class Fido2Version {
    FIDO2_0,
    FIDO2_1
}
```

### Transport

```kotlin
enum class Transport {
    USB,
    NFC,
    BLE,
    INTERNAL
}
```

### ConnectionState

```kotlin
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ACTIVE
}
```

### VerificationResult

```kotlin
sealed class VerificationResult {
    object Success : VerificationResult()
    data class Failed(val reason: String) : VerificationResult()
    data class Cancelled(val reason: String) : VerificationResult()
}
```

## Protocol Messages

### CTAP2 Command Codes

```kotlin
object Ctap2Command {
    const val MAKE_CREDENTIAL = 0x01
    const val GET_ASSERTION = 0x02
    const val GET_INFO = 0x04
    const val CLIENT_PIN = 0x06
    const val RESET = 0x07
    const val GET_NEXT_ASSERTION = 0x08
    const val CREDENTIAL_MANAGEMENT = 0x0A
}
```

### CTAP2 Status Codes

```kotlin
object Ctap2Status {
    const val SUCCESS = 0x00
    const val INVALID_COMMAND = 0x01
    const val INVALID_PARAMETER = 0x02
    const val INVALID_LENGTH = 0x03
    const val INVALID_SEQ = 0x04
    const val TIMEOUT = 0x05
    const val CHANNEL_BUSY = 0x06
    const val LOCK_REQUIRED = 0x07
    const val INVALID_CHANNEL = 0x08
    const val CBOR_UNEXPECTED_TYPE = 0x11
    const val INVALID_CBOR = 0x12
    const val MISSING_PARAMETER = 0x13
    const val LIMIT_EXCEEDED = 0x14
    const val SUPPORTS_EXTENSION = 0x15
    const val VALIDATION_ERROR = 0x16
    const val CREDENTIAL_EXCLUDED = 0x19
    const val PROCESSING = 0x21
    const val VALID_CREDENTIAL = 0x22
    const val INVALID_CREDENTIAL = 0x23
    const val USER_ACTION_PENDING = 0x24
    const val USER_ACTION_TIMEOUT = 0x25
    const val USER_ACTION_DENIED = 0x26
    const val NO_OPERATIONS = 0x27
    const val UNSUPPORTED_ALGORITHM = 0x2B
    const val OPERATION_DENIED = 0x2C
    const val KEY_STORE_FULL = 0x2D
    const val NOT_BUSY = 0x2E
    const val NO_OPERATION_PENDING = 0x2F
    const val UNSUPPORTED_OPTION = 0x2F
    const val INVALID_OPTION = 0x30
    const val KEEPALIVE_CANCEL = 0x31
    const val NO_CREDENTIALS = 0x32
    const val USER_ACTION_TIMEOUT = 0x33
    const val NOT_ALLOWED = 0x34
    const val PIN_INVALID = 0x35
    const val PIN_BLOCKED = 0x36
    const val PIN_AUTH_INVALID = 0x37
    const val PIN_NOT_SET = 0x38
    const val PIN_REQUIRED = 0x39
    const val PIN_POLICY_VIOLATION = 0x3A
    const val PIN_TOKEN_EXPIRED = 0x3B
    const val REQUEST_TOO_LARGE = 0x3C
    const val ACTION_TIMEOUT = 0x3D
    const val UP_REQUIRED = 0x3E
}
```

## HID Report Format

### Input Report (Host → Authenticator)

```kotlin
data class HidInputReport(
    val channelId: Int,
    val command: Int,
    val data: ByteArray
)
```

### Output Report (Authenticator → Host)

```kotlin
data class HidOutputReport(
    val channelId: Int,
    val status: Int,
    val data: ByteArray
)
```

## Error Handling

### Fido2Exception

```kotlin
sealed class Fido2Exception(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidCommand(message: String) : Fido2Exception(message)
    class InvalidParameter(message: String) : Fido2Exception(message)
    class Timeout(message: String) : Fido2Exception(message)
    class UserVerificationRequired(message: String) : Fido2Exception(message)
    class UserVerificationDenied(message: String) : Fido2Exception(message)
    class CredentialNotFound(message: String) : Fido2Exception(message)
    class KeyStoreFull(message: String) : Fido2Exception(message)
    class UnsupportedAlgorithm(message: String) : Fido2Exception(message)
    class CborError(message: String) : Fido2Exception(message)
    class BluetoothError(message: String) : Fido2Exception(message)
}
```

## Performance Requirements

- **Command Processing**: < 200ms for all CTAP2 operations
- **HID Report Latency**: < 50ms for report transmission
- **User Verification**: < 5s for biometric, < 10s for PIN
- **Credential Storage**: < 100ms for store/retrieve operations
- **Concurrent Sessions**: Support up to 3 simultaneous HID connections

## Security Requirements

- All private keys must be stored in Android KeyStore
- Biometric verification required for credential operations
- PIN fallback with rate limiting
- Memory zeroing for sensitive data
- CBOR encoding/decoding validation
- HID report integrity checking

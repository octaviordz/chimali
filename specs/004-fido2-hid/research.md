# Research Findings: FIDO2 Virtual Authenticator via BluetoothHidDevice

**Date**: 2026-03-01  
**Feature**: 004-fido2-hid

## Reference Implementation Analysis

### WIOsense rauth-android Library

**Decision**: Use as primary reference for CTAP2/FIDO2 implementation  
**Rationale**: Well-documented, production-ready FIDO2 library specifically designed for Android with Bluetooth HID support. Provides clean separation between Authenticator logic and transport layer via TransactionManager.

**Key Components**:
- **Authenticator**: Implements CTAP2 protocol operations (MakeCredential, GetAssertion, GetInfo, PIN management)
- **TransactionManager**: Handles transport layer abstraction and CTAP message handshakes
- **Security**: Uses Android KeyStore with StrongBox support, biometric authentication via BiometricPrompt

**Architecture Benefits**:
- Clean separation of concerns (Authenticator vs Transport)
- Android KeyStore integration for secure credential storage
- BiometricPrompt integration for user verification
- Support for both CTAP1 (U2F) and CTAP2 (FIDO2)
- Bluetooth HID device profile support

### WioKey Android App

**Decision**: Use as UI/UX reference for Bluetooth HID integration  
**Rationale**: Complete implementation using rauth-android library with working Bluetooth HID device emulation.

**Key Insights**:
- Uses Android 9.0+ BluetoothHidDevice API
- Implements proper HID descriptor configuration
- Handles cross-platform compatibility (Windows 10, macOS, Linux)

## Technical Decisions

### FIDO2 Protocol Support

**Decision**: Implement both FIDO2.0 and FIDO2.1  
**Rationale**: Maximum compatibility and future-proofing. Reference libraries support both versions with minimal overhead.

### User Verification

**Decision**: Biometric (fingerprint/face) + PIN fallback  
**Rationale**: Aligns with Android BiometricPrompt best practices and reference implementation approach. StrongBox integration provides hardware-backed security.

### Credential Storage

**Decision**: 50 credentials maximum  
**Rationale**: Industry standard capacity that balances user needs with storage constraints. Reference implementations use similar limits.

### Architecture Approach

**Decision**: Clean Architecture with MVI pattern + Hilt DI  
**Rationale**: Aligns with Chimali Constitution requirements. Reference implementation uses similar modular approach.

## Technology Stack

**Language**: Kotlin (100% for Android layers)  
**Dependencies**:
- AndroidX BiometricPrompt for user verification
- Android KeyStore/StrongBox for credential storage
- BluetoothHidDevice API for HID emulation
- SQLCipher + SQLDelight for encrypted persistence
- Hilt for dependency injection
- Jetpack Compose (Material Design 3) for UI

**Testing**: JUnit 5 + MockK for unit tests, Compose UI Testing for integration

## Security Considerations

- All private keys stored in Android KeyStore with StrongBox when available
- Biometric authentication required for credential operations
- PIN fallback for devices without biometric support
- Memory zeroing for sensitive data (per Constitution)
- CBOR library for FIDO2 message encoding/decoding

## Performance Targets

- Bluetooth HID operations: < 200ms (per Constitution)
- Registration flow: < 30 seconds
- Authentication flow: < 5 seconds
- Stable connection: 10+ minutes continuous use
- 95%+ success rate for FIDO2 operations

## Integration Points

- Bluetooth HID device profile for cross-platform compatibility
- Android KeyStore for secure credential storage
- BiometricPrompt for user verification
- SQLDelight for local credential metadata
- Jetpack Compose for modern UI implementation

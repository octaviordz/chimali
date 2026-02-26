# Phase 0 Research: FIDO2 Virtual Authenticator

**Date**: 2025-02-25  
**Status**: Research Complete  
**Objective**: Investigate technical feasibility, dependencies, and implementation approaches for FIDO2 Virtual Authenticator via Bluetooth HID

## Research Summary

### Bluetooth HID Device Implementation

**Android BluetoothHidDevice API Research**:
- **Minimum Requirements**: Android 9.0 (API 28) - confirmed in BRD constraints
- **Capabilities**: Full HID device emulation, supports custom HID descriptors
- **Limitations**: Requires BLUETOOTH_ADMIN and BLUETOOTH_PRIVILEGED permissions
- **Reference Implementation**: `wiokey-android` project provides Java-based FIDO2 HID logic to port

**FIDO2 Protocol Implementation**:
- **Standard**: FIDO2/WebAuthn Level 2 compliance required
- **HID Usage Page**: 0xF1D0 (FIDO Alliance)
- **Protocol Layers**: CTAP2 (Client to Authenticator Protocol) over HID
- **Testing Tools**: WebAuthn.io, Yubico Demo, FIDO Conformance Tools

### Android Credential Manager Integration

**Passkey Storage Research**:
- **API**: Android Credential Manager (API 28+)
- **Capabilities**: Create, store, and retrieve passkeys
- **Security**: Integration with Android KeyStore and biometric authentication
- **Metadata**: User-friendly names, relying party information

**Key Derivation Research**:
- **Standard**: IETF draft-dijkhuis-cfrg-hdkeys-06 (HDK-ECDH-P256)
- **Implementation**: Hierarchical deterministic key derivation from Master Seed
- **Mnemonic**: BIP39 for seed generation and recovery
- **Security**: Quantum-resistant algorithms when supported (ML-KEM/Kyber)

### Performance Requirements Analysis

**Bluetooth HID Latency**:
- **Target**: <200ms end-to-end (BRD NFR-PERF-030)
- **Factors**: Bluetooth connection latency, cryptographic operations, UI response
- **Optimization**: Asynchronous operations, connection pooling, efficient crypto

**Memory Management**:
- **Constraint**: Zero memory leaks, <100MB usage
- **Approach**: Explicit memory zeroing, volatile structures for sensitive data
- **Monitoring**: LeakCanary integration, Android Profiler validation

### Security Considerations

**Encryption Requirements**:
- **Primary**: AES-256-GCM for all sensitive data
- **PQC**: ML-KEM/Kyber when hardware supports it
- **Key Storage**: Android KeyStore with Strongbox when available
- **Memory Security**: Zero out sensitive data immediately after use

**Threat Model**:
- **Man-in-the-Middle**: Prevented by Bluetooth pairing and FIDO2 protocol
- **Device Theft**: Protected by biometric/PIN authentication
- **Replay Attacks**: Prevented by FIDO2 challenge-response mechanism
- **Side-channel**: Mitigated by constant-time crypto operations

### Cross-Platform Compatibility

**Desktop Support**:
- **Windows**: Native Bluetooth HID support, tested on Windows 10/11
- **macOS**: Native Bluetooth HID support, tested on macOS 12+
- **Linux**: Varies by distribution, BlueZ 5.0+ recommended
- **Testing**: WebAuthn.io for browser compatibility validation

### Implementation Dependencies

**Core Libraries**:
- **Android SDK**: BluetoothHidDevice, Credential Manager, BiometricPrompt
- **Cryptography**: Bouncy Castle for PQC, Android KeyStore integration
- **UI**: Jetpack Compose, Material Design 3 components
- **Testing**: JUnit5, MockK, Espresso, FIDO2 test tools

**External References**:
- **wiokey-android**: Java implementation to port to Kotlin
- **FIDO2 Specifications**: Official FIDO Alliance documentation
- **Android Samples**: BluetoothHidDevice and Credential Manager examples

## Technical Risks & Mitigations

### High Risk
1. **Bluetooth HID Privileges**: Requires privileged permissions
   - **Mitigation**: Early testing on target devices, fallback strategies
2. **FIDO2 Protocol Complexity**: CTAP2 implementation is non-trivial
   - **Mitigation**: Leverage existing implementations, extensive testing

### Medium Risk
1. **Performance Targets**: <200ms latency challenging
   - **Mitigation**: Performance profiling, optimization focus
2. **Cross-Platform Variability**: Different desktop Bluetooth stacks
   - **Mitigation**: Comprehensive testing on all target platforms

### Low Risk
1. **Credential Manager Integration**: Well-documented Android API
   - **Mitigation**: Follow Android best practices, sample code

## Research Conclusions

**Feasibility**: ✅ Confirmed feasible
- All required APIs available in Android 9.0+
- Reference implementations exist for guidance
- Performance targets achievable with optimization

**Implementation Approach**:
1. Port and modernize `wiokey-android` FIDO2 logic to Kotlin
2. Implement Bluetooth HID device using Android BluetoothHidDevice API
3. Integrate with Android Credential Manager for passkey storage
4. Apply Master Seed architecture with HDK-ECDH-P256 key derivation
5. Implement security controls and performance optimizations

**Next Steps**: Proceed to Phase 1 design with data modeling and contract definitions.

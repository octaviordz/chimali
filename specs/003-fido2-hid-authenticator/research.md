# Research: FIDO2 HID Virtual Authenticator

## Research Tasks

1.  **FIDO2/CTAP2 Protocol in Rust**: Identify existing Rust libraries for CTAP2 implementation or verify if implementing from scratch is feasible.
2.  **Android Bluetooth HID Profile**: Research `BluetoothHidDevice` API best practices and common pitfalls when emulating a security key.
3.  **Attestation Strategy**: Confirm the "Self-Attestation" approach for software-based authenticators and its compatibility with major Browsers/RPs.
4.  **HID Report Map for FIDO2**: Define the necessary HID descriptor and report map for a FIDO2 security key.

## Findings

### 1. FIDO2/CTAP2 Protocol Implementation
- **Decision**: Leverage Bitwarden's [**passkey-rs**](https://github.com/bitwarden/passkey-rs) (specifically the `passkey-authenticator` crate) as the core CTAP 2.0 logic.
- **Rationale**: 
    - **Google FIDO2 APIs** on Android are high-level and intended for Relying Parties or Credential Providers (app-to-app), but they do not expose a CTAP2 packet parser for HID/USB transports.
    - **passkey-rs** provides a memory-safe, production-ready Rust implementation of the CTAP 2.0 protocol (MakeCredential, GetAssertion).
    - It is already designed for cross-platform use via UniFFI, aligning with our architecture.
- **Alternatives considered**: 
    - **OpenSK (Google)**: Excellent reference but more focused on hardware embedded systems (Tock OS).
    - **Scratch implementation**: Rejected to avoid "reinventing the wheel" and potential security bugs in protocol handling.

### 2. Android Bluetooth HID Profile
- **Decision**: Use `BluetoothHidDevice` with a foreground service to maintain connection.
- **Rationale**: Android SDK 28+ provides the necessary APIs for HID emulation.

### 3. Attestation Strategy
- **Decision**: Use Self-Attestation.
- **Rationale**: Standard for software authenticators; widely accepted except for high-assurance RPs.

### 4. HID Report Map for FIDO2
- **Decision**: Follow the FIDO HID protocol specification.
- **Rationale**: Ensures compatibility with standard HID drivers on Windows/macOS/Linux.

## Detailed Integration from Research

Following analysis of `rauth-android` and `wiokey-android`, the following components are identified for reuse/porting:

### 1. HID Report Descriptor (from `wiokey-android/Constants.java`)
- **Action**: Replace the skeleton keyboard descriptor in `BluetoothHidConstants.kt` with the verified FIDO-compliant HID descriptor.
- **Benefit**: Ensures immediate compatibility with Windows, macOS, and Linux built-in FIDO HID drivers.

### 2. Bluetooth HID Registration & SDP (from `wiokey-android/HidDeviceApp.java`)
- **Action**: Port the registration logic and SDP settings (Service Name, Description, Provider, Subclass).
- **Benefit**: Correctly identifies the device as a "Virtual FIDO Key" to the host OS.

### 3. FIDO HID Framing logic (from `rauth-android/Framing.java`)
- **Action**: Implement the `Framing` logic to handle HID report fragmentation (Init, Continuation packets) and Channel ID management.
- **Benefit**: Correctly reassembles raw HID reports into complete CTAP2/U2F messages before passing them to the Authenticator.

### 4. Transaction Dispatch (from `rauth-android/TransactionManager.java`)
- **Action**: Use the `TransactionManager`'s approach to dispatching `Msg` (U2F), `Cbor` (CTAP2), and `Ping` commands.
- **Integration**: Unlike `rauth-android`, we will dispatch these messages to the **Bitwarden `passkey-rs`** library instead of a custom Kotlin authenticator.

## Resources & References

### Inspiration & Core Logic
- **[rauth-android](https://github.com/WIOsense/rauth-android)**: Core library for FIDO2 HID framing and transaction management.
- **[wiokey-android](https://github.com/octaviordz/wiokey-android)**: Reference application for Bluetooth HID configuration and user-facing pairing logic.
- **HidPeripheral**: Demo for Android Bluetooth HID peripheral emulation.
- **Allthenticate**: Inspiration for mobile-to-desktop authentication.

### Protocol & Cryptography
- **passkey-rs (Bitwarden)**: Core CTAP 2.0 authenticator implementation in Rust.
- **Awesome WebAuthn**: Curated list of FIDO2/WebAuthn resources.
- **IETF Draft: HD Keys**: [Deterministic Key Derivation for Ed25519 and Ed448](https://www.ietf.org/archive/id/draft-dijkhuis-cfrg-hdkeys-01.html).
- **Hybrid Hierarchical Deterministic Derivation (HHD)**: [Blogpost](https://hackmd.io/abYfydDxRMGkwguLiAqVbg).

### Testing & Validation
- **WebAuthn.io**: FIDO2/WebAuthn playground for testing registration and auth.
- **USBHIDTerminal**: Useful for testing raw HID communication packets.
- **FIDO Conformance Tools**: Official validation for protocol compliance.

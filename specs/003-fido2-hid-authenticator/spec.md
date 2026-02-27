# Feature Specification: FIDO2 HID Virtual Authenticator

**Feature Branch**: `003-fido2-hid-authenticator`  
**Created**: 2026-02-26  
**Status**: Draft  
**Input**: User description: "specify requirement - **FR-HID-010**: Act as a FIDO2 Virtual Authenticator via BluetoothHidDevice. Take into account the requirement for passkey support."

## Clarifications

### Session 2026-02-26
- Q: Should the implementation focus exclusively on Bluetooth HID, or should the FIDO2/CTAP2 layer be designed to support other transports (NFC/USB) in the future? → A: Multi-transport Ready (Design CTAP2 layer to be transport-agnostic)


## User Scenarios & Testing *(mandatory)*

### User Story 1 - Desktop Authentication (Priority: P1)

As a user, I want to use my Android phone as a security key for my laptop so that I can sign in to websites and apps without typing passwords.

**Why this priority**: Core value proposition of the feature - using the phone as a hardware security key replacement.

**Independent Test**: Can be tested by pairing the phone with a PC, triggering a WebAuthn request on the PC, and confirming the authentication on the phone.

**Acceptance Scenarios**:

1. **Given** the phone is paired as a Bluetooth HID device to a laptop, **When** the laptop requests FIDO2 authentication, **Then** the phone displays a confirmation prompt.
2. **Given** the user confirms the authentication on the phone, **When** the confirmation is sent via HID, **Then** the laptop successfully signs the user in.

---

### User Story 2 - Passkey Registration (Priority: P2)

As a user, I want to create a new passkey for a service on my laptop and store it securely in Chimali via the HID interface.

**Why this priority**: Necessary for the lifecycle of passkeys; users need to be able to create them, not just use existing ones.

**Independent Test**: Can be tested by initiating a "Register" flow on a FIDO2-compliant website on a desktop and verifying the passkey is stored in Chimali.

**Acceptance Scenarios**:

1. **Given** the phone is connected via HID, **When** the desktop initiates FIDO2 registration, **Then** Chimali prompts the user to create and save a new passkey.

---

### User Story 3 - Connection Management (Priority: P3)

As a user, I want to see which devices are currently paired and manage those connections easily.

**Why this priority**: Crucial for security and usability when multiple desktop devices are used.

**Independent Test**: Can be tested by listing paired devices in the UI and disconnecting one.

**Acceptance Scenarios**:

1. **Given** multiple paired devices, **When** the user opens the connection manager, **Then** all devices and their connection status are visible.

---

### Edge Cases

- **Bluetooth Disconnect**: What happens when the Bluetooth connection drops during an authentication handshake?
- **Unsupported Host**: How does the system handle hosts that do not support FIDO2 over HID or have incompatible Bluetooth stacks?
- **Resource Contention**: How does the `BluetoothHidDevice` handle other apps trying to use the same profile?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST register as a FIDO2 compliant HID device over Bluetooth.
- **FR-002**: System MUST implement the FIDO2/CTAP2 protocol in a transport-agnostic layer (supporting future NFC/USB expansion).
- **FR-003**: System MUST support the creation of resident keys (Passkeys) via the HID interface.
- **FR-004**: System MUST prompt for user presence (e.g., biometrics or confirmation button) before responding to a signing request.
- **FR-005**: System MUST securely retrieve credentials from the vault to fulfill authentication requests.
- **FR-006**: System MUST handle multiple concurrent FIDO2 requests by queuing or informing the user.
- **FR-007**: System MUST provide a persistent indicator when the HID service is active in the background.
- **FR-008**: System MUST allow users to view and manage (disconnect/unpair) paired host devices.

### Key Entities *(include if feature involves data)*

- **FIDO2 Credential**: Represents a registered credential (public key, credential ID, user handle).
- **Security Key Model**: The logical representation of the Virtual Authenticator state.
- **HID Report**: The data packets exchanged between the phone and host.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can successfully complete FIDO2 authentication on a Windows/macOS/Linux host in under 5 seconds (from prompt to completion).
- **SC-002**: The 100% of WebAuthn.io registration and login tests pass when using Chimali as the security key.
- **SC-003**: HID connection latency remains below 200ms for individual report exchanges.
- **SC-004**: System successfully maintains HID registration across app restarts and background transitions.

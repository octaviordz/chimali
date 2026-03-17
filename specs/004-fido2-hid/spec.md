# Feature Specification: FIDO2 Virtual Authenticator via BluetoothHidDevice

**Feature Branch**: `004-fido2-hid`  
**Created**: 2026-03-01  
**Status**: Draft  
**Input**: User description: "FR-HID-010: Act as a FIDO2 Virtual Authenticator via BluetoothHidDevice. Take into account the requirement for passkey support. For reference look into https://github.com/octaviordz/wiokey-android and https://github.com/WIOsense/rauth-android"

## Clarifications

### Session 2026-03-01

- Q: Which FIDO2 protocol version(s) should the virtual authenticator support? → A: Both FIDO2.0 and FIDO2.1
- Q: What user verification method should be implemented for FIDO2 operations? → A: Biometric (fingerprint/face) + PIN fallback
- Q: How many passkey credentials should the device support storing? → A: 50 credentials maximum

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.
  
  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

### User Story 1 - FIDO2 Registration (Priority: P1)

As a user, I want to register a new passkey credential using the device as a FIDO2 authenticator so that I can authenticate to services without passwords.

**Why this priority**: This is the foundational capability that enables all other FIDO2 functionality. Without registration, no authentication can occur.

**Independent Test**: Can be fully tested by initiating a FIDO2 registration flow with a compatible service and verifying successful credential creation on the device.

**Acceptance Scenarios**:

1. **Given** the device is paired via Bluetooth, **When** a FIDO2 registration request is received, **Then** the device must create and store a new credential
2. **Given** a registration request, **When** user consent is provided, **Then** the credential must be securely stored and the public key returned to the relying party

---

### User Story 2 - FIDO2 Authentication (Priority: P1)

As a user, I want to authenticate to services using my stored passkey credentials so that I can log in without passwords.

**Why this priority**: This is the primary use case for FIDO2 authenticators - enabling passwordless authentication.

**Independent Test**: Can be fully tested by initiating a FIDO2 authentication flow with a service where a credential is already registered and verifying successful authentication.

**Acceptance Scenarios**:

1. **Given** a stored credential exists, **When** a FIDO2 authentication request is received, **Then** the device must generate the appropriate cryptographic response
2. **Given** multiple credentials, **When** authentication is requested, **Then** the user must be able to select the correct credential

---

### User Story 3 - Credential Management (Priority: P2)

As a user, I want to view and manage my stored passkey credentials so that I can maintain control over my authentication methods.

**Why this priority**: Users need visibility and control over their stored credentials for security and privacy reasons.

**Independent Test**: Can be fully tested by accessing the credential management interface and verifying listing, selection, and deletion operations work correctly.

**Acceptance Scenarios**:

1. **Given** stored credentials exist, **When** the user views the credential list, **Then** all credentials must be displayed with identifying information
2. **Given** a selected credential, **When** the user chooses to delete it, **Then** the credential must be permanently removed from storage

---

[Add more user stories as needed, each with an assigned priority]

### Edge Cases

- What happens when Bluetooth connection is lost during an operation?
- **Bluetooth Connection Recovery**: When Bluetooth connection is lost during operation, system must queue the operation for retry and notify user with option to continue or cancel
- **Connection State Persistence**: Maintain operation state across connection interruptions and allow seamless resume when reconnected
- How does system handle credential storage when device memory is full?
- What happens when user denies consent during registration/authentication?
- How does system handle malformed FIDO2 requests from relying parties?
- What happens when battery is critically low during authentication?

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-HID-010**: System MUST act as a FIDO2 Virtual Authenticator via BluetoothHidDevice
- **FR-HID-011**: System MUST support complete FIDO2 ceremony operations including registration, authentication, and credential management
- **FR-HID-014**: System MUST implement proper user consent mechanisms
- **FR-HID-021**: System MUST support biometric (fingerprint/face) verification with PIN fallback for user authentication
- **FR-HID-015**: System MUST securely store private keys and credentials
- **FR-HID-016**: System MUST handle multiple credentials for different relying parties
- **FR-HID-022**: System MUST support storage of up to 50 passkey credentials per user
- **FR-HID-017**: System MUST provide Bluetooth HID device functionality
- **FR-HID-018**: System MUST support credential enumeration and management
- **FR-HID-019**: System MUST implement proper error handling for FIDO2 protocol failures
- **FR-HID-020**: System MUST support both FIDO2.0 and FIDO2.1 protocol versions
- **FR-HID-023**: System MUST provide a secure copy mechanism that automatically clears sensitive data (e.g. mnemonic seeds) from the system clipboard within 60 seconds of the copy action, avoiding prohibited background monitoring.

### Key Entities *(include if feature involves data)*

- **Passkey Credential**: Represents a FIDO2 credential containing private key, relying party information, and user metadata
- **Relying Party**: Represents the service or website that the credential is associated with
- **User Consent Record**: Represents a record of user approval for FIDO2 operations
- **Bluetooth HID Session**: Represents an active Bluetooth HID connection with a host device

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
-->

### Measurable Outcomes

- **SC-001**: Users can complete FIDO2 registration in under 30 seconds from request to credential storage
- **SC-002**: FIDO2 authentication completes in under 5 seconds from request to response
- **SC-003**: Device maintains stable Bluetooth HID connection for at least 10 minutes of continuous use
- **SC-004**: 95% of FIDO2 operations (registration, authentication, credential enumeration) complete successfully without protocol errors, measured over 100 consecutive operations
- **SC-005**: Users can successfully authenticate to at least 3 different relying party services
- **SC-006**: Credential storage remains secure and accessible after device restart

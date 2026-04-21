# Feature Specification: Fix Dev Tools QR scan button

**Feature Branch**: `009-fix-devtools-qr-scan`  
**Created**: 2026-04-20
**Status**: Draft  
**Input**: User description: "Fix regression in 'Dev Tools' screen the 'Scan QR Code' button is not working."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Import Mnemonic via QR Scan (Priority: P1)

As a developer using the Dev Tools, I want to scan a QR code containing a BIP39 mnemonic so that I can quickly restore a development wallet state without manual typing.

**Why this priority**: This is a core debugging tool for mnemonic recovery. Manual 24-word entry is error-prone and slow.

**Independent Test**: Can be fully tested by navigating to Dev Tools, clicking the scan button, and scanning a known valid QR code.

**Acceptance Scenarios**:

1. **Given** the user is on the Development Tools screen, **When** they click the "Scan QR Code" button, **Then** the system should request camera permissions (if not already granted) and launch the camera scanner interface.
2. **Given** the camera scanner is active, **When** a valid BIP39 mnemonic QR code is detected, **Then** the scanner should close and the mnemonic should be imported into the current session state.
3. **Given** the camera scanner is active, **When** the user clicks the "Back" button or dismisses the scanner, **Then** they should return to the Dev Tools screen with no state changes.

---

### User Story 2 - Permission Recovery (Priority: P2)

As a developer, I want the scan button to handle denied permissions gracefully so that I can recover from a "Permission Denied" state without restarting the app.

**Why this priority**: Silent failures when permissions are missing create a bad UX and waste developer time.

**Independent Test**: Deny camera permission, click the button, and verify a helpful message or rationale is shown.

**Acceptance Scenarios**:

1. **Given** camera permission was previously denied, **When** the user clicks "Scan QR Code", **Then** the system should show a rationale dialog or redirect to app settings.

---

### Edge Cases

- **Invalid QR Data**: What happens when the scanned QR code does not contain a valid BIP39 mnemonic?
  - *Requirement*: System should show an error notification indicating "Invalid Mnemonic QR" and stay on the scanner or return to Dev Tools without importing.
- **Low Light/Blurry Scan**: How does the system handle failed detection?
  - *Requirement*: The scanner should remain active until a valid code is found or the user cancels.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST launch a camera-based scanner when the "Scan QR Code" button is clicked.
- **FR-002**: System MUST parse QR code content to extract recovery phrase strings.
- **FR-003**: System MUST validate the scanned string against standard recovery phrase rules before importing.
- **FR-004**: System MUST update the development tools state with the new recovery phrase upon successful scan.
- **FR-005**: System MUST request necessary camera permissions at runtime before accessing the camera.

### Key Entities

- **Mnemonic (BIP39)**: A sequence of 24 words used to derive the wallet master seed.
- **Camera Scanner**: The interface component responsible for capturing and parsing the QR code.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: "Scan QR Code" button responds to clicks 100% of the time.
- **SC-002**: Camera preview launches within 500ms of permission grant/validation.
- **SC-003**: Valid QR codes are recognized and processed in under 1 second of being in frame.
- **SC-004**: No "silent failures" (clicks with no visual feedback) occur when the button is pressed.

## Assumptions

- **Camera Availability**: The device or emulator has a working camera or camera simulation.
- **Debug Only**: This feature is restricted to development builds (as indicated by "Dev Tools").
- **Existing ML Kit/CameraX**: The project already has the necessary dependencies configured (as seen in `libs.versions.toml`).

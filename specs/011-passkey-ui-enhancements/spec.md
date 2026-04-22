# Feature Specification: Passkey UI Enhancements

**Feature Branch**: `011-passkey-ui-enhancements`  
**Created**: 2026-04-21  
**Status**: Draft  
**Input**: User description: "Manage saved passkeys presentation enhancements. The delete feature should be similar to the delete/remove device already implemented in 'Devices list' at 'Chimali Authenticator' screen. Remove the 'Custom Label / Note' from Passkey Details screen."

## Clarifications

### Session 2026-04-21
- Q: Should we standardize on the "Long" snackbar duration (match Devices list) or keep "Short"? → A: Option A - **Long** (Match Devices list behavior, ~10s).
- Q: Should we use a generic icon (Fingerprint/Key) or keep the dynamic first-letter circle? → A: Option A - **Generic Icon** (Use a standard "Fingerprint" or "Key" icon).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Swipe to Delete Passkey (Priority: P1)

As a user, I want to quickly delete a passkey using a swipe gesture, similar to how I manage my paired devices, so that the management experience is consistent and efficient.

**Why this priority**: Consistency across the app is key for a premium user experience. Swipe-to-delete is a standard mobile interaction pattern already established in the "Devices" list.

**Independent Test**: Navigate to the Passkeys list, swipe a passkey item, and verify it is removed from the list and an "Undo" snackbar appears.

**Acceptance Scenarios**:

1. **Given** I am on the Passkeys list screen, **When** I swipe a passkey item to the left or right, **Then** the item should be removed from the list and an "Undo" snackbar should appear.
2. **Given** I have swiped to delete a passkey, **When** I tap "Undo" on the snackbar, **Then** the passkey should be restored to the list.
3. **Given** I have swiped to delete a passkey, **When** the snackbar disappears without interaction, **Then** the passkey should be permanently deleted.

### Edge Cases

- **Biometric Authentication**: Following the "Devices list" pattern, passkey deletion via swipe will NOT require biometric authentication. The "Undo" snackbar provides the safety net for accidental deletions. (Option B: Pattern Match)
- **Deletion Failure**: If the underlying storage fails to remove the passkey, the UI should restore the item and show an error message.
- **Last Passkey**: Deleting the last passkey should transition the screen to the "No passkeys yet" empty state.

---

### User Story 2 - Simplified Passkey Details (Priority: P1)

As a user, I want to see a clean and focused details view for my passkeys without unnecessary fields like "Custom Label / Note" that I don't use, so that the information is easier to digest.

**Why this priority**: Reducing clutter improves the legibility and "premium" feel of the application, especially for technical details like passkey metadata.

**Independent Test**: Open the details for any passkey and verify the "Custom Label / Note" field is missing.

**Acceptance Scenarios**:

1. **Given** I am on the Passkeys list screen, **When** I tap on a passkey to view its details, **Then** I should see technical metadata (RP ID, User Name, etc.) but NO "Custom Label / Note" field.
2. **Given** I am viewing passkey details, **When** I look for a way to edit the label, **Then** no such option should be visible.

---

### User Story 3 - Visual Consistency with Devices List (Priority: P2)

As a user, I want the Passkeys list to have the same polished look and feel as the Devices list, so the app feels like a cohesive product.

**Why this priority**: Enhances the "WOW" factor and visual excellence requested in the design guidelines.

**Independent Test**: Compare the layout and styling of the Passkeys list with the Devices list on the Home screen to ensure they share the same aesthetic.

**Acceptance Scenarios**:

1. **Given** I am on the Passkeys list screen, **When** I look at the items, **Then** they should use similar iconography, spacing, and typography as the "Paired Devices" items on the Home screen.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST implement `SwipeToDismissBox` for items in the `CredentialListScreen`.
- **FR-002**: System MUST display an "Undo" snackbar with `Long` duration (approx. 10s) after a swipe-to-delete action, matching the behavior in `PairedDevicesSection`.
- **FR-003**: System MUST remove the "Custom Label / Note" `OutlinedTextField` from `CredentialDetailsScreen`.
- **FR-004**: System MUST remove any UI elements and logic associated with updating the passkey label in the details view.
- **FR-005**: `CredentialItem` MUST be enhanced to match the `PairedDeviceItem` layout:
  - Use a standard **Fingerprint** or **Key** icon instead of the first-letter circle.
  - Refine typography and spacing to match the premium aesthetic of the Devices list.

### Key Entities *(include if feature involves data)*

- **PasskeyCredential**: Represents the saved FIDO2 credential.
  - `rpId`: Relying Party Identifier (e.g., "google.com")
  - `userName`: The user's account name.
  - `lastUsedAt`: Timestamp of last authentication.
  - `createdAt`: Timestamp of registration.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Swipe-to-delete interaction completes with visual feedback (snackbar) in under 500ms.
- **SC-002**: Zero occurrences of the "Custom Label / Note" field in the `CredentialDetailsScreen` after implementation.
- **SC-003**: 100% parity in swipe-to-delete behavior between "Devices" and "Passkeys" lists.

## Assumptions

- The underlying `CredentialManagementViewModel` already supports deletion and "Undo" logic, or can be easily extended to match the `PairedDevicesViewModel` pattern.
- The `label` field in `PasskeyCredential` can remain in the data model but will be ignored by the UI for now.
- Users have already registered at least one passkey to test the enhancements.

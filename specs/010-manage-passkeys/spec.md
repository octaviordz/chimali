# Feature Specification: Manage Saved Passkeys

**Feature Branch**: `010-manage-passkeys`  
**Created**: 2026-04-20  
**Status**: Draft  
**Input**: User description: "Implement 'Manage Saved Passkeys'"

## Clarifications

### Session 2026-04-20

- Q: Should the "Clear All" (bulk deletion of all passkeys) feature be accessible to users in the production environment? → A: C (Remove Entirely). Functionality is not built into the UI at all; manual deletion only.
- Q: Should users be able to rename a passkey? → A: B (Read-only). Display names provided by the service (RP) at registration are fixed and cannot be changed locally.
- Q: Should the RP icon be fetched from a remote service? → A: B (Privacy-First). Only use local placeholders or icons provided during registration to prevent leaking domain lists to 3rd party services.
- Q: Should there be an "Undo" option for passkey deletion? → A: A (Undo Snackbar). Provide a temporary safety net after deletion.
- Q: How should identical account names be distinguished? → A: B (Exact Display). Always show the exact string provided by the service (RP), even if it results in identical list entries.
- Q: What should be the default sort order? → A: By last used date (if available) or creation date, in descending order.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browse and View Passkeys (Priority: P1)

As a user, I want to see a clear list of all my saved passkeys so that I can keep track of which services I have registered with the app.

**Why this priority**: This is the fundamental requirement for "managing" passkeys. Without a list, management is impossible.

**Independent Test**: Can be fully tested by navigating to the "Manage Passkeys" screen and verifying that all previously created credentials are displayed with their respective Service Name (RP) and User Identifier.

**Acceptance Scenarios**:

1. **Given** the user has 5 saved passkeys, **When** they open the Manage Passkeys screen, **Then** they should see 5 distinct list items.
2. **Given** a passkey entry in the list, **When** viewed, **Then** it should show the Relying Party (RP) name, user display name, and creation date.

---

### User Story 2 - Search and Filter Passkeys (Priority: P2)

As a user with many saved credentials, I want to search for a specific passkey so that I can quickly find the one I want to manage without scrolling.

**Why this priority**: Improves usability as the number of saved passkeys grows.

**Independent Test**: Can be tested by typing a partial RP name in the search bar and verifying that the list updates in real-time to show only matching entries.

**Acceptance Scenarios**:

1. **Given** a search bar is present, **When** the user types "Google", **Then** only passkeys associated with google.com should be displayed.
2. **Given** no passkeys match the search term, **When** the search is active, **Then** a "No results found" message should be displayed.

---

### User Story 3 - Delete a Passkey (Priority: P1)

As a user, I want to be able to delete a passkey I no longer use so that I can maintain my digital privacy and keep my credential list clean.

**Why this priority**: Essential for lifecycle management and security (revocation).

**Independent Test**: Can be tested by selecting a passkey, clicking "Delete", and verifying it is no longer in the list or the underlying database.

**Acceptance Scenarios**:

1. **Given** a passkey is selected for deletion, **When** the user clicks "Delete", **Then** the system MUST prompt for a confirmation.
2. **Given** a deletion is confirmed, **When** the action is processed, **Then** the passkey MUST be permanently removed from the local vault.
3. **Given** a passkey was deleted, **When** the user clicks "Undo" on the post-deletion snackbar, **Then** the passkey MUST be restored.

---

### Edge Cases

- **What happens when the vault is empty?** The system should show an empty state illustration with a helpful message explaining how to add a passkey.
- **How does the system handle concurrent deletions?** The UI should be reactive to database changes, ensuring no crashes or stale data displays.
- **What if the RP has no name?** The system should fall back to displaying the RP ID (domain) as the primary identifier.
- **What if two passkeys have the same name?** The system will display them exactly as provided by the RP. Users can distinguish them by the creation date or list order.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST display a list of all stored FIDO2 credentials (passkeys).
- **FR-002**: System MUST provide a search bar to filter passkeys by RP Name or User Identifier.
- **FR-003**: System MUST allow deleting a credential via a long-press or a dedicated "delete" icon.
- **FR-004**: System MUST require user authentication (biometric or device PIN) before performing a deletion.
- **FR-005**: System MUST display the RP icon (fav-icon) where available.
- **FR-006**: System MUST show the creation timestamp for each credential.
- **FR-007**: System MUST provide an "Undo" option for a limited time (e.g., 5 seconds) after a passkey deletion.
- **FR-008**: System MUST sort passkeys by last used date (if available) or creation date in descending order by default.

### Key Entities *(include if feature involves data)*

- **Passkey (Credential)**: Represents a stored FIDO2 credential.
    - `rpId`: The domain associated with the key.
    - `userHandle`: Unique identifier for the user on that RP.
    - `displayName`: Human-readable name for the passkey.
    - `createdAt`: Timestamp of registration.
    - `signCount`: Number of times the key has been used (optional for display).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can locate a specific passkey among 50 entries in under 3 seconds using the search feature.
- **SC-002**: 100% of deletion attempts MUST be blocked until biometric/PIN authentication is successful.
- **SC-003**: The "Manage Passkeys" screen MUST load its initial list in under 300ms.
- **SC-004**: Visual consistency with Material Design 3 (M3) standards, achieving 60 FPS during list scrolling.

## Assumptions

- **Authentication reuse**: The existing biometric authentication module will be used for deletion confirmation.
- **Local Storage**: All passkey management happens locally; no cloud synchronization is in scope for this feature.
- **Hardware**: The device has at least one biometric sensor or a secure lock screen.
- **Fav-icons**: The system will only use local placeholders or icons provided during the registration ceremony to preserve user privacy.

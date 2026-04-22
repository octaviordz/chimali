# Research: Passkey UI Enhancements

## Deletion Pattern: Swipe-to-Delete with Undo

The "Devices list" implementation in `PairedDevicesViewModel` provides a robust pattern for "Undo" functionality without immediate database mutation:

- **Immediate Feedback**: Items are hidden from the UI immediately by adding their ID to a `_pendingDelete` state flow.
- **Undo Buffer**: A `removalEvents` channel triggers the UI to show a snackbar.
- **Commit/Undo**: 
    - If the user taps "Undo", the ID is removed from `_pendingDelete`.
    - If the snackbar times out, `commitRemove` is called to perform the actual database deletion.

### Decision
Adopt the `PairedDevicesViewModel` pattern in `CredentialManagementViewModel`.

- **Rationale**: Ensures consistency and provides a premium "safety net" for destructive actions.
- **Implementation**: 
    - Update `CredentialManagementState` to include `pendingDeleteIds: Set<String>`.
    - Modify `onIntent` to handle `PendingDelete`, `UndoDelete`, and `CommitDelete`.
    - Update `CredentialListScreen` to use `SwipeToDismissBox` and handle snackbar results.

## Iconography & Visuals

The current `CredentialItem` uses a `CircleShape` with a text character.

### Decision
Standardize on `Icons.Default.Fingerprint`.

- **Rationale**: Modern FIDO2/Passkey branding frequently uses fingerprint or key iconography. It aligns with the "premium" requirement and matches the Material 3 aesthetic.
- **Alternatives Considered**: `Icons.Default.Key` (Rejected as "Fingerprint" is more specific to modern passkeys/biometric auth).

## Credential Details Cleanup

The `CredentialDetailsScreen` currently includes an `OutlinedTextField` for a custom label.

### Decision
Remove the label input and associated update logic.

- **Rationale**: Simplifies the UI as requested. Most users rely on the RP ID (domain) and Username to identify passkeys.
- **Data Impact**: The `PasskeyCredential.label` field will remain in the domain model for backwards compatibility but will be treated as read-only or ignored by the UI.

## Typography

### Decision
Strictly enforce **Atkinson Hyperlegible** for all technical identifiers.

- **Rationale**: Aligns with Constitution principle **VI. Inclusion & Universal Accessibility**. Ensures RP IDs (e.g., `google.com`) are easily readable even for users with visual impairments.

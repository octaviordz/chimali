# Feature Specification: Vault Feature Completion (FR-VAULT-020)

**Feature Branch**: `053-vault-completion`

**Created**: 2026-09-07

**Status**: Draft

**Input**: User description: "Bring back and complete the 'Vault' feature. The current app shows the Vault screen but all interactive callbacks (Add, Item Detail, Labels) are no-ops. All UI screens exist but are not connected to any navigation graph. Payload serialization/encryption and decryption are also unimplemented."

## Background

The Vault feature (`001-store-credentials`) was partially implemented. All entry-form and detail screens (`PasswordEntryScreen`, `PasswordDetailScreen`, `CreditCardEntryScreen`, `CreditCardDetailScreen`, `SecureNoteEntryScreen`, `SecureNoteDetailScreen`, `LabelManagerScreen`) were built but are orphaned — they are not reachable from any navigation graph. In `AppNavGraph`, all action callbacks on `VaultListScreen` are empty stubs. Additionally, payload serialization (converting a form payload into an encrypted binary blob for storage) and decryption (converting stored bytes back into a displayable payload) were deferred and never implemented.

This feature spec defines the work to restore the Vault to a fully functional state.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Add a New Password Entry (Priority: P1)

As a user, I want to tap a button on the Vault list to open a form where I can enter a website, username, and password, save it, and see it appear in my vault list.

**Why this priority**: Adding items is the foundational action of the Vault. Without the ability to add, no other vault operation has meaning.

**Independent Test**: Can be fully tested by navigating from the Vault list to the Password entry form, filling in required fields, tapping Save, and verifying the new entry appears in the Vault list.

**Acceptance Scenarios**:

1. **Given** the user is on the Vault list screen, **When** they tap the floating action button (+), **Then** a type-selection prompt or direct form is shown allowing them to choose Password, Credit Card, or Secure Note.
2. **Given** the user selects "Password", **When** they fill in Title, Username, and Password and tap Save, **Then** the entry is encrypted and persisted, the user is returned to the Vault list, and the new entry is visible with the correct title.
3. **Given** the user leaves the required Title or Password field blank, **When** they attempt to save, **Then** the Save button is disabled and a validation hint is displayed.
4. **Given** the user taps Cancel on the entry form, **When** they confirm cancellation, **Then** no data is saved and the user is returned to the Vault list unchanged.

---

### User Story 2 - View and Copy a Stored Password (Priority: P1)

As a user, I want to tap a vault entry to view its decrypted details — including the password — so I can copy it to my clipboard when I need it.

**Why this priority**: Viewing details is the primary consumption action of the Vault; the data is worthless if it cannot be read back.

**Independent Test**: Can be fully tested by tapping an existing vault entry, verifying the decrypted title, username, and password are displayed, and confirming a copy action places the value in the clipboard.

**Acceptance Scenarios**:

1. **Given** the Vault list contains at least one password entry, **When** the user taps it, **Then** a detail screen is shown displaying the decrypted title, username, and website.
2. **Given** the user is on the Password detail screen, **When** they tap the eye icon on the password field, **Then** the password is revealed in a high-legibility font.
3. **Given** the user taps Copy on the password field, **When** 60 seconds have elapsed, **Then** the clipboard is automatically cleared of that sensitive value.
4. **Given** the user is on the detail screen, **When** they press back, **Then** any decrypted data is cleared from memory and they are returned to the Vault list.

---

### User Story 3 - Edit and Delete a Vault Entry (Priority: P1)

As a user, I want to edit an existing vault entry to update its fields, or delete it entirely if it is no longer needed.

**Why this priority**: Without edit and delete, the vault becomes a write-only store, undermining user trust and data hygiene.

**Independent Test**: Can be fully tested by opening a detail screen, tapping Edit to modify a field and save, then verifying the updated data is reflected in the list and detail view. Separately, deleting an entry and verifying it is removed from the list.

**Acceptance Scenarios**:

1. **Given** the user is on a detail screen, **When** they tap the Edit icon and change the title and username, then tap Save, **Then** the entry is updated in storage and the detail screen reflects the new values.
2. **Given** the user is on a detail screen, **When** they tap Delete and confirm the prompt, **Then** the entry is removed from storage and the user is returned to the Vault list with the entry gone.
3. **Given** the user taps Delete but then dismisses the confirmation dialog, **Then** no data is removed and the detail screen remains.

---

### User Story 4 - Add and View Credit Cards (Priority: P1)

As a user, I want to store and retrieve my credit card details (cardholder name, card number, expiration date, CVV) so they are readily accessible when needed.

**Why this priority**: Credit cards are a core credential type specified in FR-VAULT-010 alongside passwords.

**Independent Test**: Can be fully tested by opening the credit card entry form, filling in required fields, saving, and then opening the detail view to confirm the decrypted data is correctly displayed.

**Acceptance Scenarios**:

1. **Given** the user selects "Credit Card" from the Add menu, **When** they fill in cardholder name, card number, expiration, and CVV and tap Save, **Then** the card is encrypted and persisted and appears in the Vault list.
2. **Given** the user opens a saved credit card, **When** they view the detail screen, **Then** the full card number is masked by default and revealed only upon explicit tap with an eye icon.

---

### User Story 5 - Add and View Secure Notes (Priority: P2)

As a user, I want to store freeform text notes for recovery codes or arbitrary secrets so they are accessible within the vault.

**Why this priority**: Secure Notes are the third core credential type. They unblock users who need to store data that does not fit password or card formats.

**Independent Test**: Can be fully tested by opening the note entry form, entering title and body text, saving, and confirming the decrypted body is shown in the detail view.

**Acceptance Scenarios**:

1. **Given** the user selects "Secure Note" from the Add menu, **When** they fill in a title and body text and tap Save, **Then** the note is encrypted, persisted, and appears in the Vault list.
2. **Given** the user opens a saved secure note, **When** the detail screen is shown, **Then** the decrypted body text is fully visible.

---

### User Story 6 - Filter Vault Items by Label (Priority: P2)

As a user, I want to assign labels to vault entries and filter my list by label, so I can quickly find entries in a growing vault.

**Why this priority**: As the vault grows, organization becomes critical for usability, and the label infrastructure already exists in the database schema.

**Independent Test**: Can be fully tested by opening the Label Manager, creating a label, assigning it to an entry, and filtering the Vault list by that label to verify only the tagged entry is shown.

**Acceptance Scenarios**:

1. **Given** the user taps the label icon in the top bar, **When** they reach the Label Manager, **Then** they can create a new label with a name.
2. **Given** a label exists and the user is editing or creating a vault entry, **When** they assign the label to the entry and save, **Then** the entry is associated with that label in storage.
3. **Given** the user selects a label in the filter tab row on the Vault list, **When** the filter is applied, **Then** only entries tagged with that label are shown; tapping "All" restores the unfiltered view.

---

### Edge Cases

- What happens when the vault is entirely empty? The Vault list must show an empty-state message prompting the user to add their first item.
- What happens if a save fails due to an encrypted storage error? The user must see a clear, non-technical error message and must not lose their typed input.
- What happens if the user attempts to add an entry but the master seed has not been initialized (e.g., onboarding was skipped)? The system must display an informative error and prevent access to the encrypted vault.
- What happens if decryption of a stored payload fails (e.g., storage corruption)? The system must display an error on the detail screen and must not crash or show garbled data.
- What happens when the user leaves a partial entry form and navigates away? A discard confirmation prompt must appear before unsaved data is lost.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-VAULT-021**: The system MUST provide a navigation graph connecting `VaultListScreen` to type-selection, entry forms (`PasswordEntryScreen`, `CreditCardEntryScreen`, `SecureNoteEntryScreen`), detail screens (`PasswordDetailScreen`, `CreditCardDetailScreen`, `SecureNoteDetailScreen`), and the `LabelManagerScreen`.
- **FR-VAULT-022**: The system MUST implement payload serialization — converting a typed form payload (Password, CreditCard, SecureNote) into an AES-256-GCM encrypted binary blob before persisting it to the vault database.
- **FR-VAULT-023**: The system MUST implement payload deserialization — decrypting and parsing a stored binary blob back into a typed payload (Password, CreditCard, SecureNote) when the user opens a detail screen.
- **FR-VAULT-024**: The system MUST support full CRUD operations (Create, Read, Update, Delete) for all three vault item types: Password, Credit Card, and Secure Note.
- **FR-VAULT-025**: All credential detail screens MUST display sensitive fields (passwords, card numbers, CVVs) as masked by default with an explicit toggle to reveal them. Revealed values MUST be rendered using a high-legibility font that clearly distinguishes ambiguous characters.
- **FR-VAULT-026**: The system MUST clear sensitive decrypted field values from memory immediately upon navigating away from a detail or entry screen. The system MUST use mutable character/byte arrays for all sensitive in-memory representations.
- **FR-VAULT-027**: The system MUST copy sensitive values to the clipboard on user request and automatically clear that clipboard entry after 60 seconds.
- **FR-VAULT-028**: The system MUST allow users to create labels and assign them to vault entries. The Vault list MUST support filtering entries by label.
- **FR-VAULT-029**: The system MUST display a meaningful, user-friendly error message and maintain the user's current context when any vault read or write operation fails.
- **FR-VAULT-030**: The system MUST display an empty-state message on the Vault list when no entries exist or no entries match the active label filter.
- **FR-VAULT-031**: The system MUST show a discard-confirmation dialog if the user navigates away from an entry form with unsaved changes.

### Key Entities

- **VaultItem**: The encrypted container stored in the database. Contains an ID, type (PASSWORD, CREDIT_CARD, NOTE), title (for display in the list), an encrypted payload blob, CRDT state, creation and modification timestamps, backup status, and an owner identity ID.
- **PasswordPayload**: The decrypted, in-memory representation of a password entry — consisting of username, password, website URI, notes, and optional custom fields. All sensitive fields use mutable character arrays.
- **CreditCardPayload**: The decrypted, in-memory representation of a credit card entry — consisting of cardholder name, card number, expiration date, CVV, notes, and optional custom fields. All sensitive fields use mutable character arrays.
- **SecureNotePayload**: The decrypted, in-memory representation of a secure note — consisting of a title and body text. The body uses a mutable character array.
- **Label**: A user-defined tag with an ID and a name, used to organize vault entries.
- **VaultNavGraph**: The navigation component responsible for connecting all vault screens into a coherent user flow.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-VAULT-001**: A user can add a new password entry, navigate back to the list, and confirm the entry appears in under 15 seconds from tapping the Add button.
- **SC-VAULT-002**: A user can open a saved entry, reveal its password, copy it, and navigate back — with the clipboard automatically cleared within 60 seconds of the copy action.
- **SC-VAULT-003**: The Vault list correctly reflects all stored entries (the correct count and titles) immediately after creating, editing, or deleting any entry — with no stale state visible.
- **SC-VAULT-004**: Editing an existing entry updates its stored data; the previous values are no longer accessible from the detail view after saving.
- **SC-VAULT-005**: No decrypted credential data (password characters, card numbers, CVVs) remains visible in the UI or accessible in memory after the user navigates away from a detail screen.
- **SC-VAULT-006**: All three entry types (Password, Credit Card, Secure Note) can be created, viewed, edited, and deleted without application error or data loss.
- **SC-VAULT-007**: The Vault list maintains 60 FPS scrolling performance when 500 or more entries are stored.
- **SC-VAULT-008**: Label filtering correctly shows only entries with the selected label; switching to "All" restores the complete list immediately.

## Assumptions

- The Master Seed and encrypted database infrastructure (`EncryptedDriverFactory`, `VaultDatabase`) are already fully operational; this spec does not alter the encryption key derivation layer.
- The existing event-sourcing `AggregateService<VaultCommand, VaultState>` remains the write mechanism for vault entries; the serialization layer feeds encrypted payloads into existing commands (`VaultCommand.Create`, `VaultCommand.Update`, `VaultCommand.Delete`).
- The existing `VaultListScreen`, `PasswordEntryScreen`, `PasswordDetailScreen`, `CreditCardEntryScreen`, `CreditCardDetailScreen`, `SecureNoteEntryScreen`, `SecureNoteDetailScreen`, and `LabelManagerScreen` Composable screens are considered the UI baseline; their internal implementation may be refined but they are not to be rebuilt from scratch.
- Payload serialization format (binary encoding) is an implementation detail; the spec requires only that it is secure (encrypted), reversible (decryptable), and schema-compatible with future additions (additive schema evolution).
- The `ClipboardManagerService` for timed clipboard clearing already exists as a core service and does not need to be introduced by this feature.
- The onboarding flow and user preference for enabling the Vault feature are already in place; this spec assumes the user has enabled Vault during onboarding.
- Custom fields on entries are in-scope for display and storage, but a dedicated UI for adding/removing custom fields during entry creation is a stretch goal; the form may show existing custom fields for edit-in-place.

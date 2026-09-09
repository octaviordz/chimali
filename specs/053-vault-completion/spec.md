# Feature Specification: Vault Feature Completion (FR-VAULT-020)

**Feature Branch**: `053-vault-completion`

**Created**: 2026-09-07

**Status**: Draft

**Updated**: 2026-09-09 — T039 secret lifetime, retry, compatibility, UI-adapter, and coverage acceptance complete; approved Constitutions 1.0.0/1.1.0 govern application-owned cleanup, narrowly audited platform text adapters, and direct terminal-throw evidence. External-copy erasure is not claimed.

**Complexity**: L with the memory-security remediation, delivered in bounded ownership, codec, and UI verification increments; S for the save-routing correction alone. UI feasibility must be established before committing to the editor implementation.

**Input**: User description: "Bring back and complete the 'Vault' feature. The current app shows the Vault screen but all interactive callbacks (Add, Item Detail, Labels) are no-ops. All UI screens exist but are not connected to any navigation graph. Payload serialization/encryption and decryption are also unimplemented."

## Background

The Vault feature (`001-store-credentials`) was partially implemented. The existing password, credit card, secure note, and label-management screens are not reachable from the Vault list. Add, item-detail, and label actions do nothing. Saving entry contents securely and retrieving them for display also remain incomplete.

This feature spec defines the work to restore the Vault to a fully functional state.

Runtime validation on 2026-09-07 showed that saving a new entry terminates the application. Completion therefore includes reliable saves in the fully assembled application, with Vault and passkeys enabled together. Existing completed task markers do not establish that this acceptance condition has passed.

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
5. **Given** the application has just restarted with Vault and passkeys enabled and existing passkeys stored, **When** the user saves a valid password entry, **Then** the application remains open, the entry can be reopened after another restart, and existing passkeys remain usable.
6. **Given** a valid entry form and a storage failure, **When** the user taps Save, **Then** the form remains open with its input intact, a non-technical error is shown, and the user can retry after the failure is resolved without creating duplicate entries.
7. **Given** a Password, Credit Card, or Secure Note draft including custom fields, **When** key acquisition, protection of the contents, or storage fails, **Then** all fields remain intact only in the active editing session for retry, temporary operation copies are erased, and discarding the draft erases the remaining sensitive input.

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
4. **Given** an existing entry is opened for editing, **When** the user confirms discard or cancels an unchanged draft, **Then** the editor's sensitive contents are erased and returning to details displays the original stored values, including custom fields, without empty or stale values caused by cleanup.
5. **Given** the user changes a custom-field value and a save fails, **When** they retry and successfully save, **Then** the changed value is preserved, the stored entry is updated once, and reopening details shows the saved values after the draft has been erased.

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
- Vault and passkeys must coexist on both fresh and existing installations. Creating, editing, or deleting either type must not alter the other type's records.
- While a save is pending, repeated taps must not create duplicate entries or report success prematurely.
- Leaving or disposing an editor during pending work must erase the draft and release all operation copies when their operation ends or is cancelled. A late completion must not navigate a different editing session or resurrect discarded input. Cancellation cannot promise rollback of a write already committed; reopening shows the actual stored result.
- A rejected duplicate submission and cancellation before background work starts must not leave an extra copy of sensitive input behind.
- Configuration recreation and unexpected disposal must not persist secrets for automatic restoration. Recoverable failure retains input only while its editing session remains active; process death does not restore a plaintext draft.
- Existing encrypted entries containing Unicode, escaped characters, optional values, and custom fields must remain readable and editable after this remediation without a destructive reset or format migration.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-VAULT-021**: Users MUST be able to navigate from the Vault list to item-type selection, entry forms and details for Passwords, Credit Cards, and Secure Notes, and label management.
- **FR-VAULT-022**: The system MUST encrypt the contents of Password, Credit Card, and Secure Note entries before storing them, preserving all entered fields and protecting their confidentiality and integrity under the project's security requirements.
- **FR-VAULT-023**: When a user opens an entry's details, the system MUST decrypt its stored contents and display the saved fields for the correct entry type without loss or alteration.
- **FR-VAULT-024**: The system MUST support full CRUD operations (Create, Read, Update, Delete) for all three vault item types: Password, Credit Card, and Secure Note.
- **FR-VAULT-025**: All credential detail screens MUST display sensitive fields (passwords, card numbers, CVVs) as masked by default with an explicit toggle to reveal them. Revealed values MUST be rendered using a high-legibility font that clearly distinguishes ambiguous characters.
- **FR-VAULT-026**: The system MUST erase all application-owned mutable sensitive values and release application-owned secret references when their authorized operation or session ends. This includes replacement, successful save, confirmed discard, disposal, duplicate rejection, partial processing, failure before encryption, and cancellation before or during work. An active editor MAY retain its independent mutable draft through a pending save or recoverable failure; consuming a submission MUST NOT erase that retry draft. Returned details MUST be freshly retrieved rather than reuse erased data.
  - The policy covers usernames, passwords, websites, cardholder names, card numbers, expiration dates, CVVs, notes, titles, and custom-field names/values. Authorized list-title display does not exempt title ownership from this policy. App models, drafts, baselines and payload serialization MUST NOT retain sensitive immutable Strings.
  - Platform text boundaries MAY use only the audited exception in Constitution I.5. Each boundary MUST identify its API/dependency version, fields, purpose, lifetime, controls and residual copying risk. App-controlled restoration/history/caching and optional disclosure MUST follow that exception. Reference release or visible clearing MUST NOT be represented as erasure of immutable platform copies.
- **FR-VAULT-027**: The system MUST copy sensitive values to the clipboard on user request and automatically clear that clipboard entry after 60 seconds.
- **FR-VAULT-028**: The system MUST allow users to create labels and assign them to vault entries. The Vault list MUST support filtering entries by label.
- **FR-VAULT-029**: The system MUST display a meaningful, user-friendly error message and maintain the user's current context when any vault read or write operation fails.
- **FR-VAULT-030**: The system MUST display an empty-state message on the Vault list when no entries exist or no entries match the active label filter.
- **FR-VAULT-031**: The system MUST show a discard-confirmation dialog if the user navigates away from an entry form with unsaved changes.
- **FR-VAULT-032**: With Vault and passkeys enabled together, the system MUST support creating, reading, updating, and deleting each Vault item type immediately after application startup and after restart, without terminating the application.
- **FR-VAULT-033**: Vault changes MUST affect only the intended Vault entry and its history; passkey changes MUST affect only the intended passkey and its history. Existing records of the other kind MUST remain unchanged and usable.
- **FR-VAULT-034**: The system MUST initiate departure from an entry form only after a confirmed successful save or an explicit user cancellation. Pending or failed saves MUST retain the user's input while the editing session remains active, show an appropriate pending or error state, and prevent duplicate submissions. Retry MUST save the intended entry once. External lifecycle disposal is governed by FR-VAULT-026 and MUST erase the draft rather than restore plaintext automatically.
- **FR-VAULT-035**: Memory-security remediation MUST preserve compatibility with existing encrypted Password, Credit Card, and Secure Note contents, including optional and custom fields. Existing entries MUST open and remain editable without data loss, destructive reset, or mandatory migration; new writes MUST retain the existing payload-format contract and MUST NOT change passkey storage or behavior.

### Key Entities

- **Vault entry**: A securely stored item with a unique identity, type (Password, Credit Card, or Secure Note), display title, protected contents, synchronization state, creation and modification times, backup status, and owner identity.
- **Password contents**: Username, password, website address, notes, and optional custom fields. Sensitive values are subject to FR-VAULT-026.
- **Credit card contents**: Cardholder name, card number, expiration date, CVV, notes, and optional custom fields. Sensitive values are subject to FR-VAULT-026.
- **Secure note contents**: Title and body text. Sensitive values are subject to FR-VAULT-026.
- **Label**: A user-defined tag with an ID and a name, used to organize vault entries.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-VAULT-001**: A user can add a new password entry, navigate back to the list, and confirm the entry appears in under 15 seconds from tapping the Add button.
- **SC-VAULT-002**: A user can open a saved entry, reveal its password, copy it, and navigate back — with the clipboard automatically cleared within 60 seconds of the copy action.
- **SC-VAULT-003**: The Vault list correctly reflects all stored entries (the correct count and titles) immediately after creating, editing, or deleting any entry — with no stale state visible.
- **SC-VAULT-004**: Editing an existing entry updates its stored data; the previous values are no longer accessible from the detail view after saving.
- **SC-VAULT-005**: After a detail/editor session ends, none of its sensitive values remain visible through that session, its application-owned mutable buffers are zeroed, and its application-owned secret references are released. An independently owned active editor or authorized list display MAY retain its own data for its documented lifetime. Verification MUST inspect retained application-buffer references and actual lifecycle behavior, and verify each platform adapter's configured retention/disclosure controls. External runtime/platform copies are documented residual risks, not falsely reported as erased.
- **SC-VAULT-006**: All three entry types (Password, Credit Card, Secure Note) can be created, viewed, edited, and deleted without application error or data loss.
- **SC-VAULT-007**: The Vault list maintains 60 FPS scrolling performance when 500 or more entries are stored.
- **SC-VAULT-008**: Label filtering correctly shows only entries with the selected label; switching to "All" restores the complete list immediately.
- **SC-VAULT-009**: On both a fresh installation and an existing installation containing passkeys, all three Vault entry types complete create, reopen after restart, edit, and delete checks with zero crashes and zero unintended changes to passkeys; passkey creation and authentication also remain successful.
- **SC-VAULT-010**: For each Vault entry type, a deliberately failed save keeps the form and input available, shows an error, and allows one successful retry with exactly one resulting entry. Repeated Save taps during a pending operation produce no duplicates.
- **SC-VAULT-011**: For all three entry types, cleanup checks pass for success, discard, disposal, field replacement, duplicate rejection, key failure, encoding/decoding failure, encryption failure, storage failure, and cancellation before and during work. Failed saves preserve every retry-draft field, including custom fields; completed/discarded sessions retain none of their sensitive contents in application-owned storage or references. Platform adapter retention/disclosure controls and their residual exposure MUST be documented and verified under Constitution I.5. Unavailable lifecycle or memory checks are recorded as unverified, never passed.
- **SC-VAULT-012**: Fixed examples of the existing encrypted-payload format open correctly, and new writes satisfy the same format contract, for all three entry types with Unicode/escapes, missing/default/null optional fields, custom fields, and tolerated unknown fields. Valid saved values survive an edit/reopen cycle without altering passkeys.

## Assumptions

- Existing master-key setup and encrypted storage are prerequisites; this feature does not change how encryption keys are derived.
- Vault writes retain the existing auditable history model. Correct startup connections between Vault processing and storage must be verified rather than assumed; the correction requires no new data format or destructive reset of user data.
- Existing Vault list, entry, detail, and label-management screens are the user-experience baseline; completing their flows does not include a wholesale redesign.
- Stored entry contents must remain encrypted and retrievable without loss. Future additions to entry fields must preserve readability of existing entries.
- Timed clipboard clearing is an existing application capability to reuse for Vault copy actions.
- The onboarding flow and user preference for enabling the Vault feature are already in place; this spec assumes the user has enabled Vault during onboarding.
- Custom fields on entries are in-scope for display and storage, but a dedicated UI for adding/removing custom fields during entry creation is a stretch goal; the form may show existing custom fields for edit-in-place.

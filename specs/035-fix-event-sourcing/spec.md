# Feature Specification: fix-event-sourcing

**Feature Branch**: `[035-fix-event-sourcing]`  
**Created**: 2026-05-13  
**Status**: Draft  
**Input**: User description: "Part 2, continuation of 034-refactor-event-sourcing. Goal fix issues found after 034-refactor-event-sourcing implementation.  Input files @[specs/034-refactor-event-sourcing/rca-bip39-asset-loading.md], @[specs/034-refactor-event-sourcing/plan-bip39-asset-fix.md], @[specs/034-refactor-event-sourcing/test-plan-bip39-asset-fix.md], @[specs/034-refactor-event-sourcing/handover-encryption-key-management.md]"

## Clarifications

### Session 2026-05-13
- Q: Should the `EventStoreKeyProvider` use a single shared derivation label or distinct labels for each aggregate's event store? → A: Distinct labels per aggregate (e.g., vault, passkey).
- Q: How should the `EventStoreKeyProvider` behave if requested for a key before the BIP39 master seed has been initialized? → A: Throw `IllegalStateException` (Seed not initialized).
- Q: What is the preferred mechanism for performing the one-time truncation of the `EventStore` and `Snapshot` tables? → A: SQLDelight migration script (`.sqm` file).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - FIDO2 Crypto Initialization (Priority: P1)

As a user installing the app or opening it for the first time after an update, I want the core cryptographic services to initialize successfully so that I can register and use FIDO2 credentials.

**Why this priority**: Without this, the app crashes immediately on attempting to access FIDO2 features, blocking all related functionality.

**Independent Test**: Can be tested by verifying the app launches and no `FileNotFoundException` occurs for `bip39_english.txt`.

**Acceptance Scenarios**:

1. **Given** a fresh install of the application, **When** the FIDO2 crypto service initializes, **Then** the BIP39 wordlist asset is successfully loaded from the APK and the master seed is generated.
2. **Given** an existing master seed, **When** the service initializes, **Then** it correctly skips generating a new seed and derives the existing one.

---

### User Story 2 - Secure Event Sourcing Storage (Priority: P1)

As a security-conscious user, I want my Vault and Passkey events to be encrypted with a real cryptographic key derived from my master seed rather than a hardcoded dummy key.

**Why this priority**: Addresses a critical security gap (T032) where events and snapshots were essentially stored in plaintext, compromising user data at rest.

**Independent Test**: Can be tested by examining the SQLite databases (VaultDatabase and Fido2Database) and verifying that the `payload` columns in `EventStore` and `Snapshot` tables cannot be decrypted with a zero-filled key.

**Acceptance Scenarios**:

1. **Given** a user adding a new Vault entry or Passkey, **When** the event is appended to the `EventStore`, **Then** the payload is encrypted using a deterministically derived AES-256-GCM key from the master seed.
2. **Given** the system taking a snapshot, **When** the snapshot is saved, **Then** the payload is encrypted using the same derived AES-256-GCM key.
3. **Given** an app update applying this feature, **When** the app starts, **Then** the existing `EventStore` and `Snapshot` tables are truncated to remove any dummy-encrypted data.

---

### Edge Cases

- What happens when the app updates and the `EventStoreKeyProvider` attempts to derive a key, but the master seed hasn't been generated yet? → Resolved: Provider throws `IllegalStateException`.
- How does the system handle the database migration (truncation) for existing users who had dummy-encrypted events? → Resolved: Handled via SQLDelight `.sqm` migration files.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST explicitly map the `androidMain/assets` directory in the `core:security` build configuration so that `bip39_english.txt` is packaged into the final APK.
- **FR-002**: System MUST define an `EventStoreKeyProvider` interface to supply a 32-byte AES-256 key for event and snapshot payload encryption.
- **FR-003**: `EventStoreKeyProvider` implementation MUST derive keys deterministically from the existing BIP39 master seed using HMAC-SHA512 with aggregate-specific labels (e.g., `"chimali_vault_es_v1"`, `"chimali_passkey_es_v1"`).
- **FR-004**: System MUST inject the `EventStoreKeyProvider` into `EventStoreRepositoryImpl`, `SnapshotRepositoryImpl`, `PasskeyEventStoreRepositoryImpl`, and `PasskeySnapshotRepositoryImpl`, replacing the hardcoded `dummyKey`.
- **FR-005**: System MUST truncate all existing data in `EventStore` and `SnapshotStore` tables in both `VaultDatabase` and `Fido2Database` via SQLDelight migration scripts (`.sqm`) to remove unreadable, dummy-encrypted data.
- **FR-006**: `EventStoreKeyProvider` MUST throw `IllegalStateException` if a key is requested before the master seed is initialized.

### Key Entities

- **EventStoreKeyProvider**: A new component responsible for secure key derivation for the event sourcing persistence layer.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of `FileNotFoundException: bip39_english.txt` crashes are eliminated on app launch.
- **SC-002**: 100% of new event and snapshot payloads in `EventStore` and `SnapshotStore` tables are encrypted with a dynamically derived AES-256-GCM key, resolving task T032.
- **SC-003**: The project builds successfully (`local-ci.ps1` passes) and all encryption tests correctly mock the new `EventStoreKeyProvider` instead of relying on the dummy key.

## Assumptions

- The data truncation of existing events and snapshots is acceptable for the current development phase, as the previous dummy encryption makes legacy data insecure and incompatible with the new key.
- The `bip39_english.txt` asset path and contents in `core:security` are correct and only the AGP plugin configuration was missing.

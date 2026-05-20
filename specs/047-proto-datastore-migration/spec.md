# Feature Specification: Proto DataStore Migration

**Feature Branch**: `047-proto-datastore-migration`

**Created**: 2026-05-19

**Status**: Ready for Planning

**Input**: User description: "Goal since androidx.security.crypto.EncryptedSharedPreferences is deprecated. Remove all uses of EncryptedSharedPreferences. Goal make Proto DataStore the canonical way to store user preferences, user settings. Encrypted by default. Goal migrate away from SharedPreferences. Goal migrate away from EncryptedSharedPreferences. Goal use Proto DataStore (DataStore backed by Protocol Buffers) to store user settings. Goal use a shared `User Preferences` placed in common location. For reference `Set up DataStore for KMP https://developer.android.com/kotlin/multiplatform/datastore`"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Migrate Wallet Seed Storage (Priority: P1)

The system migrates the BIP39 mnemonic storage from EncryptedSharedPreferences to Proto DataStore, ensuring the critical wallet seed data remains encrypted and accessible across app restarts.

**Why this priority**: The wallet seed is the most critical security data in the application. Losing or corrupting this data would result in permanent loss of user credentials. This must be migrated first to ensure data integrity before other migrations.

**Independent Test**: Can be fully tested by verifying that existing BIP39 mnemonics stored in EncryptedSharedPreferences are successfully migrated to Proto DataStore and can be retrieved and used for key derivation without data loss or corruption.

**Acceptance Scenarios**:

1. **Given** an existing installation with a BIP39 mnemonic stored in EncryptedSharedPreferences, **When** the app launches after migration, **Then** the mnemonic is successfully read from Proto DataStore and used to derive the master seed
2. **Given** a fresh installation with no existing mnemonic, **When** the app launches for the first time, **Then** a new BIP39 mnemonic is generated and stored in Proto DataStore
3. **Given** the app is running with the new Proto DataStore implementation, **When** the user imports a mnemonic via Dev Tools, **Then** the mnemonic is stored in Proto DataStore and can be retrieved

---

### User Story 2 - Migrate FIDO2 Settings (Priority: P2)

The system migrates FIDO2 settings (max credential count) from EncryptedSharedPreferences to Proto DataStore, ensuring authenticator limits are persisted securely.

**Why this priority**: FIDO2 settings control credential storage limits and are important for device capacity management. While less critical than the wallet seed, these settings must be preserved to maintain consistent behavior across upgrades.

**Independent Test**: Can be fully tested by verifying that existing max credential count settings are migrated to Proto DataStore and the FIDO2 authenticator respects the migrated limits during registration operations.

**Acceptance Scenarios**:

1. **Given** an existing installation with a custom max credential count stored in EncryptedSharedPreferences, **When** the app launches after migration, **Then** the custom limit is read from Proto DataStore and enforced
2. **Given** the app is running with the new Proto DataStore implementation, **When** the user changes the max credential count setting, **Then** the new value is persisted in Proto DataStore
3. **Given** no custom limit has been set, **When** the app launches, **Then** the default limit (1000) is used

---

### User Story 3 - Establish Shared User Preferences Schema (Priority: P3)

The system establishes a shared User Preferences schema in a common KMP location using Protocol Buffers, providing a unified structure for storing user settings across platforms.

**Why this priority**: This establishes the foundation for future preference storage and ensures consistency across the KMP project. While not blocking the immediate migration, it's important for long-term maintainability and extensibility.

**Independent Test**: Can be fully tested by verifying that the Protocol Buffer schema compiles correctly, generates the expected data classes, and can be used to store and retrieve preference data in the common KMP module.

**Acceptance Scenarios**:

1. **Given** the new User Preferences schema is defined, **When** the project builds, **Then** the Protocol Buffer compiler generates the expected Kotlin data classes
2. **Given** the User Preferences DataStore is initialized, **When** preference values are written, **Then** they are persisted and can be retrieved
3. **Given** the common User Preferences module, **When** platform-specific code accesses preferences, **Then** the data is shared correctly across the KMP project

---

### Edge Cases

- What happens when the EncryptedSharedPreferences file is corrupted or unreadable during migration?
- How does the system handle a partial migration where some data has been migrated but not all?
- What happens if the app crashes during the migration process?
- How does the system handle concurrent access to both old and new storage during the migration window?
- What happens if the Proto DataStore file already exists (e.g., from a previous migration attempt)?
- How does the system handle encryption key rotation or changes?
- What happens when the device is low on storage during migration?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST remove all references to EncryptedSharedPreferences from the codebase (6 files identified: WalletMasterSeedProvider.kt, Fido2SettingsRepositoryImpl.kt, Fido2CryptoService.kt, AesSivEncryptionManager.kt, BluetoothHidTransportImpl.kt, WalletMasterSeedProviderTest.kt)
- **FR-002**: System MUST remove all references to SharedPreferences from the codebase
- **FR-003**: System MUST implement Proto DataStore with Protocol Buffers schema for storing user preferences and settings
- **FR-004**: System MUST ensure all sensitive data stored in Proto DataStore is encrypted by default
- **FR-005**: System MUST migrate existing BIP39 mnemonic data from EncryptedSharedPreferences to Proto DataStore without data loss
- **FR-006**: System MUST migrate existing FIDO2 settings (max credential count) from EncryptedSharedPreferences to Proto DataStore
- **FR-007**: System MUST create a shared User Preferences schema in a common KMP location
- **FR-008**: System MUST provide a data migration path that preserves existing user data during the transition
- **FR-009**: System MUST maintain encryption requirements per Constitution §I for all sensitive data
- **FR-010**: System MUST ensure Proto DataStore is the canonical and only storage mechanism for user preferences after migration

### Key Entities

- **User Preferences**: A shared schema defining all user-configurable settings and preferences across the KMP project, including wallet seed data and FIDO2 settings
- **Wallet Seed**: The BIP39 mnemonic (24 words) used to derive the master seed for cryptographic operations, must remain encrypted
- **FIDO2 Settings**: Authenticator configuration settings including max credential count limit
- **Migration Data**: Temporary entity representing data being transferred from legacy storage to Proto DataStore

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero references to EncryptedSharedPreferences remain in the codebase after migration
- **SC-002**: Zero references to SharedPreferences remain in the codebase after migration
- **SC-003**: All existing user data (BIP39 mnemonics and FIDO2 settings) is successfully migrated to Proto DataStore without data loss
- **SC-004**: Proto DataStore is the only storage mechanism used for user preferences after migration
- **SC-005**: All sensitive data stored in Proto DataStore remains encrypted per Constitution §I
- **SC-006**: The shared User Preferences schema is accessible from the common KMP location
- **SC-007**: Application performance for data access operations is maintained or improved after migration
- **SC-008**: Data migration completes successfully on first app launch after upgrade

## Assumptions

- Existing user data stored in EncryptedSharedPreferences must be preserved during migration
- Encryption requirements per Constitution §I remain unchanged and must be maintained
- The KMP DataStore setup will follow the Android developer guide for Kotlin Multiplatform
- Migration will be performed incrementally to minimize risk and allow for rollback if needed
- The Android KeyStore will continue to be used for encryption key management
- Proto DataStore will be configured to use the same encryption mechanisms as EncryptedSharedPreferences
- The migration will be transparent to end users with no manual intervention required
- Device storage capacity is sufficient to accommodate both old and new storage during the migration window

## Clarifications

### Migration Recovery Strategy
- **Crash Recovery**: Resume migration from where it left off using a transaction flag to ensure data integrity and prevent duplicate migrations
- **Corrupted Legacy Data**: Log error, skip migration, and use default values when EncryptedSharedPreferences file is corrupted or unreadable
- **Legacy File Cleanup**: Delete legacy EncryptedSharedPreferences files immediately after migration completes successfully

### Migration Observability
- **Logging Level**: Info level for migration progress tracking, Error level for failure capture and post-mortem analysis

### Concurrent Access Handling
- **Migration Window Access Pattern**: Read from legacy storage, write to new storage during migration. No reads from new storage until migration completes to ensure consistency and prevent reads from partially migrated data

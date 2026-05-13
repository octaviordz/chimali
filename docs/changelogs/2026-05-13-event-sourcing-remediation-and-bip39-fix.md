# Event Sourcing Remediation & BIP39 Asset Fix

This changelog summarizes the implementation of the technical remediation plan for event sourcing security and the fix for BIP39 wordlist asset loading.

## [Unreleased] - 2026-05-13

### Fixed
- **BIP39 Asset Loading Crash**: Resolved a critical `FileNotFoundException` where `bip39_english.txt` was missing from the APK. Explicitly mapped the `assets` directory in `core:security` build configuration to ensure the wordlist is bundled.
- **Event Sourcing Security Gap (T032)**: Replaced hardcoded "dummy" encryption keys with cryptographically secure, deterministically derived keys for the event sourcing persistence layer.
    - Implemented `EventStoreKeyProvider` to derive 32-byte AES-256-GCM keys using HMAC-SHA512 from the BIP39 master seed.
    - Applied aggregate-specific derivation labels (e.g., `"chimali_vault_es_v1"`, `"chimali_passkey_es_v1"`) to ensure cryptographic isolation between different event stores.
- **Legacy Data Cleanup**: Implemented SQLDelight migrations (`3.sqm` for `VaultDatabase` and `10.sqm` for `Fido2Database`) to truncate existing `EventStore` and `SnapshotStore` tables, removing insecure dummy-encrypted data and preventing decryption failures with the new key management system.

### Added
- **EventStoreKeyProvider**: A new foundational component in `core:security` for secure key management in the event sourcing architecture.
- **Comprehensive Testing**: Added unit tests for `EventStoreKeyProviderImpl` verifying HMAC-SHA512 derivation, error handling for uninitialized seeds, and label-based key separation.

### Changed
- **Repository Refactoring**: Updated `EventStoreRepositoryImpl` and `SnapshotRepositoryImpl` (both Vault and Passkey variants) to inject and use the new `EventStoreKeyProvider`.
- **DI Wiring**: Updated Koin modules (`SecurityModule`, `DataModule`, `Fido2Module`) to support the new key provider and its injection into repositories.

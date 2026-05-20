# Proto DataStore Migration (2026-05-20)

## Overview
Successfully migrated application configuration and core cryptographic secrets from legacy `EncryptedSharedPreferences` to a robust, type-safe `Proto DataStore` backed by SQLCipher encryption primitives. This transition eliminates intermittent XML corruption issues, centralizes multi-platform storage boundaries, and ensures atomic, transactional state updates.

## Changed
- **Persistence Architecture Migration**: Migrated `WalletMasterSeedProvider` and `Fido2SettingsRepositoryImpl` away from the Android-only `EncryptedSharedPreferences` to a unified `Proto DataStore` (`UserPreferences`).
- **Data Migration Logic**: Implemented graceful one-time migration paths. On first launch, legacy keys are decrypted from `EncryptedSharedPreferences`, committed to the `Proto DataStore`, and subsequently zeroed/deleted from the legacy storage mechanism.
- **Cross-Platform Schema Definition**: Defined `UserPreferences` using `kotlinx.serialization` and Protocol Buffers, creating a common schema abstraction `UserPreferencesSerializer` compatible with KMP boundaries.
- **Exception Handling Hardening**: Systematically removed `@Suppress("TooGenericExceptionCaught")` and `@Suppress("SwallowedException")` annotations project-wide. Refactored generic `Exception` catch blocks to explicitly handle `GeneralSecurityException`, `IOException`, and `SecurityException`, guaranteeing strict adherence to Detekt best practices without compromising crash-safety.
- **Cryptographic Modernization**: Mitigated compilation warnings in `AesSivEncryptionManager` by updating deprecated BouncyCastle `SICBlockCipher` and `AESEngine` constructors to their modern `newInstance()` factory equivalents.
- **Code Quality & Maintenance**: Cleaned up test suites (`WalletSeedMigrationTest`, `Fido2SettingsMigrationTest`, `UserPreferencesDataStoreTest`) by resolving maximum line length violations, camelCase naming discrepancies, and unused property linting errors. Extracted raw magic numbers in `EncryptionWrapper` into defined constants (`KEY_SIZE`, `IV_SIZE`, `GCM_TAG_LENGTH`).

## Security Context
The `androidx.security:security-crypto` library remains in the dependency graph strictly as a read-only dependency to facilitate the one-time migration of legacy `EncryptedSharedPreferences` payloads. No new data is written to this storage medium. All new persisted data relies on `EncryptionWrapper` integrated with AndroidKeyStore AES-GCM and Proto DataStore.

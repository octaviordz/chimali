# Changelog: Corrupted Key Repair Implementation

**Date**: 2026-04-22
**Feature**: FIDO2 Key Optimization
**Issue/Task**: Repair corrupted public keys in the background

## Summary

Implemented the background worker logic for repairing corrupted FIDO2 public keys. This ensures that if the system fails to decode a public key from the database during the paginated loading of the passkeys screen, it can be automatically re-derived from the Master Seed via the HDK path and saved back to the database without interrupting the user experience.

## Changes

### feature/fido2

#### [MODIFY] [PasskeyCredential.sq](feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/PasskeyCredential.sq)
- Added `updatePublicKey` SQL statement to allow targeted updates of the `publicKey` field by credential ID.

#### [MODIFY] [PasskeyCredentialDao.kt](feature/fido2/src/main/kotlin/com/chimali/fido2/data/dao/PasskeyCredentialDao.kt)
- Added `updatePublicKey` suspend function to expose the new SQL query to the repository and worker layers.

#### [MODIFY] [CorruptedKeyRepairWorker.kt](feature/fido2/src/main/kotlin/com/chimali/fido2/data/worker/CorruptedKeyRepairWorker.kt)
- Replaced the placeholder implementation with actual logic.
- Implemented `CorruptedKeyRepairWorkerImpl` to:
    - Iterate through a list of corrupted credential IDs.
    - Fetch the credential entity from the database.
    - Re-derive the public key using `Fido2CryptoService.getPublicKey()`.
    - Encode the derived `PublicKey` back to Base64 (X.509 format).
    - Persist the repaired key using `PasskeyCredentialDao.updatePublicKey()`.
- Fixed a circular dependency in Koin by injecting `PasskeyCredentialDao` and `Fido2CryptoService` instead of the full `CredentialRepository`.

## Verification Results

### Automated Tests
- Executed `./gradlew :feature:fido2:testDebugUnitTest` - **PASSED** (all tests including repository and worker tests).
- Verified SQLDelight generation - **SUCCESSFUL** (new query is correctly generated and accessible).

### Manual Verification
- Verified that `CredentialRepositoryImpl` correctly enqueues the worker when a corruption is detected during `getPagedCredentials`.
- Verified that the circular dependency no longer causes a `StackOverflowError` during app startup.

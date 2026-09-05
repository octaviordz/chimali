# 2026-09-05 — Migrate Encrypted Database Layer to SQLite3MultipleCiphers & DO-178B Compliance

**Branch**: `052-migrate-sqlite3mc`

## Summary

Migrated the encrypted database layer across `:core:database` and `:feature:fido2` from SQLCipher (`net.zetetic:sqlcipher-android`) to **SQLite3MultipleCiphers** (`io.toxicity.sqlite-mc`), adopting **ChaCha20-Poly1305** as the preferred default cipher. In addition, refactored the database initialization, recovery, and test infrastructure to strictly comply with the newly ratified **DO-178B Principles for Critical Code (Constitution v0.17.0 §XII)**.

---

## Motivation

1. **License & Dependency Simplification**: SQLCipher required explicit BSD-style license attribution in published distributions. `sqlite-mc` is Apache-2.0 licensed, eliminating custom attribution friction.
2. **Modern Encryption Menu**: SQLCipher was restricted to AES-256-CBC page encryption. SQLite3MultipleCiphers brings modern authenticated ciphers including **ChaCha20-Poly1305** (preferred default).
3. **KMP & JVM Host Testing**: SQLCipher's native binaries required `System.loadLibrary` workarounds and failed under host JVM unit tests without specialized mocking or device/emulator harnesses. `sqlite-mc` provides an `android-unit-test` artifact bundling native desktop binaries for seamless, zero-workaround host test execution.
4. **Resolution of Existing Encryption Bug**: `Fido2Module.fido2Database()` previously instantiated a plain unencrypted `AndroidSqliteDriver` despite KDoc claiming SQLCipher backing. This migration connects `fido2.db` to `EncryptedDriverFactory`, ensuring all credential storage is encrypted at rest.
5. **DO-178B Compliance (Constitution v0.17.0 §XII)**: The project's constitution was amended to enforce civil avionics safety principles (traceability, zero allocations in hot paths, deterministic bounded loops, test independence, and fail-safe key zeroing). The database layer was brought into full alignment with these principles.

---

## Changes

### Modified Files

#### `gradle/libs.versions.toml`
- Removed legacy `sqlcipher` (`net.zetetic:sqlcipher-android:4.5.5`) dependency alias.
- Added `sqlite-mc` dependencies pinned to version `0.8.2`:
  - `sqlite-mc-driver-android`: `io.toxicity.sqlite-mc:driver-android:0.8.2`
  - `sqlite-mc-driver-test`: `io.toxicity.sqlite-mc:android-unit-test:0.8.2`
- Preserved `sqldelight = "2.3.2"` plugin and compiler runtime.

#### `core/database/build.gradle.kts`
- Replaced `libs.sqlcipher` with `libs.sqlite.mc.driver.android`.
- Added `testImplementation(libs.sqlite.mc.driver.test)` for host JVM integration testing.

#### `core/database/src/main/java/com/chimali/core/database/EncryptedDriverFactory.kt`
- Switched driver generation from `AndroidSqliteDriver` to `SQLiteMCDriver.Factory` configured with `Key.raw(...)` and ChaCha20-Poly1305.
- Removed legacy `System.loadLibrary("sqlcipher")` and its `UnsatisfiedLinkError` guard.
- Retained PBKDF2-HMAC-SHA512 key derivation (2048 iterations, 256-bit key) with domain salt.
- **DO-178B §XII.1 (Traceability)**: Added comprehensive requirement tags (`FR-MC-010` through `FR-MC-040`, `FR-MC-080`, Constitution §XII).
- **DO-178B §XII.2 (Determinism & Zero Allocations)**:
  - Promoted `HEX_CHARS` to a static companion `CharArray` to prevent dynamic allocation per invocation.
  - Converted `salt` in `deriveDatabaseKey` to a pre-allocated static byte array `DB_PBE_SALT_BYTES`.
  - Statically bounded database recovery to `MAX_RECOVERY_ATTEMPTS = 1` within an explicit loop.
- **DO-178B §XII.5 (Fail-Safe & Memory Zeroing)**:
  - Zeroes password buffers and PBEKeySpec in `finally` blocks (`hexPassword.fill('\u0000')`, `spec.clearPassword()`).
  - Zeroes derived key byte array in `finally` (`derivedKey.fill(0)`).
  - Handles incompatible legacy databases (plain SQLite / SQLCipher v4) by safely deleting the incompatible file and recreating a fresh ChaCha20-Poly1305 database.
  - Safely encapsulates `verifyIntegrity` exceptions (`SQLiteException`, `IllegalStateException`, `IllegalArgumentException`), closing driver handles in `finally`.

#### `core/database/src/test/java/com/chimali/core/database/SQLiteMCDriverIntegrationTest.kt` [NEW]
- Added comprehensive integration tests using `sqlite-mc`:
  - In-memory `PRAGMA integrity_check` verification on host JVM.
  - File-based ChaCha20-Poly1305 database creation, closing, and reopening with key validation.
  - Mismatched key rejection test ensuring fail-secure behavior without silent data corruption (US1/AC3, §XII.3, §XII.5).
  - Incompatible database recovery test confirming automatic deletion of corrupted/legacy files and recreation of fresh databases.
- Enforced independent test execution via `@TempDir` (§XII.3).

#### `feature/fido2/build.gradle.kts`
- Removed redundant direct `libs.sqlcipher` dependency; `core:database` provides the encrypted driver abstraction (§XII.4).

#### `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/di/Fido2Module.kt`
- Updated `fido2Database` provider to instantiate `Fido2Database` via `encryptedDriverFactory.createDriver(Fido2Database.Schema, "fido2.db")`.
- Annotated with DO-178B traceability tags (`FR-MC-050`, §XII.1, §XII.4).

#### `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/security/SecurityStorageIntegrityTest.kt`
- Updated KDoc and test contracts to reflect SQLite3MultipleCiphers and ChaCha20-Poly1305.
- Linked traceability to `FR-MC-080`, `FR-HID-015`, and Constitution §XII.1/§XII.3.

#### `feature/fido2/proguard-rules.pro`
- Removed SQLCipher-specific ProGuard rules and added keep rules for `io.toxicity.sqlite.mc.**`.

---

## Verification & Quality Gates

- **Static Analysis (Detekt)**: Passed with 0 violations across `:core:database` and `:feature:fido2`.
- **Code Style (Ktlint)**: Passed with 0 violations across all source sets.
- **Automated Tests**:
  - `:core:database:test` passed (all `SQLiteMCDriverIntegrationTest` and `EncryptedDriverFactoryTest` cases).
  - `:feature:fido2:testAndroidHostTest` passed (including `SecurityStorageIntegrityTest`).

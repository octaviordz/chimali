# Feature Specification: Migrate Encrypted Database Layer from SQLCipher to SQLite3MultipleCiphers

**Feature Branch**: `052-migrate-sqlite3mc`

**Created**: 2026-09-05

**Status**: Draft

**Input**: User description: "Replace SQLCipher with SQLite3MultipleCiphers for encrypted database storage"

---

## Background

The application currently uses **SQLCipher** (`net.zetetic:sqlcipher-android`) as its encrypted SQLite provider. While functional, SQLCipher has several characteristics that motivate this change:

- It requires mandatory license attribution (BSD-style) in published applications.
- The community AAR (`sqlcipher-android`) does not natively expose a `SupportOpenHelperFactory` integration path with SQLDelight in a KMP-first manner.
- The `feature/fido2` module carries a redundant direct `sqlcipher` dependency alongside `core:database`, creating duplicated native library surface.
- SQLCipher encryption is locked to AES-256-CBC page-level encryption; SQLite3MultipleCiphers supports a richer, standards-aligned cipher menu including **ChaCha20-Poly1305** (the recommended default).

The chosen integration vehicle is the **`toxicity-io/sqlite-mc`** library (`io.toxicity.sqlite-mc`), which ships pre-built Android AAR artifacts, a Gradle plugin, an SQLDelight-compatible driver API (`SQLiteMCDriver`), and an `android-unit-test` artifact enabling JVM host tests without device-level native libraries. It is Apache-licensed, actively maintained (latest release August 2025), and supports KMP targets (Android, JVM, iOS, Linux, macOS).

---

## Clarifications

### Session 2026-09-05

- Q: Does the migration need to open existing databases encrypted with SQLCipher v4, or can all databases be treated as new (fresh encryption)? → A: No backward compatibility required — use SQLite3MultipleCiphers' preferred default cipher (ChaCha20-Poly1305).

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — New Encrypted Databases Are Created with the Preferred Cipher (Priority: P1)

A developer upgrades the library dependency. On first launch after migration, `vault.db` and `fido2.db` are created fresh using SQLite3MultipleCiphers with its preferred **ChaCha20-Poly1305** cipher. No existing database files need to be read.

**Why this priority**: The cipher selection drives the security profile, key derivation expectations, and all subsequent acceptance criteria. Establishing a clean break with no backward-compat complexity is the foundational choice for this migration.

**Independent Test**: A host unit test creates a fresh database using the new driver with ChaCha20-Poly1305, writes one row, closes, reopens with the same key, and reads the row back successfully.

**Acceptance Scenarios**:

1. **Given** no prior database file exists, **When** the application initializes the encrypted driver for the first time, **Then** a new database file is created, encrypted with ChaCha20-Poly1305, and is operational.
2. **Given** a database encrypted with ChaCha20-Poly1305 and a derived key, **When** the driver reopens it with the same key, **Then** all previously written records are readable without errors.
3. **Given** a database file encrypted with ChaCha20-Poly1305, **When** an attempt is made to open it using a mismatched key, **Then** the operation fails with a clear error indicating decryption failure (not silent data corruption).

---

### User Story 2 — JVM Host Tests Pass Without Device or Native Library Setup (Priority: P2)

A developer runs `./gradlew :core:database:test` and `./gradlew :feature:fido2:testDebugUnitTest` on a development machine (Windows or Linux CI) without any Android device or emulator. All database-layer unit tests complete successfully.

**Why this priority**: The existing `EncryptedDriverFactory` requires special-casing for JVM host tests because SQLCipher's native library cannot be loaded in JVM-only environments. The `sqlite-mc` `android-unit-test` artifact bundles the native binaries needed for JVM host tests, eliminating the `System.loadLibrary` try/catch workaround.

**Independent Test**: Run `./gradlew :core:database:test` on the development workstation — all `EncryptedDriverFactory` unit tests pass with no native-library loading guard.

**Acceptance Scenarios**:

1. **Given** a JVM host test environment without Android runtime, **When** `./gradlew :core:database:test` is executed, **Then** all tests in `EncryptedDriverFactoryTest` pass.
2. **Given** the same JVM host test environment, **When** `./gradlew :feature:fido2:testDebugUnitTest` is executed, **Then** `SecurityStorageIntegrityTest` and all other database-touching unit tests pass.
3. **Given** a host test that creates an in-memory `SQLiteMCDriver` with ChaCha20-Poly1305, **When** it executes a `PRAGMA integrity_check`, **Then** it returns `"ok"` without loading a system-level native library.

---

### User Story 3 — Database File Encryption Integrity Contract Is Maintained (Priority: P3)

After migration, the encrypted database files must still fail to open as plain SQLite databases, confirming that encryption is active and not silently disabled.

**Why this priority**: The `SecurityStorageIntegrityTest.T148d` contract explicitly guards against accidental removal of encryption. The migration must not weaken this guarantee — ChaCha20-Poly1305 produces an encrypted page format that is equally opaque to plain SQLite tools.

**Independent Test**: A binary header check against the `.db` file confirms it does not begin with the standard plain SQLite magic bytes (`53 51 4c 69 74 65 20 66 6f 72 6d 61 74 20 33 00`).

**Acceptance Scenarios**:

1. **Given** a database file created by the new driver with ChaCha20-Poly1305 encryption, **When** the first 16 bytes of the file are read, **Then** they do NOT match the plain SQLite3 magic header.
2. **Given** a database created by the new driver, **When** an attempt is made to open it using the standard unencrypted SQLite driver, **Then** the attempt fails (error code 26 or equivalent indicating "file is not a database").
3. **Given** a `PRAGMA integrity_check` executed through the new encrypted driver with the correct key, **Then** it returns `"ok"`.

---

### Edge Cases

- What happens when the master seed is unavailable at the time the driver is requested? The system must surface a clear error and prevent partial database initialization.
- What happens when the derived key does not match the stored database (corrupted seed)? The driver must fail fast with an unambiguous error rather than returning corrupted data.
- What happens on first launch when no existing database file exists? The driver must create a new encrypted database correctly using the default cipher.
- What happens if the SQLite3MultipleCiphers native library cannot be loaded on a specific device ABI? The application must log the failure and surface it as a fatal initialization error rather than falling back to unencrypted storage.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-MC-010**: The system MUST replace the `net.zetetic:sqlcipher-android` dependency with the `io.toxicity.sqlite-mc:driver-android` dependency in all modules that currently declare a direct SQLCipher dependency (`core:database`, `feature:fido2`).
- **FR-MC-020**: The new driver MUST use **ChaCha20-Poly1305** (SQLite3MultipleCiphers' preferred default cipher) for all newly created database files. No backward compatibility with the SQLCipher AES-256-CBC format is required.
- **FR-MC-030**: The new driver MUST be supplied with the same PBKDF2-HMAC-SHA512-derived 256-bit key currently produced by `EncryptedDriverFactory.deriveDatabaseKey()`, preserving the existing key derivation contract without modification.
- **FR-MC-040**: The `EncryptedDriverFactory` in `:core:database` MUST be updated to use `SQLiteMCDriver` with ChaCha20-Poly1305 cipher configuration instead of `AndroidSqliteDriver` with `SupportOpenHelperFactory`.
- **FR-MC-050**: The `Fido2Module` in `:feature:fido2` MUST be updated to use the encrypted driver for `fido2.db`, removing its redundant direct `sqlcipher` dependency and resolving the existing discrepancy between its KDoc ("backed by SQLCipher") and actual code (plain unencrypted driver).
- **FR-MC-060**: The `System.loadLibrary("sqlcipher")` call and its `UnsatisfiedLinkError` try/catch guard in `EncryptedDriverFactory` MUST be removed, as the new driver handles native initialization internally.
- **FR-MC-070**: JVM host tests in `:core:database` and `:feature:fido2` MUST continue to pass without device or emulator access. The `io.toxicity.sqlite-mc:android-unit-test` artifact MUST be added as a test dependency.
- **FR-MC-080**: The `SecurityStorageIntegrityTest.T148d` contract MUST be updated to reference the new driver and cipher whilst preserving the encryption-verification assertion (file header does not match plain SQLite magic bytes).
- **FR-MC-090**: The version catalog (`gradle/libs.versions.toml`) MUST replace the `sqlcipher` entry with `sqlite-mc` library entries pinned to a specific version.
- **FR-MC-100**: All KDoc and inline comments that reference "SQLCipher" in the context of the current driver (not historical changelogs or docs) MUST be updated to reference "SQLite3MultipleCiphers".

### Key Entities

- **EncryptedDriverFactory** (`core:database`): The central factory responsible for creating encrypted `SqlDriver` instances. Its `createDriver()` method is the primary implementation surface for this migration.
- **SQLiteMCDriver** (`io.toxicity.sqlite-mc`): The replacement driver. Configured with ChaCha20-Poly1305 and the derived key, produces an SQLDelight-compatible `SqlDriver`.
- **DatabaseModule** (`core:database`): The Koin DI module wiring `EncryptedDriverFactory` to `VaultDatabase`. Internal factory call changes; public interface unchanged.
- **Fido2Module** (`feature:fido2`): The Koin DI module that provides `Fido2Database`. Must be updated to use the encrypted driver, fixing the existing encryption gap for `fido2.db`.
- **libs.versions.toml**: The Gradle version catalog must replace the `sqlcipher` alias with `sqlite-mc` library references.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All existing automated tests (`./gradlew :core:database:test` and `./gradlew :feature:fido2:testDebugUnitTest`) pass without modification to test logic or assertions (test names, KDoc references, and driver instantiation code are updated to reflect the new driver).
- **SC-002**: A database file created by the new driver cannot be read by a standard, unencrypted SQLite tool (verified by checking the binary file header).
- **SC-003**: The `net.zetetic:sqlcipher-android` dependency is no longer present in any module dependency graph (verified via `./gradlew :core:database:dependencies` and `./gradlew :feature:fido2:dependencies`).
- **SC-004**: JVM host tests complete on a development workstation (Windows) without requiring `System.loadLibrary("sqlcipher")` or any native library pre-loading workaround.
- **SC-005**: Static analysis (`./gradlew :core:database:detekt :feature:fido2:detekt`) passes with zero new violations.
- **SC-006**: Local CI pipeline (`tools/local-ci.ps1`) completes green end-to-end with the new dependency in place.

---

## Assumptions

- The `io.toxicity.sqlite-mc` library version available at implementation time supports ChaCha20-Poly1305 as its preferred default cipher (confirmed present in the August 2025 release).
- The `toxicity-io/sqlite-mc` Apache License is compatible with the project distribution requirements and does not impose per-application attribution requirements.
- The PBKDF2-HMAC-SHA512 key derivation in `EncryptedDriverFactory.deriveDatabaseKey()` produces a raw binary key that can be supplied directly to `SQLiteMCDriver` as a raw-byte passphrase without format conversion.
- **No backward compatibility with existing SQLCipher-encrypted database files is required.** Any existing `vault.db` or `fido2.db` files will be treated as invalid and replaced with fresh, ChaCha20-Poly1305-encrypted databases on first launch.
- The `feature:fido2` module `Fido2Module.fido2Database()` currently uses a plain (unencrypted) `AndroidSqliteDriver` despite its KDoc saying "backed by SQLCipher" — this is treated as an existing bug to fix as part of this migration.
- iOS targets in `feature:fido2` are currently placeholder stubs and are explicitly out of scope for this feature.
- The `sqlite-mc` Gradle plugin is available but optional; direct AAR dependency is preferred per Constitution §XI.2 (simplest sufficient solution).

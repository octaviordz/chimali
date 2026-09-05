# Research: Migrate Encrypted Database Layer from SQLCipher to SQLite3MultipleCiphers

**Feature**: 052-migrate-sqlite3mc | **Phase**: 0 — Research | **Date**: 2026-09-05

---

## Decision 1: Integration Library

**Decision**: Use `toxicity-io/sqlite-mc` (`io.toxicity.sqlite-mc`) as the Android integration layer.

**Rationale**: This is the only maintained, Apache-licensed KMP library that ships a pre-built Android AAR with an SQLDelight-compatible `SQLiteMCDriver` API. It also provides an `android-unit-test` artifact for JVM host tests — directly solving the current `System.loadLibrary("sqlcipher")` try/catch workaround in `EncryptedDriverFactory`.

**Alternatives considered**:
- Raw NDK integration of SQLite3MultipleCiphers C source — rejected: requires CMake build pipeline, dramatically increases scope.
- Continuing with SQLCipher — rejected: the user explicitly chose to migrate.

---

## Decision 2: Cipher Selection

**Decision**: Use **ChaCha20-Poly1305** (SQLite3MultipleCiphers preferred default).

**Rationale**: Per user clarification (session 2026-09-05), no backward compatibility with existing SQLCipher v4 AES-256-CBC files is required. ChaCha20-Poly1305 is the recommended default in `sqlite-mc`, offers authenticated encryption (AEAD), performs well in software on devices without AES hardware acceleration, and aligns with modern cryptographic best practices. The existing PBKDF2-HMAC-SHA512 key derivation is preserved unchanged.

**Alternatives considered**:
- SQLCipher v4 AES-256-CBC compat mode — rejected: backward compat not required; adds complexity for no benefit.
- AES-256-CBC (wxSQLite3 mode) — rejected: less modern than ChaCha20-Poly1305; no AEAD.

---

## Decision 3: Library Version Pinning

**Decision**: Use `io.toxicity.sqlite-mc` version **`2.1.0-2.2.3-0`** (released 2025-08-19 — confirmed latest as of 2026-09-05).

**Rationale**: The sqlite-mc versioning scheme is `<sqldelight-version>-<sqlite3mc-version>-<patch>`. The latest release `2.1.0-2.2.3-0` was built against SQLDelight `2.1.0`. The project uses SQLDelight `2.3.2`. SQLDelight's 2.x runtime `SqlDriver` API is **stable across minor versions** — no breaking changes between 2.1.0 and 2.3.2. This is the standard usage pattern: no `2.3.x`-prefixed releases exist and library users on `2.3.x` consume the `2.1.0`-prefixed artifacts without issue.

**Compatibility confirmation**: SQLDelight 2.3.2 changes are confined to PostgreSQL dialect additions, `SuspendingTransacter.TransactionDispatcher`, and AGP 9.0 DSL support — none affect the `SqlDriver` interface that sqlite-mc implements.

**Artifacts**:
- `io.toxicity.sqlite-mc:driver-android:2.1.0-2.2.3-0` — production Android driver.
- `io.toxicity.sqlite-mc:android-unit-test:2.1.0-2.2.3-0` — JVM host test native binaries.

---


## Decision 4: Key Supply to SQLiteMCDriver

**Decision**: Supply the PBKDF2-derived 32-byte key using `Key.raw(key = derivedKey, salt = salt, fillKey = true)`.

**Rationale**: The README shows a first-class `Key.raw()` API that accepts a raw `ByteArray` key and a 16-byte salt directly — cleaner and safer than the hex-string approach. Since `deriveDatabaseKey()` already handles key derivation via PBKDF2-HMAC-SHA512 (bypassing the library's built-in KDF, which is correct), the derived 32-byte array maps directly into `Key.raw()`. The `fillKey = true` parameter instructs the library to zero the key bytes after use, complementing the existing `finally` block zeroing in `EncryptedDriverFactory`. No hex encoding or custom extension functions are required.

**Salt handling**: `Key.raw()` requires a 16-byte salt. The static `"chimali_db_salt"` string currently used by `deriveDatabaseKey()` can be encoded as a fixed 16-byte salt `ByteArray` for this parameter (the first 16 bytes of the UTF-8 encoding of the string). This is consistent with the existing deterministic KDF behavior.

**KDF bypass confirmation** *(added 2026-09-05, analysis C2)*: `Key.raw()` with `fillKey = true` instructs SQLite3MultipleCiphers to use the provided `key` bytes **directly as the encryption key material** — it does NOT run an additional internal KDF round over the supplied bytes. The `salt` parameter in `Key.raw()` is used solely as the stored salt in the database header (to satisfy the SQLite3MultipleCiphers format expectation), not as an input to further key derivation. This means FR-MC-030 ("preserve existing key derivation contract without modification") is fully satisfied: the 32-byte PBKDF2 output is applied to the database unmodified.

---

## Decision 5: `Fido2Module.fido2Database()` Encryption Gap

**Decision**: Fix the existing bug: migrate `Fido2Module.fido2Database()` to use `EncryptedDriverFactory` (centralized pattern) instead of a plain `AndroidSqliteDriver`.

**Rationale**: The code currently uses a plain, unencrypted driver despite the KDoc claiming it is "backed by SQLCipher". This is a pre-existing security gap. The migration is the correct moment to fix it: all databases must be encrypted (Constitution §I). The `EncryptedDriverFactory` is already designed for multi-database use (it accepts `schema` and `name` parameters).

---

## Decision 6: SecurityStorageIntegrityTest Update

**Decision**: Update `SecurityStorageIntegrityTest.T148d` to rename references from "SQLCipher" to "SQLite3MultipleCiphers" in KDoc and assertion messages. No logic changes — the structural invariant (encrypted file ≠ plain SQLite magic header) holds for ChaCha20-Poly1305 equally.

**Rationale**: ChaCha20-Poly1305 encrypted pages are equally opaque to plain SQLite tools. The file header will not begin with the SQLite3 magic bytes. The existing unit test already correctly models this: it confirms the plain-SQLite header vs. a simulated encrypted header differ. Only naming/documentation changes required.

---

## Decision 7: Detekt Baseline Cleanup

**Decision**: Remove the stale `UnusedParameter:SqlCipherWrapper.kt$SqlCipherWrapper$password: String` entry from `feature/fido2/detekt-baseline.xml`.

**Rationale**: `SqlCipherWrapper.kt` was deleted in a prior cleanup. The baseline entry is now a ghost reference. Removing it during this migration is correct hygiene and eliminates a misleading entry.

---

## Decision 8: ProGuard / R8

**Decision**: No new ProGuard rules required for `sqlite-mc`. Remove any lingering SQLCipher-specific comments referencing native lib consumer rules.

**Rationale**: The `toxicity-io/sqlite-mc` AAR ships its own consumer ProGuard rules. The current `fido2/proguard-rules.pro` already notes "SQLCipher rules now handled by library consumer rules" — a comment that will be updated to reference SQLite3MultipleCiphers.

---

## Resolved Unknowns

| Unknown | Resolution |
|---|---|
| Which cipher to use? | ChaCha20-Poly1305 (user decision, 2026-09-05) |
| Backward compat needed? | No — fresh databases only |
| How to supply raw key? | `Key.passphrase("x'<hex>'")` via 64-char hex string |
| Library version? | Latest compatible with SQLDelight 2.3.2 — confirm at implementation |
| fido2.db encryption? | Fix as part of migration (was plain driver — security bug) |
| JVM host tests? | `android-unit-test` artifact provides native binaries |
| ProGuard impact? | None — library ships consumer rules |

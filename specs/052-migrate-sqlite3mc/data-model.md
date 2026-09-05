# Data Model: Migrate Encrypted Database Layer to SQLite3MultipleCiphers

**Feature**: 052-migrate-sqlite3mc | **Phase**: 1 — Design | **Date**: 2026-09-05

---

## Overview

This feature is a pure infrastructure migration — no new data entities, tables, or schema changes are introduced. The data model of `VaultDatabase` and `Fido2Database` is unchanged. Only the encryption driver layer is replaced.

---

## Affected Components

### EncryptedDriverFactory (modified)

**Location**: `core/database/src/main/java/com/chimali/core/database/EncryptedDriverFactory.kt`

| Aspect | Before | After |
|---|---|---|
| Driver type | `AndroidSqliteDriver` + `SupportOpenHelperFactory` (SQLCipher) | `SQLiteMCDriver` (sqlite-mc) |
| Native init | `System.loadLibrary("sqlcipher")` in `init {}` with try/catch | None — driver manages natively |
| Cipher | AES-256-CBC (SQLCipher) | ChaCha20-Poly1305 (SQLite3MultipleCiphers default) |
| Key supply | Raw `ByteArray` passed to `SupportOpenHelperFactory` | Hex-encoded raw key string passed as `Key.passphrase("x'<hex>'")` |
| Key derivation | `deriveDatabaseKey()` — **unchanged** | `deriveDatabaseKey()` — **unchanged** |
| Key zeroing | `derivedKey.fill(0)` in `finally` block — **unchanged** | **unchanged** |

**Key hex-encoding rule**: the 32-byte derived key is encoded as 64 lowercase hex characters and wrapped: `"x'${hexString}'"`. A private extension function `ByteArray.toSqliteMcRawKey(): String` encodes this. It is zeroed after use (the `String` is ephemeral; the source `ByteArray` is zeroed in the existing `finally` block).

### DatabaseModule (unchanged interface, internal change)

**Location**: `core/database/src/main/java/com/chimali/core/database/di/DatabaseModule.kt`

No public API changes. `EncryptedDriverFactory.createDriver()` signature is preserved; only the internal implementation changes.

### Fido2Module (bug fix — new encrypted driver)

**Location**: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/di/Fido2Module.kt`

| Aspect | Before | After |
|---|---|---|
| Driver | Plain `AndroidSqliteDriver` (unencrypted) | `EncryptedDriverFactory.createDriver(Fido2Database.Schema, "fido2.db")` |
| Dependency | Direct `sqlcipher` import | Resolved through `EncryptedDriverFactory` (from `:core:database`) |
| `fido2.db` encryption | ❌ None (bug) | ✅ ChaCha20-Poly1305 |

`Fido2Module` gains a constructor parameter: `EncryptedDriverFactory`. This is automatically resolved by Koin since `EncryptedDriverFactory` is already a singleton in `databaseModule`.

---

## Dependency Graph Change

```
Before:
  core:database  →  net.zetetic:sqlcipher-android
  feature:fido2  →  net.zetetic:sqlcipher-android   [DUPLICATE, UNENCRYPTED BUG]

After:
  core:database  →  io.toxicity.sqlite-mc:driver-android
  feature:fido2  →  (no direct sqlite dep — uses core:database via EncryptedDriverFactory)
```

---

## Version Catalog Delta (`gradle/libs.versions.toml`)

| Change | Before | After |
|---|---|---|
| Version removed | `sqlcipher = "4.13.0"` | — |
| Version added | — | `sqlite-mc = "<latest-compatible-2.3.x>"` |
| Library removed | `sqlcipher = { group = "net.zetetic", name = "sqlcipher-android", ... }` | — |
| Library added | — | `sqlite-mc-driver = { group = "io.toxicity.sqlite-mc", name = "driver-android", version.ref = "sqlite-mc" }` |
| Library added | — | `sqlite-mc-android-unit-test = { group = "io.toxicity.sqlite-mc", name = "android-unit-test", version.ref = "sqlite-mc" }` |

---

## Test Infrastructure Change

| Module | Before | After |
|---|---|---|
| `core:database` (test) | Mocks around `System.loadLibrary` guard | `android-unit-test` artifact provides native lib; no mock guard |
| `feature:fido2` (androidHostTest) | `JdbcSqliteDriver` for schema tests; SQLCipher contract test is self-documenting stub | Same schema tests pass unchanged; contract test renamed to reference SQLite3MultipleCiphers |

---

## Security Model (Unchanged)

The security model is **preserved** — only the cipher changes:

| Property | Before (SQLCipher AES-256-CBC) | After (ChaCha20-Poly1305) |
|---|---|---|
| Encryption at rest | ✅ Page-level | ✅ Page-level |
| Authenticated encryption | ❌ HMAC only (separate) | ✅ Poly1305 AEAD (built-in) |
| Key derivation | PBKDF2-HMAC-SHA512, 2048 iter | **Unchanged** — same derivation |
| Key zeroing | `ByteArray.fill(0)` in `finally` | **Unchanged** |
| Key length | 256-bit | **Unchanged** |

ChaCha20-Poly1305 is strictly superior: it provides built-in authenticated encryption (Poly1305 MAC) rather than a separate HMAC, and performs better in software on devices without AES hardware acceleration.

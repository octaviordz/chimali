# Implementation Plan: Migrate Encrypted Database Layer from SQLCipher to SQLite3MultipleCiphers

**Branch**: `052-migrate-sqlite3mc` | **Date**: 2026-09-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/052-migrate-sqlite3mc/spec.md`

---

## Summary

Replace the `net.zetetic:sqlcipher-android` dependency in `:core:database` and `:feature:fido2` with `io.toxicity.sqlite-mc:driver-android`. Configure the new `SQLiteMCDriver` with **ChaCha20-Poly1305** (SQLite3MultipleCiphers preferred default cipher) and supply the existing PBKDF2-HMAC-SHA512-derived 256-bit key as a raw hex literal. No backward compatibility with existing SQLCipher-format files is required — databases are recreated fresh. As part of this migration, fix the pre-existing security bug in `Fido2Module` where `fido2.db` was created with a plain unencrypted driver. Remove the `System.loadLibrary("sqlcipher")` try/catch workaround and add the `android-unit-test` artifact for JVM host test support.

---

## Technical Context

**Language/Version**: Kotlin 2.3.21 (KMP module structure)

**Primary Dependencies**:
- `io.toxicity.sqlite-mc:driver-android` — replaces SQLCipher; ships `SQLiteMCDriver`
- `io.toxicity.sqlite-mc:android-unit-test` — native binaries for JVM host tests
- SQLDelight 2.3.2 — unchanged; `SQLiteMCDriver` is SQLDelight-compatible
- Koin 4.2.1 — unchanged DI

**Storage**: `vault.db` (VaultDatabase) and `fido2.db` (Fido2Database) — both encrypted with ChaCha20-Poly1305 after migration

**Testing**: JUnit 5 + MockK for `core:database` host tests; kotlin.test + JUnit 5 for `feature:fido2` host tests

**Target Platform**: Android (minSdk 28), KMP-ready module structure

**Project Type**: Mobile app (Android native with KMP shared modules)

**Performance Goals**: No regression — ChaCha20-Poly1305 is faster than AES-CBC in software (no AES hardware accel required); key derivation unchanged (PBKDF2 2048 iterations)

**Constraints**: Constitution §I.3 (SQLite database encryption); §X.5 (key zeroing in finally); Clean Architecture compliance; must compile and pass all tests.

**Scale/Scope**: 5 files modified, 1 baseline entry removed — **S (Small)** complexity

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I.3 SQLite database encryption | ✅ PASS | ChaCha20-Poly1305 (AEAD) is stronger than AES-256-CBC; encryption maintained. |
| I.4 SQLCipher exemption clause | ✅ PASS (superseded) | Migrating away; ChaCha20-Poly1305 with PBKDF2-SHA512 key derivation satisfies §I.3 equally. Constitution §I.4 wording references "SQLCipher" as a pragmatic exemption — implementation now uses SQLite3MultipleCiphers. |
| I.5 Memory Security | ✅ PASS | `deriveDatabaseKey()` unchanged; `finally` zeroing preserved; hex key string is ephemeral. |
| III. Architecture & Quality | ✅ PASS | `EncryptedDriverFactory` centralized pattern maintained; fido2.db encryption gap fixed. |
| XI.1 YAGNI / Three-Use Rule | ✅ PASS | Removing SQLCipher dependency, fixing existing bug; no new abstractions. |
| XI.2 Simplest Sufficient Solution | ✅ PASS | Direct AAR dependency — Gradle plugin skipped as unnecessary. |
| XI.3 Realistic Goal Setting | ✅ PASS | S complexity, fully verifiable via existing CI. |
| X.1 Coding Conventions | ✅ PASS | Conforms to project conventions; new extension in `ByteArrayExtensions.kt`. |
| IX. Local CI | ✅ PASS | Will verify via compile + existing test suite + detekt + ktlint. |

**Post-design re-check**: All gates still pass. The `feature:fido2` encryption gap fix is an improvement, not a violation.

---

## Project Structure

### Documentation (this feature)

```text
specs/052-migrate-sqlite3mc/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── checklists/
    └── requirements.md  # Spec quality checklist
```

### Source Code (repository root)

```text
gradle/
└── libs.versions.toml                              # [MODIFY] replace sqlcipher with sqlite-mc entries

core/database/
├── build.gradle.kts                                # [MODIFY] replace libs.sqlcipher with libs.sqliteMcDriver
└── src/main/java/com/chimali/core/database/
    └── EncryptedDriverFactory.kt                   # [MODIFY] swap SupportOpenHelperFactory → SQLiteMCDriver

feature/fido2/
├── build.gradle.kts                                # [MODIFY] remove androidMain sqlcipher dep; add test dep
├── detekt-baseline.xml                             # [MODIFY] remove stale SqlCipherWrapper.kt entry
└── src/
    └── androidMain/kotlin/com/chimali/fido2/
        └── di/
            └── Fido2Module.kt                      # [MODIFY] use EncryptedDriverFactory for fido2.db
    └── androidHostTest/kotlin/com/chimali/fido2/security/
        └── SecurityStorageIntegrityTest.kt         # [MODIFY] rename SQLCipher → SQLite3MultipleCiphers in KDoc/messages
```

**Structure Decision**: All changes reside within existing modules (`core:database`, `feature:fido2`) and the root version catalog. No new modules or files are required.

---

## Detailed Changes

### 1. [MODIFY] `gradle/libs.versions.toml`

**Remove**:
```toml
# [versions]
sqlcipher = "4.13.0"
# [libraries]
sqlcipher = { group = "net.zetetic", name = "sqlcipher-android", version.ref = "sqlcipher" }
```

**Add**:
```toml
# [versions]
sqlite-mc = "2.1.0-2.2.3-0"   # SQLDelight 2.1.0 prefix; compatible with project's SQLDelight 2.3.2 (stable driver API)
# [libraries]
sqlite-mc-driver = { group = "io.toxicity.sqlite-mc", name = "driver-android", version.ref = "sqlite-mc" }
sqlite-mc-android-unit-test = { group = "io.toxicity.sqlite-mc", name = "android-unit-test", version.ref = "sqlite-mc" }
```

---

### 2. [MODIFY] `core/database/build.gradle.kts`

**Remove**:
```kotlin
implementation(libs.sqlcipher)
```

**Add**:
```kotlin
implementation(libs.sqliteMcDriver)
// In testImplementation block:
testImplementation(libs.sqliteMcAndroidUnitTest)
```

---

### 3. [MODIFY] `core/database/src/main/java/com/chimali/core/database/EncryptedDriverFactory.kt`

**Key changes**:

a. **Remove import**: `import net.zetetic.database.sqlcipher.SupportOpenHelperFactory`

b. **Add imports**: `import io.toxicity.sqlite.mc.driver.SQLiteMCDriver`, `import io.toxicity.sqlite.mc.driver.config.encryption.key.Key`

c. **Remove `init {}` block** (the `System.loadLibrary("sqlcipher")` try/catch)

d. **Replace `createDriver()`**:

```kotlin
// Before:
val supportFactory = SupportOpenHelperFactory(derivedKey)
return AndroidSqliteDriver(schema, context, name, factory = supportFactory, cacheSize = 1)

// After (Key.raw supplies the derived ByteArray directly — no hex encoding needed):
val salt = DB_SALT_BYTES  // private val DB_SALT_BYTES = "chimali_db_salt".toByteArray(Charsets.UTF_8).copyOf(16)
val rawKey = Key.raw(key = derivedKey, salt = salt, fillKey = true)
return SQLiteMCDriver.Factory(dbName = name, schema = schema) {
    filesystem(context.databasesDir()) {
        // No encryption {} block = uses ChaCha20-Poly1305 default
    }
}.createBlocking(key = rawKey)
// derivedKey is also zeroed in the existing finally block (belt-and-suspenders with fillKey=true)
```

e. **Remove private extension function** — `Key.raw()` eliminates the need for any hex encoding helper.

f. **Update KDoc**: Replace "SQLCipher" with "SQLite3MultipleCiphers" in all method and class KDoc.

g. **Update constant name**: Rename `SQLCIPHER_KEY_LENGTH_BITS` → `DB_KEY_LENGTH_BITS` for accuracy.

---

### 4. [MODIFY] `feature/fido2/build.gradle.kts`

**Remove** from `androidMain.dependencies`:
```kotlin
implementation(libs.sqlcipher)
```

**Add** to `getByName("androidHostTest")` dependencies:
```kotlin
testImplementation(libs.sqliteMcAndroidUnitTest)
```

---

### 5. [MODIFY] `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/di/Fido2Module.kt`

**Replace** `fido2Database()` with:
```kotlin
/**
 * Provides the SQLDelight [Fido2Database] instance backed by SQLite3MultipleCiphers (ChaCha20-Poly1305).
 * The database is a singleton; the driver is created once per process via [EncryptedDriverFactory].
 */
@Single
fun fido2Database(encryptedDriverFactory: EncryptedDriverFactory): Fido2Database {
    val driver = encryptedDriverFactory.createDriver(Fido2Database.Schema, "fido2.db")
    return Fido2Database(driver)
}
```

**Remove import**: `import app.cash.sqldelight.driver.android.AndroidSqliteDriver`
**Add import**: `import com.chimali.core.database.EncryptedDriverFactory`

---

### 6. [MODIFY] `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/security/SecurityStorageIntegrityTest.kt`

- Rename test function: `` `T148d production SQLCipher database must NOT have plain SQLite magic header` `` → `` `T148d production SQLite3MultipleCiphers database must NOT have plain SQLite magic header` ``
- Update all KDoc references from "SQLCipher" to "SQLite3MultipleCiphers"
- Update string in assertion message: `"Contract: SQLCipher-encrypted DB header..."` → `"Contract: SQLite3MultipleCiphers-encrypted DB header..."`
- No logic changes

---

### 7. [MODIFY] `feature/fido2/detekt-baseline.xml`

Remove the stale entry:
```xml
<ID>UnusedParameter:SqlCipherWrapper.kt$SqlCipherWrapper$password: String</ID>
```

---

## Complexity Tracking

No constitution violations. No complexity justifications needed.

---

## Verification Plan

1. **Dependency check**: `./gradlew :core:database:dependencies :feature:fido2:dependencies | Select-String sqlcipher` — must return empty
2. **Compile check**: `./gradlew :core:database:compileDebugKotlin :feature:fido2:compileDebugKotlin`
3. **Unit tests (core)**: `./gradlew :core:database:test`
4. **Unit tests (fido2)**: `./gradlew :feature:fido2:testDebugUnitTest`
5. **Static analysis**: `./gradlew :core:database:detekt :feature:fido2:detekt :core:database:ktlintCheck :feature:fido2:ktlintCheck`
6. **Full CI**: `tools/local-ci.ps1`

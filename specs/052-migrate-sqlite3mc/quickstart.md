# Quickstart Validation Guide: SQLite3MultipleCiphers Migration

**Feature**: 052-migrate-sqlite3mc | **Date**: 2026-09-05

---

## Prerequisites

- JDK 17+
- Android SDK with compileSdk 37 configured
- Git on branch `052-migrate-sqlite3mc`
- Windows workstation (PowerShell) or Linux CI environment

---

## Step 1: Confirm Dependency Swap

Verify SQLCipher is gone and sqlite-mc is present:

```powershell
./gradlew :core:database:dependencies --configuration debugRuntimeClasspath | Select-String -Pattern "sqlcipher|sqlite-mc"
./gradlew :feature:fido2:dependencies --configuration debugRuntimeClasspath | Select-String -Pattern "sqlcipher|sqlite-mc"
```

**Expected**:
- No lines containing `sqlcipher` or `net.zetetic`
- At least one line containing `io.toxicity.sqlite-mc:driver-android`

---

## Step 2: Run JVM Host Tests (core:database)

```powershell
./gradlew :core:database:test --info
```

**Expected**:
- All `EncryptedDriverFactoryTest` tests pass
- No `UnsatisfiedLinkError` in output
- No `System.loadLibrary` warnings

---

## Step 3: Run JVM Host Tests (feature:fido2)

```powershell
./gradlew :feature:fido2:testDebugUnitTest --info
```

**Expected**:
- `SecurityStorageIntegrityTest` passes (all 3 tests: T148d-1, T148d-2, T148d-3)
- References to "SQLCipher" in test names/KDoc have been updated to "SQLite3MultipleCiphers"

---

## Step 4: Verify Encryption Is Active (ChaCha20-Poly1305)

This validation creates an encrypted database file and checks its binary header:

```powershell
# Run the EncryptedDriverFactory integration test (creates a real encrypted file)
./gradlew :core:database:test --tests "*.EncryptedDriverFactoryTest" --info
```

**Expected**: The derived file (if written to disk) must NOT begin with `53 51 4c 69 74 65 20 66 6f 72 6d 61 74 20 33 00` (plain SQLite magic). ChaCha20-Poly1305 produces an opaque binary page header.

---

## Step 5: Static Analysis

```powershell
./gradlew :core:database:detekt :feature:fido2:detekt :core:database:ktlintCheck :feature:fido2:ktlintCheck
```

**Expected**: Zero new violations. The stale `SqlCipherWrapper.kt` baseline entry in `feature/fido2/detekt-baseline.xml` should be removed.

---

## Step 6: Full Local CI

```powershell
tools/local-ci.ps1
```

**Expected**: Green end-to-end (compile + detekt + ktlint + all unit tests).

---

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---|---|---|
| `UnsatisfiedLinkError` in host tests | `android-unit-test` artifact missing from test deps | Add `testImplementation(libs.sqliteMcAndroidUnitTest)` to `core:database` and `feature:fido2` test deps |
| `SQLITE_NOTADB` at runtime | Key format incorrect | Ensure key is passed as `Key.passphrase("x'<64-hex-chars>'")` not raw bytes |
| Detekt failure on `SqlCipherWrapper.kt` | Stale baseline entry | Remove the entry from `feature/fido2/detekt-baseline.xml` |
| fido2.db unreadable after migration | Old plain-SQLite file from prior run | Delete the file — no backward compat; fresh encrypted DB will be created |

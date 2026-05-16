# Quickstart: SQL Convention Alignment

**Feature**: 043-sql-convention-alignment
**Date**: 2026-05-16

## Prerequisites

- Android Studio with Kotlin 2.1.x
- SQLDelight Gradle plugin configured
- Both database modules build successfully (`core:database`, `feature:fido2`)
- Local CI passes: `.\tools\local-ci.ps1`

## Verification Steps

### 1. Build the project

```powershell
.\gradlew assembleDebug
```

Confirms all SQLDelight code generation succeeds with new schema names.

### 2. Run unit tests

```powershell
.\gradlew test
```

Confirms all Kotlin references to generated types compile and pass.

### 3. Verify migration on existing database

```powershell
.\gradlew connectedAndroidTest
```

Confirms migrations run without data loss on a real SQLite/SQLCipher instance.

### 4. Run full local CI

```powershell
.\tools\local-ci.ps1
```

Confirms Ktlint, Detekt, lint, and all unit tests pass.

## Key Files to Inspect

| File | What Changed |
|------|-------------|
| `core/database/.../Vault.sq` | All table/column names → snake_case |
| `core/database/.../VaultDatabase/4.sqm` | Migration: rename all VaultDatabase tables/columns |
| `feature/fido2/.../Fido2Database.sq` | All table/column/view/index names → snake_case |
| `feature/fido2/.../Fido2Database/11.sqm` | Migration: rename all Fido2Database tables/columns |
| `feature/fido2/.../PasskeyCredential.sq` | Query references updated to snake_case |
| `feature/fido2/.../RelyingParty.sq` | Query references updated to snake_case |
| `feature/fido2/.../UserConsentRecord.sq` | Query references updated to snake_case |
| `feature/fido2/.../PairedDevice.sq` | Query references updated to snake_case |
| `feature/fido2/.../data/mapper/EntityMappers.kt` | Accessor names updated |
| `feature/fido2/.../data/dao/PasskeyCredentialDao.kt` | Parameter names updated |
| `feature/fido2/.../data/dao/RelyingPartyDao.kt` | Parameter names updated |

## Rollback

If migration causes issues on a device:
1. The migration is wrapped in a transaction — partial failures auto-rollback
2. Uninstall/reinstall the app (development builds only)
3. No rollback migration is needed since this is a forward-only schema evolution

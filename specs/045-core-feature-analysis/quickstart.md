# Quickstart: Core Feature Migration

**Feature**: Core Feature Migration
**Date**: 2026-05-18
**Branch**: `045-core-feature-analysis`

## Prerequisites

- Android Studio with Kotlin 2.x and AGP
- Local CI passes: `.\tools\local-ci.ps1`
- On branch `045-core-feature-analysis`

## Migration Order

Execute these migrations in strict sequence — each phase must pass local CI before proceeding to the next.

### Phase 1: BouncyCastle Provider Registration (P3 — smallest, lowest risk)

1. Add `Security.addProvider(BouncyCastleProvider())` to `ChimaliApplication.onCreate()` **before** Koin initialization
2. Add `import java.security.Security` and `import org.bouncycastle.jce.provider.BouncyCastleProvider` to `ChimaliApplication.kt`
3. In `WarmUpHelper.warmUpBouncyCastle()`, remove the `Security.addProvider()` call — keep only the ephemeral EC sign warm-up
4. In `Fido2CryptoService` init block, remove the redundant `Security.addProvider()` call
5. Run `.\tools\local-ci.ps1` — all tests must pass

### Phase 2: Vault Clipboard Cleanup (P2 — deletion only)

1. Delete `feature/vault/src/main/java/.../internal/ClipboardManagerWrapper.kt`
2. In `VaultViewModel`, replace `ClipboardManagerWrapper` with `ClipboardManagerService` from `com.chimali.core.clipboard`
3. Update `vaultModule` Koin DSL if it provides `ClipboardManagerWrapper` — remove the binding
4. Run `.\tools\local-ci.ps1` — all tests must pass

### Phase 3: SQLCipher EncryptedDriverFactory (P1 — largest scope)

1. In `core/database/`, create `EncryptedDriverFactory.kt` that uses `SupportFactory` from SQLCipher
2. Update `DatabaseModule.kt` to use `EncryptedDriverFactory` instead of raw `AndroidSqliteDriver`
3. Delete `feature/fido2/src/androidMain/.../data/storage/SqlCipherWrapper.kt`
4. Remove any FIDO2 references to `SqlCipherWrapper` from DI modules
5. Run `.\tools\local-ci.ps1` — all tests must pass

### Phase 4: PlatformUserVerification Migration (P4 — expect/actual move)

1. Copy `PlatformUserVerification.kt` (expect) from `feature/fido2/src/commonMain/.../platform/` to `core/security/src/commonMain/.../biometrics/`
2. Copy Android `actual` from `feature/fido2/src/androidMain/.../platform/` to `core/security/src/androidMain/.../biometrics/`
3. Copy iOS `actual` from `feature/fido2/src/iosMain/.../platform/` to `core/security/src/iosMain/.../biometrics/`
4. Update package declarations from `com.chimali.fido2.platform` to `com.chimali.core.security.biometrics`
5. Add `implementation(libs.androidx.biometric)` to `:core:security` androidMain dependencies
6. Update all import statements in `:feature:fido2` to use the new package
7. Delete the old files from `:feature:fido2`
8. Run `.\tools\local-ci.ps1` — all tests must pass

## Validation

After all phases complete:

```powershell
.\tools\local-ci.ps1
```

Expected: zero failures, zero new warnings, zero regressions.

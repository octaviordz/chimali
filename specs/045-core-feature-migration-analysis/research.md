# Research: Core Feature Migration

**Feature**: Core Feature Migration
**Date**: 2026-05-18
**Branch**: `045-core-feature-migration-analysis`

## Research Findings

### R1: Clipboard Manager — Already Migrated

**Decision**: The clipboard migration to `:core:common` is **already complete**. No further migration work is required.

**Rationale**: The codebase investigation revealed:
- `ClipboardManagerService` interface exists in `core/common/src/commonMain/.../ClipboardManagerService.kt`
- `AndroidClipboardManagerService` implementation exists in `core/common/src/androidMain/` — fully functional with auto-clear timer, mutex-guarded access, sensitive-data flag (API 33+), and `clearPrimaryClip()` support
- `ClipboardModule` Koin DI module exists and is already registered in `ChimaliApplication.onCreate()`
- `ClipboardError` sealed class with structured error types exists in commonMain
- `IosClipboardManagerService` placeholder exists in iosMain
- Tests exist in `androidHostTest`
- `:feature:fido2` DevToolsViewModel already uses `ClipboardManagerService` from core (not the old wrapper)

**Remaining cleanup**: The old `ClipboardManagerWrapper` stub (13 lines) still exists in `feature/vault/src/main/java/.../internal/ClipboardManagerWrapper.kt` and is still injected into `VaultViewModel`. This stub should be **deleted** and `VaultViewModel` should be refactored to use the core `ClipboardManagerService` instead.

**Alternatives considered**: None — the migration destination is already built.

---

### R2: SQLCipher Wrapper — Core Module Ready, Stub to Replace

**Decision**: Build the production `EncryptedDriverFactory` directly in `:core:database`. Delete the stub in `:feature:fido2`.

**Rationale**:
- `core/database/build.gradle.kts` already declares `implementation(libs.sqlcipher)` dependency
- `DatabaseModule.kt` has an explicit TODO: *"For final production, we'll wrap this with SQLCipher for encryption"*
- The current `SqlCipherWrapper` in `:feature:fido2` is a non-functional stub (returns `"SQLCipherSupportFactory"` as a string literal)
- Both FIDO2 (`Fido2Database`) and Vault (`VaultDatabase`) databases require encryption per Constitution §I.3
- `:core:database` currently creates `VaultDatabase` via `AndroidSqliteDriver` — this will need to switch to SQLCipher's `SupportSQLiteOpenHelper.Factory`

**Key technical details**:
- SQLCipher on Android uses `net.zetetic:sqlcipher-android` which provides `SupportFactory` wrapping `SQLiteDatabase`
- SQLDelight integrates via `AndroidSqliteDriver(schema, context, name, factory = supportFactory)`
- Key derivation must use `PBKDF2-SHA512` from the master key per Constitution §I.3
- The key material (`ByteArray`) MUST be zeroed after driver creation per Constitution §X.5

**Alternatives considered**:
- Migrating the existing stub — rejected because the stub has no useful logic
- Keeping encryption per-feature — rejected because it violates Constitution's mandate for standardized encryption

---

### R3: BouncyCastle Provider Registration — Move to App Startup

**Decision**: Move `Security.addProvider(BouncyCastleProvider())` to `ChimaliApplication.onCreate()` and make `WarmUpHelper.warmUpBouncyCastle()` idempotent.

**Rationale**:
- Currently registered lazily in `WarmUpHelper.warmUpBouncyCastle()` and redundantly in `Fido2CryptoService` (init block at line 77)
- `:core:security` already depends on `libs.bouncycastle.provider` in `androidMain`
- If Vault's future post-quantum operations need BouncyCastle before FIDO2 initializes, registration would fail
- The provider registration is a single idempotent call (`~20ms`) — trivial to move to app startup

**Key technical details**:
- `Security.addProvider()` is idempotent if the provider is already registered (returns -1, no error)
- The BouncyCastle dependency already exists in `:core:security` → no new dependency needed
- `WarmUpHelper.warmUpBouncyCastle()` will be refactored to skip the provider registration and only perform the ephemeral EC sign warm-up
- The `Fido2CryptoService.init` block's redundant `Security.addProvider` call should be removed

**Alternatives considered**:
- Creating a `CryptoProviderInitializer` in `:core:security` — rejected as over-engineering for a one-liner
- Using AndroidX App Startup library — rejected; the project already has explicit initialization in `ChimaliApplication`

---

### R4: PlatformUserVerification — Package Relocation to `:core:security`

**Decision**: Move the `PlatformUserVerification` expect/actual class from `com.chimali.fido2.platform` to `com.chimali.core.security.biometrics` in `:core:security`.

**Rationale**:
- The class is a pure platform capability query (3 boolean methods, zero side effects)
- Contains no FIDO2-specific logic — it wraps `BiometricManager` queries
- `:core:security` already exists with KMP structure (commonMain, androidMain, iosMain, KSP wiring)
- `:core:security` already depends on `libs.androidx.core.ktx` — but will need `libs.androidx.biometric` added to androidMain dependencies
- The Android `actual` requires `Context` injection (already available via Koin's `androidContext()`)
- The iOS `actual` is a safe-default stub (returns false) — no platform dependencies needed

**Key technical details**:
- Package change: `com.chimali.fido2.platform` → `com.chimali.core.security.biometrics`
- New dependency in `:core:security` androidMain: `implementation(libs.androidx.biometric)`
- `:feature:fido2` must update all import statements referencing the old package
- Koin registration: The `@Single` annotation on the `actual class` will be picked up by `:core:security`'s `SecurityModule` KSP scan if we place it under `com.chimali.core.security`
- Existing `@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")` must be preserved

**Alternatives considered**:
- Moving to `:core:common` — rejected because `:core:common` has no biometric/security dependencies
- Creating `:core:biometrics` — rejected per Constitution §XI.2 (module count must be justified by concrete isolation benefit; `:core:security` already exists and is the natural home)

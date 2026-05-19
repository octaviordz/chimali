# Data Model: Core Feature Migration

**Feature**: Core Feature Migration
**Date**: 2026-05-18
**Branch**: `045-core-feature-migration-analysis`

## Entities

### 1. EncryptedDriverFactory (NEW — `:core:database`)

Produces encrypted SQLDelight drivers for any feature module.

| Attribute | Description |
|-----------|-------------|
| `masterKeyProvider` | Dependency providing the derived encryption key bytes |
| `context` | Android application context for database file resolution |

**Operations**:
- `createDriver(schema, name) → SqlDriver` — Creates an encrypted driver using SQLCipher's SupportFactory
- `verifyIntegrity(dbPath) → Boolean` — Runs `PRAGMA integrity_check` on an encrypted database

**Validation Rules**:
- Key material (`ByteArray`) MUST be zeroed after driver creation (Constitution §X.5)
- Empty or zero-length keys MUST be rejected with an explicit error
- Driver creation MUST fail-closed: never silently create an unencrypted database

**State Transitions**: None (stateless factory)

---

### 2. ClipboardManagerService (EXISTS — `:core:common`)

Already fully implemented. Interface for secure clipboard operations with auto-clear.

| Attribute | Description |
|-----------|-------------|
| `copySensitiveData(label, text, clearDelayMs)` | Copies sensitive text with auto-clear timer |
| `clearClipboard()` | Immediately clears clipboard and cancels pending timers |

**Platform implementations**:
- `AndroidClipboardManagerService` — uses `ClipboardManager`, `setPrimaryClip`, `clearPrimaryClip`, Mutex for thread safety
- `IosClipboardManagerService` — placeholder stub

**No changes needed** to the core entity. Only the Vault's obsolete `ClipboardManagerWrapper` stub needs deletion.

---

### 3. PlatformUserVerification (MOVE — `:feature:fido2` → `:core:security`)

Pure capability query for biometric hardware state.

| Attribute | Description |
|-----------|-------------|
| `isAvailable()` | True if any biometric or device credential is enrolled and hardware is present |
| `canAuthenticate()` | True if strong biometric (Class 3) is immediately usable |
| `isDeviceSecure()` | True if device has at least PIN/pattern/password lock |

**Platform implementations**:
- Android `actual`: Wraps `BiometricManager` with `Authenticators.BIOMETRIC_STRONG` and `DEVICE_CREDENTIAL` queries
- iOS `actual`: Returns `false` for all methods (placeholder until LAContext integration)

**Validation Rules**:
- MUST be side-effect free (no UI, no coroutines, no blocking I/O)
- MUST NOT throw on any platform — unsupported platforms return safe defaults

---

## Entity Relationships

```text
ChimaliApplication
 ├── registers BouncyCastleProvider (at startup, before feature init)
 ├── provides EncryptedDriverFactory (via Koin, core:database)
 │    ├── used by Fido2Database creation
 │    └── used by VaultDatabase creation
 ├── provides ClipboardManagerService (via Koin, core:common) [EXISTS]
 │    ├── used by feature:fido2 DevToolsViewModel [DONE]
 │    └── used by feature:vault VaultViewModel [PENDING — replace wrapper]
 └── provides PlatformUserVerification (via Koin, core:security) [PENDING]
      └── used by feature:fido2 UserVerificationService
```

## Deleted Entities

| Entity | Location | Reason |
|--------|----------|--------|
| `SqlCipherWrapper` | `feature/fido2/src/androidMain/.../data/storage/` | Non-functional stub replaced by `EncryptedDriverFactory` in `:core:database` |
| `ClipboardManagerWrapper` | `feature/vault/src/main/.../internal/` | Obsolete stub; core `ClipboardManagerService` already exists and is registered |

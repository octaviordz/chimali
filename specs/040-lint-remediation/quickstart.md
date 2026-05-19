# Quickstart: Lint Remediation

**Branch**: `040-lint-remediation` | **Date**: 2026-05-15

## Overview

This feature removes all invalid inline `@Suppress` annotations from the codebase as identified by the [039 audit](../039-linting-baseline-audit/audit.md). No new features are introduced — this is a pure code quality remediation.

## Remediation Patterns

### Pattern 1: Remove Redundant `@Suppress("FunctionNaming")`

**Before**:
```kotlin
@Suppress("FunctionNaming")
@Composable
fun VaultListScreen(...) { ... }
```

**After**:
```kotlin
@Composable
fun VaultListScreen(...) { ... }
```

The Detekt config already has `ignoreAnnotated: ['Composable']` for `FunctionNaming`.

### Pattern 2: Replace `TODO:` with `DEFERRED(040):`

**Before**:
```kotlin
@Suppress("ForbiddenComment")
class SomeRepository {
    // TODO: Implement database save logic
    override suspend fun save(...) = Outcome.Success(Unit)
}
```

**After**:
```kotlin
class SomeRepository {
    // DEFERRED(040): Persistence — pending schema design
    override suspend fun save(...) = Outcome.Success(Unit)
}
```

### Pattern 3: Specific Exception Handling

**Before**:
```kotlin
@Suppress("TooGenericExceptionCaught")
class VaultRepositoryImpl {
    suspend fun save(item: Item): Outcome<Unit, DomainError> = try {
        database.doWork()
        Outcome.Success(Unit)
    } catch (e: Exception) {
        Outcome.Error(DomainError.DatabaseError(e.message, e))
    }
}
```

**After (Option A — specific catches)**:
```kotlin
class VaultRepositoryImpl {
    suspend fun save(item: Item): Outcome<Unit, DomainError> = try {
        database.doWork()
        Outcome.Success(Unit)
    } catch (e: android.database.SQLException) {
        Outcome.Error(DomainError.DatabaseError(e.message ?: "SQL error", e))
    } catch (e: IllegalArgumentException) {
        Outcome.Error(DomainError.ValidationError(e.message ?: "Invalid data", e))
    }
}
```

**After (Option B — `runCatchingOutcome` for safety-net)**:
```kotlin
class VaultRepositoryImpl {
    suspend fun save(item: Item): Outcome<Unit, DomainError> =
        runCatchingOutcome(
            onError = { DomainError.DatabaseError("Failed to save", it) }
        ) {
            database.doWork()
        }
}
```

### Pattern 4: SDK-Gated Deprecated API

**Before**:
```kotlin
@Suppress("DEPRECATION")
val device = intent.getParcelableExtra<BluetoothDevice>(key)
```

**After**:
```kotlin
val device = intent.getParcelableExtraCompat<BluetoothDevice>(key)

// Extension function (defined once in the file):
private inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }
```

## Validation

After all changes, run:

```powershell
.\tools\local-ci.ps1
```

This executes Detekt, Ktlint, and all unit tests. All must pass with zero violations.

## Files NOT to Touch

| File | Suppression | Reason |
|------|------------|--------|
| `FunctionalCatching.kt` | `TooGenericExceptionCaught` | Architectural boundary — documented in-file |
| ~~`PlatformBluetoothHid.kt`~~ | ~~`EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING`~~ | **Superseded** by spec 046 |
| ~~`PlatformUserVerification.kt`~~ | ~~`EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING`~~ | **Removed** — spec 045 migration |
| `Ctap2AttestationStatementTest.kt` | `UNCHECKED_CAST` | Valid test scope |
| `Ctap2Fido21FlagsTest.kt` | `UNCHECKED_CAST` | Valid test scope |

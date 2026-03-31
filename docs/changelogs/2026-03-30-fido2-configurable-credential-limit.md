# FIDO2 Configurable Credential Limit

**Date**: 2026-03-30  
**Feature**: FR-HID-022 (T115a)  
**Spec**: `specs/004-fido2-hid/spec.md`  
**Module**: `:feature:fido2`  

## Summary

Migrated the FIDO2 credential storage limit from a hardcoded compile-time constant to a runtime-configurable system setting, with a secure default of **1000** credentials. This change satisfies **FR-HID-022** and enables the quota to be adjusted without a code change.

## Changes

### Domain Layer

#### `Fido2SettingsRepository.kt` [NEW]
`feature/fido2/src/main/kotlin/com/chimali/fido2/domain/repository/Fido2SettingsRepository.kt`

New domain interface abstracting read/write access to FIDO2 system configuration:
```kotlin
interface Fido2SettingsRepository {
    suspend fun getMaxCredentialCount(): Int
    suspend fun setMaxCredentialCount(count: Int)
}
```

#### `Fido2Exception.kt` [MODIFIED]
`TooManyCredentials` now exposes the `limit` as a public `val` property, enabling callers (and tests) to inspect the exact quota that was exceeded:
```kotlin
class TooManyCredentials(val limit: Int) : Fido2Exception(...)
```

#### `RegisterCredentialUseCase.kt` [MODIFIED]
Replaced the hardcoded constant check with a dynamic quota lookup:
```kotlin
val limit = settingsRepository.getMaxCredentialCount()
val currentCount = credentialRepository.getCredentialStatistics().totalCredentials
if (currentCount >= limit) throw Fido2Exception.TooManyCredentials(limit)
```

### Data Layer

#### `Fido2SettingsRepositoryImpl.kt` [NEW]
`feature/fido2/src/main/kotlin/com/chimali/fido2/data/repository/Fido2SettingsRepositoryImpl.kt`

Backed by `EncryptedSharedPreferences` for security-at-rest:
- Key: `fido2_max_credential_count`
- Default: `1000`
- Injected via Hilt (`@Singleton`)

### Dependency Injection

#### `Fido2Module.kt` [MODIFIED]
Added `@Binds` mapping for `Fido2SettingsRepository → Fido2SettingsRepositoryImpl`.

### Documentation

#### `specs/004-fido2-hid/spec.md` [MODIFIED]
Updated **FR-HID-022** from a fixed limit description to:
> System MUST support configurable storage of passkey credentials per user (default: 1000).

#### `specs/004-fido2-hid/checklists/hdk-conformance.md` [MODIFIED]
Updated **Δ-004** to document that the configurable credential limit defaults to 1000 and is retrieved at runtime via `Fido2SettingsRepository`, remaining well within the accepted signed 31-bit index ceiling (2³¹−1).

## Tests

### Unit Tests (`RegisterCredentialUseCaseTest.kt`) [MODIFIED]
Added 4 targeted tests for dynamic limit enforcement:
- `should reject registration when credential limit is reached` — fails at exact quota
- `should allow registration when count is below the limit` — succeeds below quota
- `should use dynamic limit from settings repository` — verified mock interaction
- `TooManyCredentials carries the correct limit value` — asserts `exception.limit == n`

Default mock `getMaxCredentialCount() returns 1000` added to `setUp()`.

### Integration Tests [MODIFIED]
- `Fido2StressTest.kt` — mocked repository with limit `2000` to avoid interference with stress volume.
- `MultiAlgorithmIntegrationTest.kt` — mocked repository with default limit.

## Verification

```
.\gradlew :feature:fido2:testDebugUnitTest
> 47 tests completed, 0 failed
BUILD SUCCESSFUL
```

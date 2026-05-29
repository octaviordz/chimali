# 2026-05-28 — Remove AuthenticateCredentialUseCase Stub & Consolidate Authentication Architecture

**Branch**: `051-remove-auth-credential-stub`

## Summary

Removed the dead-code `AuthenticateCredentialUseCase` introduced in branch `050`, pruned its
associated stub surfaces from the repository and service layers, and aligned
`RegistrationPromptViewModel` with the NowInKMP architectural pattern of ViewModels injecting use
cases directly as the primary application-logic interactors.

---

## Motivation

Branch `050-authenticate-credential-usecase` created `AuthenticateCredentialUseCase` as a
structural refactoring, but investigation revealed it contained **no real authentication logic**.
The underlying `Fido2RepositoryImpl.authenticateCredential()` was a placeholder stub returning a
hardcoded `mock-authentication-id`, explicitly deferred in a `DEFERRED(040)` comment.

The **real and complete** FIDO2 authentication use case — `GetAssertionUseCase` — was already
implementing the full CTAP2 GetAssertion ceremony (credential discovery, user verification,
cryptographic signing, sign-count management, authenticator data assembly) and was already being
invoked directly by `AuthenticationPromptViewModel`. `AuthenticateCredentialUseCase` was therefore
dead code wrapping a mock stub.

In parallel, `RegistrationPromptViewModel` still injected `Fido2Service` and called
`Fido2Service.makeCredential()`, adding an unnecessary indirection level over
`RegisterCredentialUseCase` — inconsistent with the direct use-case injection pattern already used
by `AuthenticationPromptViewModel`.

---

## Changes

### Deleted Files

| File | Reason |
|------|--------|
| `feature/fido2/src/androidMain/.../domain/usecase/AuthenticateCredentialUseCase.kt` | Dead code — wraps a mock stub with no real logic |
| `feature/fido2/src/androidHostTest/.../domain/usecase/AuthenticateCredentialUseCaseTest.kt` | Tests a deleted class |

### Modified Files

#### `Fido2Repository.kt` (interface)
- Removed `authenticateCredential(rpId, userName)` method declaration.
- This method had only one caller (the deleted use case) and was a placeholder returning a
  hardcoded mock `CredentialId`.

#### `Fido2RepositoryImpl.kt` (implementation)
- Removed `authenticateCredential()` implementation (the mock stub body).
- Class now implements the trimmed interface with no behavioral change to real code paths.

#### `Fido2Service.kt` (interface)
- Removed `authenticateWithCredential(rpId, userName)` method declaration.
- This method was only ever called by `Fido2ServiceImpl` delegating to the deleted use case.

#### `Fido2ServiceImpl.kt`
- Removed `authenticateWithCredential()` method and its delegation to
  `AuthenticateCredentialUseCase`.
- Removed `AuthenticateCredentialUseCase` constructor parameter.
- Fixed ktlint `import-ordering` violation (merged stray import block) and
  `no-consecutive-blank-lines` violation.

#### `RegistrationPromptViewModel.kt`
- **Removed** `Fido2Service` constructor injection.
- **Added** `RegisterCredentialUseCase` direct constructor injection.
- `performRegistration()` now calls `registerCredentialUseCase(options)` directly instead of
  routing through `fido2Service.makeCredential(options)`.
- All state transitions, error propagation, cancellation, and success paths are **identical**; only
  the call site changes.
- Fixed ktlint `import-ordering` violation (`usecase` import reordered after `service` imports).

---

## Architecture Impact

### Before

```
RegistrationPromptViewModel
  └─► Fido2Service.makeCredential()
        └─► RegisterCredentialUseCase(options)  ← one extra hop

AuthenticationPromptViewModel
  └─► GetAssertionUseCase(request)             ← correct direct injection
```

### After

```
RegistrationPromptViewModel
  └─► RegisterCredentialUseCase(options)        ← symmetric with auth VM

AuthenticationPromptViewModel
  └─► GetAssertionUseCase(request)              ← unchanged
```

Both ViewModels now follow the NowInKMP pattern: **ViewModel → UseCase** with no service-layer
indirection for core FIDO2 ceremonies.

---

## Verification

| Check | Result |
|-------|--------|
| `.\gradlew compileAndroidMain` | ✅ BUILD SUCCESSFUL |
| `.\gradlew :feature:fido2:testAndroidHostTest` | ✅ All tests pass |
| `.\gradlew :feature:fido2:detekt` | ✅ No violations |
| `.\gradlew :feature:fido2:ktlintCheck` | ✅ No violations |

---

## Files Touched

```
feature/fido2/src/androidMain/kotlin/com/chimali/fido2/
  ├── data/repository/Fido2RepositoryImpl.kt          [modified]
  ├── domain/repository/Fido2Repository.kt            [modified]
  ├── domain/service/Fido2Service.kt                  [modified]
  ├── domain/service/impl/Fido2ServiceImpl.kt         [modified]
  └── presentation/viewmodel/RegistrationPromptViewModel.kt  [modified]

feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/
  └── domain/usecase/AuthenticateCredentialUseCaseTest.kt    [deleted]

feature/fido2/src/androidMain/kotlin/com/chimali/fido2/
  └── domain/usecase/AuthenticateCredentialUseCase.kt        [deleted]
```

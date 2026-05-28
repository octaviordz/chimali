# Implementation Plan: Remove AuthenticateCredentialUseCase Stub and Consolidate into GetAssertionUseCase

**Branch**: `051-remove-auth-credential-stub` | **Date**: 2026-05-28 | **Spec**: [spec.md](file:///d:/octav/source/repos/Chimali/specs/051-remove-auth-credential-stub/spec.md)

**Input**: Feature specification from `specs/051-remove-auth-credential-stub/spec.md`

## Summary

Validate that `AuthenticateCredentialUseCase` is a stub wrapping a mock/placeholder implementation and remove it entirely. Move all FIDO2 authentication responsibilities to `GetAssertionUseCase` (which is already the correct and complete CTAP2 GetAssertion ceremony implementation). Refactor `RegistrationPromptViewModel` to inject `RegisterCredentialUseCase` directly instead of via `Fido2Service`, aligning it with the NowInKMP architecture where ViewModels communicate directly with use cases.

## Technical Context

**Language/Version**: Kotlin (KMP module structure)

**Primary Dependencies**: Koin (with Koin Compiler Plugin for annotation-based DI)

**Storage**: N/A (deleting unused repository methods)

**Testing**: kotlin.test (commonTest), JUnit 5 + MockK for platform-specific tests

**Target Platform**: Android (minSdk 28), KMP-ready module structure

**Project Type**: Mobile app (Android native with KMP shared modules)

**Performance Goals**: N/A (pure structural refactoring and dead code cleanup)

**Constraints**: Clean Architecture compliance; must compile and pass all tests; no behavioral regressions.

**Scale/Scope**: 4 files deleted/modified — S (Small) complexity

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| III. Architecture & Quality | ✅ PASS | Direct use case injection at ViewModel level (NowInKMP architectural pattern). |
| XI.1 YAGNI / Three-Use Rule | ✅ PASS | Removing dead code and mock stubs from the codebase. |
| XI.2 Simplest Sufficient Solution | ✅ PASS | Cleaning up redundant service and repository layer methods. |
| XI.3 Realistic Goal Setting | ✅ PASS | Small scope (S complexity), fully verifiable. |
| X.1 Coding Conventions | ✅ PASS | Conforms to project conventions. |
| IX. Local CI | ✅ PASS | Will verify via compile + existing test suite. |

**Post-design re-check**: All gates still pass.

## Project Structure

### Documentation (this feature)

```text
specs/051-remove-auth-credential-stub/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
└── quickstart.md        # Phase 1 output
```

### Source Code (repository root)

```text
feature/fido2/src/
├── androidMain/kotlin/com/chimali/fido2/
│   ├── domain/
│   │   ├── usecase/
│   │   │   ├── AuthenticateCredentialUseCase.kt   # [DELETE]
│   │   │   ├── GetAssertionUseCase.kt             # [UNCHANGED]
│   │   │   └── RegisterCredentialUseCase.kt       # [UNCHANGED]
│   │   ├── service/
│   │   │   ├── Fido2Service.kt                    # [MODIFY] - Remove authenticateWithCredential
│   │   │   └── impl/
│   │   │       └── Fido2ServiceImpl.kt            # [MODIFY] - Remove method and UseCase injection
│   │   └── repository/
│   │       └── Fido2Repository.kt                 # [MODIFY] - Remove authenticateCredential
│   ├── data/repository/
│   │   └── Fido2RepositoryImpl.kt                 # [MODIFY] - Remove authenticateCredential
│   └── presentation/viewmodel/
│       ├── AuthenticationPromptViewModel.kt       # [UNCHANGED]
│       └── RegistrationPromptViewModel.kt         # [MODIFY] - Swap Fido2Service for RegisterCredentialUseCase
└── androidHostTest/kotlin/com/chimali/fido2/
    └── domain/usecase/
        └── AuthenticateCredentialUseCaseTest.kt   # [DELETE]
```

**Structure Decision**: All changes reside within the existing `feature/fido2` module.

## Detailed Changes

### 1. [DELETE] `AuthenticateCredentialUseCase.kt` and `AuthenticateCredentialUseCaseTest.kt`

- Delete `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/usecase/AuthenticateCredentialUseCase.kt`
- Delete `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/domain/usecase/AuthenticateCredentialUseCaseTest.kt`

### 2. [MODIFY] `Fido2Repository.kt` & `Fido2RepositoryImpl.kt`

- Remove `authenticateCredential` from `Fido2Repository` interface.
- Remove `authenticateCredential` override implementation from `Fido2RepositoryImpl`.

### 3. [MODIFY] `Fido2Service.kt` & `Fido2ServiceImpl.kt`

- Remove `authenticateWithCredential` from `Fido2Service` interface.
- Remove `authenticateWithCredential` override implementation and `AuthenticateCredentialUseCase` constructor dependency from `Fido2ServiceImpl`.

### 4. [MODIFY] `RegistrationPromptViewModel.kt`

- Remove `Fido2Service` constructor parameter.
- Add `RegisterCredentialUseCase` constructor parameter.
- Update `performRegistration` to invoke `registerCredentialUseCase(options)` directly instead of `fido2Service.makeCredential(options)`.

## Complexity Tracking

No constitution violations. No complexity justifications needed.

## Verification Plan

1. **Compile check**: Run `.\gradlew :feature:fido2:compileDebugKotlin`
2. **Unit tests**: Run `.\gradlew :feature:fido2:testDebugUnitTest`
3. **Static analysis**: Run `.\gradlew :feature:fido2:detekt :feature:fido2:ktlintCheck`

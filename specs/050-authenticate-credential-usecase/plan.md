# Implementation Plan: Authenticate Credential Use Case

**Branch**: `050-authenticate-credential-usecase` | **Date**: 2026-05-26 | **Spec**: [spec.md](file:///d:/octav/source/repos/Chimali/specs/050-authenticate-credential-usecase/spec.md)

**Input**: Feature specification from `specs/050-authenticate-credential-usecase/spec.md`

## Summary

Extract the existing `authenticateCredential` delegation from `Fido2ServiceImpl` into a dedicated `AuthenticateCredentialUseCase` class following the established use case pattern in the project. This is a pure structural refactoring — zero behavioral change. The new use case follows the same `@Factory` + `operator fun invoke()` pattern used by all 12 existing use cases, and `Fido2ServiceImpl` is rewired to delegate through it (mirroring how `makeCredential` delegates to `RegisterCredentialUseCase`).

## Technical Context

**Language/Version**: Kotlin (KMP module structure)

**Primary Dependencies**: Koin (with Koin Compiler Plugin for annotation-based DI)

**Storage**: N/A (no storage changes — delegates to existing `Fido2Repository`)

**Testing**: kotlin.test (commonTest), JUnit 5 + MockK for platform-specific tests

**Target Platform**: Android (minSdk 28), KMP-ready module structure

**Project Type**: Mobile app (Android native with KMP shared modules)

**Performance Goals**: N/A (pure structural refactoring, no performance implications)

**Constraints**: Must not alter any business logic, error handling, or return types

**Scale/Scope**: 2 files modified, 1 file created — S (Small) complexity

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| III. Architecture & Quality | ✅ PASS | Clean Architecture use case extraction — directly supports this principle |
| XI.1 YAGNI / Three-Use Rule | ✅ PASS | Use case pattern is the established architectural mandate (Constitution III); currently 12 use cases follow this pattern. `authenticateWithCredential` is the only service method that bypasses the pattern |
| XI.2 Simplest Sufficient Solution | ✅ PASS | Single-class extraction + rewire. No new abstractions, modules, or patterns introduced |
| XI.3 Realistic Goal Setting | ✅ PASS | Complexity: S (Small). Single-session deliverable |
| X.1 Coding Conventions | ✅ PASS | File named after primary class; `@Factory` annotation; `operator fun invoke` pattern |
| IX. Local CI | ✅ PASS | Will verify via compile + existing test suite |

**Post-design re-check**: All gates still pass. No design decisions introduced any new complexity.

## Project Structure

### Documentation (this feature)

```text
specs/050-authenticate-credential-usecase/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Phase 0 output (trivial — no unknowns)
├── data-model.md        # Phase 1 output (N/A — no data model changes)
└── quickstart.md        # Phase 1 output
```

### Source Code (repository root)

```text
feature/fido2/src/androidMain/kotlin/com/chimali/fido2/
├── domain/
│   ├── usecase/
│   │   ├── AuthenticateCredentialUseCase.kt   # [NEW] — the use case class
│   │   ├── DeleteCredentialUseCase.kt         # (reference pattern)
│   │   ├── GetAssertionUseCase.kt             # (reference pattern)
│   │   └── ... (10 more existing use cases)
│   ├── service/
│   │   ├── Fido2Service.kt                    # [UNCHANGED] — interface stays the same
│   │   └── impl/
│   │       └── Fido2ServiceImpl.kt            # [MODIFY] — inject + delegate to use case
│   └── repository/
│       └── Fido2Repository.kt                 # [UNCHANGED]
└── di/
    └── Fido2Module.kt                         # [UNCHANGED] — @ComponentScan auto-discovers
```

**Structure Decision**: All changes are within the existing `feature/fido2` module. The `@ComponentScan("com.chimali.fido2")` in `Fido2Module` auto-discovers `@Factory` annotated classes, so no DI module changes are needed.

## Detailed Changes

### 1. [NEW] `AuthenticateCredentialUseCase.kt`

**Path**: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/usecase/AuthenticateCredentialUseCase.kt`

Create a new use case class following the exact pattern of [DeleteCredentialUseCase.kt](file:///d:/octav/source/repos/Chimali/feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/usecase/DeleteCredentialUseCase.kt):

- Package: `com.chimali.fido2.domain.usecase`
- Annotation: `@Factory` (Koin — scoped as transient, consistent with all other use cases)
- Constructor: inject `Fido2Repository` (not `CredentialRepository` — because the current code delegates to `Fido2Repository.authenticateCredential`)
- Method: `suspend operator fun invoke(rpId: RpId): Outcome<CredentialId, DomainError>`
- Body: delegates to `fido2Repository.authenticateCredential(rpId)` — identical to the current `Fido2ServiceImpl` line

### 2. [MODIFY] `Fido2ServiceImpl.kt`

**Path**: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/service/impl/Fido2ServiceImpl.kt`

Changes:
- Add `AuthenticateCredentialUseCase` as a constructor parameter (alongside existing `registerCredentialUseCase`)
- Update `authenticateWithCredential` to delegate to the new use case: `authenticateCredentialUseCase(rpId)` instead of `fido2Repository.authenticateCredential(rpId)`
- Update KDoc to reference the new use case
- The `fido2Repository` dependency remains — it is still used by `registerNewCredential`, `getAllCredentials`, and `deleteCredential`

### 3. [UNCHANGED] Files

| File | Reason |
|------|--------|
| `Fido2Service.kt` | Interface contract unchanged |
| `Fido2Repository.kt` | Repository interface unchanged |
| `Fido2RepositoryImpl.kt` | Implementation unchanged |
| `Fido2Module.kt` | `@ComponentScan` auto-discovers the new `@Factory` class |

## Complexity Tracking

No constitution violations. No complexity justifications needed.

## Verification Plan

1. **Compile check**: `gradlew :feature:fido2:compileDebugKotlin` — ensures the new class compiles and DI wiring is correct
2. **Existing test suite**: `gradlew :feature:fido2:testDebugUnitTest` — confirms zero behavioral regression
3. **Code inspection**: Verify `Fido2ServiceImpl.authenticateWithCredential` delegates to the use case, not directly to the repository
4. **Detekt/Ktlint**: `gradlew :feature:fido2:detekt :feature:fido2:ktlintCheck` — ensures coding standards compliance

# Implementation Plan: Functional Exception Handling

**Branch**: `[029-functional-exception-handling]` | **Date**: 2026-04-29 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/029-functional-exception-handling/spec.md`

## Summary

Enforce specific exception handling by eliminating `@Suppress("TooGenericExceptionCaught")` and migrating the codebase to a functional error handling paradigm using a custom `DataResult<D, E>` and `DomainError`. This ensures errors are logged at the exact boundary they occur (via Kermit) and correctly propagated without generic `try-catch` flow control in the presentation layer.

## Technical Context

**Language/Version**: Kotlin (KMP)
**Primary Dependencies**: Coroutines, Kermit, Detekt
**Storage**: N/A
**Testing**: kotlin.test, JUnit 5, MockK
**Target Platform**: Android Native (KMP structure)
**Project Type**: Mobile app
**Performance Goals**: Avoid allocation overhead of excessive try-catch blocks; maintain 60 fps UI smoothness.
**Constraints**: Must strictly log errors at the boundary (e.g. Repository). Must accurately preserve `Throwable` causes for Crashlytics. Must accurately rethrow `CancellationException`.
**Scale/Scope**: Widespread refactor across existing repositories, ViewModels, and data sources.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **III. Uncompromising Architecture & Quality**: Passes. Refactoring removes Detekt suppressions and improves code quality (no generic exception catching).
- **IV. Performance & Reliability Excellence**: Passes. Using a functional wrapper avoids exception throwing overhead for standard flow control.

## Project Structure

### Documentation (this feature)

```text
specs/029-functional-exception-handling/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
# Project module updates
core/
└── common/
    └── src/commonMain/kotlin/com/chimali/core/common/result/
        ├── DataResult.kt
        └── DomainError.kt

feature/
└── [all feature modules]/
    ├── data/
    │   └── repository/     # Update data sources to catch precise exceptions and log at boundary
    └── presentation/
        └── viewmodel/      # Remove try-catch, handle DataResult with exhaustive when
```

**Structure Decision**: A new `result` package will be created in `core/common` to host `DataResult` and `DomainError`. Existing feature modules will be modified to use this pattern.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |

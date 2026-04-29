# Implementation Plan: Detekt Rules Upgrade and Enforcement

**Branch**: `028-detekt-rules-upgrade` | **Date**: 2026-04-29 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/028-detekt-rules-upgrade/spec.md`

## Summary

Upgrade the project's static analysis by implementing expert-level Detekt rules and thresholds. The approach involves updating `config/detekt/detekt.yml`, systematically removing all existing `@Suppress` annotations for these rules across all modules, and refactoring the codebase to resolve newly exposed violations without changing application logic.

## Technical Context

**Language/Version**: Kotlin 2.0+  
**Primary Dependencies**: Detekt, Android SDK  
**Storage**: N/A (Configuration and Source Code)  
**Testing**: JUnit 5, MockK, `kotlin.test`, Compose UI Testing  
**Target Platform**: Android (Minimum SDK 28)
**Project Type**: Android Native Application (Modularized)  
**Performance Goals**: Pass `local-ci.ps1` with 0 static analysis violations.  
**Constraints**: No business logic changes allowed; strictly structural/stylistic refactoring.  
**Scale/Scope**: Project-wide impact across all feature and core modules.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **III. Uncompromising Architecture & Quality**: ✅ PASS. Explicitly enforces mandatory Detekt rules and prohibits magic numbers.
- **VIII. Local CI/CD & Enforcement**: ✅ PASS. Ensures all code complies with quality gates before being committed, removing the reliance on suppressions.

## Project Structure

### Documentation (this feature)

```text
specs/028-detekt-rules-upgrade/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output (N/A for this task, but created for consistency)
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
config/
└── detekt/
    └── detekt.yml       # Detekt configuration update

feature/                 # All feature modules
└── **/src/**/*.kt       # Source code refactoring

core/                    # All core modules
└── **/src/**/*.kt       # Source code refactoring

tools/
└── local-ci.ps1         # CI verification
```

**Structure Decision**: Multi-module refactoring targeting the configuration in `config/` and source files across all modules.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |

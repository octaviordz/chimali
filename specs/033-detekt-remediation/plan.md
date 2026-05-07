# Implementation Plan: Detekt Rules Remediation

**Branch**: `lab/or/chimali` | **Date**: 2026-05-07 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/033-detekt-remediation/spec.md`

## Summary

The goal is to enhance code quality by enforcing Detekt rules in the `feature\fido2` module, specifically targeting the top 4 suppressed issues (`MagicNumber`, `ClassNaming`, `BooleanPropertyNaming`, and `SuspendFunWithFlowReturnType`). This refactoring will remove these suppressions from the baseline file while preserving all existing business logic.

## Technical Context

**Language/Version**: Kotlin Multiplatform (KMP)  
**Primary Dependencies**: Detekt (Static Analysis)  
**Storage**: N/A  
**Testing**: `kotlin.test`, JUnit 5  
**Target Platform**: Android (Minimum SDK 28) and KMP Shared logic  
**Project Type**: Android Native Application / KMP module  
**Performance Goals**: N/A (Refactoring must not negatively impact performance)  
**Constraints**: Zero changes to business logic; code must cleanly pass Detekt analysis without suppressions.  
**Scale/Scope**: Scope is limited to the `feature\fido2` module and its downstream consumers if public APIs are affected by renaming.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle III. Uncompromising Architecture & Quality**: This feature directly fulfills this principle, which states that static analysis via Detekt is mandatory and that "The use of 'magic numbers' is strictly prohibited; all numeric literals with domain significance must be extracted into meaningful named constants or enums to ensure maintainability and readability."
- **Principle VIII. Local CI/CD & Enforcement**: The changes must pass the `tools/local-ci.ps1` pipeline.

*Status*: PASS. The plan strictly adheres to and furthers the goals of the constitution.

## Project Structure

### Documentation (this feature)

```text
specs/033-detekt-remediation/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
feature/fido2/
├── src/
│   ├── main/kotlin/com/chimali/feature/fido2/
│   └── test/kotlin/com/chimali/feature/fido2/
└── detekt-baseline-main.xml
```

**Structure Decision**: Refactoring will be localized to the existing `feature/fido2` module structure. No new architectural components will be added.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*No violations identified.*

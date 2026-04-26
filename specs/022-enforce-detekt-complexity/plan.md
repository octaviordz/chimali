# Implementation Plan: Enforce Detekt Complexity Rules

**Branch**: `022-enforce-detekt-complexity` | **Date**: 2026-04-26 | **Spec**: [spec.md](../spec.md)
**Input**: Feature specification from `/specs/022-enforce-detekt-complexity/spec.md`

## Summary

Enforce the `CognitiveComplexMethod` Detekt rule with a threshold of 40 to ensure long-term codebase health. This requires updating the Detekt configuration in `config/detekt/detekt.yml` and refactoring specific methods across the `vault` and `fido2` features that currently exceed this threshold.

## Technical Context

**Language/Version**: Kotlin (KMP + Android)
**Primary Dependencies**: Detekt (Static Analysis), Jetpack Compose
**Storage**: N/A
**Testing**: JUnit 5, local-ci.ps1
**Target Platform**: Android, KMP
**Project Type**: Android/KMP App
**Performance Goals**: Minimal code comprehension overhead
**Constraints**: Zero business logic changes allowed
**Scale/Scope**: Refactoring ~5 specific UI/logic methods and 1 config file.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Security First**: Preserved (No logic changes).
- **II. Master Seed Architecture**: Preserved.
- **III. Uncompromising Architecture & Quality**: **Enhanced**. We are explicitly enforcing Detekt static analysis rules for code complexity to maintain high quality.
- **IV. Performance & Reliability Excellence**: Preserved.
- **V. Cross-Platform Utility**: Preserved.
- **VI. Inclusion & Universal Accessibility**: Preserved.
- **VII. Documentation Standards**: Adhered to via SpecKit.

## Project Structure

### Documentation (this feature)

```text
specs/022-enforce-detekt-complexity/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
└── quickstart.md        # Phase 1 output
```

### Source Code (repository root)

```text
config/
└── detekt/
    └── detekt.yml (Configuration update)

feature/
├── vault/
│   └── src/main/java/com/chimali/feature/vault/ui/
│       ├── PasswordDetailScreen.kt (Refactor)
│       └── components/LegibleSecretText.kt (Refactor)
└── fido2/
    └── src/androidMain/kotlin/com/chimali/fido2/
        ├── bluetooth/BluetoothHidDeviceWrapper.kt (Refactor)
        └── presentation/
            ├── management/CredentialListScreen.kt (Refactor)
            └── ui/DevelopmentToolsScreen.kt (Refactor)
```

**Structure Decision**: We will update the existing project structure in place, isolating changes to the Detekt config file and the specific files failing the new complexity rule.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

N/A

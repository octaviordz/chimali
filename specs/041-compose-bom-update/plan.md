# Implementation Plan: Compose BOM Update

**Branch**: `041-compose-bom-update` | **Date**: 2026-05-15 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/041-compose-bom-update/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command.

## Summary

Upgrade the Jetpack Compose Bill of Materials (BOM) from `2024.12.01` (Compose 1.7.3) to `2026.04.00` (Compose 1.11) to eliminate noisy `ClassNotFoundException` warnings during instrumented tests and ensure the project remains on a supported, current platform version.

## Technical Context

**Language/Version**: Kotlin 2.3.20 (Current)
**Primary Dependencies**: `androidx.compose:compose-bom`, Jetpack Compose libraries
**Storage**: N/A
**Testing**: AndroidJUnitRunner, Compose UI Testing
**Target Platform**: Android API 28 (Min) to API 35 (Target)
**Project Type**: Android Native Application (KMP Architecture)
**Performance Goals**: N/A
**Constraints**: Must maintain existing test stability with zero regressions.
**Scale/Scope**: Project-wide dependency update

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Security First**: PASS. UI dependency updates do not impact crypto.
- **III. Uncompromising Architecture**: PASS. Jetpack Compose usage remains aligned with the architecture.
- **IV. Performance & Reliability Excellence**: PASS. The update should retain or improve UI performance.
- **IX. Local CI/CD & Enforcement**: PASS. Passing `tools/local-ci.ps1` is a core requirement.
- **XI. Risk Management & Pragmatism**: PASS. Updating the BOM is the simplest sufficient solution to the log noise problem and reduces technical debt.

## Project Structure

### Documentation (this feature)

```text
specs/041-compose-bom-update/
├── plan.md              # This file
├── research.md          # Findings on Compose version mapping
└── tasks.md             # To be generated
```

*(Note: `data-model.md`, `quickstart.md`, and `contracts/` are omitted as they are not applicable for a pure dependency update).*

### Source Code (repository root)

```text
gradle/
└── libs.versions.toml   # Central dependency catalog to be updated
```

**Structure Decision**: The update happens entirely within the Gradle version catalog (`libs.versions.toml`). No new packages or modules are introduced.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*(No violations. Table intentionally left blank.)*

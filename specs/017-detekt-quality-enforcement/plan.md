# Implementation Plan: Detekt Quality Enforcement: MaxLineLength

**Branch**: `017-detekt-quality-enforcement` | **Date**: 2026-04-24 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/017-detekt-quality-enforcement/spec.md`

## Summary

The goal is to enforce the `MaxLineLength` detekt rule (120 characters) across all source sets, including test directories that are currently excluded. This will be achieved by removing the `excludes` property from the rule in `config/detekt/detekt.yml` and refactoring any existing violations in the test directories (`test`, `androidTest`, `commonTest`, etc.) without introducing logic or semantic changes.

## Technical Context

**Language/Version**: Kotlin (KMP)
**Primary Dependencies**: Detekt (Static Analysis), Ktlint, Jetpack Compose, Koin, SQLDelight
**Storage**: N/A (Configuration change)
**Testing**: JUnit 5, MockK, kotlin.test, Compose UI Testing
**Target Platform**: Android (SDK 28+), KMP
**Project Type**: Mobile App
**Performance Goals**: N/A (Build-time check)
**Constraints**: No logic or semantic changes permitted during refactoring.
**Scale/Scope**: Entire project, focusing on previously excluded test directories.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Condition | Status |
|-----------|-----------|--------|
| III. Architecture & Quality | Static analysis via Detekt must be enforced. | PASS (Improving enforcement) |
| III. Architecture & Quality | No 'magic numbers' prohibited. | PASS |
| VII. Documentation Standards | Requirement identifiers use mnemonic path format. | PASS |
| VIII. Local CI/CD | All commits must pass local-ci.ps1 (Detekt/Ktlint). | PASS (Hardening the gate) |

## Project Structure

### Documentation (this feature)

```text
specs/017-detekt-quality-enforcement/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output (Detekt Config Model)
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
config/
└── detekt/
    └── detekt.yml       # Primary configuration file to be modified

src/                     # Multi-module structure (common, android, ios)
├── androidMain/
├── commonMain/
├── commonTest/          # Target for refactoring
├── androidUnitTest/     # Target for refactoring
└── androidInstrumentedTest/ # Target for refactoring
```

**Structure Decision**: Standard KMP module structure is maintained. Primary changes are in `config/detekt/` and various `*Test/` source sets across modules.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |

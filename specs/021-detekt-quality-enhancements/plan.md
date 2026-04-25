# Implementation Plan: Detekt Quality Enhancements

**Branch**: `021-detekt-quality-enhancements` | **Date**: 2026-04-25 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/021-detekt-quality-enhancements/spec.md`

## Summary

The goal of this feature is to strengthen the project's static analysis by enforcing higher standards for logging, idiomatic Kotlin usage, and string constant management. We will enable and configure three key Detekt rules (`ForbiddenMethodCall`, `UnnecessaryLet`/`UseLet`, and `StringLiteralDuplication`) to automate code quality gates that currently rely on manual review.

## Technical Context

**Language/Version**: Kotlin 2.1.10  
**Primary Dependencies**: Detekt 1.23.8  
**Storage**: N/A  
**Testing**: JUnit 5, MockK (for any potential rule-testing if needed)  
**Target Platform**: Android (Min SDK 28) / Kotlin Multiplatform  
**Project Type**: Mobile Application Infrastructure  
**Performance Goals**: Negligible impact on Detekt execution time (< 5% increase)  
**Constraints**: Rules must not flag legitimate test-code debugging or highly volatile UI strings.  
**Scale/Scope**: Project-wide (all modules).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

1. **Principle III (Architecture & Quality)**: **PASS**. The feature directly implements the requirement for mandatory static analysis and prohibition of "magic" constants (extending it to strings).
2. **Principle VIII (Local CI/CD)**: **PASS**. These changes will automatically become part of the `tools/local-ci.ps1` gate, ensuring no code can be committed with these violations.

## Project Structure

### Documentation (this feature)

```text
specs/021-detekt-quality-enhancements/
├── plan.md              # This file
├── research.md          # Research and rule thresholds
├── data-model.md        # N/A (no data model changes)
├── quickstart.md        # Update for new linting rules
└── checklists/
    └── requirements.md  # Spec validation checklist
```

### Source Code (repository root)

```text
config/
└── detekt/
    └── detekt.yml       # Primary configuration file to be modified
```

**Structure Decision**: We are modifying the global configuration file `config/detekt/detekt.yml`. This file is already shared across all modules in the project.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |

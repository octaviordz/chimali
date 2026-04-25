# Implementation Plan: detekt-unused-code-enforcement

**Branch**: `019-detekt-unused-code-enforcement` | **Date**: 2026-04-24 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/019-detekt-unused-code-enforcement/spec.md`

## Summary

This feature enhances code quality by enforcing `UnusedPrivateMember` and `UnusedPrivateProperty` Detekt rules across the entire codebase, including test suites. It involves removing broad test-related excludes and replacing them with targeted excludes for standard generated code paths (`**/build/**`, `**/generated/**`), while establishing a strict policy for intentional unused code suppression via `@Suppress`.

## Technical Context

**Language/Version**: Kotlin 1.9+  
**Primary Dependencies**: Detekt (Tooling)  
**Storage**: N/A  
**Testing**: JUnit 5 (to verify rule enforcement in test files), `tools/local-ci.ps1`  
**Target Platform**: Android / KMP  
**Project Type**: Static Analysis Configuration  
**Performance Goals**: Minimal impact on Detekt analysis duration.  
**Constraints**: MUST exclude standard generated code to prevent build blocks from external generators.  
**Scale/Scope**: Project-wide enforcement affecting all production and test modules.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle III: Uncompromising Architecture & Quality**: This feature directly aligns with the mandate for mandatory static analysis via Detekt.
- **Principle VIII: Local CI/CD & Enforcement**: Enforcement will be automated via the `tools/local-ci.ps1` pipeline.
- **Gate 1 (Quality Enforcement)**: PASSED. Strengthening unused code detection reduces dead code and improves maintainability.
- **Gate 2 (Maintainability)**: PASSED. Moving away from broad excludes ensures consistent standards while respecting technical boundaries (generated code).

## Project Structure

### Documentation (this feature)

```text
specs/019-detekt-unused-code-enforcement/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── checklists/          # Quality validation
```

### Source Code (repository root)

```text
config/
└── detekt/
    └── detekt.yml       # Centralized Detekt configuration

# Applied project-wide across all modules
feature/*/src/
├── commonMain/
├── androidMain/
├── commonTest/
└── androidUnitTest/
```

**Structure Decision**: Centralized configuration change in `config/detekt/detekt.yml` to apply project-wide. Verification will involve creating temporary violations in various modules to ensure the gate is active.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |

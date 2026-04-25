# Implementation Plan: Detekt Rule Hardening

**Branch**: `020-detekt-rule-hardening` | **Date**: 2026-04-24 | **Spec**: [spec.md](file:///D:/octav/source/repos/Chimali/specs/020-detekt-rule-hardening/spec.md)
**Input**: Feature specification from `specs/020-detekt-rule-hardening/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

We will enhance project-wide code quality by activating and hardening six key Detekt rules: `WildcardImport`, `UnusedImports`, `NewLineAtEndOfFile`, `UnsafeCallOnNullableType`, `LateinitUsage`, and `EmptyDefaultConstructor`. This involves removing all existing rule-specific exclusions (including those for legacy modules like `feature:vault`). For the `LateinitUsage` rule, an exception will be granted for test source sets to accommodate idiomatic test fixtures (e.g., in `kotlin.test` and MockK). Refactoring will address approximately 150+ violations project-wide. Generated code and potentially high-debt `.kts` build scripts will be excluded via global configuration.

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Kotlin 2.1.0 / Detekt 1.23.7
**Primary Dependencies**: Detekt, Gradle
**Storage**: N/A
**Testing**: detekt check, tools/local-ci.ps1
**Target Platform**: Android / Kotlin Multiplatform
**Project Type**: Static Analysis Configuration
**Performance Goals**: Build time impact < 5s for detekt check
**Constraints**: Zero violations allowed in non-generated code
**Scale/Scope**: affects all modules (~10+ modules)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Gate 1: Uncompromising Architecture & Quality (Principle III)
- **Check**: All Kotlin source files must pass Detekt static analysis.
- **Verification**: Run `./gradlew detekt` and ensure zero failures.

### Gate 2: Local CI/CD (Principle VIII)
- **Check**: Changes must not break the local CI pipeline.
- **Verification**: Run `tools/local-ci.ps1` and ensure it completes successfully.

## Project Structure

### Documentation (this feature)

```text
specs/020-detekt-rule-hardening/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
config/detekt/
└── detekt.yml           # Core configuration file to be modified

app/                     # Refactor UI components
core/                    # Refactor security tests and core utilities
feature/                 # Refactor vault and fido2 modules (highest impact)
```

**Structure Decision**: This is a cross-cutting concern affecting the entire repository. The primary configuration change is in `config/detekt/detekt.yml`, with secondary refactoring across all source modules.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| N/A | N/A | N/A |

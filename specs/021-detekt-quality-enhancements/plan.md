# Implementation Plan: Detekt Quality Enhancements

**Branch**: `021-detekt-quality-enhancements` | **Date**: 2026-04-25 | **Spec**: [/specs/021-detekt-quality-enhancements/spec.md](/specs/021-detekt-quality-enhancements/spec.md)

**Input**: Feature specification from `/specs/021-detekt-quality-enhancements/spec.md`

## Summary
The goal of this feature is to harden the project's static analysis by enforcing strict Detekt rules across all feature modules. This includes removing global exclusions for `feature:vault` and `feature:fido2`, enforcing logging discipline (blocking `println` and `android.util.Log`), managing string literal duplication, and optimizing collection processing via lazy Sequences.

## Technical Context

**Language/Version**: Kotlin 2.x  
**Primary Dependencies**: Detekt 1.23.x, Kermit (Logging)  
**Storage**: N/A (Configuration-only feature targeting `config/detekt/detekt.yml`)  
**Testing**: `tools/local-ci.ps1` (Detekt task), manual verification of rule violations in feature modules.  
**Target Platform**: Android (KMP ready)  
**Project Type**: Build Infrastructure / Static Analysis Configuration  
**Performance Goals**: Negligible build-time impact; enforcement of lazily-evaluated Sequences for 10k+ items.  
**Constraints**: Global removal of module-level path exclusions for production source sets.  
**Scale/Scope**: Project-wide configuration impacting `feature:vault`, `feature:fido2`, and all future modules.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle III (Architecture & Quality)**: This feature is a direct implementation of the mandate for "mandatory" static analysis via Detekt.
- **Principle III (Magic Numbers)**: Enforces `StringLiteralDuplication` to prevent magic strings.
- **Principle IV (Performance)**: Enforces `UseSequence` to ensure the 10,000+ item scalability target is met without memory exhaustion.
- **Principle VIII (Local CI)**: Directly strengthens the quality gate described in the development workflow.

## Project Structure

### Documentation (this feature)

```text
specs/021-detekt-quality-enhancements/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
config/
└── detekt/
    └── detekt.yml       # Primary configuration file to be modified
```

**Structure Decision**: This is a configuration-focused feature. Modifications are localized to the central `config/detekt/detekt.yml` file and potential targeted refactors in `feature:vault` and `feature:fido2` to satisfy newly enforced rules.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |

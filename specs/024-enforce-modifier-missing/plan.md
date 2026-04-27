# Implementation Plan: Enforce Modifier Missing

**Branch**: `024-enforce-modifier-missing` | **Date**: 2026-04-27 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/024-enforce-modifier-missing/spec.md`

## Summary

Enhance code quality by enforcing the compose-rule `ModifierMissing`, systematically removing all `@Suppress("ModifierMissing")` annotations, and ensuring all relevant composables accept and apply a `modifier` parameter correctly. The goal is to enforce static analysis best practices without altering underlying business or UI logic.

## Technical Context

**Language/Version**: Kotlin (Android)  
**Primary Dependencies**: Jetpack Compose, Detekt (Compose Rules), Android Lint  
**Storage**: N/A  
**Testing**: Local CI pipeline (`tools/local-ci.ps1`), Detekt  
**Target Platform**: Android Native Application  
**Project Type**: Android Mobile App (Compose UI)  
**Performance Goals**: Zero degradation  
**Constraints**: Pure structural refactor, no logic changes  
**Scale/Scope**: All internal/public UI composables across all modules  

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **III. Uncompromising Architecture & Quality**: Static analysis via Detekt is mandatory to enforce coding standards. This task directly fulfills this by strictly enforcing the `ModifierMissing` rule.
- [x] **VIII. Local CI/CD & Enforcement**: Local CI must pass without static analysis violations. Refactoring ensures CI stability.

## Project Structure

### Documentation (this feature)

```text
specs/024-enforce-modifier-missing/
├── plan.md              
├── research.md          
├── data-model.md        
├── quickstart.md        
└── tasks.md             
```

### Source Code (repository root)

```text
# General Android Module Structure (applied across all UI modules)
*/src/main/java/**/ui/
└── components/
    └── [Refactored Composables]
```

**Structure Decision**: No new directories or architecture changes. Existing Composables across all UI modules will be updated in place to comply with Detekt requirements.

## Complexity Tracking

*(No constitution violations, so no complexity justification is required.)*

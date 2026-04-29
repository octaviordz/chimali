# Implementation Plan: Standardize Preview Visibility

**Branch**: `027-enforce-preview-public` | **Date**: 2026-04-29 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/027-enforce-preview-public/spec.md`

## Summary

Standardize the visibility of Compose Preview functions across the `feature:vault` module by removing lint suppressions and making previews non-public. This ensures a clean public API surface and enforces project-wide static analysis rules.

## Technical Context

**Language/Version**: Kotlin 1.9+  
**Primary Dependencies**: Jetpack Compose, Detekt, Android Lint  
**Storage**: N/A  
**Testing**: Compose UI Testing  
**Target Platform**: Android (SDK 28+)
**Project Type**: Mobile app (multi-module)  
**Performance Goals**: 60 FPS (ensure previews remain functional for development)  
**Constraints**: No logic changes, 0 visibility bypasses  
**Scale/Scope**: 8 files in `feature:vault`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

1. **Principle III Compliance**: Does the plan ensure zero tolerance for static analysis suppressions without justification? **YES**.
2. **Principle VII Compliance**: Are documentation standards maintained for visibility changes? **YES**.

## Project Structure

### Documentation (this feature)

```text
specs/027-enforce-preview-public/
├── plan.md              # This file
├── research.md          # Research on impacted files and visibility strategy
├── data-model.md        # N/A (no data changes)
├── quickstart.md        # N/A (code quality task)
└── tasks.md             # To be generated
```

### Source Code (repository root)

```text
feature/vault/src/main/java/com/chimali/feature/vault/ui/
├── CreditCardDetailScreen.kt
├── CreditCardEntryScreen.kt
├── LabelManagerScreen.kt
├── PasswordDetailScreen.kt
├── PasswordEntryScreen.kt
├── SecureNoteDetailScreen.kt
├── SecureNoteEntryScreen.kt
└── VaultListScreen.kt
```

**Structure Decision**: No new directories or files will be created in the source code. Changes are limited to modifying visibility modifiers and removing annotations in existing UI files.

## Phase 0: Research (Completed)

See [research.md](./research.md) for details. Identified 8 files requiring visibility updates and suppression removals.

## Phase 1: Design

- **Visibility Strategy**: All identified public previews will be changed to `private` (unless cross-file access is required, in which case `internal` will be used).
- **Annotation Cleanup**: `@Suppress("PreviewPublic")` and `@Suppress("ForbiddenComment")` will be removed.
- **Comment Cleanup**: Associated TODOs will be deleted.
- **Verification**: Run `local-ci.ps1` to ensure no lint regressions.

# Implementation Plan: KtLint Format Polish & Stabilization

**Branch**: `014-ktlint-format-polish` | **Date**: 2026-04-23 | **Spec**: [spec.md](file:///d:/octav/source/repos/Chimali/specs/014-ktlint-format-polish/spec.md)

## Summary
The goal is to resolve style violations blocking the KMP stabilization commit and to enforce project-wide coding standards as mandated by the Chimali Constitution. We will centralize `ktlint` configuration, introduce an `.editorconfig` for explicit rule definition, and apply formatting across all modules while strictly preserving code semantics and comments.

## Technical Context

**Language/Version**: Kotlin (KMP)  
**Primary Dependencies**: `org.jlleitschuh.gradle.ktlint` (v12.1.0)  
**Storage**: N/A  
**Testing**: `./gradlew ktlintCheck`, `./gradlew test`, `tools/local-ci.ps1`  
**Target Platform**: Android, JVM (Common)  
**Project Type**: Build Infrastructure & Quality Tooling  
**Performance Goals**: `< 60s` for project-wide formatting  
**Constraints**: 
- NO semantic changes to code logic.
- 100% preservation of all comments and KDocs.
- Support for KMP source sets (`androidMain`, `commonMain`, etc.).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Justification |
| :--- | :--- | :--- |
| **III. Uncompromising Architecture & Quality** | ✅ Pass | Enforcing mandatory `ktlint` static analysis across all modules. |
| **VII. Documentation Standards** | ✅ Pass | Updating specifications and plans following mnemonic path format. |
| **VIII. Local CI/CD & Enforcement** | ✅ Pass | Ensuring pre-commit hooks pass by resolving existing violations. |

## Proposed Changes

### Build Configuration

#### [MODIFY] [build.gradle.kts](file:///d:/octav/source/repos/Chimali/build.gradle.kts)
- Apply `ktlint` plugin to all subprojects via `subprojects { ... }` block.
- Add central `ktlint` configuration block to exclude generated files.

#### [MODIFY] [feature/fido2/build.gradle.kts](file:///d:/octav/source/repos/Chimali/feature/fido2/build.gradle.kts)
- Remove local `ktlint` plugin application (now handled at root).

### Style Standards

#### [NEW] [.editorconfig](file:///d:/octav/source/repos/Chimali/.editorconfig)
- Define standard Kotlin rules (indentation, max line length).
- Define import ordering rules to resolve current test failures.

### Quality Gates

#### [MODIFY] [tools/local-ci.ps1](file:///d:/octav/source/repos/Chimali/tools/local-ci.ps1)
- Ensure `ktlintCheck` is running against all modules.

## Project Structure

### Documentation (this feature)

```text
specs/014-ktlint-format-polish/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Research findings (ktlint status, .editorconfig absence)
├── data-model.md        # Style guide rule definitions
├── quickstart.md        # How to run formatting and check tasks
└── checklists/
    └── requirements.md  # Spec quality checklist
```

## Verification Plan

### Automated Tests
- Run `./gradlew ktlintFormat` to fix violations.
- Run `./gradlew ktlintCheck` to verify compliance.
- Run `./gradlew test` across all modules to ensure NO semantic regressions.
- Run `tools/local-ci.ps1 -SkipClean` to verify pre-commit readiness.

### Manual Verification
- Audit at least 5 files with complex comments/KDocs to ensure they are preserved correctly.
- Verify that `CredentialListScreenTest.kt` imports are correctly ordered.

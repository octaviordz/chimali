# Implementation Plan: ViewModel Forwarding Cleanup

**Branch**: `feature/viewmodel-forwarding-cleanup` | **Date**: April 27, 2026 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/025-viewmodel-forwarding-cleanup/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Remove all `@Suppress("ViewModelForwarding")` annotations from the codebase and refactor Compose components to follow proper ViewModel forwarding patterns. The goal is to enforce code quality standards while maintaining all existing functionality without behavioral changes.

## Technical Context

**Language/Version**: Kotlin 1.9+ with Jetpack Compose  
**Primary Dependencies**: Detekt with Compose rules, Koin DI, Jetpack Compose  
**Storage**: N/A (code quality enhancement only)  
**Testing**: JUnit 5, Compose UI Testing, existing test suite  
**Target Platform**: Android (Minimum SDK 28)  
**Project Type**: Mobile application with Kotlin Multiplatform module structure  
**Performance Goals**: No performance degradation, maintain existing startup times and UI smoothness  
**Constraints**: No logic changes, maintain 100% backward compatibility  
**Scale/Scope**: Single codebase cleanup affecting Compose UI components across multiple modules

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Constitution Compliance Analysis

**✅ Principle III: Uncompromising Architecture & Quality**
- This feature directly supports the mandatory static analysis via Detekt
- Enforces coding standards as required by the constitution
- Maintains clean architecture with proper ViewModel forwarding patterns
- No magic numbers introduced (code quality enhancement only)

**✅ Principle IV: Performance & Reliability Excellence**
- No performance degradation allowed (explicit constraint)
- Must maintain existing startup times and UI smoothness
- No memory leaks or battery impact changes

**✅ Principle VII: Documentation Standards**
- Following IEEE 830 SRS principles in specification
- Stable requirement identifiers used (FR-001 format)
- Living documents maintained close to code

**✅ Technical Constraints Met**
- Android Native Application (Minimum SDK 28) ✅
- Kotlin language maintained ✅
- KMP module structure preserved ✅
- Jetpack Compose (Material Design 3) ✅
- No changes to storage or hardware integration ✅

**✅ Development Workflow & Testing**
- TDD approach maintained (existing tests must pass)
- All static analysis checks must pass
- No bypassing of Local CI pipeline

**GATE STATUS: PASSED** - No constitution violations identified

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
app/
├── src/main/
│   ├── java/com/chimali/app/
│   └── AndroidManifest.xml
├── build.gradle.kts

feature/
├── authenticator/
│   ├── src/
│   │   ├── androidMain/kotlin/com/chimali/feature/authenticator/
│   │   └── commonMain/kotlin/com/chimali/feature/authenticator/
│   └── build.gradle.kts
├── editor/
│   └── [similar structure]
├── fido2/
│   ├── src/androidMain/kotlin/com/chimali/fido2/presentation/management/
│   │   └── CredentialListScreen.kt  # Contains @Suppress("ViewModelForwarding")
│   └── build.gradle.kts
└── vault/
    ├── src/main/java/com/chimali/feature/vault/ui/
    │   ├── CreditCardDetailScreen.kt
    │   ├── CreditCardEntryScreen.kt
    │   ├── LabelManagerScreen.kt
    │   ├── PasswordDetailScreen.kt
    │   ├── PasswordEntryScreen.kt
    │   ├── SecureNoteDetailScreen.kt
    │   ├── SecureNoteEntryScreen.kt
    │   └── VaultListScreen.kt
    └── build.gradle.kts

core/
├── bluetooth/
├── common/
├── crdt/
├── data/
├── database/
├── domain/
├── security/
└── ui/

config/
├── detekt/
│   └── detekt.yml  # Contains ViewModelForwarding rule configuration
└── ktlint/

tools/
└── local-ci.ps1  # Must pass before commits
```

**Structure Decision**: Android mobile application with modular feature-based architecture. The ViewModel forwarding cleanup will primarily affect Compose UI screens in the `feature/` modules, particularly `feature/fido2` and `feature/vault` modules where @Suppress annotations are currently used.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |

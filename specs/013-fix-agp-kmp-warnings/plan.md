# Implementation Plan: Fix AGP and KMP Build Warnings

**Branch**: `013-fix-agp-kmp-warnings` | **Date**: 2026-04-23 | **Spec**: [spec.md](file:///d:/octav/source/repos/Chimali/specs/013-fix-agp-kmp-warnings/spec.md)
**Input**: Feature specification from `specs/013-fix-agp-kmp-warnings/spec.md`

## Summary

Migrate the project's build configuration to be fully compatible with AGP 9.2.0. This involves replacing the deprecated `com.android.library` plugin with `com.android.kotlin.multiplatform.library` in KMP modules, removing the redundant `org.jetbrains.kotlin.android` plugin in `:feature:vault`, and cleaning up legacy flags in `gradle.properties`. A comprehensive dependency analysis will precede the changes to ensure a stable migration.

## Technical Context

**Language/Version**: Kotlin 2.3.20  
**Primary Dependencies**: Android Gradle Plugin (AGP) 9.2.0, Kotlin Multiplatform Plugin 2.3.20  
**Storage**: N/A (Build configuration change)  
**Testing**: kotlin.test, JUnit 5, MockK (verification via `./gradlew build` and `./tools/local-ci.ps1`)  
**Target Platform**: Android (Min SDK 28), JVM, Desktop  
**Project Type**: Multi-module KMP Application  
**Performance Goals**: N/A (Maintain build performance)  
**Constraints**: Must strictly adhere to AGP 9.2.0+ recommended patterns.  
**Scale/Scope**: Affects all KMP modules and core project configuration.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

1. **Modularization (Principle III)**: Migration must preserve the existing feature-by-module structure.
2. **KMP Compatibility (Principle III)**: Using `com.android.kotlin.multiplatform.library` is specifically designed for KMP, aligning with the "KMP ready" requirement.
3. **Local CI (Principle VIII)**: All changes MUST be verified by the local CI pipeline before completion.

## Project Structure

### Documentation (this feature)

```text
specs/013-fix-agp-kmp-warnings/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output (N/A for this task)
├── quickstart.md        # Phase 1 output (N/A for this task)
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
build-logic/             # Convention plugins
feature/
├── vault/               # Affected by org.jetbrains.kotlin.android removal
├── fido2/               # Affected by com.android.library migration
└── ...
core/
├── domain/              # KMP modules
├── data/
└── ...
gradle.properties        # Legacy flag removal
gradle/libs.versions.toml # Plugin definitions
```

**Structure Decision**: The changes are distributed across multiple modules. Migration will be performed systematically across all identified KMP modules.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |

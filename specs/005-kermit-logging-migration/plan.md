# Implementation Plan: Kermit Logging Migration

**Branch**: `005-kermit-logging-migration` | **Date**: 2026-04-19 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/005-kermit-logging-migration/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Migrate the project's logging framework from the Android-specific `Timber` to the Kotlin Multiplatform library `Kermit`. This will be achieved by adding `Kermit` to `libs.versions.toml`, exposing it via `core:common`, implementing a custom `LocalCrashReportingLogWriter` to replace the existing `Timber.Tree`, and replacing all existing `Timber` calls across `app`, `core:security`, and `feature:fido2` with `Logger` calls.

## Technical Context

**Language/Version**: Kotlin 2.1+
**Primary Dependencies**: Kermit 2.x
**Storage**: Local App Files (rotating 5MB log file `fido2_crash_log.txt`)
**Testing**: kotlin.test (for commonMain) / JUnit5 (legacy/androidMain)
**Target Platform**: Android (primary for now), iOS (future)
**Project Type**: Mobile Application (KMP)
**Performance Goals**: <50ms startup impact for logging init
**Constraints**: Privacy scrubbing must run before any disk I/O; No cloud sync.
**Scale/Scope**: ~30-50 files requiring import replacements.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Constitution §I (Security First)**: PASS - Privacy Log Scrubber integration is explicitly maintained in the `LogWriter` design to mask sensitive data before local persistence.
- **Constitution §III (Uncompromising Architecture & Quality)**: PASS - Migrating to Kermit is required for KMP readiness.
- **Constitution §IV (Performance & Reliability Excellence)**: PASS - Local sink rotation prevents storage exhaustion.

## Project Structure

### Documentation (this feature)

```text
specs/005-kermit-logging-migration/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
# Project layout for this migration:
build.gradle.kts (root)
gradle/libs.versions.toml

core/common/
└── build.gradle.kts

feature/fido2/
└── src/
    └── main/kotlin/com/chimali/fido2/
        ├── Fido2Initializer.kt
        └── util/logging/
            ├── LocalCrashReportingLogWriter.kt [NEW]
            ├── LocalCrashReportingTree.kt [DELETE]
            └── PrivacyLogScrubber.kt [MODIFY]
```

**Structure Decision**: The implementation will follow the existing module structure, focusing on updates to `build.gradle.kts` files and replacing the Timber implementation in `feature:fido2`'s logging utilities.

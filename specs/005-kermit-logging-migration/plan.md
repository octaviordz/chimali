# Implementation Plan: Kermit Logging Migration

**Branch**: `005-kermit-logging-migration` | **Date**: 2026-04-20 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/005-kermit-logging-migration/spec.md`

## Summary

Migrate the project's logging infrastructure from Timber to Kermit to support Kotlin Multiplatform (KMP). Crucially, the `LocalCrashReportingLogWriter` must be refactored to be fully KMP compatible, replacing Android/JVM specific I/O and Date mechanisms with `okio` and `kotlinx-datetime`, allowing it to be used in `commonMain`.

## Technical Context

**Language/Version**: Kotlin 2.1+  
**Primary Dependencies**: Kermit 2.x, Okio, Kotlinx-Datetime  
**Storage**: Local file system (Okio `FileSystem.SYSTEM`)  
**Testing**: Kotlin multiplatform testing (`kotlin.test`)  
**Target Platform**: Android, iOS, Desktop (KMP)
**Project Type**: Mobile Application / KMP Library  
**Performance Goals**: <50ms cold startup impact, non-blocking I/O or fast synchronous writes  
**Constraints**: Thread-safe file writing, 5MB log rotation limit, Privacy scrubbing mandatory  
**Scale/Scope**: All modules in the `Chimali` project  

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Security First**: PrivacyLogScrubber ensures sensitive data is not persisted to disk. Local-only crash logs adhere to Zero-Trust.
- **III. Uncompromising Architecture**: Refactoring to KMP aligns perfectly with the target architecture.
- **IV. Performance**: File writing must not block the main thread excessively; thread safety must be efficient.

**Result**: PASS

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
feature/fido2/src/
├── commonMain/
│   └── kotlin/com/chimali/fido2/util/logging/
│       ├── LocalCrashReportingLogWriter.kt
│       ├── PrivacyLogScrubber.kt
│       └── LogDirectoryProvider.kt (expect/interface)
├── androidMain/
│   └── kotlin/com/chimali/fido2/util/logging/
│       └── AndroidLogDirectoryProvider.kt
└── iosMain/
    └── kotlin/com/chimali/fido2/util/logging/
        └── IosLogDirectoryProvider.kt
```

**Structure Decision**: The logging utilities will be moved to the `commonMain` source set within the FIDO2 feature (or a shared core logging module if appropriate), utilizing `expect`/`actual` or interface injection for platform-specific directory paths.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Platform-specific I/O logic in common code | We need file writing capabilities across all platforms | Using Android `Context` directly violates KMP and limits the code to Android only. Using `okio` and `expect`/`actual` is the standard KMP approach. |

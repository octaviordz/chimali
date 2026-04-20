# Implementation Plan: KMP Logging Writer

**Branch**: `006-kmp-logging-writer` | **Date**: 2026-04-20 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/006-kmp-logging-writer/spec.md`

## Summary

Refactor the `LocalCrashReportingLogWriter` from being an Android-specific implementation to a fully Kotlin Multiplatform (KMP) compatible module. This involves migrating file I/O to `okio`, migrating timestamp generation to `kotlinx-datetime`, and abstracting the log directory resolution so the writer can be used within `commonMain` to support all KMP targets.

## Technical Context

**Language/Version**: Kotlin 2.1+  
**Primary Dependencies**: `co.touchlab:kermit:2.x`, `com.squareup.okio:okio`, `org.jetbrains.kotlinx:kotlinx-datetime`  
**Storage**: Local file system (Okio `FileSystem.SYSTEM`)  
**Testing**: `kotlin.test`, Kermit testing utilities  
**Target Platform**: Android, iOS, Desktop (KMP)
**Project Type**: Mobile Application / KMP Library  
**Performance Goals**: Fast synchronous file writes; non-blocking log rotation  
**Constraints**: 5MB rotating log file, strict privacy scrubbing before write, thread safety  
**Scale/Scope**: Impacts all modules utilizing the shared logging infrastructure  

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Security First**: Privacy logging scrubbing ensures Zero-Trust persistence of logs.
- **III. Uncompromising Architecture**: Refactoring to KMP enables multiplatform capabilities, adhering to architecture standards.
- **IV. Performance**: I/O via Okio must be optimized for minimal latency impact on the main thread.

**Result**: PASS

## Project Structure

### Documentation (this feature)

```text
specs/006-kmp-logging-writer/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── tasks.md             # To be generated
```

### Source Code (repository root)

```text
feature/fido2/src/
├── commonMain/
│   └── kotlin/com/chimali/fido2/util/logging/
│       ├── LocalCrashReportingLogWriter.kt
│       ├── LogDirectoryProvider.kt
│       ├── PrivacyLogScrubber.kt
│       └── PlatformLock.kt (expect)
├── androidMain/
│   └── kotlin/com/chimali/fido2/util/logging/
│       └── PlatformLock.kt (actual using ReentrantLock)
└── iosMain/
    └── kotlin/com/chimali/fido2/util/logging/
        └── PlatformLock.kt (actual using NSRecursiveLock)
```

**Structure Decision**: The logging components will reside in `feature/fido2/src/commonMain/kotlin/...`. An injected `LogDirectoryProvider` will handle the directory resolution, while an `expect`/`actual` `PlatformLock` will handle synchronous thread-safety for file writes across platforms.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `expect`/`actual` PlatformLock | Kermit `LogWriter` is a synchronous API, requiring synchronous locks for file safety | `Mutex` requires `runBlocking` which can cause deadlocks or is unsupported on some KMP targets. |

# Feature Specification: KMP Logging Writer

**Feature Branch**: `006-kmp-logging-writer`  
**Created**: 2026-04-20  
**Status**: Draft  
**Input**: User description: "LocalCrashReportingLogWriter must be refactored to be fully KMP compatible, replacing Android/JVM specific I/O and Date mechanisms with okio and kotlinx-datetime, allowing it to be used in commonMain."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - KMP Crash Logger Usage (Priority: P1)

As a developer, I want to use `LocalCrashReportingLogWriter` from any KMP module (commonMain) so that crash reports are logged reliably regardless of the target platform (Android, iOS, Desktop).

**Why this priority**: KMP compatibility is required to share logging logic across all platforms and adhere to the project's multiplatform architecture goals.

**Independent Test**: Can be fully tested by triggering a logged event from common code and verifying that the log is written to the expected file path using the multiplatform Okio file system.

**Acceptance Scenarios**:

1. **Given** the application is running, **When** a critical error occurs and is logged, **Then** the crash log must be persisted to the platform-specific local log directory.
2. **Given** the log writer is used in a commonMain file, **When** the code is compiled for an iOS target, **Then** the compilation must succeed without missing dependency errors.

---

### Edge Cases

- What happens when the disk is full or the log directory is not writable? The system must fall back gracefully or drop the log without crashing the app.
- How does system handle concurrent log writes? Thread safety must be ensured in a multiplatform context.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST replace all `java.io` and `android.*` dependencies in `LocalCrashReportingLogWriter` with `com.squareup.okio` equivalents.
- **FR-002**: System MUST replace all `java.util.Date` and `java.text.SimpleDateFormat` dependencies with `kotlinx-datetime` equivalents.
- **FR-003**: System MUST abstract the log directory path resolution using `expect`/`actual` or an injected provider interface, so it works across platforms.
- **FR-004**: System MUST allow `LocalCrashReportingLogWriter` to be placed in and imported from a `commonMain` source set.

### Key Entities *(include if feature involves data)*

- **LocalCrashReportingLogWriter**: The main class responsible for persisting log entries.
- **LogDirectoryProvider**: An abstraction for resolving the platform-specific directory where logs should be stored.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `LocalCrashReportingLogWriter` compiles successfully across all configured KMP targets (Android, iOS, etc.).
- **SC-002**: Zero references to `java.*` or `android.*` exist within `LocalCrashReportingLogWriter`.
- **SC-003**: Logs are correctly written to disk and timestamped on at least two different platforms (e.g., Android and iOS) during integration tests.

## Assumptions

- `okio` and `kotlinx-datetime` libraries are already available or can be added to the project without version conflicts.
- The 5MB log rotation and privacy scrubbing logic from the existing Android implementation must be preserved.

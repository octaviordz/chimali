# Feature Specification: Kermit Logging Migration

**Feature Branch**: `005-kermit-logging-migration`  
**Created**: 2026-04-19  
**Status**: Completed  
**Input**: User description: "Plan the migration from Timber to Kermit for logging."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Multiplatform Logging Support (Priority: P1)

As a developer, I want to use a unified logging API in `commonMain` so that I can log platform-agnostic logic once and have it work on both Android and future iOS targets.

**Why this priority**: Fundamental requirement for the project's transition to a full Kotlin Multiplatform (KMP) architecture (Constitution §III).

**Independent Test**: Can be tested by adding a log statement in a `commonMain` class and verifying it appears in the Android Logcat.

**Acceptance Scenarios**:

1. **Given** a class in `commonMain`, **When** I use the `Logger` API to log a message, **Then** the message MUST appear in the platform-specific log output (Logcat for Android).
2. **Given** the application starts, **When** the logging system is initialized, **Then** it MUST not crash or cause performance degradation.

---

### User Story 2 - Privacy-Safe Local File Logging (Priority: P1)

As a security-conscious application, I want all logs above a certain priority to be written to a rotating local file for post-crash analysis, while ensuring sensitive data is masked.

**Why this priority**: Essential for troubleshooting in production while maintaining the "Zero-Trust Local-First" principle (Constitution §I).

**Independent Test**: Can be fully tested by performing actions in the app and verifying the contents of `fido2_crash_log.txt` on the device storage.

**Acceptance Scenarios**:

1. **Given** a log message containing potentially sensitive data (e.g., private keys or mnemonics), **When** it is written to the local file, **Then** the sensitive data MUST be scrubbed/masked.
2. **Given** the log file reaches 5MB in size, **When** a new log entry is added, **Then** the system MUST rotate the log file, preserving the previous log as a backup.

---

### Edge Cases

- **What happens when the disk is full?** The logging system must fail gracefully without crashing the application.
- **How does the system handle rapid logging during stress tests?** The file writer must be thread-safe to prevent data corruption or application hangs.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST use Kermit (`co.touchlab.kermit`) as the primary logging framework across all modules.
- **FR-002**: System MUST implement a custom Kermit `LogWriter` that mirrors the functionality of the legacy `LocalCrashReportingTree`.
- **FR-003**: System MUST provide a mechanism to mask sensitive cryptographic and personal data in logs (Privacy Scrubbing).
- **FR-004**: System MUST maintain a 5MB rotating log file sink on the local device.
- **FR-005**: All existing `Timber` imports and calls MUST be replaced with equivalent Kermit `Logger` calls.

### Key Entities *(include if feature involves data)*

- **Logger**: The multiplatform entry point for generating log entries.
- **LogWriter**: The platform-specific or shared implementation that handles log output (e.g., Logcat, File).
- **PrivacyScrubber**: The utility responsible for identifying and masking sensitive patterns in log messages.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero (0) occurrences of `timber.log.Timber` in the `src` directory of all modules.
- **SC-002**: 100% of logging calls in `commonMain` are successfully captured by the local file sink on Android.
- **SC-003**: Log file rotation occurs at precisely 5MB (+/- 10KB) during a simulated log burst.
- **SC-004**: Cold startup time impact of logging initialization is less than 50ms (Constitution §IV).

## Assumptions

- **KMP Support**: Kermit 2.x will be used to ensure compatibility with Kotlin 2.1+.
- **Legacy Compatibility**: The existing `PrivacyLogScrubber` logic is sound and can be reused within the new Kermit `LogWriter`.
- **Scope**: This migration covers all modules currently using Timber (specifically `app`, `core:common`, `core:security`, and `feature:fido2`).

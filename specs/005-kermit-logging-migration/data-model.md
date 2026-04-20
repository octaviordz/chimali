# Data Model: Kermit Logging Migration

## Entities

### `LogDirectoryProvider` (Interface / Expect)
Provides the base directory path for log storage on the current platform.

### `LocalCrashReportingLogWriter` (Class)
The KMP-compatible Kermit LogWriter implementation.
- **Dependencies**: `LogDirectoryProvider` (or platform specific expects), `FileSystem` (from Okio).
- **State**: Current log file size, current log file handle.

### `PrivacyScrubber` (Utility)
A common utility that receives a string and returns a scrubbed string, masking sensitive information.

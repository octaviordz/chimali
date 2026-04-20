# Data Model: Kermit Logging Migration

This feature does not introduce new persistent database models or complex domain entities. It focuses on replacing the logging framework.

## Core Logging Components

### LocalCrashReportingLogWriter
- **Type**: `co.touchlab.kermit.LogWriter` implementation
- **Location**: `androidMain` (initially)
- **Responsibility**: Listens to Kermit log events, applies `PrivacyLogScrubber`, and writes the output to a 5MB rotating local file (`fido2_crash_log.txt`).
- **State**: Maintains file handles and tracks file size for rotation.

### PrivacyLogScrubber
- **Type**: Utility Object
- **Location**: Shared/Platform (existing logic)
- **Responsibility**: Masks sensitive data strings (keys, mnemonics) using RegEx before they are written to disk.

### Kermit Logger
- **Type**: Framework Entity
- **Location**: `commonMain`
- **Responsibility**: Replaces `timber.log.Timber`. Accessed via `Logger.i()`, `Logger.d()`, `Logger.e()`, etc.

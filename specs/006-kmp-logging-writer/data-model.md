# Data Model: KMP Logging Writer

## Entities

### `LogDirectoryProvider` (Interface)
An abstraction used to obtain the base directory where log files should be stored.
- `fun getLogDirectory(): String` (or `okio.Path`)

### `LocalCrashReportingLogWriter` (Class)
The custom Kermit LogWriter that handles appending logs to a rotating local file.
- **Dependencies**: `LogDirectoryProvider`
- **Internal State**: Current file path (`okio.Path`), Lock mechanism.
- **Operations**: `log(...)` -> formats message, checks file size, rotates if > 5MB, appends.

### `PrivacyLogScrubber` (Utility)
A common utility that strips sensitive information from log messages before they are persisted.
- `fun scrub(message: String): String`

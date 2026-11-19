# Local crash-log final-output privacy regression

**Finding:** SEC065-LOG-052  
**Scope:** Final file emitted by `LocalCrashReportingLogWriter`, including message, tag and throwable handling.

Added a synthetic host regression that writes an operation message with an untrusted tag and an exception whose message, cause, type names and stack frame contain one unique marker. It reads the final temporary log file and requires the fixed operation message and generic `[throwable details omitted]` line while rejecting the marker and throwable class names. This tests the final writer output rather than only an intermediate scrubber/delegate argument.

Verification on 2026-11-16: full FIDO host suite passed 1,274 tests across 168 reports with zero failures/errors/skips; KtLint and Detekt passed. `LocalCrashReportingLogWriterTest` passed all 12 cases; its XML report SHA-256 is `6a069ce526e0da56d10d870d167d604bc80c408608ab16f13ec01a5c85326bc1`. This fixture directly exercises the writer, but does not prove `Fido2Initializer` registration/order, actual app-private directory permissions, platform Logcat behavior, release retention/rotation or device filesystem failure outputs; those remain under T029/T034/T035.

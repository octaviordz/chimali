# Research: Kermit Logging Migration

## Decision 1: Custom LogWriter implementation
- **Decision**: Implement a custom Kermit `LogWriter` named `LocalCrashReportingLogWriter` to replace the existing `LocalCrashReportingTree`.
- **Rationale**: Kermit's architecture relies on `LogWriter` interfaces to direct log output. By creating a custom `LogWriter`, we can reuse the existing `java.io.File` and `java.io.FileWriter` logic currently used in the Timber implementation (since it currently resides in `androidMain`, it can continue to use Java IO until full KMP file system support is added).
- **Alternatives considered**: Using Kermit's built-in platform loggers (does not support rotating files natively out-of-the-box for all targets without additional dependencies like `kermit-crashlytics` or custom implementations).

## Decision 2: Privacy Scrubbing Integration
- **Decision**: The existing `PrivacyLogScrubber` will be injected or called directly within the new `LocalCrashReportingLogWriter`.
- **Rationale**: The scrubbing logic is business-critical and independent of the logging framework. It can be applied to the message string before writing it to the file, exactly as it is done now.
- **Alternatives considered**: Creating a Kermit `MessageStringFormatter`. Decided against this because we only want to scrub logs going to the file sink (crash reporting), while local Logcat debugging might benefit from full output depending on debug builds.

## Decision 3: Dependency Scope
- **Decision**: Add `api(libs.kermit)` in `core:common`'s `commonMain` so all dependent modules get Kermit automatically, and remove `libs.timber` from `feature:fido2`'s `androidMain`.
- **Rationale**: `core:common` is the foundational module. Exposing Kermit via `api` ensures unified logging versions across the app.
- **Alternatives considered**: Adding it individually to each module via `implementation`. This increases maintenance overhead.

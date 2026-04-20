# Quickstart: Kermit Logging

This guide explains how to use the new Kermit logging framework across the Kotlin Multiplatform project.

## Basic Usage

To log a message from any module (`commonMain`, `androidMain`, `iosMain`), use the standard Kermit `Logger`:

```kotlin
import co.touchlab.kermit.Logger

class Fido2CryptoService {
    fun generateKeys() {
        Logger.i { "Starting key generation process" }
        
        try {
            // ... logic ...
            Logger.d { "Keys generated successfully" }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to generate keys" }
        }
    }
}
```

## Privacy Scrubbing & Crash Logs

All logs at level `INFO` and above are automatically routed to the `LocalCrashReportingLogWriter` on Android, which performs two critical functions:
1. **Scrubbing**: The `PrivacyLogScrubber` automatically masks sensitive data.
2. **Rotation**: Logs are written to `fido2_crash_log.txt` up to 5MB, then rotated.

You do **not** need to manually call the scrubber. Just use `Logger.i` or higher for important diagnostic info.

## Module Configuration

`core:common` provides Kermit via its `api` configuration. If you are creating a new module, simply depend on `core:common` to access Kermit automatically:

```kotlin
// In your module's build.gradle.kts
commonMain.dependencies {
    implementation(project(":core:common"))
}
```

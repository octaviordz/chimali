# Quickstart: KMP Kermit Logging

## Initialization

In your platform-specific application entry point (e.g., Android `Application.onCreate` or iOS `AppDelegate.didFinishLaunchingWithOptions`), initialize Kermit with the cross-platform writer:

```kotlin
// Android
val logDirProvider = AndroidLogDirectoryProvider(context)
Logger.setLogWriters(
    LogcatWriter(),
    LocalCrashReportingLogWriter(logDirProvider)
)

// iOS
val logDirProvider = IosLogDirectoryProvider()
Logger.setLogWriters(
    NSLogWriter(),
    LocalCrashReportingLogWriter(logDirProvider)
)
```

## Usage in Common Code

Simply use Kermit's `Logger` API in your `commonMain` code:

```kotlin
import co.touchlab.kermit.Logger

class Fido2Authenticator {
    fun authenticate() {
        Logger.i { "Starting authentication process..." }
        try {
            // ...
        } catch (e: Exception) {
            Logger.e(e) { "Authentication failed" }
        }
    }
}
```

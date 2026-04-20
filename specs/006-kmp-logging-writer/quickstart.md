# Quickstart: KMP Logging Writer

## Initialization

You must initialize Kermit with the `LocalCrashReportingLogWriter` in your platform-specific entry points, providing the correct `LogDirectoryProvider` for that platform.

### Android (`Application.onCreate()`)

```kotlin
import co.touchlab.kermit.Logger
import co.touchlab.kermit.LogcatWriter
import com.chimali.fido2.util.logging.LocalCrashReportingLogWriter
import okio.Path.Companion.toOkioPath

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val logDirProvider = object : LogDirectoryProvider {
            override fun getLogDirectory(): okio.Path {
                return filesDir.resolve("logs").toOkioPath()
            }
        }
        
        Logger.setLogWriters(
            LogcatWriter(),
            LocalCrashReportingLogWriter(logDirProvider)
        )
    }
}
```

### iOS (`AppDelegate.didFinishLaunchingWithOptions`)

```kotlin
// In iOS common entry point or Swift bridge
val logDirProvider = object : LogDirectoryProvider {
    override fun getLogDirectory(): okio.Path {
        // Use NSFileManager to get application support or cache dir
        val docDir = NSFileManager.defaultManager.URLForDirectory(
            NSApplicationSupportDirectory, 
            NSUserDomainMask, 
            null, 
            true, 
            null
        )?.path ?: ""
        return "$docDir/logs".toPath()
    }
}

Logger.setLogWriters(
    NSLogWriter(),
    LocalCrashReportingLogWriter(logDirProvider)
)
```

## Usage

In any `commonMain` code, simply log as usual. The log will automatically be scrubbed and written to the rotating file.

```kotlin
import co.touchlab.kermit.Logger

Logger.w { "Connection timeout. Retrying..." }
Logger.e(exception) { "Fatal error occurred" }
```

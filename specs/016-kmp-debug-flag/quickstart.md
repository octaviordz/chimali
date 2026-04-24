# Quickstart: KMP Debug Flag

## Usage

Import the centralized `isDebug` flag from `:core:common`:

```kotlin
import com.chimali.core.common.isDebug

fun someFunction() {
    if (isDebug) {
        println("Debug mode is active")
    }
}
```

## How it works

- **Android**: Returns `BuildConfig.DEBUG` from the `:core:common` module.
- **iOS**: Returns `Platform.isDebugBinary` from Kotlin/Native.

## Dead Code Elimination

Because `isDebug` is a compile-time constant, R8 will strip any code inside an `if (isDebug)` block when building for release:

```kotlin
// In a release build, this entire block is removed from the APK
if (isDebug) {
    showSecretDeveloperMenu()
}
```

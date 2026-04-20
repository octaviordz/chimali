# Detailed Changes - 2026-04-20

## Logging Migration (Timber to Kermit)
Refactored the entire `:feature:fido2` module to comply with the project's multiplatform logging standard using `co.touchlab.kermit.Logger`.

### Key Changes
- **Lambda Syntax Enforcement**: Converted all static logging calls to lazy lambdas: `Logger.d { "message" }`.
- **Idiomatic String Templates**: Refactored diagnostic logging to use native Kotlin string templates instead of `String.format()`, improving readability and ensuring multiplatform compatibility for structured logs like `[DIAG:...]`.
- **Exception Logging**: Standardized error reporting with `Logger.e(throwable) { "message" }`.
- **Module Coverage**: Cleaned up the following layers:
    - CTAP2 Handlers (`Ctap2GetAssertionHandler`, `Ctap2MakeCredentialHandler`, etc.)
    - Domain Use Cases (`GetAssertionUseCase`, `SelectCredentialUseCase`)
    - Data Transports (`BluetoothHidTransportImpl`)
    - UI Components (`Fido2HomeScreen`)

## KMP Logging Infrastructure (Refactor)
Refactored the core crash reporting persistence layer to be fully platform-agnostic, enabling it to be moved from `androidMain` to `commonMain`.

### Multiplatform File I/O (Okio)
- **Dependency Migration**: Replaced all `java.io.File` and `android.content.Context` dependencies with `okio.FileSystem` and `okio.Path`.
- **Log Rotation**: Ported the 5MB log rotation logic to use `FileSystem.SYSTEM` with metadata-based size checks, ensuring consistent behavior across all KMP targets.
- **Atomic Appends**: Implemented an `expect`/`actual` `PlatformLock` using `ReentrantLock` (Android/JVM) and `NSRecursiveLock` (iOS) to ensure thread-safe file writing without blocking coroutines or relying on JVM-only `@Synchronized`.

### Multiplatform Time (kotlinx-datetime)
- **API Update**: Migrated from `System.currentTimeMillis()` and `SimpleDateFormat` to `kotlinx-datetime`.
- **Kotlin 2.x Compatibility**: Resolved symbol resolution conflicts in `kotlinx-datetime 0.7.1` by utilizing the new `kotlin.time.Clock` standard library API.

### Abstraction & DI
- **Provider Pattern**: Introduced `LogDirectoryProvider` interface in `commonMain` to abstract platform-specific path resolution.
- **Implementation**: Added `AndroidLogDirectoryProvider` (using `context.filesDir`) and `IosLogDirectoryProvider` (using `NSApplicationSupportDirectory`).

## Performance Optimizations
Addressed "OVER BUDGET" alerts and main-thread blocking during FIDO2 registration ceremonies.

### Startup Latency (NFR-PERF-030)
- **Asynchronous Warm-up**: Moved BouncyCastle provider registration and AndroidKeyStore HAL IPC pre-warming to a background thread (`Dispatchers.Default`) in `Fido2Initializer`.
- **Impact**: Removed **~660ms** of blocking work from the application's main thread during startup.

### Ceremony Latency & Contention
- **Non-blocking Synchronization**: Replaced the `@Synchronized` monitor in `WalletMasterSeedProvider.ensureInitialized()` with a `kotlinx.coroutines.sync.Mutex`.
- **Parallel Execution**: This allows multiple threads to wait asynchronously for the master seed derivation (470ms+) without stalling the underlying OS threads or the UI thread.
- **Proactive Pre-warming**: Moved the `warmUpMasterSeed()` trigger from the Bluetooth connection callback to the `Fido2HomeViewModel` initialization. The seed is now decrypted and cached as soon as the user enters the FIDO2 dashboard, ensuring the first registration request hits the memory cache immediately.

## Stability & Fixes
- **UI Compilation**: Resolved an "Unresolved reference" in `Fido2HomeScreen.kt` by re-ordering local function declarations.
- **Logging Syntax**: Fixed malformed `Logger.e` calls in `Ctap2GetAssertionHandler.kt` that caused build failures.
- **Cleanup**: Removed 5+ temporary development scripts (`.py`, `.sh`) and build logs from the project root.

## Verification
- Verified full module compilation via `./gradlew :feature:fido2:compileDebugKotlinAndroid`.
- Validated performance impact via code analysis and previous logcat profiling showing elimination of lock contention.

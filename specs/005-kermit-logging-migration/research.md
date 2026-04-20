# Research: KMP Compatible Crash Reporting Logging

## Decision 1: File I/O
**Decision**: Use `okio` multiplatform library.
**Rationale**: Native Java `java.io.*` is not available in KMP. `okio` provides a robust, multiplatform file system API (`FileSystem.SYSTEM`) that works on JVM, Native (iOS), and JS/Wasm.
**Alternatives considered**: Expect/actual writing the I/O layer manually, which is error-prone and time consuming.

## Decision 2: Time and Date Formatting
**Decision**: Use `kotlinx-datetime` multiplatform library.
**Rationale**: `java.util.Date` and `SimpleDateFormat` are JVM only. `kotlinx-datetime` is the standard JetBrains library for multiplatform date and time manipulation.
**Alternatives considered**: Expect/actual bridging to `java.time` and `NSDate`, which requires maintaining two complex implementations.

## Decision 3: Log Directory Resolution
**Decision**: Use an injected interface or `expect`/`actual` function to resolve the system's log directory path.
**Rationale**: `android.content.Context.filesDir` is strictly Android. iOS uses `NSFileManager` to get the application support or cache directory. This must be abstracted so the common log writer can obtain the correct base path.

## Decision 4: Thread Safety
**Decision**: Use `kotlinx.coroutines.sync.Mutex` or `expect`/`actual` locks.
**Rationale**: `@Synchronized` and `java.util.concurrent` are JVM-specific. For multiplatform thread-safety, `Mutex` is typically preferred if in a suspending context, or atomic references/platform locks if in synchronous code. Kermit's `LogWriter` is synchronous, so we will need either a simple platform lock or use atomic operations.

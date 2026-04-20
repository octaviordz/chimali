# Research: KMP Logging Writer

## Decision 1: Multiplatform File I/O
**Decision**: Use `com.squareup.okio:okio`.
**Rationale**: `okio` is the standard, battle-tested KMP library for file I/O operations. It provides `FileSystem.SYSTEM` which abstracts away the underlying platform file system APIs (like `java.io.File` on JVM/Android and POSIX APIs on Native/iOS).
**Alternatives considered**: Expect/actual bridging to `java.io` and `NSFileManager`, which would require maintaining two separate I/O implementations and duplicating rotation logic.

## Decision 2: Multiplatform Date and Time
**Decision**: Use `org.jetbrains.kotlinx:kotlinx-datetime`.
**Rationale**: It is the official multiplatform date/time library by JetBrains. It supports generating timestamps across all platforms without relying on `java.util.Date`.
**Alternatives considered**: Expect/actual bridging to `java.time` and `NSDate`.

## Decision 3: Thread-Safe Appending
**Decision**: Use a Mutex (from `kotlinx-coroutines-core`) or platform-specific locks if synchronous execution is strictly required. Since Kermit's `LogWriter` API is synchronous, `expect`/`actual` locks (`AtomicReference` or `ReentrantLock` on JVM, `NSRecursiveLock` on iOS) are the most appropriate for performance.
**Rationale**: `@Synchronized` is JVM-only. For a multiplatform synchronous API, we must use expect/actual locks or rely on standard atomic constructs if possible.
**Alternatives considered**: Coroutine `Mutex`, but that requires `runBlocking` which is discouraged or unsupported on some KMP targets without specific configurations.

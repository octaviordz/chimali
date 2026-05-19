# Contract: FIDO2 Platform Abstractions

**Module**: `:feature:fido2`
**Branch**: `046-kmp-expect-class-migration`
**Date**: 2026-05-18

## PlatformBluetoothHid

**Package**: `com.chimali.fido2.platform`

```kotlin
interface PlatformBluetoothHid {
    fun isSupported(): Boolean
    fun isAdapterEnabled(): Boolean
}
```

### Android implementation

- **Type**: `AndroidPlatformBluetoothHid(context: Context)`
- **Construction**: Requires Android `Context` (application or activity context acceptable for `getSystemService`).
- **Guarantees**: Idempotent, no I/O beyond system service queries; safe to call from any thread (matches prior `actual`).

### iOS implementation

- **Type**: `IosPlatformBluetoothHid()`
- **Guarantees**: Always returns `false` for both methods until CoreBluetooth integration (documented placeholder).

---

## PlatformLock

**Package**: `com.chimali.fido2.util.logging`

```kotlin
interface PlatformLock {
    fun lock()
    fun unlock()
}

inline fun <T> PlatformLock.withLock(block: () -> T): T
```

### Android implementation

- **Type**: `AndroidPlatformLock()` — no-arg constructor
- **Guarantees**: Reentrant; same thread may re-enter; `withLock` must release lock on exception

### iOS implementation

- **Type**: `IosPlatformLock()` — no-arg constructor
- **Guarantees**: Recursive native lock; parity with Android contract for future iosMain log writers

---

## Migration invariant

Replacements for `expect class` / `actual class` MUST preserve:

1. Method signatures and visibility
2. Return values for equivalent platform/environment inputs
3. Lock fairness and reentrancy semantics on each platform

No new public API surface beyond renamed implementation classes.

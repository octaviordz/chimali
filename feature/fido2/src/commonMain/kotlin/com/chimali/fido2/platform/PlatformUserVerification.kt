package com.chimali.fido2.platform

/**
 * T191 — Platform-specific user verification availability check.
 *
 * `expect class` boundary: declared in commonMain, implemented as `actual class` in each
 * platform source set.
 *
 * ## Why `expect class` instead of `expect interface`?
 *
 * Koin inject sites need a concrete type to resolve. An `expect class` with a consistent
 * constructor signature allows Koin Annotations to wire the `actual` implementation
 * without DSL overrides. It also avoids double-dispatch that an interface adapter would add.
 *
 * ## Behavioural contract
 *
 * - [isAvailable] — `true` iff __any__ biometric or device-credential mechanism is enrolled
 *   and the hardware is present. Corresponds to `BIOMETRIC_SUCCESS` on Android.
 * - [canAuthenticate] — `true` iff strong biometric (Class 3) is immediately usable.
 * - [isDeviceSecure] — `true` iff at least a PIN/pattern/password protects the device lock.
 *
 * Implementations MUST be side-effect free (no UI, no coroutines, no blocking I/O).
 */
expect class PlatformUserVerification {
    /** Returns true if any enrolled biometric/credential is present and hardware is ready. */
    fun isAvailable(): Boolean

    /** Returns true if strong biometric (Class 3 on Android) is usable right now. */
    fun canAuthenticate(): Boolean

    /** Returns true if the device has at least a PIN, pattern, or password lock. */
    fun isDeviceSecure(): Boolean
}

package com.chimali.core.security.biometrics

/**
 * Platform-specific user verification availability check.
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
interface PlatformUserVerification {
    /** Returns true if any enrolled biometric/credential is present and hardware is ready. */
    fun isAvailable(): Boolean

    /** Returns true if strong biometric (Class 3 on Android) is usable right now. */
    fun canAuthenticate(): Boolean

    /** Returns true if the device has at least a PIN, pattern, or password lock. */
    fun isDeviceSecure(): Boolean
}

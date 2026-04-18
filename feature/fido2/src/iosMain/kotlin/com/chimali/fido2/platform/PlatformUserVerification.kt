package com.chimali.fido2.platform

/**
 * T191/T192 — iOS placeholder `actual` for [PlatformUserVerification].
 *
 * Returns sensible defaults until CoreBiometrics (LAContext) is wired up in a
 * future iOS implementation task. iOS uses LocalAuthentication.framework for
 * biometric checks — this will be implemented in T191 follow-up.
 *
 * @see <a href="https://developer.apple.com/documentation/localauthentication">LocalAuthentication</a>
 */
actual class PlatformUserVerification {

    /** iOS: placeholder — always returns false until LAContext is integrated. */
    actual fun isAvailable(): Boolean = false

    /** iOS: placeholder — always returns false until LAContext is integrated. */
    actual fun canAuthenticate(): Boolean = false

    /**
     * iOS: placeholder — returns false. On iOS, device lock can be checked via
     * LAContext.canEvaluatePolicy(.deviceOwnerAuthentication).
     */
    actual fun isDeviceSecure(): Boolean = false
}

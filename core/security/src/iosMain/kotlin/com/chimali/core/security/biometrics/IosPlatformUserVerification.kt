package com.chimali.core.security.biometrics

/**
 * iOS placeholder implementation of [PlatformUserVerification].
 *
 * Returns sensible defaults until CoreBiometrics (LAContext) is wired up in a
 * future iOS implementation task. iOS uses LocalAuthentication.framework for
 * biometric checks.
 *
 * @see <a href="https://developer.apple.com/documentation/localauthentication">LocalAuthentication</a>
 */
class IosPlatformUserVerification : PlatformUserVerification {
    /** iOS: placeholder — always returns false until LAContext is integrated. */
    override fun isAvailable(): Boolean = false

    /** iOS: placeholder — always returns false until LAContext is integrated. */
    override fun canAuthenticate(): Boolean = false

    /**
     * iOS: placeholder — returns false. On iOS, device lock can be checked via
     * LAContext.canEvaluatePolicy(.deviceOwnerAuthentication).
     */
    override fun isDeviceSecure(): Boolean = false
}

package com.chimali.fido2.platform

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators

/**
 * T191 — Android `actual` implementation of [PlatformUserVerification].
 *
 * Wraps [BiometricManager] to answer capability queries without importing Android APIs
 * into commonMain. Injected via Koin (Context resolved from androidContext()).
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class PlatformUserVerification(
    private val context: Context,
) {
    private val biometricManager: BiometricManager by lazy {
        BiometricManager.from(context)
    }

    /**
     * True if any credential method (strong biometric OR device credential) is enrolled.
     * Maps to [BiometricManager.BIOMETRIC_SUCCESS] for BIOMETRIC_STRONG | DEVICE_CREDENTIAL.
     */
    actual fun isAvailable(): Boolean =
        biometricManager.canAuthenticate(
            Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL,
        ) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * True if a Class 3 (strong) biometric is currently enrolled and ready.
     */
    actual fun canAuthenticate(): Boolean =
        biometricManager.canAuthenticate(Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * True if the device is protected by at least a PIN, pattern, or password.
     */
    actual fun isDeviceSecure(): Boolean =
        biometricManager.canAuthenticate(Authenticators.DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
}

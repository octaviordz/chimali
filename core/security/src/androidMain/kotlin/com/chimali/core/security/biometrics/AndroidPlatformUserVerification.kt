package com.chimali.core.security.biometrics

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators

/**
 * Android implementation of [PlatformUserVerification].
 *
 * Wraps [BiometricManager] to answer capability queries without importing Android APIs
 * into commonMain.
 */
class AndroidPlatformUserVerification(
    private val context: Context,
) : PlatformUserVerification {
    private val biometricManager: BiometricManager by lazy {
        BiometricManager.from(context)
    }

    /**
     * True if any credential method (strong biometric OR device credential) is enrolled.
     * Maps to [BiometricManager.BIOMETRIC_SUCCESS] for BIOMETRIC_STRONG | DEVICE_CREDENTIAL.
     */
    override fun isAvailable(): Boolean =
        biometricManager.canAuthenticate(
            Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL,
        ) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * True if a Class 3 (strong) biometric is currently enrolled and ready.
     */
    override fun canAuthenticate(): Boolean =
        biometricManager.canAuthenticate(Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * True if the device is protected by at least a PIN, pattern, or password.
     */
    override fun isDeviceSecure(): Boolean =
        biometricManager.canAuthenticate(Authenticators.DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
}

package com.chimali.fido2.presentation.ui

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt

/** Configures a prompt that accepts a strong biometric or the device's secure credential. */
internal fun BiometricPrompt.PromptInfo.Builder.allowFidoUserVerification(): BiometricPrompt.PromptInfo.Builder =
    apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } else {
            // The combined authenticator bitmask is unsupported on API 28–29.
            setDeviceCredentialAllowed(true)
        }
    }

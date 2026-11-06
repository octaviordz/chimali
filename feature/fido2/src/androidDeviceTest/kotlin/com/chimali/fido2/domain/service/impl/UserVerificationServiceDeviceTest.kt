package com.chimali.fido2.domain.service.impl

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.time.TimeProvider
import com.chimali.fido2.presentation.ui.allowFidoUserVerification
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Req("FR-AUTH-010", "FR-BLE-030")
class UserVerificationServiceDeviceTest {
    @Test
    fun deviceCredentialAvailabilityMatchesAndroidPlatformSupport() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val biometricManager = BiometricManager.from(context)
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val service = UserVerificationServiceImpl(biometricManager, keyguardManager, TimeProvider())

            val expected =
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    keyguardManager.isDeviceSecure
                } else {
                    biometricManager.canAuthenticate(
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                    ) == BiometricManager.BIOMETRIC_SUCCESS
                }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                assumeTrue("This API 28–29 verification requires a configured PIN, pattern or password.", expected)
                assertTrue(
                    "A secure API 28–29 device credential must be available through KeyguardManager.",
                    service.isPinAvailable(),
                )
            } else {
                assertEquals(
                    "Chimali device-credential availability must match the supported Android API for this release.",
                    expected,
                    service.isPinAvailable(),
                )
            }
        }

    @Test
    fun fidoPromptConfigurationBuildsForThisAndroidVersion() {
        val promptInfo =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle("Chimali user verification test")
                .setSubtitle("Credential prompt configuration check")
                .allowFidoUserVerification()
                .build()

        assertTrue(
            "FIDO prompt configuration should build on the supported Android version.",
            promptInfo.title.isNotBlank(),
        )
    }
}

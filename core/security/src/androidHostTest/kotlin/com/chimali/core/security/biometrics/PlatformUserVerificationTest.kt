package com.chimali.core.security.biometrics

import android.content.Context
import androidx.biometric.BiometricManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PlatformUserVerificationTest {
    private val context: Context = mockk(relaxed = true)
    private val biometricManager: BiometricManager = mockk()
    private lateinit var verification: AndroidPlatformUserVerification

    @BeforeEach
    fun setUp() {
        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(context) } returns biometricManager
        verification = AndroidPlatformUserVerification(context)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `isAvailable returns true when BIOMETRIC_SUCCESS`() {
        every {
            biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } returns BiometricManager.BIOMETRIC_SUCCESS

        assertTrue(verification.isAvailable())
    }

    @Test
    fun `isAvailable returns false when BIOMETRIC_ERROR_NO_HARDWARE`() {
        every {
            biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

        assertFalse(verification.isAvailable())
    }

    @Test
    fun `canAuthenticate returns true when BIOMETRIC_SUCCESS`() {
        every {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns BiometricManager.BIOMETRIC_SUCCESS

        assertTrue(verification.canAuthenticate())
    }

    @Test
    fun `isDeviceSecure returns true when BIOMETRIC_SUCCESS`() {
        every {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } returns BiometricManager.BIOMETRIC_SUCCESS

        assertTrue(verification.isDeviceSecure())
    }
}

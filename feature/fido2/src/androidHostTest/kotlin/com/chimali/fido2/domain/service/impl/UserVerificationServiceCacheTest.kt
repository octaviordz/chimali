package com.chimali.fido2.domain.service.impl

import android.app.KeyguardManager
import androidx.biometric.BiometricManager
import com.chimali.core.domain.time.TimeProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class UserVerificationServiceCacheTest {
    @Test
    fun `availability cache expires on elapsed time when wall clock moves backwards`() =
        runTest {
            val biometricManager = mockk<BiometricManager>()
            val keyguardManager = mockk<KeyguardManager>(relaxed = true)
            val timeProvider = mockk<TimeProvider>(relaxed = true)
            var biometricStatus = BiometricManager.BIOMETRIC_SUCCESS
            every { biometricManager.canAuthenticate(any()) } answers {
                if (firstArg<Int>() == BiometricManager.Authenticators.BIOMETRIC_STRONG) {
                    biometricStatus
                } else {
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
                }
            }
            every { timeProvider.epochMillis() } returnsMany listOf(100_000L, 0L, 1L)
            var elapsedRealtimeMs = 1_000L
            val service =
                UserVerificationServiceImpl(biometricManager, keyguardManager, timeProvider) {
                    elapsedRealtimeMs
                }

            assertEquals(true, service.getUserVerificationAvailability().isBiometricAvailable)
            biometricStatus = BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
            elapsedRealtimeMs += 9_999L
            assertEquals(true, service.getUserVerificationAvailability().isBiometricAvailable)
            elapsedRealtimeMs += 1L
            assertEquals(false, service.getUserVerificationAvailability().isBiometricAvailable)

            verify(exactly = 2) {
                biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            }
        }
}

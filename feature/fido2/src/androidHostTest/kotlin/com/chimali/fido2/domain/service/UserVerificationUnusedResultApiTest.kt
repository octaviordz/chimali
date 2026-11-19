package com.chimali.fido2.domain.service

import kotlin.test.Test
import kotlin.test.assertFailsWith

class UserVerificationUnusedResultApiTest {
    @Test
    fun `unused result models with platform messages are not shipped`() {
        listOf(
            "com.chimali.fido2.domain.service.BiometricVerificationResult",
            "com.chimali.fido2.domain.service.PinVerificationResult",
            "com.chimali.fido2.domain.service.CombinedVerificationResult",
            "com.chimali.fido2.domain.service.DeviceLockVerificationResult",
        ).forEach { className ->
            assertFailsWith<ClassNotFoundException> { Class.forName(className) }
        }
    }
}

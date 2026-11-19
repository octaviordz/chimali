package com.chimali.core.security.biometrics

import kotlin.test.Test
import kotlin.test.assertFailsWith

class UnusedPlatformUserVerificationApiTest {
    @Test
    fun `orphaned capability adapter API is not shipped`() {
        listOf(
            "com.chimali.core.security.biometrics.PlatformUserVerification",
            "com.chimali.core.security.biometrics.AndroidPlatformUserVerification",
        ).forEach { className ->
            assertFailsWith<ClassNotFoundException> { Class.forName(className) }
        }
    }
}

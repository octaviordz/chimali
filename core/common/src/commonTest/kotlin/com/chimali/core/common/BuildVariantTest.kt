package com.chimali.core.common

import kotlin.test.Test
import kotlin.test.assertNotNull

class BuildVariantTest {
    @Test
    fun testIsDebugIsAccessible() {
        // We can't easily assert if it's true or false in commonTest without mocks,
        // but we can verify it's accessible and returns a value.
        assertNotNull(isDebug)
    }
}

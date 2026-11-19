package com.chimali

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class MainActivityPermissionDiagnosticTest {
    @Test
    fun grantedPermissionStatesDoNotEmitADiagnostic() {
        var emitted = false

        logDeniedStartupPermissions(mapOf("synthetic-granted-permission" to true)) { _, _ ->
            emitted = true
        }

        assertFalse(emitted)
    }

    @Test
    fun deniedPermissionNamesAndStatusesAreNotIncludedInDiagnostic() {
        val marker = "synthetic-permission-state-marker"
        var emittedTag: String? = null
        var emittedMessage: String? = null

        logDeniedStartupPermissions(mapOf(marker to false)) { tag, message ->
            emittedTag = tag
            emittedMessage = message
        }

        assertEquals("Chimali:MainActivity", emittedTag)
        assertEquals("Some startup permissions were denied at startup.", emittedMessage)
        assertFalse(requireNotNull(emittedMessage).contains(marker))
    }
}

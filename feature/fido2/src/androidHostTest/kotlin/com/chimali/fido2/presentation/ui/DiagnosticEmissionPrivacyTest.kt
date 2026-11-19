package com.chimali.fido2.presentation.ui

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.service.TransportLifecycleFailure
import com.chimali.fido2.service.logTransportLifecycleFailure
import com.chimali.fido2.testing.CapturedDiagnostics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Req("FR-008", "FR-009")
class DiagnosticEmissionPrivacyTest {
    @Test
    fun `transport lifecycle diagnostics omit failure details`() {
        val markers = TransportLifecycleFailure.entries.map { "synthetic-${it.name.lowercase()}-failure" }

        CapturedDiagnostics().use { diagnostics ->
            TransportLifecycleFailure.entries.forEachIndexed { index, operation ->
                logTransportLifecycleFailure(
                    Result.failure(IllegalStateException(markers[index])),
                    operation,
                )
            }

            assertTrue(diagnostics.size >= TransportLifecycleFailure.entries.size)
            diagnostics.assertNoSensitiveValues(*markers.toTypedArray())
        }
    }

    @Test
    fun `camera diagnostics omit exception details and bind error is generic`() {
        val bindMarker = "synthetic-private-camera-bind-detail"
        val releaseMarker = "synthetic-private-camera-release-detail"
        var reportedError: String? = null

        CapturedDiagnostics().use { diagnostics ->
            reportMnemonicCameraBindFailure(IllegalStateException(bindMarker)) { reportedError = it }
            logMnemonicCameraReleaseFailure(IllegalStateException(releaseMarker))

            assertEquals("Camera error", reportedError)
            assertTrue(diagnostics.size >= 2)
            diagnostics.assertNoSensitiveValues(bindMarker, releaseMarker)
        }
    }

    @Test
    fun `discoverability diagnostic omits security exception details`() {
        val marker = "synthetic-private-discoverability-detail"

        CapturedDiagnostics().use { diagnostics ->
            logDiscoverabilityLaunchFailure(SecurityException(marker))

            assertTrue(diagnostics.size > 0)
            diagnostics.assertNoSensitiveValues(marker)
        }
    }
}

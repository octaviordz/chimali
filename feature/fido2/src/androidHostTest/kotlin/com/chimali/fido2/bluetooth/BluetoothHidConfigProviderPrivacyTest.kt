package com.chimali.fido2.bluetooth

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.testing.CapturedDiagnostics
import kotlin.test.Test

@Req("NFR-SEC-041", "TR-AUTH-031")
class BluetoothHidConfigProviderPrivacyTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `configuration resolution does not emit local manufacturer or model`() {
        CapturedDiagnostics().use { diagnostics ->
            BluetoothHidConfigProvider.config

            diagnostics.assertNoSensitiveValues("manufacturer=", "model=")
        }
    }
}

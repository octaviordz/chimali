package com.chimali.fido2.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import com.chimali.fido2.testing.CapturedDiagnostics
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BluetoothHidReportSenderPrivacyTest {
    @Test
    fun `send failure diagnostics omit platform exception content`() {
        val hid = mockk<BluetoothHidDevice>()
        val device = mockk<BluetoothDevice>()
        val reportSlot = slot<ByteArray>()
        val marker = "synthetic-private-bluetooth-provider-detail"
        every { hid.sendReport(device, FIDO_HID_REPORT_ID, capture(reportSlot)) } throws IllegalStateException(marker)
        val input = byteArrayOf(1, 2, 3)
        val diagnostics = CapturedDiagnostics()

        try {
            assertFalse(BluetoothHidReportSender().send(hid, device, input))
            assertContentEquals(byteArrayOf(1, 2, 3), input)
            assertTrue(reportSlot.captured.all { it == 0.toByte() })
            assertTrue(diagnostics.size > 0)
            diagnostics.assertNoSensitiveValues(marker)
        } finally {
            diagnostics.close()
        }
    }
}

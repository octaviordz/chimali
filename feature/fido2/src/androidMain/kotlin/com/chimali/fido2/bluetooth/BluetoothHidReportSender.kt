@file:Suppress("MissingPermission")

package com.chimali.fido2.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req

/**
 * HID report identifier used by the unnumbered FIDO report descriptor.
 * Android's [BluetoothHidDevice.sendReport] API still requires the identifier argument.
 */
@Req("FR-HID-010", "FR-HID-020")
internal const val FIDO_HID_REPORT_ID = 0

/** Formats and dispatches one borrowed CTAP report through Android's HID profile. */
@Req("FR-HID-010", "FR-HID-020")
internal class BluetoothHidReportSender {
    /**
     * Sends a padded 62-byte copy to the supplied host and erases that temporary copy afterward.
     * The caller retains ownership of [data]; null profile or host and platform failures return false.
     */
    @Req("FR-HID-010", "FR-HID-020")
    fun send(
        hid: BluetoothHidDevice?,
        device: BluetoothDevice?,
        data: ByteArray,
    ): Boolean {
        if (hid == null) {
            Logger.w("sendReport: HID device not available")
            return false
        }
        if (device == null) {
            Logger.w("sendReport: no connected device")
            return false
        }

        val report = ByteArray(FIDO_HID_REPORT_SIZE)
        data.copyInto(report, 0, 0, minOf(data.size, FIDO_HID_REPORT_SIZE))
        return try {
            val isSent = hid.sendReport(device, FIDO_HID_REPORT_ID, report)
            Logger.d { "sendReport dispatched len=${report.size} success=$isSent" }
            isSent
        } catch (e: SecurityException) {
            Logger.e(e) { "sendReport: BLUETOOTH_CONNECT permission denied" }
            false
        } catch (e: IllegalStateException) {
            Logger.e(e) { "sendReport: Bluetooth not available — ${e.message}" }
            false
        } finally {
            report.fill(0)
        }
    }
}

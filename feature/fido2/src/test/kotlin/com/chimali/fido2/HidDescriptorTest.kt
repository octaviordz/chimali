package com.chimali.fido2

import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.assertNotNull

@RunWith(RobolectricTestRunner::class)
class HidDescriptorTest {

    @Test
    fun testWaratahDescriptor() {
        val descriptor = byteArrayOf(
            0x06.toByte(), 0xD0.toByte(), 0xF1.toByte(), // UsagePage(FIDO Alliance[0xF1D0])
            0x09.toByte(), 0x01.toByte(),          // UsageId(U2F Authenticator Device[0x0001])
            0xA1.toByte(), 0x01.toByte(),          // Collection(Application)
            0x85.toByte(), 0x01.toByte(),          //     ReportId(1)
            0x09.toByte(), 0x20.toByte(),          //     UsageId(Input Report Data[0x0020])
            0x15.toByte(), 0x00.toByte(),          //     LogicalMinimum(0)
            0x26.toByte(), 0xFF.toByte(), 0x00.toByte(), //     LogicalMaximum(255)
            0x95.toByte(), 0x40.toByte(),          //     ReportCount(64)
            0x75.toByte(), 0x08.toByte(),          //     ReportSize(8)
            0x81.toByte(), 0x02.toByte(),          //     Input(Data, Variable, Absolute)
            0x09.toByte(), 0x21.toByte(),          //     UsageId(Output Report Data[0x0021])
            0x91.toByte(), 0x02.toByte(),          //     Output(Data, Variable, Absolute)
            0xC0.toByte()                          // EndCollection()
        )

        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Chimali Authenticator",
            "FIDO2 Virtual Security Key",
            "Chimali",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            descriptor
        )
        assertNotNull(sdp)
    }
}

package com.chimali.fido2.data.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.content.Context
import com.chimali.core.common.annotation.Req
import com.chimali.fido2.testing.CapturedDiagnostics
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

@Req("FR-008", "FR-009", "FR-BLE-010", "TR-BLE-010")
class AndroidFidoBleGattServerHostPrivacyTest {
    @Test
    fun `notification callback diagnostic omits platform status number`() {
        val hostAddress = "00:11:22:33:44:55"
        val device = mockk<BluetoothDevice> { every { address } returns hostAddress }
        val host = AndroidFidoBleGattServerHost(mockk<Context>(relaxed = true), BleSessionOwner())
        val callbackDelegate =
            AndroidFidoBleGattServerHost::class.java.getDeclaredField("callback\$delegate").apply {
                isAccessible = true
            }
        val callback = (callbackDelegate.get(host) as Lazy<*>).value as BluetoothGattServerCallback

        CapturedDiagnostics().use { diagnostics ->
            callback.onNotificationSent(device, 777)

            assertTrue(diagnostics.containsMessage("GATT notification callback failed"))
            diagnostics.assertNoSensitiveValues(hostAddress, "777")
        }
    }

    @Test
    fun `startup failure diagnostic omits platform exception details`() =
        runTest {
            val marker = "synthetic-private-gatt-startup-detail"
            val context = mockk<Context>()
            every { context.getSystemService(Context.BLUETOOTH_SERVICE) } throws SecurityException(marker)
            val host = AndroidFidoBleGattServerHost(context, BleSessionOwner())

            CapturedDiagnostics().use { diagnostics ->
                val result = host.start({}, { _, _ -> }, { _, _, _ -> }, {})

                assertFalse(result.isSuccess)
                assertEquals(
                    marker,
                    result.exceptionOrNull()?.message,
                    "The caller still receives the original failure",
                )
                assertTrue(diagnostics.size > 0)
                diagnostics.assertNoSensitiveValues(marker)
            }
        }

    @Test
    fun `cleanup permission diagnostic omits platform exception details`() {
        val marker = "synthetic-private-gatt-cleanup-detail"
        val context = mockk<Context>()
        val host = AndroidFidoBleGattServerHost(context, BleSessionOwner())
        val server = mockk<BluetoothGattServer>()
        every { server.clearServices() } throws SecurityException(marker)
        AndroidFidoBleGattServerHost::class.java.getDeclaredField("gattServer").apply {
            isAccessible = true
            set(host, server)
        }

        CapturedDiagnostics().use { diagnostics ->
            host.stop()

            assertTrue(diagnostics.size > 0)
            diagnostics.assertNoSensitiveValues(marker)
        }
    }
}

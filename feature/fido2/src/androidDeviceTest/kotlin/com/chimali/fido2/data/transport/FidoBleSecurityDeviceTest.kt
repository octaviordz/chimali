package com.chimali.fido2.data.transport

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimali.core.common.annotation.Req
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Req("FR-BLE-020", "FR-BLE-030", "TR-BLE-010", "TR-BLE-020")
class FidoBleSecurityDeviceTest {
    private class RecordingSender : GattResponseSender {
        var responseStatus: Int? = null

        override fun sendResponse(
            device: BluetoothDevice,
            requestId: Int,
            status: Int,
            offset: Int,
            value: ByteArray?,
        ): Boolean {
            responseStatus = status
            return true
        }

        override fun notifyCharacteristicChanged(
            device: BluetoothDevice,
            characteristic: BluetoothGattCharacteristic,
            confirm: Boolean,
            value: ByteArray,
        ): Boolean = true
    }

    @Test
    fun controlPointAndNotificationConfigurationRequireMitmEncryption() {
        val callback = FidoBleGattServer(BleSessionOwner(), RecordingSender())
        val service = callback.buildFidoGattService()
        val controlPoint = service.getCharacteristic(FidoBleGattServer.UUID_FIDO_CONTROL_POINT)
        val status = service.getCharacteristic(FidoBleGattServer.UUID_FIDO_STATUS)
        val cccd = status.getDescriptor(FidoBleGattServer.UUID_CCCD)

        assertEquals(BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM, controlPoint.permissions)
        assertEquals(
            BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED_MITM or
                BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED_MITM,
            cccd.permissions,
        )
        assertTrue(service.type == BluetoothGattService.SERVICE_TYPE_PRIMARY)
    }

    @Test
    fun deviceInformationServiceExposesGenericReadableProductCharacteristics() {
        val service = FidoBleGattServer(BleSessionOwner(), RecordingSender()).buildDeviceInformationService()

        assertEquals(FidoBleGattServer.UUID_DEVICE_INFORMATION_SERVICE, service.uuid)
        assertEquals(BluetoothGattService.SERVICE_TYPE_PRIMARY, service.type)
        val expectedValues =
            mapOf(
                FidoBleGattServer.UUID_MANUFACTURER_NAME_STRING to "Chimali",
                FidoBleGattServer.UUID_MODEL_NUMBER_STRING to "Chimali Authenticator",
                FidoBleGattServer.UUID_FIRMWARE_REVISION_STRING to "1.0",
            )
        expectedValues.forEach { (uuid, expectedValue) ->
            val characteristic = service.getCharacteristic(uuid)
            assertEquals(BluetoothGattCharacteristic.PROPERTY_READ, characteristic.properties)
            assertEquals(BluetoothGattCharacteristic.PERMISSION_READ, characteristic.permissions)
            @Suppress("DEPRECATION")
            assertArrayEquals(expectedValue.toByteArray(), characteristic.value)
        }
    }

    @Test
    fun androidGattHostRegistersBothServicesBeforeReportingReady() =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                instrumentation.uiAutomation.grantRuntimePermission(
                    context.packageName,
                    Manifest.permission.BLUETOOTH_CONNECT,
                )
            }
            val host = AndroidFidoBleGattServerHost(context, BleSessionOwner())

            try {
                val result =
                    host.start(
                        onPacketReceived = {},
                        onNotificationSent = { _, _ -> },
                        onConnectionStateChanged = { _, _, _ -> },
                        onAuthenticationFailure = {},
                    )

                assertTrue(
                    "GATT host failed to register its required services: ${result.exceptionOrNull()}",
                    result.isSuccess,
                )
            } finally {
                host.stop()
            }
        }

    @Test
    fun pairingFailureRejectsControlPointWriteBeforePacketAdmission() {
        val sender = RecordingSender()
        val sessionOwner = BleSessionOwner()
        val callback =
            FidoBleGattServer(
                sessionOwner = sessionOwner,
                responseSender = sender,
                isDeviceAuthenticated = { false },
            )
        val device = mockk<BluetoothDevice>()
        every { device.address } returns "11:22:33:44:55:66"
        every { device.bondState } returns BluetoothDevice.BOND_NONE
        sessionOwner.connectHost(device.address)
        val controlPoint = mockk<BluetoothGattCharacteristic>()
        every { controlPoint.uuid } returns FidoBleGattServer.UUID_FIDO_CONTROL_POINT
        var admitted = false
        callback.onPacketReceivedListener = { admitted = true }

        callback.onCharacteristicWriteRequest(
            device = device,
            requestId = 1,
            characteristic = controlPoint,
            preparedWrite = false,
            responseNeeded = true,
            offset = 0,
            value = byteArrayOf(0x81.toByte()),
        )

        assertEquals(BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION, sender.responseStatus)
        assertFalse(admitted)
    }
}

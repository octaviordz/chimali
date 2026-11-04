package com.chimali.fido2.data.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimali.core.common.annotation.Req
import io.mockk.every
import io.mockk.mockk
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

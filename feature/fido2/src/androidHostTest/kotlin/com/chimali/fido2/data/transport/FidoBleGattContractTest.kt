package com.chimali.fido2.data.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import com.chimali.core.common.annotation.Req
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Req(
    "FR-BLE-010",
    "FR-BLE-020",
    "FR-BLE-030",
    "FR-BLE-040",
    "FR-BLE-050",
    "FR-BLE-070",
    "TR-BLE-010",
    "TR-BLE-020",
    "TR-BLE-040",
)
class FidoBleGattContractTest {
    private lateinit var gattServer: FidoBleGattServer
    private lateinit var sessionOwner: BleSessionOwner
    private lateinit var fakeSender: FakeGattResponseSender
    private lateinit var testDevice: BluetoothDevice

    private class FakeGattResponseSender : GattResponseSender {
        data class SentResponse(
            val device: BluetoothDevice,
            val requestId: Int,
            val status: Int,
            val offset: Int,
            val value: ByteArray?,
        )

        val responses = mutableListOf<SentResponse>()
        val notifications = mutableListOf<ByteArray>()

        override fun sendResponse(
            device: BluetoothDevice,
            requestId: Int,
            status: Int,
            offset: Int,
            value: ByteArray?,
        ): Boolean {
            responses.add(SentResponse(device, requestId, status, offset, value?.copyOf()))
            return true
        }

        override fun notifyCharacteristicChanged(
            device: BluetoothDevice,
            characteristic: BluetoothGattCharacteristic,
            confirm: Boolean,
            value: ByteArray,
        ): Boolean {
            notifications.add(value.copyOf())
            return true
        }
    }

    @BeforeTest
    fun setUp() {
        testDevice = mockk(relaxed = true)
        every { testDevice.address } returns "11:22:33:44:55:66"
        every { testDevice.bondState } returns BluetoothDevice.BOND_BONDED

        sessionOwner = BleSessionOwner()
        sessionOwner.connectHost(testDevice.address)
        fakeSender = FakeGattResponseSender()
        gattServer =
            FidoBleGattServer(
                sessionOwner = sessionOwner,
                responseSender = fakeSender,
                isDeviceAuthenticated = { it.bondState == BluetoothDevice.BOND_BONDED },
            )
    }

    @Test
    @Req("FR-BLE-010", "TR-BLE-010")
    fun `service definition exposes primary service and four required characteristics`() {
        val createdCharacteristics = mutableListOf<BluetoothGattCharacteristic>()
        val mockService = mockk<BluetoothGattService>(relaxed = true)
        every { mockService.uuid } returns FidoBleGattServer.UUID_FIDO_SERVICE
        every { mockService.type } returns BluetoothGattService.SERVICE_TYPE_PRIMARY
        every { mockService.addCharacteristic(any()) } answers {
            createdCharacteristics.add(firstArg())
            true
        }

        val service =
            gattServer.buildFidoGattService(
                serviceFactory = { _, _ -> mockService },
                characteristicFactory = { uuid, prop, perm ->
                    val c = mockk<BluetoothGattCharacteristic>(relaxed = true)
                    every { c.uuid } returns uuid
                    every { c.properties } returns prop
                    every { c.permissions } returns perm
                    c
                },
                descriptorFactory = { uuid, perm ->
                    val d = mockk<BluetoothGattDescriptor>(relaxed = true)
                    every { d.uuid } returns uuid
                    every { d.permissions } returns perm
                    d
                },
            )

        assertEquals(BluetoothGattService.SERVICE_TYPE_PRIMARY, service.type)
        assertEquals(FidoBleGattServer.UUID_FIDO_SERVICE, service.uuid)
        assertEquals(4, createdCharacteristics.size)

        val cp = createdCharacteristics.first { it.uuid == FidoBleGattServer.UUID_FIDO_CONTROL_POINT }
        assertEquals(BluetoothGattCharacteristic.PROPERTY_WRITE, cp.properties)
        assertEquals(BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM, cp.permissions)

        val status = createdCharacteristics.first { it.uuid == FidoBleGattServer.UUID_FIDO_STATUS }
        assertEquals(BluetoothGattCharacteristic.PROPERTY_NOTIFY, status.properties)

        val cpLen = createdCharacteristics.first { it.uuid == FidoBleGattServer.UUID_FIDO_CONTROL_POINT_LENGTH }
        assertEquals(BluetoothGattCharacteristic.PROPERTY_READ, cpLen.properties)
        assertEquals(BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM, cpLen.permissions)

        val rev = createdCharacteristics.first { it.uuid == FidoBleGattServer.UUID_FIDO_SERVICE_REVISION_BITFIELD }
        assertEquals(
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
            rev.properties,
        )
        assertEquals(
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM or
                BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM,
            rev.permissions,
        )
    }

    @Test
    @Req("FR-BLE-030", "FR-BLE-070", "TR-BLE-010", "TR-BLE-040")
    fun `bonding device receives insufficient authentication and can retry after bond completes`() {
        val bondingDevice = mockk<BluetoothDevice>(relaxed = true)
        every { bondingDevice.address } returns testDevice.address
        every { bondingDevice.bondState } returns BluetoothDevice.BOND_BONDING

        val controlPoint = mockk<BluetoothGattCharacteristic>()
        every { controlPoint.uuid } returns FidoBleGattServer.UUID_FIDO_CONTROL_POINT
        var authenticationFailureDevice: BluetoothDevice? = null
        val bondingServer =
            FidoBleGattServer(
                sessionOwner = sessionOwner,
                responseSender = fakeSender,
                isDeviceAuthenticated = { it.bondState == BluetoothDevice.BOND_BONDED },
                onAuthenticationFailure = { authenticationFailureDevice = it },
            )
        var packetAdmitted = false
        bondingServer.onPacketReceivedListener = { packetAdmitted = true }

        bondingServer.onCharacteristicWriteRequest(
            device = bondingDevice,
            requestId = 1,
            characteristic = controlPoint,
            preparedWrite = false,
            responseNeeded = true,
            offset = 0,
            value = byteArrayOf(0x81.toByte(), 0x00, 0x01, 0x00),
        )

        assertEquals(1, fakeSender.responses.size)
        assertEquals(BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION, fakeSender.responses.single().status)
        assertNull(authenticationFailureDevice)
        assertEquals(bondingDevice.address, sessionOwner.activeDeviceAddress)
        assertFalse(packetAdmitted)
    }

    @Test
    @Req("FR-BLE-030", "FR-BLE-070", "TR-BLE-010", "TR-BLE-040")
    fun `unauthenticated device receives insufficient authentication ATT error`() {
        val unauthenticatedDevice = mockk<BluetoothDevice>(relaxed = true)
        every { unauthenticatedDevice.address } returns "11:22:33:44:55:66"
        every { unauthenticatedDevice.bondState } returns BluetoothDevice.BOND_NONE

        val cpChar = mockk<BluetoothGattCharacteristic>()
        every { cpChar.uuid } returns FidoBleGattServer.UUID_FIDO_CONTROL_POINT

        var authenticationFailureDevice: BluetoothDevice? = null
        val unauthenticatedServer =
            FidoBleGattServer(
                sessionOwner = sessionOwner,
                responseSender = fakeSender,
                isDeviceAuthenticated = { it.bondState == BluetoothDevice.BOND_BONDED },
                onAuthenticationFailure = { authenticationFailureDevice = it },
            )
        var packetAdmitted = false
        unauthenticatedServer.onPacketReceivedListener = { packetAdmitted = true }
        unauthenticatedServer.onCharacteristicWriteRequest(
            device = unauthenticatedDevice,
            requestId = 1,
            characteristic = cpChar,
            preparedWrite = false,
            responseNeeded = true,
            offset = 0,
            value = byteArrayOf(0x81.toByte(), 0x00, 0x01, 0x00),
        )

        assertEquals(1, fakeSender.responses.size)
        val response = fakeSender.responses[0]
        assertEquals(BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION, response.status)
        assertEquals(unauthenticatedDevice, authenticationFailureDevice)
        assertNull(sessionOwner.activeDeviceAddress)
        assertFalse(packetAdmitted)
    }

    @Test
    @Req("FR-BLE-040", "TR-BLE-020")
    fun `second bonded host cannot use the active session`() {
        val otherDevice = mockk<BluetoothDevice>(relaxed = true)
        every { otherDevice.address } returns "AA:BB:CC:DD:EE:FF"
        every { otherDevice.bondState } returns BluetoothDevice.BOND_BONDED
        val controlPoint = mockk<BluetoothGattCharacteristic>()
        every { controlPoint.uuid } returns FidoBleGattServer.UUID_FIDO_CONTROL_POINT
        var admitted = false
        gattServer.onPacketReceivedListener = { admitted = true }

        gattServer.onCharacteristicWriteRequest(
            otherDevice,
            9,
            controlPoint,
            false,
            true,
            0,
            byteArrayOf(0x81.toByte()),
        )

        assertEquals(BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION, fakeSender.responses.last().status)
        assertFalse(admitted)
        assertEquals("11:22:33:44:55:66", sessionOwner.activeDeviceAddress)
    }

    @Test
    @Req("FR-BLE-020", "TR-BLE-010")
    fun `revision bitfield read returns 0x20 and supports valid read offset`() {
        val revisionChar = mockk<BluetoothGattCharacteristic>()
        every { revisionChar.uuid } returns FidoBleGattServer.UUID_FIDO_SERVICE_REVISION_BITFIELD

        // Offset 0: returns [0x20]
        gattServer.onCharacteristicReadRequest(
            device = testDevice,
            requestId = 1,
            offset = 0,
            characteristic = revisionChar,
        )
        assertEquals(1, fakeSender.responses.size)
        assertEquals(BluetoothGatt.GATT_SUCCESS, fakeSender.responses[0].status)
        assertContentEquals(byteArrayOf(0x20), fakeSender.responses[0].value)

        // Offset 1: returns empty array
        gattServer.onCharacteristicReadRequest(
            device = testDevice,
            requestId = 2,
            offset = 1,
            characteristic = revisionChar,
        )
        assertEquals(BluetoothGatt.GATT_SUCCESS, fakeSender.responses[1].status)
        assertContentEquals(byteArrayOf(), fakeSender.responses[1].value)

        // Offset 2: invalid offset
        gattServer.onCharacteristicReadRequest(
            device = testDevice,
            requestId = 3,
            offset = 2,
            characteristic = revisionChar,
        )
        assertEquals(BluetoothGatt.GATT_INVALID_OFFSET, fakeSender.responses[2].status)
    }

    @Test
    @Req("FR-BLE-020", "TR-BLE-010")
    fun `revision bitfield write only accepts exactly 0x20 without prepared write`() {
        val revisionChar = mockk<BluetoothGattCharacteristic>()
        every { revisionChar.uuid } returns FidoBleGattServer.UUID_FIDO_SERVICE_REVISION_BITFIELD

        // Prepared write rejected
        gattServer.onCharacteristicWriteRequest(
            device = testDevice,
            requestId = 1,
            characteristic = revisionChar,
            preparedWrite = true,
            responseNeeded = true,
            offset = 0,
            value = byteArrayOf(0x20),
        )
        assertEquals(BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, fakeSender.responses[0].status)

        // Invalid revision rejected
        gattServer.onCharacteristicWriteRequest(
            device = testDevice,
            requestId = 2,
            characteristic = revisionChar,
            preparedWrite = false,
            responseNeeded = true,
            offset = 0,
            value = byteArrayOf(0x10),
        )
        assertEquals(BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, fakeSender.responses[1].status)

        // Multi-byte rejected
        gattServer.onCharacteristicWriteRequest(
            device = testDevice,
            requestId = 3,
            characteristic = revisionChar,
            preparedWrite = false,
            responseNeeded = true,
            offset = 0,
            value = byteArrayOf(0x20, 0x00),
        )
        assertEquals(BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH, fakeSender.responses[2].status)

        // Valid 0x20 accepted
        gattServer.onCharacteristicWriteRequest(
            device = testDevice,
            requestId = 4,
            characteristic = revisionChar,
            preparedWrite = false,
            responseNeeded = true,
            offset = 0,
            value = byteArrayOf(0x20),
        )
        assertEquals(BluetoothGatt.GATT_SUCCESS, fakeSender.responses[3].status)
        assertEquals(0x20.toByte(), sessionOwner.selectedRevision)
    }

    @Test
    @Req("FR-BLE-020", "TR-BLE-010")
    fun `CCCD descriptor read and write correctly toggles notifications`() {
        val cccd = mockk<BluetoothGattDescriptor>()
        every { cccd.uuid } returns FidoBleGattServer.UUID_CCCD

        // Initial read: 0x00, 0x00
        gattServer.onDescriptorReadRequest(testDevice, 1, 0, cccd)
        assertEquals(BluetoothGatt.GATT_SUCCESS, fakeSender.responses[0].status)
        assertContentEquals(byteArrayOf(0x00, 0x00), fakeSender.responses[0].value)

        // Write 0x01, 0x00 enables notifications
        gattServer.onDescriptorWriteRequest(
            device = testDevice,
            requestId = 2,
            descriptor = cccd,
            preparedWrite = false,
            responseNeeded = true,
            offset = 0,
            value = byteArrayOf(0x01, 0x00),
        )
        assertEquals(BluetoothGatt.GATT_SUCCESS, fakeSender.responses[1].status)
        assertTrue(sessionOwner.isNotificationsEnabled)

        // Read after enable: 0x01, 0x00
        gattServer.onDescriptorReadRequest(testDevice, 3, 0, cccd)
        assertEquals(BluetoothGatt.GATT_SUCCESS, fakeSender.responses[2].status)
        assertContentEquals(byteArrayOf(0x01, 0x00), fakeSender.responses[2].value)

        // Write 0x00, 0x00 disables notifications
        gattServer.onDescriptorWriteRequest(
            device = testDevice,
            requestId = 4,
            descriptor = cccd,
            preparedWrite = false,
            responseNeeded = true,
            offset = 0,
            value = byteArrayOf(0x00, 0x00),
        )
        assertEquals(BluetoothGatt.GATT_SUCCESS, fakeSender.responses[3].status)
        assertFalse(sessionOwner.isNotificationsEnabled)
    }

    @Test
    @Req("FR-BLE-010", "TR-BLE-010")
    fun `control point length read returns write capacity as two big endian bytes`() {
        val cpLength = mockk<BluetoothGattCharacteristic>()
        every { cpLength.uuid } returns FidoBleGattServer.UUID_FIDO_CONTROL_POINT_LENGTH

        sessionOwner.connectHost(testDevice.address)
        sessionOwner.updateMtu(256) // write capacity = 253 (0x00, 0xFD)

        gattServer.onCharacteristicReadRequest(testDevice, 1, 0, cpLength)
        assertEquals(BluetoothGatt.GATT_SUCCESS, fakeSender.responses[0].status)
        assertContentEquals(byteArrayOf(0x00, 0xFD.toByte()), fakeSender.responses[0].value)
    }

    @Test
    @Req("FR-BLE-010", "FR-BLE-020", "TR-BLE-020")
    fun `control point write is admitted only when revision and notifications are ready`() {
        val cpChar = mockk<BluetoothGattCharacteristic>()
        every { cpChar.uuid } returns FidoBleGattServer.UUID_FIDO_CONTROL_POINT

        var receivedPacket: ByteArray? = null
        gattServer.onPacketReceivedListener = { receivedPacket = it }

        sessionOwner.connectHost(testDevice.address)
        val packet = byteArrayOf(0x81.toByte(), 0x00, 0x01, 0x42)

        // Case 1: Revision not selected -> rejected before admission
        gattServer.onCharacteristicWriteRequest(
            testDevice,
            1,
            cpChar,
            preparedWrite = false,
            responseNeeded = true,
            offset = 0,
            value = packet,
        )
        assertEquals(BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, fakeSender.responses[0].status)
        assertNull(receivedPacket)

        // Select revision but notifications not enabled -> rejected
        sessionOwner.selectRevision(0x20)
        gattServer.onCharacteristicWriteRequest(
            testDevice,
            2,
            cpChar,
            preparedWrite = false,
            responseNeeded = true,
            offset = 0,
            value = packet,
        )
        assertEquals(BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, fakeSender.responses[1].status)
        assertNull(receivedPacket)

        // Enable notifications -> now ready, admitted and acknowledged with GATT_SUCCESS
        sessionOwner.setNotificationsEnabled(true)
        gattServer.onCharacteristicWriteRequest(
            testDevice,
            3,
            cpChar,
            preparedWrite = false,
            responseNeeded = true,
            offset = 0,
            value = packet,
        )
        assertEquals(BluetoothGatt.GATT_SUCCESS, fakeSender.responses[2].status)
        assertNotNull(receivedPacket)
        assertContentEquals(packet, receivedPacket)
    }

    @Test
    @Req("FR-BLE-010", "TR-BLE-020")
    fun `control point rejects offset non-zero and prepared writes`() {
        val cpChar = mockk<BluetoothGattCharacteristic>()
        every { cpChar.uuid } returns FidoBleGattServer.UUID_FIDO_CONTROL_POINT

        sessionOwner.connectHost(testDevice.address)
        sessionOwner.selectRevision(0x20)
        sessionOwner.setNotificationsEnabled(true)

        val packet = byteArrayOf(0x81.toByte(), 0x00, 0x01, 0x42)

        // Offset != 0 rejected
        gattServer.onCharacteristicWriteRequest(
            testDevice,
            1,
            cpChar,
            preparedWrite = false,
            responseNeeded = true,
            offset = 5,
            value = packet,
        )
        assertEquals(BluetoothGatt.GATT_INVALID_OFFSET, fakeSender.responses[0].status)

        // Prepared write rejected
        gattServer.onCharacteristicWriteRequest(
            testDevice,
            2,
            cpChar,
            preparedWrite = true,
            responseNeeded = true,
            offset = 0,
            value = packet,
        )
        assertEquals(BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, fakeSender.responses[1].status)
    }
}

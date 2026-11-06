package com.chimali.fido2.data.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import java.util.UUID

/**
 * Sender abstraction for GATT responses and notifications, decoupling callback admission from Android hardware.
 */
@Req("FR-BLE-010", "TR-BLE-010")
interface GattResponseSender {
    fun sendResponse(
        device: BluetoothDevice,
        requestId: Int,
        status: Int,
        offset: Int,
        value: ByteArray?,
    ): Boolean

    fun notifyCharacteristicChanged(
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        confirm: Boolean,
        value: ByteArray,
    ): Boolean
}

/**
 * Implements Android GATT server callback admission and explicit ATT responses per CTAP 2.2 FIDO BLE profile.
 */
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
class FidoBleGattServer(
    private val sessionOwner: BleSessionOwner,
    private val responseSender: GattResponseSender,
    private val isDeviceAuthenticated: (BluetoothDevice) -> Boolean = { it.bondState == BluetoothDevice.BOND_BONDED },
    private val notificationSentListener: (BluetoothDevice, Int) -> Unit = { _, _ -> },
    private val onConnectionStateChanged: (BluetoothDevice, Int, Int) -> Unit = { _, _, _ -> },
    private val onConnectionRejected: (BluetoothDevice) -> Unit = {},
    private val onAuthenticationFailure: (BluetoothDevice) -> Unit = {},
    private val onGattServiceAdded: (UUID, Int) -> Unit = { _, _ -> },
) : BluetoothGattServerCallback() {
    var statusCharacteristic =
        BluetoothGattCharacteristic(
            UUID_FIDO_STATUS,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0,
        )
        private set

    var onPacketReceivedListener: ((ByteArray) -> Unit)? = null

    companion object {
        private const val BYTE_SHIFT = 8
        private const val BYTE_MASK = 0xFF

        val UUID_FIDO_SERVICE: UUID = UUID.fromString("0000FFFD-0000-1000-8000-00805F9B34FB")
        val UUID_FIDO_CONTROL_POINT: UUID = UUID.fromString("F1D0FFF1-DEAA-ECEE-B42F-C9BA7ED623BB")
        val UUID_FIDO_STATUS: UUID = UUID.fromString("F1D0FFF2-DEAA-ECEE-B42F-C9BA7ED623BB")
        val UUID_FIDO_CONTROL_POINT_LENGTH: UUID = UUID.fromString("F1D0FFF3-DEAA-ECEE-B42F-C9BA7ED623BB")
        val UUID_FIDO_SERVICE_REVISION_BITFIELD: UUID = UUID.fromString("F1D0FFF4-DEAA-ECEE-B42F-C9BA7ED623BB")
        val UUID_CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        val UUID_DEVICE_INFORMATION_SERVICE: UUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")
        val UUID_MANUFACTURER_NAME_STRING: UUID = UUID.fromString("00002A29-0000-1000-8000-00805F9B34FB")
        val UUID_MODEL_NUMBER_STRING: UUID = UUID.fromString("00002A24-0000-1000-8000-00805F9B34FB")
        val UUID_FIRMWARE_REVISION_STRING: UUID = UUID.fromString("00002A26-0000-1000-8000-00805F9B34FB")

        private const val MANUFACTURER_NAME = "Chimali"
        private const val MODEL_NUMBER = "Chimali Authenticator"
        private const val FIRMWARE_REVISION = "1.0"
    }

    /**
     * Builds the primary FIDO BLE GATT service structure with required MITM permissions.
     */
    fun buildFidoGattService(
        serviceFactory: (UUID, Int) -> BluetoothGattService = { uuid, type -> BluetoothGattService(uuid, type) },
        characteristicFactory: (UUID, Int, Int) -> BluetoothGattCharacteristic = { uuid, prop, perm ->
            BluetoothGattCharacteristic(uuid, prop, perm)
        },
        descriptorFactory: (UUID, Int) -> BluetoothGattDescriptor = { uuid, perm ->
            BluetoothGattDescriptor(uuid, perm)
        },
    ): BluetoothGattService {
        val service = serviceFactory(UUID_FIDO_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // Control Point: Write only, encrypted MITM
        val controlPoint =
            characteristicFactory(
                UUID_FIDO_CONTROL_POINT,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM,
            )
        service.addCharacteristic(controlPoint)

        // Status: Notify only
        val status =
            characteristicFactory(
                UUID_FIDO_STATUS,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                0,
            ).also { statusCharacteristic = it }
        val cccd =
            descriptorFactory(
                UUID_CCCD,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED_MITM or
                    BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED_MITM,
            )
        status.addDescriptor(cccd)
        service.addCharacteristic(status)

        // Control Point Length: Read only, encrypted MITM
        val cpLength =
            characteristicFactory(
                UUID_FIDO_CONTROL_POINT_LENGTH,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM,
            )
        service.addCharacteristic(cpLength)

        // Service Revision Bitfield: Read/Write, encrypted MITM
        val revision =
            characteristicFactory(
                UUID_FIDO_SERVICE_REVISION_BITFIELD,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM or
                    BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM,
            )
        service.addCharacteristic(revision)

        return service
    }

    /** Builds the standard Device Information Service with generic values that do not identify a device. */
    fun buildDeviceInformationService(
        serviceFactory: (UUID, Int) -> BluetoothGattService = { uuid, type -> BluetoothGattService(uuid, type) },
        characteristicFactory: (UUID, Int, Int) -> BluetoothGattCharacteristic = { uuid, prop, perm ->
            BluetoothGattCharacteristic(uuid, prop, perm)
        },
    ): BluetoothGattService {
        val service = serviceFactory(UUID_DEVICE_INFORMATION_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        listOf(
            UUID_MANUFACTURER_NAME_STRING to MANUFACTURER_NAME,
            UUID_MODEL_NUMBER_STRING to MODEL_NUMBER,
            UUID_FIRMWARE_REVISION_STRING to FIRMWARE_REVISION,
        ).forEach { (uuid, value) ->
            val characteristic =
                characteristicFactory(
                    uuid,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ,
                ).also { it.setValue(value.toByteArray(Charsets.UTF_8)) }
            service.addCharacteristic(characteristic)
        }
        return service
    }

    override fun onConnectionStateChange(
        device: BluetoothDevice,
        status: Int,
        newState: Int,
    ) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            if (!sessionOwner.connectHost(device.address)) {
                onConnectionRejected(device)
                return
            }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            sessionOwner.disconnectHost(device.address)
        }
        onConnectionStateChanged(device, status, newState)
    }

    override fun onMtuChanged(
        device: BluetoothDevice,
        mtu: Int,
    ) {
        if (sessionOwner.activeDeviceAddress == device.address) sessionOwner.updateMtu(mtu)
    }

    override fun onServiceAdded(
        status: Int,
        service: BluetoothGattService,
    ) {
        onGattServiceAdded(service.uuid, status)
    }

    override fun onNotificationSent(
        device: BluetoothDevice,
        status: Int,
    ) {
        notificationSentListener(device, status)
    }

    override fun onCharacteristicReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic,
    ) {
        if (!isActiveHost(device)) {
            responseSender.sendResponse(device, requestId, BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION, offset, null)
            return
        }
        val deviceInformationValue =
            when (characteristic.uuid) {
                UUID_MANUFACTURER_NAME_STRING -> MANUFACTURER_NAME
                UUID_MODEL_NUMBER_STRING -> MODEL_NUMBER
                UUID_FIRMWARE_REVISION_STRING -> FIRMWARE_REVISION
                else -> null
            }
        if (deviceInformationValue != null) {
            val bytes = deviceInformationValue.toByteArray(Charsets.UTF_8)
            if (offset > bytes.size) {
                responseSender.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
            } else {
                responseSender.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    bytes.copyOfRange(offset, bytes.size),
                )
            }
            return
        }
        if (!isDeviceAuthenticated(device)) {
            rejectUnauthenticatedRequest(device, requestId, offset, responseNeeded = true)
            return
        }

        when (characteristic.uuid) {
            UUID_FIDO_CONTROL_POINT_LENGTH -> {
                val capacity = sessionOwner.writeCapacity
                val bytes =
                    byteArrayOf(
                        (capacity shr BYTE_SHIFT).toByte(),
                        (capacity and BYTE_MASK).toByte(),
                    )
                if (offset > bytes.size) {
                    responseSender.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                } else {
                    val slice = bytes.copyOfRange(offset, bytes.size)
                    responseSender.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
                }
            }
            UUID_FIDO_SERVICE_REVISION_BITFIELD -> {
                val bytes = byteArrayOf(FidoBleFraming.REVISION_FIDO2)
                if (offset > bytes.size) {
                    responseSender.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                } else {
                    val slice = bytes.copyOfRange(offset, bytes.size)
                    responseSender.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
                }
            }
            else -> {
                responseSender.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
            }
        }
    }

    override fun onCharacteristicWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        characteristic: BluetoothGattCharacteristic,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray?,
    ) {
        if (!isActiveHost(device)) {
            if (responseNeeded) {
                responseSender.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION,
                    offset,
                    null,
                )
            }
            return
        }
        if (!isDeviceAuthenticated(device)) {
            rejectUnauthenticatedRequest(device, requestId, offset, responseNeeded)
            return
        }

        if (preparedWrite) {
            if (responseNeeded) {
                responseSender.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
            }
            return
        }

        if (offset != 0) {
            if (responseNeeded) {
                responseSender.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
            }
            return
        }

        val payload = value ?: byteArrayOf()

        when (characteristic.uuid) {
            UUID_FIDO_SERVICE_REVISION_BITFIELD -> {
                if (payload.size != 1) {
                    if (responseNeeded) {
                        responseSender.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH,
                            offset,
                            null,
                        )
                    }
                    return
                }
                if (payload[0] != 0x20.toByte()) {
                    if (responseNeeded) {
                        responseSender.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                            offset,
                            null,
                        )
                    }
                    return
                }
                sessionOwner.selectRevision(payload[0])
                if (responseNeeded) {
                    responseSender.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                }
            }
            UUID_FIDO_CONTROL_POINT -> {
                if (!sessionOwner.isCommandReady) {
                    if (responseNeeded) {
                        responseSender.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                            offset,
                            null,
                        )
                    }
                    return
                }
                if (payload.size > sessionOwner.writeCapacity) {
                    if (responseNeeded) {
                        responseSender.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH,
                            offset,
                            null,
                        )
                    }
                    return
                }
                if (responseNeeded) {
                    responseSender.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                }
                onPacketReceivedListener?.invoke(payload)
            }
            else -> {
                if (responseNeeded) {
                    responseSender.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                        offset,
                        null,
                    )
                }
            }
        }
    }

    override fun onDescriptorReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        descriptor: BluetoothGattDescriptor,
    ) {
        if (!isActiveHost(device)) {
            responseSender.sendResponse(device, requestId, BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION, offset, null)
            return
        }
        if (!isDeviceAuthenticated(device)) {
            rejectUnauthenticatedRequest(device, requestId, offset, responseNeeded = true)
            return
        }

        if (descriptor.uuid == UUID_CCCD) {
            val bytes =
                if (sessionOwner.isNotificationsEnabled) {
                    byteArrayOf(0x01, 0x00)
                } else {
                    byteArrayOf(0x00, 0x00)
                }
            if (offset > bytes.size) {
                responseSender.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
            } else {
                val slice = bytes.copyOfRange(offset, bytes.size)
                responseSender.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
            }
        } else {
            responseSender.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
        }
    }

    override fun onDescriptorWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        descriptor: BluetoothGattDescriptor,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray?,
    ) {
        if (!isActiveHost(device)) {
            if (responseNeeded) {
                responseSender.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION,
                    offset,
                    null,
                )
            }
            return
        }
        if (!isDeviceAuthenticated(device)) {
            rejectUnauthenticatedRequest(device, requestId, offset, responseNeeded)
            return
        }

        if (preparedWrite || offset != 0) {
            if (responseNeeded) {
                responseSender.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
            }
            return
        }

        if (descriptor.uuid == UUID_CCCD) {
            val payload = value ?: byteArrayOf()
            if (payload.contentEquals(byteArrayOf(0x01, 0x00))) {
                sessionOwner.setNotificationsEnabled(true)
                if (responseNeeded) {
                    responseSender.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                }
            } else if (payload.contentEquals(byteArrayOf(0x00, 0x00))) {
                sessionOwner.setNotificationsEnabled(false)
                if (responseNeeded) {
                    responseSender.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                }
            } else {
                if (responseNeeded) {
                    responseSender.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                        offset,
                        null,
                    )
                }
            }
        } else {
            if (responseNeeded) {
                responseSender.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                    offset,
                    null,
                )
            }
        }
    }

    private fun isActiveHost(device: BluetoothDevice): Boolean = sessionOwner.activeDeviceAddress == device.address

    private fun rejectUnauthenticatedRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        responseNeeded: Boolean,
    ) {
        val bondState = device.bondState
        if (responseNeeded) {
            responseSender.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION,
                offset,
                null,
            )
        }
        Logger.w { "FidoBleGattServer: rejected unauthenticated GATT request (bondState=$bondState)" }
        if (bondState == BluetoothDevice.BOND_BONDING) {
            // Pairing is still in progress. Keep the GATT session so the host can retry after bonding completes.
            return
        }
        sessionOwner.disconnectHost(device.address)
        onAuthenticationFailure(device)
    }
}

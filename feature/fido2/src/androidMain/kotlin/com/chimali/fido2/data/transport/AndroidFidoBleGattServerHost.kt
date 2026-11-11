package com.chimali.fido2.data.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Owns the Android GATT server and registers the FIDO service before advertising starts. */
@Req("FR-BLE-010", "FR-BLE-020", "FR-BLE-070", "TR-BLE-010", "TR-BLE-040")
interface BleGattServerHost {
    suspend fun start(
        onPacketReceived: (ByteArray) -> Unit,
        onNotificationSent: (BluetoothDevice, Int) -> Unit,
        onConnectionStateChanged: (BluetoothDevice, Int, Int) -> Unit,
        onAuthenticationFailure: (BluetoothDevice) -> Unit,
    ): Result<Unit>

    fun stop()
}

private data class PendingLegacyNotification(
    val deviceAddress: String,
    val characteristic: BluetoothGattCharacteristic,
    val value: ByteArray,
)

/** Android public-API GATT server host used by the selected BLE transport. */
@Req("FR-BLE-010", "FR-BLE-020", "FR-BLE-070", "TR-BLE-010", "TR-BLE-040")
class AndroidFidoBleGattServerHost(
    private val context: Context,
    private val sessionOwner: BleSessionOwner,
) : BleGattServerHost,
    GattResponseSender {
    private var gattServer: BluetoothGattServer? = null
    private var registered = false
    private val serviceRegistrations = ConcurrentHashMap<UUID, CompletableDeferred<Int>>()
    private var notificationSentListener: (BluetoothDevice, Int) -> Unit = { _, _ -> }
    private var connectionStateListener: (BluetoothDevice, Int, Int) -> Unit = { _, _, _ -> }
    private var authenticationFailureListener: (BluetoothDevice) -> Unit = {}
    private val legacyNotificationLock = Any()
    private var legacyNotificationValue: PendingLegacyNotification? = null
    private val callback by lazy {
        FidoBleGattServer(
            sessionOwner = sessionOwner,
            responseSender = this,
            onGattServiceAdded = { uuid, status ->
                serviceRegistrations[uuid]?.complete(status)
            },
            notificationSentListener = { device, status ->
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Logger.w { "AndroidFidoBleGattServerHost: GATT notification callback failed status=$status" }
                }
                releaseLegacyNotificationValue(expectedDeviceAddress = device.address)
                notificationSentListener(device, status)
            },
            onConnectionStateChanged = { device, status, state ->
                if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    releaseLegacyNotificationValue(expectedDeviceAddress = device.address)
                }
                connectionStateListener(device, status, state)
            },
            onConnectionRejected = { device -> gattServer?.cancelConnection(device) },
            onAuthenticationFailure = { device ->
                releaseLegacyNotificationValue(expectedDeviceAddress = device.address)
                authenticationFailureListener(device)
                gattServer?.cancelConnection(device)
            },
        )
    }
    private val service by lazy { callback.buildFidoGattService() }
    private val deviceInformationService by lazy { callback.buildDeviceInformationService() }
    val statusCharacteristic: BluetoothGattCharacteristic
        get() {
            service
            return callback.statusCharacteristic
        }

    override suspend fun start(
        onPacketReceived: (ByteArray) -> Unit,
        onNotificationSent: (BluetoothDevice, Int) -> Unit,
        onConnectionStateChanged: (BluetoothDevice, Int, Int) -> Unit,
        onAuthenticationFailure: (BluetoothDevice) -> Unit,
    ): Result<Unit> {
        if (registered && gattServer != null) return Result.success(Unit)

        return try {
            notificationSentListener = onNotificationSent
            connectionStateListener = onConnectionStateChanged
            authenticationFailureListener = onAuthenticationFailure
            callback.onPacketReceivedListener = onPacketReceived
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val server =
                manager.openGattServer(context, callback)
                    ?: error("Android did not provide a Bluetooth GATT server")
            gattServer = server
            registerService(server, service)
            registerService(server, deviceInformationService)
            registered = true
            Result.success(Unit)
        } catch (failure: CancellationException) {
            stop()
            throw failure
        } catch (failure: TimeoutException) {
            startupFailure(failure)
        } catch (failure: SecurityException) {
            startupFailure(failure)
        } catch (failure: IllegalStateException) {
            startupFailure(failure)
        }
    }

    private suspend fun registerService(
        server: BluetoothGattServer,
        service: android.bluetooth.BluetoothGattService,
    ) {
        val serviceReady = CompletableDeferred<Int>()
        check(serviceRegistrations.putIfAbsent(service.uuid, serviceReady) == null) {
            "GATT service registration is already pending for ${service.uuid}"
        }
        try {
            check(server.addService(service)) {
                "Android rejected GATT service registration for ${service.uuid}"
            }
            val status =
                withTimeoutOrNull(SERVICE_REGISTRATION_TIMEOUT_MS) { serviceReady.await() }
                    ?: throw TimeoutException("GATT service registration timed out for ${service.uuid}")
            check(status == BluetoothGatt.GATT_SUCCESS) {
                "GATT service registration failed for ${service.uuid} with status $status"
            }
        } finally {
            serviceRegistrations.remove(service.uuid, serviceReady)
        }
    }

    private fun startupFailure(failure: Exception): Result<Unit> {
        stop()
        Logger.e(failure) { "AndroidFidoBleGattServerHost: GATT server startup failed" }
        return Result.failure(failure)
    }

    override fun sendResponse(
        device: BluetoothDevice,
        requestId: Int,
        status: Int,
        offset: Int,
        value: ByteArray?,
    ): Boolean = gattServer?.sendResponse(device, requestId, status, offset, value) ?: false

    override fun notifyCharacteristicChanged(
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        confirm: Boolean,
        value: ByteArray,
    ): Boolean {
        val server = gattServer ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = server.notifyCharacteristicChanged(device, characteristic, confirm, value)
            if (status != BluetoothStatusCodes.SUCCESS) {
                Logger.w { "AndroidFidoBleGattServerHost: GATT notification initiation failed status=$status" }
            }
            return status == BluetoothStatusCodes.SUCCESS
        }

        val ownedValue = value.copyOf()
        synchronized(legacyNotificationLock) {
            if (legacyNotificationValue != null) {
                ownedValue.fill(0)
                Logger.w { "AndroidFidoBleGattServerHost: Legacy GATT notification already pending" }
                return false
            }
            legacyNotificationValue = PendingLegacyNotification(device.address, characteristic, ownedValue)
            @Suppress("DEPRECATION")
            characteristic.value = ownedValue
        }

        var initiated = false
        try {
            @Suppress("DEPRECATION")
            initiated = server.notifyCharacteristicChanged(device, characteristic, confirm)
            if (!initiated) {
                Logger.w { "AndroidFidoBleGattServerHost: Legacy GATT notification initiation was rejected" }
            }
            return initiated
        } finally {
            if (!initiated) {
                releaseLegacyNotificationValue(
                    expectedDeviceAddress = device.address,
                    expectedCharacteristic = characteristic,
                    expectedValue = ownedValue,
                )
            }
        }
    }

    private fun releaseLegacyNotificationValue(
        expectedDeviceAddress: String? = null,
        expectedCharacteristic: BluetoothGattCharacteristic? = null,
        expectedValue: ByteArray? = null,
    ) {
        val retained =
            synchronized(legacyNotificationLock) {
                val pending = legacyNotificationValue ?: return
                val sameDevice = expectedDeviceAddress == null || pending.deviceAddress == expectedDeviceAddress
                val sameCharacteristic =
                    expectedCharacteristic == null || pending.characteristic === expectedCharacteristic
                val sameValue = expectedValue == null || pending.value === expectedValue
                if (!sameDevice || !sameCharacteristic || !sameValue) {
                    return
                }
                legacyNotificationValue = null
                pending
            }
        retained.value.fill(0)
        @Suppress("DEPRECATION")
        retained.characteristic.value = null
    }

    override fun stop() {
        val server = gattServer
        gattServer = null
        registered = false
        serviceRegistrations.values.forEach { it.cancel() }
        serviceRegistrations.clear()
        notificationSentListener = { _, _ -> }
        connectionStateListener = { _, _, _ -> }
        try {
            server?.clearServices()
            server?.close()
        } catch (failure: SecurityException) {
            Logger.w(failure) { "AndroidFidoBleGattServerHost: Permission lost during GATT cleanup" }
        } finally {
            releaseLegacyNotificationValue()
        }
    }

    private companion object {
        const val SERVICE_REGISTRATION_TIMEOUT_MS = 5_000L
    }
}

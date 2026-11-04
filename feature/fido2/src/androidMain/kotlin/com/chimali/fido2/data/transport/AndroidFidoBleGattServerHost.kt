package com.chimali.fido2.data.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothManager
import android.content.Context
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
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

/** Android public-API GATT server host used by the selected BLE transport. */
@Req("FR-BLE-010", "FR-BLE-020", "FR-BLE-070", "TR-BLE-010", "TR-BLE-040")
class AndroidFidoBleGattServerHost(
    private val context: Context,
    private val sessionOwner: BleSessionOwner,
) : BleGattServerHost,
    GattResponseSender {
    private var gattServer: BluetoothGattServer? = null
    private var registered = false
    private var serviceRegistration: CompletableDeferred<Int>? = null
    private var notificationSentListener: (BluetoothDevice, Int) -> Unit = { _, _ -> }
    private var connectionStateListener: (BluetoothDevice, Int, Int) -> Unit = { _, _, _ -> }
    private var authenticationFailureListener: (BluetoothDevice) -> Unit = {}
    private val callback by lazy {
        FidoBleGattServer(
            sessionOwner = sessionOwner,
            responseSender = this,
            onServiceAdded = { status -> serviceRegistration?.complete(status) },
            onNotificationSent = { device, status -> notificationSentListener(device, status) },
            onConnectionStateChanged = { device, status, state -> connectionStateListener(device, status, state) },
            onConnectionRejected = { device -> gattServer?.cancelConnection(device) },
            onAuthenticationFailure = { device ->
                authenticationFailureListener(device)
                gattServer?.cancelConnection(device)
            },
        )
    }
    private val service by lazy { callback.buildFidoGattService() }
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
            val serviceReady = CompletableDeferred<Int>()
            serviceRegistration = serviceReady
            notificationSentListener = onNotificationSent
            connectionStateListener = onConnectionStateChanged
            authenticationFailureListener = onAuthenticationFailure
            callback.onPacketReceivedListener = onPacketReceived
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val server =
                manager.openGattServer(context, callback)
                    ?: error("Android did not provide a Bluetooth GATT server")
            gattServer = server
            check(server.addService(service)) {
                "Android rejected FIDO GATT service registration"
            }
            val status =
                withTimeoutOrNull(SERVICE_REGISTRATION_TIMEOUT_MS) { serviceReady.await() }
                    ?: throw TimeoutException("FIDO GATT service registration timed out")
            check(status == BluetoothGatt.GATT_SUCCESS) {
                "FIDO GATT service registration failed with status $status"
            }
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
    ): Boolean =
        gattServer?.let {
            AndroidGattResponseSender(
                it,
            ).notifyCharacteristicChanged(device, characteristic, confirm, value)
        }
            ?: false

    override fun stop() {
        val server = gattServer
        gattServer = null
        registered = false
        serviceRegistration?.cancel()
        serviceRegistration = null
        notificationSentListener = { _, _ -> }
        connectionStateListener = { _, _, _ -> }
        try {
            server?.clearServices()
            server?.close()
        } catch (failure: SecurityException) {
            Logger.w(failure) { "AndroidFidoBleGattServerHost: Permission lost during GATT cleanup" }
        }
    }

    private companion object {
        const val SERVICE_REGISTRATION_TIMEOUT_MS = 5_000L
    }
}

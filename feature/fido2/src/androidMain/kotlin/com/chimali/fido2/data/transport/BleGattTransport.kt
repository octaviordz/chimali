package com.chimali.fido2.data.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Controller abstraction for starting and stopping BLE advertising.
 */
@Req("FR-BLE-010", "TR-BLE-010")
interface BleAdvertiserController {
    suspend fun startAdvertising(
        serviceUuid: UUID,
        onUnexpectedFailure: (Throwable) -> Unit = {},
    ): Result<Unit>

    fun stopAdvertising()
}

/**
 * Concrete FIDO BLE GATT Transport implementing [Fido2Transport].
 */
@Req("FR-BLE-010", "FR-BLE-050", "FR-BLE-060", "FR-BLE-080", "TR-BLE-010", "TR-BLE-020", "TR-BLE-030")
class BleGattTransport(
    private val rawCtapOperation: RawCtapOperation,
    val sessionOwner: BleSessionOwner = BleSessionOwner(),
    private val advertiserController: BleAdvertiserController,
    private val responseSender: GattResponseSender,
    private val coroutineScope: CoroutineScope,
    private val gattServerHost: BleGattServerHost? = null,
    private val deviceLookup: (String) -> BluetoothDevice? = { address ->
        try {
            android.bluetooth.BluetoothAdapter
                .getDefaultAdapter()
                ?.getRemoteDevice(address)
        } catch (_: Throwable) {
            null
        }
    },
    private val statusCharacteristic: BluetoothGattCharacteristic =
        BluetoothGattCharacteristic(
            FidoBleGattServer.UUID_FIDO_STATUS,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0,
        ),
) : Fido2Transport {
    companion object {
        const val CMD_PING: Byte = 0x81.toByte()
        const val CMD_KEEPALIVE: Byte = 0x82.toByte()
        const val CMD_MSG: Byte = 0x83.toByte()
        const val CMD_CANCEL: Byte = 0xBE.toByte()
        const val CMD_ERROR: Byte = 0xBF.toByte()

        const val ERR_INVALID_CMD: Byte = 0x01
    }

    private val _connectionState = MutableStateFlow<Fido2ConnectionState>(Fido2ConnectionState.Idle)
    override val connectionState: StateFlow<Fido2ConnectionState> = _connectionState.asStateFlow()

    private val lifecycleMutex = Mutex()
    private var isStarted = false
    private var currentMsgJob: Job? = null
    private var activeHostAddress: String? = null

    private val notificationWriter: BleNotificationWriter =
        BleNotificationWriter(
            scope = coroutineScope,
            sendGattNotification = { generation, packet ->
                var initiated = false
                sessionOwner.runForCurrentSession(generation) { deviceAddress ->
                    val device = deviceLookup(deviceAddress)
                    if (device != null) {
                        initiated =
                            responseSender.notifyCharacteristicChanged(
                                device,
                                statusCharacteristic,
                                false,
                                packet,
                            )
                    }
                }
                initiated
            },
            isSessionCurrent = sessionOwner::isSessionCurrent,
            onSessionStalled = { generation ->
                Logger.w { "BleGattTransport: Notification stalled for 5 seconds; aborting session" }
                sessionOwner.runForCurrentSession(generation) { activeDevice ->
                    sessionOwner.disconnectHost(activeDevice)
                    if (activeHostAddress == activeDevice) activeHostAddress = null
                    _connectionState.value = Fido2ConnectionState.Error("Notification stall timeout")
                }
            },
        )

    @Req("FR-BLE-010", "TR-BLE-010")
    override suspend fun connect(): Result<Unit> =
        lifecycleMutex.withLock {
            if (isStarted) return Result.success(Unit)

            val gattResult =
                gattServerHost?.start(
                    onPacketReceived = ::onPacketReceived,
                    onNotificationSent = ::onHostNotificationSent,
                    onConnectionStateChanged = ::onHostConnectionStateChanged,
                    onAuthenticationFailure = { onHostAuthenticationFailure() },
                ) ?: Result.success(Unit)
            if (gattResult.isFailure) {
                _connectionState.value = Fido2ConnectionState.Error("Failed to register FIDO GATT service")
                return gattResult
            }

            val result =
                advertiserController.startAdvertising(FidoBleGattServer.UUID_FIDO_SERVICE) { failure ->
                    if (isStarted) {
                        Logger.e(failure) { "BleGattTransport: BLE advertising stopped unexpectedly" }
                        _connectionState.value = Fido2ConnectionState.Error("BLE advertising stopped unexpectedly")
                    }
                }
            if (result.isSuccess) {
                isStarted = true
                _connectionState.value = Fido2ConnectionState.Advertising
            } else {
                gattServerHost?.stop()
                _connectionState.value = Fido2ConnectionState.Error("Failed to start advertising")
            }
            result
        }

    @Req("FR-BLE-080", "FR-BT-SEL-040")
    override suspend fun connectDevice(macAddress: String): Result<Unit> =
        Result.failure(UnsupportedOutboundConnectionException())

    @Req("FR-BLE-010", "TR-BLE-010")
    override suspend fun disconnect(): Result<Unit> =
        lifecycleMutex.withLock {
            isStarted = false
            advertiserController.stopAdvertising()
            gattServerHost?.stop()
            cancelCurrentMessage()
            val activeDevice = sessionOwner.activeDeviceAddress
            if (activeDevice != null) {
                sessionOwner.disconnectHost(activeDevice)
            }
            activeHostAddress = null
            isStarted = false
            _connectionState.value = Fido2ConnectionState.Idle
            Result.success(Unit)
        }

    /**
     * Called when a GATT Control Point packet is written by the connected host.
     */
    @Req("FR-BLE-050", "TR-BLE-020", "TR-BLE-030")
    fun onPacketReceived(packet: ByteArray) {
        val message = sessionOwner.processIncomingWrite(packet) ?: return
        if (!sessionOwner.isSessionCurrent(message.sessionGeneration)) return
        dispatchMessage(message)
    }

    private fun dispatchMessage(message: FidoBleMessage) {
        when (message.command) {
            CMD_PING -> {
                val fragments = FidoBleFraming.fragmentResponse(CMD_PING, message.data, sessionOwner.notifyCapacity)
                for (frag in fragments) {
                    notificationWriter.enqueue(frag, isKeepalive = false, sessionGeneration = message.sessionGeneration)
                }
            }
            CMD_CANCEL -> {
                if (message.data.isNotEmpty()) {
                    Logger.w { "BleGattTransport: Malformed CMD_CANCEL with non-empty payload" }
                    val errFragments =
                        FidoBleFraming.fragmentResponse(
                            CMD_ERROR,
                            byteArrayOf(FidoBleFraming.ERR_INVALID_LEN),
                            sessionOwner.notifyCapacity,
                        )
                    for (frag in errFragments) {
                        notificationWriter.enqueue(
                            frag,
                            isKeepalive = false,
                            sessionGeneration = message.sessionGeneration,
                        )
                    }
                    return
                }
                Logger.i { "BleGattTransport: CMD_CANCEL received; cancelling current operation" }
                cancelCurrentMessage()
                // CANCEL itself receives no response per CTAP 2.2 spec
            }
            CMD_MSG -> {
                if (currentMsgJob?.isActive == true) {
                    Logger.w { "BleGattTransport: Busy; rejecting overlapping CMD_MSG" }
                    val errFragments =
                        FidoBleFraming.fragmentResponse(
                            CMD_ERROR,
                            byteArrayOf(FidoBleFraming.ERR_BUSY),
                            sessionOwner.notifyCapacity,
                        )
                    for (frag in errFragments) {
                        notificationWriter.enqueue(
                            frag,
                            isKeepalive = false,
                            sessionGeneration = message.sessionGeneration,
                        )
                    }
                    return
                }

                val messageJob =
                    coroutineScope.launch(start = CoroutineStart.LAZY) {
                        try {
                            val result =
                                executeCtapOperation(message.data) { status ->
                                    val keepalivePayload = byteArrayOf(status)
                                    val keepaliveFragments =
                                        FidoBleFraming.fragmentResponse(
                                            CMD_KEEPALIVE,
                                            keepalivePayload,
                                            sessionOwner.notifyCapacity,
                                        )
                                    if (sessionOwner.isSessionCurrent(message.sessionGeneration)) {
                                        notificationWriter.enqueue(
                                            keepaliveFragments[0],
                                            isKeepalive = true,
                                            sessionGeneration = message.sessionGeneration,
                                        )
                                    }
                                }

                            notificationWriter.markResponseTransmissionStarted(message.sessionGeneration)

                            val fragments =
                                FidoBleFraming.fragmentResponse(
                                    CMD_MSG,
                                    result.responseBytes,
                                    sessionOwner.notifyCapacity,
                                )
                            for (fragment in fragments) {
                                if (!sessionOwner.isSessionCurrent(message.sessionGeneration)) break
                                notificationWriter.enqueue(
                                    fragment,
                                    isKeepalive = false,
                                    sessionGeneration = message.sessionGeneration,
                                )
                            }
                        } finally {
                            if (currentMsgJob === coroutineContext[Job]) {
                                currentMsgJob = null
                            }
                        }
                    }
                currentMsgJob = messageJob
                messageJob.start()
            }
            else -> {
                val errFragments =
                    FidoBleFraming.fragmentResponse(
                        CMD_ERROR,
                        byteArrayOf(ERR_INVALID_CMD),
                        sessionOwner.notifyCapacity,
                    )
                for (frag in errFragments) {
                    notificationWriter.enqueue(frag, isKeepalive = false, sessionGeneration = message.sessionGeneration)
                }
            }
        }
    }

    /** Converts unexpected handler failures into a CTAP error so the BLE host is not left waiting. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun executeCtapOperation(
        request: ByteArray,
        onKeepalive: (Byte) -> Unit,
    ): RawCtapResult =
        try {
            rawCtapOperation.handle(request, onKeepalive)
        } catch (failure: CancellationException) {
            Logger.i { "BleGattTransport: In-flight CTAP command was cancelled" }
            throw failure
        } catch (failure: Exception) {
            Logger.e(failure) { "BleGattTransport: CTAP operation failed; returning CTAP2_ERR_OTHER" }
            RawCtapResult(byteArrayOf(RawCtapOperationImpl.CTAP2_ERR_OTHER))
        }

    /**
     * Called when Android GATT callback acknowledges notification delivery.
     */
    fun onNotificationSent(status: Int = android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
        notificationWriter.onNotificationSent(status == android.bluetooth.BluetoothGatt.GATT_SUCCESS)
    }

    /**
     * Convenience overload accepting a delivery success boolean.
     */
    fun onNotificationSent(success: Boolean) {
        notificationWriter.onNotificationSent(success)
    }

    private fun onHostNotificationSent(
        device: BluetoothDevice,
        status: Int,
    ) {
        if (activeHostAddress != device.address) {
            Logger.d { "BleGattTransport: Ignoring notification callback from inactive peer ${device.address}" }
            return
        }
        notificationWriter.onNotificationSent(status == BluetoothGatt.GATT_SUCCESS)
    }

    @Req("FR-BLE-070", "TR-BLE-040")
    private fun onHostConnectionStateChanged(
        device: BluetoothDevice,
        status: Int,
        newState: Int,
    ) {
        when (newState) {
            BluetoothProfile.STATE_CONNECTING -> {
                advertiserController.stopAdvertising()
                _connectionState.value = Fido2ConnectionState.Connecting(device)
            }
            BluetoothProfile.STATE_CONNECTED -> {
                advertiserController.stopAdvertising()
                activeHostAddress = device.address
                _connectionState.value = Fido2ConnectionState.Connected(device)
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                if (activeHostAddress != null && activeHostAddress != device.address) {
                    Logger.d { "BleGattTransport: Ignoring stale disconnect callback from ${device.address}" }
                    return
                }
                if (activeHostAddress == device.address) {
                    activeHostAddress = null
                    cancelCurrentMessage()
                }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    _connectionState.value = Fido2ConnectionState.Error("Bluetooth connection or pairing failed")
                } else if (_connectionState.value is Fido2ConnectionState.Error) {
                    // Keep pairing guidance visible until the user explicitly retries after recovery.
                } else if (isStarted) {
                    coroutineScope.launch {
                        val result =
                            advertiserController.startAdvertising(FidoBleGattServer.UUID_FIDO_SERVICE) { failure ->
                                if (isStarted) {
                                    Logger.e(failure) { "BleGattTransport: Resumed BLE advertising stopped" }
                                    _connectionState.value =
                                        Fido2ConnectionState.Error("BLE advertising stopped unexpectedly")
                                }
                            }
                        if (isStarted) {
                            _connectionState.value =
                                if (result.isSuccess) {
                                    Fido2ConnectionState.Advertising
                                } else {
                                    Fido2ConnectionState.Error("Failed to resume advertising")
                                }
                        }
                    }
                } else {
                    _connectionState.value = Fido2ConnectionState.Idle
                }
            }
        }
    }

    @Req("FR-BLE-070", "TR-BLE-040")
    private fun onHostAuthenticationFailure() {
        activeHostAddress = null
        cancelCurrentMessage()
        advertiserController.stopAdvertising()
        _connectionState.value = Fido2ConnectionState.Error("Bluetooth pairing authentication failed")
    }

    private fun cancelCurrentMessage() {
        val messageJob = currentMsgJob
        currentMsgJob = null
        messageJob?.cancel()
        notificationWriter.clear()
    }
}

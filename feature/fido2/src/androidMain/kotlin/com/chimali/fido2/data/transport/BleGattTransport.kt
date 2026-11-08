package com.chimali.fido2.data.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    /** Stops Bluetooth state observation after the transport is disconnected. */
    fun stopObservingAdapterState() = Unit
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

        private const val CTAP2_CMD_MAKE_CREDENTIAL = 0x01
        private const val CTAP2_CMD_GET_ASSERTION = 0x02
        private const val CTAP2_CMD_GET_INFO = 0x04
        private const val CTAP2_CMD_RESET = 0x07
        private const val BYTE_MASK = 0xFF
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private const val RECEIVE_TIMEOUT_MILLIS = BleSessionOwner.RECEIVE_TIMEOUT_NANOS / NANOS_PER_MILLISECOND
        private const val KEEPALIVE_INITIAL_DELAY_MILLIS = 100L
        private const val KEEPALIVE_PERIOD_MILLIS = 250L
        private const val KEEPALIVE_STATUS_PROCESSING = 0x01
    }

    private val _connectionState = MutableStateFlow<Fido2ConnectionState>(Fido2ConnectionState.Idle)
    override val connectionState: StateFlow<Fido2ConnectionState> = _connectionState.asStateFlow()

    private val lifecycleMutex = Mutex()
    private var isStarted = false
    private var currentMsgJob: Job? = null
    private var receiveTimeoutJob: Job? = null
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
                    isStarted = false
                    advertiserController.stopAdvertising()
                    gattServerHost?.stop()
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
                        onBluetoothUnavailable(failure)
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
            cancelReceiveTimeout()
            advertiserController.stopAdvertising()
            advertiserController.stopObservingAdapterState()
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
        when (val result = sessionOwner.processIncomingWrite(packet)) {
            is BleIncomingWriteResult.Complete -> {
                cancelReceiveTimeout()
                val message = result.message
                if (sessionOwner.isSessionCurrent(message.sessionGeneration)) dispatchMessage(message)
            }
            is BleIncomingWriteResult.Error -> {
                cancelReceiveTimeout()
                if (sessionOwner.isSessionCurrent(result.sessionGeneration)) {
                    enqueueErrorResponse(result.sessionGeneration, result.errorCode)
                }
            }
            is BleIncomingWriteResult.InProgress -> scheduleReceiveTimeout(result)
            BleIncomingWriteResult.NotReady -> cancelReceiveTimeout()
        }
    }

    private fun scheduleReceiveTimeout(result: BleIncomingWriteResult.InProgress) {
        cancelReceiveTimeout()
        receiveTimeoutJob =
            coroutineScope.launch {
                delay(RECEIVE_TIMEOUT_MILLIS)
                val timeout = sessionOwner.expireIncompleteRequest(result.sessionGeneration, result.timeoutToken)
                if (timeout != null && sessionOwner.isSessionCurrent(timeout.sessionGeneration)) {
                    enqueueErrorResponse(timeout.sessionGeneration, timeout.errorCode)
                }
                receiveTimeoutJob = null
            }
    }

    private fun cancelReceiveTimeout() {
        receiveTimeoutJob?.cancel()
        receiveTimeoutJob = null
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
            CMD_MSG -> dispatchCtapMessage(message)
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

    private fun dispatchCtapMessage(message: FidoBleMessage) {
        if (currentMsgJob?.isActive == true) {
            Logger.w { "BleGattTransport: Busy; rejecting overlapping CMD_MSG" }
            enqueueErrorResponse(message.sessionGeneration, FidoBleFraming.ERR_BUSY)
            return
        }

        val messageJob =
            coroutineScope.launch(start = CoroutineStart.LAZY) {
                try {
                    processCtapMessage(message)
                } finally {
                    if (currentMsgJob === coroutineContext[Job]) currentMsgJob = null
                }
            }
        currentMsgJob = messageJob
        messageJob.start()
    }

    @Req("FR-BLE-060", "TR-BLE-020")
    private suspend fun processCtapMessage(message: FidoBleMessage) {
        val startedAtNanos = System.nanoTime()
        val operation = diagnosticOperationName(message.data)
        Logger.d { "FIDO BLE diagnostic started operation=$operation" }
        val keepaliveStatus = AtomicInteger(KEEPALIVE_STATUS_PROCESSING)
        // Begin after a short grace period for fast CTAP commands, then keep the host informed at <500ms intervals.
        val keepaliveJob =
            coroutineScope.launch {
                delay(KEEPALIVE_INITIAL_DELAY_MILLIS)
                while (isActive && sessionOwner.isSessionCurrent(message.sessionGeneration)) {
                    enqueueKeepalive(message, keepaliveStatus.get().toByte())
                    delay(KEEPALIVE_PERIOD_MILLIS)
                }
            }

        val result =
            try {
                executeCtapOperation(message.data) { status ->
                    keepaliveStatus.set(status.toInt() and BYTE_MASK)
                    enqueueKeepalive(message, status)
                }
            } finally {
                withContext(NonCancellable) {
                    keepaliveJob.cancelAndJoin()
                }
            }

        queueCtapResponse(message, result, startedAtNanos, operation)
    }

    @Req("FR-BLE-060", "TR-BLE-020")
    private fun enqueueKeepalive(
        message: FidoBleMessage,
        status: Byte,
    ) {
        if (!sessionOwner.isSessionCurrent(message.sessionGeneration)) return
        val keepaliveFragments =
            FidoBleFraming.fragmentResponse(
                CMD_KEEPALIVE,
                byteArrayOf(status),
                sessionOwner.notifyCapacity,
            )
        notificationWriter.enqueue(
            keepaliveFragments[0],
            isKeepalive = true,
            sessionGeneration = message.sessionGeneration,
        )
    }

    private fun queueCtapResponse(
        message: FidoBleMessage,
        result: RawCtapResult,
        startedAtNanos: Long,
        operation: String,
    ) {
        notificationWriter.markResponseTransmissionStarted(message.sessionGeneration)
        val fragments =
            FidoBleFraming.fragmentResponse(
                CMD_MSG,
                result.responseBytes,
                sessionOwner.notifyCapacity,
            )
        var index = 0
        var accepted = true
        var sessionCurrent = true
        var terminalNotificationQueued = false
        while (index < fragments.size && accepted && sessionCurrent) {
            val isTerminalFragment = index == fragments.lastIndex
            accepted =
                notificationWriter.enqueue(
                    fragments[index],
                    isKeepalive = false,
                    sessionGeneration = message.sessionGeneration,
                    onDeliveryResult =
                        if (isTerminalFragment) {
                            { delivered ->
                                try {
                                    logDiagnosticCompletion(operation, startedAtNanos, delivered)
                                } finally {
                                    notificationWriter.resetResponseTransmission(message.sessionGeneration)
                                }
                            }
                        } else {
                            null
                        },
                )
            if (isTerminalFragment && accepted) terminalNotificationQueued = true
            index++
            sessionCurrent = sessionOwner.isSessionCurrent(message.sessionGeneration)
        }
        if (!terminalNotificationQueued) logDiagnosticNotQueued(operation, startedAtNanos)
    }

    private fun logDiagnosticCompletion(
        operation: String,
        startedAtNanos: Long,
        delivered: Boolean,
    ) {
        val elapsedMs = (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND
        Logger.i {
            "FIDO BLE diagnostic completed operation=$operation elapsedMs=$elapsedMs " +
                "terminalNotificationAcknowledged=$delivered"
        }
    }

    private fun logDiagnosticNotQueued(
        operation: String,
        startedAtNanos: Long,
    ) {
        val elapsedMs = (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND
        Logger.w {
            "FIDO BLE diagnostic ended operation=$operation elapsedMs=$elapsedMs " +
                "terminalNotificationAcknowledged=false reason=terminal_response_not_queued"
        }
    }

    private fun enqueueErrorResponse(
        sessionGeneration: Long,
        errorCode: Byte,
    ) {
        val fragments =
            FidoBleFraming.fragmentResponse(
                CMD_ERROR,
                byteArrayOf(errorCode),
                sessionOwner.notifyCapacity,
            )
        for (fragment in fragments) {
            notificationWriter.enqueue(
                fragment,
                isKeepalive = false,
                sessionGeneration = sessionGeneration,
            )
        }
    }

    private fun diagnosticOperationName(request: ByteArray): String =
        when (request.firstOrNull()?.toInt()?.and(BYTE_MASK)) {
            CTAP2_CMD_MAKE_CREDENTIAL -> "MakeCredential"
            CTAP2_CMD_GET_ASSERTION -> "GetAssertion"
            CTAP2_CMD_GET_INFO -> "GetInfo"
            CTAP2_CMD_RESET -> "ResetAuthenticator"
            else -> "Other"
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
                    cancelReceiveTimeout()
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
                                    onBluetoothUnavailable(failure)
                                }
                            }
                        if (isStarted) {
                            if (result.isSuccess) {
                                _connectionState.value = Fido2ConnectionState.Advertising
                            } else {
                                onBluetoothUnavailable(
                                    result.exceptionOrNull()
                                        ?: IllegalStateException("Failed to resume BLE advertising"),
                                )
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
        cancelReceiveTimeout()
        cancelCurrentMessage()
        advertiserController.stopAdvertising()
        _connectionState.value = Fido2ConnectionState.Error("Bluetooth pairing authentication failed")
    }

    internal fun onBluetoothUnavailable(failure: Throwable) {
        Logger.e(failure) { "BleGattTransport: Bluetooth became unavailable during an active BLE session" }
        isStarted = false
        cancelReceiveTimeout()
        cancelCurrentMessage()
        val activeDevice = activeHostAddress ?: sessionOwner.activeDeviceAddress
        activeHostAddress = null
        if (activeDevice != null) sessionOwner.disconnectHost(activeDevice)
        advertiserController.stopAdvertising()
        advertiserController.stopObservingAdapterState()
        _connectionState.value = Fido2ConnectionState.Error("Bluetooth is off; restore Bluetooth and retry")
        gattServerHost?.stop()
    }

    private fun cancelCurrentMessage() {
        val messageJob = currentMsgJob
        currentMsgJob = null
        messageJob?.cancel()
        notificationWriter.clear()
    }
}

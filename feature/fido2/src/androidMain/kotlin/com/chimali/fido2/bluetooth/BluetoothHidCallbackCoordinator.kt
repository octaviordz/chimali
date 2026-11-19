@file:Suppress("SwallowedException", "MissingPermission")

package com.chimali.fido2.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.getOrDefault
import com.chimali.fido2.domain.exception.Fido2Exception
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Closed diagnostic labels for delayed HID reconnect scheduling. */
internal enum class ProactiveConnectReason {
    ALREADY_CONNECTED_ON_ADVERTISING,
    BOND_BONDED,
    ACL_CONNECTED,
}

/** Narrow bridge from Android HID callbacks to the wrapper's session and report ownership. */
@Req("FR-HID-010", "FR-HID-020")
internal interface BluetoothHidCallbackOwner {
    /** Current Android HID profile proxy; null when the system has released it. */
    @Req("FR-HID-010", "FR-HID-020")
    var hidDevice: BluetoothHidDevice?

    /** Current bonded host accepted by the wrapper's session gate. */
    @Req("FR-HID-010", "FR-HID-020")
    var connectedDevice: BluetoothDevice?

    /** Host whose connection is deferred until Android completes pairing. */
    @Req("FR-HID-010", "FR-HID-020")
    var pendingBondDevice: BluetoothDevice?

    /** Scheduled bonded-host connection job, canceled when a direct connection arrives. */
    @Req("FR-HID-010", "FR-HID-020")
    var proactiveConnectJob: Job?

    /** Adapter used for bond and Bluetooth readiness queries. */
    @Req("FR-HID-010", "FR-HID-020")
    val bluetoothAdapter: BluetoothAdapter?

    /** Scope owning delayed proactive host connections. */
    @Req("FR-HID-010", "FR-HID-020")
    val scope: CoroutineScope

    /** Lock shared by session state and incoming-report admission. */
    @Req("FR-HID-010", "FR-HID-020")
    val sessionLock: Any

    /** Mutable lifecycle state published by the wrapper. */
    @Req("FR-HID-010", "FR-HID-020")
    val connectionState: MutableStateFlow<HidConnectionState>

    /** Advances the host generation before admitting successor session traffic. */
    @Req("FR-HID-010", "FR-HID-020")
    fun advanceSessionGeneration()

    /** Erases queue-owned reports abandoned by a session transition. */
    @Req("FR-HID-010", "FR-HID-020")
    fun discardQueuedIncomingReports()

    /** Publishes state and synchronously notifies the active transport owner. */
    @Req("FR-HID-010", "FR-HID-020")
    fun publishSessionState(state: HidConnectionState)

    /** Accepts a bonded host and updates the active session. */
    @Req("FR-HID-010", "FR-HID-020")
    fun acceptConnectedDevice(device: BluetoothDevice)

    /** Attempts an HID connection to a bonded host. */
    @Req("FR-HID-010", "FR-HID-020")
    fun connect(device: BluetoothDevice): Boolean

    /** Copies callback bytes only for the active host and transfers admitted ownership. */
    @Req("FR-HID-010", "FR-HID-020")
    fun enqueueIncomingReportFromDevice(
        device: BluetoothDevice,
        data: ByteArray,
    )
}

/** Owns Android HID lifecycle callbacks while delegating state changes to their session owner. */
@Req("FR-HID-010", "FR-HID-020")
internal class BluetoothHidCallbackCoordinator(
    private val owner: BluetoothHidCallbackOwner,
) {
    private var hidDevice: BluetoothHidDevice?
        get() = owner.hidDevice
        set(value) {
            owner.hidDevice = value
        }

    private val connectedDevice: BluetoothDevice?
        get() = owner.connectedDevice

    private val pendingBondDevice: BluetoothDevice?
        get() = owner.pendingBondDevice

    private var proactiveConnectJob: Job?
        get() = owner.proactiveConnectJob
        set(value) {
            owner.proactiveConnectJob = value
        }

    private val bluetoothAdapter: BluetoothAdapter?
        get() = owner.bluetoothAdapter

    private val scope: CoroutineScope
        get() = owner.scope

    private val sessionLock: Any
        get() = owner.sessionLock

    private val currentConnectionState: MutableStateFlow<HidConnectionState>
        get() = owner.connectionState

    private fun advanceSessionGeneration() = owner.advanceSessionGeneration()

    private fun discardQueuedIncomingReports() = owner.discardQueuedIncomingReports()

    private fun publishSessionState(state: HidConnectionState) = owner.publishSessionState(state)

    /** Records a fixed marker that the HID diagnostic snapshot boundary was reached. */
    @Req("FR-HID-010", "FR-HID-020")
    fun logDiagnosticSnapshot() {
        val hid = hidDevice
        if (hid == null) {
            Logger.d { "[DIAG] HID diagnostic snapshot unavailable" }
            return
        }
        Logger.i { "[DIAG] HID diagnostic snapshot captured" }
    }

    private fun acceptConnectedDevice(device: BluetoothDevice) = owner.acceptConnectedDevice(device)

    /** Connects an already-bonded host after its configured Bluetooth settling delay. */
    @Req("FR-HID-010", "FR-HID-020")
    @Suppress("UnusedParameter") // Retained to preserve caller context in the proactive connection API.
    fun scheduleProactiveConnect(
        device: BluetoothDevice,
        delayDuration: Duration,
        reason: ProactiveConnectReason,
    ) {
        proactiveConnectJob?.cancel()
        proactiveConnectJob =
            scope.launch {
                Logger.i { "Scheduling proactive HID connection to paired device" }
                delay(delayDuration)
                if (currentConnectionState.value is HidConnectionState.Advertising &&
                    device.bondState == BluetoothDevice.BOND_BONDED
                ) {
                    Logger.i { "Executing proactive HID connection to paired device" }
                    owner.connect(device)
                }
            }
    }

    private fun isAclConnected(device: BluetoothDevice): Boolean =
        try {
            val method = device.javaClass.getMethod("isConnected")
            (method.invoke(device) as? Boolean) ?: false
        } catch (e: SecurityException) {
            Logger.w { "isAclConnected: SecurityException querying connection state for paired device" }
            false
        } catch (e: NoSuchMethodException) {
            Logger.w { "isAclConnected: isConnected method not found on paired device" }
            false
        } catch (e: IllegalAccessException) {
            Logger.w { "isAclConnected: IllegalAccessException invoking isConnected on paired device" }
            false
        } catch (e: InvocationTargetException) {
            Logger.w { "isAclConnected: InvocationTargetException on paired device" }
            false
        }

    /** Finds a previously bonded active host when HID advertising starts. */
    @Req("FR-HID-010", "FR-HID-020")
    fun checkExistingConnectionsWhileAdvertising() {
        if (currentConnectionState.value !is HidConnectionState.Advertising) return
        val bondedDevices =
            try {
                bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
            } catch (e: SecurityException) {
                Logger.w { "checkExistingConnections: SecurityException reading bonded devices" }
                emptyList()
            }
        val targetDevice =
            bondedDevices
                .filter { device ->
                    val majorDeviceClass =
                        try {
                            device.bluetoothClass?.majorDeviceClass
                        } catch (e: SecurityException) {
                            Logger.w { "Failed to query bluetoothClass for paired device" }
                            null
                        }
                    val isHost =
                        majorDeviceClass != BluetoothClass.Device.Major.AUDIO_VIDEO &&
                            majorDeviceClass != BluetoothClass.Device.Major.WEARABLE
                    if (!isHost) {
                        Logger.d { "Skipping non-host paired device" }
                    }
                    isHost
                }.firstOrNull { isAclConnected(it) }
        if (targetDevice != null) {
            Logger.i {
                "Found a bonded device already connected via ACL. " +
                    "Initiating proactive HID connection..."
            }
            scheduleProactiveConnect(
                device = targetDevice,
                delayDuration = BluetoothHidConfigProvider.config.aclConnectDelay,
                reason = ProactiveConnectReason.ALREADY_CONNECTED_ON_ADVERTISING,
            )
        }
    }

    private fun enqueueIncomingReportFromDevice(
        device: BluetoothDevice,
        data: ByteArray,
    ) = owner.enqueueIncomingReportFromDevice(device, data)

    /** Handles Bluetooth state, pairing completion, and ACL lifecycle broadcasts. */
    val bluetoothStateReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        Logger.d { "Bluetooth adapter state event received" }
                        if (state == BluetoothAdapter.STATE_OFF) {
                            Logger.w { "Bluetooth turned OFF — resetting HID proxy and connection state" }
                            synchronized(sessionLock) {
                                advanceSessionGeneration()
                                discardQueuedIncomingReports()
                                owner.pendingBondDevice = null
                                hidDevice = null
                                owner.connectedDevice = null
                                publishSessionState(HidConnectionState.Idle)
                            }
                        }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> handleBondStateChanged(intent)
                    BluetoothDevice.ACTION_ACL_CONNECTED -> handleAclConnected(intent)
                }
            }
        }

    /** Completes profile-proxy acquisition and handles its disconnect lifecycle. */
    val serviceListener =
        object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(
                profile: Int,
                proxy: BluetoothProfile,
            ) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    owner.hidDevice = proxy as BluetoothHidDevice
                    Logger.d { "HID_DEVICE profile proxy acquired successfully" }
                    logDiagnosticSnapshot()
                    initContinuation?.takeIf { it.isActive }?.resume(Result.success(Unit))
                    initContinuation = null
                } else {
                    Logger.w { "Unexpected Bluetooth profile connected; expected HID_DEVICE" }
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    synchronized(sessionLock) {
                        advanceSessionGeneration()
                        owner.hidDevice = null
                        owner.connectedDevice = null
                        owner.pendingBondDevice = null
                        discardQueuedIncomingReports()
                        publishSessionState(HidConnectionState.Idle)
                    }
                    Logger.d { "HID_DEVICE profile proxy released (service disconnected)" }
                }
            }
        }

    /**
     * Continuation owned by the wrapper's active profile-proxy initialization attempt.
     * A retry may replace it. Proxy acquisition completes and clears it; immediate request failures
     * clear it in the wrapper. Timeout or cancellation cancels the continuation, so the service
     * callback will not resume it and clears the slot if it later arrives.
     */
    @Req("FR-HID-010", "FR-HID-020")
    var initContinuation: CancellableContinuation<Result<Unit>>? = null

    /** Builds the registration callback while sharing the lifecycle callback implementation. */
    @Req("FR-HID-010", "FR-HID-020")
    fun createRegistrationCallback(cont: CancellableContinuation<Result<Unit>>): BluetoothHidDevice.Callback =
        object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(
                pluggedDevice: BluetoothDevice?,
                registered: Boolean,
            ) {
                hidCallback.onAppStatusChanged(pluggedDevice, registered)
                if (registered) {
                    Logger.i { "registerApp: onAppStatusChanged registered=true — registration confirmed" }
                    if (cont.isActive) cont.resume(Result.success(Unit))
                } else {
                    Logger.w { "registerApp: onAppStatusChanged registered=false — registration lost" }
                    if (cont.isActive) {
                        cont.resume(
                            Result.failure(
                                Fido2Exception.BluetoothException("HID app registration failed (registered=false)"),
                            ),
                        )
                    }
                }
            }

            override fun onConnectionStateChanged(
                device: BluetoothDevice,
                state: Int,
            ) {
                hidCallback.onConnectionStateChanged(device, state)
            }

            override fun onSetReport(
                device: BluetoothDevice,
                type: Byte,
                id: Byte,
                data: ByteArray,
            ) {
                hidCallback.onSetReport(device, type, id, data)
            }

            override fun onInterruptData(
                device: BluetoothDevice,
                reportId: Byte,
                data: ByteArray,
            ) {
                hidCallback.onInterruptData(device, reportId, data)
            }

            override fun onGetReport(
                device: BluetoothDevice,
                type: Byte,
                id: Byte,
                bufferSize: Int,
            ) {
                hidCallback.onGetReport(device, type, id, bufferSize)
            }

            override fun onVirtualCableUnplug(device: BluetoothDevice) {
                hidCallback.onVirtualCableUnplug(device)
            }
        }

    /** Callback dispatched on the executor supplied to [BluetoothHidDevice.registerApp]. */
    val hidCallback =
        object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(
                pluggedDevice: BluetoothDevice?,
                registered: Boolean,
            ) {
                Logger.i {
                    "onAppStatusChanged registered=$registered pluggedDevicePresent=${pluggedDevice != null}"
                }
                logDiagnosticSnapshot()

                if (registered) {
                    synchronized(sessionLock) {
                        if (connectedDevice != null) {
                            advanceSessionGeneration()
                            owner.connectedDevice = null
                            discardQueuedIncomingReports()
                        }
                        publishSessionState(HidConnectionState.Advertising)
                    }
                    if (pluggedDevice != null && BluetoothHidConfigProvider.config.isPhantomDisconnectRequired) {
                        Logger.w {
                            "Stale plugged device reported at registration. " +
                                "Disconnecting to free L2CAP socket."
                        }
                        try {
                            val isDisconnected = hidDevice?.disconnect(pluggedDevice)
                            Logger.d {
                                "disconnect(paired device) result=$isDisconnected — " +
                                    "socket should be freed for new connections."
                            }
                        } catch (e: SecurityException) {
                            Logger.e { "Security error disconnecting stale device paired device" }
                        }
                    }
                    checkExistingConnectionsWhileAdvertising()
                } else {
                    synchronized(sessionLock) {
                        advanceSessionGeneration()
                        owner.connectedDevice = null
                        owner.pendingBondDevice = null
                        discardQueuedIncomingReports()
                        publishSessionState(HidConnectionState.Idle)
                    }
                }
            }

            override fun onConnectionStateChanged(
                device: BluetoothDevice,
                state: Int,
            ) {
                Logger.i { "Paired device connection state event received" }
                logDiagnosticSnapshot()

                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> handleConnected(device)
                    BluetoothProfile.STATE_DISCONNECTED -> handleDisconnected(device)
                    BluetoothProfile.STATE_CONNECTING -> {
                        Logger.i { "Paired device connecting..." }
                        currentConnectionState.value = HidConnectionState.Connecting(device)
                    }
                    BluetoothProfile.STATE_DISCONNECTING -> Logger.i { "Device paired device disconnecting..." }
                }
            }

            /** Copies borrowed platform bytes when the host sends a SET_REPORT command. */
            override fun onSetReport(
                device: BluetoothDevice,
                type: Byte,
                id: Byte,
                data: ByteArray,
            ) {
                Logger.d { "onSetReport received payloadLen=${data.size}" }
                enqueueIncomingReportFromDevice(device, data)
            }

            /** Copies borrowed platform bytes when the host sends an interrupt report. */
            override fun onInterruptData(
                device: BluetoothDevice,
                reportId: Byte,
                data: ByteArray,
            ) {
                Logger.d { "onInterruptData received payloadLen=${data.size}" }
                enqueueIncomingReportFromDevice(device, data)
            }

            override fun onGetReport(
                device: BluetoothDevice,
                type: Byte,
                id: Byte,
                bufferSize: Int,
            ) {
                try {
                    hidDevice?.replyReport(device, type, id, ByteArray(FIDO_HID_REPORT_SIZE))
                } catch (e: SecurityException) {
                    Logger.e { "onGetReport: BLUETOOTH_CONNECT permission denied" }
                }
            }

            override fun onVirtualCableUnplug(device: BluetoothDevice) {
                Logger.d { "onVirtualCableUnplug device=paired device" }
                synchronized(sessionLock) {
                    if (connectedDevice?.address == device.address) {
                        advanceSessionGeneration()
                        owner.connectedDevice = null
                        discardQueuedIncomingReports()
                        publishSessionState(HidConnectionState.Advertising)
                    }
                }
            }
        }

    private fun handleBondStateChanged(intent: Intent) {
        @Suppress("DEPRECATION")
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
        val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
        Logger.i { "Paired device bond state event received" }

        val hid = hidDevice
        if (hid != null) {
            try {
                hid.getConnectionState(device)
                Logger.i { "HID state queried for paired device" }
            } catch (e: IllegalStateException) {
                Logger.w { "  Could not query HID connection state for paired device" }
            }
        }
        logDiagnosticSnapshot()

        val pending = pendingBondDevice
        if (bondState == BluetoothDevice.BOND_BONDED && pending != null && pending.address == device.address) {
            Logger.i { "Deferred paired-device connection is ready" }
            owner.pendingBondDevice = null
            acceptConnectedDevice(device)
        } else if (
            bondState == BluetoothDevice.BOND_BONDED &&
            pending == null &&
            currentConnectionState.value is HidConnectionState.Advertising
        ) {
            scheduleProactiveConnect(
                device = device,
                delayDuration = BluetoothHidConfigProvider.config.bondConnectDelay,
                reason = ProactiveConnectReason.BOND_BONDED,
            )
        }
    }

    private fun handleAclConnected(intent: Intent) {
        @Suppress("DEPRECATION")
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
        Logger.d { "Paired device ACL connection event received" }
        val majorDeviceClass =
            try {
                device.bluetoothClass?.majorDeviceClass
            } catch (e: SecurityException) {
                Logger.w { "Failed to query bluetoothClass for paired device" }
                null
            }
        if (majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
            majorDeviceClass == BluetoothClass.Device.Major.WEARABLE
        ) {
            Logger.d { "Ignoring ACL connection for non-host paired device" }
            return
        }
        if (device.bondState == BluetoothDevice.BOND_BONDED &&
            currentConnectionState.value is HidConnectionState.Advertising
        ) {
            scheduleProactiveConnect(
                device = device,
                delayDuration = BluetoothHidConfigProvider.config.aclConnectDelay,
                reason = ProactiveConnectReason.ACL_CONNECTED,
            )
        }
    }

    private fun handleConnected(device: BluetoothDevice) {
        owner.proactiveConnectJob?.cancel()
        owner.proactiveConnectJob = null
        val bondState = runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
        Logger.i { "Paired device connection received" }
        when (bondState) {
            BluetoothDevice.BOND_NONE -> {
                Logger.w {
                    "Rejecting connection from completely unbonded device. " +
                        "No link key present — device has never paired."
                }
                try {
                    hidDevice?.disconnect(device)
                } catch (e: SecurityException) {
                    Logger.e { "Failed to disconnect unbonded device" }
                }
            }
            BluetoothDevice.BOND_BONDING -> {
                Logger.d {
                    "Paired device is still bonding — " +
                        "deferring connection acceptance until BOND_BONDED."
                }
                owner.pendingBondDevice = device
            }
            else -> acceptConnectedDevice(device)
        }
    }

    private fun handleDisconnected(device: BluetoothDevice) {
        Logger.d { "Device disconnected: paired device" }
        if (pendingBondDevice?.address == device.address) {
            Logger.d { "Pending paired device disconnected — clearing deferred state." }
            owner.pendingBondDevice = null
        }
        synchronized(sessionLock) {
            if (connectedDevice?.address == device.address) {
                advanceSessionGeneration()
                owner.connectedDevice = null
                discardQueuedIncomingReports()
                publishSessionState(HidConnectionState.Advertising)
            }
        }
    }
}

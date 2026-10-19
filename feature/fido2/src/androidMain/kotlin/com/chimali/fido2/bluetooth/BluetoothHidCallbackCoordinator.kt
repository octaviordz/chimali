@file:Suppress("MissingPermission")

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

    /** Records current HID, host, bond, and session state for lifecycle diagnosis. */
    @Req("FR-HID-010", "FR-HID-020")
    fun logDiagnosticSnapshot(tag: String) {
        val hid = hidDevice
        if (hid == null) {
            Logger.d { "[DIAG:$tag] hidDevice=null, _connectionState=${currentConnectionState.value}" }
            return
        }

        try {
            val connectedDevices = hid.getConnectedDevices()
            val bondedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
            Logger.i {
                "[DIAG:$tag] _connectionState=${currentConnectionState.value}, " +
                    "connectedDevice=${connectedDevice?.address}, " +
                    "pendingBondDevice=${pendingBondDevice?.address}"
            }
            Logger.i {
                "[DIAG:$tag] getConnectedDevices()=[" +
                    "${connectedDevices.joinToString { "${it.address}(state=${hid.getConnectionState(it)})" }}] " +
                    "(count=${connectedDevices.size})"
            }
            Logger.i {
                "[DIAG:$tag] bondedDevices=[${bondedDevices.joinToString { "${it.address}(bond=${it.bondState})" }}] " +
                    "(count=${bondedDevices.size})"
            }
        } catch (e: SecurityException) {
            Logger.w(e) { "[DIAG:$tag] SecurityException reading diagnostic state" }
        } catch (e: IllegalStateException) {
            Logger.w(e) { "[DIAG:$tag] IllegalStateException reading diagnostic state" }
        }
    }

    private fun connectionStateName(state: Int): String =
        when (state) {
            BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED(0)"
            BluetoothProfile.STATE_CONNECTING -> "CONNECTING(1)"
            BluetoothProfile.STATE_CONNECTED -> "CONNECTED(2)"
            BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING(3)"
            else -> "UNKNOWN($state)"
        }

    private fun bondStateName(state: Int): String =
        when (state) {
            BluetoothDevice.BOND_NONE -> "BOND_NONE(10)"
            BluetoothDevice.BOND_BONDING -> "BOND_BONDING(11)"
            BluetoothDevice.BOND_BONDED -> "BOND_BONDED(12)"
            else -> "UNKNOWN($state)"
        }

    private fun acceptConnectedDevice(device: BluetoothDevice) = owner.acceptConnectedDevice(device)

    /** Connects an already-bonded host after its configured Bluetooth settling delay. */
    @Req("FR-HID-010", "FR-HID-020")
    fun scheduleProactiveConnect(
        device: BluetoothDevice,
        delayDuration: Duration,
        reason: String,
    ) {
        proactiveConnectJob?.cancel()
        proactiveConnectJob =
            scope.launch {
                Logger.i { "Scheduling proactive HID connection to ${device.address} ($reason) in $delayDuration..." }
                delay(delayDuration)
                if (currentConnectionState.value is HidConnectionState.Advertising &&
                    device.bondState == BluetoothDevice.BOND_BONDED
                ) {
                    Logger.i { "Executing proactive HID connect to ${device.address} ($reason)..." }
                    owner.connect(device)
                }
            }
    }

    private fun isAclConnected(device: BluetoothDevice): Boolean =
        try {
            val method = device.javaClass.getMethod("isConnected")
            (method.invoke(device) as? Boolean) ?: false
        } catch (e: SecurityException) {
            Logger.w(e) { "isAclConnected: SecurityException querying connection state for ${device.address}" }
            false
        } catch (e: NoSuchMethodException) {
            Logger.w(e) { "isAclConnected: isConnected method not found on ${device.address}" }
            false
        } catch (e: IllegalAccessException) {
            Logger.w(e) { "isAclConnected: IllegalAccessException invoking isConnected on ${device.address}" }
            false
        } catch (e: InvocationTargetException) {
            Logger.w(e) { "isAclConnected: InvocationTargetException on ${device.address}" }
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
                Logger.w(e) { "checkExistingConnections: SecurityException reading bonded devices" }
                emptyList()
            }
        val targetDevice =
            bondedDevices
                .filter { device ->
                    val majorDeviceClass =
                        try {
                            device.bluetoothClass?.majorDeviceClass
                        } catch (e: SecurityException) {
                            Logger.w(e) { "Failed to query bluetoothClass for ${device.address}" }
                            null
                        }
                    val isHost =
                        majorDeviceClass != BluetoothClass.Device.Major.AUDIO_VIDEO &&
                            majorDeviceClass != BluetoothClass.Device.Major.WEARABLE
                    if (!isHost) {
                        Logger.d {
                            "Skipping non-host bonded device ${device.address} " +
                                "(majorDeviceClass=$majorDeviceClass)"
                        }
                    }
                    isHost
                }.firstOrNull { isAclConnected(it) }
        if (targetDevice != null) {
            Logger.i {
                "Found bonded device ${targetDevice.address} already connected via ACL. " +
                    "Initiating proactive HID connection..."
            }
            scheduleProactiveConnect(
                device = targetDevice,
                delayDuration = BluetoothHidConfigProvider.config.aclConnectDelay,
                reason = "ALREADY_CONNECTED_ON_ADVERTISING",
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
                        Logger.d { "BluetoothAdapter state changed: $state" }
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
                    logDiagnosticSnapshot("PROXY_ACQUIRED")
                    initContinuation?.takeIf { it.isActive }?.resume(Result.success(Unit))
                    initContinuation = null
                } else {
                    Logger.w { "onServiceConnected received for profile $profile, expected HID_DEVICE" }
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    synchronized(sessionLock) {
                        advanceSessionGeneration()
                        owner.hidDevice = null
                        owner.connectedDevice = null
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
                    "onAppStatusChanged registered=$registered pluggedDevice=${pluggedDevice?.address ?: "null"}"
                }
                logDiagnosticSnapshot("APP_STATUS_CHANGED")

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
                            "Stale pluggedDevice ${pluggedDevice.address} reported at registration. " +
                                "Disconnecting to free L2CAP socket."
                        }
                        try {
                            val isDisconnected = hidDevice?.disconnect(pluggedDevice)
                            Logger.d {
                                "disconnect(${pluggedDevice.address}) result=$isDisconnected — " +
                                    "socket should be freed for new connections."
                            }
                        } catch (e: SecurityException) {
                            Logger.e(e) { "Security error disconnecting stale device ${pluggedDevice.address}" }
                        }
                    }
                    checkExistingConnectionsWhileAdvertising()
                } else {
                    synchronized(sessionLock) {
                        advanceSessionGeneration()
                        owner.connectedDevice = null
                        discardQueuedIncomingReports()
                        publishSessionState(HidConnectionState.Idle)
                    }
                }
            }

            override fun onConnectionStateChanged(
                device: BluetoothDevice,
                state: Int,
            ) {
                Logger.i {
                    "onConnectionStateChanged device=${device.address} state=${connectionStateName(state)}"
                }
                logDiagnosticSnapshot("CONNECTION_STATE_CHANGED")

                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> handleConnected(device)
                    BluetoothProfile.STATE_DISCONNECTED -> handleDisconnected(device)
                    BluetoothProfile.STATE_CONNECTING -> {
                        Logger.i { "Device ${device.address} connecting..." }
                        currentConnectionState.value = HidConnectionState.Connecting(device)
                    }
                    BluetoothProfile.STATE_DISCONNECTING -> Logger.i { "Device ${device.address} disconnecting..." }
                }
            }

            /** Copies borrowed platform bytes when the host sends a SET_REPORT command. */
            override fun onSetReport(
                device: BluetoothDevice,
                type: Byte,
                id: Byte,
                data: ByteArray,
            ) {
                Logger.d { "onSetReport type=$type id=$id len=${data.size}" }
                enqueueIncomingReportFromDevice(device, data)
            }

            /** Copies borrowed platform bytes when the host sends an interrupt report. */
            override fun onInterruptData(
                device: BluetoothDevice,
                reportId: Byte,
                data: ByteArray,
            ) {
                Logger.d { "onInterruptData reportId=$reportId len=${data.size}" }
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
                    Logger.e(e) { "onGetReport: BLUETOOTH_CONNECT permission denied" }
                }
            }

            override fun onVirtualCableUnplug(device: BluetoothDevice) {
                Logger.d { "onVirtualCableUnplug device=${device.address}" }
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
        val previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
        Logger.i {
            "Bond state changed: device=${device.address} " +
                "${bondStateName(previousBondState)}→${bondStateName(bondState)}"
        }

        val hid = hidDevice
        if (hid != null) {
            try {
                val hidState = hid.getConnectionState(device)
                Logger.i { "  HID connection state for ${device.address}: ${connectionStateName(hidState)}" }
            } catch (e: IllegalStateException) {
                Logger.w(e) { "  Could not query HID connection state for ${device.address}" }
            }
        }
        logDiagnosticSnapshot("BOND_CHANGED")

        val pending = pendingBondDevice
        if (bondState == BluetoothDevice.BOND_BONDED && pending != null && pending.address == device.address) {
            Logger.i { "Pending device ${device.address} is now BOND_BONDED — completing deferred connection." }
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
                reason = "BOND_BONDED",
            )
        }
    }

    private fun handleAclConnected(intent: Intent) {
        @Suppress("DEPRECATION")
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
        Logger.d {
            "ACL_CONNECTED: device=${device.address} bond=${bondStateName(device.bondState)} " +
                "currentState=${currentConnectionState.value::class.simpleName}"
        }
        val majorDeviceClass =
            try {
                device.bluetoothClass?.majorDeviceClass
            } catch (e: SecurityException) {
                Logger.w(e) { "Failed to query bluetoothClass for ${device.address}" }
                null
            }
        if (majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
            majorDeviceClass == BluetoothClass.Device.Major.WEARABLE
        ) {
            Logger.d {
                "Ignoring ACL_CONNECTED for non-host device ${device.address} " +
                    "(majorDeviceClass=$majorDeviceClass)"
            }
            return
        }
        if (device.bondState == BluetoothDevice.BOND_BONDED &&
            currentConnectionState.value is HidConnectionState.Advertising
        ) {
            scheduleProactiveConnect(
                device = device,
                delayDuration = BluetoothHidConfigProvider.config.aclConnectDelay,
                reason = "ACL_CONNECTED",
            )
        }
    }

    private fun handleConnected(device: BluetoothDevice) {
        owner.proactiveConnectJob?.cancel()
        owner.proactiveConnectJob = null
        val bondState = runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
        Logger.i { "Device ${device.address} connected — bondState=${bondStateName(bondState)}" }
        when (bondState) {
            BluetoothDevice.BOND_NONE -> {
                Logger.w {
                    "Rejecting connection from completely unbonded device ${device.address}. " +
                        "No link key present — device has never paired."
                }
                try {
                    hidDevice?.disconnect(device)
                } catch (e: SecurityException) {
                    Logger.e(e) { "Failed to disconnect unbonded device" }
                }
            }
            BluetoothDevice.BOND_BONDING -> {
                Logger.d {
                    "Device ${device.address} is still bonding — " +
                        "deferring connection acceptance until BOND_BONDED."
                }
                owner.pendingBondDevice = device
            }
            else -> acceptConnectedDevice(device)
        }
    }

    private fun handleDisconnected(device: BluetoothDevice) {
        Logger.d { "Device disconnected: ${device.address}" }
        if (pendingBondDevice?.address == device.address) {
            Logger.d { "Pending bond device ${device.address} disconnected — clearing deferred state." }
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

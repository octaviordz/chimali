package com.chimali.fido2.data.transport

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.chimali.core.common.annotation.Req
import com.chimali.fido2.domain.repository.BluetoothTransportMode
import com.chimali.fido2.domain.repository.Fido2SettingsRepository
import com.chimali.fido2.domain.repository.ProcessTransportState
import com.chimali.fido2.domain.repository.TransportAvailability
import com.chimali.fido2.domain.repository.TransportPreferenceResult
import com.chimali.fido2.domain.repository.TransportUnavailableReason
import java.io.IOException
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Result of the non-initializing platform capability check.
 */
@Req("FR-BT-SEL-050", "TR-BT-SEL-020")
sealed interface CapabilityCheckResult {
    data object Supported : CapabilityCheckResult

    data object Unknown : CapabilityCheckResult

    data object Unsupported : CapabilityCheckResult

    data object PermissionRequired : CapabilityCheckResult

    data object BluetoothOff : CapabilityCheckResult
}

/**
 * Checks platform capability signals without initializing Bluetooth or transports.
 */
@Req("FR-BT-SEL-050", "TR-BT-SEL-020")
interface BluetoothCapabilityChecker {
    fun checkCapability(mode: BluetoothTransportMode): CapabilityCheckResult
}

/**
 * Default Android implementation using system signals without initializing transports.
 */
@Req("FR-BT-SEL-050", "TR-BT-SEL-020")
class AndroidBluetoothCapabilityChecker(
    private val context: Context,
) : BluetoothCapabilityChecker {
    override fun checkCapability(mode: BluetoothTransportMode): CapabilityCheckResult {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return CapabilityCheckResult.Unsupported

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect =
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED
            val hasAdvertise =
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.BLUETOOTH_ADVERTISE,
                ) == PackageManager.PERMISSION_GRANTED
            if (!hasConnect || !hasAdvertise) {
                return CapabilityCheckResult.PermissionRequired
            }
        }

        if (!adapter.isEnabled) {
            return CapabilityCheckResult.BluetoothOff
        }

        if (mode == BluetoothTransportMode.FIDO_BLE) {
            val hasLe = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
            if (!hasLe) {
                return CapabilityCheckResult.Unsupported
            }
            if (!adapter.isMultipleAdvertisementSupported) {
                return CapabilityCheckResult.Unknown
            }
            return CapabilityCheckResult.Supported
        }

        // Android exposes no public, non-initializing signal for whether an OEM
        // Bluetooth stack provides the HID_DEVICE profile service. Generic Bluetooth
        // hardware is insufficient evidence (for example, Mi/API29 can fail to bind
        // the profile). Defer that determination to the selected transport's activation.
        return classicHidCapabilityForApi(Build.VERSION.SDK_INT)
    }
}

internal fun classicHidCapabilityForApi(apiLevel: Int): CapabilityCheckResult =
    if (apiLevel < Build.VERSION_CODES.P) CapabilityCheckResult.Unsupported else CapabilityCheckResult.Unknown

/**
 * Process-scoped controller and single gated handle for FIDO2 transport selection.
 */
@Req("FR-BT-SEL-030", "FR-BT-SEL-040", "FR-BT-SEL-050", "TR-BT-SEL-020", "TR-BT-SEL-030")
class ProcessTransportSelection(
    private val settingsRepository: Fido2SettingsRepository,
    private val capabilityChecker: BluetoothCapabilityChecker,
    private val bleTransportFactory: () -> Fido2Transport,
    private val classicTransportFactory: () -> Fido2Transport,
    private val coroutineScope: CoroutineScope,
    private val snapshotTimeoutMs: Long = 5000L,
    private val activationTimeoutMs: Long = 5000L,
) : Fido2Transport {
    private val _state =
        MutableStateFlow(
            ProcessTransportState(
                processChoice = null,
                availability = TransportAvailability.Loading,
            ),
        )
    val state: StateFlow<ProcessTransportState> = _state.asStateFlow()

    private val _connectionState = MutableStateFlow<Fido2ConnectionState>(Fido2ConnectionState.Idle)
    override val connectionState: StateFlow<Fido2ConnectionState> = _connectionState.asStateFlow()

    private val snapshotMutex = Mutex()
    private var snapshotDeferred: CompletableDeferred<BluetoothTransportMode?>? = null

    private val activationMutex = Mutex()
    private var activeTransport: Fido2Transport? = null
    private var activationDeferred: CompletableDeferred<Result<Unit>>? = null
    private var connectionStateJob: kotlinx.coroutines.Job? = null

    /** Starts the snapshot asynchronously with a 5s deadline. */
    fun startSnapshot() {
        coroutineScope.launch {
            resolveSnapshot()
        }
    }

    private suspend fun resolveSnapshot(): BluetoothTransportMode? {
        var isSnapshotOwner = false
        val existingDeferred =
            snapshotMutex.withLock {
                if (_state.value.processChoice != null) {
                    return _state.value.processChoice
                }
                val existing = snapshotDeferred
                if (existing != null) {
                    existing
                } else {
                    isSnapshotOwner = true
                    val deferred = CompletableDeferred<BluetoothTransportMode?>()
                    snapshotDeferred = deferred
                    deferred
                }
            }

        if (!isSnapshotOwner) {
            return existingDeferred.await()
        }

        val snapshotCompleted =
            try {
                withTimeoutOrNull(snapshotTimeoutMs) {
                    try {
                        when (val result = settingsRepository.getTransportPreference()) {
                            is TransportPreferenceResult.Success -> {
                                val mode = result.preference.mode
                                _state.value = _state.value.copy(processChoice = mode)

                                when (capabilityChecker.checkCapability(mode)) {
                                    CapabilityCheckResult.Supported,
                                    CapabilityCheckResult.Unknown,
                                    -> {
                                        _state.value =
                                            _state.value.copy(availability = TransportAvailability.SelectedNotStarted)
                                    }
                                    CapabilityCheckResult.Unsupported -> {
                                        _state.value =
                                            _state.value.copy(
                                                availability =
                                                    TransportAvailability.Unavailable(
                                                        TransportUnavailableReason.Unsupported,
                                                    ),
                                            )
                                    }
                                    CapabilityCheckResult.PermissionRequired -> {
                                        _state.value =
                                            _state.value.copy(
                                                availability =
                                                    TransportAvailability.Unavailable(
                                                        TransportUnavailableReason.PermissionRequired,
                                                    ),
                                            )
                                    }
                                    CapabilityCheckResult.BluetoothOff -> {
                                        _state.value =
                                            _state.value.copy(
                                                availability =
                                                    TransportAvailability.Unavailable(
                                                        TransportUnavailableReason.BluetoothOff,
                                                    ),
                                            )
                                    }
                                }
                                existingDeferred.complete(mode)
                            }
                            is TransportPreferenceResult.InvalidPreference -> {
                                _state.value =
                                    _state.value.copy(
                                        availability =
                                            TransportAvailability.Unavailable(
                                                TransportUnavailableReason.InvalidPreference,
                                            ),
                                    )
                                existingDeferred.complete(null)
                            }
                            is TransportPreferenceResult.ReadError -> {
                                _state.value =
                                    _state.value.copy(
                                        availability =
                                            TransportAvailability.Unavailable(
                                                TransportUnavailableReason.PreferenceReadFailed,
                                            ),
                                    )
                                existingDeferred.complete(null)
                            }
                        }
                    } catch (e: IOException) {
                        Logger.e(e) { "ProcessTransportSelection: Unable to read saved transport preference" }
                        _state.value =
                            _state.value.copy(
                                availability =
                                    TransportAvailability.Unavailable(
                                        TransportUnavailableReason.PreferenceReadFailed,
                                    ),
                            )
                        existingDeferred.complete(null)
                    }
                }
            } catch (cancellation: CancellationException) {
                existingDeferred.complete(null)
                throw cancellation
            }
        if (snapshotCompleted == null && !existingDeferred.isCompleted) {
            Logger.w { "ProcessTransportSelection: Initial preference snapshot timed out" }
            _state.value =
                _state.value.copy(
                    availability =
                        TransportAvailability.Unavailable(
                            TransportUnavailableReason.InitializationTimeout,
                        ),
                )
            existingDeferred.complete(null)
        }

        return existingDeferred.await()
    }

    /** Retries snapshot only if processChoice is not yet frozen. */
    fun retrySnapshot() {
        if (_state.value.processChoice != null) return
        snapshotDeferred = null
        _state.value =
            ProcessTransportState(
                processChoice = null,
                availability = TransportAvailability.Loading,
            )
        startSnapshot()
    }

    /** Retries activation for the same frozen choice. */
    fun retryActivation() {
        val choice = _state.value.processChoice ?: return
        when (capabilityChecker.checkCapability(choice)) {
            CapabilityCheckResult.Supported,
            CapabilityCheckResult.Unknown,
            -> {
                _state.value = _state.value.copy(availability = TransportAvailability.SelectedNotStarted)
            }
            CapabilityCheckResult.Unsupported -> {
                _state.value =
                    _state.value.copy(
                        availability = TransportAvailability.Unavailable(TransportUnavailableReason.Unsupported),
                    )
            }
            CapabilityCheckResult.PermissionRequired -> {
                _state.value =
                    _state.value.copy(
                        availability =
                            TransportAvailability.Unavailable(TransportUnavailableReason.PermissionRequired),
                    )
            }
            CapabilityCheckResult.BluetoothOff -> {
                _state.value =
                    _state.value.copy(
                        availability = TransportAvailability.Unavailable(TransportUnavailableReason.BluetoothOff),
                    )
            }
        }
    }

    // This boundary normalizes exceptions from platform transport factories into a stable startup result.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun connect(): Result<Unit> {
        if (_state.value.processChoice == null && _state.value.availability is TransportAvailability.Loading) {
            resolveSnapshot()
        }

        val choice = _state.value.processChoice
        if (choice == null) {
            val reason =
                when (val avail = _state.value.availability) {
                    is TransportAvailability.Unavailable -> avail.reason
                    else -> TransportUnavailableReason.StartupFailed
                }
            return Result.failure(IllegalStateException("No transport selected or available: $reason"))
        }

        when (val avail = _state.value.availability) {
            is TransportAvailability.Ready -> return Result.success(Unit)
            is TransportAvailability.Unavailable -> {
                return Result.failure(IllegalStateException("Selected transport is unavailable: ${avail.reason}"))
            }
            is TransportAvailability.Loading,
            is TransportAvailability.SelectedNotStarted,
            is TransportAvailability.Starting,
            -> {
                // Proceed to activation
            }
        }

        var isAttemptOwner = false
        val currentAttempt =
            activationMutex.withLock {
                if (_state.value.availability is TransportAvailability.Ready) {
                    return Result.success(Unit)
                }
                val existing = activationDeferred
                if (existing != null) {
                    existing
                } else {
                    isAttemptOwner = true
                    val deferred = CompletableDeferred<Result<Unit>>()
                    activationDeferred = deferred
                    _state.value = _state.value.copy(availability = TransportAvailability.Starting)
                    deferred
                }
            }

        if (!isAttemptOwner) {
            return currentAttempt.await()
        }

        var transportInstance: Fido2Transport? = null
        try {
            val result =
                withTimeoutOrNull(activationTimeoutMs) {
                    val transport =
                        when (choice) {
                            BluetoothTransportMode.FIDO_BLE -> bleTransportFactory()
                            BluetoothTransportMode.CLASSIC_HID -> classicTransportFactory()
                        }
                    transportInstance = transport
                    activeTransport = transport

                    val connResult = transport.connect()
                    if (connResult.isSuccess) {
                        _state.value = _state.value.copy(availability = TransportAvailability.Ready)
                        _connectionState.value = transport.connectionState.value
                        connectionStateJob?.cancel()
                        connectionStateJob =
                            coroutineScope.launch {
                                transport.connectionState.collectLatest { state ->
                                    _connectionState.value = state
                                }
                            }
                        Result.success(Unit)
                    } else {
                        activeTransport = null
                        _state.value =
                            _state.value.copy(
                                availability =
                                    TransportAvailability.Unavailable(
                                        TransportUnavailableReason.StartupFailed,
                                    ),
                            )
                        connResult
                    }
                }
            if (result != null) {
                if (result.isFailure) {
                    transportInstance?.let { transport -> disconnectAfterFailedActivation(transport) }
                    activeTransport = null
                }
                currentAttempt.complete(result)
                return result
            }
            val timeout = TimeoutException("Selected transport startup exceeded $activationTimeoutMs ms")
            Logger.w(timeout) { "ProcessTransportSelection: Selected transport startup timed out" }
            _state.value =
                _state.value.copy(
                    availability =
                        TransportAvailability.Unavailable(
                            TransportUnavailableReason.InitializationTimeout,
                        ),
                )
            transportInstance?.let { transport -> disconnectAfterFailedActivation(transport) }
            activeTransport = null
            val failure = Result.failure<Unit>(timeout)
            currentAttempt.complete(failure)
            return failure
        } catch (e: CancellationException) {
            transportInstance?.let { transport -> disconnectAfterFailedActivation(transport) }
            activeTransport = null
            _state.value = _state.value.copy(availability = TransportAvailability.SelectedNotStarted)
            _connectionState.value = Fido2ConnectionState.Idle
            currentAttempt.complete(Result.failure(e))
            throw e
        } catch (e: Exception) {
            Logger.e(e) { "ProcessTransportSelection: Selected transport failed to start" }
            _state.value =
                _state.value.copy(
                    availability =
                        TransportAvailability.Unavailable(
                            TransportUnavailableReason.StartupFailed,
                        ),
                )
            transportInstance?.let { transport -> disconnectAfterFailedActivation(transport) }
            activeTransport = null
            val failure = Result.failure<Unit>(e)
            currentAttempt.complete(failure)
            return failure
        } finally {
            withContext(NonCancellable) {
                activationMutex.withLock {
                    if (activationDeferred === currentAttempt) {
                        activationDeferred = null
                    }
                }
            }
        }
    }

    /** Cleanup is best-effort and non-cancellable so it cannot strand shared activation waiters. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun disconnectAfterFailedActivation(transport: Fido2Transport) {
        withContext(NonCancellable) {
            try {
                transport.disconnect().onFailure { failure ->
                    Logger.e(failure) { "ProcessTransportSelection: Transport cleanup reported failure" }
                }
            } catch (failure: Exception) {
                Logger.e(failure) { "ProcessTransportSelection: Transport cleanup threw during activation failure" }
            }
        }
    }

    override suspend fun connectDevice(macAddress: String): Result<Unit> {
        if (_state.value.processChoice == null && _state.value.availability is TransportAvailability.Loading) {
            resolveSnapshot()
        }

        val choice = _state.value.processChoice
        if (choice == BluetoothTransportMode.FIDO_BLE) {
            return Result.failure(UnsupportedOutboundConnectionException())
        }

        val conn = connect()
        if (conn.isFailure) return conn

        val transport =
            activeTransport
                ?: return Result.failure(IllegalStateException("Classic transport not active"))
        return transport.connectDevice(macAddress)
    }

    override suspend fun disconnect(): Result<Unit> {
        connectionStateJob?.cancel()
        connectionStateJob = null
        val transport = activeTransport ?: return Result.success(Unit)
        val result = transport.disconnect()
        activeTransport = null
        if (_state.value.availability is TransportAvailability.Ready) {
            _state.value = _state.value.copy(availability = TransportAvailability.SelectedNotStarted)
        }
        _connectionState.value = Fido2ConnectionState.Idle
        return result
    }

    /** Releases internal listeners and resources. */
    fun close() {
        connectionStateJob?.cancel()
        connectionStateJob = null
    }
}

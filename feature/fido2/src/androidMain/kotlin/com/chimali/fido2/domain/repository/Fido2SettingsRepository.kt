package com.chimali.fido2.domain.repository

import com.chimali.core.common.annotation.Req
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Legal transport mode values.
 *
 * Persisted as stable explicit strings `fido_ble` and `classic_hid`, never enum ordinals or localized labels.
 */
@Req("FR-BT-SEL-010", "TR-BT-SEL-010")
enum class BluetoothTransportMode(
    val wireValue: String,
) {
    FIDO_BLE("fido_ble"),
    CLASSIC_HID("classic_hid"),
    ;

    companion object {
        const val FIDO_BLE_STRING = "fido_ble"
        const val CLASSIC_HID_STRING = "classic_hid"

        fun fromWire(value: String): BluetoothTransportMode? =
            when (value) {
                FIDO_BLE_STRING -> FIDO_BLE
                CLASSIC_HID_STRING -> CLASSIC_HID
                else -> null
            }
    }
}

/**
 * Durable transport selection preference.
 */
@Req("FR-BT-SEL-020", "TR-BT-SEL-010")
data class TransportPreference(
    val mode: BluetoothTransportMode,
    val selectedAtEpochMillis: Long,
)

/**
 * Result of reading the transport preference from persistent storage.
 */
@Req("FR-BT-SEL-050", "TR-BT-SEL-020")
sealed interface TransportPreferenceResult {
    data class Success(
        val preference: TransportPreference,
    ) : TransportPreferenceResult

    data class InvalidPreference(
        val rawValue: String,
    ) : TransportPreferenceResult

    data class ReadError(
        val cause: Throwable,
    ) : TransportPreferenceResult
}

/**
 * Result of saving the transport preference to persistent storage.
 */
@Req("FR-BT-SEL-020", "TR-BT-SEL-010")
sealed interface TransportSaveResult {
    data class Success(
        val preference: TransportPreference,
        val isNoOp: Boolean = false,
    ) : TransportSaveResult

    data class InvalidInput(
        val rawValue: String,
    ) : TransportSaveResult

    data class WriteError(
        val cause: Throwable,
    ) : TransportSaveResult
}

/**
 * Process transport availability state.
 */
@Req("FR-BT-SEL-040", "FR-BT-SEL-050", "TR-BT-SEL-020", "TR-BT-SEL-030")
sealed interface TransportAvailability {
    data object Loading : TransportAvailability

    data object SelectedNotStarted : TransportAvailability

    data object Starting : TransportAvailability

    data object Ready : TransportAvailability

    data class Unavailable(
        val reason: TransportUnavailableReason,
    ) : TransportAvailability
}

/**
 * Specific reasons a transport may be unavailable.
 */
@Req("FR-BT-SEL-050", "TR-BT-SEL-020")
enum class TransportUnavailableReason {
    PreferenceReadFailed,
    InvalidPreference,
    InitializationTimeout,
    Unsupported,
    PermissionRequired,
    BluetoothOff,
    StartupFailed,
}

/**
 * Controller-owned read-only runtime state.
 */
@Req("FR-BT-SEL-040", "TR-BT-SEL-030")
data class ProcessTransportState(
    val processChoice: BluetoothTransportMode?,
    val availability: TransportAvailability,
)

/**
 * Repository for managing FIDO2 authenticator configuration and settings.
 *
 * This repository abstracts the storage of persistent settings that define the
 * authenticator's behavior and limits.
 */
interface Fido2SettingsRepository {
    /**
     * Returns the maximum number of passkey credentials the authenticator will store.
     *
     * This limit is configurable and defaults to 50 per FR-HID-022.
     */
    suspend fun getMaxCredentialCount(): Int

    /**
     * Updates the maximum credential count storage limit.
     */
    suspend fun setMaxCredentialCount(count: Int)

    /**
     * Observes the persistent transport preference as a Flow.
     */
    @Req("FR-BT-SEL-010", "TR-BT-SEL-010")
    fun observeTransportPreference(): Flow<TransportPreferenceResult> =
        flowOf(
            TransportPreferenceResult.Success(
                TransportPreference(BluetoothTransportMode.FIDO_BLE, 0L),
            ),
        )

    /**
     * Reads the current transport preference from persistent storage.
     */
    @Req("FR-BT-SEL-010", "TR-BT-SEL-010")
    suspend fun getTransportPreference(): TransportPreferenceResult =
        TransportPreferenceResult.Success(
            TransportPreference(BluetoothTransportMode.FIDO_BLE, 0L),
        )

    /**
     * Saves the transport preference atomically using an explicit wire value.
     */
    @Req("FR-BT-SEL-020", "TR-BT-SEL-010")
    suspend fun saveTransportPreference(
        transportCode: String,
        epochMillis: Long = System.currentTimeMillis(),
    ): TransportSaveResult =
        BluetoothTransportMode.fromWire(transportCode)?.let {
            TransportSaveResult.Success(TransportPreference(it, epochMillis))
        } ?: TransportSaveResult.InvalidInput(transportCode)

    /**
     * Saves the transport preference atomically using a typed mode.
     */
    @Req("FR-BT-SEL-020", "TR-BT-SEL-010")
    suspend fun saveTransportPreference(
        mode: BluetoothTransportMode,
        epochMillis: Long = System.currentTimeMillis(),
    ): TransportSaveResult = saveTransportPreference(mode.wireValue, epochMillis)
}

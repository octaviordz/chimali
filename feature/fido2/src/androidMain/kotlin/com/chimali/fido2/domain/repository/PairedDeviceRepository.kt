package com.chimali.fido2.domain.repository

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.fido2.domain.model.PairedDevice
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing tracked Bluetooth devices that have
 * successfully authenticated or registered via FIDO2.
 */
interface PairedDeviceRepository {
    /**
     * Retrieves all paired devices currently tracked by the authenticator,
     * ordered by their last usage time (most recent first).
     */
    fun getAllPairedDevices(): Flow<List<PairedDevice>>

    /**
     * Saves or updates a device record.
     */
    suspend fun saveDevice(device: PairedDevice): Outcome<Unit, DomainError>

    /**
     * Deletes a device record by its MAC address.
     * Note: This only drops the tracking record from Chimali; it does not
     * sever the Android OS Bluetooth bond.
     */
    suspend fun deleteDevice(macAddress: String): Outcome<Unit, DomainError>

    /**
     * Updates the user-defined alias for a paired device.
     */
    suspend fun updateAlias(
        macAddress: String,
        alias: String?,
    ): Outcome<Unit, DomainError>
}

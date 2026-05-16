package com.chimali.fido2.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import co.touchlab.kermit.Logger
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.map
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.domain.model.PairedDevice
import com.chimali.fido2.domain.repository.PairedDeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
class PairedDeviceRepositoryImpl(
    private val database: Fido2Database,
) : PairedDeviceRepository {
    companion object {
        private const val MAJOR_DEVICE_CLASS_MASK = 0x1F00
    }

    override fun getAllPairedDevices(): Flow<List<PairedDevice>> =
        database.pairedDeviceQueries
            .select_all()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { row ->
                    PairedDevice(
                        macAddress = row.mac_address,
                        name = row.name,
                        deviceClass = row.device_class?.toInt(),
                        alias = row.alias,
                        lastUsedAt = row.last_used_at,
                        createdAt = row.created_at,
                    )
                }
            }

    override suspend fun saveDevice(device: PairedDevice): Outcome<Unit, DomainError> =
        try {
            // We do a read-modify-write to preserve created_at if it already exists
            val existing = database.pairedDeviceQueries.select_by_address(device.macAddress).executeAsOneOrNull()

            // The event passes new deviceClass. However, sometimes Android reconnects and classifies
            // the host as "Uncategorized" (Major class 0). We should not let a generic 0 class
            // overwrite a previously saved known class (like Computer or Phone).
            val incomingClass = device.deviceClass
            val isIncomingGeneric = incomingClass == null || (incomingClass and MAJOR_DEVICE_CLASS_MASK) == 0

            val finalClassToSave =
                if (isIncomingGeneric && existing?.device_class != null) {
                    existing.device_class // Keep the old valid one
                } else {
                    incomingClass?.toLong() ?: existing?.device_class
                }

            database.pairedDeviceQueries.insert_or_replace(
                mac_address = device.macAddress,
                created_at = existing?.created_at ?: device.createdAt,
                last_used_at = device.lastUsedAt,
                alias = device.alias ?: existing?.alias,
                device_class = finalClassToSave,
                name = device.name ?: existing?.name,
            )
            Outcome.Success(Unit)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "Failed to save device (database error): ${device.macAddress}" }
            Outcome.Error(DomainError.DatabaseError("Failed to save device", e))
        }

    override suspend fun updateAlias(
        macAddress: String,
        alias: String?,
    ): Outcome<Unit, DomainError> =
        try {
            database.pairedDeviceQueries.update_alias(alias, macAddress)
            Outcome.Success(Unit)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "Failed to update alias for device (database error): $macAddress" }
            Outcome.Error(DomainError.DatabaseError("Failed to update alias", e))
        }

    override suspend fun deleteDevice(macAddress: String): Outcome<Unit, DomainError> =
        try {
            database.pairedDeviceQueries.delete_by_address(macAddress)
            Outcome.Success(Unit)
        } catch (e: android.database.SQLException) {
            Logger.e(e) { "Failed to delete device (database error): $macAddress" }
            Outcome.Error(DomainError.DatabaseError("Failed to delete device", e))
        }
}

package com.chimali.fido2.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
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
    override fun getAllPairedDevices(): Flow<List<PairedDevice>> {
        return database.pairedDeviceQueries
            .selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { row ->
                    PairedDevice(
                        macAddress = row.macAddress,
                        name = row.name,
                        deviceClass = row.deviceClass?.toInt(),
                        alias = row.alias,
                        lastUsedAt = row.lastUsedAt,
                        createdAt = row.createdAt,
                    )
                }
            }
    }

    override suspend fun saveDevice(device: PairedDevice): Result<Unit> =
        runCatching {
            // We do a read-modify-write to preserve createdAt if it already exists
            val existing = database.pairedDeviceQueries.selectByAddress(device.macAddress).executeAsOneOrNull()

            // The event passes new deviceClass. However, sometimes Android reconnects and classifies
            // the host as "Uncategorized" (Major class 0). We should not let a generic 0 class
            // overwrite a previously saved known class (like Computer or Phone).
            val incomingClass = device.deviceClass
            val isIncomingGeneric = incomingClass == null || (incomingClass and 0x1F00) == 0

            val finalClassToSave =
                if (isIncomingGeneric && existing?.deviceClass != null) {
                    existing.deviceClass // Keep the old valid one
                } else {
                    incomingClass?.toLong() ?: existing?.deviceClass
                }

            database.pairedDeviceQueries.insertOrReplace(
                macAddress = device.macAddress,
                createdAt = existing?.createdAt ?: device.createdAt,
                lastUsedAt = device.lastUsedAt,
                alias = device.alias ?: existing?.alias,
                deviceClass = finalClassToSave,
                name = device.name ?: existing?.name,
            )
        }

    override suspend fun updateAlias(
        macAddress: String,
        alias: String?,
    ): Result<Unit> =
        runCatching {
            database.pairedDeviceQueries.updateAlias(alias, macAddress)
        }

    override suspend fun deleteDevice(macAddress: String): Result<Unit> =
        runCatching {
            database.pairedDeviceQueries.deleteByAddress(macAddress)
        }
}

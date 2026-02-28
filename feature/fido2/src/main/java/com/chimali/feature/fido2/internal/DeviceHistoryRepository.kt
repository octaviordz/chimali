package com.chimali.feature.fido2.internal

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.chimali.core.database.ChimaliDatabase
import com.chimali.core.database.PairedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class DeviceHistoryRepository @Inject constructor(
    private val database: ChimaliDatabase
) {
    suspend fun recordConnection(address: String, name: String) = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        
        database.transaction {
            // Check if device already exists to avoid overwriting first_paired_at if we use INSERT OR REPLACE directly
            val existing = database.vaultQueries.getAllPairedDevices().executeAsList().find { it.address == address }
            if (existing == null) {
                database.vaultQueries.insertPairedDevice(
                    address = address,
                    name = name,
                    first_paired_at = now,
                    last_connected_at = now
                )
            } else {
                database.vaultQueries.updateLastConnected(
                    last_connected_at = now,
                    address = address
                )
            }
        }
    }

    fun getRecentDevices(days: Int = 15): Flow<List<PairedDevice>> {
        val cutoff = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        return database.vaultQueries.getAllPairedDevices()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { devices ->
                devices.filter { device ->
                    device.last_connected_at?.let { lastConnectedStr ->
                        try {
                            val lastConnected = Instant.parse(lastConnectedStr)
                            lastConnected.isAfter(cutoff)
                        } catch (e: Exception) {
                            false
                        }
                    } ?: false
                }.sortedByDescending { it.last_connected_at }
            }
    }
}

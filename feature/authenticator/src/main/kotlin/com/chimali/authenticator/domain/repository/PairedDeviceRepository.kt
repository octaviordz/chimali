package com.chimali.authenticator.domain.repository

import com.chimali.authenticator.domain.model.PairedDevice
import kotlinx.coroutines.flow.Flow

interface PairedDeviceRepository {
    fun getAllDevices(): Flow<List<PairedDevice>>
    suspend fun getDeviceById(deviceId: String): PairedDevice?
    suspend fun getDevicesByName(name: String): List<PairedDevice>
    suspend fun getDevicesByPlatform(platform: String): List<PairedDevice>
    fun getTrustedDevices(): Flow<List<PairedDevice>>
    suspend fun insertDevice(device: PairedDevice)
    suspend fun updateDevice(device: PairedDevice)
    suspend fun deleteDevice(deviceId: String)
    suspend fun updateLastConnected(deviceId: String)
    suspend fun updateTrustStatus(deviceId: String, isTrusted: Boolean)
    suspend fun getDeviceCount(): Int
}

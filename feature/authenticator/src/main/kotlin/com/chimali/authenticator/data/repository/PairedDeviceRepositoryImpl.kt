package com.chimali.authenticator.data.repository

import com.chimali.authenticator.data.local.dao.PairedDeviceDao
import com.chimali.authenticator.data.local.entity.PairedDeviceEntity
import com.chimali.authenticator.data.mapper.PairedDeviceMapper
import com.chimali.authenticator.domain.model.PairedDevice
import com.chimali.authenticator.domain.model.Platform
import com.chimali.authenticator.domain.repository.PairedDeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PairedDeviceRepositoryImpl @Inject constructor(
    private val pairedDeviceDao: PairedDeviceDao,
    private val mapper: PairedDeviceMapper
) : PairedDeviceRepository {
    
    override fun getAllDevices(): Flow<List<PairedDevice>> {
        return pairedDeviceDao.getAllDevices().map { entities ->
            entities.map { mapper.toDomain(it) }
        }
    }
    
    override suspend fun getDeviceById(deviceId: String): PairedDevice? {
        return pairedDeviceDao.getDeviceById(deviceId)?.let { mapper.toDomain(it) }
    }
    
    override suspend fun getDevicesByName(name: String): List<PairedDevice> {
        return pairedDeviceDao.getDevicesByName(name).map { mapper.toDomain(it) }
    }
    
    override suspend fun getDevicesByPlatform(platform: String): List<PairedDevice> {
        return pairedDeviceDao.getDevicesByPlatform(platform).map { mapper.toDomain(it) }
    }
    
    override fun getTrustedDevices(): Flow<List<PairedDevice>> {
        return pairedDeviceDao.getTrustedDevices().map { entities ->
            entities.map { mapper.toDomain(it) }
        }
    }
    
    override suspend fun insertDevice(device: PairedDevice) {
        pairedDeviceDao.insertDevice(mapper.toEntity(device))
    }
    
    override suspend fun updateDevice(device: PairedDevice) {
        pairedDeviceDao.updateDevice(mapper.toEntity(device))
    }
    
    override suspend fun deleteDevice(deviceId: String) {
        pairedDeviceDao.deleteDeviceById(deviceId)
    }
    
    override suspend fun updateLastConnected(deviceId: String) {
        pairedDeviceDao.updateLastConnected(deviceId, System.currentTimeMillis())
    }
    
    override suspend fun updateTrustStatus(deviceId: String, isTrusted: Boolean) {
        pairedDeviceDao.updateTrustStatus(deviceId, isTrusted)
    }
    
    override suspend fun getDeviceCount(): Int {
        return pairedDeviceDao.getDeviceCount()
    }
}

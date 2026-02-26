package com.chimali.authenticator.data.mapper

import com.chimali.authenticator.data.local.entity.PairedDeviceEntity
import com.chimali.authenticator.domain.model.PairedDevice
import com.chimali.authenticator.domain.model.Platform
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PairedDeviceMapper @Inject constructor(
    private val gson: Gson
) {
    
    fun toDomain(entity: PairedDeviceEntity): PairedDevice {
        val preferencesType = object : TypeToken<Map<String, String>>() {}.type
        val preferences = try {
            gson.fromJson<Map<String, String>>(entity.preferences, preferencesType) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
        
        return PairedDevice(
            deviceId = entity.deviceId,
            deviceName = entity.deviceName,
            platform = Platform.valueOf(entity.platform),
            lastConnected = entity.lastConnected,
            isTrusted = entity.isTrusted,
            preferences = preferences
        )
    }
    
    fun toEntity(domain: PairedDevice): PairedDeviceEntity {
        return PairedDeviceEntity(
            deviceId = domain.deviceId,
            deviceName = domain.deviceName,
            platform = domain.platform.name,
            lastConnected = domain.lastConnected,
            isTrusted = domain.isTrusted,
            preferences = gson.toJson(domain.preferences)
        )
    }
}

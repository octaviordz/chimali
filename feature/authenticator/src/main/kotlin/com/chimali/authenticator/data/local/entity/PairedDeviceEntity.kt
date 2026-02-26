package com.chimali.authenticator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "paired_devices",
    foreignKeys = [],
    indices = [
        Index(value = ["deviceName"]),
        Index(value = ["platform"]),
        Index(value = ["lastConnected"])
    ]
)
data class PairedDeviceEntity(
    @PrimaryKey
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    
    @ColumnInfo(name = "device_name")
    val deviceName: String,
    
    @ColumnInfo(name = "platform")
    val platform: String,
    
    @ColumnInfo(name = "last_connected")
    val lastConnected: Long,
    
    @ColumnInfo(name = "is_trusted")
    val isTrusted: Boolean,
    
    @ColumnInfo(name = "preferences")
    val preferences: String // JSON string
)

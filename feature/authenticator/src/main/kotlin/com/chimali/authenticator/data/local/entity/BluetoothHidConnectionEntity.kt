package com.chimali.authenticator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "bluetooth_hid_connections",
    foreignKeys = [
        ForeignKey(
            entity = PairedDeviceEntity::class,
            parentColumns = ["device_id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["device_id"]),
        Index(value = ["connection_state"]),
        Index(value = ["established_at"])
    ]
)
data class BluetoothHidConnectionEntity(
    @PrimaryKey
    @ColumnInfo(name = "connection_id")
    val connectionId: String,
    
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    
    @ColumnInfo(name = "connection_state")
    val connectionState: String, // "DISCONNECTED", "CONNECTING", "CONNECTED", "ERROR"
    
    @ColumnInfo(name = "established_at")
    val establishedAt: Long,
    
    @ColumnInfo(name = "protocol_version")
    val protocolVersion: Int,
    
    @ColumnInfo(name = "capabilities")
    val capabilities: String // JSON string
)

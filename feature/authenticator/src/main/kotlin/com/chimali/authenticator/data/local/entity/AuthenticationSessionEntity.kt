package com.chimali.authenticator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "authentication_sessions",
    foreignKeys = [
        ForeignKey(
            entity = PairedDeviceEntity::class,
            parentColumns = ["device_id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PasskeyEntity::class,
            parentColumns = ["credential_id"],
            childColumns = ["credential_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["device_id"]),
        Index(value = ["credential_id"]),
        Index(value = ["session_type"]),
        Index(value = ["session_state"]),
        Index(value = ["created_at"]),
        Index(value = ["expires_at"])
    ]
)
data class AuthenticationSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    
    @ColumnInfo(name = "credential_id")
    val credentialId: String,
    
    @ColumnInfo(name = "session_type")
    val sessionType: String, // "REGISTRATION", "AUTHENTICATION"
    
    @ColumnInfo(name = "session_state")
    val sessionState: String, // "PENDING", "ACTIVE", "COMPLETED", "FAILED", "EXPIRED"
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long,
    
    @ColumnInfo(name = "request_payload")
    val requestPayload: String, // JSON string
    
    @ColumnInfo(name = "response_payload")
    val responsePayload: String // JSON string
)

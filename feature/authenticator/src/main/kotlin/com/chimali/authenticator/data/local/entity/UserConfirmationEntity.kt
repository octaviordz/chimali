package com.chimali.authenticator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "user_confirmations",
    foreignKeys = [
        ForeignKey(
            entity = AuthenticationSessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["confirmation_type"]),
        Index(value = ["authentication_method"]),
        Index(value = ["requested_at"]),
        Index(value = ["responded_at"])
    ]
)
data class UserConfirmationEntity(
    @PrimaryKey
    @ColumnInfo(name = "confirmation_id")
    val confirmationId: String,
    
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    
    @ColumnInfo(name = "confirmation_type")
    val confirmationType: String, // "BIOMETRIC", "PIN", "PATTERN"
    
    @ColumnInfo(name = "authentication_method")
    val authenticationMethod: String, // "FINGERPRINT", "FACE", "VOICE", "PIN"
    
    @ColumnInfo(name = "requested_at")
    val requestedAt: Long,
    
    @ColumnInfo(name = "responded_at")
    val respondedAt: Long,
    
    @ColumnInfo(name = "is_approved")
    val isApproved: Boolean,
    
    @ColumnInfo(name = "denial_reason")
    val denialReason: String?
)

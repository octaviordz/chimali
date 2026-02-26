package com.chimali.authenticator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index

@Entity(
    tableName = "passkeys",
    indices = [
        Index(value = ["relying_party_id"]),
        Index(value = ["user_id"]),
        Index(value = ["created_at"]),
        Index(value = ["last_used"])
    ]
)
data class PasskeyEntity(
    @PrimaryKey
    @ColumnInfo(name = "credential_id")
    val credentialId: String,
    
    @ColumnInfo(name = "relying_party_id")
    val relyingPartyId: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "user_name")
    val userName: String,
    
    @ColumnInfo(name = "display_name")
    val displayName: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "last_used")
    val lastUsed: Long,
    
    @ColumnInfo(name = "key_metadata")
    val keyMetadata: String, // JSON string
    
    @ColumnInfo(name = "is_user_verified")
    val isUserVerified: Boolean
)

package com.chimali.authenticator.domain.model

import java.util.UUID

data class PairedDevice(
    val deviceId: String = UUID.randomUUID().toString(),
    val deviceName: String,
    val platform: Platform,
    val lastConnected: Long = System.currentTimeMillis(),
    val isTrusted: Boolean = false,
    val preferences: Map<String, String> = emptyMap()
)

enum class Platform {
    WINDOWS,
    MACOS,
    LINUX,
    UNKNOWN
}

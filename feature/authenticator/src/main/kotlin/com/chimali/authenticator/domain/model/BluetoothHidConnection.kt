package com.chimali.authenticator.domain.model

import java.util.UUID

data class BluetoothHidConnection(
    val connectionId: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val establishedAt: Long = System.currentTimeMillis(),
    val protocolVersion: Int = 1,
    val capabilities: Map<String, Any> = emptyMap()
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

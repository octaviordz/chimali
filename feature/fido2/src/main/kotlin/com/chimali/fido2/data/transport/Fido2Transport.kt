package com.chimali.fido2.data.transport

import com.chimali.fido2.bluetooth.HidConnectionState
import kotlinx.coroutines.flow.StateFlow

interface Fido2Transport {
    val connectionState: StateFlow<HidConnectionState>
    suspend fun connect(): Result<Unit>
    suspend fun disconnect(): Result<Unit>
    suspend fun sendCommand(command: ByteArray): Result<ByteArray>
    suspend fun isConnected(): Boolean
}

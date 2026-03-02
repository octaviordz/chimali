package com.chimali.fido2.data.transport

interface Fido2Transport {
    suspend fun connect(): Result<Unit>
    suspend fun disconnect(): Result<Unit>
    suspend fun sendCommand(command: ByteArray): Result<ByteArray>
    suspend fun isConnected(): Boolean
}

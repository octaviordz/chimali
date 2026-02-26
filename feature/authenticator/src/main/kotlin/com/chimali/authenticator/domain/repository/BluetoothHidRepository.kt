package com.chimali.authenticator.domain.repository

import com.chimali.authenticator.domain.model.BluetoothHidConnection
import kotlinx.coroutines.flow.Flow

interface BluetoothHidRepository {
    fun getAllConnections(): Flow<List<BluetoothHidConnection>>
    suspend fun getConnectionById(connectionId: String): BluetoothHidConnection?
    fun getConnectionsByDevice(deviceId: String): Flow<List<BluetoothHidConnection>>
    fun getConnectionsByState(state: BluetoothHidConnection.ConnectionState): Flow<List<BluetoothHidConnection>>
    suspend fun getActiveConnectionForDevice(deviceId: String): BluetoothHidConnection?
    suspend fun createConnection(connection: BluetoothHidConnection)
    suspend fun updateConnection(connection: BluetoothHidConnection)
    suspend fun deleteConnection(connectionId: String)
    suspend fun deleteConnectionsByDevice(deviceId: String)
    suspend fun updateConnectionState(connectionId: String, state: BluetoothHidConnection.ConnectionState)
    suspend fun getActiveConnectionCount(): Int
    
    // Bluetooth-specific operations
    suspend fun startBluetoothDiscovery(): Boolean
    suspend fun stopBluetoothDiscovery(): Boolean
    suspend fun connectToDevice(deviceId: String): Boolean
    suspend fun disconnectFromDevice(deviceId: String): Boolean
    suspend fun sendHidData(connectionId: String, data: ByteArray): Boolean
    suspend fun receiveHidData(connectionId: String): ByteArray?
}

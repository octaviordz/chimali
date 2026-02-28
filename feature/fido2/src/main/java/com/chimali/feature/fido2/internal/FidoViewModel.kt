package com.chimali.feature.fido2.internal

import android.annotation.SuppressLint
import android.util.Log
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chimali.feature.fido2.api.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.chimali.core.fido2.CtapProcessor

@HiltViewModel
class FidoViewModel @Inject constructor(
    private val repository: CredentialRepository,
    private val requestQueue: RequestQueue,
    private val bleGattManager: com.chimali.core.bluetooth.impl.BleGattManager,
    private val deviceHistoryRepository: DeviceHistoryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val ctapProcessor = CtapProcessor()

    private val _state = MutableStateFlow(FidoState())
    val state: StateFlow<FidoState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<FidoEffect>()
    val effect: SharedFlow<FidoEffect> = _effect.asSharedFlow()

    init {
        loadDevices()
        /* 
        // Classic Bluetooth Discovery removed for Pivot to BLE
        viewModelScope.launch {
            hidManager.discoveredDevices.collect { devices ->
                ...
            }
        }
        */
        _state.update { it.copy(
            isScanning = false, 
            isRefreshing = false,
            isBlePeripheralSupported = bleGattManager.isPeripheralSupported()
        ) }
        viewModelScope.launch {
            deviceHistoryRepository.getRecentDevices().collect { recent ->
                val uiRecent = recent.map { device ->
                    com.chimali.feature.fido2.ui.PairedDevice(
                        address = device.address,
                        name = device.name,
                        isConnected = false 
                    )
                }
                _state.update { it.copy(recentDevices = uiRecent) }
            }
        }
        viewModelScope.launch {
            bleGattManager.incomingRequests.collect { (address, packet) ->
                onIntent(FidoIntent.AuthRequestReceived(address, packet))
            }
        }
        
        bleGattManager.onError = { message ->
            viewModelScope.launch {
                _effect.emit(FidoEffect.ShowToast("Bluetooth Error: $message"))
            }
        }
        
        viewModelScope.launch {
            bleGattManager.connectionEvents.collect { (address, isConnected) ->
                Log.d("FidoViewModel", "Connection event for $address: $isConnected")
                if (isConnected) {
                    try {
                        val device = bleGattManager.getConnectedDevices().find { it.address == address }
                        val name = device?.name ?: "Unknown Device"
                        deviceHistoryRepository.recordConnection(address, name)
                    } catch (e: Exception) {
                        Log.e("FidoViewModel", "Failed to record recent device for $address", e)
                    }
                }
                loadDevices()
            }
        }
        
        // Start foreground service to keep Bluetooth alive
        try {
            val serviceIntent = Intent(context, FidoBleService::class.java)
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onIntent(intent: FidoIntent) {
        when (intent) {
            is FidoIntent.AuthRequestReceived -> handleAuthRequest(intent)
            FidoIntent.UserConfirmed -> approveRequest()
            FidoIntent.UserCancelled -> cancelRequest()
            FidoIntent.ConnectionStatusRequested -> checkConnection()
            FidoIntent.RefreshDevices -> {
                loadDevices()
            }
            FidoIntent.StartScan -> { /* Removed for BLE Pivot */ }
            FidoIntent.StopScan -> { /* Removed for BLE Pivot */ }
            is FidoIntent.PairDevice -> { /* Managed by System for BLE */ }
            is FidoIntent.ConnectDevice -> connectDevice(intent.address)
            is FidoIntent.DisconnectDevice -> disconnectDevice(intent.address)
            is FidoIntent.UnpairDevice -> unpairDevice(intent.address)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectDevice(address: String) {
        // BLE device connection is managed by the FidoBleService and GATT server. 
        // The phone is an advertising PERIPHERAL. The PC must initiate.
        viewModelScope.launch {
            _effect.emit(FidoEffect.ShowToast("Phone is advertising. Please initiate connection from your PC's WebAuthn prompt."))
        }
        loadDevices()
    }

    @SuppressLint("MissingPermission")
    private fun loadDevices() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            val connectedAddresses = bleGattManager.getConnectedDevices().map { it.address }.toSet()
            val bondedDevices = bleGattManager.getBondedDevices()
            
            val recentAddresses = _state.value.recentDevices.map { it.address }.toSet()
            val paired = bondedDevices
                .filter { !recentAddresses.contains(it.address) }
                .map { device ->
                    com.chimali.feature.fido2.ui.PairedDevice(
                        address = device.address,
                        name = device.name ?: "Unknown Device",
                        isConnected = connectedAddresses.contains(device.address)
                    )
                }
            
            // Also update connection status of recent devices
            val updatedRecent = _state.value.recentDevices.map { recent ->
                recent.copy(isConnected = connectedAddresses.contains(recent.address))
            }
            
            _state.update { it.copy(pairedDevices = paired, recentDevices = updatedRecent, isRefreshing = false) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectDevice(address: String) {
        // BLE GATT server doesn't usually initiate disconnect in FIDO2, 
        // but we can close the GATT server or just ignore.
        loadDevices()
    }

    @SuppressLint("MissingPermission")
    private fun unpairDevice(address: String) {
        // Managed by system bluetooth settings for BLE
        loadDevices()
    }

    private fun handleAuthRequest(intent: FidoIntent.AuthRequestReceived) {
        if (intent.payload.isNotEmpty() && intent.payload[0].toInt() == 0x04) {
            // Non-interactive: getInfo must be answered immediately
            viewModelScope.launch {
                try {
                    val response = ctapProcessor.processPacket(intent.payload)
                    val fullResponse = byteArrayOf(response.status) + response.data
                    bleGattManager.sendReport(intent.deviceAddress, fullResponse)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return
        }

        // Simplified parsing of RP ID and User from payload
        val pending = PendingAuthRequest(
            deviceAddress = intent.deviceAddress,
            relyingPartyId = "example.com", // TODO: Parse from CTAP payload
            userName = "User",
            payload = intent.payload
        )
        _state.update { it.copy(pendingAuthRequest = pending) }
    }

    @SuppressLint("MissingPermission")
    private fun approveRequest() {
        viewModelScope.launch {
            _effect.emit(FidoEffect.RequestBiometric)
            val pending = _state.value.pendingAuthRequest ?: return@launch
            
            try {
                // Process the raw packet
                val response = ctapProcessor.processPacket(pending.payload)
                
                // Pack the response. CTAP2 responses start with a status byte.
                val fullResponse = byteArrayOf(response.status) + response.data
                
                if (bleGattManager.getConnectedDevices().any { it.address == pending.deviceAddress }) {
                    bleGattManager.sendReport(pending.deviceAddress, fullResponse)
                    
                    // Keep the service happy
                    val device = bleGattManager.getConnectedDevices().find { it.address == pending.deviceAddress }
                    val name = device?.name ?: "Unknown Device"
                    deviceHistoryRepository.recordConnection(pending.deviceAddress, name)
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _state.update { it.copy(pendingAuthRequest = null) }
                loadDevices()
            }
        }
    }

    private fun cancelRequest() {
        _state.update { it.copy(pendingAuthRequest = null) }
    }

    private fun checkConnection() {
        // Implementation for checking HID registration status
    }
}

package com.chimali.feature.fido2.internal

import android.annotation.SuppressLint
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
    private val hidManager: com.chimali.core.bluetooth.HidManager,
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
        viewModelScope.launch {
            hidManager.discoveredDevices.collect { devices ->
                val available = devices.map { device ->
                    @SuppressLint("MissingPermission")
                    val name = device.name ?: "Unknown Device"
                    com.chimali.feature.fido2.ui.PairedDevice(
                        address = device.address,
                        name = name,
                        isConnected = false
                    )
                }
                _state.update { it.copy(discoveredDevices = available) }
            }
        }
        viewModelScope.launch {
            hidManager.isScanning.collect { scanning ->
                _state.update { it.copy(isScanning = scanning, isRefreshing = scanning) }
            }
        }
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
            hidManager.incomingRequests.collect { (address, packet) ->
                onIntent(FidoIntent.AuthRequestReceived(address, packet))
            }
        }
        
        // Start foreground service to keep Bluetooth alive
        try {
            val serviceIntent = Intent(context, HidService::class.java)
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
                hidManager.startDiscovery()
            }
            FidoIntent.StartScan -> hidManager.startDiscovery()
            FidoIntent.StopScan -> hidManager.stopDiscovery()
            is FidoIntent.PairDevice -> hidManager.pairDevice(intent.address)
            is FidoIntent.ConnectDevice -> connectDevice(intent.address)
            is FidoIntent.DisconnectDevice -> disconnectDevice(intent.address)
            is FidoIntent.UnpairDevice -> unpairDevice(intent.address)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectDevice(address: String) {
        viewModelScope.launch {
            // Check both paired and available lists for the device we want to connect to
            val device = hidManager.getPairedDevices().find { it.address == address }
                ?: hidManager.discoveredDevices.value.find { it.address == address }

            if (device != null) {
                if (hidManager.connectDevice(device)) {
                    val name = device.name ?: "Unknown Device"
                    deviceHistoryRepository.recordConnection(device.address, name)
                }
                loadDevices()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadDevices() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            val connectedAddresses = hidManager.getConnectedDevices().map { it.address }.toSet()
            val paired = hidManager.getPairedDevices().map { device ->
                com.chimali.feature.fido2.ui.PairedDevice(
                    address = device.address,
                    name = device.name ?: "Unknown Device",
                    isConnected = connectedAddresses.contains(device.address)
                )
            }.filterNot { pairedDevice ->
                _state.value.recentDevices.any { recent -> recent.address == pairedDevice.address }
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
        viewModelScope.launch {
            val device = hidManager.getConnectedDevices().find { it.address == address }
            if (device != null) {
                hidManager.disconnectDevice(device)
                loadDevices()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun unpairDevice(address: String) {
        viewModelScope.launch {
            val device = hidManager.getPairedDevices().find { it.address == address }
            if (device != null) {
                hidManager.unpairDevice(device)
                loadDevices()
            }
        }
    }

    private fun handleAuthRequest(intent: FidoIntent.AuthRequestReceived) {
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
                
                // Pack the response. For a simple mockup, CTAP HID has headers but we'll assume sendReport correctly fragments
                // In a real CTAP2 over HID, we'd wrap this in an INIT response packet.
                // For the quickstart MVP, we send back a success block
                val device = hidManager.getConnectedDevices().find { it.address == pending.deviceAddress }
                if (device != null) {
                    hidManager.sendReport(device, response.data)
                }
                
                // Keep the service happy
                val name = device?.name ?: "Unknown Device"
                deviceHistoryRepository.recordConnection(pending.deviceAddress, name)
                
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

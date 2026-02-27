package com.chimali.feature.fido2.internal

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chimali.feature.fido2.api.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FidoViewModel @Inject constructor(
    private val repository: CredentialRepository,
    private val requestQueue: RequestQueue,
    private val hidManager: com.chimali.core.bluetooth.HidManager
) : ViewModel() {

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
            val device = hidManager.getPairedDevices().find { it.address == address }
            if (device != null) {
                hidManager.connectDevice(device)
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
            }
            _state.update { it.copy(pairedDevices = paired, isRefreshing = false) }
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
            userName = "User" 
        )
        _state.update { it.copy(pendingAuthRequest = pending) }
    }

    private fun approveRequest() {
        viewModelScope.launch {
            _effect.emit(FidoEffect.RequestBiometric)
        }
    }

    private fun cancelRequest() {
        _state.update { it.copy(pendingAuthRequest = null) }
    }

    private fun checkConnection() {
        // Implementation for checking HID registration status
    }
}

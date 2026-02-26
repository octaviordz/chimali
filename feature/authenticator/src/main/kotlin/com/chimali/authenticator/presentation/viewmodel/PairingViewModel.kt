package com.chimali.authenticator.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chimali.authenticator.domain.model.PairedDevice
import com.chimali.authenticator.domain.model.BluetoothHidConnection
import com.chimali.authenticator.domain.usecase.Result
import com.chimali.authenticator.data.repository.PairingRepository
import com.chimali.authenticator.domain.logging.AuthenticatorLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val pairingRepository: PairingRepository,
    private val logger: AuthenticatorLogger
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()
    
    init {
        observeDevicesAndConnections()
    }
    
    private fun observeDevicesAndConnections() {
        viewModelScope.launch {
            try {
                pairingRepository.getPairedDevices().collect { devices ->
                    _uiState.value = _uiState.value.copy(pairedDevices = devices)
                }
            } catch (e: Exception) {
                logger.e("PairingViewModel", "Error observing paired devices", e)
                _uiState.value = _uiState.value.copy(error = "Failed to load paired devices")
            }
        }
        
        viewModelScope.launch {
            try {
                pairingRepository.getDeviceConnections().collect { connections ->
                    _uiState.value = _uiState.value.copy(connections = connections)
                }
            } catch (e: Exception) {
                logger.e("PairingViewModel", "Error observing connections", e)
                _uiState.value = _uiState.value.copy(error = "Failed to load connections")
            }
        }
    }
    
    fun loadPairedDevices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // Devices and connections are already being observed
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                logger.e("PairingViewModel", "Error loading devices", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load devices: ${e.message}"
                )
            }
        }
    }
    
    fun startDiscovery() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDiscovering = true, error = null)
            
            when (val result = pairingRepository.startDiscovery()) {
                is Result.Success -> {
                    logger.i("PairingViewModel", "Device discovery started successfully")
                }
                is Result.Error -> {
                    logger.e("PairingViewModel", "Failed to start discovery", result.error)
                    _uiState.value = _uiState.value.copy(
                        isDiscovering = false,
                        error = "Failed to start discovery: ${result.error.message}"
                    )
                }
                is Result.Loading -> {
                    // Discovery is starting
                }
            }
        }
    }
    
    fun stopDiscovery() {
        viewModelScope.launch {
            when (val result = pairingRepository.stopDiscovery()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isDiscovering = false)
                    logger.i("PairingViewModel", "Device discovery stopped successfully")
                }
                is Result.Error -> {
                    logger.e("PairingViewModel", "Failed to stop discovery", result.error)
                    _uiState.value = _uiState.value.copy(
                        isDiscovering = false,
                        error = "Failed to stop discovery: ${result.error.message}"
                    )
                }
                is Result.Loading -> {
                    // Discovery is stopping
                }
            }
        }
    }
    
    fun connectToDevice(deviceId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            when (val result = pairingRepository.connectToDevice(deviceId)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    logger.i("PairingViewModel", "Device connection initiated: $deviceId")
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to connect: ${result.error.message}"
                    )
                    logger.e("PairingViewModel", "Failed to connect to device", result.error)
                }
                is Result.Loading -> {
                    // Connection is in progress
                }
            }
        }
    }
    
    fun disconnectFromDevice(deviceId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            when (val result = pairingRepository.disconnectFromDevice(deviceId)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    logger.i("PairingViewModel", "Device disconnected: $deviceId")
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to disconnect: ${result.error.message}"
                    )
                    logger.e("PairingViewModel", "Failed to disconnect from device", result.error)
                }
                is Result.Loading -> {
                    // Disconnection is in progress
                }
            }
        }
    }
    
    fun unpairDevice(deviceId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            when (val result = pairingRepository.unpairDevice(deviceId)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    logger.i("PairingViewModel", "Device unpaired: $deviceId")
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to unpair: ${result.error.message}"
                    )
                    logger.e("PairingViewModel", "Failed to unpair device", result.error)
                }
                is Result.Loading -> {
                    // Unpairing is in progress
                }
            }
        }
    }
    
    fun updateTrustStatus(deviceId: String, isTrusted: Boolean) {
        viewModelScope.launch {
            when (val result = pairingRepository.updateDeviceTrustStatus(deviceId, isTrusted)) {
                is Result.Success -> {
                    logger.i("PairingViewModel", "Trust status updated: $deviceId -> $isTrusted")
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to update trust status: ${result.error.message}"
                    )
                    logger.e("PairingViewModel", "Failed to update trust status", result.error)
                }
                is Result.Loading -> {
                    // Update is in progress
                }
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class PairingUiState(
    val isLoading: Boolean = false,
    val isDiscovering: Boolean = false,
    val isServiceActive: Boolean = true, // This would come from service status
    val pairedDevices: List<PairedDevice> = emptyList(),
    val connections: List<BluetoothHidConnection> = emptyList(),
    val error: String? = null
)

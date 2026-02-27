package com.chimali.feature.fido2.internal

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
    private val requestQueue: RequestQueue
) : ViewModel() {

    private val _state = MutableStateFlow(FidoState())
    val state: StateFlow<FidoState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<FidoEffect>()
    val effect: SharedFlow<FidoEffect> = _effect.asSharedFlow()

    fun onIntent(intent: FidoIntent) {
        when (intent) {
            is FidoIntent.AuthRequestReceived -> handleAuthRequest(intent)
            FidoIntent.UserConfirmed -> approveRequest()
            FidoIntent.UserCancelled -> cancelRequest()
            FidoIntent.ConnectionStatusRequested -> checkConnection()
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

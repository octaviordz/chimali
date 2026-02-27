package com.chimali.feature.fido2.api

sealed interface FidoIntent {
    data class AuthRequestReceived(val deviceAddress: String, val payload: ByteArray) : FidoIntent
    data object UserConfirmed : FidoIntent
    data object UserCancelled : FidoIntent
    data object ConnectionStatusRequested : FidoIntent
    data object RefreshDevices : FidoIntent
    data class DisconnectDevice(val address: String) : FidoIntent
    data class UnpairDevice(val address: String) : FidoIntent
}

data class FidoState(
    val isHidRegistered: Boolean = false,
    val pairedDevices: List<com.chimali.feature.fido2.ui.PairedDevice> = emptyList(),
    val connectedDeviceName: String? = null,
    val pendingAuthRequest: PendingAuthRequest? = null,
    val error: String? = null
)

data class PendingAuthRequest(
    val deviceAddress: String,
    val relyingPartyId: String,
    val userName: String
)

sealed interface FidoEffect {
    data class ShowToast(val message: String) : FidoEffect
    data object RequestBiometric : FidoEffect
}

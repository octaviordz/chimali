package com.chimali.feature.settings.ui

/** Stateless presentation input for the FIDO transport selector. */
data class BluetoothTransportSettingsUiState(
    val savedTransportCode: String?,
    val processTransportCode: String?,
    val availabilityCode: String,
    val isLoading: Boolean,
    val isSaving: Boolean,
    val readError: String?,
    val saveError: String?,
    val restartRequired: Boolean,
    val processChoiceResolved: Boolean = processTransportCode != null,
)

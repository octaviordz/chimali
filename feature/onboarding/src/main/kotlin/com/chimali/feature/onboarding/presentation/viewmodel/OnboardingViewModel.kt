package com.chimali.feature.onboarding.presentation.viewmodel

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import com.chimali.core.common.datastore.UserPreferences
import com.chimali.feature.onboarding.presentation.model.OnboardingStep
import com.chimali.feature.onboarding.presentation.model.OnboardingUiState
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val DEFAULT_VAULT_ROUTE = "vault/home"
private const val DEFAULT_AUTHENTICATOR_ROUTE = "fido2/home"

class OnboardingViewModel(
    private val dataStore: DataStore<UserPreferences>,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onContinueFromIntro() {
        _state.update { it.copy(step = OnboardingStep.FeatureSelection, errorMessage = null) }
    }

    fun onVaultToggled(enabled: Boolean) {
        _state.update { it.copy(vaultSelected = enabled, errorMessage = null) }
    }

    fun onPasskeyToggled(enabled: Boolean) {
        _state.update { it.copy(passkeySelected = enabled, errorMessage = null) }
    }

    fun onBackToIntro() {
        _state.update { it.copy(step = OnboardingStep.Intro, errorMessage = null) }
    }

    suspend fun finishOnboarding(): Boolean {
        val currentState = _state.value
        if (!currentState.canContinue) {
            _state.update { it.copy(errorMessage = "Select at least one feature to continue.") }
            return false
        }

        _state.update { it.copy(isSaving = true, errorMessage = null) }
        return try {
            dataStore.updateData { preferences ->
                preferences.copy(
                    onboardingCompleted = true,
                    vaultFeatureEnabled = currentState.vaultSelected,
                    passkeyAuthenticatorFeatureEnabled = currentState.passkeySelected,
                    lastVisitedMainScreen = currentState.defaultMainScreenRoute(),
                )
            }
            _state.update { it.copy(isSaving = false) }
            true
        } catch (exception: IOException) {
            _state.update {
                it.copy(
                    isSaving = false,
                    errorMessage = exception.message ?: "Unable to save onboarding preferences.",
                )
            }
            false
        }
    }

    private fun OnboardingUiState.defaultMainScreenRoute(): String =
        when {
            vaultSelected && passkeySelected -> DEFAULT_AUTHENTICATOR_ROUTE
            vaultSelected -> DEFAULT_VAULT_ROUTE
            passkeySelected -> DEFAULT_AUTHENTICATOR_ROUTE
            else -> DEFAULT_AUTHENTICATOR_ROUTE
        }
}

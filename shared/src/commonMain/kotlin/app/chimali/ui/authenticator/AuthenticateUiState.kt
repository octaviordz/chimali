package app.chimali.ui.authenticator

import app.chimali.core.model.data.SelectableAppFeature

/**
 * A sealed hierarchy describing the onboarding state for the for you screen.
 */
sealed interface AuthenticateUiState {
    /**
     * The onboarding state is loading.
     */
    data object Loading : AuthenticateUiState

    /**
     * The onboarding state was unable to load.
     */
    data object LoadFailed : AuthenticateUiState

    /**
     * There is no onboarding state.
     */
    data object NotShown : AuthenticateUiState

    /**
     * There is an onboarding state, with the given lists of features.
     */
    data class Shown(
        val features: List<SelectableAppFeature>,
    ) : AuthenticateUiState {
        val isDismissable: Boolean get() = features.any { it.isSelected }
    }
}

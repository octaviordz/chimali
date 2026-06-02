package app.chimali.ui.authenticator

import app.chimali.core.model.data.SelectableAppFeature

/**
 * A sealed hierarchy describing the onboarding state for the for you screen.
 */
sealed interface OnboardingUiState {
    /**
     * The onboarding state is loading.
     */
    data object Loading : OnboardingUiState

    /**
     * The onboarding state was unable to load.
     */
    data object LoadFailed : OnboardingUiState

    /**
     * There is no onboarding state.
     */
    data object NotShown : OnboardingUiState

    /**
     * There is an onboarding state, with the given lists of features.
     */
    data class Shown(
        val features: List<SelectableAppFeature>,
    ) : OnboardingUiState {
        val isDismissable: Boolean get() = features.any { it.isSelected }
    }
}

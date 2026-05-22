package com.chimali.feature.onboarding.presentation.model

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Intro,
    val vaultSelected: Boolean = false,
    val passkeySelected: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    val canContinue: Boolean = vaultSelected || passkeySelected
}

enum class OnboardingStep {
    Intro,
    FeatureSelection,
}

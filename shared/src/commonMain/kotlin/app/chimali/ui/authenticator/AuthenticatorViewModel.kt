package app.chimali.ui.authenticator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chimali.core.data.repository.UserDataRepository
import app.chimali.core.domain.GetSelectableAppFeatureUseCase
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@OptIn(FlowPreview::class)
@KoinViewModel
class AuthenticatorViewModel(
    private val userDataRepository: UserDataRepository,
    getSelectableFeature: GetSelectableAppFeatureUseCase,
) : ViewModel() {
    private val shouldShowOnboarding: Flow<Boolean> =
        userDataRepository.userData.map { !it.shouldHideOnboarding }

    val onboardingUiState: StateFlow<OnboardingUiState> =
        combine(
            shouldShowOnboarding,
            getSelectableFeature(),
        ) { shouldShowOnboarding, features ->
            if (shouldShowOnboarding) {
                OnboardingUiState.Shown(features = features)
            } else {
                OnboardingUiState.NotShown
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OnboardingUiState.Loading,
        )

    fun updateSelectableFeature(
        featureName: String,
        isChecked: Boolean,
    ) {
        viewModelScope.launch {
            userDataRepository.setSelectableAppFeature(featureName, isChecked)
        }
    }

    fun dismissOnboarding() {
        viewModelScope.launch {
            userDataRepository.setShouldHideOnboarding(shouldHideOnboarding = true)
        }
    }
}

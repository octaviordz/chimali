package app.chimali.ui.authenticator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chimali.core.data.repository.UserDataRepository
import app.chimali.core.domain.GetSelectableAppFeatureUseCase
import app.chimali.core.model.data.AppFeatureId
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class AuthenticatorViewModel(
    getSelectableFeature: GetSelectableAppFeatureUseCase,
) : ViewModel() {
    val onboardingUiState: StateFlow<AuthenticateUiState> =
        getSelectableFeature()
            .map { features ->
                AuthenticateUiState.Shown(features = features)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = AuthenticateUiState.Loading,
            )
}

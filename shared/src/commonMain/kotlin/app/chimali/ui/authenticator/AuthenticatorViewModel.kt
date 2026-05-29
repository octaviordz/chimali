package app.chimali.ui.authenticator

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chimali.core.data.repository.UserDataRepository
import app.chimali.core.domain.GetSelectableFeatureUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// class AuthenticatorViewModel : ViewModel() {
//    // 1. Initialize data safely using a multiplatform MutableStateFlow
//    private val _texts =
//        MutableStateFlow<List<String>>(
//            (1..16).map { i -> "This is item # $i" },
//        )
//
//    // 2. Expose an immutable StateFlow for your UI layer to observe safely
//    val texts: StateFlow<List<String>> = _texts.asStateFlow()
// }

class AuthenticatorViewModel(
    private val userDataRepository: UserDataRepository,
    getFollowableTopics: GetSelectableFeatureUseCase,
) : ViewModel() {
    private val shouldShowOnboarding: Flow<Boolean> =
        userDataRepository.userData.map { !it.shouldHideOnboarding }

    val onboardingUiState: StateFlow<OnboardingUiState> =
        combine(
            shouldShowOnboarding,
            getFollowableTopics(),
        ) { shouldShowOnboarding, topics ->
            if (shouldShowOnboarding) {
                OnboardingUiState.Shown(topics = topics)
            } else {
                OnboardingUiState.NotShown
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OnboardingUiState.Loading,
        )

    fun updateTopicSelection(
        topicId: String,
        isChecked: Boolean,
    ) {
        viewModelScope.launch {
            userDataRepository.setTopicIdFollowed(topicId, isChecked)
        }
    }

    fun updateNewsResourceSaved(
        newsResourceId: String,
        isChecked: Boolean,
    ) {
        viewModelScope.launch {
            userDataRepository.setNewsResourceBookmarked(newsResourceId, isChecked)
        }
    }

    fun dismissOnboarding() {
        viewModelScope.launch {
            userDataRepository.setShouldHideOnboarding(true)
        }
    }
}

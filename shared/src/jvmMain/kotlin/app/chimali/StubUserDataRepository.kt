package app.chimali

import app.chimali.core.data.repository.UserDataRepository
import app.chimali.core.domain.GetSelectableFeatureUseCase
import app.chimali.core.model.data.DarkThemeConfig
import app.chimali.core.model.data.UserData
import app.chimali.ui.authenticator.AuthenticatorViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * In-memory stub implementation of [UserDataRepository] for the desktop (JVM) target.
 * Provides a minimal no-op implementation so Koin can wire up [AuthenticatorViewModel]
 * without a real persistence backend.
 */
private class StubUserDataRepository : UserDataRepository {
    private val _userData = MutableStateFlow(
        UserData(
            bookmarkedNewsResources = emptySet(),
            viewedNewsResources = emptySet(),
            followedTopics = emptySet(),
            darkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
            useDynamicColor = false,
            shouldHideOnboarding = false,
        ),
    )
    override val userData: Flow<UserData> = _userData

    override suspend fun setFollowedTopicIds(followedTopicIds: Set<String>) = Unit
    override suspend fun setSelectableFeature(name: String, selected: Boolean) = Unit
    override suspend fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig) = Unit
    override suspend fun setShouldHideOnboarding(shouldHideOnboarding: Boolean) = Unit
}

actual fun platformDiModule(): Module = module {
    single<UserDataRepository> { StubUserDataRepository() }
    single { GetSelectableFeatureUseCase() }
    viewModel { AuthenticatorViewModel(get(), get()) }
}

package app.chimali.core.data.repository

import app.chimali.core.datastore.PreferencesDataSource
import app.chimali.core.model.data.DarkThemeConfig
import app.chimali.core.model.data.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

class OfflineFirstUserDataRepository(
    private val niaPreferencesDataSource: PreferencesDataSource,
) : UserDataRepository {
    override val userData: Flow<UserData> =
        niaPreferencesDataSource.userData

    override suspend fun setSelectableAppFeature(
        featureId: String,
        selected: Boolean,
    ) {
        niaPreferencesDataSource.setAppFeatureIdSelected(featureId, selected)
    }

    override suspend fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig) {
        niaPreferencesDataSource.setDarkThemeConfig(darkThemeConfig)
    }

    override suspend fun setShouldHideOnboarding(shouldHideOnboarding: Boolean) {
        niaPreferencesDataSource.setShouldHideOnboarding(shouldHideOnboarding)
    }
}

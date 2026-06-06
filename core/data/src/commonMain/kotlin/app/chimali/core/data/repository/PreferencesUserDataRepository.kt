package app.chimali.core.data.repository

import app.chimali.core.datastore.PreferencesDataSource
import app.chimali.core.model.data.AppFeatureId
import app.chimali.core.model.data.DarkThemeConfig
import app.chimali.core.model.data.UserData
import kotlinx.coroutines.flow.Flow

class PreferencesUserDataRepository(
    private val preferencesDataSource: PreferencesDataSource,
) : UserDataRepository {
    override val userData: Flow<UserData> =
        preferencesDataSource.userData

    override suspend fun setSelectableAppFeature(
        appFeatureId: AppFeatureId,
        selected: Boolean,
    ) {
        preferencesDataSource.setAppFeatureIdSelected(appFeatureId, selected)
    }

    override suspend fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig) {
        preferencesDataSource.setDarkThemeConfig(darkThemeConfig)
    }

    override suspend fun setShouldHideOnboarding(shouldHideOnboarding: Boolean) {
        preferencesDataSource.setShouldHideOnboarding(shouldHideOnboarding)
    }
}

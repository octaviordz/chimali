package app.chimali.core.data.repository

import app.chimali.core.model.data.AppFeatureId
import app.chimali.core.model.data.DarkThemeConfig
import app.chimali.core.model.data.UserData
import kotlinx.coroutines.flow.Flow

interface UserDataRepository {
    /**
     * Stream of [UserData]
     */
    val userData: Flow<UserData>

    /**
     * Sets the user's newly selected/unselected app feature
     */
    suspend fun setSelectableAppFeature(
        appFeatureId: AppFeatureId,
        selected: Boolean,
    )

    /**
     * Sets the desired dark theme config.
     */
    suspend fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig)

    /**
     * Sets whether the user has completed the onboarding process.
     */
    suspend fun setShouldHideOnboarding(shouldHideOnboarding: Boolean)
}

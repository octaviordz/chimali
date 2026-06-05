package app.chimali.core.model.data

import app.chimali.core.model.data.DarkThemeConfig

/**
 * Class summarizing user interest data
 */
data class UserData(
    val lastVisitedMainScreen: String,
    val selectedAppFeatureIds: Set<AppFeatureId>,
    val darkThemeConfig: DarkThemeConfig,
    val useDynamicColor: Boolean,
    val shouldHideOnboarding: Boolean,
)

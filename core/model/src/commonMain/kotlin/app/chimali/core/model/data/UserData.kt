package app.chimali.core.model.data

import app.chimali.core.model.data.DarkThemeConfig

/**
 * Class summarizing user interest data
 */
data class UserData(
    val bookmarkedNewsResources: Set<String>,
    val viewedNewsResources: Set<String>,
    val followedTopics: Set<String>,
    val darkThemeConfig: DarkThemeConfig,
    val useDynamicColor: Boolean,
    val shouldHideOnboarding: Boolean,
)

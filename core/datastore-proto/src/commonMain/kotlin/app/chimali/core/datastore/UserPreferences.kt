package app.chimali.core.datastore

import app.chimali.core.datastore.DarkThemeConfigProto.DARK_THEME_CONFIG_FOLLOW_SYSTEM
import app.chimali.core.datastore.ThemeBrandProto.THEME_BRAND_UNSPECIFIED
import kotlinx.serialization.Serializable

// A lot of workaround brought by Proto
@Serializable
data class UserPreferences(
    val topicChangeListVersion: Int,
    val authorChangeListVersion: Int,
    val newsResourceChangeListVersion: Int,
    val hasDoneIntToStringIdMigration: Boolean,
    val hasDoneListToMapMigration: Boolean,
    val followedTopicIds: Set<String>,
    val followedAuthorIds: Set<String>,
    val bookmarkedNewsResourceIds: Set<String>,
    val viewedNewsResourceIds: Set<String>,
    val themeBrand: ThemeBrandProto,
    val darkThemeConfig: DarkThemeConfigProto,
    val shouldHideOnboarding: Boolean,
    val useDynamicColor: Boolean,
) {
    companion object {
        val DEFAULT =
            UserPreferences(
                topicChangeListVersion = 0,
                authorChangeListVersion = 0,
                newsResourceChangeListVersion = 0,
                hasDoneIntToStringIdMigration = false,
                hasDoneListToMapMigration = false,
                themeBrand = THEME_BRAND_UNSPECIFIED,
                darkThemeConfig = DARK_THEME_CONFIG_FOLLOW_SYSTEM,
                shouldHideOnboarding = false,
                useDynamicColor = false,
                followedTopicIds = emptySet(),
                followedAuthorIds = emptySet(),
                bookmarkedNewsResourceIds = emptySet(),
                viewedNewsResourceIds = emptySet(),
            )
    }
}

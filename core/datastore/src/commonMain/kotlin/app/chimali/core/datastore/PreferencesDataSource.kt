package app.chimali.core.datastore

import androidx.datastore.core.DataStore
import app.chimali.core.model.data.AppFeatureId
import app.chimali.core.model.data.DarkThemeConfig
import app.chimali.core.model.data.UserData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PreferencesDataSource(
    private val dataStore: DataStore<UserPreferences>,
    private val dispatcher: CoroutineDispatcher,
) {
    // Expose the DataStore flow mapped directly into your Domain-level 'UserData' object
    val userData: Flow<UserData> =
        dataStore.data.map { prefs ->
            UserData(
                // Wire maps your set to a Map<String, Boolean> (using key set to extract IDs)
                selectedAppFeatureIds = prefs.selected_app_feature_ids.toAppFeatureIdSet(),
                darkThemeConfig = prefs.dark_theme_config.toDarkThemeConfig(),
                useDynamicColor = prefs.use_dynamic_color,
                shouldHideOnboarding = prefs.should_hide_onboarding,
                lastVisitedMainScreen = prefs.last_visited_main_screen,
            )
        }

    suspend fun setShouldHideOnboarding(shouldHideOnboarding: Boolean) =
        withContext(dispatcher) {
            dataStore.updateData { currentPrefs ->
                currentPrefs.copy(should_hide_onboarding = shouldHideOnboarding)
            }
        }

    suspend fun setSelectedAppFeatureIds(featureIds: Set<String>) =
        withContext(dispatcher) {
            dataStore.updateData { currentPrefs ->
                // Convert Set back to a Proto3 Map representation (true flags)
                val newMap = featureIds.associateWith { true }
                currentPrefs.copy(selected_app_feature_ids = newMap)
            }
        }

    suspend fun setAppFeatureIdSelected(
        appFeatureId: AppFeatureId,
        isSelected: Boolean,
    ) = withContext(dispatcher) {
        dataStore.updateData { currentPrefs ->
            val currentMap = currentPrefs.selected_app_feature_ids.toMutableMap()
            if (isSelected) {
                currentMap[appFeatureId.value] = true
            } else {
                currentMap.remove(appFeatureId.value)
            }
            currentPrefs.copy(selected_app_feature_ids = currentMap)
        }
    }

    suspend fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig) =
        withContext(dispatcher) {
            dataStore.updateData { currentPrefs ->
                currentPrefs.copy(dark_theme_config = darkThemeConfig.toDarkThemeConfigProto())
            }
        }

    suspend fun setDynamicColorPreference(useDynamicColor: Boolean) =
        withContext(dispatcher) {
            dataStore.updateData { currentPrefs ->
                currentPrefs.copy(use_dynamic_color = useDynamicColor)
            }
        }
}

private fun Map<String, Boolean>.toAppFeatureIdSet(): Set<AppFeatureId> =
    this.keys.mapTo(HashSet(this.size)) { AppFeatureId(it) }

private fun DarkThemeConfig.toDarkThemeConfigProto(): DarkThemeConfigProto =
    when (this) {
        DarkThemeConfig.FOLLOW_SYSTEM -> DarkThemeConfigProto.DARK_THEME_CONFIG_FOLLOW_SYSTEM
        DarkThemeConfig.DARK -> DarkThemeConfigProto.DARK_THEME_CONFIG_DARK
        DarkThemeConfig.LIGHT -> DarkThemeConfigProto.DARK_THEME_CONFIG_LIGHT
    }

private fun DarkThemeConfigProto.toDarkThemeConfig(): DarkThemeConfig =
    when (this) {
        DarkThemeConfigProto.DARK_THEME_CONFIG_UNSPECIFIED,
        DarkThemeConfigProto.DARK_THEME_CONFIG_FOLLOW_SYSTEM,
        -> DarkThemeConfig.FOLLOW_SYSTEM

        DarkThemeConfigProto.DARK_THEME_CONFIG_DARK -> DarkThemeConfig.DARK

        DarkThemeConfigProto.DARK_THEME_CONFIG_LIGHT -> DarkThemeConfig.LIGHT
    }

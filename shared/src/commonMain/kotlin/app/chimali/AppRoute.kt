package app.chimali

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable data object Onboarding : AppRoute

    @Serializable data object Authenticator : AppRoute

    @Serializable data object Vault : AppRoute

    @Serializable data object DevTools : AppRoute

    @Serializable data object Settings : AppRoute
}

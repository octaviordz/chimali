package app.chimali

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable data object Transform : AppRoute
    @Serializable data object Reflow : AppRoute
    @Serializable data object Slideshow : AppRoute
    @Serializable data object Settings : AppRoute
}

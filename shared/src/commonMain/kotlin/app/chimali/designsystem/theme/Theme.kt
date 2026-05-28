package app.chimali.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

// Custom theme object for cleaner global access
object ChimaliTheme {
    val dimensions: AppDimensions
        @Composable
        @ReadOnlyComposable
        get() = LocalAppDimensions.current
}

private val DarkColorScheme =
    darkColorScheme(
        primary = Purple80,
        secondary = PurpleGrey80,
        tertiary = Pink80,
    )

// Explicitly define your cross-platform light mode color choices
private val LightColorScheme =
    lightColorScheme(
        primary = Purple40,
        secondary = PurpleGrey40,
        tertiary = Pink40,
    )

@Composable
fun ChimaliTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Select the scheme directly based on the theme boolean
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // 1. Wrap the calculation at the top level using constraints
    BoxWithConstraints {
        val dimensions =
            when {
                maxWidth >= 840.dp -> ExpandedDimensions
                maxWidth >= 600.dp -> MediumDimensions
                else -> CompactDimensions
            }

        // 2. Provide your custom dimensions first
        CompositionLocalProvider(LocalAppDimensions provides dimensions) {
            // 3. Nest the single MaterialTheme instance inside the provider
            MaterialTheme(
                colorScheme = colorScheme,
                typography = Typography,
                content = content,
            )
        }
    }
}

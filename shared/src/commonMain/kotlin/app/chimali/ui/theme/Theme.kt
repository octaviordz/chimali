package app.chimali.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

// Explicitly define your cross-platform dark mode color choices
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Ensure your 'Typography' definition lives in commonMain
        content = content,
    )

    BoxWithConstraints {
        // Evaluate the active Material 3 Window Size Class standard
        val dimensions =
            when {
                maxWidth >= 840.dp -> ExpandedDimensions // Tablet landscape
                maxWidth >= 600.dp -> MediumDimensions // Foldable / Tablet portrait
                else -> CompactDimensions // Default values / Phones
            }

        CompositionLocalProvider(LocalAppDimensions provides dimensions) {
            MaterialTheme(
                content = content,
            )
        }
    }
}

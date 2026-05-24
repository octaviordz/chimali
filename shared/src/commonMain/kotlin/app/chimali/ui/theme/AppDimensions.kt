package app.chimali.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Data structure representing size and layout classifications
data class AppDimensions(
    val transformImageSize: Dp,
    val isCompactLayout: Boolean // Tracks if we are on a standard phone scale
)

// Standard values for Phones (< 600dp)
val CompactDimensions = AppDimensions(
    transformImageSize = 64.dp,
    isCompactLayout = true
)

// Standard values for Foldables / Medium Screens (600dp - 839dp)
val MediumDimensions = AppDimensions(
    transformImageSize = 96.dp,
    isCompactLayout = false
)

// Standard values for Tablets / Large Screens (≥ 840dp)
val ExpandedDimensions = AppDimensions(
    transformImageSize = 120.dp,
    isCompactLayout = false
)

// CompositionLocal engine to provide configurations down the UI tree
val LocalAppDimensions = staticCompositionLocalOf { CompactDimensions }

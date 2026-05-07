package com.chimali.feature.vault.ui.model

import androidx.compose.ui.graphics.Color
import com.chimali.feature.vault.ui.theme.LegibilityColors

/**
 * Represents user preferences for viewing secrets with legibility enhancements.
 */
data class LegibilitySettings(
    val fontType: LegibilityFont = LegibilityFont.Atkinson,
    val useSemanticHighlighting: Boolean = true,
    val highlightNumbers: Boolean = true,
    val numberColor: Color = LegibilityColors.NumberOrange,
    val highlightSymbols: Boolean = false,
    val symbolColor: Color = LegibilityColors.SymbolBlue,
    val highlightUppercase: Boolean = false,
    val uppercaseColor: Color = LegibilityColors.UppercasePurple,
    val highlightLowercase: Boolean = false,
    val lowercaseColor: Color = LegibilityColors.LowercaseGreen,
    val colorblindMode: Boolean = false,
)

/**
 * Available legibility fonts for password display.
 * Ordered by priority as specified in FR-001.
 */
enum class LegibilityFont {
    Atkinson, // Primary: Atkinson Hyperlegible
    JetBrainsMono, // Fallback: JetBrains Mono
    SystemDefault, // Final fallback: System monospace
}

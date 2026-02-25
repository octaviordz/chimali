package com.chimali.feature.vault.ui.model

import androidx.compose.ui.graphics.Color

/**
 * Represents user preferences for viewing secrets with legibility enhancements.
 */
data class LegibilitySettings(
    val fontType: LegibilityFont = LegibilityFont.Atkinson,
    val useSemanticHighlighting: Boolean = true,
    val highlightNumbers: Boolean = true,
    val numberColor: Color = Color(0xFFE67E22), // Orange
    val highlightSymbols: Boolean = false,
    val symbolColor: Color = Color(0xFF3498DB), // Blue
    val highlightUppercase: Boolean = false,
    val uppercaseColor: Color = Color(0xFF9B59B6), // Purple
    val highlightLowercase: Boolean = false,
    val lowercaseColor: Color = Color(0xFF27AE60), // Green
    val colorblindMode: Boolean = false
)

/**
 * Available legibility fonts for password display.
 * Ordered by priority as specified in FR-001.
 */
enum class LegibilityFont {
    Atkinson,        // Primary: Atkinson Hyperlegible
    JetBrainsMono,   // Fallback: JetBrains Mono
    SystemDefault    // Final fallback: System monospace
}

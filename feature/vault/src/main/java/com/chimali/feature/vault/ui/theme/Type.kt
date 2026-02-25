package com.chimali.feature.vault.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.chimali.feature.vault.R

/**
 * Font family definitions for legibility fonts.
 * These are used in the LegibleSecretText component for password display.
 */
object LegibilityType {
    
    /**
     * Atkinson Hyperlegible font family - primary choice for legibility.
     * Designed specifically for low vision users with excellent character differentiation.
     */
    val AtkinsonFontFamily = FontFamily(
        Font(
            resId = R.font.atkinson_hyperlegible,
            weight = FontWeight.Normal,
            style = FontStyle.Normal
        )
    )
    
    /**
     * JetBrains Mono font family - fallback monospace option.
     * Modern monospace font with distinct character shapes.
     */
    val JetBrainsMonoFontFamily = FontFamily(
        Font(
            resId = R.font.jetbrains_mono,
            weight = FontWeight.Normal,
            style = FontStyle.Normal
        )
    )
    
    /**
     * System default monospace font - final fallback.
     * Uses the platform's default monospace font.
     */
    val SystemMonospaceFontFamily = FontFamily.Monospace
}

/**
 * Helper function to get the appropriate font family based on LegibilityFont enum.
 */
fun getFontFamily(fontType: com.chimali.feature.vault.ui.model.LegibilityFont): FontFamily {
    return when (fontType) {
        com.chimali.feature.vault.ui.model.LegibilityFont.Atkinson -> LegibilityType.AtkinsonFontFamily
        com.chimali.feature.vault.ui.model.LegibilityFont.JetBrainsMono -> LegibilityType.JetBrainsMonoFontFamily
        com.chimali.feature.vault.ui.model.LegibilityFont.SystemDefault -> LegibilityType.SystemMonospaceFontFamily
    }
}

package com.chimali.core.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.chimali.core.ui.R

/**
 * Font family definitions for high legibility, enforcing Constitution §VI.
 * These are used across modules for displaying security-critical text
 * (e.g., master seeds, public keys, credential IDs).
 */
object LegibilityType {

    /**
     * Atkinson Hyperlegible font family - primary choice for security text.
     * Designed specifically for low vision users with excellent character differentiation
     * (e.g., distinct 0/O, 1/I/l).
     */
    val AtkinsonFontFamily = FontFamily(
        Font(
            resId = R.font.atkinson_hyperlegible_regular,
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
            resId = R.font.jetbrains_mono_regular,
            weight = FontWeight.Normal,
            style = FontStyle.Normal
        )
    )

    /**
     * System default monospace font - final fallback.
     */
    val SystemMonospaceFontFamily = FontFamily.Monospace
}

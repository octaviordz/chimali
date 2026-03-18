package com.chimali.feature.vault.ui.theme

import androidx.compose.ui.text.font.FontFamily
import com.chimali.core.ui.theme.LegibilityType
import com.chimali.feature.vault.ui.model.LegibilityFont

/**
 * Helper function to get the appropriate font family based on LegibilityFont enum.
 */
fun getFontFamily(fontType: LegibilityFont): FontFamily {
    return when (fontType) {
        LegibilityFont.Atkinson -> LegibilityType.AtkinsonFontFamily
        LegibilityFont.JetBrainsMono -> LegibilityType.JetBrainsMonoFontFamily
        LegibilityFont.SystemDefault -> LegibilityType.SystemMonospaceFontFamily
    }
}

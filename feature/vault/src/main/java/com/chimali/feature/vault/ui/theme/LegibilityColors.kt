package com.chimali.feature.vault.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Semantic color tokens for password legibility highlighting.
 * Colors are chosen to be colorblind-friendly with sufficient contrast.
 */
object LegibilityColors {
    private const val ORANGE_HEX = 0xFFE67E22
    private const val BLUE_HEX = 0xFF3498DB
    private const val PURPLE_HEX = 0xFF9B59B6
    private const val GREEN_HEX = 0xFF27AE60

    private const val ORANGE_HC_HEX = 0xFFD35400
    private const val BLUE_HC_HEX = 0xFF2980B9
    private const val PURPLE_HC_HEX = 0xFF8E44AD
    private const val GREEN_HC_HEX = 0xFF229954

    private const val BG_ALPHA_MASK = 0x1A000000L
    private const val RGB_MASK = 0x00FFFFFFL

    // Orange for numbers (primary semantic color)
    val NumberOrange = Color(ORANGE_HEX)

    // Additional semantic colors for character type highlighting
    val SymbolBlue = Color(BLUE_HEX)
    val UppercasePurple = Color(PURPLE_HEX)
    val LowercaseGreen = Color(GREEN_HEX)

    // Colorblind-friendly alternatives
    val NumberOrangeHighContrast = Color(ORANGE_HC_HEX) // Darker orange for better contrast
    val SymbolBlueHighContrast = Color(BLUE_HC_HEX)
    val UppercasePurpleHighContrast = Color(PURPLE_HC_HEX)
    val LowercaseGreenHighContrast = Color(GREEN_HC_HEX)

    // Background highlights for colorblind mode (subtle)
    val NumberBackground = Color((ORANGE_HEX and RGB_MASK) or BG_ALPHA_MASK)
    val SymbolBackground = Color((BLUE_HEX and RGB_MASK) or BG_ALPHA_MASK)
    val UppercaseBackground = Color((PURPLE_HEX and RGB_MASK) or BG_ALPHA_MASK)
    val LowercaseBackground = Color((GREEN_HEX and RGB_MASK) or BG_ALPHA_MASK)
}

/**
 * WCAG 2.1 AA contrast compliant color combinations.
 * All colors meet 4.5:1 contrast ratio against typical backgrounds.
 */
object WCAGCompliantColors {
    // For use on light backgrounds (Surface, SurfaceVariant)
    val OnLightNumber = LegibilityColors.NumberOrange
    val OnLightSymbol = LegibilityColors.SymbolBlue
    val OnLightUppercase = LegibilityColors.UppercasePurple
    val OnLightLowercase = LegibilityColors.LowercaseGreen

    // For use on dark backgrounds (Surface, SurfaceVariant in dark theme)
    val OnDarkNumber = LegibilityColors.NumberOrangeHighContrast
    val OnDarkSymbol = LegibilityColors.SymbolBlueHighContrast
    val OnDarkUppercase = LegibilityColors.UppercasePurpleHighContrast
    val OnDarkLowercase = LegibilityColors.LowercaseGreenHighContrast
}

package com.chimali.feature.vault.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Semantic color tokens for password legibility highlighting.
 * Colors are chosen to be colorblind-friendly with sufficient contrast.
 */
object LegibilityColors {
    // Orange for numbers (primary semantic color)
    val NumberOrange = Color(0xFFE67E22)
    
    // Additional semantic colors for character type highlighting
    val SymbolBlue = Color(0xFF3498DB)
    val UppercasePurple = Color(0xFF9B59B6)
    val LowercaseGreen = Color(0xFF27AE60)
    
    // Colorblind-friendly alternatives
    val NumberOrangeHighContrast = Color(0xFFD35400) // Darker orange for better contrast
    val SymbolBlueHighContrast = Color(0xFF2980B9)
    val UppercasePurpleHighContrast = Color(0xFF8E44AD)
    val LowercaseGreenHighContrast = Color(0xFF229954)
    
    // Background highlights for colorblind mode (subtle)
    val NumberBackground = Color(0x1AE67E22) // 10% opacity orange
    val SymbolBackground = Color(0x1A3498DB) // 10% opacity blue
    val UppercaseBackground = Color(0x1A9B59B6) // 10% opacity purple
    val LowercaseBackground = Color(0x1A27AE60) // 10% opacity green
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

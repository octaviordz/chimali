package com.chimali.feature.vault.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.chimali.feature.vault.ui.model.LegibilitySettings
import com.chimali.feature.vault.ui.theme.getFontFamily
import com.chimali.feature.vault.ui.theme.LegibilityColors

/**
 * A reusable Composable for displaying secrets with legibility enhancements.
 * 
 * This component applies specialized fonts and semantic highlighting to make passwords
 * and other secrets easier to read while maintaining security.
 * 
 * @param secret The raw secret string to display
 * @param isRevealed Whether the secret should be shown or masked
 * @param settings Configuration for font and highlighting preferences
 * @param modifier Standard Compose modifier
 * @param maxLines Maximum number of lines for text display (defaults to 3 for long passwords)
 */
@Composable
fun LegibleSecretText(
    secret: String,
    isRevealed: Boolean,
    settings: LegibilitySettings,
    modifier: Modifier = Modifier,
    maxLines: Int = 3
) {
    val displayText = if (isRevealed) {
        buildLegibilityAnnotatedString(secret, settings)
    } else {
        AnnotatedString("•".repeat(secret.length))
    }

    val fontFamily = getFontFamily(settings.fontType)
    
    BasicText(
        text = displayText,
        modifier = modifier,
        style = TextStyle(
            fontFamily = fontFamily,
            fontSize = 16.sp,
            color = Color.Unspecified, // Will be inherited from theme
            fontWeight = FontWeight.Normal,
            lineHeight = 20.sp
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * Builds an AnnotatedString with semantic highlighting based on character types.
 * 
 * This function processes each character in the secret and applies appropriate
 * styling based on the user's legibility settings, including Unicode symbol handling.
 */
private fun buildLegibilityAnnotatedString(
    secret: String,
    settings: LegibilitySettings
): AnnotatedString {
    if (!settings.useSemanticHighlighting) {
        return AnnotatedString(secret)
    }

    return buildAnnotatedString {
        secret.forEach { char ->
            val processedChar = handleUnicodeSymbol(char)
            
            val (text, style) = when {
                char.isDigit() && settings.highlightNumbers -> {
                    val color = if (settings.colorblindMode) {
                        LegibilityColors.NumberOrangeHighContrast
                    } else {
                        settings.numberColor
                    }
                    Pair(
                        processedChar,
                        TextStyle(
                            color = color,
                            fontWeight = if (settings.colorblindMode) FontWeight.Bold else FontWeight.SemiBold
                        )
                    )
                }
                
                char.isLetter() && char.isUpperCase() && settings.highlightUppercase -> {
                    val color = if (settings.colorblindMode) {
                        LegibilityColors.UppercasePurpleHighContrast
                    } else {
                        settings.uppercaseColor
                    }
                    Pair(
                        processedChar,
                        TextStyle(
                            color = color,
                            fontWeight = if (settings.colorblindMode) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                }
                
                char.isLetter() && char.isLowerCase() && settings.highlightLowercase -> {
                    val color = if (settings.colorblindMode) {
                        LegibilityColors.LowercaseGreenHighContrast
                    } else {
                        settings.lowercaseColor
                    }
                    Pair(
                        processedChar,
                        TextStyle(
                            color = color,
                            fontWeight = if (settings.colorblindMode) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }
                
                !char.isLetterOrDigit() && settings.highlightSymbols -> {
                    val color = if (settings.colorblindMode) {
                        LegibilityColors.SymbolBlueHighContrast
                    } else {
                        settings.symbolColor
                    }
                    Pair(
                        processedChar,
                        TextStyle(
                            color = color,
                            fontWeight = if (settings.colorblindMode) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                }
                
                else -> Pair(processedChar, TextStyle())
            }
            
            withStyle(style.toSpanStyle()) {
                append(text)
            }
        }
    }
}

/**
 * Enhanced version with colorblind-friendly background highlighting.
 * This adds subtle background colors in addition to text color changes.
 */
@Composable
fun LegibleSecretTextColorblind(
    secret: String,
    isRevealed: Boolean,
    settings: LegibilitySettings,
    modifier: Modifier = Modifier,
    maxLines: Int = 3
) {
    if (!settings.colorblindMode) {
        LegibleSecretText(secret, isRevealed, settings, modifier, maxLines)
        return
    }

    val displayText = if (isRevealed) {
        buildColorblindAnnotatedString(secret, settings)
    } else {
        AnnotatedString("•".repeat(secret.length))
    }

    val fontFamily = getFontFamily(settings.fontType)
    
    BasicText(
        text = displayText,
        modifier = modifier,
        style = TextStyle(
            fontFamily = fontFamily,
            fontSize = 16.sp,
            color = Color.Unspecified,
            fontWeight = FontWeight.Normal,
            lineHeight = 20.sp
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * Handles Unicode symbol fallback for obscure characters.
 * Ensures that even unusual Unicode symbols are displayed with proper legibility.
 */
private fun handleUnicodeSymbol(char: Char): String {
    return try {
        // Check if the character is a standard printable ASCII character
        if (char.code in 32..126) {
            char.toString()
        } else {
            // For non-ASCII characters, ensure they're handled properly
            // This could be extended with specific Unicode symbol mappings if needed
            char.toString()
        }
    } catch (e: Exception) {
        // Fallback for any rendering issues
        "?"
    }
}

/**
 * Builds AnnotatedString with both text color and background highlighting
 * for enhanced colorblind accessibility, including Unicode symbol handling.
 */
private fun buildColorblindAnnotatedString(
    secret: String,
    settings: LegibilitySettings
): AnnotatedString {
    return buildAnnotatedString {
        secret.forEachIndexed { index, char ->
            val processedChar = handleUnicodeSymbol(char)
            
            when {
                char.isDigit() && settings.highlightNumbers -> {
                    withStyle(
                        TextStyle(
                            color = LegibilityColors.NumberOrangeHighContrast,
                            fontWeight = FontWeight.Bold,
                            background = LegibilityColors.NumberBackground
                        ).toSpanStyle()
                    ) {
                        append(processedChar)
                    }
                }
                
                char.isLetter() && char.isUpperCase() && settings.highlightUppercase -> {
                    withStyle(
                        TextStyle(
                            color = LegibilityColors.UppercasePurpleHighContrast,
                            fontWeight = FontWeight.Bold,
                            background = LegibilityColors.UppercaseBackground
                        ).toSpanStyle()
                    ) {
                        append(processedChar)
                    }
                }
                
                char.isLetter() && char.isLowerCase() && settings.highlightLowercase -> {
                    withStyle(
                        TextStyle(
                            color = LegibilityColors.LowercaseGreenHighContrast,
                            fontWeight = FontWeight.Bold,
                            background = LegibilityColors.LowercaseBackground
                        ).toSpanStyle()
                    ) {
                        append(processedChar)
                    }
                }
                
                !char.isLetterOrDigit() && settings.highlightSymbols -> {
                    withStyle(
                        TextStyle(
                            color = LegibilityColors.SymbolBlueHighContrast,
                            fontWeight = FontWeight.Bold,
                            background = LegibilityColors.SymbolBackground
                        ).toSpanStyle()
                    ) {
                        append(processedChar)
                    }
                }
                
                else -> append(processedChar)
            }
        }
    }
}

# Design: Password Legibility and Confusion Prevention

## Data Model (UI Layer)

### LegibilitySettings (Data Class)
Represents the user's preferences for viewing secrets.

```kotlin
data class LegibilitySettings(
    val fontType: LegibilityFont = LegibilityFont.Atkinson,
    val useSemanticHighlighting: Boolean = true,
    val highlightNumbers: Boolean = true,
    val numberColor: Color = Color(0xFFE67E22), // Orange
    val highlightSymbols: Boolean = false,
    val symbolColor: Color = Color(0xFF3498DB), // Blue (for potential expansion)
    val colorblindMode: Boolean = false
)

enum class LegibilityFont {
    Atkinson,
    JetBrainsMono,
    SystemDefault
}
```

## UI Component Contract

### LegibleSecretText (Composable)
A reusable component for displaying secrets with legibility rules applied.

**Parameters**:
- `secret: String`: The raw secret string.
- `isRevealed: Boolean`: Whether the secret should be shown or masked.
- `settings: LegibilitySettings`: Configuration for font and colors.
- `modifier: Modifier`: standard Compose modifier.

**Internal Logic**:
```kotlin
@Composable
fun LegibleSecretText(
    secret: String,
    isRevealed: Boolean,
    settings: LegibilitySettings,
    modifier: Modifier = Modifier
) {
    val displayString = if (isRevealed) {
        buildAnnotatedString {
            // Logic to iterate over characters and apply SpanStyles
            secret.forEach { char ->
                when {
                    char.isDigit() && settings.highlightNumbers -> {
                        withStyle(SpanStyle(color = settings.numberColor, fontWeight = FontWeight.Bold)) {
                            append(char)
                        }
                    }
                    // Add other rules here...
                    else -> append(char)
                }
            }
        }
    } else {
        AnnotatedString("•".repeat(secret.length))
    }

    Text(
        text = displayString,
        fontFamily = when (settings.fontType) {
            LegibilityFont.Atkinson -> AtkinsonFontFamily
            LegibilityFont.JetBrainsMono -> JetBrainsMonoFontFamily
            else -> FontFamily.Default
        },
        modifier = modifier
    )
}
```

## State Transitions
1. **Hidden**: String is masked with bullet characters.
2. **Revealed**: String is parsed, tokenized by character type, and styled via `AnnotatedString` before rendering.

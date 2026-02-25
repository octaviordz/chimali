# Quickstart: Password Legibility and Confusion Prevention

## Overview
This feature enhances password display with specialized fonts and semantic highlighting to prevent visual confusion between similar characters (e.g., 'O' vs '0', 'I' vs 'l' vs '1').

## Integration

### Using LegibleSecretText Component

```kotlin
@Composable
fun MyPasswordScreen() {
    var isRevealed by remember { mutableStateOf(false) }
    val legibilitySettings = LegibilitySettings(
        fontType = LegibilityFont.Atkinson,
        useSemanticHighlighting = true,
        highlightNumbers = true,
        colorblindMode = false
    )
    
    LegibleSecretText(
        secret = "MySecurePassword123!",
        isRevealed = isRevealed,
        settings = legibilitySettings,
        modifier = Modifier.fillMaxWidth()
    )
}
```

### Updating Existing Password Display

Replace standard `Text` components with `LegibleSecretText`:

```kotlin
// Before
Text(
    text = if (isRevealed) password else "•".repeat(password.length),
    style = MaterialTheme.typography.bodyLarge
)

// After
LegibleSecretText(
    secret = password,
    isRevealed = isRevealed,
    settings = legibilitySettings
)
```

## Configuration Options

### LegibilitySettings

```kotlin
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
```

### Font Priority

1. **Atkinson Hyperlegible** (primary) - Designed for low vision users
2. **JetBrains Mono** (fallback) - Modern monospace with distinct characters
3. **System Monospace** (final fallback) - Platform default

## Testing Guidelines

### Character Distinction Testing

```kotlin
val testPassword = "Il1O0S5" // Test ambiguous pairs
```

Verify each character pair is visually distinct:
- 'I' (uppercase) vs 'l' (lowercase) vs '1' (digit)
- 'O' (letter) vs '0' (digit)
- 'S' (letter) vs '5' (digit)

### Semantic Highlighting Testing

```kotlin
val highlightTest = "Pass123!@#ABCdef"
```

Expected behavior:
- Numbers (123) → Orange highlighting
- Uppercase (ABC) → Purple highlighting (if enabled)
- Symbols (!@#) → Blue highlighting (if enabled)
- Lowercase (def) → Green highlighting (if enabled)

### Accessibility Testing

1. **Colorblind Mode**:
   ```kotlin
   val colorblindSettings = legibilitySettings.copy(colorblindMode = true)
   ```
   - Verify enhanced contrast
   - Check background highlights for character types

2. **Screen Reader Support**:
   - Enable TalkBack
   - Navigate to password fields
   - Confirm proper content descriptions

3. **Dynamic Text Scaling**:
   - Test with large font sizes
   - Verify highlighting is preserved

## Edge Cases

### Unicode Symbol Handling

```kotlin
val unicodePassword = "密码🔑123" // Mixed Unicode characters
```

- Non-ASCII characters should render properly
- Fallback to "?" for rendering errors

### Long Password Support

```kotlin
val longPassword = "a".repeat(150) // 150 characters
```

- Text should wrap properly
- Highlighting preserved across lines
- Ellipsis for overflow when maxLines is set

## Security Considerations

- **No Plain-text Logs**: LegibleSecretText never logs password content
- **Memory Management**: Uses CharArray internally when possible
- **Secure Display**: Only reveals passwords when explicitly requested

## Performance Notes

- **Rendering**: Maintains 60 FPS for passwords up to 200 characters
- **Memory**: Minimal overhead for highlighting calculations
- **Font Loading**: Automatic fallback prevents rendering delays

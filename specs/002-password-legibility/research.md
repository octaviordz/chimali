# Research: Password Legibility and Confusion Prevention

## Font Selection

| Font | Type | Legibility (O/0, I/l/1) | Pros | Cons |
| :--- | :--- | :--- | :--- | :--- |
| **Atkinson Hyperlegible** | Sans-Serif | Excellent (High Differentiation) | Designed for low vision; core project recommendation. | Not monospaced by default (can be an issue for alignment). |
| **JetBrains Mono** | Monospaced | Excellent (Dot in 0, Crossbars on I) | Modern, clean, distinct characters. | Coding focused, maybe too technical for some. |
| **Lexend** | Sans-Serif | Very Good | Designed specifically to reduce visual load. | Less distinct '1' vs 'l' than Atkinson. |
| **Andale Mono** | Monospaced | Good | Traditional legibility font. | Dated look; less "premium". |
| **Lucida Console** | Monospaced | Good | Broadly available. | Very dated aesthetic. |

**Decision**: 
- Primary: **Atkinson Hyperlegible** for main credential viewing.
- Alternative (Toggle): **JetBrains Mono** for users who prefer monospaced alignment for complex secrets.

## Semantic Highlighting (Orange for Numbers)

**Requirement**: Display numbers in orange within secrets.

**Implementation Plan**:
- Use `AnnotatedString` in Compose.
- Apply a `SpanStyle` with `Color(0xFFE67E22)` (or the Material 3 `Tertiary` color if it fits the orange/amber range) to all matches of `\d`.
- **Contrast Check**: Ensure the chosen orange meets 4.5:1 contrast ratio against the background (Surface/SurfaceVariant).

## Colorblind Accessibility

**Requirement**: Avoid reliance on color alone.

**Implementation Plan**:
- For digits (orange), add a subtle "weight" increase or a very light background hint (e.g., 5% opacity box) to ensure users with Achromatopsia can still identify segments.
- Highlighting intensity should be user-configurable.

## Implementation Pattern in Compose

- Create a `SecretText` Composable.
- Takes `text: String` and `isRevealed: Boolean`.
- Uses `visualTransformation` for masked state.
- For revealed state, uses a `buildAnnotatedString` loop to identify digits and symbols.

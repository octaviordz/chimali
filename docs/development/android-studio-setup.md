# Android Studio Detekt Plugin Setup

## Purpose
Configure Android Studio to provide real-time MultipleEmitters rule feedback during development.

## Setup Instructions

### 1. Install Detekt Plugin
1. Open Android Studio
2. Go to `File` → `Settings` → `Plugins`
3. Search for "Detekt" 
4. Install the Detekt plugin by GitLab Arturbosch
5. Restart Android Studio

### 2. Configure Detekt Plugin
1. Go to `File` → `Settings` → `Other Settings` → `Detekt`
2. Set the configuration path to: `config/detekt/detekt.yml`
3. Enable "Run Detekt on the fly"
4. Configure severity levels:
   - MultipleEmitters: Error (red highlight)
   - Other Compose rules: Warning (yellow highlight)

### 3. Enable Real-time Analysis
1. Go to `File` → `Settings` → `Editor` → `Inspections`
2. Expand "Kotlin" → "Detekt"
3. Enable "MultipleEmitters" inspection
4. Set severity to "Error"
5. Enable "Highlight on the fly"

### 4. Quick Fix Configuration
The Detekt plugin provides automatic quick fixes for common MultipleEmitters patterns:
- Extract to separate functions
- Wrap in single container (Column/Row/Box)
- Conditional composition patterns

### 5. Live Templates (Optional)
Create live templates in `File` → `Settings` → `Editor` → `Live Templates` → `Kotlin`:

**`comp` (Compliant Composable)**
```kotlin
@Composable
fun $NAME$(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        $END$
    }
}
```

**`compbox` (Box-wrapped Composable)**
```kotlin
@Composable
fun $NAME$(
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        $END$
    }
}
```

## Verification
To verify the setup:
1. Open a Compose function with MultipleEmitters violation
2. Red squiggly lines should appear under violations
3. Hover to see error message
4. Use Alt+Enter to see quick fix options

## Troubleshooting
- If plugin doesn't detect violations, verify the config path
- If performance is slow, disable "Run on the fly" for large projects
- Check that the Detekt rule is active in detekt.yml

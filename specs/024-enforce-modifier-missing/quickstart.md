# Quickstart: Enforce Modifier Missing

**Phase 1 Output**

## Developer Guide

If you are contributing to UI components in the Chimali project, you must adhere to the `ModifierMissing` Detekt rule.

### How to Write Compliant Composables

**Don't:**
```kotlin
@Composable
fun MyButton(text: String) { // Fails Detekt ModifierMissing
    Button(modifier = Modifier.fillMaxWidth(), onClick = {}) {
        Text(text)
    }
}
```

**Do:**
```kotlin
@Composable
fun MyButton(
    text: String,
    modifier: Modifier = Modifier // Passed as first optional parameter
) {
    Button(
        modifier = modifier.fillMaxWidth(), // Applied to the root element
        onClick = {}
    ) {
        Text(text)
    }
}
```

### Validating Changes

Run the local CI pipeline to ensure your components pass static analysis:

```powershell
.\tools\local-ci.ps1
```

Or, run Detekt specifically:

```powershell
.\gradlew detektAll
```

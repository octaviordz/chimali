# Data Model & Configuration: Detekt Quality Enforcement

This document describes the configuration changes and the "No-Logic Refactoring" model for enforcing MaxLineLength.

## Configuration Model

### Detekt Rule: MaxLineLength
| Property | Value | Rationale |
|----------|-------|-----------|
| `active` | `true` | Enable enforcement |
| `maxLineLength` | `120` | Project standard |
| `excludeRawStrings` | `true` | Avoid breaking constants/JSON |
| `excludes` | `[]` | **NEW**: Enforce on tests |

### Gradle Plugin Application
We will move from manual module-by-module application to a root-level `subprojects` configuration to ensure zero-leakage of quality standards.

## Refactoring Logic Model (No-Logic Constraint)

All refactorings must follow the **Identity Transformation** principle: the compiled bytecode must be effectively identical in logic, and runtime behavior must remain unchanged.

### Pattern 1: Parameter Wrapping
**Before**:
```kotlin
Text(text = item.type.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
```
**After**:
```kotlin
Text(
    text = item.type.name,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)
```

### Pattern 2: Expression Intermediate Variables
**Before**:
```kotlin
selectedTabIndex = if (selectedLabelId == null) 0 else labels.indexOfFirst { it.id == selectedLabelId } + 1
```
**After**:
```kotlin
val index = if (selectedLabelId == null) 0 else {
    labels.indexOfFirst { it.id == selectedLabelId } + 1
}
selectedTabIndex = index
```

### Pattern 3: Test Assertions
**Before**:
```kotlin
assertEquals("Expected value that is quite long and makes the line exceed 120 characters", actualValueFromSystem)
```
**After**:
```kotlin
val expected = "Expected value that is quite long and makes the line exceed 120 characters"
assertEquals(expected, actualValueFromSystem)
```

## Validation Strategy
1. **Compilation Check**: `local-ci.ps1`'s "Compile All" task must pass.
2. **Logic Check**: All unit tests must pass.
3. **Quality Check**: `detekt` must pass with zero `MaxLineLength` findings.

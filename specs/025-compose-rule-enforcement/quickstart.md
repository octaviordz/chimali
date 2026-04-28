# Quickstart Guide: Compose Rule Enforcement

**Feature**: Compose Rule Enforcement  
**Date**: 2026-04-27  
**Purpose**: Quick guide for implementing Compose rule cleanup

## Overview

This guide provides step-by-step instructions for removing `@Suppress(LambdaParameterInRestartableEffect)` and `@Suppress(ComposableParamOrder)` annotations from the Chimali codebase while maintaining code quality and functionality.

## Prerequisites

### Environment Setup
- **Kotlin**: 1.9.20+
- **Jetpack Compose**: BOM 2024.02.00+
- **Detekt**: 1.23.0+ with Compose rules enabled
- **Build Tools**: Gradle 8.0+
- **Testing**: JUnit 5.10.0+, Compose UI Testing 1.5.0+

### Required Tools
```bash
# Install required tools
./gradlew detekt                    # Run static analysis
rg "@Suppress" --type kotlin         # Find suppressions
./gradlew test                      # Run tests
./gradlew assembleDebug              # Build project
```

## Quick Start Process

### Step 1: Discover Suppressions
```bash
# Find all LambdaParameterInRestartableEffect suppressions
rg "@Suppress.*LambdaParameterInRestartableEffect" --type kotlin -A 2 -B 2

# Find all ComposableParamOrder suppressions  
rg "@Suppress.*ComposableParamOrder" --type kotlin -A 2 -B 2

# Generate suppression inventory
./gradlew detekt --config-file config/detekt/detekt.yml
```

### Step 2: Analyze Each Suppression

#### For LambdaParameterInRestartableEffect:
1. **Locate the restartable effect** (e.g., `LaunchedEffect`, `remember`, `derivedStateOf`)
2. **Check lambda parameter names** - they should be descriptive, not just `it`
3. **Identify underlying issues** that the suppression was hiding
4. **Fix parameter naming** and any underlying problems

**Example Fix**:
```kotlin
// Before (with suppression)
@Suppress("LambdaParameterInRestartableEffect")
LaunchedEffect(key1, key2) { 
    data?.let { 
        processData(it)  // 'it' is not descriptive
    }
}

// After (suppression removed)
LaunchedEffect(key1, key2) { 
    data?.let { processedData -> 
        processData(processedData)  // descriptive parameter name
    }
}
```

#### For ComposableParamOrder:
1. **Identify the Composable function** with suppression
2. **Check current parameter order**
3. **Reorder to follow Compose conventions**: content, modifier, other parameters
4. **Handle binary compatibility** for public functions

**Example Fix**:
```kotlin
// Before (with suppression)
@Suppress("ComposableParamOrder")
@Composable
fun MyComponent(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String = ""
) {
    // Implementation
}

// After (suppression removed)
@Composable
fun MyComponent(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = ""
) {
    // Implementation
}
```

### Step 3: Handle Complex Cases

#### Binary Compatibility Issues
For public functions that cannot be safely reordered:
```kotlin
// Create overload for backward compatibility
@Composable
@Deprecated("Use new parameter order", ReplaceWith("MyComponent(title, modifier, subtitle)"))
fun MyComponent(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String = ""
) = MyComponent(title, modifier, subtitle)

// New preferred signature
@Composable
fun MyComponent(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = ""
) {
    // Implementation
}
```

#### Complex Restartable Effects
For effects that require careful refactoring:
```kotlin
// Before
@Suppress("LambdaParameterInRestartableEffect")
LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
        when (event) {
            is Event.Success -> handleSuccess(event.data)
            is Event.Error -> handleError(event.error)
        }
    }
}

// After - extract to separate function
@Composable
fun MyComponent(viewModel: MyViewModel) {
    LaunchedEffect(viewModel) {
        viewModel.events.collect { uiEvent ->
            handleUiEvent(uiEvent)
        }
    }
}

// Separate function for better readability
private fun handleUiEvent(event: UiEvent) {
    when (event) {
        is UiEvent.Success -> handleSuccess(event.data)
        is UiEvent.Error -> handleError(event.error)
    }
}
```

### Step 4: Validation

#### After Each Change:
```bash
# Verify compilation
./gradlew compileDebugKotlin

# Run static analysis
./gradlew detekt

# Run tests
./gradlew test

# Check specific module
./gradlew :feature:fido2:compileDebugKotlin
```

#### Full Validation:
```bash
# Clean build
./gradlew clean

# Full test suite
./gradlew testDebugUnitTest

# UI tests
./gradlew connectedDebugAndroidTest

# Detekt analysis
./gradlew detekt
```

## Module-by-Module Approach

### Recommended Order:
1. **Core modules** (`core:common`, `core:data`) - Foundation code
2. **Feature modules** (`feature:authenticator`, `feature:fido2`, `feature:vault`) - UI code
3. **App module** (`app`) - Main application

### Module Checklist:
- [ ] All suppressions identified
- [ ] LambdaParameterInRestartableEffect suppressions removed
- [ ] ComposableParamOrder suppressions removed
- [ ] Code compiles without warnings
- [ ] Tests pass
- [ ] Detekt analysis clean

## Common Patterns and Solutions

### Pattern 1: Simple Lambda Parameter Rename
```kotlin
// Common issue: using 'it' in complex lambdas
@Suppress("LambdaParameterInRestartableEffect")
remember { data.map { process(it) } }

// Solution: descriptive parameter name
remember { data.map { item -> process(item) } }
```

### Pattern 2: Complex Parameter Reordering
```kotlin
// Complex case with multiple parameters
@Suppress("ComposableParamOrder")
@Composable
fun ComplexComponent(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String,
    enabled: Boolean = true,
    subtitle: String = ""
)

// Solution: reorder by priority
@Composable
fun ComplexComponent(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    subtitle: String = ""
)
```

### Pattern 3: Nested Effects
```kotlin
// Nested restartable effects
@Suppress("LambdaParameterInRestartableEffect")
LaunchedEffect(key) {
    data?.let { 
        LaunchedEffect(it.id) {
            processItem(it)
        }
    }
}

// Solution: flatten and extract
@Composable
fun MyComponent(key: String, data: Data?) {
    LaunchedEffect(key) {
        data?.let { item ->
            processItem(item)
        }
    }
}
```

## Troubleshooting

### Common Issues:
1. **Compilation fails after removing suppression**
   - Check for underlying issues the suppression was hiding
   - Look for improper parameter names or effect usage

2. **Binary compatibility warnings**
   - Use overloads for public functions
   - Add @Deprecated annotations for migration

3. **Tests fail after changes**
   - Verify functionality is preserved
   - Update tests if parameter order changed

4. **Detekt still shows warnings**
   - Check for remaining suppressions
   - Verify Detekt configuration includes Compose rules

### Getting Help:
- Check `research.md` for detailed technical guidance
- Review `data-model.md` for entity relationships
- Consult `contracts/cleanup-interface.md` for interface specifications

## Success Metrics

### Completion Criteria:
- [ ] Zero `@Suppress(LambdaParameterInRestartableEffect)` annotations
- [ ] Zero `@Suppress(ComposableParamOrder)` annotations (except documented exceptions)
- [ ] All code compiles without warnings
- [ ] All tests pass
- [ ] Detekt analysis clean

### Quality Metrics:
- [ ] Improved code readability
- [ ] Better parameter naming conventions
- [ ] Consistent Compose parameter ordering
- [ ] No functionality regressions

## Next Steps

After completing the cleanup:
1. Review the generated reports in `contracts/`
2. Update documentation if needed
3. Commit changes with descriptive messages
4. Create pull request for review
5. Monitor for any issues in production

## Resources

- [Compose Parameter Ordering Guidelines](https://developer.android.com/jetpack/compose/phases#ordering-composables)
- [Detekt Compose Rules](https://detekt.dev/docs/rules/compose)
- [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)

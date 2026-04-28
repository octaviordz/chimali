# Quick Start: Compose MultipleEmitters Rule Enforcement

**Purpose**: Quick guide for developers to comply with MultipleEmitters rule  
**Target Audience**: Android developers working with Jetpack Compose  
**Last Updated**: 2026-04-27

## What is the MultipleEmitters Rule?

The MultipleEmitters rule enforces that Compose functions emit content through a single path. This prevents performance issues and makes code more predictable.

## Quick Reference

### ✅ Correct Patterns

#### Single Content Emission
```kotlin
@Composable
fun UserProfile() {
    Column {  // Single emission point
        Text("User Name")
        Button("Edit") { }
    }
}
```

#### Conditional Content
```kotlin
@Composable
fun ConditionalContent(showDetails: Boolean) {
    Column {  // Single emission point
        if (showDetails) {
            Text("Detailed information")
        } else {
            Text("Summary")
        }
    }
}
```

#### Content with State
```kotlin
@Composable
fun Counter() {
    var count by remember { mutableStateOf(0) }
    
    Column {  // Single emission point
        Text("Count: $count")
        Button("Increment") { count++ }
    }
}
```

### ❌ Incorrect Patterns

#### Multiple Emissions
```kotlin
@Composable
fun BadExample() {
    Text("First emission")  // ❌ First emission
    Button("Second") { }   // ❌ Second emission
}
```

#### Mixed Content Types
```kotlin
@Composable
fun MixedContent() {
    Text("Text content")   // ❌ Text emission
    Dialog(onDismiss = { }) {  // ❌ Dialog emission
        Text("Dialog content")
    }
}
```

## Common Refactoring Patterns

### Extract to Separate Functions
```kotlin
// Before (MultipleEmitters)
@Composable
fun BadScreen() {
    Text("Header")
    LazyColumn {
        items(items) { item ->
            Text(item.name)
        }
    }
}

// After (Single emission)
@Composable
fun GoodScreen() {
    Column {
        HeaderContent()
        ItemList(items)
    }
}

@Composable
private fun HeaderContent() {
    Text("Header")
}

@Composable
private fun ItemList(items: List<Item>) {
    LazyColumn {
        items(items) { item ->
            Text(item.name)
        }
    }
}
```

### Use Composition for Conditional Content
```kotlin
// Before (MultipleEmitters)
@Composable
fun ConditionalDialog(showDialog: Boolean) {
    Text("Main content")
    if (showDialog) {
        Dialog(onDismiss = { }) {
            Text("Dialog content")
        }
    }
}

// After (Single emission)
@Composable
fun ConditionalDialog(showDialog: Boolean) {
    Box {
        Text("Main content")
        if (showDialog) {
            DialogContent()
        }
    }
}

@Composable
private fun DialogContent() {
    Dialog(onDismiss = { }) {
        Text("Dialog content")
    }
}
```

## Testing Your Changes

### 1. Run Static Analysis
```bash
./gradlew detekt
```

### 2. Run Local CI
```powershell
./tools/local-ci.ps1
```

### 3. Check for Suppressions
Search for any remaining suppressions:
```bash
grep -r "MultipleEmitters" --include="*.kt" .
```

## Getting Help

### Documentation
- [Compose Guidelines](docs/development/compose-patterns.md)
- [Detekt Configuration](config/detekt/detekt.yml)

### Code Review Tips
- Look for functions with multiple UI elements at the top level
- Check for proper composition patterns
- Verify no suppressions are added

### Common Questions

**Q: Can I use multiple `if` statements?**
A: Yes, as long as they're within a single container like `Column`, `Row`, or `Box`.

**Q: What about lazy containers?**
A: `LazyColumn`, `LazyRow`, etc. are single emission points even though they contain multiple items.

**Q: Can I emit dialogs and overlays?**
A: Yes, but they should be composed through a single entry point, often using conditional composition.

## Migration Checklist

- [ ] Remove all `@Suppress("MultipleEmitters")` annotations
- [ ] Refactor functions with multiple emissions
- [ ] Test functionality remains unchanged
- [ ] Run static analysis to verify compliance
- [ ] Update any related documentation

## Enforcement Timeline

- **Week 1**: Remove existing suppressions
- **Week 2**: CI/CD integration complete
- **Ongoing**: Automatic enforcement on all changes

Remember: The goal is better performance and more maintainable Compose code!

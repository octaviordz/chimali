# Compose Patterns Guide

**Purpose**: Comprehensive guide for writing Compose functions that follow the MultipleEmitters rule  
**Target Audience**: Android developers working with Jetpack Compose  
**Last Updated**: 2026-04-27

## Understanding the MultipleEmitters Rule

### What is MultipleEmitters?

The MultipleEmitters rule enforces that Compose functions emit content through a single path. This means a Composable function should have exactly one place where it emits UI content at the top level.

### Why is this Important?

1. **Performance**: Prevents unnecessary recompositions
2. **Predictability**: Makes code behavior easier to understand
3. **Maintainability**: Reduces complex composition logic
4. **60 FPS Target**: Ensures smooth UI performance

## ✅ Correct Patterns

### 1. Single Container Pattern

```kotlin
@Composable
fun UserProfile() {
    Column {  // Single emission point
        Text("User Name")
        Button("Edit") { }
        Spacer(Modifier.height(8.dp))
        Text("user@example.com")
    }
}
```

### 2. Conditional Content Pattern

```kotlin
@Composable
fun ConditionalContent(showDetails: Boolean) {
    Column {  // Single emission point
        Text("User Information")
        
        if (showDetails) {
            Text("Detailed information")
            Button("Hide Details") { }
        } else {
            Text("Summary")
            Button("Show Details") { }
        }
    }
}
```

### 3. State Management Pattern

```kotlin
@Composable
fun Counter() {
    var count by remember { mutableStateOf(0) }
    
    Column {  // Single emission point
        Text("Count: $count")
        Button("Increment") { count++ }
        Button("Decrement") { 
            if (count > 0) count-- 
        }
    }
}
```

### 4. List Pattern

```kotlin
@Composable
fun ItemList(items: List<String>) {
    LazyColumn {  // Single emission point
        items(items) { item ->
            Text(item)
            Divider()
        }
    }
}
```

### 5. Dialog/Overlay Pattern

```kotlin
@Composable
fun ScreenWithDialog(showDialog: Boolean) {
    Box {  // Single emission point
        // Main content
        Column {
            Text("Main Content")
            Button("Show Dialog") { /* showDialog = true */ }
        }
        
        // Overlay content
        if (showDialog) {
            Dialog(onDismiss = { /* showDialog = false */ }) {
                Text("Dialog Content")
                Button("Close") { /* showDialog = false */ }
            }
        }
    }
}
```

## ❌ Incorrect Patterns

### 1. Multiple Top-Level Elements

```kotlin
@Composable
fun BadExample() {
    Text("First emission")  // ❌ First emission
    Button("Second") { }   // ❌ Second emission
    Spacer(Modifier.height(8.dp))  // ❌ Third emission
}
```

### 2. Mixed Content Types

```kotlin
@Composable
fun MixedContent() {
    Text("Text content")   // ❌ Text emission
    Dialog(onDismiss = { }) {  // ❌ Dialog emission
        Text("Dialog content")
    }
}
```

### 3. Conditional Top-Level Content

```kotlin
@Composable
fun ConditionalTopLevel() {
    if (someCondition) {
        Text("Content")    // ❌ Conditional emission
    }
    Button("Action") { }  // ❌ Another emission
}
```

## Refactoring Strategies

### 1. Wrap in Container

**Before:**
```kotlin
@Composable
fun BadHeader() {
    Text("Title")
    Text("Subtitle")
}
```

**After:**
```kotlin
@Composable
fun GoodHeader() {
    Column {
        Text("Title")
        Text("Subtitle")
    }
}
```

### 2. Extract to Separate Functions

**Before:**
```kotlin
@Composable
fun BadScreen() {
    Text("Header")
    LazyColumn {
        items(items) { item ->
            Text(item.name)
        }
    }
}
```

**After:**
```kotlin
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

### 3. Use Box for Overlays

**Before:**
```kotlin
@Composable
fun BadOverlay() {
    Text("Main content")
    if (showDialog) {
        Dialog { /* content */ }
    }
}
```

**After:**
```kotlin
@Composable
fun GoodOverlay() {
    Box {
        Text("Main content")
        if (showDialog) {
            Dialog { /* content */ }
        }
    }
}
```

### 4. Conditional Composition

**Before:**
```kotlin
@Composable
fun BadConditional() {
    if (isLoading) {
        LoadingIndicator()
    } else {
        Content()
    }
    ErrorBar()  // This will cause MultipleEmitters
}
```

**After:**
```kotlin
@Composable
fun GoodConditional() {
    Column {
        if (isLoading) {
            LoadingIndicator()
        } else {
            Content()
        }
        ErrorBar()
    }
}
```

## Advanced Patterns

### 1. Composition Local Pattern

```kotlin
@Composable
fun ThemedScreen() {
    CompositionLocalProvider(LocalColors provides customColors) {
        Column {  // Single emission point
            Header()
            Content()
            Footer()
        }
    }
}
```

### 2. Animation Pattern

```kotlin
@Composable
fun AnimatedContent(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxWidth()
    ) {  // Single emission point
        Column {
            Text("Animated Content")
            Button("Action") { }
        }
    }
}
```

### 3. Custom Layout Pattern

```kotlin
@Composable
fun CustomLayoutScreen() {
    Layout(
        content = {
            Text("Item 1")
            Text("Item 2")
            Text("Item 3")
        }
    ) { measurables, constraints ->
        // Custom layout logic
    }
}
```

## Common Scenarios and Solutions

### Scenario 1: Form with Error States

**Problem:** Multiple conditional elements

```kotlin
@Composable
fun BadForm() {
    if (hasError) {
        Text("Error message")
    }
    TextField("Email", value = email, onValueChange = { })
    Button("Submit") { }
}
```

**Solution:** Wrap in Column

```kotlin
@Composable
fun GoodForm() {
    Column {
        if (hasError) {
            Text("Error message", color = MaterialTheme.colorScheme.error)
        }
        TextField("Email", value = email, onValueChange = { })
        Button("Submit") { }
    }
}
```

### Scenario 2: Screen with Navigation

**Problem:** Navigation elements mixed with content

```kotlin
@Composable
fun BadNavigation() {
    TopAppBar("Title")
    LazyColumn { /* content */ }
    BottomNavigation { /* nav items */ }
}
```

**Solution:** Use Scaffold

```kotlin
@Composable
fun GoodNavigation() {
    Scaffold(
        topBar = { TopAppBar("Title") },
        bottomBar = { BottomNavigation { /* nav items */ } }
    ) { padding ->
        LazyColumn(
            contentPadding = padding
        ) { /* content */ }
    }
}
```

### Scenario 3: List with Header and Footer

**Problem:** Header, list, and footer as separate emissions

```kotlin
@Composable
fun BadListScreen() {
    Text("Header")
    LazyColumn { /* items */ }
    Text("Footer")
}
```

**Solution:** Use Column with LazyColumn

```kotlin
@Composable
fun GoodListScreen() {
    Column {
        Text("Header")
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) { /* items */ }
        Text("Footer")
    }
}
```

## Testing Your Changes

### 1. Static Analysis

```bash
./gradlew detekt
```

### 2. Local CI

```bash
./tools/local-ci.ps1
```

### 3. Pre-commit Check

```bash
git add .
git commit -m "Test changes"  # Will run pre-commit hook
```

## IDE Integration

### Android Studio Setup

1. Install Detekt plugin
2. Configure to highlight MultipleEmitters violations
3. Enable real-time analysis

### Quick Fixes

Common quick fixes provided by IDE:
- "Wrap in Column"
- "Extract to function"
- "Use Box for overlay"

## Performance Considerations

### Impact of MultipleEmitters

1. **Recomposition Overhead**: Multiple emissions can cause unnecessary recompositions
2. **State Management**: Complex composition makes state management harder
3. **Memory Usage**: Inefficient composition can increase memory usage

### Best Practices

1. **Keep Composables Small**: Single responsibility functions
2. **Use Proper Containers**: Column, Row, Box, LazyColumn, etc.
3. **Extract When Needed**: Break down complex composables
4. **Test Performance**: Use Compose compiler reports

## Troubleshooting

### Common Issues

**Issue**: "This @Composable function emits content but doesn't have a modifier parameter"

**Solution**: Add modifier parameter to allow flexibility

```kotlin
@Composable
fun GoodComposable(modifier: Modifier = Modifier) {
    Column(modifier = modifier) { /* content */ }
}
```

**Issue**: Complex conditional logic

**Solution**: Extract conditional logic to separate functions

```kotlin
@Composable
fun ComplexScreen(condition: Boolean) {
    Column {
        if (condition) {
            ConditionalContent()
        } else {
            DefaultContent()
        }
    }
}
```

## Resources

- [Compose Rules Documentation](https://mrmans0n.github.io/compose-rules/)
- [Jetpack Compose Performance](https://developer.android.com/jetpack/compose/performance)
- [Compose Guidelines](https://developer.android.com/jetpack/compose/guidelines)

## Checklist for New Composables

- [ ] Single emission point at top level
- [ ] No @Suppress("MultipleEmitters") annotations
- [ ] Proper container (Column, Row, Box, etc.)
- [ ] Modifier parameter for flexibility
- [ ] Test with Detekt
- [ ] Verify performance impact

Remember: The goal is better performance and more maintainable Compose code!

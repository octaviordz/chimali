# Quickstart: Resolving MagicNumber Violations

This guide explains how to identify and fix `MagicNumber` violations following the new project-wide enforcement.

## Running the Check

To check for magic numbers (and other Detekt rules), run:

```bash
./gradlew detekt
```

## How to Resolve Violations

### 1. Extract to Constant (Preferred)

If you see:
```kotlin
fun retry() {
    repeat(3) { ... } // 3 is a magic number
}
```

Change it to:
```kotlin
private const val MAX_RETRY_COUNT = 3

fun retry() {
    repeat(MAX_RETRY_COUNT) { ... }
}
```

### 2. Use Named Arguments

If the number is self-explanatory in context and the function has a clear parameter name:
```kotlin
// Allowed because ignoreNamedArgument is true
delay(timeMillis = 500) 
```

### 3. Move to Build Configuration

For values in `build.gradle.kts` that are not SDK versions (which are ignored), consider using `gradle.properties` or a shared `Versions` object.

## When to Exclude

Generated code is automatically excluded. If you encounter a file that is tool-generated but not in the standard `build/generated` path, add it to the `excludes` list in `detekt.yml`.

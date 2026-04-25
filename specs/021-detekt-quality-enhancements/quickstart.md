# Quickstart: Detekt Quality Enhancements

This feature introduces new quality gates to the project. Developers must adhere to the following rules to pass the local CI.

## 1. Logging Discipline
Standard output calls (`println`, `print`) are now **prohibited** in production code.

**Bad:**
```kotlin
println("User logged in: $userId")
```

**Good:**
```kotlin
Logger.d { "User logged in: $userId" }
```

*Note: println is still allowed in `test` and `androidTest` source sets.*

## 2. Idiomatic Scope Functions
Avoid unnecessary use of `.let` when it doesn't provide null-safety or a meaningful scope shift.

**Bad:**
```kotlin
val name = "Chimali"
name.let { println(it) } // Unnecessary let
```

**Good:**
```kotlin
val name = "Chimali"
println(name)
```

## 3. String Literal Duplication
Avoid using the same string literal multiple times in a single file. Extract them to constants.

**Bad:**
```kotlin
// Used in multiple places in the same file
Text("Submit")
Button(onClick = { /* ... */ }) { Text("Submit") }
```

**Good:**
```kotlin
private const val BUTTON_LABEL_SUBMIT = "Submit"

Text(BUTTON_LABEL_SUBMIT)
Button(onClick = { /* ... */ }) { Text(BUTTON_LABEL_SUBMIT) }
```

*Threshold: 3 or more occurrences of strings >= 5 characters.*

## Verification
Run the local CI to verify your changes:
```powershell
./tools/local-ci.ps1
```

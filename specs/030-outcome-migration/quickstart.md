# Quickstart: Using Outcome

## Overview

The `Outcome` class provides a functional way to handle errors in the domain and data layers, completely eliminating the need for `try-catch` blocks in ViewModels and UI code.

## How to use

### In a Repository/Service (Data Boundary)

Always catch specific exceptions at the boundary, log them using Kermit, and wrap them in a categorized `Outcome.Error`.

```kotlin
suspend fun fetchItems(): Outcome<List<Item>, DomainError.NetworkError> {
    return try {
        val data = api.get()
        Outcome.Success(data)
    } catch (e: IOException) {
        Logger.e(e) { "Failed to fetch items" }
        Outcome.Error(DomainError.NetworkError("Network failure", e))
    }
}
```

### In a ViewModel (Presentation Layer)

Never use `try-catch`. Always use an exhaustive `when` expression.

```kotlin
when (val result = fetchItems()) {
    is Outcome.Success -> {
        _state.update { it.copy(items = result.data) }
    }
    is Outcome.Error -> {
        // Safe to read result.error.message because we know the type
        _state.update { it.copy(error = result.error.message) }
    }
}
```

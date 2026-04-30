# Functional Exception Handling Quickstart

## Overview
This feature replaces all `try-catch` blocks in ViewModels and Domain logic with functional `DataResult` types. It also removes all `@Suppress("TooGenericExceptionCaught")` instances.

## Usage

### 1. Repository Boundary (Data Layer)
Catch precise exceptions from the API, log them, and return a `DataResult.Error`.

```kotlin
suspend fun fetchData(): DataResult<MyData, DomainError> {
    return try {
        val response = api.getData()
        DataResult.Success(response)
    } catch (e: IOException) {
        // Log at the boundary!
        Logger.e(e) { "Network failure fetching data" }
        DataResult.Error(DomainError.NetworkError(e))
    } catch (e: SecurityException) {
        Logger.e(e) { "Security failure" }
        DataResult.Error(DomainError.SecurityError(e))
    }
}
```

### 2. ViewModel (Presentation Layer)
Use exhaustive `when` to handle results without `try-catch`.

```kotlin
fun loadData() {
    viewModelScope.launch {
        when (val result = repository.fetchData()) {
            is DataResult.Success -> {
                _uiState.value = UiState.Success(result.data)
            }
            is DataResult.Error -> {
                // No logging here, it was done at the boundary!
                _uiState.value = UiState.Error(mapErrorToUserMessage(result.error))
            }
        }
    }
}
```

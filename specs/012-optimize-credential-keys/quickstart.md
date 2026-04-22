# Quickstart: Lazy Loading Credentials

This guide explains how to use the new paginated repository methods to render the passkeys list without causing UI stuttering.

## 1. Using the Pagination Methods

In your `GetAllCredentialsUseCase`, instead of fetching all credentials at once, fetch them by page:

```kotlin
val pageSize = 20L
val page0 = repository.getPagedCredentials(limit = pageSize, offset = 0L)
val page1 = repository.getPagedCredentials(limit = pageSize, offset = pageSize)
```

## 2. Managing State in ViewModel

The `CredentialManagementViewModel` should maintain an active list and a pagination state:

```kotlin
private val _credentials = MutableStateFlow<List<PasskeyCredential>>(emptyList())
private var currentOffset = 0L
private val PAGE_SIZE = 20L
private var hasMorePages = true

fun loadNextPage() {
    if (!hasMorePages || isLoading) return
    
    viewModelScope.launch {
        isLoading = true
        val result = getAllCredentialsUseCase(limit = PAGE_SIZE, offset = currentOffset)
        
        result.onSuccess { newItems ->
            if (newItems.size < PAGE_SIZE) hasMorePages = false
            currentOffset += newItems.size
            _credentials.update { it + newItems }
        }
        isLoading = false
    }
}
```

## 3. Triggering Loads from Jetpack Compose

In `CredentialListScreen.kt`, use `LazyColumn`'s layout info to detect when the user is near the end of the list:

```kotlin
val listState = rememberLazyListState()

// Observe scrolling to trigger next page
LaunchedEffect(listState) {
    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
        .collect { lastVisibleIndex ->
            if (lastVisibleIndex != null && lastVisibleIndex >= state.credentials.size - 5) {
                viewModel.loadNextPage()
            }
        }
}

LazyColumn(state = listState) {
    items(state.credentials) { credential ->
        CredentialItem(credential)
    }
}
```

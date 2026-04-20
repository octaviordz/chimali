# Quickstart: core:common (KMP)

## Overview
The `core:common` module provides shared infrastructure for the Chimali project, including event bus, clipboard management, and coroutine dispatcher management. It is a Kotlin Multiplatform (KMP) module supporting Android and iOS.

## Key Components

### 1. Fido2EventBus
A shared reactive stream for cross-module events.
```kotlin
// In commonMain
class MyService(private val eventBus: Fido2EventBus) {
    fun doSomething() {
        eventBus.post(Fido2Event.SomeEvent)
    }
}
```

### 2. ClipboardManagerService
Interface for interacting with the system clipboard.
```kotlin
// In commonMain
interface ClipboardManagerService {
    fun copyToClipboard(label: String, text: String, isSensitive: Boolean)
    fun clearClipboard()
}
```

### 3. Coroutine Dispatchers
Standardized qualifiers for injecting dispatchers.
```kotlin
// In commonMain
class MyViewModel(
    @Named(DispatcherQualifiers.IO) private val ioDispatcher: CoroutineDispatcher
) : ViewModel()
```

## Setup in Feature Modules
Add the dependency to your feature's `commonMain`:
```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
        }
    }
}
```

## Initialization
Include `commonModule` in your Koin initialization:
```kotlin
startKoin {
    modules(commonModule, platformModule, ...)
}
```

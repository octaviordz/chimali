# core:domain

Kotlin Multiplatform (KMP) module for shared business logic, UseCases, and Domain Models.

## Architecture
This module follows Clean Architecture principles:
- **commonMain**: Platform-agnostic domain logic and entities.
- **androidMain**: Android-specific extensions.
- **iosMain**: iOS-specific extensions.

## Dependency Injection
Standardized on **Koin Annotations**. Use `@Factory` for UseCases and `@Single` for Repositories/Services.

## Usage
Add as a dependency to feature modules:
```kotlin
implementation(project(":core:domain"))
```

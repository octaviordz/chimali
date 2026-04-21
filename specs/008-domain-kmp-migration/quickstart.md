# Quickstart: core:domain KMP Module

## Configuration
The `core:domain` module is now a Kotlin Multiplatform (KMP) module supporting Android and iOS.

### Source Sets
- `commonMain`: Shared domain logic.
- `androidMain`: Android-specific domain logic/extensions.
- `iosMain`: iOS-specific domain logic/extensions.

## Adding a New UseCase
1. Define the UseCase in `commonMain`:
   ```kotlin
   package com.chimali.core.domain.usecase

   import org.koin.core.annotation.Factory

   @Factory
   class MyUseCase {
       operator fun invoke() {
           // Business logic here
       }
   }
   ```
2. Koin will automatically detect the `@Factory` annotation during build (KSP).

## Compilation
- **Android**: `./gradlew :core:domain:assembleDebug`
- **iOS**: `./gradlew :core:domain:iosArm64MainKlibrary`

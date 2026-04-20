# Implementation Plan: KMP Common Module Migration

Refactor the `core:common` module from an Android-only library to a Kotlin Multiplatform (KMP) module to support cross-platform development (Android and iOS).

## Technical Context

### Existing Structure
- `core:common` is an Android library (`com.android.library`).
- Uses Koin for DI.
- Contains clipboard management, event bus, and dispatcher qualifiers.

### Proposed Structure
- `core:common` will be a KMP library.
- **commonMain**:
    - `Fido2EventBus`
    - `ClipboardManagerService` (interface)
    - `DispatcherQualifiers`
    - `DispatchersModule` (common parts)
    - Koin common definitions
- **androidMain**:
    - `AndroidClipboardManagerService`
    - `AndroidDispatchers`
    - Android-specific Koin module
- **iosMain**:
    - `IosClipboardManagerService` (placeholder)
    - `IosDispatchers`
    - iOS-specific Koin module

## Constitution Check

| Principle | Adherence | Rationale |
|-----------|-----------|-----------|
| III. Architecture | ✅ | Maintaining modularity and using Koin for KMP support. |
| IV. Performance | ✅ | Preserving clipboard clearing logic (60s security requirement). |
| VII. Documentation | ✅ | IEEE 830 compliant spec and detailed planning. |

## Proposed Changes

### [core:common]

#### [MODIFY] [build.gradle.kts](file:///d:/octav/source/repos/Chimali/core/common/build.gradle.kts)
- Apply `kotlin("multiplatform")` instead of `kotlin("android")`.
- Configure `androidTarget()`, `iosArm64()`, `iosSimulatorArm64()`.
- Define source sets and move dependencies.

#### [NEW] [ClipboardManagerService.kt](file:///d:/octav/source/repos/Chimali/core/common/src/commonMain/kotlin/com/chimali/core/clipboard/ClipboardManagerService.kt)
- Move existing interface from `main` to `commonMain`.

#### [NEW] [Fido2EventBus.kt](file:///d:/octav/source/repos/Chimali/core/common/src/commonMain/kotlin/com/chimali/core/events/Fido2EventBus.kt)
- Move existing class from `main` to `commonMain`.

#### [NEW] [DispatcherQualifiers.kt](file:///d:/octav/source/repos/Chimali/core/common/src/commonMain/kotlin/com/chimali/core/common/di/DispatcherQualifiers.kt)
- Move existing object from `main` to `commonMain`.

#### [MODIFY] [AndroidClipboardManagerService.kt](file:///d:/octav/source/repos/Chimali/core/common/src/androidMain/kotlin/com/chimali/core/clipboard/AndroidClipboardManagerService.kt)
- Ensure it remains in `androidMain` and correctly implements the moved interface.

#### [DELETE] [main/ source directories](file:///d:/octav/source/repos/Chimali/core/common/src/main)
- Remove once all code is moved to `commonMain` or `androidMain`.

## Verification Plan

### Automated Tests
- Run `./gradlew :core:common:test` to verify Android unit tests.
- Run `./gradlew :core:common:compileKotlinIosArm64` to verify iOS compilation.
- Verify `commonTest` execution on JVM.

### Manual Verification
- Check that feature modules (e.g., `:feature:fido2`) can still build and access `core:common` components.

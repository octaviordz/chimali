# Implementation Plan: KMP Domain Module Migration

Refactor the `core:domain` module from an Android-only library to a Kotlin Multiplatform (KMP) module to support cross-platform development (Android and iOS). This migration is strictly structural, setting up the foundation for future shared business logic.

## Technical Context

### Existing Structure
- `core:domain` is an Android library (`com.android.library`).
- Uses Koin Annotations for DI (currently empty).
- Contains no Kotlin source files at the moment.
- `build.gradle.kts` is configured for Android only.

### Proposed Structure
- **commonMain**:
    - Placeholder for shared UseCases, Domain Models, and Repository interfaces.
    - Dependencies: `koin-core`, `koin-annotations`, `kotlinx-coroutines-core`.
- **androidMain**:
    - Android-specific domain extensions if needed.
    - Dependencies: `koin-android`.
- **iosMain**:
    - iOS-specific domain extensions if needed.
- **KSP Configuration**:
    - Configured for target-specific Koin Annotations processing (`kspAndroid`, `kspIosArm64`, etc.).

## Constitution Check

| Principle | Adherence | Rationale |
|-----------|-----------|-----------|
| III. Architecture | ✅ | Standardizing on Koin Annotations for KMP support ensures long-term architectural integrity. |
| III. Quality | ✅ | Clean architecture preservation by separating pure domain logic from platform dependencies. |
| VII. Documentation | ✅ | IEEE 830 compliant spec and detailed planning. |

## Proposed Changes

### [core:domain]

#### [MODIFY] [build.gradle.kts](file:///d:/octav/source/repos/Chimali/core/domain/build.gradle.kts)
- Apply `kotlin("multiplatform")` and `com.google.devtools.ksp`.
- Configure `androidTarget()`, `iosArm64()`, and `iosSimulatorArm64()`.
- Define source sets and move dependencies to appropriate locations.
- Configure target-specific KSP for Koin Annotations.

#### [NEW] [Source Directory Structure]
- Create `src/commonMain/kotlin/com/chimali/core/domain`.
- Create `src/androidMain/kotlin/com/chimali/core/domain`.
- Create `src/iosMain/kotlin/com/chimali/core/domain`.

## Verification Plan

### Automated Tests
- **Android Compilation**: `./gradlew :core:domain:assembleDebug`.
- **iOS Compilation**: `./gradlew :core:domain:iosArm64MainKlibrary`.
- **Unit Tests**: Ensure `./gradlew :core:domain:allTests` passes (once tests are added).

### Manual Verification
- Verify that a dummy class in `commonMain` is accessible from both `androidMain` and `iosMain`.
- Check for zero `android.*` imports in `commonMain`.

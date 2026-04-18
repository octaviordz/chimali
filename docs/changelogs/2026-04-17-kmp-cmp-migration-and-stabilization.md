# Detailed Changes: KMP & CMP Migration and Test Stabilization

**Date**: 2026-04-17  
**Feature Area**: Infrastructure / Kotlin Multiplatform (KMP) / Compose Multiplatform (CMP) / Testing  
**Status**: Completed  

## Goal
Advance the project's technical architecture towards full Multiplatform support. This includes migrating from Hilt to Koin for dependency injection, integrating Compose Multiplatform plugins, adding KMP-native cryptographic libraries, and stabilizing the test suite to run on non-Android targets.

## Architectural Changes

### 1. Dependency Injection: Hilt to Koin Migration
- **Objective**: Replace the JVM-bound Hilt framework with Koin to enable dependency injection across Android and iOS targets (Constitution §III).
- **Changes**:
    - Integrated `koin-core`, `koin-android`, `koin-compose`, and `koin-annotations` in `libs.versions.toml`.
    - Refactored `:feature:vault` and `:feature:fido2` to use Koin modules and `@KoinViewModel` annotations.
    - Added KSP support for Koin annotation processing to ensure compile-time safety for dependency wiring.

### 2. Compose Multiplatform (CMP) Integration
- **Objective**: Transition UI development from standard Jetpack Compose to Compose Multiplatform to allow UI sharing with iOS.
- **Changes**:
    - Applied the `org.jetbrains.compose` and `org.jetbrains.kotlin.plugin.compose` plugins to the `:feature:vault` and `:feature:fido2` modules.
    - Standardized `libs.versions.toml` to use Multiplatform-compatible Compose libraries.
    - Established the directory structure for iOS UI implementation (`feature/vault/src/iosMain/`).

### 3. KMP-Native Cryptography (Signum Adoption)
- **Objective**: Introduce a multiplatform-native cryptographic library to reduce reliance on BouncyCastle (JVM-only).
- **Changes**:
    - Added `at.asitplus.signum:indispensable` to the project's dependency graph.
    - Established the strategy for migrating cryptographic operations from BouncyCastle (in `androidMain`) to Signum (in `commonMain`).

## Technical Fixes & Test Stabilization

### 4. Multiplatform Assertion Migration
- **Objective**: Resolve legacy JUnit dependencies that block non-JVM builds.
- **Fixes**:
    - Migrated 300+ assertions from JUnit 4/5 (`assertArrayEquals`, `assertInstanceOf`, `assertThrows`) to the multiplatform `kotlin.test` framework (`assertContentEquals`, `assertIs`, `assertFailsWith`).
    - Standardized error handling in `RegisterCredentialUseCaseTest` and `Fido2CryptoServiceTest` using `assertFailsWith`.

### 5. Cryptographic Type Mismatch Resolution
- **Issue**: Updated HDK data contracts to be platform-agnostic (using `ByteArray` for all keys/scalars) caused mismatches in existing tests that used `BigInteger` and `ECPoint`.
- **Fix**: Implemented explicit serialization in `Fido2CryptoServiceTest`, `Fido2StressTest`, and `MultiAlgorithmIntegrationTest` using `P256Group` utilities.

### 6. Git Hygiene
- **Metadata Exclusion**: Updated the root `.gitignore` to explicitly exclude `.kotlin/` metadata and `*.klib` artifacts, addressing local build cache tracking issues.

## Verification Results
- **Android Targets**: `.\gradlew testDebugUnitTest` passed 100% across all 13 modules.
- **KMP Compilation**: Verified that `:feature:fido2` and `:feature:vault` successfully resolve dependencies for `commonMain` and `iosMain` targets.
- **Koin Validation**: Confirmed `@Single` and `@KoinViewModel` bindings generate valid code via KSP.

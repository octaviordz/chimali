# Changelog: Build Modernization & DSL Fixes (2026-02-26)

## Overview
Resolved multiple compilation errors across the newly integrated FIDO2 modules and existing `core:bluetooth` components. These issues stemmed from newer, stricter syntax requirements in the Android Gradle Plugin (AGP 9.0+), Kotlin 2.0.x, and SQLDelight 2.x.

## Changes

### AGP 9.0+ & Kotlin 2.0+ DSL Migration
- **Explicit LibraryExtension (`android`)**: The implicit `android { }` DSL block has been deprecated in AGP 9.0+ when `android.newDsl=true`. Migrated `feature:fido2` and `core:fido2` build scripts to use explicit `extensions.configure<com.android.build.api.dsl.LibraryExtension> { }`.
- **Compiler Options (`kotlin`)**: Migrated away from deprecated `kotlinOptions { jvmTarget = "17" }`. Adopted the modern Kotlin DSL structure: `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }`.

### Source Set Restructuring
- **`feature:fido2`**: Relocated all `api`, `internal`, and `ui` source packages from `src/` to the standard Android `src/main/java/com/chimali/feature/fido2/` directory structure. This resolved missing reference errors during code compilation.
- **`core:bluetooth`**: Moved `HidManager.kt` from the root `src/` directory to its proper package location at `src/main/kotlin/com/chimali/core/bluetooth/impl/`.

### Type Strictness & Dependency Updates
- **Kotlin 2.0 Literal Narrowing**: Explicitly appended `.toByte()` to all hex literals within the FIDO HID descriptor in `HidManager.kt`, complying with stricter compiler safety checks in Kotlin 2.0 (`Int` to `Byte`).
- **SQLDelight 2.x Coroutine Context**: In `CredentialRepository.kt`, updated `mapToList()` to explicitly provide a CoroutineContext (`Dispatchers.IO`) per SQLDelight 2.x API changes. Updated imports from `com.squareup.sqldelight` to `app.cash.sqldelight`.
- **Material Icons Catalog**: Adjusted `libs.versions.toml` to accurately define and reference the `androidx-compose-material-icons-extended` dependency using standard Gradle catalog nesting conventions.

## Impact
All `:core:*` and `:feature:*` modules compile successfully under Kotlin 2.0.21+ and AGP 9.0.1+ without deprecation warnings related to the Android block.

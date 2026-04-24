# Changelog: KMP Debug Flag Implementation

**Date**: 2026-04-24
**Author**: Antigravity
**Status**: COMPLETED

## Overview
Implemented a secure, platform-agnostic build variant detection system using the `expect/actual` pattern in Kotlin Multiplatform. This replaces insecure hardcoded guards and enables R8/ProGuard dead-code elimination for sensitive development tools.

## Changes

### Core Infrastructure (:core:common)
- **Centralized Detection**: Added `expect val isDebug` in `commonMain` to provide a unified API for build variant checks across all modules.
- **Android Implementation**: Implemented `actual val isDebug` using reflection on `ActivityThread.currentApplication()`. This allows automatic detection of the `FLAG_DEBUGGABLE` flag without requiring a `Context` parameter, maintaining compatibility with the single-variant Android Kotlin Multiplatform (AKM) plugin.
- **iOS Implementation**: Implemented `actual val isDebug` using `Platform.isDebugBinary` from Kotlin/Native.
- **Build Configuration**: Enabled explicit `iosMain` source set hierarchy in `build.gradle.kts` to support native code compilation.
- **Testing**: Added `BuildVariantTest` in `commonTest` to verify flag accessibility.

### Feature Adoption (:feature:fido2)
- **Security Hardening**: Replaced `if (true)` hardcoded guards in `DevelopmentToolsScreen.kt` with `isDebug`. Sensitive mnemonic and master seed recovery tools are now physically stripped from production binaries via R8.
- **Refactoring**: Updated `Fido2Initializer` to consume the centralized `isDebug` flag directly, removing the need for the `isDebug` parameter in its `init` method.

### Application (:app)
- **Initialization**: Simplified `ChimaliApplication.kt` by removing the manual passing of `BuildConfig.DEBUG` to feature initializers.

## Verification
- **Android**: Verified via `:core:common:compileAndroidHostTest` and local CI pipeline.
- **iOS**: Syntactically verified; binary validation requires a Mac host.
- **Ktlint**: Resolved all style violations in the newly created files.

## References
- Specification: `specs/016-kmp-debug-flag/spec.md`
- Implementation Plan: `specs/016-kmp-debug-flag/plan.md`
- Task List: `specs/016-kmp-debug-flag/tasks.md`

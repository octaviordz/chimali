# Changelog: CI Hardening & KtLint Polish (2026-04-23)

## Overview
This update focuses on hardening the local CI infrastructure, enforcing project-wide code quality standards, and resolving remaining build compliance issues following the KMP transition.

## Changes

### 🛠 CI Infrastructure (`tools/local-ci.ps1`)
- **Strict Failure Detection**: Refactored the `Run-Task` helper to correctly monitor `$LASTEXITCODE`. The pipeline now reliably stops on Gradle failures instead of reporting a false "PASS".
- **Comprehensive Compilation**: Added a new "Compile All" phase that targets:
    - `compileDebugSources` & `compileAndroidMain` (Production)
    - `compileDebugUnitTestSources` (Unit Tests)
    - `compileAndroidHostTestSources` (Android Host Tests)
    - `compileDebugAndroidTestSources` (Instrumented Tests)
- **Incremental Build Optimization**: Switched the `Clean` step to **opt-in** via the `-Clean` flag. This significantly reduces local iteration time (down to ~57s) and avoids Windows file-lock issues.

### 🎨 Code Quality & Formatting
- **Project-wide KtLint**: Applied `./gradlew ktlintFormat` across all 13 modules to ensure 100% compliance with the Official Kotlin Style Guide.
- **Enforcement Gate**: Verified that `ktlintCheck` correctly fails the build on style violations, providing a robust pre-commit quality gate.
- **Detekt Noise Reduction**: Updated `detekt.yml` with targeted exclusions for test source sets (e.g., `WildcardImport`, `LongMethod`) to allow expressive test code while maintaining strict production standards.

### ⚙️ Build Compliance
- **KSP/Koin Stabilization**: Suppressed `defaultModule` deprecation warnings by passing `KOIN_DEFAULT_MODULE = "true"` to KSP arguments across all core and feature modules.
- **Editor Standards**: Standardized `.editorconfig` to enforce `insert_final_newline`, `trim_trailing_whitespace`, and `lf` line endings across the entire repository.

## Fixed
- **Test Compilation**: Resolved a critical "No value passed for parameter 'user'" error in `RegisterCredentialUseCaseTest.kt` that was previously hidden from the CI pipeline.
- **Formatting Conflicts**: Manually resolved KtLint conflicts in `BluetoothHidConfig.kt` and `Fido2CryptoService.kt` where auto-format was insufficient.

## Verification
- ✅ `local-ci.ps1` PASS (57.09s)
- ✅ `ktlintCheck` PASS
- ✅ `detekt` PASS
- ✅ Unit Tests PASS

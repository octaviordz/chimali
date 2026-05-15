# Compose BOM Update and Test Fixes (2026-05-15)

## Overview
This update completes the migration to the Jetpack Compose Bill of Materials (BOM) version `2026.05.00` to ensure dependency alignment and eliminate instrumentation test noise. Several compilation issues exposed by the update and subsequent refactors were fixed to restore full stability to the continuous integration pipeline.

## Build Infrastructure
- **Compose BOM Upgrade**: Updated the `compose-bom` dependency in `gradle/libs.versions.toml` to version `2026.05.00`. Removed manual version pins for Compose libraries that are now natively managed by the BOM, aligning the environment with Kotlin 2.3.20 requirements.
- **KMP Resource Workaround**: Resolved a build failure encountered during the execution of `CopyResourcesToAndroidAssetsTask` in the `feature:fido2` module. Added `androidResources { enable = true }` to explicitly enable Android-specific resource packaging required by JetBrains Compose 1.11.0 and AGP 9+.

## Quality and Test Fixes
- **Test Import Remediations**: Addressed compilation errors in `RegistrationFlowIntegrationTest.kt` and several other UI tests in `feature/fido2`. Restored missing `ComponentActivity` and `createAndroidComposeRule` imports that were omitted during a prior refactoring phase.
- **Auto-Formatting Compliance**: Executed Ktlint auto-fixing (`tools/local-ci.ps1`) to strictly format import orders and syntax across Android device test sets.
- **CI Validation**: Passed the complete pipeline, ensuring zero violations for Detekt, Ktlint, and Android Lint, while successfully compiling and passing all tests across the project.

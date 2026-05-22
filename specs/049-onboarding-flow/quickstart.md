# Quickstart: Onboarding Flow

**Feature**: 049-onboarding-flow | **Date**: 2026-05-22

## Overview

This feature introduces a first-run onboarding experience that introduces Chimali and allows users to select their desired core features: Vault, Passkey Authenticator, or both. It restructures the app's root navigation to handle routing based on onboarding state and feature selection.

## Getting Started for Developers

### 1. Proto DataStore Updates

The state is stored in `UserPreferences`. Run a Gradle build after modifying the proto file to generate the updated Kotlin data classes.

```bash
# From the repository root
./gradlew :core:common:generateProto
```

### 2. New Module: `feature:onboarding`

A new module is introduced for the onboarding UI.

*   **Path**: `feature/onboarding`
*   **Dependencies**: `:core:common` (for DataStore), `:core:ui` (for theme/components).
*   **Key Components**:
    *   `OnboardingNavGraph`: Internal navigation for the onboarding flow.
    *   `OnboardingViewModel`: Manages UI state and commits final feature selections to `UserPreferencesDataStore`.

### 3. App Shell Navigation Restructuring

The `app` module now owns the root `NavHost`.

*   **Path**: `app/src/main/kotlin/com/chimali/ChimaliApp.kt` (New)
*   **Responsibilities**:
    *   Observe `UserPreferences` to determine if onboarding is complete.
    *   If incomplete: route to `OnboardingNavGraph`.
    *   If complete: render the Main Shell (with conditional BottomNavigationBar).
    *   Route to Vault or Fido2 modules based on feature selection and last visited state.

### 4. Testing the Flow

To test the onboarding flow repeatedly during development:

1.  **Clear App Data**: This wipes the DataStore, simulating a fresh install.
    ```bash
    adb shell pm clear com.chimali
    ```
2.  **Launch App**: The app should start directly into the onboarding flow.
3.  **Test Re-take**: Navigate to the (new) Settings screen and trigger the onboarding re-take flow to ensure existing preferences are updated correctly.

## Architecture Notes

*   **No Circular Dependencies**: The `feature:onboarding` module MUST NOT depend on `feature:vault` or `feature:fido2`. The app module depends on all three and orchestrates the navigation between them.
*   **State Observation**: The app shell should collect the `UserPreferences` flow. Use a loading state (e.g., a splash screen) while the initial preferences are read from disk to avoid a flash of the wrong destination.

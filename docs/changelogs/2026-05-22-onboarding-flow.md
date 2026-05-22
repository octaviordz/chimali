# Onboarding Flow and App Shell Migration

## Overview
This update introduces the First-Run Onboarding Experience for new users and completes the migration to a unified app shell using a consolidated `AppNavGraph`. It resolves the missing foundational app routing, provides dynamic navigation based on user preferences, and adds a dedicated Settings module to manage those preferences.

## Major Changes

### 1. First-Run Onboarding Experience
- Added the `:feature:onboarding` module to handle the initial user experience.
- Implemented `IntroScreen` to welcome users.
- Implemented `FeatureSelectionScreen` allowing users to selectively enable the **Vault** feature, the **Passkey Authenticator** feature, or both.
- Configured data persistence to save feature selections into the `UserPreferences` Proto DataStore.

### 2. Unified App Shell & Dynamic Navigation
- Introduced `AppNavGraph` and `AppRoutingState` to the `:app` module, replacing the legacy hardcoded FIDO2 startup flow.
- The app now intelligently routes users to:
  - **Onboarding**: If the user has never completed the first-run experience.
  - **Main Shell**: Showing dynamic tabs depending on the features selected.
- Implemented an adaptive `BottomNavigationBar` in the main shell that only appears if the user has enabled *both* the Vault and Authenticator features.
- If only a single feature is enabled, the app routes directly to that feature without presenting a bottom navigation bar.
- Re-introduced the "Dev Tools" tab, which now dynamically appears alongside feature tabs specifically on `isDebug` builds.

### 3. Settings & Preference Management
- Added the `:feature:settings` module to allow users to adjust their feature preferences post-onboarding.
- Implemented the `SettingsScreen` where users can trigger the "Retake Onboarding" flow.
- Ensured seamless routing from the `AppNavGraph` into the Settings screens.

### 4. Technical Improvements & Bug Fixes
- Added comprehensive unit and UI testing across the new modules, including `OnboardingViewModelTest`, `SettingsScreenTest`, and `AppRoutingTest`.
- Fixed window inset UI overlapping issues in the onboarding flow, ensuring screens render properly under system UI elements like camera lenses and battery indicators.
- Fixed an infinite navigation loop bug in the app shell that occurred when attempting to save the "Dev Tools" tab state to the DataStore.
- Added JUnit 5 migration configurations to ensure smooth local CI test execution.

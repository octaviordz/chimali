# Data Model: Onboarding Flow

**Feature**: 049-onboarding-flow | **Date**: 2026-05-22

## Entities

### UserPreferences (Extension)

The existing `UserPreferences` proto message in `core/common` will be extended to store onboarding state.

**File**: `core/common/src/main/proto/user_preferences.proto`

**New Fields**:
*   `onboarding_completed` (bool, tag 5): Flag indicating if the user has finished the onboarding flow. Default: `false`.
*   `vault_feature_enabled` (bool, tag 6): Flag indicating if the Vault (Password Manager) feature is selected. Default: `false`.
*   `passkey_authenticator_feature_enabled` (bool, tag 7): Flag indicating if the Passkey Authenticator feature is selected. Default: `false`.
*   `last_visited_main_screen` (string, tag 8): Stores the route identifier of the last visited main screen (e.g., "vault/home" or "fido2/home"). Used to restore the last view for dual-feature users. Default: `""`.

*Note*: The `reserved 5 to 10;` statement in the current proto file must be updated or removed to allow using tags 5 through 8.

## State Transitions

### Onboarding Flow

1.  **Initial State**: `onboarding_completed` is `false`.
    *   App launch routes to the `feature:onboarding` graph.
2.  **Feature Selection**: User toggles Vault and/or Passkey features.
    *   UI state holds temporary selections.
3.  **Completion**: User finishes onboarding.
    *   `vault_feature_enabled` is set to the user's choice.
    *   `passkey_authenticator_feature_enabled` is set to the user's choice.
    *   `last_visited_main_screen` is initialized to the Passkey route if both are enabled, or the respective route if only one is enabled.
    *   `onboarding_completed` is set to `true`.
    *   App navigates to the main shell, which reads the new preferences and routes to the correct default screen.

### Re-take Onboarding

1.  **Trigger**: User initiates re-take from Settings.
2.  **Flow**: User goes through onboarding screens.
3.  **Completion**:
    *   `vault_feature_enabled` and `passkey_authenticator_feature_enabled` are updated.
    *   `last_visited_main_screen` is reset based on the new selection (especially important if downgrading from dual to single feature).
4.  **Cancellation**: If user backs out before completion, no changes are committed to the DataStore.

## Validation Rules

*   **Feature Selection Constraint**: During onboarding (or re-take), the user MUST select at least one feature before the "Continue/Finish" button is enabled. The state where both `vault_feature_enabled` and `passkey_authenticator_feature_enabled` are false is invalid and must be prevented by the UI.

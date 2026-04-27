# Changelog: Compose Modifier Compliance & Lint Cleanup

**Date**: 2026-04-27
**Task ID**: [024-enforce-modifier-missing]
**Status**: COMPLETED

## Summary
Successfully eliminated project-wide technical debt related to `ModifierMissing` and `ComposableParamOrder` lint rules. This was achieved through a systematic refactoring of over 30 UI components across the `feature/vault` and `feature/fido2` modules, establishing a robust and compliant parameter ordering pattern.

## Changes

### Jetpack Compose Refactoring
- **Modifier Enforcement**: Updated all identified composables to accept a `modifier: Modifier = Modifier` parameter and apply it to their root UI node (e.g., `Scaffold`, `Surface`, `ListItem`, `Row`).
- **Standardized Signature Pattern**: Established a consistent parameter ordering to resolve conflicts between `ComposableParamOrder` and `LambdaParameterEventTrailing` rules:
    - **Pattern**: `(requiredData, requiredEventLambdas, modifier: Modifier = Modifier, optionalParams)`
    - **Logic**: By placing required event lambdas before the `modifier` (which has a default), the `modifier` remains the trailing parameter, satisfying both the requirement that `modifier` follows required params and the rule against required trailing lambdas.

### Quality & Static Analysis
- **Suppression Cleanup**: Purged all `@Suppress("ModifierMissing")` and `@Suppress("ComposableParamOrder")` annotations from the codebase.
- **Documentation**: Updated `docs/quality.md` with a new "Compose Modifier conventions" section, codifying the parameter ordering rules to ensure long-term codebase health.
- **CI Hardening**: Verified that the updated components pass all `detekt` and `ktlint` checks locally via `tools/local-ci.ps1`.

### Affected Modules & Components

#### `:feature:vault`
- `VaultListScreen`, `VaultItemRow`
- `SecureNoteEntryScreen`, `SecureNoteDetailScreen`
- `PasswordEntryScreen`, `PasswordDetailScreen`, `DetailRow`
- `LabelManagerScreen`
- `CreditCardEntryScreen`, `CreditCardDetailScreen`

#### `:feature:fido2`
- `Fido2HomeScreen`, `StatusIndicator`, `TransportToggleButton`, `PulseAnimation`
- `AuthenticationPromptScreen`, `AuthenticationPromptContent`, `AuthenticationProgressIndicator`
- `RegistrationPromptScreen`, `RegistrationPromptContent`, `RegistrationProgressIndicator`
- `BiometricPromptComponent`, `EditPairedDeviceScreen`
- `CredentialListScreen`, `CredentialItem`, `DeleteConfirmationDialog`, `CredentialDetailsScreen`

## Impact
- **Developer Experience**: Improved IDE feedback and consistency across UI components.
- **Code Quality**: Reduced technical debt and improved adherence to official Jetpack Compose API design guidelines.
- **Maintainability**: Clearer component signatures and eliminated "magic" suppressions.

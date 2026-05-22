# Interface Contracts: Onboarding Flow

**Feature**: 049-onboarding-flow | **Date**: 2026-05-22

## Internal Navigation Contracts

While there are no external APIs, this feature establishes new internal contracts for navigation and state management.

### App Shell State Contract

The app shell requires a consolidated view of the user's preferences to make routing decisions.

```kotlin
// core/domain or feature/onboarding
data class AppRoutingState(
    val isLoading: Boolean = true,
    val isOnboardingCompleted: Boolean = false,
    val vaultEnabled: Boolean = false,
    val passkeyEnabled: Boolean = false,
    val lastVisitedScreen: String = ""
)
```

### Module Navigation Interfaces

To keep feature modules decoupled, the app shell uses navigation interfaces or explicit route strings exposed by each module.

*   `feature:onboarding`: Exposes `OnboardingDestinations.ONBOARDING_ROUTE`.
*   `feature:vault`: Exposes `VaultDestinations.VAULT_HOME_ROUTE`.
*   `feature:fido2`: Exposes `Fido2Destinations.HOME_ROUTE`.

The app module's `NavHost` defines these composables and wires them together.

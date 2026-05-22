# Research: Onboarding Flow

**Feature**: 049-onboarding-flow | **Date**: 2026-05-22

## R1: Onboarding State Persistence Mechanism

**Decision**: Extend the existing `UserPreferences` Proto DataStore with onboarding-specific fields.

**Rationale**: The project already has a KMP-compatible Proto DataStore at `core/common` using `kotlinx.serialization.protobuf`. Onboarding state (completion flag, feature selection, last-visited screen) is lightweight, non-sensitive user preference data — exactly what this store is designed for. Adding fields to the existing proto message avoids introducing a new persistence mechanism while leveraging the project's encrypted DataStore wrapper.

**Alternatives considered**:
- **Separate DataStore file**: Would add DI complexity and a second file without justification. Rejected per Principle XI (YAGNI/Three-Use Rule — only one consumer exists).
- **SharedPreferences**: Not KMP-compatible and already being migrated away from (see `migrationVersion`/`migrationCompleted` fields). Rejected.
- **SQLCipher/SQLDelight**: Overkill for simple flag/enum storage. Rejected per Principle XI.

## R2: Navigation Architecture Restructuring

**Decision**: Introduce an app-level root `NavHost` in the `app` module that manages top-level routing (onboarding vs. main shell). The main shell composable will host a conditional bottom navigation bar with entries for Vault and/or Passkey Authenticator based on feature selection.

**Rationale**: Currently `MainActivity` directly renders `Fido2RegistrationNavGraph` which owns the bottom navigation. The onboarding feature requires:
1. A routing decision before any feature screen (onboarding vs. main).
2. Conditional inclusion of Vault and/or Authenticator based on feature selection.
3. The existing `Fido2RegistrationNavGraph` remains intact as a nested graph within the Authenticator tab.

This restructuring follows Clean Architecture (Principle III) by placing the routing decision at the app shell level rather than embedding onboarding awareness in feature modules.

**Alternatives considered**:
- **Embed onboarding check inside `Fido2RegistrationNavGraph`**: Violates separation of concerns. The FIDO2 module should not know about onboarding or vault. Rejected.
- **New module for the app shell**: Principle XI (Module Count) explicitly prohibits new modules without measurable isolation/build benefit. The app module is the natural owner of root navigation. Rejected.

## R3: Feature Selection Default Screen Logic

**Decision**: Store the feature selection as two boolean fields (`vaultEnabled`, `passkeyAuthenticatorEnabled`) in `UserPreferences`. Store the default/last-visited screen as a string enum-like field. The routing logic in the app shell reads these values to determine the start destination.

**Rationale**: Two booleans are the simplest representation for 3 valid states (vault-only, passkey-only, both). A string field for the screen identifier allows extensibility without schema changes. The routing logic is:
1. If `onboardingCompleted == false` → navigate to onboarding.
2. If only vault enabled → Vault screen (no bottom bar for feature switching).
3. If only passkey enabled → Passkey Authenticator screen (no bottom bar for feature switching).
4. If both enabled → Bottom navigation bar with both tabs; start on `lastVisitedScreen` (default: Passkey Authenticator).

**Alternatives considered**:
- **Single enum for feature selection**: Would require a third "both" state and doesn't scale. Rejected.
- **Bitfield**: Over-engineered for 2 features. Rejected per Principle XI.

## R4: Onboarding UI Module Placement

**Decision**: Place onboarding UI screens in the `feature:onboarding` module. This is a new feature module.

**Rationale**: The onboarding flow has dedicated UI screens (welcome, vision, feature selection), a ViewModel, and state management. This cleanly separates the onboarding concern from vault/authenticator modules. The `feature:onboarding` module depends only on `core:common` (for DataStore/UserPreferences) and `core:ui` (for shared UI components). It does not depend on vault or fido2 modules. The app module depends on `feature:onboarding` and uses its exposed composable as a navigation destination.

**Alternatives considered**:
- **Place onboarding screens in the app module**: The app module currently has only 2 files (Application + Activity). Adding UI screens, ViewModels, and state would bloat it and violate the Feature-by-module architecture (Principle III). Rejected.
- **Place in `core:ui`**: Core modules should not contain feature-specific UI. Rejected.

**Justification per Principle XI (Module Count)**: A new module is justified here because:
1. Onboarding has a clear domain boundary (its own UI screens, ViewModel, state).
2. It has a distinct dependency graph (only `core:common` and `core:ui`).
3. Build isolation benefit: onboarding changes don't recompile vault/fido2 modules.

## R5: Re-take Onboarding from Settings

**Decision**: The existing application does not currently have a dedicated settings screen. The re-take option will be exposed as a composable function from `feature:onboarding` that the app shell's settings/profile section can invoke. For the initial implementation, a simple settings entry point will be added to the app shell's navigation.

**Rationale**: The spec requires a settings option but the codebase has no settings screen yet. The Fido2 module has `DevelopmentToolsScreen` accessible via bottom nav (dev-only). A proper settings screen is needed regardless, so the onboarding re-take option becomes its first entry. The settings screen lives in the app module since it aggregates options from multiple features.

**Alternatives considered**:
- **Defer re-take to a future settings feature**: The spec lists it as P2 and requires it. Cannot defer. Rejected.
- **Add re-take to `DevelopmentToolsScreen`**: That's a dev-only screen, not user-facing. Rejected.

## R6: Cross-Feature Navigation Mechanism

**Decision**: Use a Material 3 `NavigationBar` (bottom navigation) at the app shell level when the user has selected both features. The bottom bar will show two items: "Vault" and "Authenticator". When only one feature is selected, the bottom bar is hidden and the single feature's screen is the root.

**Rationale**: The existing `Fido2RegistrationNavGraph` already uses a `NavigationBar` with "Authenticator" and "Dev Tools" tabs. The restructured app shell will own the top-level bottom bar, and the existing FIDO2 internal navigation will be nested. This is consistent with M3 patterns and the existing UX language. The bottom bar pattern is already established in the codebase, so users will find it familiar.

**Alternatives considered**:
- **Navigation Drawer**: Less discoverable on mobile. The app has exactly 2 top-level features — bottom bar is ideal for 2-5 items per M3 guidelines. Rejected.
- **Top Tabs**: Less standard for top-level feature switching on Android. Rejected.

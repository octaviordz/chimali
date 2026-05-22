# Implementation Plan: Onboarding Flow

**Branch**: `049-onboarding-flow` | **Date**: 2026-05-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/049-onboarding-flow/spec.md`

## Summary

Add a new onboarding process displayed by default to all new users on initial application run. It introduces Chimali's vision, allows feature selection (Vault, Passkey Authenticator, or both), stores preferences in the Proto DataStore, and restructures the app's root navigation to conditionally route based on this onboarding state and feature selection.

## Technical Context

**Language/Version**: Kotlin 2.x, Android minSdk 28

**Primary Dependencies**: Jetpack Compose, Navigation Compose, Koin, Proto DataStore

**Storage**: `UserPreferences` DataStore (ProtoBuf) for onboarding state and feature selections.

**Testing**: kotlin.test, JUnit 5, Compose UI testing

**Target Platform**: Android Native

**Project Type**: Android Native Application (KMP structure)

**Performance Goals**: Onboarding must not impact the app's cold-start performance target (< 2s). The splash/loading state while reading preferences must be minimal.

**Constraints**: Must adhere to Material Design 3 guidelines. Must respect `UserPreferences` schema versioning (add fields, don't break existing). Must cleanly separate `feature:onboarding` from `feature:vault` and `feature:fido2`.

**Scale/Scope**: Impacts root application navigation. Adds new `feature:onboarding` module. Modifies existing `app` and `core:common` modules.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| Principle I Security First | Onboarding state is not highly sensitive, but is stored in the existing encrypted UserPreferences DataStore. | Pass |
| Principle III Architecture | Requires a new module (`feature:onboarding`) to isolate the flow, and restructuring of `app` module navigation to manage top-level routing. | Pass |
| Principle IV Performance | DataStore reads must be asynchronous and not block the main thread during app launch. | Pass |
| Principle VI Accessibility | Onboarding UI must support TalkBack and dynamic text scaling. | Pass |
| Principle VIII Event Sourcing | Not applicable for simple preference toggles. | Pass |
| Principle XI Pragmatism | The new module is justified (distinct domain, UI, dependencies). Enum-like string storage for routes prevents schema churn. | Pass |

**Gate Resolution**: Passed. The architecture aligns with the principles. A new feature module is justified, and the data model extensions fit the existing patterns without violating security or performance constraints.

## Project Structure

### Documentation (this feature)

```text
specs/049-onboarding-flow/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
app/
├── src/main/kotlin/com/chimali/
│   ├── ChimaliApplication.kt
│   ├── MainActivity.kt
│   └── navigation/
│       ├── AppNavGraph.kt          # New: Root navigation
│       └── AppDestinations.kt      # New: Root destinations

core/
├── common/
│   └── src/main/proto/
│       └── user_preferences.proto  # Updated: New onboarding fields

feature/
├── onboarding/                     # New Module
│   └── src/main/kotlin/com/chimali/feature/onboarding/
│       ├── di/
│       ├── presentation/
│       │   ├── viewmodel/
│       │   ├── ui/
│       │   └── navigation/
│       └── domain/
└── settings/                       # New Module (or sub-package in app)
    └── src/main/kotlin/com/chimali/feature/settings/
        └── ui/
            └── SettingsScreen.kt   # New: Simple entry point to trigger re-take
```

**Structure Decision**: 
1. `core:common`: Update proto schema.
2. `feature:onboarding`: Create a new module for the onboarding UI and logic.
3. `app`: Implement the root `AppNavGraph` to orchestrate navigation based on DataStore state, decoupling feature modules.
4. `feature:settings`: Create a minimal settings module to host the "Re-take Onboarding" entry point, accessible from the app shell.

## Complexity Tracking

No constitution violations detected. Complexity is managed by extending the existing Proto DataStore rather than introducing a new persistence mechanism.

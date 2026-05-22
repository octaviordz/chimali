---

description: "Task list for Onboarding Flow feature implementation"
---

# Tasks: Onboarding Flow

**Input**: Design documents from `/specs/049-onboarding-flow/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: TDD is mandatory for this project. Tests are included for ViewModels and Navigation state.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Update `UserPreferences` proto schema in `core/common/src/main/proto/user_preferences.proto`
- [ ] T002 Generate proto files (run `./gradlew :core:common:generateProto`)
- [ ] T003 Create `feature:onboarding` module structure and `feature/onboarding/build.gradle.kts`
- [ ] T004 Create `feature:settings` module structure and `feature/settings/build.gradle.kts`
- [ ] T005 Add new modules to `settings.gradle.kts` and app `app/build.gradle.kts`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T006 Create AppRoutingState model in `app/src/main/kotlin/com/chimali/navigation/AppRoutingState.kt`
- [ ] T007 Setup Koin DI module for onboarding in `feature/onboarding/src/main/kotlin/com/chimali/feature/onboarding/di/OnboardingModule.kt`
- [ ] T008 Setup Koin DI module for settings in `feature/settings/src/main/kotlin/com/chimali/feature/settings/di/SettingsModule.kt`
- [ ] T009 Update `ChimaliApplication.kt` to include new Koin modules

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - First-Run Onboarding Experience (Priority: P1) 🎯 MVP

**Goal**: New users see an onboarding flow introducing the vision before reaching any main application content.

**Independent Test**: Install app fresh. App routes to onboarding intro screens, then allows completion to reach main shell.

### Tests for User Story 1

- [ ] T010 [P] [US1] Unit test for OnboardingViewModel initial state in `feature/onboarding/src/test/kotlin/com/chimali/feature/onboarding/presentation/viewmodel/OnboardingViewModelTest.kt`

### Implementation for User Story 1

- [ ] T011 [P] [US1] Create OnboardingDestinations in `feature/onboarding/src/main/kotlin/com/chimali/feature/onboarding/presentation/navigation/OnboardingDestinations.kt`
- [ ] T012 [P] [US1] Implement Vision/Intro Screens in `feature/onboarding/src/main/kotlin/com/chimali/feature/onboarding/presentation/ui/IntroScreen.kt`
- [ ] T013 [US1] Implement OnboardingViewModel in `feature/onboarding/src/main/kotlin/com/chimali/feature/onboarding/presentation/viewmodel/OnboardingViewModel.kt`
- [ ] T014 [US1] Implement OnboardingNavGraph in `feature/onboarding/src/main/kotlin/com/chimali/feature/onboarding/presentation/navigation/OnboardingNavGraph.kt`
- [ ] T015 [US1] Implement AppDestinations in `app/src/main/kotlin/com/chimali/navigation/AppDestinations.kt`
- [ ] T016 [US1] Implement AppNavGraph in `app/src/main/kotlin/com/chimali/navigation/AppNavGraph.kt` to route based on `isOnboardingCompleted`
- [ ] T017 [US1] Update `MainActivity.kt` to setContent to `AppNavGraph` instead of Fido2RegistrationNavGraph

**Checkpoint**: At this point, User Story 1 should be fully functional.

---

## Phase 4: User Story 2 - Feature Selection and Default Screen (Priority: P1)

**Goal**: Allow user to select Vault, Passkey, or both. Use selection to determine the default screen on subsequent launches.

**Independent Test**: Complete onboarding selecting different combinations. Verify app launch routes to correct default screen.

### Tests for User Story 2

- [ ] T018 [P] [US2] Unit test for Feature Selection state in `feature/onboarding/src/test/kotlin/com/chimali/feature/onboarding/presentation/viewmodel/OnboardingViewModelTest.kt`

### Implementation for User Story 2

- [ ] T019 [US2] Implement Feature Selection Screen in `feature/onboarding/src/main/kotlin/com/chimali/feature/onboarding/presentation/ui/FeatureSelectionScreen.kt`
- [ ] T020 [US2] Update `OnboardingViewModel.kt` to save user feature selections to DataStore
- [ ] T021 [US2] Update `AppNavGraph.kt` routing logic to use `vaultEnabled` and `passkeyEnabled` for setting start destination

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently.

---

## Phase 5: User Story 3 - Cross-Feature Navigation (Priority: P2)

**Goal**: For users selecting both features, provide a bottom navigation bar to switch between Vault and Passkey, and persist the last-visited screen.

**Independent Test**: Complete onboarding with both features. Verify bottom bar exists, switch tabs, kill app, verify app restarts on the last visited tab.

### Tests for User Story 3

- [ ] T022 [US3] Unit test for last-visited persistence logic in app shell (mock DataStore) in `app/src/test/kotlin/com/chimali/navigation/AppRoutingTest.kt`

### Implementation for User Story 3

- [ ] T023 [US3] Extract existing bottom bar from `Fido2RegistrationNavGraph.kt` (since it will now be managed by the app shell).
- [ ] T024 [US3] Implement dynamic BottomNavigationBar in `AppNavGraph.kt` (shows only if both features enabled).
- [ ] T025 [US3] Implement logic in `AppNavGraph.kt` to save `lastVisitedScreen` to DataStore on route change.

**Checkpoint**: All user stories should now be independently functional.

---

## Phase 6: User Story 4 - Re-take Onboarding from Settings (Priority: P2)

**Goal**: Allow users to restart the onboarding flow from the settings to change their feature preferences.

**Independent Test**: Open settings, trigger re-take, complete flow with new selection, verify new defaults take effect.

### Tests for User Story 4

- [ ] T026 [P] [US4] Compose UI test for Settings Screen interaction in `feature/settings/src/androidTest/kotlin/com/chimali/feature/settings/ui/SettingsScreenTest.kt`

### Implementation for User Story 4

- [ ] T027 [US4] Implement `SettingsScreen.kt` in `feature/settings/src/main/kotlin/com/chimali/feature/settings/ui/SettingsScreen.kt`
- [ ] T028 [US4] Add Settings tab to `AppNavGraph.kt` (if applicable) or add an entry point from the main screens.
- [ ] T029 [US4] Update `AppNavGraph.kt` to support deep linking/navigating to the `OnboardingNavGraph` from Settings.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T030 Add loading/splash screen logic in `AppNavGraph` while reading DataStore to prevent brief flash of incorrect route
- [ ] T031 Run static analysis and formatting (`./gradlew detekt ktlintFormat`)
- [ ] T032 Verify all tests pass (`./gradlew testDebugUnitTest connectedDebugAndroidTest`)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can proceed sequentially in priority order (US1 → US2 → US3 → US4)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **US1 (P1)**: Can start after Foundational
- **US2 (P1)**: Extends US1's onboarding graph and AppNavGraph
- **US3 (P2)**: Extends US2's AppNavGraph
- **US4 (P2)**: Independent module, relies on AppNavGraph routing capability

### Parallel Opportunities

- UI screens (T012, T019) can be built in parallel with ViewModels (T010, T013).
- Settings module (T026, T027) can be built completely in parallel with the rest of the app until wiring (T028).

---

## Implementation Strategy

### MVP First (User Story 1 & 2)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL)
3. Complete Phase 3: US1 (Intro screens & routing)
4. Complete Phase 4: US2 (Feature selection & routing logic)
5. **STOP and VALIDATE**: Verify end-to-end first-run experience.
6. Complete remaining phases for dual-feature users and settings.

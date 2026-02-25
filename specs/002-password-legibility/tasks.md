# Tasks: Password Legibility and Confusion Prevention

**Input**: Design documents from `/specs/002-password-legibility/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 [P] Configure Atkinson Hyperlegible Font in `feature/vault/src/main/res/font/atkinson_hyperlegible.xml`
- [ ] T002 [P] Configure JetBrains Mono Font in `feature/vault/src/main/res/font/jetbrains_mono.xml`
- [ ] T003 [P] Add Google Fonts dependency to `feature/vault/build.gradle.kts`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

- [ ] T004 [P] Define `LegibilitySettings` and `LegibilityFont` enum in `feature/vault/src/main/java/com/chimali/feature/vault/ui/model/LegibilitySettings.kt`
- [ ] T005 [P] Define `LegibilityColors` with orange semantic tokens in `feature/vault/src/main/java/com/chimali/feature/vault/ui/theme/LegibilityColors.kt`
- [ ] T006 [P] Implement `AtkinsonFontFamily` and `JetBrainsMonoFontFamily` definitions in `feature/vault/src/main/java/com/chimali/feature/vault/ui/theme/Type.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Distinguishing characters (Priority: P1) 🎯 MVP

**Goal**: Ensure ambiguous characters like 'O/0' and 'I/l/1' are unmistakable.

**Independent Test**: Display "Il1O0" in the app and verify each character is visually distinct using the legibility font.

- [ ] T007 [US1] Implement `LegibleSecretText` Composable foundation with font switching logic in `feature/vault/src/main/java/com/chimali/feature/vault/ui/components/LegibleSecretText.kt`. Ensure proper wrapping for extreme password lengths.
- [ ] T008 [US1] Update `CredentialDetailScreen.kt` to replace standard Text with `LegibleSecretText` for password display (depends on T007)

---

## Phase 4: User Story 2 - Character Type Identification (Priority: P2)

**Goal**: Highlight digits (numbers) in orange within the secret text.

**Independent Test**: Input "Pass123" and verify "123" is rendered in orange with bold weight.

- [x] T009 [US2] Implement regex-based `AnnotatedString` builder for digit highlighting in `feature/vault/src/main/java/com/chimali/feature/vault/ui/components/LegibleSecretText.kt`
- [x] T010 [US2] Add colorblind-friendly support (e.g., subtle weighting or pattern) to digit highlighting in `LegibleSecretText.kt` (depends on T009)
- [ ] T016 [US2] Implement symbol character highlighting with distinct visual treatment in `LegibleSecretText.kt` (depends on T009)
- [ ] T017 [US2] Implement uppercase letter highlighting with distinct visual treatment in `LegibleSecretText.kt` (depends on T009)
- [ ] T018 [US2] Implement lowercase letter highlighting with distinct visual treatment in `LegibleSecretText.kt` (depends on T009)

---

## Phase 5: Edge Case Handling

**Purpose**: Handle non-standard symbols and extreme password lengths

- [ ] T019 [P] Implement Unicode symbol fallback handling in `LegibleSecretText.kt` for obscure characters
- [ ] T020 [P] Implement proper text wrapping and highlighting preservation for passwords 100+ characters in `LegibleSecretText.kt`

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Final validation and accessibility checks

- [ ] T011 [P] Perform WCAG 2.1 AA contrast check for orange highlighting in `LegibilityColors.kt`
- [ ] T012 [P] Verify Screen Reader (TalkBack) compatibility for `LegibleSecretText`
- [ ] T013 [P] Verify UI integrity with **Dynamic Text Scaling** (Principle VI)
- [ ] T014 [P] Verify visibility in **High-Contrast Mode** (Principle VI)
- [ ] T015 Update `docs/quickstart.md` with instructions for testing legibility mode

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup.
- **User Stories (Phase 3+)**: All depend on Foundational completion.
- **Polish (Final Phase)**: Depends on all user stories.

### Parallel Opportunities

- T001, T002, T003 can run in parallel.
- T004, T005, T006 can run in parallel.
- T011 and T012 can run in parallel.

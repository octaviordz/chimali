# Tasks: Standardize Preview Visibility

**Input**: Design documents from `/specs/027-enforce-preview-public/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [X] T001 Initialize implementation tracking and prepare for refactoring

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T002 Verify current lint status by running `tools/local-ci.ps1` to establish a baseline

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Clean Public API Surface (Priority: P1) 🎯 MVP

**Goal**: Restrict visibility of Compose Previews to `private` and remove technical debt (rule bypasses and TODOs).

**Independent Test**: Verify that no UI preview components are accessible or visible from outside their respective modules or parent components.

### Implementation for User Story 1

- [X] T003 [P] [US1] Refactor visibility to `private` and remove `@Suppress("PreviewPublic")` in `feature/vault/src/main/java/com/chimali/feature/vault/ui/CreditCardDetailScreen.kt`
- [X] T004 [P] [US1] Refactor visibility to `private` and remove `@Suppress("PreviewPublic")` in `feature/vault/src/main/java/com/chimali/feature/vault/ui/CreditCardEntryScreen.kt`
- [X] T005 [P] [US1] Refactor visibility to `private` and remove `@Suppress("PreviewPublic")` in `feature/vault/src/main/java/com/chimali/feature/vault/ui/LabelManagerScreen.kt`
- [X] T006 [P] [US1] Refactor visibility to `private` and remove `@Suppress("PreviewPublic")` in `feature/vault/src/main/java/com/chimali/feature/vault/ui/PasswordDetailScreen.kt`
- [X] T007 [P] [US1] Refactor visibility to `private` and remove `@Suppress("PreviewPublic")` in `feature/vault/src/main/java/com/chimali/feature/vault/ui/PasswordEntryScreen.kt`
- [X] T008 [P] [US1] Refactor visibility to `private` and remove `@Suppress("PreviewPublic")` in `feature/vault/src/main/java/com/chimali/feature/vault/ui/SecureNoteDetailScreen.kt`
- [X] T009 [P] [US1] Refactor visibility to `private` and remove `@Suppress("PreviewPublic")` in `feature/vault/src/main/java/com/chimali/feature/vault/ui/SecureNoteEntryScreen.kt`
- [X] T010 [P] [US1] Refactor visibility to `private`, remove `@Suppress("PreviewPublic")`, `@Suppress("ForbiddenComment")`, and associated TODO in `feature/vault/src/main/java/com/chimali/feature/vault/ui/VaultListScreen.kt`

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently. All targeted Previews should be `private` with no lint suppressions.

---

## Phase 4: User Story 2 - Automated Quality Enforcement (Priority: P2)

**Goal**: Ensure the system automatically prevents the introduction of public UI previews through static analysis.

**Independent Test**: Run the automated build system and verify that it passes with zero "PreviewPublic" violations.

### Implementation for User Story 2

- [X] T011 [US2] Run `tools/local-ci.ps1` and verify that all "PreviewPublic" and "ForbiddenComment" violations are resolved
- [X] T012 [US2] Manually introduce a temporary public preview in any file and verify that `local-ci.ps1` correctly flags it (then revert)

**Checkpoint**: At this point, User Stories 1 AND 2 are complete. Automated quality checks are enforcing the new standard.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [X] T013 [P] Verify all modified components render correctly in the Android Studio Preview pane
- [X] T014 [P] Update project documentation if any new visibility standards need to be explicitly mentioned
- [X] T015 [P] Create and update project changelogs

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### Parallel Opportunities

- All tasks marked [P] can run in parallel within their respective phases.
- Once the Foundational phase (T002) is complete, all tasks T003-T010 can be executed in parallel.

---

## Parallel Example: User Story 1

```bash
# Launch multiple refactoring tasks together:
Task: "Refactor visibility in CreditCardDetailScreen.kt"
Task: "Refactor visibility in PasswordDetailScreen.kt"
Task: "Refactor visibility in VaultListScreen.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (Baseline check)
3. Complete Phase 3: User Story 1 (Refactoring)
4. **STOP and VALIDATE**: Verify previews in Android Studio
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Clean API achieved (MVP!)
3. Add User Story 2 → Test independently → Automated enforcement achieved

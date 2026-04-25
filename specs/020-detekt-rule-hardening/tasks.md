# Tasks: Detekt Rule Hardening

**Input**: Design documents from `specs/020-detekt-rule-hardening/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Verify project is on branch `020-detekt-rule-hardening`
- [ ] T002 [P] Sync Gradle to ensure Detekt is ready for analysis

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core configuration that MUST be complete before refactoring begins

- [ ] T003 Harden `config/detekt/detekt.yml` by activating WildcardImport, UnusedImports, NewLineAtEndOfFile, UnsafeCallOnNullableType, LateinitUsage, and EmptyDefaultConstructor
- [ ] T004 [P] Remove all rule-specific exclusions for the target rules in `config/detekt/detekt.yml`
- [ ] T005 [P] Configure global `excludes` in `config/detekt/detekt.yml` to cover all `**/build/**` and `**/build/generated/**` paths [US2]
- [ ] T006 Evaluate `.kts` file violations and apply global exclusion in `config/detekt/detekt.yml` if technical debt is excessive [US1]

**Checkpoint**: Foundation ready - rules are active and violations will be reported by the build system.

---

## Phase 3: User Story 1 - Automated Quality Gate Enforcement (Priority: P1) 🎯 MVP

**Goal**: Refactor the codebase to comply with the newly hardened rules.

**Independent Test**: Introduce a violation (e.g., a wildcard import) in any production file and run `./gradlew detekt`. The build MUST fail.

### Implementation for User Story 1

- [ ] T007 [P] [US1] Refactor `core:security` tests to remove `lateinit` and wildcard imports in `core/security/src/test/kotlin/`
- [ ] T008 [P] [US1] Refactor `feature:fido2` tests and production code to resolve 130+ violations (Lateinit, Wildcards, !! calls) in `feature/fido2/`
- [ ] T009 [P] [US1] Refactor `feature:vault` to resolve legacy violations (EmptyDefaultConstructor, UnusedImports, NewLineAtEndOfFile, Wildcards) in `feature/vault/`
- [ ] T010 [P] [US1] Resolve wildcard imports in `app/src/main/kotlin/` (mostly Compose-related)
- [ ] T011 [P] [US1] Resolve remaining violations in `core:common`, `core:database`, and `core:bluetooth` modules

**Checkpoint**: At this point, User Story 1 should be fully functional and the codebase should pass all detekt checks.

---

## Phase 4: User Story 2 - Generated Code Compatibility (Priority: P2)

**Goal**: Ensure generated code is correctly ignored by the quality gates.

**Independent Test**: Verify that detekt passes even when generated files (e.g., Room or BuildKonfig) contain violations.

### Implementation for User Story 2

- [ ] T012 [US2] Validate that global exclusions in `config/detekt/detekt.yml` correctly ignore `**/build/generated/**`
- [ ] T013 [US2] Manually verify a specific generated file (e.g., `BuildKonfig.kt` or Room DAO) is not being analyzed

**Checkpoint**: Generated code is reliably excluded from static analysis.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T014 [P] Update `specs/020-detekt-rule-hardening/walkthrough.md` with implementation results
- [ ] T015 Run Local CI pipeline via `tools/local-ci.ps1`
- [ ] T016 [US1] Verify SC-003: Introduce violation and confirm build failure within 30 seconds

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup. MUST complete before refactoring begins.
- **User Story 1 (Phase 3)**: Depends on Phase 2.
- **User Story 2 (Phase 4)**: Can run in parallel with User Story 1 or after.
- **Polish (Final Phase)**: Depends on all user stories being complete.

### Parallel Opportunities

- T007, T008, T009, T010 can run in parallel across different feature modules.
- Verification tasks (T012, T013) can run as soon as Phase 2 is done.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 & 2 to enable the rules.
2. Focus on `feature:fido2` as it has the highest concentration of violations.
3. Once `fido2` is clean, move to `vault` and `core:security`.
4. Run `./gradlew detekt` frequently to verify progress.

### Parallel Team Strategy

- Different developers can take different modules (`core`, `feature:fido2`, `feature:vault`) to refactor in parallel.

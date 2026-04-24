# Tasks: Detekt Quality Enforcement: MagicNumber

**Input**: Design documents from `/specs/018-detekt-magic-number/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md

**Organization**: Tasks are grouped by user story to ensure full project-wide compliance through incremental refactoring.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different modules/files)
- **[Story]**: US1 (Enforcement), US2 (Compliance Verification)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Configure Detekt with tuned settings before removing exclusions.

- [ ] T001 Run `./gradlew detekt` to establish a clean baseline with current exclusions
- [ ] T002 Update `config/detekt/detekt.yml` with tuned settings: `ignoreAnnotation: true`, `ignoreEnums: true`, `ignoreRanges: true`, and expand `ignoreNumbers` to include common SDK versions (17, 21, 24, 30, 31, 33, 34, 35)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Activate the rule for all source sets.

- [ ] T003 Remove the specified `excludes` list from `MagicNumber` rule in `config/detekt/detekt.yml`, while adding exclusions for `**/build/generated/**` and `**/*Generated.kt`
- [ ] T004 Run `./gradlew detekt --continue` and capture violations to `artifacts/magic_number_violations.txt` for systematic resolution

---

## Phase 3: User Story 1 - Enforce MagicNumber Project-Wide (Priority: P1) 🎯 MVP

**Goal**: Refactor all codebases to comply with the magic number standards.

**Independent Test**: Running `detekt` on a specific module passes successfully.

### Implementation for User Story 1

- [ ] T005 [P] [US1] Refactor magic numbers in `app` module Kotlin files in `app/src/`
- [ ] T006 [P] [US1] Refactor magic numbers in `core:database` module Kotlin files in `core/database/src/`
- [ ] T007 [P] [US1] Refactor magic numbers in `core:security` module Kotlin files in `core/security/src/`
- [ ] T008 [P] [US1] Refactor magic numbers in `core:ui` module Kotlin files in `core/ui/src/`
- [ ] T009 [P] [US1] Refactor magic numbers in `feature:vault` module Kotlin files in `feature/vault/src/`
- [ ] T010 [P] [US1] Refactor magic numbers in all `build.gradle.kts` and `settings.gradle.kts` files across the project
- [ ] T011 [P] [US1] Refactor magic numbers in all `test` and `androidTest` source sets across all modules (e.g., `**/src/test/**`)

**Checkpoint**: Individual modules and test suites should now be compliant.

---

## Phase 4: User Story 2 - Automated Compliance (Priority: P2)

**Goal**: Ensure project-wide compliance and automated gate verification.

**Independent Test**: Full project `./gradlew detekt` passes with zero MagicNumber violations.

### Implementation for User Story 2

- [ ] T012 [US2] Run project-wide `./gradlew detekt` to verify 100% compliance
- [ ] T013 [US2] Fix any remaining edge-case violations discovered in the final project-wide scan

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T014 [P] Update `specs/018-detekt-magic-number/quickstart.md` with any newly established patterns discovered during refactoring
- [ ] T015 Run Local CI pipeline via `tools/local-ci.ps1` to ensure no regressions in tests or formatting

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately.
- **Foundational (Phase 2)**: Depends on Setup (Phase 1) - BLOCKS all user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational (Phase 2).
- **User Story 2 (Phase 4)**: Depends on User Story 1 (Phase 3).
- **Polish (Phase 5)**: Depends on all user stories being complete.

### User Story Dependencies

- **User Story 1 (P1)**: Independent after Phase 2.
- **User Story 2 (P2)**: Depends on completion of US1.

### Parallel Opportunities

- T005 through T011 can run in parallel (different modules and directories).
- T014 can run in parallel with US2 verification.

---

## Parallel Example: User Story 1

```bash
# Refactor different modules in parallel:
Task: "T005 Refactor magic numbers in app module"
Task: "T009 Refactor magic numbers in feature:vault module"
```

---

## Implementation Strategy

### MVP First (Compliance)

1. Complete Phase 1: Setup (T001-T002)
2. Complete Phase 2: Foundational (T003-T004)
3. Complete Phase 3: User Story 1 (T005-T011)
4. **STOP and VALIDATE**: Run `detekt` on individual modules.
5. Proceed to US2 for final verification.

---

## Notes

- Use `private const val` for file-local magic numbers.
- Use named arguments (e.g., `delay(timeMillis = 500)`) where appropriate.
- Generated code MUST remain excluded.

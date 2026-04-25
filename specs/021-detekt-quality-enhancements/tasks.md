# Tasks: Detekt Quality Enhancements

**Input**: Design documents from `/specs/021-detekt-quality-enhancements/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, quickstart.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Create backup of `config/detekt/detekt.yml`
- [ ] T002 Verify `tools/local-ci.ps1` is operational for baseline checks

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

- [ ] T003 Ensure `detekt` Gradle plugin is configured to fail on any weighted issue (`maxIssues: 0`) in `config/detekt/detekt.yml`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Prevent Standard Output (Priority: P1) 🎯 MVP

**Goal**: Fail the build if `println` or `print` is used in production modules.

**Independent Test**: Add a `println("test")` to a production file and verify `./gradlew detekt` fails with `ForbiddenMethodCall`.

### Implementation for User Story 1

- [ ] T004 [US1] Enable `ForbiddenMethodCall` in `config/detekt/detekt.yml` for `kotlin.io.print` and `kotlin.io.println`
- [ ] T005 [P] [US1] Configure `excludes` for `ForbiddenMethodCall` in `config/detekt/detekt.yml` to ignore test source sets (`**/test/**`, `**/androidTest/**`, etc.)
- [ ] T006 [US1] Verify US1 by running `./gradlew detekt` on a file with `println`

**Checkpoint**: At this point, standard output is prohibited in production code.

---

## Phase 4: User Story 2 - Idiomatic Scope Functions (Priority: P2)

**Goal**: Flag unnecessary or non-idiomatic use of scope functions like `let`.

**Independent Test**: Add a redundant `.let { it.toString() }` to a non-nullable string and verify Detekt flags it.

### Implementation for User Story 2

- [ ] T007 [US2] Enable `UnnecessaryLet` in `config/detekt/detekt.yml`
- [ ] T008 [US2] Enable `UseLet` in `config/detekt/detekt.yml`
- [ ] T009 [US2] Verify US2 by running `./gradlew detekt` on a file with redundant `let`

**Checkpoint**: At this point, the codebase enforces idiomatic scope function usage.

---

## Phase 5: User Story 3 - String Literal Duplication (Priority: P3)

**Goal**: Identify duplicated string literals to encourage extraction into constants.

**Independent Test**: Use the same string literal 3 times in one file and verify Detekt flags `StringLiteralDuplication`.

### Implementation for User Story 3

- [ ] T010 [US3] Enable `StringLiteralDuplication` in `config/detekt/detekt.yml`
- [ ] T011 [US3] Configure `threshold: 3` and `excludeStringsWithLessThan5Characters: true` for `StringLiteralDuplication` in `config/detekt/detekt.yml`
- [ ] T012 [US3] Verify US3 by running `./gradlew detekt` on a file with duplicated strings

**Checkpoint**: All user stories should now be independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T013 Run project-wide Detekt and fix any new violations discovered to reach zero-issue baseline
- [ ] T014 Update `specs/021-detekt-quality-enhancements/walkthrough.md` with implementation results
- [ ] T015 Run Local CI pipeline via `tools/local-ci.ps1`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
- **Polish (Final Phase)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Independent after Phase 2
- **User Story 2 (P2)**: Independent after Phase 2
- **User Story 3 (P3)**: Independent after Phase 2

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Verify logging discipline in production modules.

### Incremental Delivery

1. Foundation ready
2. Add Logging Discipline → Test → Deliver
3. Add Idiomatic Scoping → Test → Deliver
4. Add String Management → Test → Deliver

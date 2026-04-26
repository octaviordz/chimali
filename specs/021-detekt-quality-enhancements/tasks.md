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

- [x] T001 Create backup of `config/detekt/detekt.yml`
- [x] T002 Verify `tools/local-ci.ps1` is operational for baseline checks

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

- [x] T003 Ensure `detekt` Gradle plugin is configured to fail on any weighted issue (`maxIssues: 0`) in `config/detekt/detekt.yml`
- [x] T027 [P] Remove all module-level `excludes` for `feature/vault` across ALL rules in `config/detekt/detekt.yml`
- [x] T028 [P] Remove all module-level `excludes` for `feature/fido2` across ALL rules in `config/detekt/detekt.yml`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Prevent Standard Output (Priority: P1) 🎯 MVP

**Goal**: Fail the build if `println` or `print` is used in production modules.

**Independent Test**: Add a `println("test")` to a production file and verify `./gradlew detekt` fails with `ForbiddenMethodCall`.

### Implementation for User Story 1

- [x] T004 [US1] Enable `ForbiddenMethodCall` in `config/detekt/detekt.yml` for `kotlin.io.print` and `kotlin.io.println`
- [x] T005 [P] [US1] Configure `excludes` for `ForbiddenMethodCall` in `config/detekt/detekt.yml` to ignore test source sets (`**/test/**`, `**/androidTest/**`, etc.)
- [x] T006 [US1] Verify US1 by running `./gradlew detekt` on a file with `println`

**Checkpoint**: At this point, standard output is prohibited in production code.

---

## Phase 4: User Story 2 - Idiomatic Scope Functions (Priority: P2)

**Goal**: Flag unnecessary or non-idiomatic use of scope functions like `let`.

**Independent Test**: Add a redundant `.let { it.toString() }` to a non-nullable string and verify Detekt flags it.

### Implementation for User Story 2

- [x] T007 [US2] Enable `UnnecessaryLet` in `config/detekt/detekt.yml` with test exclusions
- [x] T008 [US2] Enable `UseLet` in `config/detekt/detekt.yml` with test exclusions
- [x] T009 [US2] Verify US2 by running `./gradlew detekt` on a file with redundant `let`

**Checkpoint**: At this point, the codebase enforces idiomatic scope function usage.

---

## Phase 5: User Story 3 - String Literal Duplication (Priority: P3)

**Goal**: Identify duplicated string literals to encourage extraction into constants.

**Independent Test**: Use the same string literal 5 times in one file and verify Detekt flags `StringLiteralDuplication`.

### Implementation for User Story 3

- [x] T010 [US3] Enable `StringLiteralDuplication` in `config/detekt/detekt.yml`
- [x] T011 [US3] Configure `threshold: 5` and `excludeStringsWithLessThan5Characters: true` for `StringLiteralDuplication` in `config/detekt/detekt.yml`
- [x] T012 [US3] Verify US3 by running `./gradlew detekt` on a file with duplicated strings

**Checkpoint**: All user stories should now be independently functional.

---

## Phase 6: User Story 4 - Harden Structured Logging (Priority: P1)

**Goal**: Banning direct `android.util.Log` usage to enforce structured Kermit logging.

**Independent Test**: Add `android.util.Log.d("tag", "msg")` to a production file and verify Detekt failure.

### Implementation for User Story 4

- [x] T016 [US4] Update `ForbiddenMethodCall` in `config/detekt/detekt.yml` to include all `android.util.Log.*` variants
- [x] T017 [US4] Verify US4 by running `./gradlew detekt` on a file with `Log.e(...)`

---

## Phase 7: User Story 5 - Prevent API Decay & Collection Bugs (Priority: P2)

**Goal**: Fail build on `@Deprecated` usage and unsafe collection downcasts.

**Independent Test**: Use a deprecated method or `as MutableList` on a `List` and verify Detekt violations.

### Implementation for User Story 5

- [x] T018 [P] [US5] Enable `Deprecation` rule with test source set exclusions in `config/detekt/detekt.yml`
- [x] T019 [P] [US5] Enable `DontDowncastCollectionTypes` rule in `config/detekt/detekt.yml` with test exclusions

---

## Phase 8: User Story 6 - Optimize Collection Processing (Priority: P2)

**Goal**: Enforce Sequence usage for long chains (Constitution §IV).

**Independent Test**: Create a chain of 3+ collection operations and verify `CouldBeSequence` suggestion.

### Implementation for User Story 6

- [x] T020 [US6] Enable `CouldBeSequence` rule with `threshold: 3` and test exclusions in `config/detekt/detekt.yml`

---

## Phase 9: User Story 7 - Maintainable Imports & Strings (Priority: P3)

**Goal**: Eliminate internal wildcard imports and encourage Raw Strings for escaped text.

**Independent Test**: Use wildcard for `com.chimali.*` and string with 5+ escapes; verify violations.

### Implementation for User Story 7

- [x] T021 [US7] Remove internal wildcard exception for `com.chimali.fido2.presentation.ui.components.*` in `config/detekt/detekt.yml`
- [x] T022 [US7] Enable `StringShouldBeRawString` with `maxEscapedCharacterCount: 4` in `config/detekt/detekt.yml`

---

## Phase 10: Addendum - Zero-Exclusion Quality Gate (Priority: P1)

**Goal**: Enforce all existing complexity and style rules on feature modules.

**Independent Test**: Verify no `excludes` for `feature/vault` or `feature:fido2` remain for rules like `CognitiveComplexMethod`.

### Implementation for US8

- [x] T029 [US8] Audit and remove any remaining path-based exclusions for feature modules in `config/detekt/detekt.yml`
- [x] T030 [US8] Run `.\tools\local-ci.ps1` and perform targeted `@Suppress` for legitimate existing violations in `feature:vault` and `feature:fido2`

---

## Phase 11: Final Polish & Remediation

**Purpose**: Fix violations surfaced by new rules and update documentation.

- [x] T023 [P] Fix the internal wildcard import in `feature:fido2` to satisfy T021
- [x] T024 [P] Resolve any new `android.util.Log` or `CouldBeSequence` violations in `feature:vault` and `feature:fido2`
- [x] T025 Update `walkthrough.md` with implementation results for new quality gates in `specs/021-detekt-quality-enhancements/walkthrough.md`
- [x] T026 Run Local CI pipeline via `tools/local-ci.ps1`

---

## Original Polish Tasks (Preserved for History)

- [x] T013 Run project-wide Detekt and fix any new violations discovered to reach zero-issue baseline
- [x] T014 Update `specs/021-detekt-quality-enhancements/walkthrough.md` with implementation results
- [x] T015 Run Local CI pipeline via `tools/local-ci.ps1`

---

## Phase 12: Final Implementation and Verification (Consolidated)

**Purpose**: Completed remediation work and final verification as per latest execution.

- [x] T001 Verify existing Detekt configuration in `config/detekt/detekt.yml`
- [x] T002 Create backup of `config/detekt/detekt.yml`
- [x] T003 Update `tools/local-ci.ps1` to correctly report Detekt violations across all modules
- [x] T004 [US1] Enable `ForbiddenMethodCall` in `config/detekt/detekt.yml` for `kotlin.io.print` and `kotlin.io.println`
- [x] T005 [P] [US1] Configure `excludes` for `ForbiddenMethodCall` in `config/detekt/detekt.yml` to ignore test source sets
- [x] T006 [US4] Enable `ForbiddenImport` in `config/detekt/detekt.yml` for `android.util.Log`
- [x] T007 [US2] Enable `RedundantLet` in `config/detekt/detekt.yml`
- [x] T008 [US3] Configure `StringLiteralDuplication` in `config/detekt/detekt.yml` with `threshold: 5`
- [x] T009 [US5] Enable `Deprecation` and `UnsafeCallOnNullableType` (for collection safety) in `config/detekt/detekt.yml`
- [x] T010 [US6] Enable `CouldBeSequence` in `config/detekt/detekt.yml` with `threshold: 3`
- [x] T011 [US7] Enable `WildcardImport` and `MaxLineLength` in `config/detekt/detekt.yml`
- [x] T012 [US8] Remove all path-based exclusions for `feature:vault` and `feature:fido2` in `config/detekt/detekt.yml`
- [x] T013 [US1/US4] Refactor `println` and `android.util.Log` usage to Kermit `Logger` in all modules
- [x] T014 [US1/US4] Verify clean pass for logging rules via `./gradlew detekt`
- [x] T015 [US2] Refactor redundant `.let` usage flagged by Detekt
- [x] T016 [US2] Verify clean pass for scope function rules
- [x] T017 [US3] Extract duplicated string literals into constants in `feature:fido2` and `feature:vault`
- [x] T018 [US3] Verify clean pass for string duplication rules
- [x] T019 [US6] Convert long collection processing chains to Sequences in `feature:vault`
- [x] T020 [US6] Verify clean pass for performance rules
- [x] T021 [US8] Suppress `TooGenericExceptionCaught` in `feature:fido2` domain models and viewmodels
- [x] T022 [US11] Suppress `ForbiddenComment` for legitimate `TODO` and `FIXME` across project
- [x] T023 [P] Verify final project health by running `.\tools\local-ci.ps1`
- [x] T024 [P] Run `.\tools\local-ci.ps1` and ensure all checks pass

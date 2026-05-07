---

description: "Task list template for feature implementation"
---

# Tasks: Detekt Rules Remediation

**Input**: Design documents from `specs/033-detekt-remediation/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: No new tests requested, but ensuring all existing tests pass via `local-ci.ps1` is critical.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1)
- Include exact file paths in descriptions

## Path Conventions

- Paths are localized to `feature/fido2/` within the Chimali repository.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Execute `tools\local-ci.ps1` to verify current project stability before beginning refactoring.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

*(No foundational blocking tasks required for this refactoring)*

**Checkpoint**: Foundation ready - user story implementation can now begin.

---

## Phase 3: User Story 1 - Technical Debt Reduction (Priority: P1) 🎯 MVP

**Goal**: Eliminate the top 4 suppressed code smells in the Detekt baseline file (MagicNumber, ClassNaming, BooleanPropertyNaming, SuspendFunWithFlowReturnType).

**Independent Test**: The project compiles successfully, unit tests pass without modifying business logic, and the targeted rules can be safely removed from the `detekt-baseline-main.xml`.

### Implementation for User Story 1

- [ ] T002 [US1] Resolve `SuspendFunWithFlowReturnType` by removing the redundant `suspend` keyword from functions returning `Flow` in `feature/fido2/`.
- [ ] T003 [US1] Resolve `BooleanPropertyNaming` violations by applying `is` or `has` prefixes to boolean properties across `feature/fido2/`.
- [ ] T004 [US1] Resolve `ClassNaming` violations by renaming objects, classes, or interfaces to strict PascalCase in `feature/fido2/`.
- [ ] T005 [US1] Resolve `MagicNumber` violations by extracting hardcoded numeric literals into appropriate `private const val` or `companion object const val` properties across `feature/fido2/`.

**Checkpoint**: At this point, User Story 1 should be fully functional and code modifications are complete.

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T006 Update `feature/fido2/detekt-baseline-main.xml` to delete all suppressed `<ID>` entries corresponding to `SuspendFunWithFlowReturnType`, `BooleanPropertyNaming`, `ClassNaming`, and `MagicNumber`.
- [ ] T007 Run `tools\local-ci.ps1` to validate that Detekt passes fully with the updated baseline and that no unit tests were broken by the refactoring.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Start immediately
- **Foundational (Phase 2)**: N/A
- **User Stories (Phase 3+)**: Start after Phase 1.
- **Polish (Final Phase)**: Depends on US1 completion.

### User Story Dependencies

- **User Story 1 (P1)**: Independent execution.

### Within Each User Story

- Refactoring tasks (T002 - T005) can largely run in parallel, though it is recommended to tackle one rule category at a time to minimize merge conflicts.

### Parallel Opportunities

- Due to the nature of static analysis fixes, many files will be modified. Parallelization across rule categories is possible.

---

## Parallel Example: User Story 1

```bash
# Fix specific rule violations in parallel passes
Task: "Resolve SuspendFunWithFlowReturnType"
Task: "Resolve BooleanPropertyNaming violations"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup validation.
2. Execute Phase 3: User Story 1 refactorings.
3. Complete Polish phase: Cleanup baseline and validate via `local-ci.ps1`.
4. **STOP and VALIDATE**: Ensure no business logic has changed.

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Verify tests pass frequently during refactoring.
- Commit after each task or rule category.

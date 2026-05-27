# Tasks: Authenticate Credential Use Case

**Input**: Design documents from `specs/050-authenticate-credential-usecase/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, quickstart.md

**Tests**: The constitution mandates TDD. A unit test task is included for the new use case.

**Organization**: Tasks are grouped by user story. Given the small scope (S complexity), this is a linear sequence.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Path Conventions

- **Android KMP module**: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/`
- **Tests**: `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/` or `feature/fido2/src/commonTest/`

---

## Phase 1: Setup

**Purpose**: No project setup needed — existing project, existing module. Skip.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: No foundational/blocking tasks. All infrastructure exists.

---

## Phase 3: User Story 1 - Consistent Authentication Invocation (Priority: P1) 🎯 MVP

**Goal**: Create `AuthenticateCredentialUseCase` following the established `@Factory` + `operator fun invoke()` pattern.

**Independent Test**: Invoke the new use case and confirm it returns the same result as the current `authenticateWithCredential` code path.

### Tests for User Story 1

- [x] T001 [US1] Write unit test for `AuthenticateCredentialUseCase` in `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/domain/usecase/AuthenticateCredentialUseCaseTest.kt` — verify it delegates to `Fido2Repository.authenticateCredential(rpId)` and returns the outcome unchanged for both success and error cases

### Implementation for User Story 1

- [x] T002 [US1] Create `AuthenticateCredentialUseCase` class in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/usecase/AuthenticateCredentialUseCase.kt` — annotated with `@Factory`, injecting `Fido2Repository`, with `suspend operator fun invoke(rpId: RpId): Outcome<CredentialId, DomainError>` delegating to `fido2Repository.authenticateCredential(rpId)`

**Checkpoint**: `AuthenticateCredentialUseCase` exists and passes its unit test. The existing code continues to work unchanged.

---

## Phase 4: User Story 2 - Service Layer Delegation (Priority: P2)

**Goal**: Rewire `Fido2ServiceImpl.authenticateWithCredential` to delegate through the new use case.

**Independent Test**: Confirm `Fido2ServiceImpl` injects and delegates to `AuthenticateCredentialUseCase`.

**Depends on**: Phase 3 (User Story 1) — the use case must exist before the service can inject it.

### Implementation for User Story 2

- [x] T003 [US2] Modify `Fido2ServiceImpl` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/service/impl/Fido2ServiceImpl.kt` — add `AuthenticateCredentialUseCase` constructor parameter, update `authenticateWithCredential` to delegate to `authenticateCredentialUseCase(rpId)` instead of `fido2Repository.authenticateCredential(rpId)`, update KDoc to reference the new use case

**Checkpoint**: `Fido2ServiceImpl.authenticateWithCredential` now delegates through the use case. All existing behavior is preserved.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Verification and lint compliance.

- [x] T004 Compile the project: `.\gradlew :feature:fido2:compileDebugKotlin`
- [x] T005 [P] Run existing test suite: `.\gradlew :feature:fido2:testDebugUnitTest`
- [x] T006 [P] Run Detekt and Ktlint: `.\gradlew :feature:fido2:detekt :feature:fido2:ktlintCheck`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: Skipped — no setup needed
- **Phase 2 (Foundational)**: Skipped — no blocking prerequisites
- **Phase 3 (US1)**: Can start immediately — T001 → T002 (TDD: test first, then implement)
- **Phase 4 (US2)**: Depends on Phase 3 (T002 must be complete) — T003
- **Phase 5 (Polish)**: Depends on Phase 4 — T004 → T005 [P] T006

### User Story Dependencies

- **User Story 1 (P1)**: No dependencies — standalone new class
- **User Story 2 (P2)**: Depends on US1 — the use case must exist to be injected

### Within Each User Story

- US1: Test (T001) → Implementation (T002)
- US2: Implementation only (T003)
- Polish: Compile (T004) → Parallel lint + tests (T005, T006)

### Parallel Opportunities

- T005 and T006 can run in parallel (different Gradle tasks, no file conflicts)
- All other tasks are sequential due to the linear dependency chain

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 3: User Story 1 (T001 → T002)
2. **STOP and VALIDATE**: Compile + run test to confirm the use case works
3. The new use case exists but is not yet wired — existing code is unaffected

### Full Delivery

1. Complete Phase 3: User Story 1 (T001 → T002)
2. Complete Phase 4: User Story 2 (T003)
3. Complete Phase 5: Polish (T004 → T005 + T006)
4. All acceptance scenarios from spec.md are satisfied

---

## Notes

- Total tasks: 6
- This is S (Small) complexity — entire refactoring can be completed in a single session
- No data model, schema, or DI module changes needed — `@ComponentScan` auto-discovers the new `@Factory` class
- The `fido2Repository` dependency stays in `Fido2ServiceImpl` — it's still used by `registerNewCredential`, `getAllCredentials`, and `deleteCredential`

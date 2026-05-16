# Tasks: FIDO2 Performance RCA Fix

**Input**: Design documents from `specs/042-fido2-perf-rca/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md

**Tests**: Not explicitly requested, but verification is expected.
**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

*(N/A - Existing project)*

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

*(N/A - Existing infrastructure)*

---

## Phase 3: User Story 1 - Accurate Performance Telemetry for FIDO2 Authentication (Priority: P1) 🎯 MVP

**Goal**: Accurately track raw system processing time during authentication, excluding user interaction time.

**Independent Test**: Can be fully tested by triggering a GetAssertion operation that includes user interaction and verifying that the `Total` and `User` times are correctly segregated in the performance logs, and no false-positive violation is raised when pure system latency is under 200ms.

### Implementation for User Story 1

- [x] T001 [US1] Wrap `deferred.await()` with `LatencyProfiler.startUserInteraction` and `endUserInteraction` inside a `try/finally` block in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/Ctap2GetAssertionHandler.kt`

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently.

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

*(N/A)*

---

## Dependencies & Execution Order

### Phase Dependencies

- **User Stories (Phase 3+)**: Can start immediately.

### User Story Dependencies

- **User Story 1 (P1)**: No dependencies.

### Within Each User Story

- Core implementation before integration
- Story complete before moving to next priority

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 3: User Story 1
2. **STOP and VALIDATE**: Test User Story 1 independently
3. Deploy/demo if ready

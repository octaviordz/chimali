---
description: "Task list for Event Sourcing Model Integration"
---

# Tasks: Event Sourcing Model Integration

**Input**: Design documents from `specs/034-refactor-event-sourcing/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/event-store-contract.md

**Tests**: Test tasks are included per the Constitution IX requirement for TDD.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Create `DomainEvent.kt`, `EventKind.kt`, and `TraceEntry.kt` base types in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/eventsourcing/`
- [x] T002 Create `Snapshot.kt` and `OptimisticConcurrencyException.kt` in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/eventsourcing/`
- [x] T003 [P] Create `Decider.kt` and `AggregateService.kt` interfaces in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/eventsourcing/`
- [x] T004 [P] Create `EventStoreRepository.kt` and `SnapshotRepository.kt` interfaces in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/repository/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T005 [P] Setup EventStore and Snapshot SQL schemas for Vault in `core/database/src/main/sqldelight/com/chimali/core/database/Vault.sq`
- [x] T006 [P] Setup EventStore and Snapshot SQL schemas for Passkey in `feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/Fido2Database.sq`
- [x] T007 Rename the core database from `ChimaliDatabase` to `VaultDatabase` in both SQLDelight schema files and Kotlin DI modules (`DatabaseModule.kt`, `VaultModule.kt`).
- [x] T008 Add SQLDelight data truncation migration scripts for both databases to automatically clear existing `VaultEntry` and `PasskeyCredential` data.

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - System Auditability & Traceable Mutations (Priority: P1) 🎯 MVP

**Goal**: Record all state changes as granular, immutable events, and ensure the system can deterministically reconstruct an entity's state.

**Independent Test**: Append multiple contradictory events and verify the final reconstructed state resolves correctly while maintaining a complete trace log.

### Tests for User Story 1

- [x] T008 [P] [US1] Unit test for VaultDecider pure functions in `core/domain/src/commonTest/kotlin/com/chimali/core/domain/eventsourcing/vault/VaultDeciderTest.kt`
- [x] T009 [P] [US1] Unit test for PasskeyDecider pure functions in `core/domain/src/commonTest/kotlin/com/chimali/core/domain/eventsourcing/passkey/PasskeyDeciderTest.kt`
- [x] T010 [P] [US1] Integration test for Vault Aggregate Service in `core/data/src/test/kotlin/com/chimali/core/data/eventsourcing/VaultAggregateServiceImplTest.kt`

### Implementation for User Story 1 (Vault Aggregate)

- [x] T011 [P] [US1] Create `VaultEvent.kt`, `VaultCommand.kt`, and `VaultState.kt` in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/eventsourcing/vault/`. All event and state classes MUST be annotated with `@Serializable` (KMP-compatible).
- [x] T012 [US1] Implement `VaultDecider.kt` in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/eventsourcing/vault/`
- [x] T013 [US1] Implement `EventStoreRepositoryImpl.kt` for VaultDatabase in `core/data/src/main/kotlin/com/chimali/core/data/eventsourcing/`
- [x] T014 [US1] Implement `VaultAggregateServiceImpl.kt` with append and retry logic in `core/data/src/main/kotlin/com/chimali/core/data/eventsourcing/`
- [x] T015 [US1] Refactor `VaultRepositoryImpl.kt` to dispatch commands via AggregateService in `feature/vault/src/main/java/com/chimali/feature/vault/internal/`

### Implementation for User Story 1 (Passkey Aggregate)

- [x] T016 [P] [US1] Create `PasskeyEvent.kt`, `PasskeyCommand.kt`, and `PasskeyState.kt` in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/eventsourcing/passkey/`. All event and state classes MUST be annotated with `@Serializable` (KMP-compatible).
- [x] T017 [US1] Implement `PasskeyDecider.kt` in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/eventsourcing/passkey/`
- [x] T018 [US1] Implement `PasskeyEventStoreRepositoryImpl.kt` for Fido2Database in `feature/fido2/src/commonMain/kotlin/com/chimali/fido2/data/eventsourcing/`
- [x] T019 [US1] Implement `PasskeyAggregateServiceImpl.kt` with append and retry logic in `feature/fido2/src/commonMain/kotlin/com/chimali/fido2/data/eventsourcing/`
- [x] T020 [US1] Refactor `PasskeyRepositoryImpl.kt` to dispatch commands via AggregateService in `feature/fido2/src/commonMain/kotlin/com/chimali/fido2/data/`

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Temporal Queries and Time Travel (Priority: P1)

**Goal**: Query the exact state of an entity at any specific point in the past using timestamp-based reconstruction.

**Independent Test**: Replay the event stream up to a specific timestamp and compare the reconstructed state against expected values.

### Tests for User Story 2
- [x] T021 [P] [US2] Integration test for temporal queries (`asOf` timestamp) in both databases.

### Implementation for User Story 2
- [x] T022 [P] [US2] Implement `getEventsUpTo` in `EventStoreRepositoryImpl.kt` (Vault)
- [x] T023 [P] [US2] Implement `getEventsUpTo` in `PasskeyEventStoreRepositoryImpl.kt` (Passkey)
- [x] T024 [US2] Implement `getStateAt(asOf: Instant)` in `VaultAggregateServiceImpl.kt`
- [x] T025 [US2] Implement `getStateAt(asOf: Instant)` in `PasskeyAggregateServiceImpl.kt`

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Snapshot-Accelerated Hydration (Priority: P2)

**Goal**: Periodically save snapshots of reconstructed states (every 20 events) so that subsequent data loads are fast.

**Independent Test**: Load an entity from a snapshot with subsequent events, verifying the state matches full hydration.

### Tests for User Story 3

- [X] T026 [P] [US3] Unit test for Snapshot loading and fallback in `core/data/src/test/kotlin/com/chimali/core/data/eventsourcing/SnapshotRepositoryImplTest.kt`

### Implementation for User Story 3

- [X] T027 [P] [US3] Implement `SnapshotRepositoryImpl.kt` in `core/data/src/main/kotlin/com/chimali/core/data/eventsourcing/`
- [X] T028 [P] [US3] Implement `PasskeySnapshotRepositoryImpl.kt` in `feature/fido2/src/commonMain/kotlin/com/chimali/fido2/data/eventsourcing/PasskeySnapshotRepositoryImpl.kt`
- [X] T029 [US3] Integrate snapshot saving logic (threshold: 20 events) into `VaultAggregateServiceImpl.kt`
- [X] T030 [US3] Integrate snapshot saving logic (threshold: 20 events) into `PasskeyAggregateServiceImpl.kt`

**Checkpoint**: All user stories should now be independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T031 [P] Run `tools/local-ci.ps1` to ensure all tests pass and no linting violations exist.
- [ ] T032 Verify AES-256-GCM encryption is correctly applied to all persisted event and snapshot payloads in both databases.
- [ ] T033 [US3] Benchmark hydration performance with and without snapshots for an aggregate with 1,000+ events to verify the 90% reduction target (SC-003).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - US1 (Vault & Passkey) can proceed in parallel.
  - US2 and US3 depend on the core append/read logic from US1.
- **Polish (Final Phase)**: Depends on all user stories being complete

### Parallel Opportunities

- Vault and Passkey decider implementations and tests can run completely in parallel.
- Database schema changes (Vault.sq and Fido2Database.sq) can be executed in parallel.
- Snapshot repository implementations can be built in parallel.

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Verify end-to-end event sourcing works for Vault and Passkey mutations.

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 (Temporal Queries) → Test independently → Deploy/Demo
4. Add User Story 3 (Snapshots) → Test independently → Deploy/Demo

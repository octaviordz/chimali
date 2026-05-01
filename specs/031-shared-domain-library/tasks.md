---
description: "Task list template for feature implementation"
---

# Tasks: Shared Domain Library Evolution

**Input**: Design documents from `/specs/031-shared-domain-library/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, quickstart.md

**Tests**: Test tasks are included as requested by the Constitution (`kotlin.test` required for core libraries).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Paths are relative to the repository root.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Update `core:domain` build configuration to include `kotlinx-datetime`, `kotlinx-serialization`, and `signum` in `core/domain/build.gradle.kts`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T002 Create unified package structures for models and value objects in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Centralized Domain Identity Types (Priority: P1) 🎯 MVP

**Goal**: Provide strongly-typed identifiers to prevent primitive obsession and avoid cross-module integration bugs.

**Independent Test**: Value classes compile cleanly and `CredentialId` correctly delegates random generation without `java.*` dependencies.

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [x] T003 [P] [US1] Write unit test for `CredentialId` generation logic in `core/domain/src/commonTest/kotlin/com/chimali/core/domain/valueobject/CredentialIdTest.kt`

### Implementation for User Story 1

- [x] T004 [P] [US1] Create `@JvmInline value class RpId` in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/valueobject/RpId.kt`
- [x] T005 [P] [US1] Create `@JvmInline value class UserId` in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/valueobject/UserId.kt`
- [x] T006 [P] [US1] Create `@JvmInline value class PasskeyId` in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/valueobject/PasskeyId.kt`
- [x] T007 [US1] Migrate and refactor `CredentialId` with KMP-safe generation using `CryptoRand` in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/valueobject/CredentialId.kt`
- [x] T008 [US1] Clean up legacy references, unify existing `ValueObjects.kt` in `core:domain`, and delete the duplicate `CredentialId.kt` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/model/CredentialId.kt`
- [x] T008a [US1] Refactor `CredentialRepository` in `feature/fido2` to use `CredentialId` instead of `String`
- [x] T008b [US1] Refactor FIDO2 UseCases (`SelectCredentialUseCase`, `GetAssertionUseCase`) to consume the new `CredentialId` and `RpId` types

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Platform-Agnostic Domain Models (Priority: P2)

**Goal**: Deliver KMP-compatible domain entities avoiding JVM-specific types like `java.time.Instant`.

**Independent Test**: The models successfully serialize and deserialize via `kotlinx.serialization`, proving platform agnosticism.

### Tests for User Story 2 ⚠️

- [x] T009 [P] [US2] Write unit tests ensuring serialization compatibility of domain models in `core/domain/src/commonTest/kotlin/com/chimali/core/domain/model/DomainModelSerializationTest.kt`

### Implementation for User Story 2

- [x] T010 [P] [US2] Create KMP-safe `RelyingParty` model using `RpId` in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/model/RelyingParty.kt`
- [x] T011 [P] [US2] Create KMP-safe `CredentialSummary` model using `kotlinx.datetime.Instant` in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/model/CredentialSummary.kt`
- [x] T012 [P] [US2] Create KMP-safe `UserConsentRecord` model in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/model/UserConsentRecord.kt`

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [x] T013 Update `feature:fido2` dependencies to ensure it references the unified `core:domain` module in `feature/fido2/build.gradle.kts`
- [x] T014 Run validation pipeline via `tools/local-ci.ps1`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed sequentially in priority order (P1 → P2)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Depends on User Story 1 (since models require the strong IDs created in US1)

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- Identifier models (US1) before Entity models (US2)
- Story complete before moving to next priority

### Parallel Opportunities

- Identifier models (RpId, UserId, PasskeyId) can be created concurrently.
- Domain models (RelyingParty, CredentialSummary, UserConsentRecord) can be implemented concurrently once the identifier models are available.

---

## Parallel Example: User Story 1

```bash
# Launch all simple IDs for User Story 1 together:
Task: "Create @JvmInline value class RpId in core/domain/src/commonMain/kotlin/com/chimali/core/domain/valueobject/RpId.kt"
Task: "Create @JvmInline value class UserId in core/domain/src/commonMain/kotlin/com/chimali/core/domain/valueobject/UserId.kt"
Task: "Create @JvmInline value class PasskeyId in core/domain/src/commonMain/kotlin/com/chimali/core/domain/valueobject/PasskeyId.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Verify that new identifier value classes compile and are usable.

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → MVP!
3. Add User Story 2 → Ensure cross-platform build compatibility
4. Each story adds value without breaking previous stories

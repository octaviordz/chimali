# Tasks: Remediate FIDO2 Authenticator

**Input**: Design documents from `specs/054-remediate-fido2-authenticator/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/authenticator-remediation.md`, `quickstart.md`

**Tests**: Required by FR-008 and Constitution XII.3. Write focused tests before their implementation tasks.

**Organization**: Tasks are grouped by user story so each increment remains independently testable.

## Phase 1: Setup

**Purpose**: Establish traceable implementation scope and test seams.

- [X] T001 Document FR-001 through FR-008 traceability in `specs/054-remediate-fido2-authenticator/plan.md` and relevant production/test KDoc.

---

## Phase 2: Foundational

**Purpose**: Isolate capability information from the stale facade without changing CTAP ceremony ownership.

- [X] T002 Create a focused authoritative authenticator-information owner and relocate `AuthenticatorInfo` from `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/service/Fido2Authenticator.kt`.
- [X] T003 [P] Add focused capability-information tests in `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/ctap2/`.

**Checkpoint**: Capability information has an independent owner before facade removal.

---

## Phase 3: User Story 1 - Complete an authenticator ceremony (Priority: P1) 🎯 MVP

**Goal**: Preserve the established CTAP registration/assertion paths while removing the duplicate nonfunctional facade.

**Independent Test**: Existing CTAP ceremony tests and new dependency tests prove that live registration and assertion
continue to use their established handlers/use cases after facade removal.

- [X] T004 [P] [US1] Add regression tests for the authoritative CTAP make-credential and get-assertion paths in `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/ctap2/`.
- [X] T005 [US1] Replace `Fido2Authenticator` capability lookup in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BluetoothHidTransportImpl.kt` with the focused capability-information owner.
- [X] T006 [US1] Delete `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/service/Fido2Authenticator.kt` and `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/service/impl/Fido2AuthenticatorImpl.kt`; remove all bindings/imports/references.
- [X] T007 [US1] Verify no production FIDO2 source contains a reachable `Not implemented` authenticator placeholder in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/`.

**Checkpoint**: CTAP ceremonies remain operational and no stale authenticator facade exists.

---

## Phase 4: User Story 2 - Inspect credential availability and verification requirements (Priority: P1)

**Goal**: Return accurate stored credential views and user-verification statistics.

**Independent Test**: Persist credentials with policy values `1` and `3`; verify policy filtering and statistics select
only policy `3` credentials while general and relying-party listings retain all applicable credentials.

- [X] T008 [P] [US2] Add policy-filtering and statistics regression tests in `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/repository/CredentialRepositoryImplTest.kt` or the existing focused repository test file.
- [X] T009 [US2] Implement persisted `cred_protect_policy` filtering in `getCredentialsRequiringUserVerification()` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/CredentialRepositoryImpl.kt`.
- [X] T010 [US2] Derive `credentialsRequiringUserVerification` from the same persisted policy rule in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/CredentialRepositoryImpl.kt`.
- [X] T011 [US2] Verify all-credential and relying-party repository flows remain unchanged in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/CredentialRepositoryImpl.kt`.

**Checkpoint**: Credential policy projections and statistics are accurate and independently tested.

---

## Phase 5: User Story 3 - Avoid unsupported placeholder operations (Priority: P2)

**Goal**: Retire unsupported placeholder operations and retain correct get-info capability output.

**Independent Test**: Build a get-info response after facade removal and verify its advertised capability information is
unchanged from the supported CTAP contract.

- [X] T012 [P] [US3] Add get-info regression coverage for the focused capability-information owner in `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/ctap2/Ctap2ProtocolTest.kt`.
- [X] T013 [US3] Verify production transport source no longer depends on the deleted facade in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BluetoothHidTransportImpl.kt`.

**Checkpoint**: The public live transport exposes only supported behavior and correct capabilities.

---

## Phase 6: Polish & Cross-Cutting Validation

**Purpose**: Run the documented verification suite and finalize traceability.

- [X] T014 Run focused FIDO2 host tests and update requirement references in `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/`.
- [X] T015 Run `:feature:fido2` compile, test, Detekt, and Ktlint validation from `specs/054-remediate-fido2-authenticator/quickstart.md`.
- [X] T016 Update completed task markers and verify no unchecked implementation task remains in `specs/054-remediate-fido2-authenticator/tasks.md`.

## Dependencies & Execution Order

- Phase 1 precedes all implementation work.
- Phase 2 creates the capability-information seam before facade removal.
- User Story 1 and User Story 2 can proceed after Phase 2; User Story 3 depends on User Story 1.
- Polish follows all story phases.

## Parallel Opportunities

- T003 and T004 can be written independently of production changes.
- T008 can be written independently while capability work progresses.
- T012 can be added independently once the capability contract is understood.

## Implementation Strategy

1. Establish a focused capability owner and its tests.
2. Remove the obsolete facade while preserving the existing CTAP ceremony path.
3. Correct the persisted verification-policy projections.
4. Run focused tests, module validation, and a final convergence check.

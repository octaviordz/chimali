# Tasks: FIDO2 HID Virtual Authenticator

**Input**: Design documents from `/specs/003-fido2-hid-authenticator/`
**Prerequisites**: [plan.md](./plan.md) (required), [spec.md](./spec.md) (required), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/ctap2-hid.md](./contracts/ctap2-hid.md)

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)

## Path Conventions

- Paths follow the multi-module architecture defined in `plan.md`.
- **Core Modules**: `core/fido2/`, `core/bluetooth/`, `core/security/`
- **Feature Module**: `feature/fido2/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and Rust/UniFFI integration.

- [ ] T001 Create `core/fido2/` and `feature/fido2/` module structures per `plan.md`
- [ ] T002 Initialize Rust project in `core/fido2/rust/` and add `passkey-rs` dependency
- [ ] T003 Configure UniFFI bindings in `core/fido2/rust/src/lib.rs` and `core/fido2/kotlin/`
- [ ] T004 [P] Configure Hilt modules for FIDO2 and Bluetooth in `feature/fido2/src/internal/di/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core protocol logic and database schema.

- [ ] T005 Implement FIDO2 credential schema in `core/database/` using SQLDelight
- [ ] T006 [P] Implement transport-agnostic CTAP2 wrapper in `core/fido2/rust/src/ctap_wrapper.rs`
- [ ] T007 [P] Implement Bluetooth HID Profile registration logic in `core/bluetooth/src/HidManager.kt`
- [ ] T008 Setup MVI State and Intent definitions in `feature/fido2/src/api/`
- [ ] T008a [P] Implement concurrent request queuing logic in `feature/fido2/src/internal/RequestQueue.kt` (ref: FR-006)

**Checkpoint**: Foundation ready - Protocol logic and HID registration are possible.

---

## Phase 3: User Story 1 - Desktop Authentication (Priority: P1) 🎯 MVP

**Goal**: Use Android phone as a security key for a desktop host.

**Independent Test**: Pair with PC, trigger WebAuthn request, confirm on phone.

### Implementation for User Story 1

- [ ] T009 [US1] Implement HID Interrupt service to receive CTAP reports in `feature/fido2/src/internal/HidService.kt`
- [ ] T010 [US1] Implement Credential Lookup logic (Resident Key) in `feature/fido2/src/internal/CredentialRepository.kt`
- [ ] T011 [US1] Create User Confirmation UI in `feature/fido2/src/ui/ConfirmationScreen.kt`
- [ ] T012 [US1] Bridge CTAP signature request to Rust `passkey-authenticator` in `core/fido2/rust/`
- [ ] T013 [US1] Wire MVI ViewModel to handle authentication lifecycle in `feature/fido2/src/internal/FidoViewModel.kt`

**Checkpoint**: User Story 1 (MVP) functional - Signed response sent via HID.

---

## Phase 4: User Story 2 - Passkey Registration (Priority: P2)

**Goal**: Create and store new passkeys from a desktop request.

**Independent Test**: Trigger registration on webauthn.io, save credential in Chimali.

### Implementation for User Story 2

- [ ] T014 [US2] Implement `MakeCredential` report handling in `feature/fido2/src/internal/HidService.kt`
- [ ] T015 [US2] Create Passkey Creation UI/Dialog in `feature/fido2/src/ui/CreatePasskeyScreen.kt`
- [ ] T016 [US2] Implement credential persistence logic in `feature/fido2/src/internal/CredentialRepository.kt`
- [ ] T017 [US2] Implement Self-Attestation generation in `core/fido2/rust/`

**Checkpoint**: User Story 2 functional - New credentials saved to vault.

---

## Phase 5: User Story 3 - Connection Management (Priority: P3)

**Goal**: Manage paired desktop devices.

**Independent Test**: List paired devices and disconnect via UI.

### Implementation for User Story 3

- [ ] T018 [US3] Create device list UI in `feature/fido2/src/ui/DeviceManagerScreen.kt`
- [ ] T019 [US3] Implement Bluetooth pairing/unpairing logic in `core/bluetooth/src/HidManager.kt`
- [ ] T020 [P] [US3] Store paired device metadata in `core/database/`

**Checkpoint**: All user stories functional.

---

## Phase N: Polish & Cross-Cutting Concerns

- [ ] T021 [P] Ensure persistent notification is shown when HID service is active
- [ ] T022 Implement error handling for Bluetooth disconnects in `feature/fido2/src/internal/`
- [ ] T023 [P] Add high-legibility font support to confirmation screens (ref: FR-UI-010)
- [ ] T023a [P] Audit and implement explicit memory zeroing-out for sensitive data in volatile memory (Constitution Principle I)
- [ ] T024 Validate full end-to-end flow and SC-003 latency targets (< 200ms) using `quickstart.md`

---

## Dependencies & Execution Order

1. **Setup (T001-T004)** must be completed first.
2. **Foundational (T005-T008)** blocks all User Stories.
3. **User Story 1 (P1)** is the primary focus (MVP).
4. **User Story 2 & 3** can proceed after US1 or in parallel if foundation is stable.

---

## Implementation Strategy

### MVP First
1. Complete Setup + Foundation.
2. Implement **User Story 1** (Authentication) to achieve core value.
3. Verify with `WebAuthn.io`.

### Parallel Opportunities
- T006 (Rust protocol) and T007 (Kotlin HID) can be worked on in parallel.
- UI screens (T011, T015, T018) can be mocked and drafted in parallel with protocol logic.

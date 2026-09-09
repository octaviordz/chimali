# Tasks: Vault Feature Completion

**Input**: Design documents from `specs/053-vault-completion/`  
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/)

## Format: `[ID] [P?] [Story] Description with file path`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g. `[US1]`, `[US2]`, `[US3]`, `[US4]`, `[US5]`, `[US6]`)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify and prepare feature infrastructure and dependencies

- [x] T001 Verify dependencies and KMP build for `:feature:vault` in `feature/vault/build.gradle.kts`
- [x] T002 Verify Koin dependency injection registrations in `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultModule.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core cryptographic serialization services and base navigation prerequisites that all user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T003 [P] Create `VaultCryptoService` contract in `feature/vault/src/main/java/com/chimali/feature/vault/internal/crypto/VaultCryptoService.kt`
- [x] T004 [P] Unit tests for `VaultCryptoService` in `feature/vault/src/test/java/com/chimali/feature/vault/internal/crypto/VaultCryptoServiceTest.kt`
- [x] T005 Implement `VaultCryptoServiceImpl` using `AesEncryptionManager` and `EventStoreKeyProvider` with memory zeroing in `feature/vault/src/main/java/com/chimali/feature/vault/internal/crypto/VaultCryptoServiceImpl.kt`
- [x] T006 Register `VaultCryptoService` in `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultModule.kt`
- [x] T007 Define `VaultDestinations` constants in `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultDestinations.kt`
- [x] T008 Implement `VaultNavGraph` with `NavHost` connecting list, entry, and detail routes in `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultNavGraph.kt`
- [x] T009 Replace stubbed `VaultListScreen` in `app/src/main/kotlin/com/chimali/navigation/AppNavGraph.kt` with `VaultNavGraph`

**Checkpoint**: Core crypto engine and navigation shell are ready; user story flows can now be integrated.

---

## Phase 3: User Story 1 - Add a New Password Entry (Priority: P1) 🎯 MVP

**Goal**: Users can tap (+) on VaultListScreen, choose Password, fill in details, save, and see the encrypted item saved and displayed in the vault list.

**Independent Test**: Navigate from Vault list -> Add Password form -> Save -> Verify item appears in list.

### Tests for User Story 1
- [x] T010 [P] [US1] Unit test for saving password payload via ViewModel in `feature/vault/src/test/java/com/chimali/feature/vault/internal/VaultViewModelPasswordSaveTest.kt`

### Implementation for User Story 1
- [x] T011 [US1] Update `VaultIntent` in `feature/vault/src/main/java/com/chimali/feature/vault/api/VaultMvi.kt` to support `SavePassword(payload: PasswordPayload, labelIds: List<UUID>)`
- [x] T012 [US1] Implement `SavePassword` intent handling in `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultViewModel.kt` using `VaultCryptoService` and `VaultService`
- [x] T013 [US1] Add Add-Item-Type selector (bottom sheet / dialog) in `feature/vault/src/main/java/com/chimali/feature/vault/ui/VaultListScreen.kt` triggered by FAB
- [x] T014 [US1] Connect `PasswordEntryScreen` onSave to `VaultViewModel.processIntent(VaultIntent.SavePassword(...))` in `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultNavGraph.kt`

**Checkpoint**: User Story 1 complete! Password creation delivers the core MVP.

---

## Phase 4: User Story 2 - View and Copy a Stored Password (Priority: P1)

**Goal**: Users can tap a password entry to view decrypted details, reveal the password with high-legibility font, and copy it to clipboard with 60s auto-clear.

**Independent Test**: Tap password item in list -> Open detail -> Toggle password visibility -> Copy to clipboard -> Verify clipboard cleared after 60s.

### Tests for User Story 2
- [x] T015 [P] [US2] Unit test for password decryption in `feature/vault/src/test/java/com/chimali/feature/vault/internal/VaultViewModelPasswordDecryptTest.kt`

### Implementation for User Story 2
- [x] T016 [US2] Update `VaultIntent.DecryptItem` and `VaultState` in `feature/vault/src/main/java/com/chimali/feature/vault/api/VaultMvi.kt` to expose decrypted `PasswordPayload`
- [x] T017 [US2] Implement item decryption in `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultViewModel.kt` via `VaultCryptoService.decryptPassword`
- [x] T018 [US2] Wire list item click to detail route and connect `PasswordDetailScreen` to decrypted state and clipboard copy in `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultNavGraph.kt`
- [x] T019 [US2] Ensure `PasswordDetailScreen` calls `payload.clearMemory()` when back navigation occurs in `feature/vault/src/main/java/com/chimali/feature/vault/ui/PasswordDetailScreen.kt`

**Checkpoint**: User Stories 1 and 2 deliver end-to-end password management.

---

## Phase 5: User Story 3 - Edit and Delete a Vault Entry (Priority: P1)

**Goal**: Users can edit or delete an existing vault entry from its detail screen.

**Independent Test**: Open detail view -> Edit fields -> Save -> Verify updated values. Open detail view -> Tap Delete -> Confirm -> Verify entry removed from list.

### Tests for User Story 3
- [x] T020 [P] [US3] Unit test for deleting vault entries in `feature/vault/src/test/java/com/chimali/feature/vault/internal/VaultViewModelDeleteTest.kt`

### Implementation for User Story 3
- [x] T021 [US3] Implement entry deletion confirmation dialog and callback in `feature/vault/src/main/java/com/chimali/feature/vault/ui/PasswordDetailScreen.kt`
- [x] T022 [US3] Connect onDelete callback to `VaultViewModel.processIntent(VaultIntent.DeleteItem(id))` and navigate back in `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultNavGraph.kt`
- [x] T023 [US3] Support edit mode on `PasswordEntryScreen` pre-populated with existing payload in `feature/vault/src/main/java/com/chimali/feature/vault/ui/PasswordEntryScreen.kt`

**Checkpoint**: Full lifecycle (Create, Read, Update, Delete) functional for passwords.

---

## Phase 6: User Story 4 - Add and View Credit Cards (Priority: P1)

**Goal**: Users can add, view, and delete encrypted Credit Card entries.

**Independent Test**: Add Credit Card -> Verify in list -> Open detail -> Reveal card number/CVV -> Delete card.

### Tests for User Story 4
- [x] T024 [P] [US4] Unit test for credit card save and decrypt in `feature/vault/src/test/java/com/chimali/feature/vault/internal/VaultViewModelCreditCardTest.kt`

### Implementation for User Story 4
- [x] T025 [US4] Add `SaveCreditCard` intent and `selectedCreditCardPayload` state in `feature/vault/src/main/java/com/chimali/feature/vault/api/VaultMvi.kt`
- [x] T026 [US4] Implement credit card encryption and decryption in `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultViewModel.kt`
- [x] T027 [US4] Wire `CreditCardEntryScreen` and `CreditCardDetailScreen` into `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultNavGraph.kt`

**Checkpoint**: Credit cards fully functional alongside passwords.

---

## Phase 7: User Story 5 - Add and View Secure Notes (Priority: P2)

**Goal**: Users can add, view, and delete encrypted Secure Notes.

**Independent Test**: Add Secure Note -> Verify in list -> Open detail -> Read note text -> Delete note.

### Tests for User Story 5
- [x] T028 [P] [US5] Unit test for secure note save and decrypt in `feature/vault/src/test/java/com/chimali/feature/vault/internal/VaultViewModelSecureNoteTest.kt`

### Implementation for User Story 5
- [x] T029 [US5] Add `SaveSecureNote` intent and `selectedSecureNotePayload` state in `feature/vault/src/main/java/com/chimali/feature/vault/api/VaultMvi.kt`
- [x] T030 [US5] Implement secure note encryption and decryption in `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultViewModel.kt`
- [x] T031 [US5] Wire `SecureNoteEntryScreen` and `SecureNoteDetailScreen` into `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultNavGraph.kt`

**Checkpoint**: All three credential types (Passwords, Cards, Notes) are operational.

---

## Phase 8: User Story 6 - Filter Vault Items by Label (Priority: P2)

**Goal**: Users can manage labels and filter items by label in the vault list.

**Independent Test**: Open Label Manager -> Create label -> Filter list by label -> Confirm only matching items display.

### Implementation for User Story 6
- [x] T032 [P] [US6] Add label CRUD queries and repository methods in `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultRepositoryImpl.kt`
- [x] T033 [US6] Wire `onManageLabelsClick` in `VaultListScreen` to navigate to `LabelManagerScreen` in `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultNavGraph.kt`
- [x] T034 [US6] Wire label filter tabs in `VaultListScreen` to `VaultViewModel.processIntent(VaultIntent.LoadItems(labelId))` in `feature/vault/src/main/java/com/chimali/feature/vault/ui/VaultListScreen.kt`

**Checkpoint**: Organization and filtering operational.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Quality checks, formatting, and end-to-end verification

- [x] T035 [P] Run unit tests across `:feature:vault` via `./gradlew :feature:vault:testDebugUnitTest`
- [x] T036 [P] Run static analysis and formatting checks via `./gradlew detekt ktlintCheck`
- [x] T037 Execute quickstart verification scenarios from `specs/053-vault-completion/quickstart.md`
- [x] T038 Verify zero-memory trace policies (`clearMemory()` on dispose) across all detail and entry screens

---

## Dependencies & Execution Order

### Phase Dependencies
- **Setup (Phase 1)**: No dependencies
- **Foundational (Phase 2)**: Depends on Phase 1 - **BLOCKS all user stories**
- **User Stories (Phase 3-8)**: Depend on Phase 2 completion
  - US1 (Add Password) -> US2 (View/Copy Password) -> US3 (Edit/Delete) -> US4 (Credit Cards) -> US5 (Secure Notes) -> US6 (Labels)
- **Polish (Phase 9)**: Depends on all user stories complete

### Parallel Opportunities
- T003 & T004 can be created in parallel (Contract & Tests)
- T010, T015, T020, T024, T028 can be written concurrently
- Phase 4 (US2) and Phase 6 (US4) models can proceed in parallel once Phase 2 is complete

---

## Implementation Strategy (MVP First)

1. **Phase 1 & 2**: Setup & Foundational (crypto service + navigation graph)
2. **Phase 3**: User Story 1 (Add Password) -> **Validate MVP**
3. **Phase 4 & 5**: View, copy, edit, and delete Passwords
4. **Phase 6 & 7**: Add Cards and Notes
5. **Phase 8**: Label filtering
6. **Phase 9**: Polish, static analysis, and verification

## Phase 10: Convergence

Assessed 2026-09-07 against the amended spec and plan after the original implementation pass (T001–T038 checked). Earlier completion markers are preserved as history, not evidence that acceptance currently passes. Findings F1–F12 correspond to the in-session assessment. No application code changed during convergence.

- [x] T039 **CRITICAL** [F1] Under approved Constitutions 1.0.0/1.1.0 I.5, remove application-retained immutable secret Strings from entry-screen state and payload serialization in `feature/vault/src/main/java/com/chimali/feature/vault/ui/PasswordEntryScreen.kt`, `CreditCardEntryScreen.kt`, `SecureNoteEntryScreen.kt`, and `internal/crypto/VaultCryptoServiceImpl.kt`; define mutable draft/payload ownership and guaranteed cleanup on success, discard, disposal, and failure, including key-provider failure before encryption. Audit any necessary platform text adapters and verify their controls without claiming external-copy erasure. Preserve existing encrypted-payload compatibility and retain drafts only while the user is editing/retrying. Add cleanup tests under `feature/vault/src/test/java/com/chimali/feature/vault/internal/crypto/` per Constitution I, X.2, X.5; FR-VAULT-026; SC-VAULT-005; T038 (contradicts).
- [X] T040 **CRITICAL** [F2] Correct service-boundary error handling in `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultRepositoryImpl.kt` and `internal/crypto/VaultCryptoServiceImpl.kt`: cover actual driver/crypto failure types, include key acquisition in guarded cleanup, return safe `Outcome.Error` messages, and propagate coroutine cancellation; replace broad catches with specific handling and test failures. Do not mask the aggregate cast failure instead of correcting DI per Constitution X.7, XII.5; FR-VAULT-029; plan: Phase 5 boundary handling (contradicts).
- [X] T041 **CRITICAL** [F3] Qualify both aggregate-service and event-store providers/consumers as `vault` or `passkey` in `core/data/src/main/kotlin/com/chimali/core/data/di/DataModule.kt`, `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/di/Fido2Module.kt`, `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/CredentialRepositoryImpl.kt`, and `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultModule.kt`; retain matching snapshot qualifiers, audit overlapping annotated bindings/constructors, and load `coreDataModule` in `app/src/main/kotlin/com/chimali/ChimaliApplication.kt`. Ensure both module orders resolve the correct services and stores without editing generated files per FR-VAULT-024, FR-VAULT-032, FR-VAULT-033; US1/AC5; plan: Phase 5 dependency isolation; T002 (contradicts).
- [X] T042 **CRITICAL** [F4] Add stable identity keys to lazy item rendering in `feature/vault/src/main/java/com/chimali/feature/vault/ui/VaultListScreen.kt` and `LabelManagerScreen.kt`; verify item identity is preserved when entries change or filters switch per Constitution X.4; SC-VAULT-003, SC-VAULT-007 (contradicts).
- [X] T043 [F5] Introduce explicit pending/success/failure mutation state or effects in `feature/vault/src/main/java/com/chimali/feature/vault/api/VaultMvi.kt` and `internal/VaultViewModel.kt`; consume them in `ui/navigation/VaultNavGraph.kt` and all entry screens so save/delete navigation occurs only on confirmed success, failed saves retain input, pending submissions are disabled, and retry uses stable entry identity without duplicates. Add delayed/failure/retry/cancellation tests to `feature/vault/src/test/java/com/chimali/feature/vault/internal/VaultViewModelTest.kt` and navigation instrumentation tests per FR-VAULT-029, FR-VAULT-034; SC-VAULT-010; US1/AC6; plan: Phase 5 mutation completion (partial).
- [ ] T044 [F6] Add `app/src/androidTest/kotlin/com/chimali/di/VaultPasskeyWiringTest.kt` using production module composition/generated registrations and both relative module orders; assert actual qualified aggregates, event stores, and snapshot stores are correctly isolated. Replace placeholder assertions in `feature/vault/src/androidTest/java/com/chimali/feature/vault/internal/VaultServicePasswordTest.kt` with real create/reopen/update/delete coverage for all three types and unchanged passkey records, including event/projection checks. Extend existing FIDO2 integration tests under `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/integration/` to verify passkey save/authentication still work. Do not mock away the aggregate wiring under test per FR-VAULT-032, FR-VAULT-033; SC-VAULT-009; plan: Phase 5 regression verification (missing).
- [X] T045 [F7] Render actionable non-technical read/decryption/label errors from `VaultState.errorMessage` in `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultNavGraph.kt`, `VaultListScreen.kt`, and `LabelManagerScreen.kt`; stop indefinite detail spinners on failure, support retry, and prevent stale selected payloads from displaying for another entry. Verify missing-seed and corrupt-payload cases per FR-VAULT-029; spec: storage/decryption/master-seed edge cases (partial).
- [ ] T046 [F8] Replace the password-copy callback that dispatches `ClearClipboard` in `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultNavGraph.kt` with an actual copy intent/service call via `api/VaultMvi.kt` and `internal/VaultViewModel.kt`; show success only after copying and verify sensitive clipboard clearing after 60 seconds in the existing clipboard service. Add an end-to-end copy assertion per FR-VAULT-027; SC-VAULT-002; US2/AC3; T018 (contradicts).
- [X] T047 [F9] Implement label selection and persistence through all entry screens, `feature/vault/src/main/java/com/chimali/feature/vault/api/VaultMvi.kt`, `internal/VaultViewModel.kt`, `api/VaultService.kt`, and `internal/VaultRepositoryImpl.kt`, using the existing label association schema in `core/database/src/`; preserve assignments on edits and test selected-label/All results per FR-VAULT-028; SC-VAULT-008; US6/AC2–AC3; T011, T032 (missing).
- [X] T048 [F10] Correct edit payload ownership in `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultNavGraph.kt` and the three entry/detail screen pairs: detail disposal currently clears the same payload retained for editing. Ensure an independently owned draft survives transitions without sharing zeroed custom-field arrays; cancel returns to valid details and a successful edit shows refreshed values. Add transition/disposal tests per FR-VAULT-024, FR-VAULT-026; SC-VAULT-004, SC-VAULT-006; US3/AC1 (partial).
- [X] T049 [F11] Compare all editable fields against their initial values in `feature/vault/src/main/java/com/chimali/feature/vault/ui/PasswordEntryScreen.kt`, `CreditCardEntryScreen.kt`, and `SecureNoteEntryScreen.kt`; include website, notes, and custom fields in discard detection and show the required validation hint when required fields are empty. Verify toolbar/system back and explicit cancel per FR-VAULT-031; US1/AC3–AC4; spec: partial-entry edge case (partial).
- [X] T050 [F12] Extend `specs/053-vault-completion/quickstart.md` with SC-VAULT-009/010 fresh/existing-installation, restart, all-type CRUD, passkey-isolation, pending/failure/retry scenarios; execute plan Phase 5 build, host/unit, connected-device, and static checks plus the original memory/clipboard/500-item performance checks. Record exact commands, results, and unavailable-environment blockers in the verification guide; replace unsupported prior completion assumptions with measured evidence without rewriting T001–T038. Verify actual ownership identity is valid in the real-storage scenarios and investigate any subsequent failure before marking acceptance complete per SC-VAULT-001–010; plan: Phases 4–5 verification; T035–T038 (partial).

### Convergence dependencies

- Crash correction: T041 → T044. Service failure handling and save-flow regression: T040 → T043 → T044. These are the immediate reported-issue path.
- Coordinate T039 with T048 before completing T043's draft disposal behavior. T045–T049 address remaining original-scope acceptance gaps, not additional causes of the reported cast exception.
- T050 follows all remediation and relevant tests. A checkmark requires actual verification; unavailable device checks remain outstanding.

### Assessment summary

Reviewed FR-VAULT-021–034 (14 requirements), SC-VAULT-001–010 (10 outcomes), all six user stories, original plan Phases 1–4 plus amended Phase 5, and applicable Constitution I, III, IV, VII, VIII, X.2/X.4/X.5/X.7, XI, XII.3/XII.5 constraints. Findings: 5 contradicts, 5 partial, 2 missing, 0 unrequested; severity: 4 CRITICAL, 7 HIGH, 1 MEDIUM. Runtime/coverage/performance acceptance is not proven by this source review and is explicitly assigned to T050.

## Phase 11: Convergence

Runtime reassessment: 2026-09-07 22:44:58, process 27024. The supplied log now reaches `VaultAggregateServiceImpl.execute` and `EventStoreRepositoryImpl.append`, confirming the reported operation uses the Vault aggregate. It fails with `JsonEncodingException`: `VaultEvent.Created.type` conflicts with the default polymorphic JSON discriminator `type`. This is a newly exposed serialization failure after the original routing failure. No successful save is established by this log.

- [X] T051 **CRITICAL** [F13] Handle serialization failures at the event-store boundary in `core/data/src/main/kotlin/com/chimali/core/data/eventsourcing/EventStoreRepositoryImpl.kt` for append and both read methods, returning `Result.failure` for specific serialization errors so `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultRepositoryImpl.kt` can produce a safe `Outcome.Error`. Preserve cancellation, ensure transaction rollback, and zero key/plaintext/decrypted buffers in `finally` on success and failure. Coordinate with T040 and T043 so a failed operation remains visible with its draft retained. Evidence: only `android.database.SQLException` is caught and the supplied serialization exception escapes into the ViewModel coroutine per Constitution X.5, XII.5; FR-VAULT-029, FR-VAULT-034; plan: Phase 5 boundary handling (contradicts).
- [X] T052 **CRITICAL** [F14] Remove the polymorphic JSON discriminator collision in `core/data/src/main/kotlin/com/chimali/core/data/eventsourcing/EventStoreRepositoryImpl.kt` and, only if required by the chosen encoding, `core/domain/src/commonMain/kotlin/com/chimali/core/domain/eventsourcing/vault/VaultEvent.kt`. Use a discriminator distinct from the existing item `type` field consistently for encode/decode, or an equivalently unambiguous serialization mapping. Preserve item type and all event fields. Inspect existing persisted-format fixtures and define compatible reads for any previously valid event encoding before changing writes; do not reset databases, rename fields blindly, or change Passkey encoding as a side effect. Evidence: `Json` registers `DomainEvent` subclasses without a custom discriminator and `Created` declares `val type: String`; encoding fails before encryption/insertion per FR-VAULT-024, FR-VAULT-032; SC-VAULT-006, SC-VAULT-009; US1/AC2, US4/AC1, US5/AC1; plan: Phase 5 preserve stored formats (contradicts).
- [X] T053 [F15] Complete the missing aggregate-provider qualifier in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/di/Fido2Module.kt`: `passkeyAggregateService` must be registered with the same `passkey` qualifier requested by `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/CredentialRepositoryImpl.kt`. Regenerate wiring through the build and verify actual resolution of both qualified aggregates with both module orders in the T044 production-composition test. The event-store provider is already qualified; the aggregate provider currently has only `@Single`. T041's existing checkmark is preserved under the append-only contract but its runtime-completion claim is superseded by this finding per FR-VAULT-033; SC-VAULT-009; plan: Phase 5 dependency isolation; T041 (partial).
- [X] T054 [F16] Add `core/data/src/test/kotlin/com/chimali/core/data/eventsourcing/EventStoreRepositoryImplTest.kt` exercising the actual production serializer and event-store implementation, including `Created` for PASSWORD/CREDIT_CARD/NOTE and `Updated`/`Deleted`, append/read and from-sequence round-trips, field/byte preservation, compatible historical fixtures where applicable, malformed event handling, rollback, and buffer cleanup. Assert the current collision fails before the fix and passes after T051–T052. Extend T044's real-storage/application graph regression to create/reopen/edit/delete all types and resolve/use the Passkey service after T053. Execute and record actual results through T050; mock-based aggregate tests and compilation are insufficient evidence per FR-VAULT-024, FR-VAULT-029, FR-VAULT-032, FR-VAULT-033; SC-VAULT-006, SC-VAULT-009; plan: Phase 5 regression verification; Constitution XII.3 (missing).

### Reassessment scope and execution order

The current spec already requires successful CRUD, coexistence, and handled failures; no new spec or plan requirement is needed. This pass reassesses the reported save path and the changed dependency boundaries against FR-VAULT-024/029/032/033/034, SC-VAULT-006/009/010, the corresponding save acceptance scenarios, and Phase 5 serialization compatibility, dependency isolation, boundary handling, and production-path verification decisions. Applicable Constitution X.5, XII.3, XII.5 were checked. Other Phase 10 findings remain outstanding in their existing tasks and are not duplicated here.

Four additional findings: 2 contradicts, 1 partial, 1 missing; 2 CRITICAL and 2 HIGH. Write T054's regression cases first, implement T051–T053, then complete T054/T044 and T050 verification. T043 remains necessary to preserve user input on handled failures. Convergence outcome: tasks_appended; application code, spec, plan, and earlier task markers were not modified by this pass.

## Phase 12: Convergence

Focused reassessment: 2026-09-08, T039 ownership/serialization and related entry/detail behavior. The user explicitly authorized updating `spec.md` and `plan.md` to adopt the preceding recommendation; those amendments precede this convergence section. Application code and all earlier tasks/markers remain unchanged. The previous implementation pass is evidenced by T001–T038 and subsequent checked remediation tasks. This is not a fresh assessment of unrelated feature acceptance.

- [x] T055 **CRITICAL** [F17] Complete the field policy and platform-boundary audit in `research.md` and `data-model.md` under approved Constitution 1.0.0 I.5. Cover every FR-VAULT-026 field and resolved input/rendering/accessibility/clipboard API, including snapshots, undo, saved state, IME, autofill and sharing. Record owners, authorized lifetimes, controls, residual exposure, exact versions/source evidence and synthetic runtime probes; justify the smallest compliant adapter approach. Current widgets are not automatically accepted. Verify app-owned erasure/reference release separately from platform controls; do not claim external-copy erasure. No blanket metadata exception per FR-VAULT-026; SC-VAULT-005; plan 6.1; Constitution I, X.2, X.5.
- [x] T056 **CRITICAL** [F18] Implement explicit mutable draft and submission ownership in `feature/vault/src/main/java/com/chimali/feature/vault/internal/payload/`, typed draft holders under `ui/`, `api/VaultMvi.kt`, and `internal/VaultViewModel.kt`: deep-copy all mutable fields/custom fields for submissions, wipe superseded/removed arrays and any owned comparison baseline, redact diagnostics, clean rejected duplicate and never-started submissions, retain only the active retry draft, and clean on success/discard/disposal. Define stable retry identity, pending edit disabling, and late-result/session handling. Add deterministic ownership/dispatch tests under `src/test/java/com/chimali/feature/vault/ui/` and `internal/VaultViewModelTest.kt` per FR-VAULT-026, FR-VAULT-029, FR-VAULT-031, FR-VAULT-034; SC-VAULT-010–011; US1/AC7; plan: 6.2; Constitution I, X.2, X.5 (contradicts).
- [X] T057 **CRITICAL** [F19] Guard complete crypto ownership in `feature/vault/src/main/java/com/chimali/feature/vault/internal/crypto/VaultCryptoService.kt` and `VaultCryptoServiceImpl.kt`: include key acquisition, all plaintext/scratch allocations, and partial results in cleanup, protect submitted payloads outside dispatcher entry, and erase decoded results if cancellation prevents delivery. Acquire the key before encoding where practical without treating ordering alone as cleanup. Preserve cancellation and safe error mapping. Add nonempty retained-reference failure/cancellation tests for all three types in `src/test/java/com/chimali/feature/vault/internal/crypto/`, coordinating never-started ViewModel jobs with T056 per FR-VAULT-026, FR-VAULT-029; SC-VAULT-011; plan: 6.2–6.4; Constitution I, X.2, X.5, XII.5 (contradicts).
- [X] T058 **CRITICAL** [F20] Replace secret String DTO/full-JSON encoding and decoding in `feature/vault/src/main/java/com/chimali/feature/vault/internal/crypto/VaultCryptoServiceImpl.kt` with a bounded mutable-buffer codec under `internal/crypto/`; document the audited library/codec choice in `specs/053-vault-completion/research.md` and update `contracts/vault-crypto-contract.md`. Preserve the exact existing payload-format contract and key label; wipe scratch, retired backing arrays, partial fields, and pooled storage if used. Ensure error/exception logging does not retain plaintext excerpts. Extend retained-reference tests to encoder/decoder partial failures and growth/replacement per FR-VAULT-022–023, FR-VAULT-026, FR-VAULT-035; SC-VAULT-011–012; plan: 6.3; Constitution I, X.2, X.5 (contradicts).
- [x] T059 **CRITICAL** [F21] Implement the T055-audited platform adapters across the three entry/detail screens and affected list/clipboard flows. Integrate mutable drafts and explicit success/discard/disposal cleanup; prevent retained application secret Strings and unnecessary app-controlled save/restore/history/caching. Audit keyboard learning, autofill, accessibility and disclosure controls; disable unnecessary behavior or explicitly justify and test it. Preserve layouts, reveal/masking, multiline input and user-requested copy. Remediate `VaultItem.title` and other retained application String models or establish separately justified classification; no blanket metadata exception. Platform String adapters must satisfy Constitution 1.0.0 I.5 and are not evidence of external erasure. Keep passkey UI unchanged per FR-VAULT-025–027, 029, 031, 034; SC-VAULT-005/011; plan 6.1–6.2.
- [X] T060 **CRITICAL** [F22] Complete detail/editor lifecycle ownership in `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultNavGraph.kt`, `internal/VaultViewModel.kt`, and the entry/detail screen pairs: transfer an independent draft before clearing details, clear selected/replaced/abandoned payloads and navigation references, freshly decrypt stored details after cancel and after reopening a saved edit, and suppress late completion navigation for abandoned sessions. Extend `src/androidTest/java/com/chimali/feature/vault/ui/navigation/VaultNavigationInstrumentationTest.kt` with actual production graph/screens for all three types, including custom fields, failed save/retry, pending disposal, system back, and recreation. Coordinate clipboard assertions with T046. T048's deep copy is partial evidence, not proof of return/disposal correctness per FR-VAULT-024, FR-VAULT-026, FR-VAULT-034; SC-VAULT-004–006, SC-VAULT-010–011; US3/AC4–AC5; plan: 6.2, 6.4; Constitution I, X.5 (partial).
- [X] T061 **HIGH** [F23] Add independent legacy compatibility fixtures and tests under `feature/vault/src/test/java/com/chimali/feature/vault/internal/crypto/` and `src/test/resources/`: capture synthetic fixtures with the current codec and actual AES implementation before T058, freeze the old format oracle, then validate old payload → new decoder and new encoder → legacy oracle for all types, Unicode/escapes, null/default/empty/missing optional fields, custom fields, unknown fields, and edit/reopen. Keep randomized nonce handling, key derivation/label, envelope, and JSON value types unchanged; do not infer compatibility from only new-code round trips per FR-VAULT-022–023, FR-VAULT-035; SC-VAULT-012; plan: 6.3–6.4 (missing).
- [x] T062 **HIGH** [F24] Execute and record Phase 6 completion evidence in `specs/053-vault-completion/quickstart.md`: run the crypto/draft/ViewModel suites, real Vault navigation tests, Android build/static checks, and T055's dependency-specific platform-control and app-owned memory checks with synthetic secrets. Record commands, versions, coverage required by Constitution XII.3, actual results, and unavailable checks. Verify all SC-VAULT-011 failure/lifetime cases and SC-VAULT-012 fixtures, and reuse T044/T046 for passkey isolation and clipboard timeout. T039 remains open until ownership, compatible codec, audited UI adapter/retention-control implementation, lifecycle, and verification gates all pass; earlier T038/T043/T048/T050 completion markers are not substitute evidence per FR-VAULT-026, FR-VAULT-033–035; SC-VAULT-002, SC-VAULT-005, SC-VAULT-009–012; plan: 6.4 (partial).

### Phase 12 dependencies and scope

- T039 remains the umbrella acceptance item; T055–T062 decompose its remaining work and dependencies rather than authorize a second implementation. Preserve existing markers as history. T060 completes the missing T048 lifecycle evidence; T056/T059/T060 complete the relevant T043 retry/disposal behavior.
- Start T055's field-policy/feasibility assessment and T061's **fixture capture** before changing representations or the old codec. Write ownership/cleanup regressions before fixes. T056 and T057 may proceed while the UI feasibility gate is unresolved, using the conservative sensitive-field policy.
- T058 depends on T057's ownership contract and T061's captured legacy fixtures. Complete T061's new-code compatibility assertions after T058. This split is a capture-then-verification sequence, not a circular dependency.
- T059 depends on T055's successful adapter/control audit under Constitution 1.0.0 and T056. T060 integrates T056/T057/T059; its failing transition tests may be written first. T062 follows all fixes and completed compatibility/lifecycle evidence. The approved platform exception permits only audited boundary copies; unverified adapter controls or app-owned cleanup keep UI/T039 acceptance open.
- Scoped inventory: 11 functional requirements (FR-VAULT-022–027, 029, 031, 033–035), 8 outcomes (SC-VAULT-002, 004–006, 009–012), 5 acceptance scenarios (US1/AC6–AC7, US2/AC4, US3/AC4–AC5), four Phase 6 decision groups, and Constitution I, X.2, X.5, XII.3, XII.5. Code evidence is the current entry/detail, payload, crypto, navigation, ViewModel, and test implementation inspected in the preceding analysis and rechecked for this amendment.
- Eight findings: 4 contradicts, 2 partial, 2 missing, 0 unrequested; 6 CRITICAL and 2 HIGH. Convergence outcome: tasks_appended. This assessment does not assert runtime/heap acceptance or reopen unrelated feature work.


### Phase 12 implementation evidence — 2026-09-08

- T057 complete: submitted payload cleanup encloses dispatcher entry; key acquisition and plaintext cleanup are guarded; cancellation propagates; decoded payloads are erased when dispatcher return cannot deliver ownership. Retained-reference tests cover key-provider/encryption/decryption failures and cancellation, including never-started submission cleanup through the ViewModel.
- T058 complete: `VaultPayloadCodec` replaces String DTO/full-JSON serialization with owned mutable character/UTF-8 buffers. Scratch and partially parsed fields are erased on failure; allocation-failure probes exercise every observed encoder/decoder allocation. Unknown nested values use iterative parsing, and errors omit plaintext excerpts. The existing `VaultItem.title` String projection remains explicitly assigned to T055/T059.
- T061 complete: frozen JSON and real-AES fixtures were captured with the previous codec before replacement. All three types pass old-ciphertext reads, fixed JSON byte comparisons, independent JSON parsing, and edit/reopen tests, with optional/null/empty fields, custom fields, Unicode and malformed-input coverage.
- T055 remains open: the resolved Compose foundation 1.11.1 source audit and `ComposeSecretRetentionTest` demonstrate an immutable String snapshot surviving logical clearing. No compliant input/rendering component has been established; IME/accessibility/autofill/rendering erasure is unproven. Field policy and evidence are recorded in `research.md` and `data-model.md`.
- T056 remains partial: payload arrays, deep copies, redacted diagnostics, custom-field replacement/disposal, rejected/never-started submissions, stable retry identity, pending input disabling and abandoned-session suppression are implemented and host-tested. Entry-screen String state and typed mutable UI draft integration remain subject to T055/T059.
- T059 remains open; current String UI bridges are explicitly noncompliant with its acceptance gate.
- T060 remains partial: independent edit ownership, selected-payload clearing, fresh decryption on detail resume, and abandoned mutation handling are implemented. New `VaultOwnershipNavigationTest` uses the actual graph/screens and real AES for all types, with discard and save-failure/retry/reopen cases. It compiles, but has not run on a device; pending disposal, system-back and recreation coverage remain outstanding.
- T062 and umbrella T039 remain open: 47 host tests pass; Vault Detekt/Ktlint, Android navigation-test compilation and app compilation pass. Measured line/branch coverage is below Constitution XII.3's required threshold. No device is connected for this pass. Exact commands, coverage and remaining checks are in `quickstart.md` section 5. Prior completion markers do not establish UI or device acceptance.


### Phase 12 continuation - 2026-09-08

T056/T060: fixed host-reproduced cancellation races: cancelled providers left editing permanently pending, and abandoned saves could publish stale refreshed items. Matching cancelled submissions now release pending state without resetting retry identity; refresh checks cancellation before publishing. Added all-type system-back/discard instrumentation coverage; it compiles, but no device is connected. Recreation and pending-disposal device cases remain outstanding.

T062: 51 host tests and static/app/navigation compilation checks pass. Crypto-service coverage is 84/84 lines and 16/17 branches; other coverage gaps and UI/device gates remain. See `quickstart.md` for exact results. No additional task is marked complete by this pass.


### Phase 12 detail lifecycle evidence - 2026-09-08

T060: added six passing parameterized host cases covering dismissed late decryption and delivered-payload clearing/ViewModel disposal for all three types, with actual allocation references. Added a compiled all-type pending-save graph disposal test against actual navigation/screens; its delayed completion is non-cooperative to exercise abandonment. Device execution and recreation scenarios remain outstanding.

T062: 58 host tests, static checks, app and navigation-test compilation pass (`.gradle/t039-lifecycle-final.log`). Queued-decryption cancellation is additionally verified to avoid key acquisition and publication. Current coverage and remaining gates are recorded in `quickstart.md`. T039 remains open under plan 6.1/6.4; no UI erasure claim is inferred from these tests.


### Phase 12 mutable draft ownership - 2026-09-08

T056: replaced retained main-field String state in all three entry screens with typed mutable draft owners. Replacement erases retired arrays; independent submissions preserve retry data; discard and confirmed success explicitly clear owned arrays with idempotent disposal fallback. Added retained-reference tests and coverage instrumentation for the new owners. 60 host tests and static/app/navigation compilation checks pass. Earlier notes describing application main-field String state are superseded by this implementation.

T055/T059/T039 remain open: `displayText()` intentionally exposes the remaining widget String boundary; framework/input/rendering/clipboard erasure is not proven or exempted. T056/T060 completion still needs device lifecycle evidence; no new task marker is asserted by the array implementation alone.


### Phase 12 restoration and boundary evidence - 2026-09-08

T060: restored editor routes return to the list instead of accepting a save without the lost edit identity. Plaintext remains absent from saved state. Added all-type StateRestorationTester coverage; actual device execution remains outstanding.

T057/T062: simplified staged decryption ownership transfer without changing its contract; full crypto-service line/branch coverage now passes (83/83, 15/15). New tests cover failed mutable input, erased borrowed slices, oversized serialization before allocation, multiple custom fields and truncated containers. 63 host tests and static/app/navigation compilation checks pass; other coverage gaps are recorded in `quickstart.md`. UI/framework erasure remains gated; a scope-decision request has not amended any requirement.


### Phase 12 codec audit - 2026-09-08

T058/T062: explicit safe parser throws and small escape/Unicode functions preserve error behavior while exposing exception-only paths to instrumentation. Final validated fields now transfer directly without a runtime payload-type ownership switch. Added malformed required-field/truncated-token cleanup cases. 64 host tests and static/app/navigation compilation checks pass; codec coverage is 353/353 lines and 206/208 branches. Remaining defensive branches are recorded, not excluded. UI/framework erasure and device execution still gate T039.


### Phase 12 late callback correction - 2026-09-08

T056/T060: ignored post-closure text callbacks before input inspection/allocation, guarded delayed custom-field/save/discard callbacks, disabled fields after success, and moved dirty-text comparison into typed owners. Null/empty custom-field baselines no longer produce a spurious dirty state. The new late-input regression failed before correction; 65 host tests and static/app/navigation compilation checks pass afterward. UI runtime verification and the strict erasure-scope decision remain outstanding.

### Approved scope amendment - 2026-09-08

The user approved `t039-scope-proposal.md` and Constitution 1.0.0. Current T039/T055/T059/T062 wording and plan 6.1 supersede earlier historical universal-erasure gate notes. T055 now audits narrow platform adapters and retention/disclosure controls; app-owned mutable cleanup, reference release, classified fields, compatibility, critical coverage and real runtime verification remain required. Existing task checkmarks are not new acceptance evidence. T039 remains open while those requirements are implemented and verified.

### Platform adapter continuation - 2026-09-09

T055/T059: clipboard intents now transfer owned mutable buffers and wipe them on all terminal paths, including never-started jobs. Corrected Android's sensitive-clipboard extra after a failing regression. All three entry forms now request restricted keyboard retention via the actual input connection. Masked secret rendering avoids full-secret String conversion; revealed rendering uses only the required AnnotatedString adapter. The current boundary/control audit is in `research.md`.

T062: 72 Vault host tests and 4 focused Android clipboard host tests pass; Vault static checks, Android test compilation and app compilation pass (`.gradle/t039-platform-validation.log`). A new actual-editor input-connection instrumentation test is added but not yet validated. A local managed Android API 35 device is being provisioned to run actual navigation; runtime results are pending. Title ownership through domain events/projections, remaining platform-control audit, coverage and lifecycle/runtime evidence keep T039 open.

T060/T062 runtime update: the API 35 emulator is available and the first 15 navigation cases executed but failed (mostly one-second detail-selection timeouts). A longer-wait retry stalled on Compose idleness and was stopped after capturing thread evidence. Fixtures now use the same outer `MaterialTheme` provider as the app. Focused runtime verification of that correction is pending; see `quickstart.md`. Device availability is no longer the blocker, and no runtime pass is inferred from compilation.

T055/T062 focused runtime result: all 3 actual-editor keyboard-boundary cases pass on API 35 (`.gradle/t039-device-input.log`), confirming configured IME retention flags and omission of initial surrounding text for each entry type. The corrected navigation suite is rerunning separately. Broader ownership, undo/control and coverage gates remain open.

T060/T062 corrected navigation result: all **15/15** actual navigation cases pass on API 35 (`.gradle/t039-device-navigation-themed.log`): discard, system-back discard, failed-save/retry/reopen, pending graph disposal and restoration for each type. T060 remains open for the full ownership/retained-reference and lifecycle acceptance scope; runtime availability and these functional transitions are now verified. Title/event/projection ownership, undo/control audit and critical coverage still keep T039 open.

T059 dependency/T062: snapshot storage now erases acquired key and plaintext byte buffers on every exit; event append erases each serialized byte buffer. Four new regressions failed before correction; all 21 core-data tests and module static checks pass afterward. Immutable metadata models/serialization remain unresolved. Stronger actual-navigation tests now observe real codec/key buffers and check handoff/disposal erasure; their first run passed 14/15 with a password system-back return timeout. A synchronization correction is under integration verification. Exact logs and limitations are in `quickstart.md`.

T060/T062 integration update: the corrected stronger navigation suite passes all **15/15** API 35 cases with retained real codec/key-buffer erasure checks, alongside 72 Vault host tests, Vault static checks and app compilation (`.gradle/t039-ownership-integration-final.log`). Frozen legacy metadata JSON fixtures were captured and pass the current event/snapshot serializers (`.gradle/t039-metadata-legacy-fixtures.log`). They precede, rather than satisfy, the still-open title-model/metadata-serialization remediation.

T059 continuation: `VaultItem`, aggregate commands/events/state and the vault-entry projection now own mutable title buffers. Crypto, repository list transfer and ViewModel list/selection/submission lifetime cleanup erase retired title owners. Migration 5 converts the projection from `TEXT` to UTF-8 `BLOB`; `verifySqlDelightMigration` passes. The custom event/snapshot title serializer retains the exact frozen v1 JSON string format only at its single-call framework boundary and never stores a title `String` in application state.

Runtime evidence: `VaultOwnershipNavigationTest` passes all 15 API 35 parameterized cases after the title migration (`.gradle/t039-vaultitem-title-device.log`). The test storage boundary copies the title, encrypted payload and CRDT arrays on both persistence and read, so detail cleanup has no alias to retained storage or list state.

### T039 completion - 2026-09-09

The user approved the narrowly scoped coverage proposal and Constitution 1.1.0. The final scoped report records complete line coverage and full measurable branch coverage: `VaultCryptoServiceImpl` 83/83 lines and 15/15 branches, `MutableDraftField` 26/26 and 10/10, and `VaultPayloadCodec` 357/357 and 207/208. The sole uncredited branch is the documented direct EOF throw; its independent malformed-input test asserts the exact exception and erased partial buffers, satisfying XII.3's approved terminal-throw rule. The final cross-module command passes database migration verification, domain/data/Vault tests, all listed Ktlint/Detekt checks, app compilation, Vault Android-test compilation, and the scoped report. Focused clipboard timeout host tests also pass. T039 and T062 are complete.

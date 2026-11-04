---
description: "Implementation tasks for Bluetooth transport selection"
---

# Tasks: Bluetooth Transport Selection

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contract](contracts/transport-selection.md), [quickstart.md](quickstart.md)

**Tests**: The specification explicitly requires independent tests and physical ceremonies; project rules require test-first development. Write the relevant tests before implementing each batch and establish the expected failure, then rerun after implementation. Record unavailable prerequisites as unverified, not passed.

**Format**: `- [ ] T### [P?] [US#?] action with file path`. `[P]` means independent files and no unfinished prerequisite within the same parallel batch. Stories share the foundation; no claim of independent delivery is made before it exists.

**Evidence**: Use `specs/064-bluetooth-transport-selection/implementation-evidence.md` with the ten fields in `docs/trd.md` §9.1. Use synthetic data. Feature 058's hosted-work waiver applies; its disabled runner must remain disabled. No task authorizes a stub BLE default, weakening coverage or reactivating hosted infrastructure.

## Phase 1: Setup

**Purpose**: Establish scope, dependency state and durable requirement traceability before product code.

- [X] T001 Record current baseline, source revision, worktree state, external BLE prerequisite owner/revision and availability in `specs/064-bluetooth-transport-selection/implementation-evidence.md`; retain existing user changes and mark unknowns unverified.
- [X] T002 Record the initial availability of the independent `specs/064-fido-ble-transport` implementation in `specs/064-bluetooth-transport-selection/implementation-evidence.md`; after its T018–T019 provide the isolated BLE contract and implementation, review them against FIDO service protocol, authenticated pairing, lifecycle/connection semantics and `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/Fido2Transport.kt`, then record the exact integration revision or blocked status. Complete this review before parent T012–T015; the child feature's physical acceptance T020 follows that parent composition and is not a prerequisite for this review. BLE implementation remains owned by the independent part.
- [X] T003 Allocate unused stable canonical IDs for transport selection, startup, pending/recovery and no-interruption behavior in `docs/brd.md` and `docs/trd.md`; map feature FR-001–012 in `specs/064-bluetooth-transport-selection/implementation-evidence.md` without reusing or removing existing IDs.
- [X] T004 Synchronize the new canonical requirements and evidence obligations in `.agents/rules/specify-rules.md`, retaining Constitution VIII, Classic cross-platform duties and the feature-058 waiver.

**Checkpoint**: Canonical IDs exist before Tier 1 code/tests. Initial T002 availability recording permits independent test design and preference logic; its final isolated BLE review requires the child T018–T019 and precedes integrated composition. Real BLE delivery and physical evidence remain prerequisites for accepting US1/US3. Reviewer-owned items in `specs/064-bluetooth-transport-selection/checklists/transport-lifecycle.md` remain for reviewer assessment; do not mark them as implementation work.

## Phase 2: Foundational (Shared Prerequisites)

**Purpose**: Establish preference storage, read-only state, process ownership and one shared transport handle for all stories.

### Tests first

- [X] T005 [P] Add missing/default and round-trip serialization assertions, unrelated-field preservation and lossless serialization of an unknown transport code without assigning domain validity to `core/common/src/commonTest/kotlin/com/chimali/core/common/datastore/UserPreferencesSerializerTest.kt`.
- [X] T006 [P] Add independent repository tests for atomic save, unchanged timestamp on no-op/failure, preserved fields, serialized saves, typed rejection of unknown stored and input transport codes without overwrite or fallback, and corrupt/I/O error outcomes in `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/repository/TransportPreferenceRepositoryTest.kt`; cite canonical IDs from T003.
- [X] T007 [P] Add process-gate tests for one snapshot before transport construction or hardware, service-first creation without premature use, non-initializing capability check, SelectedNotStarted/Starting states, known unsupported and unknown-capability behavior, concurrent callers, immutable selection, separate snapshot and lazy activation 5s deadlines from their respective start events, cancellation/late cleanup, fresh bounded retries, and zero unselected-fake calls in `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/ProcessTransportSelectionTest.kt`; cite canonical IDs from T003.

### Models and storage

- [X] T008 Extend `core/common/src/commonMain/kotlin/com/chimali/core/common/datastore/UserPreferences.kt` additively: `bluetoothTransport` is “ProtoNumber 9, string, default fido_ble”; `bluetoothTransportSelectedAtEpochMillis` is “ProtoNumber 10, Long, default 0”; preserve existing fields.
- [X] T009 Define transport values and typed preference/availability failures in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/repository/Fido2SettingsRepository.kt`: “Legal values: FIDO_BLE and CLASSIC_HID, persisted as stable explicit strings `fido_ble` and `classic_hid`, never enum ordinals or localized labels”; “an unknown explicit value is a configuration failure, never a reason to activate another transport”; expose read/save and read-only runtime state.
- [X] T010 Implement transactional choice/timestamp operations in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/Fido2SettingsRepositoryImpl.kt`: “Timestamp is nonnegative and informational, not an activation/ordering authority”; “0 means no explicit choice”; “Update both fields atomically, copying the current preferences to preserve fields 1–8”; preserve prior values on failed writes and avoid unrelated legacy-limit migration.

### Runtime and composition

- [X] T011 Implement the process-scoped controller and typed loading/selected-not-started/starting/ready/unavailable state and selected-mode startup capability check that never initializes either transport in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/ProcessTransportSelection.kt`; apply the data-model constraints “processChoice | One immutable startup mode, absent while unresolved”, “availability | Loading, SelectedNotStarted, Starting, Ready, Unavailable(reason)” and “selectedHandle | Controller-owned process instance, never persisted or exposed to settings”; use the specified failure reasons; start a 5s snapshot deadline on snapshot launch and a separate 5s activation deadline on each lazy activation attempt, ending each at success or failure; share concurrent attempts, fence late callbacks, release attempt-owned resources before retry, and keep the frozen choice on activation retry without main-thread blocking.
- [X] T012 Reconcile the selector-facing handle with the delivered BLE lifecycle contract and existing HID state/connection consumers in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/Fido2Transport.kt`; integrate the real separately delivered BLE implementation only after T002 is satisfied, retaining Classic behavior.
- [X] T013 Remove concrete implementation auto-bindings and competing unqualified bindings in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/di/Fido2Module.kt` and `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BluetoothHidTransportImpl.kt`; expose only what the app composition root needs, without adding feature-module concrete bindings or constructing the unselected transport.
- [X] T014 Define any qualified concrete factories and bind one gated selected `Fido2Transport` only at the app composition root, then start its snapshot after DI in `app/src/main/kotlin/com/chimali/ChimaliApplication.kt`; allow service shell creation before snapshot completion but gate selected transport construction, Bluetooth initialization, transport use and preference writes until a valid choice is frozen, including service-first launch.
- [X] T015 Ensure `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/service/Fido2TransportService.kt` and `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/Fido2HomeViewModel.kt` share the same process handle across service restart/reconnect and surface selected-mode failures without fallback.
- [X] T016 Run the focused tests from T005–T007 and existing Classic lifecycle/session-generation tests via `specs/064-bluetooth-transport-selection/quickstart.md`; record commands, revision, outcomes and limitations in `specs/064-bluetooth-transport-selection/implementation-evidence.md`.

**Checkpoint**: Shared foundation and canonical traceability are ready. T012–T016 require the real compatible BLE prerequisite for integrated completion; keep their status open if unavailable. Failures in T016 stop dependent story progression until repaired.

## Phase 3: User Story 1 — FIDO BLE by Default (P1) 🎯 MVP

**Goal**: A fresh installation selects FIDO BLE without configuration and presents the selected/active state.

**Independent test**: On a clean installation, confirm FIDO BLE is selected and complete Windows registration and authentication without opening settings; physical acceptance requires the real BLE prerequisite.

### Tests first

- [X] T017 [P] [US1] Add a settings screen semantics test for both named choices, default saved BLE, loading, selected-not-started, starting and active/unavailable distinction in `feature/settings/src/androidTest/kotlin/com/chimali/feature/settings/ui/SettingsScreenTest.kt`.
- [X] T018 [P] [US1] Add presentation tests for absent preference/default, transient and persistent read failure, disabled selection, retry and storage/relaunch/technical-assistance guidance without data reset, unavailable selected mode and no fallback in `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/presentation/viewmodel/TransportSettingsViewModelTest.kt`; cite canonical IDs.

### Implementation and validation

- [X] T019 [US1] Implement FIDO2 presentation state and intents in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/TransportSettingsViewModel.kt`, deriving saved/process/availability state including SelectedNotStarted and Starting, and `restartRequired = savedChoice != processChoice` when both are resolved; “Unavailable is never presented as active”; avoid inventing a saved choice during loading.
- [X] T020 [US1] Add the Passkey Authenticator > Bluetooth HID section, two accessible radio choices and selected/not-started/starting/active/loading/unavailable text and persistent-read-failure retry and recovery guidance without suggesting app-data clearing to `feature/settings/src/main/kotlin/com/chimali/feature/settings/ui/SettingsScreen.kt`; use localized resources in `feature/settings/src/main/res/values/strings.xml`.
- [X] T021 [US1] Supply ViewModel state/callbacks to the stateless screen through `app/src/main/kotlin/com/chimali/navigation/AppNavGraph.kt` without direct settings-to-concrete-FIDO/storage dependencies.
- [ ] T022 [US1] Run T017–T018 and the clean-install physical BLE registration/assertion procedure in `specs/064-bluetooth-transport-selection/quickstart.md`; record separate host/UI/device outcomes and environment in `specs/064-bluetooth-transport-selection/implementation-evidence.md`.

**Checkpoint**: US1 is accepted only after real BLE registration and authentication succeed; a mock or missing BLE prerequisite leaves it unverified.

## Phase 4: User Story 2 — Switch to Classic HID (P2)

**Goal**: Save Classic while BLE remains active; apply Classic only on full process restart.

**Independent test**: Starting from BLE, select Classic, observe the pending notification, restart the process, then complete Windows registration and authentication over Classic.

### Tests first

- [X] T023 [P] [US2] Add preference save success/failure, pending Classic and rapid/no-op selection assertions to `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/presentation/viewmodel/TransportSettingsViewModelTest.kt`; assert no service/transport side effects.
- [X] T024 [P] [US2] Add pending-versus-active and save-error UI semantics assertions to `feature/settings/src/androidTest/kotlin/com/chimali/feature/settings/ui/SettingsScreenTest.kt`.

### Implementation and validation

- [X] T025 [US2] Implement serialized saves and derived pending state in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/TransportSettingsViewModel.kt`, reporting success only after the repository write and preserving active process state on failure.
- [X] T026 [US2] Present restart instructions, distinct saved/active state and actionable unavailable/save errors in `feature/settings/src/main/kotlin/com/chimali/feature/settings/ui/SettingsScreen.kt` and `feature/settings/src/main/res/values/strings.xml`; no automatic restart or fallback.
- [ ] T027 [US2] Run T023–T024 and the physical BLE-to-Classic restart, registration and assertion procedures in `specs/064-bluetooth-transport-selection/quickstart.md`; record outcome and artifacts in `specs/064-bluetooth-transport-selection/implementation-evidence.md`.

## Phase 5: User Story 3 — Switch Back to FIDO BLE (P2)

**Goal**: Apply the same pending and restart semantics from Classic back to BLE.

**Independent test**: Start with Classic active, save FIDO BLE, restart the process and complete a FIDO BLE ceremony on Windows.

### Tests first

- [X] T028 [P] [US3] Add Classic-to-BLE pending/activation and restored-choice assertions to `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/presentation/viewmodel/TransportSettingsViewModelTest.kt`, including no premature delegate replacement.
- [X] T029 [P] [US3] Add return-to-current-mode cancellation and BLE pending accessibility assertions to `feature/settings/src/androidTest/kotlin/com/chimali/feature/settings/ui/SettingsScreenTest.kt`.

### Implementation and validation

- [X] T030 [US3] Complete symmetric selection and return-to-current pending cancellation in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/TransportSettingsViewModel.kt` and `feature/settings/src/main/kotlin/com/chimali/feature/settings/ui/SettingsScreen.kt`.
- [ ] T031 [US3] Run T028–T029 and the Classic-to-BLE process restart and real Windows ceremony procedures in `specs/064-bluetooth-transport-selection/quickstart.md`; record outcomes in `specs/064-bluetooth-transport-selection/implementation-evidence.md`.

## Phase 6: User Story 4 — Lifecycle and In-Flight Safety (P3)

**Goal**: Preserve preferences across lifecycle events and allow an in-flight ceremony to finish on its original transport.

**Independent test**: Set each choice, perform process death, force-stop and device reboot, verify the restored selection is not started, then start the authenticator and verify the selected mode becomes active; change the preference mid-ceremony and observe uninterrupted completion.

### Tests first

- [X] T032 [P] [US4] Add deterministic bounded in-flight save tests in `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/TransportSelectionCeremonyTest.kt`; cover registration and authentication for each transport with bounded synchronization; assert the original handle/response, pending choice, no disconnect or unauthorized credential/event mutation, and no late response after cancellation.
- [X] T033 [P] [US4] Add process recreation, service restart/reconnect and stored-preference restoration assertions in `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/ProcessTransportSelectionTest.kt`.

### Implementation and validation

- [X] T034 [US4] Close any lifecycle gaps exposed by T032–T033 in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/ProcessTransportSelection.kt` and `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/service/Fido2TransportService.kt`, retaining one process choice and bounded cleanup.
- [ ] T035 [US4] Run T032–T033 plus both-mode force-stop, OS process death, reboot, mid-ceremony save and unavailable-mode recovery procedures from `specs/064-bluetooth-transport-selection/quickstart.md`; document each result and environment in `specs/064-bluetooth-transport-selection/implementation-evidence.md`.

## Phase 7: Polish and Cross-Cutting Concerns

- [X] T036 [P] Update user pairing, process-restart and recovery guidance in `docs/brd.md` and `specs/064-bluetooth-transport-selection/quickstart.md`, preserving existing Classic obligations and identifying the distinct BLE prerequisite.
- [X] T037 [P] Review new Tier 1 declarations/tests against canonical IDs and document bidirectional mapping and missing links in `specs/064-bluetooth-transport-selection/implementation-evidence.md`; correct annotations in affected `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/` files before acceptance.
- [X] T038 Perform TalkBack, high-contrast and 200% scaling review of all transport settings states per `specs/064-bluetooth-transport-selection/quickstart.md`; record actual device evidence or unverified status in `specs/064-bluetooth-transport-selection/implementation-evidence.md`.
- [ ] T039 Run local static analysis and required host/device tests using `tools/local-ci.ps1`; perform candidate-versus-current-accepted-target coverage and raw-count nonregression assessment using `tools/integration-ci.ps1` with trusted harness, exact candidate/target, authorized device, external evidence directory and compatible accepted-target baseline and independently trusted `baselines/current.json` index under the evidence root. Obtain both from trusted accepted-target evidence with matching target/tree/bundle digest and retained relative paths; a supplied baseline hint must match the indexed bundle. If no index exists, use only the applicable reviewed feature-058 baseline establishment/promotion procedure and its prerequisites; measuring the target alone does not create or authorize an index. Record completeness, floors, regression, target identity and hosted-waiver limitations; if any prerequisite is unavailable, record the assessment as unverified rather than passing in `specs/064-bluetooth-transport-selection/implementation-evidence.md`.
- [X] T040 Run `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.performance.SimulatedLatencyAssessmentTest` using `specs/060-fido2-latency-measurement/quickstart.md`; inspect and retain the report with each implemented app-internal FIDO operation, per-operation <200ms decision, synthetic relying party and user-verification substitutions, revision and limitations in `specs/064-bluetooth-transport-selection/implementation-evidence.md`; treat missing/partial assessment as unverified or failed.
- [ ] T041 Run the full acceptance matrix in `specs/064-bluetooth-transport-selection/quickstart.md`, including each switch direction, pre/post synthetic credential and committed-event comparison, both ceremony types on each transport and before/after settings semantics; record SC-001–006 individually with TRD §9.1 evidence fields and physical host/device identity in `specs/064-bluetooth-transport-selection/implementation-evidence.md`; leave missing prerequisite/device/host evidence unverified or deferred.

## Dependencies & Execution Order

Use [integration.md](integration.md) as the shared milestone and ownership guide. The parent gate/composition is delivered before BLE physical acceptance; the independent BLE component is accepted before full parent selection acceptance. Qualified Selection:Txxx and BLE:Txxx references distinguish the task lists.

- Setup T001 and T003–T004 plus T002's initial availability record precede selector Tier 1 code/test changes. The independent BLE part owns its implementation; its isolated contract/implementation review completes T002 after child T018–T019 and before parent T012–T015. The child physical acceptance T020 follows parent composition, so it is not required to complete T002. Real, compatible BLE and physical evidence block integrated story acceptance and any release claiming BLE as default. Focused test-first preference work T005–T011 can progress independently after canonical IDs exist.
- T005–T007 are independent test files; after tests exist, T008–T011 follow their data/runtime dependencies. T012 requires both T011 and the separate BLE implementation. T013–T015 compose the common handle; T016 validates the resulting foundation.
- US1 T017–T022 follows the foundation and provides the minimum demonstrable feature. US2 follows US1's screen/controller. US3 follows US2's save/pending infrastructure. US4 follows the shared lifecycle and both direction flows. Each has its own independently stated acceptance procedure, but stories share these prerequisites and are not fully independent implementations.
- Final phase follows story implementation. T036 and T037 may start together after the final code settles because they have different edit targets; finish both before T039–T041. T038 requires the implemented UI and a suitable device.

### Parallel Examples

- US1: T017 and T018 write different test files after the foundation; T019–T021 integrate sequentially.
- US2: T023 and T024 write different test files after US1; T025 precedes T026–T027.
- US3: T028 and T029 write different test files after US2; T030 precedes T031.
- US4: T032 and T033 write different test files after US3; T034 precedes T035.
- Do not parallelize work on the same ViewModel, screen, shared evidence file or canonical documents merely because different stories refer to them.

## Implementation Strategy

1. Complete setup and test-first foundation, recording the external BLE prerequisite status honestly.
2. Deliver US1 as the MVP only when real FIDO BLE exists and physical Windows registration and authentication succeed. A preference-only increment is preparatory, not an accepted MVP.
3. Add US2, US3 and US4 in order, reusing common storage and process state; validate each independently.
4. Complete accessibility, traceability, local coverage and acceptance evidence. Keep incomplete physical/hosted evidence explicitly unverified and preserve the approved hosted waiver.

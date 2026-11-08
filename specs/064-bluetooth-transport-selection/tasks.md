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
- [X] T022 [US1] Run T017–T018 and the clean-install physical BLE registration/assertion procedure in `specs/064-bluetooth-transport-selection/quickstart.md`; record separate host/UI/device outcomes and environment in `specs/064-bluetooth-transport-selection/implementation-evidence.md`.

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
- [X] T027 [US2] Run T023–T024 and the physical BLE-to-Classic restart, registration and assertion procedures in `specs/064-bluetooth-transport-selection/quickstart.md`; record outcome and artifacts in `specs/064-bluetooth-transport-selection/implementation-evidence.md`.

## Phase 5: User Story 3 — Switch Back to FIDO BLE (P2)

**Goal**: Apply the same pending and restart semantics from Classic back to BLE.

**Independent test**: Start with Classic active, save FIDO BLE, restart the process and complete a FIDO BLE ceremony on Windows.

### Tests first

- [X] T028 [P] [US3] Add Classic-to-BLE pending/activation and restored-choice assertions to `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/presentation/viewmodel/TransportSettingsViewModelTest.kt`, including no premature delegate replacement.
- [X] T029 [P] [US3] Add return-to-current-mode cancellation and BLE pending accessibility assertions to `feature/settings/src/androidTest/kotlin/com/chimali/feature/settings/ui/SettingsScreenTest.kt`.

### Implementation and validation

- [X] T030 [US3] Complete symmetric selection and return-to-current pending cancellation in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/TransportSettingsViewModel.kt` and `feature/settings/src/main/kotlin/com/chimali/feature/settings/ui/SettingsScreen.kt`.
- [X] T031 [US3] Run T028–T029 and the Classic-to-BLE process restart and real Windows ceremony procedures in `specs/064-bluetooth-transport-selection/quickstart.md`; record outcomes in `specs/064-bluetooth-transport-selection/implementation-evidence.md`.

## Phase 6: User Story 4 — Lifecycle and In-Flight Safety (P3)

**Goal**: Preserve preferences across lifecycle events and allow an in-flight ceremony to finish on its original transport.

**Independent test**: Set each choice, perform process death, force-stop and device reboot, verify the restored selection is not started, then start the authenticator and verify the selected mode becomes active; change the preference mid-ceremony and observe uninterrupted completion.

### Tests first

- [X] T032 [P] [US4] Add deterministic bounded in-flight save tests in `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/TransportSelectionCeremonyTest.kt`; cover registration and authentication for each transport with bounded synchronization; assert the original handle/response, pending choice, no disconnect or unauthorized credential/event mutation, and no late response after cancellation.
- [X] T033 [P] [US4] Add process recreation, service restart/reconnect and stored-preference restoration assertions in `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/ProcessTransportSelectionTest.kt`.

### Implementation and validation

- [X] T034 [US4] Close any lifecycle gaps exposed by T032–T033 in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/ProcessTransportSelection.kt` and `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/service/Fido2TransportService.kt`, retaining one process choice and bounded cleanup.
- [X] T035 [US4] Run T032–T033 plus both-mode force-stop, OS process death, reboot, mid-ceremony save and unavailable-mode recovery procedures from `specs/064-bluetooth-transport-selection/quickstart.md`; document each result and environment in `specs/064-bluetooth-transport-selection/implementation-evidence.md`. **Partial BLE-active save evidence and acknowledged assertion responses are in Records 183–184; Settings dismissal with Classic ceremony success is Record 186; Classic authentication with BLE selected before the ceremony is Record 187; the consent-time change attempt in Record 188 ended with Classic still checked; Record 189 tracks the reported consent-prompt loss and Back-handler correction; Record 190 verifies a BLE-active/Classic-pending registration returned to the same consent prompt using Settings' Back button and completed successfully; Record 191 verifies Classic persisted across ASUS force-stop/relaunch, remained stopped until explicit Start, then advertised; Record 192 records an inconclusive physical system-Back retest; Record 193 corrects overlay callback priority; Record 194 verifies system Back closes the overlay with the Classic service retained, without an in-flight ceremony; Record 195 verifies Mi BLE force-stop/relaunch persistence and explicit advertising; Record 196 documents the safe kill request that did not actually kill the foreground process; Record 197 captures a Windows follow-up error with the system-Back route inconclusive; Record 198 verifies Mi BLE retry restored advertising after Bluetooth was toggled off and back on; Record 199 records passing non-coverage local CI; Record 200 initially documents the Mi reboot check as unavailable because the device left ADB; Record 222 later verifies BLE selection persisted and advertising resumed after explicit Start; Record 201 verifies both saved modes survive an ActivityManager background-process kill after the foreground authenticator is stopped; Record 202 verifies Android's sticky BLE foreground service restarts after controlled process-crash injection; Record 203 records repeated physical in-flight BLE system-Back failures without a correlated CTAP trace; Record 204 verifies idle BLE Settings/system-Back dismissal on the current ASUS candidate; Record 205 verifies BLE error guidance and advertiser recovery after radio-off and explicit retry on ASUS; Record 206 strengthens and compiles the pending-ceremony shell-retention regression; Record 207 executes both focused overlay tests successfully on ASUS/API35; Record 208 runs the selected BLE GATT, transport lifecycle and consent-settings device suites (9/9) on ASUS/API35; Record 215 captures a failed physical system-Back retry; Record 217 verifies a successful BLE system-Back authentication retry; and Record 220 verifies Classic GetAssertion succeeded with BLE saved pending, the same sign-in prompt returned after system Back, and BLE was explicitly restored after restart; Record 228 verifies BLE GetAssertion succeeded with Classic saved pending while BLE remained process-active; Record 229 verifies Classic MakeCredential succeeded with BLE saved pending.** All four physical SC-005 ceremony/change combinations now have successful evidence. The requested procedure run is complete; natural OS resource reclamation remains Unverified and Mi/API29 Classic is Unsupported (Record 231). Fresh BLE retry after restoring the saved choice is Record 185. Evidence reconciliation in Record 231 completes the requested procedure run; organic low-memory reclamation remains Unverified and Mi/API29 Classic remains Unsupported.

## Phase 7: Polish and Cross-Cutting Concerns

**T035 continuation**: ASUS BLE force-stop/relaunch preserved BLE as selected but not started until explicit user action; see Record 209. The phone was returned to Ready for Authentication afterward. ASUS Classic force-stop/relaunch likewise preserved the selected mode, stayed stopped until explicit Start, and resumed HID advertising after Android visibility consent; see Record 218. Both ASUS mode-specific force-stop subcases are verified; the remaining lifecycle matrix remains open. Physical in-flight system-Back authentication succeeded on retry; see Record 217.

**System-Back regression follow-up**: The overlay test now uses a real Navigation Compose `NavHost` and verifies the same consent `NavBackStackEntry` remains after system Back. It passed 2/2 on ASUS/API35; see Record 210. A later physical system-Back authentication retry also succeeded in WebAuthn.io with an acknowledged BLE GetAssertion (Record 217); exact consent-screen restoration was not captured.

  **2026-11-08 lifecycle follow-up**: Classic radio-off recovery initially produced inconsistent state: the dashboard showed `Ready to Start` while Settings and Android Bluetooth diagnostics showed Classic active (Record 214). Record 216 corrects the stale Idle/Ready dashboard mapping and verifies Classic Retry restores `Ready for Authentication` on ASUS/API35. BLE was then saved, applied after force-stop/relaunch and explicitly started. One physical in-flight system-Back authentication ended with a Windows follow-up error (Record 215); a later BLE retry succeeded (Record 217). Record 220 now verifies the opposite live-save direction: Classic GetAssertion completed after BLE was saved pending, Android system Back returned to the same sign-in prompt, and BLE was restored after process restart. The final device state is BLE Ready for Authentication; remaining T035 lifecycle cases stay open.

**System gesture follow-up**: An ADB-synthesized left-edge Back gesture closed idle Transport Settings on ASUS/API35 and retained the active BLE foreground service (Record 211). A physical in-flight system-Back authentication later succeeded on retry (Record 217); the broader lifecycle matrix remains open.

**Mi/API29 unavailable Classic recovery**: Classic HID could not bind Android's HID service and timed out; the current build now reports the startup failure with Retry guidance on the dashboard. Saving BLE, restarting and explicitly starting restored advertising (Record 223; BLE Record 201). Classic HID remains unavailable on this Mi device; other T035 lifecycle cases stay open.

**T039 local validation follow-up**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache` passed all non-coverage stages in 206.05 seconds, with 1,156 test cases and no XML-reported failures (Record 224). Coverage completeness/floors and the trusted accepted-target comparison remain unverified as previously deferred.

**Mi current-candidate radio recovery**: On Mi/API29, Bluetooth-off produced actionable Retry guidance; restoring Bluetooth and tapping Retry returned the app to Advertising with its foreground service and BLE registration active (Record 225; BLE Record 203). This adds a scoped idle recovery check only; T035 remains open.

**Unavailable-state mapping regression**: Focused host assertions cover every `TransportUnavailableReason` and verify both its specific dashboard message and Retry action (Record 226; BLE Record 204). Physical T035 checks remain open.

**Classic service restart follow-up**: The Classic HID foreground service restarted after controlled `am crash`, with Android recording `APP CRASH(EXCEPTION)` and the new service returning foreground at `lastStartId=2`. Classic remained process-selected/active; BLE was explicitly restored afterward (Record 212). Natural OS process reclamation and in-flight host recovery remain open.

**Current-build advertiser-permission recovery**: Revoking `BLUETOOTH_ADVERTISE` changed the ASUS process PID; after regrant, the app remained stopped at Ready to Start until explicit Start restored BLE advertising (Record 213). This was idle permission revocation, not an in-flight CTAP interruption.

- [X] T036 [P] Update user pairing, process-restart and recovery guidance in `docs/brd.md` and `specs/064-bluetooth-transport-selection/quickstart.md`, preserving existing Classic obligations and identifying the distinct BLE prerequisite.
- [X] T037 [P] Review new Tier 1 declarations/tests against canonical IDs and document bidirectional mapping and missing links in `specs/064-bluetooth-transport-selection/implementation-evidence.md`; correct annotations in affected `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/` files before acceptance.
- [X] T038 Perform TalkBack, high-contrast and 200% scaling review of all transport settings states per `specs/064-bluetooth-transport-selection/quickstart.md`; record actual device evidence or unverified status in `specs/064-bluetooth-transport-selection/implementation-evidence.md`.
- [ ] T039 Run local static analysis and required host/device tests using `tools/local-ci.ps1`; perform candidate-versus-current-accepted-target coverage and raw-count nonregression assessment using `tools/integration-ci.ps1` with trusted harness, exact candidate/target, authorized device, external evidence directory and compatible accepted-target baseline and independently trusted `baselines/current.json` index under the evidence root. Obtain both from trusted accepted-target evidence with matching target/tree/bundle digest and retained relative paths; a supplied baseline hint must match the indexed bundle. If no index exists, use only the applicable reviewed feature-058 baseline establishment/promotion procedure and its prerequisites; measuring the target alone does not create or authorize an index. Record completeness, floors, regression, target identity and hosted-waiver limitations; if any prerequisite is unavailable, record the assessment as unverified rather than passing in `specs/064-bluetooth-transport-selection/implementation-evidence.md`. **Current-worktree local CI passed for static analysis, compile, unit and Android host checks (Record 236); the exact rebased candidate non-coverage CI also passed (Record 232). Its ASUS/API35 coverage capture ended Incomplete/Failed on six opt-in remote-advertiser/Perfetto preconditions and reported execution-input attribution mismatches. The local index still targets `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`; the canonical archived index is for protected `main` at `f1fd56baf8641cea99edc7fafa8725fdda21044c`. Neither is compatible with target `e7918b6e256319b1305536c7815a92f6132ece99`. Fresh exact-target recapture first failed five opt-in Perfetto/tracing tests and produced no counters or comparable bundle (Record 233). After the Feature 058 filter correction and 520/520 policy pass, FIDO2 passed 41/41 on ASUS. The initial full rehearsal hit app Keystore AEAD; after clearing only the previously authorized `com.chimali.shrunk` package, a later full exact-target manual workload and analysis completed. Repository instructions were 77,209/134,569 (57.38%); Tier 1 instruction/branch percentages were data 87.95%/77.03%, domain 71.33%/71.28%, security 81.40%/66.84%, and FIDO2 64.47%/61.62%. An unclean-state verifier retry failed at app Keystore startup; after resetting only the authorized `com.chimali.shrunk` package, a fresh full `hdkCoverageVerify` run passed app/FIDO2/device/host workloads and configured floors (Record 237). The capture runner now clears and records only the `com.chimali.shrunk` test package before real runs, with 520/520 policy fixtures passing. The harness is still dirty, so immutable capture remains unavailable; accepted index still points to `f1fd56baf8641cea99edc7fafa8725fdda21044c`, not target `e7918b6e256319b1305536c7815a92f6132ece99`. No target bundle/index promotion or candidate comparison exists. Coverage gate remains Unverified; see Record 237 and Feature 058 §11.149.**

  **Target reset-order correction (2026-11-08)**: Clearing `com.chimali` alone did not fix the failure; the instrumentation app is separate `com.chimali.shrunk`. Installing the exact target APK before clearing that isolated package allowed the exact-target `hdkCoverageVerify` workload and floors to pass (77,209/134,569 repository instructions; app 11/11; FIDO2 41/41; zero failed tasks). Feature 058 capture now performs this install-then-clear order; policy fixtures pass 520/520. The run remains manual, the harness dirty, and the accepted index incompatible, so T039 stays open; see Record 239 and Feature 058 §11.151.
- [X] T040 Run `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.performance.SimulatedLatencyAssessmentTest` using `specs/060-fido2-latency-measurement/quickstart.md`; inspect and retain the report with each implemented app-internal FIDO operation, per-operation <200ms decision, synthetic relying party and user-verification substitutions, revision and limitations in `specs/064-bluetooth-transport-selection/implementation-evidence.md`; treat missing/partial assessment as unverified or failed.
- [X] T041 Run the full acceptance matrix in `specs/064-bluetooth-transport-selection/quickstart.md`, including each switch direction, pre/post synthetic credential and committed-event comparison, both ceremony types on each transport and before/after settings semantics; record SC-001–006 individually with TRD §9.1 evidence fields and physical host/device identity in `specs/064-bluetooth-transport-selection/implementation-evidence.md`; leave missing prerequisite/device/host evidence unverified or deferred. **Matrix recorded with partial criteria** in Record 181 and reconciled against later physical ceremonies in Record 227; SC-002 exact credential/event mapping remains partial; all four physical SC-005 ceremony/change combinations are now verified in Records 227–229.

## Dependencies & Execution Order

Use [integration.md](integration.md) as the shared milestone and ownership guide. The parent gate/composition is delivered before BLE physical acceptance; the independent BLE component is accepted before full parent selection acceptance. Qualified Selection:Txxx and BLE:Txxx references distinguish the task lists.

- Setup T001 and T003–T004 plus T002's initial availability record precede selector Tier 1 code/test changes. The independent BLE part owns its implementation; its isolated contract/implementation review completes T002 after child T018–T019 and before parent T012–T015. The child physical acceptance T020 follows parent composition, so it is not required to complete T002. Real, compatible BLE and physical evidence block integrated story acceptance and any release claiming BLE as default. Focused test-first preference work T005–T011 can progress independently after canonical IDs exist.
- T005–T007 are independent test files; after tests exist, T008–T011 follow their data/runtime dependencies. T012 requires both T011 and the separate BLE implementation. T013–T015 compose the common handle; T016 validates the resulting foundation.
- US1 T017–T022 follows the foundation and provides the minimum demonstrable feature. US2 follows US1's screen/controller. US3 follows US2's save/pending infrastructure. US4 follows the shared lifecycle and both direction flows. Each has its own independently stated acceptance procedure, but stories share these prerequisites and are not fully independent implementations.
- Final phase follows story implementation. T036 and T037 may start together after the final code settles because they have different edit targets; finish both before T039–T041. T038 requires the implemented UI and a suitable device.

### Parallel Examples

### Latest T039 Coverage Status (2026-11-08)

The latest full manual ASUS/API35 `hdkCoverageVerify` passed with clean JUnit outcomes (app 13/13; FIDO2 57/57) and all configured floors: repository 89,780/149,027 instructions (60.24%); FIDO2 67,685/100,179 instructions and 4,037/6,660 branches. The routine workload now excludes the five reviewed Perfetto classes and the exact opt-in two-device reconnect-stress class, whose first attempt reported an assumption failure without the separate advertiser. The policy suite passed 520/520. The candidate and harness remain dirty, so this is not an immutable capture. The accepted index still targets incompatible `f1fd56baf8641cea99edc7fafa8725fdda21044c`; no accepted-target raw-count comparison or promotion is claimed. T039 remains open; see Record 240 and Feature 058 §11.152.

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

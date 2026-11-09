# Implementation Evidence: Bluetooth Transport Selection

### Record 248: Fresh Main/Candidate Coverage Pair Passes Floors and Direct Ratios; T039 Remains Unverified
- **requirement**: Parent T039; exact-candidate coverage and Tier 1 raw-ratio nonregression against the current protected-main target.
- **scope**: Complete ASUS/API35 coverage captures for protected main and the Feature 064 candidate, using the same clean Feature 058 harness and matching measurement fingerprint.
- **revision**: Target `f1fd56baf8641cea99edc7fafa8725fdda21044c`, tree `3a41f6da4b81d7d16e3d90afad6f1b0c9d881487`; candidate `82d5d7b002d92cae825094d7f7c79e06bf15c5e5`, tree `d65c753f1e260b444dbfdb40eff1fdfe208ffd48`; harness `82d5d7b002d92cae825094d7f7c79e06bf15c5e5`.
- **named_procedure**: Target Comparison run `b24e1d3c-e0e9-4896-91f6-cb41ea60db23`; Candidate run `5b27c72b-22e5-4e3d-83f7-7cce77321a45`. Both are Complete/Passed with matching fingerprint `ad37d140a261224264b9134c53aecfafe02b7f6a564eab496f6d68e206d2cddb`. Candidate floors pass, including repository instructions 90,437/148,816 (60.771%). Direct raw-count comparison shows all applicable Tier 1 ratios nondecreasing: FIDO2 improved from 54,633/86,070 instructions (63.475%) and 3,367/5,510 branches (61.107%) to 68,342/99,968 instructions (68.364%) and 4,106/6,578 branches (62.420%); data/domain/security Tier 1 counts match.
- **environment**: Windows 11; ASUS AI2302 Android 15/API35 (`R7AIB7002901AZ3`); Gradle 9.6.0; 2026-11-08 local time.
- **expected_observed_result**: Complete matching-fingerprint measurements, passing candidate floors and no Tier 1 raw-ratio regression.
- **limitations**: The canonical accepted index still references run `c1b392a9-f830-4283-83f2-44e36ac5d731` with bundle digest `d71928160a4ce5f3f9a989f2fff6263d8246d9eb8fbbe8c49c88a0d9f9eabfa1`; it does not index fresh target run `b24e1d3c-e0e9-4896-91f6-cb41ea60db23`. No index was modified or promoted. `gh auth status` reports the active GitHub account's token is invalid, so `tools/integration-ci.ps1` was not run.
- **status**: Direct comparison and floors pass as diagnostic evidence. Parent T039 and BLE T038 remain Unverified pending the reviewed accepted-baseline path and valid GitHub authentication.
- **artifact_link**: Self-contained package at `D:\octav\source\repos\.worktrees\chimali\coverage-evidence\064-current-main-comparison-2026-11-09`; raw comparison report `assessments/064-raw-count-review.md`; BLE Record 222.

**Feature**: [spec.md](spec.md) | **Branch**: `064-bluetooth-transport-selection`
**Delivery Coordination**: [integration.md](integration.md)
**Status**: Software implementation and parent-gated BLE composition are present. Settings instrumentation passed 12/12 on both phones and BLE GATT security tests passed 4/4 per phone. The original FIDO2 suites were 50/55 on ASUS/API35 and 47/52 on Mi/API29. A corrected package filter excluded the five controlled trace classes, and the remaining suite passed 50/50 on ASUS and 47/47 on Mi; the trace procedures remain unverified (Record 051; BLE Record 069). After the Mi/API29 radio-off recovery change, ordinary local CI passed KtLint, Detekt, Release lint, compile, unit and configured KMP Android host checks without coverage (Record 057); the focused Nordic advertiser device test passed 1/1 on Mi/API29 (Record 058). The full FIDO2 host suite passed 1,133/1,133 and the dashboard UI class passed 6/6 on each phone (Records 052–053). The parent selection gate test passed 15/15 (BLE Record 069). Earlier ASUS trace import remains incomplete (zero FIDO2 slices and parser errors). Android-only BLE↔Classic saved-selection/restart checks passed, BLE restart after reboot is verified, and the Mi/API29 force-stop/relaunch check confirmed explicit-start gating (Record 054; BLE Record 073). A subsequent Mi BLE-to-Classic attempt failed when Android could not bind the HID Device profile; saving BLE and restarting restored advertising (Record 055; BLE Record 074). A Mi/API29 radio-off test confirms Advertising changes to a retryable error and explicit Retry restores the advertiser (Record 056; BLE Record 075). A permission-revocation sticky-service crash was reproduced and fixed; direct Settings regrant and explicit BLE recovery passed on ASUS (parent Record 039; BLE Record 055). The later Mi/API29 reboot check also passed: BLE remained selected at Ready to Start without an active service, then explicit Start restored advertising (Record 222; BLE Record 200). ASUS TalkBack, Windows ceremonies, mid-ceremony/recovery/stale-bond/50-cycle acceptance and trusted coverage nonregression remain Unverified; coverage remains deferred by owner direction.

---

## 1. Baseline & Worktree State (T001)

- **Worktree Path**: `D:\octav\source\repos\.worktrees\chimali\ofrenda`
- **Branch**: `064-bluetooth-transport-selection`
- **Base Revision**: `27e75da807d9836b01b6f5b3b838403554f49288`
- **Feature Coordination**:
  - *Parent Selection Scope*: Preference storage (`UserPreferences`), repository (`Fido2SettingsRepository`), controller (`ProcessTransportSelection`), settings UI (`Fido2SettingsScreen`), and app composition (`ChimaliApplication`).
  - *Independent BLE Component Scope*: [specs/064-fido-ble-transport](../064-fido-ble-transport/spec.md), providing the real BLE implementation and lazy factory.
- **Unavailable Hardware / Environments**: Target Android device and desktop test hosts (Windows 11, macOS, Linux) are not attached to this headless environment. Runtime checks remain `Unverified`.
- **Constitutional Waivers**: Feature-058 disabled hosted runner waiver applies; local test execution is required.

---

## 2. Independent BLE Prerequisite Availability (T002 — Isolated BLE Review)

- **Independent Component Path**: `specs/064-fido-ble-transport`
- **Reviewed Integration Revision**: `bb8a1d00bda3e192c7b19d263c56c93dfb3ed52e` (working tree)
- **Isolated Review Findings**:
  - *FIDO Service Protocol*: Primary service `0000FFFD-0000-1000-8000-00805F9B34FB` with characteristics `F1D0FFF1`–`F1D0FFF4` conforming to FIDO CTAP 2.2 §11.4; revision bitfield exposes bit 5 (`0x20`) as required.
  - *Authenticated Pairing & Security*: GATT attributes configured with encryption/authentication requirements; unauthenticated write attempts are rejected with `GATT_INSUFFICIENT_AUTHENTICATION`.
  - *Lifecycle & Connection Semantics*: `BleGattTransport` implements peripheral advertising via Nordic Advertiser 1.3.1; host-initiated connection with single active host exclusivity; 3-second receive assembly timeout; 5-second GATT notification completion deadline; cooperative `CMD_CANCEL` bypasses `ERR_BUSY` without sending response packets.
  - *Neutral Transport Contract*: Conforms strictly to `Fido2Transport` interface (`connect()`, `disconnect()`, `connectionState: StateFlow<Fido2ConnectionState>`). Outbound `connectDevice()` correctly fails immediately with typed `UnsupportedOutboundConnectionException`.
- **Review Determination**: SATISFIED. Isolated BLE contract and implementation are compatible and approved for Milestone M2 app-level composition (`Selection:T012–T015`, `BLE:T028`). Physical Windows 11 acceptance (`BLE:T020`, `Selection:T022`) remains deferred to subsequent acceptance milestones.

---

## 3. Requirement Traceability Mapping (T003)

| Local Feature Spec ID | Canonical BRD/TRD mapping | Code and verification evidence | Current evidence |
|---|---|---|---|---|
| **FR-001** (Settings hierarchy) | `FR-BT-SEL-010`; `TR-BT-SEL-040` | `SettingsScreen.kt`; `SettingsScreenTest` | UI test source compiled; screen behavior not executed |
| **FR-002** (Radio options and BLE default) | `FR-BT-SEL-010`; `TR-BT-SEL-010` | `UserPreferences.kt`; repository/ViewModel tests | Host logic passed; clean-install device ceremony unverified |
| **FR-003** (Process and pending indication) | `FR-BT-SEL-010`, `FR-BT-SEL-030`, `FR-BT-SEL-050`; `TR-BT-SEL-040` | `TransportSettingsViewModel`; ViewModel tests and `SettingsScreenTest` | Host state tests passed; Compose test source compiled only |
| **FR-004** (Durable persistence) | `FR-BT-SEL-020`; `TR-BT-SEL-010` | `Fido2SettingsRepositoryImpl`; `TransportPreferenceRepositoryTest`; `UserPreferencesSerializerTest` | Host persistence passed; BLE reboot persistence on Mi and Classic reboot persistence on ASUS passed (Records 064 and 148) |
| **FR-005** (Restart enforcement) | `FR-BT-SEL-030`; `TR-BT-SEL-020` | `ProcessTransportSelection`; `ProcessTransportSelectionTest` | Host lifecycle tests passed; both transport choices remained stopped until explicit Start after reboot/process restart (Records 148–149); OS process-death and remaining recovery procedures open |
| **FR-006** (Cancel pending restart) | `FR-BT-SEL-030`; `TR-BT-SEL-040` | `TransportSettingsViewModelTest`; `SettingsScreenTest` | Host callback passed; Compose test source compiled only |
| **FR-007** (Accessibility) | `FR-BT-SEL-040`; `TR-BT-SEL-040` | `SettingsScreen.kt`; `SettingsScreenTest`; Record 010 | Instrumentation passes at 200% Compose text scale; device high-contrast and largest native scale pass after scrolling; TalkBack announcements remain Unverified |
| **FR-008** (FIDO BLE MakeCredential/GetAssertion) | `FR-BLE-030`, `FR-BLE-050`, `FR-BLE-060`, `FR-BLE-080`; `TR-BLE-010/020/030/040` | GATT, framing, raw CTAP and lifecycle host suites | Host logic passed; physical Windows ceremonies unverified |
| **FR-009** (Classic HID preservation) | `FR-HID-010`; `FR-BT-SEL-010` | Existing `BluetoothHidTransportLifecycleTest` and Classic transport | Host regression suite passed; Windows/macOS/Linux ceremonies unverified |
| **FR-010** (Single gated handle) | `FR-BT-SEL-040`; `TR-BT-SEL-020/030` | `ProcessTransportSelectionTest`;`NeutralTransportContractTest`; app compilation | Host gate tests and app compilation passed |
| **FR-011** (Typed unavailable status/no fallback) | `FR-BT-SEL-050`; `TR-BT-SEL-020/040` | `ProcessTransportSelectionTest`; `TransportSettingsViewModelTest`; `SettingsScreenTest` | Host state tests passed; UI test source compiled only |
| **FR-012** (In-flight ceremony safety) | `FR-BT-SEL-060`; `TR-BT-SEL-040` | `TransportSettingsViewModel.select`; `TransportSelectionCeremonyTest` | Bounded synthetic host tests passed for both modes and ceremony types; physical in-flight matrix unverified |
| **SC-001** (Clean-install BLE default and ceremonies) | `FR-BT-SEL-010`; child `FR-BLE-030/050/060/080` | Preference host tests and BLE host suites; clean-install Windows procedure | Host logic passed; physical requirement unverified |
| **SC-002** (Switching requires restart) | `FR-BT-SEL-030`; `TR-BT-SEL-020` | Process/ViewModel tests both directions | Host logic passed; physical switching/ceremonies unverified |
| **SC-003** (Both modes complete ceremonies) | `FR-BLE-030/050/060/080`; `FR-HID-010` | BLE and Classic host lifecycle/CTAP suites | Host fakes passed; Windows 11 interoperability unverified |
| **SC-004** (Preference survives reboot) | `FR-BT-SEL-020/030`; `TR-BT-SEL-010/020` | Repository and process recreation host tests; physical reboot procedures | Passed: BLE persisted over Mi reboot (Record 064) and Classic persisted over ASUS reboot with selected-not-started state and explicit BLE restoration (Record 148) |
| **SC-005** (In-flight safety) | `FR-BT-SEL-060`; `TR-BT-SEL-040` | `TransportSelectionCeremonyTest` | Synthetic boundary cases passed; real credential/event comparison unverified |
| **SC-006** (Saved/process state shown separately) | `FR-BT-SEL-030/050`; `TR-BT-SEL-040` | `TransportSettingsViewModelTest`; `SettingsScreenTest`; Record 010 | Host state and Settings instrumentation passed; TalkBack remains Unverified |

---

## 4. Evidence Log (TRD §9.1)

### Record 001: Initial Evidence Baseline
- **requirement**: `FR-BT-SEL-010` through `FR-BT-SEL-060`, `TR-BT-SEL-010` through `TR-BT-SEL-040`
- **scope**: Baseline environment, worktree state, and canonical requirement mapping
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288`
- **named_procedure**: `speckit-implement` Phase 1 Setup (Selection:T001, T002, T003, T004)
- **environment**: Windows 11, JDK 17, Android SDK 37, Git worktree `ofrenda`
- **expected_observed_result**: Canonical IDs allocated without collision; traceability matrix initialized; runtime tests not yet executed.
- **date**: 2026-11-02
- **limitations**: Headless agent environment without physical Bluetooth radio or desktop test hosts.
- **status**: `Unverified`
- **artifact_link**: [implementation-evidence.md](implementation-evidence.md)

### Record 002: Transport Selection Foundation (Phase 2)
- **requirement**: `FR-BT-SEL-010`, `FR-BT-SEL-020`, `FR-BT-SEL-030`, `FR-BT-SEL-040`, `FR-BT-SEL-050`, `TR-BT-SEL-010`, `TR-BT-SEL-020`, `TR-BT-SEL-030`
- **scope**: Durable preference serialization, repository transactional persistence, and process controller gating
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` (working tree)
- **named_procedure**: `testAndroidHostTest` on `UserPreferencesSerializerTest`, `TransportPreferenceRepositoryTest`, `ProcessTransportSelectionTest` (Selection:T005–T011)
- **environment**: Windows 11, JDK 17, Gradle 9.6.0, host JVM tests in `:core:common` and `:feature:fido2`
- **expected_observed_result**: Default `fido_ble` selection verified; lossless round-trip and unknown transport serialization passed; atomic preferences update with fields 1–8 preservation confirmed; non-initializing capability check and 5s snapshot/activation deadlines enforced; concurrent callers share single activation attempt; zero unselected fake calls verified; all 17 new tests passed (100% success).
- **date**: 2026-11-02
- **limitations**: Simulated platform capability and transports in host tests; physical Bluetooth hardware and Windows ceremonies remain unverified.
- **status**: `Verified (Host Logic) / Unverified (Physical Hardware)`
- **artifact_link**: [implementation-evidence.md](implementation-evidence.md)

### Record 003: Parent-Gated App Composition and Selector Verification
- **requirement**: `FR-BT-SEL-010`, `FR-BT-SEL-020`, `FR-BT-SEL-030`, `FR-BT-SEL-040`, `FR-BT-SEL-050`, `TR-BT-SEL-010`, `TR-BT-SEL-020`, `TR-BT-SEL-030`
- **scope**: App-only composition of one `Fido2Transport` through the process gate; lazy Classic and FIDO BLE factories; shared service/home process handle; selector saved/process distinction; serialized persistence, retry, pending cancellation and no-fallback states.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus uncommitted worktree changes on `064-bluetooth-transport-selection`.
- **named_procedure**: `:app:compileDebugKotlin :feature:fido2:testAndroidHostTest :feature:fido2:compileAndroidDeviceTest :feature:settings:compileDebugAndroidTestKotlin`.
- **environment**: Windows 11 x64 worktree `ofrenda`; Gradle wrapper 9.6.0; Android SDK 37; no attached Android device (`adb devices` returned no serials).
- **expected_observed_result**: `BUILD SUCCESSFUL`; app code compiled; all 1,112 FIDO2 Android host tests passed with zero failures, errors or skips; lifecycle/security Android device tests and settings Compose tests compiled. The selector tests cover transient/persistent read failure, invalid stored preference, no writes while unresolved, save failure, frozen active mode, both pending-switch directions, cancellation and no-op writes.
- **date**: 2026-11-02
- **limitations**: Android UI and device tests were compiled, not executed. No physical BLE radio, pairing peer or attached device was available. Host tests do not establish system pairing, UI rendering or physical ceremonies.
- **status**: `Passed (app compile and host logic) / Compiled Only (Android UI and device tests) / Unverified (physical acceptance)`.
- **artifact_link**: [ProcessTransportSelection.kt](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/ProcessTransportSelection.kt), [ChimaliApplication.kt](../../app/src/main/kotlin/com/chimali/ChimaliApplication.kt), [TransportSettingsViewModelTest.kt](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/presentation/viewmodel/TransportSettingsViewModelTest.kt), [SettingsScreenTest.kt](../../feature/settings/src/androidTest/kotlin/com/chimali/feature/settings/ui/SettingsScreenTest.kt)

### Record 004: Settings State Semantics and Recovery Guidance
- **requirement**: `FR-BT-SEL-010`, `FR-BT-SEL-030`, `FR-BT-SEL-040`, `FR-BT-SEL-050`, `TR-BT-SEL-020`, `TR-BT-SEL-040`
- **scope**: Settings radio semantics and saved/process labels; Loading, SelectedNotStarted, Starting and unavailable states; read/save retry guidance; full-restart and cancel-pending behavior; BLE pairing recovery and no-fallback user flow.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus uncommitted worktree changes on `064-bluetooth-transport-selection`.
- **named_procedure**: `:feature:settings:compileDebugAndroidTestKotlin`; host `TransportSettingsViewModelTest`; source review of `SettingsScreen.kt` and parent/child quickstarts.
- **environment**: Windows 11 x64, Android SDK 37, Compose Android test compilation; no attached device.
- **expected_observed_result**: Compose test sources compile for BLE default selection, radio selected states, pending versus process state, unavailable-not-active display, read-error retry and guidance, and cancel callback. The settings screen now labels a frozen but unavailable or unstarted choice as “Selected for this process,” never “Active”.
- **date**: 2026-11-02
- **limitations**: Compose tests were not run; TalkBack, contrast, font scaling and touch/focus behavior remain unverified on device.
- **status**: `Compiled Only (UI test source) / Unverified (device accessibility)`.
- **artifact_link**: [SettingsScreen.kt](../../feature/settings/src/main/kotlin/com/chimali/feature/settings/ui/SettingsScreen.kt), [transport_strings.xml](../../feature/settings/src/main/res/values/transport_strings.xml), [parent quickstart](quickstart.md)

### Record 005: Host-Simulated FIDO Operation Latency (T040)
- **requirement**:`NFR-PERF-030`, `TR-PERF-010`, `SC-006`
- **scope**: Ten measured occurrences each of GetAssertion, GetInfo, MakeCredential and ResetAuthenticator under the feature-060 synthetic relying-party and immediate user-verification workload.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus source fingerprint `15df35052a9e764e12ea0065be543283aaa66f6abf1f5c961b69781011660ec9`.
- **named_procedure**: `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.performance.SimulatedLatencyAssessmentTest`; report run ID `f5c1d92c-9bd5-434c-ac71-822bd4a33d15`.
- **environment**: Windows 11 amd64; OpenJDK 21.0.10; JVM `System.nanoTime`; synthetic storage, credential derivation, relying party and user-verification adapters.
- **expected_observed_result**: `PASS`; each measured operation was below 200ms. Maxima: GetAssertion 2.72335ms, GetInfo 8.01045ms, MakeCredential 6.47775ms, ResetAuthenticator 0.19460ms.
- **date**: 2026-11-02
- **limitations**: CTAP transport paths, BLE/HID handoff, production storage and real user verification are not traversed. Build-size/memory checks and all physical timing remain outstanding; this record does not complete BLE T037 or physical acceptance.
- **status**: `Passed (host-simulated internal-operation scope only)`.
- **artifact_link**: [latency assessment JSON](../../feature/fido2/build/reports/fido2/060/simulated-latency-assessment.json), [latency quickstart](../060-fido2-latency-measurement/quickstart.md)

### Record 006: Repository Local CI (Selection Software Verification)
- **requirement**: `FR-BT-SEL-010` through `FR-BT-SEL-060`, `TR-BT-SEL-010` through `TR-BT-SEL-040`
- **scope**: Full repository local CI after parent-gated BLE composition, including KtLint, Detekt, release lint, all configured compilation targets, unit tests and KMP Android host tests.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus uncommitted worktree changes on `064-bluetooth-transport-selection`.
- **named_procedure**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache`; finished successfully on 2026-11-02.
- **environment**: Windows 11 x64; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5; `adb devices` listed no attached device.
- **expected_observed_result**: Full local CI pipeline completed successfully. FIDO2 Android host suite: 1,112 tests, 0 failures, 0 errors, 0 skipped. Parent app, settings UI tests and BLE Android device tests compiled; physical/device test execution was unavailable.
- **date**: 2026-11-02
- **limitations**: Coverage was not requested by this local-CI invocation. The trusted candidate-versus-accepted-target baseline/index assessment in Selection:T039, physical ceremonies, accessibility device checks and hosted integration remain Unverified. Local CI does not satisfy those acceptance requirements.
- **status**: `Passed (local software verification) / Compiled Only (Android UI and device test sources) / Unverified (physical acceptance and trusted coverage integration)`.
- **artifact_link**: [local CI script](../../tools/local-ci.ps1), [parent quickstart](quickstart.md), [BLE integration coordination](integration.md)

### Record 007: In-Flight Ceremony Selection and Cancellation Regression (T032)
- **requirement**: `FR-BT-SEL-030`, `FR-BT-SEL-040`, `TR-BT-SEL-030`
- **scope**: Deterministic bounded host-side ceremony gates over BLE and Classic modes; save a different process choice during registration and authentication; assert the original handle/response, pending saved choice, no disconnect or inactive transport activation, no credential/event mutation before approval, and no mutation or late response after cancellation.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus uncommitted worktree changes on `064-bluetooth-transport-selection`.
- **named_procedure**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache`; full repository local CI completed successfully on 2026-11-02.
- **environment**: Windows 11 x64; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5; no attached Android device.
- **expected_observed_result**: Full local CI passed. FIDO2 Android host suite: 1,114 tests, 0 failures, 0 errors, 0 skipped. The new `TransportSelectionCeremonyTest` exercises both ceremony types in both transport modes for approved and cancelled flows; KtLint, Detekt, release lint and configured compilation/test stages passed.
- **date**: 2026-11-02
- **limitations**: Ceremony routing and side effects are represented by bounded host fakes at the process-selection boundary; production CTAP paths have separate host lifecycle tests. Physical BLE/Classic ceremonies, Android device/UI execution, accessibility and trusted coverage assessment remain Unverified.
- **status**: `Passed (host selection/cancellation regression and local software CI) / Unverified (physical acceptance and trusted coverage integration)`.
- **artifact_link**: [TransportSelectionCeremonyTest.kt](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/TransportSelectionCeremonyTest.kt), [local CI script](../../tools/local-ci.ps1), [parent task list](tasks.md)

## 5. Tier 1 Canonical-to-Code Reverse Index (Selection:T037)

The local FR-001–FR-012 mappings above connect the parent feature wording to canonical identifiers. The independent child matrix in [BLE implementation evidence](../064-fido-ble-transport/implementation-evidence.md#2-requirement-traceability-mapping-t002) supplies the BLE local-to-canonical direction. This reverse index checks canonical IDs back to representative declarations and verification sources in `:feature:fido2`; the declarations and tests use only IDs registered in BRD/TRD.

| Canonical ID | Representative Tier 1 declarations | Verification source |
|---|---|---|
| `FR-BT-SEL-010` | `Fido2SettingsRepository`, `Fido2SettingsRepositoryImpl`, `TransportSettingsViewModel` | `UserPreferencesSerializerTest`, `TransportPreferenceRepositoryTest`, `TransportSettingsViewModelTest` |
| `FR-BT-SEL-020` | `Fido2SettingsRepositoryImpl.saveTransportPreference` | `UserPreferencesSerializerTest`, `TransportPreferenceRepositoryTest` |
| `FR-BT-SEL-030` | `ProcessTransportSelection`, `TransportSettingsViewModel.select` | `ProcessTransportSelectionTest`, `TransportSettingsViewModelTest`, `TransportSelectionCeremonyTest` |
| `FR-BT-SEL-040` | `ProcessTransportSelection`, `Fido2Transport`, `Fido2TransportService` | `ProcessTransportSelectionTest`,`NeutralTransportContractTest`, `FidoBleLifecycleTest` |
| `FR-BT-SEL-050` | `AndroidBluetoothCapabilityChecker`, `TransportSettingsViewModel` | `ProcessTransportSelectionTest`, `TransportSettingsViewModelTest`; settings test source compiled only |
| `FR-BT-SEL-060` | `TransportSettingsViewModel.select` | `TransportSettingsViewModelTest`, `TransportSelectionCeremonyTest` |
| `FR-BLE-010` |`NordicBleAdvertiserController`, `FidoBleGattServer`, `BleGattTransport` | `FidoBleGattContractTest`, `FidoBleLifecycleTest`; physical discovery unverified |
| `FR-BLE-020` | `FidoBleGattServer`, `FidoBleFraming` | `FidoBleGattContractTest`, `FidoBleFramingTest`; radio negotiation unverified |
| `FR-BLE-030` | GATT MITM permissions and authenticated-host admission | `FidoBleGattContractTest`; Android security test compiled only; numeric-comparison ceremony unverified |
| `FR-BLE-040` | `BleSessionOwner`, `BleGattTransport` | `BleSessionOwnerTest`, `FidoBleLifecycleTest` |
| `FR-BLE-050` | `FidoBleFraming`, `BleSessionOwner`, `BleNotificationWriter` | `FidoBleFramingTest`, `FidoBleRecoveryTest`, `BleNotificationWriterTest` |
| `FR-BLE-060` | `BleGattTransport`, `BleNotificationWriter` | `FidoBleAssertionTest`, `FidoBleCancellationTest`, `BleNotificationWriterTest` |
| `FR-BLE-070` | `FidoBleGattServer.rejectUnauthenticatedRequest`, `BleGattTransport.onHostConnectionStateChanged/onHostAuthenticationFailure`, Home recovery and service Retry | `FidoBleGattContractTest`, `FidoBleLifecycleTest`; exact stale-bond causation and physical Forget/Pair/Retry unverified |
| `FR-BLE-080` | `Fido2Transport`, `RawCtapOperation`, `Fido2TransportService` | `RawCtapOperationTest`, `FidoBleLifecycleTest`, parent-gated composition tests |
| `TR-BT-SEL-010` | `UserPreferences`, `Fido2SettingsRepositoryImpl` | `UserPreferencesSerializerTest`, `TransportPreferenceRepositoryTest` |
| `TR-BT-SEL-020` | `ProcessTransportSelection` | `ProcessTransportSelectionTest` |
| `TR-BT-SEL-030` | App composition in `ChimaliApplication`, neutral `Fido2Transport` |`NeutralTransportContractTest`; `:app:compileDebugKotlin` passed |
| `TR-BT-SEL-040` | `TransportSettingsViewModel`, `SettingsScreen` | `TransportSettingsViewModelTest`; physical `feature:settings:connectedDebugAndroidTest` passed 11 tests; TalkBack/high-contrast/200% scaling inspection remains unverified |
| `TR-BLE-010` |`NordicBleAdvertiserController`, `AndroidFidoBleGattServerHost`, `FidoBleGattServer` | GATT/lifecycle host tests; physical Android API/security behavior unverified |
| `TR-BLE-020` | `FidoBleFraming`, `BleSessionOwner`, `BleNotificationWriter` | Framing, session, writer and recovery host suites |
| `TR-BLE-030` | `RawCtapOperation`, shared CTAP handlers | `RawCtapOperationTest`, `FidoBleAssertionTest`, Classic regression tests |
| `TR-BLE-040` | `BleGattTransport`, `Fido2HomeViewModel.retryTransport`, service Retry | Cancellation/recovery/lifecycle host suites; physical recovery unverified |

**Open traceability/acceptance links**: `FR-BLE-070` now covers observable GATT authentication and link failures through cleanup and user guidance, but Android does not expose a reliable callback-level proof that every generic link error was caused by a lost bond. The target stale-bond procedure remains open. The `TransportSelectionCeremonyTest` models the process-selection boundary with synthetic host-operation gates; it does not replace real CTAP/credential/event commit race testing or the physical acceptance matrix. The settings instrumentation suite ran on a physical Android 10 device; FIDO2 device-suite failures and unavailable API 31+/Perfetto checks are detailed in Record 009 and the child evidence. TalkBack/high-contrast/200% scaling review remains open. Coverage floors and changed-Tier-1 raw-count nonregression require the trusted feature-058 integration evidence and remain Unverified.

### Record 008: Traceability Corrections, BLE Authentication Recovery, and Full Local CI (T037)
- **requirement**: `FR-BT-SEL-060`, `FR-BLE-030`, `FR-BLE-070`, `TR-BT-SEL-040`, `TR-BLE-040`
- **scope**: Corrected canonical annotations for in-flight save and BLE pairing recovery; unauthenticated GATT access now clears the session, cancels the link, exposes a recovery error, and remains stopped until explicit Retry; reviewed both local-to-canonical and canonical-to-code/test links.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus uncommitted worktree changes on `064-bluetooth-transport-selection`.
- **named_procedure**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache`; focused FIDO2 KtLint, Detekt, `testAndroidHostTest`, `compileAndroidDeviceTest`, and settings Compose test compilation.
- **environment**: Windows 11 x64; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5; no attached Android device.
- **expected_observed_result**: Full repository local CI passed. FIDO2 Android host suite: 1,116 tests, 0 failures, 0 errors, 0 skipped. Focused format/static checks, Android device-test source compilation and settings Android test-source compilation passed.
- **date**: 2026-11-02
- **limitations**: GATT auth and recovery behavior is host-simulated. Physical stale-bond behavior, Android UI/device execution, Windows ceremonies, accessibility and trusted coverage assessment remain Unverified.
- **status**: `Passed (traceability review and local software verification) / Compiled Only (Android UI/device test sources) / Unverified (physical acceptance and trusted coverage)`.
- **artifact_link**: [FidoBleGattServer.kt](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/FidoBleGattServer.kt), [BleGattTransport.kt](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleGattTransport.kt), [TransportSelectionCeremonyTest.kt](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/TransportSelectionCeremonyTest.kt), [local CI](../../tools/local-ci.ps1)

### Record 009: Physical Settings Instrumentation and Coverage-Gate Attempt (T038–T039)
- **requirement**: `TR-BT-SEL-040`, parent T038 accessibility review, parent T039 local verification/coverage assessment
- **scope**: Configured settings Android instrumentation suite on one attached phone; separate opt-in repository coverage run. Instrumentation UI assertions are not a substitute for TalkBack, contrast or text-scaling inspection.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus uncommitted worktree changes on `064-bluetooth-transport-selection`.
- **named_procedure**: `:feature:settings:connectedDebugAndroidTest`; `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage`.
- **environment**: Windows 11 x64; Xiaomi Mi A2 Android 10 / API 29; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: Settings instrumentation passed 11 tests, 0 failures, 0 errors and 0 skips. The opt-in coverage run passed static analysis, lint, compilation, unit tests and core host suites, then stopped at a single FIDO2 host trace-lifecycle failure (1,116 FIDO2 host tests, 1 failed); an isolated rerun of that test class passed. No coverage counters, inventory, completeness or floor decision was emitted. The corresponding child details are in [child Record 014](../064-fido-ble-transport/implementation-evidence.md#record-014-opt-in-coverage-gate-attempt-t038--unverified).
- **date**: 2026-11-02
- **limitations**: This device run did not exercise accessibility services or review high contrast/text scaling. A targeted rerun does not convert the failed full coverage run into a pass. The worktree contains uncommitted changes and no exact candidate commit was supplied; no trusted external evidence root, compatible accepted-target bundle and independently trusted `baselines/current.json` index were supplied or located in the measured tree. No target-only baseline/index was created. Hosted enforcement remains waived/disabled under feature-058.
- **status**: `Passed (settings instrumentation) / Failed (opt-in coverage run stopped before analysis) / Unverified (accessibility, coverage floors and trusted raw-count regression)`.
- **artifact_link**: [settings device-test XML](../../feature/settings/build/outputs/androidTest-results/connected/debug/TEST-Mi%20A2%20-%2010.xml), [settings screen test](../../feature/settings/src/androidTest/kotlin/com/chimali/feature/settings/ui/SettingsScreenTest.kt), [captured coverage-gate outcome](../../feature/fido2/build/reports/fido2/064/coverage-gate-attempt.json), [coverage plan](../058-enforce-coverage-targets/plan.md), [parent task list](tasks.md)

### Record 010: Settings Accessibility Review and Large-Text Scroll Fix (T038)
- **requirement**: `FR-BT-SEL-040`, `TR-BT-SEL-040`
- **scope**: Settings transport selection and process status, radio options, high contrast, large text, scroll reachability, and remaining page actions.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus uncommitted worktree changes on `064-bluetooth-transport-selection`.
- **named_procedure**: `:feature:settings:connectedDebugAndroidTest --no-configuration-cache --no-problems-report`; install current `shrunkDebug`; inspect Settings on the attached device at its largest native text preset with high-contrast text enabled, then scroll through the transport card; restore original system settings afterward.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; Mi A2 / Android 10 (API 29), display 1080×2160; system font scale 1.3 (largest selectable preset), high-contrast text enabled during inspection; TalkBack disabled after review; system settings restored to font scale 1.0 and high-contrast text disabled.
- **expected_observed_result**: Settings instrumentation passed 12 tests, 0 failures, 0 errors and 0 skips. Added `largeTextCanScrollToTransportStatusAndRemainingSettingsActions`, which supplies Compose font scale 2.0 and scrolls to both “Selected; not started” and “Re-take Onboarding”. Device inspection confirmed that, at font scale 1.3 with high-contrast text, the process selection and availability state become visible by scrolling and the remaining Back/onboarding actions are reachable. Before the scroll fix, the Settings page clipped lower content. The enabled text outline and radio selection remained legible in the captured view.
- **date**: 2026-11-02
- **limitations**: Android 10 exposes no 200% system font preset on this device; 200% was verified in the Compose instrumentation environment, not as a system setting. TalkBack activation opened its first-run tutorial, so app focus order and spoken announcements were not verified. Visual review covered the selected BLE state; dynamic loading/error states were exercised by instrumentation assertions rather than physical visual inspection. Local screenshots: [largest text with high contrast](../../build/accessibility-review/transport-settings-largest-readable-after-fix.png), [scrolled state and page actions](../../build/accessibility-review/transport-settings-largest-readable-after-scroll.png).
- **status**: `Passed (12 Settings instrumentation tests; large-text scrolling; high-contrast and largest native-scale visual check) / Unverified (TalkBack announcements and system-level 200% scaling)`.
- **artifact_link**: [Settings screen](../../feature/settings/src/main/kotlin/com/chimali/feature/settings/ui/SettingsScreen.kt), [instrumentation tests](../../feature/settings/src/androidTest/kotlin/com/chimali/feature/settings/ui/SettingsScreenTest.kt), [parent quickstart accessibility procedure](quickstart.md#accessibility)

### Record 011: User-Confirmed Windows WebAuthn BLE Registration (T022 — Partial)
- **requirement**: `FR-BT-SEL-010`, `FR-BT-SEL-030`, `FR-BT-SEL-040`, `FR-BT-SEL-050`; `SC-001`, `SC-003`; T022, T041
- **scope**: One user-initiated Windows browser registration using the BLE-selected ASUS authenticator after app-level selection composition and installation of the updated `shrunkDebug` build. This contributes partial evidence to the parent clean-install/ceremony requirement; it does not cover transport switching or the full acceptance matrix.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus current uncommitted worktree changes on `064-bluetooth-transport-selection`; installed APK built from this worktree after the GATT bonding-in-progress recovery change.
- **named_procedure**: User-triggered Windows WebAuthn registration with the phone in `Ready for Authentication`; inspect Android app-process logcat and Bluetooth GATT state after the ceremony. Child evidence: [BLE Record 019](../064-fido-ble-transport/implementation-evidence.md).
- **environment**: ASUS_AI2302 / ASUS Zenfone 10, Android 15/API 35, `shrunkDebug`; Windows OS/build, browser/build, Bluetooth adapter and relying-party identifier not captured.
- **expected_observed_result**: User reports the registration succeeded. Android independently logged a successful GATT connection, MTU 517, FIDO2 revision `0x20`, enabled notifications, MakeCredential dispatch, user approval (`success=true`), attestation response encoding/delivery, clean disconnect and advertising recovery. Android returned to `Ready for Authentication / Waiting for request from PC`.
- **date**: 2026-11-03
- **limitations**: The installation was not demonstrated as a clean install. No host-side WebAuthn trace, exact relying party, Windows/browser version or desktop identity was retained. This does not establish default selection after clean install, GetAssertion with the new credential, numeric-comparison rejection, Classic HID, either switch direction, process restart semantics, or the complete SC-001–006 matrix. T022 and T041 remain open.
- **status**: `Passed (one user-confirmed Windows WebAuthn registration over the selected BLE implementation, with matching Android GATT/MakeCredential response evidence) / Unverified (clean-install default, assertion and remaining parent acceptance matrix)`.
- **artifact_link**: [BLE implementation evidence Record 019](../064-fido-ble-transport/implementation-evidence.md), [FIDO GATT server](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/FidoBleGattServer.kt), [transport selection coordinator](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/ProcessTransportSelection.kt), [T022 and T041](tasks.md)

### Record 012: Full Local CI after BLE Advertiser Failure-Boundary Review (T039 — Partial)
- **requirement**: Parent T039; `FR-BT-SEL-010` through `FR-BT-SEL-060`; `TR-BT-SEL-010` through `TR-BT-SEL-040`
- **scope**: Current-tree local static analysis, release lint, compilation, repository unit tests and configured Android host tests after the last BLE exception-boundary change. This advances the local software portion of T039 and includes host-side process gate and selected-handle ceremony checks.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus the current staged worktree and the focused `TooGenericExceptionCaught` suppression on`NordicBleAdvertiserController.startAdvertising`.
- **named_procedure**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache`, with an invocation-local wrapper appending `--no-problems-report` after the ordinary command could not write the existing Gradle problems report. The final run completed in 375.55 seconds.
- **environment**: Windows 11 x64; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; Gradle 9.6.0; Microsoft OpenJDK 25.0.5; Android SDK 37. ASUS Android 15 and Mi Android 10 were connected, but local-ci did not execute connected instrumentation.
- **expected_observed_result**: KtLint, Detekt, release lint, all configured compilation tasks, repository unit tests and configured Android host suites passed. FIDO2 host suite: 1,119/1,119; core:common 13/13; core:security 71/71; core:domain 77/77; each had zero failures, errors or skipped tests. Android device-test source compilation passed.
- **date**: 2026-11-03
- **limitations**: This does not execute connected Android device tests or establish the physical acceptance matrix. Trusted accepted-target coverage baseline/index, exact candidate/target raw-count comparison and integration assessment were not run. T039 and T041 remain open; feature-058's hosted waiver and disabled runner remain unchanged.
- **status**: `Passed (current local static analysis, compilation, repository unit tests and configured Android host tests) / Compiled Only (Android device-test sources) / Unverified (connected device tests, physical acceptance and trusted coverage/nonregression)`.
- **artifact_link**: [local CI script](../../tools/local-ci.ps1), [parent host selection ceremony test](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/TransportSelectionCeremonyTest.kt), [BLE supporting evidence Record 021](../064-fido-ble-transport/implementation-evidence.md), [T039 and T041](tasks.md)

### Record 013: Parent Coverage Gate Prerequisite Audit (T039 — Unverified)
- **requirement**: T039;`NFR-ARCH-050`; TRD §2.2/§9 (coverage floors, evidence and current-target nonregression)
- **scope**: Read-only audit of accepted-target index freshness and exact candidate identity required by the parent coverage gate. No integration pipeline, candidate capture or baseline promotion was run.
- **revision**: Candidate worktree HEAD `c9195ea608ff3394e32aa217e7e640e5579d6f4d` plus 82 staged/unstaged changed paths; local `refs/remotes/origin/main` is `f1fd56baf8641cea99edc7fafa8725fdda21044c`.
- **named_procedure**: Inspect `D:\octav\coverage-local-validation\baselines\current.json`, enumerate current.json files under the local coverage-validation root, inspect local origin/branch ancestry, attempt read-only `git ls-remote origin refs/heads/main`, and compare the local main SHA with GitHub's live `main` ref through the read-only commit comparison connector.
- **environment**: Windows 11; current worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; Git remote `origin` is configured for Chimali.
- **expected_observed_result**: The available accepted-target index and indexed run identify target `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2` and run `0251dac6-c603-4de9-ad19-e0784460e8ec`. No index for `main` was found. A read-only GitHub compare (`base=f1fd56baf8641cea99edc7fafa8725fdda21044c`, `head=main`) returned `identical`, confirming remote `main` is `f1fd56baf8641cea99edc7fafa8725fdda21044c`. Candidate HEAD `c9195ea608ff3394e32aa217e7e640e5579d6f4d` descends from the indexed target but not from current `main`.
- **date**: 2026-11-03
- **limitations**: The live target is confirmed, but the available index is for older target `fa93a40…`, not current `main` `f1fd56b…`. The integration script's exact-commit isolated checkout would also omit current uncommitted feature changes. Therefore floor/completeness/nonregression checks were not performed and no coverage pass is claimed. T039 remains open; the feature-058 hosted-runner waiver is unchanged.
- **status**: `Unverified (live target unavailable, accepted index bound to older target, current implementation not represented by an exact candidate commit)`.
- **artifact_link**: `D:\octav\coverage-local-validation\baselines\current.json`; [current remote main commit](https://github.com/octaviordz/Chimali/commit/f1fd56baf8641cea99edc7fafa8725fdda21044c); [feature-058 implementation evidence](../058-enforce-coverage-targets/implementation-evidence.md); [integration pipeline](../../tools/integration-ci.ps1); [T039](tasks.md)

### Record 014: ASUS Settings Semantics and Large-Text Instrumentation (T038 — Partial)
- **requirement**: T038; `FR-BT-SEL-040`, `TR-BT-SEL-040`
- **scope**: Device-executed Compose settings semantics for transport choices, loading/unavailable/pending/active state, retry/cancel behavior, and scroll access at a 200% Compose font scale. Does not exercise Android TalkBack service announcements or system high-contrast mode.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus current staged worktree; instrumentation ran against the current settings UI sources.
- **named_procedure**: Set `ANDROID_SERIAL=R7AIB7002901AZ3`; run `:feature:settings:connectedDebugAndroidTest -Pandroid.injected.device.serial=R7AIB7002901AZ3 -Pandroid.testInstrumentationRunnerArguments.class=com.chimali.feature.settings.ui.SettingsScreenTest --no-configuration-cache --no-problems-report --max-workers=1`.
- **environment**: ASUS_AI2302 / ASUS Zenfone 10, Android 15/API 35, serial `R7AIB7002901AZ3`, Windows 11, Gradle 9.6.0, Microsoft OpenJDK 25.0.5. TalkBack package is installed but `accessibility_enabled=0`; the settings test task ran only on the ASUS.
- **expected_observed_result**: Gradle reported `Running tests on devices: ASUS_AI2302 - 15`. The XML names ASUS serial `R7AIB7002901AZ3` and reports 12 tests, 0 failures, 0 errors, 0 skipped. Coverage includes default FIDO BLE selected, active-versus-saved Classic state, pending BLE state, loading/unavailable/read-error/retry/cancel states, and the 200% Compose font-scale scroll test. After instrumentation, a read-only check showed `MainActivity` foreground and the production FIDO foreground service/advertiser still active.
- **date**: 2026-11-03
- **limitations**: Compose semantics assertions at an injected 200% font scale do not replace a full system font-scale/high-contrast visual review on this ASUS. TalkBack announcements were not exercised because TalkBack is disabled. The earlier high-contrast/native-scale review is separately documented in Record 010; this record supplements T038 and keeps ASUS TalkBack status Unverified.
- **status**: `Passed (12 ASUS API 35 settings instrumentation cases, including transport states and 200% Compose text-scale scroll; previous device review documented separately) / Unverified (TalkBack on ASUS and ASUS system high-contrast review)`.
- **artifact_link**: [ASUS SettingsScreenTest XML](../../feature/settings/build/outputs/androidTest-results/connected/debug/TEST-ASUS_AI2302%20-%2015.xml), [SettingsScreenTest.kt](../../feature/settings/src/androidTest/kotlin/com/chimali/feature/settings/ui/SettingsScreenTest.kt), [T038](tasks.md)

### Record 015: ASUS FIDO2 Device Suite Outcome (T039 — Partial)
- **requirement**: Parent T039; `FR-BT-SEL-030`, `FR-BT-SEL-040`; BLE lifecycle/security integration boundary
- **scope**: Connected FIDO2 Android device suite on the ASUS API 35 phone, explicitly pinned by serial. This supplements the passing local CI/host-test record and does not cover the full physical selection matrix.
- **revision**: Candidate worktree HEAD `c9195ea608ff3394e32aa217e7e640e5579d6f4d` plus 82 staged/unstaged paths on branch `064-bluetooth-transport-selection`.
- **named_procedure**: Set `ANDROID_SERIAL=R7AIB7002901AZ3`; run `:feature:fido2:connectedAndroidDeviceTest -Pandroid.injected.device.serial=R7AIB7002901AZ3 --no-configuration-cache --no-problems-report --max-workers=1`.
- **environment**: ASUS_AI2302 / ASUS Zenfone 10, Android 15/API 35, serial `R7AIB7002901AZ3`; Windows 11 x64 host; Gradle 9.6.0; Microsoft OpenJDK 25.0.5. Mi A2 was visible to ADB but not selected.
- **expected_observed_result**: Gradle ran only on `ASUS_AI2302 - 15`. The FIDO2 device XML reports 51 tests, 46 passed and 5 failed, no errors or skips. The BLE lifecycle and security classes passed (3/3 and 2/2). Five performance/research experiments failed at their tracing precondition in the generic suite invocation; the report records tracing-related assumption violations/exceptions. Their standalone frozen run procedures were not started.
- **date**: 2026-11-03
- **limitations**: The connected-device suite is not fully green, and no feature-058 trusted coverage/index comparison was run. Existing host ceremony tests verify the parent gate and selected handle with synthetic boundaries; this device run does not certify Windows transport switching or ceremonies. Parent T039 remains open pending the experiment runs and trusted coverage assessment.
- **status**: `Partial (BLE device lifecycle/security passed; 46/51 FIDO2 device cases passed) / Failed (five standalone research/performance experiment cases not configured for this invocation) / Unverified (trusted coverage, Windows switching and full acceptance matrix)`.
- **artifact_link**: [BLE device suite evidence Record 027](../064-fido-ble-transport/implementation-evidence.md), [ASUS connected-device XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [parent T039](tasks.md)

### Record 016: ASUS FIDO2 Suite Follow-up (T039 — Partial)
- **requirement**: Parent T039; connected-device verification and truthful reporting of skips/failures
- **scope**: Diagnostic suite rerun after temporarily moving tracing preconditions outside coroutine scopes; the diagnostic test changes were reverted.
- **revision**: Candidate worktree HEAD `c9195ea608ff3394e32aa217e7e640e5579d6f4d` plus existing staged/unstaged changes; no diagnostic source edits remain.
- **named_procedure**: `:feature:fido2:ktlintAndroidDeviceTestSourceSetCheck --no-configuration-cache --no-problems-report --max-workers=1`; then pinned `:feature:fido2:connectedAndroidDeviceTest` on serial `R7AIB7002901AZ3`.
- **environment**: ASUS_AI2302 / ASUS Zenfone 10, Android 15/API 35, serial `R7AIB7002901AZ3`; Windows 11 x64, Gradle 9.6.0, Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: KtLint passed. The fresh XML still reports 51 tests with 5 tracing-precondition failures, 0 errors and 0 skips, even though Gradle returned success. The five methods report Perfetto/tracing assumptions. The temporary test refactor did not change XML outcomes and was reverted.
- **date**: 2026-11-03
- **limitations**: XML is from the temporary test-source diagnostic and is not a validation artifact for current source. No trustworthy coverage capture or feature-058 accepted-target comparison was performed. Parent T039 remains open.
- **status**: `Passed (targeted KtLint) / Failed (XML contains five failures despite Gradle success) / Unverified (current-source device-suite acceptance and trusted coverage)`.
- **artifact_link**: [BLE diagnostic Record 028](../064-fido-ble-transport/implementation-evidence.md), [ASUS connected-device XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [T039](tasks.md)

### Record 017: Parent Start-Control Recovery After Permission Revocation (T035 — Partial)
- **requirement**: Parent T035; `FR-BT-SEL-050`; supporting BLE `FR-BLE-070`, `TR-BLE-040`
- **scope**: Verify that the parent-facing authenticator Start control routes to transport retry when the selected process gate retains an unavailable snapshot after Bluetooth permission revocation.
- **revision**: Candidate `HEAD` `c9195ea608ff3394e32aa217e7e640e5579d6f4d` plus the dirty worktree containing `Fido2HomeViewModel.toggleTransport()` retry routing and its focused host tests.
- **named_procedure**: ASUS API 35 permission-revocation recovery walkthrough and host mapping tests, detailed in [BLE implementation evidence Record 029](../064-fido-ble-transport/implementation-evidence.md).
- **environment**: ASUS Zenfone 10 / ASUS_AI2302, Android 15/API 35, serial `R7AIB7002901AZ3`, `shrunkDebug`; Windows 11 x64 host. Xiaomi/Mi was not selected or used.
- **expected_observed_result**: With `BLUETOOTH_ADVERTISE` revoked, tapping Start recovered the permission state, returned the UI to `Ready for Authentication`, and restored `Fido2TransportService` foreground state and Chimali advertising. Focused host tests passed for unavailable-state Retry routing and normal Start/Stop mapping.
- **date**: 2026-11-03
- **limitations**: Recovery was exercised through ADB permission revocation; this did not verify system permission prompt presentation, stale-bond guidance, cancellation, Windows GetAssertion, or the full process-death/reboot matrix. Parent T035 and T041 remain open.
- **status**: `Passed (permission-revocation recovery and focused action mapping) / Unverified (remaining parent failure-recovery and physical acceptance matrix)`.
- **artifact_link**: [BLE implementation evidence Record 029](../064-fido-ble-transport/implementation-evidence.md), [focused host test](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/presentation/viewmodel/Fido2HomeViewModelTest.kt), [T035/T041](tasks.md)

### Record 018: Current Parent Local CI and Coverage-Gate Outcome (T039 — Partial)
- **requirement**: Parent T039;`NFR-ARCH-050`, `TR-BT-SEL-050`; child FIDO2 BLE T036/T038
- **scope**: Current-source local static analysis, compilation, repository unit tests, host suites and the ASUS-pinned coverage workload for the parent-gated selection implementation.
- **revision**: Candidate `HEAD` `c9195ea608ff3394e32aa217e7e640e5579d6f4d` plus current dirty worktree on `064-bluetooth-transport-selection`.
- **named_procedure**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage`, using a temporary script copy to append `--no-problems-report`; local `GRADLE_USER_HOME` was `.gradle/codex-user-home`; `ANDROID_SERIAL=R7AIB7002901AZ3`.
- **environment**: Windows 11 x64, Gradle 9.6.0, Android SDK 37, Microsoft OpenJDK 25.0.5; ASUS Zenfone 10 / Android 15/API 35 / serial `R7AIB7002901AZ3`. The attached Mi A2 was not selected.
- **expected_observed_result**: KtLint, Detekt, release lint, compilation, repository unit tests and Android host tests passed; FIDO2 host suite XML reports 1,121/1,121. Coverage stopped when the ASUS FIDO2 device suite recorded 5 tracing-precondition failures among 51 tests; 46 passed, 0 errors, 0 skips. `hdkCoverageVerify` and coverage floors were not reached.
- **date**: 2026-11-03
- **limitations**: This run does not provide coverage completeness/floor evidence or candidate-versus-current-accepted-target raw-count nonregression. The trusted local index remains bound to an older target, and the current candidate is dirty. Feature-058's hosted-runner waiver remains unchanged; parent T039 remains open.
- **status**: `Passed (local source checks, compilation, repository unit and host tests) / Failed (five FIDO2 connected-device research tests lacking their Perfetto preconditions) / Unverified (coverage completeness/floors and accepted-target nonregression)`.
- **artifact_link**: [child current local-CI Record 031](../064-fido-ble-transport/implementation-evidence.md), [BLE disconnect and stale-callback regression Record 032](../064-fido-ble-transport/implementation-evidence.md#record-032-ble-disconnect-and-stale-callback-cleanup-t035t036--software-partial), [ASUS FIDO2 XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [coverage procedure](../../tools/integration-ci.ps1), [T039](tasks.md)

### Record 019: Mi A2 FIDO2 Device Suite Outcome (T039 — Partial)
- **requirement**: Parent T039; child FIDO2 BLE T036; `FR-BT-SEL-030`, `FR-BLE-020`, `FR-BLE-030`, `TR-BLE-010`, `TR-BLE-020`
- **scope**: Run the configured FIDO2 Android device-test suite on the attached Mi A2 as additional API 29 evidence. This does not exercise Windows ceremonies, selected-handle runtime composition or the trusted coverage comparison.
- **revision**: Candidate `HEAD` `c9195ea608ff3394e32aa217e7e640e5579d6f4d` plus current uncommitted Feature 064 changes, including the device-test-only Ed25519 verifier fallback, on `064-bluetooth-transport-selection`.
- **named_procedure**: Set `GRADLE_USER_HOME` to `.gradle/codex-user-home`; run `:feature:fido2:connectedAndroidDeviceTest --no-configuration-cache --no-problems-report --max-workers=1` with only serial `2ac6876` connected. The isolated instrumentation package was `com.chimali.fido2.test`; no clear, uninstall, production reinstall or permission change was issued.
- **environment**: Mi A2 / product `jasmine`, Android 10/API 29, serial `2ac6876`; installed production package `com.chimali.shrunk` remained present and its process remained alive. Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: The initial XML reported 48 tests, 42 passed, 6 failed, including an Ed25519 platform-verifier gap. The test now uses a Bouncy Castle JCA verifier fallback only when Android lacks a platform Ed25519 verifier; the isolated persistence suite passed 2/2, and the updated full-suite XML reports 48 tests, 43 passed, 5 failed, 0 errors and 0 skipped. Both GATT security tests passed. The remaining five research/performance cases failed their Perfetto/tracing preconditions. API-31 BLE service lifecycle tests were excluded on API 29.
- **date**: 2026-11-03
- **limitations**: This device suite is partial and failed; its two GATT tests use mocked devices and do not establish actual pairing or BLE interoperability. The API-29 Ed25519 fallback is test-only and uses the BC JCA API path because no non-BC platform verifier is available. Feature 058 requires an API 35-or-newer device for coverage, so this run cannot count toward parent T039's coverage floors, completeness or nonregression. It does not establish the required ASUS/API 35 suite, parent settings/switch matrix, Windows registration/assertion, or physical recovery. Parent T039 remains open.
- **status**: `Passed (43/48 Mi A2 device tests, including both BLE security and both persisted ceremony tests) / Failed (five Perfetto/tracing preconditions) / Not run (API-31 lifecycle cases on API 29) / Unverified (Windows ceremonies, parent selection acceptance and trusted coverage)`.
- **artifact_link**: [retained full Mi A2 suite XML](../../feature/fido2/build/reports/fido2/064/mi-full-suite-20261103.xml), [retained focused persistence XML](../../feature/fido2/build/reports/fido2/064/mi-ed25519-persistence-focused-20261103.xml), [Mi A2 FIDO2 test report](../../feature/fido2/build/reports/androidTests/connected/androidMain/index.html), [BLE evidence Record 033](../064-fido-ble-transport/implementation-evidence.md#record-033-full-fido2-android-device-suite-on-mi-a2-t036--partial), [parent T039](tasks.md)

### Record 020: Mi A2 Production BLE Advertising Failure (T039 — Partial)
- **requirement**: Parent T039; child FIDO2 BLE T020/T035; `FR-BT-SEL-030`, `FR-BLE-010`, `FR-BLE-060`, `TR-BLE-010`
- **scope**: Open Chimali Authenticator on the Mi A2 and manually start the FIDO BLE authenticator. Verify whether the production app publishes the FIDO service over BLE.
- **revision**: Candidate `HEAD` `c9195ea608ff3394e32aa217e7e640e5579d6f4d` plus current uncommitted Feature 064 changes; installed `shrunkDebug` APK SHA-256 `B80BCEE041872D014D305456FF0C8956B306B61DC4AF5A0EE7BAC0FFAC123B0F`.
- **named_procedure**: Launch `com.chimali.shrunk/com.chimali.MainActivity`, tap Start Authenticator, wait eight seconds, inspect Bluetooth GATT and service state and app/Bluetooth logcat. Android rejected shell start/stop commands against the non-exported service, so no privileged state change occurred.
- **environment**: Mi A2 / product `jasmine`, Android 10/API 29, serial `2ac6876`; Windows 11 x64; Android SDK 37.
- **expected_observed_result**: The FIDO GATT service registered successfully, but `onAdvertisingSetStarted` returned status 18 twice. The app removed its GATT service, reported “Transport connect failed,” and returned to Ready to Start; no Chimali advertising service UUID was active.
- **date**: 2026-11-03
- **limitations**: This is a physical BLE startup failure on Mi, not proof of behavior on all Xiaomi devices. It blocks the Mi portion of parent composition and recovery acceptance. API 29 cannot satisfy the Feature 058 API 35 coverage policy; the coverage gate is deferred and no coverage claim is made. Parent T039 remains open.
- **status**: `Failed (Mi production BLE advertising startup) / Passed (GATT service registration only) / Unverified (Windows interoperability, transport selection composition, recovery and coverage)`.
- **artifact_link**: [BLE device evidence Record 034](../064-fido-ble-transport/implementation-evidence.md#record-034-mi-a2-ble-advertising-startup-failure-t020t035--failed), [Mi parent T039](tasks.md)

### Record 021: Mi A2 Advertising Compatibility Fix (T039 — Partial)
- **requirement**: Parent T039; BLE T020; `FR-BT-SEL-030`, `FR-BLE-010`, `FR-BLE-060`, `TR-BLE-010`
- **scope**: Follow up the Mi A2/API 29 production advertiser failure with an explicit connectable, legacy and scannable Nordic advertising configuration.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current dirty Feature 064 changes; installed shrunkDebug APK SHA-256 `EB33BA14FEE32AC080B9A12A742835D390CC94362F32B7386CF0142EB2AE3A7F`.
- **named_procedure**: Run the focused Nordic advertiser host test; assemble shrunkDebug; install over the existing `com.chimali.shrunk` package with `adb install -r` (no clear/uninstall); tap Start Authenticator; inspect UI, GATT state, service and callback log.
- **environment**: Mi A2 / product `jasmine`, Android 10/API 29, serial `2ac6876`; Windows 11 x64; Gradle 9.6.0; Android SDK 37.
- **expected_observed_result**: The focused test passed. First-device retry revealed that connectable legacy settings require `scannable=true`; after setting it, Android reported advertising start status 0, UI displayed Advertising / Ready to pair, the foreground service remained active and the FIDO GATT service was registered and started. User app data was preserved.
- **date**: 2026-11-03
- **limitations**: Mi startup recovery is verified, but Windows registration/assertion, transport-switch composition, lifecycle stress and trusted coverage remain unverified. API 29 is ineligible for Feature 058 coverage; that gate is deferred as requested.
- **status**: `Passed (Mi FIDO BLE advertising and GATT startup after configuration fix) / Unverified (Windows interoperability, parent transport-selection acceptance and coverage)`.
- **artifact_link**: [BLE recovery evidence Record 035](../064-fido-ble-transport/implementation-evidence.md#record-035-mi-a2-legacy-connectable-advertising-recovery-t020--passed-ble-startup), [parent T039](tasks.md)

### Record 022: Mi A2 Local Advertising Stop/Start Lifecycle (T039 — Partial)
- **requirement**: Parent T039; BLE T035; `FR-BT-SEL-030`, `FR-BLE-010`, `FR-BLE-060`, `TR-BLE-040`
- **scope**: Verify a user-driven stop and restart, then Home/background persistence of the selected BLE authenticator on Mi after the advertiser compatibility correction.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current dirty Feature 064 changes; shrunkDebug APK SHA-256 `EB33BA14FEE32AC080B9A12A742835D390CC94362F32B7386CF0142EB2AE3A7F`.
- **named_procedure**: Tap Stop Authenticator and inspect service/GATT state after two seconds; tap Start Authenticator and inspect advertising callback, FIDO GATT service, service process and UI after five seconds; press Home and verify service/GATT state while Launcher is foreground.
- **environment**: Mi A2 / Android 10/API 29, serial `2ac6876`; Windows 11 x64; Android SDK 37.
- **expected_observed_result**: Stop triggered Android `stopAdvertisingSet()` and removed the transport service and FIDO GATT service. Start restored active advertiser status 0, registered/started FIDO GATT service, foreground service and Advertising UI state. With Launcher foreground after Home, the transport service and registered/started FIDO GATT service remained available. The app was returned to foreground afterward.
- **date**: 2026-11-03
- **limitations**: Single local lifecycle; no host connection, in-flight ceremony, measured Stop <1s timing, 50-cycle stress or reboot/process-death recovery. Mi API 29 cannot satisfy the API 35 coverage policy, and the user deferred that gate.
- **status**: `Passed (Mi app-driven BLE stop/start cleanup, background persistence and recovery) / Unverified (parent physical ceremonies, transport switching, repeated lifecycle recovery and coverage)`.
- **artifact_link**: [BLE lifecycle evidence Record 036](../064-fido-ble-transport/implementation-evidence.md#record-036-mi-a2-app-driven-advertising-stop-and-restart-t035--partial), [parent T039](tasks.md)

### Record 023: Mi A2 Nordic Advertiser Device Regression (T039 — Partial)
- **requirement**: Parent T039; BLE T036; `FR-BT-SEL-030`, `FR-BLE-010`, `TR-BLE-010`
- **scope**: Add a device regression for the API 29 advertiser startup configuration that previously failed on Mi.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current dirty Feature 064 changes.
- **named_procedure**: Run the API-filtered`NordicBleLegacyAdvertisingDeviceTest` on ADB serial `2ac6876`, with the app-owned advertiser stopped to avoid overlap; then restart the production app advertiser.
- **environment**: Xiaomi Mi A2 / Android 10/API 29, serial `2ac6876`; Windows 11 x64; Gradle 9.6.0; Android SDK 37.
- **expected_observed_result**: The test reports 1 test, 0 failures/errors/skips. Chimali production advertising was restored afterward with Android callback status 0 and registered FIDO GATT service.
- **date**: 2026-11-03
- **limitations**: One Mi/API 29 device only; no Windows host ceremony, switch matrix, accepted-target coverage comparison or other-device claim. API 29 cannot satisfy Feature 058 coverage; that gate is deferred.
- **status**: `Passed (Mi Nordic advertiser startup regression) / Unverified (parent acceptance and coverage obligations)`.
- **artifact_link**: [BLE device test evidence Record 037](../064-fido-ble-transport/implementation-evidence.md#record-037-nordic-legacy-advertiser-instrumentation-on-mi-a2-t036--passed), [retained Mi device-test XML](../../feature/fido2/build/reports/fido2/064/mi-connected-suite-post-retry-fix-20261104.xml), [parent T039](tasks.md)

### Record 024: Post-Fix FIDO2 Host and Mi Device Suite (T039 — Partial)
- **requirement**: Parent T039; BLE T036; parent selected-handle composition boundary
- **scope**: Rerun configured FIDO2 Android host and connected-device suites after the Mi advertiser compatibility change and instrumentation regression were added.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes on branch `064-bluetooth-transport-selection`.
- **named_procedure**: Set `GRADLE_USER_HOME=.gradle/codex-user-home`, `ANDROID_SERIAL=2ac6876`; run `:feature:fido2:testAndroidHostTest :feature:fido2:connectedAndroidDeviceTest`. The host suite completed; the device suite completed with failing tracing-precondition tests. Gradle also reported an `AccessDeniedException` while writing the default problems report.
- **environment**: Xiaomi Mi A2 / Android 10/API 29, serial `2ac6876`; Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: Host XML totals 1,126 tests, 0 failures/errors/skips. Device XML totals 49 tests, 5 failures, 0 errors/skips; 44 passed, including`NordicBleLegacyAdvertisingDeviceTest.connectableLegacyAdvertisingStartsOnApi28Through30`. All five failed cases are research/performance tests requiring separately configured Perfetto/tracing. No coverage task ran.
- **date**: 2026-11-03
- **limitations**: T039 remains open. The five tracing tests did not pass, API-31 lifecycle tests are unavailable on API 29, and the Mi is ineligible for Feature 058 coverage. This does not establish Windows ceremonies, BLE/Classic switching or the full parent acceptance matrix.
- **status**: `Passed (FIDO2 host suite, 1,126/1,126; advertiser regression on Mi) / Failed (five tracing preconditions; secondary Gradle problems-report write) / Unverified (trusted coverage and physical parent acceptance)`.
- **artifact_link**: [retained Mi full-suite XML](../../feature/fido2/build/reports/fido2/064/mi-full-suite-after-legacy-advertising-20261103.xml), [host XML directory](../../feature/fido2/build/test-results/testAndroidHostTest), [parent T039](tasks.md)

### Record 025: ASUS API 35 FIDO2 Device Suite and Perfetto Smoke Test (T039 — Partial)
- **requirement**: Parent T039; BLE T036; parent gate/selected-handle composition boundary
- **scope**: Verify the current FIDO2 connected suite and standalone trace lifecycle on the API 35 ASUS after the Mi advertiser compatibility change.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes.
- **named_procedure**: With `ANDROID_SERIAL=R7AIB7002901AZ3`, run the untraced configured `:feature:fido2:connectedAndroidDeviceTest`; separately start a 180-second Perfetto `atrace_apps=com.chimali.fido2.test` session, run the filtered `PerformanceTraceDeviceTest`, stop and pull the trace, and verify `Fido2.%` slices with Trace Processor.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: The full device XML reports 51 tests, 46 passed, 5 failed, 0 errors and 0 skips. All five failures are research/performance tests requiring active tracing or dedicated instrumentation arguments. The separate trace test reports 1/1 passed; Trace Processor found `Fido2.Registration.SettingsLookup` once and its coroutine slice four times. No coverage task ran.
- **date**: 2026-11-03
- **limitations**: This does not complete parent T039, prove the four procedure-specific performance experiments, or satisfy Windows physical ceremonies and switching. The full suite ran without Perfetto, so its trace precondition failures remain failures; API35 only makes the suite eligible for future coverage, not a coverage result. No production app data was cleared or modified.
- **status**: `Passed (46/51 ASUS device tests; separate Perfetto trace test and trace contents) / Failed (five configured-suite tracing preconditions) / Unverified (coverage and physical parent acceptance)`.
- **artifact_link**: [ASUS full-suite XML](../../feature/fido2/build/reports/fido2/064/asus-full-suite-current-20261103.xml), [ASUS trace XML and trace](../064-fido-ble-transport/implementation-evidence.md#record-040-asus-api-35-perfetto-trace-lifecycle-smoke-test-t036--passed-scoped), [parent T039](tasks.md)

### Record 026: Ordinary Local CI on the Current Feature 064 Candidate (T039 — Local Portion Passed)
- **requirement**: Parent T039 local-CI requirement; child BLE T036/T038
- **scope**: Execute ordinary repository CI with fixes disabled after the current Feature 064 implementation, excluding the owner-deferred coverage gate and connected-device tests.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes.
- **named_procedure**: Run a temporary copy of `tools/local-ci.ps1`, adding only `--no-problems-report` to its Gradle command; invoke `-NoFix -NoConfigurationCache` with `GRADLE_USER_HOME=.gradle/codex-user-home`. The temporary copy was removed after the run. No `-RunCoverage` flag was set.
- **environment**: Windows 11 x64; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; Gradle 9.6.0; SDK 37; Microsoft OpenJDK 25.0.5; Mi A2/API 29 and ASUS AI2302/API 35 attached but not exercised by this ordinary run.
- **expected_observed_result**: Local CI completed successfully in 643.35 seconds: KtLint, Detekt, release lint, compile-all (production plus test sources), repository unit tests, and configured core/FIDO2 Android host suites passed. FIDO2 Android host XML totals 1,126 tests with 0 failures/errors/skips. Kotlin daemon marker-file access errors triggered fallback compilation; no compile failure resulted.
- **date**: 2026-11-03
- **limitations**: This result covers the local checks only. T039 remains open for connected suite outcomes, deferred coverage completeness/floors/nonregression, and the hosted waiver record; parent physical T022/T027/T031/T035/T041 remain open.
- **status**: `Passed (ordinary local CI with fixes disabled) / Unverified or deferred (device acceptance, trusted coverage and physical parent acceptance)`.
- **artifact_link**: [local CI script](../../tools/local-ci.ps1), [host XML directory](../../feature/fido2/build/test-results/testAndroidHostTest), [parent T039](tasks.md)

### Record 027: Expanded Nordic Advertiser Test and ASUS FIDO2 Suite (T039 — Partial)
- **requirement**: Parent T039; BLE T036; `FR-BT-SEL-030`, `FR-BLE-010`, `TR-BLE-010`
- **scope**: Rerun the FIDO2 host and ASUS device suites after broadening the Mi-specific Nordic legacy advertiser regression to API 28+ and adding instrumentation Bluetooth runtime grants for API 31+.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes.
- **named_procedure**: Set `GRADLE_USER_HOME=.gradle/codex-user-home` and `ANDROID_SERIAL=R7AIB7002901AZ3`; run `:feature:fido2:ktlintAndroidDeviceTestSourceSetCheck :feature:fido2:testAndroidHostTest :feature:fido2:connectedAndroidDeviceTest --no-configuration-cache --no-problems-report --max-workers=1`.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; Windows 11 x64; Gradle 9.6.0; SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: Device-test KtLint passed. The FIDO2 Android host suite passed 1,126/1,126. The full device XML reports 52 tests, 47 passed, 5 failed, 0 errors and 0 skips; the new `connectableLegacyAdvertisingStartsOnApi28Plus` passed. The five failed cases are research/performance tracing procedures run without their own tracing setup/arguments.
- **date**: 2026-11-03
- **limitations**: T039 remains open. The five tracing failures are not passes. The separately configured Perfetto lifecycle smoke passes in BLE Record 040. Coverage was not run, and this suite proves neither Windows ceremonies nor parent BLE/Classic switch acceptance.
- **status**: `Passed (device-test KtLint, 1,126 host tests, ASUS API 35 advertiser regression) / Failed (five configured-suite tracing procedures) / Unverified (coverage and physical parent acceptance)`.
- **artifact_link**: [ASUS full-suite XML](../../feature/fido2/build/reports/fido2/064/asus-full-suite-after-api28-advertiser-test-20261103.xml), [BLE Record 043](../064-fido-ble-transport/implementation-evidence.md#record-043-api-28-nordic-advertiser-regression-on-asus-api-35-t036--partial), [parent T039](tasks.md)

### Record 028: Focused ASUS Advertiser Recheck (T039 — Partial)
- **requirement**: Parent T039; BLE T036; `FR-BT-SEL-030`, `FR-BLE-010`, `TR-BLE-010`
- **scope**: Rerun the API 28+ Nordic advertiser regression after reconnecting the ASUS, scoped to its instrumentation class.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes.
- **named_procedure**: Set `GRADLE_USER_HOME=.gradle/codex-user-home` and `ANDROID_SERIAL=R7AIB7002901AZ3`; run the filtered `:feature:fido2:connectedAndroidDeviceTest` task for`NordicBleLegacyAdvertisingDeviceTest` with `--no-configuration-cache --no-problems-report --max-workers=1`.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: The focused advertiser test passed, 1/1, with 0 failures/errors/skips.
- **date**: 2026-11-04
- **limitations**: This scoped check does not satisfy the full ASUS connected suite, Windows ceremonies, transport switching, deferred coverage or parent acceptance; parent T039 remains open.
- **status**: `Passed (focused ASUS advertiser recheck) / Unverified (full parent acceptance)`.
- **artifact_link**: [ASUS focused advertiser XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [BLE Record 044](../064-fido-ble-transport/implementation-evidence.md#record-044-focused-nordic-advertiser-recheck-on-asus-api-35-t036--passed-scoped), [parent T039](tasks.md)

### Record 029: ASUS Production Launch Failure and Backup Restore Guard (T039 — Partial)
- **requirement**: Parent T039; parent T035 lifecycle/recovery; `FR-BT-SEL-030`
- **scope**: Reproduce the ASUS production-app launch failure without clearing app state, trace the failure to stored wallet-seed decryption during eager database construction, and prevent future platform backups from restoring local ciphertext without its Android Keystore key.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 and backup-policy changes.
- **named_procedure**: On ASUS/API 35, query package and process state; run `adb shell am start -W -n com.chimali.shrunk/com.chimali.MainActivity`; inspect the crash logcat; then run `:app:processShrunkDebugMainManifest` and `:app:processShrunkDebugResources` with `--no-configuration-cache --no-problems-report --max-workers=1`. Do not clear or uninstall app data.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`, version 0.1.0; Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: Production launch initially failed with `AEADBadTagException` in `EncryptionWrapper.decrypt` while `WalletMasterSeedProvider` decrypts the persisted wallet mnemonic, called by `EncryptedDriverFactory.createDriver` while Koin creates `VaultDatabase` and the vault screen. `am start` returned launch status `ok`, followed by Android's app-crash dialog. The app manifest enabled backup and had no explicit extraction rules. As an inference, restore of ciphertext without the app-scoped Keystore key is consistent with this failure; the exact earlier restore contents were not inspected. The candidate now disables backup and excludes app-private files, databases and preferences from both cloud backup and device transfer. ShrunkDebug manifest processing, resource processing and full `:app:assembleShrunkDebug` passed; `aapt2` inspection confirmed `allowBackup=false` and both compiled rule resources in the candidate APK. After the owner authorized clearing only the synthetic `com.chimali.shrunk` profile, the rebuilt APK was installed and fresh launch/advertising passed (Record 030 and BLE Record 045). Kotlin daemon marker-file access fell back to in-process compilation; the build completed successfully.
- **date**: 2026-11-04
- **limitations**: Backup exclusion prevents this class of mismatch on future backup/restore flows; it did not repair the already-unreadable wallet seed or prove the prior restore payload caused it. The owner-authorized reset erased the synthetic wallet/passkeys for `com.chimali.shrunk`; the unrelated `com.chimali` package and other app data remain untouched. Backup/restore-cycle validation, Windows ceremonies, full lifecycle acceptance and coverage remain unverified.
- **status**: `Failed (initial ASUS production launch against restored test profile) / Passed (backup-disabled shrunkDebug build and fresh-profile launch) / Unverified (backup/restore cycle and full acceptance)`.
- **artifact_link**: [sanitized ASUS crash excerpt](../../feature/fido2/build/reports/fido2/064/asus-production-launch-crash-20261104.txt), [candidate shrunkDebug APK](../../app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk), [fresh-profile launch and advertising (Record 030)](#record-030-asus-fresh-profile-launch-and-advertising-after-authorized-test-reset-t039-partial), [application manifest](../../app/src/main/AndroidManifest.xml), [legacy backup rules](../../app/src/main/res/xml/backup_rules.xml), [Android 12+ extraction rules](../../app/src/main/res/xml/data_extraction_rules.xml), [parent T039](tasks.md)

### Record 030: ASUS Fresh Profile Launch and Advertising After Authorized Test Reset (T039 — Partial)
- **requirement**: Parent T039; parent T035 device lifecycle; child BLE T036; `FR-BT-SEL-030`, `FR-BLE-010`, `TR-BLE-010`
- **scope**: Restore the ASUS's disposable shrunkDebug test installation after the existing encrypted test profile failed Keystore authentication; verify fresh app startup and the BLE advertising state without touching the separate debug package.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 and backup-policy changes.
- **named_procedure**: With owner authorization, run `adb -s R7AIB7002901AZ3 shell pm clear --user 0 com.chimali.shrunk`; install the rebuilt `app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk`; launch `com.chimali.shrunk/com.chimali.MainActivity`; grant Nearby devices and notification prompts; select Passkey Authenticator in onboarding; tap Start Authenticator; inspect the visible status, app process, recent advertising logs and `dumpsys bluetooth_manager`. No PC/security-key dialog was controlled.
- **environment**: ASUS AI2302 (Zenfone 10) / Android 15/API 35, serial `R7AIB7002901AZ3`; package `com.chimali.shrunk` version 0.1.0; Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: Data clear and APK install succeeded. The process remained alive without a new Keystore exception. The app displayed `Advertising...` and `Ready to pair with a new host PC`. Bluetooth manager showed one connectable, scannable legacy advertising set for `com.chimali.shrunk` (appIf 13); two additional ongoing advertising sets belonged to Google Play services. The advertiser log recorded payload size 19 and `StartAdvertisingSet`.
- **date**: 2026-11-04
- **limitations**: The authorized reset erased only the synthetic wallet and passkeys stored by `com.chimali.shrunk`; `com.chimali` and other package data were untouched. This verifies app launch/UI and Bluetooth-manager registration, not independent RF discovery, Windows pairing, MakeCredential/GetAssertion, BLE/Classic switching, reboot/50-cycle recovery, coverage or full acceptance. Coverage remains deferred.
- **status**: `Passed (fresh profile startup and ASUS BLE advertising state) / Unverified (physical ceremonies and complete parent acceptance)`.
- **artifact_link**: [fresh ASUS advertising excerpt](../../feature/fido2/build/reports/fido2/064/asus-fresh-profile-advertising-20261104.txt), [child BLE Record 045](../064-fido-ble-transport/implementation-evidence.md#record-045-fresh-shrunkdebug-profile-advertises-on-asus-api-35-t036--passed-scoped), [parent T039](tasks.md)

### Record 031: ASUS Force-Stop and BLE Restart Recovery Cycle (T039 — Partial)
- **requirement**: Parent T039/T035; BLE T035/T036; `FR-BT-SEL-030`, `FR-BLE-010`, `TR-BLE-010`
- **scope**: Verify one physical Android force-stop/relaunch cycle in the BLE-selected synthetic profile, including advertiser teardown and explicit restart.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 and backup-policy changes.
- **named_procedure**: With `com.chimali.shrunk` advertising, run `adb shell am force-stop com.chimali.shrunk`; verify no app process or app-owned advertising entry; relaunch its main activity and verify `Ready to Start` and selected Authenticator tab; tap Start Authenticator and verify the UI and Bluetooth-manager advertiser return. No user or package other than `com.chimali.shrunk` was force-stopped.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; `com.chimali.shrunk` version 0.1.0; Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: Force-stop terminated the app and removed its BLE set. Cold launch succeeded without a new Keystore exception and showed `Ready to Start` with Authenticator still selected. Starting the service restored the active advertising UI and one connectable, scannable legacy advertiser set for Chimali.
- **date**: 2026-11-04
- **limitations**: One BLE force-stop cycle passed. Reboot, mode switching, in-flight ceremony, mid-ceremony commit, permission/recovery scenarios, 50-cycle memory, Windows MakeCredential/GetAssertion, and full coverage/acceptance remain unverified; T035/T039/T041 remain open.
- **status**: `Passed (one ASUS force-stop/relaunch and BLE recovery cycle) / Unverified (complete acceptance matrix)`.
- **artifact_link**: [ASUS lifecycle excerpt](../../feature/fido2/build/reports/fido2/064/asus-force-stop-relaunch-20261104.txt), [child BLE Record 046](../064-fido-ble-transport/implementation-evidence.md#record-046-asus-ble-force-stop-and-restart-cycle-t035t036--passed-scoped), [parent T035/T039](tasks.md)

### Record 032: ASUS BLE Advertise Permission Denial and Prompt Recovery (T035/T039 — Passed, Scoped)
- **requirement**: Parent T035/T039; BLE T035/T036; `FR-BT-SEL-030`, `FR-BLE-010`, `TR-BLE-010`
- **scope**: Observe a Nearby devices permission denial in Android Settings, Android process-exit reason, runtime permission prompt on app launch and advertiser recovery on the ASUS synthetic BLE profile.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 and backup-policy changes.
- **named_procedure**: While `com.chimali.shrunk` is advertising, open its app details and choose Permissions → Nearby devices → Don't allow; inspect `ApplicationExitInfo`, permission state and advertiser list. Launch Chimali, accept the Android Nearby Devices runtime permission prompt, verify Ready to Start with no active advertiser, tap Start Authenticator, and verify app advertising. The separate `com.chimali` package was not targeted.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; package `com.chimali.shrunk` version 0.1.0; Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: Settings denial changed the permission to denied, removed Chimali’s advertiser (ongoing count 3→2), and Android `ApplicationExitInfo` recorded the previous process exit as `PERMISSION CHANGE` / `permissions revoked`, not an app crash. Launching Chimali invoked Android’s Nearby Devices runtime prompt. After Allow, the app showed `Ready to Start` without advertising; Start Authenticator restored `Advertising...`, `Ready to pair with a new host PC`, app_if 13 / Advertising ID 0, and ongoing count 3.
- **date**: 2026-11-04
- **limitations**: One ASUS/API 35 cycle; the grant was through Android’s runtime permission prompt rather than the Settings Allow option. No in-flight ceremony, Windows ceremonies, reboot/mode switching, coverage or full parent acceptance was tested. T035/T039/T041 remain open; coverage remains deferred.
- **status**: `Passed (Settings permission denial, process exit, runtime-prompt grant and advertising recovery) / Unverified (direct Settings grant and complete acceptance)`.
- **artifact_link**: [sanitized ASUS Settings recovery summary](../../feature/fido2/build/reports/fido2/064/asus-settings-permission-recovery-20261104.txt), [child BLE Record 052](../064-fido-ble-transport/implementation-evidence.md#record-052-asus-settings-denial-and-nearby-devices-prompt-recovery-t035t036--passed-scoped), [parent T035/T039](tasks.md)

### Record 033: Current Parent Host Validation and BLE Device Suites After Retry Fix (T039 — Partial)
- **requirement**: Parent T039 local static/host portion and T035 lifecycle; BLE T036
- **scope**: Revalidate repository checks and selected-transport lifecycle behavior after correcting retry notification ordering, while rerunning both available Android BLE devices.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes, including the service retry fix.
- **named_procedure**: Run a temporary `tools/local-ci.ps1` copy with `--no-problems-report`, passing `-NoFix -NoConfigurationCache` and not passing `-RunCoverage`; then run complete `:feature:fido2:connectedAndroidDeviceTest` on ASUS/API 35 and Mi/API 29, and the focused ASUS `FidoBleLifecycleDeviceTest` class. The temporary script is removed.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5; ASUS AI2302/API 35 (`R7AIB7002901AZ3`); Mi A2/API 29 (`2ac6876`).
- **expected_observed_result**: Ordinary local CI passed in 781.16 seconds with KtLint, Detekt, release lint, compile-all, unit tests, and configured host suites passing; FIDO2 host had 1,126/1,126 passing. Focused ASUS lifecycle tests passed 3/3. ASUS full device suite: 47/52; Mi full device suite: 44/49. The five failures on each device are the same Perfetto/calibration/research test cases run without their configured dedicated trace procedures/arguments. The ASUS retry lifecycle test that failed in the first run passed after the production fix. Coverage was omitted.
- **date**: 2026-11-04
- **limitations**: This is local host/static and BLE device evidence, not completion of Selection T039: Feature 058 coverage and accepted-target nonregression are deferred/unverified, and the required external trusted baseline inputs are not established here. Windows ceremonies, both physical transport switch directions, accessibility and Selection T041 acceptance remain unverified. BLE T036 remains partial due tracing failures and physical acceptance.
- **status**: `Passed (ordinary local CI, FIDO2 host 1,126/1,126 and focused ASUS lifecycle suite) / Failed (five unconfigured tracing cases in each full device run) / Unverified (coverage comparison and full parent acceptance)`.
- **artifact_link**: [ASUS device suite](../../feature/fido2/build/reports/fido2/064/asus-connected-suite-post-retry-fix-summary-20261104.txt), [Mi device suite](../../feature/fido2/build/reports/fido2/064/mi-connected-suite-post-retry-fix-20261104.xml), [ordinary local CI summary](../../feature/fido2/build/reports/fido2/064/local-ci-post-retry-fix-20261104.txt), [BLE Record 048](../064-fido-ble-transport/implementation-evidence.md#record-048-current-local-ci-and-asusmi-connected-device-suites-after-retry-fix-t036--partial), [T039/T041](tasks.md)

### Record 034: Parent Settings Connected UI Suite on ASUS/API 35 (T039/T041 — Partial)
- **requirement**: Parent T039/T041; transport-selection settings semantics and accessibility-test scope
- **scope**: Run the configured settings Android instrumentation suite on the API 35 ASUS after current parent/BLE implementation changes.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes.
- **named_procedure**: Set `ANDROID_SERIAL=R7AIB7002901AZ3` and `GRADLE_USER_HOME=.gradle/codex-user-home`; run `:feature:settings:connectedDebugAndroidTest --no-configuration-cache --no-problems-report --max-workers=1`.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: The connected settings UI instrumentation task completed successfully. The retained XML contains the exact per-test totals.
- **date**: 2026-11-04
- **limitations**: Instrumented settings tests do not establish physical TalkBack/high-contrast compliance, Windows ceremonies, BLE/Classic switch journeys, process restart persistence, in-flight ceremony safety, coverage or full SC-001–006 acceptance. Parent T039/T041 remain open.
- **status**: `Passed (configured ASUS settings Android instrumentation suite) / Unverified (physical accessibility and complete parent acceptance)`.
- **artifact_link**: [ASUS settings connected-test XML](../../feature/fido2/build/reports/fido2/064/parent-settings-connected-api35-20261104.xml), [parent T039/T041](tasks.md)

### Record 035: ASUS Production-Variant Update and BLE Restart After Service Retry Fix (T039 — Partial)
- **requirement**: Parent T039/T035; BLE T036; `FR-BT-SEL-030`, `FR-BLE-010`, `FR-BLE-060`
- **scope**: Build and install the current shrunkDebug app without clearing its local data; check cold launch and BLE startup on the production composition.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus uncommitted Feature 064 changes, including the foreground retry fix.
- **named_procedure**: Run `:app:assembleShrunkDebug --no-configuration-cache --no-problems-report --max-workers=1`; update package with `adb install -r`; launch MainActivity; inspect process, UI and permissions; tap Start Authenticator and verify Bluetooth manager. No `pm clear` or uninstall was run.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; package `com.chimali.shrunk` 0.1.0; Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: Production variant assembled successfully in 8m38s and installed in place. Cold launch succeeded with a live process, Authenticator selection retained, advertiser stopped until explicit Start, and no new Keystore crash. Explicit Start restored `Advertising...`, `Ready to pair with a new host PC` and one app-owned advertising set.
- **date**: 2026-11-04
- **limitations**: No data clear was performed for this update. This is Android UI/Bluetooth-manager evidence only; no Windows ceremony, transport switch, reboot/stress, coverage or full acceptance was performed. The parent coverage and SC-001–006 obligations remain open.
- **status**: `Passed (in-place production-variant update and BLE restart on ASUS) / Unverified (physical host behavior and complete parent acceptance)`.
- **artifact_link**: [ASUS in-place update evidence](../../feature/fido2/build/reports/fido2/064/asus-retry-fix-shrunkdebug-reinstall-20261104.txt), [BLE Record 049](../064-fido-ble-transport/implementation-evidence.md#record-049-asus-shrunkdebug-in-place-update-and-ble-restart-after-retry-fix-t036--passed-scoped), [parent T039/T041](tasks.md)

### Record 036: ASUS shrunkDebug Reset and BLE Radio Toggle Recovery (T035/T039 — Partial)
- **requirement**: Parent T035/T039; BLE T035/T036; `FR-BT-SEL-030`, `FR-BLE-010`, `TR-BLE-010`
- **scope**: Recover the synthetic ASUS profile from the saved-wallet launch failure and verify that BLE advertising is active after a Bluetooth adapter off/on interruption.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus uncommitted Feature 064 changes.
- **named_procedure**: After owner approval, clear data only for `com.chimali.shrunk`; initialize the Passkey Authenticator feature, grant the required Android runtime prompts, and start it. Verify Android Bluetooth state, UI and ongoing advertising after toggling Bluetooth off and on. Leave `com.chimali` untouched.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; Windows 11 x64; package `com.chimali.shrunk` 0.1.0.
- **expected_observed_result**: Android recorded the data-clear process exit as a user-requested stop, not a crash. After first-run setup and explicit start, the UI showed `Advertising...` / `Ready to pair with a new host PC` and the Bluetooth manager showed one Chimali advertising set plus two unrelated Google Play services sets. Following the radio off/on cycle, Bluetooth was on, the process was alive, the same advertising UI was visible and the Chimali advertising set was again listed.
- **date**: 2026-11-04
- **limitations**: This does not cover full reboot, in-flight ceremony, Bluetooth transport-mode selection/switching, Windows ceremonies, stale-bond recovery, 50-cycle memory, coverage or parent SC-001–006. The reset applied only to the synthetic package. T035/T039/T041 remain open; coverage remains deferred.
- **status**: `Passed (scoped profile reset/setup and post-toggle BLE recovery) / Unverified (remaining parent lifecycle and host acceptance)`.
- **artifact_link**: [ASUS reset and radio recovery summary](../../feature/fido2/build/reports/fido2/064/asus-reset-radio-recovery-20261104.txt), [BLE Record 053](../064-fido-ble-transport/implementation-evidence.md#record-053-asus-shrunkdebug-data-reset-and-bluetooth-radio-toggle-recovery-t035t036--passed-scoped), [parent T035/T039](tasks.md)

### Record 037: ASUS Reboot Persistence and Explicit BLE Restart (T035/T039 — Partial)
- **requirement**: Parent T035/T039; BLE T035/T036; `FR-BT-SEL-030`, `FR-BLE-010`, `FR-BLE-060`
- **scope**: Verify selected FIDO BLE mode persists over an ASUS reboot and the authenticator is explicitly restarted after unlock.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus uncommitted Feature 064 changes.
- **named_procedure**: Reboot ASUS/API 35 while `com.chimali.shrunk` is advertising. Wait for adb, unlock the phone, launch the app, inspect whether the saved Authenticator choice persists and advertising remains stopped until explicit Start; then verify restored advertising.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; synthetic package `com.chimali.shrunk` 0.1.0; Windows 11 x64.
- **expected_observed_result**: After the user unlocked the rebooted device, MainActivity launched with the Authenticator selection preserved and `Ready to Start`; no Chimali advertiser was present until Start Authenticator was pressed. Explicit Start returned the UI to `Advertising...` / `Ready to pair with a new host PC`; Bluetooth manager listed one Chimali advertising set plus two unrelated Google Play services sets.
- **date**: 2026-11-04
- **limitations**: One reboot/restart cycle; this does not cover mode switching, in-flight ceremony/reboot, Windows registration/assertion, 50-cycle memory, coverage or SC-001–006. Parent T035/T039/T041 remain open; coverage remains deferred.
- **status**: `Passed (post-reboot selection persistence and explicit BLE advertising restart on ASUS) / Unverified (remaining parent acceptance matrix)`.
- **artifact_link**: [ASUS reboot persistence and restart summary](../../feature/fido2/build/reports/fido2/064/asus-reboot-ble-restart-20261104.txt), [BLE Record 054](../064-fido-ble-transport/implementation-evidence.md#record-054-asus-reboot-persistence-and-explicit-ble-restart-t035t036--passed-scoped), [parent T035/T039](tasks.md)

### Record 038: ASUS BLE/Classic Saved Selection and Restart (T027/T031 — Partial)
- **requirement**: Parent T027/T031/T041; `FR-BT-SEL-010`, `FR-BT-SEL-020`, `FR-BT-SEL-030`, `FR-BT-SEL-040`, `FR-BT-SEL-050`
- **scope**: Exercise the Android app's saved-versus-active transport selection and both process-restart directions on ASUS, and verify the selected Android transport registers without simultaneously activating the other mode.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus uncommitted Feature 064 changes.
- **named_procedure**: On an initialized `com.chimali.shrunk` app advertising BLE, open Authenticator Settings and save Bluetooth HID (Classic). Verify the screen reports restart required and active BLE remains unchanged. Select BLE to cancel the pending choice and confirm the notice clears. Save Classic again, force-stop the package and explicitly launch MainActivity; verify Classic is selected and stopped, then start it and inspect Android's HID-device registration and BLE advertiser count. Save BLE, force-stop/relaunch MainActivity, verify BLE is selected and stopped, then Start and verify its advertising set. Do not interact with Windows/WebAuthn dialogs.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; Windows 11 x64 (no host ceremony); synthetic package `com.chimali.shrunk` 0.1.0.
- **expected_observed_result**: Saving Classic displayed `A restart is required to use the saved choice` while active mode remained FIDO Bluetooth LE and its BLE advertiser stayed active. Selecting BLE cleared the pending-change notice. Saving Classic again and restarting preserved Classic selection, showed `Ready to Start`, and did not start Chimali BLE advertising. After Start, Android requested 120-second discoverability; after Allow, Bluetooth logs reported HID app registration and `BluetoothHidTransport connected and advertising`; Bluetooth manager had no Chimali BLE advertiser. Saving BLE and restarting then showed `Ready to Start` with BLE selected and no Chimali advertiser; explicit Start restored `Advertising...` / `Ready to pair with a new host PC` and one Chimali BLE advertising set (app_if 13; total count 3, including two unrelated Google Play services sets).
- **date**: 2026-11-04
- **limitations**: This verifies Android selection persistence, restart gating, pending-choice cancellation and one activation per transport only. It does not verify physical Windows discovery, pairing/numeric comparison, MakeCredential/GetAssertion on both transports, committed-event equivalence, in-flight changes, accessibility or full SC-001–006. T027/T031/T041 remain open; this Android-only procedure did not use PC control, and coverage remains deferred.
- **status**: `Passed (Android saved/active selection and both restart/activation directions on ASUS) / Unverified (Windows host ceremonies, full acceptance and accessibility)`.
- **artifact_link**: [ASUS transport switch excerpt](../../feature/fido2/build/reports/fido2/064/asus-transport-switch-android-only-20261104.txt), [T027/T031/T041](tasks.md)

### Record 039: ASUS Sticky-Service Permission Revocation Recovery Fix (T035/T039 — Partial)
- **requirement**: Parent T035/T039; BLE T035/T036; `FR-BT-SEL-030`, `FR-BLE-010`, `FR-BLE-070`, `FR-HID-010`
- **scope**: Correct the crash after Android revokes Nearby devices while the connected-device service is sticky, and verify the complete direct Settings permission recovery path on ASUS.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus uncommitted Feature 064 changes, including `Fido2TransportService` prerequisite preflight.
- **named_procedure**: Run focused permission guard host tests and KtLint, build/install shrunkDebug, start BLE, use Android Settings to deny Nearby devices, inspect process exits/logcat/advertiser state, allow the permission in Settings, relaunch MainActivity and explicitly restart BLE.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; Windows 11 x64; Gradle 9.6.0; SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: The host guard test passed 5/5 and the full FIDO2 host suite passed 1,131/1,131 with zero failures/errors/skips; lint passed, and shrunkDebug assembled and installed. Before the change, Android killed the process for permission revocation, restarted the sticky service, and it crashed in `startForeground` with `SecurityException`. After the change, Android restarted the service once; its null-intent permission guard logged a warning, returned `START_NOT_STICKY`, and the service was destroyed without another crash/restart. The BLE advertiser disappeared. Direct Settings Allow plus relaunch showed Ready to Start without auto-advertising; explicit Start restored BLE advertising and the expected ready-to-pair UI.
- **date**: 2026-11-04
- **limitations**: One ASUS/API 35 permission interruption/regrant cycle; no real Windows ceremony, process death in another mode, mid-ceremony save, 50-cycle test, coverage or full SC-001–006. Parent T035/T039/T041 remain open; coverage remains deferred.
- **status**: `Passed (guard tests, focused lint/build and ASUS permission-crash fix/recovery) / Unverified (remaining parent interruption, Windows and coverage acceptance)`.
- **artifact_link**: [ASUS sticky-service permission recovery](../../feature/fido2/build/reports/fido2/064/asus-sticky-service-permission-recovery-20261104.txt), [BLE Record 055](../064-fido-ble-transport/implementation-evidence.md#record-055-asus-permission-revocation-sticky-service-crash-fix-t035t036--passed-scoped), [T035/T039](tasks.md)

### Record 040: ASUS Android Process-Death Subcheck (T035 — Unverified)
- **requirement**: Parent T035; `FR-BT-SEL-030`, `FR-BLE-060`
- **scope**: Attempt an Android OS process-death check while the saved mode was BLE and its foreground transport service was active, without clearing app data or interacting with Windows.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes.
- **named_procedure**: With no ceremony active, try `adb shell kill -9 <pid>`; then move the app to background and try `adb shell am kill com.chimali.shrunk`. Reinspect PID/service/advertiser, then return MainActivity to foreground.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; `com.chimali.shrunk`; Windows 11 x64 (not controlled).
- **expected_observed_result**: Direct signaling was denied to the shell (`Operation not permitted`). Android's safe background kill preserved the process while the connected-device foreground service was active. MainActivity returned to foreground; the same process, service and Chimali BLE registration remained active. No app data was cleared and no ceremony was active.
- **date**: 2026-11-04
- **limitations**: This does not simulate OS process death and cannot establish cold-process transport-selection behavior. No privileged/root escalation was attempted. Existing one-cycle force-stop/relaunch and reboot evidence remain separately scoped. Parent T035/T041 remain open; Windows acceptance and coverage are unverified/deferred.
- **status**: `Unverified (Android shell could not terminate a process protected by its active foreground BLE service)`.
- **artifact_link**: [sanitized ASUS process-death subcheck](../../feature/fido2/build/reports/fido2/064/asus-process-death-check-20261104.txt), [T035](tasks.md)

### Record 041: ASUS Android-Managed Process Crash and Sticky BLE Service Recovery (T035 — Passed, Scoped)
- **requirement**: Parent T035; `FR-BT-SEL-030`, `FR-BT-SEL-040`, `FR-BLE-060`
- **scope**: Verify Android-managed app-process termination and sticky foreground-service restoration while BLE is active, with no ceremony or app-data reset.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes.
- **named_procedure**: Confirm no ceremony was active; run Android's documented `am crash com.chimali.shrunk`; inspect `ApplicationExitInfo`, package PID, foreground service and Bluetooth manager; explicitly reopen MainActivity and inspect selected transport/status.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; synthetic package `com.chimali.shrunk`; Windows 11 x64 (not controlled).
- **expected_observed_result**: Android recorded PID 31713 exit as reason `APP CRASH(EXCEPTION)` / `crash`; it automatically restarted the sticky Fido2TransportService as PID 5197. The service returned to foreground and Bluetooth manager listed Chimali BLE app_if 13 as registered. Reopening MainActivity showed `Advertising...`, `Ready to pair with a new host PC`, and `FIDO2 / WebAuthn over FIDO Bluetooth LE`. No profile data was cleared.
- **date**: 2026-11-04
- **limitations**: This is an induced VM crash, not an organic low-memory kill. It exercises one BLE-mode sticky restart only: no pending alternate, Classic restart, in-flight ceremony, Windows host, or 50-cycle run. Parent T035/T041 remain open; coverage is deferred.
- **status**: `Passed (Android-managed app crash and sticky BLE-service restoration on ASUS) / Unverified (organic OS kill and remaining lifecycle/host acceptance)`.
- **artifact_link**: [sanitized ASUS sticky-service crash recovery](../../feature/fido2/build/reports/fido2/064/asus-sticky-service-crash-recovery-20261104.txt), [T035](tasks.md)

### Record 042: ASUS Pending Classic Choice Across Android-Managed Process Crash (T035 — Passed, Scoped)
- **requirement**: Parent T035; `FR-BT-SEL-020`, `FR-BT-SEL-030`, `FR-BT-SEL-040`, `FR-BLE-060`
- **scope**: While BLE was active, save Classic pending, then verify the new process applies the saved choice after Android terminates the app process and does not fall back to BLE.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes.
- **named_procedure**: Verify Settings shows active BLE; save Classic and confirm pending/restart notice; with no ceremony active run documented `adb shell am crash com.chimali.shrunk`; inspect exit reason, service and Bluetooth manager; reopen MainActivity.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; Windows ceremonies/dialogs not used.
- **expected_observed_result**: Classic was saved pending while BLE remained active. Android reported prior PID 5197 as `APP CRASH(EXCEPTION)`, restarted the FIDO2 service under PID 7566, and the service became foreground. The Bluetooth manager showed HID_DEVICE and no Chimali GATT advertiser/app_if 13. Reopened MainActivity showed `Advertising...` and `FIDO2 / WebAuthn over Bluetooth HID`. The persisted Classic choice took effect after process restart without BLE fallback.
- **date**: 2026-11-04
- **limitations**: One induced VM crash on ASUS/API35; this is not an organic low-memory kill or host ceremony. The reverse Classic→BLE run restored the saved BLE choice and explicit advertising after a system crash prompt, but immediate sticky BLE restart was unverified. Mid-ceremony, stale-bond, cancellation, 50 cycles and Windows acceptance remain open; parent T035/T041 remain unchecked.
- **status**: `Passed (one pending Classic choice applied after Android-managed process restart; no BLE fallback) / Unverified (organic kill and remaining lifecycle/host acceptance)`.
- **artifact_link**: [sanitized ASUS two-transport process-restart evidence](../../feature/fido2/build/reports/fido2/064/asus-both-transport-process-restart-20261104.txt), [T035](tasks.md)

### Record 043: Current Local CI Without Deferred Coverage (T039 — Partial)
- **requirement**: Parent T039 local static-analysis, compilation and host-test portion
- **scope**: Re-run repository local CI after the transport-service permission guard and trace-test timeout cleanup, omitting the owner-deferred coverage phase.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes.
- **named_procedure**: Run `tools/local-ci.ps1 -NoFix -NoConfigurationCache`, using the worktree Gradle user home and a temporary script copy with `--no-problems-report`; do not pass `-RunCoverage`. Delete the temporary script after completion.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; repository JDK; ASUS AI2302/API35 and Mi A2/API29 connected but not used for connected testing.
- **expected_observed_result**: Local CI passed in 489.43 seconds: KtLint, Detekt, release lint, all-module compilation, unit tests and configured KMP Android host tests succeeded. Gradle reused valid up-to-date outputs where applicable and used Kotlin's compiler fallback after daemon marker-file access trouble. Coverage was omitted.
- **date**: 2026-11-04
- **limitations**: No connected-device suite, coverage, accepted-target raw-count comparison or hosted integration ran in this invocation. Earlier device suite records remain partial due to procedure-specific trace/research cases. Coverage remains deferred by owner direction; parent T039/T041 remain open.
- **status**: `Passed (ordinary local CI static, compile and host scope; coverage intentionally omitted) / Unverified (connected device acceptance, trusted coverage comparison and hosted integration)`.
- **artifact_link**: [local CI summary, coverage omitted](../../feature/fido2/build/reports/fido2/064/local-ci-no-coverage-20261104.txt), [T039/T041](tasks.md)

### Record 044: ASUS Windows Bluetooth Discovery (T022 — Unverified)
- **requirement**: Parent T022; `FR-BT-SEL-010`, `FR-BLE-010`, `TR-BLE-010`
- **scope**: Check whether Windows 11 discovers the active ASUS Chimali FIDO BLE authenticator using the system Bluetooth add-device scan; do not pair, create a credential, or start a WebAuthn ceremony.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Confirm the Authenticator page reports Advertising and Ready to pair; inspect the connected-device foreground service and Bluetooth runtime grants; in Windows Settings > Bluetooth & devices > Devices, start Add device > Bluetooth, let discovery scan for more than one minute, record listed devices, then cancel.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`, `com.chimali.shrunk`; Windows 11 x64, Bluetooth enabled; no browser ceremony was used.
- **expected_observed_result**: Android showed `Advertising...` / `Ready to pair with a new host PC`; the connected-device foreground service was running and the app's Bluetooth advertise/connect/scan grants were present. Windows' Bluetooth discovery dialog remained empty for over one minute and did not list Chimali Authenticator. The scan was cancelled without pairing or modifying existing pairings.
- **date**: 2026-11-04
- **limitations**: This does not prove whether the advertiser emitted valid FIDO service data or whether Windows filters this device type; no independent BLE scanner, connection, registration, assertion, transport switch or recovery procedure was run. T022 and physical acceptance remain unverified; coverage remains deferred.
- **status**: `Unverified (Android reports advertising, but Windows did not discover the authenticator)`.
- **artifact_link**: [sanitized ASUS/Windows discovery observation](../../feature/fido2/build/reports/fido2/064/asus-windows-ble-discovery-20261104.txt), [T022](tasks.md)

### Record 045: Integrated-Browser WebAuthn Registration Attempt (T022 — Unverified)
- **requirement**: Parent T022; `FR-BT-SEL-010`, `FR-BLE-030`, `TR-BLE-010`
- **scope**: Attempt one real Windows registration for the authorized synthetic account using the integrated browser and active ASUS BLE authenticator; do not claim success without a credential response.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: In the integrated browser, submit Register at WebAuthn.io for `codex-synthetic-064-20261103`; inspect the browser page and Windows app inventory for the Windows Security key choice; stop after the operation resolves.
- **environment**: Windows 11 x64; integrated in-app browser; ASUS AI2302 / Android 15/API35, serial `R7AIB7002901AZ3`, `com.chimali.shrunk` showing BLE advertising.
- **expected_observed_result**: WebAuthn.io returned `The operation either timed out or was not allowed`. No Windows security-key chooser appeared in the observed UI, no assertion/registration result was returned, and no passkey was shown as created. A preceding Windows Bluetooth add-device scan was also empty (Record 044).
- **date**: 2026-11-04
- **limitations**: This does not establish whether the failure is caused by the in-app browser/Windows provider path or BLE interoperability; no credential response or authenticated-pairing proof exists. T022 remains unverified; coverage remains deferred.
- **status**: `Unverified (registration request timed out or was disallowed before an authenticator result)`.
- **artifact_link**: [sanitized WebAuthn timeout observation](../../feature/fido2/build/reports/fido2/064/asus-webauthn-registration-timeout-20261104.txt), [T022](tasks.md)

### Record 046: Nordic Legacy Advertiser API-Level Device Check (T022/T039 — Passed, Scoped)
- **requirement**: Parent T022 physical advertising-start subcheck; `FR-BLE-010`, `TR-BLE-010`
- **scope**: Verify the selected Nordic legacy advertiser can start on both connected Android versions; this does not certify Windows discovery or an authenticated ceremony.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Run only`NordicBleLegacyAdvertisingDeviceTest.connectableLegacyAdvertisingStartsOnApi28Plus` via `:feature:fido2:connectedAndroidDeviceTest` with the instrumentation class filter; inspect each connected-device XML result and verify the ASUS app advertiser remains registered afterward.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; ASUS AI2302/API35 serial `R7AIB7002901AZ3`; Xiaomi Mi A2/API29 serial `2ac6876`.
- **expected_observed_result**: The focused test passed 1/1 on ASUS/API35 and 1/1 on Mi/API29, with zero failures, errors or skips. The test started a connectable legacy advertiser and stopped its own instance; afterward the ASUS app still had its foreground service, GATT server registration and advertising entry.
- **date**: 2026-11-04
- **limitations**: This only confirms Nordic advertiser API startup on these two devices. It does not verify Windows discovery, exact on-air FIDO service data, host connection, authenticated pairing, registration/assertion, lifecycle matrix or trusted coverage. Parent T022/T039/T041 remain open; coverage is deferred.
- **status**: `Passed (focused advertiser-start check on API29 and API35) / Unverified (host discovery and full parent acceptance)`.
- **artifact_link**: [focused device-test summary](../../feature/fido2/build/reports/fido2/064/nordic-advertiser-device-test-20261104.txt), [ASUS result XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [Mi result XML](../../feature/fido2/build/reports/fido2/064/mi-nordic-legacy-advertising-device-test-20261104.xml)

### Record 047: Cross-Device FIDO Service Advertisement Discovery (T022 — Passed, Scoped)
- **requirement**: Parent T022 advertising/discovery subcheck; `FR-BLE-010`, `TR-BLE-010`
- **scope**: Verify an active Chimali advertiser on each connected phone is discoverable by the other phone's Android BLE scanner through the standard FIDO service UUID.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Keep both Chimali Authenticator instances advertising. Run `FidoBleAdvertiserDiscoveryDeviceTest.activeAuthenticatorAdvertisesTheFidoServiceUuid` on both devices. Grant scan permissions to the instrumentation package; on Android 10, also grant the test-only background location prerequisite. Ignore results advertising the local adapter name so a device cannot satisfy its own check.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; ASUS AI2302/API35 serial `R7AIB7002901AZ3`; Xiaomi Mi A2/API29 serial `2ac6876`; both `com.chimali.shrunk` apps showed Advertising.
- **expected_observed_result**: The unfiltered scanner on each phone received a result containing FIDO service UUID `0000FFFD-0000-1000-8000-00805F9B34FB` from a distinct remote device name. The focused test passed 1/1 on each phone with zero failures, errors or skips.
- **date**: 2026-11-04
- **limitations**: This confirms advertisement visibility between these two Android devices only. Windows Settings still did not list the ASUS, and the integrated-browser registration request timed out; no Windows GATT connection, authenticated pairing, credential response, assertion, switching or full acceptance matrix was completed. T022 remains open; coverage is deferred.
- **status**: `Passed (cross-device FIDO service discovery on ASUS/API35 and Mi/API29) / Unverified (Windows discovery and ceremony)`.
- **artifact_link**: [cross-device discovery test summary](../../feature/fido2/build/reports/fido2/064/fido-advertiser-peer-discovery-20261104.txt), [ASUS result XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [Mi result XML](../../feature/fido2/build/reports/fido2/064/fido2-nonperf-mi-20261104.xml)

### Record 048: Parent and BLE Configured Host/Device Validation (T039/T041 — Partial)
- **requirement**: Parent T039/T041 and BLE T036; `FR-BT-SEL-010–060`, `FR-BLE-010–080`, `TR-BT-SEL-010–040`, `TR-BLE-010–050`
- **scope**: Re-run configured common/FIDO2 host tests, settings unit and connected instrumentation, and the complete FIDO2 Android device suite on the currently connected ASUS and Mi; coverage was omitted per owner direction.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Run `:core:common:testAndroidHostTest :feature:fido2:testAndroidHostTest :feature:settings:testDebugUnitTest :feature:settings:connectedDebugAndroidTest :feature:fido2:connectedAndroidDeviceTest --no-configuration-cache --no-problems-report --max-workers=1`, using the worktree-local Gradle user home. Do not invoke `-RunCoverage`.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; ASUS AI2302/API35 serial `R7AIB7002901AZ3`; Xiaomi Mi A2/API29 serial `2ac6876`.
- **expected_observed_result**: Common/FIDO2 host tests and settings unit tests were up-to-date and successful. Settings connected instrumentation passed 12/12 on each phone. FIDO2 connected suite completed with 48/53 passed and 5 failed on ASUS, and 45/50 passed and 5 failed on Mi; the same five Perfetto/measurement research cases failed on both devices because dedicated tracing configuration and frozen per-run arguments were not supplied. The Nordic advertiser startup and cross-device service-discovery cases passed on both devices. The Gradle task therefore exited nonzero; these research cases are not counted as passes.
- **date**: 2026-11-04
- **limitations**: This is not full device-suite acceptance. The five procedure-specific research cases remain unresolved; no Windows GATT connection or WebAuthn ceremony passed. Coverage and trusted candidate-versus-target raw-count assessment were omitted by owner direction and remain unverified/deferred. Parent T039/T041 and BLE T036 remain open.
- **status**: `Partial (host and settings checks passed; focused BLE device checks passed) / Failed (full connected FIDO2 suite has five unsatisfied research procedures per device) / Deferred (coverage)`.
- **artifact_link**: [ASUS connected-suite XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [Mi connected-suite XML](../../feature/fido2/build/reports/fido2/064/mi-connected-suite-post-retry-fix-20261104.xml), [ASUS settings XML](../../feature/settings/build/outputs/androidTest-results/connected/debug/TEST-ASUS_AI2302%20-%2015.xml), [Mi settings XML](../../feature/settings/build/outputs/androidTest-results/connected/debug/TEST-Mi%20A2%20-%2010.xml), [T039/T041](tasks.md)

### Record 049: Current Worktree Local CI Without Coverage (T039 — Partial)
- **requirement**: Parent T039 local static/compile/unit/host portion; `FR-BT-SEL-010–060`, `TR-BT-SEL-010–040`
- **scope**: Re-run ordinary local CI against the current worktree without the owner-deferred coverage phase.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Run `tools/local-ci.ps1 -NoFix -NoConfigurationCache` with `.gradle/codex-user-home` and a temporary script copy adding Gradle's `--no-problems-report`; remove the temporary copy after the run. Do not pass `-RunCoverage`.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; ASUS AI2302/API35 and Xiaomi Mi A2/API29 connected.
- **expected_observed_result**: Local CI passed in 78 seconds: KtLint, Detekt, release lint, comprehensive compilation, unit tests and configured KMP Android host tests all succeeded. Most tasks were up-to-date; the existing FIDO2 Detekt task executed. Coverage was omitted.
- **date**: 2026-11-04
- **limitations**: This confirms only the ordinary local CI scope. The current complete connected FIDO2 suite has five unsatisfied procedure-specific cases per device (Record 048); trusted accepted-target comparison, coverage, full physical ceremonies and hosted integration were not run. Parent T039/T041 remain open; coverage is deferred by owner direction.
- **status**: `Passed (ordinary local CI without coverage) / Unverified (connected-suite acceptance, target comparison and hosted integration) / Deferred (coverage)`.
- **artifact_link**: [current local CI summary](../../feature/fido2/build/reports/fido2/064/current-local-ci-no-coverage-20261104.txt), [T039/T041](tasks.md), [current connected-suite outcomes](#record-048-parent-and-ble-configured-hostdevice-validation-t039t041--partial)

### Record 050: Current Worktree Local CI After GATT Service Update (T039 — Partial)
- **requirement**: Parent T039 local static/compile/unit/host portion; `FR-BT-SEL-010–060`, `TR-BT-SEL-010–040`
- **scope**: Re-run ordinary local CI against the current uncommitted tree after adding ordered FIDO and Device Information GATT service registration; omit coverage per owner direction.
- **revision**: Base `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` on branch `064-bluetooth-transport-selection`, plus current uncommitted Feature 064 changes.
- **named_procedure**: Run `tools/local-ci.ps1 -NoFix -NoConfigurationCache` using `.gradle/codex-user-home`; use and then remove a temporary script copy that adds `--no-problems-report`. Do not pass `-RunCoverage`.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; ASUS AI2302/API35 and Xiaomi Mi A2/API29 connected.
- **expected_observed_result**: Local CI passed in 167.2 seconds. KtLint, Detekt, release lint, comprehensive compilation, unit tests and configured KMP Android host tests succeeded. Kotlin daemon marker-file AccessDenied warnings triggered compiler fallback but did not fail tasks.
- **date**: 2026-11-04
- **limitations**: This does not cover the connected Android suite, full physical Windows ceremonies, trusted accepted-target coverage/nonregression or hosted integration. Coverage remains deferred; parent T039 and T041 remain open.
- **status**: `Passed (ordinary local CI without coverage) / Unverified (physical acceptance, trusted target comparison and hosted integration) / Deferred (coverage)`.
- **artifact_link**: [latest current-tree CI summary](../../feature/fido2/build/reports/fido2/064/current-local-ci-no-coverage-20261104.txt), [T039/T041](tasks.md), [connected-suite outcomes](#record-048-parent-and-ble-configured-hostdevice-validation-t039t041--partial)

### Record 051: Current Configured Host and Connected Device Suites (T039/T041 — Partial)
- **requirement**: Parent T039/T041 and BLE T036; `FR-BT-SEL-010–060`, `FR-BLE-010–080`, `TR-BT-SEL-010–040`, `TR-BLE-010–050`
- **scope**: Re-run common/FIDO2 host tests, settings unit and connected tests, and the complete current FIDO2 Android device suite on ASUS/API35 and Mi/API29. Coverage remains deferred.
- **revision**: Base `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` on branch `064-bluetooth-transport-selection`, plus uncommitted Feature 064 changes.
- **named_procedure**: Run `:core:common:testAndroidHostTest :feature:fido2:testAndroidHostTest :feature:settings:testDebugUnitTest :feature:settings:connectedDebugAndroidTest :feature:fido2:connectedAndroidDeviceTest --no-configuration-cache --no-problems-report --max-workers=1` with `.gradle/codex-user-home`; then run the non-research suite with `-Pandroid.testInstrumentationRunnerArguments.notPackage=com.chimali.fido2.util.performance`; do not run coverage.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; ASUS AI2302/API35 serial `R7AIB7002901AZ3`; Xiaomi Mi A2/API29 serial `2ac6876`.
- **expected_observed_result**: Common/FIDO2 host tests and settings unit tests completed successfully. Settings instrumentation passed 12/12 on each device. ASUS FIDO2 instrumentation ran 55 tests: 50 passed, 5 failed, 0 errors/skips; Mi ran 52: 47 passed, 5 failed, 0 errors/skips. The original run showed five research-class failures at trace preconditions. On the follow-up, comma-separated`notClass` excluded only `EmpiricalCalibrationDeviceTest`; four remaining research classes ran and failed before measurement because Perfetto was not active with the test package in `atrace_apps`. ASUS: 54 tests, 50 passed and 4 failed. Mi: 51 tests, 47 passed and 4 failed. Both had zero errors/skips, and all other executed tests in the follow-up passed. No measurement assertions or frozen-procedure arguments were reached. A corrected follow-up using`notPackage=com.chimali.fido2.util.performance` excluded exactly the five controlled tracing classes and passed 50/50 tests on ASUS and 47/47 on Mi, with zero failures/errors/skips. BLE-relevant `FidoBleSecurityDeviceTest` passed 4/4 on each phone, including actual registration of both FIDO and Device Information GATT services; Nordic advertiser and cross-device discovery cases also passed. The original full-suite Gradle invocation exited nonzero. The corrected package-filtered non-research follow-up succeeded.
- **date**: 2026-11-04
- **limitations**: Procedure-specific research tests are not passes and no measurement arguments were supplied. The original full-suite XML was overwritten by later connected-test runs. The failed class-filter attempt and corrected non-research result XML are preserved separately under `feature/fido2/build/reports/fido2/064`. This does not complete Windows discovery/ceremonies, transport switch ceremonies, stale-bond or mid-ceremony recovery, full T035/T036 acceptance, trusted coverage/nonregression or hosted integration. Coverage remains deferred by owner direction.
- **status**: `Passed (host/settings checks and scoped BLE device tests) / Passed (non-research device suite ASUS 50/50 and Mi 47/47) / Partial (original full run ASUS 50/55 and Mi 47/52; five controlled trace procedures remain unverified) / Unverified (Windows and full parent acceptance) / Deferred (coverage)`.
- **artifact_link**: [ASUS non-research result XML](../../feature/fido2/build/reports/fido2/064/fido2-nonperf-asus-20261104.xml), [Mi non-research result XML](../../feature/fido2/build/reports/fido2/064/fido2-nonperf-mi-20261104.xml), [class-filter attempt ASUS XML](../../feature/fido2/build/reports/fido2/064/fido2-notclass-followup-asus-20261104.xml), [class-filter attempt Mi XML](../../feature/fido2/build/reports/fido2/064/fido2-notclass-followup-mi-20261104.xml), [ASUS settings XML](../../feature/settings/build/outputs/androidTest-results/connected/debug/TEST-ASUS_AI2302%20-%2015.xml), [Mi settings XML](../../feature/settings/build/outputs/androidTest-results/connected/debug/TEST-Mi%20A2%20-%2010.xml), [T039/T041](tasks.md)

### Record 052: Retry Action After Runtime Transport Error (T035 — Implemented, Host-Verified)
- **requirement**: Parent T035 recovery; `FR-BT-SEL-050`, `FR-BLE-070`, `TR-BLE-040`
- **scope**: Ensure the authenticator's main transport control dispatches Retry after a runtime transport error even when the frozen process selection still reports `Ready`.
- **revision**: Base `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` on branch `064-bluetooth-transport-selection`, plus current uncommitted changes.
- **named_procedure**: Run the focused `Fido2HomeViewModelTest`, the full `:feature:fido2:testAndroidHostTest` suite, and `:feature:fido2:ktlintCheck`, each with `--no-configuration-cache --no-problems-report --max-workers=1`.
- **environment**: Windows 11 x64; Gradle 9.6.0; ASUS AI2302/API35 and Xiaomi Mi A2/API29 connected; tests use host fakes and do not change device state.
- **expected_observed_result**: The toggle action now routes every `Fido2ConnectionState.Error` to the service's explicit retry path. This covers runtime advertiser/link errors where process availability remains `Ready`; previously the control issued Start, which was ignored because the service already considered startup requested. Focused ViewModel tests passed 2/2, the full FIDO2 Android host suite passed 1,133/1,133 with zero failures/errors/skips, and feature KtLint passed.
- **date**: 2026-11-04
- **limitations**: Host verification establishes action selection only. The actual retry after an Android Bluetooth radio interruption and recovery remains to be exercised on device; no Windows host, pairing, credential ceremony, coverage or complete lifecycle acceptance is established. Parent T035/T041 remain open; coverage is deferred.
- **status**: `Passed (runtime-error retry routing, FIDO2 host tests 1,133/1,133, focused ViewModel tests 2/2, feature KtLint) / Unverified (physical radio interruption/retry and full acceptance)`.
- **artifact_link**: [focused host test XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.presentation.viewmodel.Fido2HomeViewModelTest.xml), [home ViewModel action](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/Fido2HomeViewModel.kt), [parent T035](tasks.md), [BLE implementation evidence](../064-fido-ble-transport/implementation-evidence.md#record-071-runtime-advertiser-error-retry-action-t035--implemented-host-verified)

### Record 053: Retry Label and Action on Transport Error (T035 — Implemented, Host/Device Verified)
- **requirement**: Parent T035 recovery; `FR-BT-SEL-050`, `FR-BLE-070`, `TR-BLE-040`
- **scope**: Make the home-screen action accessible and accurately labeled when a selected transport reports a runtime error; verify Retry invokes the action on ASUS/API35 and Mi/API29.
- **revision**: Base `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` on branch `064-bluetooth-transport-selection`, plus current uncommitted Feature 064 changes.
- **named_procedure**: Run the full `:feature:fido2:testAndroidHostTest`, then `:feature:fido2:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.chimali.fido2.presentation.ui.AuthenticatorDashboardComponentsTest`, then `:feature:fido2:ktlintCheck`, all with `--no-configuration-cache --no-problems-report --max-workers=1`.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; ASUS AI2302/API35 serial `R7AIB7002901AZ3`; Xiaomi Mi A2/API29 serial `2ac6876`.
- **expected_observed_result**: On `Fido2ConnectionState.Error`, `TransportToggleButton` now shows “Retry Authenticator” with a refresh icon; clicking it follows the ViewModel action, which routes Error to the service retry path even if availability remains `Ready`. Full FIDO2 host tests passed 1,133/1,133. The focused dashboard Compose/device class passed 6/6 on ASUS and 6/6 on Mi; a final rerun after the named-constant cleanup again had zero failures, errors or skips, and its Retry label/callback assertion passed on both devices. Feature KtLint passed. Ordinary local CI also passed after this recovery update: KtLint, Detekt, release lint, compile-all and unit/host checks completed; coverage was not run.
- **date**: 2026-11-04
- **limitations**: Device UI tests verify rendering and callback only. They do not simulate a physical radio interruption or prove a full BLE host reconnect; Windows ceremonies, stale-bond recovery, 50-cycle memory and full T035/T041 acceptance remain open. Coverage is deferred.
- **status**: `Passed (Retry UI and callback; 1,133/1,133 FIDO2 host tests; 6/6 device UI tests per phone; KtLint) / Unverified (physical interruption/reconnect and full acceptance)`.
- **artifact_link**: [ASUS device result XML](../../feature/fido2/build/reports/fido2/064/authenticator-dashboard-retry-asus-final-20261104.xml), [Mi device result XML](../../feature/fido2/build/reports/fido2/064/authenticator-dashboard-retry-mi-final-20261104.xml), [host results](../../feature/fido2/build/test-results/testAndroidHostTest/index.html), [Retry UI](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/Fido2HomeScreen.kt), [parent T035](tasks.md), [BLE Record 072](../064-fido-ble-transport/implementation-evidence.md#record-072-runtime-advertiser-error-retry-ui-t035--implemented-hostdevice-verified)

### Record 054: Mi A2 BLE Force-Stop and Explicit Restart (T035 — Passed, Scoped)
- **requirement**: Parent T035 lifecycle; `FR-BT-SEL-030`, `FR-BLE-010`, `FR-BLE-060`
- **scope**: Verify on Mi/API29 that the saved BLE choice survives a package force-stop/relaunch, stays stopped before explicit Start, and resumes advertising afterward.
- **revision**: Base `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` on branch `064-bluetooth-transport-selection`, plus current uncommitted Feature 064 changes.
- **named_procedure**: Capture initial UI/service/advertiser state; force-stop `com.chimali.shrunk`; explicitly launch `com.chimali.shrunk/com.chimali.MainActivity`; inspect the screen and service before tapping Start; tap the visible Start Authenticator button; inspect UI, service and Bluetooth advertiser registration again.
- **environment**: Windows 11 x64; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; package `com.chimali.shrunk`.
- **expected_observed_result**: Before force-stop, the screen showed Advertising and the foreground service/advertiser were registered. After force-stop and explicit MainActivity launch, the screen showed Ready to Start with the FIDO Bluetooth LE label and no transport service instance. Only after the explicit Start tap did the UI return to Advertising, the service become foreground, and Bluetooth manager list `com.chimali.shrunk (Registered)`. No app data was cleared.
- **date**: 2026-11-04
- **limitations**: One BLE-mode force-stop/restart cycle on Mi only. Classic-mode lifecycle, OS process death, reboot, in-flight ceremony, Windows discovery/ceremonies, stale-bond recovery, 50-cycle behavior, and complete T035/T041 acceptance remain open. Coverage is deferred.
- **status**: `Passed (Mi/API29 BLE selection persistence and explicit-start gating after force-stop) / Unverified (remaining parent lifecycle and host acceptance)`.
- **artifact_link**: [Mi force-stop/restart procedure and observations](../../feature/fido2/build/reports/fido2/064/mi-force-stop-restart-20261104.txt), [Advertising before restart](../../feature/fido2/build/reports/fido2/064/mi-current-screen-20261104.png), [Ready to Start after relaunch](../../feature/fido2/build/reports/fido2/064/mi-force-stop-before-start-20261104.png), [Advertising after explicit Start](../../feature/fido2/build/reports/fido2/064/mi-force-stop-after-start-20261104.png), [T035/T041](tasks.md)

### Record 055: Mi A2 Classic HID Startup Failure and BLE Recovery (T027/T035 — Partial)
- **requirement**: Parent T027/T035; `FR-BT-SEL-040`, `FR-BT-SEL-050`; BLE lifecycle recovery
- **scope**: Exercise saved BLE-to-Classic process selection and startup, then restore the BLE selection on Mi/API29.
- **revision**: Base `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` on branch `064-bluetooth-transport-selection`, plus current uncommitted Feature 064 changes.
- **named_procedure**: While BLE advertising, save Classic and verify the pending-restart state; force-stop and explicitly relaunch the package; start Classic after approving Android Bluetooth visibility; capture the profile-binding failure; save BLE, force-stop/relaunch and explicitly start BLE.
- **environment**: Windows 11 x64; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; package `com.chimali.shrunk`.
- **expected_observed_result**: Classic became the selected mode after restart but failed to start. Logcat reported `Could not bind to Bluetooth Service with Intent { act=android.bluetooth.IBluetoothHidDevice }`, then a 5,000 ms HID startup timeout. Settings showed `Startup timed out`; no BLE fallback occurred. Saving BLE, restarting the process and explicitly starting restored BLE advertising. The system listing of HidHostService does not establish availability of Android's HID Device peripheral profile.
- **date**: 2026-11-04
- **limitations**: One Mi A2/API29 Classic start attempt only; cannot distinguish device ROM/build capability from a transient/profile-service fault, and does not generalize to Xiaomi devices. No Windows connection, pairing, registration/assertion ceremony, Classic support on another phone, or complete T027/T035/T041 acceptance was established. No app data was cleared. Coverage remains deferred.
- **status**: `Failed (Mi/API29 Classic HID profile binding/startup) / Passed (BLE saved-choice recovery and advertising restoration) / Unverified (Windows ceremony, other devices, full acceptance)`.
- **artifact_link**: [procedure, observations and screenshots index](../../feature/fido2/build/reports/fido2/064/mi-classic-hid-failure-and-ble-recovery-20261104.txt), [Classic-mode failure screen](../../feature/fido2/build/reports/fido2/064/mi-classic-hid-profile-error-20261104.png), [timed-out Settings state](../../feature/fido2/build/reports/fido2/064/mi-restore-ble-settings-20261104.png), [BLE advertising restored](../../feature/fido2/build/reports/fido2/064/mi-ble-restored-after-classic-20261104.png), [parent T027/T035](tasks.md), [BLE Record 074](../064-fido-ble-transport/implementation-evidence.md#record-074-mi-a2-classic-hid-startup-failure-and-ble-recovery-t035--partial)

### Record 056: Mi A2 BLE Radio-Off Failure and Retry Recovery (T035 — Passed, Scoped)
- **requirement**: Parent T035 lifecycle/recovery; `FR-BT-SEL-030`, `FR-BT-SEL-050`; BLE T035; `FR-BLE-060`, `FR-BLE-070`
- **scope**: Verify the patched BLE advertiser reacts to a system Bluetooth-off event, shows actionable failure state, and can resume after radio restoration and explicit Retry.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Install the updated `shrunkDebug` build on Mi/API29; start Chimali and verify advertising plus Android Bluetooth advertiser registration; turn Bluetooth off in Android Settings; inspect app state and `dumpsys bluetooth_manager`; turn Bluetooth back on; explicitly Retry and inspect UI and advertiser registration.
- **environment**: Windows 11 x64; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; package `com.chimali.shrunk`; patched Nordic Android advertiser controller.
- **expected_observed_result**: Before interruption, UI showed Advertising and Bluetooth manager listed `com.chimali.shrunk (Registered)`. With Bluetooth off (`enabled: false`, `state: BLE_ON`), the advertiser registration disappeared and the app showed `Error Occurred / BLE advertising stopped unexpectedly` with Retry Authenticator. Restoring Bluetooth did not silently restart advertising; explicit Retry restored Advertising and `com.chimali.shrunk (Registered)`.
- **date**: 2026-11-04
- **limitations**: One BLE radio-off/on cycle on Mi/API29. No remote host connection, in-flight ceremony interruption, permission revocation, process death/reboot, stale-bond recovery, 50-cycle stress, ASUS repeat, Windows ceremony, or complete T035/T041 matrix was verified. Coverage is deferred.
- **status**: `Passed (Mi/API29 radio-off failure reporting and explicit Retry recovery) / Unverified (remaining lifecycle and host acceptance)`.
- **artifact_link**: [test procedure and observations](../../feature/fido2/build/reports/fido2/064/mi-ble-radio-off-recovery-20261104.txt), [advertising baseline](../../feature/fido2/build/reports/fido2/064/mi-ble-radio-baseline-20261104.png), [Bluetooth off](../../feature/fido2/build/reports/fido2/064/mi-bluetooth-off-after-fix.png), [app error state](../../feature/fido2/build/reports/fido2/064/mi-app-bluetooth-off-after-fix.png), [advertising after Retry](../../feature/fido2/build/reports/fido2/064/mi-app-explicit-retry-after-fix.png), [parent T035/T041](tasks.md), [BLE Record 075](../064-fido-ble-transport/implementation-evidence.md#record-075-mi-a2-radio-off-failure-and-retry-recovery-t035--passed-scoped)

### Record 057: Current Ordinary Local CI After Radio-Off Recovery (T039 — Passed, Scoped)
- **requirement**: Parent T039 local static analysis, compile, unit and KMP Android host scope; `FR-BT-SEL-010–060`, `TR-BT-SEL-010–040`
- **scope**: Validate the current worktree including the Mi radio-off recovery fix, without running the owner-deferred coverage phase.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Run `tools/local-ci.ps1 -NoFix -NoConfigurationCache` with worktree-local Gradle user home. The direct script first stopped at a Gradle problems-report `AccessDeniedException`; rerun a temporary copy with `--no-problems-report`, remove that copy after completion, and omit `-RunCoverage`.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; connected Xiaomi Mi A2/API29; ASUS not connected for this run.
- **expected_observed_result**: The temporary-script run passed KtLint, Detekt, Release lint, comprehensive compilation, unit tests, and configured KMP Android host tests in 171.66 seconds. Kotlin daemon marker-file `AccessDeniedException` warnings fell back to in-process compilation and did not fail compilation. Coverage was not run.
- **date**: 2026-11-04
- **limitations**: Ordinary local CI does not run connected device acceptance, Windows ceremonies, coverage floors, trusted accepted-target raw-count comparison or hosted integration. T039's full coverage/nonregression work remains deferred by owner direction; Windows and full physical Feature 064 acceptance remain open.
- **status**: `Passed (ordinary local CI without coverage) / Deferred (coverage) / Unverified (trusted target comparison, hosted and physical acceptance)`.
- **artifact_link**: [T039 local CI scope](tasks.md), [coverage enforcement plan and owner waiver](../../058-enforce-coverage-targets/plan.md), [current transport recovery evidence](#record-056-mi-a2-ble-radio-off-failure-and-retry-recovery-t035--passed-scoped)

### Record 058: Mi A2 Nordic Legacy Advertising Device Test (T039 — Passed, Scoped)
- **requirement**: Parent T039 connected-device validation; BLE T015/T036; `FR-BLE-010`, `TR-BLE-010`
- **scope**: Re-run the focused Android device check that starts a real connectable legacy BLE advertisement on the updated current build.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Run `:feature:fido2:connectedAndroidDeviceTest` with class `com.chimali.fido2.data.transport.NordicBleLegacyAdvertisingDeviceTest`, `--no-configuration-cache --no-problems-report --max-workers=1`; inspect connected device XML.
- **environment**: Windows 11 x64; Gradle 9.6.0; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; ASUS not connected for this run.
- **expected_observed_result**: `connectableLegacyAdvertisingStartsOnApi28Plus` passed 1/1, zero failures/errors/skips, in 0.18 seconds on the current APK. The test verifies actual Nordic advertising starts with the required legacy/connectable settings on API29.
- **date**: 2026-11-04
- **limitations**: Focused startup test only; it does not exercise radio-off recovery, remote host discovery, authenticated pairing, Windows ceremonies, or the full FIDO2 device suite. The five controlled trace cases remain unresolved in the recorded complete device run; full T039/T041 remains open and coverage is deferred.
- **status**: `Passed (Mi/API29 Nordic legacy advertising startup 1/1) / Unverified (full device/Windows acceptance and trusted coverage comparison) / Deferred (coverage)`.
- **artifact_link**: [Mi connected-device XML](../../feature/fido2/build/reports/fido2/064/mi-nordic-legacy-advertising-device-test-20261104.xml), [BLE T036 and parent T039](tasks.md)

### Record 059: Windows Save-Passkey Dialog Disappeared (T031/T041 — Unverified)
- **requirement**: Parent T031/T041 Windows registration; `FR-BT-SEL-020`, `FR-BLE-020`
- **scope**: Record the latest user-observed Windows WebAuthn registration prompt outcome without inferring credential creation.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Attempt synthetic WebAuthn registration in the integrated browser and follow the Windows security-key path previously selected by the user; inspect whether the browser reports registration success and the synthetic credential is available.
- **environment**: Windows 11 host; integrated browser/Edge state could not be verified by Computer Use during this continuation. Synthetic account `codex-synthetic-064-20261103`.
- **expected_observed_result**: The user reported that the Windows dialog labeled “Guardar clave de paso” disappeared and suspected it may have timed out. No browser success message or credential result was observed. Treat the ceremony as incomplete; cause of dialog dismissal is unknown.
- **date**: 2026-11-04
- **limitations**: This is not a registration pass or a confirmed Windows timeout diagnosis. No successful registration, GetAssertion, paired-host discovery, or transport interoperability conclusion is established. Parent T031/T041 remain open.
- **status**: `Unverified (dialog disappeared; no browser success or credential result)`.
- **artifact_link**: [parent T031/T041](tasks.md), [BLE registration evidence](../064-fido-ble-transport/implementation-evidence.md#record-078-windows-save-passkey-dialog-disappeared-t020t023--unverified)

### Record 060: Integrated Browser WebAuthn Focus Error (T031/T041 — Unverified)
- **requirement**: Parent T031/T041 Windows registration; `FR-BT-SEL-020`, `FR-BLE-020`
- **scope**: Retry synthetic registration in the requested integrated browser and distinguish page-focus failure from a Windows security-key or transport result.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Open WebAuthn.io in the ChatGPT in-app browser, submit the existing synthetic account for registration, and inspect both page state and system prompt state.
- **environment**: Windows 11 host; ChatGPT in-app browser tab on `https://webauthn.io/`; synthetic account `codex-synthetic-064-20261103`.
- **expected_observed_result**: The browser page returned “The operation is not allowed at this time because the page does not have focus.” Bringing the in-app browser forward and retrying did not launch a Windows prompt. Selecting the ChatGPT window to bring the app forward timed out in Computer Use. No registration or authenticator operation occurred.
- **date**: 2026-11-04
- **limitations**: This is a browser focus failure, not evidence of Windows BLE discovery, pairing or Chimali transport failure. The Windows registration/GetAssertion and parent acceptance procedures remain open; user interaction to focus the integrated browser is needed before another attempt.
- **status**: `Unverified (browser page lacked focus; no Windows prompt or credential result)`.
- **artifact_link**: [parent T031/T041](tasks.md), [BLE focus-error evidence](../064-fido-ble-transport/implementation-evidence.md#record-079-integrated-browser-webauthn-focus-error-t020t023--unverified)

### Record 061: Integrated Browser WebAuthn Operation Did Not Complete (T031/T041 — Unverified)
- **requirement**: Parent T031/T041 Windows registration; `FR-BT-SEL-020`, `FR-BLE-020`
- **scope**: Re-attempt the authorized synthetic registration after the user focused the integrated browser page; inspect for Windows prompt and browser success.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: In the focused ChatGPT in-app WebAuthn.io tab, submit Register for the existing synthetic account and observe the browser, Windows prompt and result.
- **environment**: Windows 11 host; ChatGPT in-app browser at `https://webauthn.io/`; synthetic account `codex-synthetic-064-20261103`.
- **expected_observed_result**: After the user clicked the page to focus it, Register produced the WebAuthn.io message “The operation either timed out or was not allowed” with a link to the WebAuthn privacy considerations. No Windows security-key prompt, authenticator request, registration success or credential result appeared.
- **date**: 2026-11-04
- **limitations**: WebAuthn.io does not distinguish timeout from browser policy rejection in this message. This does not establish Windows BLE discovery, pairing or a Chimali transport failure. Parent T031/T041 remain open.
- **status**: `Unverified (WebAuthn operation timed out or was not allowed; no Windows prompt or credential result)`.
- **artifact_link**: [parent T031/T041](tasks.md), [BLE operation outcome](../064-fido-ble-transport/implementation-evidence.md#record-080-integrated-browser-webauthn-operation-did-not-complete-t020t023--unverified)

### Record 062: Mi A2 Reboot Recovery Check Interrupted (T035 — Unverified)
- **requirement**: Parent T035 reboot and saved-selection recovery; `FR-BT-SEL-030`, `FR-BT-SEL-050`, `FR-BLE-060`
- **scope**: Verify that the saved BLE choice and explicit-start behavior survive a device reboot on Mi/API29.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Capture pre-reboot state, issue `adb -s 2ac6876 reboot`, wait for the device, then verify boot completion, foreground transport service, selected BLE state and whether advertising waits for explicit Start.
- **environment**: Windows 11 x64; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; package `com.chimali.shrunk`.
- **expected_observed_result**: Before reboot, MainActivity was resumed, Fido2TransportService was foreground and Android listed `com.chimali.shrunk (Registered)` as an advertiser. After reboot, `adb wait-for-device` returned but `sys.boot_completed` was blank, the lock screen was active, Bluetooth Manager was unavailable, and the device later disappeared from `adb devices`. Post-reboot app/service/selection state could not be inspected.
- **date**: 2026-11-04
- **limitations**: No app data was cleared. This is an interrupted reboot attempt, not evidence of recovered or failed selection behavior. The device must be unlocked/reconnected before the subcheck can be repeated; parent T035/T041 remain open.
- **status**: `Unverified (Mi disconnected before boot completion and app state inspection)`.
- **artifact_link**: [parent T035/T041](tasks.md), [BLE reboot evidence](../064-fido-ble-transport/implementation-evidence.md#record-081-mi-a2-reboot-recovery-check-interrupted-t035--unverified)

### Record 063: Windows Passkey Dialog Closed at Change (T031/T041 — Unverified)
- **requirement**: Parent T031/T041 Windows registration; `FR-BT-SEL-020`, `FR-BLE-020`
- **scope**: Record the user-handled Windows passkey dialog outcome from the latest synthetic registration attempt.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: From WebAuthn.io's synthetic registration attempt, choose the Windows “Cambiar” control and then select the Bluetooth security key; inspect the ensuing Windows prompt and browser result.
- **environment**: Windows 11 host; ChatGPT in-app browser at `https://webauthn.io/`; synthetic account `codex-synthetic-064-20261103`.
- **expected_observed_result**: The user reported that clicking “Cambiar” closed the dialog before a security-key choice or subsequent prompt appeared. WebAuthn.io still displayed “The operation either timed out or was not allowed.” No registration success or credential result was observed.
- **date**: 2026-11-04
- **limitations**: Timeout is suspected by the user but unconfirmed. This is not a Windows registration pass and does not establish transport failure. Parent T031/T041 remain open.
- **status**: `Unverified (dialog closed at Change; no security-key selection, browser success or credential result)`.
- **artifact_link**: [parent T031/T041](tasks.md), [BLE dialog outcome](../064-fido-ble-transport/implementation-evidence.md#record-082-windows-passkey-dialog-closed-at-change-t020t023--unverified)

### Record 064: Mi A2 BLE Selection and Explicit Start After Reboot (T035 — Passed, Scoped)
- **requirement**: Parent T035 reboot/saved-selection recovery; `FR-BT-SEL-030`, `FR-BT-SEL-050`, `FR-BLE-060`
- **scope**: Verify on Mi/API29 that reboot preserves the saved BLE choice, does not restart advertising automatically, and explicit Start restores the selected transport.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Record active BLE/service/advertiser state; reboot with `adb -s 2ac6876 reboot`; after boot, open `com.chimali.shrunk/com.chimali.MainActivity`; verify Ready to Start and no service/advertiser; tap Start; confirm Advertising, foreground service, advertiser registration, and saved/active BLE selection in Settings.
- **environment**: Windows 11 x64; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; package `com.chimali.shrunk`.
- **expected_observed_result**: Before reboot, MainActivity was resumed, Fido2TransportService was foreground and Android listed `com.chimali.shrunk (Registered)`. After boot, the service and advertiser were absent. Launching the app showed Ready to Start and BLE footer with no foreground service or advertiser. After explicit Start, the service became foreground and Android registered the advertiser; the UI showed Advertising. Settings then showed “FIDO Bluetooth LE (default)” selected and “Active in this process: FIDO Bluetooth LE / Active.” The device initially reconnected while locked/incomplete, then reported boot complete and allowed verification. No app data was cleared.
- **date**: 2026-11-04
- **limitations**: This proves one BLE-mode reboot/explicit-start subcheck only. It does not cover Classic reboot behavior, Windows discovery/registration/assertion, in-flight recovery, stale-bond recovery, 50-cycle stress, or full T035/T041 acceptance. Coverage remains deferred by owner direction.
- **status**: `Passed (Mi/API29 BLE selection persistence and explicit-start reboot subcheck) / Unverified (remaining lifecycle, Windows and full acceptance)`.
- **artifact_link**: [Mi reboot procedure and observations](../../feature/fido2/build/reports/fido2/064/mi-reboot-ble-explicit-start-20261104.txt), [Ready to Start after boot](../../feature/fido2/build/reports/fido2/064/mi-reboot-after-launch-20261104.png), [Advertising after explicit Start](../../feature/fido2/build/reports/fido2/064/mi-reboot-after-explicit-start-20261104.png), [saved and active BLE selection](../../feature/fido2/build/reports/fido2/064/mi-reboot-settings-20261104.png), [BLE reboot subcheck](../064-fido-ble-transport/implementation-evidence.md#record-083-mi-a2-ble-selection-and-explicit-start-after-reboot-t035--passed-scoped)

### Record 065: WebAuthn Retry After Security-Key Selection (T031/T041 — Unverified)
- **requirement**: Parent T031/T041 Windows registration and acceptance; `FR-BT-SEL-020`, `FR-BLE-020`
- **scope**: Retry the authorized synthetic Windows registration after the user selected “Cambiar” → “Llave de seguridad”; inspect browser outcome and Mi app/service state.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: In the ChatGPT integrated browser at WebAuthn.io, submit Register for `codex-synthetic-064-20261103`; after the user-selected security-key path, inspect the browser result and query Mi activity, service, Bluetooth advertiser registration and recent logs over ADB.
- **environment**: Windows 11 host; ChatGPT in-app browser at `https://webauthn.io/`; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; package `com.chimali.shrunk`.
- **expected_observed_result**: The user reported that Mi left Chimali after selecting “Llave de seguridad.” The retry page showed “The operation either timed out or was not allowed,” with no registration success or credential result. ADB afterward showed `com.chimali.shrunk/com.chimali.MainActivity` resumed, `Fido2TransportService` foreground, and `com.chimali.shrunk (Registered)` in Bluetooth Manager; the service record showed `crashCount=1`. The inspected recent filtered log lines contained no matching FIDO request failure that explains the reported app switch.
- **date**: 2026-11-05
- **limitations**: The WebAuthn message does not distinguish timeout from browser policy rejection. The ADB snapshot confirms the app/service/advertiser state only when inspected and does not establish the exact transition during the user-reported switch. No successful Windows registration, assertion, or credential result was observed; parent T031/T041 remain open.
- **status**: `Unverified (WebAuthn operation timed out or was not allowed; no credential result)`.
- **artifact_link**: [parent T031/T041](tasks.md), [BLE retry evidence](../064-fido-ble-transport/implementation-evidence.md#record-084-webauthn-retry-after-security-key-selection-t020t023--unverified)

### Record 066: ASUS Bluetooth Security-Key Conversation Interrupted (T031/T041 — Unverified)
- **requirement**: Parent T031/T041 Windows registration and acceptance; `FR-BT-SEL-020`, `FR-BLE-020`
- **scope**: Investigate the reported Windows Bluetooth security-key conversation and Android app switch during the ASUS/API35 synthetic registration attempt.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Start the Chimali Authenticator on ASUS, submit Register for the synthetic WebAuthn.io account, select the Windows Bluetooth security-key path as directed by the user, then inspect Android activity, FIDO service, BLE advertiser and recent logcat events.
- **environment**: Windows 11 host; ChatGPT in-app browser at `https://webauthn.io/`; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`.
- **expected_observed_result**: The user reported Windows “La conversación con la clave de seguridad de Bluetooth está en curso” while the Android screen left Chimali. Android logs recorded a `swipe-up` gesture at 11-05 06:25:58 followed by the ASUS launcher becoming foreground. At 06:26:06, `Fido2TransportService` was destroyed and its GATT server cleared, closed and unregistered. At 06:26:07, the foreground service restarted and re-registered FIDO service UUID `0000fffd-0000-1000-8000-00805f9b34fb`. A later ADB check showed MainActivity resumed, UI “Advertising...”, the service foreground, and `com.chimali.shrunk (Registered)`. WebAuthn.io returned “The operation either timed out or was not allowed”; no credential result was observed.
- **date**: 2026-11-05
- **limitations**: The logs establish a swipe-up transition and a brief service/GATT restart, but do not identify who or what generated the gesture or prove that it caused the Windows timeout. The generic WebAuthn result does not distinguish timeout from browser policy rejection. Parent T031/T041 remain open.
- **status**: `Unverified (Windows conversation did not yield a credential result; Android recorded a swipe-up and service/GATT restart)`.
- **artifact_link**: [ASUS WebAuthn activity and service log excerpt](../../feature/fido2/build/reports/fido2/064/asus-webauthn-switch-to-launcher-20261105.txt), [parent T031/T041](tasks.md), [BLE investigation](../064-fido-ble-transport/implementation-evidence.md#record-085-asus-bluetooth-security-key-conversation-interrupted-t020t023--unverified)

### Record 067: FIDO GATT Notification Callback Recursion Fixed (T031/T041 — Implementation Verified)
- **requirement**: Parent T031/T041 Windows registration path; `FR-BT-SEL-020`, `FR-BLE-020`
- **scope**: Fix the ASUS-observed crash during Windows Bluetooth security-key registration and verify callback forwarding.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Rename the callback listener to remove collision with Android's `onNotificationSent` override; run `FidoBleGattContractTest`, then lint Android main and host-test source sets.
- **environment**: Windows 11 host; Gradle 9.6.0; feature module `:feature:fido2`.
- **expected_observed_result**: ASUS logcat showed Windows GATT connection followed by repeated `FidoBleGattServer.onNotificationSent` frames, `StackOverflowError`, app process death and foreground-service restart. The override now forwards to`notificationSentListener`. The regression test verified the listener receives the device/status exactly once. Focused Android host test passed; both Android main and host-test KtLint tasks passed. The corrected `shrunkDebug` build was installed on ASUS AI2302/API35; after granting nearby-device and notification permissions, enabling Passkey Authenticator during onboarding and tapping Start, the UI showed “Advertising...”, the foreground FIDO service was active and Android listed `com.chimali.shrunk (Registered)`.
- **date**: 2026-11-05
- **limitations**: This corrects the confirmed recursion crash and verifies deployment/advertising on ASUS. A Windows registration/assertion retry is still needed to verify end-to-end ceremony behavior; parent T031/T041 remain open.
- **status**: `Passed (source correction, focused callback regression test, focused lint, ASUS installation and advertising) / Unverified (physical Windows ceremony)`.
- **artifact_link**: [ASUS callback-crash log excerpt](../../feature/fido2/build/reports/fido2/064/asus-webauthn-callback-stackoverflow-20261105.txt), [ASUS corrected-build deployment](../../feature/fido2/build/reports/fido2/064/asus-callback-fix-deployment-20261105.txt), [BLE callback-fix evidence](../064-fido-ble-transport/implementation-evidence.md#record-086-fido-gatt-notification-callback-recursion-fixed-t020t023--implementation-verified)

### Record 068: Corrected Build Installed on Mi for User Verification (T031/T041 — Setup Passed)
- **requirement**: Parent T031/T041 Windows registration and acceptance; `FR-BT-SEL-020`, `FR-BLE-020`
- **scope**: Deploy the corrected callback-fix build to the Mi device so the user can test it.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Attempt in-place install; after signature mismatch, uninstall only `com.chimali.shrunk` with the user's approval and install the `shrunkDebug` APK; query installed version.
- **environment**: Windows 11 host; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; package `com.chimali.shrunk`.
- **expected_observed_result**: In-place update was rejected because the installed package had a different signing key. The user authorized erasing only this package's local Mi data. Uninstall and APK installation then succeeded. Package manager reports version `0.1.0`, versionCode `1`, installed at `2026-11-05 01:19:49`. MainActivity was then launched; Fido2TransportService was not foreground and Android Bluetooth Manager showed no `com.chimali.shrunk` registration.
- **date**: 2026-11-05
- **limitations**: MainActivity was launched after installation. The FIDO service was not foreground and Android Bluetooth Manager showed no com.chimali.shrunk registration. Mi first-run setup, BLE advertising, and Windows WebAuthn registration/assertion are unverified. This record establishes deployment only; T031/T041 remain open.
- **status**: `Passed (Mi APK installation and MainActivity launch) / Unverified (first-run setup, BLE advertising and WebAuthn ceremony)`.
- **artifact_link**: [Mi corrected-build deployment](../../feature/fido2/build/reports/fido2/064/mi-callback-fix-deployment-20261105.txt), [parent T031/T041](tasks.md), [BLE deployment record](../064-fido-ble-transport/implementation-evidence.md#record-087-corrected-build-installed-on-mi-for-user-verification-t020t023--setup-passed)

### Record 069: ASUS Clean-Install Registration and Assertion (T022 — Passed, Scoped)
- **requirement**: Parent T022 clean-install BLE registration and assertion; `FR-BT-SEL-020`, `FR-BT-SEL-030`, `FR-BLE-020`, `FR-BLE-030`
- **scope**: Record physical FIDO BLE registration and assertion after a clean `com.chimali.shrunk` installation on ASUS using the parent-gated BLE-default build.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`, source revision `37388495` plus uncommitted callback fix; installed `shrunkDebug` version `0.1.0`.
- **named_procedure**: User ran registration and authentication on ASUS. Independently submit WebAuthn.io Authenticate for the synthetic account in the integrated browser; correlate result with Android GATT, FIDO revision, local user-verification, service and advertiser state.
- **environment**: Windows 11 host; ChatGPT integrated browser; WebAuthn.io; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`.
- **expected_observed_result**: User reported both registration and authentication succeeded. The independent assertion displayed “You're logged in!” on WebAuthn.io. Correlated ASUS logcat showed a successful GATT server connection, MTU 517, selected FIDO2 revision `0x20`, and successful local fingerprint verification for Chimali. Android continued to show the FIDO transport registered and advertising after the ceremony; captured screen displays “Advertising...”.
- **date**: 2026-11-05
- **limitations**: Registration is user-reported; this run independently verifies assertion only. Numeric-comparison values/rejection, independently captured registration metadata, multi-run stability and remaining BLE/selection lifecycle acceptance are not covered. This record closes only the clean-install registration/assertion task T022; it does not accept the full parent feature.
- **status**: `Passed (user-reported registration and independently observed assertion with correlated ASUS BLE/UV evidence) / Unverified (remaining parent acceptance matrix)`.
- **artifact_link**: [ASUS assertion correlation](../../feature/fido2/build/reports/fido2/064/asus-webauthn-assertion-20261105.txt), [ASUS screen after assertion](../../feature/fido2/build/reports/fido2/064/asus-after-web-authn-assertion-20261105.png), [BLE assertion record](../064-fido-ble-transport/implementation-evidence.md#record-088-asus-integrated-browser-fido-ble-getassertion-t023--partial), [parent T022](tasks.md)
### Record 070: ASUS BLE-to-Classic Restart and Return to BLE (T027/T031 — Partial)
- **requirement**: Parent T027 BLE-to-Classic and T031 Classic-to-BLE restart behavior; `FR-BT-SEL-030`, `FR-BT-SEL-040`, `FR-BT-SEL-050`
- **scope**: Verify that saving a different transport is pending until full process restart, Classic can register on ASUS, and the saved BLE choice can restore BLE.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; corrected `shrunkDebug` version `0.1.0`.
- **named_procedure**: From active BLE, select Classic, inspect pending status, force-stop/relaunch, verify Ready to Start, explicitly start Classic, inspect HID_DEVICE registration; select BLE, force-stop/relaunch, explicitly start and verify advertising.
- **environment**: ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; Android UI and system profile evidence via ADB; no Windows Classic ceremony performed.
- **expected_observed_result**: Classic save remained pending while BLE stayed active. After restart, the selected Classic mode was Ready to Start, then explicit Start successfully acquired HID_DEVICE and registered the app. Saving BLE remained pending until a second restart; explicit Start then restored BLE advertising, foreground service and GATT registration. The user authorized Android's 120-second Bluetooth visibility consent for Classic startup.
- **date**: 2026-11-05
- **limitations**: Windows registration/assertion over Classic was not performed; after returning to BLE, no additional Windows ceremony was run. Therefore T027 and T031 remain open. No app-data reset or credential deletion was performed in this sequence.
- **status**: `Passed (ASUS process-pending semantics, Classic HID registration, and BLE restoration) / Unverified (physical ceremonies for each switch direction and remaining parent matrix)`.
- **artifact_link**: [ASUS Classic/BLE process-switch evidence](../../feature/fido2/build/reports/fido2/064/asus-classic-ble-switch-20261105.txt), [BLE lifecycle record](../064-fido-ble-transport/implementation-evidence.md#record-089-asus-classic-hid-activation-and-ble-restoration-t023t035--partial), [parent T027/T031](tasks.md)
### Record 071: ASUS BLE Background, Explicit Stop and Restart (T035 — Partial)
- **requirement**: Parent T035 lifecycle and recovery; `FR-BT-SEL-050`, `FR-BT-SEL-060`, `FR-BLE-060`, `FR-BLE-070`
- **scope**: Record BLE transport survival under ordinary app backgrounding, explicit Stop cleanup and explicit Start restoration on ASUS.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; corrected `shrunkDebug` version `0.1.0`.
- **named_procedure**: With BLE Advertising active, press Home; inspect top-resumed app, foreground service and Bluetooth advertiser; reopen MainActivity, stop explicitly and inspect cleanup; start explicitly and inspect restoration.
- **environment**: ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; ADB dumpsys and UI hierarchy.
- **expected_observed_result**: ASUS launcher became top-resumed while Fido2TransportService remained foreground and Android listed Chimali registered/advertising. After explicit Stop, UI showed Ready to Start, the service was absent and Chimali was no longer in ongoing advertising. Explicit Start restored the foreground service, BLE registration and Advertising UI.
- **date**: 2026-11-05
- **limitations**: One BLE background/Stop/Start sequence only. Tool-observation intervals include deliberate waits and ADB overhead, not exact thresholds. It does not cover process death, reboot, in-flight choice change, OS permission loss, Classic recovery or the full interruption matrix. Parent T035 remains open.
- **status**: `Passed (ordinary BLE background survival and explicit cleanup/restart) / Unverified (remaining parent lifecycle and recovery acceptance)`.
- **artifact_link**: [ASUS BLE background/Stop/Start observations](../../feature/fido2/build/reports/fido2/064/asus-ble-background-stop-restart-20261105.txt), [BLE lifecycle record](../064-fido-ble-transport/implementation-evidence.md#record-090-asus-ble-background-explicit-stop-and-restart-t035--partial), [parent T035](tasks.md)
### Record 072: Mi Fresh-Install BLE Advertising and Passive PC GATT Connection (T035 — Partial)
- **requirement**: Parent T035 device lifecycle and transport startup; `FR-BT-SEL-010`, `FR-BT-SEL-050`, `FR-BLE-010`
- **scope**: Verify the corrected Mi build can start BLE advertising after the authorized clean package install and observe its paired-PC connection behavior.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; corrected `shrunkDebug` version `0.1.0`.
- **named_procedure**: Start the Mi authenticator using the saved/default BLE selection; inspect foreground service, Bluetooth registration, home UI and paired-PC GATT events.
- **environment**: Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; paired Windows 11 PC; package `com.chimali.shrunk`.
- **expected_observed_result**: Mi showed FIDO Bluetooth LE and entered Advertising with its FIDO service foreground and GATT registered. Android logged a successful PC GATT connection and MTU 517, then a clean disconnect/session-generation advance after about five seconds. The app remained Advertising.
- **date**: 2026-11-05
- **limitations**: No FIDO revision selection or CTAP operation was observed, so this is not a WebAuthn pass. Mi's app data is fresh and contains no synthetic credential. Windows ceremony, cancellation, stale-bond, process-death, permission-loss and repeated-cycle acceptance remain open.
- **status**: `Passed (Mi fresh-install BLE startup and passive GATT connect/disconnect observation) / Unverified (WebAuthn and remaining lifecycle acceptance)`.
- **artifact_link**: [Mi passive GATT connection](../../feature/fido2/build/reports/fido2/064/mi-passive-gatt-connection-20261105.txt), [BLE record](../064-fido-ble-transport/implementation-evidence.md#record-091-mi-fresh-install-ble-advertising-and-passive-pc-gatt-connection-t020--partial), [parent T035](tasks.md)

### Record 073: Current Worktree Local CI Without Deferred Coverage (T039 — Partial)
- **requirement**: Parent software-quality and coverage gate; `FR-BT-SEL-060`
- **scope**: Validate current source and host-test changes with the repository's ordinary local CI while honoring the user's explicit decision to defer the coverage gate.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current uncommitted candidate changes.
- **named_procedure**: Run `tools/local-ci.ps1 -NoFix -NoConfigurationCache` without `-RunCoverage`.
- **environment**: Windows 11 host; Gradle 9.6.0; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`.
- **expected_observed_result**: Local CI completed successfully in 143.34 seconds. KtLint, Detekt, Release lint, compile-all, unit tests and configured KMP Android host tests passed. Coverage was not run. The runner explicitly notes ordinary local CI does not establish hosted integration acceptance.
- **date**: 2026-11-05
- **limitations**: This verifies the current software candidate only. Coverage baseline/index and coverage gate remain deferred; Windows Classic ceremonies, remaining BLE protocol/lifecycle acceptance and hosted integration are not established by this run.
- **status**: `Passed (ordinary non-coverage local CI) / Unverified (deferred coverage gate and remaining parent acceptance)`.
- **artifact_link**: [Parent T039](tasks.md), [BLE local validation record](../064-fido-ble-transport/implementation-evidence.md#record-092-current-worktree-local-ci-without-deferred-coverage-t036t038--partial)

### Record 074: Latest shrunkDebug Installed and Advertising on Mi (BLE Deployment — Partial)
- **requirement**: Parent integrated device readiness; `FR-BT-SEL-010`, `FR-BT-SEL-050`
- **scope**: Build and update the latest candidate on Mi without clearing its app data, then confirm BLE startup and foreground state.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current uncommitted source candidate; APK SHA-256 `07F5C1E66B3166E7440A312FCE94CC9BFDE5EDFBDB3278B4DBD83F0AA9917876`.
- **named_procedure**: Run `:app:assembleShrunkDebug`; install the resulting APK with `adb install -r`; launch MainActivity, explicitly start the authenticator, and inspect UI, package version, Android service and advertiser registration.
- **environment**: Windows host; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; package `com.chimali.shrunk`.
- **expected_observed_result**: Build succeeded. In-place install succeeded without uninstall or data wipe. Installed version reports `0.1.0` / versionCode `1`. MainActivity showed “Advertising...” and “Ready to pair with a new host PC”; Fido2TransportService was foreground and Android listed `com.chimali.shrunk (Registered)` as an advertiser.
- **date**: 2026-11-05
- **limitations**: This verifies deployment and BLE startup only. No Windows FIDO revision selection, CTAP request, user verification or WebAuthn ceremony was performed. The record does not certify the remaining BLE or parent acceptance matrix.
- **status**: `Passed (latest APK build, in-place Mi update, BLE startup and registration) / Unverified (Mi WebAuthn and remaining acceptance)`.
- **artifact_link**: [Mi latest-build deployment and advertising](../../feature/fido2/build/reports/fido2/064/mi-current-build-deployment-20261105.txt), [BLE deployment record](../064-fido-ble-transport/implementation-evidence.md#record-093-latest-shrunkdebug-installed-and-advertising-on-mi-t020-partial)

### Record 075: Mi Radio-Off Error and Retry Recovery on Current Build (T035 — Partial)
- **requirement**: Parent transport unavailable-mode recovery; `FR-BT-SEL-050`, `FR-BT-SEL-060`
- **scope**: Verify that loss of the Bluetooth radio surfaces an unavailable transport state and explicit Retry restores BLE without fallback.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current `shrunkDebug` candidate installed on Mi.
- **named_procedure**: While the authenticator was advertising, turn Bluetooth off in Android Settings; inspect app UI/service; turn Bluetooth on; return to Chimali and tap Retry Authenticator.
- **environment**: Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; package `com.chimali.shrunk`, version `0.1.0`.
- **expected_observed_result**: With `bluetooth_on=0`, Chimali showed “Error Occurred / BLE advertising stopped unexpectedly”. After Android reported Bluetooth ON and Retry was tapped, Chimali returned to “Advertising... / Ready to pair with a new host PC”; Fido2TransportService was foreground and Android listed the app as a registered advertiser.
- **date**: 2026-11-05
- **limitations**: One radio-off/retry sequence only. No host pairing or WebAuthn ceremony. Other process-death, permission-loss, in-flight cancellation, bond recovery and 50-cycle procedures remain open.
- **status**: `Passed (Mi radio-off failure state and explicit BLE retry recovery) / Unverified (remaining parent lifecycle acceptance)`.
- **artifact_link**: [Mi radio-off and retry observations](../../feature/fido2/build/reports/fido2/064/mi-radio-off-retry-current-build-20261105.txt), [BLE recovery record](../064-fido-ble-transport/implementation-evidence.md#record-094-current-candidate-device-security-and-radio-recovery-t035-partial)

### Record 076: Current-Candidate Cross-Device FIDO BLE Discovery (T020 — Partial)
- **requirement**: Parent clean-install BLE default discovery; `FR-BT-SEL-010`, `FR-BT-SEL-020`
- **scope**: Verify that each current-build Android phone can observe a distinct remote FIDO BLE service advertisement from the other phone.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current source candidate and current `shrunkDebug` Mi install.
- **named_procedure**: Keep both apps actively advertising; run `:feature:fido2:connectedAndroidDeviceTest` filtered to `FidoBleAdvertiserDiscoveryDeviceTest` across both attached devices; inspect per-device XML and verify both apps remain advertising.
- **environment**: Windows host; Mi A2 / Android 10 / API29 / serial `2ac6876`; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`.
- **expected_observed_result**: The cross-device discovery test passed 1/1 on Mi and 1/1 on ASUS, each observing a distinct remote advertisement containing the FIDO service UUID. After the test, both apps still had their FIDO foreground services and registered advertisers.
- **date**: 2026-11-05
- **limitations**: This proves Android-to-Android service discovery, not Windows discovery, authenticated pairing, numeric comparison, GetInfo, MakeCredential, GetAssertion or acceptance of the default-selection story.
- **status**: `Passed (current-build Android cross-device FIDO BLE discovery on API29/API35) / Unverified (Windows and remaining parent acceptance)`.
- **artifact_link**: [Mi discovery test XML](../../feature/fido2/build/reports/fido2/064/mi-fido-ble-discovery-20261105.xml), [ASUS discovery test XML](../../feature/fido2/build/reports/fido2/064/asus-fido-ble-discovery-20261105.xml), [BLE discovery record](../064-fido-ble-transport/implementation-evidence.md#record-095-current-candidate-cross-device-fido-ble-discovery-t020-partial)

### Record 077: Mi Force-Stop and Explicit BLE Restart on Current Build (T035 — Partial)
- **requirement**: Parent process lifecycle and no automatic start; `FR-BT-SEL-050`, `FR-BT-SEL-060`
- **scope**: Confirm that the latest selected BLE mode stays stopped after force-stop/relaunch and only resumes after explicit user Start.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current `shrunkDebug` version `0.1.0` installed on Mi.
- **named_procedure**: Start from Advertising, force-stop `com.chimali.shrunk`, inspect service/advertising, relaunch MainActivity and inspect before tapping Start, then verify service and advertiser after explicit Start.
- **environment**: Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; package `com.chimali.shrunk`.
- **expected_observed_result**: Force-stop removed the FIDO foreground service and advertiser. Relaunch showed Ready to Start and did not auto-start a transport. Explicit Start returned the UI to Advertising, restored the foreground service and registered advertiser.
- **date**: 2026-11-05
- **limitations**: One BLE force-stop/relaunch sequence only; no Classic-mode process-death path, reboot, mid-ceremony preference change, Windows ceremony or 50-cycle test.
- **status**: `Passed (Mi BLE force-stop, stopped-on-relaunch and explicit restart behavior) / Unverified (remaining parent T035 and T041 acceptance)`.
- **artifact_link**: [Mi current-build force-stop/restart observations](../../feature/fido2/build/reports/fido2/064/mi-current-build-force-stop-restart-20261105.txt), [BLE lifecycle record](../064-fido-ble-transport/implementation-evidence.md#record-096-mi-current-build-force-stop-and-explicit-ble-restart-t035-partial)

### Record 078: ASUS Classic WebAuthn Attempt (T027/T031 — Incomplete)
- **requirement**: Parent Classic HID registration/assertion and switch acceptance; `FR-BT-SEL-030`, `FR-BT-SEL-040`, `FR-BT-SEL-050`
- **scope**: Attempt WebAuthn authentication over the current ASUS Classic HID selection after process restart and the authorized Bluetooth visibility consent.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current uncommitted candidate; `shrunkDebug` version `0.1.0`.
- **named_procedure**: Save Bluetooth HID (Classic), force-stop/relaunch, explicitly start Classic, allow Bluetooth visibility for 120 seconds, and submit Authenticate on WebAuthn.io for the synthetic account.
- **environment**: Windows 11 host; ChatGPT integrated browser; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`.
- **expected_observed_result**: Android showed the FIDO2/WebAuthn over Bluetooth HID selection and started the authenticator after the system visibility consent. WebAuthn.io returned “The operation either timed out or was not allowed.” No Windows security-key prompt, success message or credential result was visible. The app was restored to BLE afterward and its advertiser/foreground service were confirmed active.
- **date**: 2026-11-05
- **limitations**: The result is not a successful ceremony and does not distinguish browser focus, provider choice, HID pairing or protocol interoperability. No Windows dialog control was performed. T027/T031 and full T041 remain open.
- **status**: `Failed (this attempt did not complete WebAuthn) / Unverified (Classic transport root cause and parent acceptance)`.
- **artifact_link**: [ASUS Classic WebAuthn attempt](../../feature/fido2/build/reports/fido2/064/asus-classic-webauthn-attempt-20261105.txt), [parent switch tasks](tasks.md)

### Record 079: Cross-Device 100-Cycle GATT Reconnect Stress (T035 — Partial)
- **requirement**: Parent transport lifecycle and reconnect stability; `FR-BT-SEL-050`, `FR-BT-SEL-060`
- **scope**: Exercise 50 GATT connect, FIDO-service discovery, disconnect and close cycles in each Android-to-Android direction using both current builds.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current source candidate and `shrunkDebug` package version `0.1.0` on both phones.
- **named_procedure**: Keep both apps advertising. Run `FidoBleReconnectStressDeviceTest` on Mi as client to ASUS server, then on ASUS as client to Mi server; retain per-device instrumentation XML and process PSS samples.
- **environment**: Windows host; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`.
- **expected_observed_result**: Both directions passed 1/1 instrumentation test with zero failures, errors or skips: Mi→ASUS 50/50 cycles in 75.963s; ASUS→Mi 50/50 in 32.266s. ASUS PSS was 175,636 KiB before and 178,067 KiB immediately after the first run (+2,431 KiB), returning to 175,378 KiB after an idle interval; Mi PSS was 109,952 KiB before and 110,636 KiB after the reverse run (+684 KiB). Both foreground services and registered advertisers remained present after the test.
- **date**: 2026-11-05
- **limitations**: These cycles cover Android GATT connection and service discovery only. They do not exercise pairing, secured characteristic access, real CTAP requests, interrupted requests, Windows, stale-bond recovery or cancellation. PSS snapshots are process-wide and noisy, and do not prove absence of leaks. Full T035 and T041 remain open.
- **status**: `Passed (100 Android-to-Android GATT reconnect/service-discovery cycles) / Unverified (interrupted-request, Windows and remaining parent lifecycle acceptance)`.
- **artifact_link**: [Mi client→ASUS server XML](../../feature/fido2/build/reports/fido2/064/mi-fido-ble-reconnect-stress-20261105.xml), [ASUS client→Mi server XML](../../feature/fido2/build/reports/fido2/064/asus-fido-ble-reconnect-stress-20261105.xml), [PSS and procedure notes](../../feature/fido2/build/reports/fido2/064/cross-device-100-gatt-reconnect-cycles-20261105.txt), [parent T035](tasks.md)

### Record 080: ASUS Classic Mode Start and Visibility Consent (T027/T031 — Partial)
- **requirement**: Parent saved Classic transport activation and OS discoverability consent; `FR-BT-SEL-030`, `FR-BT-SEL-050`
- **scope**: Verify Classic selection activates after app restart, the app requests Android visibility consent, and accepting it starts the selected transport.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current `shrunkDebug` candidate, version `0.1.0`.
- **named_procedure**: Select Bluetooth HID (Classic) in app settings, restart the app process, explicitly Start Authenticator, accept Android's “make your phone visible to other Bluetooth devices for 120 seconds” request, then inspect app and Android Bluetooth state. Restore BLE selection and explicitly start the authenticator.
- **environment**: ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; Chimali package `com.chimali.shrunk`.
- **expected_observed_result**: After restart, the app displayed “FIDO2 / WebAuthn over Bluetooth HID.” Start requested the system visibility consent; after authorized Allow, Android reported `SCAN_MODE_CONNECTABLE_DISCOVERABLE`, and the app displayed Advertising. Windows pairing, HID connection and WebAuthn were not verified in this procedure. The saved choice was restored to FIDO Bluetooth LE and the app returned to BLE Advertising with its foreground service and registered advertiser.
- **date**: 2026-11-05
- **limitations**: Consent and app-level mode activation do not prove that Windows discovers or pairs with Chimali, that HID reports are exchanged, or that a WebAuthn ceremony succeeds. The 120-second visibility interval is transient. Parent T027/T031/T041 remain open.
- **status**: `Passed (Classic app-mode activation and OS visibility consent; BLE restored) / Unverified (Windows pairing, HID connection and Classic WebAuthn)`.
- **artifact_link**: [Classic visibility consent and restore notes](../../feature/fido2/build/reports/fido2/064/asus-classic-visibility-20261105.txt), [parent Classic tasks](tasks.md)

### Record 081: ASUS BLE Advertising Permission Revocation and Recovery (T035 — Partial)
- **requirement**: Parent selected-transport availability and explicit recovery; `FR-BT-SEL-050`, `FR-BT-SEL-060`
- **scope**: Revoke the selected BLE advertiser permission while active, then restore it and verify the saved BLE choice remains stopped until explicit Start.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current `shrunkDebug` candidate, version `0.1.0`.
- **named_procedure**: From BLE Advertising, revoke only `BLUETOOTH_ADVERTISE` using package manager; inspect Android ApplicationExitInfo and ActivityManager logs; regrant permission, relaunch MainActivity and explicitly Start.
- **environment**: ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`.
- **expected_observed_result**: Android recorded reason `PERMISSION CHANGE`, status 0, no trace, and ActivityManager scheduled the started `Fido2TransportService` for restart; the app process was killed by the OS as part of the permission change. No Java fatal stack trace appeared in the sampled log. After regrant, the app showed Ready to Start; explicit Start restored BLE Advertising, the foreground service and the registered advertiser.
- **date**: 2026-11-05
- **limitations**: The intermediate no-permission service state was not captured in-app while Android applied the runtime change. This does not verify permission revocation during GATT/CTAP traffic, stale-bond recovery, cancellation or the full both-mode lifecycle matrix.
- **status**: `Passed (ASUS permission-change process exit and explicit BLE recovery) / Unverified (in-ceremony interruption and remaining parent T035/T041 acceptance)`.
- **artifact_link**: [ASUS permission revocation and recovery observations](../../feature/fido2/build/reports/fido2/064/asus-advertise-permission-revocation-recovery-20261105.txt), [parent lifecycle tasks](tasks.md)

### Record 082: Current Worktree Ordinary Local CI Without Coverage (T039 — Partial)
- **requirement**: Parent candidate static analysis, compilation and required host/device-test source validation; `FR-BT-SEL-070`, `FR-BT-SEL-080`
- **scope**: Run the repository's ordinary local CI against the current Feature 064 worktree while preserving the owner's explicit coverage deferral.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current uncommitted source candidate.
- **named_procedure**: Run `ktlintAndroidDeviceTestSourceSetCheck`, then run a temporary copy of `tools/local-ci.ps1` from the ignored `build/` directory with `--no-problems-report`, using a worktree-local Gradle user home; invoke `-NoFix -NoConfigurationCache` without `-RunCoverage`.
- **environment**: Windows host; Gradle 9.6.0; connected Android SDK; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`.
- **expected_observed_result**: Android device-test KtLint check passed (48 tasks; 6 executed). Ordinary local CI passed in 317.78 seconds: KtLint, Detekt, Release lint, comprehensive configured compilation, unit-test tasks, and configured KMP Android host-test tasks. Gradle reported the tasks successful; most test outputs were UP-TO-DATE in the existing build cache.
- **date**: 2026-11-05
- **limitations**: Coverage was deliberately omitted by owner direction. This does not establish raw-count nonregression, coverage floors/completeness, hosted enforcement, Windows acceptance or physical Bluetooth interop. The default Gradle problems report could not be written in the restricted environment; the temporary script disabled that report. The first ordinary invocation stopped at that report write before tests.
- **status**: `Passed (ordinary local CI without coverage; configured tasks successful, most outputs up-to-date) / Deferred by owner (coverage) / Unverified (trusted coverage nonregression and hosted acceptance)`.
- **artifact_link**: [temporary local-CI script](../../build/local-ci-no-problems.ps1), [current task list](tasks.md), [BLE local validation record](../064-fido-ble-transport/implementation-evidence.md#record-100-current-worktree-ordinary-local-ci-without-coverage-t039-partial)

### Record 083: ASUS Classic Visibility and Windows Discovery Attempt (T027/T031 — Partial)
- **requirement**: Parent Classic selection availability and lifecycle; `FR-BT-SEL-030`, `FR-BT-SEL-040`, `FR-BT-SEL-050`
- **scope**: Verify that the selected Classic transport starts, requests Android discoverability, and can be discovered from the Windows host.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current uncommitted candidate, `shrunkDebug` version `0.1.0`.
- **named_procedure**: Select Bluetooth HID (Classic), restart Chimali, explicitly start, accept the Android visibility prompt, inspect Bluetooth mode and service, and attempt host-side device listing.
- **environment**: Windows 11 host; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`.
- **expected_observed_result**: Android displayed the authorized 120-second visibility prompt; after Allow, `SCAN_MODE_CONNECTABLE_DISCOVERABLE` and the Chimali transport foreground service were observed. The app showed Advertising in Bluetooth HID mode. Android's event log returned to `SCAN_MODE_CONNECTABLE` 210 seconds after entry to discoverable mode, despite the prompt's 120-second text. Windows Settings accessibility was unavailable and `Get-PnpDevice` returned Access Denied, so Windows discovery/pairing could not be observed. The app was restored to BLE Advertising and Android returned to connectable-only mode.
- **date**: 2026-11-05
- **limitations**: No Windows discovery, pairing, HID connection/report, registration, authentication or ceremony result. The observed 210-second Android discoverability interval needs follow-up; host side not visible through current controls.
- **status**: `Passed (ASUS Classic start and system visibility consent) / Unverified (Windows discovery and full parent acceptance)`.
- **artifact_link**: [ASUS Classic discovery attempt](../../feature/fido2/build/reports/fido2/064/asus-classic-discovery-attempt-20261105.txt), [BLE Classic record](../064-fido-ble-transport/implementation-evidence.md#record-101-asus-classic-visibility-and-windows-discovery-attempt-t027t031-partial)

### Record 084: Mi API 29 Pattern-Based User Verification Failure and Candidate Correction (T027 — Partial)
- **requirement**: Parent physical registration and user verification acceptance; `FR-BT-SEL-040`, `FR-BT-SEL-050`; BLE `T020` registration acceptance.
- **scope**: Diagnose the Mi A2 required-user-verification registration failure and prepare a compatibility correction.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current uncommitted source candidate, built `shrunkDebug` package version `0.1.0`.
- **named_procedure**: Trace the reported message through registration gating, inspect AndroidX authenticator support for API 29, update device-credential detection and prompt configuration, build and install the updated `com.chimali.shrunk` package.
- **environment**: Windows 11 host; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`.
- **expected_observed_result**: User reported WebAuthn registration failure “User verification is required before credential registration” on a phone secured with a pattern and no fingerprint. Code inspection found API 29's unsupported `DEVICE_CREDENTIAL` availability query and combined prompt-authenticator mask. The candidate uses `KeyguardManager.isDeviceSecure()` and the legacy AndroidX device-credential prompt option on API <30. `:app:assembleShrunkDebug` succeeded in 19m42s. Signature mismatch prevented `install -r`; under prior explicit authorization, only `com.chimali.shrunk` was uninstalled/reinstalled. The user granted location only while using the app, selected Passkey Authenticator during onboarding, and explicitly started BLE; Advertising and the transport foreground service were observed. The integrated WebAuthn.io tab is open but reports no page focus; corrected registration has not yet been attempted.
- **date**: 2026-11-05
- **limitations**: Root cause is supported by AndroidX API documentation and source flow, but the corrected prompt and required-UV registration have not been exercised on Mi. Package-local wallet/passkeys/settings were cleared during the authorized reinstall. Coverage was not run.
- **status**: `Diagnosed and corrected in source; build succeeded and candidate installed / Unverified (Mi registration retest and physical acceptance)`.
- **artifact_link**: [Mi API 29 pattern user-verification notes](../../feature/fido2/build/reports/fido2/064/mi-api29-pattern-user-verification-20261105.txt), [BLE user-verification record](../064-fido-ble-transport/implementation-evidence.md#record-102-mi-api29-pattern-based-user-verification-failure-and-candidate-correction-t020-partial)

### Record 085: ASUS WebAuthn Registration Success Confirmed (T027 — Partial)
- **requirement**: Parent transport registration and BLE-default selection; `FR-BT-SEL-020`, `FR-BT-SEL-030`; BLE `T020` registration evidence.
- **scope**: Record the latest ASUS registration report with the integrated browser's success indicator.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7`; ASUS installed APK SHA-256 `07f5c1e66b3166e7440a312fce94cc9bfde5edfbdb3278b4dbd83f0aa9917876`.
- **named_procedure**: The user completed WebAuthn registration on ASUS; inspect the integrated WebAuthn.io result and read device package/service identity.
- **environment**: Windows 11; ChatGPT integrated browser at WebAuthn.io; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; synthetic account `codex-synthetic-064-20261103`.
- **expected_observed_result**: User reported completing registration on ASUS. The integrated WebAuthn.io page displayed “Success! Now try to authenticate...” after the ceremony. The ASUS installed APK hash was recorded, and `Fido2TransportService` was foreground.
- **date**: 2026-11-05
- **limitations**: This records ASUS registration only. The browser success and user report do not expose Android UV flags or CTAP response bytes for this ceremony. It does not verify Mi/API29 pattern support, assertion, the switch matrix, or complete parent T027/T031/T041 acceptance.
- **status**: `Passed (ASUS WebAuthn registration, browser success visible) / Unverified (Mi API29 pattern retest and remaining parent acceptance)`.
- **artifact_link**: [ASUS registration success notes](../../feature/fido2/build/reports/fido2/064/asus-web-authn-registration-success-20261105.txt), [BLE registration record](../064-fido-ble-transport/implementation-evidence.md#record-103-asus-webauthn-registration-success-confirmed-t020-partial)

### Record 086: ASUS WebAuthn Assertion Rejected Before App-Side GATT Session (T041 — Partial)
- **requirement**: Parent end-to-end WebAuthn acceptance and BLE/Classic ceremony matrix; `FR-BT-SEL-020`, `FR-BT-SEL-030`, `FR-BT-SEL-050`.
- **scope**: Record the assertion attempt immediately following the ASUS registration success observation.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7`; ASUS installed APK SHA-256 `07f5c1e66b3166e7440a312fce94cc9bfde5edfbdb3278b4dbd83f0aa9917876`.
- **named_procedure**: Click Authenticate in the integrated WebAuthn.io page for the registered synthetic account and inspect the reported Windows result plus ASUS foreground/GATT logs.
- **environment**: Windows 11; ChatGPT integrated browser/WebAuthn.io; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; account `codex-synthetic-064-20261103`.
- **expected_observed_result**: User reported Windows showed “No se puede usar esta clave de seguridad. Prueba con otra distinta.” Browser showed no login-success result. ASUS MainActivity was resumed and `Fido2TransportService` foreground. Filtered logs contained no app-side GATT connection/session, revision selection or CTAP operation in the attempt window. A follow-up Bluetooth Manager snapshot listed the package both as a registered GATT server and in the ongoing advertising set; this verifies current advertising, not Windows discovery or pairing during the failed attempt.
- **date**: 2026-11-05
- **limitations**: Evidence points to failure before an observed app-side GATT session but does not identify whether Windows key selection, Bluetooth routing/pairing or browser security-key handling caused it. No dialog screenshot, GATT operation or authenticator response was captured. This does not invalidate the preceding registration success record, but it does not verify assertion.
- **status**: `Failed (this assertion attempt; user-reported Windows error) / Unverified (failure cause and remaining parent acceptance)`.
- **artifact_link**: [ASUS assertion failure notes](../../feature/fido2/build/reports/fido2/064/asus-webauthn-assertion-security-key-unavailable-20261105.txt), [prior ASUS registration record](#record-085-asus-webauthn-registration-success-confirmed-t027--partial)

### Record 087: API 28–29 Secure Device-Credential Availability on Connected Phones (T041 — Partial)
- **requirement**: Parent physical UV readiness and acceptance evidence; `FR-BT-SEL-020`, `FR-BT-SEL-050`.
- **scope**: Verify the corrected platform credential-availability branch on API29 and compare API35 behavior with AndroidX.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted source/test changes; shrunkDebug artifact SHA-256 `FF79746D1A0F4FF899A8EA3F57D44A3546538834086A6ABDA2348DD94832BE1B`.
- **named_procedure**: Run `:feature:fido2:connectedAndroidDeviceTest` filtered to `UserVerificationServiceDeviceTest` on the connected Mi/API29 and ASUS/API35 devices.
- **environment**: Gradle 9.6.0; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`.
- **expected_observed_result**: Mi passed 1/1 with zero skips; its configured secure credential satisfied the API<30 KeyguardManager branch and `isPinAvailable()` returned true. ASUS passed 1/1 with zero skips; service availability matched `BiometricManager.canAuthenticate(DEVICE_CREDENTIAL)`. Exact per-device XML is linked in the artifact.
- **date**: 2026-11-05
- **limitations**: This is a platform availability check, not a user-entered BiometricPrompt or WebAuthn ceremony. Mi/API29 registration and Windows assertion remain unverified; no coverage gate was run.
- **status**: `Passed (credential availability detection on API29/API35) / Unverified (Mi WebAuthn registration, Windows assertion and full acceptance)`.
- **artifact_link**: [API-versioned device test notes](../../feature/fido2/build/reports/fido2/064/user-verification-device-test-api29-20261105.txt), [Mi XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi%20A2%20-%2010.xml), [ASUS XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml)
### Record 088: API 29 Device-Credential Detection and FIDO Prompt Builder Compatibility (T041 — Partial)
- **requirement**: Parent user-verification readiness for WebAuthn acceptance; `FR-BT-SEL-020`, `FR-BT-SEL-050`.
- **scope**: Verify legacy API credential detection and shared BiometricPrompt configuration on API29, plus the API35 authenticator mask path.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted source/test changes; shrunkDebug artifact SHA-256 `FF79746D1A0F4FF899A8EA3F57D44A3546538834086A6ABDA2348DD94832BE1B`.
- **named_procedure**: Run `:feature:fido2:connectedAndroidDeviceTest` filtered to `UserVerificationServiceDeviceTest` on the connected Mi/API29 and ASUS/API35 devices.
- **environment**: Gradle 9.6.0; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`.
- **expected_observed_result**: Both devices passed 2/2 with zero XML failures or skips. On Mi, the secure pattern made `KeyguardManager.isDeviceSecure` true and `isPinAvailable()` returned true. On both devices, the shared FIDO prompt info built without displaying a prompt; ASUS credential availability matched AndroidX's API30+ query.
- **date**: 2026-11-05
- **limitations**: Gradle exited successfully but the Android test listener printed an `ArrayIndexOutOfBoundsException` while reporting one finish event; the XML reports contain both test cases as passed on each device. The targeted KtLint device-test source check also passed; full local CI remains unrun. This does not verify an actual prompt interaction, Mi WebAuthn registration, Windows assertion, or full acceptance. Coverage remains deferred.
- **status**: `Passed (API29 credential detection and API28–29 prompt builder; API35 comparison) / Unverified (actual WebAuthn ceremonies and full acceptance)`.
- **artifact_link**: [API-versioned device test notes](../../feature/fido2/build/reports/fido2/064/user-verification-device-test-api29-20261105.txt), [Mi XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi%20A2%20-%2010.xml), [ASUS XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml)
### Record 089: Current Worktree Local CI Without Deferred Coverage (T039 — Partial)
- **requirement**: Parent local static analysis, compilation and host-test verification before final coverage/nonregression acceptance.
- **scope**: Validate current Feature 064 working-tree changes using ordinary local CI, explicitly excluding the deferred coverage gate.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes.
- **named_procedure**: Run `build/local-ci-no-problems.ps1 -NoFix -NoConfigurationCache` with a worktree-local Gradle user home; do not pass `-RunCoverage`.
- **environment**: Windows 11; Gradle 9.6.0; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; connected Mi A2/API29 and ASUS AI2302/API35 for the separate focused instrumentation run.
- **expected_observed_result**: Local CI passed in 510.42 seconds: KtLint, Detekt, release lint, configured compilation, unit tests and KMP Android host tests. The focused credential/prompt instrumentation separately passed 2/2 per phone, with zero XML failures/skips, and targeted device-test KtLint passed.
- **date**: 2026-11-05
- **limitations**: Kotlin daemon marker access was denied, so Gradle used compiler fallback. AndroidTestResultListener printed an `ArrayIndexOutOfBoundsException` while reporting one finish event, although each device XML shows 2/2 passed. No coverage or accepted-target regression assessment was run, as coverage is deferred; hosted integration and physical acceptance remain unverified.
- **status**: `Passed (ordinary local quality/compile/unit/host checks) / Deferred (coverage by owner direction) / Unverified (trusted nonregression, hosted integration and physical acceptance)`.
- **artifact_link**: [Current local-CI summary](../../feature/fido2/build/reports/fido2/064/current-worktree-local-ci-no-coverage-20261105.txt), [Mi focused device-test XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi%20A2%20-%2010.xml), [ASUS focused device-test XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml)

### Record 090: ASUS Assertion Retry Reached CTAP but Did Not Complete in WebAuthn (T041 — Partial)
- **requirement**: Parent physical WebAuthn ceremony and BLE-default acceptance; `FR-BT-SEL-020`, `FR-BT-SEL-030`, `FR-BT-SEL-050`.
- **scope**: Correlate a new integrated-browser Authenticate attempt with ASUS GATT, CTAP, browser and foreground-service observations; this is distinct from Record 086's attempt with no observed app-side GATT session.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7`; installed ASUS `shrunkDebug` APK SHA-256 `07f5c1e66b3166e7440a312fce94cc9bfde5edfbdb3278b4dbd83f0aa9917876`.
- **named_procedure**: Start Authenticate on integrated WebAuthn.io for the synthetic account and correlate returned browser state with ASUS logcat and Bluetooth service state.
- **environment**: Windows 11; ChatGPT integrated browser / WebAuthn.io; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; synthetic account `codex-synthetic-064-20261103`.
- **expected_observed_result**: ASUS accepted a GATT connection, negotiated MTU 517, selected revision `0x20` and enabled notifications. The app logged one GetAssertion returning`NO_CREDENTIALS`, followed by an assertion with one candidate that completed internally and produced a response. A third request remained pending and timed out after 120 seconds. A later retry again produced a no-candidate response and an internally completed assertion, then the GATT link disconnected and the app logged an invalid state. The user subsequently reported the Windows dialog “No se puede usar esta clave de seguridad. Prueba con otra distinta.” The integrated WebAuthn page did not show login success, and the read-only ASUS screen capture showed Chimali on its Advertising home screen without an authentication prompt.
- **date**: 2026-11-05
- **limitations**: CTAP handler success does not prove Windows accepted or received the response. Request/client correlation, prompt completion, notification delivery to the host and the exact reason for the pending request remain unverified. This does not invalidate the earlier no-GATT observation in Record 086; it is a separate later attempt.
- **status**: `Failed (user-reported Windows rejection; no login success) / Unverified (request correlation and failure cause)`.
- **artifact_link**: [ASUS assertion CTAP follow-up](../../feature/fido2/build/reports/fido2/064/asus-webauthn-assertion-ctap-followup-20261105.txt), [earlier no-GATT attempt](#record-086-asus-webauthn-assertion-rejected-before-app-side-gatt-session-t041--partial)

### Record 091: Retained Request State Closes Prompt Startup Windows (T041 — Partial)
- **requirement**: A BLE assertion request must reach user verification after routing into the authentication prompt; `FR-BT-SEL-020`, `FR-BT-SEL-050`.
- **scope**: Make home navigation and both prompt viewmodels collect the bus's retained registration/authentication request StateFlows, eliminating startup gaps around non-replaying SharedFlow subscriptions. Verify dispatch-before-auth-presenter construction.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes.
- **named_procedure**: Run focused Android host tests for `AuthenticationIntegrationTest`, `RegistrationOwnerIntegrationTest` and `Fido2UiEventBusTest`; run Android main/host KtLint checks.
- **environment**: Windows 11; Gradle 9.6.0; local worktree Gradle home `.gradle/codex-user-home`.
- **expected_observed_result**: 54 focused tests passed with zero failures/errors/skips, including a test that dispatches GetAssertion before creating the prompt ViewModel and verifies `AwaitingUserConsent`. KtLint passed for affected main/host source sets.
- **date**: 2026-11-05
- **limitations**: No app was installed or changed on the ASUS due to the signer mismatch in BLE Record 111. This validates prompt-request delivery at the host/ViewModel layer only, not Compose navigation or Windows acceptance. The RCA remains a supported hypothesis until the updated build is tested physically.
- **status**: `Passed (host-level retained request routing checks) / Unverified (device prompt delivery and physical ceremonies)`.
- **artifact_link**: [Authentication integration XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.integration.AuthenticationIntegrationTest.xml), [registration owner XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.integration.RegistrationOwnerIntegrationTest.xml), [event bus XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.presentation.navigation.Fido2UiEventBusTest.xml)

### Record 092: ASUS In-Place Deployment and Post-Fix Ceremony Handoff (T041 — Partial)
- **requirement**: Validate that retained request routing completes a real BLE authentication and does not regress the parent BLE-default behavior; `FR-BT-SEL-020`, `FR-BT-SEL-050`.
- **scope**: Rebuild the candidate with the installed ASUS signer, update without data loss, start FIDO BLE, and attempt WebAuthn.io authentication with the existing synthetic credential.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes; APK SHA-256 `892F0F7E5A2B4876BAAB1A62141EA0E4E9E5776A59E6B979E9705F78C9C2FF04`.
- **named_procedure**: Build `:app:assembleShrunkDebug` with worktree-local Gradle home and `JAVA_TOOL_OPTIONS=-Duser.home=C:/Users/octav`; verify signer SHA-256 `f6c948b953eb7ec7153e95f0ae4830254c7b0f882cb741e54a18e21a2a3c7510`; update using `adb install -r`; launch the app, select Start Authenticator and initiate Authenticate from integrated WebAuthn.io.
- **environment**: Windows 11; ChatGPT integrated browser; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; synthetic account `codex-synthetic-064-20261103`.
- **expected_observed_result**: Candidate signer matched the installed package and in-place update returned `Success`; local app data was preserved. The app's FIDO GATT server registered with status 0 and the foreground transport service began advertising. The integrated browser showed no WebAuthn result after the automation click, and no ASUS GATT connection or CTAP request was observed.
- **date**: 2026-11-05
- **limitations**: No Windows security dialog, Android prompt, GetAssertion, browser success, or post-fix ceremony result was observed. The automation click did not produce a visible WebAuthn activation or request; a direct user click is needed to continue this test. Parent physical acceptance remains open.
- **status**: `Passed (matching-signer update and BLE foreground start) / Unverified (post-fix Windows authentication and complete parent acceptance)`.
- **artifact_link**: [Candidate APK](../../app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk), [BLE deployment and retest evidence](../064-fido-ble-transport/implementation-evidence.md#record-114-asus-in-place-deployment-and-post-fix-retest-attempt-t023--partial), [parent integration handoff](integration.md)

### Record 093: Latest-Candidate Cross-Device GATT Reconnect Stress (T035 — Partial)
- **requirement**: Validate parent lifecycle behavior after repeated BLE connections and disconnections; `FR-BT-SEL-020`, `FR-BT-SEL-050`.
- **scope**: Use Mi/API29 as the GATT scanner and the updated ASUS/API35 as FIDO BLE advertiser for 50 service-discovery/connect/disconnect cycles; inspect ASUS service state and scanner heap readings.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes; ASUS APK SHA-256 `892F0F7E5A2B4876BAAB1A62141EA0E4E9E5776A59E6B979E9705F78C9C2FF04`.
- **named_procedure**: Build `:feature:fido2:assembleAndroidDeviceTest`, install the resulting device-test APK on Mi, and run `FidoBleReconnectStressDeviceTest` with `runFidoBleReconnectStress=true`.
- **environment**: Windows 11; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; candidate installed on ASUS in place with existing app data retained.
- **expected_observed_result**: The instrumentation run passed 1/1 in 79.275 seconds. All 50 GATT connection/service-discovery/disconnection cycles succeeded. Mi Java heap changed from 1,943,384 to 2,900,328 bytes and native heap from 6,046,992 to 7,283,928 bytes. ASUS GATT server remained registered and its foreground service remained active after the final disconnect.
- **date**: 2026-11-05
- **limitations**: This repeated discovery-only GATT procedure does not verify classic mode, security/pairing, interrupted CTAP work, process death/reboot, settings switch acceptance, or memory leak freedom. Parent T035 and T041 remain open.
- **status**: `Passed (50 latest-candidate GATT reconnect cycles) / Unverified (full parent lifecycle and acceptance matrix)`.
- **artifact_link**: [Sanitized output and heap samples](../../feature/fido2/build/reports/fido2/064/asus-postfix-reconnect-stress-20261105.txt), [BLE T035 evidence](../064-fido-ble-transport/implementation-evidence.md#record-115-post-deployment-50-cycle-mi-to-asus-gatt-reconnect-stress-t035--partial), [integration plan](integration.md)

### Record 094: Current Worktree Local CI Without Coverage (T039 — Partial)
- **requirement**: Run parent local static/host validation and keep coverage/nonregression and hosted acceptance distinct; `FR-BT-SEL-060`.
- **scope**: Execute the repository's ordinary local CI phases without its opt-in coverage gate, while preserving the report-write failure of the unmodified script.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes.
- **named_procedure**: Attempt `tools/local-ci.ps1 -NoFix -NoConfigurationCache`; run `build/local-ci-no-problems.ps1 -NoFix -NoConfigurationCache` as workaround. Coverage was not enabled.
- **environment**: Windows 11; Gradle 9.6.0; worktree-local Gradle home `.gradle/codex-user-home`.
- **expected_observed_result**: KtLint tasks reached `UP-TO-DATE`, but the official script's Gradle invocation exited nonzero after `AccessDeniedException` while writing `build/reports/problems/problems-report.html`; no source lint error was reported. With `--no-problems-report`, KtLint, Detekt, release lint, comprehensive compilation, unit tests and KMP Android host tests passed in 63.38 seconds.
- **date**: 2026-11-05
- **limitations**: The workaround addresses only Gradle report output. Coverage, trusted accepted-target comparison, connected device lifecycle acceptance, and hosted integration were not run. Coverage is deferred by owner direction and the hosted runner remains waived/disabled.
- **status**: `Passed (ordinary local static/compile/unit/host validation via workaround) / Failed (official script report output) / Deferred (coverage by owner direction) / Unverified (trusted target comparison and physical acceptance)`.
- **artifact_link**: [Local CI result and failure details](../../feature/fido2/build/reports/fido2/064/local-ci-no-coverage-20261105.txt), [BLE local CI record](../064-fido-ble-transport/implementation-evidence.md#record-116-current-worktree-local-ci-without-coverage-t038--partial), [integration plan](integration.md)

### Record 095: BLE Terminal Notification Timing Evidence (T041 — Partial)
- **requirement**: Parent acceptance diagnosis requires a distinct physical WebAuthn response boundary; `FR-BT-SEL-020`, `FR-BT-SEL-050`.
- **scope**: Review and retain the component diagnostic that times a fully reassembled FIDO BLE CTAP request through Android's final response-notification acknowledgement. This is a handoff metric, not proof of a successful WebAuthn ceremony.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted diagnostic and test changes.
- **named_procedure**: Run the BLE notification-writer host test and KtLint checks as documented in BLE Record 117; inspect the emitted result fields and procedure boundary.
- **environment**: Windows 11; Gradle 9.6.0; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; host-only verification, no PC ceremony performed.
- **expected_observed_result**: Focused BLE host suite passed 10/10 with zero failures/errors/skips and both source-set KtLint checks passed. Diagnostic output excludes request and credential data and reports terminal notification acknowledgement.
- **date**: 2026-11-05
- **limitations**: Physical GetAssertion, diagnostic timing value, Windows receipt/acceptance, both switch-direction ceremonies, lifecycle matrix and full SC-001–006 acceptance remain open. A notification acknowledgement from Android does not establish host receipt. Coverage remains deferred by owner direction.
- **status**: `Passed (scoped host diagnostic and handoff review) / Unverified (physical Windows result and parent acceptance matrix)`.
- **artifact_link**: [BLE diagnostic record](../064-fido-ble-transport/implementation-evidence.md#record-117-ble-request-to-response-notification-diagnostic-t023--partial), [transport diagnostic source](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleGattTransport.kt), [parent acceptance plan](integration.md)

### Record 096: Worktree Local CI After BLE Diagnostic Changes (T039 — Partial)
- **requirement**: Validate local checks and ordinary tests while respecting the owner's explicit coverage deferral.
- **scope**: Run KtLint, Detekt, release lint, compilation, unit tests and KMP Android host tests after the BLE response-handoff diagnostic changes.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes.
- **named_procedure**: `build/local-ci-no-problems.ps1 -NoFix -NoConfigurationCache` with worktree-local Gradle home and the profile-specific `user.home` for the known local signing setup.
- **environment**: Windows 11; Gradle 9.6.0; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`.
- **expected_observed_result**: KtLint, Detekt, release lint, source/test compilation, unit tests and KMP Android host tests passed in 195.49 seconds. Kotlin daemon marker AccessDeniedException warnings were followed by successful compiler fallback.
- **date**: 2026-11-05
- **limitations**: Coverage was not run by owner direction. Trusted candidate-versus-accepted-target nonregression, hosted integration and device acceptance remain separate.
- **status**: `Passed (ordinary local checks and tests) / Deferred (coverage by owner direction) / Unverified (target comparison, hosted and remaining device acceptance)`.
- **artifact_link**: [BLE local CI evidence](../064-fido-ble-transport/implementation-evidence.md#record-118-worktree-local-ci-after-ble-diagnostic-changes-t038--partial), [local CI helper](../../build/local-ci-no-problems.ps1)

### Record 097: Mi/API29 Candidate Advertising Evidence (T041 — Partial)
- **requirement**: Capture component transport startup on the Mi/API29 device as part of parent feature acceptance; `FR-BT-SEL-010`, `FR-BT-SEL-020`.
- **scope**: Deploy the corrected signed build, launch Chimali, select the FIDO2 authenticator and verify GATT registration and BLE advertising.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes; APK SHA-256 `3F7D2397678637CDDE4544DDD7943F66B22A12CE8218D12A0E7CA4FC655C78A3`.
- **named_procedure**: Verify package signer, reinstall only the previously authorized Mi package `com.chimali.shrunk`, start the FIDO2 authenticator, inspect app UI and Android GATT/advertising callbacks.
- **environment**: Windows 11; Xiaomi Mi A2; Android 10/API29; ADB serial `2ac6876`.
- **expected_observed_result**: Install/launch succeeded; FIDO GATT and Device Information services were added with status 0; advertising set started with status 0; UI reported readiness to pair with a new host PC.
- **date**: 2026-11-05
- **limitations**: Mi local app data was removed during the explicitly authorized reinstall. No Windows pairing/ceremony or transport switch, restart, cancellation, recovery or full SC-001–006 matrix was exercised. Parent T027, T031, T035 and T041 remain open; coverage remains deferred.
- **status**: `Passed (Mi/API29 BLE startup smoke check) / Unverified (parent cross-transport and lifecycle acceptance)`.
- **artifact_link**: [Component Mi deployment evidence](../064-fido-ble-transport/implementation-evidence.md#record-119-miapi29-corrected-build-deployment-and-ble-advertising-t023--partial), [sanitized deployment result](../../feature/fido2/build/reports/fido2/064/mi-deployment-20261105.txt), [integration plan](integration.md)

### Record 098: Integrated-Browser Attempt Did Not Start Mi Ceremony (T041 — Partial)
- **requirement**: Obtain an observable Windows-hosted WebAuthn ceremony against the selected BLE transport; `FR-BT-SEL-030`, `FR-BT-SEL-050`.
- **scope**: Attempt registration from the integrated WebAuthn.io page while Mi was advertising and check for a Windows prompt and Mi GATT/CTAP activity.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes.
- **named_procedure**: Click the authorized synthetic-account Register button in the integrated browser and inspect current browser accessibility state, open Windows windows and Mi device logs.
- **environment**: Windows 11; integrated WebAuthn.io tab; Xiaomi Mi A2 / Android 10/API29.
- **expected_observed_result**: No Windows security-key dialog or visible WebAuthn result appeared, and Mi showed no new GATT/CTAP request. The attempt therefore did not reach the authenticator or exercise BLE transport.
- **date**: 2026-11-05
- **limitations**: The host ceremony, Windows rejection diagnosis, transport selection and parent SC-001–006 matrix remain open; browser activation/routing is a possible blocker but no root cause is established.
- **status**: `Failed (integrated-browser attempt did not start) / Unverified (host-to-Mi ceremony and BLE result)`.
- **artifact_link**: [Mi attempt evidence](../064-fido-ble-transport/implementation-evidence.md#record-120-integrated-browser-registration-attempt-on-mi-t023--unverified), [integration plan](integration.md)

### Record 099: Mi/API29 User-Verification Readiness (T041 — Partial)
- **requirement**: Verify the API29 device credential prerequisite for the Mi BLE authenticator; `FR-BT-SEL-030`, `FR-BT-SEL-050`.
- **scope**: Run the focused prompt-configuration and secure-device-credential instrumentation class on Mi/API29.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes.
- **named_procedure**: Run `:feature:fido2:connectedAndroidDeviceTest` filtered to `UserVerificationServiceDeviceTest`, targeting ADB serial `2ac6876`.
- **environment**: Xiaomi Mi A2 / Android 10/API29; Windows 11; Gradle 9.6.0.
- **expected_observed_result**: Both focused device tests passed with zero failures, errors or skips; Android reports a configured device credential and Chimali builds the API29 FIDO prompt configuration.
- **date**: 2026-11-05
- **limitations**: No user entered the pattern; no Windows request, credential registration/authentication, or parent switch acceptance was exercised.
- **status**: `Passed (device credential availability and prompt configuration) / Unverified (prompt interaction, Windows ceremony and parent matrix)`.
- **artifact_link**: [BLE component test evidence](../064-fido-ble-transport/implementation-evidence.md#record-121-api-29-pattern-availability-and-fido-prompt-configuration-t036--partial), [instrumentation XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi%20A2%20-%2010.xml)

### Record 100: Mi/API29 BLE Force-Stop Recovery (T035 — Partial)
- **requirement**: Verify parent transport cleanup and explicit restart after BLE process termination; `FR-BT-SEL-040`, `FR-BT-SEL-050`.
- **scope**: Force-stop the Mi Chimali process during advertising, relaunch the authenticator and verify BLE remains stopped until explicit Start.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes.
- **named_procedure**: Force-stop package `com.chimali.shrunk`; explicitly launch `com.chimali.MainActivity` (avoid LeakCanary's launcher activity); inspect service/UI, tap Start, inspect GATT service and advertiser status callbacks.
- **environment**: Xiaomi Mi A2; Android 10/API29; Windows 11; candidate package signer matches the prior ASUS-compatible Android debug identity.
- **expected_observed_result**: Service and advertising stopped after force-stop; MainActivity relaunched in Ready to Start state without restarting transport; explicit Start registered the FIDO and Device Information services and resumed advertising.
- **date**: 2026-11-05
- **limitations**: One BLE force-stop case only; no Classic path, OS process death, reboot or in-flight ceremony. Parent T035 and the full acceptance matrix remain open.
- **status**: `Passed (Mi BLE force-stop/explicit restart slice) / Unverified (remaining parent lifecycle cases)`.
- **artifact_link**: [BLE component restart evidence](../064-fido-ble-transport/implementation-evidence.md#record-122-miapi29-ble-force-stop-and-explicit-restart-t035--partial), [sanitized report](../../feature/fido2/build/reports/fido2/064/mi-force-stop-restart-20261105.txt)

### Record 101: Mi/API29 BLE Background-Service Slice (T035 — Partial)
- **requirement**: Verify parent BLE availability persists while the Android app is backgrounded; `FR-BT-SEL-040`, `FR-BT-SEL-050`.
- **scope**: Background the advertising Mi app, inspect foreground service and persistent notification, then restore the activity.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes.
- **named_procedure**: Send `KEYCODE_HOME`; inspect the app process, foreground transport service, and notification; explicitly relaunch MainActivity.
- **environment**: Xiaomi Mi A2; Android 10/API29; Windows 11.
- **expected_observed_result**: The transport process/service and ongoing notification remained present while the UI was backgrounded. Returning to MainActivity showed Advertising and the foreground service remained active.
- **date**: 2026-11-05
- **limitations**: Service persistence does not prove continuous remote Windows discovery. No OS process death, reboot, Classic or cross-transport ceremony was exercised.
- **status**: `Passed (Mi BLE foreground-service background persistence) / Unverified (full parent lifecycle and remote acceptance)`.
- **artifact_link**: [BLE component background evidence](../064-fido-ble-transport/implementation-evidence.md#record-123-miapi29-ble-background-service-persistence-t029t035--partial), [sanitized report](../../feature/fido2/build/reports/fido2/064/mi-background-service-20261105.txt)

### Record 102: Required-UV Mi Registration at Windows Provider Selection (T041 — Partial)
- **requirement**: Establish a WebAuthn host request against the selected Mi BLE transport with user verification required; `FR-BT-SEL-030`, `FR-BT-SEL-050`.
- **scope**: Configure cross-platform attachment and a security-key hint, invoke registration, and determine whether Windows dispatches a request to the active Mi advertiser.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes.
- **named_procedure**: Set WebAuthn registration UV to Required, attachment to Cross-Platform, enable the Security Key hint, click Register, then inspect Windows window inventory and Mi GATT/CTAP logs.
- **environment**: Windows 11; Codex integrated WebAuthn.io tab; Xiaomi Mi A2 / Android 10/API29.
- **expected_observed_result**: A native Windows “Guardar la clave de paso” dialog opened, defaulting to a Windows-local passkey and offering “Cambiar”. The user has been asked to select the Security key provider. Mi had no GATT connection/write or CTAP request before provider selection.
- **date**: 2026-11-05
- **limitations**: Provider selection is pending. The Windows dialog establishes that WebAuthn reached the OS credential chooser, but not that the Mi authenticator was selected or contacted. No Mi registration or pattern entry occurred; parent T041 remains open.
- **status**: `In Progress (native Windows provider chooser open) / Unverified (Windows-to-Mi GATT, ceremonies and switch matrix)`.
- **artifact_link**: [BLE attempt details](../064-fido-ble-transport/implementation-evidence.md#record-124-cross-platform-required-uv-windows-credential-chooser-t023--in-progress), [sanitized report](../../feature/fido2/build/reports/fido2/064/mi-webauthn-required-uv-attempt-20261105.txt)
- **follow_up_observation**: A later Mi snapshot showed Bluetooth ON, the BLE advertiser foreground and no connected or bonded devices. No GATT/CTAP request was observed; physical pairing and the stale-bond recovery procedure remain pending.
- **browser_recheck**: The integrated WebAuthn.io page retained Required user verification, Cross-Platform attachment and the Security Key hint and displayed its prior no-focus message. After focusing the page heading, CUA reported the WebArea focused; the prior message remained in page content. Windows' enumerated window list did not show a separate credential chooser. No retry was submitted.

### Record 104: Classic HID Non-Initializing Capability Classification
- **requirement**: Report only reliably detectable transport capability before activation and defer unknown OEM profile support to the frozen selected transport; `FR-BT-SEL-050`, `TR-BT-SEL-020`.
- **scope**: Align Classic HID's capability result with the Mi/API29 observation that generic Bluetooth hardware can be present while Android cannot bind the OEM HID_DEVICE profile service.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes.
- **named_procedure**: Review `AndroidBluetoothCapabilityChecker.checkCapability` and `AndroidPlatformBluetoothHid.isSupported`; compile the Android main source set using `GRADLE_USER_HOME=.gradle/codex-user-home` and `:feature:fido2:compileAndroidMain --no-configuration-cache --no-problems-report`.
- **environment**: Windows 11; Gradle 9.6.0; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`.
- **expected_observed_result**: With adapter present and enabled, Classic HID now reports Unknown unless a known unsupported API level applies; it no longer treats generic Bluetooth hardware as proof that the OEM HID_DEVICE service is available. BLE's existing capability checks are unchanged. No transport is constructed or initialized during this check. Android main compilation succeeded in 19 seconds with 38 tasks; the Kotlin daemon could not create its temp marker under the profile directory, then the configured non-daemon fallback completed successfully.
- **date**: 2026-11-05
- **limitations**: No host/device tests were run, and this source change has not been installed on Mi. It changes only preflight classification; Mi's physical Classic profile-bind failure, parent T027/T031/T035/T041, and owner-deferred coverage remain open.
- **status**: `Passed (source change and Android main compilation) / Unverified (regression tests and device behavior)`.
- **artifact_link**: [Capability checker](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/ProcessTransportSelection.kt), [Classic API/hardware preflight](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/platform/AndroidPlatformBluetoothHid.kt), [Mi Classic profile-binding evidence](#record-055-mi-a2-classic-hid-startup-failure-and-ble-recovery-t035--partial)

### Record 103: Mi/API29 Controlled Process-Death BLE Recovery (T035 — Partial)
- **requirement**: Verify parent-selected BLE restarts after Android-managed process death without applying another transport or clearing credentials; `FR-BT-SEL-040`, `FR-BT-SEL-050`.
- **scope**: Induce a controlled app process death while Mi is advertising BLE, observe the started-service restart, then reopen MainActivity and confirm the selected/active mode.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes.
- **named_procedure**: Run the shell-controlled crash for `com.chimali.shrunk`, inspect ActivityManager and Bluetooth logs, then explicitly launch MainActivity and inspect the service and UI.
- **environment**: Xiaomi Mi A2; Android 10/API29; Windows 11; BLE process choice selected; no host ceremony active.
- **expected_observed_result**: ActivityManager restarted the sticky FIDO transport service after the shell-induced process death. The new process registered the FIDO and Device Information GATT services, restarted BLE advertising successfully, and MainActivity showed Advertising with BLE active. No fallback or app-data reset occurred.
- **date**: 2026-11-05
- **limitations**: Controlled crash evidence does not represent organic low-memory termination, reboot, pending-mode application, Classic recovery, in-flight ceremony safety or physical host acceptance. Parent T035/T041 remain open.
- **status**: `Passed (Mi BLE sticky-service recovery after controlled process death) / Unverified (remaining process, mode and ceremony matrix)`.
- **artifact_link**: [BLE component evidence](../064-fido-ble-transport/implementation-evidence.md#record-125-miapi29-controlled-process-death-ble-recovery-t035--partial), [sanitized log](../../feature/fido2/build/reports/fido2/064/mi-controlled-process-death-20261105.txt)

### Record 105: Mi/API29 Clean-Install BLE Default Startup (T041 — Partial)
- **requirement**: Verify clean-install transport behavior starts the specified BLE authenticator and preserves a single active selected transport; `FR-BT-SEL-010`, `FR-BT-SEL-020`, `TR-BT-SEL-010`, `TR-BT-SEL-030`.
- **scope**: Freshly install the shrunk candidate on Mi, choose Passkey Authenticator during onboarding, explicitly start it, and inspect the visible mode, foreground service and registered GATT server.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD recorded in the linked report, with staged and unstaged changes.
- **named_procedure**: Install `shrunkDebug` on Xiaomi Mi A2/API29, complete onboarding with Passkey Authenticator selected, tap Start Authenticator, then inspect MainActivity, foreground service and `dumpsys bluetooth_manager`.
- **environment**: Windows 11; Xiaomi Mi A2 (`jasmine`), Android 10/API29; ADB serial `2ac6876`; Bluetooth ON.
- **expected_observed_result**: Fresh setup reached the FIDO2/WebAuthn-over-FIDO-Bluetooth-LE home, showed `Advertising...`, started `Fido2TransportService` in the foreground, and registered the package in Android's GATT Server Map. No alternate Classic transport was activated.
- **date**: 2026-11-05
- **limitations**: This confirms the fresh-install BLE start on Mi only; it does not verify Windows discovery, either ceremony, credential preservation comparisons, switch directions, in-flight safety, or SC-001–006. Parent T027/T031/T035/T041 remain open. Coverage remains owner-deferred.
- **status**: `Passed (Mi fresh-install BLE startup) / Unverified (parent acceptance matrix and Windows ceremonies)`.
- **artifact_link**: [BLE component evidence](../064-fido-ble-transport/implementation-evidence.md#record-126-miapi29-fresh-install-and-ble-advertising-recovery-t029--partial), [sanitized Mi clean-install and advertising evidence](../../feature/fido2/build/reports/fido2/064/mi-clean-install-advertising-20261105.txt)

### Record 106: Classic HID API-Preflight Regression Source and Host Compilation
- **requirement**: Do not report Classic HID as definitely unsupported based only on generic Bluetooth hardware when public, non-initializing APIs cannot verify the OEM HID_DEVICE profile; `FR-BT-SEL-050`, `TR-BT-SEL-020`.
- **scope**: Extract the Classic API-level result mapping, add a host-test regression case for API O/P/Q, and compile Android host-test sources.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD recorded in the linked build report with staged and unstaged changes.
- **named_procedure**: Run `:feature:fido2:compileAndroidHostTest --no-configuration-cache --no-problems-report` after adding `classicHidCapabilityIsUnknownWhenAndroidCannotPreflightOemProfileBinding`.
- **environment**: Windows 11; Gradle 9.6.0; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`.
- **expected_observed_result**: Android host-test compilation succeeded in 22 seconds (41 tasks: 8 executed, 33 up-to-date). A Kotlin daemon temporary-marker `AccessDeniedException` triggered the successful non-daemon fallback. Test source covers O as Unsupported and P/Q as Unknown.
- **date**: 2026-11-05
- **limitations**: The regression test was compiled but not run. This does not establish Classic profile binding or Windows HID/WebAuthn interoperability on Mi. Full local CI, T039 coverage/nonregression and parent T027/T031/T035/T041 remain open or deferred.
- **status**: `Passed (source and host-test compilation) / Unverified (test execution, physical Classic activation and full acceptance)`.
- **artifact_link**: [Sanitized host compilation record](../../feature/fido2/build/reports/fido2/064/classic-hid-classifier-host-compile-20261105.txt), [capability checker](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/ProcessTransportSelection.kt), [host test](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/ProcessTransportSelectionTest.kt)
- **follow_up_static_analysis**: `:feature:fido2:ktlintAndroidMainSourceSetCheck` and `:feature:fido2:ktlintAndroidHostTestSourceSetCheck` both passed in 26 seconds (44 tasks: 8 executed, 36 up-to-date). Tests were not run.

### Record 107: Mi/API29 Windows Required-UV Request Admission and Timeout (T041 — Partial)
- **requirement**: Observe a parent-selected BLE transport handling a Windows-hosted required-UV registration through a terminal response; `FR-BT-SEL-030`, `FR-BT-SEL-050`.
- **scope**: Invoke the synthetic WebAuthn.io registration against the advertising Mi, select the Windows Bluetooth security-key route, and inspect browser result and sanitized Android request lifecycle.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with staged and unstaged changes; deployed build is recorded in BLE Record 119.
- **named_procedure**: Use integrated WebAuthn.io with required UV, cross-platform attachment and the security-key hint; keep authentication dialogs under user control; inspect ADB GATT/CTAP lifecycle.
- **environment**: Windows 11; Xiaomi Mi A2 / Android 10/API29; synthetic account `codex-synthetic-064-20261103`.
- **expected_observed_result**: Windows showed “La conversación con la clave de seguridad de Bluetooth está en curso.” Mi admitted GetInfo/GetAssertion and MakeCredential. The user closed the Windows dialog because it prevented access to Codex; the BLE link disconnected, the in-flight CTAP command was canceled, and WebAuthn.io reported timeout/not-allowed. No credential success was observed.
- **date**: 2026-11-05
- **limitations**: The Mi pattern prompt was not observed because the user was away from the device. This does not identify a product defect or confirm credential storage; repeat physical acceptance only when the user can complete the Windows and Mi prompts. Parent T041 and SC-001–006 remain open.
- **status**: `Passed (BLE host connection and MakeCredential admission) / Failed (ceremony ended without WebAuthn success after host dialog closure) / Unverified (user verification and complete parent acceptance)`.
- **artifact_link**: [BLE component test record](../064-fido-ble-transport/implementation-evidence.md#record-127-miapi29-required-uv-webauthn-attempt-ended-by-host-disconnect-t023--partial), [BLE transport lifecycle](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleGattTransport.kt), [parent integration plan](integration.md)

### Record 108: BLE Keepalive and Response-Phase Follow-up (T041 — Partial)
- **requirement**: Preserve the parent-selected BLE path while it processes a real host request; component `FR-015`, canonical `FR-BLE-060` and `TR-BLE-020`.
- **scope**: Review the Mi timeout trace against the child protocol contract; correct periodic BLE processing/user-presence keepalive and reset response suppression after terminal delivery for subsequent requests.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` plus current staged and unstaged changes; correction is not deployed.
- **named_procedure**: Trace the accepted CTAP request through the raw-operation callback into `BleGattTransport`; run the focused Android host-test class with virtual time. No Mi device interaction was performed for this correction.
- **environment**: Windows 11 worktree; no device test performed for this correction.
- **expected_observed_result**: A periodic 250ms keepalive loop now defaults to PROCESSING, switches to UP_NEEDED when the handler reports user presence, and is canceled/joined before terminal response queuing. After terminal-notification acknowledgment, keepalives resume for the next request only when the same session is still current. The focused `FidoBleAssertionTest` class passed 6/6 with zero failures, errors or skips.
- **date**: 2026-11-05
- **limitations**: The prior Mi run ended after the user closed the native Windows dialog. The keepalive omission and response-phase leak are confirmed source gaps but are not proven to be that run's cause. Physical delivery, Windows acceptance and user verification remain unverified; parent T041 remains open.
- **status**: `Passed (focused Android host test) / Implemented (offline source correction) / Unverified (physical ceremony)`.
- **artifact_link**: [BLE implementation follow-up](../064-fido-ble-transport/implementation-evidence.md#record-128-periodic-fido-ble-keepalive-source-correction-t019--implementation-follow-up), [transport source](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleGattTransport.kt), [regression test](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/FidoBleAssertionTest.kt)

### Record 109: Transport Settings ViewModel Host Regression (Selection T027 — Partial)
- **requirement**: Preserve saved-versus-active selection, pending restart state, retry/error handling and symmetric BLE/Classic choice behavior; `FR-BT-SEL-020`, `FR-BT-SEL-030`, `FR-BT-SEL-060`.
- **scope**: Run the parent transport-selection ViewModel host suite after the current implementation changes, without using an Android device or Windows authentication dialog.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; current source includes staged and unstaged changes.
- **named_procedure**: Run `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.presentation.viewmodel.TransportSettingsViewModelTest --no-configuration-cache --no-problems-report` with a worktree-local Gradle user home.
- **environment**: Windows 11; Gradle 9.6.0; Android host-test JVM; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`.
- **expected_observed_result**: All six tests passed with zero failures, errors or skips in 19 seconds. The task compiled current Android main sources and exercised only the ViewModel host tests.
- **date**: 2026-11-05
- **limitations**: This does not run the settings screen instrumentation suite, select Classic on-device, restart the process or execute Windows ceremonies. Parent T027, T031, T035 and T041 remain open.
- **status**: `Passed (six transport-selection ViewModel host tests) / Unverified (settings instrumentation and physical switch/ceremony acceptance)`.
- **artifact_link**: [JUnit XML report](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.presentation.viewmodel.TransportSettingsViewModelTest.xml), [task T027](tasks.md)

### Record 110: BLE Notification Queue Overflow Cleanup (Selection T041 — Partial)
- **requirement**: Keep the selected BLE session bounded and make notification queue pressure observable through deterministic session cleanup; component `FR-BLE-050`, `TR-BLE-020`, `TR-BLE-040`.
- **scope**: Correct BLE notification-writer overflow handling and verify the component's focused queue and assertion regressions.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` plus current staged and unstaged changes.
- **named_procedure**: Run the focused `BleNotificationWriterTest` and `FidoBleAssertionTest` Android host-test classes and Android main/host-test KtLint checks.
- **environment**: Windows 11; Gradle 9.6.0; worktree-local Gradle user home; Android host-test JVM. Mi was not operated.
- **expected_observed_result**: The total outstanding byte limit includes in-flight plus queued notifications. Queue overflow rejects the excess packet, clears writer ownership and invokes session-stall cleanup. Clearing an unfinished send cancels its coroutine, which erases the borrowed buffer in `finally`. Both focused suites passed (12/12 and 6/6), and affected KtLint checks passed.
- **date**: 2026-11-05
- **limitations**: This verifies deterministic writer behavior only. Android GATT and remote-host recovery under real queue pressure, Windows ceremonies and SC-001–006 remain unverified; parent T041 remains open.
- **status**: `Passed (18 focused host tests and affected source-set KtLint) / Implemented (total-byte bound and queue overflow cleanup) / Unverified (physical acceptance)`.
- **artifact_link**: [BLE evidence](../064-fido-ble-transport/implementation-evidence.md#record-129-ble-outbound-queue-overflow-cleanup-t013t031--source-follow-up), [notification writer](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleNotificationWriter.kt), [task T041](tasks.md)

### Record 111: Legacy Android GATT Notification Buffer Ownership (Selection T041 — Partial)
- **requirement**: Preserve BLE response bytes through Android notification delivery and release them only after callback/teardown; component `FR-BLE-050`, `TR-BLE-020`, `TR-BLE-040`.
- **scope**: Keep a private legacy characteristic-value copy on API 28–32 until the Android notification callback or server close; use the API 33 explicit-value overload where available.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` plus current staged and unstaged changes.
- **named_procedure**: Compile Android main through the focused host test command; run the writer borrow-lifetime and assertion suites plus Android main/host-test KtLint.
- **environment**: Windows 11; Gradle 9.6.0; API28+ compile configuration; Android host-test JVM. No device was operated.
- **expected_observed_result**: The legacy path copies the value into sender-owned characteristic storage and wipes it on notification callback or GATT server close; writer-owned bytes are erased in a `finally` after sender initiation returns. Focused host suites passed 12/12 and 6/6; Android main compilation and affected KtLint checks passed.
- **date**: 2026-11-05
- **limitations**: No Android API29 notification or Windows ceremony was performed. Device callback timing and physical response integrity remain unverified; parent T041 remains open.
- **status**: `Passed (focused host suites, Android main compilation, affected KtLint) / Implemented (legacy buffer lifecycle) / Unverified (physical notification delivery)`.
- **artifact_link**: [BLE evidence](../064-fido-ble-transport/implementation-evidence.md#record-130-android-notification-buffer-ownership-on-api-28-32-t013t031--source-follow-up), [Android GATT host](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/AndroidFidoBleGattServerHost.kt)

### Record 112: Explicit BLE Framing Error Recovery (Selection T041 — Partial)
- **requirement**: Preserve the child's framing error contract through the selected BLE transport and keep invalid traffic out of credential handling; `FR-BT-SEL-010`, `FR-BT-SEL-040`, component `FR-BLE-050`.
- **scope**: Route malformed frame and receive-timeout outcomes from the BLE session owner to session-fenced `CMD_ERROR` notifications.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` plus staged and unstaged corrections.
- **named_procedure**: Run focused BLE session-owner, recovery and cancellation host tests, then the full `:feature:fido2:testAndroidHostTest` suite and Android main/host-test KtLint checks.
- **environment**: Windows 11; Gradle 9.6.0; Android host-test JVM. Mi was not operated.
- **expected_observed_result**: Short and overlong framing, invalid continuation sequence, unexpected continuation and timed-out partial input now produce typed error outcomes; current-session errors generate the specified BLE `CMD_ERROR` response. Timeout expires at exactly 3000ms; disabling CCCD clears partial input. `BleSessionOwner` now delegates packet parsing to the bounded reusable assembler. After this refactor, the full FIDO2 Android host suite passed 1,146/1,146 with zero failures, errors or skips; Detekt and affected source-set KtLint passed.
- **date**: 2026-11-05
- **limitations**: No physical BLE peer or WebAuthn ceremony was tested. Parent T035 and full T041 remain open; BLE T023 remains open.
- **status**: `Passed (full FIDO2 host suite 1,146/1,146, Detekt and affected source-set KtLint) / Implemented (shared parser, framing error routing, exact receive deadline and CCCD cleanup) / Unverified (physical acceptance)`.
- **artifact_link**: [BLE evidence](../064-fido-ble-transport/implementation-evidence.md#record-131-explicit-ble-framing-errors-and-recovery-t030t032--source-follow-up), [full host-suite report](../../feature/fido2/build/reports/tests/testAndroidHostTest/index.html), [transport selection integration](integration.md)

### Record 113: FIDO BLE Error Response Encoder (Selection T041 — Partial)
- **requirement**: Keep parent-selected BLE error output conformant to the FIDO BLE response envelope; `FR-BT-SEL-010`, component `FR-BLE-050`.
- **scope**: Correct and test the reusable error encoder's response status, declared length and payload bytes.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` plus staged and unstaged corrections.
- **named_procedure**: Run `FidoBleFramingTest` and Android main/host-test KtLint checks.
- **environment**: Windows 11; Gradle 9.6.0; Android host-test JVM. No device was operated.
- **expected_observed_result**: Error response encoder produces `[0xBF, 0x00, 0x01, errorCode]`; the focused byte-vector test and affected KtLint checks passed.
- **date**: 2026-11-05
- **limitations**: Runtime parent-selected Windows interoperability remains unverified; parent T041 remains open.
- **status**: `Passed (focused framing vector and affected KtLint) / Implemented (error encoder correction) / Unverified (physical acceptance)`.
- **artifact_link**: [BLE evidence](../064-fido-ble-transport/implementation-evidence.md#record-132-fido-ble-error-response-frame-vector-t007t032--source-follow-up), [integration plan](integration.md)

### Record 114: Truthful BLE Capacity Reporting (Selection T041 — Partial)
- **requirement**: Keep selected BLE capacity declarations within the negotiated ATT and platform limits; `FR-BT-SEL-010`, component `FR-BLE-020`.
- **scope**: Ensure the parent-selected BLE implementation reports actual write/notification capacity and rejects invalid core/platform limits.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` plus staged and unstaged corrections.
- **named_procedure**: Run `BleSessionOwnerTest`, `FidoBleFramingTest`, the complete FIDO2 Android host suite and affected KtLint checks.
- **environment**: Windows 11; Gradle 9.6.0; Android host-test JVM. No device was operated.
- **expected_observed_result**: Session owner no longer floors platform-limited values above platform support; invalid write max below 20 and invalid `maxMsgSize` are rejected. The full suite passed 1,146/1,146 with zero failures, errors or skips; affected KtLint passed.
- **date**: 2026-11-05
- **limitations**: No physical platform-limit report or Windows transport acceptance was observed. Parent T041 remains open.
- **status**: `Passed (full FIDO2 host suite 1,146/1,146 and affected KtLint) / Implemented (capacity alignment) / Unverified (physical acceptance)`.
- **artifact_link**: [BLE evidence](../064-fido-ble-transport/implementation-evidence.md#record-133-truthful-ble-write-and-notification-capacities-t011t017--source-follow-up), [integration plan](integration.md)

### Record 115: Full Local CI After Parent/BLE Integration (Selection T039/T041 — Partial)
- **requirement**: Validate local static analysis, release lint, compilation, unit tests and configured host tests for the current parent-gated transport integration; coverage and hosted acceptance remain separately assessed.
- **scope**: Run ordinary local CI with no automatic fixes or configuration cache and without opt-in coverage.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` plus staged/unstaged changes.
- **named_procedure**: `.\build\local-ci-no-problems.ps1 -NoFix -NoConfigurationCache`.
- **environment**: Windows 11; Gradle 9.6.0; Android SDK; local checkout.
- **expected_observed_result**: KtLint, Detekt, release lint, comprehensive compilation, unit tests, and configured KMP Android host tests passed in 90.44 seconds. Coverage was not run; no physical host/device tests were part of this command.
- **date**: 2026-11-05
- **limitations**: Trusted candidate-versus-accepted-target coverage and the physical BLE/Classic switch matrix remain unverified. Parent T039/T041 and BLE T038 remain open.
- **status**: `Passed (ordinary local CI) / Deferred (coverage by owner direction) / Unverified (trusted comparison and physical acceptance)`.
- **artifact_link**: [BLE verification record](../064-fido-ble-transport/implementation-evidence.md#record-134-full-local-ci-after-ble-contract-corrections-t036t038--local-verification), [integration plan](integration.md)

### Record 116: Prescribed Local CI Run (Selection T039 — Partial)
- **requirement**: Run the repository's prescribed non-mutating static analysis, compilation and host-test pipeline; assess coverage and raw-count nonregression separately with trusted accepted-target evidence.
- **scope**: Execute `tools/local-ci.ps1` with `-NoFix -NoConfigurationCache` and omit `-RunCoverage` per the owner's existing coverage deferral.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` plus staged/unstaged changes.
- **named_procedure**: `.\tools\local-ci.ps1 -NoFix -NoConfigurationCache`.
- **environment**: Windows 11; Gradle 9.6.0; `.tmp/gradle-home` with problems-report generation disabled locally to avoid the restricted report path.
- **expected_observed_result**: The prescribed script completed successfully in 213.47 seconds: KtLint, Detekt, release lint, comprehensive main/unit/host/device-test compilation, unit tests and configured Android host tests passed. The connected-device coverage gate was not invoked.
- **date**: 2026-11-05
- **limitations**: No connected device suite, coverage floor/completeness run or trusted candidate-versus-accepted-target raw-count comparison was performed. Those prerequisites remain deferred/unverified; T039 remains unchecked, as do physical acceptance tasks.
- **status**: `Passed (prescribed local CI) / Deferred (coverage under owner direction) / Unverified (trusted comparison and physical acceptance)`.
- **artifact_link**: [local CI script](../../tools/local-ci.ps1), [coverage instructions](../../specs/058-enforce-coverage-targets/plan.md), [integration plan](integration.md)

### Record 117: ASUS BLE Registration and Authentication (Partial T041)
- **requirement**: `FR-BT-SEL-010` through `FR-BT-SEL-060`; `SC-001`–`SC-006`; parent T041; BLE T023.
- **scope**: Validate one synthetic WebAuthn registration followed by authentication while the ASUS device is using the selected BLE transport; capture the browser-visible outcome and Android diagnostic timing.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`.
- **named_procedure**: WebAuthn.io Cross-Platform / Security Key ceremony for synthetic account `codex-synthetic-064-20261103`; select Windows `Cambiar → Llave de seguridad` and complete both ceremonies on the ASUS authenticator.
- **environment**: ASUS `ASUS_AI2302`, Android 15 / API 35; Windows build `10.0.26300.9818`; ChatGPT integrated browser; WebAuthn.io. Exact browser engine version and Windows product label were not captured.
- **expected_observed_result**: Android showed Advertising before the ceremony. Registration succeeded and WebAuthn.io offered authentication; authentication succeeded and WebAuthn.io displayed “You're logged in!”. The credential listed `ble` and `usb` transports. Android FIDO BLE logs recorded MakeCredential completion at 7,171 ms and successful GetAssertion exchanges; details and observed GetAssertion diagnostic durations are in [BLE Record 136](../064-fido-ble-transport/implementation-evidence.md#record-136-asus-windows-webauthn-registration-and-assertion-t023).
- **date**: 2026-11-06
- **limitations**: This is BLE-only evidence for one ASUS and one Windows/browser setup. It does not cover switching BLE-to-Classic or Classic-to-BLE, Classic HID registration/assertion, before-and-after credential/event comparisons, process/reboot recovery, or other SC-001–006 rows. Therefore T041 and parent T027/T031/T035 remain unchecked. Coverage and trusted target comparison remain deferred/unverified under the owner's direction; T039 remains unchecked.
- **status**: `Passed (BLE registration and authentication on ASUS) / Partial (T041) / Unverified (full acceptance matrix, Classic HID, recovery and coverage)`.
- **artifact_link**: [BLE physical evidence Record 136](../064-fido-ble-transport/implementation-evidence.md#record-136-asus-windows-webauthn-registration-and-assertion-t023), [parent integration plan](integration.md), [acceptance matrix](quickstart.md)

### Record 118: ASUS Classic HID WebAuthn Key Rejection (T027 — Partial)
- **requirement**: Parent T027; BLE-to-Classic process restart, physical registration/assertion procedure and preservation of credentials across the switch.
- **scope**: Switch the ASUS from the previously tested BLE mode to Classic HID, restart the app process, start Classic, and attempt WebAuthn authentication with the existing synthetic BLE-created credential.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`.
- **named_procedure**: Quickstart Physical Validation steps 2–3; WebAuthn.io Cross-Platform authentication with the Security Key hint; select Windows `Cambiar → Llave de seguridad`.
- **environment**: ASUS_AI2302, Android 15/API 35; Windows build `10.0.26300.9818`; ChatGPT integrated browser; WebAuthn.io; synthetic account `codex-synthetic-064-20261103`.
- **expected_observed_result**: Saving Bluetooth HID (Classic) while BLE was active showed the pending-restart state and kept BLE active. After force-stop/relaunch, the app showed `Ready to Start` and `FIDO2 / WebAuthn over Bluetooth HID`; it did not start automatically. Explicit Start opened Android's 120-second visibility consent; Allow led to app `Advertising...`. After the user selected `Cambiar → Llave de seguridad`, Windows showed`No reconocemos esta llave de seguridad. Prueba con otra distinta.` WebAuthn.io then showed its generic timeout/not-allowed error. The ASUS remained in `Advertising...`; no HID connection or CTAP GetAssertion event appeared in the inspected Android log window. Read-only Windows PnP enumeration showed the paired `Zenfone 10` device but no HID Device service instance (`00001124`) for it.
- **date**: 2026-11-07
- **limitations**: This is a failed Classic ceremony, not a pass. It does not distinguish stale pairing/service enumeration from a HID descriptor, host-provider or other interop issue. No Windows unpair/re-pair was performed. T027 remains open; the Classic-to-BLE reverse ceremony, broader lifecycle matrix and full T041 remain open.
- **status**: `Passed (saved/active separation, process restart and explicit Classic startup) / Failed (Windows rejected the selected security key; no Classic ceremony completed) / Unverified (Windows HID discovery/pairing cause and report exchange)`.
- **artifact_link**: [Classic transport implementation](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/bluetooth/BluetoothHidDeviceWrapper.kt), [physical procedure](quickstart.md), [integration handoff](integration.md)

### Record 119: ASUS Classic HID Pair Reset and Rediscovery Handoff (T027 — Partial)
- **requirement**: Parent T027; establish the host/device Classic HID pairing needed before registration and assertion.
- **scope**: Remove only the ASUS–Windows Bluetooth pairing at both ends and re-enable Classic discoverability before repeating Windows host pairing.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`.
- **named_procedure**: User removed Windows `Zenfone 10` and ASUS `LOQ15IRX10-3` pairings. Restart Chimali MainActivity, stop/start Classic authenticator, and accept the Android 120-second visibility prompt.
- **environment**: ASUS_AI2302, Android 15/API 35; Windows build `10.0.26300.9818`.
- **expected_observed_result**: The user confirmed both old pairings were removed. Read-only Android bonded-device state no longer included Windows host `20:BD:1D:1D:93:E4`; Windows PnP no longer listed `Zenfone 10`. Classic mode remained selected. After explicit stop/start and renewed 120-second system consent, ASUS returned to `Advertising...`. At the last inspection Windows had not yet added a new Zenfone pair or HID service instance; no second WebAuthn attempt was made.
- **date**: 2026-11-07
- **limitations**: This records prerequisite cleanup and restored discoverability only. New Windows pairing, HID service enumeration and the Classic ceremony remain pending; parent T027 is still open.
- **status**: `Passed (old pairing removed and Classic discoverability renewed) / Pending (Windows HID re-pair and ceremony)`.
- **artifact_link**: [Classic rejection Record 118](#record-118-asus-classic-hid-webauthn-key-rejection-t027--partial), [physical procedure](quickstart.md), [integration handoff](integration.md)

### Record 120: ASUS Classic HID Authentication After Re-pair (T027 — Partial)
- **requirement**: Parent T027; complete the Classic assertion procedure after BLE-to-Classic selection, process restart and Windows re-pair.
- **scope**: Re-pair ASUS with Windows, start the selected Classic HID authenticator, and authenticate the existing synthetic credential previously registered over BLE.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`.
- **named_procedure**: User paired `Zenfone 10` in Windows Bluetooth Settings after forgetting both old bonds; start WebAuthn.io authentication for `codex-synthetic-064-20261103`, select Windows `Cambiar → Llave de seguridad`, and complete the ASUS passkey prompt.
- **environment**: ASUS_AI2302, Android 15/API 35; Windows 11 build `10.0.26300.9818`; ChatGPT integrated browser; WebAuthn.io.
- **expected_observed_result**: User confirmed Windows and ASUS showed the pair as paired and connected, although Windows did not show a `Ready` status. ASUS displayed `Connected to PC` with `FIDO2 / WebAuthn over Bluetooth HID` selected. The user completed the ASUS `Sign in with Passkey` prompt; WebAuthn.io displayed “You're logged in!” for the synthetic account, and the credential listed `ble` and `usb` transports. Android logcat recorded `FIDO2 Operation succeeded for host: LOQ15IRX10-3 (20:BD:1D:1D:93:E4)` and `GetAssertion = 241ms (Total: 22361ms, User: 22120ms, HID reports accepted: true)`.
- **date**: 2026-11-07
- **limitations**: This establishes one successful Classic HID assertion after re-pairing; Windows' lack of a Ready status and prior PnP HID-service visibility remain unresolved observations. Classic registration after the switch, reverse Classic-to-BLE ceremonies, credential/event comparisons, the broader lifecycle matrix and other SC-001–006 rows remain unverified. Parent T027/T031/T035/T041 remain open; coverage and trusted target comparison remain deferred/unverified under owner direction.
- **status**: `Passed (Classic HID authentication after re-pair on ASUS/Windows) / Partial (T027) / Unverified (Classic registration, reverse switch, lifecycle and full acceptance matrix)`.
- **artifact_link**: [Classic rejection Record 118](#record-118-asus-classic-hid-webauthn-key-rejection-t027--partial), [pair reset Record 119](#record-119-asus-classic-hid-pair-reset-and-rediscovery-handoff-t027--partial), [physical procedure](quickstart.md), [integration handoff](integration.md)

### Record 121: ASUS Classic HID Registration and Failed New-Credential Sign-in (T027 — Partial)
- **requirement**: Parent T027; register and authenticate using Classic HID after the BLE-to-Classic process restart and Windows re-pair.
- **scope**: Register a synthetic cross-platform credential through Classic HID on ASUS/Windows, then attempt required-UV authentication with the newly registered credential.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`.
- **named_procedure**: WebAuthn.io `codex-synthetic-064-20261103`; Registration and Authentication both configured for Cross-Platform, Security Key hint and required user verification; user selected Windows `Cambiar → Llave de seguridad` and completed the ASUS registration prompt.
- **environment**: ASUS_AI2302, Android 15/API 35; Windows 11 build `10.0.26300.9818`; ChatGPT integrated browser; WebAuthn.io; Windows host `LOQ15IRX10-3` (`20:BD:1D:1D:93:E4`).
- **expected_observed_result**: WebAuthn.io displayed “Success! Now try to authenticate...” after registration. Android logged successful `MakeCredential` response encoding and `FIDO2 Operation succeeded` for the Windows host; diagnostic handoff was 415 ms (8,330 ms total, 7,915 ms user wait), with HID reports accepted. During the subsequent Authenticate attempt, the user reported Windows “No se pudo iniciar sesión. Vuelve a intentarlo y asegúrate de completar la acción de seguimiento.” Android logcat showed an initial `GetAssertion` lookup with no matching credential, then a request with one candidate, followed by `Assertion failed during GetAssertion`; no browser sign-in success was observed.
- **date**: 2026-11-07
- **limitations**: Classic registration passed, but authentication with the newly registered credential did not. The available evidence does not establish whether the assertion failure resulted from credential selection, local verification, CTAP response handling or another host/authenticator interaction. A separate Classic assertion using the BLE-created credential succeeded after re-pairing (Record 120), but does not convert this failed follow-up into a pass. Parent T027 remains partial/open; T031/T035/T041, reverse-switch and lifecycle acceptance remain unverified. Coverage and trusted target comparison remain deferred under owner direction.
- **status**: `Passed (Classic MakeCredential registration) / Failed (follow-up authentication with the newly registered credential) / Partial (T027; assertion with a prior BLE credential passed in Record 120) / Unverified (failure cause and remaining acceptance matrix)`.
- **artifact_link**: [Classic assertion after re-pair Record 120](#record-120-asus-classic-hid-authentication-after-re-pair-t027--partial), [physical procedure](quickstart.md), [integration handoff](integration.md)

### Record 122: ASUS Classic-to-BLE Restart and Credential Lookup Failure (T031 — Partial)
- **requirement**: Parent T031; switch from Classic HID to FIDO BLE after process restart, then use a real Windows ceremony while preserving saved credentials.
- **scope**: Save BLE while Classic was active, verify pending-versus-active status, force-stop and relaunch Chimali, explicitly start BLE, then attempt WebAuthn authentication for the synthetic account.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`.
- **named_procedure**: In Settings select `FIDO Bluetooth LE (default)` while Classic reports active; verify restart-required state; force-stop `com.chimali.shrunk`, start MainActivity, explicitly tap Start Authenticator, then attempt WebAuthn.io Cross-Platform Authenticate with the Security Key hint and required user verification.
- **environment**: ASUS_AI2302, Android 15/API 35; Windows 11 build `10.0.26300.9818`; host `LOQ15IRX10-3` (`20:BD:1D:1D:93:E4`); ChatGPT integrated browser; WebAuthn.io; package `com.chimali.shrunk`.
- **expected_observed_result**: Settings showed Classic active and BLE saved pending; after force-stop/relaunch the home screen showed `Ready to Start` and `FIDO2 / WebAuthn over FIDO Bluetooth LE`, with no auto-start. Explicit Start changed the app to `Ready for Authentication` / `Waiting for request from PC`. During authentication Android accepted a BLE GATT connection from the Windows host and logged two `GetAssertion` requests with `allowCredentials=1`, both returning`NO_CREDENTIALS`. The user reported Windows`No reconocemos esta clave de seguridad. Prueba con otra distinta.` The passkey manager continued to list `webauthn.io` / `codex-synthetic-064-20261103` as a saved credential last used 2026-11-07.
- **date**: 2026-11-07
- **limitations**: This verifies the saved-mode process restart and explicit BLE startup, but the reverse-switch ceremony failed at credential lookup. The initial CTAP representation trace did not reveal a byte-conversion defect, but it missed the destructive projection policy. A subsequent source audit found that saving a new credential for the same RP/user deleted all older rows; see Record 123 for the RCA and local correction. The exact host-request ID was not captured or compared with the saved row, so the observed request is not yet proven to reference a particular deleted credential. No credential IDs or sensitive data were logged. Parent T031/T041 remain open, and the complete switch/credential/event matrix is not satisfied.
- **status**: `Passed (Classic-to-BLE save/restart/explicit start; BLE host connection) / Failed (WebAuthn assertion returned NO_CREDENTIALS) / Unverified (credential-ID mismatch cause and full acceptance matrix)`.
- **artifact_link**: [Classic round-trip attempt Record 121](#record-121-asus-classic-hid-registration-and-failed-new-credential-sign-in-t027--partial), [physical procedure](quickstart.md), [integration handoff](integration.md)

### Record 123: Credential Projection Replaced Same-Account Passkeys (Source RCA and Correction)
- **requirement**: Parent T031 and BLE T023; preserve usable credential IDs when registering the same RP/account across BLE and Classic HID.
- **scope**: Trace the failed Classic-to-BLE GetAssertion path through CTAP credential matching and the SQLDelight credential projection; remove destructive same-RP/user replacement and preserve existing rows in an in-place schema migration.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`, with local implementation changes not committed.
- **named_procedure**: Trace the DAO/schema path, implement non-destructive credential persistence and migration 2, run the focused migration and DAO host tests, build `:app:assembleShrunkDebug`, and install the build on ASUS and Mi.
- **environment**: Windows 11; local worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; device observations are from ASUS Android 15/API35 and Windows 11 on 2026-11-07.
- **expected_observed_result**: Source had a `UNIQUE(rp_id, user_id)` constraint and a save operation that deleted every matching RP/user projection before inserting the new credential. The correction removes this uniqueness constraint, inserts projections without deleting prior credentials, and adds SQLDelight migration 2 to copy existing rows and recreate all four credential indexes. The focused migration and DAO host tests passed 1/1 each; `:app:assembleShrunkDebug` succeeded; and the updated APK installed and launched on ASUS, and installed on Mi for the owner's later test. ASUS then displayed the Android Nearby devices permission prompt; WebAuthn.io reported that its page had lost focus, so no Windows prompt appeared.
- **date**: 2026-11-07
- **limitations**: The exact allow-list identifier was not captured. The ASUS installed build had an obsolete debug signing certificate; under prior user authorization, only `com.chimali.shrunk` was uninstalled and its local synthetic data was erased before installing the new APK. Therefore ASUS started from a fresh database and did not execute migration 2 against legacy rows; the synthetic host test did execute the generated migration from schema version 1 and confirmed row/index preservation. The new WebAuthn attempt was blocked by page focus before Windows or Android handled a ceremony. The full FIDO2 Android host suite passed 1,148/1,148 with zero failures, errors or skips; BLE/Classic cross-transport assertion remains unverified. Do not mark transport acceptance tasks complete.
- **status**: `Confirmed (same RP/user rows were deleted during registration projection) / Implemented (schema, persistence and migration; focused/full host tests, app build and deployment succeeded) / Unverified (device migration with prior data, Windows BLE ceremony and acceptance matrix)`.
- **artifact_link**: [DAO implementation](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/dao/PasskeyCredentialDao.kt), [schema migration](../../feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/Fido2Database/2.sqm), [parent integration handoff](integration.md)

### Record 124: Mi Migration Failure and View-Safe Correction (T031/T035 — Partial)
- **requirement**: Parent T031/T035; preserve database state across the multi-credential schema migration and leave the app launchable after update.
- **scope**: Investigate Mi's first-update startup crash, rebuild the dependent SQL view safely, verify the legacy schema through a synthetic SQLite migration test, and retry the app update without clearing Mi data.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`, local changes uncommitted.
- **named_procedure**: Install the initial candidate on Mi in place, inspect the crash (`credential_summary` referenced a missing `passkey_credential` table), update migration 2 to drop/recreate that view, run the focused legacy-view migration test, rebuild and update Mi in place, then launch MainActivity and inspect the passkey manager.
- **environment**: Mi A2 / Android API 29; Windows 11; local worktree `.worktrees/chimali/ofrenda`.
- **expected_observed_result**: Initial Mi launch crashed with SQLite`no such table: main.passkey_credential` while validating `credential_summary`. After correction, the host migration test passed 1/1 with a legacy view and credential. The updated shrunkDebug APK installed in place; MainActivity stayed open at `Ready to Start`, with no SQLite error. Manage Saved Passkeys displayed`No passkeys yet`.
- **date**: 2026-11-07
- **limitations**: Mi contained no saved passkey rows, so the physical database did not independently prove credential retention. The synthetic host migration test verified data/view/index preservation. No WebAuthn ceremony, advertising start, process recovery or lifecycle cycle was performed in this update. Parent T031/T035/T041 and BLE T035 remain open.
- **status**: `Failure reproduced (first migration invalidated dependent view) / Corrected (migration recreates view) / Passed (focused host migration test and in-place Mi launch) / Unverified (device credential rows and transport ceremony)`.
- **artifact_link**: [migration 2](../../feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/Fido2Database/2.sqm), [migration regression test](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/database/Fido2DatabaseCredentialMigrationTest.kt), [integration handoff](integration.md)

### Record 125: ASUS BLE Security-Key Chooser Rejection After Classic Pairing (T031 — Partial)
- **requirement**: Parent T031/T041; after Classic-to-BLE selection and process restart, complete an assertion with the BLE GATT transport.
- **scope**: Capture the user's latest WebAuthn.io BLE attempt after ASUS was re-paired to Windows and the Windows security-key chooser was completed.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; local changes uncommitted.
- **named_procedure**: Start a BLE WebAuthn security-key ceremony, choose the security-key option in Windows and complete the ASUS prompt.
- **environment**: ASUS Android 15/API35 and Windows 11; user-reported observation on 2026-11-07.
- **expected_observed_result**: Windows displayed “No reconocemos esta clave de seguridad. Prueba con otra distinta.” The BLE assertion did not succeed. No Android request/log evidence or exact credential allow-list ID was supplied for this attempt.
- **date**: 2026-11-07
- **limitations**: This is a host-reported failure without captured Android CTAP logs, so the failure stage and cause are unknown. It does not establish whether the latest multi-credential migration build was installed on ASUS. The storage correction is not yet confirmed by a successful BLE assertion. Parent T031/T041 and BLE physical recovery checks remain open; coverage stays owner-deferred.
- **status**: `Failed (Windows rejected the BLE security-key attempt) / Unverified (Android request stage, credential match and cause)`.
- **artifact_link**: [parent integration handoff](integration.md), [credential-retention RCA](#record-123-credential-projection-replaced-same-account-passkeys-source-rca-and-correction)

### Record 126: ASUS BLE Attempt Prerequisites Were Not Met (T031 — Unverified Attempt)
- **requirement**: Parent T031/T041; run a real Windows ceremony after selecting BLE and starting the authenticator.
- **scope**: Inspect ASUS after the user reported Windows' security-key rejection; verify Android Bluetooth permission state, active screen and installed build identity without changing device state.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; latest local APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Read `dumpsys package`, app-ops, UI hierarchy, package APK hashes and recent logcat from both attached devices.
- **environment**: ASUS_AI2302/API35 (`R7AIB7002901AZ3`), Mi A2/API29 (`2ac6876`), Windows 11, 2026-11-07.
- **expected_observed_result**: ASUS displayed Android's system permission dialog requesting Nearby Devices access. `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` were all denied (`appops: ignore`). The installed ASUS APK hash was `33c2c25839bcc640e8dca16ebc52d5d405dd4ce80ac4708696d5e49374ddba8e`, which differs from the current locally built APK; Mi's installed APK hash matched the current build exactly. No matching FIDO CTAP log was found in the inspected ASUS buffer.
- **date**: 2026-11-07
- **limitations**: This establishes that ASUS did not have the current build and was awaiting permission, but does not establish exactly which conditions applied at the instant of the user's reported Windows chooser. The reported Windows rejection is therefore not a valid verification of the latest credential-retention correction. The permission dialog was left untouched and ASUS was not updated. Parent T031/T041 and BLE physical checks remain open.
- **status**: `Observed (ASUS permission prompt open, Bluetooth permissions denied, installed APK differs from current build; Mi APK matches) / Unverified (reported Windows failure stage and cause; current-build ASUS ceremony)`.
- **artifact_link**: [parent integration handoff](integration.md), [BLE permission/build check Record 140](../064-fido-ble-transport/implementation-evidence.md#record-140-asus-permission-and-build-prerequisite-check-t023t035--partial)

### Record 127: Mi-to-Current-ASUS 50-Cycle GATT Reconnect Stress (T035 — Partial)
- **requirement**: Parent T035; validate repeated BLE connection recovery and retain before/after memory observations as part of the lifecycle matrix.
- **scope**: Run the opt-in Mi/API29 scanner test against ASUS/API35 running the current candidate; verify 50 remote FIDO service discovery, connect, disconnect and close cycles while ASUS continues advertising.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; current shrunkDebug APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Build `:feature:fido2:assembleAndroidDeviceTest`; install only `feature/fido2/build/outputs/apk/androidTest/fido2-androidTest.apk` on Mi; run `FidoBleReconnectStressDeviceTest` with `runFidoBleReconnectStress=true` while the current ASUS production candidate is advertising.
- **environment**: Mi A2/API29 scanner (`2ac6876`), ASUS_AI2302/API35 advertiser (`R7AIB7002901AZ3`), Windows 11 workstation; 2026-11-07.
- **expected_observed_result**: Device instrumentation reported `OK (1 test)` in 85.827 seconds. Mi logcat recorded `cycles=50`, Java heap 2,763,928 bytes before and 4,206,360 after; native heap 6,132,216 bytes before and 7,325,304 after. ASUS remained on the current APK, showed `Advertising...`, had FIDO app_if 13 registered and its foreground transport service active after the test.
- **date**: 2026-11-07
- **limitations**: Heap values are immediate process-wide samples without forced collection; they neither prove nor disprove a leak. This tests unauthenticated GATT connection, FIDO service discovery, disconnect and close only. No Windows ceremony, CTAP transaction interruption, stale-bond recovery, permission revocation or cancellation was included. Parent T035/T041 remain open; coverage remains deferred.
- **status**: `Passed (50 Mi-to-current-ASUS GATT reconnect/service-discovery cycles) / Observed (before/after Java/native heap values and ASUS advertising persistence) / Unverified (memory leak acceptance and remaining lifecycle matrix)`.
- **artifact_link**: [captured output](../../feature/fido2/build/reports/fido2/064/asus-current-reconnect-stress-20261107.txt), [device stress test source](../../feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/data/transport/FidoBleReconnectStressDeviceTest.kt), [BLE detailed Record 141](../064-fido-ble-transport/implementation-evidence.md#record-141-current-asus-50-cycle-gatt-reconnect-stress-t035--partial)

### Record 128: Current Candidate Installed and Advertising on ASUS; Local Credential Store Empty (T031 — Partial)
- **requirement**: Parent T031/T041; the selected BLE transport must start after permission recovery and the candidate must retain synthetic credential state across an in-place update.
- **scope**: After the user granted Android Nearby Devices, install the current shrunkDebug APK in place on ASUS, finish the existing Passkey Authenticator setup, explicitly start BLE, and inspect the credential manager and runtime advertiser.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; local APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Verify permissions, use `adb install -r` with no uninstall or data-clear command, launch MainActivity, select Passkey Authenticator in onboarding, tap Start Authenticator, check Bluetooth advertiser registration/service, and open Manage Saved Passkeys.
- **environment**: ASUS_AI2302 / Android 15/API35 (`R7AIB7002901AZ3`); Mi A2/API29 (`2ac6876`); integrated WebAuthn.io browser; 2026-11-07.
- **expected_observed_result**: All three Nearby Devices permissions were granted by the user. `adb install -r` succeeded and the ASUS installed APK SHA-256 matched the local candidate. The app showed `Advertising...` / `Ready to pair with a new host PC`; Bluetooth manager showed Chimali app_if 13, GATT advertiser registration, and three total advertisements including two unrelated system advertisers; `Fido2TransportService` was foreground. Manage Saved Passkeys showed`No passkeys yet`.
- **date**: 2026-11-07
- **limitations**: No passkey was present to authenticate or to confirm retention of a pre-update credential; none was deleted by this update. ASUS had been reset earlier under prior authorization, so the device did not exercise migration 2 against legacy rows. A new synthetic registration and authentication remain required. Parent T031/T041 and BLE ceremony/recovery acceptance remain open; coverage stays owner-deferred.
- **status**: `Passed (in-place current-candidate install with permissions preserved, onboarding, explicit BLE start, GATT advertiser/service registration) / Observed (no saved ASUS credential) / Unverified (registration/authentication, existing-device credential retention and full acceptance)`.
- **artifact_link**: [BLE deployment evidence Record 142](../064-fido-ble-transport/implementation-evidence.md#record-142-current-candidate-asus-install-permission-recovery-and-advertising-t023t035--partial), [reconnect stress Record 127](#record-127-mi-to-current-asus-50-cycle-gatt-reconnect-stress-t035--partial)

### Record 129: Current ASUS BLE WebAuthn Registration Succeeded (T031 — Partial)
- **requirement**: Parent T031/T041; complete a real Windows ceremony after selecting and restarting into FIDO BLE, then verify registration and assertion behavior.
- **scope**: Complete synthetic WebAuthn registration over ASUS BLE using the current in-place shrunkDebug candidate and confirm both Android CTAP completion, local persistence and WebAuthn.io's registration result.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; ASUS candidate APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: On the current BLE-advertising ASUS, submit registration for the approved synthetic account `codex-synthetic-064-20261103` in the integrated WebAuthn.io tab, select the Bluetooth security-key option in Windows, complete ASUS user verification, then verify the browser result and saved-passkey list.
- **environment**: ASUS_AI2302 / Android 15/API35 (`R7AIB7002901AZ3`), Windows 11 build `10.0.26300.9818`, host `LOQ15IRX10-3`, ChatGPT integrated browser, WebAuthn.io; 2026-11-07.
- **expected_observed_result**: Android accepted a BLE GATT `MakeCredential` request, logged `MakeCredential request admitted`, and returned a 218-byte CBOR response for a 32-byte credential ID; the operation completed in 4,040ms with terminal notification acknowledged. WebAuthn.io displayed `Success! Now try to authenticate...`. Chimali's saved-passkey manager listed `webauthn.io` / `codex-synthetic-064-20261103`, last used 2026-11-07. BLE advertising remained active.
- **date**: 2026-11-07
- **limitations**: This is registration-only; authentication of the newly created credential has not yet been completed. A nearby earlier `GetAssertion` request returned`NO_CREDENTIALS` while the ASUS local passkey list was empty; it preceded the successful MakeCredential and does not establish a regression after registration. Exact host allow-list bytes were not logged. BLE default ceremony passed, but Classic-to-BLE switch/restart and the parent acceptance matrix remain incomplete; coverage remains deferred.
- **status**: `Passed (current-build BLE MakeCredential and WebAuthn.io registration; local synthetic credential persisted) / Unverified (assertion round trip, cross-transport switch and full parent acceptance)`.
- **artifact_link**: [captured output](../../feature/fido2/build/reports/fido2/064/asus-current-ble-registration-20261107.txt), [BLE detailed Record 143](../064-fido-ble-transport/implementation-evidence.md#record-143-current-asus-ble-webauthn-registration-t023--partial), [current candidate install Record 128](#record-128-current-candidate-installed-and-advertising-on-asus-local-credential-store-empty-t031--partial)

### Record 130: Current ASUS BLE Registration and Authentication Round Trip (T031 — Partial)
- **requirement**: Parent T031/T041; after selecting BLE, complete real registration and authentication while preserving the credential across a transport change.
- **scope**: Complete BLE registration and a follow-up GetAssertion on the current ASUS candidate for the synthetic account; verify app persistence, Android CTAP completion and WebAuthn.io browser sign-in.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Register `codex-synthetic-064-20261103` on ASUS FIDO BLE; confirm the new local saved credential; then authenticate on WebAuthn.io using the Windows Bluetooth security-key provider and ASUS user verification.
- **environment**: ASUS_AI2302 / Android 15/API35 (`R7AIB7002901AZ3`), Windows 11 build `10.0.26300.9818`, host `LOQ15IRX10-3`, ChatGPT integrated browser, WebAuthn.io; 2026-11-07.
- **expected_observed_result**: The preceding MakeCredential completed successfully and was stored on-device (parent Record 129/BLE Record 143). Two following `GetAssertion` requests with one allowed credential were accepted and returned assertions: the first completed in 221ms; the second completed after Android required user verification and took 16,505ms. Both terminal BLE responses were acknowledged. WebAuthn.io `/profile` displayed `You're logged in!` for the synthetic account and listed device-bound credentials with `ble` transports.
- **date**: 2026-11-07
- **limitations**: This validates a same-transport BLE registration/authentication round trip on the current candidate, not a BLE↔Classic switch. WebAuthn.io showed multiple credentials, so the page's transport list is not mapped byte-for-byte to the assertion allow-list; the exact credential ID was deliberately not copied. Parent T031/T041 remain open for switch/recovery and acceptance matrices; coverage remains deferred.
- **status**: `Passed (current-candidate BLE registration and user-verified assertion round trip; browser login success) / Unverified (cross-transport credential matching and full parent acceptance)`.
- **artifact_link**: [captured CTAP/browser result](../../feature/fido2/build/reports/fido2/064/asus-current-ble-authentication-20261107.txt), [BLE evidence Record 144](../064-fido-ble-transport/implementation-evidence.md#record-144-current-asus-ble-webauthn-authentication-t023--partial), [registration Record 129](#record-129-current-asus-ble-webauthn-registration-succeeded-t031--partial)

### Record 131: Current-Candidate BLE-to-Classic Restart and HID Activation (T027 — Partial)
- **requirement**: Parent T027; save Classic while BLE is active, apply it after a full process restart, then complete Classic registration and assertion.
- **scope**: With a current-candidate BLE credential already registered and authenticated, save Classic, force-stop/relaunch, explicitly Start, accept the previously authorized 120-second visibility prompt and verify Android HID profile registration.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; production APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: In Settings save Bluetooth HID (Classic), verify active BLE/pending Classic; force-stop and relaunch MainActivity; explicitly start Classic; accept Android's 120-second Bluetooth visibility request; inspect process state, GATT advertiser count, HID profile proxy and registration callbacks.
- **environment**: ASUS_AI2302 / Android 15/API35 (`R7AIB7002901AZ3`), Windows 11 build `10.0.26300.9818`, host `LOQ15IRX10-3`; 2026-11-07.
- **expected_observed_result**: Settings showed Classic saved pending while BLE remained active. After force-stop/relaunch, Classic was selected and `Ready to Start` with BLE advertiser absent. After explicit Start and user-authorized visibility, logs showed HID_DEVICE proxy acquisition, `registerApp()` returning true, `onAppStatusChanged registered=true`, and `BluetoothHidTransport connected and advertising`.
- **date**: 2026-11-07
- **limitations**: This proves mode persistence, restart gating and current-candidate Android HID registration only. The user is now attempting a Windows Classic assertion using the newly BLE-created credential; the ceremony result is pending and is not included in this record. Parent T027/T041 remain open.
- **status**: `Passed (current-candidate BLE-to-Classic save/restart/explicit start and Android HID registration) / In progress (Windows Classic assertion of BLE-created credential) / Unverified (full registration/assertion matrix and acceptance)`.
- **artifact_link**: [captured activation output](../../feature/fido2/build/reports/fido2/064/asus-current-ble-to-classic-activation-20261107.txt), [BLE registration/authentication Record 130](#record-130-current-asus-ble-registration-and-authentication-round-trip-t031--partial)

### Record 132: Current-Candidate BLE-Created Credential Authenticated over Classic (T027 — Partial)
- **requirement**: Parent T027; after switching from BLE to Classic and restarting, verify that a credential registered over BLE can authenticate over Classic HID.
- **scope**: Complete the BLE-to-Classic assertion on ASUS with the current candidate, using the synthetic WebAuthn account and the BLE-created credential.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; ASUS APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: After Record 131's Classic activation, select the Bluetooth security key in Windows, complete ASUS verification, and confirm WebAuthn.io login; repeat authentication once to confirm a second ceremony.
- **environment**: ASUS_AI2302 / Android 15/API35 (`R7AIB7002901AZ3`), Windows 11 build `10.0.26300.9818`, host `LOQ15IRX10-3`, ChatGPT integrated browser, WebAuthn.io, synthetic account `codex-synthetic-064-20261103`; 2026-11-07.
- **expected_observed_result**: User reported the first Classic biometric prompt was dismissed when the phone/screen rotated. Android logs show HID reports accepted and subsequent GetAssertion operations completed; Android's biometric service logged successful verification. WebAuthn.io `/profile` showed `You're logged in!` and listed device-bound credentials with `ble` and `usb` transports. The user then completed a second authentication and reported that it worked. No credential IDs or allow-list bytes were copied.
- **date**: 2026-11-07
- **limitations**: The initial rotation interrupted/dismissed a prompt and that attempt is not counted as a successful ceremony. The subsequent Classic sign-in is corroborated by successful Android GetAssertion/HID logs and WebAuthn.io's logged-in profile; the second successful sign-in is user-reported. The profile contains multiple credentials, so transport arrays cannot be mapped to the exact asserted credential. This passes the scoped BLE-created-credential-over-Classic assertion check, not Classic registration, Classic-created-credential-over-BLE, interruption recovery, lifecycle matrix, or complete acceptance. Coverage remains owner-deferred.
- **status**: `Passed (BLE-created credential authenticated over current-candidate Classic HID; user reports second authentication also succeeded) / Unverified (reverse cross-transport ceremony, interruption recovery and remaining acceptance matrix)`.
- **artifact_link**: [Classic HID activation Record 131](#record-131-current-candidate-ble-to-classic-restart-and-hid-activation-t027--partial), [BLE registration/authentication Record 130](#record-130-current-asus-ble-registration-and-authentication-round-trip-t031--partial)

### Record 133: Current-Candidate Classic HID Registration (T027 — Partial)
- **requirement**: Parent T027; after the BLE-to-Classic restart, complete a real Classic registration and assertion procedure.
- **scope**: Register an additional synthetic credential on the current ASUS candidate while Classic HID is selected and paired to Windows.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; ASUS shrunkDebug SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: On WebAuthn.io, use `codex-synthetic-064-20261103`; set attachment Cross-Platform, Security Key hint, and user verification Required; choose Windows Bluetooth security key and complete ASUS verification.
- **environment**: ASUS_AI2302 / Android 15/API35 (`R7AIB7002901AZ3`), Windows 11 build `10.0.26300.9818`, host `LOQ15IRX10-3`, ChatGPT integrated browser, WebAuthn.io; 2026-11-07.
- **expected_observed_result**: WebAuthn.io displayed `Success! Now try to authenticate...`. Android logged successful user verification, a 218-byte MakeCredential CBOR response for a 32-byte credential ID, successful FIDO2 operation and accepted HID reports. The raw credential ID was not captured.
- **date**: 2026-11-07.
- **limitations**: This proves Classic HID registration and a follow-up Classic assertion on the current candidate. WebAuthn.io showed the newly listed device-bound credential with USB transport; exact credential IDs and allow-list bytes were deliberately not captured. This does not complete reverse Classic-to-BLE authentication, event/credential comparison or the complete parent acceptance matrix. Coverage remains owner-deferred.
- **status**: `Passed (current-candidate Classic MakeCredential and user-verified GetAssertion; WebAuthn.io login success) / Unverified (reverse BLE ceremony and remaining acceptance)`.
- **artifact_link**: [registration trace](../../feature/fido2/build/reports/fido2/064/asus-current-classic-registration-20261107.txt), [assertion trace](../../feature/fido2/build/reports/fido2/064/asus-current-classic-authentication-20261107.txt), [prior Classic HID activation Record 131](#record-131-current-candidate-ble-to-classic-restart-and-hid-activation-t027--partial), [cross-transport assertion Record 132](#record-132-current-candidate-ble-created-credential-authenticated-over-classic-t027--partial)

### Record 134: Current-Candidate Classic-to-BLE Restart and WebAuthn Ceremony (T031 — Partial)
- **requirement**: Parent T031; save BLE while Classic is active, apply the saved choice only after process restart, then complete a real Windows BLE ceremony.
- **scope**: Exercise the reverse transport selection on ASUS and authenticate on BLE after restart using the synthetic account's available credentials.
- **revision**: Worktree branch 064-bluetooth-transport-selection; source HEAD 39acc7f87768ab5a6bbe539ae31e4f7c386f667c; ASUS shrunkDebug APK SHA-256 4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4.
- **named_procedure**: User saved FIDO Bluetooth LE in Chimali while Classic was active, force-stopped/relaunched, and explicitly started BLE; WebAuthn.io Authenticate was run for codex-synthetic-064-20261103 with Security Key hint and Required user verification.
- **environment**: ASUS_AI2302 / Android 15/API35 (R7AIB7002901AZ3), Windows 11 build 10.0.26300.9818, host LOQ15IRX10-3, ChatGPT integrated browser, WebAuthn.io; 2026-11-07.
- **expected_observed_result**: Android GATT server accepted Windows connections and completed allow-listed GetAssertion requests. WebAuthn.io /profile displayed You're logged in!; the account listed five device-bound credentials, including BLE-capable transport entries.
- **date**: 2026-11-07.
- **limitations**: The account profile contains multiple credentials; raw IDs/allow-list bytes were not copied, so the asserted credential cannot be mapped conclusively to the new Classic registration in Record 133. This proves successful BLE ceremony after the Classic-to-BLE preference/restart flow, but not credential/event before/after equivalence. Parent T031's real ceremony requirement is satisfied; parent T041 and remaining lifecycle/recovery checks remain open. Coverage is owner-deferred.
- **status**: Passed (user-reported Classic-to-BLE save/restart/start; current-candidate GATT GetAssertion and WebAuthn.io login success) / Unverified (exact credential mapping and remaining acceptance matrix).
- **artifact_link**: sanitized filtered output ../../feature/fido2/build/reports/fido2/064/asus-current-classic-to-ble-authentication-20261107.txt; Classic registration/assertion Record 133; BLE registration/authentication Record 130
### Record 135: Focused Current Credential-Retention Regression Rerun (T039 Support)
- **requirement**: Validate the multi-credential projection and migration correction before further physical ceremonies.
- **scope**: Rerun the schema migration and DAO tests against the current working tree after the credential-retention correction.
- **revision**: Branch 064-bluetooth-transport-selection; HEAD 39acc7f87768ab5a6bbe539ae31e4f7c386f667c plus uncommitted working-tree changes.
- **named_procedure**: Run Fido2DatabaseCredentialMigrationTest and PasskeyCredentialDaoTest using the local Gradle 9.6.0 distribution and worktree-local Gradle user home.
- **environment**: Windows 11, Gradle 9.6.0, worktree D:\octav\source\repos\.worktrees\chimali\ofrenda; 2026-11-07.
- **expected_observed_result**: Migration regression 1/1, DAO Get operations 1/1, DAO insert/update operations 3/3; total 5 passed with no failures/errors/skips. Gradle reported BUILD SUCCESSFUL in 28 seconds.
- **date**: 2026-11-07.
- **limitations**: Focused run only. It does not replace the prior full 1,148-test FIDO2 host suite, the full configured device suite, static analysis, coverage or accepted-target nonregression. T039 remains open for remaining verification and explicitly deferred coverage.
- **status**: Passed (5 focused regression tests).
- **artifact_link**: sanitized run summary ../../feature/fido2/build/reports/fido2/064/credential-migration-dao-focused-test-20261107.txt; migration test feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/database/Fido2DatabaseCredentialMigrationTest.kt
### Record 136: Current Worktree Static Analysis and Release Lint (T039 — Partial)
- **requirement**: Parent T039 requires local static analysis and configured tests, then separately requires trusted accepted-target coverage/nonregression evidence.
- **scope**: Run repository KtLint, Detekt and release Android lint after the credential-retention migration and current feature changes.
- **revision**: Branch `064-bluetooth-transport-selection`; HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus current working-tree changes.
- **named_procedure**: Using worktree-local Gradle 9.6.0 and Gradle user home, run `ktlintCheck detekt lintRelease --no-configuration-cache`.
- **environment**: Windows 11; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; 2026-11-07.
- **expected_observed_result**: `BUILD SUCCESSFUL` in 1m 5s; 550 actionable tasks, 46 executed and 504 up-to-date. KtLint, Detekt and `:app:lintRelease` completed successfully.
- **date**: 2026-11-07.
- **limitations**: Combined with Record 135's five focused migration/DAO tests and the previously recorded configured host suites, this supports the local static/test portion. It does not run the trusted candidate-versus-accepted-target coverage assessment or establish coverage eligibility. Coverage remains owner-deferred, and parent T039 stays open. The lifecycle and full acceptance items T035/T041 also remain open.
- **status**: Passed (repository KtLint, Detekt and release lint) / Unverified (trusted coverage/nonregression prerequisite and complete acceptance).
- **artifact_link**: Gradle console result from this worktree run; focused regression summary [Record 135](#record-135-focused-current-credential-retention-regression-rerun-t039-support).

### Record 137: Post-Ceremony Synthetic Account Transport Inventory (T041 Support)
- **requirement**: Parent T041 requires before/after synthetic credential and committed-event comparison for the transport switch matrix.
- **scope**: Read the integrated WebAuthn.io profile after the current Classic-to-BLE authentication to verify account-level credential inventory and browser success state.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; current ASUS APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Inspect the already completed integrated-browser WebAuthn.io `/profile` page for synthetic account `codex-synthetic-064-20261103`; do not modify or delete credentials.
- **environment**: ASUS_AI2302 / Android 15/API35, Windows 11, integrated browser, WebAuthn.io; 2026-11-07.
- **expected_observed_result**: The page showed `You're logged in!` and five device-bound credentials. Three credential entries listed `ble` and `usb` transports; two listed `usb` only.
- **date**: 2026-11-07.
- **limitations**: The UI masks credential IDs and no before-switch inventory or committed-event IDs/counts were captured. The transport arrays therefore cannot be mapped to the exact credential selected in the latest assertion, and this account snapshot does not satisfy T041's event/credential comparison or complete SC-001–006 matrix.
- **status**: Observed (post-ceremony account inventory) / Unverified (credential identity mapping, event comparison and full acceptance matrix).
- **artifact_link**: Integrated-browser WebAuthn.io profile observed after the successful Classic-to-BLE assertion; related physical ceremony [parent Record 134](#record-134-current-candidate-classic-to-ble-restart-and-webauthn-ceremony-t031--partial) and [BLE Record 147](../064-fido-ble-transport/implementation-evidence.md#record-147-parent-gated-current-asus-ble-assertion-after-classic-to-ble-restart-t023t035--partial).

### Record 138: Current-Candidate ASUS Advertiser Permission Revocation and Recovery (T035 — Partial)
- **requirement**: Parent T035 requires lifecycle/recovery evidence in both selected transports, including permission loss and explicit recovery without fallback.
- **scope**: Revoke and restore only the current app's Android 15 `BLUETOOTH_ADVERTISE` runtime permission while BLE is active; observe service restart, selected-mode state and explicit recovery.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus current working-tree changes; installed candidate APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: While advertising, revoke `android.permission.BLUETOOTH_ADVERTISE`; inspect permission, process, foreground service and logcat; restore permission, launch MainActivity, confirm stopped state, then tap Start Authenticator and confirm advertising resumes.
- **environment**: ASUS_AI2302 / Android 15/API35 (R7AIB7002901AZ3), Windows 11; 2026-11-07.
- **expected_observed_result**: Permission changed from granted to denied. ActivityManager scheduled the sticky service restart and restarted the package process. After regrant and activity launch, Chimali showed `Ready to Start` with advertising stopped. Explicit Start transitioned to `Ready for Authentication`; ActivityManager allowed Retry foreground-service startup and Android logged `StartAdvertisingSet`.
- **date**: 2026-11-07.
- **limitations**: No in-flight CTAP request was active during revocation. Stale-bond recovery and cancellation remain unverified; this is a single BLE permission recovery check, not the whole parent lifecycle matrix. Coverage remains owner-deferred.
- **status**: Passed (current-candidate BLE advertiser permission revocation/regrant and explicit recovery) / Unverified (ceremony interruption/cancellation, stale-bond and remaining matrix).
- **artifact_link**: sanitized trace [current ASUS permission recovery](../../feature/fido2/build/reports/fido2/064/asus-current-advertiser-permission-recovery-20261107.txt); see also earlier [BLE lifecycle evidence](../064-fido-ble-transport/implementation-evidence.md#record-099-asus-advertiser-permission-recovery-t035-partial).

### Record 139: Current Connected FIDO Device Suite and BLE Discovery Rerun (T039 — Partial)
- **requirement**: Parent T039 requires configured host/device tests in addition to static analysis; tests with missing prerequisites remain unverified.
- **scope**: Run the configured connected FIDO2 Android device suite on both attached phones, then repeat the BLE service-discovery test on Mi with ASUS advertising as the separate peer.
- **revision**: Branch `064-bluetooth-transport-selection`; HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes.
- **named_procedure**: Run `:feature:fido2:connectedAndroidDeviceTest --no-configuration-cache` on ASUS/API35 and Mi/API29. Then run only `FidoBleAdvertiserDiscoveryDeviceTest` on Mi/API29 with the ASUS current candidate advertising.
- **environment**: Windows 11; ASUS_AI2302/API35 (R7AIB7002901AZ3); Mi A2/API29 (2ac6876); worktree-local Gradle 9.6.0; 2026-11-07.
- **expected_observed_result**: Combined run reports ASUS 58 tests/7 failures and Mi 55 tests/6 failures, with no errors/skips in either report. Both devices' failures include five controlled Perfetto/research cases and the explicitly opt-in 50-cycle peer stress test, reported as assumption violations. ASUS also reports discovery failure because it saw zero nearby results and no distinct FIDO advertiser; the Mi discovery case passed. A subsequent Mi-only discovery run with ASUS active as a peer passed 1/1 (Gradle BUILD SUCCESSFUL in 40s).
- **date**: 2026-11-07.
- **limitations**: The full connected task is not green because the controlled procedures need their declared setup and ASUS lacked a separate advertiser during its scan. The isolated discovery rerun only addresses that test. This is not full lifecycle/ceremony acceptance or trusted coverage/nonregression; coverage is owner-deferred and T039 remains open.
- **status**: Partial (isolated current-candidate discovery passed; all unconfigured test outcomes recorded as failures/unverified, not passed).
- **artifact_link**: [sanitized connected-suite summary](../../feature/fido2/build/reports/fido2/064/connected-device-suite-20261107.txt); Mi isolated test XML `../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi A2 - 10.xml`.

## SC-001–006 Acceptance Audit (2026-11-07)

Scope for all rows: branch `064-bluetooth-transport-selection`, source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus current working-tree changes; physical ASUS_AI2302/API35, Windows 11 build `10.0.26300.9818`, host `LOQ15IRX10-3`, integrated WebAuthn.io, synthetic account `codex-synthetic-064-20261103`. See the linked evidence records for the full TRD §9.1 fields. No raw credential IDs or event payloads were copied.

| Criterion | Current evidence and gap | Status |
|---|---|---|
| SC-001 — Fresh install BLE registration and authentication without opening settings | After the authorized reset of only `com.chimali.shrunk`, the current candidate opened first-run onboarding; the user enabled Passkey Authenticator and tapped Start without visiting transport Settings. The app showed BLE advertising, then completed MakeCredential and user-verified GetAssertion with WebAuthn.io success (Records 128–130; BLE Records 142–144). | Passed |
| SC-002 — Both switch directions preserve existing credentials/events and complete new-transport registration and authentication | BLE→Classic saved selection/restart, BLE-created credential assertion on Classic, and Classic registration/assertion passed (Records 131–133). Classic→BLE saved selection/restart and BLE assertion passed (Record 134/BLE Record 147); the current BLE assertion succeeded again in Record 142. During a fresh bidirectional switch/restart check, ASUS's encrypted `fido2.db` was 110,592 bytes and had the same SHA-256 (`d5248459eb986f44afa46e57ed08a4f0f6562b808969403fb457ac629548f517`) before the first switch, after Classic restart, and after restoring/restarting BLE; no WAL/SHM sidecar was present. The passkey manager showed all three synthetic rows after both directions (Record 146). The encrypted DB was not decrypted; byte-for-byte identity proves its credential and committed-event contents were unchanged during this selection-only procedure. | Passed |
| SC-003 — Both transports independently complete registration and authentication with Windows | BLE registration and authentication passed (Records 129–130); current-candidate Classic registration and user-verified HID authentication passed (Record 133; sanitized traces linked there). | Passed |
| SC-004 — Preference survives reboot and selected transport starts only after explicit Start | Mi/API29 BLE preference survived reboot, remained Ready to Start and activated only after explicit Start (Record 064). On ASUS/API35, saved Classic survived reboot, remained stopped on first launch, then explicit Start acquired the HID_DEVICE profile and logged `BluetoothHidTransport connected and advertising`; BLE was restored and explicitly started afterward (Record 148). | Passed |
| SC-005 — Concurrent selection during registration and authentication leaves original ceremony/events intact on both transports | Deterministic bounded host tests cover both ceremony types and both transport modes (Record 007; mapping summary at top of this evidence file). Physical concurrent save during a held confirmation and before/after credential/event comparison were not performed. | Partial |
| SC-006 — Saved, active and pending transport are distinct before restart; restored mode is selected but not started afterward | Settings semantics/UI tests passed (BLE Record 069). Current ASUS inspection confirms BLE active with Classic pending and restart required, then Classic selected but not started. In the reverse direction, BLE is saved while the current process is Classic and not started, a restart notice appears, and after restart BLE is selected but not started until explicit Start (Records 131, 134 and current Record 140). | Passed |

This audit does not close T041: SC-005 still has an unverified physical in-flight save and credential/event comparison gap, and the broader switching/recovery matrix remains open. Parent T035/T039 and BLE T035/T038 remain open as listed in the task files; owner-deferred coverage is not a pass. SC-001 used an authorized clean-data first-run profile with an in-place candidate install rather than an uninstall/reinstall; transport selection and ceremonies followed the fresh-profile path.

### Record 140: Current ASUS Bidirectional Saved-Choice Restart and Credential Visibility (T035/T041 — Partial)
- **requirement**: Parent T035 requires both-mode process restart and selection recovery; T041/SC-002 and SC-006 require transport-choice semantics and credential preservation across each switch.
- **scope**: On the current ASUS candidate, save Classic while BLE is active, restart and verify Classic selected/not started; then save BLE while Classic is the process choice, restart and explicitly start BLE. Check local synthetic passkey visibility across these transitions.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; ASUS candidate APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Inspect settings and the passkey manager; save the alternative transport; confirm active mode and restart notice; force-stop and relaunch MainActivity; confirm new process selection is not started; inspect the passkey manager; repeat the reverse switch; explicitly start BLE.
- **environment**: ASUS_AI2302 / Android 15/API35 (R7AIB7002901AZ3), Windows 11 build 10.0.26300.9818, host LOQ15IRX10-3; synthetic account codex-synthetic-064-20261103; 2026-11-07.
- **expected_observed_result**: BLE→Classic save left BLE active and displayed the restart notice. After force-stop/relaunch, Classic was checked and `Selected; not started`; the BLE foreground service was absent. The three local synthetic passkey rows remained visible. Classic→BLE save showed BLE pending while Classic remained the process choice; after restart BLE was selected but not started, and explicit Start returned the app to `Ready for Authentication` with the foreground service active. The same three local synthetic passkey rows remained visible after restoration.
- **date**: 2026-11-07.
- **limitations**: No ceremony was in flight; this does not test concurrent selection, reboot, OS-managed process death, stale-bond recovery or unavailable-mode recovery. Only passkey row count and synthetic account labels were compared; IDs and committed event sequences were not read, so SC-002's exact preservation claim is unverified. Parent T035/T041 remain open.
- **status**: Passed (current-candidate bidirectional save/process-restart state and explicit BLE start) / Observed (three synthetic passkey rows visible across switches) / Unverified (event/ID equality and remaining matrix).
- **artifact_link**: [sanitized UI/device sequence](../../feature/fido2/build/reports/fido2/064/asus-current-bidirectional-selection-restart-20261107.txt); earlier ceremony records [BLE→Classic](#record-132-current-candidate-ble-created-credential-authenticated-over-classic-t027--partial) and [Classic→BLE](#record-134-current-candidate-classic-to-ble-restart-and-webauthn-ceremony-t031--partial).

### Record 141: Full Legacy Credential Projection Preservation Regression (T039 Support)
- **requirement**: Parent T039; validate the cross-transport credential-retention correction and migration before acceptance.
- **scope**: Strengthen and rerun the SQLDelight migration 2 host regression to verify all legacy passkey columns survive table rebuild, the dependent view remains usable, required indexes are restored and the production DAO insertion path can add a second credential for the same RP/user.
- **revision**: Branch `064-bluetooth-transport-selection`; HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes.
- **named_procedure**: Run `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.database.Fido2DatabaseCredentialMigrationTest`, then `:feature:fido2:ktlintAndroidHostTestSourceSetCheck` using worktree-local Gradle 9.6.0.
- **environment**: Windows 11; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; 2026-11-07.
- **expected_observed_result**: The migration test passed 1/1 with no failure/error/skip. Assertions cover timestamps, algorithm, credential policy, derivation profile, encrypted metadata bytes, label, public key, RP metadata/indexes, user metadata/indexes, sign count, dependent view contents and four indexes. After migration, `PasskeyCredentialDao.insertCredentialProjection` inserted a new credential sharing the legacy RP/user; both IDs were returned. The host-test KtLint check passed.
- **date**: 2026-11-07.
- **limitations**: Host SQLite migration fixture, not an ASUS legacy database migration; ASUS was reset under previous authorization and had no old local credentials. This does not establish exact cross-transport credential-ID/event-history equality or coverage nonregression. Parent T039 remains open with coverage deferred.
- **status**: Passed (complete legacy projection migration regression and KtLint).
- **artifact_link**: test source `../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/database/Fido2DatabaseCredentialMigrationTest.kt`; result `../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.database.Fido2DatabaseCredentialMigrationTest.xml`; KtLint report `../../feature/fido2/build/reports/ktlint/ktlintAndroidHostTestSourceSetCheck/ktlintAndroidHostTestSourceSetCheck.txt`.

### Record 142: Current ASUS BLE Authentication After Classic-to-BLE Restore (T031/T041 Support)
- **requirement**: Parent T031 and SC-003 require an end-to-end Windows WebAuthn authentication over BLE; T041 requires independently recorded ceremony outcomes.
- **scope**: After the user restored BLE and confirmed it was ready, complete the pending WebAuthn.io authentication for synthetic account `codex-synthetic-064-20261103` using the Windows Bluetooth security-key flow.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; current candidate as recorded in parent Record 140.
- **named_procedure**: User completed “Cambiar → Llave de seguridad” and ASUS verification with the phone held in portrait; inspect the integrated WebAuthn.io tab for the resulting ceremony state.
- **environment**: ASUS_AI2302 / Android 15/API35, Windows 11 build 10.0.26300.9818, integrated browser WebAuthn.io; synthetic account codex-synthetic-064-20261103; 2026-11-07.
- **expected_observed_result**: The integrated page displayed “You're logged in!” after the authentication attempt. The account profile listed five device-bound credentials, including three whose transport arrays contain `ble` and `usb`.
- **date**: 2026-11-07.
- **limitations**: WebAuthn.io masks credential IDs, and no correlated Android/Windows trace or exact assertion credential ID was captured in this observation. The page state confirms successful WebAuthn authentication in the BLE flow reported by the user, but cannot identify which listed credential was asserted. It does not close SC-002, SC-005 or the remaining T035/T041 recovery and comparison checks.
- **status**: Passed (user-operated BLE authentication; integrated-browser WebAuthn success observed) / Unverified (exact credential mapping and remaining acceptance matrix).
- **artifact_link**: Integrated-browser WebAuthn.io profile observed immediately after the attempt; credential inventory is summarized in parent Record 137.

### Record 143: Current-Candidate Mi BLE Explicit Recovery Start (T035 Support)
- **requirement**: Parent T035 requires selected transports to remain stopped until explicit recovery/start and documents unavailable-mode recovery without fallback.
- **scope**: On Mi/API29, inspect the saved BLE selection after the previously recorded reboot persistence check, explicitly start the authenticator, and verify Android GATT service registration, advertising, and foreground service state.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; installed APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Inspect MainActivity, confirm `Ready to Start`, tap `Start Authenticator`, then inspect activity/service state, filtered logcat and Bluetooth GATT registration.
- **environment**: Xiaomi Mi A2 (jasmine), Android 10/API29, ADB serial `2ac6876`; synthetic package `com.chimali.shrunk`; Windows 11; 2026-11-07.
- **expected_observed_result**: Before the tap the UI reported `Ready to Start` / `Tap 'Start Authenticator' to begin`. Afterward it reported `Advertising...` / `Ready to pair with a new host PC`. `Fido2TransportService` was foreground; Android registered FIDO service `0000fffd-0000-1000-8000-00805f9b34fb` and Device Information service `0000180a-0000-1000-8000-00805f9b34fb`; Bluetooth stack reported advertising-set status 0. The installed package hash matches the current ASUS candidate in Record 140.
- **date**: 2026-11-07.
- **limitations**: This captures explicit start after a stopped selected BLE state, paired with the earlier Mi reboot-persistence result (Record 064); it is not a new reboot run. It does not cover Classic reboot, physical ceremony interruption/cancellation, stale-bond recovery, or the complete recovery matrix. Parent T035/T041 remain open.
- **status**: Passed (current-candidate Mi saved-BLE explicit start and service/advertiser activation) / Unverified (other lifecycle/recovery checks).
- **artifact_link**: Current Mi UI hierarchy and ADB/logcat state observed during this run; earlier reboot selection evidence [Record 064](#record-064-mi-api29-ble-preference-survives-reboot-and-requires-explicit-start).

### Record 144: Current FIDO2 Host Suite and Changed-Source Lint Rerun (T039 Support)
- **requirement**: Parent T039 requires configured host tests and local static checks before the separate trusted coverage/nonregression assessment.
- **scope**: Run the complete FIDO2 Android host suite and KtLint checks for Android production and Android host-test source sets against the current working tree.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes.
- **named_procedure**: Run Gradle 9.6.0 tasks `:feature:fido2:testAndroidHostTest`, `:feature:fido2:ktlintAndroidMainSourceSetCheck`, and `:feature:fido2:ktlintAndroidHostTestSourceSetCheck` with `--no-configuration-cache` and the worktree-local Gradle user home.
- **environment**: Windows 11; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; 2026-11-07.
- **expected_observed_result**: `BUILD SUCCESSFUL` in 48 seconds. The 155 XML suites report 1,148 tests, 0 failures, 0 errors, and 0 skipped. Both KtLint tasks passed (one was up-to-date).
- **date**: 2026-11-07.
- **limitations**: This is FIDO2 Android host-test and source-set lint evidence only; it does not rerun the full repository local CI or connected device suite, complete other modules' checks, establish accepted-target coverage/nonregression, or close physical lifecycle and acceptance gaps. Coverage remains owner-deferred. Parent T039 remains open.
- **status**: Passed (current FIDO2 host suite and requested lint tasks) / Unverified (remaining T039 gates and coverage).
- **artifact_link**: XML reports `../../feature/fido2/build/test-results/testAndroidHostTest/`; current Gradle console result from this work session.

### Record 145: Full Ordinary Local CI Rerun Without Coverage (T039 Support)
- **requirement**: Parent T039 requires repository static analysis, release lint, comprehensive compilation and configured unit/host suites before its independent coverage/nonregression gate.
- **scope**: Run the repository's ordinary local CI pipeline in no-fix mode against the current working tree, deliberately omitting opt-in coverage.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes.
- **named_procedure**: Run `tools/local-ci.ps1 -NoFix -NoConfigurationCache` with `GRADLE_USER_HOME` set to this worktree's `.tmp/gradle-home`; do not pass `-RunCoverage`.
- **environment**: Windows 11; Gradle 9.6.0, JDK 17, Android SDK 37; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; 2026-11-07.
- **expected_observed_result**: Pipeline completed successfully in 139.87 seconds. Repository KtLint, Detekt, release lint, compile-all, unit tests and configured core/FIDO2 Android host-test tasks passed. A Kotlin daemon attempt emitted a warning and fell back to non-daemon compilation; compilation still succeeded. Most tasks were up-to-date.
- **date**: 2026-11-07.
- **limitations**: The script did not run connected device suites or coverage. Coverage remains owner-deferred, and trusted candidate-versus-accepted-target completeness/floor/raw-count nonregression is unverified. T039 stays open; this result does not close physical T035/T041.
- **status**: Passed (ordinary local CI without coverage) / Unverified (connected-suite remainder and trusted coverage/nonregression).
- **artifact_link**: Gradle reports in module `build/reports` and `build/test-results`; full command result from this work session.

### Record 146: Current ASUS Bidirectional Switch Preserves Encrypted Credential/Event Store (T041/SC-002)
- **requirement**: Parent T041 and SC-002 require both transport switches to preserve existing credentials and committed event history while applying the saved choice after restart.
- **scope**: Capture the encrypted local database byte identity before and after BLE→Classic and Classic→BLE selection/restart sequences, and verify passkey rows remain visible. No ceremony was in flight during the switch comparison.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; installed ASUS candidate SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: On ASUS/API35 with BLE active, hash `com.chimali.shrunk`'s `databases/fido2.db` read-only through `run-as`; save Classic, force-stop/relaunch and confirm Classic selected/not started; inspect saved passkeys; save BLE, force-stop/relaunch and confirm BLE selected/not started; explicitly start BLE; hash the same database again. Do not decrypt, print or otherwise expose database contents.
- **environment**: ASUS_AI2302, Android 15/API35 (R7AIB7002901AZ3), Windows 11 build 10.0.26300.9818; synthetic account codex-synthetic-064-20261103; 2026-11-07.
- **expected_observed_result**: The encrypted `fido2.db` was 110,592 bytes with SHA-256 `d5248459eb986f44afa46e57ed08a4f0f6562b808969403fb457ac629548f517` before the first switch, after Classic was restored, and after BLE was restored and started. The database directory contained no WAL or SHM sidecar at the observations. Classic loaded `Ready to Start` and `FIDO2 / WebAuthn over Bluetooth HID`, with no transport service; BLE loaded `Ready to Start` and no service until explicit Start, then returned to `Ready for Authentication`, with the FIDO GATT service registered and advertising. The passkey manager showed three `webauthn.io` rows after Classic and after BLE restoration.
- **date**: 2026-11-07.
- **limitations**: The database is encrypted, so its credential IDs and event sequences were not decoded or enumerated. Identical full database bytes, absence of sidecars, and three visible rows prove no credential/event database mutation during these selection-only restarts, but this is not a per-ID semantic inventory. No ceremony was in flight; SC-005 remains partial. Classic reboot, process-death and remaining T035 recovery cases remain open.
- **status**: Passed (both direction selection/restart preserved the byte-identical encrypted credential/event database; all three passkey rows remained visible) / Unverified (in-flight selection and remaining acceptance matrix).
- **artifact_link**: Sanitized device sequence recorded in this evidence entry; related bidirectional semantics [Record 140](#record-140-current-asus-bidirectional-saved-choice-restart-and-credential-visibility-t035t041--partial), BLE→Classic/Classic→BLE ceremonies [Records 132–134](#record-132-current-candidate-ble-created-credential-authenticated-over-classic-t027--partial).

### Record 147: ASUS User-Cancelled BLE Authentication Attempt (T035 Support — Unverified)
- **requirement**: Parent T035 requires physical ceremony cancellation/interruption recovery and restoration to a usable selected transport.
- **scope**: Attempt one user-operated cancellation during the synthetic WebAuthn authentication flow on ASUS with BLE selected and advertising.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; installed candidate SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Start WebAuthn.io authentication for `codex-synthetic-064-20261103`; the user reports tapping Cancel on ASUS; inspect the browser, app state, service and recent Android logs.
- **environment**: ASUS_AI2302 / Android 15/API35 (R7AIB7002901AZ3), Windows 11, integrated browser WebAuthn.io; 2026-11-07.
- **expected_observed_result**: The user reports tapping Cancel on the ASUS device during the active authentication attempt. WebAuthn.io displayed “The operation either timed out or was not allowed.” The ASUS authenticator returned to `Ready for Authentication` / `Waiting for request from PC`; its foreground service remained active and Android still listed the FIDO `0xFFFD` service as registered and started.
- **date**: 2026-11-07.
- **limitations**: No new GATT connection or `CMD_CANCEL received` log could be correlated with this attempt; recent filtered app logs were empty for the action. The precise Android prompt and protocol stage at the user's Cancel action were not independently captured. An earlier, separate disconnect at 13:37 logged an invalid-state GetAssertion followed by generic in-flight command cancellation, but this does not prove CTAPHID_CANCEL. This supports user-reported phone-side cancellation recovery only; protocol-level CTAP cancellation remains unverified and parent T035 remains open.
- **status**: Passed (user-reported ASUS Cancel action returned the app to Ready with BLE service active) / Unverified (protocol-level CTAPHID_CANCEL and correlated GATT trace).
- **artifact_link**: Integrated-browser WebAuthn.io state and current ASUS ADB/UI/service inspection from this work session; protocol-cancel behavior remains covered only by deterministic host tests.

### Record 148: ASUS Classic Preference Survives Device Reboot (SC-004)
- **requirement**: Parent SC-004 requires the selected transport preference to survive a device reboot and become active only after an explicit start.
- **scope**: Verify the saved Classic HID choice after a full ASUS reboot, confirm the authenticator remains stopped on cold start, then restore BLE and explicitly start it.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; installed candidate SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: With Classic saved, reboot ASUS. After unlock, launch MainActivity and inspect the selected mode and service state. Save BLE in Settings, force-stop/relaunch, verify Ready to Start with no service, then explicitly tap Start and inspect GATT/service state.
- **environment**: ASUS_AI2302 / Android 15/API35 (ADB serial `R7AIB7002901AZ3`), Windows 11 host `LOQ15IRX10-3`; app `com.chimali.shrunk`; 2026-11-07.
- **expected_observed_result**: After reboot, MainActivity showed `Ready to Start`, `Start Authenticator`, and `FIDO2 / WebAuthn over Bluetooth HID`; no transport service was running. This verifies the saved Classic choice survived reboot and remained inactive until Start. After explicit Start and the approved 120-second visibility prompt, MainActivity returned `Ready for Authentication`; Android logs show the `HID_DEVICE` profile proxy was acquired and `BluetoothHidTransport connected and advertising`. BLE was then saved and applied after force-stop/relaunch; the app showed `FIDO2 / WebAuthn over FIDO Bluetooth LE` and remained `Ready to Start` with no service until explicit Start. Start returned `Ready for Authentication` / `Waiting for request from PC`; `Fido2TransportService` was foreground and Android registered FIDO GATT service `0000fffd-0000-1000-8000-00805f9b34fb` with active advertising.
- **date**: 2026-11-07.
- **limitations**: This completes the transport-preference reboot criterion across the previously recorded Mi BLE reboot result (Record 064) and this ASUS Classic reboot. It does not exercise OS process death in both modes, in-flight save, unavailable-mode recovery, or the rest of T035/T041. SC-005 remains partial.
- **status**: Passed (Classic selection persisted across full ASUS reboot, remained stopped until explicit Start, then activated; BLE restoration/start also passed) / Unverified (in-flight and remaining lifecycle/recovery matrix).
- **artifact_link**: ASUS MainActivity UI hierarchy and ADB window, service and Bluetooth GATT state observed after reboot and after explicit BLE Start; preceding bidirectional store comparison [Record 146](#record-146-current-asus-bidirectional-switch-preserves-encrypted-credentialevent-store-t041sc-002).

### Record 149: Current Mi BLE Force-Stop and Explicit-Start Recovery (T035 Support)
- **requirement**: Parent T035 requires force-stop/relaunch and explicit recovery checks for each selected transport.
- **scope**: On Mi/API29 with BLE active, force-stop `com.chimali.shrunk`, verify service/process termination, relaunch MainActivity and verify the persisted BLE choice remains stopped until explicit Start.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; installed candidate SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4` (same candidate verified in Record 143).
- **named_procedure**: Confirm BLE Advertising, issue Android `am force-stop`, verify no process/service, launch `MainActivity`, inspect stopped selection, tap Start, then inspect UI, foreground service and registered BLE FIDO service.
- **environment**: Xiaomi Mi A2 (jasmine), Android 10/API29 (ADB serial `2ac6876`); Windows 11; 2026-11-07.
- **expected_observed_result**: Force-stop removed the app process and `Fido2TransportService`. Relaunch showed `Ready to Start`, `Start Authenticator`, and `FIDO2 / WebAuthn over FIDO Bluetooth LE`, with no transport service. Explicit Start changed the UI to `Advertising...` / `Ready to pair with a new host PC`; `Fido2TransportService` returned in the foreground and Android reported FIDO service `0000fffd-0000-1000-8000-00805f9b34fb` started and registered.
- **date**: 2026-11-07.
- **limitations**: This is the BLE-selected Mi force-stop/relaunch subcheck only. It does not cover Classic force-stop, process death, physical ceremony interruption, stale-bond recovery, in-flight setting changes or full T035/T041. SC-005 remains partial.
- **status**: Passed (current-candidate Mi BLE selection persisted across force-stop/relaunch; explicit Start restored advertising/GATT service) / Unverified (remaining parent lifecycle/recovery matrix).
- **artifact_link**: Mi UI hierarchy before/after explicit Start and ADB process/service/Bluetooth GATT state from this run; explicit prior stopped-state start [Record 143](#record-143-current-candidate-mi-ble-explicit-recovery-start-t035-support).

### Record 150: Current Mi BLE Recovery After Bluetooth Radio Off (T035 Support)
- **requirement**: Parent T035 requires unavailable-mode recovery without fallback, app restart or credential loss.
- **scope**: Turn Bluetooth off while current-candidate BLE is advertising, inspect the resulting app/service state, turn Bluetooth back on, and use the in-app Retry action.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; installed candidate SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Use Android Quick Settings to disable Bluetooth while BLE Advertising; inspect Bluetooth adapter state and Chimali UI; re-enable Bluetooth; tap Retry Authenticator; verify advertiser, service and UI recover without process restart or transport change.
- **environment**: Xiaomi Mi A2 (jasmine), Android 10/API29 (ADB serial `2ac6876`); Windows 11; 2026-11-07.
- **expected_observed_result**: With the adapter disabled (`settings global bluetooth_on=0`, Bluetooth manager `enabled=false`), Chimali showed `Error Occurred` / `BLE advertising stopped unexpectedly`, exposed `Pairing help` and `Retry Authenticator`, and retained `FIDO2 / WebAuthn over FIDO Bluetooth LE`. After restoring Bluetooth (`bluetooth_on=1`, state `ON`), Retry returned the app to `Advertising...` / `Ready to pair with a new host PC`; the foreground `Fido2TransportService` remained active and Android showed registered/started FIDO service `0000fffd-0000-1000-8000-00805f9b34fb`.
- **date**: 2026-11-07.
- **limitations**: This tests Bluetooth-radio loss/recovery on Mi, not runtime permission revocation or interrupted CTAP/GATT traffic. No Windows ceremony was in flight. Parent T035 and T041 remain open; coverage T039 remains owner-deferred.
- **status**: Passed (current-candidate BLE reports radio-off failure and recovers after Bluetooth restoration plus Retry, without app restart or fallback) / Unverified (remaining lifecycle/recovery matrix).
- **artifact_link**: Mi app UI, Android Bluetooth adapter state, foreground service and GATT map captured before/after the radio transition; earlier radio-off recovery [Record 056](#record-056-mi-api29-ble-radio-off-recovery).

### Record 151: ASUS Stale-Bond Re-Pair and BLE Authentication Recovery (T035 Support — Partial)
- **requirement**: Parent T035 requires unavailable/stale-pair recovery without deleting credentials or requiring an app restart; SC-003 requires the BLE authentication ceremony to complete.
- **scope**: Remove only the ASUS phone-side bond while Windows retains its pair, attempt BLE authentication, then recreate the bond and retry the existing synthetic credential.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; ASUS current-candidate APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Keep ASUS BLE active and synthetic data intact; forget only the phone-side `LOQ15IRX10-3` bond in Android Bluetooth Settings; attempt WebAuthn.io authentication via Windows Bluetooth security key; cancel the failed attempt, recreate the bond as reported by the user, and retry authentication.
- **environment**: ASUS_AI2302 / Android 15/API35 (ADB serial `R7AIB7002901AZ3`), Windows 11 build `10.0.26300.9818`, host `LOQ15IRX10-3`, integrated WebAuthn.io; synthetic account `codex-synthetic-064-20261103`; 2026-11-07.
- **expected_observed_result**: During the stale-bond attempt Windows displayed “Activa la clave de seguridad Bluetooth o insértela en el puerto USB”; ASUS showed no new prompt, and the attempt ended with WebAuthn.io’s generic timeout/not-allowed result. The user then reported recreating the bond. On retry, WebAuthn.io displayed “You're logged in!”; Android logs showed a successful GATT connection, MTU 517, FIDO revision `0x20`, enabled notifications, successful GetInfo and two GetAssertion operations, with the terminal notification acknowledged for both assertion responses. ASUS returned to Ready for Authentication with the foreground service and FIDO GATT server still active; Android's bonded-device list again contained `LOQ15IRX10-3`.
- **date**: 2026-11-07.
- **limitations**: The first attempt did not reach a correlated CTAP operation and the ASUS UI did not surface pairing-help guidance while Windows waited. The user reported recreating the bond, but the exact phone/Windows re-pair actions and matching comparison number were not independently captured. The successful retry proves authentication after bond restoration, not the missing in-app stale-bond guidance. T035 remains open; SC-003's BLE assertion is passed, but the complete T041 matrix remains open.
- **status**: Passed (user-reported bond recreation followed by successful BLE GetAssertion and WebAuthn.io login) / Unverified (first-attempt stale-bond diagnosis, guided recovery steps, and matching-number evidence).
- **artifact_link**: Integrated WebAuthn.io success state; ASUS app UI/logcat, bonded-device and GATT service state; supporting BLE evidence [Record 164](../064-fido-ble-transport/implementation-evidence.md#record-164-asus-stale-bond-re-pair-and-ble-authentication-recovery-t035-support--partial).

### Record 152: Current-Tree ASUS Coverage Attempt and Required Device-Suite Failure (T039 — Failed/Partial)
- **requirement**: Parent T039; coverage FR-006, FR-016, FR-017, FR-019, FR-020 and BRD NFR-ARCH-050.
- **scope**: Run current-tree ordinary local CI and the configured complete host/device coverage workload against the ASUS API 35 device; determine whether a valid full coverage report and current-target comparison can be produced.
- **revision**: Branch `064-bluetooth-transport-selection`; HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged working-tree changes; coverage APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache` with worktree-local `GRADLE_USER_HOME=.tmp/gradle-home` passed in 96.63s. Then run `./gradlew --no-configuration-cache -I tools/hdk-coverage.init.gradle --project-prop coverageHarnessRoot=<worktree> --project-prop kotlin.compiler.execution.strategy=in-process hdkHostCoverage hdkCoverageVerify`, with `ANDROID_SERIAL=R7AIB7002901AZ3`.
- **environment**: Windows 11; Gradle 9.6.0; Microsoft JDK 25.0.5; Android SDK platform 37; ASUS AI2302 / Android 15/API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Mi A2 also connected but excluded by `ANDROID_SERIAL`; 2026-11-07.
- **expected_observed_result**: Ordinary local CI passed KtLint, Detekt, release lint, compile-all, unit tests and configured Android host tests. Coverage instrumentation passed app 11/11 and common 1/1 on ASUS, then `:feature:fido2:connectedAndroidDeviceTest` failed: 58 tests, 50 passed, 6 failed, 0 errors, 0 skipped. Failures were five trace/Perfetto research procedures without their required active trace and procedure arguments, plus `FidoBleReconnectStressDeviceTest` without its explicit opt-in/separate advertiser setup. Gradle exited 1 before `hdkCoverageAnalyze`/`hdkCoverageVerify`; no valid module or repository counters/floor decision were produced. The old `baselines/current.json` indexes target `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`, while current `origin/lab/or/chimali` is `c9195ea608ff3394e32aa217e7e640e5579d6f4d`, so it cannot support the current target comparison. After instrumentation, Android no longer listed `com.chimali.shrunk`; I reinstalled the built APK without a data-clear command, producing a fresh package installation. Android uninstall normally removes package-private data, so the earlier local synthetic credential may have been removed by test-runner cleanup.
- **date**: 2026-11-07.
- **limitations**: This is a failed/incomplete coverage attempt, not coverage evidence; do not infer floors or nonregression. Resolve the procedure-gated device tests in a supported full workload, then capture a clean committed candidate and a fresh exact-target comparison with a trusted current index. The app reinstall cannot restore data removed by Android uninstall. T039 remains open.
- **status**: Passed (ordinary local CI); Failed (required FIDO2 connected-device workload; no eligible coverage report); Unverified (coverage completeness, floors and target nonregression).
- **artifact_link**: [ASUS FIDO2 device-test XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [FIDO2 HTML report](../../feature/fido2/build/reports/androidTests/connected/androidMain/index.html), [coverage policy](../../tools/coverage/policy.json).

### Record 153: ASUS Filtered Current-Tree Coverage Floors Passed (T039 — Partial)
- **requirement**: Parent T039; coverage FR-006, FR-016, FR-017, FR-019, FR-020 and BRD NFR-ARCH-050.
- **scope**: Re-run the configured host/device coverage and floor verification on ASUS API 35 after supplying the opt-in reconnect stress argument and excluding the five standalone trace-research cases under the established non-research suite procedure.
- **revision**: Branch `064-bluetooth-transport-selection`; HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged/unstaged changes; dirty candidate, so no commit/tree fingerprint or exact candidate identity is claimed.
- **named_procedure**: `ANDROID_SERIAL=R7AIB7002901AZ3`; run `.\gradlew.bat --no-configuration-cache -Pkotlin.compiler.execution.strategy=in-process -I tools/hdk-coverage.init.gradle -Pandroid.testInstrumentationRunnerArguments.notPackage=com.chimali.fido2.util.performance -Pandroid.testInstrumentationRunnerArguments.runFidoBleReconnectStress=true hdkCoverageVerify` using worktree-local `GRADLE_USER_HOME=.tmp\gradle-home`; Mi API 29 remained active as a separate FIDO advertiser.
- **environment**: Windows 11 x64; Gradle 9.6.0; Microsoft JDK 25.0.5; Android SDK platform 37; ASUS AI2302 / Android 15 API 35 / arm64-v8a / serial `R7AIB7002901AZ3`; Mi A2 / Android 10 API 29 / serial `2ac6876`; 2026-11-07.
- **expected_observed_result**: `hdkCoverageVerify` succeeded after fresh execution, analysis and verification. FIDO2 Android instrumentation XML has zero failures/errors/skips; the explicitly opted-in ASUS-to-Mi 50-cycle test passed. Canonical counters report Tier 1: `:core:data` 1277/1452 instructions (87.948%), 57/74 branches (77.027%); `:core:domain` 5124/7184 (71.325%), 412/578 (71.280%); `:core:security` 3624/4452 (81.402%), 258/386 (66.839%); `:feature:fido2` 65312/99368 (65.727%), 3991/6525 (61.165%); `:feature:authenticator` NotApplicable with 0 executable instructions. All applicable Tier 2 instruction floors pass, including app 817/2379 (34.342%), common 549/1781 (30.825%) and database 409/623 (65.650%). Repository total is 87213/147893 instructions (58.970%) against the 50% floor. The FIDO2 device report contains 53 tests, all passed, with zero errors/skips; the five `com.chimali.fido2.util.performance` experiments were excluded because each requires its own controlled tracing procedure and run arguments.
- **date**: 2026-11-07.
- **limitations**: This establishes current-tree local completeness and configured coverage floors only. No candidate-versus-current-accepted-target raw-count comparison was run: the available `baselines/current.json` index points to `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`, while current `origin/lab/or/chimali` is `c9195ea608ff3394e32aa217e7e640e5579d6f4d`; the candidate is also dirty. Do not promote a measured target or infer nonregression. The five excluded research procedures remain individually unverified. T039 remains open for exact-target nonregression and trusted evidence requirements.
- **status**: Passed (current-tree test completeness and configured local instruction/branch floors) / Unverified (exact accepted-target nonregression, clean candidate/evidence provenance and five standalone research procedures).
- **artifact_link**: [canonical raw counters](../../build/reports/hdk-coverage/counters.json); [production inventory](../../build/reports/hdk-coverage/production-inventory.json); [repository JaCoCo report](../../build/reports/hdk-coverage/repository/index.html); [ASUS FIDO2 connected-device XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml).

### Record 154: ASUS Fresh-Install BLE Default and Start (SC-001 / T027 Support)
- **requirement**: Parent SC-001 and T027; BLE as the selected default for a fresh installation and explicit startup only after the user chooses Start.
- **scope**: After Android test cleanup removed the ASUS package, reinstall the current shrunkDebug APK without a data-clear command, complete first-run feature selection, and verify default BLE mode plus explicit service activation.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged worktree changes; APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: Install `app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk`; launch MainActivity; grant Nearby devices and notification permissions using the system prompts; select Passkey Authenticator during first-run setup; observe Ready to Start before tapping Start; then inspect service and Android GATT registration.
- **environment**: ASUS AI2302 / Android 15 API 35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11; 2026-11-07.
- **expected_observed_result**: First-run feature selection completed with the Passkey Authenticator option checked. Before Start, Chimali displayed `Ready to Start` and `FIDO2 / WebAuthn over FIDO Bluetooth LE`. After Start, `Fido2TransportService` was foreground and Android Bluetooth Manager listed `com.chimali.shrunk` as a registered BLE app/server. This confirms fresh-install default selection and explicit activation on ASUS API35.
- **date**: 2026-11-07.
- **limitations**: The reinstall followed Android test-runner removal of the package, so prior local passkeys and settings may have been erased. This is a clean first-run/default/start check, not the transport switch matrix, a WebAuthn ceremony, or proof of credential preservation. Do not use it to close T035 or T041.
- **status**: Passed (fresh install defaults to BLE and activates only after explicit Start on ASUS/API35) / Unverified (credential ceremonies and remaining recovery/acceptance items).
- **artifact_link**: ASUS `android layout` snapshots of first-run Welcome, Passkey Authenticator selection, `Ready to Start`, and post-Start; ADB foreground-service and Bluetooth Manager registration snapshots; APK hash above.

### Record 155: ASUS Fresh-Install BLE Registration and Authentication (T031 / SC-001 Support)
- **requirement**: Parent T031 and SC-001/SC-003; verify a fresh-install BLE credential can be registered and used for user-verified authentication on the current integrated candidate.
- **scope**: Register and authenticate synthetic account `codex-synthetic-064-20261103` over ASUS BLE after test-runner cleanup removed the package's previous private data.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged/unstaged changes; APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: Complete first-run Passkey Authenticator setup, select the default BLE mode and explicitly start it; in the integrated WebAuthn.io tab register the synthetic account through Windows `Cambiar → Llave de seguridad`, complete ASUS verification, then authenticate with the new credential.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11; integrated WebAuthn.io; 2026-11-07.
- **expected_observed_result**: The user reported WebAuthn.io `Success! Now try to authenticate...` after registration and `You're logged in!` after authentication. Android logged successful MakeCredential admission/processing, a 32-byte credential ID, 218-byte CBOR response, and acknowledged terminal notification (5.129 s). The successful GetAssertion logged one matching candidate, completed processing, and acknowledged its terminal notification (5.967 s). Earlier attempts in the same interaction returned `NO_CREDENTIALS` before registration had committed; the subsequent registration and assertion succeeded.
- **date**: 2026-11-07.
- **limitations**: The credential ID bytes and database/event sequence were not captured, and only BLE was exercised in this fresh data set. This does not complete Classic registration or the transport-switch identity matrix; T041 remains open.
- **status**: Passed (fresh-install BLE registration and user-verified assertion; WebAuthn.io and Android terminal acknowledgement both observed) / Unverified (exact identity mapping, Classic round trip and full acceptance matrix).
- **artifact_link**: ASUS filtered ADB diagnostics for MakeCredential and GetAssertion at 15:31; user-reported WebAuthn.io registration and authentication success; [fresh-install BLE startup evidence](#record-154-asus-fresh-install-ble-default-and-start-sc-001--t027-support).

### Record 156: ASUS Fresh BLE Credential Classic Assertion Attempt Timed Out (T041 — Failed / Unverified)
- **requirement**: Parent T041; cross-transport acceptance requires assertion of the fresh BLE-created synthetic credential over Classic HID after selecting and starting Classic.
- **scope**: Attempt WebAuthn.io authentication for `codex-synthetic-064-20261103` while ASUS was in Classic HID Advertising mode; determine whether the request reached Chimali.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged/unstaged changes; ASUS APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: With ASUS showing Classic HID Advertising, begin Authenticate for the synthetic WebAuthn.io account, choose the Windows Bluetooth security key, and inspect Windows, browser, Android UI and app logs after the attempt.
- **environment**: Windows 11; ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; WebAuthn.io in the integrated browser; 2026-11-07.
- **expected_observed_result**: The user reported Windows displayed “Activa la clave de seguridad Bluetooth o insértala en el puerto USB,” then the dialog timed out and closed. WebAuthn.io showed its generic timeout/not-allowed result when inspected. ASUS stayed in MainActivity with `Advertising...` and `FIDO2 / WebAuthn over Bluetooth HID`; app logs during the observed window contain no correlated GetAssertion/CTAP request. The user reports Windows Settings lists `Zenfone 10` as paired, not connected.
- **date**: 2026-11-07.
- **limitations**: The WebAuthn timeout, Windows prompt and log interval were not correlated by a shared request ID; the absence of a CTAP log does not locate the Windows/Bluetooth failure stage. No successful Classic assertion was observed for the fresh BLE-created credential. Inspect the Windows pair/connection state and retry only after the host can connect to the HID device. Parent T041 and the full acceptance matrix remain open.
- **status**: Failed (this Classic assertion attempt timed out before a successful browser result) / Unverified (whether a Classic HID connection or CTAP request reached Android, exact credential mapping, and root cause).
- **artifact_link**: ASUS Android layout snapshot and PID-filtered app logs from the attempt; user-reported Windows security-key message and `Zenfone 10` paired/not-connected status; [fresh-install BLE credential evidence](#record-155-asus-fresh-install-ble-registration-and-authentication-t031--sc-001-support).

### Record 157: ASUS Windows Bond Recreated and Classic HID Authentication Succeeded (T035 / T041 Support)
- **requirement**: Parent T035 stale-bond recovery and T041 Classic authentication matrix; verify the ASUS–Windows bond can be recreated and a synthetic-account assertion can complete over Classic HID.
- **scope**: Forget the old ASUS bond for `LOQ15IRX10-3`, have the user remove Windows `Zenfone 10`, pair again with matching numbers, and authenticate the existing synthetic WebAuthn.io account over Classic HID.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged worktree changes; ASUS APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: In ASUS Bluetooth Settings, forget only `LOQ15IRX10-3`; after user confirmation that Windows `Zenfone 10` was removed, launch Chimali in Classic HID mode and pair from Windows Add device → Bluetooth while confirming matching pairing numbers. Authenticate `codex-synthetic-064-20261103` in the integrated WebAuthn.io page using the Bluetooth security key and ASUS verification.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11; host `LOQ15IRX10-3`; integrated WebAuthn.io; 2026-11-07.
- **expected_observed_result**: Android Bluetooth Manager no longer listed `LOQ15IRX10-3` immediately after Forget, then listed it again after the user confirmed matching pairing numbers. Chimali displayed `Connected to PC`, `Stop Authenticator`, and `FIDO2 / WebAuthn over Bluetooth HID`; Android registered the HID_DEVICE profile. WebAuthn.io displayed `You're logged in!` for the synthetic account. App logs show a GetAssertion request with one allow-listed credential, one matching candidate processed after user verification, and accepted HID reports; the assertion completed in 7.851 seconds including 7.626 seconds of user interaction.
- **date**: 2026-11-07.
- **limitations**: This supports stale-bond recovery after system Forget/Pair and successful Classic account authentication. The exact credential ID is masked and was not mapped to the fresh BLE registration; several non-matching allow-list attempts preceded the successful request. No in-app diagnosis/guidance was shown during the earlier stale-bond failure. The full transport-direction, credential/event identity and lifecycle matrix remains open; T035/T041 remain open.
- **status**: Passed (phone and Windows bond recreated with matching-number confirmation; Classic HID account authentication succeeded) / Unverified (exact credential mapping, in-app guided recovery, and full acceptance matrix).
- **artifact_link**: ASUS Bluetooth Settings Forget action and bonded-device snapshots; user-confirmed Windows removal and matching-number pairing; ASUS MainActivity `Connected to PC` layout; Bluetooth Manager HID_DEVICE registration; PID-filtered app logs at 16:18; integrated WebAuthn.io `/profile` success state.

### Record 158: ASUS BLE Assertion Succeeded After Classic HID Authentication (T041 Support)
- **requirement**: Parent T041; verify the reverse transport direction by authenticating the existing synthetic WebAuthn.io account over BLE after the ASUS–Windows Classic HID pair and Classic authentication succeeded.
- **scope**: Save FIDO Bluetooth LE in Chimali Settings, restart the app process, explicitly start the BLE authenticator, and authenticate `codex-synthetic-064-20261103` through the integrated WebAuthn.io page using the already-paired Windows PC.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged worktree changes; ASUS APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: In Settings select FIDO Bluetooth LE; force-stop/relaunch Chimali; verify Ready to Start with the saved BLE choice; explicitly tap Start Authenticator; authenticate the synthetic account via Windows Bluetooth security key and complete ASUS verification.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11; host `LOQ15IRX10-3`; integrated WebAuthn.io; 2026-11-07.
- **expected_observed_result**: After restart, ASUS displayed `Ready to Start` and `FIDO2 / WebAuthn over FIDO Bluetooth LE`; explicit Start displayed `Ready for Authentication` and `Waiting for request from PC`. Android logs show GATT connection, notifications enabled, successful GetInfo, then GetAssertion with one matching candidate, processing and terminal notification acknowledgement (`elapsedMs=4355`). WebAuthn.io displayed `You're logged in!` for the synthetic account.
- **date**: 2026-11-07.
- **limitations**: The credential ID remains masked and was not mapped to the preceding BLE registration or Classic assertion. Several allow-list attempts returned `NO_CREDENTIALS` before one candidate matched; the successful WebAuthn.io result and completed Android terminal notification are evidence of a successful account authentication over BLE. This does not complete the full cross-transport credential/event and settings matrix; T041 remains open.
- **status**: Passed (reverse-direction BLE account authentication after Classic pairing/authentication) / Unverified (exact credential mapping, event comparison and full acceptance matrix).
- **artifact_link**: ASUS Settings and Ready to Start/Ready for Authentication screenshots; ADB BLE logs at 16:27 (GATT, notifications, GetInfo and GetAssertion); integrated WebAuthn.io `/profile` success state.

### Record 159: ASUS BLE User-Cancellation Attempt Ended with Windows Read Error (T035 — Partial)
- **requirement**: Parent T035 interruption/cancellation recovery; verify the authenticator returns to an available BLE state after a user cancels an assertion.
- **scope**: Start WebAuthn.io authentication for `codex-synthetic-064-20261103` over ASUS BLE and have the user tap Cancel on ASUS during the verification prompt.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged worktree changes; ASUS APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: With the ASUS BLE authenticator explicitly started and Windows bonded, initiate WebAuthn.io Authenticate for the synthetic account; select the Bluetooth security key in Windows; tap Cancel on the ASUS user-verification prompt; inspect browser, Android state and app logs.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11; host `LOQ15IRX10-3`; integrated WebAuthn.io; 2026-11-07.
- **expected_observed_result**: The user reports tapping Cancel on ASUS. Windows displayed `No se puede leer la clave de seguridad. Vuelve a intentarlo.` WebAuthn.io remained on its start page without success. Android logs show GATT connection, notifications enabled, GetInfo success, several nonmatching allow-list requests, then a matching GetAssertion candidate; processing failed at 9.035 seconds, the terminal notification was acknowledged, notifications were disabled and the GATT session disconnected. Chimali returned to `Ready for Authentication` / `Waiting for request from PC`; the BLE service and advertising remained active.
- **date**: 2026-11-07.
- **limitations**: The user-reported cancellation and app return state support UI-level recovery, but the Android logs do not show a correlated CTAPHID_CANCEL or prove that the user cancel caused the Windows read error. WebAuthn.io did not show a success result. Protocol-level cancellation and the complete T035 interruption/recovery matrix remain unverified.
- **status**: Partial (attempt reached matching BLE GetAssertion and authenticator returned to Ready with BLE service active) / Failed (WebAuthn authentication) / Unverified (CTAPHID_CANCEL handling and causal attribution).
- **artifact_link**: User report of Windows error and ASUS cancel; Android app-PID logcat around 16:29:45–16:29:58; Android layout showing Ready for Authentication; integrated WebAuthn.io start-page state.

### Record 160: ASUS BLE Authentication Recovered on Immediate Retry (T035 / T041 Support)
- **requirement**: Parent T035 retry recovery after an interrupted/failed ceremony and T041 BLE assertion acceptance; determine whether the existing BLE service accepts a new assertion after the prior failed attempt without app restart or bond reset.
- **scope**: After the BLE cancellation/read-error attempt returned Chimali to Ready for Authentication, retry WebAuthn.io authentication for `codex-synthetic-064-20261103` over the existing BLE session.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged worktree changes; ASUS APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: Close the Windows error dialog; leave Chimali on BLE at Ready for Authentication without force-stop, app restart, or bond reset; start another WebAuthn.io Authenticate attempt and complete Windows Bluetooth security-key selection and ASUS verification.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11; host `LOQ15IRX10-3`; integrated WebAuthn.io; 2026-11-07.
- **expected_observed_result**: Before the retry, Chimali displayed Ready for Authentication and `Waiting for request from PC`; the FIDO foreground service and BLE advertiser/server were active and the Windows bond was present. On retry, GATT connected at 16:34:56, negotiated MTU 517, enabled notifications, and completed GetInfo. Several nonmatching allow-list requests returned `NO_CREDENTIALS`; one matching GetAssertion candidate was selected, completed, and acknowledged with a terminal notification at 16:35:08 (`elapsedMs=10241`). WebAuthn.io displayed `You're logged in!` for the synthetic account. No restart or bond reset occurred between the failed attempt and successful retry.
- **date**: 2026-11-07.
- **limitations**: Credential ID remains masked and not correlated to the prior failed operation; the previous user-reported Cancel is not confirmed by a CTAPHID_CANCEL log. This demonstrates service/session retry recovery and BLE assertion success, not protocol cancellation semantics or completion of the full acceptance matrix.
- **status**: Passed (successful BLE account authentication on a fresh request after the preceding failed attempt without restart/re-pair) / Unverified (exact credential mapping, CTAPHID_CANCEL, full T035/T041 matrix).
- **artifact_link**: ASUS Ready for Authentication state before retry; app-PID Android logs at 16:34:56–16:35:11 showing connection, GetInfo, matched GetAssertion and acknowledged response; WebAuthn.io `/profile` success state.

### Record 161: ASUS Classic HID Force-Stop and Explicit-Start Recovery (T035 Support)
- **requirement**: Parent T035; verify a saved Classic HID transport survives app force-stop/relaunch, remains inactive until explicit Start, and can then reacquire HID_DEVICE and advertise.
- **scope**: While BLE was active, save Bluetooth HID (Classic), force-stop and relaunch Chimali, inspect the stopped selected mode, tap Start Authenticator, and inspect the Classic service state.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged worktree changes; ASUS APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: Select Classic in Settings while BLE is active; force-stop `com.chimali.shrunk`; relaunch MainActivity; verify Classic remains selected and no transport is active; tap Start, approve the previously authorized 120-second visibility request, and inspect MainActivity, HID_DEVICE and foreground service.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11 bonded host `LOQ15IRX10-3`; 2026-11-07.
- **expected_observed_result**: After force-stop/relaunch, MainActivity displayed `Ready to Start`, `Start Authenticator`, and `FIDO2 / WebAuthn over Bluetooth HID`, confirming no automatic startup. After Start and visibility approval, MainActivity displayed `Ready for Authentication` / `Waiting for request from PC`; app logs show HID_DEVICE profile proxy acquisition, `DIAG:REGISTER_SUCCESS` in Advertising state and `BluetoothHidTransport connected and advertising`. `Fido2TransportService` was foreground and Android exposed the HID_DEVICE profile.
- **date**: 2026-11-07.
- **limitations**: This closes the ASUS Classic force-stop/relaunch/explicit-start subcase, not OS process death, in-flight operation/save, unavailable-mode recovery, or the full parent T035 matrix.
- **status**: Passed (Classic selection survived force-stop/relaunch and explicitly restarted into HID advertising) / Unverified (remaining parent lifecycle/recovery procedures).
- **artifact_link**: ASUS pre/post-relaunch screenshots; PID-filtered app log at 16:42:33; Android Bluetooth Manager HID_DEVICE profile; foreground-service state.

### Record 162: ASUS Classic Transport Recovered After Process Death (T035 Support — Partial)
- **requirement**: Parent T035 OS-process-death recovery; verify the Classic transport foreground service can recover after the app process dies and does not fall back to BLE.
- **scope**: With ASUS Classic HID advertising and the Windows bond present, induce an Android VM crash of `com.chimali.shrunk`, inspect process/service/profile state, then reopen MainActivity and verify selected transport and user-visible availability.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged worktree changes; ASUS APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: With Classic selected and explicitly started, use Android ActivityManager `am crash com.chimali.shrunk`; inspect process death and sticky-service restart, then launch MainActivity and inspect active transport, foreground service and Bluetooth profile.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11 bonded host `LOQ15IRX10-3`; 2026-11-07.
- **expected_observed_result**: Android logged `FATAL EXCEPTION: main`, SIGKILL for PID 7333, and `Process com.chimali.shrunk ... has died` at 16:44:48. Android started a new package process (PID 8481); logs show successful HID_DEVICE registration (`DIAG:REGISTER_SUCCESS`, Advertising) at 16:44:51. The foreground FIDO service remained active, Bluetooth Manager exposed HID_DEVICE advertising, and reopening MainActivity showed `Ready for Authentication`, `Waiting for request from PC`, and `FIDO2 / WebAuthn over Bluetooth HID`. No BLE fallback was observed.
- **date**: 2026-11-07.
- **limitations**: This is a deliberate VM crash, not organic low-memory termination. It verifies service recovery and Classic selection/advertising, not an in-flight ceremony, save commit, credential/event integrity, or unavailable-mode recovery; parent T035 remains open.
- **status**: Passed (Classic FIDO service restarted after process death and retained Classic HID without fallback) / Unverified (organic process death and remaining parent T035 cases).
- **artifact_link**: Android ActivityManager/AndroidRuntime log at 16:44:48; app PID 8481 HID_DEVICE registration logs at 16:44:51; foreground-service/Bluetooth Manager snapshots; post-recovery MainActivity screenshot.

### Record 163: ASUS BLE Transport Recovered After Process Death (T035 Support — Partial)
- **requirement**: Parent T035 OS-process-death recovery; verify the BLE foreground service restores its GATT server and advertiser after the app process dies, retaining BLE without Classic fallback.
- **scope**: With BLE selected and advertising, induce Android VM crash of `com.chimali.shrunk`, inspect sticky-service restart/GATT registration/advertising and reopen MainActivity.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged worktree changes; ASUS APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: Confirm BLE is the selected and active mode; use Android ActivityManager `am crash com.chimali.shrunk`; inspect process death and restart, the `Fido2TransportService`, GATT FIDO service and advertiser; reopen MainActivity and inspect status and selected transport.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11 host bond retained; 2026-11-07.
- **expected_observed_result**: Before the induced crash, PID 8950 hosted the foreground FIDO service and BLE GATT server (`0000fffd`). Android logged a main-thread FATAL EXCEPTION and process death at 16:49:58–16:49:59, then restarted the service in PID 9475 at 16:50:00. PID 9475 registered the GATT server and FIDO service successfully at 16:50:01. The foreground service was active, Bluetooth Manager listed `com.chimali.shrunk` advertising and the FIDO service, and MainActivity showed `Ready for Authentication`, `Waiting for request from PC`, and `FIDO2 / WebAuthn over FIDO Bluetooth LE`.
- **date**: 2026-11-07.
- **limitations**: This is an induced VM crash, not organic low-memory termination. It does not test process death during an in-flight CTAP ceremony, durable save/event integrity, physical interruption, stale-bond guidance or unavailable-mode recovery. Parent T035 remains open.
- **status**: Passed (BLE service/GATT advertising restarted after process death and selected BLE mode remained active) / Unverified (organic death and the remaining lifecycle/recovery cases).
- **artifact_link**: AndroidRuntime/ActivityManager log at 16:49:58–16:50:00; PID 9475 GATT registration at 16:50:01; foreground-service and Bluetooth Manager snapshots; post-recovery UI hierarchy `.tmp/asus-ble-post-process-death.xml`.

### Record 164: ASUS BLE Selection Persisted Across Reboot (T035 Support — Partial)
- **requirement**: Parent T035 reboot and explicit-start procedure; verify persisted mode after Android reboot and that the transport does not auto-start.
- **scope**: Reboot ASUS while BLE is the selected and active mode; after boot/unlock inspect saved choice and service state, then explicitly start the authenticator and verify availability.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged worktree changes; installed ASUS shrunkDebug APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: Reboot ASUS with FIDO Bluetooth LE selected and advertising; wait for Android boot completion, unlock once, open MainActivity, inspect mode/status and FIDO service/advertiser before starting; tap Start Authenticator and inspect UI, service and Bluetooth Manager.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11 bond retained; 2026-11-07.
- **expected_observed_result**: After reboot, Android booted to credential-locked state and Chimali had no process/service/advertiser. After the user unlocked the phone and MainActivity was opened, Chimali displayed `Ready to Start`, `Tap 'Start Authenticator' to begin`, and `FIDO2 / WebAuthn over FIDO Bluetooth LE`; therefore mode persisted and did not auto-start. After explicit Start, UI showed `Ready for Authentication` and `Waiting for request from PC`; `Fido2TransportService` was foreground in PID 13968, Android registered the FIDO GATT service `0000fffd`, and Bluetooth Manager listed Chimali advertising.
- **date**: 2026-11-07.
- **limitations**: This verifies selected-mode persistence and explicit-start behavior across one reboot. The locked-before-first-unlock state prevented app inspection until user unlock. It does not verify a ceremony interrupted by reboot, credential/event byte-for-byte integrity or the Classic reboot direction; parent T035 remains open.
- **status**: Passed (BLE selected mode persisted and remained stopped until explicit Start, then BLE service/advertiser started) / Unverified (in-flight reboot, Classic reboot and remaining T035 acceptance cases).
- **artifact_link**: `sys.boot_completed`, locked/unlocked Android user state, package manager/service snapshots, `.tmp/asus-ble-post-reboot.xml`, and `.tmp/asus-ble-post-reboot-start.xml`.

### Record 165: Current-Tree Local CI and FIDO2 Host Regression (T039 — Partial)
- **requirement**: Parent T039 current-tree static analysis, build and host regression checks; report connected coverage and accepted-target comparison independently.
- **scope**: Re-run FIDO2 Android host tests and the repository's non-device local CI on the current staged/unstaged worktree, including the credential-projection/schema migration changes.
- **revision**: Branch `064-bluetooth-transport-selection`; HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes. Gradle user home was redirected to the worktree `.tmp/gradle-home` after the configured shared cache denied lock-file access.
- **named_procedure**: Run `.\gradlew.bat :feature:fido2:testAndroidHostTest`; then run `.\tools/local-ci.ps1 -NoFix -NoConfigurationCache` with `GRADLE_USER_HOME` set to `.tmp/gradle-home`.
- **environment**: Windows 11 / PowerShell / Gradle 9.6.0; ASUS AI2302 / Android 15 API35 attached and unlocked; 2026-11-07. Local CI's `-RunCoverage` option was not selected.
- **expected_observed_result**: Direct FIDO2 host test task succeeded in 44 seconds. Current host XML reports aggregate 1,148 tests, 0 failures, 0 errors and 0 skipped, including the credential database migration test (1/1). Repository local CI succeeded in 177.96 seconds: KtLint, Detekt, Release lint, compile-all, unit tests and configured common/security/domain/FIDO2 Android host tests all passed.
- **date**: 2026-11-07.
- **limitations**: Gradle reported most tasks up-to-date. This command is non-device local validation only; connected coverage and accepted-target raw-count nonregression were not run here. A separate filtered current-tree capture passed configured connected tests and local floors (parent Record 153; BLE Record 168), but that candidate was dirty and the accepted-target index was stale. The trusted exact-target comparison still needs a committed clean candidate and matching accepted-target evidence/index. Parent T039 remains open.
- **status**: Passed (current-tree local CI and FIDO2 host regression) / Unverified (connected device checks, coverage floors and trusted candidate-versus-target comparison).
- **artifact_link**: Gradle output for both commands; `feature/fido2/build/test-results/testAndroidHostTest/` current XML reports; worktree-local Gradle cache.

### Record 166: ASUS Classic Selection Persisted Across Reboot (T035 Support — Partial)
- **requirement**: Parent T035 verify the saved Classic mode persists across Android reboot and does not auto-start.
- **scope**: Save Classic while BLE was active, force-stop/relaunch to apply it, reboot ASUS, then inspect selected mode and service state after the required first unlock.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; installed ASUS shrunkDebug APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: Save Bluetooth HID (Classic) in Settings while BLE is active; force-stop/relaunch to apply Classic and confirm Ready to Start; reboot; after Android boot/unlock reopen MainActivity and inspect selected transport/status plus FIDO service state.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows host bond retained; 2026-11-07.
- **expected_observed_result**: After force-stop/relaunch and again after reboot/unlock, MainActivity displayed `Ready to Start`, `Tap 'Start Authenticator' to begin`, and `FIDO2 / WebAuthn over Bluetooth HID`. After reboot the Activity process existed (PID 7217) but no `Fido2TransportService` was registered, so Classic remained selected but inactive. BLE was restored afterward via Settings, force-stop/relaunch and explicit Start; it now advertises.
- **date**: 2026-11-07.
- **limitations**: This verifies Classic selection persistence and no automatic startup; Classic HID reactivation immediately after reboot, in-flight reboot, credentials/events and remaining matrix cases were not exercised by this reboot check. Parent T035 remains open.
- **status**: Passed (Classic remained selected and stopped across reboot) / Unverified (post-reboot Classic activation, in-flight operations and remaining T035 cases).
- **artifact_link**: `.tmp/asus-classic-pre-reboot.xml`; post-reboot `.tmp/asus-classic-post-reboot.xml`; Android user/package/service state snapshots.

### Record 167: ASUS BLE Authentication Succeeded After Reboot (T041 Support)
- **requirement**: Parent T041 BLE assertion after reboot and T035 restoration of a usable authenticator; verify WebAuthn succeeds over the persisted BLE mode after its explicit restart.
- **scope**: After BLE survived reboot and was explicitly started, perform a synthetic WebAuthn.io authentication using the already paired ASUS/Windows setup.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; installed ASUS shrunkDebug APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: With BLE selected and advertising after reboot, click Authenticate for `codex-synthetic-064-20261103` in the integrated WebAuthn.io page, choose `Cambiar → Llave de seguridad` in Windows, and complete ASUS verification.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11 host `LOQ15IRX10-3`; integrated WebAuthn.io; 2026-11-07.
- **expected_observed_result**: WebAuthn.io displayed `You're logged in!`. Android PID 13881 logged GATT connection at 11:12:01, MTU 517, notifications enabled and GetInfo success. The app recorded three nonmatching allow-list GetAssertion requests; a matching single-candidate request completed and acknowledged its terminal notification (`elapsedMs=249`), followed by a user-verified assertion which completed and acknowledged its terminal notification (`elapsedMs=18976`). Chimali remained in BLE mode and returned to a ready state.
- **date**: 2026-11-07.
- **limitations**: The browser account success and Android traces are time-correlated to this attempt, but credential IDs are masked, and the precise relationship between the two successful CTAP assertion responses and the browser ceremony is not fully correlated. This is post-reboot BLE account-level authentication, not exact credential/event identity or full SC-001–006 acceptance. T041 remains open.
- **status**: Passed (WebAuthn account authentication after reboot with successful BLE GATT/CTAP response evidence) / Unverified (exact credential mapping, response-to-browser correlation and complete acceptance matrix).
- **artifact_link**: ASUS PID-filtered logcat 11:12:01–11:12:26; integrated WebAuthn.io `/profile` success state and captured browser screenshot.

### Record 168: ASUS BLE Authentication Succeeded After Phone-Side Bond Recreation (T035/T041 Support)
- **requirement**: Parent T035 stale-bond retry and T041 BLE assertion after a system bond recreation.
- **scope**: Forget only the ASUS-side Windows bond, restore it per user report, then run a fresh synthetic WebAuthn authentication over the parent-gated BLE transport.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; ASUS/Mi shrunkDebug APK SHA-256 `87D47784A7BE23BE0539B0A39603AF7FF104B699CD72F92A0D462FE3E5BFDE1B`.
- **named_procedure**: Forget the phone-side `LOQ15IRX10-3` bond while Windows retains its pair; restore pairing; click Authenticate manually in integrated WebAuthn.io; select `Cambiar → Llave de seguridad` and complete ASUS verification.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11 host `LOQ15IRX10-3`; integrated WebAuthn.io; 2026-11-07.
- **expected_observed_result**: After the user reported repairing the bond, WebAuthn.io displayed `You're logged in!` on a fresh user-initiated attempt. Android logs show GATT MTU 517, GetInfo, unmatched allow-list requests and acknowledged GetAssertion responses; Chimali returned to Ready for Authentication.
- **date**: 2026-11-07.
- **limitations**: The user confirmed that matching numbers appeared on both devices, but this was not independently captured. The user opened Android Settings directly rather than using the app's Pairing help; the authentication demonstrates BLE success after bond recreation but not the guided stale-bond path, exact credential mapping, event comparison or full SC-001–006 matrix. Parent T035/T041 remain open.
- **status**: Passed (synthetic account BLE authentication after phone-side bond recreation; matching numbers confirmed by user) / Unverified (in-app stale-bond guidance, exact credential/event comparison and complete acceptance matrix).
- **artifact_link**: ASUS app-PID logcat at 17:35–17:45; Bluetooth Manager bonded-device snapshot; integrated WebAuthn.io `/profile` success state; BLE Record 180.

### Record 169: ASUS Ready-State Pairing Help Availability (T035 Support)
- **requirement**: Parent T035 recovery discoverability; expose pairing guidance when the selected BLE transport is ready and advertising.
- **scope**: Verify the focused dashboard state behavior, style checks, device installation and ASUS Ready-for-Authentication screen after Pairing help became available beyond Error.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; installed ASUS/Mi `shrunkDebug` APK SHA-256 `B2871B0FEC662193614EA5ABAB282AE868FA0DEE291E1753C6000259063497B3`.
- **named_procedure**: Run focused `AuthenticatorDashboardComponentsTest` on ASUS and Android main/device-test KtLint; install `shrunkDebug` on ASUS and Mi; explicitly start the ASUS authenticator and inspect its ready screen/service.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Mi A2 / Android 10 API29 / serial `2ac6876`; 2026-11-07.
- **expected_observed_result**: Focused dashboard tests passed 9/9 and both KtLint tasks passed. Gradle reported successful installation on both devices. ASUS retained the prior install timestamp and host row; after explicit Start, the screen showed Ready for Authentication / Waiting for request from PC with Pairing help visible, while the foreground transport service and BLE advertising were active.
- **date**: 2026-11-07.
- **limitations**: Actual settings navigation and retry after radio restoration were exercised in BLE Record 179. This update confirms discoverability in the ready state but does not exercise the guided stale-bond recovery; user confirmed the recent matching-number re-pair and successful auth but opened Android Settings directly. Full T035 and T041 acceptance remain open.
- **status**: Passed (ready-state discoverability, focused UI tests, lint and device deployment) / Unverified (guided stale-bond interaction and remaining T035/T041 cases).
- **artifact_link**: BLE Record 181; focused ASUS dashboard-test XML; Gradle install output; ASUS UI hierarchy and service/advertiser snapshots.

### Record 170: Reported ASUS Authentication Retry Without Correlated CTAP Evidence (T035/T041 — Partial)
- **requirement**: Parent T035 retry recovery and T041 BLE authentication; record the user's result and the limits of current device evidence.
- **scope**: Inspect the result of a user-directed fresh WebAuthn retry after the new Pairing help build was installed.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; ASUS/Mi `shrunkDebug` APK SHA-256 `B2871B0FEC662193614EA5ABAB282AE868FA0DEE291E1753C6000259063497B3`.
- **named_procedure**: User cancels the stale Windows prompt, retries authentication for the synthetic WebAuthn account, and reports the outcome; inspect browser page and ASUS state/logs.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11; integrated WebAuthn.io; synthetic account `codex-synthetic-064-20261103`; 2026-11-07.
- **expected_observed_result**: The user reported fresh login success; the integrated browser displayed `You're logged in!`. ASUS remained in BLE Ready-for-Authentication state with the BLE service/advertiser active. The inspected log buffer contained no matching post-install CTAP/GetAssertion exchange, and the profile had displayed success before the reported retry.
- **date**: 2026-11-07.
- **limitations**: The latest result is user-reported and not distinguishable from the previous browser success using the persistent profile state; no device-side trace correlates it to a fresh BLE operation. Do not count it as independent ceremony verification or protocol-cancellation evidence. T035/T041 remain open.
- **status**: Partial (user-reported login success and browser success state) / Unverified (fresh attempt correlation, device-side CTAP evidence and recovery matrix).
- **artifact_link**: User reply; integrated WebAuthn.io profile snapshot; ASUS UI and post-install log inspection; BLE Record 182.

### Record 171: ASUS/Mi Pairing Guidance Clarified and Deployed (T035 Support)
- **requirement**: Parent T035 recovery help; make two-sided bond repair actionable when Windows labels the phone by its model.
- **scope**: Add explicit phone/PC bond-removal guidance, label the phone-only Bluetooth Settings action, verify the dialog on physical devices and deploy the revised build.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; ASUS/Mi `shrunkDebug` APK SHA-256 `841B3775DB7DDD7E12FAC977771C875D718C706D34803C2D14E312503BFA81F5`.
- **named_procedure**: Run `AuthenticatorDashboardComponentsTest` on connected ASUS and Mi; run Android main/device-test KtLint; install on both phones; inspect the Pairing help dialog and verify BLE returns to Ready after dismissal.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Mi A2 / Android 10 API29 / serial `2ac6876`; Windows 11; 2026-11-07.
- **expected_observed_result**: Tests passed 9/9 on each phone, both KtLint tasks passed, and Gradle reported successful installation to both devices. ASUS first-install timestamp and paired-host row remained; BLE selection persisted but the service stopped for the in-place upgrade until explicit Start. The updated screen showed Ready for Authentication with Pairing help visible. The dialog accurately described removing the saved PC pair from the phone and the saved phone pair from the PC, noted the Windows model-name label, and stated that passkeys remain on the phone. Closing it returned to the ready screen with the BLE foreground service running.
- **date**: 2026-11-07.
- **limitations**: UI guidance is verified; actual stale-bond repair through the dialog is not. User reported fresh WebAuthn success without correlated post-install CTAP evidence (parent Record 170; BLE Record 182). Parent T035/T041 remain open.
- **status**: Passed (focused tests on both devices, lint, deployment and on-device help content) / Unverified (guided stale-bond interaction, remaining T035 recovery and full T041 acceptance).
- **artifact_link**: BLE Record 183; ASUS and Mi connected-test XML; install output; ASUS dialog and ready-state hierarchy.

### Record 172: Parent Ceremony and Process-Selection Host Tests Passed (T035 Support)
- **requirement**: Parent T035 requires execution of deterministic in-flight selection and process-restoration tests from T032–T033 before the remaining lifecycle procedures.
- **scope**: Inspect current FIDO2 Android host-test XML for the dedicated transport-selection ceremony and process-selection suites.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes.
- **named_procedure**: Review `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.transport.TransportSelectionCeremonyTest.xml` and `TEST-com.chimali.fido2.data.transport.ProcessTransportSelectionTest.xml` from the latest current-tree host suite.
- **environment**: Windows 11 / Gradle 9.6.0; 2026-11-07.
- **expected_observed_result**: `TransportSelectionCeremonyTest` passed 2/2, and `ProcessTransportSelectionTest` passed 16/16; each reported zero failures, errors and skips.
- **date**: 2026-11-07.
- **limitations**: These are deterministic host tests using synthetic operation gates. They do not substitute for physical mid-ceremony save, both-mode force-stop/process-death/reboot, or unavailable-mode recovery. Parent T035 remains open for those physical procedures.
- **status**: Passed (T032/T033 dedicated host suites) / Unverified (physical lifecycle procedures still required by T035).
- **artifact_link**: The two XML reports under `feature/fido2/build/test-results/testAndroidHostTest/`; broader current-tree host evidence is parent Record 165 / BLE Record 177.

### Record 173: App-Guided Phone-Side Bond Removal and BLE Retry (T035 — Partial)
- **requirement**: Parent T035 stale-bond guidance; launch Android Bluetooth settings from Chimali, remove the stale phone bond, and verify transport recovery.
- **scope**: Exercise the revised Pairing help path on ASUS through phone-side Forget and Retry while Windows retains its saved phone entry.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; ASUS/Mi `shrunkDebug` APK SHA-256 `841B3775DB7DDD7E12FAC977771C875D718C706D34803C2D14E312503BFA81F5`.
- **named_procedure**: Open Pairing help → Open phone Bluetooth settings; navigate to `LOQ15IRX10-3` device details, Forget and confirm; return and Retry; inspect the bonded-device list, app state, service and advertiser.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11 host `LOQ15IRX10-3`; 2026-11-07.
- **expected_observed_result**: Android's Forget confirmation named `LOQ15IRX10-3`; afterward the Windows host was absent from Android's Bonded devices list. Tapping Retry restored Ready for Authentication / Waiting for request from PC and restarted BLE advertising with the FIDO foreground service present. The user then removed the Windows `Zenfone 10` pair and re-paired, reporting matching numbers. ASUS Android Bluetooth logs recorded BONDING at 18:27:15 and BONDED at 18:27:29 for `LOQ15IRX10-3`. Following the guided recovery, the user reported a fresh WebAuthn login success; ASUS logs at 18:33:58–18:34:07 show BLE GetInfo/GetAssertion, one admitted allow-list credential selected for assertion, successful Android user verification (`addAuthToken`), terminal notification acknowledgements and GATT disconnect. The integrated WebAuthn.io profile simultaneously showed its logged-in success state for codex-synthetic-064-20261103 and seven masked credential entries, including multiple BLE/USB transport lists; the profile does not identify the exact asserted credential.
- **date**: 2026-11-07.
- **limitations**: Matching-number confirmation is user-reported; Android logs confirm the bonded state. The correlated ceremony confirms account-level BLE assertion after guided recovery, but exact credential identity and full SC-001–006 event/count semantics remain unverified. Other physical T035 lifecycle cases remain open.
- **status**: Passed (guided phone-side Forget, BLE Retry, system-confirmed re-pair, and fresh BLE assertion) / Partial (remaining parent lifecycle procedures and full T041 acceptance matrix).
- **artifact_link**: BLE Record 184; Android device detail/Forget confirmation, bonded-device and post-Retry service/advertiser snapshots; ASUS logcat 18:33:58–18:34:07 and integrated WebAuthn.io logged-in profile.

### Record 174: ASUS BLE User Cancellation Returned to Ready (T035 Support)
- **requirement**: Parent T035 physical cancellation recovery; verify a user-canceled Android verification prompt during BLE GetAssertion returns the authenticator to a usable state.
- **scope**: Start a fresh synthetic WebAuthn authentication, select the Windows Bluetooth security key, cancel on ASUS and inspect the app/link recovery.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; ASUS `shrunkDebug` APK SHA-256 `841B3775DB7DDD7E12FAC977771C875D718C706D34803C2D14E312503BFA81F5`.
- **named_procedure**: User starts Authenticate for `codex-synthetic-064-20261103`, chooses `Cambiar → Llave de seguridad` in Windows, taps Cancel on ASUS, closes the resulting Windows error and reports the app state.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11 host `LOQ15IRX10-3`; integrated WebAuthn.io; 2026-11-07.
- **expected_observed_result**: Android logs at 18:42:05–18:42:11 show BLE GetInfo and admitted GetAssertion operations; one request entered processing with a selected credential candidate and its terminal notification was acknowledged after 4,808 ms. The user reports tapping Cancel on ASUS; Chimali returned to Ready for Authentication and Windows displayed a security-key read error. The GATT link disconnected at 18:42:14; the phone bond remained.
- **date**: 2026-11-07.
- **limitations**: No CTAPHID_CANCEL receipt or CTAP response status was logged, so protocol-level host cancellation and the precise CTAP error remain unverified. This is physical Android prompt-cancel recovery only; other parent T035 lifecycle procedures and the full T041 matrix remain open.
- **status**: Passed (phone-side cancellation recovery and preserved bond) / Unverified (host CANCEL protocol result and remaining T035/T041 criteria).
- **artifact_link**: BLE Record 185; ASUS logcat 18:42:05–18:42:14; Bluetooth Manager bond and app foreground state; user's Windows/ASUS report.

### Record 175: Parent Local CI and Host Validation Without Deferred Coverage (T039 — Partial)
- **requirement**: Parent T039 local static analysis, compilation and configured host/device verification; preserve the explicitly deferred accepted-target coverage gate as unverified.
- **scope**: Run the repository local CI pipeline against the current candidate without `-RunCoverage`; inspect current JUnit XML outcomes and retain the exact scope/limitations.
- **revision**: Branch `064-bluetooth-transport-selection`; HEAD `39acc7f8` plus staged and unstaged changes; test APK SHA-256 `841B3775DB7DDD7E12FAC977771C875D718C706D34803C2D14E312503BFA81F5`.
- **named_procedure**: `$env:GRADLE_USER_HOME=.tmp/gradle-home; .\tools\local-ci.ps1 -NoFix -NoConfigurationCache`.
- **environment**: Windows 11; Gradle wrapper 9.6.0; OpenJDK 25.0.5; no connected-device suite invoked by this local CI command; 2026-11-07.
- **expected_observed_result**: KtLint passed (16.8 s), Detekt passed (22.76 s), Release Lint passed (54.52 s), comprehensive configured compilation passed (16.17 s), and the complete local pipeline exited successfully in 177.61 s. Recent JUnit reports from app and FIDO2 show 1,153 tests, zero failures, zero errors and zero skips; FIDO2 host reports account for 1,148. Connected dashboard tests remain separately recorded as 9/9 per ASUS and Mi in BLE Record 183 / parent Record 171.
- **date**: 2026-11-07.
- **limitations**: `-RunCoverage` and `tools/integration-ci.ps1` were deliberately not run under the owner's coverage-gate deferral. The accepted-target index/bundle is stale or unavailable and the candidate worktree is dirty; no raw-count nonregression result is claimed. This run does not execute connected-device suites; device results are separate records. Parent T039 therefore remains partial/unverified for coverage, trusted target comparison and hosted enforcement.
- **status**: Passed (local static checks, compilation and current configured host tests) / Unverified (accepted-target comparison, coverage gate and hosted enforcement).
- **artifact_link**: Gradle output from `tools/local-ci.ps1`; current app/FIDO2 JUnit XML under their `build/test-results` directories; BLE Record 183 / parent Record 171 for connected UI tests.

### Record 176: ASUS BLE Radio-Off Interruption Exposed Delayed Cleanup (T035 — Partial)
- **requirement**: Parent T035 physical unavailable-transport recovery; terminate an active operation when Bluetooth is disabled and verify the transport can be retried.
- **scope**: Before the recovery fix, disable Bluetooth during an active synthetic WebAuthn BLE GetAssertion, restore Bluetooth, and inspect the Android request lifecycle, browser result, app and bond state.
- **revision**: Branch `064-bluetooth-transport-selection`; pre-fix candidate source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; subsequent uncommitted recovery fix installed as APK SHA-256 `7F62FE8250D0051AA408C6D23E7564AAA16960696A09B3C32B61065BAA0A56C4`.
- **named_procedure**: Begin a synthetic-account Authenticate on the BLE transport; toggle the ASUS Bluetooth adapter off during admitted GetAssertion; restore Bluetooth and inspect logcat, Bluetooth Manager, WebAuthn.io and Chimali.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11 host `LOQ15IRX10-3`; integrated WebAuthn.io; BLE GATT; 2026-11-07.
- **expected_observed_result**: ASUS logs showed GATT/GetInfo/GetAssertion admitted around 18:53:15–18:53:18. Bluetooth-off at about 18:53:37 caused Android to clear/close the GATT server. GetAssertion ended about 70,670 ms later with `terminalNotificationAcknowledged=false` and `terminal_response_not_queued`; GATT disconnected around 18:54:35. WebAuthn.io timed out. Bluetooth was subsequently enabled, the ASUS–Windows bond remained, and the user reported Chimali returned to Ready for Authentication.
- **date**: 2026-11-07.
- **limitations**: This record retains the pre-fix failure. The updated build has since surfaced a retryable Error, restored GATT advertising after explicit Retry, and completed a fresh BLE authentication afterward (parent Record 177; BLE Record 187). The remaining parent T035/T041 procedures remain open; coverage remains owner-deferred.
- **status**: Partial (pre-fix failure reproduced; post-fix interruption, explicit Retry and successful follow-up authentication recorded) / Unverified (remaining parent T035/T041 procedures).
- **artifact_link**: ASUS logcat around 18:53:15–18:54:35; WebAuthn.io timeout; installed APK hash above; BLE Record 187 for focused regression and redeployment.

### Record 177: Updated ASUS Build Surfaces Retryable State on Bluetooth Loss (T035 — Partial)
- **requirement**: Parent T035 unavailable-mode recovery; detect Bluetooth loss during an active Windows BLE security-key attempt and retain an explicit recovery path.
- **scope**: On the updated ASUS build, begin a synthetic-account WebAuthn authentication, disable Bluetooth while Windows is waiting for the phone, restore Bluetooth, and inspect Chimali state and service availability.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; installed APK SHA-256 `7F62FE8250D0051AA408C6D23E7564AAA16960696A09B3C32B61065BAA0A56C4`.
- **named_procedure**: Start BLE on ASUS; initiate the synthetic WebAuthn authentication; run `adb shell svc bluetooth disable`, inspect the callback/UI, restore with `adb shell svc bluetooth enable`, and inspect enabled/service state.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11 host `LOQ15IRX10-3`; integrated WebAuthn.io; BLE GATT; 2026-11-07.
- **expected_observed_result**: ASUS logs showed repeated GetAssertion traffic while Windows' security-key dialog was open, including a final invalid-state error at 19:19:57. The adapter-loss handler logged `Bluetooth became unavailable during an active BLE session` at 19:20:02. The Android hierarchy showed `Error Occurred` and `Bluetooth is off; restore Bluetooth and retry`. Bluetooth was restored to `enabled: true`, and `Fido2TransportService` remained foreground with one BLE app registered. WebAuthn.io then reported that the interrupted operation timed out. The user tapped Retry Authenticator; at 19:28:47 Android re-registered FIDO service `0xFFFD` and Device Information service `0x180A`, started BLE advertising, and Chimali showed `Ready for Authentication / Waiting for request from PC`. A subsequent authentication completed with acknowledged GetAssertion at 19:31:16, and WebAuthn.io displayed `You're logged in!`.
- **date**: 2026-11-07.
- **limitations**: The modal Windows dialog prevented the user from returning to the browser during the interruption. The interrupted WebAuthn operation timed out as expected; a fresh post-retry BLE ceremony then succeeded. The broader parent T035/T041 matrix remains open; coverage is owner-deferred.
- **status**: Passed (radio-loss detection, retryable error, Bluetooth restoration, explicit Retry restoring BLE advertising, and successful post-recovery authentication) / Partial (remaining parent T035/T041 procedures).
- **artifact_link**: ASUS logcat around 19:19:57–19:20:02, 19:28:47 and 19:31:16; `uiautomator` hierarchies showing retryable error and subsequent Ready state; Bluetooth Manager state after re-enable; WebAuthn.io timeout followed by `You're logged in!`; BLE Record 187.

### Record 178: Final Candidate Non-Coverage Local CI Passed (T039 — Partial)
- **requirement**: Parent T039 current-candidate static analysis, compilation, and configured host verification; preserve owner-deferred accepted-target comparison as unverified.
- **scope**: Run the repository's ordinary local CI after the adapter-loss cleanup change; exclude the coverage and accepted-target comparison workload as directed by the owner.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; deployed candidate APK SHA-256 `7F62FE8250D0051AA408C6D23E7564AAA16960696A09B3C32B61065BAA0A56C4`.
- **named_procedure**: `$env:GRADLE_USER_HOME=.tmp/gradle-home; .\tools\local-ci.ps1 -NoFix -NoConfigurationCache`.
- **environment**: Windows 11; Gradle 9.6.0; OpenJDK 25.0.5; ASUS/API35 and Mi/API29 connected but not used as part of this CI execution; 2026-11-07.
- **expected_observed_result**: Repository KtLint passed in 12.39 seconds, Detekt passed in 22.05 seconds, release lint passed in 51.65 seconds, compile-all passed in 16.39 seconds, unit tests passed in 21.47 seconds, configured KMP Android host tests passed in 10.5 seconds; the script completed successfully in 134.49 seconds. The separate full FIDO2 Android host suite passed 1,150/1,150 in BLE Record 187.
- **date**: 2026-11-07.
- **limitations**: Coverage floors and trusted accepted-target raw-count nonregression were not run under the owner's deferral. The evidence index/target remains stale or unavailable and the worktree is dirty. Connected physical acceptance is separate; parent T035/T041 remain open. Parent T039 remains partial/unverified for coverage, target comparison and hosted enforcement.
- **status**: Passed (non-coverage local CI) / Unverified (accepted-target comparison and coverage gate).
- **artifact_link**: Gradle output from `tools/local-ci.ps1`; FIDO2 host JUnit XML; BLE Record 187 for candidate behavior and installation.

### Record 179: Current ASUS BLE-to-Classic Selection Preserved Local Credential Store (T041 Support — Partial)
- **requirement**: Parent SC-002 / SC-006 and T041; confirm the saved Classic choice becomes active after one process restart while the current synthetic BLE credential and committed event database remain unchanged.
- **scope**: On the current ASUS build, save Classic while BLE is active; inspect pending/active UI; force-stop and relaunch; explicitly start Classic; verify the local synthetic account remains listed and compare encrypted credential/event database bytes before and after the switch.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; installed `shrunkDebug` APK SHA-256 `7F62FE8250D0051AA408C6D23E7564AAA16960696A09B3C32B61065BAA0A56C4`.
- **named_procedure**: From active BLE, hash only `databases/fido2.db` via `run-as`; save `Bluetooth HID (Classic)` in transport settings and inspect pending/active status; compare the DB hash; force-stop and explicitly launch `MainActivity`; tap Start and approve the 120-second Android visibility prompt; verify HID_DEVICE and the active transport; inspect Passkeys; hash the database again. Do not decrypt or expose database contents.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11 host `LOQ15IRX10-3`; package `com.chimali.shrunk`; 2026-11-07.
- **expected_observed_result**: Before selection the app showed Ready for Authentication over FIDO Bluetooth LE and the passkey manager listed `codex-synthetic-064-20261103`. The encrypted database hash was `cf5f09c46cdaf654b906b932172abda3f43687f47a17d8e7339cd3aefafbe429`. Saving Classic showed “A restart is required to use the saved choice” while the process remained active on BLE; the database hash was unchanged. After force-stop and launch, the app showed Ready to Start with `FIDO2 / WebAuthn over Bluetooth HID`. After Start and the 120-second visibility approval, it showed Ready for Authentication over Bluetooth HID and Android listed the app under HID_DEVICE. The synthetic passkey row remained visible, and the encrypted database hash was unchanged at the same value. The user then completed Authenticate for the same synthetic account using the Windows Bluetooth security key. Android logs show HID CTAPHID_INIT/GetInfo, several allow-list attempts returning `NO_CREDENTIALS`, followed by a successful user-verified GetAssertion (`addAuthToken`), `Assertion response ready`, and `HID reports accepted: true`; the completed assertion took 3,425 ms including 3,191 ms of user interaction. WebAuthn.io displayed “You're logged in!” for `codex-synthetic-064-20261103`. The app showed Connected to PC and updated Last used to 19:50. After the assertion, encrypted `fido2.db` hashed `f6c67e2381e1d5808c6be2bc139c90edcd9104c3c7908fc95ece3c6809e5f0b7`.
- **date**: 2026-11-07.
- **limitations**: Byte identity proves the encrypted database was unchanged across preference save, process restart and Classic activation; it does not disclose individual event rows or exact credential IDs. The phone held one local synthetic credential at the time; the WebAuthn.io profile has multiple historical masked entries, so the server-side credential ID cannot be mapped exactly, although HID GetAssertion, user verification, Android response and browser login are time-correlated. The reverse switch and registration on Classic followed by authentication on BLE remain required for full T041; parent T035 still lacks physical mid-ceremony selection evidence.
- **status**: Passed (BLE→Classic saved/active semantics, unchanged local credential/event database across switch, retained passkey, and account-level Classic HID assertion) / Partial (exact credential/event mapping and remaining T041/T035 criteria).
- **artifact_link**: ASUS UIAutomator hierarchies before and after selection/relaunch/Start and on the Passkeys screen; `run-as sha256sum` output before/after switching and after assertion; Android Bluetooth Manager HID_DEVICE snapshot; ASUS logcat 19:50:24–19:50:30; integrated WebAuthn.io logged-in profile for the synthetic account.

### Record 180: Current ASUS Classic Registration Authenticated After BLE Switch (T041 Support — Partial)
- **requirement**: Parent SC-002 / SC-003 / SC-006 and T041; verify current-build Classic registration followed by saved-mode restart, BLE activation and a successful BLE authentication on the same synthetic WebAuthn account.
- **scope**: From Classic HID active, register the synthetic account; verify the local passkey entry; save BLE while Classic remains active; confirm pending-choice semantics and database preservation; restart and explicitly start BLE; authenticate the account over BLE.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; installed `shrunkDebug` APK SHA-256 `7F62FE8250D0051AA408C6D23E7564AAA16960696A09B3C32B61065BAA0A56C4`.
- **named_procedure**: With Classic active and the current BLE credential present, begin WebAuthn.io Register for `codex-synthetic-064-20261103`; choose the Windows Bluetooth security key and complete ASUS verification. Verify the local passkey manager. Save FIDO Bluetooth LE; confirm pending while Classic remains active and hash `databases/fido2.db`; force-stop/relaunch, explicitly Start BLE, verify GATT registration; begin WebAuthn.io Authenticate and complete the Windows Bluetooth-key flow.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11 host `LOQ15IRX10-3`; integrated WebAuthn.io; BLE GATT and Classic HID; 2026-11-07.
- **expected_observed_result**: On Classic, Android admitted MakeCredential at 19:56:31.893, reported successful user verification at 19:56:35.618, and completed an encoded MakeCredential response with accepted HID reports at 19:56:36.074. WebAuthn.io reported registration success. The local passkey manager displayed two `webauthn.io` rows for the synthetic account. Saving BLE showed “A restart is required to use the saved choice” while Classic stayed the process transport; encrypted database hash `3659b140192b8e18e19ea402febeea60baa09eee7ad5db9d20009de9f665f327` did not change across save, force-stop and relaunch. The app showed BLE selected and Ready to Start until explicit Start; it then showed Ready for Authentication and Android registered the FIDO GATT service. On the subsequent user-initiated authentication, the host connected over BLE, Android completed GetInfo and an admitted GetAssertion with one candidate; after nonmatching allow-list attempts, it executed the headless fast path, produced `Assertion response ready`, and logged `terminalNotificationAcknowledged=true` at 19:59:04.493. WebAuthn.io displayed “You're logged in!” for the synthetic account. The app returned to Ready for Authentication, and the encrypted database hash after authentication was `73145a46a6cde5f911c714d1fa6148322c048464d96759d4c90a8088448a37b0`.
- **date**: 2026-11-07.
- **limitations**: The remote account page masks credential IDs and lists historical records; the app has two local synthetic credentials. Android logs and the browser success are time-correlated, but no exact remote ID-to-local credential mapping was captured, so the assertion cannot be attributed conclusively to the newly registered Classic credential. The database hash proves it was unchanged across the preference save/restart; the post-assertion hash differs, but encrypted row/event contents were not read. The full exact credential/event comparison and physical mid-ceremony save remain incomplete; T041/T035 stay open.
- **status**: Passed (current-build Classic MakeCredential; pending Classic→BLE preference semantics; explicit BLE activation; account-level BLE WebAuthn authentication with acknowledged GetAssertion) / Partial (exact credential identity/event comparison and remaining T041/T035 criteria).
- **artifact_link**: ASUS logcat at 19:56:31–19:56:36 and 19:59:03–19:59:04; UIAutomator hierarchies for Classic/BLE state and two passkey rows; encrypted database hashes; WebAuthn.io registration success and logged-in profile.

### Record 181: SC-001–SC-006 Acceptance Matrix Recorded (T041 — Partial Results)
- **requirement**: Parent T041; run and document each SC-001–SC-006 row with TRD §9.1 evidence fields, the synthetic account, physical host/device identity, outcomes and limitations.
- **scope**: Consolidate the clean-install BLE ceremony, both physical transport-switch directions, current-build Classic registration and authentication, lifecycle/reboot evidence, in-flight host tests and physical settings semantics. Report each success or gap separately; do not infer exact credential identity from an account-level browser result.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; current ASUS APK SHA-256 `7F62FE8250D0051AA408C6D23E7564AAA16960696A09B3C32B61065BAA0A56C4`.
- **named_procedure**: Review parent Records 154–180 and BLE Records 171–187; on the current ASUS install compare encrypted `fido2.db` hashes before/after both preference saves and restarts, run the current Classic registration/assertion and BLE assertion ceremonies, verify passkey-list count and saved/active UI states, and assess each SC against its own prescribed evidence.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11 host `LOQ15IRX10-3`; integrated WebAuthn.io; synthetic account `codex-synthetic-064-20261103`; 2026-11-07.
- **expected_observed_result**:

| Criterion | Result | Evidence and limitation |
|---|---|---|
| SC-001 — fresh BLE registration and authentication | Passed on the clean-install candidate; exact final-patch registration not repeated | ASUS fresh-install BLE registration and user-verified authentication succeeded with Android MakeCredential/GetAssertion responses and WebAuthn.io success (Record 155). The adapter-loss patch APK was installed in place; subsequent BLE authentication passed (Records 177 and 180). The exact patch APK was not freshly cleared/re-registered. |
| SC-002 — both switch directions preserve credentials/events and complete ceremonies | Partial | BLE→Classic save/restart left the encrypted database byte-identical, retained the local BLE credential, and Classic GetAssertion succeeded (Record 179). Classic→BLE save/restart likewise left the database byte-identical; Classic MakeCredential then BLE GetAssertion succeeded at account level (Record 180). Database hash covers encrypted credential/event bytes, but exact row/event counts and remote credential IDs were not independently decoded/mapped. |
| SC-003 — MakeCredential and GetAssertion on both transports | Passed at account level across recorded builds | BLE MakeCredential/GetAssertion succeeded in the fresh-install sequence (Record 155); current installed build completed Classic MakeCredential and GetAssertion, plus BLE GetAssertion (Records 179–180, 182). Exact cross-transport credential IDs are masked. |
| SC-004 — saved preference survives reboot and requires explicit start | Passed for both saved modes | BLE reboot restored BLE selected/Ready to Start, then explicit Start restored BLE (Record 164). Classic reboot restored Classic selected/Ready to Start; explicit activation was verified in the related reboot procedure (Record 166). |
| SC-005 — in-flight ceremony remains on original transport after concurrent setting save | Partial | Deterministic bounded host suite passed its registration/authentication cases for both transports and verified pending choice, original response and no unintended writes (Records 165/172; `TransportSelectionCeremonyTest` 2/2). On ASUS, saving Classic during BLE GetAssertion kept BLE active and assertion responses were acknowledged (Records 183–184), but neither attempt established login success while Classic was pending; a fresh login succeeded only after BLE was restored (Record 185). Record 187 shows a successful Classic authentication with BLE selected before the ceremony began. Record 188's consent-time attempt also logged in successfully, but the captured post-ceremony Settings hierarchy showed Classic checked and BLE unchecked, so it does not prove a changed choice remained pending. Physical consent-time save success is still unverified in both directions. |
| SC-006 — saved, active, pending and restored-not-started UI states | Passed | Settings UI tests and on-device inspection covered both directions. Current ASUS displayed active BLE with Classic pending, then Ready to Start after restart and Ready for Authentication after Classic Start (Record 179); the reverse pending/restart/Start states were observed in Record 180. |

- **date**: 2026-11-07.
- **limitations**: This matrix distinguishes result scope from missing exact identity evidence. Remote WebAuthn credential IDs are masked; the current install held one local credential before the first switch and two after Classic registration, but the server-side asserted ID cannot be correlated byte-for-byte. Encrypted database byte identity across settings transitions proves no database content changed during those transitions; the underlying event rows were not decoded. Physical SC-005 remains incomplete under parent T035: no successful physical ceremony has yet been verified with a transport choice changed during the consent wait. Coverage and accepted-target comparison remain owner-deferred under parent T039 and BLE T038. This record is an acceptance matrix with partial results, not a claim that every criterion passed.
- **status**: Passed (matrix review and per-criterion documentation; BLE/Classic ceremony and lifecycle outcomes recorded) / Partial (SC-002 exact identity/event comparison, physical consent-time setting change and remaining lifecycle criteria; SC-001 registration not repeated on the final adapter-loss-patch APK).
- **artifact_link**: Parent Records 154–188; BLE Records 171–187; `TransportSelectionCeremonyTest` and `ProcessTransportSelectionTest` XML; current Android logs and UI hierarchies referenced by Records 179–180 and 188; installed APK hash above.

### Record 182: Classic-Registered Synthetic Account Authenticated After BLE Restore (T031/T041 Support — Partial)
- **requirement**: Parent SC-002 / SC-003 and T031/T041; after Classic registration, restore BLE and perform a user-initiated WebAuthn assertion on the current ASUS build.
- **scope**: Authenticate the synthetic WebAuthn account after restoring BLE following Classic credential registration; observe the WebAuthn.io result and listed account credentials.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; installed ASUS `shrunkDebug` APK SHA-256 `7F62FE8250D0051AA408C6D23E7564AAA16960696A09B3C32B61065BAA0A56C4`.
- **named_procedure**: With BLE selected and the authenticator started, the user initiated Authenticate in the integrated WebAuthn.io tab for `codex-synthetic-064-20261103` and completed the Windows Bluetooth security-key flow and ASUS verification.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11 host `LOQ15IRX10-3`; integrated WebAuthn.io; 2026-11-07.
- **expected_observed_result**: After the user completed the Android “Sign in with Passkey” prompt, the integrated WebAuthn.io profile displayed “You're logged in!” for the synthetic account. ASUS logcat recorded BLE GetInfo followed by allow-list GetAssertion requests; one candidate proceeded through successful user verification (`addAuthToken: 0`), `Assertion response ready`, and a terminal notification acknowledged at 20:15:31.310. The authenticator returned to Ready for Authentication over FIDO Bluetooth LE. The WebAuthn.io profile showed multiple masked device-bound credentials, including USB-only and BLE/USB transport entries. This confirms a time-correlated, user-verified account-level BLE authentication after the Classic registration.
- **date**: 2026-11-07.
- **limitations**: WebAuthn.io masks credential IDs and contains multiple historical credentials; the trace cannot establish that the newly Classic-registered credential was the one asserted. Exact credential-ID/event comparison and physical mid-ceremony preference-save evidence remain incomplete; coverage and accepted-target comparison remain owner-deferred.
- **status**: Passed (user-verified BLE GetAssertion with acknowledged response and WebAuthn.io account login after BLE restoration) / Partial (exact asserted credential identity and physical mid-ceremony preference-save evidence).
- **artifact_link**: ASUS logcat 20:15:25–20:15:31, Ready-for-Authentication UI hierarchy, integrated WebAuthn.io profile showing the logged-in synthetic account and masked credential transport list; Records 180–181 for transport-switch evidence.

### Record 183: ASUS Preference Save During BLE GetAssertion (T035 / SC-005 — Partial)
- **requirement**: Parent T035 / SC-005; change the saved transport while a ceremony is in flight, verify that the current process retains its original transport and can deliver its original operation response, then verify the ceremony outcome.
- **scope**: While ASUS was actively serving BLE, save Classic during a user-initiated synthetic WebAuthn authentication; observe saved/pending/active UI, Android BLE responses, browser result, then restore the saved preference to BLE without restarting.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; installed ASUS `shrunkDebug` APK SHA-256 `7F62FE8250D0051AA408C6D23E7564AAA16960696A09B3C32B61065BAA0A56C4`.
- **named_procedure**: Start from active BLE, open transport Settings, start WebAuthn.io Authenticate for `codex-synthetic-064-20261103` with user verification Required and the Security Key hint, and save Classic while BLE GetAssertion requests are being serviced. Observe pending and active mode in Settings and correlate Android response/notification logs. Restore saved BLE and return to the authenticator screen without restarting.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11 host `LOQ15IRX10-3`; integrated WebAuthn.io; BLE GATT; 2026-11-07.
- **expected_observed_result**: Settings showed BLE selected and active before the request. During the request, selecting Classic displayed “A restart is required to use the saved choice” and “Active in this process: FIDO Bluetooth LE.” Android admitted BLE GetAssertion at 20:18:23.371, executed the headless fast path, completed an assertion and logged `Assertion response ready` at 20:18:23.498; the terminal notification was acknowledged at 20:18:23.502. Another GetAssertion was admitted at 20:18:23.651 and ended after 120,086 ms at 20:20:23.695. WebAuthn.io remained on its start page and showed no successful login. Saved BLE was restored before process restart; the settings screen returned to Active with no pending-change notice, and the app was returned to Ready for Authentication over BLE.
- **date**: 2026-11-07.
- **limitations**: This demonstrates that the saved Classic choice did not replace the live BLE process transport and that one original BLE assertion response was produced and acknowledged. It does not prove full ceremony completion: the browser never displayed login success and a subsequent request timed out. The exact asserted remote credential ID and credential/event counts were not mapped. T035 remains open for complete physical mid-ceremony outcome and other lifecycle/recovery subchecks; coverage and accepted-target comparison remain owner-deferred.
- **status**: Partial (physical preference-save semantics, BLE process continuity and acknowledged assertion response); Unverified (end-to-end WebAuthn success after the concurrent save and remaining T035 lifecycle criteria).
- **artifact_link**: ASUS UI hierarchy showing saved Classic / active BLE and the restored BLE state; ASUS app-PID logcat 20:18:22–20:20:23; WebAuthn.io page remaining on its start screen; Record 181 SC-005 matrix.

### Record 184: Repeated ASUS Concurrent Save After Settings Overlay Fix (T035 / SC-005 — Partial)
- **requirement**: Parent T035 / SC-005; changing the saved transport during an in-flight BLE ceremony must leave the current process on BLE and allow the original ceremony to finish.
- **scope**: On the updated app after a fresh synthetic registration, save Classic from Settings while BLE remains the active process transport; initiate WebAuthn authentication, capture BLE request responses and browser outcome, then restore the saved choice to BLE.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes, including the Settings overlay fix; installed ASUS `shrunkDebug` APK hash was not captured during this run.
- **named_procedure**: Install/relaunch the current `shrunkDebug` build; register `codex-synthetic-064-20261103` using WebAuthn.io with Registration User Verification Required, Cross-Platform attachment and Security Key hint; open Chimali transport Settings and save Bluetooth HID (Classic); from WebAuthn.io start Authenticate with User Verification Required and Security Key hint; complete Windows/ASUS prompts if presented; inspect browser and Android logs; restore FIDO Bluetooth LE in Settings and return to Authenticator without restarting.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11 host `LOQ15IRX10-3`; integrated WebAuthn.io; BLE GATT; package `com.chimali.shrunk`; 2026-11-07.
- **expected_observed_result**: WebAuthn.io first showed `Success! Now try to authenticate...` after the fresh BLE registration. Settings showed `Bluetooth HID (Classic)` saved and `Active in this process: FIDO Bluetooth LE`; after saving Classic, it showed `A restart is required to use the saved choice`. Android logged a GATT connection at 20:40:21.597, GetInfo completion, an allow-list nonmatch, then an admitted matching GetAssertion whose response was ready at 20:40:23.171 and whose terminal notification was acknowledged at 20:40:23.176. A subsequent GetAssertion was admitted at 20:40:23.327 and timed out after 120,000 ms at 20:42:23.342; the terminal diagnostic completed at 20:42:23.371. The browser snapshot after the operation showed no login-success message. Settings was reopened, confirmed Classic remained pending while BLE was active, then FIDO Bluetooth LE was saved again; the restart notice cleared. The app returned to `Advertising... / Ready to pair with a new host PC` over FIDO Bluetooth LE.
- **date**: 2026-11-07.
- **limitations**: The overlay fix kept the main shell composed under Settings, and this run again shows the saved Classic choice did not replace the live BLE process and an assertion response reached terminal acknowledgement. The WebAuthn ceremony did not produce browser-confirmed login, and the later admitted GetAssertion timed out; the user did not report completing the Windows/ASUS prompts, so no full end-to-end success is claimed. The exact credential ID and any response-to-browser correlation remain unknown. T035/SC-005 remains partial; other force-stop, process-death, reboot, interruption, stale-bond and unavailable-mode evidence is in preceding records. Coverage and accepted-target comparison remain owner-deferred.
- **status**: Partial (saved/active separation, live BLE session continuity, acknowledged assertion response and restoration to BLE); Unverified (end-to-end WebAuthn success for the concurrent-save ceremony and remaining parent T035 procedures).
- **artifact_link**: ASUS app-PID logcat around 20:40:21–20:42:23; WebAuthn.io DOM snapshot after timeout; ASUS UIAutomator hierarchy showing Classic pending / active BLE and post-restore Advertising; preceding Record 183; connected overlay test `AppShellWithOverlayTest`.

### Record 185: ASUS BLE Authentication Recovered After Concurrent-Save Timeout (T035 / SC-005 Support)
- **requirement**: Parent T035 / SC-005; following a timed-out in-flight operation and restored saved BLE preference, confirm the selected BLE process can accept and complete a fresh authentication without a process restart.
- **scope**: After Record 184's concurrent-save attempt timed out and saved BLE was restored in Settings, authenticate the same synthetic WebAuthn account again and inspect the browser result, BLE trace and app state.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; installed ASUS APK hash was not captured for this run.
- **named_procedure**: With FIDO Bluetooth LE saved and advertising, the user initiated Authenticate in the integrated WebAuthn.io tab for `codex-synthetic-064-20261103` and completed the Windows Bluetooth security-key and ASUS verification flow; inspect the WebAuthn result, device logs and app status.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11 host `LOQ15IRX10-3`; integrated WebAuthn.io; BLE GATT; 2026-11-07.
- **expected_observed_result**: The WebAuthn.io profile displayed `You're logged in!` for the synthetic account. At 20:48:19.384 ASUS accepted a GATT connection at MTU 517. It completed GetInfo at 20:48:20.480, returned `NO_CREDENTIALS` for a nonmatching allow-list entry, then processed a matching GetAssertion. The assertion response was ready at 20:48:24.616 and the terminal notification was acknowledged at 20:48:24.621; the GATT client disconnected at 20:48:27.908. No app restart or bond reset was performed. Chimali returned to `Advertising... / Ready to pair with a new host PC` over FIDO Bluetooth LE.
- **date**: 2026-11-07.
- **limitations**: This verifies a fresh BLE authentication after restoring BLE and after the earlier timed-out attempt, supporting session recovery. It does not turn Record 184's concurrent-save attempt into a successful WebAuthn ceremony because this login began after the saved preference had been restored to BLE. The exact credential ID remains masked and is not mapped to local credential/event data. Parent T035 and SC-005 remain partial for the required successful in-flight preference change and remaining lifecycle criteria; coverage and accepted-target comparison remain owner-deferred.
- **status**: Passed (fresh BLE WebAuthn login with acknowledged GetAssertion after restoring BLE, without app restart); Partial (physical concurrent-save success, exact credential mapping and remaining T035 lifecycle criteria).
- **artifact_link**: `.tmp/spec-064/asus-record-185-logcat.txt`; `.tmp/spec-064/asus-record-185-ready-ble.xml`; integrated WebAuthn.io profile success state with masked synthetic credential list; parent Record 184 for the preceding timeout and settings restoration.

### Record 186: Ceremony Callback Reveals Main Shell Beneath Settings (T035 — Physical Verification)
- **requirement**: Parent T035 / SC-005; when an external FIDO registration or authentication request arrives while Settings is open, keep the FIDO observer/navigation alive and reveal the ceremony prompt.
- **scope**: Keep the MainShell and FIDO navigation graph composed under Settings, dismiss the Settings overlay on external registration/authentication callbacks, run the focused shell instrumentation test on both available Android devices, and complete a physical Classic HID authentication while Settings is open.
- **revision**: Branch `064-bluetooth-transport-selection`; dirty source tree based on HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; installed `shrunkDebug` candidate SHA-256 `5A6BB7F300B49C775A1E6F536A6B7E987A018FB70872D7759FA8AEE39E84D5E5`.
- **named_procedure**: Run `:app:ktlintMainSourceSetCheck :app:ktlintAndroidTestSourceSetCheck :feature:fido2:ktlintAndroidMainSourceSetCheck`; assemble `:app:assembleShrunkDebug :app:assembleShrunkDebugAndroidTest`; install app/test APKs in place on ASUS and Mi; run `AppShellWithOverlayTest` on each device using AndroidJUnitRunner. On ASUS, open Settings with Bluetooth HID (Classic) active, initiate synthetic-account Authenticate in WebAuthn.io, select the Windows Bluetooth security key and complete the Android verification prompt.
- **environment**: Windows 11; ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Xiaomi Mi A2 / Android 10 API29 / serial `2ac6876`; Gradle 9.6.0; 2026-11-07.
- **expected_observed_result**: Focused KtLint and both assemblies passed. `ceremonyRequestDismissesSettingsWithoutDisposingMainShell` passed 1/1 on ASUS and 1/1 on Mi. The test dispatched an external-authentication callback while Settings covered the main shell, then confirmed the prompt became visible without disposing the shell. On ASUS, the user confirmed Settings was open before the physical authentication, disappeared when the verification prompt appeared, and was reopened after authentication. WebAuthn.io displayed `You're logged in!`; ASUS logs at 21:13:21–21:13:26 show HID GetAssertion processing, an assertion response, and accepted HID reports. The post-ceremony UI hierarchy showed Settings with Bluetooth HID (Classic) active. Both app and test APKs were installed without uninstalling packages or clearing local data.
- **date**: 2026-11-07.
- **limitations**: The user-reported Settings dismissal and verified WebAuthn success establish the physical Settings-to-Classic-ceremony flow, but no UI hierarchy was captured while the prompt was visible, and the exact credential ID remains masked. This does not verify WebAuthn completion while a newly saved transport choice remains pending, which remains open under SC-005/T035. Other lifecycle subcases remain open; coverage and accepted-target comparison remain owner-deferred.
- **status**: Passed (focused composition test on ASUS and Mi, 1/1 each; build and lint; physical Classic authentication with Settings dismissed and WebAuthn success); Partial (mid-ceremony transport-save success and remaining T035 lifecycle criteria).
- **artifact_link**: `.tmp/spec-064/asus-record-186-overlay-test.txt`; `.tmp/spec-064/mi-record-186-overlay-test.txt`; `.tmp/spec-064/asus-record-186-auth-logcat.txt`; `.tmp/spec-064/asus-record-186-auth-ui.xml`; integrated WebAuthn.io `You're logged in!` result; installed candidate SHA-256 above; `app/src/androidTest/kotlin/com/chimali/navigation/AppShellWithOverlayTest.kt`.

### Record 187: Classic Authentication with BLE Choice Already Pending (T035 / SC-005 Supporting Evidence)
- **requirement**: Parent T035 / SC-005; confirm that the active transport continues to serve ceremonies when another transport has been saved as pending.
- **scope**: Complete a Classic HID WebAuthn authentication after saving BLE while Classic remained active, without restarting the app.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; current ASUS candidate with the Settings consent action.
- **named_procedure**: Save FIDO Bluetooth LE in Chimali Settings while Bluetooth HID (Classic) remains active; start WebAuthn.io Authenticate for `codex-synthetic-064-20261103`; complete the Windows Bluetooth security-key chooser and ASUS verification.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11 host `LOQ15IRX10-3`; integrated WebAuthn.io; Bluetooth HID (Classic); 2026-11-07.
- **expected_observed_result**: The user completed the ASUS “Sign in with Passkey” prompt and WebAuthn.io displayed `You're logged in!`. Android logged Classic HID GetAssertion processing, an assertion response, and accepted HID reports. BLE was saved before the browser ceremony began, so this is supporting evidence for a pending saved choice, not the strict in-flight change.
- **date**: 2026-11-07.
- **limitations**: The transport choice was saved before the browser ceremony started. This does not establish the strict consent-time save procedure captured in Record 188, and the exact remote credential ID remains masked. Parent T035 remains open for BLE-active to Classic-pending in-flight success and other lifecycle criteria; coverage remains owner-deferred.
- **status**: Passed (Classic account authentication with BLE saved pending); Partial (strict concurrent-save criteria and remaining T035 lifecycle cases).
- **artifact_link**: ASUS UI and GetAssertion/HID log evidence from the 21:25 ceremony; user-confirmed WebAuthn.io login result; Record 188 for the strict in-flight reverse direction.

### Record 188: Successful Classic Authentication Without a Confirmed Consent-Time Setting Change (T035 / SC-005 — Inconclusive)
- **requirement**: Parent T035 / SC-005; while a ceremony is waiting for user confirmation, allow the user to change the saved transport and then complete the ceremony on the original process-active transport.
- **scope**: During Classic HID GetAssertion, open Settings from the consent prompt, save BLE without confirming or cancelling, return to consent, and complete authentication.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; installed ASUS APK SHA-256 `146602878042CA086DC51A94FC3E549325FDCA8916DA82D7063CE16E1CBACA8F`.
- **named_procedure**: Start with Classic HID advertising. Begin Authenticate in WebAuthn.io for `codex-synthetic-064-20261103`, select the Windows Bluetooth security key, and wait for ASUS consent. Tap **Transport settings**, save FIDO Bluetooth LE, return to consent without confirming or cancelling, then complete ASUS user verification.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11 host `LOQ15IRX10-3`; integrated WebAuthn.io; Bluetooth HID (Classic); 2026-11-07.
- **expected_observed_result**: WebAuthn.io displayed `You're logged in!`. Android logged GetAssertion processing at 21:50:21.832, `Assertion response ready` at 21:50:21.984, and `[NFR-PERF-030][local-handoff] GetAssertion = 252ms (Total: 3880ms, User: 3628ms, HID reports accepted: true)`. However, the captured post-ceremony Settings hierarchy showed `Bluetooth HID (Classic)` checked and `Selected for this process`, while `FIDO Bluetooth LE (default)` was unchecked. The ceremony succeeded over Classic, but the evidence does not show that the saved transport changed during consent.
- **date**: 2026-11-07.
- **limitations**: The post-ceremony selection contradicts the expected saved BLE choice. Do not count this run as a successful consent-time setting change. It records only a successful Classic authentication and an inconclusive attempt to change transport during the consent wait. Both physical consent-time switch directions, exact credential/event identity, and remaining T035 lifecycle/recovery procedures remain open. Coverage and accepted-target comparison remain owner-deferred.
- **status**: Passed (Classic HID authentication); Inconclusive (consent-time transport change not visible in captured Settings state; remaining parent T035/SC-005 procedures open).
- **artifact_link**: `.tmp/spec-064/asus-record-188-ble-pending-classic-active-settings.xml`; `.tmp/spec-064/asus-record-188-classic-getassertion-logcat.txt`; user-confirmed WebAuthn.io `You're logged in!` result; parent Records 183–187.

### Record 189: Consent Prompt Not Restored After Opening Transport Settings (T035 / SC-005 — Fix Implemented, Physical Retest Pending)
- **requirement**: Parent T035 / SC-005; opening Transport settings during an in-flight ceremony must preserve the consent prompt and allow returning to it without cancelling the request.
- **scope**: User-reported ASUS behavior, app-shell Back handling correction, and focused Compose regression check. This record does not claim the physical ceremony completed after the correction.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; corrected ASUS APK SHA-256 `A9F3932128F5BD112B3F4770DFB6AC583AA5011B1B9063B388210AB20C54E645`.
- **named_procedure**: During a synthetic WebAuthn consent prompt on ASUS, open Chimali's **Transport settings** and return to the Authenticator. The user reported that the consent prompt disappeared and did not reappear. Inspection found the full-screen Settings overlay did not consume Android system Back, allowing the underlying FIDO NavHost to handle Back. `AppShellWithOverlay` now accepts and invokes an overlay Back callback, and `AppNavGraph` closes Settings through it before the underlying navigation handles Back. A regression test verifies system Back closes the overlay while preserving the pending consent content.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Mi A2 / Android API29 / serial `2ac6876`; connected `shrunkDebug` Android tests; 2026-11-07.
- **expected_observed_result**: `:app:compileShrunkDebugAndroidTestKotlin` succeeded. `AppShellWithOverlayTest` passed 2/2 on ASUS/API35 and 2/2 on Mi/API29, including `systemBackClosesSettingsAndRestoresPendingCeremonyPrompt`. The Android connected-test task removed the `com.chimali.shrunk` package from both phones; the corrected ASUS APK was reinstalled without clearing app data, but the package had already been removed and its local test state was reset. The user granted the fresh-install permission and completed onboarding with Passkey Authenticator selected. The WebAuthn page still showed the prior timeout, so no physical ceremony has yet verified this correction.
- **date**: 2026-11-07.
- **limitations**: The regression test exercises the shell overlay contract with a pending consent view; it does not run the full production NavHost plus live BLE ceremony. Physical Back behavior, return to the same active consent prompt, in-flight save result and WebAuthn completion remain unverified. T035 and SC-005 remain open; parent T039 and BLE T038 remain owner-deferred.
- **status**: Passed (source correction, compile, focused Compose regression on both connected devices); Unverified (physical in-flight ceremony restoration and transport change).
- **artifact_link**: `app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-ASUS_AI2302 - 15.xml`; `app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-Mi A2 - 10.xml`; `app/src/androidTest/kotlin/com/chimali/navigation/AppShellWithOverlayTest.kt`.

### Record 190: BLE-Active Registration with Classic Saved During Consent (T035 / SC-005 — Passed for Settings Back)
- **requirement**: Parent T035 / SC-005; allow changing the saved transport while a ceremony awaits consent, preserve the process-active transport and the same consent request, then complete the operation.
- **scope**: Fresh ASUS synthetic registration on BLE with Bluetooth HID (Classic) saved pending through Chimali Transport Settings. This record verifies the app's Settings Back button path; Android system Back remains covered by the focused shell test, not by this physical ceremony.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; installed ASUS `shrunkDebug` APK SHA-256 `A9F3932128F5BD112B3F4770DFB6AC583AA5011B1B9063B388210AB20C54E645`.
- **named_procedure**: From fresh BLE advertising, start WebAuthn.io Register for `codex-synthetic-064-20261103`, select the Windows Bluetooth security key, open Chimali **Transport settings** from the ASUS consent screen, change the saved choice to Bluetooth HID (Classic) without restarting, use the Settings screen's **Back** button, confirm the same consent prompt returns, and complete registration.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11 host `LOQ15IRX10-3`; integrated WebAuthn.io; FIDO Bluetooth LE active; 2026-11-07.
- **expected_observed_result**: The user confirmed Classic was selected without restarting and that Settings' Back button returned to the same consent prompt. The integrated WebAuthn.io page displayed `Success! Now try to authenticate...`. ASUS logcat showed `MakeCredential response ready payloadLen=219` at 22:48:37.927 and a completed MakeCredential operation with `terminalNotificationAcknowledged=true` at 22:48:37.934. After the ceremony, the ASUS UI hierarchy showed `Advertising...` and `FIDO2 / WebAuthn over FIDO Bluetooth LE`, supporting that the existing process stayed on BLE while Classic remained the saved pending choice.
- **date**: 2026-11-07.
- **limitations**: The Settings screen state was not captured while Classic was selected, so the exact saved-versus-process row values are based on the user's report plus the post-ceremony BLE-active UI. The successful registration supports one physical BLE-active→Classic-pending direction; the opposite physical direction remains inconclusive in Record 188. The system-Back route was not used in this physical attempt, and force-stop, process-death, reboot and unavailable-mode T035 procedures remain incomplete. Coverage and accepted-target comparison remain owner-deferred.
- **status**: Passed (fresh WebAuthn registration, MakeCredential terminal acknowledgement, return to the same consent via Settings Back, BLE still active); Partial (exact Settings-row capture, physical system Back, opposite switch direction and other T035 lifecycle checks).
- **artifact_link**: `.tmp/spec-064/asus-record-190-after-ceremony.xml`; `app/src/androidTest/kotlin/com/chimali/navigation/AppShellWithOverlayTest.kt`; connected test result XMLs cited in Record 189; integrated WebAuthn.io registration success observed in session.

### Record 191: ASUS Classic Selection Persists Across Force-Stop (T035 — Partial)
- **requirement**: Parent T035; a saved transport choice survives force-stop/relaunch and is selected without starting until the user explicitly starts the authenticator.
- **scope**: Current ASUS/API35 build after Record 190, where BLE was process-active and Classic was saved pending during a successful registration. Force-stop the app, explicitly launch `MainActivity`, and inspect the resulting transport/start state without changing app data.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes. Installed APK SHA-256 and local `shrunkDebug` APK SHA-256 both equal `A9F3932128F5BD112B3F4770DFB6AC583AA5011B1B9063B388210AB20C54E645`.
- **named_procedure**: `adb shell am force-stop com.chimali.shrunk`, followed by `adb shell am start -n com.chimali.shrunk/com.chimali.MainActivity`; inspect the foreground activity and UI hierarchy.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; `com.chimali.shrunk` version `0.1.0`, installed 2026-11-07 22:41 UTC; 2026-11-07.
- **expected_observed_result**: The app relaunched in `Ready to Start`, with the UI transport label `FIDO2 / WebAuthn over Bluetooth HID`; no active advertising state was displayed. The changed saved Classic choice therefore survived force-stop while the newly started process remained stopped until explicit Start. After tapping Start and accepting Android's 120-second visibility prompt under the existing test authorization, the UI showed `Advertising...` and `Ready to pair with a new host PC`, Android Bluetooth reported `SCAN_MODE_CONNECTABLE_DISCOVERABLE`, and `Fido2TransportService` was foreground. The installed APK hash matched the local candidate.
- **date**: 2026-11-07.
- **limitations**: This covers one Classic force-stop/relaunch/start sequence only. It does not verify BLE-mode force-stop, Android-managed process death, reboot, or unavailable-mode recovery. The visibility window is time-limited. No credentials were cleared or modified. These remaining procedures keep parent T035 open; coverage and accepted-target comparison remain owner-deferred.
- **status**: Passed (Classic saved choice persisted across force-stop; process remained stopped until explicit Start; Classic advertiser/foreground service became active afterward); Partial (remaining T035 lifecycle procedures).
- **artifact_link**: `.tmp/spec-064/asus-record-191-after-force-stop.xml`; `.tmp/spec-064/asus-record-191-after-classic-start.xml`; `.tmp/spec-064/asus-record-191-installed.apk`; local `app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk`.

### Record 192: Physical Android System-Back Retest (T035 / SC-005 — Inconclusive)
- **requirement**: Parent T035 / SC-005; Android system Back from Transport Settings during a live FIDO consent must close the overlay and restore the same pending consent without cancelling the ceremony.
- **scope**: Attempted ASUS/API35 WebAuthn authentication while Classic HID was active after Record 191. The requested path was to open Chimali Transport Settings from the live consent and use Android's edge-swipe/system Back gesture.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; installed `shrunkDebug` APK matches local candidate SHA-256 `A9F3932128F5BD112B3F4770DFB6AC583AA5011B1B9063B388210AB20C54E645`.
- **named_procedure**: User-initiated WebAuthn.io Authenticate, select the Bluetooth security key in Windows, open Transport Settings from ASUS consent, and use Android system Back. The browser ultimately showed `The operation either timed out or was not allowed`.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11 host `LOQ15IRX10-3`; integrated WebAuthn.io; Bluetooth HID (Classic); 2026-11-07.
- **expected_observed_result**: WebAuthn.io ended in its timeout state. Android logcat during the window contained successful Classic HID MakeCredential/GetAssertion operations and later `NO_CREDENTIALS` responses; the current app hierarchy showed `Connected to PC`. These events were not correlated to opening Settings or using system Back, and do not establish that the same pending consent returned.
- **date**: 2026-11-07.
- **limitations**: The user later reported Windows showed `No se pudo iniciar sesion. Vuelve a intentarlo y asegurate de completar la accion de seguimiento` during the system-Back attempt. This confirms the ceremony did not complete, but does not confirm whether the same consent prompt returned. The browser timeout and uncorrelated transport logs make the physical system-Back check inconclusive. Do not count it as passed; the focused Compose system-Back test remains the only direct evidence for that route. A nested NavHost BackHandler may have taken precedence over the overlay callback; Record 193 addresses that ordering gap. Parent T035/SC-005 remain open. Coverage and accepted-target comparison remain owner-deferred.
- **status**: Inconclusive (system-Back action and consent restoration unverified; WebAuthn attempt timed out).
- **artifact_link**: WebAuthn.io timeout observed in the integrated browser; Android UI and logcat were inspected live, but no raw log containing device identifiers was retained.

### Record 193: Overlay Back Callback Priority Correction (T035 / SC-005 — Source Verified, Physical Retest Pending)
- **requirement**: Parent T035 / SC-005; Android system Back from Transport Settings must close the overlay and reveal the still-pending FIDO consent rather than dispatching Back to the retained FIDO NavHost.
- **scope**: Follow-up to the user's physical Windows error report in Record 192. `AppShellWithOverlay` initially registered its BackHandler before composing the retained MainShell; the nested navigation host could register a later Back callback and take precedence. The overlay callback is now composed after main and overlay content so it wins dispatch while visible. The regression test now installs an underlying `BackHandler` and asserts system Back closes the overlay without invoking that handler, while pending consent remains displayed.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes. Current ASUS APK SHA-256 matches local candidate: `6D0D87D179AFD0A375276EA44177D340A16931D380F218495AF13B8C247237C9`.
- **named_procedure**: Compile focused Android test sources, run app KtLint, assemble `shrunkDebug`, install with `adb install -r` without uninstalling or clearing app data, and inspect current device state.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11 host `LOQ15IRX10-3`; 2026-11-07.
- **expected_observed_result**: `:app:compileShrunkDebugAndroidTestKotlin` and `:app:ktlintCheck` passed; `:app:assembleShrunkDebug` passed; in-place installation succeeded and the installed APK hash matched. The saved Classic selection and Windows bond remained visible; after Start and accepting the 120-second Android visibility prompt, the app showed `Ready for Authentication` in Bluetooth HID mode with the foreground service running. No physical system-Back ceremony has yet been completed on this corrected build.
- **date**: 2026-11-07.
- **limitations**: Dispatcher registration order is the likely cause suggested by the callback structure and user-reported failure, but the failure logs do not prove which callback consumed the physical gesture. The strengthened Compose test was compiled but not run on-device because the connected-test cleanup can remove the app package and its local data. Physical system Back, same-consent restoration and WebAuthn completion remain pending. Coverage and accepted-target comparison remain owner-deferred.
- **status**: Passed (source correction, focused test compilation, lint, build and data-preserving installation); Unverified (physical corrected system-Back flow).
- **artifact_link**: `app/src/main/kotlin/com/chimali/navigation/AppShellWithOverlay.kt`; `app/src/androidTest/kotlin/com/chimali/navigation/AppShellWithOverlayTest.kt`; `.tmp/spec-064/asus-system-back-ready.xml`; local and installed `shrunkDebug` APKs.

### Record 194: ASUS System Back Closes the Settings Overlay (T035 / SC-005 — Partial Physical Pass)
- **requirement**: Parent T035 / SC-005; Android system Back must close the in-app transport Settings overlay while preserving the active authenticator shell and transport process.
- **scope**: Physical ASUS check of the corrected overlay callback priority with the Classic transport foreground service active. No WebAuthn ceremony or pending consent prompt was active.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; installed ASUS APK SHA-256 `6D0D87D179AFD0A375276EA44177D340A16931D380F218495AF13B8C247237C9`.
- **named_procedure**: Open Chimali Transport Settings from the authenticator screen and dispatch Android's system Back key (`adb shell input keyevent 4`), then inspect the foreground UI and service state.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; `com.chimali.shrunk`; Bluetooth HID (Classic); 2026-11-07.
- **expected_observed_result**: Settings displayed `Bluetooth HID (Classic)` and `Active in this process: Bluetooth HID (Classic)`. System Back returned to `Ready for Authentication` / `Waiting for request from PC`. `Fido2TransportService` remained foreground. The main shell and Classic mode were retained.
- **date**: 2026-11-07.
- **limitations**: This verifies real Android system Back against the running app and active process, but no consent prompt or FIDO request was pending. It does not prove that the same consent returns or that WebAuthn completes during an in-flight ceremony; that physical SC-005 case remains unverified. `SCAN_MODE_CONNECTABLE` was observed after the earlier 120-second visibility window and is not attributed to Back.
- **status**: Passed (physical Settings overlay closed and active shell/service remained); Partial (in-flight consent restoration and ceremony completion).
- **artifact_link**: `.tmp/spec-064/asus-record-194-after-system-back.xml`; `.tmp/spec-064/asus-system-back-ready.xml`.

### Record 195: Mi BLE Force-Stop Persistence and Explicit Start (T035)
- **requirement**: Parent T035 / US4; after force-stop/relaunch, the saved transport remains selected but stopped until explicit Start, and the selected BLE advertiser starts successfully.
- **scope**: Current candidate on Mi A2/API29. The package was updated in place with matching signer, Passkey Authenticator onboarding was completed, and the BLE force-stop/relaunch/start procedure was run without clearing app data or creating credentials.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; local and installed APK SHA-256 `6D0D87D179AFD0A375276EA44177D340A16931D380F218495AF13B8C247237C9`.
- **named_procedure**: Verify BLE is selected at Ready to Start, run `adb shell am force-stop com.chimali.shrunk`, explicitly relaunch MainActivity, verify it remains Ready to Start with FIDO Bluetooth LE selected and no foreground transport service, then tap Start and verify the BLE advertiser.
- **environment**: Mi A2 / Android 10 API29 / serial `2ac6876`; Windows 11; `com.chimali.shrunk` version `0.1.0`; 2026-11-07.
- **expected_observed_result**: After force-stop/relaunch, the UI still showed `FIDO2 / WebAuthn over FIDO Bluetooth LE` and `Ready to Start`; no foreground FIDO transport service was present. After explicit Start, the UI showed `Advertising...` and `Ready to pair with a new host PC`; `Fido2TransportService` became foreground, and Android Bluetooth logged `onAdvertisingSetStarted` with status `0`. The installed APK SHA-256 matched the candidate.
- **date**: 2026-11-07.
- **limitations**: This closes only the Mi/API29 BLE force-stop/relaunch and explicit-start subcase. No remote Windows connection or ceremony followed. Parent T035 remains open for in-flight consent/system-Back restoration, process death, reboot and unavailable-mode recovery; coverage T039 and BLE T038 remain owner-deferred.
- **status**: Passed (BLE selection persistence, no auto-start after force-stop, explicit-start advertising); Partial (remaining parent lifecycle cases).
- **artifact_link**: `.tmp/spec-064/mi-record-195-after-force-stop.xml`; `.tmp/spec-064/mi-record-195-after-start.xml`; `.tmp/spec-064/mi-record-195-installed.apk`; child BLE Record 188.

### Record 196: Mi Foreground Process Was Not Killed by Safe ActivityManager Request (T035 — Unverified)
- **requirement**: Parent T035; verify Android-managed process death while the selected transport foreground service is active and whether Android restarts its sticky service.
- **scope**: Mi/API29 with BLE advertising and `Fido2TransportService` running foreground. The app was backgrounded; `adb shell am kill com.chimali.shrunk` was issued, then process/service state was inspected. A same-UID `run-as ... kill -9 <pid>` attempt was also made but the device returned `kill: unknown pid`.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; installed current APK SHA-256 `6D0D87D179AFD0A375276EA44177D340A16931D380F218495AF13B8C247237C9`.
- **named_procedure**: Background the Mi app; capture PID, run ActivityManager's safe `am kill`, wait and compare PID/service. Attempted same-UID signal injection was rejected by the device and did not terminate the process.
- **environment**: Mi A2 / Android 10 API29 / serial `2ac6876`; current BLE foreground service; 2026-11-07.
- **expected_observed_result**: PID remained `2503` before and after `am kill`; `Fido2TransportService` remained foreground with `startRequested=true`, `stopIfKilled=false` and `START_STICKY` configured in source. The signal attempt failed before sending a signal. No process death or service restart occurred.
- **date**: 2026-11-07.
- **limitations**: This safe kill request did not exercise process death and cannot prove sticky restart. No app data or active BLE advertising was changed. A valid Android-managed process death source remains necessary; this procedure is Unverified, not Passed.
- **status**: Unverified (the process was protected by its foreground service; no restart occurred).
- **artifact_link**: Mi logcat/dumpsys observations were inspected live; no raw logs retained.

### Record 197: ASUS System-Back Ceremony Ended with Windows Follow-Up Error (T035 / SC-005 — Failed Attempt, Route Inconclusive)
- **requirement**: Parent T035 / SC-005; opening Transport Settings during an active WebAuthn ceremony and using Android system Back must restore the same pending consent and permit completion.
- **scope**: User-reported ASUS system-Back attempt after the overlay BackHandler priority correction in Record 193. This captures the Windows outcome; the exact Android navigation and request lifecycle were not observable in correlated device logs.
- **revision**: Branch `064-bluetooth-transport-selection`; ASUS corrected shrunkDebug build SHA-256 `6D0D87D179AFD0A375276EA44177D340A16931D380F218495AF13B8C247237C9`.
- **named_procedure**: The user attempted Android system Back during an authentication operation. Windows displayed `No se pudo iniciar sesión. Vuelve a intentarlo y asegúrate de completar la acción de seguimiento`.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11; Bluetooth HID (Classic); 2026-11-07.
- **expected_observed_result**: WebAuthn did not complete successfully. Afterward, ASUS showed `Ready for Authentication` / `Waiting for request from PC`, with Classic active. Recent ASUS logcat contained no ceremony events correlated with this attempt, so it does not establish whether system Back reached the Settings overlay, whether the same consent prompt returned, or whether another Windows follow-up step failed.
- **limitations**: The Windows error is evidence of a failed ceremony, not proof that the overlay Back correction failed or succeeded. The user has not confirmed that the same ASUS consent prompt reappeared after system Back. The focused shell test and idle physical overlay check remain positive evidence, but physical in-flight restoration and completion remain unverified. Parent T035/SC-005 stay open; parent T039 and BLE T038 remain owner-deferred.
- **status**: Failed (WebAuthn sign-in); Inconclusive (system-Back route and consent restoration).
- **artifact_link**: User-reported Windows error; current ASUS UI/logcat snapshot from 2026-11-07.

### Record 198: Mi BLE Advertising Recovers After Bluetooth Radio Is Restored (T035 — Partial)
- **requirement**: Parent T035; an unavailable BLE mode must report recovery guidance and resume advertising when Bluetooth is restored and the user retries.
- **scope**: Mi/API29 current-candidate radio-off interruption and explicit in-app retry. No Windows host or ceremony was active.
- **revision**: Branch `064-bluetooth-transport-selection`; installed shrunkDebug APK SHA-256 `6D0D87D179AFD0A375276EA44177D340A16931D380F218495AF13B8C247237C9`.
- **named_procedure**: Turn Bluetooth off through Android Quick Settings, inspect Chimali's unavailable state, restore Bluetooth through Quick Settings, then tap `Retry Authenticator`.
- **environment**: Mi A2 / Android API29 / serial `2ac6876`; `com.chimali.shrunk`; FIDO Bluetooth LE; 2026-11-07.
- **expected_observed_result**: With Bluetooth off, Chimali showed `Error Occurred` and `Bluetooth is off; restore Bluetooth and retry`. After Bluetooth was restored (`bluetooth_on=1`, Bluetooth manager enabled) and Retry was tapped, Chimali showed `Advertising...`; Android logged BLE advertising start status `0`, and the FIDO foreground service remained active.
- **limitations**: No Windows host or active WebAuthn ceremony tested host recovery. This verifies radio restoration plus explicit retry only; parent T035 remains open for other lifecycle cases.
- **status**: Passed (Mi unavailable-mode message and retry restored BLE advertising); Partial (no host ceremony).
- **artifact_link**: Mi UI hierarchy and logcat inspected live on 2026-11-07; parent Record 195 for Mi force-stop/relaunch evidence.

### Record 199: Current Worktree Non-Coverage Local CI (Parent and BLE Software Validation)
- **requirement**: Parent T039 and BLE T038 local static-analysis, compilation and configured host/unit-test portion. This record does not claim their coverage, accepted-target nonregression or connected-device requirements.
- **scope**: Current shared worktree on branch `064-bluetooth-transport-selection`; `tools/local-ci.ps1 -NoFix -NoConfigurationCache` ran with `GRADLE_USER_HOME` set to the worktree-local `.tmp/gradle-home` to avoid the inaccessible global Gradle distribution lock.
- **revision**: Working tree based on source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; uncommitted concurrent changes were preserved.
- **named_procedure**: Run `tools/local-ci.ps1 -NoFix -NoConfigurationCache` without `-RunCoverage`.
- **environment**: Windows development host; Gradle 9.6.0; Kotlin 2.3.21; 2026-11-07.
- **expected_observed_result**: KtLint passed, Detekt passed, Release lint passed, all configured main/test-source compilation passed, ShrunkDebug unit tests passed, and configured KMP Android host suites passed. Total reported duration: 206.76 seconds. Kotlin daemon temp-file access errors caused compilation fallback to non-daemon mode; the build still completed successfully.
- **limitations**: The run did not execute connected device tests, coverage completeness/floors, candidate-versus-accepted-target raw-count comparison or hosted integration checks. The first attempt exposed ComposableParamOrder findings in `AppShellWithOverlay` and the registration/authentication prompt composables; their parameter order was corrected before this passing run. Parent T039 and BLE T038 remain open and owner-deferred for coverage/nonregression.
- **status**: Passed (non-coverage static analysis, lint, compilation, unit and configured host tests); Deferred (coverage/nonregression and device execution).
- **artifact_link**: Console output from `tools/local-ci.ps1`; Gradle reports under module `build/reports`.

### Record 200: Mi Reboot State Could Not Be Inspected (T035 — Unverified)
- **requirement**: Parent T035; after reboot, saved transport selection must remain selected and transport must wait for explicit Start.
- **scope**: Mi A2/API29 reboot check initiated after BLE radio-off recovery. No app data or settings were changed.
- **revision**: Branch `064-bluetooth-transport-selection`; installed current candidate hash was recorded in parent Record 198.
- **named_procedure**: Issue `adb reboot` to the connected Mi device, then poll ADB enumeration and inspect Chimali state after it becomes available.
- **environment**: Mi A2 / Android API29 / serial `2ac6876`; 2026-11-07.
- **expected_observed_result**: Mi disappeared from ADB after the reboot and did not re-enumerate during the observation window. It could not be queried for boot-complete, lock state, app launch, saved BLE selection or foreground service. The ASUS remained connected to ADB.
- **limitations**: No post-reboot application observation was possible. This is not evidence that the app or transport failed or passed; reboot persistence remains unverified pending device reconnection.
- **status**: Unverified (device unavailable over ADB after reboot).
- **artifact_link**: ADB device-list observations on 2026-11-07; no app-state artifact available.

### Record 201: Both Saved Modes Survive ActivityManager Background-Process Kill (T035 — Partial)
- **requirement**: Parent T035 / SC-004; saved transport selection must be restored after OS process death, while a new process must not activate the selected transport before an explicit Start.
- **scope**: ASUS/API35, no ceremony active. Two sequential checks covered Classic saved and BLE saved. For each kill, the authenticator was explicitly stopped first, so its foreground transport service was not running when Android killed the app process.
- **revision**: Branch `064-bluetooth-transport-selection`; current worktree candidate based on source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; no reinstall or app-data reset occurred.
- **named_procedure**: First, tap Chimali's Stop Authenticator with Classic selected; verify Ready to Start and no FIDO service; background the app; run `adb shell am kill com.chimali.shrunk`; inspect Android's ApplicationExitInfo; explicitly launch `MainActivity`; inspect restored selection/service state. Second, open Transport Settings in the relaunched Classic process, save BLE pending while Classic remains the frozen process choice, background the app and repeat the ActivityManager kill and explicit MainActivity launch.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; Classic HID selected; 2026-11-08 local device time.
- **expected_observed_result**: Classic cycle: PID changed from `29811` to `2858`; Android recorded `reason=10 (USER REQUESTED)`, `subreason=24 (KILL BACKGROUND)`, and `description=kill background`. After explicit relaunch, Chimali showed `Ready to Start` and `FIDO2 / WebAuthn over Bluetooth HID`, with no FIDO service. BLE cycle: after saving BLE pending from the Classic process, PID changed from `2858` to `3809`; Android recorded the same `KILL BACKGROUND` exit. After explicit relaunch, Transport Settings showed `Selected for this process: FIDO Bluetooth LE` and `Selected; not started`; after Settings was closed, the home screen showed `Ready to Start` and `FIDO2 / WebAuthn over FIDO Bluetooth LE`, with no FIDO service. Both saved modes survived process death and neither auto-started.
- **limitations**: ActivityManager killed the process only after the authenticator had been stopped and its foreground service removed. This does not exercise process death while the transport foreground service is active, sticky-service restart, or subsequent advertising. The T035 active-service process-death subcase remains open.
- **status**: Passed (background-process death, both mode selections restored and no auto-start); Partial (foreground-service process death/restart).
- **artifact_link**: `.tmp/spec-064/asus-record-201-after-process-kill.xml`; `.tmp/spec-064/asus-record-201-services.txt`; `.tmp/spec-064/asus-record-201-after-process-kill-ble-settings.xml`; `.tmp/spec-064/asus-record-201-after-process-kill-ble-services.txt`; Android `dumpsys activity exit-info` observations.

### Record 202: Android Restarts Active BLE Foreground Service After App Process Crash (T035 — Partial)
- **requirement**: Parent T035 / SC-004; when the process hosting an active selected transport exits, Android should restart the sticky service, resolve the persisted process choice, and restore only that transport without data loss or user-mode fallback.
- **scope**: Current ASUS/API35 build with FIDO BLE active and no WebAuthn ceremony in progress. The app was deliberately terminated through Android's controlled `am crash` command while the BLE service was foreground.
- **revision**: Branch `064-bluetooth-transport-selection`; candidate APK SHA-256 `082859FCC7745587269EAC1470DB55B317F6BA04A0331E31848475A62041F6BC`; installed with `adb install -r`, preserving app data.
- **named_procedure**: Build `:app:assembleShrunkDebug`, install with `adb install -r`, and compare the pulled installed APK hash with the local candidate; verify BLE `Ready for Authentication`, foreground `Fido2TransportService`, and registered FIDO GATT service; run `adb shell am crash com.chimali.shrunk`; inspect Android exit history and service state; relaunch MainActivity and confirm the resumed transport.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; FIDO Bluetooth LE; 2026-11-08 device time.
- **expected_observed_result**: Before the injected crash, package PID was `6352`. Android `dumpsys activity exit-info` recorded `reason=4 (APP CRASH(EXCEPTION))` at 00:09:08.059. Within the observation window, PID `6973` hosted `Fido2TransportService`; it was foreground, `startRequested=true`, and had `lastStartId=2` (restarted service start). Android registered a new GATT server; `dumpsys bluetooth_manager` shows FIDO GATT service `0xFFFD` started and the Chimali advertiser active from 00:09:10. After MainActivity was explicitly reopened, the UI showed `Ready for Authentication` and `FIDO2 / WebAuthn over FIDO Bluetooth LE`.
- **limitations**: This is controlled crash injection, not natural memory-pressure eviction. It proves Android's sticky-service restart after a process crash on ASUS/API35, but does not cover Mi/API29 or a spontaneous OS resource-reclamation event. No ceremony was active, and this does not test in-flight request recovery. Parent T035 remains open for system Back, Mi reboot and the remaining recovery cases.
- **status**: Passed (controlled active-process crash followed by sticky BLE service/GATT advertising restart and UI recovery); Partial (natural OS process death, in-flight recovery and other lifecycle cases).
- **artifact_link**: `.tmp/spec-064/asus-record-202-installed.apk` (hash matches local candidate); `.tmp/spec-064/asus-record-202-exit-info.txt`; `.tmp/spec-064/asus-record-202-after-crash-services.txt`; `.tmp/spec-064/asus-record-202-bluetooth-manager.txt`; `.tmp/spec-064/asus-record-202-after-restart.xml`; terminal output from `am crash` and restart inspection.

### Record 203: ASUS System-Back Authentication Retry Shows Windows Follow-Up Error (T035 / SC-005 — Inconclusive)
- **requirement**: Parent T035 / SC-005; Android system Back from Transport Settings during an active authentication must restore the same pending consent and permit WebAuthn completion.
- **scope**: User-reported ASUS authentication attempts using Android system Back during the operation. In the latest retest, Windows again displayed `No se pudo iniciar sesión. Vuelve a intentarlo y asegúrate de completar la acción de seguimiento`.
- **revision**: Branch `064-bluetooth-transport-selection`; ASUS installed shrunkDebug candidate SHA-256 `082859FCC7745587269EAC1470DB55B317F6BA04A0331E31848475A62041F6BC`. The physical ceremony could not be correlated to a fresh CTAP trace.
- **named_procedure**: Start WebAuthn authentication, open Chimali Transport Settings during the operation, invoke Android system Back, then observe the Windows and ASUS outcomes.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11; FIDO Bluetooth LE; 2026-11-07 local date.
- **expected_observed_result**: The user-reported WebAuthn ceremony failed with the Windows follow-up error, including on the latest system-Back retest. After that report, the attached ASUS hierarchy showed Chimali at `Ready for Authentication` / `Waiting for request from PC`; the FIDO transport service remained foreground. The retained logcat sample contained no correlated CTAP ceremony events.
- **limitations**: This confirms another unsuccessful authentication attempt but not whether the same consent prompt returned after system Back or whether a separate Windows follow-up action failed. It neither proves nor disproves the Compose BackHandler correction. Physical in-flight consent restoration and successful completion remain unverified. Parent T035/SC-005 remains open; parent T039 and BLE T038 remain owner-deferred.
- **status**: Failed (user-reported WebAuthn authentication); Inconclusive (system-Back route and consent restoration).
- **artifact_link**: `.tmp/spec-064/asus-record-203-after-system-back.xml`; `.tmp/spec-064/asus-record-203-correlated-logcat.txt`; `.tmp/spec-064/asus-system-back-latest.xml`; user-reported Windows error.

### Record 204: Current ASUS Build Closes Idle Settings with Android System Back (T035 — Partial)
- **requirement**: Parent T035 / SC-005; Android system Back from Transport Settings closes the overlay while preserving the authenticator and active transport service.
- **scope**: Current installed ASUS candidate, with no WebAuthn ceremony active. This is an idle overlay check and does not cover a pending consent prompt.
- **revision**: Branch `064-bluetooth-transport-selection`; installed shrunkDebug APK SHA-256 `082859FCC7745587269EAC1470DB55B317F6BA04A0331E31848475A62041F6BC`.
- **named_procedure**: Open Settings from the Ready for Authentication dashboard, verify the settings screen is present, dispatch Android system Back (`adb shell input keyevent 4`), and inspect the resulting UI and service state.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; 2026-11-07 local date.
- **expected_observed_result**: Settings showed `Active in this process: FIDO Bluetooth LE` before Back. After the system Back key event, the UI returned to `Ready for Authentication` / `Waiting for request from PC`. `Fido2TransportService` remained foreground with `startRequested=true`, and the installed APK hash matched the candidate used for Record 202.
- **limitations**: No FIDO request was active. This proves the idle BLE overlay route on the current build, but not restoration/completion of the same consent during a ceremony. The latest in-flight attempt remains inconclusive in Record 203; parent T035/SC-005 remains open.
- **status**: Passed (idle Settings/system-Back dismissal with foreground BLE service retained); Partial (in-flight consent restoration remains unverified).
- **artifact_link**: `.tmp/spec-064/asus-record-204-settings.xml`; `.tmp/spec-064/asus-record-204-after-system-back.xml`; `.tmp/spec-064/asus-record-204-installed.apk`; `.tmp/spec-064/asus-record-204-service.txt`.

### Record 205: ASUS BLE Recovers After Bluetooth Radio-Off and Explicit Retry (T035 — Partial)
- **requirement**: Parent T035; when the selected BLE transport becomes unavailable, report the reason without fallback and restore it after Bluetooth returns and the user retries.
- **scope**: Current ASUS/API35 BLE candidate with no WebAuthn ceremony active. The Android Bluetooth adapter was disabled, restored and followed by the in-app Retry action.
- **revision**: Branch `064-bluetooth-transport-selection`; installed shrunkDebug APK SHA-256 `082859FCC7745587269EAC1470DB55B317F6BA04A0331E31848475A62041F6BC`.
- **named_procedure**: Confirm BLE `Ready for Authentication` and foreground service; execute `adb shell svc bluetooth disable`; inspect app/UI and radio state; execute `adb shell svc bluetooth enable`; confirm the app still awaits Retry; tap `Retry Authenticator`; inspect ready UI, service and Bluetooth diagnostics.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; FIDO Bluetooth LE; 2026-11-07 local date.
- **expected_observed_result**: On adapter disable, Chimali showed `Error Occurred` and `Bluetooth is off; restore Bluetooth and retry`; Android's Bluetooth setting read `0`. Restoring the adapter changed the setting to `1` while the app remained in its retryable error state. Tapping Retry returned the app to `Ready for Authentication` / `Waiting for request from PC`. The FIDO foreground service was active (`startRequested=true`, `lastStartId=3`); Bluetooth diagnostics showed the Chimali advertiser and FIDO `0xFFFD` GATT service registered.
- **limitations**: No Windows connection or CTAP ceremony was active, so host-side reconnection and recovery during an admitted request were not tested. This verifies selected BLE unavailable-state and explicit retry recovery only. Parent T035 remains open for in-flight consent/system-Back, reboot and remaining lifecycle cases; parent T039 and BLE T038 remain owner-deferred.
- **status**: Passed (BLE error guidance, no fallback, explicit retry and advertiser/GATT restoration); Partial (no host ceremony/reconnect tested).
- **artifact_link**: `.tmp/spec-064/asus-record-205-bluetooth-off.xml`; `.tmp/spec-064/asus-record-205-radio-restored-before-retry.xml`; `.tmp/spec-064/asus-record-205-after-retry.xml`; `.tmp/spec-064/asus-record-205-service.txt`; `.tmp/spec-064/asus-record-205-bluetooth-manager.txt`; `.tmp/spec-064/asus-record-205-recovery-logcat.txt`.

### Record 206: System-Back Regression Retains the Pending Ceremony Composition (T035 / SC-005 — Compiled, Runtime Pending)
- **requirement**: Parent T035 / SC-005; dismissing Transport Settings with system Back must not invoke the underlying ceremony BackHandler or dispose the pending consent composition.
- **scope**: Strengthened `AppShellWithOverlayTest.systemBackClosesSettingsAndRestoresPendingCeremonyPrompt` to assert a stable remembered ceremony identity and no disposal of the retained main shell, in addition to verifying overlay closure and suppression of the underlying BackHandler.
- **revision**: Branch `064-bluetooth-transport-selection`; current working-tree implementation. No production source behavior changed for this test update.
- **named_procedure**: Run `:app:compileShrunkDebugAndroidTestKotlin` and `:app:ktlintCheck` with the worktree-local Gradle home. Do not invoke connected instrumentation because its cleanup can uninstall `com.chimali.shrunk` and remove the device's local synthetic wallet.
- **environment**: Windows 11; Gradle 9.6.0; ASUS API35 attached but not used for instrumentation; 2026-11-07 local date.
- **expected_observed_result**: Android test sources compiled successfully in 15 seconds; Kotlin daemon temporary-file access failed and Gradle completed using its non-daemon fallback. App KtLint passed in 19 seconds. The device test itself was not run.
- **limitations**: At the time of this record, compilation and lint did not prove runtime behavior. An isolated Android 15 AVD remained `offline` in ADB through first boot and reconnect and was stopped. The focused instrumentation was subsequently run on ASUS in Record 207. The physical in-flight attempt remains inconclusive in Record 203, and the idle device route in Record 204 does not close SC-005.
- **status**: Passed (test-source compilation and KtLint); runtime result recorded in the follow-up Record 207; physical in-flight consent restoration remains unverified.
- **artifact_link**: `app/src/androidTest/kotlin/com/chimali/navigation/AppShellWithOverlayTest.kt`; Gradle output for `:app:compileShrunkDebugAndroidTestKotlin` and `:app:ktlintCheck`; `.tmp/spec-064-avd/emulator.out.log`; `.tmp/spec-064-avd/emulator.err.log`.

### Record 207: ASUS System-Back Overlay Regression Passes Instrumentation (T035 / SC-005 — Focused UI Pass)
- **requirement**: Parent T035 / SC-005; when Android system Back dismisses Transport Settings, retain the pending ceremony composition and do not dispatch Back to the underlying FIDO flow.
- **scope**: Run the current `AppShellWithOverlayTest` class on the connected ASUS. The test covers both external-request dismissal and the system-Back path, including the remembered pending-ceremony identity, shell disposal, and underlying BackHandler.
- **revision**: Branch `064-bluetooth-transport-selection`; current app test source; installed production shrunkDebug APK remained at SHA-256 `082859FCC7745587269EAC1470DB55B317F6BA04A0331E31848475A62041F6BC`.
- **named_procedure**: Build/package the Android test APK; install only `com.chimali.shrunk.test` with `adb install -r`; invoke `adb shell am instrument -w -e class com.chimali.navigation.AppShellWithOverlayTest com.chimali.shrunk.test/androidx.test.runner.AndroidJUnitRunner`; uninstall only `com.chimali.shrunk.test`; compare the production package install/update timestamps; explicitly restore the authenticator to Ready for Authentication.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11 development host; Gradle 9.6.0; 2026-11-07 local date.
- **expected_observed_result**: Instrumentation exited successfully with `OK (2 tests)` in 1.805 seconds. The test package was removed after execution. `com.chimali.shrunk` retained the same `firstInstallTime=2026-11-07 22:41:00` and `lastUpdateTime=2026-11-08 00:08:15` before and after. The test runner returned Android to the launcher and the FIDO service was stopped; explicit Start restored `Ready for Authentication` over BLE and the foreground transport service. The app's stored preference and package were not cleared or reinstalled.
- **limitations**: This validates Compose shell callback order and remembered state with an in-memory ceremony identity; it does not execute a live Windows CTAP ceremony or prove the physical gesture's end-to-end WebAuthn success. The user's Windows follow-up error in Record 203 remains inconclusive until the same-consent restoration outcome is confirmed. Parent T035 remains open.
- **status**: Passed (both focused overlay UI tests on ASUS/API35; production package timestamps unchanged and authenticator explicitly restored); Partial (physical in-flight WebAuthn/system-Back remains unresolved).
- **artifact_link**: `app/src/androidTest/kotlin/com/chimali/navigation/AppShellWithOverlayTest.kt`; `.tmp/spec-064/asus-record-207-package-before.txt`; `.tmp/spec-064/asus-record-207-package-after.txt`; `.tmp/spec-064/asus-record-207-packages-after.txt`; `.tmp/spec-064/asus-record-207-after.xml`; `.tmp/spec-064/asus-record-207-after-launch.xml`; `.tmp/spec-064/asus-record-207-restored.xml`; `.tmp/spec-064/asus-record-207-restored-service.txt`; AndroidJUnitRunner console result `OK (2 tests)`.

### Record 208: ASUS BLE and Consent-Settings Device Suites Pass (T035 / T039 — Focused Device Pass)
- **requirement**: Parent T035 / SC-005 BLE lifecycle and settings behavior; parent T039 device-test validation. Coverage and accepted-target nonregression remain separately owner-deferred.
- **scope**: Run the FIDO BLE GATT security, transport-service lifecycle and transport-settings-during-ceremony device suites on the current ASUS/API35 build. The settings suite exercises its Compose settings/ceremony UI; it does not create a real Windows WebAuthn ceremony.
- **revision**: Branch `064-bluetooth-transport-selection`; current working-tree build; production shrunkDebug package SHA-256 `082859FCC7745587269EAC1470DB55B317F6BA04A0331E31848475A62041F6BC`.
- **named_procedure**: Build `:feature:fido2:assembleAndroidDeviceTest`; stop the production authenticator through its UI and confirm Ready to Start/no foreground transport service; install only the isolated `com.chimali.fido2.test` APK; run `adb shell am instrument -w -e class com.chimali.fido2.data.transport.FidoBleSecurityDeviceTest,com.chimali.fido2.service.FidoBleLifecycleDeviceTest,com.chimali.fido2.presentation.ui.TransportSettingsDuringCeremonyTest com.chimali.fido2.test/androidx.test.runner.AndroidJUnitRunner`; uninstall only `com.chimali.fido2.test`; relaunch MainActivity and explicitly start the authenticator.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; Gradle 9.6.0; 2026-11-08 device time.
- **expected_observed_result**: AndroidJUnitRunner reported `OK (9 tests)` in 4.304 seconds: four GATT security tests, three transport-service lifecycle tests and two transport-settings-during-ceremony UI tests. The isolated test package was removed. The production package retained `firstInstallTime=2026-11-07 22:41:00` and `lastUpdateTime=2026-11-08 00:08:15`; its stored wallet was not cleared or reinstalled. The authenticator was explicitly restored to `Ready for Authentication`, and `Fido2TransportService` was foreground with `startRequested=true`.
- **limitations**: This is a focused subset, not the entire app/device suite or coverage gate. It does not validate the physical Windows WebAuthn/system-Back ceremony, discovery by a separate advertiser, Mi/API29 behavior, coverage completeness/floors or accepted-target raw-count nonregression. Parent T035 remains open for the physical in-flight system-Back and other lifecycle cases; parent T039 remains open for full validation and owner-deferred coverage.
- **status**: Passed (the three selected ASUS/API35 device suites, 9/9); Partial (full parent gate and physical ceremony acceptance remain outstanding).
- **artifact_link**: `.tmp/spec-064/asus-record-208-device-test-output.txt`; `.tmp/spec-064/asus-record-208-stopped.xml`; `.tmp/spec-064/asus-record-208-restored.xml`; `.tmp/spec-064/asus-record-208-restored-service.txt`; test APK at `feature/fido2/build/outputs/apk/androidTest/fido2-androidTest.apk`.

### Record 209: ASUS BLE Selection Persists Across Force-Stop Until Explicit Start (T035 — Partial)
- **requirement**: Parent T035; saved transport survives force-stop/relaunch, remains stopped on relaunch and starts only after explicit user action.
- **scope**: Current ASUS/API35 production package with FIDO BLE active. The app process was force-stopped and MainActivity reopened without uninstalling or clearing data.
- **revision**: Branch `064-bluetooth-transport-selection`; installed shrunkDebug APK SHA-256 `082859FCC7745587269EAC1470DB55B317F6BA04A0331E31848475A62041F6BC`.
- **named_procedure**: Capture package timestamps and Ready for Authentication; run `adb shell am force-stop com.chimali.shrunk`; confirm no transport service; launch `com.chimali.shrunk/com.chimali.MainActivity`; inspect the dashboard and Transport Settings; verify BLE is selected but not started; return and tap Start Authenticator; inspect the ready UI and foreground service.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; saved mode FIDO Bluetooth LE; 2026-11-08 device time.
- **expected_observed_result**: After relaunch the dashboard showed `Ready to Start` / `Tap 'Start Authenticator' to begin`. Transport Settings reported `Selected for this process: FIDO Bluetooth LE` and `Selected; not started`. No transport service was running. After explicit Start, the dashboard showed `Ready for Authentication` / `Waiting for request from PC`, and `Fido2TransportService` was foreground with `startRequested=true`. Production `firstInstallTime=2026-11-07 22:41:00` and `lastUpdateTime=2026-11-08 00:08:15` were identical before and after.
- **limitations**: This verifies ASUS BLE force-stop/relaunch persistence and explicit-start behavior only; it does not cover BLE host reconnection after force-stop, Classic mode on ASUS, in-flight ceremonies, Mi/API29 or remaining T035 recovery cases. Parent T035 remains open.
- **status**: Passed (ASUS BLE saved selection persisted; no automatic start; explicit start restored foreground service); Partial (remaining T035 matrix open).
- **artifact_link**: `.tmp/spec-064/asus-record-209-package-before.txt`; `.tmp/spec-064/asus-record-209-package-after.txt`; `.tmp/spec-064/asus-record-209-before-force-stop.xml`; `.tmp/spec-064/asus-record-209-after-force-stop-services.txt`; `.tmp/spec-064/asus-record-209-after-relaunch.xml`; `.tmp/spec-064/asus-record-209-after-relaunch-services.txt`; `.tmp/spec-064/asus-record-209-settings-after-relaunch.xml`; `.tmp/spec-064/asus-record-209-after-explicit-start.xml`; `.tmp/spec-064/asus-record-209-after-explicit-start-service.txt`.

### Record 210: ASUS System-Back Regression Preserves a Real Navigation Consent Entry (T035 / SC-005 — Focused UI Pass)
- **requirement**: Parent T035 / SC-005; system Back dismisses Transport Settings without popping the pending consent route from the retained Navigation Compose back stack.
- **scope**: Strengthen the shell regression to use an actual Navigation Compose `NavHost` with a pending-consent destination and assert route/entry identity after Back. This remains a focused UI test, not a live Windows CTAP ceremony.
- **revision**: Branch `064-bluetooth-transport-selection`; working-tree source at HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; production shrunkDebug installed APK hash and timestamps were unchanged.
- **named_procedure**: Run `:app:compileShrunkDebugAndroidTestKotlin :app:ktlintCheck` and `:app:assembleShrunkDebugAndroidTest`; install only `com.chimali.shrunk.test`; invoke `adb shell am instrument -w -e class com.chimali.navigation.AppShellWithOverlayTest com.chimali.shrunk.test/androidx.test.runner.AndroidJUnitRunner`; uninstall only the isolated test package; restore MainActivity and explicitly start BLE.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11 development host; Gradle 9.6.0; 2026-11-08 device time.
- **expected_observed_result**: Both `AppShellWithOverlayTest` tests passed (`OK (2 tests)`). The system-Back test kept `consent` as the current NavHost destination, preserved the exact `NavBackStackEntry` object and kept the main shell composed while closing Settings. `:app:compileShrunkDebugAndroidTestKotlin`, `:app:ktlintCheck` and test-APK assembly passed. Gradle used its non-daemon fallback after Kotlin-daemon temporary-file access failed. The test package was removed. The production app retained `firstInstallTime=2026-11-07 22:41:00` and `lastUpdateTime=2026-11-08 00:08:15`. The test runner returned the app to Ready to Start; explicit Start restored Ready for Authentication and the foreground BLE service.
- **limitations**: This exercises the real Navigation Compose back stack and app shell callback ordering but not the production MainShell/Authenticator NavHost with an active Windows WebAuthn ceremony. The physical in-flight system-Back attempt remains inconclusive in Record 203; the same-consent restoration result is still unknown. Parent T035/SC-005 remains open.
- **status**: Passed (focused ASUS/API35 Compose + Navigation regression, 2/2; app data/package retained and BLE restored); Partial (physical in-flight ceremony remains unverified).
- **artifact_link**: `app/src/androidTest/kotlin/com/chimali/navigation/AppShellWithOverlayTest.kt`; `.tmp/spec-064/asus-record-210-overlay-navhost-test.txt`; `.tmp/spec-064/asus-record-210-package-before.txt`; `.tmp/spec-064/asus-record-210-package-after.txt`; `.tmp/spec-064/asus-record-210-restored.xml`; `.tmp/spec-064/asus-record-210-restored-service.txt`.

### Record 211: ASUS Android Edge-Swipe Back Closes Idle Transport Settings (T035 / SC-005 — Partial)
- **requirement**: Parent T035 / SC-005; Android system Back dismisses Transport Settings and returns to the active authenticator without stopping its transport.
- **scope**: Current ASUS/API35 production build with BLE advertising and no FIDO ceremony active. A left-edge system Back gesture was synthesized from ADB while idle Settings was visible.
- **revision**: Branch `064-bluetooth-transport-selection`; installed shrunkDebug APK SHA-256 `082859FCC7745587269EAC1470DB55B317F6BA04A0331E31848475A62041F6BC`.
- **named_procedure**: Open Settings from the BLE dashboard; verify `Active in this process: FIDO Bluetooth LE`; execute `adb shell input swipe 1 1200 720 1200 350`; inspect the UI and `Fido2TransportService` state.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; FIDO Bluetooth LE; 2026-11-08 device time.
- **expected_observed_result**: Before the gesture, Settings showed BLE as active in this process. The edge swipe closed Settings and returned to `Ready for Authentication` / `Waiting for request from PC`. `Fido2TransportService` remained foreground with `startRequested=true`.
- **limitations**: This verifies an ADB-synthesized Android edge-swipe route while idle, not a human gesture during a live WebAuthn ceremony. It does not resolve the user's physical in-flight system-Back failure in Record 203. Parent T035/SC-005 remains open.
- **status**: Passed (idle edge-swipe dismissal with BLE service retained); Partial (in-flight ceremony route remains inconclusive).
- **artifact_link**: `.tmp/spec-064/asus-record-211-settings-before-gesture.xml`; `.tmp/spec-064/asus-record-211-after-edge-swipe.xml`; `.tmp/spec-064/asus-record-211-service.txt`.

### Record 212: ASUS Classic HID Foreground Service Restarts After Controlled Process Crash (T035 — Partial)
- **requirement**: Parent T035; both saved modes should be re-resolved and restored when Android restarts the sticky foreground transport service after process death.
- **scope**: ASUS/API35, first save Classic while BLE is process-active, restart and explicitly start Classic, then inject an app-process crash while the Classic transport service is foreground. After restart, save BLE and restore the default BLE mode.
- **revision**: Branch `064-bluetooth-transport-selection`; installed shrunkDebug APK SHA-256 `082859FCC7745587269EAC1470DB55B317F6BA04A0331E31848475A62041F6BC`.
- **named_procedure**: Save Classic in Transport Settings and observe BLE active with Classic pending; force-stop and relaunch MainActivity; confirm Classic selected/not started; tap Start Authenticator and allow Android's 120-second Bluetooth visibility prompt; verify HID Device profile and foreground service; record PID and run `adb shell am crash com.chimali.shrunk`; inspect exit history, new PID, Settings process mode, service start ID and Bluetooth HID state. Save BLE pending, force-stop/relaunch, then explicitly Start BLE and verify Ready for Authentication.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows host paired; `com.chimali.shrunk`; 2026-11-08 device time.
- **expected_observed_result**: Before crash, Classic was active with `SCAN_MODE_CONNECTABLE_DISCOVERABLE`, `HID_DEVICE` listed and the FIDO service foreground. PID changed from `20345` to `20683`; Android exit history recorded `APP CRASH(EXCEPTION)`. ActivityManager restarted `Fido2TransportService` as foreground with `startRequested=true` and `lastStartId=2`; the HID profile and connectable/discoverable scan mode were present. MainActivity showed Ready for Authentication over Bluetooth HID (Classic), and Transport Settings confirmed Classic active in this process. After saving BLE and force-stop/relaunch, BLE was selected at Ready to Start; explicit Start restored Ready for Authentication over FIDO Bluetooth LE and the foreground service.
- **limitations**: `am crash` is controlled exception injection, not natural memory-pressure process death. No Windows WebAuthn request or CTAP ceremony was active; this does not prove a host connection or in-flight recovery. Parent T035 remains open for natural process death, live consent restoration and remaining unavailable-mode cases.
- **status**: Passed (controlled Classic HID service-process restart and saved-mode re-resolution; default BLE restored); Partial (natural Android process reclamation and live ceremony recovery unverified).
- **artifact_link**: `.tmp/spec-064/asus-record-212-settings-before-classic.xml`; `.tmp/spec-064/asus-record-212-classic-pending.xml`; `.tmp/spec-064/asus-record-212-after-force-stop-services.txt`; `.tmp/spec-064/asus-record-212-after-relaunch.xml`; `.tmp/spec-064/asus-record-212-classic-not-started.xml`; `.tmp/spec-064/asus-record-212-classic-start-prompt.xml`; `.tmp/spec-064/asus-record-212-classic-active.xml`; `.tmp/spec-064/asus-record-212-classic-active-service.txt`; `.tmp/spec-064/asus-record-212-pid-before-crash.txt`; `.tmp/spec-064/asus-record-212-pid-after-crash.txt`; `.tmp/spec-064/asus-record-212-exit-info.txt`; `.tmp/spec-064/asus-record-212-services-before-crash.txt`; `.tmp/spec-064/asus-record-212-services-after-crash.txt`; `.tmp/spec-064/asus-record-212-bluetooth-before-crash.txt`; `.tmp/spec-064/asus-record-212-bluetooth-after-crash.txt`; `.tmp/spec-064/asus-record-212-after-crash-main.xml`; `.tmp/spec-064/asus-record-212-after-crash-settings.xml`; `.tmp/spec-064/asus-record-212-ble-pending-on-classic.xml`; `.tmp/spec-064/asus-record-212-ble-ready-to-start.xml`; `.tmp/spec-064/asus-record-212-final-ble.xml`; `.tmp/spec-064/asus-record-212-final-ble-service.txt`.

### Record 213: Current ASUS BLE Recovers After Advertiser Permission Revocation (T035 — Partial)
- **requirement**: Parent T035; report and recover when Android revokes the Bluetooth advertiser permission, without losing the saved BLE selection or app data.
- **scope**: Current ASUS/API35 shrunkDebug installation with BLE active; revoke and regrant only `android.permission.BLUETOOTH_ADVERTISE`, relaunch MainActivity and explicitly restart BLE.
- **revision**: Branch `064-bluetooth-transport-selection`; installed shrunkDebug APK SHA-256 `082859FCC7745587269EAC1470DB55B317F6BA04A0331E31848475A62041F6BC`.
- **named_procedure**: Capture app PID/package timestamps and foreground service; run `adb shell pm revoke com.chimali.shrunk android.permission.BLUETOOTH_ADVERTISE`; inspect permission and PID; regrant with `adb shell pm grant com.chimali.shrunk android.permission.BLUETOOTH_ADVERTISE`; launch MainActivity and inspect the selected mode; tap Start Authenticator and verify BLE ready, foreground service and advertiser.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; FIDO Bluetooth LE; 2026-11-08 device time.
- **expected_observed_result**: The permission changed from granted to denied, and the app process PID changed from `20929` to `21425`. After regrant and MainActivity launch, the app showed `Ready to Start`; BLE was not considered active until explicit Start. Start returned the UI to `Ready for Authentication` / `Waiting for request from PC`, with `Fido2TransportService` foreground and `lastStartId=3`; Bluetooth diagnostics showed the Chimali BLE advertiser registered. Production package `firstInstallTime`/`lastUpdateTime` remained unchanged.
- **limitations**: This is permission revocation while idle, not a permission loss during an active CTAP ceremony. It confirms current-build permission recovery only; parent T035 remains open for in-flight consent recovery, natural OS process death and remaining lifecycle cases.
- **status**: Passed (permission regrant, explicit BLE restart and advertiser recovery without package/data reset); Partial (in-flight permission loss and broader lifecycle matrix).
- **artifact_link**: `.tmp/spec-064/asus-record-213-pid-before-revoke.txt`; `.tmp/spec-064/asus-record-213-pid-after-revoke.txt`; `.tmp/spec-064/asus-record-213-permission-before.txt`; `.tmp/spec-064/asus-record-213-permission-after-revoke.txt`; `.tmp/spec-064/asus-record-213-permission-after-regrant.txt`; `.tmp/spec-064/asus-record-213-service-before.txt`; `.tmp/spec-064/asus-record-213-service-after-revoke.txt`; `.tmp/spec-064/asus-record-213-service-after-regrant.txt`; `.tmp/spec-064/asus-record-213-after-regrant-before-start.xml`; `.tmp/spec-064/asus-record-213-after-explicit-start.xml`; `.tmp/spec-064/asus-record-213-service-after-start.txt`; `.tmp/spec-064/asus-record-213-bluetooth-after-start.txt`.

### Record 214: ASUS Classic Radio-Off Retry Leaves Dashboard and Process Status Inconsistent (T035 — Inconclusive)
- **requirement**: Parent T035; after the selected Classic HID transport becomes unavailable, it must report the failure and recover consistently when Bluetooth is restored and the user retries.
- **scope**: ASUS/API35, Classic HID selected and explicitly started; Bluetooth was disabled, the Android visibility request was denied while the radio was off, then Bluetooth was restored and Retry was used.
- **revision**: Branch `064-bluetooth-transport-selection`; installed shrunkDebug APK SHA-256 `082859FCC7745587269EAC1470DB55B317F6BA04A0331E31848475A62041F6BC`; app data retained.
- **named_procedure**: Save Classic while BLE remains active; force-stop/relaunch and explicitly start Classic; disable Bluetooth; request Start while the radio is off and deny Android's enable/visibility request; restore Bluetooth; tap Retry and allow the visibility request; inspect the dashboard, Settings, service and Bluetooth HID profile.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; Windows 11 host; 2026-11-08 device time.
- **expected_observed_result**: With Bluetooth off, Android displayed the system enable/visibility request. Denying it produced Chimali's `Bluetooth Required` guidance. After Bluetooth was restored, Retry and the Android visibility request completed; the FIDO foreground service remained active and Bluetooth diagnostics showed `HID_DEVICE` plus connectable/discoverable mode. However, the dashboard showed `Ready to Start` while Transport Settings reported `Bluetooth HID (Classic)` active in this process. The UI/service/profile state therefore did not converge to a single recovered-ready state.
- **limitations**: No Windows ceremony was active, and no remote host connection was tested. The inconsistent post-retry status leaves Classic recovery unverified; investigate why the home connection state remains Idle while process selection reports Ready before claiming recovery. BLE was restored after capturing this result.
- **status**: Inconclusive (radio restore and HID profile were observed, but dashboard and process state disagreed).
- **artifact_link**: `.tmp/spec-064/asus-record-214-before.xml`; `.tmp/spec-064/asus-record-214-settings.xml`; `.tmp/spec-064/asus-record-214-classic-pending.xml`; `.tmp/spec-064/asus-record-214-after-force-stop.txt`; `.tmp/spec-064/asus-record-214-ready-to-start.xml`; `.tmp/spec-064/asus-record-214-start-prompt.xml`; `.tmp/spec-064/asus-record-214-current.xml`; `.tmp/spec-064/asus-record-214-classic-active-before-radio-off.xml`; `.tmp/spec-064/asus-record-214-classic-service-before-radio-off.txt`; `.tmp/spec-064/asus-record-214-radio-off-setting.txt`; `.tmp/spec-064/asus-record-214-radio-off.xml`; `.tmp/spec-064/asus-record-214-current-radio-off.xml`; `.tmp/spec-064/asus-record-214-settings-radio-off.xml`; `.tmp/spec-064/asus-record-214-classic-start-radio-off.xml`; `.tmp/spec-064/asus-record-214-after-deny-start.xml`; `.tmp/spec-064/asus-record-214-radio-restored-setting.txt`; `.tmp/spec-064/asus-record-214-after-radio-restored-before-retry.xml`; `.tmp/spec-064/asus-record-214-after-retry.xml`; `.tmp/spec-064/asus-record-214-after-retry-allow.xml`; `.tmp/spec-064/asus-record-214-final-classic.xml`; `.tmp/spec-064/asus-record-214-startup-logcat.txt`; `.tmp/spec-064/asus-record-214-settings-after-retry.xml`; `.tmp/spec-064/asus-system-back-ble-pending.xml`; `.tmp/spec-064/asus-system-back-ble-pending-return.xml`; `.tmp/spec-064/asus-system-back-ble-after-relaunch.xml`; `.tmp/spec-064/asus-system-back-ble-restored.xml`.

### Record 215: ASUS System-Back Authentication Attempt Ends with Windows Follow-Up Error (T035 / SC-005 — Inconclusive)
- **requirement**: Parent T035 / SC-005; Android system Back from Transport Settings during an active authentication must restore the same pending consent and permit completion.
- **scope**: User-reported in-flight WebAuthn authentication attempt on ASUS after invoking Android system Back. The user reported Windows `No se pudo iniciar sesión. Vuelve a intentarlo y asegúrate de completar la acción de seguimiento`.
- **revision**: Branch `064-bluetooth-transport-selection`; installed shrunkDebug APK SHA-256 `082859FCC7745587269EAC1470DB55B317F6BA04A0331E31848475A62041F6BC`; no app data or transport selection was changed during inspection.
- **named_procedure**: During WebAuthn authentication, open Chimali Transport Settings and use Android system Back; inspect the Windows result, ASUS foreground UI, service state and recent logcat.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11; 2026-11-08 local date.
- **expected_observed_result**: Windows reported the follow-up failure and the ceremony did not succeed. The subsequent ASUS snapshot showed `Ready to Start`; `Fido2TransportService` was still foreground with `startRequested=true` and `lastStartId=3`. The sampled logcat had no correlated CTAP request/result. The same-consent restoration outcome has not yet been confirmed.
- **limitations**: The error establishes a failed ceremony but does not identify whether Android system Back dismissed the pending consent, the authenticator stopped processing, or Windows failed another follow-up step. This neither proves nor disproves the overlay BackHandler correction. Physical in-flight consent restoration and successful authentication remain unverified; parent T035/SC-005 stays open.
- **status**: Failed (user-reported WebAuthn ceremony); Inconclusive (exact Back route and consent restoration).
- **artifact_link**: `.tmp/spec-064/asus-system-back-failure.xml`; live `adb shell dumpsys activity services com.chimali.shrunk` output; live logcat sample from the same inspection.

### Record 216: ASUS Classic Radio-Off Retry Now Restores the Active Dashboard State (T035 — Partial Pass)
- **requirement**: Parent T035; a selected transport that loses its live connection must expose recovery and retry the frozen choice when it becomes available again.
- **scope**: Source correction for the idle-connection/ready-process mismatch found in Record 214; focused host validation and current-candidate ASUS/API35 Classic radio-off recovery. No Windows ceremony was active.
- **revision**: Branch `064-bluetooth-transport-selection`; ASUS radio-off recovery was tested on APK SHA-256 `9B1BD4F1AF784445F01C820B423EDDDC3B513E45D29569D320BF08B646DAFC79`, installed in place with app data preserved. After the final Compose parameter-order adjustment, the final source was rebuilt and installed in place as APK SHA-256 `C52D54E35A5211276CF14D53634FCB23643468068A36BE2367815084595AEFB7`; the app was explicitly started on BLE and returned to Ready for Authentication. `firstInstallTime` remained `2026-11-07 22:41:00` across updates.
- **named_procedure**: Map `Idle` plus process `Ready` to Retry; surface the mismatched state as retryable on the dashboard; run `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.presentation.viewmodel.Fido2HomeViewModelTest :feature:fido2:ktlintCheck`; assemble/install `:app:assembleShrunkDebug`; on ASUS save Classic, force-stop/relaunch and explicitly start; disable Bluetooth; invoke Retry and accept Android's request to enable Bluetooth and grant 120-second visibility; inspect UI, service and HID profile. Then save BLE, force-stop/relaunch, explicitly start and verify BLE recovery.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; Gradle 9.6.0; 2026-11-08 device time.
- **expected_observed_result**: Focused `Fido2HomeViewModelTest` passed 2/2 with zero failures/errors/skips. Repository local CI passed KtLint, Detekt, release lint, comprehensive compilation, unit tests and configured Android host tests in 196.52 seconds after the Compose parameter-order correction; coverage was not run. ShrunkDebug assembly passed. On the ASUS radio-off test build, Classic started in the foreground and showed Ready for Authentication. Bluetooth-off changed the dashboard to `Error Occurred` with `Retry Authenticator` and the message `Transport is not reporting an active connection; retry to restore it`. Accepting Android's Bluetooth/visibility request re-enabled the radio and restored `Ready for Authentication`; the foreground service was active, Bluetooth showed `SCAN_MODE_CONNECTABLE_DISCOVERABLE`, and `HID_DEVICE` was registered. BLE was then saved, applied after force-stop/relaunch and explicitly started; the final in-place-installed build returned to BLE `Ready for Authentication` with its foreground service active.
- **limitations**: This verifies the app-side retry path and Classic HID service/profile recovery on ASUS/API35, but does not confirm Windows reconnection or a WebAuthn ceremony after radio recovery. It does not resolve the separate physical in-flight system-Back failure in Record 215, natural OS resource reclamation, Mi/API29 Classic behavior, or owner-deferred coverage/nonregression.
- **status**: Passed (focused action mapping and ASUS Classic radio-off/retry recovery; default BLE restored); Partial (host ceremony and remaining T035/T039 acceptance outstanding).
- **artifact_link**: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/Fido2HomeViewModel.kt`; `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/Fido2HomeScreen.kt`; `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/presentation/viewmodel/Fido2HomeViewModelTest.kt`; `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.presentation.viewmodel.Fido2HomeViewModelTest.xml`; `app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk` (final installed SHA-256 `C52D54E35A5211276CF14D53634FCB23643468068A36BE2367815084595AEFB7`); `.tmp/spec-064/asus-classic-retest-settings.xml`; `.tmp/spec-064/asus-classic-retest-start.xml`; `.tmp/spec-064/asus-classic-retest-prompt.xml`; `.tmp/spec-064/asus-classic-retest-active.xml`; `.tmp/spec-064/asus-classic-off-after-fix.xml`; `.tmp/spec-064/asus-classic-retry-radio-off-prompt.xml`; `.tmp/spec-064/asus-classic-retry-after-radio-on.xml`; `.tmp/spec-064/asus-classic-restore-settings.xml`; `.tmp/spec-064/asus-final-ble-start.xml`; `.tmp/spec-064/asus-final-ble-ready.xml`; `.tmp/spec-064/asus-final-build-ready.xml`.

### Record 217: ASUS BLE Authentication Succeeds After Physical System-Back Retry (T035 / SC-005 — Partial Pass)
- **requirement**: Parent T035 / SC-005; opening Transport Settings during a live authentication and dismissing them with Android system Back must allow the pending authentication to resume and complete.
- **scope**: User repeated the ASUS physical in-flight system-Back procedure on the final installed build and reported `it works`. Integrated WebAuthn.io showed `You're logged in!` for `codex-synthetic-064-20261103`; Android recorded successful BLE GetAssertion and response acknowledgement.
- **revision**: Branch `064-bluetooth-transport-selection`; installed shrunkDebug APK SHA-256 `C52D54E35A5211276CF14D53634FCB23643468068A36BE2367815084595AEFB7`; package updated in place, with app data retained.
- **named_procedure**: Start WebAuthn authentication for the synthetic account; select the Bluetooth security key in Windows; open Chimali Transport Settings during the pending authentication; use Android system Back and continue the ASUS verification flow; inspect WebAuthn.io and Android's BLE diagnostics.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11; integrated WebAuthn.io; FIDO Bluetooth LE; 2026-11-08 local date.
- **expected_observed_result**: The user reported the retry worked. WebAuthn.io displayed `You're logged in!` and the credential list remained scoped to the synthetic account. Android logcat recorded an admitted GetAssertion, `Assertion completed`, `Assertion response ready`, and `FIDO BLE diagnostic completed operation=GetAssertion` with `terminalNotificationAcknowledged=true`; Android biometric scheduling reported success for Chimali. Afterward ASUS showed `Ready for Authentication` / `Waiting for request from PC`, with `Fido2TransportService` foreground and started.
- **limitations**: The phone's consent prompt and Back transition were not screen-recorded, and the log does not identify the Settings Back event itself. The user's response and successful WebAuthn result support the physical route working on this retry; the prior failed attempt remains in Record 215. Credential IDs are masked in WebAuthn.io, so the exact credential used is unknown. Other lifecycle cases and owner-deferred coverage/nonregression remain open.
- **status**: Passed (user-reported physical system-Back retry followed by WebAuthn success and acknowledged BLE assertion); Partial (same-consent screen transition not directly captured, exact credential identity unknown, broader T035/T039 remain open).
- **artifact_link**: `.tmp/spec-064/asus-system-back-success.xml`; integrated browser state at `https://webauthn.io/profile` showing `You're logged in!`; same-window Android logcat sample; `adb shell dumpsys activity services com.chimali.shrunk` output.

### Record 218: ASUS Classic Selection Survives Force-Stop and Explicit Start (T035 — Partial Pass)
- **requirement**: Parent T035; both transport selections persist across force-stop/relaunch, remain stopped until explicit user action, then activate the saved mode.
- **scope**: ASUS/API35 Classic HID force-stop/relaunch subcase. Existing wallet and passkeys were preserved; no package reinstall or data clear occurred.
- **revision**: Branch `064-bluetooth-transport-selection`; working-tree revision `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; installed package `firstInstallTime=2026-11-07 22:41:00`, `lastUpdateTime=2026-11-08 08:09:46`.
- **named_procedure**: Save Bluetooth HID (Classic) in Chimali Transport Settings while BLE is active; return to the authenticator; force-stop `com.chimali.shrunk`; relaunch; verify saved Classic and no transport service before Start; tap Start Authenticator; allow Android's 120-second visibility request; inspect dashboard, foreground service and HID profile. Then restore BLE through Settings, force-stop/relaunch, explicitly start, and verify BLE Ready for Authentication.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; 2026-11-08 local date.
- **expected_observed_result**: After Classic relaunch, the dashboard showed Ready to Start and `FIDO2 / WebAuthn over Bluetooth HID`, with no service started. Explicit Start requested visibility permission; after Allow, the dashboard showed Ready for Authentication, `Fido2TransportService` was foreground with `startRequested=true`, and Android listed `HID_DEVICE`. BLE was subsequently restored, force-stop/relaunched, explicitly started, and returned to Ready for Authentication over FIDO Bluetooth LE with the foreground service active.
- **limitations**: Verifies Classic saved-choice persistence/no-auto-start and explicit start on ASUS/API35. It does not establish natural OS resource reclamation, Mi reboot, live ceremony switching, Windows HID reconnection or the remaining unavailable-mode cases; T035 remains open. Local shrunkDebug APK was not available in this worktree, so no APK digest is asserted.
- **status**: Passed (ASUS Classic force-stop/relaunch and explicit activation; BLE restored afterward); Partial (broader T035 lifecycle matrix remains open).
- **artifact_link**: `.tmp/spec-064/asus-classic-force-stop.txt`; `.tmp/spec-064/asus-final.png`; device UI hierarchy `/sdcard/window.xml` at test time; `adb shell dumpsys activity services com.chimali.shrunk`; `adb shell dumpsys bluetooth_manager`.

### Record 219: Full FIDO2 Connected Suite Partially Executes Without Required External Fixtures (T039 — Unverified Prerequisites)
- **requirement**: Parent T039; execute required connected FIDO2 device tests and record actual outcomes and environmental limitations.
- **scope**: Direct `:feature:fido2:connectedAndroidDeviceTest` run on ASUS/API35. This was a connected device-test run only; no coverage aggregation or accepted-target comparison was invoked.
- **revision**: Branch `064-bluetooth-transport-selection`; working-tree revision `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; Gradle 9.6.0 with isolated `GRADLE_USER_HOME=.tmp/gradle-home`.
- **named_procedure**: Run `:feature:fido2:connectedAndroidDeviceTest` against the attached ASUS; parse the produced XML/HTML reports; inspect test precondition failures; verify the production app state afterward.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows development host; 2026-11-08 local date.
- **expected_observed_result**: 63 tests executed: 56 passed, 7 failed, 0 errors and 0 skipped. Failures were one advertiser-discovery check with zero nearby BLE results (requires a distinct remote FIDO advertiser), one 50-cycle reconnect stress check lacking `runFidoBleReconnectStress=true` and its separate advertiser, and five performance/trace checks requiring configured Perfetto tracing / `atrace_apps`. ASUS production app remained on BLE Ready for Authentication with the foreground service active. No coverage aggregate was produced.
- **limitations**: This run does not pass the complete connected suite. The three external-fixture classes remain unverified until a second advertiser and configured tracing are available and the stress test is explicitly opted in. Coverage completeness/floors and accepted-target nonregression remain owner-deferred; parent T039 remains open.
- **status**: Partial (56 tests passed); Unverified (seven environment/setup-dependent failures, coverage and nonregression).
- **artifact_link**: `.tmp/spec-064/asus-full-connected-device-suite-2026-11-08.txt`; `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302 - 15.xml`; `feature/fido2/build/reports/androidTests/connected/androidMain/index.html`.

### Record 220: ASUS Classic GetAssertion Completes with BLE Saved Pending During Consent (T035 / SC-005 — Passed)
- **requirement**: Parent T035 / SC-005; saving the alternate mode during an active ceremony must not change the in-process transport or prevent the current ceremony from completing; the saved choice applies after process restart.
- **scope**: ASUS/API35 physical Windows WebAuthn GetAssertion over Classic HID. During the active sign-in prompt, BLE was saved while Classic remained active; Android system Back returned to the same sign-in prompt, which then completed.
- **revision**: Branch `064-bluetooth-transport-selection`; working-tree revision `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; installed application package and app data were preserved (`firstInstallTime=2026-11-07 22:41:00`, `lastUpdateTime=2026-11-08 08:09:46`).
- **named_procedure**: Start WebAuthn.io Authenticate for `codex-synthetic-064-20261103`; select the Bluetooth security key in Windows; wait for ASUS `Sign in with Passkey`; open Chimali Transport Settings; save FIDO Bluetooth LE while Settings reports Classic active; use Android system Back; confirm the same sign-in prompt returns; complete device verification; inspect WebAuthn.io and Android CTAP logs. Then force-stop/relaunch the app, verify BLE is selected and stopped, explicitly Start BLE, and inspect UI/service state.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11; integrated WebAuthn.io; synthetic account; 2026-11-08 local date.
- **expected_observed_result**: Settings showed `Active in this process: Bluetooth HID (Classic)` and `A restart is required to use the saved choice` after BLE was selected. Android system Back returned to the same `Sign in with Passkey` UI. The user completed verification; WebAuthn.io displayed `You're logged in!`, Android logged `Assertion completed` and `Assertion response ready`, and the ASUS showed `Connected to PC` over Classic. After force-stop/relaunch, BLE was selected but stopped until explicit Start; Start restored `Ready for Authentication`, `FIDO2 / WebAuthn over FIDO Bluetooth LE`, and the foreground transport service. App install/update timestamps were unchanged.
- **limitations**: This verifies the physical Classic-active/BLE-pending authentication direction on ASUS/API35 and the system-Back return for that attempt. It does not verify the converse in-flight save direction beyond Record 190's Settings-Back registration, exact credential identity, Mi/API29, natural OS process reclamation or the owner-deferred coverage/nonregression gate. T035 remains open for its remaining matrix.
- **status**: Passed (Classic GetAssertion succeeded with BLE saved pending; same sign-in prompt returned after system Back; BLE applied after restart); Partial (broader T035 lifecycle matrix remains open).
- **artifact_link**: `.tmp/spec-064/asus-classic-auth-ble-pending.log`; `.tmp/spec-064/asus-final-ble.xml`; `.tmp/spec-064/asus-final-ble-service.txt`; integrated WebAuthn.io profile showed `You're logged in!` for the synthetic account.

### Record 221: Mi Remote Advertisement Discovery and 50-Cycle BLE Reconnect Pass (T035 / T039 — Partial Pass)
- **requirement**: Parent T035 lifecycle/recovery and T039 connected device coverage; verify a second Android's FIDO service advertisement and repeated GATT reconnects against the FIDO BLE service.
- **scope**: Two-device ASUS/API35 scanner and Mi A2/API29 advertiser. ASUS BLE stayed Ready for Authentication; Mi BLE was actively advertising. The opt-in reconnect stress test ran only on ASUS. A reboot was issued after the test; post-reboot Mi state is unverified because ADB disconnected during boot.
- **revision**: Branch `064-bluetooth-transport-selection`; working-tree revision `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; instrumentation APK built from this worktree.
- **named_procedure**: On Mi, open the existing Chimali install and start the saved BLE transport. On ASUS run `FidoBleAdvertiserDiscoveryDeviceTest`; then run `FidoBleReconnectStressDeviceTest` with `runFidoBleReconnectStress=true`. Capture the 50-cycle memory readings. Reboot Mi and inspect its post-boot saved transport and explicit-start behavior.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Mi A2 / Android 10 API29 / serial `2ac6876`; Windows 11; 2026-11-08.
- **expected_observed_result**: Mi showed `Advertising...` with its foreground service active; ASUS discovered a distinct remote FIDO advertisement and the test passed `OK (1 test)`. The reconnect stress test passed `OK (1 test)` in 31.834 seconds after 50 GATT discover/disconnect/reconnect cycles. Java heap changed from 6,611,488 to 3,240,480 bytes; native heap changed from 8,729,888 to 8,466,352 bytes. Mi briefly reappeared after reboot but then disappeared from ADB; subsequent 30-second polling showed only ASUS.
- **limitations**: Remote advertisement discovery and 50-cycle stress are verified for this ASUS↔Mi pair. Mi reboot persistence is unverified; no post-boot selection/service state was available. This does not close parent T035 or coverage/nonregression in T039.
- **status**: Passed (remote discovery and 50-cycle stress); Unverified (Mi post-reboot lifecycle state, full suite prerequisites and coverage/nonregression).
- **artifact_link**: `.tmp/spec-064/mi-two-device-tests-2026-11-08.txt`; connected test runner output and filtered Android logcat recorded in the same file.

### Record 222: Mi BLE Selection Persists Across Reboot and Advertising Resumes After Explicit Start (T035 — Passed Scoped Check)
- **requirement**: Parent T035 / SC-004; preserve the selected BLE transport across reboot, keep the transport stopped until explicit Start, and verify Start restores advertising.
- **scope**: Mi A2/API29 after the reboot recorded as unavailable in parent Record 200 and the ADB loss noted in Record 221. Reconnect without reinstalling or clearing package data; inspect the selected transport and service state, then explicitly start the authenticator.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus existing staged/unstaged changes; package `com.chimali.shrunk` version 0.1.0.
- **named_procedure**: Reconnect the unlocked Mi to ADB; inspect package identity and running services; launch MainActivity; capture Ready to Start; tap Start Authenticator; inspect UI, foreground service and Bluetooth Manager registration.
- **environment**: Mi A2 / Android 10 API29 / serial `2ac6876`; Windows 11; 2026-11-08 local date.
- **expected_observed_result**: Package first-install and last-update times remained `2026-11-07 15:53:12` and `2026-11-07 16:28:51`. Before Start, MainActivity showed `Ready to Start` and `FIDO2 / WebAuthn over FIDO Bluetooth LE`; `Fido2TransportService` was not running. After Start, the UI showed `Advertising...` / `Ready to pair with a new host PC`, the FIDO service was foreground with `startRequested=true`, and Bluetooth Manager listed `com.chimali.shrunk (Registered)`.
- **limitations**: This verifies the Mi BLE reboot-selection and explicit-start subcheck only. It does not cover a Windows ceremony on Mi, natural OS process reclamation, or the complete T035 matrix.
- **status**: Passed (BLE selection persisted across reboot and advertising resumed only after explicit Start on Mi/API29); Partial (full T035 lifecycle acceptance remains open).
- **artifact_link**: `.tmp/spec-064/mi-postboot-check-2026-11-08.txt`; `.tmp/spec-064/mi-postboot.png` (Ready to Start); `.tmp/spec-064/mi-postboot-after-start.png` (Advertising).

### Record 223: Mi Classic HID Startup Timeout Shows Actionable Dashboard Error and BLE Recovers (T035 — Passed Scoped Check)
- **requirement**: Parent T035 / FR-BT-SEL-050 / TR-BT-SEL-020; expose startup failure and recovery for an unavailable selected transport.
- **scope**: Mi A2/API29 current candidate after the prior Classic timeout had only been visible in Transport Settings; in-place update, Classic start failure reproduction, then saved BLE explicit-start recovery. App data retained.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus existing staged/unstaged changes; shrunkDebug APK SHA-256 `14E005B44A5D9762F91FF428AFE72FA38E6AC7EA5FD2A595008ABA3E92CD7E29`.
- **named_procedure**: Build and install shrunkDebug in place; select Classic; force-stop/relaunch; grant Android's 120-second visibility consent and start; inspect dashboard, Settings and logcat; select BLE, force-stop/relaunch, explicitly start, and inspect the service and Bluetooth registration.
- **environment**: Mi A2 / Android 10 API29 / serial `2ac6876`; Windows development host; Gradle 9.6.0; 2026-11-08.
- **expected_observed_result**: Focused `Fido2HomeViewModelTest` passed 3/3 with zero failures/errors. Android could not bind `android.bluetooth.IBluetoothHidDevice`; HID startup timed out after 5 seconds. The dashboard displayed `Error Occurred`, the reason-specific initialization-timeout guidance and `Retry Authenticator`; Settings showed `Startup timed out`. After saving BLE and restarting, explicit Start restored `Advertising...`, the foreground FIDO transport service, and Bluetooth Manager registration for `com.chimali.shrunk`.
- **limitations**: Classic HID cannot start on this Android 10 device due to the unavailable HID service, so no Classic Windows pairing or ceremony was attempted. This verifies failure reporting and BLE recovery, not Classic availability or the remaining T035 matrix. Coverage and accepted-target nonregression remain owner-deferred.
- **status**: Passed (actionable failure shown and BLE restored); Partial (Classic unavailable on Mi/API29; remaining T035 cases open).
- **artifact_link**: `.tmp/spec-064/mi-classic-dashboard-error.png`; `.tmp/spec-064/mi-settings-classic-error.png`; `.tmp/spec-064/mi-classic-startup-result.png`; `.tmp/spec-064/mi-classic-timeout-log.txt`; `.tmp/spec-064/mi-ble-restored-after-classic-failure.png`.

### Record 224: Repository Non-Coverage Local CI Passes After Dashboard Failure-Guidance Fix (T039 — Partial)
- **requirement**: Parent T039; run required local static analysis, compilation and host/unit tests, while keeping coverage/accepted-target results distinct.
- **scope**: Current worktree after transport failure guidance changes; ordinary local CI without coverage or connected device suites.
- **revision**: Branch `064-bluetooth-transport-selection`; HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; staged and unstaged changes remain, so this is not clean-candidate integration evidence.
- **named_procedure**: `pwsh -File tools/local-ci.ps1 -NoFix -NoConfigurationCache` with `GRADLE_USER_HOME=.tmp/gradle-home`.
- **environment**: Windows development host; Gradle 9.6.0; 2026-11-08 local date.
- **expected_observed_result**: Script reported PASS in 206.05 seconds. KtLint, Detekt, Release lint, comprehensive compilation, unit tests and KMP Android host tests passed. Fresh app/core/feature test XML comprised 157 reports with 1,156 tests, zero failures, zero errors and zero skips.
- **limitations**: `-RunCoverage` was not specified; no connected-device suite, coverage floor/completeness measurement, trusted accepted-target comparison or hosted check ran. Current source state is dirty and cannot establish clean-candidate coverage/nonregression. Parent T039 remains open.
- **status**: Passed (non-coverage local CI); Unverified (coverage completeness/floors, accepted-target nonregression and hosted acceptance).
- **artifact_link**: `.tmp/spec-064/local-ci-2026-11-08.txt`; Gradle test XML under `app/build/test-results`, `core/*/build/test-results`, and `feature/*/build/test-results`.

### Record 225: Mi/API29 BLE Radio-Off Retry Restores Advertising on Current Candidate (T035 — Passed Scoped Check)
- **requirement**: Parent T035 / FR-BT-SEL-050; make radio loss visible and restore the selected transport after Bluetooth becomes available.
- **scope**: Mi A2/API29 current shrunkDebug candidate, BLE advertising, Android Quick Settings Bluetooth radio-off/on, then Retry; production package data retained.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; current installed shrunkDebug candidate.
- **named_procedure**: Confirm BLE Advertising and foreground service; turn Bluetooth off through the Android Quick Settings tile; reopen Chimali and inspect status; restore Bluetooth through the same tile; tap Retry Authenticator and inspect the UI, service and Bluetooth Manager.
- **environment**: Mi A2 / Android 10 API29 / serial `2ac6876`; 2026-11-08 device time.
- **expected_observed_result**: When Bluetooth global setting was `0`, dashboard showed `Error Occurred`, `Bluetooth is off; restore Bluetooth and retry`, and Retry Authenticator. After restoring Bluetooth (`1`) and tapping Retry, the dashboard returned to `Advertising...`; the FIDO foreground service was active with `startRequested=true`, and Bluetooth Manager registered `com.chimali.shrunk`. Logcat showed the explicit `RETRY_TRANSPORT` service intent.
- **limitations**: No Windows host connection, WebAuthn ceremony or in-flight request was active. Shell-based `svc bluetooth disable` was denied, so the radio was toggled through Android Quick Settings. This closes one Mi idle radio-off subcase only; T035 and the owner-deferred T039 coverage/nonregression gate remain open.
- **status**: Passed (Mi BLE radio-off error and Retry recovery); Partial (broader lifecycle/recovery scope remains open).
- **artifact_link**: `.tmp/spec-064/mi-ble-radio-recovery-2026-11-08.txt`; `.tmp/spec-064/mi-ble-radio-recovery-logcat-2026-11-08.txt`; `.tmp/spec-064/mi-bt-off-main-current.png`; `.tmp/spec-064/mi-bt-retry-result.png`.

### Record 226: Every Typed Transport-Unavailable Reason Shows Guidance and Retry (T035 / T027 — Passed Host Check)
- **requirement**: Parent T035 / FR-BT-SEL-050 / TR-BT-SEL-020; transport unavailability must be visible and recoverable.
- **scope**: Deterministic dashboard state and Retry-action mapping for every `TransportUnavailableReason`; host tests only.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes.
- **named_procedure**: `:feature:fido2:ktlintAndroidHostTestSourceSetCheck :feature:fido2:testAndroidHostTest --tests com.chimali.fido2.presentation.viewmodel.Fido2HomeViewModelTest`, with Gradle 9.6.0 and in-process Kotlin compiler.
- **environment**: Windows development host; 2026-11-08 local date.
- **expected_observed_result**: Build passed; focused ViewModel tests passed 3/3 with zero failures/errors/skips. The regression asserts distinct guidance and Retry for PreferenceReadFailed, InvalidPreference, InitializationTimeout, Unsupported, PermissionRequired, BluetoothOff and StartupFailed.
- **limitations**: This confirms deterministic UI state mapping, not all physical unavailable modes or the complete T035 lifecycle matrix. Parent T035 remains open.
- **status**: Passed (typed error guidance and Retry mapping); Partial (physical T035 matrix remains open).
- **artifact_link**: `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.presentation.viewmodel.Fido2HomeViewModelTest.xml`.

### Record 227: SC-005 Physical In-Flight Transport-Change Matrix Reconciled (T041 — Partial)
- **requirement**: Parent T041 / SC-005; for registration and authentication on each transport, a concurrent saved-choice change must not interrupt the in-flight ceremony or replace its process-active transport.
- **scope**: Reconcile the four physical combinations against the immutable acceptance matrix in Record 181, later physical evidence and the deterministic T032 host suite. No new ceremony was started for this reconciliation.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus existing staged/unstaged changes; ASUS current candidate retained app data.
- **named_procedure**: Review parent Records 190, 183–185, 220 and the T032 host results recorded in Records 165/172; enumerate BLE/Classic registration and authentication separately, requiring an alternate saved preference during the active physical ceremony for each physical pass.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11; integrated WebAuthn.io; synthetic account `codex-synthetic-064-20261103`; evidence reviewed 2026-11-08.
- **expected_observed_result**:

| Physical ceremony | Result | Evidence |
|---|---|---|
| BLE MakeCredential with Classic saved pending | Passed | Same consent returned through Settings Back and registration completed successfully; Record 190. |
| BLE GetAssertion with Classic saved pending | Unverified | Earlier attempts acknowledged assertion responses but did not establish successful login while Classic was pending; Records 183–185. |
| Classic MakeCredential with BLE saved pending | Unverified | No physical ceremony evidence found. |
| Classic GetAssertion with BLE saved pending | Passed | Same sign-in prompt returned after Android system Back; user verification and GetAssertion completed with WebAuthn.io login success; Record 220. |

The deterministic T032 host tests cover registration and authentication on each transport. They do not substitute for the two missing physical combinations.
- **limitations**: Only two of four physical combinations are verified. The BLE registration case used the in-app Settings Back action; the Classic authentication case used Android system Back. SC-005 remains partial, and parent T035 remains open for the broader lifecycle/unavailable-mode matrix, including natural OS resource reclamation. Parent T039 and BLE T038 coverage/nonregression remain owner-deferred.
- **status**: Partial (two physical combinations passed; two unverified; deterministic host matrix passes).
- **artifact_link**: `.tmp/spec-064/asus-classic-auth-ble-pending.log`; WebAuthn.io profile and authentication success for Record 220; `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/TransportSelectionCeremonyTest.kt`.

### Record 228: BLE GetAssertion Succeeds with Classic Saved Pending (T035 / SC-005 — Passed Scoped Check)
- **requirement**: Parent T035 / SC-005; a saved transport change during an active ceremony must not replace the original process transport or prevent the ceremony from completing.
- **scope**: Physical BLE GetAssertion on ASUS/API35 with Classic saved pending during the ceremony; BLE remained active before and after the assertion.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged/unstaged changes; installed package `firstInstallTime=2026-11-07 22:41:00`, `lastUpdateTime=2026-11-08 08:09:46`.
- **named_procedure**: Confirm Settings shows BLE active and Classic saved with restart required; start Authenticate for the synthetic WebAuthn.io account, complete the Bluetooth-key flow and ASUS user verification; inspect the browser result, GetAssertion diagnostics and post-ceremony saved/active settings.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11; integrated WebAuthn.io; synthetic account `codex-synthetic-064-20261103`; 2026-11-08.
- **expected_observed_result**: WebAuthn.io showed `You're logged in!`. Android admitted GetAssertion with one allowed credential, completed the assertion after user verification, produced the response and logged `terminalNotificationAcknowledged=true` (elapsed 44,496 ms). Transport Settings afterward still showed Classic selected and `Active in this process: FIDO Bluetooth LE`; the BLE foreground service remained available and Bluetooth Manager listed `com.chimali.shrunk (Registered)`.
- **limitations**: This verifies BLE authentication with Classic pending. It does not verify Classic MakeCredential with BLE pending, exact remote credential identity, or other open T035 lifecycle cases. Parent T039 and BLE T038 coverage/nonregression remain owner-deferred.
- **status**: Passed (BLE GetAssertion completed with Classic pending); Partial (the remaining physical SC-005 combination and broader T035 matrix remain open).
- **artifact_link**: `.tmp/spec-064/asus-ble-auth-classic-pending-2026-11-08.log`; `.tmp/spec-064/asus-pending-classic.xml`; `.tmp/spec-064/asus-settings-result.xml`; WebAuthn.io profile for the synthetic account.

### Record 229: Classic MakeCredential Succeeds with BLE Saved Pending (T035 / SC-005 — Passed Scoped Check)
- **requirement**: Parent T035 / SC-005; a saved transport change during an active ceremony must not replace the original process transport or prevent the ceremony from completing.
- **scope**: Physical Classic HID MakeCredential on ASUS/API35 with FIDO Bluetooth LE saved pending during the ceremony; Classic remained process-active before and after registration.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged/unstaged changes; installed package `firstInstallTime=2026-11-07 22:41:00`, `lastUpdateTime=2026-11-08 08:09:46`.
- **named_procedure**: Save BLE in Chimali Transport Settings while Classic is active and confirm restart required; start Register for the synthetic WebAuthn.io account; complete Windows Bluetooth-key selection and ASUS user verification; inspect browser result, HID diagnostics and post-ceremony transport state.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11; integrated WebAuthn.io; synthetic account `codex-synthetic-064-20261103`; 2026-11-08.
- **expected_observed_result**: WebAuthn.io displayed `Success! Now try to authenticate...`. Android logged an admitted MakeCredential request, created a 32-byte credential ID, emitted a 218-byte CBOR response and reported `HID reports accepted: true`. Transport Settings afterward still showed BLE saved with restart required and `Active in this process: Bluetooth HID (Classic)`; Android continued to list `HID_DEVICE` and the FIDO foreground service.
- **limitations**: This verifies Classic registration with BLE pending. It does not map the remote credential ID to the local database/event row or close remaining T035 lifecycle/recovery cases. Parent T039 and BLE T038 coverage/nonregression are evaluated separately.
- **status**: Passed (Classic MakeCredential completed with BLE pending); Partial (other T035 lifecycle/recovery checks remain open).
- **artifact_link**: `.tmp/spec-064/asus-classic-reg-ble-pending-2026-11-08.log`; `.tmp/spec-064/asus-classic-ble-pending.xml`; `.tmp/spec-064/asus-settings-after-registration.xml`; WebAuthn.io registration success for the synthetic account.

### Record 230: Authorized Coverage Gate Attempt Did Not Complete (T039 — Unverified)
- **requirement**: Parent T039; local static/host/device validation, complete coverage/floor assessment and candidate-versus-current-accepted-target raw-count nonregression.
- **scope**: Current staged/unstaged worktree; local CI with coverage enabled and connected FIDO2 device suites on both attached phones.
- **revision**: Branch `064-bluetooth-transport-selection`; HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; working tree dirty and therefore not an exact committed candidate.
- **named_procedure**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage`, Gradle 9.6.0, `GRADLE_USER_HOME=.tmp/gradle-home`.
- **environment**: Windows host; ASUS AI2302/API35 and Mi A2/API29; 2026-11-08.
- **expected_observed_result**: KtLint, Detekt, Release lint, Compile All, Unit Tests and KMP Android Host Tests passed. The coverage workflow failed at `:feature:fido2:connectedAndroidDeviceTest` after 349.58 seconds. ASUS ran 63 tests with 6 failures; Mi ran 60 with 7 failures; 110 of 123 cases passed. Twelve failures require declared opt-in stress/Perfetto setup, and Mi's separate-advertiser discovery case lacked its required remote BLE advertiser. No fresh canonical counters or floor-verification result were produced.
- **limitations**: Completeness, Tier 1/Tier 2/repository floors and raw-count nonregression remain Unverified. A retained accepted-target index exists for `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`, but live remote-ref lookup failed because GitHub credentials are unavailable; the local branch ref points elsewhere, and the candidate worktree is dirty. `tools/integration-ci.ps1` was not run. Hosted integration remains waived and the hosted runner disabled.
- **status**: Unverified (the connected coverage gate and exact candidate comparison did not complete; no pass is claimed).
- **artifact_link**: `D:\octav\source\repos\.worktrees\chimali\coverage-evidence\064-coverage-2026-11-08\coverage-attempt-summary.json`; `TEST-ASUS_AI2302 - 15.xml`; `TEST-Mi A2 - 10.xml`.

### Record 231: Parent T035 Lifecycle and Recovery Evidence Reconciled (T035 — Procedure Complete, Partial Outcomes)
- **requirement**: Parent T035 / T032–T033; run and document both-mode force-stop, process-death, reboot, in-flight preference-save and unavailable-mode recovery procedures.
- **scope**: Reconcile the already executed host, device and physical WebAuthn procedures against the T035 matrix. No device or app state was changed for this reconciliation.
- **revision**: Branch `064-bluetooth-transport-selection`; HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged/unstaged changes.
- **named_procedure**: Review task T035 and the parent quickstart matrix against implementation evidence Records 055, 141, 183–229; verify each requested procedure has a recorded outcome and environment.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Mi A2 / Android 10 API29 / serial `2ac6876`; Windows 11 for the physical WebAuthn ceremonies; evidence reconciled 2026-11-08.
- **expected_observed_result**:

| T035 procedure | Evidence and outcome |
|---|---|
| T032–T033 framing, cancellation, interruption and cleanup regressions | Deterministic host suites are recorded in BLE Records 187, 192 and 204; current local host run passed before device coverage stopped (Record 230). |
| Force-stop and explicit restart | BLE on ASUS and Mi, Classic on ASUS; saved selection persisted and transport remained stopped until explicit Start (Records 191, 195, 209, 218, 222). |
| OS process death/service restart | Both saved modes survived ActivityManager background kill while stopped (Record 201); controlled active-service crash restarted BLE and Classic in new processes and restored the selected service without fallback (Records 202 and 212). |
| Reboot | ASUS BLE and Classic, and Mi BLE retained selection and required explicit Start (Records 164, 166, 222). |
| Mid-ceremony saved-choice change | All four physical SC-005 combinations succeeded with the alternate transport pending: BLE registration/authentication and Classic registration/authentication (Records 190, 220, 228, 229). |
| Unavailable-mode recovery | Radio-off error and Retry recovered BLE on ASUS and Mi (Records 205 and 225); Classic radio recovery on ASUS is Record 216; revoked BLE advertiser permission recovery is Record 213; Mi/API29 Classic HID startup timeout displayed guidance and BLE Retry recovery (Record 223); all typed unavailable reasons map to distinct dashboard guidance and Retry (Record 226). |

- **limitations**: The prescribed controlled process-death procedures are complete, but organic low-memory reclamation of an active foreground service was not reproduced. Classic HID is unsupported on Mi/API29 because Android cannot bind `IBluetoothHidDevice`; the app reports this and BLE recovers, but Mi Classic interop is not established. The cancellation attempt in Record 203 failed before the successful system-Back retry in Record 217; no protocol-level CTAPHID_CANCEL trace was captured. These limits remain explicit and do not turn partial or unsupported results into passes.
- **status**: Procedure complete; Partial (natural memory-pressure reclamation and Mi Classic are respectively Unverified and Unsupported; successful physical SC-005 matrix and scoped recovery cases are recorded).
- **artifact_link**: Parent Records 201–229; BLE Records 187, 192, 193, 199–205; `specs/064-bluetooth-transport-selection/quickstart.md`.

### Record 232: Rebased Candidate Local CI Passes; Coverage Capture Remains Incomplete (T039 — Unverified)
- **requirement**: Parent T039; exact-candidate local validation, coverage completeness/floors and raw-count nonregression against the current accepted target.
- **scope**: Clean rebased candidate; ordinary local CI plus one ASUS/API35 coverage capture. No accepted-target comparison or baseline promotion was performed.
- **revision**: Branch `064-bluetooth-transport-selection`; candidate `6c483486f83a8df20d8fdc59a05a05ce9558c567`; tree `5423439e15fb5c832fecad43e05eaadfc8a8e490`; target `e7918b6e256319b1305536c7815a92f6132ece99` is an ancestor. Working tree was clean before measurement.
- **named_procedure**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache`; then `tools/coverage/capture.ps1` with Purpose `Candidate`, the exact candidate/target, external output root `D:\octav\source\repos\.worktrees\chimali\coverage-evidence\candidate-6c483`, and ASUS serial `R7AIB7002901AZ3` (API35).
- **environment**: Windows; Gradle 9.6.0, Java 21.0.10, Kotlin 2.3.21, AGP 9.4.1, JaCoCo 0.8.15; ASUS AI2302 / Android 15 API35; 2026-11-08.
- **expected_observed_result**: Exact-candidate non-coverage local CI passed in 197.87 seconds, including KtLint, Detekt, Release lint, comprehensive compilation, unit tests and KMP Android host tests. Coverage run `5eae2e16-2add-4256-847f-dae87026a1dd` ended `Collection Status: Incomplete`, `Tests: Failed` at `:feature:fido2:connectedAndroidDeviceTest`. ASUS XML reported six precondition failures: the remote-advertiser/50-cycle case was not explicitly opted in with a separate advertiser, and Perfetto research/trace cases lacked their declared active session. Partial counters were emitted but capture also reported missing/changed execution inputs and attribution-count mismatches; they are not accepted as complete coverage evidence.
- **limitations**: No completeness or Tier 1/Tier 2/repository floor decision is valid, and raw-count nonregression was not assessed. The local evidence-root index binds `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`; the canonical archived index observed on `swe/coverage-evidence` binds protected `main` at `f1fd56baf8641cea99edc7fafa8725fdda21044c`. Neither matches target `e7918b6e256319b1305536c7815a92f6132ece99`. No target-only measurement was promoted or treated as accepted. `tools/integration-ci.ps1` was not run because no compatible index exists. Hosted integration remains waived and its runner disabled.
- **status**: Passed (exact-candidate non-coverage local CI); Unverified (coverage completeness/floors and accepted-target comparison).
- **artifact_link**: `D:\octav\source\repos\.worktrees\chimali\coverage-evidence\candidate-6c48348\5eae2e16-2add-4256-847f-dae87026a1dd\summary.md`; corresponding `manifest.json`, `counters.json`, and `reports/tests`; [coverage workflow](../058-enforce-coverage-targets/plan.md); indexed baseline `D:\octav\coverage-local-validation\baselines\current.json`.

### Record 233: Exact Rebased Target Baseline Recapture Still Incomplete (T039 — Unverified)
- **requirement**: T039; establish a complete coverage capture for the exact rebased target before candidate-versus-target raw-count assessment.
- **scope**: Clean isolated checkout of target `e7918b6e256319b1305536c7815a92f6132ece99`; fresh single-device ASUS/API35 coverage capture using the target's committed harness. GitHub's read API independently confirmed `refs/heads/lab/or/chimali` still resolved to this SHA on 2026-11-08; local Git `ls-remote` remains unavailable because the current process has no GitHub credential. No baseline index was created or modified.
- **revision**: Target and harness `e7918b6e256319b1305536c7815a92f6132ece99`; target tree `afe0683226166775f4fccdfc8accb06c46bd5697`.
- **named_procedure**: `tools/coverage/capture.ps1` with `Purpose=Comparison`, target equal to source revision, ASUS serial `R7AIB7002901AZ3`; run `57372dab-0995-436c-b07c-83c4f6d791d1`.
- **environment**: Windows host; Gradle 9.6.0, Kotlin 2.3.21, AGP 9.4.1, JaCoCo 0.8.15; ASUS AI2302 / Android 15 API35; 2026-11-08.
- **expected_observed_result**: Gradle executed the host workload and started the connected device suites. `:feature:fido2:connectedAndroidDeviceTest` failed with 5 failures among 46 tests: `EmpiricalCalibrationDeviceTest`, `FilteredMeasurementPilotDeviceTest`, `LowerOverheadControlExplorationDeviceTest`, `PerformanceTraceDeviceTest`, and `TraceOverheadExplorationDeviceTest`. Each failure reports missing opt-in Perfetto/tracing setup; the capture therefore stopped before canonical counters or a comparable target bundle were produced.
- **limitations**: This is a failed measurement attempt, not a baseline. It cannot establish completeness, floors, or nonregression. The accepted-target index remains unchanged; parent T039 and BLE T038 remain open. The feature-058 harness/opt-in workload needs an authorized complete Perfetto/session setup or a reviewed test-selection correction before another valid capture.
- **status**: Unverified (exact target recapture failed at the required device suite; no accepted baseline was created).
- **artifact_link**: `D:\octav\source\repos\.worktrees\chimali\coverage-evidence\recreated-baseline-e791\57372dab-0995-436c-b07c-83c4f6d791d1\summary.md`; `manifest.json`; target device report `feature/fido2/build/outputs/androidTest-results/connected` in the isolated target checkout.

### Record 234: Corrected Routine Exclusion Passes FIDO2 Task; Initial Full Target Rehearsal Blocked by App Keystore Data (T039 — Unverified)
- **requirement**: Parent T039; complete local validation, exact target capture, floor/completeness assessment and accepted-target raw-count comparison.
- **scope**: Validate the corrected Feature 058 routine filter on the exact target, run the full host/device coverage task graph, and recheck the trusted archived accepted-target index.
- **revision**: Exact target `e7918b6e256319b1305536c7815a92f6132ece99`, tree `afe0683226166775f4fccdfc8accb06c46bd5697`; harness branch `064-bluetooth-transport-selection`, dirty at `6c483486f83a8df20d8fdc59a05a05ce9558c567` plus uncommitted coverage-tool updates.
- **named_procedure**: Ran the target `:feature:fido2:connectedAndroidDeviceTest` using the revised `tools/hdk-coverage.init.gradle` and AndroidJUnitRunner `notPackage=com.chimali.fido2.util.performance`; then manually invoked `hdkHostCoverage hdkCoverageAnalyze` against the clean target. This rehearsal was not packaged by `tools/coverage/capture.ps1`. Read `baselines/current.json` from GitHub's `swe/coverage-evidence` branch via the connected GitHub repository reader.
- **environment**: Windows; ASUS AI2302 / Android 15 API35 (`R7AIB7002901AZ3`); Gradle 9.6.0; 2026-11-08.
- **expected_observed_result**: Target FIDO2 connected tests passed 41/41 in 10 suites without failures/errors/skips and without any of the five excluded Perfetto suites. The complete graph reached `:app:connectedShrunkDebugAndroidTest`; three `CredentialCryptoProviderTest` cases passed, but `LeakCanaryMnemonicOwnerTest` failed during app startup as Android Keystore raised `VERIFICATION_FAILED`/`AEADBadTagException` decrypting the installed `com.chimali` wallet database. `hdkCoverageAnalyze` did not complete. The trusted current index identifies `f1fd56baf8641cea99edc7fafa8725fdda21044c`, which does not match target `e7918b6e256319b1305536c7815a92f6132ece99`.
- **limitations**: This record describes the initial failed rehearsal. Follow-up Record 235 documents that the previously authorized `com.chimali.shrunk` reset allowed a later full manual workload and analysis to complete; separate `com.chimali` data was not cleared. The later run is not an immutable capture and did not produce a candidate comparison or accepted-index promotion.
- **status**: Passed (FIDO2 routine workload filter and Feature 058 policy suite 520/520); Unverified (full target coverage and current-accepted-target comparison).
- **artifact_link**: Feature 058 evidence §§11.146–11.147; target FIDO2 JUnit `D:\octav\source\repos\.worktrees\chimali\baseline-e791-source\feature\fido2\build\outputs\androidTest-results\connected\androidMain\TEST-ASUS_AI2302 - 15.xml`; app JUnit failure `D:\octav\source\repos\.worktrees\chimali\baseline-e791-source\app\build\outputs\androidTest-results\connected\shrunkDebug\TEST-ASUS_AI2302 - 15.xml`; [trusted accepted-target index](https://github.com/octaviordz/chimali/blob/swe/coverage-evidence/baselines/current.json).

### Record 235: Exact-Target Manual Coverage Analysis Recreated; Acceptance Still Pending (T039 — Unverified)
- **requirement**: Complete target workload and coverage analysis before parent T039 comparison or promotion.
- **scope**: Follow-up to Record 234. With prior user authorization, only `com.chimali.shrunk` was cleared; `adb pm clear` returned Success and `com.chimali` remained installed and untouched. Full manual `hdkHostCoverage hdkCoverageAnalyze` then completed on the exact target.
- **revision**: Target `e7918b6e256319b1305536c7815a92f6132ece99`, tree `afe0683226166775f4fccdfc8accb06c46bd5697`; dirty harness based on `6c483486f83a8df20d8fdc59a05a05ce9558c567` plus coverage-tool updates.
- **named_procedure**: ASUS API35 full host/device workload with corrected routine Perfetto exclusion, then `hdkCoverageAnalyze`; app instrumentation 11/11, FIDO2 41/41, required core:common and settings device suites, host suites and analysis completed. Counters and production inventory were written. A later `hdkCoverageVerify` attempt reran connected dependencies and failed on app Keystore AEAD startup. One full retry used an incorrectly named runner environment variable and failed FIDO2 selection; the corrected full retry again failed at app Keystore startup, while a subsequent focused FIDO2 task passed 41/41. Later attempts overwrote the app JUnit output; only the counters/inventory from the successful manual analysis remain available. No successful verifier receipt is claimed.
- **environment**: Windows; ASUS AI2302 Android 15 API35 serial `R7AIB7002901AZ3`; target checkout `D:\octav\source\repos\.worktrees\chimali\baseline-e791-source`; 2026-11-08.
- **expected_observed_result**: Repository instructions 77,209/134,569 (57.38%). Tier 1 instruction/branch: data 1,277/1,452 (87.95%)/57/74 (77.03%); domain 5,124/7,184 (71.33%)/412/578 (71.28%); security 3,624/4,452 (81.40%)/258/386 (66.84%); FIDO2 56,458/87,578 (64.47%)/3,461/5,617 (61.62%). These manual counters exceed configured 60% Tier 1 and 50% repository floors; applicable Tier 2 instruction counters exceed 20%.
- **limitations**: Dirty harness prevented immutable `capture.ps1` receipt. Trusted accepted index remains bound to target `f1fd56baf8641cea99edc7fafa8725fdda21044c`, not `e7918b6`. No compatible target baseline was promoted and no candidate raw-count comparison was performed. T039 remains Unverified.
- **status**: Manual exact-target workload and analysis completed; formal capture, successful verifier evidence, review/promotion and candidate comparison remain open.
- **artifact_link**: Feature 058 §11.148; target `build/reports/hdk-coverage/counters.json` and `production-inventory.json`; app/FIDO2 JUnit reports in the target checkout.

### Record 236: Feature Worktree Local CI Passed (T039 — Partial)
- **requirement**: Run local static analysis, compilation and required host tests before parent coverage comparison.
- **scope**: Current feature worktree `064-bluetooth-transport-selection`; ordinary `tools/local-ci.ps1 -NoFix -NoConfigurationCache` pipeline, without optional connected coverage.
- **revision**: HEAD `6c483486f83a8df20d8fdc59a05a05ce9558c567` plus current staged/unstaged implementation and evidence changes.
- **named_procedure**: Added `--no-problems-report` to the local CI Gradle command after the report file was denied on this Windows environment. Then ran `tools/local-ci.ps1 -NoFix -NoConfigurationCache` with isolated `GRADLE_USER_HOME` and read-only dependency cache.
- **environment**: Windows PowerShell; Gradle 9.6.0; 2026-11-08.
- **expected_observed_result**: KtLint, Detekt, Release lint, compile-all, unit tests, and KMP Android host tests all passed; local CI exited 0 in 74.67 seconds. The Feature 058 offline policy suite passed 520/520, and PowerShell parsing passed for the modified local coverage scripts. Its notice correctly says this does not establish hosted or coverage integration acceptance.
- **limitations**: Connected-device coverage and accepted-target comparison are not part of this invocation and remain Unverified under T039.
- **status**: Local non-coverage CI passed; T039 remains Partial/Unverified pending trusted target capture, compatible accepted baseline and candidate comparison.
- **artifact_link**: `tools/local-ci.ps1`; Gradle output observed for this run; parent coverage Record 235.

### Record 237: Exact-Target Full Coverage and Floor Verification Passed (T039 — Partial)
- **requirement**: Complete target workload and configured coverage floors before accepted-target comparison or promotion.
- **scope**: Follow-up to Record 235. Reset only `com.chimali.shrunk`; the separately installed `com.chimali` package remained intact. Ran the full exact-target verifier graph.
- **revision**: Target `e7918b6e256319b1305536c7815a92f6132ece99`, tree `afe0683226166775f4fccdfc8accb06c46bd5697`; harness at dirty HEAD `6c483486f83a8df20d8fdc59a05a05ce9558c567` plus working changes.
- **named_procedure**: `hdkCoverageVerify` on ASUS API35 with the corrected exact Perfetto package exclusion; app 11/11, FIDO2 41/41, core:common 3/3, required settings device suite, configured host suites, coverage analysis and verifier all completed. Exit 0 in 13m04s.
- **environment**: Windows; ASUS AI2302 Android 15 API35 serial `R7AIB7002901AZ3`; target checkout `D:\octav\source\repos\.worktrees\chimali\baseline-e791-source`; 2026-11-08.
- **expected_observed_result**: `BUILD SUCCESSFUL`. Repository instructions 77,209/134,569 (57.38%). Tier 1 instruction/branch: data 87.95%/77.03%, domain 71.33%/71.28%, security 81.40%/66.84%, FIDO2 64.47%/61.62%; applicable Tier 2 instruction floors passed.
- **limitations**: Manual verifier run only; dirty harness prevented immutable `capture.ps1` receipt. Accepted index still names target `f1fd56baf8641cea99edc7fafa8725fdda21044c`, so target compatibility is absent. No index promotion or candidate raw-count comparison occurred.
- **status**: Target workload and floors passed; T039 remains Partial/Unverified pending formal capture, accepted-baseline review and candidate comparison.
- **artifact_link**: Feature 058 §11.149; target coverage counters/inventory and connected JUnit reports.

### Record 238: Candidate Coverage Floors Pass; T039 Accepted-Target Comparison Remains Unverified
- **requirement**: Parent T039; execute complete candidate workload, configured floors and candidate-versus-current-accepted-target raw-count nonregression.
- **scope**: Full candidate-worktree `hdkCoverageVerify` run after Feature 058 capture-runner changes; manual verifier only. No app data for `com.chimali` was cleared.
- **revision**: Branch `064-bluetooth-transport-selection`, HEAD `6c483486f83a8df20d8fdc59a05a05ce9558c567` plus dirty staged/unstaged work; target `e7918b6e256319b1305536c7815a92f6132ece99`.
- **named_procedure**: ASUS/API35 full `hdkCoverageVerify`, corrected Perfetto opt-out filter, `--no-problems-report`, no configuration cache; exit 0 in 32m42s. All required device and configured host tasks completed; exact floor verifier passed.
- **environment**: Windows; ASUS AI2302 Android 15 API35 (`R7AIB7002901AZ3`); Gradle 9.6.0; 2026-11-08.
- **expected_observed_result**: Candidate repository instructions 88,322/149,128 (59.23%). Tier 1: data 87.95% instructions / 77.03% branches; domain 71.33% / 71.28%; security 81.40% / 66.84%; FIDO2 67.39% / 60.48%. Configured Tier 1, Tier 2 and repository floors passed. Data/domain/security class inventory digests and raw counters match exact-target rehearsal. FIDO2 inventory digest differs, so its changed raw counts and repository aggregates do not prove nonregression.
- **limitations**: Dirty harness/tree prevents immutable `capture.ps1` receipt. Canonical accepted index still points to `f1fd56baf8641cea99edc7fafa8725fdda21044c`, incompatible with target `e7918b6`; no accepted-target comparison or promotion occurred. Candidate floors passed, but T039 is not complete.
- **status**: Candidate coverage workload and floors passed; accepted-target comparison remains Unverified.
- **artifact_link**: Feature 058 §11.150; candidate counters/inventory and JUnit reports; exact-target rehearsal counters in `baseline-e791-source`.

### Record 239: Exact-Target Verifier Passes After Install-Then-Clear; Baseline Still Unsealed (T039 — Unverified)
- **requirement**: Parent T039; recreate the exact target workload and floor assessment before accepted-target raw-count comparison.
- **scope**: Exact target `e7918b6e256319b1305536c7815a92f6132ece99` on ASUS/API35 using the current Feature 058 routine Perfetto filter. User authorized clearing `com.chimali`; that reset alone did not fix the failure. The isolated `com.chimali.shrunk` package was absent before the connected task installed it, so it was installed explicitly, cleared, and then reused by Gradle.
- **revision**: Target `e7918b6e256319b1305536c7815a92f6132ece99`, tree `afe0683226166775f4fccdfc8accb06c46bd5697`; harness based on `6c483486f83a8df20d8fdc59a05a05ce9558c567` plus dirty Feature 058 updates.
- **named_procedure**: `adb install -r app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk`; `adb shell pm clear com.chimali.shrunk`; full `hdkCoverageVerify` with the current init script, `notPackage=com.chimali.fido2.util.performance`, in-process Kotlin compiler, and ASUS serial `R7AIB7002901AZ3`. Gradle daemon recorded successful completion from 11:24:30 to 11:29:56 local time. App connected suite passed 11/11; FIDO2 passed 41/41; configured core:common/settings device and host workloads plus analysis/verifier completed. Feature 058 policy suite passed 520/520; capture script parses and `git diff --check` passes.
- **environment**: Windows 11; Gradle 9.6.0; Java 21.0.10; ASUS AI2302 Android 15/API35; 2026-11-08.
- **expected_observed_result**: Exact-target floors passed: repository instructions 77,209/134,569 (57.38%); Tier 1 data 1,277/1,452 instructions and 57/74 branches; domain 5,124/7,184 and 412/578; security 3,624/4,452 and 258/386; FIDO2 56,458/87,578 and 3,461/5,617. No workload task reported failure. Canonical counters SHA-256 `412e65269bf2bd6a1bcb2fcdae37c93a727a410d125220a6257abb8c6e6f1998`; inventory SHA-256 `1c41187790fd1e5d584d7c2a03fd9f4b80045eee21d72623a074e86887583dd8`.
- **limitations**: This is a successful manual verifier run, not an immutable capture receipt. The harness remains dirty. The trusted accepted index still targets `f1fd56baf8641cea99edc7fafa8725fdda21044c`, not `e7918b6`; no baseline promotion or candidate raw-count comparison was performed. Clearing the separate production `com.chimali` package was not needed to resolve the test failure.
- **status**: Exact-target manual workload and configured floors passed; T039 remains Unverified pending immutable capture, accepted-index compatibility/review, and candidate comparison.
- **artifact_link**: Feature 058 §11.151; target `build/reports/hdk-coverage/counters.json` and `production-inventory.json`; app/FIDO2 connected JUnit reports; capture reset-order implementation in `tools/coverage/capture.ps1`.

### Record 240: Current Candidate Coverage Workload Passes; Accepted-Target Comparison Remains Unverified
- **requirement**: Parent T039; complete routine host/device coverage workload, enforce configured floors and compare candidate raw counts against a compatible independently indexed accepted target.
- **scope**: Full manual ASUS/API35 `hdkCoverageVerify` on the dirty candidate after Feature 058 added a reviewed exact-class exclusion for the opt-in two-device reconnect stress test. The first attempt's FIDO2 XML included the test's expected missing-advertiser assumption failure; the rerun excludes that exact class with AndroidJUnitRunner `notClass` and produced clean JUnit results.
- **revision**: Branch `064-bluetooth-transport-selection`, HEAD `6c483486f83a8df20d8fdc59a05a05ce9558c567` plus staged/unstaged changes; target `e7918b6e256319b1305536c7815a92f6132ece99`.
- **named_procedure**: Full `hdkCoverageVerify` with `notPackage=com.chimali.fido2.util.performance`, `notClass=com.chimali.fido2.data.transport.FidoBleReconnectStressDeviceTest`, ASUS/API35 and in-process Kotlin compiler; exit 0 in 5m59s. App JUnit 13/13 and FIDO2 JUnit 57/57, zero failures/errors/skips. All required device/host tasks, analysis and configured floors completed. Feature 058 policy suite passed 520/520; policy validation, PowerShell parsing and `git diff --check` passed.
- **environment**: Windows; ASUS AI2302 Android 15/API35 (`R7AIB7002901AZ3`); Gradle 9.6.0; 2026-11-08.
- **expected_observed_result**: Repository instructions 89,780/149,027 (60.24%). Tier 1 data 1,277/1,452 instructions and 57/74 branches; domain 5,124/7,184 and 412/578; security 3,624/4,452 and 258/386; FIDO2 67,685/100,179 and 4,037/6,660. All configured floors passed. Counters SHA-256 `1613603b4bb7f8191f9aa9de68bf48c54ace234f7736cdf1495995a40517c262`; inventory SHA-256 `9d821c04fa799ab9860b730e57634bbb99fc2640139a4ad6774c991cb50d7316`.
- **limitations**: This remains a manual run using a dirty candidate/harness; no immutable capture receipt exists. The trusted accepted index still targets `f1fd56baf8641cea99edc7fafa8725fdda21044c`, not `e7918b6`, so no compatible accepted-target raw-count comparison or promotion is established. The user-authorized `com.chimali` production app data clear was performed; the separate `com.chimali.shrunk` coverage package was reset for the measured run. Hosted integration remains waived.
- **status**: Routine candidate workload and floors pass. T039 remains Unverified pending a clean immutable capture and a compatible independently indexed accepted baseline/comparison.
- **artifact_link**: Feature 058 §11.152; current candidate counters, production inventory and app/FIDO2 JUnit reports; `tools/coverage/policy.json`, `capture.ps1` and `hdk-coverage.init.gradle`.

### Record 241: Capture-Style Exact Class Exclusion Reaches AndroidJUnitRunner
- **requirement**: Parent T039; validate the routine FIDO2 device workload filter before formal candidate capture.
- **scope**: Focused ASUS/API35 FIDO2 connected device suite with both reviewed Perfetto package and opt-in BLE reconnect class environment filters set as in Feature 058 capture.
- **revision**: Branch HEAD `6c483486f83a8df20d8fdc59a05a05ce9558c567` plus dirty Feature 058 changes.
- **named_procedure**: `:feature:fido2:connectedAndroidDeviceTest` via `tools/hdk-coverage.init.gradle`; Gradle completed in 1m43s. JUnit had 57 tests, zero failures/errors/skips, and no stress-test suite.
- **environment**: Windows; ASUS AI2302 Android 15/API35 (`R7AIB7002901AZ3`); Gradle 9.6.0; 2026-11-08.
- **expected_observed_result**: Capture-style environment property `ORG_GRADLE_PROJECT_android.testInstrumentationRunnerArguments.notClass` correctly excludes `FidoBleReconnectStressDeviceTest`; the rest of the FIDO2 connected suite passes.
- **limitations**: This focused run validates filter behavior only. Candidate/harness cleanliness and compatible accepted-target index remain unavailable; no full immutable capture or candidate comparison was made.
- **status**: Filter behavior verified; T039 remains Unverified.
- **artifact_link**: Feature 058 §11.153; ASUS FIDO2 connected JUnit report; `tools/coverage/capture.ps1` and policy.

### Record 242: Exact-Target Capture Exposes Historical Source Filter Compatibility (T039 — Unverified)
- **requirement**: Parent T039; create a complete immutable capture for the exact current target before candidate comparison.
- **scope**: Fresh capture attempt for target `e7918b6e256319b1305536c7815a92f6132ece99` with the committed candidate as the trusted harness. No Android package data was cleared because the build stopped before device setup.
- **revision**: Candidate/harness commit `2ef5d87f74c8bd0f6b966380b1dc4d8ef9e6eab2`; current worktree has one uncommitted harness compatibility change; target tree `afe0683226166775f4fccdfc8accb06c46bd5697`.
- **named_procedure**: `tools/coverage/capture.ps1 -Purpose Comparison` on ASUS/API35. The first attempt stopped while configuring `:feature:fido2:connectedAndroidDeviceTest`: policy class exclusion `FidoBleReconnectStressDeviceTest` did not exist in the historical target source tree. Updated the capture runner to pass `notClass` only when that test source exists and the trusted Gradle init script to report only class exclusions present in the measured revision. Gradle `help` configuration passed for both target and candidate; an exact-source check confirmed the class is absent from target and present in candidate. Feature 058 policy fixtures passed 520/520; `git diff --check` passed.
- **environment**: Windows 11; ASUS AI2302 Android 15/API35 (`R7AIB7002901AZ3`); Gradle 9.6.0; 2026-11-08.
- **expected_observed_result**: The source-compatible exclusion change removes a harness configuration failure when comparing revisions predating the opt-in stress test, while retaining the exact exclusion for the candidate where the class exists.
- **limitations**: This attempted run produced no target counters or immutable bundle. The trusted harness must be clean before capture, so commit the correction first. The indexed accepted baseline still targets protected `main` at `f1fd56baf8641cea99edc7fafa8725fdda21044c`, not current target `e7918b6`; no index promotion or raw-count comparison is authorized by this measurement.
- **status**: Compatibility fix is policy-tested and Gradle-configured on target and candidate; T039 remains Unverified pending clean-harness exact-target capture and compatible independently indexed baseline review.
- **artifact_link**: Capture attempt `c273eb13-14a9-4909-a08e-b9593b582de1`; target checkout `D:\octav\source\repos\.worktrees\chimali\baseline-e791-recapture-20261108`; Feature 058 §11.154; `tools/hdk-coverage.init.gradle`.

### Record 243: Exact-Target Capture Passed an Empty notClass Filter and Silently Ran Zero Tests (T039 — Unverified)
- **requirement**: Parent T039; complete immutable exact-target capture and candidate-versus-accepted-target comparison.
- **scope**: Follow-up capture using the clean, committed Feature 058 harness after serializing connected-device test tasks.
- **revision**: Harness `9c0aa97f7d5d8b7f01760e411cb4e8cd6f7fb5b4`, clean; target `e7918b6e256319b1305536c7815a92f6132ece99`, tree `afe0683226166775f4fccdfc8accb06c46bd5697`.
- **named_procedure**: `tools/coverage/capture.ps1 -Purpose Comparison` on ASUS API35, run `34b5850b-a782-4269-9361-24fb3e32869e`. Gradle dry-run confirmed connected tasks serialize. Capture installed and cleared only `com.chimali.shrunk`, then ran connected suites sequentially. Because PowerShell's `[Environment]::SetEnvironmentVariable(name, $null, 'Process')` left an empty environment variable, AGP passed `-e notClass` with no value. Each connected task reported zero JUnit tests; the displayed `Invalid userId -2` message was nonfatal and not the root cause. Capture correctly marked collection Incomplete, tests Failed, and floors Unverified rather than accepting zero-test results.
- **environment**: Windows 11; ASUS AI2302 Android 15/API35 (`R7AIB7002901AZ3`); Gradle 9.6.0; 2026-11-08.
- **expected_observed_result**: The ordering fix prevents connected-task concurrency. The zero-test outcome was traced to an empty runner filter, not a UTP user-selection defect. Direct instrumentation with `--user 0` and Gradle instrumentation with the empty filter removed both pass FIDO2 41/41.
- **limitations**: No target coverage counters, inventory, immutable comparable bundle or candidate comparison were produced. The independently trusted accepted index still points to protected `main` at `f1fd56baf8641cea99edc7fafa8725fdda21044c`, incompatible with target `e7918b6`; no baseline promotion is claimed.
- **status**: Harness serialization committed; exact-target capture remains Incomplete/Failed and T039 Unverified.
- **artifact_link**: Capture manifest and Gradle logs under `D:\octav\source\repos\.worktrees\chimali\coverage-evidence\064-coverage-2026-11-08\34b5850b-a782-4269-9361-24fb3e32869e`; harness commit `9c0aa97f7d5d8b7f01760e411cb4e8cd6f7fb5b4`.

### Record 244: Exact-Target FIDO2 Runner Executes 41 Tests After Omitting Empty Filter (T039 — Partial)
- **requirement**: Parent T039; establish a truthful nonzero connected test result before retrying formal capture.
- **scope**: Focused exact-target FIDO2 connected suite on ASUS/API35 after changing capture environment cleanup to remove absent runner filters.
- **revision**: Target `e7918b6e256319b1305536c7815a92f6132ece99`, tree `afe0683226166775f4fccdfc8accb06c46bd5697`; pending harness fix in the feature worktree.
- **named_procedure**: Ran `:feature:fido2:connectedAndroidDeviceTest` with the Feature 058 init script, routine `notPackage=com.chimali.fido2.util.performance`, no `notClass` variable, `--no-daemon`, and in-process Kotlin compilation. The Gradle worker command contained `notPackage` only. Exit 0; JUnit XML reports 41 tests, zero failures/errors/skips; a nonempty JaCoCo `coverage.ec` was collected.
- **environment**: Windows 11; ASUS AI2302 Android 15/API35 (`R7AIB7002901AZ3`); Gradle 9.6.0; 2026-11-08.
- **expected_observed_result**: Removing the unset class-filter environment key restores actual AndroidJUnitRunner discovery and yields the expected 41-test FIDO2 suite.
- **limitations**: This is a focused device suite, not a full immutable capture. The accepted baseline index is still incompatible with target `e7918b6`; no candidate comparison or promotion is claimed.
- **status**: Root cause corrected in `capture.ps1`; focused target test passed; full capture and accepted-target comparison remain open.
- **artifact_link**: Target FIDO2 JUnit report and `coverage.ec`; run command captured in Gradle daemon `daemon-52076.out.log`; Feature 058 §11.155.
### Record 245: Immutable Exact-Target and Candidate Captures Pass Floors; Accepted-Target Gate Remains Unverified
- **requirement**: Parent T039; complete exact-candidate coverage and compare affected Tier 1 raw coverage ratios to the trusted accepted target using Feature 058's integration procedure.
- **scope**: Immutable exact-target capture for `e7918b6e256319b1305536c7815a92f6132ece99` and immutable candidate capture for `52dfb065b475df74821e4135e3f22da9ddd2f774`, with direct raw-count comparison of changed Tier 1 scope.
- **revision**: Target tree `afe0683226166775f4fccdfc8accb06c46bd5697`; candidate branch `064-bluetooth-transport-selection`, HEAD `52dfb065b475df74821e4135e3f22da9ddd2f774`; both source/harness preflight and postflight states clean.
- **named_procedure**: Trusted harness `7c40b2a722376143a37873c35d4dcf49fc17393c` captured exact target run `f05928c2-d767-4363-a572-b3f1e28351dd`; clean candidate/harness captured candidate run `2c62cd8b-abd2-4f4a-ba9b-2f25143e50c3`. ASUS AI2302/API35; both manifests report collection Complete, tests Passed, and matching measurement fingerprint `b1be466f4ebf9003f5bd6330030e2888c12b9a94b5e6068f7b50ef48b14cae1c`. Both standalone floor assessments pass. Changed Tier 1 `:feature:fido2`: target instructions 56,458/87,578 and branches 3,461/5,617; candidate instructions 68,342/100,196 and branches 4,106/6,662. `:core:data` 1,277/1,452 and 57/74, `:core:domain` 5,124/7,184 and 412/578, and `:core:security` 3,624/4,452 and 258/386 are identical across runs. Candidate repository aggregate is 90,437/149,044 (60.678%); exact target is 77,209/134,569 (57.375%).
- **environment**: Windows 11; ASUS AI2302 / Android 15 API35 (`R7AIB7002901AZ3`); Gradle 9.6.0; 2026-11-08 local time.
- **expected_observed_result**: Captures are complete, tests and configured floors pass, policy fingerprints match, and measured changed Tier 1 ratios are nondecreasing.
- **limitations**: This direct raw-count assessment does not replace required `tools/integration-ci.ps1` validation against an independently trusted accepted-baseline index. The canonical trusted index points to protected `main` at `f1fd56baf8641cea99edc7fafa8725fdda21044c`, not target `e7918b6`; no index was fabricated, created, or promoted. Hosted integration is waived and remains unverified. Parent T039 remains Unverified.
- **status**: Exact target and candidate captures and floors pass; formal accepted-target integration gate remains Unverified.
- **artifact_link**: Target bundle and assessment under `D:\octav\source\repos\.worktrees\chimali\coverage-evidence\recreated-baseline-e791\f05928c2-d767-4363-a572-b3f1e28351dd`; candidate bundle and assessment under `D:\octav\source\repos\.worktrees\chimali\coverage-evidence\064-coverage-2026-11-08\2c62cd8b-abd2-4f4a-ba9b-2f25143e50c3`; Feature 058 §11.156.
### Record 246: Feature Branch Synced with Protected Main; Fresh Coverage Assessment Required
- **requirement**: Parent T039 and the integration coordination guide; base the final candidate assessment on the current accepted main target and retain exact revision evidence.
- **scope**: Merge the current protected target into the Feature 064 branch, keep `.specify/feature.json` pointed at `specs/064-bluetooth-transport-selection`, and verify local checks and current baseline metadata.
- **revision**: Merge commit `f6e85e53d63eafdd551772d012dedc67ca8c7d9e`; protected target `f1fd56baf8641cea99edc7fafa8725fdda21044c`; target commit is an ancestor of HEAD; HEAD tree `8d58832266f54094a85eb1206cb084ac8ff339a4` differs from the pre-merge captured tree `415fedc43edf5188c694346ac996728c2dacd56d`.
- **named_procedure**: Merged `origin/main` locally after resolving Feature 061 documentation conflicts by preserving the branch's later rollout evidence and leaving Feature 064 selected in `feature.json`. Merge pre-commit Local CI passed KtLint, Detekt, Release lint, compile, unit and KMP Android host tests. Read the protected `main` ref via the connected GitHub repository reader; it remains at `f1fd56baf8641cea99edc7fafa8725fdda21044c`. The accepted archive index identifies run `c1b392a9-f830-4283-83f2-44e36ac5d731` with measurement fingerprint `6b2b9e491e02baf41f8084e3df9cfe1555b5005c525d34eaf89889d79590b3bd`; recent Feature 064 captures have fingerprint `b1be466f4ebf9003f5bd6330030e2888c12b9a94b5e6068f7b50ef48b14cae1c`.
- **environment**: Windows 11; requested `ofrenda` worktree; ASUS AI2302/API35 attached; 2026-11-08 local time.
- **expected_observed_result**: Current accepted main is in candidate ancestry, active feature pointer remains the parent spec, and ordinary local CI passes.
- **limitations**: The latest candidate tree has not been recaptured. The accepted bundle and current harness fingerprints differ, so accepted-target compatibility is not established. `git ls-remote` fails because the local GitHub CLI token is invalid; the GitHub connector verified the live branch SHA but cannot substitute for the required integration script's Git remote check. Parent T039 remains Unverified; do not reuse pre-merge candidate run 2c62cd8b as a HEAD assessment.
- **status**: Branch synchronization complete and local CI passed; candidate recapture, compatible accepted-target assessment and formal `integration-ci.ps1` remain open.
- **artifact_link**: Merge commit `f6e85e53d63eafdd551772d012dedc67ca8c7d9e`; accepted baseline archive `swe/coverage-evidence` run `c1b392a9-f830-4283-83f2-44e36ac5d731`; prior candidate capture Record 245; Feature 058 §11.157.
### Record 247: Exact Post-Merge Candidate Passes Floors; Accepted Baseline Is Incompatible
- **requirement**: Parent T039; verify the exact current candidate against trusted, compatible accepted-target evidence and retain completeness, test, floor and regression decisions.
- **scope**: Candidate commit after protected-main synchronization, exact accepted archive bundle and canonical index verification.
- **revision**: Candidate `08a7e318666d1d67ddbf25053d5615cc55f6c46f`, tree `3ee1abd006f2676f7152bce95a4483c6b1287b19`; target `f1fd56baf8641cea99edc7fafa8725fdda21044c`.
- **named_procedure**: Captured Candidate run `3026b76a-4a15-44fa-ac56-03b523cde305` on ASUS API35; collection Complete and tests Passed. Standalone floor assessment passes repository 90,437/149,044 instructions (60.678%) and FIDO2 68,342/100,196 instructions (68.208%) and 4,106/6,662 branches (61.633%). Extracted the accepted bundle for run `c1b392a9-f830-4283-83f2-44e36ac5d731` from `origin/swe/coverage-evidence`; computed bundle digest `d71928160a4ce5f3f9a989f2fff6263d8246d9eb8fbbe8c49c88a0d9f9eabfa1` matches canonical `baselines/current.json`. Ran `tools/coverage/verify.ps1` with that exact bundle/index and current candidate. Decision: collection Complete, tests Passed, floors Passed, comparison compatibility Incompatible, regression Failed, integration Failed.
- **environment**: Windows 11; ASUS AI2302 / Android 15 API35 (`R7AIB7002901AZ3`); Gradle 9.6.0; 2026-11-08 local time.
- **expected_observed_result**: Candidate coverage floors pass. Trusted comparison rejects the accepted run because its measurement fingerprint `6b2b9e491e02baf41f8084e3df9cfe1555b5005c525d34eaf89889d79590b3bd` differs from current candidate `b1be466f4ebf9003f5bd6330030e2888c12b9a94b5e6068f7b50ef48b14cae1c`; it additionally reports baseline harness-file digest mismatches and changed `:feature:fido2` required-device-task exclusion evidence.
- **limitations**: This did not run `tools/integration-ci.ps1`; local live `git ls-remote` fails because GitHub CLI reports an invalid token. The connected GitHub reader independently confirmed main at the indexed target SHA. No accepted index was modified. A full target capture using the current harness and a reviewed baseline-comparison path remain required; T039 remains Unverified.
- **status**: Candidate exact capture and floors pass; formal accepted-target compatibility/regression fails pending an authorized reviewed baseline refresh and full integration wrapper run.
- **artifact_link**: Candidate bundle `D:\octav\source\repos\.worktrees\chimali\coverage-evidence\064-main-final-2026-11-08\3026b76a-4a15-44fa-ac56-03b523cde305`; decision `D:\octav\source\repos\.worktrees\chimali\coverage-evidence\064-main-final-2026-11-08\assessments\3026b76a-4a15-44fa-ac56-03b523cde305\decision.json`; accepted bundle/index under sibling `trusted-baseline`; Feature 058 §11.158.

# Implementation Evidence: Bluetooth Transport Selection

**Feature**: [spec.md](spec.md) | **Branch**: `064-bluetooth-transport-selection`
**Delivery Coordination**: [integration.md](integration.md)
**Status**: Software implementation and parent-gated BLE composition are present. Settings instrumentation passed 12/12 on both phones and BLE GATT security tests passed 4/4 per phone. The original FIDO2 suites were 50/55 on ASUS/API35 and 47/52 on Mi/API29. A corrected package filter excluded the five controlled trace classes, and the remaining suite passed 50/50 on ASUS and 47/47 on Mi; the trace procedures remain unverified (Record 051; BLE Record 069). The latest local CI without coverage passed before the runtime-error recovery update (Record 050); after the recovery update, the full FIDO2 host suite passed 1,133/1,133, the dashboard UI class passed 6/6 on each phone, and KtLint passed (Records 052–053). The parent selection gate test passed 15/15 (BLE Record 069). Earlier ASUS trace import remains incomplete (zero FIDO2 slices and parser errors). Android-only BLE↔Classic saved-selection/restart checks passed, and BLE restart after reboot is verified. A permission-revocation sticky-service crash was reproduced and fixed; direct Settings regrant and explicit BLE recovery passed on ASUS (parent Record 039; BLE Record 055). ASUS TalkBack, Windows ceremonies, mid-ceremony/recovery/stale-bond/50-cycle acceptance and trusted coverage nonregression remain Unverified; coverage remains deferred by owner direction.

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
| **FR-004** (Durable persistence) | `FR-BT-SEL-020`; `TR-BT-SEL-010` | `Fido2SettingsRepositoryImpl`; `TransportPreferenceRepositoryTest`; `UserPreferencesSerializerTest` | Host persistence and serializer tests passed; reboot unverified |
| **FR-005** (Restart enforcement) | `FR-BT-SEL-030`; `TR-BT-SEL-020` | `ProcessTransportSelection`; `ProcessTransportSelectionTest` | Host lifecycle tests passed; process-death/reboot procedure unverified |
| **FR-006** (Cancel pending restart) | `FR-BT-SEL-030`; `TR-BT-SEL-040` | `TransportSettingsViewModelTest`; `SettingsScreenTest` | Host callback passed; Compose test source compiled only |
| **FR-007** (Accessibility) | `FR-BT-SEL-040`; `TR-BT-SEL-040` | `SettingsScreen.kt`; `SettingsScreenTest`; Record 010 | Instrumentation passes at 200% Compose text scale; device high-contrast and largest native scale pass after scrolling; TalkBack announcements remain Unverified |
| **FR-008** (FIDO BLE MakeCredential/GetAssertion) | `FR-BLE-030`, `FR-BLE-050`, `FR-BLE-060`, `FR-BLE-080`; `TR-BLE-010/020/030/040` | GATT, framing, raw CTAP and lifecycle host suites | Host logic passed; physical Windows ceremonies unverified |
| **FR-009** (Classic HID preservation) | `FR-HID-010`; `FR-BT-SEL-010` | Existing `BluetoothHidTransportLifecycleTest` and Classic transport | Host regression suite passed; Windows/macOS/Linux ceremonies unverified |
| **FR-010** (Single gated handle) | `FR-BT-SEL-040`; `TR-BT-SEL-020/030` | `ProcessTransportSelectionTest`; `NeutralTransportContractTest`; app compilation | Host gate tests and app compilation passed |
| **FR-011** (Typed unavailable status/no fallback) | `FR-BT-SEL-050`; `TR-BT-SEL-020/040` | `ProcessTransportSelectionTest`; `TransportSettingsViewModelTest`; `SettingsScreenTest` | Host state tests passed; UI test source compiled only |
| **FR-012** (In-flight ceremony safety) | `FR-BT-SEL-060`; `TR-BT-SEL-040` | `TransportSettingsViewModel.select`; `TransportSelectionCeremonyTest` | Bounded synthetic host tests passed for both modes and ceremony types; physical in-flight matrix unverified |
| **SC-001** (Clean-install BLE default and ceremonies) | `FR-BT-SEL-010`; child `FR-BLE-030/050/060/080` | Preference host tests and BLE host suites; clean-install Windows procedure | Host logic passed; physical requirement unverified |
| **SC-002** (Switching requires restart) | `FR-BT-SEL-030`; `TR-BT-SEL-020` | Process/ViewModel tests both directions | Host logic passed; physical switching/ceremonies unverified |
| **SC-003** (Both modes complete ceremonies) | `FR-BLE-030/050/060/080`; `FR-HID-010` | BLE and Classic host lifecycle/CTAP suites | Host fakes passed; Windows 11 interoperability unverified |
| **SC-004** (Preference survives reboot) | `FR-BT-SEL-020/030`; `TR-BT-SEL-010/020` | Repository and process recreation host tests | Host logic passed; device reboot unverified |
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
- **requirement**: `NFR-PERF-030`, `TR-PERF-010`, `SC-006`
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
| `FR-BT-SEL-040` | `ProcessTransportSelection`, `Fido2Transport`, `Fido2TransportService` | `ProcessTransportSelectionTest`, `NeutralTransportContractTest`, `FidoBleLifecycleTest` |
| `FR-BT-SEL-050` | `AndroidBluetoothCapabilityChecker`, `TransportSettingsViewModel` | `ProcessTransportSelectionTest`, `TransportSettingsViewModelTest`; settings test source compiled only |
| `FR-BT-SEL-060` | `TransportSettingsViewModel.select` | `TransportSettingsViewModelTest`, `TransportSelectionCeremonyTest` |
| `FR-BLE-010` | `NordicBleAdvertiserController`, `FidoBleGattServer`, `BleGattTransport` | `FidoBleGattContractTest`, `FidoBleLifecycleTest`; physical discovery unverified |
| `FR-BLE-020` | `FidoBleGattServer`, `FidoBleFraming` | `FidoBleGattContractTest`, `FidoBleFramingTest`; radio negotiation unverified |
| `FR-BLE-030` | GATT MITM permissions and authenticated-host admission | `FidoBleGattContractTest`; Android security test compiled only; numeric-comparison ceremony unverified |
| `FR-BLE-040` | `BleSessionOwner`, `BleGattTransport` | `BleSessionOwnerTest`, `FidoBleLifecycleTest` |
| `FR-BLE-050` | `FidoBleFraming`, `BleSessionOwner`, `BleNotificationWriter` | `FidoBleFramingTest`, `FidoBleRecoveryTest`, `BleNotificationWriterTest` |
| `FR-BLE-060` | `BleGattTransport`, `BleNotificationWriter` | `FidoBleAssertionTest`, `FidoBleCancellationTest`, `BleNotificationWriterTest` |
| `FR-BLE-070` | `FidoBleGattServer.rejectUnauthenticatedRequest`, `BleGattTransport.onHostConnectionStateChanged/onHostAuthenticationFailure`, Home recovery and service Retry | `FidoBleGattContractTest`, `FidoBleLifecycleTest`; exact stale-bond causation and physical Forget/Pair/Retry unverified |
| `FR-BLE-080` | `Fido2Transport`, `RawCtapOperation`, `Fido2TransportService` | `RawCtapOperationTest`, `FidoBleLifecycleTest`, parent-gated composition tests |
| `TR-BT-SEL-010` | `UserPreferences`, `Fido2SettingsRepositoryImpl` | `UserPreferencesSerializerTest`, `TransportPreferenceRepositoryTest` |
| `TR-BT-SEL-020` | `ProcessTransportSelection` | `ProcessTransportSelectionTest` |
| `TR-BT-SEL-030` | App composition in `ChimaliApplication`, neutral `Fido2Transport` | `NeutralTransportContractTest`; `:app:compileDebugKotlin` passed |
| `TR-BT-SEL-040` | `TransportSettingsViewModel`, `SettingsScreen` | `TransportSettingsViewModelTest`; physical `feature:settings:connectedDebugAndroidTest` passed 11 tests; TalkBack/high-contrast/200% scaling inspection remains unverified |
| `TR-BLE-010` | `NordicBleAdvertiserController`, `AndroidFidoBleGattServerHost`, `FidoBleGattServer` | GATT/lifecycle host tests; physical Android API/security behavior unverified |
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
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus the current staged worktree and the focused `TooGenericExceptionCaught` suppression on `NordicBleAdvertiserController.startAdvertising`.
- **named_procedure**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache`, with an invocation-local wrapper appending `--no-problems-report` after the ordinary command could not write the existing Gradle problems report. The final run completed in 375.55 seconds.
- **environment**: Windows 11 x64; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; Gradle 9.6.0; Microsoft OpenJDK 25.0.5; Android SDK 37. ASUS Android 15 and Mi Android 10 were connected, but local-ci did not execute connected instrumentation.
- **expected_observed_result**: KtLint, Detekt, release lint, all configured compilation tasks, repository unit tests and configured Android host suites passed. FIDO2 host suite: 1,119/1,119; core:common 13/13; core:security 71/71; core:domain 77/77; each had zero failures, errors or skipped tests. Android device-test source compilation passed.
- **date**: 2026-11-03
- **limitations**: This does not execute connected Android device tests or establish the physical acceptance matrix. Trusted accepted-target coverage baseline/index, exact candidate/target raw-count comparison and integration assessment were not run. T039 and T041 remain open; feature-058's hosted waiver and disabled runner remain unchanged.
- **status**: `Passed (current local static analysis, compilation, repository unit tests and configured Android host tests) / Compiled Only (Android device-test sources) / Unverified (connected device tests, physical acceptance and trusted coverage/nonregression)`.
- **artifact_link**: [local CI script](../../tools/local-ci.ps1), [parent host selection ceremony test](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/TransportSelectionCeremonyTest.kt), [BLE supporting evidence Record 021](../064-fido-ble-transport/implementation-evidence.md), [T039 and T041](tasks.md)

### Record 013: Parent Coverage Gate Prerequisite Audit (T039 — Unverified)
- **requirement**: T039; `NFR-ARCH-050`; TRD §2.2/§9 (coverage floors, evidence and current-target nonregression)
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
- **requirement**: Parent T039; `NFR-ARCH-050`, `TR-BT-SEL-050`; child FIDO2 BLE T036/T038
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
- **named_procedure**: Run the API-filtered `NordicBleLegacyAdvertisingDeviceTest` on ADB serial `2ac6876`, with the app-owned advertiser stopped to avoid overlap; then restart the production app advertiser.
- **environment**: Xiaomi Mi A2 / Android 10/API 29, serial `2ac6876`; Windows 11 x64; Gradle 9.6.0; Android SDK 37.
- **expected_observed_result**: The test reports 1 test, 0 failures/errors/skips. Chimali production advertising was restored afterward with Android callback status 0 and registered FIDO GATT service.
- **date**: 2026-11-03
- **limitations**: One Mi/API 29 device only; no Windows host ceremony, switch matrix, accepted-target coverage comparison or other-device claim. API 29 cannot satisfy Feature 058 coverage; that gate is deferred.
- **status**: `Passed (Mi Nordic advertiser startup regression) / Unverified (parent acceptance and coverage obligations)`.
- **artifact_link**: [BLE device test evidence Record 037](../064-fido-ble-transport/implementation-evidence.md#record-037-nordic-legacy-advertiser-instrumentation-on-mi-a2-t036--passed), [retained Mi device-test XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi%20A2%20-%2010.xml), [parent T039](tasks.md)

### Record 024: Post-Fix FIDO2 Host and Mi Device Suite (T039 — Partial)
- **requirement**: Parent T039; BLE T036; parent selected-handle composition boundary
- **scope**: Rerun configured FIDO2 Android host and connected-device suites after the Mi advertiser compatibility change and instrumentation regression were added.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes on branch `064-bluetooth-transport-selection`.
- **named_procedure**: Set `GRADLE_USER_HOME=.gradle/codex-user-home`, `ANDROID_SERIAL=2ac6876`; run `:feature:fido2:testAndroidHostTest :feature:fido2:connectedAndroidDeviceTest`. The host suite completed; the device suite completed with failing tracing-precondition tests. Gradle also reported an `AccessDeniedException` while writing the default problems report.
- **environment**: Xiaomi Mi A2 / Android 10/API 29, serial `2ac6876`; Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: Host XML totals 1,126 tests, 0 failures/errors/skips. Device XML totals 49 tests, 5 failures, 0 errors/skips; 44 passed, including `NordicBleLegacyAdvertisingDeviceTest.connectableLegacyAdvertisingStartsOnApi28Through30`. All five failed cases are research/performance tests requiring separately configured Perfetto/tracing. No coverage task ran.
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
- **named_procedure**: Set `GRADLE_USER_HOME=.gradle/codex-user-home` and `ANDROID_SERIAL=R7AIB7002901AZ3`; run the filtered `:feature:fido2:connectedAndroidDeviceTest` task for `NordicBleLegacyAdvertisingDeviceTest` with `--no-configuration-cache --no-problems-report --max-workers=1`.
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
- **named_procedure**: Run only `NordicBleLegacyAdvertisingDeviceTest.connectableLegacyAdvertisingStartsOnApi28Plus` via `:feature:fido2:connectedAndroidDeviceTest` with the instrumentation class filter; inspect each connected-device XML result and verify the ASUS app advertiser remains registered afterward.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; ASUS AI2302/API35 serial `R7AIB7002901AZ3`; Xiaomi Mi A2/API29 serial `2ac6876`.
- **expected_observed_result**: The focused test passed 1/1 on ASUS/API35 and 1/1 on Mi/API29, with zero failures, errors or skips. The test started a connectable legacy advertiser and stopped its own instance; afterward the ASUS app still had its foreground service, GATT server registration and advertising entry.
- **date**: 2026-11-04
- **limitations**: This only confirms Nordic advertiser API startup on these two devices. It does not verify Windows discovery, exact on-air FIDO service data, host connection, authenticated pairing, registration/assertion, lifecycle matrix or trusted coverage. Parent T022/T039/T041 remain open; coverage is deferred.
- **status**: `Passed (focused advertiser-start check on API29 and API35) / Unverified (host discovery and full parent acceptance)`.
- **artifact_link**: [focused device-test summary](../../feature/fido2/build/reports/fido2/064/nordic-advertiser-device-test-20261104.txt), [ASUS result XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [Mi result XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi%20A2%20-%2010.xml)

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
- **artifact_link**: [cross-device discovery test summary](../../feature/fido2/build/reports/fido2/064/fido-advertiser-peer-discovery-20261104.txt), [ASUS result XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [Mi result XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi%20A2%20-%2010.xml)

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
- **artifact_link**: [ASUS connected-suite XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [Mi connected-suite XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi%20A2%20-%2010.xml), [ASUS settings XML](../../feature/settings/build/outputs/androidTest-results/connected/debug/TEST-ASUS_AI2302%20-%2015.xml), [Mi settings XML](../../feature/settings/build/outputs/androidTest-results/connected/debug/TEST-Mi%20A2%20-%2010.xml), [T039/T041](tasks.md)

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
- **expected_observed_result**: Common/FIDO2 host tests and settings unit tests completed successfully. Settings instrumentation passed 12/12 on each device. ASUS FIDO2 instrumentation ran 55 tests: 50 passed, 5 failed, 0 errors/skips; Mi ran 52: 47 passed, 5 failed, 0 errors/skips. The original run showed five research-class failures at trace preconditions. On the follow-up, comma-separated `notClass` excluded only `EmpiricalCalibrationDeviceTest`; four remaining research classes ran and failed before measurement because Perfetto was not active with the test package in `atrace_apps`. ASUS: 54 tests, 50 passed and 4 failed. Mi: 51 tests, 47 passed and 4 failed. Both had zero errors/skips, and all other executed tests in the follow-up passed. No measurement assertions or frozen-procedure arguments were reached. A corrected follow-up using `notPackage=com.chimali.fido2.util.performance` excluded exactly the five controlled tracing classes and passed 50/50 tests on ASUS and 47/47 on Mi, with zero failures/errors/skips. BLE-relevant `FidoBleSecurityDeviceTest` passed 4/4 on each phone, including actual registration of both FIDO and Device Information GATT services; Nordic advertiser and cross-device discovery cases also passed. The original full-suite Gradle invocation exited nonzero. The corrected package-filtered non-research follow-up succeeded.
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

# Implementation Evidence: Bluetooth Transport Selection

**Feature**: [spec.md](spec.md) | **Branch**: `064-bluetooth-transport-selection`  
**Delivery Coordination**: [integration.md](integration.md)  
**Status**: Software implementation and parent-gated BLE composition are present. The latest ASUS Android 15 Settings instrumentation passed 12 tests, including transport-state semantics and a 200% Compose text-scale scroll regression. High-contrast/native-scale review is documented separately; ASUS TalkBack, physical BLE ceremonies and trusted coverage nonregression remain Unverified. The Mi A2/API 29 FIDO2 device suite was run and recorded in Record 019; it was partial, with 42/48 passing, both BLE security tests passing, one Ed25519 verifier failure and five tracing-precondition failures. The latest full ASUS FIDO2 device suite had five tracing-precondition failures, so neither connected run produced a coverage-floor/completeness result. The ASUS `shrunkDebug` app currently fails at startup with a Keystore `AEADBadTagException` after package restoration; no app data was cleared.

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

# Implementation Evidence: FIDO BLE Transport

### Record 222: Exact Protected-Main and Candidate Captures Match; Formal BLE Gate Still Unverified
- **requirement**: BLE T038; complete local coverage, verify configured floors, and compare affected Tier 1 raw ratios using an independently trusted accepted-target baseline.
- **scope**: Full ASUS/API35 captures for protected main and the Feature 064 candidate with the same clean trusted harness and measurement fingerprint.
- **revision**: Target `f1fd56baf8641cea99edc7fafa8725fdda21044c`, tree `3a41f6da4b81d7d16e3d90afad6f1b0c9d881487`; candidate `82d5d7b002d92cae825094d7f7c79e06bf15c5e5`, tree `d65c753f1e260b444dbfdb40eff1fdfe208ffd48`; harness `82d5d7b002d92cae825094d7f7c79e06bf15c5e5`.
- **named_procedure**: Target Comparison run `b24e1d3c-e0e9-4896-91f6-cb41ea60db23` and Candidate run `5b27c72b-22e5-4e3d-83f7-7cce77321a45`. Both collection and tests passed, with matching fingerprint `ad37d140a261224264b9134c53aecfafe02b7f6a564eab496f6d68e206d2cddb`. Standalone verification passed candidate floors: repository 90,437/148,816 (60.771%); all configured Tier 1 and Tier 2 floors passed. Direct raw-ratio comparisons were nondecreasing for all applicable Tier 1 modules. FIDO2 rose from 54,633/86,070 instructions (63.475%) and 3,367/5,510 branches (61.107%) to 68,342/99,968 instructions (68.364%) and 4,106/6,578 branches (62.420%); unchanged Tier 1 data/domain/security ratios matched.
- **environment**: Windows 11; ASUS AI2302 Android 15/API35 (`R7AIB7002901AZ3`); Gradle 9.6.0; 2026-11-08 local time.
- **expected_observed_result**: Same-harness target/candidate workload is complete, tests pass, candidate floors pass, and measured Tier 1 raw ratios do not regress.
- **limitations**: This direct comparison does not satisfy T038's accepted-index requirement. The trusted index still references run `c1b392a9-f830-4283-83f2-44e36ac5d731` and digest `d71928160a4ce5f3f9a989f2fff6263d8246d9eb8fbbe8c49c88a0d9f9eabfa1`, not the fresh target run. The index was not changed. GitHub CLI authentication is invalid, so the mandatory live-target/integration check did not run; hosted enforcement remains waived.
- **status**: Raw comparison and candidate floors pass diagnostically. BLE T038 remains Unverified pending the reviewed accepted-baseline path and valid GitHub authentication.
- **artifact_link**: Self-contained diagnostic package at `D:\octav\source\repos\.worktrees\chimali\coverage-evidence\064-current-main-comparison-2026-11-09`; raw comparison report `assessments/064-raw-count-review.md`; parent Record 248.

**Feature**: [spec.md](spec.md) | **Branch**: `064-bluetooth-transport-selection`
**Parent Coordination**: [integration.md](../064-bluetooth-transport-selection/integration.md)
**Status**: BLE software implementation and parent-gated composition are present. After the Mi/API29 radio-off recovery change, ordinary local CI passed KtLint, Detekt, Release lint, compile, unit and configured KMP Android host checks without coverage (Record 076); the focused Nordic advertiser device test passed 1/1 on Mi/API29 (Record 077). The full FIDO2 host suite passed 1,133/1,133, `Fido2HomeViewModelTest` passed 2/2, and the dashboard UI class passed 6/6 on each phone (Records 071–072). The latest configured host/device run passed parent `ProcessTransportSelectionTest` 15/15, settings instrumentation 12/12 on each phone, and BLE GATT security 4/4 per device. The original full FIDO2 suites were 50/55 on ASUS/API35 and 47/52 on Mi/API29; a corrected package filter excluded the five controlled trace classes and the remaining suite passed 50/50 on ASUS and 47/47 on Mi; the trace procedures remain unverified (Record 069). The CTAP Device Information Service passed focused host/device checks and real Android service registration on API29/API35 (Record 067). Earlier Trace Processor inspection found zero FIDO2 slices and reported systrace parse failures (Records 050–051). A Nearby devices revocation crash from sticky foreground-service restart is fixed and verified on ASUS: service preflight stops without restart when permissions are missing; direct Settings grant plus explicit Start recovered BLE (Record 055). Android-only transport switching and reboot/radio recovery are recorded (parent Records 036–038; BLE Records 053–054); Mi/API29 also retained BLE selection through force-stop/relaunch and resumed advertising only after explicit Start (parent Record 054; BLE Record 073). The Mi Classic HID startup attempt then timed out at Android HID Device profile binding; switching back to BLE and restarting restored advertising (parent Record 055; BLE Record 074). Mi/API29 radio-off now surfaces a retryable failure and explicit Retry resumes BLE advertising (Record 075; parent Record 056). Mi cannot satisfy Feature 058 API35 coverage-device policy; coverage remains deferred. Physical Windows ceremonies, in-flight/stale-bond recovery, 50-cycle memory, TalkBack and trusted coverage comparison remain unverified.

---

## 1. Baseline & Worktree State (T001)

- **Worktree Path**: `D:\octav\source\repos\.worktrees\chimali\ofrenda`
- **Branch**: `064-bluetooth-transport-selection`
- **Base Revision**: `27e75da807d9836b01b6f5b3b838403554f49288`
- **Feature Ownership Scope**:
  - *BLE Transport Owner*: GATT Service `0xFFFD`, framing, buffers, `BleSessionOwner`, `BleNotificationWriter`, `RawCtapOperation`, `BleGattTransport`, and isolated unit/host tests.
  - *Parent Integration Owner*: Settings preference storage, UI, process gate `ProcessTransportSelection`, and app-level composition in `ChimaliApplication`.
- **Unavailable Hardware / Environments**: Physical Android BLE peripheral device and Windows 11 22H2+ test host are not attached to this headless environment. All physical ceremony evidence is recorded as `Unverified` until executed on target hardware.
- **Constitutional Waivers**: Feature-058 disabled hosted runner waiver remains in effect; local verification applies.

---

## 2. Requirement Traceability Mapping (T002)

| Local Feature Spec ID | Canonical BRD/TRD mapping | Code and verification evidence | Current evidence |
|---|---|---|---|---|
| **FR-001** (Primary FIDO GATT service) | `FR-BLE-010`; `TR-BLE-010` | `FidoBleGattServer`; `FidoBleGattContractTest` | Host contract passed; physical discovery unverified |
| **FR-002** (Characteristics and revision 0x20) | `FR-BLE-020`; `TR-BLE-010` | `FidoBleGattServer`; `FidoBleGattContractTest` | Host contract passed; radio negotiation unverified |
| **FR-003** (Continuous advertising) | `FR-BLE-010`; `TR-BLE-010` |`NordicBleAdvertiserController`; `FidoBleLifecycleTest` | Host lifecycle passed; physical advertisement timing unverified |
| **FR-004** (Dynamic MTU and 20–512 bytes) | `FR-BLE-020`; `TR-BLE-020` | `FidoBleFraming`; `BleSessionOwner`; framing tests | Host boundaries passed; device MTU behavior unverified |
| **FR-005** (Framing and sequence wrap) | `FR-BLE-050`; `TR-BLE-020` | `FidoBleFraming`; `FidoBleFramingTest` | Host protocol tests passed |
| **FR-006** (3-second receive deadline) | `FR-BLE-050`; `TR-BLE-020` | `BleSessionOwner`; `FidoBleRecoveryTest` | Virtual-clock host tests passed |
| **FR-007** (Authenticated numeric-comparison pairing) | `FR-BLE-030`; `TR-BLE-010` | `FidoBleGattServer`; GATT contract and security device tests | Host auth rejection passed; device source compiled; numeric comparison unverified |
| **FR-008** (Raw CTAP routing/status TX) | `FR-BLE-080`; `TR-BLE-030` | `BleGattTransport`; `RawCtapOperation`; CTAP host tests | Host boundary passed; physical ceremonies unverified |
| **FR-009** (Ordered notification delivery) | `FR-BLE-050`; `TR-BLE-020` | `BleNotificationWriter`; writer/assertion tests | Host queue and ordering tests passed |
| **FR-010** (Foreground service lifecycle) | `FR-BLE-080`; `TR-BLE-010` | `Fido2TransportService`; `FidoBleLifecycleDeviceTest` | Device test source compiled only; background service behavior unverified |
| **FR-011** (Explicit Stop cleanup) | `FR-BLE-080`; `TR-BLE-010/040` | `BleGattTransport.disconnect`; `FidoBleLifecycleTest`; Record 026 | Host cleanup passed; ASUS real-service Stop-to-advertising-removal measured 476ms (<1s); interruption/reconnect cleanup unverified |
| **FR-012** (Typed unavailable status) | `FR-BT-SEL-050`; `TR-BT-SEL-020` | `ProcessTransportSelection`;`NeutralTransportContractTest` | Host selection/capability tests passed; unsupported hardware unverified |
| **FR-013** (Single active host) | `FR-BLE-040`; `TR-BLE-020` | `BleSessionOwner`; lifecycle/GATT contract tests | Host exclusivity passed |
| **FR-014** (Stale bond recovery) | `FR-BLE-070`; `TR-BLE-040` | GATT auth rejection, BLE transport error state, Home pairing guidance and Retry; `FidoBleLifecycleTest` | Generic auth/link failure recovery host tests passed; exact stale-bond cause and physical recovery unverified |
| **FR-015** (Keepalive cadence) | `FR-BLE-060`; `TR-BLE-040` | Notification writer and CTAP processing loop; assertion/cancellation tests | Host cadence tests passed; physical cadence unverified |
| **FR-016** (Cooperative CMD_CANCEL) | `FR-BLE-060`; `TR-BLE-040` | `BleGattTransport`; `FidoBleCancellationTest` | Host cancellation passed; Android prompt dismissal unverified |
| **SC-001** (Windows MakeCredential) | `FR-BLE-030/050/060/080`; `TR-BLE-030` | Registration / physical BLE procedure | Unverified: no Android radio or Windows host |
| **SC-002** (Windows GetAssertion) | `FR-BLE-030/050/060/080`; `TR-BLE-030` | Assertion / physical BLE procedure | Unverified: no Android radio or Windows host |
| **SC-003** (Fragmentation byte integrity) | `FR-BLE-050`; `TR-BLE-020` | `FidoBleFramingTest`, independent wire vectors and physical link | Host framing passed; secured-link integrity unverified |
| **SC-004** (50 reconnect cycles) | `FR-BLE-040/070`; `TR-BLE-020/040` | Physical reconnect stress procedure | Unverified: device cycles and memory observation unavailable |
| **SC-005** (Start/Stop timing) | `FR-BLE-010/080`; `TR-BLE-010` | Physical startup, Stop and background procedure | ASUS Start 419ms, Stop 476ms, and 10-second advertising persistence passed (Record 026); interruption/cycle behavior remains unverified |
| **SC-006** (Internal operation latency) |`NFR-PERF-030`; `TR-PERF-010` | Synthetic latency assessment | Host pass: 10 measured samples per operation; maxima GetAssertion 32.272ms, GetInfo 5.151ms, MakeCredential 13.635ms, ResetAuthenticator 2.783ms. Current release AAB/base and idle/advertising memory comparisons are recorded in Records 011, 030 and 031; physical timing remains diagnostic/unverified. |

---

## 3. Evidence Log (TRD §9.1)

### Record 001: Initial Evidence Baseline
- **requirement**: `FR-BLE-010` through `FR-BLE-080`, `TR-BLE-010` through `TR-BLE-040`
- **scope**: Baseline environment, worktree state, and canonical requirement mapping
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288`
- **named_procedure**: `speckit-implement` Phase 1 Setup (T001, T002, T003)
- **environment**: Windows 11, JDK 17, Android SDK 37, Git worktree `ofrenda`
- **expected_observed_result**: Canonical IDs allocated without collision; traceability matrix initialized; runtime tests not yet executed.
- **date**: 2026-11-02
- **limitations**: Headless agent environment without physical Bluetooth radio or Windows 11 pairing partner.
- **status**: `Unverified`
- **artifact_link**: [implementation-evidence.md](implementation-evidence.md)

### Record 002: Nordic Advertiser Dependency Resolution & Build Verification (T004)
- **requirement**: `TR-BLE-010`,`NFR-ARCH-020`
- **scope**: Dependency pinning, resolution, and toolchain build compatibility
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288`
- **named_procedure**: `speckit-implement` Phase 1 Setup (T004)
- **environment**: Windows 11, JDK 17, Gradle 9.6.0, AGP 9.4.1, Kotlin 2.3.21, compileSdk 37, minSdk 28
- **expected_observed_result**:`no.nordicsemi.android.kotlin.ble:advertiser:1.3.1` resolves successfully from Maven Central; `:feature:fido2` compiles and passes `testAndroidHostTest` (55 actionable tasks executed successfully).
- **date**: 2026-11-02
- **limitations**: Production artifact contains only advertiser; high-level server and mock modules are excluded as specified in research.md.
- **status**: `Passed`
- **artifact_link**: [gradle/libs.versions.toml](../../gradle/libs.versions.toml), [feature/fido2/build.gradle.kts](../../feature/fido2/build.gradle.kts)

### Record 003: Core BLE Framing, Raw CTAP Operation, and Notification Writer (T005–T013, T017)
- **requirement**: `FR-BLE-010`, `FR-BLE-020`, `FR-BLE-050`, `FR-BLE-080`, `TR-BLE-010`, `TR-BLE-020`, `TR-BLE-030`
- **scope**: Packet framing/reassembly, neutral transport contracts, raw CTAP dispatch boundary, bounded FIFO notification writer, dynamic per-session capacities in `BleSessionOwner`.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288`
- **named_procedure**: Host unit testing (`FidoBleFramingTest`, `RawCtapOperationTest`,`NeutralTransportContractTest`, `BleNotificationWriterTest`, `BleSessionOwnerTest`)
- **environment**: Windows 11, OpenJDK 17, Gradle 9.6.0, Kotlin Test / JUnit 5 runner
- **expected_observed_result**: 100% pass across all framing invariants (modulo 128 sequences, 64-byte payload fragmentation, max 512-byte write capacity, dynamic ATT MTU negotiation, keepalive coalescing, 5s notification stall detection).
- **date**: 2026-11-02
- **limitations**: Executed on JVM host environment; hardware radio timing unverified.
- **status**: `Passed`
- **artifact_link**: [FidoBleFraming.kt](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/FidoBleFraming.kt), [RawCtapOperation.kt](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/RawCtapOperation.kt), [BleSessionOwner.kt](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleSessionOwner.kt), [BleNotificationWriter.kt](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleNotificationWriter.kt)

### Record 004: GATT Server Contract, Admission, and Peripheral Lifecycle (T014, T016, T018, T019, T024)
- **requirement**: `FR-BLE-010`, `FR-BLE-020`, `FR-BLE-030`, `FR-BLE-040`, `TR-BLE-010`, `TR-BLE-020`
- **scope**: Public GATT server callback admission, explicit ATT error codes, primary service `0xFFFD`, characteristic UUIDs `F1D0FFF1`–`F1D0FFF4`, CCCD subscriptions, single-host exclusivity, register-before-advertise, session generation fencing.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288`
- **named_procedure**: Host test execution (`FidoBleGattContractTest`, `FidoBleLifecycleTest`)
- **environment**: Windows 11, OpenJDK 17, Gradle 9.6.0
- **expected_observed_result**: 8 GATT contract tests passed (rejection of unauthenticated writes, prepared write rejection, invalid offset handling, revision bitfield 0x20 read/write, Control Point length read). 8 lifecycle tests passed (idempotent connect/disconnect, failure propagation, single host rejection of concurrent peers, session generation invalidation).
- **date**: 2026-11-02
- **limitations**: Android Bluetooth stack mocked on JVM; physical numeric comparison pairing requires target device.
- **status**: `Passed`
- **artifact_link**: [FidoBleGattServer.kt](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/FidoBleGattServer.kt), [BleGattTransport.kt](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleGattTransport.kt), [FidoBleGattContractTest.kt](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/FidoBleGattContractTest.kt), [FidoBleLifecycleTest.kt](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/FidoBleLifecycleTest.kt)

### Record 005: CTAP2 Assertion Flow, Virtual-Clock Recovery, and CANCEL/Stall Handling (T021, T022, T030–T033)
- **requirement**: `FR-BLE-050`, `FR-BLE-060`, `FR-BLE-080`, `TR-BLE-020`, `TR-BLE-030`, `TR-BLE-040`
- **scope**: End-to-end unframed assertion dispatching, fragmented CBOR response FIFO ordering, UV error propagation (`CTAP2_ERR_OPERATION_DENIED`), unauthorized error handling, 3-second virtual-clock receive deadline, sequence mismatch reset, valid CANCEL bypassing BUSY, malformed CANCEL length rejection, 5-second notification stall trigger.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288`
- **named_procedure**: Host test execution (`FidoBleAssertionTest`, `FidoBleRecoveryTest`, `FidoBleCancellationTest`)
- **environment**: Windows 11, OpenJDK 17, Gradle 9.6.0
- **expected_observed_result**: 13 tests passed across all three suites (4 assertion tests, 5 virtual clock recovery tests, 4 cancellation and queue tests). Zero failures.
- **date**: 2026-11-02
- **limitations**: Synthetic assertions evaluated with virtual time and mock core handler; Windows 11 physical credential ceremony marked Unverified per T020/T023.
- **status**: `Passed`
- **artifact_link**: [FidoBleAssertionTest.kt](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/FidoBleAssertionTest.kt), [FidoBleRecoveryTest.kt](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/FidoBleRecoveryTest.kt), [FidoBleCancellationTest.kt](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/FidoBleCancellationTest.kt)

### Record 006: Parent-Gated BLE Composition and Full Host Verification (T028, Selection:T012–T016)
- **requirement**: `FR-BLE-010`, `FR-BLE-020`, `FR-BLE-030`, `FR-BLE-040`, `FR-BLE-080`, `FR-BT-SEL-030`, `FR-BT-SEL-040`, `TR-BLE-010`, `TR-BLE-020`, `TR-BT-SEL-030`
- **scope**: Real Android GATT server host, Nordic advertiser lifecycle, process-gated lazy BLE/Classic factory binding, single frozen transport handle shared by app, service and home, register-before-advertise, failure cleanup/retry and no fallback.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus uncommitted worktree changes on `064-bluetooth-transport-selection`.
- **named_procedure**: `:app:compileDebugKotlin :feature:fido2:testAndroidHostTest :feature:fido2:compileAndroidDeviceTest :feature:settings:compileDebugAndroidTestKotlin`.
- **environment**: Windows 11 x64 worktree `ofrenda`; Gradle wrapper 9.6.0; Android SDK 37; no attached Android device (`adb devices` returned no serials).
- **expected_observed_result**: `BUILD SUCCESSFUL`; the app compiled; all 1,112 FIDO2 host tests passed with zero failures, errors or skips; Android security/lifecycle device tests and settings Compose tests compiled. The service re-arms its UI event collector after a failed start before a retry attempt.
- **date**: 2026-11-02
- **limitations**: Device tests were not executed. No physical phone, BLE radio, numeric-comparison pairing peer or Windows FIDO client was attached; compile and host fakes do not establish radio behavior.
- **status**: `Passed (app compile and host behavior) / Compiled Only (device/UI test sources) / Unverified (physical BLE acceptance)`.
- **artifact_link**: [AndroidFidoBleGattServerHost.kt](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/AndroidFidoBleGattServerHost.kt), [Fido2TransportService.kt](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/service/Fido2TransportService.kt), [FidoBleLifecycleDeviceTest.kt](../../feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/service/FidoBleLifecycleDeviceTest.kt)

### Record 007: Host-Simulated FIDO Operation Latency (T037 — Partial)
- **requirement**: `SC-006`,`NFR-PERF-030`, `TR-PERF-010`
- **scope**: Ten measured occurrences each of GetAssertion, GetInfo, MakeCredential and ResetAuthenticator; this is the latency sub-procedure only.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus source fingerprint `15df35052a9e764e12ea0065be543283aaa66f6abf1f5c961b69781011660ec9`.
- **named_procedure**: `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.performance.SimulatedLatencyAssessmentTest`; report run ID `f5c1d92c-9bd5-434c-ac71-822bd4a33d15`.
- **environment**: Windows 11 amd64; OpenJDK 21.0.10; JVM `System.nanoTime`; synthetic relying-party, storage, credential derivation and user-verification adapters.
- **expected_observed_result**: `PASS`; all four operation types measured ten times and every assessed maximum was below 200ms: GetAssertion 2.72335ms, GetInfo 8.01045ms, MakeCredential 6.47775ms, ResetAuthenticator 0.19460ms.
- **date**: 2026-11-02
- **limitations**: BLE/HID framing, physical transport, production storage and human verification are outside the workload. Release bundle size was subsequently measured in Record 011; memory profiling remains unverified, so T037 remains incomplete.
- **status**: `Passed (latency sub-procedure only)`.
- **artifact_link**: [latency assessment JSON](../../feature/fido2/build/reports/fido2/060/simulated-latency-assessment.json), [latency quickstart](../060-fido2-latency-measurement/quickstart.md)

### Record 008: Repository Local CI and CTAP Error Response Regression
- **requirement**: `FR-BLE-050`, `FR-BLE-080`, `TR-BLE-030`, `TR-BLE-040`
- **scope**: Full repository local CI after the BLE handler boundary was changed to return CTAP2_ERR_OTHER on unexpected CTAP-operation exceptions, plus the regression assertion that the connected BLE host receives that response.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus uncommitted worktree changes on `064-bluetooth-transport-selection`.
- **named_procedure**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache`; finished successfully on 2026-11-02.
- **environment**: Windows 11 x64; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5; no attached Android device.
- **expected_observed_result**: Full local CI completed successfully. FIDO2 Android host suite: 1,112 tests, zero failures, errors or skips. KtLint, Detekt, release lint, project compilation and configured unit tests passed; BLE device-test source compiled.
- **date**: 2026-11-02
- **limitations**: Device suites were not run on hardware; physical pairing, Windows ceremonies, memory/device cycle checks and trusted coverage integration remain unverified. The host test does not establish physical CTAP behavior.
- **status**: `Passed (local software verification) / Compiled Only (Android device tests) / Unverified (physical acceptance and trusted integration)`.
- **artifact_link**: [BleGattTransport.kt](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleGattTransport.kt), [FidoBleCancellationTest.kt](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/FidoBleCancellationTest.kt), [child quickstart](quickstart.md)

### Record 009: Parent-Gated Ceremony Save/Cancel Regression and Full Local CI
- **requirement**: `FR-BT-SEL-030`, `FR-BT-SEL-040`, `FR-BLE-050`, `FR-BLE-080`, `TR-BT-SEL-030`, `TR-BLE-030`
- **scope**: Full repository local CI after adding bounded registration/authentication save and cancellation regression cases across the selected BLE and Classic process handles.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus uncommitted worktree changes on `064-bluetooth-transport-selection`.
- **named_procedure**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache`; finished successfully on 2026-11-02.
- **environment**: Windows 11 x64; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5; no attached Android device.
- **expected_observed_result**: Full local CI completed successfully. FIDO2 Android host suite: 1,114 tests, 0 failures, 0 errors, 0 skipped. The parent test holds an approved or cancelled host ceremony while a different mode is saved and verifies the selected handle remains connected; KtLint, Detekt, release lint and configured compile/test stages passed.
- **date**: 2026-11-02
- **limitations**: Parent ceremony tests use synthetic host-side gates and do not establish BLE radio behavior. Android device tests, physical pairing/Windows ceremonies, memory/cycle checks and trusted coverage integration remain Unverified.
- **status**: `Passed (local software verification) / Compiled Only (Android device tests) / Unverified (physical acceptance and trusted integration)`.
- **artifact_link**: [TransportSelectionCeremonyTest.kt](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/TransportSelectionCeremonyTest.kt), [parent integration coordination](../064-bluetooth-transport-selection/integration.md), [child quickstart](quickstart.md)

### Record 010: Pairing Recovery, Nordic Contract Documentation, and Full Local CI
- **requirement**: `FR-BLE-030`, `FR-BLE-070`, `FR-BLE-080`, `TR-BLE-010`, `TR-BLE-040`
- **scope**: Active-host authentication failure cleanup and recovery guidance; documented Nordic advertiser-only selection and native Android GATT supplement; full repository regression after traceability annotation changes.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus uncommitted worktree changes on `064-bluetooth-transport-selection`.
- **named_procedure**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache`; focused FIDO2 host/device-test compilation and settings Compose test compilation.
- **environment**: Windows 11 x64; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5; no attached Android device.
- **expected_observed_result**: Final full local CI completed successfully in 142.78s. FIDO2 Android host suite: 1,116 tests, 0 failures, 0 errors, 0 skipped. Device/UI test sources compile but were not executed.
- **date**: 2026-11-02
- **limitations**: Host tests do not establish physical stale-bond diagnosis, numeric-comparison pairing, Windows ceremonies, lifecycle timings or accessibility. Published AAR/POM, minimum-SDK and license review remains open; coverage integration and accepted-target nonregression remain Unverified. Release bundle size is recorded in Record 011; physical FIDO2 device-suite results are recorded in Record 012 and runtime memory in Record 013.
- **status**: `Passed (local software verification) / Executed with failures (Android device suite; see Record 012) / Unverified (physical interoperability, dependency metadata review and trusted coverage integration)`.
- **artifact_link**: [FidoBleGattServer.kt](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/FidoBleGattServer.kt), [Nordic research](research.md), [Bluetooth resources](../../docs/resources.md), [BLE quickstart](quickstart.md), [local CI](../../tools/local-ci.ps1)

### Record 011: Current Release Bundle Size Comparison (T037)
- **requirement**:`NFR-PERF-060`, T037
- **scope**: Release AAB and compressed/uncompressed `base/` module byte sizes for the current implementation worktree compared with a clean build at its pre-change commit. The candidate includes all current branch/worktree changes, including concurrent feature edits; this is not an isolated BLE-only contribution. Rebuilt after the BLE peer-disconnect cancellation, stale-callback guards and session-generation notification fencing in Record 032.
- **revision**: Candidate `HEAD` `c9195ea608ff3394e32aa217e7e640e5579d6f4d` plus the dirty current worktree on `064-bluetooth-transport-selection`; clean baseline commit `27e75da807d9836b01b6f5b3b838403554f49288`.
- **named_procedure**: `:app:bundleRelease --no-problems-report --no-configuration-cache --max-workers=1`; compare AAB length, SHA-256, and ZIP `base/*` compressed/uncompressed totals with the retained clean baseline. Release configuration has `isMinifyEnabled = true` and `isShrinkResources = true` in `app/build.gradle.kts`.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5; same local dependency/cache environment. Candidate worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; clean baseline clone `D:\octav\source\repos\.worktrees\chimali\ofrenda-size-baseline-27e75da`.
- **expected_observed_result**: Current candidate AAB 41,494,991 bytes; compressed base module 25,528,121 bytes and uncompressed base 63,500,669 bytes. Candidate SHA-256 is `ce732a6bf7c979e50f0505bf95935f2940f49fd2cc981a88a65260c63fe09825`. Clean baseline AAB is 41,380,574 bytes; compressed/uncompressed base are 25,485,443/63,407,692 bytes. Candidate deltas are +114,417 bytes AAB, +42,678 bytes compressed base and +92,977 bytes uncompressed base, within the 50,000,000-byte base-module and +500,000-byte feature-delta budgets.
- **date**: 2026-11-03
- **limitations**: Byte-size comparison is not Play delivery-size measurement or isolated BLE attribution; bundles use the repository devRelease signing configuration. Runtime-memory comparison in Record 030 predates the disconnect cancellation and session-generation fencing changes and does not establish memory-trim, peak-workload or reconnect-cycle leak behavior; the latter is T035.
- **status**: `Passed (current release AAB/base byte-size limits and minification/resource-shrinking configuration)`.
- **artifact_link**: [current release AAB](../../app/build/outputs/bundle/release/app-release.aab), [baseline release AAB](../../../ofrenda-size-baseline-27e75da.aab), [current byte counts and SHA-256 comparison](../../feature/fido2/build/reports/fido2/064/release-bundle-size-assessment.json), [release build configuration](../../app/build.gradle.kts), [memory comparison Record 030](#record-030-asus-idle-versus-advertising-memory-snapshot-t037--partial), [T037](tasks.md)

### Record 012: Physical Android Device-Test Run (T036 — Partial)
- **requirement**: `FR-BLE-020`, `FR-BLE-030`, `FR-BLE-060`, `TR-BLE-010`, `TR-BLE-020`, T036
- **scope**: Configured FIDO2 Android instrumentation suite on one attached physical phone; this does not include external CTAP-host interoperability or Android API 31+ service behavior. Includes the rerun with system tracing enabled.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus uncommitted worktree changes on `064-bluetooth-transport-selection`.
- **named_procedure**: `:feature:fido2:connectedAndroidTest` (executes `:feature:fido2:connectedAndroidDeviceTest`).
- **environment**: Xiaomi Mi A2; Android 10 / API 29; `/proc/meminfo` MemTotal 3,829,332 kB; Windows 11 x64 host; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: Initial 48-test run: 42 passed, 6 failed, 0 errors, 0 reported skips. With Perfetto/atrace explicitly enabled for `com.chimali.fido2.test`, the rerun still executed 48 tests and produced 43 passes, 5 failures, 0 errors, 0 reported skips. Both `FidoBleSecurityDeviceTest` cases passed. The tracing setup removed the disabled-tracing failures; remaining failures are the API 29 absence of a non-BC platform Ed25519 verifier and four research/performance experiment tests requiring their exact predeclared per-run instrumentation arguments. `FidoBleLifecycleDeviceTest` is annotated for API 31+ and did not execute on this API 29 device.
- **date**: 2026-11-02
- **limitations**: The runs do not establish real GATT traffic with a separate authenticator, numeric-comparison pairing, Android 12+ foreground-service lifecycle, Windows ceremonies, or accessibility. The four protocol-specific performance experiments were not launched with synthetic substitute arguments; their frozen run identities are part of separate performance-investigation procedures and must not be invented for this generic suite. This Android 10/API 29 device is below Feature 058's API 35 minimum for an authorized coverage assessment. The complete device-suite checklist remains open.
- **status**: `Partial (BLE security device cases passed; trace setup verified; full suite failed 5 tests; coverage device prerequisite unmet)`.
- **artifact_link**: [connected-device XML results](../../feature/fido2/build/reports/fido2/064/mi-connected-suite-post-retry-fix-20261104.xml), [instrumentation HTML report](../../feature/fido2/build/reports/androidTests/connected/androidMain/index.html), [FidoBleSecurityDeviceTest.kt](../../feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/data/transport/FidoBleSecurityDeviceTest.kt)

### Record 013: Release-App Memory Snapshot and Instrumented Memory/Lifecycle Checks (T037 — Partial)
- **requirement**:`NFR-PERF-060`, T037
- **scope**: One process PSS snapshot after installing and launching the minified release APK on the 4 GB-class phone, plus the app's configured instrumentation cases for startup, synthetic credential process restart, memory-trim callback handling and mnemonic-owner lifecycle cleanup.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus uncommitted worktree changes on `064-bluetooth-transport-selection`.
- **named_procedure**: Build with `:app:assembleRelease`; install/launch `com.chimali`; sample using `adb shell dumpsys meminfo com.chimali` and `adb shell cat /proc/meminfo`; run `:app:connectedShrunkDebugAndroidTest`.
- **environment**: Xiaomi Mi A2; Android 10 / API 29; MemTotal 3,829,332 kB; Windows 11 x64; Android SDK 37; Microsoft OpenJDK 25.0.5; release APK and shrunkDebug instrumentation variant.
- **expected_observed_result**: At the observed release-app launch snapshot, process total PSS was 46,116 kB (45.04 MiB), below the standard-tier 150 MB baseline limit. The OS permission controller was foreground while the app's launch-time permission request was displayed; the app process had one Activity. Separately, app instrumentation passed 11 tests with 0 failures/errors/skips, including `severeAndroidTrimCallbacksFenceTheProductionWalletGeneration`, `activityDisposalClearsSyntheticMnemonicOwnerWithoutRetainingLifecycleObjects`, both `ProductionAppProcessRestartTest` cases and startup composition.
- **date**: 2026-11-02
- **limitations**: No permission was granted through the OS prompt. The PSS snapshot was captured while that system prompt was foreground and is not a settled in-app baseline. Instrumentation exercises explicit memory-trim callbacks and synthetic lifecycle cleanup, not a measured ceremony stress peak or 50 BLE reconnect cycles. No high-end-tier adaptation check or full LeakCanary trace from a real ceremony was captured. T037 remains open; these results do not establish the full memory budget.
- **status**: `Passed (11 app instrumentation cases for startup/process restart/memory-trim/lifecycle) / Observed (single launch PSS sample) / Unverified (tiered baseline/peak and real ceremony leak/cycle acceptance)`.
- **artifact_link**: [release APK](../../app/build/outputs/apk/release/app-release.apk), [app device-test XML](../../app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-Mi%20A2%20-%2010.xml), [app instrumentation report](../../app/build/reports/androidTests/connected/shrunkDebug/index.html), [memory-trim test](../../app/src/androidTest/kotlin/com/chimali/WalletSessionMemoryPressureCallbackTest.kt), [T037](tasks.md), [NFR-PERF-060](../../docs/brd.md)

### Record 014: Opt-In Coverage Gate Attempt (T038 — Unverified)
- **requirement**: Feature-058 Tier 1/Tier 2/repository floors, coverage completeness and accepted-target nonregression; T038
- **scope**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` local run. It reached the configured coverage gate but stopped during the FIDO2 host suite before coverage analysis, completeness or floor decisions were emitted.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus uncommitted worktree changes on `064-bluetooth-transport-selection`.
- **named_procedure**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage`; failure from `:feature:fido2:testAndroidHostTest` during `hdkHostCoverage`.
- **environment**: Windows 11 x64; Xiaomi Mi A2 Android 10 / API 29 attached; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: The earlier full `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` run passed static analysis, release lint, all-module compilation, unit tests and core host suites; its coverage-stage core device test passed 1/1. Its FIDO2 host run completed 1,116 tests with one failure: `CoroutineTraceLifecycleTest.lifecycle pairs every resume across dispatcher changes` observed resumed cookies `[100, 101, 103]` versus suspended `[100, 101, 102, 103]`. The lifecycle test now waits for its terminal callback before comparing queues; a focused run completed all 7 tests with 0 failures/errors, and a subsequent focused Gradle invocation exited successfully. A direct coverage-gate rerun reached FIDO2 connected instrumentation but failed on that device suite before coverage verification. With Perfetto enabled, the suite reported 43/48 passing and the five failures detailed in Record 012. The gate produced no canonical counters, inventory, completeness or floor decisions. The settings connected-device suite separately passed 11/11 in parent Record 009.
- **date**: 2026-11-02
- **limitations**: Coverage remains unverified; the attempted connected device is Android 10/API 29, below the Feature 058 policy's minimum API 35. Host/device test failures prevented canonical counters, inventory, completeness and floor decisions. The retry used a worktree-local Gradle home with the existing read-only dependency cache because the shared Gradle cache lock was inaccessible; the focused lifecycle test passed, but the full gate still failed at FIDO2 device tests. This worktree is dirty, so it has no exact candidate commit for `tools/integration-ci.ps1`; no trusted external evidence root, compatible accepted-target bundle and independently trusted `baselines/current.json` index were supplied or located in the measured tree. No target-only baseline or index was created. Hosted enforcement remains waived/disabled under feature-058. T038 remains open and all coverage decisions are Unverified.
- **status**: `Failed (prior coverage gate stopped on host-test failure; retry stopped on connected FIDO2 device failures) / Passed (focused 7-test coroutine lifecycle suite after race fix) / Unverified (authorized-device coverage completeness, floors and trusted raw-count regression)`.
- **artifact_link**: [latest FIDO2 device XML results](../../feature/fido2/build/reports/fido2/064/mi-connected-suite-post-retry-fix-20261104.xml), [focused lifecycle test XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.util.performance.CoroutineTraceLifecycleTest.xml), [captured coverage-gate outcome](../../feature/fido2/build/reports/fido2/064/coverage-gate-attempt.json), [local CI](../../tools/local-ci.ps1), [coverage plan](../058-enforce-coverage-targets/plan.md), [integration task](tasks.md)

### Record 015: FIDO2 Host Suite Rerun (T036 — Host Portion)
- **requirement**: `FR-BLE-020`, `FR-BLE-030`, `FR-BLE-060`, `TR-BLE-010`, `TR-BLE-020`, T036
- **scope**: Full configured FIDO2 Android host-test suite and the declared synthetic per-operation latency assessment. The physical Android results remain in Record 012.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus current uncommitted worktree changes on `064-bluetooth-transport-selection`.
- **named_procedure**: `:feature:fido2:testAndroidHostTest --max-workers=1 --no-configuration-cache --no-problems-report`; separately isolated `SimulatedLatencyAssessmentTest` rerun.
- **environment**: Windows 11 x64; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5. No Android device was used by the host suite.
- **expected_observed_result**: The first full rerun completed 1,116 tests with one failure in `SimulatedLatencyAssessmentTest` because its measured assessment disposition was `FAIL`; the isolated test then passed. A subsequent serialized full-suite rerun completed 1,116 tests with 0 failures, 0 errors, and 0 skips. Its saved latency report disposition was `PASS`; each of MakeCredential, GetAssertion, GetInfo, and ResetAuthenticator had 10 measured occurrences, with maxima below 200 ms.
- **date**: 2026-11-03
- **limitations**: The successful rerun establishes only the synthetic host workload. It does not replace the failing device-suite outcomes in Record 012 or establish physical BLE registration/assertion, Windows numeric-comparison pairing, lifecycle/cycle acceptance, or trusted coverage integration. T036 remains open.
- **status**: `Passed (host suite and synthetic latency assessment on serialized rerun) / Failed (initial host rerun had one latency-disposition failure) / Unverified (physical interoperability and trusted coverage)`.
- **artifact_link**: [host test report](../../feature/fido2/build/reports/tests/testAndroidHostTest/index.html), [latency assessment JSON](../../feature/fido2/build/reports/fido2/060/simulated-latency-assessment.json), [latency assessment test](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/performance/SimulatedLatencyAssessmentTest.kt), [device-suite evidence](#record-012-physical-android-device-test-run-t036--partial)

### Record 016: Nordic Advertiser Collection and API 35 Lifecycle (T036 — Partial)
- **requirement**: `FR-BLE-020`, `FR-BLE-030`, `FR-BLE-060`, `TR-BLE-010`, `TR-BLE-020`, T036
- **scope**: Nordic advertiser flow collection and startup-failure handling, the full FIDO2 Android host suite, filtered `FidoBleLifecycleDeviceTest` instrumentation on the attached ASUS Android 15/API 35 phone and configured Android 10/API 29 phone, and live Android BLE registration. The instrumentation validates service foreground lifecycle and retry cleanup; it does not exercise a separate host, pairing, CTAP traffic, or a Windows ceremony.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus uncommitted worktree changes on `064-bluetooth-transport-selection`.
- **named_procedure**: `:feature:fido2:testAndroidHostTest --max-workers=1 --no-configuration-cache --no-problems-report`; `:feature:fido2:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.chimali.fido2.service.FidoBleLifecycleDeviceTest --no-configuration-cache --no-problems-report`; `:app:assembleShrunkDebug`, install with `adb install -r`, start Authenticator, inspect `dumpsys bluetooth_manager` and GATT service maps; targeted `ktlintAndroidMainSourceSetCheck` and `ktlintAndroidHostTestSourceSetCheck`; `git diff --check`.
- **environment**: Windows 11 x64; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; Gradle 9.6.0; Android SDK 37; ASUS_AI2302 Android 15/API 35 (serial `R7AIB7002901AZ3`) and Xiaomi Mi A2 Android 10/API 29 (serial `2ac6876`).
- **expected_observed_result**: Nordic's cold advertising `Flow` is now collected; startup waits for an `ADVERTISE_SUCCESS` event and reports startup/stream failures. The full FIDO2 host suite ran 1,118 tests with 0 failures, 0 errors, and 0 skips; the new`NordicBleAdvertiserControllerTest` passed 2/2, and the synthetic latency report disposition was `PASS` with ten operations each for MakeCredential, GetAssertion, GetInfo, and ResetAuthenticator. ASUS Android 15 ran 3 lifecycle tests with 0 failures, 0 errors, and 0 skips: `serviceIsPrivateAndDeclaresConnectedDeviceForegroundType`, `repeatedStartKeepsForegroundNotificationAndStopCleansTransport`, and `retryAfterFailedStartupConnectsAgainAndRestoresForegroundNotification`. Android 10 ran 0 tests because the lifecycle class is gated to API 31+. On the ASUS after installing `shrunkDebug`, Bluetooth showed an active connectable Chimali advertiser and registered FIDO service `0000fffd-0000-1000-8000-00805f9b34fb` with characteristics F1D0FFF1–FFF4. After the user canceled PC control, the app's Stop Authenticator action removed its advertising set and foreground service. Targeted Android main and host-test source KtLint checks passed, and `git diff --check` passed.
- **date**: 2026-11-03
- **limitations**: Live advertiser and service registration prove local Android BLE startup only. No external GATT host connected or exchanged CTAP messages. Windows pairing/numeric comparison, browser registration/assertion, selected-handle composition, accessibility, coverage acceptance, and the parent settings/switch matrix are unverified. PC control was canceled before pairing or ceremony verification; T036 remains open.
- **status**: `Passed (host suite 1,118/1,118; 3 API 35 lifecycle cases; live local BLE registration; targeted lint and whitespace checks) / Not applicable (API 29 device ran 0 API-gated cases) / Unverified (external host interoperability and remaining parent gates)`.
- **artifact_link**: [FIDO2 host test report](../../feature/fido2/build/reports/tests/testAndroidHostTest/index.html), [advertiser controller test XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.transport.NordicBleAdvertiserControllerTest.xml), [API 35 instrumentation XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [API 29 instrumentation XML](../../feature/fido2/build/reports/fido2/064/mi-connected-suite-post-retry-fix-20261104.xml), [lifecycle device-test source](../../feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/service/FidoBleLifecycleDeviceTest.kt), [Nordic advertiser source](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/NordicBleAdvertiserController.kt), [T036](tasks.md)

### Record 017: Initial Windows Pairing and GATT Authentication Recovery (T020 — Historical Observation)
- **requirement**: `FR-BLE-030`, `FR-BLE-070`, `TR-BLE-040`, T020, T036
- **scope**: User-initiated Windows Bluetooth pairing attempt observed from ASUS Android 15 system logs, diagnosis of the resulting Chimali error, and the host-side regression change. No Windows UI was controlled by the implementation driver.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus current uncommitted worktree changes on `064-bluetooth-transport-selection`.
- **named_procedure**: Inspect current Chimali UI hierarchy, Android Bluetooth permission state, `dumpsys activity services`, `dumpsys bluetooth_manager`, and Android `logcat`; run `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.transport.FidoBleGattContractTest --no-configuration-cache --no-problems-report`; run targeted Android main/host KtLint checks; assemble `:app:assembleShrunkDebug` and install with `adb install -r`.
- **environment**: ASUS_AI2302 / ASUS Zenfone 10, Android 15/API 35, app variant `shrunkDebug`, Windows host and Bluetooth adapter details not captured. The Android app had `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, and `BLUETOOTH_SCAN` granted.
- **expected_observed_result**: Before the source change, Android system logs showed the pairing dialog was accepted, then the bond transitioned `BOND_NONE` → `BOND_BONDING` → `BOND_BONDED`. Chimali subsequently displayed `Error Occurred / Bluetooth pairing authentication failed`; its GATT server reported a disconnect with status 0. Source tracing identifies that UI state as the `onAuthenticationFailure` path reached after an unauthenticated GATT request; the pre-change app did not log the bond state at that exact request, so a request arriving during `BOND_BONDING` is a supported diagnosis, not a directly observed value. The server now returns `GATT_INSUFFICIENT_AUTHENTICATION` without tearing down the session when the bond is still in progress; it continues to reject unauthenticated data and preserves the existing failure handling for unbonded requests. The focused GATT contract test suite passed, targeted Android main and host-test KtLint checks passed, the shrunkDebug app built successfully, and the updated APK installed successfully with app data preserved.
- **date**: 2026-11-03
- **limitations**: This historical record does not by itself prove the live pairing issue is resolved. Windows OS build, adapter, exact GATT request timing, and host-side logs are unavailable. Pairing/bonding succeeded at the Android system level; the later user-confirmed Windows registration and assertion outcomes are recorded in Record 019. The Bluetooth advertising permission was already granted, so no Android permission dialog was expected. T020's evidence-recording task is complete with unavailable dimensions marked Unverified; T036 remains open.
- **status**: `Observed (Android bond completed, then app GATT authentication error) / Implemented and host-tested (transient bonding recovery) / Built and installed (updated shrunkDebug) / Unverified (live Windows retest and CTAP ceremonies)`.
- **artifact_link**: [focused GATT contract test XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.transport.FidoBleGattContractTest.xml), [GATT server authentication guard](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/FidoBleGattServer.kt), [GATT authentication tests](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/FidoBleGattContractTest.kt), [shrunkDebug APK](../../app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk), [T020](tasks.md)

### Record 018: Post-Update Ready State While Windows Shows Disconnected (T020 — Historical Observation)
- **requirement**: `FR-BLE-010`, `FR-BLE-030`, `TR-BLE-010`, T020, T036
- **scope**: Post-update ASUS Android 15 snapshot after the user started Authenticator; no Windows UI or host logs were accessed.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus current uncommitted worktree changes on `064-bluetooth-transport-selection`.
- **named_procedure**: Read the app UI hierarchy; inspect `dumpsys bluetooth_manager`, `dumpsys activity services com.chimali.shrunk`, and app-process `logcat` after the user reported that Windows showed a speaker icon and a nonworking “Conectar” action.
- **environment**: ASUS_AI2302 / ASUS Zenfone 10, Android 15/API 35; updated `shrunkDebug` APK; Windows build and Bluetooth adapter details unavailable.
- **expected_observed_result**: Phone UI showed `Ready for Authentication` and `Waiting for request from PC`. Android reported three ongoing advertising sets in total, including a Chimali set; the FIDO GATT server was registered and its GATT handle map showed zero requests at this snapshot. The foreground transport service remained active. The user reports that Windows lists the paired item with a speaker icon and its `Conectar` action does not connect.
- **date**: 2026-11-03
- **limitations**: The snapshot confirms local advertising and registration, not Windows connection or CTAP traffic. Windows was not controlled and its screen, device category, GATT client trace, and exact “Conectar” target were not captured. A Windows Settings status of disconnected while idle does not by itself prove FIDO GATT failure: Windows WebAuthn uses the platform API to negotiate FIDO transports, and the CTAP BLE flow scans/connects and discovers GATT services when a request is underway. The speaker icon alone does not prove that Chimali exposed an audio profile. A browser security-key ceremony remains necessary to establish host interoperability.
- **status**: `Passed (local Android advertising, GATT registration, and ready state) / Observed (Windows shows speaker icon and Connect does not establish a link, per user) / Unverified (Windows WebAuthn GATT connection and CTAP exchange)`.
- **artifact_link**: Live ADB snapshot was not retained; [Nordic advertiser configuration](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/NordicBleAdvertiserController.kt), [FIDO GATT service](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/FidoBleGattServer.kt), [T020](tasks.md)

### Record 019: User-Confirmed Windows WebAuthn Registration over BLE (T020 — Partial)
- **requirement**: `FR-BLE-010`, `FR-BLE-030`, `FR-BLE-050`, `TR-BLE-010`, `TR-BLE-020`, T020, T036
- **scope**: One user-initiated Windows browser registration attempt over the updated Android BLE authenticator. The user reported registration succeeded; Android app-process logs independently show the GATT connection and MakeCredential response path.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus current uncommitted worktree changes on `064-bluetooth-transport-selection`; installed APK `shrunkDebug` built from this worktree after the bonding-in-progress recovery change.
- **named_procedure**: User-triggered Windows WebAuthn registration with the phone showing `Ready for Authentication`; inspect Android app-process `logcat` and `dumpsys bluetooth_manager` after completion. No Windows UI was controlled by the implementation driver.
- **environment**: ASUS_AI2302 / ASUS Zenfone 10, Android 15/API 35, updated `shrunkDebug`; Windows build, browser build, Bluetooth adapter and relying-party identifier not captured.
- **expected_observed_result**: Android logged GATT connection `status=0 connected=true`, MTU 517, selected FIDO2 revision `0x20`, and notifications enabled. Logs show one GetAssertion request with no matching credential followed by a completed GetAssertion, then a MakeCredential request dispatched to the phone UI; the user completed registration, the deferred request resolved `success=true`, and a MakeCredential attestation response was encoded and delivered. The GATT connection disconnected with `status=0`, notifications were disabled, and advertising resumed. The phone UI returned to `Ready for Authentication / Waiting for request from PC`. Android GATT handle map showed the registered FIDO service and expected F1D0FFF1–FFF4 characteristics. The user confirms the browser registration worked.
- **required-check outcomes**: Clean-install discovery is Unverified because this installation was not demonstrated as clean; local Android advertiser/GATT registration was observed in Record 018. Numeric-comparison acceptance is Unverified because the matching number was not retained; rejection was not attempted and is Unverified. The real WebAuthn registration is user-confirmed with Android MakeCredential request/response evidence. A physical GetInfo-specific exchange was not retained and is Unverified; host GetInfo protocol coverage is covered by the host evidence matrix. Physical PROCESSING/UP_NEEDED keepalive cadence during UV was not measured and is Unverified; host timing/ordering tests are recorded in Records 003 and 025.
- **date**: 2026-11-03
- **limitations**: The user's browser success is reported but no Windows/browser screen, exact relying party, Windows build, or host-side WebAuthn trace was retained. The Android build revision cannot be bound to an immutable source commit; the installed APK came from the then-dirty worktree after the bonding recovery change. This is one registration path on Android 15, not GetInfo-specific behavior, numeric-comparison rejection, repeat registration, GetAssertion for a known credential, another Windows/browser combination, or parent transport selection/switch acceptance. T036 remains open for the full device-suite and composition checks.
- **status**: `Passed (one user-confirmed Windows WebAuthn registration with observed Android GATT/MakeCredential request-response and advertising recovery) / Partial (one GetAssertion without a credential followed by one completed assertion) / Unverified (clean-install discovery, numeric-comparison acceptance/rejection, physical GetInfo-specific response, physical keepalive cadence, immutable source revision and remaining FIDO BLE/parent acceptance)`.
- **artifact_link**: Android log buffer was inspected live but not retained; [GATT server](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/FidoBleGattServer.kt), [BLE transport](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleGattTransport.kt), [focused GATT contract tests](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/FidoBleGattContractTest.kt), [T020](tasks.md)

### Record 020: Full FIDO2 Android Host Suite after Bonding Recovery (T036 — Partial)
- **requirement**: T036; `FR-BLE-030`, `FR-BLE-050`, `FR-BLE-070`; `TR-BLE-020`, `TR-BLE-040`
- **scope**: Complete configured FIDO2 Android host-test task after the GATT bonding-in-progress recovery change. This supplements Record 017's focused contract test and is software evidence only.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus current worktree changes on `064-bluetooth-transport-selection`; source/build tree includes the GATT authentication recovery implementation.
- **named_procedure**: `:feature:fido2:testAndroidHostTest --max-workers=1 --no-configuration-cache --no-problems-report`.
- **environment**: Windows x64; Microsoft OpenJDK 25.0.5; Gradle 9.6.0; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; configured host tests ran without an attached device requirement.
- **expected_observed_result**: Gradle generated 153 JUnit XML suite reports containing 1,119 tests, 0 failures, 0 errors and 0 skipped. `FidoBleGattContractTest` is included in the suite. The Gradle HTML report shows the test task completed.
- **date**: 2026-11-03
- **limitations**: Host tests do not establish physical Android lifecycle or complete Windows interoperability. Android device suites, full registration/assertion matrix, interruption and repeated-cycle evidence, selection composition acceptance and trusted coverage assessment remain separate; T036/T038 remain open.
- **status**: `Passed (full configured FIDO2 Android host suite: 1,119/1,119) / Unverified (physical/device acceptance, complete composed parent check and trusted coverage)`.
- **artifact_link**: [FIDO2 Android host HTML report](../../feature/fido2/build/reports/tests/testAndroidHostTest/index.html), [GATT contract test XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.transport.FidoBleGattContractTest.xml), [T036 and T038](tasks.md)

### Record 021: Repository Local CI after BLE Advertiser Failure-Boundary Review (T036 — Partial)
- **requirement**: T036; `FR-BLE-010`, `FR-BLE-030`, `FR-BLE-070`; `TR-BLE-010`, `TR-BLE-040`; parent `FR-BT-SEL-040`, `TR-BT-SEL-020/030`
- **scope**: Repository static checks, release lint, configured compilation, repository unit tests and configured Android host suites after documenting the intentionally broad exception boundary around the Nordic advertiser Flow. Parent gate and selection ceremony host tests are included in the FIDO2 host suite. This run does not execute connected Android instrumentation.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus the current staged worktree and the focused `TooGenericExceptionCaught` suppression on`NordicBleAdvertiserController.startAdvertising`.
- **named_procedure**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache`, with an invocation-local wrapper appending `--no-problems-report` to Gradle calls because the existing report file was inaccessible to the current process. Worktree-local Gradle user home reused the existing read-only dependency cache. Final run completed successfully in 375.55 seconds.
- **environment**: Windows 11 x64; Microsoft OpenJDK 25.0.5; Gradle 9.6.0; Android SDK 37; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`. ASUS Android 15 and Mi Android 10 were connected, but this command did not run connected-device tests.
- **expected_observed_result**: KtLint, Detekt, release lint, configured compilation, repository unit tests, and the four configured Android host suites all passed. FIDO2 Android host tests: 1,119 tests, 0 failures, 0 errors, 0 skipped. Other host suites reported: core:common 13, core:security 71, core:domain 77, each with 0 failures/errors/skips. Device-test source compilation passed; instrumentation was not executed by local-ci.
- **date**: 2026-11-03
- **limitations**: The normal wrapper invocation could not write Gradle's existing `build/reports/problems/problems-report.html`; the successful run suppressed only that generated problems report. Connected Android tests, physical BLE/Classic ceremonies, settings-switch acceptance and trusted feature-058 coverage/nonregression are not established by this local CI result. T036/T039 remain open.
- **status**: `Passed (local static analysis, release lint, configured compilation, repository unit tests and configured Android host suites) / Compiled Only (connected Android test sources) / Unverified (connected-device execution, physical acceptance and trusted coverage)`.
- **artifact_link**: [FIDO2 Android host report](../../feature/fido2/build/reports/tests/testAndroidHostTest/index.html), [local CI script](../../tools/local-ci.ps1), [transport selection host composition test](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/TransportSelectionCeremonyTest.kt), [T036 and T038](tasks.md)

### Record 022: Current Synthetic Latency Rerun and ASUS Memory Snapshot (T037 — Partial)
- **requirement**: T037; `FR-BLE-030`, `FR-BLE-050`; `TR-BLE-030`
- **scope**: Re-run of the declared host-simulated operation assessment plus a read-only snapshot of the currently installed ASUS app process memory. The pre-existing release AAB size comparison is retained in Record 002.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus current worktree changes; the only source change since the preceding runtime build is a Detekt suppression annotation and rationale on the advertiser startup boundary.
- **named_procedure**: `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.performance.SimulatedLatencyAssessmentTest --no-configuration-cache --no-problems-report --max-workers=1`; `adb -s R7AIB7002901AZ3 shell dumpsys meminfo com.chimali.shrunk`.
- **environment**: Windows 11 amd64; OpenJDK 25.0.5 host assessment; ASUS_AI2302 / Android 15/API 35, installed `shrunkDebug`; Android app process `com.chimali.shrunk`.
- **expected_observed_result**: The synthetic assessment passed with ten measured occurrences each for GetAssertion, GetInfo, MakeCredential and ResetAuthenticator. Maximum assessed operation times were 2.2209ms, 2.42995ms, 3.54145ms and 0.32575ms respectively; all were below 200ms. The ASUS memory snapshot reported TOTAL PSS 145,069 KB, TOTAL RSS 251,924 KB and TOTAL SWAP PSS 4,330 KB. Record 002's release AAB comparison reports a +103,645-byte AAB delta and +43,553-byte base compressed delta against its clean baseline.
- **date**: 2026-11-03
- **limitations**: Latency uses synthetic storage, credential derivation, relying party and user-verification adapters; it does not traverse BLE, HID or production persistence. The memory measurement is a single current-process snapshot without a matched quiescent baseline or post-cycle comparison; it cannot establish no leak. AAB sizing in Record 002 was measured on the adjacent source tree before the suppression annotation. Full 50-cycle memory stress and current build-size remeasurement remain incomplete; T037 remains open.
- **status**: `Passed (host-simulated internal-operation latency) / Observed (single ASUS memory snapshot) / Partial (prior release AAB size comparison) / Unverified (physical latency, memory delta/leak assessment and 50-cycle stress)`.
- **artifact_link**: [current simulated latency JSON](../../feature/fido2/build/reports/fido2/060/simulated-latency-assessment.json), [current test XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.performance.SimulatedLatencyAssessmentTest.xml), [release AAB size assessment](../../feature/fido2/build/reports/fido2/064/release-bundle-size-assessment.json), [T037](tasks.md)

### Record 023: ASUS-Only BLE Service Lifecycle Instrumentation (T029/T036 — Partial)
- **requirement**: T029, T036; `FR-BLE-060`, `FR-BLE-080`; `TR-BLE-030`; parent `FR-BT-SEL-040`
- **scope**: Filtered Android service lifecycle instrumentation on the ASUS only. It validates connected-device foreground service declaration, repeated starts, explicit service Stop cleanup, foreground notification lifecycle and retry after startup failure using an injected fake `Fido2Transport`. The Mi was excluded.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus current staged worktree and focused Detekt suppression on the Nordic advertiser startup boundary.
- **named_procedure**: Set `ANDROID_SERIAL=R7AIB7002901AZ3`; run `:feature:fido2:connectedAndroidDeviceTest -Pandroid.injected.device.serial=R7AIB7002901AZ3 -Pandroid.testInstrumentationRunnerArguments.class=com.chimali.fido2.service.FidoBleLifecycleDeviceTest --no-configuration-cache --no-problems-report --max-workers=1`; afterward inspect ASUS `dumpsys activity services` and `dumpsys bluetooth_manager` read-only.
- **environment**: ASUS_AI2302 / ASUS Zenfone 10, Android 15/API 35, serial `R7AIB7002901AZ3`; Windows 11 host interaction was not involved; Mi A2 was not selected.
- **expected_observed_result**: Gradle reported `Running tests on devices: ASUS_AI2302 - 15`. The device XML names serial `R7AIB7002901AZ3` and reports 3 tests, 0 failures, 0 errors, 0 skipped: `serviceIsPrivateAndDeclaresConnectedDeviceForegroundType`, `repeatedStartKeepsForegroundNotificationAndStopCleansTransport`, and `retryAfterFailedStartupConnectsAgainAndRestoresForegroundNotification`. The post-test read-only snapshot showed the user's Chimali foreground service (`com.chimali.shrunk`, notification 1001) and an advertising set registered under `com.chimali.shrunk` still active.
- **date**: 2026-11-03
- **limitations**: The lifecycle tests use a fake transport, so they do not measure real BLE advertiser start/Stop duration, prove background advertising persistence, exercise GATT interruption/reconnection, or run TalkBack. Keepalive, 50-cycle stress, parent transport switching and complete Android device-suite acceptance remain open. This is partial evidence for T029/T036, not their full acceptance.
- **status**: `Passed (3 ASUS Android 15 foreground-service lifecycle cases, ASUS serial independently confirmed, post-test app advertising still active) / Unverified (real advertiser timing/background behavior, interruptions, TalkBack, full device-suite and acceptance matrix)`.
- **artifact_link**: [ASUS instrumentation XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [device lifecycle test source](../../feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/service/FidoBleLifecycleDeviceTest.kt), [T029/T036](tasks.md)

### Record 024: Accepted-Target Coverage Prerequisite Audit (T038 — Unverified)
- **requirement**: T038; feature-058 coverage floors, trusted target comparison and accepted-index identity
- **scope**: Read-only audit of trusted accepted-target index availability and the candidate/target identities required for BLE coverage nonregression. No candidate or target coverage capture was started.
- **revision**: Candidate worktree HEAD `c9195ea608ff3394e32aa217e7e640e5579d6f4d` plus 82 staged/unstaged changed paths; current implementation remains uncommitted. Local `refs/remotes/origin/main` resolves to `f1fd56baf8641cea99edc7fafa8725fdda21044c`.
- **named_procedure**: Read `D:\octav\coverage-local-validation\baselines\current.json`, enumerate current.json files under `D:\octav\coverage-local-validation`, inspect local origin refs and candidate ancestry, attempt read-only `git ls-remote origin refs/heads/main`, and compare the local main SHA with GitHub's live `main` ref through the read-only commit comparison connector.
- **environment**: Windows 11 worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; configured remote `origin` points to the Chimali GitHub repository.
- **expected_observed_result**: The newest locally available accepted-target index identifies target `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2` and run `0251dac6-c603-4de9-ad19-e0784460e8ec`; the indexed bundle is retained. No `current.json` for `main` was found under the inspected validation root. A read-only GitHub commit comparison (`base=f1fd56baf8641cea99edc7fafa8725fdda21044c`, `head=main`) returned `identical`, confirming current remote `main` is `f1fd56baf8641cea99edc7fafa8725fdda21044c`. Candidate HEAD `c9195ea608ff3394e32aa217e7e640e5579d6f4d` descends from the older indexed target but not from current `main`.
- **date**: 2026-11-03
- **limitations**: The live remote target is now known, but the only located index is bound to older target `fa93a40…`, not current `main` `f1fd56b…`. The integration script also isolates the exact candidate commit, while the current implementation and evidence remain uncommitted, so it would omit the present working-tree changes. No coverage floors, completeness, or nonregression result is claimed. T038 remains open.
- **status**: `Unverified (exact current remote target cannot be established; available trusted index is bound to an older target and current implementation is uncommitted)`.
- **artifact_link**: `D:\octav\coverage-local-validation\baselines\current.json`; `D:\octav\coverage-local-validation\t146-commit-86af321\evidence\baselines\current.json`; [current remote main commit](https://github.com/octaviordz/Chimali/commit/f1fd56baf8641cea99edc7fafa8725fdda21044c); [coverage acceptance procedure](../058-enforce-coverage-targets/plan.md); [T038](tasks.md)

### Record 025: ASUS-Only GATT Security Device Tests (T015/T036 — Partial)
- **requirement**: T015, T036; `FR-BLE-020`, `FR-BLE-030`; `TR-BLE-010`, `TR-BLE-020`
- **scope**: Android API 35 device execution of GATT service security-attribute and unauthenticated-request rejection tests. The test fixture uses a mocked `BluetoothDevice`; this validates Android-side construction/callback behavior, not real Bluetooth numeric comparison.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus the current staged implementation and updated `FidoBleSecurityDeviceTest` fixture explicitly setting `bondState=BOND_NONE`.
- **named_procedure**: Set `ANDROID_SERIAL=R7AIB7002901AZ3`; run `:feature:fido2:connectedAndroidDeviceTest -Pandroid.injected.device.serial=R7AIB7002901AZ3 -Pandroid.testInstrumentationRunnerArguments.class=com.chimali.fido2.data.transport.FidoBleSecurityDeviceTest --no-configuration-cache --no-problems-report --max-workers=1`; run `:feature:fido2:ktlintAndroidDeviceTestSourceSetCheck --no-configuration-cache --no-problems-report`.
- **environment**: ASUS_AI2302 / ASUS Zenfone 10, Android 15/API 35, serial `R7AIB7002901AZ3`; Gradle 9.6.0 and Microsoft OpenJDK 25.0.5. The Mi was excluded from device selection.
- **expected_observed_result**: First ASUS run exposed an incomplete MockK fixture because `FidoBleGattServer` now inspects `bondState`; the test was updated to represent an unbonded device explicitly. The rerun selected only `ASUS_AI2302 - 15`; its XML confirms device serial `R7AIB7002901AZ3`, 2 tests, 0 failures, 0 errors and 0 skipped. Both MITM characteristic/CCCD permissions and unauthenticated control-point rejection passed. Targeted Android device-test KtLint passed. A post-test read-only snapshot still showed the app's foreground FIDO service and advertiser active.
- **date**: 2026-11-03
- **limitations**: The pairing-failure test mocks the Bluetooth device and authenticated-state provider; it does not cause an actual system pairing dialog or validate numeric-comparison acceptance/rejection. Keepalive, real Windows request, full Android device suite, physical interruption and switch acceptance remain open. T015/T036 remain partial at the feature acceptance boundary.
- **status**: `Passed (2 ASUS Android 15 GATT security device tests and targeted KtLint after explicit bond-state fixture fix; app advertising remained active) / Unverified (actual pairing, numeric comparison, complete device suite and physical acceptance matrix)`.
- **artifact_link**: [ASUS security instrumentation XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [security test source](../../feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/data/transport/FidoBleSecurityDeviceTest.kt), [GATT server](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/FidoBleGattServer.kt), [T015/T036](tasks.md)

### Record 026: ASUS Production BLE Start/Stop, Background and Accessibility Evidence (T029 — Evidence Recorded)
- **requirement**: T029; `FR-BLE-010`, `FR-BLE-060`, `FR-BLE-080`; `TR-BLE-010`, `TR-BLE-030`
- **scope**: Real installed production BLE service on ASUS Android 15. Measured explicit `ACTION_START` until Android reported Chimali's ongoing advertising set and explicit `ACTION_STOP` until that set disappeared; then verified service/advertising persistence while the activity was backgrounded.
- **revision**: `27e75da807d9836b01b6f5b3b838403554f49288` plus the current worktree; installed `shrunkDebug` BLE implementation. No source change was made for this timing procedure.
- **named_procedure**: On serial `R7AIB7002901AZ3`, use `run-as com.chimali.shrunk am startservice --user 0 ... -a com.chimali.fido2.STOP_TRANSPORT` and `am start-foreground-service --user 0 ... -a com.chimali.fido2.START_TRANSPORT`; time from before each intent through host observation of Chimali's advertising-set presence/absence in `dumpsys bluetooth_manager`. Send `KEYCODE_HOME`, wait 10 seconds, verify the ongoing advertising set and `Fido2TransportService` foreground notification with `dumpsys`, then return `MainActivity` to the foreground.
- **environment**: ASUS_AI2302 / ASUS Zenfone 10, Android 15/API 35, package `com.chimali.shrunk`, installed `shrunkDebug`, Windows 11 host. Mi was not targeted.
- **expected_observed_result**: Focused end-to-end measurement: Start to first OS report of Chimali advertising = 419ms (<500ms); Stop intent to advertising-set disappearance = 476ms (<1s). Another interval measured Stop 371ms from service intent acceptance and Start 225ms from acceptance. Advertising and foreground service remained active during a 10-second background interval. MainActivity was restored, and the app returned to advertising/foreground state afterward.
- **date**: 2026-11-03
- **accessibility evidence**: The parent device review in [parent Record 010](../064-bluetooth-transport-selection/implementation-evidence.md) inspected selected BLE transport status at the largest available native text size and with high-contrast text; the settings UI instrumentation in [parent Record 014](../064-bluetooth-transport-selection/implementation-evidence.md) verified the selected-not-started state and scroll access at 200% Compose text scale. TalkBack focus and announcements remain Unverified because its first-run tutorial prevented completion of the review.
- **limitations**: OS-state observation uses ADB/dumpsys polling, so the measured intervals include command/observation overhead and are conservative. An earlier overinclusive timing attempt reported 534ms because it stopped the timer only after both advertising and a separate foreground-service inspection; it is not used as the Start acceptance measurement. This does not test process death, reboot, Bluetooth interruption, active-client connection, repeated-cycle memory, TalkBack, or BLE-to-Classic selection. T029's evidence-recording action is complete; the unverified accessibility/lifecycle dimensions remain clearly open for feature acceptance.
- **status**: `Passed (ASUS real-service advertising Start <500ms, explicit Stop <1s, 10-second background persistence, selected BLE state high-contrast/native-scale review, and 200% Compose text-scale state/scroll check) / Unverified (TalkBack, interruption, process death/reboot, repeated-cycle memory and parent transport lifecycle)`.
- **artifact_link**: [ASUS lifecycle timing artifact](../../feature/fido2/build/reports/fido2/064/asus-lifecycle-timing-20261103.json), [parent accessibility review Record 010](../064-bluetooth-transport-selection/implementation-evidence.md), [parent settings instrumentation Record 014](../064-bluetooth-transport-selection/implementation-evidence.md), [Nordic advertiser controller](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/NordicBleAdvertiserController.kt), [BLE lifecycle task](tasks.md)

### Record 028: ASUS Suite Follow-up and Performance-Test Precondition Audit (T036 — Partial)
- **requirement**: T036; feature-058 requires complete device outcomes without counting skipped/failed work as a pass
- **scope**: Re-run the full FIDO2 Android device suite after a temporary diagnostic moved tracing assumptions outside coroutine scopes; inspect the fresh XML and compare it with the original ASUS run. The temporary source edit was reverted because it did not change the outcome.
- **revision**: Candidate worktree HEAD `c9195ea608ff3394e32aa217e7e640e5579d6f4d` plus existing 82 staged/unstaged paths; the five test files are back to their prior implementation.
- **named_procedure**: `:feature:fido2:ktlintAndroidDeviceTestSourceSetCheck --no-configuration-cache --no-problems-report --max-workers=1`; then `:feature:fido2:connectedAndroidDeviceTest -Pandroid.injected.device.serial=R7AIB7002901AZ3 --no-configuration-cache --no-problems-report --max-workers=1` while temporarily testing the assumption placement.
- **environment**: ASUS_AI2302 / ASUS Zenfone 10, Android 15/API 35, serial `R7AIB7002901AZ3`; Windows 11 x64 host; Gradle 9.6.0; Microsoft OpenJDK 25.0.5. The Mi A2 was not selected.
- **expected_observed_result**: Targeted Android-device-test KtLint passed. The connected task exited successfully, but the fresh XML still reports 51 tests, 5 failures, 0 errors and 0 skips; the same five performance/research cases fail with `AssumptionViolatedException` stating Perfetto/tracing is not enabled. This shows the temporary assumption-placement change did not make the generic suite record these as skips. It was reverted, and T036 remains open; the standalone research procedures were not run.
- **date**: 2026-11-03
- **limitations**: The generated XML corresponds to the temporary diagnostic test sources, not a source revision retained in the worktree. It is diagnostic evidence only and does not validate the current tree. No experiment run IDs or synthetic replacements were supplied. Do not treat the Gradle exit code alone as a passing test report when the XML records failures.
- **status**: `Passed (targeted KtLint) / Failed (fresh device XML still records five tracing-precondition failures despite a successful Gradle exit) / Unverified (standalone experiments and full device-suite acceptance)`.
- **artifact_link**: [fresh ASUS connected-device XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [experiment procedures](../062-fido2-performance-investigation/artifacts), [T036](tasks.md)

### Record 027: Full FIDO2 Android Device Suite on ASUS (T036 — Partial)
- **requirement**: T036; `FR-BLE-020`, `FR-BLE-030`, `FR-BLE-060`, `TR-BLE-010`, `TR-BLE-020`; parent selected-handle integration boundary
- **scope**: Run the configured FIDO2 Android device-test suite on the ASUS API 35 phone with its serial explicitly selected. The Mi A2 was attached to ADB but was not selected or used.
- **revision**: Candidate worktree HEAD `c9195ea608ff3394e32aa217e7e640e5579d6f4d` plus 82 staged/unstaged paths on branch `064-bluetooth-transport-selection`.
- **named_procedure**: Set `ANDROID_SERIAL=R7AIB7002901AZ3`; run `:feature:fido2:connectedAndroidDeviceTest -Pandroid.injected.device.serial=R7AIB7002901AZ3 --no-configuration-cache --no-problems-report --max-workers=1`.
- **environment**: ASUS_AI2302 / ASUS Zenfone 10, Android 15/API 35, serial `R7AIB7002901AZ3`; Windows 11 x64 host; Gradle 9.6.0; Microsoft OpenJDK 25.0.5. ADB also listed Mi A2 serial `2ac6876`, which the Gradle task did not run on.
- **expected_observed_result**: Gradle reported `Running tests on devices: ASUS_AI2302 - 15`. XML reports 51 tests, 46 passed and 5 failed, with no errors or skips. BLE-focused `FidoBleSecurityDeviceTest` (2/2) and `FidoBleLifecycleDeviceTest` (3/3) passed, as did the app's other non-experimental device tests. Failures were isolated to `EmpiricalCalibrationDeviceTest`, `FilteredMeasurementPilotDeviceTest`, `LowerOverheadControlExplorationDeviceTest`, `PerformanceTraceDeviceTest` and `TraceOverheadExplorationDeviceTest`, which did not meet the tests' tracing precondition in this general suite invocation. The test output records JUnit assumption violations and a tracing-precondition exception; their standalone frozen run procedures were not started.
- **date**: 2026-11-03
- **limitations**: This is not a passing full device suite. The five research/performance experiment cases need their declared standalone procedures and exact arguments; no substitute values were supplied. Parent integrated composition is covered by the previously recorded host suite but this run did not execute external Windows pairing or a real credential ceremony. Keep T036 open until the configured suite and required parent-boundary checks have acceptable, separately documented outcomes.
- **status**: `Partial (46/51 ASUS API 35 device tests passed, including all five BLE lifecycle/security cases) / Failed (five unconfigured research/performance experiment cases) / Unverified (physical pairing, GetAssertion, interruption/reconnect and full acceptance)`.
- **artifact_link**: [ASUS connected-device XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [Android test report](../../feature/fido2/build/reports/androidTests/connected/androidMain/index.html), [T036](tasks.md)

### Record 029: ASUS Permission Revocation Recovery Through Start Control (T035 — Partial)
- **requirement**: T035; `FR-BLE-070`, `TR-BLE-040`; parent `FR-BT-SEL-050`
- **scope**: Exercise the normal Chimali Start control after Android revokes `BLUETOOTH_ADVERTISE`, then confirm the permission is restored and the selected BLE service resumes advertising. This verifies the user-facing retry path for a stale process availability snapshot.
- **revision**: Candidate `HEAD` `c9195ea608ff3394e32aa217e7e640e5579d6f4d` on branch `064-bluetooth-transport-selection`, plus the dirty worktree containing `Fido2HomeViewModel.toggleTransport()` recovery routing and `Fido2HomeViewModelTest`; this exact worktree was built and installed as `shrunkDebug` on the ASUS.
- **named_procedure**: On ASUS serial `R7AIB7002901AZ3`, stop Chimali advertising in the app, run `adb shell pm revoke com.chimali.shrunk android.permission.BLUETOOTH_ADVERTISE`, launch `com.chimali.shrunk/com.chimali.MainActivity`, tap the visible `Start Authenticator` control, then inspect `dumpsys package`, `dumpsys activity services com.chimali.shrunk`, and `dumpsys bluetooth_manager`. An initial UI dump landed on LeakCanary after revocation; no tap was issued there. Relaunching `MainActivity` explicitly returned to the authenticator and the Start tap exercised the app recovery path. Permission was restored before ending device work.
- **environment**: ASUS Zenfone 10 (`ASUS_AI2302`), Android 15/API 35, serial `R7AIB7002901AZ3`; Chimali `shrunkDebug`; Windows 11 x64 host; Gradle 9.6.0; Microsoft OpenJDK 25.0.5. The Xiaomi/Mi device was not selected or used.
- **expected_observed_result**: Before Start, Android reported `BLUETOOTH_ADVERTISE: granted=false`. Tapping Start moved the UI to `Ready for Authentication / Waiting for request from PC`; Android then reported `BLUETOOTH_ADVERTISE: granted=true`. `Fido2TransportService` was foreground with notification 1001 and `dumpsys bluetooth_manager` listed `com.chimali.shrunk` among ongoing advertising sets. Focused host regression tests cover mapping unavailable Idle/Error states to Retry while preserving normal Start and Stop actions; both passed with KtLint.
- **date**: 2026-11-03
- **limitations**: The Android permission had been granted before task execution, so this test used ADB revocation rather than an interactive system permission prompt; it verifies recovery from the revoked-permission state but not prompt presentation/consent behavior. It does not test stale bonds, an active peer interruption, cancellation, 50 reconnect cycles, TalkBack, Windows assertion, or Mi behavior. T035 remains open.
- **status**: `Passed (focused retry routing host tests and ASUS API 35 revoked-permission recovery; permission restored and BLE advertising active) / Unverified (system permission prompt UX and remaining T035 recovery/lifecycle matrix)`.
- **artifact_link**: [focused host test source](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/presentation/viewmodel/Fido2HomeViewModelTest.kt), [focused host test XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.presentation.viewmodel.Fido2HomeViewModelTest.xml), [home view model](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/Fido2HomeViewModel.kt), [T035](tasks.md)

### Record 030: ASUS Idle-versus-Advertising Memory Snapshot (T037 — Partial)
- **requirement**: T037; `SC-006`,`NFR-PERF-060`, `TR-PERF-010`
- **scope**: Compare process memory with the Chimali app idle and with the production BLE advertiser active, as a short runtime-memory check supplementing the release bundle-size and host latency records.
- **revision**: Candidate `HEAD` `c9195ea608ff3394e32aa217e7e640e5579d6f4d` plus the dirty Feature 064 worktree; installed `shrunkDebug` package was built from this worktree.
- **named_procedure**: On ASUS serial `R7AIB7002901AZ3`, use the app's Stop control, collect six `dumpsys meminfo com.chimali.shrunk` readings at approximately one-second intervals, use the app's Start control, confirm `Fido2TransportService` is foreground and Chimali advertising is listed by `dumpsys bluetooth_manager`, then collect six more readings.
- **environment**: ASUS Zenfone 10 / ASUS_AI2302, Android 15/API 35, package `com.chimali.shrunk`, `shrunkDebug`; Windows 11 x64, Gradle 9.6.0, Microsoft OpenJDK 25.0.5. Xiaomi/Mi was not selected or used.
- **expected_observed_result**: Six idle readings had median PSS 174,826 KB (range 174,698–174,826 KB), median RSS 281,864 KB (range 281,736–281,864 KB), and median swap PSS 105 KB. Six active-advertising readings had median PSS 175,159 KB (range 175,143–176,076 KB), median RSS 282,224 KB (range 282,208–283,152 KB), and median swap PSS 105 KB. Active-minus-idle median deltas were +333 KB PSS, +360 KB RSS and 0 KB swap PSS. The advertising service remained foreground during active samples.
- **date**: 2026-11-03
- **limitations**: Short single-session comparison only. It estimates the immediate process-memory difference between app-idle and BLE-advertising states; it does not establish long-term leak behavior, connected-session memory, memory-trim behavior or the 50-cycle stress requirement. This completes the requested T037 memory check; T035's reconnect-cycle memory baseline remains unverified.
- **status**: `Observed (six ASUS idle and six active advertising memory samples with raw report retained; included in completed T037 evidence) / Unverified (leak, memory-trim and 50-cycle behavior)`.
- **artifact_link**: [raw memory samples and state comparison](../../feature/fido2/build/reports/fido2/064/asus-memory-state-comparison-20261103.json), [release bundle comparison](../../feature/fido2/build/reports/fido2/064/release-bundle-size-assessment.json), [simulated latency assessment](../../feature/fido2/build/reports/fido2/060/simulated-latency-assessment.json), [T035](tasks.md)

### Record 031: Current Local CI and Coverage-Gate Outcome (T036/T038 — Partial)
- **requirement**: T036, T038; `FR-BLE-020`, `FR-BLE-030`, `FR-BLE-060`, `TR-BLE-010`, `TR-BLE-020`; parent selected-handle composition and coverage T039
- **scope**: Current-source repository static analysis, release lint, compilation, repository unit tests, Android host tests, and configured connected-device coverage workload pinned to the ASUS. This run does not perform Windows ceremonies or accepted-target comparison.
- **revision**: Candidate `HEAD` `c9195ea608ff3394e32aa217e7e640e5579d6f4d` plus the current dirty worktree; latency artifact source fingerprint `48383f3e02919a3f52ef820fb44c9fb4928bdfb86ece3a74effd2c0d7909c0f3`.
- **named_procedure**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage`, using a temporary copy of the script with `--no-problems-report` appended to Gradle invocations after the ordinary wrapper could not write the existing problems report; `GRADLE_USER_HOME` pointed to `.gradle/codex-user-home` and `ANDROID_SERIAL=R7AIB7002901AZ3`.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5 for the local run (Gradle selected cached JetBrains JDK 21 for its daemon); ASUS Zenfone 10 / ASUS_AI2302, Android 15/API 35, serial `R7AIB7002901AZ3`. Mi A2 serial `2ac6876` was attached but not selected.
- **expected_observed_result**: KtLint, Detekt, release lint, compilation, repository unit tests and Android host suites passed; FIDO2 Android host XML totals 1,121 tests, 0 failures, 0 errors, 0 skipped. The connected FIDO2 suite ran on ASUS and reported 51 tests, 46 passed and 5 failed, with 0 errors/skips. The failing cases were `EmpiricalCalibrationDeviceTest`, `FilteredMeasurementPilotDeviceTest`, `LowerOverheadControlExplorationDeviceTest`, `PerformanceTraceDeviceTest` and `TraceOverheadExplorationDeviceTest`; each failed at its declared Perfetto/tracing precondition. Consequently the coverage-gate Gradle task failed at `:feature:fido2:connectedAndroidDeviceTest` and did not reach `hdkCoverageVerify`; no coverage-floor/completeness result was produced.
- **date**: 2026-11-03
- **limitations**: The five research tests require their separately controlled Perfetto procedures; the general run does not satisfy those preconditions and their XML failures are not passes. No raw-count candidate-versus-accepted-target comparison was run; the accepted index remains bound to an older target and current candidate changes are uncommitted. After the connected coverage run, `com.chimali.shrunk` was no longer installed. Reinstalling the built APK resulted in `AEADBadTagException` while `WalletMasterSeedProvider` decrypted the stored mnemonic during encrypted database setup; the data was not cleared, and further device testing is suspended pending safe recovery. The separate `com.chimali` package remains installed. T036/T038 remain open.
- **status**: `Passed (local static, compile, repository unit and Android host checks) / Failed (five of 51 ASUS FIDO2 device tests at tracing preconditions; subsequent shrunkDebug launch fails on Keystore authentication of stored encrypted data) / Unverified (coverage floors, completeness, accepted-target nonregression and physical acceptance)`.
- **artifact_link**: [current ASUS FIDO2 XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [FIDO2 host XML directory](../../feature/fido2/build/test-results/testAndroidHostTest), [current simulated-latency report](../../feature/fido2/build/reports/fido2/060/simulated-latency-assessment.json), [local CI script](../../tools/local-ci.ps1), [T036/T038](tasks.md)

### Record 032: BLE Disconnect, Stale-Callback and Session-Generation Fencing (T035/T036 — Software Partial)
- **requirement**: T035, T036; `FR-BLE-060`, `FR-BLE-070`, `TR-BLE-040`
- **scope**: Source review found that an unexpected active-peer disconnect cleared the session generation but did not cancel the in-flight CTAP job or queued notifications. The transport cancels the active job and clears pending notifications; job cleanup checks its own `Job` identity so a late canceled coroutine cannot clear a successor job reference. A stale-disconnect guard ignores a prior peer's delayed disconnection while a successor is active. GATT notification-completion callbacks retain peer identity, and notifications from inactive peers are ignored. A further race review found that delayed sends and notification timeout callbacks could affect a successor session. Completed BLE messages and queued notifications now carry the session generation; stale sends, failures and timeout callbacks are fenced, and address resolution/notification initiation plus stall recovery run only while that generation still owns the session. Host regressions cover active-disconnect cancellation/no late response/advertising recovery, successor-session preservation against an old disconnect or notification callback, rejection of stale generation work and delayed-send completion.
- **revision**: Candidate `HEAD` `c9195ea608ff3394e32aa217e7e640e5579d6f4d` plus current uncommitted Feature 064 changes, including BLE disconnect cancellation, stale-disconnect/notification callback filtering and focused host regressions.
- **named_procedure**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache` using a temporary copy that appended `--no-problems-report` to Gradle calls because the checked-in script's default problems report was not writable; this ran KtLint, Detekt, release lint, all-source compilation, repository unit tests and configured Android host suites. Also ran `:feature:fido2:compileAndroidDeviceTest :feature:fido2:ktlintAndroidDeviceTestSourceSetCheck`; the connected suite was not invoked. Rebuild with `:app:bundleRelease --no-configuration-cache --no-problems-report --max-workers=1`.
- **environment**: Windows 11 x64; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; Gradle 9.6.0; Microsoft OpenJDK 25.0.5. Host-only; no Android device or Windows authentication UI was used.
- **expected_observed_result**: The full ordinary local CI completed successfully: KtLint, Detekt, release lint, all-source compilation, repository unit tests, core Android host suites and FIDO2 Android host suite passed. Current FIDO2 host XML totals 1,126 tests, 0 failures, 0 errors and 0 skips; this includes 8 notification-writer, 14 lifecycle and 5 cancellation tests. Android device-test source compilation and device-test KtLint passed. The connected device suite and coverage gate were not invoked in this ordinary run. The final release bundle build passed and current byte-size delta remains within limits (Record 011).
- **date**: 2026-11-03
- **limitations**: These tests prove host-mode cancellation, stale-generation fencing and inactive-peer callback boundaries only; they do not establish real Android callback ordering, same-address delayed notification callbacks across reconnect, Bluetooth interruption/reconnect behavior, mid-ceremony persistent-state commit semantics, or the required 50-cycle physical memory matrix. This ordinary local-CI invocation does not include connected-device suites or coverage capture; previous connected results include five Perfetto-precondition failures. The runtime-memory comparison predates the generation-fencing changes and does not establish reconnect-cycle behavior. T035/T036 remain open.
- **status**: `Passed (ordinary repository local CI, 1,126 FIDO2 host tests, device source compilation, 27-case focused lifecycle/writer/cancellation suites, KtLint and current release bundle) / Unverified or incomplete (connected suite, real peer interruption, same-address delayed callbacks, physical lifecycle/recovery matrix, reconnect-cycle memory and coverage/full acceptance)`.
- **artifact_link**: [FIDO BLE lifecycle test](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/FidoBleLifecycleTest.kt), [notification writer tests](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/BleNotificationWriterTest.kt), [BLE transport](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleGattTransport.kt), [writer XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.transport.BleNotificationWriterTest.xml), [lifecycle XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.transport.FidoBleLifecycleTest.xml), [cancellation XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.transport.FidoBleCancellationTest.xml), [T035/T036](tasks.md)

### Record 033: Full FIDO2 Android Device Suite on Mi A2 (T036 — Partial)
- **requirement**: T036; `FR-BLE-020`, `FR-BLE-030`, `FR-BLE-060`, `TR-BLE-010`, `TR-BLE-020`; parent selected-handle integration boundary
- **scope**: Run the configured FIDO2 Android device-test suite against the only connected Android device, Mi A2/API 29, without clearing or replacing the installed `com.chimali.shrunk` app data.
- **revision**: Candidate `HEAD` `c9195ea608ff3394e32aa217e7e640e5579d6f4d` plus current uncommitted Feature 064 changes, including the device-test-only Ed25519 verifier fallback, on branch `064-bluetooth-transport-selection`.
- **named_procedure**: Set `GRADLE_USER_HOME` to `.gradle/codex-user-home`; run `:feature:fido2:connectedAndroidDeviceTest --no-configuration-cache --no-problems-report --max-workers=1` with only ADB device `2ac6876` attached. After the initial suite exposed Android 10's missing non-BC Ed25519 verifier, rerun `:feature:fido2:connectedAndroidDeviceTest` with `--project-prop android.testInstrumentationRunnerArguments.class=com.chimali.fido2.data.crypto.CredentialPersistenceCeremonyTest` to verify the fallback in isolation, then rerun the full suite against the updated test source. The task installed and ran the isolated `com.chimali.fido2.test` instrumentation target; no `pm clear`, uninstall, production-package install, or permission change was issued.
- **environment**: Mi A2 / product `jasmine`, Android 10/API 29, serial `2ac6876`; `com.chimali.shrunk` was already installed and its process remained present after the suite. Windows 11 x64; Gradle 9.6.0; Microsoft OpenJDK 25.0.5; Android SDK 37.
- **expected_observed_result**: The latest full-suite XML reports 48 tests, 43 passed, 5 failed, 0 errors and 0 skipped. Both `FidoBleSecurityDeviceTest` cases passed, including MITM characteristic/CCCD permissions and unauthenticated control-point rejection. The two `CredentialPersistenceCeremonyTest` cases passed after the test-only JCA verifier fell back to Bouncy Castle on Android 10; its isolated retained XML also reports 2/2 passed. Three credential process-restart cases passed. Five research/performance cases failed their declared Perfetto/tracing preconditions. API-31-gated `FidoBleLifecycleDeviceTest` cases did not run on API 29.
- **date**: 2026-11-03
- **limitations**: This remains a failed, partial device-suite result, not a passing suite or BLE interoperability test. The Ed25519 test fallback uses Bouncy Castle JCA because Android 10 has no non-BC platform verifier; it verifies the product's lightweight-Bouncy-Castle signature through a separate JCA API path, while Android releases with a platform verifier continue to use that independent provider. The five research cases require separately configured tracing procedures. The API 29 device cannot execute API-31 service lifecycle tests. The Feature 058 coverage policy requires API 35 or newer, so this Mi run is ineligible for T038 coverage-floor, completeness, or nonregression evidence. No Windows host ceremony, pairing comparison, GetAssertion-over-BLE, interruption/reconnect, parent selector composition, or coverage gate was run. The installed production app remained installed; T036 remains open.
- **status**: `Passed (43/48 Mi A2 Android 10 device tests, including both BLE GATT security and both persisted ceremony cases) / Failed (five Perfetto/tracing preconditions) / Not run (API-31 lifecycle tests on this API 29 device) / Unverified (Windows interoperability, parent composition, physical lifecycle acceptance and trusted coverage)`.
- **artifact_link**: [retained full Mi A2 suite XML](../../feature/fido2/build/reports/fido2/064/mi-full-suite-20261103.xml), [retained focused persistence XML](../../feature/fido2/build/reports/fido2/064/mi-ed25519-persistence-focused-20261103.xml), [Mi A2 Android test report](../../feature/fido2/build/reports/androidTests/connected/androidMain/index.html), [BLE security tests](../../feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/data/transport/FidoBleSecurityDeviceTest.kt), [T036](tasks.md)

### Record 034: Mi A2 BLE Advertising Startup Failure (T020/T035 — Failed)
- **requirement**: T020, T035; `FR-BLE-010`, `FR-BLE-060`, `TR-BLE-010`
- **scope**: Reinstalled the current `shrunkDebug` APK over the existing app without clearing data, opened Chimali Authenticator, and tapped **Start Authenticator** on Mi A2. Checked the foreground service, Bluetooth GATT server and advertiser state, and app screen after startup.
- **revision**: `c9195ea608ff3394e32aa217e7e640e5579d6f4d` plus the current dirty worktree; APK `app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk`, SHA-256 `B80BCEE041872D014D305456FF0C8956B306B61DC4AF5A0EE7BAC0FFAC123B0F`.
- **named_procedure**: Launch `com.chimali.shrunk/com.chimali.MainActivity`, tap the visible Start Authenticator button, wait eight seconds, then inspect `dumpsys activity services`, `dumpsys bluetooth_manager`, and app/Bluetooth logcat. A shell attempt to start/stop the private service was rejected by Android and did not change service state.
- **environment**: Xiaomi Mi A2 (`jasmine`), Android 10/API 29, serial `2ac6876`; Windows 11 x64; Gradle 9.6.0; Android SDK 37.
- **expected_observed_result**: GATT server registration and FIDO service addition succeeded (`0000fffd-0000-1000-8000-00805f9b34fb`, status 0), but Android's `onAdvertisingSetStarted` returned status 18 twice. Chimali then deleted the GATT service, emitted “Transport connect failed,” returned to “Ready to Start,” and `mAdvertisingServiceUuids` remained empty. The foreground-service notification alone therefore did not indicate working advertising.
- **date**: 2026-11-03
- **limitations**: This Mi run did not establish active BLE advertising, Windows discovery, pairing, a WebAuthn ceremony or recovery. The prior successful ASUS API 35 live-advertising result remains separate and does not demonstrate Mi support. Mi remains below the API 35 coverage-policy minimum; the coverage gate is deferred as requested. T020/T035 remain open.
- **status**: `Failed (Mi A2 BLE advertiser startup; status 18, GATT removed, UI returned to Ready to Start) / Passed (GATT registration and service addition only) / Unverified (Windows interoperability and recovery)`.
- **artifact_link**: Live state captured in the 2026-11-03 ADB session (not retained); [Nordic advertiser controller](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/NordicBleAdvertiserController.kt), [T020/T035](tasks.md)

### Record 035: Mi A2 Legacy Connectable Advertising Recovery (T020 — Passed, BLE Startup)
- **requirement**: T020; `FR-BLE-010`, `FR-BLE-060`, `TR-BLE-010`
- **scope**: Diagnose and correct Mi A2/API 29 advertising startup using the approved Nordic Kotlin BLE Library, then install the rebuilt app as an in-place update and verify the production UI, Android advertiser callback, foreground service, and FIDO GATT service.
- **revision**: `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current dirty worktree; updated shrunkDebug APK SHA-256 `EB33BA14FEE32AC080B9A12A742835D390CC94362F32B7386CF0142EB2AE3A7F`.
- **named_procedure**: Set Nordic `BleAdvertisingSettings` to `connectable = true`, `legacyMode = true`, and `scannable = true`; assert those fields in`NordicBleAdvertiserControllerTest`; run `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.transport.NordicBleAdvertiserControllerTest`; assemble `:app:assembleShrunkDebug`; install with `adb install -r` without clearing app data; open MainActivity and tap Start Authenticator; inspect app screen, `dumpsys bluetooth_manager`, service state and Bluetooth logcat.
- **environment**: Xiaomi Mi A2 / product `jasmine`, Android 10/API 29, serial `2ac6876`; Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 21 Gradle daemon.
- **expected_observed_result**: Focused Nordic advertiser host tests passed. The first run with legacy/connectable but non-scannable settings was rejected locally by `AdvertisingSetParameters.Builder.build()` because Android requires connectable legacy advertising to be scannable. After setting all three parameters, the device callback reported `onAdvertisingSetStarted ... status=0`; the app showed “Advertising... Ready to pair with a new host PC”; the foreground `Fido2TransportService` remained active; and the GATT server listed registered, started FIDO service `0000fffd-0000-1000-8000-00805f9b34fb` with FIDO characteristics. The existing saved app data was preserved by in-place installation.
- **date**: 2026-11-03
- **limitations**: This verifies Mi API 29 advertisement/GATT startup only. No Windows discovery, pairing ceremony, GetAssertion, interruption recovery or 50-cycle test was performed. The run cannot satisfy Feature 058's API 35 coverage policy; the user deferred the coverage gate.
- **status**: `Passed (focused Nordic advertiser host test and Mi A2 advertising/GATT startup after explicit connectable legacy scannable configuration) / Unverified (Windows interoperability, full lifecycle acceptance and coverage)`.
- **artifact_link**: [focused Nordic advertiser host test](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.transport.NordicBleAdvertiserControllerTest.xml), [Nordic advertiser controller](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/NordicBleAdvertiserController.kt), [T020/T035](tasks.md)

### Record 036: Mi A2 App-Driven Advertising Stop and Restart (T035 — Partial)
- **requirement**: T035; `FR-BLE-010`, `FR-BLE-060`, `TR-BLE-010`, `TR-BLE-040`
- **scope**: Exercise Start → Stop → Start and background/foreground persistence from Chimali Authenticator on the Mi A2 after applying the legacy connectable scannable advertising fix; verify service/GATT cleanup and re-registration without a host connection.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current dirty worktree; installed shrunkDebug APK SHA-256 `EB33BA14FEE32AC080B9A12A742835D390CC94362F32B7386CF0142EB2AE3A7F`.
- **named_procedure**: With Chimali advertising, tap Stop Authenticator; after two seconds inspect the app service, FIDO GATT service and Bluetooth log; tap Start Authenticator; after five seconds inspect advertiser callback, service and GATT registration; press Home, verify the launcher is foreground after three seconds while checking service/GATT/advertiser presence, then return to MainActivity.
- **environment**: Xiaomi Mi A2 / product `jasmine`, Android 10/API 29, serial `2ac6876`; Windows 11 x64; Gradle 9.6.0; Android SDK 37.
- **expected_observed_result**: Stop invoked Android `stopAdvertisingSet()`; the transport service and FIDO GATT service were absent at the two-second check. Start restored the registered/started FIDO GATT service, active foreground `Fido2TransportService`, and the production UI's Advertising state; Android reported `onAdvertisingSetStarted ... status=0`. After Home, Launcher was foreground while `Fido2TransportService`, the registered GATT server and started FIDO service remained present. App was returned to foreground and remained advertising at the end.
- **date**: 2026-11-03
- **limitations**: These are single local stop/restart and background checks with no connected host. They do not cover interruption during a ceremony, permission revocation, stale bond recovery, 50 reconnect cycles, memory baseline or Windows assertions. Stop duration was not measured to the <1s criterion. API 29 remains ineligible for Feature 058 coverage, which the user deferred.
- **status**: `Passed (Mi manual advertising stop cleanup, restart restoration and background persistence) / Unverified (Stop <1s timing, host interruption/reconnect, revocation, repeated cycle/memory, Windows interoperability and coverage)`.
- **artifact_link**: ADB log captured during the 2026-11-03 session (not retained); [Mi advertising recovery Record 035](#record-035-mi-a2-legacy-connectable-advertising-recovery-t020--passed-ble-startup), [T035](tasks.md)

### Record 037: Nordic Legacy Advertiser Instrumentation on Mi A2 (T036 — Passed)
- **requirement**: T036; `FR-BLE-010`, `TR-BLE-010`
- **scope**: Add and execute an API 28–30 Android device regression test that starts the production Nordic advertiser controller with its explicit connectable, legacy, scannable FIDO configuration, then stops it in `finally`.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current dirty worktree;`NordicBleLegacyAdvertisingDeviceTest` is new in this worktree.
- **named_procedure**: Stop the app-owned advertiser from the UI to avoid competing sets; run `:feature:fido2:connectedAndroidDeviceTest --no-configuration-cache --no-problems-report --max-workers=1 --project-prop=android.testInstrumentationRunnerArguments.class=com.chimali.fido2.data.transport.NordicBleLegacyAdvertisingDeviceTest` with `ANDROID_SERIAL=2ac6876`; verify retained XML; reopen MainActivity and restart its advertiser.
- **environment**: Xiaomi Mi A2 / Android 10/API 29, serial `2ac6876`; Windows 11 x64; Gradle 9.6.0; SDK 37; JetBrains JDK 21 Gradle daemon.
- **expected_observed_result**: The API-filtered device test ran exactly one test, `connectableLegacyAdvertisingStartsOnApi28Through30`, with 0 failures, errors or skips. The test's start returned success. After it completed, Chimali was relaunched and restored to Advertising with advertiser callback status 0, active foreground service and registered/started FIDO GATT service.
- **date**: 2026-11-03
- **limitations**: The test proves local advertiser API startup on one API 29 model; it does not exercise peer discovery, Windows, pairing, a ceremony or other OEM/API implementations. Coverage eligibility is separate; the user deferred Feature 058's API 35 coverage gate.
- **status**: `Passed (one API 29 Nordic advertiser instrumentation test; 0 failures/errors/skips) / Unverified (other APIs/OEMs, Windows interoperability and acceptance matrix)`.
- **artifact_link**: [retained Mi A2 device-test XML](../../feature/fido2/build/reports/fido2/064/mi-connected-suite-post-retry-fix-20261104.xml), [device test source](../../feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/data/transport/NordicBleLegacyAdvertisingDeviceTest.kt), [T036](tasks.md)

### Record 038: Post-Fix FIDO2 Host and Mi Device Suite (T036 — Partial)
- **requirement**: T036; parent gate/selected-handle composition boundary; `FR-BLE-010`, `FR-BLE-020`, `FR-BLE-030`, `FR-BLE-060`, `TR-BLE-010`, `TR-BLE-020`
- **scope**: Rerun the complete configured FIDO2 Android host suite and connected Android device suite after the Mi-compatible legacy advertiser change and addition of the API 28–30 advertiser regression.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus the current uncommitted Feature 064 changes on branch `064-bluetooth-transport-selection`.
- **named_procedure**: Set `GRADLE_USER_HOME=.gradle/codex-user-home` and `ANDROID_SERIAL=2ac6876`; run `:feature:fido2:testAndroidHostTest :feature:fido2:connectedAndroidDeviceTest`. Gradle used its in-process Kotlin compiler fallback after a Kotlin daemon marker-file access error. The connected task ended failed; a secondary Gradle problems-report write also received `AccessDeniedException`.
- **environment**: Xiaomi Mi A2 / product `jasmine`, Android 10/API 29, serial `2ac6876`; Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: The host suite passed 1,126 tests with 0 failures, errors or skips, including parent selection-gate composition coverage. The connected device XML reports 49 tests, 5 failures, 0 errors and 0 skips; the new`NordicBleLegacyAdvertisingDeviceTest.connectableLegacyAdvertisingStartsOnApi28Through30` passed. The five failing research/performance cases each stopped at their required Perfetto/tracing precondition. The remaining 44 device test cases passed, including the advertiser regression. No coverage task ran.
- **date**: 2026-11-03
- **limitations**: This is a partial, failed connected suite, not T036 completion. The tracing cases need their separately controlled Perfetto procedures and are not accepted as passes; API-31 lifecycle cases cannot execute on API 29. Mi API 29 does not meet the API 35 minimum for Feature 058 coverage. This run proves the Mi advertiser controller starts on API 29 and host composition tests pass; it does not prove Windows interoperability, parent switching acceptance, physical ceremony recovery, full coverage or 50-cycle stress.
- **status**: `Passed (1,126/1,126 configured FIDO2 host tests; Mi advertiser instrumentation regression included in 44/49 device cases) / Failed (five required tracing preconditions in the connected suite; Gradle problems-report write) / Unverified (API-31 lifecycle, Windows ceremonies, physical recovery and coverage)`.
- **artifact_link**: [retained post-fix Mi device-suite XML](../../feature/fido2/build/reports/fido2/064/mi-full-suite-after-legacy-advertising-20261103.xml), [FIDO2 host XML directory](../../feature/fido2/build/test-results/testAndroidHostTest), [advertiser device regression](../../feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/data/transport/NordicBleLegacyAdvertisingDeviceTest.kt), [T036](tasks.md)

### Record 039: Mi A2 Perfetto App-Tracing Availability Probe (T036 — Unverified)
- **requirement**: T036;`NFR-PERF-030`, `TR-AUTH-031`
- **scope**: Attempt to satisfy the tracing precondition for the standalone synthetic Android trace-lifecycle test on the connected Mi A2 without changing production app data.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus the current uncommitted Feature 064 changes.
- **named_procedure**: Install only `:feature:fido2:installAndroidDeviceTest`; start Perfetto with a minimal config selecting `atrace_apps: "com.chimali.fido2.test"` and `/dev/null` output; run the focused `PerformanceTraceDeviceTest` using `:feature:fido2:connectedAndroidDeviceTest` and its class filter. Also checked the repository's saved Perfetto config and the device's `debug.atrace.tags.enableflags` property.
- **environment**: Xiaomi Mi A2 / product `jasmine`, Android 10/API 29, serial `2ac6876`; user build (`ro.debuggable=0`); Windows 11 x64; Gradle 9.6.0; Android SDK 37.
- **expected_observed_result**: The test package installed successfully and Perfetto accepted the minimal background invocation, but `debug.atrace.tags.enableflags` remained `0`; the focused test XML reports 1 test, 1 failure, 0 errors and 0 skips at `Trace.isEnabled()`'s explicit “Start Perfetto with this test app in atrace_apps” assumption. The repository's saved config uses fields unsupported by this device's older Perfetto parser. The device denied trace output under `/data/misc/perfetto-traces` and `/data/local/tmp`; no trace file was retained. The production app and its data were not modified.
- **date**: 2026-11-03
- **limitations**: This does not verify Android trace emission and does not turn the five full-suite tracing cases into passes. It shows this Mi user build could not enable the app trace tag with the tested shell Perfetto setup. Repeat these diagnostics on an authorized supported API 35+ userdebug/perfetto environment; Mi API 29 remains ineligible for Feature 058 coverage.
- **status**: `Unverified (Mi app tracing unavailable through tested Perfetto configuration; focused test did not pass)`.
- **artifact_link**: [focused Mi test XML](../../feature/fido2/build/reports/fido2/064/mi-perfetto-focused-test-after-attempt-20261103.xml), [trace test source](../../feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/util/performance/PerformanceTraceDeviceTest.kt), [T036](tasks.md)

### Record 040: ASUS API 35 Perfetto Trace Marker Smoke Test (T036 — Partial)
- **requirement**: T036;`NFR-PERF-030`, `TR-AUTH-031`
- **scope**: Run the standalone synthetic trace lifecycle test with a live Perfetto `atrace_apps` session on ASUS, separate from the procedure-specific calibration and overhead studies.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes on branch `064-bluetooth-transport-selection`.
- **named_procedure**: Install the Android device-test APK with `:feature:fido2:installAndroidDeviceTest`; start a 180-second Perfetto session using a 16 MiB ring buffer and `atrace_apps: "com.chimali.fido2.test"`; run `:feature:fido2:connectedAndroidDeviceTest --no-configuration-cache --no-problems-report --max-workers=1 --project-prop=android.testInstrumentationRunnerArguments.class=com.chimali.fido2.util.performance.PerformanceTraceDeviceTest`; stop the session, pull the trace, delete the remote trace, then query `slice` for `Fido2.%` events with Trace Processor.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; Windows 11 x64; Gradle 9.6.0; SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: The focused test reports 1 test, 0 failures/errors/skips. A re-query of the linked raw trace found one `S` and one `F` raw ftrace print marker for `Fido2.Registration.SettingsLookup`, and four `S`/`F` pairs for `Fido2.Registration.SettingsLookup.Coroutine`. The `slice` query returned zero `Fido2.*` slices, while Trace Processor reported `systrace_parse_failure=37`; `traced_final_flush_failed` and `traced_flushes_failed` were zero. The earlier claim that these were imported slice counts was inaccurate. The raw trace has since been deleted after analysis; only aggregate results are retained. The device trace path was removed. Only the test APK was installed; no production app data was cleared or changed.
- **date**: 2026-11-03
- **limitations**: This verifies the standalone test and observes its expected raw marker pairs on ASUS only; parser errors prevent healthy slice-import verification. It does not run the separately parameterized calibration, pilot or overhead experiments, nor does it make the full Mi or ASUS connected suite green. T036 remains open.
- **status**: `Passed (one API 35 focused device test and five raw begin/end marker pairs) / Failed (trace parser health, 37 systrace parse failures) / Unverified (imported Fido2 slices, procedure-specific studies and full configured device suite)`.
- **artifact_link**: [sanitized ASUS trace marker re-query](../../feature/fido2/build/reports/fido2/064/asus-perfetto-marker-requery-20261104.txt), [ASUS trace lifecycle XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [trace test source](../../feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/util/performance/PerformanceTraceDeviceTest.kt), [T036](tasks.md)

### Record 041: Current FIDO2 Device Suite on ASUS/API 35 (T036 — Partial)
- **requirement**: T036; `FR-BLE-020`, `FR-BLE-030`, `FR-BLE-060`, `TR-BLE-020`; parent selected-handle composition boundary
- **scope**: Run the complete configured FIDO2 Android device suite on the API 35 ASUS after the Mi advertising compatibility change and separate Perfetto smoke validation.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes on branch `064-bluetooth-transport-selection`.
- **named_procedure**: Set `GRADLE_USER_HOME=.gradle/codex-user-home` and `ANDROID_SERIAL=R7AIB7002901AZ3`; run `:feature:fido2:connectedAndroidDeviceTest --no-configuration-cache --no-problems-report --max-workers=1` with no Perfetto session active. Production app `com.chimali.shrunk` was not launched, uninstalled, or cleared.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: The retained XML reports 51 tests, 46 passed, 5 failed, 0 errors and 0 skips. The 46 passing cases include 3 `FidoBleLifecycleDeviceTest` cases, 2 GATT security cases, 2 credential persistence cases, 3 process-restart cases and the settings/UI tests. All five failures are the research/performance cases that require an active trace setup or specific instrumentation arguments. The separate `PerformanceTraceDeviceTest` passes under Perfetto in Record 040.
- **date**: 2026-11-03
- **limitations**: The five failed cases are not passes; four require their dedicated calibrated procedure and arguments, while `PerformanceTraceDeviceTest` passes only in its separately configured run. The API28–30 Mi-specific advertiser regression is inapplicable on API 35. This device suite does not exercise Windows registration/assertion, BLE/Classic switching, or the full parent acceptance matrix; T036/T039 remain open.
- **status**: `Passed (46/51 ASUS device tests, including API 35 BLE lifecycle and GATT security) / Failed (five procedure-specific tracing cases without active setup) / Unverified (full configured suite, Windows ceremonies and parent acceptance)`.
- **artifact_link**: [retained ASUS full-suite XML](../../feature/fido2/build/reports/fido2/064/asus-full-suite-current-20261103.xml), [focused ASUS Perfetto trace smoke (Record 040)](#record-040-asus-api-35-perfetto-trace-lifecycle-smoke-test-t036--passed-scoped), [T036](tasks.md)

### Record 043: API 28+ Nordic Advertiser Regression on ASUS/API 35 (T036 — Partial)
- **requirement**: T036; `FR-BLE-010`, `TR-BLE-010`
- **scope**: Extend the Mi advertiser regression to API 28+ and verify the same production Nordic advertiser controller starts and stops on API 35, granting runtime Bluetooth permissions only to the instrumentation package where the platform requires them.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes, including the widened API filter and instrumentation permission grant.
- **named_procedure**: Run `:feature:fido2:ktlintAndroidDeviceTestSourceSetCheck :feature:fido2:testAndroidHostTest :feature:fido2:connectedAndroidDeviceTest --no-configuration-cache --no-problems-report --max-workers=1` with `ANDROID_SERIAL=R7AIB7002901AZ3`; retain the complete device XML. On 2026-11-04, run `:feature:fido2:detekt --no-configuration-cache --no-problems-report --max-workers=1` after this test-source change.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: The expanded `connectableLegacyAdvertisingStartsOnApi28Plus` test passed on ASUS. Device-test-source KtLint and the FIDO2 host suite passed (1,126/1,126); the follow-up FIDO2 Detekt task also passed. The full device XML reports 52 tests, 47 passed, 5 failed, 0 errors and 0 skips; the five failures remain the research/performance tracing cases. No coverage task ran.
- **date**: 2026-11-03
- **limitations**: This extends advertiser startup evidence to one API 35 ASUS and preserves the Mi/API 29 result; it does not establish other OEM/API behavior or Windows discovery/ceremonies. The full device suite remains partial: four experiments need their dedicated procedures/arguments and the standalone trace test requires a live Perfetto session (it passes separately in Record 040). T036 remains open.
- **status**: `Passed (API 35 advertiser regression; source-set KtLint; 1,126/1,126 host tests) / Failed (five full-suite tracing preconditions without configured setup) / Unverified (Windows interoperability and full acceptance)`.
- **artifact_link**: [retained ASUS full-suite XML](../../feature/fido2/build/reports/fido2/064/asus-full-suite-after-api28-advertiser-test-20261103.xml), [expanded advertiser device test](../../feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/data/transport/NordicBleLegacyAdvertisingDeviceTest.kt), [T036](tasks.md)

### Record 044: Focused Nordic Advertiser Recheck on ASUS/API 35 (T036 — Passed, Scoped)
- **requirement**: T036; `FR-BLE-010`, `TR-BLE-010`
- **scope**: Repeat the API 28+ advertiser startup regression on the ASUS after it was reconnected, using only the filtered device test.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes.
- **named_procedure**: Set `GRADLE_USER_HOME=.gradle/codex-user-home` and `ANDROID_SERIAL=R7AIB7002901AZ3`; run `:feature:fido2:connectedAndroidDeviceTest --no-configuration-cache --no-problems-report --max-workers=1 --project-prop=android.testInstrumentationRunnerArguments.class=com.chimali.fido2.data.transport.NordicBleLegacyAdvertisingDeviceTest`.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: The focused device test passed: 1 test, 0 failures, 0 errors and 0 skips. `connectableLegacyAdvertisingStartsOnApi28Plus` started and stopped the production Nordic advertiser controller successfully.
- **date**: 2026-11-04
- **limitations**: This is a focused advertising-controller regression only. It does not establish the app's foreground advertising UI, Windows discovery, WebAuthn ceremonies, transport switching, coverage or the full device suite; T036 remains open.
- **status**: `Passed (focused advertiser startup on ASUS/API 35) / Unverified (Windows interoperability and complete acceptance)`.
- **artifact_link**: [ASUS focused advertiser XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [advertiser test source](../../feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/data/transport/NordicBleLegacyAdvertisingDeviceTest.kt), [T036](tasks.md)

### Record 045: Fresh shrunkDebug Profile Advertises on ASUS/API 35 (T036 — Passed, Scoped)
- **requirement**: T036; `FR-BLE-010`, `TR-BLE-010`; parent recovery boundary
- **scope**: Recover ASUS test availability after the prior test profile's stored wallet seed became undecryptable, preserving the separate debug package and verifying the fresh shrunkDebug profile starts BLE advertising.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 and Android backup-policy changes.
- **named_procedure**: After the owner explicitly authorized resetting only `com.chimali.shrunk`, run `adb shell pm clear --user 0 com.chimali.shrunk`; install `app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk`; launch `com.chimali.shrunk/com.chimali.MainActivity`; grant Android's Nearby devices and notification prompts; select Passkey Authenticator in clean-profile onboarding; tap Start Authenticator; inspect UI text, process state, fresh logcat and `dumpsys bluetooth_manager`. The separate `com.chimali` package was untouched.
- **environment**: ASUS AI2302 (Zenfone 10) / Android 15/API 35, serial `R7AIB7002901AZ3`; shrunkDebug package version 0.1.0, rebuilt from current worktree; Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: `pm clear` and APK installation succeeded. The app process stayed alive without a new Keystore/decryption exception. The UI showed `Advertising...` and `Ready to pair with a new host PC`. Bluetooth manager reported one connectable, scannable legacy advertising set registered to `com.chimali.shrunk` (appIf 13); two separate ongoing sets belonged to Google Play services. The advertiser log recorded `BluetoothLeAdvertiser` data size 19 and `StartAdvertisingSet`.
- **date**: 2026-11-04
- **limitations**: The owner-authorized clear erased the prior synthetic wallet and passkeys for `com.chimali.shrunk`; no other app data was cleared. This is app/UI and Android Bluetooth-manager evidence, not an independent RF scan, Windows pairing, WebAuthn ceremony, interruption/recovery matrix, coverage run or full connected suite. The backup-policy change was not tested through an actual backup/restore cycle; T036 remains open.
- **status**: `Passed (fresh ASUS/API 35 app launch and advertiser state) / Unverified (independent host discovery and physical ceremonies)`.
- **artifact_link**: [fresh ASUS advertising excerpt](../../feature/fido2/build/reports/fido2/064/asus-fresh-profile-advertising-20261104.txt), [rebuilt shrunkDebug APK](../../app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk), [T036](tasks.md)

### Record 046: ASUS BLE Force-Stop and Restart Cycle (T035/T036 — Passed, Scoped)
- **requirement**: T035/T036; `FR-BLE-010`, `TR-BLE-010`; parent T035 lifecycle boundary
- **scope**: Exercise one Android force-stop/relaunch cycle with BLE advertising active, verify the authenticator selection survives while runtime advertising stops, then restart advertising from the UI.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 and backup-policy changes.
- **named_procedure**: With the fresh synthetic ASUS profile advertising, run `adb shell am force-stop com.chimali.shrunk`; verify the app process and its entry in Bluetooth manager's ongoing advertisements disappear; relaunch `com.chimali.shrunk/com.chimali.MainActivity`; inspect the visible state and persisted Authenticator tab; tap Start Authenticator; verify advertising returns in UI and Bluetooth manager. The separate `com.chimali` package was not targeted.
- **environment**: ASUS AI2302 (Zenfone 10) / Android 15/API 35, serial `R7AIB7002901AZ3`; `com.chimali.shrunk` 0.1.0; Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: Before force-stop, UI showed `Advertising...` and Bluetooth manager listed the app advertising set. Force-stop removed the process and app-owned advertising set. Cold relaunch succeeded with a live process and no fresh Keystore error; UI showed `Ready to Start` and retained the Authenticator selection. Tapping Start restored `Advertising...`, `Ready to pair with a new host PC`, and one app-owned ongoing legacy set. Two Google Play services advertising sets were unrelated and remained present.
- **date**: 2026-11-04
- **limitations**: This is one force-stop/relaunch/start cycle on a freshly reset synthetic profile. It does not cover reboot, OS process death independent of force-stop, in-flight ceremony, transport-mode changes, permission revocation, stale-bond recovery, 50-cycle stress/memory, Windows ceremonies, or coverage. T035/T036 remain open.
- **status**: `Passed (one ASUS force-stop/relaunch cycle and BLE restart) / Unverified (remaining lifecycle and host acceptance matrix)`.
- **artifact_link**: [ASUS force-stop/relaunch record](../../feature/fido2/build/reports/fido2/064/asus-force-stop-relaunch-20261104.txt), [T035/T036](tasks.md)

### Record 042: Full Ordinary Local CI on Mi/ASUS Candidate (T036/T038 — Coverage Deferred)
- **requirement**: BLE T036/T038; parent Selection T039 local-CI portion
- **scope**: Run ordinary repository CI after the Mi advertiser compatibility fix and focused API 29–35 device checks, with autofixes disabled and coverage omitted per the owner’s coverage deferral.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes on branch `064-bluetooth-transport-selection`.
- **named_procedure**: Run a temporary, untracked copy of `tools/local-ci.ps1` with only its Gradle command amended to include `--no-problems-report`; invoke `-NoFix -NoConfigurationCache` with `GRADLE_USER_HOME=.gradle/codex-user-home`. The temporary script was removed after completion. `-RunCoverage` was not supplied.
- **environment**: Windows 11 x64; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; Gradle 9.6.0; SDK 37; Microsoft OpenJDK 25.0.5. Both Mi A2/API 29 and ASUS AI2302/API 35 were connected, but the ordinary local CI pipeline did not run connected-device tests.
- **expected_observed_result**: Pipeline completed successfully in 643.35 seconds. KtLint, Detekt, release lint, all-source and test-source compilation (including Android device-test source), repository unit tests, and configured core/FIDO2 Android host suites passed. The FIDO2 host XML remains 1,126 tests, 0 failures/errors/skips. Kotlin daemon marker-file access errors fell back to in-process compilation; no compile failure resulted. Coverage and connected-device suites were not part of this local-CI run.
- **date**: 2026-11-03
- **limitations**: This completes only the ordinary local-CI portion of T036/T038/parent T039. It does not establish connected-device suite acceptance, coverage floors/completeness, trusted accepted-target nonregression, Windows ceremonies, physical transport switching or parent T041. Coverage remains deferred as directed.
- **status**: `Passed (ordinary local CI with fixes disabled; all configured static, lint, compile, unit and host checks) / Unverified or deferred (coverage and physical acceptance)`.
- **artifact_link**: [local CI procedure](../../tools/local-ci.ps1), [FIDO2 host XML directory](../../feature/fido2/build/test-results/testAndroidHostTest), [T036/T038](tasks.md)

### Record 047: ASUS Bluetooth Advertise Permission Revocation and Controlled Recovery (T035/T036 — Passed, Scoped)
- **requirement**: T035/T036; `FR-BLE-010`, `TR-BLE-010`; parent T035 lifecycle boundary
- **scope**: Revoke runtime `BLUETOOTH_ADVERTISE` while the ASUS synthetic profile is advertising, record Android's process-exit reason, explicitly regrant permission, then relaunch and restart advertising.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 and backup-policy changes.
- **named_procedure**: On ASUS/API 35, confirm `BLUETOOTH_ADVERTISE` is granted and app-owned advertising is active; run `adb shell pm revoke --user 0 com.chimali.shrunk android.permission.BLUETOOTH_ADVERTISE`; capture `ApplicationExitInfo`, permission state and `dumpsys bluetooth_manager`; explicitly run `adb shell pm grant --user 0 com.chimali.shrunk android.permission.BLUETOOTH_ADVERTISE`; launch `com.chimali.shrunk/com.chimali.MainActivity`, inspect its Ready to Start state, tap Start Authenticator, and verify restored advertising. No other package was targeted.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; `com.chimali.shrunk` 0.1.0; Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: Before revoke, permission was granted, the Activity was foreground and Bluetooth manager showed the app advertising. `pm revoke` set permission denied, removed Chimali advertising (ongoing count 3→2), and Android `ApplicationExitInfo` recorded the prior PID exit as `PERMISSION CHANGE` / `permissions revoked`, not an app crash; the task returned to the launcher. Explicit `pm grant` and Activity relaunch returned `Status=ok`, showed `Ready to Start`, and did not advertise prematurely. Tapping `Start Authenticator` restored `Advertising...`, `Ready to pair with a new host PC`, app_if 13 / Advertising ID 3, and ongoing count 3. The controlled grant source and complete sequence are recorded.
- **date**: 2026-11-04
- **limitations**: One ASUS/API 35 cycle with adb-driven grant; no human-operated Android Settings regrant flow or in-flight ceremony was tested. This does not cover cancellation, stale bonds, reboot, 50-cycle memory, Windows ceremonies, coverage or full acceptance. T035/T036 remain open.
- **status**: `Passed (controlled revoke/process-exit/explicit-regrant/relaunch/advertising recovery on ASUS) / Unverified (Settings UX and remaining lifecycle/host acceptance matrix)`.
- **artifact_link**: [controlled ASUS permission cycle](../../feature/fido2/build/reports/fido2/064/asus-permission-cycle-controlled-20261104.txt), [earlier permission-revocation excerpt](../../feature/fido2/build/reports/fido2/064/asus-advertise-permission-recovery-20261104.txt), [T035/T036](tasks.md)

### Record 048: Current Local CI and ASUS/Mi Connected Device Suites After Retry Fix (T036 — Partial)
- **requirement**: T036; parent selected-handle composition boundary and local host portion of Selection T039
- **scope**: Revalidate ordinary repository CI and configured FIDO2 Android device suites after fixing a race that could remove the foreground notification during retry.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus uncommitted Feature 064 changes, including the retry foreground-state fix.
- **named_procedure**: Run a temporary copy of `tools/local-ci.ps1` with Gradle `--no-problems-report` added, using `-NoFix -NoConfigurationCache` and omitting `-RunCoverage`; the temporary script is removed afterward. Run `:feature:fido2:connectedAndroidDeviceTest --no-configuration-cache --no-problems-report --max-workers=1` separately with `ANDROID_SERIAL=R7AIB7002901AZ3` (ASUS API 35) and `ANDROID_SERIAL=2ac6876` (Mi A2 API 29). Also run the focused `FidoBleLifecycleDeviceTest` class on ASUS.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5; ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; Mi A2 / Android 10/API 29, serial `2ac6876`.
- **expected_observed_result**: Ordinary local CI passed in 781.16 seconds: KtLint, Detekt, release lint, compile-all, repository unit tests and configured KMP Android host suites; FIDO2 host result was 1,126/1,126. The focused ASUS lifecycle class passed 3/3. The full ASUS suite passed 47/52, and the full Mi suite passed 44/49. On both devices, the only failures were the same five Perfetto/tracing research tests, which require their dedicated trace sessions and/or procedure arguments. The previously failing ASUS retry-notification lifecycle test now passes in both the focused class and full suite. Coverage was not run.
- **date**: 2026-11-04
- **limitations**: Five device tracing/research cases remain failed in the unconfigured full-suite run and are not passes; their dedicated measurement protocols are separate from BLE transport validation. Mi API 29 cannot satisfy Feature 058's API 35+ coverage-device prerequisite. Neither suite verifies Windows ceremonies, physical transport switching, all interruption/reboot/50-cycle scenarios, accepted-target coverage/nonregression or full acceptance. T036/T038 remain open and coverage remains deferred.
- **status**: `Passed (ordinary local CI; 1,126/1,126 FIDO2 host tests; ASUS lifecycle retry fix; 47/52 ASUS and 44/49 Mi device suites) / Failed (five tracing-precondition cases per full device suite) / Unverified (Windows and complete acceptance; coverage deferred)`.
- **artifact_link**: [ASUS full-suite XML](../../feature/fido2/build/reports/fido2/064/asus-connected-suite-post-retry-fix-summary-20261104.txt), [Mi full-suite XML](../../feature/fido2/build/reports/fido2/064/mi-connected-suite-post-retry-fix-20261104.xml), [ordinary local CI summary](../../feature/fido2/build/reports/fido2/064/local-ci-post-retry-fix-20261104.txt), [Retry implementation](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/service/Fido2TransportService.kt), [T036/T038](tasks.md)

### Record 049: ASUS shrunkDebug In-Place Update and BLE Restart After Retry Fix (T036 — Passed, Scoped)
- **requirement**: T036; `FR-BLE-010`, `FR-BLE-060`, `TR-BLE-010`; parent selected-handle/lifecycle boundary
- **scope**: Build and install the current production synthetic variant after the foreground retry ordering change, then verify its saved Authenticator selection and explicit BLE restart without clearing app data.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus uncommitted Feature 064 changes, including the retry foreground-state fix.
- **named_procedure**: Run `:app:assembleShrunkDebug --no-configuration-cache --no-problems-report --max-workers=1`; install with `adb install -r app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk`; cold launch `com.chimali.shrunk/com.chimali.MainActivity`; inspect UI, runtime permission and Bluetooth manager; tap Start Authenticator and inspect again. No app clear/uninstall was run.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; package `com.chimali.shrunk` 0.1.0; Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: `assembleShrunkDebug` passed in 8m38s and in-place install returned `Success`. Cold launch returned status `ok`, kept a live process, showed `Ready to Start` and retained the Authenticator tab; `BLUETOOTH_ADVERTISE` was granted and no new Keystore failure was observed. After Start Authenticator, UI showed `Advertising...` and `Ready to pair with a new host PC`; Bluetooth manager listed one app-owned ongoing advertiser (appIf 13). Two unrelated Google Play services sets remained.
- **date**: 2026-11-04
- **limitations**: Confirms one production-variant install/launch/restart on ASUS only. Does not establish Windows discovery or ceremonies, permission-loss recovery, BLE/Classic switching, reboot/50-cycle acceptance, coverage or parent SC-001–006. T036 remains partial due five unconfigured trace/research failures; physical acceptance remains open.
- **status**: `Passed (shrunkDebug in-place update and ASUS BLE advertiser restart) / Unverified (host ceremonies and full acceptance)`.
- **artifact_link**: [ASUS in-place update evidence](../../feature/fido2/build/reports/fido2/064/asus-retry-fix-shrunkdebug-reinstall-20261104.txt), [APK](../../app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk), [T036](tasks.md)

### Record 050: ASUS Trace Lifecycle Completion Race (T036 — Partial)
- **requirement**: T036;`NFR-PERF-030`, `TR-AUTH-031`
- **scope**: Re-run the standalone synthetic Android trace lifecycle device test on ASUS, investigate its immediate completion assertion, and validate the bounded-drain test synchronization with the host lifecycle suite and source-set lint.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus uncommitted Feature 064 changes, including the updated `PerformanceTraceDeviceTest`.
- **named_procedure**: Start a 180-second Perfetto session with a 16 MiB ring buffer and `atrace_apps: "com.chimali.fido2.test"`; run `:feature:fido2:connectedAndroidDeviceTest --no-configuration-cache --no-problems-report --max-workers=1 --project-prop=android.testInstrumentationRunnerArguments.class=com.chimali.fido2.util.performance.PerformanceTraceDeviceTest`; stop/pull/query/delete the raw trace. Then run `:feature:fido2:ktlintAndroidDeviceTestSourceSetCheck :feature:fido2:testAndroidHostTest --tests com.chimali.fido2.util.performance.CoroutineTraceLifecycleTest --no-configuration-cache --no-problems-report --max-workers=1`.
- **environment**: Windows 11 x64; ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; Perfetto v46.0; Trace Processor v59.1; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: The initial focused device run failed at `LookupTraceCorrelation.isComplete()` immediately after the coroutine returned, matching the lifecycle-restore drain race already guarded against by the host suite. The device test was changed to wait up to five seconds for lifecycle completion; its rerun passed 1/1. The host `CoroutineTraceLifecycleTest` passed 7/7 and `ktlintAndroidDeviceTestSourceSetCheck` passed. Perfetto ran and the raw trace was pulled, queried, then deleted from both phone and worktree; the query returned zero `Fido2.*` slices, so trace-slice emission was not confirmed by this capture.
- **date**: 2026-11-04
- **limitations**: This fixes deterministic test synchronization and verifies correlation bookkeeping on one ASUS/API 35 device. The zero-slice query means this run does not validate emitted Android trace slices; the prior scoped trace record is separate evidence. It does not exercise Windows ceremonies, full transport interruption/recovery, 50-cycle acceptance, coverage, or the parent matrix. T036 remains open and coverage remains deferred.
- **status**: `Passed (bounded device lifecycle-drain wait; 1/1 ASUS test; 7/7 host lifecycle tests; device-source KtLint) / Unverified (emitted Fido2 trace slices; full configured suite and acceptance)`.
- **artifact_link**: [ASUS trace lifecycle race-fix summary](../../feature/fido2/build/reports/fido2/064/asus-trace-lifecycle-race-fix-20261104.txt), [ASUS test XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [host test XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.util.performance.CoroutineTraceLifecycleTest.xml), [updated device test](../../feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/util/performance/PerformanceTraceDeviceTest.kt), [T036](tasks.md)

### Record 051: ASUS Full Connected Suite with Perfetto Enabled (T036 — Partial)
- **requirement**: T036;`NFR-PERF-030`, `TR-AUTH-031`; FIDO BLE device-suite boundary
- **scope**: Run the full configured FIDO2 Android device suite on ASUS/API 35 with the standalone trace precondition enabled, and distinguish transport/lifecycle results from research tests requiring dedicated arguments.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus uncommitted Feature 064 changes, including the trace lifecycle test synchronization update.
- **named_procedure**: Start a 300-second Perfetto session with a 16 MiB ring buffer and `atrace_apps: "com.chimali.fido2.test"`; run `:feature:fido2:connectedAndroidDeviceTest --no-configuration-cache --no-problems-report --max-workers=1` with `ANDROID_SERIAL=R7AIB7002901AZ3`; stop/pull/query/delete the raw trace.
- **environment**: Windows 11 x64; ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; Perfetto v46.0; Trace Processor v59.1; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: The suite reported 52 tests: 48 passed, 4 failed, 0 errors, 0 skips. The updated `PerformanceTraceDeviceTest` passed. The four failures are `EmpiricalCalibrationDeviceTest` (missing required `calibration_phase=holdout`) and `FilteredMeasurementPilotDeviceTest`, `LowerOverheadControlExplorationDeviceTest`, and `TraceOverheadExplorationDeviceTest` (missing their dedicated frozen per-run inputs). No arguments were invented. Trace Processor reported zero `Fido2.*` slices, 65 `systrace_parse_failure` events, zero CPU ftrace overrun counters, zero `traced_final_flush_failed`, and zero `traced_flushes_failed`. Raw trace was pulled, queried, then deleted locally and from the ASUS.
- **date**: 2026-11-04
- **limitations**: The suite remains partial and four failures are not passes. Missing study-specific inputs must be taken from the corresponding frozen research procedures before those cases can be executed. The trace capture did not confirm emitted FIDO2 slices and had systrace parse failures. This does not exercise Windows ceremonies, transport switching, the complete interruption/stale-bond/50-cycle matrix, trusted coverage or parent SC-001–006. T036 remains open and coverage is deferred.
- **status**: `Passed (48/52 ASUS device tests; standalone trace lifecycle precondition passed) / Failed (four research tests missing dedicated inputs) / Unverified (trace-slice emission; physical and complete acceptance)`.
- **artifact_link**: [ASUS full-suite/Perfetto summary](../../feature/fido2/build/reports/fido2/064/asus-connected-suite-perfetto-summary-20261104.txt), [Gradle run log](../../feature/fido2/build/reports/fido2/064/asus-connected-suite-perfetto-20261104.txt), [ASUS XML report](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [T036](tasks.md)

### Record 052: ASUS Settings Denial and Nearby Devices Prompt Recovery (T035/T036 — Passed, Scoped)
- **requirement**: T035/T036; `FR-BLE-010`, `FR-BLE-070`, `TR-BLE-010`; parent T035 lifecycle boundary
- **scope**: Exercise a human-visible Nearby devices permission denial from Android Settings, capture Android's permission-change process exit, and recover by granting the runtime permission and explicitly starting the authenticator.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus uncommitted Feature 064 changes.
- **named_procedure**: On ASUS/API 35, open app details for `com.chimali.shrunk`; choose Permissions → Nearby devices → Don't allow; verify runtime permission denial, Android `ApplicationExitInfo`, and disappearance from Bluetooth ongoing advertisers. Launch Chimali, allow its Android Nearby Devices runtime prompt, verify Ready to Start with no active advertising, tap Start Authenticator, then verify status and Bluetooth manager. Delete temporary UI hierarchy dumps after inspection.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; synthetic package `com.chimali.shrunk` 0.1.0; Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5.
- **expected_observed_result**: Settings denial changed `BLUETOOTH_ADVERTISE` to denied, removed Chimali from active advertising (count 3→2), and Android recorded the prior process exit as `PERMISSION CHANGE` / `permissions revoked`, not an application exception. Launch prompted “Allow Chimali to find, connect to, and determine the relative position of nearby devices?”; after Allow, Chimali showed Ready to Start and did not advertise automatically. After the explicit Start Authenticator tap, the UI showed `Advertising...` / `Ready to pair with a new host PC`; Bluetooth manager reported one Chimali advertiser (app_if 13, Advertising ID 0) plus two unrelated Google Play services advertisers. No separate broadcast/advertising confirmation appeared. Temporary UI hierarchy dumps were deleted from host and device; only the sanitized result is retained.
- **date**: 2026-11-04
- **limitations**: One ASUS/API 35 cycle; the grant was through Android's runtime permission prompt rather than the Settings Allow option. No in-flight ceremony, Windows pairing, stale bond, reboot, 50-cycle memory, coverage or full acceptance matrix was exercised. T035/T036 remain open.
- **status**: `Passed (Settings denial, permission-change process exit, runtime-prompt grant and explicit advertising restart) / Unverified (direct Settings regrant, host ceremonies and remaining lifecycle matrix)`.
- **artifact_link**: [sanitized ASUS Settings recovery summary](../../feature/fido2/build/reports/fido2/064/asus-settings-permission-recovery-20261104.txt), [T035/T036](tasks.md)

### Record 053: ASUS shrunkDebug Data Reset and Bluetooth Radio Toggle Recovery (T035/T036 — Passed, Scoped)
- **requirement**: T035/T036; `FR-BLE-010`, `FR-BLE-060`, `TR-BLE-010`; parent T035 lifecycle boundary
- **scope**: Reset only the synthetic `com.chimali.shrunk` profile after its saved wallet ciphertext prevented launch, reinitialize FIDO2, and inspect advertiser state after a Bluetooth radio off/on interruption.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus uncommitted Feature 064 changes.
- **named_procedure**: After owner approval, run `adb shell pm clear com.chimali.shrunk`; leave `com.chimali` untouched. Launch `MainActivity`, accept the previously authorized Nearby devices and notifications prompts, select Passkey Authenticator in first-run setup, and tap Start Authenticator. Verify permission, UI, process and advertiser. Toggle Bluetooth off then on with `svc bluetooth`; after the adapter reports on, verify the app UI and Bluetooth advertiser list.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; synthetic package `com.chimali.shrunk` 0.1.0; Windows 11 x64.
- **expected_observed_result**: The clear command succeeded and Android recorded the old PID exit as `USER REQUESTED` / `FORCE STOP`, description `stop com.chimali.shrunk due to clear data`; there was no new crash. First-run setup completed, the Authenticator screen showed `Ready to Start`, then `Advertising...` / `Ready to pair with a new host PC` after explicit start. `BLUETOOTH_ADVERTISE` was granted, and Bluetooth manager listed one Chimali set plus two unrelated Google Play services sets (3 total). After a Bluetooth off/on cycle, the adapter reported on, the app remained alive, the UI again showed `Advertising...` / `Ready to pair with a new host PC`, and Bluetooth manager again listed the Chimali advertising set (3 total).
- **date**: 2026-11-04
- **limitations**: The reset erased only synthetic local data in `com.chimali.shrunk`; the separate `com.chimali` package was not touched. This is one ASUS reset/setup and radio-toggle cycle. It does not cover a full phone reboot, mid-ceremony interruption, BLE/Classic switching, Windows ceremonies, stale bonds, 50-cycle memory, coverage or the complete acceptance matrix. The radio-off advertiser-list snapshot was not considered conclusive; only post-enable recovery is claimed. T035/T036 remain open.
- **status**: `Passed (scoped shrunkDebug reset/setup and post-toggle BLE advertising recovery on ASUS) / Unverified (remaining interruption and host acceptance matrix; coverage deferred)`.
- **artifact_link**: [sanitized ASUS reset and radio recovery record](../../feature/fido2/build/reports/fido2/064/asus-reset-radio-recovery-20261104.txt), [T035/T036](tasks.md)

### Record 054: ASUS Reboot Persistence and Explicit BLE Restart (T035/T036 — Passed, Scoped)
- **requirement**: T035/T036; `FR-BLE-010`, `FR-BLE-060`, `TR-BLE-010`; parent T035 lifecycle boundary
- **scope**: Verify first-run Authenticator selection persists across an ASUS reboot and BLE requires the explicit user start after reboot.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus uncommitted Feature 064 changes.
- **named_procedure**: Reboot ASUS/API 35 while the synthetic profile is initialized and advertising; wait for adb reconnection and unlock the device; inspect Bluetooth and the app before pressing Start; then explicitly start the authenticator and verify the UI and Bluetooth advertiser list.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; synthetic package `com.chimali.shrunk` 0.1.0; Windows 11 x64.
- **expected_observed_result**: The app package and selected Authenticator feature persisted across reboot. Once unlocked, MainActivity launched and showed `Ready to Start`; no app process or Chimali advertiser was running before explicit Start (two unrelated Google Play services advertisers were listed). After Start Authenticator, the process remained alive, `BLUETOOTH_ADVERTISE` was granted, the UI showed `Advertising...` / `Ready to pair with a new host PC`, and Bluetooth manager listed one Chimali set (app_if 13) plus two unrelated Google Play services sets.
- **date**: 2026-11-04
- **limitations**: One reboot and explicit restart cycle; no mid-ceremony reboot, Windows ceremony, BLE/Classic switch, stale-bond recovery, 50-cycle memory, coverage or full acceptance matrix. T035/T036 remain open.
- **status**: `Passed (Authenticator selection persistence and explicit BLE restart after one ASUS reboot) / Unverified (remaining interruption and host acceptance matrix; coverage deferred)`.
- **artifact_link**: [ASUS reboot persistence and restart summary](../../feature/fido2/build/reports/fido2/064/asus-reboot-ble-restart-20261104.txt), [T035/T036](tasks.md)

### Record 055: ASUS Permission-Revocation Sticky-Service Crash Fix (T035/T036 — Passed, Scoped)
- **requirement**: T035/T036; `FR-BLE-010`, `FR-BLE-070`, `FR-HID-010`, `TR-BLE-010`, `TR-BLE-040`; parent T035 lifecycle boundary
- **scope**: Reproduce the Android 14+ connected-device foreground-service crash after Nearby devices revocation, prevent a sticky null-intent restart without its runtime prerequisites, and verify direct Settings grant recovery.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes, including the service preflight guard.
- **named_procedure**: Run focused host tests for `Fido2TransportServiceForegroundPermissionTest`; run FIDO2 AndroidMain/AndroidHostTest KtLint; assemble shrunkDebug and install on ASUS/API 35. Start BLE, deny Nearby devices from Android Settings while advertising, inspect `ApplicationExitInfo`, logcat and Bluetooth manager for 7 seconds; grant Nearby devices in Settings, relaunch MainActivity and explicitly start BLE.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; Microsoft OpenJDK 25.0.5; ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; synthetic package `com.chimali.shrunk`.
- **expected_observed_result**: Focused host test passed 5/5; the full FIDO2 host suite passed 1,131/1,131 with zero failures/errors/skips; FIDO2 AndroidMain and AndroidHostTest KtLint passed; `:app:assembleShrunkDebug` passed and in-place install returned Success. Before the fix, revoking permissions caused `PERMISSION CHANGE`, sticky restart, then `SecurityException` in `startForeground`. With the fix, Android recorded only the permission-change process exit; it retried the service once with a null intent, the guard logged `Bluetooth permissions are unavailable; stopping transport service without a foreground restart`, and `onDestroy` ran. There was no crash or second restart, and Chimali advertising disappeared (ongoing count 3→2). Direct Android Settings Allow then restored the permission; MainActivity showed Ready to Start with no premature advertisement. Explicit Start restored `Advertising...`, `Ready to pair with a new host PC`, app_if 13, and one Chimali advertiser (ongoing count 3).
- **date**: 2026-11-04
- **limitations**: One ASUS/API 35 revocation/regrant cycle; no Windows ceremony, mid-ceremony revoke, API31–34 device matrix, stale-bond recovery, 50-cycle run, coverage or complete acceptance. T035/T036 remain open; coverage remains deferred.
- **status**: `Passed (1,131/1,131 FIDO2 host tests, permission-guard tests, lint/build, ASUS sticky-restart crash prevention and direct Settings recovery) / Unverified (remaining interruption and host acceptance matrix)`.
- **artifact_link**: [ASUS sticky-service permission recovery summary](../../feature/fido2/build/reports/fido2/064/asus-sticky-service-permission-recovery-20261104.txt), [host test XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.service.Fido2TransportServiceForegroundPermissionTest.xml), [T035/T036](tasks.md)

### Record 056: Mi A2 API 29 Pre-Runtime-Permission Startup Smoke Check (T036 — Passed, Scoped)
- **requirement**: T036; `FR-BLE-010`, `FR-BLE-060`, `TR-BLE-010`; parent selected-handle startup boundary
- **scope**: Install the service permission-guard build in place on Android API29 and verify that its pre-Android-12 compatibility branch does not block FIDO BLE startup.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes, including the foreground-service permission guard.
- **named_procedure**: In-place install `app-shrunkDebug.apk` on Mi A2/API29; explicitly launch MainActivity; verify Ready to Start; tap Start Authenticator; inspect UI, service log, Bluetooth-manager GATT registration and process state. Preserve existing app data.
- **environment**: Xiaomi Mi A2 / Android 10/API29, serial `2ac6876`; Windows 11 x64; shrunkDebug package `com.chimali.shrunk`.
- **expected_observed_result**: `adb install -r` returned Success. MainActivity showed Ready to Start before activation. After explicit Start, the UI showed `Advertising...` / `Ready to pair with a new host PC`; logcat reported `FIDO2 transport is active — service running in foreground`, Bluetooth manager listed `com.chimali.shrunk (Registered)`, the process remained alive, and Bluetooth was on. The guard correctly skipped Android 12+ runtime-permission prerequisites for API29.
- **date**: 2026-11-04
- **limitations**: This is a startup compatibility smoke check only; Android 10's Bluetooth manager output did not expose an ongoing advertiser count in the inspected section. It does not establish independent over-the-air advertisement, Windows discovery/pairing, ceremonies, full device suite, coverage or nonregression. Mi API29 cannot satisfy Feature 058's API35+ coverage device prerequisite. T036 remains open.
- **status**: `Passed (API29 in-place install and service/UI startup smoke check) / Unverified (over-the-air host behavior, complete device suite and coverage)`.
- **artifact_link**: [Mi API29 service guard smoke summary](../../feature/fido2/build/reports/fido2/064/mi-api29-service-guard-smoke-20261104.txt), [T036](tasks.md)

### Record 057: ASUS Android Process-Death Subcheck (T035 — Unverified)
- **requirement**: BLE T035; `FR-BLE-060`; parent process-selection recovery boundary
- **scope**: Attempt to independently terminate the active Android package process while BLE advertising was live, without changing the saved authenticator selection or local profile.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes.
- **named_procedure**: Run `adb shell kill -9 <pid>` and, after backgrounding the app, `adb shell am kill com.chimali.shrunk`; inspect process/service/Bluetooth state and restore MainActivity.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; synthetic package `com.chimali.shrunk`; Windows not controlled.
- **expected_observed_result**: Direct process signaling was denied to the shell (`Operation not permitted`). The safe Android package kill preserved the active foreground service and process. Chimali remained registered with Bluetooth, and MainActivity was restored to foreground. No app data was cleared.
- **date**: 2026-11-04
- **limitations**: OS process death was not induced; this result does not establish cold-process selection restoration. No privilege escalation was attempted. Force-stop and reboot checks are recorded separately. BLE T035/T036 remain open; Windows and coverage acceptance remain unverified/deferred.
- **status**: `Unverified (foreground-service process could not be killed by Android shell)`.
- **artifact_link**: [sanitized ASUS process-death subcheck](../../feature/fido2/build/reports/fido2/064/asus-process-death-check-20261104.txt), [T035/T036](tasks.md)

### Record 058: ASUS Android-Managed Process Crash and Sticky BLE Service Recovery (T035 — Passed, Scoped)
- **requirement**: BLE T035; `FR-BLE-060`; parent process-selection recovery boundary
- **scope**: Exercise the production sticky transport service across Android-managed app-process termination while FIDO BLE is active.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes.
- **named_procedure**: With no ceremony active and synthetic app data intact, run documented `adb shell am crash com.chimali.shrunk`; verify `ApplicationExitInfo`, new PID, foreground service, GATT advertiser registration and reopened Activity UI.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; Windows not controlled.
- **expected_observed_result**: Android recorded PID 31713 as `APP CRASH(EXCEPTION)` / `crash`, then restarted Fido2TransportService under PID 5197. The connected-device service was foreground again, Bluetooth manager listed Chimali app_if 13, and MainActivity showed BLE `Advertising...` / `Ready to pair with a new host PC`. Local profile data remained intact.
- **date**: 2026-11-04
- **limitations**: An induced VM crash is not an organic OS low-memory kill. Only one BLE-active restart was exercised; no pending switch, Classic restart, ceremony interruption, Windows host or 50-cycle test. BLE T035/T036 remain open; coverage is deferred.
- **status**: `Passed (one Android-managed process crash and sticky BLE recovery) / Unverified (organic OS kill and remaining acceptance)`.
- **artifact_link**: [sanitized ASUS sticky-service crash recovery](../../feature/fido2/build/reports/fido2/064/asus-sticky-service-crash-recovery-20261104.txt), [T035/T036](tasks.md)

### Record 059: ASUS FIDO BLE Selection Restored After Classic Process Restart (T035 — Partial)
- **requirement**: BLE T035; `FR-BLE-060`; parent selected-transport recovery boundary
- **scope**: Verify a pending BLE selection is restored after a process restart from active Classic, then explicitly restore BLE advertising if the induced-crash alert interrupts service recovery.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes.
- **named_procedure**: With Classic active, save FIDO BLE; confirm pending notice; induce Android `am crash`; inspect immediate state and system prompt; dismiss the prompt, reopen MainActivity, inspect selection/advertising, then Start if stopped.
- **environment**: ASUS AI2302 / Android 15/API35, serial `R7AIB7002901AZ3`; synthetic `com.chimali.shrunk`; Windows not controlled.
- **expected_observed_result**: Android recorded the induced crash. A system `Chimali keeps stopping` prompt appeared, so immediate sticky BLE restart was not established. After dismissing it, MainActivity showed `Ready to Start` with FIDO BLE selected and no Chimali advertiser. Explicit Start restored `Advertising...`, `Ready to pair with a new host PC`, foreground service and app_if 13. Data was preserved.
- **date**: 2026-11-04
- **limitations**: The prompt interrupted confirmation of automatic BLE service restart. This is an induced VM crash, not an organic low-memory kill. No Windows ceremony or in-flight operation was active. BLE T035/T036 remain open; coverage is deferred.
- **status**: `Passed (saved BLE selection and explicit post-crash recovery) / Unverified (immediate sticky BLE recovery and complete T035 acceptance)`.
- **artifact_link**: [sanitized ASUS two-transport process-restart evidence](../../feature/fido2/build/reports/fido2/064/asus-both-transport-process-restart-20261104.txt), [T035/T036](tasks.md)

### Record 060: Local CI Host and Static Checks After Lifecycle Fixes (T036 — Partial)
- **requirement**: BLE T036 host/static portion; parent gate/selected-handle host checks
- **scope**: Re-run ordinary repository CI on current Feature 064 changes without invoking connected tests, coverage, or the deferred trusted-target assessment.
- **revision**: Candidate `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` plus current uncommitted Feature 064 changes.
- **named_procedure**: Run `tools/local-ci.ps1 -NoFix -NoConfigurationCache` with the worktree Gradle user home and temporary `--no-problems-report` override; do not pass `-RunCoverage`. Remove the temporary script.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; repository JDK; connected ASUS AI2302/API35 and Mi A2/API29 were not used by this host-only run.
- **expected_observed_result**: Local CI passed in 489.43 seconds: KtLint, Detekt, release lint, all-module compilation, unit tests and configured KMP Android host tests passed. Up-to-date host results were reused where Gradle determined inputs unchanged; Kotlin daemon marker-file access triggered compiler fallback but no task failure.
- **date**: 2026-11-04
- **limitations**: The focused 1,131-test FIDO2 host result remains in Record 055; this CI invocation does not re-run connected Android suites, the procedure-specific trace/research experiments, coverage floors, or accepted-target comparison. BLE T036 remains partial; coverage remains deferred.
- **status**: `Passed (ordinary local CI and parent-gated host composition scope) / Unverified (configured connected-device suite and coverage/nonregression acceptance)`.
- **artifact_link**: [local CI summary, coverage omitted](../../feature/fido2/build/reports/fido2/064/local-ci-no-coverage-20261104.txt), [T036/T038](tasks.md)

### Record 061: ASUS Windows Bluetooth Discovery (T020/T023 — Unverified)
- **requirement**: BLE T020/T023; `FR-BLE-010`, `FR-BLE-030`, `TR-BLE-010`; parent T022
- **scope**: Check whether Windows 11 discovers the active ASUS Chimali FIDO BLE authenticator using the system Bluetooth add-device scan; do not pair, create a credential, or start a WebAuthn ceremony.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Confirm the Authenticator page reports Advertising and Ready to pair; inspect the connected-device foreground service and Bluetooth runtime grants; in Windows Settings > Bluetooth & devices > Devices, start Add device > Bluetooth, let discovery scan for more than one minute, record listed devices, then cancel.
- **environment**: ASUS AI2302 / Android 15/API 35, serial `R7AIB7002901AZ3`, `com.chimali.shrunk`; Windows 11 x64, Bluetooth enabled.
- **expected_observed_result**: Android showed `Advertising...` / `Ready to pair with a new host PC`; the connected-device foreground service was running and the app's Bluetooth advertise/connect/scan grants were present. Windows' Bluetooth discovery dialog remained empty for over one minute and did not list Chimali Authenticator. The scan was cancelled without pairing or modifying existing pairings.
- **date**: 2026-11-04
- **limitations**: This does not prove whether the advertiser emitted valid FIDO service data or whether Windows filters this device type; no independent BLE scanner, GATT connection, pairing, registration, assertion, cancellation, stale-bond recovery or 50-cycle procedure was run. T020/T023/T035 remain unverified; coverage remains deferred.
- **status**: `Unverified (Android reports advertising, but Windows did not discover the authenticator)`.
- **artifact_link**: [sanitized ASUS/Windows discovery observation](../../feature/fido2/build/reports/fido2/064/asus-windows-ble-discovery-20261104.txt), [T020/T023/T035](tasks.md)

### Record 062: Integrated-Browser WebAuthn Registration Attempt (T020/T023 — Unverified)
- **requirement**: BLE T020/T023; `FR-BLE-030`, `TR-BLE-010`; parent T022
- **scope**: Attempt one real Windows registration for the authorized synthetic account using the integrated browser and active ASUS BLE authenticator; do not claim success without a credential response.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: In the integrated browser, submit Register at WebAuthn.io for `codex-synthetic-064-20261103`; inspect the browser page and Windows app inventory for the Windows Security key choice; stop after the operation resolves.
- **environment**: Windows 11 x64; integrated in-app browser; ASUS AI2302 / Android 15/API35, serial `R7AIB7002901AZ3`, `com.chimali.shrunk` showing BLE advertising.
- **expected_observed_result**: WebAuthn.io returned `The operation either timed out or was not allowed`. No Windows security-key chooser appeared in the observed UI, no assertion/registration result was returned, and no passkey was shown as created. A preceding Windows Bluetooth add-device scan was also empty (Record 061).
- **date**: 2026-11-04
- **limitations**: This does not establish whether the failure is caused by the in-app browser/Windows provider path or BLE interoperability; no credential response or authenticated-pairing proof exists. T020/T023 remain unverified; coverage remains deferred.
- **status**: `Unverified (registration request timed out or was disallowed before an authenticator result)`.
- **artifact_link**: [sanitized WebAuthn timeout observation](../../feature/fido2/build/reports/fido2/064/asus-webauthn-registration-timeout-20261104.txt), [T020/T023/T035](tasks.md)

### Record 063: Nordic Legacy Advertiser API-Level Device Check (T036 — Passed, Scoped)
- **requirement**: BLE T036 Android device-test portion; `FR-BLE-010`, `TR-BLE-010`; parent selected-handle boundary
- **scope**: Verify the selected Nordic legacy advertiser can start on both connected Android versions; this does not certify Windows discovery or an authenticated ceremony.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Run only`NordicBleLegacyAdvertisingDeviceTest.connectableLegacyAdvertisingStartsOnApi28Plus` via `:feature:fido2:connectedAndroidDeviceTest` with the instrumentation class filter; inspect each connected-device XML result and verify the ASUS app advertiser remains registered afterward.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; ASUS AI2302/API35 serial `R7AIB7002901AZ3`; Xiaomi Mi A2/API29 serial `2ac6876`.
- **expected_observed_result**: The focused test passed 1/1 on ASUS/API35 and 1/1 on Mi/API29, with zero failures, errors or skips. The test started a connectable legacy advertiser and stopped its own instance; afterward the ASUS app still had its foreground service, GATT server registration and advertising entry.
- **date**: 2026-11-04
- **limitations**: This only confirms Nordic advertiser API startup on these two devices. It does not verify Windows discovery, exact on-air FIDO service data, GATT host connection, authenticated pairing, registration/assertion, full configured device suite or trusted coverage. BLE T020/T023/T035/T036 remain open; coverage is deferred.
- **status**: `Passed (focused advertiser-start check on API29 and API35) / Unverified (host discovery and complete BLE acceptance)`.
- **artifact_link**: [focused device-test summary](../../feature/fido2/build/reports/fido2/064/nordic-advertiser-device-test-20261104.txt), [ASUS result XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [Mi result XML](../../feature/fido2/build/reports/fido2/064/mi-nordic-legacy-advertising-device-test-20261104.xml)

### Record 064: Cross-Device FIDO Service Advertisement Discovery (T036 — Passed, Scoped)
- **requirement**: BLE T036 Android device-test/discovery portion; `FR-BLE-010`, `TR-BLE-010`; parent T022
- **scope**: Verify an active Chimali advertiser on each connected phone is discoverable by the other phone's Android BLE scanner through the standard FIDO service UUID.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Keep both Chimali Authenticator instances advertising. Run `FidoBleAdvertiserDiscoveryDeviceTest.activeAuthenticatorAdvertisesTheFidoServiceUuid` on both devices. Grant scan permissions to the instrumentation package; on Android 10, also grant the test-only background location prerequisite. Ignore results advertising the local adapter name so a device cannot satisfy its own check.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; ASUS AI2302/API35 serial `R7AIB7002901AZ3`; Xiaomi Mi A2/API29 serial `2ac6876`; both `com.chimali.shrunk` apps showed Advertising.
- **expected_observed_result**: The unfiltered scanner on each phone received a result containing FIDO service UUID `0000FFFD-0000-1000-8000-00805F9B34FB` from a distinct remote device name. The focused test passed 1/1 on each phone with zero failures, errors or skips.
- **date**: 2026-11-04
- **limitations**: This confirms advertisement visibility between these two Android devices only. Windows Settings still did not list the ASUS, and the integrated-browser registration request timed out; no Windows GATT connection, authenticated pairing, credential response, assertion, switching, 50-cycle stress or complete BLE acceptance was completed. BLE T020/T023/T035/T036 remain open; coverage is deferred.
- **status**: `Passed (cross-device FIDO service discovery on ASUS/API35 and Mi/API29) / Unverified (Windows discovery and ceremony)`.
- **artifact_link**: [cross-device discovery test summary](../../feature/fido2/build/reports/fido2/064/fido-advertiser-peer-discovery-20261104.txt), [ASUS result XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [Mi result XML](../../feature/fido2/build/reports/fido2/064/fido2-nonperf-mi-20261104.xml)

### Record 065: Configured FIDO BLE Host and Android Device Suite (T036 — Partial)
- **requirement**: BLE T036 and parent gate/selected-handle integration boundary; `FR-BLE-010–080`, `TR-BLE-010–050`, parent `FR-BT-SEL-030–050`
- **scope**: Execute the configured FIDO2 host and device suites together with parent settings unit and connected instrumentation on the currently connected ASUS and Mi; omit owner-deferred coverage.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Run `:core:common:testAndroidHostTest :feature:fido2:testAndroidHostTest :feature:settings:testDebugUnitTest :feature:settings:connectedDebugAndroidTest :feature:fido2:connectedAndroidDeviceTest --no-configuration-cache --no-problems-report --max-workers=1`, using the worktree-local Gradle user home. Do not invoke `-RunCoverage`.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; ASUS AI2302/API35 serial `R7AIB7002901AZ3`; Xiaomi Mi A2/API29 serial `2ac6876`.
- **expected_observed_result**: Common/FIDO2 host tests and settings unit tests were up-to-date and successful. Settings connected instrumentation passed 12/12 on each phone. FIDO2 connected suite completed with 48/53 passed and 5 failed on ASUS, and 45/50 passed and 5 failed on Mi. The five failures on each device were `EmpiricalCalibrationDeviceTest`, `FilteredMeasurementPilotDeviceTest`, `LowerOverheadControlExplorationDeviceTest`, `PerformanceTraceDeviceTest` and `TraceOverheadExplorationDeviceTest`; their controlled Perfetto configuration and/or frozen run arguments were not supplied. The Nordic advertiser startup and cross-device service-discovery cases passed on both phones. Gradle exited nonzero; research cases are not passes.
- **date**: 2026-11-04
- **limitations**: Full FIDO2 connected-suite validation remains incomplete. No Windows GATT connection or WebAuthn ceremony passed. Coverage and trusted candidate-versus-target comparison were omitted by owner direction and remain unverified/deferred. BLE T036, parent T039 and parent T041 remain open.
- **status**: `Partial (host and parent settings checks passed; BLE startup/discovery passed on both phones) / Failed (full FIDO2 connected suite has five unsatisfied research procedures per device) / Deferred (coverage)`.
- **artifact_link**: [ASUS connected-suite XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [Mi connected-suite XML](../../feature/fido2/build/reports/fido2/064/mi-connected-suite-post-retry-fix-20261104.xml), [ASUS settings XML](../../feature/settings/build/outputs/androidTest-results/connected/debug/TEST-ASUS_AI2302%20-%2015.xml), [Mi settings XML](../../feature/settings/build/outputs/androidTest-results/connected/debug/TEST-Mi%20A2%20-%2010.xml), [BLE T036](tasks.md)

### Record 066: Current Worktree Local CI Without Coverage (T036/T038 — Partial)
- **requirement**: BLE T036 configured host portion and T038 local verification scope; parent T039 local CI portion
- **scope**: Re-run ordinary local CI against current source without invoking coverage; coverage remains deferred by owner direction.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Run `tools/local-ci.ps1 -NoFix -NoConfigurationCache` with `.gradle/codex-user-home` and a temporary script copy adding Gradle's `--no-problems-report`; remove the temporary copy afterward. Do not pass `-RunCoverage`.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; ASUS AI2302/API35 and Xiaomi Mi A2/API29 connected.
- **expected_observed_result**: Local CI passed in 78 seconds: KtLint, Detekt, release lint, comprehensive compilation, unit tests and configured KMP Android host tests succeeded. Most tasks were up-to-date; the FIDO2 Detekt task executed. Coverage was omitted.
- **date**: 2026-11-04
- **limitations**: The latest complete connected FIDO2 suite still has five unsatisfied procedure-specific cases per device (Record 065). The local-CI run does not satisfy Android device acceptance, Windows ceremonies, coverage completeness/floors or trusted accepted-target raw-count comparison. BLE T036/T038 and parent T039 remain open; coverage is deferred.
- **status**: `Passed (ordinary local CI without coverage) / Partial (configured connected suites) / Unverified (trusted coverage comparison) / Deferred (coverage)`.
- **artifact_link**: [current local CI summary](../../feature/fido2/build/reports/fido2/064/current-local-ci-no-coverage-20261104.txt), [BLE T036/T038](tasks.md), [current connected-suite outcomes](#record-065-configured-fido-ble-host-and-android-device-suite-t036--partial)

### Record 067: CTAP Device Information Service and Ordered Registration (T016 — Passed, Scoped)
- **requirement**: BLE T016; `FR-BLE-010`, `TR-BLE-010`
- **scope**: Add the required standard Device Information Service alongside FIDO service 0xFFFD, expose generic non-identifying manufacturer/model/firmware values, allow those reads before bonding for the active host, and ensure GATT startup awaits both service-added callbacks before succeeding.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Run `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.transport.FidoBleGattContractTest` and `:feature:fido2:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.chimali.fido2.data.transport.FidoBleSecurityDeviceTest` with worktree-local Gradle home and `--no-configuration-cache --no-problems-report --max-workers=1`; the on-device host-start test opens the real Android GATT server and awaits service-added callbacks for both registrations.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; ASUS AI2302/API35 serial `R7AIB7002901AZ3`; Xiaomi Mi A2/API29 serial `2ac6876`.
- **expected_observed_result**: Focused FIDO GATT contract suite passed 12/12. Android device tests passed 4/4 on both ASUS and Mi: they inspect Device Information primary 0x180A service shape and three read-only characteristics (`Chimali`, `Chimali Authenticator`, `1.0`), and verify the actual platform GATT host reports success only after both service-added callbacks. Host contract tests cover active unbonded-host reads with valid and invalid offsets. Startup matches callbacks by service UUID and fails/cleans up if either registration is rejected, fails or times out. Android's Bluetooth stack continues to own mandatory GAP; the app does not duplicate GAP registration.
- **date**: 2026-11-04
- **limitations**: The test proves both Android service-added callbacks succeeded but does not inspect an active remote GATT database or Windows discovery/pairing. A complete WebAuthn ceremony remains unverified. T020/T023/T035/T036 remain open; coverage remains deferred.
- **status**: `Passed (host contract and actual Android service registration on API29/API35) / Unverified (remote GATT database and Windows interoperability)`.
- **artifact_link**: [FIDO GATT implementation](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/FidoBleGattServer.kt), [GATT host](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/AndroidFidoBleGattServerHost.kt), [host test results](../../feature/fido2/build/reports/tests/testAndroidHostTest/index.html), [ASUS device result XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml), [Mi device result XML](../../feature/fido2/build/reports/fido2/064/fido2-nonperf-mi-20261104.xml), [T016](tasks.md)

### Record 068: Current Worktree Local CI Without Coverage (T036/T038 — Partial)
- **requirement**: BLE T036 configured host portion; parent T039 local CI portion; `FR-BLE-010–080`, `TR-BLE-010–050`
- **scope**: Re-run ordinary local CI against the current uncommitted tree after Device Information Service changes; omit coverage as directed by the owner.
- **revision**: Base `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` on branch `064-bluetooth-transport-selection`, plus current uncommitted Feature 064 changes.
- **named_procedure**: Run `tools/local-ci.ps1 -NoFix -NoConfigurationCache` with `.gradle/codex-user-home`; a temporary copy adds `--no-problems-report` and is removed after the run. Do not pass `-RunCoverage`.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; ASUS AI2302/API35 and Xiaomi Mi A2/API29 connected.
- **expected_observed_result**: Local CI passed in 167.2 seconds: KtLint, Detekt, release lint, comprehensive compilation, unit tests and configured KMP Android host tests succeeded. Kotlin daemon marker-file AccessDenied warnings triggered compiler fallback without task failures.
- **date**: 2026-11-04
- **limitations**: The full configured connected FIDO2 suite still has five unsatisfied procedure-specific research cases per device (Record 065). This does not complete T036, physical Windows acceptance or trusted coverage/nonregression. Coverage remains deferred by owner direction.
- **status**: `Passed (ordinary local CI without coverage) / Partial (configured connected suites) / Unverified (Windows acceptance and trusted comparison) / Deferred (coverage)`.
- **artifact_link**: [latest local CI summary](../../feature/fido2/build/reports/fido2/064/current-local-ci-no-coverage-20261104.txt), [T036/T038](tasks.md), [connected-suite outcomes](#record-065-configured-fido-ble-host-and-android-device-suite-t036--partial)

### Record 069: Current Configured Host and Connected Device Suites (T036 — Partial)
- **requirement**: BLE T036; parent T039/T041 configured-suite scope; `FR-BLE-010–080`, `TR-BLE-010–050`
- **scope**: Re-run current common/FIDO2 host tests, settings unit and connected tests, and full FIDO2 Android device suites on ASUS/API35 and Mi/API29; omit owner-deferred coverage.
- **revision**: Base `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` on branch `064-bluetooth-transport-selection`, plus uncommitted Feature 064 changes.
- **named_procedure**: Run `:core:common:testAndroidHostTest :feature:fido2:testAndroidHostTest :feature:settings:testDebugUnitTest :feature:settings:connectedDebugAndroidTest :feature:fido2:connectedAndroidDeviceTest --no-configuration-cache --no-problems-report --max-workers=1` with `.gradle/codex-user-home`; then run the non-research suite with `-Pandroid.testInstrumentationRunnerArguments.notPackage=com.chimali.fido2.util.performance`; do not run coverage.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; ASUS AI2302/API35 serial `R7AIB7002901AZ3`; Xiaomi Mi A2/API29 serial `2ac6876`.
- **expected_observed_result**: Common/FIDO2 host tests and settings unit tests succeeded; parent `ProcessTransportSelectionTest` passed 15/15. Settings instrumentation passed 12/12 on each phone. ASUS FIDO2 instrumentation ran 55 tests: 50 passed, 5 failed, 0 errors/skips; Mi ran 52: 47 passed, 5 failed, 0 errors/skips. The original run showed five research-class failures at trace preconditions. On the follow-up, comma-separated`notClass` excluded only `EmpiricalCalibrationDeviceTest`; four remaining research classes ran and failed before measurement because Perfetto was not active with the test package in `atrace_apps`. ASUS: 54 tests, 50 passed and 4 failed. Mi: 51 tests, 47 passed and 4 failed. Both had zero errors/skips, and all other executed tests in the follow-up passed. No measurement assertions or frozen-procedure arguments were reached. A corrected follow-up using`notPackage=com.chimali.fido2.util.performance` excluded exactly the five controlled tracing classes and passed 50/50 tests on ASUS and 47/47 on Mi, with zero failures/errors/skips. BLE `FidoBleSecurityDeviceTest` passed 4/4 per phone, including actual registration of both FIDO and Device Information GATT services; Nordic advertiser and cross-device discovery tests passed on both devices. The original full-suite Gradle invocation exited nonzero; the corrected package-filtered non-research follow-up succeeded.
- **date**: 2026-11-04
- **limitations**: Research procedures are not passes and no measurement arguments were supplied. The original full-suite XML was overwritten by later connected-test runs. The failed class-filter attempt and corrected non-research result XML are preserved separately under `feature/fido2/build/reports/fido2/064`. Windows GATT discovery/ceremonies, transport-switch ceremonies, stale-bond and mid-ceremony recovery, full T036 acceptance, trusted coverage/nonregression and hosted integration remain open. Coverage remains deferred by owner direction.
- **status**: `Passed (host/settings checks and scoped BLE device tests) / Passed (non-research device suite ASUS 50/50 and Mi 47/47) / Partial (original full run ASUS 50/55 and Mi 47/52; five controlled trace procedures remain unverified) / Unverified (Windows and full acceptance) / Deferred (coverage)`.
- **artifact_link**: [ASUS non-research result XML](../../feature/fido2/build/reports/fido2/064/fido2-nonperf-asus-20261104.xml), [Mi non-research result XML](../../feature/fido2/build/reports/fido2/064/fido2-nonperf-mi-20261104.xml), [class-filter attempt ASUS XML](../../feature/fido2/build/reports/fido2/064/fido2-notclass-followup-asus-20261104.xml), [class-filter attempt Mi XML](../../feature/fido2/build/reports/fido2/064/fido2-notclass-followup-mi-20261104.xml), [ASUS settings XML](../../feature/settings/build/outputs/androidTest-results/connected/debug/TEST-ASUS_AI2302%20-%2015.xml), [Mi settings XML](../../feature/settings/build/outputs/androidTest-results/connected/debug/TEST-Mi%20A2%20-%2010.xml), [parent selection gate test XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.transport.ProcessTransportSelectionTest.xml), [BLE T036](tasks.md), [parent T039/T041](../../064-bluetooth-transport-selection/tasks.md)

### Record 070: Current ASUS and Mi BLE Runtime Snapshot (T035 — Scoped)
- **requirement**: BLE T035 lifecycle evidence; `FR-BLE-010`, parent `FR-BT-SEL-030`
- **scope**: Read-only snapshot of the currently connected ASUS/API35 and Mi/API29 devices to confirm whether the Chimali transport foreground service and Android Bluetooth advertiser registration are present.
- **revision**: Uncommitted Feature 064 worktree on branch `064-bluetooth-transport-selection`; source changes were not installed or modified as part of this snapshot.
- **named_procedure**: Query each device's model/API with `adb shell getprop`; inspect `dumpsys activity services com.chimali.shrunk` and filter `dumpsys bluetooth_manager` for the app advertiser registration. No app state or saved transport choice was changed.
- **environment**: Windows 11 x64 host; ASUS AI2302 serial `R7AIB7002901AZ3`, API35; Xiaomi Mi A2 serial `2ac6876`, API29.
- **expected_observed_result**: On both devices, `Fido2TransportService` was foreground and Android's Bluetooth manager listed `com.chimali.shrunk` as a registered advertiser. ASUS reported advertiser `app_if: 13`; Mi reported the registered package entry. This is a current runtime snapshot, not a connection or ceremony test.
- **date**: 2026-11-04
- **limitations**: Does not prove advertised payload contents, remote GATT connection, pairing/security, WebAuthn registration/assertion, selection persistence, lifecycle recovery, or 50-cycle memory behavior. Parent/BLE T035 and Windows acceptance remain open; coverage remains deferred.
- **status**: `Passed (current foreground-service and advertiser-registration snapshot on API29/API35) / Unverified (full lifecycle and host acceptance)`.
- **artifact_link**: [filtered Android runtime snapshot](../../feature/fido2/build/reports/fido2/064/current-android-advertising-state-20261104.txt), [BLE T035](tasks.md), [parent T035](../../064-bluetooth-transport-selection/tasks.md)

### Record 071: Runtime Advertiser Error Retry Action (T035 — Implemented, Host-Verified)
- **requirement**: BLE T035 recovery; `FR-BLE-070`, `TR-BLE-040`; parent `FR-BT-SEL-050`
- **scope**: Correct main-screen recovery routing when the selected BLE transport fails after startup and the selection controller still reports availability `Ready`.
- **revision**: Base `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` on branch `064-bluetooth-transport-selection`, plus current uncommitted changes.
- **named_procedure**: Run the focused `Fido2HomeViewModelTest`, the full `:feature:fido2:testAndroidHostTest` suite, and `:feature:fido2:ktlintCheck`, each with `--no-configuration-cache --no-problems-report --max-workers=1`.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; ASUS AI2302/API35 and Xiaomi Mi A2/API29 connected; no device state changed.
- **expected_observed_result**: `transportToggleAction` now maps every connection `Error` to Retry, including runtime BLE advertiser/link failures while transport availability remains `Ready`. The previous branch chose Start in that state, but the service ignores Start while `startRequested` is still true. The focused ViewModel suite passed 2/2, the full FIDO2 Android host suite passed 1,133/1,133 with zero failures/errors/skips, and feature KtLint passed.
- **date**: 2026-11-04
- **limitations**: This host test verifies button action routing only. Physical adapter-off/on with Retry recovery, a Windows connection, authentication, 50-cycle stress and the remaining T035 matrix are not established. Coverage remains deferred.
- **status**: `Passed (runtime-error Retry routing, FIDO2 host tests 1,133/1,133, focused ViewModel tests 2/2, feature KtLint) / Unverified (physical interruption/recovery and complete BLE acceptance)`.
- **artifact_link**: [focused host test XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.presentation.viewmodel.Fido2HomeViewModelTest.xml), [parent T035/T041](../../064-bluetooth-transport-selection/tasks.md), [parent evidence](../064-bluetooth-transport-selection/implementation-evidence.md#record-052-retry-action-after-runtime-transport-error-t035--implemented-host-verified)

### Record 072: Runtime Advertiser Error Retry UI (T035 — Implemented, Host/Device Verified)
- **requirement**: BLE T035 recovery; `FR-BLE-070`, `TR-BLE-040`; parent `FR-BT-SEL-050`
- **scope**: Label runtime transport-error recovery as Retry in the authenticator's main button and ensure the error button remains actionable.
- **revision**: Base `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` on branch `064-bluetooth-transport-selection`, plus current uncommitted Feature 064 changes.
- **named_procedure**: Run the full FIDO2 Android host suite, the focused `AuthenticatorDashboardComponentsTest` connected on both phones, and feature KtLint with `--no-configuration-cache --no-problems-report --max-workers=1`.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; ASUS AI2302/API35 serial `R7AIB7002901AZ3`; Xiaomi Mi A2/API29 serial `2ac6876`.
- **expected_observed_result**: Error state displays “Retry Authenticator” and a refresh icon; click invokes the supplied toggle callback. The full host suite passed 1,133/1,133; the dashboard device class passed 6/6 on each phone, and the final post-constant rerun again had zero failures, errors or skips; KtLint passed. Ordinary local CI after this update passed KtLint, Detekt, release lint, compile-all and unit/host checks; coverage was not run.
- **date**: 2026-11-04
- **limitations**: Device UI results prove label/callback only, not physical BLE retry after radio interruption or remote reconnection. Windows ceremonies, stale-bond recovery, 50-cycle memory and full T035/T041 remain open. Coverage remains deferred.
- **status**: `Passed (host and device Retry presentation/action checks; KtLint) / Unverified (physical transport interruption/reconnect and complete BLE acceptance)`.
- **artifact_link**: [ASUS result XML](../../feature/fido2/build/reports/fido2/064/authenticator-dashboard-retry-asus-final-20261104.xml), [Mi result XML](../../feature/fido2/build/reports/fido2/064/authenticator-dashboard-retry-mi-final-20261104.xml), [full FIDO2 host results](../../feature/fido2/build/test-results/testAndroidHostTest/index.html), [Retry UI](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/Fido2HomeScreen.kt), [parent T035](../../064-bluetooth-transport-selection/tasks.md), [parent Record 053](../064-bluetooth-transport-selection/implementation-evidence.md#record-053-retry-label-and-action-on-transport-error-t035--implemented-hostdevice-verified)

### Record 073: Mi A2 BLE Force-Stop and Explicit Restart (T035 — Passed, Scoped)
- **requirement**: BLE T035 lifecycle; `FR-BLE-010`, `FR-BLE-060`; parent T035
- **scope**: Verify saved BLE selection and explicit-start gating through an Android package force-stop/relaunch on Mi/API29.
- **revision**: Base `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` on branch `064-bluetooth-transport-selection`, plus current uncommitted Feature 064 changes.
- **named_procedure**: Record current MainActivity, foreground-service and Bluetooth advertiser states; run `adb -s 2ac6876 shell am force-stop com.chimali.shrunk`; explicitly launch `com.chimali.shrunk/com.chimali.MainActivity`; confirm Ready to Start and no service before tapping Start; tap the displayed Start Authenticator control; confirm the advertising UI, foreground service and registered advertiser return.
- **environment**: Windows 11 x64; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; synthetic package `com.chimali.shrunk`.
- **expected_observed_result**: BLE advertising was active before force-stop. After relaunch, the saved FIDO BLE mode was still selected but the screen showed Ready to Start and the transport service was absent. Explicit Start restored Advertising, the foreground service and `com.chimali.shrunk (Registered)` in Bluetooth manager. No application data was cleared.
- **date**: 2026-11-04
- **limitations**: Scoped to one Mi BLE force-stop/restart. It does not establish Windows interaction, Classic selection, OS process-death/reboot recovery, in-flight behavior, stale-bond recovery, 50-cycle memory, or full BLE T035/T036 acceptance. Coverage is deferred.
- **status**: `Passed (API29 BLE force-stop/explicit-restart lifecycle subcheck) / Unverified (remaining BLE lifecycle and host acceptance)`.
- **artifact_link**: [procedure and observations](../../feature/fido2/build/reports/fido2/064/mi-force-stop-restart-20261104.txt), [initial advertising screen](../../feature/fido2/build/reports/fido2/064/mi-current-screen-20261104.png), [stopped after relaunch](../../feature/fido2/build/reports/fido2/064/mi-force-stop-before-start-20261104.png), [advertising after explicit Start](../../feature/fido2/build/reports/fido2/064/mi-force-stop-after-start-20261104.png), [parent Record 054](../064-bluetooth-transport-selection/implementation-evidence.md#record-054-mi-a2-ble-force-stop-and-explicit-restart-t035--passed-scoped)

### Record 074: Mi A2 Classic HID Startup Failure and BLE Recovery (T035 — Partial)
- **requirement**: BLE T035 lifecycle/recovery; parent T027/T035; `FR-BLE-060`, `FR-BLE-070`
- **scope**: Record BLE-to-Classic selection behavior on Mi/API29 and verify recovery to the BLE default path after Classic startup fails.
- **revision**: Base `HEAD` `6a60432363fb1d5f8dc6787230f6b098cd11c82c` on branch `064-bluetooth-transport-selection`, plus current uncommitted Feature 064 changes.
- **named_procedure**: Start from active BLE advertising; save Classic, restart the process and start the authenticator; capture Android HID Device profile bind failure; save BLE, restart the process, explicitly start it and confirm advertising.
- **environment**: Windows 11 x64; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; package `com.chimali.shrunk`.
- **expected_observed_result**: Android reported `Could not bind to Bluetooth Service with Intent { act=android.bluetooth.IBluetoothHidDevice }`, followed by a 5,000 ms startup timeout. The app did not fall back to BLE. After saving BLE and restarting, explicit Start restored `Advertising...` and the BLE footer.
- **date**: 2026-11-04
- **limitations**: This scoped check does not diagnose whether Classic HID is unavailable on this ROM or the profile service failed transiently. It does not prove Windows interoperability, ceremonies, Classic availability on other hardware, BLE interruption/stale-bond/cancellation handling, 50 cycles, or complete BLE T035/T036 acceptance. Coverage remains deferred.
- **status**: `Failed (Classic HID startup/profile binding on Mi/API29) / Passed (BLE selection recovery and advertising restore) / Unverified (Windows and remaining BLE acceptance)`.
- **artifact_link**: [procedure, observations and screenshots index](../../feature/fido2/build/reports/fido2/064/mi-classic-hid-failure-and-ble-recovery-20261104.txt), [Classic failure screen](../../feature/fido2/build/reports/fido2/064/mi-classic-hid-profile-error-20261104.png), [BLE restore screen](../../feature/fido2/build/reports/fido2/064/mi-ble-restored-after-classic-20261104.png), [BLE T035](tasks.md), [parent Record 055](../064-bluetooth-transport-selection/implementation-evidence.md#record-055-mi-a2-classic-hid-startup-failure-and-ble-recovery-t027t035--partial)

### Record 075: Mi A2 Radio-Off Failure and Retry Recovery (T035 — Passed, Scoped)
- **requirement**: BLE T035; `FR-BLE-060`, `FR-BLE-070`; parent T035 lifecycle/recovery
- **scope**: Verify system Bluetooth-off events terminate the active Nordic advertiser, surface an error, and allow deliberate recovery after radio restoration.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Install updated `shrunkDebug`; start BLE advertising; toggle Bluetooth off in Android Settings; confirm no advertiser is registered and inspect the app error; turn Bluetooth back on; tap Retry Authenticator and verify advertising registration returns.
- **environment**: Windows 11 x64; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; package `com.chimali.shrunk`.
- **expected_observed_result**: Radio off changed the app from Advertising to `Error Occurred / BLE advertising stopped unexpectedly` with Retry Authenticator; Bluetooth manager had no Chimali advertiser registration. Radio restoration alone left the app in Error. Explicit Retry returned the UI to Advertising and registered `com.chimali.shrunk` with Android Bluetooth.
- **date**: 2026-11-04
- **limitations**: One radio-off/on cycle on Mi/API29. Remote Windows GATT, interrupted FIDO ceremony, permission revocation, stale bond, process death/reboot, ASUS repeat, and 50-cycle behavior remain unverified; BLE T035/T036 are not complete. Coverage remains deferred.
- **status**: `Passed (Mi/API29 radio-off error reporting and explicit Retry recovery) / Unverified (remaining BLE lifecycle/host matrix)`.
- **artifact_link**: [procedure and observations](../../feature/fido2/build/reports/fido2/064/mi-ble-radio-off-recovery-20261104.txt), [Advertising baseline](../../feature/fido2/build/reports/fido2/064/mi-ble-radio-baseline-20261104.png), [Bluetooth off](../../feature/fido2/build/reports/fido2/064/mi-bluetooth-off-after-fix.png), [error UI](../../feature/fido2/build/reports/fido2/064/mi-app-bluetooth-off-after-fix.png), [advertising after Retry](../../feature/fido2/build/reports/fido2/064/mi-app-explicit-retry-after-fix.png), [BLE T035](tasks.md), [parent Record 056](../064-bluetooth-transport-selection/implementation-evidence.md#record-056-mi-a2-ble-radio-off-failure-and-retry-recovery-t035--passed-scoped)

### Record 076: Current Ordinary Local CI After Radio-Off Recovery (T036 — Passed, Scoped)
- **requirement**: BLE T036 configured host and static/compile/unit scope; parent T039 local validation; `FR-BLE-010–080`, `TR-BLE-010–050`
- **scope**: Validate the current Feature 064 tree after the Android advertiser radio-off recovery change; omit coverage as directed by the owner.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Run `tools/local-ci.ps1 -NoFix -NoConfigurationCache` with worktree-local Gradle user home. The direct script first stopped at a Gradle problems-report `AccessDeniedException`; rerun a temporary copy with `--no-problems-report`, remove the copy after completion, and omit `-RunCoverage`.
- **environment**: Windows 11 x64; Gradle 9.6.0; Android SDK 37; Xiaomi Mi A2/API29 connected; ASUS not connected for this run.
- **expected_observed_result**: KtLint, Detekt, Release lint, comprehensive compilation, unit tests, and configured KMP Android host tests passed in 171.66 seconds. Kotlin daemon marker-file `AccessDeniedException` warnings used compiler fallback without failing compilation. Coverage was not run.
- **date**: 2026-11-04
- **limitations**: This run does not execute configured connected Android suites, actual Windows ceremonies, trusted current-target comparison or hosted integration. BLE T038 remains deferred by owner direction; T036 physical and complete acceptance remain open.
- **status**: `Passed (ordinary local CI without coverage) / Deferred (coverage) / Unverified (connected physical and hosted acceptance)`.
- **artifact_link**: [BLE T036/T038](tasks.md), [parent T039 evidence](../064-bluetooth-transport-selection/implementation-evidence.md#record-057-current-ordinary-local-ci-after-radio-off-recovery-t039--passed-scoped), [Feature 058 plan](../058-enforce-coverage-targets/plan.md)

### Record 077: Mi A2 Nordic Legacy Advertising Device Test (T036 — Passed, Scoped)
- **requirement**: BLE T015/T036; `FR-BLE-010`, `TR-BLE-010`
- **scope**: Re-run the API28+ device test for a real connectable legacy Nordic BLE advertisement on the current integrated build.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Run `:feature:fido2:connectedAndroidDeviceTest` with class `com.chimali.fido2.data.transport.NordicBleLegacyAdvertisingDeviceTest`, `--no-configuration-cache --no-problems-report --max-workers=1`; inspect the API29 connected-device XML.
- **environment**: Windows 11 x64; Gradle 9.6.0; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; ASUS not connected for this run.
- **expected_observed_result**: `connectableLegacyAdvertisingStartsOnApi28Plus` passed 1/1 with zero failures/errors/skips in 0.18 seconds, confirming the current app build starts a real connectable legacy advertiser on Mi/API29.
- **date**: 2026-11-04
- **limitations**: This does not establish radio interruption, authenticated remote pairing, Windows registration/assertion, all connected device tests, or full BLE T036 acceptance. Full-suite research failures and Windows ceremony gaps remain recorded; BLE T038 coverage is deferred by owner direction.
- **status**: `Passed (Mi/API29 real legacy-advertising startup 1/1) / Unverified (remaining device/Windows acceptance) / Deferred (coverage)`.
- **artifact_link**: [Mi connected-device XML](../../feature/fido2/build/reports/fido2/064/mi-nordic-legacy-advertising-device-test-20261104.xml), [BLE T036/T038](tasks.md), [parent Record 058](../064-bluetooth-transport-selection/implementation-evidence.md#record-058-mi-a2-nordic-legacy-advertising-device-test-t039--passed-scoped)

### Record 078: Windows Save-Passkey Dialog Disappeared (T020/T023 — Unverified)
- **requirement**: BLE T020 registration and T023 assertion; `FR-BLE-020`, `FR-BLE-030`
- **scope**: Record the latest user-observed Windows WebAuthn registration prompt outcome without inferring credential creation.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Attempt synthetic WebAuthn registration in the integrated browser and follow the Windows security-key path previously selected by the user; inspect whether the browser reports registration success and the synthetic credential is available.
- **environment**: Windows 11 host; integrated browser/Edge state could not be verified by Computer Use during this continuation. Synthetic account `codex-synthetic-064-20261103`.
- **expected_observed_result**: The user reported that the Windows dialog labeled “Guardar clave de paso” disappeared and suspected it may have timed out. No browser success message or credential result was observed. Treat the ceremony as incomplete; cause of dialog dismissal is unknown.
- **date**: 2026-11-04
- **limitations**: This is not a registration pass or a confirmed Windows timeout diagnosis. No successful registration, GetAssertion, paired-host discovery, or transport interoperability conclusion is established. BLE T020/T023 remain open.
- **status**: `Unverified (dialog disappeared; no browser success or credential result)`.
- **artifact_link**: [BLE T020/T023](tasks.md), [parent evidence](../064-bluetooth-transport-selection/implementation-evidence.md#record-059-windows-save-passkey-dialog-disappeared-t031t041--unverified)

### Record 079: Integrated Browser WebAuthn Focus Error (T020/T023 — Unverified)
- **requirement**: BLE T020 registration and T023 assertion; `FR-BLE-020`, `FR-BLE-030`
- **scope**: Retry synthetic registration in the requested integrated browser and distinguish page-focus failure from a Windows security-key or transport result.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Open WebAuthn.io in the ChatGPT in-app browser, submit the existing synthetic account for registration, and inspect both page state and system prompt state.
- **environment**: Windows 11 host; ChatGPT in-app browser tab on `https://webauthn.io/`; synthetic account `codex-synthetic-064-20261103`.
- **expected_observed_result**: The browser page returned “The operation is not allowed at this time because the page does not have focus.” Bringing the in-app browser forward and retrying did not launch a Windows prompt. Selecting the ChatGPT window to bring the app forward timed out in Computer Use. No registration or authenticator operation occurred.
- **date**: 2026-11-04
- **limitations**: This is a browser focus failure, not evidence of Windows BLE discovery, pairing or Chimali transport failure. BLE registration/GetAssertion acceptance remains open; user interaction to focus the integrated browser is needed before another attempt.
- **status**: `Unverified (browser page lacked focus; no Windows prompt or credential result)`.
- **artifact_link**: [BLE T020/T023](tasks.md), [parent focus-error evidence](../064-bluetooth-transport-selection/implementation-evidence.md#record-060-integrated-browser-webauthn-focus-error-t031t041--unverified)

### Record 080: Integrated Browser WebAuthn Operation Did Not Complete (T020/T023 — Unverified)
- **requirement**: BLE T020 registration and T023 assertion; `FR-BLE-020`, `FR-BLE-030`
- **scope**: Re-attempt the authorized synthetic registration after the user focused the integrated browser page; inspect for Windows prompt and browser success.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: In the focused ChatGPT in-app WebAuthn.io tab, submit Register for the existing synthetic account and observe the browser, Windows prompt and result.
- **environment**: Windows 11 host; ChatGPT in-app browser at `https://webauthn.io/`; synthetic account `codex-synthetic-064-20261103`.
- **expected_observed_result**: After the user clicked the page to focus it, Register produced the WebAuthn.io message “The operation either timed out or was not allowed” with a link to the WebAuthn privacy considerations. No Windows security-key prompt, authenticator request, registration success or credential result appeared.
- **date**: 2026-11-04
- **limitations**: WebAuthn.io does not distinguish timeout from browser policy rejection in this message. This does not establish Windows BLE discovery, pairing or a Chimali transport failure. BLE T020/T023 remain open.
- **status**: `Unverified (WebAuthn operation timed out or was not allowed; no Windows prompt or credential result)`.
- **artifact_link**: [BLE T020/T023](tasks.md), [parent operation outcome](../064-bluetooth-transport-selection/implementation-evidence.md#record-061-integrated-browser-webauthn-operation-did-not-complete-t031t041--unverified)

### Record 081: Mi A2 Reboot Recovery Check Interrupted (T035 — Unverified)
- **requirement**: BLE T035 reboot and saved-selection recovery; `FR-BLE-060`, parent `FR-BT-SEL-030`
- **scope**: Verify that the saved BLE selection survives a device reboot and resumes only after the required explicit start on Mi/API29.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Capture pre-reboot state, issue `adb -s 2ac6876 reboot`, wait for the device, then verify boot completion, transport service, saved BLE selection and advertiser state.
- **environment**: Windows 11 x64; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; package `com.chimali.shrunk`.
- **expected_observed_result**: Before reboot, MainActivity was resumed, Fido2TransportService was foreground and Android listed `com.chimali.shrunk (Registered)` as an advertiser. After reboot, `adb wait-for-device` returned but `sys.boot_completed` was blank, the lock screen was active, Bluetooth Manager was unavailable, and the device later disappeared from `adb devices`. Post-reboot app/service/selection state could not be inspected.
- **date**: 2026-11-04
- **limitations**: No app data was cleared. This is an interrupted reboot attempt, not evidence of recovered or failed BLE selection/advertising behavior. The device must be unlocked/reconnected before the subcheck can be repeated; BLE T035/T036 remain open.
- **status**: `Unverified (Mi disconnected before boot completion and app state inspection)`.
- **artifact_link**: [BLE T035/T036](tasks.md), [parent reboot evidence](../064-bluetooth-transport-selection/implementation-evidence.md#record-062-mi-a2-reboot-recovery-check-interrupted-t035--unverified)

### Record 082: Windows Passkey Dialog Closed at Change (T020/T023 — Unverified)
- **requirement**: BLE T020 registration and T023 assertion; `FR-BLE-020`, `FR-BLE-030`
- **scope**: Record the user-handled Windows passkey dialog outcome from the latest synthetic registration attempt.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: From WebAuthn.io's synthetic registration attempt, choose the Windows “Cambiar” control and then select the Bluetooth security key; inspect the ensuing Windows prompt and browser result.
- **environment**: Windows 11 host; ChatGPT in-app browser at `https://webauthn.io/`; synthetic account `codex-synthetic-064-20261103`.
- **expected_observed_result**: The user reported that clicking “Cambiar” closed the dialog before a security-key choice or subsequent prompt appeared. WebAuthn.io still displayed “The operation either timed out or was not allowed.” No registration success or credential result was observed.
- **date**: 2026-11-04
- **limitations**: Timeout is suspected by the user but unconfirmed. This is not a Windows registration pass and does not establish transport failure. BLE T020/T023 remain open.
- **status**: `Unverified (dialog closed at Change; no security-key selection, browser success or credential result)`.
- **artifact_link**: [BLE T020/T023](tasks.md), [parent dialog outcome](../064-bluetooth-transport-selection/implementation-evidence.md#record-063-windows-passkey-dialog-closed-at-change-t031t041--unverified)

### Record 083: Mi A2 BLE Selection and Explicit Start After Reboot (T035 — Passed, Scoped)
- **requirement**: BLE T035 reboot/saved-selection recovery; `FR-BLE-060`; parent `FR-BT-SEL-030`
- **scope**: Verify on Mi/API29 that reboot preserves the saved BLE choice, does not restart advertising automatically, and explicit Start restores advertising.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Record active BLE/service/advertiser state; reboot with `adb -s 2ac6876 reboot`; after boot, open `com.chimali.shrunk/com.chimali.MainActivity`; verify Ready to Start and no service/advertiser; tap Start; confirm Advertising, foreground service, advertiser registration, and saved/active BLE selection in Settings.
- **environment**: Windows 11 x64; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; package `com.chimali.shrunk`.
- **expected_observed_result**: Before reboot, MainActivity was resumed, Fido2TransportService was foreground and Android listed `com.chimali.shrunk (Registered)`. After boot, the service and advertiser were absent. Launching the app showed Ready to Start and BLE footer with no foreground service or advertiser. After explicit Start, the service became foreground and Android registered the advertiser; the UI showed Advertising. Settings then showed “FIDO Bluetooth LE (default)” selected and “Active in this process: FIDO Bluetooth LE / Active.” The device initially reconnected while locked/incomplete, then reported boot complete and allowed verification. No app data was cleared.
- **date**: 2026-11-04
- **limitations**: This proves one BLE-mode reboot/explicit-start subcheck only. It does not cover Classic reboot behavior, Windows discovery/registration/assertion, in-flight recovery, stale-bond recovery, 50-cycle stress, or full BLE T035/T036 acceptance. Coverage remains deferred by owner direction.
- **status**: `Passed (Mi/API29 BLE selection persistence and explicit-start reboot subcheck) / Unverified (remaining lifecycle, Windows and full BLE acceptance)`.
- **artifact_link**: [Mi reboot procedure and observations](../../feature/fido2/build/reports/fido2/064/mi-reboot-ble-explicit-start-20261104.txt), [Ready to Start after boot](../../feature/fido2/build/reports/fido2/064/mi-reboot-after-launch-20261104.png), [Advertising after explicit Start](../../feature/fido2/build/reports/fido2/064/mi-reboot-after-explicit-start-20261104.png), [saved and active BLE selection](../../feature/fido2/build/reports/fido2/064/mi-reboot-settings-20261104.png), [parent reboot subcheck](../064-bluetooth-transport-selection/implementation-evidence.md#record-064-mi-a2-ble-selection-and-explicit-start-after-reboot-t035--passed-scoped)

### Record 084: WebAuthn Retry After Security-Key Selection (T020/T023 — Unverified)
- **requirement**: BLE T020 registration and T023 assertion; `FR-BLE-020`, `FR-BLE-030`
- **scope**: Retry the authorized synthetic Windows registration after the user selected “Cambiar” → “Llave de seguridad”; inspect browser outcome and Mi app/service state.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: In the ChatGPT integrated browser at WebAuthn.io, submit Register for `codex-synthetic-064-20261103`; after the user-selected security-key path, inspect the browser result and query Mi activity, service, Bluetooth advertiser registration and recent logs over ADB.
- **environment**: Windows 11 host; ChatGPT in-app browser at `https://webauthn.io/`; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; package `com.chimali.shrunk`.
- **expected_observed_result**: The user reported that Mi left Chimali after selecting “Llave de seguridad.” The retry page showed “The operation either timed out or was not allowed,” with no registration success or credential result. ADB afterward showed `com.chimali.shrunk/com.chimali.MainActivity` resumed, `Fido2TransportService` foreground, and `com.chimali.shrunk (Registered)` in Bluetooth Manager; the service record showed `crashCount=1`. The inspected recent filtered log lines contained no matching FIDO request failure that explains the reported app switch.
- **date**: 2026-11-05
- **limitations**: The WebAuthn message does not distinguish timeout from browser policy rejection. The ADB snapshot confirms the app/service/advertiser state only when inspected and does not establish the exact transition during the user-reported switch. No successful BLE registration/assertion, Windows credential result, or completed T020/T023 acceptance was observed.
- **status**: `Unverified (WebAuthn operation timed out or was not allowed; no credential result)`.
- **artifact_link**: [BLE T020/T023](tasks.md), [parent retry evidence](../064-bluetooth-transport-selection/implementation-evidence.md#record-065-webauthn-retry-after-security-key-selection-t031t041--unverified)

### Record 085: ASUS Bluetooth Security-Key Conversation Interrupted (T020/T023 — Unverified)
- **requirement**: BLE T020 registration and T023 assertion; `FR-BLE-020`, `FR-BLE-030`
- **scope**: Investigate the reported Windows Bluetooth security-key conversation and Android app switch during the ASUS/API35 synthetic registration attempt.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Start the Chimali Authenticator on ASUS, submit Register for the synthetic WebAuthn.io account, select the Windows Bluetooth security-key path as directed by the user, then inspect Android activity, FIDO service, BLE advertiser and recent logcat events.
- **environment**: Windows 11 host; ChatGPT in-app browser at `https://webauthn.io/`; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`.
- **expected_observed_result**: The user reported Windows “La conversación con la clave de seguridad de Bluetooth está en curso” while the Android screen left Chimali. Android logs recorded a `swipe-up` gesture at 11-05 06:25:58 followed by the ASUS launcher becoming foreground. At 06:26:06, `Fido2TransportService` was destroyed and its GATT server cleared, closed and unregistered. At 06:26:07, the foreground service restarted and re-registered FIDO service UUID `0000fffd-0000-1000-8000-00805f9b34fb`. A later ADB check showed MainActivity resumed, UI “Advertising...”, the service foreground, and `com.chimali.shrunk (Registered)`. WebAuthn.io returned “The operation either timed out or was not allowed”; no credential result was observed.
- **date**: 2026-11-05
- **limitations**: The logs establish a swipe-up transition and a brief service/GATT restart, but do not identify who or what generated the gesture or prove that it caused the Windows timeout. The generic WebAuthn result does not distinguish timeout from browser policy rejection. BLE T020/T023 remain open.
- **status**: `Unverified (Windows conversation did not yield a credential result; Android recorded a swipe-up and service/GATT restart)`.
- **artifact_link**: [ASUS WebAuthn activity and service log excerpt](../../feature/fido2/build/reports/fido2/064/asus-webauthn-switch-to-launcher-20261105.txt), [BLE T020/T023](tasks.md), [parent investigation](../064-bluetooth-transport-selection/implementation-evidence.md#record-066-asus-bluetooth-security-key-conversation-interrupted-t031t041--unverified)

### Record 086: FIDO GATT Notification Callback Recursion Fixed (T020/T023 — Implementation Verified)
- **requirement**: BLE T020 registration and T023 assertion; `FR-BLE-020`, `FR-BLE-030`
- **scope**: Fix the ASUS-observed crash during Windows Bluetooth security-key registration and verify callback forwarding.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Rename the callback listener to remove collision with Android's `onNotificationSent` override; run `FidoBleGattContractTest`, then lint Android main and host-test source sets.
- **environment**: Windows 11 host; Gradle 9.6.0; feature module `:feature:fido2`.
- **expected_observed_result**: ASUS logcat showed Windows GATT connection followed by repeated `FidoBleGattServer.onNotificationSent` frames, `StackOverflowError`, app process death and foreground-service restart. The override now forwards to`notificationSentListener`. The regression test verified the listener receives the device/status exactly once. Focused Android host test passed; both Android main and host-test KtLint tasks passed. The corrected `shrunkDebug` build was installed on ASUS AI2302/API35; after granting nearby-device and notification permissions, enabling Passkey Authenticator during onboarding and tapping Start, the UI showed “Advertising...”, the foreground FIDO service was active and Android listed `com.chimali.shrunk (Registered)`.
- **date**: 2026-11-05
- **limitations**: This corrects the confirmed recursion crash and verifies deployment/advertising on ASUS. A Windows registration/assertion retry is still needed to verify end-to-end ceremony behavior; BLE T020/T023 remain open.
- **status**: `Passed (source correction, focused callback regression test, focused lint, ASUS installation and advertising) / Unverified (physical Windows ceremony)`.
- **artifact_link**: [ASUS callback-crash log excerpt](../../feature/fido2/build/reports/fido2/064/asus-webauthn-callback-stackoverflow-20261105.txt), [ASUS corrected-build deployment](../../feature/fido2/build/reports/fido2/064/asus-callback-fix-deployment-20261105.txt), [BLE T020/T023](tasks.md), [parent callback-fix evidence](../064-bluetooth-transport-selection/implementation-evidence.md#record-067-fido-gatt-notification-callback-recursion-fixed-t031t041--implementation-verified)

### Record 087: Corrected Build Installed on Mi for User Verification (T020/T023 — Setup Passed)
- **requirement**: BLE T020 registration and T023 assertion; `FR-BLE-020`, `FR-BLE-030`
- **scope**: Deploy the corrected callback-fix build to the Mi device so the user can test it.
- **revision**: Current Feature 064 worktree, branch `064-bluetooth-transport-selection`; uncommitted candidate changes.
- **named_procedure**: Attempt in-place install; after signature mismatch, uninstall only `com.chimali.shrunk` with the user's approval and install the `shrunkDebug` APK; query installed version.
- **environment**: Windows 11 host; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; package `com.chimali.shrunk`.
- **expected_observed_result**: In-place update was rejected because the installed package had a different signing key. The user authorized erasing only this package's local Mi data. Uninstall and APK installation then succeeded. Package manager reports version `0.1.0`, versionCode `1`, installed at `2026-11-05 01:19:49`. MainActivity was then launched; Fido2TransportService was not foreground and Android Bluetooth Manager showed no `com.chimali.shrunk` registration.
- **date**: 2026-11-05
- **limitations**: MainActivity was launched after installation. The FIDO service was not foreground and Android Bluetooth Manager showed no com.chimali.shrunk registration. Mi first-run setup, BLE advertising, and Windows WebAuthn registration/assertion are unverified. This record establishes deployment only; BLE T020/T023 remain open.
- **status**: `Passed (Mi APK installation and MainActivity launch) / Unverified (first-run setup, BLE advertising and WebAuthn ceremony)`.
- **artifact_link**: [Mi corrected-build deployment](../../feature/fido2/build/reports/fido2/064/mi-callback-fix-deployment-20261105.txt), [BLE T020/T023](tasks.md), [parent deployment record](../064-bluetooth-transport-selection/implementation-evidence.md#record-068-corrected-build-installed-on-mi-for-user-verification-t031t041--setup-passed)

### Record 088: ASUS Integrated-Browser FIDO BLE GetAssertion (T023 — Partial)
- **requirement**: BLE T023 GetAssertion; `FR-BLE-030`, `FR-BLE-050`
- **scope**: Independently complete one authenticated WebAuthn assertion through the corrected ASUS BLE authenticator and retain device-side correlation and diagnostic timing.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`, source revision `37388495` plus uncommitted callback fix; `shrunkDebug` APK version `0.1.0` installed on ASUS.
- **named_procedure**: In the ChatGPT integrated browser at WebAuthn.io, submit Authenticate for synthetic account `codex-synthetic-064-20261103`; correlate browser outcome with ASUS GATT, revision selection, biometric result, service and advertiser state over ADB.
- **environment**: Windows 11 host; ChatGPT integrated browser; WebAuthn.io; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`.
- **expected_observed_result**: WebAuthn.io displayed “You're logged in!”. ASUS logcat recorded a successful GATT server connection at 08:25:37.370, MTU 517, FIDO2 revision `0x20` selected at 08:25:38.036, and successful Android fingerprint verification at 08:25:40.970 for `com.chimali.shrunk`. Android Bluetooth Manager continued to show Chimali registered and advertising; a captured ASUS screen showed “Advertising...” after the ceremony. Device-side connection-to-biometric-success interval was 3.600 seconds. The user also reported that both registration and authentication succeeded on ASUS; registration details are user-reported in this record.
- **date**: 2026-11-05
- **limitations**: The 3.600-second interval is not full relying-party end-to-end timing. Numeric-comparison accept/reject, independently captured MakeCredential metadata, UV keepalive cadence and full diagnostic timing remain unverified. This records one successful GetAssertion only; it does not complete all BLE physical acceptance obligations.
- **status**: `Passed (one ASUS WebAuthn GetAssertion with correlated BLE revision and local UV) / Unverified (complete timing and remaining BLE acceptance matrix)`.
- **artifact_link**: [ASUS assertion and device correlation](../../feature/fido2/build/reports/fido2/064/asus-webauthn-assertion-20261105.txt), [ASUS screen after assertion](../../feature/fido2/build/reports/fido2/064/asus-after-web-authn-assertion-20261105.png), [BLE T023](tasks.md), [parent physical ceremony record](../064-bluetooth-transport-selection/implementation-evidence.md#record-069-asus-clean-install-registration-and-assertion-t022--passed-scoped)
### Record 089: ASUS Classic HID Activation and BLE Restoration (T023/T035 — Partial)
- **requirement**: BLE T023/T035 lifecycle handoff; parent selection T027/T031; `FR-BLE-060`, `FR-BT-SEL-030`, `FR-BT-SEL-040`
- **scope**: Verify parent-selected Classic activation after full process restart and confirm restoration to BLE on the ASUS test device.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; corrected `shrunkDebug` version `0.1.0`.
- **named_procedure**: From active BLE, save Classic and confirm pending state; force-stop/relaunch MainActivity; explicitly Start and inspect HID profile registration; save BLE, repeat process restart, explicitly Start and inspect advertising.
- **environment**: ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; Windows 11 host not used for Classic ceremony.
- **expected_observed_result**: Classic was pending while BLE remained active. After restart, MainActivity showed Classic selected and Ready to Start with no active service until Start. Following user approval of Android's 120-second Bluetooth visibility prompt, Android acquired HID_DEVICE, `registerApp()` returned true and `onAppStatusChanged registered=true`; Settings reported Classic active. The reverse choice remained pending until restart; after explicit Start, BLE returned to Advertising with its GATT service registered.
- **date**: 2026-11-05
- **limitations**: No Windows Classic HID discovery or WebAuthn ceremony was performed. This covers process selection and transport activation only, not T023 assertion or T035 interruption/recovery and 50-cycle acceptance.
- **status**: `Passed (ASUS Classic HID profile registration and BLE process-switch restoration) / Unverified (Windows Classic ceremony and remaining BLE lifecycle matrix)`.
- **artifact_link**: [ASUS Classic/BLE process-switch evidence](../../feature/fido2/build/reports/fido2/064/asus-classic-ble-switch-20261105.txt), [BLE implementation evidence](implementation-evidence.md), [parent Selection T027/T031](../064-bluetooth-transport-selection/tasks.md)
### Record 090: ASUS BLE Background, Explicit Stop and Restart (T035 — Partial)
- **requirement**: BLE T035 lifecycle recovery; `FR-BLE-060`, `FR-BLE-070`
- **scope**: Confirm foreground BLE service and advertising survive ordinary app backgrounding, explicit Stop releases them, and explicit Start restores them.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; corrected `shrunkDebug` version `0.1.0`.
- **named_procedure**: With BLE Advertising active, press Home, inspect top-resumed app/service/advertiser; reopen MainActivity, Stop Authenticator and inspect cleanup; explicitly Start and inspect recovery.
- **environment**: ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; ADB dumpsys and UI hierarchy.
- **expected_observed_result**: With ASUS launcher top-resumed, the FIDO service remained foreground and Android showed the Chimali advertiser registered. After explicit Stop, the UI changed to Ready to Start; subsequent state showed no foreground FIDO service and no Chimali advertising registration. Explicit Start restored the foreground service, BLE registration and Advertising UI.
- **date**: 2026-11-05
- **limitations**: A single ordinary background/Stop/Start sequence only. Host command intervals included deliberate waits and ADB overhead and do not establish the strict <1s Stop/<500ms Start budgets. OS process-death, reboot, permission-loss, active ceremony interruption, stale-bond recovery and 50 cycles remain unverified; T035 stays open.
- **status**: `Passed (background survival and explicit resource release/recovery) / Unverified (timing budgets and remaining lifecycle matrix)`.
- **artifact_link**: [ASUS BLE background/Stop/Start observations](../../feature/fido2/build/reports/fido2/064/asus-ble-background-stop-restart-20261105.txt), [BLE T035](tasks.md), [parent lifecycle record](../064-bluetooth-transport-selection/implementation-evidence.md#record-071-asus-ble-background-stop-and-restart-t035--partial)
### Record 091: Mi Fresh-Install BLE Advertising and Passive PC GATT Connection (T020 — Partial)
- **requirement**: BLE T020 device discovery and connection setup; `FR-BLE-010`, `FR-BLE-020`
- **scope**: Verify that the corrected Mi build starts BLE advertising and observe one paired-PC GATT connection after fresh installation.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; corrected `shrunkDebug` version `0.1.0`.
- **named_procedure**: On Mi/API29 after the authorized package reset and install, start the authenticator in BLE mode; inspect UI, service, GATT registration and device logcat for the paired PC interaction.
- **environment**: Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; Windows 11 paired PC `LOQ15IRX10-3`; package `com.chimali.shrunk`.
- **expected_observed_result**: Mi entered Advertising with the FIDO transport service foreground and its GATT server registered. Android logged one successful PC GATT connection, MTU 517 negotiation and session cleanup on disconnect about five seconds later; afterward the app remained Advertising.
- **date**: 2026-11-05
- **limitations**: No revision `0x20`, FIDO request/CTAP operation, local verification or browser result was logged. The PC connection may have been discovery rather than a WebAuthn request. The clean Mi package data contains no registered synthetic credential. This is not a registration/assertion pass; BLE T020 remains open.
- **status**: `Passed (Mi advertising/GATT registration and one paired-PC GATT connection) / Unverified (FIDO protocol interaction and WebAuthn ceremony)`.
- **artifact_link**: [Mi passive GATT connection](../../feature/fido2/build/reports/fido2/064/mi-passive-gatt-connection-20261105.txt), [BLE T020](tasks.md), [parent Mi deployment evidence](../064-bluetooth-transport-selection/implementation-evidence.md#record-072-mi-fresh-install-advertising-and-passive-pc-gatt-connection-t020--partial)

### Record 092: Current Worktree Local CI Without Deferred Coverage (T036/T038 — Partial)
- **requirement**: BLE regression and coverage gate; `FR-BLE-060`, `FR-BLE-070`
- **scope**: Validate source and host-test changes using ordinary local CI, with coverage explicitly deferred by the user.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current uncommitted candidate changes.
- **named_procedure**: Run `tools/local-ci.ps1 -NoFix -NoConfigurationCache`, without `-RunCoverage`.
- **environment**: Windows 11 host; Gradle 9.6.0; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`.
- **expected_observed_result**: Local CI completed successfully in 143.34 seconds. KtLint, Detekt, Release lint, compile-all, unit tests and configured KMP Android host tests passed. Coverage was not run. The runner's notice states that this does not establish hosted integration acceptance.
- **date**: 2026-11-05
- **limitations**: BLE coverage acceptance remains deferred. This run does not establish numeric-comparison accept/reject, independent registration, strict latency budgets, 50-cycle stability or the full interruption/recovery matrix.
- **status**: `Passed (ordinary non-coverage local CI) / Unverified (deferred coverage gate and remaining BLE acceptance)`.
- **artifact_link**: [BLE T036/T038](tasks.md), [parent validation record](../064-bluetooth-transport-selection/implementation-evidence.md#record-073-current-worktree-local-ci-without-deferred-coverage-t039--partial)

### Record 093: Latest shrunkDebug Installed and Advertising on Mi (T020 — Partial)
- **requirement**: BLE device discovery and advertising startup; `FR-BLE-010`, `FR-BLE-020`
- **scope**: Build the current worktree candidate, update the Mi package in place without data removal, and verify FIDO BLE advertising and foreground-service registration.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current uncommitted candidate; APK SHA-256 `07F5C1E66B3166E7440A312FCE94CC9BFDE5EDFBDB3278B4DBD83F0AA9917876`.
- **named_procedure**: Run `:app:assembleShrunkDebug`, `adb install -r`, launch MainActivity, explicitly start the authenticator, then inspect Android UI/service/advertiser state.
- **environment**: Windows host; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; package `com.chimali.shrunk`.
- **expected_observed_result**: APK assembly and in-place update succeeded. Installed package reports version `0.1.0`, versionCode `1`. The app showed “Advertising...” and “Ready to pair with a new host PC”; Fido2TransportService was foreground and Android listed Chimali as a registered advertiser.
- **date**: 2026-11-05
- **limitations**: No paired-host revision selection, numeric-comparison result, GetInfo/keepalive behavior, MakeCredential, local user verification or WebAuthn.io result was captured. T020 remains open.
- **status**: `Passed (current-build installation and BLE advertising startup) / Unverified (protocol and WebAuthn acceptance)`.
- **artifact_link**: [Mi latest-build deployment and advertising](../../feature/fido2/build/reports/fido2/064/mi-current-build-deployment-20261105.txt), [parent deployment record](../064-bluetooth-transport-selection/implementation-evidence.md#record-074-latest-shrunkdebug-installed-and-advertising-on-mi-ble-deployment--partial)

### Record 094: Current-Candidate Device Security and Radio Recovery (T035 — Partial)
- **requirement**: BLE authenticated GATT access and recovery; `FR-BLE-020`, `FR-BLE-060`, `FR-BLE-070`
- **scope**: Exercise the current candidate's GATT security test suite on both connected Android devices, the applicable foreground lifecycle suite, and a real Mi radio-off/Retry sequence.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current source and `shrunkDebug` APK SHA-256 `07F5C1E66B3166E7440A312FCE94CC9BFDE5EDFBDB3278B4DBD83F0AA9917876`.
- **named_procedure**: Run `:feature:fido2:connectedAndroidDeviceTest` filtered to `FidoBleSecurityDeviceTest` on Mi and ASUS; filter to `FidoBleLifecycleDeviceTest` on both; retain XML. On Mi, with current app Advertising, disable Bluetooth in Settings, verify unavailable error, re-enable Bluetooth, and tap Retry.
- **environment**: Windows host; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`.
- **expected_observed_result**: GATT security tests passed 4/4 on Mi and 4/4 on ASUS, with zero failures/errors/skips. Foreground-service lifecycle tests passed 3/3 on ASUS. On Mi, `FidoBleLifecycleDeviceTest` has `@SdkSuppress(minSdkVersion = 31)`, so its API29 run discovered zero tests; this is not counted as a pass. Mi radio-off transitioned to “Error Occurred / BLE advertising stopped unexpectedly”; after Bluetooth was restored and Retry was tapped, UI returned to Advertising, service was foreground and advertiser registration was present.
- **date**: 2026-11-05
- **limitations**: These focused checks do not cover full BLE protocol acceptance, numeric comparison, a Windows ceremony, permission revocation, process death, stale bond, cancellation or 50-cycle stability. The Mi lifecycle instrumentation class is not applicable below API31; manual Mi BLE Stop/Start and radio recovery evidence remains separately scoped.
- **status**: `Passed (4/4 GATT security tests on both devices; 3/3 ASUS lifecycle tests; Mi radio-off/retry recovery) / Not run by API gate (Mi lifecycle class) / Unverified (remaining BLE acceptance)`.
- **artifact_link**: [ASUS GATT security XML](../../feature/fido2/build/reports/fido2/064/asus-fido-ble-security-20261105.xml), [Mi GATT security XML](../../feature/fido2/build/reports/fido2/064/mi-fido-ble-security-20261105.xml), [ASUS lifecycle XML](../../feature/fido2/build/reports/fido2/064/asus-fido-ble-lifecycle-20261105.xml), [Mi lifecycle API-gated XML](../../feature/fido2/build/reports/fido2/064/mi-fido-ble-lifecycle-20261105.xml), [Mi radio-off recovery](../../feature/fido2/build/reports/fido2/064/mi-radio-off-retry-current-build-20261105.txt), [BLE T035](tasks.md)

### Record 095: Current-Candidate Cross-Device FIDO BLE Discovery (T020 — Partial)
- **requirement**: BLE advertiser discovery; `FR-BLE-010`, `TR-BLE-010`
- **scope**: Observe the FIDO BLE service UUID from a distinct nearby Android device while each phone is advertising the current implementation.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current source candidate; latest Mi `shrunkDebug` APK SHA-256 `07F5C1E66B3166E7440A312FCE94CC9BFDE5EDFBDB3278B4DBD83F0AA9917876`.
- **named_procedure**: Keep both Chimali apps advertising and run `:feature:fido2:connectedAndroidDeviceTest --project-prop=android.testInstrumentationRunnerArguments.class=com.chimali.fido2.data.transport.FidoBleAdvertiserDiscoveryDeviceTest` on both devices; inspect device-specific XML.
- **environment**: Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`.
- **expected_observed_result**: `activeAuthenticatorAdvertisesTheFidoServiceUuid` passed 1/1 on both devices with zero failures/errors/skips. Each scanner found the FIDO service UUID advertised by the other physical phone. Both app services and advertiser registrations remained active after the run.
- **date**: 2026-11-05
- **limitations**: This is Android-to-Android discovery only. It does not establish Windows discovery or authenticated pairing, numeric comparison, GetInfo/keepalives, registration, assertion, or full T020/T023 acceptance.
- **status**: `Passed (remote FIDO UUID discovery on both API29 and API35) / Unverified (Windows and remaining BLE acceptance)`.
- **artifact_link**: [Mi discovery test XML](../../feature/fido2/build/reports/fido2/064/mi-fido-ble-discovery-20261105.xml), [ASUS discovery test XML](../../feature/fido2/build/reports/fido2/064/asus-fido-ble-discovery-20261105.xml), [parent discovery record](../064-bluetooth-transport-selection/implementation-evidence.md#record-076-current-candidate-cross-device-fido-ble-discovery-t020--partial)

### Record 096: Mi Current-Build Force-Stop and Explicit BLE Restart (T035 — Partial)
- **requirement**: BLE transport stopped state and explicit recovery; `FR-BLE-060`, `FR-BLE-070`
- **scope**: Verify the current BLE selection is not automatically restarted after an Android force-stop/relaunch and explicit Start restores service resources.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current `shrunkDebug` version `0.1.0` installed on Mi.
- **named_procedure**: From Advertising, force-stop the application; inspect service/advertiser state, relaunch MainActivity, inspect the pre-Start state, then explicitly Start and inspect UI/service/advertiser registration.
- **environment**: Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; package `com.chimali.shrunk`.
- **expected_observed_result**: Force-stop removed service and advertising. MainActivity relaunched in Ready to Start without auto-start. Explicit Start restored Advertising, the foreground service and the registered GATT advertiser.
- **date**: 2026-11-05
- **limitations**: This single BLE force-stop path does not establish Classic mode, OS process death outside force-stop, reboot, permission revocation, mid-ceremony change, stale-bond recovery or 50-cycle stability.
- **status**: `Passed (Mi BLE force-stop and explicit-start recovery) / Unverified (full BLE T035 lifecycle/recovery matrix)`.
- **artifact_link**: [Mi current-build force-stop/restart observations](../../feature/fido2/build/reports/fido2/064/mi-current-build-force-stop-restart-20261105.txt), [BLE T035](tasks.md), [parent lifecycle record](../064-bluetooth-transport-selection/implementation-evidence.md#record-077-mi-force-stop-and-explicit-ble-restart-on-current-build-t035--partial)

### Record 097: Cross-Device 100-Cycle GATT Reconnect Stress (T035 — Partial)
- **requirement**: BLE connection lifecycle, FIDO service discovery and cleanup; `FR-BLE-060`, `FR-BLE-070`, `TR-BLE-020`
- **scope**: Exercise 50 connect, FIDO-service discovery, disconnect and close cycles from each Android phone against the other phone's current GATT server.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current source candidate and `shrunkDebug` package version `0.1.0` on both devices.
- **named_procedure**: With both Chimali apps advertising, run `FidoBleReconnectStressDeviceTest` on Mi as GATT client to ASUS, then on ASUS as client to Mi. Retain the two instrumentation XML reports and PSS observations.
- **environment**: Windows host; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`.
- **expected_observed_result**: Mi→ASUS passed 1/1 test, 50/50 cycles, zero failures/errors/skips in 75.963s. ASUS→Mi passed 1/1, 50/50 cycles, zero failures/errors/skips in 32.266s. ASUS PSS increased 2,431 KiB immediately after the first run and was 258 KiB below its pre-run snapshot after idle; Mi PSS increased 684 KiB after the reverse run. Both devices retained the FIDO foreground service and registered advertiser.
- **date**: 2026-11-05
- **limitations**: The test covers discovery, connection, service discovery, disconnect and close, not authenticated pairing, security rejection, CTAP, notification delivery, in-flight interruption/cancellation, stale bonds or Windows. PSS is process-wide/noisy and not proof of leak freedom. Full BLE T035 and T038 remain open.
- **status**: `Passed (100 cross-device Android GATT reconnect/service-discovery cycles) / Unverified (security, CTAP interruption, Windows and remaining BLE acceptance)`.
- **artifact_link**: [Mi→ASUS instrumentation XML](../../feature/fido2/build/reports/fido2/064/mi-fido-ble-reconnect-stress-20261105.xml), [ASUS→Mi instrumentation XML](../../feature/fido2/build/reports/fido2/064/asus-fido-ble-reconnect-stress-20261105.xml), [PSS and procedure notes](../../feature/fido2/build/reports/fido2/064/cross-device-100-gatt-reconnect-cycles-20261105.txt), [BLE T035](tasks.md), [parent lifecycle evidence](../064-bluetooth-transport-selection/implementation-evidence.md#record-079-cross-device-100-cycle-gatt-reconnect-stress-t035--partial)

### Record 098: ASUS Classic Mode Start and Visibility Consent (Parent T027/T031 — Partial)
- **requirement**: Verify selection preserves the independent BLE component while parent Classic HID activates; `FR-BLE-010`, `FR-BLE-070`
- **scope**: Check that the shared app-level mode setting can activate Classic after restart and restore the BLE component afterward.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current `shrunkDebug` candidate, version `0.1.0`.
- **named_procedure**: Select Bluetooth HID (Classic), restart the app process, explicitly start the authenticator, allow the Android 120-second visibility prompt, inspect reported system/app mode, then select BLE and explicitly start it again.
- **environment**: ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`.
- **expected_observed_result**: App displayed Bluetooth HID mode after restart. Following explicit Android visibility consent, Bluetooth reported `SCAN_MODE_CONNECTABLE_DISCOVERABLE` and the app showed Advertising. Windows discovery, pairing, HID reports and WebAuthn were not exercised. Restoring FIDO Bluetooth LE and explicitly starting returned the app to BLE Advertising with foreground service and registered advertiser.
- **date**: 2026-11-05
- **limitations**: This exercises app selection, Android consent and BLE restoration only. It does not certify Classic host interoperability and does not complete BLE T035. Parent T027/T031 and BLE T035/T038 remain open.
- **status**: `Passed (Classic mode selection/consent and subsequent BLE restoration) / Unverified (Windows Classic interoperability and remaining BLE lifecycle)`.
- **artifact_link**: [ASUS Classic visibility-consent notes](../../feature/fido2/build/reports/fido2/064/asus-classic-visibility-20261105.txt), [parent transport-selection evidence](../064-bluetooth-transport-selection/implementation-evidence.md#record-080-asus-classic-mode-start-and-visibility-consent-t027t031--partial)

### Record 099: ASUS Advertiser-Permission Revocation and BLE Recovery (T035 — Partial)
- **requirement**: BLE unavailable-permission handling and explicit lifecycle recovery; `FR-BLE-060`, `FR-BLE-070`
- **scope**: Revoke the app's Bluetooth advertising permission while BLE is active, then restore permission and verify explicit restart returns the selected transport.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current ASUS `shrunkDebug` version `0.1.0`.
- **named_procedure**: While BLE Advertising, revoke only `BLUETOOTH_ADVERTISE`; inspect Android process exit reason and service restart; restore the permission, relaunch the app and explicitly Start the authenticator.
- **environment**: ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`.
- **expected_observed_result**: Android ApplicationExitInfo reported `PERMISSION CHANGE` with status 0 and no trace; ActivityManager logged the permission-driven process kill and scheduled the sticky FIDO transport service to restart. Sampled AndroidRuntime logs showed no fatal Java exception. Following permission regrant, the app remained Ready to Start until explicit Start; BLE Advertising, foreground service and registered advertiser then returned.
- **date**: 2026-11-05
- **limitations**: Android killed the app process as the runtime permission changed, so this did not observe an in-app error state during the transition. No in-flight GATT/CTAP request, cancellation, stale bond or Windows behavior was tested. Full BLE T035 remains open.
- **status**: `Passed (ASUS OS permission-change lifecycle and explicit BLE recovery) / Unverified (in-flight permission interruption and remaining BLE T035/T038 acceptance)`.
- **artifact_link**: [ASUS permission revocation and recovery notes](../../feature/fido2/build/reports/fido2/064/asus-advertise-permission-revocation-recovery-20261105.txt), [BLE T035](tasks.md), [parent record](../064-bluetooth-transport-selection/implementation-evidence.md#record-081-asus-ble-advertising-permission-revocation-and-recovery-t035--partial)

### Record 100: Current Worktree Ordinary Local CI Without Coverage (T036 — Partial)
- **requirement**: BLE implementation static analysis, configured compilation and host/device-test source validation; `FR-BLE-080`, `TR-BLE-030`
- **scope**: Validate the current uncommitted implementation and test source with ordinary local CI while retaining the owner's coverage deferral.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current uncommitted source candidate.
- **named_procedure**: Run `:feature:fido2:ktlintAndroidDeviceTestSourceSetCheck`; then invoke a temporary `tools/local-ci.ps1` copy with Gradle's problems report disabled and a worktree-local Gradle user home, using `-NoFix -NoConfigurationCache` and no `-RunCoverage`.
- **environment**: Windows host; Gradle 9.6.0; configured SDK/device environment; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`.
- **expected_observed_result**: Focused Android device-test KtLint passed (48 tasks; 6 executed). Ordinary local CI passed in 317.78 seconds: KtLint, Detekt, Release lint, comprehensive configured compilation, unit-test tasks, and configured KMP Android host-test tasks. Most test outputs were UP-TO-DATE; no failing tasks were reported.
- **date**: 2026-11-05
- **limitations**: Coverage and integration/nonregression checks were not run. This does not establish Tier 1/Tier 2/repository floors, trusted accepted-target comparisons, hosted enforcement, WebAuthn interoperability or remaining physical BLE T035 acceptance. Gradle's default problems-report write was denied by the execution environment; the temporary script disabled that report.
- **status**: `Passed (ordinary local CI without coverage; configured tasks successful, most outputs up-to-date) / Deferred by owner (coverage) / Unverified (trusted coverage comparison and physical BLE acceptance)`.
- **artifact_link**: [temporary local-CI script](../../build/local-ci-no-problems.ps1), [BLE task list](tasks.md), [parent local-CI evidence](../064-bluetooth-transport-selection/implementation-evidence.md#record-082-current-worktree-ordinary-local-ci-without-coverage-t039--partial)

### Record 101: ASUS Classic Visibility and Windows Discovery Attempt (T023 — Partial)
- **requirement**: BLE deployment transport selection and Classic cross-transport acceptance; `FR-BLE-060`, `FR-BLE-070`, parent `FR-BT-SEL-030`, `FR-BT-SEL-040`
- **scope**: Confirm Classic starts on ASUS and review host visibility; record impact on BLE's default selection.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current uncommitted candidate, `shrunkDebug` version `0.1.0`.
- **named_procedure**: Select Classic in app settings, restart, explicitly start, accept Android visibility consent, inspect app/system state and host device listing, then restore BLE.
- **environment**: ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; Windows 11 host; package `com.chimali.shrunk`.
- **expected_observed_result**: Android accepted the 120-second visibility request, reported connectable/discoverable and showed the active Classic transport foreground service. The system event log records a 210-second interval before returning to connectable-only, exceeding the prompt text. The Windows device list was unavailable through current controls (`Get-PnpDevice`: Access Denied); no host discovery or HID/WebAuthn exchange was verified. BLE was restored and explicitly started; Chimali showed BLE Advertising with foreground service active.
- **date**: 2026-11-05
- **limitations**: Does not verify Windows discovery/pairing, secured GATT, CTAP, WebAuthn, numeric comparison or BLE T023 diagnostic timing. The system-reported visibility duration requires follow-up.
- **status**: `Passed (ASUS Classic activation and discoverability request; BLE restored) / Unverified (Windows interop and remaining BLE/parent acceptance)`.
- **artifact_link**: [ASUS Classic discovery attempt](../../feature/fido2/build/reports/fido2/064/asus-classic-discovery-attempt-20261105.txt), [parent Classic record](../064-bluetooth-transport-selection/implementation-evidence.md#record-083-asus-classic-visibility-and-windows-discovery-attempt-t027t031-partial)

### Record 102: Mi API 29 Pattern-Based User Verification Failure and Candidate Correction (T020 — Partial)
- **requirement**: BLE physical registration/GetInfo acceptance and user verification; `FR-BLE-050`, `FR-BLE-060`, `FR-BLE-080`, parent `FR-BT-SEL-040`.
- **scope**: Trace Mi required-UV registration failure to Android 10 device-credential API behavior and prepare a compatible candidate.
- **revision**: Feature 064 worktree, branch `064-bluetooth-transport-selection`; current uncommitted source candidate, built `shrunkDebug` package version `0.1.0`.
- **named_procedure**: Trace reported error to `RegisterCredentialUseCase`, inspect API29's device-credential availability/prompt constraints, update compatibility handling, build and deploy the Mi package.
- **environment**: Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; Windows 11; package `com.chimali.shrunk`.
- **expected_observed_result**: User observed the required-UV registration error with a secure pattern and no fingerprint. Current service queried unsupported `canAuthenticate(DEVICE_CREDENTIAL)` on API29, so the pattern was reported unavailable; the old combined prompt flags were also unsupported there. The candidate now checks `KeyguardManager.isDeviceSecure()` on API <30 and uses AndroidX's legacy device-credential prompt configuration. The shrunkDebug build succeeded; signature mismatch required the previously authorized package-only uninstall/reinstall. The user granted location only while using the app, selected Passkey Authenticator during onboarding, and explicitly started BLE; Advertising and the transport foreground service were observed. The integrated WebAuthn.io tab is open but reports no page focus; corrected registration has not yet been attempted.
- **date**: 2026-11-05
- **limitations**: Physical pattern prompt, UV flag, registration, subsequent assertion and Windows ceremony remain unverified. Package-local test data was cleared by authorized reinstall; no other package was uninstalled. Coverage remains deferred.
- **status**: `Diagnosed and corrected in source; build succeeded and candidate installed / Unverified (physical required-UV registration, assertion and BLE acceptance)`.
- **artifact_link**: [Mi API 29 pattern user-verification notes](../../feature/fido2/build/reports/fido2/064/mi-api29-pattern-user-verification-20261105.txt), [parent user-verification record](../064-bluetooth-transport-selection/implementation-evidence.md#record-084-mi-api-29-pattern-based-user-verification-failure-and-candidate-correction-t027-partial)

### Record 103: ASUS WebAuthn Registration Success Confirmed (T020 — Partial)
- **requirement**: BLE physical MakeCredential registration; `FR-BLE-020`, `FR-BLE-030`, parent `FR-BT-SEL-020`, `FR-BT-SEL-030`.
- **scope**: Add the ASUS registration outcome independently observed in the integrated browser.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7`; ASUS installed APK SHA-256 `07f5c1e66b3166e7440a312fce94cc9bfde5edfbdb3278b4dbd83f0aa9917876`.
- **named_procedure**: User completes WebAuthn registration on ASUS and the integrated WebAuthn.io page is checked for its result.
- **environment**: Windows 11; ChatGPT integrated browser/WebAuthn.io; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; synthetic account `codex-synthetic-064-20261103`.
- **expected_observed_result**: User reported ASUS registration completion; WebAuthn.io showed “Success! Now try to authenticate...”. The ASUS FIDO transport service was foreground after the ceremony.
- **date**: 2026-11-05
- **limitations**: ASUS/API35 only. Does not verify the Mi/API29 device-credential fix, assertion, numeric-comparison rejection, required keepalive/CTAP details, or all of BLE T020 and parent acceptance.
- **status**: `Passed (ASUS registration result) / Unverified (Mi API29 registration and remaining BLE T020 acceptance)`.
- **artifact_link**: [ASUS registration success notes](../../feature/fido2/build/reports/fido2/064/asus-web-authn-registration-success-20261105.txt), [parent ASUS registration record](../064-bluetooth-transport-selection/implementation-evidence.md#record-085-asus-webauthn-registration-success-confirmed-t027-partial)

### Record 104: ASUS WebAuthn GetAssertion Rejected Before App-Side GATT Session (T023 — Partial)
- **requirement**: BLE physical Windows GetAssertion; `FR-BLE-030`, `FR-BLE-050`.
- **scope**: Record the assertion attempt immediately following the ASUS registration success observation.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7`; ASUS installed APK SHA-256 `07f5c1e66b3166e7440a312fce94cc9bfde5edfbdb3278b4dbd83f0aa9917876`.
- **named_procedure**: Click Authenticate in the integrated WebAuthn.io page for the registered synthetic account and inspect the reported Windows result plus ASUS foreground/GATT logs.
- **environment**: Windows 11; ChatGPT integrated browser/WebAuthn.io; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; account `codex-synthetic-064-20261103`.
- **expected_observed_result**: User reported Windows showed “No se puede usar esta clave de seguridad. Prueba con otra distinta.” Browser showed no login-success result. ASUS MainActivity was resumed and `Fido2TransportService` foreground. Filtered logs contained no app-side GATT connection/session, revision selection or CTAP operation in the attempt window. A follow-up Bluetooth Manager snapshot listed the package both as a registered GATT server and in the ongoing advertising set; this verifies current advertising, not Windows discovery or pairing during the failed attempt.
- **date**: 2026-11-05
- **limitations**: Evidence points to failure before an observed app-side GATT session but does not identify whether Windows key selection, Bluetooth routing/pairing or browser security-key handling caused it. No dialog screenshot, GATT operation or authenticator response was captured. This does not invalidate the preceding registration success record, but it does not verify assertion or full BLE T023 acceptance.
- **status**: `Failed (this assertion attempt; user-reported Windows error) / Unverified (failure cause, diagnostic timing and remaining BLE T023 acceptance)`.
- **artifact_link**: [ASUS assertion failure notes](../../feature/fido2/build/reports/fido2/064/asus-webauthn-assertion-security-key-unavailable-20261105.txt), [prior ASUS registration record](#record-103-asus-webauthn-registration-success-confirmed-t020--partial)

### Record 105: API 28–29 Secure Device-Credential Availability on Connected Phones (T023 — Partial)
- **requirement**: BLE platform user-verification readiness for physical registration/assertion; `FR-BLE-030`, `FR-BLE-050`.
- **scope**: Verify the corrected credential-availability branch on API29 and compare API35 behavior with AndroidX.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted source/test changes; shrunkDebug artifact SHA-256 `FF79746D1A0F4FF899A8EA3F57D44A3546538834086A6ABDA2348DD94832BE1B`.
- **named_procedure**: Run `:feature:fido2:connectedAndroidDeviceTest` filtered to `UserVerificationServiceDeviceTest` on the connected Mi/API29 and ASUS/API35 devices.
- **environment**: Gradle 9.6.0; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`.
- **expected_observed_result**: Mi passed 1/1 with zero skips; its configured secure credential satisfied the API<30 KeyguardManager branch and `isPinAvailable()` returned true. ASUS passed 1/1 with zero skips; service availability matched `BiometricManager.canAuthenticate(DEVICE_CREDENTIAL)`.
- **date**: 2026-11-05
- **limitations**: This is a platform availability check, not a user-entered BiometricPrompt or WebAuthn ceremony. Mi/API29 registration, Windows GetAssertion and remaining T023 acceptance remain unverified; no coverage gate was run.
- **status**: `Passed (credential availability detection on API29/API35) / Unverified (WebAuthn ceremonies and full BLE T023 acceptance)`.
- **artifact_link**: [API-versioned device test notes](../../feature/fido2/build/reports/fido2/064/user-verification-device-test-api29-20261105.txt), [Mi XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi%20A2%20-%2010.xml), [ASUS XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml)
### Record 106: API 29 Device-Credential Detection and FIDO Prompt Builder Compatibility (T023 — Partial)
- **requirement**: BLE user-verification readiness for Windows registration/assertion; `FR-BLE-030`, `FR-BLE-050`.
- **scope**: Verify legacy API credential detection and shared BiometricPrompt configuration on API29, plus the API35 authenticator mask path.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted source/test changes; shrunkDebug artifact SHA-256 `FF79746D1A0F4FF899A8EA3F57D44A3546538834086A6ABDA2348DD94832BE1B`.
- **named_procedure**: Run `:feature:fido2:connectedAndroidDeviceTest` filtered to `UserVerificationServiceDeviceTest` on the connected Mi/API29 and ASUS/API35 devices.
- **environment**: Gradle 9.6.0; Xiaomi Mi A2 / Android 10 / API29 / serial `2ac6876`; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`.
- **expected_observed_result**: Both devices passed 2/2 with zero XML failures or skips. On Mi, the secure pattern made `KeyguardManager.isDeviceSecure` true and `isPinAvailable()` returned true. On both devices, the shared FIDO prompt info built without displaying a prompt; ASUS credential availability matched AndroidX's API30+ query.
- **date**: 2026-11-05
- **limitations**: Gradle exited successfully but the Android test listener printed an `ArrayIndexOutOfBoundsException` while reporting one finish event; the XML reports contain both test cases as passed on each device. The targeted KtLint device-test source check also passed; full local CI remains unrun. This does not verify an actual prompt interaction, Mi WebAuthn registration, Windows GetAssertion, or full BLE T023 acceptance. Coverage remains deferred.
- **status**: `Passed (API29 credential detection and API28–29 prompt builder; API35 comparison) / Unverified (actual WebAuthn ceremonies and full BLE T023 acceptance)`.
- **artifact_link**: [API-versioned device test notes](../../feature/fido2/build/reports/fido2/064/user-verification-device-test-api29-20261105.txt), [Mi XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi%20A2%20-%2010.xml), [ASUS XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml)
### Record 107: Current Worktree Local CI Without Deferred Coverage (T038 — Partial)
- **requirement**: BLE local static analysis, required host/device test readiness and coverage/nonregression evidence.
- **scope**: Validate current BLE/parent working-tree changes with ordinary local CI while preserving the explicit coverage deferral.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes.
- **named_procedure**: Run `build/local-ci-no-problems.ps1 -NoFix -NoConfigurationCache` with a worktree-local Gradle user home; do not pass `-RunCoverage`.
- **environment**: Windows 11; Gradle 9.6.0; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; connected Mi A2/API29 and ASUS AI2302/API35 for the separate focused instrumentation run.
- **expected_observed_result**: Local CI passed in 510.42 seconds: KtLint, Detekt, release lint, configured compilation, unit tests and KMP Android host tests. The focused credential/prompt instrumentation separately passed 2/2 per phone, with zero XML failures/skips, and targeted device-test KtLint passed.
- **date**: 2026-11-05
- **limitations**: Kotlin daemon marker access was denied, so Gradle used compiler fallback. AndroidTestResultListener printed an `ArrayIndexOutOfBoundsException` while reporting one finish event, although each device XML shows 2/2 passed. No coverage completeness/floor or trusted target comparison was run; coverage is explicitly deferred and the hosted runner remains disabled. Physical Windows acceptance remains open.
- **status**: `Passed (ordinary local quality/compile/unit/host checks) / Deferred (coverage by owner direction) / Unverified (trusted nonregression, hosted integration and physical BLE acceptance)`.
- **artifact_link**: [Current local-CI summary](../../feature/fido2/build/reports/fido2/064/current-worktree-local-ci-no-coverage-20261105.txt), [Mi focused device-test XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi%20A2%20-%2010.xml), [ASUS focused device-test XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml)

### Record 113: Prompt Viewmodels Observe Retained Requests (T023 — Partial)
- **requirement**: Deliver a retained CTAP GetAssertion or MakeCredential request to its prompt even if dispatch precedes prompt-viewmodel subscription; `FR-BLE-030`, `FR-BLE-050`.
- **scope**: Have the home route and both prompt viewmodels observe retained request StateFlows, removing the prompt viewmodels' snapshot-plus-non-replaying-flow startup race. Add an integration test that dispatches assertion before creating its presenter.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes.
- **named_procedure**: Run `:feature:fido2:testAndroidHostTest` filtered to `AuthenticationIntegrationTest`, `RegistrationOwnerIntegrationTest` and `Fido2UiEventBusTest`; run Android main/host KtLint checks.
- **environment**: Windows 11; Gradle 9.6.0; worktree-local Gradle home `.gradle/codex-user-home`; daemon marker access was denied and Gradle compiled using fallback.
- **expected_observed_result**: All 54 focused tests passed: AuthenticationIntegrationTest 26/26, RegistrationOwnerIntegrationTest 23/23 and Fido2UiEventBusTest 5/5; the new late-presenter assertion opens `AwaitingUserConsent`. Android main/host KtLint checks passed.
- **date**: 2026-11-05
- **limitations**: This is host-side routing/retention evidence; it does not prove the Compose route launches on the ASUS or that Windows accepts an assertion. The candidate is not installed because of the signer mismatch documented in Record 111; T023 remains open.
- **status**: `Passed (focused host prompt routing and retained-state tests) / Unverified (ASUS prompt and Windows WebAuthn result)`.
- **artifact_link**: [Authentication integration XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.integration.AuthenticationIntegrationTest.xml), [registration owner XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.integration.RegistrationOwnerIntegrationTest.xml), [event bus XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.presentation.navigation.Fido2UiEventBusTest.xml)

### Record 108: Opt-In Reconnect Stress and Heap Baseline Capture (T035 — Partial)
- **requirement**: BLE repeated connection recovery and memory observation; `FR-BLE-060`, `FR-BLE-070`, `TR-BLE-030`.
- **scope**: Prepare a dedicated one-phone scanner test for 50 authenticated-peer GATT discovery/connect/disconnect cycles and retain Java/native heap snapshots before and after the cycle set.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` plus uncommitted stress-test changes.
- **named_procedure**: Compile Android device-test sources and run `:feature:fido2:ktlintAndroidDeviceTestSourceSetCheck`; the stress instrumentation itself is opt-in through `runFidoBleReconnectStress=true` and was not launched.
- **environment**: Windows 11; Gradle 9.6.0; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; no device test executed for this record.
- **expected_observed_result**: `:feature:fido2:compileAndroidDeviceTest` and `:feature:fido2:ktlintAndroidDeviceTestSourceSetCheck` completed successfully. The test logs Java heap and native heap bytes before and after the 50 cycles. Default connected suites skip the test unless explicitly enabled on one chosen scanner phone, with a separate FIDO BLE advertiser kept active.
- **date**: 2026-11-05
- **limitations**: This validates compilation and test hygiene only. No 50-cycle device run, retained-owner inspection, interrupted-request scenario, memory trend or no-leak conclusion was observed. Physical T035 remains open.
- **status**: `Passed (device-test source compilation and KtLint) / Unverified (physical reconnect cycles, recovery, and memory behavior)`.
- **artifact_link**: [Reconnect stress device test](../../feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/data/transport/FidoBleReconnectStressDeviceTest.kt), [opt-in procedure](quickstart.md#physical-procedures)

### Record 109: ASUS GetAssertion Retry Reached GATT and CTAP (T023 — Partial)
- **requirement**: BLE physical Windows GetAssertion and diagnostic timing; `FR-BLE-030`, `FR-BLE-050`.
- **scope**: Correlate a new WebAuthn.io Authenticate attempt with ASUS BLE setup and CTAP handler outcomes; this attempt is distinct from Record 104, which had no observed app-side GATT session.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7`; installed ASUS `shrunkDebug` APK SHA-256 `07f5c1e66b3166e7440a312fce94cc9bfde5edfbdb3278b4dbd83f0aa9917876`.
- **named_procedure**: Start Authenticate in the integrated WebAuthn.io browser for the synthetic account and correlate browser state, ASUS GATT and app logs.
- **environment**: Windows 11; ChatGPT integrated browser / WebAuthn.io; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; synthetic account `codex-synthetic-064-20261103`.
- **expected_observed_result**: ASUS connected over GATT, negotiated MTU 517, selected revision `0x20` and enabled notifications. One GetAssertion returned`NO_CREDENTIALS`; a subsequent request with one candidate completed internally and produced a response. Another request remained pending and logged a 120-second timeout. A later retry again produced an internal assertion response, then the link disconnected while another request was active. The user reported that Windows displayed “No se puede usar esta clave de seguridad. Prueba con otra distinta.” WebAuthn.io showed no login success, and a read-only ASUS screen capture showed Chimali on its Advertising home screen without an authentication prompt.
- **date**: 2026-11-05
- **limitations**: Internal CTAP response logs are not end-to-end proof. Browser completion, Windows response acceptance, biometric/prompt completion, notification delivery, diagnostic end-to-end timing, request correlation and root cause remain unverified. The single run does not establish the full BLE T023 ceremony requirement.
- **status**: `Failed (user-reported Windows rejection; no WebAuthn success) / Unverified (prompt flow, response delivery, end-to-end timing and failure cause)`.
- **artifact_link**: [ASUS assertion CTAP follow-up](../../feature/fido2/build/reports/fido2/064/asus-webauthn-assertion-ctap-followup-20261105.txt), [earlier no-GATT attempt](#record-104-asus-webauthn-getassertion-rejected-before-app-side-gatt-session-t023--partial), [BLE T023](tasks.md)

### Record 110: Retained Pending-Request Navigation State (T023 — Partial)
- **requirement**: Deliver inbound Windows GetAssertion requests to the visible authentication prompt; `FR-BLE-030`, `FR-BLE-050`.
- **scope**: Replace the home screen's startup snapshot plus non-replaying event-only navigation with observation of retained registration/authentication request StateFlows, and verify event-bus retention/cleanup on the JVM host.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted source and host-test changes.
- **named_procedure**: `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.presentation.navigation.Fido2UiEventBusTest --no-problems-report --no-configuration-cache`.
- **environment**: Windows 11; Gradle 9.6.0; worktree-local Gradle home `.gradle/codex-user-home`; Kotlin daemon marker access was denied, so compilation used Gradle's fallback strategy.
- **expected_observed_result**: Gradle completed successfully. XML reports 5 tests, zero skips, zero failures and zero errors, including the new assertion that pending authentication state is retained for a late presenter and cleared on completion.
- **date**: 2026-11-05
- **limitations**: This verifies event-bus state and host compilation, not Compose navigation on a device. The candidate has not been installed on ASUS and no post-fix Windows GetAssertion has succeeded. The hypothesized startup race remains unconfirmed; T023 stays open.
- **status**: `Passed (focused Android host tests) / Unverified (device prompt routing and Windows GetAssertion)`.
- **artifact_link**: [Focused host-test XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.presentation.navigation.Fido2UiEventBusTest.xml), [event bus](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/navigation/Fido2UiEventBus.kt), [home screen observer](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/Fido2HomeScreen.kt)

### Record 111: ASUS Candidate Build Rejected by Installed Signing Identity (T023 — Partial)
- **requirement**: Deploy the candidate to the ASUS device for a physical Windows GetAssertion retest; `FR-BLE-030`, `FR-BLE-050`.
- **scope**: Assemble the updated `shrunkDebug` candidate and attempt an in-place install on the ASUS without clearing app data.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7`; APK SHA-256 `C6E5582F00D3F297853312657906B1A65DD16ECBD31051423EC55109F6619417`.
- **named_procedure**: Run `:app:assembleShrunkDebug --no-problems-report --no-configuration-cache`, then `adb -s R7AIB7002901AZ3 install -r app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk`.
- **environment**: ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`.
- **expected_observed_result**: APK assembly passed. In-place installation was rejected with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; the installed app's signer SHA-256 is `f6c948b953eb7ec7153e95f0ae4830254c7b0f882cb741e54a18e21a2a3c7510`, while the candidate signer SHA-256 is `6bb5df3f4d4e68e75dac02e7f246a7dacf9ae87eb9e58ea79cfec5ca8a227410`.
- **date**: 2026-11-05
- **limitations**: No uninstall or data clear was performed, preserving the existing synthetic passkey. The candidate is not installed, so its pending-request navigation change has not been verified on device and a Windows ceremony was not repeated. T023 remains open.
- **status**: `Passed (candidate assembly) / Blocked (in-place device install due to signer mismatch) / Unverified (post-fix Windows ceremony)`.
- **artifact_link**: [Candidate APK](../../app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk), [focused host-test record](#record-110-retained-pending-request-navigation-state-t023--partial)

**Superseding deployment update (2026-11-05)**: Record 111 documents the first candidate's signer mismatch. A rebuild using the existing ASUS signing identity has since been installed in place; no app-data reset was performed. See Record 114.

### Record 112: Current Worktree Local CI Without Coverage (T038 — Partial)
- **requirement**: Local static analysis, configured host tests and source compilation; coverage assessment and accepted-target nonregression are separate requirements.
- **scope**: Revalidate the current worktree after pending-request navigation changes and reconnect-stress test preparation, explicitly omitting coverage.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes.
- **named_procedure**: `build/local-ci-no-problems.ps1 -NoFix -NoConfigurationCache`; no `-RunCoverage` argument.
- **environment**: Windows 11; Gradle 9.6.0; worktree-local Gradle home `.gradle/codex-user-home`.
- **expected_observed_result**: Local CI passed in 153.74 seconds: KtLint, Detekt, release lint, configured compilation, unit tests and KMP Android host tests. The reconnect stress test's Android device-test source compiled. Kotlin daemon marker access was denied and Gradle used compiler fallback.
- **date**: 2026-11-05
- **limitations**: No connected Android device test, coverage completeness/floors, trusted accepted-target nonregression or hosted integration was run. Coverage remains explicitly deferred by owner direction. Physical Windows ceremonies and lifecycle acceptance remain open.
- **status**: `Passed (ordinary local static, compile, unit and host checks) / Deferred (coverage by owner direction) / Unverified (device acceptance, trusted nonregression and hosted integration)`.
- **artifact_link**: [FIDO2 UiEventBus host-test XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.presentation.navigation.Fido2UiEventBusTest.xml), [reconnect stress test](../../feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/data/transport/FidoBleReconnectStressDeviceTest.kt)

### Record 114: ASUS In-Place Deployment and Post-Fix Retest Attempt (T023 — Partial)
- **requirement**: Deploy the retained-request routing correction and verify real Windows 11 GetAssertion with the preserved synthetic credential; `FR-BLE-030`, `FR-BLE-050`.
- **scope**: Rebuild with the signer matching the installed ASUS package, update without clearing app data, start BLE advertising, and attempt the integrated-browser authentication ceremony.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes; APK SHA-256 `892F0F7E5A2B4876BAAB1A62141EA0E4E9E5776A59E6B979E9705F78C9C2FF04`.
- **named_procedure**: Build `:app:assembleShrunkDebug` with worktree-local `GRADLE_USER_HOME` and `JAVA_TOOL_OPTIONS=-Duser.home=C:/Users/octav`; verify signer SHA-256 `f6c948b953eb7ec7153e95f0ae4830254c7b0f882cb741e54a18e21a2a3c7510`; run `adb -s R7AIB7002901AZ3 install -r`; launch Chimali, select Start Authenticator, and click Authenticate on the integrated WebAuthn.io page for the existing synthetic account.
- **environment**: Windows 11; integrated ChatGPT browser / WebAuthn.io; ASUS AI2302 / Android 15 / API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; synthetic account `codex-synthetic-064-20261103`.
- **expected_observed_result**: Build succeeded and the candidate certificate matched the installed package. `adb install -r` returned `Success`; the app data was preserved. The app was launched, Start Authenticator activated the FIDO BLE service, GATT server registration returned status 0, and the service entered foreground advertising. The integrated-page click caused no visible WebAuthn result; filtered ASUS logs showed no GATT connection or CTAP request after the attempt.
- **date**: 2026-11-05
- **limitations**: No Windows security prompt, GATT session, CTAP operation, Android authentication prompt or WebAuthn success was observed in this post-fix attempt. The automation click did not produce a visible WebAuthn activation or request. The candidate is installed and ready for a direct user click; T023 remains open.
- **status**: `Passed (matching-signer in-place deployment and BLE service start) / Unverified (post-fix Windows GetAssertion)`.
- **artifact_link**: [Installed candidate APK](../../app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk), [retained-request host tests](#record-113-prompt-viewmodels-observe-retained-requests-t023--partial), [integration handoff](../064-bluetooth-transport-selection/integration.md)

### Record 115: Post-Deployment 50-Cycle Mi-to-ASUS GATT Reconnect Stress (T035 — Partial)
- **requirement**: Verify repeated GATT reconnects remain clean after the latest candidate deployment, including memory observations; `FR-BLE-060`, `FR-BLE-070`, `TR-BLE-030`.
- **scope**: Run the opt-in scanner-side 50-cycle test from Mi/API29 against the updated ASUS/API35 FIDO BLE advertiser; inspect both pre/post heap values and ASUS service state.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes; latest ASUS candidate APK SHA-256 `892F0F7E5A2B4876BAAB1A62141EA0E4E9E5776A59E6B979E9705F78C9C2FF04`.
- **named_procedure**: Build with `:feature:fido2:assembleAndroidDeviceTest`, install `feature/fido2/build/outputs/apk/androidTest/fido2-androidTest.apk` on Mi, then run `adb -s 2ac6876 shell am instrument -w -e class com.chimali.fido2.data.transport.FidoBleReconnectStressDeviceTest -e runFidoBleReconnectStress true com.chimali.fido2.test/androidx.test.runner.AndroidJUnitRunner`.
- **environment**: Windows 11; Mi A2 / Android 10 / API29 scanner; ASUS AI2302 / Android 15 / API35 advertiser; ASUS package `com.chimali.shrunk` running the updated in-place candidate; test package `com.chimali.fido2.test`.
- **expected_observed_result**: Instrumentation returned `OK (1 test)` in 79.275 seconds. All 50 GATT connect, FIDO service-discovery, disconnect and close cycles passed. Mi Java heap was 1,943,384 bytes before and 2,900,328 bytes immediately after (+956,944); native heap was 6,046,992 bytes before and 7,283,928 bytes after (+1,236,936). ASUS GATT remained registered, transport foreground service stayed active, and the final server connection was disconnected.
- **date**: 2026-11-05
- **limitations**: This checks unauthenticated GATT discovery/reconnect only; it does not establish secured GATT, CTAP, pairing, request interruption, stale-bond recovery, cancellation or leak freedom. Memory samples are immediate process-wide values without forced collection. BLE T035 remains partial.
- **status**: `Passed (50 GATT discovery/reconnect cycles against the latest ASUS candidate) / Unverified (interrupted ceremonies, recovery matrix and leak acceptance)`.
- **artifact_link**: [Sanitized device output and heap samples](../../feature/fido2/build/reports/fido2/064/asus-postfix-reconnect-stress-20261105.txt), [device test source](../../feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/data/transport/FidoBleReconnectStressDeviceTest.kt)

### Record 116: Current Worktree Local CI Without Coverage (T038 — Partial)
- **requirement**: Validate static analysis, compilation, unit tests and KMP host tests while keeping the owner-deferred coverage/nonregression gate explicitly separate.
- **scope**: Run the repository local CI path without `-RunCoverage`; record the official script's report-output failure and the no-problems wrapper result.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes.
- **named_procedure**: First attempt `tools/local-ci.ps1 -NoFix -NoConfigurationCache`; then run `build/local-ci-no-problems.ps1 -NoFix -NoConfigurationCache` with worktree-local Gradle home. Do not pass `-RunCoverage`.
- **environment**: Windows 11; Gradle 9.6.0; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`.
- **expected_observed_result**: KtLint tasks reached `UP-TO-DATE`, but the official script's Gradle invocation exited nonzero after `AccessDeniedException` while writing `build/reports/problems/problems-report.html`; no source lint error was reported. The no-problems wrapper passed KtLint, Detekt, release lint, comprehensive source/test compilation, unit tests and KMP Android host tests in 63.38 seconds; most tasks were up-to-date.
- **date**: 2026-11-05
- **limitations**: The wrapper only works around Gradle's problems-report output permission. Coverage completeness/floors, connected device acceptance and trusted candidate-versus-accepted-target nonregression were not run. Coverage remains explicitly deferred by owner direction; hosted integration remains waived/unverified.
- **status**: `Passed (ordinary local static/compile/unit/host validation via workaround) / Failed (official script report output) / Deferred (coverage by owner direction) / Unverified (trusted target comparison and physical acceptance)`.
- **artifact_link**: [Local CI result and failure details](../../feature/fido2/build/reports/fido2/064/local-ci-no-coverage-20261105.txt), [local CI helper](../../build/local-ci-no-problems.ps1), [current coverage plan](../058-enforce-coverage-targets/plan.md)

### Record 117: BLE Request-to-Response-Notification Diagnostic (T023 — Partial)
- **requirement**: Capture physical GetAssertion timing with an explicit response-handoff boundary; `FR-BLE-030`, `FR-BLE-050`, `TR-BLE-020`.
- **scope**: Add a sanitized diagnostic window from a completely reassembled FIDO BLE CTAP request through Android's acknowledgement callback for the final response notification. Log only the CTAP operation label, elapsed milliseconds and notification acknowledgement result; never log request, credential or response bytes.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted diagnostic and test changes.
- **named_procedure**: Run `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.transport.BleNotificationWriterTest :feature:fido2:ktlintAndroidMainSourceSetCheck :feature:fido2:ktlintAndroidHostTestSourceSetCheck --no-problems-report --no-configuration-cache`.
- **environment**: Windows 11; Gradle 9.6.0; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; worktree-local Gradle user home; Android hardware not used for this focused run.
- **expected_observed_result**: Build succeeded in 29 seconds. The FIDO2 notification-writer suite reports 10 tests, zero failures, zero errors and zero skips; both source-set KtLint checks passed. New assertions cover a successful GATT acknowledgement and a terminal-response callback failed by clearing the queue.
- **date**: 2026-11-05
- **limitations**: This is host evidence for the diagnostic callback only. No new physical Windows GetAssertion or timing sample was captured. An Android notification-sent callback proves local Android GATT handoff only; it cannot establish Windows receipt, CTAP acceptance or browser success. The recent user-reported Windows message is not correlated with an app-side request in the available device logs. T023 remains open.
- **status**: `Passed (diagnostic callback implementation, focused host tests and KtLint) / Unverified (physical GetAssertion, real timing and Windows response acceptance)`.
- **artifact_link**: [Notification-writer test XML](../../feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.transport.BleNotificationWriterTest.xml), [BLE transport diagnostic](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleGattTransport.kt), [notification writer](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleNotificationWriter.kt)

### Record 118: Worktree Local CI After BLE Diagnostic Changes (T038 — Partial)
- **requirement**: Validate source checks, compilation and ordinary tests without running the explicitly deferred coverage gate.
- **scope**: Run the repository's no-problems local CI wrapper after adding BLE request-to-response notification timing and callback tests.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes.
- **named_procedure**: `build/local-ci-no-problems.ps1 -NoFix -NoConfigurationCache` using worktree-local Gradle home and `JAVA_TOOL_OPTIONS=-Duser.home=C:/Users/octav`.
- **environment**: Windows 11; Gradle 9.6.0; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`.
- **expected_observed_result**: KtLint, Detekt, release lint, source/test compilation, unit tests and KMP Android host tests passed in 195.49 seconds. The Kotlin daemon emitted AccessDeniedException marker warnings; compiler fallback completed successfully.
- **date**: 2026-11-05
- **limitations**: Coverage was not run by user direction. This does not establish connected-device acceptance, coverage floors, candidate-versus-accepted-target nonregression or hosted integration.
- **status**: `Passed (ordinary local static/compile/unit/host validation) / Deferred (coverage by owner direction) / Unverified (physical acceptance and trusted target comparison)`.
- **artifact_link**: [Local CI helper](../../build/local-ci-no-problems.ps1), [coverage plan](../058-enforce-coverage-targets/plan.md)

### Record 119: Mi/API29 Corrected Build Deployment and BLE Advertising (T023 — Partial)
- **requirement**: Verify corrected FIDO BLE transport startup on the available Mi device; `FR-BLE-010`, `FR-BLE-020`, `TR-BLE-010`.
- **scope**: Install the candidate with the Android debug signing key matching the previously tested package identity, launch Chimali, grant location while using the app, and start the authenticator.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes; APK SHA-256 `3F7D2397678637CDDE4544DDD7943F66B22A12CE8218D12A0E7CA4FC655C78A3`.
- **named_procedure**: Verify candidate and installed signer certificates, uninstall/reinstall only `com.chimali.shrunk` under the user's prior explicit Mi reinstall authorization, launch the app, choose the FIDO2 feature, start the authenticator, then inspect UI and Android Bluetooth logs.
- **environment**: Windows 11; Xiaomi Mi A2 (`jasmine`), Android 10/API29; ADB serial `2ac6876`; package `com.chimali.shrunk` version `0.1.0`.
- **expected_observed_result**: Install and launch succeeded. The foreground `Fido2TransportService` started; Android registered the GATT server and FIDO (`0000fffd-0000-1000-8000-00805f9b34fb`) and Device Information (`0000180a-0000-1000-8000-00805f9b34fb`) services with status 0. Advertising-set start callback returned status 0, and UI displayed “Advertising… Ready to pair with a new host PC.”
- **date**: 2026-11-05
- **limitations**: Reinstall erased this app's prior Mi wallet, passkeys and settings. This confirms advertising only; Windows pairing/connection, CTAP, WebAuthn ceremony and user-verification behavior, lifecycle recovery and full physical acceptance remain unverified. Coverage remains deferred by owner direction.
- **status**: `Passed (Mi/API29 installation, launch, GATT service registration and advertising) / Unverified (Windows ceremony, user verification and lifecycle acceptance)`.
- **artifact_link**: [Sanitized Mi deployment evidence](../../feature/fido2/build/reports/fido2/064/mi-deployment-20261105.txt), [BLE transport implementation](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleGattTransport.kt)

### Record 120: Integrated-Browser Registration Attempt on Mi (T023 — Unverified)
- **requirement**: Exercise a real Windows-hosted WebAuthn registration against Mi/API29 over FIDO BLE; `FR-BLE-030`, `FR-BLE-040`, `TR-BLE-020`.
- **scope**: With Mi advertising, click Register in the existing integrated WebAuthn.io tab using the authorized synthetic test account, then inspect the page, Windows open-window inventory and Mi logs.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes; Mi candidate APK SHA-256 `3F7D2397678637CDDE4544DDD7943F66B22A12CE8218D12A0E7CA4FC655C78A3`.
- **named_procedure**: Start Authenticator on Mi, click Register in the integrated WebAuthn.io tab, and inspect updated browser state, Windows dialogs and fresh Mi GATT/CTAP log activity.
- **environment**: Windows 11; Xiaomi Mi A2 / Android 10/API29; integrated WebAuthn.io tab; synthetic account `codex-synthetic-064-20261103`.
- **expected_observed_result**: The click did not open a Windows security-key prompt or produce a visible page result. The Mi log contained no new GATT connection or CTAP request after advertising started. No registration ceremony completed.
- **date**: 2026-11-05
- **limitations**: This does not establish a Mi transport failure because the browser did not initiate an observable host request; user-activation/browser routing remains unresolved. No new credential was created. BLE T023 remains open.
- **status**: `Failed (attempt did not launch the ceremony) / Unverified (Mi Windows registration and root cause)`.
- **artifact_link**: [Mi advertising and deployment evidence](../../feature/fido2/build/reports/fido2/064/mi-deployment-20261105.txt), [integration plan](../064-bluetooth-transport-selection/integration.md)

### Record 121: API 29 Pattern Availability and FIDO Prompt Configuration (T036 — Partial)
- **requirement**: Verify that Mi's configured device credential is recognized for FIDO user verification on API 29; `FR-AUTH-010`, `FR-BLE-030`.
- **scope**: Run the focused Android instrumentation class against Mi/API29 to compare Chimali availability with `KeyguardManager.isDeviceSecure` and construct the FIDO prompt configuration.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes.
- **named_procedure**: `:feature:fido2:connectedAndroidDeviceTest --no-configuration-cache --no-problems-report --max-workers=1 --project-prop=android.testInstrumentationRunnerArguments.class=com.chimali.fido2.domain.service.impl.UserVerificationServiceDeviceTest`, with `ANDROID_SERIAL=2ac6876`.
- **environment**: Windows 11; Xiaomi Mi A2, Android 10/API29; Gradle 9.6.0; worktree-local Gradle home.
- **expected_observed_result**: Device instrumentation XML reports 2 tests, 0 failures, 0 errors and 0 skipped: secure-device-credential availability matches the platform, and FIDO `BiometricPrompt.PromptInfo` configuration builds on API29.
- **date**: 2026-11-05
- **limitations**: The test does not display the prompt, ask for the pattern, prove a user-entered verification, or complete CTAP/WebAuthn registration. T023 remains open.
- **status**: `Passed (pattern-backed availability and API29 prompt configuration) / Unverified (actual prompt acceptance and WebAuthn ceremony)`.
- **artifact_link**: [Mi API29 instrumentation XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi%20A2%20-%2010.xml), [device test](../../feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/domain/service/impl/UserVerificationServiceDeviceTest.kt), [prompt builder](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/UserVerificationPromptInfo.kt)

### Record 122: Mi/API29 BLE Force-Stop and Explicit Restart (T035 — Partial)
- **requirement**: Verify that a force-stopped BLE authenticator releases transport and remains stopped until an explicit user restart; `FR-BLE-060`, `FR-BLE-070`, `TR-BLE-030`.
- **scope**: Force-stop the deployed Mi app, relaunch its actual `MainActivity`, confirm no transport service/advertising, then tap Start Authenticator and inspect GATT/advertising callbacks.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes; deployed candidate signer SHA-256 `f6c948b953eb7ec7153e95f0ae4830254c7b0f882cb741e54a18e21a2a3c7510`.
- **named_procedure**: Run `adb -s 2ac6876 shell am force-stop com.chimali.shrunk`; relaunch explicitly with `adb -s 2ac6876 shell am start -n com.chimali.shrunk/com.chimali.MainActivity`; inspect PID/service/UI; tap Start and inspect GATT/advertising logs.
- **environment**: Windows 11; Xiaomi Mi A2 / Android 10/API29; ADB serial `2ac6876`.
- **expected_observed_result**: Force-stop removed the process and foreground transport service. Explicit `MainActivity` relaunch showed Ready to Start with no transport service. Tapping Start registered FIDO and Device Information services and started advertising with status 0. `monkey` selected the LeakCanary launcher, so the real activity was started explicitly. No app data was cleared.
- **date**: 2026-11-05
- **limitations**: This is one force-stop/relaunch cycle. OS low-memory death, reboot, mid-ceremony interruption, stale bonds, cancellation, Classic mode and the full parent lifecycle matrix remain open. A Bluetooth `svc disable` attempt exited 137 while the adapter remained ON and is not a radio-off pass.
- **status**: `Passed (Mi BLE force-stop and explicit user-restart cycle) / Unverified (remaining lifecycle and recovery matrix)`.
- **artifact_link**: [Sanitized Mi restart evidence](../../feature/fido2/build/reports/fido2/064/mi-force-stop-restart-20261105.txt), [integration plan](../064-bluetooth-transport-selection/integration.md)

### Record 123: Mi/API29 BLE Background Service Persistence (T029/T035 — Partial)
- **requirement**: Verify the BLE foreground service and its persistent notification survive backgrounding; `FR-BLE-060`, `FR-BLE-070`, `TR-BLE-030`.
- **scope**: Start Mi advertising, send the app to the background, inspect process/service/notification state, then bring MainActivity forward again.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes.
- **named_procedure**: Start authenticator; send `KEYCODE_HOME`; inspect `pidof`, `dumpsys activity services`, `dumpsys notification`; explicitly bring `com.chimali.shrunk/com.chimali.MainActivity` to foreground.
- **environment**: Xiaomi Mi A2 / Android 10/API29; ADB serial `2ac6876`.
- **expected_observed_result**: After backgrounding, the app process remained present and `Fido2TransportService` remained foreground with notification ID 1001 in `fido2_transport_channel`; the notification included a Stop action. Bringing MainActivity forward returned to Advertising and the service remained active.
- **date**: 2026-11-05
- **limitations**: No peer scan directly proved uninterrupted radio discovery; this does not cover OS low-memory death, reboot, Classic, in-flight ceremony, or the complete T035 matrix.
- **status**: `Passed (Mi/API29 foreground-service and notification persistence while backgrounded) / Unverified (remote discovery and remaining lifecycle acceptance)`.
- **artifact_link**: [Sanitized Mi background evidence](../../feature/fido2/build/reports/fido2/064/mi-background-service-20261105.txt), [BLE lifecycle source](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/service/Fido2TransportService.kt)

### Record 124: Cross-Platform Required-UV Windows Credential Chooser (T023 — In Progress)
- **requirement**: Start a real Windows-hosted FIDO BLE MakeCredential with user verification required and record the response handoff; `FR-BLE-030`, `FR-BLE-040`, `TR-BLE-020`.
- **scope**: Configure WebAuthn.io with `userVerification=required`, `attachment=cross_platform`, `security-key` registration hint, `attestation=none`, and the synthetic test account; click Register while Mi/API29 advertises.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes; deployed Mi candidate remains the signer-matched build recorded in Record 119.
- **named_procedure**: In the URL-verified Codex integrated browser tab, select required UV and cross-platform attachment, enable the security-key hint, click Register, inspect Windows windows and Mi GATT/CTAP logs.
- **environment**: Windows 11; Xiaomi Mi A2 / Android 10/API29; WebAuthn.io; synthetic account `codex-synthetic-064-20261103`.
- **expected_observed_result**: Clicking Register opened a native Windows “Guardar la clave de paso” dialog for the synthetic account. It defaulted to saving on Windows and offered “Cambiar”; the user has been asked to select Cambiar → Security key. Before provider selection, Mi logged no new GATT connection/write or MakeCredential/CTAP event, and no credential was created.
- **date**: 2026-11-05
- **limitations**: Windows provider selection and a user-entered Mi pattern remain pending; no authenticator request has been observed yet. This is not a transport or pattern-prompt failure. T023 remains open.
- **status**: `In Progress (native Windows credential chooser opened; waiting for cross-platform security-key selection) / Unverified (Mi GATT, CTAP and user verification)`.
- **artifact_link**: [Sanitized required-UV attempt evidence](../../feature/fido2/build/reports/fido2/064/mi-webauthn-required-uv-attempt-20261105.txt), [integration plan](../064-bluetooth-transport-selection/integration.md)
- **follow_up_observation**: A later ADB snapshot still showed the Mi app Advertising with `Fido2TransportService` foreground and Bluetooth ON. `dumpsys bluetooth_manager` reported no connected or bonded devices. No GATT or CTAP activity appeared; stale-bond recovery cannot be exercised until pairing creates a bond.
- **browser_recheck**: A current integrated-browser snapshot still showed registration UV Required, Cross-Platform attachment and the Security Key hint, but WebAuthn.io displayed its prior no-focus message. After focusing the page heading, CUA reported the WebArea focused; the prior message remained in page content. Windows' enumerated window list did not show a separate credential chooser. No new registration request was submitted.

### Record 125: Mi/API29 Controlled Process-Death BLE Recovery (T035 — Partial)
- **requirement**: Verify the selected BLE foreground service recovers after Android-managed app process death without switching transport or clearing wallet data; `FR-BLE-060`, `FR-BLE-070`, `TR-BLE-030`.
- **scope**: While Mi was advertising BLE with no active host ceremony, induce a shell-controlled process crash, observe ActivityManager's sticky-service restart, then reopen MainActivity and inspect service, GATT registration, advertising and selected-mode UI.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` with uncommitted changes; deployed build matches Record 119.
- **named_procedure**: Run `adb -s 2ac6876 shell am crash com.chimali.shrunk`; inspect `logcat`, process and `dumpsys activity services`; explicitly launch `com.chimali.shrunk/com.chimali.MainActivity`; inspect UI and transport callbacks.
- **environment**: Xiaomi Mi A2 (`jasmine`), Android 10/API29; ADB serial `2ac6876`; Bluetooth adapter ON; BLE selected; no active ceremony.
- **expected_observed_result**: The shell-induced `RemoteServiceException` terminated PID 11964. ActivityManager scheduled `Fido2TransportService` to restart after 1,000ms and started PID 13001. The restarted service registered the FIDO and Device Information GATT services with status 0, and Android's advertising-set callback returned status 0. Explicit MainActivity launch displayed `Advertising...` / `Ready to pair with a new host PC`; the service was foreground. No app data was cleared.
- **date**: 2026-11-05
- **limitations**: This is a controlled shell-induced crash, not organic low-memory termination, reboot, permission revocation, pending transport change or an in-flight ceremony. No remote Windows discovery or ceremony was observed. Parent T035, BLE T035 and full T041 remain open.
- **status**: `Passed (Mi/API29 controlled process-death sticky-service restart and BLE re-advertising) / Unverified (organic death, interruption matrix, remote host and full lifecycle acceptance)`.
- **artifact_link**: [Sanitized Mi controlled process-death log](../../feature/fido2/build/reports/fido2/064/mi-controlled-process-death-20261105.txt), [BLE service implementation](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/service/Fido2TransportService.kt)

### Record 126: Mi/API29 Fresh Install and BLE Advertising Recovery (T029 — Partial)
- **requirement**: Start the clean-install default FIDO BLE transport and verify the foreground lifecycle and GATT server registration; `FR-BLE-010`, `FR-BLE-060`, `FR-BLE-070`, `TR-BLE-010`, `TR-BLE-030`.
- **scope**: Reinstall the authorized `com.chimali.shrunk` candidate, complete Passkey Authenticator onboarding on Mi/API29, explicitly start the authenticator, and inspect the UI, service and Android GATT server map.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD recorded in the linked sanitized report, with staged and unstaged changes.
- **named_procedure**: `adb uninstall com.chimali.shrunk`; `adb install app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk`; grant the previously authorized foreground location permission; select Passkey Authenticator in onboarding; tap Start Authenticator; inspect `dumpsys activity services`, `dumpsys bluetooth_manager` and the UI hierarchy.
- **environment**: Windows 11; Xiaomi Mi A2 (`jasmine`), Android 10/API29; ADB serial `2ac6876`; `shrunkDebug` version 0.1.0.
- **expected_observed_result**: MainActivity displayed `Advertising...` and `Ready to pair with a new host PC`; `Fido2TransportService` was foreground with notification ID 1001; Android's GATT Server Map listed `com.chimali.shrunk (Registered)`. Bluetooth was ON. The separate `com.chimali` package was untouched.
- **date**: 2026-11-05
- **limitations**: No remote Windows discovery, authenticated pairing, GATT request, CTAP operation, user verification, registration, assertion or timing measurement occurred. This does not close T020, T023 or T035. Coverage remains owner-deferred.
- **status**: `Passed (Mi/API29 fresh install, onboarding, foreground service and GATT registration) / Unverified (remote host, ceremony and remaining lifecycle matrix)`.
- **artifact_link**: [Sanitized Mi clean-install and advertising evidence](../../feature/fido2/build/reports/fido2/064/mi-clean-install-advertising-20261105.txt), [transport service](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/service/Fido2TransportService.kt)

### Record 127: Mi/API29 Required-UV WebAuthn Attempt Ended by Host Disconnect (T023 — Partial)
- **requirement**: Observe a Windows-hosted FIDO BLE MakeCredential with required user verification and determine whether the Mi request reaches a terminal response; `FR-BLE-030`, `FR-BLE-040`, `TR-BLE-020`.
- **scope**: Start one synthetic-account registration in the integrated WebAuthn.io page with required UV and cross-platform attachment while Mi advertises BLE; observe browser, Windows dialog and sanitized Android request lifecycle.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; deployed Mi build remains the signer-matched candidate from Record 119; source has staged and unstaged changes.
- **named_procedure**: Click Register for `codex-synthetic-064-20261103`; user selects the Windows security-key path and handles all credential/pattern prompts; inspect ADB logs and WebAuthn.io result without controlling the native authentication dialog.
- **environment**: Windows 11; Codex integrated browser; Xiaomi Mi A2 / Android 10/API29; `com.chimali.shrunk`.
- **expected_observed_result**: Windows displayed “La conversación con la clave de seguridad de Bluetooth está en curso.” Mi logs showed GATT connection, GetInfo/GetAssertion exchanges and admission of MakeCredential (`credProtect policy=2`). Repeated overlapping CMD_MSG packets were rejected while the operation remained active. The user closed the Windows dialog because it blocked interaction with Codex; Mi then logged cancellation of the in-flight CTAP command, and WebAuthn.io reported timeout/not-allowed. No successful registration was observed.
- **date**: 2026-11-05
- **limitations**: User was away from the Mi and did not complete or observe a pattern prompt. This run proves request admission and cancellation on disconnect only; it does not establish whether Mi displayed system verification, whether the credential was created, or that the repeated CMD_MSG traffic caused the timeout. T023 and physical verification remain open.
- **status**: `Passed (GATT/CTAP MakeCredential request admission observed) / Failed (WebAuthn ceremony timed out after dialog closure) / Unverified (Mi pattern prompt, credential persistence and successful response handoff)`.
- **artifact_link**: [BLE transport request lifecycle](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleGattTransport.kt), [integration plan](../064-bluetooth-transport-selection/integration.md)

### Record 128: Periodic FIDO BLE Keepalive Source Correction (T019 — Implementation Follow-up)
- **requirement**: Emit BLE `CMD_KEEPALIVE` notifications no more than 500ms apart while processing or awaiting user presence, then stop before terminal response delivery; feature `FR-015`, canonical `FR-BLE-060` and `TR-BLE-020`.
- **scope**: Trace the actual CTAP handler callbacks and response-writer phase through `BleGattTransport`; correct periodic delivery and ensure the writer reopens the keepalive phase after terminal notification acknowledgment for the next transaction.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` plus current staged and unstaged changes; correction is not deployed.
- **named_procedure**: Compare `spec.md` FR-015 and task T019 with `BleGattTransport.processCtapMessage`; run the focused virtual-time regressions for cadence, PROCESSING/UP_NEEDED status, stop-before-response, and keepalive resumption on a later request with `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.transport.FidoBleAssertionTest`.
- **environment**: Offline source review in the Windows worktree; Mi device was not operated during this correction.
- **expected_observed_result**: Source starts a periodic keepalive after 100ms, enqueues current PROCESSING/UP_NEEDED status every 250ms while the session is current, cancels/joins that job before terminal response queuing, and resets response suppression only after the current session's terminal notification is acknowledged. The focused host-test class passed 6/6 with zero failures, errors or skips, including cadence, status transition, stop-before-response and a subsequent request that emits keepalives.
- **date**: 2026-11-05
- **limitations**: The correction is not installed on Mi. The preceding timeout followed a user-closed Windows dialog; it does not prove the missing cadence or response-phase leak caused that timeout. Physical keepalive delivery, user verification and successful Windows registration remain unverified.
- **status**: `Passed (focused Android host test) / Implemented (source correction) / Unverified (deployed keepalive delivery and Windows ceremony)`.
- **artifact_link**: [BLE transport](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleGattTransport.kt), [keepalive regression test](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/FidoBleAssertionTest.kt), [task T019](tasks.md)

### Record 129: BLE Outbound Queue Overflow Cleanup (T013/T031 — Source Follow-up)
- **requirement**: A full bounded notification queue must not silently discard retained protocol work; failed sends and timeout must abort/fence the affected session and erase owned queue buffers; `FR-BLE-050`, `TR-BLE-020`, `TR-BLE-040`.
- **scope**: Correct overflow handling in `BleNotificationWriter`, include in-flight bytes in the total owned-buffer limit, and cancel/erase sender-owned buffers on cleanup before running the queue-ownership/stall suite plus BLE assertion regressions.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` plus current staged and unstaged changes.
- **named_procedure**: Run `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.transport.BleNotificationWriterTest --tests com.chimali.fido2.data.transport.FidoBleAssertionTest --no-configuration-cache --no-problems-report`, followed by Android main and host-test KtLint checks.
- **environment**: Windows 11; Gradle 9.6.0; worktree-local Gradle user home; Android host-test JVM. Mi was not operated.
- **expected_observed_result**: The total outstanding byte limit includes in-flight and queued packet storage. Capacity overflow clears and fences the active writer session and invokes stalled-session cleanup; the incoming notification is rejected. Cleanup cancels an unfinished send coroutine, whose `finally` erases its borrowed bytes after release. `BleNotificationWriterTest` passed 12/12 and `FidoBleAssertionTest` passed 6/6, with zero failures, errors or skips. Android main and host-test KtLint checks passed.
- **date**: 2026-11-05
- **limitations**: Host tests do not validate Android GATT queue pressure or remote peer behavior. The correction has not been deployed; physical BLE recovery and Windows ceremonies remain unverified.
- **status**: `Passed (18 focused host tests and affected source-set KtLint) / Implemented (total-byte bound and overflow cleanup) / Unverified (physical queue-pressure recovery)`.
- **artifact_link**: [notification writer](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleNotificationWriter.kt), [writer regression tests](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/BleNotificationWriterTest.kt), [assertion regression tests](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/FidoBleAssertionTest.kt)

### Record 130: Android Notification Buffer Ownership on API 28–32 (T013/T031 — Source Follow-up)
- **requirement**: Do not mutate notification bytes while the Android GATT stack owns them; erase owned copies after notification completion or server teardown, and release buffers in send-failure/cancellation paths; `FR-BLE-050`, `TR-BLE-020`, `TR-BLE-040`.
- **scope**: Address legacy `BluetoothGattServer.notifyCharacteristicChanged(device, characteristic, confirm)` buffer ownership on API 28–32 while preserving the explicit value overload on API 33+.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` plus current staged and unstaged changes.
- **named_procedure**: Review the official `BluetoothGattServer` API warning that the deprecated overload is not memory-safe; copy the submitted value for legacy calls, retain it until `onNotificationSent`, wipe on callback/server close, and run the focused writer/assertion host suites and Android main/host-test KtLint checks.
- **environment**: Windows 11; Gradle 9.6.0; API28+ compile configuration; Android host-test JVM. No Android device was operated.
- **expected_observed_result**: API33+ sends through the explicit value overload. API28–32 stores an owned copy until notification callback and clears it after completion; server close also clears any pending copy. Notification initiation now wipes the writer-owned buffer in `finally`, and stalled-session cleanup stops/closes the GATT server. The latest `BleNotificationWriterTest` passed 12/12 and `FidoBleAssertionTest` passed 6/6, zero failures/errors/skips; affected KtLint checks passed.
- **date**: 2026-11-05
- **limitations**: Host tests verify writer borrowing and API code compiles, but they do not exercise real Android framework buffer behavior, callback timing, or Mi/API29 delivery. Physical compatibility and ceremony outcomes remain unverified.
- **status**: `Passed (18 focused host tests, Android main compilation and affected KtLint) / Implemented (API-specific buffer ownership) / Unverified (physical legacy-API delivery)`.
- **artifact_link**: [Android GATT host](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/AndroidFidoBleGattServerHost.kt), [notification writer](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleNotificationWriter.kt), [BluetoothGattServer API reference](https://developer.android.com/reference/android/bluetooth/BluetoothGattServer)

### Record 131: Explicit BLE Framing Errors and Recovery (T030/T032 — Source Follow-up)
- **requirement**: Reply with the specified BLE `CMD_ERROR` for malformed frames and expired partial requests, erase invalid assembly state, and accept only bytes within declared message bounds; `FR-BLE-050`, `TR-BLE-020`.
- **scope**: Reuse the bounded `FidoBleFraming.RequestAssembler` for parsing; add typed incoming-write outcomes, route current-session errors to `CMD_ERROR`, enforce the session receive deadline, erase partial buffers on failure/replacement/unsubscription, and preserve clean transaction recovery.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` plus staged and unstaged corrections.
- **named_procedure**: Run the focused `BleSessionOwnerTest`, `FidoBleRecoveryTest` and `FidoBleCancellationTest` Android host suites, then run the full `:feature:fido2:testAndroidHostTest` suite and Android main/host-test KtLint checks.
- **environment**: Windows 11; Gradle 9.6.0; Android host-test JVM. Mi was not operated.
- **expected_observed_result**: Session-owner tests asser`NotReady`, `InProgress`, `Complete` and explicit error outcomes. Short headers, empty/oversized frames, extra initialization/continuation bytes, out-of-order continuations, unexpected continuations and expired fragments map to typed BLE errors; transport emits the corresponding `CMD_ERROR`. The receive deadline expires at exactly 3000ms (2999ms remains valid); disabling CCCD erases partial request state. After replacing the duplicate parser with the reusable bounded assembler, the full FIDO2 Android host suite passed 1,146/1,146 with zero failures, errors or skips; Detekt and both affected KtLint checks passed.
- **date**: 2026-11-05
- **limitations**: This is source and host-test evidence only. Physical malformed-frame behavior, Android callback ordering and Windows interoperability remain unverified; T023 and T035 remain open.
- **status**: `Passed (full FIDO2 host suite 1,146/1,146, Detekt and affected KtLint) / Implemented (shared BLE framing parser, explicit errors, exact receive deadline and CCCD cleanup) / Unverified (physical peer behavior)`.
- **artifact_link**: [session owner](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleSessionOwner.kt), [transport error routing](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleGattTransport.kt), [recovery tests](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/FidoBleRecoveryTest.kt), [full host-suite report](../../feature/fido2/build/reports/tests/testAndroidHostTest/index.html)

### Record 132: FIDO BLE Error Response Frame Vector (T007/T032 — Source Follow-up)
- **requirement**: Encode BLE `ERROR` as a response status, two-byte big-endian payload length and one-byte error payload; `FR-BLE-050`, `TR-BLE-020`.
- **scope**: Correct the reusable `encodeErrorFrame` helper and its byte-vector test; the production transport already used the full response fragmenter.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` plus staged and unstaged corrections.
- **named_procedure**: Compare CTAP 2.2 BLE §11.4.4.2–3 with `encodeErrorFrame`; run `FidoBleFramingTest` and Android main/host-test KtLint checks.
- **environment**: Windows 11; Gradle 9.6.0; Android host-test JVM. No device was operated.
- **expected_observed_result**: The encoded timeout error is `[0xBF, 0x00, 0x01, 0x05]`, representing response status, payload length 1 and error code. `FidoBleFramingTest` passed; Android main and host-test KtLint passed.
- **date**: 2026-11-05
- **limitations**: The transport's runtime error path had already used `fragmentResponse`; this correction fixes the reusable helper/vector only. No physical peer interoperability was tested.
- **status**: `Passed (framing vector and affected source-set KtLint) / Implemented (correct error response encoder) / Unverified (physical peer behavior)`.
- **artifact_link**: [framing encoder](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/FidoBleFraming.kt), [framing vector tests](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/FidoBleFramingTest.kt), [FIDO BLE contract](contracts/fido-ble.md)

### Record 133: Truthful BLE Write and Notification Capacities (T011/T017 — Source Follow-up)
- **requirement**: Report Control Point capacity as `min(512, ATT_MTU-3, platform write capacity)` without exceeding a 20-byte minimum; report notification capacity as `min(ATT_MTU-3, platform notification capacity)`; `FR-BLE-020`, `TR-BLE-020`.
- **scope**: Remove capacity clamping that could claim bytes unsupported by Android, reject unsupported write/max-message configurations and ensure notifications can carry a complete BLE error response.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` plus staged and unstaged corrections.
- **named_procedure**: Run `BleSessionOwnerTest` and `FidoBleFramingTest`, then the full `:feature:fido2:testAndroidHostTest` suite and Android main/host-test KtLint checks.
- **environment**: Windows 11; Gradle 9.6.0; Android host-test JVM. No device was operated.
- **expected_observed_result**: Host coverage asserts exact platform-limited notification capacity, rejects write capacity below 20 and validates `maxMsgSize` within 1–65535. Focused tests passed, then the full suite passed 1,146/1,146 with zero failures, errors or skips; KtLint passed.
- **date**: 2026-11-05
- **limitations**: These tests use configured limits, not OEM-reported limits from physical Android hardware. Physical notification throughput remains unverified.
- **status**: `Passed (full FIDO2 host suite 1,146/1,146 and affected KtLint) / Implemented (truthful supported capacities and constructor validation) / Unverified (physical platform limits)`.
- **artifact_link**: [session owner](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleSessionOwner.kt), [capacity tests](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/BleSessionOwnerTest.kt), [data model](data-model.md)

### Record 134: Full Local CI After BLE Contract Corrections (T036/T038 — Local Verification)
- **requirement**: Run repository static analysis, lint, compilation, unit tests and configured host tests after BLE framing/capacity changes; keep coverage gate separate and owner-deferred.
- **scope**: Execute the worktree's no-problems-report local CI wrapper with `-NoFix -NoConfigurationCache`, without its opt-in `-RunCoverage` flag.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` plus staged/unstaged changes.
- **named_procedure**: `.\build\local-ci-no-problems.ps1 -NoFix -NoConfigurationCache`.
- **environment**: Windows 11; Gradle 9.6.0; Android SDK; local checkout; coverage omitted under the owner's prior direction.
- **expected_observed_result**: KtLint, Detekt, release lint, comprehensive compile, unit tests and configured common/security/domain/FIDO2 Android host suites all passed in 90.44 seconds. No connected device tests or coverage/nonregression comparison ran.
- **date**: 2026-11-05
- **limitations**: This does not satisfy coverage floors, trusted baseline comparison or physical interoperability. BLE T023/T035, parent physical gates and T038/T039 remain open.
- **status**: `Passed (ordinary local CI without coverage) / Unverified (coverage, trusted target comparison and physical acceptance)`.
- **artifact_link**: [CI wrapper](../../build/local-ci-no-problems.ps1), [framing contract corrections](contracts/fido-ble.md), [parent integration plan](../064-bluetooth-transport-selection/integration.md)

### Record 135: Autonomous BLE Receive Timeout (T030/T032 — Source Follow-up)
- **requirement**: Expire an incomplete request after three seconds even if the peer sends no further packet, clear partial state, and send a timeout error; `FR-BLE-050`, `TR-BLE-020`.
- **scope**: Schedule a transport timeout for each accepted partial fragment. Fence callbacks with both session generation and assembly token so stale timers cannot expire a refreshed request or successor session. Add host coverage for autonomous expiry and stale-token rejection.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `373884950af81efa401c39671343b6af5387aee7` plus staged and unstaged changes.
- **named_procedure**: Compile Android main, host-test and device-test Kotlin sources; run affected Android main/host-test/device-test KtLint checks and `:feature:fido2:detekt`; execute `:feature:fido2:testAndroidHostTest`.
- **environment**: Windows 11; Gradle 9.6.0; local worktree Gradle cache at `.tmp/gradle-home`. Mi was not operated.
- **expected_observed_result**: Android main, host-test and device-test compilation completed with Gradle's in-process fallback after Kotlin daemon marker access was denied; all three affected KtLint checks and Detekt passed. The full FIDO2 Android host suite passed 1,148/1,148 with zero failures, errors or skips. `git diff --check` passed.
- **date**: 2026-11-05
- **limitations**: Host verification does not prove Android callback timing on OEM hardware or Windows interoperability. Physical timeout and Windows acceptance remain open.
- **status**: `Passed (Android main/host/device-test compile, affected KtLint, Detekt, full FIDO2 host suite 1,148/1,148) / Implemented (scheduled timeout with session/assembly fencing) / Unverified (physical peer behavior)`.
- **artifact_link**: [session owner](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleSessionOwner.kt), [transport timeout](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleGattTransport.kt), [timeout tests](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/FidoBleLifecycleTest.kt)

### Record 136: ASUS Windows WebAuthn Registration and Assertion (T023)
- **requirement**: T023; real Windows WebAuthn GetAssertion over the parent-gated FIDO BLE transport, with diagnostic operation timing recorded separately from any acceptance threshold.
- **scope**: Perform one synthetic registration and a subsequent authentication using `codex-synthetic-064-20261103` on WebAuthn.io, with the Windows security-key chooser directed to the ASUS Chimali authenticator. Observe the browser result and the Android FIDO BLE diagnostic events.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`.
- **named_procedure**: Open WebAuthn.io with Cross-Platform attachment, Registration User Verification Required, Security Key hint and the synthetic account; select Windows `Cambiar → Llave de seguridad`; complete registration, then authenticate with the resulting credential.
- **environment**: ASUS `ASUS_AI2302`, Android 15 / API 35; Windows build `10.0.26300.9818`; ChatGPT integrated browser; WebAuthn.io. Exact browser engine version and Windows product label were not captured.
- **expected_observed_result**: Registration succeeded and WebAuthn.io displayed “Success! Now try to authenticate…”. ASUS BLE diagnostics recorded MakeCredential completion in 7,171 ms with terminal notification acknowledged. Authentication then succeeded; WebAuthn.io displayed “You're logged in!” for the synthetic account and listed the credential transports as `ble` and `usb`. Android logs showed successful GetInfo and GetAssertion exchanges with terminal notifications acknowledged; observed GetAssertion diagnostic durations across repeated chooser interactions were 12,854 ms, 182 ms and 5,064 ms. These diagnostic values are observations, not a three-second acceptance claim.
- **date**: 2026-11-06
- **limitations**: This validates BLE registration and assertion on one ASUS/Windows/browser combination only. No Classic HID ceremony, transport switch, clean-install comparison, interruption/recovery sequence or repeated-cycle test was performed. Exact browser engine version was unavailable. Related parent T027/T031/T035/T041 remain open; coverage T038 remains unverified/deferred.
- **status**: `Passed (synthetic WebAuthn registration and authentication over BLE; diagnostic timing observed) / Unverified (other hosts, Classic HID, transport-switch matrix, recovery and coverage)`.
- **artifact_link**: [parent acceptance evidence Record 117](../064-bluetooth-transport-selection/implementation-evidence.md#record-117-asus-ble-registration-and-authentication-partial-t041), [BLE quickstart](quickstart.md), [parent integration plan](../064-bluetooth-transport-selection/integration.md)

### Record 137: Same-Account Credential Retention RCA (T023 — Source Correction)
- **requirement**: T023; allow credentials created over one parent-selected transport to remain discoverable over another.
- **scope**: Trace`NO_CREDENTIALS` following a successful same-account registration on a different transport and remove destructive RP/user projection replacement.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`, with local implementation changes not committed.
- **named_procedure**: Inspect CTAP allow-list matching, credential persistence, SQLDelight schema and projection save semantics; implement non-destructive projection insertion and migration 2; run focused migration/DAO host tests and the full FIDO2 Android host suite; build `:app:assembleShrunkDebug` and deploy it to ASUS and Mi.
- **environment**: Windows 11; local worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; ASUS Android 15/API35 observation from 2026-11-07.
- **expected_observed_result**: The previous schema allowed only one row per RP/user and the save path explicitly removed prior rows. The correction removes that constraint and adds a migration that preserves all existing credentials while rebuilding the table and indexes. Focused host tests passed 1/1 each for migration preservation and same-account multi-credential projection; the full FIDO2 Android host suite passed 1,148/1,148 with zero failures, errors or skips. The shrunkDebug build succeeded and was installed on both devices. ASUS app data was reset only after the installed APK's obsolete signing key prevented an in-place update; the next launch displayed the Android Nearby devices prompt, while WebAuthn.io's page-focus error prevented the new registration from opening a Windows prompt.
- **date**: 2026-11-07
- **limitations**: Because ASUS app data was reset, the existing credential rows were not available to exercise migration 2 on that device; the focused SQLite host test did exercise migration from schema version 1 while retaining a credential. The exact Windows allow-list ID remains unknown; this is a likely cause supported by the observed sequence, not confirmed by byte-for-byte request comparison. Physical cross-transport re-registration/assertion remains open.
- **status**: `Confirmed (destructive projection replacement in source) / Implemented (multi-credential schema and persistence; focused host tests, build and deployment succeeded) / Unverified (full host suite, device migration with existing data and Windows BLE ceremony)`.
- **artifact_link**: [DAO implementation](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/dao/PasskeyCredentialDao.kt), [migration 2](../../feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/Fido2Database/2.sqm), [parent investigation Record 123](../064-bluetooth-transport-selection/implementation-evidence.md#record-123-credential-projection-replaced-same-account-passkeys-source-rca-and-correction)

### Record 138: Mi Migration View Dependency Correction (T035 — Partial)
- **requirement**: T035; Mi/API29 app update must preserve local state and remain launchable after schema migration.
- **scope**: Fix the legacy `credential_summary` dependency that broke table replacement in migration 2, then update Mi without uninstalling or clearing app data.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`, local changes uncommitted.
- **named_procedure**: Reproduce through in-place install/start on Mi, preserve logs, add legacy-view coverage to the SQLite migration test, build `:app:assembleShrunkDebug`, install in place and inspect MainActivity/passkey manager.
- **environment**: Mi A2 / Android API 29; Windows 11; local worktree `.worktrees/chimali/ofrenda`.
- **expected_observed_result**: First install crashed with`no such table: main.passkey_credential` while SQLite validated `credential_summary`. Migration 2 now drops/recreates the dependent view around table replacement. The focused host test passed 1/1; the rebuilt APK installed without data reset and MainActivity opened to `Ready to Start` with no migration error. The passkey manager reported no saved passkeys.
- **date**: 2026-11-07
- **limitations**: No Mi passkey rows existed, so physical credential preservation was not observed. The test exercises retention of a synthetic legacy row and confirms the recreated view and indexes. No ceremony or reconnect cycle was run.
- **status**: `Failure reproduced and corrected / Focused migration test passed / Mi app launch passed / Unverified (existing device credentials and physical transport ceremony)`.
- **artifact_link**: [migration 2](../../feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/Fido2Database/2.sqm), [regression test](../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/database/Fido2DatabaseCredentialMigrationTest.kt), [parent Record 124](../064-bluetooth-transport-selection/implementation-evidence.md#record-124-mi-migration-failure-and-view-safe-correction-t031t035--partial)

### Record 139: ASUS BLE Security-Key Chooser Rejection (T023 — Failed Attempt)
- **requirement**: T023; complete a Windows WebAuthn ceremony over the BLE GATT authenticator.
- **scope**: Record the user's latest BLE security-key attempt after ASUS was re-paired to Windows.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; local changes uncommitted.
- **named_procedure**: Start WebAuthn.io BLE security-key flow, select the Windows security-key option and complete the ASUS prompt.
- **environment**: ASUS Android 15/API35 and Windows 11; user-reported observation on 2026-11-07.
- **expected_observed_result**: Windows displayed “No reconocemos esta clave de seguridad. Prueba con otra distinta.” Registration/authentication did not succeed in this attempt.
- **date**: 2026-11-07
- **limitations**: Android CTAP logs and the exact allow-list identifier were not captured for this attempt, so the failure stage and cause are unknown. It is not evidence that the migration correction resolved cross-transport credential matching. Whether ASUS had the latest database-migration build installed is unverified. T023 remains open for the BLE ceremony; T035/T038 remain open or owner-deferred as previously documented.
- **status**: `Failed (Windows rejected the BLE security-key attempt) / Unverified (Android request stage, credential matching, cause and installed build)`.
- **artifact_link**: [parent Record 125](../064-bluetooth-transport-selection/implementation-evidence.md#record-125-asus-ble-security-key-chooser-rejection-after-classic-pairing-t031--partial), [credential retention RCA](#record-137-same-account-credential-retention-rca-t023--source-correction)

### Record 140: ASUS Permission and Build Prerequisite Check (T023/T035 — Partial)
- **requirement**: T023/T035; the BLE transport must have the required runtime permissions and the candidate build before a host ceremony or lifecycle recovery is accepted.
- **scope**: Read ASUS package permission state, current system dialog, installed APK hash and logcat after a user-reported Windows security-key rejection; compare the installed APK against the current local candidate and Mi deployment.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; local candidate SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Read-only ADB inspection of `dumpsys package`, app-ops, current UI hierarchy, `pm path`/`sha256sum` and recent logcat on ASUS and Mi.
- **environment**: ASUS_AI2302/API35 (`R7AIB7002901AZ3`), Mi A2/API29 (`2ac6876`), Windows 11; 2026-11-07.
- **expected_observed_result**: ASUS had the Android Nearby Devices permission dialog open. `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` were denied and app-ops were `ignore`. ASUS installed APK hash `33c2c25839bcc640e8dca16ebc52d5d405dd4ce80ac4708696d5e49374ddba8e` differed from the current candidate; Mi's installed APK matched the candidate. No corresponding FIDO CTAP operation was found in the inspected ASUS log buffer. The installed ASUS APK and current candidate have the same Android Debug signing certificate (SHA-256 `6bb5df3f4d4e68e75dac02e7f246a7dacf9ae87eb9e58ea79cfec5ca8a227410`), so a data-preserving in-place update is technically compatible once the user resolves the permission prompt.
- **date**: 2026-11-07
- **limitations**: Inspection was read-only; the system prompt was not operated and no APK was installed. This cannot prove which permission/build state applied when Windows displayed its rejection. The latest BLE failure cannot be attributed to the candidate storage correction. Await user-granted Nearby Devices permission before updating/testing ASUS; do not clear ASUS data. BLE T023/T035 remain open.
- **status**: `Observed (permission prompt and denied BLE grants on ASUS; stale installed APK; current candidate already on Mi) / Unverified (Windows attempt stage, current-build ASUS advertising/ceremony and recovery matrix)`.
- **artifact_link**: [parent Record 126](../064-bluetooth-transport-selection/implementation-evidence.md#record-126-asus-ble-attempt-prerequisites-were-not-met-t031--unverified-attempt), [reconnect stress Record 121](#record-115-post-deployment-50-cycle-mi-to-asus-gatt-reconnect-stress-t035--partial)

### Record 141: Current ASUS 50-Cycle GATT Reconnect Stress (T035 — Partial)
- **requirement**: T035; exercise the BLE GATT connection/discovery/recovery loop for 50 consecutive connect/disconnect/reconnect cycles with memory observations.
- **scope**: Use Mi/API29 as the scanner and ASUS/API35, running the exact current production APK, as a separate live FIDO BLE advertiser; run the opt-in one-phone instrumentation test and inspect ASUS after the cycles.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; production shrunkDebug APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`; instrumentation APK SHA-256 `44e9749c26a71737d1c80886f279f040f6584657ea5312bcbb62938e3123c218`.
- **named_procedure**: Run `:feature:fido2:assembleAndroidDeviceTest`; install only the generated Android device-test APK on Mi; invoke `adb -s 2ac6876 shell am instrument -w -e class com.chimali.fido2.data.transport.FidoBleReconnectStressDeviceTest -e runFidoBleReconnectStress true com.chimali.fido2.test/androidx.test.runner.AndroidJUnitRunner`; inspect instrumentation output/logcat and ASUS advertiser/service state afterward.
- **environment**: Mi A2/API29 scanner (`2ac6876`), ASUS_AI2302/API35 advertiser (`R7AIB7002901AZ3`), Windows 11 host; 2026-11-07.
- **expected_observed_result**: The test runner returned `OK (1 test)` in 85.827 seconds. Mi test log recorded `cycles=50`, Java heap before/after 2,763,928/4,206,360 bytes (+1,442,432), native heap before/after 6,132,216/7,325,304 bytes (+1,193,088). The remote service-discovery assertion passed each cycle. ASUS retained app_if 13, the FIDO foreground transport service and `Advertising...`; the installed ASUS APK SHA-256 matched the locally built current APK.
- **date**: 2026-11-07
- **limitations**: Heap samples are immediate and process-wide, with no forced collection; they are not proof of leak freedom. The stress test performs unauthenticated connect/service discovery/disconnect/close, not CTAP or a Windows ceremony. It does not cover active-request interruption, stale-bond guided recovery, permission revocation or ceremony cancellation. BLE T035 remains partial; T038 is owner-deferred.
- **status**: `Passed (50/50 GATT discovery/reconnect cycles against current ASUS candidate) / Observed (memory samples and server advertising persistence) / Unverified (leak acceptance and interruption/stale-bond/cancellation recovery)`.
- **artifact_link**: [captured output](../../feature/fido2/build/reports/fido2/064/asus-current-reconnect-stress-20261107.txt), [test source](../../feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/data/transport/FidoBleReconnectStressDeviceTest.kt), [parent integration Record 127](../064-bluetooth-transport-selection/implementation-evidence.md#record-127-mi-to-current-asus-50-cycle-gatt-reconnect-stress-t035--partial)

### Record 142: Current Candidate ASUS Install, Permission Recovery and Advertising (T023/T035 — Partial)
- **requirement**: T023/T035; BLE advertising must recover after required permissions are granted and the selected transport is explicitly started.
- **scope**: Verify the user's Nearby Devices grant, install the exact current production build in place on ASUS, set up the Passkey Authenticator feature, explicitly start BLE, and inspect the credential list and Android GATT advertiser.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; shrunkDebug SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Read Nearby permission state; install with `adb install -r` without uninstall/data reset; launch MainActivity; select Passkey Authenticator; tap Start Authenticator; inspect the UI, package permissions, foreground service, and Bluetooth GATT advertiser registration.
- **environment**: ASUS_AI2302 / Android 15/API35 (`R7AIB7002901AZ3`); Mi A2/API29 (`2ac6876`); 2026-11-07.
- **expected_observed_result**: The user granted `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN`. The install succeeded in place and installed APK hash matched the local build. ASUS displayed `Advertising...` and `Ready to pair with a new host PC`; Bluetooth manager reported app_if 13 for `com.chimali.shrunk`, a registered GATT advertiser and three active advertising sets total (two unrelated system advertisers); `Fido2TransportService` remained foreground. Manage Saved Passkeys showed`No passkeys yet`.
- **date**: 2026-11-07
- **limitations**: No device data was cleared by this install, but ASUS already had no stored passkeys following an earlier authorized reset; therefore no legacy credential was available to verify physical migration/retention. A new synthetic WebAuthn registration/assertion is required. This is advertiser/startup recovery only, not WebAuthn ceremony, connection recovery or full T035 acceptance. T023/T035 remain open and T038 remains owner-deferred.
- **status**: `Passed (user-granted Bluetooth permissions, in-place candidate install, explicit BLE start, advertiser/service registration) / Observed (empty passkey store) / Unverified (new registration/assertion and remaining interruption/recovery acceptance)`.
- **artifact_link**: [parent deployment evidence Record 128](../064-bluetooth-transport-selection/implementation-evidence.md#record-128-current-candidate-installed-and-advertising-on-asus-local-credential-store-empty-t031--partial), [current 50-cycle stress Record 141](#record-141-current-asus-50-cycle-gatt-reconnect-stress-t035--partial)

### Record 143: Current ASUS BLE WebAuthn Registration (T023 — Partial)
- **requirement**: T023; complete a registration ceremony through the selected parent-gated BLE GATT authenticator.
- **scope**: Register the approved synthetic WebAuthn account on ASUS using the current in-place shrunkDebug candidate, then verify the CTAP response, browser success and persisted local credential.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; ASUS production APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Submit WebAuthn.io Register for `codex-synthetic-064-20261103` with Cross-Platform attachment, Security Key hint and required user verification; select Bluetooth security key in Windows and complete the ASUS verification prompt; inspect browser status, logcat and Chimali passkey manager.
- **environment**: ASUS_AI2302 / Android 15/API35 (`R7AIB7002901AZ3`), Windows 11 build `10.0.26300.9818`, host `LOQ15IRX10-3`, ChatGPT integrated browser, WebAuthn.io; 2026-11-07.
- **expected_observed_result**: BLE admitted `MakeCredential`, encoded a 218-byte CBOR response with a 32-byte credential ID, and completed in 4,040ms with the terminal notification acknowledged. WebAuthn.io displayed `Success! Now try to authenticate...`. The saved-passkey manager listed `webauthn.io` / `codex-synthetic-064-20261103`, last used 2026-11-07. The device continued advertising.
- **date**: 2026-11-07
- **limitations**: This proves BLE registration on the current candidate, not authentication. A prior-in-sequence GetAssertion returned`NO_CREDENTIALS` while the local list was empty and before MakeCredential; it is not evidence against the newly registered credential. No raw credential ID or host allow-list bytes were logged. T023 remains partial pending current-build assertion and lifecycle recovery; parent cross-transport acceptance is also open. Coverage T038 remains owner-deferred.
- **status**: `Passed (BLE MakeCredential, WebAuthn.io success and local credential persistence on the current candidate) / Unverified (assertion with the new credential and remaining physical acceptance)`.
- **artifact_link**: [captured output](../../feature/fido2/build/reports/fido2/064/asus-current-ble-registration-20261107.txt), [parent Record 129](../064-bluetooth-transport-selection/implementation-evidence.md#record-129-current-asus-ble-webauthn-registration-succeeded-t031--partial), [device reconnect stress Record 141](#record-141-current-asus-50-cycle-gatt-reconnect-stress-t035--partial)

### Record 144: Current ASUS BLE WebAuthn Authentication (T023 — Partial)
- **requirement**: T023; complete an assertion ceremony over the parent-gated BLE authenticator after registration.
- **scope**: Authenticate the newly stored synthetic WebAuthn credential using the current ASUS candidate; corroborate BLE GetAssertion completion, required user verification and WebAuthn.io success.
- **revision**: Worktree branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; shrunkDebug SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: After the BLE registration in Record 143, click Authenticate on WebAuthn.io for `codex-synthetic-064-20261103`; choose the Windows Bluetooth security key and complete ASUS verification; inspect the profile result and Android logcat.
- **environment**: ASUS_AI2302 / Android 15/API35 (`R7AIB7002901AZ3`), Windows 11 build `10.0.26300.9818`, host `LOQ15IRX10-3`, ChatGPT integrated browser, WebAuthn.io; 2026-11-07.
- **expected_observed_result**: Android admitted GetAssertion with one allow-listed credential. A first assertion completed through the headless fast path in 221ms. A second assertion invoked Android user verification (`CredentialAvailable=true`, successful AuthSession dismissal, auth token added), completed in 16,505ms, and acknowledged the terminal BLE notification. WebAuthn.io `/profile` displayed `You're logged in!` for the synthetic account and three device-bound credentials with transport arrays [`ble`,`usb`], [`usb`], [`ble`,`usb`].
- **date**: 2026-11-07
- **limitations**: Exact credential IDs and allow-list bytes were not copied, so the profile's listed transport arrays are not mapped individually to this assertion. This is a BLE same-transport registration/authentication round trip, not a Classic-to-BLE or BLE-to-Classic test. Physical interruption/stale-bond/cancellation acceptance remains open; T038 is owner-deferred.
- **status**: `Passed (current ASUS candidate GetAssertion with required user verification; WebAuthn.io login success) / Unverified (cross-transport switch and remaining physical acceptance)`.
- **artifact_link**: [captured CTAP/browser result](../../feature/fido2/build/reports/fido2/064/asus-current-ble-authentication-20261107.txt), [parent integration Record 130](../064-bluetooth-transport-selection/implementation-evidence.md#record-130-current-asus-ble-registration-and-authentication-round-trip-t031--partial), [registration Record 143](#record-143-current-asus-ble-webauthn-registration-t023--partial)

### Record 145: Current ASUS BLE-Created Credential Authenticated over Classic HID (T023/T035 — Partial)
- **requirement**: T023/T035; verify cross-transport credential use and document user-verification interruption behavior.
- **scope**: Authenticate the current ASUS BLE-created synthetic credential after switching and restarting into Classic HID.
- **revision**: Parent branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; shrunkDebug APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Start Classic HID after the saved transport change and process restart; select the Windows Bluetooth security key for WebAuthn authentication; complete Android verification and inspect browser and logcat results.
- **environment**: ASUS_AI2302 / Android 15/API35 (`R7AIB7002901AZ3`), Windows 11 build `10.0.26300.9818`, ChatGPT integrated browser/WebAuthn.io; 2026-11-07.
- **expected_observed_result**: The user reported a screen rotation dismissed the first biometric check. Android logs subsequently showed successful biometric authentication, Classic HID reports accepted, and GetAssertion completion. WebAuthn.io `/profile` showed the synthetic account logged in. The user performed a second authentication and reported success.
- **date**: 2026-11-07
- **limitations**: The rotation-dismissed attempt is not counted as success. Browser profile credential transport arrays are not mapped to the exact assertion credential. This is BLE-to-Classic assertion evidence only; reverse-direction ceremony, interruption recovery, lifecycle matrix and T038 coverage (owner-deferred) remain open.
- **status**: `Passed (BLE-created credential used for Classic HID authentication; second success reported by user) / Unverified (reverse cross-transport ceremony and remaining physical acceptance)`.
- **artifact_link**: [parent cross-transport evidence Record 132](../064-bluetooth-transport-selection/implementation-evidence.md#record-132-current-candidate-ble-created-credential-authenticated-over-classic-t027--partial), [BLE registration/authentication Record 144](#record-144-current-asus-ble-webauthn-authentication-t023--partial)

### Record 146: Current ASUS Classic HID MakeCredential (T023/T035 — Parent-Gated Partial)
- **requirement**: Parent-gated Classic integration; document the companion physical registration result for the independently implemented BLE transport.
- **scope**: Complete current-candidate Classic HID registration using the shared authenticator core after BLE was previously selected and exercised.
- **revision**: Parent branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; shrunkDebug APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Select Cross-Platform, Security Key hint and Required user verification on WebAuthn.io; perform Classic registration for the existing synthetic test account through Windows and ASUS.
- **environment**: ASUS_AI2302 / Android 15/API35 (`R7AIB7002901AZ3`), Windows 11 build `10.0.26300.9818`, ChatGPT integrated browser; 2026-11-07.
- **expected_observed_result**: WebAuthn.io displayed `Success! Now try to authenticate...`. Android completed user verification and returned a successful MakeCredential response over Classic HID. The browser's exact credential and transports were not copied.
- **date**: 2026-11-07.
- **limitations**: Classic is outside the independent BLE component's transport implementation. This is supporting parent-gated evidence only, not BLE acceptance. The Classic credential authenticated successfully over HID; reverse Classic-to-BLE authentication and BLE lifecycle T035 remain open. T038 coverage remains owner-deferred.
- **status**: `Passed (current-candidate Classic MakeCredential and user-verified GetAssertion; WebAuthn.io login success) / Unverified (reverse BLE use)`.
- **artifact_link**: [parent Classic registration and assertion Record 133](../064-bluetooth-transport-selection/implementation-evidence.md#record-133-current-candidate-classic-hid-registration-t027--partial), [Classic assertion trace](../../feature/fido2/build/reports/fido2/064/asus-current-classic-authentication-20261107.txt), [BLE current-candidate assertion Record 145](#record-145-current-asus-ble-created-credential-authenticated-over-classic-hid-t023t035--partial)

### Record 147: Parent-Gated Current ASUS BLE Assertion after Classic-to-BLE Restart (T023/T035 — Partial)
- **requirement**: T023 and parent Selection T031; provide physical current-candidate BLE assertion evidence after the parent's reverse transport selection flow.
- **scope**: Complete a synthetic Windows WebAuthn authentication on BLE after Classic HID had been active and the user saved/restarted into BLE.
- **revision**: Parent branch 064-bluetooth-transport-selection; source HEAD 39acc7f87768ab5a6bbe539ae31e4f7c386f667c; shrunkDebug SHA-256 4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4.
- **named_procedure**: User changed the saved transport to FIDO Bluetooth LE, force-stopped/relaunched, explicitly started it; WebAuthn.io Authenticate for the synthetic account then used the Windows Bluetooth security key and ASUS verification.
- **environment**: ASUS_AI2302 / Android 15/API35 (R7AIB7002901AZ3), Windows 11, integrated browser/WebAuthn.io; 2026-11-07.
- **expected_observed_result**: Android GATT connected to the Windows host and returned GetAssertion responses; WebAuthn.io showed You're logged in! and a profile containing five credentials with BLE-capable entries.
- **date**: 2026-11-07.
- **limitations**: This is physical BLE assertion evidence after the parent switch, not completion of BLE lifecycle T035. The exact asserted credential was not mapped to the Classic-created credential from parent Record 133 because raw credential IDs were not copied. Interruption/stale-bond/cancellation matrix and T038 coverage remain open/deferred.
- **status**: Passed (current-candidate BLE GetAssertion and browser login after Classic-to-BLE restart flow) / Unverified (credential mapping and remaining BLE acceptance).
- **artifact_link**: parent Classic-to-BLE evidence Record 134; sanitized output ../../feature/fido2/build/reports/fido2/064/asus-current-classic-to-ble-authentication-20261107.txt
### Record 148: Focused Credential-Migration and DAO Regression Rerun (T036 Support)
- **requirement**: Recheck the shared credential persistence correction used by BLE ceremonies.
- **scope**: Run the focused legacy migration and passkey DAO tests in the current integrated worktree.
- **revision**: Parent branch 064-bluetooth-transport-selection; HEAD 39acc7f87768ab5a6bbe539ae31e4f7c386f667c plus working-tree changes.
- **named_procedure**: Run Fido2DatabaseCredentialMigrationTest and PasskeyCredentialDaoTest with the worktree-local Gradle 9.6.0 installation.
- **environment**: Windows 11, Gradle 9.6.0, 2026-11-07.
- **expected_observed_result**: The migration test passed 1/1; DAO query tests passed 1/1 and insert/update tests 3/3; 5 total passed, no failures/errors/skips.
- **date**: 2026-11-07.
- **limitations**: Focused regression coverage only. Full BLE device/lifecycle T035 and owner-deferred T038 coverage remain open.
- **status**: Passed (5 focused regression tests).
- **artifact_link**: parent focused test Record 135; sanitized run summary ../../feature/fido2/build/reports/fido2/064/credential-migration-dao-focused-test-20261107.txt
### Record 149: Current Worktree Static Analysis and Release Lint (T038 Support)
- **requirement**: Preserve parent-gated local validation evidence for BLE delivery; BLE T038 coverage is separately deferred.
- **scope**: Run repository KtLint, Detekt and release Android lint for the integrated current worktree.
- **revision**: Parent branch `064-bluetooth-transport-selection`; HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes.
- **named_procedure**: Worktree-local Gradle 9.6.0 task run `ktlintCheck detekt lintRelease --no-configuration-cache`.
- **environment**: Windows 11; 2026-11-07.
- **expected_observed_result**: `BUILD SUCCESSFUL` in 1m 5s; KtLint, Detekt and release lint passed.
- **date**: 2026-11-07.
- **limitations**: Parent-gated local validation only. It does not satisfy BLE T038's trusted accepted-target coverage/nonregression comparison, which remains owner-deferred; BLE T035 recovery matrix also remains partial.
- **status**: Passed (static analysis and release lint) / Unverified (deferred coverage and remaining physical acceptance).
- **artifact_link**: Parent validation Record 136; focused migration/DAO regression Record 148.

### Record 150: Post-Ceremony Synthetic Credential Transport Inventory (T023/T035 Support)
- **requirement**: Provide supporting parent-gated physical BLE ceremony evidence without overstating BLE scope or the full acceptance matrix.
- **scope**: Read the synthetic WebAuthn.io profile after the current Classic-to-BLE authentication and note the credential transport inventory.
- **revision**: Parent branch `064-bluetooth-transport-selection`; HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; ASUS candidate SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Inspect, without modifying credentials, the integrated-browser WebAuthn.io profile for `codex-synthetic-064-20261103` after successful BLE GetAssertion.
- **environment**: ASUS_AI2302 / Android 15/API35, Windows 11, integrated browser; 2026-11-07.
- **expected_observed_result**: WebAuthn.io showed logged-in status and five device-bound credentials; three listed `ble` and `usb`, while two listed `usb` only.
- **date**: 2026-11-07.
- **limitations**: Credential IDs are masked; there is no pre-switch inventory or committed-event comparison. This does not identify the exact asserted credential or complete BLE recovery/lifecycle T035. Coverage T038 remains owner-deferred.
- **status**: Observed (account transport inventory after BLE ceremony) / Unverified (exact credential mapping and remaining acceptance).
- **artifact_link**: Parent account inventory Record 137; BLE assertion [Record 147](#record-147-parent-gated-current-asus-ble-assertion-after-classic-to-ble-restart-t023t035--partial).

### Record 151: Current-Candidate ASUS BLE Advertiser Permission Recovery (T035 — Partial)
- **requirement**: BLE T035 requires physical interruption and recovery evidence using the parent-gated composition.
- **scope**: Revoke and restore Android 15's advertiser permission while current-candidate FIDO BLE is active; verify explicit recovery.
- **revision**: Parent branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus current working-tree changes; ASUS APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Revoke `BLUETOOTH_ADVERTISE`, observe Android service-process restart, regrant, launch MainActivity and verify advertising remains stopped until the user taps Start Authenticator.
- **environment**: ASUS_AI2302 / Android 15/API35, Windows 11; 2026-11-07.
- **expected_observed_result**: Android marked advertiser permission denied and restarted the app's service process. After permission regrant and activity launch, Chimali showed `Ready to Start`; after explicit Start it showed `Ready for Authentication` and Android Bluetooth logged `StartAdvertisingSet`.
- **date**: 2026-11-07.
- **limitations**: No CTAP ceremony was in flight. Cancellation, stale-bond, interrupted ceremony and complete BLE T035 matrix remain unverified. T038 coverage stays owner-deferred.
- **status**: Passed (advertiser permission loss/regrant recovery) / Unverified (remaining recovery acceptance).
- **artifact_link**: [sanitized ASUS permission-recovery trace](../../feature/fido2/build/reports/fido2/064/asus-current-advertiser-permission-recovery-20261107.txt); parent [Record 138](../064-bluetooth-transport-selection/implementation-evidence.md#record-138-current-candidate-asus-advertiser-permission-revocation-and-recovery-t035--partial).

### Record 152: Connected Device Suite Follow-Up and Remote BLE Discovery (T036/T035 — Partial)
- **requirement**: Recheck current parent-gated BLE integration on physical devices; discovery requires a separate remote advertiser.
- **scope**: Execute the connected FIDO2 suite on both phones and rerun FIDO service scanning from Mi with ASUS active as the peer advertiser.
- **revision**: Parent branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes.
- **named_procedure**: `:feature:fido2:connectedAndroidDeviceTest --no-configuration-cache`; then select only `FidoBleAdvertiserDiscoveryDeviceTest` on Mi while ASUS advertises.
- **environment**: ASUS_AI2302/API35 and Mi A2/API29; Windows 11; Gradle 9.6.0; 2026-11-07.
- **expected_observed_result**: The combined suite reports ASUS 58/7 failures and Mi 55/6 failures; the failures are the five controlled Perfetto/research procedures and opt-in 50-cycle test on both phones, plus ASUS's discovery test without a remote advertiser. The Mi discovery test passed. Isolated Mi discovery with ASUS advertising passed 1/1.
- **date**: 2026-11-07.
- **limitations**: Broad-suite failures remain documented; no claim that controlled cases passed. BLE T035 interruption/cancellation/stale-bond checks remain incomplete and T038 coverage remains owner-deferred.
- **status**: Partial (isolated two-device advertising discovery pass; broad suite contains known unconfigured failures).
- **artifact_link**: [sanitized connected-suite summary](../../feature/fido2/build/reports/fido2/064/connected-device-suite-20261107.txt); current isolated report `../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi A2 - 10.xml`.

### Record 153: ASUS Bidirectional Parent-Gated Restart and Passkey Visibility (T035 — Partial)
- **requirement**: BLE T035 relies on parent T028 composition; document lifecycle/recovery behavior without treating parent scope as BLE-complete.
- **scope**: Observe BLE→Classic and Classic→BLE saved-choice process restarts on the current ASUS candidate; verify selected-not-started and final BLE startup while retaining local passkeys.
- **revision**: Parent branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Save each alternate selection in settings, observe pending/active semantics, force-stop and relaunch, inspect selection and passkey manager, then explicitly start BLE after restoring it.
- **environment**: ASUS_AI2302 / Android 15/API35, Windows 11 build 10.0.26300.9818; 2026-11-07.
- **expected_observed_result**: Both switch directions applied after process restart. Classic showed selected/not started and no FIDO foreground service; return to BLE showed selected/not started until explicit Start restored `Ready for Authentication`. Three synthetic local passkey rows remained visible throughout.
- **date**: 2026-11-07.
- **limitations**: No CTAP ceremony was in flight; IDs/event history were not compared; OS process death, reboot, stale bond and cancellation remain incomplete. BLE T035 stays partial and coverage T038 stays owner-deferred.
- **status**: Passed (bidirectional process restart state and BLE explicit start) / Observed (passkey rows remained visible) / Unverified (remaining lifecycle and event identity).
- **artifact_link**: [sanitized current ASUS sequence](../../feature/fido2/build/reports/fido2/064/asus-current-bidirectional-selection-restart-20261107.txt); parent [Record 140](../064-bluetooth-transport-selection/implementation-evidence.md#record-140-current-asus-bidirectional-saved-choice-restart-and-credential-visibility-t035t041--partial).

### Record 154: Full Legacy Credential Migration Regression (T036 Support)
- **requirement**: Revalidate the shared credential projection change that lets BLE and Classic registrations coexist for one account.
- **scope**: Rerun the schema migration regression after extending assertions to every legacy passkey field, including encrypted metadata.
- **revision**: Parent branch `064-bluetooth-transport-selection`; HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes.
- **named_procedure**: Run the focused `Fido2DatabaseCredentialMigrationTest` and Android host-test KtLint task using Gradle 9.6.0.
- **environment**: Windows 11; 2026-11-07.
- **expected_observed_result**: Migration regression passed 1/1; host-test KtLint passed. All credential fields, encrypted metadata, indexes and summary view are asserted. The actual `PasskeyCredentialDao.insertCredentialProjection` path adds a second credential for the legacy RP/user without replacing the old row.
- **date**: 2026-11-07.
- **limitations**: Host migration test only; it does not verify an existing ASUS database migration, physical cross-transport ID/event equivalence or T038 coverage.
- **status**: Passed (complete legacy projection migration regression and lint).
- **artifact_link**: Parent migration regression [Record 141](../064-bluetooth-transport-selection/implementation-evidence.md#record-141-full-legacy-credential-projection-preservation-regression-t039-support); source `../../feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/database/Fido2DatabaseCredentialMigrationTest.kt`.

### Record 155: Current ASUS BLE Authentication After Transport Restore (T023 Support)
- **requirement**: BLE T023 requires an end-to-end current-candidate GetAssertion using the parent-gated GATT composition.
- **scope**: Verify the user-operated BLE authentication attempt after Classic-to-BLE selection was restored, the app restarted, and BLE was explicitly started.
- **revision**: Parent branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; current candidate as recorded in parent Record 140.
- **named_procedure**: Complete the Windows Bluetooth security-key chooser and ASUS verification in portrait; inspect the integrated WebAuthn.io page for the result.
- **environment**: ASUS_AI2302 / Android 15/API35, Windows 11 build 10.0.26300.9818, integrated browser WebAuthn.io; synthetic account codex-synthetic-064-20261103; 2026-11-07.
- **expected_observed_result**: WebAuthn.io displayed “You're logged in!”; the profile listed five credentials, three with BLE+USB transport metadata.
- **date**: 2026-11-07.
- **limitations**: Credential IDs are masked and this observation did not capture a correlated GATT/CTAP trace or identify the exact asserted credential. Interrupted-request/cancellation and stale-bond recovery remain open under T035; T038 coverage remains owner-deferred.
- **status**: Passed (user-operated BLE authentication; browser success observed) / Unverified (exact asserted credential and remaining BLE acceptance matrix).
- **artifact_link**: Parent authentication evidence [Record 142](../064-bluetooth-transport-selection/implementation-evidence.md#record-142-current-asus-ble-authentication-after-classic-to-ble-restore-t031t041-support).

### Record 156: Current-Candidate Mi BLE Advertising Recovery Start (T035 Support)
- **requirement**: BLE T035 requires physical lifecycle/recovery evidence and an explicit unverified accounting of remaining scenarios.
- **scope**: Confirm the installed current candidate on Mi/API29 can start BLE after its persisted selected mode is initially stopped.
- **revision**: Parent branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; installed APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Confirm Ready to Start in the authenticator screen, explicitly tap Start, and inspect Android service, GATT and advertising state.
- **environment**: Xiaomi Mi A2 (jasmine), Android 10/API29, ADB serial `2ac6876`; Windows 11; 2026-11-07.
- **expected_observed_result**: Explicit Start changed the UI to Advertising / Ready to pair; the foreground service registered FIDO `0xFFFD` and Device Information `0x180A` GATT services, and Android's advertiser reported status 0.
- **date**: 2026-11-07.
- **limitations**: This supports selected-mode recovery after a stopped state, in conjunction with the earlier reboot persistence result (parent Record 064). No new reboot, interrupted CTAP, cancellation, stale-bond procedure or memory-baselined 50-cycle run occurred. BLE T035 remains open; owner-deferred coverage T038 remains open.
- **status**: Passed (current-candidate Mi explicit BLE start and GATT/advertiser activation) / Unverified (remaining lifecycle acceptance).
- **artifact_link**: Parent evidence [Record 143](../064-bluetooth-transport-selection/implementation-evidence.md#record-143-current-candidate-mi-ble-explicit-recovery-start-t035-support); earlier reboot result [parent Record 064](../064-bluetooth-transport-selection/implementation-evidence.md#record-064-mi-api29-ble-preference-survives-reboot-and-requires-explicit-start).

### Record 157: Current FIDO2 Host Regression Suite and KtLint Rerun (T036 Support)
- **requirement**: BLE T036 and parent T039 require configured host regression tests and lint after transport/credential implementation changes.
- **scope**: Re-run the full FIDO2 Android host suite plus Android production and host-test KtLint against the current working tree, including the credential projection migration regression.
- **revision**: Parent branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes.
- **named_procedure**: Run Gradle tasks `:feature:fido2:testAndroidHostTest`, `:feature:fido2:ktlintAndroidMainSourceSetCheck`, and `:feature:fido2:ktlintAndroidHostTestSourceSetCheck` with the worktree-local Gradle cache.
- **environment**: Windows 11; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; 2026-11-07.
- **expected_observed_result**: Build succeeded in 48 seconds; 155 suites reported 1,148 tests with no failures/errors/skips. Both lint tasks passed.
- **date**: 2026-11-07.
- **limitations**: Host tests and KtLint do not replace current-device BLE acceptance, interrupted-request/cancellation and stale-bond procedures, full device suites or owner-deferred coverage/nonregression. T035 and T038 remain open.
- **status**: Passed (current FIDO2 host suite and lint) / Unverified (physical recovery and coverage gates).
- **artifact_link**: Parent host-test rerun [Record 144](../064-bluetooth-transport-selection/implementation-evidence.md#record-144-current-fido2-host-suite-and-changed-source-lint-rerun-t039-support); XML reports `../../feature/fido2/build/test-results/testAndroidHostTest/`.

### Record 158: Ordinary Repository Local CI Without Coverage (T036 Support)
- **requirement**: BLE T036 requires configured host suites and parent-gate validation; parent T039 requires ordinary static analysis/build/test validation before separate trusted coverage assessment.
- **scope**: Run the current repository local CI path without its opt-in coverage gate after the credential projection migration and BLE integration changes.
- **revision**: Parent branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes.
- **named_procedure**: Run `tools/local-ci.ps1 -NoFix -NoConfigurationCache` using the worktree-local Gradle cache, without `-RunCoverage`.
- **environment**: Windows 11; Gradle 9.6.0, JDK 17, Android SDK 37; worktree `D:\octav\source\repos\.worktrees\chimali\ofrenda`; 2026-11-07.
- **expected_observed_result**: Ordinary local CI succeeded in 139.87 seconds: repository KtLint, Detekt, release lint, compile-all, unit tests and configured KMP Android host-test tasks passed. The Kotlin daemon warned and fell back to non-daemon compilation successfully.
- **date**: 2026-11-07.
- **limitations**: No connected-device suite, coverage completeness/floor check or trusted accepted-target nonregression comparison was run. The owner-deferred coverage gate remains open; BLE T038 is not passed. BLE physical recovery task T035 remains partial.
- **status**: Passed (ordinary local CI without coverage) / Unverified (coverage and remaining physical/device checks).
- **artifact_link**: Parent local CI result [Record 145](../064-bluetooth-transport-selection/implementation-evidence.md#record-145-full-ordinary-local-ci-rerun-without-coverage-t039-support).

### Record 159: Current ASUS Transport-Switch Credential/Event Store Preservation (Parent T041 Support)
- **requirement**: Parent T041/SC-002 verifies that credentials and committed events survive both Bluetooth transport switches; BLE contributes its current candidate and assertion evidence.
- **scope**: During current-candidate BLE→Classic and Classic→BLE selection/restart transitions, compare the complete encrypted FIDO2 database bytes and inspect local passkey visibility.
- **revision**: Parent branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; ASUS candidate APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Read-only hash of the app-owned database before/after both switch/restart directions; verify Classic is selected/stopped after restart, restore BLE and explicitly start it, then inspect passkey rows.
- **environment**: ASUS_AI2302 / Android 15/API35, Windows 11 build 10.0.26300.9818; 2026-11-07.
- **expected_observed_result**: Database file remained 110,592 bytes with identical SHA-256 `d5248459eb986f44afa46e57ed08a4f0f6562b808969403fb457ac629548f517` across both switches; no WAL/SHM sidecars were present. All three synthetic WebAuthn passkey rows remained visible. Explicit Start restored BLE advertising and FIDO GATT registration.
- **date**: 2026-11-07.
- **limitations**: The encrypted database was not decrypted and individual IDs/sequences were not displayed; byte identity proves the stored database did not change through the selection-only procedure. Physical save during an in-flight ceremony and BLE T035 recovery gaps remain open; T038 coverage remains owner-deferred.
- **status**: Passed (shared-store preservation across both process-choice restarts) / Unverified (in-flight selection and full BLE recovery matrix).
- **artifact_link**: Parent detailed comparison [Record 146](../064-bluetooth-transport-selection/implementation-evidence.md#record-146-current-asus-bidirectional-switch-preserves-encrypted-credentialevent-store-t041sc-002).

### Record 160: ASUS User-Cancelled BLE Authentication Attempt (T035 — Unverified)
- **requirement**: BLE T035 requires physical request interruption/cancellation recovery.
- **scope**: Record one current-candidate ASUS authentication attempt in which the user tapped Cancel on the Android device.
- **revision**: Parent branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; installed ASUS candidate SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Start WebAuthn.io authentication for the synthetic account, hand off the Windows/Android security-key prompts to the user, then inspect the page, app UI, GATT registration, foreground service and recent logs after the user cancels.
- **environment**: ASUS_AI2302 / Android 15/API35, Windows 11, integrated browser WebAuthn.io; 2026-11-07.
- **expected_observed_result**: The user reported tapping Cancel on ASUS during the active authentication attempt. WebAuthn.io returned the generic timeout/not-allowed message; the app returned to Ready for Authentication, the foreground service remained active and FIDO GATT service `0xFFFD` stayed registered/started.
- **date**: 2026-11-07.
- **limitations**: No fresh GATT/CTAP cancellation log was captured for this attempt, so it is not proven to have reached Chimali's protocol cancellation handler. A separate earlier disconnect logged generic in-flight command cancellation after GetAssertion's invalid-state report; it is not proof that CTAPHID_CANCEL was received. This supports phone-side cancellation recovery only; protocol-level cancellation and the other T035 scenarios remain open. Coverage T038 remains owner-deferred.
- **status**: Passed (user-reported ASUS Cancel action and recovery to Ready for Authentication with BLE active) / Unverified (protocol-level cancellation and correlated GATT trace).
- **artifact_link**: Parent cancellation attempt [Record 147](../064-bluetooth-transport-selection/implementation-evidence.md#record-147-asus-user-cancelled-ble-authentication-attempt-t035-support--unverified).

### Record 161: Current Mi BLE Force-Stop and Explicit-Start Recovery (T035 Support)
- **requirement**: BLE T035 requires physical lifecycle/recovery evidence, including selected BLE remaining stopped until explicit start after process termination.
- **scope**: Force-stop the current Mi BLE authenticator, relaunch the app and verify the saved BLE transport is selected but inactive until explicit Start.
- **revision**: Parent branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; installed candidate SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4` (same candidate as Record 156).
- **named_procedure**: From Advertising, Android force-stop `com.chimali.shrunk`; verify process/service absence; launch MainActivity; inspect UI, tap Start and confirm foreground service plus registered GATT FIDO service.
- **environment**: Xiaomi Mi A2 (jasmine), Android 10/API29 (ADB serial `2ac6876`); Windows 11; 2026-11-07.
- **expected_observed_result**: The process and service were absent after force-stop. MainActivity relaunched with `Ready to Start` and `FIDO2 / WebAuthn over FIDO Bluetooth LE`; no service ran until Start. Explicit Start restored `Advertising...`, the foreground `Fido2TransportService`, and registered/started GATT service `0000fffd-0000-1000-8000-00805f9b34fb`.
- **date**: 2026-11-07.
- **limitations**: This verifies force-stop/relaunch and explicit BLE recovery on Mi only. It does not cover physical CTAP interruption, protocol-correlated cancellation, stale-bond recovery or the complete 50-cycle memory-baselined procedure. BLE T035 remains open; owner-deferred coverage T038 remains open.
- **status**: Passed (Mi BLE remains selected and stopped after force-stop/relaunch; explicit Start recovers advertising/GATT) / Unverified (remaining BLE recovery acceptance).
- **artifact_link**: Parent lifecycle evidence [Record 149](../064-bluetooth-transport-selection/implementation-evidence.md#record-149-current-mi-ble-force-stop-and-explicit-start-recovery-t035-support).

### Record 162: ASUS Classic Selection Reboot and BLE Restoration (T035 Support)
- **requirement**: BLE T035 depends on the parent process gate and saved transport restoration to support physical acceptance after reboot.
- **scope**: After reboot with Classic selected, verify the saved mode is not started automatically; save BLE, relaunch the process and explicitly start BLE.
- **revision**: Parent branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; installed candidate SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Reboot ASUS, unlock, inspect MainActivity and service, switch saved choice to BLE, force-stop/relaunch, verify Ready to Start, then explicitly start and inspect advertising/GATT registration.
- **environment**: ASUS_AI2302 / Android 15/API35 (ADB serial `R7AIB7002901AZ3`), Windows 11; 2026-11-07.
- **expected_observed_result**: After reboot, Classic appeared selected and stopped with no transport service. Explicit Start plus the approved 120-second visibility prompt restored `Ready for Authentication`; Android logs show HID_DEVICE profile acquisition and `BluetoothHidTransport connected and advertising`. The app then applied BLE after force-stop/relaunch and remained stopped until explicit Start. Start restored Ready for Authentication, foreground service, and registered FIDO GATT service `0xFFFD`.
- **date**: 2026-11-07.
- **limitations**: This supports parent SC-004 and verifies BLE selection after an intervening Classic reboot, but does not itself verify a BLE-device reboot or physical BLE interruption/stale-bond recovery. BLE T035 remains open.
- **status**: Passed (Classic persisted and activated after reboot; BLE was restored and explicitly started) / Unverified (BLE T035 interruption/recovery matrix).
- **artifact_link**: Parent reboot evidence [Record 148](../064-bluetooth-transport-selection/implementation-evidence.md#record-148-asus-classic-preference-survives-device-reboot-sc-004).

### Record 163: Current Mi BLE Recovery After Bluetooth Radio Off (T035 Support)
- **requirement**: BLE T035 requires interrupted/unavailable BLE lifecycle recovery without unsafe fallback or restart dependence.
- **scope**: While current BLE advertising is active on Mi, disable Bluetooth, observe the user-visible recoverable error, restore Bluetooth and retry the selected BLE transport.
- **revision**: Parent branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; installed APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: Use Quick Settings to disable and re-enable Bluetooth; inspect MainActivity and service state after each transition; tap Retry Authenticator after the adapter returns ON; verify Advertising and FIDO GATT service registration.
- **environment**: Xiaomi Mi A2 (jasmine), Android 10/API29 (ADB serial `2ac6876`); Windows 11; 2026-11-07.
- **expected_observed_result**: Bluetooth manager changed to `enabled=false`; Chimali showed `Error Occurred` / `BLE advertising stopped unexpectedly`, `Pairing help` and `Retry Authenticator`, while retaining BLE as the selected transport. After Bluetooth was restored to `enabled=true` / `ON`, Retry returned to `Advertising...` / `Ready to pair with a new host PC`; the foreground service remained running and Android reported GATT FIDO service `0xFFFD` registered and started.
- **date**: 2026-11-07.
- **limitations**: This covers radio loss/recovery, not runtime permission revocation, stale-bond guided recovery, interruption during an active CTAP operation, or protocol cancellation. BLE T035 remains open; owner-deferred coverage T038 remains open.
- **status**: Passed (current-candidate Mi radio-off error and explicit Retry recovery) / Unverified (remaining physical BLE recovery matrix).
- **artifact_link**: Parent radio recovery evidence [Record 150](../064-bluetooth-transport-selection/implementation-evidence.md#record-150-current-mi-ble-recovery-after-bluetooth-radio-off-t035-support).

### Record 164: ASUS Stale-Bond Re-Pair and BLE Authentication Recovery (T035 Support — Partial)
- **requirement**: BLE T035 requires observable stale-bond guided recovery and a clean subsequent BLE ceremony without credential deletion or app restart.
- **scope**: Forget the phone-side PC bond while Windows retains its pairing, observe the failed attempt, restore the bond, and authenticate the existing synthetic credential over BLE.
- **revision**: Parent branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; current ASUS APK SHA-256 `4d6667edf73fb6d94e5ac771e6a48280e5b2b8ed28918a8d6792f0074290dab4`.
- **named_procedure**: In Android Bluetooth Settings forget `LOQ15IRX10-3` on ASUS only; attempt authentication on WebAuthn.io using the Windows Bluetooth security key; cancel the failed attempt; recreate the bond as reported by the user; retry the same synthetic account and inspect browser, service and app diagnostics.
- **environment**: ASUS_AI2302 / Android 15/API35 (`R7AIB7002901AZ3`), Windows 11 build `10.0.26300.9818`, host `LOQ15IRX10-3`, integrated WebAuthn.io; account `codex-synthetic-064-20261103`; 2026-11-07.
- **expected_observed_result**: The first stale-pair attempt left ASUS showing Ready for Authentication with no request UI; Windows displayed “Activa la clave de seguridad Bluetooth o insértela en el puerto USB,” then the user canceled. After the user reported recreating the bond, the retry succeeded: WebAuthn.io displayed `You're logged in!`; Android logged a GATT connection, MTU 517, FIDO revision `0x20`, notifications enabled, GetInfo, and two GetAssertion responses with terminal notification acknowledgements. The FIDO foreground service and registered GATT service remained active and ASUS returned to Ready for Authentication.
- **date**: 2026-11-07.
- **limitations**: No CTAP operation was correlated to the first failed attempt, and ASUS did not display Pairing Help or a pairing prompt then. The exact re-pair actions and matching comparison value were not recorded; therefore the successful post-re-pair assertion proves recovery but not the requested in-app guided recovery path. BLE T035 remains open; T038 coverage remains owner-deferred.
- **status**: Passed (successful BLE GetAssertion after user-reported bond recreation; synthetic credential remained usable) / Unverified (stale-bond diagnosis and guided re-pair flow).
- **artifact_link**: Parent detailed record [Record 151](../064-bluetooth-transport-selection/implementation-evidence.md#record-151-asus-stale-bond-re-pair-and-ble-authentication-recovery-t035-support--partial); integrated browser and Android log/service/UI snapshots.

### Record 165: Current-Tree ASUS Coverage Attempt and Required Device-Suite Failure (T038 — Failed/Partial)
- **requirement**: BLE T038; coverage FR-006, FR-016, FR-017, FR-019, FR-020 and BRD NFR-ARCH-050.
- **scope**: Run the current-tree coverage workload on ASUS/API35, including the configured FIDO2/common/settings/app host and connected-device suites; retain the failure boundary and avoid reporting incomplete data as measured coverage.
- **revision**: Parent branch `064-bluetooth-transport-selection`; HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged working-tree changes; ASUS coverage APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: With `ANDROID_SERIAL=R7AIB7002901AZ3` and worktree-local `GRADLE_USER_HOME=.tmp/gradle-home`, run the Spec 058 init script and `hdkHostCoverage hdkCoverageVerify` after ordinary `tools/local-ci.ps1 -NoFix -NoConfigurationCache`.
- **environment**: ASUS AI2302 / Android 15/API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11, Gradle 9.6.0, Microsoft JDK 25.0.5, Android SDK platform 37; Mi A2 connected but not selected; 2026-11-07.
- **expected_observed_result**: App connected tests passed 11/11 and common device tests passed 1/1. The FIDO2 device suite failed at 58 tests with 50 passed and 6 failed (zero errors/skips): five Perfetto-dependent research tests lacked their required tracing/procedure setup, and the reconnect stress test lacked its explicit opt-in and remote advertiser setup. Coverage aggregation/verifier did not complete and no floor percentages are available. The previously indexed accepted baseline identifies `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`; current target is `c9195ea608ff3394e32aa217e7e640e5579d6f4d`, so the index is stale for this comparison. The instrumentation run removed `com.chimali.shrunk`; I reinstalled the current built APK without issuing a data-clear command, but Android uninstall may have removed that package’s local data/passkeys.
- **date**: 2026-11-07.
- **limitations**: Failure of the mandatory connected workload makes this run ineligible for coverage floors, completeness and nonregression. A fresh procedure-complete capture plus committed clean candidate and fresh exact-target/index are still required. T038 remains open.
- **status**: Passed (ordinary local CI); Failed (required FIDO2 connected-device task; no coverage report); Unverified (coverage floors and target comparison).
- **artifact_link**: Parent detailed coverage record [Record 152](../064-bluetooth-transport-selection/implementation-evidence.md#record-152-current-tree-asus-coverage-attempt-and-required-device-suite-failure-t039--failedpartial); [ASUS FIDO2 device-test XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml); [FIDO2 HTML report](../../feature/fido2/build/reports/androidTests/connected/androidMain/index.html).

### Record 166: ASUS-to-Mi FIDO GATT Fifty-Cycle Reconnect Stress (T035 Support)
- **requirement**: BLE T035 requires 50 consecutive connect/disconnect/reconnect cycles with before/after memory observations.
- **scope**: Run the dedicated opt-in physical BLE reconnect stress test on ASUS as GATT client against the separately advertising Mi A2 authenticator; discover the FIDO service and connect/disconnect 50 times.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged working-tree changes.
- **named_procedure**: `:feature:fido2:connectedAndroidTest` with the class filter `com.chimali.fido2.data.transport.FidoBleReconnectStressDeviceTest` and `runFidoBleReconnectStress=true`; `ANDROID_SERIAL=R7AIB7002901AZ3`.
- **environment**: ASUS AI2302 / Android 15 API 35 / arm64-v8a (serial `R7AIB7002901AZ3`) as client; Xiaomi Mi A2 / Android 10 API 29 (serial `2ac6876`) as remote BLE advertiser; Windows 11, Gradle 9.6.0, JDK 25.0.5; 2026-11-07.
- **expected_observed_result**: Focused connected-device run passed 1/1 with zero failures and skips. The test discovered the remote FIDO GATT service and completed all 50 cycles. Instrumentation log: Java heap before `6,021,664` bytes and after `6,677,024` bytes (+`655,360`); native heap before `8,712,896` bytes and after `8,730,656` bytes (+`17,760`).
- **date**: 2026-11-07.
- **limitations**: This is a single before/after heap snapshot and does not establish a leak trend or profile process-wide retained memory. It covers the reconnect stress subcase only; physical interruption during CTAP, runtime permission revocation, full stale-bond guided recovery and protocol-correlated cancellation remain incomplete. This was not a coverage run; T038 remains open.
- **status**: Passed (50-cycle ASUS-client/Mi-advertiser GATT stress) / Unverified (remaining BLE T035 scenarios and coverage gate).
- **artifact_link**: [Focused ASUS connected-device XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml); [instrumentation log](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/ASUS_AI2302%20-%2015/logcat-com.chimali.fido2.data.transport.FidoBleReconnectStressDeviceTest-remoteFidoGattServiceSupportsFiftyDiscoverDisconnectReconnectCycles.txt).

### Record 167: ASUS BLE Foreground-Service Device Lifecycle Checks (T035 Support)
- **requirement**: BLE T035 lifecycle/retry evidence; FR-BLE-060 and TR-BLE-030.
- **scope**: Run Android API 35 device tests for private connected-device foreground service declaration, idempotent Start/Stop, notification cleanup and retry after a simulated transport startup failure.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged working-tree changes.
- **named_procedure**: `:feature:fido2:connectedAndroidTest` filtered to `com.chimali.fido2.service.FidoBleLifecycleDeviceTest`; `ANDROID_SERIAL=R7AIB7002901AZ3`.
- **environment**: ASUS AI2302 / Android 15 API 35 / arm64-v8a (serial `R7AIB7002901AZ3`); Windows 11; Gradle 9.6.0; 2026-11-07.
- **expected_observed_result**: Connected-device suite passed 3/3, zero failures and skips. Service privacy/type, repeated-start idempotence, Stop notification/transport cleanup, and Retry after a simulated startup failure all passed.
- **date**: 2026-11-07.
- **limitations**: The test substitutes an in-process fake transport and does not revoke Android permission or exercise the physical advertiser, host interruption, system-process death, reboot, mid-ceremony save or stale-bond prompt. T035 remains open; this is not a coverage run and T038 remains open.
- **status**: Passed (scoped foreground-service device lifecycle cases) / Unverified (remaining physical lifecycle and recovery matrix).
- **artifact_link**: [ASUS connected-device XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml); [FidoBleLifecycleDeviceTest](../../feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/service/FidoBleLifecycleDeviceTest.kt).

### Record 168: ASUS Filtered Current-Tree Coverage Floors Passed (T038 — Partial)
- **requirement**: BLE T038; coverage FR-006, FR-016, FR-017, FR-019, FR-020 and BRD NFR-ARCH-050; parent T039.
- **scope**: Execute current-tree host/device coverage and local floor verification on ASUS API 35; use the established non-research suite filter and the opt-in ASUS-client/Mi-advertiser reconnect workload.
- **revision**: Branch `064-bluetooth-transport-selection`; HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged/unstaged changes; dirty candidate, so exact candidate commit/tree identity is unavailable.
- **named_procedure**: `ANDROID_SERIAL=R7AIB7002901AZ3`; run `.\gradlew.bat --no-configuration-cache -Pkotlin.compiler.execution.strategy=in-process -I tools/hdk-coverage.init.gradle -Pandroid.testInstrumentationRunnerArguments.notPackage=com.chimali.fido2.util.performance -Pandroid.testInstrumentationRunnerArguments.runFidoBleReconnectStress=true hdkCoverageVerify` using `.tmp\gradle-home`; Mi A2/API29 serial `2ac6876` remained a separate advertising peer.
- **environment**: Windows 11 x64; Gradle 9.6.0; Microsoft JDK 25.0.5; Android SDK platform 37; ASUS AI2302 / Android 15 API 35 / arm64-v8a / serial `R7AIB7002901AZ3`; Mi A2 / Android 10 API 29 / serial `2ac6876`; 2026-11-07.
- **expected_observed_result**: `hdkCoverageVerify` succeeded after the complete configured capture and analysis. FIDO2 device XML shows 53 tests passed, zero failures/errors/skips, including the explicit 50-cycle reconnect stress. Tier 1 applicable instruction/branch results: `:core:data` 87.948%/77.027%; `:core:domain` 71.325%/71.280%; `:core:security` 81.402%/66.839%; `:feature:fido2` 65.727%/61.165%; `:feature:authenticator` NotApplicable (0 executable instructions). Applicable Tier 2 floors pass: app 34.342%, common 30.825%, database 65.650%; other applicable Tier 2 modules also exceed 20%. Repository instructions are 58.970%, above the 50% floor.
- **date**: 2026-11-07.
- **limitations**: The five trace-research classes were intentionally excluded from this general coverage suite because each requires its own controlled tracing procedure and run arguments; this does not mark those studies passed. The current-tree local coverage and floor check does not establish candidate-versus-accepted-target nonregression: the only available `baselines/current.json` points to `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`, but current `origin/lab/or/chimali` is `c9195ea608ff3394e32aa217e7e640e5579d6f4d`. Candidate is dirty and exact-target trusted evidence/index are unavailable. T038 remains open pending the trusted raw-count comparison and provenance requirements.
- **status**: Passed (current-tree connected/host workload, completeness and configured local floors) / Unverified (trusted exact-target nonregression and five standalone research procedures).
- **artifact_link**: Parent coverage result [Record 153](../064-bluetooth-transport-selection/implementation-evidence.md#record-153-asus-filtered-current-tree-coverage-floors-passed-t039--partial); [canonical raw counters](../../build/reports/hdk-coverage/counters.json); [production inventory](../../build/reports/hdk-coverage/production-inventory.json); [repository JaCoCo report](../../build/reports/hdk-coverage/repository/index.html); [ASUS FIDO2 connected-device XML](../../feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302%20-%2015.xml).

### Record 169: ASUS Fresh-Install BLE Default and Explicit Advertising Start (T028 / T035 Support)
- **requirement**: BLE T028/FR-BLE-010 and parent SC-001/FR-BT-SEL-003; confirm parent composition selects FIDO BLE by default on a fresh installation and starts hardware only after explicit user action.
- **scope**: After the coverage test runner removed the ASUS package, reinstall the current candidate in a fresh package state, choose the Passkey Authenticator feature, and verify the default BLE selection remains stopped until Start.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged/unstaged worktree changes; ASUS APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: Install `app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk`, launch MainActivity, grant Nearby devices and notification prompts, select Passkey Authenticator in onboarding, inspect the stopped state, tap Start, then inspect foreground service and BLE GATT registration.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11; 2026-11-07.
- **expected_observed_result**: On the first-run feature-selection screen, Passkey Authenticator was selected. MainActivity displayed `Ready to Start`, `Start Authenticator`, and `FIDO2 / WebAuthn over FIDO Bluetooth LE`. No FIDO service was running before Start. After explicit Start, `Fido2TransportService` was foreground and Android Bluetooth Manager listed Chimali as a registered BLE app/server.
- **date**: 2026-11-07.
- **limitations**: This clean first-run check follows test-runner removal of the package; previous local credentials/settings may have been lost. It does not establish WebAuthn registration/assertion, transport switching, interruption recovery or credential/event preservation. BLE T035 remains open.
- **status**: Passed (parent-gated BLE default and explicit advertising lifecycle on fresh ASUS install) / Unverified (remaining physical ceremonies and recovery matrix).
- **artifact_link**: ASUS Android layout snapshots of first-run feature selection and Ready to Start, then ADB foreground-service and Bluetooth Manager registration state; [APK hash](../064-bluetooth-transport-selection/implementation-evidence.md#record-154-asus-fresh-install-ble-default-and-start-sc-001--t027-support).

### Record 170: ASUS Fresh-Install BLE Registration and Authentication (T030 / T035 Support)
- **requirement**: BLE T030 and parent T031/T041; verify a credential created over BLE can complete user-verified assertion on the integrated candidate.
- **scope**: Register and authenticate synthetic account `codex-synthetic-064-20261103` over BLE after Android test cleanup caused the ASUS app to be reinstalled in a fresh package state.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged/unstaged changes; ASUS APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: Complete first-run Passkey Authenticator setup, leave BLE selected and explicitly start Chimali; in integrated WebAuthn.io register the synthetic account through the Windows Bluetooth security-key chooser, complete ASUS verification, then authenticate with that credential.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11; integrated WebAuthn.io; 2026-11-07.
- **expected_observed_result**: User reported WebAuthn.io `Success! Now try to authenticate...` after registration and `You're logged in!` after authentication. Android diagnostics showed MakeCredential completed with terminal notification acknowledgement and GetAssertion matched one candidate, completed processing and acknowledged the terminal notification. The current fresh-install BLE round trip succeeded.
- **date**: 2026-11-07.
- **limitations**: The credential ID and database/event sequence were not captured. Classic was not exercised against this fresh data set, and physical interruption, cancellation, stale-bond recovery and the complete acceptance matrix remain outstanding. T035/T041 remain open.
- **status**: Passed (fresh-install BLE registration and assertion reported successful by WebAuthn.io and Android terminal acknowledgements) / Unverified (exact credential identity/event comparison, Classic round trip and full recovery matrix).
- **artifact_link**: User-reported WebAuthn.io registration and authentication success; Android BLE ceremony diagnostics; [fresh-install BLE startup evidence](#record-169-asus-fresh-install-ble-default-and-explicit-advertising-start-t028--t035-support).

### Record 171: ASUS Stale-Bond Re-Pair and Classic HID Assertion Succeeded (T035 Support — Partial)
- **requirement**: BLE T035 stale-bond recovery; exercise phone-side Forget, Windows-side removal, Pair again and a fresh authentication retry with the parent-gated Classic transport.
- **scope**: Remove only the ASUS bond for `LOQ15IRX10-3`, have the user remove Windows `Zenfone 10`, recreate the pair with matching numbers, then authenticate the synthetic WebAuthn.io account while Classic HID is selected.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged worktree changes; ASUS APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: Forget only the saved `LOQ15IRX10-3` bond in ASUS Bluetooth Settings; after user-confirmed removal of Windows `Zenfone 10`, pair again via Windows Bluetooth Settings and confirm the matching numbers on both devices; complete Classic HID WebAuthn authentication for `codex-synthetic-064-20261103`.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11; integrated WebAuthn.io; 2026-11-07.
- **expected_observed_result**: The ASUS bonded-device list showed the target bond removed and then restored after the user confirmed matching-number pairing. Chimali displayed `Connected to PC`; Bluetooth Manager exposed HID_DEVICE; WebAuthn.io displayed `You're logged in!`. App logs show one allow-listed credential candidate processed after user verification and HID reports accepted.
- **date**: 2026-11-07.
- **limitations**: This verifies successful system-level Forget/Pair and account-level Classic assertion after retry, but does not establish the exact credential ID, that this credential was created over BLE, or that the app displayed guided stale-bond recovery. BLE-specific interruption/cancellation and the complete recovery matrix remain open; T035 remains open.
- **status**: Passed (system bond recreation and Classic HID assertion) / Unverified (credential-to-transport mapping, in-app guided recovery, and full BLE recovery matrix).
- **artifact_link**: ASUS Bluetooth Settings and bonded-device state; matching-number pairing reported by the user; Chimali `Connected to PC`; Bluetooth Manager HID_DEVICE registration; filtered Android app logcat; WebAuthn.io logged-in profile page.

### Record 172: ASUS BLE Authentication Succeeded After Classic HID Pair and Assertion (T035 Support)
- **requirement**: BLE T035 and parent T041; verify BLE lifecycle after the Classic-mode re-pair by explicitly restarting BLE and completing a fresh account authentication.
- **scope**: Save BLE as the selected transport after Classic HID authentication, restart the process, explicitly start BLE advertising/GATT, then authenticate the synthetic account over BLE using the same Windows host.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged worktree changes; ASUS APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: Select FIDO Bluetooth LE in Chimali Settings; force-stop/relaunch; explicitly tap Start Authenticator; complete the WebAuthn.io synthetic-account authentication through Windows Bluetooth security-key selection and ASUS verification.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11; integrated WebAuthn.io; 2026-11-07.
- **expected_observed_result**: Chimali showed Ready to Start after process restart and changed to Ready for Authentication after explicit BLE Start. A GATT connection enabled notifications and completed GetInfo; a later GetAssertion matched one candidate, processed and acknowledged its terminal notification (`elapsedMs=4355`). WebAuthn.io displayed `You're logged in!` for `codex-synthetic-064-20261103`.
- **date**: 2026-11-07.
- **limitations**: The exact assertion credential ID is masked and not linked to the earlier BLE or Classic ceremony. Initial allow-list entries returned `NO_CREDENTIALS` before the successful match. This adds BLE-side cross-transport evidence but does not close physical interruption/cancellation, exact credential mapping or the complete lifecycle/recovery matrix; BLE T035 remains open.
- **status**: Passed (BLE account authentication after Classic transport and re-pair) / Unverified (credential identity and remaining T035 recovery/lifecycle procedures).
- **artifact_link**: ASUS BLE startup screenshots; ADB GATT/GetInfo/GetAssertion diagnostics at 16:27; integrated WebAuthn.io `/profile` success state.

### Record 173: ASUS BLE Cancellation Attempt Returned to Ready with Windows Read Error (T035 — Partial)
- **requirement**: BLE T035 cancellation/recovery; verify UI and transport recovery after cancellation during a BLE assertion.
- **scope**: Start synthetic-account BLE authentication, select the Windows Bluetooth security key and have the user tap Cancel on ASUS during verification.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged worktree changes; ASUS APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: Start BLE and initiate WebAuthn.io Authenticate for `codex-synthetic-064-20261103`; in Windows choose the Bluetooth security key and tap Cancel on the ASUS verification prompt.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11; integrated WebAuthn.io; 2026-11-07.
- **expected_observed_result**: The user reports tapping Cancel on ASUS; Windows displayed `No se puede leer la clave de seguridad. Vuelve a intentarlo.` Android logged GATT, notifications enabled, GetInfo, allow-listed GetAssertion attempts and one matching candidate; the last operation failed after 9.035 seconds but acknowledged its terminal notification. The GATT session disconnected, while Chimali returned to Ready for Authentication and the BLE service/advertiser remained active.
- **date**: 2026-11-07.
- **limitations**: No correlated CTAPHID_CANCEL was logged and causality between the tap and Windows read error is unverified. This is UI/session recovery evidence, not proof of protocol cancellation. Other interruption and lifecycle recovery cases remain open; T035 remains open.
- **status**: Partial (Ready state and BLE service recovered after the failed attempt) / Failed (WebAuthn authentication) / Unverified (protocol-level cancellation semantics).
- **artifact_link**: User-reported ASUS cancellation and Windows dialog; Android app-PID logcat around 16:29:45–16:29:58; layout showing Ready for Authentication; WebAuthn.io start page.

### Record 174: BLE Assertion Succeeded on Immediate Retry After Failed Ceremony (T035 Support)
- **requirement**: BLE T035 retry recovery; verify a new BLE authentication can succeed after a failed/canceled ceremony without restarting Chimali or resetting the Bluetooth bond.
- **scope**: Retry authentication for the same synthetic WebAuthn.io account after the Windows security-key read error and prior GetAssertion failure.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged worktree changes; ASUS APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: With the existing BLE service advertising and Chimali showing Ready for Authentication, close the Windows error dialog, start a new WebAuthn.io Authenticate attempt for `codex-synthetic-064-20261103`, and complete user verification.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11; integrated WebAuthn.io; 2026-11-07.
- **expected_observed_result**: The retry connected over GATT, negotiated MTU 517, enabled notifications and completed GetInfo. Earlier nonmatching allow-list entries were rejected; the matching GetAssertion candidate completed, `Assertion completed` and `Assertion response ready` were logged, and the terminal notification was acknowledged (`elapsedMs=10241`). WebAuthn.io displayed `You're logged in!`; no app restart or re-pair was needed.
- **date**: 2026-11-07.
- **limitations**: The exact credential ID remains masked. The prior failed attempt is associated with a user-reported Cancel and Windows read error, but no correlated CTAPHID_CANCEL was logged, so protocol cancellation remains unverified. Other T035 interruption/lifecycle cases remain open.
- **status**: Passed (BLE authentication recovery on immediate retry) / Unverified (prior attempt's protocol-level cancellation and remaining recovery matrix).
- **artifact_link**: Android MainActivity Ready for Authentication state; app-PID logcat at 16:34:56–16:35:11; WebAuthn.io success page.

### Record 175: ASUS BLE Service Recovered After Process Death (T035 Support — Partial)
- **requirement**: BLE T035 process-death recovery; verify Android restarts the foreground FIDO service and GATT server after app process death, keeping BLE selected and available.
- **scope**: With BLE selected and active on ASUS, induce a process crash, inspect service/GATT advertising restoration, and reopen Chimali.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged worktree changes; ASUS APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: With FIDO Bluetooth LE selected and the app at Ready for Authentication, run Android ActivityManager `am crash com.chimali.shrunk`; verify process death, sticky FIDO service restart, GATT FIDO service/advertiser registration, then reopen MainActivity.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; 2026-11-07.
- **expected_observed_result**: PID 8950 died after an induced main-thread VM crash at 16:49:58–16:49:59. ActivityManager restarted `Fido2TransportService` in PID 9475 at 16:50:00; the new process registered `0000fffd` and Device Information GATT services at 16:50:01. The service was foreground, Bluetooth Manager showed the FIDO service and Chimali advertiser, and reopened MainActivity showed Ready for Authentication / Waiting for request from PC with BLE selected.
- **date**: 2026-11-07.
- **limitations**: Induced crash only; organic process eviction, in-flight ceremony/save, physical interruption, cancellation protocol, stale-bond guidance and unavailable-mode recovery were not tested by this procedure. T035 remains open.
- **status**: Passed (BLE foreground service, GATT server and advertiser recovered after induced process death) / Unverified (organic eviction and remaining T035 lifecycle/recovery matrix).
- **artifact_link**: AndroidRuntime/ActivityManager log at 16:49:58–16:50:00; PID 9475 GATT registration at 16:50:01; service and Bluetooth Manager snapshots; `.tmp/asus-ble-post-process-death.xml` UI hierarchy.

### Record 176: ASUS BLE Selection Persisted Across Reboot (T035 Support — Partial)
- **requirement**: BLE T035 reboot lifecycle; verify the saved BLE mode survives Android reboot without automatically activating the authenticator, then explicitly start it.
- **scope**: Reboot ASUS while BLE was selected and advertising; after user unlock inspect mode and service, explicitly Start, and verify BLE availability.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged worktree changes; installed ASUS shrunkDebug APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: Reboot ASUS, wait for Android boot completion, unlock once, open MainActivity and inspect the saved transport and inactive state; tap Start Authenticator and verify the GATT service, advertiser and foreground service.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; 2026-11-07.
- **expected_observed_result**: Android rebooted to credential-locked state; after the user unlocked, MainActivity showed `Ready to Start` and `FIDO2 / WebAuthn over FIDO Bluetooth LE`, with no FIDO process/service/advertising before Start. After explicit Start, the UI showed Ready for Authentication / Waiting for request from PC; `Fido2TransportService` ran foreground in PID 13968 and Android registered/advertised FIDO GATT service `0000fffd`.
- **date**: 2026-11-07.
- **limitations**: One BLE reboot only. No in-flight operation, post-reboot credential/event comparison or Classic reboot direction was performed. T035 remains open.
- **status**: Passed (BLE choice persisted across reboot, remained inactive until explicit Start and then advertised) / Unverified (remaining reboot/recovery matrix).
- **artifact_link**: Android boot/user state and service/advertiser snapshots; `.tmp/asus-ble-post-reboot.xml`; `.tmp/asus-ble-post-reboot-start.xml`.

### Record 177: Current-Tree FIDO2 Host Suite and Local CI (T036/T038 Support)
- **requirement**: Revalidate configured FIDO2 host behavior and the current-tree static/build/unit-test baseline after the credential-retention database migration updates.
- **scope**: Run the full FIDO2 Android host suite and repository non-device local CI against the current worktree.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes.
- **named_procedure**: Run `.\gradlew.bat :feature:fido2:testAndroidHostTest`; run `.\tools/local-ci.ps1 -NoFix -NoConfigurationCache` with worktree `.tmp/gradle-home` as `GRADLE_USER_HOME`.
- **environment**: Windows 11 / PowerShell / Gradle 9.6.0; 2026-11-07.
- **expected_observed_result**: FIDO2 Android host suite succeeded with 1,148 tests, 0 failures, 0 errors, 0 skipped; `Fido2DatabaseCredentialMigrationTest` passed 1/1. Non-device local CI succeeded in 177.96 seconds with KtLint, Detekt, release lint, all compilation, unit tests and configured KMP Android host suites passing.
- **date**: 2026-11-07.
- **limitations**: This is host/local validation; no connected-device suite or coverage run occurred in this procedure. A separate filtered current-tree capture passed configured connected tests and local floors (BLE Record 168), but did not establish trusted accepted-target nonregression because the candidate was dirty and the indexed baseline was stale. T038 remains open pending that trusted exact-target comparison; the earlier unfiltered workload's setup failures remain documented at BLE Record 165.
- **status**: Passed (current-tree host/local checks) / Unverified (coverage and connected-device acceptance).
- **artifact_link**: Gradle output and current XML files under `feature/fido2/build/test-results/testAndroidHostTest/`.

### Record 178: ASUS BLE WebAuthn Authentication After Reboot (T035 Support)
- **requirement**: BLE T035 post-reboot retry/availability; confirm that after BLE selection is restored and explicitly started, the authenticator can complete a fresh WebAuthn assertion.
- **scope**: Following Android reboot, re-open Chimali, verify BLE remained selected/stopped, explicitly start its GATT service, then authenticate the existing synthetic WebAuthn.io account over BLE.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; installed ASUS shrunkDebug APK SHA-256 `900c5c96860db7f94aa0d1f63c020f25b4cf32a17e4e5caf79aa0e4fa69609e2`.
- **named_procedure**: Select/restore FIDO Bluetooth LE, tap Start Authenticator, then click Authenticate for `codex-synthetic-064-20261103`; complete the Windows Bluetooth security-key chooser and ASUS user verification.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11 host; integrated WebAuthn.io; 2026-11-07.
- **expected_observed_result**: WebAuthn.io displayed `You're logged in!`. Android PID 13881 accepted GATT at MTU 517, enabled notifications, completed GetInfo, and logged two acknowledged successful matching GetAssertion responses; the user-verified request completed in 18,976ms. App remained in FIDO BLE mode.
- **date**: 2026-11-07.
- **limitations**: Credential IDs are masked, and the precise correlation between each acknowledged GetAssertion and the browser result is not available. This is one post-reboot account-level ceremony, not evidence for cancellation, stale-bond guidance, interruption recovery, or the full SC-001–006 matrix. T035 remains open.
- **status**: Passed (post-reboot BLE WebAuthn authentication and Android response acknowledgement) / Unverified (exact credential mapping, detailed browser-response correlation and remaining T035 procedures).
- **artifact_link**: ASUS app-PID logcat at 11:12:01–11:12:26; integrated WebAuthn.io `/profile` success page.

### Record 179: ASUS BLE Pairing Guidance and Retry Recovery (T035 Support)
- **requirement**: FR-BLE-070 / TR-BLE-040; expose a phone-side path to Android Bluetooth settings from pairing guidance and retain Retry after recoverable BLE errors.
- **scope**: Add Open Bluetooth settings and Retry actions to the BLE pairing-help dialog; exercise the dialog on ASUS, open and return from Android Bluetooth settings, and recover BLE advertising after Bluetooth is restored.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; installed ASUS/Mi `shrunkDebug` APK SHA-256 `87D47784A7BE23BE0539B0A39603AF7FF104B699CD72F92A0D462FE3E5BFDE1B`.
- **named_procedure**: Run `:feature:fido2:connectedAndroidDeviceTest` filtered to `AuthenticatorDashboardComponentsTest` on ASUS; install with `:app:installShrunkDebug`; on ASUS toggle Bluetooth off/on, open Pairing help, select Open Bluetooth settings, return to Chimali and select Retry.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Mi A2 / Android 10 API29 / serial `2ac6876`; Windows 11 / PowerShell / Gradle 9.6.0; 2026-11-07.
- **expected_observed_result**: `AuthenticatorDashboardComponentsTest` passed 7/7 with zero failures, errors or skips. The ASUS system opened `com.android.settings/.Settings$BluetoothPairingDetailActivity` from the dialog. Returning and choosing Retry restored `Ready for Authentication`, the foreground `Fido2TransportService`, GATT app interface 13 and advertising. The in-place install preserved first-install timestamp and Bluetooth permission grants on ASUS.
- **date**: 2026-11-07.
- **limitations**: The radio-off path verifies the new Settings route and Retry recovery, not the stale-bond UI path. The subsequent phone-only bond removal/re-pair and successful account authentication are recorded separately in Record 180; the app did not visibly surface this guidance, and matching-number confirmation was not captured. T035 remains open for guided stale-bond recovery, physical request interruption/protocol cancellation and remaining lifecycle procedures.
- **status**: Passed (focused ASUS UI test; actual Android Bluetooth Settings launch; Retry recovered BLE after radio restoration) / Partial (account authentication after bond recreation; stale-bond guidance and matching-number evidence unavailable).
- **artifact_link**: `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302 - 15.xml`; `.tmp/asus-bluetooth-settings-scroll.png`; `.tmp/asus-ble-post-reboot-start.xml`; ASUS app-PID logcat at 17:31–17:33.

### Record 180: ASUS BLE Authentication After Phone-Side Bond Recreation (T035 Support)
- **requirement**: FR-BLE-070 / TR-BLE-040; verify recovery when the phone has forgotten its Windows bond while Windows retains the old association.
- **scope**: Remove only the ASUS-side bond, attempt synthetic-account authentication with the retained Windows pair, then restore pairing and inspect BLE/CTAP recovery.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; installed ASUS/Mi `shrunkDebug` APK SHA-256 `87D47784A7BE23BE0539B0A39603AF7FF104B699CD72F92A0D462FE3E5BFDE1B`.
- **named_procedure**: Forget `LOQ15IRX10-3` only in ASUS Bluetooth Settings while retaining the Windows pair; start synthetic WebAuthn authentication; restore phone pairing; inspect Android bond/GATT state and browser result.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11 host `LOQ15IRX10-3`; integrated WebAuthn.io; 2026-11-07.
- **expected_observed_result**: After phone-only Forget, Android showed no bonded Windows host. The automated browser-triggered attempt returned WebAuthn's timeout/not-allowed message and is not valid ceremony evidence. The user reported repairing the pair and confirmed that matching numbers were shown on both devices; Android again listed the Windows host as bonded. The user opened Android Settings directly, not through Chimali's Pairing help. After closing the stale Windows prompt and manually clicking Authenticate, WebAuthn.io displayed `You're logged in!`. Android logs at 17:45:03–17:45:14 show GATT at MTU 517, GetInfo, several expected `NO_CREDENTIALS` responses for nonmatching allow-list entries and acknowledged GetAssertion responses; Chimali returned to Ready for Authentication.
- **date**: 2026-11-07.
- **limitations**: Matching-number confirmation is user-reported and was not independently captured. The app remained Ready and did not display Pairing help during the observed attempt; this proves BLE authentication after bond recreation but not use of the app's guided path. Exact credential mapping is masked. The browser automation timeout is excluded from success evidence. T035 remains open for exercising the in-app guidance and completing physical interruption/protocol cancellation and remaining lifecycle procedures.
- **status**: Passed (fresh user-initiated BLE account authentication after phone-side bond recreation; user confirms matching numbers) / Unverified (in-app stale-bond guidance and complete T035 matrix).
- **artifact_link**: ASUS app-PID logcat 17:35–17:45; Bluetooth Manager bonded-device snapshot; integrated WebAuthn.io `/profile` success state.

### Record 181: ASUS Pairing Help Remains Available While BLE Is Ready (T035 Support)
- **requirement**: FR-BLE-070 / TR-BLE-040; make pairing recovery guidance reachable during normal BLE availability, including while advertising and waiting for a PC request.
- **scope**: Keep the Pairing help action visible outside active GATT connection states; validate its state-visibility and callback behavior, then install and inspect the current build on ASUS.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; installed ASUS/Mi `shrunkDebug` APK SHA-256 `B2871B0FEC662193614EA5ABAB282AE868FA0DEE291E1753C6000259063497B3`.
- **named_procedure**: Run focused `AuthenticatorDashboardComponentsTest` on ASUS; run Android main/device-test KtLint; install with `:app:installShrunkDebug`; start Chimali and inspect the screen while BLE is advertising.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Mi A2 / Android 10 API29 / serial `2ac6876`; Windows 11 / Gradle 9.6.0; 2026-11-07.
- **expected_observed_result**: Focused dashboard tests passed 9/9 with zero failures/errors/skips; KtLint passed for Android main and device-test source sets. Gradle installed the shrunkDebug APK on both connected phones. ASUS preserved its first-install timestamp and existing paired-host display. After Start, ASUS showed `Ready for Authentication`, `Waiting for request from PC`, and an accessible `Pairing help` action while BLE remained active; Android reported the foreground transport service and BLE advertiser.
- **date**: 2026-11-07.
- **limitations**: The Settings/Retry dialog actions were exercised earlier in Record 179. The latest ready-state inspection did not open Settings or execute Retry. The previous fresh authentication after re-pair is Record 180; user confirmed matching numbers and clarified they opened Android Settings directly. Guided stale-bond recovery and the remaining T035 interruption/lifecycle matrix are still unverified.
- **status**: Passed (9/9 focused UI tests, KtLint, in-place deployment to both devices, ASUS Ready-for-Authentication screen exposes Pairing help) / Unverified (full guided stale-bond path and remaining T035 matrix).
- **artifact_link**: `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302 - 15.xml`; ASUS UI hierarchy after installation; Gradle install output; BLE service/advertiser snapshots.

### Record 182: User-Reported WebAuthn Retry Success Without a Correlated Device Trace (T035/T041 — Partial)
- **requirement**: BLE T035 retry recovery and T041 synthetic-account authentication; preserve the reported outcome while distinguishing it from independently correlated device evidence.
- **scope**: Following the updated ASUS build and a fresh user-directed retry, inspect the WebAuthn result and available phone logs for a correlated BLE ceremony.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; installed ASUS/Mi `shrunkDebug` APK SHA-256 `B2871B0FEC662193614EA5ABAB282AE868FA0DEE291E1753C6000259063497B3`.
- **named_procedure**: User cancels the stale Windows prompt and retries the synthetic WebAuthn login; inspect the integrated WebAuthn.io profile and ASUS app/CTAP state without initiating another ceremony.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11; integrated WebAuthn.io; synthetic account `codex-synthetic-064-20261103`; 2026-11-07.
- **expected_observed_result**: The user reported `Fresh login succeeded`. The integrated browser displayed `You're logged in!` and the synthetic account profile. ASUS remained Ready for Authentication with BLE selected and advertising. Inspection of the available post-install log buffer found no matching new CTAP/GetAssertion exchange; the profile had also shown the same logged-in state before the reported retry.
- **date**: 2026-11-07.
- **limitations**: The browser state alone does not distinguish this retry from the earlier successful ceremony, and no new device CTAP trace correlated to the report. Record as user-reported success only; do not count it as independently verified BLE authentication or as closure of retry/cancellation recovery. T035/T041 remain open.
- **status**: Partial (user reports fresh login success and WebAuthn.io displays success) / Unverified (fresh-ceremony correlation, device-side GetAssertion evidence and protocol cancellation).
- **artifact_link**: User reply confirming fresh login; integrated WebAuthn.io synthetic-account profile snapshot; ASUS Ready-for-Authentication UI snapshot and post-install log inspection.

### Record 183: Pairing Guidance Clarifies Phone and PC Bond Removal (T035 Support)
- **requirement**: FR-BLE-070 / TR-BLE-040; provide actionable recovery instructions when Windows identifies the phone by its model name and preserve the assurance that saved passkeys remain on-device.
- **scope**: Clarify pairing guidance and phone Bluetooth settings action; assert the guidance in the dashboard UI tests, validate on ASUS and Mi, then deploy and inspect the ASUS dialog and returned ready state.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; installed ASUS/Mi `shrunkDebug` APK SHA-256 `841B3775DB7DDD7E12FAC977771C875D718C706D34803C2D14E312503BFA81F5`.
- **named_procedure**: Run focused `AuthenticatorDashboardComponentsTest` on both connected phones; run Android main/device-test KtLint; deploy via `:app:installShrunkDebug`; start ASUS BLE, open Pairing help, inspect copy, close it and verify ready/advertising restoration.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Mi A2 / Android 10 API29 / serial `2ac6876`; Windows 11 / Gradle 9.6.0; 2026-11-07.
- **expected_observed_result**: The focused dashboard suite passed 9/9 on ASUS and 9/9 on Mi, with zero failures/errors/skips; both Android KtLint tasks passed. Gradle installed the updated APK on both devices. ASUS retained its first-install timestamp and paired Windows host row; the update restored Ready to Start with BLE selected and not advertising, as designed. After explicit Start, the app reached Ready for Authentication. Pairing help displayed instructions to remove the old pair on each side, noted Windows may show the phone by model name, confirmed matching numbers, and stated saved passkeys stay on the phone. Closing the dialog returned to Ready while the foreground transport service remained active.
- **date**: 2026-11-07.
- **limitations**: The dialog copy and settings action are verified, but this run did not execute the guided stale-bond removal/re-pair/Retry flow. The user's recent authentication retry remains user-reported without a matching device CTAP trace (Record 182). T035 remains open for guided stale-bond recovery, protocol cancellation, physical interruption and the remaining lifecycle matrix.
- **status**: Passed (9/9 focused UI tests per phone; lint; deployment; on-device recovery instructions visible and BLE readiness retained after dismissal) / Unverified (guided stale-bond recovery and remaining T035 procedures).
- **artifact_link**: Android device-test XML reports for ASUS and Mi; Gradle install output; ASUS Bluetooth Pairing help and Ready-for-Authentication UI snapshots; service/advertiser state.

### Record 184: Pairing Help Opens Android Settings and BLE Recovers After Phone-Side Forget (T035 — Partial)
- **requirement**: FR-BLE-070 / TR-BLE-040; exercise the in-app path to Android Bluetooth settings, remove a stale phone-side bond, and restore the authenticator's ready/advertising state.
- **scope**: Starting from BLE Ready, open Pairing help, launch phone Bluetooth settings, use the device details page to forget the PC bond, return through the dialog and tap Retry.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; ASUS/Mi `shrunkDebug` APK SHA-256 `841B3775DB7DDD7E12FAC977771C875D718C706D34803C2D14E312503BFA81F5`.
- **named_procedure**: Use Chimali's Pairing help → Open phone Bluetooth settings; open the saved `LOQ15IRX10-3` device details, select Forget and confirm; return to Chimali and select Retry; verify Android bond state, BLE UI, foreground service and advertiser.
- **environment**: ASUS AI2302 / Android 15 API35 / arm64-v8a / serial `R7AIB7002901AZ3`; Windows 11 host `LOQ15IRX10-3`; 2026-11-07.
- **expected_observed_result**: The button opened Android's Bluetooth settings. From the device's gear/details page, the Forget confirmation named `LOQ15IRX10-3`; after confirmation, that device disappeared from Android's `Bonded devices` list. Returning to Chimali and tapping Retry restored Ready for Authentication / Waiting for request from PC. `Fido2TransportService` remained present and the BLE advertiser restarted. The user then removed the stale Windows `Zenfone 10` pairing and re-paired, reporting matching numbers on both devices; Android logs recorded BONDING at 18:27:15 and BONDED at 18:27:29 for `LOQ15IRX10-3`. After the user reported a fresh WebAuthn login success, ASUS logs at 18:33:58–18:34:07 correlated with BLE GetInfo/GetAssertion, one admitted allow-list credential selected for assertion, successful Android user verification (`addAuthToken`), terminal notification acknowledgements, and the GATT disconnect. The integrated WebAuthn.io profile simultaneously displayed You're logged in! for codex-synthetic-064-20261103 and showed seven masked credential entries, including multiple BLE/USB transport lists; the profile does not reveal which exact credential the assertion used. The user confirmed WebAuthn login success.
- **date**: 2026-11-07.
- **limitations**: Matching-number comparison is user-reported, while system logs confirm the bond reached BONDED. The correlated ceremony establishes a successful BLE account assertion after guided bond recovery, but does not identify the exact credential ID or close the remaining physical interruption/cancellation/lifecycle cases or full T041 matrix.
- **status**: Passed (in-app navigation, phone-side Forget, BLE Retry, re-pair, and fresh BLE WebAuthn assertion after guided recovery) / Partial (remaining T035 procedures and exact-credential T041 comparisons).
- **artifact_link**: ASUS Bluetooth settings/detail and Forget confirmation UI snapshots; post-Forget Bonded Devices state; post-Retry MainActivity/service/advertising snapshots; ASUS logcat 18:33:58–18:34:07 and integrated WebAuthn.io logged-in profile.

### Record 185: ASUS BLE User Cancellation Returned to Ready (T035 — Partial)
- **requirement**: T035 physical cancellation recovery; cancel during the Android user-verification prompt for a real BLE GetAssertion and verify the authenticator recovers.
- **scope**: Start a fresh synthetic WebAuthn authentication after the guided re-pair, select the Windows Bluetooth security key, cancel at the ASUS prompt and inspect app/link state.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; ASUS `shrunkDebug` APK SHA-256 `841B3775DB7DDD7E12FAC977771C875D718C706D34803C2D14E312503BFA81F5`.
- **named_procedure**: User starts Authenticate for `codex-synthetic-064-20261103`, selects `Cambiar → Llave de seguridad` in Windows, taps Cancel on ASUS, closes the resulting Windows dialog and reports the app state.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11; integrated WebAuthn.io; BLE GATT; 2026-11-07.
- **expected_observed_result**: ASUS logs at 18:42:05–18:42:11 show BLE GetInfo, admitted GetAssertion requests, processing of a selected credential candidate, and terminal notification acknowledgement after 4,808 ms. The user confirmed tapping Cancel on ASUS; Chimali returned to Ready for Authentication and Windows displayed “No se puede leer la clave de seguridad. Vuelve a intentarlo.” Bluetooth logs show the GATT link disconnected at 18:42:14 while the phone-side bond remained.
- **date**: 2026-11-07.
- **limitations**: No CTAPHID_CANCEL receipt or CTAP response status code was logged, so this proves recovery after user cancellation of Android verification, not the host-sent CTAPHID_CANCEL protocol path or the precise returned error. WebAuthn login did not succeed in this attempt. Physical link interruption, permission revocation, remaining lifecycle cases and the full T035 matrix remain open.
- **status**: Passed (real GetAssertion reached the phone; user canceled ASUS verification; app returned Ready and retained its bond) / Unverified (protocol CANCEL frame/result and remaining T035 cases).
- **artifact_link**: ASUS logcat 18:42:05–18:42:14; Android bonded-device/foreground Activity state; user-reported Windows error and ASUS cancellation outcome.

### Record 186: FIDO BLE Local CI and Host Validation Without Deferred Coverage (T035 Support)
- **requirement**: BLE implementation validation and parent-gated T035 readiness; run current host checks while preserving the owner-deferred coverage gate.
- **scope**: Run the repository's non-coverage local CI pipeline on the current BLE candidate and review FIDO2 host-suite reports.
- **revision**: Branch `064-bluetooth-transport-selection`; HEAD `39acc7f8` plus staged and unstaged changes; ASUS/Mi shrunkDebug APK SHA-256 `841B3775DB7DDD7E12FAC977771C875D718C706D34803C2D14E312503BFA81F5`.
- **named_procedure**: `$env:GRADLE_USER_HOME=.tmp/gradle-home; .\tools\local-ci.ps1 -NoFix -NoConfigurationCache`.
- **environment**: Windows 11; Gradle wrapper 9.6.0; OpenJDK 25.0.5; ASUS/API35 and Mi/API29 connected but not used by the local-CI execution; 2026-11-07.
- **expected_observed_result**: KtLint, Detekt, Release Lint, comprehensive configured compilation and the full non-coverage local pipeline passed in 177.61 s. The FIDO2 Android host suite reported 1,148 tests, zero failures, zero errors and zero skips; app reports add five passing tests. Focused dashboard device tests are separately recorded as 9/9 on each phone (Record 183).
- **date**: 2026-11-07.
- **limitations**: Coverage floors and trusted accepted-target nonregression were not run; T038 remains owner-deferred/unverified. The local CI pipeline compiled connected-test sources but did not run the connected suite. Physical BLE acceptance remains partial under T035.
- **status**: Passed (FIDO2 host suite, app tests, static analysis and compilation) / Unverified (coverage, accepted-target comparison and remaining physical lifecycle tests).
- **artifact_link**: Gradle output from `tools/local-ci.ps1`; `feature/fido2/build/test-results/testAndroidHostTest/TEST-*.xml`; app test XML; BLE Record 183 for connected tests.

### Record 187: Adapter Loss Cancels Active BLE Request and Resets the Session (T035 — Partial)
- **requirement**: FR-BLE-050 / FR-BLE-080 / TR-BLE-020 / TR-BLE-040; stop an active CTAP operation and expose a retryable transport state when Bluetooth becomes unavailable.
- **scope**: Correct the radio-off lifecycle gap observed in parent Record 176, add host regressions for active request cancellation and adapter-state monitoring after advertising pauses for a GATT connection, then build and install the candidate.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged and unstaged changes; installed ASUS/Mi shrunkDebug APK SHA-256 `7F62FE8250D0051AA408C6D23E7564AAA16960696A09B3C32B61065BAA0A56C4`.
- **named_procedure**: Run `:feature:fido2:testAndroidHostTest` filtered to `FidoBleCancellationTest` and `NordicBleAdvertiserControllerTest`, run `:feature:fido2:ktlintCheck`, then run `:app:installShrunkDebug`.
- **environment**: Windows 11; Gradle 9.6.0; OpenJDK 25.0.5; ASUS AI2302 / Android 15 API35 / `R7AIB7002901AZ3`; Mi A2 / Android 10 API29 / `2ac6876`; 2026-11-07.
- **expected_observed_result**: The full FIDO2 Android host suite passed 1,150/1,150 with zero failures/errors/skips; FIDO2 KtLint passed. The repository non-coverage local CI pipeline passed in 134.49 seconds. `:app:installShrunkDebug` installed the candidate on both ASUS and Mi without clearing app data. The code now keeps the adapter-state receiver registered while GATT is connected, cancels the active message and clears the session on radio loss, stops GATT, reports a retryable Error state, and routes re-advertising failures through the same cleanup. After the user tapped Start on ASUS, a fresh BLE authentication completed; Android logged GetAssertion terminal notification acknowledgement at 19:17:28 and the user confirmed WebAuthn success. In a subsequent attempt with the Windows security-key dialog open, Bluetooth was disabled and restored. ASUS logged the Bluetooth-unavailable cleanup callback at 19:20:02; the visible app state changed to `Error Occurred — Bluetooth is off; restore Bluetooth and retry`. WebAuthn.io reported that interrupted attempt timed out. The user tapped Retry; ASUS returned to Ready for Authentication, re-registered FIDO and Device Information GATT services, and restarted BLE advertising at 19:28:47. A subsequent BLE GetAssertion completed with terminal notification acknowledged at 19:31:16; the user confirmed WebAuthn success and WebAuthn.io showed `You're logged in!` for the synthetic account. ASUS returned to Ready for Authentication.
- **date**: 2026-11-07.
- **limitations**: The post-retry ceremony succeeded at account level, but credential IDs remain masked and are not correlated to a specific stored record. Earlier scoped records cover ASUS permission revocation/regrant (Record 055), the 50-cycle GATT stress with before/after heap samples (Record 141), guided stale-bond recovery (Record 184), and phone-prompt cancellation recovery (Record 185); their limitations, including unverified leak acceptance and host-sent CTAPHID_CANCEL status, remain explicit. Coverage/T038 is owner-deferred; the parent selection matrix remains open.
- **status**: Passed (radio loss, explicit Retry, restored BLE advertising, post-recovery synthetic authentication, 1,150 host tests, repository non-coverage CI) / Unverified (exact credential mapping, leak acceptance, protocol-level CANCEL result, parent T035/T041 matrix and deferred coverage).
- **artifact_link**: Full FIDO2 host JUnit XML under `feature/fido2/build/test-results/testAndroidHostTest/`; Gradle output from `tools/local-ci.ps1`; installed APK hash above; ASUS logs at 19:17:28, 19:20:02, 19:28:47 and 19:31:16; post-recovery WebAuthn.io `You're logged in!`; related scoped physical records 055, 141, 184, 185 and parent Records 176–177.

### Record 188: Mi BLE Force-Stop Persistence and Explicit Start (T035 Support)
- **requirement**: BLE T035 / parent T035; BLE remains the selected process choice after force-stop and does not start until explicit user action.
- **scope**: Current `shrunkDebug` candidate installed in place on Mi A2/API29, then the Passkey Authenticator onboarding choice was restored, force-stop/relaunch was performed, and BLE was explicitly started. No credential was created or erased.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus working-tree changes; local and installed APK SHA-256 `6D0D87D179AFD0A375276EA44177D340A16931D380F218495AF13B8C247237C9`.
- **named_procedure**: `adb install -r` current candidate; launch MainActivity; select Passkey Authenticator in onboarding; record default BLE and Ready to Start; `adb shell am force-stop com.chimali.shrunk`; explicitly relaunch MainActivity; verify BLE remains selected and the transport remains stopped; tap Start and verify GATT advertiser startup.
- **environment**: Mi A2 / Android 10 API29 / serial `2ac6876`; package `com.chimali.shrunk` version `0.1.0`; 2026-11-07.
- **expected_observed_result**: In-place install succeeded with the same Android Debug signing certificate. After onboarding, UI showed `FIDO2 / WebAuthn over FIDO Bluetooth LE` and `Ready to Start`. Force-stop/relaunch preserved BLE and remained stopped. Explicit Start changed the UI to `Advertising...`; `Fido2TransportService` was foreground and Android logged `onAdvertisingSetStarted` with status `0`.
- **date**: 2026-11-07.
- **limitations**: This verifies BLE choice persistence and explicit BLE startup on Mi/API29, not a remote Windows connection or WebAuthn ceremony. Process-death, reboot and unavailable-mode procedures remain part of the parent T035 matrix; BLE T038 coverage remains deferred/unverified.
- **status**: Passed (Mi/API29 BLE force-stop/relaunch and explicit-start subcase); Partial (no post-restart WebAuthn ceremony).
- **artifact_link**: `.tmp/spec-064/mi-record-195-after-force-stop.xml`; `.tmp/spec-064/mi-record-195-after-start.xml`; `.tmp/spec-064/mi-record-195-installed.apk`.
### Record 189: Current Worktree Non-Coverage Local CI (Software Validation)
- **requirement**: BLE T038 local static-analysis, compile and configured host-test portion only; coverage and accepted-target nonregression are separately owner-deferred.
- **scope**: Current shared worktree and current parent-gated BLE implementation; no BLE source behavior was modified in this validation step.
- **revision**: Branch `064-bluetooth-transport-selection`, working tree based on source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; concurrent user changes preserved.
- **named_procedure**: Run `tools/local-ci.ps1 -NoFix -NoConfigurationCache` with worktree-local `GRADLE_USER_HOME`; coverage switch omitted.
- **environment**: Windows development host; Gradle 9.6.0; Kotlin 2.3.21; 2026-11-07.
- **expected_observed_result**: KtLint, Detekt, Release lint, all configured production/test-source compilation, ShrunkDebug unit tests and configured KMP Android host tests passed in 206.76 seconds. Kotlin daemon temp-file access restrictions triggered non-daemon fallback, after which compilation and validation succeeded.
- **limitations**: No connected device BLE acceptance, coverage floors/completeness, exact accepted-target raw-count comparison or hosted integration gate was run. The accepted-target evidence/index remains owner-deferred; this record does not close T038.
- **status**: Passed (non-coverage local software checks); Deferred (coverage/nonregression and remaining physical BLE procedures).
- **artifact_link**: Console output from `tools/local-ci.ps1`; module Gradle reports under `build/reports`.

### Record 190: ASUS BLE Foreground Service Restarts After Controlled Process Crash (BLE Lifecycle Support)
- **requirement**: BLE FR-010 / FR-011 and parent T035; Android's sticky connected-device service should recreate the selected BLE transport after process exit and continue advertising while enabled.
- **scope**: Current ASUS/API35 build with the BLE service active and no FIDO ceremony in progress. The package was terminated through Android's controlled `am crash` injection to exercise the service restart path.
- **revision**: Branch `064-bluetooth-transport-selection`; installed shrunkDebug candidate SHA-256 `082859FCC7745587269EAC1470DB55B317F6BA04A0331E31848475A62041F6BC`; updated in place with app data preserved.
- **named_procedure**: Start BLE and verify foreground service/GATT; run `adb shell am crash com.chimali.shrunk`; inspect `ApplicationExitInfo`, service foreground state, BLE GATT/advertiser registration and Chimali UI after relaunching MainActivity.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11 host paired; 2026-11-08 device time.
- **expected_observed_result**: Android recorded `APP CRASH(EXCEPTION)` for prior PID `6352`. ActivityManager restarted PID `6973`; `Fido2TransportService` returned foreground with `startRequested=true` and `lastStartId=2`. Bluetooth diagnostics showed the Chimali advertiser active and FIDO service `0xFFFD` started. After MainActivity was reopened, the screen showed `Ready for Authentication` and FIDO Bluetooth LE.
- **limitations**: Controlled crash injection is not natural OS memory-pressure termination. No FIDO request was active, and this does not verify mid-ceremony recovery or Mi/API29 behavior. The result supplements but does not close the parent's full T035 matrix; T038 coverage/nonregression remains owner-deferred.
- **status**: Passed (controlled active BLE service process-crash restart and advertiser restoration on ASUS/API35); Partial (natural system kill and in-flight recovery).
- **artifact_link**: Parent Record 202; `.tmp/spec-064/asus-record-202-installed.apk` (hash matches local candidate); `.tmp/spec-064/asus-record-202-exit-info.txt`; `.tmp/spec-064/asus-record-202-after-crash-services.txt`; `.tmp/spec-064/asus-record-202-bluetooth-manager.txt`; `.tmp/spec-064/asus-record-202-after-restart.xml`.

### Record 191: Physical System-Back Authentication Retries Remain Inconclusive (Parent T035 / SC-005)
- **requirement**: Parent-gated T035 / SC-005; preserve the active FIDO ceremony while system Back dismisses Transport Settings and restores its consent prompt.
- **scope**: Repeated user-reported ASUS authentication attempts using Android system Back during the operation. Windows again showed `No se pudo iniciar sesión. Vuelve a intentarlo y asegúrate de completar la acción de seguimiento` on the latest attempt.
- **revision**: Branch `064-bluetooth-transport-selection`; ASUS installed shrunkDebug candidate SHA-256 `082859FCC7745587269EAC1470DB55B317F6BA04A0331E31848475A62041F6BC`; no BLE source behavior was changed for this attempt.
- **named_procedure**: Start authentication, open Chimali Transport Settings during the pending operation, invoke Android system Back, then inspect the phone and Windows result.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11; 2026-11-07 local date.
- **expected_observed_result**: The user reports another failed Windows ceremony. A post-attempt ASUS UI snapshot shows Ready for Authentication with FIDO Bluetooth LE active and its foreground transport service running. The available logcat sample contains no correlated CTAP operation.
- **limitations**: Confirmation is pending on whether the same consent prompt reappeared. Without a correlated CTAP trace, the Windows error cannot identify whether the Back route, pending request lifecycle, or another host-side follow-up action caused the failure. This does not close BLE lifecycle acceptance or parent SC-005.
- **status**: Failed (user-reported BLE authentication); Inconclusive (Back route and consent restoration).
- **artifact_link**: Parent Record 203; `.tmp/spec-064/asus-record-203-after-system-back.xml`; `.tmp/spec-064/asus-record-203-correlated-logcat.txt`; `.tmp/spec-064/asus-system-back-latest.xml`.

### Record 192: ASUS BLE Radio-Off Error and Retry Recover on the Current Candidate (T035 — Partial)
- **requirement**: BLE T035 / parent T035; report Bluetooth unavailability without fallback and recover the selected BLE advertiser after the user restores Bluetooth and retries.
- **scope**: Current ASUS/API35 BLE candidate with no ceremony active. Bluetooth was disabled and re-enabled through Android's service control, followed by the app's explicit Retry action.
- **revision**: Branch `064-bluetooth-transport-selection`; installed shrunkDebug APK SHA-256 `082859FCC7745587269EAC1470DB55B317F6BA04A0331E31848475A62041F6BC`.
- **named_procedure**: Confirm BLE `Ready for Authentication` and active foreground service; run `adb shell svc bluetooth disable`; inspect the app's failure state and Bluetooth setting; run `adb shell svc bluetooth enable`; confirm the error remains pending explicit retry; tap `Retry Authenticator`; inspect the restored UI and foreground service.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; FIDO Bluetooth LE; 2026-11-07 local date.
- **expected_observed_result**: Disabling the adapter changed Chimali to `Error Occurred` with `Bluetooth is off; restore Bluetooth and retry` and the system setting reported `0`. Restoring Bluetooth set it to `1` but kept the app in the retryable error state. Tapping Retry returned Chimali to `Ready for Authentication` / `Waiting for request from PC`; `Fido2TransportService` remained foreground with `startRequested=true` and `lastStartId=3`.
- **limitations**: No Windows connection or FIDO ceremony was active, and no CTAP request was interrupted. This verifies adapter loss and explicit BLE recovery on the current ASUS candidate, not host retry during an active operation. Parent T035 remains open for in-flight consent/system-Back, reboot and remaining lifecycle cases; BLE T038 remains owner-deferred.
- **status**: Passed (BLE unavailable-state message and explicit Retry restored the foreground service and ready UI); Partial (no host ceremony/reconnect tested).
- **artifact_link**: Parent Record 205; `.tmp/spec-064/asus-record-205-bluetooth-off.xml`; `.tmp/spec-064/asus-record-205-radio-restored-before-retry.xml`; `.tmp/spec-064/asus-record-205-after-retry.xml`; `.tmp/spec-064/asus-record-205-service.txt`; `.tmp/spec-064/asus-record-205-bluetooth-manager.txt`; `.tmp/spec-064/asus-record-205-recovery-logcat.txt`.

### Record 193: ASUS BLE GATT, Service Lifecycle and Consent Settings Device Suites Pass (T036 / T038 — Focused Device Pass)
- **requirement**: BLE T036 device-suite validation; BLE T038 local validation context. Coverage floors, completeness and accepted-target nonregression remain deferred under parent T039.
- **scope**: Current ASUS/API35 candidate; execute the focused BLE GATT security, foreground-service lifecycle and consent-time transport-settings UI suites in the isolated feature test application.
- **revision**: Branch `064-bluetooth-transport-selection`; current working-tree build; production shrunkDebug package SHA-256 `082859FCC7745587269EAC1470DB55B317F6BA04A0331E31848475A62041F6BC`.
- **named_procedure**: Build `:feature:fido2:assembleAndroidDeviceTest`; stop the production authenticator and verify Ready to Start/no active transport service; install only `com.chimali.fido2.test`; invoke AndroidJUnitRunner with `FidoBleSecurityDeviceTest`, `FidoBleLifecycleDeviceTest` and `TransportSettingsDuringCeremonyTest`; remove the isolated test package; reopen the production app and explicitly start BLE.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Windows 11 development host; Gradle 9.6.0; 2026-11-08 device time.
- **expected_observed_result**: AndroidJUnitRunner reported `OK (9 tests)` in 4.304 seconds (4 GATT security, 3 transport-service lifecycle and 2 transport-settings UI tests). The isolated test package was uninstalled. Production `com.chimali.shrunk` retained its install and update timestamps; its local app data was not cleared. The BLE authenticator returned to `Ready for Authentication` with `Fido2TransportService` foreground.
- **limitations**: This selected subset does not include all configured Android device suites, a live Windows WebAuthn ceremony, discovery from a separate advertiser, Mi/API29 behavior, coverage completeness/floors or accepted-target raw-count comparison. It supplements Record 069 but does not close BLE T038's coverage/nonregression obligations. The physical in-flight system-Back outcome remains inconclusive in parent Record 203 / BLE Record 191.
- **status**: Passed (selected ASUS/API35 BLE device tests, 9/9); Partial (full BLE/parent acceptance, physical host ceremony and coverage/nonregression remain outstanding).
- **artifact_link**: Parent Record 208; `.tmp/spec-064/asus-record-208-device-test-output.txt`; `.tmp/spec-064/asus-record-208-stopped.xml`; `.tmp/spec-064/asus-record-208-restored.xml`; `.tmp/spec-064/asus-record-208-restored-service.txt`; `feature/fido2/build/outputs/apk/androidTest/fido2-androidTest.apk`.

### Record 194: ASUS BLE Saved Choice Persists Through Force-Stop (Parent T035 / BLE T035 — Partial)
- **requirement**: Parent T035 and BLE lifecycle persistence; preserve the selected BLE mode through force-stop, require explicit Start after relaunch and restore advertising without data loss.
- **scope**: Current ASUS/API35 BLE installation; force-stop only the production package, reopen MainActivity, inspect saved/process choice, then start BLE explicitly.
- **revision**: Branch `064-bluetooth-transport-selection`; installed shrunkDebug APK SHA-256 `082859FCC7745587269EAC1470DB55B317F6BA04A0331E31848475A62041F6BC`.
- **named_procedure**: Follow the parent quickstart T035 lifecycle procedure: `adb shell am force-stop com.chimali.shrunk`; launch MainActivity; inspect dashboard and Transport Settings; tap Start Authenticator; confirm BLE ready and foreground service active.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; `com.chimali.shrunk`; FIDO Bluetooth LE; 2026-11-08 device time.
- **expected_observed_result**: MainActivity relaunched to `Ready to Start`. Settings reported `Selected for this process: FIDO Bluetooth LE` / `Selected; not started`, and no `Fido2TransportService` remained after force-stop/relaunch. After explicit Start, the app showed `Ready for Authentication` and the transport service was foreground. Production package install/update timestamps were identical before and after, confirming the app package/data were not reset.
- **limitations**: This is ASUS BLE force-stop persistence only; it does not test host reconnection, Classic persistence on ASUS, BLE behavior on Mi/API29 or in-flight ceremony recovery. See parent Record 209; remaining T035 lifecycle cases remain open.
- **status**: Passed (selected BLE persisted and resumed only after explicit Start); Partial (complete parent lifecycle matrix remains open).
- **artifact_link**: Parent Record 209; `.tmp/spec-064/asus-record-209-package-before.txt`; `.tmp/spec-064/asus-record-209-package-after.txt`; `.tmp/spec-064/asus-record-209-before-force-stop.xml`; `.tmp/spec-064/asus-record-209-after-force-stop-services.txt`; `.tmp/spec-064/asus-record-209-after-relaunch.xml`; `.tmp/spec-064/asus-record-209-after-relaunch-services.txt`; `.tmp/spec-064/asus-record-209-settings-after-relaunch.xml`; `.tmp/spec-064/asus-record-209-after-explicit-start.xml`; `.tmp/spec-064/asus-record-209-after-explicit-start-service.txt`.

### Record 195: ASUS Idle BLE Settings Close on Android Edge-Swipe Back (Parent T035 / BLE T035 — Partial)
- **requirement**: Parent T035 and BLE lifecycle; Android system Back closes Transport Settings while the BLE authenticator remains active.
- **scope**: Current ASUS/API35 BLE production build; idle Settings overlay closed with a left-edge Android Back gesture synthesized through ADB; no FIDO ceremony was active.
- **revision**: Branch `064-bluetooth-transport-selection`; installed shrunkDebug APK SHA-256 `082859FCC7745587269EAC1470DB55B317F6BA04A0331E31848475A62041F6BC`.
- **named_procedure**: Open Settings; confirm `Active in this process: FIDO Bluetooth LE`; run `adb shell input swipe 1 1200 720 1200 350`; inspect the returned screen and transport foreground service.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; FIDO Bluetooth LE; 2026-11-08 device time.
- **expected_observed_result**: Settings closed to `Ready for Authentication` / `Waiting for request from PC`; the FIDO transport service remained foreground and active.
- **limitations**: This is an ADB-synthesized idle system gesture. It does not test a human Back gesture or the same consent prompt during a live host ceremony. The latter remains inconclusive in BLE Record 191 / parent Record 203.
- **status**: Passed (idle BLE Settings edge-swipe Back and service retention); Partial (live in-flight restoration unverified).
- **artifact_link**: Parent Record 211; `.tmp/spec-064/asus-record-211-settings-before-gesture.xml`; `.tmp/spec-064/asus-record-211-after-edge-swipe.xml`; `.tmp/spec-064/asus-record-211-service.txt`.

### Record 196: Parent Classic HID Service Restart Verified on ASUS (Parent-Gated Lifecycle Evidence)
- **requirement**: Parent-gated process lifecycle coordination; verify the alternative Classic transport restart while retaining BLE as the product default afterward.
- **scope**: Parent T035 cross-mode lifecycle evidence. This record links to the parent's controlled Classic process-crash recovery; the BLE implementation itself was not changed.
- **revision**: Branch `064-bluetooth-transport-selection`; ASUS shrunkDebug build hash `082859FCC7745587269EAC1470DB55B317F6BA04A0331E31848475A62041F6BC`.
- **named_procedure**: Review parent Record 212 for Classic save-pending, force-stop/relaunch, foreground HID service, controlled `am crash`, sticky-service restart and BLE restoration steps.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; parent transport settings used FIDO BLE default and Bluetooth HID (Classic) alternative; 2026-11-08 device time.
- **expected_observed_result**: Android restarted the Classic-selected `Fido2TransportService` after controlled `APP CRASH(EXCEPTION)`; the new process reported Classic active and `lastStartId=2`. BLE was subsequently restored and returned to foreground Ready for Authentication.
- **limitations**: This parent-gated test covers only controlled crash injection, not natural OS memory pressure, a Windows WebAuthn ceremony, or an active CTAP request. It supplements BLE lifecycle evidence but does not close the parent lifecycle matrix or BLE coverage gate.
- **status**: Passed (parent Classic service restart and BLE default restoration on ASUS/API35); Partial (natural process death and ceremony recovery remain open).
- **artifact_link**: Parent Record 212; `.tmp/spec-064/asus-record-212-exit-info.txt`; `.tmp/spec-064/asus-record-212-services-after-crash.txt`; `.tmp/spec-064/asus-record-212-bluetooth-after-crash.txt`; `.tmp/spec-064/asus-record-212-after-crash-settings.xml`; `.tmp/spec-064/asus-record-212-final-ble.xml`; `.tmp/spec-064/asus-record-212-final-ble-service.txt`.

### Record 197: Current ASUS BLE Advertiser Recovers After Permission Revocation (T035 — Partial)
- **requirement**: BLE T035; recover the selected BLE transport after Android revokes its advertiser permission, preserving the saved selection and app data.
- **scope**: Current parent-gated ASUS/API35 build; revoke and regrant `BLUETOOTH_ADVERTISE` while idle, then explicitly restart BLE.
- **revision**: Branch `064-bluetooth-transport-selection`; current installed shrunkDebug APK SHA-256 `082859FCC7745587269EAC1470DB55B317F6BA04A0331E31848475A62041F6BC`.
- **named_procedure**: Run `adb shell pm revoke com.chimali.shrunk android.permission.BLUETOOTH_ADVERTISE`; inspect permission/PID; run `adb shell pm grant com.chimali.shrunk android.permission.BLUETOOTH_ADVERTISE`; launch MainActivity; verify Ready to Start; tap Start and inspect BLE advertising/service state.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; package `com.chimali.shrunk`; FIDO BLE; 2026-11-08 device time.
- **expected_observed_result**: Android reported the advertiser permission denied, and app PID changed from `20929` to `21425`. After permission regrant and app launch, Chimali showed Ready to Start; explicit Start returned it to Ready for Authentication with the foreground FIDO service and advertiser registered. Production app install/update timestamps were unchanged.
- **limitations**: This is idle permission revocation, not revocation during an active CTAP operation or the physical Windows ceremony. Parent T035 remains open for in-flight consent recovery and remaining lifecycle cases. BLE T038 coverage/nonregression is still owner-deferred.
- **status**: Passed (current-build idle permission regrant and explicit BLE advertising recovery); Partial (in-flight interruption and coverage gate remain open).
- **artifact_link**: Parent Record 213; `.tmp/spec-064/asus-record-213-pid-before-revoke.txt`; `.tmp/spec-064/asus-record-213-pid-after-revoke.txt`; `.tmp/spec-064/asus-record-213-permission-before.txt`; `.tmp/spec-064/asus-record-213-permission-after-revoke.txt`; `.tmp/spec-064/asus-record-213-permission-after-regrant.txt`; `.tmp/spec-064/asus-record-213-after-regrant-before-start.xml`; `.tmp/spec-064/asus-record-213-after-explicit-start.xml`; `.tmp/spec-064/asus-record-213-service-after-start.txt`; `.tmp/spec-064/asus-record-213-bluetooth-after-start.txt`.

### Record 198: ASUS Android Performance Trace Device Test Passes with Perfetto (Tier 1 — Focused Device Pass)
- **requirement**: BLE Tier 1 observability; verify the implemented synthetic FIDO operation trace section emits and correlates on a supported Android device when tracing is enabled.
- **scope**: One focused `PerformanceTraceDeviceTest` on ASUS/API35 with a Perfetto session whose `atrace_apps` targets `com.chimali.fido2.test`. No relying-party traffic, coverage aggregation or transport ceremony was performed.
- **revision**: Branch `064-bluetooth-transport-selection`; working-tree revision `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; instrumentation APK built by `:feature:fido2:connectedAndroidDeviceTest`.
- **named_procedure**: Configure and start Perfetto with the FIDO2 test package in `atrace_apps`; run `adb shell am instrument -w -e class com.chimali.fido2.util.performance.PerformanceTraceDeviceTest com.chimali.fido2.test/androidx.test.runner.AndroidJUnitRunner`; stop the trace session and retain test output and trace.
- **environment**: ASUS AI2302 / Android 15 API35 / serial `R7AIB7002901AZ3`; Android Perfetto v46.0; 2026-11-08 local date.
- **expected_observed_result**: Focused instrumented test returned `OK (1 test)` with Perfetto active. The trace was 8,321,391 bytes, SHA-256 `6FD9153CF91DE676086FDC0ACFF6BC730BCA8284F6A30516DB525A1DF40B3844`.
- **limitations**: This verifies only the selected trace-emission test and its explicit tracing prerequisite. It does not validate empirical calibration/pilot datasets, full connected-suite acceptance, coverage floors or accepted-target nonregression. Parent T039 and BLE T038 remain open/deferred.
- **status**: Passed (focused Android trace instrumentation with captured Perfetto evidence); Partial (full device suite and acceptance gates remain outstanding).
- **artifact_link**: `.tmp/spec-064/fido2-perfetto-test.pbtxt`; `.tmp/spec-064/fido2-perfetto-test.trace`; `.tmp/spec-064/fido2-perfetto-device-test.txt`.

### Record 199: ASUS Discovers Mi FIDO Advertisement and Completes 50 GATT Reconnect Cycles (Tier 1 — Device Pass)
- **requirement**: FR-BLE-010 / TR-BLE-010 remote service discovery and FR-BLE-060 / FR-BLE-070 / TR-BLE-030 repeated connection cleanup and recovery.
- **scope**: ASUS/API35 operated as scanner and GATT client; Mi A2/API29 advertised the actual FIDO BLE service. The 50-cycle reconnect stress case was explicitly enabled. Both production app installs and local app data were retained.
- **revision**: Branch `064-bluetooth-transport-selection`; working-tree revision `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; test APK from current worktree.
- **named_procedure**: Start BLE advertising on Mi; run `FidoBleAdvertiserDiscoveryDeviceTest` on ASUS; run `FidoBleReconnectStressDeviceTest` with `runFidoBleReconnectStress=true` on ASUS; inspect test output and recorded memory values.
- **environment**: ASUS AI2302 / Android 15 API35 / `R7AIB7002901AZ3`; Mi A2 / Android 10 API29 / `2ac6876`; 2026-11-08.
- **expected_observed_result**: Mi was advertising FIDO Bluetooth LE with `Fido2TransportService` foreground. ASUS discovered a distinct remote FIDO service and discovery passed `OK (1 test)`. The 50-cycle GATT discovery/disconnect/reconnect stress test passed `OK (1 test)` in 31.834 seconds. Java heap was 6,611,488 bytes before and 3,240,480 after; native heap was 8,729,888 bytes before and 8,466,352 after. ASUS remained BLE Ready for Authentication afterward.
- **limitations**: The heap comparison is a before/after sample, not proof of leak-free long-run memory or exact baseline equivalence. This covers one ASUS↔Mi device pair only. It does not establish broader vendor compatibility, parent full connected suite, coverage floors or accepted-target nonregression. A later Mi reboot disconnected ADB; parent Record 221 retains the unverified reboot outcome.
- **status**: Passed (remote FIDO advertising discovery and all 50 opt-in GATT reconnect cycles); Partial (device pair and memory scope limited; coverage gate remains owner-deferred).
- **artifact_link**: `.tmp/spec-064/mi-two-device-tests-2026-11-08.txt`; `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302 - 15.xml` (earlier full connected suite only; focused test output is preserved in the artifact file).

### Record 200: Mi BLE Selection Persists Across Reboot and Advertising Resumes After Explicit Start (Parent T035 / BLE T035 — Passed Scoped Check)
- **requirement**: Parent T035 / SC-004 and BLE lifecycle requirement; preserve the saved BLE choice through reboot, keep advertising stopped until explicit user action, then restore BLE service operation.
- **scope**: Mi A2/API29 after the reboot recorded as unverified in parent Record 221. Reconnect Mi to ADB without reinstalling or clearing data; launch MainActivity, inspect the transport choice and service state, then tap Start Authenticator.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus existing staged/unstaged changes; installed package `com.chimali.shrunk` version 0.1.0.
- **named_procedure**: Reconnect the unlocked phone and approve USB debugging; inspect app package identity and service state; open MainActivity; capture the Ready to Start screen; tap Start Authenticator; inspect UI, foreground service and Bluetooth Manager registration.
- **environment**: Mi A2 / Android 10 API29 / serial `2ac6876`; Windows 11; 2026-11-08 local date.
- **expected_observed_result**: Package install and update times remained `2026-11-07 15:53:12` and `2026-11-07 16:28:51`. Before Start, MainActivity showed `Ready to Start` and `FIDO2 / WebAuthn over FIDO Bluetooth LE`; no `Fido2TransportService` was running. After tapping Start, UI showed `Advertising...` / `Ready to pair with a new host PC`, the FIDO service was foreground with `startRequested=true`, and Bluetooth Manager listed `com.chimali.shrunk (Registered)`.
- **limitations**: Verifies saved BLE selection and explicit-start recovery for this Mi reboot only. It does not verify Mi Windows WebAuthn, natural OS process reclamation, in-flight reboot, or the full T035 lifecycle matrix. The earlier ADB disconnect made the check delayed; once reconnected, it was completed without reinstalling or clearing app data.
- **status**: Passed (Mi/API29 BLE selection persisted through reboot; advertising resumed only after explicit Start); Partial (broader T035 acceptance and owner-deferred coverage/nonregression remain open).
- **artifact_link**: `.tmp/spec-064/mi-postboot-check-2026-11-08.txt`; `.tmp/spec-064/mi-postboot.png` (Ready to Start); `.tmp/spec-064/mi-postboot-after-start.png` (Advertising); device UI dump `/sdcard/chimali-mi-postboot-active.xml`.

### Record 201: Mi Classic HID Bind Timeout Is Surfaced and BLE Recovers (Parent T035 — Passed Scoped Check)
- **requirement**: Parent T035 / FR-BT-SEL-050 / TR-BT-SEL-020; report a transport-specific startup failure with recovery guidance and restore the available BLE transport.
- **scope**: Mi A2/API29, existing `com.chimali.shrunk` installation updated in place; selected Classic HID, initiated start after Android's 120-second Bluetooth visibility consent, then switched back to BLE and explicitly started it. No app data was cleared.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus existing staged/unstaged changes; shrunkDebug APK SHA-256 `14E005B44A5D9762F91FF428AFE72FA38E6AC7EA5FD2A595008ABA3E92CD7E29`.
- **named_procedure**: Build `:app:assembleShrunkDebug`; install with `adb install -r`; save Classic in Chimali Transport Settings; force-stop/relaunch; explicitly Start; inspect home screen, Settings and Android logs. Save BLE, force-stop/relaunch, explicitly Start, and inspect foreground service plus Bluetooth Manager registration.
- **environment**: Mi A2 / Android 10 API29 / serial `2ac6876`; Windows development host; Gradle 9.6.0; 2026-11-08.
- **expected_observed_result**: Focused `Fido2HomeViewModelTest` passed 3/3 with zero failures/errors. Classic startup failed because Android could not bind `android.bluetooth.IBluetoothHidDevice`; the connect attempt timed out after 5 seconds. The dashboard displayed `Error Occurred`, `Bluetooth transport initialization timed out. Retry or choose another transport in Settings.`, and `Retry Authenticator`. Transport Settings reported `Startup timed out`. After selecting BLE and restarting the app process, explicit Start restored `Advertising...`; the FIDO foreground service had `startRequested=true`, and Bluetooth Manager listed `com.chimali.shrunk (Registered)`.
- **limitations**: This verifies typed startup-timeout guidance and BLE recovery on Mi/API29. Classic HID itself is unavailable on this Android 10 device, and the timeout is the observed unsupported platform path; no Windows WebAuthn ceremony or Classic HID pairing was attempted. It does not close the broader T035 lifecycle matrix or owner-deferred coverage/nonregression gate.
- **status**: Passed (failure surfaced and BLE recovery verified); Partial (Classic unavailable on Mi/API29 and broader lifecycle remains open).
- **artifact_link**: `.tmp/spec-064/mi-classic-dashboard-error.png`; `.tmp/spec-064/mi-settings-classic-error.png`; `.tmp/spec-064/mi-classic-startup-result.png`; `.tmp/spec-064/mi-classic-timeout-log.txt`; `.tmp/spec-064/mi-ble-restored-after-classic-failure.png`.

### Record 202: Repository Non-Coverage Local CI Passes (T038 — Partial)
- **requirement**: BLE T038 / parent T039; validate static analysis, compilation and host/unit tests separately from coverage acceptance.
- **scope**: Current staged/unstaged worktree after dashboard failure-guidance changes; ordinary local CI only.
- **revision**: Branch `064-bluetooth-transport-selection`; HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; candidate tree was dirty.
- **named_procedure**: `pwsh -File tools/local-ci.ps1 -NoFix -NoConfigurationCache` with `GRADLE_USER_HOME=.tmp/gradle-home`.
- **environment**: Windows development host; Gradle 9.6.0; 2026-11-08 local date.
- **expected_observed_result**: PASS in 206.05 seconds for KtLint, Detekt, Release lint, comprehensive compilation, unit tests and KMP Android host tests. Fresh app/core/feature XML contained 1,156 tests with zero failures/errors/skips.
- **limitations**: Coverage `-RunCoverage`, coverage completeness/floors, trusted accepted-target nonregression, connected device suites and hosted acceptance were not run. Dirty candidate evidence cannot satisfy T038's exact clean-candidate comparison; T038 remains open.
- **status**: Passed (non-coverage local CI); Unverified (coverage and accepted-target nonregression).
- **artifact_link**: `.tmp/spec-064/local-ci-2026-11-08.txt`; Gradle test XML under app/core/feature build outputs.

### Record 203: Mi/API29 BLE Advertising Recovers After Bluetooth-Off Retry (Parent T035 — Passed Scoped Check)
- **requirement**: Parent T035 / FR-BT-SEL-050 / BLE interruption and advertising recovery; report radio loss and recover after Bluetooth returns.
- **scope**: Existing Mi A2/API29 `com.chimali.shrunk` installation, BLE actively advertising; real Android Quick Settings radio-off/on and in-app Retry; app data retained.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; installed current shrunkDebug candidate with the dashboard failure guidance.
- **named_procedure**: Confirm BLE Advertising and foreground service; switch Bluetooth off with the Android Quick Settings tile; reopen Chimali and inspect error/retry state; restore Bluetooth with the tile; tap Retry Authenticator; inspect UI, service and Bluetooth Manager registration.
- **environment**: Mi A2 / Android 10 API29 / serial `2ac6876`; 2026-11-08 device time.
- **expected_observed_result**: With Bluetooth global setting `0`, Chimali displayed `Error Occurred`, `Bluetooth is off; restore Bluetooth and retry`, and `Retry Authenticator`. After radio restoration (global setting `1`) and tapping Retry, the app returned to `Advertising...`; `Fido2TransportService` remained foreground with `startRequested=true`, and Bluetooth Manager listed `com.chimali.shrunk (Registered)`. The retry intent was observed in logcat.
- **limitations**: No host connection or WebAuthn ceremony was active, so this proves idle radio-off/retry recovery only. `adb shell svc bluetooth disable` was denied on this device; the controlled UI Quick Settings Bluetooth tile was used instead. This does not close parent T035 or the coverage/nonregression gate.
- **status**: Passed (Mi/API29 BLE error guidance and advertising recovery); Partial (no in-flight ceremony, full T035 remains open).
- **artifact_link**: `.tmp/spec-064/mi-ble-radio-recovery-2026-11-08.txt`; `.tmp/spec-064/mi-ble-radio-recovery-logcat-2026-11-08.txt`; `.tmp/spec-064/mi-bt-off-main-current.png`; `.tmp/spec-064/mi-bt-retry-result.png`.

### Record 204: All Typed Transport-Unavailable Dashboard Messages Remain Retryable (Parent T035 / BLE T027 — Passed Host Check)
- **requirement**: FR-BT-SEL-050 / TR-BT-SEL-020 and BLE T027; present actionable guidance for every typed unavailability reason and retain a retry path.
- **scope**: `Fido2HomeViewModel` dashboard mapping and transport toggle action, exercised with deterministic Android host tests; no hardware state changed.
- **revision**: Branch `064-bluetooth-transport-selection`; source HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c` plus staged/unstaged changes.
- **named_procedure**: Run `:feature:fido2:ktlintAndroidHostTestSourceSetCheck` and `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.presentation.viewmodel.Fido2HomeViewModelTest` with Gradle 9.6.0 and in-process Kotlin compiler.
- **environment**: Windows development host; Gradle 9.6.0; 2026-11-08 local date.
- **expected_observed_result**: Build passed; the focused ViewModel suite passed 3/3 with zero failures/errors/skips. Assertions cover PreferenceReadFailed, InvalidPreference, InitializationTimeout, Unsupported, PermissionRequired, BluetoothOff and StartupFailed; each maps to its specific dashboard error and Retry action.
- **limitations**: Host tests verify reason-to-message/action mapping but do not replace the Mi/ASUS physical recovery procedures or complete parent T035 acceptance.
- **status**: Passed (typed guidance and retry mapping); Partial (physical lifecycle matrix remains open).
- **artifact_link**: `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.presentation.viewmodel.Fido2HomeViewModelTest.xml`; `.tmp/spec-064/local-ci-2026-11-08.txt` (full non-coverage CI run before the expanded reason matrix).

### Record 205: Authorized Coverage Gate Attempt Did Not Complete (BLE T038 / Parent T039 — Unverified)
- **requirement**: BLE T038 defers to parent T039 for local completeness/floors and accepted-target raw-count comparison.
- **scope**: Current staged/unstaged worktree; Gradle local CI and connected FIDO2 suites on both attached Android devices.
- **revision**: Branch `064-bluetooth-transport-selection`; HEAD `39acc7f87768ab5a6bbe539ae31e4f7c386f667c`; working tree dirty, so this was not an exact committed candidate.
- **named_procedure**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage`, Gradle 9.6.0, `GRADLE_USER_HOME=.tmp/gradle-home`.
- **environment**: Windows host; ASUS AI2302/API35 (`ASUS_AI2302 - 15`) and Mi A2/API29 (`Mi A2 - 10`), both connected by ADB; 2026-11-08.
- **expected_observed_result**: KtLint, Detekt, Release lint, Compile All, Unit Tests and KMP Android Host Tests passed. Coverage execution then failed at `:feature:fido2:connectedAndroidDeviceTest` after 349.58 seconds. The two device reports contain 123 tests total: ASUS 63 tests / 6 failures, Mi 60 / 7 failures; 110 cases passed. Twelve failures were opt-in multi-device stress or Perfetto/session prerequisites; Mi additionally failed the distinct remote FIDO advertiser discovery prerequisite. The workflow stopped before fresh canonical coverage counters and `hdkCoverageVerify` were produced.
- **limitations**: Coverage completeness, Tier 1/Tier 2/repository floors and nonregression are Unverified. The prior indexed baseline targets `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`; live GitHub ref lookup failed for missing credentials, and the checkout is dirty. No `tools/integration-ci.ps1` comparison was run. Hosted integration remains waived and its runner disabled.
- **status**: Unverified (coverage gate and accepted-target comparison did not complete; no pass is claimed).
- **artifact_link**: `D:\octav\source\repos\.worktrees\chimali\coverage-evidence\064-coverage-2026-11-08\coverage-attempt-summary.json`; `TEST-ASUS_AI2302 - 15.xml`; `TEST-Mi A2 - 10.xml`.

### Record 206: Rebased Candidate Local CI Passes; BLE Coverage Capture Remains Incomplete (T038 — Unverified)
- **requirement**: BLE T038; complete current-tree coverage, required floors and accepted-target raw-count nonregression, coordinated by parent T039.
- **scope**: Exact clean rebased candidate, ordinary local CI and a single-device ASUS/API35 candidate coverage capture.
- **revision**: Branch `064-bluetooth-transport-selection`; candidate `6c483486f83a8df20d8fdc59a05a05ce9558c567`; tree `5423439e15fb5c832fecad43e05eaadfc8a8e490`; target `e7918b6e256319b1305536c7815a92f6132ece99` is an ancestor.
- **named_procedure**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache`, followed by candidate capture run `5eae2e16-2add-4256-847f-dae87026a1dd` using `tools/coverage/capture.ps1` and ASUS serial `R7AIB7002901AZ3`.
- **environment**: Windows; Gradle 9.6.0, Java 21.0.10, Kotlin 2.3.21, AGP 9.4.1, JaCoCo 0.8.15; ASUS AI2302 / Android 15 API35; 2026-11-08.
- **expected_observed_result**: Exact-candidate KtLint, Detekt, Release lint, comprehensive compilation, unit tests and KMP Android host tests passed in 197.87 seconds. Coverage capture ended Incomplete/Failed because the connected FIDO2 suite had six setup/precondition failures: remote-advertiser/reconnect stress was not opted in with a distinct advertiser, and Perfetto research/trace tests lacked the declared session. Although partial module counters were emitted, the capture reported changed/missing execution inputs and attribution mismatches.
- **limitations**: The incomplete capture cannot establish inventory completeness, module floors, repository floors or nonregression. The local evidence-root index points to old target `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`; the canonical archived index observed on `swe/coverage-evidence` is for protected `main` at `f1fd56baf8641cea99edc7fafa8725fdda21044c`. Neither matches target `e7918b6e256319b1305536c7815a92f6132ece99`. No accepted index was altered or created from a target-only measurement. Parent T039 and BLE T038 remain open; hosted enforcement remains waived/disabled.
- **status**: Passed (exact-candidate non-coverage local CI); Unverified (coverage gate and accepted-target comparison).
- **artifact_link**: `D:\octav\source\repos\.worktrees\chimali\coverage-evidence\candidate-6c48348\5eae2e16-2add-4256-847f-dae87026a1dd\summary.md`; `manifest.json`; `counters.json`; `reports/tests`; parent Record 232.

### Record 207: Exact Rebased Target Baseline Recapture Still Incomplete (T038 — Unverified)
- **requirement**: BLE T038; complete coverage and accepted-target raw-count nonregression, coordinated by parent T039.
- **scope**: Fresh measurement of the exact rebased target in an isolated clean checkout. No candidate comparison or baseline promotion was performed.
- **revision**: Target/harness `e7918b6e256319b1305536c7815a92f6132ece99`; target tree `afe0683226166775f4fccdfc8accb06c46bd5697`.
- **named_procedure**: `tools/coverage/capture.ps1` with Purpose `Comparison`, target equal to source revision, ASUS serial `R7AIB7002901AZ3`; run `57372dab-0995-436c-b07c-83c4f6d791d1`.
- **environment**: Windows; Gradle 9.6.0, Kotlin 2.3.21, AGP 9.4.1, JaCoCo 0.8.15; ASUS AI2302 / Android 15 API35; 2026-11-08.
- **expected_observed_result**: `:feature:fido2:connectedAndroidDeviceTest` reported 5 setup-dependent failures among 46 tests. The failed cases require the opt-in Perfetto/tracing session; capture stopped before canonical counters, complete evidence, or a target bundle was produced.
- **limitations**: No baseline was created, and the accepted-target index was not changed. BLE T038 and parent T039 remain Unverified. Retry requires a valid authorized Perfetto/session setup or a reviewed correction to the workload selection.
- **status**: Unverified (target measurement incomplete; not coverage evidence).
- **artifact_link**: Parent Record 233; `D:\octav\source\repos\.worktrees\chimali\coverage-evidence\recreated-baseline-e791\57372dab-0995-436c-b07c-83c4f6d791d1\summary.md`; target connected-test report in the isolated target checkout.

### Record 208: Routine Filter Verified; Initial Full Baseline Rehearsal Blocked by App Keystore Data (T038 — Unverified)
- **requirement**: BLE T038 complete target coverage and accepted-target raw-count nonregression under parent T039.
- **scope**: Verify the corrected Feature 058 filter on the exact target, then run its full host/device coverage task graph without accepting incomplete results.
- **revision**: Exact target `e7918b6e256319b1305536c7815a92f6132ece99`, tree `afe0683226166775f4fccdfc8accb06c46bd5697`; harness branch `064-bluetooth-transport-selection`, dirty at `6c483486f83a8df20d8fdc59a05a05ce9558c567` plus coverage-tool changes.
- **named_procedure**: Ran `:feature:fido2:connectedAndroidDeviceTest` with `tools/hdk-coverage.init.gradle` and AndroidJUnitRunner `notPackage=com.chimali.fido2.util.performance`, followed by manual `hdkHostCoverage hdkCoverageAnalyze` against the clean target. The latter was a rehearsal, not a `capture.ps1` bundle.
- **environment**: Windows; ASUS AI2302/API35 (`R7AIB7002901AZ3`); Gradle 9.6.0; AGP 9.4.1; 2026-11-08.
- **expected_observed_result**: FIDO2 connected tests passed 41/41 in 10 suites with no failures/errors/skips and none of the five excluded Perfetto suites. The full graph reached `:app:connectedShrunkDebugAndroidTest`; three `CredentialCryptoProviderTest` cases passed, but `LeakCanaryMnemonicOwnerTest` failed during app startup as Android Keystore raised `VERIFICATION_FAILED`/`AEADBadTagException` decrypting the installed `com.chimali` wallet database. `hdkCoverageAnalyze` did not complete. The Feature 058 policy suite passed 520/520.
- **limitations**: This record describes the initial failed rehearsal. Child Record 209 documents the later successful manual workload and analysis after clearing only the previously authorized `com.chimali.shrunk` package; separate `com.chimali` data remained untouched. No immutable target bundle, compatible index promotion or candidate comparison resulted.
- **status**: Passed (routine FIDO2 selection and policy enforcement); Unverified (full target coverage and comparison; no baseline promoted).
- **artifact_link**: Feature 058 evidence §§11.146–11.147; target ASUS FIDO2 JUnit `D:\octav\source\repos\.worktrees\chimali\baseline-e791-source\feature\fido2\build\outputs\androidTest-results\connected\androidMain\TEST-ASUS_AI2302 - 15.xml`; failed app JUnit `D:\octav\source\repos\.worktrees\chimali\baseline-e791-source\app\build\outputs\androidTest-results\connected\shrunkDebug\TEST-ASUS_AI2302 - 15.xml`; [trusted archive index](https://github.com/octaviordz/chimali/blob/swe/coverage-evidence/baselines/current.json).

### Record 209: Exact-Target Manual Coverage Analysis Recreated; Acceptance Still Pending (T038 — Unverified)
- **requirement**: BLE T038 target workload and accepted-target raw-count assessment, coordinated by parent T039.
- **scope**: Follow-up to Record 208. Cleared only `com.chimali.shrunk` under prior user authorization; exact-target full host/device workload and `hdkCoverageAnalyze` completed on ASUS.
- **revision**: Target `e7918b6e256319b1305536c7815a92f6132ece99`, tree `afe0683226166775f4fccdfc8accb06c46bd5697`; dirty current harness.
- **named_procedure**: Corrected routine Perfetto filter; manual `hdkHostCoverage hdkCoverageAnalyze`; app 11/11 and FIDO2 41/41 instrumentation, required core:common/settings device work and host suites completed. A subsequent verifier invocation reran connected tasks and failed at app Keystore startup.
- **environment**: ASUS AI2302 Android 15 API35 (`R7AIB7002901AZ3`); Windows; 2026-11-08.
- **expected_observed_result**: Repository instructions 77,209/134,569 (57.38%). Tier 1 instruction/branch: data 87.95%/77.03%, domain 71.33%/71.28%, security 81.40%/66.84%, FIDO2 64.47%/61.62%. Applicable Tier 2 instruction results exceed 20%.
- **limitations**: Manual output only; dirty harness prevents immutable capture. No successful floor-verifier receipt, accepted-index promotion or candidate comparison. Archive index still names target `f1fd56baf8641cea99edc7fafa8725fdda21044c`, incompatible with `e7918b6`.
- **status**: Manual target workload/analysis completed; T038 remains Unverified pending formal baseline and candidate comparison.
- **artifact_link**: Parent Record 235; Feature 058 §11.148; target `build/reports/hdk-coverage/counters.json` and `production-inventory.json`.

### Record 210: Feature Worktree Local CI Passed (T038 — Partial)
- **requirement**: BLE T038 local static analysis and required host tests before coverage comparison.
- **scope**: Current feature worktree local CI with optional connected coverage excluded.
- **revision**: HEAD `6c483486f83a8df20d8fdc59a05a05ce9558c567` plus current staged/unstaged changes.
- **named_procedure**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache`; Gradle report output disabled in the script to avoid a Windows denied-write failure.
- **environment**: Windows PowerShell; Gradle 9.6.0; 2026-11-08.
- **expected_observed_result**: KtLint, Detekt, Release lint, compile-all, unit tests and KMP Android host tests passed; exit code 0 in 74.67 seconds. Feature 058 offline policy fixtures passed 520/520; PowerShell parsing passed for the modified scripts.
- **limitations**: Local success does not establish connected-device coverage, accepted-target compatibility or raw-count nonregression. Those remain Unverified.
- **status**: Local checks passed; T038 remains Unverified pending formal target capture and candidate comparison.
- **artifact_link**: Parent Record 236; `tools/local-ci.ps1`; Feature 058 §11.148.

### Record 211: Exact-Target Full Coverage and Floor Verification Passed (T038 — Partial)
- **requirement**: BLE T038 target completeness, configured floors and accepted-target comparison, coordinated by parent T039.
- **scope**: Follow-up to BLE Record 209. Reset only `com.chimali.shrunk`, then ran the exact-target full `hdkCoverageVerify` task graph; separate `com.chimali` app data was preserved.
- **revision**: Target `e7918b6e256319b1305536c7815a92f6132ece99`, tree `afe0683226166775f4fccdfc8accb06c46bd5697`; dirty current harness.
- **named_procedure**: ASUS API35 with corrected routine Perfetto exclusions; app 11/11, FIDO2 41/41, core:common 3/3, settings device task and host suites passed; analysis and exact integer floor verifier passed. Gradle exit 0 in 13m04s.
- **environment**: ASUS AI2302 Android 15 API35 (`R7AIB7002901AZ3`); Windows; 2026-11-08.
- **expected_observed_result**: Repository instructions 77,209/134,569 (57.38%). Tier 1 instruction/branch: data 87.95%/77.03%, domain 71.33%/71.28%, security 81.40%/66.84%, FIDO2 64.47%/61.62%; applicable Tier 2 floors passed.
- **limitations**: Manual run only, no immutable capture. Accepted index remains bound to `f1fd56baf8641cea99edc7fafa8725fdda21044c`; no compatible baseline promotion or candidate raw-count comparison exists. T038 remains Unverified for the full gate.
- **status**: Target completeness and floors passed; formal capture, baseline review and candidate comparison remain open.
- **artifact_link**: Parent Record 237; Feature 058 §11.149; target counters/inventory and app/FIDO2 JUnit XML.

### Record 212: Candidate Coverage Floors Pass; T038 Accepted-Target Comparison Remains Unverified
- **requirement**: BLE T038 candidate completeness, configured floors and accepted-target raw-count nonregression under parent T039 coordination.
- **scope**: Full manual candidate `hdkCoverageVerify` on ASUS/API35 after Feature 058 runner updates; the production-ID `com.chimali` data was preserved.
- **revision**: Candidate `6c483486f83a8df20d8fdc59a05a05ce9558c567` plus dirty staged/unstaged harness and docs; target `e7918b6e256319b1305536c7815a92f6132ece99`.
- **named_procedure**: Full Gradle verifier graph with the reviewed routine Perfetto filter; all required device and host tasks completed; exit 0 in 32m42s. Exact integer coverage verifier passed.
- **environment**: ASUS AI2302 / Android 15 API35 (`R7AIB7002901AZ3`); Windows; Gradle 9.6.0; 2026-11-08.
- **expected_observed_result**: Tier 1 floors passed: data 1,277/1,452 instructions and 57/74 branches; domain 5,124/7,184 and 412/578; security 3,624/4,452 and 258/386; FIDO2 67,527/100,198 and 4,029/6,662. Repository was 88,322/149,128 instructions (59.23%); applicable Tier 2 floors passed.
- **limitations**: Dirty harness prevents immutable capture. Accepted index still targets `f1fd56baf8641cea99edc7fafa8725fdda21044c`; no accepted-target comparison/promotion. FIDO2 production inventory differs from target rehearsal, so raw counts are not a nonregression result. T038 remains Unverified for the complete gate.
- **status**: Candidate full workload and configured floors passed; formal capture and accepted-target comparison remain open.
- **artifact_link**: Parent Record 238; Feature 058 §11.150; current candidate counters/inventory and device JUnit reports.

### Record 213: Exact-Target Verifier Passes After Install-Then-Clear; BLE Coverage Comparison Remains Open (T038 — Unverified)
- **requirement**: BLE T038; complete the parent-coordinated exact target and candidate coverage comparison with a compatible indexed baseline.
- **scope**: Exact target `e7918b6e256319b1305536c7815a92f6132ece99` measured on ASUS/API35 using the current Feature 058 routine Perfetto filter. Clearing production package `com.chimali` did not solve the Keystore error. The app instrumentation package is separate `com.chimali.shrunk`; it was absent before Gradle's connected task, so the exact target APK was installed, that package was cleared, then the full verifier ran.
- **revision**: Target `e7918b6e256319b1305536c7815a92f6132ece99`, tree `afe0683226166775f4fccdfc8accb06c46bd5697`; dirty harness based on feature HEAD `6c483486f83a8df20d8fdc59a05a05ce9558c567` plus Feature 058 changes.
- **named_procedure**: `adb install -r app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk`, `adb shell pm clear com.chimali.shrunk`, and full ASUS/API35 `hdkCoverageVerify` with `notPackage=com.chimali.fido2.util.performance`; app suite 11/11, FIDO2 41/41, required core:common/settings device tasks and configured host tasks completed. Gradle daemon recorded successful completion. Feature 058 policy suite passed 520/520 after updating capture to build/install/clear in that order.
- **environment**: Windows 11; ASUS AI2302 Android 15/API35 (`R7AIB7002901AZ3`); Gradle 9.6.0; 2026-11-08.
- **expected_observed_result**: Exact target passed configured floors: repository 77,209/134,569 instructions (57.38%); Tier 1 data 87.95% instructions / 77.03% branches, domain 71.33% / 71.28%, security 81.40% / 66.84%, FIDO2 64.47% / 61.62%. No workload task reported failure.
- **limitations**: Manual verifier only; the dirty harness prevented immutable capture. Accepted index still targets `f1fd56baf8641cea99edc7fafa8725fdda21044c`; no compatible target baseline was promoted or used for candidate raw-count comparison. Parent T039 coordination therefore remains incomplete.
- **status**: Exact-target manual workload and floors passed; T038 remains Unverified pending immutable capture, accepted-target compatibility/review, and candidate comparison.
- **artifact_link**: Parent Record 239; Feature 058 §11.151; target `build/reports/hdk-coverage/counters.json` and `production-inventory.json`; FIDO2 connected JUnit report; `tools/coverage/capture.ps1`.

### Record 214: Routine BLE Coverage Workload and Floors Pass; Accepted-Target Comparison Remains Unverified
- **requirement**: BLE T038; complete the parent-coordinated coverage workload and compatible accepted-target raw-count assessment.
- **scope**: Current candidate ASUS/API35 manual verifier after Feature 058 explicitly excluded only the opt-in two-device reconnect stress test from the single-phone routine workload. The class needs a distinct FIDO BLE advertiser and remains a separate specialized procedure.
- **revision**: Candidate branch HEAD `6c483486f83a8df20d8fdc59a05a05ce9558c567` plus dirty working changes; parent target `e7918b6e256319b1305536c7815a92f6132ece99`.
- **named_procedure**: `hdkCoverageVerify` with the five reviewed Perfetto classes excluded by package and `FidoBleReconnectStressDeviceTest` excluded by exact AndroidJUnitRunner class filter. Exit 0 in 5m59s; FIDO2 JUnit 57/57 with zero failures/errors/skips; app 13/13; all required device/host workloads and configured floors passed. Feature 058 policy suite passed 520/520.
- **environment**: ASUS AI2302 / Android 15 API35 (`R7AIB7002901AZ3`); Windows 11; Gradle 9.6.0; 2026-11-08.
- **expected_observed_result**: FIDO2 Tier 1 67,685/100,179 instructions and 4,037/6,660 branches. Repository 89,780/149,027 instructions (60.24%); data 1,277/1,452 and 57/74; domain 5,124/7,184 and 412/578; security 3,624/4,452 and 258/386. Floors passed. Counter/inventory digests are in parent Record 240 and Feature 058 §11.152.
- **limitations**: Dirty harness prevents immutable capture. Accepted index remains for `f1fd56baf8641cea99edc7fafa8725fdda21044c`, incompatible with the target; no accepted-target comparison or promotion. User-authorized `com.chimali` data clearing occurred; the isolated `com.chimali.shrunk` test package was reset for the run. Hosted integration remains waived.
- **status**: Routine BLE coverage workload and floors pass; T038 remains Unverified until the parent coordinates immutable capture and a compatible accepted-target raw-count comparison.
- **artifact_link**: Parent Record 240; Feature 058 §11.152; candidate counters/inventory and connected JUnit reports.

### Record 215: Capture-Style Routine BLE Suite Filter Verified
- **requirement**: BLE T038; ensure the routine connected FIDO2 test suite excludes the opt-in two-device stress class while retaining regular BLE tests.
- **scope**: Focused Android device suite on ASUS/API35 using the same environment arguments used by `tools/coverage/capture.ps1`.
- **revision**: Candidate HEAD `6c483486f83a8df20d8fdc59a05a05ce9558c567` plus dirty harness changes.
- **named_procedure**: `:feature:fido2:connectedAndroidDeviceTest`, Perfetto `notPackage` plus exact BLE reconnect-stress `notClass`. Gradle exit 0 in 1m43s; FIDO2 JUnit 57/57 with zero failures/errors/skips and the opt-in class absent.
- **environment**: ASUS AI2302 / Android 15 API35 (`R7AIB7002901AZ3`); Windows 11; Gradle 9.6.0; 2026-11-08.
- **expected_observed_result**: The capture-style environment filters are honored by AndroidJUnitRunner; regular BLE device tests pass.
- **limitations**: Focused filter verification only. T038 still needs an immutable full candidate capture and compatible accepted-target raw-count comparison.
- **status**: Filter behavior verified; T038 remains Unverified.
- **artifact_link**: Parent Record 241; Feature 058 §11.153; FIDO2 connected JUnit report.

### Record 216: Exact-Target Capture Reveals Pre-Stress-Test Source Compatibility (T038 — Unverified)
- **requirement**: BLE T038; obtain a complete immutable exact-target comparison bundle and compare against reviewed accepted-target evidence.
- **scope**: Fresh exact-target capture attempt on ASUS/API35 using the committed candidate harness. Device data was not cleared; capture failed before device setup.
- **revision**: Candidate/harness commit `2ef5d87f74c8bd0f6b966380b1dc4d8ef9e6eab2`; one uncommitted Feature 058 harness correction; target `e7918b6e256319b1305536c7815a92f6132ece99`.
- **named_procedure**: `tools/coverage/capture.ps1 -Purpose Comparison` failed during Gradle configuration because the policy's exact `FidoBleReconnectStressDeviceTest` exclusion was absent from target sources. The capture runner now supplies AndroidJUnitRunner `notClass` only when that test source exists, and the trusted init script reports only class exclusions present in the measured revision. Gradle configuration passed against target and candidate after the correction; exact-source checks confirmed absent-on-target/present-on-candidate; policy fixtures passed 520/520 and `git diff --check` passed.
- **environment**: Windows 11; ASUS AI2302 Android 15/API35 (`R7AIB7002901AZ3`); Gradle 9.6.0; 2026-11-08.
- **expected_observed_result**: Historical target revisions configure without requiring a test class that was added later; candidate revisions with the stress test continue to record and apply its exact exclusion.
- **limitations**: No coverage workloads, counters or immutable bundle were produced by this attempt. The harness correction must be committed before a trusted capture. The accepted index remains bound to `f1fd56baf8641cea99edc7fafa8725fdda21044c`, incompatible with target `e7918b6`; no baseline promotion or nonregression comparison is claimed.
- **status**: Source-compatibility fix configured and policy-validated; T038 remains Unverified pending capture and accepted-baseline review.
- **artifact_link**: Capture attempt `c273eb13-14a9-4909-a08e-b9593b582de1`; parent Record 242; Feature 058 §11.154; `tools/hdk-coverage.init.gradle`.

### Record 217: Exact-Target Capture Passed an Empty notClass Filter and Silently Ran Zero Tests (T038 — Unverified)
- **requirement**: BLE T038; complete the parent-coordinated immutable capture and compatible accepted-target raw-count comparison.
- **scope**: Follow-up to Record 216 using the committed, clean Feature 058 harness and serialized connected-device task graph.
- **revision**: Harness `9c0aa97f7d5d8b7f01760e411cb4e8cd6f7fb5b4`, clean; target `e7918b6e256319b1305536c7815a92f6132ece99`, tree `afe0683226166775f4fccdfc8accb06c46bd5697`.
- **named_procedure**: Exact-target comparison capture on ASUS API35, run `34b5850b-a782-4269-9361-24fb3e32869e`. Connected tests ran sequentially after the ordering fix, but PowerShell left an empty `notClass` variable, so AGP passed `-e notClass` with no value and JUnit reported zero tests. The `Invalid userId -2` message was nonfatal and not causal. Direct instrumentation with `--user 0` and the routine `notPackage` filter passed 41/41 FIDO2 tests.
- **environment**: Windows 11; ASUS AI2302 Android 15/API35 (`R7AIB7002901AZ3`); Gradle 9.6.0; 2026-11-08.
- **expected_observed_result**: The earlier capture was correctly marked Incomplete/Failed. The empty-filter cause is corrected in the follow-up parent Record 244; the focused Gradle task now produces 41/41 tests and nonempty execution data.
- **limitations**: Capture manifest is Incomplete/Failed, with no counters, production inventory, or comparable bundle. Trusted accepted index remains for `f1fd56baf8641cea99edc7fafa8725fdda21044c`, not the parent target `e7918b6`; no candidate nonregression result exists.
- **status**: T038 remains Unverified under parent T039 coordination.
- **artifact_link**: Parent Record 243; run manifest and logs at `D:\octav\source\repos\.worktrees\chimali\coverage-evidence\064-coverage-2026-11-08\34b5850b-a782-4269-9361-24fb3e32869e`.

### Record 218: Exact-Target FIDO2 Coverage Runs 41/41 After Empty Filter Removal (T038 — Partial)
- **requirement**: BLE T038; verify true FIDO2 device-test discovery before the parent-coordinated full capture.
- **scope**: Focused FIDO2 connected suite on exact target `e7918b6e256319b1305536c7815a92f6132ece99` after the capture script removes absent runner filters.
- **revision**: Target tree `afe0683226166775f4fccdfc8accb06c46bd5697`; pending Feature 058 harness fix in the feature branch.
- **named_procedure**: Ran `:feature:fido2:connectedAndroidDeviceTest` on ASUS/API35 with package exclusion `com.chimali.fido2.util.performance`, no class exclusion, fresh Gradle daemon and in-process Kotlin compiler. The instrumentation command omitted `notClass`; JUnit reported 41 tests, zero failures/errors/skips; nonempty JaCoCo device data was collected.
- **environment**: Windows 11; ASUS AI2302 Android 15/API35 (`R7AIB7002901AZ3`); Gradle 9.6.0; 2026-11-08.
- **expected_observed_result**: The exact-target FIDO2 connected suite is discovered and passes after removing the empty filter; this resolves the prior zero-test suite issue.
- **limitations**: No full immutable capture, compatible accepted baseline, or raw-count comparison is established. Keep T038 Unverified until parent T039 is completed.
- **status**: Focused device suite passed; parent-coordinated full coverage gate remains open.
- **artifact_link**: Target FIDO2 JUnit report and coverage.ec; parent Record 244; Feature 058 §11.155.
### Record 219: Immutable BLE Coverage Captures Pass Floors; Trusted Accepted-Target Gate Remains Open
- **requirement**: BLE T038; complete candidate coverage and verify affected Tier 1 raw-ratio nonregression against the trusted accepted target.
- **scope**: Exact target and candidate Feature 064 BLE transport bundles, ASUS/API35, with direct FIDO2 Tier 1 raw-count comparison.
- **revision**: Target `e7918b6e256319b1305536c7815a92f6132ece99`, tree `afe0683226166775f4fccdfc8accb06c46bd5697`; candidate `52dfb065b475df74821e4135e3f22da9ddd2f774`.
- **named_procedure**: Exact-target run `f05928c2-d767-4363-a572-b3f1e28351dd` and candidate run `2c62cd8b-abd2-4f4a-ba9b-2f25143e50c3` both have complete immutable manifests, tests Passed, configured floors Passed, and the same measurement fingerprint `b1be466f4ebf9003f5bd6330030e2888c12b9a94b5e6068f7b50ef48b14cae1c`. FIDO2 changed-module counts are target 56,458/87,578 instructions and 3,461/5,617 branches; candidate 68,342/100,196 instructions and 4,106/6,662 branches. Candidate repository aggregate is 90,437/149,044 instructions (60.678%).
- **environment**: Windows 11; ASUS AI2302 / Android 15 API35 (`R7AIB7002901AZ3`); Gradle 9.6.0; 2026-11-08 local time.
- **expected_observed_result**: Candidate FIDO2 instruction and branch ratios are nondecreasing relative to the exact target; configured floors pass.
- **limitations**: The independently trusted accepted index still identifies protected `main` at `f1fd56baf8641cea99edc7fafa8725fdda21044c`, not the measured target. `integration-ci.ps1` did not accept these runs as the formal current-target comparison; no index was created or promoted. Hosted enforcement remains waived.
- **status**: Captures and floors pass; BLE T038 remains Unverified pending trusted accepted-baseline resolution and formal integration comparison.
- **artifact_link**: Target `D:\octav\source\repos\.worktrees\chimali\coverage-evidence\recreated-baseline-e791\f05928c2-d767-4363-a572-b3f1e28351dd`; candidate `D:\octav\source\repos\.worktrees\chimali\coverage-evidence\064-coverage-2026-11-08\2c62cd8b-abd2-4f4a-ba9b-2f25143e50c3`; parent Record 245.
### Record 220: Parent Branch Synced with Protected Main; BLE Coverage Must Be Recaptured
- **requirement**: BLE T038; candidate coverage and nonregression must bind the exact current parent candidate and accepted target.
- **scope**: Parent Feature 064 merge of protected main and required follow-up to BLE coverage evidence.
- **revision**: Merge commit `f6e85e53d63eafdd551772d012dedc67ca8c7d9e`, containing accepted main `f1fd56baf8641cea99edc7fafa8725fdda21044c`; current tree `8d58832266f54094a85eb1206cb084ac8ff339a4` differs from the last measured candidate tree.
- **named_procedure**: Parent merge pre-commit Local CI passed. Connected GitHub reader confirmed live main still resolves to the accepted-index commit. The accepted archive run fingerprint is `6b2b9e491e02baf41f8084e3df9cfe1555b5005c525d34eaf89889d79590b3bd`; prior Feature 064 captures use `b1be466f4ebf9003f5bd6330030e2888c12b9a94b5e6068f7b50ef48b14cae1c`.
- **environment**: Windows 11; ASUS AI2302/API35 attached; 2026-11-08 local time.
- **expected_observed_result**: The current branch includes the accepted main target and local host/unit checks pass; the previous capture is explicitly treated as pre-merge evidence.
- **limitations**: The post-merge candidate has no coverage capture. Fingerprint compatibility with the current accepted bundle is unresolved. Local `git ls-remote` fails because GitHub CLI authentication is invalid; the connector confirms the target SHA but cannot satisfy the script's authenticated remote check. BLE T038 remains Unverified.
- **status**: Parent sync complete; fresh candidate capture and formal accepted-target comparison remain open.
- **artifact_link**: Parent Feature 064 Record 246; accepted bundle run `c1b392a9-f830-4283-83f2-44e36ac5d731`; Feature 058 §11.157.
### Record 221: Post-Merge BLE Candidate Floors Pass; Indexed Baseline Fails Compatibility
- **requirement**: BLE T038; verify exact candidate FIDO2 coverage and compare it to the trusted accepted target using the reviewed policy.
- **scope**: Candidate run after merging protected main; accepted bundle digest, candidate floors and comparison decision.
- **revision**: Candidate `08a7e318666d1d67ddbf25053d5615cc55f6c46f`, tree `3ee1abd006f2676f7152bce95a4483c6b1287b19`; accepted target `f1fd56baf8641cea99edc7fafa8725fdda21044c`.
- **named_procedure**: Capture `3026b76a-4a15-44fa-ac56-03b523cde305` on ASUS/API35: complete collection, passing tests and floors; FIDO2 68,342/100,196 instructions and 4,106/6,662 branches. The current accepted archive run digest matches its index. Trusted `verify.ps1` reports Incompatible because accepted/candidate measurement fingerprints differ (`6b2b9e49...` versus `b1be466f...`); harness-file hashes and FIDO2 exclusion evidence differ too. No baseline index was edited.
- **environment**: Windows 11; ASUS AI2302 / Android 15 API35 (`R7AIB7002901AZ3`); Gradle 9.6.0; 2026-11-08 local time.
- **expected_observed_result**: FIDO2 floors pass for the exact current candidate; the older accepted bundle is rejected as incompatible under current measured policy.
- **limitations**: No valid regression decision or end-to-end integration result. A current-harness exact-target capture and reviewed baseline update/comparison are required. The local integration wrapper also needs working GitHub CLI credentials for `git ls-remote`. BLE T038 remains Unverified.
- **status**: Candidate floors pass; accepted-target gate remains unverified due confirmed compatibility mismatch.
- **artifact_link**: Parent Feature 064 Record 247; candidate run `3026b76a-4a15-44fa-ac56-03b523cde305`; trusted accepted run `c1b392a9-f830-4283-83f2-44e36ac5d731`; Feature 058 §11.158.
### Record 223: Exact Parent Candidate Capture and FIDO2 Regression Decision Pass; Integration Wrapper Pending
- **requirement**: BLE T038; verify candidate FIDO2 coverage floors and current-harness raw-count nonregression under parent T039 coordination.
- **scope**: Exact parent HEAD candidate bundle, measured protected-main target bundle, and production verifier decision.
- **revision**: Candidate `c1411ed141d2c4064c0db22967d4c8ea04c70af1`, tree `bd1887a8b1220f88945a948eece9317b1cc2d1ea`; target `f1fd56baf8641cea99edc7fafa8725fdda21044c`.
- **named_procedure**: Candidate run `c6156938-bfd8-4084-b2c9-de30341d9055`, ASUS AI2302/API35, collection Complete and tests Passed. FIDO2 instructions 68,342/99,968 (68.364%), branches 4,106/6,578 (62.420%); configured floors pass. The external measured-target index identifies exact-main run `b24e1d3c-e0e9-4896-91f6-cb41ea60db23`, digest `b07f05ae590454c44f2eb8a4f4416fb5cf8478cc77334f1476e069e66cf3ee41`. Production verifier outcomes: comparison Compatible, regression Passed, overall Passed; candidate bundle digest `68bf8b297d0c9c9c52e48801ce765dd4e1c3ef4b991d4f07e3edae9f5197b39a`.
- **environment**: Windows 11; ASUS AI2302 / Android 15 API35; Gradle 9.6.0; 2026-11-09 UTC.
- **expected_observed_result**: FIDO2 floors pass and no raw-ratio regression is found against the measured target.
- **limitations**: `tools/integration-ci.ps1` live remote validation remains unrun because local GitHub CLI authentication is invalid. Decision retains `integration=Unverified`, and no canonical accepted archive index was modified. BLE T038 remains unchecked pending parent T039 wrapper completion.
- **status**: Exact capture and trusted measured-target comparison pass; BLE T038 remains Unverified solely at the shared live integration-wrapper gate.
- **artifact_link**: Candidate run and decision in `D:\octav\source\repos\.worktrees\chimali\coverage-evidence\064-current-main-comparison-2026-11-09`; measured-target run/index in `measured-target-index`.
### Record 224: Parent Integration Wrapper Passes BLE Coverage and Nonregression Gate
- **requirement**: BLE T038; complete local coverage floors and accepted-target raw-count nonregression under parent T039's integration orchestration.
- **scope**: Exact Feature 064 candidate and protected-main comparison using the reviewed trusted harness and external indexed target evidence.
- **revision**: Candidate `c1411ed141d2c4064c0db22967d4c8ea04c70af1`; protected `origin/main` `f1fd56baf8641cea99edc7fafa8725fdda21044c`.
- **named_procedure**: Parent `tools/integration-ci.ps1` passed end to end with live origin/main SHA validation before and after. Candidate ASUS/API35 run `a04ca570-043f-43e8-8de5-8bf705016327` has complete collection and passing connected tests. FIDO2 instruction coverage is 68,342/99,968 (68.364%) and branches 4,106/6,578 (62.420%); configured floors pass. Production verifier reports Compatible comparison, Passed regression and overall passed.
- **environment**: Windows 11; ASUS AI2302 / Android 15 API35 (`R7AIB7002901AZ3`); Gradle 9.6.0; 2026-11-09 local time.
- **expected_observed_result**: BLE/FIDO2 coverage meets configured floors and does not regress against the independently indexed target.
- **limitations**: Hosted integration remains waived and unverified; publication remains pending. The canonical accepted archive index was not promoted. Wrapper shutdown reported only a locked temporary build file during detached-worktree cleanup.
- **status**: BLE T038 local coverage and regression requirements pass; retain the explicit hosted waiver.
- **artifact_link**: Candidate bundle and decision under `D:\octav\source\repos\.worktrees\chimali\coverage-evidence\064-current-main-comparison-2026-11-09\measured-target-index` (`a04ca570-043f-43e8-8de5-8bf705016327`).

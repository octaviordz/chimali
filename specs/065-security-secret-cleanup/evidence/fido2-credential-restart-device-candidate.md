# FIDO credential process-restart — candidate-bound device execution

**Date:** 2026-11-20
**Candidate:** commit `49e37f606c08dd51a899d4728ce23052334c608a`, tree `46c871e56b4a99f075c1e6426fd320af2512e9d3`; compiled inputs identical (documentation-only commits since freeze).

## Execution

`tools/run-hdk-process-restart-test.ps1 -Serial <device>` rebuilt `:feature:fido2:assembleAndroidDeviceTest` from candidate content and ran the three-phase `com.chimali.fido2.data.crypto.CredentialProcessRestartTest` (prepare synthetic credential → operating-system process restart → recover and sign → fixture cleanup) on all three cohorts:

| Cohort | Prepare | Recover/sign | Result |
|---|---|---|---|
| `emulator-5554` (AOSP API 35) | OK (1 test, 5.208 s) | OK (1 test, 4.481 s) | "Process-restart recovery passed using a synthetic P-256 credential." |
| Xiaomi Mi A2 `2ac6876` (API 29/MIUI) | OK (1 test, 3.9 s) | OK (1 test, 2.966 s) | Same pass message |
| ASUS AI2302 `R7AIB7002901AZ3` (API 35) | OK (1 test, 1.62 s) | OK (1 test, 1.619 s) | Same pass message |

Instrumentation APK SHA-256: `feature/fido2/build/outputs/apk/androidTest/fido2-androidTest.apk` = `A16F72B8485445CC888B2498D5FE4A5BC88032FAB3C42C3DC20329ABAE9AB0A6` (candidate-content build; test package `com.chimali.fido2.test` uninstalled by the runner after each run).

## Scope and limits

This upgrades SEC065-FIDO-017 persistence/restart device evidence from exploratory-worktree to candidate-bound across emulator and both physical cohorts — a synthetic P-256 credential survives an operating-system process kill and signs after recovery on candidate content at API 29 and API 35. Heap-root inspection of the recovered credential, provider/JCA copies and OEM keystore behavior remain open under T015/T027. No persistent device state remains; the per-run fixture directory was cleaned by the test's `finally`.

## Candidate-bound fido2 persistence/lifecycle/user-verification device batch (2026-11-20)

`:feature:fido2:connectedAndroidDeviceTest` filtered to `CredentialPersistenceCeremonyTest`, `LocalProfileLifecycleTest` and `UserVerificationServiceDeviceTest` ran on all three cohorts against candidate content (`fido2-androidTest.apk` `A16F72B8485445CC888B2498D5FE4A5BC88032FAB3C42C3DC20329ABAE9AB0A6`):

| Cohort | Result |
|---|---|
| emulator-5554 (API 35) | 11/11 pass, 0 failures/errors/skips |
| Mi A2 `2ac6876` (API 29/MIUI) | 11/11 pass |
| ASUS `R7AIB7002901AZ3` (API 35) | 11/11 pass |

Per-class: `CredentialPersistenceCeremonyTest` 2, `LocalProfileLifecycleTest` 7, `UserVerificationServiceDeviceTest` 2. Development/test-instrumented evidence; heap-root/provider-copy coverage remains open under T015/T027.
## Candidate-bound fido2 BLE/UI-lifecycle device batch (2026-11-20)

`:feature:fido2:connectedAndroidDeviceTest` filtered to `FidoBleLifecycleDeviceTest`, `TransportSettingsDuringCeremonyTest`, `EditPairedDeviceScreenLifecycleTest`, `PairedDevicesSectionLifecycleTest`, `LifecycleClearedStateTest` and `Fido2SettingsRepositoryTest` against candidate content (`fido2-androidTest.apk` `A16F72B8485445CC888B2498D5FE4A5BC88032FAB3C42C3DC20329ABAE9AB0A6`):

| Cohort | Result |
|---|---|
| emulator-5554 (API 35) | 15/15 pass, 0 failures/errors/skips |
| ASUS `R7AIB7002901AZ3` (API 35) | 15/15 pass |
| Mi A2 `2ac6876` (API 29/MIUI) | 11/11 eligible pass; `FidoBleLifecycleDeviceTest` (4 tests) not discovered on API 29 (SDK-gated at test discovery, not a failure) |

Per-class on API 35 cohorts: FidoBleLifecycle 4, TransportSettingsDuringCeremony 2, EditPairedDeviceScreenLifecycle 1, PairedDevicesSectionLifecycle 1, LifecycleClearedState 1, Fido2SettingsRepository 6. Transport/UI lifecycle and settings-repository coverage now candidate-bound on all three cohorts; BLE lifecycle coverage is API 35-only pending an API 29-capable path or recorded platform limitation. Heap-root coverage remains open under T015/T027.
## Candidate-bound fido2 dashboard-components harness failure (2026-11-20)

`:feature:fido2:connectedAndroidDeviceTest` on `emulator-5554` (API 35) against candidate content (`fido2-androidTest.apk` `A16F72B8485445CC888B2498D5FE4A5BC88032FAB3C42C3DC20329ABAE9AB0A6`):

- `DevelopmentToolsScreenTest` — **6/6 pass**, zero failures.
- `AuthenticatorDashboardComponentsTest` — **1/9 pass, 8 failures**, all identical: `IllegalStateException: ComponentActivity has already set content` thrown by the `@After disposeSyntheticDeviceUi` cleanup, which calls `composeTestRule.setContent {}` after the test already populated content. With this activity/Compose version a second `setContent` on the same activity is illegal, so the cleanup itself fails every populated test. **Classified as a test-harness defect, not a product finding** — the rendered-component assertions are not reached past the cleanup throw in the reported result; a fix (dispose via a fresh rule or remove the redundant `setContent`) is recommended for a future revision and cannot be applied to the frozen candidate.

Physical-cohort runs were not attempted for the failing class (deterministic harness failure); `DevelopmentToolsScreenTest` physical coverage remains open alongside the dashboard fix.
## Candidate-bound fido2 BLE security/advertising device batch (2026-11-20)

`:feature:fido2:connectedAndroidDeviceTest` against candidate content (`fido2-androidTest.apk` `A16F72B8485445CC888B2498D5FE4A5BC88032FAB3C42C3DC20329ABAE9AB0A6`):

| Cohort | Classes | Result |
|---|---|---|
| emulator-5554 (API 35) | FidoBleSecurityDeviceTest, FidoBleAdvertiserDiscoveryDeviceTest, NordicBleLegacyAdvertisingDeviceTest | Security 4/4 pass; Nordic 1/1 pass; AdvertiserDiscovery **failed** — `Observed 0 nearby BLE results, but no device advertised 0000fffd-… within 12000ms` (requires a physical BLE peripheral advertising FFF0; environment/hardware limitation, not product) |
| Mi A2 `2ac6876` (API 29/MIUI) | FidoBleSecurityDeviceTest, NordicBleLegacyAdvertisingDeviceTest | 5/5 pass |
| ASUS `R7AIB7002901AZ3` (API 35) | FidoBleSecurityDeviceTest, NordicBleLegacyAdvertisingDeviceTest | 5/5 pass |

BLE security and legacy-advertising coverage is now candidate-bound on all three cohorts including real Bluetooth stacks on both physical devices; advertiser-discovery coverage requires dedicated BLE test hardware and remains an environment-gated open cell. Heap-root coverage remains open under T015/T027.
## Candidate-bound fido2 BLE stress/corpus gating check (2026-11-20)

`:feature:fido2:connectedAndroidDeviceTest` on `emulator-5554` (API 35) against candidate content (`fido2-androidTest.apk` `A16F72B8485445CC888B2498D5FE4A5BC88032FAB3C42C3DC20329ABAE9AB0A6`) for `FidoBleReconnectStressDeviceTest` (2 tests) and `FidoBlePhysicalCorpusClientDeviceTest` (1 test): all three **skipped** via `AssumptionViolatedException` — they require explicit opt-in instrumentation arguments (`runFidoBleReconnectStress`, `runFidoBlePhysicalCorpusServer`, `runFidoBlePhysicalCorpusClient`) and dedicated BLE hardware endpoints (a separate FIDO BLE advertiser and a separate Android GATT central). This is correct harness gating, not a product result; physical-hardware BLE stress/corpus coverage remains an open T027 cell requiring the dedicated test endpoints.
## Candidate-bound fido2 performance report/timing device batch (2026-11-20)

`:feature:fido2:connectedAndroidDeviceTest` filtered to `AndroidMeasurementReportTest` (5 tests) and `AndroidTimingAssessmentTest` (4 tests) against candidate content (`fido2-androidTest.apk` `A16F72B8485445CC888B2498D5FE4A5BC88032FAB3C42C3DC20329ABAE9AB0A6`): **9/9 pass, zero failures/errors/skips** on each of `emulator-5554` (API 35), Mi A2 `2ac6876` (API 29/MIUI) and ASUS `R7AIB7002901AZ3` (API 35). These are measurement/report correctness tests (synthetic workloads), not long benchmark runs. The gated exploration classes (`EmpiricalCalibrationDeviceTest`, `FilteredMeasurementPilotDeviceTest`, `LowerOverheadControlExplorationDeviceTest`, `TraceOverheadExplorationDeviceTest`, `AndroidSimulatedLatencyAssessmentTest`) require opt-in instrumentation arguments and remain unexercised opt-in cells.
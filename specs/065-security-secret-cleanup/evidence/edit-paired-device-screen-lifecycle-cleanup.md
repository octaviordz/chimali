# Edit paired-device screen lifecycle cleanup

- **Finding:** SEC065-FIDO-024
- **Owners:** `EditPairedDeviceScreen` paired-device collection and unsaved alias draft
- **Classification:** CLS-FIDO-004 (paired Bluetooth host identity and association metadata)
- **Requirements:** FR-002/003/004/010; NFR-SEC-041/050; T005/T006/T015/T027/T039
- **Fixtures:** Synthetic paired host, MAC-shaped marker and alias only.

## Change

`EditPairedDeviceScreen` now collects `PairedDevicesViewModel.pairedDevices` through `collectAsStateWithLifecycleAndReset(emptyList())`. When the lifecycle leaves `STARTED`, collection stops and Compose state resets to an empty list. The selected device becomes absent and `remember(device)` recreates the alias draft from the persisted alias when collection resumes. Save clears the local draft before passing the alias to `updateAlias`; cancel/back and delete clear it before navigation.

The screen still renders the paired device's name, address and alias while active. This change bounds app-owned UI state; it does not erase immutable Strings or establish release of Compose, accessibility, IME, renderer, Android framework or heap copies.

## Regression coverage

`EditPairedDeviceScreenLifecycleTest#clearsUnsubmittedAliasDraftWhenStoppedAndRestoresPersistedAliasOnResume` types a synthetic unsaved alias, moves its lifecycle owner below `STARTED`, verifies the field is removed, resumes, and verifies only the persisted alias returns.

## Verification

- `:feature:fido2:compileAndroidMain :feature:fido2:compileAndroidDeviceTest :feature:fido2:ktlintAndroidMainSourceSetCheck :feature:fido2:ktlintAndroidDeviceTestSourceSetCheck`: passed.
- `:feature:fido2:assembleAndroidTest`: passed.
- Direct `adb shell am instrument` invocation on the authorized Mi A2 (Android API 29), selecting only this test: `OK (1 test)`.
- The test-only package `com.chimali.fido2.test` was removed from both the Mi A2 and emulator after device testing; the production Chimali package remained installed on the Mi A2.

An earlier Gradle connected-test invocation selected both connected devices despite an attempted serial selector; it was interrupted after the test had started. The successful result above comes from a subsequent direct launch addressed only to the Mi A2. The test uses a mocked ViewModel and synthetic Compose data; it does not access clipboard contents, Bluetooth, production credentials or persisted app data.

## Limits

This is focused UI lifecycle evidence on one API 29 device. It does not prove OS background-process behavior, immutable String erasure, renderer/accessibility/IME release, heap-root absence, or device-cohort coverage. Those remain open.

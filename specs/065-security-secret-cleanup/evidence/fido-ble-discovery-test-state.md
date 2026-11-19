# FIDO BLE discovery test state and permission scope

**Finding:** SEC065-FIDO-020  
**Scope:** `feature/fido2/src/androidDeviceTest/AndroidManifest.xml` and `FidoBleAdvertiserDiscoveryDeviceTest.kt`  
**Revision:** Dirty exploratory worktree; source review 2026-11-16

The device-test manifest grants BLE scan/connect permissions on Android 12+ and location permissions on older supported releases. These declarations apply to the FIDO `androidDeviceTest` source set; they are not added to the production app manifest. The exact merged instrumentation manifest and permission restoration behavior are not verified here.

The test scans nearby BLE advertisements to find a distinct device advertising the FIDO service UUID. Its callback reads each `ScanResult`, service UUID list and advertised name during callback execution, then retains only an integer match count and a latch signal. It no longer stores a `ScanResult` or remote device/name reference. The scanner is stopped in `finally`, including timeout and assertion-failure paths. Assertion output contains aggregate counts and the fixed service UUID only.

Source compilation and Android device-test KtLint passed:

```text
:feature:fido2:compileAndroidDeviceTest
:feature:fido2:ktlintAndroidDeviceTestSourceSetCheck
BUILD SUCCESSFUL
```

No BLE scan was run on a physical device in this review. Nearby-device observation, Android framework/native copies, merged test-manifest behavior and runtime permission lifecycle remain unverified. The Mi A2 clipboard observation was a separate approved synthetic test and does not verify BLE behavior.

# FIDO authentication notification privacy — candidate-bound device execution

**Date:** 2026-11-20
**Candidate:** commit `49e37f606c08dd51a899d4728ce23052334c608a`, tree `46c871e56b4a99f075c1e6426fd320af2512e9d3`; compiled inputs identical (documentation-only commits since freeze).

## Execution

`:feature:fido2:connectedAndroidDeviceTest` with `-Pandroid.testInstrumentationRunnerArguments.class=com.chimali.fido2.service.Fido2AuthNotificationPrivacyDeviceTest` on `emulator-5554` (AOSP Android 15/API 35, unlocked), Gradle 9.6.0, JDK 25.

Result XML (`feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15.xml`): `tests="2" failures="0" errors="0" skipped="0"`.

- `cleanupRemovesSyntheticAuthenticationNotificationFromAndroidManager` — pass (0.047 s)
- `authenticationNotificationUsesRedactedPublicVersion` — pass (0.008 s)

Instrumentation APK SHA-256: `feature/fido2/build/outputs/apk/androidTest/fido2-androidTest.apk` = `A16F72B8485445CC888B2498D5FE4A5BC88032FAB3C42C3DC20329ABAE9AB0A6` (built from candidate content; uninstalled by the connected-test task after the run).

## Scope and limits

This upgrades the SEC065-FIDO-NOTIF-001 device evidence from exploratory-worktree to candidate-bound on one cohort (API 35 emulator). It verifies the notification's private/public copy configuration and request-bound cleanup on candidate content. Physical-device cohorts, OEM notification-manager behavior, TalkBack/preview surfaces and heap-root coverage remain open under T006/T015/T027. Connected-test packages were removed after execution; no persistent device state remains.

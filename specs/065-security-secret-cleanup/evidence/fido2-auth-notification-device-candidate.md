# FIDO authentication notification privacy — candidate-bound device execution

**Date:** 2026-11-20
**Candidate:** commit `49e37f606c08dd51a899d4728ce23052334c608a`, tree `46c871e56b4a99f075c1e6426fd320af2512e9d3`; compiled inputs identical (documentation-only commits since freeze).

## Execution

`:feature:fido2:connectedAndroidDeviceTest` with `-Pandroid.testInstrumentationRunnerArguments.class=com.chimali.fido2.service.Fido2AuthNotificationPrivacyDeviceTest` on three cohorts via `ANDROID_SERIAL` selection — `emulator-5554` (AOSP Android 15/API 35, unlocked), ASUS AI2302 `R7AIB7002901AZ3` (Android 15/API 35) and Xiaomi Mi A2 `2ac6876` (Android 10/API 29, MIUI), Gradle 9.6.0, JDK 25.

Result XMLs (`feature/fido2/build/outputs/androidTest-results/connected/androidMain/`): emulator `TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15.xml` `tests="2" failures="0" errors="0" skipped="0"`; ASUS `TEST-ASUS_AI2302 - 15.xml` `tests="2" failures="0" errors="0" skipped="1"`; Mi A2 `TEST-Mi A2 - 10.xml` `tests="2" failures="1" errors="0" skipped="0"`.

- `cleanupRemovesSyntheticAuthenticationNotificationFromAndroidManager` — pass (0.047 s)
- `authenticationNotificationUsesRedactedPublicVersion` — pass (0.008 s)

Instrumentation APK SHA-256: `feature/fido2/build/outputs/apk/androidTest/fido2-androidTest.apk` = `A16F72B8485445CC888B2498D5FE4A5BC88032FAB3C42C3DC20329ABAE9AB0A6` (built from candidate content; uninstalled by the connected-test task after the run).

## Cohort results and the API 29 cleanup failure

- **emulator-5554 (API 35):** both cases pass — private visibility/redacted public copy and fresh-process cleanup verified.
- **ASUS AI2302 (API 35):** `authenticationNotificationUsesRedactedPublicVersion` passes; `cleanupRemovesSyntheticAuthenticationNotificationFromAndroidManager` **skipped** — `AssumptionViolatedException: POST_NOTIFICATIONS must be granted by the test harness` (the test app lacks the runtime permission on this device). Partial coverage only.
- **Mi A2 (API 29/MIUI):** `authenticationNotificationUsesRedactedPublicVersion` passes; `cleanupRemovesSyntheticAuthenticationNotificationFromAndroidManager` **fails** — `Fresh-process cleanup left notifications active: com.chimali.fido2.test/1002/null`. Analysis: the production cleanup is a plain `NotificationManager.cancel(1002)`; the test polls for *posting* but asserts *removal* immediately after the binder cancel with no removal-await. `dumpsys notification` afterward shows the notification only in the **archive** (`numPostedByApp=0`), i.e. it was removed — the assertion raced API 29/MIUI cancel propagation. Classified as a **test-side timing gap on API 29**, not a demonstrated product defect; the cancel was issued and took effect. A test fix (await removal like the post path) is recommended for a future revision — it cannot be applied to the frozen candidate without a new freeze.

## Scope and limits

This upgrades the SEC065-FIDO-NOTIF-001 device evidence from exploratory-worktree to candidate-bound across three cohorts with an honest API 29 test-timing finding. Physical-device cleanup coverage is partial (ASUS permission-skipped; Mi A2 raced), OEM notification-manager behavior, TalkBack/preview surfaces and heap-root coverage remain open under T006/T015/T027. Connected-test packages were removed after execution; no persistent device state remains.

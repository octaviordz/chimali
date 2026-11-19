# FIDO authentication notification lifetime

**Finding:** SEC065-FIDO-NOTIF-001  
**Scope:** App-owned lifetime of the background authentication notification in `Fido2TransportService`.

## Change

The service now tracks which deferred request owns the replaceable notification. Posting, completion removal and shutdown cleanup are serialized through the tracker. When that request completes or is cancelled, the completion callback asks `NotificationManager` to remove the notification only if the same request still owns it. A superseded request cannot race with and cancel a newer request's notification. `onDestroy` closes the tracker, drops the tracked request and requests removal of any remaining authentication notification; later posts from a racing collector are rejected. Because process death can bypass `onDestroy` and the request owner is process-local, `Fido2Initializer.init` also cancels the fixed authentication notification ID on the next fresh app-process startup, before FIDO work begins.

The private notification still contains the RP for unlocked verification. `VISIBILITY_PRIVATE` and a fixed generic public version remain configured for insecure contexts.

## Verification

`Fido2TransportServiceNotificationPrivacyTest` passes four host cases: private/public copy separation, stale completion protection, shutdown owner clearing, and an exact-ID verification that fresh-start cleanup calls `NotificationManager.cancel(1002)`. The full `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck` run passed 1,332 tests across 172 reports with zero failures, errors, or skips; Android main compilation and FIDO KtLint passed. The API 35 emulator's `Fido2AuthNotificationPrivacyDeviceTest` also passes 2/2. It posted a synthetic notification at ID 1002, observed it in Android's active notification list, called the cleanup helper, and observed that it had been removed. See `fido-auth-notification-manager-api35.md` for the direct instrumentation procedure and environment.

The API 35 device case exercises the real `NotificationManager` from the isolated FIDO test package, but does not instantiate `ChimaliApplication` or simulate a process restart. It establishes API-level cancellation of a synthetic notification, not SystemUI/lock-screen/screen-sharing/OEM rendering, accessibility, or real process-death recovery; those remain unverified. The temporary package was uninstalled after the run and the synthetic notification was absent in a follow-up check.

## Source and result hashes

- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/Fido2Initializer.kt` — SHA-256 `8b352b6aaff0f3682e53336e8513b1b45de9e2810b6771a05335619c94e8c5db`
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/service/Fido2TransportService.kt` — SHA-256 `49b53662bd1eb2af38236e1c6ce3ea68970e93172819bf4ebe4c6009a4481006`
- `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/service/Fido2TransportServiceNotificationPrivacyTest.kt` — SHA-256 `4785fea0059953a0adffda39ad2483845d19f2698e83886e4fe81c16f5ad68d3`
- `feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/service/Fido2AuthNotificationPrivacyDeviceTest.kt` — SHA-256 `5bf7f106257f9d2f34bb6cff749daf448c65b56a1cdeb96440406ca3953f9914`
- `feature/fido2/build/outputs/apk/androidTest/fido2-androidTest.apk` — SHA-256 `187e5d8da991103ffb3116297d6977ad021b162ecd396d00fa1b753e27e2e11b`
- `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.service.Fido2TransportServiceNotificationPrivacyTest.xml` — SHA-256 `b233dc2eafbd302400bf48dd58af90e93ef47904847e75cad0a9cf88f13f0944`
- `feature/fido2/build/reports/tests/testAndroidHostTest/index.html` — SHA-256 `3f7ca7095a80a47a213a92a0487ba8113f8fe71fcc4caf624287faeb2506eeaa`

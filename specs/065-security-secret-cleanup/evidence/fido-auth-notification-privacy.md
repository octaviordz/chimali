# FIDO authentication notification privacy

**Date:** 2026-11-17  
**Requirements:** FR-003/004/005; NFR-SEC-041; TR-AUTH-031; T005/T006/T027/T029/T034/T035.  
**Finding:** SEC065-FIDO-NOTIF-001.

## Source correction

When the app is backgrounded, `Fido2TransportService` can post a sign-in notification for an active authentication request. Its private notification body includes the RP ID so the user can verify the relying party when the device is unlocked. The notification is now explicitly `VISIBILITY_PRIVATE` and supplies a generic public version (`Chimali Authenticator` / `A passkey request is waiting. Unlock Chimali to review it.`) for insecure contexts such as the secure lock screen and screen sharing. The public version contains no RP or account data. Android documents `setPublicVersion()` as the notification replacement shown in insecure contexts; `VISIBILITY_PRIVATE` restricts the private content to trusted/unlocked contexts ([NotificationCompat.Builder](https://developer.android.com/reference/androidx/core/app/NotificationCompat.Builder#setPublicVersion(android.app.Notification)), [Notification visibility](https://developer.android.com/reference/android/app/Notification#visibility)).

The existing transport lifecycle errors continue to use fixed Kermit labels under SEC065-LOG-030.

## Verification

`Fido2TransportServiceNotificationPrivacyTest` passed 1 test with zero failures/errors/skips. A synthetic RP marker remains in the private copy for verification and is absent from the public title/text constants. The subsequent full `:feature:fido2:testAndroidHostTest` suite passed **1,290 tests across 171 reports**, with zero failures/errors/skips; `:feature:fido2:ktlintCheck` passed and Android main compilation passed.

The test validates content construction, not Android SystemUI display. Lock-screen, screen-sharing and OEM rendering, NotificationManager retention/dismissal, full-screen delivery and configured diagnostic destinations remain unverified under T027/T029/T034/T035. Private RP text remains system-owned until notification replacement or dismissal; no zeroization claim is made for framework copies.

XML SHA-256: `1487cd639730c187b03f5e90369d2e9365b70a0ffcdea0d8991c2d821dbdf600`.

An Android device test now constructs the production `Notification` helper using a synthetic RP marker without posting a notification. On the API 35 AOSP emulator (`emulator-5554`), `Fido2AuthNotificationPrivacyDeviceTest` passed 1 test with zero failures/errors/skips. It verified `VISIBILITY_PRIVATE`, the marker in the private text and the fixed marker-free public title/text. The test APK was run only on the emulator. Device-test XML SHA-256: `79d02b7b6490b1ce80ce4ae5174656286d338645c074c493907e48006a1bb8c0`.

This strengthens object-configuration evidence; it still does not post through NotificationManager or inspect lock-screen, screen-sharing, OEM, accessibility or lifecycle behavior. The device-test source compiles and FIDO KtLint passes.

The adjacent `Fido2TransportServiceForegroundPermissionTest` was source-inspected and dispositioned as no relevant usage: its cases pass only SDK constants and synthetic permission booleans to the prerequisite predicate. Its file-specific rationale is recorded in `source-inventory.json`.

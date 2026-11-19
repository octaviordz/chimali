# FIDO notification cleanup through Android NotificationManager

- **Finding:** SEC065-FIDO-NOTIF-001.
- **Requirements:** FR-003/004/005; NFR-SEC-041; TR-AUTH-031; T006/T029.
- **Environment:** Android API 35 emulator, product `sdk_slim_x86_64`, model `Android ATD built for x86_64`.
- **Procedure:** Installed `feature/fido2/build/outputs/apk/androidTest/fido2-androidTest.apk` only on `emulator-5554`, temporarily granted `POST_NOTIFICATIONS` to its isolated test package, then ran `adb -s emulator-5554 shell am instrument -w -e class com.chimali.fido2.service.Fido2AuthNotificationPrivacyDeviceTest com.chimali.fido2.test/androidx.test.runner.AndroidJUnitRunner`. The test posted a synthetic notification at ID 1002, confirmed it appeared in `NotificationManager.activeNotifications`, called `clearStaleAuthenticationRequestNotification`, and confirmed ID 1002 disappeared. The test's `finally` also cancels the synthetic notification and deletes its test channel.
- **Result:** Both `Fido2AuthNotificationPrivacyDeviceTest` cases passed (`OK (2 tests)`). The temporary test package was uninstalled after the run, restoring its permission/package state; a follow-up check found no synthetic notification marker. The Mi A2 was not selected or modified by this run.
- **Source:** `feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/service/Fido2AuthNotificationPrivacyDeviceTest.kt`, SHA-256 `5bf7f106257f9d2f34bb6cff749daf448c65b56a1cdeb96440406ca3953f9914`.
- **Test APK:** `feature/fido2/build/outputs/apk/androidTest/fido2-androidTest.apk`, SHA-256 `187e5d8da991103ffb3116297d6977ad021b162ecd396d00fa1b753e27e2e11b`.
- **Limitations:** The test exercises the real Android `NotificationManager` API from the isolated FIDO test package, not `ChimaliApplication.onCreate` or a real service process restart. It does not observe SystemUI, lock-screen or OEM rendering, accessibility, or real process-death recovery. Those remain open under T006/T027/T029/T034/T035.

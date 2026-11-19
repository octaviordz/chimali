# Label manager draft cleanup at lifecycle boundaries

**Finding:** SEC065-VAULT-010  
**Date:** 2026-11-16  
**Scope:** Unsubmitted label-name Compose state in `LabelManagerScreen`

The screen now clears its remembered label-name state on `ON_STOP`, composition disposal and Back navigation. It clears the state before passing a submitted name to the create callback. The cleanup releases the screen-owned String reference; it cannot erase immutable snapshots held by Compose, the input method or callback copies.

A red-before test APK initially failed because the label remained visible after a synthetic `ON_STOP`. The corrected instrumentation test passes on Android API 35 AOSP ATD x86_64 (`emulator-5554`): it entered a synthetic marker, dispatched `ON_STOP` through a test lifecycle owner, and verified the marker was absent from the text field. The test APK was uninstalled and verified absent afterward. The attached physical Mi A2 was not used.

- Procedure: `adb -s emulator-5554 shell am instrument -w -e class com.chimali.feature.vault.ui.LabelManagerLifecycleCleanupTest com.chimali.feature.vault.test/androidx.test.runner.AndroidJUnitRunner`
- Test APK SHA-256: `2091956ED24BBA0DF1EDBE3F8170FB7967889776E193AFBC6C96ED962C58204F`
- `:feature:vault:ktlintCheck :feature:vault:compileDebugAndroidTestKotlin` passed.

This is one emulator lifecycle case. It does not establish OEM behavior, input-method/rendering snapshots or true erasure of prior immutable String copies.

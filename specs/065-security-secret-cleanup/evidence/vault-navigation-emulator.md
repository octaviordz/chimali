# Vault navigation ownership: API 35 emulator

- Date: 2026-11-14
- Device: `emulator-5554`, Android ATD built for x86_64, API 35
- Build fingerprint: `Android/sdk_slim_x86_64/emu64x:15/AE3A.240806.019/12368160:userdebug/test-keys`
- Data: synthetic test-only vault entries; isolated instrumentation package
- Test source: `feature/vault/src/androidTest/java/com/chimali/feature/vault/ui/navigation/VaultOwnershipNavigationTest.kt`

## Command

```powershell
adb -s emulator-5554 install -r feature/vault/build/outputs/apk/androidTest/debug/vault-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e class com.chimali.feature.vault.ui.navigation.VaultOwnershipNavigationTest com.chimali.feature.vault.test/androidx.test.runner.AndroidJUnitRunner
```

## Result

The parameterized Android navigation ownership suite passed all 15 cases in 182.978 seconds. Coverage includes password, credit-card and secure-note edit/discard, failed-save retry, system back, graph disposal during save, and saved-state restoration. Each case's cleanup confirmed the codec/key-provider buffers collected by the instrumentation were erased.

This is emulator evidence for the exercised navigation scenarios. It does not establish physical-device behavior, full-app process-death behavior, heap-root absence, or platform/provider erasure.
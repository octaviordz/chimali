# Dashboard Compose teardown regression

- Date: 2026-11-22
- Worktree identity: dirty exploratory worktree; no frozen candidate declared.
- Change: removed the `@After` teardown that called `setContent {}` a second time. The Compose test rule owns Activity/composition disposal. Fixture review found only synthetic Bluetooth display identifiers and state values; no secret `ByteArray`/`CharArray` fixture owners require erasure.
- Procedure: `:feature:fido2:connectedAndroidDeviceTest` filtered to `AuthenticatorDashboardComponentsTest` on `dev35_aosp_atd_x86_64_Pixel_2` (API 35 AOSP emulator), serialized to one device with `ANDROID_SERIAL=emulator-5554`.
- Case results: `statusIndicatorRendersEveryConnectionStateAndAdvertisingMode` Passed; `pairedDeviceRowsRenderNamesAndInvokeEditAndConnectActions` Passed; `pairedDeviceWithoutAliasUsesFallbackNameAndIconClass` Passed; `pairedDeviceIconFallbackAndWearableClassesAreRendered` Passed; `transportButtonUsesStartAndStopLabelsAndInvokesToggle` Passed; `pairingRecoveryDialogExposesBluetoothSettingsAndRetryActions` Passed; `pairingHelpButtonInvokesItsAccessibleAction` Passed; `pairingHelpIsAvailableWhenBleIsIdleAdvertisingOrErrored` Passed; `emptyPairedDeviceListHidesTheDevicesHeading` Passed.
- Result: 9 passed, 0 failed, 0 errors, 0 skipped.
- Limitation: emulator evidence only; it is not physical cohort or frozen-candidate evidence.

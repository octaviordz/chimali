# BLE notification send-buffer erasure (2026-11-15)

## Requirement and scope

FR-003/004/010 and `FR-BLE-050` / `TR-BLE-020`; `BleNotificationWriter`'s independently owned packet copy passed to the GATT initiation callback, plus its focused host test.

## Revision and procedure

Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`. Added `BleNotificationWriterTest.send buffer is erased after platform initiation returns`, which captures the callback buffer reference and a synthetic pre-return snapshot. Ran the focused test, then `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck`.

## Environment and observed result

Windows JVM host, Gradle 9.6.0, JDK 25; synthetic packet bytes only. The focused test passes. The complete FIDO host suite passes 1,252 tests across 167 XML reports with zero failures, errors or skips; FIDO KtLint passes. The callback observed the expected bytes before returning; the retained reference to the writer-owned array contained only zeros afterward; the caller's input remained unchanged.

## Limitations

This demonstrates the application-owned buffer's erasure after the send callback returns. It does not establish Android Bluetooth stack/controller copies, device heap roots, or physical transport behavior. T005/T006/T012/T015/T027/T029/T034/T035 remain open.

## Artifact links

- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/BleNotificationWriter.kt`
- `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/BleNotificationWriterTest.kt`
- `feature/fido2/build/reports/tests/testAndroidHostTest/index.html`
- `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.transport.BleNotificationWriterTest.xml`
- `feature/fido2/build/reports/ktlint/`

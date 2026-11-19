# CTAPHID channel key and parsed-message owner cleanup

- **Finding:** SEC065-CTAP-015
- **Classification:** CLS-FIDO-005 (CTAPHID channel identifier)
- **Requirements:** NFR-SEC-041; TR-AUTH-031; FR-HID-010; FR-VAL-020; T005/T006/T012/T015/T027/T029/T041
- **Environment:** Windows JVM host; Gradle 9.6.0/JDK 25; synthetic channel IDs and payloads.

## Change

The parser pending-message map, active request table and registered-channel registry now use the 32-bit value packed from the four CID bytes instead of retaining lowercase hex `String` keys. Partial parser state no longer keeps a separate channel byte-array copy; the parser erases its temporary CID unless a completed `CtapHidMessage` takes ownership. `CtapHidMessage.clearMemory()` clears both its channel and payload arrays. Dispatchers clear rejected and synchronous messages, request owners clear rejected messages after response handling and admitted messages after the handler completes, and transport fallback clears messages when dispatch does not take ownership. U2F-to-CTAP2 asynchronous routing now copies the CID for the nested owner before the source message is erased.

## Verification

- `HidReportParserTest`: 19 tests passed, including staging replacement/sequence failure/reset/expiry cleanup.
- `CtapHidRequestOwnerTest`: 4 tests passed; stale/rejected/cancelled/failed request channel and payload arrays are erased.
- `CtapHidCommandDispatcherPrivacyTest`: 4 tests passed.
- `BluetoothHidTransportSessionGenerationTest`: 1 test passed after changing its MockK assertion to snapshot the CID during synchronous response construction, before production terminal erasure.
- Full `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck`: 1,250 tests across 167 XML reports; zero failures/errors/skips; KtLint passed.

## Limits

No Android device or physical Bluetooth run. Already-framed HID packet arrays and platform/driver copies are owned by later transport stages; this finding does not claim their erasure. Runtime session behavior, OS copies and heap roots remain open under T005/T006/T015/T027/T029/T034/T035.

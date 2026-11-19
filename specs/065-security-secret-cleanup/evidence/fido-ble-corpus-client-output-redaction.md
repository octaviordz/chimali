# Physical FIDO BLE corpus client output and buffer cleanup

- **Finding:** SEC065-LOG-056
- **Source:** `feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/data/transport/FidoBlePhysicalCorpusClientDeviceTest.kt`
- **Classifications:** CLS-FIDO-001/002/004
- **Requirements:** FR-003/004/008/009/010; NFR-SEC-041; TR-AUTH-031; T005/T006/T029/T033
- **Date:** 2026-11-17

## Source correction

The physical corpus client previously placed the actual FIDO response fragment as hexadecimal text in a JUnit assertion message, and address comparison used `assertEquals`, which can expose expected and observed paired-device addresses in failure output. Both now report only fixed scenario labels, fragment indices and response lengths. Exhausted connection retries no longer attach the platform exception as an assertion cause.

The client now erases each received notification fragment after comparison (including discarded keepalives), erases the generated expected-fragment arrays and each outgoing fragment after the GATT write callback, clears the characteristic reference, and drains remaining response/write/read state during disconnect or failed setup. The first GATT client now disconnects in a `finally` path if a corpus assertion fails.

## Verification and limits

`:feature:fido2:compileAndroidDeviceTest :feature:fido2:ktlintCheck` passes. No physical FIDO BLE corpus procedure was run; the source contains an ASUS-to-Mi authenticated peer workflow with 250 successor cycles and is not clipboard evidence. Runtime GATT behavior, Android framework/native copies, peer object/string lifetime and heap roots remain unverified.

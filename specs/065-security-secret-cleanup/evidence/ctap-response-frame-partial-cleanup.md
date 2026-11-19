# CTAP response frame partial-construction cleanup

Date: 2026-11-18  
Finding: SEC065-CORE-050  
Sources:

- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/bluetooth/HidReportParser.kt`
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/FidoBleFraming.kt`

## Source review and correction

Both response framers copy CTAP response payload bytes into mutable HID/BLE packet arrays and accumulate multiple packets before returning. If a later packet allocation, copy, or list admission failed, earlier packets could become unreachable while still containing response data. An admission failure could also strand the current packet. The BLE single-frame encoders did not clear their partial frame if the input range copy failed.

Both packet-list builders now retain admitted packets under local ownership until the complete list is ready to transfer. Failed construction clears every staged packet; failed list admission clears the current packet immediately. BLE initialization and continuation frame encoders also erase a partially written frame on failed copy.

## Verification and limitations

Failure injection now covers HID packet admission, BLE partial initialization-frame copying, and BLE response-frame admission. The focused `HidReportParserTest` passes 20 cases and `FidoBleFramingTest` passes 17; both verify current and previously staged arrays are cleared while borrowed payload inputs remain intact. The full FIDO host suite passes 1,363 tests across 175 reports with zero failures, errors or skips; KtLint passes. Successful packet lists still transfer to their callers; transport queue completion, Bluetooth framework copies and heap-root absence remain open.

# CTAP Windows compatibility test fixture cleanup

`Ctap2WindowsCompatibilityTest` uses synthetic authenticator data, signatures, credential IDs, CTAP HID requests and encoded response packets to verify raw CBOR byte-string compatibility. Teardown zeros every tracked input, expected, encoded, decoded and packet byte array; clears the reassembled HID message and parser assembly; and clears MockK answers and call history that retained the synthetic assertion and candidate fixtures. A second test verifies the teardown erases retained buffers and message state.

Validation: both focused tests pass; the full FIDO host suite passes 1,307 tests across 172 XML reports with zero failures/errors/skips; and `:feature:fido2:ktlintCheck` passes. This is synthetic host evidence only; it does not establish production authenticator/provider behavior, platform copies, heap reachability or device behavior.

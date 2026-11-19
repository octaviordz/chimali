# CTAP response-builder intermediate buffer cleanup

Date: 2026-11-15
Finding: SEC065-CTAP-016

## Change

`Ctap2ResponseBuilder.serializeAuthData` previously copied each input array into `List<Byte>` values via `toList()`, then allocated a final array, leaving boxed/intermediate copies with no erasure owner. It now computes the checked exact output size and writes directly into one `ByteArray`. `makeCredentialResponse` owns that serialized array only through CBOR/HID framing and erases it in a `finally` helper.

The response path now also clears the CBOR body produced by `CborCodec` and the one-byte-status-prefixed payload after `HidReportParser.encodeResponse` returns or throws. HID report arrays are independent output that transfer to the transport caller; this finding does not claim cleanup of those returned reports or Bluetooth/controller copies.

## Tests and verification

`Ctap2ProtocolTest` now observes the serialized authenticator data and status-prefixed payload while the encoding callbacks use them, verifies exact wire bytes and input preservation, then confirms the retained operation arrays are zeroed after the callbacks return. It separately confirms the returned payload copy remains valid. Focused protocol class passes. The complete FIDO Android host suite passes **1,257 tests across 167 XML reports**, zero failures/errors/skips, and `:feature:fido2:ktlintCheck` passes.

No pre-change test run was made for this finding; the source inspection identified the boxed conversion and missing terminal cleanup. Provider/CBOR internals, returned HID report ownership, transport/controller copies, and heap behavior remain outside this host assertion.

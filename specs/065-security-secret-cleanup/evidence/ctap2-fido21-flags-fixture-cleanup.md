# CTAP2 FIDO 2.1 flags test fixture cleanup

**Date:** 2026-11-17  
**Finding:** SEC065-FIDO-031  
**Requirements:** FR-003/004/010; NFR-SEC-041; TR-AUTH-031; T005/T006/T012.

`Ctap2Fido21FlagsTest` constructs a synthetic AAGUID, client-data hash, user ID, encoded CTAP2 request and decoded nested values. The test now tracks those arrays and decoded roots. Its `@AfterTest` recursively zeros decoded byte arrays, clears mutable maps/collections, zeros all explicit arrays and releases its tracking lists after every test. The remaining request labels are fixed synthetic immutable strings.

## Verification

The focused class passes 6 tests; `:feature:fido2:ktlintCheck` passes. Full FIDO host suite passes 1,290 tests across 171 reports with zero failures/errors/skips. Current XML SHA-256: `2f140a69bbab186840d65ebbb2307c58d1dac5e128c4e209d1cf2e8e9e748de2`; report index SHA-256: `f70e1e1becb000b7cd1c09a55d53957b8293dc3c602c7849e43b1573bf6a3beb`.

This is test fixture cleanup only. CBOR library internals, production request lifetime, immutable text, provider/native copies and heap roots remain unverified.

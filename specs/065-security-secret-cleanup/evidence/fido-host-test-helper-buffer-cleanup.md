# FIDO host-test helper buffer cleanup

- **Finding:** SEC065-CORE-044
- **Owners:** `TestCredentialIds.testCredentialIdFromLabel`; `SensitiveCborCodecMock.stubSensitiveRequestCompatibility`
- **Classifications:** CLS-FIDO-001/002/007/008
- **Requirements:** FR-003/004/010; NFR-SEC-041; TR-AUTH-031; T005/T006/T012
- **Date:** 2026-11-17

## Corrections

`testCredentialIdFromLabel` now clears both the UTF-8 label bytes and the padded credential-ID bytes in `finally` after canonical encoding. The returned `CredentialId.encoded` immutable Base64URL test value remains with its caller as intended for test use.

`stubSensitiveRequestCompatibility` previously mocked `eraseDecodedRequestValues` as a no-op. It now recursively clears decoded `ByteArray` values and `SensitiveCredentialText` owners through maps and iterables, matching the production cleanup contract. A focused regression retains nested synthetic owners and asserts their contents are erased after the helper's cleanup call.

## Verification

- Focused `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.testing.SensitiveCborCodecMockTest :feature:fido2:ktlintCheck`: passed.
- Full `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck`: passed, 1,287 tests across 170 reports, zero failures/errors/skips.
- `SensitiveCborCodecMockTest.xml` SHA-256: `77ABED03C8CA63245EAA625BDF346BAA8788DAA43AC7BF8A1B1331D126C4A81F`.
- `Ctap2MakeCredentialHandlerValidationTest.xml` SHA-256: `1A97BB1E035A93D74B10503AC235023AB997A1EABB4AAA086D6B865F309AA045`.
- `Ctap2GetAssertionHandlerTest.xml` SHA-256: `5E908870A3726C87390BC9247CE5687017A84CF5EBEB63B9538587619661D0EF`.

This is host-test fixture/helper evidence only. Immutable returned credential-ID Strings, decoder/provider implementation beyond the covered helper recursion, production callers, native copies and heap roots remain outside this claim.

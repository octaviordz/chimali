# CBOR decoded owner list admission cleanup

Date: 2026-11-18  
Finding: SEC065-CORE-051  
Source: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/CborCodec.kt`

## Source review and correction

The recursive CBOR decoder copied each byte string or created a `SensitiveCredentialText` owner, then inserted it into the method-level cleanup list. If insertion failed, the newly created value was not present in the `decodeMap` `finally` cleanup and could remain populated after an exceptional allocation failure.

The decoder now uses staged admission helpers for both owner types. Each helper erases its current value unless the cleanup list accepts it; after admission the existing map-level transfer/failure cleanup remains responsible.

## Verification and limitations

Failure injection was added immediately before cleanup-list admission for both decoded owner types. `CborCodecTest` passes 20 tests, including the regression that confirms text owners are zeroed and byte owners are cleared when admission throws. The full FIDO host suite passes 1,357 tests across 175 reports, with zero failures, errors or skips; `:feature:fido2:ktlintCheck` passes. Borrower completion, provider/native copies and heap-root behavior remain open.

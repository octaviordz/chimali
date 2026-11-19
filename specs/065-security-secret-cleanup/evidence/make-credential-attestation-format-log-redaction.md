# MakeCredential attestation format diagnostic redaction

**Date:** 2026-11-17  
**Finding:** SEC065-LOG-058  
**Requirements:** FR-008/009; NFR-SEC-041; TR-AUTH-031  
**Revision:** Dirty exploratory worktree; not a frozen product candidate

## Finding and correction

`Ctap2MakeCredentialHandler.encodeAttestationResponse` interpolated `attestation.fmt` into a debug diagnostic. The protocol requires this value in the response, but it does not need to be copied into local logs. The diagnostic now uses a fixed label and retains only `authDataBytes.size`; the CBOR response continues to carry the format field unchanged.

The existing registration terminal-path test now supplies the synthetic account marker as the attestation format during the success path and requires it to be absent from captured diagnostics. Against the original log line, the success case failed in the diagnostic privacy assertion; after replacing the dynamic format with a fixed label, the focused class passed.

## Verification

- Focused `Ctap2MakeCredentialHandlerValidationTest`: **24 tests passed**.
- Complete `:feature:fido2:testAndroidHostTest`: **1,310 tests across 172 reports**, zero failures/errors/skips.
- `:feature:fido2:ktlintCheck`: passed.

No real credential, attestation or account data was used. This source regression does not verify the configured release/debug destinations, all diagnostic emitters, or frozen-candidate behavior.

## Evidence hashes

- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2MakeCredentialHandler.kt` SHA-256 `6ba4f29862451385d641b2a9d255ace3357d9b295fbb30fd7a56e7236b5198c9`
- `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/ctap2/Ctap2MakeCredentialHandlerValidationTest.kt` SHA-256 `04a066073cb74b0b71ba896e6d713e0313a43a251aef7135b960c0f9704ec674`
- `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.ctap2.Ctap2MakeCredentialHandlerValidationTest.xml` SHA-256 `2f83cd9a0c62527fd149fa504877ef1e8abe4e58cb8383cf503a6c531c0595fd`
- `feature/fido2/build/reports/tests/testAndroidHostTest/index.html` SHA-256 `2e61991736ae781864c7f4ff3d6db1e3b62782737434aca7e161501149e02281`

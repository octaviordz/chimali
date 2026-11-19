# CTAP sensitive text owner list admission cleanup

Date: 2026-11-18  
Finding: SEC065-CORE-049  
Sources:

- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2MakeCredentialHandler.kt`
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2GetAssertionHandler.kt`

## Source review and correction

Both handlers created mutable sensitive-text owners and then admitted them to a local cleanup list. If list admission failed, the newly created owner was not present in the outer `finally` cleanup set. The assertion response path had this gap for credential ID, name and display-name projections; the MakeCredential parser had the same gap for temporary String-to-owner conversions.

Each path now retains the owner only after successful list admission. If admission fails, a local `finally` erases that owner immediately. Once admitted, the existing outer cleanup remains responsible until successful ownership transfer or method termination.

## Verification and limitations

Injected list-admission failures now cover both handlers. `Ctap2MakeCredentialHandlerValidationTest` passes 25 cases and `Ctap2GetAssertionHandlerTest` passes 20; each verifies that the current unadmitted owner is zeroed. The full FIDO host suite passes 1,360 tests across 175 reports with zero failures, errors or skips; KtLint passes. Caller terminal cleanup, transport/provider copies and heap-root absence remain open.

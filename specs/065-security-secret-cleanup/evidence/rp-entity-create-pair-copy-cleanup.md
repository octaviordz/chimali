# RP entity create pair-copy failure cleanup

Date: 2026-11-18  
Finding: SEC065-CTAP-012

## Source correction

`PublicKeyCredentialRpEntity.create` now stages its RP ID and name owners through a guarded factory. If creating the second owner or constructing the entity fails, the first staged owner is erased. Ownership transfers to the returned entity only after successful construction.

## Verification and limitations

The regression injects failure during creation of the second owner and verifies that the first staged copy is zeroed. The full `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck` run passed: 1,336 tests across 172 reports, with zero failures, errors or skips; KtLint passed. Allocation exhaustion was not injected. UI/platform copies, provider behavior, process interruption and heap roots remain open.

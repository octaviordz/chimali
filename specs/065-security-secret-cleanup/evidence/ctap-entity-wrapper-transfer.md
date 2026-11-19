# CTAP request entity wrapper-transfer cleanup

Date: 2026-11-18  
Finding: SEC065-CTAP-012

## Source correction

`PublicKeyCredentialRpEntity.fromSensitiveOwners` now keeps the RP ID/name pair under caller cleanup until entity construction succeeds; failure erases both owners. `PublicKeyCredentialUserEntity.fromSensitiveOwners` applies the same transfer rule to the user-ID bytes, name and display-name owner. Deterministic model-factory failures verify cleanup of every original buffer.

The production MakeCredential handler also retains its outer request cleanup, so this change makes the entity factory contract locally complete without weakening request-terminal cleanup.

## Verification and limitations

The full FIDO host suite passes 1,335 tests across 172 reports with no failures, errors or skips. FIDO KtLint passes. Allocation exhaustion was not injected; downstream UI/platform copies, provider behavior, process interruption and heap roots remain open.

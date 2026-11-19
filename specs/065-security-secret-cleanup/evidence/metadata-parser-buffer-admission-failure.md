# Metadata parser buffer-admission failure cleanup

Date: 2026-11-18  
Findings: SEC065-CRYPTO-001, SEC065-FIDO-OWNER-001

## Regression

The metadata parser ownership test now injects a failure while allocating the next scratch buffer after the RP ID has already been parsed. It verifies that prior parser buffers and the decrypted plaintext are erased during unwinding. This exercises the partial-parse allocation-failure path in `CredentialMetadataProtectionService`.

## Verification and limitations

The focused `CredentialMetadataProtectionServiceOwnershipTest` suite passes 12 cases with zero failures, errors or skips. `:feature:fido2:ktlintCheck` passes. This does not establish provider/native copies or heap-root release; allocation exhaustion outside the injected buffer-factory boundary remains unverified.

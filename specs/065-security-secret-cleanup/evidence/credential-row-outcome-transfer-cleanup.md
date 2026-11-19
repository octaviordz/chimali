# Credential row outcome-wrapper transfer cleanup

Date: 2026-11-18  
Finding: SEC065-CORE-053  
Source: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/mapper/EntityMappers.kt`

## Source review and correction

`mapCredentialRecord` constructed a `PasskeyCredential`, cleared its local owner references to mark the model transferred, and only then allocated `Outcome.Success`. If result-wrapper allocation failed, no caller had received the credential and the mapper no longer retained an owner reference to erase its text and credential-ID buffers.

The mapper now retains the constructed model until `Outcome.Success` has been created. If wrapper construction or any earlier operation fails, the `finally` erases the model's sensitive text and credential-ID owner, while the existing local cleanup clears any pre-transfer aliases. Ownership is marked transferred only after the result wrapper exists.

## Verification and limitations

`EntityMappersTest` passes 31 tests, including a synthetic `Outcome.Success` factory failure that verifies the constructed credential's RP/user text and credential-ID bytes, plus source-row buffers, are erased. The full FIDO host suite passes 1,358 tests across 175 reports with zero failures, errors or skips; KtLint passes. AAGUID/public-key lifecycle, caller terminal cleanup, database/provider copies and heap-root absence remain open.

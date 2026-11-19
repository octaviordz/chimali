# Credential search and integration fixture owner lifecycle

**Date:** 2026-11-16  
**Scope:** FIDO credential search query flow, management ViewModel cancellation, and synthetic integration-test fixture cleanup.

## Search owner path

`SearchCredentialsUseCase` returns the repository cold flow and borrows the caller-owned `SensitiveCredentialText` query for the full collection. `CredentialManagementViewModel` copies that query for each collection, stages emitted credentials until successful completion, and erases the operation copy in `finally`. Cancellation or failure erases staged values; successful values transfer into ViewModel state, which has separate replacement, screen-stop and ViewModel-clear cleanup. The use-case KDoc now describes the full query/emission contract.

A regression captures the operation query while its search flow is suspended and confirms it is readable during collection, then submits a replacement query and verifies the prior operation owner is zeroed after cancellation. The focused ViewModel suite and full FIDO host suite pass.

## Synthetic integration fixture cleanup

`ManagementIntegrationTest` previously kept generated mutable `PasskeyCredential` fixtures in a shared `MutableStateFlow` after each test and did not clear its ViewModel. Teardown now clears the ViewModelStore, erases every tracked synthetic credential and empties the flow. This test-only correction is recorded as SEC065-CORE-039; focused integration tests, full FIDO host suite and KtLint pass.

## Remaining scope

This source/host evidence does not establish Compose or accessibility snapshots, keyboard/IME retention, production caller cleanup after successful search, SQLDelight/provider/native copies, heap roots, physical UI behavior or frozen-candidate evidence. These remain open under T005/T006/T012/T015/T027/T036.

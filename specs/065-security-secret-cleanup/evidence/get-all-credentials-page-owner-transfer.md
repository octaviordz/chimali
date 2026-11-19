# GetAllCredentials page owner transfer

Date: 2026-11-15  
Scope: `GetAllCredentialsUseCase` and its current production caller, `CredentialManagementViewModel`.

## Source contract

`CredentialRepository.getPagedCredentials` transfers each mutable `PasskeyCredential` in a successful page to its caller. Errors transfer no credential owner. `GetAllCredentialsUseCase` forwards the page without copying or retaining it, and documents that its caller must erase each credential after its final borrower completes.

The repository interface now states the corresponding contracts for paged retrieval and Flow emissions. A Flow producer remains responsible for erasure when downstream emission fails before transfer; a collector owns a credential after successful emission.

## Regression

`GetAllCredentialsUseCaseTest` now uses a real synthetic credential owner to verify the use case forwards the identical model, preserves its mutable RP/user text until caller erasure, and that `eraseSensitiveText` zeros those fields. The caller test cleanup also erases the fixture on assertion failure.

Focused command:

```text
gradlew :feature:fido2:testAndroidHostTest --tests com.chimali.fido2.domain.usecase.GetAllCredentialsUseCaseTest :feature:fido2:ktlintCheck
```

Result: 3 tests passed, 0 failures/errors/skips; KtLint passed.

## Limits

The only production caller found is `CredentialManagementViewModel`, whose full and visible credential state owns page models and erases state during replacement/removal/disposal. This is source and host-test evidence. Compose renderer copies, Android runtime behavior, provider copies, and heap-root release are not established; T006/T015 remain open.

# Credential search query borrower contract

**Scope:** `CredentialRepository` API, `SearchCredentialsUseCase`, `CredentialRepositoryReadOperations.searchCredentials`, and caller-owned `SensitiveCredentialText`.

`SearchCredentialsUseCase` returns a cold Flow and forwards the same mutable `SensitiveCredentialText` reference. Repository collection reads it lazily while matching user names/display names. It does not copy or erase the query. The caller retains ownership, must keep the text valid through collection, and erases it after collection ends. Repository-produced credential models transfer on successful Flow emission; nonmatches, rejected emissions, and source rows are erased by the repository.

`CredentialRepositoryImplTest` verifies a query remains readable during search and erases it in the caller's `finally`. The full FIDO host suite passes 1,271 tests across 168 reports with zero failures/errors/skips; KtLint passes and Detekt reports zero findings. This is a source/host contract and does not establish UI collection lifetime, concurrent collectors, database/provider copies, or heap roots (T005/T006/T015/T027).

# Credential-management page admission cleanup

Date: 2026-11-18  
Finding: SEC065-FIDO-OWNER-004  
Requirements: FR-003/004/010; NFR-SEC-041; TR-AUTH-031; T012  
Sources: `CredentialManagementViewModel.kt`, `CredentialManagementViewModelTest.kt`

## Ownership correction

`GetAllCredentialsUseCase` returns a caller-owned credential page. Before this correction, `CredentialManagementViewModel.loadCredentials` appended that page to its retained list with `fullCredentialList + newItems`. If replacement-list construction failed, the incoming page had not transferred to ViewModel state and had no cleanup boundary.

The ViewModel now uses `combineOwnedCredentialPage`. It transfers the incoming owners only after the combined list has been constructed. If construction throws, it erases incoming owners that are not identity-aliased by the retained page and propagates the failure; the existing retained page stays live.

## Verification and limits

The new regression injects a throwing page-composition function and verifies that the incoming credential text is erased while the retained credential remains readable. `CredentialManagementViewModelTest` passes 32 tests. The full `:feature:fido2:testAndroidHostTest` suite passes 1,354 tests across 175 reports with zero failures, errors or skips, and `:feature:fido2:ktlintCheck` passes.

The injected exception exercises the failed-transfer path but does not force JVM allocation exhaustion. Compose/framework text snapshots, external provider copies, and heap roots remain outside this host test.

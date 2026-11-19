# GetAssertion candidate-result transfer cleanup

Date: 2026-11-18  
Finding: SEC065-FIDO-OWNER-004  
Requirements: FR-003/004/010; NFR-SEC-041; TR-AUTH-031; T012  
Sources: `GetAssertionUseCase.kt`, `GetAssertionCandidateOwnershipTest.kt`

## Ownership correction

`GetAssertionUseCase.getCandidateSummaries` selected candidate summaries, marked them transferred, and only then created `Outcome.Success`. If result construction threw, the cleanup block treated selected candidates as caller-owned even though no result had been returned.

The new `createOwnedCandidateResult` helper owns the complete repository result while candidate selection and result construction run. It transfers selected summaries only after the result factory succeeds. On selection or wrapper failure it erases every repository-owned summary; on success it erases excluded summaries and transfers only the selected owners.

## Verification and limits

The new regression injects a throwing result factory and verifies that both the selected and excluded summary owners are erased. The focused `GetAssertionCandidateOwnershipTest` class passes 2 tests. The full `:feature:fido2:testAndroidHostTest` suite passes 1,355 tests across 175 reports with zero failures, errors or skips, and `:feature:fido2:ktlintCheck` passes.

The test covers a throwing factory, not JVM allocation exhaustion during `Outcome.Success` creation. The existing CTAP handler remains responsible for erasing successfully transferred candidate summaries after its last borrower completes; platform/provider copies and heap roots remain open.

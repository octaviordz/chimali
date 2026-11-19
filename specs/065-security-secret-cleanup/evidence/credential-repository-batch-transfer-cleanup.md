# Credential repository batch transfer cleanup

Date: 2026-11-18  
Finding: SEC065-CORE-054  
Source: `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/CredentialRepositoryReadOperations.kt`

## Source review and correction

Several batch APIs set their transfer marker before allocating `Outcome.Success`; if wrapper construction failed, their `finally` skipped cleanup even though no caller had received the result. Batch hydration also inserted newly created credential/summary models directly into local lists, so a failed list admission could leave the current model outside the batch cleanup set.

The APIs now construct the result wrapper before marking ownership transferred. All five list-return paths use a shared `createOwnedBatchResult` helper that erases every batch item if result construction throws, and transfers only after the wrapper factory returns. Credential and summary admission helpers retain each model only after list insertion succeeds and erase the current model if insertion fails. Existing `finally` blocks still clear all previously admitted items and source rows on failed exits.

## Verification and limitations

`:feature:fido2:compileAndroidMain` passes after the correction. Regressions inject both rejected list admissions and a throwing result-wrapper factory for `PasskeyCredential` and `CredentialSummary`; they verify that owned text and the credential-ID buffer are erased, while independent source owners remain live. The focused `CredentialRepositoryImplTest` class passes 15 tests; the full `:feature:fido2:testAndroidHostTest` suite passes 1,353 tests across 175 XML reports with zero failures, errors or skips, and `:feature:fido2:ktlintCheck` passes. The injected exception verifies the shared result-construction cleanup path; it does not simulate JVM allocation exhaustion during `Outcome.Success` allocation. Caller terminal erasure, DB/provider copies and heap-root behavior remain open.

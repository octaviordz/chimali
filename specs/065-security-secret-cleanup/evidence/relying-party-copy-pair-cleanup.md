# RelyingParty copy pair-owner cleanup

Date: 2026-11-18  
Finding: SEC065-CORE-001

## Source correction

`RelyingParty.copy` now stages both independent text owners until the new model has accepted custody. If producing the second owner fails, the first staged owner is erased. If model creation fails after both owners exist, the guarded `fromOwnedText` path and the copy's `finally` clear them. A deterministic owner-factory failure after the first copy verifies cleanup and confirms the source model remains readable.

## Verification and limitations

The full `:core:domain:testAndroidHostTest` suite passes 107 tests across 24 reports with no failures, errors or skips. `:core:domain:ktlintCheck` passes. Allocation exhaustion was not injected; serializer/DAO/provider copies and heap-root behavior remain open.

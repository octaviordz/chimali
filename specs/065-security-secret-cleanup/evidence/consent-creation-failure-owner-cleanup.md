# Consent creation failure owner cleanup

**Date:** 2026-11-16  
**Finding:** SEC065-CORE-038  
**Scope:** `GetUserConsentUseCase.invoke` consent-record ownership from creation through repository save and result transfer.

The use case creates a `UserConsentRecord` with mutable RP text, then passes it to `saveUserConsent`. Before the fix, an `Outcome.Error`, cancellation, or thrown repository exception exited without erasing that owner. The use case now retains ownership until the storage call succeeds and the caller result is constructed. A `finally` erases the record unless that success result transfers it to the caller.

`GetUserConsentUseCaseTest` captures the synthetic record and asserts each RP code unit is zero after a storage error and after repository cancellation. Existing successful cases exercise returned RP text and remain responsible for caller cleanup. The full FIDO host suite passes 1,283 tests across 169 reports with zero failures/errors/skips; FIDO KtLint passes.

This closes the use-case-owned failure/cancellation path only. Caller cleanup after successful transfer, optional immutable consent context fields, SQLDelight/generated/provider copies, production callsite reachability and heap roots remain open under T005/T006/T012/T015.

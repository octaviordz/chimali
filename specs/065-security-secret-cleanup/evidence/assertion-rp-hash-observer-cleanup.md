# SEC065-CORE-040 — GetAssertion RP hash observer failure cleanup

**Date:** 2026-11-16  
**Revision:** dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

`GetAssertionUseCase` created an owned RP-ID hash and invoked its internal observation hook before entering the `try/finally` that erases the hash. If the hook threw, the operation abandoned the mutable hash without zeroing it. The regression captures the hash and throws a synthetic `IllegalStateException`. Against the original placement, the test failed because the captured array remained nonzero. The hook now runs inside the protected region, and the regression confirms the hash is zero after the exception and that sign-counter processing never starts.

Verification: `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.domain.usecase.GetAssertionUseCaseTest :feature:fido2:ktlintCheck` passes all 24 cases in `GetAssertionUseCaseTest` and FIDO KtLint. The full FIDO host suite then passes 1,285 tests across 169 reports with zero failures, errors, or skips. The initial invocation exposed a missing test stub before reaching the assertion; after adding the stub, the regression failed against the original source and passed with the fix.

Limits: this is host evidence for the in-process mutable hash owner. It does not establish `MessageDigest` provider copies, Android/native behavior, heap-root absence, configured diagnostic destinations, or frozen-candidate acceptance.

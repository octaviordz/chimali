# Passkey event JSON capacity-failure cleanup

**Finding:** SEC065-CORE-024  
**Date:** 2026-11-15  
**Candidate:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

The test-first build failed because the new test referenced the not-yet-added `SensitiveJsonFields.encodeSanitized` owner boundary. `SensitiveJsonFields.encode` now transfers the sanitized serialization array into a helper whose `finally` covers checked capacity arithmetic, output-buffer construction, field replacement and result copy. Overflow in `Math.multiplyExact`/`Math.addExact` throws; both the sanitized byte array and any constructed `ErasableJsonBuffer` are erased. Placeholder bytes are cleared in their own `finally`.

`PasskeyEventStorageOwnershipTest` forces capacity overflow using a lazy repeating value collection and a small retained sanitized JSON array. It confirms the expected `ArithmeticException` and verifies every sanitized byte is zeroed. The complete ownership class passes after correction, and the Android-host KtLint check passes.

The complete `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck` run passes 1,245 tests across 166 XML reports with zero failures/errors/skips. This covers application-owned serializer/intermediate buffers only; encryption provider, SQLDelight/driver copies, storage pages, successful event/state consumer release and heap roots remain open under T005/T006/T012/T015.


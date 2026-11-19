# Request entity ownership failure test cleanup

**Date:** 2026-11-17  
**Requirements:** FR-002/003/004/010; T005/T006/T012.

`RequestEntityOwnershipFailureTest` now records every test-owned `SensitiveCredentialText`, byte array, and RP/user entity and erases/releases them from `@AfterTest`, including when assertions fail. The helper used to inspect `copyCharacters()` now clears that temporary `CharArray` in `finally`, rather than leaving another untracked copy. This preserves the existing assertions for failed construction, copy isolation, and independent owner cleanup.

Verification: `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.domain.model.RequestEntityOwnershipFailureTest` passed both cases; `:feature:fido2:ktlintCheck` passed. The test covers synthetic RP/user values only; this is fixture-owner evidence, not production or heap evidence.
Focused XML result: `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.domain.model.RequestEntityOwnershipFailureTest.xml` SHA-256: `7755d9b65e62fd8e66257ea33f65292636f6286fa504bd4941462adb64014245`.


Source SHA-256: `98c592eb9845e4db5340a93fc1c4d9d9594f2485474e3a2aeb44e5c0790bfb6d`.

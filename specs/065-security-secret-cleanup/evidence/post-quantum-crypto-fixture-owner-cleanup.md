# Post-Quantum Crypto Fixture Owner Cleanup

**Date:** 2026-11-15  
**Revision:** dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

`PostQuantumCryptoTest` created synthetic seeds, fixture signatures/messages, public-key output arrays and malformed input arrays without one terminal cleanup owner. It now tracks arrays created by the test helpers and clears them in `@AfterTest`, so cleanup runs after both normal completion and assertion/exception exits. The test also clears byte arrays returned by the provider's seed accessor and the digest temporary used to render a public-key fixture hash.

The production `PostQuantumCrypto` path clears its copied xi and exported private-key encoding. It returns a Bouncy Castle ML-DSA `PrivateKey` object to its caller for one signing operation. Inspection of the configured BC 1.80 class/API found that its provider implementation does not override `Destroyable`; the Java default `destroy()` throws `DestroyFailedException`. Its internal key parameters therefore cannot be erased through the supported public API. The FIDO caller keeps this object in operation scope, but provider-object/heap behavior remains a documented limitation rather than a successful erasure claim.

The source inventory now maps production and test owners separately under `CLS-KEY-003`.

Verification: the full `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck` run passes. `PostQuantumCryptoTest` passes all 12 cases; the full FIDO host suite passes 1,247 tests across 166 XML reports with zero failures/errors/skips, and KtLint passes.

**Limits:** Host tests cannot inspect BC private-key fields, provider copies, immutable fixture strings, native allocations, or heap roots. This test fixture cleanup does not prove erasure of those provider/runtime owners.

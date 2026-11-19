# HMAC Secret Test Fixture Cleanup

**Date:** 2026-11-15  
**Revision:** dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

Source review of `HmacSecretProcessorZeroingTest` found that its reference-output helper created a domain-separated credential input and a temporary HMAC output without erasing either. The helper now retains and clears both arrays in `finally`; it also computes only the second 32-byte block when the synthetic salt fixture contains two blocks. The processor's production ownership contract is unchanged.

Verification: `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.crypto.HmacSecretProcessorZeroingTest :feature:fido2:ktlintCheck` passed. The class contains five cases with zero failures/errors/skips; FIDO KtLint passed.

The source inventory now maps both the production processor and host test independently under `CLS-KEY-003`; it corrects the prior erroneous vault ownership link for this test. Current inventory totals: 663 files and 282 usage records (260 Relevant, 376 Open, 27 assessed-no-relevant-usage). A full current-path/hash/usage-link check reports zero missing paths, stale hashes, duplicate IDs, broken or nonreciprocal links, Relevant files without usage, or missing no-use rationales.

**Limits:** This test-fixture cleanup does not verify JCA `Mac`/`SecretKeySpec` internal copies, production callers' release of the returned result, or heap roots. The independent physical-device, managed-heap, full-source-inventory and frozen-candidate obligations remain open.

# SEC065-ERR-022 — Registration challenge decoder cause discard

**Date:** 2026-11-16  
**Revision:** dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

`MakeCredentialOptions.withDecodedBase64Challenge` translated malformed Base64 into a fixed public error but retained the platform decoder exception as its cause. The retained cause includes decoder detail derived from the supplied input and can travel through downstream error formatting.

The new `MakeCredentialOptionsTest.malformed base64 challenge does not retain decoder failure` regression used a synthetic malformed challenge marker and asserted the fixed message and a null cause. It failed before the correction because the wrapped `IllegalArgumentException` was present. The decoder now discards that lower-level exception and returns only `IllegalArgumentException("Invalid base64 challenge")`.

Verification: focused `MakeCredentialOptionsTest` and FIDO KtLint passed. Full `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck :feature:fido2:detekt` passed: 1,266 tests across 167 XML reports, zero failures/errors/skips; KtLint and Detekt passed.

Limits: JVM Android-host/source evidence only. Other challenge projections, downstream diagnostic destinations, native/provider behavior, immutable challenge Strings and heap retention remain open under T005/T006/T012/T029/T033/T035.

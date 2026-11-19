# CBOR shape test fixture cleanup

**Date:** 2026-11-17  
**Finding:** SEC065-CRYPTO-002 follow-up  
**Requirements:** FR-003/004/010; NFR-SEC-041; TR-AUTH-031; T005/T006/T012.

`CryptoUtilsTest` retains four CBOR protocol-shape cases after the unused `MemoryUtils` helper and its direct tests were removed. The remaining cases owned synthetic RP hash, AAGUID, credential ID, client-data challenge and encoded protocol buffers, but did not clear them after each test.

The test now tracks each explicitly owned `ByteArray` and clears/releases the tracked buffers in `@AfterTest`. The decoder result map in the first case is passed to `CborCodec.eraseDecodedRequestValues` from `finally`. The test uses only synthetic values. Immutable synthetic RP/user/type/origin strings remain GC-managed.

## Verification

Focused `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.crypto.CryptoUtilsTest :feature:fido2:ktlintCheck` passes all four cases and KtLint. The subsequent full FIDO host suite passes 1,290 tests across 171 reports, zero failures/errors/skips; KtLint passes. Current `CryptoUtilsTest.xml` SHA-256: `578444d204cde0ed400952df5ec645d1a78106f81f77f8ac92cd4802880c08ae`.

This evidence covers test-owned mutable arrays only. It does not establish the lifetime of production protocol values, compatibility-decoder immutable strings, provider/framework copies or heap roots.

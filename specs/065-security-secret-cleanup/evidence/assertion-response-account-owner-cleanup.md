# SEC065-CORE-041 — CTAP assertion response account owner cleanup

**Date:** 2026-11-16  
**Revision:** dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

`Ctap2GetAssertionHandler.handleRaw` owns a successful `AssertionObject` while encoding its response. The response finalizer previously zeroed `authData` and `signature` but left the optional `PublicKeyCredentialUserEntity` ID/name/display-name arrays populated. Its `encodeResponse` helper also left its local map holding account fields after the encoder completed.

A synthetic regression retains the serializer map and user owner. Before the fix, it failed because the map still held account-field references after response encoding. The response finalizer now calls `eraseSensitiveText()` on the assertion user, and `encodeResponse` clears its map in `finally`. The success regression verifies the map is empty and mutable user fields are zeroed. The existing encoder-failure regression now also verifies that user fields are cleared on that exceptional path.

Verification: `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.ctap2.Ctap2GetAssertionHandlerTest :feature:fido2:ktlintCheck` passes all 19 handler cases and FIDO KtLint. The full FIDO host suite passes 1,286 tests across 169 reports with zero failures, errors, or skips.

Limits: the follow-up below removes immutable account-field copies at this response-map boundary. CBOR/provider internals beyond the app-owned encoder buffers, `RawCtapResult`/transport copies and heap-root absence remain unverified. The returned response bytes remain caller-owned protocol output.




## Mutable CBOR projection follow-up (2026-11-17)

A second source review found that the response map materialized immutable copies through `PublicKeyCredentialUserEntity.id`, `.name` and `.displayName` before the CBOR encoder ran. The existing `CborEncodingEngine` already accepts `SensitiveCredentialText`, writes directly from mutable owners, and clears temporary UTF-8 bytes. `Ctap2GetAssertionHandler.encodeResponse` now makes owned mutable projections, uses the name owner for the empty-display-name fallback, erases the unused empty temporary immediately, and clears all retained projections in its `finally` block. The protocol response content and field names are unchanged.

The new assertions failed before the correction because the retained response-map fields were not mutable owners. The focused 19-case `Ctap2GetAssertionHandlerTest` and `:feature/fido2:ktlintCheck` pass after the change. The complete FIDO Android host suite also passes 1,311 tests across 172 reports with zero failures, errors or skips. The failure-path regression captures the actual encoder map and verifies the mutable projection owners are zeroed after the encoder throws; the success-path regression checks field content during encoding and verifies zeroing afterward.

Limits: CBOR response bytes remain live caller-owned protocol output. This does not establish provider/transport copies, heap-root absence, device behavior or frozen-candidate status.

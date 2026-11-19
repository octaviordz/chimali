# CTAP CBOR decoder map ownership review

- **Finding:** SEC065-CTAP-006/007
- **Owner:** `CborCodec.decodeSensitiveRequest`, `decodeMap`, and `eraseDecodedRequestValues`
- **Date:** 2026-11-17
- **Scope:** Parser-created map/value aliasing and request-map custody; not provider/native or heap evidence.

## Source trace

The decoder copies each CBOR byte-string range with `copyOfRange`, so each parsed byte value has a distinct mutable owner independent of the borrowed request buffer. Arrays and maps are newly allocated as the input tree is traversed. No source-level parser path was found that inserts one decoder-created mutable value under multiple map/list positions.

Sensitive text values are decoded into `SensitiveCredentialText` owners. Map-key owners are compared against the fixed CTAP field-name allow-list and erased immediately; an unknown text key is omitted and its recursively decoded values are erased before continuing. If decoding does not return a complete map, the decoder's `finally` erases every byte/text owner registered during parsing. A valid map transfers its mutable values to the MakeCredential or GetAssertion handler; both handlers erase decoded map descendants in their request-terminal `finally` blocks.

The GetAssertion HMAC-secret processor runs within the handler's awaited request path. The source trace establishes that the decoded map remains owned through that call and is erased when the handler reaches its terminal cleanup. It does not prove that every nested downstream implementation avoids retaining a separate borrower after the awaited call, nor that CBOR/JVM/provider layers leave no copies.

## Verification

The current `CborCodecTest` report contains 18 tests, with zero failures, errors, or skips. Coverage includes successful mutable text transfer and erasure, key-owner cleanup, unknown nested value cleanup, HMAC salt erasure, and partial-owner erasure when a non-format exception interrupts decoding. The full FIDO host suite report contains 1,327 tests across 172 reports, with zero failures, errors, or skips; KtLint passes.

## Remaining scope

This review closes only the parser-created alias question for the current source. Joined borrower lifetime outside the awaited handler call, SQL/provider/native copies, physical transport/framework copies, heap roots, and frozen-candidate reconciliation remain open under T006/T012/T015/T037.

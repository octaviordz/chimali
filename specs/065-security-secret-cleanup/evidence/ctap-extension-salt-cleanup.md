# CTAP extension salt owner

## Evidence record

- **Requirement:** NFR-SEC-041; TR-AUTH-031; FR-003/004/009/010; T009/T012
- **Scope:** Raw CBOR decoding of nested GetAssertion `hmac-secret.saltEnc` and cleanup of the decoded byte-string owner
- **Revision:** Current dirty worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; exploratory, not frozen
- **Named procedure:** `CborCodecTest.sensitive decoding preserves hmac salt input until request cleanup`
- **Environment:** Windows host; Gradle 9.6.0; JDK 25 launcher
- **Expected and observed result:** A CBOR request with `extensions.hmac-secret.saltEnc` decodes to the same mutable byte owner during active use; `eraseDecodedRequestValues` zeroes that owner at request cleanup. The raw-CBOR test failed before correction because the decoder dropped the nested key's value, and passed after adding `saltEnc` to the fixed sensitive-key allowlist.
- **Date:** 2026-11-14
- **Limitations:** Focused host decoder regression only. It does not prove the complete GetAssertion UI/session lifecycle, alternate platform decoders, provider copies, heap-root absence, or full CTAP2.1 hmac-secret conformance. Other unknown nested fields continue to be discarded.
- **Status:** Passed (focused owner-retention and cleanup behavior)
- **Artifact link:** `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.crypto.CborCodecTest.xml`; `feature/fido2/build/reports/tests/testAndroidHostTest/index.html`

## Finding and correction

`CborCodec.decodeSensitiveRequest` decodes text map keys into mutable owners and retains only a fixed allowlist. The allowlist included `hmac-secret` but omitted its consumed nested key `saltEnc`. As a result, the decoder erased the byte-string value before the active `HmacSecretProcessor` borrower could read it. The allowlist now includes `saltEnc`; the decoded owner remains request-scoped and the outer handler erases it through `eraseDecodedRequestValues` on terminal cleanup.

The focused `CborCodecTest` class passes 18 tests with no failures, errors or skips. The full current-source FIDO request lifecycle and all joined borrowers remain under T009/T012. This finding covers the observed extension input path and does not certify other nested extension fields.

## Consumer handoff follow-up (2026-11-14)

Added `HmacSecretProcessorZeroingTest.sensitive CBOR salt owner remains readable through HMAC processing then request cleanup erases it`. It passes the decoded nested extension map to the real `HmacSecretProcessor`, confirms the same salt owner remains nonzero during processing, verifies the processor erases its owned root copy, and then confirms outer request cleanup zeros the salt owner. The complete five-case `HmacSecretProcessorZeroingTest` class passed on the Windows JVM host. This closes the decoder-to-consumer ownership path in the host harness; the production `handleRaw` run, provider/native copies and HPROF root attribution remain unverified.

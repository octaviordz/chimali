# Multi-algorithm integration test fixture cleanup

`MultiAlgorithmIntegrationTest` runs ECDSA, Ed25519 and ML-DSA registration/authentication with a deterministic synthetic seed and real cryptographic operations. Teardown now erases the seed, derived HDK root pair, provider-returned seed/key copies, challenges and client-data hashes, signature-verification message arrays, MakeCredential/GetAssertion option owners, registration attestation/credential results, assertion auth-data/signatures, repository-stored independent credential copies and all returned summary RP owners. It clears MockK stubs/call history and the Android Log static interception. A teardown regression checks root/provider key and byte fixture zeroing; the in-memory repository has an explicit clear operation.

Credential IDs and RP/UserId compatibility encodings are immutable synthetic strings, so their references are released through result cleanup, repository clearing, and local scope rather than overwritten. No disk persistence is used.

Validation: the focused class passes 4 tests; the full FIDO Android host suite passes 1,304 tests across 171 XML reports with zero failures, errors, or skips. `:feature:fido2:ktlintCheck` passes. This is host-only synthetic key/credential evidence; it does not establish provider/native, persistent database, heap, platform logging, or physical-device behavior.

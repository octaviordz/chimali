# MakeCredential credential-identifier diagnostic removal

**Finding:** SEC065-LOG-051  
**Scope:** `Ctap2MakeCredentialHandler` success response diagnostic and its captured host regression.

The handler emitted `Encoding MakeCredential response for credId=${credentialId.size}bytes`. It did not log the identifier bytes, but labeled the dynamic length as a credential ID in an operational log. Since exact credential identifiers are prohibited diagnostic data and response encoding has no need to expose identifier metadata, removed the emission. The existing `registration diagnostics exclude RP and account input on every terminal path` regression now also asserts that `credId=` is absent from captured unsanitized Kermit output on success, malformed input, storage failure, and cancellation.

Verification on 2026-11-16: the full FIDO host suite passed 1,273 tests across 168 reports with zero failures/errors/skips; KtLint and Detekt passed. `Ctap2MakeCredentialHandlerValidationTest` passed all 24 cases; its XML report SHA-256 is `1d695388bb447b0b3e718e499acb4d5e5a4e2508bc38951887433505e90f9e72`. Other MakeCredential output lengths and the fixed attestation-format label remain separate bounded protocol diagnostics; destination-level Logcat/local-file retention remains under T029/T034/T035.

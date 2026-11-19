# Get Assertion credential matching test fixture cleanup

`GetAssertionCredentialMatchingTest` checks allow-list filtering, constant-time credential-ID matching and `credProtect` behavior using synthetic IDs and RP values. Teardown now erases each owned `GetAssertionOptions` RP-text owner and client-data hash, each candidate summary RP-text owner, the produced assertion authentication-data and signature arrays, selector byte fixtures and the mock crypto-signature fixture. MockK stubs and invocation history are cleared so test calls do not keep fixture objects alive. A teardown regression verifies zeroing of representative option, summary and assertion owners.

Canonical `CredentialId` values are immutable Base64URL strings, so their characters cannot be overwritten; the test releases references through local scope, cleared mocks and cleared fixture lists. Its label helper already zeros its temporary label and decoded byte arrays.

Validation: the focused class passes 4 tests; the full FIDO Android host suite passes 1,301 tests across 171 XML reports with zero failures, errors, or skips. `:feature:fido2:ktlintCheck` passes. This is synthetic host-test evidence only; it does not certify production/framework retention, provider/native cleanup, heap reachability or physical-device behavior.

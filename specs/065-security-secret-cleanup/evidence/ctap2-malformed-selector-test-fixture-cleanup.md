# CTAP2 malformed selector test fixture cleanup

`Ctap2GetAssertionMalformedSelectorTest` sends synthetic CTAP2 requests through the handler to verify malformed allow-list and UV-option rejection, plus valid absent, empty, and byte-string selector behavior. The test now recursively tracks mutable selector inputs, owns request/hash/channel/response arrays and decoder-produced byte-string copies, removes its decoder callback, zeros tracked arrays, and clears MockK calls and answers in unconditional teardown. A teardown regression verifies an owned selector fixture is zeroed.

Validation: the focused class passes 3 tests; the full FIDO Android host suite passes 1,300 tests across 171 XML reports with zero failures, errors, or skips. `:feature:fido2:ktlintCheck` passes. This is synthetic host-test evidence only; derived immutable domain values and production/framework, provider/native, heap, or physical-device behavior are not certified by this fixture cleanup.

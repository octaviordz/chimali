# CTAP2 headless assertion test fixture cleanup

`Ctap2GetAssertionHeadlessTest` exercises the handler with synthetic data, including a fixed RP ID, a zero-filled client-data hash, a synthetic CTAPHID channel ID, request bytes, and fake CBOR/HID responses. The test now tracks all mutable arrays it creates or receives, clears them in `@AfterTest`, and clears MockK stubs and recorded calls that can retain fixture references. The codec compatibility stub is installed in `@BeforeTest` so clearing mocks does not leak setup state between tests. A teardown regression verifies an owned fixture array is zeroed.

Validation: the focused test class passes 2 tests; the full FIDO Android host suite passes 1,299 tests across 171 XML reports with zero failures, errors, or skips. `:feature:fido2:ktlintCheck` passes. This is synthetic host-test evidence only; it does not establish production/framework retention, provider/native cleanup, heap reachability, or physical-device behavior.

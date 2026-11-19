# PRF derivation test fixture cleanup

**Finding:** SEC065-FIDO-007  
**Scope:** Synthetic arrays in `PrfKeyDerivationZeroingTest` and `PrfKeyDerivationTest`; no production credential or PRF input is used.

The derivation zeroing test created synthetic input salts and expected output arrays, plus output copies returned by its HMAC mock. It asserted result cleanup but left the input and expected arrays populated after the test. The test now clears the result and all fixture arrays in `finally`, then asserts that the fixture salts and expected outputs are zero. This cleanup also runs if setup, derivation, or an assertion fails.

The earlier `PrfKeyDerivationZeroingTest` evidence above remains valid. A follow-up review found the neighboring nine-case `PrfKeyDerivationTest` also retained mutable salt and mocked output arrays in test-instance fields. Added an `@AfterTest` owner cleanup that erases both salt arrays and both output fixture arrays after every test, including failed assertions and setup. The focused `PrfKeyDerivationTest` passes 9/9 with zero failures/errors/skips; FIDO KtLint passes. XML SHA-256: `0046344568d084ae12edf87ceaf9642d668518e248afc2c1446bd896c1f41424`.

This test-only cleanup does not establish production request, crypto-provider, immutable-copy or heap-root behavior; those remain open under T005/T006/T015.

# PRF test fixture buffer cleanup

**Finding:** SEC065-FIDO-006  
**Scope:** Synthetic arrays owned by Android-host tests; no production credential or PRF input is used.

`PrfExtensionInputTest` keeps synthetic salt arrays in test-instance fields, and `Ctap2GetAssertionHandlerPrfTest` keeps synthetic output arrays in fields borrowed by `PrfExtensionOutput` and its CBOR map. Both test classes now zero these arrays in `@AfterTest` teardown and include direct cleanup assertions. The three-salt rejection case also clears its local salt in `finally`. The PRF input accessor assertions use identity checks rather than creating boxed copies of the salts.

Focused verification on Windows/JDK 25:

- `:feature:fido2:testAndroidHostTest --tests '*Ctap2GetAssertionHandlerPrfTest' --tests '*PrfExtensionInputTest'`
- `:feature:fido2:ktlintCheck`

Results: the focused run passed 21 tests across two XML reports with zero failures, errors, or skips; KtLint passed. A subsequent full FIDO host run passes 1,270 tests across 167 reports with zero failures, errors, or skips; KtLint and Detekt pass. Current full-suite XML SHA-256 values: `103e450dd75b4b66333fb5940ca03148eb1d22170f4c9aac4209034bcfa6632a` (CTAP PRF) and `ac9e1bd4741cf0e41edadc2391e81f1421dc9f05d0f7b3538acc3b203b9bded3` (PRF input).

This closes test-owned mutable fixture lifetime only. Immutable compiler/runtime copies and production request/provider lifecycle evidence remain open under T005/T006/T015.

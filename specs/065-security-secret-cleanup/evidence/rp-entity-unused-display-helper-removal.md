# RP entity unused display-helper removal

**Date:** 2026-11-17
**Finding:** SEC065-CORE-045 follow-up; T006/T012

`PublicKeyCredentialRpEntity.getDomain()` and `getSafeName()` each converted mutable RP ID/name owners into immutable strings. Repository search found no production consumers; only assertions in `Fido2DomainModelsTest` used them. Removed these methods and replaced the assertions with an API-absence regression. The still-used `name` property remains as the consent/UI boundary; request processing continues to borrow mutable owners through `idText` and `nameText`.

**Verification:** `:feature:fido2:testAndroidHostTest --tests '*Fido2DomainModelsTest' :feature:fido2:ktlintCheck` succeeded. `Fido2DomainModelsTest` reports 9 tests, 0 failures, 0 errors, and 0 skips; the full FIDO host suite passes 1,328 tests across 172 reports with zero failures/errors/skips, and KtLint passes.

This removes only unused String conversion helpers. It does not establish lifecycle control over the required consent/UI String, Android framework consumers, persistence/provider copies, external compiled consumers, or heap roots.

- Model source SHA-256: `282732833a6027c065854b873fe7d3663e82490f8f013669b9fe82f1a673af6f`
- Regression source SHA-256: `8d45fd53b3402814149b1a43f9b23427ca7923bf032b9d445fdb8ab7dbe04b05`
- Focused test XML SHA-256: `a5d1fa1b22689bf77a6533d7bff43f320ee6f3296300ddd714101101ce55c42b`

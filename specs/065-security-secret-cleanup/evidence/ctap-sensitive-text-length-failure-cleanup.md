# Sensitive credential text length-failure cleanup

- **Finding:** SEC065-CTAP-010.
- **Requirement:** FR-004/010; NFR-SEC-041; TR-AUTH-031; T012/T015.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.domain.model.SensitiveCredentialTextTest :feature:fido2:ktlintCheck`.
- **Environment:** Windows host; Gradle 9.6.0; JDK 25; synthetic mutable `CharSequence` and destination buffer.
- **Observed:** The new regression failed before the source change because a changing source length triggered `require` outside the cleanup guard and left the prefilled destination unchanged. Moving validation into the guarded block made cleanup run on this failure too. All 5 focused class cases pass and FIDO KtLint passes.
- **Date:** 2026-11-15.
- **Limitations:** Host/source behavior only. Other conversion call sites, immutable compatibility values, provider/runtime retention, heap roots and candidate freeze remain open.
- **Status:** Passed for the specific owner cleanup regression only.
- **Artifacts:** `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.domain.model.SensitiveCredentialTextTest.xml`; `feature/fido2/build/reports/ktlint/`; source and test paths are listed in `source-inventory.json`.
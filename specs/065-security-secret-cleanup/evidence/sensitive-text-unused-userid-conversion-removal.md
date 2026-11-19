# Unused immutable UserId conversion removal

- **Finding:** SEC065-CTAP-013.
- **Requirement:** FR-004/010; NFR-SEC-041; TR-AUTH-031; T012/T005.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.domain.model.SensitiveCredentialTextTest --tests com.chimali.fido2.data.repository.CredentialRepositoryImplTest`; `:feature:fido2:ktlintCheck`.
- **Environment:** Windows host; Gradle 9.6.0; JDK 25; API-surface reflection and synthetic repository fixture.
- **Observed:** Source search found no production or test caller of `SensitiveCredentialText.toUserId`; its public method materialized a new immutable String-backed `UserId`. The new API-absence regression failed before removal, including Kotlin's mangled inline-value return method. The unused method and import were removed. The repository regression now constructs its required test `UserId` explicitly from the synthetic owner at that test boundary. Both focused suites pass: `SensitiveCredentialTextTest` 6/6 and `CredentialRepositoryImplTest` 9/9; the complete FIDO host suite also passes 1,261 tests across 167 reports with zero failures/errors/skips, and FIDO KtLint passes.
- **Date:** 2026-11-16.
- **Limitations:** This removes only the unused conversion API. At the time of this 2026-11-16 record, `toRpId()` still created required existing `RpId` values; follow-up removal and explicit RP-ID boundary handling are documented under SEC065-CORE-045 in `sensitive-text-active-conversion-review.md`. Provider/runtime String lifetime, heap roots, generated-schema owners and frozen-candidate acceptance remain open.
- **Status:** Passed for API-surface removal and its focused host regressions only.
- **Artifacts:** `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.domain.model.SensitiveCredentialTextTest.xml` SHA-256 `cf72c64e4eae47d32adb478609de0f6220b4152123bf2d416ea41eb654d3980c`; `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.repository.CredentialRepositoryImplTest.xml` SHA-256 `65589c22b3aef3ee17fd52f0d427e4855fa20bd2a784adaadf15e715a51369e`; source and test paths are listed in `source-inventory.json`.

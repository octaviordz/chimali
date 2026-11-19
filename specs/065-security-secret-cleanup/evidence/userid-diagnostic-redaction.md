# UserId diagnostic string redaction

- **Finding:** SEC065-CORE-030.
- **Requirement:** FR-009/010; NFR-SEC-041; TR-AUTH-031; T005/T033/T035.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** Test-first `:core:domain:testAndroidHostTest --tests com.chimali.core.domain.valueobject.UserIdPrivacyTest`, followed by `:core:domain:allTests :core:domain:ktlintCheck`.
- **Environment:** Windows host; Gradle 9.6.0; JDK 25; synthetic account identifier marker.
- **Observed:** `UserId.toString()` initially included the synthetic identifier; the regression failed before correction. The value class now returns fixed `UserId([redacted])`, while its explicit `.value` API remains available. The complete domain host suite passed 92 tests across 16 reports with zero failures/errors/skips; KtLint passed. The complete FIDO host suite also passes 1,261 tests across 167 reports with zero failures/errors/skips; FIDO KtLint passes.
- **Date:** 2026-11-16.
- **Limitations:** Explicit `.value` access remains necessary for protocol/storage and retains immutable String semantics; consumer scoping and provider/runtime heap retention remain open. Kotlin/Native execution and configured diagnostic destination capture are not established.
- **Status:** Passed for generic diagnostic-string redaction only.
- **Artifacts:** `core/domain/build/test-results/testAndroidHostTest/TEST-com.chimali.core.domain.valueobject.UserIdPrivacyTest.xml` SHA-256 `f257ccc8edc48363ccd8d2f3e88eaabbf2f26d286912ebdc7d7a3924e109f2c6`; source and test are mapped in `source-inventory.json`.

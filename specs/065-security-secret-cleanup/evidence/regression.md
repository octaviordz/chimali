# Regression and Local CI Evidence

## Pre-removal dirty worktree execution (historical)

- **Requirement:** FR-008/009; FR-010 through FR-014; T041
- **Scope:** Actual-change focused regressions plus repository local validation. This is a dirty exploratory worktree, not a frozen product candidate.
- **Revision:** Commit `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57` plus current uncommitted changes; exact file hashes are in `source-inventory.json`.
- **Named procedure:** `SEC065-REGRESSION` and repository `tools/local-ci.ps1 -NoFix`.
- **Environment:** Windows host, repository Gradle wrapper, connected API 35 emulator `emulator-5554`; local CI compile/test work ran on host. No hosted workflow was invoked.
- **Expected and observed result:** `:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.bluetooth.HidReportParserTest'` passed 19 tests. A focused rerun of `Ctap2GetAssertionMalformedSelectorTest` and `RequiredRegistrationVerificationIntegrationTest` passed after correcting mocked HID encoders to return independent packet copies. Full local CI passed KtLint, Detekt, Release lint, compile-all, unit tests, and Android Host Tests; the FIDO2 host suite completed 1,233 tests with zero failures. The script printed its explicit notice that hosted integration remains Unverified.
- **Date:** 2026-11-14
- **Limitations:** Host and local checks only; no physical-device clipboard cohort, full HPROF content/root inspection, iOS runtime, hosted integration or independent review is established. Test-report XML is retained under module `build/test-results` directories; these are generated, reproducible local outputs, not controlled raw security captures.
- **Status:** Passed (current-tree local implementation/regression scope)
- **Artifact link:** `feature/fido2/build/reports/tests/testAndroidHostTest/index.html`; `build/reports/problems/problems-report.html`; command log was observed in the current execution.

## Intermediate post-removal run (historical)

- **Requirement:** FR-001/002/008/009; T005/T031/T033/T041
- **Scope:** FIDO source rebuild and prescribed local CI after removing the unreachable legacy credential crypto services and their two direct host suites.
- **Revision:** Commit `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57` plus current uncommitted changes; current source inventory contains 632 files and 71 usage records.
- **Named procedure:** `:feature:fido2:testAndroidHostTest`; `.\tools\local-ci.ps1 -NoFix`.
- **Environment:** Windows host; Gradle 9.6.0; JDK 25 launcher. No hosted workflow was invoked.
- **Expected and observed result:** FIDO Android main/host test compilation and KSP regeneration succeeded; generated Koin Kotlin/Java source search found no legacy service classes or bindings. All 1,218 remaining FIDO host tests across 163 XML reports passed with 0 failures, errors, or skips. Local CI passed KtLint, Detekt, Release lint, Compile All, Unit Tests, and KMP Android Host Tests; the script repeated that hosted integration remains Unverified.
- **Date:** 2026-11-14
- **Limitations:** Dirty exploratory tree only; not candidate-bound. Physical clipboard cohorts, full HPROF content/root inspection, iOS runtime, hosted integration, configured destination matrix and independent review remain open. Two legacy direct-source suites (15 tests total) were removed with their classes and are not included in the current suite count.
- **Status:** Passed (current-tree local implementation/regression scope)
- **Artifact link:** `feature/fido2/build/reports/tests/testAndroidHostTest/index.html`; `feature/fido2/build/generated/ksp/android/androidMain/kotlin/org/koin/ksp/generated/`; current source inventory.

## Failure diagnosis retained

The first full host run had six failures in the two selector/registration suites because mock HID encoders returned an alias to the input response payload. Production immediately erases that payload after framing. The mock now returns `payload.copyOf()`, matching the encoder’s output ownership contract. Both suites passed focused, and the subsequent full Android Host Test stage passed.


## Current-tree regression after SEC065-CTAP-007

- **Requirement:** NFR-SEC-041; TR-AUTH-031; FR-003/004/008/009/010; T009/T012/T041
- **Scope:** Raw-CBOR extension input retention and terminal cleanup, plus repository local CI after the decoder allowlist correction.
- **Revision:** Commit `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57` plus current uncommitted changes; current source inventory has 632 files and 73 usages.
- **Named procedure:** `:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.data.crypto.CborCodecTest'`; `.\tools\local-ci.ps1 -NoFix`.
- **Environment:** Windows host; Gradle 9.6.0; JDK 25 launcher. No hosted workflow was invoked.
- **Expected and observed result:** The raw-CBOR salt owner regression fails before the fix because `saltEnc` is discarded, then passes after adding the consumed protocol key to the decoder allowlist. The full 18-case `CborCodecTest` class passes. Local CI passes KtLint, Detekt, Release lint, Compile All, Unit Tests and KMP Android Host Tests; the current FIDO host suite has 1,220 tests across 163 XML reports, with 0 failures, errors or skips. The script explicitly leaves hosted integration Unverified.
- **Date:** 2026-11-14
- **Limitations:** Dirty exploratory tree only; not candidate-bound. Full GetAssertion lifecycle/replay, provider copies, HPROF roots, physical clipboard cohorts, iOS runtime, hosted integration and independent review remain open.
- **Status:** Passed (current-tree local implementation/regression scope)
- **Artifact link:** `evidence/ctap-extension-salt-cleanup.md`; `feature/fido2/build/reports/tests/testAndroidHostTest/index.html`; `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.crypto.CborCodecTest.xml`.

## Consumer handoff regression and refreshed local CI

- **Requirement:** NFR-SEC-041; TR-AUTH-031; FR-003/004/009/010; T009/T041
- **Scope:** Decode a synthetic nested `extensions.hmac-secret.saltEnc` CBOR request; pass the resulting owner through `HmacSecretProcessor`; verify borrower readability, root-copy erasure and outer request cleanup; rerun current repository local CI.
- **Revision:** Commit `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57` plus current uncommitted worktree; not frozen.
- **Named procedure:** `HmacSecretProcessorZeroingTest.sensitive CBOR salt owner remains readable through HMAC processing then request cleanup erases it`; `:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.data.crypto.HmacSecretProcessorZeroingTest'`; `tools/local-ci.ps1 -NoFix`.
- **Environment:** Windows 11 host; Gradle 9.6.0; JDK 25 launcher; no hosted workflow invoked.
- **Expected and observed result:** All 5 `HmacSecretProcessorZeroingTest` cases passed. The same decoded salt owner remained readable through the actual processor call and was zeroed by request cleanup; the processor-owned root copy was zeroed. Refreshed local CI passed KtLint, Detekt, Release lint, Compile All, Unit Tests and KMP Android Host Tests. Current FIDO host XML totals: 163 reports, 1,220 tests, 0 failures, 0 errors and 0 skips.
- **Date:** 2026-11-14
- **Limitations:** Host tests and local build checks only; not frozen-candidate evidence. The production `handleRaw` device path, provider/native copies, HPROF root attribution, physical clipboard matrix, iOS runtime, hosted integration and independent review remain unverified.
- **Status:** Passed (focused consumer ownership and current-tree local regression)
- **Artifact link:** `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.crypto.HmacSecretProcessorZeroingTest.xml`; `feature/fido2/build/reports/tests/testAndroidHostTest/index.html`; observed `tools/local-ci.ps1 -NoFix` command result.

## Clipboard interruption outcome and final local checks

- **Requirement:** BRD NFR-SEC-050; TR-CLIP-010; FR-006/007; T019/T021/T023/T041
- **Scope:** Record cancellation of a scheduled Android cleanup while the process remains available, ensure the Vault caller gives truthful unconfirmed feedback, and rerun the repository local checks.
- **Revision:** Commit `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57` plus current dirty worktree; not frozen.
- **Named procedure:** `:core:common:testAndroidHostTest --tests 'com.chimali.core.clipboard.AndroidClipboardManagerServiceTest'`; `:feature:vault:testDebugUnitTest --tests 'com.chimali.feature.vault.internal.VaultClipboardOwnershipTest'`; `tools/local-ci.ps1 -NoFix`.
- **Environment:** Windows host; Gradle 9.6.0; JDK 25 launcher; mocked clipboard metadata and virtual scheduler; no hosted workflow invoked.
- **Expected and observed result:** Clipboard service host suite passed 28 cases; Vault clipboard ownership suite passed with the new interrupted-outcome case. Full local CI passed KtLint, Detekt, Release lint, Compile All, Unit Tests and KMP Android Host Tests. An interrupted scheduled job records `Interrupted`, submits no clear request in the regression, and the caller says cleanup could not be confirmed.
- **Date:** 2026-11-14
- **Limitations:** Host/current-tree evidence only. Process death cannot record terminal status; physical cohorts, preview/TalkBack behavior, heap review, hosted integration and independent security review remain open.
- **Status:** Passed (focused host and current-tree local regression)
- **Artifact link:** `core/common/build/test-results/testAndroidHostTest/TEST-com.chimali.core.clipboard.AndroidClipboardManagerServiceTest.xml`; `feature/vault/build/test-results/testDebugUnitTest/TEST-com.chimali.feature.vault.internal.VaultClipboardOwnershipTest.xml`; `evidence/clipboard-host.md`; local `tools/local-ci.ps1 -NoFix` result.

## Database metadata output cleanup follow-up

- **Requirement:** FR-003/004/010; SEC065-DB-007/008/009.
- **Scope:** Encrypted metadata buffers returned during PasskeyCredentialDao writes, RelyingPartyDao writes and searchable metadata migration.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** `:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.data.database.CredentialAuditSchemaTest' --tests 'com.chimali.fido2.data.database.SearchableMetadataMigrationTest'`.
- **Environment:** Windows host; Gradle 9.6.0; JDK 25 launcher; SQLDelight JDBC in-memory SQLite.
- **Expected and observed result:** Credential DAO audit regression passed (1 case); searchable migration suite passed (9 cases); 0 failures, errors or skipped. Each test checks that app-owned output arrays are zero after synchronous SQLDelight writes and stored rows retain independent expected bytes. The new migration assertions failed before correction for both encrypted metadata arrays.
- **Date:** 2026-11-14.
- **Limitations:** Host/JDBC evidence only. Production encrypted-driver/native copies, provider retention, persisted file behavior and heap roots remain open.
- **Status:** Passed (focused host regressions only).
- **Artifact link:** `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.database.CredentialAuditSchemaTest.xml`; `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.database.SearchableMetadataMigrationTest.xml`; `evidence/db-dao-metadata-cleanup.md`; `assessment.md#SEC065-DB-009`.

## Encrypted metadata API ownership contract

- **Requirement:** NFR-SEC-041; TR-AUTH-031; FR-003/004/010; SEC065-CRYPTO-001.
- **Scope:** Shared `EncryptedMetadataService` contract and `AesGcmEncryptedMetadataService` caller-visible array ownership.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** `:core:security:testAndroidHostTest --tests 'com.chimali.core.security.impl.AesGcmEncryptedMetadataServiceTest'`.
- **Environment:** Windows host; Gradle 9.6.0; JDK 25 launcher; JCA AES/GCM provider.
- **Expected and observed result:** All 12 tests passed with zero failures/errors/skips. New ownership case confirms plaintext/AAD/ciphertext inputs remain unchanged during calls; ciphertext and decrypted plaintext are independent arrays that can be erased separately.
- **Date:** 2026-11-14.
- **Limitations:** Host test and source-contract review only. JCA/provider internal copies, native memory and heap-root retention remain unverified.
- **Status:** Passed (scoped API/adapter ownership behavior).
- **Artifact link:** `core/security/build/test-results/testAndroidHostTest/TEST-com.chimali.core.security.impl.AesGcmEncryptedMetadataServiceTest.xml`; `assessment.md#SEC065-CRYPTO-001`; `core/security/src/commonMain/kotlin/com/chimali/core/security/api/EncryptedMetadataService.kt`.

## Unused memory utility removal

- **Requirement:** FR-002/003/009; T005/T033; SEC065-CRYPTO-002.
- **Scope:** `MemoryUtils` production reachability, misleading immutable-String cleanup helpers, and remaining CBOR protocol tests in `CryptoUtilsTest`.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** Repository-wide source reference search followed by `:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.data.crypto.CryptoUtilsTest'`.
- **Environment:** Windows host; Gradle 9.6.0; JDK 25 launcher; KSP Android main and host-test compilation.
- **Expected and observed result:** Search found no production callers; only `CryptoUtilsTest` directly used the class. Removed the unused `@Single` utility and its memory-helper cases while retaining the CBOR shape checks. All 4 remaining `CryptoUtilsTest` cases passed; Gradle regenerated Android main KSP outputs and compiled the FIDO host-test source set.
- **Date:** 2026-11-14.
- **Limitations:** Source-reachability and host-test evidence only. Historical performance/conformance records may describe the former file; this does not assess other open FIDO memory owners or prove heap absence.
- **Status:** Passed (bounded dead-code removal).
- **Artifact link:** `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.crypto.CryptoUtilsTest.xml`; `assessment.md#SEC065-CRYPTO-002`; `docs/hdk-conformance-and-enhancements-corrections.md`.

## Unused authenticator-data helper removal

- **Requirement:** FR-002/003/008/009; T005/T033; SEC065-CRYPTO-003.
- **Scope:** Production reachability of `AuthenticatorDataBuilder`, its former direct privacy test, and the actual CTAP response test path.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** Repository-wide source reference review followed by `:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.data.crypto.CryptoUtilsTest' --tests 'com.chimali.fido2.ctap2.Ctap2ProtocolTest'`.
- **Environment:** Windows host; Gradle 9.6.0; JDK 25 launcher; KSP Android main and host-test compilation.
- **Expected and observed result:** No production FIDO call sites existed; actual CTAP handlers and `Ctap2ResponseBuilder` use separate protocol paths. Removed the unused `@Single` builder and its direct privacy test. The retained CBOR utility suite passed 4 cases and CTAP protocol suite passed 9, with zero failures/errors/skips.
- **Date:** 2026-11-14.
- **Limitations:** Source-reachability and host tests only. The prior T051 capture is historical evidence for the then-present source and does not describe current product behavior; configured runtime destination evidence for other paths remains open.
- **Status:** Passed (bounded dead-code removal and adjacent protocol regression).
- **Artifact link:** `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.crypto.CryptoUtilsTest.xml`; `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.ctap2.Ctap2ProtocolTest.xml`; `assessment.md#SEC065-CRYPTO-003`.

## Registration authenticator-data and signing-input cleanup

- **Requirement:** NFR-SEC-041; TR-AUTH-031; FR-003/004/010; T070; SEC065-CRYPTO-004.
- **Scope:** RP-ID hash conversion, packed self-attestation authenticator-data assembly, signer input lifecycle, and success/failure output transfer.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** `:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.domain.usecase.RegisterCredentialUseCaseTest'`.
- **Environment:** Windows host; Gradle 9.6.0; JDK 25 launcher; Android main and host-test compilation.
- **Expected and observed result:** All 40 cases pass. Unicode RP hash remains correct in the signed bytes, and the mock snapshots the signing input before return while the test confirms the borrowed signing-input array is all zero afterward.
- **Date:** 2026-11-14.
- **Limitations:** Application-owned buffers only. Crypto provider copies, all heap roots, and post-terminal heap state remain unverified under T015.
- **Status:** Passed (focused host regression).
- **Artifact link:** `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.domain.usecase.RegisterCredentialUseCaseTest.xml`; `assessment.md#SEC065-CRYPTO-004`.

The current inventory maps SEC065-CRYPTO-004 to the active registration attestation owner and its signer-input regression; this mapping does not close T005 or the broader crypto/heap boundary.

## Database integrity diagnostic class-name removal

- **Requirement:** FR-008/009; T033/T035; SEC065-LOG-038.
- **Scope:** Raw Kermit output from all `EncryptedDriverFactory.verifyIntegrity` exception catches.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** `:core:database:testDebugUnitTest --tests 'com.chimali.core.database.EncryptedDriverFactoryTest'`.
- **Environment:** Windows host; Gradle 9.6.0; JDK 25; Android debug unit-test runtime.
- **Expected and observed result:** The unique runtime class marker reached the captured raw Kermit output before correction. After replacing dynamic class names with a fixed failure label, all 9 factory tests pass and the integrity check continues to fail closed.
- **Date:** 2026-11-14.
- **Limitations:** Host source-emission evidence only; configured Android destinations and the broader diagnostic inventory remain open.
- **Status:** Passed (focused host regression).
- **Artifact link:** `core/database/build/test-results/testDebugUnitTest/TEST-com.chimali.core.database.EncryptedDriverFactoryTest.xml`; `assessment.md#SEC065-LOG-038`.

## Onboarding storage error mapping

- **Requirement:** FR-008/009; T072; SEC065-ERR-008.
- **Scope:** `IOException` handling in `OnboardingViewModel.finishOnboarding`.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** `:feature:onboarding:testDebugUnitTest`.
- **Environment:** Windows host; Gradle 9.6.0; JDK 25; Android debug unit-test runtime.
- **Expected and observed result:** A unique exception marker and synthetic private path reached `OnboardingUiState.errorMessage` before correction. The ViewModel now uses a fixed failure message; all 7 tests pass, preserving selection state and resetting `isSaving`.
- **Date:** 2026-11-14.
- **Limitations:** Focused user-facing state evidence only; other exception-to-UI owners and wider audit gates remain open.
- **Status:** Passed (focused host regression).
- **Artifact link:** `feature/onboarding/build/test-results/testDebugUnitTest/TEST-com.chimali.feature.onboarding.presentation.viewmodel.OnboardingViewModelTest.xml`; `assessment.md#SEC065-ERR-008`.

## Vault DomainError-to-UI mapping

- **Requirement:** FR-008/009; T073; SEC065-ERR-009.
- **Scope:** All ten `DomainError.message` mappings in `VaultViewModel` UI state.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** `:feature:vault:testDebugUnitTest`.
- **Environment:** Windows host; Gradle 9.6.0; JDK 25; Android debug unit-test runtime.
- **Expected and observed result:** A unique crypto error marker and synthetic private path reached the save error state before correction. All ten branches now store fixed user-facing wording; all 89 unit tests pass, with state and mutation behavior preserved.
- **Date:** 2026-11-14.
- **Limitations:** Host UI-state evidence only; configured destinations and other error-to-UI owners remain open.
- **Status:** Passed (focused regression and full Vault unit suite).
- **Artifact link:** `feature/vault/build/test-results/testDebugUnitTest/TEST-com.chimali.feature.vault.internal.VaultViewModelTest.xml`; `assessment.md#SEC065-ERR-009`.

## MakeCredential deferred terminal cleanup

- **Requirement:** FR-003/010; T009/T074; SEC065-CTAP-008.
- **Scope:** Exceptional exit after publishing a registration event to `Fido2UiEventBus` and before awaiting the UI result.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** `:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.ctap2.Ctap2MakeCredentialHandlerValidationTest'`.
- **Environment:** Windows host; Gradle 9.6.0; JDK 25; Android host-test runtime, real CBOR codec and event bus.
- **Expected and observed result:** A throwing synthetic keepalive callback left the registration request cached before correction. The wait block now cancels an active deferred in `finally`; event-bus completion removes the pending request, and outer request cleanup erases decoded text owners. All 23 validation cases pass.
- **Date:** 2026-11-14.
- **Limitations:** Host lifecycle behavior only; process interruption, device concurrency and heap-root evidence remain open.
- **Status:** Passed (focused host regression).
- **Artifact link:** `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.ctap2.Ctap2MakeCredentialHandlerValidationTest.xml`; `assessment.md#SEC065-CTAP-008`.

## GetAssertion deferred terminal cleanup

- **Requirement:** FR-003/010; T009/T075; SEC065-CTAP-009.
- **Scope:** Exceptional exit after publishing an authentication event and before a UI result completes.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** `:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.ctap2.Ctap2GetAssertionHandlerTest'`.
- **Environment:** Windows host; Gradle 9.6.0; JDK 25; Android host-test runtime, real CBOR codec and event bus.
- **Expected and observed result:** A throwing synthetic keepalive callback left the authentication request cached before correction. Dispatch, callback and wait now execute inside a cleanup `try/finally` that cancels an active deferred; event-bus completion removes the pending request, and outer request cleanup erases decoded text owners. All GetAssertion handler cases pass.
- **Date:** 2026-11-14.
- **Limitations:** Host lifecycle behavior only; process interruption, device concurrency and heap-root evidence remain open.
- **Status:** Passed (focused host regression).
- **Artifact link:** `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.ctap2.Ctap2GetAssertionHandlerTest.xml`; `assessment.md#SEC065-CTAP-009`.

## Android clipboard label privacy

- **Requirement:** FR-006/009; T017/T076; SEC065-CLIP-ANDROID-002.
- **Scope:** Caller-controlled label metadata at the Android sensitive clipboard publication boundary.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** `:core:common:testAndroidHostTest --tests 'com.chimali.core.clipboard.AndroidClipboardManagerServiceTest'`.
- **Environment:** Windows host; Gradle 9.6.0; JDK 25; Android host-test runtime with mocked clipboard and synthetic identifying label.
- **Expected and observed result:** The synthetic label was forwarded to `ClipData.newPlainText` before correction. The adapter now uses the fixed generic `Password` label, and the regression plus all 29 service tests pass.
- **Date:** 2026-11-14.
- **Limitations:** Host publication contract only; OEM preview/UI behavior and physical Android cohorts remain open.
- **Status:** Passed (focused host regression).
- **Artifact link:** `core/common/build/test-results/testAndroidHostTest/TEST-com.chimali.core.clipboard.AndroidClipboardManagerServiceTest.xml`; `assessment.md#SEC065-CLIP-ANDROID-002`.

## Vault event-store partial-batch cleanup

- **Requirement:** FR-003/004/010; T006/T007/T077; SEC065-DB-010.
- **Scope:** Application-owned mutable `VaultEvent` title and payload arrays decoded before a later row in `getEvents` or `getEventsFrom` fails.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** `:core:data:testDebugUnitTest --tests com.chimali.core.data.eventsourcing.EventStorePartialDecodeOwnershipTest --tests com.chimali.core.data.eventsourcing.EventStoreRepositoryImplTest`.
- **Environment:** Windows host; Gradle 9.6.0; JDK 25; Android library local JVM unit-test runtime; synthetic in-memory mutable owners.
- **Expected and observed result:** The regression failed when the batch cleanup action was removed: the first decoded title/payload owners remained populated after the second row failed. With cleanup restored, both buffers are zeroed; the focused regression and all five existing repository tests pass.
- **Date:** 2026-11-14.
- **Limitations:** Does not establish SQLDelight/native driver copies, immutable UTF-8 String erasure, cancellation at every provider boundary, Android encrypted-driver behavior or heap-root absence. T005/T012/T015 remain open.
- **Status:** Passed (focused host regression; source-level application-owned arrays).
- **Artifact link:** `core/data/build/test-results/testDebugUnitTest/TEST-com.chimali.core.data.eventsourcing.EventStorePartialDecodeOwnershipTest.xml`; `core/data/build/test-results/testDebugUnitTest/TEST-com.chimali.core.data.eventsourcing.EventStoreRepositoryImplTest.xml`; `assessment.md#SEC065-DB-010`.

## Current full FIDO host suite after DAO buffer assertions

- **Requirement:** FR-003/004/010; T005/T012/T041.
- **Scope:** Complete `:feature:fido2:testAndroidHostTest` host suite and module KtLint after adding retained-array assertions for credential DAO insert/update paths.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck`.
- **Environment:** Windows JVM host; Gradle 9.6.0; JDK 25.
- **Expected and observed result:** 1,247 tests across 166 XML reports; zero failures, errors or skips; FIDO KtLint passes.
- **Date:** 2026-11-15.
- **Limitations:** Host-only suite; Android runtime, generated SQLDelight and provider copies, native storage, heap roots, frozen-candidate review and independent review remain open.
- **Status:** Passed (current exploratory full host suite).
- **Artifact link:** `feature/fido2/build/reports/tests/testAndroidHostTest/index.html`; `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.dao.PasskeyCredentialDaoTest$InsertUpdateOperations.xml`; `feature/fido2/build/reports/ktlint/`; `evidence/evidence-validation.md`.

## Paired-device ViewModel replay cleanup

- **Requirement:** FR-003/010; NFR-SEC-041; T005/T006/T015/T027/T041; SEC065-FIDO-005.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** Focused `PairedDevicesViewModelOwnershipTest`; full `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck`.
- **Environment:** Windows JVM host; Gradle 9.6.0; JDK 25; synthetic data.
- **Observed:** Focused 2 tests pass. Full suite: 1,250 tests / 167 XML reports; zero failures, errors or skips; KtLint passes.
- **Date:** 2026-11-15.
- **Limitations:** No device run; Android UI collectors, database/native copies, immutable String lifetime, backup/deleted-page behavior, heap roots and candidate freeze remain open.
- **Status:** Passed for source/host scope only.
- **Artifacts:** `evidence/paired-device-viewmodel-replay-owner-cleanup.md`; focused XML report; full host HTML report; `evidence/index.md`.

## Paired-device ViewModel replay cleanup

- **Requirement:** FR-003/010; NFR-SEC-041; T005/T006/T015/T027/T041; SEC065-FIDO-005.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** Focused `PairedDevicesViewModelOwnershipTest`; full `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck`.
- **Environment:** Windows JVM host; Gradle 9.6.0; JDK 25; synthetic data.
- **Observed:** Focused 2 tests pass. Full suite: 1,250 tests / 167 XML reports; zero failures, errors or skips; KtLint passes.
- **Date:** 2026-11-15.
- **Limitations:** No device run; Android UI collectors, database/native copies, immutable String lifetime, backup/deleted-page behavior, heap roots and candidate freeze remain open.
- **Status:** Passed for source/host scope only.
- **Artifacts:** `evidence/paired-device-viewmodel-replay-owner-cleanup.md`; focused XML report; full host HTML report; `evidence/index.md`.

## Paired-device ViewModel replay cleanup

- **Requirement:** FR-003/010; NFR-SEC-041; T005/T006/T015/T027/T041; SEC065-FIDO-005.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** Focused `PairedDevicesViewModelOwnershipTest`; full `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck`.
- **Environment:** Windows JVM host; Gradle 9.6.0; JDK 25; synthetic data.
- **Observed:** Focused 2 tests pass. Full suite: 1,250 tests / 167 XML reports; zero failures, errors or skips; KtLint passes.
- **Date:** 2026-11-15.
- **Limitations:** No device run; Android UI collectors, database/native copies, immutable String lifetime, backup/deleted-page behavior, heap roots and candidate freeze remain open.
- **Status:** Passed for source/host scope only.
- **Artifacts:** `evidence/paired-device-viewmodel-replay-owner-cleanup.md`; focused XML report; full host HTML report; `evidence/index.md`.

## Vault payload copy ownership

- **Requirement:** FR-VAULT-026; FR-003/010; NFR-SEC-041; T005/T006/T012/T015/T027/T041; SEC065-VAULT-005.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** Test-first `PayloadCopyOwnershipTest`; full `:feature:vault:testDebugUnitTest :feature:vault:ktlintCheck`.
- **Environment:** Windows JVM host; Gradle 9.6.0; JDK 25; synthetic data.
- **Observed:** Two new copy-isolation regressions fail before the fix and pass afterward. Full suite: 93 tests / 21 reports; zero failures, errors or skips; KtLint passes.
- **Date:** 2026-11-15.
- **Limitations:** No Android device run; Compose/IME snapshots, provider/native copies, heap roots and candidate freeze remain open.
- **Status:** Passed for source/host copy isolation only.
- **Artifacts:** `evidence/vault-payload-copy-owner-isolation.md`; focused XML; full Vault HTML report; `evidence/index.md`.

$1015.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** Focused `HidReportParserTest`, `CtapHidRequestOwnerTest`, `CtapHidCommandDispatcherPrivacyTest`, `BluetoothHidTransportSessionGenerationTest`; full `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck`.
- **Environment:** Windows JVM host; Gradle 9.6.0; JDK 25; synthetic transport fixtures.
- **Observed:** Focused classes pass 28 total cases (19+4+4+1). Full suite: 1,250 tests / 167 reports; zero failures, errors or skips; KtLint passes.
- **Date:** 2026-11-15.
- **Limitations:** No Android/physical Bluetooth run; already-framed HID packet arrays, platform copies, OS retention, heap roots and candidate freeze remain open.
- **Status:** Passed for source/host owner scope only.
- **Artifacts:** `evidence/ctaphid-channel-owner-cleanup.md`; focused XML reports; full FIDO HTML report; `evidence/index.md`.

## Sensitive text failure cleanup and complete FIDO rerun

- **Requirement:** FR-004/010; NFR-SEC-041; TR-AUTH-031; T012/T015; SEC065-CTAP-010.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; not frozen.
- **Named procedure:** Test-first changing-length regression in `SensitiveCredentialTextTest`, followed by `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck`.
- **Environment:** Windows JVM host; Gradle 9.6.0; JDK 25; synthetic mutable character source and destination.
- **Observed:** The regression failed before correction because source-length validation bypassed the destination cleanup handler. Validation now runs inside the guarded block. All five focused cases pass; the complete FIDO suite passes 1,251 tests across 167 reports, zero failures/errors/skips, and KtLint passes.
- **Date:** 2026-11-15.
- **Limitations:** Host/source evidence only. Other conversion boundaries, immutable values, provider/runtime copies, heap roots and frozen candidate remain open.
- **Status:** Passed for the bounded mutable-copy cleanup behavior.
- **Artifacts:** `evidence/ctap-sensitive-text-length-failure-cleanup.md`; focused XML; full FIDO XML/HTML reports; FIDO KtLint report.
## SEC065-CTAP-013 — Remove unused immutable UserId conversion

- **Finding:** An unused public `SensitiveCredentialText.toUserId()` projected owner contents into an immutable String-backed value without any production caller.
- **Test-first evidence:** Added an API-absence assertion that detects Kotlin-mangled inline-return method names. It failed before source correction. Removed the method/import and changed the one host-test fixture to construct its synthetic UserId explicitly at its test boundary.
- **Commands:** `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.domain.model.SensitiveCredentialTextTest --tests com.chimali.fido2.data.repository.CredentialRepositoryImplTest`; `:feature:fido2:ktlintCheck`; full `:feature:fido2:testAndroidHostTest`.
- **Results:** Focused classes passed 6/6 and 9/9 with KtLint passing. Full FIDO host suite passed 1,261 tests across 167 XML reports, zero failures/errors/skips.
- **Limits:** Required RpId immutable compatibility projection, provider/native/runtime copies, remaining ownership inventory and heap roots are open. This exploratory run is not frozen-candidate evidence.
- **Evidence:** `evidence/sensitive-text-unused-userid-conversion-removal.md`; test results linked in `evidence/index.md`.

## SEC065-CORE-030 — UserId generic diagnostic redaction

- **Requirement:** FR-009/010; NFR-SEC-041; TR-AUTH-031; T005/T033/T035.
- **Test-first behavior:** A new synthetic-marker regression failed before adding the fixed `UserId([redacted])` override; explicit `.value` access remains unchanged.
- **Verification:** `:core:domain:allTests :core:domain:ktlintCheck`; 92 tests across 16 reports, zero failures/errors/skips. Full FIDO host suite passes 1,261 tests across 167 reports, zero failures/errors/skips; FIDO KtLint passes.
- **Limits:** Explicit value consumers, configured diagnostic capture, Kotlin/Native behavior, provider/runtime String copies and heap roots remain open.
- **Evidence:** `evidence/userid-diagnostic-redaction.md`; `core/domain/build/test-results/testAndroidHostTest/TEST-com.chimali.core.domain.valueobject.UserIdPrivacyTest.xml`.

## SEC065-ERR-016 — Vault aggregate retry error cause discard

- **Requirement:** FR-009/010; NFR-SEC-041; TR-AUTH-031; T005/T033/T035.
- **Test-first behavior:** The retry-exhaustion regression used a synthetic database-error marker and failed before the raw exception was discarded.
- **Verification:** `:core:data:testDebugUnitTest --tests com.chimali.core.data.eventsourcing.VaultAggregateServiceImplTest :core:data:ktlintCheck`; 14 tests passed, zero failures/errors/skips; KtLint passed.
- **Limits:** Covers only append retry exhaustion. Earlier state-read errors, other exception translations, configured destinations, provider/native copies and heap roots remain open. Exploratory worktree only.
- **Evidence:** `evidence/vault-aggregate-retry-error-cause-discard.md`; `core/data/build/test-results/testDebugUnitTest/TEST-com.chimali.core.data.eventsourcing.VaultAggregateServiceImplTest.xml`.

## SEC065-ERR-017 — Vault event repository cause discard

- **Requirement:** FR-009/010; NFR-SEC-041; TR-AUTH-031; T005/T033/T035.
- **Test-first behavior:** A synthetic parser marker injected through the event read path caused the new fixed-message/no-cause assertion to fail before the adapter correction.
- **Verification:** `:core:data:testDebugUnitTest --tests com.chimali.core.data.eventsourcing.EventStoreRepositoryImplTest :core:data:ktlintCheck`; eight focused tests pass after correction. Full `core:data` results are recorded after the suite refresh.
- **Limits:** Snapshot adapter, unchecked provider failures, configured destinations, SQLDelight/native copies and heap roots remain open. Exploratory worktree only.
- **Evidence:** `evidence/vault-event-repository-cause-discard.md`; `core/data/build/test-results/testDebugUnitTest/TEST-com.chimali.core.data.eventsourcing.EventStoreRepositoryImplTest.xml`.

## SEC065-ERR-018 — Vault snapshot repository cause discard

- **Requirement:** FR-009/010; NFR-SEC-041; TR-AUTH-031; T005/T033/T035.
- **Test-first behavior:** Synthetic encryption/decode markers made the new fixed-message/cause-free result assertions fail before correction.
- **Verification:** `:core:data:testDebugUnitTest :core:data:ktlintCheck`; 48 tests across 9 XML reports pass, zero failures/errors/skips. `SnapshotRepositoryImplTest` passes six cases; snapshot buffer-owner tests pass with sanitized failures and erasure assertions.
- **Limits:** Source/host only. JDBC/native copies, configured diagnostic destinations, heap roots and frozen candidate remain open.
- **Evidence:** `evidence/vault-snapshot-repository-cause-discard.md`; snapshot and buffer-owner XML reports.

## Full local CI after exception-sanitization lint corrections

- **Verification:** `pwsh -NoProfile -File tools/local-ci.ps1 -NoFix` passed KtLint, Detekt, release lint, compile-all, unit tests, and configured KMP Android host tests. The host suites produced 1,475 passing tests across 205 XML reports with zero failures/errors/skips.
- **Code quality correction:** added file-scoped, rationale-bearing suppressions for intentional raw-cause discard in Vault event/snapshot repositories; refactored Passkey aggregate cancellation-result handling through a nullable helper, preserving cancellation propagation while satisfying Detekt; localized the existing large aggregate test class suppression with its shared-fixture rationale.
- **Evidence:** the focused aggregate regression report is linked from `evidence/index.md`. This is local host evidence for a dirty exploratory tree; it does not establish hosted integration, physical cohorts, heap/provider behavior, candidate freeze or independent release review.

## SEC065-ERR-021 — Credential ID decoder cause discard

- **Requirement:** FR-009/010; NFR-SEC-041; TR-AUTH-031; T005/T012/T033/T035.
- **Test-first behavior:** `CredentialIdTest.malformed encoded credential identifier does not retain decoder failure` failed before correction because malformed Base64URL decoding retained the lower-level exception as the public error cause. The decoder now exposes fixed validation text without the cause.
- **Verification:** `:core:domain:allTests :core:domain:ktlintCheck` passes 93 common-domain host tests across 16 XML reports with zero failures/errors/skips. The full `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck` run passes 1,265 tests across 167 XML reports with zero failures/errors/skips.
- **Limits:** Host/source only. Downstream diagnostic destinations, native/provider behavior, immutable encoded ID lifetime and heap retention remain open.
- **Evidence:** `evidence/credential-id-decoder-cause-discard.md`; `core/domain/build/test-results/testAndroidHostTest/TEST-com.chimali.core.domain.valueobject.CredentialIdTest.xml`; FIDO suite report set linked in the evidence index.

## SEC065-ERR-022 — Registration challenge decoder cause discard

- **Requirement:** FR-009/010; NFR-SEC-041; TR-AUTH-031; T005/T012/T033/T035.
- **Test-first behavior:** `MakeCredentialOptionsTest.malformed base64 challenge does not retain decoder failure` failed before correction because malformed Base64 retained the lower-level decoder exception as its cause. The decoder now exposes only fixed validation text.
- **Verification:** Focused FIDO host test and KtLint passed. Full FIDO host suite passes 1,266 tests across 167 XML reports with zero failures/errors/skips; KtLint and Detekt pass.
- **Limits:** Host/source only. Native/provider behavior, configured diagnostic destinations, immutable challenge Strings and heap retention remain open.
- **Evidence:** `evidence/make-credential-challenge-cause-discard.md`; `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.domain.model.MakeCredentialOptionsTest.xml`; full suite report set.

## SEC065-ERR-023 — Relying Party repository DAO failure cause discard

- **Requirement:** FR-009/010; NFR-SEC-041; TR-AUTH-031; T005/T033/T035.
- **Test-first behavior:** A synthetic DAO marker regression for save, delete and count-update failed before correction because `Result.failure` retained the raw exception message. Ordinary `Exception` failures now return fixed cause-free error text; cancellation still propagates and fatal `Error`s are not swallowed.
- **Verification:** The focused `RelyingPartyRepositoryFlowOwnershipTest` passes all six cases. Full FIDO host suite passes 1,267 tests across 167 XML reports with zero failures/errors/skips; KtLint and Detekt pass.
- **Limits:** Host/source only. Provider internals, production caller reachability, configured diagnostic destinations and heap behavior remain open.
- **Evidence:** `evidence/relying-party-repository-error-cause-discard.md`; `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.repository.RelyingPartyRepositoryFlowOwnershipTest.xml`; full suite report set.

## SEC065-ERR-024 — Passkey storage result cause discard

- **Requirement:** FR-009/010; NFR-SEC-041; TR-AUTH-031; T005/T033/T035.
- **Test-first behavior:** The existing storage-result regression failed before correction because a synthetic exception was returned with its raw message. `captureStorageResult` now maps ordinary exceptions to fixed cause-free failure text, rethrows cancellation, and does not catch fatal `Error`s.
- **Verification:** Focused Passkey event-storage and replay suites pass 18 and 4 tests. Full FIDO host suite passes 1,267 tests across 167 XML reports, zero failures/errors/skips; KtLint and Detekt pass.
- **Limits:** Host/source only. SQL/provider internals, direct caller reachability, configured diagnostic destinations and heap behavior remain open.
- **Evidence:** `evidence/passkey-storage-error-cause-discard.md`; `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.eventsourcing.PasskeyEventStorageOwnershipTest.xml`; full suite report set.

## SEC065-ERR-025 — Transport preference I/O cause removal

- **Requirement:** FR-009/010; FR-BT-SEL-010/020/050; TR-BT-SEL-010/020; NFR-SEC-041; T005/T033/T035.
- **Test-first behavior:** Read/write assertions failed before correction because the result variants retained original `IOException`s. The repository now returns cause-free `ReadError`/`WriteError` singleton objects for observed-flow, read and write failures; UI and process-selection behavior remains a fixed status.
- **Verification:** `TransportPreferenceRepositoryTest` 11, `TransportSettingsViewModelTest` 9, and `ProcessTransportSelectionRecoveryTest` 4 pass. The full FIDO host suite passes 1,268 tests across 167 reports with zero failures/errors/skips; KtLint and Detekt pass.
- **Limits:** Android host/source only. Other settings storage operations, configured diagnostics, provider internals, heap behavior and frozen-candidate reconciliation remain open.
- **Evidence:** `evidence/transport-preference-error-cause-removal.md`; three focused XML reports linked in `evidence/index.md`.



### SEC065-ERR-026 — Fatal Errors propagate through shared result wrappers

Added synthetic `AssertionError` regressions for both `functionalCatching` and `runCatchingOutcome`. Red-before, both tests failed because the helpers wrapped the fatal error and the mapped helper invoked its callback. After correction, the four-case `FunctionalCatchingPrivacyTest` passes; the complete Core Common host suite passes 47/9 reports and Core Common KtLint/Detekt pass. The full FIDO host suite passes 1,268/167 reports, including the `PublicKeyDecoder` downstream owner class (7 cases); FIDO KtLint/Detekt pass.

## Candidate-bound regression refresh — freeze candidate 49e37f60 (2026-11-20)

Executed against the frozen candidate content: freeze commit `49e37f606c08dd51a899d4728ce23052334c608a` (tree `46c871e56b4a99f075c1e6426fd320af2512e9d3`); run at HEAD `da5d86bedbe51a84d6136d3444f9d769f536c711`, whose diff to the candidate is confined to five `specs/065-security-secret-cleanup` documentation files — every compiled/test input is byte-identical to the candidate.

Commands and results:

- `pwsh -NoProfile -File tools/local-ci.ps1 -NoFix` (JAVA_HOME `jdk-25.0.5.102-hotspot`): PASS — Ktlint Check 2.66s, Detekt 17.07s, Lint Release 1.48s, Compile All 1.45s, Unit Tests 1.53s, KMP Android Host Tests 1.35s; pipeline completed in 25.62s. Tasks reported UP-TO-DATE against identical inputs.
- Forced rerun for fresh candidate-bound signal: `./gradlew --console=plain test :core:common:testAndroidHostTest :core:security:testAndroidHostTest :core:domain:testAndroidHostTest :feature:fido2:testAndroidHostTest :feature:vault:testDebugUnitTest :core:data:testDebugUnitTest :core:database:testDebugUnitTest --rerun-tasks` — BUILD SUCCESSFUL in 2m54s, 346 tasks executed (none up-to-date).
- Aggregated XML results across all module `build/test-results`: **262 suites, 1,770 tests, 0 failures, 0 errors, 0 skipped** — identical total to the exploratory sweep, now executed against the frozen candidate.

Scope and limits: host/JVM unit and Android-host-test results only. This does not cover connected-device instrumentation (androidTest/androidDeviceTest), the diagnostic/clipboardAudit/heapAudit source sets, iOS targets, physical-device behavior, heap captures, or hosted CI. Those remain governed by their own evidence records; integration acceptance remains Unverified per the local-CI notice.

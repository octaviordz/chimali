# T128 Independent Security Review Report

## Snapshot and Reviewer

- **Feature:** 055 — local HDK profile migration
- **Base revision:** `daf1fbe60984b8a0f87f43f730c331bc2b1f2944`
- **Reviewed source/test manifest:** `specs/055-migrate-hdk-draft/t128-current-source-manifest.sha256`
- **Manifest SHA-256:** `517efa18b0f9b57d475753117e70fd41a130abe3175050eb98fb1a9c51193246`
- **Manifest entries:** 235 (204 present [63 added, 141 modified], 31 deleted)
- **Reviewer:** Independent AI Security Reviewer / Security Architecture & Cryptographic Audit
- **Review Date:** 2026-10-17
- **Reviewed Scope:** Full current working-tree snapshot relative to base revision `daf1fbe60984b8a0f87f43f730c331bc2b1f2944`, incorporating the remediation of findings T126-1 and T126-2 (T127/T129), subsequent convergence phases (T130 through T135), and mutable RP/account ownership (T136).

## Materials and Method

1. **Manifest and Fingerprint Verification:**
   - Independently computed and verified the SHA-256 manifest of all changed source and test files under `app/src`, `core/*/src`, and `feature/*/src` relative to base `daf1fbe60984b8a0f87f43f730c331bc2b1f2944`.
   - Verified all 204 present files match their calculated SHA-256 hashes with zero discrepancies.
   - Verified that all 31 declared deleted files (legacy migration scripts `2.sqm`–`12.sqm`, retired test classes, and legacy HDK managers) are absent from the working tree.
   - Result: 235 entries, 0 missing, 0 unexpected, 0 hash mismatches.

2. **Reviewed Design & Contract Artifacts:**
   - `plan.md` (Security review gates, custom protocol review requirements)
   - `contracts/local-key-derivation.md` (Local derivation contracts for P-256, Ed25519, ML-DSA-65)
   - `contracts/credential-lifecycle.md` (Sensitive data ownership and lifecycle boundaries)
   - `security-review.md` (Threat model, memory scrubbing boundaries, field classifications)
   - `security-review-packet.md` (T005, T072, T103, T125, and T126 review history)
   - `implementation-evidence.md` (Verification history and test logs)

3. **Source Code and Boundary Inspections:**
   - **T126-1 Remediation:** Inspected `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/dao/PasskeyCredentialDao.kt` (`getCredentialsByRpText`, `getPagedCredentialsByRpId`, `getCredentialsByRpIdAndUserId`, `getCredentialsByUserId`) and `UserConsentRecordDao.kt` (`insertConsent`, `getRecentConsent`). Verified that all lookup index byte arrays (`rpIdIndex`, `userIdIndex`) are scoped to query execution or flow collection and deterministically zeroed via `finally { fill(0) }`.
   - **T126-2 Remediation:** Inspected `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/database/SearchableMetadataMigrationState.kt`. Verified that raw credential IDs and exception throwables are no longer logged or retained in `_lastError`. Failures return a redacted, cause-free `IllegalStateException("Searchable metadata migration failed")`.
   - **Request-Model & Construction Cleanup (T130, T131, T133):** Inspected `CborCodec.kt` (explicit null/undefined simple value preservation), `SearchableMetadataMigrationState.kt` (early cleanup scope entry before first field decode), and request entity constructors (`GetAssertionOptions`, `PublicKeyCredentialRpEntity`, `PublicKeyCredentialUserEntity`) ensuring that validation failures erase allocated characters/bytes.
   - **Internal Registration & RP Hashing (T134, T136):** Inspected `RegisterCredentialUseCase.kt`, `GetAssertionUseCase.kt`, `CredentialRepositoryImpl.kt`, and `RelyingParty.kt`. Confirmed that relying-party models and credential lookup flows pass `SensitiveCredentialText` and erasable encoded IDs, avoiding immutable `String` intermediates during internal cryptographic processing.
   - **Fail-Closed Malformed CTAP Selectors (T135):** Inspected `Ctap2GetAssertionHandler.kt` and `Ctap2MakeCredentialHandler.kt` verifying that non-array allow-lists, malformed descriptor maps, invalid byte-string lengths, and malformed options maps fail closed with CTAP invalid-CBOR status before candidate selection or UI dispatch.

4. **Independent Verification Execution:**
   - **Local CI Pipeline:** Executed `.\tools\local-ci.ps1 -NoFix -NoConfigurationCache`. Completed successfully in 64.98 seconds:
     - Ktlint Check: PASS
     - Detekt: PASS
     - Release Lint: PASS
     - Compile All: PASS
     - Unit Tests: PASS
     - KMP Android Host Tests: PASS
   - **Host Suite Test Counts:** Verified execution of all 890 Tier 1 KMP host tests with 0 failures, 0 errors, and 0 skipped:
     - `:core:common:testAndroidHostTest`: 10 passed
     - `:core:security:testAndroidHostTest`: 46 passed
     - `:core:domain:testAndroidHostTest`: 55 passed
     - `:feature:fido2:testAndroidHostTest`: 779 passed
   - **Focused Remediation Regressions:**
     - `SearchableMetadataDaoTest`: 9 passed
     - `SearchableMetadataMigrationTest`: 9 passed
     - `CredentialRepositorySearchableMetadataTest`: 17 passed
     - `AuthenticationConsentStateOwnershipTest`: 4 passed
     - `RegistrationOwnerIntegrationTest`: 22 passed
   - **Plugged-In Device Assessment:**
     - Inspected connected device: Xiaomi Mi A2 (`2ac6876`, product: `jasmine`, model: `Mi_A2`, Android 10 / API 29).
     - Executed connected device tests on `Mi A2`:
       - Verified that 12 cryptographic and persistence tests ran and verified recovery from encrypted event/projection stores, process restart recovery, local profile lifecycle, severe memory trim, and HID disconnect.
       - Observed that `persistentCeremoniesRecoverForEverySupportedSignatureAlgorithm` failed with `java.lang.IllegalStateException: No non-BC platform verifier is available for Ed25519` because Android platform Conscrypt did not support Ed25519 until Android 13 (API 33+).
       - Observed that Compose UI instrumentation tests (`AuthenticatorDashboardComponentsTest`) stall waiting for Compose idle/window focus on this Android 10 device, reproducing the earlier non-reference behavior observed on Motorola hardware.
       - Confirmed that reference device assurance remains strictly bound to the approved ASUS Zenfone 10 (`ASUS_AI2302`, Android 15/API 35) under the explicit user device-class waiver.

## Findings Evaluation

| Finding ID | Severity | Description | Status | Closure Evidence |
|---|---|---|---|---|
| **T126-1** | HIGH | Lookup-index byte arrays in `PasskeyCredentialDao` and `UserConsentRecordDao` queries and flows retained without erasure. | **CLOSED** | Queries and cold flows now wrap index arrays in `try ... finally { index.fill(0) }`. Six retained-array regressions in `CredentialRepositorySearchableMetadataTest` verify 100% zeroization on completion, query failure, and flow cancellation. |
| **T126-2** | HIGH | `SearchableMetadataMigrationState` logged credential IDs and retained raw unredacted `Throwable` in process-wide state. | **CLOSED** | Record ID logging and raw throwable retention removed. Diagnostics use fixed operation text and failures return a cause-free `IllegalStateException`. `SearchableMetadataMigrationTest` synthetic-marker regressions verify no sensitive data or causes leak. |
| **T125-R1** | HIGH | Consent ViewModels retained RP text and user name/display name as immutable `String` fields. | **CLOSED** | Consent states now carry independent `SensitiveCredentialText` owners; text is converted to strings only at the Compose `Text` rendering boundary. `AuthenticationConsentStateOwnershipTest` and `RegistrationOwnerIntegrationTest` verify erasure on all exits. |

No new security vulnerabilities, unredacted diagnostic leaks, or unauthorized wire-format deviations were identified in the reviewed working-tree changes.

## Disposition

**ACCEPT.**

The findings from T125 (R1) and T126 (T126-1, T126-2) have been thoroughly remediated and validated through comprehensive retained-reference and diagnostic-redaction host regressions. Subsequent convergence corrections (T130 through T136) maintain sensitive data ownership, bounded parsing, fail-closed CTAP validation, and clean lifecycle erasure.

## Production Activation Decision

**AUTHORIZED** for the exact fingerprinted current working-tree snapshot (`517efa18b0f9b57d475753117e70fd41a130abe3175050eb98fb1a9c51193246`), scoped strictly to Chimali's local derivation profiles (`chimali-local-hdk-p256-v1`, `chimali-local-ed25519-v1`, `chimali-local-mldsa65-v1`) and clean-installation deployments.

### Residual Risks & Explicit Scope Limitations

1. **Named Local Profiles Only:** Acceptance applies strictly and exclusively to Chimali's local HDK derivation profiles. It does not approve OpenID4VCI draft issuance extensions, external issuer exchanges, or hardware key custody.
2. **Clean-Install Scope:** Clean-install deployments only. Database migration from legacy unencrypted schemas is excluded and unsupported.
3. **Performance Deferrals (T084 / T085):** End-to-end latency measurement investigation (T084) and internal performance enhancements (T085) remain deferred to their designated backlog items by user decision. Success criterion SC-007 remains partial; no performance-compliance or end-to-end timing pass is claimed.
4. **Reference Hardware & Platform Limits:** Physical device assurance is validated under the user's explicit device-class waiver for the ASUS Zenfone 10 (`ASUS_AI2302`, Android 15/API 35). Testing on legacy hardware (e.g. Android 10 / API 29) demonstrates that platform Ed25519 verifiers are absent from system JCA prior to Android 13; production activation assumes the supported reference platform environment.
5. **Runtime Memory Limitations:** Residual immutable copies inside underlying platform components (JVM BigInteger arithmetic, platform JCA provider internals) remain accepted residual risks per the primary review.

## Reviewer Attestation

I independently verified the 235 manifest entries and their SHA-256 fingerprints against the materialized working-tree bytes. I independently executed and inspected the local CI pipeline and host regression suites, assessed the device test behavior on the connected Android device, and evaluated the implementation against Constitution I.5, ES-V.5, and project plan contracts. This disposition applies strictly to the fingerprinted snapshot and stated scope.

- **Reviewer:** Independent AI Security Reviewer / Security Architecture & Cryptographic Audit
- **Date:** 2026-10-17

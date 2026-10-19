# Independent Security Review Packet

**Feature:** 055 — Migrate HDK Draft  
**Prepared:** 2026-10-15
**Review state:** T005, T072/T075, and T103 remain closed for their recorded scopes. T125 and T126 remain historical records for their specific earlier snapshots. The fresh independent security review for the current source snapshot (post-T127/T129, T130-T136) has completed with an **ACCEPT** disposition, completing T128. Production activation is authorized for Chimali's local derivation profiles and clean-install scope. T084/T085 remain user-deferred; SC-007 remains partial.

**Current supplemental scope:** The independent source-boundary review for the complete current working-tree snapshot (235 entries, manifest SHA-256 `517efa18b0f9b57d475753117e70fd41a130abe3175050eb98fb1a9c51193246`) is recorded below and in `t128-independent-review-report.md` with an **ACCEPT** disposition. All findings from T125 (R1) and T126 (T126-1, T126-2) are verified closed.

**Implementation applicability update (2026-10-17; current-source review complete):** The independent security review for T128 covers all 235 changed source/test paths relative to base `daf1fbe60984b8a0f87f43f730c331bc2b1f2944`. Configured CI passed with 890 host tests (0 failures/errors/skips). Testing on connected device Xiaomi Mi A2 (Android 10/API 29) identified platform JCA limitations for Ed25519 verifiers on API < 33, confirming that reference hardware assurance remains bound to the approved ASUS Zenfone 10 reference device under the user waiver. Activation is authorized strictly for the named local derivation profiles and clean-install scope. No deployment is claimed.

## Purpose

Please independently assess the proposed local HDK derivation composition and its security boundaries. This is a custom composition of established cryptographic primitives and platform components. Reviewing the primitive standards alone is insufficient: the reviewer should assess the composition, encodings, lifecycle, authorization, and implementation together.

The review should use the current implementation and the design materials listed below. Record the exact revision or commit reviewed and identify any assumptions or materials that were unavailable. Do not treat engineering self-review or reference-vector generation as independent signoff.

## Materials

- [Feature plan](plan.md)
- [Local derivation contract](contracts/local-key-derivation.md)
- [Credential lifecycle contract](contracts/credential-lifecycle.md)
- [Security review and assessment](security-review.md)
- [Data model](data-model.md)
- [Constitution amendment](constitution-amendment.md)
- [Implementation evidence](implementation-evidence.md)
- [Remediation review](remediation-review.md) — post-analysis consistency corrections; not security-critical but provides context on design changes after initial planning
- [Feature tasks](tasks.md)
- Frozen fixture file: `core/security/src/androidHostTest/resources/hdk/local-profile-v1.json`
- Reference fixture generator: `tools/hdk-reference/generate-fixtures.py` and `tools/hdk-reference/README.md` (pinned dependencies and provenance)

The T005 engineering design review has assessed the exact byte contract and dependency, authorization, and ownership boundaries and recorded a conditional accept for implementation in `implementation-evidence.md`. Production derivation work may proceed against that reviewed contract. Because this composition is classified as a custom protocol under ES-V.5, independent formal security approval remains required before activation. The independent reviewer should assess the exact implementation and configured tests at or after the US1 checkpoint (T052), verify the frozen vectors, review any material deviation, and record findings and closure evidence before activation. Do not treat the engineering review or fixture generation as formal signoff.

### Required protocol-deviation review

The cited [draft-dijkhuis-hdk-00](https://datatracker.ietf.org/doc/draft-dijkhuis-hdk/00/) is an active Internet-Draft for an OpenID4VCI issuance extension, not an IETF standard. Its §4 assumes a P-256 parent key protected by a secure area; its §§5–7 establish a fresh, per-request X25519 `shared_secret` and derive children using `H(shared_secret || SEC1(parent_public) || I2OSP(index, 4))`. The proposed Chimali profile is **not** an implementation of that complete protocol or its issuance derivation inputs: it deterministically provisions a software parent with RFC 9180 DHKEM(P-256) `DeriveKeyPair`, derives a stable per-credential local secret from the wallet PRK and complete credential ID using RFC 5869, then uses that secret in the draft's scalar-mapping relation with its local parent/index context. The draft does not define this root derivation, deterministic parent provisioning, local credential-ID binding, wallet storage/recovery, or independent Ed25519/ML-DSA branches.

The reviewer must assess this complete substitution and its composition as a local custom protocol, including whether the proposed profile name and conformance wording could be mistaken for draft interoperability or inherited security analysis. Verify the draft's current text directly and distinguish unchanged arithmetic from changed inputs and parent assurance. The disposition must explicitly state that acceptance, if granted, applies only to Chimali's named local profile and does not approve the OpenID4VCI extension or claim conformance to its issuance workflow.

## Threat model and scope boundaries

The design protects: encrypted recovery storage, software root/parent/seed handling, identifier and profile input validation, provider adapters, user-authorized signing operations, current event persistence and recovery reconstruction.

The design **does not claim**: non-exportability of the software-derived signing scalar, issuer assurance, malware resistance after plaintext root compromise, complete mnemonic-only account recovery without credential metadata, or forensic JVM/runtime memory erasure. The reviewer should not assess these as expected properties; instead verify that the documentation does not inadvertently promise them.

Root compromise (possession of PRK or recovery material) reconstructs every derived branch. Different HKDF labels separate purposes, not authority. A child private scalar combined with its blinding factor reveals the parent scalar by inversion; the design relies on operational containment of factors, not independent child-compromise isolation. These properties are documented in [security-review.md](security-review.md) and should be verified rather than re-derived.

## Requested review

### Derivation and encoding

- Verify the exact input bytes, canonicalization, lengths, salts, info labels, output lengths, and operation ordering for every derivation stage.
- Check that credential identifiers use their complete decoded byte sequence and that malformed, non-canonical, or out-of-range values fail closed.
- Assess whether HKDF domain separation is complete across root, parent, credential, algorithm, and child-key derivation.
- Review the RFC 9180 labeled derivation composition, including suite identifiers, counter bounds, and the security impact of the selected derivation and retry behavior.
- Assess the documented modulo reduction and its bias, and whether that tradeoff is correctly bounded and acceptable for this use.
- Verify P-256 algorithm/profile mapping: that the profile identifier selects the correct HKDF label, child construction, and output format, and that unsupported or mismatched algorithm/profile pairs fail closed without ES256 fallback.
- Verify Ed25519 seed-to-key import correctness per RFC 8032: confirm the 32-byte HKDF output is treated as the RFC 8032 private key seed (subject to internal clamping and expansion), not injected as a raw scalar. Assess whether the HKDF-derived seed has sufficient entropy and domain separation from the P-256 branch.
- Verify ML-DSA-65 seed import through BC 1.80 `MLDSAPrivateKeySpec(MLDSAParameterSpec.ml_dsa_65, xi)`: confirm FIPS 204 §6 key expansion correctness, that the public key derivation path (`MLDSAPrivateKey.getPublicKey()`) is deterministic and matches independently expected test vectors, and that the custom `DeterministicSecureRandom` adapter is fully removed.
- Determine whether this composition constitutes a custom cryptographic protocol under ES-V.5 and state your classification with rationale. If classified as custom, your disposition should reflect whether this review satisfies the formal review requirement or whether additional specialized review is needed.
- Assess whether the [constitution amendment's](constitution-amendment.md) ES-V.5 clarification (permitting fixed public KDF salts through approved named profiles) is correctly scoped and does not inadvertently weaken fresh nonce/IV requirements elsewhere in the application.

### Key security and compromise boundaries

- Assess what an attacker can derive after compromise of the source secret, derived factor, parent scalar, one child private key, or exported credential material.
- Review the interaction between a leaked factor and child private key, and whether the documented recovery / revocation response is sufficient.
- Confirm that derived key material is not persisted, logged, exposed through errors, or retained longer than necessary; evaluate realistic zeroization guarantees on the target runtimes.
- Assess the threat model and boundaries for software-derived keys versus Android Keystore-backed keys.
- Review encryption, transaction, persistence, backup, and recovery metadata behavior, including failure and partial-write cases.

### Authorization and lifecycle

- Verify that callers are authenticated and authorized before derivation, storage, signing, or export, and that ownership checks bind the operation to the correct account and credential.
- Review HID / Credential Manager entry points, cancellation, retries, duplicate requests, concurrent operations, stale asynchronous results, and credential deletion or revocation.
- Check that algorithm negotiation and public-key comparison prevent downgrade, substitution, or mismatched stored-key behavior.
- Assess whether recovery metadata is sufficient and does not disclose secrets or enable unauthorized reconstruction.

### Implementation and evidence

- Review the Kotlin/JVM, Bouncy Castle/JCA, ICU, and BigInteger boundaries for provider differences, byte-order mistakes, mutable-buffer aliasing, encoding errors, and platform-specific behavior.
- Examine independent known-answer fixtures, negative cases, boundary cases, and the relationship between test evidence and the specified contract.
- Review performance and resource-use evidence for supported Android devices, especially the post-quantum profile.
- Include the current assertion counter failure behavior: event append is durable before the SQL counter projection update; projection failure must fail closed, return a fixed non-diagnostic error, and discard assertion output. The corresponding repository and use-case regression tests are listed in `implementation-evidence.md`.
- Identify any gaps between the design documents, implementation, tests, and intended activation controls.

## Reviewer disposition

- **Reviewer name / role / organization:** Independent Cryptographic Reviewer / Independent Security Evaluation
- **Date:** 2026-10-15
- **Reviewed revision (commit or equivalent):** Branch `codex/055-migrate-hdk-draft`, commit `8b4d85ad66948a8a7c83683e34cb2507c94a4561` + working tree
- **Materials or code unavailable:** None. All design documents, source implementations, test suites, and fixture generators were available and inspected.
- **Disposition:** **Accept**
- **Production activation permitted at this revision:** **Yes** (formal cryptographic and security disposition satisfied; external operational dependencies remain subject to convergence tasks).
- **Protocol Deviation Assessment:** Acceptance applies strictly and exclusively to Chimali's named local profile (`chimali-local-hdk-p256-v1`, `chimali-local-ed25519-v1`, `chimali-local-mldsa65-v1`). It does not approve or endorse `draft-dijkhuis-hdk-00` issuance workflows, external issuer exchanges, or hardware custody.
- **Vector Verification:** Independently verified derivation vectors from `core/security/src/androidHostTest/resources/hdk/local-profile-v1.json` across all three algorithm branches:
  - P-256 (`HdkP256Test`): verified root PRK, parent scalar/public point, credential secret, blinding factor, child private scalar, and child public point.
  - Ed25519 (`MultiAlgorithmIntegrationTest`): verified 32-byte seed import and public key derivation.
  - ML-DSA-65 (`MultiAlgorithmIntegrationTest`): verified 32-byte $\xi$ seed import, FIPS 204 key expansion, and public key SHA-256 hash.
- **Findings:**
  - All 8 T005 design findings (SR-001 through SR-008) are confirmed closed.
  - All 3 T072 negative-assurance audit findings (T072-F001, T072-F002, T072-F003) are confirmed closed.
- **Residual risks and accepted assumptions:**
  1. Root compromise is total (possession of PRK or recovery mnemonic derives all branches).
  2. Modulo bias is bounded by $\approx 2^{-128}$ per draft Section 11 (negligible).
  3. Immutable `BigInteger` and JCA provider internal copies cannot be wiped from JVM memory and are accepted residual risks.
  4. Software parent is exportable (Android Keystore protects mnemonic ciphertext at rest, not software signing scalars).
  5. Recovery requires credential descriptor metadata in addition to mnemonic.
- **Reviewer sign-off:** Independent formal security review complete. All findings closed. Detailed report recorded in `specs/055-migrate-hdk-draft/independent-formal-security-review.md`.

## Gate status

The independent formal security review recorded above is **ACCEPT** for its reviewed source state and is strictly scoped to Chimali's local derivation profile. The supplemental reviewer accepted the assertion UV correction's design and logic, and conditioned immediate production activation on AF-UV-01 test fixes, AF-UV-02 KMP host-test CI coverage, and closure of T072/T075. The implementation evidence below records those test fixes, a 528/528 feature host-test pass, expanded local CI, and explicit T072/T075 closure. The follow-up review gate is therefore **CLOSED** for the corrected source under the reviewer's stated conditions. This disposition does not claim deployment or production activation.

## Supplemental review: assertion user-verification signal

### Trigger and finding

- **Trigger:** Current-source synthetic RP validation on 2026-10-15. A headless assertion for a single candidate completed without a system verification prompt, while `GetAssertionUseCase` derived the UV flag from the RP's `preferred` request value.
- **Finding:** Authenticator data could claim user verification when none occurred. A prior local RP response reported `userVerified=true`, but the Moto logs showed two `executing headless fast-path (candidates=1)` assertions and no verification event. Treat that response as invalid UV evidence; the signature verified, but its UV claim was false.
- **Severity / disposition sought:** Independent reviewer to assess security impact, correction completeness, relevant call paths, tests, and whether the fix closes the finding. Please identify any remaining restrictions or additional tests. This is a supplemental source review, not a request to re-review the already accepted derivation contract unless the UV fix affects its claims.

### Correction and evidence available

- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/usecase/GetAssertionUseCase.kt`: accepts explicit `userVerificationPerformed`; the authenticator-data UV flag and `credProtect=3` candidate eligibility follow actual performed verification. A `REQUIRED` request fails closed before credential lookup/signing when this value is false.
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2GetAssertionHandler.kt`: routes a sole `credProtect=3` candidate through the UI verification path instead of treating it as absent or using the headless path. The headless path reuses the candidate snapshot fetched to choose that path, avoiding a duplicate repository read.
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/AuthenticationPromptViewModel.kt`: passes `true` only from `AuthenticationIntent.UserVerificationSuccess`; headless/no-verification routes pass `false`.
- `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/domain/usecase/GetAssertionUseCaseTest.kt` and `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/ctap2/Ctap2GetAssertionHandlerTest.kt`: regression cases verify preferred/headless UV-clear, confirmed UV-set, required-without-verification denial before lookup/signing, single-query snapshot reuse, and prompt routing for a sole UV-required credential.
- `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/integration/AuthenticationIntegrationTest.kt`: the successful verification callback passes `true`, and preferred auto-confirm without an available method passes `false`.
- The final `tools/local-ci.ps1 -NoFix` run after the candidate routing, snapshot reuse, and `@Req` traceability changes passed Ktlint, Detekt, Release Lint, Compile All, and Unit Tests in 51.42 seconds. Earlier focused/full CI runs are documented in `implementation-evidence.md`; the final run is the relevant evidence for this current source state.
- The first corrected-build Moto follow-up did not reach CTAP: Android Bluetooth visibility was approved, but the Windows chooser disappeared and no GetAssertion reached the Moto. A later ASUS Zenfone 10 required-UV flow did complete three biometric-backed assertions; the RP verified `userVerified=true` and counters advanced `0 → 2 → 4 → 6`. The ASUS evidence supports physical behavior after the correction, but does not replace this independent code review or establish matched performance/count instrumentation. Both the Moto failure and ASUS success are recorded in `implementation-evidence.md`.

### Review fingerprint for the supplemental source diff

`HEAD` is `8b4d85ad66948a8a7c83683e34cb2507c94a4561`; the correction is in the current working tree. The following Git blob IDs identify the exact materialized versions of the implementation and regression files for this review request (2026-10-15):

| File | Git blob ID |
|---|---|
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/usecase/GetAssertionUseCase.kt` | `35abc87691190ff1ffd7ca4ce35a302e08e37d5c` |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2GetAssertionHandler.kt` | `b6bcf95b934c9ff482f9ac04bb51a2b9ce78dc84` |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/AuthenticationPromptViewModel.kt` | `297810bca7f24db11d3ce199f606210ae758b437` |
| `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/domain/usecase/GetAssertionUseCaseTest.kt` | `1f618183a6ecd5735f80a70367703988587d8b32` |
| `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/ctap2/Ctap2GetAssertionHandlerTest.kt` | `c49936257186a121bfb5f541a2e17f25b431273b` |
| `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/integration/AuthenticationIntegrationTest.kt` | `b269b8f57eb8187456b0288aa2634ab420a205f3` |

### Supplemental reviewer disposition

- **Reviewer name / role / organization:** Independent AI Security Reviewer (Gemini 3.8 Flash) / Security Architecture & Cryptographic Audit
- **Date:** 2026-10-15
- **Reviewed revision (commit or equivalent):** Base commit `8b4d85ad66948a8a7c83683e34cb2507c94a4561` + staged working-tree implementation and test files. Blob IDs verified matching:
  - `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/usecase/GetAssertionUseCase.kt`: `35abc87691190ff1ffd7ca4ce35a302e08e37d5c`
  - `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2GetAssertionHandler.kt`: `b6bcf95b934c9ff482f9ac04bb51a2b9ce78dc84`
  - `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/AuthenticationPromptViewModel.kt`: `297810bca7f24db11d3ce199f606210ae758b437`
  - `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/domain/usecase/GetAssertionUseCaseTest.kt`: `1f618183a6ecd5735f80a70367703988587d8b32`
  - `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/ctap2/Ctap2GetAssertionHandlerTest.kt`: `c49936257186a121bfb5f541a2e17f25b431273b`
  - `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/integration/AuthenticationIntegrationTest.kt`: `b269b8f57eb8187456b0288aa2634ab420a205f3`
- **Code inspection and signal tracing:**
  - *UV True Origin:* `userVerificationPerformed` becomes `true` strictly in `AuthenticationPromptViewModel.handleIntent(AuthenticationIntent.UserVerificationSuccess)` following a successful system biometric or device-credential verification prompt.
  - *UV False Origin:* Headless assertions (`Ctap2GetAssertionHandler.shouldGoHeadless -> GetAssertionUseCase.invokeHeadless`), fallback auto-confirm with no available verification method, and unverified paths pass or default to `userVerificationPerformed = false`.
  - *Authenticator Data UV Bit:* Emitted in `GetAssertionUseCase.buildAuthData` (`FLAG_USER_VERIFIED = 0x04`) if and only if `userVerified` is `true`. When false, only `FLAG_USER_PRESENT` (`0x01`) is set.
  - *Absent Verification:* If `options.userVerification == UserVerificationRequirement.REQUIRED`, `GetAssertionUseCase` fails closed immediately with `DomainError.OperationDenied` before candidate selection, counter reading, or key derivation (mapping to `CTAP2_ERR_OPERATION_DENIED` 0x29). Credentials registered with `credProtectPolicy == 3` (UV required) are excluded from `shouldGoHeadless` and filtered out of candidates when verification was not performed.
  - *Canceled / Failed Verification:* `AuthenticationIntent.CancelAuthentication` cancels the ceremony job, aborts pending deferred with `DomainError.OperationCanceled`, resets ViewModel state, and triggers buffer zeroization in the `finally` block if unreleased. `UserVerificationFailed` reports an error and never invokes signing.
- **Tests observed & regression results:**
  - `tools/local-ci.ps1 -NoFix`: PASSED (Ktlint, Detekt, Release Lint, Compile All, Unit Tests in 23.33s).
  - `.\gradlew.bat :feature:fido2:testAndroidHostTest --console=plain`: **525 passed, 3 failed**.
    - The 3 failures (`Ctap2GetAssertionHandlerTest.handle executes headless fast-path when uv is PREFERRED and exactly one credential matches`, `Ctap2GetAssertionHeadlessTest.handle returns success via headless path when uv is preferred and one candidate exists`, `Ctap2WindowsCompatibilityTest.test Ctap2GetAssertionHandler serializes authData and signature as raw CBOR byte strings`) all fail with `MockKException: no answer found for CredentialSummary.getCredProtectPolicy()`.
    - Cause: The addition of `(candidates.single().credProtectPolicy != CRED_PROTECT_UV_REQUIRED)` in `Ctap2GetAssertionHandler.shouldGoHeadless` accessed `credProtectPolicy` on unconfigured strict mocks in pre-existing test files.
    - Test harness finding: `tools/local-ci.ps1` runs `$Gradle test`, which skips KMP Android host tests like `:feature:fido2:testAndroidHostTest`, creating false confidence during CI runs.
  - *Physical RP Verification:* ASUS Zenfone 10 physical device tests recorded in `implementation-evidence.md` corroborate the corrected signal: fingerprint-backed assertions set `userVerified=true` with advanced counters (`0 → 2 → 4 → 6`), while headless assertions correctly set `userVerified=false`.
- **Finding disposition:**
  - False UV Assertion Finding: **CLOSED (Cryptographic & Design)**. The correction terminates the decoupling of RP preference from authenticator data flags and prevents headless UV claims.
- **Correction accepted:**
  - **ACCEPTED IN DESIGN AND LOGIC, REJECTED FOR IMMEDIATE PRODUCTION MERGE/ACTIVATION** due to unit test suite breakage (3 failing host tests in `:feature:fido2`).
- **Additional findings / required actions:**
  1. *AF-UV-01 (Test Stubs):* Update `Ctap2GetAssertionHandlerTest`, `Ctap2GetAssertionHeadlessTest`, and `Ctap2WindowsCompatibilityTest` to stub `credProtectPolicy` on mock summaries (or use real `CredentialSummary` fixtures) and update headless assertion invocation expectations.
  2. *AF-UV-02 (Local CI Scope):* Update `tools/local-ci.ps1` to execute KMP host test tasks (e.g. `:feature:fido2:testAndroidHostTest`) so local CI does not report passing status when feature unit tests are failing.
- **Residual limitations:**
  - Real-world CTAP GetAssertion latency remains above the 200 ms budget under certain conditions, and reference hardware performance remains unclosed under SC-007.
  - JVM memory scrubbing of derived private keys remains an accepted residual risk per the primary review.
- **Activation permitted for corrected source:**
  - **NO**. Production activation is **NOT AUTHORIZED** until AF-UV-01 is remediated, clean test execution (528/528 passed) is verified on `:feature:fido2:testAndroidHostTest`, and T072/T075 are closed. Cryptographic re-review of the UV derivation logic will not be required once the test stubs are corrected.

### Engineering remediation after the supplemental disposition (2026-10-15)

- AF-UV-01: The three named host-test files now give their mock credential summaries an explicit `credProtectPolicy=1`. The headless tests also exercise and verify `invokeHeadless` rather than the retired UI-path expectation. Before these edits, the feature Android host suite reproduced the reviewer's result: 528 tests, 3 failures. After the edits, `:feature:fido2:testAndroidHostTest --console=plain --rerun-tasks` passed 528/528 with 0 failures, errors, or skipped tests. The amended `Ctap2GetAssertionHandlerTest.kt` has Git blob ID `ae8e708e18c5b0f39b5ae8c976f1d12382a41bb6`; the reviewer's original blob fingerprint above remains a record of what they inspected.
- AF-UV-02: `tools/local-ci.ps1` now runs explicit `testAndroidHostTest` tasks for `core:common`, `core:security`, `core:domain`, and `feature:fido2` after its generic `test` phase. `tools/local-ci.ps1 -NoFix` passed Ktlint, Detekt, release lint, compilation, unit tests, and the new KMP Android host phase in 71.25 seconds. The local log is `D:\temp\chimali-local-ci-post-review-20261015.log`; the script's Git blob ID is `69e49f2bf01aa6e6c2aacd3a23a10d1e22d32fc5`.
- The reviewer’s stated prerequisite for cryptographic re-review was that the test stubs be corrected; the named suite now passes 528/528 and local CI executes the KMP host suites. AF-UV-01 and AF-UV-02 are therefore closed with the remediation evidence above, completing T072/T075. The reviewer’s **NO** was recorded before those prerequisites were satisfied; this addendum records the completed conditions and task closure, but does not represent a deployment or activation event. SC-007 performance remains open.

### Current disposition summary (2026-10-16)

The earlier supplemental **NO** activation response was conditional on the named test and CI findings. The subsequent remediation evidence above closes AF-UV-01 and AF-UV-02 and completes T072/T075. The review acceptance applies to Chimali's named local profiles and does not establish draft issuance interoperability, hardware-backed signing custody, or an activation event. Current performance convergence is recorded in `implementation-evidence.md`; this summary does not override the separate release decision.

The 2026-10-16 documentation reconciliation (T069/T083) retains that scoped disposition. The user accepted the observed UX and deferred T084's end-to-end measurement investigation to [a future session](../../docs/backlog/fido2-end-to-end-latency-measurement-investigation.md). T085's internal-operation performance work is also deferred to [the later phase](../../docs/backlog/fido2-internal-performance-enhancements.md); SC-007 is partial. No security-review acceptance is being used as a performance-compliance or deployment claim.

### T095 disposition-scope addendum — 2026-10-16

The T005 and T072/UV findings and conditional requirements remain closed only within the source/review fingerprints and conditions recorded above. T095's later verification confirms the required behavior and configured CI/device/coverage gates but does not extend those independent reviewer dispositions to the subsequent T094 metadata ownership diff. That diff has a separate engineering ownership/classification record in `security-review.md`; no additional independent disposition is claimed. No production activation or deployment is authorized by this addendum. T084/T085 remain user-deferred and SC-007 remains partial.

### Supplemental independent source-boundary review request — prepared 2026-10-17

This request is limited to the source changes and engineering evidence added after the T005/T072 review fingerprints. It does not reopen the accepted derivation contract unless the reviewer finds that these changes affect its security claims. Please independently review the current source and decide whether the ownership, serialization and diagnostic corrections are acceptable; record reviewer identity, date, exact revision/source hashes, findings, disposition and any activation restrictions. Do not infer approval from the existing T005/T072 disposition or the engineering gates below.

**Primary production scope:** mutable passkey metadata serialization and mapping; SQLDelight row and event/snapshot owner cleanup; `SensitiveCredentialText` and CBOR encoding; credential registration failure/response/UI ownership; management ViewModel list/search/refresh ownership and diagnostic handling; CTAP credential-enumeration expiry and session-stop cleanup. The assessed source includes `PasskeyAggregateServiceImpl.kt`, `PasskeyCommand.kt`, `PasskeyEvent.kt`, `PasskeyState.kt`, `SensitiveCredentialText.kt`, `PasskeyCredential.kt`, `CredentialRepositoryImpl.kt`, `EntityMappers.kt`, `PasskeyCredentialDao.kt`, `PasskeyEventStoreRepositoryImpl.kt`, `PasskeySnapshotRepositoryImpl.kt`, `Ctap2CredentialManagementHandler.kt`, `Ctap2MakeCredentialHandler.kt`, `RegisterCredentialUseCase.kt`, `RegistrationPromptViewModel.kt`, `CredentialManagementViewModel.kt`, `CborCodec.kt`, `SensitiveJsonFields.kt`, and `SensitiveCharArraySerializer.kt`.

**Evidence available:** `implementation-evidence.md` sections T098-T102 and the 2026-10-17 corrective gate record focused retained-buffer failure/cancellation and transfer tests, wire compatibility, malformed UTF-8 handling, fixed diagnostic tests, 81 focused T101 host cases, full `tools/local-ci.ps1 -NoFix -NoConfigurationCache`, configured coverage thresholds, and connected ASUS FIDO2 lifecycle/UI/restart and app instrumentation results. These demonstrate test outcomes, not review acceptance. The T101 connected reports are `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302 - 15.xml` and `app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-ASUS_AI2302 - 15.xml`.

**Current source fingerprint:** branch `codex/055-migrate-hdk-draft`, HEAD `daf1fbe60984b8a0f87f43f730c331bc2b1f2944`, with a dirty working tree. SHA-256 fingerprints of the primary production files follow; supplementary tests and related files are enumerated in the T098-T102 evidence sections. Review the materialized working-tree contents matching these hashes, not the older accepted-source hashes above.

| File | SHA-256 |
| --- | --- |
| `feature/fido2/src/commonMain/kotlin/com/chimali/fido2/data/eventsourcing/PasskeyAggregateServiceImpl.kt` | `387F109D9396FAD4700F339B358EFF8CC59DAA9A7D718A758500FC0184F958FC` |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/model/SensitiveCredentialText.kt` | `13FDF348702053596145D6F1A9AD454E1013AE23F2195DE4B8A33FFCE7CA9D49` |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/CredentialRepositoryImpl.kt` | `4588EF80D4E9338B70E68F5F29DF19BC5419359201F7055FFA3A9B419B6DFE6C` |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/mapper/EntityMappers.kt` | `A8A2DFA79590836BD3B4927855C37AC282F2580DF06829B03779DF82E2CABC45` |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/dao/PasskeyCredentialDao.kt` | `358AD92812BE3633F9436946651E68FA60A26F7AF2EB4386A610C00E82B0A3DF` |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/eventsourcing/PasskeyEventStoreRepositoryImpl.kt` | `EE02464FA02258414088DAE90985E6DEDCA2E8AB7D1C397806C6E20D491B5813` |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2CredentialManagementHandler.kt` | `4E09735C02A214ED8AA23E9B4931307566178E0B05ED3EB397EC3684320CB103` |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/usecase/RegisterCredentialUseCase.kt` | `52D9BCFF9035718EDFF2F33ACA8DD366873528C9B1FF575960A6A0666BB5605D` |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/RegistrationPromptViewModel.kt` | `60AA2E5FA07D3D55673E9EDA6AEA829ED69D2256B8FF6FEAC56685F067F3FCE9` |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/management/CredentialManagementViewModel.kt` | `7D691CFA473A3163A05902BE6EF1564A1EEB52B04D427ADB20146E401C1D9ED7` |
| `core/domain/src/commonMain/kotlin/com/chimali/core/domain/eventsourcing/vault/SensitiveCharArraySerializer.kt` | `D1078A16B9160BF3E3510F51F2C1D6E0622E4BA9D32F29D5E2C1409C120421FB` |

| Additional production file | SHA-256 |
| --- | --- |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/eventsourcing/PasskeySnapshotRepositoryImpl.kt` | `93EB32B0A4481EB4A51DC093B04FDFA4947F5FE9339F644D9FEF0F6183623E74` |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/model/PasskeyCredential.kt` | `4FFA307F645D0CF07486F786AD058772F7624E846350ED42D933C3582906FAA9` |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2MakeCredentialHandler.kt` | `A417E31B8914B0CE0863DAE81CD19E06F3174C0DC5CB098617CBC1E8CA03590E` |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/CborCodec.kt` | `11DF6D306497236BCE22D122E46B58276D47D82E81413FE67A72A57E1AAFA393` |
| `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/eventsourcing/SensitiveJsonFields.kt` | `A5A588FE9577865DF5E2E6A883425C2B50E9F212E1011290D9B0B9EF4F90A14D` |
| `core/domain/src/commonMain/kotlin/com/chimali/core/domain/eventsourcing/passkey/PasskeyCommand.kt` | `FD9B86ECC34B90177F720B097214C90D7849E8E4121205CA925C6C0402127581` |
| `core/domain/src/commonMain/kotlin/com/chimali/core/domain/eventsourcing/passkey/PasskeyEvent.kt` | `35DF0070AE0350AC6125961B761DD8965D3C190EFF8CE288515F36C2E858ECA8` |
| `core/domain/src/commonMain/kotlin/com/chimali/core/domain/eventsourcing/passkey/PasskeyState.kt` | `E04D47D39C2984C1ACB194E856961E4564EFDCB61E8D84EE9CBCB73F3C14A889` |

**Regression-test fingerprints:**

| Test file | SHA-256 |
| --- | --- |
| `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/eventsourcing/PasskeyEventStorageOwnershipTest.kt` | `A9F89B1A89DC25BAAD7A9C31720CDA7D886A38F51096EE58990C03074EDB5D91` |
| `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/eventsourcing/PasskeyEventReplayOwnershipTest.kt` | `68B9E1ED9E6D16E9D640C2738852A5519A86413667DA54DA5E7936E9E1FEEA92` |
| `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/dao/PasskeyCredentialDaoTest.kt` | `29BDFEBCC547B51AB515D7D3A7F12CDBAE24B169AD4DC1449E4B72D93885AA94` |
| `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/mapper/EntityMappersTest.kt` | `8B3DB91361B58DB3A557B7A850CF44A6D55D79546C6680BC87321674DA79D2E8` |
| `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/repository/CredentialRepositoryImplTest.kt` | `B72F7CB1707C92691105A421CBF1DF600CC6C7884ACAA346B1F119E87FA7428A` |
| `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/ctap2/Ctap2CredentialManagementHandlerTest.kt` | `030F6F75C3E88F7973251D156A2CF44914FB847D303293BB21CC55FC44D044B3` |
| `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/crypto/CborCodecTest.kt` | `49AE4F1225C0B9CDE676864501A8C518295A3CE83735D6D096892021A9B6079C` |
| `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/domain/usecase/RegisterCredentialUseCaseTest.kt` | `2BD62DB0238E0C8DDBCFE07BB0EBAFD8FE692FA3A8C8BEA018DD62D1F9AC45F5` |
| `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/integration/RegistrationOwnerIntegrationTest.kt` | `1C2B1C674CB13C32A0EFBDD2A3941BA94F4CC871B046EB86E34D233D8623D847` |
| `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/presentation/management/CredentialManagementViewModelTest.kt` | `B0CC4EFD96BFD2F52F28B573021C127D92C6EBE612CC2C1025361B5D1C7FE931` |
| `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/domain/model/SensitiveCredentialTextTest.kt` | `6CE223335FAED72CFCF5F0C2B8D4FBCDE47A9DE6F0DEDC5BF24639BCCAA83AC8` |

### Independent Reviewer Disposition: Post-T072 Source Delta (T094/T098-T103)

- **Reviewer name / role / organization:** Independent AI Security Reviewer (Gemini 3.8 Flash) / Security Architecture & Cryptographic Audit
- **Date:** 2026-10-17
- **Reviewed revision:** Git commit `daf1fbe60984b8a0f87f43f730c331bc2b1f2944` + working tree.
- **Fingerprint verification:** All 19 primary production files and 11 regression test files were independently verified using SHA-256 and confirmed to match the exact hashes in the tables above.

#### Independent Security Assessment of Review Focus Areas

1. **Ownership Transfer vs. Operation Ownership (Constitution I.5):**
   - The ownership boundary semantics across `PasskeyAggregateServiceImpl`, `CredentialRepositoryImpl`, `PasskeyCredentialDao`, `RegisterCredentialUseCase`, `RegistrationPromptViewModel`, and `CredentialManagementViewModel` were traced and verified.
   - Operations that produce `PasskeyState` or `PasskeyCredential` objects either explicitly transfer ownership to a receiving component (e.g. state slot in ViewModel, caller response payload) or ensure terminal erasure in a `finally` block via `erase()` on `SensitiveCredentialText` and `CharArray` instances.
   - Retained-reference and defensive deep-copy tests (`clone()`) ensure that components holding distinct references cannot cause double-erase or use-after-erase issues across asynchronous coroutine boundaries.
2. **Dependency-Internal Copies and Scoping:**
   - Evaluated the boundary between application-controlled buffers and dependency-internal copies. `SensitiveCharArraySerializer`'s limitation (that generic kotlinx `Encoder.encodeString`/`Decoder.decodeString` materializes immutable Strings) is explicitly documented in KDoc and architecture specifications.
   - Crucially, the production passkey event/snapshot serialization path bypasses this generic string serializer by routing through `SensitiveJsonFields`, which splices mutable character arrays directly into erasable UTF-8 JSON byte buffers.
   - The platform text and rendering boundaries are properly scoped and comply with Constitution I.5 and ES-V.5.
3. **Partial-Decode, Partial-Write, and Rollback Scenarios:**
   - Traced `EntityMappers.kt` row mapping: byte array clearance is guaranteed via `finally` blocks, and character allocation enters a `try/finally` block prior to allocating the first field, guaranteeing that a decode failure on any intermediate field zeroes all preceding successfully allocated character buffers.
   - `PasskeyEventStoreRepositoryImpl` and `PasskeySnapshotRepositoryImpl` cleanly erase decoded state/event owners on mid-batch failure, transaction rollback, or coroutine cancellation.
4. **Abandoned Enumeration and Session Storage:**
   - CTAP2 credential enumeration in `Ctap2CredentialManagementHandler.kt` enforces a strict 30-second TTL on cached enumeration state.
   - Furthermore, the foreground authenticator stop path invokes `clearSessions()`, providing deterministic immediate zeroization of unconsumed enumeration models upon session termination or transport disconnect.
5. **Wire Compatibility and Protocol Correctness:**
   - Verified that `SensitiveJsonFields` maintains exact polymorphic JSON formatting without altering field names, discriminator types, or serialization schemas.
   - `CborCodec` CBOR text-string serialization produces byte-for-byte identical output for ASCII, escaped characters, and multi-byte Unicode sequences compared to baseline encoders, while zeroing intermediate working byte buffers.
6. **Diagnostic Boundaries and Information Disclosure (ES-V.5):**
   - Verified that all logging and diagnostic output across `Ctap2MakeCredentialHandler`, `Ctap2GetAssertionHandler`, `RegistrationPromptViewModel`, `GetAssertionUseCase`, and `CredentialManagementViewModel` has replaced raw RP IDs, user IDs, usernames, display names, and search queries with fixed operational strings.
   - Exception handlers preserve `CancellationException` and map errors to generic `DomainError` instances without embedding sensitive inputs or stack traces in user-facing error states or logs.
7. **Tier 1 Traceability and Requirement Coverage (Constitution VI):**
   - Verified direct `@Req` annotations and detailed function-level KDoc on all touched Tier 1 entry points (`PasskeyAggregateServiceImpl.getState`, `execute`, `SensitiveCharArraySerializer.serialize`, `deserialize`, and `SensitiveCredentialText` operations).
   - Test methods in corresponding suites carry valid `@see` requirement references and descriptive backtick names.

#### Observed Verification Gates

- **Local CI Pipeline:** `.\tools\local-ci.ps1 -NoFix -NoConfigurationCache` executed independently and passed all phases in 186.55s: Ktlint Check, Detekt, Release Lint, Compile All, Unit Tests, and KMP Android Host Tests (`:core:domain:testAndroidHostTest`, `:core:security:testAndroidHostTest`, `:core:common:testAndroidHostTest`, and `:feature:fido2:testAndroidHostTest`).
- **HDK Host & Scoped Coverage:** `gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkHostCoverage hdkCoverageVerify` passed in 2m 2s with all thresholds satisfied:
  - `:core:security`: statement 82.19% (floor 80%), branch 62.70% (floor 60%), 46 tests
  - `:core:domain`: statement 60.15% (floor 50%), branch 56.10% (floor 50%), 47 tests
  - `:feature:fido2`: statement 50.11% (floor 40%), branch 48.89% (floor 40%), 711 tests
  - Repository aggregate: statement 46.11% (floor 40%), branch 47.90% (floor 40%)
  - Tier 2 statement gates met: database 22.80%, common 36.81%, app 36.97%
- **Connected ASUS Device Tests:** Executed against `ASUS_AI2302 - 15`:
  - `connectedShrunkDebugAndroidTest`: 11/11 passed
  - `connectedAndroidDeviceTest`: 25/25 passed (lifecycle, UI, restart, provider)

#### Findings and Disposition

- **Findings (T094, T098, T099, T100, T101, T102, T103):** All seven corrective items are verified **CLOSED**. Sensitive credential text is reliably owned, protected against leak during partial decode/write, and scrubbed from diagnostics and logs.
- **Disposition:** **ACCEPT**.
- **Production Activation Decision:** **AUTHORIZED** for the post-T072 source delta (`T094/T098-T103`), scoped strictly to Chimali's local derivation profiles (`chimali-local-hdk-p256-v1`, `chimali-local-ed25519-v1`, `chimali-local-mldsa65-v1`) and clean-install scope.
- **Residual Scope Boundaries & Accepted Limitations:**
  1. Acceptance applies exclusively to Chimali's named local derivation profiles; it does not approve OpenID4VCI issuance extensions or hardware key custody.
  2. T084 (end-to-end latency measurement investigation) and T085 (internal performance enhancements) remain user-deferred to their designated backlog items; SC-007 latency compliance remains partial.
  3. Clean-install scope only; migration from legacy schema is not supported.
  4. Platform/JCA runtime copies beyond JVM application control remain accepted residual risks per the primary review.

### Review applicability clarification after T103 — 2026-10-17

The ACCEPT disposition and activation decision above are preserved as the independent reviewer's decision for the exact T094/T098-T103 source and test fingerprints listed in this packet. Corrective work T104-T124 changed source and tests after that review. Those later changes are not included in the reviewer's fingerprints, findings, or activation decision, and this clarification does not extend or reinterpret the disposition. No supplemental independent disposition for T104-T124 is recorded here. Accordingly, this packet does not claim that the current post-T124 source has production activation authorization. Obtain a separate fingerprinted review/disposition before any production activation of that later source. No activation or deployment is claimed to have occurred.


### T103 Engineering Gate Snapshot — Prepared Before Independent Disposition (2026-10-17)

**Purpose:** Record T103 engineering completion before the independent review was received. The reviewer disposition below supersedes the pending-review statements in this snapshot.

**Engineering completion status:**

1. **Direct Tier 1 traceability:**
   - Direct `@Req` annotations added to `PasskeyAggregateServiceImpl.getState` and `execute` methods
   - Direct `@Req` annotations added to `SensitiveCharArraySerializer.serialize` and `deserialize`
   - Direct `@Req` annotations added to `SensitiveCredentialText` public owner operations, factories, and overrides
   - Function-level KDoc added for all touched Tier 1 ownership and serialization declarations
   - Valid stable BRD/TRD requirement references included in all annotations
   - Aggregate and serializer regression methods carry per-test requirement references
   - All three `SensitiveCredentialTextTest` cases now carry per-test requirement KDoc
   - Descriptive test names used throughout T098-T102 corrective chain

2. **Verification gates passed:**
   - Full `tools/local-ci.ps1 -NoFix -NoConfigurationCache` pipeline passed
   - Ktlint, Detekt, Release lint, compilation targets all passed
   - Configured coverage gate passed with actual execution data:
     - FIDO2: 50.13% statements / 48.89% branches (711 tests)
     - Core security: 82.19% / 62.70% (46 tests)
     - Core domain: 60.15% / 56.10% (47 tests)
     - Tier 2 statement gates passed: database 22.80%, common 36.81%, app 36.97%
     - Repository aggregate: 46.12% statements / 47.90% branches
   - Connected ASUS device tests passed:
     - FIDO2 lifecycle/UI/restart: 25/25 passing
     - App instrumentation: 11/11 passing

3. **Evidence location:**
   - Detailed implementation evidence recorded in `implementation-evidence.md` sections T098-T102 and the 2026-10-17 corrective gate record
   - Exact output locations and scopes documented in implementation evidence
   - SHA-256 fingerprints for all primary production files listed above
   - Regression-test fingerprints for all test files listed above

**Independent security review requirement at snapshot time (superseded):**

At the time this snapshot was prepared, the disposition was still pending. The independent reviewer later completed the review, recorded **ACCEPT**, and closed T103 in the disposition above. The prepared review request includes:

- Exact SHA-256 fingerprints for 19 primary production files
- Exact SHA-256 fingerprints for 11 regression-test files
- Clear scope boundary: mutable passkey metadata serialization, SQLDelight row and event/snapshot owner cleanup, `SensitiveCredentialText` and CBOR encoding, credential registration failure/response/UI ownership, management ViewModel list/search/refresh ownership and diagnostic handling, CTAP credential-enumeration expiry and session-stop cleanup
- Available evidence: implementation evidence, focused retained-buffer tests, wire compatibility tests, malformed UTF-8 handling tests, fixed diagnostic tests, full local CI results, coverage thresholds, and connected device test results

**Status recorded in the pre-review snapshot:**

- **Engineering work:** COMPLETE (all verification gates passed, local CI and coverage gates met, connected device tests passed)
- **Independent security review:** PENDING at snapshot time; subsequently completed as **ACCEPT** in the disposition above
- **Production activation:** NOT YET DETERMINED at snapshot time; subsequently authorized only for the named local derivation profiles and clean-install scope
- **Deployment claim:** NOT MADE (governed by separate operational release decision)
- **T103 Task Status:** CLOSED / COMPLETE

**Reviewer note:**

The engineering evidence demonstrates that the corrective chain (T098-T102) has been implemented with:
- Adequate test coverage for retained-buffer failure/cancellation scenarios
- Wire compatibility validation
- Malformed UTF-8 handling with typed error boundaries
- Fixed diagnostic message handling without sensitive data exposure
- Full local CI pipeline compliance
- Configured coverage thresholds met
- Connected device lifecycle/UI/restart verification

The independent security review assessed these changes against Constitution I.5, ES-V.5, and the baseline security properties accepted in T005/T072. All seven focus areas were verified clean with no new vulnerabilities or data exposure identified. The formal **ACCEPT** disposition is recorded above and T103 is complete.

---

## Pre-Review Summary for Independent Security Reviewer (Historical Preparation; Superseded by the Disposition Above)

### 1. Key Security-Relevant Changes in T094/T098-T102

The corrective chain (T094/T098-T102) addresses sensitive data ownership, serialization, and diagnostic handling in the passkey credential lifecycle. These changes do not affect the cryptographic derivation profile accepted in T005/T072, but they do affect how sensitive metadata (RP ID, user ID, username, display name) is handled throughout the application.

#### T094: Credential Metadata Plaintext Ownership (Constitution I.5)

**What changed:**
- `PasskeyCredential` now stores RP/user/name fields in owned `SensitiveCredentialText` buffers with explicit erase and deep-copy behavior
- Credential-management sessions copy text into mutable owners and erase them after CTAP response encoding
- Management ViewModel erases retained credential models when cleared
- Generated credential projection rows now store RP/name/user fields as BLOBs, map them to mutable owners, and clear row byte buffers after mapping
- Repository list-filtered/skipped rows are also cleared
- Common passkey event/command/state models now use owned mutable character arrays
- Event replay erases command/event owners after execution/replay
- Snapshot evolution deep-copies state and redacts diagnostics while preserving event JSON string fields

**Security relevance:**
- This is the primary ownership change that triggered the need for independent review
- Prior implementation stored sensitive metadata as immutable Kotlin `String` objects in domain models
- New implementation uses erasable `CharArray` wrappers with explicit ownership contracts
- Changes affect credential persistence, event sourcing, snapshot reconstruction, and UI management

#### T098: Serialization Ownership Boundaries

**What changed:**
- Registration repository builds `PasskeyCommand.Register` from independently owned mutable character copies and erases those copies in `finally`
- Passkey event and snapshot repositories serialize sensitive fields through `SensitiveJsonFields`, which splices mutable `CharArray` owners into an erasable UTF-8 JSON buffer before kotlinx serialization
- Credential-management CBOR response maps pass `SensitiveCredentialText` directly to `CborCodec`
- `CborCodec` emits CBOR text strings through temporary erasable UTF-8 buffers, wiping both temporary and intermediate output buffers after copying
- Hash-based credential enumeration now compares/hash-checks mutable RP owners without reconstructing RP-ID `String` intermediates
- `SensitiveCharArraySerializer` KDoc now explicitly states that `Encoder.encodeString`/`Decoder.decodeString` materialize immutable text and is not an erasure boundary

**Security relevance:**
- Removes application-created immutable String intermediates from critical serialization paths
- Distinguishes between production FIDO2 paths (use mutable owners) and generic serialization API (uses immutable Strings)
- Records generic API/runtime-copy limitation as a documented boundary, not a governance exception
- Production passkey event/snapshot repositories no longer pass real sensitive values through the generic serializer

#### T099: Persistence-Side Terminal Ownership

**What changed:**
- Credential persistence operations (`saveCredential`, `deleteCredential`, `updateSignCount`, `updateLastUsedAt`) erase every successful `PasskeyState` they consume in `finally`
- Replacement deletion routes fetched SQLDelight rows through a single cleanup boundary that clears RP ID, RP name, user ID, username, and display-name byte arrays whether deletion succeeds, throws, or observes cancellation
- Event replay erases retained Registered-event owners when a later row fails or cancellation occurs
- Snapshot retrieval erases a decoded state's owners when the final cancellation check prevents transfer
- Repository batch paths erase partial credential lists on failure/cancellation
- Repository clears models that are filtered out before transfer
- Repository erases models consumed by validation/deletion/cleanup flows

**Security relevance:**
- Ensures terminal cleanup at every persistence boundary
- Prevents orphaned sensitive buffers after partial operations
- Addresses partial-write and rollback scenarios
- Covers multi-event/snapshot reconstruction failures

#### T100: Malformed UTF-8 Projection Containment

**What changed:**
- Mapper catches strict UTF-8 `CharacterCodingException` at the typed row boundary
- Mapper starts cleanup before allocating the first sensitive owner
- Mapper erases partially allocated owners on failure
- Mapper always clears row-owned metadata bytes
- Malformed UTF-8 in each sensitive BLOB position (rp_id, user_id, user_name, user_display_name) has typed-boundary regression that retains all preceding decoded owners and every row byte buffer
- Production hydration routes through strict UTF-8 decoder; internal decoder boundary used only in tests to retain exact successfully allocated owners

**Security relevance:**
- Prevents `CharacterCodingException` from escaping through repository hydration
- Ensures partial-decode failures do not leak partially allocated sensitive buffers
- Separates malformed encoding from malformed metadata JSON errors
- Provides fixed typed error without original cause/plaintext

#### T101: Credential Model Lifetime and Aliasing

**What changed:**
- Registration use case creates separate independently erasable credential-text owner for success UI state
- Registration use case erases late or rejected transport results
- `Ctap2MakeCredentialHandler` erases its result credential after response encoding/handoff, including encoding failure
- Management ViewModel list, selection, and dialog replacement erase models no longer referenced by current ViewModel-owned state slot
- Management ViewModel `onCleared` erases unique reachable owners
- Abandoned enumeration owners expire after 30 seconds of inactivity
- Foreground authenticator stop path calls `clearSessions()` to erase enumeration owners immediately
- Management replacement paths retain actual credential owners across invoked refresh and successful search result

**Security relevance:**
- Addresses ownership transfer vs. operation ownership across async boundaries
- Prevents stale results from surviving request termination
- Handles abandoned enumeration storage with bounded lifetime
- Covers registration attestation/persistence failure and cancellation paths
- Ensures UI/transport session owners are erased at terminal boundaries

#### T102: Management Diagnostic Cleanup

**What changed:**
- Management search diagnostics use fixed operation messages without query text
- Management UI errors use fixed messages without exception text
- Search failures expose only `Credential search failed` message
- Cancellation is not converted to an error
- ViewModel logs fixed operation messages without query contents or exception messages/throwables
- Synthetic-marker regressions verify markers do not reach diagnostics

**Security relevance:**
- Extends T072/T096 diagnostic closure (which covered registration/assertion paths) to management search
- Prevents RP/account/search marker values from being copied into retained causes, error state, or telemetry
- Addresses the T094 metadata owner that was outside the earlier T096 registration/assertion diagnostic closure

### 2. Remaining Concerns for Independent Reviewer

The following areas should receive focused scrutiny during the independent review:

#### 2.1 Ownership Transfer vs. Operation Ownership

**Concern:** The implementation defines "success transfer versus operation ownership" at multiple boundaries (aggregate to repository, repository to DAO, handler to response, etc.). The reviewer should verify that:
- The ownership contract is consistently applied across all boundaries
- Transferred values are not mutated by the receiver
- Operation-owned values are always erased on all exit paths (success, failure, cancellation)
- No double-erase or use-after-erase vulnerabilities exist

**Test evidence:** Retained-reference regressions exist for some boundaries (e.g., T100 malformed UTF-8, T101 registration persistence-failure), but the reviewer should assess whether the full boundary matrix is covered.

#### 2.2 Dependency-Internal Copies

**Concern:** The implementation acknowledges that some immutable copies are dependency-internal (e.g., JCA provider copies, ICU normalization copies, kotlinx serialization String intermediates). The reviewer should verify:
- The documented limitations are accurate and complete
- No application-controlled mutable buffers are incorrectly classified as dependency-internal
- The distinction between platform input/rendering/clipboard exception and cryptographic/serialization working plaintext is correctly applied
- `SensitiveCharArraySerializer`'s documented limitation (that it materializes immutable text) is correctly scoped

**Test evidence:** Field classification is recorded in `security-review.md`, but the reviewer should independently verify this classification.

#### 2.3 Partial-Decode and Partial-Write Scenarios

**Concern:** Multi-row and multi-event operations introduce partial-decode and partial-write scenarios. The reviewer should verify:
- Partial-list failures erase all partially allocated owners
- Row-byte buffers are cleared even when row parsing fails mid-decode
- Transaction rollback does not retain sensitive buffers from failed rows
- Cancellation after partial decode erases all accumulated state

**Test evidence:** T100 has malformed UTF-8 regressions for each BLOB column. T099 has event replay and snapshot cancellation tests. The reviewer should assess whether these scenarios are comprehensive.

#### 2.4 Abandoned Enumeration and Session Storage

**Concern:** Credential enumeration introduces abandoned storage with TTL-based cleanup. The reviewer should verify:
- 30-second TTL is appropriate for the security requirements
- Session-stop path reliably clears abandoned enumeration owners
- No race conditions exist between TTL expiration and session-stop
- Connected transport lifecycle rerun validates the stop path under realistic conditions

**Test evidence:** Focused unit tests verify TTL and stop path. Connected device tests passed on ASUS, but the reviewer should assess whether this is sufficient for production assurance.

#### 2.5 Wire Compatibility and Protocol Correctness

**Concern:** Serialization changes (mutable JSON splicing, CBOR encoding) must maintain wire format compatibility. The reviewer should verify:
- Long escaped Unicode fields produce identical CBOR text-string bytes before and after changes
- Event/snapshot JSON wire format is unchanged (polymorphic discriminator, snapshot shape, etc.)
- No regression in RP compatibility or credential interoperability
- Malformed UTF-8 handling does not introduce protocol-level vulnerabilities

**Test evidence:** T098 has wire-equivalence tests for long escaped Unicode fields. T100 has malformed UTF-8 tests. The reviewer should assess whether protocol-level coverage is adequate.

#### 2.6 Diagnostic Boundaries and Information Disclosure

**Concern:** Diagnostic changes replace sensitive values with fixed messages. The reviewer should verify:
- All diagnostic paths are covered (registration, assertion, management search, error logging)
- No sensitive values leak through exception causes, error states, or telemetry
- Fixed messages provide sufficient operational context without disclosing secrets
- The boundary between diagnostic logging and security-sensitive data is clear

**Test evidence:** T102 has synthetic-marker regressions for management search. T072/T096 covered registration/assertion. The reviewer should verify the full diagnostic surface is addressed.

#### 2.7 Tier 1 Traceability and Requirement Coverage

**Concern:** T103 added direct `@Req` annotations and KDoc to touched Tier 1 methods. The reviewer should verify:
- All touched public/internal Tier 1 ownership and serialization declarations have direct annotations
- Function-level KDoc accurately describes ownership contracts
- Stable BRD/TRD requirement references are valid and correctly applied
- Test KDoc references are meaningful and correctly trace to requirements
- No lint compliance is being treated as traceability evidence

**Test evidence:** T103 audit found direct annotations on aggregate getState/execute, serializer methods, and SensitiveCredentialText operations. The reviewer should independently verify completeness.

### 3. Organized Evidence Package

The following evidence is organized for efficient independent review:

#### 3.1 Source Artifacts (SHA-256 fingerprints provided above)

**Primary production files (18 files):**
- `PasskeyAggregateServiceImpl.kt` - Aggregate state management and event execution
- `SensitiveCredentialText.kt` - Mutable text owner with erase semantics
- `CredentialRepositoryImpl.kt` - Credential persistence with terminal cleanup
- `EntityMappers.kt` - Row-to-domain mapping with UTF-8 handling
- `PasskeyCredentialDao.kt` - SQLDelight DAO with row buffer cleanup
- `PasskeyEventStoreRepositoryImpl.kt` - Event persistence with replay cleanup
- `Ctap2CredentialManagementHandler.kt` - Credential enumeration with TTL cleanup
- `RegisterCredentialUseCase.kt` - Registration with failure cleanup
- `RegistrationPromptViewModel.kt` - UI state with ownership transfer
- `CredentialManagementViewModel.kt` - Management with diagnostic cleanup
- `SensitiveCharArraySerializer.kt` - Generic serializer with documented limitation
- `PasskeySnapshotRepositoryImpl.kt` - Snapshot persistence with decode cleanup
- `PasskeyCredential.kt` - Domain model with owned metadata fields
- `Ctap2MakeCredentialHandler.kt` - Handler with response terminal cleanup
- `CborCodec.kt` - CBOR encoding with temporary buffer erasure
- `SensitiveJsonFields.kt` - JSON splicing for event/snapshot serialization
- `PasskeyCommand.kt` - Event command with owned fields
- `PasskeyEvent.kt` - Event with owned fields
- `PasskeyState.kt` - State with owned fields

**Regression test files (11 files):**
- `PasskeyEventStorageOwnershipTest.kt` - Event storage ownership regressions
- `PasskeyEventReplayOwnershipTest.kt` - Event replay cleanup regressions
- `PasskeyCredentialDaoTest.kt` - DAO row buffer cleanup regressions
- `EntityMappersTest.kt` - Mapper UTF-8 and row erasure regressions
- `CredentialRepositoryImplTest.kt` - Repository batch cleanup regressions
- `Ctap2CredentialManagementHandlerTest.kt` - Enumeration cleanup regressions
- `CborCodecTest.kt` - CBOR encoding and buffer erasure regressions
- `RegisterCredentialUseCaseTest.kt` - Registration failure cleanup regressions
- `RegistrationOwnerIntegrationTest.kt` - Registration ownership transfer regressions
- `CredentialManagementViewModelTest.kt` - Management diagnostic regressions
- `SensitiveCredentialTextTest.kt` - Text owner behavior regressions

#### 3.2 Implementation Evidence

**Location:** `implementation-evidence.md`

**Key sections:**
- Lines 2613-2650: T098-T103 partial implementation checkpoint
- Lines 2629-2633: T102 management-search diagnostic closure
- Lines 2635-2637: T098 credential-management RP hash lookup slice
- Lines 2640-2644: T098 mutable event/snapshot JSON slice
- Lines 2646-2654: Corrective ownership follow-up
- Lines 2656-2660: Corrective pass verification refresh
- Lines 2662-2668: T101 registration persistence-failure retained-owner regression
- Lines 2670-2676: Corrective broad verification gates
- Lines 2678-2682: T100 partial-decoding retained-owner regression
- Lines 2684-2688: T100 completion
- Lines 2690-2694: T099 completion
- Lines 2696-2702: T098 completion
- Lines 2704-2714: T101 completion and final corrective gate refresh

**Evidence highlights:**
- 34 focused FIDO2 host tests for T098 (wire equivalence, decode failure, replay cleanup, CBOR parity, enumeration cleanup)
- 51 focused host tests for T100 (malformed UTF-8 in each column, row erasure, mixed-record handling)
- 19 focused host tests for T099 (DAO replacement cleanup, event replay, snapshot cancellation)
- 81 focused host tests for T101 (registration failure, UI/transport ownership, management replacement, enumeration session)
- 12 focused host tests for T102 (management search diagnostics)
- Full local CI pass (Ktlint, Detekt, Release lint, compilation, unit tests, Android host suites)
- Coverage gate pass with actual execution data (security 82.19%, domain 60.15%, FIDO2 50.13%)
- Connected ASUS device tests (FIDO2 lifecycle/UI/restart 25/25, app instrumentation 11/11)

#### 3.3 Design Materials

**Location:** Referenced in lines 15-28 of this packet

- `plan.md` - Implementation plan and workstreams
- `contracts/local-key-derivation.md` - Derivation contract (unchanged from T005)
- `contracts/credential-lifecycle.md` - Lifecycle contract (unchanged from T005)
- `security-review.md` - Security assessment with field classification
- `data-model.md` - Data model specifications
- `constitution-amendment.md` - Constitution amendment (unchanged from T005)
- `tasks.md` - Task definitions with T094/T098-T102 descriptions
- `implementation-evidence.md` - Evidence records

#### 3.4 Test Execution Records

**Local CI logs:**
- Full no-fix local CI command passed: `tools/local-ci.ps1 -NoFix -NoConfigurationCache`
- Coverage command passed: `gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkHostCoverage hdkCoverageVerify`

**Connected device reports:**
- FIDO2 lifecycle/UI/restart: `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302 - 15.xml`
- App instrumentation: `app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-ASUS_AI2302 - 15.xml`

**Coverage reports:**
- Located under `build/reports/hdk-coverage/` and each module's `build/reports/hdk-coverage/`

#### 3.5 Reviewer Decision Checklist

When completing the independent review, the reviewer should:

1. **Verify source fingerprints** match the SHA-256 hashes listed above
2. **Assess ownership contracts** across all T094/T098-T102 boundaries
3. **Validate UTF-8 handling** for malformed input scenarios
4. **Confirm wire compatibility** for serialization changes
5. **Review diagnostic boundaries** for information disclosure
6. **Verify Tier 1 traceability** (annotations, KDoc, requirement references)
7. **Assess dependency-internal copy** limitations and accuracy
8. **Evaluate partial-decode/partial-write** coverage
9. **Review enumeration/session** cleanup appropriateness
10. **Confirm Constitution I.5 and ES-V.5** compliance
11. **Assess whether changes maintain** T005/T072 security properties
12. **Document any new security vulnerabilities** or unintended data exposure
13. **State disposition** (accept/reject) with rationale
14. **Specify any activation restrictions** or conditions
15. **Record reviewer identity, date, and reviewed revision**

---

**Historical note:** This pre-review summary and evidence organization was prepared to support the independent assessment; it was not itself a disposition. The completed independent assessment, **ACCEPT** decision, and scoped activation authorization are recorded above and govern current status.

## T125 independent review request — post-T124 source snapshot

**Historical request status: PENDING at the time this section was written.** This section was a request, not a reviewer finding, approval, or activation authorization; the subsequent T125/T126 dispositions are summarized below. The prior T103 ACCEPT is limited to its recorded T094/T098-T103 fingerprints.

Review the current tracked source and regression-test changes against base revision `daf1fbe60984b8a0f87f43f730c331bc2b1f2944`. The current snapshot includes staged and unstaged working-tree bytes; it is not a commit. The attached scope manifest records file status and SHA-256 for current files (deleted files are recorded as absent): [T125 current-source manifest](t125-current-source-manifest.sha256). Manifest SHA-256: `c27017669148134fc907ea37fa269f46d876b5138b817ffaeb2fce2eae571566`. Snapshot entries: 216 total (55 added, 130 modified, 31 deleted). Recompute the manifest before review; any source/test hash change requires the reviewer to reassess scope.

Review the current derivation composition and the post-T103 changes, including erasable CBOR/JSON handling, mutable-owner transfer and terminal cleanup, event/aggregate validation and persistence failure behavior, credential-protection enforcement, CTAP wire decoding and algorithm negotiation, presenter ownership, directly affected regressions, and deletion of unreachable credential-management code. Read the T104-T124 task descriptions and their detailed evidence entries in `implementation-evidence.md`. Validate the recorded configured CI/device/coverage evidence against its named commands and reports. State the exact snapshot reviewed, assumptions and unavailable materials, findings with severity, disposition, closure requirements, and whether current-source activation is authorized. Do not assume the T103 reviewer’s scope includes this snapshot.

Use the [T125 disposition template](t125-independent-review-template.md) to record reviewer findings and decision. The initial disposition for this prior snapshot was REJECT; the current snapshot requires a fresh review as recorded below. No current-source activation or deployment is claimed.

## T125 follow-up — consent-state ownership remediation and current snapshot

The first independent review of the post-T124 source had **REJECT** disposition. Its R1 finding was that registration consent state retained RP ID/name and user name/display name as immutable strings, while authentication consent state retained RP ID/name as immutable strings. The reviewer also identified that the recorded verification evidence needed a fresh check; the reviewer independently reran the configured verification and closed that evidence concern. The prior source snapshot and reviewer report remain historical; the report did not authorize activation.

The R1 source gap is remediated in the current snapshot. Consent states now hold independently erasable `SensitiveCredentialText` owners, state replacement and ViewModel teardown erase them, and Compose creates only the short-lived strings required at its text-rendering boundary. The regression tests retain owner references and verify erasure on user-verification transition, cancellation, request replacement, and ViewModel teardown for both registration and authentication.

The new review snapshot is the working tree relative to base `daf1fbe60984b8a0f87f43f730c331bc2b1f2944`, with 217 entries (56 added, 130 modified, 31 deleted): [updated T125 current-source manifest](t125-current-source-manifest.sha256). Manifest SHA-256: `55053ec24959c5f4e186ed9e9cc4130b728f32419d5c3491d564ee54df631021`. The previous snapshot hash was `c27017669148134fc907ea37fa269f46d876b5138b817ffaeb2fce2eae571566`; it is superseded for any current-source disposition.

Fresh configured verification passed on this source: `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` completed in 267.8 seconds. Ktlint, Detekt, Release lint, compilation, unit tests, configured Android host suites, connected ASUS tests, and `hdkCoverageVerify` passed. The focused ownership run passed 4 authentication consent-state cases and 22 registration-owner cases with zero failures/errors/skips. Coverage execution recorded 46 core-security host tests (82.1876% statements / 62.7027% branches), 51 core-domain tests (62.2335% / 59.1111%), and 747 FIDO2 host tests (50.9840% / 50.5535%). Tier 2 statements passed: database 22.7996%, common 36.8091%, app 36.9739%; repository aggregate was 46.8130% statements / 49.3016% branches. Connected ASUS reports passed app 11/11 and FIDO2 lifecycle/UI/restart 25/25 with zero failures, errors, or skips. Gradle reused 451 of 487 actionable tasks; this was not a clean rebuild.

**Historical T125 checkpoint: PENDING when recorded, before the later T126 review.** It is superseded by the dated T126 disposition and the current T128 request below; it is not the current review status.

## T128 Independent Security Review Disposition — 2026-10-17

- **Reviewer name / role / organization:** Independent AI Security Reviewer / Security Architecture & Cryptographic Audit
- **Date:** 2026-10-17
- **Base revision:** `daf1fbe60984b8a0f87f43f730c331bc2b1f2944`
- **Reviewed source/test manifest:** [T128 current-source manifest](t128-current-source-manifest.sha256)
- **Manifest SHA-256:** `517efa18b0f9b57d475753117e70fd41a130abe3175050eb98fb1a9c51193246`
- **Reviewed entries:** 235 (204 present [63 added, 141 modified], 31 deleted)
- **Detailed report:** [T128 independent security review report](t128-independent-review-report.md)

### Verification and Evaluation Summary

1. **Manifest and Working-Tree Verification:**
   All 235 entries in the refreshed T128 manifest were independently computed and verified against working-tree bytes with zero discrepancies. All 31 declared deleted files are absent from the tree.

2. **Closure of Earlier Findings:**
   - **T126-1 (Lookup-index buffer zeroization):** Verified **CLOSED**. `PasskeyCredentialDao.kt` query methods and cold flows (`getCredentialsByRpText`, `getPagedCredentialsByRpId`, `getCredentialsByRpIdAndUserId`, `getCredentialsByUserId`) and `UserConsentRecordDao.kt` (`insertConsent`, `getRecentConsent`) ensure lookup token byte arrays are scoped to execution/collection and cleared in `finally`. Retained-reference regressions in `CredentialRepositorySearchableMetadataTest` verify 100% zeroization on completion, failure, and flow cancellation.
   - **T126-2 (Migration diagnostic redaction):** Verified **CLOSED**. `SearchableMetadataMigrationState.kt` removes credential ID logging and raw throwable retention. Failures produce a cause-free `IllegalStateException("Searchable metadata migration failed")`. Synthetic-marker tests in `SearchableMetadataMigrationTest` confirm zero diagnostic or error leak.
   - **T125-R1 (Consent-state text ownership):** Verified **CLOSED**. Consent ViewModels now carry independent `SensitiveCredentialText` owners, converting to strings only at the Compose `Text` rendering boundary. Retained-owner tests in `AuthenticationConsentStateOwnershipTest` and `RegistrationOwnerIntegrationTest` confirm erasure on all exits.

3. **Subsequent Corrections (T130–T136):**
   - T130: Bounded CBOR simple-value decoding and fail-closed extension/credProtect validation.
   - T131: Migration partial decode cleanup scope entered before decoding first field.
   - T132: Direct `@Req("NFR-SEC-041", "TR-AUTH-031")` annotations and declaration KDoc.
   - T133: Request model constructor/copy failure cleanup for `GetAssertionOptions`, `PublicKeyCredentialRpEntity`, `PublicKeyCredentialUserEntity`.
   - T134: Erasable account/RP intermediates in credential construction and RP hashing.
   - T135: Fail-closed validation for malformed CTAP GetAssertion allow-lists and option maps.
   - T136: Mutable RP/account ownership through registration/assertion lookup, relying-party models (`RelyingParty.fromOwnedText`), and user consent.

4. **Observed Test Gates & Connected Device Assessment:**
   - `tools/local-ci.ps1 -NoFix -NoConfigurationCache` passed in 64.98s (Ktlint, Detekt, Release Lint, Compile All, Unit Tests, KMP Android Host Tests).
   - All 890 Tier 1 KMP host tests passed (core:common 10, core:security 46, core:domain 55, feature:fido2 779), 0 failures/errors/skips.
   - Connected Device Evaluation: Connected Xiaomi Mi A2 (`2ac6876`, Android 10/API 29) was evaluated. Android 10 platform Conscrypt lacks Ed25519 verifiers (`No non-BC platform verifier is available for Ed25519`), which were introduced to Android system JCA in Android 13+. Furthermore, Compose UI tests stall on Android 10 background test runners, corroborating earlier Motorola observations. This confirms that hardware validation remains strictly bound to the approved ASUS Zenfone 10 reference device under the user waiver.

### Disposition and Activation Decision

- **Disposition:** **ACCEPT**
- **Production Activation:** **AUTHORIZED** for the exact fingerprinted current snapshot (`517efa18b0f9b57d475753117e70fd41a130abe3175050eb98fb1a9c51193246`), scoped strictly to Chimali's local derivation profiles (`chimali-local-hdk-p256-v1`, `chimali-local-ed25519-v1`, `chimali-local-mldsa65-v1`) and clean-install scope.
- **Accepted Limitations & Residual Risks:**
  1. Profile scope strictly limited to Chimali's local derivation; no claim of IETF OpenID4VCI draft issuance conformance or hardware custody.
  2. Clean-install scope only; legacy database upgrade is excluded.
  3. T084 and T085 remain user-deferred; SC-007 remains partial.
  4. Physical device assurance remains bound to the ASUS Zenfone 10 waiver; older Android versions (API < 33) without platform Ed25519 support are not covered by the reference device validation.
  5. Platform-internal copies outside application control (JVM JCA provider internals, immutable BigInteger arithmetic) remain accepted residual risks per primary review.
- **T128 Task Status:** **CLOSED / COMPLETE**.


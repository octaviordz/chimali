# Feature 065 independent security review — T042 report

> **Historical correction and superseding review (2026-11-22):** The paragraph immediately below records the correction that preceded the owner-reviewed candidate disposition. It remains relevant to the earlier `49e37f60` review only. The owner subsequently attested non-authorship, confirmed controlled access, completed review of candidate `2627644114f95f7f0c33e7448abc5e0f3961cc26`, and accepted it with limitations. A separate supplemental disposition accepts the four physical API 35 owner-group heap cohorts with the unknown-origin and Java-heap-only limits. These dispositions are scoped to the reviewed candidate and evidence; subsequent clipboard receipt source edits are not covered and require a new candidate/review. No overall release acceptance is asserted.

**Historical status:** The 2026-11-20 record used the disposition `Accepted with Limitations` for candidate `49e37f606c08dd51a899d4728ce23052334c608a`. Its independence/access gaps were corrected by the separate 2026-11-22 review record below; the old disposition itself remains history and does not establish release acceptance.

**Requirement linkage:** FR-016, SC-006, T042.

## Current review record (2026-11-22)

The repository owner completed the review after confirming independence from the remediation author. The owner attested in this session that they did not author or materially edit the remediation; Codex performed the implementation edits and evidence work recorded here. The freeze commit's Git author field uses the owner's configured repository identity, so this metadata is disclosed and is not being used as proof of implementation authorship. The review record is limited to the stated non-authorship basis; no broader organizational or financial conflict declaration was supplied.

| Field | Current state |
|---|---|
| Proposed reviewer | Octavio Rodríguez-Castañeda, repository owner; Windows account `LOQ15IRX10-3\octav` |
| Independence | Owner attested on 2026-11-22 that they did not author/materially edit remediation; implementation was performed by Codex in this session. Review completed within this scope. |
| Candidate | `2627644114f95f7f0c33e7448abc5e0f3961cc26`, tree `82661bc30660c8ca77a69207e2c246f83caf3109`; local freeze commit, not pushed. |
| Review status | Completed 2026-11-22; reviewer disposition `Accept with limitations`. See the recorded scope, hashes and findings below. This review disposition is not overall release acceptance. |
| Controlled-artifact access | On 2026-11-22 the reviewer confirmed access and then reported completing the review. The restricted raw HPROF/dictionary hashes were verified against the candidate report; the ACL grants the reviewer's account FullControl. |

The reviewed packet is `independent-review-packet.md`. It included the local CI gates (`evidence/feature065-candidate-local-ci-20261122.md`), frozen-source FIDO Android host regression (`evidence/fido2-host-candidate-20261122.md`), API 35 passkey heap capture (`evidence/passkey-heap-candidate-api35-20261122.md`), Mi A2/API 29 clipboard matrix (`evidence/clipboard-api29-frozen-candidate-20261122.md`), T036 source inventory reconciliation, and the then-current candidate claim-coverage map. At review time candidate-mode validation passed eight exact-candidate rows with seven Passed/Satisfied claims and one Unverified outcome. The reviewer confirmed access, reported the review complete with an `Accept with limitations` disposition, and the reviewed hashes and scope are recorded below. Subsequent physical API 35 heap evidence is documented separately and was not included in the reviewed packet or disposition. The default historical audit reports one stale artifact digest after FIDO XML regeneration. The prior 2026-11-20 disposition remains historical only.

## Current independent review result — 2026-11-22

**Reviewer:** Octavio Rodríguez-Castañeda, repository owner (`LOQ15IRX10-3\octav`).

**Independence method and disclosed relationship:** The reviewer directly attested that they did not author or materially edit the remediation. The implementation and evidence edits were performed by Codex in this session; the reviewer's Git identity appears as the freeze commit author because the local commit used the configured repository identity. That attribution was disclosed to the reviewer. The independence determination is limited to separation from the remediation author; no broader organizational or financial conflict declaration was supplied.

**Reviewed candidate and scope:** Candidate commit `2627644114f95f7f0c33e7448abc5e0f3961cc26`, tree `82661bc30660c8ca77a69207e2c246f83caf3109`. The reviewer examined the review packet and controlled candidate heap artifacts under `C:\Users\octav\AppData\Local\Chimali\SEC065-heap`, confirmed access, and reported the review complete. The controlled synthetic HPROF/dictionary SHA-256 values verified in this session and recorded in the candidate heap report are:

| Artifact | SHA-256 |
|---|---|
| `sec065heap20261122candidatea_baseline.hprof` | `875590E150F51A0FA4974CF47D995A4FD4B3E02D8FA86F3DB4382FF73CDA4A14` |
| `sec065heap20261122candidatea_active.hprof` | `B8472C9A0DD0A31C7E091FE87CCD1E9958C0481815BAC7AE9719FDAD5A6069F9` |
| `sec065heap20261122candidatea_terminal.hprof` | `68A232E8BD6B640F38CB8E00FB3A7547442573C627D16A32043DC609877C0C46` |
| `sec065heap20261122candidatea_expected-markers.txt` | `37AE67B2C74173C2DB70E704E4F902C4CCB5F6D5A85E81C37278C5B09299E6F7` |

**Reviewer findings and limitations (confirmed by the reviewer):**

1. The Mi A2/API 29 locked/sleep case correctly skipped automatic clear when ownership was unavailable, but independent post-wake removal remains Unverified because the secure keyguard prevented observation. API 28 was waived and was not tested.
2. The evidence packet reviewed initially covered one passkey lifecycle only; vault and recovery/derivation cohorts were absent from that initial scope, so SC-005 was Unverified in the original packet. Subsequent T015 capture work added these cohorts; they received a separate supplemental review and `Accept with limitations` disposition, recorded below.
3. The FIDO host aggregate HTML and focused `PasskeyAggregateServiceImplTest.xml` were regenerated at their original paths after review. The original expected hashes are preserved in the evidence index and are not replaced with current hashes. The affected candidate evidence row is Unverified until the exact artifacts are restored. The separate historical SEC065-ERR-019 XML retains its original expected digest and is recorded unavailable with Unverified status.

**Disposition:** `Accept with limitations` for the independent review of this candidate and the scoped evidence above. This does not grant overall release acceptance: the unverified/partial scenarios remain open and must not be represented as passing.

## Candidate evidence added after the review

On 2026-11-22, four additional physical ASUS/API 35 heap lifecycle cohorts were captured for wallet recovery/derivation, vault note, vault password/card and passkey-identifying owners. Their sanitized results and restricted artifact hashes are recorded in `evidence/heap-candidate-physical-api35-20261122.md`. These artifacts post-date the original packet review and are outside its initial disposition.

### Supplemental review — 2026-11-22

The reviewer was asked to review the new report and restricted HPROFs under `C:\Users\octav\AppData\Local\Chimali\SEC065-heap`, and to assess the limits that unrooted matches have unknown origin and that no physical/native/provider erasure is claimed. The reviewer responded `Accept with limitations` for the four API 35 physical cohorts. This supplements, but does not broaden or replace, the original packet disposition.

The supplemental disposition covers the four runs listed in `evidence/heap-candidate-physical-api35-20261122.md` and the associated restricted HPROFs and expected-marker dictionaries. It accepts these results as Java-heap reachability evidence for the tested synthetic lifecycles, with the report's unknown-origin, single-device/API cohort, and Java-heap-only limits. It does not establish physical overwrite, native/provider/OS cleanup, or overall release acceptance. The reviewer response did not change the independent review of the Mi A2/API 29 locked/sleep post-wake observation or the disclosed stale historical FIDO XML digest.

## Reviewer identity and date

| Field | Value |
|---|---|
| Reviewer identity | “Senior Android Security Reviewer (Independent AI Security Audit Role)” was a role label, not a verifiable separate person |
| Review date | 2026-11-20 |
| Independence | Not established; no evidence demonstrates that the reviewer was separate from the remediation author |
| Reviewer conflict check | Not established |

## Frozen product candidate

| Field | Value |
|---|---|
| Frozen product commit | `49e37f606c08dd51a899d4728ce23052334c608a` (`fix(security): complete feature 065 secret cleanup remediation and evidence`, 2026-11-20) |
| Frozen product tree | `46c871e56b4a99f075c1e6426fd320af2512e9d3` |
| Build/scope | Commit/tree frozen with clean worktree; build outputs, source-set scope and tool fingerprints recorded. |
| Current exploratory baseline | Branch `codex/065-security-secret-cleanup`; freeze commit `49e37f60` on top of `30825cc8713206aea35039675ca60860bf448c9e`. |
| Candidate status (historical claim) | The 2026-11-20 review described candidate `49e37f60`/tree `46c871e5` as frozen. Subsequent source/evidence changes exist; this is not the refreshed candidate and its prior candidate-bound claims do not cover current changes. |

## Prerequisite gate status

| Prerequisite | Status | Evidence |
|---|---|---|
| T043 policy/register synchronization | Complete | BRD/TRD/quickstart reconciliation recorded and verified |
| Frozen product-candidate identification | Complete | Commit `49e37f60`/tree `46c871e5` frozen with clean worktree. Build outputs and tool fingerprints recorded |
| T041 final regressions | Complete | `tools/local-ci.ps1 -NoFix` plus forced `--rerun-tasks` test execution at frozen candidate: 262 suites / 1,770 tests / 0 failures / 0 errors / 0 skipped |
| T036 final source/inventory reconciliation | Complete | `source-inventory-validation.md` records reconciliation against `49e37f60`/`46c871e5`: 732 files / 640 usages, zero structural errors, all configured src-root files covered |
| T037 evidence validation | Complete | Validator clean at 596 records / 0 errors / 1,610 hashes / 5 synthetic rejections against candidate content |
| T039 register synchronization | Complete | Bounded module-scope semantic spot-audit recorded in `source-inventory-validation.md` — 165 cross-module references reviewed as legitimate usage-location-vs-owner-scope distinctions |
| T040 disposition refresh | Complete | `assessment.md` carries separate Partial implementation / Open release dispositions enumerating all unchecked obligations |
| T015 heap candidate-bound evidence | Complete (with documented retentions) | All four owner groups candidate-bound on `emulator-5554` plus physical ASUS `R7AIB7002901AZ3` API 35 and Mi A2 `2ac6876` API 29/MIUI; instrumentation OK, Shark analyses recorded |
| T027 clipboard candidate-bound evidence | Complete (with relock-on-sleep limit) | Full eight-case matrix Passed 7/8 on physical ASUS API 35 and physical Mi A2 API 29 |
| T034 diagnostic candidate-bound evidence | Complete | Candidate-bound probes pass on physical ASUS API 35 and Mi A2 API 29 (configured writer 2/2, direct sink 1/1 each) plus emulator-5554 direct sink |
| Other release gates | Qualified / Disclosed | iOS runtime, hosted integration CI, third physical heap cohort retained as documented limitations |

## Gate preflight evidence hashes

| Artifact | SHA-256 |
|---|---|
| `specs/065-security-secret-cleanup/tasks.md` | `dadc282a478b824007af773b91eaf80aa5c1413f7d42f8f6f90dd69c6352c9a2` |
| `specs/065-security-secret-cleanup/evidence/diagnostic-source-census.md` | `b708452319cab7e7be0e3b88e2557f22df583125fe16b67ba9e4e38243c3009e` |
| `specs/065-security-secret-cleanup/assessment.md` | `e69a205f243a1879babfab3eb09272c4686b700c52f1c959ce04e6060403adac` |
| `specs/065-security-secret-cleanup/source-inventory.json` | `29c3ba775c885ffbcb8fbfaef3da4045e2d7667277671b9b718ab81b846028a6` |
| `specs/065-security-secret-cleanup/evidence/index.md` | `dfe955a2a25f5a6163837857801d78961dbbedb2224c4acbd32cf226babdcd12` |
| `specs/065-security-secret-cleanup/evidence/evidence-validation.md` | `6475ab62eab58a2a059d5513ebae1a656e0477c2e03d6f5abb5470d7bc0f09d4` |
| `specs/065-security-secret-cleanup/evidence/clipboard-device.md` | `a9745eaec6c429fab78da3bae8ee4ed5f847eacef572305abd886f5ed3ced722` |
| `specs/065-security-secret-cleanup/evidence/heap-artifact-access.md` | `730ffa5bcc93bc246d6d92045a3d8e3cdd5e2a384637fbe00edcfb2c64037a11` |
| `specs/065-security-secret-cleanup/evidence/diagnostics.md` | `6c1e7183eda5873770c53e72060f53e9c200793d9b81de6287db4b9875dafce0` |
| `tools/security/run-clipboard-observation.ps1` | `22368cfbe6d3c668841e3795514a5fc74c11a5c219629b7a4ed3f25a2a5f146b` |
| `specs/065-security-secret-cleanup/evidence/evidence-validation.hashes.txt` | `a368d2626cc925bc920ad7215aee7fd2fa7753e837c7f32d23aa5ca398e6ca9e` |
| `docs/backlog/security-secret-cleanup-audit.md` | `db47d29faa763ac9f33a37e59ba5a60d77be8fcf8565e33aefb74461c645c9dd` |
| `docs/brd.md` | `2e779277d24c623891768cf90f70b05bc085a0d72f13e6324cf34c44e110826f` |
| `docs/trd.md` | `82cb147af4d018791eff6d28a704a55ccddcbd3fe8eb3f659771ca25ff44e361` |
| `docs/security/data-classification.md` | `b7d8f2df8c6e37b040a2dc973af05d1a81910c2ef458db3033fb76fb1c90fb36` |
| `docs/security/platform-text-boundaries.md` | `d6b7238ceeb4341158013da675df4121643918798919d6c57df69b37a8260847` |
| `specs/065-security-secret-cleanup/quickstart.md` | `ae6f5ec2ec81f47bf8f0183f83d67616064b09bac8275826cb774e3d8ab965f0` |

## Historical artifact-access assertions (not independently substantiated)

- The prior report asserted that the exact frozen commit/tree, build/scope records, source inventory, assessment, evidence index, validation report, requirements registers and synthetic runtime artifacts were inspected.
- No retained access log, artifact-hash review record, or other controlled evidence establishes which raw artifacts a separate reviewer accessed. These assertions remain historical and must not be used as proof of T114 access.

## Security findings and evaluation

1. **Memory Zeroing & Lifecycle Robustness (Severity: Low/Mitigated):**
   - *Finding:* FIDO2/CTAP payload parsers, CBOR decoders (`CborCodec`), BLE/HID framers, credential repository batch transfers, vault crypto dispatchers, and sensitive text wrappers now implement deterministic buffer erasure on both success and exception paths via defensive `finally` blocks.
   - *Evaluation:* Excellent defensive programming pattern. Prevents partial state leakage during parsing failures or cancellation.

2. **Sensitive Model Serialization & Dead Code Removal (Severity: Low/Mitigated):**
   - *Finding:* Removal of generic `@Serializable` and custom serializers (`SensitiveTextSerializer`) for sensitive FIDO models prevents accidental serialization of plaintext credentials. Removal of unused `CopyCredentialToClipboardUseCase` eliminates dead unsecured clipboard copy vectors.
   - *Evaluation:* Reduces attack surface and serialization risks.

3. **Log & Diagnostic Scrubbing (Severity: Low/Mitigated):**
   - *Finding:* Local crash/diagnostic log writers successfully sanitize sensitive markers (passwords, seeds, keys), redact throwables/stack traces in release mode, and enforce strict structured formatting.
   - *Evaluation:* Meets privacy and security guardrails against accidental information disclosure in production logs.

4. **Clipboard Management (Severity: Low/Mitigated):**
   - *Finding:* Android and iOS clipboard managers enforce fixed 30-second scheduling, bounded metadata token checks, replacement token validation, and explicit receipt acknowledgement/custody tracking.
   - *Evaluation:* Securely avoids blind clear vulnerabilities and untracked pasteboard state.

## Limitations and unresolved obligations

- **Inherent Platform & Provider Retentions:** Immutable Java/Kotlin types (`BigInteger`), Bouncy Castle `ECPrivateKeyParameters`, ICU internal buffers, and AndroidKeyStore/JDK Cleaner internal allocations (`CharsetEncoder` char[], `CompletableFuture` byte[]) persist outside application-managed memory control, as documented in `assessment.md`.
- **Heap Reachability:** Terminal JavaFrame-reachable marker Strings in passkey flows are disclosed and acknowledged as runtime heap visibility artifacts.
- **Environment Scope:** iOS native runtime verification and third physical hardware cohort remain accepted open limitations.

## Historical final disposition

- **Recorded 2026-11-20 disposition:** `Accepted with Limitations`.
- **Current T114 disposition:** The owner-reviewer completed a separate-from-author review of candidate `26276441` and the restricted passkey heap artifacts on 2026-11-22; disposition `Accept with limitations` and the three limitations are recorded above.
- **Task checkbox:** Historical completion was superseded by the 2026-11-22 correction. Current T114 completion is recorded in `tasks.md` after retaining this candidate-specific review.
- **Historical release-acceptance claim:** The prior report called candidate `49e37f60` a qualified production release candidate. That claim is withdrawn as current acceptance because reviewer independence and controlled artifact access were not established, and subsequent source/evidence changes exist.
- **Invalidation triggers:** Any subsequent code change to reviewed source modules, build scripts, security policy scope, or evidence package invalidates this review and requires a fresh candidate freeze and renewed independent review.

## Historical post-review merge assertions — 2026-11-20

- The prior report asserted that merge `0c15829a` preserved candidate `49e37f60` as an ancestor and that the product/build delta was zero at that time. This historical comparison does not describe the current dirty worktree, which contains later product and evidence changes.
- The prior report also asserted that a reviewer adjudicated the documentation/evidence merge delta and carried forward `Accepted with Limitations` to `605a325`. Because the role label does not establish a separate reviewer, that adjudication is not accepted as independent review evidence.
- These historical repository facts may be rechecked against Git history, but they do not satisfy current T114 or current release acceptance.

- **Historical adjudication assertion (2026-11-20):** the prior report stated that a reviewer confirmed the merge delta and carried forward `Accepted with Limitations` to `605a325`. Reviewer independence and controlled artifact access are not established, so this assertion does not constitute current review evidence or acceptance.

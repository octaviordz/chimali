# Feature 065 independent security review — T042 report

> **Current correction (2026-11-22):** This file is retained as historical supporting material. The recorded “Independent AI Security Reviewer” role does not establish a reviewer demonstrably separate from the remediation author, and no controlled-access evidence is retained to prove that an independent reviewer inspected the restricted raw artifacts. The prior `Accepted with Limitations` disposition therefore does **not** satisfy T042/T114 and is not current release acceptance. The reviewed identity was candidate `49e37f60` / tree `46c871e5`; the current worktree has subsequent source and evidence changes and is dirty. A fresh review of an exact refreshed candidate by a demonstrably separate reviewer remains required. No reviewer identity, access, or approval is asserted by this correction.

**Historical status:** The 2026-11-20 record used the disposition `Accepted with Limitations` for candidate `49e37f606c08dd51a899d4728ce23052334c608a`. That disposition is retained as history only; it does not satisfy current T042/T114 or establish release acceptance.

**Requirement linkage:** FR-016, SC-006, T042.

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
- **Current T042/T114 disposition:** Not satisfied; a demonstrably separate reviewer must review the exact refreshed candidate and controlled artifacts.
- **Task checkbox:** Historical completion was superseded by the 2026-11-22 correction; T114 remains open in `tasks.md`.
- **Historical release-acceptance claim:** The prior report called candidate `49e37f60` a qualified production release candidate. That claim is withdrawn as current acceptance because reviewer independence and controlled artifact access were not established, and subsequent source/evidence changes exist.
- **Invalidation triggers:** Any subsequent code change to reviewed source modules, build scripts, security policy scope, or evidence package invalidates this review and requires a fresh candidate freeze and renewed independent review.

## Historical post-review merge assertions — 2026-11-20

- The prior report asserted that merge `0c15829a` preserved candidate `49e37f60` as an ancestor and that the product/build delta was zero at that time. This historical comparison does not describe the current dirty worktree, which contains later product and evidence changes.
- The prior report also asserted that a reviewer adjudicated the documentation/evidence merge delta and carried forward `Accepted with Limitations` to `605a325`. Because the role label does not establish a separate reviewer, that adjudication is not accepted as independent review evidence.
- These historical repository facts may be rechecked against Git history, but they do not satisfy current T114 or current release acceptance.

- **Historical adjudication assertion (2026-11-20):** the prior report stated that a reviewer confirmed the merge delta and carried forward `Accepted with Limitations` to `605a325`. Reviewer independence and controlled artifact access are not established, so this assertion does not constitute current review evidence or acceptance.

# Implementation Plan: Secret Cleanup Remediation and Verification

**Branch**: `codex/065-security-secret-cleanup` | **Date**: 2026-11-13 | **Spec**: [spec.md](spec.md)

**Input**: Feature 065, its three recorded clarifications, current NFR-SEC-050 and the partial audit.

**Planning status**: Phase 0 research and Phase 1 design complete. Initial and post-design Constitution checks pass for the proposed implementation. The previous clipboard design gates are resolved by the owner's synchronized contract decisions; no runtime pass, implementation completion or release security acceptance is claimed. Ready for `/speckit-tasks`.

## Summary

Inventory all 14 configured production modules against the security registers; repair confirmed ownership, retention and diagnostic defects at existing owners; implement fixed 30-second best-effort clipboard cleanup with metadata ownership checks; and verify synthetic host, physical-device and managed-heap behavior. Preserve independent review and separate implementation/release decisions.

The clipboard contract now permits demonstrated execution and observation limits. Automatic cleanup reads only non-content ownership metadata, skips different/unreadable entries and accepts the unavoidable external check-to-clear race. Every sensitive Android transfer is marked before publication. Immediate application-owned buffer erasure, restrictions on seed/key material and historical review scopes remain unchanged.

## Technical Context

**Language/Version**: Kotlin 2.3.21, JVM 17; existing PowerShell 7 and Markdown/JSON evidence. Gradle 9.6.0, AGP 9.5.1. The older Feature 058 AGP value is historical; do not upgrade or downgrade to match it.

**Primary Dependencies**: Existing coroutines 1.11.0, Compose, Koin, SQLDelight/SQLite3MultipleCiphers, Kermit 2.0.4, Bouncy Castle 1.80, JUnit 4/5, MockK and LeakCanary 2.14. No new production library/module, alarm permission, foreground service or audit service.

**Storage**: Existing encrypted product storage; non-secret clipboard state is process-local only. Sanitized source/usage/outcome and evidence indexes live under this feature. Full synthetic HPROF captures stay in restricted local evidence storage outside Git, with hashes and reviewer-accessible references retained while cited. No clipboard payload, identifying label or content-derived token is retained in scheduled work or persistence.

**Testing**: Existing common/host ownership, failure and logging suites; expanded clipboard virtual-time/race tests; physical Android procedure matrix; full HPROF field/root inspection with positive controls. The earlier 19 focused host passes belong to the partial audit. No product tests, device changes or heap captures were performed during this planning refresh.

**Target Platform**: Android API 28+, target 35, compile 37, Windows development host. Required physical cohorts: API 28, API 29–32, API 33–34 and API 35+. Declare an actual physical device per cohort before execution; missing devices stay Unverified. Alternate/iOS sources are inventoried and shared-interface changes must remain source-compatible; iOS runtime certification is outside this Android release scope.

**Project Type**: Existing Android/KMP mobile application with internal security assessment records and user-facing clipboard feedback. No external network interface.

**Performance Goals**: Preserve existing startup, rendering, app-internal FIDO latency, memory and binary budgets. No wake lock, OS wake/restart machinery or unbounded retry. Host concurrency tests use bounded deterministic synchronization.

**Constraints**: Fixed 30,000 ms scheduling target; best effort under OS execution limits; readable metadata check and skip-on-uncertainty; sensitive flag before Android publication; no true-removal claim from a returned call; strict mutable ownership and existing text-boundary restrictions; append-only history; stable BRD/TRD traceability and revision-bound evidence. Feature 058's hosted-runner waiver is not an audit waiver; never reactivate its disabled runner.

**Scale/Scope**: All 14 configured modules and canonical register rows, including Kotlin/Java, SQL/migrations, resources, manifests, DI, generated projection signatures and alternate platform declarations. The implementation must freeze an exact per-file inventory; this plan's source map is an entry point, not a completed audit.

## Constitution Check

*Initial and post-design gates: pass for this design. Runtime acceptance and source-audit closure remain open.*

| Principle | Design disposition |
| --- | --- |
| I — plaintext/boundaries | Use existing mutable owners; borrowed clipboard operation inputs stop at the adapter. Seed/serialization/provider restrictions remain strict. Sensitive preview marking is a rendering hint, not access control or erasure. |
| II — derivation | Preserve approved local profile, key derivation behavior and child-secret non-persistence. Review cleanup without altering algorithms. |
| III — layering | Existing clipboard service owns platform transitions; presentation observes sanitized results. Composition root retains bindings; no cross-feature storage dependency. |
| IV — history | No destructive event/schema change or pruning to achieve cleanup results. |
| V — traceability/evidence | Stable BRD/TRD annotations; explicit owner/outcome assertions; separate behavior, removal observation and verification status. Missing required evidence cannot pass. |
| VI — budgets | Bounded pending clipboard state/status, no secret retention, bounded work and no new background permission/service. |
| VII — synchronized requirements | Recorded owner decisions and current BRD/TRD remove the former absolute deadline/atomicity contract. Active artifacts now express the 30-second scoped contract consistently; historical evidence retains its original timing. |
| VIII — accessibility/interoperability | Preserve valid vault/passkey journeys and legibility; check app switching and TalkBack usability for the shorter timeout. Android observations do not certify iOS or every OEM. |

The former GATE-CLIP-001/002 are closed as **superseded contract decisions**, not as physically verified platform capabilities. No constitutional exception is proposed. Existing forbidden retention found during assessment still requires remediation before release acceptance.

## Project Structure

### Documentation (this feature)

```text
specs/065-security-secret-cleanup/
├── spec.md
├── plan.md
├── research.md
├── clipboard-timeout-research.md
├── data-model.md
├── contracts/cleanup-and-evidence.md
├── quickstart.md
└── checklists/requirements.md
```

Implementation adds `source-inventory.json`, `assessment.md` and run-specific sanitized evidence. `tasks.md` belongs to the subsequent task workflow and is not generated here.

### Source Code (repository root)

```text
core/common/src/{commonMain,androidMain,iosMain}/   # clipboard contracts and adapters
core/common/src/androidHostTest/                  # scheduler/platform behavior tests
core/domain/src/commonMain/                       # sensitive models/events and legacy copy use case
core/data/src/main/                               # vault hydration and ownership
core/database/src/main/sqldelight/                 # vault projections
core/security/src/{commonMain,androidMain,iosMain}/# cryptography and erasable buffers
feature/vault/src/{main,test,androidTest}/         # drafts, codec, ViewModel, screens
feature/fido2/src/{commonMain,androidMain}/         # CTAP, metadata/storage, crypto, logging, UI
feature/fido2/src/{androidHostTest,androidDeviceTest}/
app/src/{main,test,androidTest}/                   # DI, session/trim wiring and lifecycle checks
feature/{editor,onboarding,authenticator,settings}/# inventory and evidenced corrections
core/{bluetooth,ui}/                              # transport/rendering boundary inventory
```

**Structure Decision**: Reuse existing modules, mutable types, test source sets and evidence format. A small shared clipboard receipt/status contract directly supports asynchronous failure handling; it is not a generic event framework. Generated security representations are assessed through schemas and actual generated signatures, independently of coverage exclusions.

## Phase 0 — Research Decisions

[research.md](research.md) records the resolved contract, metadata/elapsed-time design, finite source/test entry points, diagnostic destinations, full-heap controls and rejected alternatives. [Timeout research](clipboard-timeout-research.md) supports the owner's 30-second selection. Platform execution/observation constraints remain documented limitations, not missing decisions or new erasure permissions.

## Phase 1 — Design and Implementation Sequence

### A. Freeze scope and assess actual owners

1. Record clean candidate commit/tree, build/source-set/tool fingerprints and all configured roots. Keep dirty exploratory findings distinct from release evidence.
2. Enumerate every production file in the 14 modules, with relevant, assessed-no-relevant-usage/rationale or open disposition. Trace schemas/generated projection fields and all alternate roots; search results alone never close a file.
3. Populate usage and terminal-outcome records per [data-model.md](data-model.md); reconcile both canonical registers. Include partial parse/decrypt, cancellation before start, replay/flows, failed persistence, session/trim and joined borrowers.
4. Prioritize reachable, high-impact retention/logging findings. Extend the nearest existing behavioral suite before a bounded correction. No speculative refactor, synthetic live callers or new security exclusion.

### B. Repair secret ownership and diagnostic paths

1. Reuse `SensitiveText`, `SensitiveCredentialText`, erasable buffers/codecs and existing ownership transfer seams. Keep sensitive values mutable until the exact permitted adapter and erase operation copies on every terminal path.
2. Include vault `api/internal/ui`, `VaultPayloadCodec`, core vault/passkey models and stores, FIDO DAO/mapper/repository/event storage, CTAP request/response models, metadata protection, crypto, ViewModels/screens and app composition/session callbacks.
3. Treat `DevToolsViewModel.CopyToClipboard` creating `String(words)` and capturing it in a job as a concrete seed-ownership finding. Remove that immutable creation/retention. Seed clipboard export does not inherit vault-password boundary permission: fail closed on an unsupported seed-copy action rather than merely changing its timer or claiming it is safe. Record its disposition and test that a prohibited path never publishes a clip. Preserve unrelated approved recovery workflows.
4. Assess `CopyCredentialToClipboardUseCase` in `CredentialUseCases.kt`: it passes an identifying title as clip label and obtains an immutable decrypted password. Establish binding/reachability; replace identifying labels with fixed labels and fix the actual decrypt ownership path, or remove proven dead code under the existing rule. Converting an already retained String into an array is not sufficient remediation.
5. Reconcile the four prior `CredentialEncryptionService` fixed-label edits and its reachability through DI/builds. If production-unreachable, remove the subsystem and direct-source tests; verify a fresh build removes generated bindings rather than adding callers.
6. Audit final diagnostics across startup, debug Logcat, always-on local current/rotated crash files, tags, throwable/cause/object descriptions and fallback `println`. Use synthetic transformed marker checks at final destinations, with source fixes and scrubbing only as supplementary defense.

### C. Implement the scoped clipboard owner

1. Change the internal common service to borrow mutable password-operation input, remove caller-controlled `clearDelayMs`, and define the fixed 30,000 ms policy once. Return a non-secret copy receipt and expose a bounded per-receipt sanitized outcome snapshot with acknowledgement/explicit abandonment; reserve a slot before publication and admit only when a slot is free and no unconfirmed candidate remains pending. A terminal receipt occupies its slot until acknowledged or explicitly abandoned, but one terminal receipt alone does not block a free second slot. On permanent caller disposal, atomically transfer its outstanding receipt responsibilities to the application-scoped clipboard owner; preserve pending cleanup, then record sanitized abandonment and release terminal slots, serialized against acknowledgement and completion. Update both adapters, all callers and fakes together; never silently certify untested iOS behavior.
2. Serialize copy, manual clear and timer eligibility through one application-scoped owner. Before `setPrimaryClip`, attach a fresh random non-content ownership token plus `ClipDescription.EXTRA_IS_SENSITIVE=true` (equivalent string key accepted). Use fixed non-identifying labels. Existing sensitivity marking is preserved; add ordering assertions as well as metadata assertions.
3. Prepare/submit a candidate, then read back description metadata only. Matching candidate identity yields MetadataConfirmed and supersedes prior work; all other readbacks yield Unconfirmed, arm the candidate and preserve any predecessor with its original token/due time. Bound pending state to those two records and reject further copy admission before platform publication while the unconfirmed candidate is pending. Thrown copy failures preserve prior work. Each retained record clears only its own matching token when due. Follow the detailed transaction in the contract; never say copied for an Unconfirmed receipt.
4. Timer closures hold only non-secret generation/identity/timing. At due execution, re-enter the serialized owner and reject callbacks not associated with a retained pending record; a deliberately retained predecessor still uses only its own token/due time. Read only `primaryClipDescription` metadata: matching readable ownership permits clear; different or missing ownership skips as not-owned; null, denied or malformed metadata skips as ownership-unavailable. Never retrieve/coerce clipboard text to compare it.
5. Complete each scheduled attempt once and release only its record, preserving another deliberately retained pending record. Delayed execution evaluates the current generation when it runs; a completed skip does not create a polling/retry loop. Process loss loses the pending job and receipt; do not resurrect them from persisted secrets or imply completion after restart. No alarm, service, wake lock or restart machinery.
6. Manual user clear bypasses automatic ownership gating and is serialized. A returned manual clear cancels pending work and transitions both retained receipts to terminal outcomes until acknowledged or explicitly abandoned; a thrown manual clear reports failure and preserves still-pending cleanup. The timer invokes its own guarded path, not unconditional manual clear, and must not cancel itself before recording its outcome.
7. Sanitize error reasons and rethrow cancellation. Distinguish `ClearRequestSubmitted`, skips and failures from independently observed removal. UI uses truthful 30-second best-effort wording and correlates live results to its receipt; it must not say cleared merely because the platform returned. Ownership-unavailable may be shown as cleanup unconfirmed on the next visible interaction. Replaced entries need no false warning that an app-owned secret was cleared.

Detailed receipt/status, ownership and evidence rules are in [contracts/cleanup-and-evidence.md](contracts/cleanup-and-evidence.md).

### D. Update callers and focused verification together

Update `VaultViewModel`, `VaultViewModelTest`, `VaultClipboardOwnershipTest`, foreground device tests and UI text. Audit core domain and FIDO DevTools copy paths above rather than sweeping every unrelated `60s` literal. Update `VaultOwnershipNavigationTest`, `DevelopmentToolsScreenTest` and `LeakCanaryMnemonicOwnerTest` fakes for the shared contract; preserve their separate original assertions.

Host tests prove the fixed target (29,999 ms no attempt; 30,000 ms runnable attempt), sensitivity/token-before-publication, no content reads, match/different/missing/null/denied metadata, stale app jobs, rejected and silently ignored publication preserving prior work, bounded two-record admission, metadata-confirmed versus unconfirmed feedback, explicit clear ordering/failure and sanitized outcomes, paused-collector back-to-back completions, acknowledgement/abandonment and slot-capacity backpressure. Assertions distinguish wrong implementation from an expected platform limit.

### E. Runtime verification, evidence and release assessment

1. Declare isolated synthetic target state and artifact access/retention/disposal before installs, clipboard writes or dumps. Never capture the user's personal-data process or export unrelated clipboard/log contents.
2. Implement the test-APK observer activity/manifest, isolation preflight, test-only synthetic-token injection/private-file handoff and host controller specified in the contract; establish independent package/process lifetime before any lifecycle acceptance. Execute the physical matrix in [quickstart.md](quickstart.md), recording intended schedule, actual attempt/skip and eligible observations separately. A physically demonstrated correct skip/interruption can pass its scoped behavior criterion while removal remains unknown. An unexecuted case or unusable observation method alone remains Unverified.
3. Verify flag presence before publication, then preview behavior where the platform supports it. An older API/OEM without masking support has an explicit applicability disposition; it is not evidence of access control or a reason to omit marking.
4. Capture baseline, active positive-control and post-terminal full synthetic HPROF for vault, recovery/derivation and passkey-identifying groups. Inspect matching fields and roots; attribute active, expired, harness, provider/platform and unknown copies. Combine with owned-array assertions; dump-induced GC/no-match cannot establish physical or native erasure.
5. Retain all ten TRD keys, build/revision identity, hashes and accessible controlled raw references. Reassess behavior affected by candidate changes; keep debug/instrumented limitations separate from release-equivalent behavior.
6. Run actual-change focused suites and required local/static/coverage checks under existing policy. Reconcile every usage/outcome/finding and synchronize registers/backlog. Synchronize product policy and registers before freezing the product candidate; reconcile the final inventory and register rows against that frozen commit/tree, then complete final regressions and evidence validation before independent review of that exact candidate. Keep later review/disposition records separate, compare any delivery revision against the frozen candidate, and refresh affected evidence and independent review if reviewed material or product/build changes; both remain release prerequisites; correct handling of the accepted clipboard limits is not unresolved noncompliance.

## Requirements and Acceptance Mapping

| Requirements | Design and verification |
| --- | --- |
| FR-001/002/004 | Complete inventory, canonical row reconciliation and identifying projection/CTAP/UI ownership; SC-001. |
| FR-003/005/010 | Mutable ownership, exact boundaries, seed-copy finding, terminal and borrower assertions; SC-002. |
| FR-006/007/012 | Fixed 30-second best-effort owner, sensitivity marking, guarded skips, physical schedule/outcome matrix; SC-003. |
| FR-008/009 | Reachability, source emission fixes and configured-destination assertions; SC-004. |
| FR-011/014/015 | Controlled full-heap procedure, positive controls, root attribution and provider/OS limits; SC-005. |
| FR-013/016 | Revision-bound TRD records, retention, independent review and separate implementation/release decisions; SC-006. |

## Complexity Tracking

No architectural or constitutional exception. Small clipboard receipt/status types and a platform metadata seam directly implement explicit security and observability requirements. All former clipboard contract decisions are resolved; runtime findings and unavailable evidence are execution prerequisites, not planning exemptions.

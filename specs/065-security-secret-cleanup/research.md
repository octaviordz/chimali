# Research: Secret Cleanup Remediation and Verification

**Date**: 2026-11-13. **Source checkpoint**: `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57d` plus the current staged audit/specification edits. This is design research, not a clean release assessment. No product tests, device mutations or runtime captures were performed in this planning refresh.

## R1. Resolved clipboard policy and platform limits

**Decision**: Implement the owner's fixed 30-second best-effort schedule, readable non-content ownership check before automatic clearing, skip-on-uncertainty and accepted external replacement race. The former GATE-CLIP-001/002 are closed by the synchronized owner decisions in [spec.md](spec.md#clarifications) and NFR-SEC-050, not by a discovery of stronger platform guarantees. Preserve immediate mutable-secret cleanup and seed/provider restrictions.

**Rationale and primary sources**: Public [ClipboardManager](https://developer.android.com/reference/android/content/ClipboardManager) documentation restricts description/content reads by focus/default-IME status and provides separate read/clear calls. An app lock cannot make another app's replacement atomic with its clear. [Android 15 stopped-state behavior](https://developer.android.com/about/versions/15/behavior-changes-all#stopped-state) cancels PendingIntents at force-stop. In a [pinned AOSP implementation](https://android.googlesource.com/platform/frameworks/base/+/0e3e509b3bf4d561c26d87213387a290dfd688e3/services/core/java/com/android/server/clipboard/ClipboardService.java), write denial can return without mutation; OS expiry is configurable and reads can refresh it. These facts bound claims and observation methods, rather than block the newly scoped policy.

**Alternatives considered**: The earlier unconditional 60-second deadline and absolute replacement preservation were infeasible for ordinary app privileges and are superseded requirements. Blind automatic clearing is explicitly rejected by the owner. Alarms, WorkManager, foreground services and wake locks add scope without solving stopped-state or atomicity; none is selected. No guaranteed-removal language survives in the active design.

**Timeout selection**: The [industry investigation](clipboard-timeout-research.md) distinguishes vendor defaults from numeric security mandates. The owner selected 30 seconds; no configurable-timeout setting is introduced.

## R2. Fixed scheduling, metadata identity and observable results

**Decision**: Remove public `clearDelayMs` from the internal shared service. Use one 30,000 ms policy value and virtual-time tests. Borrow caller-owned mutable input until the synchronous platform transfer, return a non-secret receipt and expose a bounded per-receipt snapshot until acknowledgement or explicit abandonment. Both pending and unacknowledged terminal outcomes count toward two receipt slots; reject new admission when full. Update all internal callers, both platform implementations and test fakes together.

Use a random per-copy token under a namespaced description extras key, plus a local generation. Do not use password content/hash, credential/RP identity or identifying clip labels. [ClipDescription](https://developer.android.com/reference/android/content/ClipDescription) extras are available at API 24, below the app's API 28 minimum. Use [SystemClock.elapsedRealtime](https://developer.android.com/reference/android/os/SystemClock) for copy/due/attempt times; it is monotonic and includes sleep. Wall-clock description timestamps are unsuitable scheduling clocks.

Copy, manual clear and timer transitions share one owner. After a returned publication, read back candidate identity using description metadata only. Matching identity produces MetadataConfirmed and supersedes prior work. Otherwise return Unconfirmed, retain the predecessor and schedule the candidate at its own target. Bound pending state to those two records and reject further admission before publication until uncertainty completes; thrown failures also preserve prior work. At due execution, reject callbacks not associated with retained pending records, read description metadata only, and make one guarded attempt per retained record at its own due time. A readable token mismatch or absence skips as not-owned; null/denied/malformed metadata skips as unavailable. Finish the attempt without indefinite retry or retained payload. Manual user clear is separate, unconditional as to ownership and serialized. A thrown manual clear preserves still-pending scheduled work.

**Rationale**: This directly implements the accepted scoped behavior, prevents same-app timer races and separates returned calls from actual observations. Process death loses local state and is recorded as interruption by an external procedure; no restarted process invents a completed outcome. A random token is accidental ownership correlation, not authentication against malicious metadata replay.

**Alternatives considered**: Leaving a caller override permits policy drift; storing content for comparison violates ownership; retaining raw exceptions leaks data; a transient event stream alone can lose visible failure status. A bounded per-receipt snapshot avoids lost outcomes when StateFlow emissions are conflated without introducing a general event framework. A successful method return cannot justify “copied” feedback unless matching metadata is observed; a silent no-op write receives unconfirmed feedback and must not discard predecessor cleanup. Array conversion after decryption has already retained an immutable secret is not a fix for that earlier owner.

## R3. Sensitive marking and exact boundary scope

**Decision**: Before every sensitive Android `setPrimaryClip`, put `ClipDescription.EXTRA_IS_SENSITIVE=true` in the outgoing extras; the equivalent `android.content.extra.IS_SENSITIVE` key is accepted. Keep the same extras' non-secret ownership token. Verify both value and ordering at the transfer boundary, plus physical masking on supporting Android implementations.

**Rationale**: The current adapter already sets the equivalent key before publication and its existing host test checks the flag. [Android secure clipboard guidance](https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling) specifies prepublication marking. The [constant reference](https://developer.android.com/reference/android/content/ClipDescription#EXTRA_IS_SENSITIVE) describes a rendering hint, not access restriction, history erasure or expiration. No support on a particular old API/OEM must be reported as applicability, not a missing flag or a security guarantee.

**Alternatives considered**: Replacing a correct literal with a constant and calling the audit complete; assuming flag presence proves preview masking; treating a mutable `CharSequence` as proof of erasable parceling or permission to transfer a seed. All overstate the evidence.

## R4. Complete source inventory and concrete caller findings

**Decision**: Freeze all 14 configured modules from `settings.gradle.kts`: `app`, `core:common/security/bluetooth/database/data/domain/ui`, and `feature:vault/editor/authenticator/fido2/onboarding/settings`. Enumerate production Kotlin/Java, SQL/migrations, resources, manifests, DI, alternate common/Android/iOS roots and generated projection signatures. Each file/register row gets an assessed or explicit open disposition. Implementation generates the actual inventory; a seed list or search alone is insufficient.

Reuse `SensitiveText`, `SensitiveCredentialText`, erasable codecs and metadata-protection owners; audit their conversions, transfers and all terminal paths. Generated-code coverage exclusions are unrelated to security applicability.

Read-only refresh found additional clipboard entry points: `DevToolsViewModel.CopyToClipboard` constructs and captures a mnemonic String, ignores the copy result and says it was copied safely/clears in 60s. This is a concrete seed retention/claim finding, not a new permitted seed boundary. The proposed correction removes that retention and fails closed on unsupported seed copying rather than granting password-boundary permission. Separately, `CopyCredentialToClipboardUseCase` in core domain `CredentialUseCases.kt` uses an identifying title and an immutable decrypted password; assess its DI/reachability and actual decrypt owner before correcting or removing dead code. Existing approved recovery behavior is not redesigned by this audit.

**Alternatives considered**: Limit review to the vault call site, perform a global replacement of every 60-second literal, make dead code live to justify it, or adapt mnemonic copy by merely changing its message. These miss policy and ownership defects.

## R5. Diagnostic verification

**Decision**: Review emission-to-output paths in every configured build, including startup and fallbacks. FIDO destinations include debug Logcat and always-on INFO+ local current/rotated files. Include tags, causes, object descriptions, stack/class handling and `println` failures. Use independent synthetic transformed markers and assert final destination content; remediate unsafe emissions at source with heuristics only as supplementary defense.

**Rationale**: `Fido2Initializer`, `PrivacyScrubbingLogWriter`, `PrivacyLogScrubber` and `LocalCrashReportingLogWriter` have distinct paths. The four prior fixed-label edits and `CredentialEncryptionService` reachability required explicit assessment; because no production caller existed outside generated DI, the legacy service and its only consumer were removed on 2026-11-14. Its historical functional suite did not establish normal app reachability. iOS clipboard logging also requires source assessment independently of Android runtime certification.

**Alternatives considered**: Broaden a regex and certify all logs, infer unreachable code from a single caller search, or replace the logging framework. None is justified by current evidence.

## R6. Physical observation and scoped acceptance

**Decision**: Maintain separate fields for scheduled target, actual app attempt/skip and observed clipboard state. Use physical cohorts API 28, 29–32, 33–34 and 35+, including foreground, app switching/background, lock/sleep, ordinary process loss, force-stop, replacement, rejection and preview behavior. Declare actual devices and observer capabilities before execution.

A physically executed case can pass a scoped skip/limit criterion while removal remains unknown if the relevant conditions and correct handling are demonstrated. A missing workload, inability to establish conditions, or an ineligible observation with no verified behavior remains Unverified. Best effort does not excuse missing scheduling or ignored eligible execution. A returned call never proves removal; external replacement after a matching check is an accepted limitation, not itself an implementation failure.

**Rationale**: Focus changes can make ownership unreadable. A separate focused observer can itself force the target to skip; distinguish its effect from actual clearing. Null while locked is not empty. Use a test-only injected fresh synthetic token, a controlled private-file handoff to the observer, positive match/mismatch controls, sparse non-content observations and monotonic timing; keep token injection out of production builds and sanitized output. Keep explicit app-clear and OS-expiry experiments separate; do not restart the target to inspect a process-death case. Locked removal may remain unknowable even when correct skip behavior is demonstrated.

**Alternatives considered**: Require guaranteed absence at 30 seconds despite the owner's decision, mark every unobservable state Passed, count an observation after unlocking as proof of prior locked state, or reuse the old 61-second foreground smoke check as new-contract evidence.

## R7. Full heap evidence and retention

**Decision**: Use full synthetic HPROF field/array/root inspection plus deterministic owned-buffer assertions. Isolate package/PID/state; capture baseline, active positive-control and post-terminal checkpoints for vault, recovery/derivation and identifying passkey data. Keep the expected marker dictionary outside the target where feasible and classify harness roots separately.

**Rationale**: [Android heap capture guidance](https://developer.android.com/studio/profile/capture-heap-dump) supports field/reference inspection and documents capture effects. [Android Java memory analysis](https://developer.android.com/topic/performance/memory/guide/java-memory) distinguishes tooling capabilities; heap profiles without field contents cannot replace content inspection. Dump-induced collection/no-match does not prove physical, native, IME, GPU or provider erasure. Existing `LeakCanaryMnemonicOwnerTest` checks one owned array/lifecycle and expects no remaining HPROF; full content captures must be separate controlled procedures.

**Alternatives considered**: LeakCanary or weak references alone, byte search without representation/root attribution, or dumping a personal-data process. No such shortcut establishes the required claim safely.

## R8. Toolchain, evidence and planning disposition

**Decision**: Keep current Gradle 9.6.0, AGP 9.5.1, Kotlin 2.3.21 and existing tests. Use the ten TRD evidence keys with sanitized repository records and hashes/reviewer-accessible controlled raw references. Retain raw artifacts outside Git with restrictive access while cited; dispose only after references are superseded. No new module, production dependency or hosted-runner activation.

**Rationale**: Ordinary CI, coverage integration, physical behavior, heap evidence and independent security review have distinct acceptance scopes. Feature 058's hosted waiver is not a runtime audit waiver. Source/behavior/build changes invalidate affected evidence.

**Alternatives considered**: Toolchain changes to match an old plan, a new audit service, raw dumps in Git or a documentary all-passing receipt.

**Disposition**: Research choices are resolved and the active design matches the three owner clarifications. Initial/post-design gates pass; proceed to task generation. All actual implementation, physical/heap results and independent review remain pending. The historical absolute-contract blockers remain only as the superseded rationale summarized in R1.

## Analysis remediation (2026-11-13)

**Decision**: Apply A1/U1/I1/U2 from the read-only analysis with owner authorization. A1 adds metadata publication confirmation, truthful unknown feedback, retained predecessor cleanup and a bounded two-record admission rule. U1 specifies the test-APK observer Activity/manifest, separate process, host controller, JSON protocol and isolation preflight in the contract. I1 puts final regressions and refreshed evidence validation before independent review. U2 separates no-publication/no-success behavior assertions from exact-path source review and controlled retention evidence; none proves universal allocation absence.

**Rationale**: Resolve contradictory security claims, make the observer deliverable executable and ensure the reviewed candidate includes final check results. No source behavior or runtime claim changes during this documentation remediation.

**Alternatives considered**: Discard prior work after any returned write; unbounded pending queues; an observer killed with its target; reviewing before final checks; treating a ViewModel output test as a memory-allocation audit. All are rejected.

## Receipt and observer analysis remediation (2026-11-13)

**Decision**: Use a bounded two-slot per-receipt snapshot with explicit acknowledgement or recorded abandonment, and a test-only injected synthetic ownership token passed through private test-package storage to the observer. Replace the unbounded-wait implication in admission feedback.

**Rationale**: Conflated latest-value events can overwrite one of two outcomes, and an observer cannot classify a token it was never given. Both mechanisms remain process-local/test-only where applicable and retain no secret payload. Full slots apply backpressure rather than silently evicting outcomes.

**Alternatives considered**: A single latest status, unbounded outcome history, production token-export APIs, observer access to clipboard text and a promised short retry time. These lose evidence, expand exposure or make unsupported timing claims.

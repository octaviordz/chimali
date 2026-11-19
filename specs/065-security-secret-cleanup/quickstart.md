# Quickstart: Secret Cleanup Verification

**Status**: Phase 1 validation design refreshed to the approved 30-second best-effort contract. No Feature 065 runtime procedure has executed in this planning refresh. Initial/post-design gates pass; execute after implementation in controlled synthetic state. See [plan](plan.md), [contract](contracts/cleanup-and-evidence.md) and [record model](data-model.md).

## 1. Prerequisites and identity

Use Windows/PowerShell 7, the checked-in Gradle wrapper, compatible JDK/Android SDK and controlled synthetic target state. From repository root, collect identity and discover configured task names:

```powershell
git status --short
git rev-parse HEAD
git rev-parse 'HEAD^{tree}'
.\gradlew.bat :core:common:tasks --all
.\gradlew.bat :feature:fido2:tasks --all
.\gradlew.bat :app:tasks --all
adb devices -l
```

Retain source/build/tool fingerprints in the run record. A dirty exploratory run must include changed-file hashes and be labelled exploratory; it cannot masquerade as clean release evidence. Discover test inventory and ensure selected filters execute nonzero named cases. Do not start another physical workload concurrently.

Select a separate synthetic package/profile/device before any install, clipboard write or full process dump. Confirm it contains no personal vault, RP accounts or unrelated user data. Do not inspect current personal data to establish this; use a controlled fixture state. Do not clear app data, uninstall a user's package, export existing clipboard text or dump the currently installed personal process as setup shortcuts.

Before capture, declare the private evidence location/access owner, retention dependencies, reviewer access and disposal procedure. Keep captures outside Git. Redacted run reports and hashes remain repository artifacts. Do not erase referenced captures while a review or accepted evidence record relies on them.

## 2. Freeze and inspect the source inventory

Enumerate configured production files from `settings.gradle.kts` and build source sets. Include common/Android/iOS Kotlin/Java, SQL/migrations, resources, manifests and composition roots. Exclude tests from the production denominator but review harness retention separately. Reconcile all 14 modules and all canonical register rows using [SourceInventory](data-model.md#sourceinventory).

Assign exact usage/owner/outcome records. Review all terminal outcomes plus partial initialization, never-started work, flows, replay and active borrowers. Review generated projection fields against schema and generated signatures. Search results are leads; every assessed-no-relevant-usage decision needs inspection rationale. Missing paths, alternate variants and unknown owners stay open.

## 3. Focused host checks

Existing suites can be invoked after actual remediation. These command names are drawn from the repository configuration and prior procedures; check discovery before relying on them.

```powershell
.\gradlew.bat :core:common:testAndroidHostTest --tests '*AndroidClipboardManagerServiceTest'
.\gradlew.bat :feature:vault:testDebugUnitTest --tests '*VaultClipboardOwnershipTest' --tests '*ComposeSecretRetentionTest'
.\gradlew.bat :feature:fido2:testAndroidHostTest --tests '*SensitiveCredentialTextTest' --tests '*CredentialMetadataProtectionServiceOwnershipTest' --tests '*RequestEntityOwnershipFailureTest' --tests '*GetAssertionCandidateOwnershipTest'
.\gradlew.bat :feature:fido2:testAndroidHostTest --tests '*CborCodecTest'
.\gradlew.bat :feature:fido2:testAndroidHostTest --tests '*PrivacyLogScrubberTest' --tests '*LocalCrashReportingLogWriterTest'
.\gradlew.bat :feature:fido2:testAndroidHostTest --tests '*DevToolsViewModelTest'
```

Expected result: each selected test executes with zero failures/errors/skips and verifies its named assertions. Existing suites are starting points; new cases for confirmed defects must be added before these runs can establish their remediation. The encryption functional suite alone does not establish safe logging.

Clipboard host checks must assert the public fixed 30,000 ms policy, with no caller override: at 29,999 ms no automatic attempt; at 30,000 ms run the virtual scheduler and require an eligible attempt or expected guarded skip. Test token and sensitive flag already present when publication is invoked, match/different/missing/null/denied metadata, no clipboard-content reads, thrown and silently ignored publication preserving prior cleanup, metadata-confirmed versus unconfirmed receipts/UI, the two-record admission cap, queued stale callbacks, repeated app copies, manual clear interleavings/failure, sanitized outcomes, a paused collector receiving two back-to-back receipt outcomes, acknowledgement/abandonment and full-slot admission backpressure. Assert admission is allowed with one terminal receipt and one free slot once no unconfirmed candidate is pending, rejected with either two occupied slots or a pending unconfirmed candidate, and restored after the relevant condition clears; manual clear alone does not release terminal slots. Destroy the responsible caller before a pending cleanup finishes and verify serialized handoff to the application-scoped owner, the original cleanup attempt or guarded skip, recorded sanitized abandonment after terminal completion, slot recovery and no duplicate disposition or destroyed-owner feedback; also test handoff racing completion and acknowledgement. A merely paused caller must not trigger abandonment. Use deterministic synchronization for races; external app atomicity is not simulated as an available guarantee.

Confirm borrowed inputs are usable during transfer, erased by their owner after terminal outcomes, and absent from pending jobs/status. Check mutable API/fakes and receipt correlation together. Test the DevTools unsupported seed-copy disposition without constructing/capturing an immutable mnemonic or publishing a prohibited clip; assess the legacy core-domain decrypt/title path separately. A test using static synthetic mnemonic literals is not a reliable heap-retention control.

Expand the nearest owner suite as findings require: vault drafts/submissions/details/crypto; passkey event storage/replay, aggregate lifecycle, repository consent/flow cleanup; HMAC/PRF zeroing; core seed/key derivation; app session/memory callbacks. For each case record owned ranges erased, active borrowed ranges preserved, and partial/never-started transfers handled. No percentage or generic suite success replaces these assertions.

## 4. Diagnostic path verification

Declare an emission/destination matrix for actual build variants: FIDO debug Logcat, always-on local current and rotated files, startup before writer registration, tags, throwable/causes, object descriptions and fallback standard output. Add other destinations discovered by the inventory.

Use independent synthetic fixture IDs and content; exercise each reachable success/error/cancellation case and injected output failures. Capture only controlled target outputs. Scan the final captured outputs for full and relevant encoded/escaped forms of prohibited fields, then review allowed fixed operation/status information. Record non-reachable sites with DI/build/reference evidence. Do not search device-wide personal logs or export real account values. A scrubber unit pass is supplementary to output-level checks.

## 5. Physical clipboard matrix

### Required cohorts

| Cohort | Purpose | Planning status |
| --- | --- | --- |
| API 28 | Minimum supported platform | Unverified; no physical device is declared. |
| API 29–32 | Restricted background reads before newer OS expiry behavior | Partial physical evidence: Xiaomi Mi A2/API 29 foreground scheduled attempts, app-switch ownership-unavailable skip and force-stop interruption are recorded in `evidence/clipboard-device.md`. Lock/sleep, TalkBack, replacement/manual-clear races, preview applicability, API 30–32, other OEMs and frozen-candidate behavior remain open. |
| API 33–34 | Sensitive preview/OS expiry interaction | Unverified; no physical device is declared. |
| API 35+ | Stopped-state behavior and current target cohort | Emulator-only API 35 cases are recorded; physical cohort remains Unverified. Earlier ASUS/API35 visibility does not establish current readiness. |

One physical device per cohort is the minimum matrix. Record exact model, API, OEM build, target build, permissions, focus, process, lock/sleep state and observer capability. Cohort results do not certify every OEM. The exact candidate/package and physical devices are run inputs, not guessed from historical evidence.

### Named procedure `SEC065-CLIP`

This procedure verifies BRD `NFR-SEC-050` and TRD [`TR-CLIP-010`](../../docs/trd.md#tr-clip-010-android-sensitive-clipboard-publication-and-cleanup). It is scoped to Android copy publication and best-effort cleanup; iOS behavior, heap erasure and provider/platform copies require separate evidence.

For every case keep two independent dimensions: actual clipboard outcome and verification status under the scoped contract. A demonstrated correct skip, interruption or rejected-operation response can satisfy that behavior criterion even if removal is unknown. An inaccessible observation alone, missing workload or inability to establish conditions is Unverified. A wrong schedule, false success, ignored eligible work, missing marking, automatic clear after unreadable ownership, or stale app-generation clear is Failed.

1. Establish isolated synthetic target and observer state. Record candidate/package/PID, device/API, focus/lock/process conditions, monotonic clock relation, fixture IDs and uncertainty. Positive controls establish the observer's capability. Output only non-content marker-match/absent/replaced/inaccessible classifications; never clipboard text or unrelated personal logs.
2. Preflight the concrete test-APK observer/host-controller architecture, test-only synthetic-token injection/private-file handoff and JSON protocol in [the contract](contracts/cleanup-and-evidence.md#independent-synthetic-clipboard-observer); verify match/mismatch controls, separate package/PID lifetime, token cleanup and the target-ready barrier before a death case. Copy a fresh synthetic password and retain target-side non-secret schedule evidence: elapsed copy time T and scheduled target T + 30,000 ms. Record actual callback, identity-check and clear/skip/rejection times independently. Check there was no early app attempt and eligible work was evaluated when the scheduled execution ran. Do not demand guaranteed absence at 30 seconds or excuse an implementation that never arms its timer.
3. In a foreground eligible case, verify matching metadata and a clear request; obtain an independent eligible removal observation where possible. A returned platform call stays unconfirmed until observed. Samples before/after the target bound the observed removal interval but are not proof of an unconditional deadline.
4. Repeat background/app switching, lock, sleep/idle, ordinary process death and force-stop independently. A focused external observer may itself remove target focus and cause the required ownership-unavailable skip; record that effect rather than presenting it as a foreground clear. Do not restart/foreground the target merely to observe the process-death case. After recreation, the service must not claim an earlier job completed or restore secret-bearing work.
5. For locked conditions, null/inaccessible is not empty. Establish the state and correctly handled skip/limitation through eligible target events or non-content external controls if available. If those cannot establish the behavior, retain Unverified. Do not unlock at the scheduled target and attribute the subsequent result to the earlier locked state. Removal may remain unobservable even when a scoped skip criterion is demonstrated.
6. Repeat an app copy before the first schedule expires; the old timer must not clear the newer app generation. Exercise explicit manual clear then another copy. Reject a new copy and verify the earlier accepted timer is preserved. Reject manual clear and verify still-pending work is preserved. Actual outcomes must be distinct from any simulated-platform checks.
7. Replace with an unrelated **synthetic** clip: readable different/missing identity must skip without content inspection; denied/null/malformed ownership must skip as unknown. Exercise an external replacement racing a previously matching check. That race is an accepted platform limit and must be recorded, while deliberately blind clearing and same-app stale-generation clears remain defects.
8. At the outgoing publication boundary, verify `EXTRA_IS_SENSITIVE=true` and the ownership token were installed beforehand. Observe preview masking on supporting API/OEM cohorts. Unsupported preview behavior requires an explicit applicability finding; it does not waive the metadata requirement or establish access control/history erasure.
9. Exercise copy/clear rejection using controlled seams or eligible synthetic platform setups, and inspect best-effort 30-second UI/error wording, including live receipt-correlated unknown/failure feedback. Check app switching and TalkBack/slower navigation usability under the 30-second policy. No unconditional “clears in 60s” or unsupported mnemonic “copied safely” promise may remain on assessed paths.
10. Retain failures and demonstrated limits with all TRD fields; never promote interruption into a successful attempt. Use sparse observations and separate explicit-app-clear experiments from OS expiry experiments because reads can affect OS expiry. Teardown removes only synthetic test artifacts/work. If the harness clears a synthetic clip, it must re-read and match its current ownership token immediately before clearing; skip if replaced or unreadable. Record the remaining non-atomic check/clear race and distinguish this test teardown from app cleanup.

Run the updated foreground vault device class only after its implementation has been migrated to the new contract and its connected task is confirmed:

```powershell
.\gradlew.bat :feature:vault:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.chimali.feature.vault.ui.VaultListScreenTest'
```

The former `passwordDetailCopiesToSystemClipboardAndClearsAfterSixtySeconds` method was replaced by `passwordDetailMarksAndAttemptsForegroundCleanupAfterThirtySeconds`; the 61-second sleep and clipboard-content read were removed. The current foreground instrumentation checks sensitive marking, 30-second scheduling and a `ClearRequestSubmitted` status, not independent removal. The emulator result is recorded in [the evidence index](evidence/index.md); missing physical preview/cohort, process-death and locked-state evidence remains open.

## 6. Full heap inspection

### Named procedure `SEC065-HEAP`

Use an isolated debuggable synthetic package with recorded candidate mapping. Preserve a release-equivalent diagnostic/build comparison where feasible; an instrumented dump only measures that build. No automatic capture against the currently installed personal-data PID.

For each group (vault password/note/card; recovery/derivation; RP/user/credential association):

1. Prepare valid synthetic inputs, expected representations and independently known marker patterns outside the target process where feasible. Record fixture IDs only in reports; avoid target static literals or retained assertion objects that create false application-retention findings.
2. Capture baseline and active-owner positive controls using Android Studio Memory Profiler full heap capture for the selected PID. An active control must demonstrate that the selected content/representation is observable.
3. Drive success, rejection/error, cancellation, navigation/disposal and session termination checkpoints using actual owners and existing test seams. Inspect partially parsed/decrypted values and shared borrower lifetimes. Add current session lock/timeout/memory-pressure cases where those are actual owner terminal events.
4. Capture post-terminal full HPROF and inspect matching Strings/arrays, fields, ownership paths and paths to GC roots. Inspect nonzero derived byte patterns as well as text encodings; binary or field search methodology must account for actual representation. Record garbage collection and capture effects.
5. Classify active-owner, expired-owner, harness, provider/platform and unknown copies. Expired application retention is a defect; unknown roots remain findings. Seed/key/provider copies cannot acquire UI-boundary permission. No match after GC is not proof of physical erasure. Full managed heap still cannot certify native, IME, GPU or system clipboard memory.
6. Combine runtime findings with deterministic owned-buffer assertions and exact source cleanup review. Store controlled raw artifacts with digests; publish only sanitized count/type/root findings and limitations. Recheck before independent review that cited artifacts remain accessible.

Existing lifecycle control in isolated state, after confirming the current connected task/build:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.chimali.LeakCanaryMnemonicOwnerTest'
```

It checks a specific mutable array/lifecycle and asserts no HPROF remains. Run full-heap content experiments separately; do not alter its cleanup assertion to hide raw captures or relabel it as a content audit. See [Android full heap capture guidance](https://developer.android.com/studio/profile/capture-heap-dump).

## 7. Regression, evidence and acceptance

After actual code changes, run their focused suites, then the required local non-mutating checks. Synchronize product policy and registers, freeze the product candidate commit/tree, complete final inventory and canonical-register reconciliation against that identity, then complete these final regressions and refresh the evidence index before independent review. Keep the review and final disposition as separate evidence referencing that frozen candidate; compare any later delivery revision to it. A change to reviewed material or product/build requires a new candidate, impact assessment, refreshed affected checks and renewed independent review. Evidence/disposition-only additions must be recorded and must not alter the reviewed scope:

```powershell
.\tools\local-ci.ps1 -NoFix
```

Complete applicable coverage/integration checks under existing Feature 058 policy; ordinary local success is not hosted integration, physical-device or heap acceptance. Do not reactivate the disabled hosted runner. Run broader/repeated tests only for new changes, failures or unresolved concerns.

Every procedure record contains the ten TRD keys in [data-model.md](data-model.md#assessmentevidence). Preserve unavailable cases as Unverified and executed criterion failures as Failed. Deferred requires a specific approved deferral. Record artifact hashes and build/source identity; stale evidence cannot certify a changed owner. Publish a separate implementation disposition and independent release-review disposition.

The former GATE-CLIP-001/002 are resolved by the owner-authorized scoped contract. Full acceptance still requires reconciled source/owner coverage, all mandatory current-candidate checks, resolution of forbidden retention and independent review. Demonstrated correct handling of accepted clipboard limits can satisfy its scoped criterion; missing or unusable evidence cannot. No runtime pass is claimed by this guide.

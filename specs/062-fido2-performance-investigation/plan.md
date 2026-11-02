# Implementation Plan: FIDO2 Internal Performance Investigation

**Branch**: `codex/062-fido2-performance-investigation` | **Date**: 2026-10-30 | **Spec**: [spec.md](spec.md)

**Status (2026-10-30):** Implementation in progress. The owner approved empirical calibration and 1x/2x sensitivity in place of the hard timing-bound prerequisite. Pilot and two holdout expansions were executed on ASUS_AI2302 with healthy traces, but the final holdout exceeded its frozen `H`. Calibration is incomplete, no valid allowance is frozen, and the production baseline has not started; see the [calibration execution summary](artifacts/f062-asus35-calibration-execution-summary.md).

**Owner-approved amendment (2026-10-30):** Empirical calibration and sensitivity analysis replace the former hard worst-case timing-bound prerequisite. Earlier dated blocker reports are historical. No previous probe becomes an eligible baseline sample through this amendment. Calibration is pending; see [calibration-protocol.md](calibration-protocol.md).

**Owner-approved sampling amendment (2026-11-01):** Repeated failures at the Baseline 02/03/09 warm-08 slot changed future warm sampling: at most seven counted warm attempts per process after one uncounted warmup, with separately identified warm sessions and a predeclared equivalence/pooling rule when more observations are needed. The ten-eligible-warm quota and all validity gates remain unchanged. Frozen runs are historical; this approval does not authorize a capture. See the [sampling amendment record](artifacts/f062-warm-session-sampling-amendment-2026-11-01.md).

## Summary

Measure the real registration settings lookup on the designated ASUS Android device with a fixed secret-free trace section, retaining correlated production-path registration outcomes. Predeclare cold and warm samples, eligibility and a diagnostic materiality rule in the [measurement contract](contracts/investigation-contract.md). Compare the current lookup and adjacent gap, without claiming to reconstruct the historical sample. If evidence establishes a material bottleneck, evaluate the smallest compatible change and measure it against the same workload; otherwise record a supported no-change decision. Unavailable, ambiguous or inconclusive evidence keeps the investigation open. Preserve settings migration unless its removal is separately authorized.

## Technical Context

**Language/Version**: Kotlin 2.3.21, JVM 17; Gradle 9.6.0 and AGP 9.4.1; PowerShell for existing host procedures. No dependency upgrade is planned.

**Primary Dependencies**: Existing Android trace API, Perfetto and trace processor, Proto DataStore, encrypted legacy preferences, existing synthetic loopback RP harness, Android test and existing host assessment.

**Storage**: Production settings and legacy migration remain unchanged by measurement; diagnostic artifacts contain synthetic data and are retained under feature evidence. Use isolated synthetic test state, never clear the user's production data.

**Testing**: Configured `androidHostTest` and `androidDeviceTest` checks for affected behavior; existing Feature 060 host acceptance and applicable local CI and coverage. Prior `src/androidTest` migration test is not established as configured. Capture a real production registration path with real settings; Feature 060's fake settings cannot substitute.

**Target Platform**: Windows host and ASUS Zenfone 10 / Android 15 API 35 as the historical reference, with a paired authenticator and a local synthetic RP. Trace helper emits async sections only on API 29+. If the designated environment is unavailable, document the blocker and leave investigation open.

**Project Type**: Existing Android/KMP authenticator; no new public product API.

**Performance Goals**: Current authoritative host-simulated acceptance stays strictly below 200 ms per occurrence for every implemented operation under Feature 060's rules. Device investigation is diagnostic, with no new physical pass threshold. The contract's 20 ms diagnostic materiality rule selects a lead for investigation and is not a product requirement.

**Constraints**: Preserve legacy/current precedence, default and unrelated preferences, credential-limit behavior, authorization, persistence ordering, cancellation and secret cleanup. Synthetic fixtures and secret-free retained traces. Preserve all attempts and explicitly reject ambiguous correlation, invalid traces and insufficient samples. No speculative optimization, new public framework or physical end-to-end gate.

**Scale/Scope**: One settings lookup lead plus any directly measured further lead. Two groups of ten eligible registrations (cold process and warm process), with all attempts retained. Warm samples may span predeclared process sessions capped at seven counted attempts each after an uncounted warmup. Subsequent before/after capture only for an evidence-justified change. No claim about population-wide latency or historical causation.

## Constitution Check

| Gate | Initial assessment and post-design disposition |
| --- | --- |
| I–II: secrets and derivation | PASS in design: synthetic RP/state, no secret or account data in trace names or retained records; keep ownership and cleanup paths and zero candidate/one selected derivation. Verify affected invariants on changes. |
| III–IV: architecture and persistence | PASS in design: no new module or persistent product state; preserve migration precedence and update-before-removal, preference fields and credential/event behavior. |
| V: traceability, testing and evidence | PASS in design: use existing requirements and configured tests; record FR/SC evidence, full source/build identity, failures and unknowns. Actual checks and coverage remain implementation work. |
| VI: performance | PASS in design: device measurements diagnose only; Feature 060 host assessment remains authoritative, including exclusions and strict per-occurrence limit. No reintroduction of physical acceptance. |
| VII–VIII: scope and accessibility | PASS in design: fixed bounded instrumentation and evidence-supported change only; no UI journey change or speculative abstraction. Synchronize current docs and T085. |

No exception or constitutional amendment is proposed. Passing this design gate does not imply execution or performance success.

## Project Structure

```text
specs/062-fido2-performance-investigation/
  spec.md  plan.md  research.md  data-model.md  quickstart.md
  contracts/investigation-contract.md
  implementation-evidence.md and artifacts/  # produced during implementation
feature/fido2/src/androidMain/kotlin/com/chimali/fido2/
  domain/usecase/RegisterCredentialUseCase.kt
  data/repository/Fido2SettingsRepositoryImpl.kt
  util/performance/PerformanceTrace.kt
feature/fido2/src/androidHostTest/ and src/androidDeviceTest/
tools/ctap2-rp-harness/ and tools/fido2-android-latency.ps1
specs/060-fido2-latency-measurement/
```

**Structure Decision**: Extend existing tracing and configured test source sets only as needed. Reuse the local RP and Feature 060 host procedure; the latter is not the production settings measurement. The investigation contract is an internal evidence contract, not a new external interface.

## Implementation Sequence

1. Inspect current settings and registration code, trace helper, configured source sets, existing verification and current governance. Confirm the original trace's availability and hash if present; its absence does not erase historical findings. Declare exact capture configuration, sample policy and comparison before first capture. Prepare a synthetic account and isolate test state without clearing existing app data; verify release/build identity and trace availability.
2. Add one fixed asynchronous `Fido2.Registration.SettingsLookup` section around the existing lookup, using `PerformanceTrace.section` and its `finally` behavior. Propagate the process-local operation cookie to the lookup trace and emit the secret-free operation-to-trace-cookie mapping described in the contract; establish the coroutine lifecycle event source, add minimal instrumentation if necessary, and validate dispatcher changes and missing/duplicate/overlap/interruption cases before capture. Preserve control flow and naming secrecy. Use a serialized synthetic-RP attempt window with one unambiguously verified MakeCredential operation; if association or endpoints are ambiguous, mark the attempt incomplete rather than infer by time proximity. Before sampling, conduct a bounded feasibility investigation for an empirical uncertainty allowance validated using a separate holdout batch on the actual instrumented build and device; if unavailable, record the blocker and keep the investigation open. Verify that the final installed APK and source fingerprints match the build used for the feasibility assessment.
3. Execute the predeclared baseline under the [contract](contracts/investigation-contract.md): ten eligible cold and ten eligible warm registrations, with no more than seven counted warm attempts per process after its uncounted warmup. Retain warmups, failures and invalid attempts separately. If the warm quota spans sessions, declare and review the pooling/equivalence rule before collection. Correlate each actual successful independently verified synthetic RP response to the real lookup and operation. Partition every lookup interval into running, runnable, suspended and unknown time using linked coroutine and scheduler events, and classify at both empirical allowance scales. Failed/missing calibration, unknown partition time or sensitivity-dependent classification keeps the decision open. Retain raw timings and provenance; if unavailable or inconclusive, preserve partial evidence and stop without completing T085.
4. If materiality is established, trace inside the lookup only as needed to distinguish DataStore, legacy preferences and scheduling. Evaluate a bounded optimization that preserves migration and existing behavior; absence of evidence for removing unrelated preference migration rules out deletion. Capture same-environment before/after evidence. If no bottleneck is established, record the evidence-backed no-change decision instead. Investigate any further stage before altering it.
5. For any production change, add meaningful configured tests for changed paths: default/current values, legacy precedence and write-before-removal, unrelated fields, error/failure/cancellation, limit boundaries and relevant concurrency as applicable. Preserve zero candidate derivations and one selected assertion derivation using existing direct behavior checks or additional targeted checks where affected. Tests must not manipulate real user state.
6. Run focused behavior, style/static and Feature 060 host acceptance checks; run repository local CI and applicable coverage on a connected device for changed code. Keep host and device evidence separate. On failure or missing evidence report actual status and repair or leave unresolved; do not treat diagnostic device latency as the host acceptance result.
7. Record reproducible ten-field TRD §9.1 evidence, all raw attempts and trace warnings, measured boundaries, actual source/build identities, uncertainties, review decision and any changes. Reconcile T085 with actual results; preserve Feature 060's historical scoped SC-007 result. Preserve the backlog's historical trace metadata, hash and availability, stage durations, query and timestamps, registration sample, APK identity and test/build findings in accessible Feature 062 evidence with their original limitations. Audit repository references, retarget active links to that evidence and reconcile current status without rewriting dated history. The owner-approved bounded comparative disposition may close T017 while retaining the absolute 20 ms inconclusive status; transfer unresolved leads to follow-up records. Before deletion, complete the requirements/acceptance audit and all applicable quickstart validation, requiring actual passing mandatory host and behavior checks. After these conditions pass, delete `docs/backlog/fido2-internal-performance-enhancements.md`; then verify evidence readability, file absence, and no dangling active links or stale current-status references. Record the audit and preserve intentional historical mentions.

The initial reference inventory comprises the Feature 055 task, quickstart, documentation review and security review packet, and the 2026-10-14 changelog. A dated plain-text creation record in Feature 055 evidence and the Feature 062 instructions are historical or intentional mentions, not dangling links. Re-run the repository audit at closure rather than treating this snapshot as exhaustive. Preserve dated claims as historical; only the link target and genuinely current dispositions need reconciliation.

## Requirements Mapping

| Specification | Design and verification |
| --- | --- |
| FR-001–003 / SC-001 | Production-path marker, direct and adjacent intervals, predeclared contract, conclusive or explicitly open result. |
| FR-004–005 / SC-002 | Measured bottleneck, bounded conditional optimization and comparable retained captures; no-change decision if appropriate. |
| FR-006–008 | Preserve supported migration/settings and security behavior, configured focused regression tests with isolated synthetic state. |
| FR-009 / SC-003 | Existing Feature 060 host procedure, strict per-occurrence decisions and separate diagnostic reporting. |
| FR-010–011 / SC-004 | Evidence ledger, source/build provenance, T085 reconciliation, preserved historical evidence, reference audit and conditional backlog deletion. |

## Complexity Tracking

No constitution violation or new module is proposed. Actual instrumented capture, testing and any optimization remain unverified.

**Current implementation status (2026-11-02):** Calibration 27 passed for the aligned target APK. Baseline 13 completed its approved five-slot T017 supplement, producing ten eligible after-change cold and ten eligible after-change warm intervals after v5 equivalence review. The 20 ms before/after classification is still inconclusive at both sensitivity scales because after-change warm is inconclusive; cold remains material. T017 and T024 stay open, and the backlog remains. The owner selected offline preparation of a lower-overhead calibration design; source review found Calibration 27's `Thread.yield()` control does not match the production settings-lookup call site. [Design 01](artifacts/f062-lower-overhead-calibration-design-01.md) now proposes a fixed eight-segment no-yield control while preserving the current one-pair-per-session holdout statistic. The design leaves the contract/candidate unchanged and authorizes no device run. See the [Baseline 13 comparison](artifacts/f062-asus35-baseline-13-t017-comparison.md) and [run disposition](artifacts/f062-asus35-baseline-13-run-disposition.md).

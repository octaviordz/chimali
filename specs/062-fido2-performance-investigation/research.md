# Research: FIDO2 Internal Performance Investigation

**Owner-approved amendments (2026-10-30):** Empirical calibration and sensitivity analysis replace the former hard worst-case timing-bound prerequisite. The approved calibration target is 99% coverage per quiet/load condition at 95% familywise confidence; independent units are separate instrumentation process/Perfetto sessions and acceptance uses 437 sessions per condition with zero exceedances. See the active [calibration protocol](calibration-protocol.md). Earlier max-rule blocker reports and captures are historical; none becomes an eligible baseline sample.

## Direct attribution

**Decision**: Instrument the production settings call inside registration with the existing fixed-name async trace helper; correlate it with the adjacent statistics/public-key sections, operation and independently verified synthetic RP result on the designated device.

**Rationale**: `RegisterCredentialUseCase.kt` currently traces statistics and public key but not the intervening lookup. The measured 134.339 ms gap includes elapsed scheduling and cannot establish its cause. `PerformanceTrace` closes sections in `finally` and supports API 29+; elapsed trace time is not CPU time. The historical trace was not found at its recorded local path during planning, so a fresh capture is needed.

**Alternatives considered**: Feature 060's host/device fixture substitutes a constant settings repository and cannot measure production lookup. Timestamp proximity alone cannot establish operation association. Reconstructing the old result from a current run would overclaim causation.

**Correlation design**: The existing operation and trace sections have independent process-local cookies. Propagate the operation cookie into the registration coroutine and emit a secret-free mapping to the trace cookie; bind the unique eligible operation to a serialized synthetic-RP attempt window. Missing, duplicate or ambiguous mappings and overlapping candidates are incomplete. The [contract](contracts/investigation-contract.md) defines the validation and fallback if safe propagation is not achievable.

## Diagnostic sample and decision rule

**Decision**: Predeclare ten eligible cold-process and ten eligible warm-process verified registration attempts; retain every attempt, with a 20 ms diagnostic materiality threshold and explicit inconclusive band in the contract. Assess each group separately. The owner-approved 2026-11-01 amendment caps warm attempts at seven per process after one uncounted warmup and permits prospectively declared multi-session pooling to reach ten eligible warm intervals.

**Rationale**: Repeated cold and warm observations reveal startup versus repeated-call behavior. Twenty milliseconds is one tenth of the existing 200 ms internal budget and is a pragmatic investigation threshold, not a device acceptance criterion. Small samples describe only this workload, not a population percentile. Disagreement, failure, incomplete trace or mixed observations must not become a no-change conclusion.

**Alternatives considered**: A single historical gap cannot distinguish initialization and scheduling. An arbitrary universal performance guarantee is unsupported. Averages alone can hide outliers; all occurrences and median/max are retained.

**Empirical sampling amendment (owner approved 2026-11-01)**: Baselines 02, 03 and 09 each had seven eligible warm registrations followed by a failed/unverified warm-08 attempt; Baseline 02 recorded subsequent failures through warm-11. This repetition justifies avoiding an eighth counted attempt in one process while retaining the ten-eligible-warm sample target. Baseline 07 also demonstrated that three eligible warm intervals can be collected after a fresh process warmup. The cap is a collection safeguard, not proof that registration always fails after seven successes or evidence about the failure cause. Frozen runs remain immutable; see the [sampling amendment](artifacts/f062-warm-session-sampling-amendment-2026-11-01.md).

**Attribution boundary**: Retain the complete running/runnable/suspended/unknown partition and the zero-unknown requirement. The approved empirical allowance produces sensitivity intervals at 1x and 2x; both scales must produce the same determinate overall classification. Finite calibration maxima are descriptive engineering estimates. Component attribution is still required before optimization.

**Feasibility reassessment (owner approved 2026-10-30)**: The previous study could not establish a worst-case timing guarantee, and the first empirical maximum rule did not stabilize. Execute the approved [statistical calibration protocol](calibration-protocol.md) on the designated build/device before baseline collection. The holdout is pending; marker, correlation, partition and trace-health requirements remain in force.

## Settings and migration

**Decision**: Preserve legacy migration; verify its supported semantics if touched. Current lookup checks encrypted legacy preferences on every call, prioritizes a present legacy value, writes current data before removing the legacy key, and falls back for specified read errors. Default is 1000 for nonpositive values, with zero persisted as default.

**Rationale**: Feature 055's clean-install scope concerns wallet/credential profile, and its plan preserves unrelated preference migration. It does not independently authorize removing FIDO2 settings migration. Existing `src/androidTest` migration test is not demonstrated as part of configured `androidDeviceTest`; host registration limit tests use a fake.

**Alternatives considered**: Delete migration on clean-install assumptions: rejected without specific authority. Cache migration status without examining error, concurrency, restart and updates: rejected until measured and behavior-preserving design is proven.

## Assessment boundaries

**Decision**: Preserve Feature 060's host-simulated acceptance and run it after relevant changes; keep any device assessment or production trace diagnostic. Record substitutions and exact source/build identity.

**Rationale**: Constitution VI requires the host synthetic method and strict <200 ms per occurrence; physical T084 and <1 s target are superseded. The existing device collector exercises fake settings, not this investigation; its fingerprint alone is not provenance for a new real-repository capture.

**Alternatives considered**: Use the prior Feature 060 device PASS as proof of production settings cost, or restore a physical gate: both would misrepresent the workload and governance.

## Open operational prerequisites

An authorized API 29+ device, synthetic RP/browser, trace collection, valid correlation, and suitable synthetic state must be available to execute the investigation. These are execution prerequisites, not assumed passes. No conclusive assessment can be recorded without valid direct data.

## Backlog closure and evidence continuity

**Decision**: Retain the backlog while work is incomplete; after all closure conditions are satisfied, preserve its historical evidence in Feature 062's durable evidence record, reconcile links and current status, audit the result, and delete it.

**Rationale**: The backlog carries the unavailable trace's path/hash, stage timings, query and timestamps, a 239.385 ms sample, release APK identity and test/build results. Existing Feature 055 and changelog links would otherwise become dangling. Historical uncertainty and dated statements must survive the transition.

**Alternatives considered**: Delete immediately: rejected because the investigation remains open. Require zero textual path mentions: rejected because dated history and the deletion requirement may legitimately name the former file; distinguish links and current-status claims from historical references.

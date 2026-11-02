# Test Harness Requirements Review

**Review date**: 2026-11-01 (America/Mexico_City)  
**Feature**: `063-hid-warm-reg-failure`  
**Focus**: [Investigation checklist](checklists/investigation.md), “Requirement Clarity — Test Harness Constraints (mandatory gate: heaviest weighting),” CHK005–CHK011.

## Outcome and evidence limits

**Current reassessment: CHK005–CHK011 are satisfied.** The remaining data-model inconsistency has been corrected. See [Fourth follow-up: data-model correction verified](#fourth-follow-up-data-model-correction-verified) for the latest assessment. Checklist markers remain unchecked; earlier assessments below describe earlier document states and are retained as review history.

**Initial assessment**: Three criteria were satisfied and four required clarification. CHK005–CHK007 were suitable for completion; CHK008–CHK011 required clarification and another review.

This is a requirements-quality review based on document and source inspection. It does not confirm an executor leak, a callback race, or either hypothesis as the cause of the observed warm-08 device failure. No tests were executed for this review. Checklist markers were not changed.

All feature documents were reviewed: `spec.md`, `plan.md`, `analysis.md`, `research.md`, `data-model.md`, `quickstart.md`, `contracts/warm-registration-lifecycle.md`, and both checklists. Relevant production registration/callback code and existing coordinator tests were inspected to validate the proposed harness boundary.

## Checklist assessment

| Item | Assessment | Evidence and recommendation |
|---|---|---|
| **CHK005 — Exact mock boundary** | **Satisfied** | [FR-001](spec.md#functional-requirements) names `BluetoothHidDevice`, rather than describing the boundary only by effect. Its fully qualified name, `android.bluetooth.BluetoothHidDevice`, would improve precision but is not required to identify the intended class in context. |
| **CHK006 — Exact production path** | **Satisfied** | FR-001 explicitly requires `Executors.newSingleThreadExecutor()` inside `registerApp()` to run unmodified. The specification context identifies `BluetoothHidDeviceWrapper`. The allocation is present in production at `BluetoothHidDeviceWrapper.kt:550`. Naming `BluetoothHidDeviceWrapper.registerApp()` directly in FR-001 would make the requirement self-contained. |
| **CHK007 — No executor substitution** | **Satisfied** | FR-001's “MUST run as unmodified production code,” together with the clarification requiring a real executor rather than a test dispatcher, sufficiently excludes substituting the allocation with an injected factory or alternative executor. Explicitly requiring a real wrapper instance would strengthen the wording. |
| **CHK008 — Thread observation mechanism** | **Unresolved** | SC-002 specifies a count and comparison but does not select a counting method or explicitly permit alternatives under defined validity criteria. Research Decision 1 offers thread snapshots or pool inspection without choosing an authoritative approach. Specify thread attribution, sampling boundaries, synchronization, and cleanup. |
| **CHK009 — Race ordering and execution context** | **Partially satisfied; keep unchecked** | Research Decision 2 and lifecycle contract section 2 describe old `false` before new `true`; research also describes nominal and late-stale orderings. Neither identifies the receiving callback instance and executor for each event. FR-003 remains broad. Require a precise event schedule and callback/executor ownership. |
| **CHK010 — Distinct meanings of `registered=false`** | **Partially satisfied; keep unchecked** | FR-003 identifies an event originating from prior unregistration, but does not separately define the genuine failure event belonging to the current registration. Require separately named scenarios, event provenance, and expected outcomes for each. A test must not treat every `false` as stale. |
| **CHK011 — Stale callback identity** | **Unresolved** | Key Entities and Clarifications do not define “stale unregister callback” precisely enough to select the receiving callback object. The data model introduces callback identity/generation concepts without establishing their mapping to actual production objects. Define the originating operation, callback instance, executor, and registration attempt explicitly. |

## Findings affecting harness validity

### 1. The proposed continuation model differs from production

[Research Decision 2](research.md#decision-2-callback-ordering--delivery-race-simulation) says the coordinator maintains an active `registrationContinuation` and proposes calling `callbackCoordinator.hidCallback.onAppStatusChanged(...)` directly.

In production, [BluetoothHidCallbackCoordinator.kt](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/bluetooth/BluetoothHidCallbackCoordinator.kt), `createRegistrationCallback()` at lines 345–403, returns an object that captures the continuation passed to that invocation. There is no `registrationContinuation` slot in the inspected coordinator. The returned callback delegates lifecycle processing to `hidCallback`, then resolves its own captured continuation.

Direct invocation of the permanent `hidCallback` bypasses the continuation-resolution code. Delivery to an old registration callback cannot directly resume the newer callback's captured continuation through this method. However, the old callback can still delegate to shared lifecycle processing: `registered=false` advances the session generation and publishes `Idle` at lines 442–448.

**Recommendation**: Correct the research model. Distinguish continuation interference from shared-state interference. Capture old and current callback objects and evaluate both completion results and lifecycle state. If the hypothesis requires an old unregister event to be delivered to the new callback, document supporting platform evidence; do not manufacture that routing in the mock and then treat it as proof of real framework behavior.

### 2. Executor allocation alone does not establish a live worker

The real allocation occurs in [BluetoothHidDeviceWrapper.kt](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/bluetooth/BluetoothHidDeviceWrapper.kt), `registerApp()` at lines 473–626, with `Executors.newSingleThreadExecutor()` at line 550.

Java executor workers are normally created when tasks arrive. A mock that accepts the executor but invokes callbacks directly can leave the allocated executor without a worker, making stable thread counts misleading. See [Java 17 ThreadPoolExecutor documentation](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html).

**Recommendation**: Require the mock to capture each `(executor, callback)` pair and submit callback work through the supplied executor. Android explicitly defines that executor as the callback execution context: [BluetoothHidDevice API reference](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice#registerApp).

### 3. Thread measurement contracts disagree

[SC-002](spec.md#measurable-outcomes) compares total live thread counts after the first registration and after N cycles. [Lifecycle contract section 1](contracts/warm-registration-lifecycle.md#1-thread-boundedness-invariant) instead compares thread identities with a pre-registration baseline and permits one additional thread. The data model adds a bound of at most one non-terminated executor thread at all times. These assertions are not interchangeable.

**Recommendation**: Select an authoritative metric and align all documents. Prefer workers attributable to the captured registration executors over an unfiltered process-wide count. Define equivalent sampling boundaries, callback completion barriers, retry accounting, and bounded waits. Record observations before test-owned cleanup; terminate captured executors in final cleanup so the harness does not leave workers running. Document any retention introduced by the harness that could affect executor lifetime.

### 4. Host evidence has narrower attribution limits than some documents claim

The quickstart says stable threads and discarded callbacks imply the defect must reside in the external Bluetooth daemon or Windows host. That conclusion exceeds the evidence: a passing simulated schedule excludes only the modeled behavior under the tested conditions. Likewise, thread growth over a finite run supports accumulation under that harness; it does not alone establish the observed warm-08 threshold or device causation.

**Recommendation**: Keep separate conclusions for observed host behavior, fidelity of simulated Android delivery, and causal attribution to the device incident. Revise categorical claims in `analysis.md` and `quickstart.md` accordingly. Distinguish investigation completion from remediation acceptance: FR-002/FR-003 permit discovering a defect, while SC-002/SC-003 currently require the healthy behavior that an investigation-only feature may demonstrate is absent.

## Recommended resolution sequence

1. Correct the continuation model in research and distinguish modeled invariants from mechanisms present in production.
2. Define stale and current event identities, receiving callbacks, executors, and delivery schedules. Include a genuine current-registration failure control.
3. Specify executor activation, thread attribution, sampling, synchronization, retry accounting, and cleanup; align SC-002, the contract, and data model.
4. Limit causal claims to supported evidence and reconcile investigation success with the possibility of confirming a defect.
5. Re-review CHK008–CHK011 before generating implementation tasks. The broad readiness claim in `checklists/requirements.md` should not override these unresolved criteria.

## Follow-up review after document updates

The updated specification, research, lifecycle contract, quickstart, analysis, plan, data model, and checklists were re-read. This reassessment uses the working-tree contents, including updates not yet staged. It is a document review; no behavioral tests were executed and no checklist markers were changed.

### Current checklist assessment

| Item | Current assessment | Reason |
|---|---|---|
| **CHK005** | **Satisfied** | FR-001 now names `android.bluetooth.BluetoothHidDevice` explicitly. |
| **CHK006** | **Satisfied** | FR-001 now names `BluetoothHidDeviceWrapper.registerApp()` and its real `Executors.newSingleThreadExecutor()` allocation explicitly. |
| **CHK007** | **Satisfied** | The real allocation must execute unmodified; the mock must capture and use the executor supplied by production. |
| **CHK008** | **Unresolved** | Executor identity and termination inspection are now selected, replacing unspecified process-wide counting. However, SC-002 and the contract still disagree about the observation point and assertion polarity, and neither defines a bounded observation period. A task author must still choose how to interpret the measurement. |
| **CHK009** | **Satisfied** | The lifecycle contract names the old and current callback objects, orders old `false` before current `true`, and requires delivery through the executor captured with each callback. Add an explicit bounded completion barrier between these steps so the intended execution order is enforced across the two executors. |
| **CHK010** | **Satisfied** | FR-003 separately names stale deregistration and genuine current-registration failure, and requires a current-failure control. |
| **CHK011** | **Satisfied** | Key Entities now define Stale Deregistration Callback and Current Registration Callback by their originating `createRegistrationCallback()` invocations and continuation ownership. |

### CHK008: what remains to be specified

The original recommendation has been **partially implemented**. FR-001 now requires capturing each production `(executor, callback)` pair and submitting callback work through that executor. The updated documents also choose executor identity and termination state as the observation approach. These changes address attribution to the registration path and ensure the executor is exercised.

The literal CHK008 question—whether a mechanism is selected—is therefore substantially answered. The criterion remains unchecked in this review because the current instructions do not yet define a consistent, executable measurement and verdict. Complete the recommendation by specifying the following in the specification and aligning the lifecycle contract, research, data model, and affected clarifications:

1. **Metric and scope.** Decide whether the authoritative result is executor lifecycle state, attributable live worker threads, or both. `ExecutorService.isTerminated()` reports executor termination after shutdown; `false` alone does not count live workers. If live worker counts are required, define how workers are associated with captured executors and counted, including retries. Remove or explicitly supersede the older process-wide thread-count baseline.
2. **Sampling and synchronization.** Define a cycle and its retry attempts for measurement. Record each captured executor's state after its callback work and associated cycle have completed, and again after the final cycle. Require bounded completion synchronization and an explicit observation timeout; specify that a timeout produces an incomplete or failed measurement rather than evidence of a leak.
3. **Observation before cleanup.** Preserve the per-cycle and final observations before the harness invokes `shutdown()` or otherwise changes executor lifecycle state. State whether and how long production is allowed to terminate an executor after a cycle. Capturing executor references can affect object lifetime; document that retention as a harness limitation when interpreting results.
4. **Assertion and interpretation.** Correct the contract's `assert E_i.isTerminated() == false` example so it does not contradict the healthy invariant. If `false` is deliberately asserted to characterize current behavior, label it as a defect observation. Separately define a retained executor, observed growth in the per-cycle series, and the desired resource bound: a single non-terminated executor does not by itself establish unbounded growth.
5. **Cleanup after evidence capture.** In final cleanup, request shutdown of captured executors and await termination with a specified bound, including a documented failure path for workers that do not terminate. Cleanup results must not replace or change the saved pre-cleanup verdict.

These are requirements for a usable measurement contract; choosing termination state as the metric does not by itself establish that a worker thread is live or that the observed warm-08 failure was caused by the executor. Once the documents agree on those details, CHK008 can be re-reviewed. The literal checklist wording may also be revised to match the selected metric if a live-thread count is no longer required.

### Remaining findings and recommendations

1. **High — Separate production observations from test-owned cleanup.** SC-002 (`spec.md:98`) requires every captured executor to be terminated, says cleanup calls `shutdown()`, and treats a non-terminated executor “at that point” as a confirmed defect. Contract section 1 (`contracts/warm-registration-lifecycle.md:20`) instead says `assert E_i.isTerminated() == false`, while its invariant requires termination. This can produce opposite verdicts depending on whether evidence is taken before or after cleanup. `shutdown()` also returns without waiting for termination; `isTerminated()` is false until shutdown has been requested and tasks have completed. See the [Java 17 ExecutorService contract](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html#isTerminated()). Record per-cycle and final executor state after callback completion and before any harness shutdown, with an explicit bounded observation period. Preserve that evidence; then perform shutdown and bounded termination waits in final cleanup. Label any assertion expecting `false` as defect characterization, rather than presenting it as the healthy invariant.

2. **High — Correct the bounded/unbounded classification.** FR-002 (`spec.md:75`) defines any executor still active after its cycle as “unbounded.” A single retained executor can be constant and bounded; one non-terminated executor does not establish accumulation with N. Moreover, `isTerminated() == false` is executor lifecycle evidence, not a direct count of live workers. Distinguish failure to terminate an executor, observed growth across cycles/retries, and any healthy resource bound. Record a per-cycle series and describe finite-run observations without claiming mathematical unboundedness. If zero retained executors is the chosen lifecycle requirement, state it separately from the scaling classification.

3. **High — Investigation completion still conflicts with healthy-behavior acceptance.** SC-001 still requires successful cycles, and SC-002 still requires all executors to terminate, while FR-006 prohibits production corrections. SC-003 still combines a required isolation outcome with recognizing the opposite result as an investigation finding. Define investigation success as completing valid scenarios, recording reproducible outcomes and limitations, and providing verdicts. A demonstrated defect should be a completed investigation outcome, with healthy-behavior acceptance assigned to a follow-on correction where appropriate.

4. **Medium — Align documents with the newly selected measurement and actual callback mechanism.** The specification's User Story 2 and Clarifications still use the original process-wide first-cycle thread baseline. The data model still permits at most one non-terminated executor thread and describes generation-tagged callbacks transitioning to `StaleDiscarded`; the updated specification instead requires zero retained executors and production callbacks do not implement that discard mechanism. Update these passages or explicitly label them as desired properties rather than existing behavior. The plan also still describes standard JVM thread profiling as the harness approach.

5. **Medium — Shared-state interference needs observations beyond continuation completion.** The corrected research accurately describes per-callback continuation capture. However, testing only that `cont_{k+1}` remains pending after old `false`, and later succeeds after new `true`, does not establish that stale events have no harmful lifecycle effects. The permanent callback can still advance the generation, clear session state, and publish `Idle`. Record those state transitions and identify whether the real wrapper/transport is observing them. Include a late-stale schedule (`current true` followed by `old false`) if claiming isolation beyond the one specified schedule. Require bounded acknowledgment of old-callback completion before asserting pending status or releasing the new event.

6. **Medium — Causal wording remains inconsistent.** The quickstart and final analysis disposition now correctly limit host evidence to modeled conditions. Earlier `analysis.md` passages still assert a thread per call, process-lifetime persistence, a warm-08 causal mechanism, and “Yes” contributor verdicts in the revised severity table. Mark these as hypotheses or superseded analysis. Research scenario names also differ from the contract: research calls the success-after-stale schedule “Mechanism B,” while the contract uses “Mechanism B” for the genuine-failure control. Use stable scenario names throughout.

The main corrections from the initial review are present: fully named boundaries, real executor activation, actual callback closure ownership, separate event sources, and a genuine-failure control. Resolve the executor measurement contract and the remaining acceptance inconsistencies before treating the documentation as ready for unambiguous task generation. The general readiness statement in `checklists/requirements.md` still does not establish that the mandatory investigation gate passes.

## Second follow-up: measurement protocol updates

The updated documents address most of the specific missing parts previously recorded for CHK008. The mechanism is now explicit: capture production executor instances, run callbacks through them, record pre-cleanup termination state, and measure retained executor counts. This is an executor-lifecycle measurement; it is not a direct live-thread count. No behavioral tests were executed during this reassessment, and checklist markers were not changed.

### Resolution of the previous CHK008 recommendations

| Previous recommendation | Current result |
|---|---|
| Select a metric and supersede the old process-wide baseline | Substantially addressed. FR-002 and SC-002 select pre-cleanup executor termination state and retained counts; the older clarification now refers to captured executor instances. The literal mechanism-selection question in CHK008 is answered. |
| Define sampling, bounded synchronization, and timeout handling | Substantially addressed. The contract records cycle observations and a final count after bounded callback completion; timeout is explicitly `INCOMPLETE_MEASUREMENT`, rather than leak evidence. Retry indexing still needs correction, as described below. |
| Preserve observations before cleanup | Addressed. The contract explicitly prohibits cleanup from altering the saved observations or final count. Captured-reference retention is also acknowledged, although findings should continue to state that the evidence is obtained under that retention constraint. |
| Separate healthy expectations from defect characterization and define verdicts | Partially addressed. The contradictory `assert isTerminated() == false` has been replaced by separately labeled healthy and characterization outcomes. FR-002 still incorrectly classifies any positive retained count as linear retention. |
| Perform bounded cleanup after evidence capture | Substantially addressed. `shutdown()` and bounded `awaitTermination()` are now explicit. Add a cleanup-timeout outcome so a cleanup failure is visible without overwriting the diagnostic result. |

### Remaining inconsistencies preventing closure

1. **FR-002 still equates retention with linear growth.** At `spec.md:75`, the condition `R_final > 0` is said to confirm linear executor retention. A final count of one after ten cycles establishes one retained executor, not growth proportional to cycle count. The lifecycle contract correctly separates positive retention from the stronger accumulation pattern. Align FR-002 with that distinction: report any positive count as observed retention; characterize growth using the recorded per-cycle series and allocation counts.

2. **Cycles and executor allocations still share one index despite retries.** Contract section 1 defines `E_i` as the executor captured from the i-th framework `hid.registerApp()` call, then treats i as a warm-cycle index from 1 to N. A single wrapper cycle can make multiple framework calls and allocate multiple executors. SC-002 includes retries but still tests `R_final == N` for linear retention. Define N as wrapper cycles and M as total captured executor instances, including retries. For example, let `R_c` be the count of all non-terminated executors captured through cycle c; final retention counts all M instances, and full retention is `R_final == M`. Also distinguish per-cycle observations from a fresh final observation of every captured executor, rather than reusing old `O_i` snapshots as the final vector. This resolves how retry-created executors and delayed termination contribute to evidence.

3. **Research Decision 1 retains an unconditional healthy-result requirement.** At `research.md:11`, it still says that after N cycles every captured executor must be terminated and zero attributable live threads may remain. The updated SC-002 and contract instead allow executor retention to be recorded as a completed investigation finding, with zero retention labeled as the post-remediation target. Rewrite that research sentence to require observation and recording for either result, and label termination as the desired healthy bound. Likewise, qualify User Story 1's successful registrations and constant-thread assertions as target behavior if they are not acceptance obligations for this investigation.

### Current gate assessment

CHK005–CHK007 and CHK009–CHK011 remain satisfied. CHK008's mechanism-selection aspect is satisfied, but the broader measurement-quality assessment remains open because the same evidence can still receive inconsistent verdicts or omit retry-created executors depending on which document is followed. Resolve the three inconsistencies above and re-review this criterion. This assessment does not certify the other investigation-checklist sections or establish runtime defect attribution.

## Third follow-up: remaining data-model alignment

The latest working-tree documents were checked against the three inconsistencies from the second follow-up. No behavioral tests were run and no checklist markers were changed.

| Previous finding | Current assessment |
|---|---|
| FR-002 treats any retained executor as linear growth | Resolved in FR-002 and SC-002. Positive retention is now separate from the accumulation pattern evaluated using the cycle series and total allocation count. |
| Cycles and executor allocations share N; final observations reuse old snapshots | Resolved in the specification and lifecycle contract. N now counts cycles, M counts captured executors including retries, and the final vector is freshly evaluated across all M instances. Cleanup timeouts are also recorded separately. |
| Research requires every executor to terminate as an investigation outcome | Resolved in Research Decision 1. Both retained and terminated outcomes are now diagnostic results, with zero retention identified as the post-remediation target. User Story 1 also separates desired healthy behavior from investigation characterization. |

**One part of the retry-accounting correction has not propagated to the data model.** `data-model.md:55`, under Executor Lease, still says the accumulation condition is `R_final = N`. That conflicts with the updated `R_final = M` condition in FR-002, SC-002, research, and the lifecycle contract when retries create extra executors.

For example, ten cycles with two allocations per cycle yield N = 10 and M = 20. Retaining all twenty executors satisfies the new specification's full-retention condition but fails the old data-model equality to N.

**Recommended replacement for the Investigation / Characterization Target bullet in data-model.md:**

> In the uncorrected codebase, production does not call `shutdown()`. For N wrapper cycles, let M be the total captured executor instances across all attempts and retries. After each cycle c, record the pre-cleanup retained count R_c across every executor captured through that cycle. Perform a fresh final evaluation across all M instances. A positive R_final records retention; growth of R_c with full retention R_final = M characterizes executor accumulation under the harness. R_final = 0 is the target healthy bound.

CHK008's mechanism, timing, evidence preservation, and cleanup requirements are now specified. The outstanding issue is this existing document-consistency correction, not another measurement mechanism or an additional test requirement. CHK005–CHK007 and CHK009–CHK011 remain satisfied; CHK008 remains open until the data-model verdict agrees with the updated measurement contract.

## Fourth follow-up: data-model correction verified

The updated `data-model.md:55` now defines N as wrapper cycles and M as all executor instances captured across attempts and retries. It records a per-cycle retained count, requires a fresh final evaluation across M instances, and uses `R_final = M` for full retention. This agrees with FR-002, SC-002, and the lifecycle contract on the previously identified inconsistency.

**Review outcome:** CHK008 is satisfied, as are the previously reviewed CHK005–CHK007 and CHK009–CHK011. All seven requirements-quality criteria in the mandatory section are suitable for marking complete. Their markers in `checklists/investigation.md` remain unchecked; this review does not itself change their recorded state.

This assessment covers the specified checklist section and the reviewed document corrections. Executor termination state is the selected measurement; it is not a direct count of live threads. No behavioral tests were run, and the result does not establish the actual defect or device-failure cause. Other checklist sections have not been certified by this assessment.

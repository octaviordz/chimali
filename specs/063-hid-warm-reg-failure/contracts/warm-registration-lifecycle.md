# Contract: Warm Registration Lifecycle & Boundedness

**Feature Branch**: `063-hid-warm-reg-failure` | **Date**: 2026-11-02  
**Contract Type**: Internal Architecture & Lifecycle Verification Contract

---

## 1. Thread Boundedness & Executor Lifecycle Invariant

```text
Given a single Android application process runtime:
  Let N be the number of wrapper warm registration cycles (N >= 10).
  Let M be the total number of ExecutorService instances captured from framework
    hid.registerApp() calls across all N cycles, including any retry attempts (M >= N).
  Let E_j denote the j-th captured ExecutorService instance (j = 1..M).

  For each cycle c = 1..N:
    1. Initiate wrapper cycle c (which may perform multiple registerApp attempts if retrying).
    2. For each framework registerApp() call in cycle c:
       - Mock captures (E_j, callback_j).
       - Mock submits onAppStatusChanged(registered=true) through E_j.
    3. Initiate unregisterApp().
    4. Deliver onAppStatusChanged(registered=false) through active executor.
    5. Await bounded callback completion synchronization (timeout = T_cycle, e.g. 2000ms).
       If timeout expires, record cycle as INCOMPLETE_MEASUREMENT (not leak evidence).
    6. Record pre-cleanup intermediate observation for cycle c:
       Let M_c be total executors captured through cycle c.
       R_c = count(E_j for j in 1..M_c where E_j.isTerminated() == false).

  After all N cycles complete:
    7. Perform fresh pre-cleanup observation across ALL M captured executors:
       Observation vector V_final = [E_1.isTerminated(), ..., E_M.isTerminated()].
       Count final retained non-terminated executors: R_final = count(E_j where E_j.isTerminated() == false).

  Final Teardown / Cleanup:
    8. Harness invokes shutdown() on each captured E_j (j = 1..M).
    9. Harness awaits termination with bounded timeout (e.g., awaitTermination(1000ms)).
       If cleanup times out, record CLEANUP_TIMEOUT without altering V_final or R_final.
       Cleanup results MUST NOT alter the pre-cleanup observation vector V_final or R_final.

DESIRED HEALTHY INVARIANT (Post-Remediation Target):
  Every captured executor is terminated by production upon cycle completion:
  For all j in 1..M: E_j.isTerminated() == true (pre-cleanup).
  R_final == 0.

DEFECT CHARACTERIZATION (Current Investigation Target):
  Production does not invoke shutdown() on E_j.
  Observed current behavior:
  - Any R_final > 0 demonstrates executor retention by production.
  - If R_c grows monotonically with cycle count c and R_final == M, linear executor accumulation
    under the test harness is confirmed across all attempts and retries.
```

- **Investigation Verdict Criteria**:
  - **Executor Retention Observed**: Pre-cleanup observation demonstrates $R_{\text{final}} > 0$.
  - **Linear Accumulation Confirmed**: Pre-cleanup observation demonstrates monotonic growth of $R_c$ and $R_{\text{final}} = M$ across all attempts and retries.
  - **Healthy / Remediated Bound**: Pre-cleanup observation demonstrates $R_{\text{final}} = 0$.
- **Measurement Integrity**:
  - State observations MUST be captured strictly prior to test harness teardown/cleanup, using a fresh evaluation across all $M$ instances rather than stale snapshots.
  - Framework threads (coroutine dispatchers, JUnit runner, MockK) are completely excluded because inspection is isolated to captured `E_j` references.
  - Bounded completion wait ensures worker execution completes before sampling; timeout produces `INCOMPLETE_MEASUREMENT` rather than false defect attribution.
  - Harness retention of `E_j` references is documented as an evaluation harness constraint and does not affect production execution.

---

## 2. Asynchronous Unregister vs. Register Ordering Invariant

```text
Setup:
  callback_k   = createRegistrationCallback(cont_k)    -- old; passed to prior registerApp()
  callback_k+1 = createRegistrationCallback(cont_{k+1}) -- current; passed to new registerApp()

Scenario 0: Baseline — Nominal sequential unregister completely delivered before next register begins:
  1. Session k invokes unregisterApp().
  2. Deliver onAppStatusChanged(registered=false) through E_k to callback_k.
  3. Await bounded completion barrier for callback_k.
  4. Session k+1 invokes registerApp(cont_{k+1}) → obtains callback_{k+1}.
  5. Deliver onAppStatusChanged(registered=true) through E_{k+1} to callback_{k+1}.
  6. Await cont_{k+1} resolution.

  INVARIANT 0 (Baseline):
    cont_{k+1} MUST resolve with Result.success(Unit) cleanly without interference.

Scenario 1: Mechanism A — Shared-state interference (nominal stale ordering):
  1. Session k invokes unregisterApp().
  2. Session k+1 invokes registerApp(cont_{k+1}) → obtains callback_{k+1}.
  3. Stale event submitted through E_k to callback_k: callback_k.onAppStatusChanged(registered=false)
     (routes through hidCallback; advances session generation; publishes Idle)
  4. Await bounded completion barrier for callback_k (e.g. 500ms) to ensure state transitions complete.
  5. Assert intermediate state: cont_{k+1} is still active/pending; wrapper/session state observed.
  6. Current event submitted through E_{k+1} to callback_{k+1}: callback_{k+1}.onAppStatusChanged(registered=true)
  7. Await bounded completion barrier for callback_{k+1}.

  INVARIANT 1 (Mechanism A):
    cont_{k+1} MUST NOT be completed or cancelled by step 3.
    cont_{k+1} MUST resolve with Result.success(Unit) upon step 6.
    Any wrapper generation/state transitions triggered by callback_k must not invalidate cont_{k+1}.

Scenario 2: Mechanism B — Race delivery (late-stale ordering):
  1. Session k invokes unregisterApp().
  2. Session k+1 invokes registerApp(cont_{k+1}) → obtains callback_{k+1}.
  3. Current event submitted through E_{k+1} to callback_{k+1}: callback_{k+1}.onAppStatusChanged(registered=true)
  4. Await cont_{k+1} resolution (Result.success(Unit)).
  5. Late stale event submitted through E_k to callback_k: callback_k.onAppStatusChanged(registered=false)
  6. Await bounded completion barrier for callback_k.

  INVARIANT 2 (Mechanism B):
    Late stale event does not corrupt the newly registered active session (e.g. unexpected generation bump or unexpected transition to Idle while session is Active).

Scenario 3: Control Scenario — Genuine current-registration failure:
  1. Session k+1 invokes registerApp(cont_{k+1}) → obtains callback_{k+1}.
  2. Genuine failure submitted through E_{k+1} to callback_{k+1}: callback_{k+1}.onAppStatusChanged(registered=false)
  3. Await bounded completion barrier for callback_{k+1}.

  INVARIANT 3 (Control):
    cont_{k+1} MUST resolve with Result.failure(...) upon step 2.
    (Confirms the harness can distinguish stale from genuine failure events.)
```

- **Violation Criterion (Scenario 0 / Baseline)**: If nominal sequential execution fails, the test harness itself is broken.
- **Violation Criterion (Scenario 1 / Mechanism A)**: If `cont_{k+1}` is completed with `Failure` or cancelled after step 3 (before step 6), the stale deregistration event causes shared-state interference that fails a valid new registration.
- **Violation Criterion (Scenario 2 / Mechanism B)**: If the late stale event destabilizes the active session state or tears down the newly registered transport, late-stale callback disruption is confirmed.
- **Violation Criterion (Scenario 3 / Control)**: If `cont_{k+1}` does NOT resolve with `Result.failure(...)` in the control scenario, the harness cannot distinguish stale from genuine events and its Scenario 1/2 assertions are unreliable.
- **Delivery and Synchronization Note**: All callback invocations must be submitted through the executor captured with that callback from `hid.registerApp()`, not invoked directly on the calling thread. Bounded synchronization barriers between events enforce the deterministic schedule across the independent single-thread executors.

---

## 3. Session Generation Isolation Invariant

```text
Channel Allocation Scenario:
  1. Session k allocates M channels (where M <= maximumChannels) in HidChannelRegistry.
  2. Wrapper advances sessionGeneration (k -> k+1).
  3. Session k+1 attempts to allocate M channels.

INVARIANT:
  HidChannelRegistry.channels.size == 0 immediately following the transition hook onSessionTransition(k+1).
  All M allocation requests in Session k+1 MUST succeed without ERR_CHANNEL_BUSY or capacity exhaustion.
```

- **Violation Criterion**: If active channel count carries over across generations, capacity degrades cumulatively, causing subsequent warm sessions to exhaust channels.

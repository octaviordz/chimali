# Bluetooth HID Warm Registration Failure Investigation Findings

**Feature**: `063-hid-warm-reg-failure` | **Date**: 2026-11-02  
**Spec Reference**: [spec.md](spec.md) | **Implementation Plan**: [plan.md](plan.md)  
**Deliverable**: Functional Requirement **FR-009** / Success Criteria **SC-004**, **SC-006**

---

## Executive Summary

This investigation localized the behavioral failure mechanisms underlying the Bluetooth HID warm registration failure reported in `docs/backlog/fido2-warm-registration-security-key-failure.md` (warm-08 RP `NotAllowedError` / Windows "No se puede leer la clave de seguridad" after repeated FIDO2 registrations in a single process).

Deterministic host-side behavioral test harnesses were built in `feature/fido2/src/androidHostTest/` without modifying production code (per **FR-006**). The investigation evaluated three static analysis defect hypotheses (Issues 1, 2, and 4) under modeled JVM conditions.

---

## §1 Per-Issue Verdicts (FR-009 Taxonomy)

The investigation applies the FR-009 modeled verdict taxonomy:
- **Confirmed Model Contributor**
- **Confirmed Model Non-Contributor**
- **Inconclusive / Untestable (Rationale Documented)**

### Issue 1: `ExecutorService` Thread Accumulation / Leak in `BluetoothHidDeviceWrapper.registerApp()`

- **Modeled Verdict**: **Confirmed Model Contributor**
- **Behavioral Evidence**:
  - Test Suite: [`BluetoothHidWarmRegistrationStressTest`](file:///D:/octav/source/repos/.worktrees/chimali/063-hid-warm-reg-failure/feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/bluetooth/BluetoothHidWarmRegistrationStressTest.kt)
  - Method: `warmRegistrationStress_tenCycles_recordsExecutorTerminationState()`
  - Measurement Protocol: $N = 10$ warm cycles, with 1 controlled framework retry in cycle 3, yielding $M = 11$ total captured `(executor, callback)` pairs.
  - Observed $R_c$ Series: `[1, 2, 4, 5, 6, 7, 8, 9, 10, 11]` (monotonically non-decreasing).
  - Pre-Cleanup Observation:
    - $V_{\text{final}} = [\text{false}, \text{false}, \text{false}, \text{false}, \text{false}, \text{false}, \text{false}, \text{false}, \text{false}, \text{false}, \text{false}]$
    - $R_{\text{final}} = 11$ non-terminated of $M = 11$ total allocations ($R_{\text{final}} = M$).
- **Analysis**:
  Production code at `BluetoothHidDeviceWrapper.kt:550` allocates a fresh `Executors.newSingleThreadExecutor()` per attempt without retaining the reference or invoking `shutdown()` during `unregisterApp()`, cycle completion, or error teardown. Under the harness, every allocated single-thread executor worker remains alive indefinitely, confirming linear background thread accumulation in a warm process lifecycle.

---

### Issue 2: Continuation Cancellation / Premature Resolution via Stale Callback (Mechanism A)

- **Modeled Verdict**: **Confirmed Model Non-Contributor**
- **Behavioral Evidence**:
  - Test Suite: [`BluetoothHidCallbackCoordinatorTest`](file:///D:/octav/source/repos/.worktrees/chimali/063-hid-warm-reg-failure/feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/bluetooth/BluetoothHidCallbackCoordinatorTest.kt)
  - Method: `callbackScenario1_mechanismA_staleEventBeforeCurrent_recordsInterference()`
  - Observations:
    1. Session $k$ receives `callback_k = coordinator.createRegistrationCallback(cont_k)`.
    2. Session $k+1$ initiates in-flight `cont_{k+1}` via `callback_{k+1} = coordinator.createRegistrationCallback(cont_{k+1})`.
    3. Stale event `onAppStatusChanged(registered=false)` is delivered to `callback_k`.
    4. Intermediate Assertion: `cont_{k+1}.isActive` remained `true` and the coroutine job remained pending.
    5. Current event `onAppStatusChanged(registered=true)` is delivered to `callback_{k+1}`.
    6. `cont_{k+1}` cleanly resolved with `Result.success(Unit)`.
- **Analysis**:
  In `BluetoothHidCallbackCoordinator.kt:345-365`, `createRegistrationCallback()` constructs an anonymous `BluetoothHidDevice.Callback` instance whose closure captures exclusively its own `cont: CancellableContinuation<Result<Unit>>`. There is no shared `registrationContinuation` field. Delivering a stale deregistration event to `callback_k` cannot directly cancel or resume `cont_{k+1}`. Direct continuation interference via Mechanism A is ruled out.

---

### Issue 4: Asynchronous Deregistration Shared-State Overwrite (Mechanism B / Late Stale Disruption)

- **Modeled Verdict**: **Confirmed Model Contributor**
- **Behavioral Evidence**:
  - Test Suite: [`BluetoothHidCallbackCoordinatorTest`](file:///D:/octav/source/repos/.worktrees/chimali/063-hid-warm-reg-failure/feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/bluetooth/BluetoothHidCallbackCoordinatorTest.kt)
  - Method: `callbackScenario2_mechanismB_lateStaleAfterSuccess_recordsSessionStability()`
  - Observations:
    1. Session $k+1$ registers successfully: `callback_{k+1}.onAppStatusChanged(registered=true)` resolves `cont_{k+1}` and transitions `owner.connectionState` to `HidConnectionState.Advertising` (active generation $G$).
    2. An asynchronous, delayed `registered=false` from predecessor session $k$'s `unregisterApp()` arrives late at `callback_k`.
    3. `callback_k.onAppStatusChanged(null, false)` delegates to `hidCallback.onAppStatusChanged(null, false)`.
    4. Post-event Assertion: `owner.connectionState.value` was overwritten from `Advertising` to `HidConnectionState.Idle`, and `owner.generation` was incremented to $G + 1$.
- **Analysis**:
  While `callback_k` cannot resolve `cont_{k+1}`, it unconditionally invokes the shared `hidCallback.onAppStatusChanged(pluggedDevice, false)` at `BluetoothHidCallbackCoordinator.kt:351`. In the existing implementation, callbacks lack generation tokens. When an asynchronous deregistration arrives after a new session has started advertising, it destroys the active session state and resets the transport to `Idle`.

---

### Control Validation: Genuine Framework Failure Distinguishability

- **Behavioral Evidence**:
  - Method: `callbackScenario3_control_genuineFailure_resolvesWithFailure()`
  - Observations:
    - Delivery of `registered=false` to the current `callback_{k+1}` resolved `cont_{k+1}` with `Result.failure(Fido2Exception.BluetoothException)`.
- **Analysis**:
  Confirms the harness accurately discriminates between stale deregistration events and genuine framework registration failures, validating the integrity of Scenarios 0, 1, and 2.

---

## §2 Status of Deferred Requirements

- **FR-007 (HidChannelRegistry Reset Invariants)**:
  - **Status**: **Deferred to follow-on stability feature** (e.g. `064-*`).
  - **Rationale**: Static analysis proved `BluetoothHidTransportImpl.onWrapperSessionTransition()` invokes `clearChannelRegistry()`. Multi-session CID allocation testing is non-blocking for defect localization and was deferred per user instruction and spec discipline.
- **FR-008 (`core:bluetooth` DI Module Audit)**:
  - **Status**: **Deferred to codebase hygiene feature**.
  - **Rationale**: Static analysis confirmed `core:bluetooth` is isolated from the `feature:fido2` runtime DI graph.

---

## §3 Boundary Between Host Model Evidence and Physical Device Attribution

Per **FR-009 Verdict Decision Rules** and **Spec Assumptions §3**:
- **Host JVM Evidence**: Establishes mechanism validity in the Kotlin codebase under Constitution Principle V (deterministic test execution). The tests conclusively demonstrate that:
  1. The code linearly accumulates non-terminated `ExecutorService` instances across warm iterations.
  2. Late-delivered `registered=false` callbacks silently overwrite active session state to `Idle`.
- **Physical Device Attribution Boundary**:
  While these two defects explain the failure boundary observed on device (thread/resource exhaustion or asynchronous unregister race during rapid warm reconnects), host simulation alone does not prove which of the two (or combination) triggered the specific warm-08 failure on ASUS_AI2302 / Android 15. Physical device tracing during follow-on remediation verification remains the authoritative gate for real-world device resolution.

---

## §4 Backlog Closure Recommendation

- **Recommendation**: **ESCALATE TO REMEDIATION PLAN**
- **Action**: Open follow-on feature `064-hid-warm-reg-remediation` to apply targeted production fixes based on these findings:
  1. **Remediate Issue 1 (Executor Leak)**:
     - Retain the active `ExecutorService` in `BluetoothHidDeviceWrapper`.
     - Explicitly shut down prior executor instances on `unregisterApp()`, cycle reset, or retry exhaustion.
  2. **Remediate Issue 4 (Late Stale Disruption)**:
     - Tag `createRegistrationCallback` with the active `sessionGeneration` integer token.
     - In `onAppStatusChanged`, discard events whose generation does not match the active wrapper generation, preventing stale deregistration events from mutating shared state.
- **Submission Authority**:
  Submitted to the **FIDO2 Transport Lead / Repository Maintainer** for backlog item `docs/backlog/fido2-warm-registration-security-key-failure.md` §5 sign-off and closure.

---

## §5 Test Verification & Coverage Summary

### Test Suite Execution Summary

| Test Suite | Tests Run | Passed | Failed | Execution Time |
|---|---|---|---|---|
| `BluetoothHidWarmRegistrationStressTest` | 1 (10 cycles + retry) | 1 | 0 | 4.63s |
| `BluetoothHidCallbackCoordinatorTest` | 38 (includes 4 scenarios) | 38 | 0 | 9.17s |
| `BluetoothHidTransportLifecycleTest` | 15 (regression suite) | 15 | 0 | 15.72s |
| **Total** | **54** | **54** | **0** | **29.52s** |

All tests execute deterministically on the JVM host with zero flake. No production code was modified during this investigation.

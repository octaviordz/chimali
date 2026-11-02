# Research: Bluetooth HID Warm Registration Failure Investigation

**Feature Branch**: `063-hid-warm-reg-failure` | **Date**: 2026-11-02  
**Context**: Investigation of repeated warm FIDO2 security-key registration failures on Android (ASUS_AI2302 / Android 15), where warm-08 fails with RP `NotAllowedError` / Windows "No se puede leer la clave de seguridad".

---

## Decision 1: Host-Simulated Bounded Thread Harness vs. Physical Device Tracing

- **Context**: In `BluetoothHidDeviceWrapper.kt`, `Executors.newSingleThreadExecutor()` is called on every `registerApp()`. Warm sessions accumulate unmanaged executor threads unless shut down. We need repeatable, objective evidence of this accumulation.
- **Decision**: Implement a host-side behavioral test harness in `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/bluetooth/` using MockK and captured executor inspection. The test executes $N = 10$ consecutive `registerApp()` / `unregisterApp()` iterations against `BluetoothHidDeviceWrapper`. The mock substitute for `android.bluetooth.BluetoothHidDevice` must capture the `(executor, callback)` pair from each `hid.registerApp()` call and submit callback invocations (`onAppStatusChanged`) through the captured executor — not bypass it. This ensures executor worker threads are actually created and the captured executor references are the authoritative measurement unit for thread accumulation. After each cycle's callback is delivered and the cycle completes, the harness records the termination state of captured executors. Across all $M$ captured instances ($M \ge N$ accounting for retries), the harness records the pre-cleanup termination states: observing non-terminated executors ($R_{\text{final}} > 0$) characterizes production retention, with linear accumulation confirmed if $R_{\text{final}} = M$; conversely, $R_{\text{final}} = 0$ represents the desired post-remediation healthy bound.
- **Rationale**:
  - A physical device run requires extensive setup, pairing, manual taps or UI automation, and is prone to external environmental jitter.
  - A host test executes deterministically in seconds and fulfills the project constitution's requirement for deterministic behavioral verification.
  - Submitting callbacks through the captured executor rather than invoking them directly on the mock's calling thread ensures the `ThreadPoolExecutor` worker is created, making executor termination state the meaningful and attributable metric (rather than a noisy process-wide thread count that includes framework threads).
- **Alternatives Considered**:
  - *Process-wide `Thread.getAllStackTraces().size` comparison*: Rejected as the authoritative metric. Process-wide counts include JUnit, MockK, and coroutine dispatcher threads, requiring brittle baseline subtraction. Executor-attributed counting isolates exactly the registration path's resource leak.
  - *Physical device ADB thread inspection*: Rejected as primary gate due to device flakiness and lack of headless CI reproducibility. Preserved as secondary diagnostic validation.
  - *Static code inspection only*: Insufficient under backlog §5, which explicitly prohibits changes until evidence localizes the defect behaviorally.

---

## Decision 2: Callback Ordering & Delivery Race Simulation

- **Context**: `unregisterApp()` is asynchronous; its `onAppStatusChanged(registered=false)` callback arrives via Binder IPC. The production implementation of `createRegistrationCallback()` (lines 345–403 of `BluetoothHidCallbackCoordinator.kt`) returns a distinct `BluetoothHidDevice.Callback` object per invocation, each capturing the continuation for that specific registration attempt. There is no shared `registrationContinuation` slot in the coordinator. Two distinct interference mechanisms must be evaluated separately.
- **Decision**: Add deterministic callback sequencing tests to `BluetoothHidCallbackCoordinatorTest`. The harness must:
  1. Call `createRegistrationCallback(cont_k)` to obtain `callback_k` (old registration callback capturing `cont_k`).
  2. Call `createRegistrationCallback(cont_{k+1})` to obtain `callback_{k+1}` (new registration callback capturing `cont_{k+1}`).
  3. Deliver events to the named callback object that would actually receive them in production — not to the permanent `hidCallback` directly, which bypasses continuation resolution.

  Four scenarios must be evaluated:
  1. **Scenario 0: Baseline — Nominal sequential delivery**: Deliver `registered=false` to `callback_k` and await completion before initiating `registerApp(cont_{k+1})`. Assert: `cont_{k+1}` cleanly resolves with `Result.success(Unit)`.
  2. **Scenario 1: Mechanism A — Shared-state interference (nominal stale ordering)**: Deliver `onAppStatusChanged(registered=false)` through `E_k` to `callback_k` (old). Enforce a bounded completion barrier. Observe whether the resulting session-generation advance and `Idle` publication disrupts the in-flight `cont_{k+1}` or wrapper state. Then deliver `registered=true` through `E_{k+1}` to `callback_{k+1}`. Assert: `cont_{k+1}` is neither completed nor cancelled by the stale event, and resolves with `Result.success(Unit)` upon current event delivery.
  3. **Scenario 2: Mechanism B — Race delivery (late-stale ordering)**: Deliver `registered=true` through `E_{k+1}` to `callback_{k+1}`, await `cont_{k+1}` resolution (`Result.success(Unit)`), then deliver late stale `registered=false` through `E_k` to `callback_k`. Assert: late stale event does not corrupt the newly registered active session.
  4. **Scenario 3: Control — Genuine current-registration failure**: Deliver `registered=false` directly through `E_{k+1}` to `callback_{k+1}` (simulating a genuine new registration failure). Assert: `cont_{k+1}` resolves with `Result.failure(...)`. This distinguishes "stale event incorrectly fails new attempt" from "new attempt genuinely fails."
- **Rationale**:
  - Each `createRegistrationCallback()` invocation captures its own continuation in a closure. Direct invocation of `hidCallback` does not resolve any registration's continuation and cannot reproduce the per-registration interference being investigated. The harness must use the actual callback objects returned by `createRegistrationCallback()` to route events faithfully.
  - Two interference mechanisms are plausible and must be kept distinct: (a) a stale callback directly resolving the new continuation (requires callback routing that production may not provide), and (b) shared lifecycle state (session generation, `Idle` publication) disrupting the new registration indirectly.
  - Adding explicit bounded completion barriers between executor dispatches guarantees deterministic execution ordering across independent single-thread executors.
  - A nominal sequential baseline provides a verified control for harness execution.
  - A control scenario using genuine current-registration failure prevents the harness from treating every `false` event as stale.
- **Alternatives Considered**:
  - *Direct `hidCallback.onAppStatusChanged(...)` invocation*: Rejected. In production, the permanent `hidCallback` does not hold a per-registration continuation. Invoking it directly bypasses `createRegistrationCallback()`'s closure and cannot demonstrate continuation interference as it would occur on device.
  - *Artificial delay insertion (`Thread.sleep`)*: Rejected as non-deterministic and flaky. Explicit step-by-step callback invocation provides deterministic assertion of state transitions.

---

## Decision 3: Deferral of Channel Registry Reset Invariants (FR-007)

- **Context**: `HidChannelRegistry` allocates CTAPHID channel IDs up to `maximumChannels` (16). Static analysis showed `BluetoothHidTransportImpl.onWrapperSessionTransition()` calls `clearChannelRegistry()`.
- **Decision**: Defer behavioural testing of multi-session generation CID allocation to a follow-on stability verification feature (e.g. `064-*`). Do NOT generate tasks for FR-007 in feature 063.
- **Rationale**:
  - Finding A in static analysis already proved that `clearChannelRegistry()` is invoked on each session transition.
  - User direction and scope discipline (FR-006 / Constitution Principle VII) restrict feature 063 exclusively to the primary executor-leak and callback-race hypotheses.

---

## Decision 4: Deferral of `core/bluetooth` Module Audit (FR-008)

- **Context**: `core/bluetooth` contains a keyboard descriptor (`BluetoothHidConstants.kt`) and an unreferenced `BluetoothHidAuthenticator` implementation.
- **Decision**: Defer DI graph audit and cleanup of `core/bluetooth` to a separate codebase hygiene feature. Do NOT generate tasks for FR-008 in feature 063.
- **Rationale**:
  - Static analysis Finding B established that `core/bluetooth` is isolated from the `feature/fido2` graph.
  - Keeping investigation scope bounded to the primary failure causes avoids task sprawl.

---

## Summary of Decisions

| Topic | Selected Approach | Key Benefit |
|---|---|---|
| Executor Leak Evidence (FR-001, FR-002) | Host test capturing each `(executor, callback)` pair, submitting callbacks through the captured executor, taking pre-cleanup termination observations after bounded completion waits, and recording retained executor count series $R_c$ and fresh $R_{\text{final}}$ across $M$ allocations | Executor-attributed metric is attributable and immune to framework thread noise; separates pre-cleanup production evidence from harness teardown |
| Callback Race Hazard (FR-003) | Separate old and new `BluetoothHidDevice.Callback` instances obtained from `createRegistrationCallback()`; distinct Scenario 0 (baseline), Scenario 1 (shared-state Mechanism A), Scenario 2 (late-stale Mechanism B) schedules with bounded barriers, plus Scenario 3 control (genuine current failure) | Faithfully reproduces per-registration closure structure; prevents false attribution |
| Channel Registry Cap (FR-007) | Deferred to follow-on feature | Preserves focus on primary hypotheses |
| `core:bluetooth` Isolation (FR-008) | Deferred to follow-on feature | Preserves focus on primary hypotheses |

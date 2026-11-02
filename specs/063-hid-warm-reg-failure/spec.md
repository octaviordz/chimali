# Feature Specification: Bluetooth HID Warm Registration Failure Investigation

**Feature Branch**: `063-hid-warm-reg-failure`

**Created**: 2026-11-02

**Status**: Draft

**Input**: User description: "Investigate and document Bluetooth HID warm registration failure — executor leak and callback race in BluetoothHidDeviceWrapper.registerApp() causing NotAllowedError after 8 warm FIDO2 registrations in the same Android process"

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Bounded warm-session registration reliability (Priority: P1)

A developer or automated test agent runs 10 or more consecutive FIDO2 warm registration ceremonies against the same Android Chimali process (without restarting the app). The target post-remediation goal is that the app successfully completes every registration without producing a `NotAllowedError` or an equivalent HID-layer failure; under the current investigation feature, the harness executes the multi-cycle sequence to observe and characterize actual failure boundaries and resource state.

**Why this priority**: The warm-08 failure directly blocks users performing repeated FIDO2 security-key registrations in a single session. It is reproducible, device-confirmed, and attributed to a specific code path.

**Independent Test**: Can be fully tested by executing N consecutive `registerApp()` + `unregisterApp()` cycles in a host-side behavioral test and recording completion results and resource lifecycle state. Delivers diagnostic evidence and failure characterization suitable for designing bounded remediation.

**Acceptance Scenarios**:

1. **Target Healthy Bound (Given-When-Then)**: **Given** the app process has completed 7 prior warm registration cycles, **When** an 8th `registerApp()` call is made, **Then** the registration resolves as success and the HID profile is presented to the Windows host. *(Note: Target post-remediation property; current investigation characterizes actual behavior under test)*.
2. **Resource Scaling Characterization**: **Given** N consecutive `registerApp()` cycles are executed resulting in $M$ total executor allocations (accounting for retries), **When** each completes, **Then** the harness evaluates the retained non-terminated executor count; zero non-terminated executors characterizes the target healthy bound, while monotonic accumulation across cycles characterizes linear executor retention.
3. **Isolation Verification**: **Given** an `unregisterApp()` call returns asynchronously, **When** the corresponding `registered=false` callback arrives after a new `registerApp()` has already been initiated, **Then** the harness tests whether the new registration's continuation or session state is disrupted, recording the outcome as behavioral evidence.

---

### User Story 2 — Observable evidence of resource boundedness (Priority: P2)

A developer running behavioral tests can verify and record the lifecycle state of process-level resources (executors, callback registrations) associated with the HID registration path across N warm cycles.

**Why this priority**: Without observable, testable resource boundedness measurements, any defect diagnosis remains speculative. Evidence-based characterization is required before production changes per backlog §5.

**Independent Test**: Can be fully tested by a host-side test that inspects the termination state of all captured `ExecutorService` instances across N registration cycles. Delivers evidence suitable for closing the investigation backlog item.

**Acceptance Scenarios**:

1. **Given** a host-side test that calls `registerApp()` N times (N ≥ 10) with a mock Bluetooth HID profile, **When** each call completes (including any retry attempts within each cycle) and callbacks are processed through their captured executors, **Then** the harness records the termination state of each captured executor prior to test cleanup; observing any non-terminated executor characterizes the defect, and zero non-terminated executors characterizes the target healthy bound.
2. **Given** a behavioral test that simulates the `registered=false` callback from `unregisterApp()` arriving out of order (Scenario 1 nominal stale or Scenario 2 late stale), **When** a new `registerApp()` has already posted its callback, **Then** the test observes whether shared-state transitions disrupt the active continuation, recording the result as diagnostic evidence.

---

### User Story 3 — Regression safety for cold-start and single-registration paths (Priority: P3)

All existing warm-start tests and single-registration ceremonies continue to pass after any remediation is applied.

**Why this priority**: Changes to the registration lifecycle must not break the successful common case.

**Independent Test**: Can be fully tested by running the existing `BluetoothHidTransportLifecycleTest` and `BluetoothHidCallbackCoordinatorTest` suites and asserting no regressions.

**Acceptance Scenarios**:

1. **Given** the existing lifecycle test suite covering single `registerApp()` success and failure paths, **When** run after any code changes, **Then** all tests pass with no regressions.
2. **Given** a cold-start registration (first `registerApp()` in a fresh process), **When** the registration completes, **Then** the outcome is identical to the current behavior.

---

### Edge Cases

- **Bluetooth adapter reset mid-session (e.g., airplane mode toggled)**: Out of scope for the host-side test harness. Adapter reset requires the real Android `STATE_OFF` broadcast path through `BroadcastReceiver`. Deferred to physical-device validation only; must be documented explicitly as unverified in the investigation evidence.
- **Concurrent / Overlapping `registerApp()` calls**: Production `BluetoothHidDeviceWrapper.registerApp()` does not acquire a lock around its execution body, so concurrent invocations would create overlapping `suspendCancellableCoroutine` blocks and concurrent framework registrations. However, caller-level transport logic (`BluetoothHidTransportImpl.connect()`) is invoked sequentially across warm ceremony sessions. Investigating concurrent caller invocations is out of scope for this feature; the host harness tests sequential warm cycles. If an overlapping `registerApp()` scenario is executed, the harness records the behavior of concurrent active continuations as diagnostic evidence.
- **Asynchronous unregistration arrival during new registration**: Handled explicitly via Scenario 1 (nominal stale ordering) and Scenario 2 (late-stale ordering).
- **Maximum internal retry exhaustion**: `BluetoothHidDeviceWrapper.registerApp()` implements a retry loop of up to 5 attempts (1 initial attempt + 4 retries, or up to 5 framework allocations per cycle). If framework registration fails repeatedly, up to $5N$ executors may be allocated across $N$ cycles. The harness must support simulating controlled internal failure to exercise retry allocation tracking.
- **Profile proxy disconnection**: What happens if the Android BluetoothHidDevice profile service disconnects and reconnects between warm sessions (deferred to physical device).

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The investigation MUST produce a behaviorally executable test that exercises `BluetoothHidDeviceWrapper.registerApp()` N times (N ≥ 10) in a single process lifetime. The mock boundary is `android.bluetooth.BluetoothHidDevice` (unavailable on the host JVM); `Executors.newSingleThreadExecutor()` inside `BluetoothHidDeviceWrapper.registerApp()` MUST run as unmodified production code so that executor allocation is directly observable. The mock must capture the `(executor, callback)` pair from each `hid.registerApp()` call and submit callback invocations through the captured executor — not bypass it — so that executor worker threads are actually created. The harness must record the pre-cleanup termination state of each captured executor after its associated cycle completes following a bounded synchronization barrier.
- **FR-002**: The investigation MUST measure and record the pre-cleanup termination state of the $M$ total `ExecutorService` instances captured across $N$ registration cycles (accounting for all attempts and retries, where $M \ge N \ge 10$, with up to 5 attempts per cycle). The test must record the retained non-terminated executor count $R_c$ per cycle $c \in 1..N$ and a fresh final count $R_{\text{final}} = \sum_{j=1}^M (1 - E_j.\text{isTerminated}())$ across all $M$ captured instances after bounded completion synchronization. The harness must also execute a controlled retry scenario where an initial framework failure causes internal retry before eventual success or exhaustion, verifying that executors from failed attempts are tracked in $M$. If $R_{\text{final}} > 0$, executor retention is observed; if $R_c$ grows with cycle count and $R_{\text{final}} = M$, linear executor accumulation under the harness is confirmed. Conversely, $R_{\text{final}} = 0$ represents the healthy resource bound. The harness must perform cleanup (calling `shutdown()` and bounded `awaitTermination()`) only after recording all pre-cleanup observations.
- **FR-003**: The investigation MUST distinguish two distinct `registered=false` event sources — (a) the stale deregistration event delivered to the old `BluetoothHidDevice.Callback` instance returned by the prior `createRegistrationCallback()` call, and (b) a genuine failure outcome delivered to the current callback instance — and test: (1) Scenario 0 (Baseline: nominal sequential unregister completely delivered before next register begins), (2) Scenario 1 (Mechanism A: nominal stale ordering with bounded barrier), (3) Scenario 2 (Mechanism B: late-stale delivery with bounded barrier), and (4) Scenario 3 (Control: genuine current-registration failure). The harness must record whether stale events disrupt active registrations and verify that the control resolves with failure.
- **FR-004**: The investigation MUST document which modeled defect hypotheses from the static analysis (Issues 1, 2, and 4) are confirmed or refuted by behavioral evidence under the host harness, and note Issue 3 as deferred per FR-007. Distinguish modeled mechanism validity from real-world device incident attribution throughout.
- **FR-006**: The investigation MUST NOT modify production behavior until behavioral evidence localizes the failure and a bounded correction is justified (per backlog §5). Any code corrections are out of scope for this feature and belong to a separate follow-on feature.
- **FR-007 [DEFERRED]**: The verification of `HidChannelRegistry` reset invariants across warm re-registrations is deferred to a follow-on stability verification feature (e.g. `064-*`) and will NOT generate tasks in `tasks.md` for feature 063.
- **FR-008 [DEFERRED]**: The audit and quarantine of the `core/bluetooth` DI module is deferred to a cleanup feature and will NOT generate tasks in `tasks.md` for feature 063.
- **FR-009**: The investigation MUST produce a `specs/063-hid-warm-reg-failure/findings.md` document containing: a per-issue verdict for each of the static analysis hypotheses (Issues 1, 2, and 4; Issue 3 is noted as deferred per FR-007), and an explicit backlog closure recommendation (close / escalate / defer) with rationale referencing behavioral test evidence.
  - *Verdict Decision Rules (Separating Model Evidence from Device Incident Attribution)*:
    - **Confirmed Model Contributor**: Behavioral evidence in the host harness reproduces the hypothesized failure mechanism under modeled conditions (e.g., executor worker retained and accumulation scales linearly with $M$; or stale callback cancels/fails the new registration continuation or destabilizes session state). Note: Establishes mechanism validity in code, but does not alone establish device incident causation.
    - **Confirmed Model Non-Contributor**: Behavioral evidence under valid test conditions conclusively demonstrates isolation (e.g., stale callbacks have zero effect on continuation or session state across all schedules, or resources reset deterministically).
    - **Inconclusive / Untestable (Rationale Documented)**: Mechanism cannot be verified or ruled out on the host JVM due to platform framework constraints (e.g., requires real Android Bluetooth daemon / Binder IPC / hardware adapter state), or test observations yielded mixed/unresolved outcomes.
  - *Closure Decision Authority*: Per project workflow, the findings document and backlog closure recommendation will be submitted to the repository code owner / **FIDO2 Transport Lead** for review and determination on whether to close, escalate, or open a follow-on remediation task.

### Key Entities

- **Warm Registration Session**: A single `connect()` → `registerApp()` → use → `unregisterApp()` cycle performed without restarting the Android process.
- **Executor Lease**: The `java.util.concurrent.ExecutorService` instance created by `Executors.newSingleThreadExecutor()` inside `BluetoothHidDeviceWrapper.registerApp()` and passed to `hid.registerApp()`. The lifecycle of this object determines thread accumulation. The test harness must capture this reference per call and submit callbacks through it.
- **Stale Deregistration Callback**: The `BluetoothHidDevice.Callback` object returned by a prior `createRegistrationCallback()` call. Receives the `onAppStatusChanged(registered=false)` from the matching `unregisterApp()`. Its Binder reference and shared-state effects (session generation advance, `Idle` publication) are the source of potential cross-registration interference.
- **Current Registration Callback**: The `BluetoothHidDevice.Callback` object returned by the most recent `createRegistrationCallback()` call. Holds the active continuation for the in-flight `registerApp()`. Only events delivered to this instance can directly resolve its captured continuation.
- **Session Generation**: The monotonically increasing counter in `BluetoothHidDeviceWrapper` that identifies a distinct Bluetooth HID proxy acquisition lifetime. Advancing the generation is a shared-state effect of `onAppStatusChanged(registered=false)` that may disrupt an in-flight registration.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 10 or more consecutive warm registration cycles execute within a single test harness process, demonstrating that the multi-iteration harness successfully exercises the registration/deregistration sequence deterministically.
- **SC-002**: The behavioral test records the pre-cleanup termination state of all $M$ captured `ExecutorService` instances across $N$ registration cycles ($M \ge N \ge 10$, with up to 5 attempts per cycle). Pre-cleanup observation occurs after a bounded completion barrier (e.g. 2000ms) and prior to test-owned teardown. The test evaluates the per-cycle retention series $R_c$, exercises a controlled retry scenario, and performs a fresh final evaluation across all $M$ instances: $R_{\text{final}} = \sum_{j=1}^M (1 - E_j.\text{isTerminated}())$. If $R_{\text{final}} > 0$, executor retention is recorded; if $R_{\text{final}} = M$ and $R_c$ increases monotonically, linear accumulation is confirmed as an investigation finding ($R_{\text{final}} = 0$ represents the target post-remediation healthy bound). The harness then performs teardown by invoking `shutdown()` and bounded `awaitTermination()`, logging any cleanup timeout without altering the recorded pre-cleanup evidence.
- **SC-003**: Behavioral tests exist and execute covering four callback delivery schedules: (0) Scenario 0 (Baseline nominal sequential delivery), (1) Scenario 1 (Mechanism A nominal stale ordering), (2) Scenario 2 (Mechanism B late-stale ordering), and (3) Scenario 3 (Control genuine failure). The test records whether stale callbacks disrupt the current registration's continuation or session state, and confirms that Scenario 3 resolves with `Result.failure(...)`.
- **SC-004**: Each of the targeted static-analysis hypotheses (Issues 1, 2, and 4) is classified using the FR-009 taxonomy ("confirmed model contributor," "confirmed model non-contributor," or "inconclusive / untestable — rationale documented") based on behavioral evidence, with Issue 3 documented as deferred per FR-007.
- **SC-005**: All existing lifecycle and coordinator tests continue to pass with no regressions.
- **SC-006**: A `findings.md` document exists in `specs/063-hid-warm-reg-failure/` containing: (1) per-issue verdicts for Issues 1, 2, and 4 using the FR-009 modeled verdict taxonomy, (2) documentation of Issue 3 as deferred, (3) clear separation between host-modeled mechanism evidence and device incident attribution, and (4) an explicit backlog closure recommendation referencing behavioral test evidence submitted to the FIDO2 Transport Lead — sufficient to close or escalate `fido2-warm-registration-security-key-failure.md` per its §5 completion criteria.

---

## Clarifications

### Session 2026-11-02

- Q: Should the behavioral test harness simulate Bluetooth adapter resets as a required test scenario, or defer to physical-device validation? → A: Deferred to physical-device validation only. Adapter reset edge case is explicitly out of scope for the host harness and must be documented as unverified in investigation evidence.
- Q: When FR-001 says "without mocking the executor or callback delivery," does this mean the host test must use a real `Executors` thread rather than a test dispatcher? → A: Yes — the `BluetoothHidDevice` profile is mocked (unavailable on host JVM), but `Executors.newSingleThreadExecutor()` inside `registerApp()` MUST run the unmodified production code path so live thread accumulation is observable.
- Q: Should the investigation produce a structured evidence file as a required deliverable, or is a passing test suite sufficient to close or escalate the backlog item? → A: Required — a `findings.md` document in `specs/063-hid-warm-reg-failure/` with per-issue modeled verdicts (confirmed model contributor / confirmed model non-contributor / inconclusive or untestable — rationale documented; Issue 3 noted as deferred) and a backlog closure recommendation.
- Q: Should the thread-boundedness assertion in SC-002 be measured against total thread-creation opportunities (including retries) or only against the iteration count N? → A: Measured via captured executor instances: every captured executor instance $E_i$ (including retries) is tracked. The pre-cleanup termination observation records whether production releases or terminates executors, observing the series $R_i$ across all attempts.
- Q: When FR-005 states that any correction applied must have a before/after behavioral test, does that correction work belong inside this feature (063) or must it be scoped to a separate remediation feature? → A: Out of scope — 063 is investigation only; FR-005 removed; any code correction will be captured in a separate follow-on feature once behavioral evidence from this investigation localizes the defect.

---

## Assumptions

- The investigation targets the Android 15 / ASUS_AI2302 environment as the primary device profile, consistent with the backlog item's observed failure environment.
- The warm-08 failure threshold is reproducible: given the same process lifecycle, failure occurs no later than the 9th warm registration. Behavioral tests will attempt N = 10 as the minimum threshold.
- Host-side tests (running on JVM without a physical Bluetooth adapter) using test doubles for `BluetoothHidDevice` are accepted as primary evidence for **modeled JVM-level mechanisms** (executor allocation/retention, callback closure mechanics, and callback sequencing) under **Constitution Principle V (DO-178B Rigor & Deterministic Test Execution)**. However, per the backlog item's evidence requirements, host-simulated evidence characterizes behavior only under the tested model; physical-device verification is required to confirm whether a modeled mechanism caused the real-world device warm-08 failure.
- The `before_specify` mandatory hook (`speckit.git.feature`) was executed and branch `063-hid-warm-reg-failure` was created before this specification was written.
- No production code changes are permitted during the investigation phase. All assertions must be made by adding or modifying tests only. Any code corrections identified by the investigation will be planned and implemented in a separate follow-on feature (e.g., `064-*`).
- FR-007 (channel registry reset verification) and FR-008 (core/bluetooth DI module audit) are deferred to follow-on stability/cleanup features and will not generate tasks in `tasks.md` for feature 063.
- The analysis document at `specs/063-hid-warm-reg-failure/analysis.md` provides the static-analysis basis for this specification's requirements.
- Coverage gates (Tier 1 ≥ 60%, Global ≥ 50%) defined in the project constitution apply to any new tests added during this investigation.

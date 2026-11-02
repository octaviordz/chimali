# Feature Specification: Bluetooth HID Warm Registration Failure Investigation

**Feature Branch**: `063-hid-warm-reg-failure`

**Created**: 2026-11-02

**Status**: Draft

**Input**: User description: "Investigate and document Bluetooth HID warm registration failure — executor leak and callback race in BluetoothHidDeviceWrapper.registerApp() causing NotAllowedError after 8 warm FIDO2 registrations in the same Android process"

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Bounded warm-session registration reliability (Priority: P1)

A developer or automated test agent runs 10 or more consecutive FIDO2 warm registration ceremonies against the same Android Chimali process (without restarting the app). The app successfully completes every registration without producing a `NotAllowedError` or an equivalent HID-layer failure.

**Why this priority**: The warm-08 failure directly blocks users performing repeated FIDO2 security-key registrations in a single session. It is reproducible, device-confirmed, and attributed to a specific code path.

**Independent Test**: Can be fully tested by executing N consecutive `registerApp()` + `unregisterApp()` cycles in a host-side behavioral test and asserting every cycle completes with success. Delivers confidence that the registration layer is stable over an extended process lifetime.

**Acceptance Scenarios**:

1. **Given** the app process has completed 7 prior warm registration cycles, **When** an 8th `registerApp()` call is made, **Then** the registration resolves as success and the HID profile is presented to the Windows host.
2. **Given** N consecutive `registerApp()` calls are executed, **When** each completes (success or failure), **Then** the number of live background threads in the process does not increase across calls — it remains constant (O(1)) regardless of N.
3. **Given** an `unregisterApp()` call returns asynchronously, **When** the corresponding `registered=false` callback arrives after a new `registerApp()` has already been initiated, **Then** the new registration's completion signal is not affected — it resolves independently on its own outcome.

---

### User Story 2 — Observable evidence of resource boundedness (Priority: P2)

A developer running behavioral tests can verify that process-level resources (threads, callback registrations) associated with the HID registration path do not accumulate across N warm cycles.

**Why this priority**: Without observable, testable resource boundedness, any fix risks regressing silently. Evidence-based confirmation is required before production changes per backlog §5.

**Independent Test**: Can be fully tested by a host-side test that inspects thread count before and after N registration cycles and asserts no growth. Delivers evidence suitable for closing the investigation backlog item.

**Acceptance Scenarios**:

1. **Given** a host-side test that calls `registerApp()` N times (N ≥ 10) with a mock Bluetooth HID profile, **When** each call completes, **Then** the live thread count after N calls equals the live thread count after the first call — no threads are orphaned.
2. **Given** a behavioral test that simulates the `registered=false` callback from `unregisterApp()` arriving out of order, **When** a new `registerApp()` has already posted its callback, **Then** the test asserts that only the latest registration's outcome is delivered to the caller.

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

- What happens when the Bluetooth adapter resets mid-warm-session (e.g., airplane mode toggled)?
- How does the system handle a `registerApp()` call while an earlier `unregisterApp()` callback is still outstanding?
- What happens if the Android BluetoothHidDevice profile service disconnects and reconnects between warm sessions?
- How does the registration path behave when the maximum number of retries (currently 5) is reached on every warm attempt?

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The investigation MUST produce a behaviorally executable test that exercises `registerApp()` N times (N ≥ 10) in a single process lifetime against a mock or real BluetoothHidDevice profile, without mocking the executor or callback delivery.
- **FR-002**: The investigation MUST determine whether the live thread count after N registration cycles is bounded (constant) or unbounded (grows with N), and record the result as evidence.
- **FR-003**: The investigation MUST determine whether a `registered=false` callback from a prior `unregisterApp()` can resolve a subsequent registration attempt's completion with failure, and record the finding.
- **FR-004**: The investigation MUST document which issues from the static analysis (Issues 1–4 in the analysis) are confirmed as contributors to the warm-08 failure by behavioral evidence, and which remain hypothetical.
- **FR-005**: Any correction applied MUST be accompanied by a behavioral test that fails before the correction and passes after, demonstrating localization of the defect.
- **FR-006**: The investigation MUST NOT modify production behavior until behavioral evidence localizes the failure and a bounded correction is justified (per backlog §5).
- **FR-007**: The investigation MUST confirm or refute whether the `HidChannelRegistry` accumulates stale entries across warm re-registrations, using a deterministic multi-session test sequence.
- **FR-008**: The investigation MUST audit whether the `core/bluetooth` DI module's types are reachable from the `feature/fido2` DI graph in any build configuration, and document the result.

### Key Entities

- **Warm Registration Session**: A single `connect()` → `registerApp()` → use → `unregisterApp()` cycle performed without restarting the Android process.
- **Executor Lease**: The `java.util.concurrent.ExecutorService` instance created inside `registerApp()` and passed to `hid.registerApp()`. The lifecycle of this object determines thread accumulation.
- **Registration Callback**: The `BluetoothHidDevice.Callback` object created per `registerApp()` invocation; its Binder reference lifetime in the Android framework is the source of cross-session interference.
- **Session Generation**: The monotonically increasing counter in `BluetoothHidDeviceWrapper` that identifies a distinct Bluetooth HID proxy acquisition lifetime. Used to discriminate stale from current callbacks.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 10 or more consecutive warm registration cycles complete without failure in a single process lifetime, as demonstrated by a passing behavioral test.
- **SC-002**: The number of live background threads in the process after N registration cycles (N ≥ 10) is the same as after 1 cycle — no orphaned threads accumulate.
- **SC-003**: A behavioral test exists that asserts the `registered=false` callback from a prior unregister cannot fail a subsequent registration's completion signal, regardless of delivery ordering.
- **SC-004**: Each of the 4 issues identified in the static analysis is classified as either "confirmed contributor," "confirmed non-contributor," or "untestable — rationale documented," based on behavioral evidence.
- **SC-005**: All existing lifecycle and coordinator tests continue to pass with no regressions.
- **SC-006**: The investigation evidence is sufficient to either close or escalate the backlog item `fido2-warm-registration-security-key-failure.md` per its §5 investigation procedure.

---

## Assumptions

- The investigation targets the Android 15 / ASUS_AI2302 environment as the primary device profile, consistent with the backlog item's observed failure environment.
- The warm-08 failure threshold is reproducible: given the same process lifecycle, failure occurs no later than the 9th warm registration. Behavioral tests will attempt N = 10 as the minimum threshold.
- Host-side tests (running on JVM without a physical Bluetooth adapter) using test doubles for `BluetoothHidDevice` are accepted as primary evidence for the executor leak and callback race issues; physical-device confirmation is secondary.
- The `before_specify` mandatory hook (`speckit.git.feature`) was executed and branch `063-hid-warm-reg-failure` was created before this specification was written.
- No production code changes are permitted during the investigation phase. All assertions must be made by adding or modifying tests only.
- The analysis document at `specs/063-hid-warm-reg-failure/analysis.md` provides the static-analysis basis for this specification's requirements.
- Coverage gates (Tier 1 ≥ 60%, Global ≥ 50%) defined in the project constitution apply to any new tests added during this investigation.

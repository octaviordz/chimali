---

description: "Task list for Bluetooth HID Warm Registration Failure Investigation (feature 063)"
---

# Tasks: Bluetooth HID Warm Registration Failure Investigation

**Input**: Design documents from `specs/063-hid-warm-reg-failure/`

**Branch**: `063-hid-warm-reg-failure` | **Date**: 2026-11-02

**Prerequisites**: `plan.md`, `spec.md`, `data-model.md`, `contracts/warm-registration-lifecycle.md`, `research.md`

**Scope Constraint**: ALL new code MUST be confined to `feature/fido2/src/androidHostTest/`. NO production code changes permitted (FR-006). FR-007 (channel registry) and FR-008 (core/bluetooth) are explicitly deferred — no tasks generated for them.

**Coverage Gates (Constitution V)**: Any new test file must contribute toward Tier 1 ≥ 60% and Global ≥ 50% instruction coverage.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm test infrastructure is ready and the `androidHostTest` source set can compile and run the executor-capture harness. No production changes.

- [ ] T001 Verify `feature/fido2/src/androidHostTest/` source set is active in `feature/fido2/build.gradle.kts` and resolves `android.bluetooth.BluetoothHidDevice` via the Android SDK stub (or confirm MockK-based test double approach does not require a real Android stub at compile time); verify by ensuring `./gradlew :feature:fido2:compileAndroidHostTestKotlin` completes successfully with exit code 0
- [ ] T002 Confirm `testImplementation("io.mockk:mockk")`, `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")`, and `testImplementation("org.junit.jupiter:junit-jupiter")` are available to the `androidHostTest` source set in `feature/fido2/build.gradle.kts`
- [ ] T003 [P] Confirm `BluetoothHidCallbackCoordinatorTest.kt` exists at `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/bluetooth/BluetoothHidCallbackCoordinatorTest.kt` and compiles successfully against the existing test runner

**Checkpoint**: `androidHostTest` source set resolves all required dependencies. Existing `BluetoothHidCallbackCoordinatorTest` compiles and passes. Safe to add new files.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Define the shared mock contract and captured-pair data structure used by both new test files. Must be complete before US1 or US2 implementation.

- [ ] T004 Define a `CapturedRegistrationPair` data class (or named tuple) in `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/bluetooth/BluetoothHidWarmRegistrationStressTest.kt` containing fields: `executor: ExecutorService`, `callback: BluetoothHidDevice.Callback`, `cycleIndex: Int`, `attemptIndex: Int` — test-scoped only (@Req annotation not required per Constitution Principle V); per FR-001 requirement that every `(executor, callback)` pair from each `hid.registerApp()` call is captured individually
- [ ] T005 Implement the `BluetoothHidDevice` MockK test double slot in `BluetoothHidWarmRegistrationStressTest.kt`: a `MutableList<CapturedRegistrationPair>` that accumulates every `registerApp(any(), any(), any(), any(), any())` (matching `(sdp, inQos, outQos, executor, callback)`) call via an `answers` block, capturing `(arg<Executor>(3) as ExecutorService, arg<BluetoothHidDevice.Callback>(4))` and submitting callbacks through the captured executor (not the calling thread) so that `ExecutorService` worker threads are actually created — per FR-001 measurement integrity requirement

**Checkpoint**: `CapturedRegistrationPair` type is compilable; mock capture list is ready. US1 and US2 tasks can now proceed in parallel.

---

## Phase 3: User Story 1 — Bounded Warm-Session Registration Reliability (Priority: P1) 🏁 MVP

**Goal**: Implement the multi-iteration executor-capture harness (`BluetoothHidWarmRegistrationStressTest`) and record pre-cleanup executor retention evidence for N ≥ 10 warm cycles across M total allocations.

**Independent Test**: Execute N = 10 consecutive `registerApp()` + `unregisterApp()` cycles. After each cycle, record `R_c`; after all cycles, compute fresh `R_final`. Evaluate defect characterization per data-model.md §2 and contract §1.

### Implementation for User Story 1

- [ ] T006 [US1] Implement `warmRegistrationStress_tenCycles_recordsExecutorTerminationState()` test body in `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/bluetooth/BluetoothHidWarmRegistrationStressTest.kt`: initialize `BluetoothHidDeviceWrapper` with the mock `BluetoothHidDevice`; execute N = 10 `registerApp()` calls each followed by `unregisterApp()`; after each cycle c, compute `R_c = count(E_j.isTerminated() == false for j in 1..M_c)` and record it in a `List<Int>`; enforce bounded completion synchronization using `java.util.concurrent.CountDownLatch` with 2000 ms timeout per cycle (avoid `CompletableDeferred` so real worker thread dispatch on `ExecutorService` completes deterministically) before sampling — per FR-001, FR-002, and contract §1 steps 1–6
- [ ] T007 [US1] Add the controlled retry scenario in `BluetoothHidWarmRegistrationStressTest.kt`: configure the mock to return a failure on the first `registerApp()` attempt within one designated cycle so that `BluetoothHidDeviceWrapper` retries internally; assert that the retry attempt's `(executor, callback)` pair is also captured in `M` (i.e. `M > N` after this cycle); per FR-002 requirement that "executors from failed attempts are tracked in M"
- [ ] T008 [US1] Add the fresh pre-cleanup `R_final` observation in `BluetoothHidWarmRegistrationStressTest.kt`: after all N cycles complete and bounded barriers are satisfied, perform a fresh evaluation by querying `E_j.isTerminated()` again across every captured `E_j` (j = 1..M) (not reusing cached cycle snapshots) and compute `R_final = count(E_j.isTerminated() == false)`; record the full observation vector `V_final`; assert `M >= N` and log the complete `[R_1, R_2, ..., R_N]` series; do NOT call `shutdown()` before this observation — per FR-002 and contract §1 steps 7 and measurement integrity note
- [ ] T009 [US1] Add teardown in `@AfterEach` in `BluetoothHidWarmRegistrationStressTest.kt`: invoke `shutdown()` on each captured `E_j`, then `awaitTermination(1000ms)` per executor; if any cleanup times out, log `CLEANUP_TIMEOUT` without altering the recorded `V_final` or `R_final`; teardown MUST NOT interfere with pre-cleanup evidence — per FR-002 and contract §1 steps 8–9
- [ ] T010 [US1] Add assertion logic in `BluetoothHidWarmRegistrationStressTest.kt` that logs the defect characterization verdict:
  - If `R_final > 0`: log "Executor retention observed: R_final={R_final} of M={M} non-terminated"
  - If `R_final == M` and `R_c` is monotonically non-decreasing: log "Linear executor accumulation confirmed under harness: R_final=M"
  - If `R_final == 0`: log "Healthy resource bound observed: R_final=0 (target post-remediation state)"
  - Per FR-002, SC-002 defect characterization rules and data-model.md §2 Executor Lease invariants

**Checkpoint**: `BluetoothHidWarmRegistrationStressTest` compiles and executes. `R_c` series and `R_final` are recorded. Retry scenario verifies `M > N` in at least one test run. US1 independently validates executor retention characterization.

---

## Phase 4: User Story 2 — Observable Evidence of Resource Boundedness (Priority: P2)

**Goal**: Extend `BluetoothHidCallbackCoordinatorTest` with all four callback-scheduling scenarios (Scenario 0 baseline, Scenario 1 Mechanism A, Scenario 2 Mechanism B, Scenario 3 Control) to characterize stale-callback interference with active registration continuations.

**Independent Test**: Run the four new scenario test methods in `BluetoothHidCallbackCoordinatorTest`. Verify: Scenario 0 passes cleanly, Scenario 1 records whether `cont_{k+1}` survives stale-event state transitions, Scenario 2 records whether late stale disrupts active session, Scenario 3 resolves with `Result.failure(...)`.

### Implementation for User Story 2

- [ ] T011 [US2] Add **Scenario 0 (Baseline)** test method `callbackScenario0_nominalSequential_succeedsCleanly()` to `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/bluetooth/BluetoothHidCallbackCoordinatorTest.kt`: call `createRegistrationCallback(cont_k)` → deliver `registered=false` through `E_k` → await bounded barrier for `cont_k` → call `createRegistrationCallback(cont_{k+1})` → deliver `registered=true` through `E_{k+1}` → await `cont_{k+1}`; assert `cont_{k+1}` resolves with `Result.success(Unit)`; per contract §2 Scenario 0 / Invariant 0 and research.md Decision 2
- [ ] T012 [P] [US2] Add **Scenario 1 (Mechanism A)** test method `callbackScenario1_mechanismA_staleEventBeforeCurrent_recordsInterference()` to `BluetoothHidCallbackCoordinatorTest.kt`: call `createRegistrationCallback(cont_k)` and `createRegistrationCallback(cont_{k+1})`; submit `registered=false` through `E_k` to `callback_k`; enforce 500 ms bounded barrier; assert intermediate state: `cont_{k+1}` is still active/pending; then submit `registered=true` through `E_{k+1}` to `callback_{k+1}`; await `cont_{k+1}` resolution; record whether `cont_{k+1}` was prematurely cancelled or completed by the stale event; assert `cont_{k+1}` resolves with `Result.success(Unit)` upon current-event delivery per Invariant 1; per contract §2 Scenario 1, spec FR-003, SC-003. [P note: T012 and T013 are logically independent test methods targeting the same file; serialize edits to prevent conflicts]
- [ ] T013 [P] [US2] Add **Scenario 2 (Mechanism B)** test method `callbackScenario2_mechanismB_lateStaleAfterSuccess_recordsSessionStability()` to `BluetoothHidCallbackCoordinatorTest.kt`: call `createRegistrationCallback(cont_k)` and `createRegistrationCallback(cont_{k+1})`; deliver `registered=true` through `E_{k+1}` to `callback_{k+1}`; await `cont_{k+1}` resolution (`Result.success(Unit)`); then deliver late stale `registered=false` through `E_k` to `callback_k`; enforce bounded barrier; assert that no unexpected state transition (generation bump, Idle publication) destabilizes the newly registered active session; per contract §2 Scenario 2 / Invariant 2, spec FR-003, SC-003. [P note: T012 and T013 are logically independent test methods targeting the same file; serialize edits to prevent conflicts]
- [ ] T014 [US2] Add **Scenario 3 (Control)** test method `callbackScenario3_control_genuineFailure_resolvesWithFailure()` to `BluetoothHidCallbackCoordinatorTest.kt`: call `createRegistrationCallback(cont_{k+1})`; deliver `registered=false` through `E_{k+1}` to `callback_{k+1}` (simulating a genuine framework registration failure); await bounded barrier; assert `cont_{k+1}` resolves with `Result.failure(...)`; per contract §2 Scenario 3 / Invariant 3, spec FR-003, SC-003 — this control confirms the harness can distinguish stale-false from genuine-false events, making Scenario 1 and 2 assertions reliable
- [ ] T015 [US2] Validate executor delivery in all four scenario methods: confirm that each `callback.onAppStatusChanged(...)` invocation in T011–T014 is submitted via `executor.submit { callback.onAppStatusChanged(...) }` (not called directly on the test thread), consistent with FR-001 delivery contract and research.md Decision 2 delivery and synchronization note

**Checkpoint**: All four scenario test methods compile and execute. Scenario 0 passes cleanly. Scenario 3 resolves with `Result.failure(...)`. Scenarios 1 and 2 record behavioral observations of stale-event effects on `cont_{k+1}` and session state.

---

## Phase 5: User Story 3 — Regression Safety for Cold-Start and Single-Registration Paths (Priority: P3)

**Goal**: Confirm that adding new test cases to `BluetoothHidCallbackCoordinatorTest` and the new `BluetoothHidWarmRegistrationStressTest` file does not introduce regressions in existing tests.

**Independent Test**: Run the full existing test suite in `feature/fido2/src/androidHostTest/` (lifecycle tests, existing coordinator tests) and assert zero regressions.

### Implementation for User Story 3

- [ ] T016 [P] [US3] Run the existing `BluetoothHidTransportLifecycleTest` suite in `feature/fido2/src/androidHostTest/` (via `./gradlew :feature:fido2:testAndroidHostTest --tests "com.chimali.fido2.data.transport.BluetoothHidTransportLifecycleTest"`) and confirm all tests pass with zero regressions after T011–T015 are added to `BluetoothHidCallbackCoordinatorTest.kt`
- [ ] T017 [P] [US3] Run all pre-existing test methods in `BluetoothHidCallbackCoordinatorTest.kt` (tests added prior to feature 063, via `./gradlew :feature:fido2:testAndroidHostTest --tests "com.chimali.fido2.bluetooth.BluetoothHidCallbackCoordinatorTest"`) and confirm zero regressions; record pass/fail in investigation evidence per SC-005

**Checkpoint**: Zero regressions in existing test suites. US3 independently validates that investigation-phase test additions are non-breaking.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Produce the required investigation deliverable and ensure all evidence is recorded and traceable.

- [ ] T018 [P] Initialize and populate `specs/063-hid-warm-reg-failure/findings.md`: create the document skeleton upfront with section scaffolding (§1-§4), then populate with observed data after T006–T015 are executed:
  - **§1 Per-Issue Verdicts** (using FR-009 taxonomy): Issue 1 (Executor Leak), Issue 2 (Callback Race Hazard), Issue 4 (Async Unregister Disruption) — each classified as "Confirmed Model Contributor", "Confirmed Model Non-Contributor", or "Inconclusive / Untestable (Rationale Documented)" with reference to specific test evidence
  - **§2 Issue 3 Status**: Document as deferred per FR-007 (HidChannelRegistry reset invariants — follow-on feature)
  - **§3 Host-Model vs Device Attribution Boundary**: Explicit statement separating JVM-modeled mechanism evidence from physical-device incident attribution per FR-009 verdict decision rules and spec Assumptions §3
  - **§4 Backlog Closure Recommendation**: One of: close (if all three modeled contributors are ruled out or confirmed inconclusive), escalate with remediation plan (if any mechanism confirmed), or defer with rationale; reference `docs/backlog/fido2-warm-registration-security-key-failure.md` §5 completion criteria; submitted to FIDO2 Transport Lead / Repository Maintainer per FR-009 decision authority
  - Per SC-006, FR-009
- [ ] T019 Verify coverage contribution: run `./gradlew :feature:fido2:testDebugUnitTest --continue` (or the equivalent androidHostTest task) and confirm that the new test files in `BluetoothHidWarmRegistrationStressTest.kt` and the extended `BluetoothHidCallbackCoordinatorTest.kt` contribute to Tier 1 coverage (`:feature:fido2` ≥ 60% instruction) and Global floor ≥ 50%; record coverage delta in `findings.md` §5 if below gate — per Constitution Principle V and spec Assumption §6

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 completion — BLOCKS US1 and US2 tasks
- **US1 (Phase 3)**: Depends on Phase 2 (T004, T005) — can proceed in parallel with US2
- **US2 (Phase 4)**: Depends on Phase 2 (T004, T005) — can proceed in parallel with US1
- **US3 (Phase 5)**: Depends on US2 completion (T011–T015 added) — regression check
- **Polish (Phase 6)**: Depends on US1 (T006–T010), US2 (T011–T015), and US3 (T016–T017) completion

### User Story Dependencies

```
Phase 1 (Setup)
  └─► Phase 2 (Foundational: T004, T005)
        ├─► Phase 3 / US1 (T006–T010) ─┐
        └─► Phase 4 / US2 (T011–T015) ─┤
                                        └─► Phase 5 / US3 (T016–T017)
                                              └─► Phase 6 (T018, T019)
```

### Within Each User Story

- T004 → T005 (data class before mock; sequential)
- T006 → T007 → T008 → T009 → T010 (executor harness builds incrementally; sequential)
- T011 → T012 [P], T013 [P] → T014 → T015 (Scenario 0 first as baseline; Scenarios 1 & 2 parallel; Control last)
- T016 [P], T017 [P] (regression checks independent of each other)
- T018 [P], T019 (findings doc parallel with coverage run; T018 content requires T006–T017 results)

### Parallel Opportunities

- T012 and T013 (Scenarios 1 and 2 in different test methods, same file — serialize writes but assertions are independent)
- T016 and T017 (different test suites — fully parallel)
- T018 and T019 (different outputs — fully parallel after US1/US2/US3 complete)
- US1 (Phase 3) and US2 (Phase 4) after Phase 2 completes (different files)

---

## Parallel Example: US1 + US2 After Foundational

```bash
# After T004 + T005 complete:

# Stream A — US1 (BluetoothHidWarmRegistrationStressTest.kt):
Task T006: "Implement warmRegistrationStress_tenCycles_recordsExecutorTerminationState()"
Task T007: "Add controlled retry scenario (M > N verification)"
Task T008: "Add fresh R_final pre-cleanup observation and V_final vector"
Task T009: "Add @AfterEach teardown with bounded awaitTermination"
Task T010: "Add defect characterization verdict logging"

# Stream B — US2 (BluetoothHidCallbackCoordinatorTest.kt):
Task T011: "Add Scenario 0 baseline test"
Task T012: "Add Scenario 1 Mechanism A test"  [parallel with T013]
Task T013: "Add Scenario 2 Mechanism B test"  [parallel with T012]
Task T014: "Add Scenario 3 Control test"
Task T015: "Validate executor delivery in all four scenarios"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T003)
2. Complete Phase 2: Foundational — T004, T005 (**CRITICAL** — blocks US1 and US2)
3. Complete Phase 3: User Story 1 — T006–T010
4. **STOP and VALIDATE**: Execute `BluetoothHidWarmRegistrationStressTest` and record `R_c` series + `R_final`
5. If `R_final > 0`, executor leak hypothesis has JVM-modeled evidence — proceed to US2 and findings

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add US1 (executor harness) → Record executor retention evidence (MVP!)
3. Add US2 (callback scenarios) → Record callback interference evidence
4. Add US3 (regression validation) → Confirm non-breaking additions
5. Add Polish (findings.md + coverage check) → Produce backlog closure deliverable

### Parallel Team Strategy

With two developers:

1. Both complete Setup + Foundational together (T001–T005)
2. Once Foundational is done:
   - Developer A: US1 (T006–T010) — stress harness in `BluetoothHidWarmRegistrationStressTest.kt`
   - Developer B: US2 (T011–T015) — callback scenarios in `BluetoothHidCallbackCoordinatorTest.kt`
3. US3 regression check after US2 completes (T016–T017)
4. Polish jointly (T018, T019) once all stories pass

---

## Notes

- `[P]` tasks = independent files or independent test methods that can be written concurrently
- `[Story]` label maps each task to its user story for traceability (US1 = SC-001/SC-002, US2 = SC-003/SC-004, US3 = SC-005)
- **FR-007 and FR-008 are DEFERRED** — zero tasks generated for channel registry or core/bluetooth audit
- **No production code changes** — all tasks confined to `androidHostTest/` source set
- Executor callbacks MUST be submitted through the captured `ExecutorService`, not invoked on the calling thread (FR-001 measurement integrity)
- Pre-cleanup observations MUST precede `@AfterEach` teardown shutdown calls (FR-002 integrity)
- `findings.md` (T018) is a required deliverable — the backlog item cannot be closed without it (FR-009, SC-006)
- All bounded synchronization barriers use timeout ≥ 2000 ms per cycle (contract §1 step 5); 500 ms inter-event barrier for Scenario 1 (contract §2 Scenario 1 step 4)
- Commit after each logical group using `git -C "D:\octav\source\repos\.worktrees\chimali\063-hid-warm-reg-failure"`

# Investigation Checklist: Bluetooth HID Warm Registration Failure

**Purpose**: Pre-plan quality gate — validate that spec requirements are precise and complete enough to derive `tasks.md` without interpretation. Focus: executor-leak and callback-race hypotheses (FR-001–FR-004).
**Created**: 2026-11-02
**Feature**: [spec.md](../spec.md)

**Review Ownership**: This checklist is a reviewer-owned requirements-quality review artifact. Mark an item `[x]` only when the reviewer determines the requirements-quality criterion is satisfied.
**Marker Semantics**: `[x]` means the criterion has been reviewed and the requirement is written with sufficient quality. It does NOT mean implementation work is complete or tests pass.

---

## Requirement Completeness

- [x] CHK001 - Are the two primary hypotheses (executor leak and callback race) each represented as a separate, independently testable functional requirement — such that a task can be derived for each without bundling them? [Completeness, Spec §FR-001, FR-003]
- [x] CHK002 - Is there a requirement specifying how the executor-observation baseline is captured — i.e., sampling each captured executor's pre-cleanup termination state after bounded completion synchronization? [Completeness, Spec §FR-002, Lifecycle Contract §1]
- [x] CHK003 - Does the spec define what constitutes the boundary of one "warm registration cycle" for counting purposes — specifically whether the retry loop within `registerApp()` counts as one cycle or multiple? [Completeness, Spec §FR-001]
- [x] CHK004 - Are requirements defined for both expected outcomes of the executor-leak test: (a) defect confirmed — leak detected, and (b) defect not confirmed — count stable? [Completeness, Spec §FR-002, SC-004]

---

## Requirement Clarity — Test Harness Constraints *(mandatory gate: heaviest weighting)*

- [x] CHK005 - Does FR-001 identify the exact class or interface that represents `BluetoothHidDevice` in the host-JVM test boundary, rather than describing the mock boundary by effect only? [Clarity, Spec §FR-001]
- [x] CHK006 - Does FR-001 name the specific production code path that must run unmodified — i.e., does it reference `Executors.newSingleThreadExecutor()` inside `BluetoothHidDeviceWrapper.registerApp()` by name rather than by intent? [Clarity, Spec §FR-001]
- [x] CHK007 - Is "unmodified production code path" in FR-001 unambiguous enough to exclude subclasses, wrapper adapters, or injected executor factories that would substitute a different executor? [Clarity, Ambiguity, Spec §FR-001]
- [x] CHK008 - Is the mechanism for observing live thread count specified in the spec or left as an implementation detail — e.g., is `Thread.getAllStackTraces().size`, named-thread prefix filtering, or a JMX query required, or is any counting method acceptable? [Clarity, Spec §SC-002]
- [x] CHK009 - Does the callback-race requirement (FR-003) specify the exact invocation ordering that constitutes the race scenario — which callback event fires first, and on which thread or executor? [Clarity, Spec §FR-003]
- [x] CHK010 - Does FR-003 distinguish between two distinct `registered=false` events: one originating from the prior `unregisterApp()` call and one that would be the failure outcome of the new `registerApp()` — are they named or described separately? [Clarity, Ambiguity, Spec §FR-003]
- [x] CHK011 - Is the phrase "stale unregister callback" (or equivalent) defined in Key Entities or Clarifications such that a task author can unambiguously identify which callback instance it refers to? [Clarity, Spec §Key Entities]

---

## Acceptance Criteria Quality

- [x] CHK012 - Is SC-001's "10 or more consecutive warm registration cycles" unambiguous — does it count `registerApp()` invocations, completed cycle pairs, or something else? [Measurability, Spec §SC-001]
- [x] CHK013 - Is SC-002's "zero net growth regardless of retry count" verifiable with a single post-run assertion, or does it implicitly require mid-run sampling at each iteration? [Measurability, Spec §SC-002]
- [x] CHK014 - Does SC-003's criterion that "registered=false cannot fail a subsequent registration" define the specific observable outcome that constitutes failure — e.g., continuation resumed with `Result.failure(...)`, an exception thrown, or incorrect state transition? [Measurability, Spec §SC-003]
- [x] CHK015 - Is SC-004's per-issue verdict requirement time-bounded — must all four issue verdicts exist before `tasks.md` is written, or only at investigation close? [Clarity, Spec §SC-004]

---

## Scenario Coverage

- [x] CHK016 - Are requirements defined for the scenario where a new `registerApp()` is initiated while a prior continuation is still suspended — not yet resumed by any callback? [Coverage, Spec §FR-003]
- [x] CHK017 - Is the within-cycle retry scenario explicitly covered — does any requirement describe the expected thread-count behavior when `registerApp()` fails internally and is retried before the cycle completes? [Coverage, Edge Case, Spec §FR-001]
- [x] CHK018 - Does the spec cover the "race not triggered" baseline scenario — a test that confirms nominal sequential delivery (unregister then register, in order) also behaves correctly, providing a clean reference? [Coverage, Spec §FR-003]

---

## Edge Case Coverage

- [x] CHK019 - Is it specified how executor lifecycle observation isolates the registration path from unrelated framework threads (JUnit, MockK, coroutine dispatcher) — e.g., by directly capturing and inspecting the production `ExecutorService` reference? [Edge Case, Clarity, Spec §FR-001, Lifecycle Contract §1]
- [x] CHK020 - Is the maximum retry count (5 retries × N cycles = up to 5N thread-creation opportunities) referenced in the spec requirements as a stated boundary condition for SC-002, or only mentioned in Assumptions? [Edge Case, Spec §SC-002, Assumptions]
- [x] CHK021 - Does the spec define behaviour if the mock `BluetoothHidDevice` callback is never delivered — is there a required timeout, test failure mode, or explicit "no-callback = failure" rule? [Edge Case, Gap]

---

## Dependencies & Assumptions

- [x] CHK022 - Is the assumption that host-side tests are accepted as primary evidence traceable to a named authority (backlog §5, constitution principle, or owner decision) rather than stated as an uncited default? [Assumption, Spec §Assumptions]
- [x] CHK023 - Does FR-006's no-production-change constraint define with sufficient precision whether adding a new test file to `androidHostTest/` constitutes a "production change" — so a task author knows what is and is not permitted? [Clarity, Spec §FR-006]
- [x] CHK024 - Does the spec explicitly indicate that FR-007 (channel registry reset) and FR-008 (core/bluetooth DI audit) are deferred and will NOT generate tasks in the current `tasks.md`? [Dependency, Scope, Gap]

---

## Findings & Evidence Deliverable

- [x] CHK025 - Does FR-009 specify the exact file path and filename of `findings.md` — concretely enough that a task author can write "create `specs/063-hid-warm-reg-failure/findings.md`" without interpretation? [Completeness, Spec §FR-009]
- [x] CHK026 - Is the three-verdict taxonomy in FR-009 ("confirmed contributor / confirmed non-contributor / untestable — rationale documented") defined precisely enough that two independent reviewers would reach the same classification from the same test evidence? [Clarity, Spec §FR-009]
- [x] CHK027 - Does SC-006's closure recommendation specify who is authorised to make the close/escalate/defer decision, or is the decision-maker undefined — leaving the backlog §5 completion criterion unverifiable? [Completeness, Gap, Spec §SC-006]

---

## Notes

- Items CHK005–CHK011 carry the highest gate weight (FR-001 mock boundary clarity). These should be resolved before generating `tasks.md`.
- FR-007 (channel registry) and FR-008 (core/bluetooth DI audit) are explicitly out of this checklist's scope per user direction.
- Mark items `[x]` only after the reviewer confirms the requirement is written with sufficient quality to derive an unambiguous implementation task.
- Leave items unchecked when the requirement is missing, ambiguous, or internally inconsistent.
- `/speckit-implement` reads checklist checkbox state as a gate and must not modify markers.
- `checklists/requirements.md` has a separate built-in lifecycle maintained by `/speckit-specify` and `/speckit-clarify`.

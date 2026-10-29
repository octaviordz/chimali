# Measurement Validity Checklist: FIDO2 Simulated Latency

**Purpose**: Review requirement completeness, clarity, consistency, and measurability for the simulated internal-operation latency assessment.
**Reviewed**: 2026-10-29
**Feature**: [spec.md](../spec.md) | [plan.md](../plan.md) | [measurement contract](../contracts/measurement-contract.md) | [data model](../data-model.md)

**Review ownership**: Reviewer-owned requirements-quality artifact. `[x]` means the criterion is satisfied by the requirements; it does not mean implementation or performance acceptance is complete.

## Requirement completeness

- [x] CHK001 Do registration and authentication share an explicit full-ceremony boundary from fake-RP request issuance through independent response verification? [Completeness, Spec §FR-002–FR-003, Contract §Workload and boundaries]
- [x] CHK002 Are user-verification outcomes, no-deliberate-delay behavior, intrinsic test-double duration, and overlap treatment specified? [Completeness, Spec §FR-004–FR-005, Contract §Workload and boundaries]
- [x] CHK003 Are raw timing, operation identity/type, outcomes, workload, environment, revision, failures, and outliers required in the report? [Completeness, Spec §FR-006–FR-007, Data Model §Ceremony Attempt and §Measurement Report]
- [x] CHK004 Does the scope distinguish a test-double verification result from any visible prompt, biometric-success frame, or physical user interaction? [Completeness, Spec §FR-004, Contract §Workload and boundaries]
- [x] CHK005 Are complete, unambiguous associations between a simulated ceremony and every generated internal operation specified? [Completeness, Spec §FR-004, Contract §Operation association and timing validity]
- [x] CHK006 Are historical Feature 055 observations kept distinct from new simulated measurements and compliance results? [Completeness, Spec §FR-013, Contract §Evidence and target scope]

## Requirement clarity

- [x] CHK007 Are the fake-RP round trip, internal-operation intervals, and test-double interval defined as distinct measurements with explicit endpoints? [Clarity, Spec §FR-003–FR-006, Contract §Workload and boundaries, Data Model §Timing Assessment]
- [x] CHK008 Is fake-RP and substituted-transport work identified as round-trip overhead rather than app-internal processing? [Clarity, Spec §FR-003, Contract §Workload and boundaries]
- [x] CHK009 Is the verification double required to add no deliberate delay while its intrinsic execution time remains measured and reported? [Clarity, Spec §FR-004, Contract §Workload and boundaries]
- [x] CHK010 Are all elapsed intervals tied to a real monotonic clock and invalid when their boundaries are incomplete, out of order, or incompatible? [Clarity, Spec §FR-006, Contract §Operation association and timing validity]
- [x] CHK011 Are attempt outcomes clearly distinguished from the final disposition of an assessment report when evidence is incomplete? [Clarity, Spec §FR-002, §FR-008–FR-010, Data Model §Ceremony Attempt and §Measurement Report]
- [x] CHK012 Are warmups, repetitions, failures, and outliers retained and handled under a workload policy declared before measurement? [Clarity, Spec §FR-007–FR-008, Contract §Operation association and timing validity]

## Measurement and acceptance quality

- [x] CHK013 Are real and simulated boundaries, host/runtime limitations, and uncertainty/limitations disclosure required for interpretation? [Measurability, Spec §FR-001, §FR-007, Contract §Evidence and target scope]
- [x] CHK014 Is the calculation for subtracting only the measured overlap of verification-test-double execution and Bluetooth response handoff from an operation interval fully specified, including malformed or partial intervals? [Measurability, Spec §FR-005, §FR-009, Contract §Workload and boundaries, Data Model §Timing Assessment]
- [x] CHK015 Is the strict <200 ms decision defined for each operation occurrence and for repeated occurrences of an operation type, including equality at 200 ms? [Acceptance criteria, Spec §FR-009, Contract §Evidence and target scope]
- [x] CHK016 Is the threshold applied to unrounded duration with an explicit unit/precision rule, so display rounding cannot alter a decision? [Measurability, Spec §FR-006, §FR-009, Data Model §Timing Assessment]
- [x] CHK017 Are operation-level and report-level pass/fail/incomplete precedence rules defined when an operation fails, an attempt is unsuccessful, or evidence is missing? [Acceptance criteria, Spec §FR-008–FR-010, Data Model §Measurement Report]
- [x] CHK018 Must the workload cover registration and authentication and either measure each implemented operation type or disclose it as unmeasured and the project assessment as incomplete? [Consistency, Spec §FR-001, §FR-008, Contract §Assessment calculation and disposition]
- [x] CHK019 Are claims limited to the measured host/runtime and simulated scope, with no population-wide or physical-device compliance inference? [Acceptance criteria, Spec §FR-011–FR-013, Contract §Evidence and target scope]

## Scenario and edge-case coverage

- [x] CHK020 Are denied, cancelled, failed, timed-out, invalid, and unverified ceremonies retained and prevented from counting as verified samples or passing operations? [Coverage, Spec §FR-002, §FR-004, §FR-008, §FR-010]
- [x] CHK021 Are duplicate/reused identities, concurrent requests, probes, unmatched operations, and incomplete association addressed? [Coverage, Spec §Edge Cases, §FR-004, Contract §Operation association and timing validity]
- [x] CHK022 Do the requirements define how runtime suspend/resume or a monotonic-clock discontinuity affects an in-progress interval? [Coverage, Spec §Edge Cases, §FR-006, Contract §Operation association and timing validity]
- [x] CHK023 Are interrupted or incomplete records retained and barred from an unsupported passing disposition? [Recovery, Spec §FR-007, §FR-010, Data Model §Ceremony Attempt]
- [x] CHK024 Does an unavailable host path or unexercisable operation type result in explicit unmeasured/incomplete reporting rather than an inferred pass? [Coverage, Spec §FR-001, §FR-008, Contract §Operation association and timing validity]

## Consistency, dependencies, and evidence

- [x] CHK025 Do the artifacts consistently replace the physical <1,000 ms criterion with the simulated per-operation <200 ms assessment, while keeping physical results diagnostic? [Consistency, Spec §FR-011–FR-012, Plan §Constitution Check, Contract §Evidence and target scope]
- [x] CHK026 Are user-experience observations, simulated numeric assessment, and physical-device limitations kept distinct? [Consistency, Spec §User Story 3, §FR-010–FR-013]
- [x] CHK027 Are the synthetic workload, real monotonic timing, actual host/runtime, production components, and substitutions identified as prerequisites or report fields? [Dependency, Spec §FR-001, §FR-006–FR-008, Contract §Workload and boundaries]
- [x] CHK028 Are privacy, reproducibility, source traceability, raw evidence retention, and TRD §9.1 record fields specified? [Completeness, Spec §FR-007, §FR-010, Contract §Evidence and target scope, Data Model §Measurement Report]
- [x] CHK029 Are T084 supersession, T085 deferral, and SC-007's pending status tied to actual simulated assessment results rather than historical device evidence? [Consistency, Spec §FR-011–FR-013, Plan §Phase 1 — Design and Implementation Sequence, TRD §9.6 Governance and Trustworthy Result Handling]
- [x] CHK030 Can each verification-double interval and unsuccessful operation be attributed to its own ceremony/action so its time cannot be silently assigned to a later verified attempt? [Ambiguity, Spec §FR-004–FR-005, Contract §Operation association and timing validity]

## Findings

- **CHK011, CHK014–CHK017, CHK022 — Reviewed**: The assessment rules are recorded in [Contract §Assessment calculation and disposition](../contracts/measurement-contract.md#assessment-calculation-and-disposition) and the data model. The implementation must still declare its concrete workload, interval endpoints, clock behavior and warmup eligibility before measurement.

Checklist markers assess requirement quality only; none claims that the harness has run or that latency has passed.

# Feature Specification: Coverage Tool Evaluation

**Feature Branch**: `059-coverage-tool-evaluation`  
**Created**: 2026-10-28  
**Status**: Draft  
**Input**: Assessment decision `jacoco-evaluation`: a bounded, workload-first comparison of coverage tools.

## Clarifications

### Session 2026-10-28

- Q: How broad should the comparison of alternative coverage tools be? → A: Compare JaCoCo and Kover plus at most one other credible candidate found through a bounded search; no more than three candidates total.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Assess workload eligibility (Priority: P1)

The owner needs to know which options support the configured host and device workloads before considering a change.

**Why this priority**: A candidate missing required workloads cannot be assumed to replace the current tool.

**Independent Test**: Compare candidate dispositions and cited evidence against the configured workload inventory.

**Acceptance Scenarios**:

1. **Given** configured sources, variants and workloads, **When** a candidate is assessed, **Then** each material capability is classified supported, unsupported or unverified with version-specific evidence and limitations.
2. **Given** an option covers only host workloads or uses the current engine as a front end, **When** it is presented, **Then** its limited scope and engine relationship are explicit.

### User Story 2 - Compare evidence and trade-offs (Priority: P2)

The owner needs a fair account of fidelity, continuity and costs to judge the practical consequences of each option.

**Why this priority**: Similar reports can represent different measured code and counters.

**Independent Test**: Trace comparison claims to documentation or reproducible representative evidence and inspect recorded unknowns.

**Acceptance Scenarios**:

1. **Given** a material question unresolved by authoritative evidence, **When** representative measurement is necessary and feasible, **Then** its scope, conditions, raw counts and reproducible evidence are recorded.
2. **Given** incomparable inventories or unavailable resources, **When** results are reviewed, **Then** limitations are explicit and no unsupported equivalence or savings are claimed.

### User Story 3 - Make a defensible recommendation (Priority: P3)

The owner needs a traceable recommendation to retain, replace or defer the choice.

**Why this priority**: The comparison must support a decision without predetermining migration.

**Independent Test**: Review the recommendation, evidence, unresolved questions and follow-up.

**Acceptance Scenarios**:

1. **Given** the comparison, **When** the recommendation is recorded, **Then** it states retain, replace or defer, explains trade-offs and limitations, and appears in feature 058 research.
2. **Given** migration is proposed, **When** the evaluation concludes, **Then** migration requires separate approval and any approved follow-up is tracked separately.

### Edge Cases

- Authoritative documentation can establish an unsupported capability without an experiment; unknown capabilities remain unverified.
- If necessary device access or measurement resources are unavailable, document the impact and defer conclusions requiring that evidence.
- Compatible report formats do not establish equivalent counters, inventories, merged data or baselines.
- Historical local acceptance does not establish comparative superiority or present device availability; waived hosted acceptance remains unverified.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The evaluation MUST inventory configured production sources, host and device test workloads and relevant variants, and compare JaCoCo and Kover plus at most one other credible candidate identified through a bounded search (no more than three candidates total).
- **FR-002**: The comparison MUST distinguish distinct engines from front ends using an existing engine, and classify options by complete-workload eligibility or limited scope.
- **FR-003**: Each candidate MUST receive supported, unsupported or unverified dispositions with traceable version-specific evidence for workload and toolchain compatibility and relevant variant and shrinking support.
- **FR-004**: The comparison MUST examine instruction and branch meanings, Kotlin filtering, denominators, inventory and report fidelity, merging and class/probe compatibility, and baseline and regression continuity; unknowns and limitations MUST be identified.
- **FR-005**: The evaluation MUST use authoritative evidence where sufficient and perform small reproducible representative measurements only where needed and feasible to answer material unresolved questions; record why measurements were or were not performed.
- **FR-006**: Numerical claims MUST identify measured scope, conditions, raw counters and evidence; unlike inventories or counters MUST NOT be presented as equivalent without evidence.
- **FR-007**: The evaluation MUST distinguish measured, documented and unknown operational, runtime, maintenance and transition costs, including the hosted-worker constraint.
- **FR-008**: The evaluation MUST document available historical rationale and missing contemporaneous evidence for the original selection.
- **FR-009**: The outcome MUST recommend retain, replace or defer with reasons, limitations, unresolved questions and separate follow-up, and record the comparison and recommendation in feature 058 research.
- **FR-010**: The evaluation MUST preserve the active engine, thresholds, workloads, generated-code and enforcement policies; it MUST NOT reactivate the runner, change hosted controls, or claim waived hosted acceptance.
- **FR-011**: Production migration, custom hybrid collection, verifier redesign and native iOS coverage are outside this evaluation; migration requires separate approval.

### Key Entities

- **Candidate**: A versioned option, its engine relationship and assessed workload scope.
- **Evidence**: A source or reproducible measurement supporting a capability, numerical claim or cost, including limitations.
- **Recommendation**: A retain, replace or defer outcome linked to evidence and unresolved issues.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All identified candidates have explicit dispositions and evidence or an unverified status for every material comparison dimension.
- **SC-002**: Every numerical comparison identifies scope and raw counts and links to reproducible evidence; unsupported equivalence claims are absent.
- **SC-003**: One recommendation is recorded in feature 058 research with reasons, limitations and outstanding questions, and any separately approved migration follow-up is tracked.
- **SC-004**: A reviewer can trace every material conclusion to evidence or an explicit unknown, including costs and device and hosted limitations.

## Assumptions

- The owner is the decision maker; affected roles and recurring costs remain to be established during evaluation.
- Existing configuration and historical records are available for inspection. Present device access and a measurement budget are not assumed.
- Documentation may resolve a capability without execution. An evidence-backed defer outcome is acceptable when essential evidence cannot be obtained.

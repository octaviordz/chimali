---
description: "Tasks for a bounded coverage tool evaluation"
---

# Tasks: Coverage Tool Evaluation

**Input**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [quickstart.md](quickstart.md)

**Organization**: Documentation and evidence tasks grouped by user story. No production integration is authorized. Run measurements only under the conditions in T014–T015.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Independent tasks using separate output files and no unfinished dependencies.
- **[Story]**: User story owning the task.
- All output paths are repository-relative.

## Phase 1: Setup

**Purpose**: Establish the evidence workspace and evaluation boundaries.

- [ ] T001 Create `specs/059-coverage-tool-evaluation/evaluation/README.md` identifying the source revision, scope, fixed policy boundaries, artifact index and evidence status conventions from `specs/059-coverage-tool-evaluation/plan.md`.
- [ ] T002 Create `specs/059-coverage-tool-evaluation/evaluation/sources.md` as a source register with version, retrieval date, location or receipt, claim and limitations; reference existing `.specify/assessments/jacoco-evaluation/source-receipts.json` without claiming a new retrieval.

## Phase 2: Foundational

**Purpose**: Establish the actual configured workload and historical baseline before comparing options.

- [ ] T003 Record revision, current Gradle/AGP/Kotlin/Java and JaCoCo versions, source sets, modules, exclusions, variants, required and optional host/device tasks and device requirements from `tools/coverage/policy.json`, build files and `gradle/libs.versions.toml` in `specs/059-coverage-tool-evaluation/evaluation/inventory.md`; identify changes from the assessment.
- [ ] T004 [P] Record the scope and limits of historical local acceptance, hosted waiver and disabled runner using `specs/058-enforce-coverage-targets/implementation-evidence.md` and `specs/058-enforce-coverage-targets/tasks.md` in `specs/059-coverage-tool-evaluation/evaluation/history.md`; do not imply present device availability or hosted acceptance.
- [ ] T005 [P] Investigate the earliest supported JaCoCo introduction and contemporaneous selection rationale using Git history and available records; record search scope, citations and unresolved gaps in `specs/059-coverage-tool-evaluation/evaluation/origin.md`.

**Checkpoint**: Inventory and evidence boundaries are available for the story work.

## Phase 3: User Story 1 - Assess workload eligibility (Priority: P1) 🎯 MVP

**Goal**: Identify which options cover the actual required workloads and which remain limited or unverified.

**Independent Test**: Compare the candidate matrix against the current inventory and verify each disposition has versioned supporting evidence or an explicit unverified status.

- [ ] T006 [P] [US1] Research current JaCoCo's versioned workload, variant and toolchain support using primary sources and repository evidence; document scope, engine identity, evidence and limitations in `specs/059-coverage-tool-evaluation/evaluation/jacoco.md`.
- [ ] T007 [P] [US1] Research Kover's versioned host/device, variant and exact toolchain support and distinguish embedded and JaCoCo backend configurations in `specs/059-coverage-tool-evaluation/evaluation/kover.md`; revalidate the documented device-test limitation and leave unsupported claims unverified.
- [ ] T008 [P] [US1] Search authoritative documentation and project repositories for maintained JVM or Android coverage engines and integrations; consider a third candidate credible when its documentation describes a relevant coverage capability and it is distinct from JaCoCo and Kover. Select at most one, recording sources searched, selection or no-selection rationale, versioned sources and engine relationship in `specs/059-coverage-tool-evaluation/evaluation/third-candidate.md`; keep the total including JaCoCo and Kover at three or fewer.
- [ ] T009 [US1] Build `specs/059-coverage-tool-evaluation/evaluation/eligibility.md` from T003 and T006–T008, with each candidate/dimension classified supported, unsupported or unverified, scoped to workload and variant, linked to evidence and classified as complete-workload, limited or unverified; do not count Kover backend configurations as separate candidates.
- [ ] T010 [US1] Review `specs/059-coverage-tool-evaluation/evaluation/eligibility.md` against `specs/059-coverage-tool-evaluation/spec.md` FR-001–FR-003 and record missing evidence explicitly before proceeding.

## Phase 4: User Story 2 - Compare evidence and trade-offs (Priority: P2)

**Goal**: Explain fidelity, continuity and costs using defensible evidence.

**Independent Test**: Trace each comparison claim to sources or reproducible measurements; inspect all limits and unknowns.

- [ ] T011 [P] [US2] Compare instruction and branch meanings, Kotlin filtering, denominators, variants, shrinking, inventory and report fidelity for each selected candidate in `specs/059-coverage-tool-evaluation/evaluation/metrics.md`; link sources and mark unknowns.
- [ ] T012 [P] [US2] Compare execution-data merging, class/probe compatibility, mismatch handling, existing baseline and regression continuity and verifier implications for each selected candidate in `specs/059-coverage-tool-evaluation/evaluation/continuity.md`; distinguish format compatibility from equivalence.
- [ ] T013 [P] [US2] Record operational, runtime, maintenance and transition costs and concrete developer pain where evidence exists in `specs/059-coverage-tool-evaluation/evaluation/costs.md`, labeling each measured, documented, historical or unknown and noting the hosted-worker constraint.
- [ ] T014 [US2] Identify material unresolved questions in `specs/059-coverage-tool-evaluation/evaluation/measurement-decision.md`; for each, document whether a small measurement is necessary and feasible, the resources available, and why it is run or omitted; do not create a custom hybrid collector.
- [ ] T015 [US2] If T014 identifies a necessary and feasible measurement, execute the bounded comparison and record commands, revision, versions, device/runtime, tests, variants, inventory, raw covered/missed counts, artifacts and limitations in `specs/059-coverage-tool-evaluation/evaluation/measurements.md`; otherwise record the omission and its effect there. Do not combine incompatible data or represent host-only results as full-workload evidence.
- [ ] T016 [US2] Consolidate candidate-by-dimension evidence, unknowns and numerical comparability in `specs/059-coverage-tool-evaluation/evaluation/comparison.md`, linking T009 and T011–T015 and ensuring every material dimension has a disposition.

## Phase 5: User Story 3 - Make a defensible recommendation (Priority: P3)

**Goal**: Provide a traceable retain, replace or defer recommendation.

**Independent Test**: Review the recommendation, supporting evidence, unresolved issues and any separately approved follow-up.

- [ ] T017 [US3] Write a retain, replace or defer recommendation with trade-offs, evidence links, uncertainty and consequences of missing data in `specs/059-coverage-tool-evaluation/evaluation/recommendation.md` using T009 and T016; do not assume migration approval.
- [ ] T018 [US3] Record the comparison, recommendation and limitations in `specs/058-enforce-coverage-targets/research.md`, preserving existing historical decisions and identifying hosted acceptance as waived/unverified.
- [ ] T019 [US3] Assess the completion criteria in `docs/backlog/jacoco-coverage-tool-evaluation.md` and record the disposition in `specs/059-coverage-tool-evaluation/evaluation/recommendation.md`; if migration follow-up is separately approved, capture it as a separate `docs/backlog/` item, otherwise explicitly record that approval and follow-up are outstanding or unnecessary as appropriate.

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T020 Verify all candidate claims, source references, version scopes and numerical evidence against `specs/059-coverage-tool-evaluation/quickstart.md` and FR-001–FR-009; record results and unresolved limitations in `specs/059-coverage-tool-evaluation/evaluation/validation.md`.
- [ ] T021 Review the change set for changes to engine, thresholds, workloads, generated-code policy, enforcement and hosted configuration; separately check runner state using an available authoritative source, or record it as unverified if it cannot be observed. Record the evidence and resolve any contrary findings in `specs/059-coverage-tool-evaluation/evaluation/validation.md` before completion.
- [ ] T022 Update `specs/059-coverage-tool-evaluation/evaluation/README.md` with the final artifact index, evidence status and links to the completed recommendation and validation.

## Dependencies & Execution Order

- Setup precedes foundation. T003–T005 follow T001; T003, T004 and T005 can proceed independently after setup.
- US1 depends on foundation. T006–T008 are independent; T009 follows all three and T003, then T010 reviews the matrix.
- US2 uses the inventory and selected candidates; T011–T013 can proceed independently after T009. T014 follows these findings, T015 follows T014, and T016 consolidates them.
- US3 requires US1 and US2 evidence: T017 → T018 and T019. T019 does not itself authorize migration.
- Final validation follows all story outputs; T022 follows validation.

## Parallel Examples

- **US1**: T006, T007 and T008 may be investigated in parallel in separate files; merge evidence into T009 afterward.
- **US2**: T011, T012 and T013 may be researched in parallel after candidate selection, then inform T014.
- **US3**: Source and cost evidence can be reviewed in parallel before T017, but T017–T019 are sequential to keep the recommendation and final record consistent.

## Implementation Strategy

Complete setup and foundation, then deliver and independently review US1 as the MVP. Add US2's fidelity and cost comparison, including a conditional experiment decision, before producing US3's recommendation. Finish with cross-cutting validation. A documented defer outcome remains valid if essential evidence is unavailable; no unperformed activity is recorded as passing.

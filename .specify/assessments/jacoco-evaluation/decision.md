# Decision: Coverage Tool Evaluation

- **Slug**: jacoco-evaluation
- **Decided**: 2026-10-28
- **Reassessed**: 2026-10-28, America/Mexico_City
- **Verdict**: go
- **Artifacts reviewed**: [intake.md](intake.md), refreshed [research.md](research.md), revised [problem.md](problem.md), revised [concept.md](concept.md)
- **Decision scope**: Proceed to specifying the evaluation; no coverage engine is selected and the backlog is not complete.

## Scorecard

| Criterion | Rating | Justification |
| --- | --- | --- |
| Problem validity | adequate | The owner requested a comparative rationale that the existing retention decision does not supply; the documented problem is a decision-evidence gap, not a proven engine defect. [intake.md; problem.md, Problem Statement] |
| Evidence strength | adequate | Repository configuration, recorded local acceptance and versioned primary documentation establish the operating constraints and a concrete Kover 0.9.11 device-collection limitation. They justify a bounded evaluation, not a replacement decision. [research.md, Prior Art and Version and compatibility evidence] |
| Value vs. inaction | adequate | The evaluation directly answers the owner's request and has explicit evidence-quality outcomes; comparative savings and the cost of inaction remain unquantified. [problem.md, Success Metrics and Cost of Inaction] |
| Feasibility / appetite | adequate | Option B can use authoritative evidence to resolve eligibility before committing to measurements and can report unresolved material questions honestly. Experiment cost, current device availability and the proposed medium appetite remain unvalidated. [concept.md, Option B and Assumptions to Validate] |
| Strategic fit | adequate | The evaluation supports the stated coverage-evidence goals while retaining current workloads, policy and operational constraints; this assessment is not a new constitution-compliance audit. [problem.md, Goals and Non-Goals] |
| Risk posture | adequate | The concept excludes migration, custom hybrid collection, verifier redesign and hosted changes, and requires interpretation of counter/baseline differences. Evidence availability and benefit uncertainty remain. [concept.md, Out of Scope and Assumptions to Validate] |

## Verdict & Rationale

**Go** to specifying **Option B — Bounded evaluation with workload eligibility**. The refreshed evidence strengthens the basis for a focused comparison: the existing JaCoCo pipeline has documented local acceptance, and Kover 0.9.11's Gradle plugin explicitly lacks required Android device-test collection. This narrows the comparison without proving that JaCoCo is universally superior or that no alternative exists. ([research.md](research.md))

The scorecard is adequate for specifying an investigation whose purpose is to resolve these unknowns. It is not adequate for choosing a replacement or claiming cost savings. Documentation may establish a candidate's missing capability without a new experiment; any measurement should answer a material question rather than build a new integration to rescue an ineligible option. A host-only result or compatible XML must not be presented as evidence of complete-workload or baseline equivalence. ([problem.md](problem.md), Success Metrics; [concept.md](concept.md), Recommendation)

The proposed medium appetite is not an owner-approved budget or delivery estimate. Current device access, actual comparative costs and exact candidate toolchain compatibility remain unknown. They are carried forward rather than assumed. Retain or defer can be legitimate outcomes of the completed comparison; this gate does not predetermine the engine recommendation or satisfy the backlog's completion criteria.

## Handoff to /speckit-specify

- **Problem**: The owner lacks a complete comparative basis for judging coverage-tool suitability and trade-offs for Chimali's configured Android/JVM host and device workloads.
- **Chosen approach**: A bounded evaluation that distinguishes complete-workload candidates, limited host-side options and front-end changes over the same engine. Establish supported, unsupported or unverified capabilities before deciding whether representative measurement is necessary.
- **In scope**: Configured source sets and workload/variant support, exact toolchain compatibility, instruction and branch meanings, data and class/probe compatibility, inventory/report fidelity, baseline continuity, maintenance and transition costs, documented limits, and a reasoned retain/replace/defer recommendation.
- **Out of scope**: Changing the active engine, thresholds, workloads, generated-code or enforcement policy; reactivating the runner or changing hosted controls; native iOS coverage; a production-ready replacement, custom hybrid collection or verifier redesign. Any migration remains separately approved follow-up.
- **Success metrics**: The comparison gives evidence-backed supported/unsupported/unverified dispositions for each material dimension; numerical claims identify scope and raw counters and have reproducible evidence where needed; costs distinguish measured information from unknowns; feature 058 research records the recommendation and limitations with any separately approved migration follow-up.
- **Known constraint to preserve**: Kover 0.9.11's documented device-test limitation is established, not an unanswered question. Its front-end and backend options must be distinguished; current JaCoCo acceptance is historical evidence, not a comparative experiment.
- **Evidence boundary**: No comparative measurement has been run in this assessment. Hosted acceptance remains waived and unverified. Do not weaken current policy to make an option appear eligible.

### Carried-forward open questions

1. What concrete problems or recurring costs do developers experience, which roles are affected, and who participates in the owner's decision?
2. Which alternatives offer a supported complete host/device path for the current scope?
3. What establishes exact AGP 9.4.1/Kotlin 2.3.21/KMP compatibility for candidate host-side coverage?
4. What representative evidence is necessary to judge raw counters, Kotlin filtering, denominator preservation and duplicate/mismatched-class handling fairly?
5. Can existing baselines, regression checks and evidence contracts remain comparable under an alternative, and what transition would otherwise be required?
6. What are actual maintenance, runtime/reporting and migration costs within the hosted-worker constraint, and what measurement resources and appetite are available?
7. What contemporaneous records establish the earliest JaCoCo introduction and original selection rationale?

These unknowns inform specification and evaluation. They do not imply that a replacement must be built, that every candidate must be experimentally integrated, or that unsupported capabilities can be assumed.

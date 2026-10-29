# Implementation Plan: Coverage Tool Evaluation

**Branch**: `059-coverage-tool-evaluation` | **Date**: 2026-10-28 | **Spec**: [spec.md](spec.md)

**Input**: The assessment decision and clarified feature specification.

## Summary

Deliver a workload-first, evidence-backed comparison of JaCoCo, Kover, and at most one additional credible candidate. Establish eligibility before choosing whether a targeted, feasible measurement is needed. Record a retain, replace or defer recommendation in feature 058 research, with limitations and separately approved migration follow-up if applicable. This plan does not select an engine or perform the evaluation.

## Technical Context

**Language/Version**: Existing Kotlin 2.3.21, JVM target 17; PowerShell and Gradle configuration are existing inspection and verification tools. Recheck versions at execution.

**Primary Dependencies**: Gradle 9.6.0, AGP 9.4.1, JaCoCo 0.8.15; Kover 0.9.11 is the documented comparison version, subject to version-specific verification. Java 21 runtime as specified by policy.

**Storage**: Repository documentation and traceable source receipts; any experimental evidence retained with reproducibility metadata. No product storage changes.

**Testing**: Review evidence matrix and recommendation against FR-001–FR-011 and SC-001–SC-004; run a representative comparison only if material, necessary and feasible. Existing local acceptance is historical evidence.

**Target Platform**: Windows host and configured Android/JVM workloads; policy requires device API 35 and approved ABI for coverage. Present access is unknown.

**Project Type**: Internal, documentation-led technical evaluation of an Android/KMP application.

**Performance Goals**: Compare runtime and maintenance costs only when supported by evidence; no unapproved performance target or budget.

**Constraints**: Keep engine, policy, workloads and verifier unchanged; no custom hybrid collector or production integration. Hosted runner remains disabled and hosted acceptance waived/unverified. Do not equate XML format with counter or baseline equivalence.

**Scale/Scope**: Two required candidates, at most three total; current configured module and variant inventory, with native iOS excluded. Current policy lists 14 modules and three required device-test modules; revalidate rather than assume it remains unchanged.

## Constitution Check

**Before research: PASS.** No product, cryptographic, architecture, accessibility or performance behavior changes. Preserve coverage gates and evidence obligations (V), trace unsupported or unexecuted claims honestly, keep research bounded (VII), and synchronize feature 058 research with the evaluation outcome. No policy exemption is created.

**After design: PASS.** Evidence records and a documentation-only workflow add no speculative product infrastructure; optional experiments cannot weaken policy or imply hosted acceptance. No violations require justification.

## Project Structure

### Documentation (this feature)

```text
specs/059-coverage-tool-evaluation/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── tasks.md  # future task generation
```

### Repository evidence and output

```text
.specify/assessments/jacoco-evaluation/  # existing research and decision
core/ feature/ app/                     # configured workloads
 tools/coverage/policy.json             # inventory and current policy
specs/058-enforce-coverage-targets/     # existing evidence and final research destination
docs/backlog/                           # source item and separately approved follow-up
```

**Structure Decision**: The deliverable is an internal comparative research record, not an external interface or application change. No API contracts are needed. Refer to the data model for evidence and review requirements.

## Execution Design

1. Snapshot current policy, toolchain, source sets, variants, required host/device tasks and relevant historical evidence, noting revision and deviations from assessment.
2. Review versioned primary documentation for JaCoCo and Kover and run a bounded search for at most one additional credible candidate. Explain inclusion or why no third candidate qualifies. Kover backends are configurations of one candidate, not extra candidate slots.
3. Create a candidate-by-dimension matrix with supported, unsupported or unverified dispositions, scope, version, evidence and limits. Evaluate device/host eligibility before considering experiments; use the existing documented Kover device limitation as version-specific evidence, subject to revalidation.
4. Compare counters, generated-code filtering, inventory, variants, class/probe and data merging, baseline/regression continuity and operational/transition costs. Label evidence documented, measured, historical or unknown. Investigate history and original rationale using available records, recording gaps.
5. For each material unresolved question, record why a measurement is needed and feasible or why it is omitted. If performed, define identical revision, test scope, variants and inventory; retain commands, versions, environment, raw covered/missed counts, artifacts and discrepancies. Never combine incompatible data or extrapolate host-only results to complete coverage. Stop and document a limitation if prerequisites or valid comparison cannot be established.
6. Record a reasoned retain/replace/defer outcome and limitations in feature 058 research. Capture migration follow-up only with separate approval; do not close the backlog until its own completion criteria are met.

## Complexity Tracking

No constitutional violations or additional production abstractions proposed.

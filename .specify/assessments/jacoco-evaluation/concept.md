# Concept: Coverage Tool Evaluation

- **Slug**: jacoco-evaluation
- **Created**: 2026-10-28
- **Recommended option**: B — Bounded comparative evaluation

## Options

### Option A — Defer further evaluation

- **Sketch**: Keep using the existing JaCoCo pipeline and its recorded feature 058 evidence, leaving the comparative selection question open until a specific problem or a change in circumstances warrants investigation.
- **Appetite**: Small (days), as an upper budget for recording the deferral; actual effort is uncertain.
- **Trade-offs**: Avoids immediate investigation costs and preserves existing practice. It leaves the user's requested comparison and its decision evidence incomplete; no measured defect is currently established, but that does not prove the current choice is optimal.
- **Rabbit holes**: Repeated reconsideration without agreed triggers could consume attention without resolving the question.

### Option B — Bounded comparative evaluation

- **Sketch**: Compare the current arrangement with relevant alternatives against the actual Android/JVM workloads and coverage evidence requirements. Use authoritative sources and a small reproducible representative measurement where needed, distinguish front ends from engines, and record a reasoned retain, replace or defer recommendation with uncertainties and any separately approved follow-up.
- **Appetite**: Medium (weeks) as a provisional budget, not an estimate; device availability, compatibility findings and measurement complexity may change the effort.
- **Trade-offs**: Addresses the owner's request and the defined decision-evidence criteria while keeping a potential migration separate. It costs more than deferral and may still produce an inconclusive result if evidence or representative device access is unavailable.
- **Rabbit holes**: Comparing every possible product, building a production-ready replacement, attempting exhaustive device equivalence, or treating differing counters as directly comparable could expand the scope substantially.

### Option C — Documentation-only comparison

- **Sketch**: Compare the current tool and a small set of alternatives using existing repository evidence and authoritative documentation, recording unanswered compatibility and metric questions without new representative measurements.
- **Appetite**: Small (days) as a provisional budget, not an estimate; locating authoritative version-specific evidence may take longer.
- **Trade-offs**: Provides a faster initial comparison with less experimental effort. Documentation alone may not resolve connected-device, shrinking, merged-data or metric fidelity questions, so a definitive recommendation may not be supported and the backlog's completion criteria may remain unmet.
- **Rabbit holes**: Inferring project-specific compatibility from generic product descriptions or presenting missing measurements as negative results.

## Recommendation

Recommend **Option B** for the evaluation itself, not a decision to change coverage tools. It best addresses the requested comparison, evidence quality and traceability goals while permitting an honest deferral if material evidence cannot be obtained. Existing local acceptance and the absence of a demonstrated JaCoCo defect are reasons to evaluate costs and benefits carefully, not reasons to assume a migration is justified. ([problem](problem.md), [research](research.md))

## Out of Scope (for the recommended option)

- Changing the active engine, coverage thresholds or enforcement policy; any migration requires separately approved follow-up.
- Re-enabling the disabled runner, changing hosted controls or presenting waived hosted acceptance as verified.
- Establishing native iOS coverage or undertaking a comprehensive redesign of the coverage pipeline.

## Assumptions to Validate

- A finite set of alternatives can be compared meaningfully against the configured workloads, including the distinction between a front end and an underlying engine.
- Authoritative compatibility information and, where necessary, a representative local measurement can be obtained within the provisional appetite; device availability is not assumed.
- Metric and evidence differences can be described well enough for the owner to assess continuity and transition implications, even if direct numerical comparability is not possible.
- The owner accepts a documented defer recommendation if material evidence remains unavailable; the assessment itself does not establish this acceptance.

# Decision: Coverage Tool Evaluation

- **Slug**: jacoco-evaluation
- **Decided**: 2026-10-28
- **Verdict**: go
- **Artifacts reviewed**: [intake.md](intake.md), [research.md](research.md), [problem.md](problem.md), [concept.md](concept.md)

## Scorecard

| Criterion | Rating | Justification |
|-----------|--------|---------------|
| Problem validity | adequate | The owner requested a comparative rationale that the existing feature decision does not provide; no specific tool defect is established. |
| Evidence strength | adequate | Repository configuration, recorded results and selected primary documentation justify investigating; comparative evidence remains incomplete. |
| Value vs. inaction | adequate | A bounded comparison answers the explicit decision question; benefits and cost of inaction are not quantified. |
| Feasibility / appetite | adequate | A bounded review and representative measurements where needed are credible; the provisional appetite and device access remain uncertain. |
| Strategic fit | adequate | The evaluation supports coverage evidence needs while preserving current policy and the hosted-worker waiver. |
| Risk posture | adequate | The concept bounds scope and separates migration, while compatibility and measurement uncertainties remain. |

## Verdict & Rationale

**Go** to specifying the bounded comparative evaluation. Evidence is adequate to justify investigating the selection gap, not to select an engine or claim migration is beneficial. Option B addresses the request while allowing a documented deferral if essential evidence cannot be obtained. The provisional appetite is not an estimate; hosted acceptance remains waived and unverified.

## Handoff to /speckit-specify

- **Problem**: The owner lacks an evidence-based comparison of coverage tools for the actual Android/JVM workloads and requirements.
- **Chosen approach**: Compare JaCoCo and relevant alternatives using authoritative evidence and representative reproducible measurement where needed, then recommend retain, replace or defer.
- **In scope / out of scope**: Compare configured source sets, host/device workloads, variants, metrics, compatibility, evidence continuity, operating and migration costs; identify unknowns. Do not change the engine, thresholds or enforcement policy, reactivate the runner, claim hosted verification, or expand to native iOS coverage. Migration is separate follow-up.
- **Success metrics**: Document the comparison and limitations, support claims with evidence or mark them unverified, and record a traceable recommendation in feature 058 research, with any separately approved migration follow-up.
- **Carried-forward open questions**: Which candidates and engines support the exact toolchain and workloads? What measurements are needed and available? How comparable are metrics and existing baselines? What are maintenance and transition costs? What concrete user pain exists, and what evidence explains the original selection? How does the hosted-worker waiver constrain the comparison?

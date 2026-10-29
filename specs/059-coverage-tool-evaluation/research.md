# Planning Research: Coverage Tool Evaluation

This document records planning decisions, not completed comparative research. The prior [assessment research](../../.specify/assessments/jacoco-evaluation/research.md) contains primary-source links and receipts; revalidate time-sensitive claims during execution.

## Eligibility before measurement

**Decision**: Assess host/device workload support first, and mark each dimension supported, unsupported or unverified.

**Rationale**: The current policy requires both host and connected-device tests. Kover 0.9.11 documentation records a device-test collection limitation; that is evidence for that version, not a universal conclusion. Current local acceptance is historical and is not a comparison.

**Alternatives considered**: Assume report compatibility or run a host-only benchmark first; neither establishes complete-workload support.

## Bounded candidates

**Decision**: Compare JaCoCo and Kover; search for at most one additional credible candidate. Treat Kover backend configurations as variants of Kover, describing whether the engine changes.

**Rationale**: The owner set a three-candidate maximum including JaCoCo. A bounded search can find a relevant complete-workload contender without an exhaustive survey.

**Alternatives considered**: Compare every tool or assume another candidate exists; both exceed or misrepresent the agreed scope.

## Evidence and measurement

**Decision**: Prefer versioned authoritative sources and existing repository evidence. Use a small reproducible experiment only for a material question not resolved by sources, when resources and valid comparable scope exist. Otherwise explicitly record unverified status and impact on the recommendation.

**Rationale**: Exact toolchain compatibility, class identity, denominator preservation and baseline continuity are not established by compatible XML. Documented missing support can resolve eligibility without a build.

**Alternatives considered**: Build a hybrid collector or redesign the verifier to rescue an option; both exceed the approved scope.

## Decision and cost

**Decision**: Distinguish measured, documented, historical and unknown costs and evidence, and recommend retain, replace or defer according to the actual findings. Update feature 058 research and separately track only approved migration work.

**Rationale**: No quantified migration benefit, measurement budget or current device availability has been established. Hosted work remains waived and unverified.

**Alternatives considered**: Predetermine retention or replacement, or infer savings from a host-only experiment; neither follows from current evidence.

## Open evaluation questions

Exact current toolchain support, credible third candidate, fair counter/baseline equivalence, actual costs, device availability, developer pain and original selection rationale remain research outcomes, not unresolved plan choices. If essential evidence remains unavailable, the evaluation records the limitation and may recommend deferral.

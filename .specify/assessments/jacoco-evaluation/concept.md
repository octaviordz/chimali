# Concept: Coverage Tool Evaluation

- **Slug**: jacoco-evaluation
- **Created**: 2026-10-28
- **Updated**: 2026-10-28, America/Mexico_City
- **Inputs used**: [problem.md](problem.md), [research.md](research.md), [intake.md](intake.md)
- **Recommended option**: B — Bounded evaluation with workload eligibility
- **Scope of revision**: Updates the concept only; the existing decision has not been reassessed against this revision.

## Options

### Option A — Defer the comparison

- **Sketch**: Continue with the current JaCoCo pipeline and explicitly leave the comparative decision unresolved until a concrete problem, supported alternative or changed requirement warrants further investigation. The owner retains the current recorded local evidence without a new comparative recommendation.
- **Appetite**: Small (days) as a proposed upper budget for documenting the deferral, not an estimate or owner-approved allocation.
- **Trade-offs**: Avoids investigation costs when no quantified harm or migration benefit is established. It leaves the owner's requested comparison incomplete; current-tool success alone does not establish superiority. ([problem](problem.md), Cost of Inaction)
- **Rabbit holes**: Repeated reconsideration without new evidence; presenting deferral as completion of the original evaluation.

### Option B — Bounded evaluation with workload eligibility

- **Sketch**: Give the owner a comparison that distinguishes complete-workload candidates, limited host-side options and front-end changes over the same engine. Establish what each can support before investing in representative measurements; use measurements only where they can answer material unresolved suitability or cost questions. Record a retain, replace or defer recommendation with evidence, limits and any separately approved follow-up.
- **Appetite**: Medium (weeks) as an uncertain proposed budget ceiling, not a delivery estimate or owner-approved commitment. The evidence does not establish experiment cost or device availability; inability to obtain essential evidence can yield a documented defer outcome.
- **Trade-offs**: Addresses comparison completeness, numerical credibility, traceability and cost visibility from the revised problem definition. It avoids treating Kover 0.9.11's unsupported device collection as an unknown or host-only success as proof of full replacement. It still requires effort and may establish limits rather than a viable alternative. ([problem](problem.md), Success Metrics; [research](research.md), Candidate distinctions)
- **Rabbit holes**: Building a custom device integration to rescue an otherwise ineligible candidate; redesigning the verifier; comparing every product; measuring unlike inventories; or assuming a new XML report preserves current baselines.

### Option C — Complete a documentation-based comparison with explicit limits

- **Sketch**: Expand the existing source review into a comparison of documented capabilities, unsupported cases and remaining unknowns, without new comparative execution. Provide the owner a recommendation only to the extent that the available evidence supports one; preserve unresolved numerical or cost questions.
- **Appetite**: Small (days) as an uncertain proposed budget, not an estimate or owner-approved allocation.
- **Trade-offs**: The lightest substantive response to the evidence gap. Authoritative documentation can establish a missing required capability without an experiment, as it does for Kover 0.9.11 device tests. However, it may not establish exact toolchain compatibility, counter equivalence or operational costs, and it cannot support unmeasured numerical improvement claims. ([research](research.md), Version and compatibility evidence; [problem](problem.md), Success Metrics)
- **Rabbit holes**: Treating general compatibility statements as proof for this project, or making a definitive replacement recommendation despite unresolved requirements.

## Recommendation

Recommend **Option B** as the bounded evaluation concept. The goal remains a defensible tool-selection rationale, not a predetermined migration. Workload eligibility makes the investigation responsive to the refreshed evidence: Kover 0.9.11's documented device-test limitation already prevents its Gradle plugin from being treated as an established complete replacement. It can still be compared transparently as a limited option or front end; that does not justify building a new integration merely to keep it in contention. ([research](research.md), Candidate distinctions and Metrics, merging and report fidelity)

This approach permits documentary findings to resolve questions that do not need execution and reserves representative measurements for material uncertainties. It best fits the revised completeness and evidence-quality criteria while keeping cost and feasibility uncertainty visible. A retain or defer recommendation can be a valid evaluation outcome when supported by the comparison and its limitations. The evaluation itself does not decide which outcome the owner should adopt. ([problem](problem.md), Goals and Success Metrics; [intake](intake.md))

## Out of Scope (for the recommended option)

- Changing the active engine, thresholds, required workloads, generated-code policy or enforcement policy.
- Reactivating the disabled runner, changing hosted controls or claiming hosted acceptance.
- Native iOS coverage, a production-ready replacement, custom hybrid collection or a redesign of the verification infrastructure.
- Treating a reporting service as an instrumentation engine, or presenting compatible report syntax as proof of equal counters and evidence.
- Closing the backlog based solely on this concept; its required comparative recommendation and feature-research record remain outstanding.

These boundaries follow the [problem definition](problem.md) and [intake](intake.md); the production-integration exclusion bounds the evaluation rather than resolving feasibility through new implementation.

## Assumptions to Validate

- A finite comparison can give the owner useful evidence even if no complete replacement is established.
- Version-specific documentation or bounded representative evidence can resolve enough material questions within the proposed appetite; no budget or experiment duration is established yet.
- Necessary local measurement resources are available if a meaningful unresolved question requires them; historical device runs do not prove present availability.
- Counter and baseline differences can be characterized without silently weakening policy or combining incompatible host/device data.
- Unknown maintenance and transition costs can be reported honestly without inventing savings or claiming universal comparability.

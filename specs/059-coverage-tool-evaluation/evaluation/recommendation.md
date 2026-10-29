# Recommendation: Retain JaCoCo Provisionally

## Recommendation

**Retain JaCoCo for the current configured coverage workflow; defer any engine replacement decision.** This is the evidence-supported operational choice, not a claim that JaCoCo is universally superior. Kover 0.9.11's Gradle plugin does not collect required Android device-test coverage, while the current JaCoCo workflow has historical local acceptance for a complete recorded host/device run. No third candidate with an evidenced complete path was found within the agreed bounded search.

## Reasons and limits

The current workflow is the only compared option with project-specific evidence for the complete required workload. Kover remains a viable documented host-side option and offers a JaCoCo-backed front end, but neither establishes the required device collection or continuity of Chimali's custom inventory, fingerprints and accepted baselines. Exact Kover stack compatibility, cross-engine counter parity, maintenance/runtime costs, present device availability and the original JaCoCo selection rationale remain unknown. The search was bounded, not exhaustive, and no comparative measurements were run.

This evaluation does not authorize migration or alter policy, thresholds, hosted controls or runner state. No migration follow-up is proposed because no replacement is recommended. Reopen the choice if device-capable Kover support, a credible third tool, quantified recurring costs or changed requirements provide material new evidence.

## Backlog completion

The requested comparison and recommendation are now recorded here and in feature 058 research. The backlog's completion criterion for that evidence is met; no separately approved migration item applies.

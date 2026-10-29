# Evaluation Workspace

- Source revision: `eebc391bbe1956a325a95bcc6b2e401607e72ce7`.
- Scope: JaCoCo, Kover, and at most one additional credible alternative for configured host and Android device workloads.
- Fixed boundaries: preserve current engine, policy, workloads, generated-code rules, verifier and hosted controls; no runner reactivation or custom collector.
- Status vocabulary: documented, measured, historical, unknown; capability outcomes are supported, unsupported or unverified. Unknown is not a pass.
- Outcome: retain current JaCoCo workflow and defer replacement; comparative measurement omitted because Kover's assessed Gradle plugin lacks required device-test collection. Hosted acceptance and live runner state remain unverified.

## Artifacts

- `inventory.md`, `sources.md`, `history.md`, `origin.md`: setup and historical context, including the distinction between the earliest mention and first located JaCoCo setup.
- `jacoco.md`, `kover.md`, `third-candidate.md`, `eligibility.md`: candidates and workload dispositions.
- `metrics.md`, `continuity.md`, `costs.md`: fidelity, baseline and cost analysis.
- `measurement-decision.md`, `measurements.md`: experiment decision and record.
- `comparison.md`, `recommendation.md`, `validation.md`: conclusion and checks.

Final record: [feature 058 research](../../../058-enforce-coverage-targets/research.md).

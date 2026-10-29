# Quickstart: Validate the Coverage Tool Evaluation

## Prerequisites

Work from the intended revision with repository access. Read [spec.md](spec.md), [plan.md](plan.md), [data-model.md](data-model.md), current `tools/coverage/policy.json`, and the assessment research and source receipts. A connected device or a measurement budget is not assumed. Keep the disabled hosted runner disabled.

## Validation

1. Inspect the current inventory and versions, for example from the repository root:

   ```powershell
   Get-Content -Raw tools/coverage/policy.json
   Get-Content -Raw gradle/libs.versions.toml
   git rev-parse HEAD
   ```

   Expected: the evaluation identifies the revision, configured workloads and any change from historical evidence.
2. Review the candidate matrix: JaCoCo and Kover are included; at most one other candidate is included with a selection rationale. Every material dimension has a supported, unsupported or unverified disposition with scope and evidence. A documented device limitation is not silently converted to full support.
3. Inspect each numerical claim and any experiment receipt for identical declared scope, versions, commands, raw counts and reproducible artifacts. If a necessary comparison is infeasible, verify it is identified as unverified and its effect on the recommendation is explicit. A host-only run is not complete-workload evidence.
4. Check costs, class/probe and baseline continuity, historical rationale and hosted limitations for evidence or explicit unknowns. Confirm the recommendation in `specs/058-enforce-coverage-targets/research.md` is retain, replace or defer with reasons and limitations.
5. Compare the repository change set with the spec: no active engine, thresholds, workloads, generated-code policy, enforcement or hosted configuration changed. Check live runner state through an authoritative source when available; otherwise record it as unverified. Confirm any separately approved migration follow-up is tracked. The original backlog's completion criteria govern closure.

These are review procedures; no comparative measurement was run during this evaluation.

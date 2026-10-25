# Analysis Remediation Record

**Date**: 2026-10-24  
**Authorization**: User requested concrete remediation suggestions and their application after the read-only analysis.

## Applied design and task corrections

| Finding | Concrete correction | Planned verification |
| --- | --- | --- |
| U1 — omitted zero counters | Retain explicit instruction/branch totals from the same JaCoCo analysis result before XML export. After the XML is finalized, seal canonical `counters.json` with report/inventory/analyzer bindings. Normalize omitted zero XML counters only with matching proof; missing/contradictory proof fails. | T012/T015/T016/T020/T029/T075: actual exporter-generated empty/branchless fixtures, matched proof, absent/nonzero/altered proof, and omitted positive counter rejection. |
| U2 — candidate-controlled status producer | Split unprivileged capture from protected default-branch `workflow_run` reporting. A protected, pinned reporter creates the required `coverage-integration` check through a dedicated App; required protection accepts the exact App ID. Restrict the App key to the exact protected default-branch environment, excluding PR/merge refs. Validate run/repository/head/base/tree identities and invalidate stale-base decisions. | T037/T044–T049/T075/T078: forged same-named Actions success cannot satisfy the App-bound check; credentials unavailable to candidate jobs; actual source/protection/bootstrap and blocked/passing integration proof. |
| I1 — delayed repair and remeasurement | T055 defines the mandatory inline batch loop. T056–T058 execute descending risks, including immediate bounded repair and successful focused rerun before `Verified`, followed by fresh measurement/reranking before lower-risk progression. T059/T060 only audit receipts already produced. | T051/T053/T055–T060/T075: a failed high-risk case cannot advance until repaired, rerun and measured; new higher-risk candidates reenter the priority order. |
| G1 — aggregate-only shortfall | If individual floors pass while the repository remains below 50%, T061 extends the finite ledger with justified `AggregateFloor` behaviors from remaining production modules. The same risk/assertion/repair/measurement loop continues until a fresh aggregate passes; affected Tier 2 and existing Tier 1 obligations remain binding. | T051/T053/T061/T075/T077: a below-50 aggregate remains blocked despite passing individual floors; remaining-module cases and a measured passing aggregate are required for closure. |

## Scope and invariants

- Updated the specification, plan, research, evidence model, workflow contract, validation guide, tasks and quality-checklist notes consistently.
- Preserved 20 functional requirements, 8 success criteria, 80 task IDs, story labels and the 16 parallel markers. No task is marked implemented by this remediation.
- Preserved 60% Tier 1 instructions/branches, 50% repository instructions, existing affected Tier 2 instruction floors of 20%, and approximately 65% as the Tier 1 branch improvement goal.
- App/environment/reporter bootstrap, device capabilities, protected-source configuration and measured coverage remain explicit implementation prerequisites. No infrastructure was provisioned, no application tests were executed, and no runtime compliance result was created by these edits.
- These corrections resolve the four reported design gaps by inspection. Their actual behavior still requires the named implementation fixtures and operational evidence; a follow-up `/speckit-analyze` can independently recheck artifact consistency before implementation.

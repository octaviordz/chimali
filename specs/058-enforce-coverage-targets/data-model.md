# Data Model: Coverage Decisions and Evidence

**Date**: 2026-10-24  
**Status**: Design only. Structures below are tooling records, not application persistence schemas or claims of executed verification.

## CoveragePolicy

One reviewed, versioned JSON document at `tools/coverage/policy.json` supplies the executable acceptance configuration. The trusted harness selects this policy; a candidate cannot substitute a weaker file.

| Field | Meaning and validation |
| --- | --- |
| `schema_version`, `policy_id` | Supported schema version and stable policy identifier; unknown versions fail configuration validation. |
| `tier1_modules` | Exactly the five classified paths: `:core:security`, `:core:domain`, `:core:data`, `:feature:fido2`, `:feature:authenticator`. Missing/duplicate/unclassified entries fail. |
| `tier1_instruction_min_percent`, `tier1_branch_min_percent` | Integer values 60 and 60. Branch applicability requires a measured denominator. |
| `repository_instruction_min_percent` | Integer value 50; measured separately from module decisions. |
| `tier2_instruction_min_percent`, `tier2_gated_modules` | Value 20 and existing affected Tier 2 scope plus additionally changed Tier 2 owners identified during implementation. Branches reported without a new branch floor. |
| `branch_improvement_goal_percent` | Value 65; reporting goal, not an extra integration floor. |
| `metrics` | Explicit `INSTRUCTION` and `BRANCH` meanings and arithmetic; optional `LINE` is diagnostic only. |
| `counter_normalization` | `AnalyzedZeroProofV1`: canonical analysis counts and XML/inventory/analyzer bindings are mandatory; omission alone never establishes zero. Include this rule in the measurement compatibility fingerprint. |
| `production_inventory_rules`, `variant_rules` | Configured source/compilation boundaries and one measured production variant per module. Unknown/unmapped executable outputs fail accounting. |
| `generated_exclusion_rules` | Reviewed generation provenance and exact generated type/nested-class matching. No blanket handwritten-code exclusions. |
| `required_workload`, `device_profile` | Expected task/suite families, platform procedures and device capabilities. Actual tests may grow; silent removal of required workload is forbidden. |
| `regression_rule` | Nondecreasing applicable instruction/branch ratios for affected Tier 1 modules against the current accepted target. |
| `harness_revision`, `measurement_fingerprint` | Reviewed harness source identity and compatibility hash over metric definitions, toolchain/instrumentation, scope/variant/exclusion and workload/device rules. |

The compatibility hash does **not** require identical source/test contents, test names/counts, or production class bytes between baseline and candidate. These legitimately change. Such inventories are still hashed separately for run provenance.

## CoverageCounter

| Field | Meaning |
| --- | --- |
| `kind` | `INSTRUCTION`, `BRANCH`, or diagnostic `LINE`. |
| `covered`, `missed` | Required nonnegative integer counts in the canonical analysis result. Match present report-level XML counters; reject invalid, unexplained missing, duplicate aggregate or inconsistent counter data. |
| `total` | Derived as `covered + missed`; never supplied independently as an authoritative denominator. |
| `display_percent` | Derived for display only when total is positive. Never a gate input. |
| `applicability`, `reason` | `Applicable` or `NotApplicable`, with source/class inventory evidence for empty instruction scope or an actual zero branch denominator. |
| `xml_counter_present`, `zero_count_proof` | Record exporter presence. An omitted counter requires explicit canonical zero totals and proof from analysis of the identical retained compiled scope, linked by inventory/report digests and analyzer identity. Omission alone is never proof. |

Use arbitrary-precision intermediates. A floor passes when `100 * covered >= minimumPercent * total`. Regression passes when `candidateCovered * baselineTotal >= baselineCovered * candidateTotal`. A zero denominator never enters either comparison. Missing data is an error, not an evidenced zero.

JaCoCo 0.8.15 omits XML counters whose total is zero. The collector must retain explicit instruction/branch counts from the JaCoCo analysis result before XML serialization. After the report is finalized, persist `counters.json` with those counts, explicit zeros and the exact report/inventory/analyzer bindings. A valid omitted XML counter can normalize to zero only when this canonical analysis record exists, proves a zero total, and matches those bindings. Missing proof, nonzero analysis with omitted XML, a changed class inventory, malformed reports or mismatched counters fails completeness. Real exporter-generated empty and branchless XML belongs in the fixtures; handcrafted zero-counter nodes alone are insufficient. [Pinned exporter behavior](https://raw.githubusercontent.com/jacoco/jacoco/v0.8.15/org.jacoco.report/src/org/jacoco/report/internal/xml/XMLCoverageWriter.java)

## ProductionInventory and SuiteResult

`ProductionInventory` records module paths, configured source roots and source-set names, compiler task identities, selected variant, output locations, retained executable class identities, source/generated provenance, applied exclusions, and included/omitted-platform limitations. Each declared Tier 1 module has an inventory entry, including an empty module. Duplicate production class identities within a report fail validation rather than increasing the denominator. Host/device probes must match the selected class identities within the run.

`SuiteResult` identifies a task/suite/device combination and its expected role in the workload. Record discovered, executed, successful, failed, error and skipped test counts, task outcome, JUnit report hashes, execution-data hashes, timestamps and run attribution. Parameterized cases and the same suite on different devices retain their distinct execution identities. Do not count a skipped testcase as executed. Required suites with failures, errors, skipped required cases or zero actual execution fail; optional/nonapplicable procedures require explicit policy disposition and cannot hide a required obligation.

## VerificationRun

Each run has a UUID and immutable directory `runs/<run_id>/`. The manifest contains:

- `run_id`, `schema_version`, `purpose` (`Initial`, `Comparison`, or `Candidate`), start/end UTC timestamps, and user-facing execution date/timezone.
- `source_commit`, `source_tree`, optional PR `head_commit`, accepted `target_commit`, tested `candidate_commit/tree`, and before/after clean-source fingerprints. A dirty or changed measured tree cannot provide integration evidence.
- Policy/harness identities, compatibility fingerprint, tool versions, host identity, device model/API/capabilities and selected serial, measured variants, suite selectors, approved/observed exclusions, and canonical-counter/report/inventory bindings.
- Production inventories, suite results, module coverage results, repository aggregate, selection-record links, artifact manifest and the decision dimensions known when capture is sealed.

Every evidence record preserves the ten exact TRD keys: `requirement`, `scope`, `revision`, `named_procedure`, `environment`, `expected_observed_result`, `date`, `limitations`, `status`, `artifact_link`. Their contents reference stable requirements and named procedures, not just a feature-wide narrative. New coverage-specific fields supplement these keys rather than renaming them.

Hashes and run IDs detect accidental mutation and cross-run reuse; they do not by themselves authenticate a claim of test execution. Trusted workflow provenance and review remain required. Unknown status enums, malformed paths or attempts to reference artifacts outside the bundle fail validation. XML processing disables external entity resolution and does not fetch JaCoCo's DTD.

## ModuleCoverageResult

Each Tier 1 result relates one module to one run and includes its inventory, instruction/branch counters, applicable test results, floor decisions, comparison baseline identity, regression decisions, and limitations. Repository aggregate is a distinct record over all retained repository production classes. A scoped HDK diagnostic cannot replace either record.

| Situation | Required disposition |
| --- | --- |
| Verified no executable retained production instructions | Module `NotApplicable`, inventory evidence present, no invented 100% result. Reevaluate every run. |
| Executable instructions, zero measured branches | Instruction/test obligations apply; only branch metric is `NotApplicable`. |
| Missing canonical counters/zero proof, missing expected classes or unmapped source roots | `Unverified` completeness failure, never `NotApplicable`. A producer-proven omitted zero XML counter follows the normalization rule above. |
| Executable module and missing/empty required test execution | Failed/incomplete acceptance despite any available percentage. |
| Baseline empty, candidate executable | Candidate meets all applicable floors; record first applicable accepted baseline without inventing a prior ratio. |
| Baseline branchless, candidate gains branches | Apply the candidate branch floor and establish the first comparable branch result. |
| Baseline executable/branched, candidate becomes empty/branchless | Require evidenced scope accounting and a reviewed structural/behavior mapping; no automatic exemption by dropping production scope. |

## Decision dimensions and lifecycle

Store dimensions independently:

Capture manifests are sealed once collection ends. Later assessment, publication and promotion outcomes are separate immutable records referencing the capture digest; they never rewrite a sealed run manifest. Lifecycle transitions below describe the derived state across those records. Publication receipts carry archive commit identities outside the captured bundle, avoiding self-referential content hashes.

| Dimension | Values |
| --- | --- |
| `collection` | `Started`, `Complete`, `Incomplete` |
| `tests` | `Passed`, `Failed`, `Unverified` |
| `floors` | `Passed`, `Failed`, `Unverified` |
| `comparison_compatibility` | `Compatible`, `Incompatible`, `Missing` |
| `regression` | `Passed`, `Failed`, `Unverified`, or per-metric `NotApplicable` |
| `publication` | `Published`, `Failed`, `Pending` |
| `integration` | `Passed`, `Failed`, `Unverified` |

A run moves `Started -> Complete` only after required inventory, execution and report production are accounted for; collection failures leave an immutable `Incomplete` attempt with reasons. A complete run may have failing tests or floors and must retain that result. A **valid comparison baseline** requires complete collection, passed required tests, compatible measurement and trusted accepted-target identity; its floors may fail new requirements without relabeling the target as newly compliant.

Candidate integration passes only when collection is complete, required tests/floors/regression pass, compatibility is established, source/target identities are still current, artifacts are published, and the required hosted check/protection boundary is verified. Local verification can succeed while `integration` remains `Unverified` awaiting trusted publication/hosted acceptance. A successful ordinary local run, documentary approval, or existence of reports does not produce integration acceptance.

## AcceptedComparisonBaseline and ArchiveIndex

A baseline record identifies the actually accepted target commit/tree, run ID, archive branch/commit/path, artifact digests, policy/harness compatibility, and provenance (`ArchivedAcceptedRun` or `MeasuredAcceptedTarget`). It does not authorize lowering the candidate's floors.

Archive lifecycle: capture -> immutable bundle publication -> verification -> indexed comparison evidence -> promotion after actual integration/tree identity confirmation. Accepted records may be superseded but not silently overwritten. A changed target, mismatched policy, altered bundle or missing artifact makes the record unusable until remeasurement/republication. A mere pointer to expired transfer artifacts is not an accepted baseline.

The trusted publisher writes only the protected `codex/coverage-evidence` branch. `runs/<run_id>/` is append-only by run identity, with a machine manifest and human-readable index. The accepted-target index can advance with atomic conflict detection; concurrent publication cannot overwrite another run. Bundle cleanup must preserve active review/reference lifetimes. Archive failures prevent candidate acceptance. The product checkout is never altered to store its own measured evidence.

## TrustedReporterBinding

Each hosted acceptance receipt records the protected default-branch reporter workflow path and exact workflow commit, externally pinned reporter/harness SHA, dedicated GitHub App ID and installation ID, check-run ID, repository ID, capture run ID/attempt, PR head SHA, accepted base SHA, tested merge candidate SHA/tree, and assessed bundle digest. The required `coverage-integration` context is bound to that exact App ID; a GitHub Actions check with the same name is not interchangeable.

The App's private key is available only to the protected `coverage-reporting` environment with an exact selected-branch rule for the actual protected default branch; PR/merge refs are excluded. Candidate and device jobs never receive this credential. The reporter loads protected/pinned code only, never executes candidate or bundle files, validates current metadata and the eligible capture attempt, and refuses stale head/base/tree or cross-repository events. Default-branch workflow bootstrap, App installation, environment restrictions and source-bound protection must be verified before hosted acceptance can pass. On integration-target updates, the protected reporter invalidates outdated App decisions and requests fresh capture. These bindings establish the check producer; artifact digests alone do not.

## TestSelectionRecord

Records live in `test-selection.md` and can be exported into the run bundle without a new database.

| Field | Meaning |
| --- | --- |
| `candidate_id` | Stable feature-local selection ID, e.g. `COV-001`; this does not replace stable BRD/TRD requirement annotations. |
| `module`, `behavior`, `requirement_ids` | Concrete behavior and approved stable requirements. |
| `selection_reason` | `Tier1Risk`, `IndividualFloor`, or `AggregateFloor`. An aggregate shortfall permits justified candidates in any remaining production module without overriding higher-risk outstanding Tier 1 work. |
| `gap_evidence` | Fresh report/missed-decision location or existing assertion weakness; the module may already exceed its floor. |
| `impact`, `likelihood`, `risk_rationale` | Ordered risk ranks with a behavioral explanation. |
| `module_deficit` | Maximum positive percentage-point gap against applicable module floors, derived exactly from raw counts: Tier 1 60% instructions/branches, affected gated Tier 2 20% instructions. Zero when no applicable floor is breached. |
| `initial_state_or_input`, `expected_result`, `forbidden_side_effects` | Independent observable assertions, including typed failures/cleanup where relevant. |
| `test_location`, `existing_test_action` | Configured common/host/device placement and `Extend` or `Add`, with rationale. |
| `status`, `result_links`, `remaining_limitations` | `Identified`, `Selected`, `Implemented`, `Verified`, or `Deferred`; only actual passing named assertions support `Verified`. |

Risk ranks are `(impact, likelihood)` in descending order. Impact: 4 credential compromise/unauthorized access/irrecoverable sensitive-data loss; 3 integrity or recoverability failure; 2 bounded functional failure; 1 low-consequence behavior. Likelihood/exposure: 3 ordinary or externally supplied reachable conditions/known regression; 2 credible failure, boundary or lifecycle conditions; 1 constrained internal conditions. These are prioritization aids with reviewed rationale, not numerical probabilities or a new security certification.

For identical risk ranks, order larger exact `module_deficit` first; use stable candidate ID only for a remaining tie. Test effort and easy percentage gains cannot override this order. A finite reviewed candidate set and each batch's reranking are retained. Lower-risk unselected work and the 65% goal outcome remain visible; reaching a floor does not prove all security behavior is fully tested.

Every batch follows `Selected -> test/extend -> focused execution -> bounded repair if needed -> successful focused rerun -> Verified -> fresh measurement -> rerank`. A failed case remains unverified and blocks lower-risk progression; repair and measurement are performed inside the batch rather than deferred until all impact groups finish. Review-only tasks later audit these receipts and cannot backdate a missing pass.

If applicable individual floors pass while repository instructions remain below 50%, record the exact aggregate deficit and extend the finite ledger with `AggregateFloor` cases from remaining production scope. Use the same requirement/risk/assertion fields and batch loop, keep higher-risk outstanding Tier 1 cases ahead, and apply the existing 20% instruction gate to additionally changed Tier 2 owners. Aggregate improvement does not waive module floors or Tier 1 nonregression. Closure requires a fresh complete report at or above 50%, not merely an expanded task list.

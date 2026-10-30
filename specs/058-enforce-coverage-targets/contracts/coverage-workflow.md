# Coverage Workflow Contract

> **Policy update:** Feature 061 supersedes the hosted acceptance and target-freshness procedures below, which are retained as historical design. Its local manual assessment, review, initial-baseline guard and promotion entry points are implemented and fixture-validated. An authorized reviewer assesses trusted evidence against the accepted baseline identified at assessment time. A target advance alone does not require reassessment if the proposed integration tree is unchanged; a changed tree requires renewed verification. The earlier comparison does not prove nonregression against a newer baseline. The hosted rollout remains waived and unverified; fixed CI floors, required suites and evidence integrity remain binding. Active rules protect `main` and the archive, and the initial baseline is accepted; the controlled end-to-end review remains unverified. See [Feature 061 workflow](../../061-manual-coverage-integration/contracts/workflow.md).

**Date**: 2026-10-24  
**Status**: Planned developer/CI interfaces. The new entry points and hosted configuration described here must be implemented before the commands establish acceptance.

## Authority and inputs

**Delivery exception (owner, 2026-10-26):** operational T046–T049 is waived because disposable Windows/Android resources are unavailable and the persistent host/device exposure is unacceptable. The runner remains disabled and registered. The hosted clauses below describe the future rollout contract; the waiver does not make them pass or change command exit codes/receipt meanings. Local tests, hard floors, regression comparisons, and truthful evidence remain required. Duplicate operational T089 and the hosted demonstrations in T108 follow the same exception; see `../tasks.md` and `../implementation-evidence.md` §11.28.

The user-authorized hard floors are Tier 1 instructions 60%, Tier 1 branches 60% where measurable, repository aggregate instructions 50%, and existing affected Tier 2 instructions 20%. The branch improvement goal is 65%. Runtime policy must come from the reviewed trusted harness, not an arbitrary file supplied by the measured candidate. `tools/hdk-coverage.init.gradle` retains `hdkHostCoverage`, `hdkModuleCoverage`, `hdkRepositoryCoverage`, `hdkScopedCoverage` and `hdkCoverageVerify` names for compatibility; scoped output remains diagnostic. `hdkCoverageAnalyze` writes canonical counters and retained production inventories after the workload without failing solely on a below-floor result. `hdkCoverageVerify` separately enforces the hard floors.

The canonical commands use PowerShell 7 and argument arrays. They reject unknown arguments, invalid/mismatched revision IDs, nonunique module entries, traversal paths, writable output locations inside measured production scope, and evidence/configuration schemas they do not understand. Relevant manifest fields and arithmetic are specified in [data-model.md](../data-model.md).

## Capture command

`tools/coverage/capture.ps1` accepts:

| Argument | Contract |
| --- | --- |
| `-RepositoryRoot` | Absolute path to the measured clean checkout. |
| `-SourceRevision` | Exact expected commit SHA; it must equal the checkout's HEAD. Record its tree and verify clean source before/after execution. |
| `-Purpose` | `Initial`, `Comparison`, or `Candidate`; controls reporting purpose, not policy strength. |
| `-TargetRevision` | Exact accepted target SHA for candidate/comparison context. Required when the purpose is not `Initial`. |
| `-HarnessRoot` | Absolute path to the reviewed, pinned measurement harness checkout; may equal repository root for a reviewed local initial capture. |
| `-DeviceSerial` | Required selected device; preflight checks authorized/online status and required capabilities. Extra attached devices cannot silently alter the workload. |
| `-OutputRoot` | Absolute writable evidence parent outside the measured checkout. A new UUID directory is created; an existing run cannot be overwritten. |

The command reads the trusted harness policy, discovers configured scope/tasks, performs the full declared host/device workload with fresh execution, generates reports, and runs `hdkCoverageAnalyze` to write raw counters plus a retained class inventory. The bundle includes `manifest.json`, `summary.md`, canonical `counters.json`, the complete `inventory.json`, per-module/repository results, suite results, reports and file digests. Inventory entries bind source roots, compiler tasks, selected variants, output directories, generated exclusions, retained class identities and class hashes. Counters and zero proofs bind the exact inventory file digest, per-module class inventory digest, report digest and JaCoCo analyzer identity; the verifier recomputes class-list digests and checks all bindings against the bundled files. `hdkCoverageVerify` is a separate floor gate so a below-floor run still retains a complete diagnostic bundle. Omitted zero XML counters are accepted only with explicit matching analysis proof; missing proof, omitted positive counts or mismatches makes collection incomplete. It logs the bundle path and exits with the shared code below. It records and preserves diagnostics even if tests, collection or floors fail.

After discovery, an empty module is represented explicitly rather than removed. Every required executable module/suite must have matched current-run test outcomes and execution data. A report-only Gradle command is insufficient evidence of complete execution. App `shrunkDebug` device probes must be matched to compatible selected app production outputs.

## Verification command

`tools/coverage/verify.ps1` accepts `-RunBundle`, `-ComparisonBundle`, `-PolicyPath`, `-ChangedPathsFile` and `-OutputDirectory`. All paths are absolute. `-PolicyPath` is selected by trusted orchestration. A supplied baseline must be independently resolved from the accepted-target index or a trusted measurement of that target; candidate-provided arbitrary baseline paths do not qualify for hosted acceptance.

The verifier parses data without running code contained in bundles. It verifies schema, digests, expected inventories, test outcomes, revision identities, compatibility, exact floors and changed-module regression. A comparison run with complete successful tests and failing new floors may serve as a baseline. An incomplete/failed-test comparison cannot. A module's raw coverage counters cannot be replaced by the candidate's own derived percentage or `passed` flag.

It produces `decision.json` and `decision.md` with independent collection/test/floor/compatibility/regression decisions and one named reason for every unmet obligation. Absence of a required comparison is a failure to verify, not a regression pass. The canonical integration wrapper adds trusted publication and current-target checks before reporting integration success.

## Integration command

`tools/integration-ci.ps1` accepts:

| Argument | Contract |
| --- | --- |
| `-RepositoryRoot` | Measured clean candidate checkout; defaults to current repository when run locally. |
| `-CandidateRef` | Proposed exact candidate commit; defaults to clean HEAD locally. CI supplies the merge candidate explicitly. |
| `-TargetRef` | Required integration-target ref or SHA. Resolve/freeze its accepted commit; no inferred `main` or cached default. |
| `-HarnessRoot` | Required trusted harness checkout for hosted runs; local defaults must identify the reviewed source and remain local-only until trusted publication. |
| `-DeviceSerial` | Required complete-capability device selected for the policy profile. |
| `-EvidenceRoot` | Required evidence directory outside measured checkouts. |
| `-BaselineBundle` | Optional archived baseline hint; accepted only after target/provenance/compatibility verification. Otherwise obtain or measure the trusted target. |

No `-SkipTests`, `-SkipLint`, omitted-coverage flag, lower-floor override, or force-pass option exists. Invoke non-mutating full local checks with `-NoFix -NoConfigurationCache`, then canonical capture and exact comparison. If local checks run an earlier unit phase, those earlier executions do not replace fresh instrumented coverage execution. Fail rather than silently changing source to auto-fix it.

The integration target must be an ancestor of the proposed merged candidate and must match the accepted remote target, not merely a stale local ref or arbitrary SHA. Trusted CI event/current repository metadata establishes that identity; local orchestration refreshes/verifies it against the remote or fails when it cannot. A target update after resolution invalidates the decision. Local pre-push resolves an explicit configured target and verifies the pushed commit matches the assessed candidate; pushing a different ref cannot reuse its result. Local success is verification feedback, with integration still unverified until trusted publication and hosted acceptance. Hook bypass is not remote integration approval. Repository protection plus the required hosted check remains the final boundary.

## Exit codes and evidence states

| Code | Meaning |
| --- | --- |
| `0` | Requested verification succeeds. A local result alone does not prove remote protection/publication is configured. |
| `1` | Executed verification fails: test failures, fixed-floor breach, regression, or other proved acceptance failure. |
| `2` | Required evidence/prerequisite is missing, incomplete, incompatible, stale, unsupported or unavailable. |
| `3` | Invalid configuration, schema, argument or policy. |

Failure codes cannot suppress diagnostic bundle publication. A complete below-floor initial/comparison run normally exits 1, preserves raw results and is not relabeled as passing. Orchestration may use it as comparison evidence only after independently checking collection, test success, accepted-target identity and compatibility. Never treat every exit 1 as an acceptable baseline.

## Required hosted check

- Candidate capture workflow: `.github/workflows/coverage.yml`; it has read-only execution permissions and no App/publication credentials. The required final check is `coverage-integration`, produced exclusively by the dedicated coverage GitHub App, with required-status protection bound to its exact App ID rather than the GitHub Actions App or any source.
- Run for every proposed integration into the configured protected targets; use the PR head, base and exact merge candidate identities. If a merge queue is actually enabled, process its merge-group candidate. Do not omit checks through path filters.
- Required device capture runs on a disposable isolated Windows worker labelled for Android coverage, with serialized access to a clean test device. Execute candidate code with no production secrets and minimal read permissions. A queued/unavailable device worker must never produce success.
- Trusted reporter workflow `.github/workflows/coverage-report.yml` is loaded from the protected default branch through `workflow_run`. It checks out only the externally pinned protected reporter/harness revision and invokes `tools/coverage/report.ps1`, never candidate scripts or executable bundle contents. Store the App key only in the protected `coverage-reporting` environment restricted by an exact selected-branch rule to that default branch, excluding PR/merge refs. Workflow/pin changes require protected review. This bootstrap must exist before claiming required-check enforcement; no candidate-job or generic-token fallback is allowed.
- The dormant workflow code requires protected repository variables `COVERAGE_CAPTURE_WORKFLOW_ID`, `COVERAGE_HARNESS_SHA`, `COVERAGE_REPORTER_SHA`, and `COVERAGE_APP_ID`. Both SHA variables must be full reviewed commit IDs; the capture workflow ID must identify the single reviewed capture workflow. Merge-group freshness checks also require `COVERAGE_TARGET_REF` to name the exact integration branch as `refs/heads/<branch>`; no target is inferred from a queue ref. Until T046–T049 are authorized and provisioned, absence of these values must fail closed and no hosted check or runner is treated as configured.
- The reporter validates the repository ID, source workflow/run/attempt, current PR head/base/merge candidate/tree, harness/policy identities and artifact bindings, then uses an App installation token to create/update the required check on the current PR head SHA. Its receipt binds that head check to the exact tested merge candidate and base. A same-named candidate-generated Actions success cannot satisfy the App-bound requirement. If target advancement makes capture stale, the trusted reporter publishes a failure check on the captured head with the stale-binding reason, so an earlier success cannot remain the only decision. Updating the PR branch against its current base emits the explicitly enabled `synchronize` capture event and produces a fresh run; no Actions-write dispatch trigger is required. Obsolete capture events cannot overwrite the current attempt's decision.
- The reporter processes completed capture attempts including failure/cancellation/skip and explicitly requires necessary execution and verified evidence. It emits App `success` only after full verification/publication; unmet obligations produce `failure` or `action_required`, and in-progress work uses a non-completed check. It never uses a skipped/neutral conclusion as acceptance. If capture is removed or never starts, the required App check is absent and integration remains blocked.
- Required-check protection uses strict current-target verification and disallows accepted bypass. Workflow definition, device provisioning and protection configuration are separate obligations. A passing sample run alone does not prove protection is active.

`tools/coverage/report.ps1` is an internal trusted entry point, not a candidate-selectable command. The protected workflow supplies authenticated event/run metadata, its pinned harness root, the expected App/installation IDs and verified bundle paths; it obtains a short-lived App installation token from the protected key and never accepts authority settings from the candidate manifest. No hosted App server is required. Use distinct ordinary Actions job names so only the App-created check is named as the required acceptance context. Validate current repository metadata again immediately before publishing success, and record the resulting App/check/commit bindings in the immutable assessment receipt. Secrets are neither command-line arguments nor report content.

## Baseline, publication and acceptance

For a compatible archived current-target result, verify source/run identity, trusted publication and bundle hashes before comparison. Otherwise collect the accepted target in a separate isolated checkout with the same external trusted harness. Keep comparison data separate from candidate execution and never merge their `.exec/.ec` files.

The measurement compatibility policy is stable across compared runs; normal production/test additions are allowed to change counts. Shared infrastructure changes conservatively expand affected Tier 1 scope. Changed module boundaries need an evidenced mapping or reviewed replacement baseline. Empty/branchless transitions follow the applicability rules in the data model.

Publish immutable review/baseline bundles to protected `codex/coverage-evidence` using unique `runs/<run_id>/` paths. Capture is sealed before assessment; later immutable decision/publication records reference its digest without modifying it. Human-readable summaries and supporting XML/HTML/test reports remain retrievable while used for review/comparison. Transfer artifacts are not the sole long-term baseline location. Validate digests and reject extraction paths outside the bundle; a hash mismatch is blocking. Archive/index updates must detect races rather than overwrite another run.

A final hosted integration success requires complete current execution, passing applicable tests/floors/regression, compatible accepted-target identity and published accessible artifacts. This remains the historical Feature 058 hosted design under the current owner waiver, not an operational status claim. Feature 061 specifies manual review against the accepted assessment-time baseline, preserves `codex/coverage-evidence` on historical receipts, and uses `swe/coverage-evidence` for new reports after its unverified migration. After integration, promote a baseline only if the actual integrated tree equals the tested tree, recording both commit identities when necessary. A changed tested tree requires new verification. Product evidence and archive commits never assert that a different product revision was tested.

## Behavior-test selection contract

Build a finite selection inventory from fresh coverage, stable requirements and existing assertion review. Rank `(impact, likelihood)` descending across all Tier 1 modules, including modules already above their floors. The larger exact module deficit orders equal-risk cases; stable candidate ID resolves any remaining tie. If repository instructions remain below 50% after individual floors pass, extend the inventory to justified `AggregateFloor` behaviors in remaining production modules while keeping higher-risk outstanding Tier 1 work first. The ranks and record fields are defined in the data model.

Each selected behavior has an input/state, independently derived expected outcome, applicable forbidden side effects and an `Extend`/`Add` decision before coding. Use common tests for neutral behavior and platform/integration/device tests where the real boundary demands them. A weakly asserted but executed path can outrank an uncovered trivial path. Each batch must complete test/extend, focused execution, any bounded repair and successful rerun before marking its behavior verified; clean-state fresh measurement and reranking then precede lower-risk progression. Later audit tasks do not defer this loop. Maintain all hard floors and report the 65% goal separately; aggregate closure requires an actual fresh result at or above 50%.

## Contract validation obligations

The fixture harness must exercise exact/just-below floors, misleading display rounding, regression above the floor, large count products, real exporter-generated zero-counter omissions with matching and absent/contradictory analysis proof, generated/retained output inventories, empty-to-executable and branchless transitions, failed/skipped/zero required tests, stale/cross-run reports, mismatched classes/variants, changed target, incompatible policy, forged baseline identity, source mutation, archive failure, same-named candidate Actions check versus the App-bound required source, per-batch repair/reranking before lower-risk work, and aggregate-only shortfall after individual floors pass. Fixture success never substitutes for actual host/device execution or the blocked-merge demonstration.

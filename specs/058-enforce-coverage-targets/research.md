# Research: Enforce Coverage Targets

**Date**: 2026-10-24  
**Scope**: Read-only inspection for feature 058. No builds, tests, device ceremonies, repository protection changes, or fresh coverage measurements were executed.

## 1. Reuse the installed coverage toolchain

**Decision**: Retain Gradle 9.6.0, AGP 9.4.1, Kotlin 2.3.21/JVM 17, and JaCoCo 0.8.15. Extend `tools/hdk-coverage.init.gradle`; keep its existing task names as compatibility entry points. Add a small PowerShell 7 orchestration and verification layer without a new Gradle module or testing-library dependency.

**Rationale**: These versions and report tasks are present in the repository. The current gate already contains the requested 60% Tier 1 and 50% aggregate numbers; that partial edit does not establish correct discovery, completeness, or regression enforcement. Existing local CI can supply static analysis and compilation while the canonical coverage collector controls fresh execution.

**Alternatives considered**: Migrating coverage engines or adding a Gradle convention-plugin project would increase scope before correcting the existing evidence gaps. General local-CI refactoring is unnecessary.

**Evidence**: `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `tools/local-ci.ps1`, and `tools/hdk-coverage.init.gradle`.

## 2. Define the actual metrics and preserve integer counts

**Decision**: Treat JaCoCo instruction coverage as the explicit measured quantity, with branch coverage separate. Use raw covered/missed counts and exact integer cross multiplication for floors and regression comparisons. Use line coverage only as a diagnostic. Retain explicit instruction/branch counts, including zeros, from the same retained-scope JaCoCo analysis result before XML serialization; after the report is finalized, write canonical `counters.json` with report/inventory/analyzer bindings. An omitted zero-total XML counter may normalize to zero only with this matching proof; unexplained missing counters remain errors. Branch denominator zero is branch-only not applicable.

**Rationale**: Current report parsing discards counts and maps zero denominators to 0%. It cannot faithfully implement exact regression decisions or branch applicability. Instructions are bytecode operations, and JaCoCo does not count exception handling as branch coverage; behavioral failure/cleanup tests are required even when branch counters do not expose those paths. [JaCoCo counters](https://www.jacoco.org/jacoco/trunk/doc/counters.html)

**Alternatives considered**: Rounded percentages hide near-threshold failures. Claiming source-statement or MC/DC measurement from these counters is inaccurate. Existing terminal-exception evidence rules remain narrow and do not lower module floors.

**Remediation evidence (U1)**: The pinned exporter writes counters only when `getTotalCount() > 0`. Therefore absent XML alone cannot distinguish a valid zero from missing evidence. Test actual empty/branchless exporter output with matching analysis proof, and separately reject missing/contradictory proof or omitted positive totals. [JaCoCo 0.8.15 XML exporter](https://raw.githubusercontent.com/jacoco/jacoco/v0.8.15/org.jacoco.report/src/org/jacoco/report/internal/xml/XMLCoverageWriter.java)

## 3. Inventory configured production and test scope

**Decision**: Discover source sets, compilation outputs, and test tasks from the configured Android/KMP model. Maintain explicit records for all five Tier 1 modules even when empty. An empty disposition requires both a configured-source inventory and analysis of retained production output after the approved generated-code policy; source absence alone is insufficient. Native iOS targets remain outside this Android coverage claim and are named as a limitation.

**Rationale**: The current gate checks only four conventional source paths before dropping empty modules. `core:data` has Android production/tests; `feature:authenticator` currently contains only an iOS README. Configured custom roots, generated outputs, and classes without source mappings cannot silently disappear. Interfaces with no executable instructions do not create an artificial coverage denominator.

**Alternatives considered**: A static directory scan can falsely exempt a module. Manufacturing tests or executable code for an empty module would distort the goal. Requiring every empty repository module to produce execution data is also incorrect; required suite obligations follow executable scope.

## 4. Match variants and execution data

**Decision**: Freeze each module's measured production variant and permitted workload in a versioned policy. For the app, use the existing `shrunkDebug` coverage-compatible production variant and its matching unit/device outputs; do not combine debug classes with differently compiled/minified probes. Inspect configured outputs and class IDs during implementation before accepting this mapping. Merge only compatible execution data; quarantine mismatches and block completeness while a required contribution is missing. Exclude generated SQLDelight classes by exact generated type plus nested-class identity, not a broad `Type*` prefix.

**Rationale**: The app's device tests use `shrunkDebug`, while the collector selects debug compiler names. A source filename is not proof of identical bytecode. Existing SQLDelight prefix exclusions can accidentally cover handwritten siblings. Generated-source policy must be identical in baseline and candidate; no new blanket exclusions are authorized.

**Alternatives considered**: Counting multiple variants duplicates production denominators. Silently ignoring mismatches can produce an apparently successful but incomplete report. Changing production shrinking to make coverage easier would alter the product verification boundary.

## 5. Collect complete runs independently of floor results

**Decision**: Give each run a UUID and clean run directory. Force all required host/device test workloads to execute without test-result cache reuse. Capture structured suite outcomes, JUnit reports, execution files, inventories, tool versions, devices, and file hashes. Parse generated XML with DTD processing disabled/ignored and external resolution disabled. Publish completeness, test, floor, and regression outcomes separately, including on failure.

**Rationale**: Current preparation clears execution files but not all stale reports; repository-only test tasks are not all forced fresh. Counting `<testcase>` tags includes skipped/failed cases. Report task ordering alone does not execute its prerequisites when called outside the aggregate task. Gradle's JaCoCo report/verification tasks need deliberate wiring to testing and lifecycle checks. [Gradle JaCoCo plugin](https://docs.gradle.org/current/userguide/jacoco_plugin.html)

**Alternatives considered**: Timestamps or report existence alone cannot establish freshness. Failing before reports are retained would lose useful below-floor baseline evidence. No Pester installation is needed: a PowerShell fixture harness can test the parser, ratio comparisons, state transitions, and exit results.

## 6. Make integration enforcement remote and mandatory

**Decision**: Add a stable required `coverage-integration` check produced by a dedicated installed coverage GitHub App, with required-status protection bound to that exact App ID. A disposable Windows/device worker performs unprivileged capture. Protected default-branch reporting verifies the exact base/head/merge candidate/tree and publishes the App decision; same-named candidate Actions success cannot replace it. Use strict up-to-date target protection, no accepted bypass and no path-based omission. Local `tools/integration-ci.ps1` provides verification feedback while hosted acceptance remains separate. App installation, protected reporting bootstrap/environment and source-bound protection are explicit provisioning prerequisites.

**Rationale**: `.gitconfig` currently invokes local CI without coverage, and no hosted workflow is checked in. A local hook can be bypassed and cannot control remote merges. Required checks and protection are the actual merge boundary. The target branch must come from the PR or an explicit input; cached `origin/HEAD` pointing to `lab/or/jpc` is not live configuration evidence. [GitHub protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)

**Alternatives considered**: Merely making a switch default cannot satisfy mandatory remote integration. Introducing a merge queue is unnecessary; if one is already configured during provisioning, handle its merge-group candidate too.

**External prerequisites**: GitHub CLI exists but is unauthenticated. Runner availability, repository visibility/permissions, protection support, actual integration branches, and current protection settings could not be verified. Implementation must provision or validate them and prove a blocked merge before claiming FR-012/SC-003 complete. This is an execution prerequisite, not an unresolved design choice.

## 7. Separate candidate execution from trusted assessment

**Decision**: Pin the reviewed measurement harness/policy and reporter SHA in protected repository configuration. `.github/workflows/coverage.yml` captures candidate data without App secrets. `.github/workflows/coverage-report.yml` is loaded from the protected default branch via `workflow_run`, invokes only pinned protected `tools/coverage/report.ps1`/harness code and never executes candidate or bundle files. Store App credentials in a protected `coverage-reporting` environment restricted to the exact selected default-branch ref, excluding PR/merge refs. The App-authenticated check on the PR head binds the tested merge/base/tree and current capture attempt. Protected target-push handling invalidates stale-base decisions and requests new capture. Require workflow/pin review and reject stale/cross-repository events and source identity substitution. Avoid privileged execution of candidate code through `pull_request_target`.

**Rationale**: Candidate-editable thresholds cannot be the sole authority for their own acceptance. Self-hosted execution also requires a clean worker/device boundary; one-job runner registration does not itself clean reused hardware. Checksums detect altered artifacts but do not prove that untrusted test code is honest; normal code review remains necessary. [GitHub secure use](https://docs.github.com/en/actions/reference/security/secure-use)

**Alternatives considered**: A pinned harness inside a candidate-editable final job does not protect the check producer. Requiring the check name from the generic GitHub Actions App does not distinguish that job from a same-named candidate success. The chosen dedicated App source and protected reporter avoid that ambiguity. Giving a build worker publication credentials or trusting self-reported success defeats the boundary. A separate coverage service or custom cryptographic attestation system is outside scope.

**Remediation evidence (U2)**: GitHub's `workflow_run` reporting context is based on the default branch, the Checks API supports App-authenticated check runs, and environment branch restrictions match the workflow ref. Verify all three controls and the expected App ID during provisioning; unsupported controls leave enforcement unmet. [Workflow events](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#workflow_run), [Checks API](https://docs.github.com/en/rest/checks/runs#create-a-check-run), [environment branch restrictions](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments#deployment-branches-and-tags)

## 8. Bootstrap and compare accepted target evidence

**Decision**: Resolve and freeze the accepted target SHA. If a compatible result does not exist, measure that target with the same trusted external harness and policy, without modifying its production/test files. A complete below-floor target result is valid comparison evidence, not a new compliance pass. Require candidate floors plus nondecreasing exact instruction/branch ratios for changed Tier 1 modules. Shared build/test policy changes expand the affected-module set. Refresh if the target advances.

**Rationale**: New tests and production code must be allowed to change counts. Therefore compatibility fingerprints include metric/tool versions, scope/variant rules, exclusions, required suite families and device profile, but not identical ordinary source files, test names, test counts, or production bytecode hashes across different revisions. Within one run, probe/class identities still must match. Structural module changes need an explicit scope mapping or reviewed compatible replacement baseline.

**Alternatives considered**: Comparing against the branch's first run alone ignores the integration target. Requiring the old target to pass new floors prevents honest bootstrap. Comparing rounded ratios or accepting a candidate-supplied baseline enables hidden regression.

## 9. Retain evidence outside disposable build outputs

**Decision**: Write immutable run bundles containing JSON, XML, human-readable summaries/HTML, required test reports and inventories. Use Actions artifacts for job transfer and review, then publish required review/accepted-baseline bundles to a protected `codex/coverage-evidence` archive branch through the trusted publisher. Store bundles under unique run IDs, verify archive/file digests, prohibit overwriting accepted bundles, and maintain an index mapping accepted target revisions/trees to compatible runs. This avoids evidence commits changing the measured product revision. No new artifact service is introduced.

**Rationale**: `**/build/` is ignored and replaceable. Finite Actions artifact retention alone is insufficient while a baseline remains referenced; transfer artifacts also need explicit digest validation. Archive publication failure prevents integration success. [GitHub workflow artifacts](https://docs.github.com/en/actions/tutorials/store-and-share-data)

**Alternatives considered**: A tracked Markdown statement without accessible reports is insufficient. Committing large changing reports into the measured product tree creates revision churn. An external object store adds an unnecessary service. Archive branch growth must be monitored; retention cleanup cannot remove a referenced baseline or a report under active review.

## 10. Apply the approved risk-first selection strategy

**Decision**: Review fresh missed decisions and assertion weaknesses across all five Tier 1 modules. Rank impact first, then likelihood/exposure; among equal risk ranks, prefer the larger module shortfall against its applicable floor. Stable candidate ID breaks any remaining tie. A high-risk candidate above 60% precedes a lower-risk candidate below 60%. Record requirement, evidence, rationale, initial state, expected result, forbidden side effects, test placement, and observed outcome before closing a candidate.

**Rationale**: This implements clarification A and prevents selecting trivial code solely for percentage gain. Start with a finite reviewed candidate inventory and refresh after each batch. Domain's historical deficit is an investigation lead, not a fixed starting order. Use existing tests where possible, `commonTest` for neutral logic, and host/device integration where required by the actual boundary.

**Provisional investigation leads**: `SensitiveText` ownership/view cleanup; `UserConsentRecord` validation and RP matching; `AesGcmEncryptedMetadataService` failure/AAD/session behavior; `VaultAggregateServiceImpl` append/retry/reconstruction cleanup; FIDO2 assertion authorization and disconnect/cancellation. Existing tests and October 18 reports already cover parts of these behaviors. Verify discovery (including legacy `src/test` tests in KMP modules), actual gaps, assertion strength and stable BRD/TRD IDs before selecting any case. These are not claims of current defects or a finalized work order.

**Alternatives considered**: Lowest-percentage-first contradicts the approved risk order. A permanent global function-to-test inventory or blanket test generation is unnecessary; finite requirement/decision records serve this feature.

**Remediation decisions (I1/G1)**: Execute repair and successful focused reruns inside each batch before `Verified`, then obtain clean-state fresh measurement and rerank before lower-risk progression. Later tasks audit completed receipts and cannot defer those steps. If individual module floors pass while the repository remains below 50%, extend the finite ledger to requirement-backed `AggregateFloor` cases from remaining production modules, preserving higher-risk outstanding Tier 1 priority and the existing affected Tier 2 gates. Continue the same loop until a complete aggregate passes; no new blanket exclusions or easier percentage-only tests are accepted.

## 11. Synchronize governance without rewriting history

**Decision**: First implementation milestone adopts Principle V's strengthened thresholds, mandatory integration gate and regression obligations, with a 4.1.0 to 5.0.0 major governance amendment if 4.1.0 is still current. It tightens acceptance in a way that invalidates previously permissible outcomes. Synchronize BRD NFR-ARCH-050, TRD 2.2/9, rules and active instructions in the same change. Preserve BRD's separate 100% core business-logic coverage objective and existing feature-055 deferrals/review limits.

**Rationale**: The user has already authorized the new floors and risk ordering. Constitution/BRD/TRD still publish earlier minima. The amendment is scoped and justified by the feature; no extra approval loop is inferred. Historical measured results, exclusions, dates, and original acceptance decisions must remain unchanged and visibly historical.

**Alternatives considered**: Lowering the new feature to old figures ignores authorization. Claiming existing artifacts verify the new policy is false. Silently weakening separate stronger objectives is outside scope.

## Research disposition

All design choices needed for Phase 1 are resolved. Actual task-model execution, class/probe compatibility, device capabilities, remote provisioning, and measured deficits remain mandatory implementation verification. No fresh compliance or integration-enforcement claim is made by this research.

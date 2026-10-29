# Implementation Plan: Enforce Coverage Targets

**Branch**: `codex/058-enforce-coverage-targets` | **Date**: 2026-10-24 | **Spec**: [spec.md](spec.md)

**Input**: `specs/058-enforce-coverage-targets/spec.md`, including the confirmed percentages and clarification A: risk first across all Tier 1 modules, then coverage deficit among equally risky candidates.

**Planning status**: Phase 0 research and Phase 1 design complete. Existing threshold/module-list edits are partial, unverified implementation. No fresh baseline, product test run, device run, hosted check, or protection change has been performed in this planning workflow.

**Later policy change (2026-10-28)**: The owner directed removal of the separate BRD §9 100% core-business-logic coverage requirement. References below to preserving that objective record the earlier plan and are superseded by the current BRD. Coverage floors and the hosted-work waiver are unchanged.

## Summary

Keep the current JaCoCo toolchain and make its results complete, exact and enforceable. Every executable Tier 1 module must independently meet 60% instruction and 60% applicable branch coverage; repository aggregate instructions must meet 50%. Preserve existing Tier 2 instruction floors of 20%. Approximately 65% branch coverage is the remediation goal, not a second acceptance floor.

Use configured production/test inventories, explicit empty/branchless dispositions, run-specific evidence, exact integer comparisons, and a trusted accepted-target comparison. Improve behavior tests in risk order across all five critical modules, including modules already above their floors. A required GitHub integration check on a device-capable worker enforces complete verification; local hooks provide early feedback. Durable report bundles and separate completeness/test/floor/regression decisions support review and honest bootstrap.

## Technical Context

### Owner-authorized delivery exception (2026-10-26)

The owner waived operational T046–T049 after reassessing malicious-PR exposure of the persistent Windows host and attached Android device. No acceptable disposable Windows worker and disposable Android physical/virtual device are available; the investigated Sandbox/Hyper-V routes did not resolve the hardware constraints. The registered runner is disabled and must remain so until explicitly authorized by the owner. The earlier disposability-only waiver is superseded by this four-task waiver.

The same exception covers the duplicate operational scope in T089 and the hosted-control/archive/integration demonstrations in T108. Continue local baseline, behavior tests, exact hard-floor/regression measurement, retained local evidence, and final documentation. Preserve the original design below as the requirements for any future hosted rollout. Report hosted enforcement, durable hosted publication, and controlled integration as unverified/waived rather than successful. The waiver does not authorize a synthetic passing receipt, a weaker executable policy, runner reactivation, or an unrequested change to existing GitHub protections. See `tasks.md` and `implementation-evidence.md` §11.28 for scope and rationale.

**Language/Version**: Kotlin 2.3.21/JVM target 17 for product/tests; existing Groovy Gradle init script; PowerShell 7.x and JSON for orchestration, evidence and deterministic verification; YAML for the hosted workflow. Use existing Gradle 9.6.0 and AGP 9.4.1. No toolchain or production dependency upgrade.

**Primary Dependencies**: JaCoCo 0.8.15; existing Kotlin test, JUnit 4/5, coroutine test, AndroidX/Compose testing and SQLDelight test drivers. Git/GitHub Actions, a repository-installed dedicated coverage GitHub App, and Android platform tools. Use built-in .NET XML/JSON, hashing and arbitrary-precision integer support; add no Pester dependency or Gradle module. App/environment/protection provisioning remains implementation work, not an assumed existing capability.

**Storage**: Immutable JSON/XML/HTML/test-report bundles outside the measured checkout; temporary Actions transfer artifacts; a protected `codex/coverage-evidence` archive branch for required review/baseline reports and accepted-target index. Existing application storage remains outside this change.

**Testing**: Contract fixtures for coverage parsing, raw-count comparisons, inventory/applicability, completeness, revision matching and regression; configured common tests for neutral behavior, Android host tests for platform-dependent logic, and existing connected device suites. Reuse/extend existing behavior tests and demonstrate named assertions. Baseline and final full run require a connected Android device capable of all required procedures without silently inheriting historical exclusions.

**Target Platform**: Windows developer/CI host with PowerShell 7, compatible JDK/SDK 37 and connected Android API 28+ device; select a complete-capability device profile during runner provisioning. Android coverage claims include common code compiled into the chosen JVM/Android variants. iOS native coverage is explicitly outside the measured scope.

**Project Type**: Internal build/verification tooling and test remediation in an existing Android/KMP mobile application; no new product journey or public application API.

**Performance Goals**: Correctness and freshness take precedence over build-result reuse. Preserve all existing product latency/size/shrinking obligations. Record verification durations and use one serialized device workload per worker. Do not invent a CI time budget from historical timings.

**Constraints**: Exact full-precision comparisons; five-module Tier 1 inventory; no hidden production/test omissions; compatible class/probe data; unchanged generated-code policy between compared runs; exact candidate/target identity; no skipped required tests; protected trusted assessment; synthetic data only; evidence artifacts remain accessible while referenced. The user's authorized governance amendment precedes enforcement rollout.

**Scale/Scope**: L. Five Tier 1 modules, existing affected Tier 2 gates (`core:database`, `core:common`, `app`, plus any newly affected Tier 2 owner), and the configured repository-wide Android production inventory. Four independently testable implementation increments: policy/collector, baseline and selection, behavioral remediation, mandatory integration/regression/evidence. Their detailed tasks belong to `/speckit-tasks`.

## Constitution Check

*Planning gate: pass with the explicit authorized amendment below. Implementation and runtime acceptance remain open; the table does not certify compliance.*

| Principle | Initial finding | Post-design disposition |
| --- | --- | --- |
| V / governance: coverage and evidence | The prior baseline used Tier 1 40% and repository aggregate 20%. Principle V states concise tier and repository percentages and delegates detailed rules to the development rules. | Principle V 5.0.0 carries the requested concise thresholds: Tier 1 60%, Tier 2 20%, repository aggregate 50%. BRD NFR-ARCH-050, TRD §2.2/§9, contributor rules, executable gates, and current verification instructions define the metric meanings, branch applicability, integration enforcement, and Tier 1 nonregression. The separately measured runtime gates and acceptance remain implementation work. |
| I: secrets and failure cleanup | New tests/logs can traverse credential and key boundaries. | Synthetic fixtures; assertions cover owned-buffer erasure, authorization and forbidden side effects. No real secrets in artifacts. Device workers use disposable test state and no production credentials. |
| III / VII: architecture, YAGNI and module count | Coverage policy/evidence need shared tooling but no product abstraction. | Existing init script plus focused PowerShell tools; no new Gradle module, database, coverage service or product framework. Required-check and archive infrastructure directly satisfy FR-012/FR-017. |
| IV: auditability | Data-layer behavior needs failure/replay tests. | Preserve append-only product history and committed-event semantics. Do not modify product persistence to improve percentages. Evidence archive is separate from product revision identity. |
| V / rules: traceability, independence and MC/DC mindset | Percentage improvements alone can conceal weak assertions. | Each selected behavior has stable BRD/TRD requirement IDs, risk/expected-result records and deterministic assertions. No shared mutable test state; exception/cancellation/cleanup behavior remains required beyond JaCoCo branch counters. |
| VII: document synchronization | Dated feature-055 evidence and stronger separate BRD objectives already exist. | Preserve historical outcomes/exclusions/deferrals and the BRD 100% core business-logic objective. Update active instructions and clearly distinguish design, measured results and integration acceptance. |
| VI / VIII: product budgets and platform obligations | Hosted verification does not establish device-class or performance equivalence. | Preserve existing product budgets and platform/device limitations. Coverage suites cannot certify physical Bluetooth interoperability, accessibility or numerical performance without their separate required evidence. |

The owner-authorized operational exception above is the current delivery constraint. Unavailable or waived remote execution remains explicitly unverified; local measurement and class compatibility still require evidence and are not assumed passes.

## Project Structure

### Documentation (this feature)

```text
specs/058-enforce-coverage-targets/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── coverage-workflow.md
├── checklists/requirements.md
├── test-selection.md             # produced from the initial baseline during implementation
└── implementation-evidence.md    # measured implementation results; not fabricated during planning
```

### Source Code (repository root)

```text
tools/
├── hdk-coverage.init.gradle       # extend discovery, execution and report generation
├── local-ci.ps1                  # retain ordinary local mode; label its acceptance scope
├── integration-ci.ps1            # new mandatory local/CI orchestration entry point
└── coverage/
    ├── policy.json               # canonical machine-readable module/metric/workload policy
    ├── capture.ps1               # new run-specific collection and artifact packaging
    ├── verify.ps1                # new trusted deterministic report/decision entry point
    ├── publish.ps1               # trusted immutable archive publisher
    ├── report.ps1                # trusted App-authenticated final check producer
    ├── coverage-common.ps1       # shared parser, counts, compatibility and manifest validation
    ├── test-policy.ps1           # contract fixture harness, no device requirement
    └── fixtures/                 # adversarial, boundary and successful evidence fixtures
.github/workflows/coverage.yml    # unprivileged candidate/device capture
.github/workflows/coverage-report.yml # protected default-branch reporter; never candidate code
.gitconfig                       # pre-push routes to integration check; local bypass is not remote approval
.specify/memory/constitution.md   # authorized governance amendment during implementation
docs/{brd,trd}.md                 # synchronize requirements and evidence meaning
.agents/rules/specify-rules.md    # synchronize metric, gate and regression policy
core/{security,domain,data}/src/  # extend tests in actual configured source sets
feature/{fido2,authenticator}/src/# extend tests only where executable behavior exists
```

**Structure Decision**: Reuse existing source sets and tools. A shared PowerShell utility serves capture, verification and fixture testing. The developer CLI and GitHub required-status interfaces are documented in [contracts/coverage-workflow.md](contracts/coverage-workflow.md); no application API is introduced. Retain historical task names and feature-055 evidence paths as compatibility/records, while new authoritative evidence is run-specific.

## Phase 0 — Research Decisions

[research.md](research.md) resolves the toolchain, actual metrics, inventory, variants, freshness, mandatory remote check, trusted harness, accepted-target bootstrap, evidence archive, risk order and governance synchronization. Research used current repository files and primary tool/platform documentation. Existing October 18 rates are investigation context only; no present deficit ordering is inferred from them.

## Phase 1 — Design and Implementation Sequence

### A. Adopt the policy and establish trustworthy measurement

1. Synchronize the authorized governance amendment and retain the stronger separate business-logic objective. Document 60/60 Tier 1, 50 aggregate, 20 existing Tier 2, 65 branch goal, precise applicability and no-regression semantics.
2. Introduce versioned `policy.json` listing all critical modules, required suite families, approved production variants and generated-class exclusions. Store only raw counted metrics in authoritative results; derive display percentages.
3. Add deterministic XML/manifest/ratio verification with complete input validation. Compare floors by `100 * covered >= floor * total`; compare regression by `candidateCovered * baseTotal >= baseCovered * candidateTotal`, using arbitrary-precision intermediates. Retain explicit instruction/branch counts, including zeros, from the same JaCoCo analysis result before XML serialization. After the XML report is finalized, write canonical `counters.json` bound to its report hash, retained class-inventory digest and analyzer identity; validate all present XML counters against it. An omitted XML counter is normalized to zero only with this producer proof, otherwise missing counters fail. Zero denominators then follow [data-model.md](data-model.md).
4. Make configured source/compiled class inventory authoritative. Persist all five module results, including empty authenticator, and fail on unmapped executable sources/classes. Inventory all repository production modules, not only the selected module gates. Required test data is conditional on executable scope and declared suite obligations; an empty module still needs its inventory evidence.
5. Align app coverage with `shrunkDebug` and matching host/device execution; resolve incompatible class IDs before accepting contribution data. Keep one production denominator per module, exact generated SQLDelight exclusions and explicit unmatched-data failures.
6. Force fresh complete required workloads, preserve structured success/failure/skip/discovery counts, and publish failure evidence even when thresholds fail. No report cache, previous XML or ignored `.exec/.ec` file can establish current-run completeness.

### B. Capture the initial baseline and choose tests

1. Commit the intended measured state before complete capture; require a clean measured tree and record source commit/tree plus a before/after fingerprint. Dirty exploratory outputs may be investigated separately but are never integration evidence.
2. Capture a complete initial baseline on the selected capable Android device. Preserve complete-but-below-floor results as failed compliance evidence. Missing/unsupported required execution is incomplete, not an empty-module exemption.
3. Review fresh missed decisions together with requirement mappings and assertion strength in existing tests. Inventory candidates across all five critical modules, including those already above 60%.
4. Record each finite candidate in `test-selection.md` using the data model: observed gap, requirement, risk impact/likelihood rationale, initial state, expected outcome, forbidden effects, test placement and closure evidence. Rank `(impact, likelihood)` descending; among equal risk ranks choose the larger exact module deficit, then stable candidate ID. Effort or easy percentage gains do not outrank risk.
5. Investigate ownership/consent/decryption/event-integrity/authorization leads in research without claiming defects. Verify legacy-source discovery before duplicating or relocating a test. Missing stable BRD/TRD requirements are allocated through governance, not replaced by feature-local IDs in Tier 1 code.

### C. Remediate behavior in the approved order

1. Extend existing relevant tests or add focused tests at the nearest valid boundary. Neutral decisions belong in `commonTest`; Android cryptography, SQL/driver and framework behavior use configured host/device sources. Empty authenticator receives no artificial product code or tests.
2. Assert success and distinct failure classes, boundary values, typed results, authorization prerequisites, cancellation, ownership/erasure and persistence side effects as applicable. Independent fixtures/expected values must not reuse the production computation being verified.
3. Use bounded deterministic synchronization for race/lifecycle cases. A selected assertion weakness remains a valid risk candidate even when coverage is already 100% for that path.
4. Each batch is an indivisible progression: select the highest-ranked work, write/extend tests, run them, repair any exposed bounded defect, rerun focused tests until successful, and only then mark the behavior verified. Prepare a clean measurement checkpoint under the existing local-CI commit policy, capture fresh coverage, and rerank before the next lower-risk batch. An unresolved failure stops progression; repair and remeasurement are not deferred to end-of-phase tasks.
5. After individual floors are met, separately inspect repository aggregate instructions. If below 50%, extend the finite ledger to requirement-backed behavior in remaining production modules, including Tier 2, and run the same risk/repair/measurement loop until the aggregate also passes. Outstanding higher-risk Tier 1 cases keep priority. Existing affected Tier 2 floors and Tier 1 floors/nonregression remain binding; neither easier percentage gains nor new exclusions can substitute for justified tests. Aim toward 65% branches in remediated Tier 1 modules and report any goal shortfall separately.
6. Production corrections must remain bounded to defects actually exposed by selected tests. Do not add unrelated refactors, broad wrappers, exclusions or test disabling.

### D. Compare revisions and make integration mandatory

1. Implement `tools/integration-ci.ps1` with explicit target/candidate/device/evidence inputs. It invokes trusted non-mutating local checks, complete coverage capture and verification; it accepts no skip flags. A below-floor candidate cannot be overridden by a passing ordinary local run.
2. Reuse a trusted archived baseline for the current accepted target only when compatible. Otherwise measure that exact target in an isolated checkout with the same external harness. Successful test execution plus complete metrics can supply comparison evidence even when the target fails new floors; this does not accept it anew.
3. Derive affected Tier 1 modules from target-to-candidate production/test changes, including shared build/test/policy changes. Compare raw ratios under compatible measurement policies. New tests/code can change counts and inventory; fingerprints must not require identical test names/counts or bytecode across revisions.
4. Split collection from authority. `coverage.yml` performs unprivileged PR/device capture. `coverage-report.yml` is loaded from the protected default branch through `workflow_run`; it checks out only the protected, full-SHA pinned reporter/harness and validates repository/run/head/base/tested-tree identities without executing candidate files or artifact contents. `tools/coverage/report.ps1` alone creates the final `coverage-integration` check using a dedicated GitHub App installation token. Require that check context from the exact App ID, not the GitHub Actions App or any source. Store App credentials only in the `coverage-reporting` environment restricted to the actual protected default-branch ref; PR/merge refs and device workers cannot access them. Record reporter workflow commit, harness SHA, App/installation IDs and accepted-target identities in each decision. A same-named candidate Actions success is rejected by source binding.
5. Bootstrap the reviewed reporter on the protected default branch and install the dedicated App before claiming enforcement. Provision the Windows device worker and serialize device use; configure required-check protection with strict current-target handling and no accepted bypass. The protected reporter also handles integration-target push metadata to invalidate prior App checks for changed bases and request new capture; stale-head/base events cannot publish success. Test a candidate that replaces its workflow with a same-named success-only job: it must remain blocked without the App-authenticated decision. Missing App, environment restrictions, device access, credentials or protection remains an unmet operational prerequisite; no generic-token fallback is permitted.
6. Resolve PR head/base/merge-candidate SHAs and tested tree. Refresh when the target changes. On integration, verify the actual integrated product tree matches the tested tree before promoting its baseline record; if merge metadata changes only the commit SHA, retain both identities and the equal verified tree. A changed tree needs a fresh run.

### E. Retain evidence and close acceptance

1. Emit immutable, schema-validated run bundles for complete and failed attempts, including the exact ten TRD evidence keys. Upload review artifacts even on failure, without replacing the failing check's outcome.
2. Publish required review/accepted-baseline bundles to the protected evidence branch from trusted code only. Verify hashes after transfer; do not overwrite accepted run IDs. Keep active baseline reports and review artifacts accessible for their reference lifetime, with a rebuild required if evidence is unavailable or incompatible.
3. Publish separate completeness, tests, floors, regression, publication and integration decisions. Only all required successful decisions produce the required check's success.
4. Record initial/final metrics, selected behaviors and assertion outcomes, target comparison, device/platform identity, exclusions and unfulfilled 65% goals in feature evidence. Preserve feature-055 historical evidence and unverified performance/device claims.
5. Complete the [quickstart validation matrix](quickstart.md), including rejection cases and the actual repository protection demonstration. Documentary review of this plan cannot satisfy those runtime cases.

## Requirements and Acceptance Mapping

| Requirement group | Design/evidence |
| --- | --- |
| FR-001–FR-005 | Versioned policy, configured inventories, raw counters, exact floors and explicit applicability; empty/branchless/boundary fixtures. |
| FR-006–FR-008 | Initial complete capture, device/host workload identity, fresh reports and compatible unique denominators. |
| FR-009–FR-011 | Risk-ranked finite selection records, behavior assertions, traceable fixtures and final 65% goal reporting. |
| FR-012–FR-013 | Canonical integration command plus actual required GitHub status/protection; blocked missing-device/evidence cases. |
| FR-014–FR-015 | Trusted current-target bootstrap, exact ratio regression, changed-module scope and compatibility/structural mapping cases. |
| FR-016–FR-017 | Immutable ten-field evidence bundles, durable archive, accessible human/machine reports and publication-failure rejection. |
| FR-018–FR-020 | Authorized synchronized amendment, historical evidence preservation, initial/final comparison and honest status separation. |

SC-001/SC-002 require measured module/aggregate floors. SC-003 requires controlled failure cases and verified integration blocking. SC-004 requires actual initial/final complete runs. SC-005 requires selection records and goal reporting. SC-006 requires current-target comparisons. SC-007 requires retrievable artifacts. SC-008 requires a synchronized governance/gate audit.

## Complexity Tracking

| Constraint requiring explicit treatment | Why needed | Simpler alternative rejected because |
| --- | --- | --- |
| Stronger governance acceptance | User-authorized floors and mandatory regression invalidate previous acceptable outcomes. | Leaving authoritative old thresholds or asking again for already-supplied authorization leaves this feature inconsistent. Adopt the scoped amendment first. |
| Required hosted check and device worker | FR-012 requires actual integration blocking with full connected-device evidence. | Local hooks or optional coverage alone cannot control remote integration. External provisioning is an explicit acceptance prerequisite. |
| Protected evidence archive branch | FR-017 requires supporting artifacts while a baseline is referenced. | Disposable build output and finite transfer retention alone do not preserve a long-lived baseline. No additional storage service is required. |
| Shared PowerShell verification utilities | Capture, final assessment and contract fixtures all need the same schema/counter rules. | Duplicating parsing and exact-ratio logic across three consumers invites inconsistent acceptance. |

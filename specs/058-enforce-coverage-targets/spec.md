# Feature Specification: Enforce Coverage Targets

**Feature Branch**: `codex/058-enforce-coverage-targets`

**Created**: 2026-10-24

**Status**: Draft

**Complexity**: L — coverage tooling, behavioral test remediation, and integration/evidence enforcement in the existing modules.

**Input**: User description: "Complete individual coverage gates for all five Tier 1 modules at 60%, maintain a 50% repository aggregate floor, capture a fresh connected Android device baseline, improve meaningful missing branches toward approximately 65%, require coverage before integration, retain verification evidence, and prevent coverage regression in changed Tier 1 modules. Name instruction coverage explicitly."

## Clarifications

### Session 2026-10-24

- Q: Which percentages govern the coverage requirements for this feature? → A: Use the user's latest targets: Tier 1 instruction coverage at least 60% and branch coverage at least 60% where measurable; repository aggregate instruction coverage at least 50%; approximately 65% branch coverage as the remediation goal. The existing Tier 2 instruction floor remains 20%.
- Q: Should higher-risk uncovered behavior take priority across all Tier 1 modules, even when its module already meets 60%? → A: Yes. Prioritize security and data-integrity risk across all Tier 1 modules; use measured coverage deficits to order equally risky cases. Every applicable module must still meet its fixed floors before integration.

## User Scenarios & Testing *(mandatory)*

### Owner decision — 2026-10-26

T046–T049 operational runner/reporter/archive/protection/integration acceptance is waived for this delivery. The owner lacks acceptable disposable Windows and Android physical/virtual execution resources and rejects the exposure of the persistent Windows host and attached Android device to malicious PR code. The runner is disabled with registration retained. This exception also covers the duplicate T089 operational scope and hosted demonstrations in T108. Local coverage/test/ratio/regression/evidence requirements remain binding. The original hosted requirements below remain the design for a future authorized rollout; they are not claimed satisfied by this exception. See `tasks.md` and `implementation-evidence.md` §11.28.

### User Story 1 - Apply Consistent Coverage Requirements (Priority: P1)

As a maintainer, I need every critical module assessed against the same published coverage requirements so an overall repository percentage cannot conceal an under-tested critical module.

**Why this priority**: Reliable acceptance requires consistent thresholds and complete module accounting before additional tests can demonstrate compliance.

**Independent Test**: Assess controlled coverage results above, below, and exactly at each floor and verify that the acceptance decision identifies every failing module and metric independently.

**Acceptance Scenarios**:

1. **Given** Tier 1 modules with measurable production code, **When** coverage is assessed, **Then** each module must independently meet at least 60% instruction coverage and 60% branch coverage, and repository aggregate instruction coverage must meet at least 50%.
2. **Given** repository aggregate coverage above 50% and one critical module below either 60% floor, **When** acceptance is evaluated, **Then** the module failure blocks acceptance and names the failing metric.
3. **Given** `core:data` has production code, **When** coverage is assessed, **Then** it receives an individual Tier 1 result alongside `core:security`, `core:domain`, `feature:fido2`, and `feature:authenticator`.
4. **Given** an empty Tier 1 module, **When** coverage is assessed, **Then** its entry records a verified lack of executable production code as not applicable; adding executable code makes its coverage and test execution mandatory on the next assessment.
5. **Given** policy and executable gates contain different coverage thresholds, **When** the feature is accepted, **Then** the constitution states the adopted percentages, and the business and technical requirements, development rules, executable gates, and current verification instructions agree on the adopted floors and define their measurement meanings consistently.

---

### User Story 2 - Establish a Trustworthy Current Baseline (Priority: P1)

As a contributor, I need a fresh measurement across all critical modules and the full repository so I can see the actual shortfalls and prioritize work using evidence from the current revision.

**Why this priority**: Historical coverage and passing test counts cannot establish compliance after code, scope, or thresholds change.

**Independent Test**: Execute the complete host and connected Android device verification for one identified revision and inspect the resulting module inventory, test results, coverage totals, and recorded limitations.

**Acceptance Scenarios**:

1. **Given** a connected Android device and the complete verification workload, **When** the baseline is captured, **Then** all five Tier 1 modules have an explicit measured or justified not-applicable disposition, and the repository aggregate is recorded separately.
2. **Given** data from an earlier revision or run, **When** a new baseline is captured, **Then** earlier data cannot contribute to its coverage claim; each compiled production class is counted once in its scope.
3. **Given** a disconnected device, failed suite, missing report, empty execution data, or unexecuted required suite, **When** assessment finishes, **Then** the run reports failed or incomplete verification and cannot establish a passing baseline.
4. **Given** different device capabilities, **When** an exclusion or unsupported procedure affects execution, **Then** the affected procedure, reason, coverage limitation, and any required alternative evidence are visible in the baseline.

---

### User Story 3 - Improve Meaningful Branch Coverage (Priority: P2)

As a contributor, I need the baseline to guide tests for uncovered decisions so the additional coverage verifies behavior that protects users and provides margin above the acceptance floors.

**Why this priority**: Validation, boundary handling, cancellation, cleanup, and recovery decisions need behavioral assertions; increasing executed code alone does not demonstrate correctness.

**Independent Test**: Compare candidates with different risks across modules above and below their floors, verify that higher-risk behavior is selected first and equal-risk candidates are ordered by measured coverage deficits, then add tests that distinguish the selected behaviors' expected outcomes and compare the resulting coverage with the initial baseline.

**Acceptance Scenarios**:

1. **Given** uncovered domain decisions, **When** tests are improved, **Then** the selected cases cover applicable invalid inputs, boundary values, cancellation, cleanup, and absence of forbidden side effects, with assertions of expected results.
2. **Given** higher-risk uncovered behavior in a Tier 1 module already above its floors and lower-risk uncovered behavior in another module below a floor, **When** remediation is prioritized, **Then** the higher-risk behavior is selected first, and both modules must meet their applicable floors before integration.
3. **Given** a remediated module, **When** results are reviewed, **Then** progress toward approximately 65% branch coverage is reported separately from compliance with the hard 60% floor.
4. **Given** an apparent improvement caused by skipped tests, unjustified exclusions, weakened assertions, reduced scope, or altered counting rules, **When** the improvement is reviewed, **Then** it cannot establish compliance.
5. **Given** equally risky test candidates, **When** the work order is selected, **Then** measured module coverage deficits determine their relative priority, with the selection reason recorded.
6. **Given** a selected behavior, **When** a test is created or an existing test is extended, **Then** its requirement, risk rationale, input or initial state, expected outcome, and applicable forbidden side effects are recorded, and the assertions distinguish correct behavior from the identified failure.
7. **Given** individual module floors are met but repository aggregate instruction coverage remains below 50%, **When** remediation continues, **Then** justified behavior-test candidates from the remaining production modules are added and ranked until the aggregate floor is met, while retaining priority for higher-risk outstanding Tier 1 behavior.
8. **Given** a selected test exposes a defect, **When** its batch is processed, **Then** the defect is repaired and the focused tests pass before the behavior is marked verified; fresh measurement and reranking occur before advancing to lower-risk work.

---

### User Story 4 - Block Integration Without Complete Coverage Evidence (Priority: P1)

As an integrator, I need complete coverage verification to be mandatory for integration so ordinary checks or omitted optional coverage cannot admit a change that breaches the policy.

**Why this priority**: A published floor protects the repository only when the integration decision always evaluates it.

**Independent Test**: Attempt integration with passing ordinary checks but missing, stale, failed, or below-floor coverage evidence, then repeat with complete passing evidence for the same revision.

**Acceptance Scenarios**:

1. **Given** ordinary local checks pass but complete coverage verification has not run for the proposed revision, **When** integration is attempted, **Then** integration is blocked with an actionable missing-evidence reason.
2. **Given** complete passing results for the proposed revision, **When** integration is evaluated, **Then** coverage acceptance is recorded with accessible supporting reports.
3. **Given** a verified revision followed by any additional code change, **When** integration is attempted, **Then** the earlier result cannot establish coverage acceptance for the changed revision.
4. **Given** the required device is unavailable, **When** integration verification is requested, **Then** the unmet prerequisite is reported and the coverage requirement remains blocking.
5. **Given** a candidate changes its workflow to emit a same-named successful coverage check without verification, **When** integration is attempted, **Then** that check cannot satisfy the required acceptance source and integration remains blocked.

---

### User Story 5 - Preserve Evidence and Prevent Regression (Priority: P2)

As a maintainer, I need comparable coverage history and accessible reports so I can audit acceptance and prevent changed critical modules from losing coverage while remaining above their minimum floors.

**Why this priority**: Fixed floors permit gradual loss of existing coverage unless changes are also compared with a trustworthy accepted baseline.

**Independent Test**: Compare a changed Tier 1 module with its accepted integration-target baseline, including a decrease that stays above 60%, and verify that the regression is detected and explained.

**Acceptance Scenarios**:

1. **Given** a changed Tier 1 module at 70% instruction and 68% branch coverage in the accepted baseline, **When** its proposed result is 69% instruction and 68% branch coverage, **Then** integration is blocked for instruction coverage regression even though both floors are met.
2. **Given** unchanged or increased coverage in each comparable metric and all fixed floors met, **When** a changed critical module is assessed, **Then** its coverage regression check passes.
3. **Given** missing or incomparable baseline evidence, **When** regression is assessed, **Then** the system requires a comparable baseline measurement and cannot report a regression pass until it exists.
4. **Given** a successful accepted run, **When** a reviewer follows its evidence links, **Then** the revision, environment, test counts, coverage counts and percentages, exclusions, and acceptance decision are retrievable.

### Edge Cases

- A result exactly at 60% or 50% meets its floor; rounding a lower result to the displayed threshold must not turn failure into success.
- A module contains executable instructions but has no measurable branches: branch coverage is not applicable only with an evidenced zero branch denominator; instruction and execution requirements remain applicable.
- A complete exporter omits a counter because its total is zero: accept an explicit normalized zero only when analysis of the same retained compiled scope proves it; an absent counter without that proof remains missing evidence.
- An empty module acquires production code, including generated code retained by the approved measurement policy: its previous not-applicable disposition cannot be reused.
- A module contains production code in a configured source location omitted by the coverage inventory: the accounting defect blocks acceptance rather than treating that module as empty.
- Host and device execution exercise the same production class, or different compiled variants exist: matching evidence must be combined without duplicate denominators or incompatible class data.
- A partial run leaves reports behind before a rerun fails: report presence alone cannot establish a complete current result.
- An integration target changes after the regression comparison: a new comparison against the current accepted target is required.
- A module is renamed, split, or merged: its comparison must preserve the previous production scope or establish a reviewed comparable replacement baseline before acceptance.
- A measurement tool, generated-code exclusion, source inventory, or device procedure changes: baseline and proposed results must use equivalent counting and execution policies before comparison.
- A required platform procedure is unsupported: the limitation is recorded explicitly and any uncovered acceptance obligation remains unresolved until supported evidence exists.
- A report has zero executed required tests, failed tests, or skipped required tests: percentage values cannot override the missing successful execution.
- A genuine instrumentation limitation affects a terminal exception path: any exception must meet the existing documented evidence policy and cannot create a blanket exemption from module floors or regression checks.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Coverage assessment MUST inventory all five Tier 1 modules: `core:security`, `core:domain`, `core:data`, `feature:fido2`, and `feature:authenticator`, with a separate result or evidenced not-applicable disposition for each.
- **FR-002**: Each Tier 1 module with executable production code MUST independently meet instruction coverage of at least 60% and, where branches exist, branch coverage of at least 60%. Repository aggregate instruction coverage MUST be at least 50%. These are the authoritative coverage floors for this feature. Aggregate results MUST NOT replace individual module acceptance.
- **FR-003**: Instruction coverage MUST mean covered executable instructions divided by total covered plus missed instructions. Branch coverage MUST use the corresponding covered and missed branch counts. Policy and reports MUST name both metrics explicitly; any reference to instruction coverage as a statement coverage measure MUST identify it as a proxy, not a distinct measured statement metric. If the exporter omits a zero-total counter, an explicit normalized zero MUST be supported by analysis of the same retained compiled scope and linked report/inventory evidence; omission alone MUST NOT establish zero or not-applicable coverage.
- **FR-004**: Acceptance MUST compare the underlying counts at full precision. Display rounding MUST NOT affect floor or regression decisions. Existing Tier 2 requirements remain at least 20% instruction coverage, with branches reported separately where currently required.
- **FR-005**: A not-applicable module disposition MUST be supported by an inventory of its configured production scope showing no executable production code. It MUST be reevaluated on every complete run. A zero branch denominator permits only a branch-specific not-applicable result and MUST NOT exempt the module's other obligations.
- **FR-006**: Before test remediation, contributors MUST capture an initial baseline from a complete run of the required host and connected Android device suites for one identified revision. Below-floor results MUST be retained as a failed compliance baseline rather than discarded or relabeled as passing.
- **FR-007**: A run MUST demonstrate discovery and execution of the required suites and attribute its execution data to the assessed revision and run. Missing, stale, empty, mismatched, failed, or incomplete evidence MUST prevent a passing result.
- **FR-008**: Module reports MUST cover their full approved production scope; the repository aggregate MUST cover all production modules under the same counting policy. Execution evidence MUST be combined without counting the same production classes more than once within each report.
- **FR-009**: Remediation MUST identify uncovered or insufficiently asserted required behavior using fresh coverage reports, requirements, and existing tests, then prioritize security and data-integrity risk across all five Tier 1 modules regardless of whether a module already meets its floors. Each candidate's risk rationale MUST consider the impact and likelihood of incorrect behavior, including credential exposure, unauthorized access, data corruption, persistence failure, cancellation, and secret cleanup where applicable. Measured module coverage deficits MUST determine the relative priority of equally risky cases. Domain's historical shortfall MUST NOT impose a fixed module order ahead of higher-risk behavior elsewhere. For each selected behavior, contributors MUST record its requirement, risk rationale, measured coverage gap or assertion weakness, input or initial state, expected result, and applicable forbidden side effects; they MAY extend an existing test when it verifies the same behavior. Tests MUST assert expected behavior, typed failures or outcomes, cleanup, and absence of forbidden side effects as applicable. Within each batch, any exposed defect MUST be repaired and focused tests rerun successfully before marking its behavior verified; fresh measurement and reranking MUST precede lower-risk work. If repository aggregate coverage remains below 50% after individual floors are met, remediation MUST extend the candidate inventory to justified behavior in the remaining production modules while preserving priority for higher-risk outstanding Tier 1 behavior. Every applicable module and the repository aggregate MUST still meet their fixed floors before integration.
- **FR-010**: Test improvement MUST target approximately 65% branch coverage in domain and other Tier 1 modules receiving branch remediation. This is an improvement goal recorded in the final evidence; the binding Tier 1 integration floor remains 60%, and unmet improvement goals MUST be reported explicitly.
- **FR-011**: Contributors MUST NOT establish compliance by disabling or skipping required tests, weakening assertions, adding unjustified exclusions, shrinking production scope, lowering thresholds, or changing denominators to hide deficits. Existing narrowly documented instrumentation exceptions require their existing evidence and review conditions.
- **FR-012**: Integration acceptance MUST require a complete successful coverage run for the exact proposed revision, independent of whether a contributor explicitly selected optional coverage during ordinary local checks. The final acceptance producer and its credentials MUST be controlled outside the candidate; a candidate workflow emitting a same-named successful check MUST NOT satisfy the required acceptance source. A passing ordinary local run MUST NOT be presented as complete integration coverage verification.
- **FR-013**: Missing devices, failed or unsupported required procedures, missing reports, unmet floors, and unresolved comparison baselines MUST block integration with the affected scope and corrective prerequisite identified. Historical device exclusions MUST be reevaluated against the current environment and MUST NOT silently transfer to a new run.
- **FR-014**: For every Tier 1 module whose production or test scope changes, neither its instruction coverage percentage nor its applicable branch coverage percentage MAY decrease relative to the current accepted integration-target baseline. Both the fixed floors and this regression rule MUST pass. A previously empty module becoming executable MUST establish its first accepted baseline while meeting the fixed floors.
- **FR-015**: Regression comparisons MUST identify both revisions and use equivalent production boundaries, metric definitions, exclusions, compiled variant accounting, and required test/device workloads. Missing or incompatible evidence MUST require a comparable baseline measurement before acceptance. Structural changes MUST provide an explicit mapping or reviewed replacement baseline that preserves meaningful coverage accountability.
- **FR-016**: Every assessed run MUST retain the project's ten evidence fields: requirement, scope, revision, named procedure, environment, expected and observed result, date, limitations, status, and artifact link. Coverage evidence MUST additionally expose covered and missed counts, percentages, discovered/executed/failed/skipped test counts, module inventory, device identity and platform version, measurement version and exclusions, comparison baseline revision, and the per-rule acceptance decisions.
- **FR-017**: Reviewers MUST be able to retrieve human-readable and machine-readable coverage summaries and supporting test reports throughout integration review and for as long as a result is referenced as an accepted regression baseline. A summary without accessible supporting artifacts MUST NOT establish acceptance.
- **FR-018**: The constitution's Principle V MUST state the adopted Tier 1 coverage floor of 60%, Tier 2 floor of 20%, and repository aggregate floor of 50% in its concise existing wording. The BRD, TRD, development rules, executable gates, and current verification guidance MUST define the detailed JaCoCo instruction and branch metrics, branch applicability, approximately 65% branch remediation goal, mandatory integration check, and nonregression policy. These documents MUST be mutually consistent and MUST NOT assign the detailed operational requirements to the constitution when it delegates them to the development rules. The constitution amendment and version history MUST follow its governance procedure using the user's authorized target changes.
- **FR-019**: Existing work on the thresholds and expanded module gates MUST be assessed against these requirements as partial implementation; its presence MUST NOT be treated as proof of successful execution. Dated historical evidence MUST retain the thresholds, revision, environment, exclusions, and outcomes that actually applied to that run.
- **FR-020**: Completion evidence MUST include the initial baseline, the final complete run after remediation, test-selection records demonstrating risk priority and coverage-deficit ordering among equally risky candidates, the measured change per applicable Tier 1 metric, the repository aggregate, and all unresolved limitations or improvement goals. Documentary approval, test success, coverage success, and actual integration acceptance MUST remain distinguishable.

### Key Entities *(include if feature involves data)*

- **Coverage Policy**: The module classification, covered production scope, metric definitions, fixed floors, applicability rules, exclusions, and regression comparison rules used for an assessment.
- **Verification Run**: An identified revision and execution environment with discovered suites, actual execution outcomes, connected-device details, coverage data, and completeness status.
- **Module Coverage Result**: One module's covered and missed instruction and branch counts, test counts, applicability disposition, threshold decisions, and explanatory limitations.
- **Accepted Comparison Baseline**: Coverage evidence for the current accepted integration target, including the production scope and measurement policy required to compare a proposed change fairly.
- **Integration Coverage Decision**: The proposed revision's fixed-floor, completeness, and regression outcomes, with controlled acceptance-producer identity, tested revision bindings, blocking reasons and links to retained supporting reports.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every complete assessment accounts for all five critical modules; 100% of applicable Tier 1 module results independently meet both 60% floors before integration, and every not-applicable metric has supporting denominator or scope evidence.
- **SC-002**: Repository aggregate instruction coverage is at least 50% in the final complete verification run and every accepted integration assessment thereafter. Meeting individual module floors while remaining below 50% MUST trigger aggregate-deficit remediation and MUST NOT establish acceptance.
- **SC-003**: Controlled acceptance checks reject 100% of below-floor, regressing, missing, stale, mismatched, empty, failed, or incomplete required evidence cases, including cases where ordinary checks and repository aggregate coverage pass. A candidate-generated same-named success check cannot satisfy the required trusted source; producer-proven zero totals remain distinguishable from missing evidence.
- **SC-004**: An initial and a final complete baseline are recorded for identified revisions, each accounting for the five critical modules, repository aggregate, actual required test execution, connected-device environment, and limitations.
- **SC-005**: Every new or extended behavioral test has a selection record identifying its requirement, risk rationale, measured gap or assertion weakness, initial conditions, expected result, and applicable forbidden side effects. Selection records demonstrate that higher-risk behavior is prioritized across modules regardless of floor status and that measured deficits order equally risky cases. Final evidence reports branch coverage progress against the approximately 65% improvement goal for every remediated Tier 1 module, alongside the decisions demonstrating compliance with the hard 60% Tier 1 floors; no improvement-goal shortfall is concealed.
- **SC-006**: Every changed critical module accepted for integration maintains or increases each comparable coverage percentage relative to its accepted target baseline while satisfying all fixed floors.
- **SC-007**: Every accepted revision has retrievable coverage and test artifacts, all ten required evidence fields, raw metric counts, test counts, exclusions, baseline comparison identity, and explicit acceptance decisions.
- **SC-008**: The governing policy documents and executable acceptance rules contain zero conflicting active thresholds, measurement definitions, module classifications, or integration/regression obligations within this feature's scope.

## Assumptions

- The user-authorized targets are Tier 1 instruction coverage at least 60%, Tier 1 branch coverage at least 60% where measurable, repository aggregate instruction coverage at least 50%, and existing Tier 2 instruction requirements at least 20%. Approximately 65% is a branch improvement goal rather than a second integration floor.
- The existing JaCoCo instruction counter is the requested measured coverage quantity; instruction coverage is a proxy for the previously named statement measure, not literal source statement coverage. Branch and instruction percentages remain separate metrics.
- `feature:authenticator` currently has no executable production sources. The fresh inventory must verify this status; absence of code is not a coverage pass and must not prevent automatic assessment once code is introduced.
- Historical domain branch coverage of 49.10% and repository aggregate instruction coverage of 52.08% from the October 18 evidence are planning context only. They do not establish the new baseline or current compliance.
- A connected Android device and the existing required host/device procedures are prerequisites for complete verification. No device capability, excluded procedure, or physical-transport result is inferred from a host test result.
- The accepted integration target supplies the comparison revision for regression checks. Comparison uses the same measurement policy and workload; if no comparable result exists, measuring that target is part of this feature rather than grounds to waive the check.
- Coverage evidence must use synthetic test data and follow the existing evidence and secret-handling policies. Existing narrow terminal-exception instrumentation rules remain applicable without expanding their scope.
- Scope covers coverage policy synchronization, complete measurement and gating, meaningful test remediation, mandatory integration acceptance, regression prevention, and evidence retention. It does not introduce unrelated product features or broaden existing performance and device-certification claims.
- Governance synchronization includes the authorized strengthening of Principle V and related requirements. This specification records required work; it does not itself amend the constitution, execute the baseline, verify current gate changes, or authorize deployment.

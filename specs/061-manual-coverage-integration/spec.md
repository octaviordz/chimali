# Feature Specification: Manual Coverage Review for Integration

**Feature Branch**: `061-manual-coverage-integration`

**Created**: 2026-10-29

**Status**: Draft

**Input**: Establish a main-branch integration workflow with manual coverage evidence review, optional local coverage checks, and a durable evidence archive.

## Clarifications

### Session 2026-10-30

- Q: Must an existing assessment be compared again or its baseline recalculated solely because `main` advances? → A: No. Retain the original assessment and its baseline; verify again if the source tree to be integrated differs from the tested tree.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Review coverage before integration (Priority: P1)

As a user or agent authorized to accept a pull request, I need a specific, trustworthy coverage report for the proposed change so I can decide whether it is ready to enter `main`.

**Why this priority**: Coverage is an integration requirement and the review is the authority for accepting evidence.

**Independent Test**: Present a pull request with a linked report and baseline, then review valid, missing, failed, stale and mismatched evidence against the acceptance criteria.

**Acceptance Scenarios**:

1. **Given** a pull request into `main` with complete passing evidence for the tree to be integrated and the accepted baseline used when it was assessed, **When** the authorized reviewer validates it, **Then** the reviewer can authorize integration.
2. **Given** missing, inaccessible, failed, incomplete, incompatible, untrusted or otherwise invalid evidence, **When** the pull request is reviewed, **Then** the reviewer rejects it until sufficient evidence is provided. An older assessment baseline alone does not invalidate evidence for an unchanged tested tree.
3. **Given** that `main` advances after measurement, **When** the tree proposed for integration still matches the tested tree, **Then** the original comparison remains usable without recalculating its baseline or comparing again solely because `main` advanced.
4. **Given** that the tree proposed for integration differs from the tested tree, **When** integration is considered, **Then** evidence for the changed tree is required before acceptance.

### User Story 2 - Work on feature branches without mandatory coverage (Priority: P1)

As a developer, I want normal feature-branch pushes to remain available without mandatory coverage, while being able to run a complete assessment manually for early feedback.

**Why this priority**: Developers can share work promptly and still prepare the evidence needed for integration.

**Independent Test**: Push a feature branch without local coverage configuration and separately request a coverage assessment against a configured target.

**Acceptance Scenarios**:

1. **Given** a push that does not update the locally configured coverage target, **When** the developer pushes, **Then** coverage verification is not required by the local coverage hook.
2. **Given** a developer has configured a target and requests a manual assessment, **When** the assessment completes, **Then** it identifies the candidate, target, outcome and evidence for review.
3. **Given** no coverage target is configured, **When** a feature branch is pushed, **Then** the local coverage hook does not block it for missing coverage configuration.
4. **Given** a developer is new to the repository, **When** they read the project README, **Then** they can identify the main steps for optional local coverage, supplying PR evidence and obtaining review, and find the detailed procedure.

### User Story 3 - Check pushes to a configured target (Priority: P2)

As a developer, I want a local check when I push to my configured coverage target so I receive feedback before that target is updated.

**Why this priority**: The check supports the manual integration policy for direct target pushes.

**Independent Test**: Configure a target, push matching and nonmatching refs using controlled updates, and inspect the verification outcome.

**Acceptance Scenarios**:

1. **Given** `coverage.targetRef` identifies a remote target, **When** a push updates that exact target, **Then** the proposed update is assessed against the target before the push proceeds.
2. **Given** a push includes multiple updates, **When** at least one updates the configured target, **Then** each matching update is assessed and unrelated updates are not treated as target updates.
3. **Given** a local merge, **When** no push occurs, **Then** this coverage hook does not run.

### User Story 4 - Preserve a usable baseline and archive (Priority: P1)

As a maintainer, I need durable coverage evidence and an accepted baseline for `main` so future reviews compare compatible results.

**Why this priority**: Review decisions depend on retrievable evidence and a trustworthy comparison.

**Independent Test**: Establish the initial baseline, retrieve an archived report from a PR link, and validate baseline promotion after integration.

**Acceptance Scenarios**:

1. **Given** `main` is created from `lab/or/main`, **When** the first PR is considered, **Then** valid baseline evidence matches the exact creation commit and applicable policy; otherwise a new baseline is captured before acceptance.
2. **Given** a verified change is integrated using a merge commit, **When** the integrated tree matches the tested tree, **Then** its verified evidence can be promoted as the accepted baseline.
3. **Given** archived evidence is missing, corrupted or inconsistent with its recorded identity, **When** it is reviewed, **Then** it cannot establish a passing assessment.

### Edge Cases

- A push updates several refs, the configured target on a different remote, or deletes a ref without a candidate commit.
- A local merge, a feature-branch push, or a GitHub PR merge occurs without a local target-push hook executing.
- No device, multiple devices, an unauthorized or unsuitable device, or an interrupted measurement prevents a complete run.
- The target advances, the candidate changes, or conflict resolution changes the source tree after measurement.
- Evidence is inaccessible, incomplete, altered, from a different candidate or baseline, or uses an incompatible policy or measurement scope.
- No accepted initial baseline exists; a prior archived run is synthetic, historical or lacks the required identity.
- The archive branch is renamed while links, protections, existing evidence or publishing references still use its old name.
- `octaviordz` uses the GitHub rules bypass for `main`.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The primary `main` branch MUST be created from the explicitly identified existing `lab/or/main` commit. Record the creation commit as the initial baseline identity.
- **FR-002**: GitHub rules MUST require a pull request for changes to `main`, with only `octaviordz` configured to bypass those rules. No separate bypass exception record is required.
- **FR-003**: PR acceptance into `main` MUST be a manual decision by an authorized user or an agent acting for that user. Reviewer and publisher authority MUST be established from authenticated repository access and trusted configuration outside candidate-controlled content; a supplied name alone MUST NOT establish authority. The reviewer MUST reject a PR when required coverage evidence is missing, insufficient, failed, incomplete, inaccessible, incompatible or untrusted. A baseline becoming older solely because `main` advances does not by itself make evidence invalid for an unchanged tested tree.
- **FR-004**: The developer MUST provide a PR link to the specific coverage report in `swe/coverage-evidence`, identifying the candidate revision, tested tree, target revision, required test and coverage outcomes, comparison, policy and limitations.
- **FR-005**: Coverage assessment MUST preserve the applicable constitution and governing coverage floors, required test and device scope, exact comparisons, trusted verification and strict nonregression against the accepted baseline used for that assessment. A local ordinary CI pass or documentary approval alone MUST NOT be represented as a complete coverage pass.
- **FR-006**: Before the first PR into `main` is accepted, the accepted baseline MUST match the exact `lab/or/main` commit used to create `main` and the applicable policy and measurement scope. If valid matching evidence is unavailable, a new baseline MUST be captured.
- **FR-007**: A reviewer MUST require evidence for the tree proposed for integration and the accepted baseline used when that tree was assessed. An advance of `main` alone MUST NOT require recalculation of that baseline or a repeat comparison. If the proposed tree differs from the tested tree, renewed verification for the changed tree MUST precede acceptance. This policy does not establish nonregression against a newer baseline that was not compared.
- **FR-008**: PRs into `main` MUST use merge commits. After integration, the integrated tree MUST be verified against the tested tree before verified evidence is promoted as the new accepted baseline; mismatch or unavailable evidence prevents promotion.
- **FR-009**: Feature-branch pushes MUST NOT require coverage by default. A developer MAY request an explicit local assessment of a feature revision against `coverage.targetRef` before opening a PR.
- **FR-010**: Configuring `coverage.targetRef` MUST enable local coverage verification when a push updates that exact target on the intended remote. With no configured target the coverage hook MUST return without parsing update records or requiring coverage prerequisites. With a configured target, malformed records MUST be rejected; valid pushes that do not update that target MUST NOT require coverage prerequisites. Local merges MUST NOT trigger this hook.
- **FR-011**: For a multi-ref push, the hook MUST identify every update matching the configured target and require a successful assessment for each proposed target update. A deletion without a candidate MUST NOT be reported as a passing coverage assessment. Nonmatching refs MUST remain distinguishable.
- **FR-012**: Setup and verification MUST provide actionable diagnostics for unavailable or ambiguous prerequisites. Routine device and evidence-location discovery SHOULD be automated when unambiguous; the selected device MUST be authorized and capable of the required procedures, and the selected target and trusted harness MUST be verified rather than silently guessed.
- **FR-013**: Verification MUST use a reviewed, clean, identifiable trusted harness and a separately maintained accepted baseline; candidate-controlled changes MUST NOT redefine the authority for its own acceptance. Evidence MUST identify its source, target, policy, scope and limitations, and remain retrievable and integrity-checkable.
- **FR-014**: Migrate the archive to canonical branch `swe/coverage-evidence`, preserving historical contents and their Git identities. Verify read-back and protection on the new ref before removing the old `codex/coverage-evidence` branch. Preserve legacy receipt identities and do not represent historical or synthetic records as accepted baselines.
- **FR-015**: Local checks MUST NOT be represented as GitHub-enforced merge protection. The hosted coverage worker and required-check rollout remain subject to the existing owner waiver until separately authorized and verified. Existing automated CI coverage gates remain in force.
- **FR-016**: Reconcile the affected Feature 058 requirements and current operational documentation with this manual PR acceptance policy, the archive rename and existing governance; preserve historical observations, unresolved hosted limitations and coverage requirements.
- **FR-017**: Create or update the project-root README with high-level developer guidance for the coverage and integration workflow. It MUST describe feature-branch pushes, optional local assessment and `coverage.targetRef`, target-push checks, PR evidence and manual review, merge-commit and baseline handling, and links to the detailed procedures. Guidance MUST accurately distinguish implemented behavior from planned or unavailable capabilities.

### Key Entities

- **Integration target**: The remote branch selected for local push assessment; `main` is the primary PR integration branch.
- **Candidate**: The proposed source revision and tree whose tests and coverage are assessed.
- **Accepted baseline**: Verified, compatible evidence maintained for an integrated target revision. Each assessment identifies and retains the accepted baseline selected at assessment time; a later baseline does not replace that assessment's comparison.
- **Coverage report**: Retained evidence for a particular candidate, target, run, outcome and measurement scope.
- **Review decision**: An authorized user or agent's manual acceptance or rejection of evidence for a PR.
- **Trusted harness**: A reviewed, identifiable verification source independent of candidate-controlled acceptance authority.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In all reviewed PR scenarios, missing, failing, incomplete, incompatible or tree-mismatched evidence results in rejection; a valid matching report is available for an authorized decision. A `main` advance alone does not invalidate the original assessment.
- **SC-002**: All nonmatching feature-branch push scenarios proceed without a mandatory local coverage assessment, while every matching configured target update requires a passing local assessment.
- **SC-003**: The initial accepted baseline identifies the exact `main` creation commit and applicable policy before the first PR is accepted.
- **SC-004**: Every accepted PR has a retrievable report link and records the candidate, baseline and outcome; promotion occurs only after the integrated and tested trees match.
- **SC-005**: All preserved historical archive records remain retrievable after the rename, and new report links use `swe/coverage-evidence`.
- **SC-006**: A developer can start an optional local coverage assessment against the configured target and receive either a complete decision with evidence or an explicit incomplete/failure outcome.
- **SC-007**: A developer can locate the project README and follow its high-level workflow to find the relevant detailed instructions for local assessment, PR evidence and integration review.

## Assumptions

- `lab/or/main` and the remote archive remain available for the planned migration; the exact commit and archive identities are recorded at execution time.
- The reviewer has authority and access to inspect the linked report and trusted baseline. Manual review does not claim an active hosted required coverage check.
- The existing coverage policy, applicable CI gates and authorized hosted rollout waiver remain governing constraints until explicitly amended through the project's governance process.
- Feature 058's baseline comparison requirements and the governing BRD, TRD and engineering rules have been reconciled with FR-007. Local entry points and operational documentation are implemented. Active rules protect `main` and `swe/coverage-evidence`; the former `codex/coverage-evidence` branch was removed after its history was verified in the new archive. The exact-main initial baseline and the passing merged candidate are published; the latter is the accepted baseline. Hosted coverage enforcement remains unverified and waived, as recorded in implementation evidence.
- Renaming branches, configuring GitHub rules, capturing baselines and publishing evidence are delivery activities; this specification alone does not perform them.

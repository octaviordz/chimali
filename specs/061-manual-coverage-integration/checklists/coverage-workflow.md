# Coverage Workflow Requirements Checklist: Manual Coverage Review for Integration

**Purpose**: Assess whether the requirements support reliable manual coverage review, target-push checks and evidence preservation.
**Created**: 2026-10-29
**Feature**: [spec.md](../spec.md)

**Review Ownership**: This custom checklist belongs to the reviewer. Mark `[x]` only after deciding that the requirements-quality criterion is satisfied; it does not indicate implementation completion.
**Review context**: Standard-depth review before task generation, focused on manual acceptance evidence, local target routing and archive/baseline integrity.

## Requirement Completeness

- [x] CHK001 Are the distinct responsibilities of the developer, authorized reviewer and trusted evidence publisher defined throughout the PR acceptance lifecycle? [Completeness, Spec §FR-003–004, Spec §FR-013, Contract §Manual assessment and PR]
- [x] CHK002 Are the required report contents sufficient to identify the candidate, comparison baseline, policy, required test and coverage outcomes, limitations and evidence integrity? [Completeness, Spec §FR-004–005, Spec §FR-013]
- [x] CHK003 Are requirements defined for establishing and approving the initial baseline for the exact commit used to create `main`? [Completeness, Spec §FR-001, Spec §FR-006, Spec §SC-003]
- [x] CHK004 Are the conditions and responsible authority for promoting an integrated change to the accepted baseline specified? [Completeness, Spec §FR-008, Contract §Archive and baseline]
- [x] CHK005 Are the required contents and audience of the project README defined sufficiently to guide a developer from a feature branch through PR review? [Completeness, Spec §FR-017, Spec §US2]

## Requirement Clarity

- [x] CHK006 Is the meaning and accepted form of `coverage.targetRef` precise enough to identify both the intended remote and exact destination branch? [Clarity, Spec §FR-010, Contract §Local pre-push]
- [x] CHK007 Are the conditions for a target push to proceed, fail or remain incomplete distinguished clearly enough for a developer to interpret the outcome? [Clarity, Spec §FR-010–012, Contract §Local pre-push]
- [x] CHK008 Are the criteria for treating a harness as reviewed, identifiable, clean and independent of candidate-controlled approval defined? [Clarity, Spec §FR-013, Plan §Phase 1 — Design and Delivery Sequence]
- [x] CHK009 Are the criteria for an authorized user or agent to accept evidence, including the authority and provenance of the review decision, stated clearly? [Clarity, Spec §FR-003, Contract §Manual assessment and PR]
- [x] CHK010 Is the meaning of a matching tested and integrated tree, and its relationship to candidate and merge commit identities, defined unambiguously? [Clarity, Spec §FR-007–008, Contract §Archive and baseline]
- [x] CHK011 Is the scope of the `octaviordz` rules bypass specified clearly, including the absence of a separate exception-recording requirement? [Clarity, Spec §FR-002, Spec §Edge Cases]

## Requirement Consistency

- [x] CHK012 Are feature-branch pushes consistently described as free of mandatory local coverage while explicit manual feature assessments remain available? [Consistency, Spec §FR-009–010, Spec §SC-002/006]
- [x] CHK013 Are target-push checks, local merge behavior and GitHub PR acceptance clearly distinguished across the specification, plan and contract? [Consistency, Spec §FR-003/010/015, Contract §Local pre-push, Contract §Manual assessment and PR]
- [x] CHK014 Are the requirements for empty or nonmatching pushes consistent with the requirements for malformed input and ambiguous configured targets? [Consistency, Spec §FR-010–011, Contract §Local pre-push]
- [x] CHK015 Are manual PR acceptance and the hosted-runner waiver reconciled with Feature 058 and applicable coverage authority without implying that hosted enforcement has passed? [Consistency, Spec §FR-015–016, Plan §Constitution Check]
- [x] CHK016 Are the instructions for accepting a baseline compatible with the distinction between historical, synthetic and newly verified archive records? [Consistency, Spec §FR-006/014, Contract §Archive and baseline]

## Acceptance Criteria Quality

- [x] CHK017 Can a reviewer objectively determine whether missing, failed, incomplete, stale or mismatched evidence requires rejection? [Measurability, Spec §FR-003/007, Spec §SC-001]
- [x] CHK018 Can the matching and nonmatching target-push requirements be evaluated for exact remote, branch and candidate identity? [Measurability, Spec §FR-010–011, Spec §SC-002]
- [x] CHK019 Are the conditions for validating the initial baseline and accepting the first PR measurable against the creation commit and applicable policy? [Measurability, Spec §FR-001/006, Spec §SC-003]
- [x] CHK020 Can preservation of historical evidence and use of the renamed archive for new reports be evaluated without altering the meaning of historical receipts? [Measurability, Spec §FR-014, Spec §SC-005, Contract §Archive and baseline]

## Scenario and Edge Case Coverage

- [x] CHK021 Are requirements complete for an unconfigured target, a nonmatching push, a matching target push and a local merge without a push? [Coverage, Spec §FR-009–010, Spec §US2–US3]
- [x] CHK022 Are multi-ref pushes covered, including independent identification of matching updates and the consequence for the entire push when a matching assessment fails? [Coverage, Spec §FR-011, Contract §Local pre-push]
- [x] CHK023 Are target deletion, a new remote branch and an update whose proposed commit differs from the current checkout addressed with explicit outcomes? [Coverage, Spec §Edge Cases, Contract §Local pre-push, Gap]
- [x] CHK024 Are missing, multiple, unauthorized or unsuitable devices and unavailable evidence or harness prerequisites addressed without presenting an unexecuted assessment as passing? [Exception Flow, Spec §FR-012, Spec §Edge Cases]
- [x] CHK025 Are target advances, candidate changes and conflict resolution addressed both before approval and after integration but before baseline promotion? [Recovery, Spec §FR-007–008, Contract §Manual assessment and PR]
- [x] CHK026 Are failure, retry and recovery requirements defined for interrupted archive migration, unavailable publication, read-back failure and conflicting concurrent updates? [Recovery, Spec §FR-014, Contract §Archive and baseline, Gap]

## Non-Functional Requirements and Dependencies

- [x] CHK027 Are trust boundaries specified for candidate-controlled material, the reviewed harness, evidence publication and the manual reviewer? [Security, Spec §FR-013, Contract §Manual assessment and PR]
- [x] CHK028 Are artifact integrity, retention and accessibility requirements defined for as long as evidence or a baseline is referenced? [Reliability, Spec §FR-013–014, Contract §Archive and baseline]
- [x] CHK029 Are the existing coverage floors, required suites, compatible scope and nonregression requirements traceable to their governing authority? [Traceability, Spec §FR-005/015–016, Plan §Constitution Check]
- [x] CHK030 Are dependencies on GitHub permissions, branch protection, a capable device and a reviewed harness identified together with the outcome when each is unavailable? [Dependency, Spec §FR-002/012–014, Plan §Technical Context]
- [x] CHK031 Are manual PR approval records specified to retain sufficient identity and timing for later review? [Auditability, Spec §FR-003–004, Data Model §Archive and review record]

## Ambiguities and Conflicts

- [x] CHK032 Is the relationship between PR evidence approval, merge-commit creation and post-merge baseline promotion clear when the target changes concurrently? [Ambiguity, Spec §FR-007–008, Contract §Manual assessment and PR]
- [x] CHK033 Is the boundary between the user-authorized manual process and any separately authorized future hosted enforcement explicitly stated? [Clarity, Spec §FR-015–016, Plan §Constitution Check]
- [x] CHK034 Are the preservation and recovery expectations for the old archive and existing links defined if the rename or protection update only partially succeeds? [Completeness, Spec §FR-014, Plan §Phase 1 — Design and Delivery Sequence, Gap]

## Notes

- Mark items `[x]` only after reviewing the requirement-quality criterion; add findings or comments alongside the item when useful.
- `/speckit-implement` reads checklist state as a gate and does not modify custom checklist markers.
- `checklists/requirements.md` is the separate built-in specification-quality checklist.

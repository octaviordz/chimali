# Implementation Plan: Verifiable Security and Quality Contracts

**Branch**: `codex/057-improve-verification-contracts` | **Date**: 2026-10-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification and its accepted documentation-only scope clarification.

## Summary

Prepare a concise constitution amendment and synchronize BRD, TRD, engineering rules, and two canonical security registers for the five backlog areas. Completion is documentary review and traceability, not application compliance. No application, build, CI, or test code changes; no test execution, benchmarking, device audit, deployment, or new security acceptance.

The proposed amendment adds one verification-policy bullet to Principle V, with a provisional MINOR version change from 4.0.0 to 4.1.0. Exact wording, rationale, and per-area constitutional dispositions are in [constitution-amendment.md](constitution-amendment.md). It remains a proposal pending engineering approval. Planning creates reviewable artifacts without changing the live constitution or supporting authorities.

Keep existing security, latency, rendering, coverage, and accessibility obligations intact. Clarify validation outcomes; replace the TRD's universal `contentDescription` rule; document separate integration paths; preserve existing rendering thresholds while requiring complete future measurement contracts. Consolidate existing vault/local-key review evidence without fresh source or device audits.

## Technical Context

**Language/Version**: Markdown and existing document metadata; constitution baseline 4.0.0. No language/toolchain upgrade.

**Primary Dependencies**: Current constitution, BRD, TRD, engineering rules, revised backlog, and dated feature-053/055 review records. No added software dependency.

**Storage**: Version-controlled Markdown; no database schema, serialization, or application-state change.

**Testing**: Document review, local-link checks, requirement/disposition mapping, whitespace checks, and change-scope inspection only. No unit, integration, UI, benchmark, or device test execution.

**Target Platform**: Repository documentation consumed by engineering/security/release reviewers. Android journeys are future policy subjects, not execution targets for this feature.

**Project Type**: Internal governance/documentation change in an existing Android/KMP repository.

**Performance Goals**: Preserve BRD `NFR-PERF-020`: consistent 60 FPS, zero frozen frames (>700 ms), and <1% slow frames (>16 ms). Preserve existing latency/startup/size/memory budgets. Define future measurement-contract fields; do not select devices or claim measured compliance here.

**Constraints**: Approved documentation-only scope; governance approval before adopting amendments; stable IDs and authority precedence; no new security exemptions; no broadened historical review dispositions; no changed deferrals; no commit-hook or CI policy bypass.

**Scale/Scope**: M. Five policy areas, one proposed constitutional bullet, three existing supporting authorities, two new registers, and feature review/follow-up records. No new module or service.

## Constitution Check

*Initial gate: PASS for documentation-only planning. Post-design gate: PASS for task generation. Adoption of the proposed amendment remains subject to the existing governance procedure, not inferred from this plan.*

| Principle / rule | Initial check | Post-design disposition |
| --- | --- | --- |
| I — cryptographic and memory security | Registers must not become exemptions. | Classification/boundary records preserve mutable ownership, secret prohibitions, dated evidence, and unresolved limitations. |
| II — current deterministic profile | No derivation change requested. | Retain profile/clean-install scope and exact-source review limits; historical evidence is linked without reapproval. |
| III — architecture | No code or dependency-flow change. | Validation guidance changes policy only; no new wrapper or abstraction. |
| IV — append-only history | Partial-failure policy must preserve committed history. | Future integration matrices describe event/projection results without requiring deletion of history. |
| V — rigor and traceability | Evidence specificity is a governance gap. | One proposed verification-policy bullet; future details in TRD/rules; coverage floors and source annotations unchanged. |
| VI — vitals and budgets | Existing thresholds must not be weakened. | Retain 60-FPS and BRD slow/frozen-frame limits; future metrics must justify measurement against those obligations. |
| VII — conciseness, IDs, YAGNI | Avoid a general testing platform and duplicated procedures. | One concise constitutional bullet, existing supporting sections, Markdown records; stable IDs retained. |
| VIII — accessibility and transport | No new transport, UI redesign, or accessibility rollout. | Correct documentation semantics, define future verification, preserve `FR-UI-011` deferral. |
| Governance — approval/version/synchronization | Planning does not enact an amendment. | Draft exact wording and provisional 4.1.0 rationale; record approval before live adoption. |
| Engineering workflows | User excludes code/test execution. | Document checks only, no commit in this workflow; existing pre-commit/local-CI obligations remain unchanged. |

No unexplained constitutional violation or implementation exception is introduced. A pending amendment is a recorded governance decision, not an unresolved design question.

## Project Structure

### Documentation (this feature)

```text
specs/057-improve-verification-contracts/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── constitution-amendment.md
├── quickstart.md
├── contracts/documentation-review.md
└── checklists/requirements.md
```

`tasks.md` is generated by `/speckit-tasks`, not this command. Implementation will add `documentation-review.md` and `follow-ups.md` in this feature directory for actual review decisions and excluded work; the contract here defines their contents without fabricating completed review.

### Source Code (repository root)

No source-code directory is modified. The future documentation edit set is:

```text
.specify/memory/constitution.md                  # Adopt approved amendment only
.agents/rules/specify-rules.md                  # Validation/testing/evidence guidance
docs/brd.md                                    # Product obligations and references
docs/trd.md                                    # Technical verification procedures
docs/security/data-classification.md            # New canonical classifications
docs/security/platform-text-boundaries.md       # New canonical usage records
docs/backlog/constitution-testability-improvements.md # Per-item documentary status/follow-ups
specs/057-improve-verification-contracts/         # Review and follow-up records
```

**Structure Decision**: Use existing authorities and two requested registers. No public API or runtime interface is added. `contracts/documentation-review.md` describes the human-facing document/review interface, not a software endpoint.

## Phase 0 — Research Decisions

[research.md](research.md) resolves scope, amendment placement/versioning, synchronization anchors, validation policy tension, classification provenance, existing rendering thresholds, accessibility correction, integration evidence levels, and future-work reporting.

The notable baseline findings are the universal description rule in TRD §6, the exception/validation tension in rules §5.2, the broad integration language in BRD §9/rules §1, and existing rendering bounds in BRD `NFR-PERF-020`. They require targeted synchronized edits, not a runtime audit.

## Phase 1 — Design and Implementation Sequence

### A. Amendment and authority mapping — User Story 1

1. Review the exact proposed Principle V addition and five-area mapping in [constitution-amendment.md](constitution-amendment.md), preserving the other principles and current Principle V bullets. US1 delivers proposed wording, rationale/version impact, planned supporting edits, and an explicit approval state.
2. Accept US1 only as a proposal-readiness milestone. Detailed supporting edits are delivered by US2–US5; the synchronized package is not yet approved or adopted at this checkpoint.
3. After all stories, prepare and validate the complete concrete synchronized package under T031–T033. Obtain and record the actual engineering reviewer, date, exact package revision, and disposition under T034 before any adoption.
4. On affirmative approval, adopt under T035 and revalidate under T036; otherwise report the package as pending/rejected with adoption open. Use the actual adoption date, retain the ratification date, and reassess the version if review changes semantics or weakens/removes obligations.

### B. Validation and canonical registers — User Story 2

1. Replace both conflicting rules §5.2 paragraphs together: expected external input uses `Outcome.Failure` or documented protocol mapping; `require`/`check` assert internal preconditions/state and must not leak ordinary external rejection across boundaries.
2. Add constraint-class, boundary-value, side-effect, and owned-cleanup future verification obligations in rules §1/§5.2. Define UI/protocol/import/reconstruction coverage; avoid duplicate validation requirements.
3. Link data classifications from BRD security obligations and TRD §5.1/rules §5.5. Create register rows using [data-model.md](data-model.md), recording documentary scope and evidence gaps.
4. Consolidate feature-053 vault ownership/handoff and feature-055 security-review records with original dates and source scopes. Follow links to existing documentary audits as needed; do not inspect production code to certify a new current-source audit.

### C. Integration policy — User Story 3

1. Replace broad integration wording in BRD §9 and rules §1 with explicit minimum scope and references to TRD §9, retaining `FR-PASS-010` and HID requirements.
2. Add two distinct journey matrices, with six failure categories each and applicability rationale where needed, using the [review contract](contracts/documentation-review.md).
3. Define future evidence metadata, allowed substitutes, host/device distinctions, restart, response verification, cleanup, and partial persistence expectations. Record missing execution as follow-up rather than a prerequisite for document acceptance.

### D. Accessibility policy — User Story 4

1. Correct the existing TRD §6 universal `contentDescription` statement, preserving its unrelated 48dp touch-target and high-legibility obligations.
2. Document accessible names/roles/actions/states/grouping, visible-label support, decorative elements, four representative task categories, and future screen-reader procedure.
3. Keep BRD `FR-UI-011` contrast/scaling obligation and explicit deferral. Technical criteria must distinguish text categories and applicable ratios from non-text requirements; do not invent a blanket ratio for every visual element. Any standards detail added during implementation must cite its authoritative source.

### E. Rendering policy — User Story 5

1. Preserve BRD `NFR-PERF-020` values verbatim. Add minimum journeys and a technical-procedure reference without changing the general obligation.
2. Add TRD measurement-contract fields and rules for missing workload, unsupported metrics, review responsibility, and evidence levels. Existing metric/CPU-duration/frame-deadline differences must remain explicit.
3. Assign the performance verification owner responsibility for completing concrete measurement parameters; require engineering review before a later run can support compliance. Do not select hardware, thresholds, or run benchmarks in this feature.

### F. Document validation and closure

1. Apply [quickstart.md](quickstart.md): review all 20 FRs and seven SCs against exact changed sections, links, rows, and dispositions.
2. Populate the review/follow-up records using the contract. Preserve `FR-UI-011`, feature-055 T084/T085/partial SC-007, device limitations, and fingerprint-scoped security acceptance.
3. Update backlog statuses to distinguish policy delivered, implementation deferred, and execution unverified. Do not mark the full runtime backlog complete because documentation changed.
4. Inspect the diff for allowlisted documentation only; report document checks without claiming tests ran. No commit or governance adoption is performed by `/speckit-plan`.

## Requirements and Acceptance Mapping

| Spec obligations | Planned deliverables | Documentary acceptance |
| --- | --- | --- |
| FR-001, FR-002, FR-019; SC-001 | Amendment proposal, authority mapping, sync-impact/review record | Five dispositions; exact edits/version rationale; explicit approval status; docs-only scope |
| FR-003–FR-005; SC-002 | Rules §1/§5.2, TRD verification guidance | Consistent expected failures/internal assertions, constraint categories, no required execution |
| FR-006–FR-008; SC-003 | Two registers, TRD/rules/BRD references | Required fields and historical provenance; gaps explicit; no new exemption |
| FR-009–FR-011; SC-004 | BRD §9, TRD §9, rules §1 | Two complete lifecycle matrices; 12 failure-category applicability decisions |
| FR-012–FR-014; SC-005 | TRD §6/§9, rules §1, BRD UI references | Four task categories and future assessment criteria; deferral retained |
| FR-015–FR-017; SC-006 | BRD performance reference, TRD verification, rules §1 | Two minimum journeys, complete future contract fields, existing budgets retained |
| FR-018, FR-020; SC-007 | Evidence schema, review/follow-up records, backlog dispositions | No new runtime claims or expanded approvals; all excluded work traceable |

## Complexity Tracking

No constitutional violations require an exception. Pending engineering approval controls adoption of the proposed amendment; it does not prevent preparing the synchronized package or generating tasks.

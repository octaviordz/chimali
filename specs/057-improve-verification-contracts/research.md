# Research: Constitution and Supporting Documentation Contracts

**Date**: 2026-10-23 | **Scope**: Documentary research only | **Status**: Planning decisions resolved

## 1. Delivery boundary

**Decision**: Deliver constitution/supporting-document changes, canonical registers, and review/follow-up records only. Review is the acceptance method; runtime tests are future policy subjects.

**Rationale**: The accepted clarification explicitly excludes code and test execution. The earlier broad verification interpretation is superseded by the current specification.

**Alternatives considered**: Implementing benchmark fixes, running device checks, or closing product defects would recreate the rejected scope. Merely updating the backlog would fail to deliver governing documents.

## 2. Constitutional placement and semantic version

**Decision**: Propose one Principle V verification-policy bullet and provisional 4.1.0 MINOR amendment. Retain existing security, architecture, budgets, and accessibility principles; provide five-area dispositions in the amendment package.

**Rationale**: Constitution 4.0.0 intentionally relocated implementation detail. Explicit scope/outcome/evidence obligations add normative guidance without removing existing protections, fitting Governance §2 MINOR. Exact adoption requires engineering approval under Governance §1.

**Alternatives considered**: PATCH understates added obligations; MAJOR would be warranted only if review materially redefines/removes principles. Adding tools/screens/test procedures to every principle reverses the conciseness refactor. No constitution edit would omit the requested amendment focus.

## 3. Authority and synchronization

**Decision**: Keep precedence Constitution → BRD → TRD → rules → feature artifacts. Retain stable IDs and existing sections; add descriptive verification subsections under TRD §9 and links from relevant sections. No new stable ID is needed merely to repeat an existing obligation; genuinely new IDs must first be checked for uniqueness.

**Rationale**: Existing governing references cover the five subjects. Constitution Governance §3 requires synchronized BRD/TRD/rules changes. Descriptive headings avoid fabricated source traceability.

**Alternatives considered**: Moving all detail into a new standalone policy authority would obscure precedence; renumbering existing IDs would break traceability.

## 4. Validation policy tension

**Decision**: Reconcile rules §5.2 domain-error and input-validation paragraphs together. Expected invalid external input uses typed/domain or protocol failure; internal assertions remain possible and external boundary handling must contain ordinary rejection.

**Rationale**: The current text reserves exceptions exclusively for unrecoverable failures but also directs aggressive `require`/`check`. Updating just one line leaves a contradiction. Rules §3.5 already requires boundary encapsulation.

**Alternatives considered**: Requiring exceptions everywhere conflicts with domain outcomes; prohibiting all assertions unnecessarily changes internal invariant policy. One negative test per use case is weaker than constraint-class coverage and unsuitable for parameterless operations.

## 5. Security register provenance

**Decision**: Consolidate existing documentary classifications and platform usages into the two requested canonical files. Missing fields/evidence become explicit findings, not invented approvals.

**Rationale**: The [vault ownership changelog](../../docs/changelogs/2026-09-09-vault-secret-ownership-remediation.md), [vault handoff](../053-vault-completion/handoff.md), and [local-HDK security review](../055-migrate-hdk-draft/security-review.md) contain scoped historical evidence, limitations, and superseding checkpoints. Their dates/revisions must survive consolidation.

**Alternatives considered**: A new source/device audit is outside scope; labeling all historical observations current would extend assurance. A blank register schema alone would not satisfy consolidation.

## 6. Existing rendering limits

**Decision**: Retain the complete BRD `NFR-PERF-020` obligation: 60 FPS, zero frozen frames (>700 ms), <1% slow frames (>16 ms). Require future metric/method/statistic/aggregation review rather than inventing replacement thresholds.

**Rationale**: These values already exist. The real gap is repeatable workload and measurement interpretation, not an absence of every numeric limit. A CPU-duration metric is not automatically evidence of missed display deadlines.

**Alternatives considered**: New percentile limits would add unnecessary normative scope; treating the two journeys as the only obligated screens narrows the constitution. Selecting a concrete device or repairing/executing benchmark code belongs in follow-up work.

## 7. Accessibility correction

**Decision**: Correct TRD §6's current blanket description rule. Define future accessible name/role/action/state/grouping checks, supported-theme contrast criteria, scaling through 200%, and screen-reader walkthrough requirements while preserving the BRD deferral.

**Rationale**: The earlier proposal's problematic text also exists in the current technical authority. Visible labels and decorative elements require different treatment. Preserve the unrelated 48dp touch-target/high-legibility rules. Detailed standards ratios, if added, require primary-source attribution during drafting; no new ratio is selected in this plan.

**Alternatives considered**: Only changing the backlog leaves a live contradiction. Conducting a TalkBack/device audit or requiring a full UI redesign violates the accepted scope.

## 8. Integration and evidence status

**Decision**: Document two distinct entry-path lifecycle matrices and six failure-category applicability decisions per path. Define future evidence metadata and passed/failed/deferred/unverified semantics without requiring runs here.

**Rationale**: BRD §9 and rules §1 currently say “comprehensive”; TRD §4.2/§4.3 identifies different transport paths. Host substitutions cannot establish real transport execution. Feature-055 evidence preserves append-first/projection-second failure semantics and exact-source review scope.

**Alternatives considered**: One combined HID/Credential Manager sequence is a false integration model. Closing runtime backlog from document review conflates governance completion with compliance.

## 9. Review and authorization lifecycle

**Decision**: Prepare exact, synchronized edits before requesting any governance disposition. Record reviewer/date/package revision and accepted/rejected/pending status. The planning invocation does not adopt an amendment, commit, or run CI.

**Rationale**: The user has authorized planning; engineering adoption is a distinct decision required by the constitution. Existing optional commit hooks are not a mandate. The docs-only constraint does not silently waive pre-commit/local-CI policies for a future commit.

**Alternatives considered**: Asking approval before drafting a concrete package is premature; automatic live adoption during planning is outside this command. Hook bypass is unnecessary because no commit is performed.

## Resolution

All design choices needed for task generation are resolved. Concrete future measurement parameters, runtime coverage, and device availability are intentionally assigned to separate verification work. Engineering approval is a documented adoption gate, not a missing design assumption.

The BRD §9 100%-core-business-logic coverage statement differs from current constitutional/rules tier floors. Record this pre-existing discrepancy as follow-up; this feature does not silently alter coverage policy. Historical feature plans and changelogs retain their original constitutional numbering and assurance scope.

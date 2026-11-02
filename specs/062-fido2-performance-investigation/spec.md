# Feature Specification: FIDO2 Internal Performance Investigation

**Feature Branch**: `codex/062-fido2-performance-investigation`

**Created**: 2026-10-30

**Status**: Draft

**Input**: Investigate the deferred FIDO2 internal performance lead in the updated backlog, and optimize only where the evidence supports doing so.

## Clarifications

### Session 2026-10-30

- Q: May empirical calibration and sensitivity replace the hard worst-case timing-bound prerequisite? → A: Yes, owner approved. Measure clock alignment and instrumentation overhead, predeclare an empirical allowance and sensitivity analysis, keep uncertainty-dependent conclusions open, and limit conclusions to the measured device/workload.

- Q: If the production settings lookup cannot be measured or the results are inconclusive, should this investigation be considered complete? → A: Keep the investigation open until the lookup can be assessed conclusively; record attempts and blockers.
- Q: What should happen to the FIDO2 internal performance backlog item when the investigation and implementation are complete? → A: Delete it at completion, retaining the supporting evidence and reconciling references.

### Session 2026-11-01

- Q: How should warm sampling proceed after repeated failures at the eighth counted warm attempt? → A: Owner approved a prospective cap of seven counted warm attempts per process after one uncounted warmup. Collect the ten eligible warm intervals across separately identified warm-process sessions when needed; predeclare and review any pooling before new collection. Preserve failed attempts and the ten-eligible-sample requirement. This changes sampling logistics only; it does not infer a universal failure threshold or cause, and it does not authorize a particular device run.

### Session 2026-11-02

- Q: How should the investigation close when its valid before/after samples show a lower warm median, but the 20 ms absolute group rule remains inconclusive and the cold lead remains material? → A: Owner authorized an evidence-based requirements reassessment. Preserve the contract's absolute classification as inconclusive; report the disjoint sensitivity-expanded warm sample-median intervals as a descriptive sampled effect only, without population or causation claims. Close the comparative task with that bounded conclusion, and transfer the unresolved cold-path optimization lead to a separate follow-up backlog item. Keep the warm registration reliability investigation separate. This amendment does not change Feature 060 acceptance or claim that the 20 ms rule passed.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Understand the registration delay (Priority: P1)

The product owner needs a reliable explanation of whether the settings lookup contributes materially to observed registration latency, so that a change can be justified by evidence.

**Why this priority**: A single historical gap suggests a lead but does not establish its cause.

**Independent Test**: Review an investigation record that directly measures the production lookup, identifies the workload and limitations, and states whether the evidence supports optimization.

**Acceptance Scenarios**:

1. **Given** the historical registration trace, **When** investigators measure the settings lookup in a representative production path, **Then** the record separates its elapsed time from adjacent stages and describes scheduling and measurement limitations.
2. **Given** inconclusive or unavailable measurements, **When** the investigation is reported, **Then** it identifies the missing evidence and blockers, does not claim attribution or improvement, and remains open.
3. **Given** an investigated lead that is not material, **When** the evidence is reviewed, **Then** the record explains that conclusion and does not require a speculative optimization.

### User Story 2 - Improve a confirmed bottleneck safely (Priority: P2)

The user needs any justified improvement to reduce unnecessary work without changing credential behavior, stored settings, or security protections.

**Why this priority**: An optimization is useful only if it preserves reliable and secure authentication.

**Independent Test**: For each change, compare supported settings and credential behavior before and after, and compare equivalent measured workloads with all outcomes retained.

**Acceptance Scenarios**:

1. **Given** evidence of a material bottleneck, **When** an improvement is applied, **Then** its effect is measured under comparable conditions and supported behavior remains intact.
2. **Given** existing settings or a supported upgrade, **When** registration reads the credential limit, **Then** the correct limit and unrelated settings are preserved. Legacy migration may be removed only after establishing that the clean-install scope applies to those settings.
3. **Given** denial, failure or cancellation, **When** an operation ends, **Then** authorization, persistence ordering and sensitive-data cleanup retain their existing guarantees.

### User Story 3 - Review performance and close the investigation (Priority: P3)

The product owner needs a traceable decision with honest verification results, including how any changes affect the current latency acceptance assessment.

**Why this priority**: Reproducible evidence enables a sound decision and prevents simulated and physical results from being conflated.

**Independent Test**: Review the final report, linked measurements, behavior checks and backlog disposition for complete, correctly scoped outcomes.

**Acceptance Scenarios**:

1. **Given** relevant production changes, **When** the existing host-simulated assessment is rerun, **Then** every implemented operation is assessed using its existing strict per-occurrence budget and exclusions, with failures and incomplete results retained.
2. **Given** device diagnostics, **When** results are reported, **Then** they are identified separately and do not become a physical acceptance gate.
3. **Given** the completed investigation, **When** the owner reviews its disposition, **Then** T085 and current documentation reflect the evidence, limitations and any remaining work.
4. **Given** conclusive investigation and completed applicable implementation and verification, **When** the work is closed, **Then** the backlog item is deleted after its supporting evidence is preserved and its references are reconciled.

### Edge Cases

- The historical trace is unavailable, incomplete, or insufficient to attribute the delay.
- Cold starts, warm runs, scheduling pauses, failures, interrupted attempts and outliers differ materially.
- A legacy setting is present, missing, invalid, or conflicts with an existing current setting.
- A credential limit is reached or settings cannot be read; cancellation or persistence failure occurs during registration.
- An optimization shifts work to another operation or changes behavior without reducing comparable elapsed time.
- A measurement uses simulated dependencies or omits an operation; it cannot establish production-device timing or complete acceptance for the omitted operation.
- If the investigation or required implementation and verification remain incomplete, the backlog item remains in place.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The investigation MUST directly measure the production registration settings lookup and adjacent work, with boundaries sufficient to distinguish the lookup from the previously observed gap.
- **FR-002**: The investigation MUST document workload, device and build identity, timing method and boundaries, sample eligibility, cold and warm conditions, failures, outliers and limitations. Warm sampling MUST allow no more than seven counted warm attempts in one process after its labeled uncounted warmup; the ten eligible warm intervals may be gathered across separately identified process sessions under a prospectively declared comparison. Measurements MUST use synthetic data and exclude secrets from retained evidence.
- **FR-003**: Before collection, the investigation MUST declare its sample policy and the comparison used to judge whether the lookup materially contributes to delay. Any pooling across warm sessions or runs MUST be predeclared with identity/equivalence review before new collection. It MUST distinguish elapsed lookup duration from its causes using available scheduling and suspension evidence; if uncertainty could change the absolute 20 ms classification, that classification remains inconclusive. It MUST record the evidence and uncertainty. An owner-approved bounded comparative disposition may report a sampled before/after median effect when the predeclared sensitivity intervals are disjoint at both scales, while retaining an inconclusive absolute classification, making no population or causal claim, and transferring unresolved leads to explicit follow-up work. This disposition does not convert inconclusive absolute evidence into a pass.
- **FR-004**: Any performance change MUST address a measured, attributed bottleneck and have a comparable before-and-after assessment. Other suspected stages MUST be investigated before they are changed for performance.
- **FR-005**: If evidence does not justify a change, the feature MUST record a no-change disposition and its supporting evidence; it MUST NOT claim an improvement.
- **FR-006**: Any change MUST preserve existing supported settings, defaults, unrelated preferences and credential-limit enforcement. Removal of legacy migration requires confirmation that the applicable clean-install scope permits it; otherwise supported migration behavior MUST remain.
- **FR-007**: Any change MUST preserve authorization, persistence ordering, cancellation and failure behavior, sensitive-data cleanup, zero derivations during candidate listing, and exactly one selected credential derivation for a successful assertion.
- **FR-008**: Configured behavior checks MUST cover affected settings and limit behavior, including applicable migration, failure and boundary cases, and the security-sensitive invariants affected by the change.
- **FR-009**: After relevant changes, the existing host-simulated assessment MUST assess every implemented internal operation using the existing strict <200ms per-occurrence threshold and measured exclusions. Results MUST retain failures and incomplete evidence; device diagnostics MUST be reported separately and MUST NOT be represented as the project acceptance method.
- **FR-010**: The final record MUST distinguish historical, simulated and production-path diagnostic evidence, describe comparable results and limitations, and reconcile T085 and current documentation without changing the completed Feature 060 SC-007 disposition beyond its declared scope.
- **FR-011**: After the investigation's approved bounded disposition, applicable implementation and verification are complete, and every unresolved lead has an accessible follow-up record, the feature MUST delete `docs/backlog/fido2-internal-performance-enhancements.md`, preserve its supporting historical evidence in durable feature records, and reconcile links and current-status references to the deleted file. The disposition MUST state any inconclusive absolute classification and must not imply it passed. Until those conditions and the pre-deletion audit are satisfied, the backlog item MUST remain.

### Key Entities

- **Investigation record**: The question, measurement conditions, observations, attribution, uncertainties and decision for a suspected bottleneck.
- **Measurement occurrence**: An identified operation or stage, its timing boundaries, outcome, eligibility and environment.
- **Optimization decision**: The evidence supporting a change or no-change conclusion, affected behavior, verification and remaining risks.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Completion requires an explicit, evidence-backed disposition of the settings lookup based on direct production-path measurement. A conclusive absolute classification follows the 20 ms rule at both scales. Alternatively, an owner-approved bounded comparative disposition may close the comparison when ten eligible before and after samples have disjoint sensitivity-expanded median intervals at both scales; it MUST retain the absolute inconclusive status, make no population or causal claim, and identify open leads in follow-up records. All attempts and limitations are recorded.
- **SC-002**: Every implemented optimization has a comparable before-and-after record and passing checks for all affected supported behaviors; no improvement is claimed if comparison is inconclusive.
- **SC-003**: After relevant changes, every implemented operation has the required eligible host-simulated measurements and each meets the existing strict <200ms budget, or the assessment explicitly records failure or incompleteness.
- **SC-004**: The final report identifies all investigated leads and changes, their dispositions, supporting evidence and remaining follow-ups, and reconciles T085. At completion the original backlog item is absent, unresolved leads have accessible follow-up records, its supporting evidence remains accessible, and no dangling links or stale current-status references to it remain.

## Assumptions

- Feature 060's host-simulated assessment remains the authoritative project latency acceptance method; its Android workload is diagnostic. Physical measurements in this feature are also diagnostic.
- The earlier physical trace is historical evidence, not proof of causation or a current performance distribution. A new representative production-path capture may be needed.
- Feature 055's clean-install scope does not by itself prove that all unrelated settings migration may be removed. Preserve supported data until that scope is established for the specific setting.
- Existing authorization, persistence, cryptographic and privacy requirements continue to apply. This feature does not introduce a new physical latency threshold or restore the superseded T084 campaign.
- If a suitable device or trace is unavailable, record the limitation and leave the affected investigation unresolved rather than inventing a passing result.

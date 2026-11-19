# Feature Specification: Secret Cleanup Remediation and Verification

**Feature Branch**: `codex/065-security-secret-cleanup`

**Created**: 2026-11-13

**Status**: Clarified — implementation design and task list complete; implementation and release acceptance pending

**Input**: User-approved follow-through on `FUP-SEC-001`: inventory current secret and identifying-data usages, remediate confirmed cleanup and logging defects, verify clipboard lifecycle and memory cleanup with synthetic data, and record platform limits with scoped evidence.

## Clarifications

### Session 2026-11-13

- Q: What clipboard cleanup promise should Chimali make when Android prevents the app from running or clearing the clipboard? → A: Schedule clearing after 30 seconds on a best-effort basis; verify removal where possible and explicitly handle interrupted, rejected or unobservable cleanup without claiming success. The owner authorizes revising NFR-SEC-050 and related wording to this scoped promise.

- Q: What should automatic cleanup do if Chimali cannot confirm that its password is still the current clipboard entry? → A: Require a readable ownership check before automatic clearing; skip clearing if the entry is different or unreadable and record a replaced or unknown outcome. The owner accepts the unavoidable race between checking and clearing; explicit user-requested clearing remains separate.

- Q: Which clipboard cleanup delay should Chimali use? → A: 30 seconds, superseding the earlier 60-second selection. Retain best-effort clearing and ownership-check/skip rules. Every sensitive Android clipboard transfer must also be marked sensitive before publication in line with OWASP and Android secure clipboard guidance.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - End a sensitive interaction safely (Priority: P1)

As a vault or passkey user, I need the application to release and erase the sensitive data it owns when an interaction finishes, fails, is cancelled, or is abandoned, so that my credentials and account associations do not remain in application memory beyond their purpose.

**Why this priority**: Retained passwords, recovery material, or account identifiers can expose users after the original interaction has ended.

**Independent Test**: Exercise one complete synthetic vault interaction and one passkey interaction through each terminal outcome; inspect the owners and runtime retention after termination, with provider and operating-system copies identified separately.

**Acceptance Scenarios**:

1. **Given** a sensitive operation owns plaintext, **When** it succeeds, fails, or is cancelled, **Then** every application-owned sensitive mutable buffer for that operation is erased, including partially constructed values, and borrowed data is not erased before its owner ends its use.
2. **Given** sensitive data is displayed or edited, **When** the user hides it, dismisses the interaction, navigates away, or the owning session terminates, **Then** application-owned buffers are erased and any permitted platform text references are released without being retained in application state or diagnostics.
3. **Given** identifying RP or user account information crosses a request, projection, or display boundary, **When** its owning operation ends, **Then** its representation and cleanup follow the canonical classification and any remaining application-owned immutable sensitive retention is recorded as an unresolved defect.
4. **Given** a sensitive operation is cancelled before its body starts, **When** its transferred inputs lose their final owner, **Then** those inputs are erased without performing the cancelled operation.

---

### User Story 2 - Copy a password with truthful cleanup behavior (Priority: P1)

As a vault user, I need the app to schedule clipboard clearing after 30 seconds on a best-effort basis and describe the limitations honestly, so that I understand when my copied password may remain available.

**Why this priority**: The partial audit found a process-dependent timer and an ignored clear result; cleanup behavior and user-facing wording must reflect Android execution and observation limits.

**Independent Test**: Copy a synthetic password on physical Android hardware, measure its clipboard availability against the original copy time, and repeat with backgrounding, process termination, replacement, and a failed clear attempt.

**Acceptance Scenarios**:

1. **Given** a copy succeeds at time T, **When** no later copy replaces it, **Then** the app schedules best-effort clearing for T + 30 seconds, marks the outgoing sensitive entry before publication to request preview suppression on supporting Android versions, and distinguishes a cleanup attempt from verified removal.
2. **Given** a copy succeeds, **When** backgrounding, lock/sleep, process termination or platform refusal prevents scheduled cleanup or its observation, **Then** the limitation and actual result are recorded without claiming that the clipboard was cleared. A demonstrated, correctly handled platform limitation may satisfy the scoped contract; an unexecuted check remains Unverified.
3. **Given** the user copies another password before the first scheduled clearing time, **When** cleanup runs, **Then** the latest copy receives its own 30-second schedule and an earlier cleanup does not erase it prematurely.
4. **Given** the clipboard rejects a copy or clear, **When** the failure occurs, **Then** it is handled as a failure, the application does not claim that cleanup completed, and any available failure information excludes clipboard contents and identifying account data.
5. **Given** the user explicitly requests clipboard clearing, **When** the application can execute the request, **Then** it attempts to clear the current entry independently of automatic ownership checks, handles platform rejection without claiming removal, and prevents superseded app timers from later clearing a new app copy.
6. **Given** automatic cleanup becomes eligible, **When** a readable ownership check identifies the current app copy, **Then** clearing may be attempted; if the check identifies a different entry or cannot be performed, automatic clearing is skipped with a replaced or unknown outcome. The unavoidable race between checking and clearing is explicitly accepted, so perfect preservation of concurrent external replacements is not promised.

---

### User Story 3 - Diagnose failures without exposing account data (Priority: P1)

As a user, I need operational diagnostics to exclude my credentials and account associations, including when an operation fails, so that logs and crash records do not become another source of disclosure.

**Why this priority**: The partial assessment found four identifier-bearing error messages; generic redaction alone did not establish their safety.

**Independent Test**: Trigger synthetic failure paths with recognizable passwords, recovery material, RP/user identifiers, credential identifiers and lookup tokens, then inspect the actual configured diagnostic outputs for those values and their relevant encoded forms.

**Acceptance Scenarios**:

1. **Given** an operation handles sensitive or identifying inputs, **When** it emits diagnostics or reports an error, **Then** messages, tags, exception details and attached data exclude prohibited values while retaining non-sensitive operation and outcome information.
2. **Given** a prior finding identifies an unsafe log message, **When** remediation is assessed, **Then** evidence covers the corrected emission path and configured output rather than inferring safety from unrelated functional tests.
3. **Given** a source usage appears unreachable, **When** its disposition is recorded, **Then** reachability evidence and the chosen remediation are explicit; an absence of callers in a simple search alone does not certify deployed safety.

---

### User Story 4 - Assess the release with traceable evidence (Priority: P2)

As a security reviewer, I need a complete inventory and reproducible findings tied to the assessed release revision, so that I can distinguish repairs, verified behavior, unresolved risks and historical reviews.

**Why this priority**: A bounded source inspection and historical register entries cannot establish release-wide cleanup compliance.

**Independent Test**: Starting from a declared revision and source inventory, reconcile every production module and canonical register entry with assessed usages, findings and verification evidence; independently reproduce the required release checks.

**Acceptance Scenarios**:

1. **Given** a declared release candidate, **When** the inventory is reconciled, **Then** every production module and every register row has a verified assessment or an explicit open/unassessed disposition, and newly discovered data categories or boundaries receive entries.
2. **Given** a terminal-cleanup claim, **When** a reviewer evaluates it, **Then** the evidence identifies source owners, terminal outcomes, synthetic procedure, runtime observations and limitations; a host test alone cannot establish physical-device or heap results.
3. **Given** repairs and their focused checks are complete, **When** implementation completion is recorded, **Then** pending release checks and independent review remain individually visible and do not inherit a passing status.
4. **Given** all release obligations have supporting evidence, **When** an independent security reviewer evaluates the exact candidate, **Then** the reviewer records identity, revision, scope, findings and disposition before release security acceptance can be marked complete.

### Edge Cases

- Cancellation before ownership transfer, after partial decoding, during persistence, or while another operation still borrows the same data.
- Failed parsing, cryptographic failure, duplicate terminal callbacks, replaced state and a session ending during active work.
- Hidden or abandoned editors, navigation changes, memory pressure, lock/session timeout and app process termination.
- Clipboard unavailable, clear rejected, device locked or backgrounded, scheduled cleanup crossed during sleep, repeated copies, unreadable ownership and an unrelated clipboard replacement racing the ownership check.
- Exception causes, nested object descriptions, encoded identifiers, startup logging and multiple configured diagnostic destinations.
- Retained platform text, immutable cryptographic-provider values, native normalization buffers and keyboard history that the application cannot erase.
- An unavailable device, missing heap artifact, unassessed source file, changed candidate revision or an unavailable independent reviewer.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The assessment MUST declare the candidate revision, source inventory, platforms and build configurations in scope. It MUST reconcile all repository production modules and every entry in the two canonical security registers. Any unassessed source or platform MUST remain explicitly open and MUST limit the resulting claims.
- **FR-002**: For each discovered secret or identifying-data usage, the inventory MUST record classification, exact source location, owner and borrowers, representation, purpose, persistence and logging restrictions, lifetime, terminal cleanup, boundary references and evidence or finding status. New usages MUST be added to the canonical registers without creating a security exemption.
- **FR-003**: Application-owned sensitive plaintext MUST follow Constitution Principle I: explicitly erasable volatile ownership, immediate terminal cleanup on success, failure, cancellation and session termination, and no retained immutable sensitive values outside the existing audited platform text boundary policy. Ownership transfers, never-started work, partial failures and shared borrowers MUST have explicit cleanup dispositions.
- **FR-004**: Assessment and remediation MUST explicitly cover RP/user account associations, credential identifiers, database projection models, CTAP request models and UI lifetimes, including serialization and temporary representations. Classification alone MUST NOT authorize plaintext persistence, logging or garbage-collection-only cleanup.
- **FR-005**: Permitted platform input/rendering boundaries MUST be recorded by exact usage, mutable-interface availability, purpose, reference lifetime and release controls. Key/seed material, serialization, providers and diagnostic APIs MUST NOT acquire a platform text exception through this feature.
- **FR-006**: After each successful sensitive clipboard copy, the application MUST schedule best-effort explicit clearing for 30 seconds after the copy. It MUST attempt eligible cleanup when scheduled execution is available and distinguish attempted clearing from observed removal. Foreground operation, backgrounding, lock/sleep, ordinary process termination, force-stop and platform refusal MUST be assessed individually. Interrupted, rejected or unobservable outcomes MUST be handled and recorded without a guaranteed-removal claim. A verified, correctly handled platform limitation may satisfy this scoped contract; a missing check remains Unverified. User-facing wording MUST disclose best-effort cleanup and avoid an unconditional 30-second guarantee, in accordance with the owner-authorized revision of `NFR-SEC-050`. Every sensitive Android clipboard entry MUST be marked sensitive before publication to request preview suppression on supporting devices; that marking MUST NOT be represented as access prevention, erasure or guaranteed protection of clipboard history.
- **FR-007**: Copy and clear failures MUST be observed and handled without a false success indication or sensitive diagnostic output. A returned publication call without matching readable ownership metadata MUST be reported as unconfirmed copying, not copied successfully; it MUST NOT silently cancel cleanup of a previously pending copy. Scheduled cleanup MUST distinguish superseded app copies so that an older app timer does not prematurely clear a newer app copy. Before automatic clearing, the application MUST perform a readable ownership check for the current app copy without reading or exporting unrelated content. If ownership differs or cannot be checked, it MUST skip automatic clearing and record a replaced or unknown outcome. The owner accepts the unavoidable external replacement race between the check and clear; absolute cross-app replacement preservation MUST NOT be promised. Explicit user-requested clearing acts on the current entry independently of this automatic ownership rule. Removal of the sensitive entry and any remaining platform copies MUST be distinguished in the evidence.
- **FR-008**: Operational logs, crash output, error representations and attached diagnostic data MUST exclude prohibited secrets, RP/user associations, credential identifiers and keyed lookup tokens. Confirmed unsafe emission sites MUST be remediated at their source; heuristic scrubbing MUST NOT serve as the sole evidence of safety.
- **FR-009**: Every confirmed source defect MUST have a recorded disposition and remediation evidence tied to the affected behavior. The four prior fixed-label edits MUST be reconciled against the candidate rather than presumed integrated or fully verified. Unreachable code MUST receive a justified disposition consistent with the existing prohibition on dead code.
- **FR-010**: Cleanup verification MUST exercise success, failure, cancellation and session termination for each assessed owner, including partial initialization and never-started transfers where applicable. Assertions MUST cover erased owned values and preserved active borrowed values, without reusing production computations as the expected result.
- **FR-011**: Runtime memory verification MUST use synthetic markers for vault secrets, recovery material, derived-key inputs and identifying RP/user data. It MUST inspect retention after the relevant terminal interactions and document remaining copies, their retaining owners where observable, and reachability limitations. Failure to observe a marker MUST NOT be presented as proof that all provider or OS memory was erased.
- **FR-012**: Physical Android clipboard verification MUST retain the intended 30-second schedule, actual attempt timing and eligible removal observations, and cover the FR-006 lifecycle cases, repeated copies and explicit clear. Planning MUST declare the device/OS matrix and observation procedure before execution. Correctly handled platform limits, observed failures and unavailable evidence MUST be distinguished. Virtual-time and mocked results MUST be labelled separately from physical outcomes.
- **FR-013**: Evidence MUST use the TRD §9.1 schema and identify assessed revision/build, exact files, procedure, expected and observed outcomes, test environment, artifacts and limitations. Evidence MUST remain accessible for its reference lifetime; changes affecting assessed behavior MUST trigger reassessment before its evidence is reused for release acceptance.
- **FR-014**: All procedures and retained artifacts MUST use synthetic data. Device procedures MUST avoid reading or exporting real vault data, production credentials or unrelated clipboard contents. Runtime artifacts MUST have access, retention and disposal handling defined before capture, including a controlled test state that excludes unrelated user data.
- **FR-015**: The assessment MUST distinguish owned-buffer erasure from residual cryptographic-provider, native, rendering, keyboard and OS copies. Non-erasable limitations MUST carry scope, impact and a reviewer disposition; they MUST NOT be relabelled as erasure, new authorization or a platform UI exception.
- **FR-016**: The backlog, canonical registers and current evidence MUST be synchronized. Dated historical review scopes and fingerprints MUST be preserved. Implementation completion and release security acceptance MUST be tracked separately; full release acceptance requires the required runtime evidence and independent security review of the assessed candidate, with no unassessed usages or unresolved noncompliance represented as passing.

### Key Entities *(include if feature involves data)*

- **Assessed usage**: A source occurrence of classified data linked to its owner, borrowers, representations, boundaries, cleanup obligations and verification disposition.
- **Cleanup obligation**: An owner's duty to erase or release data at a defined terminal event, with applicable outcomes and evidence.
- **Clipboard copy**: A successful sensitive transfer with a copy time, scheduled cleanup time, replacement history and observed cleanup result; this record must not retain the secret itself.
- **Security finding**: A confirmed defect, unassessed usage or provider/platform limitation with affected requirements, source scope, remediation or residual disposition and verification status.
- **Assessment evidence**: A revision-bound procedure and result record linked to source usages, artifacts, environment and limitations.
- **Acceptance record**: Separate implementation and release dispositions, with reviewer identity, assessed scope and outstanding obligations.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of production modules and canonical register rows are reconciled to the declared candidate, with every discovered usage assessed or explicitly open; release acceptance has zero unassessed usages within its declared scope.
- **SC-002**: Every assessed application-owned sensitive value has evidence for each applicable terminal outcome, with zero unresolved application-owned plaintext retention defects at release acceptance.
- **SC-003**: Every successful sensitive copy schedules best-effort cleanup for 30 seconds after copying, and every sensitive Android transfer carries the sensitivity marking before publication. Every automatic clear attempt is preceded by a readable ownership check identifying the current app copy; different or unreadable ownership leads to a recorded skip. Every required physical scenario records attempts, skips and eligible observations or demonstrates the applicable execution/observation limit, including the accepted external replacement race. Every induced copy/clear failure is handled without a false successful-cleanup claim. Missing evidence and deviations from this scoped behavior leave acceptance open; a demonstrated, correctly handled platform limitation does not require guaranteed removal or atomic replacement preservation to pass.
- **SC-004**: None of the prohibited synthetic secrets, identifiers or lookup tokens appears in the diagnostic outputs examined by the declared procedures; all confirmed unsafe emission findings have verified remediation or remain open.
- **SC-005**: Runtime memory checks are executed for vault, recovery/derivation and passkey-identifying data after terminal interactions. Every observed residual copy has a recorded owner or explicit unknown-origin finding and a scoped disposition; missing runtime evidence prevents release closure.
- **SC-006**: Every completion claim links to revision-bound evidence, and independent review records the exact candidate and outstanding limitations before release security acceptance. Historical evidence retains its original scope and identity.

## Assumptions

- This feature follows the [active backlog](../../docs/backlog/security-secret-cleanup-audit.md) (`FUP-SEC-001`) and [partial assessment](../../docs/security/secret-cleanup-audit-2026-11-13.md). The partial assessment's 19 passing focused host checks are prior bounded evidence, not a release baseline or logging/heap certification.
- Existing constitutional restrictions and BRD `NFR-SEC-010`, `NFR-SEC-030`, `NFR-SEC-040`, `NFR-SEC-041` and `NFR-SEC-050` remain binding. Feature-local requirement IDs organize delivery; production traceability continues to use stable BRD/TRD IDs.
- Android is the runtime delivery target. Repository-wide source reconciliation must identify other-platform usages and explicitly report unverified platform scope; this feature does not establish iOS runtime certification.
- Planning will establish a finite usage inventory, owner/outcome matrix, physical-device matrix, diagnostic destinations and synthetic runtime capture procedures. Current source and device availability must be rechecked at execution time.
- Existing product journeys, encrypted persistence, append-only history and cryptographic behavior remain governing constraints. Remediation is limited to evidenced defects and the supporting verification needed for this audit.
- The owner authorized best-effort clipboard clearing after 30 seconds and ownership-check-based automatic cleanup with skip-on-uncertainty, accepting the unavoidable external replacement race. `NFR-SEC-050` and related promises are synchronized to that decision. Platform limits must be demonstrated and handled explicitly; this change does not weaken immediate cleanup of application-owned sensitive buffers or grant any new platform text exception.
- Provider and OS memory may remain inaccessible to application erasure or observation. The existing canonical registers govern classification and permitted boundaries; this feature grants no new exception.
- Physical execution requires suitable controlled synthetic device state, and independent release acceptance requires a reviewer separate from the remediation author. Unavailable prerequisites remain open rather than passing.

# Feature Specification: Replace BIP-32 with HDK (Analysis Phase)

**Feature Branch**: `036-replace-bip32-with-hdk`  
**Created**: 2026-05-13
**Status**: Draft  
**Input**: User description: "Perform a structured analysis of the project with the intended goal to only use Hierarchical Deterministic Keys algorithm, and procedures from https://datatracker.ietf.org/doc/html/draft-dijkhuis-cfrg-hdkeys-06, remove all BIP-32, and BIP-44 path related HDK code. The use of 'BIP-39 mnemonic passphrase' for master seed generation remains, no code changes in this regard." Addendum: "This spec is for the structured analysis. Not for the code changes. The end result of the execution, implementation of this spec should be an analysis document with 1) Feasible to change to HDK process and algorithms from https://datatracker.ietf.org/doc/html/draft-dijkhuis-cfrg-hdkeys-06 . Reasons, evidence supporting decision. 2) A analysis with the changes, needed changes, recommended changes. IMPORTANT Including @[.specify/memory/constitution.md]changes."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Assess Feasibility of Complete HDK Migration (Priority: P1)

As a security architect, I want a structured analysis evaluating the feasibility of strictly using the HDK process (`draft-dijkhuis-cfrg-hdkeys-06`), so that I can make an informed decision on how to fully eradicate BIP-32/BIP-44 legacy derivation logic.

**Why this priority**: Before committing to a large cryptographic refactoring, we must ensure the new standard covers all project requirements and we have clear evidence supporting the migration.

**Independent Test**: The resulting analysis document contains a clear "Feasibility" section with concrete reasons and evidence supporting the transition.

**Acceptance Scenarios**:

1. **Given** the current state of cryptography standards, **When** reviewing the analysis, **Then** it clearly defines if a full HDK transition is feasible and provides supporting evidence.

---

### User Story 2 - Identify Required Code and Constitution Changes (Priority: P2)

As a lead developer, I want a comprehensive list of all necessary code, architecture, and constitution changes required to remove BIP-32/BIP-44 paths, so that the subsequent implementation phase can be accurately planned and executed.

**Why this priority**: We need a blueprint mapping exactly what files and modules will be affected, and how the project constitution must be updated to reflect the strict ban on BIP-32.

**Independent Test**: The resulting analysis document contains a detailed "Changes Needed" section that maps out required refactoring, including specific updates to `.specify/memory/constitution.md` (which currently allows BIP-32 usage for the PQ branch).

**Acceptance Scenarios**:

1. **Given** the current `constitution.md`, **When** reading the analysis, **Then** it explicitly outlines how the constitution's stance on BIP-32 CKD usage must be revised.
2. **Given** the current codebase, **When** reading the analysis, **Then** it identifies all legacy BIP-32/44 dependencies or logic that must be removed.

## Edge Cases

- What happens if the HDK draft does not adequately support the post-quantum (PQ) key branch isolation currently achieved via BIP-32 CKD? The analysis must address this gap.
- How will existing wallets derived using BIP-32 be handled? The analysis must document the decision for a clean break versus migration.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The deliverable MUST be a structured Markdown analysis document, not code implementation.
- **FR-002**: The analysis MUST evaluate the feasibility of migrating strictly to the HDK algorithms defined in `draft-dijkhuis-cfrg-hdkeys-06`.
- **FR-003**: The analysis MUST provide concrete reasons and evidence supporting the decision to migrate (or not migrate) specific components.
- **FR-004**: The analysis MUST detail all needed and recommended code changes to completely remove BIP-32 and BIP-44 path related logic from the project.
- **FR-005**: The analysis MUST explicitly evaluate and propose updates to `constitution.md`, specifically addressing the current permissible use of BIP-32 for PQ branch isolation.
- **FR-006**: The analysis MUST outline a strategy ensuring that the "BIP-39 mnemonic passphrase" process for master seed generation remains intact and unchanged.

### Key Entities

- **Analysis Document**: The primary output artifact containing the feasibility study, evidence, and proposed changes.
- **Project Constitution**: `.specify/memory/constitution.md`, which defines the core cryptographic rules and must be evaluated for updates.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An analysis document is produced containing a dedicated "Feasibility and Evidence" section.
- **SC-002**: The analysis document contains a comprehensive list of recommended code changes.
- **SC-003**: The analysis document contains proposed redlines/updates for `.specify/memory/constitution.md`.
- **SC-004**: No actual product code is modified during the execution of this specific task (it is strictly an analysis phase).

## Assumptions

- We assume that `draft-dijkhuis-cfrg-hdkeys-06` is stable enough to evaluate as a total replacement for BIP-32.
- We assume the analysis will validate the previous decision that existing keys/wallets do not need a migration path (clean break).

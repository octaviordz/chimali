# Tasks: Replace BIP-32 with HDK (Analysis Phase)

**Input**: Design documents from `/specs/036-replace-bip32-with-hdk/`
**Prerequisites**: plan.md (required), spec.md (required), research.md

**Organization**: Tasks are grouped by user story. The deliverable is strictly a Markdown analysis document — no product code changes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish the analysis document structure and gather all necessary inputs

- [ ] T001 Create analysis document skeleton at specs/036-replace-bip32-with-hdk/analysis-hdk-migration.md
- [ ] T002 [P] Read and catalog all BIP-32/BIP-85/BIP-44 references in source code files, test files, and build.gradle.kts files (e.g., bitcoinj dependencies, WalletMasterSeedProvider.kt, PostQuantumCrypto.kt, Fido2CryptoService.kt, and associated test files)
- [ ] T003 [P] Read and catalog all BIP-32/BIP-44/BIP-85 references in documentation files: .specify/memory/constitution.md, docs/brd.md, docs/changelogs/, CHANGELOG.md

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Deep-read the HDK draft specification to establish the technical foundation for the feasibility analysis

- [ ] T004 Read and summarize relevant sections of draft-dijkhuis-cfrg-hdkeys-06 (https://datatracker.ietf.org/doc/html/draft-dijkhuis-cfrg-hdkeys-06) (§2.2 Seed, §2.4 DeriveSalt, §2.5 Key Derivation Rules, §3.2 Multiplicative Blinding, §4.1 HDK-ECDH-P256) — write findings to the "HDK Spec Summary" section of specs/036-replace-bip32-with-hdk/analysis-hdk-migration.md
- [ ] T005 Read existing HDK implementation in core/security/src/ to catalog which HDK operations are already implemented (HdkEcdhP256, DeriveSalt, BlindPublicKey, DeriveBlindingFactor, HashToScalar) — write findings to the "Current HDK Implementation Inventory" section of specs/036-replace-bip32-with-hdk/analysis-hdk-migration.md
- [ ] T006 Read and catalog the current PQ branch isolation logic in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt (functions: derivePqChildSeed, ckdHard, and all BIP-32/85 constants) — write findings to the "PQ Branch Isolation Audit" section of specs/036-replace-bip32-with-hdk/analysis-hdk-migration.md

**Checkpoint**: All raw inputs gathered — feasibility evaluation can begin

---

## Phase 3: User Story 1 — Assess Feasibility of Complete HDK Migration (Priority: P1) 🎯 MVP

**Goal**: Produce a "Feasibility and Evidence" section proving (or disproving) that the HDK draft fully replaces BIP-32 for every use case in the project

**Independent Test**: The analysis document contains a clear "Feasibility and Evidence" section with concrete reasons and evidence

### Implementation for User Story 1

- [ ] T007 [US1] Write "Feasibility and Evidence" section in specs/036-replace-bip32-with-hdk/analysis-hdk-migration.md evaluating whether DeriveSalt (§2.4) can replace BIP-32 CKD for PQ branch isolation — include cryptographic rationale
- [ ] T008 [US1] Write "PQ Branch Migration Strategy" subsection in specs/036-replace-bip32-with-hdk/analysis-hdk-migration.md proposing the replacement of derivePqChildSeed/ckdHard with an HDK DeriveSalt-based approach using a domain-separated context string
- [ ] T009 [US1] Write "BIP-39 Seed Generation — No Change Required" subsection in specs/036-replace-bip32-with-hdk/analysis-hdk-migration.md confirming that Bip39MasterSeedGenerator (core/security/src/androidMain/kotlin/com/chimali/core/security/impl/Bip39MasterSeedGenerator.kt) is independent from BIP-32 and requires zero changes
- [ ] T010 [US1] Write "Existing Wallets — Clean Break Decision" subsection in specs/036-replace-bip32-with-hdk/analysis-hdk-migration.md documenting that keys derived via BIP-32/85 CKD will become inaccessible after migration and justifying the clean-break approach
- [ ] T011 [US1] Write "Risk Assessment" subsection in specs/036-replace-bip32-with-hdk/analysis-hdk-migration.md identifying risks (draft stability, no third-party library, PQ curve support gaps) and mitigations

**Checkpoint**: Feasibility section complete — can be reviewed independently

---

## Phase 4: User Story 2 — Identify Required Code and Constitution Changes (Priority: P2)

**Goal**: Produce a comprehensive change list covering all code, documentation, and constitution updates needed to fully remove BIP-32/BIP-44

**Independent Test**: The analysis document contains a detailed "Changes Needed" section mapping out all required refactoring, including specific constitution updates

### Implementation for User Story 2

- [ ] T012 [P] [US2] Write "Code Changes — WalletMasterSeedProvider" subsection in specs/036-replace-bip32-with-hdk/analysis-hdk-migration.md detailing removal of ckdHard(), derivePqChildSeed(), and all BIP-32/85 constants from feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt
- [ ] T013 [P] [US2] Write "Code Changes — PostQuantumCrypto" subsection in specs/036-replace-bip32-with-hdk/analysis-hdk-migration.md detailing KDoc updates in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/PostQuantumCrypto.kt to remove BIP-85/CKD references
- [ ] T014 [P] [US2] Write "Code Changes — Fido2CryptoService" subsection in specs/036-replace-bip32-with-hdk/analysis-hdk-migration.md detailing KDoc updates in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/Fido2CryptoService.kt to remove BIP-32 references from the derivation table
- [ ] T015 [US2] Write "Constitution Redlines" section in specs/036-replace-bip32-with-hdk/analysis-hdk-migration.md with proposed diff/redlines for .specify/memory/constitution.md §II (Master Seed Architecture) — replace BIP-85/BIP-32 PQ branch language with HDK DeriveSalt context-based derivation
- [ ] T016 [US2] Write "Documentation Updates" subsection in specs/036-replace-bip32-with-hdk/analysis-hdk-migration.md listing all BIP-32/44/85 references in docs/brd.md, CHANGELOG.md, and docs/changelogs/ that must be updated or annotated as historical
- [ ] T017 [US2] Write "Recommended New Tests" subsection in specs/036-replace-bip32-with-hdk/analysis-hdk-migration.md proposing test vectors for the new DeriveSalt-based PQ child seed derivation to replace the old BIP-85 CKD tests

**Checkpoint**: All code and constitution changes are fully mapped out

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Final review and quality assurance of the analysis document

- [ ] T018 Review the complete analysis document at specs/036-replace-bip32-with-hdk/analysis-hdk-migration.md for internal consistency, completeness against spec.md success criteria (SC-001 through SC-004), and correctness of file path references
- [ ] T019 Verify SC-004 compliance: confirm that NO product source code files were modified during this analysis phase (only spec artifacts created)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational phase completion
- **User Story 2 (Phase 4)**: Depends on Foundational phase completion; can run in parallel with US1
- **Polish (Phase 5)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) — No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) — Partially references US1 findings but is independently testable

### Within Each User Story

- T007 → T008 → T009/T010 (sequential within feasibility)
- T012, T013, T014 can all run in parallel (different file analyses)
- T015 depends on T012-T014 (needs full code audit to inform constitution redlines)

### Parallel Opportunities

- T002 and T003 can run in parallel (Setup phase)
- T012, T013, T014 can all run in parallel (different source files)
- US1 and US2 can be worked on in parallel after Phase 2 completes

---

## Parallel Example: User Story 2

```text
# Launch all code audit tasks for User Story 2 together:
Task: "Write Code Changes — WalletMasterSeedProvider subsection"
Task: "Write Code Changes — PostQuantumCrypto subsection"
Task: "Write Code Changes — Fido2CryptoService subsection"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (create document skeleton, gather inputs)
2. Complete Phase 2: Foundational (read HDK spec, audit existing code)
3. Complete Phase 3: User Story 1 (feasibility and evidence)
4. **STOP and VALIDATE**: Review feasibility section independently
5. Share with stakeholders for decision

### Incremental Delivery

1. Complete Setup + Foundational → Inputs gathered
2. Add User Story 1 → Feasibility proven → Share for review (MVP!)
3. Add User Story 2 → Full change list + constitution redlines → Complete deliverable
4. Polish → Final quality check

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- SC-004: No product source code may be modified during this phase
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently

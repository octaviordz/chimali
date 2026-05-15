# Tasks: Correct Cryptographic Terminology

**Input**: Design documents from `specs/038-correct-crypto-terminology/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Initial verification and audit review

- [x] T001 Review all findings in `specs/038-correct-crypto-terminology/audit.md` against current `docs/brd.md` and `docs/trd.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Removal of invented and unused terminology across foundational documents

**⚠️ CRITICAL**: These deletions must be complete before reclassifying valid primitives.

- [x] T002 [P] Remove invented term "HHD" (Hybrid Hierarchical Deterministic) from `docs/brd.md`
- [x] T003 [P] Remove unused algorithm "Falcon-512" from `docs/brd.md`
- [x] T004 [P] Remove all occurrences of the term "HHD" from `docs/trd.md`

**Checkpoint**: Invented terminology purged - valid primitive reclassification can begin.

---

## Phase 3: User Story 1 - Technical Accuracy (Priority: P1) 🎯 MVP

**Goal**: Correct misclassifications of ML-DSA, ML-KEM, and derivation mechanisms.

**Independent Test**: Verify that NIST/FIPS terms are used correctly and PQC derivation aligns with HDK `DeriveSalt` in both BRD and TRD.

### Implementation for User Story 1

- [x] T005 [US1] Reclassify ML-DSA-65 as a digital signature algorithm (not encryption) in `docs/brd.md` line 84
- [x] T006 [US1] Replace SLIP-10/BIP-44 PQC derivation with HDK `DeriveSalt` per Constitution Principle II in `docs/brd.md` line 87
- [x] T007 [US1] Replace SLIP-10 PQC derivation with HDK `DeriveSalt` in `docs/trd.md` line 58
- [x] T008 [US1] Move ML-KEM-768 out of "Encryption Standards" and reclassify as KEM in `docs/trd.md` line 64
- [x] T009 [US1] Clarify PBKDF2-HMAC-SHA512 role as mnemonic stretching (not entry KDF) in `docs/trd.md` line 57

**Checkpoint**: Foundational documents are now factually accurate regarding cryptographic primitives.

---

## Phase 4: User Story 2 - Documentation Consistency (Priority: P2)

**Goal**: Remove stale architectural references and align with Constitution Principle II.

**Independent Test**: Ensure no references to legacy BIP-32/44 patterns remain for PQC keys.

### Implementation for User Story 2

- [x] T010 [US2] Align NFR-SEC-040 with HDK and remove "legacy BIP-32 style components" from `docs/brd.md` line 87
- [x] T011 [US2] Separate ML-KEM and ML-DSA support descriptions in `docs/trd.md` line 21 to distinguish roles

**Checkpoint**: BRD and TRD are now 100% consistent with the active Technical Constitution.

---

## Phase 5: User Story 3 - Platform Clarity (Priority: P3)

**Goal**: Use precise platform terminology for hardware-backed security.

**Independent Test**: Verify "HSM" is replaced by "Android Keystore" or "TEE/StrongBox".

### Implementation for User Story 3

- [x] T012 [US3] Replace "HSM" with "Android Keystore" in `docs/brd.md` line 31

**Checkpoint**: Document uses platform-appropriate security terminology.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final verification and documentation cleanup

- [x] T013 Verify all technical terms against `constitution.md` Principle II and Principle X.5
- [x] T014 [P] Final search for "HHD", "Falcon-512", and "SLIP-10" to ensure no PQC context remains in `docs/`
- [x] T015 [P] Update the `specs/038-correct-crypto-terminology/audit.md` to mark all findings as resolved

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately.
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories.
- **User Stories (Phase 3+)**: All depend on Foundational phase completion.
- **Polish (Final Phase)**: Depends on all desired user stories being complete.

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories.
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - Should be implemented after US1 for maximum consistency.

### Parallel Opportunities

- All Foundational tasks (T002-T004) can run in parallel as they target different documents or sections.
- Polish tasks T014-T015 can run in parallel.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Verify technical accuracy with a peer review.

### Incremental Delivery

1. Complete Setup + Foundational → Documents cleaned of invented terms.
2. Add User Story 1 → Technical accuracy achieved.
3. Add User Story 2 → Architectural consistency achieved.
4. Add User Story 3 → Platform clarity achieved.

# Feature Specification: Correct Cryptographic Terminology

**Feature Branch**: `038-correct-crypto-terminology`  
**Created**: 2026-05-15  
**Status**: Draft  
**Input**: User description: "correct and enhance statements. Use analysis document @[specs/038-correct-crypto-terminology/audit.md] information to implement fixes."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Technical Accuracy Audit (Priority: P1)

As a security architect or new developer, I need the foundational documentation to be factually accurate so that I don't make incorrect assumptions about the system's security posture or implementation details.

**Why this priority**: Correcting factually incorrect statements (like calling a signature algorithm an encryption method) is critical for preventing security vulnerabilities and implementation errors.

**Independent Test**: Can be verified by reviewing `brd.md` and `trd.md` against the NIST/FIPS standards and the project's `constitution.md`.

**Acceptance Scenarios**:

1. **Given** a reader looks at ML-DSA in `brd.md`, **When** they read the description, **Then** it correctly identifies it as a digital signature algorithm used for attestation, not an encryption method.
2. **Given** a reader looks at key derivation in `trd.md`, **When** they see PQC mentioned, **Then** it references HDK `DeriveSalt` instead of SLIP-10.
3. **Given** a reader looks at "Encryption Standards" in `trd.md`, **When** they check the section, **Then** ML-KEM is NOT listed as a symmetric encryption algorithm.

---

### User Story 2 - Documentation Consistency (Priority: P2)

As a project maintainer, I need the high-level documentation (BRD/TRD) to be in sync with the governing principles (Constitution) so that there is no ambiguity in the project's architectural direction.

**Why this priority**: Stale references to BIP-44 or SLIP-10 for PQC derivation directly contradict the recently adopted HDK standard, creating confusion for contributors.

**Independent Test**: Can be verified by cross-referencing `brd.md` with `constitution.md` Principle II.

**Acceptance Scenarios**:

1. **Given** the `constitution.md` mandates HDK, **When** the `brd.md` describes master seed architecture, **Then** it does not reference legacy BIP-44 paths for key management.
2. **Given** the project has moved away from "HHD" (Hybrid Hierarchical Deterministic), **When** a reader searches for this term, **Then** it is no longer found in foundational documents.

---

### User Story 3 - Platform Clarity (Priority: P3)

As a developer, I need to know exactly which hardware components are being leveraged for security.

**Why this priority**: "HSM" is too broad and technically inaccurate for the Android ecosystem; using the correct Android-specific terminology helps in choosing the right APIs (KeyStore/StrongBox).

**Independent Test**: Can be verified by checking that "HSM" is replaced by "Android Keystore" or "TEE" in `brd.md`.

**Acceptance Scenarios**:

1. **Given** a description of hardware-backed security, **When** it mentions the security module, **Then** it specifies "Android Keystore" or "TEE/StrongBox".

---

### Edge Cases

- **Mixed contexts**: Some documents might use "encryption" as a broad term for "cryptography" in non-technical sections. These should be clarified without becoming overly pedantic in high-level summaries, but must be precise in technical requirement sections.
- **Legacy references**: If an older document is intentionally preserved for historical context (not the case for BRD/TRD), it might contain old terms. However, BRD/TRD are living documents and must be current.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `brd.md` MUST be updated to correctly classify ML-DSA-65 as a digital signature algorithm used for attestation/signing, removing claims that it is an encryption method.
- **FR-002**: `brd.md` and `trd.md` MUST remove all references to SLIP-10 or BIP-44 in the context of Post-Quantum (PQC) key derivation.
- **FR-003**: `brd.md` and `trd.md` MUST align with `constitution.md` Principle II by specifying HDK per `draft-dijkhuis-cfrg-hdkeys-06` as the derivation mechanism.
- **FR-004**: `trd.md` MUST reclassify ML-KEM-768 as a Key Encapsulation Mechanism and move it out of any section labeled "Encryption Standards."
- **FR-005**: `brd.md` MUST replace the term "HSM" with "Android Keystore" (specifying TEE or StrongBox as appropriate).
- **FR-006**: `trd.md` MUST clarify that PBKDF2-HMAC-SHA512 is used for BIP-39 mnemonic-to-seed stretching, not as the primary key derivation function for entries.
- **FR-007**: The invented term "HHD" (Hybrid Hierarchical Deterministic) MUST be removed from all documents.
- **FR-008**: References to "Falcon-512" (not used in the project) MUST be removed from the `brd.md`.

### Key Entities *(include if feature involves data)*

- **Master Seed**: The 512-bit root of trust derived from BIP-39 mnemonic.
- **HDK (Hierarchical Deterministic Key)**: The derivation mechanism defined in IETF draft-dijkhuis-cfrg-hdkeys-06.
- **ML-DSA**: The Post-Quantum digital signature scheme (FIPS 204) used for attestation.
- **ML-KEM**: The Post-Quantum key encapsulation mechanism (FIPS 203) reserved for future remote provisioning.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of "Critical" findings identified in `specs/038-correct-crypto-terminology/audit.md` are resolved in the target files (`brd.md`, `trd.md`).
- **SC-002**: Documentation achieves 100% terminological consistency with `constitution.md` Version 0.13.0.
- **SC-003**: A keyword search for "HHD" or "Falcon-512" in the `docs/` and `.specify/` directories returns zero results.

## Assumptions

- **Constitutional Supremacy**: The `constitution.md` is the final authority on architectural and cryptographic decisions.
- **Audit Completeness**: The `audit.md` has correctly identified the lines requiring modification.
- **No functional changes**: This feature is documentation-only; it does not change the underlying cryptographic implementation, which is already aligned with the constitution.

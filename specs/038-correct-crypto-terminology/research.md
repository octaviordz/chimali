# Research: Cryptographic Terminology Alignment

This document consolidates the findings from the [Cryptographic Terminology Audit](file:///d:/octav/source/repos/Chimali/specs/038-correct-crypto-terminology/audit.md) and provides the rationale for the chosen fixes.

## Key Decisions

### Decision 1: ML-DSA Classification
- **Decision**: Explicitly label ML-DSA-65 as a **Digital Signature Algorithm** (FIPS 204).
- **Rationale**: It is technically impossible to use ML-DSA for encryption. Mislabeling it as a "primary cryptographic method" or "encryption method" in the BRD creates a false sense of security regarding payload protection.
- **Alternatives considered**: Using generic "PQC algorithm" (rejected as too vague).

### Decision 2: ML-KEM Role & Placement
- **Decision**: Reclassify ML-KEM-768 as a **Key Encapsulation Mechanism** (FIPS 203) and move it out of "Encryption Standards."
- **Rationale**: While ML-KEM is used for key establishment, it is not a symmetric encryption algorithm like AES. Keeping it in the encryption section conflates its purpose.
- **Alternatives considered**: Removing it entirely (rejected as it is planned for future remote provisioning).

### Decision 3: PQC Key Derivation Strategy
- **Decision**: Replace SLIP-10 and BIP-44 with **HDK `DeriveSalt`** (IETF `draft-dijkhuis-cfrg-hdkeys-06`) for the PQC branch.
- **Rationale**: SLIP-10 is curve-specific and cannot derive lattice-based keys. The project constitution already mandates the HDK draft for PQC isolation.
- **Alternatives considered**: Extending SLIP-10 (rejected as it would be "cryptographic invention").

### Decision 4: Terminology Standardization
- **Decision**: Replace "HSM" with "Android Keystore" and remove "HHD".
- **Rationale**: "HSM" implies a different security model than Android's TEE/SE. "HHD" is an invented term with no standard definition.
- **Alternatives considered**: Defining "HHD" in a glossary (rejected to favor standard terminology).

## Conclusion
The research phase confirms that the audit findings are technically sound and alignment with NIST/IETF standards is mandatory for project integrity.

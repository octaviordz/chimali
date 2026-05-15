# Implementation Plan: Correct Cryptographic Terminology

**Branch**: `038-correct-crypto-terminology` | **Date**: 2026-05-15 | **Spec**: [spec.md](file:///d:/octav/source/repos/Chimali/specs/038-correct-crypto-terminology/spec.md)
**Input**: Feature specification from `specs/038-correct-crypto-terminology/spec.md`

## Summary

The primary requirement is to remediate factual inaccuracies and architectural inconsistencies in the `brd.md` and `trd.md` files. The technical approach involves applying the fixes documented in the [Cryptographic Terminology Audit](file:///d:/octav/source/repos/Chimali/specs/038-correct-crypto-terminology/audit.md), specifically:
1. Reclassifying ML-DSA as a signature algorithm and ML-KEM as a Key Encapsulation Mechanism.
2. Replacing SLIP-10/BIP-44/BIP-32 references for PQC keys with HDK `DeriveSalt` per `constitution.md` Principle II.
3. Correcting platform terminology (HSM → Android Keystore/TEE).
4. Purging the invented term "HHD" and unused algorithm "Falcon-512".

## Technical Context

**Language/Version**: Markdown (GFM)  
**Primary Dependencies**: NIST FIPS 203 (ML-KEM), FIPS 204 (ML-DSA), IETF `draft-dijkhuis-cfrg-hdkeys-06`  
**Storage**: N/A  
**Testing**: Manual cross-verification against `constitution.md` and standard technical literature.  
**Target Platform**: Android (Documentation)
**Project Type**: Documentation / Architectural Specifications  
**Performance Goals**: N/A  
**Constraints**: Must strictly adhere to `constitution.md` Principle II; No cryptographic invention (Principle X.5).  
**Scale/Scope**: 2 Core documents (`brd.md`, `trd.md`) + Audit implementation.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

1. **Principle II (HDK Alignment)**: The plan explicitly replaces legacy derivation references with HDK `DeriveSalt` as mandated. **PASS**.
2. **Principle X.5 (No Invention)**: The plan removes the invented "HHD" term and uses standard NIST/IETF terminology. **PASS**.
3. **Security Standards**: Classifications of KEM vs Signature are corrected to prevent architectural misunderstanding. **PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/038-correct-crypto-terminology/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Phase 0 output: Consolidated audit findings
└── data-model.md        # Phase 1 output: Cryptographic entity definitions
```

### Source Code (repository root)

```text
docs/
├── brd.md               # Target for correction
└── trd.md               # Target for correction
```

**Structure Decision**: This is a pure documentation correction task. No source code changes are required beyond the foundational documentation files.

## Complexity Tracking

*No constitution violations identified.*

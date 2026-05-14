# Implementation Plan: Replace BIP-32 with HDK (Analysis Phase)

**Branch**: `036-replace-bip32-with-hdk` | **Date**: 2026-05-13 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/036-replace-bip32-with-hdk/spec.md`

## Summary

This plan outlines the creation of a structured analysis document regarding the migration of the project's cryptographic key derivation from legacy BIP-32/BIP-44 to the Hierarchical Deterministic Keys (HDK) process (`draft-dijkhuis-cfrg-hdkeys-06`). The deliverable for this feature is strictly the analysis—no product code will be changed during this execution phase. The analysis proves feasibility, details required code changes, and outlines necessary updates to the project constitution.

## Technical Context

**Language/Version**: Kotlin Multiplatform  
**Primary Dependencies**: None external (will use custom implementation)  
**Storage**: N/A for analysis phase  
**Testing**: JUnit 5, MockK, kotlin.test (for future verification)  
**Target Platform**: Android Native Application (Minimum SDK 28)  
**Project Type**: Mobile App / Cryptography Module  
**Performance Goals**: N/A for analysis phase  
**Constraints**: Must completely remove all BIP-32/BIP-44 dependencies  
**Scale/Scope**: Impacts all `feature/fido2` and vault key derivation  

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

**Result: FAIL (Requires Update)**
- **Reason**: Section `II. Master Seed Architecture` explicitly permits BIP-32 CKD usage for PQ branch isolation (`m/83696968'/83286642'/2'`).
- **Resolution**: The analysis document produced in this phase must redline this section and propose replacing it with an HDK `DeriveSalt` context-based derivation to comply with the new mandate to remove all BIP-32 code.

## Project Structure

### Documentation (this feature)

```text
specs/036-replace-bip32-with-hdk/
├── plan.md              # This file
├── research.md          # Core feasibility and evidence analysis
├── data-model.md        # N/A (Analysis phase)
├── quickstart.md        # N/A (Analysis phase)
└── tasks.md             # For tracking execution (if needed)
```

**Structure Decision**: No code modules are affected during this phase. All output artifacts reside within `specs/036-replace-bip32-with-hdk/`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Custom HDK Implementation | No actively maintained Kotlin Multiplatform libraries exist for `draft-dijkhuis-cfrg-hdkeys-06`. | Third-party library integration rejected due to lack of availability. |

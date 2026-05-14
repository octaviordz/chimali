# Phase 0: Outline & Research

## Feasibility of Full HDK Migration

**Decision**: A full migration to the Hierarchical Deterministic Keys (HDK) process per `draft-dijkhuis-cfrg-hdkeys-06` is highly feasible.

**Rationale**:
The primary reason BIP-32 was previously retained was to provide a cryptographically isolated seed for the Post-Quantum (ML-DSA) key branch. However, `draft-dijkhuis-cfrg-hdkeys-06` §2.4 defines a standard `DeriveSalt` mechanism using Hash functions that incorporate domain-separated `ctx` (context) strings.
Instead of using BIP-32 CKD (`m/83696968'/83286642'/2'`), the system can simply invoke `DeriveSalt` with a specific context (e.g., `ctx = "PQ_ML-DSA_Branch"`) to deterministically derive a child seed from the master seed. This provides identical cryptographic isolation without relying on legacy BIP-32 HMAC-SHA512 + chain code mechanisms, allowing us to drop the BIP-32 dependency entirely.

**Alternatives considered**:
- *Keep BIP-32 for PQ branch*: Rejected as it violates the new strict requirement to remove all BIP-32 code.

## HDK Implementation Approach

**Decision**: Fallback to Custom Kotlin Multiplatform Implementation.

**Rationale**:
`draft-dijkhuis-cfrg-hdkeys-06` is a relatively new and evolving IETF draft. A thorough search of the Kotlin Multiplatform ecosystem reveals no actively maintained, production-ready, open-source libraries that explicitly implement this specific draft version for both ECDSA and Post-Quantum curves. 
Per the specification's fallback condition, we must implement the required logic (e.g., §2.4 `DeriveSalt` and §4.1 `HDK-ECDH-P256`) internally. The current codebase already has foundational cryptographic primitives (SHA-256, HMAC, SecureRandom) that can be composed to implement the draft RFC exactly.

**Alternatives considered**:
- *Use a third-party library*: Rejected due to the lack of actively maintained libraries for this specific IETF draft in the Kotlin Multiplatform ecosystem.

## Required Constitution Changes

**Decision**: The project `constitution.md` MUST be amended.

**Rationale**:
The current constitution in Section `II. Master Seed Architecture` states:
> "The ML-DSA/Post-Quantum key branch uses a **BIP-85-style** hardened CKD derivation (`m/83696968'/83286642'/2'`) to produce a child seed that is cryptographically isolated from the ECDSA HDK branch. This BIP-32 CKD usage is intentional, limited to the PQ branch only..."

This directly contradicts the new feature requirement to remove all BIP-32 logic. The constitution must be redlined to replace this with a domain-separated HDK `DeriveSalt` approach.

## Recommended Code Changes

1. **Remove BIP-32 / BIP-44 Dependencies**: Remove any Gradle dependencies, such as `bitcoinj` or custom BIP-32 libraries, that were used for CKD.
2. **Refactor PQ Branch Isolation**: 
   - Replace `m/83696968'/83286642'/2'` string path references.
   - Implement a new `deriveChildSeed(masterSeed, context)` function leveraging `draft-dijkhuis-cfrg-hdkeys-06` §2.4.
3. **Clean up KDocs & Comments**: Scrub the codebase for references to BIP-32, BIP-44, and legacy chain codes.
4. **Verification Tests**: Add test vectors to strictly assert that the new `DeriveSalt` implementation matches the RFC draft.


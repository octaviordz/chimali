# Changelog: Project Constitution v0.6.0 & BRD Update

**Date:** 2026-03-12  
**Scope:** `.specify/memory/constitution.md`, `docs/brd.md`  
**Triggered by:** T145c analysis (speckit.analyze) and HDK research

## Overview

Following a cross-artifact consistency analysis (speckit.analyze) for T145c and a research phase on Hierarchical Deterministic Key (HDK) derivation, two project governance documents were updated to reflect clarified security policies and the architectural decision to support post-quantum cryptography via a BIP39 root seed.

---

## Constitution — v0.5.0 → v0.6.0

### §I — Security First: AES-256-SIV Exception Added

**Previous**: §I mandated AES-256-GCM for all sensitive data without exception.

**New**: §I now explicitly documents that BIP39 mnemonic seed phrases stored via `EncryptedSharedPreferences` MAY use AES-256-SIV for preference **keys** (deterministic encryption, nonce-misuse resistant). Preference **values** (where the mnemonic string lives) are still protected by AES-256-GCM, fully satisfying the original mandate.

> **Rationale**: `EncryptedSharedPreferences` from `androidx.security:security-crypto` uses AES-256-SIV for key encryption (required for deterministic lookup) and AES-256-GCM for values. The mnemonic string is stored as a value, so it is always GCM-protected. The SIV exception is documented to prevent future confusion during code review.

### §II — Master Seed Architecture: HDK Support Added

**Previous**: §II described BIP39 + HDK-ECDH-P256 (IETF `draft-dijkhuis-cfrg-hdkeys-06`).

**New**: §II now also references the **Hierarchical Deterministic Key (HDK)** architecture. A single BIP39 root seed can derive keys for both classical schemes (ECDSA/Ed25519) and Post-Quantum schemes (e.g., ML-DSA-65) without requiring an additional mnemonic.

> **Rationale**: The HDK architecture was selected after analyzing the IETF HD Keys draft and comparing with implementations in Walt-ID, Bulwark Vault, and the EU Digital Identity Wallet. It provides the best balance of portability, forward-compatibility, and FIDO2 compliance.

---

## BRD — NFR-SEC-040 Update

**Requirement**: NFR-SEC-040 — Master Key Management

**Previous text** (summarized): Required BIP39 for mnemonic seed generation and HDK for key management.

**New text** (summarized): Now explicitly states that the HDK architecture is used, supporting derivation of both classical and Post-Quantum signature schemes from a single BIP39 root seed. References `draft-dijkhuis-cfrg-hdkeys-06`.

---

## Impact

| Document | Version | Stable? |
|----------|---------|---------|
| `constitution.md` | 0.6.0 | ✅ No breaking changes |
| `brd.md` — NFR-SEC-040 | — | ✅ Additive clarification only |

No existing implementations were changed as a result of these documentation updates. The AES-256-SIV documentation matches the actual behaviour of `EncryptedSharedPreferences` already deployed in T145c.

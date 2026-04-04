# Changelog: FIDO2 Cryptographic and Transport Hardening

**Date**: 2026-04-03
**Feature**: FIDO2 Virtual Authenticator (`004-fido2-hid`)

## Overview

This update implements critical security, cryptography, and transport stability requirements mandated by the Chimali Constitution and FIDO2 specification. It resolves all remaining open tasks in `specs/004-fido2-hid/tasks.md` (marking 181/181 tasks completed) and aligns the core feature specification with the implemented outcomes.

## Changes

### 1. Cryptography: AES-256-SIV & Searchable Metadata
- Implemented `AesSivEncryptionManager` to provide AES-256-SIV (Synthetic IV) deterministic authenticated encryption (RFC 5297).
- Extracted cryptographic constraints indicating AES-256-SIV usage specifically for exact-match database indexing and key wrapping without the risk of nonce-reuse prevalent in high-frequency GCM contexts (T019a).
- Created `EncryptedMetadataIndexService` (T113a) to encrypt Relying Party (RP) lookup tags and credential aliases. This ensures all database lookup parameters are stored via deterministic ciphertext instead of plaintext, fulfilling **Constitution §I.2**.

### 2. HID Transport: Thread-Safe FIFO Queue
- Refactored `BluetoothHidTransportImpl` to resolve potential transaction fragmentation and packet interleaving (T053a).
- Introduced a Kotlin `Channel<ByteArray>` with a dedicated 256-capacity buffer and a persistent, dedicated sender coroutine. 
- Guaranteed atomic message framing across overlapping UI/Ceremony flows (e.g., handling asynchronous keep-alive packets colliding with a large credential response). This fulfills **Constitution §IV** regarding strict latency and stability constraints.

### 3. Protocol Extensions: FIDO2.1 `hmac-secret`
- Implemented `HmacSecretProcessor` to calculate and return deterministically-bound credential secrets during `GetAssertion` operations (T087a).
- Ensures seamless offline extension processing without breaking strictly isolated derivation boundaries. 

### 4. Specification & Quality Gates
- **Local-First Verification**: Audited the log configuration; validated that `LocalCrashReportingTree` already suppresses sensitive cloud instrumentation (T020a, NFR-SEC-020).
- **Windows compatibility**: Confirmed U2F legacy fallback probing logic operates correctly in `BluetoothHidTransportImpl` (T104a).
- **Static Analysis Gates Passed**: Fixed residual baseline rule exceptions; verified 100% compliance using the `detektMain` and `ktlintCheck` tasks across the FIDO2 and Security modules (T164a).
- **Specification Alignment**: Addressed issues raised by the `speckit.analyze` tool:
  - Formally mapped generic FIDO2 outcomes and edge cases (Memory Full = Error `0x27`; Denied Consent = Error `0x29`/`0x23`) directly inside the `spec.md` edge cases block.
  - Linked the 1000 credential hardware limit (`FR-HID-022`) explicitly into `plan.md`.

## Implications

- The FIDO2 implementation phase completes its primary structural execution. Focus shifts to phase deployment and minor UI refinements under the established stable baseline constraint.

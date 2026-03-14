# Constitution v0.7.0: Multi-Mode Symmetric Encryption Strategy (2026-03-13)

## Summary
The project Constitution and Business Requirements Document (BRD) have been updated to formalized the project's cryptographic strategy regarding the use of AES-256-GCM and AES-256-SIV. Based on a comprehensive security evaluation, the original mandate enforcing GCM across the board with SIV as an exception has been replaced with a formal **Multi-Mode Symmetric Encryption Strategy**.

## Architectural Changes

### Constitution Update (§I. Security First)
- Deprecated the hard mandate of "All sensitive data must be encrypted with AES-256-GCM".
- Formally established the **Multi-Mode Symmetric Encryption Strategy** based on modern Android best practices:
  1. **AES-256-GCM** MUST be used for general payload encryption (files, credential blobs, value storage) to support hardware keystore offloading and streaming without memory exhaustion.
  2. **AES-256-SIV (Synthetic IV)** MUST be used for **Searchable Encrypted Metadata** (e.g., database lookup tags, category names) requiring deterministic ciphertext, and for **Key Wrapping** where nonce-misuse resistance is paramount.

### BRD Update
- Updated `NFR-SEC-010` to reflect the adoption of the multi-mode strategy (AES-256-GCM for payloads, AES-256-SIV for metadata).

## Rationale
A comprehensive evaluation was conducted (`docs/research/AES_SIV_vs_GCM_Evaluation.md`) to determine if the project should transition entirely to an AES-SIV architecture for maximum "misuse resistance." 

The evaluation concluded that an exclusive transition to AES-SIV is detrimental to Android app health because:
1. Android Keystore natively supports hardware-accelerated GCM but not SIV.
2. SIV requires loading the entire plaintext into memory before encrypting (two-pass), risking OOMs on large attachments.
3. Jetpack Security (`EncryptedSharedPreferences`) heavily relies on a hybrid GCM/SIV approach.

Thus, the Constitution now mandates the multi-mode approach as the official project standard, rather than just an exception to the rule.

# Evaluation: AES-256-SIV vs. AES-256-GCM

## Overview
This document evaluates the cryptographic choice between **AES-256-GCM** (the current standard for payloads) and **AES-256-SIV** (used for deterministic lookups), specifically addressing whether Chimali could and should transition to a SIV-only architecture, independent of any prior internal constitutional mandates.

---

## 1. Technical Comparison

| Feature | AES-256-GCM (Galois/Counter Mode) | AES-256-SIV (Synthetic IV) |
| :--- | :--- | :--- |
| **Category** | Online Authenticated Encryption (AEAD) | Misuse-Resistant AEAD |
| **Security Risk** | **Catastrophic on Nonce Reuse**: Reusing a nonce with the same key allows an attacker to recover the keystream and forge tags. | **Resistant to Nonce Reuse**: If a nonce is reused, it only reveals if the *same* message was encrypted. No key or keystream is compromised. |
| **Determinism** | **Probabilistic**: Same plaintext + same key produces different ciphertext (via random IV). | **Deterministic**: Same plaintext + same key + same nonce produces the same ciphertext. |
| **Process** | **One-pass / Streaming**: Can encrypt data as it streams in, without knowing the total size. | **Two-pass**: Must read the entire message to calculate the MAC *before* encrypting. |
| **Performance** | **High**: Fast, parallelizable, and benefits from hardware acceleration (AES-NI / ARM NEON). | **Medium**: Roughly 1.5x–2.2x slower than GCM due to the two-pass requirement. |
| **Use Case** | General purpose large-payload encryption. | Searchable encryption, small keys, and environments where unique nonces are hard to guarantee. |

---

## 2. Evaluation: A SIV-Only Architecture

If we assume the project has no fixed requirement for GCM, we evaluate the question: **Is it possible and advisable to exclusively use AES-256-SIV across the entire application?**

### 2.1 Is it Possible?
**Partially.**
1. **Custom Code Implementation**: Yes. Since the project uses `Bouncy Castle 1.80`, all manual cryptographic operations (`AesEncryptionManager`, `CredentialEncryptionService`) could be rewritten to use `AES/SIV/NoPadding`.
2. **Third-Party Library Constraints**: **No.** Google's Jetpack Security (`EncryptedSharedPreferences`) hardcodes the use of AES-256-GCM for preference values and SIV for keys. To achieve a 100% SIV-only architecture, we would have to abandon this official, heavily audited library and maintain a custom secure preference implementation.

### 2.2 Is it Advisable?
**Recommendation: NO to a total "SIV-only" switch. YES to a Hybrid approach.**

Even on pure cryptographic and architectural merits, an exclusive SIV approach introduces detrimental trade-offs for an Android application:

#### The Disadvantages of SIV-Only:
1. **Hardware Keystore Incompatibility**: Android's hardware-backed KeyStore (StrongBox/TEE) natively supports `AES/GCM/NoPadding` for Authenticated Encryption, allowing encryption to happen entirely within the secure hardware without the key ever entering the app's RAM. Android Keystore **does not natively support AES-SIV**. A SIV-only architecture forces all keys to reside in software memory (RAM) and rely on Bouncy Castle, preventing the app from leveraging true hardware-bound payload encryption.
2. **Loss of Streaming Capability (OOM Risks)**: Because AES-SIV requires parsing the entire plaintext before encrypting, it breaks streaming. For small strings (passwords), this is fine. For large files (e.g., Vault document attachments or database backup exports), the entire file must be loaded into memory, creating a severe risk of `OutOfMemoryError` on mobile devices.
3. **Performance Overhead**: While the speed difference is negligible for a single FIDO2 credential, the two-pass delay compounds during bulk operations (e.g., unlocking a vault with 10,000 items or processing large backup blobs).

#### The Advantages of SIV (Where it Shines):
1. **Unforgiving Misuse Resistance**: SIV is immune to catastrophic nonce-reuse vulnerabilities, eliminating a massive class of developer error.
2. **Searchable Encrypted Data**: SIV provides deterministic encryption (when the nonce is omitted or static). This allows querying the database for exact metadata matches (e.g., searching for a Vault category or tag) without decrypting the dataset.

---

## 3. Recommended Best Practices (Revised Architecture)

Reflecting broader industry best practices (including the design of Google's Tink and Jetpack Security), the most robust approach avoids a "one-size-fits-all" algorithm but does not require custom cryptographic implementations when platform primitives suffice:

1. **Use AES-256-GCM for General Payloads, Encrypted Values & Streaming**: File attachments, large blobs, database exports, and encrypted metadata values. Ensure strict nonce management using `SecureRandom` 96-bit IVs. This allows for Hardware Keystore offloading and fast streaming.
2. **Use HMAC-SHA-256/512 for Searchable Metadata**: Replace deterministic AES-SIV with keyed HMAC blind indexes for exact-match database lookups. This achieves determinism without unsafe AES-GCM nonce reuse or requiring an external SIV implementation.
3. **Use Platform-Backed AES-GCM/AEAD for Key Wrapping**: Wrapping smaller key material should utilize Android Keystore-backed AES-GCM with unique nonces and associated data, rather than requiring AES-SIV.

> [!NOTE]
> This evaluation has been updated. The previous recommendation mandated AES-SIV for searchable metadata and key wrapping. The current strategy deprecates AES-SIV in favor of HMAC blind indexes and platform-backed AES-GCM to reduce custom cryptographic surface area and leverage hardware-backed primitives more fully.

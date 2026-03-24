# Key Derivation Comparison: DiceKeys Seeding WebAuthn vs. Chimali HDK

This document provides a comparative analysis between the [dicekeys/seeding-webauthn](https://github.com/dicekeys/seeding-webauthn) specification and the current in-house HDK implementation used in Chimali.

## Overview of Implementations

### DiceKeys Seeding WebAuthN
- **Core Concept**: A fully **stateless** authenticator design. The authenticator only stores a single 32-byte `seedKey`.
- **Credential ID Structure**: The `credentialId` generated during `MakeCredential` encapsulates all required data: `version || uniqueId || extState || credentialMac`. The `credentialMac` uniquely ties the credential to the RP ID.
- **Key Derivation (GetAssertion)**: The private key is essentially a pseudo-random value deterministically derived. It relies on taking the HMAC of the `seedKey` and the `credentialMac` to generate candidates until a valid scalar is found (`cPlusOne < p`).
- **Sign Counts**: Mandates **no signature counter** locally, making authenticators effortlessly cloneable since there is no state to sync.

### Chimali HDK Implementation (`Fido2CryptoService.kt`)
- **Core Concept**: A deterministically derived, **partially stateful** design. It relies on a BIP39 Master Seed.
- **Derivation Path**: Uses an HDK mechanism based on a root device key pair and the master seed. The derivation path for a credential is `[FIDO2_APP_INDEX, stableHashIndex(credentialId)]`, where the second tier index is a 31-bit integer derived from `SHA-256(credentialId)`.
- **Key Derivation**: Keys are derived in-memory on demand by blinding the device private key with a derived blinding factor (`sk' = sk * bf mod n`). This is cryptographically secure and prevents keeping the per-credential private keys on disk.
- **Storage**: Chimali stores the uncompressed public key and credential metadata (like alias, RP IDs) locally via `CredentialStorageService`.

---

## Objective Comparison

Is one objectively "better" than the other? It depends heavily on the project's requirements regarding statelessness and compliance.

### 1. Statelessness vs. Stateful Metadata
- **DiceKeys** is strictly stateless. By pushing all necessary derivation entropy (`seedKey` + `credentialMac`) and external state to the `credentialId` (which is stored by the Relying Party), the Authenticator needs no database.
- **Chimali** derives the private key statelessly (via HDK), but continues to act as a *stateful* authenticator by storing public keys and metadata.
  - *Advantage Chimali:* Allows for features like Discoverable Credentials (Resident Keys), showing a list of accounts to the user, and tracking per-credential signature counters for better RP trust.
  - *Advantage DiceKeys:* Infinite storage capacity and trivial device replacement/cloning.

### 2. Standard Compliance & Compatibility
- **DiceKeys Key Generation**: Uses a standard rejection-sampling method (NIST FIPS 186-4) from an HMAC-DRBG-like stream, making the key mathematically standard.
- **WebAuthn Features**: DiceKeys specifically lacks state, which complicates WebAuthn Level 2+ features like Resident Keys (where the authenticator must return a user without being provided a `credentialId`). Chimali's stateful nature makes Resident Keys possible.

### 3. Security & Linkability
- **DiceKeys**: Warns that embedding `extState` inside the `credentialId` (sent in plaintext to the RP) can lead to user linkability if the `extState` isn't globally uniform.
- **Chimali**: Private keys are deterministically generated, blinding the device root private key. Chimali's `credentialId` doesn't need to wrap extensive data payloads or MACs.

### Summary
**Chimali's implementation is objectively better suited for a modern FIDO2 software authenticator (Wallet)** because it balances deterministic private key recovery (satisfying backup/restore requirements via BIP39) with the ability to store local metadata, which is critical for Resident Keys and managing user profiles. The DiceKeys spec is highly optimized for hardware security modules with severely constrained memory where storing credential metadata is impossible.

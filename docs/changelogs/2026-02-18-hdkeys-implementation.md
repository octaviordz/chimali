# Changelog: HDKeys Implementation

**Date**: 2026-02-18
**Version**: v0.1.0-hdkeys
**Description**: Full implementation of Hierarchical Deterministic Keys (HDKeys) based on IETF draft-dijkhuis-cfrg-hdkeys-06.

---

## 🚀 Changes Summary

### 🔑 Security Architecture
- **Protocol**: Implemented the **HDK-ECDH-P256** recommended instantiation.
- **Privacy**: Keys are now derived with **multiplicative blinding**, making child public keys linkable to the parent only with the corresponding salt/blinding factor.
- **Standards**: Leveraged **RFC 9380** (Hashing to Elliptic Curves) and **RFC 9180** (HPKE - DHKEM).

### 🛠️ Infrastructure & Dependencies
- **Crypto Library**: Added **Bouncy Castle** (`bcprov-jdk18on`) via the Gradle version catalog for NIST P-256 group arithmetic.
- **Cleanup**: Entirely removed the legacy BIP-32 implementation (`Bip32HDKeyDerivator`, `HDKeyDerivator`).
- **Reference**: Saved the tectonic-labs/bedrock Rust implementation to `docs/reference-hhd-bedrock.rs`.

## 🏗️ Technical Architecture

The new implementation lives in the `core/security` module under `com.chimali.core.security.hdkeys`:

1.  **P256Group.kt**: Raw EC primitives (Scalar mult, point addition, keygen, ECDH shared secret).
2.  **HashToScalar.kt**: `expand_message_xmd` and `hash_to_field` implementation per RFC 9380.
3.  **DhKem.kt**: KEM operations (Encap/Decap/DeriveKeyPair) per RFC 9180.
4.  **MultiplicativeBlinding.kt**: Blinding logic for public/private keys and shared secrets.
5.  **HdkEcdhP256.kt**: The main implementation of the `HdkManager` interface.

## 🧪 Verification Results

Successfully passed **31 new unit tests** covering all components against official RFC test vectors and reference implementation cases.

| Component | Passed Tests |
|---|---|
| HdkEcdhP256 (Main) | 10 |
| P256Group (Primitives) | 7 |
| DhKem (RFC 9180) | 4 |
| HashToScalar (RFC 9380) | 6 |
| MultiplicativeBlinding | 4 |
| **Total HDKeys Tests** | **31** |

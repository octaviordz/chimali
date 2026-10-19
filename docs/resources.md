# Chimali - Project Resources & References

- **MFA Fatigue Considerations**: [Allthenticate Knowledge Base](https://www.allthenticate.com/knowledge/mfafatigue).
- **Master Key & Rescue Missions**: [Google Share Link](https://share.google/aimode/nLv21SkunZSpz8a9v) - Advanced cryptographic patterns for key management and recovery.

This file contains links, tools, and research materials that are useful for the development of Chimali but are not direct business requirements.

## WebAuthn & FIDO2
- **WebAuthn Level 3**: [Official W3C Recommendation](https://www.w3.org/TR/webauthn-3/) - The core specification for Web Authentication Level 3, defining the interface for creating and using public key-based credentials.
- **Awesome WebAuthn**: [GitHub Repository](https://github.com/yackermann/awesome-webauthn) - A curated list of awesome WebAuthn resources, libraries, and tools.
- **OpenSK**: [GitHub Repository](https://github.com/google/OpenSK) - Google's reference implementation of a FIDO2 authenticator written in Rust that supports CTAP 2.1.
- **Trussed FIDO Authenticator (ctap2.rs)**: [Source File](https://github.com/trussed-dev/fido-authenticator/blob/main/src/ctap2.rs) - Reference implementation of CTAP2 command handlers and response builders within the Trussed framework.
- **FIDOk**: [GitHub Repository](https://github.com/BryanJacobs/FIDOk) - Kotlin/Multiplatform FIDO Platform implementation suitable for use in many contexts.
- **FIDO2 Bridge**: [GitHub Repository](https://github.com/token2/FIDO2_Bridge) - Passkey credential provider for hardware security keys by Token2.
- **FIDO Allowed Cryptography List**: [Official Specification](https://fidoalliance.org/specs/fido-security-requirements-v1.0-fd-20170524/fido-authenticator-allowed-cryptography-list_20170524.html) - The official list of cryptographic algorithms and parameters allowed for use in FIDO authenticators.

## Testing & Compliance
- **FIDO2/U2F Open Source Test Suites**: [Reddit Discussion](https://www.reddit.com/r/yubikey/comments/1nkhgrq/good_open_source_test_suite_for_fido2u2f/) - Community discussion on the lack of comprehensive open-source FIDO2 test suites and recommendations for building custom test environments using `libfido2`.
- **WebAuthn.io**: [Testing Tool](https://webauthn.io/) - A demo site for testing WebAuthn registration and authentication flows.
- **Passkeys.dev**: [Developer Resources](https://passkeys.dev/) - A site with resources for developers to learn about passkeys, including test environments.

## Bluetooth & HID
- **USBHIDTerminal**: [GitHub Repository](https://github.com/452/USBHIDTerminal) - Useful for testing HID communications and understanding endpoint interactions.
- **HidPeripheral**: [GitHub Repository](https://github.com/LiangLuDev/HidPeripheral) - Android app demo for Bluetooth HID peripheral emulation (Mouse/Keyboard).

## Security Frameworks & Hardware
- **Trussed TOTP Tutorial**: [GitHub Repository](https://github.com/trussed-dev/trussed-totp-pc-tutorial) - A comprehensive tutorial for building Trussed-based security applications, with an example implementation of TOTP. Useful for understanding modular security architectures.

## Decentralized Identity & SSI
- **walt.id Identity SDK**: [GitHub Repository](https://github.com/walt-id/waltid-identity) - Open-source Decentralized Identity (SSI) and Wallet solutions for developers.
- **EU Digital Identity Wallet (EUDI)**:
  - [Repositories List](https://github.com/eu-digital-identity-wallet/.github/blob/main/profile/repositories-list.md) - Official overview of EUDI repositories.
  - [Android Wallet UI](https://github.com/eu-digital-identity-wallet/eudi-app-android-wallet-ui) - Reference implementation for the EU Wallet Android app.
  - [Android Wallet Core](https://github.com/eu-digital-identity-wallet/eudi-lib-android-wallet-core) - Core library coordinating identity/credential flows in EUDI.

## Synchronization & Local-First
- **Loro**: [GitHub Repository](https://github.com/loro-dev/loro) - High-performance CRDT library for local-first and collaborative applications, supporting JSON-like data with built-in version control.

## Cryptography & HD Keys
- **HD Keys beyond Bitcoin**: [Google Share Link](https://share.google/aimode/7zIX8QRB2KWH6asT3) - Discussion on the broader applications of Hierarchical Deterministic keys.
- **Hybrid Hierarchical Deterministic Derivation (HHD)**: [Blogpost](https://hackmd.io/abYfydDxRMGkwguLiAqVbg) - A proposal for a hybrid derivation design supporting both classical (ECDSA) and quantum-resistant (Falcon) signature schemes from a single seed.
  - **Current local-profile construction references**:
    - [Hierarchical Deterministic Keys, draft-dijkhuis-hdk-00](https://datatracker.ietf.org/doc/draft-dijkhuis-hdk/00/) - Active Internet-Draft describing an OpenID4VCI issuance extension and P-256 child-key relation. Chimali's proposed profile adapts the child arithmetic with deterministic local inputs; it does not implement the draft's ephemeral X25519 issuance exchange, secure-area parent model, or issuer workflow. The draft is work in progress, not an IETF standard.
    - [RFC 5869: HKDF](https://www.rfc-editor.org/rfc/rfc5869.html) - Extract-and-expand key derivation used by Chimali's local profile.
    - [RFC 9180: HPKE](https://www.rfc-editor.org/rfc/rfc9180.html) - The local profile uses DHKEM(P-256, HKDF-SHA256) deterministic key generation for its software parent; this use does not add HPKE issuance, encapsulation, or decapsulation.
    - [RFC 8032: Edwards-Curve Digital Signature Algorithm (EdDSA)](https://www.rfc-editor.org/rfc/rfc8032.html) - Ed25519 seed/key and signature reference for the independent Ed25519 credential branch.
    - [NIST FIPS 204: Module-Lattice-Based Digital Signature Standard](https://csrc.nist.gov/pubs/fips/204/final) - ML-DSA standard for the independent ML-DSA-65 credential branch.
    - [BIP 39: Mnemonic code for generating deterministic keys](https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki) - Mnemonic and seed-generation reference.
  - **Historical HDK draft**: [draft-dijkhuis-cfrg-hdkeys-06](https://datatracker.ietf.org/doc/html/draft-dijkhuis-cfrg-hdkeys-06) - Retained as historical background only; it is not Chimali's current local derivation requirement.
  - **Separate issuance research**: [OpenID4VCI and remote HDK issuance investigation](./backlog/openid4vci-hdk-issuance-investigation.md) - Future research only; it does not imply support in the current local profile.
- **DiceKeys Seeding WebAuthn vs. Chimali HDK**: [Comparison Document](./research/key_derivation_comparison_dicekeys_seeding.md) - Analysis comparing external stateless key derivation with Chimali's HDK approach.

## Backup & Recovery (Future Scope)
- **Shamir's Secret Sharing (Rust)**: [sss-rs GitHub](https://github.com/dsprenkels/sss-rs) - Practical implementation of Shamir's secret sharing.
- **Rescue Missions / Secret Sharing**: [Allthenticate Rescue Mission](https://www.allthenticate.com/why-allthenticate#rescue-mission) - Explanation of its use in recovery scenarios.

## User Interface & Accessibility
- **Atkinson Hyperlegible Font**: [Braille Institute](https://brailleinstitute.org/atkinson-hyperlegible-font) - A font designed specifically to improve character recognition and legibility for low-vision readers, ideal for distinguishing ambiguous characters in passwords (ref: FR-UI-010).

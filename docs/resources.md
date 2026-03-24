# Chimali - Project Resources & References

- **MFA Fatigue Considerations**: [Allthenticate Knowledge Base](https://www.allthenticate.com/knowledge/mfafatigue).
- **Master Key & Rescue Missions**: [Google Share Link](https://share.google/aimode/nLv21SkunZSpz8a9v) - Advanced cryptographic patterns for key management and recovery.

This file contains links, tools, and research materials that are useful for the development of Chimali but are not direct business requirements.

## WebAuthn & FIDO2
- **Awesome WebAuthn**: [GitHub Repository](https://github.com/yackermann/awesome-webauthn) - A curated list of awesome WebAuthn resources, libraries, and tools.

## Bluetooth & HID
- **USBHIDTerminal**: [GitHub Repository](https://github.com/452/USBHIDTerminal) - Useful for testing HID communications and understanding endpoint interactions.
- **HidPeripheral**: [GitHub Repository](https://github.com/LiangLuDev/HidPeripheral) - Android app demo for Bluetooth HID peripheral emulation (Mouse/Keyboard).

## Decentralized Identity & SSI
- **walt.id Identity SDK**: [GitHub Repository](https://github.com/walt-id/waltid-identity) - Open-source Decentralized Identity (SSI) and Wallet solutions for developers.
- **EU Digital Identity Wallet (EUDI)**:
  - [Repositories List](https://github.com/eu-digital-identity-wallet/.github/blob/main/profile/repositories-list.md) - Official overview of EUDI repositories.
  - [Android Wallet UI](https://github.com/eu-digital-identity-wallet/eudi-app-android-wallet-ui) - Reference implementation for the EU Wallet Android app.
  - [Android Wallet Core](https://github.com/eu-digital-identity-wallet/eudi-lib-android-wallet-core) - Core library coordinating identity/credential flows in EUDI.

## Cryptography & HD Keys
- **HD Keys beyond Bitcoin**: [Google Share Link](https://share.google/aimode/7zIX8QRB2KWH6asT3) - Discussion on the broader applications of Hierarchical Deterministic keys.
- **Hybrid Hierarchical Deterministic Derivation (HHD)**: [Blogpost](https://hackmd.io/abYfydDxRMGkwguLiAqVbg) - A proposal for a hybrid derivation design supporting both classical (ECDSA) and quantum-resistant (Falcon) signature schemes from a single seed.
- **Deterministic Key Derivation for Ed25519 and Ed448**: [IETF Draft](https://www.ietf.org/archive/id/draft-dijkhuis-cfrg-hdkeys-06.html) - Research on applying HD key patterns to modern curves like Ed25519.
- **DiceKeys Seeding WebAuthn vs. Chimali HDK**: [Comparison Document](./research/key_derivation_comparison_dicekeys_seeding.md) - Analysis comparing external stateless key derivation with Chimali's HDK approach.

## Backup & Recovery (Future Scope)
- **Shamir's Secret Sharing (Rust)**: [sss-rs GitHub](https://github.com/dsprenkels/sss-rs) - Practical implementation of Shamir's secret sharing.
- **Rescue Missions / Secret Sharing**: [Allthenticate Rescue Mission](https://www.allthenticate.com/why-allthenticate#rescue-mission) - Explanation of its use in recovery scenarios.

## User Interface & Accessibility
- **Atkinson Hyperlegible Font**: [Braille Institute](https://brailleinstitute.org/atkinson-hyperlegible-font) - A font designed specifically to improve character recognition and legibility for low-vision readers, ideal for distinguishing ambiguous characters in passwords (ref: FR-UI-010).

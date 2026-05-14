# Research & Reference Resources

This document serves as a collection of external resources, implementations, and research notes related to FIDO2, Passkeys, Key Management, and Digital Wallets.

## 1. Passkeys & WebAuthn Security
*   [Your Passkey is Weak](https://yourpasskeyisweak.com/) - Exploration of passkey security considerations.
*   [Yubico: WebAuthn Protocol Extension for Backup Security Keys](https://www.yubico.com/blog/yubico-proposes-webauthn-protocol-extension-to-simplify-backup-security-keys/) - Proposal to simplify backup keys.
*   [Hacker News Discussion: Yubico Proposal](https://news.ycombinator.com/item?id=32881956) - Community discussion on the backup key extension.

## 2. FIDO2 / WebAuthn Implementations & Testing
*   [Duo Labs: Android WebAuthn Authenticator](https://github.com/duo-labs/android-webauthn-authenticator) - Reference implementation of an authenticator on Android.
*   [Trussed: FIDO2 Tests (Commit bb11708)](https://github.com/trussed-dev/fido2-tests/commit/bb11708f388191955cd6a6674f741930d7a5e37a) - FIDO2 testing suite updates.
*   [Trussed: FIDO2 Tests (Commit 5f180ab)](https://github.com/trussed-dev/fido2-tests/commit/5f180ab1ff0cf1422147aefad946d7dc0698e565) - Further testing suite improvements.
*   [Trussed: FIDO Authenticator - Credential Count Search](https://github.com/search?q=repo%3Atrussed-dev/fido-authenticator%20MAX_CREDENTIAL_COUNT_IN_LIST&type=code) - Code reference for credential limits.
*   [Trussed: FIDO Authenticator - CTAP2 Source](https://github.com/trussed-dev/fido-authenticator/blob/main/src/ctap2.rs) - Rust implementation of CTAP2 logic.
*   [Trussed: FIDO Authenticator - Credential Source](https://github.com/trussed-dev/fido-authenticator/blob/main/src/credential.rs) - Rust implementation of credential management.
*   [Trussed: FIDO Authenticator - Main Source Tree](https://github.com/trussed-dev/fido-authenticator/tree/main/src) - Core logic for the Trussed FIDO authenticator.

## 3. Key Management, Wallets & Secret Sharing
*   [Privy: Shamir Secret Sharing Library](https://github.com/privy-io/shamir-secret-sharing) - TypeScript implementation of SSS.
*   [Privy Blog: Embedded Wallet Architecture Breakdown](https://privy.io/blog/embedded-wallet-architecture-breakdown) - Technical overview of embedded wallet design.
*   [Privy Blog: Shamir Secret Sharing Deep Dive](https://privy.io/blog/shamir-secret-sharing-deep-dive) - Conceptual explanation of SSS in wallet context.
*   [Ledger: Hierarchical Deterministic (HD) Wallets](https://www.ledger.com/academy/glossary/hierarchical-deterministic-wallet) - Glossary entry on HD wallet architecture (BIP32/44).
*   [Research: HD Keys Beyond Bitcoin](https://www.google.com/search?q=I+know+Hierarchical+Deterministic+keys+are+used+in+bitcoin+however+is+that+only+used+there+%3F) - Exploring the use of HD key derivation in non-crypto applications.

## 4. Digital Identity & Wallets (EU)
*   [EU Digital Identity Wallet: Open Source Publication](https://ec.europa.eu/digital-building-blocks/sites/spaces/EUDIGITALIDENTITYWALLET/pages/797082387/Open-source+code+of+the+wallet+now+published) - Official announcement and links.
*   [EU Digital Identity Wallet: Android UI](https://github.com/eu-digital-identity-wallet/eudi-app-android-wallet-ui) - Android reference implementation for the EUDI wallet.

## 5. Nearby & Device Discovery
*   [Google Developers: Nearby Fast Pair Specifications](https://developers.google.com/nearby/fast-pair/specifications/introduction) - Documentation for the Fast Pair protocol.

## 6. Other Protocols
*   [Pulse-Java: BEP v1 Implementation](https://github.com/dapperstout/pulse-java/tree/master/src/main/java/pulse/bep/v1) - Java implementation of the Pulse/BEP protocol.

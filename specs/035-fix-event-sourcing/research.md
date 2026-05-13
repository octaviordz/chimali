# Research: fix-event-sourcing

**Feature**: fix-event-sourcing
**Date**: 2026-05-13

## 1. Asset Packaging in KMP Android Libraries
- **Decision**: Add `sourceSets.getByName("main") { assets.srcDirs("src/androidMain/assets") }` to `core/security/build.gradle.kts`.
- **Rationale**: Based on `rca-bip39-asset-loading.md`, the `com.android.kotlin.multiplatform.library` plugin does not automatically discover assets under `src/androidMain/assets/`. It requires explicit configuration within the `android { ... }` block to bundle the BIP39 wordlist into the final APK.
- **Alternatives considered**: Moving the asset to `src/main/assets` (which isn't ideal for a KMP project layout).

## 2. Secure Key Derivation for Event Sourcing
- **Decision**: Implement `EventStoreKeyProvider` that derives a 32-byte key from the BIP39 Master Seed using `HMAC-SHA512` with the label `"chimali_eventstore_v1"`, taking the first 32 bytes of the HMAC output.
- **Rationale**: Follows the existing deterministic derivation pattern used in `WalletMasterSeedProvider`. Ensures that the Vault and FIDO2 aggregates are encrypted using a strong, predictable key tied to the user's root of trust.
- **Alternatives considered**: Using `PBKDF2-SHA512` (unnecessary computationally since the seed is already high entropy).

## 3. Dealing with Dummy-Encrypted Legacy Data
- **Decision**: Truncate (delete all rows) from `EventStore` and `SnapshotStore` (or `Snapshot`) tables in both `VaultDatabase` and `Fido2Database`.
- **Rationale**: The previous implementation used a zero-filled dummy key. Changing the key means existing ciphertext cannot be decrypted. Since this is pre-production (or beta) data with a known security flaw, a clean slate is required.
- **Alternatives considered**: Attempting to migrate data by decrypting with the dummy key and re-encrypting with the new key. Discarded as unnecessary overhead and potential security risk for early-stage development data.

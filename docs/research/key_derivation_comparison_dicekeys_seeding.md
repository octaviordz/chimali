# Key Derivation Comparison: DiceKeys Seeding WebAuthn and Chimali

**Current local profile reviewed**: 2026-10-14
**Status**: Current Chimali claims are stated first. The retained DiceKeys comparison is background, not an interoperability or security certification.

## Current Chimali local profile

Feature 055 defines a clean-install local profile with a full BIP39-to-RFC 5869 root, complete decoded credential-ID binding, and explicit algorithm/profile metadata. Its P-256 branch reuses the child-key arithmetic from [draft-dijkhuis-hdk-00](https://datatracker.ietf.org/doc/draft-dijkhuis-hdk/00/) with deterministic local input: the draft's per-issuance X25519 `shared_secret` is replaced by a credential secret derived from the wallet PRK and complete credential ID. Chimali also proposes deterministic RFC 9180 DHKEM(P-256) parent provisioning. These local choices are not defined by the issuance draft and do not create OpenID4VCI interoperability; the complete composition is subject to independent security review.

Ed25519 and ML-DSA-65 use independent algorithm-specific HKDF seed branches. They are not instances of the P-256 child-key scheme.

Chimali stores credential metadata and public-key projections. Reconstructing an existing credential requires the mnemonic plus retained credential metadata, including full credential ID, algorithm, and exact profile identifier. A mnemonic alone does not discover credentials after metadata loss. The software-derived parent is protected by the application's encrypted-storage and authorization policy; it is not non-exportable secure-area material. Root compromise controls all branches. Label separation separates purposes but does not contain common-root compromise.

These statements describe the accepted local-profile target. Feature implementation, device evidence, and formal review remain incomplete until their corresponding tasks and gates close.

## External DiceKeys comparison retained as background

The earlier comparison described [DiceKeys Seeding WebAuthn](https://github.com/dicekeys/seeding-webauthn) as a design that encodes derivation inputs in credential IDs and derives keys from a seed, while Chimali retains server/RP-independent credential metadata locally. That comparison is conceptual: it does not establish matching protocol behavior, equivalent threat models, issuer interoperability, or that one system is universally more secure. Confirm the referenced project's current version before using detailed implementation claims in product decisions.

The following trade-offs remain useful questions for a product-level comparison:

- **Credential state and discovery**: stateless derivation from a relying-party-supplied credential ID still needs enough authenticated metadata to authorize a credential. Chimali's current profile explicitly retains local credential records and does not promise mnemonic-only enumeration after record loss.
- **Recovery**: deterministic keys do not by themselves recover credential IDs, RP/user metadata, public-key registrations, counters, or other authenticator state. The current Chimali recovery contract requires the mnemonic and credential metadata.
- **Identity and privacy**: full credential-ID binding prevents the retired shortened-index collision behavior. Neither local label separation nor deterministic derivation prevents correlation from other credential, RP, or protocol data.
- **Protection boundary**: a common wallet root simplifies reproducible local derivation but makes root compromise affect all algorithm branches. Different HKDF labels provide purpose separation; they do not create independent compromise domains.
- **Compatibility**: Chimali's local profile is not wire-compatible with the draft's OpenID4VCI exchange or another seed-based credential-ID construction merely because both derive keys deterministically.

## Historical Chimali comparison (pre-feature; superseded)

The preceding version of this document described the retired draft-06 implementation, including a 31-bit `stableHashIndex`, old multiplicative blinding and DeriveSalt behavior, and an assertion that Chimali was “objectively better.” Those are historical statements, not current requirements or verified comparative conclusions. The old hash-index and HDK construction are retired under feature 055; the unqualified superiority claim is withdrawn. See the [current local derivation contract](../../specs/055-migrate-hdk-draft/contracts/local-key-derivation.md), [recovery boundaries](credential_id_recovery_analysis.md), and the separate [OpenID4VCI investigation](../backlog/openid4vci-hdk-issuance-investigation.md).

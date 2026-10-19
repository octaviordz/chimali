# Credential ID and Recovery Boundaries

**Current profile reviewed**: 2026-10-14
**Status**: Current requirements and recovery limits; implementation and activation review remain open.

## Current local profile

The local credential profile is a Chimali application policy. Its P-256 branch uses the child-key arithmetic in [draft-dijkhuis-hdk-00](https://datatracker.ietf.org/doc/draft-dijkhuis-hdk/00/) with different local inputs: the full 64-byte BIP39 seed is extracted through the named RFC 5869 profile, and a credential-specific secret is derived using the complete decoded credential ID. That local secret, the canonical parent public key, and the local 32-bit index form the P-256 child derivation input. The draft's per-issuance X25519 shared secret and issuer workflow are not used. This adaptation is custom protocol composition and remains subject to independent review.

Ed25519 and ML-DSA-65 use separately labeled HKDF-derived 32-byte seeds and their respective signature schemes. They are independent branches, not P-256 HDK instances.

### What recovery requires

Recovery requires both:

1. The approved wallet recovery material (the mnemonic, which produces the full BIP39 seed using the configured empty passphrase); and
2. The credential metadata needed to select and reproduce the credential, including its complete credential ID, supported COSE algorithm, exact derivation profile identifier, and the other stored credential fields required by the authenticator.

A mnemonic alone does not enumerate random credential IDs, RP/user metadata, registered public keys, signature counters, or discoverable credential records. The local profile does not promise credential discovery after losing that metadata. A supplied allow-list ID also does not by itself establish a registered credential or authorize signing; current repository, ownership, algorithm/profile, and public-key checks still apply.

The full decoded ID is a cryptographic derivation input after canonical decoding; text padding variants decode to the same bytes. The profile does not reduce the identity to the retired 31-bit hash index. Distinct IDs that collided under that old shortening must have distinct derivation inputs; this is supported by collision fixtures, not a claim that cryptographic hashes make every output mathematically unique.

### Security and ownership limits

- The P-256 parent is deterministically provisioned in software from the wallet PRK. Encrypted storage protects material at rest under the configured storage policy; it does not make the parent non-exportable hardware key material or inherit secure-area protections from the issuance draft.
- Authorization is enforced by Chimali's operation and session policy. The profile itself does not authenticate a user or establish holder assurance for an issuer.
- Root or recovery-material compromise enables reconstruction of every derived branch. Different labels separate uses but do not contain compromise of the common root.
- A leaked child factor together with its child private scalar permits recovery of the parent scalar by modular inversion. Operation-local ownership and cleanup reduce exposure duration; they do not provide independent child-compromise isolation.
- Provider, JVM, ICU, and `BigInteger` internals can create copies that application code cannot promise to erase physically. Application-owned mutable buffers must still be erased on every terminal path.
- No existing-install migration or old-key compatibility is required for feature 055. The authorized scope is a clean installation; this is not evidence that previous credentials have been converted or preserved.

## Historical pre-feature analysis (2026-03-16; superseded)

The original analysis below described the earlier draft-06 implementation and made recommendations before the feature 055 clean-install scope and current recovery contract were approved. Its conclusions are retained as history and must not be used as current recovery guidance.

- It proposed deterministic credential IDs and stateless retrieval as ways to compensate for missing credential metadata. Those proposals are not part of the approved feature and do not replace the current requirement to retain credential metadata.
- It described the shortened credential-ID path as sufficient for re-derivation. That input mapping is retired; the current profile binds the complete decoded ID through its local derivation contract.
- It noted that a mnemonic by itself does not reconstruct discoverable credential records. This remains true, but current recovery additionally requires the algorithm/profile descriptor and the credential metadata needed by the authenticator.
- Sign-count reset and relying-party policy remain credential lifecycle questions; the local derivation profile does not promise that resetting state is accepted by every relying party.

See the current [local derivation contract](../../specs/055-migrate-hdk-draft/contracts/local-key-derivation.md), [credential lifecycle contract](../../specs/055-migrate-hdk-draft/contracts/credential-lifecycle.md), and [separate OpenID4VCI investigation](../backlog/openid4vci-hdk-issuance-investigation.md).

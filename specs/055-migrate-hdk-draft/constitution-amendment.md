# Proposed Constitution Amendment: Local HDK Profile

**Prepared**: 2026-10-14  
**Target**: `.specify/memory/constitution.md`  
**Current version**: 2.0.0  
**Proposed version**: 3.0.0 (MAJOR)  
**Status**: Applied to `.specify/memory/constitution.md` on 2026-10-14; final implementation alignment remains a completion gate.

## Rationale and authorization

The current Principle II mandates the retired draft and its salt/XMD/remote-KEM design, and overstates its applicability to independently derived Ed25519/PQ keys. The user requires adopting the replacement's child construction, reducing technical debt, and targeting a clean installation. Existing key outputs, upgrade paths and re-registration workflows are explicitly outside acceptance. The user also confirmed constitution updates are required feature work.

This is a major amendment because it replaces normative key derivation and the recovery-root definition, rather than merely updating a reference. Record the feature direction and accepted design/review disposition in the amendment impact report. Use the actual application date in the constitution's amendment metadata; preserve its ratification date.

## Proposed replacement for Principle II

### II. Master Seed Architecture

The root of trust is a BIP39 recovery mnemonic protected at rest by the application's approved authenticated-encryption storage. Fresh wallet creation MUST use 256 bits of cryptographically secure entropy and the published English wordlist to produce 24 words. Private mnemonic processing MUST use owned mutable storage and validate the required wordlist/checksum before deriving wallet material.

The full BIP39 PBKDF2-HMAC-SHA512 result is a transient 64-byte input. The current wallet root MUST be the 32-byte RFC 5869 HKDF-SHA256 PRK derived by the approved, named and versioned local credential profile. The profile MUST document exact salt/context bytes and encodings, supported algorithms, parent derivation, required recovery metadata, and independent expected results. Full BIP39 working material MUST be erased after root extraction and required provisioning.

P-256 credential keys MUST use a named local profile that adapts the scalar mapping and child-key relation in Section 7 of `draft-dijkhuis-hdk-00`, substituting a separately specified local RFC 5869 credential secret for the draft's per-issuance X25519 shared secret. This is custom local composition, not the draft's issuance flow or an interoperability claim. Parent generation MUST follow the profile's published deterministic P-256 key-generation algorithm. Credential identity MUST bind the complete canonical decoded credential identifier; shortened identifier hashes and obsolete HDK path/salt/handle representations MUST NOT be part of the current profile.

Ed25519 and ML-DSA MUST use independently specified, algorithm-separated seed derivation and published key-generation/signature algorithms. They MUST NOT be described as P-256 HDK instances. All active operations MUST use the one current profile defined for their algorithm; unsupported algorithm/profile pairs MUST fail securely. Legacy compatibility implementations and speculative profile registries MUST NOT be added without a separate approved requirement.

The parent signing key in this profile is software-derived. Android Keystore protection of encrypted recovery data MUST NOT be described as making that signing scalar non-exportable. Child operations MUST preserve the documented parent profile's authorization policy. The application MUST NOT claim OpenID4VCI issuance, issuer assurance or secure-hardware child operation conformance from local derivation alone.

The provider MUST own separate cached buffers, publish initialized state atomically, and return operation-owned secret copies or an equally reviewed scoped interface. Owners MUST erase replaced and terminal secret buffers on success, error, cancellation, session end and required memory-pressure response. A private child key or per-credential seed MUST never be persisted. Plaintext mnemonic, seed, root, factor and private-key storage MUST comply with Principle I.5 without a cryptographic UI exception. Dependency-internal copy limits MUST be documented and reviewed; dropping references is not physical erasure.

Recovery reproduces a current credential only when supplied with the approved root/recovery inputs and required current credential metadata. A mnemonic alone MUST NOT imply account discovery or restoration of missing metadata.

Feature 055 establishes the current profile for a clean new installation of unreleased Chimali. It does not require retention or conversion of prior credential/database/event formats. Record that feature-specific user-authorized scope when applying current schema changes; it does not introduce general exemptions for future published formats.

## Related ES-V.5 clarification

Replace the blanket randomness sentence with:

> Security-sensitive randomness, including fresh nonces and IVs, MUST use `java.security.SecureRandom`. A fixed public salt or context required or permitted by a published deterministic KDF MAY be used only as a public domain-separation input through an approved, named, versioned derivation profile with documented byte encoding and domain separation. This exception does not authorize treating the salt/context as secret input, nonce reuse, or replacement of randomness where the governing construction requires fresh random values.

Keep the existing no-cryptographic-invention, constant-time comparison, cleanup, and mutable-ownership obligations. The local composition is documented in [the contract](contracts/local-key-derivation.md) and its review gate in [security-review.md](security-review.md); primary-source references and reproducible implementation evidence are required.

## Synchronization and metadata

- Set constitution version to **3.0.0** and amendment date to the actual day the amendment is applied.
- Update the leading impact report with replaced Principle II, deterministic-KDF clarification, clean-install scope, and affected current requirements/documents.
- Update BRD/TRD master-key and remote-provisioning statements to the accepted current local design. Remove obsolete normative salt/XMD/KEM obligations and false full-draft/PQ/hardware claims.
- Add the stable current local-HDK requirement ID used by Tier 1 annotations and tests; retain historical requirements only with explicit supersession semantics.
- Update resources, recovery guidance and current cryptographic KDoc. Preserve historical changelog/spec meaning.
- Review the amended text against implemented behavior and SC-010 before completing the feature.

## Completion evidence

The implementation record must identify the applied constitution version/date, amendment rationale and authorization, profile review disposition, synchronized BRD/TRD references, and checks showing no old normative obligation controls current derivation. Preparing this proposal satisfies planning; it does not mean the constitution amendment or security review has been executed.

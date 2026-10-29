# Feature Specification: Migrate Chimali's Local HDK Profile to the Replacement Draft

**Feature Branch**: `codex/055-migrate-hdk-draft`

**Created**: 2026-10-14

**Current performance disposition (2026-10-29)**: Physical T084 is superseded by Feature 060's host-simulated internal-operation assessment; T085 attribution profiling and optimization remain deferred. The declared Feature-060 run measured ten occurrences each of MakeCredential, GetAssertion, GetInfo and ResetAuthenticator; all 40 were below 200 ms, meeting SC-007 for the declared workload. See [Feature-060 evidence](../060-fido2-latency-measurement/implementation-evidence.md).

**Complexity estimate**: L — one bounded feature spanning existing cryptography, seed ownership, credential persistence, validation, and constitution/documentation work; no new protocol or Gradle module.

**Input**: User description: "Analyze the new document and Chimali source code, which is based on IETF draft-dijkhuis-cfrg-hdkeys-06, and specify the source code and documentation updates needed for its replacement draft-dijkhuis-hdk-00."

**Scope decision**: On 2026-10-14 the user selected the local key derivation and documentation migration and requested a separate `docs/backlog` item to investigate OpenID4VCI. Chimali has no stable releases; breaking derivation changes are authorized. This feature assumes a clean new installation, with no requirements for existing passkeys or upgrading an older installation.

## Clarifications

### Session 2026-10-14

- Q: Must this migration keep existing passkeys working after upgrade? → A: No. Chimali has no stable releases. Proceed with breaking changes needed to adopt `draft-dijkhuis-hdk-00`; legacy key compatibility and passkey preservation are not requirements. Keep the agreed local scope and the separate OpenID4VCI investigation.
- Q: How should this migration address technical debt? → A: Reduce it by removing obsolete old-draft implementations, compatibility layers, unused APIs, obsolete contract tests, and stale current documentation. Keep one current local P-256 derivation contract and retain shared primitives only for demonstrated current uses; this is scoped to the HDK migration.
- Q: Must this feature handle incompatible pre-release credentials? → A: No. Assume a clean new installation of Chimali. Remove the incompatible-credential story and all upgrade, invalidation, re-registration, and old-record transition requirements.
- Q: Are constitution updates included in this feature's specification? → A: Yes. Updating Principle II (Master Seed Architecture) and its related conformance statements is a required feature deliverable, including the amendment rationale, constitution version change, and amendment date required by governance.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create and Use Passkeys with the Updated Local Profile (Priority: P1)

As a Chimali user, I can register P-256 passkeys under the updated local derivation profile and use them reliably after restarting the application.

**Why this priority**: Adopting the replacement construction is the primary feature goal. Chimali has no stable releases, so old derivation compatibility must not constrain that adoption.

**Independent Test**: Start with a clean installation, initialize the wallet, register a P-256 credential, verify its registration signature, restart Chimali, and verify multiple assertions against the same public key. Repeat with equivalent credential identifier encodings and reconstructed wallet inputs; confirm new Ed25519 and ML-DSA credentials also register and authenticate correctly.

**Acceptance Scenarios**:

1. **Given** the updated application and an unlocked wallet, **When** a P-256 passkey is created, **Then** Chimali uses its explicitly identified local profile, which adapts the replacement draft's Section 7 scalar mapping and child-key relation with the contract's deterministic local secret and parent inputs, and retains the information needed to reproduce that key.
2. **Given** a new passkey, **When** its registration public key and self-attestation signature are produced, **Then** both use the same derivation profile and key.
3. **Given** a passkey created under the updated profile, **When** it is used after an application restart, **Then** its assertion verifies against the public key registered for that credential.
4. **Given** the same approved wallet recovery material and required metadata for an updated-profile credential, **When** the wallet is reconstructed, **Then** the credential reproduces its original public key.
5. **Given** two distinct credential identifiers that collide under the old shortened identifier mapping, **When** updated-profile passkeys are created for them, **Then** they do not collapse to the same derivation identity.
6. **Given** newly created Ed25519 or ML-DSA credentials, **When** they are registered and used under their documented independent schemes, **Then** their registration and assertion results verify correctly.

---

### User Story 2 - Understand the Implemented Security and Standards Scope (Priority: P2)

As a maintainer or security reviewer, I can determine which draft Chimali follows, where the local profile differs from the issuance protocol, and which protection and recovery guarantees are actually implemented.

**Why this priority**: A replacement-draft link alone would conceal substantial behavior changes and overstate interoperability and parent-key protection.

**Independent Test**: Review the current requirements, constitution, resource references, developer documentation, and source comments against the updated profile and migration analysis. Each current conformance claim must identify its actual scope and the permitted breaking change.

**Acceptance Scenarios**:

1. **Given** the completed migration, **When** a maintainer reads current HDK documentation, **Then** it identifies the replacement draft, the local adaptation, the clean-install scope, and the separately defined Ed25519 and ML-DSA branches.
2. **Given** a software-derived signing parent protected through encrypted recovery storage, **When** its security properties are described, **Then** the description distinguishes encryption at rest from a non-exportable signing key and states the implemented operation authorization policy.
3. **Given** historical specifications and changelogs, **When** a reviewer follows old-draft references, **Then** the historical record remains understandable and current documentation points to the migration without presenting old algorithms as current replacement-draft behavior.
4. **Given** this feature's local scope, **When** a maintainer looks for OpenID4VCI work, **Then** a separate investigation item records the unanswered integration questions and expected research deliverables.
5. **Given** implementation elements that exist solely for the retired draft, **When** the migration is completed, **Then** they are removed from active code and validation, and maintainers have one current local P-256 contract with independently justified support for other algorithms.
6. **Given** the constitution currently mandates the older draft, **When** this feature is completed, **Then** Principle II and related conformance statements describe the updated local profile and independent signature schemes, agree with the current requirements and implementation, and record the amendment rationale, version change, and amendment date.

### Edge Cases

- A clean installation has no existing wallet seed or credential metadata.
- A current credential's derivation inputs are missing, corrupted, or inconsistent with its algorithm.
- Registration generates a public key before the credential has been persisted; self-attestation must still use the same profile.
- Reordered loading, application restarts, cache warm-up, or metadata updates must not change derivation selection.
- Two distinct identifiers share the old 32-bit path index; the updated profile must bind the full credential identity.
- Padded and unpadded encodings represent identical credential bytes.
- An invalid P-256 public key, an invalid parent scalar, an empty required derivation input, or a child index outside the specified unsigned range reaches the derivation boundary.
- Derivation at child index 0 (0x00000000) and at the maximum child index (0xFFFFFFFF) both produce valid, distinct, non-zero child keys.
- Cancellation or signing failure occurs while a derived secret or copied private key is in memory.
- Recovery restores a mnemonic but omits credential records or derivation metadata; the migration must not promise account discovery from a mnemonic alone.
- Ed25519 or ML-DSA signing uses the shared recovery provider while the P-256 implementation changes.
- Historical documentation describes pre-migration behavior or older source-set support that is no longer current.
- An old helper has a demonstrated caller in another supported algorithm; cleanup must preserve that use or replace it with an appropriate current primitive rather than delete it blindly.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Chimali MUST define a new, explicitly versioned local P-256 derivation profile based on the child-key construction in sections 3, 7, and 7.1 of `draft-dijkhuis-hdk-00`. Its documented contract MUST distinguish the draft's construction from Chimali's local derivation policy.
- **FR-002**: Every newly created P-256 credential MUST use the updated local profile for generation and signing. Clean-install wallet and storage initialization is specified separately by FR-009.
- **FR-003**: Each usable credential MUST durably store or reproducibly obtain the current derivation inputs needed for registration, self-attestation, authentication, persistence, reconstruction, and any supported recovery operation. Missing or invalid required inputs MUST fail closed.
- **FR-004**: Generation and signing for a credential MUST use the same profile, parent, and credential binding. Profile selection MUST remain stable across restarts and ordinary metadata changes. A failed match MUST NOT be repaired by overwriting a registered public key.
- **FR-005**: The updated local profile MUST reproduce the same key from the same approved recovery inputs and credential metadata. It MUST bind the full credential identifier and isolate its derivation inputs from other uses of the master seed; two identifiers MUST NOT share a derivation solely because they have the same shortened old index.
- **FR-006**: The local profile MUST document which recovery inputs and metadata are required. It MUST NOT imply that encrypted storage, credential discovery, or metadata restoration is provided by the replacement draft.
- **FR-007**: The local P-256 profile MUST preserve the replacement draft's Section 7 scalar mapping (including its documented intentional statistical bias), multiplicative child-key relation, canonical 65-byte uncompressed SEC1 parent-public-key context, and unsigned child-index domain (0 through 2^32-1), while explicitly specifying and reviewing the local input substitution for the draft's per-issuance X25519 shared secret. Retired context labels, salt evolution, hash-to-field scalar mapping, and XMD expansion MUST NOT silently influence updated-profile outputs.
- **FR-008**: Derivation and child-key operations MUST reject malformed or invalid inputs before using them. Non-zero valid parent keys MUST produce non-zero valid child keys. Hierarchical derivation, where exposed, MUST preserve the documented parent-child key relationship.
- **FR-009**: Chimali MUST initialize its wallet and credential storage on a clean installation without requiring any prior installation data, legacy derivation implementation, or migration from an older profile.
- **FR-010**: Chimali MUST continue to support Ed25519 and ML-DSA as independently documented schemes. Their seed derivation MUST remain separated from P-256 blinding, and newly registered credentials MUST generate valid registration and assertion results. Preserving pre-migration key outputs is not required.
- **FR-011**: Retired remote derivation functions MUST NOT be required for the updated local profile. Functions retained for an independently justified purpose MUST be documented with their actual scope; old remote key-handle behavior MUST NOT be presented as implementing the replacement issuance protocol.
- **FR-012**: Child-key use MUST preserve the documented protection and user-authorization policy of its parent profile. Security documentation MUST accurately identify whether the parent is software-derived or protected as a non-exportable key.
- **FR-013**: Migration-touched operations MUST protect derivation secrets, seeds, and private-key working material from persistence, logs, and diagnostics. Owners MUST erase owned mutable working buffers on completion, failure, and cancellation, with documented residual limits for cryptographic dependencies.
- **FR-014**: Current requirements, constitution guidance, developer references, cryptographic comments, and recovery documentation MUST be updated together with the implementation. They MUST distinguish the updated local behavior, authorized breaking changes, separate signature branches, and future remote issuance work. Historical records MUST retain their original meaning.
- **FR-015**: Updating the project constitution MUST be a required deliverable of this feature. Principle II (Master Seed Architecture) and related conformance statements MUST replace the old-draft mandate with the current local P-256 profile, remove obsolete derivation obligations, distinguish Ed25519 and ML-DSA as independent schemes, and accurately state parent-key protection and recovery scope. The amendment MUST reflect the clean-install scope and authorized breaking changes, record its rationale, apply the version change required by governance, and update the amendment date. The constitution MUST agree with the current requirements and implementation before the feature is complete.
- **FR-016**: The migration MUST include independently established expected key results for the updated construction and evidence that clean-install initialization, new registrations, and assertions remain valid across restarts. Retired expectations MUST NOT simply be relabeled as new-draft evidence.
- **FR-017**: Credential candidate selection MUST remain free of bulk key derivation. Authentication MUST select the correct profile without requiring derivation of every candidate's public key.
- **FR-018**: A separate `docs/backlog` investigation item MUST describe OpenID4VCI integration questions, parent-key assurance, credential lifecycle and recovery concerns, security assessment, and the evidence needed to decide a future feature. Implementing the issuance protocol is outside this feature.
- **FR-019**: The migration MUST reduce technical debt by removing implementation elements, compatibility layers, unused capabilities, obsolete contract tests, and stale current guidance that exist solely to support the retired HDK construction. Active P-256 derivation MUST have one current local contract. Shared primitives retained for other supported algorithms MUST have demonstrated current uses; their required security verification MUST remain or be replaced with appropriate current verification.

### Key Entities *(include if feature involves data)*

- **Derivation Profile**: A stable identity describing the updated local P-256 construction and its application policy, distinct from independently specified signature schemes. It does not imply support for retired profiles.
- **Parent Key**: The key from which a P-256 credential key is derived, together with its documented protection and operation authorization properties.
- **Credential Derivation Identity**: The profile and credential binding needed to reproduce a registered key; travels with the credential through supported storage and reconstruction paths.
- **Credential Record**: The identifier, registered public key, algorithm, relying party association, counter, and current derivation identity needed to use an updated-profile credential.
- **Recovery Inputs**: The approved wallet recovery material and necessary credential metadata; neither the private child key nor a promise of stateless account discovery.
- **OpenID4VCI Investigation**: A separate research backlog item establishing the potential scope and dependencies of future remote credential issuance.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of clean-install acceptance cases initialize the wallet and credential storage without prior data and successfully register and use a first credential under the current construction.
- **SC-002**: 100% of newly registered P-256 fixtures use the new local profile and produce verifiable registration and authentication results before and after restart.
- **SC-003**: 100% of updated-profile recovery fixtures supplied with their documented required inputs reproduce their registered public keys. Newly registered independent signature schemes also produce verifiable registration and assertion results.
- **SC-004**: All defined malformed-input, missing-input, and cancellation cases produce the specified safe outcome; none expose owned secret material or silently use different derivation inputs.
- **SC-005**: Every current HDK conformance claim in the agreed documentation inventory identifies its actual implemented scope, with zero claims of completed OpenID4VCI interoperability or unsupported hardware protection.
- **SC-006**: All reviewed new-profile collision fixtures with distinct full identifiers remain distinct despite a shared legacy shortened index.
- **SC-007**: In the host-simulated FIDO2 ceremony defined by BRD FR-VAL-030 and TR-PERF-010, every implemented app-internal FIDO operation, including CTAP `MakeCredential` and `GetAssertion`, is below 200 ms, excluding measured user-verification work and Bluetooth response handoff. Credential candidate listing performs no key derivation. Physical Bluetooth latency is diagnostic only and is not a compliance criterion. **Current evidence (2026-10-29)**: ten measured occurrences each for MakeCredential, GetAssertion, GetInfo and ResetAuthenticator passed below 200ms; SC-007 is met for the declared simulated workload.
- **SC-008**: One linked OpenID4VCI research backlog item exists with concrete questions, dependencies, and completion criteria before this feature is considered complete.
- **SC-009**: The completed dependency and documentation review finds zero active code paths, compatibility layers, or acceptance tests whose sole purpose is the retired HDK construction. Every retained old helper has a documented current use, and all current P-256 operations use the single updated local contract.
- **SC-010**: The completed constitution review finds zero obsolete old-draft obligations governing the current derivation. Principle II agrees with the implemented local profile and independent signature schemes, and the amendment includes its rationale, required version change, and amendment date.

## Assumptions

- The user's explicit choice is a local authenticator migration. Full OpenID4VCI issuance, verifiable credential storage, issuer networking, presentation flows, and a new X25519 protocol exchange are reserved for the separate investigation.
- `draft-dijkhuis-hdk-00` is the pinned work-in-progress reference. A later draft revision is a separate compatibility decision.
- Applying the replacement's child-key construction to local deterministic passkeys requires a Chimali policy for obtaining the derivation secret that replaces the draft's X25519-derived shared_secret. This is the critical design decision for planning. It must be deterministically reproducible from approved recovery inputs, domain-separated from other master seed uses, and must not simulate an issuer exchange or claim protocol-level shared_secret semantics. That policy and its identifier are application decisions and must be fully specified and reviewed during planning.
- The replacement draft's scalar mapping function `H` introduces a small, intentional statistical bias (§11 of draft-dijkhuis-hdk-00: "this specification prefers ease of implementation over eliminating the small statistical bias"). This is an accepted security property of the draft, not a Chimali design decision. The local profile inherits this tradeoff.
- Chimali has no stable releases. The user explicitly authorizes breaking derivation changes, so preserving existing passkeys, root derivation outputs, or PQ seed outputs is not required. Retired P-256 compatibility implementations and dual-profile signing are outside the required scope.
- Acceptance assumes a clean new installation with no pre-existing wallet or passkeys. Upgrading old installations, detecting or invalidating old credentials, re-registration workflows, and converting old databases, events, or snapshots are outside this feature.
- Ed25519 and ML-DSA remain supported through independently documented derivation policies; the replacement draft does not define those schemes.
- Technical debt reduction is part of this HDK migration. It does not expand the feature into unrelated application refactoring or implementing the deferred OpenID4VCI workflow.
- The application retains its current supported platform scope. This feature does not establish functional iOS cryptography or migrate all signing parents into non-exportable hardware storage.
- Required updated-profile recovery metadata must survive supported storage and reconstruction paths. Recovery of incompatible pre-migration keys, complete mnemonic-only account recovery, and a new backup transport are separate from this feature.
- Source analysis, the technical comparison, and the documentation inventory are recorded in [analysis.md](analysis.md). Implementation design belongs in the subsequent plan.
- The constitution amendment is part of this feature's required documentation work. Planning must include it in the implementation sequence and completion criteria. The user's explicit migration direction authorizes the feature scope; the amendment must document that direction and follow the project's versioning process. This clarification records the requirement; the constitution itself will be amended as part of the feature work.
- The requested research item is [Investigate OpenID4VCI and remote HDK issuance](../../docs/backlog/openid4vci-hdk-issuance-investigation.md).



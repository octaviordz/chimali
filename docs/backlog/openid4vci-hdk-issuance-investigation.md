# Backlog: Investigate OpenID4VCI and Remote HDK Credential Issuance

**Status**: Investigation complete — production no-go; feasibility follow-up proposed
**Created**: 2026-10-14  
**Investigated**: 2026-10-23
**Origin**: User-requested follow-up to [055: Local HDK migration](../../specs/055-migrate-hdk-draft/spec.md)  
**Related analysis**: [Replacement draft and source impact](../../specs/055-migrate-hdk-draft/analysis.md)

## Objective

Determine whether Chimali should support receiving verifiable credentials through OpenID4VCI using remote HDK issuance, and define the scope, protections, dependencies, and interoperability evidence needed for a separate implementation feature.

Chimali currently provides local credential storage and FIDO2 passkeys. The agreed feature 055 scope updates its local key profile. This backlog item investigates the additional wallet and issuer workflow; it does not claim that it is implemented.

## Starting references

- [draft-dijkhuis-hdk-00](https://www.ietf.org/archive/id/draft-dijkhuis-hdk-00.html), particularly sections 4–6 and 9–12.
- [HDK Datatracker record](https://datatracker.ietf.org/doc/draft-dijkhuis-hdk/00/).
- [OpenID4VCI 1.0](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0-final.html), referenced by the pinned HDK draft. Review the authoritative text during the investigation.

## Investigation questions

1. **User value and product boundary**: Which credential types and issuers would Chimali support? Would Chimali act only as a receiving wallet? What separate storage, lifecycle, consent, and presentation capabilities would users need?
2. **Protocol integration**: What issuer discovery, authorization, request/response binding, capability negotiation, and error handling are required? How would an `hdk` proof be advertised and processed without treating ordinary P-256 support as HDK support?
3. **Parent-key assurance**: How would the issuer accept the holder association and protection policy of the parent? What is possible with Chimali's software-derived parent, and what would require a non-exportable secure-area key or another assurance mechanism?
4. **Protection and operation support**: Can the intended Android secure area support child-key operations while preserving authorization? Evaluate supported signing and agreement behavior with reproducible evidence, including any relevant intellectual-property considerations identified by the draft.
5. **Exchange and validation**: Define the lifecycle of fresh per-request X25519 keys, rejection of invalid exchange results, canonical public-key encodings, batch indexing, and verification that the received credential is bound to the expected child public key.
6. **Privacy and secret ownership**: Who can correlate parent and child keys? How should issuance secrets be protected, retained only as needed, and erased? Which issuer, credential-content, or network correlation remains?
7. **Recovery and portability**: Which issuance metadata must be backed up alongside recovery material? Can secure-area protection coexist with device replacement? Identify limitations instead of promising mnemonic-only recovery of remotely issued credentials.
8. **Scope and reuse**: Keep future remote issuance distinct from the current local passkey profile. Identify reusable primitives with demonstrated current uses and avoid adding unnecessary abstractions. Runtime support for retired draft-06 KEM handles is not assumed.
9. **Interoperability and maturity**: Confirm the current authoritative draft and its review status at investigation time; identify a compatible issuer, independent vectors, and a reproducible credential issuance scenario before claiming protocol interoperability.

## Deliverables and completion criteria

- A go/no-go recommendation supported by a concrete user journey and named credential/issuer scope.
- A requirements and gap map linking the protocol obligations to Chimali's existing capabilities and missing components.
- A documented parent-key assurance and protection decision, including recovery and privacy consequences.
- A credential lifecycle and data classification proposal covering persistent metadata and secret ownership.
- A reproducible interoperability proposal with independent expected results and defined failure cases.
- An effort estimate and proposed separate feature specification if implementation is recommended.

Research is complete when each question has an evidence-based answer or a named external dependency, and the recommendation explains the unresolved limits. Any proof of concept must describe its restricted scope; production OpenID4VCI support requires a separate authorized feature.

## Investigation outcome

See the [dated investigation and decision](../research/openid4vci-hdk-issuance-investigation.md) for the source audit, requirements/gaps, assurance decision, lifecycle and recovery proposal, interoperability experiment, effort estimate, and dependency owners.

**Decision**: Do not implement production remote HDK issuance yet. A separately authorized, synthetic-data feasibility feature is proposed. No compatible production issuer, Android protected-child operation evidence, or resolution of the HDK/OpenID4VCI proof-binding gap was established. These are explicit follow-up dependencies, not claims of completed interoperability. This research changed documentation only; feature 055's local passkey profile remains the implementation boundary.

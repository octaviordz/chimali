# Feature Specification: WebAuthn Level 3 Compliance

**Feature Branch**: `032-webauthn-l3-compliance`  
**Created**: 2026-05-03  
**Status**: Draft  
**Input**: WebAuthn Level 3 Audit Briefing — W3C Candidate Recommendation Snapshot, 13 January 2026  
**References**: [WebAuthn L3 Compliance Brief](../../docs/webauthn-l3-compliance-spec.md)

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Credential Registration with Compliant ID (Priority: P1)

A user registers a new passkey credential in the Chimali application. The system generates a Credential ID that is guaranteed to be unique and cryptographically strong, and the system accepts or rejects credentials presented by external authenticators based on valid ID formats.

**Why this priority**: Credential ID integrity is foundational to the security and interoperability of the entire WebAuthn flow. Defects here can break registration and authentication for all users.

**Independent Test**: Can be fully tested by initiating a passkey registration ceremony and verifying that the generated Credential ID meets size and entropy requirements, and that a stateless (encrypted-blob) ID from an external authenticator is accepted without rejection.

**Acceptance Scenarios**:

1. **Given** a user initiates a passkey registration, **When** the system generates a Credential ID, **Then** the ID is between 16 and 1023 bytes in length with at least 100 bits of entropy.
2. **Given** an external authenticator presents an encrypted-blob Credential ID (stateless form), **When** the system validates the ID, **Then** the ID is accepted as a valid conformant form without error.
3. **Given** an ID exceeding 1023 bytes is presented, **When** the system processes it, **Then** the credential is rejected with an appropriate error rather than silently truncated or accepted.

---

### User Story 2 — Clear Credential Display Names During Selection (Priority: P2)

A user who has multiple passkeys registered is prompted to select one during an authentication ceremony. The system displays the `name` and `displayName` fields of each credential with sufficient fidelity to distinguish between accounts.

**Why this priority**: Truncating display names below the standard threshold degrades user experience and may cause users to select the wrong credential, creating a security and usability risk.

**Independent Test**: Can be fully tested by registering two credentials whose `displayName` values differ only after the 32nd character, then initiating an authentication ceremony and confirming both names are rendered distinctly without truncation.

**Acceptance Scenarios**:

1. **Given** a credential's `displayName` is 64 bytes long, **When** the credential selection UI is shown, **Then** the full name is displayed without any truncation.
2. **Given** a credential's `displayName` is longer than 64 bytes, **When** the UI renders it, **Then** truncation (if any) occurs only beyond the 64-byte boundary.
3. **Given** a credential's `name` field is provided, **When** the UI displays it, **Then** truncation does not occur below 64 bytes.

---

### User Story 3 — Interoperable Cryptographic Algorithm Negotiation (Priority: P1)

A user registers or authenticates with a relying party that requires EdDSA (Ed25519) algorithm support. The Chimali application correctly negotiates the algorithm, and only recommended algorithm identifiers are used during the ceremony.

**Why this priority**: Algorithm interoperability is a hard conformance requirement. Omitting EdDSA or including deprecated identifiers breaks compatibility with compliant relying parties and introduces security risks.

**Independent Test**: Can be fully tested by simulating a `pubKeyCredParams` negotiation that requests EdDSA (`-8`) and verifying that Chimali accepts it; separately, confirming that deprecated identifiers (`-9`, `-19`, `-51`, `-52`) are absent from outgoing requests.

**Acceptance Scenarios**:

1. **Given** a relying party requests EdDSA (`-8`) with curve Ed25519, **When** Chimali processes the request, **Then** the credential is created successfully using the EdDSA algorithm.
2. **Given** Chimali initiates a credential creation request, **When** the algorithm preference list is evaluated, **Then** identifiers `-9`, `-19`, `-51`, and `-52` are not present.
3. **Given** ES256 (`-7`) is used, **When** the credential is created, **Then** the P-256 curve is used and the key type is EC2.

---

### User Story 4 — Adaptive Ceremony Timeouts for All Users (Priority: P2)

A user with motor or cognitive special needs is completing an authentication or registration ceremony. The system gives the user sufficient time to complete the authorization gesture, respecting the relying party's timeout hint while never applying an unreasonably short cutoff.

**Why this priority**: Accessibility compliance is a Level 3 requirement. A fixed short timeout excludes users with special needs and may breach applicable accessibility standards.

**Independent Test**: Can be fully tested by providing a `PublicKeyCredentialCreationOptions` with a custom timeout value and verifying that the ceremony timer uses that value (within the defined reasonable range), and that the upper range boundary is set to accommodate accessibility needs.

**Acceptance Scenarios**:

1. **Given** a relying party provides a timeout hint of 120 seconds, **When** a ceremony begins, **Then** the session timer is set to 120 seconds (within acceptable bounds).
2. **Given** a relying party provides no timeout hint, **When** a ceremony begins, **Then** a default timeout is used that complies with the accessibility-driven upper ceiling.
3. **Given** a relying party provides a timeout below the minimum acceptable threshold, **When** the ceremony begins, **Then** the system enforces the minimum value rather than applying the too-short RP hint.

---

### User Story 5 — PRF Extension for Deterministic Key Derivation (Priority: P3)

A user's application session requires the PRF (pseudo-random function) extension to derive a deterministic encryption key from the authenticator. The system correctly processes salt inputs from the relying party and returns the derived output.

**Why this priority**: Required for applications that need local encryption keys tied to the authenticator. Without this, advanced features relying on deterministic key derivation cannot function.

**Independent Test**: Can be fully tested by initiating an authentication ceremony with the PRF extension carrying one or two salts, and verifying that the returned output contains the correctly derived HMAC-SHA-256 bits (up to 32 bytes per salt).

**Acceptance Scenarios**:

1. **Given** a relying party provides one salt via the PRF extension, **When** the ceremony completes, **Then** the extension output contains up to 32 bytes of deterministic HMAC-SHA-256 derived data.
2. **Given** a relying party provides two salts via the PRF extension, **When** the ceremony completes, **Then** the extension output contains two separate derived values (up to 32 bytes each).
3. **Given** a relying party provides more than two salts, **When** the request is processed, **Then** the extra salts are rejected and an error is returned.

---

### User Story 6 — Attestation Object Integrity (Priority: P2)

A user's registration ceremony produces an attestation object that the relying party can successfully verify. Chimali correctly generates the attestation object and supports Basic, Self, and AttCA attestation types.

**Why this priority**: Attestation object integrity enables the relying party to validate the authenticator and trust the registered credential. Failures here break the verification flow.

**Independent Test**: Can be fully tested by completing a registration ceremony and having a conformant relying party verify the resulting attestation object without errors across all three attestation types.

**Acceptance Scenarios**:

1. **Given** a registration ceremony completes with Basic attestation, **When** the relying party processes the attestation object, **Then** verification succeeds.
2. **Given** a registration ceremony completes with Self attestation, **When** the relying party processes the attestation object, **Then** verification succeeds.
3. **Given** a registration ceremony uses the `AttCA` type, **When** the relying party processes the attestation object, **Then** verification succeeds using the AttCA attestation statement.

---

### Edge Cases

- What happens when a Credential ID of exactly 1023 bytes is presented? (Boundary: must be accepted.)
- What happens when a Credential ID of exactly 16 bytes is presented? (Boundary: must be accepted if entropy criteria are met.)
- How does the system handle a `displayName` field of exactly 64 bytes? (Must render without truncation.)
- What happens when the RP provides a `timeout` of 0 or a negative value? (System must enforce minimum safe bound.)
- What happens if an authenticator returns EdDSA with an unexpected curve (not Ed25519)? (System must reject the response.)
- What happens when the PRF extension is requested but the authenticator does not support `hmac-secret`? (System must return a clear, non-fatal error without crashing the ceremony.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-FIDO2-001**: The system MUST reject any Credential ID whose length exceeds 1023 bytes.
- **FR-FIDO2-002**: The system MUST ensure every generated entropy-based Credential ID contains at least 100 bits of entropy and is at least 16 bytes in length.
- **FR-FIDO2-003**: The system MUST accept and recognize encrypted-blob (stateless) Credential IDs as a valid conformant form.
- **FR-FIDO2-004**: The system MUST NOT truncate `name` or `displayName` fields at fewer than 64 bytes in any UI selection or display context.
- **FR-FIDO2-005**: The system MUST support the EdDSA algorithm identifier (`-8`) with the Ed25519 curve during credential creation and authentication ceremonies.
- **FR-FIDO2-006**: The system MUST ensure ES256 (`-7`) usage specifies the P-256 curve and EC2 key type.
- **FR-FIDO2-007**: The system MUST NOT include algorithm identifiers `-9`, `-19`, `-51`, or `-52` in its default preference list.
- **FR-FIDO2-008**: The system MUST apply a dynamic ceremony timeout that respects the relying party's provided timeout hint from `PublicKeyCredentialCreationOptions`.
- **FR-FIDO2-009**: The system MUST enforce an accessibility-compliant upper ceiling for ceremony timeouts, ensuring users with cognitive or motor-skill needs are not prematurely timed out.
- **FR-FIDO2-010**: The system MUST generate a complete `attestationObject` (including `authData` with AAGUID, Credential ID, and Public Key, plus `attStmt`) during the `authenticatorMakeCredential` operation.
- **FR-FIDO2-011**: The system MUST support generation of Basic, Self, and AttCA attestation types.
- **FR-FIDO2-012**: The system MUST maintain the integrity of the attestation object during conveyance to allow the relying party to execute verification.
- **FR-FIDO2-013**: The system MUST support the PRF extension by processing one or two salts provided by the relying party.
- **FR-FIDO2-014**: The PRF extension MUST return up to 32 bytes of deterministic HMAC-SHA-256 derived output per salt within the authenticator data.
- **FR-FIDO2-015**: The system MUST reject PRF extension inputs containing more than two salts.

### Key Entities

- **Credential ID**: A probabilistically unique byte sequence identifying a public key credential. Has two valid forms: entropy-based (≥16 bytes, ≥100 bits entropy) and encrypted-blob (stateless). Maximum 1023 bytes.
- **PublicKeyCredential**: The top-level credential object containing the `[[identifier]]` slot (Credential ID), attestation data, and extension outputs.
- **Attestation Object**: Generated by the authenticator during registration. Contains `authData` (AAGUID, Credential ID, Public Key) and an attestation statement (`attStmt`).
- **COSE Algorithm**: A numeric identifier mapping to a cryptographic algorithm and its required curve/key type parameters (e.g., ES256 → P-256, EdDSA → Ed25519).
- **PRF Salt**: An input value provided by the relying party (one or two permitted) used to derive a deterministic output via HMAC-SHA-256.
- **Ceremony Timer**: A session-scoped timer governing the lifetime of a registration or authentication ceremony. Must be dynamically set based on RP hint and accessibility bounds.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-FIDO2-001**: 100% of generated Credential IDs are between 16 and 1023 bytes and meet the 100-bit entropy requirement, verifiable via automated test suite.
- **SC-FIDO2-002**: 100% of credential selection UI flows display `name` and `displayName` fields without truncation below 64 bytes.
- **SC-FIDO2-003**: EdDSA-based credential creation succeeds in 100% of test cases where the relying party requests algorithm `-8` with Ed25519.
- **SC-FIDO2-004**: Zero occurrences of deprecated algorithm identifiers (`-9`, `-19`, `-51`, `-52`) appear in outgoing credential creation requests, confirmed by protocol-level assertion tests.
- **SC-FIDO2-005**: Ceremony timeouts reflect the RP-provided hint in 100% of cases where a valid hint is supplied.
- **SC-FIDO2-006**: Attestation objects generated by Chimali pass verification by a conformant, independent relying party implementation in 100% of test cases for all three attestation types.
- **SC-FIDO2-007**: PRF extension correctly returns up to 32 bytes of derived output per salt in 100% of tested scenarios with one or two salts, and correctly rejects requests with more than two salts.
- **SC-FIDO2-008**: All WebAuthn Level 3 conformance requirements identified in the audit report are resolved with zero remaining High-criticality non-conformances.

## Assumptions

- The Chimali application is assumed to act in both User Agent and Authenticator roles as described in WebAuthn § 2.1 and § 2.2.
- Attestation verification is the responsibility of the relying party; this feature covers only attestation *generation* and *conveyance integrity*.
- The `hmac-secret` CTAP2 extension is available in the hardware/software authenticator component integrated with Chimali; if unavailable, PRF failures are surfaced as non-fatal errors.
- The "reasonable range" for ceremony timeouts is defined per W3C § 15.1 cognitive and accessibility guidelines: minimum 30,000 ms (30 s), maximum 600,000 ms (10 min), default 120,000 ms (2 min) when no RP hint is provided. These values are codified in `MakeCredentialOptions` and `GetAssertionOptions` domain models.
- Encrypted-blob Credential IDs from stateless authenticators are consumed but not generated by Chimali in the current scope.
- No changes to the relying party server component are in scope; all changes are confined to the Chimali Android client application.
- Existing authenticated users will not have their registered credentials invalidated by this update; the changes apply only to new credential generation and validation logic.

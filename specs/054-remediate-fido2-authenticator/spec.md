# Feature Specification: Remediate FIDO2 Authenticator

**Feature Branch**: `054-remediate-fido2-authenticator`

**Created**: 2026-09-10

**Status**: Draft

**Input**: User description: "Implement remediations for the live FIDO2 authenticator stub and incomplete credential repository behavior."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Complete an authenticator ceremony (Priority: P1)

As a passkey user, I can register a credential and authenticate with an existing credential through the
live authenticator so that my connected host receives a valid, usable response without a stale placeholder path.

**Why this priority**: Registration and authentication are the authenticator's core purpose; a live
stale dependency creates nonfunctional duplicate behavior and violates the constitution.

**Independent Test**: Invoke each live host ceremony with valid inputs and verify that it uses the established
ceremony result and persists or uses the intended credential without depending on a nonfunctional facade.

**Acceptance Scenarios**:

1. **Given** valid registration input, **When** a host requests credential creation through the live transport,
   **Then** the host receives a valid attestation result and the resulting credential is available to the authenticator.
2. **Given** a matching stored credential and valid assertion input, **When** a host requests authentication through
   the live transport, **Then** the host receives a valid assertion result produced by that credential.
3. **Given** invalid input, denied verification, or a storage/cryptographic failure, **When** a ceremony is requested,
   **Then** the caller receives the established typed failure without a stale placeholder or a leaked secret.

---

### User Story 2 - Inspect credential availability and verification requirements (Priority: P1)

As an authenticator user, I can receive the credentials that are actually stored, including those requiring user
verification, so that credential management and policy-dependent flows operate on accurate data.

**Why this priority**: Returning an empty inventory or a hard-coded verification count conceals real credentials and
can cause incorrect security decisions.

**Independent Test**: Store credentials with distinct relying parties and verification policies, then verify that
all-credential, relying-party, and verification-required views return only the applicable credentials and statistics.

**Acceptance Scenarios**:

1. **Given** stored credentials, **When** the live authenticator lists all credentials, **Then** it emits the stored
   credentials rather than an empty result.
2. **Given** stored credentials for multiple relying parties, **When** the live authenticator filters by relying party,
   **Then** it emits only credentials for the requested relying party.
3. **Given** credentials whose policy requires user verification, **When** verification-required credentials or
   statistics are requested, **Then** the result and count reflect the persisted policy rather than a fixed value.

---

### User Story 3 - Avoid unsupported placeholder operations (Priority: P2)

As a connected-host user, I do not encounter an advertised authenticator operation that is actually a placeholder,
so that the app exposes only behavior it can perform through its supported transport.

**Why this priority**: Pairing is secondary to ceremonies, but a public live API must not advertise a nonfunctional
operation or duplicate an existing transport responsibility.

**Independent Test**: Verify that the live transport still returns correct authenticator information after the stale
facade is removed, and that no reachable production path contains an unimplemented authenticator operation.

**Acceptance Scenarios**:

1. **Given** a connected host requests authenticator information, **When** the stale facade is retired, **Then** the
   host receives the same valid capability information through the authoritative information path.
2. **Given** a production authenticator path, **When** it is exercised, **Then** it does not expose a hard-coded
   unimplemented outcome, empty credential flow, or unconditional successful state change.

### Edge Cases

- A registration request must not leave a partially stored credential when validation, verification, or key creation fails.
- An assertion request with no matching credential must return a typed not-found failure.
- Deletion and reset must not report success unless the associated stored credential state changed as requested.
- A credential whose persisted policy cannot be interpreted must be treated conservatively and surfaced as a typed error.
- Removing a stale facade must not change the capability information returned to a connected host.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST preserve live credential-creation requests through the established registration ceremony
  and remove any stale placeholder path that duplicates it.
- **FR-002**: The system MUST preserve live assertion requests through the established assertion ceremony and remove any
  stale placeholder path that duplicates it.
- **FR-003**: The system MUST expose stored credentials through the live authenticator, including relying-party filtering.
- **FR-004**: The system MUST remove stale authenticator operations that report unperformed deletion, reset,
  configuration, readiness, health, or pairing behavior as successful or available.
- **FR-005**: The system MUST determine verification-required credential views and statistics from persisted credential
  policy rather than filtering out every credential or using a fixed count.
- **FR-006**: The system MUST obtain live authenticator capability information from an authoritative, supported path
  after removing the stale facade.
- **FR-007**: The system MUST preserve credential confidentiality, integrity, and mutable-secret cleanup throughout all
  remediated paths.
- **FR-008**: The system MUST provide requirement-to-code and requirement-to-test traceability for the remediated
  critical authenticator and repository behavior.

### Key Entities *(include if feature involves data)*

- **Authenticator operation**: A public request to create, assert, list, delete, reset, or configure credentials.
- **Credential policy**: Persisted credential attributes that determine relying-party ownership and whether user
  verification is required.
- **Authenticator capability information**: The version, algorithms, transport, and resident-key capabilities exposed
  to a connected host.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of exercised live registration and assertion requests return an established ceremony result or typed
  domain failure; none depend on an unimplemented placeholder.
- **SC-002**: Credential-list and verification-policy tests return exactly the stored, applicable credentials in every
  tested scenario.
- **SC-003**: 100% of exercised production authenticator paths expose only implemented behavior and return correct
  capability information after the stale facade is removed.
- **SC-004**: All remediated critical paths have independent tests linked to their functional requirement identifiers.

## Assumptions

- Existing registration and assertion use cases, together with their CTAP handlers, are the authoritative ceremony
  implementations and remain the source of cryptographic, verification, and persistence behavior.
- Existing persisted credential policy contains sufficient information to identify verification-required credentials;
  if it does not, an additive migration is required before exposing the feature.
- Bluetooth HID remains the currently supported live transport; retiring the stale facade does not broaden transport
  support or add a standalone pairing workflow.
- This feature is limited to removing the reported FIDO2 authenticator and repository placeholders, not the broader
  constitution audit findings.

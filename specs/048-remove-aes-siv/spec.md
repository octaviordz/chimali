# Feature Specification: Remove AES-256-SIV

**Feature Branch**: `048-remove-aes-siv`

**Created**: 2026-05-20

**Status**: Draft

**Input**: User description: "Based on the AES-SIV removal analysis, create a specification with the main goal of removing AES-256-SIV. Carefully consider the changes needed to the constitution."

## Clarifications

### Session 2026-05-20

- Q: How should existing partial text search behave after AES-SIV removal? → A: Preserve partial text search over explicitly classified SQLCipher-only display fields.
- Q: What should replace the former AES-SIV key-wrapping rule? → A: Use platform-backed AES-GCM/AEAD key wrapping with unique nonces and associated data.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Update Security Policy (Priority: P1)

As a project maintainer, I need the constitution to stop mandating AES-256-SIV and instead define the approved pattern for searchable encrypted metadata, so future plans and implementations do not conflict with project governance.

**Why this priority**: The current constitution explicitly requires AES-256-SIV for searchable encrypted metadata and key wrapping. Removing AES-SIV from the code without first updating this policy would violate the constitution and create ambiguous security guidance.

**Independent Test**: Can be fully tested by reviewing the constitution and confirming it permits AES-256-GCM for encrypted values, requires keyed deterministic lookup tokens for exact-match searchable metadata, prohibits deterministic AES-GCM nonce misuse, and no longer lists AES-256-SIV as a mandatory primitive.

**Acceptance Scenarios**:

1. **Given** the current constitution mandates AES-256-SIV, **When** the security policy is updated, **Then** the policy preserves encrypted searchable metadata requirements without requiring AES-256-SIV.
2. **Given** a future feature needs exact-match lookup over sensitive metadata, **When** the team consults the constitution, **Then** the approved approach is deterministic keyed lookup tokens plus authenticated encrypted values.
3. **Given** a developer proposes deterministic AES-GCM by reusing or deriving nonces from plaintext, **When** the proposal is checked against the constitution, **Then** the proposal is rejected by policy.
4. **Given** a future feature needs key wrapping or master-key boundary protection, **When** the team consults the constitution, **Then** the approved approach is platform-backed AES-GCM/AEAD with unique nonces and associated data rather than AES-SIV.

---

### User Story 2 - Preserve Searchable Metadata Behavior (Priority: P2)

As a FIDO2 and vault user, I need credential lookup, filtering, and management behavior to continue working after AES-SIV is removed, so existing passkeys and vault items remain usable without data loss.

**Why this priority**: AES-SIV was originally justified by exact-match lookup needs. Removing it is only acceptable if searchable metadata keeps its required behavior and sensitive metadata is not downgraded to plaintext exposure.

**Independent Test**: Can be fully tested by using existing data and verifying that exact-match lookups, duplicate checks, credential selection, and management searches still return the expected records after migration.

**Acceptance Scenarios**:

1. **Given** existing credentials are stored before the migration, **When** the migration completes, **Then** each credential remains discoverable by its relying party and user identity where that lookup was previously supported.
2. **Given** two records have the same searchable metadata value, **When** exact-match lookup is performed, **Then** both records are returned using the approved deterministic lookup token.
3. **Given** searchable metadata is sensitive, **When** the data is inspected outside the running application, **Then** the raw sensitive value is not available solely from the lookup token.
4. **Given** user-facing search includes partial text matching, **When** AES-SIV is removed, **Then** the feature continues through explicitly classified SQLCipher-only display fields and does not claim deterministic lookup tokens provide substring search.

---

### User Story 3 - Decommission AES-SIV-Specific Surface Area (Priority: P3)

As an Android maintainer, I need AES-SIV-specific APIs, implementation code, documentation promises, and tests to be removed or replaced, so the project no longer maintains an unused custom cryptographic mode.

**Why this priority**: The previous analysis found AES-SIV code present but not integrated into the live storage paths. Decommissioning should happen only after the constitution and data behavior are aligned.

**Independent Test**: Can be fully tested by searching the project for AES-SIV-specific production references and confirming no remaining production requirement, API, or service depends on AES-SIV.

**Acceptance Scenarios**:

1. **Given** the constitution no longer requires AES-SIV, **When** production code is reviewed, **Then** no AES-SIV-specific encryption service remains as an active dependency.
2. **Given** documentation mentions AES-SIV as a required security primitive, **When** documentation is reviewed, **Then** those references are removed, deprecated, or replaced with the new approved metadata-search pattern.
3. **Given** Bouncy Castle remains used by non-SIV cryptographic features, **When** the AES-SIV removal is complete, **Then** the project clearly documents that full Bouncy Castle removal is outside this feature unless separately specified.

---

### User Story 4 - Validate Secure Migration (Priority: P4)

As a security reviewer, I need evidence that removal of AES-SIV does not weaken confidentiality, integrity, or availability for existing data, so the change can pass the local quality gate and security review.

**Why this priority**: This is the final proof that the policy and implementation changes behave safely together. It depends on the prior stories.

**Independent Test**: Can be fully tested by running migration and regression tests against seeded datasets and confirming the same records are accessible, sensitive fields remain protected, and tampered encrypted values are rejected.

**Acceptance Scenarios**:

1. **Given** a seeded dataset contains credentials and searchable metadata, **When** the migration runs, **Then** no records are lost or duplicated.
2. **Given** an encrypted value is tampered with after migration, **When** the application reads it, **Then** the tampering is detected and the value is not accepted as valid.
3. **Given** the application contains 10,000 stored records, **When** exact-match lookup is performed after migration, **Then** the lookup remains usable within the existing project performance expectations.

### Edge Cases

- Existing installations may contain metadata written before any deterministic lookup token existed.
- Some user-facing search flows may require partial text matching, which deterministic exact-match lookup tokens do not support.
- The same metadata value may appear across many records, so equality and frequency leakage must be acknowledged and bounded.
- Migration may be interrupted after only part of the dataset is rewritten.
- Legacy documentation, tests, comments, and build comments may still claim AES-SIV is mandatory after production code changes.
- Bouncy Castle may still be required for non-SIV cryptographic features after AES-SIV removal.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-SEC-001**: System MUST update the constitution so AES-256-SIV is no longer a mandatory encryption primitive.
- **FR-SEC-002**: System MUST preserve the rule that sensitive metadata used for search or lookup is not stored as raw plaintext outside an explicitly documented SQLCipher-only exception.
- **FR-SEC-003**: System MUST define deterministic keyed lookup tokens as the approved replacement for exact-match searchable encrypted metadata.
- **FR-SEC-004**: System MUST require authenticated encryption for stored sensitive metadata values, with random nonces or platform-generated nonces for each encryption operation.
- **FR-SEC-005**: System MUST explicitly prohibit using AES-256-GCM with fixed, reused, predictable, or plaintext-derived nonces to emulate deterministic encryption.
- **FR-SEC-006**: System MUST require separate cryptographic purposes for lookup tokens, encrypted values, database encryption, and signing material.
- **FR-SEC-007**: System MUST replace the former AES-SIV key-wrapping rule with platform-backed AES-GCM/AEAD key wrapping that uses unique nonces and associated data.
- **FR-SEC-008**: System MUST document that AES-SIV removal is separate from full Bouncy Castle removal, because Bouncy Castle remains used by other cryptographic capabilities.
- **FR-DATA-001**: System MUST preserve exact-match lookup behavior for all metadata fields that currently require exact-match lookup.
- **FR-DATA-002**: System MUST preserve existing partial-match search behavior over explicitly classified SQLCipher-only display fields and MUST NOT represent deterministic lookup tokens as supporting substring search.
- **FR-DATA-003**: System MUST migrate existing searchable metadata to the replacement lookup-token model without losing existing records.
- **FR-DATA-004**: System MUST handle interrupted migration by supporting resumable backfill that tracks per-record migration state, allows safe re-execution after crash or cancellation, and ensures records remain accessible via fallback to plaintext SQLCipher columns until migration completes.
- **FR-DATA-005**: System MUST retain compatibility with existing encrypted payloads and database-level encryption during the transition.
- **FR-CLEAN-001**: System MUST remove, deprecate, or replace AES-SIV-specific production APIs and services after replacement search behavior exists.
- **FR-CLEAN-002**: System MUST update tests, changelogs, research notes, and developer documentation that describe AES-SIV as a required primitive.
- **FR-CLEAN-003**: System MUST update stale dependency comments or policy statements that claim an external cryptography library is retained only for AES-SIV.
- **FR-VERIFY-001**: System MUST include verification that exact-match lookup still works for existing and newly created records.
- **FR-VERIFY-002**: System MUST include verification that tampered encrypted metadata values are rejected.
- **FR-VERIFY-003**: System MUST pass the local quality gate before the feature is considered complete.

### Key Entities

- **Cryptographic Policy**: The project-governance rules defining approved encryption, metadata lookup, key separation, and prohibited cryptographic practices.
- **Searchable Metadata**: Sensitive, semi-sensitive, or display-classified values that need lookup behavior, such as relying party identifiers, user identifiers, credential labels, aliases, or categories.
- **Deterministic Lookup Token**: A keyed, deterministic value used only for equality matching and not for recovering the original metadata value.
- **Encrypted Metadata Value**: The protected canonical metadata value stored separately from its lookup token and authenticated against tampering.
- **Migration State**: Progress and compatibility information needed to ensure existing records remain accessible during and after the transition.
- **Deprecated AES-SIV Surface**: Production APIs, services, tests, documentation, and policy statements that specifically depend on AES-SIV.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-SEC-001**: The constitution contains zero mandatory AES-256-SIV requirements and includes an explicit replacement policy for searchable metadata.
- **SC-SEC-002**: Security review finds zero approved paths that rely on deterministic AES-GCM nonce reuse.
- **SC-DATA-001**: 100% of pre-migration seeded records remain accessible by supported exact-match lookup flows after migration.
- **SC-DATA-002**: 100% of newly created records are written using the replacement searchable-metadata model.
- **SC-DATA-003**: Tampering checks reject altered encrypted metadata values in all covered test cases.
- **SC-CLEAN-001**: Production references to AES-SIV-specific APIs and services are removed or explicitly marked as deprecated compatibility-only code.
- **SC-CLEAN-002**: Project documentation no longer presents AES-SIV as the required solution for searchable metadata or key wrapping.
- **SC-PERF-001**: Exact-match lookup over 10,000 records completes within 200ms for credential selection and 500ms for vault management flows, preserving the existing <200ms FIDO2 action target where lookup participates.
- **SC-VERIFY-001**: The local quality gate completes successfully with the AES-SIV removal feature included.

## Assumptions

- The replacement for AES-SIV is deterministic keyed lookup tokens for equality search plus authenticated encryption for metadata values.
- AES-256-GCM remains the approved primitive for general encrypted values and key wrapping when each encryption uses a unique nonce and appropriate associated data.
- Partial text search is preserved only over explicitly classified SQLCipher-only display fields; exact-match lookup tokens do not support substring search.
- SQLCipher remains permitted for database file-level encryption, but it does not replace field-level protection for data the constitution classifies as requiring individual encryption.
- Full Bouncy Castle removal is out of scope for this feature unless later planning adds a separate provider migration.
- Existing users must not be forced to re-register credentials solely because AES-SIV is removed.

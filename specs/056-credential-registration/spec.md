# Bug Specification: Credential registration fails in optimized builds

**Branch**: `bug/056-credential-registration` | **Created**: 2026-10-15 | **Status**: Implemented and verified for the controlled provider defect
**Input**: Investigate registration failures from “Implement HDK draft migration” and implement a fix.
**Complexity**: S

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Register and use a passkey in the installed release (Priority: P1)

As a user approving a supported passkey request, I need credential creation to complete in the
installed optimized application so that I can authenticate with the resulting credential.

**Why this priority**: Registration blocks authentication for every new credential.
**Independent Test**: Exercise public-key conversion and signature verification for each supported
credential algorithm in the optimized application on ASUS. Report external browser verification separately.

**Acceptance Scenarios**:

1. **Given** a configured wallet and an approved supported registration, **When** the optimized app
   creates a credential, **Then** its crypto implementation is available and its public key can be
   returned without a missing-key or generic crypto failure.
2. **Given** a generated supported credential key, **When** it signs a challenge, **Then** the
   reconstructed public key verifies the signature and rejects changed data.

### Edge Cases

- ES256, Ed25519, and ML-DSA-65 must each remain available after optimization.
- Tests must use the installed app's crypto classes rather than an unoptimized test dependency.
- Unavailable wallets, rejected approval, and unsupported algorithms must continue to fail safely.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Supported credential public-key creation and conversion MUST work in optimized builds.
- **FR-002**: Supported credential signatures MUST verify with the corresponding public key and
  reject modified messages.
- **FR-003**: The fix MUST preserve credential identity, derivation, storage, approval, and failure handling.
- **FR-004**: Regression verification MUST exercise the optimized installed application.
- **FR-005**: The investigation MUST distinguish confirmed causes from unverified browser outcomes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All three supported credential algorithm checks pass on the ASUS optimized build.
- **SC-002**: The same checks demonstrate failure before the fix and success after it.
- **SC-003**: The release bundle stays below 50 MB; growth over 500 KB requires approval before integration.
- **SC-004**: Existing registration tests and applicable quality checks pass.

## Assumptions

- The current checkout is baseline `8b4d85ad`; migration changes in the existing stash remain separate.
- The reports include “Generated key not found in KeyStore” and “Credential cryptographic operation
  failed.” Neither message alone proves a cause.
- A browser ceremony requires host/user interaction and is reported separately from device tests.

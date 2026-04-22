# Feature Specification: Optimize Credential Keys

**Feature Branch**: `012-optimize-credential-keys`  
**Created**: 2026-04-22  
**Status**: Draft  
**Input**: User description: "Update GetAllCredentialsUseCase workflow to avoid re-deriving the keys. Add more details to the specifiction based on your findings"

## Clarifications

### Session 2026-04-22
- Q: How should the application visually indicate that the next page of credentials is being loaded at the bottom of the list? → A: No visual indicator; loading happens silently in the background, relying on the 'visible + 1 page' buffer to prevent visual jitter.
- Q: What should the application do if a credential's public key cannot be decoded from the database? → A: Schedule a background batch job using `kmpworkmanager` to attempt HDK fallback derivation and repair the database entries.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Instant Passkey List Loading (Priority: P1)

As a user, I want the passkeys screen to load instantly without delay, so that I can quickly view or manage my stored credentials.

**Why this priority**: The current implementation runs expensive cryptographic operations for every credential merely to display the list, severely degrading performance and generating excessive logs. Eliminating this bottleneck is critical for a smooth user experience.

**Independent Test**: Can be fully tested by creating multiple FIDO2 passkeys (including ML-DSA keys), navigating to the 'Passkeys' screen, and verifying that the list populates instantly without any "key pair generated" logs appearing in the background.

**Acceptance Scenarios**:

1. **Given** a user has multiple passkeys stored, **When** they navigate to the Passkeys list, **Then** the screen renders immediately.
2. **Given** a user has multiple passkeys stored, **When** the list is loaded, **Then** the cryptographic derivation engine is never invoked.

---

### User Story 2 - Lazy Loading / Infinite Scroll (Priority: P2)

As a user with a large number of passkeys, I want the list to load only the visible items and fetch more as I scroll, so that memory usage is minimized and the app remains highly responsive.

**Why this priority**: While the underlying crypto optimization fixes the primary bottleneck, pagination or lazy loading ensures that rendering a massive list of credentials doesn't cause UI stuttering or excessive memory consumption on the device.

**Independent Test**: Can be tested by creating 100+ passkeys. When navigating to the screen, only the first batch (e.g., visible + 1 page) should be loaded into memory. Scrolling down should dynamically fetch the next batch seamlessly.

**Acceptance Scenarios**:

1. **Given** a user has a large dataset of passkeys, **When** the list is initially opened, **Then** only a limited batch of items (visible + next page) is loaded.
2. **Given** a user is viewing the list, **When** they scroll near the bottom, **Then** the next batch of passkeys is loaded seamlessly without blocking the UI.

---

### Edge Cases

- **Corrupted Public Key Fallback**: If a stored public key string is corrupted or invalid in the database, the system will temporarily hide the credential from the active UI and schedule a background batch job via `kmpworkmanager`. This job will attempt an HDK fallback derivation to repair the database entries offline.
- How does the system handle retrieving credentials if a new algorithm is introduced but the stored public key format requires special decoding?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST populate the credential list using the public key data already stored in the local database.
- **FR-002**: The system MUST NOT execute cryptographic key derivations (e.g., HDK, ML-DSA generation) during read-only list operations like `getAllCredentials` or `getCredentialsForRp`.
- **FR-003**: The system MUST correctly decode the stored public keys back into the required in-memory representations for all supported algorithms (ES256, Ed25519, ML-DSA-65) without relying on the Master Seed.
- **FR-004**: The credential domain mapping logic MUST be refactored to consume the stored public key instead of requiring an external derivation source.
- **FR-005**: The system MUST implement lazy loading (pagination or infinite scroll) for the credential list.
- **FR-006**: The lazy loading mechanism MUST fetch only the currently visible items plus one additional page of items to maintain smooth scrolling and avoid visual jitter.
- **FR-007**: As the user scrolls, the system MUST dynamically request subsequent pages of data from the repository without freezing the UI.
- **FR-008**: The system MUST NOT display a visual loading indicator (e.g., spinner or skeleton) at the bottom of the list, as the local database load speed combined with the 1-page buffer makes it unnecessary and could introduce visual jitter.

### Key Entities

- **Passkey Credential**: The core entity whose stored `publicKey` attribute must be properly utilized instead of being ignored.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The Passkeys list loads at least 10x faster (e.g., under 50ms) for a list of 50 credentials compared to the previous derivation approach.
- **SC-002**: No "key pair generated" or related cryptographic derivation logs are output when performing read-only list or search operations.
- **SC-003**: 100% of the credentials accurately retain their functional public keys in the UI and subsequent operations after this optimization.
- **SC-004**: Initial load memory footprint is reduced by only fetching a single page of items, and subsequent items load seamlessly (no UI freeze > 16ms) during fast scrolling.

## Assumptions

- The `publicKey` column in the database accurately contains the uncompressed public key bytes or encoded representation matching what was originally generated.
- Decoding the stored public key string is significantly faster and computationally cheaper than re-deriving it from the master seed.

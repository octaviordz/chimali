# Security Policy Review Checklist

**Purpose**: Validate that the Chimali Constitution and architecture documents correctly reflect the removal of mandatory AES-256-SIV and the adoption of modern, secure replacements.

## Zero Mandatory AES-SIV
- [x] The constitution no longer mandates AES-256-SIV for searchable metadata.
- [x] The constitution no longer mandates AES-256-SIV for key wrapping.

## Lookup-Token Replacement
- [x] The policy requires deterministic keyed lookup tokens (e.g., HMAC-based blind indexes) for exact-match searchable metadata.
- [x] The policy explicitly prohibits the misuse of AES-GCM with fixed/reused nonces for deterministic encryption.

## SQLCipher Display Exceptions
- [x] The policy permits partial-text search ONLY via explicitly classified SQLCipher-protected display fields, not through lookup tokens.

## AEAD Key Wrapping
- [x] The policy requires platform-backed AES-GCM/AEAD for key wrapping, ensuring unique nonces and associated data are used.

## Bouncy Castle Scope
- [x] The policy clarifies that Bouncy Castle is retained for non-SIV usages (HDK, PQC, etc.), and its removal is out of scope for this specific feature.

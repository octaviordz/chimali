# Credential deletion use-case owner map

**Scope:** `CredentialRepository` deletion API, `DeleteCredentialUseCase`, `DeleteAllCredentialsUseCase`, and their host forwarding tests.

The single-delete use case forwards the immutable `CredentialId`; RP-scoped/all deletion forwards an optional immutable `RpId`. These wrappers do not decode identifiers, create mutable copies, retain credential payloads, or log values. `CredentialId.toString()` is redacted. The repository owns command/projection processing and clears hydrated credential models during bulk deletion; use cases return the repository's typed outcome directly.

The forwarding suites pass two single-delete and three bulk-delete cases in the full FIDO host run (1,272 tests/168 reports, zero failures/errors/skips). This maps only the wrapper boundaries; the mutable repository/DAO/EventStore owners, RP/String immutability, provider/database copies and heap roots remain open under T005/T006/T015.

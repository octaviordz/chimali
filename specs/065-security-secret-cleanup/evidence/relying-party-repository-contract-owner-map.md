# Relying-party repository interface owner map

- **Existing finding:** SEC065-CORE-008 / SEC065-ERR-023
- **Source:** `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/repository/RelyingPartyRepository.kt`
- **Classification:** CLS-FIDO-002
- **Review date:** 2026-11-17

The interface accepts a `RelyingParty` owner for persistence, immutable `RpId` selectors for lookup/delete/count updates, returns a mutable `RelyingParty` owner from lookup, and emits lists of mutable owners through a `Flow`. It contains no implementation, logging or additional buffering. The concrete repository implementation maps rows, erases source-row metadata, transfers successful flow emissions to the collector, and erases untransferred models on failure/cancellation; its scoped regressions and DAO failure handling are recorded in `relying-party-projection-row-cleanup.md` and `relying-party-repository-error-cause-discard.md`.

The adjacent `CeremonyLock` stores only a mutex and exposes acquisition state as booleans. It accepts and retains no RP, credential, account or other secret/identifying value, so its inventory disposition is assessed-no-relevant-usage for this cleanup audit.

This is an interface/source disposition and does not independently certify all concrete caller terminal cleanup, persistence/provider copies, or heap-root behavior.

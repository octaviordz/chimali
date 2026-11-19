# Credential repository interface owner contract

**Date:** 2026-11-16  
**Scope:** Public FIDO CredentialRepository API KDoc only; no repository implementation or runtime behavior is certified by this record.

The interface now specifies that mutable text inputs are borrowed for the operation (or full cold-flow collection) and are not retained; successful mutable return values transfer to the caller for erasure after the final borrower; successful flow emission transfers to the collector and rejected emission remains the producer’s cleanup duty. The update callback may borrow the current relying-party model only during invocation, must not retain it, and transfers its result to the repository for cleanup at call completion. Save operations borrow their model inputs and leave terminal erasure with the caller.

The change synchronizes the API contract with the already documented owner behavior in selected implementations. It does not prove that all repository methods, callback callsites, downstream collectors, SQLDelight/generated/provider copies or heap roots obey the contract. Those remain open for T005/T006/T012/T015/T036 and the complete release evidence gates. No runtime test was run for this KDoc-only change.

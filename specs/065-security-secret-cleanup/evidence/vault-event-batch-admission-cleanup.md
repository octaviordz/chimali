# Vault event batch admission cleanup

Date: 2026-11-18  
Finding: SEC065-VAULT-015  
Source: `core/data/src/main/kotlin/com/chimali/core/data/eventsourcing/EventStoreRepositoryImpl.kt`

## Source review and correction

`decodeOwnedVaultEvents` owned events already admitted to its local list and cleared them if a later row failed. Previously, `owned += decode(row)` had no cleanup for the current decoded event if list admission itself failed. The helper now keeps the decoded event in a local owner until `owned.add(event)` completes and clears it in `finally` if admission fails. Batch cleanup still covers all earlier admitted events when iteration, decode or later processing fails.

This is source inspection only. No regression test was added or run for failed list admission; `EventStorePartialDecodeOwnershipTest` covers a later-row decode failure instead. Provider/string copies, successful repository caller release, runtime and heap roots remain unverified.

# Credential metadata caller owner review

**Date:** 2026-11-17  
**Scope:** `CredentialMetadataProtectionService` callers in credential row mapping, normal repository reads, and destructive batch reads.  
**Requirements:** FR-003/004/010; NFR-SEC-041; TR-AUTH-031; T012.

## Source trace

- `PasskeyCredentialEntity.mapCredentialRecord` decrypts optional metadata against the decoded RP owner. It checks the returned metadata against the row's RP and user owners, then erases the metadata in `finally`, including when identity validation fails.
- `toDomainModelWithTextDecoder` erases the source SQLDelight row in `finally`. Mapping transfers the four mutable text owners into a successfully constructed `PasskeyCredential`; otherwise it erases every partially created text owner and decoded AAGUID/credential-ID arrays.
- `PasskeyCredentialEntity.eraseSensitiveMetadata` currently clears RP ID/name, user ID/name/display name, lookup-index arrays, and encrypted metadata. Generated SQLDelight fields are mutable byte arrays at this source boundary; driver/native copies and underlying database storage are outside this source-level guarantee.
- `CredentialRepositoryReadOperations` clears hydrated partial batches and source rows on failed batch construction, clears rejected flow emissions, and transfers successful batch results or emissions to the caller/collector. Cancellation reaches the enclosing `finally` cleanup paths.
- `DestructiveCredentialBatchReader` erases every selected source row and any partially hydrated credentials on malformed data, cancellation, or other unsuccessful exit. A complete successful list transfers to the destructive repository caller.

## Finding and limit

No additional application-owned cleanup defect was found in these reviewed paths, so no production code change was made. This review does not establish cleanup of every caller after ownership transfer, all `SensitiveCredentialText` conversions, framework/provider/native copies, generated storage behavior, complete flow fan-out, or heap roots. T012 and the broader T005/T006 inventory work remain open for those gaps.

The corresponding source usages are `U-FIDO-CREDENTIAL-READ-OWNERS-001` and `U-FIDO-DESTRUCTIVE-CREDENTIAL-BATCH-OWNER-001` in `source-inventory.json`. Existing selected host tests and their limitations remain recorded in the evidence index; this is a source review, not a new test result.

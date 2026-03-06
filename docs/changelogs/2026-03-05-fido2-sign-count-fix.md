# Changelog: FIDO2 Signature Counter (signCount) Fix

## Date: 2026-03-05
## Status: COMPLETED
## Feature: FIDO2 Authentication Reliability

### Problem
Authentication on `webauthn.io` (and potentially other strict RPs) failed during the second and subsequent login attempts with the error:
`Authentication failed: Response sign count of 1 was not greater than current count of 1`.

### Root Cause
An ID mismatch existed in the SQLDelight queries within `PasskeyCredential.sq`. Specifically, the queries `getSignCount`, `updateSignCount`, and `updateLastUsedAt` were using `WHERE credentialId = ?` (filtering by the Base64-encoded credential ID byte array) but were being passed the internal database `id` (the raw string). 

Since the internal ID never matched the Base64 string, the `UPDATE` and `SELECT` operations matched zero rows. Consequently, the signature counter remained at `0` in the database, even though the application logic was attempting to increment it. This resulted in the authenticator always returning a `signCount` of `1` in the `GetAssertion` response, triggering the RP's security check.

### Solutions
Updated the SQL queries in `PasskeyCredential.sq` to use the correct primary key `id` for identifying records during counter updates and retrievals.

### Modified Files
- `PasskeyCredential.sq`: Updated `WHERE` clauses to use `id`.
- `PasskeyCredentialDao.kt`: Updated to match the new SQLDelight generated parameter names (changing `credentialId` to `id`).

### Verification
- **Manual**: Verified that multiple consecutive logins for the same account now succeed on `webauthn.io`.
- **Build**: Successfully compiled `:feature:fido2` and verified all tests pass.

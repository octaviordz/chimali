# Dormant passkey-label update path review

**Finding:** SEC065-DB-013  
**Scope:** Custom-label update API, encrypted FIDO database storage, and legacy label compatibility.

The pre-removal source trace found a custom-label update use case with no production caller. The credential-details UI explicitly records that its custom-label field was removed. The production `Fido2Module` provides `Fido2Database` through `EncryptedDriverFactory`, which opens the database using SQLite3MultipleCiphers and a derived database key. The SQL `TEXT` declaration therefore did not establish an unencrypted database file or a plaintext-at-rest defect.

Removed the unused update use case, repository API/implementation, DAO method, and SQLDelight update query. The legacy column and model field remain so existing labels are preserved by schema migration and row hydration; this does not discard user data or claim to erase old labels. Current source has no production label-update caller, and the UI does not present that field.

Verification on 2026-11-16: full FIDO host suite passed 1,273 tests across 168 reports with zero failures/errors/skips; KtLint and Detekt passed. The one-case `Fido2DatabaseCredentialMigrationTest` passed and continues to verify preservation of `legacy-label` through the database migration; its XML report SHA-256 is `d52319d12f6368ac55ec4ca27212812182a622c309cb95fb82c8400256a9a7c4`. Repository test XML report SHA-256 is `2d9d5d749d76653eafd8b5614557bac665d86dd3c0dfbe3199689c3ca187600b`. No physical database-file, page/WAL residue, backup, or heap observation is claimed. The broader projection owner/lifetime review remains open under T005/T006/T012.

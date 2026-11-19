# Relying-party projection row cleanup

Date: 2026-11-15  
Finding: SEC065-DB-011

`RelyingPartyEntity.toDomainModel` already erased mutable row `id` and `name` buffers in its `finally` path, including timestamp/decode rejection. The entity also carries optional `encrypted_metadata`, but `eraseSensitiveMetadata` left that result buffer untouched. This diverged from the credential projection mapper, which clears its encrypted metadata buffer.

The RP row eraser now clears `encrypted_metadata` as well. `AuditTimestampMappingTest` seeds a synthetic encrypted metadata buffer and asserts it is zeroed on successful mapping and on rejected timestamp mapping, alongside the existing ID/name checks.

Focused and full host evidence: `:feature:fido2:testAndroidHostTest --tests AuditTimestampMappingTest`, followed by `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck`. The full FIDO host suite passed 1,258 tests across 167 reports with zero failures/errors/skips; KtLint passed.

This establishes cleanup of application-owned SQLDelight row arrays only. It does not prove SQLDelight/JDBC/Android provider copies, SQLite page/WAL erasure, or heap-root absence.

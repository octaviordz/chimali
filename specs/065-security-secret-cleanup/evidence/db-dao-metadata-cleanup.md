# Passkey DAO encrypted metadata cleanup

**Date:** 2026-11-14  
**Revision:** `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57` plus dirty exploratory worktree  
**Scope:** `PasskeyCredentialDao.insertCredential`, finding SEC065-DB-007  
**Test:** `com.chimali.fido2.data.database.CredentialAuditSchemaTest`

## Procedure and observation

The focused Gradle host test was run against the new assertion before changing the DAO. It exited with code 1 after one test, failing with: “The DAO must erase the encrypted metadata buffer after SQLDelight consumes it”. This showed that `CredentialMetadataProtectionService.encryptCredentialMetadata()` returned a mutable output array that remained populated after insertion.

The DAO was changed to retain the encrypted output in an operation-owned nullable reference and fill it with zeroes in `finally`, alongside its plaintext UTF-8 and lookup-token buffers. The focused test was rerun after the change and passed. It verifies both that the mock-returned encrypted buffer is zeroed and that the in-memory SQLite row still contains the expected bytes, demonstrating a distinct bind/persisted value for this host driver.

The test uses the SQLDelight JDBC in-memory driver and synthetic metadata. It does not establish production SQLite3MultipleCiphers behavior, native/provider copy cleanup, backup behavior, or heap-root absence. The initial failing Gradle console output was observed during the implementation run but was not retained as a raw transcript; the failure summary above records the observed command result. The current passing XML report is available at `feature/fido2/build/reports/tests/testAndroidHostTest/index.html`.

## Status

Passed for the scoped DAO and searchable-metadata migration buffer regressions. SQLDelight/native driver ownership and broader runtime evidence remain open under T005, T012, and T015.

## Relying-party DAO follow-up

The same focused test was extended to retain the result of `encryptRelyingPartyMetadata`, assert the output buffer is zero after insertion, and assert that the in-memory SQL row still contains the expected bytes. Before the `RelyingPartyDao` correction, the focused test exited with code 1 and failed with: “The DAO must erase the encrypted relying-party metadata buffer after SQLDelight consumes it”. `RelyingPartyDao.writeRelyingParty` now clears its encrypted metadata output in `finally` for insert and update. The focused test was rerun and passed after the correction.

This remains host SQLite ownership evidence, not proof of production native-driver copies or runtime encrypted-file behavior. The initial failing Gradle console output was observed in the implementation run but not retained as a raw transcript.

## Searchable metadata migration follow-up

`SearchableMetadataMigrationTest.test successful migration of legacy records` now retains references to the encrypted credential and relying-party arrays returned by the mock protection service, asserts both are zero after migration, and confirms both database rows retain their expected bytes. Before the source correction, the test failed at the first nonzero-buffer assertion (`SearchableMetadataMigrationTest.kt:142`). `SearchableMetadataMigrationState` now clears each encrypted output in the corresponding row `finally`. The focused `SearchableMetadataMigrationTest` class was rerun after correction: 9 tests passed.

This remains host SQLite ownership evidence, not proof of production native-driver copies or runtime encrypted-file behavior. The initial failing Gradle console output was observed in the implementation run but not retained as a raw transcript.

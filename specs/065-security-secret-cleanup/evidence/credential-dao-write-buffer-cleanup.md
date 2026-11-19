# Credential DAO write-buffer cleanup

**Date:** 2026-11-15  
**Scope:** Synthetic JVM-host assertions for `PasskeyCredentialDao.insertCredential` and `updateCredential`.

`PasskeyCredentialDaoTest` now retains references to the exact UTF-8, lookup-token and encrypted-metadata arrays passed to SQLDelight. The insert success case snapshots values at the bind boundary, then confirms every retained array is zero. A new injected SQLDelight failure case confirms the same cleanup on exceptional exit. The update case also verifies all three bound UTF-8 buffers are erased after return. The assertions guard existing `finally` cleanup and do not claim that SQLDelight, JDBC, native drivers or persisted database pages erase their own copies.

The focused command was:

```text
.\gradlew.bat :feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.dao.PasskeyCredentialDaoTest :feature:fido2:ktlintCheck
```

Observed result: build successful, six nested DAO tests pass with zero failures/errors/skips, and FIDO KtLint passes. Production encrypted-driver and database-page behavior, generated bind copies, immutable String projections and heap roots remain open under T005/T006/T012/T015/T036.

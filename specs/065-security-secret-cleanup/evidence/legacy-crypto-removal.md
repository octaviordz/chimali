# Legacy credential crypto removal

## Assessment record

- **Requirement:** FR-001/002/008/009; T005/T031/T033
- **Scope:** Unreachable `CredentialEncryptionService` and its only dependency, `CredentialStorageService`, including their generated DI registrations and direct host tests
- **Revision:** Current dirty worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`; exploratory, not a frozen candidate
- **Named procedure:** Production-source reference scan, FIDO KSP regeneration, and `:feature:fido2:testAndroidHostTest`
- **Environment:** Windows; Gradle 9.6.0; JDK 25 launcher
- **Expected and observed result:** No production source references outside the two legacy classes; both classes and their dedicated tests removed; regenerated Koin sources contain no matching bindings; all remaining FIDO host tests pass.
- **Date:** 2026-11-14
- **Limitations:** Repository source inspection and FIDO host build only. This does not establish behavior in a frozen product candidate, other diagnostic destinations, physical devices, or heap/provider copies. Historical test results for the removed classes apply only to the earlier source state.
- **Status:** Passed (current source removal and module build)
- **Artifact links:** `feature/fido2/build/reports/tests/testAndroidHostTest/index.html`; `feature/fido2/build/generated/ksp/android/androidMain/kotlin/org/koin/ksp/generated/`

## Reachability and change

Before removal, repository-wide source search found no production caller of `CredentialEncryptionService` or `CredentialStorageService` outside their own dependency relationship. The classes were registered by `@Single`, but no source constructed, injected, looked up, or called them. The active metadata implementation is `CredentialMetadataProtectionService`, which remains in the feature.

Removed these source files:

- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/CredentialEncryptionService.kt`
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/service/CredentialStorageService.kt`
- `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/crypto/CredentialEncryptionServiceTest.kt`
- `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/service/CredentialStorageServicePrivacyTest.kt`

The two dedicated host suites had 15 tests total. They exercised direct legacy class behavior, not normal application reachability. Their historical passes remain in the evidence index but are not current-tree runtime evidence.

## Verification

The configured source roots were searched for both class names, excluding build output. Stale exception-class KDoc references were corrected, and no source references remained. FIDO KSP regenerated the Android main module; generated Kotlin/Java source search found no legacy service classes or DI bindings.

Command:

```powershell
.\gradlew.bat :feature:fido2:testAndroidHostTest
```

Result: `BUILD SUCCESSFUL`; all 1,218 remaining test cases across 163 XML reports passed, with 0 failures, 0 errors, and 0 skipped tests. FIDO Android main and Android host test sources compiled successfully.

## Inventory reconciliation

Removed the four deleted files and their three owned usage records from `source-inventory.json`. The current inventory contains 632 files and 71 usages. This exploratory-tree validation does not replace frozen-candidate reconciliation under T036 or final evidence validation under T037.

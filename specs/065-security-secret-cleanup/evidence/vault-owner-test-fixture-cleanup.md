# Vault owner-test fixture cleanup

- **Finding:** SEC065-VAULT-014
- **Owners:** `VaultClipboardOwnershipTest` synthetic copy buffers; `VaultSubmissionOwnershipTest` synthetic payload/VaultItem fixture graphs
- **Classifications:** CLS-VAULT-001/002/003
- **Requirements:** FR-003/004/010; NFR-SEC-041; TR-AUTH-031; T005/T006/T012
- **Data:** Synthetic host-test values only.

## Change

`VaultClipboardOwnershipTest` now tracks every mutable copy/detail buffer created for an intent, then zeros and releases those references in `@After`, including when an assertion fails. `VaultSubmissionOwnershipTest` tracks each synthetic `PasswordPayload` and `VaultItem`, including copied drafts and service outputs; teardown clears the ViewModelStore first, then erases and releases the test owners even if cleanup exits exceptionally.

The tests continue to assert the production ViewModel's own cleanup and editor/draft isolation. Test teardown cleanup is separate and prevents test-owned references from extending fixture lifetime after a test case.

## Verification

- Focused `:feature:vault:testDebugUnitTest --tests com.chimali.feature.vault.internal.VaultClipboardOwnershipTest --tests com.chimali.feature.vault.internal.VaultSubmissionOwnershipTest :feature:vault:ktlintCheck`: passed.
- Full `:feature:vault:testDebugUnitTest :feature:vault:ktlintCheck` rerun after the focused run restored all reports: 98 tests across 23 reports, 0 failures/errors/skips.
- Current `VaultClipboardOwnershipTest.xml` SHA-256 `9D9E535B4BB117455B5AB8AEC71A9D583AE507D2122FE6676C707549D3C826D0` (13 tests); `VaultSubmissionOwnershipTest.xml` SHA-256 `1E6474C7E565C507B0CB60FD812AEC86413E702B34D14A378D71ABDAEC4F76B1` (6 tests).
- `VaultCryptoServiceTest.xml` SHA-256 after the same full run: `85944135012EAA3F3DDF0D4A0008E937092F297385360F482A363BC36B2CC305` (8 tests).

## Limits

This is host-test fixture ownership evidence. It does not prove JVM literal, Compose, provider/native, database, Android runtime or heap-root cleanup.

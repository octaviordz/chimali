# Vault crypto service test owner cleanup

- **Finding:** SEC065-VAULT-013
- **Owners:** `VaultCryptoServiceTest` synthetic payloads, encrypted `VaultItem` outputs, decrypted payload results, and mock key
- **Classifications:** CLS-VAULT-001/002
- **Requirements:** FR-003/004/010; NFR-SEC-041; TR-AUTH-031; T005/T006/T012
- **Fixtures:** Synthetic values only; no production account or vault data.

## Change

The success-path tests for password, card and note crypto now clear each returned decrypted payload and encrypted `VaultItem` in `finally`, so assertions or exceptions do not bypass test-owned array cleanup. The malformed-payload case clears its synthetic `VaultItem`; the cancellation case clears its retained input owner; and `@After` zeros the shared mock key, zeros any retained encryption-key reference and drops that reference.

Before this edit, the success tests asserted on decrypted mutable payloads and then returned without calling `clearMemory`; `VaultItem` title, encrypted payload and CRDT buffers also remained populated. The key-provider failure/cancellation paths already exercise production service cleanup; this correction is scoped to the host test's own retained fixture references.

## Verification

- Focused `:feature:vault:testDebugUnitTest --tests com.chimali.feature.vault.internal.crypto.VaultCryptoServiceTest :feature:vault:ktlintCheck`: passed (8 tests, 0 failures/errors/skips).
- Restored full `:feature:vault:testDebugUnitTest :feature:vault:ktlintCheck`: passed across 23 XML reports and 98 tests, 0 failures/errors/skips.
- Current `VaultCryptoServiceTest` report SHA-256: `72E10BED7A758D41918171FE338C638DCD5325752D2DD907C59FE88F391B853E`.

## Limits

This is host-test fixture cleanup. It does not change production crypto behavior or prove provider/native, database, Android runtime, or heap-root cleanup. Other test classes and full-suite ownership reconciliation remain separately tracked.

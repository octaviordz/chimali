# Vault payload copy owner isolation

- **Finding:** SEC065-VAULT-005
- **Classifications:** CLS-VAULT-001 and CLS-VAULT-002
- **Requirements:** FR-VAULT-026; FR-003/010; NFR-SEC-041; TR-AUTH-031; T005/T006/T012/T015/T027/T041
- **Environment:** Windows JVM host; Gradle 9.6.0/JDK 25; synthetic payload values.

## Finding and correction

The generated `copy()` methods on the four vault payload data classes shallow-copied mutable `CharArray` properties. Clearing an original would also clear a nominal copy; replacing one custom field would preserve an alias to the unchanged array. `PayloadCopyOwnershipTest` failed before the change in both regression cases. The four owner types are now regular classes with explicit `copy()` implementations that clone omitted arrays and nested custom fields. Arrays or lists explicitly supplied by a caller transfer ownership to the result. Existing value equality, content-based hash codes, redacted `toString()`, and deep `copyForEditing()` are retained. Repository search found no in-repo destructuring/componentN consumers.

## Verification

- Focused `PayloadCopyOwnershipTest`: 2 cases pass after fix; pre-fix run failed both cases.
- Full `:feature:vault:testDebugUnitTest :feature:vault:ktlintCheck`: 93 tests across 21 XML reports; 0 failures, errors, or skips; KtLint passes.
- Tests clear originals and verify copied password, note, card, and nested custom-field arrays remain unchanged; a field-replacement copy verifies its unchanged secret array is independent.

## Limits

No Android instrumentation/device run. This proves only app-owned mutable-array copy isolation on JVM. Explicit replacement buffers transfer to the copy result by contract; Compose/IME snapshots, crypto provider copies, persistence/native buffers and heap roots remain open under T005/T006/T012/T015/T027.

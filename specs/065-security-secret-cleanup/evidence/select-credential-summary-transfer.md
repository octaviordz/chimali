# Selected credential summary transfer

**Scope:** `SelectCredentialUseCase` and its host test fixture owners; synthetic credential summaries and request options.

`SelectCredentialUseCase` returns the selected existing `CredentialSummary` reference without copying or erasing it. Every candidate remains caller-owned until all downstream borrowers finish; `GetAssertionUseCase` clears its candidate list at the outer operation boundary. The test now verifies identity preservation and that selection itself leaves the summary readable. Its `@AfterTest` cleanup erases all tracked summary RP text and option RP/client-data-hash fixture arrays; the identity regression also invokes cleanup and verifies zeroed arrays directly.

The `SelectCredentialUseCaseTest` class passes six cases, including the selected-reference identity assertion and direct caller cleanup check. The full FIDO host suite passes 1,272 tests across 168 XML reports with zero failures/errors/skips; KtLint and Detekt pass. XML SHA-256: `eadd10ccce7aa9f0425494aabe249270c718761b705d70c33ddb071f0489add7`.

Source and host evidence do not establish every caller, concurrent borrowers, heap roots or frozen-candidate acceptance (T005/T006/T015/T037).

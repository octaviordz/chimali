# Relying-party timestamp test row cleanup

**Date:** 2026-11-17  
**Requirements:** FR-002/003/004/010; T005/T006/T012.

`RelyingPartyTimestampInvariantTest` uses synthetic RP IDs/names and queries generated `Relying_party` rows whose ID, name, and optional encrypted-metadata fields are mutable byte arrays. The test now tracks every query row, query-ID encoding, and constructed `RelyingParty` object, then erases/releases them in unconditional `@AfterTest`. The temporary expected-name byte arrays also clear in `finally`. Existing in-body cleanup remains to verify normal owner transfer; teardown covers exceptional/assertion exits.

A new regression directly constructs retained row/query/domain owners, invokes teardown, and asserts the row fields, query ID, and domain RP text are zeroed. The focused class passes 3 tests; the complete FIDO host suite passes 1,291 tests across 171 XML reports with zero failures/errors/skips; FIDO KtLint passes.

This is synthetic host-test fixture evidence. The in-memory SQLite database and driver are closed by each test's `finally`; this does not prove native SQLite/provider erasure or production query-row ownership. The caller is mapped under `CLS-FIDO-002` and existing RP DAO finding `SEC065-DB-008`.

Source SHA-256: `563f789352d87ca7892b6db4db36b8a8cd3c739130bdf66787a896efe073b193`  
Focused XML SHA-256: `6c5d660e19c1570aaa46e4d209289a429726b791511a665dbd84c6e4e44ab90d`.

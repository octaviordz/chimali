# Passkey event JSON path-key projection cleanup

**Finding:** Supplemental source-owner cleanup for `U-FIDO-EVENT-JSON-OWNER-001` / SEC065-CORE-024
**Date:** 2026-11-17
**Candidate:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`

`SensitiveJsonFields.JsonFieldParser.parseObject` previously converted each parsed object key to an immutable `String` and extended a `List<String>` path to compare against the configured extraction path. The parser now tracks the number of matched path components and compares each mutable parsed key array directly with the corresponding configured path component. Parsed key arrays are cleared before the parser advances. Selection semantics remain exact: only values below the configured object path are extracted, while similarly named fields before or after that object remain untouched.

Ran `:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.data.eventsourcing.PasskeyEventStorageOwnershipTest' :feature:fido2:ktlintCheck` on Windows with Gradle 9.6.0 and JDK 25. All 19 tests in `PasskeyEventStorageOwnershipTest` pass, including exact one-level and nested-path extraction with same-named fields outside the configured path; FIDO KtLint passes. Test XML SHA-256: `920E79C1187BF7B6CD97EA2D2E1D3A19E2183874BFFE85DCBAA8FCD3767885AE`. The KtLint report is empty (SHA-256 `E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855`). The subsequent complete `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck` run passes 1,318 tests across 172 reports, with zero failures/errors/skips; full report index SHA-256: `BD68CC3B61A876AC68166A29476755AECC09818D872ECEC31AFD40E86DF49708`.

This is host/source evidence for the parser's transient input-key handling. It does not prove SQLDelight/provider copies, event replay borrower completion, heap-root absence or frozen-candidate behavior; those remain open under T005/T006/T012/T015.

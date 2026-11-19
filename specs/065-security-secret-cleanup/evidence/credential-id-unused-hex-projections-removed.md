# Unused FIDO credential ID hex projections removed

- **Requirements:** FR-003/004/010; NFR-SEC-041; TR-AUTH-031; T005/T006/T012; SEC065-CORE-011.
- **Scope:** Unused `getIdHex()` accessors on `PublicKeyCredentialDescriptor` and `PublicKeyCredentialUserEntity`, and unused `PublicKeyCredentialUserEntity.id` / `getIdBase64Url()` immutable projection APIs; direct user-ID erasure and failed-copy assertions now use the mutable encoded-ID owner.
- **Revision:** Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57`.
- **Named procedure:** Repository source-reference search for the user-entity ID projections and `getIdHex`; focused `Fido2DomainModelsTest`, `RequestEntityOwnershipFailureTest` and `Ctap2GetAssertionHandlerTest`; complete `:feature:fido2:testAndroidHostTest`; `:feature:fido2:ktlintCheck`.
- **Environment:** Windows host; Gradle 9.6.0; JDK 25; synthetic user and credential identifiers.
- **Expected and observed:** Repository source search found no production callers for either hex API or the user-entity `id` / `getIdBase64Url()` projections. Those usages were confined to model and failure-path tests. The descriptor Base64URL accessor remains used by `AssertionObject`. Removed both hex APIs and the unused user-entity immutable projection APIs. Tests now verify the user entity’s scoped mutable encoded-ID owner and inspect its owned bytes after terminal cleanup without deriving a hex string. Focused model, request-copy and response tests pass; the full FIDO host suite passes 1,317 tests across 172 XML reports with zero failures/errors/skips; FIDO KtLint passes.
- **Limitations:** The existing Base64URL compatibility projections remain where protocol/API callers require them; those immutable outputs and caller lifetime are separate boundaries. This does not establish provider/native copies, UI/system snapshots, heap roots or frozen-candidate behavior.
- **Date:** 2026-11-17.
- **Status:** Passed for the source-reachability removal and named host behavior; remaining immutable Base64URL/platform/provider/heap outcomes remain Unverified.
- **Artifacts:** Changed sources and focused XML reports are indexed in `evidence/index.md`; source-inventory hashes match the current tree.

# Credential search flow rejected-emission cleanup

| TRD key | Record |
|---|---|
| `requirement` | NFR-SEC-041; TR-AUTH-031; FR-003/004/010; T005/T006/T012; SEC065-FIDO-OWNER-001 |
| `scope` | `CredentialRepositoryImplTest.CredentialManagementTests.search erases transferred credential when downstream rejects emission`: synthetic row hydration, ownership transfer to a collector, collector exception, emitted mutable-owner erasure, and source-row erasure. |
| `revision` | Commit `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57` plus dirty exploratory worktree; test source SHA-256 recorded in the evidence index. |
| `named_procedure` | `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.repository.CredentialRepositoryImplTest` and `:feature:fido2:ktlintAndroidHostTestSourceSetCheck`. |
| `environment` | Windows host; Gradle 9.6.0; JDK 25.0.5; Android host-test JVM; synthetic `Passkey_credential` fixture and synthetic search query. |
| `expected_observed_result` | Expected: a downstream exception must not strand the just-emitted credential or source row. Observed: the test retained the emitted model, threw a synthetic downstream exception, then verified all four mutable text fields and the source row text/identifier arrays were zeroed. The repository class passed 9 tests and its nested credential-management class passed 38 tests; both report zero failures/errors/skips. KtLint passed. |
| `date` | 2026-11-17. |
| `limitations` | Host-only evidence for this rejection path. It does not establish downstream borrower completion after a successfully accepted emission, SQLDelight/native copies, provider copies, release behavior, heap roots, or frozen-candidate validity. |
| `status` | Passed for the named host regression; broader T005/T006/T012/T015 remain open. |
| `artifact_link` | Test source, XML reports for the outer and nested repository test classes, and `source-inventory.json` are referenced by the evidence index. |

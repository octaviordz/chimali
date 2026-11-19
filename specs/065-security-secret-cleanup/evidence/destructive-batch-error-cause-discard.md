# Destructive credential batch cause discard

**Date:** 2026-11-15
**Scope:** Destructive credential snapshot query and hydration failures.

The fixed `DatabaseError`/`ValidationError` messages previously retained raw SQL or mapped-error causes. Two new regression assertions failed before correction: SQL query failure returned the synthetic exception as `cause`, and query collection failure did the same. `DestructiveCredentialBatchReader` now returns fixed errors without attaching SQL or mapping throwables. Cancellation still propagates; existing tests verify the whole operation fails closed and rows/partial credentials are erased.

The focused `CredentialRepositoryDestructiveBatchTest` passes 8 cases with zero failures/errors/skips. The full FIDO host suite passes 1,260 tests across 167 reports with zero failures/errors/skips, and FIDO KtLint passes. This does not establish provider/native database copies or caller terminal cleanup.

JUnit XML SHA-256: `a8e01eae5235a6306031ff54750602367ab935531932abe7d66a6f77ec1f6584` (`feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.repository.CredentialRepositoryDestructiveBatchTest.xml`).

# Consent context non-collection

**Date:** 2026-11-17  
**Finding:** SEC065-DB-003  
**Scope:** New consent-record writes from `UserConsentRecordDao.insertConsent`.

Source tracing found no established product need or retention policy for optional consent IP address, user-agent, or device-ID fields. `GetUserConsentUseCase` constructs these three values as `null`. The model and SQL schema still support the values, and older rows may contain them, so this correction does not remove the columns or mutate existing records.

The DAO now always binds `NULL` for the three context columns. The focused SQLite owner test supplied synthetic non-null values to the model and verified that the inserted row contains `NULL` for each column while the normal RP projection still round-trips and its mutable row buffers are cleared.

**Verification:** `:feature:fido2:testAndroidHostTest --tests RelyingPartyConsentOwnershipTest` passed all 5 tests (0 failures/errors/skips). The full `:feature:fido2:testAndroidHostTest` suite then passed 1,309 tests across 172 reports with zero failures/errors/skips, restoring the indexed test-result artifacts. `:feature:fido2:ktlintCheck` passed.

**Artifacts:**

- `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.dao.RelyingPartyConsentOwnershipTest.xml` SHA-256 `2675978df899e4969f3687f05fe0cfaaddfcd0e704ac8137e2e0f16dec620a132`
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/dao/UserConsentRecordDao.kt` SHA-256 `6f32fa8ae89f5aa2c6e75cf018975bb5eb9cc924ede848d12d94597464dd20b`
- `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/dao/RelyingPartyConsentOwnershipTest.kt` SHA-256 `c5f678e94a0b6aa3b770d2cc44a6f06e1dc048fa0570a1cc59a058b37d055616`

**Limitations:** This is an in-memory JDBC/SQLite result. It does not erase historical non-null rows, define a legacy-data deletion schedule, or prove SQLDelight/native/production-driver, database-page/WAL, backup, or heap cleanup. Those remain open under T005/T012/T015.

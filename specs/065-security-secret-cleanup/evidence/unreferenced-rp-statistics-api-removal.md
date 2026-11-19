# Unreferenced relying-party statistics API removal

Date: 2026-11-15  
Finding: SEC065-DB-012

Repository-wide current-source search found no production caller or test caller for `RelyingPartyDao.getRelyingPartyStatistics`, `RelyingPartyStatistics`, `TopRelyingParty`, or SQLDelight `get_top_relying_parties`. The method queried RP `id`/`name` BLOBs and returned them in mutable arrays without a production caller that could release them.

A reflection regression asserting that the public statistics method is absent failed before the removal. The unused DAO method, returned data classes, limit constant, and named SQLDelight query are now removed; the regression passes.

Verification: `:feature:fido2:testAndroidHostTest --tests RelyingPartyConsentOwnershipTest`, followed by the full `:feature:fido2:testAndroidHostTest :feature:fido2:ktlintCheck`. Full FIDO host results: 1,259 tests across 167 reports, zero failures/errors/skips; KtLint passes.

This finding covers only the proven dead statistics data path. Other raw-row DAO methods and provider copies require separate reachability/owner review; no SQLite physical erasure or heap-root claim is made.

# Historical consent context migration

| TRD key | Record |
|---|---|
| `requirement` | SEC065-DB-003; FR-003/004/010; T012 |
| `scope` | Remove previously persisted optional consent `device_id`, `ip_address`, and `user_agent` values when a FIDO database upgrades from schema version 3 to 4. New writes already bind these fields as SQL NULL. |
| `revision` | Dirty exploratory worktree; migration and instrumentation source updated 2026-11-17. |
| `named_procedure` | Focused `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.database.Fido2DatabaseCredentialMigrationTest`, followed by full `:feature:fido2:testAndroidHostTest` and `:feature:fido2:ktlintCheck`; the migration class exercises schema upgrade and credential preservation. |
| `environment` | Windows; Gradle 9.6.0; JDK 25 launcher; SQLDelight generated schema and JDBC in-memory SQLite driver. |
| `expected_observed_result` | Both migration-class cases passed. Migration 3 set all three optional identifying context columns to NULL for a synthetic legacy row and preserved its credential ID and RP bytes. The existing credential migration test also passed with the new schema migration in its full version-1-to-current path. The full FIDO host suite passed 1,312 tests across 172 reports with zero failures/errors/skips, and FIDO KtLint passed. |
| `date` | 2026-11-17 (America/Mexico_City). |
| `limitations` | Synthetic in-memory SQLite only. No existing production database was inspected or migrated on a device. SQLite driver memory, rollback journal/WAL/page remnants, encrypted-driver behavior, backups, OS/provider copies, immutable model Strings, and heap retention remain unverified. The schema columns remain nullable for compatibility; this migration test proves the requested SQL operation, not physical erasure of prior storage pages. |
| `status` | Passed for the scoped migration logic and compatibility regression; database/runtime erasure remains Unverified. |
| `artifact_link` | `feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/Fido2Database/3.sqm`; `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/database/Fido2DatabaseCredentialMigrationTest.kt`; test report `feature/fido2/build/reports/tests/testAndroidHostTest/index.html`. |

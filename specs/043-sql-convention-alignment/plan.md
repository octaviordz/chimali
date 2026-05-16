# Implementation Plan: SQL Convention Alignment

**Branch**: `043-sql-convention-alignment` | **Date**: 2026-05-16 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/043-sql-convention-alignment/spec.md`

## Summary

Refactor all SQL schema definitions (`.sq`), migration files (`.sqm`), and downstream Kotlin code across both database modules (`core:database` and `feature:fido2`) to enforce the naming and column-ordering conventions mandated by Constitution §X.3. Table names change from PascalCase to `snake_case` singular, column names from camelCase to `snake_case`, views and indexes are renamed accordingly, and column ordering is standardised to PK → audit → alphabetised. No business logic changes.

## Technical Context

**Language/Version**: Kotlin 2.1.x (KMP), SQL (SQLDelight 2.x)
**Primary Dependencies**: SQLDelight 2.x, SQLCipher
**Storage**: SQLCipher-encrypted SQLite via SQLDelight (two databases: VaultDatabase, Fido2Database)
**Testing**: JUnit 5, MockK, kotlin.test
**Target Platform**: Android (minSdk 28)
**Project Type**: Android Feature Modules (`core:database`, `feature:fido2`)
**Performance Goals**: Zero-data-loss migration; no performance regression
**Constraints**: SQLite does not support `ALTER TABLE RENAME COLUMN` reliably across all SQLCipher versions — must use the rename-recreate-copy-drop pattern
**Scale/Scope**: 5 `.sq` files, 11+ `.sqm` migration files, ~50+ Kotlin source files referencing generated types

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **X.3 SQL Table Naming**: Constitution mandates `snake_case` singular. Current schema violates this with PascalCase (e.g., `PasskeyCredential`, `VaultEntry`). This feature directly remediates.
- **X.3 SQL Column Naming**: Constitution mandates `snake_case`. Current schema violates this with camelCase (e.g., `createdAt`, `rpId`). This feature directly remediates.
- **X.3 SQL Column Ordering**: Constitution mandates PK → audit/temporal → alphabetised remaining. This feature enforces the convention.
- **X.3 SQL Index Naming**: Constitution mandates `idx_<table>_<column(s)>`. Current indexes reference old names. This feature remediates.
- **IX. Local CI/CD**: All changes must pass `tools/local-ci.ps1` before commit.
- **VIII. Event Sourcing**: `EventStore` and `SnapshotStore` tables exist in both databases. Renaming must not break event sourcing replay. The table-rename migration pattern (rename-recreate-copy-drop) preserves all data.
- **IV. Performance & Reliability**: Migrations must be wrapped in transactions and must not cause data loss.

All gates passed. The feature is a direct remediation of constitutional violations.

## Project Structure

### Documentation (this feature)

```text
specs/043-sql-convention-alignment/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output — table rename mapping
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Source Code (repository root)

```text
core/database/
└── src/main/sqldelight/com/chimali/core/database/
    ├── Vault.sq                    # Schema: Identity, IdentityBackup, VaultEntry, Label, VaultEntryLabel, EventStore, SnapshotStore
    └── VaultDatabase/
        ├── 2.sqm                   # Existing migration
        ├── 3.sqm                   # Existing migration
        └── 4.sqm                   # NEW — rename tables/columns to snake_case

feature/fido2/
├── src/commonMain/sqldelight/com/chimali/fido2/data/database/
│   ├── Fido2Database.sq            # Schema: passkey_credential, relying_party, etc.
│   ├── PairedDevice.sq             # Queries for paired_device
│   ├── PasskeyCredential.sq        # Queries for passkey_credential
│   ├── RelyingParty.sq             # Queries for relying_party
│   ├── UserConsentRecord.sq        # Queries for user_consent_record
│   └── Fido2Database/
│       ├── 2–10.sqm                # Existing migrations
│       └── 11.sqm                  # NEW — rename tables/columns to snake_case
└── src/androidMain/kotlin/com/chimali/fido2/
    ├── data/dao/                   # PasskeyCredentialDao.kt, RelyingPartyDao.kt
    ├── data/mapper/                # EntityMappers.kt
    └── data/repository/            # *RepositoryImpl.kt
```

**Structure Decision**: Modifications to existing files across two established modules. No new modules.

## Complexity Tracking

None. All changes align with constitutional mandates — no violations to justify.

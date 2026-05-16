# Feature Specification: SQL Convention Alignment

**Feature Branch**: `043-sql-convention-alignment`
**Created**: 2026-05-16
**Status**: Draft
**Input**: User description: "Refactor SQL data layer to align the code according to constitution (table names must be snake_case, column names must be snake_case, etc.). Non-goal: make logic changes."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Rename SQL Tables to snake_case (Priority: P1)

As a developer working on the Chimali codebase, I need all SQL table names to follow the `snake_case` singular convention mandated by Constitution §X.3 so that the schema is consistent, readable, and passes any future automated compliance checks.

**Why this priority**: Table names are the most visible element of the schema and are referenced across the entire data layer (DAOs, mappers, repositories, views, indexes). Renaming tables first establishes the foundation for all downstream column and index renaming.

**Independent Test**: After completion, every `CREATE TABLE` statement in all `.sq` and `.sqm` files uses `snake_case` singular names. SQLDelight compiles successfully and all existing unit tests pass without logic changes.

**Acceptance Scenarios**:

1. **Given** the current schema contains PascalCase table names (e.g., `PasskeyCredential`, `RelyingParty`, `VaultEntry`), **When** the refactoring is applied, **Then** all table names are `snake_case` singular (e.g., `passkey_credential`, `relying_party`, `vault_entry`).
2. **Given** a table has been renamed, **When** SQLDelight code generation runs, **Then** the generated Kotlin types reflect the new names and all Kotlin code referencing the generated types compiles without error.
3. **Given** a migration file introduces the rename, **When** the migration runs on an existing database, **Then** all data is preserved with zero loss.

---

### User Story 2 - Rename SQL Columns to snake_case (Priority: P1)

As a developer, I need all SQL column names to follow the `snake_case` convention mandated by Constitution §X.3 so that column names are consistent with table names and the overall schema standard.

**Why this priority**: Column naming violations are pervasive (camelCase columns like `createdAt`, `lastUsedAt`, `rpId`, `signCount`, etc.) and affect every query, mapper, and DAO in the codebase. This is equally critical as table renaming.

**Independent Test**: After completion, every column in all `.sq` and `.sqm` files uses `snake_case` names. SQLDelight compiles successfully and all existing unit tests pass without logic changes.

**Acceptance Scenarios**:

1. **Given** columns use camelCase (e.g., `createdAt`, `lastUsedAt`, `rpId`, `signCount`), **When** the refactoring is applied, **Then** all columns use `snake_case` (e.g., `created_at`, `last_used_at`, `rp_id`, `sign_count`).
2. **Given** columns are renamed, **When** Kotlin mapper/DAO code is updated to reference the new generated accessors, **Then** compilation succeeds and behavior is unchanged.
3. **Given** a migration renames columns in existing tables, **When** the migration runs, **Then** all data is preserved with zero loss.

---

### User Story 3 - Enforce Column Ordering Convention (Priority: P2)

As a developer, I need all `CREATE TABLE` statements to follow the canonical column ordering defined in Constitution §X.3 (PK first → audit/temporal → alphabetized remaining) so that the schema is predictable and reviewable.

**Why this priority**: Column ordering is a readability and maintainability convention. It has lower risk than renaming since it does not affect query results or generated Kotlin accessor names, but it does require table recreation in SQLite.

**Independent Test**: After completion, every `CREATE TABLE` in all `.sq` files follows the ordering: (1) PK column(s), (2) audit columns (`created_at`, `last_used_at`, `updated_at`) in that fixed order, (3) remaining columns alphabetized. SQLDelight compiles and all tests pass.

**Acceptance Scenarios**:

1. **Given** a table has columns in arbitrary order, **When** the refactoring is applied, **Then** the `CREATE TABLE` statement lists columns in the canonical order.
2. **Given** column order changes, **When** a migration runs, **Then** data is preserved via `INSERT INTO ... SELECT` pattern.

---

### User Story 4 - Rename Views and Indexes to snake_case (Priority: P2)

As a developer, I need all SQL views and indexes to follow the naming conventions mandated by Constitution §X.3 (views: `snake_case`; indexes: `idx_<table>_<column(s)>`) so the full schema is consistently named.

**Why this priority**: Views and indexes are secondary to tables and columns. Their renaming has a smaller Kotlin code surface and lower risk.

**Independent Test**: After completion, all `CREATE VIEW` and `CREATE INDEX` statements use `snake_case` names following the constitutional conventions. SQLDelight compiles and all tests pass.

**Acceptance Scenarios**:

1. **Given** views use PascalCase (e.g., `CredentialSummary`, `RelyingPartyStats`), **When** the refactoring is applied, **Then** views use `snake_case` (e.g., `credential_summary`, `relying_party_stats`).
2. **Given** indexes reference old column names (e.g., `idx_passkey_credential_rpId`), **When** the refactoring is applied, **Then** indexes reference the new `snake_case` column names (e.g., `idx_passkey_credential_rp_id`).

---

### User Story 5 - Update Kotlin Data Layer References (Priority: P1)

As a developer, I need all Kotlin code that references SQLDelight-generated types and accessors to compile cleanly after the SQL schema renaming, without any changes to business logic.

**Why this priority**: Without updating the Kotlin references, the build will fail. This is a mandatory companion to the SQL renaming stories.

**Independent Test**: After completion, the full project builds (`./gradlew build`) with zero compilation errors. All existing unit, integration, and instrumentation tests pass. No behavioral or logic changes are introduced.

**Acceptance Scenarios**:

1. **Given** SQLDelight regenerates Kotlin types after schema changes, **When** the Kotlin DAOs, mappers, repositories, view models, and test files are updated to reference the new generated accessor names, **Then** the project compiles with zero errors.
2. **Given** the refactoring is purely cosmetic (renaming), **When** all tests run, **Then** test outcomes are identical to the pre-refactoring baseline.
3. **Given** the local CI pipeline (`tools/local-ci.ps1`) runs, **Then** it completes successfully with zero violations.

---

### Edge Cases

- What happens when an `ALTER TABLE RENAME` migration encounters an existing database with a corrupted or partially migrated schema?
- How does the migration handle tables with foreign key constraints that reference renamed tables (e.g., `UserConsentRecord.rpId` → `RelyingParty`)?
- What happens when the Vault database (`core:database`) and FIDO2 database (`feature:fido2`) share identical table names (e.g., both have `EventStore` and `SnapshotStore`)?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: All SQL table names MUST be renamed from PascalCase to `snake_case` singular per Constitution §X.3.
- **FR-002**: All SQL column names MUST be renamed from camelCase to `snake_case` per Constitution §X.3.
- **FR-003**: All SQL view names MUST be renamed from PascalCase to `snake_case`.
- **FR-004**: All SQL index names MUST be updated to follow `idx_<table>_<column(s)>` using the new `snake_case` names.
- **FR-005**: All `CREATE TABLE` column ordering MUST follow the canonical order: PK → audit/temporal → alphabetized remaining.
- **FR-006**: SQLDelight migration files (`.sqm`) MUST be created to safely migrate existing databases, preserving all data using the rename-recreate-copy-drop pattern.
- **FR-007**: All Kotlin code referencing SQLDelight-generated types MUST be updated to compile against the new generated accessors. No logic changes are permitted.
- **FR-008**: All existing `.sq` query files MUST be updated to reference the new table, column, view, and index names.
- **FR-009**: The refactoring MUST NOT change any query logic, filtering behavior, sort ordering, or data semantics.
- **FR-010**: The refactoring MUST be applied to both database modules: `core:database` (VaultDatabase) and `feature:fido2` (Fido2Database).

### Key Entities

- **VaultDatabase** (`core:database`): Contains tables `Identity`, `IdentityBackup`, `VaultEntry`, `Label`, `VaultEntryLabel`, `EventStore`, `SnapshotStore`.
- **Fido2Database** (`feature:fido2`): Contains tables `PasskeyCredential`, `RelyingParty`, `UserConsentRecord`, `BluetoothHidSession`, `PairedDevice`, `EventStore`, `SnapshotStore`. Also contains views `CredentialSummary`, `RelyingPartyStats`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of SQL table names across both databases follow `snake_case` singular convention — zero PascalCase table names remain.
- **SC-002**: 100% of SQL column names across both databases follow `snake_case` convention — zero camelCase column names remain.
- **SC-003**: 100% of SQL views and indexes follow the constitutional naming conventions.
- **SC-004**: 100% of `CREATE TABLE` statements follow the canonical column ordering (PK → audit → alphabetized).
- **SC-005**: The project compiles with zero errors after all renaming changes.
- **SC-006**: All existing tests (unit, integration, instrumentation) pass with identical outcomes to the pre-refactoring baseline.
- **SC-007**: The local CI pipeline (`tools/local-ci.ps1`) passes with zero violations.
- **SC-008**: Database migrations preserve 100% of existing data — zero data loss on upgrade.

## Assumptions

- SQLite's `ALTER TABLE RENAME TO` is available and the rename-recreate-copy-drop migration pattern is the safe approach for column renaming (SQLite does not support `ALTER TABLE RENAME COLUMN` prior to version 3.25.0; the project targets a range of SQLite versions via SQLCipher).
- SQLDelight will regenerate Kotlin types automatically after `.sq` file changes; the Kotlin updates are purely accessor-name changes.
- The existing migration 7 (`7.sqm` in Fido2Database) already performed column reordering but did NOT rename to `snake_case`. The new migration will build on top of the current schema version.
- Both databases are independent (separate `.sq` files and migration tracks), so migrations can be developed and tested independently.
- No new audit columns need to be added as part of this refactoring — the existing columns are sufficient. Adding missing `created_at`/`updated_at` to tables that lack them (e.g., `EventStore`, `SnapshotStore`) is out of scope for this feature.

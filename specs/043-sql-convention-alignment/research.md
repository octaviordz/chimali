# Research: SQL Convention Alignment

**Feature**: 043-sql-convention-alignment
**Date**: 2026-05-16

## R1: SQLite Column Rename Strategy

**Decision**: Use the rename-recreate-copy-drop pattern for all table/column renames.

**Rationale**: SQLite's `ALTER TABLE RENAME COLUMN` was added in SQLite 3.25.0 (2018). However, the project uses SQLCipher which may bundle an older SQLite version depending on the device. The rename-recreate-copy-drop pattern (`ALTER TABLE X RENAME TO X_old; CREATE TABLE X (...); INSERT INTO X SELECT ... FROM X_old; DROP TABLE X_old;`) is universally compatible and is already the established pattern in the codebase (see `7.sqm` in Fido2Database).

**Alternatives considered**:
- `ALTER TABLE RENAME COLUMN`: Simpler syntax but not reliably available across all SQLCipher versions on Android SDK 28+.
- Raw `ALTER TABLE` + computed columns: Not applicable for this use case.

## R2: Migration Versioning

**Decision**: VaultDatabase gets migration `4.sqm` (next after existing `3.sqm`). Fido2Database gets migration `11.sqm` (next after existing `10.sqm`).

**Rationale**: Sequential numbering is required by SQLDelight's migration system. Each database maintains its own migration track.

**Alternatives considered**: None — SQLDelight mandates sequential versioning.

## R3: SQLDelight Generated Code Impact

**Decision**: Table renames change the generated Kotlin type names (e.g., `PasskeyCredential` entity class becomes `Passkey_credential` or similar based on SQLDelight's naming strategy). Column renames change the generated property names (e.g., `createdAt` becomes `created_at`). All Kotlin code referencing these generated types must be updated.

**Rationale**: SQLDelight generates Kotlin data classes from `CREATE TABLE` statements. The class name matches the table name and property names match column names. When we rename `PasskeyCredential` to `passkey_credential`, SQLDelight will generate a class named `Passkey_credential`. Import aliases in Kotlin (e.g., `import ... as PasskeyCredentialEntity`) can absorb the class name change, but property accessor changes (e.g., `.createdAt` → `.created_at`) require updates at every call site.

**Alternatives considered**:
- SQLDelight `AS` type alias: Could rename generated types but adds schema complexity. Not worth it for a one-time migration.

## R4: Foreign Key Handling

**Decision**: Foreign key constraints referencing renamed tables must be recreated with the new table names. The rename-recreate pattern naturally handles this since the `CREATE TABLE` in the migration defines foreign keys against the new table names.

**Rationale**: SQLite foreign keys reference table names by string. When we rename `RelyingParty` to `relying_party`, the `UserConsentRecord.rpId REFERENCES RelyingParty(id)` must become `user_consent_record.rp_id REFERENCES relying_party(id)`. The recreate pattern handles this atomically.

**Alternatives considered**: None — the recreate pattern is the only safe approach.

## R5: View and Index Recreation

**Decision**: All views and indexes must be dropped and recreated with new names in the migration, after the underlying tables are renamed.

**Rationale**: SQLite views and indexes reference table/column names. After renaming tables and columns, existing views become invalid. They must be dropped before table rename (to avoid FK issues) and recreated after with the new names.

**Order**: Drop views → Drop indexes → Rename tables (recreate pattern) → Recreate indexes → Recreate views.

**Alternatives considered**: None — this is the only correct approach.

## R6: Transaction Safety

**Decision**: Each database's entire migration must execute inside a single implicit SQLite transaction (SQLDelight handles this by default for `.sqm` files).

**Rationale**: If any step fails, the entire migration rolls back, leaving the database in its pre-migration state. This prevents partial renames that would corrupt the schema.

**Alternatives considered**: Manual `BEGIN/COMMIT` — unnecessary since SQLDelight wraps each `.sqm` in a transaction automatically.

## R7: Existing Migration 7.sqm Overlap

**Decision**: Migration `7.sqm` in Fido2Database already performs column reordering (PK → audit → alpha) but does NOT rename to `snake_case`. The new migration `11.sqm` will perform the snake_case rename on top of the already-reordered schema. No conflict exists because `7.sqm` is already applied to all existing databases.

**Rationale**: Migrations are sequential and additive. `11.sqm` operates on the schema as left by `10.sqm`. The column order established by `7.sqm` will be preserved (and re-verified) in `11.sqm`'s recreated tables.

**Alternatives considered**: Rewriting `7.sqm` — prohibited by Constitution §VIII (events must be immutable; migrations are analogous).

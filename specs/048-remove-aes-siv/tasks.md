# Tasks: Remove AES-256-SIV

**Input**: Design documents from `/specs/048-remove-aes-siv/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Test tasks are included because the specification requires verification for lookup behavior, tamper rejection, migration safety, and local CI.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish the working inventory and target files before touching policy or crypto code.

- [x] T001 Review the active feature artifacts in specs/048-remove-aes-siv/spec.md, specs/048-remove-aes-siv/plan.md, specs/048-remove-aes-siv/research.md, specs/048-remove-aes-siv/data-model.md, specs/048-remove-aes-siv/contracts/security-policy-contract.md, and specs/048-remove-aes-siv/contracts/metadata-lookup-contract.md
- [x] T002 Inventory current AES-SIV policy and production references in .specify/memory/constitution.md, docs/brd.md, docs/trd.md, core/security/src/commonMain/kotlin/com/chimali/core/security/api/SivEncryptionManager.kt, core/security/src/androidMain/kotlin/com/chimali/core/security/impl/AesSivEncryptionManager.kt, and feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/service/EncryptedMetadataIndexService.kt
- [x] T003 [P] Inventory exact-match and partial-search fields in feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/Fido2Database.sq, feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/PasskeyCredential.sq, feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/UserConsentRecord.sq, and feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/RelyingParty.sq
- [x] T004 [P] Inventory AES-GCM and key-boundary implementations in core/common/src/androidMain/kotlin/com/chimali/core/common/datastore/EncryptionWrapper.kt, feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt, feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/Fido2SettingsRepositoryImpl.kt, and feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/CredentialEncryptionService.kt

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Define shared security contracts and migration boundaries that all implementation stories rely on.

**Critical**: No user story implementation should begin until these tasks are complete.

- [x] T005 Create the searchable metadata field classification table in specs/048-remove-aes-siv/aes-siv-removal-analysis.md covering exact-match lookup tokens, encrypted metadata values, SQLCipher-only display fields, and deprecated plaintext columns
- [x] T006 Create the AES-SIV removal changelog stub in docs/changelogs/2026-05-20-aes-siv-removal.md with sections for constitution, storage migration, cleanup, and verification
- [x] T007 [P] Define lookup-token naming, versioning, and domain-separation constants in core/security/src/commonMain/kotlin/com/chimali/core/security/api/MetadataLookupTokenService.kt
- [x] T008 [P] Define encrypted metadata envelope naming, associated-data rules, and format version constants in core/security/src/commonMain/kotlin/com/chimali/core/security/api/EncryptedMetadataService.kt
- [x] T009 [P] Define key-wrapping review terms and platform-backed AEAD requirements in core/security/src/commonMain/kotlin/com/chimali/core/security/api/KeyWrappingPolicy.kt
- [x] T010 Validate that foundational docs and APIs do not leave unresolved AES-SIV policy conflicts in .specify/memory/constitution.md, docs/brd.md, docs/trd.md, and core/security/src/commonMain/kotlin/com/chimali/core/security/api/KeyWrappingPolicy.kt

**Checkpoint**: Foundation ready - policy, token, envelope, and key-wrapping terminology are stable enough for user story work.

---

## Phase 3: User Story 1 - Update Security Policy (Priority: P1) MVP

**Goal**: Amend project governance so AES-256-SIV is no longer mandatory and the replacement policy is explicit.

**Independent Test**: Review .specify/memory/constitution.md and confirm it permits AES-256-GCM/AEAD with unique nonces for encrypted values and key wrapping, requires keyed lookup tokens for exact-match metadata, prohibits deterministic GCM nonce misuse, and contains zero mandatory AES-256-SIV requirements.

### Tests for User Story 1

- [x] T011 [P] [US1] Add a policy review checklist for zero mandatory AES-SIV, lookup-token replacement, SQLCipher display exceptions, and AEAD key wrapping in specs/048-remove-aes-siv/checklists/security-policy.md
- [x] T012 [P] [US1] Add grep-based policy validation commands for AES-SIV, deterministic GCM nonce misuse, key wrapping, and Bouncy Castle scope in specs/048-remove-aes-siv/quickstart.md

### Implementation for User Story 1

- [x] T013 [US1] Amend Principle I in .specify/memory/constitution.md to remove mandatory AES-256-SIV and require deterministic keyed lookup tokens plus authenticated encrypted values for searchable metadata
- [x] T014 [US1] Amend key-wrapping language in .specify/memory/constitution.md to require platform-backed AES-GCM/AEAD with unique nonces and associated data instead of AES-SIV
- [x] T015 [US1] Amend SQL indexing guidance in .specify/memory/constitution.md to replace deterministic ciphertext guidance with keyed lookup-token and SQLCipher display-field guidance
- [x] T016 [US1] Update NFR-SEC-010 in docs/brd.md to describe AES-GCM/AEAD value encryption, keyed lookup tokens, SQLCipher display exceptions, and non-SIV Bouncy Castle scope
- [x] T017 [US1] Update the security technology section in docs/trd.md to replace AES-SIV searchable metadata with keyed lookup tokens and platform-backed AEAD key wrapping
- [x] T018 [US1] Update docs/research/AES_SIV_vs_GCM_Evaluation.md so current guidance no longer recommends AES-SIV as the mandatory searchable-metadata or key-wrapping primitive
- [x] T019 [US1] Complete the policy-change entry in docs/changelogs/2026-05-20-aes-siv-removal.md with constitution, BRD/TRD, and research-reference changes

**Checkpoint**: User Story 1 is independently reviewable and unblocks code removal by resolving the constitution conflict.

---

## Phase 4: User Story 2 - Preserve Searchable Metadata Behavior (Priority: P2)

**Goal**: Preserve credential lookup, filtering, duplicate checks, management search, and existing record accessibility using lookup tokens, encrypted metadata values, and SQLCipher-only display fields.

**Independent Test**: With seeded legacy data, exact-match lookup, duplicate checks, credential selection, and partial text management search return the expected records after migration.

### Tests for User Story 2

- [x] T020 [P] [US2] Add deterministic lookup-token tests for same input, different domain, versioning, and non-reversibility in core/security/src/test/kotlin/com/chimali/core/security/impl/HmacMetadataLookupTokenServiceTest.kt
- [x] T021 [P] [US2] Add encrypted metadata tamper and associated-data tests in core/security/src/test/kotlin/com/chimali/core/security/impl/AesGcmEncryptedMetadataServiceTest.kt
- [x] T021a [US2] Implement tamper detection and rejection logic in AesGcmEncryptedMetadataService with constant-time MAC verification and explicit error propagation for failed authentication
- [x] T022 [P] [US2] Add FIDO2 SQLDelight migration tests for legacy credential and consent rows in feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/database/SearchableMetadataMigrationTest.kt
- [x] T023 [P] [US2] Add credential repository lookup and partial-search regression tests in feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/repository/CredentialRepositorySearchableMetadataTest.kt
- [x] T024 [P] [US2] Add DAO exact-match token query tests in feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/dao/SearchableMetadataDaoTest.kt

### Implementation for User Story 2

- [x] T025 [US2] Implement HMAC-based lookup-token derivation in core/security/src/androidMain/kotlin/com/chimali/core/security/impl/HmacMetadataLookupTokenService.kt
- [x] T026 [US2] Implement AES-GCM encrypted metadata envelopes with associated data in core/security/src/androidMain/kotlin/com/chimali/core/security/impl/AesGcmEncryptedMetadataService.kt
- [x] T027 [US2] Register lookup-token and encrypted-metadata services in core/security/src/androidMain/kotlin/com/chimali/core/security/di/SecurityModule.kt
- [x] T028 [US2] Add lookup-token and encrypted-metadata columns plus indexes to feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/Fido2Database.sq
- [x] T029 [US2] Add the resumable SQLDelight migration for lookup tokens, encrypted metadata, and display-field retention in feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/Fido2Database/12.sqm
- [x] T030 [US2] Update credential exact-match queries to use lookup-token columns while keeping partial display search on classified SQLCipher fields in feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/PasskeyCredential.sq
- [x] T031 [US2] Update user consent RP exact-match queries to use lookup-token columns in feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/UserConsentRecord.sq
- [x] T032 [US2] Update relying-party search/query behavior and display-field classification in feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/RelyingParty.sq
- [x] T033 [US2] Create FIDO2 metadata protection orchestration in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/service/CredentialMetadataProtectionService.kt
- [x] T034 [US2] Update PasskeyCredentialDao writes and exact-match reads to persist and query lookup-token fields in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/dao/PasskeyCredentialDao.kt
- [x] T035 [US2] Update UserConsentRecordDao writes and exact-match reads to persist and query RP lookup-token fields in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/dao/UserConsentRecordDao.kt
- [x] T036 [US2] Update EntityMappers to hydrate domain models from encrypted metadata while preserving SQLCipher-only display fields in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/mapper/EntityMappers.kt
- [x] T037 [US2] Update CredentialRepositoryImpl search, duplicate-check, and credential-selection paths to use lookup-token services for exact matches in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/CredentialRepositoryImpl.kt
- [x] T038 [US2] Update RelyingPartyRepositoryImpl and RelyingPartyDao integration for display-field partial search and lookup-token exact matching in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/RelyingPartyRepositoryImpl.kt and feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/dao/RelyingPartyDao.kt

**Checkpoint**: User Story 2 preserves supported lookup/search behavior without AES-SIV.

---

## Phase 5: User Story 3 - Decommission AES-SIV-Specific Surface Area (Priority: P3)

**Goal**: Remove or replace AES-SIV-specific APIs, implementation code, documentation promises, and tests after replacement behavior exists.

**Independent Test**: Search production code for AES-SIV-specific production APIs and services and confirm no active dependency remains.

### Tests for User Story 3

- [x] T039 [P] [US3] Add or update security DI tests proving no SivEncryptionManager binding remains in core/security/src/androidHostTest/kotlin/com/chimali/core/security/di/SecurityModuleTest.kt
- [x] T040 [P] [US3] Add repository/service regression coverage proving EncryptedMetadataIndexService is not required in feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/repository/CredentialRepositoryImplTest.kt

### Implementation for User Story 3

- [x] T041 [US3] Remove the AES-SIV public API file core/security/src/commonMain/kotlin/com/chimali/core/security/api/SivEncryptionManager.kt after callers are migrated
- [x] T042 [US3] Remove the AES-SIV implementation file core/security/src/androidMain/kotlin/com/chimali/core/security/impl/AesSivEncryptionManager.kt after callers are migrated
- [x] T043 [US3] Remove AES-SIV DI comments and bindings from core/security/src/androidMain/kotlin/com/chimali/core/security/di/SecurityModule.kt
- [x] T044 [US3] Remove the unused AES-SIV metadata index service file feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/service/EncryptedMetadataIndexService.kt after lookup-token service is active
- [x] T045 [US3] Update core/security/build.gradle.kts so Bouncy Castle comments no longer claim the dependency is retained only for AES-SIV
- [x] T046 [US3] Update app/src/main/kotlin/com/chimali/ChimaliApplication.kt and feature/fido2/src/androidMain/kotlin/com/chimali/fido2/util/performance/WarmUpHelper.kt comments to classify remaining Bouncy Castle usage as non-SIV
- [x] T047 [US3] Update historical-reference notes in docs/changelogs/2026-04-03-fido2-cryptographic-and-transport-hardening.md and docs/changelogs/2026-05-20-proto-datastore-migration.md so they are clearly historical, not current policy

**Checkpoint**: User Story 3 leaves no active AES-SIV production surface while preserving non-SIV Bouncy Castle paths.

---

## Phase 6: User Story 4 - Validate Secure Migration (Priority: P4)

**Goal**: Prove AES-SIV removal does not weaken confidentiality, integrity, availability, or performance for existing data.

**Independent Test**: Run migration and regression tests against seeded datasets and confirm existing records remain accessible, tampered encrypted values are rejected, and local CI passes.

### Tests for User Story 4

- [x] T048 [P] [US4] Add seeded legacy migration coverage for interrupted and re-run migration paths in feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/database/SearchableMetadataMigrationTest.kt
- [x] T049 [P] [US4] Add tampered encrypted metadata rejection coverage for repository hydration in feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/repository/CredentialRepositorySearchableMetadataTest.kt
- [x] T050 [P] [US4] Add 10,000-record exact-match lookup performance coverage in feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/integration/SearchableMetadataPerformanceTest.kt
- [x] T051 [P] [US4] Add FIDO2 registration/authentication regression coverage using migrated records in feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/integration/RegistrationAuthenticationDataIntegrationTest.kt

### Implementation for User Story 4

- [x] T052 [US4] Add migration state reporting and retry handling for searchable metadata migration in feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/database/SearchableMetadataMigrationState.kt
- [x] T053 [US4] Add security-review evidence commands and expected outputs to specs/048-remove-aes-siv/quickstart.md
- [x] T054 [US4] Run focused host tests for security, DAO, repository, migration, and integration coverage using feature/fido2/build.gradle.kts
- [x] T055 [US4] Run the final local quality gate using tools/local-ci.ps1

**Checkpoint**: User Story 4 provides the final evidence required by the feature success criteria.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Finish consistency, traceability, and cleanup after the user stories are implemented.

- [x] T056 [P] Update specs/048-remove-aes-siv/quickstart.md with final command results and any renamed validation commands
- [x] T057 [P] Update specs/048-remove-aes-siv/contracts/security-policy-contract.md and specs/048-remove-aes-siv/contracts/metadata-lookup-contract.md if implementation names differ from design names
- [x] T058 Run final AES-SIV and deterministic-GCM search validation across .specify/memory/constitution.md, docs/brd.md, docs/trd.md, core/security, feature/fido2, app, and specs/048-remove-aes-siv
- [x] T059 Complete docs/changelogs/2026-05-20-aes-siv-removal.md with test evidence, remaining non-SIV Bouncy Castle scope, and local CI status

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**: No dependencies.
- **Phase 2 Foundational**: Depends on Phase 1 and blocks all user stories.
- **Phase 3 US1**: Depends on Phase 2 and must complete before AES-SIV production code removal.
- **Phase 4 US2**: Depends on Phase 2; implementation should not merge until US1 policy change is complete.
- **Phase 5 US3**: Depends on US1 and US2 because AES-SIV cleanup must wait for policy and replacement behavior.
- **Phase 6 US4**: Depends on US1, US2, and US3 for final validation.
- **Phase 7 Polish**: Depends on the implemented user stories.

### User Story Dependencies

- **US1 (P1)**: MVP. Can start after foundational tasks and has no dependency on other stories.
- **US2 (P2)**: Can start after foundational tasks, but must align with US1 policy decisions.
- **US3 (P3)**: Must wait for US1 and US2 because cleanup is unsafe before replacement behavior exists.
- **US4 (P4)**: Must wait for US1, US2, and US3 because it validates the complete change.

### Within Each User Story

- Tests and review checks come before implementation tasks.
- Shared APIs and SQL schema changes come before DAO/repository updates.
- Migration and backfill come before AES-SIV cleanup.
- Cleanup comes before final validation.

---

## Parallel Opportunities

- T003 and T004 can run in parallel after T001.
- T007, T008, and T009 can run in parallel after T005 and T006.
- US1 documentation tasks T011, T012, T016, T017, T018, and T019 can be split across files after T013-T015 are understood.
- US2 test tasks T020, T021, T022, T023, and T024 can run in parallel before implementation.
- US2 implementation can split core security tasks T025-T027 from SQLDelight tasks T028-T032 before DAO/repository integration T033-T038.
- US3 tests T039 and T040 can run in parallel, and cleanup tasks T041-T047 can be split by module after US2 is complete.
- US4 test tasks T048-T051 can run in parallel once US2 and US3 are implemented.

---

## Parallel Example: User Story 2

```text
Task: "Add deterministic lookup-token tests in core/security/src/test/kotlin/com/chimali/core/security/impl/HmacMetadataLookupTokenServiceTest.kt"
Task: "Add encrypted metadata tamper tests in core/security/src/test/kotlin/com/chimali/core/security/impl/AesGcmEncryptedMetadataServiceTest.kt"
Task: "Add FIDO2 migration tests in feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/database/SearchableMetadataMigrationTest.kt"
Task: "Add repository lookup and partial-search tests in feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/repository/CredentialRepositorySearchableMetadataTest.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 setup.
2. Complete Phase 2 foundational policy and API terminology.
3. Complete Phase 3 User Story 1.
4. Stop and validate the constitution and docs before code removal.

### Incremental Delivery

1. US1 resolves governance and documentation conflict.
2. US2 adds lookup-token and encrypted metadata behavior without removing AES-SIV yet.
3. US3 removes AES-SIV-specific surface only after replacement behavior is available.
4. US4 verifies migration, tamper rejection, performance, and local CI.

### Parallel Team Strategy

With multiple developers:

1. One developer owns constitution and documentation files for US1.
2. One developer owns core/security lookup-token and encrypted metadata services for US2.
3. One developer owns FIDO2 SQLDelight, DAO, and repository migration for US2.
4. One developer owns AES-SIV cleanup and non-SIV Bouncy Castle documentation for US3 after US2 lands.

---

## Notes

- Keep AES-SIV cleanup blocked until constitution and replacement storage behavior are complete.
- Do not claim full Bouncy Castle removal in this feature.
- Do not use AES-GCM with fixed, reused, predictable, or plaintext-derived nonces to emulate deterministic encryption.
- Preserve partial text search only through explicitly classified SQLCipher-only display fields.
- Avoid physical column drops unless staged through SQLDelight migrations and verified by migration tests.

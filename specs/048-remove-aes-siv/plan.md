# Implementation Plan: Remove AES-256-SIV

**Branch**: `048-remove-aes-siv` | **Date**: 2026-05-20 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/048-remove-aes-siv/spec.md`

## Summary

Remove AES-256-SIV as a mandatory Chimali primitive by first amending the constitution, then replacing exact-match searchable metadata requirements with deterministic keyed lookup tokens and authenticated encrypted metadata values. Existing partial text search is preserved through explicitly classified SQLCipher-only display fields, not through lookup tokens. AES-256-GCM/AEAD remains the approved value-encryption and key-wrapping pattern when each encryption uses a unique nonce and associated data. This feature does not remove Bouncy Castle completely; non-SIV uses remain out of scope unless a later provider-migration feature is created.

## Technical Context

**Language/Version**: Kotlin 2.x, Kotlin Multiplatform structure, Android minSdk 28

**Primary Dependencies**: Koin, SQLDelight, SQLCipher, Android Keystore, AndroidX DataStore, existing Bouncy Castle non-SIV crypto paths

**Storage**: SQLCipher-backed SQLDelight databases for FIDO2 and vault storage; Proto DataStore with Android Keystore AES-GCM wrapper for preferences and key-boundary values

**Testing**: kotlin.test, JUnit 5, MockK, Android instrumented tests, `tools/local-ci.ps1`

**Target Platform**: Android native application on Windows-hosted development; iOS placeholders remain non-functional

**Project Type**: Kotlin Multiplatform-ready Android mobile app with FIDO2 virtual authenticator and vault features

**Performance Goals**: Preserve existing <200 ms FIDO2 action target where lookup participates; exact-match lookup over 10,000 records remains within current management-flow budget

**Constraints**: Constitution must be updated before code removal; no deterministic AES-GCM nonce reuse; key wrapping uses platform-backed AES-GCM/AEAD with unique nonces and associated data; no Bouncy Castle full-removal claim; local CI must pass; no detekt or ktlint baseline weakening

**Scale/Scope**: FIDO2 searchable metadata, vault searchable-label policy review, platform-backed key-wrapping policy, AES-SIV production API cleanup, docs and constitution updates

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| Principle I Security First | Current constitution mandates AES-SIV; plan must amend policy before code removal | **Conditional Pass** - Track A must complete first |
| Principle I / X.5 Memory Security | New lookup-token, value-encryption, and key-wrapping paths must zero keys and sensitive buffers where mutable | Pass |
| Principle III Architecture | Security primitives belong in core security; feature storage consumes stable abstractions | Pass |
| Principle IV Performance | Exact-match lookup must remain indexed and support 10,000 records | Pass |
| Principle VII Documentation | Constitution, BRD/TRD, research, and stale changelog references must be aligned | Pass |
| Principle VIII Event Sourcing | Event payload compatibility must be preserved; event history is not rewritten or pruned | Pass |
| Principle IX Local CI | `tools/local-ci.ps1` must exit 0 before completion | Pending |
| Principle X.3 SQL | New columns and migrations must follow SQLDelight/SQLCipher conventions; drops are staged | Pass |
| Principle X.5 Cryptographic Code | Deterministic lookup tokens must use published primitives and domain separation; value encryption and key wrapping must use unique nonces and associated data | Pass |
| Principle XI Pragmatism | Bouncy Castle full removal and advanced substring searchable encryption remain out of scope | Pass |

**Gate Resolution**: The feature intentionally changes the constitution. Implementation must start with the constitution amendment and documentation alignment, then proceed to storage and cleanup. Any task that removes AES-SIV production code before the constitution is amended is blocked.

## Project Structure

### Documentation (this feature)

```text
specs/048-remove-aes-siv/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── metadata-lookup-contract.md
│   └── security-policy-contract.md
└── tasks.md
```

### Source Code (repository root)

```text
.specify/
└── memory/
    └── constitution.md

docs/
├── brd.md
├── trd.md
├── research/
│   ├── AES_SIV_vs_GCM_Evaluation.md
│   └── aes-siv-removal-analysis.md
└── changelogs/

core/
├── security/
│   └── src/
│       ├── commonMain/kotlin/com/chimali/core/security/api/
│       └── androidMain/kotlin/com/chimali/core/security/impl/
└── database/
    └── src/main/sqldelight/com/chimali/core/database/

feature/
└── fido2/
    └── src/
        ├── commonMain/sqldelight/com/chimali/fido2/data/database/
        ├── androidMain/kotlin/com/chimali/fido2/data/dao/
        ├── androidMain/kotlin/com/chimali/fido2/data/mapper/
        ├── androidMain/kotlin/com/chimali/fido2/data/repository/
        └── androidMain/kotlin/com/chimali/fido2/data/service/
```

**Structure Decision**: Keep the work in existing security, database, and FIDO2 modules. Do not add a new Gradle module. The policy change is cross-cutting, but the replacement behavior can be delivered through existing core security abstractions and feature-owned SQLDelight migrations.

## Phase 0: Research Summary

See [research.md](research.md). Key decisions:

- Amend the constitution to replace mandatory AES-SIV with deterministic keyed lookup tokens, authenticated encrypted values, and platform-backed AEAD key wrapping.
- Use HMAC blind indexes for exact-match search; never make AES-GCM deterministic.
- Preserve partial text search through explicitly classified SQLCipher-only display fields; lookup tokens support equality only.
- Remove AES-SIV-specific code after replacement storage behavior and policy are in place.
- Keep non-SIV Bouncy Castle usage out of scope.

## Phase 1: Design Summary

Design artifacts:

- [data-model.md](data-model.md)
- [contracts/security-policy-contract.md](contracts/security-policy-contract.md)
- [contracts/metadata-lookup-contract.md](contracts/metadata-lookup-contract.md)
- [quickstart.md](quickstart.md)

Primary design:

1. `MetadataLookupToken` stores fixed-size keyed deterministic bytes for equality lookup.
2. `EncryptedMetadataValue` stores authenticated encrypted canonical metadata.
3. `SearchableMetadataRecord` links a domain record to lookup tokens, encrypted values, and explicitly classified display fields.
4. `KeyWrappingEnvelope` captures the platform-backed AES-GCM/AEAD replacement for the former AES-SIV key-wrapping rule.
5. `MigrationState` tracks staged backfill and compatibility.
6. `DeprecatedAesSivSurface` captures API, implementation, DI, test, and documentation cleanup.

## Phase 2: Implementation Plan

### Track A - Constitution and Policy Alignment

| Step | Action | Notes |
|------|--------|-------|
| A1 | Amend `.specify/memory/constitution.md` Principle I | Remove AES-SIV MUST; add lookup-token policy; prohibit deterministic GCM nonce misuse |
| A2 | Amend key-wrapping guidance | Replace AES-SIV key-wrapping rule with platform-backed AES-GCM/AEAD using unique nonces and associated data |
| A3 | Update SQL/database guidance | Replace "deterministic ciphertext (AES-SIV)" note with keyed lookup token and SQLCipher display-field guidance |
| A4 | Update BRD/TRD/research references | Align `docs/brd.md`, `docs/trd.md`, and prior AES-SIV research notes |
| A5 | Add changelog | Document governance and security-policy change |

### Track B - Replacement Searchable Metadata Model

| Step | Action | Notes |
|------|--------|-------|
| B1 | Introduce lookup-token abstraction | Domain-separated keyed deterministic token for exact-match metadata |
| B2 | Introduce encrypted metadata value abstraction | AES-GCM value protection with unique nonce per encryption |
| B3 | Add tests for determinism, domain separation, tamper rejection, and key separation | Test-first per constitution |
| B4 | Document partial-search policy | Preserve current partial search only over explicitly classified SQLCipher-only display fields |

### Track C - FIDO2 Storage Migration

| Step | Action | Notes |
|------|--------|-------|
| C1 | Add SQLDelight migration columns for lookup tokens and encrypted metadata | Preserve compatibility; stage drops for later release if needed |
| C2 | Backfill indexes and encrypted metadata from existing records | Idempotent and interruption-safe |
| C3 | Update exact-match queries to use lookup tokens | RP ID, user ID, consent RP filters, and duplicate checks |
| C4 | Update mappers/repositories to hydrate domain models from encrypted metadata | No credential re-registration required |
| C5 | Classify and retain display fields needed for partial search | Avoid accidental plaintext-sensitive storage; lookup tokens are exact-match only |

### Track D - AES-SIV Surface Cleanup

| Step | Action | Notes |
|------|--------|-------|
| D1 | Remove or deprecate `SivEncryptionManager` API | Only after Tracks A-C are functional |
| D2 | Remove `AesSivEncryptionManager` implementation and DI references | Avoid leaving unused custom crypto |
| D3 | Remove or replace `EncryptedMetadataIndexService` | Replacement is lookup-token service |
| D4 | Update key-wrapping references that still mandate AES-SIV | Use platform-backed AES-GCM/AEAD guidance instead |
| D5 | Update stale build comments and docs claiming Bouncy Castle is retained only for AES-SIV | Bouncy Castle remains for non-SIV crypto |
| D6 | Verify remaining Bouncy Castle references are non-SIV and documented out of scope | No false "BC removed" success criterion |

### Track E - Verification

| Step | Action | Notes |
|------|--------|-------|
| E1 | Run focused unit and host tests for security/indexing paths | Determinism, domain separation, tamper rejection |
| E2 | Run key-wrapping policy and review checks | Unique nonces, associated data, platform-backed AEAD |
| E3 | Run migration tests with seeded legacy rows | 100% accessibility after migration |
| E4 | Run FIDO2 registration/authentication regression tests | No credential re-registration |
| E5 | Run `tools/local-ci.ps1` | Final quality gate |

## Post-Design Constitution Re-Check

| Principle | Gate | Status |
|-----------|------|--------|
| Principle I Security First | New policy protects sensitive metadata and key wrapping without AES-SIV; GCM nonce misuse prohibited | Pass after Track A |
| Principle III Architecture | Existing modules reused; no new module introduced | Pass |
| Principle IV Performance | Indexed lookup tokens preserve exact-match query shape; partial search stays on existing display fields | Pass |
| Principle VII Documentation | Documentation updates are explicit Track A/D deliverables | Pass |
| Principle IX Local CI | Required final verification step | Pending |
| Principle X.5 Cryptographic Code | Published primitives only; key separation, domain separation, unique nonces, and associated data required | Pass |
| Principle XI Pragmatism | Advanced substring searchable encryption and full BC removal out of scope | Pass |

## Complexity Tracking

No constitution violations require a permanent exception. The initial AES-SIV conflict is the target of this governance feature and is resolved by Track A before code removal.

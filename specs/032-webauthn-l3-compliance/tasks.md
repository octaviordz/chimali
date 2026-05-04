---
description: "Task list for WebAuthn Level 3 Compliance implementation"
---

# Tasks: WebAuthn Level 3 Compliance

**Input**: Design documents from `specs/032-webauthn-l3-compliance/`  
**Prerequisites**: [plan.md](plan.md) | [spec.md](spec.md) | [research.md](research.md) | [data-model.md](data-model.md) | [contracts/fido2-domain-contracts.md](contracts/fido2-domain-contracts.md) | [quickstart.md](quickstart.md)  
**Branch**: `032-webauthn-l3-compliance`

**Tests**: Included — mandated by Constitution §III & §VIII (TDD, 100% unit test coverage for core business logic).

**Organization**: Tasks grouped by user story (P1 first). Each story is independently testable. P1 stories (US1 + US3) are the highest-criticality audit findings.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: User story this task belongs to (US1–US6)

---

## Phase 1: Setup

**Purpose**: Verify tooling and confirm the feature branch is ready. No source changes.

- [x] T001 Verify `tools/local-ci.ps1` passes cleanly on `lab/or/chimali` before any edits (run from repo root)
- [x] T002 Create feature branch `032-webauthn-l3-compliance` from current `lab/or/chimali` and switch to it

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core shared changes that ALL user stories depend on. Must be complete before any story-specific work begins.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T003 [P] Write failing unit tests for `CredentialId.fromByteArray()` size guard (boundaries: 15→fail, 16→pass, 1023→pass, 1024→fail) in `core/domain/src/commonTest/kotlin/com/chimali/core/domain/valueobject/CredentialIdTest.kt`
- [x] T004 [P] Write failing unit tests for `PasskeyCredential.validate()` COSE allowlist — verify `coseAlgorithm = -19` is rejected and `-8` is accepted — in `feature/fido2/src/test/kotlin/com/chimali/fido2/domain/model/PasskeyCredentialTest.kt`
- [x] T005 Add `MIN_CREDENTIAL_ID_BYTES = 16` and `MAX_CREDENTIAL_ID_BYTES = 1023` constants to `core/domain/src/commonMain/kotlin/com/chimali/core/domain/valueobject/CredentialId.kt`
- [x] T006 Update `CredentialId.fromByteArray()` in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/valueobject/CredentialId.kt` to enforce the 16–1023 byte range with an `IllegalArgumentException` (make T003 tests pass)
- [x] T007 Rename `COSE_ED25519 = -19` to `COSE_EDSA = -8` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/model/PasskeyCredential.kt` and update the `validate()` allowlist to permit `-8` instead of `-19` (make T004 tests pass)
- [x] T008 [P] Update `MIN_CEREMONY_TIMEOUT_MS`, `MAX_CEREMONY_TIMEOUT_MS`, and `DEFAULT_TIMEOUT_MS` constants in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/model/MakeCredentialOptions.kt` (30_000 / 600_000 / 120_000); `getSafeTimeout()` returns `DEFAULT_TIMEOUT_MS` as placeholder (clamping wired in T030)
- [x] T009 [P] Apply identical timeout constant changes to `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/model/GetAssertionOptions.kt` (same placeholder pattern as T008)

**Checkpoint**: Foundation ready — `CredentialId` size guard, COSE constant rename, and timeout constants are in place. User story phases may now begin.

---

## Phase 3: User Story 1 — Credential Registration with Compliant ID (Priority: P1) 🎯 MVP

**Goal**: Ensure the system enforces 1023-byte max and ≥100-bit/16-byte entropy requirements on Credential IDs, and accepts encrypted-blob IDs from stateless authenticators.

**Independent Test**: Run `CredentialIdTest` — all boundary assertions pass (15→fail, 16→pass, 1023→pass, 1024→fail). Initiate a registration ceremony end-to-end and verify no `IllegalArgumentException` for a valid 32-byte generated ID.

### Tests for User Story 1

> **Write tests FIRST — ensure they FAIL before implementation**

- [x] T010 [P] [US1] Add unit tests verifying `CredentialId.fromByteArray(ByteArray(1024))` throws, and `fromByteArray(ByteArray(1023))` succeeds — in `core/domain/src/commonTest/kotlin/com/chimali/core/domain/valueobject/CredentialIdTest.kt`
- [x] T010a [P] [US1] Add unit test asserting that `CredentialId.generate()` uses `SecureRandom` as its entropy source and produces exactly `CREDENTIAL_ID_SIZE_BYTES` (32) bytes — confirming the 256-bit entropy contract that satisfies FR-FIDO2-002 — in `core/domain/src/commonTest/kotlin/com/chimali/core/domain/valueobject/CredentialIdTest.kt`
- [x] T010b [P] [US1] Add unit test asserting that `CredentialId.fromByteArray()` accepts an opaque 256-byte byte array (simulating an encrypted-blob ID from a stateless authenticator) without error, and that `toByteArray()` round-trips it faithfully — satisfying FR-FIDO2-003 — in `core/domain/src/commonTest/kotlin/com/chimali/core/domain/valueobject/CredentialIdTest.kt`
- [x] T011 [P] [US1] Add unit tests for byte-level UTF-8 string validation in `PasskeyCredential`: 64-byte ASCII displayName → passes, 65-byte ASCII displayName → fails, 21-char string of 3-byte emoji → fails (63 UTF-8 bytes) in `feature/fido2/src/test/kotlin/com/chimali/fido2/domain/model/PasskeyCredentialTest.kt`

### Implementation for User Story 1

- [x] T012 [US1] Update `PasskeyCredential.validate()` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/model/PasskeyCredential.kt` to replace `userName.length <= MAX_NAME_LENGTH` with `userName.toByteArray(Charsets.UTF_8).size <= MAX_NAME_LENGTH` (and same for `userDisplayName`) — makes T011 tests pass
- [x] T013 [US1] Update `Ctap2MakeCredentialHandler.kt` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2MakeCredentialHandler.kt` — rename local `COSE_ED25519 = -19` to `COSE_EDSA = -8` and update the algorithm negotiation `when` block to match
- [x] T014 [P] [US1] Update `Ctap2GetAssertionHandler.kt` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2GetAssertionHandler.kt` — rename `COSE_ED25519 = -19` to `COSE_EDSA = -8` and update `when` block
- [x] T015 [US1] Update database mapper / SQLDelight query that reconstructs `PasskeyCredential` from storage to translate any persisted `coseAlgorithm = -19` to `-8` (one-time read-compat migration) — locate mapper in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/`

**Checkpoint**: US1 complete — `CredentialId` size guard enforced, COSE `-8` wired end-to-end, UTF-8 byte validation active, backward-compat mapper in place. Run `tools/local-ci.ps1`.

---

## Phase 4: User Story 3 — Interoperable Cryptographic Algorithm Negotiation (Priority: P1)

**Goal**: Add full EdDSA (`-8`) support in algorithm negotiation; remove deprecated identifiers (`-9`, `-19`, `-51`, `-52`) from all preference lists.

**Independent Test**: Simulate a `pubKeyCredParams` negotiation requesting `[-8, -7]` — verify `-8` is selected. Separately assert that a request containing only `-19` results in `CTAP2_ERR_UNSUPPORTED_ALGORITHM`.

### Tests for User Story 3

- [x] T016 [P] [US3] Add unit tests for `Ctap2MakeCredentialHandler` algorithm negotiation — verify `-8` is accepted, `-19`/`-9`/`-51`/`-52` are rejected, `-7`/`-257`/`-49` are accepted — in `feature/fido2/src/androidTest/kotlin/com/chimali/fido2/ctap2/Ctap2MakeCredentialHandlerTest.kt`
- [x] T017 [P] [US3] Add unit tests for `PublicKeyCredentialParameters.createEdDsa()` factory — verify `algorithm == "EdDSA"` and `curve == "Ed25519"` — in `feature/fido2/src/test/kotlin/com/chimali/fido2/domain/model/PublicKeyCredentialParametersTest.kt`

### Implementation for User Story 3

- [x] T018 [US3] Rename `createEd25519()` to `createEdDsa()` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/model/PublicKeyCredentialParameters.kt` and update all call sites
- [x] T019 [US3] Update algorithm negotiation in `Ctap2MakeCredentialHandler.kt` (`feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2MakeCredentialHandler.kt`) to add `COSE_RS256 (-257)` to the `when` block and explicitly handle `-9`, `-19`, `-51`, `-52` as `null` (rejected) with a log warning
- [x] T020 [US3] Verify `Ctap2GetAssertionHandler.kt` (`feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2GetAssertionHandler.kt`) also rejects `-9`, `-19`, `-51`, `-52` in its algorithm-matching logic
- [x] T021 [US3] Update `Fido2AuthenticatorImpl` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/service/impl/Fido2AuthenticatorImpl.kt` to include `"EdDSA"` (COSE `-8`) in the `supportedAlgorithms` list returned by `getAuthenticatorInfo()`
- [x] T022 [P] [US3] Update `AuthenticatorInfo` in `Fido2Authenticator.kt` (`feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/service/Fido2Authenticator.kt`) if `supportedAlgorithms` is a static list — add `"EdDSA"` and ensure `"Ed25519-19"` or similar legacy entries are removed

**Checkpoint**: US3 complete — EdDSA negotiation works end-to-end, deprecated IDs rejected. Run `tools/local-ci.ps1`.

---

## Phase 5: User Story 2 — Clear Credential Display Names During Selection (Priority: P2)

**Goal**: Ensure `name` and `displayName` fields are never truncated below 64 bytes in any UI selection or display context.

**Independent Test**: Register two credentials whose `displayName` values differ only after the 32nd character. Initiate authentication — verify both names are rendered distinctly without truncation.

### Tests for User Story 2

- [x] T023 [P] [US2] Add Compose UI test verifying a `displayName` of exactly 64 UTF-8 bytes renders without truncation in the credential selection bottom sheet — in `feature/fido2/src/androidTest/kotlin/com/chimali/fido2/presentation/`
- [x] T024 [P] [US2] Add unit tests that a `PasskeyCredential` with a 64-byte UTF-8 `userDisplayName` passes validation, and a 65-byte one fails — in `feature/fido2/src/test/kotlin/com/chimali/fido2/domain/model/PasskeyCredentialTest.kt`

### Implementation for User Story 2

- [x] T025 [US2] Audit all Compose UI components in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/` for any hard-coded string truncation or `maxLines`/`overflow` settings on `name`/`displayName` Text composables and remove or raise any limit below 64 bytes
- [x] T026 [US2] Audit `Ctap2MakeCredentialHandler.decodeMakeCredentialRequest()` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2MakeCredentialHandler.kt` — ensure `userName` and `userDisplayName` are passed through at full length without substring/truncation before reaching `PasskeyCredential`
- [x] T027 [US2] Audit `Ctap2GetAssertionHandler` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2GetAssertionHandler.kt` — ensure `userName`/`userDisplayName` are not truncated during assertion lookup or display

**Checkpoint**: US2 complete — display names preserved at full 64-byte fidelity. Run `tools/local-ci.ps1`.

---

## Phase 6: User Story 4 — Adaptive Ceremony Timeouts for All Users (Priority: P2)

**Goal**: Replace static 60-second timeout with dynamic, accessibility-compliant clamping logic in both registration and authentication ceremonies.

**Independent Test**: Call `MakeCredentialOptions.getSafeTimeout()` with `timeout = null` → 120_000. With `timeout = 10_000` → 30_000. With `timeout = 700_000` → 600_000. With `timeout = 90_000` → 90_000. Same assertions for `GetAssertionOptions`.

### Tests for User Story 4

- [x] T028 [P] [US4] Add unit tests for `MakeCredentialOptions.getSafeTimeout()` covering null, below-min, in-range, and above-max inputs — in `feature/fido2/src/test/kotlin/com/chimali/fido2/domain/model/MakeCredentialOptionsTest.kt`
- [x] T029 [P] [US4] Add unit tests for `GetAssertionOptions.getSafeTimeout()` with the same boundary cases — in `feature/fido2/src/test/kotlin/com/chimali/fido2/domain/model/GetAssertionOptionsTest.kt`

### Implementation for User Story 4

- [x] T030 [US4] Finalize `getSafeTimeout()` implementation in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/model/MakeCredentialOptions.kt` — ensure `coerceIn(MIN_CEREMONY_TIMEOUT_MS, MAX_CEREMONY_TIMEOUT_MS)` is the production path (the constants were added in T007; this task wires the clamping into `getSafeTimeout()` and verifies removal of old `MAX_TIMEOUT_MS`)
- [x] T031 [US4] Finalize `getSafeTimeout()` implementation in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/model/GetAssertionOptions.kt` (same as T030, parity)
- [x] T032 [US4] Add integration test sending a CTAP2 `authenticatorMakeCredential` request with an explicit timeout hint (e.g., 90,000 ms) encoded in the request options map, and asserting that the resulting `MakeCredentialOptions.getSafeTimeout()` returns 90,000 (within-range hint passed through correctly) — in `feature/fido2/src/androidTest/kotlin/com/chimali/fido2/ctap2/Ctap2MakeCredentialHandlerTest.kt` (Also update `handleMakeCredential()` to propagate the RP timeout field from the CTAP2 request into `MakeCredentialOptions.timeout` if not already doing so.)
- [x] T033 [P] [US4] Verify that any ViewModel or UI layer that reads a ceremony timeout (e.g., registration countdown display in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/viewmodel/`) calls `getSafeTimeout()` rather than accessing `MakeCredentialOptions.timeout` directly

**Checkpoint**: US4 complete — adaptive ceremony timeouts active. Run `tools/local-ci.ps1`.

---

## Phase 7: User Story 6 — Attestation Object Integrity (Priority: P2)

**Goal**: Verify `authenticatorMakeCredential` generates complete, integrity-preserved attestation objects supporting Basic, Self, and AttCA types.

**Independent Test**: Complete a registration ceremony and verify the resulting attestation object passes verification for all three attestation types using a conformant RP verifier.

### Tests for User Story 6

- [x] T034 [P] [US6] Add unit tests for `AttestationObject` construction confirming `authData` contains non-empty AAGUID, Credential ID, and Public Key — in `feature/fido2/src/test/kotlin/com/chimali/fido2/domain/model/AttestationObjectTest.kt`
- [x] T035 [P] [US6] Add unit tests for `Ctap2MakeCredentialHandler.buildAttestationStatementMap()` verifying `"packed"` and `"none"` format handling — in `feature/fido2/src/androidTest/kotlin/com/chimali/fido2/ctap2/Ctap2MakeCredentialHandlerTest.kt`

### Implementation for User Story 6

- [x] T036 [US6] Audit `Ctap2MakeCredentialHandler.encodeAttestationResponse()` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2MakeCredentialHandler.kt` — ensure `buildAttestationStatementMap()` handles `"packed"`, `"none"`, and `"attCA"` formats; add `"attCA"` branch if missing
- [x] T037 [US6] Audit `AttestationObject.kt` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/model/AttestationObject.kt` — confirm `AttestationType` covers `BASIC`, `SELF`, and `ATT_CA`; add missing types if needed
- [x] T038 [US6] Audit `Fido2AuthenticatorImpl.makeCredential()` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/service/impl/Fido2AuthenticatorImpl.kt` — verify attestation type selection respects `MakeCredentialOptions.attestation` (`AttestationConveyancePreference`); add `AttCA` branch if missing

**Checkpoint**: US6 complete — all three attestation types generate valid, verifiable objects. Run `tools/local-ci.ps1`.

---

## Phase 8: User Story 5 — PRF Extension for Deterministic Key Derivation (Priority: P3)

**Goal**: Implement full PRF/`hmac-secret` CTAP2 extension support — parse 1–2 salts, derive HMAC-SHA-256 output (≤32 bytes per salt), return in authenticator data.

**Independent Test**: Send a `GetAssertion` request with `extensions["hmac-secret"]` containing one salt → verify 32-byte output. Two salts → two 32-byte outputs. Three salts → `CTAP2_ERR_INVALID_PARAMETER`.

### Tests for User Story 5

- [x] T039 [P] [US5] Add unit tests for `PrfExtensionInput` validation — empty list throws, single salt passes, two salts pass, three salts throw — in `feature/fido2/src/test/kotlin/com/chimali/fido2/domain/model/PrfExtensionInputTest.kt`
- [x] T040 [P] [US5] Add unit tests for `PrfKeyDerivation.derive()` — output is exactly 32 bytes, deterministic (same input → same output), empty salt throws — in `feature/fido2/src/test/kotlin/com/chimali/fido2/data/crypto/PrfKeyDerivationTest.kt`
- [x] T041 [P] [US5] Add integration tests for CTAP2 `hmac-secret` extension parsing in `Ctap2GetAssertionHandler` — verify output CBOR map contains keys 1 and 2 when two salts provided — in `feature/fido2/src/androidTest/kotlin/com/chimali/fido2/ctap2/Ctap2GetAssertionHandlerTest.kt`

### Implementation for User Story 5

- [x] T042 [US5] Create `PrfExtensionInput` and `PrfExtensionOutput` data classes in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/model/PrfExtensionInput.kt` with validation (salts: 1–2, non-empty; `MAX_SALTS = 2`, `MAX_OUTPUT_BYTES = 32`)
- [x] T043 [US5] Create `PrfKeyDerivation` service in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/PrfKeyDerivation.kt` — implement `derive(salt: ByteArray, credentialHmacSecret: ByteArray): ByteArray` using Bouncy Castle `HMac(SHA256Digest())`; implement `deriveAll(input: PrfExtensionInput, secret: ByteArray): PrfExtensionOutput`
- [x] T044 [US5] Register `PrfKeyDerivation` as a Koin `single` in the FIDO2 DI module in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/di/`
- [x] T045 [US5] Update `Ctap2MakeCredentialHandler.decodeMakeCredentialRequest()` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2MakeCredentialHandler.kt` — parse `extensions["hmac-secret"]` map keys 1 (salt1) and 2 (salt2 optional); construct `PrfExtensionInput`; propagate to `MakeCredentialRequest`
- [x] T046 [US5] Update `Ctap2MakeCredentialHandler.handleMakeCredential()` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2MakeCredentialHandler.kt` — if `prfInput` is present, invoke `PrfKeyDerivation.deriveAll()` and include `PrfExtensionOutput` in the `MakeCredentialOptions.extensions` map
- [x] T047 [US5] Update `Ctap2GetAssertionHandler` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2GetAssertionHandler.kt` — parse `hmac-secret` extension from request (same as T045 pattern); derive output; serialize into authenticator data extensions CBOR map with integer keys 1 and 2
- [x] T048 [US5] Handle >2 salts error path in both CTAP2 handlers — return `CTAP2_ERR_INVALID_PARAMETER (0x02)` when `PrfExtensionInput` constructor throws
- [x] T049 [US5] Handle missing `hmac-secret` authenticator capability gracefully — if `credentialHmacSecret` cannot be retrieved for a credential, omit PRF from response without failing the ceremony

**Checkpoint**: US5 complete — PRF extension functional. Run `tools/local-ci.ps1`.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: CI validation, documentation, and final static analysis sweep.

- [x] T050 [P] Update `detekt-baseline-main.xml` in `feature/fido2/` if any new suppressions are required for the new files; remove any stale baselines that covered the old `-19` constant
- [x] T051 [P] Run `ktlintFormat` on all modified files in `feature/fido2/` and `core/domain/` via `./gradlew ktlintFormat`
- [x] T052 [P] Run `detekt` on `feature/fido2/` and `core/domain/` via `./gradlew detekt`; resolve all new violations
- [x] T053 Execute full `tools/local-ci.ps1` pipeline and confirm zero violations, zero test failures
- [x] T054 Update `CHANGELOG.md` (or equivalent) with a WebAuthn L3 compliance entry documenting the five remediated findings
- [x] T055 [P] Update `docs/webauthn-l3-compliance-spec.md` to reflect final implementation status (mark all requirements as RESOLVED)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — BLOCKS all user story phases
- **Phase 3 (US1 P1)**: Depends on Phase 2 — can start as soon as foundation is complete
- **Phase 4 (US3 P1)**: Depends on Phase 2 — can run in parallel with Phase 3
- **Phase 5 (US2 P2)**: Depends on Phase 2; benefits from Phase 3 complete (shares `PasskeyCredential`)
- **Phase 6 (US4 P2)**: Depends on Phase 2 (T007/T008 constants); independent of Phases 3–5
- **Phase 7 (US6 P2)**: Depends on Phase 3 (COSE constant rename must be done first)
- **Phase 8 (US5 P3)**: Depends on Phase 2; logically depends on Phase 3 for COSE constant stability
- **Phase 9 (Polish)**: Depends on all desired story phases complete

### User Story Dependencies

| Story | Phase | Priority | Depends On | Independent? |
|---|---|---|---|---|
| US1 — Credential ID | 3 | P1 | Foundation (T003–T008) | YES |
| US3 — COSE Algorithms | 4 | P1 | Foundation (T006) | YES (parallel with US1) |
| US2 — Display Names | 5 | P2 | Foundation (T011 for UTF-8 fix) | YES |
| US4 — Timeouts | 6 | P2 | Foundation (T007–T008) | YES |
| US6 — Attestation | 7 | P2 | US1 complete (T006 COSE rename) | Mostly |
| US5 — PRF Extension | 8 | P3 | Foundation; US1 stable | YES |

### Parallel Opportunities

- **T007 + T008**: Timeout constants for MakeCredential and GetAssertion — different files, full parallel
- **T009 + T010**: Test tasks for US1 — different files, full parallel
- **T012 + T013**: CTAP2 handler COSE constant updates — different files, full parallel
- **T016 + T017**: US3 test tasks — different files, full parallel
- **T028 + T029**: US4 timeout test tasks — different files, full parallel
- **T034 + T035**: US6 test tasks — different files, full parallel
- **T039 + T040 + T041**: US5 test tasks — all different files, fully parallel
- **Phase 3 + Phase 4**: US1 and US3 are both P1 and can be worked in parallel by two developers
- **Phase 5 + Phase 6**: US2 and US4 touch different files and can run in parallel

---

## Parallel Example: Phase 2 Foundational

```text
# These can all be dispatched at once:
Task T003+T004: CredentialId byte guard (core/domain)
Task T006:      PasskeyCredential COSE rename (fido2/domain)
Task T007:      MakeCredentialOptions timeout (fido2/domain)
Task T008:      GetAssertionOptions timeout (fido2/domain)

# T005 (CredentialId tests) can run in parallel with T006–T008
```

## Parallel Example: Phase 8 (US5 PRF)

```text
# Tests first (parallel):
Task T039: PrfExtensionInput unit tests
Task T040: PrfKeyDerivation unit tests
Task T041: CTAP2 integration tests

# Then implementations (sequential within story):
Task T042: PrfExtensionInput + PrfExtensionOutput data classes
Task T043: PrfKeyDerivation crypto service
Task T044: Koin DI registration
Task T045+T046: MakeCredential handler integration
Task T047+T048+T049: GetAssertion handler integration
```

---

## Implementation Strategy

### MVP: P1 Stories Only (Phases 1–4)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (blocks everything)
3. Complete Phase 3: US1 — Credential ID Compliance
4. Complete Phase 4: US3 — COSE Algorithm Compliance
5. **STOP and VALIDATE**: Run `tools/local-ci.ps1`; confirm zero High-criticality audit findings remain
6. Both P1 findings (Credential ID, COSE Algorithms) are resolved → audit re-test can proceed

### Incremental Delivery

1. Foundation → US1 + US3 (P1, highest risk) → **audit gate cleared**
2. US2 + US4 (P2 accessibility & UX) → **medium-criticality cleared**
3. US6 (P2 attestation integrity) → **attestation audit cleared**
4. US5 (P3 PRF extension) → **all findings resolved**

### Parallel Team Strategy

With two developers available:

1. Both complete Phase 1 + Phase 2 together
2. Developer A: Phase 3 (US1 — Credential ID)
3. Developer B: Phase 4 (US3 — COSE Algorithms) — parallel with Developer A
4. Sync → Phase 5+6 in parallel
5. Phase 7+8 sequentially

---

## Notes

- All new constants must be named (no magic numbers — Constitution §III)
- Constitution §VIII requires `tools/local-ci.ps1` to pass after every logical group of tasks
- Test tasks marked `[P]` can run in parallel with each other (different test files)
- The data migration for `-19` → `-8` stored credentials (T014) must be verified before any release
- PRF `credentialHmacSecret` derivation strategy (T043) must not use the signing key directly — derive a separate HMAC sub-key from credential seed material
- Zero memory leaks: ensure `PrfKeyDerivation.derive()` zeros the output byte array after use if the caller does not need it retained (Constitution §I)

# Behavior Test Selection and Remediation Ledger

**Feature**: 058-enforce-coverage-targets
**Status**: Active
**Last Updated**: 2026-10-27

## 0. Coverage Evidence Used for Selection

The clean initial report used to rank the Tier 1 candidates is run `42bb76a8-47c1-49ba-9c8b-9afce9c281df`, captured from commit `aa1408ddcadc599d83ee1f971b6af48c7c1816f8` / tree `a120937362e89eee4d4c58fe2577b468240d1e6a` with clean preflight and postflight. It records 187 required suites, all passed with zero failed, errored, or skipped tests, on Android ATD API 35. The archived bundle is under `D:\temp\chimali-coverage-runs\42bb76a8-47c1-49ba-9c8b-9afce9c281df`; reports and counters are hashed in its manifest. The verifier assessment at `D:\temp\chimali-coverage-runs\assessments\42bb76a8-47c1-49ba-9c8b-9afce9c281df\decision.md` reports complete collection, passed tests, and the expected FIDO2 branch-floor failure.

| Tier 1 module | Initial instructions | Initial branches | Exact floor gap at initial run |
|---|---:|---:|---|
| `:core:security` | 3,385 / 4,452 (76.03%) | 239 / 386 (61.92%) | 0 instructions; 0 branches (713 and 7 covered above the respective floors) |
| `:core:domain` | 4,923 / 6,911 (71.23%) | 399 / 558 (71.51%) | 0 instructions; 0 branches (776 and 64 covered above the respective floors) |
| `:core:data` | 1,158 / 1,333 (86.87%) | 44 / 60 (73.33%) | 0 instructions; 0 branches (358 and 8 covered above the respective floors) |
| `:feature:fido2` | 53,008 / 86,100 (61.57%) | 3,206 / 5,526 (58.02%) | 0 instructions; **110 branches** (3,316 needed at 60%) |
| `:feature:authenticator` | 0 / 0 (proved-zero disposition) | 0 / 0 (proved-zero disposition) | Not applicable; inventory disposition only, not a behavioral test candidate |

Repository instructions were 72,418 / 132,630 (54.60%), 6,103 covered instructions above the 50% floor. The archived run's `floors` field was left `Unverified` in its manifest despite the separate decision record calculating floors. The initial bundle also lacks nonempty retained-class inventory digests and bound zero-proof report digests, so it is selection evidence, **not a trusted regression baseline** and does not close T088. The latest completed full diagnostic after COV-014 (dirty tree, before COV-015) measured FIDO2 at 3,234 / 5,526 branches (58.52%), 82 branches short. COV-015's impact remains unmeasured because the subsequent full workload had no connected Android device.

### Superseding clean checkpoint (2026-10-26)

Clean run `83ccf4f6-e85c-4db4-8476-1ee0b349c284` supersedes the older diagnostics as the latest complete measurement of the implemented test set. It measured commit `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2` / tree `5c0ed87b07424a3e572bc7328d1136caa2786280` with 202 suites and 1,271 tests, no failures/errors/skips, and a verified five-module Tier 1 inventory. All hard instruction and branch floors, plus the repository 50% instruction floor, passed. COV-015–017's isolated deltas were not separated, but their cumulative end-state is included below. This is an `Initial` capture and does not prove candidate-to-target nonregression.

| Tier 1 module | Latest clean instructions | Latest clean branches | Exact hard-floor gap | 65% branch goal |
|---|---:|---:|---:|---|
| `:core:security` | 3,385 / 4,452 (76.033%) | 239 / 386 (61.917%) | 0 instructions; 0 branches | 12 additional covered branches (minimum 251/386) |
| `:core:domain` | 5,124 / 7,184 (71.325%) | 412 / 578 (71.280%) | 0 instructions; 0 branches | Met |
| `:core:data` | 1,158 / 1,333 (86.872%) | 44 / 60 (73.333%) | 0 instructions; 0 branches | Met |
| `:feature:fido2` | 54,452 / 86,114 (63.232%) | 3,333 / 5,526 (60.315%) | 0 instructions; 0 branches | 259 additional covered branches (minimum 3,592/5,526) |
| `:feature:authenticator` | 0 / 0 (`AnalyzedZeroProofV1`) | 0 / 0 (`AnalyzedZeroProofV1`) | Not applicable; inventory only | Not applicable |

Repository instructions are 74,125 / 132,917 (55.768%), 7,666 covered instructions above the 50% floor. Compared with the older selection run, repository counts are +1,707 covered / +287 total instructions; FIDO2 is +1,444 covered / +14 total instructions and +127 covered branches; domain is +201 covered / +273 total instructions and +13 covered branches. These are cumulative cross-revision differences, not isolated per-case causal deltas. The security and FIDO2 branch figures remain short of the nonbinding 65% goal by 3.083 and 4.685 percentage points respectively. Clean current-target run `10b510ad-4638-4107-9fe6-91efdbbf9827` is now compatible with and regression-passing against the same-revision Initial capture; its raw counters match. This provides a trusted local target reference, but changed-candidate Tier 1 nonregression remains unverified until a clean candidate capture is assessed.

### Post-checkpoint security boundary batch (2026-10-26; diagnostic only)

After the sealed Initial run, the missed-branch report and source audit showed that the public-only P-256 child-derivation overload had no direct behavioral tests, and HKDF's exceptional input boundaries were not all asserted. These are security-relevant API/derivation cases; they were selected to verify scalar/public-point composition, borrowed-input ownership, RFC 5869 output limits, and rejection of malformed KDF inputs. Added cases:

| ID | Module | Behavior | Impact | Likelihood | Focused result | Measured contribution |
|---|---|---|---:|---:|---|---|
| `COV-018` | `:core:security` | Public-only P-256 child derivation composes with the parent scalar and rejects incomplete/oversized local secrets | 4 | 2 | `HdkP256Test`: 6 tests, 0 failures/errors | The full dirty-tree diagnostic advanced security from 249 to 258 covered branches cumulatively with COV-019; isolated per-case attribution is not available. |
| `COV-019` | `:core:security` | HKDF validates PRK/length boundaries, zero and maximum output lengths, credential-label ASCII constraints, and complete BIP39 seed length while preserving borrowed buffers | 4 | 2 | `HkdfSha256Test`: 9 tests, 0 failures/errors | The intermediate diagnostic after this case and before COV-018 measured 249/386 security branches. |

The final dirty-tree diagnostic after both cases measured security at 258/386 branches (66.839%), exceeding the approximate 65% goal. It reported all configured hard floors passed. This diagnostic uses current build outputs and is not a sealed candidate capture; it cannot establish target nonregression. FIDO2 remains at 3,333/5,526 branches (60.315%), 259 covered branches below the exact 65% threshold. See `implementation-evidence.md` §11.34 for run environment and full Tier 1/repository counts.

### FIDO2 sensitive CTAP compatibility case (2026-10-26; diagnostic only)

The current CTAP handler accepts client-data hashes represented as byte arrays or legacy base64 text held in `SensitiveCredentialText`. The handler must decode the exact 32-byte digest for lookup, retain it for the operation, and erase the source text owners after request processing. `COV-020` adds a focused assertion for that behavior. The focused `Ctap2GetAssertionHandlerTest` suite passed 14/14; the full host/device diagnostic also passed. FIDO2 moved from 3,333/5,526 to 3,335/5,526 covered branches (+2) and from 54,452/86,114 to 54,465/86,114 covered instructions (+13); repository instructions moved from 74,166/132,917 to 74,179/132,917 (+13). The 65% FIDO2 branch goal remains 257 additional covered branches away (3,592 required). All hard floors pass, but the counters remain unsealed dirty-tree evidence with no target regression decision. See `implementation-evidence.md` §11.35.

| ID | Module | Behavior | Impact | Likelihood | Focused result | Measured contribution |
|---|---|---|---:|---:|---|---|
| `COV-020` | `:feature:fido2` | Legacy sensitive-text clientDataHash is decoded to the exact digest for candidate lookup, then RP/hash request owners are erased | 2 | 3 | `Ctap2GetAssertionHandlerTest`: 14 tests, 0 failures/errors; ktlint passed | Full dirty-tree diagnostic: FIDO2 +2 branches and +13 instructions versus §11.34; cumulative case-level attribution was not isolated. |

### FIDO2 recovery and mnemonic decoding boundaries (2026-10-26; diagnostic only)

Missed-branch review identified sensitive state recovery and encrypted mnemonic decoding as security-relevant boundaries in already-tested production services. `COV-021` exercises each individual field that can make a supposedly initial passkey snapshot partially initialized; every such snapshot must be rejected, its owned sensitive metadata erased, and authoritative event history replayed. `COV-022` exercises invalid Base64, empty decrypted plaintext, and malformed UTF-8 during mnemonic recovery, including erasure of plaintext bytes on decode failure.

| ID | Module | Behavior | Impact | Likelihood | Focused result | Measured contribution |
|---|---|---|---:|---:|---|---|
| `COV-021` | `:feature:fido2` | Reject partially initialized passkey snapshots across every persisted state field, erase sensitive snapshot owners, and replay event history | 4 | 3 | `PasskeyAggregateSnapshotValidationTest`: 1 test covers 13 corrupted fields; 0 failures/errors, ktlint and Detekt passed | Full diagnostic: FIDO2 +20 branches and +8 instructions versus the COV-020 measurement. |
| `COV-022` | `:feature:fido2` | Reject malformed Base64, empty decrypted mnemonic plaintext, and malformed UTF-8, erasing returned plaintext owners | 4 | 2 | `WalletMasterSeedProviderTest`: 16 tests, 0 failures/errors; ktlint passed | Full diagnostic: FIDO2 +5 branches and +23 instructions versus COV-021. |
| `COV-023` | `:feature:fido2` | Omit typed `hmac-secret` response extension when credential seed derivation returns no output | 4 | 3 | `Ctap2GetAssertionHandlerTest`: 15 tests, 0 failures/errors; ktlint passed | Full diagnostic: FIDO2 +2 branches and +12 instructions versus COV-022. |

The full host/device diagnostic after COV-023 passed `hdkCoverageVerify`. FIDO2 measured 3,362/5,526 branches (60.839%), 230 covered branches below the 65% goal; all hard floors remain satisfied. These successive dirty-tree measurements are diagnostic only and are not a sealed candidate capture or changed-module regression decision. See `implementation-evidence.md` §11.37–11.39.

### Missed-branch report and assertion audit across Tier 1

- **`:core:security`** — The initial report's largest branch misses were `Bip39MasterSeedGenerator` (32), `HdkP256` (17), then generated/value-type branches in `HdkKeyPair` and `HdkChild` (16 each). The BIP39 suite already asserts valid seed derivation, invalid length/word/checksum/spacing rejection, caller ownership and temporary-buffer erasure; P-256 suites assert published vectors, malformed scalar/point rejection, sampler exhaustion and candidate/PRK erasure. Mapped to `NFR-SEC-041` / `TR-AUTH-031`; no higher-risk missing assertion displaced the selected 4/3 key lifecycle case.
- **`:core:domain`** — Largest misses were `RelyingParty` (25), `UserConsentRecord` (20), `CredentialSummary` (19), and `VaultState` (18). The RP parser/ownership suites covered common protocol boundaries but missed empty/whitespace HTTPS authorities; COV-016 adds the regression. Consent/deep-copy and vault memory paths map to `NFR-SEC-041` / `TR-AUTH-031` / `NFR-ARCH-050` and are represented by COV-003, COV-011, COV-012 and COV-013.
- **`:core:data`** — `EventStoreRepositoryImpl` had 8 missed branches and `VaultAggregateServiceImpl` 5. Existing service tests exercise optimistic retry/exhaustion, snapshot hydration/cadence, no-op/error/cancellation paths, and owner erasure (`NFR-ARCH-050`, `NFR-SEC-041`); COV-004 was extended with explicit no-snapshot-on-failure/cancellation assertions.
- **`:feature:fido2`** — The highest missed branch classes were `BluetoothHidCallbackCoordinator` (73), `BluetoothHidDeviceWrapper` (72), `PasskeyAggregateServiceImpl` (53), and `Ctap2GetAssertionHandler` (50). These map to `NFR-SEC-041`, `TR-AUTH-031`, `FR-HID-020`, and CTAP request validation. The selected COV-007/008/009/010/014/015 cases extend their weakly asserted security/protocol edges; passkey snapshot corruption/fallback, invalid replay, cancellation and ownership are also already asserted in `PasskeyAggregateServiceImplTest`.
- **`:feature:authenticator`** — Inventory and source review found no executable Kotlin production scope. Keep its zero-count disposition as accounting only; do not add artificial code or treat it as a tested behavior.

## 1. Selection Protocol and Ordering Rules

### Risk-First Hierarchy
In accordance with Principle V (5.0.0), BRD NFR-ARCH-050, and TRD §2.2/§9, test selection and remediation proceed strictly by risk priority across all Tier 1 modules before coverage-deficit ordering:
1. **Impact** (Descending 4 → 1):
   - **4**: Credential compromise, unauthorized access, or irrecoverable sensitive-data loss.
   - **3**: Integrity or recoverability failure, state corruption, or event synchronization failure.
   - **2**: Bounded functional failure, graceful error handling, or boundary validation failure.
   - **1**: Low-consequence behavior, scaffold accounting, or informational telemetry.
2. **Likelihood / Exposure** (Descending 3 → 1):
   - **3**: Ordinary or externally supplied reachable conditions, public APIs, or known regressions.
   - **2**: Credible failure, boundary conditions, or complex lifecycle transitions.
   - **1**: Constrained internal conditions or unreachable fallback branches.
3. **Module Deficit Tie-Breaker**:
   - For identical risk rank `(impact, likelihood)`, order by larger positive percentage-point gap against applicable module floors (Tier 1: 60% instructions/branches).
4. **Stable Candidate ID**:
   - For remaining ties, order by ascending stable identifier (`COV-001`, `COV-002`, etc.).

### Mandatory Inline Batch Loop (T055)
Every rank batch (Impact 4, Impact 3, Impact 2/1) must execute the following inline cycle before advancing to a lower-risk rank:
1. **Select**: Identify highest-ranked candidate(s) in the active rank.
2. **Author / Extend**: Implement independent assertions in the designated test file. Tests must verify independent expected outcomes and typed failures; manufacturing artificial defects is forbidden.
3. **Focused Execution**: Run the focused test task for the affected module.
4. **Immediate Remediation**: If an exposed product defect is revealed, repair it immediately with bounded corrections.
5. **Rerun & Verify**: Rerun the test suite to confirm passing outcome before marking status `Verified`.
6. **Fresh Measurement Checkpoint**: Capture fresh coverage data under local-CI policy.
7. **Reranking**: Rerank remaining candidates; newly discovered higher-risk cases reenter priority order. A failed or unverified test strictly blocks progression to lower-risk ranks.

---

## 2. Finite Candidate Ledger

The older rows retain their original measurement receipts. COV-017 selection used the complete clean local run `6788e4cd-753a-45bf-875b-46e9151541c9`: FIDO2 branches 3,238/5,526 (78 short). That run includes COV-015/COV-016; their isolated coverage deltas were not measured. COV-017's clean post-change checkpoint is now recorded by run `5fad15f8-0604-46ea-b822-6b0997de4e26` at commit `997cc3b3ccab94f1f57f5b9c770e98c5b969118f`, with all local hard floors passed. No lower-risk batch was started; accepted-target comparison and hosted integration remain unverified.

| ID | Module | Behavior | Reason | Impact | Likelihood | Live Deficit | Status | Test Location | Action |
|---|---|---|---|---|---|---|---|---|---|
| `COV-015` | `:feature:fido2` | Pairing-in-progress HID session admission and pending-host cleanup | `Tier1Risk` | 4 | 3 | 82 branch gap at latest complete pre-COV-015 diagnostic; COV-015 impact unmeasured | Focused and full FIDO2 host suites passed; device measurement pending | `BluetoothHidDeviceWrapperIncomingReportTest.kt` | `Add` |
| `COV-017` | `:feature:fido2` | Bond completion, delayed reconnects, callback lifetime, and session report ownership | `Tier1Risk` | 4 | 3 | 0 hard-floor gap in clean run `5fad15f8`: 3,333/5,526 branches (60.315%) | Verified component behavior and local clean checkpoint; integration Unverified | `BluetoothHidCallbackCoordinatorTest.kt`; `BluetoothHidDeviceWrapperIncomingReportTest.kt` | `Add / Extend` |
| `COV-001` | `:core:security` | Unprovisioned / cleared key failure rejection | `Tier1Risk` | 4 | 3 | 0 gap; initial surplus +713 instructions / +7 branches | `Verified` | `AesGcmEncryptedMetadataServiceTest.kt` | `Extend` |
| `COV-002` | `:core:domain` | `SensitiveText` terminal erasure, view bounds, serialization | `Tier1Risk` | 4 | 3 | 0 gap; initial surplus +776 instructions / +64 branches | `Verified` | `SensitiveTextTest.kt` | `Add` |
| `COV-016` | `:core:domain` | Malformed HTTPS origins with empty or whitespace-containing authorities are rejected | `Tier1Risk` | 4 | 3 | 0 gap at initial baseline; post-change coverage impact pending | Focused RP parser and ownership suites passed | `RelyingPartyTest.kt` | `Extend` |
| `COV-007` | `:feature:fido2` | Rejected passkey records erase owned RP/account text | `Tier1Risk` | 4 | 2 | 82 branch gap at latest completed diagnostic | Focused and full host suites passed; no sealed impact claim | `PasskeyCredentialTest.kt` | `Extend` |
| `COV-008` | `:feature:fido2` | Mnemonic encryption failure erases phrase and validation seed without persistence | `Tier1Risk` | 4 | 2 | 82 branch gap at latest completed diagnostic | Focused and full host suites passed; no sealed impact claim | `WalletMasterSeedProviderTest.kt` | `Extend` |
| `COV-014` | `:feature:fido2` | HID trust admission and active-session disconnect cleanup | `Tier1Risk` | 4 | 2 | 82 branch gap at latest complete diagnostic | Focused host suite and full configured diagnostic passed | `BluetoothHidDeviceWrapperIncomingReportTest.kt` | `Add` |
| `COV-003` | `:core:domain` | `UserConsentRecord` RP erasure, deep-copy isolation, validation wiping | `Tier1Risk` | 4 | 2 | 0 gap; initial surplus +776 instructions / +64 branches | `Verified` | `UserConsentRecordBehaviorTest.kt` | `Extend` |
| `COV-011` | `:core:domain` | Vault retry ownership, cleanup, and cancellation | `Tier1Risk` | 4 | 2 | 0 gap at latest completed diagnostic | Focused suite and full configured diagnostic passed | `VaultAggregateServiceImplTest.kt` | `Extend` |
| `COV-012` | `:core:domain` | Rejected copied consent records erase rejected RP text | `Tier1Risk` | 4 | 2 | 0 gap at latest completed diagnostic | Focused suite and full configured diagnostic passed | `UserConsentRecordBehaviorTest.kt` | `Extend` |
| `COV-013` | `:core:domain` | Serializer temporary character buffers are erased on success and failure | `Tier1Risk` | 4 | 2 | 0 gap at latest completed diagnostic | Focused suite and full configured diagnostic passed | `SensitiveTextTest.kt` | `Extend` |
| `COV-004` | `:core:data` | `VaultAggregateServiceImpl` snapshot hydration, retry exhaustion, memory scrubbing | `Tier1Risk` | 3 | 2 | 0 gap; initial surplus +358 instructions / +8 branches | `Verified` | `VaultAggregateServiceImplTest.kt` | `Extend` |
| `COV-009` | `:feature:fido2` | CTAP allow-list/UV decoding and malformed request rejection | `Tier1Risk` | 2 | 3 | 82 branch gap at latest completed diagnostic | Focused and full host suites passed; no sealed impact claim | `Ctap2GetAssertionHandlerTest.kt` | `Extend` |
| `COV-010` | `:feature:fido2` | Malformed allow-list containers/descriptors reject before credential lookup | `Tier1Risk` | 2 | 3 | 82 branch gap at latest completed diagnostic | Focused and full configured diagnostic passed | `Ctap2GetAssertionHandlerTest.kt` | `Add` |
| `COV-005` | `:core:security` | `AesGcmEncryptedMetadataService` malformed ciphertext length rejection | `Tier1Risk` | 2 | 3 | 0 gap; initial surplus +713 instructions / +7 branches | `Verified` | `AesGcmEncryptedMetadataServiceTest.kt` | `Extend` |
| `COV-006` | `:feature:authenticator` | Empty iOS-only scaffold inventory disposition; no behavioral code to test | `InventoryOnly` | 1 | 1 | Not applicable (0 / 0; no executable source) | `Not a behavioral candidate` | N/A (`AnalyzedZeroProofV1`) | `None` |

*Deficit definition*: Exact covered-count gap is `max(0, ceil(floor × total) − covered)`, derived from the archived raw integer counters. The initial report showed a hard-floor gap only for FIDO2 branches. Post-remediation diagnostic counts are kept separate from that initial baseline; no dirty diagnostic is accepted for regression or compliance.

---

## 3. Detailed Case Specifications

### COV-017: HID bond, reconnect, and callback session ownership

- **Module / requirements**: `:feature:fido2`; `FR-HID-010`, `FR-HID-020`; connection-loss safety in TRD §9.4.
- **Risk / selection**: Impact 4, likelihood 3 (`Tier1Risk`). Bluetooth callbacks and reconnect broadcasts are ordinary external events; stale host admission or queued reports crossing a session boundary can expose credential operations to the wrong session. This extends the prior Tier 1 risk audit and COV-014/COV-015 at their highest outstanding risk rank.
- **Current measurement**: clean local run `6788e4cd-753a-45bf-875b-46e9151541c9`, commit `2be39f64663c05e7d11987926a25843fb754346b`, reports FIDO2 branches 3,238/5,526 (78 short of 60%). `BluetoothHidCallbackCoordinator.kt` has 110 missed branches, including bond completion, ACL reconnect gates, delayed connect rechecks, profile lifecycle, and virtual unplug. This is selection evidence; comparison compatibility is Missing.
- **Initial / expected outcomes**: delayed connects execute only while advertising and still bonded; replacement schedules and direct connections cancel obsolete work. Bond completion admits only the matching pending host. ACL events for audio/wearable, unbonded, or nonadvertising states do not connect. Advertising discovery excludes peripherals and unconfirmed ACL links; a stale registration plugged-host hint does not admit a host. Wrong-profile/canceled initialization callbacks do not complete another attempt. The registered callback forwards through the real wrapper's admission and report ownership boundary. Adapter-off, profile loss, registration changes, explicit unregister/close, transport error, and active-host virtual unplug fence the previous generation and erase abandoned queued reports.
- **Forbidden effects**: no premature or duplicate connect, admission of an unrelated pending host, cross-generation report delivery, mutation of borrowed callback buffers, or double continuation completion.
- **Placement / existing action**: add `BluetoothHidCallbackCoordinatorTest.kt` in the configured Android host source set for callback/platform interactions and virtual-time reconnect behavior; extend `BluetoothHidDeviceWrapperIncomingReportTest.kt` for real wrapper queue erasure and admission. Android host placement is required by `android.bluetooth`/`android.content` APIs.
- **Focused receipts**: the first 46-case run failed exactly `late bond completion after callback session loss cannot admit a host` for `profile-lost` and `registration-lost`: the expected `Idle` state was `Connected`. The coordinator retained `pendingBondDevice` across both losses. Clearing that field inside the existing synchronized loss transitions corrected the exposed defect; all 46 focused cases then passed (26 coordinator, 20 wrapper). This includes 37 new cases. The initial failed JUnit report is retained under `D:/octav/coverage-local-validation/cov017-before`; passing focused reports are under `cov017-focused-passed`.
- **Diagnostic / extension**: that first iteration's full host/device diagnostic measured 3,303/5,526 branches (59.772%), 65 additional covered branches and 13 short of the floor. The same risk batch was extended to 52 new cases (34 coordinator and 18 additional wrapper cases). Its first 61-case run exposed the same stale pending-host defect after explicit `unregister`, `close`, and `error` transitions. Clearing the pending host in `unregisterApp()` (also used by `close()`) and `reportError()` made all 61 focused cases pass. Original failures: `cov017-extra-before`; final passing focused reports: `cov017-final-focused-passed`, under the same evidence root.
- **Broader verification**: all ordinary phases of `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` passed repository Ktlint, Detekt, release lint, full configured compilation, ordinary unit tests, and all configured KMP Android host suites. The retained complete final FIDO2 host reports (`cov017-final-ci-passed`) contain 957 tests and zero failures, errors, or skips. CI also exposed an existing import-order violation and a redundant test-helper parameter; both were corrected without changing assertions.
- **Final diagnostic**: the full `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` pipeline passed, including the fresh host/device gate. Canonical counters generated at `2026-10-27T00:52:40Z` (2026-10-26 local) show FIDO2 instructions 54,452/86,114 (63.232%) and branches 3,333/5,526 (60.315%): 95 additional covered branches from the clean pre-batch measurement, 17 above the exact 60% requirement of 3,316. Repository instructions are 74,125/132,917 (55.768%); all configured hard floors passed. The nonbinding 65% branch goal remains unmet. Counters, inventory, report, and matching source hashes are retained under `D:/octav/coverage-local-validation/cov017-final-diagnostic`.
- **Clean checkpoint**: Initial capture `5fad15f8-0604-46ea-b822-6b0997de4e26` measured clean commit `997cc3b3ccab94f1f57f5b9c770e98c5b969118f` with clean preflight/postflight and the same committed local harness. Its sealed bundle contains 200 suites / 1,267 test results, with zero failures, errors, or skips; the two HID suites retain 34 and 27 passing cases. The separate verifier exited 0 with collection Complete, tests Passed, floors Passed, and no unmet local reasons. FIDO2 instructions and branches match the diagnostic at 54,452/86,114 (63.232%) and 3,333/5,526 (60.315%); repository instructions are 74,125/132,917 (55.768%). Decision: `D:/octav/coverage-local-validation/assessment-5fad15f8-0604-46ea-b822-6b0997de4e26/decision.md`.
- **Status**: Component behavior Verified; full local CI and clean local measurement checkpoint Passed. The hard-floor deficit is now zero. The 65% branch improvement goal remains unmet. This Initial run has comparison compatibility Missing, regression NotApplicable, publication Pending, and integration Unverified; it is not a trusted accepted-target baseline or hosted integration decision. No further test batch or operational task was marked complete.

### Batch 1: Impact 4 (Critical Credential & Secret Protection)

#### COV-001: AesGcmEncryptedMetadataService Key Lifecycle & Unprovisioned Rejection
- **Module**: `:core:security`
- **Requirements**: `FR-006`, `TR-AUTH-031`, `NFR-SEC-041`
- **Impact**: 4 | **Likelihood**: 3 | **Selection Reason**: `Tier1Risk`
- **Initial State**: `AesGcmEncryptedMetadataService` initialized without calling `provisionKey`, or after invoking `clearKey()`.
- **Expected Result**: Calling `encrypt()` or `decrypt()` throws `IllegalStateException` with message `"Encryption key not provisioned"`.
- **Forbidden Effects**: No cipher initialization, no key retention, no null-pointer dereference.
- **Test File**: [AesGcmEncryptedMetadataServiceTest.kt](file:///d:/octav/source/repos/Chimali/core/security/src/test/kotlin/com/chimali/core/security/impl/AesGcmEncryptedMetadataServiceTest.kt)
- **Test functions**: `encrypt without provisioned key fails with illegal state`; `decrypt without provisioned key fails with illegal state`; `clearKey drops key and prevents subsequent encryption and decryption`.
- **Existing Action**: `Extend`

#### COV-002: SensitiveText Terminal Erasure, View Bounds, and Serialization Scrubbing
- **Module**: `:core:domain`
- **Requirements**: `NFR-SEC-041`, `TR-AUTH-031`
- **Impact**: 4 | **Likelihood**: 3 | **Selection Reason**: `Tier1Risk`
- **Initial State**: `SensitiveText` instances created via `takeOwnership` or `copyOf`.
- **Expected Result**:
  - `erase()` fills buffer with null characters (`\u0000`).
  - Borrowed views via `subSequence()` immediately reflect terminal erasure.
  - `subSequence()` throws `IllegalArgumentException` on negative start or start > end or end > length.
  - Borrowed view indexing throws `IllegalArgumentException` on index < 0 or index >= view length.
  - `copyOwner()` creates an independent owner whose erasure does not alter the original.
  - `toBoundaryString()` correctly exports characters to String.
  - `SensitiveTextSerializer` erases temporary character arrays on exit in both normal and exceptional flows.
- **Forbidden Effects**: No un-erased credential buffers left in heap memory.
- **Test File**: [SensitiveTextTest.kt](file:///d:/octav/source/repos/Chimali/core/domain/src/commonTest/kotlin/com/chimali/core/domain/model/SensitiveTextTest.kt)
- **Test functions**: `subSequence returns borrowed view reflecting owner mutations and erasure`; `subSequence rejects out-of-bounds start and end indices`; `view index access rejects out-of-bounds access`; `copyOwner creates isolated duplicate owner`; `serializer clears temporary buffer on encoder failure`; `serializer clears temporary buffer after successful encoding`.
- **Existing Action**: `Add`

#### COV-003: UserConsentRecord RP Erasure, Deep-Copy Isolation, and Validation Scrubbing
- **Module**: `:core:domain`
- **Requirements**: `NFR-SEC-041`, `TR-AUTH-031`
- **Impact**: 4 | **Likelihood**: 2 | **Selection Reason**: `Tier1Risk`
- **Initial State**: `UserConsentRecord` instances with valid or invalid RP URLs.
- **Expected Result**:
  - `eraseSensitiveText()` fills the underlying `rpIdOwner` with null characters.
  - `copy()` produces a deep copy of the sensitive RP text so erasing or modifying the copy does not affect the original.
  - `UserConsentRecord.fromText()` constructs record borrowing RP CharSequence and validates correctly.
  - When validation fails (e.g., blank ID or malformed RP), `rpIdOwner.erase()` is executed before re-throwing `IllegalArgumentException`.
  - `toString()` redacts sensitive metadata (`UserConsentRecord([sensitive metadata redacted])`).
- **Forbidden Effects**: No plaintext RP retention after validation rejection.
- **Test File**: [UserConsentRecordBehaviorTest.kt](file:///d:/octav/source/repos/Chimali/core/domain/src/commonTest/kotlin/com/chimali/core/domain/model/UserConsentRecordBehaviorTest.kt)
- **Test functions**: `eraseSensitiveText erases the owned RP buffer`; `copy performs deep copy of sensitive text isolating instances`; `validation failure erases the internal sensitive text buffer`.
- **Existing Action**: `Extend`

---

### Batch 2: Impact 3 (Data Integrity & Recovery)

#### COV-004: VaultAggregateServiceImpl Snapshot Hydration, Retry Exhaustion, and Memory Scrubbing
- **Module**: `:core:data`
- **Requirements**: `NFR-ARCH-050`, `NFR-SEC-041`
- **Impact**: 3 | **Likelihood**: 2 | **Selection Reason**: `Tier1Risk`
- **Initial State**: `VaultAggregateServiceImpl` with mocked `EventStoreRepository` and `SnapshotRepository`.
- **Expected Result**:
  - `getState` with `asOf == null` queries `snapshotRepository.getLatest`. If snapshot exists, hydrations calls `getEventsFrom(..., snapshot.sequenceNumber)` and evolves from `snapshot.state`.
  - `execute` with command retries up to `MAX_RETRIES` (3 times) on optimistic concurrency conflict. If all 3 retries fail, returns `Result.failure` with `RuntimeException("Aggregate command execution failed after retries")`.
  - When `currentStateResult.isFailure`, `execute` immediately returns the failure without calling decider.
  - When decider produces empty events, returns `Result.success(currentState)`.
  - When `state.sequenceNumber % SNAPSHOT_THRESHOLD == 0L`, saves snapshot to `snapshotRepository`.
  - In all execution paths (success, retry, failure), `clearSensitiveMemory()` is invoked on events and command.
- **Forbidden Effects**: No unscrubbed sensitive event or command state left in memory.
- **Test File**: [VaultAggregateServiceImplTest.kt](file:///d:/octav/source/repos/Chimali/core/data/src/test/kotlin/com/chimali/core/data/eventsourcing/VaultAggregateServiceImplTest.kt)
- **Test functions**: `getState should hydrate from snapshot when available`; `execute should retry on optimistic concurrency failure`; `execute returns failure when all retries are exhausted`; `execute clears command and event payloads when append is cancelled`; `execute returns early failure when getState fails`; `execute saves snapshot when sequence number reaches snapshot threshold`; `execute returns current state without append when decider yields no events`.
- **Existing Action**: `Extend`

---

### Batch 3: Impact 2 & 1 (Bounded Functional Failure & Scaffold Accounting)

#### COV-005: AesGcmEncryptedMetadataService Malformed Ciphertext Truncation
- **Module**: `:core:security`
- **Requirements**: `FR-006`, `NFR-SEC-041`
- **Impact**: 2 | **Likelihood**: 3 | **Selection Reason**: `Tier1Risk`
- **Initial State**: Provisioned `AesGcmEncryptedMetadataService`.
- **Expected Result**: Calling `decrypt` with ciphertext shorter than 1 + 12 bytes throws `IllegalArgumentException("Invalid ciphertext length")`.
- **Forbidden Effects**: No out-of-bounds array slicing or cryptic JCA exceptions.
- **Test File**: [AesGcmEncryptedMetadataServiceTest.kt](file:///d:/octav/source/repos/Chimali/core/security/src/test/kotlin/com/chimali/core/security/impl/AesGcmEncryptedMetadataServiceTest.kt)
- **Test function**: `ciphertext shorter than version and nonce fails with illegal argument`.
- **Existing Action**: `Extend`

#### COV-006: Feature Authenticator Scaffold Disposition Accounting
- **Module**: `:feature:authenticator`
- **Requirements**: `NFR-ARCH-050`
- **Impact**: 1 | **Likelihood**: 1 | **Selection Reason**: `Tier1Risk`
- **Initial State**: `:feature:authenticator` contains only `src/iosMain/README.md` and zero Kotlin sources.
- **Expected Result**: Complete inventory accounts for module with 0 instructions, 0 branches; verified as `NotApplicable` under `AnalyzedZeroProofV1`.
- **Forbidden Effects**: No artificial dummy code or fake tests added.
- **Test File**: N/A (`AnalyzedZeroProofV1`); inventory accounting only. It is not a verified behavioral test candidate.
- **Existing Action**: `None`

## 4. FIDO2 Continuation Cases (2026-10-25)

The current handoff identifies the active FIDO2 floor gap from sealed run `42bb76a8-47c1-49ba-9c8b-9afce9c281df`: 3,206/5,526 branches (58.02%), requiring 110 additional covered branches to reach 60%. The following cases were added against observable security/protocol behavior, not selected solely for branch yield:

| ID | Module | Behavior | Impact | Likelihood | Baseline Deficit | Status | Test Location |
|---|---|---|---:|---:|---|---|---|
| `COV-007` | `:feature:fido2` | Rejected passkey records erase all owned RP/account text | 4 | 2 | 110 branches to floor at sealed baseline | Focused and full host suites passed; measured impact is diagnostic only | `PasskeyCredentialTest.kt` — `rejecting invalid stored metadata erases every owned sensitive field` |
| `COV-008` | `:feature:fido2` | Mnemonic encryption failure erases imported phrase and validation seed without persistence | 4 | 2 | 110 branches to floor at sealed baseline | Focused and full host suites passed; measured impact is diagnostic only | `WalletMasterSeedProviderTest.kt` — `failed mnemonic encryption erases caller phrase and derived validation seed without persisting` |
| `COV-009` | `:feature:fido2` | CTAP allow-list/UV decoding and malformed UV rejection | 2 | 3 | 110 branches to floor at sealed baseline | Focused and full host suites passed; measured impact is diagnostic only | `Ctap2GetAssertionHandlerTest.kt` — `decodes allow-list identifiers and required user verification from CTAP request`; `rejects malformed CTAP user-verification option with invalid-CBOR status` |
| `COV-010` | `:feature:fido2` | Malformed allow-list containers/descriptors return invalid-CBOR before credential lookup; release the ceremony lock | 2 | 3 | 86 branches to floor in latest local diagnostic | Focused CTAP host suite passed; full configured host/device diagnostic passed; observed +1 covered branch versus preceding report, not sealed evidence | `Ctap2GetAssertionHandlerTest.kt` — `rejects malformed allow-list containers and descriptors before credential lookup` |
| `COV-011` | `:core:domain` | Vault retries preserve command payload until the next attempt, then erase command/event buffers on retry success, exhaustion, and cancellation; state cleanup erases its own title/payload buffers | 4 | 2 | No current Tier 1 floor deficit in the latest diagnostic | Targeted `VaultAggregateServiceImplTest` passed; full workload diagnostic passed; local results are not sealed evidence | `VaultAggregateServiceImplTest.kt` — `execute should retry on optimistic concurrency failure`; `execute returns failure when all retries are exhausted`; `execute clears command and event payloads when append is cancelled`; `clearing vault state erases title and payload buffers` |
| `COV-012` | `:core:domain` | Invalid copied consent records erase the rejected RP text owner without mutating the original record | 4 | 2 | No current Tier 1 floor deficit in the latest diagnostic | Focused consent suite and full configured host/device diagnostic passed; local results are not sealed evidence | `UserConsentRecordBehaviorTest.kt` — `validation failure erases the internal sensitive text buffer` |
| `COV-013` | `:core:domain` | Sensitive-text serializer clears its copied character buffer after successful encoding and encoder failure while preserving the original owner | 4 | 2 | No current Tier 1 floor deficit in the latest diagnostic | Focused `SensitiveTextTest` passed; latest complete ASUS host/device diagnostic passed; local results are not sealed evidence | `SensitiveTextTest.kt` — `serializer clears temporary buffer after successful encoding`; `serializer clears temporary buffer on encoder failure` |
| `COV-014` | `:feature:fido2` | HID rejects a never-bonded host without activating it; disconnecting a non-active host preserves the active session; active-host disconnect advances generation and erases queued owned reports | 4 | 2 | 82 branches to floor in latest local diagnostic | Focused Bluetooth HID host suite passed; full configured host/device diagnostic passed; local results are not sealed evidence | `BluetoothHidDeviceWrapperIncomingReportTest.kt` — `never-bonded host is disconnected without becoming the active session`; `active host disconnect advances generation and erases queued reports` |
| `COV-015` | `:feature:fido2` | A host connecting during Bluetooth bonding is not accepted as active, and its pending state is cleared if the host disconnects | 4 | 3 | 82 branches to floor at preceding complete diagnostic; new measured impact pending | Focused and full FIDO2 host suites passed; full device measurement pending | `BluetoothHidDeviceWrapperIncomingReportTest.kt` — `host connecting during bond is deferred and cleared if it disconnects` |
| `COV-016` | `:core:domain` | Malformed HTTPS origins with empty or whitespace-containing authorities are rejected | 4 | 3 | 0 gap at initial baseline; post-change coverage impact pending | Regression failed before bounded parser fix; focused RP and ownership suites passed; full Tier 1 remeasurement pending | `RelyingPartyTest.kt` — `RP ID parser rejects blank unsupported and malformed forms` |

### COV-015: Defer HID session admission until bonding completes

- **Module / requirements**: `:feature:fido2`; `NFR-SEC-041`, `FR-HID-020`.
- **Risk**: Impact 4, likelihood 3. A host can connect during externally initiated Bluetooth pairing; admitting the HID session before link-key completion can expose authenticator operations before the encrypted bond is ready.
- **Report evidence**: Latest completed dirty-tree report (preceding COV-015) lists 70 missed branches in `BluetoothHidCallbackCoordinator`; branch map identifies `handleConnected`'s `BOND_BONDING` branch and matching pending-device cleanup in `handleDisconnected` as unexecuted.
- **Initial / expected outcome**: A `STATE_CONNECTED` callback for a `BOND_BONDING` device records it as pending while leaving state `Advertising` and `connectedDevice` null. If it disconnects before bonding completes, pending state is cleared and no active session is created.
- **Forbidden effects**: Do not publish `Connected`, install the device as active, or retain a disconnected pending device.
- **Test and result**: `BluetoothHidDeviceWrapperIncomingReportTest.kt` — `host connecting during bond is deferred and cleared if it disconnects`; focused and complete `:feature:fido2:testAndroidHostTest` suites passed on 2026-10-26.
- **Measurement**: Full `hdkHostCoverage` could not complete because no Android device was connected. Keep the prior coverage counters as the last successful measurement; COV-015's coverage delta remains unmeasured.

### COV-016: Reject HTTPS-like RP IDs without a valid authority

- **Module / requirements**: `:core:domain`; `NFR-SEC-041`, `TR-AUTH-031`.
- **Risk**: Impact 4, likelihood 3. The RP ID identifies which credential is in scope for an authenticator request; accepting an origin with no host or whitespace in the authority can make origin identity ambiguous.
- **Report / assertion evidence**: The clean initial report records 25 missed branches in `RelyingParty`. The existing parser tests exercise domains, localhost, secure origins, unsupported schemes and malformed inputs, but did not cover empty authorities or whitespace in an origin authority.
- **Initial / expected outcome**: `isValidRpId` receives `https:///`, `https://?query`, `https://#fragment`, or `https://bad host/path` and returns `false`; valid HTTPS paths and localhost HTTP origins continue to pass.
- **Forbidden effects**: Do not accept or normalize an RP ID with no authority or whitespace inside its authority.
- **Test / result**: `RelyingPartyTest.kt` — `RP ID parser rejects blank unsupported and malformed forms`. The added cases failed before the production correction (`https:///` was accepted); focused RP parser and ownership tests passed after the correction on 2026-10-26.
- **Correction**: Parse the origin authority in-place over the existing `CharSequence`, stop at path/query/fragment delimiters, require a nonempty authority, and reject authority whitespace without creating a plaintext `String` copy.
- **Measurement**: No fresh Tier 1 measurement is available. Current diagnostic floor surplus remains based on the last completed report until the full workload can be rerun with a connected device.

After COV-007 through COV-009, a direct full Gradle diagnostic reported 3,229/5,526 FIDO2 branches (58.43%), 87 branches short of the 60% floor. After COV-010 and COV-013, the complete ASUS workload reported 3,230/5,526 branches (58.45%), 86 short. The latest complete ASUS workload after COV-014 reports 3,234/5,526 branches (58.52%), 82 short; FIDO2 instructions are 53,288/86,100 (61.89%). Latest Tier 1 counters: security 76.03% instructions / 61.92% branches, domain 71.18% / 70.70%, data 86.87% / 73.33%, FIDO2 61.89% / 58.52%; authenticator is 0/0 under proved-zero applicability. Repository instructions are 72,927/132,870 (54.89%). The configured host/device workload used ASUS_AI2302 (Android API 35), and both FIDO2 and app device result exit codes were 0. This direct diagnostic bypassed `capture.ps1` because the checkout was dirty; it is not a sealed baseline, candidate bundle, or integration decision. A Moto API 33 run immediately before the successful ASUS run recorded a FIDO2 device-result exit code of 1, so that run's lower counters are excluded from coverage evidence. Continue risk-ranked review across all five Tier 1 modules and capture a clean revision before marking any floor or regression requirement complete.

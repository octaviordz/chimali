# Behavior Test Selection and Remediation Ledger

**Feature**: 058-enforce-coverage-targets  
**Status**: Active  
**Last Updated**: 2026-10-24  

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

| ID | Module | Behavior | Reason | Impact | Likelihood | Live Deficit | Status | Test Location | Action |
|---|---|---|---|---|---|---|---|---|---|
| `COV-001` | `:core:security` | Unprovisioned / cleared key failure rejection | `Tier1Risk` | 4 | 3 | 0.0% (Passed) | `Verified` | `AesGcmEncryptedMetadataServiceTest.kt` | `Extend` |
| `COV-002` | `:core:domain` | `SensitiveText` terminal erasure, view bounds, serialization | `Tier1Risk` | 4 | 3 | +3.19% branch | `Verified` | `SensitiveTextTest.kt` | `Add` |
| `COV-003` | `:core:domain` | `UserConsentRecord` RP erasure, deep-copy isolation, validation wiping | `Tier1Risk` | 4 | 2 | +3.19% branch | `Verified` | `UserConsentRecordBehaviorTest.kt` | `Extend` |
| `COV-004` | `:core:data` | `VaultAggregateServiceImpl` snapshot hydration, retry exhaustion, memory scrubbing | `Tier1Risk` | 3 | 2 | 0.0% (Passed) | `Verified` | `VaultAggregateServiceImplTest.kt` | `Extend` |
| `COV-005` | `:core:security` | `AesGcmEncryptedMetadataService` malformed ciphertext length rejection | `Tier1Risk` | 2 | 3 | 0.0% (Passed) | `Verified` | `AesGcmEncryptedMetadataServiceTest.kt` | `Extend` |
| `COV-006` | `:feature:authenticator` | Evidenced empty iOS-only scaffold module disposition | `Tier1Risk` | 1 | 1 | 0.0% (N/A) | `Verified` | N/A (`AnalyzedZeroProofV1`) | `None` |

*Note on Live Deficits*: Module deficits are derived from the live baseline run `2ef82c38-e42f-4f67-9d62-9b73623b20e2`. `:core:domain` has a +3.19% branch deficit (56.81% observed vs 60.0% floor). Additional FIDO2 candidates will be integrated under T104 to remediate `:feature:fido2`'s +1.00% instruction and +5.60% branch deficit.

---

## 3. Detailed Case Specifications

### Batch 1: Impact 4 (Critical Credential & Secret Protection)

#### COV-001: AesGcmEncryptedMetadataService Key Lifecycle & Unprovisioned Rejection
- **Module**: `:core:security`
- **Requirements**: `FR-006`, `TR-AUTH-031`, `NFR-SEC-041`
- **Impact**: 4 | **Likelihood**: 3 | **Selection Reason**: `Tier1Risk`
- **Initial State**: `AesGcmEncryptedMetadataService` initialized without calling `provisionKey`, or after invoking `clearKey()`.
- **Expected Result**: Calling `encrypt()` or `decrypt()` throws `IllegalStateException` with message `"Encryption key not provisioned"`.
- **Forbidden Effects**: No cipher initialization, no key retention, no null-pointer dereference.
- **Test File**: [AesGcmEncryptedMetadataServiceTest.kt](file:///d:/octav/source/repos/Chimali/core/security/src/test/kotlin/com/chimali/core/security/impl/AesGcmEncryptedMetadataServiceTest.kt)
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
- **Existing Action**: `Extend`

#### COV-006: Feature Authenticator Scaffold Disposition Accounting
- **Module**: `:feature:authenticator`
- **Requirements**: `NFR-ARCH-050`
- **Impact**: 1 | **Likelihood**: 1 | **Selection Reason**: `Tier1Risk`
- **Initial State**: `:feature:authenticator` contains only `src/iosMain/README.md` and zero Kotlin sources.
- **Expected Result**: Complete inventory accounts for module with 0 instructions, 0 branches; verified as `NotApplicable` under `AnalyzedZeroProofV1`.
- **Forbidden Effects**: No artificial dummy code or fake tests added.
- **Test File**: N/A (`AnalyzedZeroProofV1`)
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

After COV-007 through COV-009, a direct full Gradle diagnostic reported 3,229/5,526 FIDO2 branches (58.43%), 87 branches short of the 60% floor. After COV-010, the latest full diagnostic reports 3,230/5,526 (58.45%), 86 branches short. FIDO2 instructions are 53,261/86,100 (61.86%). The latest full workload measured Tier 1 security at 76.03% instructions / 61.92% branches, domain at 71.14% / 70.70%, data at 86.87% / 73.33%, and FIDO2 at 61.86% / 58.45%; authenticator remained 0/0 under proved-zero applicability. Repository instructions were 72,891/132,861 (54.86%). It ran the configured host/device suites with the ASUS_AI2302 physical API 35 device and bypassed `capture.ps1` because the checkout was dirty. This is diagnostic only, not a sealed baseline, candidate bundle, or integration decision. Continue risk-ranked review across all five Tier 1 modules and capture a clean revision before marking any floor or regression requirement complete.

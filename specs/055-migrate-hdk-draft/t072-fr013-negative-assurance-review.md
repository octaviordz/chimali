# T072 FR-013 Negative-Assurance Review

**Reviewer**: Engineering AI agent (same reviewer as T005; not independent formal signoff)
**Date**: 2026-10-15
**Reviewed revision**: Branch `codex/055-migrate-hdk-draft`, working tree as of 2026-10-15
**Scope**: Complete FR-013 negative-assurance audit across all migration-touched source files

> Historical status snapshot: this 2026-10-15 artifact predates the later T072/T075 closure and the independent 2026-10-17 T103 source-delta disposition. Current review and activation scope is recorded in `security-review-packet.md`; this report's OPEN/NOT AUTHORIZED table reflects its original review date only.

## FR-013 Requirement

> "Migration-touched operations MUST protect derivation secrets, seeds, and private-key working material from persistence, logs, and diagnostics. Owners MUST erase owned mutable working buffers on completion, failure, and cancellation, with documented residual limits for cryptographic dependencies."

## Audit Method

Four parallel security auditors systematically reviewed 23 migration-touched source files across four categories:

1. **PERSISTENCE** — No secret written to DB, DataStore, SharedPreferences, files, or durable storage
2. **LOGGING** — No secret in `Log.*`, `Timber.*`, `Logger.*`, `println`, or diagnostic output
3. **DIAGNOSTICS** — No secret in error messages, `toString()`, crash reports, or exception propagation
4. **TERMINAL-PATH ERASURE** — Every owned mutable buffer containing secret material is erased in `finally` on all three paths: success, failure, and cancellation

## Audited Files (post-remediation)

### Core Security Module (6 files)

| File | Persistence | Logging | Diagnostics | Terminal Erasure | Residual Limits | Overall |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| `HkdfSha256.kt` | PASS | PASS | PASS | PASS | Not documented | **PASS-WITH-NOTES** |
| `P256ParentKeyDerivation.kt` | PASS | PASS | PASS | PASS | Not documented | **PASS-WITH-NOTES** |
| `HdkP256.kt` | PASS | PASS | PASS | PASS | Documented (L30) | **PASS** |
| `P256Group.kt` | PASS | PASS | PASS | ~~FAIL~~ → PASS | Documented (T072-F001) | **PASS** |
| `Bip39MasterSeedGenerator.kt` | PASS | PASS | PASS | PASS | Not documented | **PASS-WITH-NOTES** |
| `EventStoreKeyProviderImpl.kt` | PASS | PASS | PASS | ~~FAIL~~ → PASS | Not documented | **PASS** |

### FIDO2 Crypto Module (4 files)

| File | Persistence | Logging | Diagnostics | Terminal Erasure | Residual Limits | Overall |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| `WalletMasterSeedProvider.kt` | PASS | PASS | PASS | PASS | Not documented | **PASS-WITH-NOTES** |
| `Fido2CryptoService.kt` | PASS | PASS | PASS | PASS | Not documented | **PASS-WITH-NOTES** |
| `PostQuantumCrypto.kt` | PASS | PASS | PASS | PASS | Not documented | **PASS-WITH-NOTES** |
| `HmacSecretProcessor.kt` | PASS | PASS | PASS | PASS | Not documented | **PASS-WITH-NOTES** |

### UI, Database, and Persistence Module (13 files)

| File | Persistence | Logging | Diagnostics | Terminal Erasure | Residual Limits | Overall |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| `DevToolsViewModel.kt` | PASS | PASS | PASS | PASS | Documented (L96–98) | **PASS** |
| `DevelopmentToolsScreen.kt` | PASS | PASS | PASS | PASS | Documented (L603–605, L629) | **PASS** |
| `EncryptedDriverFactory.kt` | PASS | PASS | PASS | PASS | Documented (L24, L46, L95) | **PASS** |
| `MnemonicQrScanner.kt` | PASS | PASS | PASS | PASS | Documented | **PASS** |
| `SensitiveMnemonicInputPolicy.kt` | PASS | PASS | PASS | PASS (N/A) | N/A | **PASS** |
| `GetAssertionUseCase.kt` | PASS | PASS | PASS | PASS | Not documented | **PASS-WITH-NOTES** |
| `RegisterCredentialUseCase.kt` | PASS | PASS | PASS | PASS | Not documented | **PASS-WITH-NOTES** |
| `CredentialRepositoryImpl.kt` | PASS | PASS | PASS | PASS (N/A) | N/A | **PASS** |
| `CredentialRepository.kt` (interface) | PASS | PASS | PASS | PASS (N/A) | N/A | **PASS** |
| `PasskeyEventStoreRepositoryImpl.kt` | PASS | PASS | PASS | PASS | Documented | **PASS** |
| `PasskeySnapshotRepositoryImpl.kt` | PASS | PASS | PASS | PASS | Documented | **PASS** |
| `PrivacyScrubbingLogWriter.kt` | PASS | PASS | PASS | PASS (N/A) | Not documented | **PASS-WITH-NOTES** |
| `LocalCrashReportingLogWriter.kt` | PASS-WITH-NOTES | PASS | PASS | PASS (N/A) | Not documented | **PASS-WITH-NOTES** |
| `SecureMemory.kt` | PASS | PASS | PASS | PASS | Not documented | **PASS-WITH-NOTES** |
| `AesGcmEncryptedMetadataService.kt`† | PASS | PASS | PASS | ~~FAIL~~ → PASS-WITH-NOTES | Documented (KDoc, T072-F003) | **PASS-WITH-NOTES** |

> † `AesGcmEncryptedMetadataService.kt` pre-dates the migration (single pre-feature commit). Ownership boundary clarified via KDoc (T072-F003 resolved).

## Findings

### T072-F001 / MEDIUM — P256Group.serializeScalar intermediate buffer leak ✅ CLOSED

| Field | Description |
|---|---|
| ID | T072-F001 |
| Severity | MEDIUM |
| Affected component | `P256Group.kt` lines 82–91 (initial) → 82–95 (fixed) |
| Description | `serializeScalar()` called `scalar.toByteArray()` obtaining a mutable `bytes` buffer containing the private scalar. When the BigInteger representation was 33 bytes (leading zero, ~50% probability) or undersized, a new array was allocated via `copyOfRange`/concatenation and the original `bytes` buffer was abandoned on the heap without zeroing. No `try/finally` block existed. |
| Impact | Application-owned byte array containing a private P-256 scalar remained on the JVM heap until GC. |
| Fix applied | `bytes` is now wrapped in `try { ... return <copy> } finally { bytes.fill(0) }`. All three paths (exact 32-byte, 33-byte leading-zero, undersized) return a fresh copy and zero the intermediate buffer on success, failure, and cancellation. The immutable `BigInteger` residual remains outside application control (accepted documented limit). |
| Closure evidence | `:core:security:ktlintCheck :core:security:testAndroidHostTest` — `P256GroupTest` 9 tests, `P256ParentKeyDerivationTest` 3 tests, `HdkP256Test` 4 tests: **zero failures/errors/skips**. `tools/local-ci.ps1 -NoFix` passed (70.75s). |
| Status | **CLOSED** |

### T072-F002 / LOW — EventStoreKeyProviderImpl rootPrk acquisition outside try ✅ CLOSED

| Field | Description |
|---|---|
| ID | T072-F002 |
| Severity | LOW |
| Affected component | `EventStoreKeyProviderImpl.kt` lines 14–32 (initial) → 14–39 (fixed) |
| Description | `rootPrk` was acquired at line 15 and `label` at line 19 before the `try` block at line 21. If `toByteArray()` threw or coroutine cancellation occurred between lines 15–20, the `finally` would not execute, leaving the PRK copy without erasure. |
| Impact | Extremely narrow OOM/cancellation window during a single `String.toByteArray()` allocation on an already-owned defensive PRK copy. |
| Fix applied | `rootPrk` and `label` declared as nullable vars before `try`; assigned inside the `try` body; `finally` erases via `?.fill(0)` — covering all paths including partial acquisition failures. |
| Closure evidence | `:core:security:testAndroidHostTest` — `EventStoreKeyProviderImplTest` 3 tests: **zero failures/errors/skips**. `tools/local-ci.ps1 -NoFix` passed (70.75s). |
| Status | **CLOSED** |

### T072-F003 / LOW — AesGcmEncryptedMetadataService ownership boundary undocumented ✅ CLOSED

| Field | Description |
|---|---|
| ID | T072-F003 |
| Severity | LOW |
| Affected component | `AesGcmEncryptedMetadataService.kt` (pre-migration file) |
| Description | `provisionKey()` did not document whether the incoming `key: ByteArray` was caller-owned. `clearKey()` did not document that it only drops the reference without physical erasure. The overall caller/service ownership contract was implicit. |
| Impact | Pre-migration file; callers (`PasskeyEventStoreRepositoryImpl`, `PasskeySnapshotRepositoryImpl`) already erase their key/plaintext at their own boundary. Practical risk limited to contract ambiguity, not an actual secret leak. |
| Fix applied | Class-level KDoc added: callers retain and erase input key/plaintext arrays; returned plaintext/ciphertext is caller-owned; `SecretKeySpec` and JCA provider may retain internal copies not erasable by this class; `clearKey()` drops the service reference only. `provisionKey()` KDoc states the caller retains ownership of the input array. |
| Closure evidence | Documentation verified in source. No behavioral change. Existing caller erasure patterns in `PasskeyEventStoreRepositoryImpl` (lines 69–70, 78) and `PasskeySnapshotRepositoryImpl` (lines 71–72, 79) confirmed. |
| Status | **CLOSED** |

### T072-N001 / NOTE — Per-file residual limits not individually documented

| Field | Description |
|---|---|
| ID | T072-N001 |
| Category | Observation (no finding) |
| Affected files | `HkdfSha256.kt`, `P256ParentKeyDerivation.kt`, `Bip39MasterSeedGenerator.kt`, `WalletMasterSeedProvider.kt`, `Fido2CryptoService.kt`, `PostQuantumCrypto.kt`, `HmacSecretProcessor.kt`, `SecureMemory.kt` |
| Assessment | These files correctly erase application-owned mutable buffers but do not individually repeat residual limits for JCA/BC/ICU internal copies. Project-level residual limits are documented in `security-review.md` (lines 68–78) and `HdkP256.kt` (line 30), satisfying FR-013's documentation requirement at the design level. No finding raised. |

## Category Summaries (post-remediation)

### PERSISTENCE — PASS (23/23)
No migration-touched file intentionally persists secrets. All persistent stores use AES-GCM encryption (metadata, event store, snapshots) or Android Keystore-backed encryption (mnemonic recovery). Plaintext secrets exist only in volatile memory during derivation operations.

### LOGGING — PASS (23/23)
All migration-touched files use static diagnostic messages or the `PrivacyScrubbingLogWriter` scrubber. The scrubber strips throwable messages/causes, forwarding only exception class names and stack frames. Biometric error logging emits only numeric codes. CTAP handler paths use fixed labels. `LocalCrashReportingLogWriter` writes scrubbed messages and exception class/frames only — no message/cause text.

### DIAGNOSTICS — PASS (23/23)
Error returns use fixed `DomainError` descriptions. Exception messages contain static text only. No `data class` holds secret fields (no compiler-generated `toString()` leakage). `HdkP256.Result` and `P256ParentKeyDerivation.Result` are standard classes without `toString()` implementations.

### TERMINAL-PATH ERASURE — PASS (23/23, post-remediation)
All 23 files have systematic `try/finally` erasure of all owned mutable buffers on success, failure, and cancellation. T072-F001 (`P256Group`) and T072-F002 (`EventStoreKeyProviderImpl`) are fixed and verified by host tests. T072-F003 (`AesGcmEncryptedMetadataService`) resolved by documented ownership clarification; callers handle erasure at their own boundary.

## Independent Formal Reviewer Disposition

> [!IMPORTANT]
> **This review does not satisfy the ES-V.5 independence requirement.** This review was performed by the same engineering AI agent that completed T005. Under ES-V.5's custom-protocol classification, an independent formal reviewer — distinct from both the implementer and the engineering reviewer — must complete `security-review-packet.md` before production activation is authorized.

**Status**: The `security-review-packet.md` instrument is prepared and complete. No independent formal reviewer has been engaged. The activation gate remains **OPEN**.

## Disposition (2026-10-15; final engineering review)

**FR-013 Negative-Assurance: PASS** — All three source findings (T072-F001 MEDIUM, T072-F002 LOW, T072-F003 LOW) are closed. All 23 audited files pass all four FR-013 categories across persistence, logging, diagnostics, and terminal-path erasure, within documented residual limits for cryptographic dependencies.

### Gate status:

| Gate | Status | Notes |
|---|---|---|
| FR-013 negative assurance | ✅ **PASS** | All 3 findings closed; 23/23 files pass all categories |
| Independent formal review | 🔴 **OPEN** | No independent reviewer disposition recorded; `security-review-packet.md` ready |
| Production activation | 🔴 **NOT AUTHORIZED** | Independent formal review gate must close first |

### Test and CI evidence:

- `:core:security:ktlintCheck :core:security:testAndroidHostTest`: **43 tests, 0 failures, 0 errors, 0 skips**
  - `P256GroupTest` 9 ✅ · `HdkP256Test` 4 ✅ · `P256ParentKeyDerivationTest` 3 ✅ · `HkdfSha256Test` 5 ✅ · `Bip39MasterSeedGeneratorTest` 12 ✅ · `EventStoreKeyProviderImplTest` 3 ✅ · `SecurityModuleTest` 2 ✅ · `PlatformUserVerificationTest` 4 ✅ · `ResourceLoaderTest` 1 ✅
- `tools/local-ci.ps1 -NoFix` (**70.75s**): Ktlint ✅ · Detekt ✅ · Lint (Release) ✅ · Compile All ✅ · Unit Tests ✅

### Residual risks accepted (unchanged from T005):

1. Root compromise is total (PRK → all branches). Documented; no false containment claim.
2. Modulo bias ~2⁻¹²⁸. Accepted per draft §11.
3. `BigInteger` not erasable on JVM. Confined to shortest scope; cannot be eliminated.
4. Software parent is exportable. Keystore protects ciphertext, not the signing scalar.
5. No account discovery from mnemonic alone. Recovery requires credential metadata.
6. `draft-dijkhuis-hdk-00` is an Internet-Draft. Pinned at version 00.
7. BC/JCA/ICU/Compose/camera/IME/clipboard/OS internal copies are not application-erasable.

## Independent formal review follow-up (2026-10-15)

The subsequent independent formal security evaluation is recorded in [`independent-formal-security-review.md`](independent-formal-security-review.md), with its disposition in [`security-review-packet.md`](security-review-packet.md). That reviewer issued **ACCEPT** after reviewing the local composition, implementation, vectors, lifecycle controls, and the three remediated findings. Acceptance is limited to Chimali's named local profiles and does not claim draft issuance interoperability or hardware custody. This later disposition supersedes the earlier “independent formal review remains open” statements above. T072 is complete; production activation is permitted from the cryptographic security review perspective, subject to remaining non-security convergence work.

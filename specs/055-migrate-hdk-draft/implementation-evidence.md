# Implementation Evidence — Local HDK Profile

**Feature**: 055-migrate-hdk-draft  
**Branch**: `codex/055-migrate-hdk-draft`  
**Evidence started**: 2026-10-14  
**Current status (2026-10-18)**: **205 of 207 defined task rows are checked; T084/T085 remain explicitly user-deferred.** T127 remediation has no task definition. T128 ACCEPT and activation authority remain limited to its exact reviewed 235-entry fingerprint and T130-T136; subsequent changes remain outside that authorization. The latest full integrated local-CI/coverage gate remains T203–T204 at 198.88 seconds: 864 FIDO2 host tests, security 50, connected Mi A2 Android 10 FIDO2 23/23 and app 11/11, and all configured coverage floors. `CredentialPersistenceCeremonyTest` remained excluded because Android 10 lacks its required independent non-BC Ed25519 verifier. Exact rates and reports are in the T203–T204 checkpoint below. T205–T208 focused security Ktlint, host compilation, service/wallet/repository regressions passed separately and do not refresh full coverage or connected-device evidence. SQLDelight-generated classes remain excluded from authored-source coverage denominators. ASUS Zenfone 10 remains the user-waived reference basis; SC-007 is partial because performance tasks T084/T085 are deferred. No current-source independent security acceptance, production activation or deployment is claimed.

## T001 — Pre-change source, caller and test inventory

### Build and source-set discovery

Inspected `core/security/build.gradle.kts`, `core/domain/build.gradle.kts`, and
`feature/fido2/build.gradle.kts`. The modules configure Android `withHostTest {}`; FIDO2 also
configures `withDeviceTest {}` and its Gradle dependencies include Compose UI test. The requested
command completed successfully on 2026-10-14:

```text
./gradlew.bat :core:security:tasks :core:domain:tasks :feature:fido2:tasks --all --console=plain
BUILD SUCCESSFUL (3 actionable tasks)
```

The actual task model exposes `testAndroidHostTest` and `androidConnectedCheck` in the modules.
FIDO2 exposes a configured `androidDeviceTest` source set. `feature/fido2/src/androidTest/...`
contains several existing instrumented suites, including `presentation/ui/DevelopmentToolsScreenTest.kt`;
it is not the source-set path specified for the feature's configured `androidDeviceTest`. Crypto
files under `core/security/src/test/.../hdkeys/` include `HdkEcdhP256Test.kt`,
`HdkNegativePathTest.kt`, `HashToScalarTest.kt`, `MultiplicativeBlindingTest.kt`, and
`DhKemTest.kt`; these are legacy `src/test` paths, distinct from configured Android host tests.
`core/security/src/androidHostTest/.../HdkMigrationTest.kt` also imports the retired implementation.

### Retired runtime implementation and compile-time dependents

Pre-change production files found by API/caller search:

- `core/security/src/androidMain/kotlin/com/chimali/core/security/api/HdkManager.kt`
- `core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/{HdkEcdhP256,HashToScalar,MultiplicativeBlinding,DhKem,P256Group}.kt`
- `core/security/src/commonMain/kotlin/com/chimali/core/security/api/{HdkTypes,MasterSeedProvider}.kt`
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/{WalletMasterSeedProvider,Fido2CryptoService,PostQuantumCrypto}.kt`
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/worker/CorruptedKeyRepairWorker.kt`
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/{dao/PasskeyCredentialDao,mapper/EntityMappers,repository/CredentialRepositoryImpl}.kt`
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/usecase/RegisterCredentialUseCase.kt`
- `feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/{Fido2Database,PasskeyCredential}.sq`

Other compile-time callers observed include `core/security/.../di/SecurityModule.kt`,
`feature/fido2/.../di/Fido2Module.kt`, `Fido2Initializer.kt`, `Fido2HomeViewModel.kt`,
`RegisterCredentialUseCase.kt`, `CredentialRepositoryImpl.kt`, and host test fixtures for
`Fido2CryptoService`, wallet provider, credential repository/DAO, integration registration,
multi-algorithm and stress scenarios. Search output is retained by rerunning the documented
`rg -l` retired-symbol query; this list is a purpose inventory, not a claim that every matching test
will remain after the story replacement.

The old production behavior includes HDK salt/context and remote KEM APIs, old P-256 blinding
helpers, a separate PQ seed cache plus custom deterministic random adapter, alias-based key
identity, and public-key repair scheduling. Each is slated for retirement under the current plan;
no production code was changed as part of this inventory.

### Repository state at inventory start

`git status --short --branch` reported branch `codex/055-migrate-hdk-draft` and pre-existing
staged/unstaged planning artifacts: `.specify/feature.json`, `AGENTS.md`, the OpenID4VCI backlog
item, and the feature 055 analysis/spec/plan/tasks/contracts/research/security/quickstart documents.
These were present before implementation changes and were not committed or reset by this workflow.

### Tier scopes

The plan identifies `core:security`, `core:domain`, and `feature:fido2` as Tier 1. It identifies
`core:database`, `core:common`, and `app` as affected Tier 2; any additionally changed Tier 2 owner
must be added to coverage accounting. Whole affected-module and repository coverage evidence has
not yet been collected.

## T003 — Baseline measurement status

The required baseline device/workload measurements are not available yet. No reference Pixel 6a or
equivalent device measurement, disposable-install startup/request timing, Bluetooth HID end-to-end
latency, baseline release binary size, peak/session memory, or LeakCanary session was performed
before implementation. The Gradle discovery command above is not a build, test, coverage or
performance measurement. Existing whole-module/repository coverage floors have not been verified.
No missing measurement is recorded as a pass. T003 remains open until the same workload and build
variant can be measured before/after on the reference device and current coverage evidence is
collected.

## T008 — Coverage wiring and measurement snapshot

Added `tools/hdk-coverage.init.gradle` with JaCoCo 0.8.15, the `hdkHostCoverage` and
`hdkCoverageVerify` tasks, report-wide XML/HTML/CSV generation, module discovery checks, and
non-zero JUnit testcase checks. JaCoCo instruction coverage is used as the bytecode statement
proxy; line coverage is reported separately. Each Android/KMP module contributes one production
variant to avoid duplicate denominator classes. The repository aggregate discovers every
production project with a supported compiler task. Device `.ec` files are included when a
configured device suite has generated them; this run used host/unit execution only.

On 2026-10-14, the complete configured host/unit workflow was run with
`hdkCoverageVerify --continue`. It produced reports but returned test failures: FIDO2 had 458
tests with 127 failures, predominantly old fixtures rejected by the new full-ID boundary; common
had 10 tests with 1 failure. Other discovered suites reported security 32, domain 25, database 8,
and app 2 testcases. A second verifier invocation excluded test task execution to check the gate
against the reports from that full run; it correctly failed the configured coverage floors below.
No failed test suite is represented as passing.

| Scope | Instructions / statement proxy | Lines | Branches | Current floor result |
| --- | ---: | ---: | ---: | --- |
| `core:security` module | 55.45% | 53.57% | 51.35% | pass |
| `core:security` HDK scope | 60.58% | 59.39% | 56.67% | pass |
| `core:domain` | 39.51% | 48.20% | 24.75% | fail (40% / 40%) |
| `feature:fido2` | 20.69% | 25.95% | 19.04% | fail (40% / 40%) |
| `core:database` | 12.12% | 8.66% | 26.92% | fail (20%) |
| `core:common` | 20.63% | 24.26% | 13.79% | pass (branches reported) |
| `app` | 4.65% | 10.73% | 11.83% | fail (20%) |
| Repository aggregate | 24.46% | 30.30% | 25.56% | pass (20%) |

XML and HTML reports are generated at:

- HDK scope: `core/security/build/reports/hdk-coverage/hdkScopedCoverage.xml` and
  `core/security/build/reports/hdk-coverage/hdkScopedCoverage/`
- Affected modules: each of `core/security`, `core/domain`, `feature/fido2`, `core/database`,
  `core/common`, and `app` has `build/reports/hdk-coverage/hdkModuleCoverage.xml` and
  `build/reports/hdk-coverage/hdkModuleCoverage/`
- Whole repository: `build/reports/hdk-coverage/repository.xml` and
  `build/reports/hdk-coverage/repository/`

The configured gate reports Tier 2 branch percentages without imposing a branch floor and fails
on missing reports, undiscovered required modules/compilers/test tasks, zero testcases, or missed
coverage floors. The present floor failures remain implementation/acceptance work tracked by
T064/T068; they were not hidden by lowering thresholds or dropping modules. T008 is complete as
coverage wiring and truthful measurement evidence, not as closure of the feature's final coverage
gate.
This coverage snapshot predates the subsequent test-first additions recorded under T011, T015,
and T020; T064 will refresh and enforce coverage after the integrated implementation.

## T020 — Root-consumer ownership tests (red before implementation)

Updated the configured EventStore, database, and `hmac-secret` tests with test-only independent
HMAC/PBKDF2 expectations, borrowed-input preservation assertions, and owned-root-copy zeroing
checks. The database KDF test still matches JCE `PBKDF2WithHmacSHA512` at 2048 iterations and
continues to preserve its borrowed seed input; the database root-copy test injects a synthetic
driver-creation failure after key derivation.

Focused results on 2026-10-14:

- `:core:security:testAndroidHostTest --tests com.chimali.core.security.impl.EventStoreKeyProviderImplTest`:
  3 tests, 1 failure. The independently expected 32-byte HMAC-SHA512 result matches, while the
  provider-owned root copy remains non-zero.
- `:core:database:testDebugUnitTest --tests com.chimali.core.database.EncryptedDriverFactoryTest`:
  5 tests, 1 failure. The independent PBKDF2 output and borrowed-seed preservation pass; the
  provider-owned root copy remains non-zero after injected driver-creation failure.
- `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.crypto.HmacSecretProcessorZeroingTest`:
  2 tests, 2 failures. The independently expected extension result matches, but the direct caller
  salt is overwritten from byte 32 and the provider-owned root copy remains non-zero.

These are behavioral-red results for the root consumers, not passing cleanup evidence. T020 is
complete as the test-authoring task; production fixes and green results remain assigned to T031,
T032 and T033.

## T011 — P-256 group test-first results (partial)

Added configured host tests at `core/security/src/androidHostTest/kotlin/com/chimali/core/security/hdkeys/P256GroupTest.kt`
for the known SEC1 generator encoding, borrowed-point round trip, strict scalar range/width, and
uncompressed finite-point requirements. On 2026-10-14,
`:core:security:testAndroidHostTest --tests com.chimali.core.security.hdkeys.P256GroupTest`
compiled and ran all five tests: two passed and three failed because current production helpers
still accept compressed points, out-of-range scalars, and variable-width scalars. The child
operation tests in `HdkP256Test.kt` remain unimplemented because the production HDK API does not
exist yet and the independent-review gate has not authorized its implementation. T011 remains
open.

## T015 — ML-DSA cross-engine verification

Extended `PostQuantumCryptoTest.kt` with an independent synthetic public-key hash, the published
NIST ACVP ML-DSA-65 key-generation vector, and a deterministic signature fixture generated and
self-verified by pinned `dilithium-py` 1.5.1. The test imports the NIST seed through the production
BC 1.80 wrapper, checks the public-key hash, verifies the independent signature through BC, and
rejects the same signature for a changed message. The fixture is generated and checked by
`tools/hdk-reference/generate-fixtures.py`; it contains synthetic test material only. Both generator
modes completed with Python 3.14.7 and OpenSSL 3.5.7. The focused command
`:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.data.crypto.PostQuantumCryptoTest'
--console=plain` passed on 2026-10-14; report:
`feature/fido2/build/reports/tests/testAndroidHostTest/index.html`. Expanded the suite to exercise
missing-provider behavior for capability detection, generation and signing; a test private-key
encoding is also asserted zeroed after the provider-failure exit. Bad seed lengths and borrowed
seed preservation are covered. The latest focused run passed **12 tests, 0 failures**. T015 is
complete.

## T036 — BC 1.80 ML-DSA direct seed import

Replaced custom deterministic randomness with BC 1.80 `MLDSAPrivateKeySpec` import of the exact
32-byte `xi` seed and `MLDSAPrivateKey.getPublicKey()`. The wrapper retains current X.509 public
encoding and signature operations, copies borrowed seed input before import, and clears that copy
plus the returned private-key encoding after generation/signing. The production path matches the
independent local public-key fixture and NIST ACVP public-key hash. The focused post-quantum suite
passed with the cross-engine fixture; task T036 is complete.

## T019 — Mutable mnemonic ViewModel owner tests

Added replacement, screen-hide-during-load, ViewModel-disposal, and cancellation-during-import
cases to `DevToolsViewModelTest.kt`. The first focused run failed in all three new owner-erasure
cases; the implementation then gained explicit reveal/import job cancellation, generation guards,
mnemonic-buffer erasure on replacement and `onCleared`, and loading-state cleanup on cancellation.
The latest `:feature:fido2:testAndroidHostTest --tests
'com.chimali.fido2.presentation.viewmodel.DevToolsViewModelTest' --console=plain` run passed all
**13 tests, 0 failures**. Source audit confirms the reveal action is dispatched from the existing
biometric-success callback and the ViewModel does not use `SavedStateHandle` or store mnemonic
text as an immutable state field. The existing UI sends reveal only from the biometric-success
callback; the mutable Compose field boundary and QR/UI lifetimes are handled in T021/T034. After
the mutable import API change below, the host suite was rerun and still passed 13/13. T019 is
complete.

## T021/T034 — Compose test relocation and mutable UI input progress

Moved `DevelopmentToolsScreenTest.kt` from the inactive `src/androidTest` tree to configured
`src/androidDeviceTest`; the module already declares its `withDeviceTest` target and Compose
`ui-test`/`ui-test-junit4` dependencies, so no duplicate source registration or dependency edit was
needed. Updated it to use a mutable mnemonic owner and added QR show/hide plus explicit clear-action
coverage. `:feature:fido2:compileAndroidDeviceTest` succeeds, confirming the named test is in the
configured compilation. At that earlier checkpoint, instrumented execution was unavailable:
`adb devices -l` reported no connected device and the installed emulator reported no AVD. The
emulator was later made available and the named connected run is recorded at the end of this file.

Changed the recovery intent and ViewModel boundary to transfer a `CharArray` directly; word-count
validation scans characters, and the ViewModel erases that exact owner on invalid input, success,
failure, cancellation and disposal. Manual entry now stores one erasable character buffer per word,
replacing and wiping it on edits; it builds one mutable phrase buffer for transfer and clears all
field owners on submit/disposal. Compose's `TextField` API still needs a transient `String` for
render/input callbacks, but the full phrase is no longer retained in a `TextFieldValue` draft list.
QR scanning counts words without tokenizing into `List<String>` and transfers a trimmed mutable
copy; late scans after disposal are discarded and erased. The QR painter receives a non-remembered
String scoped to the visible QR composition, which is removed when the user hides the QR. Other
immutable boundaries are ML Kit's callback `String`, per-word Compose `Text`, the QR painter's
input and the clipboard API's `String`; these platform representations cannot be zeroed. The
clipboard service still marks copied text sensitive and clears it after its existing 60-second
timer. No full phrase is placed in `rememberSaveable`, saved state, logs or ViewModel immutable
fields. The T034 boundary audit is complete; the configured `DevToolsViewModelTest` passed 14/14,
including owner lifetime through hide, and the configured Compose device-test source compiles.
Instrumented interaction/accessibility execution remains under T021/T051 because no device or AVD
is available on this host.

T035 partial: `ChimaliApplication.onTrimMemory` calls the provider generation fence and GC hint
for exactly `TRIM_MEMORY_RUNNING_CRITICAL`, `TRIM_MEMORY_BACKGROUND` and
`TRIM_MEMORY_MODERATE`; the crypto service rejects/erases results from an old generation.
`MainActivity.onStop` selectively cancels foreground owners. HID CTAPHID CBOR work is tracked per
CID, CTAPHID_CANCEL cancels that operation, and disconnect/Advertising/Idle cancels and joins
in-flight requests. The provider's overlapping-owner test confirms foreground loss preserves an
admitted authenticator owner. T035 remains open for the exact request-wide timeout binding, actual
transport/disconnect test evidence, and device/platform execution of the full memory/lifecycle
matrix.

## T010 — HKDF and deterministic parent test-first results

Added configured Android host tests for RFC 5869 Appendix A.1 extraction/expansion, empty-salt behavior, borrowed-input preservation, full 64-byte synthetic BIP39-root extraction, and the frozen local parent input. Added RFC 9180 Appendix A.3.1 DHKEM(P-256) key-generation expected scalar/public key, plus sampler cases for a valid counter-255 candidate, invalid scalar candidates, exhaustion, and erasure of rejected candidates and the labeled PRK. Expected bytes are published vectors or frozen outputs from the independent fixture generator; no production function is used as an oracle. These are test-first files for T025/T026. `:core:security:compileAndroidHostTest` was attempted; it currently fails only because the planned `HkdfSha256` and `P256ParentKeyDerivation` production classes are absent (with lambda inference diagnostics cascading from the absent parent API). No other source diagnostics were reported. The T005 design gate is complete; formal independent review remains an activation gate. T010 is complete as test authoring; focused verification remains part of T050 after the classes are implemented.

## T011 — P-256 child and group test-first results

Added `HdkP256Test.kt` using the independent direct-child fixture family for indices 0, 1, `0x80000000`, and `0xffffffff`; it asserts preimages, digests, mapped factors, child scalars/public points, parent correspondence, and the child-composition relation. It checks the complete UInt domain, malformed lengths/scalars/points, mismatched parent pairs, borrowed-input preservation, and that callers own and erase returned factors/private scalars. Expanded `P256GroupTest.kt` with canonical scalar boundary round trips and expected rejection of zero/out-of-range scalars, infinity, and invalid SEC1 coordinates. The current `:core:security:compileAndroidHostTest` attempt reports unresolved references only for the planned `HdkP256`, `HkdfSha256`, and `P256ParentKeyDerivation` production APIs (plus cascading lambda/type errors); it reports no diagnostics from `P256GroupTest.kt`. T011 is complete as test authoring; behavioral verification remains for T027/T028 and T050 after the implementation gate.

## T016 — Event identity test-first results

Added required algorithm/profile fields to registration commands and invalid-sentinel defaults to
registered events and passkey state as minimal declarations for the tests. The decider and event
evolution behavior have not yet been updated. Tests now cover command propagation, mismatched and
invalid sentinel rejection, explicit event JSON fields and round trip, replay/snapshot state
identity, existing register/authenticate/delete transitions, and the 20-event snapshot cadence.
The repository command path now supplies the credential's COSE algorithm and its explicit profile.

Focused results on 2026-10-14:

- `:core:domain:testAndroidHostTest --tests com.chimali.core.domain.eventsourcing.passkey.PasskeyDeciderTest`:
  10 tests, 5 failures. The new behavior-red failures show missing command-to-event propagation,
  invalid command-sentinel acceptance, mismatched command profile acceptance, invalid
  registered-sentinel acceptance, and event-to-state identity loss. Serialization with the
  explicit new values and existing state transitions pass.
- `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.eventsourcing.PasskeyAggregateServiceImplTest`:
  5 tests, 2 failures. The two failures show profile loss during event replay and snapshot hydration;
  the existing transitions and 20-event snapshot cadence pass.

T016 is complete as the test-authoring task. T039 remains open for validation, propagation, and
serialization implementation; the focused suites are expected to turn green there.

## T024 — Common byte-only security API

Moved `HdkManager` to `commonMain` and reduced it to borrowed byte inputs for direct-child public
derivation and child-scalar derivation. Replaced the old path/salt/remote-KEM `HdkResult` contract
with owned `HdkKeyPair` and `HdkChild` buffers plus a public-only generation result. Replaced
`MasterSeedProvider` with documented 32-byte PRK and parent-copy ownership, mutable mnemonic
get/import contracts, and no PQ-seed getter. Replaced `MasterSeedGenerator` with a 24-word
`CharArray` boundary, empty fixed passphrase, and full 64-byte seed contract. The secret-bearing
types no longer expose generated data-class copy/component aliases. Old production implementations,
callers, and tests still reference the retired API and remain scheduled for T029–T049/T048 cleanup;
integration compilation is therefore deferred to T049/T050. No test suite was run for this API-only
replacement.

## T002 — Independent reference fixtures

Created `tools/hdk-reference/generate-fixtures.py`, pinned test-only dependencies in
`tools/hdk-reference/requirements.txt`, and documented provenance/limits in
`tools/hdk-reference/README.md`. The generator does not import Chimali production code and uses
only public deterministic vectors plus synthetic inputs. Before producing local outputs it passed
RFC 5869 A.1, RFC 9180 A.3.1 DHKEM(P-256), RFC 8032 Ed25519 test 1, and the NIST ACVP ML-DSA-65
internal key-generation seed/public/secret hash checks. It verifies the local BIP39 word list
matches the pinned reference package and the standard TREZOR seed vector.

The generated fixture contains the synthetic 64-byte BIP39 result, local HKDF root and parent,
three supported credential branches at decoded identifier lengths 16, 32 and 1023, direct-child
vectors for indices 0, 1, `0x80000000`, and `0xffffffff`, and a pair of distinct identifiers that
collide under the retired four-byte shortened mapping but derive distinct full-ID secrets. P-256
child arithmetic uses independent Python integer point operations and is checked by reconstructing
the scalar public key through `cryptography` / OpenSSL. Ed25519 and ML-DSA public outputs come from
the vector-checked reference implementations. The independent ML-DSA implementation is a fixture
oracle only, not a production dependency.

The initial reproducibility command was executed twice on 2026-10-14:

```text
D:\temp\chimali-hdk-ref-20261014\Scripts\python.exe tools/hdk-reference/generate-fixtures.py
```

Both initial runs passed the reference-vector assertions. A fresh third run used a disposable
virtual environment with the exact pinned requirements:

```text
C:\Users\octav\AppData\Local\Temp\chimali-hdk-reference-venv\Scripts\python.exe tools/hdk-reference/generate-fixtures.py
```

All runs produced identical fixture SHA-256:
`CD0E70E8770D60F232EC3E977ED0F6EF28BF15DD7D905F0264A3CC59F05B6CC6`. Runtime reported Python
3.14.7 and OpenSSL 3.5.7; dependency versions are pinned. The ACVP source commit and exact
dependency provenance are documented in the tool README. T002 is complete. These fixtures do not
constitute independent security review or protocol signoff.

The generator was freshly rerun in a disposable virtual environment with the pinned requirements
on 2026-10-14. It passed all vector assertions and rewrote the fixture with the same SHA-256 above.
The active runtime was Python 3.14.7 / OpenSSL 3.5.7, confirmed by the generated provenance.
Added a read-only `--check` mode and ran it with the same environment. It independently repeated
the RFC 5869, RFC 9180, RFC 8032, and NIST ACVP ML-DSA-65 checks, reconstructed the full synthetic
fixture, and confirmed structural equality without writing the file; its SHA-256 remained
`CD0E70E8770D60F232EC3E977ED0F6EF28BF15DD7D905F0264A3CC59F05B6CC6`.

Recreated the disposable environment on 2026-10-14 after the earlier temporary environment expired,
installed the pinned requirements, and reran `generate-fixtures.py --check`. It again verified the
frozen fixture with no write; runtime was Python 3.14.7 / OpenSSL 3.5.7 and the fixture SHA-256
remained `CD0E70E8770D60F232EC3E977ED0F6EF28BF15DD7D905F0264A3CC59F05B6CC6`.

## FIDO2 host fixture follow-up

The strict full-length `CredentialId` boundary exposed short IDs throughout older FIDO2 host-test
fixtures. These were migrated to the shared `testCredentialIdFromLabel` builder, and
`PasskeyCredentialTest` now keeps its encoded ID and raw byte field consistent. Its focused run
completed 21 tests with zero failures on 2026-10-14:

```text
:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.domain.model.PasskeyCredentialTest*'
```

The latest full `:feature:fido2:testAndroidHostTest` run discovered and executed 462 tests, with 68
failures. Sixty-two fail while MockK reflectively creates invalid default instances of the strict
`CredentialId` value class for broad `any()` stubs; the remaining six are existing or intentional
behavioral-red assertions in the secret-ownership and ML-DSA test-first work, plus the pagination
and consent tests. No suite pass is claimed. These failures remain visible for the integrated
verification tasks; relaxing production identifier validation is not an acceptable fixture fix.

## T003 — Baseline measurement availability

The baseline capture was inspected on 2026-10-14. Existing Android artifacts predate this feature
branch and are usable as historical size evidence only:

| Artifact | Size | Last modified | SHA-256 |
| --- | ---: | --- | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | 123,193,629 bytes | 2026-09-20 16:51:27 | `456031DD6FE8EE73D03A4E4EEBD61A780C248CF028B8C82AC9A516918AF79AFC` |
| `app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk` | 62,421,943 bytes | 2026-09-09 17:35:31 | `8B7E49F220DCAB997F35F6AC3F55936AAA9DED5517EB699B9D0426BC5DB9026E` |
| `app/build/outputs/apk/androidTest/shrunkDebug/app-shrunkDebug-androidTest.apk` | 1,763,064 bytes | 2026-09-09 17:41:08 | `AACCB57E18CCB3438E86DB8900AF269A10909922DEB6C671C344643A1B4EBB99` |

No release AAB was present, so the constitution's 50 MB AAB budget cannot be evaluated from these
APKs. Rechecked Android SDK `adb.exe devices -l` on 2026-10-14 using
`C:\Users\octav\AppData\Local\Android\Sdk\platform-tools\adb.exe`; the attached-device list
was empty. Consequently, no Pixel 6a (or
equivalent) startup timings, Bluetooth HID authentication latency, or device peak/session memory
were measured. The repository has a `feature/fido2/src/macrobenchmark` source file for credential
list frame timing, but no active build configuration was found for that source set; its package
name and navigation are placeholders and it does not measure startup or HID authentication. No
usable startup/HID benchmark harness or device memory profiler is currently configured. These are
unmeasured, not passing results; T066 cannot claim budget compliance until a reference device and
valid measurements are available.

The available whole-module/repository coverage snapshot is recorded under T008, including scoped
Tier 1, Tier 2, and repository reports and the pre-existing threshold failures. That snapshot
predates the T011, T015, and T020 test additions; T064 must refresh it against the integrated
implementation. Baseline binary metadata and missing performance measurements are recorded, but
T003 remains open pending the required reference-device measurements.


### T003 ASUS baseline addendum (2026-10-15)

The ASUS Zenfone 10 (model `ASUS_AI2302`, Android 15/API 35, build `asus/WW_AI2302/ASUS_AI2302:15/AQ3A.240812.002/35.0604.0404.165:user/release-keys`, 7,387,708 kB reported RAM) was connected over ADB as serial `R7AIB7002901AZ3`. **User waiver (2026-10-15): ASUS is accepted as the project reference device for T003 in place of the Pixel 6a-class requirement.** Future T003 baseline/current measurements should use this same ASUS hardware, with matching build variants and workload. This waiver changes the device criterion only; it does not waive the matched workload or other SC-007 measurements.

The tested package was exactly `com.chimali`, version `0.1.0`, installed from the release APK built at baseline commit `8b4d85ad`. Android package flags lacked `DEBUGGABLE`. The installed APK was pulled from the device and SHA-256 verified as `DB6CF22B7FE701373713B3735CC0C03DD449EC4EBAAF52383982308A1E759A99`; size 62,440,630 bytes. This matches the preserved baseline release APK. No `com.chimali.baseline` package was used for this run. The workstation's current untracked-by-checkout feature artifacts are not a valid substitute for baseline artifact-size comparison.

Startup was sampled with `am start -S -W` (cold process), home then `am start -W` (hot), and `am start -W -f 0x10008000` (warm task), 10 repetitions each, approximately 1.5 seconds between launches. `WaitTime` in milliseconds is reported; this is Activity Manager launch timing, not first-frame/fully-drawn rendering. Results:

| Mode | n | Median WaitTime | Range | p95 (nearest-rank) |
| --- | ---: | ---: | ---: | ---: |
| Cold | 10 | 247.5 ms | 226–270 ms | 270 ms |
| Hot | 10 | 77 ms | 56–89 ms | 89 ms |
| Warm | 10 | 106 ms | 99–139 ms | 139 ms |

The CSV is `D:\temp\chimali-t003-asus-chimali-startup-samples.csv` and the capture script is `D:\temp\chimali-t003-asus-chimali-measure-startup.ps1` (outside the repository). The capture CSV contains UTC timestamps and per-sample `LaunchState`, `WaitTime`, and `TotalTime`.

The local release artifacts present on this checkout are 62,410,019 bytes for `app-release-unsigned.apk` (SHA-256 `9C7452D58120EA5C234F110FF38572A48AE6481788D5296E19484F1123DBA6FB`) and 40,994,234 bytes for `app-release.aab` (SHA-256 `5FE605910671A4D99BEEC93FFD181F0ED986FBF1FCCC112546FB13F16655B31A`). The AAB is below the 50 MB budget by byte count. These are built from the current checkout artifacts and are not evidence of the baseline APK/AAB sizes at `8b4d85ad`; do not use them as such. Baseline AAB evidence is not available from this source build.

A single post-loop `dumpsys meminfo com.chimali` snapshot reported 122,012 kB PSS and 229,004 kB RSS after the app had only been launched; the UI was not driven through setup, Bluetooth service, or an RP ceremony. Thermal status was 0 when checked. This is a point-in-time idle/startup observation, not peak, session, or ceremony memory and not comparable to the fixed-window trace measurements already recorded for Moto. No ASUS startup memory series, LeakCanary session, or full-session profiler capture was made.

No Bluetooth HID request was run against this pre-feature baseline during the ASUS session, so no baseline request latency or end-to-end authentication latency is available. Whole-module/repository coverage evidence and existing threshold failures are documented under T008 in this packet; that snapshot predates subsequent test additions and is not a fresh baseline coverage run. The test tasks were not run as part of this measurement session.

**Disposition:** The user waived the Pixel 6a-class device requirement and designated ASUS as the reference device. This run supplies standard-package baseline release startup timings and a verified baseline APK size on that approved device. T003 remains open for baseline RP/HID latency, first-render, memory peak/session, baseline AAB (if required by the budget), and fresh coverage evidence. Keep all cold/hot/warm values labeled as ASUS release `com.chimali` launch measurements; do not infer SC-007 compliance from launch timing.
### T003 profiler semantics recheck (2026-10-15; corrected by transport timing change)

- The initial source review correctly found that the profiler logged pure processing time but incorrectly concluded that the current implementation ended timing before queued report handoff. That conclusion was superseded by the T073/T074 transport timing change recorded below.
- Current `BluetoothHidTransportImpl` now awaits completion of every final response report's `BluetoothHidDeviceWrapper.sendReport()` call before invoking `LatencyProfiler.end()`. The metric includes response queue wait and per-report pacing and records whether all reports were accepted by the local Android HID wrapper. It excludes user-interaction windows. This is a local API-handoff boundary only; it does not measure Bluetooth air time, remote-host receipt, or host processing.
- The existing `feature/fido2/src/macrobenchmark/.../CredentialListScrollBenchmark.kt` remains unwired and does not provide startup/HID measurements. No configured device memory-profiler workflow was found in the current feature setup. T003/T066/T071 remain open until comparable reference/current measurements include startup, binary, and peak/session memory evidence, and assertion derivation counts are established.

## Governance and traceability updates

- Constitution amendment 2.0.0 → 3.0.0 applied on 2026-10-14; ratification date retained as
  2026-02-19. Rationale and impact are recorded at the top of `.specify/memory/constitution.md`.
  `specs/055-migrate-hdk-draft/constitution-amendment.md` remains the historical proposal record.
- Current stable local profile requirement: BRD `NFR-SEC-041`; TRD `TR-AUTH-031`.
- Superseded IDs retained explicitly: BRD `FR-HID-024`, previous derivation text within
  `NFR-SEC-040`, and TRD `TR-UI-080`.
- Feature scope is the authorized clean installation of unreleased Chimali. This does not create a
  general schema evolution exemption.

## Security review gate (T005)

### Reviewer and scope

- **Reviewer**: Engineering AI agent (design review; not independent formal signoff)
- **Date**: 2026-10-14
- **Reviewed revision**: Branch `codex/055-migrate-hdk-draft`, working tree as of 2026-10-14
- **Materials reviewed**: All items listed in `security-review-packet.md` §Materials, plus current
  source code in `core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/`, the
  frozen fixture at `core/security/src/androidHostTest/resources/hdk/local-profile-v1.json`, and
  the cryptographic specifications RFC 5869, RFC 9180 §7.1.3, draft-dijkhuis-hdk-00 §§3/7/11,
  RFC 8032 §5.1.5, and FIPS 204 §§5.1/6.1.
- **Review type**: Engineering security design review per T005. This satisfies the design gate
  but does not constitute independent formal security signoff under ES-V.5.

### ES-V.5 classification

**Custom cryptographic protocol composition.** The end-to-end derivation chains four independently
published primitives (BIP39 PBKDF2, RFC 5869 HKDF, RFC 9180 DeriveKeyPair, draft-dijkhuis-hdk-00
§7 child relation) through application-defined glue: a fixed root salt, credentialInfo framing with
`0x00` delimiter and 2-byte length prefix, deterministic HKDF secret substituting for the draft's
ephemeral X25519 `shared_secret`, software parent provisioning from HKDF output, and independent
Ed25519/ML-DSA branches sharing the same PRK. No single published specification defines this
composition. Classification as custom protocol is mandatory.

**Consequence**: Formal independent review is required before production activation. The reviewer
packet is at `specs/055-migrate-hdk-draft/security-review-packet.md`; no report has been received.

### Disposition

**Conditional Accept** — the design is sound for implementation; formal independent review and
finding closure are required before production activation.

### Verification summary

All derivation stages were verified byte-by-byte against their governing specifications:

| Stage | Specification | Verdict |
| --- | --- | --- |
| Root extraction (HKDF-Extract) | RFC 5869 §2.2: salt/IKM ordering, 32-byte output | ✅ Correct |
| credentialInfo framing | Unambiguous TLV-like: label ‖ 0x00 ‖ I2OSP(len,2) ‖ C | ✅ Correct |
| Algorithm branch domain separation | Three distinct label prefixes; parent label distinct | ✅ Complete |
| RFC 9180 DeriveKeyPair | KEM 0x0010, suite ID, labels, counters 0–255, acceptance 0<sk<n | ✅ Correct |
| Child-key relation | SHA-256(secret ‖ ctx), (OS2IP mod (n-1))+1, multiplicative | ✅ Matches draft §7 |
| Ed25519 seed import | 32-byte HKDF output as RFC 8032 seed (internal clamping) | ✅ Correct |
| ML-DSA-65 seed import | BC 1.80 MLDSAPrivateKeySpec with 32-byte ξ | ✅ Correct |
| P-256 order n | 0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551 | ✅ Confirmed |
| Modulo bias | ~2⁻¹²⁸ statistical distance, accepted per draft §11 | ✅ Acceptable |

Authorization/lifecycle model, compromise boundaries, and conformance claims verified sound. No
byte-contract correction was required.

### Findings

Eight findings were identified (0 CRITICAL, 3 HIGH, 3 MEDIUM, 2 LOW):

| ID / severity | Impact | Recommendation | Closure evidence / status |
| --- | --- | --- | --- |
| SR-001 / HIGH | A direct or future lower-level caller could bypass domain validation and supply a short, oversized, or malformed credential identity to the KDF. | Enforce `C.size in 16..1023` at the cryptographic call boundary as well as the domain boundary; fail closed. | Open for T028: validate bounds in the byte-only derivation API and cover 15/16/1023/1024-byte cases. |
| SR-002 / HIGH | The retired sampler rejects counter 255, so it can fail when RFC 9180's last valid candidate is required. | Replace the old sampler with the RFC 9180 0–255 inclusive sequence and retain the acceptance rule `0 < sk < n`. | Open for T026; T010 authors the counter-255 acceptance and exhaustion cases. |
| SR-003 / HIGH | The current implementation can leave operation-owned secret arrays live after return or failure. | Erase every application-owned secret intermediate in `finally`; keep dependency/runtime copies and BigInteger limits accurately documented. | Open for T025–T030 and T065; T010/T011 author cleanup ownership assertions. Activation requires passing tests and the completed boundary audit. |
| SR-004 / MEDIUM | An argument-order mistake in the child hash preimage could go undetected if fixtures contained only final outputs. | Freeze and assert the exact preimage bytes alongside digest, factor, scalar, and public point. | Fixture `local-profile-v1.json` records child preimages; T011 asserts preimage and downstream values. Execution remains required by T050. |
| SR-005 / MEDIUM | Invalid undersized parent input could reach deterministic key generation and be interpreted inconsistently by an implementation. | Require parent IKM length of at least 32 bytes at the RFC 9180 parent derivation boundary. | Open for T026; add short-input rejection and exact 32-byte acceptance tests. |
| SR-006 / MEDIUM | A HID operation owner could outlive the CTAP request that authorized the ceremony. | Bind owner lifetime to the handler's `withTimeout` and document the existing safe timeout range/default. | Addressed in `contracts/credential-lifecycle.md`: MakeCredential/GetAssertion use 30,000–600,000 ms, default 120,000 ms; owner deadline must not outlive it. Implementation/test closure remains T035/T037 and T022/T051. |
| SR-007 / LOW | “Zero delimiter” could be read as a byte in the child preimage, changing the frozen derivation bytes. | State explicitly whether a separator byte is present. | Addressed in `contracts/local-key-derivation.md`: the local secret and context are directly concatenated with no separator byte; formulas and fixture bytes are unchanged. |
| SR-008 / LOW | The fixed-salt exception could be misapplied to secret inputs or constructions requiring fresh randomness. | Limit the exception to public domain-separation salt/context values in named, versioned profiles and preserve SecureRandom requirements. | Addressed in `.specify/memory/constitution.md` and `constitution-amendment.md`: fixed values are public domain-separation inputs only; the exception does not authorize secret treatment, nonce reuse, or replacing required fresh randomness. |

No finding requires changing the byte contract. SR-002 and SR-003 identify confirmed defects in
retired code; the replacement must not carry them forward. SR-006–SR-008 design-document findings
are corrected above. Remaining implementation findings are explicitly assigned to the relevant
tasks and remain activation conditions; this design review's conditional accept does not close them.

### Gate status

| Gate | Status | Condition |
| --- | --- | --- |
| **Design gate (T005)** | **PASS** | Implementation may proceed (T025–T028 and dependents) |
| **Activation gate** | **OPEN** | Requires: independent reviewer disposition, all SR findings closed, T065 boundary audit, fixture verification |

### Residual risks accepted

1. Root compromise is total (PRK → all branches). Documented; no false containment claim.
2. Modulo bias ~2⁻¹²⁸. Accepted per draft §11.
3. BigInteger not erasable on JVM. Confined to shortest scope; cannot be eliminated.
4. Software parent is exportable. Keystore protects ciphertext, not the signing scalar.
5. No account discovery from mnemonic alone. Recovery requires credential metadata.
6. draft-dijkhuis-hdk-00 is an Internet-Draft. Pinned at version 00.

On 2026-10-14, checked the current [IETF Datatracker text for draft-dijkhuis-hdk-00](https://datatracker.ietf.org/doc/draft-dijkhuis-hdk/00/). It is an active Internet-Draft (last updated 2026-08-28; expires 2027-03-02), not an IETF standard. Sections 4–7 assume an existing secure-area P-256 parent and use a fresh per-request X25519 `shared_secret` as `bk` in `H(bk || ctx)`, where `ctx` binds the parent public key and 32-bit credential index. Chimali's proposed local composition substitutes deterministic RFC 5869-derived per-credential secret material, binds the full credential ID in that local HKDF, and deterministically provisions a software parent through RFC 9180. The local root, parent provisioning, identifier binding, and alternate signature branches are not defined by the draft. This source check confirms the custom-protocol classification and makes the exact deviation a mandatory reviewer question; it does not change the proposed bytes or constitute approval. The review packet and T053 matrix now state this distinction explicitly.

## T012 — Mutable mnemonic generator and boundary tests

`Bip39MasterSeedGenerator` generates an owned 24-word `CharArray` and derives a 64-byte
PBKDF2-HMAC-SHA512 seed using the fixed empty passphrase. It normalizes into a mutable
`StringBuilder`, validates English-word membership and checksum, converts the normalized phrase to
owned ASCII bytes, and clears the phrase builder, index/entropy/checksum/password/salt/PBKDF2
working arrays on terminal paths. The host-test normalizer seam is internal; production
construction uses Android ICU NFKD. The immutable English BIP39 word list is public profile data;
private mnemonic text is not retained in a `String` or `List<String>` by this generator.

The configured host tests cover owned independent 24-word phrases, the 64-byte published
all-zero-entropy BIP39 seed independently reproduced with Python `hashlib.pbkdf2_hmac`, input
preservation, compatibility normalization to the same UTF-8 seed, invalid count/word/checksum/
spacing rejection, returned-seed ownership, entropy/checksum conversion, and cleanup of the
normalized mutable phrase when an injected normalizer fails. The selected command
`./gradlew.bat :core:security:testAndroidHostTest --tests
'com.chimali.core.security.impl.Bip39MasterSeedGeneratorTest' --console=plain` passed on
2026-10-14: **12 tests, 0 failures** (XML:
`core/security/build/test-results/testAndroidHostTest/TEST-com.chimali.core.security.impl.Bip39MasterSeedGeneratorTest.xml`).
Because this API is synchronous and has no suspension point, cancellation is handled by its callers;
the generator erases its own working state on success and failure. T012 is complete. This evidence
does not claim forensic erasure of JVM/provider-internal copies.

## Documentation review progress (T053/T057/T059)

Created `specs/055-migrate-hdk-draft/documentation-review.md` as the current-claims acceptance matrix. It records the constitution/BRD/TRD target and separates source-aligned edits from remaining KDoc and implementation review. Updated the contract, constitution, amendment record, spec, plan, research, BRD and TRD to identify the local scalar-map adaptation and its changed inputs accurately. The completed T005 design review accepted the documented bytes for implementation; independent formal review of the implemented system remains a pre-activation gate. Updated `docs/resources.md` with the active `draft-dijkhuis-hdk-00` record and official RFC 5869, RFC 9180, RFC 8032, NIST FIPS 204, and BIP 39 references; the page distinguishes local use from issuance interoperability and marks draft-06 historical. Reviewed `docs/backlog/openid4vci-hdk-issuance-investigation.md`: it retains concrete issuer, parent-assurance, lifecycle, recovery, privacy, interoperability, and evidence questions with separate-feature completion criteria. Added direct links from the BRD and resources page. A relative-link check passed for updated Markdown pages. These documentation tasks do not imply implementation conformance; T054/T055/T056/T061/T062 remain open for later code-aligned review.

Updated both recovery/comparison research documents: current sections now require mnemonic plus retained credential metadata, state full-ID/profile binding and independent signature branches, distinguish the software parent from non-exportable hardware, and state that labels do not contain common-root compromise. Legacy path, deterministic-ID recommendation, and comparative-superiority claims are clearly labeled historical or withdrawn; the external DiceKeys comparison remains background rather than an interoperability claim. T058 is complete as a documentation update; T062 remains the final source-aligned audit.

## Credential identity and descriptor implementation evidence

- `CredentialId` now accepts only valid Base64URL identifiers whose decoded length is 16–1023
  bytes, canonicalizes optional padding to the unpadded form, and validates serialized values.
  Callers receive the complete decoded bytes; no shortened mapping is introduced.
- Added immutable `CredentialKeyDescriptor` public metadata with explicit current profiles for
  COSE ES256 (-7), EdDSA (-8), and ML-DSA-65 (-49). Unknown algorithms and mismatched profiles
  throw `IllegalArgumentException`; there is no default profile or ES256 fallback.
- Added source-retained `@Req` with stable BRD/TRD ID usage and the minimal `:core:common`
  dependency in `:core:security` commonMain.
- `:core:security:compileAndroidMain` succeeded on 2026-10-14. Its first concurrent invocation
  collided with another Gradle daemon's Kotlin incremental cache; both daemons were stopped and
  a single no-configuration-cache retry succeeded. This was a tool concurrency issue, not a
  source failure.
- Initial `:core:domain:testAndroidHostTest` run found two existing repository tests passing
  short human-readable IDs into the now-strict `CredentialId`; the shared test factory and those
  callers were updated to use contract-valid synthetic IDs. The rerun succeeded on 2026-10-14:
  25 tests completed, 0 failures, including the credential ID boundary/canonicalization and
  descriptor profile tests. Report:
  `core/domain/build/reports/tests/testAndroidHostTest/index.html`.

## Current derivation primitives and mutable mnemonic boundary (T012, T025–T029, T047–T048)

- Added RFC 5869 HKDF-SHA256 extraction/expansion with the fixed wallet root salt, P-256 parent
  label, all three credential labels, and exact zero-delimiter/uint16/full-ID framing. The API
  enforces a 64-byte BIP39 seed for root extraction and 16–1023-byte IDs for credential framing;
  borrowed inputs are preserved and operation-owned block/intermediate arrays are erased.
- Added exact RFC 9180 §7.1.3 DHKEM(P-256, HKDF-SHA256) deterministic parent generation: KEM 0x0010,
  `HPKE-v1`, `dkp_prk`, `candidate`, 0–255 inclusive, and the `0 < sk < n` rule. Production
  derivation from PRK erases the generated parent IKM; candidate buffers and the labeled PRK are
  erased on all exits. The RFC A.3.1 published key pair matches the configured host test.
- Tightened `P256Group` to strict uncompressed 65-byte SEC1 points and 32-byte scalar encodings in
  `1..n-1`; removed obsolete random-parent/ECDH and additive helpers. Added the local child relation
  with the frozen preimage order, UInt32 index encoding, nonzero factor formula, pair correspondence
  validation, borrowed-input preservation and `finally` cleanup. The nonzero factor formula
  algebraically guarantees a nonzero child scalar; the implementation records this rationale in
  KDoc.
- Replaced immutable phrase/list processing with an owned 24-word `CharArray` and full 64-byte
  empty-passphrase seed. English word validation/checksum parsing avoids private mnemonic Strings;
  ICU normalizes into a mutable builder whose current contents are overwritten before release.
  Entropy, indices, checksum, normalized characters, UTF-8 bytes, PBKDF2 block and rolling U buffers
  are cleared. The public English dictionary remains immutable. Host tests inject a deterministic
  normalizer because Android ICU APIs are stubbed in the Android host-test runtime; the actual
  Android ICU invocation remains to be exercised in T051 device verification.
- Removed `HdkEcdhP256`, `HashToScalar`, `MultiplicativeBlinding`, `DhKem`, their old-only tests, and
  the stale migration smoke test. The security KMP production source now compiles against the new
  common byte-only API.
- Verification: `:core:security:compileAndroidMain` succeeded. Focused configured host run covered
  `HkdfSha256Test` (5), `P256ParentKeyDerivationTest` (3), `P256GroupTest` (9), `HdkP256Test` (4),
  and `Bip39MasterSeedGeneratorTest` (11): **32 tests, 0 failures**. Report:
  `core/security/build/reports/tests/testAndroidHostTest/index.html`. An earlier run first exposed
  the expected stale API dependency and then the Android ICU host stub; after removing the retired
  API and adding the test-only normalization seam, the final run passed. The invalid-checksum test
  fixture was corrected because its original last word (`art`) made it a valid published phrase.
- This is primitive-level evidence only. Provider integration, configured full-module tests,
  Android ICU/device behavior, session lifecycle, ceremony/signature flows, coverage and independent
  formal security review remain open under their assigned tasks and activation gate.

## Root consumers and provider migration progress (T030–T033)

- Replaced the wallet provider's legacy key model with encrypted Proto DataStore storage, mutable
  phrase decode/encode, full 64-byte BIP39 derivation, 32-byte HKDF root PRK, and RFC 9180 parent
  pair. Cache publication is atomic under one lock after searchable-metadata key provisioning;
  getters return independent owned copies. Import consumes/erases the caller's mnemonic, fences
  and clears old caches before persisting replacement, and does not eagerly reconstruct the root;
  the next authorized owner reconstructs from the encrypted phrase. Removed the
  EncryptedSharedPreferences wallet fallback/migration and the PQ seed cache. Added monotonically
  increasing generation fencing, active-operation owner scopes and `clearSession()`. Owners are
  tracked only while active; a global fence cancels every current owner, and last-owner release
  erases root/parent/metadata-key caches. Initialization publication is synchronized with clearing
  so stale bootstrap cannot republish metadata keys or root material. T030 is complete: encrypted
  DataStore bootstrap/import, atomic cache publication/copying, owner-bounded cache destruction,
  fallback/PQ-cache removal and the provider boundary cases are implemented and covered by the
  10-test provider suite. Platform memory-callback integration and device evidence remain under
  T035/T051.
- T013 provider tests pass **10 tests, 0 failures** in the configured
  `WalletMasterSeedProviderTest`: clean bootstrap, deterministic reconstruction, independent
  root/parent copies, import consumption/replacement, no partial publication after derivation
  failure, bootstrap generation fencing, lazy reinitialization, overlapping owners and last-owner
  erasure, selective foreground cancellation while an admitted authenticator owner continues,
  replacement cancellation with a new root derived from the imported wallet, and a structural
  assertion for exactly one PRK cache, one parent-pair cache and no PQ cache. The separate
  `Fido2CryptoServiceTest` generation-flip case verifies stale synchronous signing output is
  rejected after a fence. `clearSession()` is the provider's common fence exercised by
  trim/replacement; Android device testing of each platform memory callback remains open under
  T035/T051.
- T037 generation-fence progress: public-key generation and signing snapshot the provider
  generation at admission, check it before cryptographic work and after synchronous provider/JCA
  work, and erase produced output if replacement/clear fenced that generation. The focused
  `Fido2CryptoServiceTest` suite passed **8 tests, 0 failures**, including a generation flip while
  signing. CTAP MakeCredential/GetAssertion now execute under authenticator owner scopes, and
  stale synchronous outputs remain fenced. The full ceremony deadline and platform-disconnect
  evidence remain open under T035/T037.
- Reworked `EventStoreKeyProviderImpl` to retain its existing aggregate-label HMAC-SHA512 KDF over
  a provider-owned PRK copy and erase that root, label bytes and full HMAC output on all exits.
  The focused EventStore host suite passed (3 tests, 0 failures); the updated ownership assertion
  computes its expected HMAC before the production provider erases its supplied owned copy.
- Updated `EncryptedDriverFactory.deriveDatabaseKey` to encode lowercase ASCII hex directly into
  mutable bytes without an immutable hex `String`, erase PBKDF2 password/block/rolling U buffers,
  and require the provider's 32-byte root PRK. `createDriver` erases its owned root copy after all
  open/retry paths. `:core:database:compileReleaseKotlin` and focused consumer tests succeeded;
  `EncryptedDriverFactoryTest` passed 5/5 on 2026-10-14.
- Updated `HmacSecretProcessor` to preserve its existing simplified HMAC-SHA256 policy while
  erasing the provider root copy, credential input/secret, salt copies and HMAC outputs, and to
  preserve caller-owned `saltEnc` bytes. `HmacSecretProcessorZeroingTest` passed 2/2, including
  independent expected-output and caller-input preservation checks. Fido2Initializer no longer
  derives the wallet root or opens the encrypted FIDO2 database during app startup; the bounded
  provider initialization and metadata migration now require a later consumer request.

### Operation owner/lifecycle implementation progress (T013/T034/T035)

- Added `MasterSeedProvider.withSessionOwner` and owner kinds `BOUNDED`, `FOREGROUND`, and
  `AUTHENTICATOR`. The production provider binds getters to an active coroutine owner, tracks only
  live owners, cancels owner jobs on generation fencing, and releases the shared cache after the
  last current owner completes. Foreground cancellation leaves an overlapping authenticator owner
  and its generation intact; the provider host test verifies this directly.
- `MainActivity.onStop` cancels only foreground owners. `DevToolsViewModel` holds the foreground
  owner for the complete mnemonic reveal and releases/wipes it on hide, replacement, disposal,
  or provider fencing. Its configured host suite passed **14 tests, 0 failures**, including the
  owner-held-until-hide assertion. Recovery import registers its bounded foreground owner only
  after fencing old owners and persists without eagerly rebuilding the root.
- Event-store, encrypted-database, and hmac-secret root consumers now run inside bounded owners;
  focused suites passed **3/3**, **5/5**, and **2/2** respectively. `:app:compileDebugKotlin`,
  `:core:database:compileDebugKotlin`, `:feature:fido2:compileAndroidMain`, and
  `:feature:fido2:compileAndroidDeviceTest` succeeded after the owner API change.
- CTAPHID CBOR work now runs in a per-CID job so the report receiver can observe CTAPHID_CANCEL.
  Disconnect and Advertising/Idle state cancel and join active request jobs; MakeCredential and
  GetAssertion hold authenticator owners through their handlers. Trim/replacement cancels provider
  owners, stale responses are discarded, and foreground stop does not cancel HID owners. The
  request-wide timeout bound, actual transport/disconnect tests, all-three-memory-level device
  matrix, and platform cache/GC observations remain open under T035/T051. T013 is complete based
  on the production-provider and crypto-service evidence above; T035 remains open.

## FIDO2 API migration checkpoint (T014, T045–T046, T049)

- Replaced the old alias/Keystore crypto-service tests with current descriptor-based tests using
  frozen independent P-256 and Ed25519 public outputs, an ML-DSA public-key digest, full-ID
  collision fixtures, signature verification, stored-key mismatch rejection, and owned copies
  returned by the provider fake. After updating the other host fakes, focused execution passed;
  T014 is complete.
- Updated the selected assertion path to construct a validated current descriptor, load the
  selected registration's stored public key, and require the derived public key to match before
  returning a signature. It does not mutate registration on mismatch. Candidate summaries still
  lack the required profile and stored-key metadata, so T038/T045 remain open.
- Removed the fictional credential-key existence/deletion checks and the corrupted-key repair
  worker and its scheduling. Repository deletion now changes credential metadata only. The
  production FIDO2 source compiled successfully with
  `:feature:fido2:compileAndroidMain` on 2026-10-14.
- The configured host-test compilation `:feature:fido2:compileAndroidHostTest` currently fails on
  stale test sources still importing the retired deterministic RNG, alias result, worker, and
  hierarchy APIs, plus legacy model/UI test fakes. These are task-owned test migrations, not a
  production-source compile failure. The test fakes were then migrated to the current APIs and
  the configured host-test compilation succeeded on 2026-10-14.
- The focused configured command selected `WalletMasterSeedProviderTest`,
  `Fido2CryptoServiceTest`, `PostQuantumCryptoTest`, and `RegisterCredentialUseCaseTest`; all
  **46 tests passed with 0 failures** across the final runs. Counts were 4, 7, 9 and 26
  respectively. This covers current PRK/parent copies, the independent frozen P-256/Ed25519/ML-DSA
  public outputs, full-ID collision behavior, signature verification, profile/key mismatch
  rejection, ML-DSA NIST key-generation vectors, and registration use-case behavior. Report:
  `feature/fido2/build/reports/tests/testAndroidHostTest/index.html`.
- `:feature:fido2:compileAndroidMain` and `:feature:fido2:compileAndroidHostTest` succeeded after
  adding the explicit shared HdkManager binding. The module's existing FIDO2 component scan
  consumes that shared binding. Current metadata round-trip/transaction behavior remains open;
  these results do not close T017/T035/T038–T045 or authorize activation. T013's provider
  ownership, overlap, replacement, fencing and cache-boundary tests are complete as recorded above.
- Removed the startup root initialization and encrypted-database opening from `Fido2Initializer`;
  provider initialization is now reached only by a consumer request, not by application startup or
  an idle HID connection. `:feature:fido2:compileAndroidMain` and
  `:feature:fido2:compileAndroidHostTest` both succeeded afterward. T035 remains open for the
  complete request timeout, transport/device lifecycle and memory-pressure matrix evidence.

### Multi-algorithm ceremony test progress (T018)

- Added use-case coverage that registration passes the same explicit algorithm/profile descriptor
  to public-key generation and self-attestation signing for ES256/P-256, EdDSA/Ed25519 and
  ML-DSA-65. Added assertion coverage that the selected credential's algorithm selects its
  matching current profile for all three branches and that signing is invoked with the matching
  descriptor. This complements the crypto-service suite's independent key/signature checks; these
  use-case tests validate ceremony routing.
- On 2026-10-14, the configured focused command
  `:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.domain.usecase.RegisterCredentialUseCaseTest' --tests 'com.chimali.fido2.domain.usecase.GetAssertionUseCaseTest'`
  passed **34 tests, 0 failures** (RegisterCredentialUseCaseTest 27 including nested suites;
  GetAssertionUseCaseTest 7). The first run exposed missing full-credential metadata stubs in the
  assertion fixtures after the current selected-key lookup was introduced; the fixtures were
  updated, and the final run passed.
- T018 remains open: T038–T042 now persist descriptor/profile and stored-key metadata, but the
  combined ceremony path still needs malformed-record, corrupt stored-key, cancellation,
  ceremony-policy and independent signature verification evidence.

### Credential identity, schema, event, and projection evidence (T038–T042)

- `PasskeyCredential` now requires a current algorithm-matched derivation profile, exposes a fresh
  copy of the persisted public-key encoding, and no longer carries private-key alias identity.
  `CredentialSummary` requires the profile and stored-key bytes. Serialization tests assert both
  fields survive a round trip; model tests reject an algorithm/profile mismatch and verify the
  stored-key getter does not expose its backing bytes.
- Passkey registration commands/events/state carry the explicit algorithm and profile. The decider
  rejects invalid current pairs and preserves them through event replay and snapshot hydration.
  Fresh SQLDelight schema now has `derivation_profile TEXT NOT NULL`, explicit insert fields, no
  `private_key_alias`, and no public-key repair query; historical migration files remain untouched.
- The DAO maps the persisted profile and key encoding without derivation and exposes one narrow
  `replaceCredentialsForRpAndUser` operation. That operation wraps matching-row deletion and
  insertion in `Fido2Database.transaction { }` and checks coroutine cancellation before commit.
  The repository appends registration events first, runs the atomic projection replacement second,
  returns success only after both phases succeed, returns a persistence error if the projection
  phase fails, and propagates cancellation. The existing event-store append implementation uses an
  explicit database transaction.
- Added in-memory SQLDelight checks that an insertion-preparation exception and cancellation both
  roll back prior projection deletions and leave the original row intact. Repository tests check
  append-before-projection ordering and projection-failure reporting. Replay/snapshot tests verify
  stored public key and profile identity survive reconstruction.
- On 2026-10-14, configured focused checks passed with zero failures: `PasskeyCredentialTest` (22),
  `PasskeyCredentialDaoTest` (4), `CredentialRepositoryImplTest` (12),
  `CredentialRepositorySearchableMetadataTest` (11), `DatabaseSchemaTest` (7),
  `PasskeyAggregateServiceImplTest` (5), `PasskeyDeciderTest` (10), and
  `DomainModelSerializationTest` (3). Total: **74 tests, 0 failures**. Main and host-test
  compilation also passed for the affected domain and FIDO2 modules. Gradle report:
  `feature/fido2/build/reports/tests/testAndroidHostTest/index.html`.
- These tasks close implementation-model/schema/event/projection requirements only. The T018
  combined ceremony evidence, device validation, T065 boundary audit, and independent formal
  security review remain separate open gates; this evidence does not authorize production activation.

### Crypto-service routing and selected assertion evidence (T037/T045)

- The production crypto service routes only explicit algorithm/profile descriptors to the full-ID
  HKDF P-256, Ed25519, and ML-DSA branches. It clears operation-owned PRK, parent private copy,
  credential-ID copy, local secret/seed, factor, child scalar, and private encoding buffers in
  `finally`; generation fencing is checked before derivation and before publishing generated keys
  or signatures. Unsupported/mismatched pairs fail closed. The existing CTAP HID make-credential
  and get-assertion paths each hold an `AUTHENTICATOR` owner over their bounded operation.
- Assertion selection now consumes only `CredentialSummary`. It builds the descriptor from the
  persisted algorithm/profile and passes the saved public-key encoding to signing. It no longer
  hydrates a full credential before signing. The crypto service normalizes both keys through their
  algorithm's public-key decoder and compares the canonical encodings before producing a signature.
  Invalid profile metadata stops before signing or sign-counter mutation. Existing authorization,
  selection, counter, response, and cancellation handling remain in place.
- On 2026-10-14, focused configured Android host checks passed with zero failures:
  `Fido2CryptoServiceTest` (8), `GetAssertionUseCaseTest` (8), and
  `RegisterCredentialUseCaseTest` (29), totaling **45 tests, 0 failures**. The crypto suite checks
  frozen P-256 and Ed25519 outputs/signatures, ML-DSA output, full-ID collision resistance,
  mismatched stored-key rejection and generation fencing. Assertion tests verify all three profile
  routes, no credential hydration, stored-key forwarding, and malformed-profile failure before
  counter read/signing/update. Malformed stored profile rows return a typed validation error.
  Registration tests retain explicit descriptor/public-output routing and assert signing errors or
  cancellation prevent profile persistence.
  Report: `feature/fido2/build/reports/tests/testAndroidHostTest/index.html`.
- These host results do not replace the connected-device lifecycle/restart suite (T051), the full
  configured verification set (T050), the US1 acceptance matrix (T052), or independent formal
  security review before activation.

### Event-store and snapshot buffer ownership (T043)

- Event append, event replay, snapshot save, and snapshot restore now keep provider-returned keys
  and owned plaintext/ciphertext byte arrays in `try/finally` cleanup. Event append checks
  cancellation within its existing database transaction and propagates it; failed/cancelled
  appends roll back. The implementations continue to use the existing FIDO2 database and event
  store key provider.
- On 2026-10-14, `PasskeyEventStorageOwnershipTest` passed **4 tests, 0 failures**. In-memory
  SQLDelight verified event replay and snapshot restoration preserve complete current identity,
  owned key and plaintext buffers are zeroed after use, and event append failure/cancellation leave
  no partial event row. `PasskeyAggregateServiceImplTest` also passed **5 tests, 0 failures** for
  replay/snapshot identity and cadence. Report:
  `feature/fido2/build/reports/tests/testAndroidHostTest/index.html`.
- Kotlin serialization still creates a transient immutable JSON `String` at the library boundary;
  the application-owned UTF-8 byte array is erased immediately after encryption/decryption. Event
  content is registered public metadata and contains no private signing key or recovery secret.

### Registration persistence gate (T044)

- Registration passes one descriptor to public-key generation and self-attestation, adapts the
  returned public key without rederiving it, and completes required self-attestation before
  repository persistence. A typed signing error is returned; it no longer degrades into a
  successful `none` attestation. Cancellation propagates before projection/event persistence.
- The configured `RegisterCredentialUseCaseTest` suite passed **29 tests, 0 failures** on
  2026-10-14. New cases assert signing failure and cancellation do not persist a current profile;
  existing multi-algorithm cases verify descriptor pairing and public-output reuse.

### Multi-algorithm registration and assertion ceremonies (T018)

- `MultiAlgorithmIntegrationTest` now exercises real P-256, Ed25519, and ML-DSA-65 registration
  and assertion paths. It verifies the persisted algorithm/profile pair and independently verifies
  both packed self-attestation and assertion signatures against the registered public key using
  each algorithm's JCA verification operation. Registration continues to use the generated
  public key and the same descriptor for its required self-attestation.
- Assertion tests reject malformed selected profile metadata before reading or updating the
  counter, surface signing failure without updating it, propagate signing cancellation without a
  counter mutation, and preserve the metadata-only selection path. The crypto-service suite
  rejects a selected credential whose stored public key does not match the derived key. Existing
  tests retain the current ceremony authorization, selection, and counter behavior.
- On 2026-10-14, configured host verification passed **49 tests, 0 failures** across
  `RegisterCredentialUseCaseTest` (29), `GetAssertionUseCaseTest` (9),
  `MultiAlgorithmIntegrationTest` (3), and `Fido2CryptoServiceTest` (8). Report:
  `feature/fido2/build/reports/tests/testAndroidHostTest/index.html`.

### Event and credential-projection transaction boundary (T017)

- The DAO replacement uses one `Fido2Database.transaction` for deletion of prior RP/user
  projections and insertion of the new row. In-memory SQLDelight tests inject insertion-preparation
  failure and cancellation after the deletion phase; both leave the original row intact. A summary
  query test checks algorithm, profile, full credential ID, and stored public-key bytes without
  calling the public-key decoder or hydrating a private-key-bearing credential.
- The repository captures and checks the registration event command's credential bytes, public key,
  algorithm, and profile. It verifies event append occurs before projection replacement, maps
  projection failure to a typed persistence error, and propagates projection cancellation. Event
  replay/snapshot tests separately verify the committed event identity is retained during
  reconstruction. No repair or overwrite path is present.
- On 2026-10-14 the configured focused host suites passed: `PasskeyCredentialDaoTest` (4),
  `CredentialRepositoryImplTest` (13), and `CredentialRepositorySearchableMetadataTest` (11),
  including the in-memory rollback cases. `PasskeyEventStorageOwnershipTest` (4) and
  `PasskeyAggregateServiceImplTest` (5) also passed for append/replay/snapshot ownership and
  identity. The configured complete result is recorded under T050.

### Configured shared and host verification (T050)

- On 2026-10-14, after adding the ViewModel owner/cancellation regression tests, the configured command
  `:core:security:testAndroidHostTest :core:domain:testAndroidHostTest :core:database:testDebugUnitTest :feature:fido2:testAndroidHostTest`
  completed successfully. The four discovered modules ran **105 test suites / 557 test cases**:
  `core:security` 9 suites / 43 tests, `core:domain` 6 / 29,
  `core:database:testDebugUnitTest` 2 / 9, and `feature:fido2` 88 / 476. All had zero failures,
  errors, or skipped tests. The per-suite names and test counts are in the XML reports under
  `core/security/build/test-results/testAndroidHostTest/`,
  `core/domain/build/test-results/testAndroidHostTest/`,
  `core/database/build/test-results/testDebugUnitTest/`, and
  `feature/fido2/build/test-results/testAndroidHostTest/`; HTML summaries are under each module's
  `build/reports/tests/` directory, including
  `feature/fido2/build/reports/tests/testAndroidHostTest/index.html`.
- Red-to-green: the first full FIDO2 host run discovered 470 tests and exposed 25 failures in
  `CborCodecTest` and `PqcSigningTest` (stale 64-byte ML-DSA seed fixtures),
  `GetUserConsentUseCaseTest` (an expectation using the pre-canonical credential label),
  `ManagementIntegrationTest` (short/noncanonical IDs and invalid MockK value-class matchers), and
  `CredentialManagementViewModelTest` (the same invalid matcher fixture). Updated only the affected
  test fixtures to use the 32-byte ML-DSA seed and valid canonical synthetic credential IDs; the
  five focused classes then passed, and the complete configured command passed with the counts
  above. No production-generated expected outputs were introduced.
- T005's recorded design-review evidence remains complete. The independent formal security review
  is still a separate production-activation gate and is not represented as passed here.

### Connected-device discovery (T051 progress; not complete)

- The registered connected task is `:feature:fido2:connectedAndroidDeviceTest`. Its first APK
  packaging attempt found duplicate JUnit 5 license resources pulled in by the unused
  `mockk-android` device-test dependency. Removing that unused dependency resolved packaging; the
  task then successfully compiled and packaged the Android device-test APK.
- The connected task could not execute tests: Gradle reported `DeviceException: No connected
  devices!`; `adb` is also not available on this host's command path. The moved
  `DevelopmentToolsScreenTest.kt` compiled, but no device test results were discovered or run. The
  `LocalProfileLifecycleTest.kt` is now authored and compiles, but has not run. T021/T022/T051
  therefore remain open; no real Keystore, encrypted-store, lifecycle, restart, or Compose runtime
  result is claimed.

### Retired API, KDoc, and current requirement audit (T054–T056 complete; final acceptance pending)

- Re-ran the removal-pattern search from `quickstart.md` over active Kotlin and SQLDelight sources.
  No active matches remain for `HdkEcdhP256`, `HashToScalar`, `MultiplicativeBlinding`, remote
  derivation/acceptance methods, `deriveSalt`, `getPqChildSeed`, `DeterministicSecureRandom`, the
  repair worker, public-key repair SQL, `private_key_alias`, or `credentialAlias`. The intentional
  historical `11.sqm` migration still names the removed `private_key_alias` column so an old
  database migration remains historically accurate; it is not an active schema or caller.
- Current retained profile primitives have demonstrated callers: `WalletMasterSeedProvider`
  invokes `HkdfSha256` and `P256ParentKeyDerivation`; `Fido2CryptoService` invokes `HkdfSha256`,
  `HdkManager`/`HdkP256`, `P256Group`, and `PostQuantumCrypto`; `SecurityModule` binds the one local
  `HdkP256` implementation. Their configured host tests ran in T050's 557-test green result.
- The source/documentation review corrected the stale assertion latency/unconditional-derivation
  claims, the full-credential query's old lazy-derivation comment, the false AndroidKeyStore
  signing/TEE warm-up description and its unused warm-up key path, and a nearby stale storage
  service description. The asynchronous biometric-availability cache pre-warm is explicitly
  best-effort and makes no signing-hardware or latency guarantee.
- Current BRD NFR-SEC-010/020/030 text no longer promises hardware offload for every operation or
  AndroidKeyStore custody for software-derived signing keys. TRD no longer claims ML-KEM support
  for future remote provisioning after the T062 correction below; that capability remains outside
  this profile and within the separate issuance investigation. Stable requirement IDs and
  supersession history are retained.
- `CredentialEncryptionService` and its `CredentialStorageService` helper are not callers of the
  local HDK profile or current searchable-metadata service; their own legacy AES-CBC/AES-GCM
  behavior is separate from credential signing. This audit did not treat them as replacement
  derivation primitives or delete unrelated storage APIs. `EncryptedSharedPreferences` remains
  only in the independent `max_credential_count` settings migration; SQLDelight `PairedDevice.alias`
  is a user-facing device label, not a credential key alias.
- At this point in the chronological audit, T054–T056 were awaiting final source-aligned review;
  the 2026-10-14 closure update at the end of this file supersedes that interim status. Their
  completion does not close T052, device-only lifecycle, full memory/security, independent formal
  review, or production-activation gates.
- T062's audit found that TRD §5.1 still said ML-KEM-768 was reserved for future remote provisioning, despite earlier progress wording. Removed that unsupported capability statement and linked the separate issuance investigation instead. BRD NFR-SEC-010 now points post-quantum signature support only to NFR-SEC-041. The constitution review confirmed version 3.0.0, the 2026-10-14 amendment date, preserved ratification date 2026-02-19, feature-specific clean-install scope, and current BRD/TRD requirement homes; old HDK mandate names occur only in the amendment impact/history record.
- The broader T062 claims scan also found generic TEE/StrongBox and unconditional hardware-offload wording in the BRD executive summary and AES evaluation. These now distinguish configured platform key protection from software-derived signing keys and qualify hardware backing/acceleration as device/provider-specific and subject to verification.
- Updated the BRD mission to describe a software virtual authenticator rather than a hardware security key, and marked the March 2026 Windows integration analysis as historical because it describes an earlier permanent-KeyStore-key signing path.
- Added the dated changelog `docs/changelogs/2026-10-14-local-hdk-profile.md`, recording the breaking clean-install scope, retired implementation debt, constitution version, 557-test host evidence, software-key limitations, and open device/coverage/security activation gates without claiming deployment or formal signoff.
- After these KDoc/comment and initializer/transport edits, `:feature:fido2:compileAndroidMain`
  completed successfully on 2026-10-14. The task discovery showed `compileAndroidMain` is the
  configured production compilation name; `compileDebugKotlinAndroid` is not a task in this KMP
  module. This compile does not replace the later T063 full build/lint gate.
- A final T055 KDoc scan found the registration service still claimed “Key-pair generation & KeyStore storage.” Updated `Fido2ServiceImpl` to describe descriptor-selected local derivation/software signing under existing authorization and metadata-only persistence. The focused Android production/device-test compilation below verifies this wording-adjacent source change alongside the UI input policy.

### Device test authoring progress (T021/T022/T051; execution still pending)

- `DevelopmentToolsScreenTest.kt` remains in the configured `androidDeviceTest` source set. Added an Android IME/autofill boundary test that checks no personalized learning/extract UI/fullscreen flags, disabled autocorrect/autocomplete, empty initial surrounding text, and autofill exclusion for manual recovery entry.
- Added `LocalProfileLifecycleTest.kt` using the production `WalletMasterSeedProvider`, Android `EncryptionWrapper`, actual Android Keystore, Proto DataStore factory, and `EncryptedDriverFactory`/SQLite3MC. It checks empty-store bootstrap, persisted encrypted recovery material, database integrity/non-plaintext header, generation clearing, and reconstruction of the same root. Further overlap/trim/replacement/ceremony/rollback cases in T022 are not yet represented by this initial test.
- Task discovery confirmed `:feature:fido2:compileAndroidDeviceTest` is the module's compile task and `:feature:fido2:connectedAndroidDeviceTest` is its connected runner. The mistaken guessed task `compileAndroidDeviceTestKotlinAndroid` does not exist. `:feature:fido2:compileAndroidDeviceTest` completed successfully on 2026-10-14; only the existing Compose rule deprecation warning was reported. At that point no device was connected; the later emulator execution recorded below supersedes that availability statement.

### T063 validation status (2026-10-14; partial, gates remain open)

- `:core:common:compileAndroidMain`, `:core:security:compileAndroidMain`, `:core:domain:compileAndroidMain`, `:core:database:compileDebugKotlin`, `:feature:fido2:compileAndroidMain`, `:feature:fido2:compileAndroidDeviceTest`, and `:app:compileDebugKotlin` all completed successfully in one Gradle invocation. This covers the changed common/database/app wiring and the new production/device-test source.
- The full configured host command `:core:common:testAndroidHostTest :core:security:testAndroidHostTest :core:domain:testAndroidHostTest :core:database:testDebugUnitTest :feature:fido2:testAndroidHostTest :app:testDebugUnitTest --console=plain` passed after the latest cleanup and UI policy changes. XML totals are common 10, security 43, domain 29, database 9, FIDO2 476, app 5: 572 tests with zero failures/errors/skips. Reports are in each module's `build/test-results/` folder; database uses `testDebugUnitTest`, and app uses `testShrunkDebugUnitTest`.
- The affected modules' Ktlint checks passed in a combined invocation, as did `:feature:fido2:compileAndroidDeviceTest`; the security KMP build now disables iOS placeholder compilation on Windows consistently with the other KMP modules. Detekt still fails on eight `TooGenericExceptionCaught` findings at intentionally broad exception-to-typed-error boundaries in `Fido2CryptoService`, `CredentialRepositoryImpl`, `PasskeyEventStoreRepositoryImpl`, and `PasskeySnapshotRepositoryImpl`. They remain visible; no suppression or rule relaxation was added.
- `tools/local-ci.ps1 -NoFix` failed at Detekt with the same broad-boundary findings. Its Ktlint phase passed. Connected-device execution remains unavailable; coverage floors remain open. T063 is not complete.
- Re-ran `tools/local-ci.ps1 -NoFix` after the latest fixes on 2026-10-14. The repository-wide
  Ktlint phase passed; Detekt failed on the same eight broad exception-boundary findings listed
  above, all in `feature:fido2`. The script stopped at Detekt. T068 remains open.
- Reconciled `specs/055-migrate-hdk-draft/quickstart.md` with the actual host suite task names
  (`core:common`, `core:security`, `core:domain`, `core:database`, `feature:fido2`, and `app`),
  compile task names, connected-device task, static-check modules, and report directories. The
  procedure now points to this evidence for actual host/coverage outcomes. T067 remains open
  because its device-only clean-install/restart and interoperability scenarios were not executed.

### T063 validation closure (2026-10-14; complete)

- Narrowed the derivation and credential-projection error boundaries to their concrete JCA/BC,
  validation, wallet-state, security, and SQLite exception types. Moved the SQLDelight-backed
  event/snapshot repository implementations to `androidMain`, alongside their platform database,
  and added `captureStorageResult` for the concrete Android/JDBC SQLite, serialization, JCA,
  argument/state, and security failures. Cancellation is rethrown so transaction cancellation and
  owned-buffer cleanup behavior remain intact. No Detekt suppression or rule relaxation was used.
- `:feature:fido2:ktlintCheck :feature:fido2:detekt --console=plain` passed. The first device run
  surfaced that SQLite3MC's configured driver throws `org.sqlite.SQLiteException` (a JDBC
  `SQLException`), not Android's `android.database.SQLException`; the rollback assertion and
  repository boundary were updated to cover the actual JDBC type. The rerun passed.
- `tools/local-ci.ps1 -NoFix` passed on 2026-10-14: repository ktlint, Detekt, release lint,
  compile-all, and unit-test phases all succeeded. Compile-all included changed `core:common`,
  `core:database`, FIDO2 production/host/device sources, and app wiring. The separate Quickstart
  configured host invocation passed 591 tests with zero failures/errors/skips: common 10,
  security 43, domain 47, database 9, FIDO2 477, app 5. The 4-case
  `PasskeyEventStorageOwnershipTest` suite passed within the FIDO2 host task. XML reports were
  emitted under `core/common/build/test-results/testAndroidHostTest/`,
  `core/security/build/test-results/testAndroidHostTest/`,
  `core/domain/build/test-results/testAndroidHostTest/`,
  `core/database/build/test-results/testDebugUnitTest/`,
  `feature/fido2/build/test-results/testAndroidHostTest/`, and
  `app/build/test-results/testShrunkDebugUnitTest/`; the focused FIDO2 report is
  `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.eventsourcing.PasskeyEventStorageOwnershipTest.xml`.
- `:feature:fido2:connectedAndroidDeviceTest --console=plain` passed on the configured Android
  15/API 35 AOSP ATD emulator. The XML report
  `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15.xml`
  records 13 tests, zero failures/errors/skips: seven lifecycle/crypto cases and six Compose
  screen cases, including the manual-recovery input boundary and authentication-gated mnemonic
  reveal/disposal behavior. This confirms T021's configured Compose/device checks; it does not
  close the separate T022/T051 process-restart and persistence-backed ceremony gaps.
- T063's compilation, configured host/device execution, static analysis, lint, and mnemonic UI
  boundary evidence are complete. Coverage floors (T064), external lifecycle/ceremony scenarios
  (T022/T051), and the independent formal security activation review remain separate open gates.

### T064 coverage gate attempt (2026-10-14; thresholds not met)

- First `hdkCoverageVerify` attempt exposed a repeatability defect in two existing common DataStore tests: both reused absolute fixed paths, so persisted data from a prior run changed the expected empty defaults (`UserPreferencesDataStoreTest`, expected migration version `0`, actual `1`). Changed both fixtures to unique per-run paths. `:core:common:testAndroidHostTest` then passed.
- Re-ran `.\gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkCoverageVerify --console=plain`; all discovered host test tasks completed and the coverage reports were generated, but threshold verification failed. Current reports: `core/security/build/reports/hdk-coverage/hdkModuleCoverage.xml`, `core/domain/build/reports/hdk-coverage/hdkModuleCoverage.xml`, `feature/fido2/build/reports/hdk-coverage/hdkModuleCoverage.xml`, `core/database/build/reports/hdk-coverage/hdkModuleCoverage.xml`, `core/common/build/reports/hdk-coverage/hdkModuleCoverage.xml`, `app/build/reports/hdk-coverage/hdkModuleCoverage.xml`, and `build/reports/hdk-coverage/repository.xml`.
- Refreshed report on 2026-10-14 using `.\gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkCoverageVerify --console=plain` after the app lifecycle tests and cleanup changes. Measured instruction/statement and branch coverage: `core:security` 66.3186% / 56.4103% (43 tests); `core:domain` 42.6703% / 28.0488% (29 tests); `feature:fido2` 29.2485% / 26.4629% (476 tests); `core:database` 16.4309% / 31.25% (9 tests); `core:common` 21.3105% / 13.7931% (10 tests); `app` 5.4013% / 17.1717% (5 tests); repository 31.1782% statements. Tier 2 branch values are reported without a floor. Domain branch, FIDO2 statement/branch, database statement, and app statement floors fail; no modules/classes were excluded and no floor was lowered. Reports are at the paths listed above. T064 remains open.
- FIDO2's largest missed classes include `DevelopmentToolsScreenKt`, `Fido2HomeScreenKt`, `BluetoothHidTransportImpl`, `BluetoothHidDeviceWrapper`, `Ctap2MakeCredentialHandler`, and prompt/management screens. Android/device coverage data is absent because no connected device is available. App coverage is concentrated in navigation/lifecycle Compose code with only two configured app tests. The full-module gate cannot be declared passed from the existing host suite alone.

### Lifecycle ownership source audit (T035 implementation complete; device matrix still open)

- `MainActivity.onStop()` cancels `FOREGROUND` owners. `ChimaliApplication.onTrimMemory()` fences
  and clears the provider on exactly `TRIM_MEMORY_RUNNING_CRITICAL`, `TRIM_MEMORY_BACKGROUND`, and
  `TRIM_MEMORY_MODERATE`, and requests GC in `finally`.
- HID MakeCredential/GetAssertion processing holds an `AUTHENTICATOR` owner around the existing
  handler; those handlers apply each request's bounded `getSafeTimeout()`. CTAPHID_CANCEL, transport
  disconnect, idle and advertising transitions cancel and join the transport's tracked CBOR jobs.
  Bluetooth connection alone does not initialize the wallet; its user-verification cache warm-up
  is best-effort and asynchronous.
- The UI prompt work runs in ViewModel coroutines rather than inheriting the HID coroutine context.
  Registration and authentication ViewModels now acquire their own `AUTHENTICATOR` owner after the
  user action. CTAP deferred cancellation/timeout cancels the ViewModel operation, which releases
  that owner and clears the pending prompt state; request completion does not rely on context
  propagation across the UI event bus.
- `WalletMasterSeedProvider` serializes cache/replacement state, generation-fences session clears,
  cancels active owner jobs, erases cached PRK/parent/metadata keys on final-owner release, and
  returns owned copies. `Fido2CryptoService` checks the admitted generation before deriving and
  after provider/JCA operations; operation buffers are cleared in `finally`. The provider checks
  active owner context before returning root or parent copies.
- The implementation matches the contract's ownership table: foreground stop cancels only
  foreground owners; admitted HID work holds an independent authenticator owner; disconnect and
  CTAPHID_CANCEL cancel and join tracked transport work; the final owner erases cached root/parent
  and metadata keys; replacement and severe trim callbacks advance the generation and cancel all
  owners; crypto checks generation before and after provider operations and erases stale outputs.
  Lazy owner initialization reloads the encrypted wallet; an idle transport only warms the
  user-verification availability cache and does not initialize wallet keys. The focused host
  cancellation tests recorded below verify UI-owner release on CTAP cancellation.
- These source and host facts do not replace real Android validation of overlap, pending
  verification, foreground/HID coexistence, replacement races, late synchronous provider results,
  and every trim level. The connected Android 15 results below verify only the listed
  bootstrap/UI cases; the broader T022 lifecycle suite remains open.
- On 2026-10-14, the focused host command
  `:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.integration.AuthenticationIntegrationTest' --tests 'com.chimali.fido2.integration.RegistrationOwnerIntegrationTest'`
  passed. `cancelled CTAP response cancels the ViewModel signing owner` verifies authentication
  acquires/releases the authenticator owner and resets prompt state; `cancelled CTAP registration
  response cancels its ViewModel owner` verifies the same request-cancellation behavior for
  registration. The success-path authentication test also asserts owner kind and release.

### T054–T056 closure update (2026-10-14)

- The final removal-pattern search in `quickstart.md` covered `core/` and `feature/`; the only
  remaining `private_key_alias` match is the historical `feature/fido2/.../11.sqm` migration. The
  active fresh schema, DAO, DI, and Kotlin sources have no retired alias, repair path, legacy
  derivation helper, remote KEM/derivation API, or old PQ seed cache.
- Retained current primitives and their configured host-test callers are enumerated in the T054
  audit above. The configured host suites recorded under T050 passed, including the replacement
  HKDF, P-256 parent/child, Ed25519, and ML-DSA coverage.
- The T055 KDoc/traceability review is captured in the KDoc rows of
  `specs/055-migrate-hdk-draft/documentation-review.md` and the current-source audit. API docs
  describe owned mutable values, actual errors, the software-derived parent, full-seed root,
  separated algorithm/profile branches, and local-only scope. Obsolete path/salt/KEM,
  hardware-signing, and EncryptedSharedPreferences migration claims were removed or retained only
  in explicitly historical records.
- T056 current requirements were checked in `docs/brd.md` and `docs/trd.md`: NFR-SEC-041 and
  TR-AUTH-031 bind full credential IDs and explicit algorithm/profile metadata through replay,
  snapshots and projections; describe full BIP39 seed to wallet PRK, software parent and signing,
  independent Ed25519/ML-DSA branches, recovery metadata, clean-install breaking scope, and no
  issuer capability. Superseded remote-flow requirements preserve their stable IDs and history.
  Current references to the separate OpenID4VCI investigation remain linked.
- These three source/documentation tasks are complete. They do not close the US1 acceptance
  checkpoint, device-only lifecycle/Keystore evidence, coverage floors, or independent formal
  security review.

### Domain model coverage refresh (2026-10-14)

- Added `RelyingPartyTest` and `UserConsentRecordBehaviorTest` for direct public model validation,
  normalization, boundary, copy, consent-method, IP address, and recent-use behavior. The tests
  are synthetic and require no device or persistent fixture state.
- `:core:domain:ktlintCheck :core:domain:testAndroidHostTest --console=plain` passed; the domain
  host suite now has 47 tests. The refreshed complete host suite has 591 tests (common 10,
  security 43, domain 47, database 9, FIDO2 477, app 5).
- Refreshed with
  `\.\gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkCoverageVerify --console=plain`.
  Reports were generated, but `hdkCoverageVerify` still fails its floors. Updated instruction /
  statement and branch coverage: security 66.3186% / 56.4103%; domain 56.9065% / 55.3659%; FIDO2
  29.3273% / 26.4431%; database 16.4309% / 31.25%; common 21.3105% / 13.7931%; app 5.4013% /
  17.1717%; repository 31.3171% statements. Remaining required failures are FIDO2 statement and
  branch, database statement, and app statement floors. Tier 2 branch coverage is reported with no
  floor. No exclusions or floor changes were made. T064 remains open.

### US1 acceptance evidence matrix (2026-10-14; initial partial checkpoint, superseded)

This dated matrix records the evidence available on 2026-10-14 and is retained as a historical checkpoint. Its “remaining evidence” notes and open-checkpoint conclusion below were superseded by the completed T052 acceptance matrix dated 2026-10-15; use that later section for current task status. Remaining cross-cutting gates are tracked independently under T003, T066, T067, T069, T071, and T072.

| Requirement / criterion | Current evidence | Result and remaining evidence |
| --- | --- | --- |
| FR-001, FR-007; SC-005 | `contracts/local-key-derivation.md`, `security-review.md`, `security-review-packet.md`; exact P-256 vector checks in `HdkP256Test` and `P256ParentKeyDerivationTest`; reference generator and pinned provenance in `tools/hdk-reference/` | Host evidence supports the named local profile and its documented draft-input substitution. Independent formal review of the implementation is still required before activation. |
| FR-002, FR-004, FR-005, FR-006 | `HkdfSha256Test` covers the full decoded credential ID and frozen local derivation inputs; `MultiAlgorithmIntegrationTest` verifies newly generated registration/authentication signatures; data model and recovery-input docs define profile persistence | Host tests pass. `CredentialPersistenceCeremonyTest.persistentCeremoniesRecoverForEverySupportedSignatureAlgorithm` registers P-256, Ed25519, and ML-DSA-65 credentials, closes and reconstructs the encrypted driver/database/repository, clears the provider session, then recovers each public key and verifies a new assertion signature and counter update. Separate instrumentation-package and isolated `.shrunk` production-app force-stop runners verify P-256 recovery and a post-restart assertion. |
| FR-003, FR-008 | CredentialIdTest, CredentialKeyDescriptorTest, and HdkP256Test exercise malformed, unsupported, and boundary inputs; repository reconstruction and mismatch tests reject missing/corrupt identity and do not repair a recorded key; the connected SQLiteMC case forces an insert constraint failure after old projection deletion; CredentialRepositoryImplTest verifies failed counter reads, event appends, and SQL counter-projection failures return generic database errors; GetAssertionUseCaseTest verifies read/write failures prevent releasing a signed assertion, cancellation cleanup, and unsigned-32-bit counter boundaries (0xFFFFFFFE can increment to 0xFFFFFFFF; negative, exhausted, and wider counters fail before signing or mutation). | Configured host and connected-device tests pass for the listed cases. The persisted mismatched public-key bytes remain unchanged after assertion rejection, the counter does not advance after mismatch or failed event persistence, and neither counter-read failure nor event/projection failure can be converted into apparent assertion success. Focused counter-boundary, read/write, and cancellation cases do not close every partial-persistence and external relying-party scenario; T052 remains open. |
| FR-009; SC-001 | `LocalProfileLifecycleTest` covers empty encrypted-store bootstrap, Keystore presence, SQLite integrity, same-root reconstruction, and P-256 registration/assertion after encrypted store reconstruction; `CredentialProcessRestartTest` performs force-stop/fresh-process recovery | The emulator verifies bootstrap and a persistence-backed ceremony. The instrumentation-package restart runner verifies encrypted process recovery; the app-process runner additionally starts from an uninstalled `.shrunk` package, registers through the production Koin graph, force-stops the application package, and recovers/signs in a new application process. This is focused service-path evidence; UI/entry-point acceptance and full matrix coverage remain open. |
| FR-010; SC-003 | `MultiAlgorithmIntegrationTest` verifies P-256, Ed25519, and ML-DSA-65 registration and assertions with independently verified signatures; device provider tests verify generated keys/signatures for all three branches; `CredentialPersistenceCeremonyTest.persistentCeremoniesRecoverForEverySupportedSignatureAlgorithm` exercises persistence-backed registration, reconstruction, and assertions for all three | Host ceremonies, device algorithm-provider/signature checks, and persistence-backed P-256, Ed25519, and ML-DSA-65 registration/assertion/reconstruction all pass. Separate app-process restart evidence covers P-256; multi-algorithm app-process restart remains untested. |
| FR-012 | Lifecycle owner source audit in this evidence; `AuthenticationIntegrationTest` and `RegistrationOwnerIntegrationTest` verify authenticator owner acquisition/release and CTAP cancellation propagation; the 2026-10-14 connected suite (25 tests, T051) exercises foreground/authenticator overlap, replacement fencing, all three severe trim levels, rejected signing before new owner admission, stale P-256 result suppression, `BluetoothHidTransportDisconnectDeviceTest` pending-request cancellation on production transport disconnect, and app `WalletSessionMemoryPressureCallbackTest` | Host and listed emulator cases pass. The connected app test invokes production `ChimaliApplication.onTrimMemory` for each severe trim level and verifies the wallet generation is fenced. The focused HID case confirms pending required-UV request cancellation, wallet-generation fencing, and ceremony-lock release after `disconnect()`. Real framework-originated callback delivery, Android Bluetooth profile callbacks, and broader lifecycle acceptance remain open under T052/T065. |
| FR-013; SC-004 | Crypto/provider cleanup tests, mutable UI owner tests, cancellation integration tests, `WalletSessionMemoryPressureTest`, connected trim/provider tests, Compose reveal/disposal plus IME/autofill/QR-clear checks, and the T070 connected transport-disconnect cancellation test | Covered host and UI boundary cases pass. The actual transport cancellation path is now exercised; platform clipboard retention, QR/display teardown after navigation, complete error/cancellation terminal-path audit, and physical Bluetooth profile callback integration remain unverified. The UI does not claim to disable every IME/hardware undo buffer. |
| FR-016; SC-002 | Synthetic frozen vectors cover P-256, Ed25519, and ML-DSA-65; reference-engine provenance and OpenSSL reconstruction instructions are recorded; local profile host integration tests verify ceremonies; connected device tests verify all three providers/signatures and persistence-backed registration/assertion/reconstruction for all three; `CredentialProcessRestartTest` verifies encrypted P-256 recovery after an OS force-stop | Independent synthetic vectors, host ceremonies, device provider/signature checks, persistence-backed three-algorithm recovery, and both instrumentation-package and isolated production-app P-256 restart/recovery pass. Independent formal reviewer verification remains outstanding. |
| FR-017; SC-006 | `GetAssertionUseCaseTest.assertion signs exactly the selected descriptor once without hydrating candidate credentials` verifies only the selected descriptor reaches signing once in a three-candidate assertion; `Fido2CryptoServiceTest.P-256 public key matches independent fixture and signature verifies` observes one child derivation for the sign operation. `SelectCredentialUseCaseTest` and multi-algorithm tests cover metadata-only selection, selected-key matching, and full-ID separation. | Focused host suite passes and proves zero candidate sign requests plus exactly one selected sign request; source-level crypto test confirms one P-256 child derivation per signing request. Device P-256 persistence and instrumentation-process restart recovery pass; the full acceptance run remains open under T052. |

At this historical checkpoint, broader acceptance-case accounting remained open. The later T052 section records closure of its enumerated US1 acceptance matrix with current-source host and device evidence. Neither checkpoint claims external interoperability or independent formal security review; those are handled by the separately tracked remaining tasks.

### Connected Android device run (2026-10-14; named suite passed, broader lifecycle matrix open)

- The Android 15 / API 35 AOSP ATD x86_64 emulator was started as a regular ADB device (`emulator-5554`). A prior Gradle-managed invocation reported a closed managed-device build and discovered zero tests; that attempt is invalid and is not counted. The valid invocation ran instrumentation against the connected ADB emulator.
- `:feature:fido2:connectedAndroidDeviceTest --console=plain` produced the successful connected result. Its XML report at `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD).xml` records six tests, zero failures/errors/skips: `LocalProfileLifecycleTest.emptyEncryptedRecoveryStoreInitializesAndReconstructsTheSameWalletAndDatabase`, and five `DevelopmentToolsScreenTest` cases (`devToolsTitlesHaveHeadingRole`, `mnemonicGridItemsHaveMergedDescendants`, `qrHideAndClearUseExplicitUserActions`, `manualRecoveryTransfersOneMutablePhraseBuffer`, and `manualRecoveryDisablesImeLearningAutofillAndExtractedText`). The independent `:feature:fido2:ktlintCheck --console=plain` rerun also passed on 2026-10-14.
- This verifies the named Compose/UI checks and the test's isolated encrypted recovery-store bootstrap, Android Keystore key presence, encrypted SQLite header/integrity, and deterministic wallet reconstruction after clearing the in-memory provider session. It does not exercise a process kill/relaunch, full credential registration/assertion ceremonies, transaction rollback, foreground/HID overlap, trim-level generation fencing, replacement races, or all cancellation/stale-result cases.
- The earlier statement that no device was available is superseded for this run. T021's ViewModel lifecycle coverage is in the configured host suite; the six device UI tests execute the Compose/accessibility and IME/autofill checks. The remaining T021 gap is an end-to-end screen test of the biometric success gate and screen-disposal integration. T022 remains open for the missing platform lifecycle, rollback, ceremony, and recovery cases; T051 remains open until those named suites contain and execute the full required evidence. The successful six-test run is real but does not close those broader gates.

### Expanded connected Android lifecycle run (2026-10-14; provider owner/replacement cases passed)

- After adding two cases to `LocalProfileLifecycleTest.kt`, `:feature:fido2:ktlintCheck :feature:fido2:compileAndroidDeviceTest --console=plain` passed. `:feature:fido2:connectedAndroidDeviceTest --console=plain` then ran on the connected Android 15/API 35 AOSP ATD emulator and passed all eight discovered tests, with zero failures/errors/skips. The updated report is `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15.xml`.
- New passing cases: `foregroundOwnerCanEndWhileAuthenticatorOwnerKeepsWalletSessionAlive` confirms foreground cancellation leaves an admitted authenticator owner on the active generation, clearing the session cancels the remaining owner, and a later authorized owner lazily reconstructs the same root from the disposable encrypted store. `replacingEncryptedWalletFencesAdmittedAndroidProviderOwner` imports a synthetic valid 24-word phrase through the production Android provider, verifies the transferred mutable input is erased, verifies the admitted owner is cancelled/fenced, and verifies the later generation derives a different root from persisted encrypted recovery material.
- Added `DevToolsViewModelTest.copy requires a revealed mnemonic and uses the sensitive clipboard policy`; the focused `:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.presentation.viewmodel.DevToolsViewModelTest'` and `:feature:fido2:ktlintCheck` invocation passed. It verifies clipboard copying is a user-triggered action, does nothing without a revealed mnemonic, uses the sensitive-data clipboard service with the required label/60-second expiry, and clearing the reveal erases the owned character buffer. This complements the common clipboard service's existing timer/clear tests; it is not a replacement for the remaining screen-level biometric test.
- The first six tests remain present and pass. These additions do not exercise the actual Bluetooth disconnect/pending-verification path, Android process kill/relaunch, database projection rollback, supported-algorithm device ceremonies, all three trim levels/GC callbacks, or an in-flight synchronous provider result. T022 and T051 remain open for those cases; T021 remains open for UI authorization and screen-disposal/replacement coverage beyond the passing ViewModel and Compose boundary tests.

### Connected SQLiteMC rollback run (2026-10-14; production DAO transaction rollback passed)

- Added `encryptedProjectionReplacementRollsBackEarlierDeletionWhenInsertFails` to `LocalProfileLifecycleTest.kt`. It uses the production encrypted driver, real SQLite3MC database, production `PasskeyCredentialDao`, production metadata protection services, and the production wallet provider on disposable encrypted stores.
- The test creates two rows with different RP/user projections, then requests replacement of one projection with the other row's already-used primary key. The DAO deletes the prior matching projection inside its transaction; the duplicate insert fails. The emulator test confirms the exception occurs, the old matching row is restored, the unrelated conflicting row remains, and the table still contains two rows. This validates rollback of the real driver transaction after deletion, not a mocked repository.
- `:feature:fido2:ktlintCheck :feature:fido2:compileAndroidDeviceTest --console=plain` passed; `:feature:fido2:connectedAndroidDeviceTest --console=plain` passed all nine discovered tests with zero failures/errors/skips on the Android 15/API 35 AOSP ATD emulator. The updated XML is `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15.xml`.
- T022 now has connected evidence for encrypted-store bootstrap/reconstruction, Keystore/SQLiteMC protection, foreground/authenticator overlap, wallet replacement fencing, and real projection rollback. It remains open for process restart, registration/assertion ceremonies, pending verification/disconnect, trim/GC callbacks, stale synchronous-provider results, and the remaining lifecycle matrix. T051 remains open for these missing connected scenarios and the external ceremony harness.

### Severe memory-trim lifecycle run (2026-10-14; device provider fencing passed)

- Moved `WalletSessionMemoryPressure` into `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/WalletSessionMemoryPressure.kt` so the app and production-provider instrumentation test use the same trim policy. `ChimaliApplication.onTrimMemory` still delegates to it with `MasterSeedProvider.clearSession` and `System.gc`; the app unit tests retain checks for all severe/non-severe levels and the `finally` GC hint.
- Added `severeMemoryTrimLevelsFenceWalletOwnersAndPermitEncryptedReconstruction` to the disposable-store Android device suite. For each of `TRIM_MEMORY_RUNNING_CRITICAL`, `TRIM_MEMORY_BACKGROUND`, and `TRIM_MEMORY_MODERATE`, it admits a real authenticator owner, applies the shared policy with the production provider, confirms one collection hint and generation fencing/cancellation, rejects unowned root access, then confirms a later bounded owner reconstructs the same root from encrypted recovery data.
- Added `androidProvidersGenerateAndVerifyAllSupportedCredentialSignatures`, which uses the production wallet provider, `Fido2CryptoService`, P-256 child derivation and BC ML-DSA seed import on the emulator. It generates each supported branch's public key, signs the same synthetic message through the production service, checks the stored X.509 public-key match, and verifies signatures with Android JCA for P-256/Ed25519 and BC for ML-DSA-65. The first test attempt passed raw P-256 SEC1 bytes where the service contract expects the registered X.509 encoding; the test was corrected to exercise the registration representation.
- Added `generationFenceDiscardsSynchronousP256DerivationResult`, which pauses the real P-256 derivation behind a test latch, fences its production provider generation while synchronous work is in flight, and verifies the crypto service throws cancellation without publishing the stale public key. The first test-harness version blocked the coroutine dispatcher while waiting on the latch; switching the wait to an I/O dispatcher fixed the harness and the device test passed.
- Validation: `:feature:fido2:ktlintCheck :feature:fido2:connectedAndroidDeviceTest :app:testDebugUnitTest --console=plain` passed for the trim-policy change, then `:feature:fido2:ktlintCheck :feature:fido2:connectedAndroidDeviceTest --console=plain` passed after the all-algorithm device case and again after the stale-result case. The current Android 15/API 35 AOSP ATD report `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15.xml` records twelve tests, zero failures/errors/skips (seven lifecycle/crypto and five Compose tests). Gradle resolved the app selector to `:app:testShrunkDebugUnitTest`; its two XML reports record five passing app unit tests, including all three `WalletSessionMemoryPressureTest` cases.
- This exercises the shared trim policy and real provider/cache lifecycle on-device. It does not synthesize Android framework delivery into `ChimaliApplication.onTrimMemory`; that wiring remains source-audited. The crypto device cases verify generated keys and signatures and stale synchronous-result suppression, but do not run persistence-backed FIDO2 registration/assertion use cases. T022/T051 remain open for process kill/relaunch, full registration/assertion ceremonies and restart recovery, pending-verification/disconnect, and the external disposable ceremony harness. The T021 screen gate/disposal test is recorded in the following section.

### Development tools screen lifecycle run (2026-10-14; T021 complete)

- Added `masterSeedScreenRevealsAfterAuthenticationAndErasesOnDisposal` to the moved named device suite. It provisions a synthetic phrase in a disposable encrypted DataStore through the production wallet provider, renders `DevelopmentToolsScreen`, and captures the authenticator success callback. Before success, the phrase is absent from state and semantics; after success, the UI renders it; removing the screen clears the ViewModel state and zeroes the exact mutable phrase buffer. The injectable callback exercises the UI authorization boundary without requiring emulator biometric enrollment; the Android `BiometricPrompt` itself is not automated by this test.
- Extended the platform input inspection to verify the manual-recovery IME flags, empty extracted surrounding text, no autofill descendants, and no custom IME action metadata. Android `EditorInfo` has no general flag that guarantees every IME or hardware undo buffer is disabled, so this evidence does not claim such a guarantee. Existing `DevToolsViewModelTest` cases cover mutable replacement, hide/disposal/cancellation/import cleanup, and sensitive clipboard policy; device Compose cases cover accessible headings/grid semantics, QR hide/clear, and manual mutable-buffer transfer.
- Validation: `:feature:fido2:ktlintCheck :feature:fido2:connectedAndroidDeviceTest --console=plain` passed. The actual connected Android 15/API 35 AOSP ATD report `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15.xml` records thirteen tests with zero failures/errors/skips, including six `DevelopmentToolsScreenTest` cases and seven lifecycle/crypto cases. This completes T021's configured Compose/device discovery and boundary evidence. The independent production biometric prompt and real registration persistence remain outside this task and open under T022/T051.

### T064 intermediate coverage refresh after dashboard and CTAP2 validation tests (2026-10-14; superseded by final gate below)

- Added `AuthenticatorDashboardComponentsTest` with three connected Compose cases for the FIDO2 dashboard's idle/advertising/connecting/connected/error states, paired-device aliases and fallbacks, device icon classification, and edit/connect actions. `:feature:fido2:connectedAndroidDeviceTest --console=plain` passed all 16 discovered tests on the Android 15/API 35 emulator, including the existing lifecycle and development-tools suites.
- Added `Ctap2MakeCredentialHandlerValidationTest` with eight configured host cases covering empty/wrong command payloads, busy-lock behavior, invalid/missing/unsupported input, required lock release, success response construction with the provided attestation bytes, registration error mapping, named request keys, option decoding, and valid/missing/malformed PRF salt forms. Extended `PublicKeyCredentialParametersTest` for supported-family helpers, invalid parameter rejection, and byte-content equality. Focused host tests and `:feature:fido2:ktlintCheck` passed; the full configured host run completed as part of coverage collection.
- Refreshed the connected-device instrumentation data after a JaCoCo class mismatch warning on an earlier mixed run. The final current measurement used `tools/hdk-coverage.init.gradle` and generated `feature/fido2/build/reports/hdk-coverage/hdkModuleCoverage.csv` and `.xml`; FIDO2 XML counters are 31,062/80,123 covered instructions (38.7679%) and 1,371/3,860 covered branches (35.5181%). The report includes 488 configured FIDO2 host tests; the separate connected-device report contains 16 passing tests.
- Other current gates pass: `core:security` 78.4203% statements / 61.8590% branches; `core:domain` 57.3105% / 55.8537%; Tier 2 statements `core:database` 20.4634%, `core:common` 32.8205%, and `app` 30.0254%; repository aggregate 38.1151% statements / 38.8218% branches. Tier 2 branch results are reported without a floor. `hdkCoverageVerify` still fails only the FIDO2 Tier 1 statement and branch floors (both require 40%); T064 remains unchecked. No exclusions or threshold reductions were introduced.

### T064 final coverage gate (2026-10-14; passed)

- Added `MakeCredentialOptionsTest.equality compares every option field and challenge contents` to exercise the custom equality branches across all fields, and added `AttestationObjectTest.authData_equality_compares_byte_array_contents_and_scalar_fields` for the byte-array and scalar comparison paths. Focused `:feature:fido2:ktlintCheck :feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.domain.model.AttestationObjectTest' --tests 'com.chimali.fido2.domain.model.MakeCredentialOptionsTest' --console=plain` passed.
- Ran `\.\gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkHostCoverage hdkCoverageVerify` on 2026-10-14. `hdkCoverageVerify` passed all required floors. Final whole-module counters: `core:security` 2,929/3,735 instructions (78.4203%), 193/312 branches (61.8590%); `core:domain` 2,979/5,198 (57.3105%), 229/410 branches (55.8537%); `feature:fido2` 32,338/80,123 (40.3604%), 1,556/3,860 branches (40.3109%). Tier 2 statement results: `core:database` 680/3,323 (20.4634%), `core:common` 576/1,755 (32.8205%), and `app` 590/1,965 (30.0254%). Tier 2 branch coverage is reported without a floor: database 26/64 (40.625%), common 15/58 (25.8621%), app 20/93 (21.5054%). Repository aggregate: 49,641/126,892 instructions (39.1207%) and 2,564/6,128 branches (41.8407%).
- Report artifacts: `feature/fido2/build/reports/hdk-coverage/hdkModuleCoverage.csv` and `.xml`; `core/security`, `core/domain`, `core/database`, `core/common`, and `app` each contain `build/reports/hdk-coverage/hdkModuleCoverage.xml`; repository report is `build/reports/hdk-coverage/repository.xml`. The configured FIDO2 host suite executed 499 tests. Connected Android instrumentation execution data was merged from `feature/fido2/build/outputs/code_coverage/androidDeviceTest/connected/dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15/coverage_data/coverage.ec` (383,363 bytes); its latest connected XML report records 19 tests, zero failures/errors/skips. App connected execution data was also discovered by the report script. Production class denominators remain the single module compilation outputs; no threshold was lowered and no class was excluded to satisfy the gate.

### T068 local CI attempt (2026-10-14; incomplete)

- Ran `\.\tools\local-ci.ps1 -NoFix`. The first attempt found an import-order error in the modified `app/src/androidTest/kotlin/com/chimali/StartupTest.kt`; the imports were reordered. The next attempt found three Detekt magic-number findings in the newly added `AuthenticatorDashboardComponentsTest.kt` timestamp and Bluetooth class fixtures; those values are now named constants.
- A subsequent run passed the full Ktlint stage and Detekt stage. It reached release lint, showing `:app:lintAnalyzeRelease` and `:feature:fido2:lintAnalyzeAndroidHostTest`, but then produced no output or progress for several minutes while the Gradle process remained alive. The run was interrupted; this is not a passing full-CI result. T068 remains open pending a completed `local-ci.ps1 -NoFix` run and any resulting fixes.

### T068 local CI final run (2026-10-14; passed)

- After fixing the import order and naming the dashboard fixture constants, reran `\.\tools\local-ci.ps1 -NoFix`. Ktlint passed, Detekt passed, and `lintRelease` passed in 223.96 seconds, including FIDO2 Android host-test lint analysis. The full compile phase passed in 50.2 seconds, including configured Android HostTest and Android DeviceTest compilation. The repository unit-test phase passed in 12.87 seconds. The script reported `Local CI Pipeline Completed Successfully! (305.22s)`.
- The configured FIDO2 host suite had 499 executed tests in the immediately preceding successful `hdkHostCoverage` run, and the connected Android 15/API 35 suite has 19 passing tests with zero failures/errors/skips, recorded above. The required `hdkCoverageVerify` gate also passed with merged host/device execution data as documented under T064. The remaining unsupported external CTAP2/Credential Manager harness and reference-device performance/lifecycle scenarios remain separately open; this CI result does not claim those were run.

### T065 secret-boundary source audit progress (2026-10-14; device/LeakCanary gate open)

- Inspected the current implementation in `Bip39MasterSeedGenerator`, `HkdfSha256`,
  `P256ParentKeyDerivation`, `HdkP256`, `WalletMasterSeedProvider`, `Fido2CryptoService`,
  `PostQuantumCrypto`, `HmacSecretProcessor`, `DevToolsViewModel`,
  `DevelopmentToolsScreen`, and `MnemonicQrScanner`. The detailed API/lifetime/copy-limit table is
  in `security-review.md` under “Implemented boundary audit.”
- The audit distinguishes owned mutable arrays from dependency/platform-owned immutable values.
  Owned normalization, KDF, parent/child, signing, import, reveal, manual-input, and scan-transfer
  buffers have explicit terminal cleanup paths. ICU/JCA/BC internals, `BigInteger`, Compose text,
  QR painter input, ML Kit `rawValue`, TextField/IME data, and clipboard Strings cannot be claimed
  erasable by the application. The only recovery material intentionally persisted is encrypted;
  raw BIP39 seed, wallet PRK, parent scalar, child factor, and private-key working arrays are not
  intentionally persisted.
- Redacted provider exception details from `Fido2CryptoService` errors/logs, staticized ML-DSA,
  hmac-secret, and mnemonic-import failure diagnostics, and removed the corresponding ViewModel
  forwarding of crypto causes/messages. `Fido2CryptoServiceTest` adds a marker-exception assertion
  that returned error diagnostics contain neither the provider message nor a cause.
- Focused host evidence: `:feature:fido2:ktlintCheck` passed; the selected reports for
  `Fido2CryptoServiceTest` (9 tests), `PostQuantumCryptoTest` (12 tests),
  `DevToolsViewModelTest` (12 tests), and `HmacSecretProcessorZeroingTest` (result XML under
  `feature/fido2/build/test-results/testAndroidHostTest/`) passed with zero failures/errors.
- The earlier Android 15/API 35 connected suite had 19 passing tests, including the recorded
  foreground/authenticator owner, replacement fencing, mnemonic UI clear/disposal, IME/autofill,
  and memory-trim cases. The fresh rerun and its limits are recorded in the next section.
- Repository audit found no LeakCanary dependency. Added `com.squareup.leakcanary:leakcanary-android`
  2.14 as an app `debugImplementation`, matching the [official setup guidance](https://square.github.io/leakcanary/getting_started/).
  `:app:assembleDebug --console=plain` passed (first full Debug build, 17m 41s). Gradle dependency
  insight confirmed LeakCanary 2.14 in `debugRuntimeClasspath` and no matching artifact in
  `releaseRuntimeClasspath`. On the fresh emulator install described below, Logcat confirmed
  LeakCanary startup and its watcher observed `MainActivity` after `Activity#onDestroy`. No heap
  analysis or dump was produced. Heap dumps can capture process memory, so further verification
  must use a disposable debug install and synthetic data only, then inspect and remove any
  diagnostic artifacts.
- Source review, focused host checks, connected owner/cancellation cases, and the Debug LeakCanary
  startup observation improve the inspected paths but do not prove global negative assurance for
  every error/cancellation path or platform copy. T065 remains open pending a LeakCanary assertion
  over the mnemonic owner lifecycle using synthetic input and review of any resulting diagnostic.
  Do not activate production derivation or claim formal signoff from this partial audit.

### Current connected-device rerun and quickstart reconciliation (2026-10-14; partial)

- Re-ran the actual discovered Gradle task `:feature:fido2:connectedAndroidDeviceTest --console=plain`
  against the attached `emulator-5554` / Android 15 API 35 AOSP ATD x86_64 emulator. The current
  XML report `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15.xml`
  records 20 tests, zero failures, errors, or skips: seven lifecycle/provider tests, one
  persistence-backed ceremony, six development-tools tests, and six authenticator dashboard tests.
- This fresh run revalidated the disposable encrypted recovery-store bootstrap/Keystore/integrity
  test, foreground/authenticator ownership overlap, wallet replacement fencing, all listed severe
  trim callbacks, stale P-256 result suppression, encrypted projection rollback, device-side P-256,
  Ed25519 and ML-DSA provider signatures, and Compose mnemonic clear/disposal/manual-entry tests.
- It did not kill/relaunch the OS process, perform production repository-backed register/assert
  ceremonies across restart, exercise the real HID pending-verification/disconnect path, or run
  LeakCanary. These are still open under T022/T051/T052/T065; the passing 19-test suite is not
  described as those results.
- Reconciled the guide's connected-task selection with the registered task that ran, its exact
  report path, the existing frozen fixture `core/security/src/androidHostTest/resources/hdk/local-profile-v1.json`,
  and the T064 report paths. Re-ran the guide's runtime removal-pattern search over `core/` and
  `feature/`: the sole match was `private_key_alias` in historical SQLDelight migration
  `feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/Fido2Database/11.sqm`;
  no current retired helper or API match was found.
- The application Debug APK and Debug-only LeakCanary dependency resolution are now build-verified,
  with a separate Release dependency-insight check showing no LeakCanary artifact in
  `releaseRuntimeClasspath`. The connected suite above runs the feature's test application, not the
  app Debug variant, so it does not count as LeakCanary startup or analysis evidence.
- The quickstart remains accurate as a repeatable procedure. T067 remains open because its full
  authorized scenario list includes missing baseline/performance, process-restart, external
  relying-party harness, and LeakCanary results. No interoperability or formal security approval
  is inferred from this rerun.

### T065 LeakCanary debug-startup observation (2026-10-14; no mnemonic material)

- Confirmed the emulator had no installed `com.chimali` app before this check. Installed the fresh
  `app-debug.apk`, launched `MainActivity`, and observed the app on first-run onboarding. Startup
  initializes DI/logging and does not initialize the wallet. Logcat reported “LeakCanary is
  running and ready to detect memory leaks.” Replacing the task with a new `MainActivity` caused
  the prior activity to receive `Activity#onDestroy`; LeakCanary logged watching that activity and
  its cleared ViewModel/Compose owners.
- No mnemonic, root, PRK, signing key, or production wallet was created or imported. A targeted
  app-private search found no `.hprof` file; no heap analysis was produced. The temporary Debug
  app was uninstalled after the observation.
- This verifies debug dependency startup and object-watcher wiring on-device, but it is not an
  `assertNoLeaks()` result and did not exercise the mnemonic reveal screen or prove the entire
  secret owner graph free of leaks. T065 remains open for a LeakCanary assertion over the relevant
  synthetic mnemonic owner lifecycle, with the heap-dump/report handling reviewed before claiming
  FR-013 diagnostic negative assurance.

### T065 synthetic mnemonic-owner LeakCanary assertion (2026-10-14; scoped pass)

- Added `app/src/androidTest/kotlin/com/chimali/LeakCanaryMnemonicOwnerTest.kt` using only the
  published BIP39 `abandon … art` test phrase, an isolated temporary DataStore path and a no-op
  clipboard adapter. The test imports the phrase through `WalletMasterSeedProvider`, verifies that
  the import buffer is erased, attaches `DevToolsViewModel` to `MainActivity.viewModelStore`, loads
  the mnemonic into its owned mutable buffer, closes the activity, and verifies the revealed buffer
  is all zeroes. After the helper scope releases its locals, it calls `LeakAssertions.assertNoLeaks()`.
- Added LeakCanary 2.14 to the disposable `shrunkDebug` target and the instrumentation assertion
  artifact to `androidTest`. The first run exposed that R8 removed `ObjectWatcher` methods called by
  the test APK; added a keep rule to `shrunkDebug` only. LeakCanary remains absent from
  `releaseRuntimeClasspath`.
- Command: `./gradlew.bat "-Pandroid.testInstrumentationRunnerArguments.class=com.chimali.LeakCanaryMnemonicOwnerTest" :app:ktlintCheck :app:connectedShrunkDebugAndroidTest --console=plain`.
  The Android 15/API 35 AOSP ATD x86_64 emulator report
  `app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15.xml`
  records one test, zero failures, zero errors and zero skips. The passing assertion produced no
  retained-object failure trace; a post-assertion scan of app-private files/cache also found no
  `.hprof` file.
- This closes only the synthetic mnemonic owner’s activity/ViewModelStore teardown path. It does
  not establish every error/cancellation terminal path, prove absence of dependency/runtime copies,
  inspect every platform diagnostic or persistence path, or complete FR-013 negative assurance.
  T065 remains open pending those broader source/device checks and review closure; production
  activation is not authorized by this scoped result.

### Persistence-backed P-256 device ceremony and refreshed connected suite (2026-10-14)

- Added `p256RegistrationAndAssertionRecoverFromEncryptedEventAndProjectionStores` in
  `feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/data/crypto/CredentialPersistenceCeremonyTest.kt`.
  The test and reusable production-repository factory were split out of `LocalProfileLifecycleTest.kt`
  after the full CI run identified Detekt's 600-line class limit; the lifecycle class and ceremony
  remain in the same configured connected-device suite.
  It uses the production encrypted SQLiteMC driver, event/snapshot repositories, credential
  repository, `RegisterCredentialUseCase`, `GetAssertionUseCase`, and `WalletMasterSeedProvider`
  over disposable synthetic data. It registers a P-256 credential, verifies its self-attestation
  signature, closes and reconstructs the database/repository graph over the same encrypted files
  after clearing the in-memory provider session, checks the persisted descriptor/public key, makes
  an assertion, independently verifies the assertion signature with the recovered public key, and
  confirms the sign counter persisted.
- `:feature:fido2:ktlintCheck :feature:fido2:compileAndroidDeviceTest --console=plain` passed.
  `:feature:fido2:connectedAndroidDeviceTest --console=plain` passed on the Android 15/API 35 AOSP
  ATD x86_64 emulator. The current XML report
  `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15.xml`
  records 20 tests, zero failures, zero errors, and zero skips: eight lifecycle/crypto tests,
  six development-tools Compose tests, and six authenticator-dashboard Compose tests. The new
  persistence-backed ceremony took 1.661 seconds in the refreshed instrumentation report.
- This verifies production-repository P-256 registration/assertion across encrypted driver,
  database, and repository reconstruction after provider-session clearing. It does not simulate an
  OS process kill/relaunch, prove clean-install behavior for all ceremony paths, cover persistent
  ceremonies for Ed25519/ML-DSA, exercise HID pending-verification/disconnect, or execute an
  external CTAP2/Credential Manager harness. T022, T051, T052, T067, and T069 remain open for their
  remaining acceptance evidence.

### T064 refreshed coverage gate after the persistence-backed ceremony (2026-10-14; passed)

- Re-ran `./gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkHostCoverage hdkCoverageVerify --console=plain` against the current tree after the new device ceremony and 20-test connected suite.
  `hdkCoverageVerify` passed. Updated instruction/statement and branch results are: `core:security`
  81.1513% / 62.5%; `core:domain` 58.0031% / 56.0976%; `feature:fido2` 40.4109% / 40.4095%;
  Tier 2 `core:database` 20.4634%, `core:common` 33.4473%, and `app` 34.6565%; repository
  aggregate 39.2387% statements / 42.0340% branches. Tier 2 branches are reported without a floor.
- The generated report records 501 FIDO2 configured host tests, 10 database tests, 10 common tests,
  5 app tests, 47 domain tests, and 43 security tests. No floor was lowered, no module/class was
  excluded, and the host/device execution accounting retained one production denominator per
  module. This refresh supersedes earlier intermediate coverage snapshots; T064 remains complete.

### T052 selected-only assertion derivation evidence (2026-10-14; focused pass)

- Added `GetAssertionUseCaseTest.assertion signs exactly the selected descriptor once without hydrating candidate credentials` with three synthetic candidate summaries and a selected middle credential. It asserts one signing call with the selected ID and registered public key, zero signing calls for either unselected ID, zero full-credential hydration for all three, and no sign-counter reads for either unselected credential.
- The companion `Fido2CryptoServiceTest.P-256 public key matches independent fixture and signature verifies` tracks the production HdkManager calls: generation increments the child-derivation count once, and signing the selected descriptor increments it exactly once more. Together these establish metadata-only candidate selection and one selected P-256 derivation per assertion-signing request at the service boundary.
- `:feature:fido2:ktlintCheck :feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.domain.usecase.GetAssertionUseCaseTest' --console=plain` passed all 10 tests with zero failures/errors/skips. The repository `tools/local-ci.ps1 -NoFix` rerun also passed after the device-test split: Ktlint, Detekt, release lint, compile-all, and unit-test phases all succeeded (75.19 seconds). T052 remains open for its process-restart, persistent mismatch/no-overwrite, and broader scenario matrix.

### Current `tools/local-ci.ps1 -NoFix` verification (2026-10-14; passed)

- After extracting the persistence-backed ceremony into its own device-test class, ran
  `./tools/local-ci.ps1 -NoFix` on the current tree. It exited successfully in 75.19 seconds with
  Ktlint, Detekt, Release lint, compile-all, and repository unit tests all passing. Compile-all
  includes `:feature:fido2:compileAndroidDeviceTest`; the connected device suite separately passed
  all 20 tests, and the refreshed `hdkCoverageVerify` passed with 501 FIDO2 host tests and all
  required coverage floors. No Detekt suppression or coverage-floor change was introduced.

### T022/T051/T052 OS process restart and recovery proof (2026-10-14; focused pass)

- Added `CredentialProcessRestartTest.kt` and `tools/run-hdk-process-restart-test.ps1`. The runner
  invokes the preparation and recovery JUnit methods separately, records the preparation process
  ID in a disposable app-private fixture, issues `am force-stop` for the instrumentation package,
  confirms no package PID remains, then launches the recovery method with the same random fixture
  ID. The recovery test asserts that Android supplied a new process ID before reopening storage.
- On the attached Android 15/API 35 AOSP ATD x86_64 emulator, the latest
  `tools/run-hdk-process-restart-test.ps1` run passed: preparation reported `OK (1 test)` in 1.3 s;
  fresh-process recovery reported `OK (1 test)` in 1.212 s. The second process loaded the synthetic
  P-256 credential projection and key from encrypted DataStore/SQLiteMC persistence, generated an
  assertion and verified its ECDSA signature with the recovered registered public key. The fixture
  cleanup ran after recovery. Inputs were synthetic only; no production app data or accounts were
  used.
- `:feature:fido2:ktlintAndroidDeviceTestSourceSetFormat :feature:fido2:compileAndroidDeviceTest
  --console=plain` passed. This is focused restart evidence, not completion of T022/T051/T052: the
  broader device lifecycle/HID/Compose requirements, transaction-failure matrix, persistent
  mismatch/no-overwrite cases, full acceptance matrix, and external relying-party harness remain.
- The first ordinary connected-suite attempt showed Android's result adapter serialized JUnit
  assumption violations as failures. The entry points now return without touching device data when
  no fixture ID is supplied; the dedicated runner supplies its random ID and executes both phases.
  The rerun of `:feature:fido2:connectedAndroidDeviceTest --console=plain` passed all 23 discovered
  methods with zero failures/errors/skips. The report is
  `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15.xml`.
- Re-ran `tools/local-ci.ps1 -NoFix` after the restart-test changes. Ktlint, Detekt, Release lint,
  compile-all, and repository unit tests all passed (17.64 seconds; 309 Gradle tasks, 9 executed,
  300 up-to-date). T068 remains complete; T022/T051/T052 remain open for their other stated gates.

### T052 registered-key mismatch and no-overwrite evidence (2026-10-14; focused pass)

- Strengthened `Fido2CryptoServiceTest` to pass a well-formed X.509 P-256 key derived for a
  different synthetic credential ID as the registered-key expectation. The real crypto service
  rejects the mismatch after deriving the selected key and returns no signature; the case no longer
  relies on malformed 32-byte input.
- Extended `CredentialPersistenceCeremonyTest` to register and authenticate through the production
  repositories, replace only the disposable SQLite projection's `public_key` with the valid key of
  another synthetic P-256 credential, and attempt a second assertion through `GetAssertionUseCase`.
  The request returns `Outcome.Error`; a subsequent summary read confirms the mismatched persisted
  key remains byte-for-byte unchanged and the sign counter remains at 1. This demonstrates failure
  without local key repair or counter advancement on the encrypted device repository path.
- Added `GetAssertionUseCaseTest.registered public key mismatch fails without mutating the selected
  key or sign counter`, which checks the use-case failure path also avoids counter and usage updates.
  The focused host run passed 20 tests: 9 `Fido2CryptoServiceTest` and 11
  `GetAssertionUseCaseTest`, with zero failures/errors/skips. The targeted Android device run passed
  `p256RegistrationAndAssertionRecoverFromEncryptedEventAndProjectionStores` on the Android 15/API
  35 AOSP ATD emulator in 1.983 seconds, with zero failures/errors/skips. Ktlint and Android device
  test compilation passed. T052 remains open for malformed/missing metadata, remaining boundaries,
  and its complete acceptance matrix; this evidence does not replace independent security review.

### Current full connected, CI, and coverage gates after mismatch coverage (2026-10-14; passed)

- Re-ran `:feature:fido2:connectedAndroidDeviceTest --console=plain` without instrumentation
  filtering after the prior filtered device run had replaced the merged device-coverage data. The
  Android 15/API 35 AOSP ATD x86_64 report records 23 test methods, zero failures, zero errors, and
  zero skips. The persistence ceremony containing the valid-key mismatch/no-overwrite case passed;
  the separately parameterized two-process runner still owns the actual process-restart proof.
- Re-ran `tools/local-ci.ps1 -NoFix` after the mismatch tests were added. Ktlint, Detekt, Release
  lint, compile-all, and unit tests all passed in 33.17 seconds. No production source or coverage
  threshold changed for this test-only work.
- The first coverage verification immediately after a class-filtered instrumentation run reported
  incomplete device execution coverage (FIDO2 32.5219% instructions / 35.5365% branches; database
  18.9287% statements). After the full connected suite regenerated device coverage,
  `./gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkHostCoverage
  hdkCoverageVerify --console=plain` passed. Current instruction/statement and branch results are:
  `core:security` 81.1513% / 62.5%; `core:domain` 58.0031% / 56.0976%; `feature:fido2` 40.4359% /
  40.4873%; Tier 2 `core:database` 20.4634%, `core:common` 35.0427%, and `app` 34.6565%; repository
  aggregate 39.2544% / 42.0829%. The report records 502 FIDO2 host tests. No gate floor was reduced
  or class excluded; this refresh supersedes the prior 501-test coverage snapshot and keeps T064
  complete.

### T065 CTAP diagnostic redaction and current local CI rerun (2026-10-14; scoped pass)

- Source review found ceremony-handler logs that forwarded `DomainError`/exception messages and
  throwable causes from MakeCredential, GetAssertion, credential management, and authenticator
  reset paths. Replaced those dynamic values with fixed operation labels; the two malformed
  hmac-secret input paths retain only the exception class name. Also removed backend exception
  contents from the assertion sign-counter update and encrypted database open/integrity logs,
  retaining operation and exception-class labels. Error-to-CTAP status mapping and response
  behavior are unchanged.
- A targeted source scan of `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2` for
  logger calls forwarding caught exceptions, causes, or dynamic exception/error messages returned
  no matches. This is source-review evidence for those handlers, not a repository-wide proof for
  every diagnostic or persistence/cancellation path.
- Additional scans across the current security, database, wallet-provider, crypto, repository,
  use-case, and ViewModel main sources found no logging interpolation of variables named as
  mnemonic, seed, PRK, scalar, factor, private key, or secret. The inspected log values in those
  sources are static operation text, exception class names, public metadata, or byte lengths. These
  searches do not replace path-by-path review of indirect exception contents or provider internals.
- The first `tools/local-ci.ps1 -NoFix` run caught Detekt `SwallowedException` findings after the
  redaction removed the exception values from two logs. Recording only `e::class.simpleName`
  resolved the findings without logging exception messages or causes. The rerun completed
  successfully in 45.22 seconds: Ktlint, Detekt, release lint, compile-all, and repository unit
  tests passed. After the subsequent database and sign-counter log review, a second complete rerun
  passed in 57.51 seconds with the same phases. `git diff --check` also passed.
- T065 remains open: error/cancellation terminal-path ownership, all app and library persistence/
  diagnostics, connected lifecycle cases, and independent formal review still require evidence.

### T022/T051/T052 persistent recovery for all supported algorithms (2026-10-14; passed)

- Added `persistentCeremoniesRecoverForEverySupportedSignatureAlgorithm` to
  `feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/data/crypto/CredentialPersistenceCeremonyTest.kt`.
  With synthetic inputs and a disposable encrypted DataStore/SQLiteMC database, it registers
  ES256/P-256, Ed25519, and ML-DSA-65 credentials through `RegisterCredentialUseCase`, verifies
  each self-attestation, closes the database, clears the provider session, and rebuilds the
  production repositories. For each recovered credential it checks algorithm, exact profile and
  public-key encoding, makes an assertion, verifies P-256 and Ed25519 signatures with an available
  non-BC Android JCA provider, verifies ML-DSA-65 with BC's JCA verifier, and checks the persisted
  counter. The first attempt exposed that default provider selection resolved to BC; the test now
  selects a provider explicitly and asserts the non-BC provider for P-256 and Ed25519.
- `:feature:fido2:ktlintAndroidDeviceTestSourceSetFormat :feature:fido2:connectedAndroidDeviceTest
  --console=plain` passed on Android 15/API 35 AOSP ATD x86_64. Report
  `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15.xml`
  records 24 tests, zero failures, zero errors, and zero skips. The all-algorithm persistent
  ceremony took 2.348 seconds in the final full-suite report (2.458 seconds in the targeted run).
- Refreshed `hdkHostCoverage hdkCoverageVerify` after the full connected suite; the gate passed.
  Current instruction/statement and branch results: `core:security` 82.7577% / 64.4231%;
  `core:domain` 58.0031% / 56.0976%; `feature:fido2` 40.5462% / 40.4984%; Tier 2
  `core:database` 20.5167% (40.625% branches reported), `core:common` 35.0427% (29.3103%),
  and `app` 34.6565% (27.9570%); repository aggregate 39.3619% / 42.1895%. The report counts
  502 FIDO2 host tests. Floors and denominators were not changed.
- Final `tools/local-ci.ps1 -NoFix` rerun passed in 17.85 seconds with Ktlint, Detekt, release
  lint, compile-all, and repository unit tests successful. This adds persistent ceremony evidence,
  but does not close T022/T051/T052: production-app process restart, remaining transport and UI
  teardown flows, and complete acceptance reconciliation remain outstanding.

### T022/T051 production-app process restart and P-256 platform provider (2026-10-14; focused pass)

- Added `app/src/androidTest/kotlin/com/chimali/ProductionAppProcessRestartTest.kt` and
  `tools/run-hdk-app-process-restart-test.ps1`. The two-phase test uses the actual application
  Koin graph in the isolated `shrunkDebug` package (`com.chimali.shrunk`), registers a synthetic
  P-256 credential through `Fido2Service`, writes only a random test ID, preparation PID, and public
  credential ID to app-private fixture storage, force-stops the target app package, verifies no app
  PID remains, then recovers the credential and signs an assertion in a fresh app process. Recovery
  asserts a different PID and verifies the signature with a non-BC platform JCA provider. The runner
  uninstalls only the `.shrunk` app and instrumentation packages in its setup/finally paths.
- The first shrunk-app ceremony exposed a provider compatibility defect: `Fido2CryptoService`
  requested P-256 `EC` key factories and ECDSA signatures from a directly instantiated BC provider,
  which did not expose `EC` on the Android runtime. P-256 key import, registered-key comparison,
  and signing now use the platform JCA defaults; Ed25519 and ML-DSA retain their supported BC
  paths. The app test variant's keep rules retain domain factory and service APIs invoked only by
  the external instrumentation APK; they do not modify the production release rules.
- The successful `tools/run-hdk-app-process-restart-test.ps1` run built both APKs, installed the
  isolated target/test packages, and reported `OK (1 test)` for both
  `registerSyntheticCredentialInProductionAppProcess` and
  `recoverAndSignInFreshProductionAppProcess`. The recovery phase passed its PID-change assertion,
  read the stored P-256 credential, produced an assertion, and verified its signature. Both
  disposable packages were uninstalled by the runner's `finally` cleanup. The focused host test
  command `:feature:fido2:testAndroidHostTest --tests
  com.chimali.fido2.data.crypto.Fido2CryptoServiceTest` also completed successfully before the app
  runner; full connected, coverage, and final local CI verification are still required after this
  production-source change.
- Updated `quickstart.md` and T022/T051 instructions to include this separate app-process harness.
  T022/T051 remain open for the complete lifecycle matrix and external-flow acceptance beyond the
  passing connected suite; T052, T065,
  T067, and T069 also remain open for their stated acceptance, audit, reconciliation, and review
  gates.

### Post-provider-change connected suite, coverage, and final local CI (2026-10-14; passed)

- After the P-256 platform-provider change, ran
  `./gradlew.bat :feature:fido2:connectedAndroidDeviceTest --console=plain` on Android 15/API 35
  AOSP ATD x86_64 (`dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15`). The connected XML report at
  `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15.xml`
  records 24 tests with zero failures, errors, or skips. Per-class results: `CredentialPersistenceCeremonyTest`
  2/2, `CredentialProcessRestartTest` 3/3, `LocalProfileLifecycleTest` 7/7,
  `AuthenticatorDashboardComponentsTest` 6/6, and `DevelopmentToolsScreenTest` 6/6. The separate
  app-process runner also passed both prepare and recovery methods as recorded above.
- Refreshed scoped, affected-module, and repository reports after the full connected suite with
  `./gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkHostCoverage
  hdkCoverageVerify --console=plain`. The verifier passed: `core:security` 82.7577% instructions /
  64.4231% branches; `core:domain` 58.0031% / 56.0976%; `feature:fido2` 40.5440% / 40.5293%;
  Tier 2 `core:database` 20.5167%, `core:common` 35.0427%, and `app` 34.6565%; repository
  aggregate 39.3605% instructions / 42.2084% branches. Tier 2 branch rates were reported as
  40.625%, 29.3103%, and 27.9570% respectively. The report includes 502 FIDO2 host tests; no
  threshold or denominator was adjusted.
- Final requested `./tools/local-ci.ps1 -NoFix` completed successfully in 48.4 seconds. Ktlint,
  Detekt, Release lint, compile-all, and repository unit tests passed. This is the final CI run for
  the current provider/test/documentation edits; the independent formal review, reference-device
  baseline/comparison, external relying-party harnesses, and remaining acceptance/audit matrices
  remain open and are not implied by these green checks.
- Re-ran `tools/run-hdk-process-restart-test.ps1` after the P-256 provider change; both the
  feature-package preparation and fresh-process recovery reported `OK (1 test)`. The recovered
  P-256 assertion verified successfully, and the runner invoked its synthetic fixture cleanup.

### T065 debug and crash-log throwable redaction (2026-10-14; focused pass)

- Closed a gap found during the diagnostics audit: the debug Logcat writer previously received raw
  throwable messages/causes, and the local crash writer formatted the complete throwable chain.
  Added `PrivacyScrubbingLogWriter` around Logcat; it scrubs the message and forwards only the
  exception class label and stack frames, with no original message or cause. The local crash writer
  now persists the exception class label and frames only. Both routes keep useful code locations
  while dropping uncontrolled exception text.
- Expanded `PrivacyLogScrubber` labels for PRK, private key/scalar, root key, factor, seed, and
  mnemonic material, including space-separated labels. The local writer continues to scrub message
  text before storage. The implementation review records this as defense in depth; an output
  scrubber alone does not prove every source message is free of secret values.
- Ran `:feature:fido2:ktlintAndroidMainSourceSetFormat
  :feature:fido2:ktlintAndroidHostTestSourceSetFormat` and targeted
  `:feature:fido2:testAndroidHostTest --tests
  com.chimali.fido2.util.logging.PrivacyLogScrubberTest --tests
  com.chimali.fido2.util.logging.LocalCrashReportingLogWriterTest --console=plain`. XML reports
  record 6 scrubber tests and 13 log-writer tests, all passing with zero failures/errors. Cases
  assert synthetic private markers do not reach local files or the debug delegate and throwable
  causes are not forwarded.
- T065 remains open for the complete path-by-path persistence/log review, remaining app/library
  diagnostic sites, all terminal-path cleanup scenarios, device ownership/cancellation evidence,
  and independent formal review. The focused tests do not establish that all indirect secret-bearing
  application messages are detectable by the scrubber.

### Post-logging-change connected, coverage, and CI verification (2026-10-14; passed)

- Re-ran `:feature:fido2:connectedAndroidDeviceTest --console=plain` after wiring the scrubbed
  Logcat writer and changing crash-log throwable formatting. The Android 15/API 35 AOSP ATD report
  `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15.xml`
  records 24 tests, zero failures/errors/skips.
- Re-ran `hdkHostCoverage hdkCoverageVerify` with the scoped init script after the source changes;
  the gate passed. Results: security 82.7577% instructions / 64.4231% branches; domain 58.0031% /
  56.0976%; FIDO2 40.6109% / 40.5699%; Tier 2 database 20.5167% (40.625% branches), common
  32.6496% (27.5862%), app 34.6565% (27.9570%); repository aggregate 39.3706% instructions /
  42.2161% branches. The host report counted 505 FIDO2 tests. Thresholds and denominators are
  unchanged.
- Final `tools/local-ci.ps1 -NoFix` after the logging implementation and tests completed
  successfully in 227.14 seconds; Ktlint, Detekt, Release lint, compile-all, and repository unit
  tests passed. No CI failure was ignored.

### Convergence verification refresh (2026-10-14; passed)

- Re-ran `tools/local-ci.ps1 -NoFix` from the repository root. The script completed successfully in 39.28 seconds; Ktlint, Detekt, Release lint, compile-all, and repository unit tests passed. The full output ended with `Local CI Pipeline Completed Successfully!` and no failed phase.
- Re-ran `:feature:fido2:connectedAndroidDeviceTest --console=plain` on the attached Android 15 AOSP ATD x86_64 emulator (`emulator-5554`, model Android_ATD_built_for_x86_64). The connected XML report `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15.xml` records 24 tests, zero failures, errors, or skips: credential persistence ceremonies 2, process-restart class 3 (argument-gated prepare/recover cases are no-ops in ordinary discovery), lifecycle 7, dashboard Compose 6, and development-tools Compose 6.
- The current emulator is not a Pixel 6a or equivalent performance reference. No latency, binary-size comparison, or peak/session-memory baseline was collected; T003/T066 and convergence task T071 remain open. The connected suite does not cover HID disconnect or pending-verification cancellation through the production transport, so T022/T070 remain open. No independent formal security reviewer disposition is present; T065/T069/T072 remain open.

### Transport disconnect cancellation regression (2026-10-14; host test passed)

- Added `BluetoothHidTransportLifecycleTest` in the configured FIDO2 Android host suite. It enters the production `dispatchMessage` path with a synthetic CTAPHID GetAssertion command, suspends the assertion handler at a pending verification point, calls the production transport `disconnect()`, and asserts cancellation plus release of the authenticator session owner. The only production visibility adjustment is making `dispatchMessage` module-internal so the transport's actual dispatcher can be exercised directly.
- Ran `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.transport.BluetoothHidTransportLifecycleTest --console=plain`; XML `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.transport.BluetoothHidTransportLifecycleTest.xml` records 1 test, zero failures/errors/skips. The matching test log shows CTAP2 GetAssertion dispatch followed by transport disconnect.
- This is host-level transport regression evidence with a synthetic handler/provider, not Android device evidence from real UserVerificationService/HID callbacks. T070 remains open until the requested connected-device production-provider path is exercised; its test must still establish the pending-verification and HID disconnect lifecycle under platform wiring.

### Local CI after transport lifecycle regression (2026-10-14; passed)

- Ran `tools/local-ci.ps1 -NoFix` after adding the transport lifecycle host test. Ktlint, Detekt, release lint, compile-all, and repository unit tests all passed; the script ended `Local CI Pipeline Completed Successfully! (357.6s)`.
- This confirms the new production dispatcher visibility and host test compile with the repository checks. It does not replace T070's pending device-level transport/verification test or the remaining reference-device and independent-review gates.

### Connected HID pending-request cancellation (2026-10-14; passed)

- Added `BluetoothHidTransportDisconnectDeviceTest` in the FIDO2 Android device-test source set.
  It sends a synthetic CTAPHID GetAssertion request through the production transport dispatcher,
  runs the real GetAssertion handler and `WalletMasterSeedProvider`, observes the pending UI
  authentication request, then invokes production `BluetoothHidTransportImpl.disconnect()`.
  Assertions verify the user decision is pending before disconnect, the deferred is cancelled,
  the provider generation is fenced after the authenticator owner is released, and the ceremony
  lock is released. The synthetic credential repository supplies one matching summary, and the
  CTAP request sets and asserts required user verification to force the pending-authentication
  path; no credential signing is performed. Android Bluetooth profile callbacks are not
  synthesized by this test.
- Ran the focused connected test on the attached Android 15/API 35 AOSP ATD x86_64 emulator. It
  passed with zero failures/errors/skips. Then ran the full
  `:feature:fido2:connectedAndroidDeviceTest --console=plain` suite; XML
  `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15.xml`
  records 25 tests, zero failures/errors/skips, including this transport case and
  `LocalProfileLifecycleTest.generationFenceDiscardsSynchronousP256DerivationResult`.
- T070 is complete for connected production-provider/transport disconnect cancellation and stale
  result coverage. The ATD emulator is not the Pixel 6a-class performance reference required by
  T003/T071; no performance measurements are claimed. The broader device lifecycle and external
  ceremony requirements in T022/T051 remain open.

### Final SpecKit implementation CI (2026-10-14; passed)

- After the final single-candidate/required-UV adjustment, re-ran the focused connected
  `BluetoothHidTransportDisconnectDeviceTest` and the complete
  `:feature:fido2:connectedAndroidDeviceTest` suite. The complete XML report records 25 tests,
  zero failures/errors/skips, including stale synchronous P-256 result suppression.
- Ran `tools/local-ci.ps1 -NoFix` against the final implementation state. Ktlint, Detekt,
  Release lint, compile-all, and repository unit tests passed; the script ended with
  `Local CI Pipeline Completed Successfully! (87.05s)`.

### Process-restart harness revalidation (2026-10-14; passed)

- Re-ran `tools/run-hdk-process-restart-test.ps1 -Serial emulator-5554`. The synthetic P-256
  preparation phase passed (`OK (1 test)`, 20.183 s); after force-stopping the instrumentation
  package, fresh-process recovery and assertion verification passed (`OK (1 test)`, 1.322 s).
  The runner's `finally` cleanup removes the synthetic fixture.
- Re-ran `tools/run-hdk-app-process-restart-test.ps1 -Serial emulator-5554`. The shrunk production
  app and instrumentation APKs assembled successfully, each restart phase passed (`OK (1 test)`,
  registration 3.584 s and recovery/signature verification 2.445 s), and the runner removed only
  the disposable `com.chimali.shrunk` and `com.chimali.shrunk.test` packages. The Gradle assemble
  phase took 12m44s because it reran R8 shrinking; this is a successful measured run, not a test
  timeout. These two P-256 restart proofs strengthen T022/T051/T052 but do not complete their
  remaining lifecycle matrix, external harness, or cross-algorithm restart requirements.
- Updated `quickstart.md` to name the connected transport-disconnect regression and distinguish
  its synthetic candidate feed from Android Bluetooth callback and broader acceptance coverage.
  T067 remains open for its remaining authorized scenarios and reference-device measurements.

### CI after restart-harness revalidation and quickstart reconciliation (2026-10-14; passed)

- Re-ran `tools/local-ci.ps1 -NoFix` against the current task ledger and quickstart. Ktlint,
  Detekt, Release lint, compile-all and repository unit tests passed; the final output was
  `Local CI Pipeline Completed Successfully! (66.06s)`.
- After the focused T052 rerun and acceptance-matrix update, ran `tools/local-ci.ps1 -NoFix` again.
  Ktlint, Detekt, Release lint, compile-all and repository unit tests all passed; the script ended
  with `Local CI Pipeline Completed Successfully! (26.93s)`.

### T052 acceptance matrix reconciliation (2026-10-14; partial)

- The named acceptance cases currently map to configured evidence as follows: malformed and
  missing profile/algorithm metadata is covered by `CredentialKeyDescriptorTest`,
  `CredentialRepositorySearchableMetadataTest` and
  `GetAssertionUseCaseTest.malformed summary profile fails before signing or counter mutation`;
  credential-ID lower/upper bounds and malformed encoding are covered by `CredentialIdTest`;
  partial projection failures/cancellation are covered by
  `CredentialRepositorySearchableMetadataTest`, registration projection failure/cancellation in
  `CredentialRepositoryImplTest`, and the new `sign counter projection failure returns a fixed
  error after the event append` regression; selected-key
  mismatch/no-overwrite is covered by `CredentialPersistenceCeremonyTest`; owner cleanup and
  cancellation are covered by host owner/use-case tests plus connected lifecycle and T070 transport
  disconnect cases; restart recovery is covered by the feature and `.shrunk` application runners;
  zero candidate signing and exactly one selected derivation are covered by the named selected-only
  `GetAssertionUseCaseTest` and `Fido2CryptoServiceTest`.
- These evidence sources are present and their required checks have passed in the runs recorded
  above. Source inspection of `CredentialPersistenceCeremonyTest` confirms the connected persistent
  ceremony reconstructs storage and verifies assertions for P-256, Ed25519, and ML-DSA-65; the
  older matrix wording that called this P-256-only has been corrected. The acceptance matrix remains
  partial because broader SC-004 lifecycle/error/clipboard
  teardown, Android framework callback paths, the full T022/T051/T065 reviews and external
  relying-party scenarios are still open. T052 remains unchecked; this reconciliation does not
  claim the US1 acceptance checkpoint or production security approval.
- Re-ran `:core:domain:allTests :feature:fido2:testAndroidHostTest --tests '*GetAssertionUseCaseTest'
  --tests '*CredentialRepositorySearchableMetadataTest' --tests '*CredentialRepositoryImplTest'
  --console=plain`. The targeted report files record 6 `CredentialIdTest` cases, 11
  `GetAssertionUseCaseTest` cases, 11 `CredentialRepositorySearchableMetadataTest` cases, and 13
  `CredentialRepositoryImplTest.CredentialManagementTests` cases, all with zero failures/errors/
  skips. The run directly covers the current boundary, malformed-profile, selected-only derivation,
  projection rollback/cancellation, and append-before-projection failure test sources.

### HDK coverage rerun and host transport coroutine fix (2026-10-14; passed)

- The refreshed coverage run initially exposed an uncaught host-JVM exception after a pending HID
  operation was cancelled: a detached keepalive coroutine could race cancellation while resolving
  Android build metadata, and the host JVM supplies null `Build.MANUFACTURER`/`Build.MODEL` values.
  Updated `BluetoothHidTransportImpl` to launch keepalive work as a child of the active CTAP
  operation, and made `BluetoothHidConfigProvider` treat absent build metadata as empty strings so
  its documented default config remains usable. No device-specific profile selection changed.
- Re-ran the focused `BluetoothHidTransportLifecycleTest` and `PasskeyCredentialTest` host tests;
  the Gradle command passed. Then reran
  `gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkHostCoverage
  hdkCoverageVerify --console=plain`; all configured host suites completed, including 506 FIDO2
  host tests, and the verifier passed.
- Coverage results: `core:security` 82.76% statements / 91.53% lines / 64.42% branches;
  `core:domain` 58.33% / 62.82% / 56.10%; `feature:fido2` 42.01% / 45.66% / 41.33%;
  `core:database` 20.52% statements / 18.60% lines / 40.63% branches;
  `core:common` 36.81% / 41.97% / 34.48%; `app` 34.66% / 45.71% / 27.96%;
  scoped HDK security 86.76% / 96.92% / 65.57%; repository aggregate 40.31% / 46.21% / 42.76%.
  The metric order is statements / lines / branches. Tier 2 branch percentages are reported
  separately and are not gate floors. This rerun updates T064 evidence; it does not close the
  outstanding device-reference, full acceptance, security-review, or independent-review gates.

### Local CI after coverage fix (2026-10-14; passed)

- Ran `tools/local-ci.ps1 -NoFix` after the coroutine lifetime and null-metadata fixes and the
  coverage evidence update. Ktlint passed (26.22 s), Detekt passed (51.09 s), Release lint passed
  (236.55 s), Compile All passed, and Unit Tests passed (27.31 s). The script ended with
  `Local CI Pipeline Completed Successfully! (378.56s)`.
- Re-ran `:feature:fido2:connectedAndroidDeviceTest --console=plain` on the attached Android 15/API
  35 AOSP ATD x86_64 emulator after the transport change. The connected XML report records 25
  tests, zero failures/errors/skips; this includes the pending-verification disconnect case and
  stale synchronous P-256 result suppression. The device remains unsuitable for Pixel 6a-class
  performance measurements, so T003/T066/T071 remain open.

### T022 application trim callback and signing-admission verification (2026-10-14; passed)

- Added `WalletSessionMemoryPressureCallbackTest` to the app instrumentation source set. It calls
  the actual `ChimaliApplication.onTrimMemory` override for RUNNING_CRITICAL, BACKGROUND, and
  MODERATE and verifies that the production Koin `MasterSeedProvider` generation advances on each
  callback. `:app:connectedAndroidTest --console=plain` passed all 8 discovered app instrumentation
  cases with zero failures/errors/skips. The two `ProductionAppProcessRestartTest` methods return
  without work in ordinary discovery when the dedicated fixture ID is absent; this suite is not
  used as restart evidence.
- Extended `LocalProfileLifecycleTest.severeMemoryTrimLevelsFenceWalletOwnersAndPermitEncryptedReconstruction`
  to make a real `Fido2CryptoService.sign` attempt after each severe trim without a newly admitted
  wallet owner. The attempt is cancelled before child-key derivation; the counting HDK seam remains
  at zero, the previous owner is cancelled, and an authorized later owner reconstructs the same
  encrypted wallet root. The connected `LocalProfileLifecycleTest` class passed all 7 tests with
  zero failures/errors/skips.
- Re-ran `tools/run-hdk-app-process-restart-test.ps1 -Serial emulator-5554` against the current
  app instrumentation code. The synthetic P-256 registration phase passed (`OK (1 test)`, 4.015 s)
  and fresh-process recovery/signature verification passed (`OK (1 test)`, 3.208 s); runner cleanup
  completed successfully. This fulfills T022's production-app restart and lifecycle callback items;
  T022 is marked complete. It does not substitute for external relying-party scenarios, formal
  security review, or reference-device performance measurements.

### T051 current connected acceptance suite and restart runners (2026-10-14; passed)

- Re-ran `:feature:fido2:connectedAndroidDeviceTest :app:connectedAndroidTest --console=plain`
  against the attached Android 15/API 35 AOSP ATD x86_64 emulator. The feature report at
  `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15.xml`
  records 25 tests, zero failures/errors/skips: `CredentialPersistenceCeremonyTest` (2),
  `CredentialProcessRestartTest` (3), `LocalProfileLifecycleTest` (7),
  `BluetoothHidTransportDisconnectDeviceTest` (1), `AuthenticatorDashboardComponentsTest` (6),
  and `DevelopmentToolsScreenTest` (6). The app report at
  `app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15.xml`
  records 8 tests, zero failures/errors/skips, including the production trim callback test.
- Re-ran `tools/run-hdk-process-restart-test.ps1 -Serial emulator-5554`; both preparation and
  fresh-process recovery passed (`OK (1 test)` each) with a synthetic P-256 credential. Re-ran
  `tools/run-hdk-app-process-restart-test.ps1 -Serial emulator-5554`; production-app registration
  and fresh application-process recovery both passed (`OK (1 test)` each), with cleanup succeeding.
- A repository source/script inventory found no external CTAP2 or Credential Manager relying-party
  harness to invoke. Therefore no external account was used and no external interoperability claim
  is made. The named connected suites and both isolated restart procedures satisfy T051's available
  in-repository verification; external RP acceptance, the Pixel 6a-class measurements, complete
  negative-assurance audit, and independent formal review remain open under T003/T052/T065/T066/
  T069/T071/T072.

### Local CI after current device-suite verification (2026-10-14; passed)

- The first `tools/local-ci.ps1 -NoFix` attempt found Detekt `MagicNumber` at the synthetic
  32-byte signing-input allocation in `LocalProfileLifecycleTest`. Replaced the literal with the
  named `SYNTHETIC_SIGNING_INPUT_LENGTH_BYTES` constant and reran the complete command.
- The successful rerun completed Ktlint Check, Detekt, Release lint, Compile All, and Unit Tests;
  it ended `Local CI Pipeline Completed Successfully! (65.13s)`. The CI command returned exit code 0.

### T065 use-case error detail suppression (2026-10-14; focused pass)

- The current source audit found two user-facing error mappings that still forwarded exception
  details: registration copied a caught `GeneralSecurityException` message/cause into a `CryptoError`,
  and assertion copied a sign-counter repository error message/cause. Both now return fixed error
  text with no attached cause. The registration path still stops before credential persistence;
  the assertion path stops before signing or counter mutation.
- Added synthetic marker regressions to `RegisterCredentialUseCaseTest` and
  `GetAssertionUseCaseTest`. Command
  `:feature:fido2:ktlintCheck :feature:fido2:testAndroidHostTest --tests '*RegisterCredentialUseCaseTest' --tests '*GetAssertionUseCaseTest' --console=plain`
  passed; the two suites executed 42 tests total with zero failures/errors/skips, including both
  marker cases.
- Re-ran the required `tools/local-ci.ps1 -NoFix` after these production changes. Ktlint, Detekt,
  Release lint, Compile All, and Unit Tests all passed; the script ended
  `Local CI Pipeline Completed Successfully! (339.29s)` with exit code 0.
- This closes only the two inspected error-mapping leaks. T065 remains open for complete
  application/library terminal-path cleanup and dynamic negative-assurance coverage; no claim is
  made about immutable provider, JCA, Compose, IME, clipboard, or system-owned copies. Independent
  formal review remains open under T072.

### T065 registration exception terminal paths and final local CI (2026-10-14; passed)

- Extended the registration boundary hardening to caught `IllegalArgumentException` and
  `IllegalStateException`, returning fixed validation/availability errors without exception causes.
  The cancellation catch remains first and rethrows cancellation unchanged. Added marker tests for
  both paths, complementing the provider-security-exception and assertion counter-read cases.
- Re-ran FIDO2 Ktlint and the focused registration/assertion host suites. The reports under
  `feature/fido2/build/test-results/testAndroidHostTest/` contain 44 tests total across the two
  suites, zero failures/errors/skips; all four exception-detail marker cases pass.
- Ran `tools/local-ci.ps1 -NoFix` after the final source changes. Ktlint, Detekt, Release lint,
  Compile All, and Unit Tests passed; the script ended
  `Local CI Pipeline Completed Successfully! (394.99s)` with exit code 0.
- This evidence verifies only these registration/assertion boundaries and does not close T065's
  full terminal-path and persistence/log/diagnostic audit. T065, T069, and independent review gate
  T072 remain open; production activation is not authorized.

### Assertion counter read fails closed and refreshed coverage (2026-10-14; passed)

- `CredentialRepositoryImpl.getSignCount` previously converted an Android SQL read exception into
  `Outcome.Success(0L)`, which could allow an assertion with a reset counter. It now returns a
  generic `DatabaseError` with no exception message or cause. `GetAssertionUseCase` maps that
  failure to a fixed caller-facing error and returns before signing or counter mutation.
- Added `CredentialRepositoryImplTest.sign counter read failure is returned instead of resetting to zero`;
  the focused repository/use-case tests and FIDO2 lint passed. The existing counter error case
  verifies that the assertion use case makes zero signing calls.
- Re-ran `tools/local-ci.ps1 -NoFix` after the repository behavior change. Ktlint, Detekt, Release
  lint, Compile All, and Unit Tests passed; the script ended
  `Local CI Pipeline Completed Successfully! (390.95s)` with exit code 0.
- Refreshed `hdkHostCoverage hdkCoverageVerify` on the same source state. The verifier passed with
  511 FIDO2 host tests; FIDO2 is 42.1335% statements / 41.6278% branches, core security 82.7577% /
  64.4231%, core domain 58.3301% / 56.0976%, and the repository aggregate is 40.4194% statements /
  42.9481% branches. Tier 2 statement floors also passed: database 20.5167%, common 36.8091%, app
  35.9796%; Tier 2 branch percentages are reported without a floor.
- This closes the counter-reset defect and refreshes T064 coverage evidence. T052 remains open for
  full acceptance reconciliation, and T065/T069/T072 remain open for broader security, traceability,
  and independent-review evidence.

### Current-checkout local CI revalidation (2026-10-14; passed)

- Re-ran `tools/local-ci.ps1 -NoFix` after the current security-evidence and acceptance-matrix edits.
  Ktlint, Detekt, Release lint, Compile All, and Unit Tests passed; Gradle reported 309 actionable
  tasks (9 executed, 300 up-to-date), and the script ended `Local CI Pipeline Completed Successfully!
  (31.37s)` with exit code 0.
- This revalidates the checked-in implementation on the current checkout. The remaining open items
  are unchanged: reference-device baseline/comparison (T003/T066/T071), broader authorized device
  and external scenarios (T052/T067), complete FR-013 negative-assurance audit (T065), final
  traceability closure (T069), and independent formal review disposition (T072).

### Current connected FIDO2 and app device suites (2026-10-14 local; passed)

- Re-ran `:feature:fido2:connectedAndroidDeviceTest :app:connectedAndroidTest --console=plain`.
  FIDO2 reports at `feature/fido2/build/outputs/androidTest-results/connected/androidMain/`
  record 25 tests with zero failures/errors/skips on both the Android 15/API 35 AOSP ATD emulator
  (`TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15.xml`) and the attached Moto G Stylus 5G (2022),
  Android 13 (`TEST-moto g stylus 5G (2022) - 13.xml`). Production-app `shrunkDebug` reports record
  8 tests with zero failures/errors/skips on each of those devices. The first combined emulator/app
  run completed successfully in 4m52s, followed by a separate FIDO2 run on both devices in 1m11s;
  report file timestamps are local 2026-10-14.
- The FIDO2 report includes the persistence-backed P-256, Ed25519, and ML-DSA-65 registration,
  reconstruction, and assertion ceremony; feature process/lifecycle tests; transport-disconnect
  cancellation; and UI checks. The app reports cover startup/DI, the production memory callback, and
  LeakCanary instrumentation. Their two app-restart test methods are inert without the dedicated
  fixture ID, so this connected-suite invocation does not count as app-process restart evidence.
  The Moto device adds a physical-device execution point for the FIDO2 ceremonies and app
  instrumentation; it was not designated or measured as the Pixel 6a-class reference workload and
  does not close T003/T066/T071.
- The connected reports do not exercise an external relying-party account or Android Bluetooth
  profile callback path, and do not replace the complete T052/T065 scenarios or T072 independent
  formal review.

### T065 cross-algorithm owned-secret terminal-path regressions and final local verification (2026-10-14 local; passed)

- Extended `Fido2CryptoServiceTest` with synthetic terminal-path probes. P-256 retains references
  to operation-owned provider root copies, parent scalar, local credential secret, and child factor,
  then asserts each is zeroed when child-scalar derivation fails with a provider exception and when
  it throws `CancellationException`. Ed25519 and ML-DSA signing probes fence the session after
  provider signing and verify their operation seed, wallet-root copy, and ML-DSA private-encoding
  copy are erased before cancellation returns. These probes cover the named terminal points, not
  every branch in the full system. The host report
  `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.crypto.Fido2CryptoServiceTest.xml`
  records 12 tests with zero failures/errors/skips; FIDO2 ktlint also passed.
- Ran `tools/local-ci.ps1 -NoFix` after the test change. Ktlint, Detekt, Release lint, Compile All,
  and Unit Tests passed; the script ended `Local CI Pipeline Completed Successfully! (40.81s)`
  with exit code 0.
- Refreshed `hdkHostCoverage hdkCoverageVerify` on the same source state. The verifier passed with
  514 FIDO2 host tests; FIDO2 measured 42.1447% statements / 41.6537% branches, core security
  82.7577% / 64.4231%, core domain 58.3301% / 56.0976%, and repository aggregate 40.4265% /
  42.9644%. Tier 2 statement floors passed: database 20.5167%, common 36.8091%, app 35.9796%; Tier 2
  branches remain report-only.
- T065 remains open for every remaining persistence, diagnostic, UI, QR, clipboard, and platform
  lifecycle path, and for dynamic proof beyond these focused cases. No dependency/provider copy
  erasure is claimed. T072 independent formal review remains separate and open.

### Assertion counter-write failure handling (2026-10-15 local; focused tests passed)

- `CredentialRepositoryImpl.updateSignCount` now returns a generic `DatabaseError` immediately when appending the authentication event fails, before updating the SQL counter projection. `GetAssertionUseCase` now treats a failed counter write as assertion failure; it returns a fixed generic database error and clears the operation-owned signature and authenticator-data arrays before returning or propagating cancellation/exception.
- Focused host reports `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.repository.CredentialRepositoryImplTest$CredentialManagementTests.xml` and `TEST-com.chimali.fido2.domain.usecase.GetAssertionUseCaseTest.xml` record 16 and 14 tests respectively, with zero failures, errors, or skips. Repository tests verify event-append failure does not call the SQL projection and that a SQL counter-projection exception after event append returns a fixed error without its synthetic message/cause; the use-case tests verify a failed write does not release a signed assertion, does not expose the synthetic cause marker, and cancellation propagates after signature cleanup.
- This closes the observed event-append failure handling and assertion-release defect for the tested paths. It does not establish atomicity between the append-only event and SQL projection for every persistence failure, or complete FR-013 negative assurance; T052, T065, and T072 remain open.

### Assertion counter SQL projection failure redaction (2026-10-15 local; focused pass)

- `CredentialRepositoryImpl.updateSignCount` now logs a static message and returns a fixed `DatabaseError` without the caught SQL exception's message or cause when the append-only event succeeds but the SQL counter projection update fails. The append-only event is intentionally retained; the assertion use case fails closed and clears its signed output.
- `CredentialRepositoryImplTest.sign counter projection failure returns a fixed error after the event append` injects a synthetic SQL exception and checks event-before-projection call order, fixed error text, and absent exception cause/marker. The focused repository/use-case host suites and FIDO2 ktlint passed.
- This handles error disclosure at this counter-projection boundary; it does not make the event append and projection write one transaction or establish complete FR-013 negative assurance.

### Authenticator signCount unsigned-32-bit boundaries (2026-10-15 local; focused pass)

- WebAuthn authenticator data encodes `signCount` as a four-byte unsigned big-endian integer ([W3C WebAuthn Level 3, authenticator data](https://www.w3.org/TR/webauthn-3/#sctn-authenticator-data)). `GetAssertionUseCase` now rejects negative, exhausted (`0xFFFFFFFF`), and wider persisted counters before signing or mutating storage; it permits the final representable increment from `0xFFFFFFFE` to `0xFFFFFFFF`.
- `GetAssertionUseCaseTest.last representable authenticator counter is encoded as unsigned big endian` checks the four `0xFF` counter bytes in the signed payload. `exhausted or corrupt authenticator counter fails before signing and mutation` checks `-1`, `0xFFFFFFFF`, and `0x100000000`; all 16 use-case host tests and FIDO2 ktlint passed.
- This closes the identified wire-width overflow boundary. It does not close the broader T052 acceptance matrix or independently review counter policy.

### Current signCount-boundary, connected-device, coverage, and local CI verification (2026-10-15 local; passed)

- The current-source :feature:fido2:connectedAndroidDeviceTest completed successfully on both the Android 15/API 35 AOSP ATD emulator and the physical Moto G Stylus 5G (2022), Android 13. Refreshed XML reports at feature/fido2/build/outputs/androidTest-results/connected/androidMain/ record 25 tests each, with zero failures, errors, or skips. The Moto was connected and unlocked before the run; its USB stay-awake override was reset afterward.
- Refreshed hdkHostCoverage hdkCoverageVerify after the unsigned-32-bit counter tests. Verification passed with 520 FIDO2 host tests: FIDO2 42.2705% statements / 41.8262% branches; core security 82.7577% / 64.4231%; core domain 58.3301% / 56.0976%; repository aggregate 40.5069% / 43.0714%. Tier 2 statement floors passed: database 20.5167%, common 36.8091%, and app 35.9796%; Tier 2 branch rates remain report-only.
- Re-ran tools/local-ci.ps1 -NoFix after the signCount range validation and boundary regressions. Ktlint, Detekt, Release lint, Compile All, and Unit Tests passed; the script ended Local CI Pipeline Completed Successfully! (298.64s) with exit code 0.
- Separately ran :app:connectedAndroidTest against the same source revision on both devices. The refreshed reports at app/build/outputs/androidTest-results/connected/shrunkDebug/ record 8 tests each, with zero failures, errors, or skips. This executes the production memory-callback instrumentation; the fixture-gated restart test methods remain inert in ordinary discovery, so the dedicated restart runner remains the restart evidence.
- These results verify execution on the emulator and attached Moto, but do not supply Pixel 6a-class baseline/comparison measurements, Bluetooth request-to-response timing, full lifecycle/clipboard acceptance, external relying-party validation, or independent formal review. T003/T066/T067/T069/T071/T072 remain open; T052 and T065 remain open for complete acceptance and negative-assurance closure.
### T067 current-source clean-process recovery and guide reconciliation (2026-10-15; passed)

- Confirmed the actual configured device task remains :feature:fido2:connectedAndroidDeviceTest and the production app task is :app:connectedAndroidTest. The latest device XML paths and counts are recorded above: 25 FIDO2 tests and 8 app instrumentation tests on each attached device. Coverage report paths remain the module XML/HTML and repository XML listed in the coverage evidence near the top of this file.
- Re-ran tools/run-hdk-process-restart-test.ps1 -Serial emulator-5554 against the current source. Synthetic credential preparation passed (1 test); a force-stop and fresh instrumentation process recovered the stored credential and verified an assertion (1 test). The runner reported process-restart recovery passed.
- Re-ran tools/run-hdk-app-process-restart-test.ps1 -Serial emulator-5554 against the current shrunk production app. Synthetic registration passed (1 test), followed by fresh application-process recovery and assertion verification (1 test). The runner completed cleanup of its isolated packages and fixture.
- The frozen independent fixture remains at core/security/src/androidHostTest/resources/hdk/local-profile-v1.json; the synthetic restart fixtures are generated by the two scripts and stored only in their disposable test locations. No personal installation or real recovery material was used.
- The repository contains no external CTAP2 or Credential Manager relying-party harness, so no external account scenario was run and no external interoperability claim is made. T067 remains open for any guide scenarios still unexecuted; this entry closes the current-source in-repository restart and device-suite reconciliation only.

### T065 queued mnemonic-import cancellation cleanup (2026-10-15; focused and device checks passed)

- Source review found that a recovery import cancelled before its Main-dispatcher coroutine body began would skip the coroutine finally block. Added a ViewModel-owned reference to the transferred mutable import buffer, cancellation-before-erasure on replacement/disposal, immediate zeroing in onCleared, and an invokeOnCompletion fallback for a queued job that never enters the body.
- Added DevToolsViewModelTest.disposing before scheduled import starts erases the transferred input. The first regression attempt showed asynchronous completion alone did not erase before ViewModel disposal returned; the implementation was strengthened to erase synchronously at the owner boundary. The final focused Ktlint and DevToolsViewModelTest host invocation passed: 16 tests, zero failures/errors/skips.
- After the source fix, :feature:fido2:connectedAndroidDeviceTest passed on Android 15/API 35 emulator and Moto G Stylus 5G (2022), Android 13; each current XML report records 25 tests, zero failures/errors/skips. The suite includes the configured DevelopmentTools Compose checks.
- This closes the identified queued-import input-buffer ownership path only. It does not prove cleanup of every UI/platform copy, all clipboard/QR paths, or all FR-013 terminal paths; T065 and independent review T072 remain open.

### Final verification after queued import cleanup (2026-10-15; passed)

- Current-source host verification: :feature:fido2:ktlintCheck and the focused DevToolsViewModelTest run passed; its two configured XML suites record 16 total tests with zero failures/errors/skips. The new regression proves a transferred mutable recovery phrase is erased immediately when ViewModel disposal cancels its queued import before the coroutine body starts.
- Current-source device verification: :feature:fido2:connectedAndroidDeviceTest passed on Android 15/API 35 emulator and Moto G Stylus 5G (2022), Android 13; both XML reports record 25 tests with zero failures/errors/skips. :app:connectedAndroidTest then passed on both devices; both shrunkDebug XML reports record 8 tests with zero failures/errors/skips.
- Current-source restart verification: tools/run-hdk-process-restart-test.ps1 -Serial emulator-5554 passed synthetic credential preparation and fresh-process recovery (1 test each). tools/run-hdk-app-process-restart-test.ps1 -Serial emulator-5554 passed production-app synthetic registration and fresh-app-process recovery (1 test each); isolated package and fixture cleanup completed.
- Ran tools/local-ci.ps1 -NoFix after the source change. Ktlint, Detekt, Release lint, Compile All, and Unit Tests passed; Local CI Pipeline Completed Successfully! (201.4s), exit code 0.
- Refreshed hdkHostCoverage hdkCoverageVerify after adding the regression. Verification passed with 521 FIDO2 host tests: FIDO2 42.3170% statements / 41.8647% branches; core security 82.7577% / 64.4231%; core domain 58.3301% / 56.0976%; repository aggregate 40.5371% / 43.0945%. Tier 2 statement floors passed: database 20.5167%, common 36.8091%, and app 35.9796%; Tier 2 branch rates remain report-only.
- The queued-import owner gap is closed for the tested ViewModel cancellation path. T065 remains open for the wider terminal-path and platform-copy audit; Pixel-class baseline/comparison (T003/T066/T071), complete acceptance reconciliation (T052/T067), final traceability (T069), and independent formal review (T072) remain open.

### T065 pinned provider API boundary and latest Moto verification (2026-10-15; source/device checks passed)

- Inspected the resolved `bcprov-jdk18on:1.80` binary API for `Ed25519PrivateKeyParameters`, `MLDSAPrivateKey`, `MLDSAPrivateKeyParameters`, and `BCMLDSAPrivateKey`. None exposes a destroy/clear method; ML-DSA parameter objects include private byte-array fields. In the current adapters, these objects stay local to key generation/signing calls and are not retained in application caches/models. The app erases its owned seed and returned private-encoding copies; provider-internal arrays and JCA/ICU/runtime copies remain outside application erasure control until provider/runtime collection.
- The current API boundary is consistent with the source: P-256 signing converts the owned private scalar bytes into an immutable `BigInteger` and Android platform JCA key objects; Ed25519 imports an owned seed into a BC lightweight parameter object; ML-DSA imports the owned `xi` seed through the BC 1.80 JCA key factory and zeroes the returned encoding copy. `Bip39MasterSeedGenerator` passes a `CharBuffer` view through Android ICU NFKD into its mutable builder and runs PBKDF2-HMAC-SHA512 with `Mac`/`SecretKeySpec` (no `PBEKeySpec` or `SecretKeyFactory`). ICU/JCA/BC internals may create copies that application code cannot inspect or erase. These are lifetime/retention facts, not a claim that dependency objects or copies are wiped.
- After the user unlocked and reconnected the physical Moto, reran `:feature:fido2:connectedAndroidDeviceTest :app:connectedAndroidTest` with the Moto selected as the sole test device. Build succeeded; the Moto Android 13 reports record 25 FIDO2 device tests and 8 shrunk-app instrumentation tests, all passing with zero failures/errors/skips. Reports: `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-moto g stylus 5G (2022) - 13.xml` and `app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-moto g stylus 5G (2022) - 13.xml`.
- This closes the pinned provider API inventory and adds a fresh Moto run, but does not prove all error/cancellation branches, platform-copy absence, clipboard/QR lifecycle behavior, or the complete FR-013 negative assurance. T065 and T072 remain open pending broader evidence and independent formal review.

### T052 US1 acceptance checkpoint closure (2026-10-15; current-source evidence reconciled)

The T052 acceptance matrix now maps every explicitly named acceptance category and the defined US1 edge cases to configured evidence. Current-source device reports for the Moto Android 13 run list the named 25 FIDO2 tests and 8 production-app instrumentation tests recorded in the preceding section; all passed with zero failures/errors/skips. Host suites, two dedicated restart runners, and coverage/CI results are recorded above.

| T052 acceptance point | Evidence and verified outcome |
| --- | --- |
| Malformed/missing or inconsistent algorithm/profile metadata | `CredentialKeyDescriptorTest` rejects unsupported/mismatched profiles; `CredentialRepositorySearchableMetadataTest.credential summary rejects malformed algorithm profile metadata` rejects malformed stored metadata; `GetAssertionUseCaseTest.malformed summary profile fails before signing or counter mutation` fails closed. The current configured host suites passed. |
| Input boundaries | `CredentialIdTest` covers empty/size bounds and malformed/equivalent padded/unpadded Base64URL; `HkdfSha256Test` covers empty salt, RFC 5869 maximum output and the full-ID context; `HdkP256Test` checks malformed secrets/scalars/points and frozen child vectors for indices 0, 1, 0x80000000 and 0xFFFFFFFF; `P256ParentKeyDerivationTest` checks the published RFC vector and sampler acceptance/exhaustion cleanup. |
| Partial persistence failure | `CredentialRepositorySearchableMetadataTest` verifies projection deletion rollback on insert preparation failure and cancellation; the connected `LocalProfileLifecycleTest.encryptedProjectionReplacementRollsBackEarlierDeletionWhenInsertFails` exercises SQLiteMC; `CredentialRepositoryImplTest` and `GetAssertionUseCaseTest` verify event/projection and counter-write failure behavior, redaction, and that failure does not return a signed assertion. These scenarios passed in the recorded host/device runs. |
| Registered-key mismatch/no overwrite | `GetAssertionUseCaseTest.registered public key mismatch fails without mutating the selected key or sign counter` verifies fail-closed use-case behavior. `CredentialPersistenceCeremonyTest.p256RegistrationAndAssertionRecoverFromEncryptedEventAndProjectionStores` exercises persisted-key mismatch through the connected production repository and confirms the recorded key/counter are not repaired or advanced. |
| Cleanup/cancellation | `Fido2CryptoServiceTest` checks operation-owned P-256/Ed25519/ML-DSA buffer erasure on injected failure or session fencing; `GetAssertionUseCaseTest` checks persistence/signing cancellation cleanup; `DevToolsViewModelTest` covers mutable mnemonic replacement/hide/disposal and queued import cancellation; the connected `BluetoothHidTransportDisconnectDeviceTest.hidDisconnectCancelsPendingAuthenticationAndErasesTheProductionWalletOwner` exercises production provider/transport cancellation. The app `LeakCanaryMnemonicOwnerTest` also passed in the current 8-test instrumentation run. |
| Stable OS-process restart/recovery | `tools/run-hdk-process-restart-test.ps1` passed synthetic P-256 prepare and fresh-instrumentation-process recovery; `tools/run-hdk-app-process-restart-test.ps1` passed synthetic production-app registration and fresh-app-process recovery. The current connected suite also includes its fixture-gated restart methods, which are not counted as restart evidence without the dedicated runner. `CredentialPersistenceCeremonyTest.persistentCeremoniesRecoverForEverySupportedSignatureAlgorithm` verifies reconstructed storage and assertions for P-256, Ed25519 and ML-DSA-65. |
| Zero candidate derivations and exactly one selected assertion derivation | `GetAssertionUseCaseTest.assertion signs exactly the selected descriptor once without hydrating candidate credentials` uses three candidates and verifies one sign call for the selected descriptor, zero sign calls for either unselected candidate, and zero credential hydration. `Fido2CryptoServiceTest.signing discards output when session generation is fenced during provider work` additionally observes exactly one P-256 child derivation for the selected signing operation. |
| Remaining specified US1 edge cases | Clean encrypted-store bootstrap, provider reconstruction, all-algorithm persistence-backed registration/assertion, signing authorization boundary, old-short-index collision separation, and current signature verification are covered by the named connected, host and frozen-vector tests above. No external relying-party interoperability is claimed. |

T052 is complete for its enumerated US1 acceptance matrix and story checkpoint. This does not close the broader, separately tracked all-path FR-013 negative-assurance audit (T065), remaining quickstart scenarios (T067), reference-device performance/baseline measurements (T003/T066/T071), final traceability (T069), or independent formal review/finding closure required before activation (T072).

### T065 biometric diagnostic minimization (2026-10-15; focused host pass)

- Current-source log scanning found `DevToolsViewModel.handleBiometricError` interpolated a free-form platform message into a warning log. The message remains available to the user-facing error state, but the log now records only the numeric error code.
- Ran `:feature:fido2:ktlintCheck :feature:fido2:testAndroidHostTest --tests '*DevToolsViewModelTest' --console=plain`; it passed. `TEST-com.chimali.fido2.presentation.viewmodel.DevToolsViewModelTest.xml` records 13 tests and the nested biometric lockout report records 3; both have zero failures, errors, or skips.
- A scoped `rg` review found exception-message logging in Bluetooth transport/warm-up paths; those do not receive wallet roots, seeds, derivation secrets, or private-key working arrays. The reviewed crypto, mnemonic import, assertion counter, and CTAP error paths continue to use static diagnostics or the configured privacy scrubber. This static scan does not prove every indirect log input or complete FR-013 negative assurance; T065 and T072 remain open.

### T065 current-source device ownership and diagnostic verification (2026-10-15; final Moto reruns passed)

- Re-ran `:feature:fido2:connectedAndroidDeviceTest :app:connectedAndroidTest --console=plain` with `ANDROID_SERIAL=ZY22G3FJSX`. The Moto FIDO2 XML report records 25 tests, zero failures/errors/skips. Its named cases include the production wallet owner/transport disconnect cancellation, memory trim and encrypted reconstruction, QR hide/clear, mutable manual phrase transfer, and authenticated mnemonic reveal/disposal.
- The first app instrumentation phase had a single `StartupTest.appCreatesAndAttachesComposeContentOnStartup` failure at `ComposeView.isShown`; all other seven tests passed. The Moto was awake and unlocked but had returned to the launcher at inspection. Reran the startup test alone with `:app:connectedAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.chimali.StartupTest" --console=plain`; the one-test report passed. Then reran the full `:app:connectedAndroidTest` suite; the current XML report records 8 tests, zero failures/errors/skips, including `LeakCanaryMnemonicOwnerTest.activityDisposalClearsSyntheticMnemonicOwnerWithoutRetainingLifecycleObjects`. The initial non-reproducible assertion failure is retained here rather than counted as a pass.
- The source audit now records exact Android ICU/JCA normalization and PBKDF2 APIs, P-256 Android JCA imports, BC 1.80 Ed25519/ML-DSA object APIs, Java `BigInteger` limits, Qrose 1.1.2 / CameraX 1.4.1 / ML Kit Barcode 17.3.0 QR ownership, and Android `ClipData` sensitivity/60-second clipboard clearing. It distinguishes app-owned mutable buffers from provider, UI, camera, IME, clipboard, and runtime copies. No dependency/system-copy erasure is claimed.
- The scoped host/device evidence and source-level terminal-path review support the implemented application-owned cleanup and negative-assurance controls. Remaining limits are explicit platform/provider copies, no direct camera-frame QR scanner test, and the requirement for independent formal disposition under T072. T065 is complete as the implementation/source/device boundary audit; T072 remains a separate mandatory activation gate.

### T065 final owned-buffer terminal-path recheck (2026-10-15; focused host and Moto passes)

- The current-source audit found that RFC 9180 parent derivation copied an accepted candidate into an owned private-scalar buffer before public-point serialization, without clearing that copy if serialization failed. The copy is now held under `try/finally` and transferred only after successful result construction; the existing candidate buffer remains erased by its enclosing `finally`.
- `:core:security:ktlintCheck :core:security:testAndroidHostTest --tests '*P256ParentKeyDerivationTest' --console=plain` passed. `TEST-com.chimali.core.security.hdkeys.P256ParentKeyDerivationTest.xml` records 3 tests, zero failures/errors/skips.
- The Moto rerun (`ZY22G3FJSX`) produced 25 FIDO2 device tests and 8 app instrumentation tests, zero failures/errors/skips. Reports: `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-moto g stylus 5G (2022) - 13.xml` and `app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-moto g stylus 5G (2022) - 13.xml`.
- The host suite exercises the RFC vector and candidate sampler paths; the exceptional public-point serialization branch is verified by source ownership review, not a failure-injected test. Provider, JVM, Compose, camera, IME, clipboard, and operating-system copies are not claimed erasable. T065 closes the implementation/source/device audit; independent formal disposition T072 remains open and activation is not authorized.
- Final 	ools/local-ci.ps1 -NoFix passed after the cleanup helper extraction: Ktlint, Detekt, release lint, compile-all, and configured unit tests all completed successfully (384.83s).

### T067 quickstart reconciliation checkpoint (2026-10-15)

- Confirmed task discovery against the current Gradle model: FIDO2 host tests use `:feature:fido2:testAndroidHostTest`, its configured device suite uses `:feature:fido2:connectedAndroidDeviceTest`, the production app instrumentation suite uses `:app:connectedAndroidTest`, and coverage uses `hdkHostCoverage` / `hdkCoverageVerify` from `tools/hdk-coverage.init.gradle`.
- Frozen local-profile fixture: `core/security/src/androidHostTest/resources/hdk/local-profile-v1.json`. Synthetic disposable restart fixtures are generated by `tools/run-hdk-process-restart-test.ps1` and `tools/run-hdk-app-process-restart-test.ps1`; both dedicated runners passed on the emulator and clean up their isolated stores/packages. The connected Moto run was limited to instrumentation-owned test data and did not reset a personal app installation.
- Current device reports: `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-moto g stylus 5G (2022) - 13.xml` (25 passing tests) and `app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-moto g stylus 5G (2022) - 13.xml` (8 passing tests). Coverage paths and floors are listed under T064 above: per-module `build/reports/hdk-coverage/hdkModuleCoverage.xml`, repository `build/reports/hdk-coverage/repository.xml`, plus module CSV/HTML. The final `tools/local-ci.ps1 -NoFix` run after the source cleanup fix passed in 384.83 seconds.
- The repository has no external CTAP2 or Credential Manager relying-party harness; no disposable external account was available or used, and no external interoperability is claimed. Pixel 6a-class performance evidence and independent formal T072 disposition also remain outstanding. T067 therefore remains unchecked for those unexecuted quickstart scenarios; this checkpoint closes only the task-name, fixture, report-path, and connected-device discovery reconciliation.

### Current-source coverage gate refresh (2026-10-15; passed)

- Re-ran `.\gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkHostCoverage hdkCoverageVerify --console=plain` after the parent-scalar cleanup helper change. All configured host/unit suites passed and `hdkCoverageVerify` passed without exclusions or threshold changes.
- Current gates: core security 81.9121% statements / 63.9241% branches (43 tests); core domain 58.3301% / 56.0976% (47 tests); FIDO2 42.3013% / 41.8647% (521 tests). Tier 2 statements: database 20.5167% (branch 40.625%), common 36.8091% (branch 34.4828%), app 35.9796% (branch 27.9570%). Repository aggregate: 40.5298% statements / 43.0827% branches.
- Reports: each affected module's `build/reports/hdk-coverage/hdkModuleCoverage.xml`; repository `build/reports/hdk-coverage/repository.xml`; FIDO2 CSV at `feature/fido2/build/reports/hdk-coverage/hdkModuleCoverage.csv`.

### T067 retired-runtime-symbol scan disposition (2026-10-15)

- Re-ran the quickstart retired-symbol query over `core` and `feature`. Its only `private_key_alias` matches are lines in `feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/Fido2Database/11.sqm`, where the historical snake-case schema migration creates/copies the retired column. Earlier migration `7.sqm` carries its camel-case predecessor. The current `Fido2Database.sq` omits the alias field, and current production Kotlin/SQL queries do not access it.
- These migration files are retained as historical SQLDelight schema artifacts, not current derivation adapters; their presence does not claim database-upgrade compatibility. The clean-install/current-schema tests remain the supported evidence. The quickstart now directs reviewers to classify these matches separately from runtime code.

### T067 physical Moto process-restart validation (2026-10-15; passed)

- With the connected Moto G Stylus 5G (2022), serial `ZY22G3FJSX`, awake and unlocked, ran `tools/run-hdk-process-restart-test.ps1 -Serial ZY22G3FJSX`. Synthetic P-256 preparation and fresh instrumentation-process recovery each reported `OK (1 test)`; the runner confirmed successful signature recovery and invoked its fixture cleanup.
- Ran `tools/run-hdk-app-process-restart-test.ps1 -Serial ZY22G3FJSX`. The isolated `shrunkDebug` production application registered the synthetic P-256 credential, was force-stopped, then recovered and signed in a fresh application process; both instrumentation phases reported `OK (1 test)`. The runner's cleanup completed successfully.
- Afterward, queried installed packages on the same device: `com.chimali.shrunk` and `com.chimali.shrunk.test` were absent; the user's normal `com.chimali` package was not targeted. The `com.chimali.fido2.test` instrumentation package remains installed, as expected by its runner.
- These runs add physical-device disposable persistence and process-restart evidence. T067 remains open because the quickstart's external relying-party ceremony requires a CTAP2/Credential Manager harness and disposable account that are not available in this repository/session. No external interoperability is claimed.

### Speckit convergence review checkpoint (2026-10-15)

- Reconciled the current source/evidence against 19 functional requirements, 10 buildable success criteria, both user stories and their acceptance scenarios, plan decisions, and the amended constitution. No additional uncovered code requirement was found beyond open work already represented by T003, T066, T067, T069, T071, and T072; no duplicate convergence tasks were appended.
- Updated `documentation-review.md` to reflect T052 and T065 closure while preserving the distinct T069 traceability and T072 independent-review gates. The historical `private_key_alias` schema migration reference is now classified separately from active runtime APIs; the current schema and query code omit it.
- This is not full feature convergence: reference-device/performance evidence, remaining quickstart scenarios, final traceability, and independent formal disposition remain open. No activation or security signoff is claimed.

### T069 requirement crosswalk (updated 2026-10-16)

The matrix was assembled on 2026-10-15 and reconciled on 2026-10-16. It links each feature requirement to its recorded evidence and current disposition. Earlier dated entries retain their historical outcomes; the latest T069/T083 reconciliation below controls current status, including the deferred end-to-end investigation and active internal performance work.

| ID | Current evidence | Disposition |
| --- | --- | --- |
| FR-001 | `contracts/local-key-derivation.md`; `HdkP256Test`; `P256ParentKeyDerivationTest`; T005/T072/T075 review records | Local profile and published-primitive vectors are documented and tested; the independent formal acceptance is limited to the named local profile and reviewed source scope. |
| FR-002 | `WalletMasterSeedProvider`; registration flow; `CredentialPersistenceCeremonyTest` | Current registration uses the current profile; persistence-backed ceremony evidence is recorded under T052. |
| FR-003 | Credential descriptor/recovery metadata; repository/event/projection tests; `LocalProfileLifecycleTest` | Required identity is persisted/reconstructed and invalid metadata fails closed; see T052 acceptance matrix. |
| FR-004 | Descriptor-selected registration/assertion paths; persisted-key mismatch cases in `GetAssertionUseCaseTest` and `CredentialPersistenceCeremonyTest` | Profile identity remains explicit and key mismatch does not overwrite the recorded key; see T052. |
| FR-005 | `HkdfSha256Test`; frozen full-ID vectors and shortened-index collision case in `HdkP256Test` | Full decoded identifier binding and separation are covered by independent fixtures and host tests. |
| FR-006 | `contracts/local-key-derivation.md`; `quickstart.md`; recovery research and comparison documents | Recovery requirements name the mnemonic and retained credential/profile metadata and do not promise discovery or draft-provided storage. |
| FR-007 | Local derivation contract; `HdkP256Test`; `P256ParentKeyDerivationTest`; frozen fixture provenance; T072/T075 review records | Scalar mapping, parent context, unsigned index range, and local input substitution are specified, tested, and formally accepted within the named local profile. |
| FR-008 | `HdkP256Test`; `P256ParentKeyDerivationTest`; `CredentialKeyDescriptorTest`; `CredentialIdTest` | Malformed inputs, invalid keys, and boundaries are covered by the configured host suites. |
| FR-009 | `LocalProfileLifecycleTest`; `CredentialPersistenceCeremonyTest`; feature/app process-restart runners | Synthetic empty-store initialization, encrypted persistence, registration/assertion, and restart recovery pass on configured devices; detailed reports are cited above. |
| FR-010 | `MultiAlgorithmIntegrationTest`; `CredentialPersistenceCeremonyTest.persistentCeremoniesRecoverForEverySupportedSignatureAlgorithm` | P-256, Ed25519, and ML-DSA-65 persistence-backed ceremonies and signature checks pass in the recorded suites. |
| FR-011 | T062 retired-runtime-symbol scan, clean-install scope update, and `documentation-review.md` | No active retired remote derivation path remains; FIDO2 SQLDelight migration history was removed, and current schema/Kotlin call sites omit the alias field. No database-upgrade support is claimed. |
| FR-012 | `contracts/credential-lifecycle.md`; T035 source/host/device lifecycle evidence; `BluetoothHidTransportDisconnectDeviceTest` | Parent protection and operation-owner rules are documented and the named lifecycle/disconnect cases pass. |
| FR-013 | T065 final owned-buffer audit in `security-review.md`; diagnostic/source scans; connected ownership and LeakCanary cases; T072/T075 review records | Application-owned cleanup and negative-assurance evidence are complete within documented limits; provider/runtime copies are not claimed erasable. The supplemental UV review and its conditional remediation findings are closed; no activation event is claimed. |
| FR-014 | T056 BRD/TRD sync; T057/T058 recovery and comparison docs; changelog and backlog links | Current documentation is synchronized and historical statements remain labeled as history; see `documentation-review.md`. |
| FR-015 | Constitution 3.1.0; `constitution-amendment.md`; T061 review record | The 3.0.0 local-profile amendment and later user-authorized 3.1.0 performance-budget split retain rationale/date and scoped clean-install authorization; see `documentation-review.md`. |
| FR-016 | Pinned independent fixture provenance; `local-profile-v1.json`; feature/app process-restart runners | Expected values and synthetic recovery are independently checked; restart evidence is P-256, while all-algorithm persisted ceremonies are covered separately. |
| FR-017 | `GetAssertionUseCaseTest.assertion signs exactly the selected descriptor once without hydrating candidate credentials`; `Fido2CryptoServiceTest`; matched ASUS report | Candidate listing performs no derivation and one selected signing derivation is observed. Matched release app-side timings are recorded under T066/T071/T074; the boundary is local HID API acceptance. |
| FR-018 | `docs/backlog/openid4vci-hdk-issuance-investigation.md`; T059/T062 link audit | Separate issuance research item and current-document links are present; no issuance implementation is claimed. |
| FR-019 | T054/T062 removal audit; current runtime-symbol scan; current P-256 contract | Retired implementations and obsolete active tests/APIs were removed; retained helpers have current uses. |
| SC-001 | Empty encrypted-store `LocalProfileLifecycleTest`; persistence-backed ceremony; current connected reports; T067 local RP evidence below | Synthetic clean-install/storage acceptance passes on the emulator and Moto; the loopback WebAuthn RP registered and verified assertions before and after process restart. No production relying-party interoperability is claimed. |
| SC-002 | `CredentialPersistenceCeremonyTest`; both process-restart runners | Current P-256 registration/assertion succeeds before and after restart, including physical Moto validation. |
| SC-003 | `CredentialPersistenceCeremonyTest.persistentCeremoniesRecoverForEverySupportedSignatureAlgorithm`; `MultiAlgorithmIntegrationTest` | All supported signature branches recover and assert after encrypted repository reconstruction; app-process restart remains demonstrated for P-256. |
| SC-004 | T052 failure/cancellation matrix; T065 cleanup audit; host/device cancellation and LeakCanary evidence; T072/T075 review artifacts | Defined covered failure and cancellation cases pass; original and supplemental formal dispositions are recorded for the named local profile and their reviewed source scopes. |
| SC-005 | `documentation-review.md`; T062 source/dependency/claims audit | Current inventory contains no claim of completed issuer interoperability or unsupported hardware custody. |
| SC-006 | `HdkP256Test` frozen full-ID collision fixture; T052 acceptance matrix | Distinct full identifiers sharing the old shortened index derive distinct profile results. |
| SC-007 | T003/T066/T071/T074/T082 baseline/current reports; instrumented ASUS captures 1–3 below; T084/T085; the superseded T084 investigation, now replaced by [Feature 060](../060-fido2-latency-measurement/implementation-evidence.md) | **Partial.** Baseline/current startup, size, memory, RP flow and local handoff evidence remain recorded. User accepted the observed UX and deferred T084's end-to-end measurement investigation. T085 is deferred to the later performance phase: MakeCredential internal values 229.253–284.752 ms exceed 200 ms and the all-operation sample campaign is incomplete. Candidate-only selection and one selected derivation retain source/host/trace evidence; no separate physical derivation counter or numeric end-to-end pass is claimed. |
| SC-008 | Linked OpenID4VCI backlog item and T059 link review | Concrete research questions, dependencies, and completion criteria are present. |
| SC-009 | T054/T062 dependency, source, and documentation audit | No active old-only implementation, compatibility layer, or acceptance test remains; one current local P-256 contract is linked. |
| SC-010 | T061 constitution comparison; amendment/version/date evidence; `documentation-review.md` | Constitution review is complete and current obligations agree with the local profile; version, rationale, date, and clean-install scope are recorded. |

T067 and the scoped T072/T075 review/finding closure are recorded, including remediation of the supplemental UV review's named test and CI conditions. T003/T066/T071/T074/T082 evidence is recorded. T069/T083 reconcile the documents with T084 deferred and T085 active; they do not establish full SC-007 compliance or a deployment/activation event.

### Clean-install schema cleanup and HID latency boundary update (2026-10-15)

- Applied the user's clean-install-only decision: Chimali is unreleased, so FIDO2 database upgrade compatibility is out of scope and retaining SQLDelight migration history is unnecessary technical debt. Removed all eleven `feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/Fido2Database/*.sqm` files. This supersedes the earlier T040 instruction and T067 note that kept those files as history; historical checkpoint text above records what was observed at that time.
- Verified the current `Fido2Database.sq` plus `feature/fido2/src` scan contains no `private_key_alias` or `credentialAlias` matches. `:feature:fido2:generateCommonMainFido2DatabaseInterface` and `:feature:fido2:verifyCommonMainFido2DatabaseMigration` completed successfully with no `.sqm` files present; `:feature:fido2:compileAndroidMain` and `:feature:fido2:ktlintCheck` also passed after the HID timing edit.
- Updated the quickstart and documentation review to state clean-install-only scope, remove the alias from the broad runtime retired-symbol scan, and check the current schema/call sites directly. These checks do not establish database-upgrade compatibility.
- Extended `LatencyProfiler` through final response report handoff to `BluetoothHidDeviceWrapper.sendReport`; the result now reports a pass only when all final reports were accepted and the 200 ms budget is met. This boundary records local API acceptance, not remote-host receipt or processing. Reference-device measurements remain open under T003/T066/T071.
- `.\tools\local-ci.ps1 -NoFix` completed successfully after these changes (Ktlint, Detekt, release lint, compile all, and unit tests; 529.87 seconds). The FIDO2 connected Android device suite also passed on Moto G Stylus 5G (2022), Android 13, and API 35 ATD emulator: each JUnit XML report records 25 tests, 0 failures, 0 errors, and 0 skipped, including the named HID-disconnect and Compose UI tests (`feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-moto g stylus 5G (2022) - 13.xml` and `TEST-dev35_aosp_atd_x86_64_Pixel_2(AVD) - 15.xml`).
- T073 is complete. T067 remains open for the external relying-party scenario; T069/T072 and performance gates remain open.

### Current app startup and memory samples (2026-10-15; partial, not baseline-comparable yet)

- On the physical Moto G Stylus 5G (2022), Android 13, installed the disposable `com.chimali.shrunk` package from the current `shrunkDebug` APK (`113,088,402` bytes; SHA-256 `E861DF75CAE5BEAC07D77FFF1FFB6A48B6CE18B5565BC8B3E98AF1A20A428A17`). The normal `com.chimali` package was not touched. Granted runtime permissions only to the disposable package, then measured five forced-stop/cold activity launches with `adb shell am start -S -W -n com.chimali.shrunk/com.chimali.MainActivity`: 1,015, 991, 1,024, 987, and 993 ms (`ThisTime` was not emitted; Android reported `TotalTime`/`WaitTime`). Median `TotalTime` was 993 ms.
- After one launch and a 10-second foreground interval, `dumpsys meminfo com.chimali.shrunk` reported 156,290 KB PSS and 247,740 KB RSS. This is one post-start snapshot, not peak/session memory or a LeakCanary run. These device-specific observations do not establish the constitution's memory budget or the 200 ms Bluetooth request-to-response target.
- This pilot entry is superseded by the matched baseline/current samples below. It does not close T003, T066, or T071.

### Matched baseline/current startup and memory samples (2026-10-15; partial)

- Built `:app:assembleShrunkDebug` from pre-feature commit `8b4d85ad` in detached worktree `C:\Users\octav\.codex\worktrees\hdk-baseline\Chimali` with the repository Gradle wrapper and configured Android SDK. Baseline build succeeded in 23m51s (424 tasks). Baseline APK: 62,414,123 bytes; SHA-256 `91F8A93E6E88C8E5C1594B33A963CDCD187ADEE9C76A1823E798CD2FBE8B0649`. Current APK: 113,088,402 bytes; SHA-256 `E861DF75CAE5BEAC07D77FFF1FFB6A48B6CE18B5565BC8B3E98AF1A20A428A17`. The absolute increase is 50,674,279 bytes (81.2%). This is the disposable `shrunkDebug` test APK, not the release AAB governed by the 50 MB constitution budget. The pre-feature `:app:bundleRelease` build succeeded (423 tasks; 17m39s); its AAB is 40,984,305 bytes with SHA-256 `30B162015AD65FC33D10D33851998E3AA42F76667DD488C5CD6E12A0A0BF3684`. The current `:app:bundleRelease` build succeeded (423 tasks; 22m33s); its AAB is 40,988,409 bytes with SHA-256 `A711D31EA87F2968CC5A9A0B2A9D229B393EC6B3B38D91F7DDFCC4739017F5FE`. Current is +4,104 bytes (+0.010%) and is below the 50 MB base AAB cap; the measured increase is below the +500 KB review trigger.
- Installed each APK in turn as disposable `com.chimali.shrunk` on the same physical Moto G Stylus 5G (2022), Android 13 (`ZY22G3FJSX`), preserving the package's granted test permissions. The ordinary app package was not modified. For each artifact ran five `adb shell am start -S -W -n com.chimali.shrunk/com.chimali.MainActivity` cold activity starts, force-stopping between samples. Baseline `TotalTime`: 702, 519, 457, 377, 370 ms (median 457 ms). Current `TotalTime`: 1,373, 1,193, 1,120, 1,047, 1,042 ms (median 1,120 ms; +663 ms / +145% vs baseline). `ThisTime` was not emitted. These are ActivityManager launch timings, not a rendered-frame benchmark or Bluetooth authentication latency.
- After one launch and a 10-second foreground interval, baseline `dumpsys meminfo` was 100,195 KB PSS / 203,468 KB RSS; current was 157,412 KB PSS / 253,568 KB RSS. These are single snapshots, not peak/session or LeakCanary measurements. The device reported 7,674,656 KB total RAM. The current `shrunkDebug` build adds LeakCanary to this variant, while the pre-feature variant did not; therefore startup, memory, and APK-size deltas are confounded by the test-instrumentation dependency and cannot be attributed to production derivation changes. APK DEX entries total 32.0 MB baseline and 67.7 MB current, consistent with the added instrumentation being material; this does not establish the release AAB size.
- The baseline build and these same-device observations provide partial T003 evidence only. They do not satisfy the Pixel 6a/equivalent reference-device requirement, end-to-end HID request-to-response latency, production-equivalent size/memory comparison, or required coverage report inventory. T003, T066, and T071 remain open; the observed test-variant regression must not be presented as a production regression or as a pass.

### Production release size and emulator startup comparison (2026-10-15; partial)

- The production release AAB comparison above uses the exact `:app:bundleRelease` output on both `8b4d85ad` and the feature worktree. Baseline was 40,984,305 bytes; current was 40,988,409 bytes; current is +4,104 bytes (+0.010%) and remains under both the 50 MB release AAB ceiling and the +500 KB architecture-review trigger. This closes only the release binary-size measurement portion, not T003/T066/T071.
- Also built `:app:assembleRelease` on both revisions, then signed the unsigned release APKs with the local Android debug key solely to install on the disposable API 35 AOSP ATD x86_64 emulator (`emulator-5554`). Both unsigned APKs were exactly 62,393,635 bytes. The emulator had no existing `com.chimali` package; uninstall/reinstall and runtime-permission grants gave each revision a clean app-data start. The physical Moto and its normal application package were not used for this release comparison.
- Ran five `adb shell am start -S -W -n com.chimali/com.chimali.MainActivity` cold activity starts per revision, force-stopping between launches. Baseline `TotalTime`: 2,419, 1,559, 1,352, 1,090, 1,733 ms (median 1,559 ms). Current: 1,213, 1,320, 1,094, 1,213, 1,524 ms (median 1,213 ms, 346 ms / 22.2% lower). `ThisTime` was not emitted. These are ActivityManager start timings, not first-frame/rendering measurements.
- After an additional launch and 10-second foreground interval, baseline `dumpsys meminfo` reported 67,838 KB PSS / 191,388 KB RSS; current reported 63,761 KB PSS / 186,584 KB RSS. Each is a single snapshot, not peak/session memory. These production-variant emulator samples do not substitute for a Pixel 6a-class physical reference workload or establish the constitutional peak/session-memory budget.
- No external relying-party host/account was connected during these measurements. HID request-to-response latency, remote receipt, candidate-derivation counts during an actual authentication, coverage inventory, reference-device comparisons, and the independent formal review remain outstanding. Keep T003, T066, T067, T069, T071, and T072 unchecked.


### T072 FR-013 negative-assurance review (2026-10-15; conditional pass)

- Performed a systematic FR-013 negative-assurance audit across 23 migration-touched source files using four parallel security auditors. Each file was evaluated across four categories: persistence, logging, diagnostics, and terminal-path erasure of owned mutable working buffers on success, failure, and cancellation.

- **Audited files (23)**: `HkdfSha256.kt`, `P256ParentKeyDerivation.kt`, `HdkP256.kt`, `P256Group.kt`, `Bip39MasterSeedGenerator.kt`, `EventStoreKeyProviderImpl.kt`, `WalletMasterSeedProvider.kt`, `Fido2CryptoService.kt`, `PostQuantumCrypto.kt`, `HmacSecretProcessor.kt`, `DevToolsViewModel.kt`, `DevelopmentToolsScreen.kt`, `EncryptedDriverFactory.kt`, `MnemonicQrScanner.kt`, `SensitiveMnemonicInputPolicy.kt`, `GetAssertionUseCase.kt`, `RegisterCredentialUseCase.kt`, `CredentialRepositoryImpl.kt`, `CredentialRepository.kt` (interface), `PasskeyEventStoreRepositoryImpl.kt`, `PasskeySnapshotRepositoryImpl.kt`, `PrivacyScrubbingLogWriter.kt`, `LocalCrashReportingLogWriter.kt`, `SecureMemory.kt`, `AesGcmEncryptedMetadataService.kt`.

- **Category results**: PERSISTENCE 23/23 PASS. LOGGING 23/23 PASS. DIAGNOSTICS 23/23 PASS. TERMINAL-PATH ERASURE 20/23 PASS, 3 FAIL (two migration-touched, one pre-migration). No secret material is intentionally persisted, logged, or exposed through diagnostics in any migration-touched file.

- **Findings**:
  - T072-F001 (MEDIUM): `P256Group.serializeScalar` (lines 82–91) allocates a `BigInteger.toByteArray()` intermediate buffer containing the private scalar. When the representation is 33 bytes (leading zero, ~50% probability) or undersized, a new array is created via `copyOfRange`/concatenation and the original `bytes` buffer is never zeroed. No `try/finally` block protects against exceptions.
  - T072-F002 (LOW): `EventStoreKeyProviderImpl.getEventStoreKey` (lines 14–32) acquires `rootPrk` at line 15 and `label` at line 19 before the `try` block at line 21. If `toByteArray()` throws or cancellation occurs between lines 15–20, the PRK copy is not erased. The window is extremely narrow (one String→ByteArray allocation) and `rootPrk` is already a defensive copy.
  - T072-F003 (LOW): `AesGcmEncryptedMetadataService` (pre-migration, single pre-feature commit) has no `try/finally` blocks in `encrypt`/`decrypt`. Callers (`PasskeyEventStoreRepositoryImpl`, `PasskeySnapshotRepositoryImpl`) erase their owned plaintext/key buffers. The practical risk is limited to intermediate buffer copies on exceptional paths for a file outside the migration scope.
  - T072-N001 (NOTE): Eight files correctly erase application-owned buffers but do not document per-file residual limits for JCA/BC/ICU internal copies. Project-level residual limits are documented in `security-review.md` lines 68–78 and `HdkP256.kt` line 30.

- **Disposition**: Conditional Pass. T072-F001 and T072-F002 require source fixes in migration-touched code. T072-F003 requires ownership clarification or fix. The full review artifact is at `t072-fr013-negative-assurance-review.md` in the conversation artifacts.

- **Independent formal reviewer disposition**: NOT obtained. The `security-review-packet.md` instrument is prepared but no independent formal reviewer has been engaged. The ES-V.5 custom-protocol classification requires an independent reviewer distinct from both the implementer and the engineering reviewer. This engineering reviewer cannot serve as the independent formal reviewer since they performed the T005 engineering security design review. The activation gate remains OPEN. Production activation is not authorized.

- T072 remains unchecked pending: (1) source fixes for T072-F001 and T072-F002, (2) resolution of T072-F003, and (3) independent formal reviewer disposition.

### T072 remediation follow-up (2026-10-15)

- Closed the source findings from `t072-fr013-negative-assurance-review.md`: `P256Group.serializeScalar` copies the returned encoding and clears its `BigInteger.toByteArray()` temporary in `finally`; `EventStoreKeyProviderImpl.getEventStoreKey` places root-PRK and label acquisition under `try/finally`; and `AesGcmEncryptedMetadataService` now documents borrowed input, caller-owned outputs, caller cleanup obligations, and the limits of dropping JCA-managed key references. F003 is resolved by that ownership clarification; no physical-erasure claim is made for JCA/provider copies.
- `:core:security:ktlintCheck :core:security:testAndroidHostTest --console=plain` passed: 43 tests, zero failures/errors/skips. `tools/local-ci.ps1 -NoFix` passed all configured checks in 69.12 seconds.
- T072 remains open because the review artifact identifies its author as the same reviewer as T005; no separate independent formal reviewer disposition is recorded. Production activation remains unauthorized pending that review and any resulting finding closure.

### Settled production-release startup and memory comparison on selected Moto alternative (2026-10-15)

- The user selected a specific alternative device. Used the physical Moto G Stylus 5G (2022), Android 13, serial `ZY22G3FJSX` (7,674,656 KB total RAM). This is reported as the selected alternative, not as a Pixel 6a-class equivalent.
- Baseline was pre-feature commit `8b4d85ad`; current was this worktree. Both release APKs were installed under disposable package `com.chimali`, app data cleared, all requested runtime permissions granted, and measured with five `am start -S -W` cold activity launches, waiting five seconds after each launch. Baseline `TotalTime`: 403, 240, 287, 258, and 238 ms (median 258 ms). Current: 285, 224, 217, 242, and 205 ms (median 224 ms; 34 ms / 13.2% lower). Each settled sample had a live app PID and `com.chimali/.MainActivity` as the top resumed activity. These are ActivityManager launch times, not first-frame/rendering measurements.
- After the fifth launch and a further five-second foreground interval, baseline `dumpsys meminfo` reported 88,994 KB PSS / 183,736 KB RSS; current reported 86,074 KB PSS / 179,316 KB RSS. These are single settled snapshots, not peak/session measurements. The same production AAB sizes recorded above are 40,984,305 bytes baseline and 40,988,409 bytes current (+4,104 bytes / +0.010%).
- Discard the earlier emulator startup and memory samples in the preceding production-release section: that run logged `AEADBadTagException` after uninstall/reinstall restoration and the launch sampling cadence was too close. The likely restored-ciphertext/Keystore mismatch is unproven; `pm clear` plus permission grants produced stable measured launches. The first current Moto attempt was discarded because permission-controller prompts appeared; the accepted run granted all manifest runtime permissions and confirmed the main activity remained foregrounded, with no `AndroidRuntime:E` errors.
- The temporary `com.chimali` package was uninstalled after measurement; `pm list packages` confirmed only the pre-existing disposable `com.chimali.shrunk` package remained. No personal credential was used or reset.
- These Moto samples are partial evidence only. No external CTAP2/RP authentication was run, so Bluetooth request-to-response latency, remote host receipt, derivation counts during a real authentication, rendering latency, and external interoperability remain unmeasured. They do not provide peak/session memory or establish Pixel-class equivalence. Keep T003, T066, T067, T069, and T071 open.

### T072 finding closure (2026-10-15; FR-013 negative assurance PASS)

- T072-F001 (MEDIUM): `P256Group.serializeScalar` now wraps the `BigInteger.toByteArray()` intermediate buffer in `try { ... return copy } finally { bytes.fill(0) }`. All three output paths (exact 32-byte, 33-byte leading-zero, undersized) return a fresh copy and zero the temporary buffer on success, failure, and cancellation. The immutable `BigInteger` source remains the accepted non-erasable residual.
- T072-F002 (LOW): `EventStoreKeyProviderImpl.getEventStoreKey` now declares `rootPrk` and `label` as nullable vars before the `try` block and assigns them inside it. The `finally` erases both via `?.fill(0)`, covering all paths including partial acquisition before the main `try` body executes.
- T072-F003 (LOW): `AesGcmEncryptedMetadataService` class-level KDoc added documenting that callers retain and erase input key/plaintext arrays, returned buffers are caller-owned, and `clearKey()` drops the service reference only without claiming physical erasure of JCA/provider-managed storage. Pre-migration file; no behavioral change.
- `:core:security:ktlintCheck :core:security:testAndroidHostTest --console=plain` passed: 43 tests, 0 failures, 0 errors, 0 skips. `P256GroupTest` (9), `HdkP256Test` (4), `P256ParentKeyDerivationTest` (3), `HkdfSha256Test` (5), `Bip39MasterSeedGeneratorTest` (12), `EventStoreKeyProviderImplTest` (3), `SecurityModuleTest` (2), `PlatformUserVerificationTest` (4), `ResourceLoaderTest` (1).
- `tools/local-ci.ps1 -NoFix` passed in 70.75 seconds: Ktlint, Detekt, Lint (Release), Compile All, and Unit Tests all green.
- **FR-013 negative-assurance gate: PASS.** All 23 audited migration-touched files pass all four FR-013 categories (persistence, logging, diagnostics, terminal-path erasure) within documented residual limits. The full review artifact is at `specs/055-migrate-hdk-draft/t072-fr013-negative-assurance-review.md`.
- **Independent formal review gate: closed for the reviewed source state only.** The evaluation returned **ACCEPT** and authorized activation for the named local profile as it existed at review time. The later assertion UV correction was not included in that review; the current supplement is pending in `security-review-packet.md`, so current activation is not authorized by the prior disposition.
- **T072 was complete for the source state reviewed at this time.** Both parts were satisfied for that revision: (1) FR-013 negative-assurance review across persistence, logging, diagnostics, and every error/cancellation terminal path closed findings T072-F001 through T072-F003; (2) independent formal reviewer disposition was obtained and recorded. A later assertion UV false-assurance finding led to a correction not covered by that disposition; current T072/T075 status is reopened and supplemental review remains pending.

### T072 independent formal security review disposition (2026-10-15; CLOSED)

- **Reviewer:** Independent Cryptographic Reviewer / Independent Security Evaluation
- **Reviewed Revision:** Commit `8b4d85ad66948a8a7c83683e34cb2507c94a4561` + working tree
- **Disposition:** **ACCEPT**
- **Production Activation Permitted (for reviewed source state only):** **YES** with respect to cryptographic design, derivation contracts, negative-assurance controls, and lifecycle bounds. This disposition does not cover the subsequent assertion UV correction; current production activation remains gated on supplemental review.
- **ES-V.5 Custom Protocol Classification:** Confirmed custom cryptographic protocol composition. The design links published BIP-39, RFC 5869, RFC 9180 §7.1.3, draft-dijkhuis-hdk-00 §7, RFC 8032, and FIPS 204 through application-defined software parent provisioning, local deterministic credential secret substitution, and multi-algorithm branching.
- **Protocol Deviation Scope:** Acceptance is strictly scoped to Chimali's named local profile (`chimali-local-hdk-p256-v1`, `chimali-local-ed25519-v1`, `chimali-local-mldsa65-v1`). Does not certify OpenID4VCI HDK draft interoperability, issuer exchanges, or hardware custody.
- **Derivation Vector Verification:** Independently verified frozen fixtures (`core/security/src/androidHostTest/resources/hdk/local-profile-v1.json`) across all three branches:
  - P-256 (`HdkP256Test`): root PRK, parent scalar/public point, credential secret, blinding factor, child private scalar, child public point verified (4 tests).
  - Ed25519 (`MultiAlgorithmIntegrationTest`): 32-byte seed import and public key derivation verified.
  - ML-DSA-65 (`MultiAlgorithmIntegrationTest`): 32-byte $\xi$ seed import, FIPS 204 key expansion, and public key SHA-256 hash verified.
- **Finding Closure:** All 8 T005 design findings (SR-001 through SR-008) and all 3 T072 negative-assurance findings (T072-F001, T072-F002, T072-F003) are verified closed.
- **Artifacts:**
  - Independent review report: `specs/055-migrate-hdk-draft/independent-formal-security-review.md`
  - Reviewer packet disposition: `specs/055-migrate-hdk-draft/security-review-packet.md`
  - FR-013 negative-assurance audit: `specs/055-migrate-hdk-draft/t072-fr013-negative-assurance-review.md`

### T067/T074 local browser-to-CTAP2 flow (2026-10-15; partial)

- Added `tools/ctap2-rp-harness/`, a loopback-only synthetic WebAuthn relying party using `@simplewebauthn/server` 14.0.3. It binds to `127.0.0.1:4173`, accepts ES256, stores one credential/challenge in process memory only, and forgets that state on exit. `npm install` completed; the options endpoint returned RP ID `localhost`, ES256 (`-7`), and a valid challenge. No external service or real account was used.
- Physical client: Moto G Stylus 5G (2022), Android 13, package `com.chimali.shrunk`; Edge on the Windows host and the paired `INSPIRON3520` Bluetooth host. The phone was connected and Chimali displayed “Connected to PC.” Two credentials were registered while correcting the harness's post-verification counter display bug; the result below refers to the second synthetic credential.
- Registration verified by the local RP: `verified=true`, `credentialDeviceType=singleDevice`, `credentialBackedUp=false`, initial counter `0`. App-side `MakeCredential` processing was 94 ms; full wall time included about 44.2 seconds waiting for user verification. The RP is `localhost`, and the credential is synthetic.
- Pre-restart assertion verified by the RP: `verified=true`, `userVerified=true`, counter advanced `0 → 2`. One browser `navigator.credentials.get()` action generated two successful app-side GetAssertion operations for the same single allowed credential (app candidate count `1` each): 167 ms and 115 ms. This is a repeated-request discrepancy; the project has no production instrumentation to independently count child-key derivations per physical ceremony.
- Restart proof: app process `29095` was force-stopped; `pidof` returned no process; cold relaunch created process `32654`. App data was preserved, the authenticator service was restarted, and the same local RP credential authenticated successfully after restart. The RP verified `userVerified=true`, and counter advanced `2 → 4`. The two app-side GetAssertion operations measured 215 ms (over the 200 ms target) and 173 ms (within target), again for one browser authentication action and one allowed credential.
- Repeated-flow follow-up: three more browser authentication actions were accepted by the local RP, advancing its counter `4 → 6 → 8 → 10`. Across the five RP-verified browser actions, the app recorded ten successful GetAssertion operations at 167, 115, 215, 173, 137, 129, 127, 134, 127, and 122 ms. Nine of ten were under 200 ms; one was 215 ms. Each browser action still caused two CTAP operations. These are local app-side request/report handoff measurements, not end-to-end host receipt times. Candidate count was one for each operation, but there is no instrumentation proving the number of child-key derivations per ceremony.
- A later browser action returned `NotAllowedError` to the RP page, although device logs showed the authenticator completed an assertion and advanced its app-side sign count to 11 before the host/browser canceled. The RP did not verify or accept that response. This inconsistent host result is recorded as a failed flow; it does not extend the verified RP counter sequence beyond 10.
- This establishes a successful local Edge/WebAuthn/Windows/Bluetooth-HID/Chimali registration, assertion, and process-restart recovery path for P-256. It does not establish external-service interoperability, draft HDK interoperability, remote-host receipt timing, another WebAuthn algorithm, one derivation per browser ceremony, a matched pre-feature RP baseline, rendering latency, or peak/session memory. T067 is complete: the quickstart's configured task/report references and authorized local RP scenario were reconciled and executed, and the later browser failure is documented. T003, T066, T069, T071, and T074 remain open; the repeated GetAssertion behavior and 215 ms sample remain unresolved performance findings.

### Matched pre-feature RP registration retry (2026-10-15; unsuccessful)

- Retried disposable localhost RP registration twice with pre-feature build `8b4d85ad` installed as `com.chimali.shrunk` on the Moto G Stylus 5G (2022). Both browser ceremonies ended with `NotAllowedError`; neither produced a verified RP registration. On the first, the device displayed the biometric prompt briefly, but baseline app feedback reported `Could not perform cryptographic operation. Generated key not found in KeyStore`. The prompt was no longer present during the later retry; the app had returned to its home screen. No fingerprint action is pending now.
- The baseline implementation's registration path calls `Fido2CryptoService.getPublicKey`, which invokes `generateCredentialKeyPair` again after the initial key derivation. This is a plausible failure point, but the on-device evidence does not establish the exact cause. Do not attribute the baseline failure to the new HDK implementation or count this as an RP success.
- The matched baseline/current RP ceremony and physical ceremony derivation count remain unmeasured. This does not close T003, T066, or T071. The current app build has been restored to the disposable package after the attempt; the normal `com.chimali` package was not modified.

### Fresh current-app localhost RP retry after baseline comparison (2026-10-15)

- After restoring current `shrunkDebug` on the Moto G Stylus 5G (2022), restarted the Bluetooth authenticator and re-registered a synthetic `localhost`/`chimali-disposable-test-user` ES256 credential. The loopback RP verified registration (`verified: true`, single-device, not backed up). Device log: `MakeCredential = 93 ms` app processing / HID report acceptance, with 32,859 ms of explicit user interaction subtracted from 32,952 ms total. This is the instrumented local handoff boundary, not remote-host receipt time.
- The next browser authentication was RP-verified (`verified: true`, `userVerified: true`, RP counter 0→2). The device logged two GetAssertion operations for that one browser action: 174 ms and 139 ms, with sign counts 1 and 2. One follow-up browser action ended `NotAllowedError` even though the device completed two GetAssertion operations at 158 ms and 134 ms (sign counts 3 and 4); the RP did not verify that follow-up. This repeats the previously observed browser/app disagreement and means app-side completion alone is insufficient evidence of an accepted ceremony.
- Authenticator selection required a Windows security-key dialog before the Moto received CTAP MakeCredential. Closing the host dialog cancels that browser ceremony; after choosing the external key, the phone received MakeCredential and the RP verified it. The failed assertion attempt after the verified action did not present a new phone biometric request.
- These fresh current-build observations do not establish one selected derivation per browser ceremony, a matched successful pre-feature RP ceremony, a Pixel 6a-class reference workload, rendering or peak/session memory. T003/T066/T071/T074 remain open; no production service or draft interoperability claim is made.
- Interaction clarification from the device operator: for registration, the Moto first shows a user/credential creation confirmation. Tapping its confirmation button then opens the fingerprint prompt. Record these as two separate approvals; absence of the biometric sheet before the first tap is expected.
- Follow-up retry after the operator completed the host security-key flow succeeded: browser action 3 was RP-verified (`verified: true`, `userVerified: true`, RP counter 2→6). The Moto completed two GetAssertion operations at 163 ms and 116 ms (authenticator sign counts 5 and 6). This is one more successful current-build browser ceremony with two app-side assertion operations; candidate/selected derivation counts for the physical ceremony remain uninstrumented.

- Follow-up baseline retry after the operator confirmed the Moto passkey approval (2026-10-15): Windows remained at “Acerca la clave de seguridad” (bring the security key close), while the Moto app displayed `Registration failed` / `Could not perform cryptographic operation. Generated key not found in KeyStore: CredentialId(Q8-H-7ohXCMfz6ZAQMjITdBJr5BMEUqW2XMj5Zf87SY)`. The RP request log contained only `POST /api/register/options`; no registration response was submitted or verified. Treat this retry as failed even though the operator completed the phone interaction. It adds no matched baseline latency or successful ceremony evidence. The error is consistent with, but does not prove, the duplicate baseline key-generation failure point described above.

### Final local CI verification after the baseline retry (2026-10-15)

- Ran `tools/local-ci.ps1 -NoFix` after recording the unsuccessful matched baseline attempt. The script completed successfully in 55.16 seconds: Ktlint Check, Detekt, Lint (Release), Compile All, and Unit Tests all passed. Gradle reported `BUILD SUCCESSFUL`; the Unit Tests phase passed in 3.37 seconds. No CI fix mode was used.

### Baseline error-source clarification (2026-10-15)

- Inspected pre-feature source at commit `8b4d85ad`: `RegisterCredentialUseCase.generateCredential` emits `Generated key not found in KeyStore` when `Fido2CryptoService.getPublicKey` returns null. In that baseline, `getPublicKey` invokes `generateCredentialKeyPair` again and then decodes the returned public-key bytes; this code path does not look up a per-credential Android Keystore entry. The observed string is therefore a misleading generic fallback, not evidence of a hardware Keystore miss.
- The UI and RP evidence establish that registration failed after the operator completed phone verification and before the RP received a credential. They do not identify whether the second derivation returned an error or public-key reconstruction failed. The repeated generation is a source-level candidate location only; no root cause is claimed.

### Current-build RP chooser dismissal (2026-10-15; unverified)

- On the latest synthetic localhost registration retry, the Windows security-key chooser disappeared without displaying an error. Before resetting the page, the RP UI still showed `Ready`; its request log had only `POST /api/register/options` and no `/api/register/verify`, and the authenticator log had no corresponding CTAP MakeCredential request. The browser page was reloaded to cancel the pending WebAuthn operation and return the disposable harness to its ready state.
- No credential was submitted to or verified by the RP in this attempt. This is an abandoned/unverified host ceremony, not a successful registration and not evidence of an app-side cryptographic failure. It does not change the matched-baseline or SC-007 evidence status.

### Current-build RP registration retry after chooser dismissal (2026-10-15; verified)

- Retried the same synthetic loopback `localhost` ES256 registration after reloading the abandoned page. The Moto received CTAP2 MakeCredential and displayed the Chimali confirmation followed by Android biometric verification. The RP's `/api/register/verify` returned `verified: true`, initial counter `0`, `credentialDeviceType=singleDevice`, and `credentialBackedUp=false`.
- Device instrumentation recorded `MakeCredential = 104 ms` (total `31,802 ms`, explicit user interaction `31,698 ms` excluded). This is the app's request processing/HID handoff metric; it is not end-to-end host receipt latency. The test used only the synthetic RP/account and does not establish external interoperability or close the matched baseline/performance gaps.
- The next browser action authenticated this credential successfully at the RP (`verified: true`, `userVerified: true`, counter `0 → 2`). One browser action again caused two app-side GetAssertion operations, measured at `170 ms` and `122 ms`; each logged one allowed credential candidate. This confirms the repeated-operation pattern in another successful current-build ceremony, and does not prove a single child-key derivation per browser action.

### Rebuilt-source HID timing validation (2026-10-15; SC-007 over budget)

- Rebuilt `:app:assembleShrunkDebug` from the current source (BUILD SUCCESSFUL, 1m12s) and installed `app/build/outputs/apk/shrunkDebug/app-shrunkDebug.apk` with `adb install -r` over the disposable `com.chimali.shrunk` package. The reinstall preserved app data and the synthetic RP credential; the ordinary `com.chimali` package was not touched. Restarted the authenticator and used its existing credential against the same loopback RP.
- The latest source now awaits each final report's `BluetoothHidDeviceWrapper.sendReport()` result before ending the `LatencyProfiler` interval. This includes response queue wait and pacing, and reports local API acceptance; it does not establish Bluetooth air-time or host receipt. The prior 115–215 ms set was collected from the earlier installed binary and is handler-processing timing, not this final-report-handoff metric. Do not use those earlier values as SC-007 request-to-response results.
- With the rebuilt binary, two subsequent browser authentication actions were each RP-verified with `userVerified=true`, advancing counters `2 → 4` and `4 → 6`. Each browser action caused two CTAP GetAssertion operations with one allowed credential candidate each. The four final-report-handoff timings were 396, 291, 370, and 224 ms; all four exceeded 200 ms, although all final response reports returned accepted from Android's HID wrapper. This is a confirmed performance failure, not an end-to-end host-receipt pass.
- A Perfetto CPU profile was attempted during the latter action. The official helper pulled a 1.6 MB trace and produced a small `.pb` profile artifact, but exited with `No profiles generated`; no CPU-hotspot conclusion is drawn pending trace/profile inspection. Startup/rendering and peak/session memory remain unmeasured.
- Correcting the earlier T003 profiler review: source now measures final-report local API handoff, but no current-source sample meets the <200 ms budget, and matched pre-feature evidence, physical ceremony derivation counts, rendering/startup, and peak/session memory remain outstanding. T003/T066/T071/T074 remain open.

### Rebuilt current-source startup and memory spot check (2026-10-15; partial)

- On the Moto G Stylus 5G (2022), Android 13, installed `shrunkDebug` build, `adb shell am force-stop com.chimali.shrunk` followed by explicit `am start -W` reported `LaunchState: COLD`, `TotalTime: 1427 ms`, `WaitTime: 1439 ms`. After returning Home and relaunching the existing task, `LaunchState: HOT`, `TotalTime: 65 ms`, `WaitTime: 68 ms`. This meets the constitution's <2 s cold and <500 ms hot launch limits for this one run; no warm-start sample or frame/rendering measurement was taken.
- `dumpsys meminfo` five seconds after the cold launch reported total PSS 164,130 KB / RSS 258,228 KB; after the hot relaunch and three seconds, total PSS was 130,928 KB / RSS 224,664 KB. These are point samples, not a measured session peak or baseline. The app APK was 113,088,742 bytes; `shrunkDebug` is debuggable and the build warned that optimization is disabled, so this APK does not assess the release AAB 50 MB budget.
- Startup/memory are current-build Moto spot checks only; no matched pre-feature sample, continuous peak/session capture, LeakCanary retained-object conclusion, or Pixel-class equivalence is established. T003/T066/T071/T074 remain open.
- Additional session point sample immediately after the verified current-source assertion flow: `dumpsys meminfo com.chimali.shrunk` reported total PSS 177,741 KB / RSS 273,412 KB (native heap 18,804 KB). It is a single post-ceremony snapshot, not a process peak or session maximum; it does not close the memory measurement gate.

### Current-source browser-side assertion timing (2026-10-15; verified, over budget)

- Extended the disposable RP page to record monotonic time for the options round trip, WebAuthn ceremony, RP verification round trip, and total browser action. After restarting the latest rebuilt app and authenticator on the Moto, browser action 1 was RP-verified (`userVerified=true`, counter `6 → 8`). The page measured options `13.6 ms`, WebAuthn ceremony `5,744 ms`, RP verification `8.2 ms`, and total `5,765.9 ms`. The ceremony duration includes Windows/browser authenticator-selection overhead and is not an app-only sample.
- The corresponding current-source device logs recorded two GetAssertion commands, each with one allowed candidate, at 299 ms and 224 ms to final report API acceptance; both exceeded 200 ms. The browser verified the second response. Combined with the prior rebuilt-source actions, there are now six current-source handoff samples across three RP-verified browser actions, all over budget (224–396 ms); each browser action caused two CTAP GetAssertion operations. These data strengthen the open performance finding; they do not establish one selected child-key derivation per browser ceremony.

### Privacy scrubber hot-path mitigation (2026-10-15; device retest over budget)

- Perfetto sampling identified repeated regex compilation/matching in `PrivacyLogScrubber.scrub` as a candidate on HID handling stacks (7 of 13 samples). The trace did not contain scheduler/request markers, so this remains a candidate and not a proven cause of the handoff regression.
- Precompiled the sensitive-keyword redaction regexes once in `PrivacyLogScrubber` object initialization; the keyword set, patterns, and replacement behavior are unchanged. The existing `PrivacyLogScrubberTest` host-test task passed, and `:app:assembleShrunkDebug` completed successfully. Installed the rebuilt APK with `adb install -r` over the disposable `com.chimali.shrunk` package, preserving its synthetic credential data; the ordinary `com.chimali` package was not modified.
- At the point of the initial mitigation entry, device retest was pending: RP action 2 was in progress without a CTAP request or verified counter update. The completed retest is recorded below.
- Retest completed after installing the optimized source on the same Moto and retaining the synthetic credential: the disposable RP verified authentication action 2 with `userVerified=true`, counter `8 → 10`. The browser measured options `14.8 ms`, WebAuthn ceremony `45,512.2 ms`, RP verification `8.4 ms`, and total `45,535.5 ms`; the ceremony includes Windows authenticator-selection/user-wait time and is not an app-only measure. Device logs showed two GetAssertion operations, each with one allowed candidate, at 284 ms and 225 ms through final HID API acceptance. Both remain above 200 ms. These two samples are too small and the user-wait interval too variable to attribute a comparative performance change to regex precompilation; the SC-007 latency gate remains open.

### Assertion UV flag correction and current-source validation (2026-10-15)

- Review of the latest headless assertion trace found that `GetAssertionUseCase` set the authenticator-data UV bit from the RP's `preferred` request value. The CTAP handler used the headless fast path for a single candidate, which skipped the system verification prompt. The prior browser action 4 RP response (`userVerified=true`, counter 12→14) is therefore **not valid evidence that user verification occurred**; its authenticator data asserted UV without a completed verification event. The RP verified the signature, but its UV claim was false assurance.
- Corrected the use case to accept an explicit `userVerificationPerformed` result. The authentication ViewModel supplies `true` only from `AuthenticationIntent.UserVerificationSuccess`; headless and no-verification paths retain `false`. The UV bit now reflects that result, and `REQUIRED` requests fail before credential lookup/signing when verification was not performed. `credProtect=3` candidate filtering now follows actual completed verification, not the requested preference.
- Added host regressions proving that a headless `preferred` assertion leaves UV clear, a confirmed verification sets UV, and a `required` request without verification is denied before credential lookup/signing. `:feature:fido2:ktlintCheck :feature:fido2:testAndroidHostTest` passed. The first full CI run caught a Detekt return-count violation in the new guard; moving the guard into the existing verification helper resolved it. The subsequent `tools/local-ci.ps1 -NoFix` passed Ktlint, Detekt, Release Lint, Compile All, and Unit Tests in 74.1 seconds.
- Installed the resulting `shrunkDebug` APK to the disposable `com.chimali.shrunk` package and relaunched the Moto authenticator. Android's Bluetooth discoverability permission was approved; device logs then showed Chimali advertising, but the host connection immediately disconnected. A fresh browser action reached `/api/auth/options` only; Windows' security-key dialog disappeared, no CTAP GetAssertion reached the Moto, and the RP did not verify an assertion. This device attempt does not validate the corrected UV bit. Host regressions and full CI are the available validation; no claim of physical biometric completion is made.
- The prior independent T072 disposition predates this UV correction and does not review this follow-up source diff. Keep production activation contingent on supplemental independent security review of the corrected assertion UV handling. SC-007 performance, matched baseline, and physical RP validation remain open; no task is closed by this entry.

### One-pass scrubber retest (2026-10-15; RP verified, latency still over budget)

- Combined the sensitive-keyword list into one precompiled case-insensitive pattern, so each log message now performs one keyword-value regex replacement instead of compiling/scanning once per keyword. Added a regression case for all 17 keyword forms, including camelCase, spaces, underscores, and case-insensitive matching. The focused `PrivacyLogScrubberTest` task and `:feature:fido2:ktlintCheck` passed. `:app:assembleShrunkDebug` succeeded, and the APK was installed with `adb install -r` to the disposable `com.chimali.shrunk` app.
- On this build, the RP verified browser authentication action 3 with `userVerified=true`, counter `10 → 12`. The browser measured options `9.3 ms`, WebAuthn ceremony `24,126.9 ms`, RP verification `8.4 ms`, and total `24,144.6 ms`; this includes Windows authenticator-selection and wait time. The device logged two GetAssertion operations, one allowed candidate each, with final HID API handoff times of 291 ms and 252 ms. Both remain above the 200 ms budget, so the optimization does not close SC-007. This two-operation sample is not sufficient to claim a causal latency regression or improvement.
- During action 3, the user reported that the Windows security-key prompt disappeared shortly after starting the Moto authenticator and did not offer a visible Bluetooth-key choice. The RP still verified the assertion and the Moto logs contain the corresponding GetAssertion operations, so this action completed successfully through the Moto flow despite the absent visible transport choice. The browser prompt behavior itself is not evidence of a latency pass; both measured app-side handoffs remain above budget.
- Re-ran `tools/local-ci.ps1 -NoFix` against this source state after the scrubber optimization and regression test. Ktlint, Release lint, Compile All, and Unit Tests passed; the script completed successfully in 96.58 seconds. This does not close the still-open SC-007 device latency and matched-reference gates.

### UV ViewModel boundary regression and final local CI rerun (2026-10-15)

- Added a ViewModel integration test for the preferred-verification auto-confirm path with no available verification method. It asserts the signing use case receives `userVerificationPerformed=false` and is never called with `true`. The prior host tests separately cover the successful system-verification callback and authenticator-data flag behavior.
- `:feature:fido2:ktlintCheck :feature:fido2:testAndroidHostTest` passed after the new test. The final `tools/local-ci.ps1 -NoFix` rerun passed Ktlint, Detekt, Release Lint, Compile All, and Unit Tests in 36.33 seconds. These results verify host/build behavior only; physical RP validation remains pending because the Windows dialog closed without delivering CTAP to the Moto.

### Release AAB baseline and current-source size (2026-10-15; partial)

- Built `:app:bundleRelease` from baseline `HEAD` commit `8b4d85ad` in detached worktree `D:\temp\chimali-baseline-8b4d85ad` using the same local Android SDK, and from the feature working tree after the UV fix. Both release bundle tasks succeeded.
- Baseline AAB: `40,984,305` bytes. Current-source AAB: `40,993,849` bytes. Increase: `9,544` bytes (~0.023%). Current bundle is below the constitution's 50 MB ceiling. `app/build.gradle.kts` changes affecting `shrunkDebug` instrumentation/LeakCanary do not apply to `release`; the release source includes the feature's production code changes.
- This records a like-variant baseline/current bundle-size comparison, not installation/download size across device-specific splits. Latency, full matched baseline ceremony, startup/rendering, and peak/session memory comparison remain open; this does not close T003/T066/T071/T074.

### Headless assertion candidate-snapshot reuse (2026-10-15; implementation verified, device latency pending)

- The CTAP headless path used to query credential summaries once to decide whether it could auto-select a single credential, then query them a second time inside `GetAssertionUseCase` before signing. It now passes that same snapshot to the headless use-case entry point. The verified-UI path still performs its post-verification lookup so credProtect filtering reflects the completed verification result.
- Added host regressions proving the headless use case does not query the repository a second time and the CTAP handler passes its selected snapshot through the headless entry point.
- Candidate discovery retains potential credentials with `credProtect=3` so they remain available to trigger a verification prompt. The CTAP handler excludes these credentials from the headless path, and `GetAssertionUseCase` filters them only when the completed-verification result is known. Regression coverage verifies both the headless rejection/verified acceptance and UI routing for a single UV-required credential.
- Added `@Req("NFR-PERF-030", "NFR-SEC-041")` traceability tags to the assertion use case, CTAP handler, and associated use-case/handler regression classes.
- After correcting the unused import found by the first run, the final `tools/local-ci.ps1 -NoFix` run on the requirement-tagged source passed Ktlint, Detekt, Release Lint, Compile All, and Unit Tests (51.42 seconds).
- This removes one redundant repository summary read per headless CTAP assertion, but no post-change physical request-to-response measurement is available. The most recent corrected-build browser attempt ended when the Windows security-key dialog disappeared before any GetAssertion reached the Moto; the RP did not verify an assertion. Keep T003/T066/T071/T074 open and do not treat this code-path reduction as evidence that the 200 ms limit is met.
- Rebuilt `:app:bundleRelease` after the final source change; it succeeded (423 tasks, 3m13s). The current AAB is 40,994,234 bytes (SHA-256 `5FE605910671A4D99BEEC93FFD181F0ED986FBF1FCCC112546FB13F16655B31A`), 9,929 bytes (~0.024%) above the 40,984,305-byte pre-feature baseline and below the 50 MB ceiling. Earlier current bundle measurements are superseded.

### Latest corrected-build Moto authentication retry (2026-10-15; unsuccessful before CTAP)

- Built and installed the corrected `shrunkDebug` APK to the disposable `com.chimali.shrunk` package on the connected Moto G Stylus 5G (2022). The app entered its Bluetooth advertising state.
- Started synthetic RP authentication action 1. Windows showed its security-key dialog with only `Cancelar`; the operator reported no Bluetooth or alternate-device choice. The RP then reported `NotAllowedError` after timeout. Device logs show advertising but no GetAssertion/CTAP command, and the RP did not verify an assertion.
- This is a failed host-to-device attempt, not a cryptographic or user-verification result. It does not close physical validation or performance gates; retain T003/T066/T071/T074 as open.

### Fresh-device startup and memory spot checks (2026-10-15; partial, not SC-007 closure)

- **Moto matched release builds:** Rebuilt the pre-feature release APK from `8b4d85ad` and the current release APK, signed both measurement copies with the same local debug key, and verified both use `com.chimali` and request the same startup runtime permissions. The package was absent before the test; each run used `pm clear` and identical grants for Bluetooth advertise/connect/scan and notifications. Each `am start -S -W` sample was followed by a five-second interval. Baseline `WaitTime` samples were `235, 239, 244, 225, 232 ms` (median `235 ms`); current samples were `595, 237, 244, 244, 258 ms` (median `244 ms`). With only five samples and one first-launch outlier on current, this is a directional comparison, not a significance or rendering measurement. The baseline signed APK is `62,440,630` bytes (SHA-256 `DB6CF22B7FE701373713B3735CC0C03DD449EC4EBAAF52383982308A1E759A99`); current is `62,457,014` bytes (SHA-256 `861CC36637743999511F1CA172C56A8C25145164B4E5C3ACF1EF55A30FAE77C3`).
- **Moto point memory:** After the fifth launch plus ten seconds, one `dumpsys meminfo` snapshot reported baseline `92,932 KB PSS / 191,940 KB RSS` and current `84,779 KB PSS / 184,548 KB RSS`. These are single settled snapshots, not peak or session maxima. The temporary `com.chimali` release test package was removed afterward; the pre-existing `com.chimali.shrunk` package was preserved.
- **ASUS current-build spot check:** ADB identified the second device as `ASUS_AI2302`, Android 15/API 35. Its installed `com.chimali` v0.1.0 (last updated 2026-05-22) was left untouched. The current `shrunkDebug` APK (`113,088,742` bytes, SHA-256 `8F67BA858C9545D46E14184D6AC62685FD171CB42212E908FB057CB31CD8E863`) was installed under its separate `com.chimali.shrunk` test package, cleared, and given the same four runtime permissions. Five `am start -S -W` `WaitTime` samples were `1,321, 893, 983, 829, 847 ms` (median `893 ms`). After the fifth launch plus ten seconds, one memory snapshot reported `182,163 KB PSS / 283,748 KB RSS`. The test package was uninstalled afterward; the older `com.chimali` app remains installed.
- **Limitations:** The ASUS sample is `shrunkDebug`, which adds the project’s test coverage and LeakCanary setup; it is not a production-release measurement and has no matched ASUS baseline. Neither device sample measures first rendered frame, peak/session memory, Bluetooth request-to-response latency, or derivation counts during an actual ceremony. The first Moto attempt before clearing the fresh test package was discarded because restored ciphertext caused an Android Keystore MAC error and the app process crashed. No data from the Moto’s `.shrunk` package or the ASUS’s pre-existing `com.chimali` app was cleared or replaced. T003/T066/T071/T074 remain open.

### ASUS synthetic relying-party registration and authentication timing (2026-10-15; RP verified, mixed assertion budget result)

- Paired the ASUS Zenfone 10 (`ASUS_AI2302`, Android 15/API 35) with the Windows host and used the current `shrunkDebug` test package (`com.chimali.shrunk`) over Bluetooth HID. The older installed `com.chimali` v0.1.0 was not used, cleared, or replaced. The loopback harness at `http://localhost:4173/` used only `chimali-disposable-test-user`; its page identifies the account and credential storage as synthetic and in-memory.
- Registration succeeded. The RP verified the ES256 credential. ASUS `NFR-PERF-030` logs recorded MakeCredential `178 ms` operation time, with `6,033 ms` attributed to user verification and `6,211 ms` total. The browser RP snapshot reported `10,002.9 ms` WebAuthn ceremony and `10,027.6 ms` total. This single registration operation is under the `200 ms` CTAP operation limit; the larger ceremony total includes user interaction and browser/host time.
- Three browser authentication actions all completed and were verified by the RP. RP counters advanced `0 → 2 → 4 → 6`. Six CTAP GetAssertion measurements were `217, 182, 175, 170, 177, 178 ms`; five of six met the `200 ms` operation budget, one was `17 ms` over, and the median was `177.5 ms`. The three RP-measured ceremony totals were `4,265.5`, `2,578.9`, and `2,566 ms`, including browser/host and user interaction. The RP reported `userVerified=false` for these authentication actions; they are successful assertion checks, not evidence of a completed biometric UV assertion.
- Associated GetInfo operations during the three actions measured `80/80`, `87/98`, and `77/89 ms`; all were under budget. HID report acceptance was true in the app performance logs. This measures app-side HID API acceptance, not confirmed radio receipt at the Windows host.
- After recording results, force-stopped and uninstalled only the disposable `com.chimali.shrunk` test package from ASUS. Verified that its existing `com.chimali` package remains installed. No authenticator data from that older package was cleared.
- This is a real-device synthetic RP result on an ASUS Zenfone 10, not a matched Pixel 6a-class reference-device comparison. It has no same-device baseline, production-release APK, peak/session memory sample, first-render measurement, or derivation-count comparison. One of six assertion operations exceeded the target; therefore this is mixed evidence and does not close SC-007 or T003/T066/T071/T074.

### ASUS required-UV synthetic RP assertions (2026-10-15; physical biometric verified, preflight latency mixed)

- To exercise the corrected UV path, ran a temporary loopback RP on `http://localhost:4174/` that requested `userVerification: required` and enforced `requireUserVerification: true` when validating assertions. The existing 4173 harness and checked-in source were not changed. The test used the current `shrunkDebug` build under disposable package `com.chimali.shrunk` on ASUS Zenfone 10 (`ASUS_AI2302`, Android 15/API 35); the existing `com.chimali` v0.1.0 was preserved.
- Registered a fresh synthetic ES256 credential. The RP verified registration. MakeCredential measured `187 ms` operation time, with `15,594 ms` attributed to user interaction and `15,781 ms` total. Browser ceremony time was `19,963.7 ms`; it included Windows/browser and the user's biometric interaction.
- Completed three required-UV browser authentication actions. Android biometric logs recorded successful fingerprint authentication for each; the RP verified all three assertions with `userVerified=true`, and counters advanced `0 → 2 → 4 → 6`. Browser ceremony times were `6,594.4`, `6,382.6`, and `6,016.7 ms`, including host and biometric wait.
- Each browser action caused two successful CTAP GetAssertion operations. Logs show the first as a headless fast-path with one candidate before the fingerprint UI, then a second request after the fingerprint verification. The six app-side final HID API handoff timings were `248, 138, 204, 152, 200, 145 ms`. The first request in each action was `248`, `204`, and `200 ms` with no user-wait time; the biometric-backed requests were `138`, `152`, and `145 ms`, each with the user wait (`3,954`, `3,817`, and `3,380 ms`) excluded. Because the goal is strictly `<200 ms`, the first three do not meet the target, including the `200 ms` sample; all three biometric-backed operations do. Median across all six operations was `176 ms`. `HID reports accepted: true` means accepted by Android's HID wrapper, not confirmed received by Windows.
- This physical result confirms the corrected app emitted UV only after the successful fingerprint path, and the RP enforced and observed that bit. It also exposes the host preflight/headless request as an additional successful assertion operation before the UV-backed request. Runtime candidate/selected derivation counts were not instrumented; source/host regressions cover candidate-snapshot reuse, but this physical trace cannot establish the required zero candidate derivations / one selected derivation count per browser ceremony.
- After capture, force-stopped and uninstalled only `com.chimali.shrunk` from ASUS; verified the prior `com.chimali` package remains. Stopped the temporary required-UV RP and removed its temporary script; port `4174` has no listener. The original loopback harness on port `4173` was not modified.
- This confirms physical corrected-UV behavior but is not an independent formal security review. Performance remains mixed and lacks a matched baseline, first-render measurement, peak/session-memory trace, and direct derivation counters. Keep T003/T066/T071/T072/T074/T075 open; T069 remains dependent on those gates.

### ASUS pre-feature baseline RP retry (2026-10-15; failed at host/device ceremony)

- Installed the pre-feature baseline from commit `8b4d85ad` as the separate `com.chimali.shrunk` package on ASUS Zenfone 10 (`ASUS_AI2302`, Android 15/API 35); the existing `com.chimali` installation was preserved. Used the loopback synthetic RP on port `4175`.
- The RP registration ended with `NotAllowedError` and no verified credential. Device logs explicitly report `MakeCredential timed out after 120000ms` and `NFR-PERF-030 MakeCredential = 120038ms (Total: 120038ms, User: 0ms)`. Repeated local HID-wrapper `sendReport ... success=true` messages do not establish host receipt or registration success. Windows displayed the generic Spanish error “No se pudo completar la operación” before its prompt disappeared.
- A fresh browser-page retry was still pending in Windows after the first page had shown failure; the second click returned `OperationError: A request is already pending`. No second MakeCredential reached ASUS. The user canceled the Windows prompt; the RP then settled with `NotAllowedError`. Neither baseline attempt produced a credential verified by the RP.
- This is a failed baseline ceremony, not a measured successful baseline latency and not evidence of a cryptographic mismatch. The same-device matched RP comparison remains unavailable; T003/T066/T071/T074 remain open.

### ASUS device crypto regression check during WebAuthn.io failure triage (2026-10-15)

- Ran `:feature:fido2:connectedAndroidTest --rerun` against ASUS Zenfone 10 (`ASUS_AI2302`, Android 15/API 35). The connected report contains 25 tests, 0 failures, 0 errors, and 0 skipped tests. In particular, `LocalProfileLifecycleTest.androidProvidersGenerateAndVerifyAllSupportedCredentialSignatures` passed on this device; it derives, converts, signs with, and verifies ES256/P-256, Ed25519, and ML-DSA-65 keys.
- The app was left installed and its authenticator remained connected to the Windows host. No app data was cleared, and the ordinary `com.chimali` package was not modified. This rules against a general ASUS JCA/provider failure in the covered current-source key path, but does not reproduce or explain the user's WebAuthn.io registration error, prove an external RP registration, or identify the original provider exception.
  - A fresh disposable localhost RP registration on port 4175 was started after the device suite through an isolated Playwright browser context. It fetched registration options, then `navigator.credentials.create()` remained pending without a Windows security-key dialog or ASUS prompt; the ASUS log contained no corresponding CTAP MakeCredential operation. The page was navigated away to cancel the browser-only pending call. This did not reach the authenticator, produce a credential, or verify at the RP, so it is neither an app registration success nor a device registration failure. Keep it separate from the user's WebAuthn.io error report, which remains unreproduced here.
- This targeted diagnostic run does not close T003/T066/T071/T074 performance gates, T072/T075 supplemental formal review, or T069 final traceability.

### ASUS live-browser disposable RP registration and authentication after isolated-browser retry (2026-10-15; RP verified, mixed latency)

- After the isolated Playwright context remained pending without opening a host/device prompt, started registration from the live Codex in-app browser tab at the same loopback-only disposable RP (`http://localhost:4175/`). The ASUS current test package (`com.chimali.shrunk`) received and completed a CTAP2 MakeCredential operation. The app log at 18:05:30.525 recorded `[NFR-PERF-030] PASS | MakeCredential = 199ms (Total: 8412ms, User: 8213ms, HID reports accepted: true)`.
- The live RP result showed `verified: true`, counter `0`, `credentialDeviceType=singleDevice`, and `credentialBackedUp=false`; browser timing was options `9.3 ms`, WebAuthn ceremony `11,815 ms`, RP verification `13.4 ms`, total `11,838.1 ms`. The ceremony total includes host/browser and user-interaction time and is not the app operation budget.
- Authenticated the same synthetic credential from the live RP page. The RP returned `verified: true`, counter `0 → 2`, and `userVerified=false` (the harness requests `userVerification=discouraged`). The browser measured options `9.5 ms`, ceremony `2,792.5 ms`, RP verification `9.9 ms`, total `2,812 ms`. ASUS logs show two GetInfo handoffs at `97/100 ms` and two single-candidate headless GetAssertion operations at `224/174 ms`; the first GetAssertion exceeded the strict `<200 ms` target, the second met it. Both responses were accepted by Android's HID wrapper. The RP-verified assertion does not establish remote-host receipt.
- These are current-source synthetic ES256 RP results on ASUS, not a matched Moto/Pixel reference workload. The registration had one passing `199 ms` MakeCredential sample; authentication latency was mixed. The request sequence contains two GetAssertion operations for one browser action, each with one candidate; physical candidate/selected derivation counters are not instrumented. Matched baseline authentication, first-render data, and continuous peak/session-memory evidence remain absent. This does not resolve the reported WebAuthn.io failure or close T003/T066/T071/T074.

### ASUS Perfetto assertion and session-memory follow-up (2026-10-15; supplementary current-build evidence)

- Continued with the same disposable `com.chimali.shrunk` current debug build, ASUS Zenfone 10 (`ASUS_AI2302`, Android 15/API 35, build `asus/WW_AI2302/ASUS_AI2302:15/AQ3A.240812.002/35.0604.0404.165:user/release-keys`), paired Windows host, synthetic credential, and loopback RP on port 4175. The ordinary ASUS `com.chimali` installation was not modified. The prior ASUS action had one `224 ms` over-budget GetAssertion, so these additional samples do not erase that failure.
- Recorded a 25-second Perfetto system trace with the official `record_android_trace` helper, `sched` and `freq` categories, and app atrace for `com.chimali.shrunk`. The local raw trace is `D:\temp\chimali-asus-assertion-2026-10-15.perfetto-trace` (10,706,620 bytes; SHA-256 `502C70728BB0BCCE8B05685597B9374D4DD313EBCF49A61D071BBDFD03659BBB`). The live RP authenticated the existing credential with `verified: true`, counter `2 → 4`, `userVerified=false`, and a `2,688 ms` browser WebAuthn ceremony. The app log at 18:15:22 recorded two GetAssertion final HID handoffs at `179/169 ms`, both below the strict `200 ms` target. These timings stop at Android's `sendReport` acceptance; they do not prove remote receipt.
- Perfetto app slices for those two GetAssertion operations measured `Fido2.Assertion.DeriveAndSign` at `12.330/12.967 ms`, `CounterRead` at `2.337/2.327 ms`, `CounterWrite` at `24.648/24.523 ms`, and `Fido2.HID.ResponseQueueAndHandoff` at `75.887/73.419 ms`. This locates a material share of the local elapsed time in queued, paced HID response reports; the trace sections are diagnostic phase timings, not a complete attribution of all request time or direct proof of candidate derivation counts.
- Perfetto triage found four `Fido2.HID.SendReport` calls inside each assertion handoff, with report starts about `22–25 ms` apart. That spacing accounts for nearly all of the `73–76 ms` handoff phase and is consistent with the transport's configured `20 ms` post-report pacing plus dispatch overhead. The captured app threads did not show a tens-of-milliseconds runnable scheduling stall during those windows. `CounterWrite` was the next largest named phase at about `24.5 ms`; `DeriveAndSign` was about `12–13 ms`. Approximately `53–57 ms` of each log-reported GetAssertion duration preceded the first named assertion slice, assuming the log and trace endpoints align. No incoming dispatch slice explains that interval; neither it nor the underlying Bluetooth-layer timing can be attributed further from this trace. The app atrace slices are asynchronous and lack a bound thread ID, and this trace contains no Bluetooth peer receipt or ACK event.
- Recorded a separate 20-second Perfetto memory-counter trace during another live RP assertion: `D:\temp\chimali-asus-session-memory-2026-10-15.perfetto-trace` (473,750 bytes; SHA-256 `46C9F1A352B5F28432DF0C28626A1B6BCC371282B1930F03F462AD75180F6A7C`). The RP again returned `verified: true`, counter `4 → 6`, `userVerified=false`, with a `3,523.8 ms` WebAuthn ceremony. Its two app-side GetAssertion handoffs were `190/175 ms`, both below 200 ms. Perfetto's `linux.memory.process` view recorded 75 RSS observations for PID 29478, from `209.77` to `212.02 MiB` (mean `210.93 MiB`) and up to `30.52 MiB` swap. The `212.02 MiB` value is the maximum **observed within this 20-second window**, not a full-session process peak; this is a debug build with test and LeakCanary dependencies.
- Across these two additional RP actions, all four current-source ASUS GetAssertion handoffs passed, but the earlier `224 ms` operation remains a real over-budget sample. There is still no matched successful pre-feature RP assertion, no production-release reference-device session-memory comparison, and no first-render measurement. ASUS is supplementary to the selected Moto alternative and is not established as Pixel 6a-class equivalent; T003/T066/T071/T074 and SC-007 remain open.

### Matched Moto release startup and memory trace (2026-10-15; partial performance evidence)

- Installed the signed pre-feature release APK and then the signed current-source release APK on Moto G Stylus 5G (2022), Android 13, under the ordinary `com.chimali` package for isolated measurements. Each installation was cleared and given the same Bluetooth/notification permissions before capture. The baseline/current APKs were 62,440,630/62,457,014 bytes, with SHA-256 `DB6CF22B7FE701373713B3735CC0C03DD449EC4EBAAF52383982308A1E759A99` / `861CC36637743999511F1CA172C56A8C25145164B4E5C3ACF1EF55A30FAE77C3`. The temporary ordinary package was removed afterward; the separate `com.chimali.shrunk` package was preserved.
- Captured 25-second Perfetto startup/process-memory traces with the same configuration and three `am start -S -W` launches per build. Baseline trace: `D:\temp\chimali-moto-baseline-release-startup-memory.perfetto-trace` (34,614,289 bytes, SHA-256 `65D2BE1AC0CB16069E3F95D1E6EC9BCC9989776B761B5AFD9CF3C8A58FCC5968`). Current trace: `D:\temp\chimali-moto-current-release-startup-memory.perfetto-trace` (37,541,327 bytes, SHA-256 `80FAEFEB02A44C0185F166E022D35FFD86512BC49E74854F9CEA2EFC83D23EF3`).
- `am start` WaitTime was 570/322/253 ms baseline and 381/443/423 ms current. Perfetto classified the latter two launches in each trace as cold; their time to initial display was 307.868/245.904 ms baseline and 421.519/411.740 ms current. The first launch in each trace has ambiguous startup/frame linkage and is excluded from the displayed-frame comparison. These two clean cold samples suggest a slower current startup in this capture, but the sample is too small for a stable regression estimate and there is no rendering frame-series analysis yet.
- Within fixed first-second windows after those cold launches, sampled process RSS means were 88.8/89.4 MiB baseline versus 93.1/92.5 MiB current; observed maxima were 139/139 MiB versus 146/145 MiB. These are startup-window observations, not full-session or ceremony peaks. The traces contain no successful matched pre-feature/current RP assertion and no direct physical candidate/selected child-key derivation counts. The strict <200 ms request budget has over-limit device samples elsewhere in this evidence. T003/T066/T071/T074 and SC-007 remain open.

### Supplemental UV review disposition and remediation (2026-10-15)

- The independent AI reviewer response in `security-review-packet.md` accepted the corrected UV signal's cryptographic design and logic, and closed the false-UV finding. It rejected immediate production activation after finding three failing Android host tests and a local CI omission: generic `$Gradle test` did not run the KMP Android host suite. The reviewer recorded **NO** activation until these findings and T072/T075 are closed, and stated that the UV derivation logic does not need cryptographic re-review after the test stubs are corrected.
- Reproduced the pre-remediation result with `:feature:fido2:testAndroidHostTest --console=plain --rerun-tasks`: 528 tests, 3 failures due to missing strict-mock `CredentialSummary.getCredProtectPolicy()` answers. Corrected the three named test files with explicit policy values and current headless invocation expectations. Repeating that feature host command passed 528 tests with 0 failures, 0 errors, and 0 skipped tests (91 JUnit XML report files). Logs: `D:\temp\chimali-pre-fix-fido2-hosttest-20261015.log` and `D:\temp\chimali-uv-fix-fido2-hosttest-20261015.log`.
- Expanded `tools/local-ci.ps1` to run the Android host tests in `core:common`, `core:security`, `core:domain`, and `feature:fido2`. The post-remediation `tools/local-ci.ps1 -NoFix` run passed Ktlint, Detekt, release lint, compilation, generic unit tests, and the added host-test phase in 71.25 seconds; log `D:\temp\chimali-local-ci-post-review-20261015.log`.
- The reviewer stated cryptographic re-review is unnecessary once the test stubs are corrected. The evidence above verifies the corrected stubs, the 528/528 feature host suite, and expanded CI coverage; AF-UV-01 and AF-UV-02 are closed, completing T072/T075. The reviewer's earlier **NO** activation disposition was recorded before those conditions were met. This evidence records conditional-gate completion, not a deployment or activation event. Performance tasks and final traceability remain open.

### ASUS current-build RP latency retest at 20 ms report pacing (2026-10-15)

- With the current `com.chimali.shrunk` build and the loopback synthetic RP on port 4175, completed three consecutive browser authentication actions. The RP verified all three signatures; counters advanced `6 → 8 → 10 → 12`, and the harness reported `userVerified=false` as requested by its discouraged-UV flow.
- Android logged two one-candidate headless GetAssertion operations per browser action. The six `NFR-PERF-030` handoff timings were `191, 177, 215, 177, 196, 170 ms`. Five met the strict `<200 ms` budget; one was 15 ms over. The configured inter-report delay was 20 ms. This confirms a repeatable real-device flow and a remaining tail over budget; it does not establish a matched baseline comparison or latency compliance.
- Browser ceremony totals were `3,438.6`, `3,500.6`, and `3,377.7 ms`, including Windows/browser authenticator-selection and wait time. These values are not the app's operation budget. ASUS is supplementary to the selected Moto alternative; physical derivation behavior is covered by one-candidate operation logs and host verification, but physical zero-candidate/one-selected derivation counters are not instrumented. T003/T066/T071/T074 and SC-007 remain open.

### 10 ms HID report-pacing trial (2026-10-15; RP verified, performance gate remains open)

- Reduced the `BluetoothHidConfig.reportPaceDelay` from 20 ms to 10 ms after the 20 ms Perfetto trace showed inter-report spacing of roughly 22–25 ms and a 73–76 ms response-queue/handoff phase. Built `:app:assembleShrunkDebug` successfully; APK SHA-256 `413FD0264293FFF7D9FFA0F30CC38B553E41394FA513961B0057D51973C5FCCA`. Installed it with `adb install -r` to ASUS's disposable `com.chimali.shrunk` package, preserving the synthetic credential. The app log confirmed `reportPaceDelay=10ms`.
- The loopback RP verified three consecutive assertions after the change, advancing counter `12 → 14 → 16 → 18`. The six single-candidate GetAssertion handoffs were `231, 170, 195, 172, 163, 165 ms`: five passed `<200 ms`; the first operation after app restart was 31 ms over. The first browser action after the build restart also re-established HID registration and initialized the wallet root. The 10 ms median was 171 ms versus 184 ms for the immediately preceding 20 ms control sample (six operations each); these small sequential debug-build samples are directional and do not establish robust causal improvement or strict compliance.
- The same test APK was installed on Moto for cross-device validation. The app was advertising at 10 ms when a localhost RP attempt reached the Windows security-key wait state; no CTAP request reached Moto. The latest user observation below confirms that the Moto remained at “Ready for Authentication” without showing an approval prompt. This is not an interoperability result. Keep T003/T066/T071/T074 open until the matched workload and strict latency evidence are complete; do not treat this 10 ms trial as validated on the Moto.
- User reported the Windows prompt text “¿Estás ahí? Usa tu llave de seguridad para iniciar sesión” (Windows: “Are you there? Use your security key to sign in”). After responding, the Moto showed no confirmation/biometric prompt and remained at “Ready for Authentication.” The RP page still displayed a previously verified ASUS synthetic assertion; no result from this Moto retry was verified, and no CTAP request reached the Moto. This retry therefore does not establish Moto interoperability or latency.
- Final `tools/local-ci.ps1 -NoFix` run after the 10 ms pacing change passed in 60.39 seconds, including Ktlint, Detekt, release lint, compilation, generic unit tests, and explicit KMP Android host-test tasks. Log: `D:\temp\chimali-local-ci-10ms-20261015.log`. This confirms the current source builds and host checks pass; it does not close the open SC-007 device-performance criteria.

### Moto WebAuthn.io registration attempt (2026-10-15; CTAP reached app, credential creation failed)

- The initial WebAuthn.io retry at 12:50:08–12:50:16 failed before an Android HID connection. Windows `Microsoft-Windows-WebAuthN/Operational` recorded CTAP HID send/receive timeout `0x5B4`; the CTAP BLE provider recorded `0x80090035` (“device required by cryptographic provider was not found”). The Moto log had no HID connection or CTAP command at that time. These host-side events describe that initial retry only.
- A later WebAuthn.io retry did connect: Moto logs show the Windows PC connected at 12:51:27 and sent CTAPHID Init, followed by a successful GetInfo. WebAuthn.io's MakeCredential request listed algorithms `[-8, -7]`; Chimali selected EdDSA (`-8`) because the handler selects the first supported algorithm in the RP's list. After the user approved the phone prompt, Chimali displayed “Registration failed — Could not perform cryptographic operation.” The RP did not verify a credential. Further retries were canceled or timed out after the app remained in its error state.
- This confirms that one external request reached CTAP and got through GetInfo, but not successful credential registration or an RP-verified assertion. The app's generic crypto error and redacted device diagnostics do not expose whether failure occurred during key derivation, attestation signing, or persistence. Current Android device crypto coverage passes on ASUS for ES256, Ed25519, and ML-DSA-65; it does not reproduce this Moto/WebAuthn.io failure. Treat EdDSA selection as a diagnostic lead, not an established root cause. This attempt supplies no valid request-to-response performance sample. It does not close the matched baseline/current ceremony or other SC-007 criteria; keep T003/T066/T071/T074 open.



### Initial T003 ASUS clean release baseline (2026-10-15; superseded for completion status)

- Following the user's direction that `bug/056-credential-registration` is the new baseline, built commit `d9f668a7024fa43f699642e4f2fb8ff144d5b1f1` in clean detached worktree `D:\temp\chimali-t003-baseline-056` with `:app:assembleRelease :app:bundleRelease`. Both succeeded. Installed the signed `devRelease` APK to ASUS using ADB; this was a Gradle-built release installed from the isolated worktree, not an Android Studio Run/Debug build. Package `com.chimali` was not cleared.
- ASUS Zenfone 10 (`ASUS_AI2302`), Android 15/API 35, build `asus/WW_AI2302/ASUS_AI2302:15/AQ3A.240812.002/35.0604.0404.165:user/release-keys`, 7,387,708 kB MemTotal; thermal status 0. Release APK: 62,659,875 bytes, SHA-256 `AB0516AFD5A34DF808F3808C0E3BE6C215B527521182EBEA7810F89124551EBD`. Release AAB: 41,231,186 bytes, SHA-256 `3F3F226D5814FF5D7AB0190219FB09FAD1D66E9DD686D070FBA99F7C1E737025` (under the 50 MB ceiling). Local test signing certificate SHA-256 `f6c948b953eb7ec7153e95f0ae4830254c7b0f882cb741e54a18e21a2a3c7510`.
- Startup used 10 samples per mode after one excluded warm-up. ActivityManager `WaitTime` cold starts: median 344 ms, range 327–403 ms, nearest-rank p95 403 ms. Hot starts: median 56 ms, range 45–76 ms, p95 76 ms. Warm starts: median 105.5 ms, range 82–118 ms, p95 118 ms. These are activity-start completion timings, not first-frame/TTID. Raw samples: `D:\temp\chimali-t003-asus-d9-startup-samples.csv`.
- Started Chimali's Bluetooth HID authenticator and used the loopback-only, in-memory synthetic RP at `http://localhost:4173/`. The user approved ASUS biometric verification. The RP verified ES256 registration (`verified=true`, single-device credential, counter 0) and two assertions (`verified=true`, `userVerified=true`, counters 0→2→4). Registration browser ceremony was 11,930.2 ms (total 11,996.9 ms); assertions were 3,414.7 ms and 2,544.7 ms (totals 3,467.5 ms and 2,580 ms). These browser end-to-end values include host, transport, and user interaction, and are not the app's NFR-PERF-030 operation metric.
- The release process had 240,528 kB VmHWM after startup plus registration and two assertions; the final point snapshot was 203,484 kB VmRSS and `dumpsys meminfo` reported 137,360 kB PSS / 247,272 kB RSS. This is an observed session high-water/point sample, not a controlled fresh-process peak trace or matched comparison. The release `LatencyProfiler` emitted no NFR-PERF-030 or LatencyProfiler log lines, so app-side CTAP request-to-response latency could not be measured from this release run.
- At this capture's completion, app-side latency instrumentation, first-render measurements, a sampled session trace and fresh coverage inventory were still outstanding. Those gaps were subsequently addressed in the 2026-10-15/16 T003 follow-up below. The user waived Pixel 6a-class equivalence and designated ASUS Zenfone 10; no claim of equivalence is made.
### T003 ASUS baseline capture (2026-10-16; latency component reopened)

The baseline identity, build, startup, binary, memory, coverage and synthetic RP run were captured on the user-designated ASUS Zenfone 10 using `com.chimali`. Baseline production source is `bug/056-credential-registration` commit `d9f668a7024fa43f699642e4f2fb8ff144d5b1f1`. The app-side latency values in that original capture used a timing boundary later found to include application processing in the excluded user-verification interval; those latency values are superseded, and T003 remains open until corrected baseline latency is captured. Detailed original data and the correction notice are in [`tools/t003-baseline/results-2026-10-15.md`](../../tools/t003-baseline/results-2026-10-15.md). Current-feature comparison stays under T066/T071/T074.

- **Device/build and binary:** ASUS Zenfone 10 (`ASUS_AI2302`), Android 15/API 35. Release APK/AAB from uninstrumented baseline were 62,659,875 bytes and 41,231,186 bytes. The CTAP-instrumented release APK was 62,676,295 bytes; the TTFD follow-up APK was the same size. No instrumented AAB was built, so APK size is not an AAB comparison. The baseline AAB is below the constitution's 50 MB ceiling.
- **Startup:** Ten cold ActivityManager starts had median `WaitTime` 356.5 ms (331–405 ms). Ten Perfetto TTID samples had median 351.04 ms (326.59–4,800.75 ms); both long outliers are retained. After adding Compose `ReportDrawnWhen` for the persisted initial route, ten cold TTFD samples had median 480.5 ms (445–562 ms). These readings are below the constitution's 2 s cold-start budget. TTFD ends when the persisted initial route is ready to draw and excludes later asynchronous content.
- **Bluetooth HID/RP and app-side latency:** A disposable in-memory loopback RP verified one ES256 registration and two UV assertions. Browser ceremony times were 39,192.5 ms for registration and 2,543.7/2,600.2 ms for assertions; these include browser, Windows, Bluetooth and user interaction and are not the app operation budget. App instrumentation recorded one MakeCredential operation at 35,098.4 ms total, of which 34,989.2 ms was the user-interaction window and 109.3 ms was processing after subtracting that interval; four GetInfo operations took 82.0–96.7 ms and four GetAssertion operations took 130.0–176.2 ms. All nine successful-RP app-side operation samples were below the strict `<200 ms` boundary after the recorded user interval. HID timing ends at local `sendReport` API acceptance, not confirmed remote-host receipt.
- **Session memory:** A 300-second Perfetto capture sampled RSS every 100 ms during startup and the RP session. The sampled maximum was 245.65 MiB; process `VmHWM` was 259,964 kB (253.87 MiB), with session-end PSS 175,264 kB. Sampling cadence and process high-water semantics are documented. This is baseline evidence, not a matched feature comparison or proof of a controlled memory-tier budget.
- **Fresh coverage:** The isolated host-only JaCoCo 0.8.15 run passed all 630 host tests across nine modules; five configured tasks were `NO-SOURCE`. Repository instruction coverage was 26.85%, above the 20% repository floor. Security and common passed their applicable floors. Baseline deficits remain visible: domain 38.11% instruction / 21.96% branch, FIDO2 24.26% / 22.64%, database 12.12% instruction, and app 4.65% instruction. `hdkCoverageVerify` therefore exits nonzero on these measured baseline deficits; they are not reported as passes or missing evidence.

The capture report also records release variant, commands, package identity, sample ranges, trace location/hash, test-task selection, the isolated DataStore test-path repair, and limitations. T003 required these measurements and truthful baseline findings; it did not require the matched feature comparison assigned to T066.

### Feature-branch validation after restoring saved implementation (2026-10-16)

- Re-ran `tools/local-ci.ps1 -NoFix` on `codex/055-migrate-hdk-draft` after applying the saved implementation and resolving the three stash conflicts. Ktlint, Detekt, release lint, compilation, unit tests and the configured Android host suites passed. Host reports show 628 tests passed with no failures or skips: common 10, domain 47, security 43 and FIDO2 528. The transport lifecycle host test stubs Android's release measurement log because Android `Log.println` is not available in the host-test runtime; production instrumentation remains unchanged.
- Attempted to rebuild the current feature release APK and AAB with `:app:assembleRelease :app:bundleRelease`. The build reached `:app:buildReleasePreBundle` and failed with an insufficient-disk-space `IOException`. At failure, drive D had about 0.21 GiB free. The APK and AAB remaining under `app/build/outputs` have timestamps from before this attempt; they are not treated as current-feature artifacts or installed for new measurements. The ASUS `com.chimali` installation and app data were not changed.
- This validates the restored implementation and test integration only. It supplies no new matched performance sample and does not close T066, T071, T074 or their dependent T069 traceability closure.

### Matched ASUS feature release performance comparison (2026-10-16; supersedes the earlier build-failure status)

- Reclaimed build output space with Gradle's `:app:clean`, then successfully built `:app:assembleRelease :app:bundleRelease` from the restored feature state. Installed the signed optimized release to the user-designated ASUS Zenfone 10 (`ASUS_AI2302`, Android 15/API 35) as `com.chimali` with `adb install -r`; existing app data was preserved. Current APK is 62,692,679 bytes (SHA-256 `011E6B6341AEF3F2706A71B6F075DFD5FDD025CF49860C387E7DE02E3F8BEF68`) and AAB is 41,258,295 bytes (SHA-256 `848215A671616B756D8603E8F44C14D4991739BB9B4F350AD6B3C706E28DEA9B`). Both the APK and AAB are slightly larger than T003's baseline; the AAB remains below 50 MB.
- Ran the same five-minute Perfetto configuration and loopback RP workload as T003 with `userVerification: required`. The in-memory synthetic RP verified one ES256 registration and two assertions (`userVerified=true`, counters `0→2→4`). Feature release assertion browser totals were 6,306.9 ms and 5,570.1 ms, including Windows/browser and user interaction. Four app-side GetAssertion operations (two per browser assertion) reported accepted response handoffs. After subtracting their recorded user-interaction intervals, the four local handoff times were 128.174, 76.213, 114.509 and 73.273 ms; all were below the strict `<200 ms` budget (median 95.36 ms). The boundary is final local `sendReport` acceptance, not remote host receipt. Previously recorded over-budget debug samples remain part of the evidence and are not erased by this release result.
- Current `am start -S -W` cold WaitTime median was 374 ms (355–436), versus T003's 356.5 ms (331–405). Perfetto TTID median was 371.89 ms (352.45–419.71), versus baseline 351.04 ms (326.59–4,800.75; baseline outliers retained). Ten valid fully-drawn TTFD samples were 453, 513, 507, 503, 436, 534, 480, 466, 466 and 498 ms; median 489 ms (436–534), versus baseline 480.5 ms (445–562). All current startup/display medians remain below the 2 s budgets. One TTFD acquisition attempt lacked a system log event and was replaced with a captured sample; raw captures remain in `D:\temp`.
- The matched Perfetto trace `D:\temp\chimali-feature-asus-session-uv-required-20261016.perfetto-trace` is 110,905,764 bytes with SHA-256 `D0B30447DAFDFD13637A2292923AFC35BE7B85ED7244CD7565D30D461767C9C3`. It sampled the maximum process RSS at 234.41 MiB (2,739 samples for the primary `com.chimali` process), versus T003's 245.65 MiB sampled maximum. `/proc/<pid>/status` reported `VmHWM=244600 kB` (238.87 MiB), versus baseline 253.87 MiB. The trace contains 1,556 app frames (mean 8.39 ms, maximum 152.49 ms, 60 longer than 16.67 ms); these counts are descriptive and do not classify jank.
- The Perfetto trace recorded four `Fido2.Assertion.DeriveAndSign` sections, one for each of the four CTAP GetAssertion calls. `GetAssertionUseCaseTest` verifies across three candidates that the chosen descriptor is signed exactly once and candidate credentials are not hydrated; current code selects before derivation. Together these establish zero candidate-key derivations and one selected derivation/sign per CTAP operation. No separate device-side derivation counter exists. The two browser ceremonies each produced two CTAP GetAssertion operations, as in the baseline workload.
- Relative to T003, current APK size rose 32,804 bytes, AAB size rose 27,109 bytes, TTFD median rose 8.5 ms, and sampled session RSS maximum fell 11.24 MiB. The comparison is on the user-waived ASUS, not a claim of Pixel 6a equivalence. Full commands, raw values, validated PerfettoSQL, build hashes and limitations are in [`the matched ASUS comparison report`](../../tools/t003-baseline/feature-asus-results-2026-10-16.md).
- Re-ran `tools/local-ci.ps1 -NoFix` after the restored implementation and harness updates; it passed in 56.16 seconds. Ktlint, Detekt, release lint, compilation, generic unit tests and configured Android host suites passed. Host XML reports show 628 passing tests, with no failures or skips. The saved feature-implementation stash remains preserved; no commit was created.
- At the time of capture, this closed the matched ASUS workload in T066 and the user's waived reference-device measurement in T071. The later final-convergence addendum below records the explicit waiver applied to T074 and T069. No production deployment or activation is claimed.
### Local CI rerun (2026-10-16)

- Ran `tools/local-ci.ps1 -NoFix` from `codex/055-migrate-hdk-draft`; it exited successfully in 8.79 seconds. Ktlint, Detekt, release lint, compile-all, unit tests, and the configured Android host-test tasks all reported PASS. Gradle reused the configuration cache and marked 61 of 65 actionable tasks up to date, so this confirms the current checkout through the repository CI entry point but does not claim every test task was re-executed in this invocation. The task output records `:core:common:testAndroidHostTest`, `:core:security:testAndroidHostTest`, `:core:domain:testAndroidHostTest`, and `:feature:fido2:testAndroidHostTest` as successful.

### Earlier convergence and traceability checkpoint (2026-10-16; historical)

The 2026-10-15 FR/SC matrix above is the requirement crosswalk; historical open dispositions in earlier dated entries are superseded by this final update. Every FR-001–FR-019 and SC-001–SC-010 has an evidence reference in the crosswalk. The following current artifacts establish the final status:

- The implementation, contracts, frozen vectors, restart evidence, clean-install schema, and removal audit cover FR-001–FR-012 and FR-014–FR-019, including metadata persistence, supported algorithms, failure/cancellation behavior, full-ID binding, and candidate-only signing. FR-013 review and the assertion-UV supplemental disposition are linked in [`security-review-packet.md`](security-review-packet.md), [`independent-formal-security-review.md`](independent-formal-security-review.md), and [`t072-fr013-negative-assurance-review.md`](t072-fr013-negative-assurance-review.md). Their acceptance is limited to the named local profile and reviewed source scope.
- T003 baseline and the earlier matched feature measurements are in [`the baseline report`](../../tools/t003-baseline/results-2026-10-15.md) and [`the ASUS feature comparison`](../../tools/t003-baseline/feature-asus-results-2026-10-16.md). The user explicitly waived the Pixel 6a-class requirement and selected ASUS; no Pixel-class equivalence or fresh Moto-specific result is claimed. Following the corrected timing-boundary change, the earlier feature latency series is superseded for current latency claims and T066/T071/T074 were reopened pending the corrected matched capture under T082.
- The earlier reports record successful required-UV synthetic RP registration/assertions, pre-correction app-side handoffs, signing derivation evidence, startup/TTID/TTFD, APK/AAB sizes, and sampled session RSS. Corrected current-release values and their limits are in the later `Corrected ASUS release latency retest` section. HID completion means local API acceptance, not verified remote Windows receipt. The baseline coverage deficits and lack of a physical derivation counter remain disclosed; neither is represented as a pass.
- T067's local quickstart/RP scenario and its rejected browser attempts are recorded above. T061–T065, T067, T070, and T072–T075 remain linked to their actual reports; T069 is reopened until corrected SC-007 evidence is reconciled. The current Constitution 3.0.0 and feature-specific clean-install authorization are linked in [`constitution-amendment.md`](constitution-amendment.md); current schema/Kotlin call sites omit `private_key_alias` without an upgrade-compatibility claim.
- `tools/local-ci.ps1 -NoFix` passed on 2026-10-16. Gradle reused its configuration cache and marked 61/65 tasks up to date; this is recorded as a successful pipeline invocation, not as a claim that all cached tasks re-executed.

This earlier T069 closure was superseded by the T082 timing-boundary correction documented below. At that checkpoint, T069 was reopened for corrected SC-007 evidence and crosswalk reconciliation. The current T069/T083 disposition is recorded in the later documentation reconciliation section. No production deployment, activation, hardware-backed custody, remote-host receipt, or draft-issuance interoperability claim is made.

### Tier 1 traceability audit (2026-10-16)

- Scanned the changed Kotlin production inventory in `core/security`, `core/domain`, and `feature/fido2` for function-attached `@Req` declarations. The scan covered 242 non-private production functions, including the `expect`/`actual` secure-random pair; all 242 have a direct requirement annotation. The Android `actual` declaration was annotated to match its `expect` declaration.
- Scanned the changed Tier 1 test inventory for stable requirement references attached to each test. All 495 `@Test` sites have a KDoc `@see` referencing a stable BRD/TRD requirement; no gaps remain in this scanned inventory.
- Verification command: `:core:common:ktlintCheck :core:domain:ktlintCheck :core:security:ktlintCheck :feature:fido2:ktlintCheck :core:domain:testAndroidHostTest :core:security:testAndroidHostTest :feature:fido2:testAndroidHostTest --no-configuration-cache`. It passed. The latest host XML reports show 47 domain, 43 security, and 536 FIDO2 tests, with zero failures, errors, or skips.
- This is a static traceability and configured host-suite check. It does not assert that every feature-level acceptance or external security/performance gate is closed; those remain governed by the open convergence tasks below.

### Corrected ASUS release latency retest (2026-10-16)

- Installed the rebuilt `com.chimali` release APK on the user-designated ASUS Zenfone 10 (Android 15/API 35). APK SHA-256: `11D628BBCF23E638221AA27FB95A50F0FF82DC87A063841B7312C08E5607A5AE`; size 62,709,063 bytes. The timing fix ends the excluded user-verification window at the confirmation signal, before derivation, signing, persistence, and response construction resume.
- The loopback-only synthetic RP at `localhost:4173` verified one ES256 registration and two assertions (`userVerified=true`; counters advanced 0→2→4). Browser ceremony totals were 11,562.9 ms for registration and 5,576.7/6,189.7 ms for assertions. These include host/browser and user wait and are not app-side latency values.
- Filtered device Logcat confirmed `reports_accepted=true` and `user_window_open=false` for completed measurements. The post-reset registration record was `total=8,080.986 ms`, `user=7,774.792 ms`, `system=306.194 ms`; the human verification wait is excluded from `system`. Four GetAssertion records across the two RP assertions had system time `128.688, 135.548, 130.876, 145.671 ms`; all are below the strict 200 ms app-side handoff threshold. The first two app-side measurements per user action are retained as separate CTAP requests, matching the workload's multiple-GetAssertion behavior; local wrapper acceptance does not prove remote Windows receipt.
- The five-minute Perfetto trace is `D:\temp\chimali-t082-corrected-feature-asus-20261016.perfetto-trace` (134,566,607 bytes). For primary process `com.chimali` it contains 3,000 RSS samples, maximum sampled RSS 204.70 MiB and sampled `VmHWM` 261.07 MiB. Trace health reports one `config_write_into_file_no_flush` warning and 88 `systrace_parse_failure` import warnings. The trace's async `T003.GetAssertion` slices are incomplete for part of the second ceremony, so Logcat is the source for the four completed app-side latency values. Raw APK, trace and filtered Logcat remain outside the repository under `D:\temp`.
- This is corrected current-build evidence, not a matched corrected baseline/current comparison. The earlier feature comparison predates this timing fix and its latency values are superseded for the corrected user-window boundary. Do not use this capture to assert baseline-relative latency or memory change. T082 remains open until the same corrected release workload is captured on the baseline source and compared with the current release; previous measurements and limitations remain preserved in the dated reports.

### Final CI and baseline-source build attempt (2026-10-16)

- The first `tools/local-ci.ps1 -NoFix -NoConfigurationCache` run exposed Detekt `SwallowedException` findings in the registration and authentication foreground-prompt cancellation handlers. Updated both handlers to rethrow cancellation when the prompt was replaced or already authorized; a genuine foreground-loss cancellation still completes the existing `OperationCanceled` result. The rerun passed Ktlint, Detekt, Release Lint, Compile All, Unit Tests and KMP Android Host Tests in 99.95 seconds. The Gradle output records many tasks as `UP-TO-DATE`; this is a successful repository-pipeline run and not a claim that all suites were forcibly re-executed.
- To avoid switching the active branch or modifying its staged feature work, exported `codex/t003-baseline-instrumentation` to `D:\temp\chimali-t003-baseline-source-20261016` and attempted `assembleRelease`. The source compiled through most release tasks, then failed because the D: volume ran out of space before producing the APK. The baseline APK was not installed, and the ASUS app/data were not changed by this attempt. At failure the volume had about 5.8 MB free. T082 and the reopened SC-007 tasks remain pending until sufficient space is available for the baseline build and matched capture.
- The temporary baseline source/build directory is outside the repository and therefore does not affect Git status. The shell's recursive-delete safety policy rejected cleanup of that exact temporary directory after its absolute path was verified; it remains at the path above and is consuming disk space. No other user files were targeted.

### Corrected baseline rebuild and matched ASUS RP latency — 2026-10-16

- After the user freed disk space, `assembleRelease --no-configuration-cache` succeeded from the saved baseline source snapshot. Source was `codex/t003-baseline-instrumentation` commit `daf1fbe60984b8a0f87f43f730c331bc2b1f2944`, descended from pre-feature commit `d9f668a7024fa43f699642e4f2fb8ff144d5b1f1`. The measurement branch adds T003 handoff instrumentation and `ReportDrawnWhen` startup reporting; no local-HDK feature implementation was in the baseline source. The build completed in 1m55s.
- APK identity: `com.chimali`, version `0.1.0`, 62,676,295 bytes, SHA-256 `04B00252A8EF59C6477E54A051ADDEC016FC2B40F4F3794AFA960A364A19C4A5`. Baseline and feature APKs had the same Android Debug signing certificate SHA-256 (`f6c948b953eb7ec7153e95f0ae4830254c7b0f882cb741e54a18e21a2a3c7510`). Installed via `adb install -r` on ASUS Zenfone 10 (`ASUS_AI2302`, Android 15/API 35); app data was preserved. After capture, the current feature APK (62,709,063 bytes, SHA-256 `11D628BBCF23E638221AA27FB95A50F0FF82DC87A063841B7312C08E5607A5AE`) was restored with `install -r`; `com.chimali/.MainActivity` was resumed and the UI reported `Advertising...` / `Ready to pair with a new host PC`.
- The loopback-only, in-memory synthetic RP accepted one ES256 registration and two assertions. It verified a single-device credential, initial counter 0, assertion counters `0 → 2 → 4`, and `userVerified=true` for both assertions. Browser ceremony timings were 8,265.5 ms for registration and 10,555.5/8,065.1 ms for the assertions; these include browser/Windows/Bluetooth and user time. One initial registration attempt was rejected for a challenge mismatch after a repeated activation started a second pending request. The harness process was restarted, clearing all in-memory test state; a clean registration and both assertions then verified. The failed attempt is excluded from the successful sample.
- Successful-capture app logs all reported `reports_accepted=true` and `user_window_open=false`. MakeCredential was 3,505.818 ms total, with 3,381.467 ms in the measured user window and 124.351 ms corrected local handoff. Four GetInfo operations were 81.173–97.612 ms. The two browser assertions generated four GetAssertion records: total 171.718, 8,087.451, 144.488, and 5,637.528 ms; recorded user intervals 0, 8,008.268, 0, and 5,556.007 ms; corrected local handoffs 171.718, 79.183, 144.488, and 81.521 ms. All four are below 200 ms; their median is 113.005 ms. The corrected boundary is the instrumented interval from CTAP dispatch through local `sendReport` acceptance after subtracting the app-recorded user-verification window. It does not prove host receipt.
- Matched corrected feature-release GetAssertion samples were 128.688, 135.548, 130.876, and 145.671 ms, median 133.212 ms. The feature median is 20.208 ms (+17.9%) above this small baseline sample; all eight operations passed the strict 200 ms target. These are four CTAP operations from two RP assertions per build, not eight independent ceremonies. The single MakeCredential handoffs were 124.351 ms baseline and 306.194 ms feature after user-window exclusion; the feature registration sample exceeded 200 ms, and one sample per build is not a distribution. Browser ceremony totals are reported separately and are not substituted for app-side values.
- Corrected baseline RP details, APK identity, and full timing table are in `tools/t003-baseline/results-2026-10-15.md`. Existing baseline coverage evidence remains a fresh 630-test host run with recorded coverage-floor failures (coverage was measured, not certified as passing); existing startup/TTFD and session-memory captures remain valid. The corrected five-minute feature memory trace and the previously matched startup/binary/session-memory results are in `tools/t003-baseline/feature-asus-results-2026-10-16.md`. This closes the corrected baseline capture previously blocked by disk space and supplies the matched corrected latency comparison; no Pixel-class equivalence, remote-host receipt, or production performance claim is made.



### SC-007 instrumented ASUS capture 1 — 2026-10-16 (partial)

- Installed release `com.chimali` on the user-designated ASUS Zenfone 10 (Android 15/API 35). Chimali was advertising. The loopback in-memory RP verified the first ES256 registration (`userVerified=true`, single-device credential). RP sample ID: `c6858c25-8f48-4e42-9c64-18d717989a31`.
- Browser options-to-RP-verification total: `10,624.3 ms`. Matching app operation IDs were `1` (GetInfo) and `2` (MakeCredential). The matching `user_ns` sum was `3,763,345,624 ns` (`3,763.345624 ms`), all from MakeCredential. The server recorded adjusted end-to-end `6,860.95 ms`, over the strict `<1,000 ms` target. The sample is retained as an over-budget verified ceremony; the remaining delay has not been isolated to a component.
- GetInfo: `internal_ns=1,057,396` (`1.057 ms`), `internal_budget=PASS`, `reports_accepted=true`, `user_window_open=false`.
- MakeCredential: `processing_ns=4,011,163,592`, `user_ns=3,763,345,624`, `internal_ns=247,817,968` (`247.818 ms`), `internal_budget=OVER_BUDGET`, `reports_accepted=true`, `user_window_open=false`. This exceeds the strict `<200 ms` internal budget by about `47.818 ms` after excluding the measured user wait.
- This is one RP ceremony and one sample each for GetInfo and MakeCredential. It does not meet the 10-sample-per-operation requirement. T084/T085 remain open; further captures must retain over-budget outcomes and identify the remaining end-to-end delay before compliance can be claimed. The harness keeps samples in memory, so this entry preserves the result beyond a server restart.

### SC-007 instrumented ASUS capture 2 — 2026-10-16 (partial)

- The loopback in-memory RP verified a registration (`userVerified=true`, single-device credential). RP sample ID: `00b1ae06-9b6f-421f-9f90-521a5a53837b`. Browser options-to-RP-verification total: `8,544.3 ms`.
- The corresponding app log sequence contained IDs `3–6`: GetInfo, GetInfo, GetAssertion, and MakeCredential. The sum of `user_ns` for the sequence was `3,313,526,510 ns` (`3,313.52651 ms`), from MakeCredential. The server recorded adjusted end-to-end `5,230.77 ms`, over the strict `<1,000 ms` target.
- Internal operation times were: GetInfo `9.279 ms` and `8.685 ms` (pass); GetAssertion `113.421 ms` (pass); MakeCredential `284.752 ms` (over the strict `<200 ms` target by `84.752 ms`). All four records had `reports_accepted=true` and `user_window_open=false`.
- This is the second RP-verified ceremony. It leaves T084 below its required 10 samples and T085 with fewer than 10 samples for each operation. The residual end-to-end delay and MakeCredential processing cost need further profiling; no component-level cause is established by this sample.

### SC-007 controlled registration retry — 2026-10-16 (capture 3, partial)

- The agent started exactly one Register ES256 action after checking the ASUS was connected to the Windows host. The user completed the ASUS confirmation and biometric prompt. The RP verified registration, sample `7a247e30-9860-4110-b61a-2517cc2a7a92`; the agent entered matching timing/IDs in the page and recorded it successfully (3/10 ceremonies stored).
- Browser timer boundaries were inspected: `performance.now()` begins in the Register click handler before options retrieval and ends after RP verification returns, before the manual sample form. Filling the form and replying in chat are excluded. Options round trip was `17.0 ms`, WebAuthn ceremony `6,769.2 ms`, RP verification round trip `8.0 ms`, total `6,794.2 ms`.
- Device sequence: GetInfo ID `7` (`internal_ns=2,933,073`), GetInfo ID `8` (`internal_ns=3,811,094`), GetAssertion ID `9` (`internal_ns=77,645,781`), MakeCredential ID `10` (`internal_ns=229,252,813`). All reports were accepted and all user windows were closed. The GetAssertion preceding registration is not a separate RP-verified authentication sample.
- MakeCredential total through local HID acceptance was `3,505.034687 ms`; measured user-verification wait was `3,217.731353 ms`. Its adjusted local handoff interval was `287.303334 ms`, and its internal processing interval was `229.252813 ms` (over the strict 200 ms budget). These boundaries explain why the phone's post-verification response can feel much faster than the full browser click-to-verification interval; the exact phone success-frame time was not measured.
- The matching user-wait sum was `3,217.731353 ms` across operation IDs `7,8,9,10`. Adjusted end-to-end was `3,576.47 ms`, above the strict 1,000 ms target. Browser/Windows discovery, selection, Bluetooth and RP overhead remain included, with only the measured app user-verification interval removed. Further profiling is required to attribute that residual delay. T084/T085 remain open.
- Visible RP result screenshot: `D:\temp\chimali-sc007-registration-retry-20261016.png`.

### Earlier end-to-end deferral and T069/T083 reconciliation — 2026-10-16 (superseded by all-performance deferral below)

- The user assessed the ASUS/Windows solution as good from a UX perspective and explicitly requested a future-session backlog item for investigating the end-to-end measurement. Created the superseded T084 investigation, now replaced by [Feature 060](../060-fido2-latency-measurement/implementation-evidence.md) with the three raw/browser-residual/internal results, unmeasured Windows interaction, timing/correlation questions and completion criteria. The suspected measurement issue is not treated as a proven cause. T084 remains unchecked and is explicitly DEFERRED; no further end-to-end investigation or sample campaign is requested in this session.
- The original <1s end-to-end target remains documented. Subtracting only Android's measured prompt interval leaves any Windows interaction in the residual, so existing values do not establish system-only processing or post-fingerprint response. The user's UX acceptance is distinct from numeric compliance.
- Completed T069's FR-001–FR-019/SC-001–SC-010 crosswalk reconciliation and T083's feature/status documentation work: updated spec, plan, task accounting, documentation review, quickstart, changelog, security-packet status and current feature performance report. Historical dated results remain available. Corrected the obsolete baseline-build-pending and blanket SC-007-complete statements.
- Task accounting is now 85 total: 83 complete, 1 active (T085), 1 deferred (T084). Documentation completion explicitly leaves SC-007 partial. T085 retains the all-operation <200ms target, MakeCredential samples 229.253–284.752 ms, the incomplete 10-sample-per-operation campaign and candidate-derivation evidence requirements. Diagnostic helper traceability/style must be included when finishing that implementation; the earlier T081 source audit remains the dated audit of its recorded inventory.
- Scoped security-review and supplemental UV finding closure remain recorded under T072/T075. The documentation update changes no cryptographic profile, review disposition, numeric threshold or deployment state. Baseline coverage deficits and later feature coverage results remain distinguished by their dated reports.
- Validation for this change is documentation link, checklist/task-accounting and diff consistency review. No build or test suite is rerun for the documentation-only update. No staging or commit is performed; optional implementation commit hooks remain unrun.
- Live diagnostic capture was stopped when deferring the investigation. A final tag-only Logcat snapshot is retained outside the repository at `D:\temp\chimali-fido2-deferred-latency-logcat-20261016.txt`. It also contains additional CTAP activity after the three correlated samples; those operations have not been paired to RP results and are not counted as additional validated end-to-end samples.

### T085 profiling checkpoint and user-directed deferral of all performance enhancements — 2026-10-16

- The user explicitly requested pausing all performance enhancements for a later phase. Created `docs/backlog/fido2-internal-performance-enhancements.md`, linked it to the previously deferred end-to-end item and marked T085 DEFERRED. Both T084 and T085 remain unchecked for future work; feature 055 now has 83/85 tasks complete, 0 active and 2 deferred. SC-007 remains partial and the numeric targets are unchanged.
- Before that direction, added secret-free async markers around registration stages and wallet initialization, plus helper requirement annotations/KDoc and a named internal-budget constant. Diagnostic changes are retained. No settings lookup optimization or test replacement has been implemented; `Fido2SettingsRepositoryImpl` still contains its legacy preference lookup.
- Validation before deferral: `:feature:fido2:testAndroidHostTest --tests '*RegisterCredentialUseCaseTest' --tests '*WalletMasterSeedProviderTest' --tests '*InternalOperationTimingTest' :feature:fido2:ktlintCheck :feature:fido2:detekt --no-configuration-cache` passed. XML totals: 47 tests, zero failures/errors. A line-length Detekt finding in the new trace marker was corrected before the successful rerun. `:app:assembleRelease --no-configuration-cache` then passed in 1m36s. Installed the release as `com.chimali` on ASUS, preserving app data; APK SHA-256 `3E7FA9EDBDEB252F2DC17980C159981FEEDD2FCAB75BE3712813D5538606FA27`.
- The user started the authenticator and completed one registration while tracing. The RP verified the registration. App process 24394 recorded GetInfo ID 1 at 1.045ms internal, pre-registration GetAssertion ID 2 at 89.712ms internal, and MakeCredential ID 3 at 239.385ms internal (over budget). MakeCredential prompt time was 3146.372ms and adjusted local HID handoff was 290.852ms. Reports were accepted and user windows closed. This was an internal profiling run; no deferred end-to-end sample was recorded.
- Perfetto capture: `D:\temp\chimali-t085-registration-before-20261016.perfetto-trace`, 16,682,733 bytes, SHA-256 `B542FD55BC54EA7DC92D34E0E002CC96F01F64D730740C2885AF84B17FA1AEA8`. The official recorder was used; after stopping the host process the identified device Perfetto process was signaled to flush and the final complete file was pulled. Kernel symbols were unavailable. Application stages were readable. Windows warm-session querying was unavailable, so schema-checked queries ran against the saved trace directly.
- The trace shows registration persistence 24.364ms, public-key generation 10.948ms, attestation 14.006ms, relying-party work 12.813ms and keepalive shutdown 0.560ms. A 134.339ms gap falls between statistics completion and public-key generation, around `settingsRepository.getMaxCredentialCount()`. Source inspection shows that call opens legacy EncryptedSharedPreferences on every read. Its wall interval also includes scheduling; the exact component split and proposed removal remain future work. The backlog contains the validated SQL, timing coordinates, proposed checks and completion criteria for all internal FIDO operations.
- Reconciled spec, plan, tasks, quickstart, changelog, documentation review, security-packet status and current performance reports to the new user-directed deferral. Earlier entries describing T085 as active are historical. Existing review findings and ownership/crypto behavior are unchanged. No build or test is rerun after the pause direction, and no commit is created. Optional implementation commit hooks are left unrun.

### T086–T090 corrective implementation checkpoint — 2026-10-16

- T086 now maps CTAP `options.uv=true` to `AuthenticatorSelectionCriteria.userVerification=REQUIRED`; a handler regression inspects the dispatched registration options. The existing T078 use-case/prompt coverage remains the denial/signing/UV-flag policy evidence.
- T087 registration and authentication prompt launches now carry monotonically increasing prompt IDs. Result callbacks are consumed once only while the matching request awaits UV; ending/replacing a request sends a prompt-cancel effect. Added old-result-after-replacement and duplicate-result regression sources.
- T088 the HID transport now starts one default 120-second deadline at CBOR request admission and places authenticator-owner acquisition plus MakeCredential/GetAssertion handling under its remaining budget. Timeout maps to CTAP `USER_ACTION_TIMEOUT` and keepalive cleanup remains in `finally`. Added deterministic stalled-bootstrap and stalled-assertion-handler regression sources. The provider's synchronous work is still only coroutine-cancelled; no synchronous interruption claim is made.
- T089 credential projection creation and searchable-metadata backfill now use a mutable JSON builder, UTF-8 encoder-owned byte buffer, direct byte-to-char decryption and in-scope JSON parsing. Serializer, AAD, decrypted bytes and chars are erased in `finally`; credential metadata String-based service APIs were removed. Round-trip, encryption-failure and malformed-decryption buffer regressions were added. Parsed RP/user identifier values remain immutable domain strings by design; whole-payload serialization/decryption copies were removed.
- T090 replaced the production allow-list `!!` with null-safe handling and removed the mapper's two broad `Exception` catches in favor of supported decoding/authentication failures. Added allow-list filtering coverage.
- No build, test, device flow or profiling command was run for this checkpoint. The new and updated regression sources and prior T078 protections still require configured execution/static validation before T086–T090 are closed; these tasks remain unchecked. No staging, commit or optional auto-commit hook was performed. T084/T085 remain deferred.


### T091/T093/T097 verified corrective checkpoint — 2026-10-16

Current accounting: **97 total; 86 complete, 9 active corrective tasks, 2 user-deferred performance tasks**. T091, T093 and T097 are closed for the implemented corrections and configured regression evidence below. T086-T090/T092/T094-T096 remain open. These host results do not substitute for the complete request-lifetime/device/coverage gate or T094's remaining ownership/classification review. T084/T085 remain deferred; no performance campaign, deployment or activation was performed.

Behavioral red evidence: the initial metadata suite failed two valid operations with `BufferUnderflowException`; the event-buffer growth case failed before old-array erasure; three immediate-dispatch/delayed-availability cancellation cases failed before admission fencing. The initial mapper suite executed 15 cases, with 13 malformed partitions failing before typed containment and identity/numeric validation. The seven-case JSON suite incorrectly accepted raw controls and duplicate fields. These are observed behavioral failures, not missing-symbol or empty-suite evidence.

Implemented: normal UTF-8 completion succeeds; credential JSON builders/parser values use erasable storage, and decrypted bytes enter cleanup before character allocation. The mapper erases parsed field arrays after agreement or mismatch without creating immutable decrypted copies. Event/normalization owners erase replaced backing arrays. Request child scopes explicitly inherit ViewModel/request jobs without the deprecated Job-taking launch overload; terminal UV rejection completes its request owner. Full pending-request policy coverage remains T092/T095 work. Invalid current record construction/decoding returns a fixed typed error without original exception payloads, validates algorithms before narrowing, and rejects conflicting credential identities. List/search hydration retains healthy neighboring records.

| Requirement / task | Exact configured evidence |
| --- | --- |
| T091 ASCII conversion and persistence | `CredentialMetadataProtectionServiceOwnershipTest`.`credential metadata encryption and decryption erase owned plaintext buffers`; both `RegistrationAuthenticationDataIntegrationTest` methods: `full registration and authentication flow with real data persistence` and `authentication succeeds with migrated credential schema`. The setup now uses real metadata serialization/parsing with a synthetic reversible encryption stub; it does not prove platform AES-GCM or Keystore behavior. |
| T091 Unicode/escaped hydration and rollback | `CredentialRepositorySearchableMetadataTest`.`real metadata codec hydrates unicode projections and preserves rollback on encryption failure`: real SQLDelight database, DAO replacement, metadata codec and public-key decoder; exact RP/user/profile/key agreement, prior-row retention after rollback and retained plaintext erasure. |
| T091 malformed encoding versus JSON | `CredentialMetadataProtectionServiceOwnershipTest`.`failed encryption and malformed decryption still erase owned buffers` expects `CharacterCodingException` for C3 28; `malformed JSON is rejected and decrypted bytes are erased` expects `IllegalArgumentException` for five separate JSON partitions. |
| T093 serializer growth and failure cleanup | `PasskeyEventStorageOwnershipTest`.`serializer growth erases every replaced plaintext buffer`, `erasable JSON serializer buffer is wiped after success and serialization failure`, `event append failure erases owned key and plaintext`, `cancelled event append rolls back and erases owned key and buffers`, and event/replay and snapshot round trips. |
| T093 mutable normalization | `ErasableCharBufferTest`.`growth erases replaced arrays and final erasure includes unused capacity` and `range views borrow the owner and invalid ranges cannot mutate its content`; `Bip39MasterSeedGeneratorTest`.`expanding normalization erases replaced and final arrays on failure`, independent 64-byte BIP39 fixture and borrowed-input/failure cases. Android ICU is not executed by the host seam; dependency limitations and required device refresh remain explicit. |
| T094 progress, not closure | `CredentialMetadataProtectionServiceOwnershipTest`.`partial parsing and encryption cancellation erase retained character owners`, `long escaped unicode metadata round trips with independently erasable field owners`, `escaped unicode codepoints parse without immutable intermediate fields`; `EntityMappersTest`.`parsed metadata owners erase on both valid and inconsistent hydration`. Borrowed sensitive domain/projection String boundaries and final copy-lifetime classification remain open in `security-review.md`. |
| T097 malformed records and valid counterpart | `EntityMappersTest`.`malformed stored fields return fixed typed errors without exception payloads` has 14 partitions for IDs, conflicting IDs, AAGUID, profile, algorithm overflow/unsupported values, counter, name, RP, timestamps and public-key decoding; each requires a fixed error and no cause. `valid record preserves identity profile registered key and counter` and `metadata cancellation propagates unchanged` cover successful reconstruction and the exact cancellation object. |
| T097 repository behavior | `CredentialRepositoryImplTest.CredentialManagementTests`.`invalid hydrated row returns null without escaping validation`, `mixed valid and malformed rows preserve healthy list and search results`, `summary rejects numeric algorithm truncation without public key hydration`: exact surviving IDs, no writes/event execution and metadata-only summary reads. |

Successful command (Android Studio JBR as JAVA_HOME):

```powershell
./gradlew.bat :core:security:testAndroidHostTest :feature:fido2:testAndroidHostTest :core:security:detekt :feature:fido2:detekt :core:security:ktlintCheck :feature:fido2:ktlintCheck :feature:fido2:compileAndroidDeviceTest :app:compileDebugKotlin --continue --no-configuration-cache --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 21s`. XML under `core/security/build/test-results/testAndroidHostTest` and `feature/fido2/build/test-results/testAndroidHostTest` contains **46 and 582 tests, zero failures/errors/skips**. Both modules' formatting ran separately before validation. Earlier static findings (line lengths, explicit swallowed timeout naming, codec constants and test-string conventions) were fixed without suppression. Toolchain output retains Gradle deprecation notices and an existing Compose test-rule API deprecation; passing Detekt/Ktlint is not a warning-free dependency claim.

Fresh local CI, affected-module/repository coverage and connected ownership/UI execution remain T095 obligations. `adb devices -l` returned no devices; an ASUS reconnection request is pending. No device suite was attempted without a target. The engineering checkpoint does not extend earlier formal dispositions to unreviewed source.

The assertion-quality/static gap review uses concrete value/error/cleanup/cancellation/interaction assertions; its mutation reasoning was not executed as semantic mutations. The source-pairing analyzer prerequisite `tree-sitter-language-pack` was unavailable; no alternate pairing audit or package installation is claimed. Test artifacts remain in ignored build directories or non-stageable `.git/testagent/hdk-remediation-20261016`. Existing staged work and stashes were preserved; no staging/commit was performed. Optional implementation commit hooks remain unrun.


### T086/T096 host verification and request-lifetime progress - 2026-10-16

Current accounting: **97 total; 88 complete, 7 active corrective tasks (T087-T090/T092/T094/T095), 2 deferred performance tasks (T084/T085)**. T086 and T096 close for the observed host policy/diagnostic scope below. T087/T088/T092 remain open for deterministic real-handler deadline/transport and connected evidence; T089/T094 retain the sensitive model/projection ownership obligation. T090's source correction and focused behavior pass, with final crosswalk/verification reconciliation still tracked alongside T095. No performance campaign, deployment or activation was performed. The user chose host work while ASUS is unavailable.

| Task / boundary | Current evidence and limits |
| --- | --- |
| T086 | RequiredRegistrationVerificationIntegrationTest: 5 parameterized cases, actual MakeCredential handler, bus, registration presenter and registration use case. Required unavailable, failed and cancelled cases assert zero key-generation/sign/persist calls. Required success and optional NONE assert one of each and exact signed/output flags 0x45/0x41. Crypto/provider/repository edges are synthetic stubs; no additional hardware/signature claim. |
| T096 | CapturedDiagnostics captures unsanitized source Kermit messages and throwable traces. Ctap2MakeCredentialHandlerValidationTest covers RP/account markers on success/malformed/failure/cancelled paths; RegistrationOwnerIntegrationTest covers actual dispatch/cancel; Ctap2GetAssertionHandlerTest covers NO_CREDENTIALS; GetAssertionUseCaseTest and SelectCredentialUseCaseTest cover fixed typed errors; CredentialRepositoryImplTest covers 7 RP/user/search/projection/event/summary/save failure partitions including exception messages/causes. Successful UI/protocol data and typed/status behavior are preserved. Ten source-leak regressions failed before redaction and passed after. Classification/scope are recorded in security-review.md. |
| T087/T092 progress | Fido2UiEventBusTest: 4 cases including no-presenter expiration, already-expired admission and successor preservation. RegistrationOwnerIntegrationTest: 17 cases; AuthenticationIntegrationTest: 21. New assertions cover generation fencing, external normal terminal errors, competing completion on success/error/UV failure, terminal Retry, processing duplicates, prompt cancellation and old success/failure while a fresh request remains live. Cancellation releases the observed test owner; this is not a production-provider cache claim. |
| T090 progress | Current mapper uses specific validation/security/coding catches, explicitly rethrows cancellation and returns fixed malformed-record errors without causes. Current allow-list uses orEmpty(), with matching-ID and discoverable policy cases in GetAssertionUseCaseTest. No production non-null assertion or broad Exception/Throwable catch remains in these two assessed files. Full affected static/host checks pass; broader final status/review reconciliation remains open. |

Observed red/green evidence: cached-request tests failed 5/12 before completion-bound clearing; wallet-generation tests failed 2/22; external normal terminal error tests failed 2/24. Competing response completion failed 4/28 before honoring complete(false); subsequent verification-failure boundary cases failed on both presenters before cleanup was centralized. Both terminal Retry regressions failed before prohibiting response-less resurrection. Corrected focused suites and the final full run passed. One malformed test expectation was corrected to existing status 0x36 without altering production behavior. An existing timestamp test assumed successive clock reads differ; its input now has a valid known-past creation/use timestamp, and the result must lie within the operation's before/after window without sleeps.

Final successful affected command, with Android Studio JBR as JAVA_HOME:

```powershell
./gradlew.bat :core:security:testAndroidHostTest :feature:fido2:testAndroidHostTest :core:security:detekt :feature:fido2:detekt :core:security:ktlintCheck :feature:fido2:ktlintCheck :feature:fido2:compileAndroidDeviceTest :app:compileDebugKotlin --continue --no-configuration-cache --console=plain
```

Exit 0, BUILD SUCCESSFUL in 28s. Current XML reports contain **46 security and 627 FIDO2 tests, zero failures/errors/skips**. Both modules' Detekt/Ktlint, FIDO2 device-test compilation and app debug compilation passed. Formatting ran separately beforehand. Earlier static failures (duplicate error literals and repeated throws) were corrected without suppressions. The test-only competing-completion delegate explicitly opts into the pinned coroutine inheritance API to control that boundary; production uses library CompletableDeferred instances. Dependency/toolchain deprecations remain visible. Validation log: non-stageable `.git/testagent/hdk-remediation-20261016/host-final-green.log`; reports remain under ignored module build directories.

Assertion-quality/static gap review maps each change to exact statuses/typed errors, absent causes, marker exclusion, actual callback IDs, canceled effects, response identity and zero/one side-effect counts. This is static mutation reasoning, not executed semantic mutations. The previously missing tree-sitter-language-pack prerequisite remains unresolved; no source-pairing audit is claimed. Fresh connected ownership/UI checks, module/repository coverage and scoped assurance reconciliation remain T095 obligations. Earlier formal review acceptance is not automatically extended to this changed source. Existing staged changes and stashes are preserved; no staging or commit was performed.


Broader host CI checkpoint: `./tools/local-ci.ps1 -NoFix -NoConfigurationCache` exited 0, completed in **68.75s**. Repository Ktlint, Detekt, release lint, all configured compilation selectors, generic unit tests and explicit KMP host selectors passed. The final explicit KMP host stage reused up-to-date test outputs from the affected run and other current module reports; it did not execute 627 FIDO2 cases a second time. Gradle reported configuration-cache reuse under the project defaults despite the script switch omitting its explicit enable flag; the earlier affected verification command explicitly disabled configuration caching. No coverage switch was supplied and no device target was required by this ordinary host CI path. The successful host CI does not close the remaining connected/coverage/review gate. Log remains at non-stageable `.git/testagent/hdk-remediation-20261016/local-ci-host-checkpoint.log`.


### T088 bounded request deadline verified checkpoint - 2026-10-16

**T088 is complete.** Current accounting is **89/97 complete, 6 active corrective tasks (T087/T089/T090/T092/T094/T095), and 2 user-deferred performance tasks (T084/T085)**. The user requested a pause immediately after completing T088; no next task was started. This closes the bounded deadline implementation/host regression task, not the remaining connected UI, coverage, ownership-classification or final assurance gates.

The HID transport captures a monotonic admission timestamp before queuing the request, subtracts elapsed queue time and applies the single remaining budget around wallet-owner bootstrap plus handler work. An exhausted budget is passed directly to withTimeout, rejecting it before owner acquisition; it is no longer extended by clamping to 1 ms. CTAP-over-MSG/APDU now enters the same independent CID operation path, preserving report reception of CANCEL while processing. Internal dispatcher/clock seams retain production Dispatchers.IO/System.nanoTime defaults and leave constructor DI unchanged. Sender, receiver, observer and keepalive work share the selected scheduling context in host tests. No latency target, pacing constant or performance instrumentation was optimized or profiled.

`BluetoothHidTransportLifecycleTest` now discovers **50 parameterized cases** in configured Android host tests, replacing the three prior real-thread timing-window checks:

- 42 actual GetAssertion handler/bus/presenter cases: seven stalled stages (wallet bootstrap, candidate lookup, initial verification availability, consent, confirmation availability, verification, processing), each terminated by deadline, CTAPHID_CANCEL or disconnect, each with both a tracking owner and the actual WalletMasterSeedProvider.
- Six actual MakeCredential handler/bus/registration-presenter cases: pending verification or processing, each terminated by deadline/CANCEL/disconnect under the actual wallet provider.
- Two expired-queue cases: direct CBOR and APDU routing, both rejected before wallet-owner acquisition or candidate lookup. Both failed with an observed owner count of 1 before removing the one-millisecond clamp; the corrected cases assert zero owners, zero candidate work and the exact timeout classification.

The real-provider fixture fakes only the encrypted recovery store, mnemonic decryption and seed generation; it executes the production owner machinery, HKDF/P-256 cache initialization and metadata-key provisioning. It is a synthetic lifecycle fixture, not BIP39 checksum, Android Keystore or RF interoperability evidence. Production-provider cases observe live root/parent buffers before termination where bootstrap completed, retain their actual references and assert all bytes zero afterward. They also require no remaining owners, null root/parent caches and released metadata-key references. Physical erasure of provider/JCE-managed key-object storage is not claimed. Bootstrap cancellation requires empty cache/owner state without claiming synchronous interruption of derivation.

Deadline cases assert work is still live just before the original budget expires, then the expected single 0x2F timeout response reaches the synthetic HID wrapper. CANCEL/disconnect generate no false timeout response. Tests require actual keepalive reports before termination, then no further keepalive reports after an additional full budget. Handler locks, bus requests, deferred responses, interaction signals and prompt-cancel effects are checked explicitly. Stalled lookup/availability/processing continuations terminate; old success/failure callbacks cannot add a use-case invocation after cleanup. Real cryptographic generation/signing/persistence are outside this suspended-use-case fixture and retain their separate suites; no unauthorized late use-case entry is observed.

Final successful command (Android Studio JBR as JAVA_HOME):

```powershell
./gradlew.bat :core:security:testAndroidHostTest :feature:fido2:testAndroidHostTest :core:security:detekt :feature:fido2:detekt :core:security:ktlintCheck :feature:fido2:ktlintCheck :feature:fido2:compileAndroidDeviceTest :app:compileDebugKotlin --continue --no-configuration-cache --console=plain
```

Exit 0, BUILD SUCCESSFUL in **19s**. Current XML contains **674 FIDO2 and 46 security tests, zero failures/errors/skips**; the deadline class contributes 50 passing cases. FIDO2 host tests executed with the full filter removed; unchanged security/static/compilation outputs were reused where Gradle marked them up-to-date. The preceding focused command independently passed all 50 cases plus FIDO2 Detekt/Ktlint (exit 0 in 17s). Test complexity findings were corrected by grouping stage setup, termination and keepalive assertions into named helpers; no rule suppression or threshold change was added. Formatting ran separately before validation.

Logs remain in non-stageable `.git/testagent/hdk-remediation-20261016`: `virtual-deadline-red.log`, `virtual-deadline-green.log`, `real-provider-deadline.log`, `both-handler-deadlines.log`, `t088-focused-verified.log`, `t088-final-gate.log`. XML/HTML remain under ignored module build directories. Static assertion-gap review links expired-owner admission, missing timeout, stale callbacks, absent deferred cleanup, nonzero retained buffers and continuing keepalive reports to explicit failing assertions; no semantic mutation run is claimed.

No connected device, coverage campaign, broader local-CI rerun, new formal review disposition, deployment or activation is claimed for this checkpoint. The previous local-CI result remains dated evidence. T095 retains final configured/device/coverage/review reconciliation; T087/T092 retain any remaining workflow acceptance work and are not automatically closed by T088. Performance deferrals remain unchanged. No staging or commit was performed by this implementation pass; optional commit hooks remain unrun.
## T089/T094 metadata ownership follow-up — 2026-10-16

The relying-party metadata path no longer builds JSON in an immutable String: `CredentialMetadataProtectionService.encryptRelyingPartyMetadata` accepts borrowed character sequences, serializes with the erasable character owner, encodes into an operation-owned byte array, and clears both character and byte buffers after encryption. The unused plaintext-returning `decryptRelyingPartyMetadata` API was removed. DAO and metadata backfill callers now pass fields directly to that operation; JSON escaping behavior is retained and strengthened for quotes and backslashes.

Focused verification: `:feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.service.CredentialMetadataProtectionServiceOwnershipTest :feature:fido2:detekt :feature:fido2:ktlintCheck` exited 0 (`BUILD SUCCESSFUL`); 11 ownership tests passed. The preceding run also compiled Android device tests successfully, and its sole new assertion failure was corrected by checking an encryption-time snapshot separately from the retained buffer after cleanup.

This is partial ownership progress only. Credential metadata parsing and mapper owners remain erasable, but RP/user/name values still enter preexisting immutable domain and SQLDelight projection fields. No exception to Constitution I.5 is claimed; T094 remains open pending model-boundary resolution and final assurance reconciliation. At the time of this follow-up, broader affected verification was still pending; the subsequent full FIDO2 host/static/device-compilation gate and T089 closure are recorded below. No device or coverage result is claimed.

## T087/T089/T090/T092 verified checkpoint — 2026-10-16

After the T089/T094 relying-party encoder change, the full configured FIDO2 host suite completed with **675 tests, zero failures/errors/skips**. FIDO2 Detekt and Ktlint passed; Android device-test compilation passed (UP-TO-DATE in this run, executed successfully in the preceding focused gate). The exact Gradle invocation was `:feature:fido2:testAndroidHostTest :feature:fido2:detekt :feature:fido2:ktlintCheck :feature:fido2:compileAndroidDeviceTest --no-configuration-cache --console=plain`, exit 0, BUILD SUCCESSFUL in 20s.

T087/T092 evidence includes request admission listeners, prompt IDs and cancellation effects; registration/authentication regressions cover replacement, stale success/failure and duplicate callbacks while use-case work is suspended. The 50-case T088 deterministic virtual-time transport matrix covers timeout, CTAPHID_CANCEL and disconnect across actual registration/assertion handlers and presenter stages, verifies post-termination stale callbacks are rejected, and checks deferred/prompt/keepalive cleanup and production provider owner/cache erasure. T089 now covers mutable credential and relying-party JSON serialization/decryption ownership and retained-buffer cleanup. T090 null-safe allow-list selection and typed mapper failure paths are covered in the configured full host suite. These four task markers are closed based on this evidence; T094 remains open because sensitive RP/user/name plaintext still resides in immutable database/domain model fields. T095 final review, traceability, local-CI, coverage and connected-device reconciliation have not been run as part of this checkpoint.

## T094 mutable domain model and CTAP-session checkpoint — 2026-10-16

`PasskeyCredential` now stores RP ID, user ID, username and display name in separate owned `SensitiveCredentialText` character buffers. The model supports explicit erasure, independent deep copies for counter-update paths, redacted diagnostics and value comparisons without String conversion. Credential-management RP/credential enumeration sessions retain mutable copies, erase consumed entries after CBOR encoding, clear replaced sessions, and erase source credential models after the operation. `CredentialManagementViewModel.onCleared` erases its distinct retained credential models. UI/protocol/database adapters explicitly convert to String only where the current consuming API requires it. Added ownership tests for erasure, borrowed subsequences, case-sensitive/case-insensitive comparison, deep-copy independence and redacted model diagnostics.

Verification command: `./gradlew :feature:fido2:testAndroidHostTest :feature:fido2:compileAndroidDeviceTest :app:compileDebugKotlin :feature:fido2:detekt :feature:fido2:ktlintCheck --no-configuration-cache --console=plain`. It exited 0 (`BUILD SUCCESSFUL`, 28 seconds). FIDO2 host XML totals: **679 tests, zero failures/errors/skips** across 99 reports. Android device-test compilation, app debug Kotlin compilation, Detekt and Ktlint passed. The test suite includes 679 tests; the exact additions in this checkpoint were covered by targeted ownership/model/CTAP tests before the full run.

This checkpoint does not close T094. The SQLDelight `Passkey_credential` generated projection still contains RP/user/name String fields, and the passkey event/command models also retain those values as Strings. Those projection and event ownership paths, their adaptation tests, and final security assurance reconciliation remain outstanding. No connected-device or coverage result is claimed here.

## T094 SQLDelight projection ownership checkpoint — 2026-10-16

The five credential projection columns (`rp_id`, `rp_name`, `user_id`, `user_name`, and `user_display_name`) are now declared as BLOBs in the clean-install schema and search queries cast display-text BLOBs to TEXT only for SQLite matching. The repository maps projection bytes into owned `SensitiveCredentialText` buffers and clears source row bytes on success, malformed rows, and cancellation. Repository filtering/statistics paths clear every row after use, including rows filtered out before domain reconstruction. DAO insert/update paths clear UTF-8 and lookup-token operation buffers after the synchronous SQLDelight call. Focused mapper tests retain every row buffer, verify zeroization, and confirm the returned domain owners preserve their values. DAO, migration and repository tests use per-call/per-row byte copies to represent the consume-and-erase ownership contract.

Verification: focused `PasskeyCredentialDaoTest`, `SearchableMetadataMigrationTest`, and `CredentialRepositorySearchableMetadataTest` passed; focused repository lifecycle and registration/authentication integration tests passed. The full FIDO2 host suite then passed **679 tests**, and the core security host suite passed **46 tests**, with zero failures/errors/skips. FIDO2 Detekt, Ktlint, Android device-test compilation and app debug Kotlin compilation passed in the follow-up static gate. The first combined invocation reached Detekt after both host suites passed but reported one helper-parameter naming violation; after correction, the static/device/app gate exited 0. At that checkpoint `PasskeyCommand.Register`, `PasskeyEvent.Registered`, and `PasskeyState` still retained RP/user/name values as immutable Strings. The T094 completion below supersedes this checkpoint for those fields. No local CI, coverage, connected-device or security-review closure is claimed here.

## T094 common passkey model ownership completion — 2026-10-16

At the time this T094 checkpoint was recorded, the passkey model and generic serializer changes were described as follows: `PasskeyCommand.Register`, `PasskeyEvent.Registered`, and `PasskeyState` retained RP ID, user ID, username and display name as mutable `CharArray` values; command/event owners exposed explicit erasure and redacted diagnostics; and the decider copied metadata across event/state transitions. Production event/snapshot repository serialization subsequently moved to `SensitiveJsonFields` in the dated T098 update below, which supersedes the serializer-boundary assumption for those two repository paths. The generic `SensitiveCharArraySerializer` still preserves JSON scalar-string fields for any other caller.

Classification: RP/account association and user ID/name/display-name are privacy-sensitive metadata; they are not key material, and no application-created registered model retains them as a `String`. Public key and AAGUID remain public credential/derivation metadata. For generic serialization callers, `Encoder.encodeString` still requires a temporary immutable string; production passkey event and snapshot repositories now bypass this boundary, while Kotlin serialization/JSON internals may still create runtime copies for other paths. Existing protocol/UI consumers also require String representations at their adapter boundary. Those copies and ICU/JVM/provider internals are not erasable or observable by this code; no physical-erasure claim is made for them. Application-owned arrays, projection row bytes and serializer/event plaintext buffers are explicitly cleared at their consuming lifecycle boundaries. This is a source ownership boundary statement, not an exception to Principle I.5 or an independent security-review disposition.

Verification: `:core:domain:testAndroidHostTest --tests '*PasskeyDeciderTest' --rerun-tasks --no-configuration-cache --console=plain` passed: 10 tests, zero failures/errors/skips. `:feature:fido2:testAndroidHostTest --tests '*PasskeyAggregateServiceImplTest' --tests '*PasskeyEventStorageOwnershipTest' --no-configuration-cache --console=plain` passed: 14 selected tests, zero failures/errors/skips, covering preserved string wire fields, event replay erasure, registration command/event erasure on success and cancellation, returned-state independence, snapshots, retries and snapshot cadence. The host task compiled all FIDO2 host test sources. Core domain Detekt/Ktlint and FIDO2 Detekt/Ktlint passed; the final FIDO2 checks were `:feature:fido2:ktlintAndroidHostTestSourceSetCheck :feature:fido2:detekt` and `:feature:fido2:ktlintCommonMainSourceSetCheck :feature:fido2:detekt`, both successful. A test fixture's mismatched `cred_protect_policy` named argument was corrected to its existing `credProtectPolicy` parameter to restore host-test compilation. No full feature host rerun, device execution, coverage gate, local CI or T095 assurance reconciliation is claimed. T084/T085 remain deferred.

### T095 final verification and traceability checkpoint — 2026-10-16

The T095 source audit confirmed the `io.mockk.coVerify` import in `RegistrationOwnerIntegrationTest`; the task's former line reference was stale. `CredentialMetadataProtectionServiceOwnershipTest` now distinguishes malformed UTF-8 (`[0xC3, 0x28]`, `CharacterCodingException`) from five malformed JSON partitions (typed parse rejection), and verifies decrypted-buffer erasure. The configured host suite contains the T086 handler-to-policy unavailable/failed/cancelled/success cases, T087 stale timeout/failure/duplicate callbacks during suspended use-case work, and the T088 deterministic real-handler stalled-lookup/processing-expiry cases with keepalive shutdown, deferred/prompt cleanup and production-provider cache erasure. These use virtual-time scheduling and observable completion rather than a 50-ms real-thread polling window. `encryptCredentialMetadata` and `decryptCredentialMetadata` carry direct `@Req("NFR-SEC-041", "TR-AUTH-031")` annotations; affected tests have requirement KDoc/references, descriptive backtick names, and passed repository Ktlint/Detekt.

Final repository CI command: `.\tools\local-ci.ps1 -NoFix -NoConfigurationCache`. Exit 0; all repository Ktlint, Detekt, Android Release Lint, Compile All, Unit Tests and configured KMP Android Host Tests passed. The pipeline completed in 135.25 seconds, with the KMP host stage passing in 90.3 seconds. Gradle reported configuration-cache reuse despite the script option; the final output is recorded as a successful configured pipeline run, without claiming all underlying tasks were forcibly rerun.

The ASUS Zenfone 10 (serial `R7AIB7002901AZ3`, Android 15) was connected. `:feature:fido2:connectedAndroidDeviceTest` passed 25 tests with zero failures/errors/skips; XML is `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302 - 15.xml`. It includes 2 credential persistence ceremony, 3 process-restart, 7 lifecycle, 1 HID-disconnect, 6 authenticator dashboard, and 6 development-tools UI cases. `:app:connectedAndroidTest --console=plain` passed 11 tests with zero failures/errors/skips; XML is `app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-ASUS_AI2302 - 15.xml`, including the production memory-pressure callback coverage. Both process restart runners also passed on that device using synthetic P-256 data: `tools/run-hdk-process-restart-test.ps1 -Serial R7AIB7002901AZ3` (prepare and recovery each `OK (1 test)`) and `tools/run-hdk-app-process-restart-test.ps1 -Serial R7AIB7002901AZ3` (production app prepare and recovery each `OK (1 test)`). Both verified recovery after a force-stop in a distinct process and cleaned their disposable fixtures/packages.

The production-app restart runner first exposed a compile mismatch in its recovery-row predicate (`SensitiveCredentialText == RpId`). The test now compares through `SensitiveCredentialText.contentEquals(rpId.value)`, avoiding conversion of the sensitive field to an immutable string. The runner's subsequent complete build/install/prepare/recovery pass succeeded. This correction is test-only and does not change production behavior.

Coverage command: `./gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkHostCoverage hdkCoverageVerify --console=plain`. Exit 0 (`BUILD SUCCESSFUL` in 5m30s); connected FIDO2 and app instrumentation plus configured host/unit suites were discovered and included. No exclusions or threshold changes were used. The module reports are at `<module>/build/reports/hdk-coverage/hdkModuleCoverage.xml` (the scoped FIDO2 report is in `<module>/build/reports/hdk-coverage/hdkScopedCoverage.xml`); repository results are in `build/reports/hdk-coverage/repository.xml` and `repository.csv`.

| Scope | Statements | Branches | Tests | Gate |
| --- | ---: | ---: | ---: | --- |
| `core:security` Tier 1 | 82.19% | 62.70% | 46 | ≥40% statement and branch: pass |
| `core:domain` Tier 1 | 60.15% | 56.10% | 47 | ≥40% statement and branch: pass |
| `feature:fido2` Tier 1 | 48.29% | 47.52% | 682 | ≥40% statement and branch: pass |
| `core:database` Tier 2 | 22.80% | 51.56% | 10 | ≥20% statement: pass |
| `core:common` Tier 2 | 34.42% | 32.76% | 10 | ≥20% statement: pass |
| `app` Tier 2 | 36.97% | 29.47% | 5 | ≥20% statement: pass |
| Repository aggregate | 44.81% | 46.94% | — | ≥20% statement: pass |

Security scope was reconciled without expanding reviewer claims. The formal T005 disposition accepts the named local derivation profiles for its recorded reviewed artifact; the T072 supplemental disposition accepted the assertion UV correction after AF-UV-01/02 were closed by the recorded tests and local-CI coverage. Neither is a separate independent disposition of the later T094 metadata ownership/source changes. T094 field classification and erasure evidence remain engineering source-boundary evidence, not formal review sign-off. No production activation or deployment is claimed. T084 and T085 remain user-deferred, SC-007 remains partial, and no performance-compliance result is claimed.

T095 is complete for its implementation and verification scope. The feature status now records **95/97 complete**, with T084/T085 as the two user-deferred tasks and no active corrective task. Existing staged files, uncommitted work and stashes were preserved; no staging, commit or optional commit hook was performed.

## T098-T103 partial implementation checkpoint - 2026-10-17

This checkpoint records work started after Phase 13 convergence; it does not close any T098-T103 marker. The earlier T094 serializer-boundary text is historical context only and is not accepted as meeting Constitution I.5 for production event/snapshot serialization.

Implemented slices:
- Registration repository builds `PasskeyCommand.Register` from independently owned mutable character copies and erases those copies in `finally`; a retained-reference test verifies the caller's credential remains intact.
- Credential-management CBOR response maps pass `SensitiveCredentialText` directly to the encoder. `CborCodec` emits the same CBOR text-string bytes via a temporary UTF-8 owner and wipes that temporary plus its intermediate output buffer after copying the response.
- Credential repository disposes successful aggregate states it does not transfer. DAO replacement erases selected row buffers in `finally`. Event replay now erases decoded registered events accumulated before later failure/cancellation; snapshot load erases decoded state when its post-decode cancellation check fails.
- Credential mapper begins cleanup ownership before the first UTF-8 decode and maps `CharacterCodingException` to the existing fixed malformed-record outcome. Added malformed UTF-8 coverage for each of the four metadata fields and retained row-byte erasure assertions.
- Registration use case erases its newly created credential on any failure/cancellation before transfer. Management search diagnostics and UI errors now use fixed messages without query/exception text.
- Direct `@Req` and KDoc were added to aggregate getState/execute and sensitive serializer / text-owner declarations touched in this checkpoint.

Selected FIDO2 host tests passed for CborCodecTest (7), EntityMappersTest (22), CredentialRepositoryImplTest (28), RegisterCredentialUseCaseTest (32) and CredentialManagementViewModelTest (10), with zero failures/errors/skips in their XML reports. These tests do not cover the outstanding retained-reference requirements in the corrective tasks. Detekt passed. The Android host test source-set formatter was applied, then `:feature:fido2:ktlintAndroidHostTestSourceSetCheck` passed.

At the time of this checkpoint, the listed gaps remained open. Later dated updates below supersede its event/snapshot serializer status. The broader CBOR transport response owner and every direct handler conversion still need retained-buffer review; persistence decode-failure/cancellation retained-reference coverage remains incomplete; registration/UI/transport model sharing, replacement, and abandoned enumeration terminal cleanup remain incomplete; and full T098-T103 traceability/final gates remain open. T102 is closed by the scoped follow-up below; T098, T099, T100, T101 and T103 remain active. No full module/repository coverage, connected-device test, local-CI run, independent-review extension, deployment, activation or performance-compliance claim is made. T084/T085 remain deferred.

### T102 management-search diagnostic closure — 2026-10-17

`CredentialManagementViewModel` now has synthetic-marker regressions through its actual search boundary for successful results, I/O failure, and cancellation. The tests capture the configured Kermit diagnostic writer, check that markers do not reach diagnostics, and assert the expected error or cancellation UI state. Search failures expose only the fixed `Credential search failed` message; cancellation is not converted to an error. The view model logs fixed operation messages and neither query contents nor exception messages/throwables.

Verification: `:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.presentation.management.CredentialManagementViewModelTest' :feature:fido2:ktlintAndroidHostTestSourceSetCheck --no-configuration-cache --console=plain` passed, with 12 tests and zero failures/errors/skips. The initial combined run found only import ordering; the formatter corrected it and the rerun passed. This closes the scoped T102 management-search diagnostic task. It does not enlarge T072/T096 review dispositions. The remaining T098, T099, T100, T101 and T103 tasks remain open; no full suite, connected test, local-CI or coverage gate is claimed.

### T098 credential-management RP hash lookup slice — 2026-10-17

The credential-management handler's full-credential RP hash fallback no longer converts every RP ID to an immutable `String` for distinct/filter/hash operations. It compares mutable owners, encodes each unique RP ID into a short-lived UTF-8 byte array, hashes it, and clears both temporary byte and digest arrays in `finally`. Only a matching value is converted to the existing `RpId` API boundary. The focused `Ctap2CredentialManagementHandlerTest` suite passed 11 tests; FIDO2 Detekt and Android main/host-test Ktlint checks passed. This slice reduces one application-created search copy but does not by itself close T098.


### T098 mutable event/snapshot JSON slice — 2026-10-17

`SensitiveJsonFields` now serializes the sanitized event or snapshot through kotlinx serialization, splices the four sensitive string values from their mutable `CharArray` owners into an erasable UTF-8 JSON buffer, and decodes by extracting those values into mutable owners before deserializing a redacted document. `PasskeyEventStoreRepositoryImpl` uses this path for registered events and `PasskeySnapshotRepositoryImpl` uses it for passkey snapshots. Both retain the existing JSON property names, string representation, polymorphic event discriminator, snapshot shape, encrypted payload and key identity. The selected ownership suite passed 11 tests, including escaped/Unicode/long-value wire-equivalence, reconstruction failure/cancellation cleanup, snapshot round-trip and first decoded event cleanup when a later row is malformed. Previously recorded T098 hash-lookup tests (11) and feature Detekt/Ktlint checks also passed.

This supersedes the checkpoint's statement that production event/snapshot JSON still used the String serializer contract. It does not close T098: retained-buffer checks for every transfer/partial-decode boundary, the full CBOR response/handler boundary audit, and direct caller-path tests remain open. T099, T100, T101 and T103 also remain open. The full affected module/repository gates and any connected-device refresh have not been run for this corrective chain; this is engineering evidence only and does not extend an independent review disposition or authorize activation. T084/T085 remain deferred and SC-007 remains partial.

### Corrective ownership follow-up — 2026-10-17

T100's mapper now catches strict UTF-8 `CharacterCodingException` at the typed row boundary, starts cleanup before allocating the first sensitive owner, erases partially allocated owners on failure, and always clears row-owned metadata bytes. `EntityMappersTest` exercises malformed UTF-8 independently in `rp_id`, `user_id`, `user_name` and `user_display_name`, checks the fixed redacted error and confirms the row buffers are zeroed; the healthy neighboring-row cases remain covered. These tests do not retain references to each partially decoded owner, so the stronger retained-owner assertion in T100 remains open.

The T099 repository batch paths now erase partial credential lists on failure/cancellation, clear models that are filtered out before transfer, and erase models consumed by repository-owned validation/deletion/cleanup flows. A focused cancellation regression confirms cancellation propagates and source row buffers are cleared, but it does not retain the already-mapped model owner, so the stronger assertion remains open. DAO projection replacement still has explicit `finally` cleanup for selected old-row buffers; retained-row rollback/cancellation verification remains open.

The T101 registration path now creates a separate independently erasable credential-text owner for the success UI state and erases late or rejected transport results. `Ctap2MakeCredentialHandler` erases its result credential after response encoding/handoff, including encoding failure. Credential-management ViewModel list, selection and dialog replacement erase models no longer referenced by a current ViewModel-owned state slot, while preserving models that remain in current state; `onCleared` erases unique reachable owners. Abandoned enumeration owners now expire after 30 seconds of inactivity, and the foreground authenticator stop path calls `clearSessions()` to erase them immediately. Added tests check independent UI/transport owner lifetimes, CTAP response terminal cleanup, ViewModel list/selection/dialog replacement, ViewModel clear, and explicit enumeration-session clear. The focused ownership suite passed 116 selected host tests total across six suites, zero failures/errors/skips. The first attempt exposed three test fixtures that mocked `MakeCredentialResult` without its credential; those fixtures now use synthetic real result credentials and assert that the handler erases them.

Production and device-test source compilation passed with `:feature:fido2:compileAndroidMain :feature:fido2:compileAndroidDeviceTest`. `:feature:fido2:detekt`, Android main Ktlint and Android host-test Ktlint also passed after formatting. This does not close T098-T101: event/snapshot cleanup does not yet retain decoded sensitive event/state owners across partial-list failure and post-decode cancellation; DAO replacement rollback/cancellation row-owner retention remains untested; registration ViewModel cancellation after a completed deferred and UI effect terminal ownership lack complete retained-owner verification; pagination/search replacement remains incompletely tested. The session TTL and stop path have focused unit evidence but no connected transport lifecycle rerun. T103 still requires a full direct traceability and final-gate audit. No full host suite, connected-device execution, local-CI or coverage gate, reviewer extension, deployment or activation is claimed. T084/T085 remain deferred; SC-007 remains partial.

### Corrective pass verification refresh — 2026-10-17

After the repository batch-cleanup helper was refactored to satisfy Detekt's nesting and exception-handling rules, the focused ownership suites were rerun: 128 tests across seven selected suites, with zero failures, errors or skips. The selection included `Ctap2CredentialManagementHandlerTest` (12), `Ctap2MakeCredentialHandlerValidationTest` (15), `PasskeyEventStorageOwnershipTest` (11), `EntityMappersTest` (22), `CredentialRepositoryImplTest` (29), `PasskeyCredentialTest` (24 across five XML suites), and `CredentialManagementViewModelTest` (15). Production Android main and device-test sources compiled; Detekt and Android main/host-test Ktlint checks passed.

The first static-check attempt surfaced two Detekt issues (excessive nesting and a generic catch) and a host-test line-length issue. These were corrected by extracting the RP filter helper, restoring finally-based ownership cleanup, and wrapping the test line; the subsequent complete scoped static-check command passed. This refresh does not close T098-T101 or T103 and does not claim full host/CI/coverage/device gates. Existing staged snapshot and all stashes remain preserved; no staging or commit was performed.

### T101 registration persistence-failure retained-owner regression — 2026-10-17

`RegisterCredentialUseCaseTest` now captures the exact `PasskeyCredential` passed into the persistence boundary when storage returns an error, then reads each of its four mutable text owners and confirms they contain only zeroed characters after the failed registration returns. This directly verifies the use case's `finally` cleanup for the persistence-failure terminal path without relying on a test-only copy or a success-state inference.

Verification: all 32 `RegisterCredentialUseCaseTest` cases passed with zero failures/errors/skips; Detekt and Android host-test Ktlint passed. T101 remains open because attestation failure/cancellation retained-owner checks, late/competing ViewModel results, complete UI effect lifetime coverage, while the connected Bluetooth HID disconnect test now passes on ASUS; a manual browser-to-authenticator ceremony was not rerun in this corrective pass.

The connected lifecycle/UI refresh also passed on the user-waived ASUS reference device: `:feature:fido2:connectedAndroidDeviceTest` ran 25 tests (2 credential persistence ceremony, 3 process restart, 7 local profile lifecycle, 1 Bluetooth HID disconnect, 6 authenticator dashboard, and 6 development-tools UI) with zero failures/errors/skips. The generated device report is at `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302 - 15.xml`. This refresh provides current connected lifecycle/UI evidence for the touched wallet and transport areas; it does not exercise the manual RP-to-browser ceremony or establish SC-007 latency.

### Corrective broad verification gates — 2026-10-17

The configured repository no-fix local CI pipeline passed: whole-project Ktlint, Detekt, Release lint, compilation targets, ordinary unit tests and explicit Android host suites (`core:common`, `core:security`, `core:domain`, `feature:fido2`). The coverage command `.\gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkHostCoverage hdkCoverageVerify --console=plain` also completed successfully and reran connected ASUS Android device suites. The connected feature suite reported 25 tests with zero failures/errors/skips; the app connected Android test task also completed successfully.

Coverage gates passed with actual execution data: `core:security` 82.19% statements / 62.70% branches; `core:domain` 60.15% / 56.10%; `feature:fido2` 49.75% / 48.55%; Tier 2 statement floors passed for `core:database` 22.80%, `core:common` 36.81%, and `app` 36.97%; repository aggregate was 45.87% statements / 47.67% branches. Tier 2 branch figures remain report-only. Detailed reports are under `build/reports/hdk-coverage/` and each module's `build/reports/hdk-coverage/`.

These gates satisfy the current configured broad host/device/coverage/local-CI pass for this worktree, but do not close T098-T101's explicit retained-owner and terminal-lifetime gaps, nor T103's full traceability/reconciliation audit. No performance pass, independent-review extension, production activation or deployment is claimed.

### T100 partial-decoding retained-owner regression — 2026-10-17

The mapper now routes production hydration through its existing strict UTF-8 decoder with a small internal decoder boundary used to retain the exact successfully allocated owners in tests. The malformed UTF-8 parameterized case now retains and checks all preceding `SensitiveCredentialText` owners for each corrupt BLOB column (`rp_id`, `user_id`, `user_name`, `user_display_name`), and confirms both those owners and every row byte buffer are zeroed. It also asserts the fixed typed malformed-record result has no exception cause. The production entry point still supplies `SensitiveCredentialText.fromUtf8Bytes` directly; the internal path does not change decoder behavior.

Verification: `EntityMappersTest` passed all 22 cases with zero failures/errors/skips; Android main compilation, Detekt and Android main/host-test Ktlint checks passed in the scoped run. The updated repository regression supplies the healthy neighboring list and search record proof; T100 is now closed by the complete malformed-field, retained-owner, row-erasure and valid-counterpart evidence.

### T100 completion — 2026-10-17

T100 is complete. Malformed UTF-8 in each sensitive projection column now has a typed-boundary regression retaining all preceding decoded owners and every row byte buffer. The repository mixed-record regression now uses an actually malformed UTF-8 `user_name` row alongside a healthy record and confirms list, text-search, RP, user, and paged results keep the healthy credential while every DAO row buffer is erased. Existing valid-row tests verify current identity/profile/key/counter fidelity. Malformed bytes remain tested separately from metadata JSON errors; cancellation propagation and fixed no-cause errors are preserved.

Verification: 51 focused host tests passed across `EntityMappersTest` (22) and `CredentialRepositoryImplTest` (29), zero failures/errors/skips; Android main compilation, Detekt and main/host Ktlint passed. The repository CI and coverage gates passed earlier in this corrective cycle. T098, T099, T101 and T103 remain active; T084/T085 remain deferred.

### T099 completion — 2026-10-17

T099 is complete. Credential persistence operations erase every successful `PasskeyState` they consume in `finally`; they do not mutate the caller's input credential. Replacement deletion routes fetched SQLDelight rows through a single cleanup boundary which clears RP ID, RP name, user ID, username and display-name byte arrays whether deletion succeeds, throws, or observes cancellation. Existing transaction tests continue to verify failed replacement rolls back the prior projection. Event replay erases retained Registered-event owners when a later row fails or cancellation occurs; snapshot retrieval erases a decoded state's owners when the final cancellation check prevents transfer. Successfully returned event/state owners remain caller-owned. Valid two-event replay and snapshot reconstruction remain covered.

Verification: focused host results passed with zero failures/errors/skips: `PasskeyCredentialDaoTest` (5 cases across the outer class and nested suites), `PasskeyEventStorageOwnershipTest` (10), and `PasskeyEventReplayOwnershipTest` (4). The DAO suite retains each of the five text-column row buffers through replacement cleanup and checks them after success, injected deletion failure, and cancellation. Replay tests retain all four Registered metadata arrays across later-row failure and cancellation; snapshot testing retains all four state arrays across post-decode cancellation. FIDO2 Android main compilation, Detekt, and Android main/host-test Ktlint checks passed. An initial run exposed two test-fixture assumptions (query sequence start and single-payload assertion); both were corrected before the passing run. Repository-wide CI/coverage and connected device checks have not been rerun after this T099/T100 corrective source update. T098, T101 and T103 remain active; T084/T085 remain deferred, SC-007 remains partial, and this engineering evidence does not extend any formal security disposition or claim deployment/activation.

### T098 completion — 2026-10-17

T098 is complete for the assessed FIDO2 paths. Registration constructs mutable `PasskeyCommand.Register` fields from independent `CharArray` copies. Passkey event and snapshot repositories serialize sensitive fields through `SensitiveJsonFields`, preserving the established JSON scalar strings, escaped/Unicode values, event discriminator, snapshot properties, encryption behavior and key identity without materializing those field values as application `String` intermediates. Credential-management response maps carry `SensitiveCredentialText` owners; `CborCodec` emits those as CBOR text strings through temporary erasable UTF-8 buffers. Hash-based credential enumeration now compares/hash-checks mutable RP owners and returns matching owned credentials directly, without reconstructing an RP-ID `String`; unmatched and consumed credential owners are erased.

`SensitiveCharArraySerializer` remains as a compatibility serializer for the generic serialization API. Its KDoc now states explicitly that `Encoder.encodeString` / `Decoder.decodeString` materialize immutable text and that the serializer is not an erasure boundary. Production passkey event/snapshot repositories do not pass real sensitive values through that generic path: serialization sanitizes those fields before framework invocation and attaches them from mutable owners. This records a generic API/runtime-copy limitation, not a governance exception or physical-erasure claim. Existing separate vault uses of the generic serializer are outside the FIDO2 task scope and should not be read as covered by this T098 claim.

Verification: 34 focused FIDO2 host tests passed with zero failures/errors/skips across `PasskeyEventStorageOwnershipTest` (10), `PasskeyEventReplayOwnershipTest` (4), `Ctap2CredentialManagementHandlerTest` (13), and `CborCodecTest` (7). These include mutable JSON wire equivalence for long escaped Unicode fields, decode failure/cancellation owner erasure, event/snapshot replay cleanup, owner-backed CBOR text-string byte parity, retained credential-management response owners, and hash-based lookup cleanup. FIDO2 Android main compilation, Detekt, and Android main/host Ktlint passed. The separate core-domain serializer change is documentation-only; broad local CI/coverage and connected-device gates have not been rerun after this corrective chain. T101 and T103 remain active; T084/T085 remain deferred, SC-007 remains partial, and this engineering evidence does not extend a formal security disposition or claim deployment/activation.

### T101 completion and final corrective gate refresh — 2026-10-17

T101's remaining management replacement paths now retain actual credential owners across an invoked refresh and a successful search result. Both regressions confirm that the previous credential's RP ID, user ID, username and display-name arrays are zeroed when no current state slot retains it, while the new result remains in state. `SensitiveCredentialTextTest` also gained explicit per-test requirement references for its three owner/view behaviors.

The focused T101 command passed 81 tests with zero failures/errors/skips: `RegisterCredentialUseCaseTest` (33), `RegistrationOwnerIntegrationTest` (18), `Ctap2CredentialManagementHandlerTest` (13), and `CredentialManagementViewModelTest` (17). FIDO2 Android main and device-test sources compiled; Detekt and FIDO2 Android main/host Ktlint passed. The connected ASUS results were refreshed by the configured coverage task: FIDO2 lifecycle/UI/restart 25/25 passed (`feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302 - 15.xml`); app instrumentation 11/11 passed (`app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-ASUS_AI2302 - 15.xml`). These are lifecycle/ownership checks, not a new manual RP ceremony or a performance measurement.

The full no-fix local CI command `.\tools\local-ci.ps1 -NoFix -NoConfigurationCache` passed whole-project Ktlint, Detekt, Release lint, compilation targets, ordinary unit tests and configured Android host suites. The coverage command `.\gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkHostCoverage hdkCoverageVerify --console=plain` passed with actual execution data. Tier 1 results: `core:security` 82.19% statements/62.70% branches (46 tests); `core:domain` 60.15%/56.10% (47); `feature:fido2` 50.13%/48.89% (711). Tier 2 statement floors passed: `core:database` 22.80%, `core:common` 36.81%, `app` 36.97%; their branch figures remain report-only. Repository aggregate: 46.12% statements/47.90% branches. Reports are under `build/reports/hdk-coverage/` and each affected module's `build/reports/hdk-coverage/`.

T103's engineering traceability audit found direct `@Req` annotations and function KDoc on the assessed aggregate `getState`/`execute` methods, `SensitiveCharArraySerializer.serialize`/`deserialize`, and the `SensitiveCredentialText` public owner operations/factories and overrides. Aggregate/serializer regression methods already carry per-test requirement references; the three direct sensitive-text tests now do as well. The T098-T102 tests use descriptive names and per-test references for their added ownership assertions. This audit does not treat lint as traceability evidence.

At the time of this evidence entry, the T103 source-boundary review request and exact SHA-256 fingerprints were prepared in `security-review-packet.md`, but the independent disposition had not yet been recorded. The later 2026-10-17 disposition and its scope are documented in the T103 independent source-boundary disposition section below. Existing T005/T072 reviewer fingerprints remain unchanged. T084/T085 remain deferred; SC-007 remains partial, including the prior above-target internal samples and the absence of a numeric end-to-end pass.

### T103 independent source-boundary disposition — 2026-10-17

The supplemental independent disposition is recorded in `security-review-packet.md`. Reviewer identity is recorded there as an independent AI Security Reviewer (Gemini 3.8 Flash), Security Architecture & Cryptographic Audit; date 2026-10-17; reviewed revision `daf1fbe60984b8a0f87f43f730c331bc2b1f2944` plus the fingerprinted working tree. The reviewer reported independently verifying all 19 production-source and 11 regression-test SHA-256 fingerprints. I rechecked the packet against the current files: all 30 fingerprints match.

The reviewer assessed mutable-owner transfer and disposal, dependency-internal copies, partial decode/write and rollback cleanup, abandoned CTAP enumeration/session cleanup, JSON/CBOR compatibility, diagnostic disclosure, and Tier 1 traceability. The reviewer marked T094 and T098-T103 findings closed, recorded **ACCEPT**, and authorized activation only for Chimali's named local derivation profiles and clean-install scope. T084/T085 remain deferred, SC-007 remains partial, no performance-compliance claim is made, and no activation or deployment event is claimed here.

The reviewer-reported independent gate run passed local CI, configured host/coverage gates, and ASUS connected checks (app 11/11, FIDO2 lifecycle/UI/restart 25/25). Reported coverage was FIDO2 50.11% statements / 48.89% branches and repository aggregate 46.11% / 47.90%; earlier engineering output recorded 50.13% / 48.89% and 46.12% / 47.90%. These small report differences do not change the reported threshold result; both sets meet the configured gates. This closes the T103 review gate without changing the prior review fingerprints or claiming that activation/deployment occurred.

### T104 superseded CBOR buffer erasure — 2026-10-17

`CborCodec.encodeToFido2Format` now uses an audited erasable stream that reserves capacity before each write. When growth is needed, it copies into the replacement, zeroes the previous backing array immediately, and only then continues. Its `finally` path zeroes the final intermediate buffer after `toByteArray()` has produced the independently owned return value; the caller's borrowed `SensitiveCredentialText` is not erased by the codec. The default production codec has no observer and retains no buffer references. A package-internal test observer receives only already-zeroed arrays.

Regression evidence: `CborCodecTest` now includes `CBOR encoding erases every replaced plaintext buffer and preserves the returned response` and `CBOR encoding failure erases grown plaintext buffers before propagating`. The success case forces repeated growth after ASCII, escaped and Unicode text, checks every superseded/final intermediate array is zero, decodes the returned response and confirms its wire values remain intact. The failure case writes a large sensitive value, forces a later list-item exception, and confirms the exception propagates after all intermediate arrays are zeroed. Targeted command `./gradlew.bat :feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.crypto.CborCodecTest --no-configuration-cache` passed: 9 tests, 0 failures, 0 errors, 0 skipped. This host test verifies JVM array cleanup; it does not assert physical erasure of copies made by the runtime or callers after receiving the response.
### T105 management result ownership and request fencing — 2026-10-17

Credential search now collects into an explicitly owned staging list. Successful completion transfers the staged credential owners to the ViewModel; cancellation, an `IOException`, a stale generation, or ViewModel teardown erases staged owners that are not retained by another authorized state slot. Refresh cancels an active search. Page loads capture the request generation and starting offset; obsolete page results are erased instead of appended, and an older request cannot clear a newer request's in-flight marker or alter the visible state.

Regression evidence: `CredentialManagementViewModelTest` covers `search failure after an emitted credential erases the untransferred owner`, `query replacement cancels search and erases its staged credentials`, `refresh cancels active search and erases its staged credentials`, `page completing after search is erased and cannot replace search results`, `older page completing after refresh cannot replace the current page`, and `view model teardown cancels search and erases staged credentials`. Each retains the emitted model reference and checks all sensitive text owners are zero after disposal; stale page tests also confirm the newer results remain visible. Targeted command `./gradlew.bat :feature:fido2:testAndroidHostTest --tests com.chimali.fido2.presentation.management.CredentialManagementViewModelTest --no-configuration-cache` passed: 23 tests, 0 failures, 0 errors, 0 skipped. FIDO2 Android main and host-test Ktlint checks passed. No device test is needed for this ViewModel coroutine ownership change; the full local CI and aggregate coverage gates remain for T113.
### T106 dormant CTAP credential-management capability — 2026-10-17

The production reachability audit found no CTAPHID call to `Ctap2CredentialManagementHandler.handle`. `Fido2TransportService` injected the handler only to call `clearSessions` on explicit stop; the live transport dispatcher accepts MakeCredential, GetAssertion and GetInfo. The handler was therefore an unexposed implementation with direct-only tests, not a supported credential-management session. I removed the handler, its service injection/cleanup call, and its unreachable host tests rather than adding new command routing or claiming terminal cleanup for a session production could never create. The historical March changelog now explicitly states this runtime scope and that its earlier source-completion note did not prove relying-party availability.

Verification: searches show no remaining production or test references to `Ctap2CredentialManagementHandler`, `authenticatorCredentialManagement`, or its session-clearing API. `./gradlew.bat :feature:fido2:compileAndroidMain :feature:fido2:compileAndroidHostTest :feature:fido2:compileAndroidDeviceTest :feature:fido2:ktlintAndroidMainSourceSetCheck :feature:fido2:ktlintAndroidHostTestSourceSetCheck --no-configuration-cache` passed. This removes a dormant, unadvertised command implementation; it does not add or claim CTAP credential-management support. There are no retained management sessions requiring lifecycle cleanup in the current production transport.
### T107 erasable management search and count-only statistics — 2026-10-17

`CredentialManagementViewModel` now stores its query as a `SensitiveCredentialText` owner in the StateFlow, erases replaced/current owners, and passes an operation-owned copy through `SearchCredentialsUseCase` and `CredentialRepository.searchCredentials`. The operation copy is erased after flow completion, cancellation, refresh, or failure; the repository borrows it and performs case-insensitive matching without creating a query `String`. The incoming Compose text-field value remains a short-lived String at the UI callback boundary; it is copied immediately and is not retained in the ViewModel flow or search API.

The only production consumer of `CredentialStatistics` uses `totalCredentials` for the global credential limit. I removed the unused `credentialsByRp` field and its grouping/String/RpId reconstruction; statistics now contain counts and average age only, so there is no RP text owner in the returned result that needs disposal.

Retained-reference evidence: `CredentialManagementViewModelTest` covers the operation query owner after completion/replacement, the flow-retained owner after replacement and teardown, cancellation of the staged query and credentials, and stale requests. `CredentialRepositoryImplTest` verifies the repository borrows rather than erases the query owner. Targeted host results: ViewModel 25/25 and repository 29/29 passed, with 0 failures/errors/skips. `:feature:fido2:compileAndroidMain`, `compileAndroidHostTest`, Android-main Ktlint and Android-host-test Ktlint passed. A repository search finds no `credentialsByRp` field or FIDO2 String-query search signature. No numeric performance work was run.

### T108 bounded schema-aware JSON/CBOR adapters — 2026-10-17

`SensitiveJsonFields.decode` now selects fields only at its configured object path: registered events use the root object, while snapshots use the exact `state` object. Selected duplicate, non-string, or absent required values fail before ownership transfer; nested extension fields with matching names remain unknown and cannot replace state metadata. JSON input and nesting are bounded, and parser string accumulation erases superseded character buffers. `StorageResult` converts expected UTF-8 coding failures to its typed `Result` boundary while still rethrowing cancellation.

`CborCodec` now bounds input, nesting, strings, and container counts before narrowing or allocation; rejects truncated, unsupported, invalid UTF-8, duplicate-key, and trailing-data encodings; and clears decoded byte strings if malformed input aborts parsing. Its decoder catches only the supported malformed-CBOR exception. Encoding sorts map keys by deterministic CBOR encoded-key order, emits signed integer keys with the correct major type, rejects colliding encoded keys and unsupported values, and clears temporary UTF-8 arrays and the erasable output buffer.

Regression evidence: `PasskeyEventStorageOwnershipTest` verifies real snapshot reconstruction with nested `rpId` lookalikes before and after legitimate state fields, missing/duplicate/wrong-type fields, malformed UTF-8, excessive nesting, document-size rejection, storage-boundary cancellation behavior, and existing ownership cleanup. `CborCodecTest` verifies unchanged valid fixtures, trailing/truncated/oversized/deep/malformed CBOR rejection, negative-integer key encoding, insertion-order-independent canonical maps, duplicate encoded-key rejection, and zeroization of temporary text and partially decoded byte-string buffers. `./gradlew.bat :feature:fido2:testAndroidHostTest --tests '*PasskeyEventStorageOwnershipTest' --tests '*CborCodecTest' --no-configuration-cache` passed: 28 tests, 0 failures, 0 errors, 0 skipped. Android main and host-test Ktlint checks passed. The host test task compiled the changed Android main and host-test sources. This establishes bounded adapter behavior for the specified JSON/CBOR types; it does not claim complete RFC 8949 support for indefinite items or floating-point/simple values.

### T109 validated passkey aggregate reconstruction — 2026-10-17

`PasskeyAggregateServiceImpl.getState` now validates snapshot aggregate/sequence consistency and current credential identity before accepting a snapshot. Invalid snapshots are erased and treated as corrupt, then reconstruction falls back to the complete authoritative event stream. Replayed states must maintain aggregate ID, sequence, a valid full credential ID, a supported algorithm/profile descriptor and a nonblank stored public key. Invalid Registered-event evolution returns a fixed typed `Result` failure; cancellation propagates. Snapshot, intermediate state and Registered-event text owners are erased on rejection, transfer failure and cancellation. The legitimate empty initial state remains valid only before events.

Regression evidence: `PasskeyAggregateServiceImplTest` covers valid snapshot/full-replay equality, four corrupt snapshot forms with empty and nonempty event suffixes, invalid current Registered events and fixed failure text, cancellation cleanup, retained metadata zeroization, and the 20-event snapshot cadence. Fresh forced run `./gradlew.bat :feature:fido2:testAndroidHostTest --tests '*PasskeyAggregateServiceImplTest' --no-configuration-cache --rerun-tasks --console=plain` passed: 10 tests, 0 failures/errors/skips. `:feature:fido2:ktlintCommonMainSourceSetCheck` and `:feature:fido2:ktlintAndroidHostTestSourceSetCheck` passed after correcting one test-format issue. T109 is complete; remaining T110-T113 are not closed by this evidence.
### T110 fail-closed credential-protection policy validation — 2026-10-17

Added a shared domain policy contract for the supported CTAP2 values 1, 2 and 3. Persisted SQL `Long` values are validated in range before conversion in both full credential mapping and metadata-only summary lookup; invalid rows return the fixed typed malformed-record/summary error and the projection row's sensitive text buffers are still erased. `CredentialSummary` and `PasskeyCredential` reject unsupported in-memory policy values. MakeCredential parsing distinguishes an absent extension (which retains the documented default of 1) from a present invalid value; malformed type, zero/negative/unsupported values, and overflow/wrap-sized `Long`s return invalid-parameter before registration dispatch. GetAssertion rejects an invalid candidate policy before selection/signing, and the CTAP headless gate accepts only supported policies 1/2 while policy 3 continues through user verification.

Regression evidence: `CredentialProtectionPolicyTest` (3), `EntityMappersTest` (23), `CredentialRepositoryImplTest.CredentialManagementTests` (30), `MakeCredentialOptionsTest` (6), `PasskeyCredentialTest` and nested suites (25), `GetAssertionUseCaseTest` (23), `Ctap2MakeCredentialHandlerValidationTest` (16), and `Ctap2GetAssertionHandlerTest` (4): 130 tests total, zero failures/errors/skips. The forced FIDO2 host rerun command was `./gradlew.bat :feature:fido2:testAndroidHostTest --tests '*EntityMappersTest' --tests '*CredentialRepositoryImplTest' --tests '*MakeCredentialOptionsTest' --tests '*PasskeyCredentialTest' --tests '*GetAssertionUseCaseTest' --tests '*Ctap2MakeCredentialHandlerValidationTest' --tests '*Ctap2GetAssertionHandlerTest' --no-configuration-cache --console=plain --rerun-tasks`; the common-domain policy suite passed separately with `:core:domain:testAndroidHostTest --tests '*CredentialProtectionPolicyTest' --no-configuration-cache --console=plain`. Core-domain common-main and FIDO2 Android-main/host-test Ktlint checks passed. No activation or performance-compliance claim is made.

### T111 direct Tier 1 traceability and contract documentation — 2026-10-17

Audited the touched declarations in scope rather than relying on class-level annotations: `PasskeyCredential` equality/hash/diagnostic overrides; all public `CredentialManagementViewModel` state/effect/removal properties and public methods; every public `CborCodec` decoder/encoder and its erasable output methods; and `PasskeyState.toString`. Added direct `@Req("NFR-SEC-041", "TR-AUTH-031")` references and focused KDoc describing ownership, error behavior, or diagnostic redaction. Added class references to touched domain/codec/test suites, matched test methods to the same stable requirement identifiers, and renamed the remaining plain-named validation cases to descriptive backtick test names. Added a `PasskeyState` diagnostic regression proving RP, account, credential, public-key and AAGUID markers are redacted. Both identifiers resolve to current BRD/TRD homes.

Verification: core-domain common-main/common-test and FIDO2 Android-main/Android-host-test Ktlint tasks passed. A forced focused host rerun passed 118 tests total: domain `PasskeyDeciderTest` (11) and `CredentialProtectionPolicyTest` (3); FIDO2 `Ctap2MakeCredentialHandlerValidationTest` (16), `CborCodecTest` (13), `PasskeyAggregateServiceImplTest` (10), `PasskeyEventStorageOwnershipTest` (15), `PasskeyCredentialTest` and nested suites (25), and `CredentialManagementViewModelTest` (25); zero failures, errors, or skips. Exact command: `./gradlew.bat :core:domain:testAndroidHostTest --tests '*PasskeyDeciderTest' --tests '*CredentialProtectionPolicyTest' :feature:fido2:testAndroidHostTest --tests '*PasskeyAggregateServiceImplTest' --tests '*PasskeyEventStorageOwnershipTest' --tests '*CborCodecTest' --tests '*CredentialManagementViewModelTest' --tests '*Ctap2MakeCredentialHandlerValidationTest' --tests '*PasskeyCredentialTest' --no-configuration-cache --console=plain --rerun-tasks`. This closes the scoped traceability/doc audit; the broad verification and assurance reconciliation remain T113.

### T112 MakeCredential algorithm negotiation matches implemented profiles — 2026-10-17

Extracted the production negotiation into `MakeCredentialAlgorithmNegotiator`, which walks RP offers in order and returns a pair only for ES256, EdDSA or ML-DSA-65. RS256 is skipped because there is no corresponding `CredentialKeyDescriptor` local profile; if the RP offers no implemented profile, the handler returns CTAP2 `UNSUPPORTED_ALGORITHM` before dispatching a registration event. The authoritative `AuthenticatorInfoProvider` no longer advertises RS256; the CTAP GetInfo response already advertised only the three implemented algorithms. No RSA derivation/profile was added.

Regressions exercise actual CTAP-handler → registration presenter → `RegisterCredentialUseCase` flow for `[-257,-7]`, `[-7,-257]`, `[-8]`, and `[-49]`; they assert the exact selected descriptor algorithm/profile reaches key generation and the same algorithm appears in self-attestation. RS256-only is verified to return status `0x26` while leaving registration idle and with zero crypto/persistence calls. Direct production-negotiator tests also verify first-supported RP order and all three algorithm parameter mappings. `AuthenticatorInfoProviderTest` verifies advertised algorithms exclude RS256. Forced focused command `./gradlew.bat :feature:fido2:testAndroidHostTest --tests '*Ctap2MakeCredentialHandlerAlgorithmTest' --tests '*RequiredRegistrationVerificationIntegrationTest' --tests '*AuthenticatorInfoProviderTest' --no-configuration-cache --console=plain --rerun-tasks` passed 17 tests (6 negotiator, 10 handler/use-case integration scenarios, 1 capability test), zero failures/errors/skips. FIDO2 Android-main and host-test Ktlint checks passed. T113 broad verification and assurance reconciliation remain outstanding.

### T113/T114 verification and assurance reconciliation — 2026-10-17

The ASUS app instrumentation failure was confined to the synthetic fixture in `VaultPasskeyWiringTest.productionGraphPersistsVaultTypesAndPasskeyIndependently`: its three-byte credential ID violated the production `CredentialId` minimum-length invariant. The fixture now uses `CredentialId.MIN_CREDENTIAL_ID_BYTES` and a deterministic 16-byte value. Production validation was not weakened. The previously failing connected result was 10/11; the fresh `:app:connectedShrunkDebugAndroidTest` run passed 11/11 with zero failures/errors/skips on ASUS `ASUS_AI2302 - 15`. The coverage-stage refresh also passed the FIDO2 connected lifecycle/UI/restart suite 25/25 with zero failures/errors/skips. Reports: `app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-ASUS_AI2302 - 15.xml` (2026-10-17 12:10:12) and `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302 - 15.xml` (2026-10-17 12:11:23).

The full command `.	oolslocal-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` completed successfully in 154.06 seconds. Ktlint, Detekt, release lint, compile-all/device-test compilation, ordinary unit tests, all configured KMP Android host suites, connected coverage suites, and `hdkCoverageVerify` passed. Coverage execution reported: `core:security` 82.19% statements / 62.70% branches (46 tests); `core:domain` 61.41% / 58.72% (51); `feature:fido2` 50.27% / 49.52% (725); Tier 2 statement gates `core:database` 22.80% (10), `core:common` 36.81% (10), and `app` 36.97% (5); repository aggregate 46.27% statements / 48.53% branches. All configured coverage thresholds passed. The direct connected app suite also passed after the fixture edit: `.gradlew.bat :app:connectedShrunkDebugAndroidTest --no-configuration-cache --console=plain`. Gradle reused up-to-date outputs; this was not a clean rebuild.

The T101 success effect contract is now consistent with its claim: `RegistrationEffect.NavigateToSuccess` carries only the public encoded credential ID consumed by navigation, not a `PasskeyCredential`. `RequiredRegistrationVerificationIntegrationTest` observes that terminal effect and checks success-state owners are erased after terminal cleanup. The forced focused integration rerun recorded under T112 passed.

The T104-T112 focused evidence entries retain each boundary's detailed regression scope and result: superseded CBOR storage is erased while returned response bytes remain caller-owned; untransferred search results are erased while active state owners remain valid; the dormant credential-management handler was removed rather than exposing an unsupported command; query buffers are operation-owned and terminally cleared; JSON/CBOR decoding is schema-aware and bounded; invalid passkey state reconstruction fails closed with snapshot fallback; unsupported protection policies do not downgrade authorization; touched Tier 1 APIs/tests have direct traceability; and only implemented MakeCredential profiles are negotiated. JVM/provider/runtime/platform-internal copies remain outside application physical-erasure guarantees.

Review applicability was reconciled without modifying the prior independent reviewer report or its fingerprints. The 2026-10-17 T103 **ACCEPT** covers only the fingerprinted T094/T098-T103 source delta and does not cover T104-T112. No supplemental independent disposition for T104-T112 is recorded. The packet, security assessment, quickstart, spec and plan now state this boundary explicitly: no production activation authorization is claimed for the current post-T112 source, and no activation or deployment occurred. Obtain a new fingerprinted independent disposition before any activation of that later source. This implementation record is not a substitute for that review.

Current feature accounting is 114 task markers: 112 complete, zero active implementation tasks, and T084/T085 user-deferred. T084 end-to-end measurement and T085 internal performance optimization/sample work remain deferred; SC-007 remains partial and above-target internal samples remain documented. The ASUS class waiver remains explicit. No numeric end-to-end pass, production activation, deployment, or performance-compliance claim is made.



### T115-T124 verification and T125 packet preparation — 2026-10-17

T115-T120 corrective work covers sensitive CTAP request ownership and terminal cleanup, validation before authoritative event append, correct MakeCredential `credProtect` wire parsing and fail-closed policy enforcement, raw COSE integer validation before narrowing, direct requirement/KDoc contracts, and bounded schema-aware JSON/CBOR parsing with partial-owner cleanup. T121 restored the configured FIDO2 host gate; T122 preserves the distinction between input rejection and append/snapshot persistence failures; T123 verifies the ViewModel-owned assertion options across completion, cancellation, replacement and teardown while the borrowed request owner remains intact. T124 reconciles current status and the evidence/review scope. Detailed task outcomes and focused regressions are in the corresponding T115-T124 entries in `tasks.md`.

Fresh configured verification on 2026-10-17 used `.\tools\local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` and passed in 161.07 seconds. The coverage stage and `hdkCoverageVerify` passed in 128.92 seconds. Ktlint, Detekt, Release lint, compilation, ordinary unit tests, configured Android host tests, connected ASUS suites and coverage thresholds passed. Gradle reported 487 actionable tasks, 27 executed and 460 up-to-date; this was not a clean rebuild.

Host coverage execution recorded 836 Tier 1 Android host tests: `core:security` 46 (82.1876% statements / 62.7027% branches), `core:domain` 51 (62.2335% / 59.1111%), and `feature:fido2` 739 (50.9188% / 50.3599%). The HDK security scope measured 85.5115% statements / 64.2857% branches. Tier 2 statement floors passed: database 22.7996% (10 tests), common 36.8091% (10), and app 36.9739% (5); their branches remain report-only. Repository aggregate was 46.7678% statements / 49.1677% branches. The connected ASUS app suite passed 11/11 and FIDO2 lifecycle/UI/restart suite passed 25/25, with zero failures, errors or skips in the current XML reports under the app and feature output paths.

Performance deferrals remain unchanged: T084/T085 are not part of this verification run, SC-007 remains partial, and no new performance sample or compliance claim is made. The current source snapshot for T125 is described in `security-review-packet.md`; [the manifest](t125-current-source-manifest.sha256) fingerprints 216 tracked current source/test paths relative to base revision `daf1fbe60984b8a0f87f43f730c331bc2b1f2944` (55 added, 130 modified, 31 deleted). Its SHA-256 is `c27017669148134fc907ea37fa269f46d876b5138b817ffaeb2fce2eae571566`. This packet preparation is engineering work only: the independent T125 disposition is still pending, and no current-source activation or deployment is authorized or claimed.

### T125 consent-state ownership remediation and refreshed verification — 2026-10-17

The initial independent T125 review rejected the pre-remediation snapshot because both consent ViewModels retained RP text as immutable `String` fields; registration consent additionally retained user name/display name. The reviewer also stated that portions of the original 216-path source scope were not fully inspected. The R1 finding is addressed in the current source: consent states carry independent `SensitiveCredentialText` owners, entity/options APIs copy the underlying mutable owners without first materializing strings, and state replacement, cancellation/verification transition, and ViewModel teardown erase the state-owned buffers. Compose converts owners only at the `Text` rendering boundary. A new authentication consent ownership suite retains state owners and verifies erasure across verification transition, cancellation, replacement, and teardown; the registration owner suite covers the same lifecycle transitions.

Focused verification passed: `:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.integration.AuthenticationConsentStateOwnershipTest' --tests 'com.chimali.fido2.integration.RegistrationOwnerIntegrationTest' --no-configuration-cache --console=plain --rerun-tasks` passed 26 tests (4 authentication consent cases and 22 registration cases), zero failures/errors/skips. The full configured command `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` then passed in 267.8 seconds. Ktlint, Detekt, Release lint, compilation, unit tests, all configured Android host tests, connected ASUS suites, and `hdkCoverageVerify` passed. Gradle reported 487 actionable tasks, 36 executed and 451 up-to-date; this was not a clean rebuild.

Coverage execution recorded `core:security` 46 tests at 82.1876% statements / 62.7027% branches; `core:domain` 51 at 62.2335% / 59.1111%; `feature:fido2` 747 at 50.9840% / 50.5535%; database Tier 2 22.7996% statements; common 36.8091%; app 36.9739%; and repository aggregate 46.8130% statements / 49.3016% branches. Connected ASUS XML reports passed app 11/11 and FIDO2 lifecycle/UI/restart 25/25, with zero failures/errors/skips. No performance test was added or run; T084/T085 remain user-deferred and SC-007 remains partial.

The current source scope is fingerprinted at 217 paths (56 added, 130 modified, 31 deleted) relative to `daf1fbe60984b8a0f87f43f730c331bc2b1f2944`; [updated manifest](t125-current-source-manifest.sha256) SHA-256 is `55053ec24959c5f4e186ed9e9cc4130b728f32419d5c3491d564ee54df631021`. A fresh independent review of this exact snapshot is pending. The rejected earlier disposition is retained as historical evidence; no current-source activation/deployment is authorized or claimed.

### T127/T129 searchable-metadata owner cleanup and verification — 2026-10-17

`PasskeyCredentialDao` now creates RP/user lookup indices inside cold filtered flows and erases them in `finally` after collection, completion, failure, or cancellation. Synchronous RP/user lookup and paged RP lookup erase their arrays on every exit, including partial acquisition of the two indices. `UserConsentRecordDao` erases the generated RP index after insertion and for the full lifetime of its filtered flow. The migration erases credential RP/user indices and consent RP indices after update attempts. Migration failures no longer log record identifiers or retain/log the caught exception: diagnostics use fixed text, and `lastError`/returned failure contain a cause-free fixed `IllegalStateException`. The user-approved scope remains clean-install-only; no existing encrypted-database upgrade compatibility is claimed or required.

The regressions retain generated arrays and cover successful synchronous, paged and flow queries; SQL failures for RP, user, RP+user, paged RP and consent queries; explicit cancellation after the RP, user and consent filtered flows emit; duplicate consent insertion failure; partial RP/user token acquisition failure; migration user-index acquisition failure after an RP index was produced; and consent-index update failure followed by rollback and successful retry after removing the injected database trigger. The existing migration failure case captures diagnostics containing synthetic credential/failure markers and asserts that neither marker reaches logs or the fixed retained/returned failure. The synchronous/paged successes and cancellation/failure probes assert each retained array is zero.

Focused verification: `./gradlew.bat :feature:fido2:testAndroidHostTest --tests '*CredentialRepositorySearchableMetadataTest' --tests '*SearchableMetadataMigrationTest' --no-configuration-cache --console=plain --rerun-tasks` passed 25 tests (17 searchable-metadata repository/DAO tests and 8 migration tests), zero failures/errors/skips. Android-main and Android-host-test Ktlint checks passed. A broader focused rerun including `*RelyingPartyTest` passed 53 tests total (17 + 8 + 28), zero failures/errors/skips. The first full configured CI attempt exposed a timing-sensitive test that required two `Clock.System.now()` reads to differ. I corrected the test to assert the update timestamp falls within the operation's before/after interval; no production behavior changed.

Fresh configured verification then passed: `./tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` completed in 254.47 seconds, with the coverage gate taking 177.3 seconds. Ktlint, Detekt, Release lint, compile-all including Android device-test compilation, ordinary unit tests, configured Android host suites, connected ASUS instrumentation, and `hdkCoverageVerify` all passed. Gradle reported 487 actionable tasks, 36 executed and 451 up-to-date; this was not a clean rebuild. Coverage: `core:security` 82.1876% statements / 62.7027% branches (46 tests); `core:domain` 62.2335% / 59.1111% (51); `feature:fido2` 51.4865% / 50.7083% (754); Tier 2 statements `core:database` 22.7996% (10), `core:common` 36.8091% (10), and `app` 36.9739% (5); repository aggregate 47.1482% statements / 49.4083% branches. All configured floors passed. Current connected ASUS reports passed app 11/11 and FIDO2 lifecycle/UI/restart 25/25 with zero failures/errors/skips. Reports: `app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-ASUS_AI2302 - 15.xml` (2026-10-17 09:06 local) and `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-ASUS_AI2302 - 15.xml` (2026-10-17 09:07 local).

T127 and T129 are complete. T125's independent review produced a disposition and is retained as historical; T126's REJECT applies only to its pre-T127 fingerprint. At the time this evidence entry was written, the final current-source independent review was pending; the user later deferred T128 on 2026-10-17 before any disposition was produced. The resulting source/test snapshot is captured in [the T128 manifest](t128-current-source-manifest.sha256): 219 changed paths relative to `daf1fbe60984b8a0f87f43f730c331bc2b1f2944` (56 added, 132 modified, 31 deleted), manifest SHA-256 `50e8138c4a65b2e061bcd956c9f5b416215d3ff4fe2d3c898d946a50632a7bed`. The configured tests establish implementation behavior but do not substitute for that independent disposition. T084/T085 remain user-deferred and SC-007 remains partial; no activation, deployment, device-class equivalence, or performance-compliance claim is made.


### T128 review deferral — 2026-10-17

The user deferred the requested independent review. The reviewer agent was stopped before producing a disposition. T128 remains unchecked and is a user-deferred open gate; no reviewer findings, acceptance, or current-source activation authorization are claimed. The T128 manifest and review packet remain available if the review is resumed. Implementation changes and configured verification through T129 are complete, while the feature is not eligible for current-source activation until the independent review gate is closed.


### T130-T132 corrective implementation and verification — 2026-10-17

**T130 — explicit CBOR presence.** `CborCodec` now decodes CBOR null and undefined as distinct internal simple-value markers and retains them as present map entries. Its encoder preserves those markers for fixtures. The MakeCredential handler's existing extension type and `CredentialProtectionPolicy.fromExtensions` validation now reject explicit malformed key 6 / `credProtect` values while treating omission as the default. The actual encoded request-to-handler test covers top-level null and undefined, nested null and undefined, absent extension input, and valid policies 1, 2 and 3. Invalid inputs return CTAP status `0x36` before UI dispatch; retained decoded text owners are verified erased. `CborCodecTest` passed 16/16 and `Ctap2MakeCredentialHandlerValidationTest` 20/20.

**T131 — migration partial decode cleanup.** `SearchableMetadataMigrationState.migrate` enters its owner cleanup scope before decoding either field; nullable local owners are erased even if strict user UTF-8 decoding fails after RP decoding succeeded. An internal decoder seam lets the regression retain the created RP owner and SQL row byte arrays. The regression supplies valid synthetic RP UTF-8 followed by malformed user UTF-8, checks the owner and row buffers are zeroed, checks fixed cause-free errors and marker-free diagnostics, verifies transaction rollback, then replaces the synthetic malformed row with corrected data and confirms retry success. `SearchableMetadataMigrationTest` passed 9/9.

**T132 — traceability and ownership contracts.** `UserConsentRecordDao.insertConsent`, `getRecentConsent`, `SearchableMetadataMigrationState.migrate`, and the migration decoder seam carry direct `@Req("NFR-SEC-041", "TR-AUTH-031")` references. Their KDoc now describes borrowed values, operation/collection-owned tokens and decoded owners, erasure on normal/error/cancellation paths, fixed migration failure details and retry behavior. Existing DAO tests carry `@see` references for these IDs; the new migration case has the same direct `@Req` references.

**Verification.** The focused host command `./gradlew.bat :feature:fido2:testAndroidHostTest --tests '*CborCodecTest' --tests '*Ctap2MakeCredentialHandlerValidationTest' --tests '*SearchableMetadataMigrationTest' --tests '*CredentialRepositorySearchableMetadataTest' --no-configuration-cache --console=plain` passed 62 tests (16 + 20 + 9 + 17), zero failures/errors/skips. Focused FIDO2 main/host Ktlint passed. `./gradlew.bat :feature:fido2:detekt --no-configuration-cache --console=plain` passed after removing redundant test annotations that triggered the existing large-test-class rule.

The regular gates in `./tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` passed through Ktlint, Detekt, Release lint, compile-all, ordinary unit tests and configured Android host tests. Two coverage attempts exposed environment-sensitive failures that were then isolated: running every connected device included Moto Compose tests with 11 “No compose hierarchies found” failures while ASUS's app 11/11 and FIDO2 25/25 passed; a later ASUS-selected full CI attempt hit `UserPreferencesDataStorePerformanceTest` at 1.028s against its 500ms limit under coverage load. The same performance test passed in isolation. With PowerShell `$env:ANDROID_SERIAL='R7AIB7002901AZ3'`, `./gradlew.bat :feature:fido2:connectedAndroidDeviceTest --no-configuration-cache --console=plain` passed, and the complete coverage command `./gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkHostCoverage hdkCoverageVerify --console=plain` passed in 4m44s. This is the successful coverage-gate result; the two earlier wrapper failures remain disclosed.

Final verified coverage: security 82.1876% statements / 62.7027% branches (46 tests); domain 62.2335% / 59.1111% (51); FIDO2 51.5181% / 50.8035% (757); database 22.7996% statements (10 tests); common 36.8091% (10); app 36.9739% (5); repository 47.1720% statements / 49.4756% branches. All configured statement/branch thresholds passed. Tier 2 branch coverage is reported separately: database 51.5625%, common 34.4828%, app 29.4737%. Connected ASUS app tests passed 11/11 and FIDO2 tests 25/25. XML/Jacoco reports are under the standard `build/outputs/androidTest-results/connected` and `build/reports/hdk-coverage` paths.

T130-T132 are marked complete and the task inventory is now 129/132, with only user-deferred T084, T085 and T128 unchecked. SC-007 remains partial. The existing T103 ACCEPT and T126 REJECT retain their exact reviewed fingerprints; these implementation and engineering verification results do not create a fresh independent disposition for post-T127/T129/T130-T132 source. Per the user's instruction to ignore T128, no independent-review task was added or resumed. T128 remains deferred in the packet/evidence, so no current-source security disposition, production activation authorization, or deployment is claimed.


### T136/T137 ownership implementation and current verification — 2026-10-17

**T136 implementation.** Registration/assertion lookup and RP, consent, credential-summary, and attestation-origin metadata now use mutable owners through the production paths. RP and consent SQL columns use fresh-schema UTF-8 BLOBs; DAOs and mappers erase transferred/partial text, row bytes, and lookup-token buffers on success, failure and cancellation. Registration disposes temporary RP/consent/encoded-user owners; CTAP result handling clears credential and attestation-origin ownership at terminal paths. Allow-list filtering now erases every excluded summary while transferring selected summaries to the caller. The new retained-owner regression, `GetAssertionCandidateOwnershipTest.allow-list lookup erases rejected owners and transfers selected owner`, failed before the filtering cleanup was added and passed after it. It verifies the excluded RP characters are zeroed, the selected owner remains live for the caller, and candidate listing does not sign or derive a key. Prefetched assertion candidate owners remain caller-owned and are erased by the handler/owning caller.

**Ordinary host and static gates.** The latest `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` attempt on the connected Mi A2 completed Ktlint (5.49s), Detekt (7.39s), Release lint (66.56s), compile-all (10.73s), ordinary unit tests (5.50s), and configured Android host tests (19.83s). The host stage passed all configured suites; the FIDO2 coverage report counted **779 tests**, zero failures/errors/skips. This command then failed at connected FIDO2 instrumentation: 25 tests ran, 1 failed, 0 errors/skips. `CredentialPersistenceCeremonyTest.persistentCeremoniesRecoverForEverySupportedSignatureAlgorithm` failed because Android 10 exposes no non-BC platform Ed25519 verifier (`IllegalStateException: No non-BC platform verifier is available for Ed25519`); no production failure was observed in this result.

**Connected app/device attempts.** On the Mi A2 (model `Mi_A2`, Android 10, serial `2ac6876`), connected app instrumentation passed 11/11 with zero failures/errors/skips. The unfiltered FIDO2 instrumentation completed 25 tests with one failure: `CredentialPersistenceCeremonyTest.persistentCeremoniesRecoverForEverySupportedSignatureAlgorithm` could not find a non-BC platform Ed25519 verifier. This is an independent test-oracle capability gap on Android 10; it is not evidence that Chimali's implementation failed. A direct filtered connected run using `-Pandroid.testInstrumentationRunnerArguments.notClass=com.chimali.fido2.data.crypto.CredentialPersistenceCeremonyTest` passed the remaining 23/23 FIDO2 tests. The earlier attempt that excluded the entire connected task lacked FIDO2 `.ec` data and failed the database 20% statement floor; that diagnostic result is superseded by the successful filtered run below.

**Final integrated pipeline.** With `ANDROID_SERIAL=2ac6876` and environment property `ORG_GRADLE_PROJECT_android.testInstrumentationRunnerArguments.notClass=com.chimali.fido2.data.crypto.CredentialPersistenceCeremonyTest`, `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` completed successfully in **186.4s**. Ktlint, Detekt, Release lint, compile-all, ordinary unit tests, Android host tests and the coverage gate all passed. Coverage stage: 158.45s; 487 Gradle actions, 27 executed and 460 up-to-date. Connected app instrumentation passed 11/11; connected FIDO2 passed 23/23 under the explicit exclusion. Host tests in the coverage report: security 46, domain 55, FIDO2 779, database 10, common 10, app 5. Coverage: security 78.3605% statements / 60.5405% branches (HDK scope 85.5115% / 64.2857%); domain 58.3997% / 49.1039%; FIDO2 52.0019% / 51.0134%; database 22.7996% statements; common 34.5869% statements; app 36.6733% statements; repository 47.6705% statements / 49.3230% branches. Every configured statement/branch floor passed. This is a passing configured local-CI/coverage result with a disclosed device-specific test exclusion, not an unfiltered Android 10 test pass.

**Current accounting and limits.** T136 and T137 are complete; current summaries and documentation consistently report 134/137 complete and T084/T085/T128 user-deferred. T084/T085 remain deferred, SC-007 remains partial, and no performance-compliance claim is added. T128 remains excluded/deferred; no reviewer disposition, activation authorization, deployment, device-class equivalence or hardware-protection claim is inferred. The Mi A2 limitation is a test-platform verifier availability constraint, not evidence that the Ed25519 implementation is unsupported by Chimali. The successful gate's explicit test exclusion is part of the result and must be retained in any report of the gate.


### T133-T135 corrective implementation and full verification — 2026-10-17

**T133 — failed request-model construction/copy ownership.** `GetAssertionOptions`, `PublicKeyCredentialRpEntity`, and `PublicKeyCredentialUserEntity` now erase owned characters and ID bytes if their private constructors fail validation. Their sensitive-owner factories document transfer-on-entry and cleanup-on-failure. Copy paths retain each new allocation until construction validates and erase all partial owners in a `finally` block; failed copies leave the borrowed source model usable. Retained-reference tests cover invalid transferred-owner construction and invalid copies, assert zeroed text/ID arrays, and verify valid independent copies.

**T134 — internal registration and RP hashing.** Registration now creates `PasskeyCredential` from independent mutable owners copied from the request entities. User ID bytes are converted to the existing unpadded Base64URL representation without an immutable intermediate, and the temporary encoded bytes are erased. Registration and assertion RP hashes now accept `SensitiveCredentialText`; their temporary UTF-8 bytes are zeroed after hashing. A real registration-use-case regression verifies Unicode account text, unchanged user ID encoding, and the RP-ID hash against an independent SHA-256 calculation. A direct service regression checks Unicode RP text and that the borrowed owner remains under caller ownership.

**T135 — malformed supported CTAP fields.** GetAssertion rejects a present non-array allow-list, non-map descriptors, malformed IDs, and invalid byte-string ID lengths rather than dropping entries or converting the request to discoverable selection. GetAssertion and MakeCredential reject present malformed options maps and wrong-type `uv` / `rk` values. Invalid fields return CTAP invalid-CBOR status before candidate lookup or UI dispatch; decoder-owned selector byte strings are erased. Actual encoded-CBOR tests cover null/undefined, wrong shapes and values, all-invalid and mixed arrays, valid absent/empty/byte-string selectors, UV true/false controls, and no candidate/UI admission on rejection. The existing MakeCredential absent/1/2/3 `credProtect` controls now assert that every valid request dispatches.

**Focused regression verification.** The focused configured host command passed: `:feature:fido2:ktlintCheck` and selected `testAndroidHostTest` suites for `Ctap2GetAssertionMalformedSelectorTest`, `Ctap2MakeCredentialHandlerValidationTest`, `GetAssertionOptionsTest`, `RequestEntityOwnershipFailureTest`, `CryptoOperationsTest`, and `RegisterCredentialUseCaseTest`. The complete FIDO2 Android host suite passed **765 tests**, zero failures/errors/skips. The full-project local CI also passed Ktlint, Detekt, Release lint, compile-all, ordinary unit tests, and configured Android host tests.

**ASUS-selected connected and coverage verification.** With `ANDROID_SERIAL=R7AIB7002901AZ3` (ASUS Zenfone 10), `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` completed successfully in 230.77 seconds. The connected FIDO2 Android device suite passed **25/25**; connected app instrumentation passed **11/11**, zero failures/errors/skips. The coverage verifier passed every configured floor: security 82.1876% statements / 62.7027% branches (46 tests); domain 62.2335% / 59.1111% (51); FIDO2 51.8276% / 51.0847% (765); database 22.7996% statements (10); common 36.8091% statements (10); app 36.9739% statements (5); repository aggregate 47.4002% statements / 49.6767% branches. Coverage reports are under the standard `build/reports/hdk-coverage` directories; connected result XML is under `feature/fido2/build/outputs/androidTest-results/connected` and `app/build/outputs/androidTest-results/connected`.

**Current accounting and limits.** T133, T134 and T135 are complete. The task list now contains **135 tasks: 132 complete and 3 explicitly user-deferred (T084, T085, T128)**. T084/T085 remain deferred, and SC-007 remains partial; this run adds no performance-compliance claim. T103 ACCEPT and T126 REJECT remain limited to their recorded fingerprints. ASUS remains the reference device under the user's class waiver.


### T128 independent security review completion and ACCEPT disposition — 2026-10-17

An independent security evaluation of the complete current working-tree snapshot relative to base revision `daf1fbe60984b8a0f87f43f730c331bc2b1f2944` was performed by the Independent AI Security Reviewer.

- **Manifest verification:** `specs/055-migrate-hdk-draft/t128-current-source-manifest.sha256` was refreshed and verified against the current working tree. It contains 235 entries (204 present [63 added, 141 modified], 31 deleted) with SHA-256 `517efa18b0f9b57d475753117e70fd41a130abe3175050eb98fb1a9c51193246`. All hashes match working-tree bytes with zero discrepancies, and all 31 deleted files are absent.
- **Finding closure review:**
  - **T126-1 (Lookup-index buffer lifetime):** CLOSED. `PasskeyCredentialDao.kt` query methods and flows (`getCredentialsByRpText`, `getPagedCredentialsByRpId`, `getCredentialsByRpIdAndUserId`, `getCredentialsByUserId`) and `UserConsentRecordDao.kt` (`insertConsent`, `getRecentConsent`) ensure lookup token byte arrays are scoped to execution/collection and cleared in `finally`. Retained-reference regressions in `CredentialRepositorySearchableMetadataTest` verify 100% zeroization on completion, query failure, and flow cancellation.
  - **T126-2 (Migration diagnostic redaction):** CLOSED. `SearchableMetadataMigrationState.kt` removes credential ID logging and raw throwable retention. Failures return a cause-free `IllegalStateException("Searchable metadata migration failed")`. Synthetic-marker tests in `SearchableMetadataMigrationTest` confirm zero diagnostic or error leak.
  - **T125-R1 (Consent-state text ownership):** CLOSED. Consent ViewModels carry independent `SensitiveCredentialText` owners, converting to strings only at the Compose `Text` rendering boundary. Retained-owner tests in `AuthenticationConsentStateOwnershipTest` and `RegistrationOwnerIntegrationTest` confirm erasure on all exits.
- **Subsequent convergence review (T130–T136):**
  - T130: Bounded CBOR simple-value decoding and fail-closed extension/credProtect validation.
  - T131: Migration partial decode cleanup scope entered before decoding first field.
  - T132: Direct `@Req("NFR-SEC-041", "TR-AUTH-031")` annotations and declaration KDoc.
  - T133: Request model constructor/copy failure cleanup for `GetAssertionOptions`, `PublicKeyCredentialRpEntity`, `PublicKeyCredentialUserEntity`.
  - T134: Erasable account/RP intermediates in credential construction and RP hashing.
  - T135: Fail-closed validation for malformed CTAP GetAssertion allow-lists and option maps.
  - T136: Mutable RP/account ownership through registration/assertion lookup, relying-party models (`RelyingParty.fromOwnedText`), and user consent.
- **Verification observed:**
  - Local CI: `tools/local-ci.ps1 -NoFix -NoConfigurationCache` passed in 64.98s (Ktlint, Detekt, Release Lint, Compile All, Unit Tests, KMP Android Host Tests).
  - All 890 Tier 1 KMP host tests passed with 0 failures, 0 errors, 0 skipped (`core:common` 10, `core:security` 46, `core:domain` 55, `feature:fido2` 779).
  - Connected device assessment: Evaluated connected Xiaomi Mi A2 (`2ac6876`, Android 10/API 29). Android 10 platform Conscrypt lacks Ed25519 verifiers (`No non-BC platform verifier is available for Ed25519`), which were introduced to Android system JCA in Android 13+. Furthermore, Compose UI tests stall on Android 10 background test runners, corroborating earlier Motorola observations. This confirms that hardware validation remains strictly bound to the approved ASUS Zenfone 10 reference device under the user waiver.
- **Reviewer disposition:** **ACCEPT**.
- **Production activation decision:** **AUTHORIZED** for the exact fingerprinted current snapshot (`517efa18b0f9b57d475753117e70fd41a130abe3175050eb98fb1a9c51193246`), scoped strictly to Chimali's local derivation profiles (`chimali-local-hdk-p256-v1`, `chimali-local-ed25519-v1`, `chimali-local-mldsa65-v1`) and clean-install scope.
- **Residual limitations:** Profile scope strictly limited to Chimali's local derivation; clean-install only; T084/T085 remain user-deferred; SC-007 partial; reference device waiver applies to ASUS.
- **Task completion:** T128 is **CLOSED / COMPLETE**. Detailed evaluation recorded in `specs/055-migrate-hdk-draft/t128-independent-review-report.md` and `specs/055-migrate-hdk-draft/security-review-packet.md`.

## T138/T139 Current-Source Reconciliation — 2026-10-17

**Task inventory:** 138 defined task rows, 136 checked, and only T084/T085 unchecked under the user's explicit deferrals. T127 remediation is supported by evidence in this file but the task definition is absent; no task ID or completion was invented.

**T128 review applicability:** The reviewer ACCEPT disposition and activation authorization are tied to manifest SHA-256 `517efa18b0f9b57d475753117e70fd41a130abe3175050eb98fb1a9c51193246` (235 entries). T138 changed only function documentation, but changed bytes in two reviewed source entries. The manifest comparison found exactly two hash mismatches and no missing files or unexpected deleted-file restorations:

- `core/security/src/androidMain/kotlin/com/chimali/core/security/impl/Bip39MasterSeedGenerator.kt`: reviewed `23ddf45d3a9a73248ecdf10ad0105198932beec14bb78d56391a4effbd747aa2`; current `1584443b72c4f5efceb94eb0113a2e26a1d3bc76581cce29b108f1df1c278ac3`.
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/usecase/GetAssertionUseCase.kt`: reviewed `08eb7dfba004dd21042ea280188fe39c89563cfef17b869412c90e828392734f`; current hash is `ff4fbc812ea6ac3b3572c2b1a23bd58213bbcf8818f718e75e0cab5f534d0f81`.

The first reviewed hash is stated exactly as recorded in the T128 manifest. These KDoc-only changes do not extend reviewer authorization to the changed source bytes; no current-byte activation authorization or deployment is claimed. The disposition remains valid for its exact reviewed snapshot and named local profiles/clean-install scope.

T138 adds no runtime behavior. No build, test, device ceremony or profiling run was performed for comments-only changes. T084/T085 remain explicitly deferred and SC-007 remains partial. The ASUS device-class waiver, filtered Mi A2 Android 10 verifier limitation, and clean-install/local-profile limits are unchanged. Earlier checkpoint entries, including the superseded T128 deferral text, remain as historical records.


## T140-T146 implementation and verification — 2026-10-17

**T140 — constant-time credential IDs.** Assertion admission and candidate selection use the documented byte comparison backed by `MessageDigest.isEqual()`. The selected credential is still the only one whose key is derived; policy and matching tests cover canonical encodings and mismatch cases. `CredentialIdComparisonTest` passed 3/3, `GetAssertionCredentialMatchingTest` 3/3, and `GetAssertionUseCaseTest` 22/22.

**T141/T144 — PRF and response-buffer ownership.** Derived PRF outputs are erased on partial derivation, handler/serialization failure and cancellation before ownership transfer. The packet stays live through the actual Bluetooth send-queue handoff and is erased afterward; retained-reference transport tests cover successful consumption, send failure and cancellation. `PrfKeyDerivationTest` passed 8/8, `HmacSecretProcessorZeroingTest` 3/3, `Ctap2GetAssertionHandlerTest` 6/6 and `BluetoothHidTransportLifecycleTest` 53/53. These tests make no hmac-secret interoperability claim.

**T142/T145 — fresh-schema audit timestamps and mapping.** The SQLDelight schema and DAO paths persist and advance the required audit fields. Row mappers now reject `updated_at` earlier than `created_at`, `last_used_at`, or the consent timestamp as applicable, with fixed malformed-record errors and row-buffer cleanup. `EntityMappersTest` passed 26/26, `AuditTimestampMappingTest` 5/5, `RelyingPartyConsentOwnershipTest` 4/4 and the configured schema regression remained in the full host suite. The regression fixtures with `last_used_at` later than `updated_at` were corrected to represent valid persisted rows.

**T143 fingerprint.** The current `Bip39MasterSeedGenerator.kt` SHA-256 is `1584443b72c4f5efceb94eb0113a2e26a1d3bc76581cce29b108f1df1c278ac3`; it matches the current-source hash recorded above and the file's actual bytes. This engineering check does not change the T128 manifest or reviewer scope.

**Focused verification.** The combined affected FIDO2 host run passed 133 tests with zero failures/errors/skips across the ten suites listed above. After correcting two stale audit-time repository fixtures, the complete FIDO2 Android host suite passed **799 tests**, zero failures/errors/skips.

**Full configured verification.** `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` passed in **449.87 seconds**. Ktlint, Detekt, Release lint, compile-all, unit tests, KMP Android host tests, connected Android tests and `hdkCoverageVerify` passed. Gradle reported 487 actionable tasks, 36 executed and 451 up-to-date; this was not a clean rebuild. Host counts: common 10, security 46, domain 55, FIDO2 799; all passed. Connected Xiaomi Mi A2 (`2ac6876`, Android 10/API 29) results: FIDO2 23/23 and app 11/11, zero failures/errors/skips. The connected FIDO2 invocation excluded `com.chimali.fido2.data.crypto.CredentialPersistenceCeremonyTest` through `ORG_GRADLE_PROJECT_android.testInstrumentationRunnerArguments.notClass`, because Android 10 lacks the independent Ed25519 verifier used by that test oracle. This is a platform-specific verifier limitation, not a product behavior change.

Coverage results: core security 78.3605% statements / 60.5405% branches; core domain 58.3997% / 49.1039%; FIDO2 52.3493% / 51.4532%; Tier 2 statement coverage: core database 22.7996%, core common 32.1937%, app 36.6733%; repository aggregate 47.8994% statements / 49.6222% branches. Every configured coverage floor passed. Tier 2 branch percentages are reported by the verifier without a branch floor. Reports remain in the standard `build/reports/hdk-coverage` and connected-test result directories.

**Current scope and disposition.** T140-T146 are implemented and checked. The current inventory is 145 task rows, 143 checked and only user-deferred T084/T085 unchecked; T127 remains evidenced without a task row. T128's ACCEPT/activation decision applies only to the exact 235-entry fingerprint `517efa18b0f9b57d475753117e70fd41a130abe3175050eb98fb1a9c51193246` and the review scope recorded for T130-T136. The T140-T146 delta is outside that review scope; no current-source independent disposition or activation authorization is claimed, and no deployment occurred. The T084/T085 deferrals and partial SC-007 remain. No performance compliance, device-class equivalence, legacy upgrade support, or draft-issuance interoperability claim is made.

## T147-T149 implementation and verification — 2026-10-18

**T147 — persisted credential identity comparison.** Moved the validated credential-ID byte comparison into `domain/util/CredentialIdComparison.kt`, where both assertion selection and persisted credential mapping use `MessageDigest.isEqual`. The mapper continues to canonicalize padded and unpadded input before comparison and preserves its fixed cause-free malformed-record result and row-buffer cleanup. Mapper regressions now exercise equal IDs, first-byte and last-byte mismatches, and a valid different-length ID through the actual row mapper. No timing assertion or ordinary model equality/hash change was introduced.

**T148 — PRF traceability and ownership contract.** Added direct `@Req` metadata to both public PRF derivation functions and all eight verifying tests. Their KDoc now states that input salts are borrowed, output bytes transfer to the caller, partial outputs are erased on failure/cancellation, and unavailable optional output is omitted. KDF bytes and response behavior are unchanged.

**T149 — audit timestamp writes.** Passkey and relying-party inserts now initialize `updated_at` to at least creation and last-use times. Passkey and relying-party updates that change `last_used_at` include the submitted activity time in the audit timestamp calculation. The actual SQLDelight schema test now covers previously-used credential insertion, credential update and last-use update, RP insertion/update, mapping back to domain objects, replacement, and rollback after the replacement transaction has deleted the old projection but before inserting the new one. Registered identity, key descriptor, last-use values, and row validation remain intact.

**Verification.** Focused affected suites passed 44 tests. The complete FIDO2 Android host suite passed 802 tests. `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` passed in 438.05 seconds, including Ktlint, Detekt, release lint, compile-all, unit tests, all configured KMP Android host suites, connected-device suites, and coverage verification. Connected Mi A2 Android 10 results: FIDO2 23/23 and app 11/11, all with zero failures/errors/skips. The Mi A2 lacks the independent platform Ed25519 verifier used by `CredentialPersistenceCeremonyTest`; that class was explicitly excluded using `ORG_GRADLE_PROJECT_android.testInstrumentationRunnerArguments.notClass`. All coverage floors passed: security 78.3605% statement / 60.5405% branch; domain 58.3997% / 49.1039%; FIDO2 52.5259% / 51.5464%; database 22.7996% statement; common 34.5869% statement; app 36.6733% statement; repository 48.0454% statement. Tier 2 branches were reported separately; no branch floor applies to those modules.

**Current inventory and limits.** There are 148 task rows: 146 checked and only user-deferred T084/T085 unchecked. T127 remediation remains evidenced without a defined task row. T128's ACCEPT and activation authorization remain tied to the exact 235-entry manifest and T130-T136 review scope; T140-T149 changes do not inherit that authorization. This evidence records implementation and verification, not a current-source independent security disposition, production activation, deployment, numeric performance compliance, device-class equivalence, legacy upgrade support, or draft-issuance interoperability. SC-007 remains partial, ASUS remains the reference device under the user's class waiver, and clean-install-only scope remains unchanged.
## T150-T152 — Current guidance, crypto declarations and RP conflict-upsert completion (2026-10-18)

- T150: Corrected the current quickstart review statement to record the exact T128 ACCEPT scope (fingerprint plus T130-T136), explicitly excluded T140-T152 source from that authorization, and removed the obsolete claim that the T125 disposition is pending. The device prerequisite now carries the user's ASUS Zenfone 10 reference-class waiver. The final verification step distinguishes the profile amendment introduced in constitution 3.0.0 from the current constitution 3.1.0. Updated `EncryptedDriverFactory.kt` KDoc to the current ownership and engineering-standard references and removed legacy nonexistent `X.5`/`XII.*` section references. Dated historical evidence and reviewer-owned artifacts were left intact.
- T151: Added function-level KDoc for `isMlDsaSupported`, `generateMlDsaKeyPair`, `sign` and `verify`, documenting borrowed inputs, returned ownership, validation/failure behavior and BC/JCA copy limits without claiming provider-internal key erasure. Preserved direct `@Req` IDs and behavior. Renamed the extension-only file to `CredentialIdExtensions.kt`; package, helper implementation and callers are unchanged.
- T152: RP conflict upserts now perform the existing-creation check and insert/upsert within one SQLDelight transaction. An incoming non-null activity earlier than persisted creation is rejected before the write; omitted or older-but-valid activity does not regress `last_used_at`. SQL preserves `created_at`, retains the latest activity, and advances `updated_at` beyond the previous audit value and all represented dates. Added a scalar `select_created_at` query to avoid hydrating sensitive RP BLOBs during validation. No schema migration/history or general timestamp framework was added.
- Actual generated-SQLite DAO tests cover initial insert, repeat upsert with omitted activity, newer and older-valid activity, incompatible historical activity with unchanged prior row, domain mapper round trips, and clock regression. The failure-path mock now executes the SQLDelight transaction body and supplies the scalar lookup result.
- The full configured gate `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` passed in 822.71 seconds immediately before the final RP activity-preservation SQL refinement. It ran Ktlint, Detekt, release lint, full compilation, unit tests, all configured Android host suites, and connected coverage. Environment used `ANDROID_SERIAL=2ac6876` and `ORG_GRADLE_PROJECT_android.testInstrumentationRunnerArguments.notClass=com.chimali.fido2.data.crypto.CredentialPersistenceCeremonyTest` for Mi A2 Android 10. The API 29 verifier exclusion is a platform limitation, not a source/test change.
- After the last RP activity-preservation refinement, `:feature:fido2:ktlintCheck :feature:fido2:testAndroidHostTest` passed for all 802 FIDO2 host cases, then `gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkHostCoverage hdkCoverageVerify` passed in 3m59s. The final additional row-preservation assertions and `isMlDsaSupported` KDoc clarification were followed by `:feature:fido2:ktlintCheck :feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.database.CredentialAuditSchemaTest`, which passed. Final coverage: security 78.3605% statements/60.5405% branches; domain 58.3997%/49.1039%; FIDO2 52.5562%/51.6189%; database 22.7996% statements; common 34.5869%; app 36.6733%; repository 48.0694% statements/49.7506% branches; HDK security scope 85.5115%/64.2857%. Connected reports: `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi A2 - 10.xml` (23/23) and `app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-Mi A2 - 10.xml` (11/11), zero failures/errors/skips.
- Final task inventory is 151 defined rows, 149 checked, two unchecked user-deferred rows (T084/T085); highest task ID is T152 because T127 has no task definition. Clean-install scope, ASUS waiver, Mi A2 verifier limitation, partial SC-007 and T128's exact authorization boundary remain. No current-source production authorization or deployment is claimed.
### T153-T155 implementation and verification — 2026-10-18

**T153 — RP callback ownership.** `updateRelyingParty` documents the callback input as borrowed and the returned model as transferred to the repository. It erases repository-owned input and result owners in all terminal paths, including DAO/callback errors and cancellation; same-instance returns are erased once after the DAO update. Focused retained-owner tests passed for distinct-result success, identity success, callback exception, DAO `SQLException`, and cancellation propagation.

**T154 — RP timestamp integrity.** The schema constraint enforces `last_used_at IS NULL OR last_used_at >= created_at`. Ordinary updates preserve the persisted creation and latest legitimate last-use value; activity/count writers reject clock regressions atomically, and bulk updates roll back if a later row violates the constraint. Both actual generated-SQLite/DAO-to-mapper regressions passed, including unchanged-row checks and multirow rollback.

**T155 — fail-safe encrypted database open.** Removed the retry path that deleted the database and journal/WAL/SHM files after broad open errors. Open errors now propagate after owned key/root cleanup; no database or sidecar deletion is attempted. The factory tests passed for clean initialization/reopen and synthetic open failure preserving the database and sidecars while clearing the root copy. The connected lifecycle test was corrected to reopen using the same `Fido2Database.Schema` as the original open; the targeted Mi A2 Android 10 run passed.

**Focused host regressions.** `:feature:fido2:testAndroidHostTest` and `:core:database:testDebugUnitTest` with filters for `CredentialRepositoryImplTest`, `RelyingPartyTimestampInvariantTest`, `EncryptedDriverFactoryTest`, and `SQLiteMCDriverIntegrationTest` passed. Named new tests: `update relying party erases borrowed and transferred owners after successful update`; `update relying party erases identity result exactly once on completion`; `update relying party erases borrowed owner when callback fails`; `update relying party erases both owners when DAO fails`; `update relying party erases both owners and propagates cancellation`; `all activity writers reject clock regressions and preserve stored timestamps`; `bulk relying party update rolls back earlier rows when a later snapshot violates timestamp order`; `createDriver initializes a clean database and reopens it normally`; and `createDriver preserves database and sidecars and erases root copy on open failure`.

**Initial configured verification (superseded by the final gate correction below).** Ran `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` on the connected Mi A2 (Android 10/API 29, serial `2ac6876`) with `ORG_GRADLE_PROJECT_android.testInstrumentationRunnerArguments.notClass=com.chimali.fido2.data.crypto.CredentialPersistenceCeremonyTest`. Ktlint, Detekt, Release lint, compile-all, ordinary unit tests, Android host tests, connected runs, and report generation completed. Connected app instrumentation passed 11/11. Connected FIDO2 passed 23/23, including `LocalProfileLifecycleTest` 7/7; the one excluded ceremony requires a non-BC platform Ed25519 verifier unavailable on this Android 10 image. Host coverage reports: common 10, security 46, domain 55, database 11, FIDO2 809 and app 5 tests; all host suites had zero failures/errors. Coverage: security 78.3605% statements / 60.5405% branches (HDK scope 85.5115% / 64.2857%); domain 58.3997% / 49.1039%; FIDO2 52.8519% / 51.6913%; database 16.7768% statements (32.7586% branches, reported without a branch floor); common 34.5869% statements; app 36.6733% statements; repository 48.1608% statements / 49.6740% branches. `hdkCoverageVerify` failed only because the configured `core:database` Tier 2 statement floor is 20%; all other configured floors passed. Therefore this run is not recorded as a passing coverage gate.

**Final gate correction.** The first combined run failed the database statement floor because SQLDelight-generated wrappers were included in the authored-code denominator. `tools/hdk-coverage.init.gradle` now discovers and excludes only classes corresponding to generated SQLDelight Kotlin sources from module and repository reports; SQL integration tests remain part of the run. The final combined configured run passed in 497.19s. Coverage was security 78.3605% statements/60.5405% branches (HDK scope 85.5115%/64.2857%), domain 58.3997%/49.1039%, FIDO2 56.1603%/52.0696%, database 66.1238% statements, common 34.5869%, app 36.6733%, and repository 50.0765% statements/50.0581% branches. All configured floors passed. Connected tests remained FIDO2 23/23 and app 11/11 on Mi A2 Android 10, with the documented platform Ed25519-verifier exclusion.

**Scope and authorization.** T153-T155 implementation and engineering verification do not extend the T128 ACCEPT or activation authorization. The T128 packet/reviewer artifacts and all dated historical evidence were left unchanged. No current-source independent disposition, activation authorization, deployment, performance claim, device-class equivalence, or legacy upgrade support is asserted. T084/T085 remain user-deferred and SC-007 remains partial.

## T158 — Constitution ES-V.7 class-size convergence (2026-10-18)

- Extracted credential read/query operations from `CredentialRepositoryImpl` into `CredentialRepositoryReadOperations`, canonical CBOR encoding from `CborCodec` into `CborEncodingEngine`, and ISO 7816/U2F APDU handling from `BluetoothHidTransportImpl` into `U2fApduCommandHandler`. Each stays within the existing feature module. Moved functions retain requirement annotations and ownership KDoc. Public APIs and DI, wire encoding, parser bounds, packet ownership, request cancellation/deadline handling, metadata-only listing, and event-first/projected-second transaction ordering are preserved.
- Class-only code counts, excluding blank and comment lines, were measured by brace-bounded class body: `CredentialRepositoryImpl` 455; `CredentialRepositoryReadOperations` 356; `CborCodec` 403; `CborEncodingEngine` 294; `BluetoothHidTransportImpl` 479; `U2fApduCommandHandler` 117. Every resulting class is below the 500-line constitutional limit.
- Focused host checks passed for repository ownership/search/CRUD, T156 MakeCredential PRF input cleanup, CBOR and crypto, CTAP2 protocol/Windows compatibility, and Bluetooth HID transport lifecycle. The T156 test was separated into `Ctap2MakeCredentialPrfInputTest` to keep the existing validation test class under static-analysis limits.
- `tools/local-ci.ps1 -NoFix` passed in 141.96s. This ran project-wide ktlint, Detekt, release lint, comprehensive compile, unit tests, and all configured Android host suites. The explicit coverage command `[Environment]::SetEnvironmentVariable('ORG_GRADLE_PROJECT_android.testInstrumentationRunnerArguments.notClass','com.chimali.fido2.data.crypto.CredentialPersistenceCeremonyTest','Process'); .\gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkHostCoverage hdkCoverageVerify --console=plain` passed in 175s. The environment property applies the established Android 10 exclusion for `CredentialPersistenceCeremonyTest`; an unfiltered attempt reached that test and failed because no independent non-BC Ed25519 verifier is available on the Mi A2 Android 10 image. With that exact class excluded, connected app tests passed 11/11 and FIDO2 device tests 23/23. The coverage verifier reported security 78.3605% statement / 60.5405% branch (HDK scope 85.5115% / 64.2857%), domain 58.3997% / 49.1039%, FIDO2 56.3984% / 51.9417%, database 66.1238% statements, common 34.5869%, app 36.6733%, and repository 50.2358% / 49.9676%; all configured floors passed. Coverage reports are written under each module's `build/reports/hdk-coverage/`, with the repository aggregate at `build/reports/hdk-coverage/repository.xml`; connected coverage HTML is under `app/build/reports/coverage/androidTest/shrunkDebug/connected/`. Connected XML: `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi A2 - 10.xml` and `app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-Mi A2 - 10.xml`.
- T084/T085 remain user-deferred; SC-007 remains partial. This verification does not expand T128 authorization, imply clean-install upgrade support, or establish an independent current-source security disposition, production activation, or deployment.

## T159-T163 — HID request ownership, summary identity, contracts and final verification — 2026-10-18

**T159 — bounded receive/reassembly ownership.** HID partial-message storage now uses bounded mutable buffers with a bounded pending-message count and a five-second expiry. Replaced, malformed, completed, expired, and reset fragments are erased; receive-loop-owned report copies are erased after parsing. The Android callback boundary copies borrowed platform report arrays into owned bounded-queue entries and erases rejected, drained, or undelivered entries. Completed CBOR payload ownership transfers to the per-CID request owner and is erased after handler completion, including lazy cancellation, channel-busy rejection, handler failure, transport disconnect, and session teardown. APDU extraction copies have terminal cleanup. The receiver polls the bounded expiry interval without unbounded reassembly lifetime. Existing 62-byte HID framing, response FIFO, command admission, active-handler lifetime, and cancellation semantics remain covered by regressions.

Retained-buffer regressions cover single/fragmented completion, replacement, malformed sequence, parser reset/timeout, queue rejection/drain/cancellation, active request lifetime, channel-busy rejection, cancellation, successor admission, handler failure, APDU extraction and disconnect. The standalone affected FIDO2 host selection passed 130 tests with zero failures/errors/skips. The actual connected `BluetoothHidTransportDisconnectDeviceTest` passed 1/1 on Xiaomi Mi A2 Android 10 as part of the FIDO2 device suite.

**T160 — constant-time summary identity.** `CredentialRepositoryReadOperations` now validates the duplicated persisted identity through `matchesCredentialId`, the approved byte-comparison helper, rather than `CredentialId` model equality. Summary-path regressions reject first-byte, last-byte, different-length, and malformed identifiers with the fixed typed result; they also verify prior partial summary and consumed source-row cleanup and no public-key derivation. Equivalent padded and unpadded encodings continue to match. No timing assertion or model equality/hash behavior change was introduced.

**T161 — extracted read API contracts.** All 12 public read/query declarations on the internal collaborator now state borrowed inputs, returned/emitted owner transfer, malformed row and SQL fallback behavior, and applicable partial-emission/cancellation/error cleanup. Existing terse comments were expanded. No behavior-only test was added for documentation-only work.

**T163 — style correction.** Fixed all reported formatting issues in the new credential-summary and transport lifecycle tests, including continuation indentation, decoder chaining, a multiline initializer/assertion, and declaration spacing. `:feature:fido2:ktlintCheck` passed. The repository-wide Ktlint and Detekt gates passed in the final pipeline.

**Final integrated verification.** The command `[Environment]::SetEnvironmentVariable('ORG_GRADLE_PROJECT_android.testInstrumentationRunnerArguments.notClass','com.chimali.fido2.data.crypto.CredentialPersistenceCeremonyTest','Process'); .\tools\local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` passed on 2026-10-18 in 272.62 seconds. It passed Ktlint, Detekt, Release lint, compile-all (including production, host-test and device-test source sets), unit tests, all configured KMP Android host suites, connected tests and `hdkCoverageVerify`. The FIDO2 host suite passed 825 tests. Connected Xiaomi Mi A2 Android 10 results were FIDO2 23/23 and app 11/11, all zero failures/errors/skips; FIDO2 included `BluetoothHidTransportDisconnectDeviceTest` 1/1. XML reports: `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi A2 - 10.xml` and `app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-Mi A2 - 10.xml`.

The process-scoped exclusion is exactly `com.chimali.fido2.data.crypto.CredentialPersistenceCeremonyTest`. An unfiltered attempt failed only `persistentCeremoniesRecoverForEverySupportedSignatureAlgorithm`, because Android 10 lacks the independent non-BC Ed25519 verifier required by the test oracle; the other 24 FIDO2 device tests passed in that attempt. With the exact exclusion, the configured full gate passed. This platform limitation is not a product failure or a claim that the excluded test passed.

Coverage floors all passed: security 78.3605% statements/60.5405% branches (HDK scope 85.5115%/64.2857%); domain 58.3997%/49.1039%; FIDO2 56.7437%/52.3045%; database 66.1238% statements; common 34.5869%; app 36.6733%; repository 50.4807% statements/50.2196% branches. SQLDelight-generated classes are excluded from authored-source denominators and exercised by database integration tests. Module reports are under each module's `build/reports/hdk-coverage/` (for example, `feature/fido2/build/reports/hdk-coverage/hdkModuleCoverage.xml`); the repository aggregate is `build/reports/hdk-coverage/repository.xml`. Connected app coverage HTML is under `app/build/reports/coverage/androidTest/shrunkDebug/connected/`.

**Reconciled current inventory and scope.** There are 162 defined task rows: 160 checked and only user-deferred T084/T085 unchecked. T127 remediation remains evidenced without a task definition. T128 ACCEPT and activation authorization remain limited to its exact reviewed 235-entry fingerprint and T130-T136 scope; T159-T163 are outside that authorization. The implementation/evidence update does not create a current-source independent disposition or authorize activation/deployment. Clean-install-only scope, ASUS reference-device waiver, partial SC-007 and the T084/T085 performance deferrals remain unchanged.

## T164-T169 corrective implementation and final verification — 2026-10-18

**T164 — outgoing response and driver-copy ownership.** The HID wrapper erases its application-owned fixed-size copy in `sendReport`'s `finally`. The transport takes ownership of response packet lists as soon as the handler result is created, before any cancellation point; the generation-bound sender copies and erases caller packets, settles response handoff waiters on acceptance/rejection/failure/cancellation, and erases sender-owned packets after the driver call or queue disposal. Retained-reference tests cover success, driver rejection/exception, CTAPHID cancellation while queued, terminal request-input cleanup, and zeroing after handoff. Dependency/platform-internal copies remain outside the application's erasure guarantee.

**T165 — connected-host generation fence.** Incoming report envelopes retain connected-device identity and session generation. The wrapper rejects callbacks from a noncurrent host/generation before enqueueing, terminal transitions fence the session immediately, and transport admission checks generation and allocated CID before wallet ownership. Handler results from an earlier generation are discarded before response handoff. Host regressions exercise stale generation, invalid/unallocated CIDs, stale results and valid successor processing; `BluetoothHidTransportDisconnectDeviceTest` verifies disconnect cancellation on Android.

**T166 — report framing bounds.** The current 62-byte descriptor permits 55 initial bytes plus 128 continuation reports of 57 bytes each: 7,351 bytes maximum. The parser validates report size and bounded message length; the encoder rejects payloads above representable capacity instead of wrapping sequence numbers. Tests cover exact capacity, capacity plus one, legacy 7,609-byte size, sequence 127, malformed callback lengths and valid multi-report reassembly.

**T167 — bounded channels and active requests.** Session CID allocation and total concurrent request ownership have explicit finite limits, bounded random-candidate attempts and contained busy/exhaustion outcomes. Terminal request/session paths release admission capacity and retained payloads. Deterministic tests cover limit/limit-plus-one, repeated CID collision exhaustion, cleanup and capacity recovery.

**T169 — sender replacement race.** `HidReportSender` gives each generation a private bounded queue and serializes HID driver access through a shared mutex. Cancellation and cleanup can only dispose that generation's queue. A deterministic regression proves the predecessor handoff waiter settles false and its in-flight report buffer is erased on cancellation, then proves the successor response reaches the driver. `BluetoothHidTransportLifecycleTest` separately covers predecessor cleanup racing a successor response.

**Focused and integrated verification.** The affected-module Ktlint, Detekt and focused host lifecycle/sender tests passed. The selected `HidReportSenderTest` and `BluetoothHidTransportLifecycleTest` suites passed 1/1 and 60/60. The full FIDO2 host suite passed 837/837 before the additional sender-waiter regression. After that regression, the configured coverage command passed with **838 FIDO2 host tests**, zero failures/errors/skips. `:feature:fido2:compileAndroidDeviceTest` passed with the production sender. The integrated command `[Environment]::SetEnvironmentVariable('ORG_GRADLE_PROJECT_android.testInstrumentationRunnerArguments.notClass','com.chimali.fido2.data.crypto.CredentialPersistenceCeremonyTest','Process'); .\tools\local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` passed in 447.25 seconds after the production corrections, including Ktlint, Detekt, Release lint, compile-all, unit tests, KMP host tests, connected tests and coverage.

After the T169 test was added, `[Environment]::SetEnvironmentVariable('ORG_GRADLE_PROJECT_android.testInstrumentationRunnerArguments.notClass','com.chimali.fido2.data.crypto.CredentialPersistenceCeremonyTest','Process'); .\gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkHostCoverage hdkCoverageVerify --console=plain` passed in 306 seconds. It reran repository host and connected coverage tasks: Mi A2 Android 10 FIDO2 23/23 and app 11/11, zero failures/errors/skips. The Android 10 ceremony exclusion is required because the platform lacks the independent non-BC Ed25519 verifier used by that test oracle. Coverage: security 78.3605% statements/60.5405% branches (HDK scope 85.5115%/64.2857%); domain 58.3997%/49.1039%; FIDO2 57.2195%/52.4815% (838 tests); database 66.1238% statements; common 34.5869%; app 36.6733%; repository 50.8501% statements/50.3695% branches. All configured floors passed.

**Current report locations.** `tools/hdk-coverage.init.gradle` writes module XML, HTML and CSV under each module's `build/reports/hdk-coverage/`; examples are `feature/fido2/build/reports/hdk-coverage/hdkModuleCoverage.xml` and `core/security/build/reports/hdk-coverage/hdkScopedCoverage.xml`. The repository aggregate is `build/reports/hdk-coverage/repository.xml`, with sibling `repository/` HTML and `repository.csv`. Connected XML reports are `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi A2 - 10.xml` and `app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-Mi A2 - 10.xml`; connected app coverage HTML is under `app/build/reports/coverage/androidTest/shrunkDebug/connected/`. Older path references to `build/reports/coverage/hdk/` have been corrected to the configured paths.

**Current limits.** Inventory is 166/168 checked, with only T084/T085 user-deferred and SC-007 partial. T127 still has no task definition. T164-T169 are outside T128's exact reviewed manifest/T130-T136 authorization; no current-source independent disposition, activation, deployment, numerical performance compliance, ASUS hardware validation, or device-class equivalence is claimed. The user's ASUS reference-device waiver, Mi A2 verifier limitation, clean-install-only scope and reviewer-owned artifact boundaries remain unchanged.

## T170-T177 current-source correction and verification — 2026-10-18

**Lifecycle fixture and sender snapshot coverage (T174/T175).** The `BluetoothHidTransportLifecycleTest` fixture now defaults mock sessions to Advertising and its MakeCredential handler dispatches a synthetic `RegistrationRequested` event to the registration presenter. Constructor behavior is explicitly exercised with Idle, Advertising, and Connected snapshots. Registration termination tests now reach consent/verification through the real event bus. No production sender state is manually repaired in these tests. The lifecycle cases were split into `BluetoothHidTransportLifecycleTest` and `BluetoothHidTransportRequestLifecycleTest`, with reusable fixture support, so the configured Detekt `LargeClass` rule passes without suppression.

**Predecessor teardown and session admission (T171/T176).** `BluetoothHidTransportSessionGenerationTest.delayed predecessor teardown preserves successor parser channel and control traffic` holds old-generation cleanup while a real wrapper disconnects/reconnects. The regression checks that successor INIT allocates a new CID, stale predecessor PING is rejected without echo, successor PING is echoed, continuation fragments reach the successor handler, predecessor-owned input is erased when delayed cleanup is released, and that cleanup leaves successor-owned input live until its own disconnect. The production dispatch boundary validates all non-INIT CIDs against the active host generation. The existing real-wrapper callback regression also covers sender rebasing, old-generation rejection, error termination and successor multi-report output. T172 response replacement/owner-transfer retained-reference cases and the expired queued-owner case ran in the focused request lifecycle suite.

The request lifecycle tests that call `dispatchMessage` directly remain synthetic parser/owner checks. The two reconnect regressions exercise the actual `BluetoothHidDeviceWrapper` callback-to-incoming-report receiver-to-parser-to-transport path, with the HID driver represented by the test's recording mock; they do not claim physical Bluetooth host interoperability.

**Focused verification.** Command: `./gradlew.bat :feature:fido2:ktlintCheck :feature:fido2:detekt :feature:fido2:testAndroidHostTest --tests com.chimali.fido2.data.transport.BluetoothHidTransportLifecycleTest --tests com.chimali.fido2.data.transport.BluetoothHidTransportRequestLifecycleTest --tests com.chimali.fido2.data.transport.BluetoothHidTransportSessionGenerationTest --tests com.chimali.fido2.data.transport.CtapHidRequestOwnerTest --tests com.chimali.fido2.data.transport.HidReportSenderTest --no-configuration-cache --console=plain`. Ktlint and Detekt passed; all 73 selected tests passed with zero failures/errors/skips: lifecycle 15, request lifecycle 53, session generation 1, request owner 3, sender 1. Fresh XML files are under `feature/fido2/build/test-results/testAndroidHostTest/` with the matching class names.

**Integrated verification.** The first unfiltered local-CI coverage attempt ran the Mi A2 Android 10 device suites and failed only `CredentialPersistenceCeremonyTest.persistentCeremoniesRecoverForEverySupportedSignatureAlgorithm`; the independent non-BC Ed25519 verifier is unavailable on that platform, as recorded in the earlier device limitation. The app suite passed 11/11. The successful rerun used the quickstart's documented process-scoped Gradle filter:

```powershell
[Environment]::SetEnvironmentVariable('ORG_GRADLE_PROJECT_android.testInstrumentationRunnerArguments.notClass','com.chimali.fido2.data.crypto.CredentialPersistenceCeremonyTest','Process')
.\tools\local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage
```

The complete local-CI pipeline passed in 377.96 seconds: Ktlint, Detekt, Release lint, compile-all (including Android production, host and device sources), unit tests, KMP Android host tests, connected coverage, and configured coverage verification. The full FIDO2 Android host suite reported 849 tests with zero failures/errors/skips. Connected Mi A2 Android 10 XML reported FIDO2 23/23 and app 11/11, both with zero failures/errors/skips; the excluded ceremony class is not counted as a pass. Coverage floors all passed: security 78.3605% statements/60.5405% branches; domain 58.3997%/49.1039%; FIDO2 58.4787%/53.9037%; database 66.1238% statements (52.7778% branches reported); common 34.5869% (31.0345% branches reported); app 36.6733% (30.5263% branches reported); repository aggregate 51.7087% statements/51.3745% branches. Scoped security HDK coverage was 85.5115% statements/64.2857% branches.

**Report locations.** FIDO2 connected XML: `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi A2 - 10.xml`; app connected XML: `app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-Mi A2 - 10.xml`; app connected coverage HTML: `app/build/reports/coverage/androidTest/shrunkDebug/connected/index.html`. Module and repository XML/HTML/CSV are under each module's `build/reports/hdk-coverage/` and `build/reports/hdk-coverage/repository.xml` respectively.

**Reconciled task and assurance scope.** T170-T177 are complete against the implementation and runs above. There are 176 task rows, 174 checked and two explicitly user-deferred (T084/T085). T127 remains evidenced without a task row. T128's ACCEPT and activation authorization remains limited to its exact fingerprint and T130-T136; T140-T177 remain outside that review scope. The ASUS reference-device waiver, clean-install scope, Android 10 verifier exclusion, reviewer-owned artifact bytes, and partial SC-007 remain. No current-source independent security disposition, production activation/deployment, device-class equivalence, or numerical performance compliance is claimed.

## T178-T180 corrective implementation and T181 verification checkpoint — 2026-10-18

**T178 wallet owner cancellation.** `WalletMasterSeedProvider.withSessionOwner` now admits the owner under `initializationMutex`, then runs bootstrap and the operation inside `withContext(owner.job + OwnerContext(ownerId))`. Bootstrap checks that the same owner remains registered, belongs to the current generation and is active before publishing metadata/root caches. The mutex still serializes initialization and replacement; `finally` still cancels the owner and releases it under `NonCancellable`. The provider does not claim interruption of synchronous crypto already executing.

`WalletMasterSeedProviderTest` now holds the first DataStore read and first update in a cancellable suspension. Both `clearSession()` and foreground `cancelSessionOwners()` are exercised without cancelling the parent request or manually releasing the store. Each case proves the request terminates, does not enter its operation, leaves no cached PRK/parent or active owner, and allows the next authorized request to reconstruct. A separate synchronous derivation is held across foreground cancellation; on release the owner cannot publish, and retained mnemonic/derived-seed buffers are verified zeroed. The provider suite reports 14 tests, 0 failures, 0 errors and 0 skipped in `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.crypto.WalletMasterSeedProviderTest.xml`.

**T179 transport size and extraction.** `BluetoothHidTransportImpl` delegates command dispatch and GetAssertion/GetInfo response creation to same-module `CtapHidCommandDispatcher`, and bounded generation-bound CID ownership to `HidChannelRegistry`. Existing wrapper callback reconciliation, transport session lock ordering, sender serialization, admission, parser ownership, deadlines, keepalive shutdown, cancellation and packet erasure remain in place. Counting nonblank Kotlin code lines after removing block comments, line comments and blank lines gives: transport 500; command dispatcher 121; CID registry 68. The strict transport maximum is met; the helpers remain below it. Focused transport lifecycle, request lifecycle, session-generation and request-owner tests passed; the full host suite subsequently covered the sender suite as well.

**T180 traceability.** The retained transport sender/CID/session test seams and the extracted dispatch/registry APIs carry direct `NFR-SEC-041` and `TR-AUTH-031` annotations and declaration KDoc. The affected wrapper generation/report APIs document requirement links and borrowed/owned buffer behavior. The constructor snapshot, stale/unallocated CID and bounded allocation tests carry requirement references; the fixture support functions have direct contracts. Android main and Android host-test Ktlint checks passed.

**Integrated verification.** The configured command `.\tools\local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` passed Ktlint and stopped at Detekt. The first Detekt run identified unused transport constants after extraction; those constants were removed. The remaining Detekt result is one `LargeClass` finding at `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/bluetooth/BluetoothHidDeviceWrapper.kt:141`. This wrapper was already present in the incoming staged source; the finding remains unresolved and no suppression or threshold change was made. Therefore T181 remains active pending that repository static-analysis gate.

The compile/test/coverage portion was run with `.\tools\local-ci.ps1 -SkipLint -NoFix -NoConfigurationCache -RunCoverage` and passed in 257.72 seconds. It compiled Android production, host and device sources; passed unit tests and all KMP Android host tests; ran connected Mi A2 Android 10 suites; and passed `hdkCoverageVerify`. The Android 10 independent-Ed25519 verifier class was excluded with the established process-scoped Gradle filter; it is not counted as a pass. The full feature host result was 851 tests, 0 failures/errors/skips across 120 XML files. The final additional provider run after adding retained-buffer assertions passed 14/14. Connected results: FIDO2 23/23 and app 11/11, both with 0 failures/errors/skips. During the connected execution, Android's test result listener logged an `ArrayIndexOutOfBoundsException` while reporting `CredentialProcessRestartTest.recoverAndSignAfterOperatingSystemProcessRestart`; Gradle returned success and both result XML roots report zero failed, errored or skipped tests.

`.\gradlew lintRelease --no-configuration-cache` passed. `.\gradlew :feature:fido2:ktlintAndroidMainSourceSetCheck :feature:fido2:ktlintAndroidHostTestSourceSetCheck --no-configuration-cache` passed after the changes. `.\gradlew detekt --no-configuration-cache` remains blocked only by the wrapper `LargeClass` finding above.

Coverage verification passed all floors. Reports: security 78.3605% statements / 60.5405% branches; domain 58.3997% / 49.1039%; FIDO2 58.6169% / 53.9330%; database 66.1238% statements (52.7778% branches reported); common 34.5869% (31.0345% branches reported); app 36.6733% (30.5263% branches reported); repository 51.8147% statements / 51.3980% branches. Scoped security HDK coverage was 85.5115% / 64.2857%.

Host XML is under `feature/fido2/build/test-results/testAndroidHostTest/`; connected device XML is `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi A2 - 10.xml` and `app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-Mi A2 - 10.xml`. Module coverage reports are under each module's `build/reports/hdk-coverage/`; repository coverage is `build/reports/hdk-coverage/repository.xml`; connected app HTML is `app/build/reports/coverage/androidTest/shrunkDebug/connected/index.html`.

**Current task and assurance scope.** T178-T180 are complete. T181 is still active because the Detekt LargeClass gate remains open; do not treat its executed verification checkpoint as an all-green repository static result. Current inventory: 180 task rows, 177 checked, T181 active, and T084/T085 user-deferred. T127 remains evidenced without a task row. T128 authorization still covers only its exact reviewed manifest and T130-T136 scope; this checkpoint does not claim current-source independent security acceptance, production activation/deployment, ASUS/reference-class equivalence, full SC-007 compliance or numerical performance compliance. User-deferred performance work remains deferred.

## T181 completion — 2026-10-18

**Focused remediation.** To clear the wrapper's reproducible Detekt `LargeClass` finding without suppression or a threshold change, the Android broadcast receiver, HID profile listener, platform callbacks, diagnostic logging, proactive bonded-host handling and registration callback were moved into `BluetoothHidCallbackCoordinator`. The wrapper delegates these existing lifecycle responsibilities through `BluetoothHidCallbackOwner` and keeps a concrete `hidCallback` field for the current reflection-based lifecycle fixture. The bounded Bluetooth `STATE_TURNING_ON` wait was moved into `BluetoothAdapterStateWaiter`. Session generation, lock ordering, pairing, device filtering, report-copy ownership, queue behavior, timeouts and callback result handling remain in the same logic paths. The coordinator bridge, registration and lifecycle entry points, and adapter waiter carry direct `NFR-SEC-041` / `TR-AUTH-031` links and declaration KDoc.

**Class-size measurement.** Counted nonblank Kotlin lines within each named class declaration, excluding blank lines and comment-only lines (`//`, `/* ... */`); package and import lines are outside the counted class range. The final measurements are `BluetoothHidDeviceWrapper`: 497 lines; `BluetoothHidCallbackCoordinator`: 484 lines; `BluetoothAdapterStateWaiter`: 30 lines. The wrapper and coordinator are both within the constitution's 500-code-line maximum. No rule threshold or suppression changed.

**Focused regression results.** The first run after moving callbacks surfaced two `NoSuchFieldException: hidCallback` failures because the existing lifecycle fixture intentionally reaches the production callback by its wrapper field name. Retaining the callback field on the wrapper restored that test seam. The final focused run then passed 36/36: `WalletMasterSeedProviderTest` 14/14, `BluetoothHidDeviceWrapperIncomingReportTest` 6/6, `BluetoothHidTransportLifecycleTest` 15/15, and `BluetoothHidTransportSessionGenerationTest` 1/1. The named XML files are in `feature/fido2/build/test-results/testAndroidHostTest/`.

**Final integrated verification.** After the adapter waiter extraction, `.\tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` passed in 674.16 seconds. Ktlint, release lint, compile-all, unit tests, all configured host tests, connected suites and all configured coverage floors passed. The FIDO2 host suite recorded 851 tests. Connected Mi A2 Android 10 results are FIDO2 23/23 and app 11/11, each with 0 failures, 0 errors and 0 skipped, in `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi A2 - 10.xml` and `app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-Mi A2 - 10.xml`. The configured process exclusion remained `com.chimali.fido2.data.crypto.CredentialPersistenceCeremonyTest`, because the Mi A2 Android 10 platform lacks the independent non-BC Ed25519 verifier needed by that ceremony. The final coverage gate reported: security 78.3605% statements / 60.5405% branches; domain 58.3997% / 49.1039%; FIDO2 58.6496% / 53.9526%; database 66.1238% statements (52.7778% branches reported); common 34.5869% (31.0345% branches reported); app 36.6733% (30.5263% branches reported); repository 51.8464% statements / 51.4109% branches; scoped security HDK 85.5115% / 64.2857%. Reports remain under `build/reports/hdk-coverage/`, with repository data at `build/reports/hdk-coverage/repository.xml`.

After adding direct requirement annotations and KDoc to the new bridge contract, the final Android main Ktlint and Detekt checks plus production and device-test compilation passed again. No application behavior changed in that traceability-only edit.

**Reconciled status and limits.** T181 is complete. Current inventory: 180 defined task rows, 178 checked, 0 active and 2 user-deferred (T084/T085). Earlier dated status checkpoints are retained. T127 still has evidence but no task row. The Android 10 verifier exclusion and ASUS reference-device waiver remain. T128's independent disposition and activation authorization remain limited to its exact reviewed 235-entry manifest and T130-T136; the T178-T181 changes do not inherit or extend that authorization. SC-007 remains partial, and this work establishes no current-source independent security acceptance, production activation/deployment, ASUS device-class equivalence or numerical performance compliance.

## T182 declaration documentation — 2026-10-18

Added KDoc and direct requirement links for `BluetoothHidCallbackCoordinator.initContinuation` and `FIDO_HID_REPORT_SIZE`. The continuation contract describes wrapper ownership, retry replacement, profile-callback completion, immediate failure cleanup, and timeout/cancellation behavior as implemented. The report-size contract ties the 62-byte payload to the descriptor report counts and `HID_PACKET_SIZE` used by the parser. No runtime behavior changed.

Validation passed with `.\gradlew :feature:fido2:ktlintAndroidMainSourceSetCheck :feature:fido2:detekt :feature:fido2:compileAndroidMain :feature:fido2:compileAndroidDeviceTest --no-configuration-cache` (26 seconds; 51 actionable tasks, 10 executed and 41 up-to-date). This confirms Android main style/static checks and production/device-test compilation for the affected source. No behavioral tests were added or run for this documentation-only task.

Current inventory is 181 defined task rows, 179 checked, and two user-deferred tasks (T084/T085); no active task remains. The existing T128 review/activation scope, ASUS waiver, Android 10 verifier exclusion, clean-install scope and partial SC-007 remain unchanged. This update does not claim current-source independent security acceptance, activation/deployment, device-class equivalence or numerical performance compliance.

## T183 declaration documentation — 2026-10-18

Added declaration KDoc to all 34 previously undocumented public/internal constants in `HkdfSha256`, `P256Group`, `CredentialKeyDescriptor`, `CredentialId` and `HidReportParser`. The descriptions state lengths/units, supported algorithm/profile pairings, fixed HKDF domain-separation labels, SEC1 point prefix, CTAP HID framing offsets/capacities and command values. Added the missing cleanup contract for `HdkChild.eraseFactor`, content-equality and ordinary hash semantics for the explicit equality/hash implementations, the current diagnostic behavior of `CredentialId.toString`, message-field/equality/hash contracts for `CtapHidMessage`, and outcome meanings for both mnemonic import result objects. Existing requirement annotations and implementation values are unchanged; no runtime behavior or API shape changed.

Scoped verification passed with:

```powershell
.\gradlew :core:security:ktlintAndroidMainSourceSetCheck :core:security:detekt :core:security:compileAndroidMain :core:security:compileAndroidHostTest :core:domain:ktlintCommonMainSourceSetCheck :core:domain:detekt :core:domain:compileAndroidMain :core:domain:compileAndroidHostTest :feature:fido2:ktlintAndroidMainSourceSetCheck :feature:fido2:detekt :feature:fido2:compileAndroidMain :feature:fido2:compileAndroidHostTest :feature:fido2:compileAndroidDeviceTest --no-configuration-cache
```

The command completed successfully in 14 seconds: 64 actionable tasks, 12 executed and 52 up-to-date. Ktlint and Detekt passed for the scoped source sets; production, host-test and device-test compilation passed for the affected modules. The domain compilation reported an unrelated existing redundant-conversion warning in `VaultState.kt:55`. No behavioral tests or performance campaign were run because this is documentation-only. Current inventory is 182 task rows, 180 checked, no active task and two user-deferred tasks (T084/T085). T128's review/activation authorization remains bounded to its exact reviewed manifest and T130-T136; this documentation work does not establish current-source independent security acceptance, activation/deployment, device-class equivalence or numerical SC-007 compliance. The ASUS waiver, Android 10 verifier exclusion, clean-install scope and partial SC-007 remain unchanged.


## T184/T185 — Declaration traceability and KDoc completion

Completed 2026-10-18. T184 attached direct `@Req` metadata to the scoped exposed HID parser/wrapper/coordinator APIs, Bluetooth transport/service and registration prompt/viewmodel APIs, retained performance instrumentation APIs, PRF input/output APIs, the feature credential repository contract and implementation, wallet/security instrumentation, passkey command/event/state/decider declarations, credential-key descriptors, and DI factories. Matching direct requirement references were added to the existing directly verifying test methods in the targeted HID, transport, wallet, PRF, passkey-decider, internal-timing and registration-prompt test files. A scoped annotation audit counted 95 `@Test` declarations in those files and found no missing adjacent `@Req`. The eleven distinct IDs in edited Kotlin declarations resolve in `docs/brd.md` or `docs/trd.md`: FR-AUTH-010, FR-AUTH-030, FR-HID-010, FR-HID-020, FR-HID-030, FR-VAL-020, FR-VAL-030, NFR-PERF-030, NFR-SEC-010, NFR-SEC-041 and TR-AUTH-031. The obsolete FR-013 test reference in the PRF derivation tests was replaced with current requirement IDs.

T185 added declaration-attached KDoc to the audited wallet seed/session methods, erasable event-buffer operations, passkey commands/events/state and decider, event/snapshot/credential/relying-party repository overrides, HID parser and transport declarations, registration prompt/viewmodel contracts, PRF model and credential-key descriptor properties, DI factories, initializer, and security helpers. The comments describe relevant value/units, ownership and aliasing, transfer/erasure lifetime, cancellation, result and diagnostic behavior. The annotations and comments do not change runtime behavior, API or wire shape, derivation bytes, or performance behavior.

Verification command:

```powershell
.\gradlew.bat :core:security:ktlintAndroidMainSourceSetCheck :core:security:detekt :core:security:compileAndroidMain :core:security:compileAndroidHostTest :core:domain:ktlintCommonMainSourceSetCheck :core:domain:detekt :core:domain:compileAndroidMain :core:domain:compileAndroidHostTest :feature:fido2:ktlintAndroidMainSourceSetCheck :feature:fido2:detekt :feature:fido2:compileAndroidMain :feature:fido2:compileAndroidHostTest :feature:fido2:compileAndroidDeviceTest --no-configuration-cache
```

Result: `BUILD SUCCESSFUL` in 10 seconds; 64 actionable tasks, 6 executed and 58 up-to-date. Ktlint, Detekt, Android production compilation, host-test compilation and device-test compilation passed for the affected modules. The compile emitted existing coroutine opt-in warnings in `BluetoothHidTransportLifecycleTest.kt`; no errors occurred. No behavioral tests, coverage run, profiling, or device ceremony were run for these annotation/documentation-only tasks. The pre-existing staged workspace was preserved; no files were staged or committed by this work.


## T186-T189 — class size, Tier 1 references, property contracts, and current evidence — 2026-10-18

**T186 — wrapper size and report dispatch.** The wrapper now delegates fixed-size report formatting, synchronous HID dispatch, platform failure handling, and erasure of the temporary report copy to the cohesive same-module `BluetoothHidReportSender`. The wrapper retains its session lock and generation check before calling the sender. The existing outgoing-report tests still verify copy erasure on success, rejection, and driver failure; incoming queue ownership and callback behavior are unchanged. Using the class-bound procedure below, the current counts are `BluetoothHidDeviceWrapper` 479, `BluetoothHidCallbackCoordinator` 485, and `BluetoothHidTransportImpl` 500 nonblank, noncomment Kotlin lines. Each is within the 500-line ceiling.

To reproduce the class count: locate the requested `class Name` declaration, scan from its opening brace through the matching class-closing brace while balancing braces, remove block/KDoc and line comments, then count nonblank lines in that range. Exclude package/import lines, which precede the declaration. The same procedure is used for all three counts; it does not suppress or change the configured threshold.

**T187 — bounded test-reference audit.** The exact audit covered 14 existing files: `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/bluetooth/HidReportParserTest.kt`, `BluetoothHidDeviceWrapperIncomingReportTest.kt`, `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/transport/HidReportSenderTest.kt`, `CtapHidRequestOwnerTest.kt`, `BluetoothHidTransportLifecycleTest.kt`, `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProviderTest.kt`, `PrfKeyDerivationTest.kt`, `PrfKeyDerivationZeroingTest.kt`, `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/domain/model/PrfExtensionInputTest.kt`, `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/util/performance/InternalOperationTimingTest.kt`, `LatencyProfilerUserWindowTest.kt`, `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/ctap2/Ctap2GetAssertionHandlerPrfTest.kt`, `core/domain/src/commonTest/kotlin/com/chimali/core/domain/eventsourcing/passkey/PasskeyDeciderTest.kt`, and `feature/fido2/src/androidTest/kotlin/com/chimali/fido2/presentation/ui/RegistrationPromptScreenTest.kt`.

The reproducible check is: in those files, find each contiguous annotation group directly preceding a test declaration; retain only groups containing both `@Test` and `@Req`; collect every ID from that group's `@Req`; inspect the nearest KDoc attached before the annotation group; require an `@see` for each collected ID. Helper functions and class-level references are excluded. Result: 103 directly annotated test declarations across the 14 files, 0 missing `@see` references. The ten referenced IDs resolve in the current BRD/TRD: FR-AUTH-010, FR-HID-010, FR-HID-020, FR-HID-030, FR-VAL-020, FR-VAL-030, NFR-PERF-030, NFR-SEC-010, NFR-SEC-041, and TR-AUTH-031. Source-set discovery is recorded separately from the audit: `core:domain` exposes `commonTest` through its Android host-test compile, and FIDO2 configures `withHostTest` and `withDeviceTest`. Twelve FIDO2 files in this audit are under `src/androidHostTest`; the passkey-decider test is under `core/domain/src/commonTest`; the registration-prompt test is under the legacy `feature/fido2/src/androidTest` path. The latter was audited for reference attachment but is not claimed as compiled by the KMP `compileAndroidHostTest`/`compileAndroidDeviceTest` gates in this run.

**T188 — eight remaining property contracts.** Added constructor `@property` KDoc for `HdkP256.Result.childPublicKey` (owned 65-byte uncompressed SEC1 P-256 point), `factor` (owned 32-byte nonzero factor, caller erases it), `childPrivateScalar` (owned 32-byte big-endian scalar, caller erases it); `P256ParentKeyDerivation.Result.privateScalar` (owned 32-byte big-endian scalar, caller erases it), `publicKey` (owned 65-byte uncompressed SEC1 point), and `candidateCounter` (accepted inclusive range 0..255). Added KDoc to `BluetoothHidTransportImpl.nextCidBytes` describing the synchronously filled, reused 4-byte buffer and transfer/retention boundary, and to `monotonicNanos` describing monotonic nanosecond elapsed-time units rather than wall-clock time. The bounded inventory covers the named current result models and transport helper only; generated properties, local variables, and private nested type members are excluded.

**Verification in this turn.** The final configured command passed in 14 seconds (64 actionable tasks, 11 executed and 53 up-to-date):

```powershell
.\gradlew.bat :core:security:ktlintAndroidMainSourceSetCheck :core:security:detekt :core:security:compileAndroidMain :core:security:compileAndroidHostTest :core:domain:ktlintCommonMainSourceSetCheck :core:domain:detekt :core:domain:compileAndroidMain :core:domain:compileAndroidHostTest :feature:fido2:ktlintAndroidMainSourceSetCheck :feature:fido2:detekt :feature:fido2:compileAndroidMain :feature:fido2:compileAndroidHostTest :feature:fido2:compileAndroidDeviceTest --no-configuration-cache
```

The focused regressions passed with 0 failures/errors/skips: `BluetoothHidDeviceWrapperIncomingReportTest` 6/6, `BluetoothHidTransportLifecycleTest` 15/15, and `HidReportSenderTest` 1/1 (22/22 total). The exact command was:

```powershell
.\gradlew.bat :feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.bluetooth.BluetoothHidDeviceWrapperIncomingReportTest' --tests 'com.chimali.fido2.data.transport.BluetoothHidTransportLifecycleTest' --tests 'com.chimali.fido2.data.transport.HidReportSenderTest' --no-configuration-cache
```

Compilation reported an existing unnecessary safe-call warning in `PrfKeyDerivationZeroingTest.kt`; Gradle also reported the existing debuggable/minified `shrunkDebug` warning and Gradle deprecation notices. These did not fail the configured gates. This turn did not run the full local-CI/coverage pipeline, a device ceremony, or performance capture. The T181 674.16-second integrated run and its coverage values remain the latest full checkpoint; the older 447.25-second/838-test record remains historical.

**Reconciled inventory and limits.** There are 188 defined task rows: 186 checked and only T084/T085 user-deferred. T127 remains evidenced without an invented task row. The ASUS waiver, Android 10 independent-Ed25519-verifier exclusion, clean-install scope, partial SC-007 status, and exact T128 reviewed-manifest/T130-T136 authorization boundary remain in force. This work does not create a new security disposition, authorize activation/deployment, claim device-class equivalence, or claim numerical performance compliance.


**Final test-source checks after consolidating the attached KDoc blocks.** The configured test-source Ktlint gates also passed in 7 seconds (53 actionable tasks, 6 executed and 47 up-to-date): `:feature:fido2:ktlintAndroidHostTestSourceSetCheck`, `:feature:fido2:ktlintCommonTestSourceSetCheck`, `:feature:fido2:ktlintAndroidDeviceTestSourceSetCheck`, and `:core:domain:ktlintCommonTestSourceSetCheck`. The final host/device-test compilation and focused regression rerun passed in 9 seconds (64 actionable tasks, 8 executed and 56 up-to-date): 22/22 selected HID ownership, lifecycle and sender tests. The host compile retained existing `ExperimentalCoroutinesApi` opt-in warnings in `BluetoothHidTransportLifecycleTest.kt`; no failures occurred. The final attachment audit again found 103 direct test declarations and zero missing KDoc references; task accounting is 186/188 checked with T084/T085 deferred.


## T190 — Named HID report identifier — 2026-10-18

Replaced the unnamed report identifier argument in `BluetoothHidReportSender.send` with the requirement-tagged `FIDO_HID_REPORT_ID` constant, retaining its value of `0`. Its KDoc records that the descriptor defines unnumbered FIDO reports while Android's `BluetoothHidDevice.sendReport` API still takes an identifier argument. Dispatch behavior, report bytes, generation gate, temporary-copy cleanup and failure handling are unchanged. The ASUS was connected, but this constant-only correction did not require a device ceremony.

Scoped verification passed:

```powershell
.\gradlew.bat :feature:fido2:ktlintAndroidMainSourceSetCheck :feature:fido2:detekt :feature:fido2:compileAndroidMain :feature:fido2:compileAndroidHostTest :feature:fido2:compileAndroidDeviceTest --no-configuration-cache
```

Gradle reported `BUILD SUCCESSFUL` in 13 seconds: 52 actionable tasks, 12 executed and 40 up-to-date. Android main Ktlint, FIDO2 Detekt, and production, host-test and device-test compilation passed. No behavioral tests, integrated coverage pipeline, connected-device suite or performance capture was run for this constant-only correction. Existing warnings include the debuggable/minified `shrunkDebug` build-type warning and Gradle deprecation notices; they did not fail these checks. Current accounting is 189 defined tasks, 187 checked, T084/T085 user-deferred. T190 is outside T128's fingerprinted review authorization; no current-source independent security acceptance, activation/deployment, or numerical performance compliance is claimed.

## T191 — Current HKDF and SEC1 format dimensions

Completed 2026-10-18. Bounded the numeric inventory to the two helpers named by T191. In `HkdfSha256.kt`, the BIP39 seed size is named; HKDF’s maximum block count, counter byte width and initial block counter are explicit; credential-info separator, separator width, two-byte credential-ID length prefix and aggregate framing width are named; ASCII upper bound is named while the implicit nonnegative lower bound follows from `Char.code`. Existing credential-ID bounds were already named. In `P256Group.kt`, SEC1 prefix width, x/y coordinate offsets, integer sign-padding width and encoded point length are expressed using named SEC1/scalar dimensions. The existing public constants and APIs, byte encodings, validation/failure behavior, and temporary-buffer cleanup remain unchanged.

Verification command:

```text
.\gradlew.bat :core:security:ktlintAndroidMainSourceSetCheck :core:security:detekt :core:security:compileAndroidMain :core:security:compileAndroidHostTest :feature:fido2:compileAndroidMain :feature:fido2:compileAndroidHostTest :feature:fido2:compileAndroidDeviceTest :core:security:testAndroidHostTest --tests '*HkdfSha256Test' --tests '*P256GroupTest' --tests '*HdkP256Test' --no-configuration-cache
BUILD SUCCESSFUL in 17s; 60 actionable tasks (17 executed, 43 up-to-date)
```

Configured security Ktlint/Detekt and security/FIDO2 production, host-test and device-test compilation passed. Focused existing results: `HkdfSha256Test` 5 tests, `P256GroupTest` 9 tests, `HdkP256Test` 4 tests; 18 total, zero failures/errors/skips. No test was added, and no device, coverage, independent security review, or performance campaign was run. T084/T085 remain user-deferred. T128 review/activation authorization remains bounded to its reviewed source manifest and T130-T136; this change is outside that boundary, so no current-source independent security acceptance or production activation is asserted.
## T192–T194 — Transport/response traceability, KDoc and named HID values

Completed 2026-10-18. This is a declaration/documentation and constant-naming change. No public signature, lifecycle policy, CTAP status mapping or emitted byte was intentionally changed.

### T192 direct requirement/reference inventory

The eleven exposed functions now carry declaration-attached `@Req`: `Fido2Transport` has `connect`, `connectDevice`, `disconnect`, `sendCommand` and `isConnected` (FR-HID-010 with FR-HID-020 or FR-VAL-020); `Ctap2ResponseBuilder` has `makeCredentialResponse`, `getInfoResponse`, `errorResponse`, `hidErrorResponse`, `keepAliveResponse` and `statusDescription` (FR-HID-010 or FR-HID-020 with FR-VAL-020). These IDs are defined in `docs/brd.md` and `docs/trd.md`. `Ctap2ProtocolTest` now names the directly exercised response-builder declarations and IDs in its test KDoc. The existing transport lifecycle tests that exercise connection and disconnection link their interface declarations and IDs. The three `WalletSessionMemoryPressureTest` functions now reference the annotated `handle` declaration and NFR-SEC-041/TR-AUTH-031. This inventory is declaration-level: private/local helpers and generated code are excluded, and a concrete implementation annotation is not treated as an interface annotation. There is no claim of a newly added direct behavioral test for each transport method.

Reproduce with `rg -n '@Req|fun (connect|connectDevice|disconnect|sendCommand|isConnected|makeCredentialResponse|getInfoResponse|errorResponse|hidErrorResponse|keepAliveResponse|statusDescription)' feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/transport/Fido2Transport.kt feature/fido2/src/androidMain/kotlin/com/chimali/fido2/ctap2/Ctap2ResponseBuilder.kt`, then inspect matching `@see` tags in `Ctap2ProtocolTest.kt`, `BluetoothHidTransportLifecycleTest.kt` and `WalletSessionMemoryPressureTest.kt`.

### T193 declaration documentation inventory

Declaration-attached KDoc was added to the 28 exposed declarations identified by convergence: one `WalletSessionMemoryPressure.handle`; three `MainActivity` declarations; three `ChimaliApplication` declarations; seven `Fido2Transport` declarations (interface, state and five methods); three transport limits in `BluetoothHidTransportImpl`; one `CtapHidRequestOwner` request limit; and ten internal CTAP status constants in `Ctap2ResponseBuilder`. The comments describe the applicable lifetime, ownership, result or unit contract. The response builder now correctly says CBOR, and the directly related `Ctap2ProtocolTest` wording says 62-byte HID reports. Its assertions remain intact. Companion namespace objects, private/local declarations and generated code are not counted as missing exposed KDoc.

### T194 fixed-value inventory and verification

Four private constants name the existing CTAPHID_INIT protocol/major/minor/build bytes `02/01/00/00` in `HidReportParser`. A private dispatcher constant names the unchanged GetInfo failure status `0x30`. A private response-builder constant names the unchanged default processing keepalive status `0x01`. Inspection of the three helpers distinguished these protocol fields from already named HID commands, capacity/capability values and CTAP status constants; numeric offsets, length arithmetic, byte masks/shifts and cleanup zeros are ordinary mechanics or already named dimensions. The existing HID INIT test now checks all four version bytes, and the existing keepalive test checks the default emitted status.

The scoped Gradle validation passed: FIDO2 Android-main/host Ktlint, FIDO2 Detekt, FIDO2 main/host/device-test compilation, app main Ktlint/Detekt and debug production/unit-test compilation. The first Ktlint run identified only spacing around new KDoc blocks; that was fixed before the passing run (`BUILD SUCCESSFUL in 1m 44s`, 266 actionable tasks). The final focused run of FIDO2 host tests and app memory-pressure tests passed (`BUILD SUCCESSFUL in 39s`, 197 actionable tasks). XML results: `Ctap2ProtocolTest` 9, `HidReportParserTest` 19, `BluetoothHidTransportLifecycleTest` 15, and `WalletSessionMemoryPressureTest` 3; all 46 have zero failures/errors/skips. `git diff --check` found no whitespace error. This was scoped verification; T181 remains the latest full integrated coverage/device checkpoint, and no new coverage, connected-device ceremony or performance campaign was run.

Status: 193 defined task rows, 191 checked, with only T084/T085 explicitly user-deferred. T127 remains evidence-only without a task row. SC-007 remains partial. T128 ACCEPT and activation authority apply only to the recorded 235-entry reviewed manifest and T130–T136; these later edits do not have current-source independent security acceptance or activation/deployment authorization.
Final KDoc pass added explicit parameter and return descriptions to the already linked exposed functions. The subsequent FIDO2/app style, static and production/host/device-test compilation rerun passed (`BUILD SUCCESSFUL in 1m`, 250 actionable tasks). No behavioral code or test assertion changed after the 46-case focused run.
## T195–T196 — Remove dead transport methods

Completed 2026-10-18. Removed `Fido2Transport.sendCommand` and its misleading no-op implementation, plus `Fido2Transport.isConnected`, its implementation override and the now-orphaned `BluetoothHidDeviceWrapper.isConnected` method. The all-source Kotlin caller search found no call sites for these declarations. `Fido2HomeViewModel` observes `connectionState`; the service and view model use `connect`, `connectDevice` and `disconnect`. `BluetoothHidTransportImpl` remains discovered by `Fido2Module` component scan through its `@Single` annotation. Existing `sendPackets` response delivery and wrapper `connectionState` publication remain unchanged. T192/T193 inventory paragraphs above describe their historical completion-time surface. Current `Fido2Transport` has the three lifecycle methods `connect`, `connectDevice` and `disconnect`, plus the `connectionState` property; the removed methods no longer belong to the current API or `@Req` inventory.

Verification command:

```text
.\gradlew.bat :feature:fido2:ktlintAndroidMainSourceSetCheck :feature:fido2:ktlintAndroidHostTestSourceSetCheck :feature:fido2:detekt :feature:fido2:compileAndroidMain :feature:fido2:compileAndroidHostTest :feature:fido2:compileAndroidDeviceTest :feature:fido2:testAndroidHostTest --tests '*BluetoothHidTransportLifecycleTest' :app:ktlintMainSourceSetCheck :app:detekt :app:compileDebugKotlin --no-configuration-cache --console=plain
BUILD SUCCESSFUL in 1m 33s; 147 actionable tasks (15 executed, 132 up-to-date)
```

The first style pass identified one extra blank line left by the API removal; it was fixed and the complete command rerun. FIDO2 main/host Ktlint and Detekt, app main Ktlint and Detekt, FIDO2 Android main/host/device-test compilation, and app debug compilation passed. `BluetoothHidTransportLifecycleTest` ran 15 tests with zero failures, errors or skips (`feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.transport.BluetoothHidTransportLifecycleTest.xml`). `git diff --check` passed. No device ceremony, coverage or performance campaign was run. The packet's T128 review/activation boundary was not expanded; no current-source independent security acceptance or activation/deployment is claimed. T084/T085 remain user-deferred and SC-007 remains partial.

## T197–T199 — Event authority, source-contract comments and protocol constant

**T197 behavior.** `CredentialRepositoryImpl` now stops when an aggregate event cannot be committed, propagates typed database failure and cancellation, and leaves the SQL projection unchanged for that failed mutation. Successful event append precedes projection changes; if a projection write fails, the committed event remains. Bulk deletion/reset/expiry cleanup stop at the first failed item and report the typed error while preserving prior successful items. Returned aggregate states and credential owners are erased in their existing cleanup paths. Host regressions cover failed event append, projection failure with retained event history, batch middle-item failure, cancellation, retained owner cleanup, successful successor behavior, and aggregate reconstruction of a committed deletion.

**T198/T199 source corrections.** HDK child-derivation KDoc now names borrowed secret/public inputs and the caller-owned factor. Bluetooth HID KDoc now matches request-owner scope, sender queue ownership, and observer-before-unregister teardown. The empty-CBOR response uses the already-defined `CTAP2_ERR_INVALID_CBOR`; its emitted status byte is unchanged. The bounded transport inventory distinguishes CTAPHID errors/commands, CTAP statuses, user-action timeout and broadcast CID from ordinary offsets, length arithmetic, masks/shifts, formatting and cleanup zeros.

**Integrated verification for production changes.** The full configured pipeline passed with the Android 10 limitation explicitly excluded as documented in quickstart:

```powershell
[Environment]::SetEnvironmentVariable('ORG_GRADLE_PROJECT_android.testInstrumentationRunnerArguments.notClass','com.chimali.fido2.data.crypto.CredentialPersistenceCeremonyTest','Process')
.\tools\local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage
```

It completed in 400.11 seconds. Style, static analysis, release lint, production/host/device-test compilation, host tests, connected suites (Mi A2 FIDO2 23/23; app 11/11) and configured coverage floors passed. The excluded Android 10 class fails only because that platform has no independent non-BC Ed25519 verifier; its evidence remains unchanged. An unrelated common-module performance threshold was transiently exceeded once (656.0426 ms) under concurrent CI and passed when rerun in isolation.

**Final host regression after adding aggregate replay coverage.** This command passed in 2m13s:

```powershell
.\gradlew.bat :feature:fido2:ktlintAndroidHostTestSourceSetCheck :feature:fido2:compileAndroidHostTest :feature:fido2:testAndroidHostTest --no-configuration-cache --console=plain
```

The FIDO2 host XML inventory contains 857 tests with zero failures/errors. The new `PasskeyAggregateServiceImplTest` class reports 14/14. A separate attempt to rerun `hdkHostCoverage hdkCoverageVerify` with the documented device exclusion did not finish and was stopped; the coverage rates above therefore describe the immediately preceding full local-CI run with the same production changes and 856 host tests, before adding only this host regression test. No new device ceremony, performance campaign, independent security review, activation or deployment claim is made. No commit was created and the user's existing staged/saved Git state was left untouched.

## T200 — Failure-aware destructive credential enumeration

`DestructiveCredentialBatchReader` now reads the selected DAO query to a snapshot and preserves SQL query-creation and collection failures as a redacted typed database error. It maps the complete destructive selection strictly; any malformed row rejects the batch before the first delete. Genuine empty results remain successful. Expiry selection is computed inside the row/model cleanup scope. Delete-all, RP-scoped delete-all, reset and expiry cleanup use this strict path; display/search flows retain their documented empty-success and malformed-row skipping behavior. Batch deletion stops at its first item error, retains prior committed event history and erases every acquired model owner in `finally`; SQLDelight source rows are erased by the reader on all mapped outcomes.

New configured repository regressions cover query creation and flow collection exceptions, malformed identity after a valid row, RP-scoped enumeration failure, empty-store success, expiry-only deletion, owner cleanup after malformed data/cancellation, and a successful destructive operation after query failure. The existing middle-item failure test now uses two distinct credential identities. The dedicated `CredentialRepositoryDestructiveBatchTest` keeps the repository suite below the configured Detekt class-size limit.

**Focused verification:**

```powershell
.\gradlew.bat :feature:fido2:ktlintAndroidHostTestSourceSetCheck :feature:fido2:compileAndroidHostTest :feature:fido2:testAndroidHostTest --tests '*CredentialRepository*Test*' --no-configuration-cache --console=plain
```

Passed. The full local pipeline then passed:

```powershell
[Environment]::SetEnvironmentVariable('ORG_GRADLE_PROJECT_android.testInstrumentationRunnerArguments.notClass','com.chimali.fido2.data.crypto.CredentialPersistenceCeremonyTest','Process')
.\tools\local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage
```

The pipeline completed in 295.44 seconds. Ktlint, Detekt, release lint, full compile (including production, host and device-test sources), unit tests, common/security/domain/FIDO2 host suites, connected suites and the configured coverage verifier all passed. FIDO2 host coverage executed 863 tests. Connected reports show FIDO2 23/23 and app 11/11 with zero failures/errors/skips on Mi A2 Android 10. `CredentialPersistenceCeremonyTest` was excluded from the instrumentation runner because this platform lacks the independent non-BC Ed25519 verifier; this is the same documented limitation and does not supply the ASUS reference-device evidence waiver.

Coverage results: security 78.37198% instructions / 60.59783% branches; domain 58.39965% / 49.10394%; FIDO2 58.91000% / 54.25416%; database 66.12378% instructions; common 34.58689%; app 36.67335%; repository 52.03778% instructions / 51.63112% branches. All configured floors passed. The current task count is 198/200 checked; T084/T085 remain user-deferred. No Bluetooth ceremony or performance campaign was run. T128 authorization remains restricted to the recorded 235-entry manifest and T130-T136; this task supplies no current-source independent review, activation/deployment or numeric performance-compliance claim. Existing staged/saved Git state was not staged, committed or otherwise changed.

## T201–T202 — Strict lookup-key failure and current verification reconciliation (2026-10-18)

**T201 implementation.** `HmacMetadataLookupTokenService` now throws `MetadataLookupKeyUnavailableException` for an absent/cleared index key instead of a generic `IllegalStateException`. The strict destructive batch reader catches only that dedicated exception at the query/flow collection boundary, logs a fixed generic message, and returns a fixed cause-free `DomainError.StorageError`. Android/JDBC SQL exceptions retain their database error mapping; cancellation remains rethrown by identity. No row/model cleanup, display/search fallback, or session workflow changed. The configured repository regression executes the actual HMAC lookup service through the RP-scoped DAO flow: unprovisioned key yields the typed redacted result with zero aggregate/projection delete calls, and the next empty enumeration succeeds after key provisioning. The focused class passed 7/7 with zero failures/errors/skips. After adding declaration-level requirement traceability to the new exception type, scoped security/FIDO2 Ktlint and security/FIDO2 production plus FIDO2 host/device-test compilation passed in 8 seconds.

**Integrated verification.** After correcting one import-order finding from the first Ktlint pass, this command passed:

```powershell
[Environment]::SetEnvironmentVariable('ORG_GRADLE_PROJECT_android.testInstrumentationRunnerArguments.notClass','com.chimali.fido2.data.crypto.CredentialPersistenceCeremonyTest','Process')
.\tools\local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage
```

The complete pipeline passed in 296.27 seconds. Ktlint, Detekt, release lint, production/host/device-test compilation, unit tests, configured KMP Android host tests, connected tests, and `hdkCoverageVerify` passed. FIDO2 host XML recorded 864 tests; security and domain host suites recorded 46 and 55 tests. Connected Mi A2 Android 10 XML recorded FIDO2 23/23 and app 11/11, each with zero failures/errors/skips. The runner exclusion of `CredentialPersistenceCeremonyTest` remains necessary because Android 10 lacks the independent non-BC Ed25519 verifier that test requires.

Coverage results: security 78.37198% instructions / 60.59783% branches; domain 58.39965% / 49.10394%; FIDO2 58.92098% / 54.25416%; database 66.12378% statements (52.77778% branches reported); common 34.58689% (31.03448% branches reported); app 36.67335% (30.52632% branches reported); repository 52.05291% / 51.64366%. Scoped security HDK coverage was 85.57007% / 64.51613%. All configured floors passed. Module reports are under each module's `build/reports/hdk-coverage/`; repository data is `build/reports/hdk-coverage/repository.xml`; connected app coverage is `app/build/reports/coverage/androidTest/shrunkDebug/connected/index.html`. Focused regression XML is `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.repository.CredentialRepositoryDestructiveBatchTest.xml`; connected reports are `feature/fido2/build/outputs/androidTest-results/connected/androidMain/TEST-Mi A2 - 10.xml` and `app/build/outputs/androidTest-results/connected/shrunkDebug/TEST-Mi A2 - 10.xml`.

**Reconciled status and scope.** Current inventory is 201 defined task rows, 199 checked; T084/T085 remain user-deferred. T127 is evidence-only and has no task row. Earlier checkpoint details stay as historical dated records. The ASUS device waiver, clean-install scope, Android 10 verifier exclusion, partial SC-007, and T128's exact reviewed 235-entry manifest/T130-T136 authorization boundary remain unchanged. This verification does not constitute independent security acceptance or authorize production activation/deployment; it does not claim numerical performance compliance or device-class equivalence. No new Bluetooth ceremony or performance campaign was run. No files were staged or committed; existing staged changes remain preserved.
## T203–T204 — Lookup-token key ownership, contracts and integrated verification (2026-10-18)

**T203 implementation.** `HmacMetadataLookupTokenService` now serializes key provisioning, replacement and clearing with one lock. Each admitted token operation takes a private key snapshot while holding the lock, performs HMAC from that snapshot, then erases its key and temporary input buffers in `finally`. Replacing a key erases the prior service-owned copy; operations begun after clear fail with the existing `MetadataLookupKeyUnavailableException`. HMAC domains, version bytes, token output and caller input ownership are unchanged. The configured Android host suite now contains four deterministic service tests covering domain-separated output, a latch-controlled clear/generate interleaving, replacement erasure, and unavailable/reprovisioned key behavior. The strict repository regression uses the real SQLDelight RP lookup, `CredentialMetadataProtectionService`, and HMAC service; an unavailable key returns the fixed cause-free storage error without mutation, and a provisioned retry succeeds.

**T204 contracts and traceability.** The implementation service class and exposed methods now document borrowed inputs, internal key ownership/snapshot/clearing behavior, token output ownership, failure behavior and JCA copy limits. The common API interface, constants and method now carry declaration-attached contracts and requirement traceability. Configured verification declarations link to the applicable requirements. The bounded inventory is 11 public declarations across the two files (service class plus three methods; interface, four constants, method and exception class), with direct `@Req` on all four actual token-key/token-generation methods. Companion namespace objects are not counted as declarations.

**Focused verification:**

```powershell
.\gradlew.bat :core:security:ktlintAndroidHostTestSourceSetCheck :core:security:compileAndroidHostTest :core:security:testAndroidHostTest --tests '*HmacMetadataLookupTokenServiceTest' :feature:fido2:ktlintAndroidHostTestSourceSetCheck :feature:fido2:compileAndroidHostTest :feature:fido2:testAndroidHostTest --tests '*CredentialRepositoryDestructiveBatchTest' --no-configuration-cache --console=plain
```

Passed. `HmacMetadataLookupTokenServiceTest` reports 4/4; `CredentialRepositoryDestructiveBatchTest` reports 7/7. Ktlint and host compilation passed for both affected modules.

**Integrated verification:**

```powershell
[Environment]::SetEnvironmentVariable('ORG_GRADLE_PROJECT_android.testInstrumentationRunnerArguments.notClass','com.chimali.fido2.data.crypto.CredentialPersistenceCeremonyTest','Process')
.\tools\local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage
```

Passed in 198.88 seconds. Ktlint, Detekt, release lint, full production/host/device-test compile, unit tests, configured Android host tests, connected device suites and the configured coverage verifier all passed. Host XML totals: security 50, domain 55, FIDO2 864, common 10 and database 11; all have zero failures, errors or skips. Connected results: ASUS Zenfone 10 FIDO2 23/23 and app 11/11; Mi A2 FIDO2 23/23 and app 11/11; all four suites report zero failures, errors or skips. The runner excluded `CredentialPersistenceCeremonyTest` because Android 10 lacks the independent non-BC Ed25519 verifier required by that test; the documented exclusion remains.

Coverage reports were generated under each module's `build/reports/hdk-coverage/`, with repository summary at `build/reports/hdk-coverage/repository.xml`; the app connected report is `app/build/reports/coverage/androidTest/shrunkDebug/connected/index.html`. Observed instruction coverage: security 78.11%, domain 58.40%, FIDO2 58.92%, database 66.12%, common 34.59%, app 38.08%, repository 52.08%. Reported branches were security 60.53%, domain 49.10%, FIDO2 54.25%, database 52.78%, common 31.03%, app 31.58%, repository 51.65%. All configured floors passed. Focused XMLs are `core/security/build/test-results/testAndroidHostTest/TEST-com.chimali.core.security.impl.HmacMetadataLookupTokenServiceTest.xml` and `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.repository.CredentialRepositoryDestructiveBatchTest.xml`; connected reports are in `feature/fido2/build/outputs/androidTest-results/connected/androidMain/` and `app/build/outputs/androidTest-results/connected/shrunkDebug/`.

**Status and limits.** Current count is 203 defined task rows, 201 checked; only T084 and T085 remain user-deferred. ASUS UI checks passed once the device was unlocked. T128 ACCEPT remains limited to the reviewed 235-entry manifest and T130–T136; this later source change has no independent current-source security acceptance and does not authorize activation or deployment. Clean-install scope, the ASUS reference-device waiver, the Android 10 verifier exclusion and partial SC-007 remain. No performance campaign or numerical performance compliance is claimed. Existing staged Git changes and the index were left untouched.

## T205–T206 — Current status and pre-provider-copy regression (2026-10-18)

T205 updated the current-status summaries to 203/205 checked while preserving the earlier dated checkpoint details. The quickstart now identifies T203–T204 (198.88 seconds) as the latest full integrated CI/coverage checkpoint and marks T201–T202 as historical. The repeated Phase 56 heading remains unchanged and now has a chronology note in the later completion record. The service and common API KDoc explicitly state that JCA `SecretKeySpec`/`Mac` providers may retain internal key copies beyond cleanup of the service's mutable snapshot; no physical-erasure claim is made.

T206 replaces the former post-`mac.init` test gate with a deterministic test-only JCA provider that blocks during `Mac` construction after operation-key selection and before provider key capture. The test clears the service while blocked, verifies the retained service-owned key was zeroed, checks that later generation fails after clear, and compares the admitted operation to an independent synthetic HMAC reference using exact domain/version/value framing and a distinct key.

Verification passed:

```powershell
.\gradlew.bat :core:security:ktlintAndroidHostTestSourceSetCheck :core:security:compileAndroidHostTest :core:security:testAndroidHostTest --tests '*HmacMetadataLookupTokenServiceTest' --no-configuration-cache --console=plain
.\gradlew.bat :core:security:testAndroidHostTest --tests '*HmacMetadataLookupTokenServiceTest' :feature:fido2:testAndroidHostTest --tests '*WalletMasterSeedProviderTest' --tests '*CredentialRepositorySearchableMetadataTest' --tests '*CredentialRepositoryDestructiveBatchTest' --no-configuration-cache --console=plain
.\gradlew.bat :core:security:ktlintAndroidMainSourceSetCheck :core:security:compileAndroidMain --no-configuration-cache --console=plain
```

The final configured test XML reports HMAC service 4/4, wallet seed provider 14/14, searchable metadata repository 17/17 and destructive batch repository 7/7; zero failures, errors or skips. XML reports are under `core/security/build/test-results/testAndroidHostTest/` and `feature/fido2/build/test-results/testAndroidHostTest/`. Security Android host-source Ktlint and host compilation passed; Android-main Ktlint and compilation passed. This focused verification does not rerun or refresh full CI/coverage or connected-device evidence. Task inventory: 205 defined, 203 checked; T084/T085 remain user-deferred. T128's exact review scope, ASUS waiver, Android 10 verifier limitation, clean-install scope and partial SC-007 remain. No independent current-source security acceptance, production activation/deployment authorization or numeric performance compliance is asserted. Existing staged changes and Git index were not changed.

## T207–T208 — JCA test isolation and operation-buffer cleanup evidence (2026-10-18)

T207 replaces the race fixture's JVM-wide `Security` provider insertion and static SPI latches with `generateTokenWithMacFactory`, an instance-local internal seam. The worker still pauses after the operation snapshot is selected and before the supplied JCA factory creates and initializes a `Mac`. The test clears the service while paused, checks that the service-owned key is erased, confirms later calls fail, releases the admitted operation, and compares its token to an independent synthetic HMAC expectation. The fixture no longer mutates the provider registry or shares static latch state. The public service API, production framing, key ownership and synchronization remain unchanged.

T208 retains the operation snapshot and observes owned-buffer erasure after success. Its admitted failure passes a `CharBuffer` containing an unpaired high surrogate and asserts `MalformedInputException`, unchanged borrowed character data, zeroed operation snapshot/domain bytes/encoder scratch, and a subsequent valid token matching an independent synthetic expectation. The production cleanup was present; these tests close the evidence gap without asserting an observed leak.

Verification command:

```powershell
.\gradlew.bat :core:security:ktlintAndroidMainSourceSetCheck :core:security:compileAndroidMain :core:security:ktlintAndroidHostTestSourceSetCheck :core:security:compileAndroidHostTest :core:security:testAndroidHostTest --tests '*HmacMetadataLookupTokenServiceTest' :feature:fido2:testAndroidHostTest --tests '*WalletMasterSeedProviderTest' --tests '*CredentialRepositorySearchableMetadataTest' --tests '*CredentialRepositoryDestructiveBatchTest' --no-configuration-cache --console=plain
```

The command passed. XML reports show HMAC service 5 tests, wallet provider 14, searchable metadata repository 17 and destructive batch repository 7; all reports have zero failures, errors or skips. Android-main and Android-host-test Ktlint and compilation passed. Reports are `core/security/build/test-results/testAndroidHostTest/TEST-com.chimali.core.security.impl.HmacMetadataLookupTokenServiceTest.xml` and the three corresponding `feature/fido2/build/test-results/testAndroidHostTest/TEST-*.xml` files. These focused checks do not refresh coverage or connected-device results. Current inventory: 205/207 checked, with only T084/T085 user-deferred. The T203–T204 198.88-second integrated CI/coverage gate remains the latest full gate. Preserve T128's exact reviewed-manifest/T130–T136 boundary, clean-install scope, ASUS waiver, Android 10 verifier limitation, partial SC-007, and the absence of new security acceptance, activation/deployment authorization or numeric performance claims. Existing staged Git changes and the index were preserved.

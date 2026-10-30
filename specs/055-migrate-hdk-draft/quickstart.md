# Validation Guide: Current Local HDK Profile

**Date**: 2026-10-14  
**Related contracts**: [derivation bytes](contracts/local-key-derivation.md), [credential lifecycle](contracts/credential-lifecycle.md).  
**Current performance disposition (2026-10-29)**: Physical T084 is superseded by Feature 060's host-simulated assessment; T085 profiling and optimization remain deferred. The declared assessment recorded ten measured occurrences each for MakeCredential, GetAssertion, GetInfo and ResetAuthenticator; all 40 passed below 200 ms, meeting SC-007 for that workload. See [Feature-060 evidence](../060-fido2-latency-measurement/implementation-evidence.md).

## Prerequisites

- Repository root `D:\octav\source\repos\Chimali`, current feature branch, and completed implementation/constitution amendment. Review dispositions are revision-scoped: the recorded T103 ACCEPT covers only its fingerprinted T094/T098-T103 delta. T128 authorizes only the exact verified current-source fingerprint and scope recorded in [security-review-packet.md](security-review-packet.md); any source change requires reassessing that authorization.
- The repository's supported JDK and Android SDK 37 setup; use its Gradle wrapper and pinned dependencies.
- Configured host fixtures and tests, including the actual production wallet/provider behavior. Replace tests that duplicate wallet formulas as their own oracle.
- A disposable Android API 28+ emulator/device for fresh-install tests. The user waived the Pixel 6a-class requirement and designated the ASUS Zenfone 10 as the reference device for hardware evidence. Never reset a personal installation as part of this guide.
- Independent expected-value fixture provenance, including reference-engine versions, published vector checks, and public synthetic test inputs. No private user recovery material in fixtures or reports.

## 1. Confirm task discovery and compile

From the repository root:

```powershell
Set-Location 'D:\octav\source\repos\Chimali'
.\gradlew.bat :core:security:tasks :core:domain:tasks :feature:fido2:tasks --all --console=plain
.\gradlew.bat :core:common:compileAndroidMain :core:security:compileAndroidMain :core:domain:compileAndroidMain :core:database:compileDebugKotlin :feature:fido2:compileAndroidMain :feature:fido2:compileAndroidDeviceTest :app:compileDebugKotlin
.\gradlew.bat :core:security:compileAndroidHostTest :core:domain:compileAndroidHostTest :feature:fido2:compileAndroidHostTest
```

Expected: current DI bindings, new current-model SQL mappings, mutable mnemonic API call sites, and host sources compile. Old `src/test/.../hdkeys` files are not accepted as execution evidence. Compilation is not an interoperability or security signoff.

## 2. Run current host and shared verification

```powershell
.\gradlew.bat :core:common:testAndroidHostTest :core:security:testAndroidHostTest :core:domain:testAndroidHostTest :core:database:testDebugUnitTest :feature:fido2:testAndroidHostTest :app:testDebugUnitTest --console=plain
```

The configured Android host execution must include applicable common tests. Confirm test names in XML reports; a successful empty suite does not satisfy acceptance.

Required evidence:

- RFC 5869 and RFC 9180 parent vector checks; independently fixed draft/local-profile outputs and unsigned-index boundaries.
- Validated points/scalars and explicit algorithm/profile matching; malformed and missing input failures.
- Full-ID binding, a fixture pair colliding under the removed shortened mapping, and equivalent Base64URL encodings.
- Production registration public key/self-attestation agreement and valid assertions after provider reinitialization/restart.
- New Ed25519 and ML-DSA deterministic seed import and independently verified signatures, with no custom randomness adapter.
- Current SQL insert/map/summary, event replay and snapshot round trips preserve algorithm/profile/key identity. Registration uses explicit event/projection transaction phases and reports success only after both commit. Inject failure/cancellation after each prior-projection deletion and before insertion; assert complete projection rollback and intact prior records, retained committed event/descriptor/key, and typed error or propagated cancellation.
- Selected stored-key mismatch rejects signing with no repair query or worker; candidate listing performs zero derivation.
- Owner cleanup on success/failure/cancellation, publication failure, concurrent getters/clear, cache destruction and caller/provider non-aliasing. Test fakes return separate owned storage where the real contract does.

Do not add old-key, upgrade, invalidation, legacy-schema or re-registration suites for this feature.

## 3. Run platform clean-install and restart validation

```powershell
.\gradlew.bat :feature:fido2:compileAndroidDeviceTest :app:assembleDebug
adb devices
```

The module configures `androidDeviceTest`; use that source set for the real Keystore/DataStore/SQLiteMC tests. Discover the connected task from the actual registered model rather than copying an older AGP command:

```powershell
$hdkDeviceTaskOutput = & .\gradlew.bat :feature:fido2:tasks --all --console=plain
$hdkConnectedTasks = @($hdkDeviceTaskOutput | ForEach-Object {
    if ($_ -match '^\s*(connected[A-Za-z0-9]*Android(?:Device)?Test)\s+-') { $matches[1] }
} | Sort-Object -Unique)
$hdkChosenTask = @('connectedAndroidDeviceTest', 'connectedAndroidTest') |
    Where-Object { $_ -in $hdkConnectedTasks } | Select-Object -First 1
if (-not $hdkChosenTask -and $hdkConnectedTasks.Count -eq 1) { $hdkChosenTask = $hdkConnectedTasks[0] }
if (-not $hdkChosenTask) { throw 'Resolve the configured connected-device task from the task list before running.' }
& .\gradlew.bat ":feature:fido2:$hdkChosenTask" --console=plain
```

The connected suite's test fixture must create its own empty stores on a disposable test installation. Expected: first initialization creates encrypted mnemonic storage, current encrypted credential/event storage opens, a current credential registers and authenticates, and a fresh process/provider reconstructs the same key. `CredentialPersistenceCeremonyTest.kt` also injects a different valid synthetic P-256 public key into the test projection and verifies that assertion fails without changing that stored value or incrementing the sign counter. Verify real framework behavior and immutable-text dependency limits in the audited normalization/display adapters.

The suite must also discover and execute `feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/data/crypto/CredentialPersistenceCeremonyTest.kt` and `feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/presentation/ui/DevelopmentToolsScreenTest.kt`; the Compose test was moved from `src/androidTest` and updated for mutable mnemonic owners. Record named Compose results for reveal/import/hide, replacement/disposal, cancellation, existing authorization/clipboard behavior and rendering/accessibility. A passing ViewModel test or compiled but undiscovered UI source is insufficient. Verify device-test dependencies in `feature/fido2/build.gradle.kts` and avoid duplicate source registration.

For the pending-authentication transport path, also execute
`feature/fido2/src/androidDeviceTest/kotlin/com/chimali/fido2/data/transport/BluetoothHidTransportDisconnectDeviceTest.kt`.
It drives the real CTAPHID dispatcher, GetAssertion handler, wallet provider and transport
`disconnect()` with a synthetic candidate summary, and verifies cancellation, generation fencing
and ceremony-lock release. It does not simulate Android Bluetooth profile callbacks or replace the
broader lifecycle, clipboard, or external relying-party acceptance cases.

The ordinary connected-suite run has no cross-invocation argument channel, so the OS process-restart proof is driven separately with a random synthetic fixture ID:

```powershell
.\tools\run-hdk-process-restart-test.ps1
```

This assembles and installs the device-test APK, registers a synthetic P-256 credential in encrypted disposable DataStore/SQLiteMC files, records the preparation PID, force-stops the instrumentation package, confirms it has no live PID, and invokes recovery in a fresh process. Recovery must observe a different PID, load the persisted descriptor/public key, sign an assertion, and independently verify its signature. The test removes its synthetic database and files. Optionally pass `-AdbPath` or `-Serial` when platform tools or the target device are not at their defaults.

The production application package has a separate two-phase restart proof. Run it with:

```powershell
.\tools\run-hdk-app-process-restart-test.ps1
```

This builds the isolated `shrunkDebug` app/test APK pair (`com.chimali.shrunk` and
`com.chimali.shrunk.test`), registers a synthetic P-256 credential through the app's production Koin
graph, then force-stops the **target application package** and confirms its PID is gone before
starting recovery in a fresh app process. Recovery asserts a different PID, reloads the credential
from the app's encrypted persistence, signs an assertion and verifies it with a non-BC platform
JCA provider. The runner uninstalls only these `.shrunk` packages before and after the run; it does
not touch a normal `com.chimali` installation. The fixture ID and credential ID are synthetic, and
the app-private fixture is removed after recovery.

The app instrumentation suite also verifies the production memory callback bridge. Run it with:

```powershell
.\gradlew.bat :app:connectedAndroidTest --console=plain
```

`WalletSessionMemoryPressureCallbackTest` calls `ChimaliApplication.onTrimMemory` for all three
severe levels and verifies the production wallet generation is fenced. The two
`ProductionAppProcessRestartTest` methods do no work during ordinary discovery without the
dedicated fixture ID; only `run-hdk-app-process-restart-test.ps1` constitutes restart evidence.

Exercise the lifecycle contract's overlapping foreground/HID owners: foreground loss ends its owner while an already authorized bounded HID ceremony can finish; pending foreground-dependent verification is cancelled; disconnect ends transport work; last-owner release erases caches. Replacement and each of `TRIM_MEMORY_RUNNING_CRITICAL`, `TRIM_MEMORY_BACKGROUND`, `TRIM_MEMORY_MODERATE` fence/cancel all old-generation work, clear caches and issue the required GC hint. Assert rejection of new signing admission, suppression of stale results from an already running synchronous provider call, cleanup and lazy same-wallet reconstruction on a later authorized request. An idle reconnect/background event must not initialize it.

For a manual browser-to-authenticator flow, use the repository's loopback-only disposable RP harness at [`tools/ctap2-rp-harness`](../../tools/ctap2-rp-harness/README.md). It requires Node 22+, creates a synthetic in-memory account, offers ES256 only, and discards the credential when its server exits. Start it with `npm install` and `npm start`, then open `http://localhost:4173` in Edge on the Windows host. Pair the test Android device with that host, start Chimali's authenticator service, and register the synthetic credential. Verify the RP reports a verified registration and assertion. Force-stop and relaunch the same app package without clearing app data, start the authenticator service again, and authenticate the same credential; record the app process IDs, assertion counters, app-side request timings and verification results.

This harness is an actual local WebAuthn RP/browser/CTAP2 flow, not an external production account or relying-party service. It covers P-256/ES256 only. Ed25519 and ML-DSA evidence remains in the independent fixture and device/host test suites; do not claim those algorithms interoperate with browser WebAuthn. No issuer or OpenID4VCI endpoint is part of this validation. Record harness limitations and any repeated CTAP requests or over-budget samples; a verified RP response alone does not prove one derivation per browser ceremony or remote-host receipt timing.

## 4. Static checks and measured coverage

```powershell
.\gradlew.bat :core:security:detekt :core:security:ktlintCheck :core:domain:detekt :core:domain:ktlintCheck :feature:fido2:detekt :feature:fido2:ktlintCheck :core:common:detekt :core:common:ktlintCheck :core:database:detekt :core:database:ktlintCheck :app:detekt :app:ktlintCheck
```

Use the implementation's new scoped init script and tasks, defined by this plan:

```powershell
.\gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkHostCoverage hdkCoverageVerify
```

The script must pin JaCoCo 0.8.15, instrument actual configured tasks, derive production class outputs from the current compilation model, and fail on missing execution data or zero discovered tests. `hdkHostCoverage` produces scoped HDK and whole affected-module XML/HTML. `hdkCoverageVerify` independently enforces Tier 1 instruction/statement and branch coverage ≥60%, each affected Tier 2 instruction/statement floor ≥20% (`core:database`, `core:common`, `app`, plus any additionally changed Tier 2 owner), and repository aggregate instruction/statement coverage ≥50%; report Tier 2 branches separately. Merge discovered host/device execution data where needed without duplicate production class denominators. A scoped report or aggregate percentage cannot replace a module gate.

Before integration, collect all module floors and repository aggregate alongside the full local CI run. Wire broader actual host/device accounting if the required report does not exist; the vault-only script cannot establish these metrics. Missing module data or a pre-existing gate failure must remain visible and unresolved until the required gate is met; do not exclude that module, change denominators, lower a threshold or report missing data as a pass.

## 5. Performance, lifecycle and debt review

**Current disposition (2026-10-16):** The user accepted the observed ASUS/Windows UX and deferred the end-to-end measurement investigation and remaining T084 campaign to [the superseding Feature 060 assessment](../060-fido2-latency-measurement/spec.md). The procedure below is retained for that future session. Subtracting only Android's prompt interval leaves any unmeasured Windows interaction in the residual; that value is not established as system-only delay or post-fingerprint response time. The <200ms internal-operation target remains documented; its T085 investigation, optimization and sample campaign are also deferred to [the later phase](../../docs/backlog/fido2-internal-performance-enhancements.md).

On the user-designated ASUS workload and release build, measure both SC-007 timing boundaries. The RP harness records the browser's monotonic timer from RP options request through RP signature verification. After each verified ceremony, sum `user_ns` from the matching `ChimaliT003` records, divide by 1,000,000 to convert nanoseconds to milliseconds, and enter that value with all matching numeric `operation_id` values. The page stores the adjusted RP-to-verified-response value (<1,000ms target) in memory and the server logs a `SC007_E2E` record. Record all 10 successful ceremonies, including failures against the limit. The user-wait sum must come only from the matching app records.

`ChimaliT003` also reports `internal_ns`, calculated from CTAP dispatch through response construction after subtracting the measured user-verification interval and before Bluetooth queue/report handoff. Every implemented FIDO/CTAP operation must be <200ms; `processing_ns` is the unadjusted duration and `system_ns` remains the adjusted local HID handoff, not remote-host receipt. Keep the RP sample ID, app operation IDs, raw RP total, user interval, adjusted RP value, app internal values, and report-acceptance result together. Confirm zero candidate-list derivations and one selected assertion derivation. Record baseline/completed binary size and peak/session memory with the same build variant; enforce current constitution budgets. Exercise HID disconnect/session end, foreground lifecycle changes and required memory-pressure callbacks, verifying cache destruction and cancellation cleanup. Check instrumented LeakCanary results for reproducible retained owners.

Check removed implementations and APIs in current source; matches below must be absent or have a specific demonstrated non-retired use:

```powershell
rg -n 'HdkEcdhP256|HashToScalar|MultiplicativeBlinding|requestRemoteDerivation|acceptRemoteKey|deriveSalt|getPqChildSeed|DeterministicSecureRandom|CorruptedKeyRepairWorker|update_public_key|credentialAlias' core feature
```

Exclude historical specifications/changelogs from this runtime check. Verify actual deletion or replacement, not disabled code, empty wrappers, renamed old tests, or suppressed dead-code warnings.

This feature supports a clean installation only. Chimali has not been released, so the FIDO2 SQLDelight `.sqm` migration-history files were removed to avoid carrying obsolete schema-version artifacts; database-upgrade compatibility is out of scope. Confirm `Fido2Database.sq` and current Kotlin/SQLDelight call sites do not define or use the retired `private_key_alias` field:

```powershell
rg -n 'private_key_alias|credentialAlias' feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/Fido2Database.sq feature/fido2/src
```

No matches are expected. Do not interpret this fresh-schema check as evidence of database-upgrade compatibility.

## 6. Documentation and final CI

Verify that the local-profile amendment was introduced in constitution 3.0.0 and that the current constitution is 3.1.0, with rationale/version/date, synchronized BRD/TRD, exact local profile claims, parent-protection limits, and the separate OpenID4VCI backlog item. Current API/test traceability must reference stable requirement IDs; feature-local numbering alone is insufficient.

```powershell
# On Mi A2 / Android 10, exclude the ceremony test requiring an unavailable independent Ed25519 verifier.
[Environment]::SetEnvironmentVariable('ORG_GRADLE_PROJECT_android.testInstrumentationRunnerArguments.notClass','com.chimali.fido2.data.crypto.CredentialPersistenceCeremonyTest','Process')
.\tools\local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage
```

Also retain the explicit host-test evidence from step 2: the existing generic `test` selector is not proof that every KMP host suite was discovered. Verify any added coverage wiring against the current task model before recording results.

Record executed commands, discovered task names, test/report paths, independent fixture provenance, review disposition, coverage, latency/memory/size measurements and limitations in [implementation-evidence.md](implementation-evidence.md). That report retains the named host/device suites, synthetic process-restart runners, ownership/LeakCanary checks, required coverage floors and successful local-CI invocations, with dates and cache-reuse limits. T072/T075 review findings and the supplemental UV review's stated test/CI conditions are closed within the reviewed scope. The T128 independent disposition ACCEPTs only its exact fingerprinted 235-entry manifest and T130-T136 scope. Later T140-T177 source changes are outside that reviewed scope and do not inherit its activation authorization. T103 remains limited to its fingerprinted T094/T098-T103 delta. No current-source production activation or deployment is claimed.

The corrected baseline build succeeded after disk space was freed. T003/T066/T071/T074/T082 evidence includes matched ASUS startup, TTID/TTFD, size, memory and corrected local HID handoff results. The user waived Pixel 6a-class equivalence and selected ASUS. Earlier Moto failures remain diagnostic history. The localhost RP results establish neither production-service nor draft HDK interoperability.

T069/T083 documentation reconciliation is complete. Feature 060's declared host-simulated assessment measured ten occurrences each for MakeCredential, GetAssertion, GetInfo and ResetAuthenticator; all 40 passed below 200 ms, meeting SC-007 for that workload. The physical T084 campaign was superseded by the owner-authorized 2026-10-29 scope amendment. T085 optimization and its follow-up campaign remain deferred; historical device observations remain diagnostic records. Physical UX and HID timing are not substitutes for the simulated internal-operation metric.

Earlier 2026-10-18 verification checkpoint: 174/176 task rows checked; T084 physical campaign was superseded by the owner-authorized simulated assessment on 2026-10-29; T085 remains deferred. The filtered integrated local-CI pipeline passed in 377.96 seconds, with 849 FIDO2 host tests, connected Mi A2 FIDO2 23/23 and app 11/11, and all configured coverage floors. The Android 10 `CredentialPersistenceCeremonyTest` class was excluded because the independent non-BC Ed25519 verifier is unavailable on that platform. Its exact rates, report paths, race outcomes and commands are retained in `implementation-evidence.md`.

Historical 2026-10-18 T181 verification: 178/180 task rows were checked, with T084/T085 user-deferred. `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` passed after the callback/state-waiter extraction: 851 FIDO2 host tests, connected Mi A2 FIDO2 23/23 and app 11/11, and all configured coverage floors. The same Android 10 `CredentialPersistenceCeremonyTest` exclusion remains. This engineering verification does not extend T128 authorization or claim current-source independent review, activation, deployment, ASUS reference-device equivalence or numerical SC-007 compliance. T127 still has no task definition; clean-install scope is unchanged.

Historical 2026-10-18 T182 completion: current inventory is 181 task rows, 179 checked and T084/T085 user-deferred. The continuation and HID report-size declarations now document their ownership/lifecycle and descriptor/parser contracts and carry direct requirement links. Android main Ktlint, Detekt, production compilation and device-test compilation passed. No runtime behavior changed; T128 review scope, the Android 10 verifier exclusion, ASUS waiver, clean-install scope and partial SC-007 are unchanged.

Historical 2026-10-18 T183 completion: current inventory is 182 task rows, 180 checked and T084/T085 user-deferred. Declaration KDoc now documents the remaining scoped current-profile constants and the HID framing/command constants, plus equality/hash, diagnostic, cleanup and result semantics. Scoped Ktlint, Detekt and production/host/device-test compilation passed; no behavior changed. T128 review scope, the Android 10 verifier exclusion, ASUS waiver, clean-install scope and partial SC-007 are unchanged.

## T197–T199 implementation checkpoint (2026-10-18)

The event-authority and projection-order changes, corrected ownership/lifecycle KDoc, and existing invalid-CBOR constant reuse are complete. Run the full configured local-CI/coverage pipeline with the Mi A2 Android 10 verifier exclusion above. Its latest passing result is recorded in [implementation-evidence.md](implementation-evidence.md); it predates one additional host-only aggregate-history replay test. After that test was added, the complete FIDO2 host suite passed with 857 tests and no failures. A further integrated coverage refresh did not complete, so treat the recorded coverage figures as belonging to the preceding 856-test CI pass. T084/T085 remain deferred; SC-007 and numerical performance compliance remain partial/unclaimed.

## Historical verification checkpoint — T201/T202 (2026-10-18)

`tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` passed in 296.27 seconds. It passed Ktlint, Detekt, release lint, full production/host/device-test compilation, unit tests, configured KMP Android host suites, connected Mi A2 Android 10 FIDO2 (23/23) and app (11/11) suites, and all configured coverage floors. The FIDO2 host suite recorded 864 tests. The `CredentialPersistenceCeremonyTest` class was excluded because Android 10 lacks the independent non-BC Ed25519 verifier it requires. Coverage reports are under each module's `build/reports/hdk-coverage/`; repository coverage is `build/reports/hdk-coverage/repository.xml`; connected app coverage is `app/build/reports/coverage/androidTest/shrunkDebug/connected/index.html`. Exact rates and the destructive-batch focused test report are recorded in `implementation-evidence.md`.

At that checkpoint, inventory was 201/203 defined task rows checked; T084 physical campaign was superseded by the owner-authorized simulated assessment on 2026-10-29; T085 remains deferred. T127 is evidenced without a task definition. This gate did not expand T128's exact reviewed-manifest/T130-T136 authorization or claim current-source independent acceptance, production activation/deployment, ASUS device-class equivalence or numerical SC-007 compliance. Clean-install scope and partial SC-007 remain.

## Latest full integrated verification checkpoint — T203/T204 (2026-10-18)

`tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` passed in 198.88 seconds. It passed Ktlint, Detekt, release lint, production/host/device-test compilation, unit tests, configured KMP Android host suites, connected Mi A2 Android 10 FIDO2 (23/23) and app (11/11) suites, and all configured coverage floors. The FIDO2 host suite recorded 864 tests and the security host suite 50. `CredentialPersistenceCeremonyTest` was excluded because Android 10 lacks its required independent non-BC Ed25519 verifier. Exact rates and report paths are in `implementation-evidence.md` under T203–T204.

T205–T206 subsequently passed focused security style and host compilation plus `HmacMetadataLookupTokenServiceTest`, `WalletMasterSeedProviderTest`, `CredentialRepositorySearchableMetadataTest` and `CredentialRepositoryDestructiveBatchTest`. These focused checks do not refresh the integrated coverage or connected-device results. Current inventory is 203/205 task rows checked; T084 physical campaign was superseded by the owner-authorized simulated assessment on 2026-10-29; T085 remains deferred. T128's exact review scope, the ASUS device waiver, clean-install scope, Android 10 verifier limitation and partial SC-007 remain unchanged.

## Latest focused implementation checkpoint — T207/T208 (2026-10-18)

The race fixture now uses an instance-local internal MAC factory seam and does not modify the JVM-wide JCA provider registry. The cleanup regression verifies operation snapshot and owned buffer erasure after success and after strict UTF-8 rejects an unpaired surrogate with `MalformedInputException`; it also checks the borrowed input and a valid successor HMAC.

```powershell
.\gradlew.bat :core:security:ktlintAndroidMainSourceSetCheck :core:security:compileAndroidMain :core:security:ktlintAndroidHostTestSourceSetCheck :core:security:compileAndroidHostTest :core:security:testAndroidHostTest --tests '*HmacMetadataLookupTokenServiceTest' :feature:fido2:testAndroidHostTest --tests '*WalletMasterSeedProviderTest' --tests '*CredentialRepositorySearchableMetadataTest' --tests '*CredentialRepositoryDestructiveBatchTest' --no-configuration-cache --console=plain
```

This focused command passed. XML counts: HMAC 5, wallet 14, searchable metadata 17, destructive batch 7; all zero failures/errors/skips. Android-main and Android-host-test style and compilation passed. Inventory is 205/207 checked; only T084/T085 are user-deferred. The T203–T204 integrated 198.88-second CI/coverage result remains the latest full gate; no coverage or connected-device refresh was performed. T128's review boundary and all device, clean-install, SC-007 and activation limitations remain unchanged.




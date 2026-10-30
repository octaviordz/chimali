# Feature 060 Implementation Evidence

This record contains the ten fields required by TRD §9.1 for the host acceptance assessment and the separate Android device diagnostic.

## Host acceptance assessment

**Requirement**: TR-PERF-010; Feature-060 FR-001–FR-013; Feature-055 SC-007.

**Scope**: Host-simulated assessment of production `CtapHidCommandDispatcher.handleGetInfo`, `RegisterCredentialUseCase`, `GetAssertionUseCase`, `Ctap2ResetAuthenticatorHandler` and `ResetAuthenticatorUseCase`. GetInfo and MakeCredential execute within one registration action; GetAssertion and ResetAuthenticator execute within one authentication action. The fake RP independently verifies synthetic registration and authentication responses. This is the project latency acceptance method and replaces Feature-055 T084 and the physical RP-to-response target by the owner-authorized 2026-10-29 scope decision.

**Revision**: `7025f87dca4605c4348a3438a90c6ed428d9eb51+worktree-source-sha256:959e6ed8d6ba0fa21a08fe2bb72bdd4162947dcd09b47adc5961635b7df6fcf5` (HEAD plus SHA-256 of measured production, shared/host/device test sources, Gradle/dependency inputs and fingerprinted spec/contract).

**Named procedure**: `SimulatedLatencyAssessmentTest.runDeclaredAssessment`, run with `.\gradlew.bat :feature:fido2:testAndroidHostTest --tests com.chimali.fido2.performance.SimulatedLatencyAssessmentTest`. Workload `060-host-v1`: two warmups and ten measured repetitions per ceremony; all attempts retained; descriptive min/median/max; no outlier removal; strict per-occurrence `<200 ms` comparison.

**Environment**: Windows 11 amd64; OpenJDK 64-Bit Server VM 21.0.10+1-b1163.105; real monotonic `System.nanoTime` (`jvm:System.nanoTime`). Host report generated `2026-10-29T22:28:24.522588400Z`.

**Expected / observed result**: Expected: 24 actions (4 warmups, 20 measured), independently verified fake-RP responses, two operations for each action, and ten eligible occurrences for each of four operation types. Observed: 24 actions and 48 operation occurrences retained; all measured occurrences passed and host disposition was **PASS**. The startup GetInfo warmup can exceed the budget; warmups remain recorded and are ineligible by the declared policy.

| Operation | Measured occurrences | Minimum (ms) | Median (ms) | Maximum (ms) | Result |
|---|---:|---:|---:|---:|---|
| MakeCredential | 10 | 4.0295 | 5.2631 | 13.6359 | PASS |
| GetAssertion | 10 | 3.7122 | 5.5914 | 34.9114 | PASS |
| GetInfo | 10 | 1.5167 | 2.6145 | 4.4156 | PASS |
| ResetAuthenticator | 10 | 0.2529 | 0.4313 | 1.9908 | PASS |

**Date**: 2026-10-29 UTC; host report timestamp above.

**Limitations**: The workload uses no browser, HID/Bluetooth transport, production persistence or physical-device workflow. GetInfo calls the production dispatcher, provider and response encoder with HID callbacks substituted. ResetAuthenticator uses the production handler/use case against isolated storage. Credential persistence and key derivation are synthetic; JCA ECDSA signing/verification is real. Immediate UV uses a working fake with no deliberate delay; its intrinsic execution is recorded and gates the production use case. The host Windows clock is cross-checked with `QueryUnbiasedInterruptTimePrecise`; a difference above 5 ms or unavailable continuity evidence invalidates timing. Awake scheduling is included. T085 profiling attribution and optimization remain deferred.

**Status**: **PASS**. This is the project's relevant latency assessment and replaces the physical feature-055 T084 campaign; historical physical observations remain diagnostic context. T085 remains deferred.

**Artifact link**: [Final host machine-readable report](artifacts/host-measurement-20261029T222824Z.json), copied from the final run output at `feature/fido2/build/reports/fido2/060/simulated-latency-assessment.json`.

One earlier complete host run, retained as [host diagnostic evidence](artifacts/host-measurement-20261029T221859Z.json), returned `FAIL`: one of its 40 eligible occurrences, a verified and clock-continuous MakeCredential measurement, was 344.2817 ms; the report retains the other 39 eligible occurrences. This run-to-run outlier is retained under the predeclared no-removal policy; it is evidence of host measurement variability, not silently discarded. The final full-CI host run is retained separately as [final host assessment](artifacts/host-measurement-20261029T222824Z.json), matching the report timestamp, revision, workload and summaries recorded above.

## Android device diagnostic

**Requirement**: TR-PERF-010; Feature-060 Android extension FRs and Android measurement-validity checklist.

**Scope**: Same fake-RP registration and authentication workflow on ASUS_AI2302 (Android 15, API 35), using production registration/assertion use cases and the GetInfo and ResetAuthenticator paths. No browser, Bluetooth transport, physical authenticator, production persistence or biometric prompt is exercised. Device disposition is diagnostic and does not replace or change host acceptance.

**Revision**: `7025f87dca4605c4348a3438a90c6ed428d9eb51+worktree-source-sha256:959e6ed8d6ba0fa21a08fe2bb72bdd4162947dcd09b47adc5961635b7df6fcf5` (matched by the device report, host collector and built instrumentation package identity).

**Named procedure**: `.\tools\fido2-android-latency.ps1 -Serial R7AIB7002901AZ3`. The collector selects one authorized device, serializes execution, computes and supplies the source fingerprint and run ID, runs `:feature:fido2:connectedAndroidDeviceTest`, extracts the AndroidX `PlatformTestStorage` output, verifies run/source/package/APK identity, and writes a separate JSON artifact. On collection or execution error it records `INCOMPLETE` and includes recoverable partial attempts when available.

**Environment**: `asus ASUS_AI2302`; Android 15, API 35; clock domain `android:System.nanoTime`; test package `com.chimali.fido2.test`; instrumentation APK SHA-256 `c4a8d250634d2e010b3741e4a9a53fd2e9c6034cedea5032f125a91786fd2a3d`. Source and package provenance verified. Run ID `58f5c694-0a65-40ee-befc-e8a52a8ff61a`; report generated `2026-10-29T22:30:26Z`.

**Expected / observed result**: Expected: 24 ceremony attempts (4 warmups and 20 measured), all independently verified; 48 operations; ten eligible measured occurrences for each operation type; valid primary/secondary clock evidence; strict `<200 ms` comparison. Observed: all attempts and operations were retained, all 40 eligible operation measurements passed, and Android diagnostic disposition was **PASS**.

| Operation | Measured occurrences | Minimum (ms) | Median (ms) | Maximum (ms) | Result |
|---|---:|---:|---:|---:|---|
| MakeCredential | 10 | 10.2171 | 10.8334 | 12.8421 | PASS |
| GetAssertion | 10 | 6.1427 | 6.5437 | 7.0891 | PASS |
| GetInfo | 10 | 4.4153 | 4.6151 | 4.8583 | PASS |
| ResetAuthenticator | 10 | 1.0852 | 1.1980 | 1.3646 | PASS |

The Android clock-capture probe retained 1,000 samples: minimum 1,666 ns, median 1,771 ns, maximum 217,760 ns. The probe reports capture overhead and does not adjust operation measurements. Both primary (`System.nanoTime`) and secondary (`SystemClock.elapsedRealtimeNanos`) deltas and their divergence are retained per timing interval.

**Date**: 2026-10-29 UTC; Android report timestamp above.

**Limitations**: The primary monotonic delta is measured between midpoint readings around `SystemClock.elapsedRealtimeNanos`; divergence over 5 ms, unavailable secondary readings or invalid intervals make evidence incomplete. Shorter sleep and awake scheduling cannot be excluded. Instrumentation and fixture overhead are included and test-double overhead is not independently calibrated. Settings and verification policy use working fakes; MockK remains for the broad stateful credential repository and final `Fido2CryptoService` because complete faithful store/HDK implementations exceed this fixture, and unused final CTAP collaborators are isolated outside the measured GetInfo path. These substitutions are listed in the report. No device serial is retained.

**Status**: **PASS** for this Android diagnostic run. A valid device performance `FAIL` would remain diagnostic and would not fail implementation CI; missing/invalid measurements or provenance remain `INCOMPLETE`.

**Artifact link**: [Final Android machine-readable report](artifacts/android-measurement-58f5c694-0a65-40ee-befc-e8a52a8ff61a.json).

## Verification record

| Check | Command | Result |
|---|---|---|
| Focused host performance package | `.\gradlew.bat :feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.performance.*'` | 37 tests passed; zero failures, errors or skips. |
| Android behavior checks | `.\gradlew.bat :feature:fido2:connectedAndroidDeviceTest '-Pandroid.testInstrumentationRunnerArguments.class=com.chimali.fido2.performance.AndroidTimingAssessmentTest'` and corresponding report/workload classes | Clock, report, and workload checks passed on ASUS_AI2302. Direct invocation without collector provenance records an INCOMPLETE provenance envelope, as required. |
| Collector behavior and script parsing | `.\tools\tests\fido2-android-latency-tests.ps1`; PowerShell parser check for `tools/fido2-android-latency.ps1` | Passed: PASS, FAIL, INCOMPLETE, interrupted partial evidence, and provenance rejection behavior. |
| Style and static analysis | `.\gradlew.bat ktlintCheck detekt`; release lint via local CI | Passed, including the new shared and Android device test sources. |
| Full local CI and connected coverage | `.\tools\local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` | Passed in 539.61 s on the fingerprinted source; all-module compilation, unit/KMP host suites, connected-device suites and `hdkCoverageVerify` passed. Device performance threshold is not a CI completion gate. |

Coverage counters were generated at `2026-10-29T22:29:55Z` on ASUS_AI2302 / Android 15. `hdkCoverageVerify` passed all configured floors and regression checks. Tier 1 instruction/branch coverage: `core:data` 87.95%/77.03%, `core:domain` 71.33%/71.28%, `core:security` 81.40%/66.84%, and `feature:fido2` 63.48%/61.11%. App and shared-module counters are retained in `build/reports/hdk-coverage/counters.json`.

All retained reports include the run identity, attempts, raw monotonic timings, decisions, environment, source/package provenance, verification outcomes, substitutions and limitations. They contain no credentials, private keys, account identifiers, biometric information or device serial.

An earlier full-CI attempt on this same source fingerprint stopped at the host acceptance assertion because its completed measurement was FAIL; that run and the subsequent successful host assessment are both retained. The later full CI run completed every phase, including connected coverage. The device performance result is never used as a CI gate.

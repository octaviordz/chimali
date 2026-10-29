# Feature 060 Implementation Evidence

This record contains the ten fields required by TRD §9.1.

**Requirement**: TR-PERF-010; Feature-060 FR-001–FR-013; Feature-055 SC-007.

**Scope**: Declared host-simulated assessment of production `CtapHidCommandDispatcher.handleGetInfo`, `RegisterCredentialUseCase`, `GetAssertionUseCase`, `Ctap2ResetAuthenticatorHandler` and `ResetAuthenticatorUseCase`. GetInfo and MakeCredential execute inside the same registration action; GetAssertion and ResetAuthenticator execute inside the same authentication action. The fake RP independently verifies each synthetic registration/authentication response. No browser, HID/Bluetooth transport, production persistence or biometric interaction is used for latency measurement.

**Revision**: `3af1def72156551ec5366fda96dc1bb973595270+worktree-source-sha256:4c29493b2107042bedae57ea8b2ad96a37689c78f08cfc55fee5e32ed568561b` (HEAD plus SHA-256 fingerprint of measured working-tree production sources, host fixtures, and specification inputs).

**Named procedure**: `SimulatedLatencyAssessmentTest.runDeclaredAssessment`, run with `.\gradlew.bat :feature:fido2:testAndroidHostTest --tests com.chimali.fido2.performance.SimulatedLatencyAssessmentTest` from the repository root. Workload `060-host-v1`: two warmups and ten measured repetitions each for registration and authentication; all attempts retained; min/median/max are descriptive; no outlier removal; strict per-occurrence `<200 ms` comparison.

**Environment**: Windows 11 amd64; OpenJDK 64-Bit Server VM 21.0.10+1-b1163.105; real monotonic `System.nanoTime` (`jvm:System.nanoTime`). Run generated at `2026-10-29T17:48:56.618103400Z` after the native clock failure handling change.

**Expected / observed result**: Expected: every inventoried operation measured inside a fake-RP action with independent response verification, and every eligible operation below 200 ms with valid suspend-continuity evidence. Observed: 24 actions retained (4 warmups, 20 measured), all independently verified; 48 operation occurrences retained (12 per type, including warmups); all measured operation occurrences passed, and all 48 operation intervals had verified continuity. The startup warmup GetInfo occurrence took 504.5131 ms; it is retained but ineligible and excluded from the measured assessment by the declared warmup policy. Project disposition: **PASS** for this declared host-simulated assessment. These values characterize this host/runtime run, not a population distribution or physical-device round trip.

| Operation | Count | Minimum (ms) | Median (ms) | Maximum (ms) | Result |
|---|---:|---:|---:|---:|---|
| MakeCredential | 10 | 4.1698 | 5.4114 | 8.1593 | PASS |
| GetAssertion | 10 | 2.6589 | 4.5029 | 5.9946 | PASS |
| GetInfo | 10 | 1.9645 | 2.6563 | 5.0987 | PASS |
| ResetAuthenticator | 10 | 0.2030 | 0.3453 | 1.0780 | PASS |

**Date**: 2026-10-29 UTC; report generated `2026-10-29T17:48:56.618103400Z`.

**Limitations**: Registration/authentication CTAP request dispatch, HID/Bluetooth transport and physical-device experience were not traversed. GetInfo uses the production dispatcher, capability provider and response encoder with HID callbacks substituted. ResetAuthenticator uses the production handler/use case against isolated mocked storage. Credential/RP persistence and credential key derivation are synthetic adapters; JCA ECDSA signing/verification is real. The immediate UV double adds no deliberate delay, records its intrinsic execution interval, and its success/denial/cancellation/timeout/error result gates workflow execution. On Windows, interval continuity is cross-checked with `QueryUnbiasedInterruptTimePrecise`; a divergence over 5 ms or unavailable secondary-clock evidence invalidates the interval, and both deltas are retained. Process scheduling while Windows remains awake is included in real elapsed time. T085 profiling attribution and optimization remain deferred.

**Status**: Executed; PASS for all four inventoried app-internal operation types in the declared simulated workload. This replaces Feature-055 T084 as the project's relevant latency assessment; the physical target and campaign are superseded by the owner's scope decision. The three historical physical registration observations remain diagnostic context. T085 remains deferred.

**Artifact link**: [Machine-readable assessment report](artifacts/simulated-latency-assessment.json). The run also writes `feature/fido2/build/reports/fido2/060/simulated-latency-assessment.json`.

## Verification run record

| Check | Command | Result |
|---|---|---|
| Focused performance tests | `\.\gradlew.bat :feature:fido2:ktlintCheck :feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.performance.*'` | 37 tests passed; 0 failures, errors or skips. |
| Native clock failure regression | `\.\gradlew.bat :feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.performance.TimingAssessmentTest'` | Passed; verifies native loading and invocation failures become unavailable clock evidence and an incomplete report. |
| Feature style check | `\.\gradlew.bat :feature:fido2:ktlintCheck` | Passed. |
| Full local CI and connected coverage | `\.\tools\local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` | Passed in 630.8 s on the current source revision, including KtLint, Detekt, release lint, all-module compilation, unit/KMP host suites, connected device suites and `hdkCoverageVerify`. Kotlin compiler strategy was `in-process`; JaCoCo was 0.8.15. |

Coverage ran on ASUS_AI2302, Android 15. Connected results: FIDO2 25/25, app 11/11, `core:common` 1/1, and settings 3/3; all had zero failures/errors/skips. Tier 1 instruction/branch results were: `core:data` 87.95%/77.03%, `core:domain` 71.33%/71.28%, `core:security` 81.40%/66.84%, and `feature:fido2` 63.48%/61.11%. `hdkCoverageVerify` passed all configured module and repository floors/regression checks. The retained counters report was generated during this current-source run at `2026-10-29T17:50:49Z` (UTC), matching `build/reports/hdk-coverage/counters.json`.

The report artifact contains every warmup and measured action, raw monotonic endpoints, operation decisions, environment, source fingerprint, verification outcome, and declared real/simulated boundaries. It contains no credentials, private keys, real account identity or biometric information.

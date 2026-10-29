# Feature 060 Implementation Evidence

This record contains the ten fields required by TRD §9.1.

**Requirement**: TR-PERF-010; Feature-060 FR-001–FR-013; Feature-055 SC-007.

**Scope**: Declared host-simulated assessment of production `RegisterCredentialUseCase`, `GetAssertionUseCase`, `CtapHidCommandDispatcher.handleGetInfo`, and `Ctap2ResetAuthenticatorHandler` plus `ResetAuthenticatorUseCase`. Synthetic registration and authentication responses were independently verified. The GetInfo and reset operations ran as separately identified and verified operation probes; they are not represented as WebAuthn ceremonies. No physical device, browser, HID/Bluetooth transport, production persistence or biometric interaction was used.

**Revision**: `332b5515602ec78e2998162bfc2cee6eff2ca0ab+worktree-source-sha256:dcd1aef05f3c849dd904d2f47a14bb5b76597f19049b2574cce3523ebb2438eb` (HEAD plus SHA-256 fingerprint of relevant working-tree sources and specification inputs).

**Named procedure**: `SimulatedLatencyAssessmentTest.runDeclaredAssessment`, run with `.\gradlew.bat :feature:fido2:testAndroidHostTest --tests com.chimali.fido2.performance.SimulatedLatencyAssessmentTest` from the repository root. Workload `060-host-v1`: two warmups and ten measured repetitions per registration/authentication ceremony and per operation probe; all attempts retained; min/median/max are descriptive; no outlier removal; strict per-occurrence `<200 ms` comparison.

**Environment**: Windows 11 amd64; OpenJDK 64-Bit Server VM 21.0.10+1-b1163.105; real monotonic `System.nanoTime` (`jvm:System.nanoTime`). Run generated at `2026-10-29T10:55:46.599913600Z`.

**Expected / observed result**: Expected: independently verified synthetic registration/authentication and verified internal-operation probes, with each measured operation below 200 ms. Observed: 48 actions retained (8 warmups, 40 measured); all responses verified. Ten measured occurrences of each operation passed. Project disposition: **PASS** for the declared host workload. This observation is specific to this host/runtime and does not represent a population-wide or physical-device result.

| Operation | Count | Minimum (ms) | Median (ms) | Maximum (ms) | Result |
|---|---:|---:|---:|---:|---|
| MakeCredential | 10 | 1.5870 | 1.9047 | 3.3000 | PASS |
| GetAssertion | 10 | 1.6128 | 1.8830 | 2.2922 | PASS |
| GetInfo | 10 | 0.3539 | 1.0345 | 1.6235 | PASS |
| ResetAuthenticator | 10 | 0.1373 | 0.2198 | 0.3485 | PASS |

**Date**: 2026-10-29 UTC.

**Limitations**: Registration/authentication CTAP dispatch, HID/Bluetooth transport and physical-device experience were not traversed. The GetInfo probe calls the production dispatcher, capability provider and response encoder with HID transport callbacks substituted. The reset probe uses the production handler/use case against isolated mocked storage. Credential/RP persistence and credential key derivation are synthetic adapters; JCA ECDSA signing/verification is real. The immediate UV double adds no deliberate delay and records its intrinsic duration for registration/authentication. T085 profiling attribution and optimization remain deferred.

**Status**: Executed; PASS for all four inventoried app-internal operation types in the declared simulated workload. Feature-055 SC-007 is met by this assessment. Physical T084 is superseded, not completed by physical evidence; the three historical physical registration observations remain diagnostic context.

**Artifact link**: [Machine-readable assessment report](artifacts/simulated-latency-assessment.json). The run also writes `feature/fido2/build/reports/fido2/060/simulated-latency-assessment.json`.

## Verification run record

| Check | Command | Result |
|---|---|---|
| Focused performance tests | `\.\gradlew.bat :feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.performance.*'` | 31 tests passed; 0 failures, errors or skips. |
| Ktlint and Detekt | `\.\gradlew.bat :feature:fido2:ktlintAndroidHostTestSourceSetCheck :feature:fido2:detekt` | Both passed on final host-test sources. |
| Full local CI and coverage | `\.\tools\local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` | Compilation, unit/host tests and configured checks passed earlier. Connected Android coverage could not run because no device was connected; coverage remains unverified. |

The report artifact contains every warmup and measured action, raw monotonic endpoints, operation decisions, environment, source fingerprint, verification outcome, and declared real/simulated boundaries. It contains no credentials, private keys, real account identity or biometric information.

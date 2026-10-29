# Quickstart: Simulated FIDO2 Ceremony Latency

This guide describes the implemented host-simulated assessment. No physical phone, browser, Bluetooth device or human biometric action is required.

## Preparation

Review [the measurement contract](contracts/measurement-contract.md), [data model](data-model.md), and [implementation evidence](implementation-evidence.md). The assessment runs two warmups and ten measured repetitions for registration, authentication, and each internal operation probe. It calls production registration and assertion use cases directly, independently verifies their synthetic responses, and verifies GetInfo and reset probe responses.

## Current host checks

From the repository root, run:

```powershell
.\gradlew.bat :feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.performance.*'
```

The focused performance package contains 31 passing tests. The reproducible measurement entry point is:

```powershell
.\gradlew.bat :feature:fido2:testAndroidHostTest --tests com.chimali.fido2.performance.SimulatedLatencyAssessmentTest
```

It writes `feature/fido2/build/reports/fido2/060/simulated-latency-assessment.json`. The retained [assessment artifact](artifacts/simulated-latency-assessment.json) preserves the declared run. It retains 48 actions: 24 registration/authentication ceremony attempts and 24 operation-probe attempts. All 40 measured action responses verified; there are ten measured occurrences for each of MakeCredential, GetAssertion, GetInfo and ResetAuthenticator. All 40 passed the strict <200 ms operation target, so the declared assessment disposition is PASS.

All elapsed-time measurements use the real JVM `System.nanoTime` clock (`jvm:System.nanoTime`); no controlled clock is used. Credential persistence and key derivation are synthetic adapters; the test uses a synthetic host key pair and actual JCA signing and verification. GetInfo exercises the production command dispatcher, capability provider and CTAP response encoder, with HID transport callbacks substituted. ResetAuthenticator exercises the production handler and use case against isolated mocked storage. Registration/authentication CTAP dispatch, HID/Bluetooth transport, production persistence and the physical-device workflow are not traversed. The pass applies only to this declared host workload and is not a physical latency result.

## Behavior checks

1. Run the command above from the repository root. The test executes both ceremonies and both operation probes using a predeclared workload and writes machine-readable results.
2. Inspect each operation occurrence, its raw endpoints, exclusion intervals, assessed duration and decision. Do not treat the full simulated round trip as the per-operation duration.
3. Review operation probes, substitutions, limitations and disposition in the report and [implementation evidence](implementation-evidence.md).
4. Re-run the broader applicable host/style/static/coverage gates before treating code changes as fully verified. All reported elapsed durations use a real monotonic clock.

## Monotonic-clock measurement

1. The runner declares two warmups and ten measured attempts per ceremony and per operation probe, descriptive min/median/max summaries, and a retain-all outlier policy.
2. Ceremony timing spans fake RP request issuance through independent response verification. Each internal operation has its own real monotonic interval; the user-verification double's intrinsic execution time is separately recorded for registration and authentication and no deliberate delay is introduced.
3. Reports retain every action and attempt. This run had no failed, denied, cancelled, timed-out or unverified measured actions; warmups are retained but excluded from decision summaries.
4. The declared workload measured all four inventoried operation types and passed. T085 profiling/optimization remains separately deferred.





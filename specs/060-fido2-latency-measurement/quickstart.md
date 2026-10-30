# Quickstart: Simulated FIDO2 Ceremony Latency

## Android device diagnostic

The Android run executes the same fake-RP registration and authentication workload on one selected device. It retains two warmups and ten measured attempts for each ceremony, independently verifies each response, and records GetInfo, MakeCredential, GetAssertion and ResetAuthenticator timings. User verification is immediate and simulated; no browser, Bluetooth transport, physical authenticator or biometric prompt is exercised.

Connect and authorize one Android device, then run from the repository root:

```powershell
.\tools\fido2-android-latency.ps1 -Serial R7AIB7002901AZ3
```

The collector builds and runs the instrumentation test, supplies a source fingerprint and unique run ID, extracts the AndroidX test-storage report, verifies the report against the built test package SHA-256, and writes a separate artifact at `specs/060-fido2-latency-measurement/artifacts/android-measurement-{run-id}.json`. It requires an explicit `-Serial` when multiple authorized devices are connected. The collector stores no device serial. On an interrupted or invalid run it retains an `INCOMPLETE` artifact and includes partial attempts when the device or AndroidX output makes them recoverable.

The primary elapsed duration is the real `System.nanoTime` delta using midpoint readings around `SystemClock.elapsedRealtimeNanos`. The report retains both clock deltas and their divergence. Missing/invalid secondary evidence or absolute divergence above 5 ms makes timing incomplete. A separate 1,000-sample clock-capture probe reports its min/median/max overhead; measured durations are not adjusted. Shorter sleeps and awake scheduling remain limitations.

A valid device occurrence at or above 200 ms makes that device assessment `FAIL` but does not fail implementation CI or replace the host acceptance result. Missing provenance, invalid measurements or collection defects produce `INCOMPLETE`. See the [final device report](artifacts/android-measurement-58f5c694-0a65-40ee-befc-e8a52a8ff61a.json) and the detailed [implementation evidence](implementation-evidence.md).

## Preparation

Review [the measurement contract](contracts/measurement-contract.md), [data model](data-model.md), and [implementation evidence](implementation-evidence.md). The assessment runs two warmups and ten measured repetitions each for registration and authentication. Each fake-RP action includes its internal operations under the same action identity and is independently verified.

## Current host checks

From the repository root, run:

```powershell
.\gradlew.bat :feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.performance.*'
```

The focused performance package command runs all behavior and report checks. The reproducible measurement entry point is:

```powershell
.\gradlew.bat :feature:fido2:testAndroidHostTest --tests com.chimali.fido2.performance.SimulatedLatencyAssessmentTest
```

The focused performance package has 37 passing tests. Full repository verification passed with:

```powershell
.\tools\local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage
```

This run passed style/static checks, release lint, all-module compilation, unit and KMP host suites, connected coverage on ASUS_AI2302 (Android 15), and `hdkCoverageVerify`. The local CI script supplies Kotlin's required `in-process` compiler strategy to the coverage gate. Detailed device and module coverage results are in [implementation evidence](implementation-evidence.md).

It writes `feature/fido2/build/reports/fido2/060/simulated-latency-assessment.json`. The retained [assessment artifact](artifacts/simulated-latency-assessment.json) preserves the declared run. It retains 24 fake-RP actions: 4 warmups and 20 measured attempts. Each registration action measures GetInfo and MakeCredential; each authentication action measures GetAssertion and ResetAuthenticator. The report retains ten measured occurrences for each operation type, all associated with their enclosing verified action.

All elapsed-time measurements use the real JVM `System.nanoTime` clock (`jvm:System.nanoTime`); no controlled clock is used. Credential persistence and key derivation are synthetic adapters; the test uses a synthetic host key pair and actual JCA signing and verification. GetInfo exercises the production command dispatcher, capability provider and CTAP response encoder within registration, with HID transport callbacks substituted. ResetAuthenticator exercises the production handler and use case within authentication against isolated mocked storage. Registration/authentication CTAP dispatch, HID/Bluetooth transport, production persistence and the physical-device workflow are not traversed. This is an app-internal simulated assessment and does not claim physical latency.

## Behavior checks

1. Run the command above from the repository root. The test executes registration and authentication, including all four internal operations, using a predeclared workload and writes machine-readable results.
2. Inspect each operation occurrence, its raw endpoints, exclusion intervals, assessed duration and decision. Do not treat the full simulated round trip as the per-operation duration.
3. Review operation-to-action associations, substitutions, limitations and disposition in the report and [implementation evidence](implementation-evidence.md).
4. Re-run the broader applicable host/style/static/coverage gates before treating code changes as fully verified. All reported elapsed durations use a real monotonic clock.

For the device diagnostic, inspect the `assessment.operation_summaries` and every measured attempt in the linked Android report. Check the device metadata, source fingerprint, test package and APK SHA-256, verified responses, clock continuity, operation decisions, and recorded substitutions. Device performance disposition and host acceptance are separate results.

## Monotonic-clock measurement

1. The runner declares two warmups and ten measured attempts per ceremony, descriptive min/median/max summaries, and a retain-all outlier policy.
2. Ceremony timing spans fake RP request issuance through independent response verification. Each internal operation has its own real monotonic interval; the user-verification double's intrinsic execution time is separately recorded for registration and authentication and no deliberate delay is introduced.
3. Reports retain every action and attempt. The verification double's outcome gates use-case execution; unsuccessful measured actions remain in the report and make the project assessment incomplete. On Windows, interval endpoints compare `System.nanoTime` with the independent `QueryUnbiasedInterruptTimePrecise` clock. More than 5 ms divergence or unavailable continuity evidence makes the interval incomplete; the independent clock is only a validity check and does not supply reported elapsed time. Other host operating systems fail closed with incomplete continuity evidence.
4. Check that every operation type has the declared number of eligible measured occurrences and that failed/incomplete attempts are surfaced. T085 profiling/optimization remains separately deferred.





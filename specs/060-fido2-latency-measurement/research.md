# Research: Simulated FIDO2 Ceremony Latency

## Android device extension

**Decision**: Compile the shared test-only ceremony, fixture, RP verifier and report calculations from `src/androidSharedTest/kotlin` into both Android host and device test trees. Isolate platform clock providers and use the project's JUnit 4 device runner. Settings and verification policy use working fakes; MockK is limited to the broad repository, final HDK-backed crypto service and unused final CTAP dispatcher collaborators. Run the same four-operation inventory with two warmups and ten measured repetitions per ceremony. Preserve the separate host acceptance result.

**Rationale**: A shared workload makes the device result interpretable against the existing boundaries while avoiding divergent report logic. The host fixture currently depends on JVM MockK, the clock imports Windows JNA and the runner reads Git and local files, so device support requires explicit platform seams. Instrumentation and mock overhead remain part of the measured device environment and must be disclosed.

**Alternatives considered**: Duplicating the fixture and calculation risks drift; running the existing host test on a desktop while a phone is attached does not measure Android execution. A browser/Bluetooth campaign would measure a different workflow and remains out of scope.

**Decision**: Report real `System.nanoTime` durations on Android and compare endpoint deltas with `SystemClock.elapsedRealtimeNanos()`, sampled between two primary-clock readings. Use the primary midpoint and retain both deltas and secondary-minus-primary difference. Absolute divergence over 5 ms, a missing secondary reading or invalid interval makes the evidence incomplete. A 1,000-sample clock-probe estimate reports min/median/max without correcting operation times. Short sleeps below the tolerance, awake scheduling and uncalibrated test-double overhead are limitations. See [Android SystemClock documentation](https://developer.android.com/reference/android/os/SystemClock).

**Rationale**: Both sources are real monotonic clocks with distinct sleep behavior. The secondary source is continuity evidence, not the reported elapsed duration; alignment and scheduling limit detection precision.

**Alternatives considered**: The Windows-only suspend check is unavailable on Android. An unchecked primary clock cannot substantiate the planned invalid-interval handling.

**Decision**: The PowerShell runner identifies exactly one authorized device and binds a deterministic source fingerprint, run ID and built test-package identity to the run. The instrumented test writes every completed ceremony attempt to private storage and AndroidX `PlatformTestStorage`; Gradle extracts the separate report. The host verifies the source fingerprint, package, and built APK SHA-256 before retaining it. On disconnect, crash, absent provenance or extraction failure, record an incomplete host-side run manifest and retain partial evidence when available. See [Android PlatformTestStorage](https://developer.android.com/reference/androidx/test/platform/io/PlatformTestStorage).

**Rationale**: Android cannot rely on the host runner's Git process or working directory; an explicit handoff and durable extraction make the origin and interruption status reviewable. A device result must never silently replace the existing host report.

**Alternatives considered**: Log-only evidence may be truncated or lost; treating a missing report as success conceals interrupted execution.

**Decision**: A valid Android occurrence at or above 200 ms is a device assessment FAIL and remains visible as diagnostic evidence, but that performance result alone does not prevent completion of the Android measurement work or replace the host acceptance assessment. The runner and its correctness checks still detect and report infrastructure defects, invalid evidence and incomplete collection independently.

**Rationale**: The owner chose diagnostic device performance while retaining host acceptance. Separating measurement validity from the measured outcome permits a useful report even when the device is slower than the comparison target.

**Alternatives considered**: Failing the measurement workflow solely on the device threshold would make device speed an additional completion gate; treating an over-threshold result as PASS would misrepresent the measurement.

## Measurement boundary

**Decision**: The principal feature measurement starts when a fake relying party issues a synthetic request and ends after it receives and independently verifies the response. Retain the raw duration and report exactly which real and simulated components it covers. Measure app-internal processing separately where supported.

**Rationale**: The user wants repeatable insight into Chimali's work without waiting for external systems or a person. The simulated round trip also includes any fake relying-party and simulated transport work, so it cannot automatically be described as pure app processing.

**Alternatives considered**: A physical browser-to-RP campaign has different scope and requires hardware and human interaction; treating simulated round trip and internal operation timing as identical conceals overhead.

## User-verification double

**Decision**: Supply explicit success, denial, cancellation, timeout and error outcomes at the user-verification boundary without a real biometric prompt; introduce no deliberate delay, record intrinsic test-double execution duration for each invocation, and distinguish that interval from internal work. Record `not invoked` with no duration when an attempt ends before invocation.

**Rationale**: Repeatable interaction removes real human delay while still exercising application control flow. The double is not evidence about platform biometric behavior.

**Alternatives considered**: Physical verification introduces variable external waiting; bypassing the application's verification decision entirely can leave the desired workflow unexercised and must be disclosed if unavoidable.

## Clock and benchmark scope

**Decision**: Use a real monotonic clock for every elapsed-time measurement, including the ceremony and intrinsic test-double execution. Predeclare the workload, warmup, repetitions and summary. Preserve failures and outliers and identify environmental variability.

**Rationale**: A real monotonic clock measures elapsed time while the declared host workflow runs. Host scheduling, runtime warmup, fake-RP verification, and simulated transport all affect the result. No defensible host threshold follows automatically from the current physical target.

**Alternatives considered**: Timing only mocked operations would omit relevant execution; unexplained broad pass thresholds are unreliable.

## Existing code and amended obligation

**Decision**: Inspect existing FIDO2 host tests, handlers and lifecycle seams to find the smallest path using real production logic; use synthetic independent RP verification and minimal doubles. Record all unavailable components. Preserve historical ASUS evidence; mark T084 superseded and retain the separate T085 profiling/optimization deferral.

**Rationale**: Existing host tests include real handler fixtures and mocked transport/user-interaction collaborators, but their presence does not demonstrate the new end-to-end measurement. The existing `BaselineMeasurements` internal metric and the physical RP-to-verified-response metric have distinct boundaries. The owner-authorized 2026-10-29 amendment retires the physical target and makes the simulated internal-operation assessment authoritative. The strict <200ms internal target remains unchanged; the simulation does not establish physical device timing.

**Alternatives considered**: The physical target was retired through explicit owner authorization and synchronized governance amendment; physical timings remain diagnostic and must not be presented as compliance.






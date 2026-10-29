# Data Model: Simulated FIDO2 Ceremony Latency

## Workload Definition

Version, ceremony type, synthetic fixture, fake RP request and independent verification, production components exercised, substituted transport/platform and verification dependencies, declared verification outcome and no-deliberate-delay policy, monotonic clock source, runtime/host, warmup, repetitions, summary statistic, existing strict <200ms per-operation threshold, and measurement boundaries. Any workload change is recorded and prevents unsupported direct comparison.

## Ceremony Attempt

Unique run and attempt IDs; ceremony type; outcome (`verified`, `rejected`, `cancelled`, `failed`, `timed_out`, or `incomplete`); synthetic request and response evidence without secret material; fake-RP issuance and verification-completion milestones; test-double invocation state and, for each invocation, execution start/end, intrinsic elapsed duration and user-verification outcome; app-internal processing endpoints where available; clock domain and unrounded elapsed values; invalidity and missing-data reasons. An attempt ending before invocation records `not invoked` and has no test-double execution duration, not a zero duration. Only an independently verified response qualifies as a verified sample. All attempts, including warmup, failures and outliers, are retained and labeled.

## Timing Assessment

Raw simulated round trip is response-verification completion minus request issuance using the same valid monotonic clock. It includes fake-RP and substituted transport work as documented. Each internal operation is a separately bounded interval. Its assessed duration is its raw duration minus the union of measured overlap with verification-test-double execution and Bluetooth response handoff. Retain the raw endpoints, exclusions and calculation. Missing, negative, out-of-order, incompatible or ambiguous decision-relevant data are incomplete, never clamped to a pass. Compare each eligible occurrence at unrounded clock precision: strictly below 200 ms passes; 200 ms or above fails. Retain and label warmups separately according to the predeclared eligibility policy. On Windows, each interval also records the delta from `QueryUnbiasedInterruptTimePrecise` and its divergence from the `System.nanoTime` delta; divergence over 5 ms, unavailable secondary-clock evidence, or an invalid primary interval makes the interval incomplete. This secondary clock validates continuity but never supplies the reported duration. All elapsed durations use a real monotonic clock and describe the actual measured host runtime.

## Measurement Report

Contains workload definition, inventory of all implemented operation types, source revision, test and runtime environment, sample chronology, raw results, declared summary/statistic, comparisons if justified, failures and outliers, measurement limitations and real/simulated boundary map. Report failures and incomplete evidence separately. A complete pass requires eligible measurements for every implemented operation type and every eligible occurrence to pass; unmeasured types make the project assessment incomplete. Evidence is synthetic and durable beyond the run. TRD §9.1 verification records include requirement, scope, revision, named_procedure, environment, expected_observed_result, date, limitations, status and artifact_link. Physical T084 is superseded by this simulated assessment. T085 profiling attribution and optimization remain deferred and are not claimed complete by measurement alone.







# Investigate parser failures in FIDO2 calibration exploration

**Status:** Open follow-up; cause unknown.  
**Origin:** Feature 062 lower-overhead calibration Exploration 07.

## Finding

The approved CPU-load exploration completed its synthetic workload and recorded 90 lookup markers. All 45 enabled fixed-lifecycle lookups had eight lifecycle slices, but Trace Processor reported 81 `systrace_parse_failure` errors. The run failed its trace-health gate, so no timing distribution from it was accepted. Flush and CPU-overrun counters were zero, and raw-trace cleanup passed.

The retained aggregate also contains 81 unclassified print shapes and 11 low-memory candidates. Those counts do not establish which events caused the parser errors. The raw trace was removed under the approved retention procedure, so this run cannot be re-examined for event-level context. The failure is an instrumentation/analysis investigation signal, not evidence of an app defect.

## Next work

1. Review the [parser-failure review](../../specs/062-fido2-performance-investigation/artifacts/f062-overhead-explore-07-cpu-parser-failure-review.md), its retained aggregate outputs, and the [Exploration 07 protocol](../../specs/062-fido2-performance-investigation/artifacts/f062-overhead-exploration-protocol-07.md). Determine whether further diagnosis is useful for a future measurement method.
2. Check what event-level detail the pinned Trace Processor can provide and design a diagnostic that retains only the information needed, consistent with the investigation's synthetic-data and raw-trace cleanup requirements. State explicitly if the available evidence cannot establish a cause.
3. If a new device trace is needed, prepare a fresh prospective declaration and identity and follow the applicable device and capture authorization. Preserve the nonzero trace-health failure rule and retain any failed or incomplete attempt.
4. If a lower-overhead calibration is later pursued, validate its method, identity and independent holdout separately. Exploration 07 cannot be used as a calibration bound or a baseline sample.

## Completion

Record a supported diagnosis or an explicit inconclusive disposition, the retained evidence and a decision on whether further calibration work is warranted. This follow-up does not reopen Feature 062 or change its absolute 20 ms result, which remains inconclusive.

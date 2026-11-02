# Data Model: FIDO2 Internal Performance Investigation

## Investigation run

Fields: unique run ID, phase (baseline or after-change), revision and dirty-source fingerprint including settings repository, trace instrumentation and build inputs, APK identity/hash, device model/OS, workload and settings state, capture configuration, sampling and materiality policy declared before capture, warm-session grouping/pooling rule and equivalence review when applicable, start/end, warnings, artifact locations and disposition. Host-simulated assessment is a separate linked run.

States: `declared → collecting → complete | incomplete`; complete requires all predeclared eligible samples and valid provenance. Interrupted or unavailable capture is incomplete, retains recoverable records and does not close T085.

## Registration attempt

Fields: run ID, sequence, cold/warm/warmup classification, process session, operation identity, explicit operation-to-trace-cookie mapping, serialized attempt-window observations, request/verification outcome, lookup and adjacent section endpoints/durations, linked thread/coroutine scheduling events, running/runnable/suspended/thread-sleeping/thread-uninterruptible-sleep/unknown partition with raw endpoints, calibration ID, empirical allowance, 1x/2x sensitivity intervals, internal operation interval and exclusions when available, trace validity/loss warnings, and eligibility/rejection reason. An attempt belongs to exactly one run. A warm process session has one uncounted warmup and no more than seven counted warm attempts; observations from multiple sessions count toward ten eligible warm intervals only under a schedule and pooling/equivalence rule declared before new collection. Only unambiguously associated, successful independently verified attempts with complete valid trace evidence count toward the declared sample quota; all others remain recorded. Nonzero unknown partition or missing or invalid empirical calibration prevents a conclusive disposition.

## Investigation decision

Fields: evaluated run(s), per-group raw observations, median and maximum, declared threshold, calibration/allowance provenance, classifications at both scales, elapsed classification (`material`, `not-material`, `inconclusive`), scheduling/attribution validity, limitations, further profiling, decision (`change`, `no-change`, `open`), and verification references. Either group incomplete or inconclusive makes the overall result inconclusive and the decision `open`; when both are determinate, either material group makes the overall result material, and two not-material groups make it not-material. Unavailable direct measurement or unresolved decision-relevant scheduling uncertainty also means `open`. A change requires a measured attributed lead and a comparable before/after record. A no-change decision requires conclusive evidence under the declared policy. Neither classification represents a device acceptance pass or proves the cause of the historical sample.

## Verification record

Use the existing TRD §9.1 fields: requirement, scope, revision, named procedure, environment, expected/observed result, date, limitations, status and artifact link. Link raw attempt evidence and behavior/host acceptance reports. No credential, private key, account identifier, biometric data or device serial appears in durable artifacts.

## Backlog closure record

Fields: conclusive investigation and applicable implementation/verification dispositions, retained historical evidence target, reference inventory and classifications, link/status updates, deletion result and final audit. Closure is allowed only after the other required dispositions are complete and preserved evidence is accessible. Historical plain-text mentions and instructions naming the old path may remain; dangling links and stale current-status claims may not. An unresolved condition keeps the backlog and the closure record open.

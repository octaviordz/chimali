# Backlog: Investigate repeated warm FIDO2 security-key registration failures

**Status:** Open — failure layer and reproducibility are not established  
**Created:** 2026-11-01  
**Related work:** [Feature 062 production-path investigation](../../specs/062-fido2-performance-investigation/spec.md), [T009 task status](../../specs/062-fido2-performance-investigation/tasks.md), and [FIDO2 internal performance backlog](fido2-internal-performance-enhancements.md)

## Observed issue

Two ASUS_AI2302 / Android 15 synthetic registration runs show the same warm-process sequence. In each run, one uncounted warmup and warm-01 through warm-07 completed successfully in the same app process. The next counted attempt, warm-08, failed. That is eight successful registrations in the warm process before the next attempt failed; warm-08 is the eighth counted warm attempt and the ninth registration after the warm process began.

- **Baseline 02:** warm-08 through warm-11 failed before an app registration operation was captured. The RP reported `NotAllowedError` for the failures. The user reported the Windows message `No se puede leer la clave de seguridad. Vuelve a intentarlo` for warm-11. Two schedule slots remained, which could not produce the ten-sample warm quota. See the [run disposition](../../specs/062-fido2-performance-investigation/artifacts/f062-asus35-baseline-02-run-disposition.md) and [attempt progress](../../specs/062-fido2-performance-investigation/artifacts/f062-asus35-baseline-02-run-progress.md).
- **Baseline 03:** warm-08 was canceled after the same Windows message. The trace had no registration markers, operation mapping, or terminal; scheduler, clock and health evidence were complete, with clean raw-file cleanup. Its declaration required stopping on incomplete marker/correlation evidence, so the run stopped at 7/10 eligible warm samples. See the [warm-08 disposition](../../specs/062-fido2-performance-investigation/artifacts/f062-asus35-baseline-03-warm-08-disposition.md) and [run disposition](../../specs/062-fido2-performance-investigation/artifacts/f062-asus35-baseline-03-run-disposition.md).

This is a repeated association in two runs, not evidence of a fixed eight-registration limit or a known cause. The tests used the same device, host/browser and local RP path; they are not broad independent replications. Because the failed traces contain no app registration markers or operation mapping, the records do not establish that the request reached Chimali's registration code. The RP error and Windows message also do not, by themselves, distinguish a Windows security-key broker, browser WebAuthn/CTAP path, authenticator transport, RP ceremony state, timeout, cancellation, or other cause.

## Investigation goal

Determine whether the failure is reproducibly associated with warm-process attempt count and identify the failing layer or retain an explicit inconclusive result. Record both the counted attempt number and total successful registrations in that process, including the uncounted warmup. Do not treat this issue as a settings-lookup performance result or use it to infer the cause of the measured lookup duration.

## Investigation procedure

1. Review all retained Baseline 02 and Baseline 03 attempts, RP outcomes, operator-reported Windows messages, app markers, mappings, process IDs, connection state and trace-health evidence. Keep operator reports distinct from machine observations.
2. Before any new device registration, predeclare the synthetic origin, build/device identity, process-state sequence, attempt cap, evidence to retain, eligibility rules, failure dispositions and stop rules. Capture one operation at a time. Obtain owner approval for the exact declaration; existing Baseline 02/03 approval does not authorize another series.
3. For each attempt, retain the warm attempt number, number of successful operations already completed in that process, RP result, exact user-reported Windows outcome, Android app/connection state, operation markers, correlation, trace-health, and cleanup result. A missing app marker is evidence of missing app-path observation, not proof of a particular external cause.
4. If comparing cold and warm behavior, change only the predeclared process-state condition. Do not clear app data, remove stored credentials, reset the RP, or change the origin to manufacture a passing run. Stop on identity drift, ambiguous app dispatch/correlation, instrumentation or health failure, or cleanup failure.
5. Do not change production behavior until evidence localizes the failure and a bounded correction has a clear preservation requirement for credentials, user data, authentication security and cancellation behavior.

Coordinate device work with Feature 062 T009 so reliability evidence and performance samples remain separately labeled. A diagnostic reliability attempt cannot silently satisfy the T009 quota. Baseline 04 was approved and then stopped at cold-04 after an incomplete capture timeout. It collected three eligible cold samples and no warm samples; it does not assess or resolve this warm-specific reliability pattern. Any further capture needs a fresh reviewed declaration and renewed owner approval. See the [Baseline 04 run disposition](../../specs/062-fido2-performance-investigation/artifacts/f062-asus35-baseline-04-run-disposition.md).

## Completion criteria

- Preserve an attempt-by-attempt record for every declared run, including failures, cancellations, missing markers and any stopped/incomplete result.
- Determine whether the same attempt-count pattern recurs under the declared conditions, or state why the available data cannot resolve it.
- Attribute the failure to a component/layer only when supported by direct evidence; otherwise list the unresolved candidates and the additional evidence needed without assigning blame.
- Record a decision to fix, monitor, or leave the behavior unresolved. Any production change requires configured behavior tests and verification appropriate to its scope.

## Current disposition

The two existing runs support a repeated warning at warm-08 after eight successful warm-process registrations, but do not establish a causal threshold. Baseline 03 remains stopped and incomplete. No new reliability-only capture is authorized by this backlog item.

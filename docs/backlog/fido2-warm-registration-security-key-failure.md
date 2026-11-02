# Investigate repeated warm FIDO2 security-key registration failures

**Status:** Open; cause unknown. Keep separate from the SettingsLookup performance investigation.

## Observed behavior

During synthetic WebAuthn registration attempts on ASUS_AI2302 through the local RP, repeated warm-process attempts have sometimes failed after several successful registrations. Reported Windows messages include “No se puede leer la clave de seguridad. Vuelve a intentarlo.” One earlier attempt also surfaced a browser `NotAllowedError`. These are user-visible outcomes, not a diagnosis of the component that failed.

Evidence has now recurred in three stopped production-path series:

- Baseline 02 had seven eligible warm registrations; subsequent warm attempts reported RP `NotAllowedError` and Windows security-key read errors.
- Baseline 03 had seven eligible warm registrations; warm-08 was canceled after the Windows security-key read error and lacked registration markers, operation mapping, and terminal evidence.
- Baseline 09 had seven eligible warm registrations; warm-08 was reported with the same Windows message. Its retained result has `rp_outcome=not_verified`, zero registration markers/mappings/terminal events, zero nonzero health counters, and verified raw-trace cleanup.

The repeat at warm-08 is a useful investigation signal, not proof of a threshold at eight registrations. The available attempts do not identify whether the source is Windows/browser, the authenticator transport, the app/service, user cancellation, or another transient condition. Trace health being clean does not establish where the WebAuthn ceremony failed.

## Investigation procedure

1. Review the exact stopped-run declarations, runner results, and operator messages before proposing another device series. Preserve each failed attempt and its original schedule slot; do not retry or replace it.
2. Form a hypothesis that can be distinguished using non-secret diagnostics. Prefer a short, separately declared synthetic diagnostic with explicit stop rules over another full baseline.
3. Capture timestamped RP/browser outcome, Android app/service state, and permitted transport/system diagnostics around each single request. Keep the local RP, app build, device identity, and process condition fixed for the diagnostic.
4. Determine whether the failure occurs before dispatch, during the authenticator ceremony, or after a response reaches the RP. Do not infer this boundary from the Windows message alone.
5. Compare successful and failed attempts only when their event sources and correlation are complete. Report missing evidence and competing explanations; do not turn a diagnostic into baseline data.
6. Obtain a fresh explicit approval before any new on-device registration or biometric prompt.

## Current references

- [Baseline 02 run disposition](../../specs/062-fido2-performance-investigation/artifacts/f062-asus35-baseline-02-run-disposition.md)
- [Baseline 03 run disposition](../../specs/062-fido2-performance-investigation/artifacts/f062-asus35-baseline-03-run-disposition.md)
- [Baseline 09 run disposition](../../specs/062-fido2-performance-investigation/artifacts/f062-asus35-baseline-09-run-disposition.md)
- [Baseline 09 warm-08 evidence](../../specs/062-fido2-performance-investigation/artifacts/f062-asus35-baseline-09-warm-08-disposition.md)

This item is a separate reliability investigation from the cold settings lookup performance follow-up. Its closure requires a defensible failure-boundary finding or an explicit decision that the cause cannot be isolated with available instrumentation; recurrence alone is not closure.

## Performance-sampling response (2026-11-01)

Feature 062 now limits future warm performance samples to seven counted attempts per process after an uncounted warmup, with the original ten-eligible-warm quota preserved across prospectively declared sessions. This avoids deliberately repeating the observed warm-08 position during performance sampling. It does not identify the registration failure boundary or cause and does not close this reliability investigation. See the [sampling amendment](../../specs/062-fido2-performance-investigation/artifacts/f062-warm-session-sampling-amendment-2026-11-01.md).

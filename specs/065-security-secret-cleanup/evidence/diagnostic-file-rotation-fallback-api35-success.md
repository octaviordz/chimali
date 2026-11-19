# Configured diagnostic rotation and fallback on API 35

| Field | Result |
|---|---|
| Requirements | FR-008/009/013/014; T029/T034/T035 |
| Procedure | `ConfiguredFidoDiagnosticPrivacyTest#configuredFileWriterRotationAndFallbackOmitSyntheticValuesAndFailures` |
| Device | API 35 Android ATD x86_64 emulator `emulator-5554`; no physical device used |
| Revision | Dirty exploratory worktree; not a frozen product candidate |
| Date | 2026-11-17 |
| Run | `SEC065_RUN_ROTATION_FALLBACK_EVIDENCE_20261117` |

## Changes that made the case executable

The `clipboardAudit` flavor now uses a minimal `ClipboardAuditApplication` that starts only the clipboard Koin module. The prior full application initialization exceeded the emulator's process-start window in repeated instrumentation launches; the minimal audit graph allowed the target and instrumentation to start. Production variants continue to use `ChimaliApplication`.

The test previously used a 256-byte rotation threshold, but `PrivacyLogScrubber` replaced its long synthetic padding token and reduced the final record below that threshold. The test now uses a 64-byte threshold, below the fixed timestamp/throwable record overhead, so the second write deterministically rotates the first entry.

## Result

Built and installed only the disposable `clipboardAudit` target and Android test packages on `emulator-5554`. A serial-scoped, method-filtered invocation passed: **OK (1 test)**. The test checked that both the active and rotated local files contained the redacted password and fixed throwable wording with no synthetic marker, and that a filesystem path blocker produced only the fixed fallback message without marker, private path, or exception details. The test's `finally` removed its isolated synthetic directory. Both disposable packages were then uninstalled.

Target APK SHA-256: `7b17e547cd04eb4edcd1b83cb64dc1ab9cf0aa7d978b7b9c3987cc61eedb0b87`. Android test APK SHA-256: `a76b7a65efa74e89b37ff1b28d1b2b0f30adc370ecfa1dbb858667bcbf664911`.

The raw sanitized instrumentation output (`OK (1 test)`, no synthetic payload values) is retained at `build/security/clipboard-observation/configured-rotation-fallback-api35-20261117.txt`, SHA-256 `33f2236d3e7f831b93cdc5c6b0e3cb0627fa7fe5ad0a0a206658244e2683d849`.

## Limits

This passes the controlled writer rotation/fallback assertions for the debuggable `clipboardAudit` variant on one API 35 emulator. It does not establish release-variant rotation/fallback, all configured diagnostic destinations, other failure modes, iOS behavior, full source coverage, heap behavior, or frozen-candidate status. The earlier startup failures and missing-argument/threshold failures are retained in `diagnostic-file-rotation-fallback-api35.md`; they are not promoted to passes.

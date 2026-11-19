# Diagnostic file rotation and fallback probe

| Field | Result |
|---|---|
| Requirement | FR-008/009/013/014; T029/T034/T035 |
| Procedure | `ConfiguredFidoDiagnosticPrivacyTest#configuredFileWriterRotationAndFallbackOmitSyntheticValuesAndFailures` |
| Device | API 35 Android ATD x86_64 emulator `emulator-5554`; no physical device used |
| Revision | Dirty exploratory worktree based on `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57` |
| Date | 2026-11-16 local |

## Test added

The instrumentation case constructs the production `LocalCrashReportingLogWriter` with an isolated app-private directory and a small rotation threshold. It is designed to require both the active log and `.bak` log to contain only scrubbed synthetic password text and fixed throwable wording. A second isolated directory uses a regular file as a path blocker and captures `System.err`; assertions require the writer's fixed fallback and reject marker, path, and exception details. The run directory is removed in `finally`.

The new instrumentation source compiles for `release` and `clipboardAudit`, and app Android-test KtLint passes. The audit target/test APKs were built successfully; target SHA-256 `3f2911bcdf2d6c52e69ac6e3494eb16d0999d6d47a04dec9c849cf943ee3150e`, instrumentation SHA-256 `1ef8a3f1e5dd27d8783a1b12407eac88dc3bc35aa865964f6337e93045f1194c`.

## Runtime attempts and result

The `clipboardAudit` target/test APKs were installed only on API 35 emulator `emulator-5554`. Three serial-scoped invocations of the method-filtered test failed before the test body ran because Android reported that `com.chimali.clipboardaudit` failed to complete process startup and terminated the instrumentation for an ANR. The dedicated `security-audit-SEC065_RUN_ROTATION_FALLBACK_20261117` directory was absent afterward, confirming that the body did not create or publish its synthetic marker. These attempts are **Unverified**, not passing assertions.

On 2026-11-17, after rebuilding the current `clipboardAudit` target and test APKs, one further direct serial-scoped invocation returned `INSTRUMENTATION_RESULT: shortMsg=Process crashed` before any test result. The target's scoped `security-audit-*` directory was absent; both temporary packages were uninstalled after the attempt. No diagnostic assertions ran, so the rotation/fallback case remains **Unverified**. The APK hashes were target `8f447db260bdc3b142a09316fb1f81d1bd670215e838602e547c928f55e88a2d` and test `1ef8a3f1e5dd27d8783a1b12407eac88dc3bc35aa865964f6337e93045f1194c`.

Two earlier release-variant development runs also did not execute the test body: one found the writer interface had been removed by R8, and the next found an optimized Okio companion missing across the target/test classloader boundary. The test was then changed to use the production `AndroidLogDirectoryProvider` with a scoped `ContextWrapper`; the final source compiles, but a release runtime rerun has not been performed. Narrow R8 keep rules now preserve the writer interface and provider ABI needed by the instrumentation call.

The existing host suite covers writer rotation and fixed fallback behavior, and earlier instrumentation covers configured current-file redaction. These failed attempts are not promoted to runtime evidence. A later API 35 `clipboardAudit` run passed after isolating its Application startup and fixing the scrub-aware rotation threshold; see `evidence/diagnostic-file-rotation-fallback-api35-success.md`. That success is limited to the debuggable audit variant. Release-variant rotation/fallback and the broader diagnostic matrix remain open under T029/T034/T035.

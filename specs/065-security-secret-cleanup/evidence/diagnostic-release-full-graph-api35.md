# Diagnostic writer verification with the full release application graph

**Date:** 2026-11-17  
**Finding:** SEC065-LOG-055  
**Requirements:** FR-008/009/013/014; T029/T034/T035  
**Device:** API 35 Android ATD emulator `emulator-5554`  
**Revision:** Dirty exploratory worktree; not a frozen product candidate

## Procedure

Built the isolated `diagnosticRelease` variant from the production `release` build type, with a unique application ID suffix `com.chimali.diagnosticrelease`. It retains `ChimaliApplication` and the full production dependency graph, minification, resource shrinking, release rules, and non-debuggable status. The unique package identity prevents interaction with an installed product package.

Installed only the target and instrumentation APKs on `emulator-5554`. `dumpsys package` reported `flags=0x0`. Ran both methods in `ConfiguredFidoDiagnosticPrivacyTest` with synthetic marker/run ID arguments and `expectedDebug=false`:

- `configuredWritersRedactSyntheticValuesAndOmitThrowableDetails`
- `configuredFileWriterRotationAndFallbackOmitSyntheticValuesAndFailures`

The startup/configuration case verified app-private INFO/WARN/ERROR records, fixed throwable wording, and absence of the synthetic marker. The writer case verified active/rotated files, plus fixed fallback output for a blocked log-directory path with no marker, path, or exception detail.

## Result and limits

Instrumentation output: **OK (2 tests)**. The test results cover this non-debuggable, minified, release-derived variant and full application startup graph on one API 35 emulator. This is not a signed product release candidate and does not cover all diagnostic sources, platform Logcat retention, other devices, iOS, or the full T029/T034/T035 matrix. Both isolated packages were uninstalled; `pm path` returned no installed path for either ID. No product package or user data was modified.

## Artifacts

- Sanitized output `build/security/clipboard-observation/diagnostic-release-full-graph-api35-20261117.txt` SHA-256 `00ede3158b972a0474e9ff56cda7b7f766c6af7da38c9a47a07bfde4c1ac4f85`
- Target APK SHA-256 `292c7292c56751f42057caf5107d87c6181f07de8528f303ce96c951578c514b`
- Instrumentation APK SHA-256 `e3fa9a8eefaaa7a8cf21e537ce56e519092b0b76c88199569945dcffaa6639d8`
- `app/build.gradle.kts` SHA-256 `0e6799e33714e85c7120b5905220a117f8f3cccedd178d9604272de549cc125e`
- `app/src/androidTest/kotlin/com/chimali/security/ConfiguredFidoDiagnosticPrivacyTest.kt` SHA-256 `b2c3c101545f5b06896284ec5f192805a3f12b718f480f554edc18b1e99d8687`

`:app:ktlintCheck` passed after adding the isolated build type.

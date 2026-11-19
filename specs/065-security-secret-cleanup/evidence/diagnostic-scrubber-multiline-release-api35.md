# Release-derived diagnostic scrubber verification

**Date:** 2026-11-18  
**Finding:** SEC065-LOG-057  
**Requirements:** FR-008/009/013/014; NFR-SEC-041; TR-AUTH-031; T029/T034/T035  
**Device:** API 35 AOSP emulator `emulator-5554`  
**Candidate:** Dirty exploratory worktree; no frozen product candidate supplied

## Procedure

Built the isolated `diagnosticRelease` target and instrumentation APK, then installed them only on `emulator-5554`. The target package was `com.chimali.diagnosticrelease`; the instrumentation package was `com.chimali.diagnosticrelease.test`. The focused instrumentation method was run with `expectedDebug=false` and generated synthetic marker/run-ID arguments.

The INFO, WARN, and ERROR messages contained marker-bearing values with comma/semicolon suffixes, adjacent sensitive labels, and embedded newlines. The test exercised the actual configured application logger and verified its app-private log file contained the redacted password/seed fields and fixed throwable wording, with no marker present. `am instrument` reported `OK (1 test)`.

Both isolated packages were uninstalled and verified absent after the run. The pre-existing `com.chimali.test` package was left untouched. A sanitized run record is retained at `build/security/clipboard-observation/diagnostic-scrubber-multiline-api35-20261118.txt`.

## Result and limits

This proves that this release-derived, non-debuggable target's app-private diagnostic writer redacts the tested punctuation and multiline suffixes. The test does not prove complete source/destination coverage or product-release signing/acceptance. No separate Logcat result is claimed. Other diagnostic emitters, iOS behavior, immutable String retention, and frozen-candidate review remain open under T029/T034/T035.

## Artifacts

- Sanitized run record SHA-256: `b204577614d610520e4102f338031159473880f072a1268139b7e450237aa7bd`
- Target APK SHA-256: `1981fb670dfeb8a79527fb5228b34a208bd1421045d7a91d31cebf481ad9e1c2`
- Instrumentation APK SHA-256: `208fd099c92117fee198b6bff00c7d7ce464c711a516403e03702fbd58e7c319`
- `app/src/androidTest/kotlin/com/chimali/security/ConfiguredFidoDiagnosticPrivacyTest.kt` SHA-256: `30a1f88714c23bab87e80cf620d773aac748c5acf8a4533e09efddbdd6a6c5d8`
- `app/src/androidTest/kotlin/com/chimali/LeakCanaryMnemonicOwnerTest.kt` SHA-256: `e854664424d2aa2a9d9783630d90252cb0e5ca429ae2bbb0482ebd5dca39fe1b`

The release-derived app and test APK assembly succeeded. The app-wide Android-test KtLint task remains failing on existing style findings in the shared app test source set; the report contains no diagnostic-probe source finding, and the instrumentation test compiled and passed.

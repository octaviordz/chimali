# Koin startup logger removal smoke check (API 35)

`ChimaliApplication` no longer calls Koin's Android logger override; Koin 4.2.1's default `EmptyLogger` is retained so DI startup has no separate Koin-to-Logcat writer ahead of FIDO writer registration.

The updated app compiled with `:app:compileDebugKotlin` and assembled with `:app:assembleDiagnosticDebug`. The isolated `com.chimali.diagnosticdebug` package was installed on API 35 AOSP emulator `emulator-5554`, launched through its launcher activity, and observed running as process `27304`. A PID-filtered `logcat` query for both `KOIN` and `Koin` tags returned zero records. The package was then removed and package-manager enumeration confirmed it absent. A previous incorrectly correlated query was excluded; this result uses the actual device PID. This is a bounded startup check only; it does not prove the absence of all Android log output or verify every startup failure path.

- `app/src/main/kotlin/com/chimali/ChimaliApplication.kt` SHA-256: `be99800640df71415b346bed5b7972551e2a9839b37001188542b3df2349f7c5`
- `app/build/outputs/apk/diagnosticDebug/app-diagnosticDebug.apk` SHA-256: `531e83c7ccf93313f9785f189fd675f626590cb01d3b3bd844b9b3ed8806b30b`
- Device/API: `emulator-5554`, API 35 AOSP
- PID-bound startup query: PID `27304`; `KOIN`/`Koin` tagged records: 0
- Package cleanup: package absent after the attempt
- Remaining: startup destination capture with valid PID-bound Logcat, other pre-registration emitters, release-derived build behavior, dependency/framework output, and frozen-candidate verification.

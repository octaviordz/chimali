# Configured debug Logcat redaction probe (API 35)

On 2026-11-18, the isolated `diagnosticDebug` target passed both `ConfiguredFidoDiagnosticPrivacyTest` cases on the API 35 AOSP emulator (`emulator-5554`). The test verified synthetic INFO and WARN records reached Logcat with sensitive fields redacted and the synthetic marker absent. It also verified app-private writer redaction and the isolated rotation/fallback behavior. Logcat was read as a bounded 500-line snapshot and filtered in the instrumentation process by a unique synthetic run ID.

The initial probes exposed test-harness limitations: an unbounded device-side filter could stall, and Kermit omits throwable details from Logcat entirely. The final assertions match the observable contract: redacted records are present and no synthetic value is present. App-private output continues to assert the fixed throwable placeholder.

Both unique debug target/test packages were removed and verified absent. No real clipboard content was read. This is one debug build on one emulator; T034 and T035 remain open for the complete diagnostic matrix, release candidate, and independent review.

- Run record: `build/security/clipboard-observation/diagnostic-logcat-debug-api35-20261118.txt`
- Target APK SHA-256: `6f32b2024532c1205b0dcb699dd93c49ae7c79c86a36461be43df3f528d8ca72`
- Instrumentation APK SHA-256: `b538035a55d0dbecb2e66517cbc8fdcdd6f84a83702517208f3e0ff309b1435a1`
- Instrumentation source SHA-256: `24c619eb3cbf426296a5030808a003067ec0b4509053813e16dee5839adb7e04`

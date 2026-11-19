# Configured release-derived Logcat exclusion (API 35)

On 2026-11-18, the isolated, minified, non-debuggable `diagnosticRelease` target passed both `ConfiguredFidoDiagnosticPrivacyTest` cases on API 35 AOSP emulator `emulator-5554`. Synthetic INFO/WARN/error diagnostics were redacted in app-private output with fixed throwable wording. The test's bounded Logcat snapshot contained neither the synthetic run ID nor the synthetic marker. The configured rotation and filesystem-fallback checks also passed.

Both unique target/test packages were removed and verified absent. No real clipboard text was read. This is release-derived exploratory evidence on one emulator; it does not establish product signing, all diagnostic paths, iOS behavior, the complete source/destination/failure matrix, or frozen-candidate acceptance. T034 and T035 remain open.

- Sanitized run record SHA-256: `e2bbc1ad8eda292401e80da4facab80ff7ad429d9544caeafbeced4df5f1fdde`
- Target APK SHA-256: `79f5d93baa7b325cb72c2a8b8df675873ea554ad6bef17e789e23eb159eb38c4`
- Instrumentation APK SHA-256: `2300b3faf27b6e239c713fb9e37e2545fbb15e5daf3dcd6514e23f313dbf7c26`
- Instrumentation source SHA-256: `3144728e25c948398f9293b51a200e72765b47815df1dfb160ca2d8f9f8cf89d`

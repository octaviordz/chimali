# FIDO persistence ceremony test owner cleanup

**Finding**: SEC065-FIDO-017  
**Date**: 2026-11-16  
**Environment**: API 35 AOSP emulator `emulator-5554`; synthetic test data only.

## Changes and verification

`CredentialPersistenceCeremonyTest` erases temporary UTF-8 source arrays used to create synthetic user IDs, registration/assertion challenges, combined authenticator-data messages, returned attestation/assertion signatures, mismatch public-key arrays, and stored public-key copies. Its outer `finally` also clears the synthetic credential-ID list references after recovery assertions. The focused suite passed `OK (2 tests)` on API 35.

`CredentialProcessRestartTest` erases user-ID source bytes and mutable attestation/assertion authData/signature buffers. The explicit lifecycle sequence passed: prepare synthetic credential (`OK (1 test)`), force-stop the instrumentation target process and confirm no PID remained, recover and sign after restart (`OK (1 test)`), then remove the fixture (`OK (1 test)`).

Build and lint: `:feature:fido2:assembleAndroidTest :feature:fido2:ktlintCheck` passed. Latest instrumentation APK SHA-256: `3889f1e0027bb96f19c260d17c71919350c318536c14e1b9d1a92cbb15238635`. The instrumentation package was uninstalled after the test.

These tests use only synthetic values. `CredentialId`, `UserId`, and RP values include immutable JVM Strings that cannot be zeroed; this change shortens mutable-buffer lifetimes and releases local references, but makes no claim of immutable-string or heap erasure. Provider/native copies and physical-device behavior were not measured.

# FIDO transport-settings test fixture cleanup

**Finding**: SEC065-FIDO-016  
**Date**: 2026-11-16  
**Scope**: Synthetic Android device-test fixtures only.

`TransportSettingsDuringCeremonyTest` covers authentication and registration consent prompts. Each test creates synthetic RP/user `SensitiveCredentialText` values, checks that opening transport settings leaves the prompt visible without confirming or cancelling, and erases the test-owned state in a `finally` block. The helper also fills its temporary UTF-8 source byte array with zero after constructing the mutable fixture.

Focused API 35 emulator run on `emulator-5554`: both tests passed (`OK (2 tests)`). `:feature:fido2:assembleAndroidTest :feature:fido2:ktlintCheck` passed. Instrumentation APK SHA-256: `6f7626603eb06c4e678200125603aa534be33d7bbadae9de9f81ccfc8142013d`. Test APK was uninstalled and verified absent afterward.

This test covers its own synthetic fixture cleanup and the settings callback behavior only. It does not exercise a real CTAP request, OS credential-provider handoff, production request lifecycle, rendered/accessibility copy release, heap erasure, or physical-device behavior.
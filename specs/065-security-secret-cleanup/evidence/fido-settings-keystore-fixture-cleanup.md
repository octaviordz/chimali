# FIDO settings test Keystore fixture cleanup

**Finding**: SEC065-FIDO-018  
**Date**: 2026-11-16  
**Environment**: API 35 AOSP emulator `emulator-5554`; instrumentation-package fixture only.

`Fido2SettingsRepositoryTest` previously created the AndroidX default master key but left the Keystore entry behind after each test. Its fixture now removes the isolated preferences/DataStore files and deletes the AndroidKeyStore alias in `finally`, asserting the alias is absent afterward. The context wrapper returns itself as its application context so `EncryptedSharedPreferences` and the repository both access the same UUID-scoped preference file.

Verification: `:feature:fido2:assembleAndroidTest :feature:fido2:ktlintCheck` passed; all six focused device tests passed (`OK (6 tests)`). Every test executes teardown and verifies the master-key alias is absent. Instrumentation APK SHA-256: `ee0e72dc571b2181029d7ad4f825bc61d6676be1483e6b2ae8942080d73a0637`. The instrumentation package was uninstalled afterward.

An intermediate attempt used a per-test alias without changing the repository's `MasterKeys` lookup; that mismatch caused the focused suite to fail. After using the same AndroidX alias as production and making `getApplicationContext()` retain the test wrapper's UUID-scoped preference namespace, all six cases passed. No production settings behavior changed.

The test uses only synthetic settings and a Keystore key scoped to its instrumentation package. This does not inspect or delete the production app's Keystore alias.

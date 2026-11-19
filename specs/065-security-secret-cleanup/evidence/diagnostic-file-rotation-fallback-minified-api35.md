# Minified audit-variant diagnostic rotation and fallback

**Date:** 2026-11-17  
**Finding:** SEC065-LOG-055  
**Device:** API 35 Android ATD emulator `emulator-5554`  
**Variant:** `clipboardAuditMinified`, application ID `com.chimali.clipboardaudit.minified`, minified and non-debuggable (`dumpsys package` reported `flags=0x0`).

The isolated minified audit target and Android test APK were built with R8 and installed under unique package IDs. The method-filtered instrumentation test `ConfiguredFidoDiagnosticPrivacyTest#configuredFileWriterRotationAndFallbackOmitSyntheticValuesAndFailures` passed (1 test). It verified that both active and rotated app-private diagnostic files contain the redacted synthetic record and fixed throwable placeholder without the marker, and that the blocked-directory fallback emits only fixed wording without marker, path, or exception detail. The run used synthetic marker `SEC065_DIAG_MARKER_MINIFIED_CAPTURE_20261117A` and run ID `SEC065_RUN_MINIFIED_ROTATION_CAPTURE_20261117A`; test output contains only the test outcome, not private log content.

Both isolated APK packages were uninstalled after the run. The temporary audit directory is removed by the test's `finally` cleanup. The production package was not installed or modified.

**Result:** Passed for one API 35 emulator minified/non-debuggable audit-variant rotation/fallback case. This does not cover all diagnostic sources/destinations, real release installation/signing, other devices, or independent security review.

**Artifacts:**

- `build/security/clipboard-observation/configured-rotation-fallback-minified-api35-20261117.txt` SHA-256 `03e102aadbf68b1110e8e5324ea2008b4d6fd80b8fddc78f52c33de03eea3b2a`
- `app/build/outputs/apk/clipboardAuditMinified/app-clipboardAuditMinified.apk` SHA-256 `74abb4658c847276cc15d6416f62ab80fb30d6e8b68cacacd401f1302bf851bf`
- `app/build/outputs/apk/androidTest/clipboardAuditMinified/app-clipboardAuditMinified-androidTest.apk` SHA-256 `fc34d25f10e8ca8e7a86730e2ba8ee81c021a61d2b0685886acbef934d0ac2d7`
- `app/src/clipboardAuditMinified/AndroidManifest.xml` SHA-256 `2d8b803a2bea0f98c3f92cc5a2505683df655a3a9c960170ef170087c5bf47a0`
- `app/src/clipboardAuditMinified/java/com/chimali/security/ClipboardAuditApplication.kt` SHA-256 `218598e07a80bfedf77eabd0db0f31537ae1efa73c3ae052e04e990f661a7cbe`
- `app/src/clipboardAuditMinified/java/com/chimali/security/ClipboardTargetCopyActivity.kt` SHA-256 `d3253a4795d9ff6ab06081badfab04a64a49e9a7c8c60ea97b8926a2c48a19fc`

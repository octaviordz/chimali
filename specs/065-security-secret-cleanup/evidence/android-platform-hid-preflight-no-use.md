# Android HID capability preflight no-use assessment

**Date:** 2026-11-17  
**Source:** `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/platform/AndroidPlatformBluetoothHid.kt`.

The inspected preflight reads Android SDK level, the fixed Bluetooth package-manager feature, adapter presence, and adapter enabled state, then returns Boolean capability/status results. It does not read or expose paired-device addresses/names, credential/RP/user identity, secret material, dynamic diagnostics, or persisted data. The inventory now records a file-specific `Assessed-no-relevant-usage` rationale. Actual HID profile activation is a separate path and remains subject to its own source/runtime assessment.

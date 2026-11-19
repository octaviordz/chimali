# Debug-only camera permission scope

**Date:** 2026-11-16  
**Revision:** Exploratory dirty worktree; no frozen candidate claim.

The app main manifest and the FIDO Android main manifest both contributed camera declarations to the merged release manifest. Source review found the camera consumer is the development QR scanner, reached from the debug-only development-tools UI. The camera permission and optional camera hardware feature declaration now live in `app/src/debug/AndroidManifest.xml`; the FIDO main manifest no longer declares either one, and the app main manifest no longer declares either one.

**Verification:** `gradlew.bat :app:processReleaseMainManifest :app:processDebugMainManifest :app:processClipboardAuditMainManifest` completed successfully after the change. The resulting release merged manifest contains neither a `uses-permission` for `android.permission.CAMERA` nor a `uses-feature` for `android.hardware.camera`. The debug merged manifest contains both declarations. The disposable `clipboardAudit` merged manifest contains neither. CameraX component metadata remains in manifests because CameraX is still a build dependency; that metadata is not a camera permission or hardware feature declaration.

**Limits:** This verifies manifest packaging for these variants only. It does not verify camera scanner behavior, other flavors, or a frozen release candidate.

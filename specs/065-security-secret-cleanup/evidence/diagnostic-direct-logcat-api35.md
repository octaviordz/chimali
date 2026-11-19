# Direct performance Logcat sink probe (API 35)

`BaselineMeasurements` bypasses the configured Kermit writers and emits its completion summary with `android.util.Log.println`. On 2026-11-18, `DirectFidoPerformanceLogcatPrivacyTest` passed **1/1** on API 35 AOSP emulator `emulator-5554` in both `diagnosticDebug` and the minified, non-debuggable `diagnosticRelease` diagnostic target. Each run confirmed the completion summary contains the fixed `MakeCredential` label and timing fields, and the separate incomplete-disconnect record contains only its fixed reason. Neither bracketed Logcat segment contained the raw UTF-8, Base64, or hexadecimal synthetic response packet. Both target/test package pairs were removed and verified absent after the runs.

The release-derived probe uses a diagnostic-only R8 rule to preserve `BaselineMeasurements` as an instrumentation seam; all other target code remains minified. This variant is not product-signed and the keep rule means it is not byte-for-byte product release evidence. The initial unadapted release probe failed with an `IncompatibleClassChangeError` because R8 changed the Kotlin singleton dispatch shape. The compatibility failure is retained at `build/security/clipboard-observation/direct-performance-logcat-release-harness-failure-api35-20261118.txt`; a later attempt using a stale target APK failed before the sink was invoked because that APK did not contain the expected class. After explicitly rebuilding `diagnosticRelease`, the test passed as recorded above. These failures are test-harness/packaging failures, not product behavior or leak findings.

This covers one direct performance sink in two isolated API 35 diagnostic targets. Other direct sinks, startup-before-registration, throwable/object-description variants, full reachability, iOS and frozen product-candidate acceptance remain open; T029/T034/T035 remain open.

- Initial successful run record SHA-256: `f6ced4dfdb443034e1d80f2a8b548036ea051aa826570604fa917b94b5f1d75b`
- Completion and disconnect run record SHA-256: `0fffd59490b8f1070cb1816b4f1f1a344180e709f40b663743bf387280029a2d`
- Failed release harness run record SHA-256: `7b47b18bc5890bb4d20e66934249c18d5f78bb5fc5e42f34b71be9d1f931cd69`
- Debug target APK SHA-256: `6f32b2024532c1205b0dcb699dd93c49ae7c79c86a36461be43df3f528d8ca72`
- Release-derived diagnostic target APK SHA-256: `EC41F839BEF110A9F415AE53CECF026F31D32B6D604BC69DF9B40CF3292F08F1`
- Release-derived instrumentation APK SHA-256: `2E2CF416BD980C25D3DDAA846F9E8985CA41540FE04514AC9A9B7451024EB1DD`
- Release-only R8 probe rule SHA-256: `A9C65913A980F263CEB835DA89563402C0DDBA85675BCBDC5A2C660D171D2721`
- Current test source SHA-256: `6091478BE2835AE92D223DCE199EA657523B25CC8647BB5C009074CD4C1D4DC1`
- Current `app/build.gradle.kts` SHA-256: `4F15DEEAA78C299F0031E4E458EF9F7413D4549B188043A84B909469DFAB017F`
- Successful release-derived test output: `OK (1 test)` on API 35 AOSP emulator `emulator-5554`; build `:app:assembleDiagnosticReleaseAndroidTest` and `:app:assembleDiagnosticRelease` both completed successfully before installation.

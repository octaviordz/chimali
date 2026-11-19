# Debug and release-derived diagnostic writer verification after startup registration fix

**Date:** 2026-11-18  
**Finding:** SEC065-LOG-057  
**Requirements:** FR-008/009/013/014; NFR-SEC-041; TR-AUTH-031; T029/T034/T035  
**Device:** API 35 AOSP emulator `emulator-5554`  
**Candidate:** Dirty exploratory worktree; no frozen product candidate supplied

## Startup issue and correction

The first isolated debuggable target launch hit an ActivityManager startup ANR. Its main-thread trace was in Bouncy Castle curve parameter initialization while `ChimaliApplication.onCreate` synchronously constructed a provider. The trace showed the call through `LongArray` multiplication and `X962NamedCurves.configureBasepoint`; it was not retained outside the emulator. Removed that duplicate eager provider construction. `Fido2Initializer` already installs the privacy-scrubbed writers before starting best-effort Bouncy Castle warm-up on its feature scope. `PublicKeyDecoder` now ensures the bundled provider is registered immediately before its EdDSA/ML-DSA provider lookups; a regression removes the registered provider, decodes a synthetic EdDSA public key, confirms success and restores the test process provider.

## Runtime and host results

After the correction, the isolated debuggable `diagnosticDebug` target passed both `ConfiguredFidoDiagnosticPrivacyTest` cases (`OK (2 tests)`), including configured app-private output redaction and the isolated rotation/filesystem-fallback path. The rebuilt non-debuggable `diagnosticRelease` target passed the same two cases (`OK (2 tests)`). Both targets used unique package identities, and both target/test package pairs were removed and verified absent after each run. Existing `com.chimali` and `com.chimali.test` packages were not targeted.

The focused `PublicKeyDecoderTest` passes 8/8, including the no-pre-registered-provider regression. The full FIDO Android host suite passes 1,333 tests across 172 reports with zero failures, errors or skips; FIDO KtLint passes. The app-wide Android-test KtLint task remains failing on existing shared test-source style findings.

## Limits

This covers one API 35 emulator, two Android build modes, the configured app-private writer, and isolated local file rotation/fallback. There is no separate Logcat assertion, complete emitter/destination/reachability matrix, iOS result, product-signed candidate, frozen candidate, or independent review. T029/T034/T035 remain open.

## Artifacts

- Debug run record SHA-256: `ae951fa0bd9b8e7106de2191e36e4978ef0d6d21bfd78ff92a3663139fb301b5`
- Release-derived run record SHA-256: `33f7363e96d1260df72c85f5f6fb1227d80e4f875973c74f8e39351458f52267`
- Debug target APK SHA-256: `6f32b2024532c1205b0dcb699dd93c49ae7c79c86a36461be43df3f528d8ca72`
- Debug instrumentation APK SHA-256: `90335f2fc3af5e80cbd02bda3c9aa5239410939c7bc3afac0228e48a6b67fe44`
- Release-derived target APK SHA-256: `79f5d93baa7b325cb72c2a8b8df675873ea554ad6bef17e789e23eb159eb38c4`
- Release-derived instrumentation APK SHA-256: `208fd099c92117fee198b6bff00c7d7ce464c711a516403e03702fbd58e7c319`
- `app/build.gradle.kts` SHA-256: `ECE4B8A0E5769801DBFD02B22E050C4D5FBB0182B1542BAC47D4A1BBA39D4B96`
- `app/src/main/kotlin/com/chimali/ChimaliApplication.kt` SHA-256: `C0C03B7EFA6B93D0312A42CDBC0BC4881C46ED1F13BBC3CA6F23BCA42AFE58EC`
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/PublicKeyDecoder.kt` SHA-256: `359F75FF93208D0CA4AF060C70C072DF4E0FB69F5D52310C3652DE56C3E32DEE`
- `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/crypto/PublicKeyDecoderTest.kt` SHA-256: `9DE44D3F00E89BAEF4DF453689B6B59F4D36B948F3655E7944C4746BA111F032`
- `app/src/androidTest/kotlin/com/chimali/security/ConfiguredFidoDiagnosticPrivacyTest.kt` SHA-256: `30A1F88714C23BAB87E80CF620D773AAC748C5ACF8A4533E09EFDBDD6A6C5D8`\n- `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.data.crypto.PublicKeyDecoderTest.xml` SHA-256: `E4AF70CF526AACB604181FEA60C6316662A247285E2A4E59C6D9B989FF1A453D`\n- `feature/fido2/build/reports/tests/testAndroidHostTest/index.html` SHA-256: `23B7DB2B41E6198AA1B28BD6B543A778B8F4AB669494D97CA41989A96BE830B2`
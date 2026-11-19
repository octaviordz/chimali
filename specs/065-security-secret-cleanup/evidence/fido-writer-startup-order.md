# FIDO writer startup ordering

| Field | Result |
|---|---|
| Requirement | FR-008/009; NFR-SEC-041; TR-AUTH-031; T005/T029/T035; SEC065-LOG-053 |
| Scope | Ordering of Kermit writer registration and asynchronous FIDO transport-preference startup |
| Revision | Dirty exploratory worktree based on current HEAD; source changed 2026-11-16 |
| Procedure | Source call-order review of `ChimaliApplication.onCreate`; `:app:testDebugUnitTest :app:ktlintCheck --no-daemon` |
| Environment | Windows host; Gradle 9.6.0; JDK 25 launcher; no device selected |
| Expected/observed | FIDO writers are registered before `ProcessTransportSelection.startSnapshot()` launches asynchronous preference lookup. App unit suite passed 7 tests across 3 XML reports with zero failures/errors/skips; KtLint passed. |
| Date | 2026-11-16 |
| Limitations | Source-order evidence does not force the prior race at runtime; Koin `androidLogger()` and any Kermit emissions during DI construction are separate, uncaptured startup paths. Logcat/local-file runtime behavior, release startup and other FIDO emitters remain open. |
| Status | Passed (source-order correction and app host verification) |
| Artifact links | `assessment.md#SEC065-LOG-053`; `app/build/test-results/testShrunkDebugUnitTest/TEST-com.chimali.MainActivityPermissionDiagnosticTest.xml` SHA-256 `9610ebe22544c1588e02775fa66ef560bfcf2c47c7ca3b9d973cf3795c0f096e`; `app/build/test-results/testShrunkDebugUnitTest/TEST-com.chimali.navigation.AppRoutingTest.xml` SHA-256 `a98c701b8c005f42e1d7ef838ff01d43d7f147255a268b4eaa8ce8ea98dc5ecf`; `app/build/test-results/testShrunkDebugUnitTest/TEST-com.chimali.WalletSessionMemoryPressureTest.xml` SHA-256 `3d1a5e6f4a921aed055d11079afac4b01cc1d43379bf9a00b60062d250f83f70`; app KtLint task |

`app/src/main/kotlin/com/chimali/ChimaliApplication.kt` SHA-256: `4bcee21845a1cab9b849c1cd837e897ce443f16b764b7c7d28f4d2427cc6f792`.


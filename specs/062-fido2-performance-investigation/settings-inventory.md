# Settings and Test Inventory

**Inspection date:** 2026-10-30  
**Scope:** Feature 062 task T002. Read-only source and build configuration inventory; no settings were changed and no tests were run.

## Production behavior

`Fido2SettingsRepositoryImpl.getMaxCredentialCount()` first reads the shared Proto DataStore flow, then opens encrypted legacy preferences and checks `max_credential_count` on each call. If present, the legacy integer is written to Proto DataStore before the legacy key is synchronously removed, and the legacy value is returned. GeneralSecurityException, IOException, and SecurityException during the legacy read are logged and treated as a missing value. If no legacy value is used, a positive DataStore value is returned; a nonpositive value produces the application default of 1000, and a zero value is persisted as that default. `setMaxCredentialCount()` writes only the requested field using `copy`, preserving other preference fields.

The current source comments and implementation identify a possible cost in opening encrypted preferences for each lookup. That is a profiling lead only; no component timing was collected. The legacy path and its migration precedence must remain until a measured and authorized change is proven safe.

## Registration path and limit

`RegisterCredentialUseCase` queries credential statistics, then calls `getMaxCredentialCount()`, then rejects when `totalCredentials >= limit`, before generating a key. Existing registration host tests use a mocked `Fido2SettingsRepository`; they cover below-limit, at-limit, and increased-limit behavior. The production lookup remains unmeasured by those tests.

## Configured tests and build

`feature/fido2/build.gradle.kts` configures Kotlin Multiplatform `androidHostTest` and `androidDeviceTest`, and adds `src/androidSharedTest/kotlin` to both. The host suite uses JUnit 5/MockK and coroutine test dependencies; the device suite uses AndroidX instrumentation and MockK Android dependencies.

No configured `Fido2SettingsRepositoryTest` was found in `src/androidHostTest`, `src/androidDeviceTest`, or `src/androidSharedTest`. The plan notes that the legacy migration test under the old `src/androidTest` location is not established as part of the configured device source set. Any justified settings behavior change therefore needs suitable isolated synthetic-state coverage in the configured device suite, with host tests for affected use-case invariants as applicable.

## Disposition

No optimization is justified by this inventory alone. Do not remove migration, cache a migration decision, alter defaults, or change settings storage until the production-path investigation yields conclusive attribution and applicable regression tests are prepared.
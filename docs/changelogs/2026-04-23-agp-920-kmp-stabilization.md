# Detailed Changelog: AGP 9.2.0 & KMP Build Stabilization (2026-04-23)

## Goal
Stabilize the Kotlin Multiplatform (KMP) build infrastructure following the migration to Android Gradle Plugin (AGP) 9.2.0 and the new `com.android.kotlin.multiplatform.library` plugin.

## Technical Summary
The migration to AGP 9.2.0 introduced strict separation between platform-specific and common source sets in the new KMP-Android plugin. This session focused on resolving regressions in compilation and testing caused by these architectural changes.

## Changes

### 1. Test Architecture Refactoring
- **JVM Dependency Isolation**: Relocated tests requiring `java.security`, `javax.crypto`, or JVM-specific file system access from `commonTest` to `androidHostTest`.
- **Affected Files**:
    - [HdkMigrationTest.kt](/core/security/src/androidHostTest/kotlin/com/chimali/core/security/HdkMigrationTest.kt) (Moved from `commonTest`)
    - [PasskeyCredentialDaoTest.kt](/feature/fido2/src/test/kotlin/com/chimali/fido2/data/dao/PasskeyCredentialDaoTest.kt) (Fixed for SqlDelight 2.0 compatibility)

### 2. SqlDelight 2.0 Stabilization
- **QueryResult Wrapper Support**: SqlDelight 2.0 changed the return types of generated query methods (e.g., `insert`, `update`) from `Unit` to `QueryResult<Long>`.
- **Mocking Strategy**: Updated DAO unit tests to use relaxed MockK mocks and stub query returns with `mockk()` to prevent `ClassCastException` and `Argument type mismatch` errors during test execution.

### 3. iOS Platform Compatibility
- **Native File System Access**: Added missing `@OptIn(ExperimentalForeignApi::class)` to [IosLogDirectoryProvider.kt](/feature/fido2/src/iosMain/kotlin/com/chimali/fido2/util/logging/IosLogDirectoryProvider.kt) to resolve compilation errors when interacting with `NSFileManager`.
- **Okio Relocation**: Moved `LocalCrashReportingLogWriter` implementation to platform source sets to handle `FileSystem.SYSTEM` usage correctly in the new plugin architecture.

### 4. Build Configuration Fixes
- **Compose Runtime Injection**: Resolved `IncompatibleComposeRuntimeVersionException` by adding explicit `compose.runtime` dependencies to `commonMain` in `:feature:fido2`.
- **Plugin Migration Cleanup**: Removed redundant flags from `gradle.properties` that were deprecated in AGP 9.2.0.

## Verification Results

### Build Stability
- **`./gradlew assemble`**: Successfully completed across all modules.
- **Deprecation Warnings**: Zero occurrences of "Deprecated compatibility plugin" warnings.

### Unit Test Suite
- **Executed Tasks**:
    - `:feature:fido2:testAndroidHostTest`
    - `:core:security:testAndroidHostTest`
- **Result**: 23/23 tests passed.

## Impact
These changes ensure that the project is fully compatible with the latest Android development tools while maintaining a clean separation of concerns for multiplatform targets (Android, iOS, and JVM).

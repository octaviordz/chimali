# Gradle 10 Compatibility Audit and Resolution Report

This document summarizes the investigation into Gradle build deprecations and incompatibilities identified during the build process, specifically those affecting future compatibility with Gradle 10.

## 1. Executive Summary
We have analyzed the build output with `--warning-mode all` and `--stacktrace`. Most project-level incompatibilities have been resolved through build script modernizations. One remaining warning is attributed to an internal call within an external plugin (Detekt) and cannot be resolved through configuration alone until the plugin is updated by its maintainers.

## 2. Resolved Incompatibilities

### 2.1 Deprecated `Project.file()` and `Project.getBuildDir()`
**Status**: RESOLVED
**Description**: Usage of legacy `file()` and `buildDir` APIs in the root `build.gradle.kts` for report path generation.
**Fix**: Migrated to the modern `ProjectLayout` API.
*   **Detekt Baseline**: Updated to `layout.projectDirectory.file(...)`.
*   **Lint Reports**: Updated to `layout.buildDirectory.file(...).get().asFile`.
*   **Detekt Reports**: Updated to `layout.buildDirectory.file(...)`.

### 2.2 KSP `defaultModule` Deprecation
**Status**: RESOLVED
**Description**: Koin Annotations processor (KSP) warned that `defaultModule` generation is deprecated and will be removed in future versions.
**Fix**: Added `arg("KOIN_DEFAULT_MODULE", "false")` to the global KSP configuration in the root `build.gradle.kts`. Since Chimali uses explicit `@Module` and `@ComponentScan` definitions, disabling the default module is safe and recommended.

### 2.3 Obsolete `ktlint` Plugin Version
**Status**: RESOLVED (UPGRADED)
**Description**: The project was using `ktlint` version `12.1.0`.
**Fix**: Upgraded to `14.2.0` in `libs.versions.toml` to ensure compatibility with modern Gradle internals and improve stability.

## 3. Persistent External Incompatibilities

### 3.1 Detekt Plugin Internal Deprecation
**Status**: PENDING (Plugin Maintainer Action)
**Issue**: `The ReportingExtension.file(String) method has been deprecated. This is scheduled to be removed in Gradle 10.`
**Evidence**:
```text
at org.gradle.api.reporting.ReportingExtension.file(ReportingExtension.java:98)
at io.gitlab.arturbosch.detekt.DetektPlugin.apply(DetektPlugin.kt:28)
at Build_gradle$1.execute(build.gradle.kts:19)
```
**Conclusion**: The Detekt Gradle Plugin (v1.23.8) internally calls a deprecated `ReportingExtension` method during its initialization phase. Since 1.23.8 is currently the latest stable version, this warning will persist until the Detekt maintainers release a version targeting Gradle 10 compatibility (likely the upcoming 2.0 release).

## 4. Minor UX/UI Deprecations Identified
During the audit, several minor Compose and platform-level deprecations were identified in the logs. While not build-breaking, they should be addressed in future maintenance cycles:
*   **Icons**: `Icons.Filled.Label` and `Icons.Filled.List` should be migrated to `AutoMirrored` versions.
*   **Lifecycle**: `LocalLifecycleOwner` has moved to the `lifecycle-runtime-compose` library.
*   **Buttons**: `outlinedButtonBorder` now requires an `enabled` parameter for correct opacity handling.

## 5. Verification
The build remains functional and successful (`BUILD SUCCESSFUL`). Targeted verification of KSP tasks confirms that the `defaultModule` warning has been silenced.

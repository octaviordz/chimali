# FIDO2 Build Script & Code Quality Polish (2026-05-21)

## Summary
Refined the `:feature:fido2` build configuration, resolved redundant code in `:core:domain`, and addressed several editor warnings and detekt issues to improve codebase maintainability and alignment with Kotlin 2.3/Compose 1.11 standards.

## Changes
- **Domain Quality Improvements**:
    - Removed redundant `toInt()` calls in `PasskeyCommand.Register.hashCode()` within `:core:domain`, as `Long.hashCode()` already returns an `Int`.
- **Build Script Hardening**:
    - Refactored `build.gradle.kts` in `:feature:fido2` to eliminate KDoc-related syntax warnings in configuration blocks.
    - Suppressed unstable API usage warnings for incubating AGP features (`optimization` block).
    - Resolved `StringLiteralDuplication` detekt issues by extracting the `"androidMainImplementation"` configuration name into a private variable.
    - Modernized `sourceSets` configuration by replacing `val ... by getting` with `getByName("...")` to avoid unused property warnings while maintaining explicit configuration.
- **Dependency Modernization**:
    - Replaced the deprecated `compose.runtime` accessor with a stable version-catalog-backed dependency `libs.compose.runtime`.
    - Updated `libs.versions.toml` to include `compose-runtime` (`org.jetbrains.compose.runtime:runtime`) explicitly for better dependency management.
- **Project Stability**:
    - Performed a full Gradle synchronization to validate the new dependency structure and resolve IDE inspection discrepancies.

## Impact
These changes ensure a cleaner developer experience with fewer "false positive" warnings in the IDE and automated CI checks, while preparing the module for future KMP target expansions by stabilizing the source set definitions.

# Changelog Details: Compose Detekt and Android Lint Integration (2026-04-26)

## Summary
To enforce rigorous code quality, standard formatting, and platform-specific stability across the Chimali project, we integrated Compose-specific Detekt rules and officially configured Android Lint.

## Changes

### Compose Detekt Rules
- Added `io.nlopez.compose.rules:detekt:0.5.7` to the version catalog (`libs.versions.toml`).
- Wired the `detektPlugins` dependency into the root `build.gradle.kts` dynamically for all modules that apply the Jetpack Compose compiler plugin (`:app`, `:core:ui`, `:feature:*`).
- Configured the new `compose` section inside `config/detekt/detekt.yml` to strictly enforce all active rules without modifying pre-existing sections (e.g., complexity, style).
- Triaged and addressed pre-existing Compose violations, specifically focusing on Compose standard naming, modifier ordering, and forbidden comment overrides. We adhered to the mandate of applying `@Suppress` at the narrowest applicable declaration scope rather than at the file level.

### Android Lint configuration
- Configured a unified `lint` DSL block inside the root `build.gradle.kts` for both `com.android.application` and `com.android.library` modules.
- Transitioned away from the deprecated `BaseExtension` toward the modern AGP 9.0+ compliant `ApplicationExtension` and `LibraryExtension`.
- Enabled key Lint properties including `abortOnError = true`, `checkReleaseBuilds = true`, `htmlReport = true`, and XML report generation per module.
- Suppressed known false-positives (such as `TypographyFractions` and `TypographyQuotes`) while strictly enforcing `RtlHardcoded` checks.

### CI Pipeline and Documentation
- Appended `Run-Task "Lint (Release)" "$Gradle lintRelease"` to `tools/local-ci.ps1` to prevent platform regressions from reaching main branch branches.
- Authored a comprehensive `docs/quality.md` file designed for fast onboarding. It covers how developers can trigger `ktlintCheck`, `detekt`, and `lintRelease` locally, and documents exactly how to format the tracking comment `// TODO: resolve ...` alongside suppressions.

## Impact
- **Stability**: Zero-tolerance on Android layout issues and hard-coded RTL directions via Android Lint.
- **Maintainability**: Unified code formatting across Compose UI components avoiding unoptimized state-reading patterns.
- **Onboarding**: Clear guidelines exist on where reports are stored and how to triage and override rule sets.

# Code Quality Guide

This document outlines the tools and processes used to enforce code quality across the Chimali project.

## Overview

The project relies on three primary static analysis tools:

1. **Ktlint**: Enforces standard Kotlin formatting and style conventions.
2. **Detekt**: Analyzes Kotlin code for code smells, complexity, and architectural issues.
   - **Compose Rules**: Detekt is configured with `detekt-compose-rules` to enforce Jetpack Compose best practices (e.g., naming conventions, modifier usage, state management) across all Compose-enabled modules.
3. **Android Lint**: Scans Android modules for platform-specific issues such as hardcoded RTL layout attributes, accessibility problems, and structural correctness.

## Running locally

Before pushing code, you should run these checks locally to ensure CI will pass. All tools are integrated into the Gradle build.

Run individual checks:
- `./gradlew ktlintCheck` — Run formatting checks.
- `./gradlew detekt` — Run Detekt (including Compose rules).
- `./gradlew lintRelease` — Run Android Lint.

Or, run the entire local CI pipeline which executes these checks sequentially:
```powershell
./tools/local-ci.ps1
```

## Compose rule suppressions

If a specific Compose rule cannot be satisfied, you may suppress it at the **narrowest declaration scope** (never at the file or class level).

Use the `@Suppress` annotation followed by a mandatory `// TODO: resolve Compose rule violation` comment indicating why the rule is bypassed and how to fix it in the future.

```kotlin
@Suppress("ComposableNaming") // TODO: resolve Compose rule violation — rename to PascalCase
fun myComposable() {
    // ...
}
```

> **Note**: Do not use broad suppressions like `@Suppress("all")`. The `ForbiddenComment` Detekt rule is active, so ensure you strictly use `// TODO:` as your tracking comment prefix.

## Android Lint suppressions

Similarly, Android Lint issues should be suppressed at the narrowest scope possible.

### Kotlin
Use the `@SuppressLint` annotation in Kotlin files:
```kotlin
@SuppressLint("RtlHardcoded") // TODO: resolve Lint violation — replace hardcoded left/right with start/end
fun myLegacyFunction() {
    // ...
}
```

### XML
Use the `tools:ignore` attribute in XML layouts:
```xml
<LinearLayout
    xmlns:tools="http://schemas.android.com/tools"
    tools:ignore="RtlHardcoded"
    android:layout_gravity="left"> <!-- TODO: resolve Lint violation -->
</LinearLayout>
```

*(For project-wide suppressions, a `lint.xml` file is required, but local suppressions are preferred.)*

## Report locations

Detailed HTML and XML reports are generated to help you pinpoint issues:

- **Detekt**: `build/reports/detekt/<module>.html` (e.g., `app/build/reports/detekt/app.html`)
- **Android Lint**: `build/reports/lint/<module>.html` (e.g., `app/build/reports/lint/app.html`)

## Adding new rules

- **Compose Rules**: Add new rules or modify existing active states in `config/detekt/detekt.yml` under the `compose:` block.
- **Detekt Rules**: Manage standard Detekt configuration in `config/detekt/detekt.yml`.
- **Android Lint**: Modify `lint { ... }` block settings inside the root `build.gradle.kts` (such as `disable` or `enable` parameters).

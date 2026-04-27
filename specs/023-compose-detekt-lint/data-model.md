# Data Model: Compose Detekt & Android Lint Integration

**Feature**: `023-compose-detekt-lint` | **Date**: 2026-04-26

> Note: This feature is a pure build-system and tooling integration. There are no runtime data models,
> database entities, or API contracts. This document captures the **configuration data model** — the
> shape of every configuration artefact this feature creates or mutates.

---

## Entity 1: Version Catalog Entry (`gradle/libs.versions.toml`)

### New Version

| Key | Value | Notes |
|-----|-------|-------|
| `compose-rules` | `"0.5.7"` | Pinned; update via Renovate/Dependabot |

### New Library

| Key | Group | Artifact | Version Ref |
|-----|-------|----------|-------------|
| `detekt-compose-rules` | `io.nlopez.compose.rules` | `detekt` | `compose-rules` |

---

## Entity 2: Root Build Script (`build.gradle.kts`)

### New subprojects block — Compose rule plugin

```
Condition:   plugins.hasPlugin("org.jetbrains.kotlin.plugin.compose")
Scope:       subprojects + afterEvaluate
Action:      dependencies { detektPlugins(libs.detekt.compose.rules) }
Applies to:  :app, :core:ui, :feature:authenticator, :feature:editor, :feature:fido2, :feature:vault
```

### New subprojects block — Android Lint DSL

```
Condition:   plugins.hasPlugin("com.android.application") || plugins.hasPlugin("com.android.library")
Scope:       subprojects + afterEvaluate
Action:      android { lint { ... } }
Applies to:  All Android modules (Compose + non-Compose)
```

#### Lint DSL Properties

| Property | Value | Rationale |
|----------|-------|-----------|
| `abortOnError` | `true` | Fails build on any lint error (SC-002, FR-003) |
| `checkReleaseBuilds` | `true` | Enables `lintRelease` task |
| `htmlReport` | `true` | HTML report for human review |
| `xmlReport` | `true` | XML report for CI parsing |
| `htmlOutput` | `build/reports/lint/<module>.html` | Per-module isolation |
| `xmlOutput` | `build/reports/lint/<module>.xml` | Per-module isolation |
| `disable` | `TypographyFractions`, `TypographyQuotes`, `TypographyDashes`, `TypographyEllipsis`, `TypographyOther` | Suppress known false-positive typography rules |
| `enable` | `RtlHardcoded`, `RtlCompat`, `RtlEnabled` | Catch hard-coded RTL direction issues |
| `checkGeneratedSources` | `false` | Exclude KSP/KAPT generated code (FR-006) |
| `ignoreTestSources` | `true` | Exclude test sources from lint |

---

## Entity 3: Detekt Configuration (`config/detekt/detekt.yml`)

### New top-level section

```yaml
compose:
  active: true
  excludes: '**/build/**,**/generated/**'
  # All rules active at error severity via global build.maxIssues: 0
  CompositionLocalAllowlist:
    active: true
    allowedCompositionLocals: []
  CompositionLocalNaming:
    active: true
  ContentEmitterReturningValues:
    active: true
  ModifierComposable:
    active: true
  ModifierMissing:
    active: true
  ModifierNaming:
    active: true
  ModifierNotUsedAtRoot:
    active: true
  ModifierReused:
    active: true
  ModifierWithoutDefault:
    active: true
  MultipleEmitters:
    active: true
  MutableStateAutoboxing:
    active: true
  MutableStateParam:
    active: true
  ComposableAnnotationNaming:
    active: true
  ComposableNaming:
    active: true
  ComposableParamOrder:
    active: true
  DefaultsVisibility:
    active: true
  LambdaParameterInRestartableEffect:
    active: true
  RememberMissing:
    active: true
  RememberStateMissing:
    active: true
  UnstableCollections:
    active: true
  ViewModelForwarding:
    active: true
  ViewModelInjection:
    active: true
```

### Preservation constraint
All existing sections (`complexity`, `coroutines`, `naming`, etc.) are **unchanged**.

---

## Entity 4: CI Script (`tools/local-ci.ps1`)

### Mutation: new Run-Task call

| Position | After | Task Name | Gradle Command | IgnoreFailure |
|----------|-------|-----------|----------------|---------------|
| Inside `if (-not $SkipLint)` block | `Run-Task "Detekt"` | `"Lint (Release)"` | `"$Gradle lintRelease"` | `false` |

---

## Entity 5: Quality Documentation (`docs/quality.md`)

### New file — sections

| Section | Content |
|---------|---------|
| Overview | Short description of each tool: Ktlint, Detekt (with Compose rules), Android Lint |
| Running locally | Commands: `./gradlew ktlintCheck`, `./gradlew detekt`, `./gradlew lintRelease`, `tools/local-ci.ps1` |
| Compose rule suppressions | `@Suppress("RuleId")` inline; `// TODO: resolve Compose rule violation` tracking pattern |
| Android Lint suppressions | `@SuppressLint("IssueId")` for Kotlin; `tools:ignore="IssueId"` for XML; `lint.xml` for project-wide |
| Report locations | `build/reports/detekt/<module>.html`, `build/reports/lint/<module>.html` |
| Adding new rules | How to enable additional Compose rules or lint checks |

---

## Entity 6: Violation Suppression Pattern (per FR-007)

### Kotlin — Detekt Compose rule

```kotlin
@Suppress("ComposableNaming") // TODO: resolve Compose rule violation — rename to PascalCase
fun myComposable() { ... }
```

### Kotlin — Android Lint

```kotlin
@SuppressLint("RtlHardcoded") // TODO: resolve Lint violation — replace hardcoded left/right with start/end
```

### XML — Android Lint

```xml
<LinearLayout
    tools:ignore="RtlHardcoded"
    android:layout_gravity="left"> <!-- TODO: resolve Lint violation -->
```

### Constraints

- Suppression scope: declaration-level only (no file-level or class-level)
- Tracking comment is mandatory and must include "TODO: resolve"
- Broad `@Suppress("all")` or wildcard patterns are prohibited (FR-007)

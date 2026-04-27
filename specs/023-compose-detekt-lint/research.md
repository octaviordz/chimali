# Research: Compose Detekt & Android Lint Integration

**Feature**: `023-compose-detekt-lint` | **Date**: 2026-04-26

## R-001: Compose Rules Detekt Plugin

**Decision**: Use `io.nlopez.compose.rules:detekt:0.5.7`

**Rationale**: This is the canonical community rule set for Detekt, maintained by Mario Sanoguera de Lorenzo (creator
of the compose-rules project). Version 0.5.7 is the latest stable release as of April 2026 and is compatible with
Detekt 1.23.x. The plugin is declared as a `detektPlugins` dependency (not a Gradle plugin), consistent with how
Detekt extension rule sets are distributed.

**Integration method**: The library JAR is placed on the Detekt classpath via `detektPlugins(...)` dependency
configuration. No additional Gradle plugin `id` is needed — Detekt auto-discovers rule providers via the JAR's
service loader manifest.

**Configuration namespace**: `compose` (all rules are under the top-level `compose:` YAML key in `detekt.yml`).
The full list of rule IDs is available at https://mrmans0n.github.io/compose-rules/rules/.

**Alternatives considered**:
- `twitter/compose-rules` (HLint-based): Rejected — Detekt-native version is preferred to stay in the existing
  Detekt toolchain.
- Detekt baseline (`--create-baseline`): Rejected per clarification Q5 — per-violation `@Suppress` is required.

---

## R-002: Lint DSL in Root `build.gradle.kts`

**Decision**: Add `lint {}` block inside a `subprojects {}` / `afterEvaluate {}` conditional in the root
`build.gradle.kts`, discriminating on Android plugin presence.

**Rationale**: The project has no `build-logic/` convention plugin module. The existing cross-cutting
configuration (Detekt, Ktlint) is applied in the root `subprojects {}` block. Adding Lint in the same location
keeps the architecture consistent and avoids introducing a new Gradle module.

**Lint DSL (AGP 9.2.0)**:
```kotlin
lint {
    abortOnError = true
    checkReleaseBuilds = true
    htmlReport = true
    xmlReport = true
    htmlOutput = file("${layout.buildDirectory.get()}/reports/lint/${project.name}.html")
    xmlOutput  = file("${layout.buildDirectory.get()}/reports/lint/${project.name}.xml")
    disable += setOf("TypographyFractions", "TypographyQuotes", "TypographyDashes",
                     "TypographyEllipsis", "TypographyOther")
    enable  += setOf("RtlHardcoded", "RtlCompat", "RtlEnabled")
    checkGeneratedSources = false
    ignoreTestSources = true
}
```

**AGP version note**: `abortOnError` and `htmlOutput`/`xmlOutput` are stable in AGP 9.x. `checkReleaseBuilds`
ensures `lintRelease` is a valid target.

**Alternatives considered**:
- Root `lint.xml` file: Provides only issue enable/disable, not `abortOnError` or report configuration.
  Rejected as insufficient.
- Per-module `lint {}` block: Rejected per clarification Q3 — single change point preferred.

---

## R-003: Detekt YAML — Compose Rule Section

**Decision**: Append a top-level `compose:` section to `config/detekt/detekt.yml` with all rules enabled
at `active: true`. The global `build.maxIssues: 0` setting causes any active finding to fail the build,
achieving error-severity enforcement without needing per-rule `severity: error` overrides.

**Key rules in the compose rule set** (all enabled):

| Rule ID | Catches |
|---------|---------|
| `CompositionLocalAllowlist` | Unregistered `CompositionLocal` usage |
| `CompositionLocalNaming` | `CompositionLocal` naming violations |
| `ContentEmitterReturningValues` | Composables returning values while emitting content |
| `ModifierComposable` | `Modifier` extension composables without Modifier parameter |
| `ModifierMissing` | Missing `Modifier` parameter in composables |
| `ModifierNaming` | `modifier` parameter named differently |
| `ModifierNotUsedAtRoot` | Modifier not applied at root layout |
| `ModifierReused` | Same Modifier instance reused across siblings |
| `ModifierWithoutDefault` | `Modifier` parameter missing default `Modifier` value |
| `MultipleEmitters` | Multiple content emitters in one composable |
| `MutableStateAutoboxing` | `mutableStateOf<Int/Long/Float/Double>` instead of primitive version |
| `MutableStateParam` | Mutable state passed as function parameter |
| `ComposableAnnotationNaming` | Annotation composables not named as adjectives |
| `ComposableNaming` | Composable functions not following naming convention (PascalCase) |
| `ComposableParamOrder` | Parameters out of recommended order |
| `DefaultsVisibility` | `@PreviewParameterProvider` defaults not public |
| `LambdaParameterInRestartableEffect` | Lambda params in `LaunchedEffect`/`remember` |
| `RememberMissing` | Expensive calls not wrapped in `remember` |
| `RememberStateMissing` | `mutableStateOf` not wrapped in `remember` |
| `UnstableCollections` | `List`/`Map`/`Set` used as Composable params (causes recomposition) |
| `ViewModelForwarding` | ViewModel forwarded to child composables |
| `ViewModelInjection` | ViewModel not injected via Koin/Hilt at composable entry point |

**Generated code exclusion**: The existing `config:excludes` pattern `'.*/build/.*,.*/generated/.*'` covers
KSP/KAPT generated code. The `complexity.excludes` pattern `'**/build/**,**/generated/**'` also applies.
Adding the same exclusion to the `compose:` section ensures consistent behaviour.

---

## R-004: CI Script Extension

**Decision**: Add `lintRelease` as a `Run-Task` call immediately after the `Detekt` task within the
`if (-not $SkipLint)` guard block in `tools/local-ci.ps1`.

**Rationale**: This keeps the lint escape hatch consistent with the existing `$SkipLint` switch, and
ensures Android Lint runs in the same CI stage as Detekt/Ktlint. Running `lintRelease` (not `lint`)
ensures it validates against production build configuration (minification enabled), consistent with
clarification Q2.

**Command**: `./gradlew lintRelease` (runs `lintRelease` on all subprojects that define it).

---

## R-005: Pre-Existing Violations Triage

**Decision**: Run `./gradlew detekt` after adding the plugin to discover any pre-existing Compose
violations. Each violation is suppressed with:
```kotlin
@Suppress("ComposeRuleName") // TODO: resolve Compose rule violation — <brief description>
```

Broad file-level or class-level `@Suppress` is prohibited (FR-007). If zero violations are found,
no suppressions are needed and the feature is complete.

**Detection command**: `./gradlew detekt --continue` (continues past errors to report all violations).

---

## R-006: `docs/quality.md` Content Scope

**Decision**: Create `docs/quality.md` with the following sections:
1. Overview of static analysis tools (Detekt, Compose rules, Ktlint, Android Lint)
2. Running checks locally (commands for each tool)
3. Compose rule suppressions — inline `@Suppress` pattern + tracking TODO
4. Android Lint suppressions — inline `@SuppressLint` / `tools:ignore` in XML + `lint.xml` file method
5. Report locations (HTML paths)
6. Adding new Detekt rules or lint checks

This satisfies FR-005 and the documentation standards principle (Constitution §VII).

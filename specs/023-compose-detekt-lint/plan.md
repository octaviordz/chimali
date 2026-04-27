# Implementation Plan: Compose Detekt & Android Lint Integration

**Branch**: `023-compose-detekt-lint` | **Date**: 2026-04-26 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/023-compose-detekt-lint/spec.md`

## Summary

Integrate the `io.nlopez.compose.rules:detekt` rule set (v0.5.7) at **error severity** into the existing Detekt pipeline,
and activate Android Lint (`lintRelease`) in CI via the project-root `build.gradle.kts` subprojects block (no separate
convention plugin exists). The Compose rule plugin targets only modules that already apply the Compose compiler plugin
(`app`, `core:ui`, `feature:authenticator`, `feature:editor`, `feature:fido2`, `feature:vault`).
Pre-existing violations are suppressed with targeted `@Suppress` annotations and tracking TODO comments.
The CI script (`tools/local-ci.ps1`) is extended with a `lintRelease` step, and `docs/quality.md` is created with
usage and suppression guidance.

## Technical Context

**Language/Version**: Kotlin 2.3.20, JVM 17  
**AGP**: 9.2.0 (confirms `lint {}` DSL available)  
**Primary Dependencies**:
- `io.nlopez.compose.rules:detekt:0.5.7` (new — `detektPlugins` dependency)
- Detekt 1.23.8 (existing)
- Android Lint (bundled with AGP 9.2.0 — no extra dependency)

**Storage**: N/A  
**Testing**: Verify via intentional violation + `./gradlew detekt` and `./gradlew lintRelease`  
**Target Platform**: Android (minSdk 28, compileSdk 35)  
**Project Type**: Android KMP application (no separate `build-logic/` convention plugin module — lint DSL lives in root `build.gradle.kts` `subprojects {}` block)  
**Performance Goals**: Local quality check completes < 5 min on warm cache (SC-003)  
**Constraints**: No per-module duplication; no baseline files; no rule-severity demotions  
**Scale/Scope**: 6 Compose modules, 1 app module, 4 non-Compose Android/KMP modules

### Compose Modules (receive `detektPlugins` + lint DSL)

| Module | Path |
|--------|------|
| `:app` | `app/` |
| `:core:ui` | `core/ui/` |
| `:feature:authenticator` | `feature/authenticator/` |
| `:feature:editor` | `feature/editor/` |
| `:feature:fido2` | `feature/fido2/` |
| `:feature:vault` | `feature/vault/` |

### Non-Compose Modules (lint DSL only, no Compose rules)

| Module | Path |
|--------|------|
| `:core:common` | `core/common/` (KMP) |
| `:core:bluetooth` | `core/bluetooth/` |
| `:core:data` | `core/data/` |
| `:core:database` | `core/database/` |
| `:core:domain` | `core/domain/` |
| `:core:security` | `core/security/` |
| `:core:crdt` | `core/crdt/` |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| **I. Security First** | ✅ Pass | This feature adds analysis tooling only; no changes to encryption or key handling |
| **III. Architecture & Quality** | ✅ Pass | Directly fulfils the mandate: "Static analysis via Detekt and Ktlint is mandatory" — this extends it with Compose rules |
| **VII. Documentation Standards** | ✅ Pass | `docs/quality.md` creation is a required deliverable (FR-005) |
| **VIII. Local CI/CD Enforcement** | ✅ Pass | `tools/local-ci.ps1` is extended with `lintRelease`; `SkipLint` flag will gate it consistently |

No violations. Complexity Tracking table not needed.

## Project Structure

### Documentation (this feature)

```text
specs/023-compose-detekt-lint/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (affected files)

```text
gradle/
└── libs.versions.toml              # Add compose-rules version + library entry

build.gradle.kts                    # Add detektPlugins in Compose subprojects block;
                                    # Add lint {} DSL in all Android subprojects block

config/
└── detekt/
    └── detekt.yml                  # Add compose-rules section (all rules, error severity)
                                    # Add generated code exclusions

tools/
└── local-ci.ps1                    # Add lintRelease step after Detekt

docs/
└── quality.md                      # New file: quality tooling guide with suppression instructions

# Compose source files (any that trigger pre-existing violations):
app/src/                            # @Suppress + TODO comments as needed
core/ui/src/                        # @Suppress + TODO comments as needed
feature/*/src/                      # @Suppress + TODO comments as needed
```

## Phase 0: Research

*See [research.md](./research.md)*

## Phase 1: Design

### Architecture Decision: Dependency Declaration Pattern

**Decision**: Add the `detektPlugins` dependency inside an additional `subprojects {}` block in the
root `build.gradle.kts`, conditioned on modules that apply the Compose compiler plugin — using
`plugins.hasPlugin("org.jetbrains.kotlin.plugin.compose")` as the discriminator. This avoids per-module
changes and mirrors the existing `detekt` and `ktlint` wiring pattern already present in the root build file.

**Decision**: Add the Android Lint `lint {}` DSL inside the existing `subprojects {}` block, conditioned
on modules that apply the Android plugin — using `plugins.hasPlugin("com.android.application")` or
`plugins.hasPlugin("com.android.library")`. This covers all Android modules uniformly without per-module
duplication.

**Decision**: The Compose rule set is configured entirely via `config/detekt/detekt.yml`. The YAML
namespace for the rule set is `compose` (as defined by `io.nlopez.compose.rules`). All rules default
to `active: true`; severity inherits from the global `build.maxIssues: 0` setting which treats any
finding as an error.

### Key Design Choices

- **No `build-logic/` module required**: The project centralises cross-module configuration in root
  `build.gradle.kts` `subprojects {}` blocks. The same pattern applies here.
- **Plugin discrimination**: `plugins.hasPlugin()` evaluated after `afterEvaluate {}` in Gradle
  avoids ordering issues when checking plugin application state in subprojects.
- **Generated code exclusion**: Detekt already excludes `**/build/**` and `**/generated/**` at the
  global level. Lint's `ignoreTestSources = true` and `checkGeneratedSources = false` (default) cover
  the Lint side.
- **`lintRelease` availability**: The `:app` module defines `release` and `shrunkDebug` build types.
  All library modules inherit `release`. `lintRelease` is available on all Android modules.
- **`$SkipLint` gate**: The existing `$SkipLint` switch in `local-ci.ps1` will also gate the new
  `lintRelease` step, keeping the escape hatch consistent.

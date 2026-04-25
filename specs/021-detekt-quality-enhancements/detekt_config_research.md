# Research: Top 5 Detekt Configuration Improvements
**Feature**: `021-detekt-quality-enhancements`  
**Date**: 2026-04-25  
**Scope**: Analysis of `config/detekt/detekt.yml` against Detekt 1.23.8 best practices

---

## Analysis Summary

The current configuration is well-structured and already enforces a solid quality baseline (`maxIssues: 0`, `MagicNumber`, `MaxLineLength: 120`, coroutine safety rules, etc.). However, several high-impact rules are either disabled, have dangerously permissive thresholds, or are missing entirely. The five recommendations below are ordered by **risk-to-quality ratio** — i.e., the severity of the bugs or maintainability debt they prevent vs. the implementation effort.

---

## Recommendation 1 — Tighten Complexity Thresholds (Critical Debt Risk)

### Current State

| Rule                    | Current Threshold | Industry Standard |
|-------------------------|-------------------|-------------------|
| `CognitiveComplexMethod` | 100               | 15–20             |
| `CyclomaticComplexMethod` | 40              | 10–15             |
| `LongMethod`            | 400 lines         | 30–60 lines       |
| `LargeClass`            | 600 lines         | 200–300 lines     |
| `LongParameterList`     | 12 params         | 5–7 params        |
| `TooManyFunctions`      | 15 (all scopes)   | 10–11 (class)     |

### Problem

The current thresholds are so permissive they effectively disable these rules for any real-world code. A method with 100 cognitive complexity points is impossible to understand, review, or safely refactor. These are **not** quality gates; they are escape hatches that allow code rot to accumulate undetected.

The `feature/vault` and `feature/fido2` exclusions for several of these rules compound the problem: the two most security-critical modules in the codebase are exempted from complexity enforcement.

### Recommendation

**Phase 1 (immediate)**: Tighten thresholds to catch the worst offenders without breaking the build. These values are still lenient but realistic for a legacy module starting to be refactored.

YAML changes required:
- CognitiveComplexMethod.threshold: 100 → 35, remove feature/vault exclusion
- CyclomaticComplexMethod.threshold: 40 → 20, remove feature/vault exclusion
- LongMethod.threshold: 400 → 100, remove feature/vault exclusion
- LargeClass.threshold: 600 → 300
- LongParameterList.functionThreshold: 12 → 8, constructorThreshold: 12 → 8, ignoreDefaultParameters: false → true, remove feature/vault and feature/fido2 exclusions
- TooManyFunctions: thresholdInFiles: 15 → 12, thresholdInClasses/Interfaces/Objects: 15 → 11

**Phase 2 (follow-up spec)**: Drive down to target values (Cognitive: 15, Cyclomatic: 10, LongMethod: 60) after planned refactoring.

### Effort: Medium | Risk: Low (only flags new violations; no code changes required for the config update itself)

### Acceptance Criteria
- `local-ci.ps1` passes with tightened thresholds after existing violations are remediated.
- No `excludes` for `feature/vault` on complexity rules.

---

## Recommendation 2 — Enable `ForbiddenMethodCall` for Logging (Spec-021 FR-001)

### Current State

```yaml
ForbiddenMethodCall:
  active: true
  methods:
    - reason: 'print does not allow you to configure the output stream. Use a logger instead.'
      value: 'print'
    - reason: 'println does not allow you to configure the output stream. Use a logger instead.'
      value: 'println'
```

### Problem

`ForbiddenImport` already blocks `kotlin.io.println` but `ForbiddenMethodCall` is only banning the short names `print` / `println`. Without fully-qualified method signatures, the rule relies on name matching only and misses calls via aliased imports or `System.out.println`.

Additionally, `android.util.Log` direct calls (`Log.d`, `Log.e`, etc.) bypass the project's structured Kermit logging and are not blocked.

### Recommendation

Replace short names with fully-qualified signatures and add android.util.Log variants:

```yaml
ForbiddenMethodCall:
  active: true
  methods:
    - reason: 'Use the project Kermit logger instead of print.'
      value: 'kotlin.io.print'
    - reason: 'Use the project Kermit logger instead of println.'
      value: 'kotlin.io.println'
    - reason: 'Use the project Kermit logger instead of android.util.Log.'
      value: 'android.util.Log.d'
    - reason: 'Use the project Kermit logger instead of android.util.Log.'
      value: 'android.util.Log.e'
    - reason: 'Use the project Kermit logger instead of android.util.Log.'
      value: 'android.util.Log.i'
    - reason: 'Use the project Kermit logger instead of android.util.Log.'
      value: 'android.util.Log.v'
    - reason: 'Use the project Kermit logger instead of android.util.Log.'
      value: 'android.util.Log.w'
    - reason: 'Use the project Kermit logger instead of android.util.Log.'
      value: 'android.util.Log.wtf'
```

### Effort: Low | Risk: Low

### Acceptance Criteria
- A file calling `android.util.Log.d(...)` in a `main` source set causes Detekt to fail.
- A file calling `Logger.d { ... }` (Kermit) passes cleanly.

---

## Recommendation 3 — Activate `Deprecation` & `DontDowncastCollectionTypes` (Potential-Bugs Gap)

### Current State

```yaml
Deprecation:
  active: false

DontDowncastCollectionTypes:
  active: false
```

### Problem

**`Deprecation`**: With Kotlin 2.1.x and Compose evolving rapidly, deprecated API usage silently accumulates. No static analysis gate prevents deprecated API calls from reaching `main`.

**`DontDowncastCollectionTypes`**: Downcasting `List` to `MutableList`, `Map` to `MutableMap` etc. bypasses Kotlin's immutability contract and can cause `ConcurrentModificationException` or silent mutations. The active `DoubleMutabilityForCollection` rule catches `var MutableList` but **not** unsafe downcasts.

### Recommendation

```yaml
Deprecation:
  active: true
  excludes:
    - '**/test/**'
    - '**/androidTest/**'
    - '**/commonTest/**'
    - '**/jvmTest/**'
    - '**/androidUnitTest/**'
    - '**/androidInstrumentedTest/**'
    - '**/jsTest/**'
    - '**/iosTest/**'

DontDowncastCollectionTypes:
  active: true
```

### Effort: Low | Risk: Medium (may surface existing deprecated API usage)

### Acceptance Criteria
- Usage of a `@Deprecated` API in `main` source sets causes Detekt to flag it.
- Downcasting a `List` to `MutableList` via `as` causes Detekt to flag it.

---

## Recommendation 4 — Enable `CouldBeSequence` for Performance (Performance Gap)

### Current State

```yaml
CouldBeSequence:
  active: false
  threshold: 3
```

### Problem

The performance rule `CouldBeSequence` is disabled. In a codebase handling 10,000+ vault items (Constitution §IV), chained collection operations like `.filter { }.map { }.take(n)` allocate intermediate lists at every step. The sequence equivalent is zero-allocation between steps and dramatically faster for large datasets.

The `threshold: 3` default is already ideal and requires no change.

### Recommendation

```yaml
CouldBeSequence:
  active: true
  threshold: 3
```

### Effort: Very Low (single line change) | Risk: Low

### Acceptance Criteria
- A chain of 3+ collection operations on a `List` (e.g., `.filter { }.map { }.first()`) causes Detekt to suggest using `asSequence()`.

---

## Recommendation 5 — Harden `WildcardImport` Exceptions + Enable `StringShouldBeRawString` (Maintainability Gap)

### Current State

```yaml
WildcardImport:
  active: true
  excludeImports:
    - 'androidx.compose.runtime.*'
    - 'androidx.compose.material.icons.filled.*'
    - 'androidx.compose.material3.*'
    - 'androidx.compose.foundation.layout.*'
    - 'com.chimali.fido2.presentation.ui.components.*'  # ← internal wildcard: anti-pattern

StringShouldBeRawString:
  active: false
```

### Problem

**`WildcardImport`**: The exclusion for `com.chimali.fido2.presentation.ui.components.*` wildcards an **internal** project package. This obscures which specific components are used, breaks "find usages" accuracy, and makes dependency analysis unreliable. The Compose SDK wildcard exceptions are a standard industry practice; internal package wildcards are not.

**`StringShouldBeRawString`**: Disabled, meaning strings containing 3+ escape sequences (common in regex, JSON templates, URL patterns) are not flagged. Raw strings improve readability and eliminate escape-sequence bugs.

### Recommendation

Remove the internal wildcard exception and enable StringShouldBeRawString:

```yaml
WildcardImport:
  active: true
  excludeImports:
    - 'androidx.compose.runtime.*'
    - 'androidx.compose.material.icons.filled.*'
    - 'androidx.compose.material3.*'
    - 'androidx.compose.foundation.layout.*'
    # REMOVED: 'com.chimali.fido2.presentation.ui.components.*'

StringShouldBeRawString:
  active: true
  maxEscapedCharacterCount: 2
  ignoredCharacters: []
  excludes:
    - '**/test/**'
    - '**/androidTest/**'
    - '**/commonTest/**'
    - '**/jvmTest/**'
    - '**/androidUnitTest/**'
    - '**/androidInstrumentedTest/**'
```

### Effort: Low (config change + remediation of the single wildcard import in fido2 UI) | Risk: Low

### Acceptance Criteria
- `import com.chimali.fido2.presentation.ui.components.*` causes a `WildcardImport` violation.
- A string with 3+ escape sequences is flagged as a candidate for raw string.

---

## Summary Table

| # | Rule / Area | Category | Effort | Impact |
|---|-------------|----------|--------|--------|
| 1 | Tighten complexity thresholds (Cognitive, Cyclomatic, LongMethod, LargeClass, LongParameterList) | `complexity` | Medium | Critical — prevents long-term code rot in security-critical modules |
| 2 | `ForbiddenMethodCall` — add FQNs + `android.util.Log.*` | `style` | Low | High — enforces structured Kermit logging project-wide |
| 3 | Enable `Deprecation` + `DontDowncastCollectionTypes` | `potential-bugs` | Low | High — catches correctness bugs silently accumulating with Kotlin/Compose upgrades |
| 4 | Enable `CouldBeSequence` | `performance` | Very Low | Medium — directly tied to Constitution §IV (10k+ vault items) |
| 5 | Remove internal `WildcardImport` exception + enable `StringShouldBeRawString` | `style` | Low | Medium — closes maintainability gap in fido2 UI layer |

---

## Out of Scope (deferred to future spec)

- `ForbiddenSuppress` — should be enabled to prevent `@Suppress` abuse, but requires an audit of existing suppressions first.
- `BracesOnIfStatements` / `BracesOnWhenStatements` — formatting rules best enforced via Ktlint, not Detekt.
- `UndocumentedPublicClass/Function/Property` — valuable but extremely high noise for a codebase without established KDoc conventions.
- Complexity thresholds Phase 2 (target values) — depends on planned vault refactoring.

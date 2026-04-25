# Detekt Configuration Analysis: Top 5 Quality Enhancements
**Date**: 2026-04-25  
**Project**: Chimali  
**Scope**: `config/detekt/detekt.yml`

---

## 1. 🔴 Tighten Complexity Thresholds (Critical Debt Risk)

### Current State
| Rule | Current Threshold | Industry Standard |
|---|---|---|
| `CognitiveComplexMethod` | 100 | 15–20 |
| `CyclomaticComplexMethod` | 40 | 10–15 |
| `LongMethod` | 400 lines | 30–60 lines |
| `LargeClass` | 600 lines | 200–300 lines |

### Problem
Current thresholds effectively disable these rules. Method with 100 cognitive complexity is unmaintainable. Exclusions for `feature/vault` (security-critical) compound the risk.

### Recommendation
Tighten thresholds to realistic immediate targets (Cognitive: 35, Cyclomatic: 20, LongMethod: 100) and remove `feature/vault` exclusions.

---

## 2. 🟠 Enable `ForbiddenMethodCall` for Logging

### Problem
Short names (`print`, `println`) are unreliable for blocking standard output. Direct calls to `android.util.Log.*` bypass the project's structured Kermit logging.

### Recommendation
Use fully-qualified names (`kotlin.io.println`) and add all `android.util.Log` variants to the forbidden list.

---

## 3. 🟠 Activate `Deprecation` & `DontDowncastCollectionTypes`

### Problem
Deprecated API usage accumulates without warning. Unsafe downcasting (e.g., `List` as `MutableList`) violates Kotlin immutability contracts and causes runtime crashes.

### Recommendation
Enable both rules to prevent API decay and collection-related bugs.

---

## 4. 🟡 Enable `CouldBeSequence` for Performance

### Problem
Constitution §IV targets 10,000+ vault items. Chained list operations (`filter.map`) allocate intermediate collections. Sequences are lazy and zero-allocation between steps.

### Recommendation
Enable the rule with `threshold: 3` to optimize high-volume data processing.

---

## 5. 🟡 Harden `WildcardImport` Exceptions & Raw Strings

### Problem
Internal wildcard imports (`com.chimali.*`) obscure dependencies. Escape-heavy strings (regex, JSON) are hard to read and maintain.

### Recommendation
Remove internal wildcard exceptions and enable `StringShouldBeRawString` for literals with 3+ escape sequences.

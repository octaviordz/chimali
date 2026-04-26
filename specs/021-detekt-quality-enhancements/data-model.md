# Data Model: Detekt Configuration State

This document describes the intended state of the `detekt.yml` configuration and the rules being hardened.

## Core Rules Configuration

| Rule | Status | Threshold/Constraint |
|---|---|---|
| `ForbiddenMethodCall` (Logging) | Active | Prohibit `println`, `print`, `android.util.Log.*` |
| `StringLiteralDuplication` | Active | Threshold: 5, Min Length: 5 |
| `StringShouldBeRawString` | Active | Max Escapes: 4 (Complex only) |
| `UseSequence` | Active | Chain length: 3+ |
| `DontDowncastCollectionTypes` | Active | Prohibit `as MutableList` etc. |
| `WildcardImport` | Active | Prohibit internal `com.chimali.*` imports |
| `Deprecation` | Active | Fail on `@Deprecated` usage |

## Module Exclusion Matrix

| Module | Production Code (`main`) | Test Code (`test`, `androidTest`) |
|---|---|---|
| `feature:vault` | **NO EXCLUSIONS** | Exclusions Permitted |
| `feature:fido2` | **NO EXCLUSIONS** | Exclusions Permitted |
| `core:domain` | **NO EXCLUSIONS** | Exclusions Permitted |
| Generated Code | Exclusions Permitted | Exclusions Permitted |

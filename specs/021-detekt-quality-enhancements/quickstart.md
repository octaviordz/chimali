# Quickstart: Detekt Quality Hardening

## Overview
This feature enforces strict quality gates on feature modules. Developers must ensure their code complies with the newly activated rules and the removal of module-wide exclusions.

## Verification Workflow
To verify compliance locally, run the standard CI pipeline:

```powershell
.\tools\local-ci.ps1
```

The pipeline will now fail if:
1. You use `println` or `android.util.Log` in feature modules.
2. You have high complexity in `feature:vault` or `feature:fido2` that was previously hidden by exclusions.
3. You have duplicated strings (>= 5 occurrences) or non-idiomatic `let` usage.

## Suppressing Legitimate Violations
If a specific violation is legitimate (e.g., a complex cryptographic algorithm that cannot be further simplified), use targeted suppression rather than global exclusions:

```kotlin
@Suppress("CognitiveComplexMethod")
fun myComplexFunction() { ... }
```

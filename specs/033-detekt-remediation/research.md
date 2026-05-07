# Phase 0: Research & Remediation Strategy

## Overview
This document outlines the strategy for resolving the top 4 Detekt issues in the `feature\fido2` module, removing the suppressions from `detekt-baseline-main.xml`.

## 1. MagicNumber (68 occurrences)
- **Decision**: Extract all hardcoded numeric literals into `private const val` or `companion object const val` properties with descriptive names.
- **Rationale**: Direct alignment with Constitution Principle III. Increases code readability and makes boundary limits, sizes, and delay values self-documenting.
- **Alternatives considered**: Suppressing the rule on a per-file basis. Rejected because it violates the constitution's zero-tolerance policy for magic numbers.

## 2. ClassNaming (60 occurrences)
- **Decision**: Rename offending classes, interfaces, or objects to adhere strictly to `PascalCase`.
- **Rationale**: Enforces standard Kotlin naming conventions across the module.
- **Alternatives considered**: Keeping legacy names for test classes. Rejected to maintain uniform standards across main and test source sets.

## 3. BooleanPropertyNaming (55 occurrences)
- **Decision**: Prefix all boolean properties with standard Kotlin interrogative prefixes (e.g., `is`, `has`, `should`, `can`) based on the Detekt configuration.
- **Rationale**: Adheres to idiomatic Kotlin property naming, making conditionals read like natural language.
- **Alternatives considered**: Modifying the Detekt configuration to allow arbitrary names. Rejected as it degrades code clarity.

## 4. SuspendFunWithFlowReturnType (18 occurrences)
- **Decision**: Remove the `suspend` keyword from the signatures of functions returning `Flow<T>`. 
- **Rationale**: `Flow` is a cold stream. Collecting the flow suspends, so the creation/return of the flow itself does not need to be a suspending operation. This is a standard Kotlin Coroutines best practice.
- **Alternatives considered**: None. The current usage is technically incorrect and redundant.

## Conclusion
All technical unknowns are resolved. The remediation is purely structural and stylistic, with no algorithmic or logical changes required.

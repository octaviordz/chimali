# Data Model: Detekt MagicNumber Configuration

This document describes the configuration entities and their attributes for the Detekt `MagicNumber` rule.

## Entities

### MagicNumberRule
Represents the configuration for the MagicNumber check.

| Attribute | Type | Value (Target) | Description |
|-----------|------|----------------|-------------|
| `active` | Boolean | `true` | Enables the rule. |
| `excludes` | List<String> | `['**/build/generated/**', '**/*Generated.kt']` | Files to ignore. |
| `ignoreNumbers` | List<String> | `['-1', '0', '1', '2', '17', '21', '24', '30', '31', '33', '34', '35']` | Literal numbers to ignore. |
| `ignoreAnnotation` | Boolean | `true` | Ignore numbers in annotations. |
| `ignoreEnums` | Boolean | `true` | Ignore numbers in enums. |
| `ignoreRanges` | Boolean | `true` | Ignore numbers in ranges. |
| `ignoreNamedArgument` | Boolean | `true` | Ignore numbers used as named arguments. |

## Relationships

- **MagicNumberRule** is defined within the **RuleSet** (Style) in `detekt.yml`.
- **Exclusions** are relative to the project root or module source sets.

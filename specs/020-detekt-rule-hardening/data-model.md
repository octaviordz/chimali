# Data Model: Detekt Rule Configuration

## Entities

### DetektRule
Represents a static analysis rule to be enforced.

| Field | Description |
|-------|-------------|
| Name | The unique identifier of the rule (e.g., `WildcardImport`) |
| Active | Boolean indicating if the rule is enabled |
| Excludes | List of path patterns to ignore |
| ExcludeImports | (Specific to WildcardImport) List of allowed wildcard patterns |

## Rule Enforcement Status

| Rule | Targeted State |
|------|----------------|
| WildcardImport | Active, No custom excludes, No allowed wildcards |
| UnusedImports | Active, No custom excludes |
| NewLineAtEndOfFile | Active, No custom excludes |
| UnsafeCallOnNullableType | Active, No custom excludes |
| LateinitUsage | Active, No custom excludes |
| EmptyDefaultConstructor | Active, No custom excludes |

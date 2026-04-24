# Research: Detekt Quality Enforcement (MaxLineLength)

## Analysis of Current State

### Disconnected Enforcement
The `MaxLineLength` rule is defined in `config/detekt/detekt.yml`, but its enforcement is inconsistent across the project because the Detekt plugin is not applied to all modules.
- **Applied**: `feature:fido2`, `app` (likely)
- **Missing**: `feature:vault`, `core:security`, `core:ui`, etc.

This explains why violations in `VaultListScreen.kt` (e.g., Line 100, 135 chars) are not failing the build despite the rule being "active".

### Scope of Exclusions
The current `MaxLineLength` rule explicitly excludes all test directories:
```yaml
excludes: ['**/test/**', '**/androidTest/**', '**/commonTest/**', '**/jvmTest/**', '**/androidUnitTest/**', '**/androidInstrumentedTest/**', '**/jsTest/**', '**/iosTest/**']
```
Removing these will trigger violations in almost every module's test suite.

## Findings

### Auto-Correction
- **Detekt Auto-Correct**: The `MaxLineLength` rule in Detekt **does not** support auto-correction. It can identify the issue but cannot automatically wrap the code.
- **Ktlint Integration**: `ktlint` does support wrapping, but we are currently focusing on the Detekt rule enforcement.

### Violation Mapping (Partial)
Based on initial grep analysis, here are the expected high-impact areas:
- `feature:vault`: Several UI components and repository implementations.
- `feature:fido2`: Logging utilities and test suites.
- `core:security`: Cryptographic implementation details (though some might be raw strings).

## Decisions

1. **Standardize Plugin Application**: We must apply the Detekt plugin to all modules via the root `build.gradle.kts` to ensure project-wide enforcement.
2. **Incremental Fixes**: Once the plugin is applied and excludes are removed, we will use an iterative approach to refactor code:
    - Focus on test code first (as requested).
    - Handle `MaxLineLength` violations by introducing appropriate line breaks.
3. **Preserve Raw Strings**: We will ensure `excludeRawStrings: true` remains active to avoid breaking long JSON strings or cryptographic constants that are naturally long.

## Refactoring Patterns

To stay under 120 chars without changing logic:
- **Compose UI**: Break long parameter lists into multiple lines (one per parameter).
- **Assertions**: Break long `assertEquals` or `assertThat` calls.
- **Mocks**: Use intermediate variables for long mock setups.

---
**Status**: Research Complete. Ready for Phase 1 Design.

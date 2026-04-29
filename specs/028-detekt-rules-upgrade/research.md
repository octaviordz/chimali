# Research: Detekt Rules Upgrade and Enforcement

## Rule Mapping and Configuration

### Decision: Mapping User Rules to Detekt Standard Names
- **CyclomaticComplexity**: Mapped to `CyclomaticComplexMethod`.
- **GlobalScopeUsage**: Mapped to `GlobalCoroutineUsage`.
- **Rationale**: Using the standard Detekt rule names ensures compatibility with the current version and prevents configuration errors.

### Decision: Threshold Application
- All thresholds provided in the specification will be applied literally to the mapped rules.
- **Rationale**: The user has provided specific expert-level thresholds that should be strictly enforced.

## Refactoring Patterns

### Decision: MagicNumber Resolution
- Extract literal numbers to `const val` in the appropriate scope.
- Priority: 
  1. Companion Object (if scoped to a class).
  2. File-level (if shared across a file).
  3. Core/Module constants (if shared globally).
- **Rationale**: Aligns with Constitution Principle III (prohibition of magic numbers).

### Decision: LongMethod/LargeClass Resolution
- Use **Extract Function** and **Extract Class** refactoring patterns.
- Ensure that extracted functions have meaningful names and follow the single responsibility principle.
- **Rationale**: Improves maintainability without changing business logic.

### Decision: SwallowedException Resolution
- Ensure exceptions are either logged via the project's standard logger (Kermit) or rethrown.
- **Rationale**: Prevents silent failures while maintaining visibility into errors.

## Suppression Enforcement

### Decision: Global Suppression Removal
- Use regex search to find `@Suppress("RuleName")` or `@Suppress(..., "RuleName", ...)` and remove the specific rule or the entire annotation if empty.
- **Rationale**: Ensures that all hidden technical debt for these specific rules is exposed for resolution.

## Risk Assessment

### Decision: Logic Integrity
- No logic changes will be performed. Any refactoring must be verifiable through existing unit tests.
- **Rationale**: Minimizes the risk of regressions while improving code quality.

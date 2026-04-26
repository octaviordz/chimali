# Research: Enforce Detekt Complexity Rules

## Detekt Configuration

- **Decision**: Update `config/detekt/detekt.yml` to enable the `CognitiveComplexMethod` rule with a threshold of 40.
- **Rationale**: The specification requires enforcing this rule at 40 to align with the agreed-upon standards for Chimali's current codebase state.
- **Alternatives considered**: Leaving it disabled or setting it to a lower threshold (rejected by user clarification).

## Code Refactoring Strategy

- **Decision**: For the 5 failing methods (`buildLegibilityAnnotatedString`, `PasswordDetailScreen`, `registerApp`, `CredentialListScreen`, `DevelopmentToolsContent`), we will use "Extract Method" and "Extract Composable" techniques.
- **Rationale**: This is the safest way to reduce cognitive complexity without altering underlying business logic. Smaller, well-named helper functions/composables inherently document the code and reduce nesting.
- **Alternatives considered**: Disabling the rule for these specific files via `@Suppress` (rejected, as the goal is to enforce the rule and improve quality).

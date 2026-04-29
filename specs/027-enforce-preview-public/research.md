# Research: Preview Visibility Standardization

## Findings

### Current State
Multiple files in the `feature/vault` module contain Compose Preview functions that are declared as `public` and use `@Suppress("PreviewPublic")` and `@Suppress("ForbiddenComment")` to bypass lint checks. These functions often include a TODO comment: `// TODO: Make internal once preview isolation is addressed`.

### Files Impacted
1. `feature/vault/src/main/java/com/chimali/feature/vault/ui/CreditCardDetailScreen.kt`
2. `feature/vault/src/main/java/com/chimali/feature/vault/ui/CreditCardEntryScreen.kt`
3. `feature/vault/src/main/java/com/chimali/feature/vault/ui/LabelManagerScreen.kt`
4. `feature/vault/src/main/java/com/chimali/feature/vault/ui/PasswordDetailScreen.kt`
5. `feature/vault/src/main/java/com/chimali/feature/vault/ui/PasswordEntryScreen.kt`
6. `feature/vault/src/main/java/com/chimali/feature/vault/ui/SecureNoteDetailScreen.kt`
7. `feature/vault/src/main/java/com/chimali/feature/vault/ui/SecureNoteEntryScreen.kt`
8. `feature/vault/src/main/java/com/chimali/feature/vault/ui/VaultListScreen.kt`

### Visibility Choice
According to Android Best Practices and the `PreviewPublic` rule, previews should not be public. They should be `private` if they are only used within the file, or `internal` if they are used across the module (e.g., for Multipreview or shared tooling). 

Given that these previews appear to be standalone and file-specific, **making them `private` or `internal` is the recommended path**. Since the existing TODO explicitly mentions "Make internal", I will proceed with making them `internal` where appropriate, or `private` if they are purely local.

### Decision
- **Action**: Remove `@Suppress("PreviewPublic")` and `@Suppress("ForbiddenComment")`.
- **Action**: Remove the associated TODO comments.
- **Action**: Change the visibility of the Preview functions to `internal` (as suggested by the existing TODOs).
- **Rationale**: This aligns with the project's goal of enforcing static analysis rules and keeping the public API clean.

### Alternatives Considered
- **Keeping the suppression**: Rejected as it contributes to technical debt and violates the feature goal.
- **Making them private**: Also a valid option, but `internal` is more flexible if these previews are ever referenced by module-level tooling. I will default to `internal` to satisfy the "Make internal" TODO.

## Unknowns Resolved
- **Which files are impacted?**: Identified 8 files via grep.
- **What visibility to use?**: `internal` is chosen based on the existing TODOs and project context.
